package com.splitandmerge.mkvslice.data.update

import java.io.File

sealed interface Verified {
    val file: File
    val expectedLength: Long
}
