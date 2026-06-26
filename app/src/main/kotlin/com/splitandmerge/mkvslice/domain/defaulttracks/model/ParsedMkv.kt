package com.splitandmerge.mkvslice.domain.defaulttracks.model

data class ParsedMkv(
    val docType: String,
    val segmentDataOffset: Long,
    val firstClusterOffset: Long?,
    val tracksOffset: Long?,
    val tracksSize: Long?,
    val seekHeads: List<ParsedSeekHead>,
    val tracks: List<TrackInfo>,
    val cuesOffset: Long?,
    val segmentSizeVintWidth: Int,
    val segmentSize: Long,
    val multiSegment: Boolean = false,
    val tracksSizeVintWidth: Int = 2
)

data class ParsedSeekHead(
    val offset: Long,
    val size: Long,
    val idLen: Int,
    val sizeLen: Int,
    val entries: List<SeekEntry>
)

data class SeekEntry(
    val seekId: Long,
    val seekPosition: Long
)
