package com.splitandmerge.mkvslice.ui.rename

import com.splitandmerge.mkvslice.domain.rename.RenameDecision

data class RenameFileRowState(
    val id: String,
    val uri: String,
    val displayName: String,
    val sizeBytes: Long,
    val parentKey: String,
    val parentKnown: Boolean,
    val supportsRename: Boolean,
    val existingNamesInParent: List<String>,
    val isChecked: Boolean = true,
    val originalBaseName: String,
    val extension: String,
    val newBaseName: String,
    val decision: RenameDecision = RenameDecision.NO_CHANGE,
    val targetName: String = displayName,
    /**
     * True for rows that came from the OpenMultipleDocuments picker path.
     * Used for: (a) showing the "(N) suffix" caption in STEP 8 preview,
     * (b) selecting the try-then-retry-(N) apply strategy in STEP 9.
     * NOT propagated to RenameInputItem — the planner stays pure.
     */
    val isPickedFile: Boolean = false
)

