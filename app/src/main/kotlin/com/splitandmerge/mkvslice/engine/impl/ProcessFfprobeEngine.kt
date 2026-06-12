package com.splitandmerge.mkvslice.engine.impl

import com.antonkarpenko.ffmpegkit.FFprobeKit
import com.splitandmerge.mkvslice.engine.FfprobeEngine
import com.splitandmerge.mkvslice.engine.FormatInfo
import com.splitandmerge.mkvslice.engine.ProbeResult
import com.splitandmerge.mkvslice.engine.StreamInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

import android.content.Context
import android.net.Uri
import com.antonkarpenko.ffmpegkit.FFmpegKitConfig

@Singleton
class ProcessFfprobeEngine @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) : FfprobeEngine {

    private val json = Json { ignoreUnknownKeys = true }

    private fun resolveUri(uri: String): String {
        return if (uri.startsWith("content://")) {
            FFmpegKitConfig.getSafParameterForRead(context, Uri.parse(uri)) ?: uri
        } else {
            uri
        }
    }

    override suspend fun probe(uri: String): ProbeResult = withContext(Dispatchers.IO) {
        val resolvedUri = resolveUri(uri)
        val args = "-v error -hide_banner -show_format -show_streams -of json \"$resolvedUri\""
        Timber.tag("ENGINE").d("ffprobe probe: %s (resolved: %s)", uri, resolvedUri)
        val session = FFprobeKit.execute(args)
        // getOutput() captures stdout (actual probe data); allLogsAsString captures stderr (log messages)
        val output = session.output?.trim()?.ifEmpty { null }
            ?: session.allLogsAsString?.trim()?.ifEmpty { null }
            ?: error("ffprobe returned no output for $uri")
        parseProbeOutput(output)
    }

    override suspend fun keyframes(uri: String): List<Double> = withContext(Dispatchers.IO) {
        val resolvedUri = resolveUri(uri)
        // Use -show_packets instead of -show_frames -skip_frame nokey.
        // -show_frames requires full frame decoding which hangs on large files (4.5GB+).
        // -show_packets reads packet headers only — instant even for 50GB files.
        // Output format: "pts_time,flags" — keyframes have 'K' in the flags field.
        val args = "-v error -hide_banner " +
                "-select_streams v:0 " +
                "-show_packets -show_entries packet=pts_time,flags " +
                "-of csv=p=0 \"$resolvedUri\""
        Timber.tag("ENGINE").d("ffprobe keyframes: %s", uri)
        val session = FFprobeKit.execute(args)
        val output = session.output?.trim()?.ifEmpty { null }
            ?: session.allLogsAsString?.trim()?.ifEmpty { null }
        if (output.isNullOrBlank()) {
            Timber.tag("ENGINE").e("ffprobe keyframes returned empty output for %s", uri)
            return@withContext emptyList()
        }
        // Filter for keyframe packets (flags contain 'K') and extract timestamps
        val keyframes = output.lineSequence()
            .filter { it.contains(",K") }
            .mapNotNull { it.split(",").firstOrNull()?.trim()?.toDoubleOrNull() }
            .sorted()
            .toList()
        Timber.tag("ENGINE").d("ffprobe found %d keyframes for %s", keyframes.size, uri)
        keyframes
    }

    // ─── JSON parsing ─────────────────────────────────────────────────────────

    private fun parseProbeOutput(raw: String): ProbeResult {
        // ffprobe outputs JSON but may have preamble log lines; find first '{'
        val jsonStart = raw.indexOf('{')
        val jsonStr = if (jsonStart >= 0) raw.substring(jsonStart) else raw
        val root = json.parseToJsonElement(jsonStr).jsonObject

        val fmt = root["format"]?.jsonObject
            ?: error("No 'format' key in ffprobe output")
        val streams = root["streams"]?.jsonArray?.map { parseStream(it.jsonObject) }
            ?: emptyList()

        val format = FormatInfo(
            filename    = fmt["filename"]?.jsonPrimitive?.content ?: "",
            nbStreams   = fmt["nb_streams"]?.jsonPrimitive?.int ?: 0,
            formatName  = fmt["format_name"]?.jsonPrimitive?.content ?: "",
            durationSeconds = fmt["duration"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
            sizeBytes   = fmt["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
            bitRate     = fmt["bit_rate"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
        )
        return ProbeResult(format = format, streams = streams)
    }

    private fun parseStream(s: JsonObject): StreamInfo {
        val disposition = s["disposition"]?.jsonObject
            ?.entries
            ?.associate { (k, v) -> k to (v.jsonPrimitive.content.toIntOrNull() ?: 0) }
            ?: emptyMap()

        val tags = s["tags"]?.jsonObject
        return StreamInfo(
            index      = s["index"]?.jsonPrimitive?.int ?: 0,
            codecType  = s["codec_type"]?.jsonPrimitive?.content ?: "",
            codecName  = s["codec_name"]?.jsonPrimitive?.content ?: "",
            profile    = s["profile"]?.jsonPrimitive?.content,
            width      = s["width"]?.jsonPrimitive?.content?.toIntOrNull(),
            height     = s["height"]?.jsonPrimitive?.content?.toIntOrNull(),
            channels   = s["channels"]?.jsonPrimitive?.content?.toIntOrNull(),
            sampleRate = s["sample_rate"]?.jsonPrimitive?.content,
            language   = tags?.get("language")?.jsonPrimitive?.content,
            title      = tags?.get("title")?.jsonPrimitive?.content,
            disposition = disposition
        )
    }
}
