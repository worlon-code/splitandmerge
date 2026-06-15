# Merge SAF Descriptor v2 Fix Plan

## Problem
The Phase 6 merge still fails on devices using cross-user profiles (like dual-app/work profiles) and specific OEM external storage. `PathResolver.resolveTreeUriToRealPath` returns null for these cross-user `content://` URIs, causing `Merger.kt` to fall back to injecting raw `content://` URIs directly into FFmpeg's `concat.txt`. FFmpegKit then correctly rejects the `content` protocol because it isn't whitelisted and FFmpeg's `concat` demuxer cannot directly read Android ContentResolver descriptors without the internal `saf:` piping provided by FFmpegKit.

## Proposed change
To ensure universal support across profiles and cloud providers, we will abandon fighting for real absolute paths in `concat.txt` and return to utilizing FFmpegKit's native `saf:` file descriptor mappings with a correctly whitelisted protocol configuration and canonical arguments. We will also transition the engine to use array-based argument passing to circumvent parsing faults.

### 1. Use FFmpegKit's SAF parameter helper for inputs
**Goal:** Map input `content://` URIs safely to FFmpegKit's `saf:N.mkv` descriptors.
- **`domain/merger/Merger.kt`**: Replace `resolveTreeUriToRealPath` with `FFmpegKitConfig.getSafParameterForRead(context, Uri.parse(partUri))`.
- Wrap saf-id acquisition and closing in an `AutoCloseable` `SafScope` so that `closeParcelFileDescriptor` is called for every acquired ID, even on exception. Use `SafScope().use { ... }` in `Merger.runMerge` (A3).
- **Error Handling (Q1)**: If `getSafParameterForRead` returns null, REFUSE the merge and throw `EngineError.InputUnreadable(uri, "saf parameter unavailable")`.

### 2. Add `-protocol_whitelist` before `-i`
**Goal:** Allow the concat demuxer to legally read the child `saf:N.mkv` descriptors.
- **`domain/merger/Merger.kt`**: Update `cmd` to inject `-protocol_whitelist file,crypto,data,saf` immediately before the `-f concat` / `-i <concat_file>` argument cluster.
- **`MergeListWriter` (A2)**: Create a helper to write the `concat.txt`. Crucially, lines for saf children MUST be unquoted (e.g. `file saf:18.mkv`).

### 3. Restore Missing Canonical Flags
**Goal:** Ensure canonical merge stream mapping to prevent zero-ts and codec drop failures per `AI/ENGINE.md §4`.
- **`domain/merger/Merger.kt`**: Append `-map 0`, `-map 0:t?`, `-avoid_negative_ts make_zero`, and `-f matroska` to the builder.

### 4. Switch to Array Overload of FFmpegKit
**Goal:** Avoid unpredictable tokenization by whitespace and prevent unwanted SAF auto-substitution bugs within FFmpegKit.
- **`engine/impl/ProcessFfmpegEngine.kt`**: Inspect `ffmpeg-kit-min:2.1.0` to confirm the exact public symbols available for `getSafParameterForRead`, `closeParcelFileDescriptor`, and the array-form async execute (A1). Document the chosen symbols in `AI/ENGINE.md §2` before coding.

## Files touched
#### [MODIFY] `app/src/main/kotlin/com/splitandmerge/mkvslice/domain/merger/Merger.kt`
#### [NEW] `app/src/main/kotlin/com/splitandmerge/mkvslice/domain/merger/SafScope.kt`
#### [NEW] `app/src/main/kotlin/com/splitandmerge/mkvslice/domain/merger/MergeListWriter.kt`
#### [MODIFY] `app/src/main/kotlin/com/splitandmerge/mkvslice/engine/impl/ProcessFfmpegEngine.kt`
#### [MODIFY] `app/src/main/kotlin/com/splitandmerge/mkvslice/platform/saf/PathResolver.kt` (Add Javadoc only)
#### [MODIFY] `AI/ENGINE.md`
#### [MODIFY] `AI/KNOWN_ISSUES.md`
#### [MODIFY] `AI/CHANGELOG.md`

## Tests added/updated
- **`MergeListWriterTest`**: Assert that `concat.txt` lines are exactly `file saf:N.mkv` and unquoted (A2).
- **`MergerArgvTest`**: Assert the exact argv construction, ensuring the protocol whitelist, map flags, and avoid_negative_ts are strictly present in correct order.
- **`EngineSmokeTest`**: Add `argvWithSpacesInPath` to ensure paths with spaces are preserved successfully by the array overload.
- **`MergerSafConcatTest`**: (Instrumented) Provide 3 small test fixtures generated in the app-private directory via `FileProvider` to yield `content://` URIs. Invoke `Merger.runMerge` and assert success and absence of red-flag log messages. Also, add an instrumented test variant to run under user 95 if present (Q2).
- Ensure existing `MergeValidatorTest` remains green.

## Migration notes
None required. This adjusts the FFmpeg execution path solely; no DB or architecture boundary alterations.

## Rollback plan
Revert `Merger.kt` and `ProcessFfmpegEngine.kt` to the Phase 6 baseline if the array execution signature introduces regression or linkage errors in the `ffmpeg-kit-min:2.1.0` AAR.

## Open questions
Resolved.

Status: APPROVED-WITH-AMENDMENTS
