package com.splitandmerge.mkvslice.ui.splitconfig

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.splitandmerge.mkvslice.data.db.JobDao
import com.splitandmerge.mkvslice.data.settings.SettingsRepository
import com.splitandmerge.mkvslice.data.settings.SettingsState
import com.splitandmerge.mkvslice.domain.cleanup.TitleCleaner
import com.splitandmerge.mkvslice.domain.storage.OutputFolderValidation
import com.splitandmerge.mkvslice.domain.storage.OutputFolderValidator
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SplitConfigViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val jobDao = mockk<JobDao>(relaxed = true)
    private val titleCleaner = mockk<TitleCleaner>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val outputFolderValidator = mockk<OutputFolderValidator>()
    private val context = mockk<Context>(relaxed = true)
    private val contentResolver = mockk<ContentResolver>(relaxed = true)

    private val savedStateHandle = SavedStateHandle(
        mapOf(
            "uri" to "content://video",
            "filename" to "test.mkv",
            "sizeBytes" to "2000000000",
            "durationSec" to "120.0"
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { context.contentResolver } returns contentResolver
        every { titleCleaner.cleanTitle(any()) } returns "test"
        every { settingsRepository.settingsFlow } returns flowOf(SettingsState())
        io.mockk.mockkStatic(Uri::class)
        val uriMap = mutableMapOf<String, Uri>()
        every { Uri.parse(any()) } answers {
            val str = firstArg<String>()
            uriMap.getOrPut(str) {
                val mockUri = mockk<Uri>(relaxed = true)
                every { mockUri.toString() } returns str
                mockUri
            }
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        io.mockk.unmockkAll()
    }

    @Test
    fun `init loads defaultOutputFolderUri from SettingsRepository`() = runTest {
        every { settingsRepository.settingsFlow } returns
                flowOf(SettingsState(defaultOutputFolderUri = "content://default_folder"))

        val viewModel = SplitConfigViewModel(
            savedStateHandle, jobDao, titleCleaner, settingsRepository, outputFolderValidator, context
        )

        assertEquals("content://default_folder", viewModel.state.value.outputFolder)
    }

    @Test
    fun onFolderPicked_validUri_callsValidator_persistsToDataStore_andClearsDialog() = runTest {
        val folder = "content://valid_folder"
        val needed = 2000000000L
        every {
            outputFolderValidator.validate(folder, needed, assumePermissionPersisted = false)
        } returns OutputFolderValidation.Ok

        val viewModel = SplitConfigViewModel(
            savedStateHandle, jobDao, titleCleaner, settingsRepository, outputFolderValidator, context
        )
        viewModel.updateOutputFolder(folder)

        coVerify { settingsRepository.setDefaultOutputFolderUri(folder) }
        assertNull(viewModel.validationResult.value)
        assertEquals(folder, viewModel.state.value.outputFolder)
    }

    @Test
    fun onFolderPicked_notReachable_setsValidationResult_doesNotPersist() = runTest {
        val folder = "content://invalid"
        val needed = 2000000000L
        every {
            outputFolderValidator.validate(folder, needed, assumePermissionPersisted = false)
        } returns OutputFolderValidation.NotReachable

        val viewModel = SplitConfigViewModel(
            savedStateHandle, jobDao, titleCleaner, settingsRepository, outputFolderValidator, context
        )
        viewModel.updateOutputFolder(folder)

        coVerify(exactly = 0) { settingsRepository.setDefaultOutputFolderUri(any()) }
        assertEquals(OutputFolderValidation.NotReachable, viewModel.validationResult.value)
        assertEquals("", viewModel.state.value.outputFolder)
    }

    @Test
    fun onFolderPicked_insufficientSpace_setsValidationResult_doesNotPersist() = runTest {
        val folder = "content://low_space"
        val needed = 2000000000L
        val validation = OutputFolderValidation.InsufficientSpace(needed, 500L)
        every {
            outputFolderValidator.validate(folder, needed, assumePermissionPersisted = false)
        } returns validation

        val viewModel = SplitConfigViewModel(
            savedStateHandle, jobDao, titleCleaner, settingsRepository, outputFolderValidator, context
        )
        viewModel.updateOutputFolder(folder)

        coVerify(exactly = 0) { settingsRepository.setDefaultOutputFolderUri(any()) }
        assertEquals(validation, viewModel.validationResult.value)
        assertEquals("", viewModel.state.value.outputFolder)
    }

    @Test
    fun onPickFolderAgain_clearsValidationResult() = runTest {
        val folder = "content://invalid"
        val needed = 2000000000L
        every {
            outputFolderValidator.validate(folder, needed, assumePermissionPersisted = false)
        } returns OutputFolderValidation.NotReachable

        val viewModel = SplitConfigViewModel(
            savedStateHandle, jobDao, titleCleaner, settingsRepository, outputFolderValidator, context
        )
        viewModel.updateOutputFolder(folder)

        assertEquals(OutputFolderValidation.NotReachable, viewModel.validationResult.value)
        viewModel.onPickFolderAgain()
        assertNull(viewModel.validationResult.value)
    }
}
