package com.splitandmerge.mkvslice.domain.cleanup

import com.splitandmerge.mkvslice.data.repository.CleanupRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class TitleCleanerPatternTest {

    private lateinit var mockRepository: CleanupRepository
    private lateinit var titleCleaner: TitleCleaner

    @Before
    fun setUp() {
        mockRepository = mockk()
        every { mockRepository.observePatterns() } returns flowOf(DEFAULT_CLEANUP_PATTERNS)
        coEvery { mockRepository.getAllPatterns() } returns DEFAULT_CLEANUP_PATTERNS
        titleCleaner = TitleCleaner(mockRepository)
    }

    @Test
    fun cleans_baahubali_example() {
        val input = "Baahubali The Epic 2025 2160p NEWEB-DL DUAL DTS HD MA DDP5.1 H.265.mkv"
        val expected = "Baahubali The Epic (2025)"
        assertEquals(expected, titleCleaner.cleanTitle(input))
    }

    @Test
    fun cleans_kantara_url_prefix() {
        val input = "www.5MovieRulz.graphics - Kantara.Chapter.1.2024.4K.WEB-DL.x265.mkv"
        val expected = "Kantara Chapter 1 (2024)"
        assertEquals(expected, titleCleaner.cleanTitle(input))
    }

    @Test
    fun wraps_year_only() {
        val input = "Devara.2024.2160p.HDR.HEVC.Friday4KPopc.mkv"
        val expected = "Devara (2024) Friday4KPopc"
        assertEquals(expected, titleCleaner.cleanTitle(input))
    }

    @Test
    fun too_short_falls_back() {
        val input = "ab.mkv"
        val expected = "ab"
        assertEquals(expected, titleCleaner.cleanTitle(input))
    }
}
