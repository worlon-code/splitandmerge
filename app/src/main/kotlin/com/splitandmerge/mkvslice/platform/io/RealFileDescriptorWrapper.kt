package com.splitandmerge.mkvslice.platform.io

import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import java.io.IOException

class RealFileDescriptorWrapper(private val pfd: ParcelFileDescriptor) : FileDescriptorWrapper {
    private val fd = pfd.fileDescriptor

    override fun size(): Long {
        return Os.fstat(fd).st_size
    }

    override fun isRegularFile(): Boolean {
        val st = Os.fstat(fd)
        return OsConstants.S_ISREG(st.st_mode)
    }

    override fun isSeekable(): Boolean {
        return try {
            val current = Os.lseek(fd, 0, OsConstants.SEEK_CUR)
            val end = Os.lseek(fd, 0, OsConstants.SEEK_END)
            Os.lseek(fd, current, OsConstants.SEEK_SET)
            end >= 0
        } catch (e: Exception) {
            false
        }
    }

    override fun isWritable(): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            return try {
                val flags = Os.fcntlInt(fd, OsConstants.F_GETFL, 0)
                val accMode = flags and OsConstants.O_ACCMODE
                accMode == OsConstants.O_WRONLY || accMode == OsConstants.O_RDWR
            } catch (e: Exception) {
                false
            }
        }
        return true
    }

    override fun seek(offset: Long, whence: Int): Long {
        val androidWhence = when (whence) {
            0 -> OsConstants.SEEK_SET
            1 -> OsConstants.SEEK_CUR
            2 -> OsConstants.SEEK_END
            else -> throw IllegalArgumentException("Unknown whence: $whence")
        }
        return Os.lseek(fd, offset, androidWhence)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return Os.read(fd, buffer, offset, length)
    }

    override fun write(buffer: ByteArray, offset: Int, length: Int): Int {
        return Os.write(fd, buffer, offset, length)
    }

    override fun fdatasync() {
        Os.fdatasync(fd)
    }

    override fun close() {
        pfd.close()
    }
}
