package com.splitandmerge.mkvslice.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.antonkarpenko.ffmpegkit.FFmpegKit
import com.antonkarpenko.ffmpegkit.FFprobeKit
import com.antonkarpenko.ffmpegkit.ReturnCode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class EngineSmokeTest {

    private lateinit var workingDir: File
    private lateinit var sourceMp4: File
    private lateinit var subtitleAss: File
    private lateinit var fixtureMkv: File

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        workingDir = File(context.cacheDir, "engine-smoke").apply { mkdirs() }
        sourceMp4 = copyAsset("fixture_source_hevc.mp4")
        subtitleAss = copyAsset("fixture_subtitle.ass")
        fixtureMkv = File(workingDir, "fixture.mkv")

        if (!fixtureMkv.exists() || fixtureMkv.length() == 0L) {
            val muxCommand = buildString {
                append("-y -i ")
                append(quoted(sourceMp4))
                append(" -i ")
                append(quoted(subtitleAss))
                append(" -map 0:v -map 0:a? -map 1:0 ")
                append("-c:v copy -c:a copy -c:s ass -f matroska ")
                append(quoted(fixtureMkv))
            }
            val session = FFmpegKit.execute(muxCommand)
            assertTrue("fixture mux failed: ${session.allLogsAsString}", ReturnCode.isSuccess(session.returnCode))
            assertTrue("fixture.mkv was not created", fixtureMkv.exists() && fixtureMkv.length() > 0L)
        }
    }

    @Test
    fun versionReportsLgplOnly() {
        val session = FFmpegKit.execute("-version")
        val out = session.allLogsAsString.orEmpty()

        assertTrue("FFmpeg -version output did not contain 'libavformat'. Output was: '$out'", out.contains("libavformat"))
        assertFalse("FFmpeg version output contains --enable-gpl: '$out'", out.contains("--enable-gpl"))
        assertFalse("FFmpeg version output contains nonfree: '$out'", out.contains("nonfree"))
    }

    @Test
    fun copyFiveSecondsFromFixture() {
        val outFile = File(workingDir, "copy-five-seconds.mkv").apply { delete() }
        val args = buildString {
            append("-y -ss 0 -i ")
            append(quoted(fixtureMkv))
            append(" -t 5 -map 0 -map 0:t? -c copy -avoid_negative_ts make_zero -f matroska ")
            append(quoted(outFile))
        }

        val session = FFmpegKit.execute(args)

        assertTrue("copy command failed: ${session.allLogsAsString}", ReturnCode.isSuccess(session.returnCode))
        assertTrue(outFile.length() > 0L)
    }

    @Test
    fun probeReportsExpectedStreams() {
        val session = FFprobeKit.execute(
            "-v error -print_format json -show_streams ${quoted(fixtureMkv)}"
        )
        val json = session.output.orEmpty()

        assertTrue(json.contains("\"codec_name\":\"hevc\"") || json.contains("\"codec_name\": \"hevc\""))
        assertTrue(json.contains("\"codec_type\":\"subtitle\"") || json.contains("\"codec_type\": \"subtitle\""))
    }

    private fun copyAsset(assetName: String): File {
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val outFile = File(workingDir, assetName)
        if (!outFile.exists()) {
            testContext.assets.open(assetName).use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return outFile
    }

    private fun quoted(file: File): String = "\"${file.absolutePath}\""
}
