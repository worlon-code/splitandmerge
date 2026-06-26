package com.splitandmerge.mkvslice.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.splitandmerge.mkvslice.data.db.entity.DefaultTrackFileResultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DefaultTrackFileResultDao {
    @Query("SELECT * FROM default_track_file_results WHERE jobId = :jobId ORDER BY createdAt ASC")
    fun observeResultsForJob(jobId: String): Flow<List<DefaultTrackFileResultEntity>>

    @Query("SELECT * FROM default_track_file_results WHERE jobId = :jobId ORDER BY createdAt ASC")
    suspend fun getResultsForJob(jobId: String): List<DefaultTrackFileResultEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(result: DefaultTrackFileResultEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(results: List<DefaultTrackFileResultEntity>)
}
