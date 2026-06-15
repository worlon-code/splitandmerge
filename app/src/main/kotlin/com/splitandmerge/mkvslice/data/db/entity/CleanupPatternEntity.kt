package com.splitandmerge.mkvslice.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cleanup_patterns",
    indices = [Index(value = ["orderIndex"])]
)
data class CleanupPatternEntity(
    @PrimaryKey val id: String,
    val regex: String,
    val replacement: String,
    val enabled: Boolean,
    val isBuiltIn: Boolean,
    val orderIndex: Int,
    val label: String,
    val createdAt: Long
)
