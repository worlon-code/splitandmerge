package com.splitandmerge.mkvslice.data.update

import kotlinx.serialization.Serializable

@Serializable
data class UpdateManifest(
    val versionName: String,
    val versionCode: Int,
    val apkUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val changelog: List<String> = emptyList()
)
