package com.splitandmerge.mkvslice.engine.impl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Unit tests for [parseFfprobeJson] — the pure JSON parser behind ProcessFfprobeEngine.probe().
 *
 * Covers the production crash where ffmpeg-kit interleaved libav log lines into the captured
 * ffprobe output (e.g. "[eac3 @ 0x...]" on E-AC3 files, "[matroska,webm @ 0x...]" on the 59 GB
 * file), which made kotlinx.serialization throw "Unexpected JSON token ... got [".
 *
 * No mocking and no Android Context are needed: parsing is a pure function.
 */
class ProcessFfprobeEngineTest {

    private val cleanJson = """
        {
          "format": {
            "filename": "sample.mkv",
            "nb_streams": 2,
            "format_name": "matroska,webm",
            "duration": "120.500000",
            "size": "123456789",
            "bit_rate": "1500000"
          },
          "streams": [
            {
              "index": 0,
              "codec_type": "video",
              "codec_name": "h264",
              "width": 1920,
              "height": 1080
            },
            {
              "index": 1,
              "codec_type": "audio",
              "codec_name": "eac3",
              "channels": 6,
              "tags": { "language": "tel" },
              "disposition": { "default": 1 }
            }
          ]
        }
    """.trimIndent()

    @Test
    fun cleanJson_parsesAllFields() {
        val result = parseFfprobeJson(cleanJson)

        assertEquals("sample.mkv", result.format.filename)
        assertEquals(2, result.format.nbStreams)
        assertEquals("matroska,webm", result.format.formatName)
        assertEquals(120.5, result.format.durationSeconds, 0.001)
        assertEquals(123456789L, result.format.sizeBytes)
        assertEquals(1500000L, result.format.bitRate)

        assertEquals(2, result.streams.size)
        assertEquals("video", result.streams[0].codecType)
        assertEquals("h264", result.streams[0].codecName)
        assertEquals(1920, result.streams[0].width)
        assertEquals(1080, result.streams[0].height)
        assertEquals("audio", result.streams[1].codecType)
        assertEquals("eac3", result.streams[1].codecName)
        assertEquals(6, result.streams[1].channels)
        assertEquals("tel", result.streams[1].language)
        assertEquals(1, result.streams[1].disposition["default"])
    }

    /**
     * The real crash shape: "{" then a newline then an E-AC3 DECODER log line, then the rest
     * of the JSON. indexOf('{')+substring alone would NOT fix this (the '{' is at offset 0);
     * the log line must be stripped.
     */
    @Test
    fun eac3DecoderLogPollution_isStrippedAndParsed() {
        val polluted = cleanJson.replaceFirst(
            "{",
            "{\n[eac3 @ 0xb400007193dd5800] incomplete frame"
        )

        val result = parseFfprobeJson(polluted)

        assertEquals("sample.mkv", result.format.filename)
        assertEquals(2, result.streams.size)
        assertEquals("eac3", result.streams[1].codecName)
    }

    /** The 59 GB file case: a matroska/webm DEMUXER log line interleaved after "{". */
    @Test
    fun matroskaDemuxerLogPollution_isStrippedAndParsed() {
        val polluted = cleanJson.replaceFirst(
            "{",
            "{\n[matroska,webm @ 0xb400007219e000] Could not find codec parameters"
        )

        val result = parseFfprobeJson(polluted)

        assertEquals("sample.mkv", result.format.filename)
        assertEquals(2, result.streams.size)
    }

    /** Multiple interleaved log lines (a leading demuxer line + an interior decoder line). */
    @Test
    fun multipleLogLines_areStripped() {
        val polluted = "[matroska,webm @ 0x1a2b] Estimating duration from bitrate\n" +
            cleanJson.replaceFirst("{", "{\n[eac3 @ 0x3c4d] channel element error")

        val result = parseFfprobeJson(polluted)

        assertEquals("sample.mkv", result.format.filename)
        assertEquals(2, result.streams.size)
    }

    /** Output with no JSON object at all (e.g. only log lines) fails clearly, not with a parser crash. */
    @Test
    fun noJsonObject_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException::class.java) {
            parseFfprobeJson("[eac3 @ 0x1] no json present on this run")
        }
    }
}