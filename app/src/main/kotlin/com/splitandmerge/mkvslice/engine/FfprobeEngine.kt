package com.splitandmerge.mkvslice.engine

import kotlinx.serialization.Serializable

@Serializable
data class StreamInfo(
    val index: Int,
    val codecType: String,      // "video", "audio", "subtitle", "attachment"
    val codecName: String,
    val profile: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val channels: Int? = null,
    val sampleRate: String? = null,
    val language: String? = null,
    val title: String? = null,
    val disposition: Map<String, Int> = emptyMap()
)

@Serializable
data class FormatInfo(
    val filename: String,
    val nbStreams: Int,
    val formatName: String,
    val durationSeconds: Double,
    val sizeBytes: Long,
    val bitRate: Long
)

@Serializable
data class ProbeResult(
    val format: FormatInfo,
    val streams: List<StreamInfo>
)

interface FfprobeEngine {
    suspend fun probe(uri: String): ProbeResult
    suspend fun keyframes(uri: String): List<Double>
}
