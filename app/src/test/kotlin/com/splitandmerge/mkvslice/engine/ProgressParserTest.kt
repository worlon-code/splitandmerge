package com.splitandmerge.mkvslice.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressParserTest {

    @Test
    fun parseLine_extractsTimeAndSpeed() {
        val line = "frame=  144 fps=96 q=-1.0 size=1024kB time=00:00:06.00 bitrate=1398.1kbits/s speed=3.99x"

        val progress = ProgressParser.parseLine(line)

        requireNotNull(progress)
        assertEquals(6.0, progress.timeSeconds, 0.0001)
        assertEquals(3.99, progress.speed, 0.0001)
    }

    @Test
    fun parseLine_defaultsSpeedWhenMissing() {
        val line = "size=1024kB time=00:01:02.34 bitrate=1398.1kbits/s"

        val progress = ProgressParser.parseLine(line)

        requireNotNull(progress)
        assertEquals(62.34, progress.timeSeconds, 0.0001)
        assertEquals(0.0, progress.speed, 0.0001)
    }

    @Test
    fun parseLine_returnsNullWhenNoProgressPresent() {
        assertNull(ProgressParser.parseLine("Output #0, matroska, to 'out.mkv':"))
    }

    @Test
    fun parseLine_parsesHoursCorrectly() {
        val line = "size=999999kB time=02:30:15.50 bitrate=99kbits/s speed=1.00x"
        val progress = ProgressParser.parseLine(line)!!
        val expected = 2 * 3600.0 + 30 * 60.0 + 15.0 + 0.50
        assertEquals(expected, progress.timeSeconds, 0.001)
        assertEquals(1.0, progress.speed, 0.001)
    }

    @Test
    fun parseLine_speedNaDefaultsToZero() {
        val line = "size=0kB time=00:00:01.00 bitrate=0.0kbits/s speed=N/A"
        val progress = ProgressParser.parseLine(line)!!
        assertEquals(1.0, progress.timeSeconds, 0.001)
        assertEquals(0.0, progress.speed, 0.001)
    }

    @Test
    fun parseLine_returnsNullForEmpty() {
        assertNull(ProgressParser.parseLine(""))
    }
}
