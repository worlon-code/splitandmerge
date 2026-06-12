package com.splitandmerge.mkvslice.di

import android.content.Context
import androidx.room.Room
import com.splitandmerge.mkvslice.data.db.AppDatabase
import com.splitandmerge.mkvslice.data.db.JobDao
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
        // Auto-migrations aren't used for initial v1
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    fun provideJobDao(db: AppDatabase): JobDao {
        return db.jobDao()
    }
}
