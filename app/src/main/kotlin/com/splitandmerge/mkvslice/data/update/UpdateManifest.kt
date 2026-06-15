package com.splitandmerge.mkvslice.data.update

import kotlinx.serialization.Serializable

@Serializable
data class UpdateManifest(
    val version: String,                  // e.g. "0.0.9"
    val versionCode: Int,                 // monotonic
    val apkUrl: String,                   // HTTPS REQUIRED
    val sha256: String,                   // 64-char hex
    val sizeBytes: Long,                  // expected APK size
    val releaseNotesUrl: String? = null,  // optional
    val minSupportedVersionCode: Int = 0  // for forced upgrades
)
