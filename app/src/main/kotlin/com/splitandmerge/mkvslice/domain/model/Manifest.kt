package com.splitandmerge.mkvslice.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Manifest(
    val schema: Int = 1,
    val source: ManifestSource,
    val parts: List<ManifestPart>,
    val ffmpegVersion: String,
    val appVersion: String,
)

@Serializable
data class ManifestSource(
    val name: String,
    val size: Long,
    val durationSeconds: Double,
    val sha256First64MB: String,
    val video: ManifestVideo,
    val audio: List<ManifestAudio>,
    val subs: List<ManifestSub>,
)

@Serializable
data class ManifestVideo(
    val codec: String,
    val width: Int,
    val height: Int,
    val hdr: String
)

@Serializable
data class ManifestAudio(
    val codec: String,
    val lang: String? = null
)

@Serializable
data class ManifestSub(
    val codec: String,
    val lang: String? = null
)

@Serializable
data class ManifestPart(
    val index: Int,
    val name: String,
    val start: Double,
    val end: Double,
    val size: Long,
)
