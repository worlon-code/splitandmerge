# 01 — Problem Statement & Goals

## 1. The User Story

> As a user with very large local video files (4K MKVs with embedded subtitles), I want a native Android app that lets me **split** them into a chosen number of parts (or auto-split with a hard 9 GB-per-part ceiling) and later **merge** the parts back into a single file at the **exact original quality** with **subtitles intact and in sync**.

## 2. Hard Requirements (must work in v1)

| # | Requirement | Notes |
|---|-------------|-------|
| H1 | Split a video into **N user-defined parts** (e.g. 3 parts). | N ≥ 2. |
| H2 | Auto-split where each part is **≤ 9 GB**. | Hard cap by default. See Q in doc 11. |
| H3 | Combine modes: when both N and 9 GB are in play, the cap wins (more parts than N). | Discussed in doc 03. |
| H4 | **Lossless** split: zero re-encoding, byte-identical streams. | `-c copy -map 0`. |
| H5 | Embedded subtitles **preserved in every part**. | All subtitle streams, all languages. |
| H6 | Subtitle timing in each part starts at **00:00:00** of that part. | Achieved automatically by `-avoid_negative_ts make_zero` + standard FFmpeg behaviour; verify in doc 05. |
| H7 | **Merge parts back** to one file with **identical** video/audio/subtitle quality. | `concat` demuxer + `-c copy`. |
| H8 | Original 4K (or whatever resolution / HDR / DV / Atmos) preserved. | Stream copy guarantees this. |
| H9 | Runs **fully on-device**. No cloud, no upload. | Privacy + size. |
| H10 | Survives **long-running jobs** (≥ 1 hour) without being killed. | Foreground service. |

## 3. Soft Requirements (v1.x or v2)

- S1. Multi-job queue (queue several splits/merges).
- S2. Per-job notification with progress + cancel.
- S3. Drag-and-drop / multi-select of parts to merge.
- S4. Verify part integrity post-write (size + ffprobe quick check).
- S5. Material 3 dynamic colour, AMOLED dark, foldable + tablet two-pane.
- S6. Support input containers beyond MKV (MP4, AVI, WebM, MOV, TS) — *if* user confirms.
- S7. Preserve chapters, attached fonts (for ASS), all audio tracks, all subtitle tracks.
- S8. Display a parsed report of the file (codecs, streams, languages) before splitting.
- S9. Update-check screen (like your KissKh / HTML Viewer apps' Settings).

## 4. Non-Goals (explicitly NOT in scope)

- ❌ Re-encoding / transcoding (no resolution change, no codec change, no quality knob).
- ❌ Editing video content (no trimming arbitrary ranges other than the part boundaries, no joining of *different* sources, no filters).
- ❌ Streaming or downloading from the internet.
- ❌ DRM-protected content.
- ❌ Subtitle editing (we only preserve and shift timing).
- ❌ Cloud sync.

## 5. Success Criteria for v1

The app is **done for v1** when:

1. ✅ A 50 GB 4K HEVC MKV with two audio tracks and three subtitle tracks (one PGS bitmap, two SRT) splits into ≤ 9 GB parts in under 15 minutes on a Pixel-class device.
2. ✅ Each part plays correctly start-to-finish in VLC for Android **with subtitles**, with no A/V drift.
3. ✅ Merging the parts back produces a file that is byte-identical for the elementary streams (verifiable with `ffprobe -show_streams`) and visually indistinguishable.
4. ✅ Cancel mid-job leaves no half-written corrupted output.
5. ✅ App passes Play Store review for storage permissions (no `MANAGE_EXTERNAL_STORAGE` requested).

## 6. Why "9 GB" specifically?

Best guess at the constraint behind this number:

- 9 GB is below the **single-archive limits** of most legacy upload pipes (e.g. 10 GB, often advertised as "up to 10 GB").
- 9 GB exceeds the **FAT32 4 GB / `exFAT` practical** limits some external drives and SD cards still use, *but* most uses today are upload/share pipelines, not filesystem limits.
- Confirm with user (see doc 11). The number is a parameter; the algorithm is identical for 4 GB, 9 GB, 25 GB, etc.

## 7. Out-of-band concerns

- **Licensing.** FFmpeg with default builds is **LGPL 2.1+**, but if any GPL-only flag is enabled (e.g. `--enable-gpl`, x264, libfdk_aac), the app must be GPL. Plan: stick to LGPL build to keep the app under a permissive licence. Subtitle codecs (libass) are ISC; PGS is muxed as bytes, no codec needed for stream copy.
- **APK size.** A bundled FFmpeg AAR adds **~30–80 MB** of native libs (4 ABIs). We will **`abiFilters`** to `arm64-v8a` only by default to keep APK ≤ 40 MB; ship `armeabi-v7a` separately if needed.
- **Play Store policy.** "All Files Access" is restricted. SAF only.
