package com.splitandmerge.mkvslice.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.splitandmerge.mkvslice.data.db.entity.CleanupPatternEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CleanupPatternDao {

    @Query("SELECT * FROM cleanup_patterns ORDER BY orderIndex ASC")
    fun observeAll(): Flow<List<CleanupPatternEntity>>

    @Query("SELECT * FROM cleanup_patterns ORDER BY orderIndex ASC")
    suspend fun getAll(): List<CleanupPatternEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(pattern: CleanupPatternEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(patterns: List<CleanupPatternEntity>)

    @Query("DELETE FROM cleanup_patterns WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM cleanup_patterns WHERE isBuiltIn = 1")
    suspend fun deleteBuiltIns()
}
