package com.splitandmerge.mkvslice.domain.merger

import com.splitandmerge.mkvslice.engine.FfprobeEngine
import com.splitandmerge.mkvslice.engine.ProbeResult
import com.splitandmerge.mkvslice.engine.FormatInfo
import com.splitandmerge.mkvslice.engine.StreamInfo
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class MergeValidatorTest {

    private lateinit var ffprobeEngine: FfprobeEngine
    private lateinit var mergeValidator: MergeValidator

    @Before
    fun setup() {
        ffprobeEngine = mockk()
        mergeValidator = MergeValidator(ffprobeEngine)
    }

    @Test
    fun `validate passes for identical parts`() = runBlocking {
        val uri1 = "part1.mkv"
        val uri2 = "part2.mkv"

        val stream1 = StreamInfo(index = 0, codecName = "hevc", codecType = "video", width = 1920, height = 1080)
        val stream2 = StreamInfo(index = 1, codecName = "aac", codecType = "audio", channels = 2, sampleRate = "48000")

        val format = FormatInfo("part.mkv", 2, "matroska", 10.0, 1000L, 800L)
        val result = ProbeResult(format, listOf(stream1, stream2))

        coEvery { ffprobeEngine.probe(uri1) } returns result
        coEvery { ffprobeEngine.probe(uri2) } returns result

        // Should not throw
        mergeValidator.validate(listOf(uri1, uri2))
    }

    @Test
    fun `validate fails for different stream count`() = runBlocking {
        val uri1 = "part1.mkv"
        val uri2 = "part2.mkv"

        val stream1 = StreamInfo(index = 0, codecName = "hevc", codecType = "video")
        val stream2 = StreamInfo(index = 1, codecName = "aac", codecType = "audio")

        val format = FormatInfo("part.mkv", 2, "matroska", 10.0, 1000L, 800L)
        val result1 = ProbeResult(format, listOf(stream1, stream2))
        val result2 = ProbeResult(format, listOf(stream1)) // Missing audio

        coEvery { ffprobeEngine.probe(uri1) } returns result1
        coEvery { ffprobeEngine.probe(uri2) } returns result2

        val exception = assertThrows(IllegalStateException::class.java) {
            runBlocking { mergeValidator.validate(listOf(uri1, uri2)) }
        }
        assertEquals("Stream count mismatch between parts. Part 1 has 2 streams, Part 2 has 1.", exception.message)
    }

    @Test
    fun `validate fails for different video resolution`() = runBlocking {
        val uri1 = "part1.mkv"
        val uri2 = "part2.mkv"

        val stream1Ref = StreamInfo(index = 0, codecName = "hevc", codecType = "video", width = 1920, height = 1080)
        val stream1Cur = StreamInfo(index = 0, codecName = "hevc", codecType = "video", width = 1280, height = 720)

        val format = FormatInfo("part.mkv", 1, "matroska", 10.0, 1000L, 800L)
        val result1 = ProbeResult(format, listOf(stream1Ref))
        val result2 = ProbeResult(format, listOf(stream1Cur))

        coEvery { ffprobeEngine.probe(uri1) } returns result1
        coEvery { ffprobeEngine.probe(uri2) } returns result2

        val exception = assertThrows(IllegalStateException::class.java) {
            runBlocking { mergeValidator.validate(listOf(uri1, uri2)) }
        }
        assertEquals("Resolution mismatch in video stream 0. Part 1 is 1920x1080, Part 2 is 1280x720.", exception.message)
    }
}
