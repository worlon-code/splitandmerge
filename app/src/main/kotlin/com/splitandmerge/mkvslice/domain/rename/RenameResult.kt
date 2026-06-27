package com.splitandmerge.mkvslice.domain.rename

sealed interface RenameStatus {
    object Success : RenameStatus
    data class Failed(val reason: String) : RenameStatus
    data class Skipped(val reason: String) : RenameStatus
    data class Excluded(val reason: String) : RenameStatus
}

data class RenameResult(
    val uri: String,
    val oldName: String,
    val newName: String,
    val status: RenameStatus
)

data class RenameBatchResult(
    val results: List<RenameResult>,
    val successCount: Int,
    val failedCount: Int,
    val skippedCount: Int,
    val excludedCount: Int
)
