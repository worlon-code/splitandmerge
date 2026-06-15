package com.splitandmerge.mkvslice.domain.merger

/**
 * Pure Kotlin formatters for merge progress display.
 * No Android dependencies — fully JVM-testable.
 */
object MergeFormatters {

    /**
     * Formats a byte-per-second speed into a human-readable string.
     * One decimal place; tiers at 1024 boundaries.
     * Examples: "512.0 B/s", "12.3 KB/s", "245.6 MB/s", "1.2 GB/s"
     */
    fun formatSpeed(bytesPerSec: Long): String {
        val kb = 1024L
        val mb = kb * 1024L
        val gb = mb * 1024L
        return when {
            bytesPerSec < kb -> String.format("%.1f B/s",  bytesPerSec.toDouble())
            bytesPerSec < mb -> String.format("%.1f KB/s", bytesPerSec.toDouble() / kb)
            bytesPerSec < gb -> String.format("%.1f MB/s", bytesPerSec.toDouble() / mb)
            else             -> String.format("%.1f GB/s", bytesPerSec.toDouble() / gb)
        }
    }

    /**
     * Formats a duration in seconds into a compact string.
     * Examples: "45s", "2m 3s", "1h 5m"
     */
    fun formatEta(seconds: Long): String {
        if (seconds <= 0L) return "—"
        val h = seconds / 3600L
        val m = (seconds % 3600L) / 60L
        val s = seconds % 60L
        return when {
            h > 0L -> "${h}h ${m}m"
            m > 0L -> "${m}m ${s}s"
            else   -> "${s}s"
        }
    }

    /**
     * Formats a duration in seconds to "Xh Ym Zs" for the result screen.
     * Example: 13447.0 → "3h 44m 7s"
     */
    fun formatDuration(seconds: Double): String {
        val total = seconds.toLong()
        val h = total / 3600L
        val m = (total % 3600L) / 60L
        val s = total % 60L
        return when {
            h > 0L -> "${h}h ${m}m ${s}s"
            m > 0L -> "${m}m ${s}s"
            else   -> "${s}s"
        }
    }
}
