package com.splitandmerge.mkvslice.platform.io

import java.io.File
import java.io.InputStream
import java.io.OutputStream

interface FileSystem {
    fun cacheDir(): File
    fun exists(file: File): Boolean
    fun canRead(file: File): Boolean
    fun length(file: File): Long
    fun openInput(file: File): InputStream
    fun openOutput(file: File): OutputStream
    fun createNewFile(file: File): Boolean
    fun delete(file: File): Boolean
}
