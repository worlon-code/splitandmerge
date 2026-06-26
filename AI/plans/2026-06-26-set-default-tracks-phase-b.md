# Set Default Tracks — Phase B (Language & Region Matching)

## Problem
Currently, the "Set Default Tracks" manual batch flow requires checking files individually and editing their tracks one-by-one. We need an automated way to match and apply configurations across similar files based on language and region preferences.

## Proposed Changes
We will implement pure Kotlin language normalization and batch matching logic, and wire it into the ViewModel and UI without touching any byte-editing engine code, Room tables, or the background job executor.

### Files touched:
- [LanguageNormaliser.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/domain/defaulttracks/LanguageNormaliser.kt) (NEW)
- [BatchMatcher.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/domain/defaulttracks/BatchMatcher.kt) (NEW)
- [DefaultTracksViewModel.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/ui/defaulttracks/DefaultTracksViewModel.kt) (MODIFY)
- [DefaultTracksFlowScreen.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/ui/defaulttracks/DefaultTracksFlowScreen.kt) (MODIFY)
- [LanguageNormaliserTest.kt](file:///d:/Repos/splitandmerge/app/src/test/kotlin/com/splitandmerge/mkvslice/domain/defaulttracks/LanguageNormaliserTest.kt) (NEW)
- [BatchMatcherTest.kt](file:///d:/Repos/splitandmerge/app/src/test/kotlin/com/splitandmerge/mkvslice/domain/defaulttracks/BatchMatcherTest.kt) (NEW)

## Verification Plan
- JVM unit tests covering language mapping, doublet collapse, region matching, tie-breakers, and commentary de-prioritization.
- ViewModel test asserting matching/stamping logic.
- UI test asserting button enabling and selection behavior.
- Run gates: `./gradlew lintDebug`, `./gradlew testDebugUnitTest`, `./gradlew connectedDebugAndroidTest`, `./gradlew assembleRelease`.
