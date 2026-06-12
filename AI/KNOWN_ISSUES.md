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
| K-006 | MINOR | OPEN | Filename cleanup may strip a noise token that's actually part of the title (e.g. "True" in "True Lies"). User can edit the cleaned title in S5 before continuing. v0.0.4 adds a per-job "disable cleanup" toggle. |
| K-007 | MAJOR | OPEN | Stitch design pack: `mergeorder_light/` not yet generated. Re-prompt scheduled. |
| K-008 | KNOWN | OPEN | Settings → Reliability → "Improve reliability on this device" only displays the OEM helper text on Xiaomi/OnePlus/Huawei/Realme; works on all OEMs. |
| K-009 | KNOWN | OPEN | x86_64 emulator can't run the engine smoke test (no HEVC decoder on most images). Tests are gated with `assumeTrue` on `Build.SUPPORTED_64_BIT_ABIS.contains("arm64-v8a")`. CI emulator is x86_64 → engine smoke runs only on physical devices. |

## Resolved

(none yet — populate as fixes land)

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
