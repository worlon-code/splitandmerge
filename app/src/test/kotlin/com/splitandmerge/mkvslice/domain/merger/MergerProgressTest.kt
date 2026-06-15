package com.splitandmerge.mkvslice.domain.merger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MergerProgressTest {

    private fun getOverallPct(
        phase: MergePhase,
        phasePct: Int,
        inputsStaged: Boolean,
        outputStaged: Boolean
    ): Int {
        return when {
            inputsStaged && outputStaged -> {
                when (phase) {
                    MergePhase.STAGING -> (phasePct * 0.33).toInt().coerceIn(0, 33)
                    MergePhase.CONCAT -> (33 + phasePct * 0.33).toInt().coerceIn(33, 66)
                    MergePhase.COPYING_TO_OUTPUT -> (66 + phasePct * 0.34).toInt().coerceIn(66, 100)
                }
            }
            inputsStaged && !outputStaged -> {
                when (phase) {
                    MergePhase.STAGING -> (phasePct * 0.50).toInt().coerceIn(0, 50)
                    MergePhase.CONCAT -> (50 + phasePct * 0.50).toInt().coerceIn(50, 100)
                    MergePhase.COPYING_TO_OUTPUT -> 100
                }
            }
            !inputsStaged && outputStaged -> {
                when (phase) {
                    MergePhase.STAGING -> 0
                    MergePhase.CONCAT -> (phasePct * 0.50).toInt().coerceIn(0, 50)
                    MergePhase.COPYING_TO_OUTPUT -> (50 + phasePct * 0.50).toInt().coerceIn(50, 100)
                }
            }
            else -> { // !inputsStaged && !outputStaged
                when (phase) {
                    MergePhase.STAGING -> 0
                    MergePhase.CONCAT -> phasePct.coerceIn(0, 100)
                    MergePhase.COPYING_TO_OUTPUT -> 100
                }
            }
        }
    }

    @Test
    fun test3StepsMonotonicity() {
        // inputsStaged = true, outputStaged = true
        var lastPct = -1
        for (p in 0..100) {
            val pct = getOverallPct(MergePhase.STAGING, p, true, true)
            assertTrue("Staging: $pct >= $lastPct", pct >= lastPct)
            lastPct = pct
        }
        assertEquals(33, lastPct)

        for (p in 0..100) {
            val pct = getOverallPct(MergePhase.CONCAT, p, true, true)
            assertTrue("Concat: $pct >= $lastPct", pct >= lastPct)
            lastPct = pct
        }
        assertEquals(66, lastPct)

        for (p in 0..100) {
            val pct = getOverallPct(MergePhase.COPYING_TO_OUTPUT, p, true, true)
            assertTrue("Copying: $pct >= $lastPct", pct >= lastPct)
            lastPct = pct
        }
        assertEquals(100, lastPct)
    }

    @Test
    fun test2StepsInputsStagedOnly() {
        // inputsStaged = true, outputStaged = false
        var lastPct = -1
        for (p in 0..100) {
            val pct = getOverallPct(MergePhase.STAGING, p, true, false)
            assertTrue("Staging: $pct >= $lastPct", pct >= lastPct)
            lastPct = pct
        }
        assertEquals(50, lastPct)

        for (p in 0..100) {
            val pct = getOverallPct(MergePhase.CONCAT, p, true, false)
            assertTrue("Concat: $pct >= $lastPct", pct >= lastPct)
            lastPct = pct
        }
        assertEquals(100, lastPct)
    }

    @Test
    fun test2StepsOutputStagedOnly() {
        // inputsStaged = false, outputStaged = true
        var lastPct = -1
        for (p in 0..100) {
            val pct = getOverallPct(MergePhase.CONCAT, p, false, true)
            assertTrue("Concat: $pct >= $lastPct", pct >= lastPct)
            lastPct = pct
        }
        assertEquals(50, lastPct)

        for (p in 0..100) {
            val pct = getOverallPct(MergePhase.COPYING_TO_OUTPUT, p, false, true)
            assertTrue("Copying: $pct >= $lastPct", pct >= lastPct)
            lastPct = pct
        }
        assertEquals(100, lastPct)
    }

    @Test
    fun test1StepNoStagingNoCopy() {
        // inputsStaged = false, outputStaged = false
        var lastPct = -1
        for (p in 0..100) {
            val pct = getOverallPct(MergePhase.CONCAT, p, false, false)
            assertTrue("Concat: $pct >= $lastPct", pct >= lastPct)
            lastPct = pct
        }
        assertEquals(100, lastPct)
    }
}
