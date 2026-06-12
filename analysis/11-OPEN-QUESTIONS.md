# 11 — Open Questions

These are the questions whose answers materially change the design or the agent-generation prompt. **Please answer all of them before we proceed to scaffolding or master-prompt generation.**

Each question lists my recommended default in **bold** so you can simply say "go with defaults" if you don't have a strong preference.

---

## Q1 — Hard 9 GB cap, or soft target?

- **(a) Hard cap (default).** No part may exceed 9 GB. Algorithm aims at 95 % and re-splits anything that comes in over.
- (b) Soft target. Aim for 9 GB; allow ±1 % overshoot for cleaner cuts.
- (c) Configurable per-job in Settings.

---

## Q2 — Default cap value

- **(a) 9 GB exactly = 9 663 676 416 bytes (default).** Matches your spec literally.
- (b) 8 GB.
- (c) Custom default; user can change in Settings.

---

## Q3 — Input containers in v1

- **(a) MKV + MP4 (default).** Covers >95 % of real-world content.
- (b) MKV only.
- (c) MKV + MP4 + AVI + WebM + MOV + TS (all FFmpeg-supported).

(MP4 input still produces MKV output — see doc 02.)

---

## Q4 — Output container

- **(a) Always MKV (default).** Most compatible with any subtitle codec.
- (b) Match input container.

---

## Q5 — Output destination

- **(a) Same folder as input by default; user can override per job (default).**
- (b) Always a configured "MKV Slice" folder under Movies (Settings).
- (c) Always ask per job (no default).

---

## Q6 — Output naming

- **(a) `<basename>.partNNN.mkv` (default).** e.g. `Show.S01E01.4K.part001.mkv`.
- (b) `<basename> - Part NNN.mkv`.
- (c) Custom template in Settings.

---

## Q7 — Min / target Android SDK

- **(a) min 26 (Android 8) / target 35 (Android 15) (default).** Captures ~92 % of devices in 2026.
- (b) min 24 (Android 7) / target 35.
- (c) Other — specify.

---

## Q8 — ABI strategy

- **(a) `arm64-v8a` only for v1 (default).** Smallest APK, modern devices.
- (b) `arm64-v8a` + `armeabi-v7a` (older 32-bit phones).
- (c) All four ABIs (largest APK).

---

## Q9 — Distribution

- **(a) GitHub Releases, like your KissKh and HTML Viewer apps (default).**
- (b) Google Play Store.
- (c) Both.

---

## Q10 — Signing

- (a) New dedicated keystore for this app.
- **(b) Tell me later; generate a debug-signed build for now (default).**
- (c) Reuse an existing keystore (you'd need to share location).

---

## Q11 — Application id (package name)

Pick one — examples (`<your-id>.<app>`):

- `com.yadlapati.mkvslice`
- `dev.yadlapati.mkvslice`
- `com.yourdomain.mkvslice`

I'll default to `dev.yadlapati.mkvslice` if you don't say.

---

## Q12 — App display name

- **(a) MKV Slice (default).**
- (b) Video Splitter Pro.
- (c) ClipCutter.
- (d) (Your suggestion).

---

## Q13 — Stitch design folder location

Where will the Stitch design pack be downloaded? My agent rules will need this path. Examples:

- `C:\Users\OYADLAPATI\Downloads\stitch_mkvslice\`
- `C:\Users\OYADLAPATI\source\repos\AI-LE\Kotlin APK\design\`

---

## Q14 — ADB path

- **(a) `D:\idm\platform-tools-latest-windows\platform-tools\adb.exe` (default — same as your other agent rules).**
- (b) Other — specify.

---

## Q15 — FFmpeg engine choice

- (a) Use archived **FFmpegKit** binaries pinned to last release (fastest to start, future-proofing weak).
- **(b) Use a maintained community fork (default).** I'll research one specifically when scaffolding — recommend confirming a name now if you have a preference.
- (c) Build FFmpeg from source via Gradle (most work, most control).

---

## Q16 — Toolchain manager

You use FVM in your Flutter project. Android equivalent:

- **(a) Plain Gradle wrapper (`./gradlew`) — Android Studio's bundled JDK 17 (default).**
- (b) `sdkman` for JDK pinning.
- (c) Other.

---

## Q17 — Multi-job behaviour

- **(a) One at a time (default).** Queue up; run sequentially. Simpler, gentler on storage IO.
- (b) Parallel up to 2 jobs.
- (c) User configurable.

---

## Q18 — Streams to preserve

- **(a) Everything: video, all audio, all subs, chapters, attachments (default).**
- (b) Video + first audio + first subtitle only.
- (c) User picks per job.

---

## Q19 — Update-check feature

- **(a) Yes, GitHub-Releases-based update check (matches your other apps) (default).**
- (b) No; user will sideload manually.

---

## Q20 — Telemetry / analytics

- **(a) None (default).** Maximum privacy.
- (b) Crashlytics (free, Google).
- (c) Custom HTTP-only crash uploader.

---

## Q21 — License model for the app itself

- **(a) Apache 2.0 (default).**
- (b) MIT.
- (c) GPL-3.
- (d) Proprietary (closed).

(Affects only OUR code. FFmpeg licence is separate; see doc 07.)

---

## Q22 — Naming the agents file

- **(a) `AGENTS.md` (default — same as your HTML Viewer app).**
- (b) `agent-rules.md`.
- (c) Both — `AGENTS.md` + `RULES.md`.

---

## Q23 — Where the master prompt should target

- **(a) Codex (Claude/ChatGPT-style), then Antigravity for execution (default per your message).**
- (b) Just one of them.
- (c) Generic — agnostic prompt usable in any agentic IDE.

---

## Q24 — When can I generate the agent file + master prompt?

- (a) Now, with my recommended defaults filled in.
- **(b) After you've reviewed this analysis and answered questions (default).**
- (c) After Stitch designs are downloaded.

---

## Quick way to reply

Just paste:

```
Q1: a
Q2: a
Q3: a
... (or "defaults for all")
Q11: dev.yadlapati.mkvslice
Q12: MKV Slice
Q13: C:\Users\OYADLAPATI\source\repos\AI-LE\Kotlin APK\design\
```

…and I'll move to producing the `AGENTS.md` + master prompt + scaffolded project.
