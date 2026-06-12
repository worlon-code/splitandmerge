# 02 — Technical Deep Dive

This document explains *why* certain choices are forced upon us by the format and codec details. If you skip everything else, read sections 2.3 (keyframes) and 2.5 (stream copy).

## 2.1 Container vs. Codec

A `.mkv` file is a **Matroska container**. Inside it, several *streams* live:

```
input.mkv (container)
├── Video stream     (codec: HEVC / H.264 / AV1 / VP9 …)
├── Audio stream #1  (codec: AC3 / EAC3 / DTS / TrueHD / AAC / Opus …)
├── Audio stream #2  (optional, e.g. commentary)
├── Subtitle stream  (codec: SRT / ASS / PGS / VobSub …)
├── Subtitle stream  (other language)
├── Chapters         (table)
└── Attachments      (e.g. fonts for ASS rendering)
```

The split/merge operation acts at the **container** level. We don't touch codecs. The bytes of each compressed video frame, each compressed audio packet, and each subtitle event are copied unchanged from input to output. That's how 4K HDR / Dolby Vision / Atmos survive untouched.

### Key insight

> **Splitting is ~free CPU-wise.** It is dominated by sequential disk read/write throughput. On NVMe-class internal storage (~1.5 GB/s read, ~800 MB/s write on a Pixel 8 Pro / S24), a 50 GB split is ~1–2 minutes of pure IO. Reality adds FFmpeg parsing overhead but stays in the few-minutes range.

## 2.2 MKV vs. MP4 — for THIS app

| Property | MKV (Matroska) | MP4 (ISO BMFF) |
|---|---|---|
| Native subtitle types | SRT, ASS, PGS, VobSub, WebVTT | `mov_text` only (and PGS as a non-standard ext) |
| Multiple audio tracks | ✅ | ✅ |
| Chapters | ✅ | ✅ |
| Attachments (fonts) | ✅ | ❌ |
| moov/`+faststart` issues for big files | n/a | yes — `moov` atom must be relocated, can need rewrite |
| Segmented streaming | DASH/HLS not needed | works |

**Recommendation:** Output is always **MKV** (regardless of input container). This sidesteps mp4's poor subtitle support and the moov-atom relocation cost on huge files. We can label this clearly in the UI: "Output: .mkv (preserves all streams)."

## 2.3 Keyframes (I-frames) and GOPs — why we *cannot* split anywhere

Modern video codecs (H.264/HEVC/AV1/VP9) compress by referencing earlier frames. A **GOP** (Group of Pictures) starts at an **I-frame** (independent / keyframe) and contains P-frames (predict from previous) and B-frames (predict from previous + future).

```
GOP:  I  B  B  P  B  B  P  B  B  P   I   B  B  P …
      └────────── must include the I to decode any of these ──────────┘
```

If we cut between an I-frame and a P-frame, the next part cannot decode its first ~1–2 seconds (no reference). Symptoms: green/grey frames, glitches, or refusal to play.

**Therefore:** every cut point must coincide with a keyframe in the video stream.

### How we find keyframes

```bash
ffprobe -v error -select_streams v:0 \
  -skip_frame nokey -show_frames -show_entries frame=pkt_pts_time \
  -of csv=p=0 input.mkv
```

This dumps a list of timestamps where keyframes exist. Typical content has a keyframe every **2 seconds** (Blu-ray) up to every **10 seconds** (YouTube/Netflix style "long GOP"). So our cut precision is roughly that interval.

### Practical implication for the 9 GB cap

We can't always hit *exactly* 9.000 GB; we hit "the largest size ≤ 9 GB at a real keyframe", e.g. 8.94 GB. That's fine. The algorithm in doc 03 quantises to keyframes.

## 2.4 Bitrate ≠ constant

For a CBR (constant bitrate) file, `size_bytes / duration_s = bitrate` exactly. Real-world files are mostly **VBR**: action scenes are bigger, static scenes smaller.

So a "9 GB at average bitrate ⇒ X seconds" estimate is *only an estimate*. We:

1. Compute target time T from average bitrate.
2. Pick the keyframe ≤ T (always cuts a bit short, never over).
3. Run the split.
4. **Measure** the output size.
5. If size > 9 GB by any margin (shouldn't happen with step 2, but VBR pathological cases exist), back off one keyframe and retry.

## 2.5 Stream Copy: the magic phrase

```bash
ffmpeg -ss <kf_time> -i input.mkv -t <duration> \
       -map 0 -c copy -avoid_negative_ts make_zero \
       part_001.mkv
```

- `-ss <kf_time>` **before** `-i` = fast input seek; with `-c copy`, FFmpeg snaps to the keyframe at or before this time.
- `-t <duration>` = how long this part lasts.
- `-map 0` = include **every stream** from input #0 (video, all audios, all subs, chapters, attachments unless filtered).
- `-c copy` = **no re-encoding**, all codecs.
- `-avoid_negative_ts make_zero` = rewrite timestamps so each part starts at PTS 0. **Critical for subtitle timing.**

This single command is the heart of the splitter. Everything else is logistics.

## 2.6 What "lossless merge" actually requires

`ffmpeg -f concat -safe 0 -i list.txt -c copy output.mkv`

For this to succeed, the parts must share:

- Same codec parameters (codec ID, profile, level, resolution, pixel format, framerate).
- Same audio sample rate, channel layout.
- Same subtitle codec.
- Same SPS/PPS/VPS (for H.264/HEVC) — Matroska handles this via `CodecPrivate`; if all parts came from the same source, they will match.

**Because every part originated from the same `-c copy` split of the same source**, all of these are guaranteed identical. Merge is reliable.

## 2.7 Why MediaCodec (the Android native API) is *not* the answer

MediaCodec / MediaMuxer / MediaExtractor are great for:
- Re-encoding short clips.
- Reading individual frames.
- Lower-power preview.

They are bad for our problem because:
- MediaMuxer's MKV support is read-only / partial (varies by Android version).
- No multi-subtitle stream support.
- No PGS/VobSub bitmap subtitle write support.
- Concat / lossless split is not a first-class operation.

So MediaCodec/MediaMuxer can be used for *thumbnails* or *preview* but not for the split/merge engine.

## 2.8 What FFmpeg gives us out of the box

- All codecs already supported.
- Concat demuxer for lossless merge.
- Stream-copy split with `-ss`/`-t`.
- `ffprobe` for metadata and keyframe lists (same binary, different name).
- Subtitle timestamp rewriting via `-avoid_negative_ts`.
- Robust error reporting.

The cost is the **30–80 MB** of native code we ship and the **library availability** problem (see doc 07).
