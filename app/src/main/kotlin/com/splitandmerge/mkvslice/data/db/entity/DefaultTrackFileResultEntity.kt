package com.splitandmerge.mkvslice.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "default_track_file_results",
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
data class DefaultTrackFileResultEntity(
    @PrimaryKey val id: String,
    val jobId: String,
    val uri: String,
    val displayName: String,
    @ColumnInfo(defaultValue = "UNKNOWN") val status: String = "UNKNOWN",
    @ColumnInfo(defaultValue = "") val reason: String = "",
    @ColumnInfo(defaultValue = "SKIPPED") val writeStrategy: String = "SKIPPED",
    @ColumnInfo(defaultValue = "") val appliedSpecJson: String = "",
    val createdAt: Long
)
