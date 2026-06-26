# Phase B Enhancement — Positional Tie-Breaker + Resolved Target Display

## Problem

Season packs where every episode has the **same layout** (e.g. Track 4 = KO audio + another KO audio,
Track 6 = KO sub + another KO sub) cause batch matching to fall into the AMBIGUITY guard and mark every
episode "Needs Review", defeating one-tap apply.

Additionally the Configure Tracks list shows generic badges ("AUD: UND / will modify") instead of the
concrete target track, so the user cannot verify the outcome without opening each editor.

## Proposed Change

### (A) Positional Tie-Breaker — BatchMatcher.kt + Preference + ViewModel

Add **Rung 0 — POSITIONAL** (highest priority) to `runLadder()`:

> If the seed's chosen track number is present in the candidate's same-language track list,
> select that track immediately — before ALREADY-DEFAULT and all other rungs.

Changes:
- **`Preference`** — add two nullable fields: `seedAudioTrackNumber: Long?` and `seedSubTrackNumber: Long?`
- **`BatchMatcher.runLadder()`** — new Rung 0 at the top: match by `trackNumber == seedTrackNumber`
- **`DefaultTracksViewModel.applyToSimilar()`** — populate the two new fields from `seedAudioChoice` / `seedSubChoice`

### (B) Resolved Target Display — DefaultTracksFlowScreen.kt

In `FileRowItem`, after the existing language-badge row, render a new compact "target" row when
`file.matchState == MATCHED` and `file.chosenSpec != null`:

```
→ AUD: Track 4 (KOR)  SUB: Track 6 (KOR)
```

If `chosenSpec.defaultSubtitleTrackNumber == null` → `SUB: None`
If `chosenSpec.forcedSubtitle` → append `[F]`

### (C) Tests — 5 new JUnit tests in BatchMatcherTest.kt

1. `positionalAudioPicksExactTrackOverDefault` — same-lang, Rung 0 wins over flagDefault
2. `positionalAudioPicksCorrectWhenNoDefault` — same-lang no default, Rung 0 resolves (no PARTIAL_NEEDS_REVIEW)
3. `positionalMissingFallsToAlreadyDefault` — seed track number absent in candidate → Rung 1 takes over
4. `positionalSubtitlePicksExactTrack` — subtitle dimension: Rung 0 by track number
5. `positionalBothAudioAndSubResolved` — both dimensions resolved positionally

## Files Touched

| File | Intent |
|------|--------|
| `BatchMatcher.kt` | Add `seedAudioTrackNumber`/`seedSubTrackNumber` to `Preference`; add Rung 0 to `runLadder()` |
| `DefaultTracksViewModel.kt` | Populate new `Preference` fields in `applyToSimilar()` |
| `DefaultTracksFlowScreen.kt` | Render resolved-target line in `FileRowItem` |
| `BatchMatcherTest.kt` | 5 new positional tests |

**NOT touched:** Engine, EditSpec, Room, serializers, JobService, LanguageNormaliser, LanguageNormaliserTest, DefaultTracksViewModelTest, DefaultTracksUiTest, HiltTestRunner.

## Tests Added

5 new JVM unit tests in `BatchMatcherTest.kt` using `org.junit.Assert.*`.

## Migration Notes

`Preference` gets two new nullable fields with default `null` — all existing call sites that construct `Preference` without these fields need the parameters added as named args with `null`.

## Rollback Plan

Revert `BatchMatcher.kt`, `DefaultTracksViewModel.kt`, `DefaultTracksFlowScreen.kt`, `BatchMatcherTest.kt` to HEAD.

## Open Questions

None — scope is fully specified by user.
