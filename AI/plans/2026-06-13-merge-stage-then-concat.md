# Plan: Stage-then-concat Merge Fix

## Problem
During a merge operation using the FFmpeg `concat` demuxer, the app crashes with `SIGSEGV` inside `libffmpegkit.so` at `saf_close+96` on the native background thread `dmx0:concat`.

**Root Cause:**
FFmpeg's `concat` demuxer spawns a native background pthread named `dmx0:concat` to read child packets in parallel. When transitioning between input parts, this thread closes the active child input. Because the thread is spawned internally by `libavformat`, it is not attached to the JVM. When `libffmpegkit.so`'s native `saf_close` executes on this unattached thread, its JNI callback `GetEnv()` returns `null` and dereferences it, resulting in a segmentation fault. This crash reproduces universally on all Android user profiles.

Because `closeParcelFileDescriptor` does not exist in `com.antonkarpenko:ffmpeg-kit-min:2.1.0` (only `closeFFmpegPipe` is exposed), we also cannot manually close SAF descriptors.

## Proposed change
We will implement a two-step merge that stages inputs to local cache using Java stream copying, concat-merges them using real paths (using the `file` protocol), and cleans up temporary files in a `finally` block.

### 1. Pre-flight Disk Space Validation
Before staging, we must calculate the required space: `needed = sum(part sizes) * 2`.
- If `available < needed * 1.05`, throw `EngineError.InsufficientStorage(needed, available)`.
- The UI must catch this error and display: *"Merge needs <needed_GB> GB free in app cache. Currently <available_GB> GB available. Free up app cache or move parts to a smaller set."*

### 2. Step 1: Stage Parts to Local Cache
- Stream each input part from its `content://` URI to `cacheDir` as a temporary `.mkv` file.
- Use Java `FileInputStream` / `FileOutputStream` with a standard 8 KB buffer to keep heap usage minimal.
- Do NOT use FFmpeg or JNI for this step.

### 3. Step 2: Concat Using Real Absolute Paths
- Build `concat.txt` using the real absolute paths (no `saf:` references).

### 4. Step 3: Run Canonical Merge Argv on Staged Files
Run the merge command using the `file` protocol:
```text
-hide_banner -y
-f concat -safe 0
-i <concat.txt path>
-map 0 -map 0:t?
-c copy
-avoid_negative_ts make_zero
-f matroska
<cache>/merge_tmp.mkv
```
*(Drop `-protocol_whitelist` as children are standard files).*

### 5. Step 4 & 5: Save Output and Clean Up
- Copy `merge_tmp.mkv` to the output destination.
- Always delete `staged_partN.mkv`, `concat.txt`, and `merge_tmp.mkv` from the cache directory in `finally{}`.

### 6. Progress Reporting
- **Phase A (Staging):** Map 0% to 50% based on bytes staged vs total expected size.
- **Phase B (Concat):** Map 50% to 100% based on FFmpeg's `time=` output vs total expected duration.

## Files touched (paths + intent)

#### [MODIFY] `app/src/main/kotlin/com/splitandmerge/mkvslice/domain/merger/Merger.kt`
- Replace virtual `saf:` parameter setup with staging copies in cache.
- Implement the 2× disk space pre-flight validation.
- Implement always-run `finally` block to purge cache files.
- Emit staging copy progress updates.

#### [MODIFY] `app/src/main/kotlin/com/splitandmerge/mkvslice/engine/FfmpegEngine.kt`
- Remove `getSafParameter` / `closeSafParameter` from the public interface.

#### [MODIFY] `app/src/main/kotlin/com/splitandmerge/mkvslice/engine/impl/ProcessFfmpegEngine.kt`
- Remove `getSafParameter` override.

#### [DELETE] `app/src/main/kotlin/com/splitandmerge/mkvslice/domain/merger/SafScope.kt`
- Delete the file entirely if it remains.

## Tests added/updated

1. **`MergerArgvTest`** (Unit):
   - Assert the exact command line construction (ensure no `-protocol_whitelist` and that inputs are local file paths).
2. **`MergerStagingTest`** (Instrumented):
   - Stage 3 small dummy MKV fixtures via `FileProvider`.
   - Run `Merger.runMerge` and verify it runs successfully.
   - Assert that no native tombstone is created.
3. **`MergerCleanupTest`** (Unit):
   - Verify that staged files, `concat.txt`, and temporary files are completely purged on success as well as when an error/exception is thrown.
4. **`PreflightStorageTest`** (Unit):
   - Mock directory free space to assert that the 2× pre-flight check correctly throws `EngineError.InsufficientStorage` when space is tight.

## Migration notes
- None.

## Rollback plan
- Revert changes to the SAF parameter flow if staging copies introduce unforeseen I/O bottlenecks.

## Open questions
- None.
