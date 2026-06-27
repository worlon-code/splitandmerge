package com.splitandmerge.mkvslice.domain.rename

sealed interface RenameOutcome {
    data class Success(val actualName: String) : RenameOutcome
    data class Failure(val errorMessage: String, val isPermissionError: Boolean = false) : RenameOutcome
}

interface DocumentRenamer {
    fun rename(uri: String, newName: String): RenameOutcome
    fun supportsRename(uri: String): Boolean
}
