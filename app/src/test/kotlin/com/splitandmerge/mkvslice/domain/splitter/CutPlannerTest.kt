package com.splitandmerge.mkvslice.domain.splitter

import com.splitandmerge.mkvslice.domain.model.SplitMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CutPlannerTest {

    private val planner = CutPlanner()
    private val keyframes = listOf(0.0, 5.0, 10.0, 15.0, 20.0, 25.0, 30.0, 35.0, 40.0)

    @Test
    fun exactParts_producesPartCountMinusOneCuts() {
        val plan = planner.plan(
            mode = SplitMode.EXACT_PARTS,
            requestedParts = 4,
            targetCapBytes = 9_000L,
            ceilingBytes = 9_500L,
            durationSeconds = 40.0,
            totalSizeBytes = 40_000L,
            keyframes = keyframes
        )

        assertEquals(3, plan.cuts.size)
        assertEquals(listOf(10.0, 20.0, 30.0), plan.cuts)
    }

    @Test
    fun sizeCapOnly_snapsToKeyframes() {
        val plan = planner.plan(
            mode = SplitMode.SIZE_CAP_ONLY,
            requestedParts = null,
            targetCapBytes = 10_000L,
            ceilingBytes = 12_000L,
            durationSeconds = 40.0,
            totalSizeBytes = 40_000L,
            keyframes = keyframes
        )

        assertTrue(plan.cuts.isNotEmpty())
        assertTrue(plan.cuts.all { it in keyframes })
    }

    @Test
    fun both_prefersSizeCapWhenEqualPartsWouldExceedTarget() {
        val plan = planner.plan(
            mode = SplitMode.BOTH,
            requestedParts = 2,
            targetCapBytes = 9_000L,
            ceilingBytes = 9_500L,
            durationSeconds = 40.0,
            totalSizeBytes = 40_000L,
            keyframes = keyframes
        )

        assertTrue(plan.cuts.size > 1)
        assertTrue(plan.cuts.all { it in keyframes })
    }
}
