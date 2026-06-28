package com.splitandmerge.mkvslice.ui.splitconfig

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.splitandmerge.mkvslice.data.db.JobDao
import com.splitandmerge.mkvslice.data.settings.SettingsRepository
import com.splitandmerge.mkvslice.data.settings.SettingsState
import com.splitandmerge.mkvslice.data.db.entity.JobEntity
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

    @Test
    fun testDecimalByteSizeCapValidationAndConversion() = runTest {
        val capturedJobs = mutableListOf<JobEntity>()
        io.mockk.coEvery { jobDao.upsert(capture(capturedJobs)) } returns Unit

        val viewModel = SplitConfigViewModel(
            savedStateHandle, jobDao, titleCleaner, settingsRepository, outputFolderValidator, context
        )

        // 1. Check default state is valid (100 MB)
        viewModel.updateByteSplit(true)
        viewModel.updateMode(com.splitandmerge.mkvslice.domain.model.SplitMode.SIZE_CAP_ONLY)
        org.junit.Assert.assertTrue(viewModel.isConfigValid())
        assertNull(viewModel.getByteSizeCapError())

        // (a) "1.5" + GB -> targetCapBytes == 1610612736
        viewModel.updateByteSplitSizeUnit(SizeUnit.GB)
        viewModel.updateByteSizeCapInput("1.5")
        org.junit.Assert.assertTrue(viewModel.isConfigValid())
        assertNull(viewModel.getByteSizeCapError())
        viewModel.startSplitJob(context)
        assertEquals(1, capturedJobs.size)
        assertEquals(1610612736L, capturedJobs[0].targetCapBytes)

        // (b) "0.5" + MB -> targetCapBytes == 524288
        viewModel.updateByteSplitSizeUnit(SizeUnit.MB)
        viewModel.updateByteSizeCapInput("0.5")
        org.junit.Assert.assertTrue(viewModel.isConfigValid())
        assertNull(viewModel.getByteSizeCapError())
        viewModel.startSplitJob(context)
        assertEquals(2, capturedJobs.size)
        assertEquals(524288L, capturedJobs[1].targetCapBytes)

        // (c) FLOOR DISCRIMINATOR — "0.7" + GB -> targetCapBytes == 751619276
        viewModel.updateByteSplitSizeUnit(SizeUnit.GB)
        viewModel.updateByteSizeCapInput("0.7")
        org.junit.Assert.assertTrue(viewModel.isConfigValid())
        assertNull(viewModel.getByteSizeCapError())
        viewModel.startSplitJob(context)
        assertEquals(3, capturedJobs.size)
        assertEquals(751619276L, capturedJobs[2].targetCapBytes)

        // (d) "2." + GB -> targetCapBytes == 2147483648
        viewModel.updateByteSplitSizeUnit(SizeUnit.GB)
        viewModel.updateByteSizeCapInput("2.")
        org.junit.Assert.assertTrue(viewModel.isConfigValid())
        assertNull(viewModel.getByteSizeCapError())
        viewModel.startSplitJob(context)
        assertEquals(4, capturedJobs.size)
        assertEquals(2147483648L, capturedJobs[3].targetCapBytes)

        // (e) leading-dot ".5" + GB -> targetCapBytes == 536870912
        viewModel.updateByteSplitSizeUnit(SizeUnit.GB)
        viewModel.updateByteSizeCapInput(".5")
        org.junit.Assert.assertTrue(viewModel.isConfigValid())
        assertNull(viewModel.getByteSizeCapError())
        viewModel.startSplitJob(context)
        assertEquals(5, capturedJobs.size)
        assertEquals(536870912L, capturedJobs[4].targetCapBytes)

        // (f) PREVIEW recompute — for "0.3" + GB against source size of 944,162,109 bytes:
        // totalSize = 944,162,109 bytes. 0.3 GB = 322,122,547 bytes.
        // 944,162,109 / 322,122,547 = 2.93 -> CEIL = 3 parts.
        val customSavedState = SavedStateHandle(
            mapOf(
                "uri" to "content://video",
                "filename" to "test.mkv",
                "sizeBytes" to "944162109",
                "durationSec" to "120.0"
            )
        )
        val vm2 = SplitConfigViewModel(
            customSavedState, jobDao, titleCleaner, settingsRepository, outputFolderValidator, context
        )
        vm2.updateByteSplit(true)
        vm2.updateMode(com.splitandmerge.mkvslice.domain.model.SplitMode.SIZE_CAP_ONLY)
        vm2.updateByteSplitSizeUnit(SizeUnit.GB)
        vm2.updateByteSizeCapInput("0.3")
        assertEquals(3, vm2.state.value.predictedPartCount)

        // (g) FLOOR-TO-ZERO — "0.0000001" + MB (floors to 0) -> action DISABLED / invalid, not committed
        viewModel.updateByteSplitSizeUnit(SizeUnit.MB)
        viewModel.updateByteSizeCapInput("0.0000001")
        org.junit.Assert.assertFalse(viewModel.isConfigValid())
        assertEquals("Size must be greater than 0", viewModel.getByteSizeCapError())
        viewModel.startSplitJob(context)
        assertEquals(5, capturedJobs.size) // still 5, not committed

        // (h) OVERFLOW — "99999999999" + GB -> action DISABLED / invalid, not committed
        viewModel.updateByteSplitSizeUnit(SizeUnit.GB)
        viewModel.updateByteSizeCapInput("99999999999")
        org.junit.Assert.assertFalse(viewModel.isConfigValid())
        assertEquals("Value is too large (overflows)", viewModel.getByteSizeCapError())
        viewModel.startSplitJob(context)
        assertEquals(5, capturedJobs.size) // still 5

        // (i) BY-PARTS UNAFFECTED: count field still parses as Int, dot makes it invalid/0
        viewModel.updateByteSplit(true)
        viewModel.updateMode(com.splitandmerge.mkvslice.domain.model.SplitMode.EXACT_PARTS)
        viewModel.updatePartsCount(3)
        org.junit.Assert.assertTrue(viewModel.isConfigValid())
        val partsVal = "3.5".toIntOrNull() ?: 0
        viewModel.updatePartsCount(partsVal)
        org.junit.Assert.assertFalse(viewModel.isConfigValid())

        // Reset mode for invalid checks
        viewModel.updateMode(com.splitandmerge.mkvslice.domain.model.SplitMode.SIZE_CAP_ONLY)

        // (j) INVALID INPUTS — assert EACH of these INDIVIDUALLY: "", ".", "abc", "1.2.3", "1e3", "+1.5", "0", "-1"
        // ""
        viewModel.updateByteSizeCapInput("")
        org.junit.Assert.assertFalse(viewModel.isConfigValid())
        assertEquals("Size cap cannot be empty", viewModel.getByteSizeCapError())

        // "."
        viewModel.updateByteSizeCapInput(".")
        org.junit.Assert.assertFalse(viewModel.isConfigValid())
        assertEquals("Please enter digits", viewModel.getByteSizeCapError())

        // "abc"
        viewModel.updateByteSizeCapInput("abc")
        org.junit.Assert.assertFalse(viewModel.isConfigValid())
        assertEquals("Please enter a valid decimal number", viewModel.getByteSizeCapError())

        // "1.2.3"
        viewModel.updateByteSizeCapInput("1.2.3")
        org.junit.Assert.assertFalse(viewModel.isConfigValid())
        assertEquals("Please enter a valid decimal number", viewModel.getByteSizeCapError())

        // "1e3"
        viewModel.updateByteSizeCapInput("1e3")
        org.junit.Assert.assertFalse(viewModel.isConfigValid())
        assertEquals("Scientific notation is not allowed", viewModel.getByteSizeCapError())

        // "+1.5"
        viewModel.updateByteSizeCapInput("+1.5")
        org.junit.Assert.assertFalse(viewModel.isConfigValid())
        assertEquals("Signs (+ or -) are not allowed", viewModel.getByteSizeCapError())

        // "0"
        viewModel.updateByteSizeCapInput("0")
        org.junit.Assert.assertFalse(viewModel.isConfigValid())
        assertEquals("Size must be greater than 0", viewModel.getByteSizeCapError())

        // "-1"
        viewModel.updateByteSizeCapInput("-1")
        org.junit.Assert.assertFalse(viewModel.isConfigValid())
        assertEquals("Signs (+ or -) are not allowed", viewModel.getByteSizeCapError())
    }
}
