package com.splitandmerge.mkvslice.domain.merger

import com.splitandmerge.mkvslice.engine.FfprobeEngine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MergeValidator @Inject constructor(
    private val ffprobeEngine: FfprobeEngine
) {
    suspend fun validate(partUris: List<String>) {
        if (partUris.isEmpty()) throw IllegalArgumentException("No parts provided for merge.")
        if (partUris.size == 1) return // Nothing to validate against if there's only one part

        val probes = partUris.map { uri -> ffprobeEngine.probe(uri) }
        val reference = probes.first()

        for (i in 1 until probes.size) {
            val current = probes[i]
            
            // Check stream count
            if (reference.streams.size != current.streams.size) {
                throw IllegalStateException("Stream count mismatch between parts. Part 1 has ${reference.streams.size} streams, Part ${i + 1} has ${current.streams.size}.")
            }

            // Check codec compatibility for all streams
            reference.streams.forEachIndexed { index, refStream ->
                val curStream = current.streams[index]
                
                if (refStream.codecType != curStream.codecType) {
                    throw IllegalStateException("Stream $index type mismatch. Part 1 is '${refStream.codecType}', Part ${i + 1} is '${curStream.codecType}'.")
                }
                
                if (refStream.codecName != curStream.codecName) {
                    throw IllegalStateException("Stream $index codec mismatch. Part 1 uses '${refStream.codecName}', Part ${i + 1} uses '${curStream.codecName}'.")
                }

                // For video, resolution must match
                if (refStream.codecType == "video") {
                    if (refStream.width != curStream.width || refStream.height != curStream.height) {
                        throw IllegalStateException("Resolution mismatch in video stream $index. Part 1 is ${refStream.width}x${refStream.height}, Part ${i + 1} is ${curStream.width}x${curStream.height}.")
                    }
                }
                
                // For audio, channels and sample rate generally should match to avoid glitching during concat copy
                if (refStream.codecType == "audio") {
                    if (refStream.channels != curStream.channels) {
                        throw IllegalStateException("Audio channels mismatch in stream $index. Part 1 has ${refStream.channels}, Part ${i + 1} has ${curStream.channels}.")
                    }
                    if (refStream.sampleRate != curStream.sampleRate) {
                        throw IllegalStateException("Audio sample rate mismatch in stream $index. Part 1 has ${refStream.sampleRate}, Part ${i + 1} has ${curStream.sampleRate}.")
                    }
                }
            }
        }
    }
}
