package com.splitandmerge.mkvslice.util.log

import android.util.Log
import timber.log.Timber
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class FileLoggingTree(
    private val logsDir: File,
    private val timeProvider: () -> ZonedDateTime = { ZonedDateTime.now() }
) : Timber.Tree() {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    private var currentWriter: BufferedWriter? = null
    private var currentFile: File? = null
    private var currentDateStr: String = ""
    private var currentSize: Long = 0L

    init {
        try {
            if (!logsDir.exists()) {
                logsDir.mkdirs()
            }
            instance = this
        } catch (e: Exception) {
            android.util.Log.e("FileLoggingTree", "Failed to create logs directory", e)
        }
    }

    @Synchronized
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        try {
            val now = timeProvider()
            val dateStr = now.format(dateFormatter)

            val levelStr = when (priority) {
                2 -> "V" // Log.VERBOSE
                3 -> "D" // Log.DEBUG
                4 -> "I" // Log.INFO
                5 -> "W" // Log.WARN
                6 -> "E" // Log.ERROR
                7 -> "A" // Log.ASSERT
                else -> "U"
            }

            val redactedMessage = UriRedactor.redact(message)
            val timeStr = now.format(timeFormatter)

            val builder = StringBuilder()
            builder.append(timeStr).append("  ").append(levelStr).append("  ").append(tag ?: "").append("  ").append(redactedMessage).append("\n")
            if (t != null) {
                val sw = java.io.StringWriter()
                val pw = java.io.PrintWriter(sw)
                t.printStackTrace(pw)
                builder.append(sw.toString())
            }

            val logLine = builder.toString()
            val lineBytes = logLine.toByteArray(Charsets.UTF_8)

            ensureWriter(dateStr, lineBytes.size.toLong())

            val writer = currentWriter
            if (writer != null) {
                writer.write(logLine)
                writer.flush()
                currentSize += lineBytes.size
            }
        } catch (e: Exception) {
            android.util.Log.e("FileLoggingTree", "FileLoggingTree failed to write log entry", e)
        }
    }

    private fun ensureWriter(dateStr: String, nextWriteSize: Long) {
        val writer = currentWriter
        val file = currentFile
        
        if (writer != null && file != null && currentDateStr == dateStr && (currentSize + nextWriteSize) <= 10L * 1024 * 1024) {
            return
        }

        closeWriter()

        currentDateStr = dateStr
        val rolledFile = resolveNextFile(dateStr, nextWriteSize)
        currentFile = rolledFile
        currentSize = if (rolledFile.exists()) rolledFile.length() else 0L

        try {
            val fos = FileOutputStream(rolledFile, true)
            currentWriter = BufferedWriter(OutputStreamWriter(fos, Charsets.UTF_8))
        } catch (e: Exception) {
            currentWriter = null
            android.util.Log.e("FileLoggingTree", "FileLoggingTree failed to open file writer", e)
            throw e
        }
    }

    private fun resolveNextFile(dateStr: String, nextWriteSize: Long): File {
        val baseFile = File(logsDir, "app-$dateStr.log")
        if (!baseFile.exists() || (baseFile.length() + nextWriteSize) <= 10L * 1024 * 1024) {
            return baseFile
        }

        var seq = 1
        while (true) {
            val seqStr = String.format("%03d", seq)
            val candidate = File(logsDir, "app-$dateStr-$seqStr.log")
            if (!candidate.exists() || (candidate.length() + nextWriteSize) <= 10L * 1024 * 1024) {
                return candidate
            }
            seq++
        }
    }

    private fun closeWriter() {
        try {
            currentWriter?.close()
        } catch (e: Exception) {
            android.util.Log.w("FileLoggingTree", "FileLoggingTree failed to close writer", e)
        } finally {
            currentWriter = null
        }
    }

    fun close() {
        synchronized(this) {
            closeWriter()
        }
    }

    fun getCurrentFileName(): String? = synchronized(this) {
        currentFile?.name
    }

    fun logForTest(priority: Int, tag: String?, message: String, t: Throwable?) {
        log(priority, tag, message, t)
    }

    companion object {
        @Volatile
        private var instance: FileLoggingTree? = null

        fun flushIfPlanted() {
            instance?.close()
        }

        fun currentFileName(): String? {
            return instance?.getCurrentFileName()
        }
    }
}
