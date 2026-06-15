package com.splitandmerge.mkvslice.data.repository

import com.splitandmerge.mkvslice.data.db.CleanupPatternDao
import com.splitandmerge.mkvslice.data.db.entity.CleanupPatternEntity
import com.splitandmerge.mkvslice.domain.cleanup.DEFAULT_CLEANUP_PATTERNS
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CleanupRepository @Inject constructor(
    private val dao: CleanupPatternDao
) {
    fun observePatterns(): Flow<List<CleanupPatternEntity>> = dao.observeAll()

    suspend fun getAllPatterns(): List<CleanupPatternEntity> = dao.getAll()

    suspend fun upsert(pattern: CleanupPatternEntity) {
        dao.upsert(pattern)
    }

    suspend fun delete(id: String) {
        dao.deleteById(id)
    }

    suspend fun resetToDefaults() {
        dao.deleteBuiltIns()
        dao.insertAll(DEFAULT_CLEANUP_PATTERNS)
    }
}
