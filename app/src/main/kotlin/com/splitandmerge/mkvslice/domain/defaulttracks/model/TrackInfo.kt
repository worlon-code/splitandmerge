package com.splitandmerge.mkvslice.domain.defaulttracks.model

data class TrackInfo(
    val trackNumber: Long,
    val trackType: Int,
    val language: String,
    val flagDefault: Int,
    val flagForced: Int,
    val name: String?,
    val codec: String,
    val byteOffset: Long,
    val flagDefaultOffset: Long?,
    val flagForcedOffset: Long?,
    val trackEntryEnd: Long,
    val voidDonors: List<VoidDonor>,
    val sizeVintWidth: Int = 2
)
