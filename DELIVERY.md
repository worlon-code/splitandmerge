# DELIVERY — How to bootstrap the Video Splitter repo

> Read this first. It maps every file in this folder, says which ones go
> into the new repo, and gives the exact PowerShell to copy them.

This folder (`C:\Users\OYADLAPATI\source\repos\AI-LE\Kotlin APK\`) is the
**spec workspace**. The actual Android code will live in a **separate
repo** that you create. The agent (Codex / Antigravity / Claude Code /
Cursor / Copilot) initialises the repo for you — see Step 4.

---

## 1. What's in this workspace

```
Kotlin APK/
├── DELIVERY.md                      ← this file
├── AGENTS.md                        ← rules every agent obeys (24.6 KB)
├── TESTING-AGENT.md                 ← test sub-agent rules + samples + CI (33.2 KB)
├── MASTER-PROMPT.md                 ← paste this into the agent (13.5 KB)
├── ANSWERS.md  (in analysis/)       ← Phase-0 decisions (Q1..Q24 + 4 clarifs)
├── STITCH-BRIEF.md                  ← single-file Stitch project brief (38.2 KB)
├── STITCH-PROMPT-ROUND2.md          ← continuation prompt for missing screens
├── run-tests.ps1                    ← one-shot test runner (9.9 KB)
├── .github/workflows/
│   └── android-test.yml             ← CI: unit + emulator instrumented tests
├── analysis/                        ← deep-dive pack 00–13 + ANSWERS + Stitch source files
├── Design/                          ← Stitch design exports (consolidate from the two Stitch folders)
└── AI/                              ← documentation skeleton (16 files + plans/)
```

Total: 43 source files, ~360 KB plain text. No code yet.

## 2. What goes into the new repo (and what stays here)

When the agent initialises the new Android repo, it copies **everything
above** verbatim. After that:

- **This workspace stays read-only.** It's the spec source of truth.
- **The new repo** is where the agent will scaffold `app/`, `gradle/`,
  `gradlew.bat`, etc., and where every commit lands.

The agent never edits files inside this workspace.

## 3. Two folders you still need to handle

### 3a. Consolidate Stitch outputs

Two Stitch folders sit here:
- `stitch_video_splitter_design_brief/`
- `stitch_video_splitter_design_brief (1)/`
- (and the third batch dropped via attachments)

The agent will move their contents into `Design/` under the canonical
folder names listed in [STITCH-BRIEF.md §4](STITCH-BRIEF.md). Your only
remaining design task is:

- Re-prompt Stitch for the missing **`mergeorder_light/`** variant
  (see [AI/KNOWN_ISSUES.md K-007](AI/KNOWN_ISSUES.md)).

### 3b. Pick the GitHub releases repo

You haven't named it yet. The default referenced in docs is
`splitandmerge/mkvslice-releases`. Change it now if you want a different
name; the agent reads the value from
`app/src/main/res/values/strings.xml` ([API_USAGE.md §2](AI/API_USAGE.md)).

## 4. How to start the agent

### 4a. Create an empty target folder

```powershell
New-Item -ItemType Directory -Force -Path "C:\Users\OYADLAPATI\source\repos\video-splitter" | Out-Null
```

(Or any path you prefer. The agent only needs the absolute path.)

### 4b. Open the agent (Codex / Antigravity / Claude Code / Cursor / Copilot)

Pick whichever you prefer. The MASTER-PROMPT works in all of them.

### 4c. Paste the master prompt

Copy the entire contents of [MASTER-PROMPT.md](MASTER-PROMPT.md) as the
**system / first message** prompt.

### 4d. Reply with the target path

The agent will ask for the target path. Reply:

```
Initialise the repo at C:\Users\OYADLAPATI\source\repos\video-splitter.
The spec workspace is at C:\Users\OYADLAPATI\source\repos\AI-LE\Kotlin APK.
Copy from there.
```

The agent will then:

1. Verify the target folder exists and is essentially empty.
2. `git init` and set branch to `main`.
3. Copy all files from the spec workspace into the target.
4. Create `.gitignore`, `README.md`, `AI/`, `logs/`, `app/` skeleton.
5. Run `./gradlew --version` to validate the Gradle wrapper.
6. Make the first commit: `chore: initial scaffold (Phase 1 + 2)`.
7. **Stop and report.**

It will NOT proceed to Phase 3 (Compose UI) without your approval per
[AGENTS.md §2](AGENTS.md).

## 5. Phase plan after Phase 1

The agent works in 8 phases, each with a gate you approve. Full plan in
[analysis/12-ROADMAP.md](analysis/12-ROADMAP.md):

| Phase | Goal | Gate |
|---|---|---|
| 1 | Scaffold + agent + AI/ docs | First debug APK builds + installs |
| 2 | Stitch designs imported to `Design/` | You confirm screen mapping |
| 3 | Compose UI + nav with mock data | All screens reachable |
| 4 | Engine: probe + keyframes | Smoke test green |
| 5 | Engine: split | Real split of 1 GB fixture; subs intact |
| 6 | Engine: merge | Round-trip MD5 of streams matches |
| 7 | Polish + Settings + update check | All 17 screens functional |
| 8 | Release v0.0.1 | APK signed, GitHub release created |

## 6. Key documents at a glance

| Doc | When to read |
|---|---|
| [AGENTS.md](AGENTS.md) | Before every agent session. The rules. |
| [TESTING-AGENT.md](TESTING-AGENT.md) | When writing or running tests. |
| [MASTER-PROMPT.md](MASTER-PROMPT.md) | Once, to bootstrap the agent. |
| [ANSWERS.md](analysis/ANSWERS.md) | When something feels ambiguous. |
| [analysis/00-MASTER-INDEX.md](analysis/00-MASTER-INDEX.md) | When you want the full design tour. |
| [analysis/12-ROADMAP.md](analysis/12-ROADMAP.md) | When deciding what to do next. |
| [AI/](AI/) | Always available to the agent during work. |

## 7. Testing

End every feature with:

```powershell
.\run-tests.ps1
```

That single command:
- Verifies your device is online via ADB.
- Builds the debug + androidTest APKs.
- Runs JVM unit tests + instrumented tests.
- Optionally fires a monkey smoke (`-Monkey 2000`).
- Pulls the HTML report into `logs/test-reports/<ts>/`.
- Exits non-zero on any red.

## 8. Distribution

GitHub Releases only (Q9). The agent builds + signs the APK and
publishes both:

- The APK in your `*-releases` repo.
- A `videosplitter-version.json` describing the release.

Users download via the in-app **Settings → Updates → Check for updates**
flow, which validates SHA-256 before invoking `PackageInstaller`. See
[AI/API_USAGE.md](AI/API_USAGE.md).

## 9. What you still need to do (manual)

These are the only things the agent can't do for you:

1. Create the empty target folder for the Android repo.
2. Create the empty GitHub repo for the Android source.
3. Create the empty GitHub repo for the releases (defaults to
   `<owner>/mkvslice-releases`).
4. Re-prompt Stitch for the missing `mergeorder_light/` variant
   (K-007).
5. Provide ADB-connected physical device for instrumented tests
   (you said you already have this).
6. (Optional) Generate a release keystore (`keystore.properties` is
   read by Gradle if present; falls back to debug-signed if absent).

## 10. File checklist (copy verifying)

The agent's first commit must contain at least:

- [x] AGENTS.md
- [x] TESTING-AGENT.md
- [x] MASTER-PROMPT.md
- [x] STITCH-BRIEF.md
- [x] STITCH-PROMPT-ROUND2.md
- [x] run-tests.ps1
- [x] .github/workflows/android-test.yml
- [x] analysis/ (14 files + 3 STITCH-* + ANSWERS)
- [x] Design/ (consolidated from the two Stitch folders)
- [x] AI/ (16 docs + plans/README.md)

Plus the scaffolded items:

- [ ] .gitignore
- [ ] README.md (root, user-facing)
- [ ] gradlew, gradlew.bat, gradle/wrapper/
- [ ] settings.gradle.kts, build.gradle.kts (root)
- [ ] gradle/libs.versions.toml
- [ ] app/build.gradle.kts
- [ ] app/src/main/AndroidManifest.xml
- [ ] app/src/main/kotlin/com/splitandmerge/mkvslice/App.kt + MainActivity.kt
- [ ] logs/.gitkeep

After the first commit, the agent stops and waits for your "go" to
proceed to Phase 3.

---

## TL;DR

1. Make an empty folder for the new repo.
2. Open your favourite agent.
3. Paste [MASTER-PROMPT.md](MASTER-PROMPT.md) as system prompt.
4. Tell the agent the source (this folder) and target paths.
5. Approve at each phase gate.
6. Run `.\run-tests.ps1` after every meaningful change.
7. When `0.0.99` is reached, ASK before bumping to `1.1.0`.

That's it. Everything else is in the docs.
