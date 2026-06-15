package com.splitandmerge.mkvslice.domain.merger

import timber.log.Timber
import java.io.File

/**
 * Sweeps orphaned merge cache artefacts left behind by a process kill.
 *
 * Only files matching the whitelist patterns AND older than [cutoffMs] are deleted.
 * The 60-second cutoff prevents deleting files belonging to a job that just started
 * on a slow device (race-safe).
 *
 * Whitelisted patterns (A7):
 *   - staged_part*
 *   - merge_tmp*
 *   - concat.txt
 *   - *.part.tmp
 *   - *.mkv.tmp
 *   - *.mp4.tmp
 *
 * Room database files (e.g. mkvslice_db.lck) are explicitly NOT in the whitelist
 * and will never be touched.
 */
object MergeCacheSweeper {

    /** Default cutoff: exactly 60 seconds. Do NOT widen (per agent rules). */
    const val CUTOFF_MS = 60_000L

    /**
     * Deletes whitelisted files in [cacheDir] that are older than [cutoffMs].
     *
     * @param cacheDir the application cache directory (`Context.cacheDir`)
     * @param cutoffMs files with `lastModified < now - cutoffMs` are deleted (default 60 s)
     */
    fun sweep(cacheDir: File, cutoffMs: Long = CUTOFF_MS) {
        val threshold = System.currentTimeMillis() - cutoffMs
        val files = cacheDir.listFiles() ?: return

        var deleted = 0
        var skipped = 0

        for (file in files) {
            if (!file.isFile) continue
            if (!isWhitelisted(file.name)) {
                skipped++
                continue
            }
            if (file.lastModified() >= threshold) {
                // File is newer than cutoff — belongs to a live job, leave it.
                skipped++
                continue
            }
            val removed = file.delete()
            if (removed) {
                Timber.tag("SWEEP").i("deleted stale cache file: ${file.name}")
                deleted++
            } else {
                Timber.tag("SWEEP").w("failed to delete: ${file.name}")
            }
        }

        Timber.tag("SWEEP").i("cache sweep complete: deleted=$deleted skipped=$skipped")
    }

    /**
     * Returns `true` if the given filename matches a whitelisted merge-cache pattern.
     * This is the single authoritative pattern list (A7).
     */
    internal fun isWhitelisted(name: String): Boolean =
        name.startsWith("staged_part")   ||
        name.startsWith("merge_tmp")     ||
        name == "concat.txt"             ||
        name.endsWith(".part.tmp")       ||
        name.endsWith(".mkv.tmp")        ||
        name.endsWith(".mp4.tmp")
}
