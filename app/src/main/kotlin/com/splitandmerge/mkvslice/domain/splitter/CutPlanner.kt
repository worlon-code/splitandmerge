package com.splitandmerge.mkvslice.domain.splitter

import com.splitandmerge.mkvslice.domain.model.SplitMode
import javax.inject.Inject

data class CutPlan(
    val cuts: List<Double>,          // Cut points in seconds (N-1 for N parts)
    val estimatedParts: Int,
    val mode: SplitMode,
    val requestedParts: Int?,
    val targetCapBytes: Long,
    val ceilingBytes: Long
)

class CutPlanner @Inject constructor() {

    /**
     * Plan the set of cut timestamps for a split job.
     *
     * @param mode         Splitting mode: exact parts, size-cap only, or both.
     * @param requestedParts Required for [SplitMode.EXACT_PARTS] and [SplitMode.BOTH].
     * @param targetCapBytes Soft cap (default 9 GB). Used as the target; actual limit is [ceilingBytes].
     * @param ceilingBytes   Hard cap (default 9.5 GB). A part exceeding this triggers re-split.
     * @param durationSeconds Total duration of the input file.
     * @param totalSizeBytes  Total file size in bytes (for bitrate estimation).
     * @param keyframes       Sorted ascending list of keyframe timestamps (seconds).
     */
    fun plan(
        mode: SplitMode,
        requestedParts: Int?,
        targetCapBytes: Long,
        ceilingBytes: Long,
        durationSeconds: Double,
        totalSizeBytes: Long,
        keyframes: List<Double>
    ): CutPlan {
        require(durationSeconds > 0) { "Duration must be positive" }
        require(keyframes.isNotEmpty()) { "Keyframe list cannot be empty" }
        require(targetCapBytes > 0) { "targetCapBytes must be positive" }
        require(ceilingBytes >= targetCapBytes) { "ceiling must be >= target" }

        val cuts = when (mode) {
            SplitMode.EXACT_PARTS -> exactParts(requireNotNull(requestedParts), durationSeconds, keyframes)
            SplitMode.SIZE_CAP_ONLY -> sizeCap(durationSeconds, totalSizeBytes, targetCapBytes, keyframes)
            SplitMode.BOTH -> both(requireNotNull(requestedParts), durationSeconds, totalSizeBytes, targetCapBytes, keyframes)
        }

        return CutPlan(
            cuts = cuts,
            estimatedParts = cuts.size + 1,
            mode = mode,
            requestedParts = requestedParts,
            targetCapBytes = targetCapBytes,
            ceilingBytes = ceilingBytes
        )
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    /**
     * Place N-1 cuts by dividing duration evenly, snapping each to the nearest keyframe
     * at-or-before the ideal time.
     *
     * Per spec §3.3.1 — throws if distinct cuts < parts-1 (not enough keyframes).
     */
    internal fun exactParts(parts: Int, duration: Double, keyframes: List<Double>): List<Double> {
        require(parts >= 2) { "Must request at least 2 parts" }
        val raw = (1 until parts).map { it * (duration / parts) }
        val snapped = raw.map { nearestKeyframeAtOrBefore(it, keyframes) }.distinct()
        require(snapped.size == parts - 1) {
            "Cannot place $parts cuts; not enough unique keyframes (got ${snapped.size})."
        }
        return snapped
    }

    /**
     * Walk through time in steps of ~95% of cap size (estimated by avg bitrate).
     * Snap each step to the keyframe at-or-before.
     *
     * Per spec §3.3.2.
     */
    internal fun sizeCap(
        duration: Double,
        totalSize: Long,
        cap: Long,
        keyframes: List<Double>
    ): List<Double> {
        val avgBytesPerSec = totalSize.toDouble() / duration
        val capSecondsApprox = (cap * 0.95) / avgBytesPerSec
        val cuts = mutableListOf<Double>()
        var nextCut = capSecondsApprox
        while (nextCut < duration) {
            val kf = nearestKeyframeAtOrBefore(nextCut, keyframes)
            if (cuts.isNotEmpty() && kf <= cuts.last()) break   // safety: no infinite loop
            cuts += kf
            nextCut += capSecondsApprox
        }
        return cuts
    }

    /**
     * Attempt exactParts first; if the proportional part size exceeds cap use sizeCap.
     *
     * Per spec §3.3.3 — the cap wins.
     */
    internal fun both(
        requestedParts: Int,
        duration: Double,
        totalSize: Long,
        cap: Long,
        keyframes: List<Double>
    ): List<Double> {
        val approxPartSize = totalSize / requestedParts
        return if (approxPartSize <= cap * 0.95) {
            exactParts(requestedParts, duration, keyframes)
        } else {
            sizeCap(duration, totalSize, cap, keyframes)
        }
    }

    /**
     * Binary-search to find the largest keyframe <= [t].
     * Returns keyframes[0] if all keyframes are after [t].
     */
    internal fun nearestKeyframeAtOrBefore(t: Double, kf: List<Double>): Double {
        val idx = kf.binarySearch(t)
        return when {
            idx >= 0 -> kf[idx]
            else -> {
                val insertionPoint = -idx - 1
                kf[(insertionPoint - 1).coerceAtLeast(0)]
            }
        }
    }
}
