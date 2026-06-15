package com.splitandmerge.mkvslice.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.splitandmerge.mkvslice.data.db.AppDatabase
import com.splitandmerge.mkvslice.data.db.JobDao
import com.splitandmerge.mkvslice.data.db.CleanupPatternDao
import com.splitandmerge.mkvslice.data.db.migrations.Migration_3_4
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "mkvslice_db"
        )
        .addMigrations(Migration_3_4)
        .addCallback(object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                Migration_3_4.seedDatabasePatterns(db)
            }
        })
        .build()
    }

    @Provides
    fun provideJobDao(db: AppDatabase): JobDao {
        return db.jobDao()
    }

    @Provides
    fun provideCleanupPatternDao(db: AppDatabase): CleanupPatternDao {
        return db.cleanupPatternDao()
    }
}
