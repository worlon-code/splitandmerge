package com.splitandmerge.mkvslice.domain.model

data class ProbeResult(
    val sourceUri: String,
    val originalFilename: String,
    val sizeBytes: Long,
    val durationSeconds: Double,
    val containerLabel: String,
    val containerExtension: String,
    val avgBitrate: Long,
    val video: VideoStream,
    val audio: List<AudioStream>,
    val subtitles: List<SubtitleStream>,
    val chapters: Int,
    val attachments: Int,
)

data class VideoStream(
    val codec: String,
    val codecLong: String,
    val width: Int,
    val height: Int,
    val frameRate: Double,
    val pixFmt: String,
    val bitDepth: Int,
    val hdr: HdrType,
    val bitrate: Long,
)

data class AudioStream(
    val index: Int,
    val codec: String,
    val codecLong: String,
    val language: String?,
    val channels: Int,
    val sampleRate: Int,
    val bitrate: Long,
    val isDefault: Boolean,
)

data class SubtitleStream(
    val index: Int,
    val codec: String,
    val language: String?,
    val isDefault: Boolean,
    val isBitmap: Boolean,
)

enum class HdrType { SDR, HDR10, HDR10Plus, DolbyVision }
