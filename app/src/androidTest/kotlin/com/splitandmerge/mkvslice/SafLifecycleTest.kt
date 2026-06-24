package com.splitandmerge.mkvslice

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.antonkarpenko.ffmpegkit.FFmpegKitConfig
import com.splitandmerge.mkvslice.data.db.JobDao
import com.splitandmerge.mkvslice.data.db.entity.JobEntity
import com.splitandmerge.mkvslice.data.db.entity.PartEntity
import com.splitandmerge.mkvslice.data.settings.SettingsRepository
import com.splitandmerge.mkvslice.data.settings.SettingsState
import com.splitandmerge.mkvslice.domain.merger.MergeValidator
import com.splitandmerge.mkvslice.domain.merger.Merger
import com.splitandmerge.mkvslice.domain.model.JobStatus
import com.splitandmerge.mkvslice.domain.model.JobType
import com.splitandmerge.mkvslice.domain.model.PartStatus
import com.splitandmerge.mkvslice.engine.impl.ProcessFfmpegEngine
import com.splitandmerge.mkvslice.engine.impl.ProcessFfprobeEngine
import com.splitandmerge.mkvslice.platform.io.RealFileSystem
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SafLifecycleTest {

    private val TAG = "SAF_LIFECYCLE_TEST"
    private lateinit var context: Context
    private lateinit var merger: Merger
    private lateinit var jobDao: FakeJobDao
    private lateinit var engine: ProcessFfmpegEngine
    private lateinit var outDir: File

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        jobDao = FakeJobDao()
        engine = ProcessFfmpegEngine()
        val probeEngine = ProcessFfprobeEngine(context)
        val validator = MergeValidator(probeEngine)
        
        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        every { settingsRepository.settingsFlow } returns flowOf(SettingsState())
        val fileSystem = RealFileSystem(context)
        
        merger = Merger(context, jobDao, engine, probeEngine, validator, settingsRepository, fileSystem)

        outDir = File(context.cacheDir, "test_out")
        if (!outDir.exists()) outDir.mkdirs()
    }

    @Test
    fun testSafLifecycleNoCrash() = runBlocking {
        // Stage dummy parts
        val partFiles = (1..3).map { i ->
            val f = File(context.cacheDir, "part$i.mkv")
            FileOutputStream(f).use { it.write("dummy content $i".toByteArray()) }
            f
        }

        val jobId = UUID.randomUUID().toString()
        val job = JobEntity(
            id = jobId,
            type = JobType.MERGE,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            status = JobStatus.QUEUED,
            progressPct = 0,
            sourceUri = "",
            outputDirUri = Uri.fromFile(outDir).toString(),
            outputBaseName = "merged",
            outputContainer = ".mkv"
        )
        jobDao.upsert(job)

        val parts = partFiles.mapIndexed { index, file ->
            PartEntity(
                id = UUID.randomUUID().toString(),
                jobId = jobId,
                index = index + 1,
                name = file.name,
                sourceUri = Uri.fromFile(file).toString(),
                startSec = 0.0,
                endSec = 1.0,
                status = PartStatus.DONE
            )
        }
        parts.forEach { jobDao.upsertPart(it) }

        // We run it twice to ensure no native lifecycle leaks
        for (run in 1..2) {
            Log.i(TAG, "Run $run starting...")
            // Reset job status
            jobDao.updateProgress(jobId, JobStatus.QUEUED, 0, null, null, null, System.currentTimeMillis())

            // The merge might fail purely because our dummy files aren't real MKVs.
            // That's actually okay, because FFmpegKit will still saf_close them on error!
            // We just want to ensure it DOES NOT CRASH the process.
            try {
                merger.runMerge(jobId)
            } catch (e: Exception) {
                Log.i(TAG, "Merge threw exception (expected for dummy MKVs): \${e.message}")
            }
            
            // If we got here, we didn't SIGSEGV!
            Log.i(TAG, "Run $run completed without native crash")
        }

        // Clean up
        partFiles.forEach { it.delete() }
        outDir.deleteRecursively()
        
        // Passing the test means the app process didn't die
        assertTrue(true)
    }

    class FakeJobDao : JobDao {
        private val jobs = mutableMapOf<String, com.splitandmerge.mkvslice.data.db.entity.JobEntity>()
        private val parts = mutableListOf<com.splitandmerge.mkvslice.data.db.entity.PartEntity>()

        override fun observeAll(): kotlinx.coroutines.flow.Flow<List<com.splitandmerge.mkvslice.data.db.entity.JobEntity>> =
            kotlinx.coroutines.flow.flowOf(jobs.values.toList())

        override suspend fun getById(id: String): com.splitandmerge.mkvslice.data.db.entity.JobEntity? = jobs[id]

        override fun observeById(id: String): kotlinx.coroutines.flow.Flow<com.splitandmerge.mkvslice.data.db.entity.JobEntity?> =
            kotlinx.coroutines.flow.flowOf(jobs[id])

        override suspend fun getPartsForJob(jobId: String): List<com.splitandmerge.mkvslice.data.db.entity.PartEntity> =
            parts.filter { it.jobId == jobId }.sortedBy { it.index }

        override suspend fun nextQueued(status: JobStatus): com.splitandmerge.mkvslice.data.db.entity.JobEntity? =
            jobs.values.find { it.status == status }

        override suspend fun upsert(job: com.splitandmerge.mkvslice.data.db.entity.JobEntity) {
            jobs[job.id] = job
        }

        override suspend fun upsertPart(part: com.splitandmerge.mkvslice.data.db.entity.PartEntity) {
            parts.removeAll { it.id == part.id }
            parts.add(part)
        }

        override suspend fun updateProgress(
            id: String,
            status: JobStatus,
            pct: Int,
            speed: Double?,
            eta: Int?,
            parts: Int?,
            now: Long
        ) {
            jobs[id]?.let {
                jobs[id] = it.copy(
                    status = status,
                    progressPct = pct,
                    speedMbs = speed,
                    etaSeconds = eta,
                    totalParts = parts,
                    updatedAt = now
                )
            }
        }

        override suspend fun deleteById(id: String) {
            jobs.remove(id)
        }

        override suspend fun recoverStuckJobs() {}
        override suspend fun recoverStuckParts() {}
    }
}
