# Subtitles

> Operational summary. Deep dive in
> [`analysis/05-SUBTITLE-HANDLING.md`](../analysis/05-SUBTITLE-HANDLING.md).

The single feature most "video splitter" apps get wrong. We get it right by
**doing nothing fancy** — FFmpeg already does everything if we don't get in
its way.

## 1. Codec families we handle

| Family | Examples | FFmpeg codec id | Storage type | Stream-copy? |
|---|---|---|---|---|
| Plain text | SRT, WebVTT | `subrip`, `webvtt` | UTF-8 lines + cue numbers + timestamps | ✅ |
| Styled text | ASS / SSA | `ass` | Header (styles, fonts) + dialogue | ✅ |
| Bitmap (HD) | PGS (Blu-ray) | `hdmv_pgs_subtitle` | Raw image bitstream + display set timestamps | ✅ |
| Bitmap (SD) | VobSub (DVD) | `dvd_subtitle` | IDX index + SUB MPEG2-PS | ✅ |

All four are preserved with `-c copy`. We never burn-in, never re-render,
never transcode.

## 2. Single canonical command

```
-map 0 -map 0:t? -c copy -avoid_negative_ts make_zero -copyts
```

Verbatim across every split and merge invocation.

| Flag | Why |
|---|---|
| `-map 0` | Include every stream (video + all audio + all subs + chapters). |
| `-map 0:t?` | Include attached fonts (matters for ASS rendering in players). |
| `-c copy` | No re-encoding. The raw bytes from the original encoder pass through. |
| `-avoid_negative_ts make_zero` | Each output part starts at PTS 0; subtitle packet PTS shifts with it. |
| `-copyts` | Preserve original timestamps before the rewrite (needed by some bitmap subs). |

## 3. Why timestamps "just work"

When we cut from `t = 1230s` to `t = 2460s`, subtitle events inside that
range originally have packet PTS like `1232.5s`. Without
`-avoid_negative_ts make_zero`, those would carry over and a viewer would
see "subtitle at 1232 s" inside a 1230-s file — never displayed.

With the flag, FFmpeg's muxer treats the segment start as PTS 0 and shifts
**every** packet — video, audio, subtitle, chapter mark. The shift propagates
through:

- **SRT / WebVTT**: cue numbers may renumber from 1; content unchanged.
- **ASS**: dialogue lines store their own timestamps (`Dialogue: 0,1:23:45.67,...`).
  FFmpeg's ASS muxer reads from packet PTS and re-emits, so the shift
  propagates inside the line text. Verified in FFmpeg ≥ 5.x.
- **PGS / VobSub**: display set / IDX timestamps come from packet PTS;
  shifted.

In v1 we trust this fully. If we ever see a corner case (rare TV
captures), the fallback is to add `-muxpreload 0 -muxdelay 0` to the
command — but only on explicit retry. Not on the happy path.

## 4. PGS — special note

PGS subtitles are organised in **Display Sets**:

```
PCS (Presentation Composition) — when to show
WDS (Window Definition)        — where on screen
PDS / ODS (Palette / Object)   — what to draw
END                             — end of set
```

If our cut lands **inside** a display set, the part can show a partial
subtitle or none at all. FFmpeg's PGS demuxer handles this by emitting the
open display set's PCS into the next part — which we get for free with
`-c copy` over a contiguous range.

UI consequence: a subtitle visible across a cut boundary may "appear at the
start of the next part". We document this in the Settings → Help text and
in [SCREENS.md](SCREENS.md).

## 5. ASS attachments (fonts)

MKV supports embedded `.ttf` / `.otf` font attachments referenced by ASS
`Style` entries. `-map 0` does **not** include attachments by default; we
must add `-map 0:t?` (the `t` stream type, `?` = optional).

```
ffmpeg ... -map 0 -map 0:t? -c copy ...
```

This preserves all fonts so ASS renders correctly in players that don't
have those fonts installed locally (most phone players don't).

## 6. Multi-track preservation

`-map 0` includes them all:

- Multiple audio tracks (e.g. Telugu DTS-HD MA + Hindi E-AC3 + commentary).
- Multiple subtitle tracks (e.g. English ASS + Telugu ASS).
- Default flags on the original streams.
- `language` metadata.
- `disposition:default`, `disposition:forced` flags.

The user does not pick tracks — Q18 = preserve everything. v1.x will add a
"Pick tracks" UI; not v1.

## 7. Merge subtitle reconstruction

The concat demuxer with `-map 0 -c copy` re-emits every subtitle stream of
every part. Provided each part has the same set of subtitle tracks (which
they do, because all parts came from the same split), the merged file
contains every subtitle event with timestamps reconstructed across the
boundary.

Pre-merge validation (in `MergeValidator`) asserts: each part's
`subtitleStreams.size` and per-stream codec IDs match part 1. Mismatch =
refuse to merge with a clear UI error.

## 8. Hardcoded ("burned-in") subtitles

Hardcoded subtitles are part of the **video stream** itself — there is no
separate subtitle track. We can't extract or shift them; they are pixels.
Splitting works fine — the pixels stay where they are. We mention this
once in S15 → "Subtitles" help text. No special UI handling.

## 9. Edge cases

| Scenario | Behaviour |
|---|---|
| Source has 0 subtitle tracks | Fine. Merge works. UI shows "0 tracks". |
| Source has SRT only, output is MKV | Trivial; `-c copy` works. |
| Source has PGS, output is MKV | Trivial; `-c copy` works. |
| Source is MP4 with `mov_text` | Output is MKV; `mov_text` re-muxed without conversion. |
| Source is MP4 with PGS (rare extension) | We force MKV output (D3 dialog). |
| Source has malformed PTS (TV capture) | `-avoid_negative_ts make_zero` usually fixes; explicit retry available with `-fflags +genpts`. |
| Subtitle PTS = -1 (FFmpeg "no PTS") | Demuxer drops these packets silently. We log it. |

## 10. Test coverage

Mandatory tests in `app/src/androidTest/.../engine/`:

- `EngineSmokeTest#allSubStreamsPresentInPart1` — fixture has ASS English +
  AAC Telugu; the first split part must list both via `ffprobe`.
- `EngineSmokeTest#subTimestampShiftedToZero` — first cue PTS in part 2
  must be `< 0.5 s` after the part start (i.e. the original cue at
  `1232.5s` becomes `2.5s` after a cut at `1230.0s`).
- `MergeRoundTripTest#subtitlePacketsCountMatches` — `ffprobe -show_packets
  -select_streams s` count is identical between original and merged file.

Manual checklist for v1 acceptance (run on a real device with VLC):

| Test | Source file | Expected behaviour |
|---|---|---|
| T1 | 4K HEVC + SRT (eng) | Each part has SRT, plays in VLC, no missing cues. |
| T2 | 4K HEVC + ASS (with attached `.ttf`) | Part has ASS + the font; styled correctly in VLC. |
| T3 | Blu-ray rip with PGS (eng) + AC3 + TrueHD | Each part has PGS; subs display in VLC. |
| T4 | DVD rip with VobSub (multi-language) | Each part has all VobSub tracks. |
| T5 | Round-trip split → merge | Subtitle packet PTS list before split == after merge. |

## 11. What an agent must NOT do here

- ❌ Use `-c:s mov_text` or any subtitle-codec conversion.
- ❌ Drop `-map 0:t?` — fonts are part of "preserve everything".
- ❌ Use `-c:s copy` only (i.e. without `-map 0`) — doesn't include
  multi-track.
- ❌ Re-render subtitles to bitmap.
- ❌ Hardcode (burn-in) subtitles. v1 is lossless; never re-encode.
- ❌ Strip language metadata. We carry it through.
