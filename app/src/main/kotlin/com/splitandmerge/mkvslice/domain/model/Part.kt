package com.splitandmerge.mkvslice.domain.model

enum class PartStatus {
    PENDING,
    RUNNING,
    DONE,
    FAILED
}

data class Part(
    val id: String,
    val jobId: String,
    val index: Int,
    val name: String,
    val startSec: Double,
    val endSec: Double,
    val sizeBytes: Long? = null,
    val sha256: String? = null,
    val status: PartStatus
)
