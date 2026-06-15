package com.splitandmerge.mkvslice.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.splitandmerge.mkvslice.data.db.JobDao
import com.splitandmerge.mkvslice.data.db.entity.JobEntity
import com.splitandmerge.mkvslice.domain.model.JobStatus
import com.splitandmerge.mkvslice.domain.model.JobType
import com.splitandmerge.mkvslice.ui.library.LibraryScreen
import com.splitandmerge.mkvslice.ui.library.LibraryViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test

class LibraryScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val mockDao = mockk<JobDao>(relaxed = true)

    @Test
    fun testFailedJobShowsDetailSheetOnTap() {
        val failedJob = JobEntity(
            id = "failed-1",
            type = JobType.SPLIT,
            createdAt = 1000L,
            updatedAt = 2000L,
            status = JobStatus.FAILED,
            progressPct = 40,
            errorMessage = "Codec error: subtitle not supported",
            sourceUri = "content://source/1",
            outputDirUri = "content://out/1",
            outputBaseName = "TestFailedApp",
            outputContainer = ".mkv"
        )
        every { mockDao.observeAll() } returns flowOf(listOf(failedJob))
        every { mockDao.observeById("failed-1") } returns flowOf(failedJob)

        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val viewModel = LibraryViewModel(mockDao, context)

        composeTestRule.setContent {
            LibraryScreen(
                viewModel = viewModel,
                onNavigateToSettings = {},
                onStartSplitFlow = { _, _ -> },
                onStartMergeFlow = {},
                onNavigateToJobDetail = {},
                onNavigateToSplitResult = {},
                onNavigateToMergeResult = {}
            )
        }

        // Click on the failed job row
        composeTestRule.onNodeWithText("TestFailedApp").assertIsDisplayed()
        composeTestRule.onNodeWithText("TestFailedApp").performClick()

        // Verify that the JobDetailSheet bottom sheet is shown
        composeTestRule.onNodeWithText("Error Message").assertIsDisplayed()
        composeTestRule.onNodeWithText("Codec error: subtitle not supported").assertIsDisplayed()
    }
}
