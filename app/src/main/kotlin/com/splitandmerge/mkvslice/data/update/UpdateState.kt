package com.splitandmerge.mkvslice.data.update

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState

    data class Available(
        val versionCode: Int,
        val versionName: String,
        val changelog: List<String>,
        val url: String,
        val sha256: String,
        val size: Long
    ) : UpdateState

    data class Downloading(val progress: Float) : UpdateState
    data object Verifying : UpdateState
    data object NeedsInstallPermission : UpdateState
    data object ReadyToInstall : UpdateState
    data object Installing : UpdateState
    data object Installed : UpdateState
    data class Error(val reason: String) : UpdateState
}
