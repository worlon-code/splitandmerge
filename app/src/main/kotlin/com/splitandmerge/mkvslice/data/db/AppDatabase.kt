package com.splitandmerge.mkvslice.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.splitandmerge.mkvslice.data.db.entity.JobEntity
import com.splitandmerge.mkvslice.data.db.entity.PartEntity
import com.splitandmerge.mkvslice.data.db.entity.CleanupPatternEntity
import com.splitandmerge.mkvslice.data.db.entity.DefaultTrackFileResultEntity

@Database(
    entities = [
        JobEntity::class,
        PartEntity::class,
        CleanupPatternEntity::class,
        DefaultTrackFileResultEntity::class
    ],
    version = 6,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun jobDao(): JobDao
    abstract fun cleanupPatternDao(): CleanupPatternDao
    abstract fun defaultTrackFileResultDao(): DefaultTrackFileResultDao
}
