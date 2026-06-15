package com.splitandmerge.mkvslice.util.log

import java.io.File

object LogPurger {
    fun purgeOldLogs(logsDir: File, retentionDays: Int = 7) {
        if (!logsDir.exists() || !logsDir.isDirectory) return
        val cutoff = System.currentTimeMillis() - (retentionDays.toLong() * 24 * 60 * 60 * 1000)
        val files = logsDir.listFiles() ?: return
        for (file in files) {
            if (file.isFile && file.name.startsWith("app-") && file.name.endsWith(".log")) {
                if (file.lastModified() < cutoff) {
                    file.delete()
                }
            }
        }
    }
}
