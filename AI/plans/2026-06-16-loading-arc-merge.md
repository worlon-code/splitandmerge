# Step 4b: LoadingArc on Merge Pick-Parts

This change addresses K-017 by displaying a visual progress indicator (`LoadingArc`) when ffprobe processes picked video parts in the merge flow.

## Problem
When a user picks multiple/large MKV parts in the merge flow, `MergeOrderViewModel.addParts()` runs ffprobe on each to parse durations. This can take 5-30 seconds, and currently leaves the UI completely idle with no feedback.

## Proposed Change
- Introduce `verifying: Boolean` to `MergeOrderState`.
- Set `verifying = true` during `addParts` execution, wrapping it in `try/finally` to set `verifying = false` on completion/errors.
- Update `MergeOrderScreen` to render the custom `LoadingArc` component and a "Verifying parts..." label when `verifying == true`.
- Implement unit tests covering successful probing and failure scenarios.

## Files touched
- [MergeOrderViewModel.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/ui/mergeorder/MergeOrderViewModel.kt)
- [MergeOrderScreen.kt](file:///d:/Repos/splitandmerge/app/src/main/kotlin/com/splitandmerge/mkvslice/ui/mergeorder/MergeOrderScreen.kt)
- [MergeOrderViewModelTest.kt](file:///d:/Repos/splitandmerge/app/src/test/kotlin/com/splitandmerge/mkvslice/ui/mergeorder/MergeOrderViewModelTest.kt)

## Tests added/updated
- `test_addParts_emitsVerifyingTrue_thenFalse_onSuccess`
- `test_addParts_emitsVerifyingFalse_onException`

## Migration notes
None.

## Rollback plan
Revert ViewModel state additions and screen conditional layout changes.
