package com.splitandmerge.mkvslice.data.cleanup.backup

import kotlinx.serialization.Serializable

@Serializable
data class CleanupPatternBackup(
    val regex: String,
    val replacement: String,
    val enabled: Boolean,
    val label: String
)

@Serializable
data class CleanupBackupFile(
    val version: Int = 1,
    val patterns: List<CleanupPatternBackup>
)
