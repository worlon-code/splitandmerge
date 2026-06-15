# v0.0.7 Candidate — Merge Disk Optimizations

**Status: DRAFT — do NOT implement. No code. No tests. Review only.**
**Target: v0.0.7**
**Date: 2026-06-13**

---

## Context

The current stage-then-concat workflow uses cache disk equal to ~2× total input size
(staged copies + FFmpeg `merge_tmp.mkv`) plus a full SAF copy-out at the end.
For a 25.68 GB 3-part merge this means ~51 GB peak cache consumption and a 7-minute
SAF copy-out step at the end.

Three targeted optimizations can reduce peak disk and eliminate the copy-out entirely
for users whose media lives on emulated storage.

---

## Optimization 1 — Real-path fast-path for OUTPUT

### Problem
After FFmpeg concat finishes, `merge_tmp.mkv` is copied byte-by-byte to the SAF output
URI via `openOutputStream`. For a 25 GB file this is ~7 minutes of pure I/O that
touches the same bytes twice.

### Proposed approach
If the user's output tree URI resolves to a real path under `/storage/emulated/0/...`
(or any path accessible as a `File`), pass that absolute path directly as the FFmpeg
output argument:

```
ffmpeg … -f matroska /storage/emulated/0/Movies/MyFolder/merged.mkv
```

This eliminates `merge_tmp.mkv` entirely and cuts peak disk from 2× to 1× input size.

### How to detect real path
```kotlin
fun Uri.toRealPathOrNull(context: Context): String? {
    if (scheme == "file") return path
    val docId = DocumentsContract.getTreeDocumentId(this)
    // Split docId by ":", e.g. "primary:Movies/MyFolder"
    val (volume, relative) = docId.split(":").let { it[0] to it.getOrElse(1) { "" } }
    return if (volume == "primary") "/storage/emulated/0/$relative" else null
}
```

Guard: if `File(realPath).canWrite()` returns false (e.g. SD card), fall back to
the existing `merge_tmp` + SAF copy-out path.

### Risks
- `DocumentsContract.getTreeDocumentId` API level requires API 21+. Already satisfied
  (minSdk 26).
- Path may not be stable across reboots on certain OEMs (Samsung Secure Folder). Must
  also check `Build.MANUFACTURER` is not in the known-broken set from K-013 analysis.
- SD-card volumes use `cardXXXX:` volume names — do not attempt real-path on these.

---

## Optimization 2 — Real-path fast-path for INPUTS

### Problem
Inputs are staged from their content URIs to `staged_part_N.mkv`. For inputs that
already live on emulated storage this is a redundant copy.

### Proposed approach
Before staging each input part, attempt `contentUri.toRealPathOrNull(context)`.
If successful and `File(realPath).canRead()` is true, write that real path directly
into `concat.txt` and skip the staging copy entirely for that part.

```
# concat.txt when real-path resolves:
file '/storage/emulated/0/Movies/Part1.mkv'
file '/storage/emulated/0/Movies/Part2.mkv'
# staged only when real path not available:
file '/data/user/0/com.splitandmerge.mkvslice/cache/staged_part_2.mkv'
```

### Risks
- SAF URI is not the same as `MediaStore` URI. `DocumentsContract.getDocumentId` may
  return a path-relative document ID only, not a volume-relative path. Requires careful
  parsing per OEM.
- Must keep staging available as fallback for cloud-backed inputs (K-002).
- `-safe 0` flag is already set in the concat command — accepts absolute paths.

---

## Optimization 3 — Sequential staging cleanup

### Problem
Today all staged parts are kept until the `finally{}` block at the end of `runMerge`.
For a 3-part 25 GB merge, peak cache = 25 GB (staged) + 25 GB (merge_tmp) = 50 GB.

### Proposed approach
Track which input part FFmpeg is currently reading by parsing the `time=` progress
from its log. When `time_seconds` crosses the known end-PTS of part N (sum of
durations of parts 0..N−1), delete `staged_part_N.mkv` immediately.

```
end_pts[0] = duration[0]
end_pts[1] = duration[0] + duration[1]
...
```

The FFmpeg progress callback (log lines) already emits `time=HH:MM:SS.xx`.
A watch-loop in a separate coroutine (or in the existing log callback) can compare
`currentTimeSec > endPts[lastDeletedPart]`.

### Risks
- FFmpeg does not guarantee monotonically increasing `time=` on error or seek.
  Must debounce: only trigger delete when `time=` stays above `end_pts[N]` for 2+ seconds.
- During the delete window, if FFmpeg seeks back (rare but possible with damaged files),
  it will get a file-not-found error and fail. Need to measure probability in practice.
- Does not help if real-path fast-path (Opt 2) is used (no staged files).

---

## Estimated Peak Disk After All Three Optimizations

| Scenario | Current | After Opt 1+2+3 |
|----------|---------|-----------------|
| All inputs + output on emulated storage | 2× input | ~0 extra (direct paths) |
| Inputs on emulated, output on ext SD | 2× input | 1× input (merge_tmp gone, staging skipped) |
| Some inputs not resolvable | 2× input | ~1.5× input (skip staging for resolvable parts) |
| All inputs on SD / cloud | 2× input | 2× input (no change, all fall back) |

---

## Sequencing

These three optimizations are independent and can be implemented in any order.
Recommended order:

1. **Opt 2** (skip staging) — highest impact per complexity unit; self-contained.
2. **Opt 1** (direct FFmpeg output) — eliminates copy-out; requires real-path helper.
3. **Opt 3** (sequential cleanup) — bounds peak disk for the fallback path; most risky.

---

## Open Questions

1. Should `toRealPathOrNull` be a `ContentResolver`-based approach or a pure
   `DocumentsContract` string-split? The latter is faster but less robust on OEM forks.
2. Is it acceptable to run Opt 3 only when staging at least 2 parts? (Single-part
   staging is effectively a rename + concat — trivial peak disk.)
3. Should the fallback to staging be silent (just use the slower path) or should
   it emit a `Timber.i("MERGE")` diagnostic?

---

## Status

```
STATUS: DRAFT
TARGET: v0.0.7
NEEDS APPROVAL: yes, before any code is written
```
