package com.splitandmerge.mkvslice.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.splitandmerge.mkvslice.data.db.entity.JobEntity
import com.splitandmerge.mkvslice.data.db.entity.PartEntity
import com.splitandmerge.mkvslice.data.db.entity.CleanupPatternEntity

@Database(
    entities = [
        JobEntity::class,
        PartEntity::class,
        CleanupPatternEntity::class
    ],
    version = 5,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun jobDao(): JobDao
    abstract fun cleanupPatternDao(): CleanupPatternDao
}
