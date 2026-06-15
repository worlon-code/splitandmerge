package com.splitandmerge.mkvslice.engine

import android.content.Context
import android.net.Uri
import com.antonkarpenko.ffmpegkit.FFmpegKit
import com.antonkarpenko.ffmpegkit.FFmpegKitConfig

class FfmpegKitApiCheck {
    fun check(context: Context, uri: Uri) {
        val saf: String = FFmpegKitConfig.getSafParameterForRead(context, uri)
        FFmpegKit.executeWithArgumentsAsync(arrayOf("a")) { }
    }
}
