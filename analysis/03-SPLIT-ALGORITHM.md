# 03 — Split Algorithm

This is the engine. Every other piece (UI, queue, service) wraps this.

## 3.1 Inputs to the algorithm

| Field | Type | Description |
|---|---|---|
| `inputUri` | `content://` URI | from SAF picker |
| `mode` | enum | `EXACT_PARTS` / `SIZE_CAP_ONLY` / `BOTH` |
| `requestedParts` | int? | required when `EXACT_PARTS` or `BOTH` |
| `maxPartBytes` | long | default `9 * 1024^3` = 9 663 676 416 bytes; 0 means no cap |
| `outputDir` | `tree URI` | from SAF folder picker |
| `outputBaseName` | string | e.g. `MyMovie.S01E01` (default: derived from input) |

## 3.2 High-level algorithm

```
1. Probe input
   - duration (s)
   - total size (bytes)
   - bitrate (avg) = size * 8 / duration  (bits/sec)
   - all stream metadata (video kf interval, audios, subs)

2. Get keyframe timestamps (sorted ascending)
   keyframes = ffprobe(... -skip_frame nokey ... v:0)

3. Decide cut points (in seconds, video-stream PTS)
   cutPoints = pickCuts(mode, requestedParts, maxPartBytes,
                        duration, totalSize, keyframes)

4. For each consecutive pair (t_start, t_end):
       run split command -> partN.mkv
   Verify each output:
       size_partN <= maxPartBytes (if cap mode)

5. If verification fails, back off one keyframe and retry that part.
6. Emit JSON manifest (input metadata, parts list, hashes — see 3.6).
```

## 3.3 `pickCuts` — the actual logic

### 3.3.1 EXACT_PARTS mode (no size cap)

```kotlin
fun pickCuts_exactParts(
    duration: Double,
    parts: Int,
    keyframes: List<Double>
): List<Double> {
    val raw = (1 until parts).map { it * (duration / parts) }
    return raw.map { target -> nearestKeyframeAtOrBefore(target, keyframes) }
              .distinct()
              .also { require(it.size == parts - 1) {
                  "Cannot place $parts cuts; not enough keyframes." } }
}
```

`cuts.size == parts - 1` because N parts need N-1 cut points.

### 3.3.2 SIZE_CAP_ONLY mode

```kotlin
fun pickCuts_sizeCap(
    duration: Double,
    totalSize: Long,
    cap: Long,
    keyframes: List<Double>
): List<Double> {
    val avgBytesPerSec = totalSize.toDouble() / duration
    // Slightly conservative: target 95% of cap so VBR spikes don't push us over.
    val capSecondsApprox = (cap * 0.95) / avgBytesPerSec
    val cuts = mutableListOf<Double>()
    var nextCut = capSecondsApprox
    while (nextCut < duration) {
        val kf = nearestKeyframeAtOrBefore(nextCut, keyframes)
        if (cuts.isNotEmpty() && kf <= cuts.last()) break  // safety
        cuts += kf
        nextCut += capSecondsApprox
    }
    return cuts
}
```

After splitting, **measure each output** (the `0.95` factor is heuristic; if a part still exceeds `cap`, we re-split that one part further, see 3.5).

### 3.3.3 BOTH mode (N parts AND ≤ cap)

```kotlin
fun pickCuts_both(...): List<Double> {
    val byParts = pickCuts_exactParts(duration, requestedParts, keyframes)
    // If equal-parts already satisfies the cap, use that (estimated by
    // proportional bitrate; a part covering 1/N of duration ≈ totalSize / N).
    val approxPartSize = totalSize / requestedParts
    return if (approxPartSize <= cap * 0.95) byParts
           else pickCuts_sizeCap(duration, totalSize, cap, keyframes)
}
```

In other words: **the cap wins**. If the user asks for 3 parts but a 90 GB file would yield 30 GB parts, we'll produce 11 parts (each ≤ 9 GB) and clearly tell the user in the UI: *"Requested 3 parts, but 9 GB cap requires 11 parts. Continue?"*

## 3.4 The actual FFmpeg command per part

```bash
ffmpeg -hide_banner -y \
  -ss <part_start_seconds> \
  -i <inputUri or file path or pipe:> \
  -to <part_end_seconds> \
  -map 0 -c copy \
  -avoid_negative_ts make_zero \
  -copyts \
  -map_metadata 0 \
  -map_chapters 0 \
  -movflags +faststart \
  -f matroska \
  <outputDir>/<base>.partNNN.mkv
```

Notes:

- `-ss` *before* `-i` = fast input seek; FFmpeg lands on the keyframe ≤ value. We've already chosen a keyframe so this is exact.
- `-to` (not `-t`) takes an absolute end timestamp, easier to reason about with `-copyts`.
- `-map 0` = include all streams.
- `-c copy` = no re-encode.
- `-avoid_negative_ts make_zero` = first packet PTS in the part starts at 0; **subtitles included**.
- `-copyts` = preserve original timestamps before the rewrite (needed for some subtitle codecs).
- `-map_metadata 0` `-map_chapters 0` = preserve title, language, and chapters that fall in this segment.
- `-f matroska` = always output MKV (we standardise on MKV regardless of input container).
- `+faststart` is mp4-specific; harmless on MKV but we'll drop it.

### Real example for a 50 GB file split at 9 GB cap

After probing: duration = 7200 s, size = 53 687 091 200 B (≈ 50 GiB), avg ≈ 7 456 540 B/s.

- `capSecondsApprox = (9 663 676 416 * 0.95) / 7 456 540 ≈ 1231.4 s`
- Keyframes at … 1228.0, 1230.0, 1232.0 …
- First cut: 1230.0
- Second cut: nearest kf ≤ 2461.4 → 2460.0
- Third cut: nearest kf ≤ 3692.8 → 3692.0
- … up to 7200.0
- Total: 6 cuts = **7 parts**, average ~7.6 GB each.

## 3.5 Verification & re-split fallback

After all parts written:

```kotlin
parts.forEach { p ->
    val sz = p.length()
    if (cap != 0L && sz > cap) {
        // Rare. Re-split p in half; abandon the original.
        splitOnce(p, halfwayKeyframe(p))
    }
}
```

Then run `ffprobe -v error -show_format <part>` to confirm a valid file.

## 3.6 Manifest file

For each split job we emit `<base>.split.json` next to the parts:

```json
{
  "schema": 1,
  "source": {
    "name": "MyMovie.S01E01.4K.mkv",
    "size": 53687091200,
    "duration_seconds": 7200.0,
    "sha256_first_64MB": "….",
    "video": { "codec": "hevc", "width": 3840, "height": 2160, "hdr": "HDR10" },
    "audio": [ {"codec":"truehd","lang":"eng"}, {"codec":"ac3","lang":"jpn"} ],
    "subs":  [ {"codec":"pgs","lang":"eng"}, {"codec":"ass","lang":"eng"} ]
  },
  "parts": [
    { "index":1, "name":"…part001.mkv", "start":0.0,    "end":1230.0, "size":… },
    { "index":2, "name":"…part002.mkv", "start":1230.0, "end":2460.0, "size":… },
    …
  ],
  "ffmpeg_version": "6.x",
  "app_version": "0.0.1"
}
```

**Why a manifest:** the **merge screen** can read it back, so the user doesn't have to manually order parts. Even better: missing a part is detectable. (See doc 04.)

## 3.7 Cancellation & resume

- Each part is written to a `*.partN.mkv.tmp` file, renamed atomically only after FFmpeg returns 0.
- A cancel signal sends `SIGINT` to the FFmpeg process (FFmpeg writes a clean trailer when it can; otherwise the `.tmp` file stays and is deleted on next launch).
- On crash/restart, the queue Room table holds the job state; we resume by retrying any incomplete part (we never partially-write into a final-named file).

## 3.8 Edge cases (handled)

- **Single keyframe at start (extremely long GOP).** Ffmpeg returns at most one keyframe before duration; we error out with a clear message and offer "force re-encode" *only* in v2 (out of scope for v1).
- **Very short videos (< cap).** Just emit a single part = original (or refuse if user requested ≥ 2 parts).
- **Audio-only / video-only files.** Stream copy still works; the `keyframes` list comes from the first stream available.
- **Variable framerate (VFR).** Stream copy preserves it; cuts still align to video keyframes.

## 3.9 Why this is "lossless"

Because we never invoke an encoder. The bytes that came out of the original encoder go straight into the new container. Verifiable:

```bash
ffmpeg -i input.mkv  -map 0:v:0 -c copy -f md5 -
ffmpeg -i merged.mkv -map 0:v:0 -c copy -f md5 -
```

These two MD5s of the elementary video stream are **identical** for a successful split→merge round-trip. (Audio and subtitles too.)
