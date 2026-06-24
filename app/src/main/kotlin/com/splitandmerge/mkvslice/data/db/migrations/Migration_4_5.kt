package com.splitandmerge.mkvslice.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration_4_5 : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `jobs` ADD COLUMN `splitFormat` TEXT NOT NULL DEFAULT 'STRUCTURAL'")
        db.execSQL("ALTER TABLE `parts` ADD COLUMN `byteOffset` INTEGER")
        db.execSQL("ALTER TABLE `parts` ADD COLUMN `byteSize` INTEGER")
    }
}
