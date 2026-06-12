# Plans

> Plan / MD files awaiting or received user approval. The agent writes a
> file here BEFORE starting non-trivial work, per
> [AGENTS.md §2](../../AGENTS.md). The user reads, approves (or asks for
> edits), and only then does the agent implement.

## Naming

```
YYYY-MM-DD-<short-kebab-title>.md
```

e.g. `2026-06-12-add-resume-job.md`.

## Template

```markdown
# <Title>
## Problem
<one paragraph>
## Proposed change
<numbered steps>
## Files touched (paths + intent)
<list>
## Tests added/updated
<list>
## Migration notes
<row per breaking change, or "none">
## Rollback plan
<one paragraph>
## Open questions
<list>
```

## Status convention

A plan file's first line after the title can be one of:

- `Status: DRAFT` — not yet shown to the user.
- `Status: AWAITING APPROVAL` — shown; no answer yet.
- `Status: APPROVED <YYYY-MM-DD>` — implementation may proceed.
- `Status: REJECTED <YYYY-MM-DD>` — keep file for history; do not implement.
- `Status: SUPERSEDED BY <other plan>` — replaced by a later plan.

## What lives here

- Architecture changes (new module, new layer, layer rename).
- Engine changes (FFmpeg artefact, new flag, new fallback path).
- Schema migrations (Room).
- New screens or non-trivial UI restructures.
- Anything touching > 1 file beyond a single bug fix.

## What does NOT live here

- Single-line bug fixes (just open a PR).
- Doc updates (handled inline; see release-doc gate in
  [AGENTS.md §8](../../AGENTS.md)).
- Test-only changes.

## Lifecycle

1. Agent writes plan → `Status: DRAFT`.
2. Agent presents to user; status flips to `Status: AWAITING APPROVAL`.
3. User: "approve" / "rework" / "reject".
4. Agent updates status, then either:
   - Implements (APPROVED).
   - Edits and re-presents (REWORK).
   - Stops (REJECTED).
5. After implementation, the plan stays here as historical record. Move
   the matching task in `tasks.md` to `DONE-v<x.y.z>`.
