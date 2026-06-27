package com.splitandmerge.mkvslice.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.isRoot
import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import com.splitandmerge.mkvslice.data.db.JobDao
import com.splitandmerge.mkvslice.data.db.entity.JobEntity
import com.splitandmerge.mkvslice.domain.model.JobType
import com.splitandmerge.mkvslice.domain.model.JobStatus
import com.splitandmerge.mkvslice.data.settings.SettingsRepository
import com.splitandmerge.mkvslice.domain.cleanup.TitleCleaner
import com.splitandmerge.mkvslice.domain.progress.JobProgressTracker
import com.splitandmerge.mkvslice.domain.storage.OutputFolderValidator
import com.splitandmerge.mkvslice.engine.FfprobeEngine
import com.splitandmerge.mkvslice.data.update.UpdateRepository
import com.splitandmerge.mkvslice.data.update.UpdateState
import com.splitandmerge.mkvslice.ui.cleanup.CleanupPatternsScreen
import com.splitandmerge.mkvslice.ui.cleanup.CleanupPatternsScreenTablet
import com.splitandmerge.mkvslice.ui.cleanup.CleanupPatternsViewModel
import com.splitandmerge.mkvslice.ui.cleanup.CleanupState
import com.splitandmerge.mkvslice.ui.dialogs.CleanupPreviewSheet
import com.splitandmerge.mkvslice.ui.dialogs.ContainerPromotionSheet
import com.splitandmerge.mkvslice.ui.dialogs.FolderCollisionSheet
import com.splitandmerge.mkvslice.ui.filedetails.FileDetailsScreen
import com.splitandmerge.mkvslice.ui.filedetails.FileDetailsViewModel
import com.splitandmerge.mkvslice.ui.library.LibraryScreen
import com.splitandmerge.mkvslice.ui.library.LibraryScreenTablet
import com.splitandmerge.mkvslice.ui.library.LibraryViewModel
import com.splitandmerge.mkvslice.ui.mergeconfig.MergeConfigScreen
import com.splitandmerge.mkvslice.ui.mergeconfig.MergeConfigViewModel
import com.splitandmerge.mkvslice.ui.mergeorder.MergeOrderScreen
import com.splitandmerge.mkvslice.ui.mergeorder.MergeOrderViewModel
import com.splitandmerge.mkvslice.domain.merger.PartModeDetector
import com.splitandmerge.mkvslice.domain.merger.PreFlightEvaluator
import com.splitandmerge.mkvslice.ui.onboarding.OnboardingScreen
import com.splitandmerge.mkvslice.ui.oss.OssNoticesScreen
import com.splitandmerge.mkvslice.ui.progress.JobProgressScreen
import com.splitandmerge.mkvslice.ui.progress.JobProgressViewModel
import com.splitandmerge.mkvslice.ui.result.MergeResultScreen
import com.splitandmerge.mkvslice.ui.result.MergeResultViewModel
import com.splitandmerge.mkvslice.ui.result.SplitResultScreen
import com.splitandmerge.mkvslice.ui.settings.SettingsScreen
import com.splitandmerge.mkvslice.ui.settings.SettingsViewModel
import com.splitandmerge.mkvslice.ui.splitconfig.SplitConfigScreen
import com.splitandmerge.mkvslice.ui.splitconfig.SplitConfigScreenTablet
import com.splitandmerge.mkvslice.ui.splitconfig.SplitConfigViewModel
import com.splitandmerge.mkvslice.ui.splitconfirm.SplitConfirmScreen
import com.splitandmerge.mkvslice.ui.defaulttracks.DefaultTracksFlowScreen
import com.splitandmerge.mkvslice.ui.defaulttracks.DefaultTracksViewModel
import com.splitandmerge.mkvslice.ui.defaulttracks.DefaultTracksUiState
import com.splitandmerge.mkvslice.ui.defaulttracks.FileRowState
import com.splitandmerge.mkvslice.ui.defaulttracks.TrackDraft
import com.splitandmerge.mkvslice.ui.help.HelpScreen
import com.splitandmerge.mkvslice.domain.defaulttracks.model.TrackInfo
import com.splitandmerge.mkvslice.domain.defaulttracks.AudioChoice
import com.splitandmerge.mkvslice.domain.defaulttracks.SubChoice
import com.splitandmerge.mkvslice.ui.rename.RenameVideosScreen
import com.splitandmerge.mkvslice.ui.rename.RenameVideosViewModel
import com.splitandmerge.mkvslice.ui.rename.RenameVideosUiState
import com.splitandmerge.mkvslice.ui.rename.RenameFileRowState
import com.splitandmerge.mkvslice.ui.rename.InlineCreateState
import com.splitandmerge.mkvslice.domain.rename.RenameDecision
import com.splitandmerge.mkvslice.domain.rename.RenameBatchResult
import com.splitandmerge.mkvslice.domain.rename.RenameResult
import com.splitandmerge.mkvslice.domain.rename.RenameStatus
import com.splitandmerge.mkvslice.data.db.entity.CleanupPatternEntity
import com.splitandmerge.mkvslice.theme.VideoSplitterTheme
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

class ScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val mockContext = InstrumentationRegistry.getInstrumentation().targetContext
    private val mockSavedStateHandle = SavedStateHandle(mapOf("jobId" to "test-job-id"))
    private val mockJobDao = mockk<JobDao>(relaxed = true)
    private val mockTitleCleaner = mockk<TitleCleaner>(relaxed = true)
    private val mockSettingsRepository = mockk<SettingsRepository>(relaxed = true).apply {
        every { settingsFlow } returns kotlinx.coroutines.flow.flowOf(com.splitandmerge.mkvslice.data.settings.SettingsState())
    }
    private val mockOutputFolderValidator = mockk<OutputFolderValidator>(relaxed = true)
    private val mockJobProgressTracker = mockk<JobProgressTracker>(relaxed = true)
    private val mockFfprobeEngine = mockk<FfprobeEngine>(relaxed = true)
    private val mockMergeValidator = mockk<com.splitandmerge.mkvslice.domain.merger.MergeValidator>(relaxed = true)
    private val mockUpdateRepository = mockk<UpdateRepository>(relaxed = true).apply {
        every { state } returns kotlinx.coroutines.flow.MutableStateFlow(com.splitandmerge.mkvslice.data.update.UpdateState.Idle)
    }
    private val mockCleanupRepository = mockk<com.splitandmerge.mkvslice.data.repository.CleanupRepository>(relaxed = true)
    private val mockPartModeDetector = mockk<PartModeDetector>(relaxed = true)
    private val mockPreFlightEvaluator = mockk<PreFlightEvaluator>(relaxed = true)
    private val mockDefaultTrackFileResultDao = mockk<com.splitandmerge.mkvslice.data.db.DefaultTrackFileResultDao>(relaxed = true)
    private val mockFileSystem = mockk<com.splitandmerge.mkvslice.platform.io.FileSystem>(relaxed = true)

    private val mockLibraryViewModel by lazy {
        val dummyJobs = listOf(
            JobEntity(
                id = "job-1",
                type = JobType.SPLIT,
                createdAt = System.currentTimeMillis() - 3600000L,
                updatedAt = System.currentTimeMillis() - 3500000L,
                status = JobStatus.DONE,
                progressPct = 100,
                sourceUri = "content://media/1",
                outputDirUri = "content://media/2",
                outputBaseName = "Sample Movie One (2024)",
                outputContainer = ".mkv"
            ),
            JobEntity(
                id = "job-2",
                type = JobType.MERGE,
                createdAt = System.currentTimeMillis() - 1800000L,
                updatedAt = System.currentTimeMillis(),
                status = JobStatus.RUNNING,
                progressPct = 45,
                sourceUri = "",
                outputDirUri = "content://media/2",
                outputBaseName = "Sample Show Two (2025)",
                outputContainer = ".mkv"
            )
        )
        every { mockJobDao.observeAll() } returns flowOf(dummyJobs)
        LibraryViewModel(mockJobDao, mockDefaultTrackFileResultDao, mockFileSystem, mockContext)
    }
    
    private val mockFileDetailsViewModel = FileDetailsViewModel(mockFfprobeEngine)
    private val mockSplitConfigViewModel = SplitConfigViewModel(
        savedStateHandle = mockSavedStateHandle,
        jobDao = mockJobDao,
        titleCleaner = mockTitleCleaner,
        settingsRepository = mockSettingsRepository,
        outputFolderValidator = mockOutputFolderValidator,
        context = mockContext
    )
    private val mockJobProgressViewModel = JobProgressViewModel(
        savedStateHandle = mockSavedStateHandle,
        jobDao = mockJobDao,
        jobProgressTracker = mockJobProgressTracker,
        context = mockContext
    )
    private val mockMergeOrderViewModel = MergeOrderViewModel(
        context = mockContext,
        ffprobeEngine = mockFfprobeEngine,
        mergeValidator = mockMergeValidator,
        partModeDetector = mockPartModeDetector,
        preFlightEvaluator = mockPreFlightEvaluator
    )
    private val mockMergeConfigViewModel = MergeConfigViewModel(
        jobDao = mockJobDao,
        context = mockContext,
        titleCleaner = mockTitleCleaner,
        settingsRepository = mockSettingsRepository,
        outputFolderValidator = mockOutputFolderValidator
    )
    private val mockMergeResultViewModel = MergeResultViewModel(
        savedStateHandle = mockSavedStateHandle,
        jobDao = mockJobDao,
        ffprobeEngine = mockFfprobeEngine,
        context = mockContext
    )
    private val mockSettingsViewModel = SettingsViewModel(
        settingsRepository = mockSettingsRepository,
        updateRepository = mockUpdateRepository,
        outputFolderValidator = mockOutputFolderValidator,
        context = mockContext
    )
    private val mockCleanupPatternsViewModel = CleanupPatternsViewModel(
        cleanupRepository = mockCleanupRepository,
        json = kotlinx.serialization.json.Json,
        ioDispatcher = kotlinx.coroutines.Dispatchers.Main
    )

    private fun createMockRenameViewModel(
        uiState: RenameVideosUiState,
        filesList: List<RenameFileRowState> = emptyList(),
        perRowAutoSuffix: Set<String> = emptySet(),
        cleanupPatterns: List<CleanupPatternEntity> = emptyList(),
        selectedPatternIds: Set<String> = emptySet(),
        inlineCreateState: InlineCreateState = InlineCreateState(),
        keepScreenOn: Boolean = false
    ): RenameVideosViewModel {
        val vm = mockk<RenameVideosViewModel>(relaxed = true)
        every { vm.uiState } returns MutableStateFlow(uiState)
        every { vm.filesList } returns MutableStateFlow(filesList)
        every { vm.perRowAutoSuffix } returns MutableStateFlow(perRowAutoSuffix)
        every { vm.cleanupPatterns } returns flowOf(cleanupPatterns)
        every { vm.selectedPatternIds } returns MutableStateFlow(selectedPatternIds)
        every { vm.inlineCreateState } returns MutableStateFlow(inlineCreateState)
        every { vm.keepScreenOn } returns MutableStateFlow(keepScreenOn)
        return vm
    }

    private fun createMockDefaultTracksViewModel(
        uiState: DefaultTracksUiState,
        filesList: List<FileRowState> = emptyList(),
        drafts: Map<String, TrackDraft> = emptyMap(),
        lastEditedUri: String? = null,
        applyHint: String? = null
    ): DefaultTracksViewModel {
        val vm = mockk<DefaultTracksViewModel>(relaxed = true)
        every { vm.uiState } returns MutableStateFlow(uiState)
        every { vm.filesList } returns MutableStateFlow(filesList)
        every { vm.drafts } returns MutableStateFlow(drafts)
        every { vm.lastEditedUri } returns MutableStateFlow(lastEditedUri)
        every { vm.applyHint } returns MutableStateFlow(applyHint)
        return vm
    }

    private fun saveScreenshot(name: String) {
        // Wait for Compose idle before capturing
        composeTestRule.waitForIdle()
        
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = context.getExternalFilesDir("screenshots") ?: return
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "$name.png")
        
        val nodeToCapture = try {
            val roots = composeTestRule.onAllNodes(isRoot())
            val count = roots.fetchSemanticsNodes().size
            if (count > 1) {
                roots[count - 1]
            } else {
                composeTestRule.onRoot()
            }
        } catch (e: Throwable) {
            composeTestRule.onRoot()
        }
        
        val imageBitmap = nodeToCapture.captureToImage()
        val bitmap = imageBitmap.asAndroidBitmap()
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    @Test
    fun captureOnboardingScreen() {
        composeTestRule.setContent {
            VideoSplitterTheme(darkTheme = false) {
                OnboardingScreen(onFinished = {})
            }
        }
        saveScreenshot("S1_onboarding")
    }

    @Test
    fun captureLibraryScreenPhone() {
        composeTestRule.setContent {
            val config = LocalConfiguration.current
            val overrideConfig = Configuration(config).apply { screenWidthDp = 360 }
            CompositionLocalProvider(LocalConfiguration provides overrideConfig) {
                VideoSplitterTheme(darkTheme = false) {
                    LibraryScreen(
                        viewModel = mockLibraryViewModel,
                        onNavigateToSettings = {},
                        onStartSplitFlow = { _, _ -> },
                        onStartMergeFlow = {},
                        onStartDefaultTracksFlow = {},
                        onNavigateToJobDetail = {},
                        onNavigateToSplitResult = {},
                        onNavigateToMergeResult = {}
                    )
                }
            }
        }
        saveScreenshot("S2_library_phone")
    }

    @Test
    fun captureSplitConfigScreenPhone() {
        composeTestRule.setContent {
            val config = LocalConfiguration.current
            val overrideConfig = Configuration(config).apply { screenWidthDp = 360 }
            CompositionLocalProvider(LocalConfiguration provides overrideConfig) {
                VideoSplitterTheme(darkTheme = false) {
                    SplitConfigScreen(
                        viewModel = mockSplitConfigViewModel,
                        onBack = {},
                        onConfirm = {}
                    )
                }
            }
        }
        saveScreenshot("S5_split_config_phone")
    }

    @Test
    fun captureMergeOrderScreen() {
        composeTestRule.setContent {
            VideoSplitterTheme(darkTheme = false) {
                MergeOrderScreen(
                    viewModel = mockMergeOrderViewModel,
                    onBack = {},
                    onContinue = {}
                )
            }
        }
        saveScreenshot("S10_merge_order")
    }

    @Test
    fun captureSettingsScreen() {
        composeTestRule.setContent {
            VideoSplitterTheme(darkTheme = false) {
                SettingsScreen(
                    viewModel = mockSettingsViewModel,
                    onBack = {},
                    onNavigateToCleanupPatterns = {},
                    onNavigateToOssNotices = {},
                    onNavigateToLogs = {},
                    onNavigateToRenameVideos = {},
                    onNavigateToHelp = {}
                )
            }
        }
        saveScreenshot("S15_settings")
    }

    @Test
    fun captureCleanupPatternsScreenPhone() {
        val mockCleanupVM = mockk<CleanupPatternsViewModel>(relaxed = true)
        every { mockCleanupVM.state } returns MutableStateFlow(CleanupState(
            patterns = listOf(
                CleanupPatternEntity(id = "1", label = "Site Domain Cleanup", regex = "www\\.[a-zA-Z0-9-]+\\.[a-z]{2,}", replacement = "", enabled = true, isBuiltIn = true, orderIndex = 1, createdAt = 0L),
                CleanupPatternEntity(id = "2", label = "Quality Tag Removal", regex = "1080p|720p|2160p|4K", replacement = "", enabled = true, isBuiltIn = true, orderIndex = 2, createdAt = 0L),
                CleanupPatternEntity(id = "3", label = "Release Group Tags", regex = "-[a-zA-Z0-9]+$", replacement = "", enabled = true, isBuiltIn = true, orderIndex = 3, createdAt = 0L),
                CleanupPatternEntity(id = "4", label = "Show Season Pattern", regex = "S([0-9]+)E([0-9]+)", replacement = "S$1E$2", enabled = true, isBuiltIn = false, orderIndex = 4, createdAt = 0L)
            ),
            sampleInput = "www.example-site.com - Sample Movie One 2024 1080p WEB-DL x265-GROUP.mkv",
            sampleOutput = "Sample Movie One (2024)"
        ))
        
        composeTestRule.setContent {
            val config = LocalConfiguration.current
            val overrideConfig = Configuration(config).apply { screenWidthDp = 360 }
            CompositionLocalProvider(LocalConfiguration provides overrideConfig) {
                VideoSplitterTheme(darkTheme = false) {
                    CleanupPatternsScreen(
                        viewModel = mockCleanupVM,
                        onBack = {}
                    )
                }
            }
        }
        saveScreenshot("S15a_cleanup_patterns_phone")
    }

    @Test
    fun captureSetDefaultTracksList() {
        val dummyTracksFiles = listOf(
            FileRowState(
                uri = "content://media/1",
                displayName = "Sample Movie One (2024).mkv",
                sizeBytes = 1024L * 1024L * 800L,
                isMkv = true,
                isChecked = true,
                status = "WILL_CHANGE",
                reason = "",
                audioTracks = listOf(
                    TrackInfo(
                        trackNumber = 1L,
                        trackType = 2,
                        language = "eng",
                        flagDefault = 1,
                        flagForced = 0,
                        name = "Main Audio",
                        codec = "A_AAC",
                        byteOffset = 0L,
                        flagDefaultOffset = null,
                        flagForcedOffset = null,
                        trackEntryEnd = 100L,
                        voidDonors = emptyList()
                    )
                ),
                subtitleTracks = listOf(
                    TrackInfo(
                        trackNumber = 2L,
                        trackType = 17,
                        language = "eng",
                        flagDefault = 0,
                        flagForced = 0,
                        name = "English Subs",
                        codec = "S_TEXT/UTF8",
                        byteOffset = 0L,
                        flagDefaultOffset = null,
                        flagForcedOffset = null,
                        trackEntryEnd = 100L,
                        voidDonors = emptyList()
                    ),
                    TrackInfo(
                        trackNumber = 3L,
                        trackType = 17,
                        language = "fre",
                        flagDefault = 0,
                        flagForced = 0,
                        name = "French Subs",
                        codec = "S_TEXT/UTF8",
                        byteOffset = 0L,
                        flagDefaultOffset = null,
                        flagForcedOffset = null,
                        trackEntryEnd = 100L,
                        voidDonors = emptyList()
                    )
                )
            ),
            FileRowState(
                uri = "content://media/2",
                displayName = "Sample Clip Three (2026).mkv",
                sizeBytes = 1024L * 1024L * 50L,
                isMkv = true,
                isChecked = true,
                status = "UNCHANGED",
                reason = "",
                audioTracks = listOf(
                    TrackInfo(
                        trackNumber = 1L,
                        trackType = 2,
                        language = "und",
                        flagDefault = 1,
                        flagForced = 0,
                        name = null,
                        codec = "A_AAC",
                        byteOffset = 0L,
                        flagDefaultOffset = null,
                        flagForcedOffset = null,
                        trackEntryEnd = 100L,
                        voidDonors = emptyList()
                    )
                ),
                subtitleTracks = emptyList()
            )
        )
        val mockDefaultTracksVM = createMockDefaultTracksViewModel(
            uiState = DefaultTracksUiState.ReadyList(),
            filesList = dummyTracksFiles,
            lastEditedUri = "content://media/1"
        )

        composeTestRule.setContent {
            val config = LocalConfiguration.current
            val overrideConfig = Configuration(config).apply { screenWidthDp = 360 }
            CompositionLocalProvider(LocalConfiguration provides overrideConfig) {
                VideoSplitterTheme(darkTheme = false) {
                    DefaultTracksFlowScreen(
                        viewModel = mockDefaultTracksVM,
                        onBack = {}
                    )
                }
            }
        }
        saveScreenshot("S12_set_default_tracks_list")
    }

    @Test
    fun captureSetDefaultTracksEditor() {
        val dummyTracksFiles = listOf(
            FileRowState(
                uri = "content://media/1",
                displayName = "Sample Movie One (2024).mkv",
                sizeBytes = 1024L * 1024L * 800L,
                isMkv = true,
                isChecked = true,
                status = "WILL_CHANGE",
                reason = "",
                audioTracks = listOf(
                    TrackInfo(
                        trackNumber = 1L,
                        trackType = 2,
                        language = "eng",
                        flagDefault = 1,
                        flagForced = 0,
                        name = "English",
                        codec = "A_AC3",
                        byteOffset = 0L,
                        flagDefaultOffset = null,
                        flagForcedOffset = null,
                        trackEntryEnd = 100L,
                        voidDonors = emptyList()
                    ),
                    TrackInfo(
                        trackNumber = 2L,
                        trackType = 2,
                        language = "fre",
                        flagDefault = 0,
                        flagForced = 0,
                        name = "French",
                        codec = "A_AAC",
                        byteOffset = 0L,
                        flagDefaultOffset = null,
                        flagForcedOffset = null,
                        trackEntryEnd = 100L,
                        voidDonors = emptyList()
                    )
                ),
                subtitleTracks = listOf(
                    TrackInfo(
                        trackNumber = 3L,
                        trackType = 17,
                        language = "eng",
                        flagDefault = 0,
                        flagForced = 0,
                        name = "English SDH",
                        codec = "S_TEXT/UTF8",
                        byteOffset = 0L,
                        flagDefaultOffset = null,
                        flagForcedOffset = null,
                        trackEntryEnd = 100L,
                        voidDonors = emptyList()
                    ),
                    TrackInfo(
                        trackNumber = 4L,
                        trackType = 17,
                        language = "fre",
                        flagDefault = 0,
                        flagForced = 0,
                        name = "French Subs",
                        codec = "S_TEXT/UTF8",
                        byteOffset = 0L,
                        flagDefaultOffset = null,
                        flagForcedOffset = null,
                        trackEntryEnd = 100L,
                        voidDonors = emptyList()
                    )
                )
            )
        )
        val mockDefaultTracksVM = createMockDefaultTracksViewModel(
            uiState = DefaultTracksUiState.ReadyList(editingFileUri = "content://media/1"),
            filesList = dummyTracksFiles
        )

        composeTestRule.setContent {
            val config = LocalConfiguration.current
            val overrideConfig = Configuration(config).apply { screenWidthDp = 360 }
            CompositionLocalProvider(LocalConfiguration provides overrideConfig) {
                VideoSplitterTheme(darkTheme = false) {
                    DefaultTracksFlowScreen(
                        viewModel = mockDefaultTracksVM,
                        onBack = {}
                    )
                }
            }
        }
        saveScreenshot("S12_set_default_tracks_editor")
    }

    @Test
    fun captureRenameSourcePicker() {
        val mockRenameVM = createMockRenameViewModel(
            uiState = RenameVideosUiState.Idle
        )

        composeTestRule.setContent {
            val config = LocalConfiguration.current
            val overrideConfig = Configuration(config).apply { screenWidthDp = 360 }
            CompositionLocalProvider(LocalConfiguration provides overrideConfig) {
                VideoSplitterTheme(darkTheme = false) {
                    RenameVideosScreen(
                        viewModel = mockRenameVM,
                        onBack = {}
                    )
                }
            }
        }
        saveScreenshot("S14_rename_source_picker")
    }

    @Test
    fun captureRenamePreview() {
        val dummyRenameFiles = listOf(
            RenameFileRowState(
                id = "row-1",
                uri = "content://media/1",
                displayName = "www.example-site.com - Sample Movie One 2024 1080p WEB-DL x265-GROUP.mkv",
                sizeBytes = 1024L * 1024L * 500L,
                parentKey = "parent-1",
                parentKnown = true,
                supportsRename = true,
                existingNamesInParent = emptyList(),
                isChecked = true,
                originalBaseName = "www.example-site.com - Sample Movie One 2024 1080p WEB-DL x265-GROUP",
                extension = ".mkv",
                newBaseName = "Sample Movie One (2024)",
                decision = RenameDecision.RENAME,
                targetName = "Sample Movie One (2024).mkv",
                isPickedFile = false
            ),
            RenameFileRowState(
                id = "row-2",
                uri = "content://media/2",
                displayName = "Sample.Show.Two.2025.2160p.DUAL.DDP5.1.H265-RLSGRP.mkv",
                sizeBytes = 1024L * 1024L * 1024L * 8L,
                parentKey = "parent-1",
                parentKnown = true,
                supportsRename = true,
                existingNamesInParent = listOf("Sample Show Two (2025).mkv"),
                isChecked = true,
                originalBaseName = "Sample.Show.Two.2025.2160p.DUAL.DDP5.1.H265-RLSGRP",
                extension = ".mkv",
                newBaseName = "Sample Show Two (2025)",
                decision = RenameDecision.SKIP_COLLISION,
                targetName = "Sample Show Two (2025).mkv",
                isPickedFile = false
            ),
            RenameFileRowState(
                id = "row-3",
                uri = "content://media/3",
                displayName = "Sample Clip Three (2026).mkv",
                sizeBytes = 1024L * 1024L * 1024L * 2L,
                parentKey = "parent-1",
                parentKnown = true,
                supportsRename = true,
                existingNamesInParent = emptyList(),
                isChecked = false,
                originalBaseName = "Sample Clip Three (2026)",
                extension = ".mkv",
                newBaseName = "Sample Clip Three (2026)",
                decision = RenameDecision.NO_CHANGE,
                targetName = "Sample Clip Three (2026).mkv",
                isPickedFile = false
            )
        )
        val mockRenameVM = createMockRenameViewModel(
            uiState = RenameVideosUiState.ReadyList(),
            filesList = dummyRenameFiles
        )

        composeTestRule.setContent {
            val config = LocalConfiguration.current
            val overrideConfig = Configuration(config).apply { screenWidthDp = 360 }
            CompositionLocalProvider(LocalConfiguration provides overrideConfig) {
                VideoSplitterTheme(darkTheme = false) {
                    RenameVideosScreen(
                        viewModel = mockRenameVM,
                        onBack = {}
                    )
                }
            }
        }
        saveScreenshot("S14_rename_preview")
    }

    @Test
    fun captureRenameResults() {
        val batchResult = RenameBatchResult(
            results = listOf(
                RenameResult("content://media/1", "www.example-site.com - Sample Movie One 2024 1080p WEB-DL x265-GROUP.mkv", "Sample Movie One (2024).mkv", RenameStatus.Success),
                RenameResult("content://media/2", "Sample.Show.Two.2025.2160p.DUAL.DDP5.1.H265-RLSGRP.mkv", "Sample Show Two (2025).mkv", RenameStatus.Skipped("File already exists")),
                RenameResult("content://media/3", "Sample Clip Three (2026).mkv", "Sample Clip Three (2026).mkv", RenameStatus.Excluded("Unchecked"))
            ),
            successCount = 1,
            failedCount = 0,
            skippedCount = 1,
            excludedCount = 1
        )
        val mockRenameVM = createMockRenameViewModel(
            uiState = RenameVideosUiState.Results(batchResult)
        )

        composeTestRule.setContent {
            val config = LocalConfiguration.current
            val overrideConfig = Configuration(config).apply { screenWidthDp = 360 }
            CompositionLocalProvider(LocalConfiguration provides overrideConfig) {
                VideoSplitterTheme(darkTheme = false) {
                    RenameVideosScreen(
                        viewModel = mockRenameVM,
                        onBack = {}
                    )
                }
            }
        }
        saveScreenshot("S14_rename_results")
    }

    @Test
    fun captureSettingsUpdateAvailable() {
        val mockUpdateRepo = mockk<UpdateRepository>(relaxed = true).apply {
            every { state } returns kotlinx.coroutines.flow.MutableStateFlow(
                UpdateState.Available(
                    versionCode = 15,
                    versionName = "0.0.14",
                    changelog = listOf(
                        "Title-Clean Rename (folder + multi-file pick)",
                        "Cleanup Patterns backup & restore + optional Replace-with",
                        "AMOLED theme mode support",
                        "Keep-screen-on during splits and renames"
                    ),
                    url = "https://example.com/app-release.apk",
                    sha256 = "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef",
                    size = 15L * 1024L * 1024L
                )
            )
        }
        val mockSettingsVM = SettingsViewModel(
            settingsRepository = mockSettingsRepository,
            updateRepository = mockUpdateRepo,
            outputFolderValidator = mockOutputFolderValidator,
            context = mockContext
        )

        composeTestRule.setContent {
            val config = LocalConfiguration.current
            val overrideConfig = Configuration(config).apply { screenWidthDp = 360 }
            CompositionLocalProvider(LocalConfiguration provides overrideConfig) {
                VideoSplitterTheme(darkTheme = false) {
                    SettingsScreen(
                        viewModel = mockSettingsVM,
                        onBack = {},
                        onNavigateToCleanupPatterns = {},
                        onNavigateToOssNotices = {},
                        onNavigateToLogs = {},
                        onNavigateToRenameVideos = {},
                        onNavigateToHelp = {}
                    )
                }
            }
        }
        saveScreenshot("S17_update_available")
    }

    @Test
    fun captureHelpScreen() {
        composeTestRule.setContent {
            VideoSplitterTheme(darkTheme = false) {
                HelpScreen(onBack = {})
            }
        }
        saveScreenshot("S18_help")
    }
}
