# 05 — Subtitle Handling

The single most-asked-about feature, and the place where most "video splitter" apps fall down.

## 5.1 The four subtitle codec families we will encounter

| Family | Examples | Storage | How FFmpeg treats it on `-c copy` |
|---|---|---|---|
| **Plain text** | SRT (`subrip`), WebVTT (`webvtt`) | UTF-8 lines + cue numbers + timestamps | Re-muxed into the part with shifted timestamps. Cue numbers may renumber from 1; that's fine. |
| **Styled text** | ASS / SSA (`ass`) | Header (styles, fonts) + dialogue lines | Header is copied to **every part** (it must be). Dialogue lines clipped + shifted. |
| **Bitmap (HD)** | PGS (`hdmv_pgs_subtitle`) — Blu-ray | Raw image bitstream + display set timestamps | Pure stream copy; FFmpeg recomputes presentation timestamps. |
| **Bitmap (SD)** | VobSub (`dvd_subtitle`) — DVD | IDX index + SUB MPEG2-PS bitstream | Stream-copied; IDX regenerated for each part. |

Native FFmpeg supports all four with `-c copy`.

## 5.2 The timestamp problem (and why `-avoid_negative_ts make_zero` solves it)

When we cut from `t = 1230s` to `t = 2460s`, the subtitle events inside that range originally have timestamps like `1232.5s`, `1237.0s`, … If we copy them as-is, the subtitle would appear 20 minutes into a part that's only 20 minutes long — wrong.

`-avoid_negative_ts make_zero` instructs the **muxer** to treat the start of the segment as PTS 0. All packets — *including subtitle packets* — get shifted. Subtitle that fired at `1232.5s` now fires at `2.5s` from start, which is correct for the part.

This works for **all four** subtitle families because FFmpeg shifts the *packet PTS* (the muxer-level field), not the codec-internal data. Bitmap subtitles' display-set timestamps come from packet PTS; text subtitles use packet PTS as their cue start. Both are correct after the shift.

### Caveat for ASS

ASS dialogue lines store their own timestamps **in the line text**, e.g. `Dialogue: 0,1:23:45.67,1:23:48.12,...`. FFmpeg's ASS muxer reads these from the codec context and re-emits them based on packet PTS, so the shift propagates. Verified in FFmpeg ≥ 5.x.

If we ever see a corner case (some old recordings), we have a fallback:

```bash
ffmpeg -i part.mkv -map 0:s:0 -c copy -muxpreload 0 -muxdelay 0 …
```

In v1 we don't pre-emptively add this; we add it only if a real-world test file misbehaves.

## 5.3 PGS (Blu-ray) is special

PGS subtitles are bitmap segments organised into **Display Sets**. A display set spans:

- Presentation Composition Segment (PCS) — when to show.
- Window Definition (WDS) — where on screen.
- Palette + Object Definition (PDS, ODS) — what to draw.
- End of Display Set (END).

If a cut lands **inside** a display set, the part can show a partial subtitle (or none). FFmpeg's PGS demuxer handles this gracefully — it emits the open display set's PCS to the next part — but only when stream-copying a contiguous range. Our implementation (cut at video keyframe, keep `-c copy`) preserves this correctness.

**Edge case:** a subtitle visible across a cut boundary is shown in the first part until cut, then re-shows at start of the next part. Acceptable; documenting in the UI as "subtitles spanning split points appear at the start of the next part."

## 5.4 VobSub (DVD)

VobSub is two-file (`.idx` + `.sub`) at filesystem level, but inside MKV it lives as a single track. FFmpeg generates a fresh IDX header in each part. No special handling needed.

## 5.5 Multiple subtitle tracks

`-map 0` includes them all. We do not number or filter; the user gets exactly what was in the source.

## 5.6 Subtitle preservation in merge

Concat demuxer with `-map 0 -c copy` re-emits every subtitle stream. Provided each part has the same set of subtitle tracks (which they do, because they came from the same split source), the merged file contains every subtitle event with timestamps reconstructed across the boundary.

We will **explicitly assert** this in pre-merge validation: each part's `s:N` count and codec must match part 1's.

## 5.7 The "burned-in" question

"Hardcoded" / burned-in subtitles are part of the **video stream** itself. There is no separate subtitle track. We can't extract or shift them; they are pixels. Splitting works fine — the pixels stay where they are. We mention this in the UI for completeness but no special handling.

## 5.8 ASS attachments (fonts)

MKV supports embedded `.ttf` / `.otf` font attachments referenced by ASS subtitles. `-map 0` does **not** include attachments by default; we must add `-map 0:t?` (the `t` stream type, `?` = optional).

```bash
ffmpeg ... -map 0 -map 0:t? -c copy ...
```

That preserves all fonts. Required for ASS to render correctly in players that don't have the fonts installed locally.

## 5.9 Quick test plan for v1 acceptance

| Test | Source file | Expected behaviour |
|---|---|---|
| T1 | 4K HEVC + SRT (eng) | Each part has SRT, plays in VLC, no missing cues. |
| T2 | 4K HEVC + ASS (with attached `.ttf`) | Part has ASS + the font; styled correctly in VLC. |
| T3 | Blu-ray rip with PGS (eng) + AC3 + TrueHD | Each part has PGS; subs display in VLC. |
| T4 | DVD rip with VobSub (multi-language) | Each part has all VobSub tracks. |
| T5 | Round-trip split → merge | Subtitle timestamps before split == subtitle timestamps after merge (use `ffprobe -show_packets -select_streams s`). |

## 5.10 Bottom line

> Subtitle handling reduces to **using `-map 0 -map 0:t? -c copy -avoid_negative_ts make_zero`** consistently in both split and merge commands. FFmpeg does the rest correctly. The job of the app is to *not get in its way*.
