# Fix Merge SAF Paths & Validation (Phase 6)

## Problem
The FFmpeg concat demuxer fails with "Protocol 'saf' not on whitelist" and "Invalid argument". This happens because `saf:N.mkv` virtual descriptors are passed into `concat.txt`, which FFmpeg cannot resolve directly. Furthermore, there are underlying issues such as missing pre-merge validation (allowing codec mismatches to crash late), incorrect schema usage (storing CSV lists in `manifestPath`), and inaccurate storage pre-flight checks (measuring the destination rather than `cacheDir`).

## Proposed change
This plan covers five key fixes required for Phase 6 merge robustness:

### 1. Resolve SAF tree URIs to real filesystem paths
**Goal:** Replace `saf:N.mkv` with actual filesystem paths (e.g. `file '/storage/emulated/0/...part001.mkv'`) in the `concat.txt` manifest.
- **`platform/saf/PathResolver.kt`**: Add a robust method `fun resolveTreeUriToRealPath(uri: Uri): String?` that attempts to map SAF DocumentFile representations to their real absolute paths.
- **`domain/merger/MergeListWriter.kt`**: Update to write real absolute paths to `concat.txt` instead of virtual SAF URIs.

### 2. Restore Canonical Argv
**Goal:** Maintain standard FFmpeg argv without relying on insecure or fragile workarounds.
- Do NOT add `-protocol_whitelist concat,file,crypto,data,saf`.
- Rely entirely on the real absolute filesystem paths resolved in step 1.

### 3. Implement `MergeValidator`
**Goal:** Fail fast in the UI if parts are incompatible, rather than crashing in the native FFmpeg layer.
- **`domain/merger/MergeValidator.kt`** [NEW]: Implement a class that uses `ProcessFfprobeEngine` to parse metadata from all parts and ensure uniform codecs, resolutions, and stream layouts.
- **`domain/merger/Merger.kt`**: Invoke `MergeValidator` before assembling the concat manifest.
- **`presentation/merge/MergeOrderViewModel.kt`**: Catch validation exceptions and display clear error states to the user.

### 4. Schema Hygiene (`JobEntity`)
**Goal:** Stop abusing `manifestPath` for storing transient CSV part lists.
- **`data/db/JobEntity.kt`**: Ensure `manifestPath` strictly points to the actual manifest text file.
- **`data/repository/JobRepository.kt`**: Refactor related logic to query the actual source parts table/relations instead of parsing CSVs from `manifestPath`.

### 5. Storage Pre-flight Check Refactor
**Goal:** Ensure we check for space accurately.
- **`domain/merger/StorageValidator.kt`** (or relevant pre-flight component): Update logic to check free space against `context.cacheDir` rather than the target destination volume, since `FFmpegKit` processes files via the cache directory under certain conditions.

## Files touched
#### [NEW] `app/src/main/kotlin/com/splitandmerge/mkvslice/domain/merger/MergeValidator.kt`
#### [MODIFY] `app/src/main/kotlin/com/splitandmerge/mkvslice/platform/saf/PathResolver.kt`
#### [MODIFY] `app/src/main/kotlin/com/splitandmerge/mkvslice/domain/merger/MergeListWriter.kt`
#### [MODIFY] `app/src/main/kotlin/com/splitandmerge/mkvslice/domain/merger/Merger.kt`
#### [MODIFY] `app/src/main/kotlin/com/splitandmerge/mkvslice/presentation/merge/MergeOrderViewModel.kt`
#### [MODIFY] `app/src/main/kotlin/com/splitandmerge/mkvslice/data/db/JobEntity.kt`
#### [MODIFY] `app/src/main/kotlin/com/splitandmerge/mkvslice/data/repository/JobRepository.kt`

## Tests added/updated
- Add unit tests for `PathResolver.kt` verifying correct mapping for common external storage paths.
- Add unit tests for `MergeValidator.kt` mocking ffprobe responses for matching and mismatching inputs.

## Migration notes
- If schema updates require a Room migration, a new migration version will be added to ensure existing `JobEntity` tables are correctly updated without losing job history.

## Rollback plan
- Revert the `PathResolver` integration in `MergeListWriter` if real path extraction is unstable on certain OEM devices.
- Drop `MergeValidator` checks if ffprobe parsing becomes a performance bottleneck for large merges.

## Open questions
> [!IMPORTANT]
> Is there a specific Room DB migration strategy required for fixing the `JobEntity.manifestPath` CSV storage, or should we just clear pending jobs if the schema is updated?
