package com.splitandmerge.mkvslice.domain.defaulttracks.model

data class VoidDonor(
    val offset: Long,
    val headerSize: Int,
    val payloadSize: Long,
    val type: Int // 1 = inside TrackEntry, 2 = inside Tracks, 3 = adjacent after Tracks
)
