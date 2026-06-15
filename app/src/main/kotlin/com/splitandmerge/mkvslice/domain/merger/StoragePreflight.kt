package com.splitandmerge.mkvslice.domain.merger

import android.content.Context
import com.splitandmerge.mkvslice.engine.EngineError
import java.io.File

object StoragePreflight {
    fun checkSpace(
        context: Context,
        partSizes: List<Long>,
        inputsStaged: Boolean,
        outputStaged: Boolean,
        resolvedOutputPath: String?
    ) {
        val totalPartsSize = partSizes.sum()
        val maxPartSize = partSizes.maxOrNull() ?: 0L

        // Cache space needed:
        // - Staging: sum of parts (if staged)
        // - Temp output: sum of parts (if staged)
        val cacheNeeded = (if (inputsStaged) totalPartsSize else 0L) +
                          (if (outputStaged) totalPartsSize else 0L)

        val cacheAvailable = context.cacheDir.usableSpace
        val cacheRequiredWithMargin = (cacheNeeded * 1.05).toLong()

        if (cacheAvailable < cacheRequiredWithMargin) {
            throw EngineError.InsufficientStorage(cacheRequiredWithMargin, cacheAvailable)
        }

        // Output volume space needed (if output is real path and not staged):
        if (!outputStaged && resolvedOutputPath != null) {
            val outputFile = File(resolvedOutputPath)
            val outputDir = outputFile.parentFile ?: outputFile
            val outputAvailable = outputDir.usableSpace
            val outputRequiredWithMargin = (totalPartsSize * 1.05).toLong()
            if (outputAvailable < outputRequiredWithMargin) {
                throw EngineError.InsufficientStorage(outputRequiredWithMargin, outputAvailable)
            }
        }
    }
}
