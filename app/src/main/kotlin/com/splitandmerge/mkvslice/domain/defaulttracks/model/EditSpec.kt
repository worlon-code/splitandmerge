package com.splitandmerge.mkvslice.domain.defaulttracks.model

data class EditSpec(
    val defaultAudioTrackNumber: Long,
    val defaultSubtitleTrackNumber: Long?,
    val forcedSubtitle: Boolean
)
