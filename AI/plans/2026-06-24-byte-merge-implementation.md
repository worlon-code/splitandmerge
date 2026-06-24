# Byte-Merge Implementation Plan
Status: AWAITING APPROVAL

## Problem
Phase 1 (Byte Split) is complete but unreleased. We need to implement Phase 2 (Byte Merge), which reverses the split process. It must detect byte-split parts, reassemble them byte-identically to the original file, verify payloads and hashes, and do so without relying on a sidecar `.split.json` file. It should bypass FFmpeg entirely, streaming payloads in order of their header index directly to the output.

## Proposed change
1. **Create `PartModeDetector` & `PreFlightEvaluator`**:
   - `PartModeDetector` sniffs the first 8 bytes of a URI:
     - `MKVSLICE` if first 8 bytes match "MKVSLICE".
     - `EBML` if first 4 bytes match `0x1A 0x45 0xDF 0xA3`.
     - `OTHER` otherwise.
   - `PreFlightEvaluator` performs checks on headers of a selected part set (completeness, contiguity, format version, duplicate detection, truncation, session match) and returns a typed `PreFlightResult`.
2. **Create `TransportMerger`**:
   - Streams payloads in `partIndex` order using a buffer.
   - Computes per-part SHA-256 and compares to the trailer.
   - Computes overall running SHA-256 and compares to the last part's `wholeFileSha256`.
   - Restores the original filename from the last part's trailer.
   - Cleans up (deletes) the output file if cancelled or pre-completion failure occurs.
   - Keeps the file and marks the job failed if a post-write verification failure occurs (hash/size mismatch).
   - Reuses `OutputFolderValidator` for storage space pre-flight validation.
3. **Branch Wiring in `Merger.kt`**:
   - Intercepts selected parts upfront, sniffs magic using `PartModeDetector`.
   - If `MKVSLICE` is detected, routes to `TransportMerger.runMerge`.
   - Otherwise, falls back to the existing FFmpeg structural merge.
4. **Merge UI Integration**:
   - Update `MergeOrderViewModel` to use `PartModeDetector` to skip `ffprobe` on byte-split parts, and to run `PreFlightEvaluator`.
   - Update `MergeOrderScreen` to show a "Byte Merge Mode" chip/indicator and display precise pre-flight block reasons.

## Files touched
- `app/src/main/kotlin/com/splitandmerge/mkvslice/domain/merger/PartModeDetector.kt` [NEW]
- `app/src/main/kotlin/com/splitandmerge/mkvslice/domain/merger/PreFlightEvaluator.kt` [NEW]
- `app/src/main/kotlin/com/splitandmerge/mkvslice/domain/merger/TransportMerger.kt` [NEW]
- `app/src/main/kotlin/com/splitandmerge/mkvslice/domain/merger/Merger.kt` [MODIFY] (Branch wiring, inject detector/merger)
- `app/src/main/kotlin/com/splitandmerge/mkvslice/ui/mergeorder/MergeOrderViewModel.kt` [MODIFY] (Integrate pre-flight evaluation and detection)
- `app/src/main/kotlin/com/splitandmerge/mkvslice/ui/mergeorder/MergeOrderScreen.kt` [MODIFY] (Show Byte Merge chip & specific errors)

## Tests added/updated
- `PartModeDetectorTest.kt` [NEW] (Validate magic-sniffing and full pre-flight evaluation table)
- `TransportMergerTest.kt` [NEW] (Validate happy-path merge, per-part/whole-file SHA verification failures, space pre-flight failure, cancel cleanup, and full round-trip verification)

## Migration notes
None (v5 schema was already registered in Phase 1).

## Rollback plan
Revert modifications to `Merger.kt` and UI classes, and delete the new domain files.

## Open questions
None.
