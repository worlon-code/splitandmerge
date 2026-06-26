package com.splitandmerge.mkvslice.domain.defaulttracks

import com.splitandmerge.mkvslice.platform.io.FileDescriptorWrapper
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest

sealed class RollbackResult {
    object SUCCESS : RollbackResult()
    object REFUSED : RollbackResult()
    class FAILED(val message: String) : RollbackResult()
}

class FlagJournal(
    private val cacheDir: File,
    private val jobId: String,
    private val fileIndex: Int
) {
    private val journalFile = File(cacheDir, "defaulttracks_${jobId}_$fileIndex.journal")

    fun writeJournal(fd: FileDescriptorWrapper, originalFileLength: Long, spanOffset: Long, originalBytes: ByteArray) {
        val outsideHash = computeOutsideHash(fd, spanOffset, originalBytes.size, originalFileLength)
        
        FileOutputStream(journalFile).use { fos ->
            val out = java.io.DataOutputStream(fos)
            out.writeBytes("DTJ2")
            out.writeLong(originalFileLength)
            out.writeLong(spanOffset)
            out.writeInt(originalBytes.size)
            out.write(originalBytes)
            out.write(outsideHash)
            out.flush()
            fos.channel.force(true) // fsync
        }
    }

    fun deleteJournal() {
        if (journalFile.exists()) {
            journalFile.delete()
        }
    }

    fun exists(): Boolean = journalFile.exists()

    fun rollback(fd: FileDescriptorWrapper): RollbackResult {
        if (!journalFile.exists()) return RollbackResult.REFUSED
        
        try {
            var originalLength = 0L
            var spanOffset = 0L
            var spanSize = 0
            var originalBytes = ByteArray(0)
            var expectedOutsideHash = ByteArray(0)
            
            FileInputStream(journalFile).use { fis ->
                val inp = java.io.DataInputStream(fis)
                val magic = ByteArray(4)
                inp.readFully(magic)
                if (String(magic) != "DTJ2") {
                    throw IOException("Invalid journal magic")
                }
                originalLength = inp.readLong()
                spanOffset = inp.readLong()
                spanSize = inp.readInt()
                originalBytes = ByteArray(spanSize)
                inp.readFully(originalBytes)
                expectedOutsideHash = ByteArray(32)
                inp.readFully(expectedOutsideHash)
            }
            
            val currentSize = fd.size()
            if (currentSize != originalLength) {
                return RollbackResult.REFUSED
            }
            
            // Verify bytes outside the span did not change (file not externally modified)
            val currentOutsideHash = computeOutsideHash(fd, spanOffset, spanSize, originalLength)
            if (!expectedOutsideHash.contentEquals(currentOutsideHash)) {
                return RollbackResult.REFUSED
            }
            
            // Write original bytes back
            fd.seek(spanOffset, 0)
            fd.write(originalBytes, 0, originalBytes.size)
            fd.fdatasync()
            
            deleteJournal()
            return RollbackResult.SUCCESS
        } catch (e: Exception) {
            return RollbackResult.FAILED(e.message ?: "Unknown error")
        }
    }

    private fun computeOutsideHash(fd: FileDescriptorWrapper, spanOffset: Long, spanSize: Int, originalLength: Long): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(65536)
        
        // Hash prefix [0, spanOffset)
        var bytesRead = 0L
        fd.seek(0, 0)
        while (bytesRead < spanOffset) {
            val toRead = minOf(buffer.size.toLong(), spanOffset - bytesRead).toInt()
            val read = fd.read(buffer, 0, toRead)
            if (read <= 0) break
            digest.update(buffer, 0, read)
            bytesRead += read
        }
        
        // Hash suffix [spanOffset + spanSize, originalLength)
        bytesRead = spanOffset + spanSize
        fd.seek(bytesRead, 0)
        while (bytesRead < originalLength) {
            val toRead = minOf(buffer.size.toLong(), originalLength - bytesRead).toInt()
            val read = fd.read(buffer, 0, toRead)
            if (read <= 0) break
            digest.update(buffer, 0, read)
            bytesRead += read
        }
        
        return digest.digest()
    }
}
