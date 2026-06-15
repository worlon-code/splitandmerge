package com.splitandmerge.mkvslice.engine.impl

import com.antonkarpenko.ffmpegkit.FFmpegKit
import com.splitandmerge.mkvslice.engine.EngineEvent
import com.splitandmerge.mkvslice.engine.FfmpegEngine
import com.splitandmerge.mkvslice.engine.ProgressParser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class ProcessFfmpegEngine @Inject constructor() : FfmpegEngine {

    private val activeSessions = ConcurrentHashMap<String, Long>() // token -> FFmpeg session id

    override suspend fun version(): String = suspendCancellableCoroutine { cont ->
        FFmpegKit.executeAsync("-version") { session ->
            val out = session.allLogsAsString?.trim() ?: "unknown"
            cont.resume(out.lineSequence().firstOrNull()?.trim() ?: "unknown")
        }
    }

    override fun execute(args: List<String>): Flow<EngineEvent> = callbackFlow {
        val token = UUID.randomUUID().toString()
        trySend(EngineEvent.Started(token))

        Timber.tag("ENGINE").d("start token=%s args=%s", token, args)

        val session = FFmpegKit.executeWithArgumentsAsync(
            args.toTypedArray(),
            /* completeCallback */ { session ->
                val exit = session.returnCode?.value ?: -1
                Timber.tag("ENGINE").d("done token=%s exit=%d", token, exit)
                activeSessions.remove(token)
                trySend(EngineEvent.Completed(exit))
                close()
            },
            /* logCallback */ { log ->
                val line = log.message?.trimEnd() ?: return@executeWithArgumentsAsync
                Timber.tag("ENGINE").v("stderr %s", line)
                trySend(EngineEvent.Stderr(line))
                ProgressParser.parseLine(line)?.let { progress ->
                    trySend(progress)
                }
            },
            /* statisticsCallback */ null
        )

        activeSessions[token] = session.sessionId
        Timber.tag("ENGINE").d("start sessionId=%d token=%s", session.sessionId, token)

        awaitClose {
            // Flow cancelled → send SIGINT to FFmpeg
            activeSessions.remove(token)?.let { sessionId ->
                Timber.tag("ENGINE").d("cancel sessionId=%d token=%s", sessionId, token)
                FFmpegKit.cancel(sessionId)
            }
        }
    }

    override suspend fun cancel(token: String) {
        if (token == "all") {
            // Cancel every active session. Used by JobService.cancelCurrentJob().
            activeSessions.keys.toList().forEach { t ->
                activeSessions.remove(t)?.let { sessionId ->
                    Timber.tag("ENGINE").d("cancel(all) sessionId=%d token=%s", sessionId, t)
                    FFmpegKit.cancel(sessionId)
                }
            }
        } else {
            activeSessions.remove(token)?.let { sessionId ->
                Timber.tag("ENGINE").d("cancel(explicit) sessionId=%d token=%s", sessionId, token)
                FFmpegKit.cancel(sessionId)
            }
        }
    }
}
