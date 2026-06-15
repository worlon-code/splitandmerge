package com.splitandmerge.mkvslice.domain.merger

import java.io.File

object MergeListWriter {
    /**
     * Writes the concat demuxer list file.
     * SAF paths must NOT be quoted (e.g. `file saf:18.mkv`).
     */
    fun writeSafList(outputFile: File, safPaths: List<String>) {
        val content = safPaths.joinToString("\n") { "file $it" }
        outputFile.writeText(content)
    }
}
