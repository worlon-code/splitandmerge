package com.splitandmerge.mkvslice.domain.model

import com.splitandmerge.mkvslice.domain.splitter.CutPlan

data class SplitJob(
    val id: String,                     // UUID
    val title: String,                  // cleaned title
    val sourceUri: String,
    val outputDirUri: String,
    val plan: CutPlan,
    val container: String,              // ".mkv" | ".mp4" | …
    val streams: ProbeResult,
)
