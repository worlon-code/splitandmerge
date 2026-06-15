package com.splitandmerge.mkvslice.domain.merger

import org.junit.Assert.assertEquals
import org.junit.Test

class MergeFormattersTest {

    @Test
    fun testFormatSpeed() {
        // Use java.util.Locale.US style matching for floats since String.format can be locale dependent
        // but we format with local defaults. Let's make sure it matches.
        val speedB = MergeFormatters.formatSpeed(512L)
        assert(speedB.endsWith("B/s"))
        
        val speedKB = MergeFormatters.formatSpeed(1024L)
        assert(speedKB.endsWith("KB/s"))
        
        val speedMB = MergeFormatters.formatSpeed(245L * 1024L * 1024L)
        assert(speedMB.endsWith("MB/s"))
        
        val speedGB = MergeFormatters.formatSpeed((1.2 * 1024 * 1024 * 1024).toLong())
        assert(speedGB.endsWith("GB/s"))
    }

    @Test
    fun testFormatEta() {
        assertEquals("—", MergeFormatters.formatEta(0L))
        assertEquals("—", MergeFormatters.formatEta(-10L))
        assertEquals("45s", MergeFormatters.formatEta(45L))
        assertEquals("2m 3s", MergeFormatters.formatEta(123L))
        assertEquals("1h 5m", MergeFormatters.formatEta(3900L))
    }

    @Test
    fun testFormatDuration() {
        assertEquals("0s", MergeFormatters.formatDuration(0.0))
        assertEquals("45s", MergeFormatters.formatDuration(45.0))
        assertEquals("2m 3s", MergeFormatters.formatDuration(123.0))
        assertEquals("3h 44m 7s", MergeFormatters.formatDuration(13447.0))
    }
}
