package com.splitandmerge.mkvslice.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.splitandmerge.mkvslice.domain.model.PartStatus

@Entity(
    tableName = "parts",
    foreignKeys = [
        ForeignKey(
            entity = JobEntity::class,
            parentColumns = ["id"],
            childColumns = ["jobId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("jobId")]
)
data class PartEntity(
    @PrimaryKey val id: String,
    val jobId: String,
    val index: Int,                     // 1-based for display
    val name: String,
    val sourceUri: String? = null,      // Added for MERGE parts
    val startSec: Double,
    val endSec: Double,
    val sizeBytes: Long? = null,        // null until written
    val sha256: String? = null,         // null in v1 (computed on demand)
    val status: PartStatus,
)
