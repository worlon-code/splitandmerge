package com.splitandmerge.mkvslice.ui

import com.splitandmerge.mkvslice.ui.library.LibraryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testMockJobsAreLoadedOnInit() {
        val viewModel = LibraryViewModel()
        val jobs = viewModel.jobs.value
        assertNotNull(jobs)
        assertEquals(3, jobs.size)
        assertEquals("1", jobs[0].id)
        assertEquals("Kantara Chapter 1 (2024)", jobs[0].outputBaseName)
        assertEquals("2", jobs[1].id)
        assertEquals("Bahubali (2025).merged", jobs[1].outputBaseName)
    }
}
