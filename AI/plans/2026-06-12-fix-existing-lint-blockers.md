# Fix existing lint blockers
## Problem
The required Phase 2 verification flow is blocked by 3 pre-existing lint errors that are unrelated to the new design-index documentation:

1. `app/src/main/AndroidManifest.xml`
   - `AppLinkUrlError` on the `VIEW` intent filter because it declares MIME types without the URL structure lint expects for deep/app links.
2. `app/src/main/res/xml/data_extraction_rules.xml`
   - Two `FullBackupContent` errors because `exclude` entries are missing the required `domain` attribute.

Without fixing these, the repo cannot satisfy the mandatory `lint -> test -> debug build -> install` workflow.

## Proposed change
1. Adjust the `VIEW` intent filter in `AndroidManifest.xml` so it reflects the intended file-open behavior without triggering app-link validation.
2. Correct `data_extraction_rules.xml` so the backup exclusion rules use valid `domain` attributes.
3. Re-run:
   - `./gradlew.bat lint --warning-mode=summary`
   - `./gradlew.bat test`
   - `./run-tests.ps1`
4. If those pass, run:
   - `./gradlew.bat assembleDebug`
   - ADB device check
   - debug APK install if exactly one device is connected
5. Capture logs under `logs/` for each required step.

## Files touched (paths + intent)
- `app/src/main/AndroidManifest.xml`: fix the invalid `VIEW` intent filter.
- `app/src/main/res/xml/data_extraction_rules.xml`: add valid backup-rule domains.
- `AI/WORK_SUMMARY.md`: record the lint-blocker fix after successful verification.
- `AI/tasks.md`: note completion if the lint-blocker follow-up is closed.

## Tests added/updated
- No new automated tests expected unless the manifest/resource fixes expose a gap.
- Mandatory verification:
  - `lint`
  - `test`
  - `run-tests.ps1`
  - `assembleDebug`
  - debug install if a device is connected

## Migration notes
- No schema or user-data migration.
- Backup rule changes only affect auto-backup configuration metadata.

## Rollback plan
- Revert the manifest and backup-rule edits.
- Re-run lint to confirm the repo returns to its prior state.

## Open questions
- None, assuming the goal is to unblock the required verification workflow with the minimal safe fix.
