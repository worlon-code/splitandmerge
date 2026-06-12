package com.splitandmerge.mkvslice.engine

/**
 * Parses FFmpeg stderr lines to extract progress information.
 * All FFmpeg progress lines contain "time=" and "speed=" fields.
 *
 * Example line:
 *   frame=  144 fps= 96 q=-1.0 size=    1024kB time=00:00:06.00 bitrate=1398.1kbits/s speed=3.99x
 */
object ProgressParser {

    // Matches "time=HH:MM:SS.cc" — hours may exceed 99 for long files
    private val TIME_RE = Regex("""time=(\d+):(\d{2}):(\d{2})\.(\d{2})""")
    // Matches "speed=1.23x" or "speed=N/A"
    private val SPEED_RE = Regex("""speed=\s*([\d.]+)x""")

    /**
     * Returns a [EngineEvent.Progress] if the line contains parseable progress data, else null.
     */
    fun parseLine(line: String): EngineEvent.Progress? {
        val timeMatch = TIME_RE.find(line) ?: return null
        val (h, m, s, cs) = timeMatch.destructured
        val timeSeconds = h.toLong() * 3600.0 +
                m.toLong() * 60.0 +
                s.toLong() +
                cs.toDouble() / 100.0

        val speed = SPEED_RE.find(line)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

        return EngineEvent.Progress(timeSeconds = timeSeconds, speed = speed)
    }
}
