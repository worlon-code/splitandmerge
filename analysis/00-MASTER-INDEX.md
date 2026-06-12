# Video Splitter & Merger — Android (Kotlin) — Deep Analysis

**Working name (placeholder):** *MKV Slice* (we can rename later).
**Target platform:** Native Android, Kotlin + Jetpack Compose.
**Core goal:** Lossless split of large video files (MKV, MP4, etc.) — including embedded subtitles — into user-defined parts and/or auto-chunks bounded by **9 GB per part**, plus a lossless merge that restores 4K (and any other) original quality with consistent subtitles.

---

## 1. TL;DR — Can it be built?

**Yes, on Android, with FFmpeg under the hood.** The two operations the app needs are:

1. **Lossless split** = FFmpeg stream copy (`-c copy -map 0`) cut at keyframe boundaries.
2. **Lossless merge** = FFmpeg `concat` demuxer (`-f concat -c copy`) over the parts.

Neither operation re-encodes a single frame, so 4K/HDR/Dolby Vision/AV1/H.265 quality is byte-preserved. Subtitles (SRT/ASS/PGS/VobSub) ride along as additional streams via `-map 0`.

The hard work is **NOT the FFmpeg commands**. It is:

- Picking split points exactly on **keyframes** so every part plays without artifacts.
- Honouring **two split modes simultaneously** (N parts AND ≤ 9 GB cap).
- **Subtitle timestamp adjustment** so each part's subs start at 00:00:00.
- **Android plumbing**: SAF storage, foreground service for hour-long jobs, large-file (>4 GB) IO, scoped storage on Android 11+.
- **Library choice**: FFmpegKit was archived by Arthenica in **April 2025**. We need a maintained fork or self-built FFmpeg. (See [07-LIBRARIES-AND-DEPENDENCIES.md](07-LIBRARIES-AND-DEPENDENCIES.md).)

---

## 2. Document Map

| # | File | What it covers |
|---|------|----------------|
| 00 | [00-MASTER-INDEX.md](00-MASTER-INDEX.md) | This page. Summary + map. |
| 01 | [01-PROBLEM-AND-GOALS.md](01-PROBLEM-AND-GOALS.md) | Hard requirements, soft requirements, non-goals. |
| 02 | [02-TECHNICAL-DEEP-DIVE.md](02-TECHNICAL-DEEP-DIVE.md) | MKV/MP4 internals, GOPs, why keyframes matter, codecs we must preserve. |
| 03 | [03-SPLIT-ALGORITHM.md](03-SPLIT-ALGORITHM.md) | Step-by-step split logic, FFmpeg commands, dual-mode (N parts + 9 GB cap). |
| 04 | [04-MERGE-ALGORITHM.md](04-MERGE-ALGORITHM.md) | Concat demuxer, validation, fallback paths. |
| 05 | [05-SUBTITLE-HANDLING.md](05-SUBTITLE-HANDLING.md) | Text vs bitmap subs, timestamp shift, edge cases. |
| 06 | [06-ANDROID-ARCHITECTURE.md](06-ANDROID-ARCHITECTURE.md) | Compose + ViewModel + Foreground Service + Room queue. |
| 07 | [07-LIBRARIES-AND-DEPENDENCIES.md](07-LIBRARIES-AND-DEPENDENCIES.md) | FFmpeg integration options post-FFmpegKit, license matrix. |
| 08 | [08-UI-FLOW-AND-SCREENS.md](08-UI-FLOW-AND-SCREENS.md) | Screen list and Stitch design brief (what to ask Stitch for). |
| 09 | [09-PROJECT-STRUCTURE.md](09-PROJECT-STRUCTURE.md) | Folder & module layout for Android Studio. |
| 10 | [10-RISKS-AND-EDGE-CASES.md](10-RISKS-AND-EDGE-CASES.md) | What can go wrong, how we mitigate. |
| 11 | [11-OPEN-QUESTIONS.md](11-OPEN-QUESTIONS.md) | Questions you must answer before code generation. |
| 12 | [12-ROADMAP.md](12-ROADMAP.md) | Phased delivery plan (MVP → v1 → v1.x). |
| 13 | [13-NEXT-STEPS.md](13-NEXT-STEPS.md) | Once questions are answered: agents + master prompt outline. |

---

## 3. Headline Findings

1. **Stream-copy is mandatory.** Re-encoding a 50 GB 4K HEVC file on a phone is not viable (hours of CPU, thermal throttle, quality loss). Stream copy is ~IO speed; a 50 GB split runs in minutes.
2. **9 GB cap means split-by-time, verified-by-size.** FFmpeg cannot directly say "stop at 9 GB". We pick a *time* that, given the average bitrate, lands just under 9 GB at the next keyframe, then verify after writing. (Algorithm in doc 03.)
3. **MKV is friendlier than MP4** for muxed subtitles (SRT/ASS/PGS all native). MP4 only natively carries `mov_text`. If the user feeds an MP4 with PGS subs, we either keep the output as MKV or convert subs to a compatible track.
4. **FFmpegKit retirement** is the single biggest external risk. Plan: pin to a maintained community fork (e.g., one of the active forks that still publishes AAR/Maven artifacts as of mid-2026) **or** vendor pre-built FFmpeg `.so` binaries and write our own thin JNI wrapper. Decision in doc 07.
5. **Foreground service is non-negotiable.** A 50 GB split on a phone takes long enough that Doze / app standby will kill any background job. Use `ForegroundService` with `dataSync`/`mediaProcessing` type.
6. **SAF + `ParcelFileDescriptor`** lets FFmpeg read content URIs via `pipe:` or `/proc/self/fd/N`. We will *not* require manage-all-files permission — it is a Play Store policy red flag for non-file-manager apps.

---

## 4. What I need from you before code generation

See [11-OPEN-QUESTIONS.md](11-OPEN-QUESTIONS.md). The biggest ones:

1. Min Android SDK / target SDK?
2. Stitch design output folder path (once Stitch generates it)?
3. Distribute via **GitHub Releases** like your other apps, or Play Store?
4. Where do output parts go by default — same folder, sub-folder, or user-picked folder per job?
5. How strict is the 9 GB? Hard cap (never exceed), or soft target (±1 %)?
6. Which input containers must work on day one — MKV only, or MKV + MP4 + AVI + WebM + MOV + TS?
7. Do we need preserve-everything (multi-audio, chapters, attachments, fonts), or just video + primary audio + subs?

Once answered, I'll generate the agents file (`AGENTS.md` style like your KissKh / HTML Viewer apps) and the master prompt for Codex / Antigravity.

---

## 5. Status

This pack is **analysis only**. No Android project is scaffolded yet. We do that after questions are answered.
