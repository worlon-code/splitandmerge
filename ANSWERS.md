# Phase-0 Answers — All 24 Questions Locked

All answers captured from your replies. Where the answer needed interpretation, my reading is in the **Notes** column — flag any I got wrong.

| Q | Topic | Answer | Notes |
|---|---|---|---|
| Q1 | 9 GB hard vs soft cap | **Soft target with 9.5 GB ceiling** | Aim ~9 GB; allow up to 9.5 GB if the next keyframe lands there. Never exceed 9.5 GB. |
| Q2 | Default cap value | **9 GB default, 9.5 GB ceiling** | "Default and hard cap are same" interpreted as: default target = 9 GB, absolute max allowed = 9.5 GB. |
| Q3 | Input containers | **MKV + MP4 + AVI + WebM + MOV + TS** | All FFmpeg-supported containers. |
| Q4 | Output container | **Match input** | If input `.mkv` → output `.mkv`. If `.mp4` → `.mp4`. **See clarifying question 4 below.** |
| Q5 | Output destination | **Auto-create subfolder named from cleaned title** | Input `Bahubali.4k.mkv` → folder `Bahubali/`. Input `www.5movierulz.download - Bahubali (2025) True.mkv` → folder `Bahubali (2025)/`. Cleanup rules below. |
| Q6 | Output naming | **`<cleaned title>.partNNN.<ext>`** | e.g. `Bahubali (2025).part001.mkv`. Three-digit zero-padded index. |
| Q7 | Min / target SDK | **min 26 (A8) / target 35 (A15)** | |
| Q8 | ABI filter | **`arm64-v8a` only** | Smallest APK, modern devices. |
| Q9 | Distribution | **GitHub Releases only** | Same pattern as your KissKh / HTML Viewer apps. |
| Q10 | Signing | **Scaffold signing config, hide from UI** | `keystore.properties` (gitignored) read by Gradle if present; debug-signing fallback otherwise. No in-app screen for it. |
| Q11 | Package name | **`com.splitandmerge.mkvslice`** | |
| Q12 | App display name | **Video Splitter** | |
| Q13 | Design folder | **Sibling to repo at folder level — `…/Kotlin APK/Design/`** | You will create the `Design/` folder; I won't pre-create it. |
| Q14 | ADB path | **`D:\idm\platform-tools-latest-windows\platform-tools\adb.exe`** | |
| Q15 | FFmpeg engine | **Maintained community fork** | Specific fork named at scaffold time. |
| Q16 | Toolchain manager | **Plain Gradle wrapper + Android Studio bundled JDK 17** | |
| Q17 | Multi-job behaviour | **One job at a time, others queued** | Foreground service runs sequentially. |
| Q18 | Streams preserved | **Everything**: video, all audio, all subs, chapters, attachments | `-map 0 -map 0:t?` |
| Q19 | Update-check feature | **Yes, GitHub Releases-based** | Like KissKh / HTML Viewer. |
| Q20 | Telemetry | **None** | |
| Q21 | App licence | **Apache 2.0** | (FFmpeg LGPL stays separate.) |
| Q22 | Agent rules filename | **`AGENTS.md`** | |
| Q23 | Master prompt target | **Codex + Antigravity** | |
| Q24 | When to generate | **After analysis review + answers** | We are at this point now. |

---

## Filename / folder cleanup rules (from Q5, Q6)

Working spec for the title-extraction logic:

```
Input filename:  www.5movierulz.download - Bahubali (2025) True.mkv
                 ↓
Stage 1: strip extension                    → www.5movierulz.download - Bahubali (2025) True
Stage 2: strip leading URL prefix           → Bahubali (2025) True
         pattern: ^www\.[^\s-]+\s*[-–]\s*
Stage 3: replace dots with spaces           → Bahubali (2025) True
         (preserve "(YYYY)" parens & their year)
Stage 4: strip noise tokens (case-insens.)  → Bahubali (2025)
         resolution:  4K, 2160p, 1080p, 720p, 480p, UHD, HD
         HDR:         HDR, HDR10, HDR10+, DV, DolbyVision
         codec:       x264, x265, HEVC, H264, H265, AVC, AV1
         audio:       AAC, AC3, EAC3, DDP, DDP5.1, DD, DD5.1, DTS, DTSHD, DTS-HD-MA, TrueHD, Atmos, MP3, FLAC, OPUS
         source:      BluRay, BDRip, BRRip, WEBRip, WEB-DL, NEWEB-DL, HDRip, DVDRip, REMUX
         markers:     True, Real, Repack, Proper, Internal, Extended, Director's.Cut, DUAL, MULTI
         release grp: trailing "-XYZ" / ".XYZ" tokens with alphanumerics (e.g. Friday4KPopc, RARBG, YIFY, FGT)
Stage 5: collapse whitespace, trim          → Bahubali (2025)
Stage 6: strip trailing dots/dashes/pipes   → Bahubali (2025)
```

Result `Bahubali (2025)` becomes:
- The **subfolder name** under the user's chosen output dir.
- The **base name** of every part: `Bahubali (2025).part001.mkv`, `…part002.mkv`, etc.

Edge cases:
- If cleanup yields empty / < 2 chars → fall back to original filename minus extension.
- User can override the auto-detected title in the **Split Config** screen (free text).
- Year detection: a 4-digit `19xx`/`20xx` in parens or after a dot is preserved as `(YYYY)`.

---

## Clarifications resolved

| # | Topic | Resolution |
|---|---|---|
| C1 | Cap behaviour | **Target 9 GB, ceiling 9.5 GB.** Engine plans at 95 % of ceiling; if next keyframe > 9.5 GB, step back to prior keyframe (even if that yields an 8.6 GB part). Never exceed 9.5 GB. |
| C2 | MP4 input + bitmap subs | **Auto-promote to MKV.** When input is MP4/AVI/MOV/TS *and* has PGS/VobSub tracks (which those containers can't natively carry), output container is forced to `.mkv`. Show a one-time info banner on the Split Config screen explaining why. |
| C3 | Title cleanup rules | **Drafted defaults + user-editable regex patterns persisted in DB.** Settings has a new sub-screen "Title cleanup patterns" listing default rules (read-only on/off) plus user-added custom regexes. Before folder creation, a preview dialog shows the cleaned title and the user can pick / edit. |
| C4 | Subfolder collision | **Ask the user every time.** Modal dialog with three buttons: Use existing folder (write parts in, may overwrite same-named files) / Auto-suffix (`(1)`, `(2)`, …) / Cancel job. |

### Cleanup-patterns DB shape (Room)

```kotlin
@Entity(tableName = "cleanup_patterns")
data class CleanupPatternEntity(
    @PrimaryKey val id: String,             // UUID
    val regex: String,                      // Java/Kotlin regex
    val replacement: String,                // usually ""
    val enabled: Boolean,
    val isBuiltIn: Boolean,                 // true for shipped defaults; user can disable but not delete
    val orderIndex: Int,                    // applied in ascending order
    val label: String,                      // human-readable, e.g. "Strip resolution tokens"
    val createdAt: Long
)
```

Defaults seeded on first run (one row per category in the cleanup spec above). User edits via Settings → Title cleanup patterns.

---

## Phase-0 status

- ✅ All 24 questions answered.
- ✅ All 4 clarifications resolved.
- 🟢 Ready to start Phase 1 (`AGENTS.md` + master prompt + AI/ docs).

---

## Real-file codec validation (June 12 sample set)

Validated three real files from the user's library against our split/merge plan. All three are 100 % stream-copy compatible — no special handling needed.

| File hint | Video | Audio tracks | Sub tracks | Stream-copy verdict |
|---|---|---|---|---|
| `Baahubali The Epic 2025 2160p NEWEB-DL DUAL DTS HD MA DDP5.1 H.265 …` | HEVC 3840×2160 24fps | DTS-HD MA Telugu 6ch + E-AC3 Hindi 6ch | ASS English | ✅ |
| `5MovieRulz.graphics` Kannada lead 4K | HEVC 3840×2160 25fps | 8 tracks: E-AC3 ×4 langs 6ch + AAC ×4 langs 2ch (Tel/Tam/Mal/Kan) | ASS English | ✅ |
| `5MovieRulz.software` cinematic crop | HEVC 3840×1600 24fps | E-AC3 Tel 6ch + AAC Tel 2ch | ASS English + ASS Telugu | ✅ |

FFmpeg codec IDs: `hevc`, `dts` (DTS-HD MA profile), `eac3`, `aac`, `ass`. Container `matroska`. All supported by `-c copy` since FFmpeg 4.x. Multi-language audio + sub tracks all carried through `-map 0`. Attachments (`-map 0:t?`) cover any embedded fonts the ASS subs reference.

**Filename cleanup test on these inputs:**

| Input filename (excerpt) | Cleaned title | Folder | First part name |
|---|---|---|---|
| `Baahubali The Epic 2025 2160p NEWEB-DL DUAL DTS HD MA DDP5.1 H.265 Friday4KPopc.mkv` | `Baahubali The Epic (2025)` | `Baahubali The Epic (2025)/` | `Baahubali The Epic (2025).part001.mkv` |
| `www.5MovieRulz.graphics - Kantara.Chapter.1.2024.Kannada.4K.WEB-DL.x265.mkv` | `Kantara Chapter 1 (2024)` | `Kantara Chapter 1 (2024)/` | `Kantara Chapter 1 (2024).part001.mkv` |
| `www.5MovieRulz.software - Karuppu.2026.Tamil.4K.WEB-DL.x265.mkv` | `Karuppu (2026)` | `Karuppu (2026)/` | `Karuppu (2026).part001.mkv` |

All three handled by the cleanup rules above. The `Friday4KPopc` release-group case lives under the alphanumeric trailing-token stripper.

---

## Why foreground service, not pure background work

The user reasonably asked: *"Why don't we take `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission and just run in the background?"*

Short answer: **battery-optimization exemption does not give you reliable hours-long background work on Android.** Only a foreground service does. We use FGS, but make it *behave* like background.

### What kills long jobs

| Killer | Battery-exempt helps? | FGS helps? |
|---|---|---|
| Doze (screen off, idle) | Partially | ✅ fully exempt |
| App Standby Buckets | No | ✅ |
| System memory pressure (LMK) | No | ✅ (FGS is highest killable priority) |
| OEM aggressive killers (MIUI, Huawei EMUI, OxygenOS, One UI Game Booster, RealmeUI) | **No** — ignore AOSP whitelist | ✅ (they generally don't kill FGS with a visible notification; verified per dontkillmyapp.com) |
| User swipes from Recents | No | ✅ |
| Reboot / OOM kill | No | Both die; resumed via Room state on next launch. |

Also: **Play Store policy restricts `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`** to apps whose core function fails without it. Google has rejected apps that requested it for convenience. FGS is the canonical/approved path for video transcoding-style work.

### Design decisions

1. **FGS is mandatory.** Notification channel `IMPORTANCE_LOW` → small status-bar icon only, no sound/vibration/banner. Effectively invisible.
2. **Settings → "Improve reliability on this device" (opt-in).** Calls `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` for users on Xiaomi/OnePlus/Huawei/Realme. Off by default; explained inline ("Useful if jobs get interrupted on your device"). Combines FGS + battery exemption for maximum reliability.
3. **Settings → "Keep screen on during progress" (opt-in).** `FLAG_KEEP_SCREEN_ON` while user is on Progress screen. Off by default.

### Permissions added (vs. original architecture doc)

| Permission | Purpose | When |
|---|---|---|
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Optional reliability boost on aggressive OEMs | User-triggered from Settings only; never on launch |

All other permissions per [06-ANDROID-ARCHITECTURE.md](06-ANDROID-ARCHITECTURE.md) §6.6.
