# Known Issues

> Living document. Every "we know it's broken / not yet implemented" goes
> here, with severity and a planned fix release. The agent appends rows
> here at the moment of discovery — never silently ignores.

Severity legend:

| Tag | Meaning |
|---|---|
| `BLOCK` | Blocks v1 release. Must be fixed before tagging. |
| `MAJOR` | Functional gap visible to many users; ship with caveat in changelog. |
| `MINOR` | Edge case or polish item; backlog. |
| `KNOWN` | Documented v1 limitation; will not fix in v1. |

Status legend: `OPEN`, `IN-PROGRESS`, `FIXED-IN-vX.Y.Z`.

---

## Open at v0.0.1 scaffold time (2026-06-12)

| ID | Severity | Status | Title |
|---|---|---|---|
| K-001 | KNOWN | OPEN | No resume after a cancelled / interrupted job (re-run starts at part 1). Lands in v0.0.3. |
| K-002 | KNOWN | OPEN | Cloud-backed inputs (e.g. Drive content URIs) are refused. Workaround: copy to local storage. v0.0.2 adds an "Import to app cache" path. |
| K-003 | KNOWN | OPEN | No two-pane treatment yet for S4, S7, S8, S15. Phone-style layout is rendered on tablets. v0.0.5+. |
| K-004 | KNOWN | OPEN | Single-keyframe / static-GOP files cannot split. Error sheet directs the user to "Open with another app". A re-encode fallback lands in v1.1+. |
| K-005 | MINOR | OPEN | Subtitles spanning a cut boundary appear at the start of the next part instead of the end of the previous one. Documented in app help text. |
| K-006 | MINOR | OPEN | Filename cleanup may strip a noise token that's actually part of the title (e.g. "True" in "True Lies"). User can edit the cleaned title in S5 before continuing. v0.0.4 adds a per-job "disable cleanup" toggle. (Mitigated in v0.0.9 — TRUE/REAL removed from default pattern set; user-added patterns can still hit this.) |
| K-007 | MAJOR | OPEN | Stitch design pack: `mergeorder_light/` not yet generated. Re-prompt scheduled. |
| K-008 | KNOWN | OPEN | Settings → Reliability → "Improve reliability on this device" only displays the OEM helper text on Xiaomi/OnePlus/Huawei/Realme; works on all OEMs. |
| K-009 | KNOWN | OPEN | x86_64 emulator can't run the engine smoke test (no HEVC decoder on most images). Tests are gated with `assumeTrue` on `Build.SUPPORTED_64_BIT_ABIS.contains("arm64-v8a")`. CI emulator is x86_64 → engine smoke runs only on physical devices. |
| K-018 | MINOR | OPEN | Round-trip drift on long sources. BySize and ByParts merges add ~4 s (~0.09%) to 4393 s source on M51. Audio/video/subs stay in sync. Planned fix in v0.0.12. |
| K-019 | MINOR | OPEN | Merger unit-test seams. MergerFastPathTest uses real tmpdir filesystem, mockkConstructor(FileInputStream), hardcoded staged_part_N paths in assertions, println side-channel in Log.e mocks. Planned fix in v0.0.11. |
| K-021 | MINOR | OPEN | Merged output ~571 KB smaller than sum of parts. 3-part input totals 1,029,345,683 bytes; merged output is 1,028,760,434 bytes (~571 KB delta). Likely cause: MKV container header dedup during concat. Planned investigation in v0.0.11+. |
| K-022 | MINOR | OPEN | Use du instead of df for verification cache sampling. df samples partition-wide bytes, contaminated by background app I/O during Step 3-v2. Action: replace `df` with `du` via run-as in the verification protocol. Planned process improvement in v0.0.11+. |

## Resolved

| ID | Severity | Status | Title |
|---|---|---|---|
| K-020 | MAJOR | FIXED-IN-v0.0.10 | Fast-path crash on scoped-storage SAF inputs. added input-readability probe (1-byte read) and output-writability probe (create/delete) inside Merger's canFastPath gate. On any probe failure, Merger logs a Timber warning and falls back to staging. |
| K-017 | MINOR | FIXED-IN-v0.0.9 | Merge "Pick parts" flow showed no progress indicator while MergeOrderViewModel.addParts() ran ffprobe on each selected part. Fixed by adding `verifying: Boolean` to MergeOrderState and rendering LoadingArc + disabled action buttons in MergeOrderScreen. |
| K-016 | MAJOR | FIXED-IN-v0.0.8 | SplitResultScreen showed hardcoded "Bahubali (2025).part1.mkv / part2 / part3" on every split regardless of the actual source. Fixed by introducing SplitResultViewModel that loads real PartEntity rows from JobDao. Also removed dead JobsScreen/JobsScreenTablet/JobsViewModel/Routes.JOBS scaffolding from Phase 3 that was never wired into AppNav. |
| K-015 | MAJOR | FIXED-IN-v0.0.8 | Merger, Splitter, and MergeResultScreen folder naming collision (SAF rename suffix) crash. |
| K-014 | KNOWN | FIXED-IN-v0.0.7 | Merged output duration drifts by ≈3 s relative to the sum of input part durations. Fixed by positioning `-ss` before `-i` in the Splitter for fast seek and using identical cut timestamps verbatim. |
| K-013 | MAJOR | FIXED-IN-v0.0.5 | FFmpegKit concat demuxer native JNI saf_close crash on unattached pthread. Fixed structurally by implementing stage-then-concat to cache and merge via standard file system paths. |


## How to add a row

```
| K-NNN | <severity> | OPEN | <one-line description ending with a planned release> |
```

Then add the same K-NNN to `tasks.md` under the appropriate section.

## Closing a row

When fixed:

1. Mark status `FIXED-IN-v<x.y.z>`.
2. Move the row to "Resolved".
3. Add a 🔧 BUG FIXES line in `CHANGELOG.md` referencing K-NNN.
