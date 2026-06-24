# Engine — FFmpeg & Transport Integration

> The heart of the app. Two distinct engines now coexist:
> **FFmpeg Structural** (default — keyframe-aware, re-mux with `-c copy`) and
> **Transport / Byte-exact** (opt-in — raw byte chop/concat, SHA-256 verified, no FFmpeg).
> The single most-likely-to-break component if we're not careful.

This file is the **operational** complement to
[`analysis/02-TECHNICAL-DEEP-DIVE.md`](../analysis/02-TECHNICAL-DEEP-DIVE.md),
[`analysis/03-SPLIT-ALGORITHM.md`](../analysis/03-SPLIT-ALGORITHM.md),
[`analysis/04-MERGE-ALGORITHM.md`](../analysis/04-MERGE-ALGORITHM.md), and
[`analysis/07-LIBRARIES-AND-DEPENDENCIES.md`](../analysis/07-LIBRARIES-AND-DEPENDENCIES.md).

Read those files for the *why*. This file is the *how*.

## 1. Choice of FFmpeg artefact

Picked at the start of Phase 4 of the roadmap. Per Q15 we use a **maintained
community fork** of FFmpegKit. The agent must:

1. Survey the latest publish dates on Maven Central / GitHub of the candidate
   forks.
2. Confirm 16 KB page-size support (Android 15 mandate).
3. Confirm `arm64-v8a` artefact is available.
4. Confirm LGPL build (no `-gpl` flag, no x264, no libfdk_aac).
5. Pin the version in `gradle/libs.versions.toml`.
6. Smoke-test on emulator + real device with `-version` and a 5-second
   `-c copy` against `app/src/androidTest/assets/fixture.mkv`.

If no maintained fork is acceptable, fall back to vendoring pre-built
`.so` files under `app/src/main/jniLibs/arm64-v8a/`. We **never** enable
GPL flags.

The exact artefact is recorded once chosen in:

```
gradle/libs.versions.toml  → [libraries] ffmpeg = { module = "<group>:<artifact>", version.ref = "ffmpeg" }
                              [versions]  ffmpeg = "<exact version>"
```

## 2. Engine API

The Compose UI never calls FFmpeg directly. It goes through three thin
interfaces in `engine/`:

```kotlin
// engine/FfmpegEngine.kt
interface FfmpegEngine {
    suspend fun version(): String
    fun execute(args: List<String>): Flow<EngineEvent>
    suspend fun cancel(token: String)
}

sealed class EngineEvent {
    data class Started(val token: String) : EngineEvent()
    data class Progress(val timeSeconds: Double, val speed: Double) : EngineEvent()
    data class Stderr(val line: String) : EngineEvent()
    data class Completed(val exitCode: Int) : EngineEvent()
}

// engine/FfprobeEngine.kt
interface FfprobeEngine {
    suspend fun probe(uri: String): ProbeResult
    suspend fun keyframes(uri: String): List<Double>
}

// engine/ProgressParser.kt — pure Kotlin, fully unit-tested
object ProgressParser {
    fun parseLine(line: String): EngineEvent.Progress?
}
```

`FfmpegEngine.execute(args)` returns a cold `Flow` that emits
`Started → many Stderr/Progress → Completed`. Cancellation of the flow's
collector triggers `cancel(token)` which sends `SIGINT` to the child process.

Implementation lives in `engine/impl/`. There is one implementation today,
`ProcessFfmpegEngine`, which calls into the chosen library and parses
stderr. A `StubFfmpegEngine` exists for the JVM unit tests.

## 3. Split — exact FFmpeg command

```bash
ffmpeg -hide_banner -y \
  -ss <part_start_seconds>           \   # fast input seek (BEFORE -i)
  -i <inputUri or path or pipe:>     \
  -to <part_end_seconds>             \   # absolute end time, used with -copyts
  -map 0                             \   # all video / audio / subtitle streams
  -map 0:t?                          \   # all attached fonts (optional)
  -c copy                            \   # NO re-encoding, ever
  -avoid_negative_ts make_zero       \   # part PTS starts at 0 (subs included)
  -copyts                            \   # preserve original ts pre-rewrite
  -map_metadata 0                    \
  -map_chapters 0                    \
  -f matroska                        \   # always MKV when subs need it
  <outputDir>/<base>.partNNN.mkv
```

Notes:
- `-ss` BEFORE `-i` = fast seek; FFmpeg lands on the keyframe at-or-before
  the value. We always pass an exact keyframe.
- We use **`-to`**, not `-t`, with `-copyts`, so logic is "from this PTS to
  that PTS" rather than "for this duration".
- `-c copy` is the only flag that matters for losslessness. Never replace.
- `-f matroska` is enforced when the input is `.mp4` / `.avi` / `.mov` /
  `.ts` AND has bitmap subtitles (PGS / VobSub). Otherwise we keep input's
  container.
- Subtitle timestamp shift is automatic via `-avoid_negative_ts make_zero`.
  See [SUBTITLES.md](SUBTITLES.md).

## 4. Merge — exact FFmpeg command & Disk Optimization

To bypass JNI crash/segmentation faults (K-013) that occur when FFmpeg's background concat demuxer thread (`dmx0:concat`) internally closes SAF descriptors, a **Stage-then-concat** workflow is used with the following disk optimizations:

1. **Fast-path Staging & Move Skip:** If the input part SAF URIs and/or output directory tree URI resolve to physical file system paths (e.g. on `/storage/emulated/0/...`), staging copies and temporary output caching are bypassed. FFmpeg reads/writes directly on these physical paths.
2. **Staging:** For input URIs that do not resolve to physical paths, they are copied to standard local files in the app's cache directory (e.g. `staged_part_0.mkv`, `staged_part_1.mkv`).
3. **Sequential Cache Cleanup:** To minimize peak cache size, during the `CONCAT` phase, when the linear progress reaches beyond a part's boundary plus a 5-second safety margin, its local staged cache file is deleted immediately. The last staged part is deleted in the `finally` block.
4. **Concat:** Perform the merge using standard file paths and write the output to a temporary cache file (`merge_tmp.mkv`) if output staging is needed.
5. **Move:** Copy the output file from cache to the final SAF destination directory if output staging was used.
6. **Cleanup:** Always delete any remaining staged files and temporary output file in a `finally` block.

The FFmpeg command runs using the `file` protocol:

```bash
ffmpeg -hide_banner -y \
  -f concat -safe 0                  \   # demuxer mode
  -i concat.txt                      \   # absolute local file paths inside
  -map 0 -c copy                     \
  -avoid_negative_ts make_zero       \
  -f matroska                        \
  <cache>/merge_tmp.mkv
```

`concat.txt` content (lines, one per part):
```
file /data/user/0/com.splitandmerge.mkvslice.debug/cache/staged_part_0.mkv
file /data/user/0/com.splitandmerge.mkvslice.debug/cache/staged_part_1.mkv
…
```

Pre-merge validation runs `ffprobe` over every part and refuses on
mismatched codec / resolution / pixel format / track count. Same-source
parts always match.

## 5. Probe — what we extract

`FfprobeEngine.probe(uri)` returns the `ProbeResult` defined in
[DATA_MODELS.md §1.1](DATA_MODELS.md). Implementation calls:

```bash
ffprobe -v error -hide_banner \
  -show_format -show_streams -show_chapters -show_entries side_data \
  -of json <input>
```

Then parses the JSON via `kotlinx.serialization`. We deliberately avoid
`-show_packets` here — too much output on multi-hour HEVC files. Instead:

`FfprobeEngine.keyframes(uri)` runs:

```bash
ffprobe -v error -hide_banner \
  -select_streams v:0 -skip_frame nokey \
  -show_frames -show_entries frame=pkt_pts_time \
  -of csv=p=0 <input>
```

Output is one timestamp per line. We parse to `List<Double>` and cache it
along with the probe result for the duration of the screen.

## 6. Cut planner

`domain/splitter/CutPlanner.kt` (pure Kotlin, JVM-only, fully unit-tested):

```kotlin
class CutPlanner @Inject constructor() {

    fun plan(
        mode: SplitMode,
        requestedParts: Int?,                  // required for EXACT_PARTS / BOTH
        targetCapBytes: Long,                  // 9 GB by default
        ceilingBytes: Long,                    // 9.5 GB; never exceeded
        durationSeconds: Double,
        totalSizeBytes: Long,
        keyframes: List<Double>,
    ): CutPlan {
        require(durationSeconds > 0)
        require(keyframes.first() == 0.0 || keyframes.isNotEmpty())

        val cuts = when (mode) {
            SplitMode.EXACT_PARTS   -> exactParts(requestedParts!!, durationSeconds, keyframes)
            SplitMode.SIZE_CAP_ONLY -> sizeCap(durationSeconds, totalSizeBytes, ceilingBytes, keyframes)
            SplitMode.BOTH          -> both(requestedParts!!, durationSeconds, totalSizeBytes, ceilingBytes, keyframes)
        }
        return finalise(cuts, durationSeconds, totalSizeBytes, mode, requestedParts, targetCapBytes, ceilingBytes)
    }

    private fun nearestKeyframeAtOrBefore(t: Double, kf: List<Double>): Double {
        val idx = kf.binarySearch(t)
        return if (idx >= 0) kf[idx] else kf[(-idx - 1 - 1).coerceAtLeast(0)]
    }

    // ... see analysis/03-SPLIT-ALGORITHM.md §3.3 for the full algorithm
}
```

Key invariants tested in `CutPlannerTest`:

- `plan(EXACT_PARTS, n=N).cuts.size == N - 1`.
- `plan(SIZE_CAP_ONLY).expectedPartSizes.all { it <= ceilingBytes }`.
- `plan(BOTH)`: cap wins when equal-parts would exceed it.
- All cuts are members of the keyframe list.
- Empty-keyframe fallback returns a single cut at duration / 2.

## 7. Verification + auto-resplit

After all parts written:

```kotlin
parts.forEach { p ->
    val sz = saf.length(p.uri)
    if (sz > job.plan.ceilingBytes) {
        // Re-split this part in half at its midpoint keyframe.
        // Rare. Backed by an integration test.
        splitter.resplit(p, halfwayKeyframe(p))
    }
}
```

We always verify with `ffprobe -v error -show_format <uri>` to confirm a
valid file before marking the part DONE.

## 8. Manifest

After every successful split job, we emit `<base>.split.json` next to the
parts. Schema in [DATA_MODELS.md §1.5](DATA_MODELS.md). The manifest
enables one-tap merge from S9 even after a phone reboot.

```kotlin
class Manifest @Inject constructor(private val json: Json) {
    fun write(job: SplitJob, parts: List<PartResult>, dest: ParcelFileDescriptor)
    fun read(src: ParcelFileDescriptor): Manifest
}
```

## 9. Cancellation

```kotlin
override fun execute(args: List<String>): Flow<EngineEvent> = callbackFlow {
    val token = UUID.randomUUID().toString()
    trySend(EngineEvent.Started(token))
    val process = startNative(token, args)              // implementation-specific
    val collector = launch { process.stderr.lineFlow().collect { line ->
        ProgressParser.parseLine(line)?.let { trySend(it) }
        trySend(EngineEvent.Stderr(line))
    }}
    val exit = process.awaitExit()
    collector.cancel()
    trySend(EngineEvent.Completed(exit))
    awaitClose { runCatching { process.signalInt() } } // SIGINT on flow cancel
}
```

A flow collector cancellation = `SIGINT` to FFmpeg. FFmpeg writes a clean
trailer when it can; otherwise the part `.tmp` file stays and is deleted on
next launch.

## 10. Error classification

```kotlin
sealed class EngineError {
    data class InsufficientStorage(val needed: Long, val have: Long) : EngineError()
    data class InputUnreadable(val uri: String, val reason: String) : EngineError()
    data class OutputWritePermission(val uri: String) : EngineError()
    data class CodecMismatch(val partIdx: Int, val detail: String) : EngineError()
    data object Cancelled : EngineError()
    data class Other(val exitCode: Int, val stderrTail: String) : EngineError()
}
```

We classify by parsing the last 200 stderr lines. The classifier is a pure
function; tests in `EngineErrorClassifierTest` against captured stderr
samples for each category.

## 11. Storage check

Before any job:

- **Split jobs:**
  ```kotlin
  val needed = (job.streams.sizeBytes * 1.05).toLong()
  val have   = StatFs(job.outputDirRealPath).availableBytes
  if (have < needed) throw EngineError.InsufficientStorage(needed, have)
  ```
- **Merge jobs:**
  ```kotlin
  val needed = totalSizeRequired * 2
  val available = context.cacheDir.usableSpace
  if (available < needed * 1.05) throw EngineError.InsufficientStorage(needed, available)
  ```

The padding/multiplier accounts for staging requirements and container overhead. We refuse early rather than letting FFmpeg or staging fail mid-write.

## 12. SAF integration

FFmpeg cannot natively read Android `content://` URIs. We use FFmpegKit's SAF protocol integration to bridge this **for split operations only**:

```kotlin
// 1. Get a saf: protocol descriptor
val safPath = FFmpegKitConfig.getSafParameterForRead(context, uri)
    ?: throw EngineError.InputUnreadable(uri.toString(), "saf parameter unavailable")

// 2. Use it in arguments (must enable saf in protocol_whitelist)
val args = listOf("-protocol_whitelist", "file,crypto,data,saf", "-i", safPath, ...)

// 3. DO NOT manually close the descriptor. 
// The native layer manages the lifecycle and automatically calls saf_close 
// internally when FFmpeg finishes tearing down the context.
```

Merge operations **do not** use SAF protocol directly in FFmpeg to avoid native thread JNI crashes (K-013). Instead, they stage files to standard files first and merge using the `file` protocol.

Caveats:
- The `saf:` protocol requires the array-form `FFmpegKit.executeWithArgumentsAsync` to bypass tokenization issues.
- **CRITICAL**: Do NOT call `closeFFmpegPipe` on `saf:` paths. That is for named pipes only. Calling it on SAF paths will cause a double-close SIGSEGV.

## 13. Output staging

We write FFmpeg's output to **app-private cache** first
(`getExternalFilesDir(null)`), then move the file to the SAF output URI on
success. This avoids `pipe:` write quirks and gives us atomic-ish renames.

```kotlin
val tmp = File(cacheDir, "$jobId.partNNN.mkv.tmp")
runEngine(args, output = tmp)
saf.copyAndDelete(tmp, outDir.createDocument("video/x-matroska", finalName))
```

## 14. Concurrency

One job at a time (Q17). The service mutex serialises `execute()` calls.
Two simultaneous reads of the same input are fine; two writes to the same
output path are not — guarded by a per-output `Mutex`.

## 15. Logging

Every FFmpeg invocation logs:

```
ENGINE start  job=<id> part=<n>  args=<argv>
ENGINE stderr <line>
ENGINE prog   t=<seconds> speed=<x.x>
ENGINE done   job=<id> part=<n>  exit=<code>
```

Lines are prefixed `ENGINE` for easy `logcat -s` filtering. We never log
content bytes, only metadata.

## 16. Update path

`engine/` is the single most-likely-to-replace package as the FFmpeg
ecosystem evolves. Plan for:

- v1.1: migrate from chosen fork to in-house FFmpeg build (Gradle task).
- v1.2: add hardware-decoder path for thumbnails (MediaCodec, NOT for split).
- v1.3: re-encode mode for files with too-few keyframes (graceful
  degradation). Out of scope for v1.

## 17. What an agent must NOT do here

- ❌ Add a new flag to the split / merge command without updating this file.
- ❌ Replace `-c copy` with anything that re-encodes.
- ❌ Use the `concat` filter (re-encodes). Demuxer only.
- ❌ Use the `concat:` protocol (broken for MKV).
- ❌ Skip pre-merge validation.
- ❌ Skip post-write verification.
- ❌ Drop the manifest.
- ❌ Pass arbitrary user-supplied strings into the FFmpeg command without
  argv-quoting them. Use `ProcessBuilder.command(List<String>)` form, never
  string interpolation.
- ❌ Call `FFmpegKitConfig.closeFFmpegPipe` on `saf:<id>.<ext>` paths. The SAF descriptor lifecycle is native-managed.
- ❌ Manually close SAF descriptors. Let FFmpegKit handle teardown automatically.

---

## 18. Transport Engine (Byte-exact Split / Merge) — v0.0.12+

A pure-Kotlin, FFmpeg-free byte-chop/concat engine for users who need
**true SHA-256 byte identity** between source and merged output. The FFmpeg
engine remains the default; Transport mode is a **per-job opt-in toggle**
on `SplitConfigScreen`.

### 18.1 Why it exists

FFmpeg concat re-muxes — it regenerates a random Matroska `SegmentUID`,
`WritingApp`, and timestamps. The merged output is NOT byte-identical to the
source even though media streams match. Transport mode chops raw bytes and
binary-concats them back; the output literally IS the original bytes (exact
SHA-256 match). Parts are **not individually playable**.

### 18.2 Key source files

| File | Role |
|---|---|
| `domain/transport/FrameCodec.kt` | 64-byte binary frame header encoder/decoder |
| `domain/transport/TransportSplitter.kt` | Byte-exact splitter: reads SAF → writes N framed parts |
| `domain/merger/PartModeDetector.kt` | Sniffs first 8 bytes (`MKVSLICE` magic) to detect byte parts |
| `domain/merger/PreFlightEvaluator.kt` | Validates session compatibility, contiguity, duplicates |
| `domain/merger/TransportMerger.kt` | Byte-exact merger: streams parts → SAF output with SHA-256 verification |
| `domain/merger/Merger.kt` | Routes to `TransportMerger` when `PartModeDetector` returns `MKVSLICE` |

### 18.3 Frame header (64 bytes, FrameCodec)

Every byte-split part is prefixed with a 64-byte binary header:

```
Bytes  0..7   Magic: 'MKVSLICE' (ASCII, 0x4D4B56534C494345)
Bytes  8..9   Version: 1 (UShort, big-endian)
Bytes 10..11  partIndex (0-based, UShort, big-endian)
Bytes 12..13  totalParts (UShort, big-endian)
Bytes 14..21  payloadOffset (ULong, big-endian) — byte offset of this chunk in original
Bytes 22..29  payloadSize (ULong, big-endian) — bytes in this chunk's payload
Bytes 30..37  originalTotalSize (ULong, big-endian)
Bytes 38..70  padding / reserved (zero-filled)
```

The LAST part also carries a 32-byte per-whole-file SHA-256 trailer written
after the payload (appended, not in the header). Each part's payload SHA-256
is computed per-part and stored in `PartEntity.partSha256`.

### 18.4 Split algorithm (TransportSplitter)

- Buffer size: **512 KB** (no full-file load; streams SAF → SAF).
- Chunking: equal-sized chunks for `EXACT_PARTS`; `ceil(fileSize / cap)` chunks
  for `SIZE_CAP_ONLY`. Last chunk gets the remainder (may be smaller).
- SHA-256 computed **single-pass** during the read loop (both per-part
  `MessageDigest` and whole-file `MessageDigest` update on the same buffer read).
- Part naming: `<base>.part_<NN>_<TT>.mkv` (1-based, zero-padded, e.g. `part_01_03`).
- On cancellation: all written parts are deleted via SAF (`DocumentFile.delete()`).
- `splitFormat = "BYTE"` stored on `JobEntity`.

### 18.5 Merge algorithm (TransportMerger)

- `PartModeDetector` sniffs magic on first selected file. MKVSLICE → `TransportMerger`.
- `PreFlightEvaluator` validates: session identity (`originalTotalSize` + `totalParts`
  identical across all parts), contiguity (`payloadOffset` chain), no duplicates,
  no missing parts, no truncation, no unknown version.
- Output filename restored from the last part's session metadata (original source name).
- Storage check: `OutputFolderValidator` against `originalTotalSize` before creating output.
- Streams all payloads in `partIndex` order to a single SAF output stream; running
  SHA-256 updated on every chunk.
- **Fail-closed**: if any part is missing or pre-flight fails → FAILED, no output file created.
- **Verify-keep**: if the file is fully written but SHA-256 / size mismatches → FAILED,
  output file **KEPT** (never deleted); error message includes the output file path so
  the user can locate it.
- Cancellation mid-merge → output file deleted.

### 18.6 Size cap input (SplitConfigScreen)

The byte-exact size-cap field accepts **decimal input** (e.g. `0.3`, `1.5`):
- Unit selector: **MB / GB** segmented toggle (default MB). `1 MB = 1 048 576 bytes`;
  `1 GB = 1 073 741 824 bytes` (binary, 1024-based throughout).
- Conversion: `BigDecimal(input) × factor`, `FLOOR` rounding, `longValueExact()`.
- Validation: rejects empty, `.`, `abc`, `1.2.3`, `1e3`, `+N`, `-N`, floor-to-zero,
  and Long overflow — Start/Continue disabled with inline error message.
- Keyboard type: `KeyboardType.Decimal` (decimal point permitted).

### 18.7 Detection decision summary

| Signal | Route |
|---|---|
| All selected files have `MKVSLICE` magic + same session | `TransportMerger` |
| Any file lacks magic OR sessions differ | BLOCK — user-facing error |
| Mixed byte + normal parts | BLOCK — must pick one type |
| Missing/extra/duplicate parts | BLOCK — exact error per case |
| Unknown version byte | BLOCK — version not supported |
| All files are valid EBML MKV (structural parts) | FFmpeg structural merge |
| No MKVSLICE magic, no valid EBML | BLOCK — unrecognised format |

### 18.8 What an agent must NOT do in the Transport engine

- ❌ Re-encode, remux, or call FFmpeg in the Transport path — byte copy only.
- ❌ Load an entire part into memory — stream in 512 KB chunks.
- ❌ Delete the output file when the merge completes but verification fails.
- ❌ Accept missing parts — fail closed.
- ❌ Mix MB (binary) and MB (decimal) — stick to binary (1024-based) throughout.
