package com.splitandmerge.mkvslice.domain.defaulttracks

import com.splitandmerge.mkvslice.domain.defaulttracks.model.TrackInfo
import com.splitandmerge.mkvslice.platform.io.FileDescriptorWrapper

class TrackAnalyser {
    private val reader = EbmlReader()

    fun analyse(fd: FileDescriptorWrapper): List<TrackInfo> {
        val parsed = reader.parse(fd)
        return parsed.tracks
    }
}
