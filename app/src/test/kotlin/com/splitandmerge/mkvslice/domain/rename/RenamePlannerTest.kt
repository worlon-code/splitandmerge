package com.splitandmerge.mkvslice.domain.rename

import com.splitandmerge.mkvslice.data.db.entity.CleanupPatternEntity
import com.splitandmerge.mkvslice.domain.cleanup.DEFAULT_CLEANUP_PATTERNS
import com.splitandmerge.mkvslice.domain.cleanup.TitleCleaner
import com.splitandmerge.mkvslice.domain.cleanup.cleanTitleWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class RenamePlannerTest {

    @Test
    fun test_cleanTitleWith_purity() {
        val mockRepo = io.mockk.mockk<com.splitandmerge.mkvslice.data.repository.CleanupRepository>(relaxed = true)
        io.mockk.every { mockRepo.observePatterns() } returns kotlinx.coroutines.flow.emptyFlow()
        val titleCleaner = TitleCleaner(mockRepo)
        
        // Set global cachedPatterns to a conflicting set via reflection
        val field = TitleCleaner::class.java.getDeclaredField("cachedPatterns")
        field.isAccessible = true
        
        val conflictingPatterns = listOf(
            CleanupPatternEntity(
                id = "conflict",
                regex = "^.*$",
                replacement = "GLOBAL_CONFLICT",
                enabled = true,
                isBuiltIn = false,
                orderIndex = 0,
                label = "Conflict",
                createdAt = 0L
            )
        )
        field.set(titleCleaner, conflictingPatterns)
        
        // Verify that titleCleaner.cleanTitle indeed returns the conflict title
        assertEquals("GLOBAL_CONFLICT", titleCleaner.cleanTitle("some_title.mkv"))
        
        // Define our explicit passed patterns
        val explicitPatterns = listOf(
            CleanupPatternEntity(
                id = "pass-1",
                regex = "replace_me",
                replacement = "passed_value",
                enabled = false, // even if false, cleanTitleWith applies it!
                isBuiltIn = false,
                orderIndex = 1,
                label = "Passed 1",
                createdAt = 0L
            ),
            CleanupPatternEntity(
                id = "pass-2",
                regex = "prefix_",
                replacement = "",
                enabled = true,
                isBuiltIn = false,
                orderIndex = 0, // orderIndex 0 means this runs FIRST
                label = "Passed 2",
                createdAt = 0L
            )
        )
        
        // Pass them in out-of-order in the list
        val passedList = listOf(explicitPatterns[0], explicitPatterns[1])
        val result = cleanTitleWith("prefix_replace_me", passedList)
        
        // Assert output reflects ONLY the passed list
        assertEquals("passed_value", result)
        
        // Assert that the global cachedPatterns is unchanged before/after
        val currentGlobal = field.get(titleCleaner) as List<*>
        assertEquals(1, currentGlobal.size)
        assertEquals(conflictingPatterns[0], currentGlobal[0])
    }

    @Test
    fun test_cleanTitle_characterization() {
        val mockRepo = io.mockk.mockk<com.splitandmerge.mkvslice.data.repository.CleanupRepository>(relaxed = true)
        io.mockk.every { mockRepo.observePatterns() } returns kotlinx.coroutines.flow.flowOf(DEFAULT_CLEANUP_PATTERNS)
        val titleCleaner = TitleCleaner(mockRepo)
        
        // Wait briefly for observation collection
        Thread.sleep(50)
        
        // Pin cleanTitle's current output
        assertEquals("Bahubali (2025) True", titleCleaner.cleanTitle("www.5movierulz.download - Bahubali (2025) True.mkv"))
        assertEquals("Title", titleCleaner.cleanTitle("Title.1080p.BluRay.x264.mkv"))
        assertEquals("Show S01E01", titleCleaner.cleanTitle("Show.S01E01.HEVC.mkv"))
    }

    @Test
    fun test_planner_basic_cases() {
        val item1 = RenameInputItem(
            id = "1",
            oldDisplayName = "file1.mkv",
            newBaseName = "file1_new",
            supportsRename = true,
            parentKnown = false,
            existingNamesInParent = emptyList()
        )
        val item2 = RenameInputItem(
            id = "2",
            oldDisplayName = "file2.mkv",
            newBaseName = "file2_new",
            supportsRename = false,
            parentKnown = true,
            existingNamesInParent = emptyList()
        )
        val item3 = RenameInputItem(
            id = "3",
            oldDisplayName = "file3.mkv",
            newBaseName = "file3",
            supportsRename = true,
            parentKnown = true,
            existingNamesInParent = emptyList()
        )

        val results = RenamePlanner.plan(listOf(item1, item2, item3))
        
        assertEquals(RenameDecision.EXCLUDED_UNVERIFIABLE, results[0].decision)
        assertEquals("file1.mkv", results[0].targetName)
        
        assertEquals(RenameDecision.EXCLUDED_UNRENAMABLE, results[1].decision)
        assertEquals("file2.mkv", results[1].targetName)
        
        assertEquals(RenameDecision.NO_CHANGE, results[2].decision)
        assertEquals("file3.mkv", results[2].targetName)
    }

    @Test
    fun test_planner_validation_failures() {
        val testCases = listOf(
            "" to RenameDecision.EXCLUDED_INVALID,
            "a" to RenameDecision.EXCLUDED_INVALID,
            "file<name" to RenameDecision.EXCLUDED_INVALID,
            "file>name" to RenameDecision.EXCLUDED_INVALID,
            "file:name" to RenameDecision.EXCLUDED_INVALID,
            "file\"name" to RenameDecision.EXCLUDED_INVALID,
            "file/name" to RenameDecision.EXCLUDED_INVALID,
            "file\\name" to RenameDecision.EXCLUDED_INVALID,
            "file|name" to RenameDecision.EXCLUDED_INVALID,
            "file?name" to RenameDecision.EXCLUDED_INVALID,
            "file*name" to RenameDecision.EXCLUDED_INVALID,
            "file\tname" to RenameDecision.EXCLUDED_INVALID,
            " filename" to RenameDecision.EXCLUDED_INVALID,
            "filename " to RenameDecision.EXCLUDED_INVALID,
            ".filename" to RenameDecision.EXCLUDED_INVALID,
            "filename." to RenameDecision.EXCLUDED_INVALID
        )

        for ((badName, expectedDecision) in testCases) {
            val item = RenameInputItem(
                id = "id_" + badName.hashCode(),
                oldDisplayName = "original.mkv",
                newBaseName = badName,
                supportsRename = true,
                parentKnown = true,
                existingNamesInParent = emptyList()
            )
            val result = RenamePlanner.plan(listOf(item)).first()
            assertEquals("Failed for badName='$badName'", expectedDecision, result.decision)
        }
    }

    @Test
    fun test_planner_unrecognized_video_ext() {
        val item = RenameInputItem(
            id = "1",
            oldDisplayName = "file.txt",
            newBaseName = "new_base",
            supportsRename = true,
            parentKnown = true,
            existingNamesInParent = emptyList()
        )
        val result = RenamePlanner.plan(listOf(item)).first()
        assertEquals(RenameDecision.EXCLUDED_INVALID, result.decision)
    }

    @Test
    fun test_planner_existing_file_collision_and_case_insensitive() {
        val existing = listOf("collision.mkv", "AA.mkv")
        val itemA = RenameInputItem(
            id = "A",
            oldDisplayName = "fileA.mkv",
            newBaseName = "collision",
            supportsRename = true,
            parentKnown = true,
            existingNamesInParent = existing
        )
        val itemB = RenameInputItem(
            id = "B",
            oldDisplayName = "fileB.mkv",
            newBaseName = "aa",
            supportsRename = true,
            parentKnown = true,
            existingNamesInParent = existing
        )

        // Default: no opt-in to auto-suffixing, so they must SKIP_COLLISION
        val results = RenamePlanner.plan(listOf(itemA, itemB))
        
        assertEquals(RenameDecision.SKIP_COLLISION, results[0].decision)
        assertEquals("fileA.mkv", results[0].targetName)

        assertEquals(RenameDecision.SKIP_COLLISION, results[1].decision)
        assertEquals("fileB.mkv", results[1].targetName)
    }

    @Test
    fun test_planner_existing_file_collision_opt_in() {
        val existing = listOf("collision.mkv", "AA.mkv")
        val itemA = RenameInputItem(
            id = "A",
            oldDisplayName = "fileA.mkv",
            newBaseName = "collision",
            supportsRename = true,
            parentKnown = true,
            existingNamesInParent = existing
        )
        val itemB = RenameInputItem(
            id = "B",
            oldDisplayName = "fileB.mkv",
            newBaseName = "aa",
            supportsRename = true,
            parentKnown = true,
            existingNamesInParent = existing
        )

        // With opt-in: resolves via auto-suffixing
        val results = RenamePlanner.plan(listOf(itemA, itemB), perRowAutoSuffix = setOf("A", "B"))
        
        assertEquals(RenameDecision.RENAME, results[0].decision)
        assertEquals("collision (1).mkv", results[0].targetName)

        assertEquals(RenameDecision.RENAME, results[1].decision)
        assertEquals("aa (1).mkv", results[1].targetName)
    }

    @Test
    fun test_planner_risky_auto_suffix_union() {
        val existing = listOf("bb.mkv", "bb (1).mkv")
        val item1 = RenameInputItem(
            id = "1",
            oldDisplayName = "a.mkv",
            newBaseName = "bb",
            supportsRename = true,
            parentKnown = true,
            existingNamesInParent = existing
        )
        val item2 = RenameInputItem(
            id = "2",
            oldDisplayName = "c.mkv",
            newBaseName = "bb (1)",
            supportsRename = true,
            parentKnown = true,
            existingNamesInParent = existing
        )

        // Opt-in for both to test suffix resolution across active candidates
        val results = RenamePlanner.plan(listOf(item1, item2), perRowAutoSuffix = setOf("1", "2"))
        
        assertEquals(RenameDecision.RENAME, results[0].decision)
        assertEquals("bb (2).mkv", results[0].targetName)

        assertEquals(RenameDecision.RENAME, results[1].decision)
        assertEquals("bb (1) (1).mkv", results[1].targetName)
    }

    @Test
    fun test_planner_risky_intra_batch_collision_exclusion() {
        val item1 = RenameInputItem(
            id = "1",
            oldDisplayName = "x.mkv",
            newBaseName = "bb",
            supportsRename = true,
            parentKnown = true,
            existingNamesInParent = emptyList()
        )
        val item2 = RenameInputItem(
            id = "2",
            oldDisplayName = "y.mkv",
            newBaseName = "bb",
            supportsRename = true,
            parentKnown = true,
            existingNamesInParent = emptyList()
        )

        // No opt-in: defaults to SKIP_COLLISION
        val results = RenamePlanner.plan(listOf(item1, item2))
        
        assertEquals(RenameDecision.SKIP_COLLISION, results[0].decision)
        assertEquals("x.mkv", results[0].targetName)

        assertEquals(RenameDecision.SKIP_COLLISION, results[1].decision)
        assertEquals("y.mkv", results[1].targetName)
    }

    @Test
    fun test_planner_parent_scoping_isolation() {
        val itemA = RenameInputItem(
            id = "A",
            oldDisplayName = "fileA.mkv",
            newBaseName = "bb",
            supportsRename = true,
            parentKnown = true,
            existingNamesInParent = emptyList(),
            parentKey = "parent1"
        )
        val itemB = RenameInputItem(
            id = "B",
            oldDisplayName = "fileB.mkv",
            newBaseName = "bb",
            supportsRename = true,
            parentKnown = true,
            existingNamesInParent = emptyList(),
            parentKey = "parent2"
        )

        // Even without opting in, different parent folders must not collide
        val results = RenamePlanner.plan(listOf(itemA, itemB))

        assertEquals(RenameDecision.RENAME, results[0].decision)
        assertEquals("bb.mkv", results[0].targetName)

        assertEquals(RenameDecision.RENAME, results[1].decision)
        assertEquals("bb.mkv", results[1].targetName)
    }
}
