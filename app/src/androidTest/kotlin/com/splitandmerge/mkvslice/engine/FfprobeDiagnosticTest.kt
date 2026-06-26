package com.splitandmerge.mkvslice.engine

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.antonkarpenko.ffmpegkit.FFprobeKit
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Diagnostic test to figure out exactly what FFprobeKit returns
 * for keyframe extraction on a large MKV file.
 * Uses android.util.Log directly (no Timber needed).
 */
@RunWith(AndroidJUnit4::class)
class FfprobeDiagnosticTest {

    private val TAG = "FFPROBE_DIAG"
    private lateinit var sourceFile: File

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        sourceFile = File(context.getExternalFilesDir(null), "Dridam.mkv")
        Log.i(TAG, "Source file: ${sourceFile.absolutePath}")
        Log.i(TAG, "Source exists: ${sourceFile.exists()}")
        Log.i(TAG, "Source size: ${sourceFile.length()} bytes")
    }

    @Test
    fun diagnoseKeyframeExtraction() {
        org.junit.Assume.assumeTrue("Skipping: Dridam.mkv fixture is not present on the device", sourceFile.exists())
        assertTrue("Source file not found: ${sourceFile.absolutePath}", sourceFile.exists())

        // 1. Simple probe to verify FFprobeKit works at all
        Log.i(TAG, "=== STEP 1: Basic probe ===")
        val probeArgs = "-v error -hide_banner -show_format -of json ${sourceFile.absolutePath}"
        val probeSession = FFprobeKit.execute(probeArgs)
        Log.i(TAG, "Probe returnCode: ${probeSession.returnCode}")
        Log.i(TAG, "Probe output length: ${probeSession.output?.length ?: -1}")
        Log.i(TAG, "Probe allLogsAsString length: ${probeSession.allLogsAsString?.length ?: -1}")
        Log.i(TAG, "Probe output (first 500): ${probeSession.output?.take(500)}")
        Log.i(TAG, "Probe logs (first 500): ${probeSession.allLogsAsString?.take(500)}")

        // 2. Keyframe extraction - the command that fails
        Log.i(TAG, "=== STEP 2: Keyframe extraction ===")
        val kfArgs = "-v error -hide_banner " +
                "-select_streams v:0 -skip_frame nokey " +
                "-show_frames -show_entries frame=pkt_pts_time " +
                "-of csv=p=0 ${sourceFile.absolutePath}"
        Log.i(TAG, "Keyframe args: $kfArgs")
        val kfSession = FFprobeKit.execute(kfArgs)
        Log.i(TAG, "KF returnCode: ${kfSession.returnCode}")
        Log.i(TAG, "KF output length: ${kfSession.output?.length ?: -1}")
        Log.i(TAG, "KF allLogsAsString length: ${kfSession.allLogsAsString?.length ?: -1}")
        Log.i(TAG, "KF output (first 500): ${kfSession.output?.take(500)}")
        Log.i(TAG, "KF logs (first 500): ${kfSession.allLogsAsString?.take(500)}")

        // 3. Try alternative: use -show_packets instead of -show_frames (much faster, no decoding)
        Log.i(TAG, "=== STEP 3: Keyframes via packets (no decode) ===")
        val pktArgs = "-v error -hide_banner " +
                "-select_streams v:0 " +
                "-show_packets -show_entries packet=pts_time,flags " +
                "-of csv=p=0 ${sourceFile.absolutePath}"
        Log.i(TAG, "Packet args: $pktArgs")
        val pktSession = FFprobeKit.execute(pktArgs)
        Log.i(TAG, "PKT returnCode: ${pktSession.returnCode}")
        Log.i(TAG, "PKT output length: ${pktSession.output?.length ?: -1}")
        Log.i(TAG, "PKT allLogsAsString length: ${pktSession.allLogsAsString?.length ?: -1}")
        Log.i(TAG, "PKT output (first 500): ${pktSession.output?.take(500)}")
        Log.i(TAG, "PKT logs (first 500): ${pktSession.allLogsAsString?.take(500)}")

        // Parse keyframes from packet output (flag contains 'K' for keyframes)
        val pktOutput = pktSession.output?.trim() ?: pktSession.allLogsAsString?.trim() ?: ""
        val keyframes = pktOutput.lineSequence()
            .filter { it.contains(",K") } // CSV: pts_time,flags — keyframes have 'K' in flags
            .mapNotNull { it.split(",").firstOrNull()?.trim()?.toDoubleOrNull() }
            .sorted()
            .toList()
        Log.i(TAG, "Parsed ${keyframes.size} keyframes from packets")
        if (keyframes.isNotEmpty()) {
            Log.i(TAG, "First 10 keyframes: ${keyframes.take(10)}")
            Log.i(TAG, "Last 5 keyframes: ${keyframes.takeLast(5)}")
        }

        // 4. Simplest possible test: just count frames with -count_packets
        Log.i(TAG, "=== STEP 4: Count video keyframes ===")
        val countArgs = "-v error -hide_banner " +
                "-select_streams v:0 " +
                "-show_entries stream=nb_read_packets " +
                "-count_packets " +
                "-skip_frame nokey " +
                "-of csv=p=0 ${sourceFile.absolutePath}"
        val countSession = FFprobeKit.execute(countArgs)
        Log.i(TAG, "COUNT returnCode: ${countSession.returnCode}")
        Log.i(TAG, "COUNT output: ${countSession.output?.trim()}")
        Log.i(TAG, "COUNT logs: ${countSession.allLogsAsString?.trim()}")

        // Assert we found keyframes via at least one method
        assertTrue(
            "No keyframes found via any method! Check FFPROBE_DIAG logs in logcat.",
            keyframes.isNotEmpty()
        )
    }
}
