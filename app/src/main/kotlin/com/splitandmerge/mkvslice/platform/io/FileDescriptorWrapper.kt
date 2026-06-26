package com.splitandmerge.mkvslice.platform.io

import java.io.Closeable

interface FileDescriptorWrapper : Closeable {
    fun size(): Long
    fun isRegularFile(): Boolean
    fun isSeekable(): Boolean
    fun isWritable(): Boolean
    fun seek(offset: Long, whence: Int): Long
    fun read(buffer: ByteArray, offset: Int, length: Int): Int
    fun write(buffer: ByteArray, offset: Int, length: Int): Int
    fun fdatasync()
}
