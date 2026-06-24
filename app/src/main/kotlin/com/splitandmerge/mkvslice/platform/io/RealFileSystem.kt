package com.splitandmerge.mkvslice.platform.io

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealFileSystem @Inject constructor(
    @ApplicationContext private val context: Context
) : FileSystem {
    override fun cacheDir(): File = context.cacheDir

    override fun exists(file: File): Boolean = file.exists()

    override fun canRead(file: File): Boolean = file.canRead()

    override fun length(file: File): Long = file.length()

    override fun openInput(file: File): InputStream = FileInputStream(file)

    override fun openOutput(file: File): OutputStream = FileOutputStream(file)

    override fun createNewFile(file: File): Boolean = file.createNewFile()

    override fun delete(file: File): Boolean = file.delete()
}
