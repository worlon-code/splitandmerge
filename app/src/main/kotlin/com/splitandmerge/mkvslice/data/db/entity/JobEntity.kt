package com.splitandmerge.mkvslice.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.splitandmerge.mkvslice.domain.model.JobStatus
import com.splitandmerge.mkvslice.domain.model.JobType
import com.splitandmerge.mkvslice.domain.model.SplitMode

@Entity(tableName = "jobs")
data class JobEntity(
    @PrimaryKey val id: String,
    val type: JobType,                  // SPLIT | MERGE
    val createdAt: Long,                // epoch millis
    val updatedAt: Long,
    val status: JobStatus,              // QUEUED, RUNNING, DONE, FAILED, CANCELLED
    val progressPct: Int,               // 0..100
    val errorMessage: String? = null,
    val sourceUri: String,              // SAF URI
    val outputDirUri: String,           // tree URI
    val outputBaseName: String,
    val outputContainer: String,        // ".mkv" | ".mp4" | …
    val mode: SplitMode? = null,        // null for MERGE
    val requestedParts: Int? = null,
    val targetCapBytes: Long? = null,
    val ceilingCapBytes: Long? = null,
    val manifestPath: String? = null,
    val speedMbs: Double? = null,       // Realtime processing speed in MB/s or 'x'
    val etaSeconds: Int? = null,        // Estimated time remaining
    val totalParts: Int? = null,        // Actual calculated parts by CutPlanner
    @ColumnInfo(defaultValue = "STRUCTURAL") val splitFormat: String = "STRUCTURAL",
)
