# 04 — Merge Algorithm

The merge is shorter than the split, but has its own gotchas.

## 4.1 The two FFmpeg ways to "merge" — pick the right one

| Method | Re-encode? | Works for our case? | Notes |
|---|---|---|---|
| **`concat` demuxer** (`-f concat -i list.txt -c copy`) | No | ✅ Use this | Requires identical codec params across parts. We control that. |
| `concat` filter (`-filter_complex concat=…`) | Yes | ❌ | Re-encodes everything. Slow, lossy. |
| `concat` protocol (`concat:a.mkv|b.mkv`) | No | ⚠️ Only for MPEG-TS / a few formats | Doesn't work cleanly for MKV. |

**We use the demuxer, always.**

## 4.2 The command

```bash
ffmpeg -hide_banner -y \
  -f concat -safe 0 \
  -i mergelist.txt \
  -map 0 -c copy \
  -avoid_negative_ts make_zero \
  -f matroska \
  output.mkv
```

`mergelist.txt` content:

```
file '/storage/emulated/0/Movies/MyMovie.S01E01.part001.mkv'
file '/storage/emulated/0/Movies/MyMovie.S01E01.part002.mkv'
file '/storage/emulated/0/Movies/MyMovie.S01E01.part003.mkv'
```

- `-safe 0` allows absolute paths in `list.txt`.
- `-map 0` ensures all streams (video, every audio, every subtitle) propagate.
- `-c copy` = lossless.
- `-avoid_negative_ts make_zero` resets PTS at the start; concat then increments naturally.

## 4.3 Reading parts via SAF (the Android twist)

FFmpeg's `concat` demuxer reads files **by path**, but on Android with SAF we typically have `content://` URIs. Two options:

### Option A (recommended): persist the parts to a real path

If the user picks a real folder with `ACTION_OPEN_DOCUMENT_TREE` *that maps to a real filesystem path* (most internal storage and SD-card tree URIs do), FFmpeg can read the file paths via `/storage/emulated/0/...` directly. We resolve the URI → path with helpers like `DocumentFile.getUri()` + `MediaStore` (or use SAF's openFileDescriptor and pipe).

### Option B: open each part as a `pipe:` source

Cleaner for SAF, but `concat` demuxer **cannot** read multiple pipes well. We would have to chain the parts via a pre-concatenated FIFO, which is fragile on Android.

### Option C (chosen): **pre-flight copy / pass-through**

We build the `mergelist.txt` with **path-resolved entries** from the SAF tree URI. If a part's path can't be resolved (rare; e.g. cloud-backed providers), we fail with a clear message: "Please move the parts to local storage before merging."

This keeps us aligned with Play Store policy (no `MANAGE_EXTERNAL_STORAGE`) while still using FFmpeg's straightforward demuxer.

## 4.4 Pre-merge validation

Before running ffmpeg, we run `ffprobe` on every part and compare:

```kotlin
fun validateForMerge(parts: List<File>): MergeIssue? {
    val probes = parts.map { ffprobe(it) }
    val ref = probes.first()
    probes.forEachIndexed { i, p ->
        if (p.video.codec != ref.video.codec ||
            p.video.width != ref.video.width ||
            p.video.height != ref.video.height ||
            p.video.pixFmt != ref.video.pixFmt ||
            p.audio.map { it.codec } != ref.audio.map { it.codec } ||
            p.subs.map { it.codec }  != ref.subs.map { it.codec }) {
            return MergeIssue.MismatchAt(i, …)
        }
    }
    return null
}
```

If parts came from the same split job (manifest match), this is guaranteed to pass. If the user mixes parts from different sources, we detect it and refuse.

## 4.5 Use the manifest if present

When the user picks any single part file from a split, we look in the same folder for `*.split.json`. If found, we:

1. Auto-discover the other parts (named `*.partNNN.mkv`).
2. Verify all parts are present (parts 1..N, none missing).
3. Show the user a one-tap "Merge all" option pre-ordered correctly.
4. After merge, optionally compare the round-trip MD5 of the elementary streams against what the manifest recorded for the source.

If no manifest, the user picks parts manually in order.

## 4.6 Merge correctness — what to expect

- **Video:** byte-identical elementary stream. Round-trip MD5 of `0:v:0` matches.
- **Audio:** byte-identical elementary stream. Round-trip MD5 of each `0:a:i` matches.
- **Text subtitles (SRT/ASS):** content identical; cue indices may renumber (purely cosmetic).
- **Bitmap subtitles (PGS/VobSub):** byte-identical raw subpic data; only header timestamps recomputed.
- **Chapters:** preserved from part 1; chapters from later parts are appended with offset (FFmpeg handles automatically).
- **Attachments / fonts:** preserved from part 1 (FFmpeg dedups; if the same font was in every part, we end up with one copy, which is correct).

## 4.7 Edge cases

- **A part is corrupted / truncated.** `ffprobe` validation catches this. Show: "Part 003 fails integrity check; cannot merge."
- **Last part is much smaller.** Normal — that's the leftover after `floor(total / cap)` parts.
- **User reorders parts.** We allow it, but show a warning: "Reordering parts may produce a video that doesn't make narrative sense." Merge will still succeed technically.
- **Partial selection (e.g. parts 1, 2, 4 — skip 3).** We refuse: "Cannot merge non-consecutive parts."
- **MP4 input → MKV merge.** If the user's source was MP4 and the parts are MP4, we still produce MKV out (consistent with our "always MKV out" rule from doc 02). Subtitle types in MP4 are limited; warn upfront.

## 4.8 Cancellation

`SIGINT` to the merge ffmpeg process. Output file `output.mkv.tmp` is deleted; nothing is partially-renamed. Re-running the merge starts clean.
