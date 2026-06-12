package com.splitandmerge.mkvslice.engine

sealed class EngineEvent {
    data class Started(val token: String) : EngineEvent()
    data class Progress(val timeSeconds: Double, val speed: Double) : EngineEvent()
    data class Stderr(val line: String) : EngineEvent()
    data class Completed(val exitCode: Int) : EngineEvent()
}
