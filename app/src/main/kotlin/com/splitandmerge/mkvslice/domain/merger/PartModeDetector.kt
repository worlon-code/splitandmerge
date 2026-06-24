package com.splitandmerge.mkvslice.domain.merger

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

enum class PartMode {
    MKVSLICE,
    EBML,
    OTHER
}

/**
 * Single source of truth for classifying part file types.
 * Sniffs the first 8 bytes of a file to check for MKVSLICE or EBML magic header.
 */
@Singleton
class PartModeDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun detectMode(uriString: String): PartMode {
        if (uriString.isBlank()) return PartMode.OTHER
        val uri = try {
            Uri.parse(uriString)
        } catch (e: Exception) {
            return PartMode.OTHER
        }

        try {
            context.contentResolver.openInputStream(uri)?.use { inp ->
                val bytes = ByteArray(8)
                var totalRead = 0
                while (totalRead < 8) {
                    val read = inp.read(bytes, totalRead, 8 - totalRead)
                    if (read <= 0) break
                    totalRead += read
                }
                if (totalRead < 8) return PartMode.OTHER

                val magic = String(bytes, Charsets.US_ASCII)
                if (magic == "MKVSLICE") {
                    return PartMode.MKVSLICE
                }

                if (bytes[0] == 0x1A.toByte() &&
                    bytes[1] == 0x45.toByte() &&
                    bytes[2] == 0xDF.toByte() &&
                    bytes[3] == 0xA3.toByte()
                ) {
                    return PartMode.EBML
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return PartMode.OTHER
    }
}
