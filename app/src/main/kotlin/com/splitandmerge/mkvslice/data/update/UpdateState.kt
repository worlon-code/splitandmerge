package com.splitandmerge.mkvslice.data.update

data class UpdateState(
    val phase: Phase = Phase.Idle,
    val manifest: UpdateManifest? = null,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val errorMessage: String? = null
)

enum class Phase {
    Idle, Checking, UpToDate,
    AvailableButDebug,
    AvailableReady,
    Downloading, Verifying,
    ReadyToInstall,
    InstallLaunched,
    Error
}
