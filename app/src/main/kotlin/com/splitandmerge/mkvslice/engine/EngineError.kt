package com.splitandmerge.mkvslice.engine

sealed class EngineError : Exception() {
    data class InsufficientStorage(val needed: Long, val have: Long) : EngineError() {
        override val message: String
            get() = "Insufficient storage: Merger needs %.2f GB in app cache, but only %.2f GB is available.".format(
                needed.toDouble() / (1024 * 1024 * 1024),
                have.toDouble() / (1024 * 1024 * 1024)
            )
    }
    data class InputUnreadable(val uri: String, val reason: String) : EngineError() {
        override val message: String get() = "Unreadable input $uri: $reason"
    }
    data class OutputWritePermission(val uri: String) : EngineError()
    data class CodecMismatch(val partIdx: Int, val detail: String) : EngineError()
    data object Cancelled : EngineError()
    data class Other(val exitCode: Int, val stderrTail: String) : EngineError()
}
