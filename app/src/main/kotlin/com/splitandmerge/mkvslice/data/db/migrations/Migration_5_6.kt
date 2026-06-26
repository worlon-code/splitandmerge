package com.splitandmerge.mkvslice.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migration_5_6 : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `default_track_file_results` (
                `id` TEXT NOT NULL, 
                `jobId` TEXT NOT NULL, 
                `uri` TEXT NOT NULL, 
                `displayName` TEXT NOT NULL, 
                `status` TEXT NOT NULL DEFAULT 'UNKNOWN', 
                `reason` TEXT NOT NULL DEFAULT '', 
                `writeStrategy` TEXT NOT NULL DEFAULT 'SKIPPED', 
                `appliedSpecJson` TEXT NOT NULL DEFAULT '', 
                `createdAt` INTEGER NOT NULL, 
                PRIMARY KEY(`id`), 
                FOREIGN KEY(`jobId`) REFERENCES `jobs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_default_track_file_results_jobId` ON `default_track_file_results` (`jobId`)")
    }
}
