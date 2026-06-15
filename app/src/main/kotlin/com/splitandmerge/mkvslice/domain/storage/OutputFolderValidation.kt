package com.splitandmerge.mkvslice.domain.storage

sealed class OutputFolderValidation {
    data object Ok : OutputFolderValidation()
    data object NotReachable : OutputFolderValidation()
    data object PermissionRevoked : OutputFolderValidation()
    data class NotWritable(val reason: String) : OutputFolderValidation()
    data class InsufficientSpace(
        val needed: Long,
        val have: Long
    ) : OutputFolderValidation()
}
