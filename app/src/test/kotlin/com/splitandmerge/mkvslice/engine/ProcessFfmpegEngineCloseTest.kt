package com.splitandmerge.mkvslice.engine

import com.antonkarpenko.ffmpegkit.FFmpegKit
import com.antonkarpenko.ffmpegkit.FFmpegSession
import com.splitandmerge.mkvslice.engine.impl.ProcessFfmpegEngine
import io.mockk.*

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import com.antonkarpenko.ffmpegkit.FFmpegSessionCompleteCallback
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import com.splitandmerge.mkvslice.engine.EngineEvent

class ProcessFfmpegEngineCloseTest {

    private lateinit var engine: ProcessFfmpegEngine

    @Before
    fun setup() {
        mockkStatic(FFmpegKit::class)
        mockkStatic(android.util.Log::class)
        mockkStatic(timber.log.Timber::class)

        // Mock timber
        val timberTree = mockk<timber.log.Timber.Tree>(relaxed = true)
        timber.log.Timber.plant(timberTree)

        engine = ProcessFfmpegEngine()
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `ProcessFfmpegEngine completeCallback does not crash and emits Completed`() = runBlocking {
        // We simulate the executeWithArgumentsAsync call
        val sessionMock = mockk<FFmpegSession>(relaxed = true)
        every { sessionMock.returnCode?.value } returns 0
        every { sessionMock.sessionId } returns 123L

        every {
            FFmpegKit.executeWithArgumentsAsync(
                any(),
                any(),
                any(),
                any()
            )
        } answers {
            // invoke completeCallback immediately
            val completeCb = it.invocation.args[1] as FFmpegSessionCompleteCallback
            Thread {
                completeCb.apply(sessionMock)
            }.start()
            sessionMock
        }

        val flow = engine.execute(listOf("-version"))
        val events = flow.toList()

        // It should have emitted Started and Completed
        assertTrue(events.any { it is EngineEvent.Started })
        assertTrue(events.any { it is EngineEvent.Completed && it.exitCode == 0 })
    }

    /**
     * Verifies that cancel("all") calls FFmpegKit.cancel() for every registered session
     * and drains activeSessions completely. Uses reflection to prime the internal map.
     */
    @Test
    fun `cancel all drains every active session`() = runBlocking {
        every { FFmpegKit.cancel(any<Long>()) } just Runs

        // Prime activeSessions via reflection (avoids needing live FFmpegKit sessions).
        val field = engine.javaClass.getDeclaredField("activeSessions").also {
            it.isAccessible = true
        }
        @Suppress("UNCHECKED_CAST")
        val activeSessions = field.get(engine)
            as java.util.concurrent.ConcurrentHashMap<String, Long>

        activeSessions["token-1"] = 10L
        activeSessions["token-2"] = 20L
        activeSessions["token-3"] = 30L

        engine.cancel("all")

        // All sessions should have been cancelled and removed.
        verify(exactly = 1) { FFmpegKit.cancel(10L) }
        verify(exactly = 1) { FFmpegKit.cancel(20L) }
        verify(exactly = 1) { FFmpegKit.cancel(30L) }
        assertTrue("activeSessions should be empty after cancel(all)", activeSessions.isEmpty())
    }
}

