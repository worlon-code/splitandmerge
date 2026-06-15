package com.splitandmerge.mkvslice.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.splitandmerge.mkvslice.domain.cleanup.DEFAULT_CLEANUP_PATTERNS

object Migration_3_4 : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.beginTransaction()
        try {
            // 1. Create table
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `cleanup_patterns` (
                    `id` TEXT NOT NULL, 
                    `regex` TEXT NOT NULL, 
                    `replacement` TEXT NOT NULL, 
                    `enabled` INTEGER NOT NULL, 
                    `isBuiltIn` INTEGER NOT NULL, 
                    `orderIndex` INTEGER NOT NULL, 
                    `label` TEXT NOT NULL, 
                    `createdAt` INTEGER NOT NULL, 
                    PRIMARY KEY(`id`)
                )
            """.trimIndent())

            // 2. Create index on orderIndex
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cleanup_patterns_orderIndex` ON `cleanup_patterns` (`orderIndex`)")

            // 3. Seed built-in rules
            seedDatabasePatterns(db)

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun seedDatabasePatterns(db: SupportSQLiteDatabase) {
        for (pattern in DEFAULT_CLEANUP_PATTERNS) {
            val enabledInt = if (pattern.enabled) 1 else 0
            val isBuiltInInt = if (pattern.isBuiltIn) 1 else 0
            val escapedRegex = sqlEscape(pattern.regex)
            val escapedReplacement = sqlEscape(pattern.replacement)
            val escapedLabel = sqlEscape(pattern.label)
            
            db.execSQL("""
                INSERT OR REPLACE INTO `cleanup_patterns` 
                (`id`, `regex`, `replacement`, `enabled`, `isBuiltIn`, `orderIndex`, `label`, `createdAt`) 
                VALUES 
                ('${pattern.id}', '$escapedRegex', '$escapedReplacement', $enabledInt, $isBuiltInInt, ${pattern.orderIndex}, '$escapedLabel', ${pattern.createdAt})
            """.trimIndent())
        }
    }

    private fun sqlEscape(s: String): String = s.replace("'", "''")
}
