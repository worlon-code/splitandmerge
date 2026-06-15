package com.splitandmerge.mkvslice.ui.settings

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.splitandmerge.mkvslice.data.settings.SettingsRepository
import com.splitandmerge.mkvslice.data.settings.ThemeMode
import com.splitandmerge.mkvslice.domain.storage.OutputFolderValidation
import com.splitandmerge.mkvslice.domain.storage.OutputFolderValidator
import com.splitandmerge.mkvslice.data.update.UpdateRepository
import com.splitandmerge.mkvslice.data.update.UpdateState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
class SettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val updateRepository = mockk<UpdateRepository>(relaxed = true)
    private val outputFolderValidator = mockk<OutputFolderValidator>()
    private val context = mockk<Context>(relaxed = true)
    private val contentResolver = mockk<ContentResolver>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { context.contentResolver } returns contentResolver
        every { settingsRepository.settingsFlow } returns flowOf(
            com.splitandmerge.mkvslice.data.settings.SettingsState()
        )
        every { updateRepository.state } returns kotlinx.coroutines.flow.MutableStateFlow(UpdateState())
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
    fun onFolderPicked_validUri_callsValidator_persistsAndClearsDialog() = runTest {
        val folder = "content://valid_folder"
        val needed = 1024L * 1024L * 1024L
        every { outputFolderValidator.validate(folder, needed, assumePermissionPersisted = false) } returns OutputFolderValidation.Ok

        val viewModel = SettingsViewModel(settingsRepository, updateRepository, outputFolderValidator, context)
        viewModel.updateOutputFolder(folder)

        coVerify { settingsRepository.setDefaultOutputFolderUri(folder) }
        assertNull(viewModel.validationResult.value)
    }

    @Test
    fun onFolderPicked_notReachable_setsValidationResult_doesNotPersist() = runTest {
        val folder = "content://invalid"
        val needed = 1024L * 1024L * 1024L
        every { outputFolderValidator.validate(folder, needed, assumePermissionPersisted = false) } returns OutputFolderValidation.NotReachable

        val viewModel = SettingsViewModel(settingsRepository, updateRepository, outputFolderValidator, context)
        viewModel.updateOutputFolder(folder)

        coVerify(exactly = 0) { settingsRepository.setDefaultOutputFolderUri(any()) }
        assertEquals(OutputFolderValidation.NotReachable, viewModel.validationResult.value)
    }

    @Test
    fun onFolderPicked_insufficientSpace_setsValidationResult_doesNotPersist() = runTest {
        val folder = "content://low_space"
        val needed = 1024L * 1024L * 1024L
        val validation = OutputFolderValidation.InsufficientSpace(needed, 500L)
        every { outputFolderValidator.validate(folder, needed, assumePermissionPersisted = false) } returns validation

        val viewModel = SettingsViewModel(settingsRepository, updateRepository, outputFolderValidator, context)
        viewModel.updateOutputFolder(folder)

        coVerify(exactly = 0) { settingsRepository.setDefaultOutputFolderUri(any()) }
        assertEquals(validation, viewModel.validationResult.value)
    }

    @Test
    fun onPickFolderAgain_clearsValidationResult() = runTest {
        val folder = "content://invalid"
        val needed = 1024L * 1024L * 1024L
        every { outputFolderValidator.validate(folder, needed, assumePermissionPersisted = false) } returns OutputFolderValidation.NotReachable

        val viewModel = SettingsViewModel(settingsRepository, updateRepository, outputFolderValidator, context)
        viewModel.updateOutputFolder(folder)

        assertEquals(OutputFolderValidation.NotReachable, viewModel.validationResult.value)
        viewModel.onPickFolderAgain()
        assertNull(viewModel.validationResult.value)
    }
}
