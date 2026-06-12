# 10 — Risks & Edge Cases

The places where this project can quietly break. Each risk has a mitigation we will bake into v1.

## 10.1 Library / engine risks

### R1 — FFmpegKit is archived
**Impact:** version rot, potential A15 16 KB-page incompat, no security fixes.
**Mitigation:** treated in [07-LIBRARIES-AND-DEPENDENCIES.md](07-LIBRARIES-AND-DEPENDENCIES.md) — choose a maintained fork; plan migration to in-house FFmpeg build by v1.1.

### R2 — Native-side ABI / page-size break on newer Android
**Impact:** crash on launch on A15+ devices.
**Mitigation:** smoke-test the chosen artifact on at least one A15 emulator + one physical A14 device before wiring screens.

### R3 — `pipe:` IO incompatibility for SAF-only inputs
**Impact:** users on cloud-storage providers (e.g. Drive) can't split.
**Mitigation:** detect early; show "Move file to local storage" message; offer "Import to app cache" in v1.1.

## 10.2 Format / codec edge cases

### R4 — Long-GOP files (keyframe every 10 s)
**Impact:** can't hit 9 GB target precisely; some parts may be 8.2 GB instead of 8.9 GB.
**Mitigation:** acceptable. Document in UI: "Cuts align to keyframes; part sizes may be slightly under the cap."

### R5 — Single-keyframe / static GOP
**Impact:** can't split at all (only one cut point — at start).
**Mitigation:** detect during keyframe scan; show explanatory error: "This video has too few keyframes for lossless splitting. Re-encoding is required (not supported in v1)." Offer "open with another app" intent.

### R6 — VBR pathological spike pushes a part over 9 GB
**Impact:** verification fails, we re-split that part.
**Mitigation:** algorithm targets 95 % of cap; verification + automatic re-split (doc 03 §3.5).

### R7 — Mismatched parts in merge (user picked from different sources)
**Impact:** concat would silently produce a corrupt or stuttering output.
**Mitigation:** pre-merge ffprobe validation (codec, resolution, pix_fmt, audio + sub stream signature). Hard refuse on mismatch.

### R8 — Discontinuous timestamps in source (rare but happens with TV captures)
**Impact:** `-avoid_negative_ts make_zero` may not fully fix; player shows odd timecodes.
**Mitigation:** add `-fflags +genpts` for repair, only on explicit retry (don't slow the happy path).

### R9 — DRM / encrypted streams
**Impact:** can't read.
**Mitigation:** detect with `ffprobe`; show "Encrypted content not supported." Out of scope.

### R10 — Variable framerate (VFR)
**Impact:** PTS rewrite still works for stream copy; no quality loss.
**Mitigation:** none needed. Covered by `-c copy` semantics.

### R11 — HDR / Dolby Vision metadata
**Impact:** lost if we re-mux into MP4 or strip side-data.
**Mitigation:** we always output MKV with `-map 0` → side-data preserved. Verified manually with HDR test fixture.

### R12 — Atmos / TrueHD audio
**Impact:** large bitrate (~6–8 Mbps) inflates parts. No quality issue.
**Mitigation:** none — the bitrate is what it is.

## 10.3 Storage / IO edge cases

### R13 — Insufficient free space
**Impact:** ffmpeg writes a partial file then fails.
**Mitigation:** **before** kicking off, check `StatFs.getAvailableBytes()` against `inputSize * 1.05` (we expect outputs ≈ same total bytes, with a tiny container overhead). Block job with friendly error.

### R14 — User picks output folder on different volume than input (e.g. SD card vs emulated)
**Impact:** copy speed is bounded by the slower volume.
**Mitigation:** none required. Document expected speed in progress UI.

### R15 — Output folder loses permission mid-job (revoked by another app)
**Impact:** mid-job write failure.
**Mitigation:** catch IOException → mark job FAILED with clear text; nothing lost (we keep `.tmp` then atomically rename).

### R16 — File names with emojis / non-FAT-friendly chars
**Impact:** SAF accepts; some external tools don't.
**Mitigation:** show the user what we'll name parts; allow override.

### R17 — Files > 4 GB in `int` arithmetic
**Impact:** overflow in size math.
**Mitigation:** use `Long` everywhere; never `Int.toLong()` after arithmetic.

## 10.4 Service / lifecycle risks

### R18 — App killed mid-job
**Impact:** part `.tmp` file orphaned; job state inconsistent.
**Mitigation:** Room marks status RUNNING; on next start, `JobScheduler` rolls back any RUNNING job to FAILED with reason "interrupted"; orphaned `.tmp` files cleaned up by a sweep. User can retry.

### R19 — Doze / battery-saver kills FGS
**Impact:** modern Android specifically does **not** kill FGS, but battery-saver may throttle CPU.
**Mitigation:** `WAKE_LOCK` + foreground service of type `dataSync`. We don't fight battery-saver beyond that.

### R20 — Notification permission denied (A13+)
**Impact:** no progress visibility.
**Mitigation:** request at first launch; if denied, in-app progress still works (full-screen progress activity if user opens the app while job runs).

### R21 — Two foreground services from competing apps fight for resources
**Impact:** none we can fix.
**Mitigation:** none.

## 10.5 UX risks

### R22 — User cancels then starts again immediately, expecting resume
**Impact:** we restart from part 1 (no resume in v1 — explicit).
**Mitigation:** dialog: "Cancel will stop and discard any incomplete part. Already-finished parts are kept." Resume from a partial-job state lands in v1.1.

### R23 — User picks 2 parts but file requires 7 due to cap
**Impact:** confusion.
**Mitigation:** S6 "Confirmation" screen reads: "Requested 2 parts, but 9 GB cap requires 7. Continue?" with both numbers visible.

### R24 — Long file names crash share / open intents
**Impact:** other apps choke.
**Mitigation:** truncate to 200 chars; show full name in tooltip / row.

### R25 — User merges out-of-order on purpose
**Impact:** weird video, but technically valid.
**Mitigation:** S10 shows a "Custom order" warning chip; merge proceeds.

## 10.6 Security / privacy risks

### R26 — User mistakenly shares a part containing sensitive metadata
**Impact:** privacy.
**Mitigation:** offer "Strip metadata" toggle in Settings (v1.1). Default off (because some metadata is required for HDR; we don't want to silently degrade).

### R27 — Downloaded update APK tampered
**Impact:** trojanised update.
**Mitigation:** `mkvslice-version.json` includes `sha256`; we verify before installing.

### R28 — Path traversal via crafted manifest filenames
**Impact:** attacker-supplied manifest could point at arbitrary paths.
**Mitigation:** restrict manifest part references to siblings of the manifest file (no `..`, no absolute outside the parent SAF tree).

## 10.7 Test risks

### R29 — Real 4K HEVC files are huge → unfit for emulator unit tests
**Mitigation:** generate small synthetic fixtures with FFmpeg in `androidTest` setup (e.g. 60 s 720p HEVC + SRT) for engine smoke tests; keep large-file tests as a manual checklist in `AI/KNOWN_ISSUES.md`.

### R30 — FFmpeg version variation across forks may shift command-line behaviour
**Mitigation:** lock the FFmpeg artifact version in `libs.versions.toml`; the engine has integration tests that assert specific behaviour (e.g. `-show_frames -skip_frame nokey` output format) so a silent breaking change is caught.

## 10.8 Summary of accepted limitations for v1

These are **explicitly not problems** in v1 — only mentioned to keep expectations clear:

- No re-encoding (so no fixing single-keyframe files).
- No DRM.
- No editing/trimming arbitrary ranges.
- No subtitle editing — only carry & shift.
- No cloud picker integration beyond what SAF provides.
- No resume of cancelled or interrupted jobs (re-run starts fresh).
