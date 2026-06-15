package com.splitandmerge.mkvslice.ui.mergeconfig

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
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
class MergeConfigViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val jobDao = mockk<JobDao>(relaxed = true)
    private val titleCleaner = mockk<TitleCleaner>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val outputFolderValidator = mockk<OutputFolderValidator>()
    private val context = mockk<Context>(relaxed = true)
    private val contentResolver = mockk<ContentResolver>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { context.contentResolver } returns contentResolver
        every { titleCleaner.cleanTitle(any()) } returns "test_merged"
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

        val viewModel = MergeConfigViewModel(
            jobDao, context, titleCleaner, settingsRepository, outputFolderValidator
        )

        assertEquals("content://default_folder", viewModel.state.value.outputFolder)
    }

    @Test
    fun onFolderPicked_validUri_callsValidator_persistsToDataStore_andClearsDialog() = runTest {
        val folder = "content://valid_folder"
        val needed = 2500000000L // 1.0 GB + 1.5 GB
        every {
            outputFolderValidator.validate(folder, needed, assumePermissionPersisted = false)
        } returns OutputFolderValidation.Ok

        val cursor1 = mockk<Cursor>(relaxed = true)
        every { cursor1.moveToFirst() } returns true
        every { cursor1.getColumnIndex(OpenableColumns.SIZE) } returns 0
        every { cursor1.getLong(0) } returns 1000000000L
        every { contentResolver.query(Uri.parse("content://part1"), any(), any(), any(), any()) } returns cursor1

        val cursor2 = mockk<Cursor>(relaxed = true)
        every { cursor2.moveToFirst() } returns true
        every { cursor2.getColumnIndex(OpenableColumns.SIZE) } returns 0
        every { cursor2.getLong(0) } returns 1500000000L
        every { contentResolver.query(Uri.parse("content://part2"), any(), any(), any(), any()) } returns cursor2

        val viewModel = MergeConfigViewModel(
            jobDao, context, titleCleaner, settingsRepository, outputFolderValidator
        )
        viewModel.initMock("content://part1,content://part2")
        viewModel.updateOutputFolder(folder)

        coVerify { settingsRepository.setDefaultOutputFolderUri(folder) }
        assertNull(viewModel.validationResult.value)
        assertEquals(folder, viewModel.state.value.outputFolder)
    }

    @Test
    fun onFolderPicked_notReachable_setsValidationResult_doesNotPersist() = runTest {
        val folder = "content://invalid"
        val needed = 2500000000L // 1.0 GB + 1.5 GB
        every {
            outputFolderValidator.validate(folder, needed, assumePermissionPersisted = false)
        } returns OutputFolderValidation.NotReachable

        val cursor1 = mockk<Cursor>(relaxed = true)
        every { cursor1.moveToFirst() } returns true
        every { cursor1.getColumnIndex(OpenableColumns.SIZE) } returns 0
        every { cursor1.getLong(0) } returns 1000000000L
        every { contentResolver.query(Uri.parse("content://part1"), any(), any(), any(), any()) } returns cursor1

        val cursor2 = mockk<Cursor>(relaxed = true)
        every { cursor2.moveToFirst() } returns true
        every { cursor2.getColumnIndex(OpenableColumns.SIZE) } returns 0
        every { cursor2.getLong(0) } returns 1500000000L
        every { contentResolver.query(Uri.parse("content://part2"), any(), any(), any(), any()) } returns cursor2

        val viewModel = MergeConfigViewModel(
            jobDao, context, titleCleaner, settingsRepository, outputFolderValidator
        )
        viewModel.initMock("content://part1,content://part2")
        viewModel.updateOutputFolder(folder)

        coVerify(exactly = 0) { settingsRepository.setDefaultOutputFolderUri(any()) }
        assertEquals(OutputFolderValidation.NotReachable, viewModel.validationResult.value)
        assertEquals("", viewModel.state.value.outputFolder)
    }

    @Test
    fun onFolderPicked_insufficientSpace_setsValidationResult_doesNotPersist() = runTest {
        val folder = "content://low_space"
        val needed = 2500000000L // 1.0 GB + 1.5 GB
        val validation = OutputFolderValidation.InsufficientSpace(needed, 500L)
        every {
            outputFolderValidator.validate(folder, needed, assumePermissionPersisted = false)
        } returns validation

        val cursor1 = mockk<Cursor>(relaxed = true)
        every { cursor1.moveToFirst() } returns true
        every { cursor1.getColumnIndex(OpenableColumns.SIZE) } returns 0
        every { cursor1.getLong(0) } returns 1000000000L
        every { contentResolver.query(Uri.parse("content://part1"), any(), any(), any(), any()) } returns cursor1

        val cursor2 = mockk<Cursor>(relaxed = true)
        every { cursor2.moveToFirst() } returns true
        every { cursor2.getColumnIndex(OpenableColumns.SIZE) } returns 0
        every { cursor2.getLong(0) } returns 1500000000L
        every { contentResolver.query(Uri.parse("content://part2"), any(), any(), any(), any()) } returns cursor2

        val viewModel = MergeConfigViewModel(
            jobDao, context, titleCleaner, settingsRepository, outputFolderValidator
        )
        viewModel.initMock("content://part1,content://part2")
        viewModel.updateOutputFolder(folder)

        coVerify(exactly = 0) { settingsRepository.setDefaultOutputFolderUri(any()) }
        assertEquals(validation, viewModel.validationResult.value)
        assertEquals("", viewModel.state.value.outputFolder)
    }

    @Test
    fun onPickFolderAgain_clearsValidationResult() = runTest {
        val folder = "content://invalid"
        val needed = 0L
        every {
            outputFolderValidator.validate(folder, needed, assumePermissionPersisted = false)
        } returns OutputFolderValidation.NotReachable

        val viewModel = MergeConfigViewModel(
            jobDao, context, titleCleaner, settingsRepository, outputFolderValidator
        )
        viewModel.updateOutputFolder(folder)

        assertEquals(OutputFolderValidation.NotReachable, viewModel.validationResult.value)
        viewModel.onPickFolderAgain()
        assertNull(viewModel.validationResult.value)
    }
}
