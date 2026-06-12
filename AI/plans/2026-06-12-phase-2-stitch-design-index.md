# Phase 2 - Stitch design import and screen mapping
## Problem
Phase 2 requires confirming that the imported Stitch design exports are complete and creating a single mapping index from each design folder to the intended Compose screen. The repository already contains the extracted Stitch exports, but there is no canonical index yet, and the `Design/` folder currently contains duplicate extracted bundles and source zip files. There is also a rule conflict: the roadmap and master prompt call for `Design/INDEX.md`, while `AGENTS.md` marks `Design/` as read-only.

## Proposed change
1. Audit the current `Design/` contents and identify the canonical exported folders that should participate in the mapping.
2. Resolve the rule conflict with the user before writing any file inside `Design/`.
3. Create one approved mapping document that links each Stitch folder to:
   - the screen name from the screen spec,
   - the intended Compose destination/file name,
   - variant notes such as light/dark/tablet or dialog coverage,
   - any missing or duplicate design exports that need follow-up.
4. If approved, cleanly ignore duplicate bundle folders during indexing so the mapping references only the canonical design set and not the repeated archive extracts.
5. Update `AI/WORK_SUMMARY.md` and `AI/tasks.md` after the mapping is completed.

## Files touched (paths + intent)
- `Design/INDEX.md` or an alternate approved path: add the canonical Stitch-to-screen mapping document.
- `AI/WORK_SUMMARY.md`: record completion of Phase 2 mapping work.
- `AI/tasks.md`: move the Phase 2 items to done and note any follow-up.
- `AI/plans/2026-06-12-phase-2-stitch-design-index.md`: this plan and the decision record for the path conflict.

## Tests added/updated
- No automated code tests are expected for this phase because the work is documentation and design indexing only.
- Manual verification will be the screen-by-screen review of the mapping against the imported Stitch folders and the screen inventory in the specs.

## Migration notes
- No code or data migration.
- If the mapping document cannot live under `Design/` because of the read-only rule, the approved alternate location must be treated as the canonical source for Phase 2 going forward.

## Rollback plan
- Remove the mapping document and revert the `AI/WORK_SUMMARY.md` / `AI/tasks.md` updates.
- Leave imported design assets unchanged.

## Open questions
- Should the canonical mapping file be written to `Design/INDEX.md` as the roadmap specifies, or to a non-read-only location such as `AI/DESIGN_INDEX.md` to comply with `AGENTS.md`?
- Should the duplicate extracted Stitch bundle folders and zip archives be referenced in the index, or should the index point only at the normalized top-level design folders?
