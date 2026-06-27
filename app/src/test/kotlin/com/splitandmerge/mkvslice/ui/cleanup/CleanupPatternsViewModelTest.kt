package com.splitandmerge.mkvslice.ui.cleanup

import com.splitandmerge.mkvslice.data.db.entity.CleanupPatternEntity
import com.splitandmerge.mkvslice.data.repository.CleanupRepository
import com.splitandmerge.mkvslice.domain.cleanup.DEFAULT_CLEANUP_PATTERNS
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import com.splitandmerge.mkvslice.data.cleanup.backup.CleanupBackupFile
import com.splitandmerge.mkvslice.data.cleanup.backup.CleanupPatternBackup
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CleanupPatternsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockRepository: CleanupRepository
    private val patternsFlow = MutableStateFlow(DEFAULT_CLEANUP_PATTERNS)
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockRepository = mockk(relaxed = true)
        every { mockRepository.observePatterns() } returns patternsFlow
        coEvery { mockRepository.getAllPatterns() } coAnswers { patternsFlow.value }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun test_init_observesRepository() = runTest {
        val vm = CleanupPatternsViewModel(mockRepository, json, testDispatcher)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(12, vm.state.value.patterns.size)
    }

    @Test
    fun test_togglePattern_callsRepository() = runTest {
        val vm = CleanupPatternsViewModel(mockRepository, json, testDispatcher)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.togglePattern("url_prefix", false)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { mockRepository.upsert(match { it.id == "url_prefix" && !it.enabled }) }
    }

    @Test
    fun test_addPattern_callsRepository() = runTest {
        val vm = CleanupPatternsViewModel(mockRepository, json, testDispatcher)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.addPattern("test-regex", "test-label")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { mockRepository.upsert(match { it.regex == "test-regex" && it.label == "test-label" && !it.isBuiltIn }) }
    }

    @Test
    fun test_deletePattern_callsRepository() = runTest {
        val vm = CleanupPatternsViewModel(mockRepository, json, testDispatcher)
        testDispatcher.scheduler.advanceUntilIdle()

        val customRule = CleanupPatternEntity(
            id = "custom-1",
            regex = "rule",
            replacement = "",
            enabled = true,
            isBuiltIn = false,
            orderIndex = 12,
            label = "Custom",
            createdAt = 12345L
        )
        patternsFlow.value = DEFAULT_CLEANUP_PATTERNS + customRule
        testDispatcher.scheduler.advanceUntilIdle()

        vm.deletePattern("custom-1")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { mockRepository.delete("custom-1") }
    }

    @Test
    fun test_deletePattern_builtIn_doesNotCallRepository() = runTest {
        val vm = CleanupPatternsViewModel(mockRepository, json, testDispatcher)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.deletePattern("url_prefix")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { mockRepository.delete("url_prefix") }
    }

    @Test
    fun test_backup_roundtrip() = runTest {
        val vm = CleanupPatternsViewModel(mockRepository, json, testDispatcher)
        testDispatcher.scheduler.advanceUntilIdle()

        // Create a mock stream to capture output
        val outputStream = java.io.ByteArrayOutputStream()
        vm.exportBackup(outputStream)
        testDispatcher.scheduler.advanceUntilIdle()

        val jsonString = outputStream.toString("UTF-8")
        val backupFile = json.decodeFromString<CleanupBackupFile>(jsonString)

        assertEquals(12, backupFile.patterns.size)
        // Verify a built-in one matches
        val urlPrefixBackup = backupFile.patterns.find { it.regex == "^www\\.[^\\s\\-]+\\s*[-\\u2013]\\s*" }
        assertEquals(true, urlPrefixBackup?.enabled)
        assertEquals("", urlPrefixBackup?.replacement)
    }

    @Test
    fun test_restore_deduplication() = runTest {
        val vm = CleanupPatternsViewModel(mockRepository, json, testDispatcher)
        testDispatcher.scheduler.advanceUntilIdle()

        // Prepare backup JSON
        val backup = CleanupBackupFile(
            version = 1,
            patterns = listOf(
                // 1. Exact duplicate (same regex, same replacement, different label/enabled) -> IGNORED
                CleanupPatternBackup(
                    regex = "^www\\.[^\\s\\-]+\\s*[-\\u2013]\\s*", // existing built-in pattern has this regex and replacement=""
                    replacement = "",
                    enabled = false,
                    label = "Duplicate URL Prefix"
                ),
                // 2. Trailing space difference in regex -> NON-equivalent -> ADDED
                CleanupPatternBackup(
                    regex = "^www\\.[^\\s\\-]+\\s*[-\\u2013]\\s* ",
                    replacement = "",
                    enabled = true,
                    label = "New URL Prefix with Space"
                ),
                // 3. Brand new pattern -> ADDED
                CleanupPatternBackup(
                    regex = "extra-regex",
                    replacement = "[extra]",
                    enabled = false,
                    label = "Brand New Pattern"
                )
            )
        )
        val backupJson = json.encodeToString(backup)
        val inputStream = java.io.ByteArrayInputStream(backupJson.toByteArray(Charsets.UTF_8))

        // Reset flow to contain original patterns
        patternsFlow.value = DEFAULT_CLEANUP_PATTERNS
        
        vm.importRestore(inputStream)
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify restoreOutcome state
        val outcome = vm.state.value.restoreOutcome
        org.junit.Assert.assertNotNull(outcome)
        assertEquals(2, outcome?.addedCount)
        assertEquals(1, outcome?.ignoredPatterns?.size)
        assertEquals("Duplicate URL Prefix", outcome?.ignoredPatterns?.first()?.first)
        // Check exact match label in original built-ins
        val existingMatchLabel = outcome?.ignoredPatterns?.first()?.second
        assertEquals("Strip leading URL prefix", existingMatchLabel)

        // Verify repository interaction
        coVerify {
            mockRepository.addAll(match { list ->
                list.size == 2 &&
                list[0].regex == "^www\\.[^\\s\\-]+\\s*[-\\u2013]\\s* " && list[0].label == "New URL Prefix with Space" && list[0].enabled &&
                list[1].regex == "extra-regex" && list[1].replacement == "[extra]" && !list[1].enabled &&
                list[0].orderIndex == 12 && list[1].orderIndex == 13
            })
        }
    }

    @Test
    fun test_cleanTitleWith_replacement() {
        val pattern = CleanupPatternEntity(
            id = "test-1",
            regex = "rule",
            replacement = "success",
            enabled = true,
            isBuiltIn = false,
            orderIndex = 1,
            label = "Test Rule",
            createdAt = 12345L
        )
        // (a) a pattern with a non-empty replacement REPLACES the matched text (not deletes)
        val cleaned = com.splitandmerge.mkvslice.domain.cleanup.cleanTitleWith("my-rule-name", listOf(pattern))
        assertEquals("my-success-name", cleaned)
    }

    @Test
    fun test_updatePattern_replacement_persists() = runTest {
        val vm = CleanupPatternsViewModel(mockRepository, json, testDispatcher)
        testDispatcher.scheduler.advanceUntilIdle()

        val existingPattern = CleanupPatternEntity(
            id = "rule-1",
            regex = "old-regex",
            replacement = "old-replacement",
            enabled = true,
            isBuiltIn = false,
            orderIndex = 1,
            label = "Old Label",
            createdAt = 12345L
        )
        patternsFlow.value = listOf(existingPattern)
        testDispatcher.scheduler.advanceUntilIdle()

        // (b) editing an existing pattern's replacement persists (upsert same id)
        vm.updatePattern("rule-1", "new-regex", "New Label", "new-replacement")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify {
            mockRepository.upsert(match {
                it.id == "rule-1" &&
                it.regex == "new-regex" &&
                it.label == "New Label" &&
                it.replacement == "new-replacement"
            })
        }
    }

    @Test
    fun test_createPattern_replacement_persists() = runTest {
        val vm = CleanupPatternsViewModel(mockRepository, json, testDispatcher)
        testDispatcher.scheduler.advanceUntilIdle()

        // (c) create-with-replacement persists with that replacement
        vm.addPattern("regex-val", "label-val", "replacement-val")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify {
            mockRepository.upsert(match {
                it.regex == "regex-val" &&
                it.label == "label-val" &&
                it.replacement == "replacement-val" &&
                !it.isBuiltIn
            })
        }
    }
}
