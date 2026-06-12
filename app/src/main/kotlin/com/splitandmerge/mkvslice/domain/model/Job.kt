package com.splitandmerge.mkvslice.domain.model

enum class JobType {
    SPLIT,
    MERGE
}

enum class JobStatus {
    QUEUED,
    RUNNING,
    DONE,
    FAILED,
    CANCELLED
}

data class Job(
    val id: String,
    val type: JobType,
    val status: JobStatus,
    val createdAt: Long,
    val updatedAt: Long,
    val inputPathUri: String,
    val outputPathUri: String,
    val outputBaseName: String,
    val mode: SplitMode? = null,
    val requestedParts: Int? = null,
    val maxPartBytes: Long? = null,
    val manifestPath: String? = null,
    val errorDetails: String? = null,
    val progress: Float = 0f
)
