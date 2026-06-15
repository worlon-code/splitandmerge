package com.splitandmerge.mkvslice.domain.merger

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MergeCacheSweeperTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var cacheDir: File

    @Before
    fun setUp() {
        cacheDir = tmpFolder.newFolder("cache")
    }

    // ── Whitelist pattern tests ───────────────────────────────────────────────

    @Test fun `isWhitelisted - staged_part prefix`() {
        assertTrue(MergeCacheSweeper.isWhitelisted("staged_part_0.mkv"))
        assertTrue(MergeCacheSweeper.isWhitelisted("staged_part_99.mkv"))
    }

    @Test fun `isWhitelisted - merge_tmp prefix`() {
        assertTrue(MergeCacheSweeper.isWhitelisted("merge_tmp.mkv"))
        assertTrue(MergeCacheSweeper.isWhitelisted("merge_tmp_abc"))
    }

    @Test fun `isWhitelisted - concat dot txt exact`() {
        assertTrue(MergeCacheSweeper.isWhitelisted("concat.txt"))
    }

    @Test fun `isWhitelisted - part tmp suffix`() {
        assertTrue(MergeCacheSweeper.isWhitelisted("output.part.tmp"))
    }

    @Test fun `isWhitelisted - mkv tmp suffix`() {
        assertTrue(MergeCacheSweeper.isWhitelisted("myvideo.mkv.tmp"))
    }

    @Test fun `isWhitelisted - mp4 tmp suffix`() {
        assertTrue(MergeCacheSweeper.isWhitelisted("myvideo.mp4.tmp"))
    }

    @Test fun `isWhitelisted - Room db lck is NOT whitelisted`() {
        assertFalse(MergeCacheSweeper.isWhitelisted("mkvslice_db.lck"))
    }

    @Test fun `isWhitelisted - Room db file is NOT whitelisted`() {
        assertFalse(MergeCacheSweeper.isWhitelisted("mkvslice_db"))
    }

    @Test fun `isWhitelisted - Room wal file is NOT whitelisted`() {
        assertFalse(MergeCacheSweeper.isWhitelisted("mkvslice_db-wal"))
    }

    @Test fun `isWhitelisted - Room shm file is NOT whitelisted`() {
        assertFalse(MergeCacheSweeper.isWhitelisted("mkvslice_db-shm"))
    }

    @Test fun `isWhitelisted - arbitrary file not in list`() {
        assertFalse(MergeCacheSweeper.isWhitelisted("some_other_file.bin"))
        assertFalse(MergeCacheSweeper.isWhitelisted("thumbnail.jpg"))
        assertFalse(MergeCacheSweeper.isWhitelisted("http_cache"))
    }

    // ── Sweep behaviour tests ─────────────────────────────────────────────────

    @Test fun `stale whitelisted files are deleted`() {
        val staleFile = File(cacheDir, "staged_part_0.mkv").also { it.createNewFile() }
        // Back-date the file well beyond the cutoff.
        staleFile.setLastModified(System.currentTimeMillis() - 120_000L)

        MergeCacheSweeper.sweep(cacheDir, cutoffMs = 60_000L)

        assertFalse("stale staged file should be deleted", staleFile.exists())
    }

    @Test fun `fresh whitelisted files are kept`() {
        val freshFile = File(cacheDir, "staged_part_0.mkv").also { it.createNewFile() }
        // lastModified defaults to now — well inside the 60 s window.

        MergeCacheSweeper.sweep(cacheDir, cutoffMs = 60_000L)

        assertTrue("fresh staged file should be kept", freshFile.exists())
    }

    @Test fun `mkvslice_db lck is never deleted even when stale`() {
        val lockFile = File(cacheDir, "mkvslice_db.lck").also { it.createNewFile() }
        lockFile.setLastModified(System.currentTimeMillis() - 300_000L)

        MergeCacheSweeper.sweep(cacheDir, cutoffMs = 60_000L)

        assertTrue("Room lock file must never be deleted", lockFile.exists())
    }

    @Test fun `all A7 patterns deleted when stale`() {
        val patterns = listOf(
            "staged_part_1.mkv",
            "merge_tmp.mkv",
            "concat.txt",
            "output.part.tmp",
            "movie.mkv.tmp",
            "clip.mp4.tmp"
        )
        val files = patterns.map { name ->
            File(cacheDir, name).also {
                it.createNewFile()
                it.setLastModified(System.currentTimeMillis() - 120_000L)
            }
        }

        MergeCacheSweeper.sweep(cacheDir, cutoffMs = 60_000L)

        files.forEach { file ->
            assertFalse("${file.name} should be deleted", file.exists())
        }
    }

    @Test fun `non-whitelisted files are never deleted even when stale`() {
        val safeFiles = listOf(
            "mkvslice_db.lck",
            "mkvslice_db",
            "mkvslice_db-wal",
            "mkvslice_db-shm",
            "thumbnail.jpg",
            "http_cache"
        ).map { name ->
            File(cacheDir, name).also {
                it.createNewFile()
                it.setLastModified(System.currentTimeMillis() - 300_000L)
            }
        }

        MergeCacheSweeper.sweep(cacheDir, cutoffMs = 60_000L)

        safeFiles.forEach { file ->
            assertTrue("${file.name} should never be deleted", file.exists())
        }
    }

    @Test fun `sweep on empty directory does not throw`() {
        // cacheDir is empty; must complete without exception.
        MergeCacheSweeper.sweep(cacheDir)
    }

    @Test fun `sweep ignores subdirectories`() {
        val subDir = File(cacheDir, "staged_part_subdir").also { it.mkdir() }

        MergeCacheSweeper.sweep(cacheDir, cutoffMs = 0L) // cutoff = 0 so everything is "old"

        assertTrue("subdirectories should not be deleted", subDir.exists())
    }
}
