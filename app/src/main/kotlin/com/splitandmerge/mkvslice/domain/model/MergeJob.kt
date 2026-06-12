package com.splitandmerge.mkvslice.domain.model

data class MergeJob(
    val id: String,
    val title: String,
    val partsInOrder: List<PartRef>,
    val outputUri: String,
    val outputContainer: String,
)

data class PartRef(
    val index: Int,
    val uri: String,
    val sizeBytes: Long,
    val codecSignature: String,         // for pre-merge validation
)
