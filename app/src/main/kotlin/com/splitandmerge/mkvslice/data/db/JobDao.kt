package com.splitandmerge.mkvslice.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.splitandmerge.mkvslice.data.db.entity.JobEntity
import com.splitandmerge.mkvslice.domain.model.JobStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface JobDao {
    @Query("SELECT * FROM jobs ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<JobEntity>>

    @Query("SELECT * FROM jobs WHERE id = :id")
    suspend fun getById(id: String): JobEntity?

    @Query("SELECT * FROM jobs WHERE id = :id")
    fun observeById(id: String): Flow<JobEntity?>

    @Query("SELECT * FROM parts WHERE jobId = :jobId ORDER BY `index` ASC")
    suspend fun getPartsForJob(jobId: String): List<com.splitandmerge.mkvslice.data.db.entity.PartEntity>

    @Query("SELECT * FROM jobs WHERE status = :status ORDER BY createdAt ASC LIMIT 1")
    suspend fun nextQueued(status: JobStatus = JobStatus.QUEUED): JobEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(job: JobEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPart(part: com.splitandmerge.mkvslice.data.db.entity.PartEntity)

    @Query("UPDATE jobs SET status = :status, progressPct = :pct, speedMbs = :speed, etaSeconds = :eta, totalParts = :parts, updatedAt = :now WHERE id = :id")
    suspend fun updateProgress(id: String, status: JobStatus, pct: Int, speed: Double?, eta: Int?, parts: Int?, now: Long)

    @Query("DELETE FROM jobs WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Startup recovery: any job still RUNNING after a process kill is marked FAILED.
     * Safe to call on a clean install (no rows matched → no-op).
     */
    @Query("UPDATE jobs SET status = 'FAILED', errorMessage = 'Process killed unexpectedly' WHERE status = 'RUNNING'")
    suspend fun recoverStuckJobs()

    /**
     * Paired with [recoverStuckJobs]: marks RUNNING parts as FAILED (A5).
     */
    @Query("UPDATE parts SET status = 'FAILED' WHERE status = 'RUNNING'")
    suspend fun recoverStuckParts()
}
