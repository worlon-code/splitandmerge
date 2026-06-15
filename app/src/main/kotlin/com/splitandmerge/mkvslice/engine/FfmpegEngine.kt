package com.splitandmerge.mkvslice.engine

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.Flow

interface FfmpegEngine {
    suspend fun version(): String
    fun execute(args: List<String>): Flow<EngineEvent>
    suspend fun cancel(token: String)
}
