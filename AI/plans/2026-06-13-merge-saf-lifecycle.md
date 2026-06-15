# Fix: Merge SIGSEGV — saf_close null pointer in libffmpegkit.so

## Problem
During a merge operation the app crashes (signal 11, SIGSEGV) inside `libffmpegkit.so`
at `saf_close+96`, called from `avio_close → avformat_close_input → av_read_frame`
(thread `dmx0:concat`, pid 26471, tid 26541). The crash is a null-pointer dereference.

Root cause: our `closeSafParameter` calls `FFmpegKitConfig.closeFFmpegPipe(safPath)`,
which is the named-pipe API, **not** the SAF descriptor API. More importantly,
`SafScope(ffmpegEngine).use {}` closes the SAF paths as soon as the `use`-block body
returns — which happens the moment `ffmpegEngine.execute(cmd).collect {}` returns.
At that exact instant the native concat demuxer thread may still be running its teardown
and calling `safClose(id)` on the same descriptor. The double-close (once from our code
via `closeFFmpegPipe`, and once from FFmpegKit's internal teardown) corrupts the file
descriptor and causes the null-pointer dereference.

## AAR API surface verified (com.antonkarpenko:ffmpeg-kit-min:2.1.0)

Inspected via `javap -p` on classes.jar. Confirmed public symbols relevant to SAF:

```
// acquire
public static String getSafParameter(Context, Uri, String mode)   // "r" or "w"
public static String getSafParameterForRead(Context, Uri)          // shorthand
public static String getSafParameterForWrite(Context, Uri)         // shorthand

// close — DOES NOT EXIST for SAF:
//   closeParcelFileDescriptor(int)  → NOT in this fork
//   The only public close is:
public static void closeFFmpegPipe(String)  // for named pipes only — NOT for SAF

// SAF lifecycle is managed internally by the native layer:
private static int safOpen(int)   // called by FFmpeg when it opens a saf: URL
private static int safClose(int)  // called by FFmpeg when it closes a saf: URL
```

**Conclusion**: There is NO public API to close a SAF descriptor in this fork.
The native layer calls `safClose(id)` automatically when FFmpeg is done with the
file. User code must NEVER call `closeFFmpegPipe` on a `saf:` path, and must
NEVER perform any manual close of SAF descriptors. The lifecycle is:
  1. `getSafParameter(...)` — registers the descriptor in `safIdMap`.
  2. FFmpeg reads from `saf:<id>.<ext>`.
  3. FFmpegKit's native `safClose` is called automatically by FFmpeg on teardown.
  4. After `Completed` callback fires and the native thread finishes, the descriptor
     is already released.

## Proposed Change

### FIX 1 — Remove closeSafParameter entirely / replace with no-op

`FfmpegEngine.closeSafParameter(safPath: String)` is the wrong abstraction.
Remove it from the interface, or replace with a no-op and document clearly.

- **`FfmpegEngine.kt`**: Remove `closeSafParameter` from the interface.
- **`ProcessFfmpegEngine.kt`**: Remove the `closeSafParameter` override.
- **`FfmpegKitApiCheck.kt`**: Remove the stray `closeFFmpegPipe(saf)` call.

### FIX 2 — Delete SafScope (it only wraps the wrong close)

`SafScope` exists solely to call `closeSafParameter` for every SAF path.
Since that call is wrong and must be removed, `SafScope` serves no purpose.
Delete `SafScope.kt` and remove its usage from `Merger.kt`.

### FIX 3 — Restructure Merger.runMerge without SafScope

The new lifecycle in `Merger.runMerge`:

```kotlin
// No SafScope. Descriptors are managed internally by FFmpegKit.
val safPaths = partUris.map { partUri ->
    ffmpegEngine.getSafParameter(context, Uri.parse(partUri), "r")
        ?: throw EngineError.InputUnreadable(partUri, "saf parameter unavailable")
}

val concatFile = File(context.cacheDir, "concat.txt")
MergeListWriter.writeSafList(concatFile, safPaths)

val cmd = listOf(
    "-hide_banner", "-y",
    "-protocol_whitelist", "file,crypto,data,saf",
    "-f", "concat",
    "-safe", "0",
    "-i", concatFile.absolutePath,
    "-map", "0",
    "-map", "0:t?",
    "-c", "copy",
    "-avoid_negative_ts", "make_zero",
    "-f", "matroska",
    tempOutputFile.absolutePath
)

// FFmpegKit manages SAF descriptor lifecycle internally.
// Do NOT call closeFFmpegPipe or any manual close.
ffmpegEngine.execute(cmd).collect { event -> ... }
```

### FIX 4 — Add safety pause before close() in completeCallback

Even without our manual close, there can be a race between the `completeCallback`
firing and the `callbackFlow`'s `close()` causing the caller's coroutine to resume
and proceed before the concat demuxer thread has fully unwound.

In `ProcessFfmpegEngine.kt` `completeCallback`:

```kotlin
completeCallback = { session ->
    val exit = session.returnCode?.value ?: -1
    Timber.tag("ENGINE").d("done token=%s exit=%d", token, exit)
    activeSessions.remove(token)
    trySend(EngineEvent.Completed(exit))
    // Tombstone 2026-06-13 / plan 2026-06-13-merge-saf-lifecycle.md:
    // Allow FFmpegKit's native demuxer thread to finish internal teardown
    // before the callbackFlow channel closes and the caller resumes.
    Thread.sleep(100)
    close()
}
```

### FIX 5 — (Optional) DebugCrashCollector  T-111

Debug-only `FileObserver` on `/data/tombstones/` that adb-pulls new tombstones
into `logs/` on the next test run. Low priority; open as T-111 in `AI/tasks.md`.

## Files touched (paths + intent)

| File | Change |
|---|---|
| `app/.../engine/FfmpegEngine.kt` | Remove `closeSafParameter` from interface |
| `app/.../engine/impl/ProcessFfmpegEngine.kt` | Remove `closeSafParameter`, add 100 ms pause |
| `app/.../engine/FfmpegKitApiCheck.kt` | Remove stray `closeFFmpegPipe(saf)` call |
| `app/.../domain/merger/SafScope.kt` | **DELETE** |
| `app/.../domain/merger/Merger.kt` | Remove SafScope usage; SAF paths acquired directly |
| `AI/ENGINE.md` | §2 update: document correct close API (none needed), add §17 rule |
| `AI/KNOWN_ISSUES.md` | Add K-013 |
| `AI/CHANGELOG.md` | Add Unreleased BUG FIX entry |
| `AI/tasks.md` | Add T-111 |

New test files:

| File | Type |
|---|---|
| `app/src/androidTest/.../SafLifecycleTest.kt` | Instrumented |
| `app/src/test/.../ProcessFfmpegEngineCloseTest.kt` | JVM unit |

## Tests added/updated

1. **MergerArgvTest.kt** — existing; verify argv content unchanged after SafScope removal.
2. **SafLifecycleTest.kt** (new, instrumented):
   - Stage 3 small fixture MKV files via FileProvider.
   - Run `Merger.runMerge`.
   - Assert merge output is non-zero-size.
   - Assert tombstone count before == tombstone count after.
   - Run 2× to catch lifecycle leaks.
3. **ProcessFfmpegEngineCloseTest.kt** (new, JVM):
   - Verify `closeFFmpegPipe` is never called during a mock execute.
   - Verify no reference to `closeSafParameter` exists in the compiled engine.

## Migration notes

- No version bump (debug build).
- `closeSafParameter` removed from the public `FfmpegEngine` interface — any
  future mock implementations (e.g. `FakeFfmpegEngine` in tests) must be updated.
- If a future AAR version exposes a public `closeParcelFileDescriptor`, we can
  call it in `finally` after `Completed`; update ENGINE.md at that time.

## Rollback plan

1. Revert `FfmpegEngine.kt` to restore `closeSafParameter`.
2. Revert `ProcessFfmpegEngine.kt`.
3. Restore `SafScope.kt` from git history.
4. Revert `Merger.kt` to use `SafScope.use {}`.
5. Revert documentation edits.

## Open questions

None — API surface fully verified from `javap` output before writing this plan.

---

PHASE: 6
STATUS: blocked-pending-approval
NEXT: user reviews AI/plans/2026-06-13-merge-saf-lifecycle.md
NEEDS APPROVAL: yes — implementation start