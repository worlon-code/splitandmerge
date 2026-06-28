package com.splitandmerge.mkvslice.ui.defaulttracks

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.splitandmerge.mkvslice.domain.defaulttracks.model.EditSpec
import com.splitandmerge.mkvslice.domain.defaulttracks.model.TrackInfo
import com.splitandmerge.mkvslice.domain.defaulttracks.AudioChoice
import com.splitandmerge.mkvslice.domain.defaulttracks.SubChoice
import com.splitandmerge.mkvslice.ui.library.LibraryScreen
import com.splitandmerge.mkvslice.ui.library.LibraryViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import com.splitandmerge.mkvslice.domain.defaulttracks.RowState
import com.splitandmerge.mkvslice.domain.progress.JobProgressTracker

@RunWith(AndroidJUnit4::class)
class DefaultTracksUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testLibraryScreenThreeTiles() {
        val mockLibraryViewModel = mockk<LibraryViewModel>(relaxed = true)
        val stateFlow = MutableStateFlow(com.splitandmerge.mkvslice.ui.library.LibraryState(jobs = emptyList(), isInitialLoad = false))
        every { mockLibraryViewModel.state } returns stateFlow
        every { mockLibraryViewModel.jobs } returns MutableStateFlow(emptyList())
        every { mockLibraryViewModel.orphanJournals } returns MutableStateFlow(emptyList())

        var tileTapped = false

        composeTestRule.setContent {
            LibraryScreen(
                viewModel = mockLibraryViewModel,
                onNavigateToSettings = {},
                onStartSplitFlow = { _, _ -> },
                onStartMergeFlow = {},
                onStartDefaultTracksFlow = { tileTapped = true },
                onNavigateToJobDetail = {},
                onNavigateToSplitResult = {},
                onNavigateToMergeResult = {}
            )
        }

        // Assert "Set defaults" action FAB is present
        composeTestRule.onNodeWithText("Set defaults").assertExists()
        
        // Tap "Set defaults" and assert callback triggered
        composeTestRule.onNodeWithText("Set defaults").performClick()
        assertTrue(tileTapped)
    }

    @Test
    fun testTrackEditorSubtitleForced() {
        val testFileState = FileRowState(
            uri = "content://test/file1.mkv",
            displayName = "file1.mkv",
            sizeBytes = 1000L,
            isMkv = true,
            audioTracks = listOf(
                TrackInfo(1L, 2, "jp", 1, 0, "Audio 1", "E-AC3", 0L, null, null, 0L, emptyList())
            ),
            subtitleTracks = listOf(
                TrackInfo(2L, 17, "en", 0, 0, "Sub 1", "SRT", 0L, null, null, 0L, emptyList())
            ),
            currentSpec = EditSpec(1L, null, false),
            chosenSpec = EditSpec(1L, null, false)
        )

        var confirmedAudio: AudioChoice? = null
        var confirmedSub: SubChoice? = null
        var confirmedForced: Boolean? = null

        composeTestRule.setContent {
            TrackEditorScreen(
                fileState = testFileState,
                initialAudioChoice = AudioChoice.KeepCurrent,
                initialSubChoice = SubChoice.KeepCurrent,
                initialForcedSubtitle = false,
                onConfirm = { aud, sub, forced ->
                    confirmedAudio = aud
                    confirmedSub = sub
                    confirmedForced = forced
                },
                onCancel = {},
                onSaveDraft = { _, _, _ -> }
            )
        }

        // Initially subtitle is "None", so forced switch should be disabled
        composeTestRule.onNodeWithText("Forced Subtitle").assertExists()
        composeTestRule.onNode(isOn()).assertDoesNotExist() // no switch is checked

        // English subtitle track should exist
        composeTestRule.onNodeWithText("Track 2 (EN)").assertExists()
        
        // Select Track 2
        composeTestRule.onNodeWithText("Track 2 (EN)").performClick()
        
        // Forced switch should now be enabled. Let's toggle it.
        composeTestRule.onNode(isToggleable()).performClick()
        
        // Click confirm
        composeTestRule.onNodeWithText("Confirm").performClick()
        
        // Assert confirmed choices are updated
        assertTrue(confirmedAudio is AudioChoice.KeepCurrent)
        assertTrue(confirmedSub is SubChoice.Track)
        assertEquals(2L, (confirmedSub as SubChoice.Track).trackNumber)
        assertEquals(true, confirmedForced)
    }

    @Test
    fun testFileListBatchScreenToggles() {
        val files = listOf(
            FileRowState("uri1", "file1.mkv", 100L, true, isChecked = true, status = "WILL_CHANGE"),
            FileRowState("uri2", "file2.mkv", 200L, true, isChecked = false, status = "UNCHANGED"),
            FileRowState("uri3", "file3.txt", 50L, false, isChecked = false, status = "SKIPPED", reason = "not-mkv")
        )

        val mockViewModel = mockk<DefaultTracksViewModel>(relaxed = true)
        every { mockViewModel.filesList } returns MutableStateFlow(files)
        every { mockViewModel.uiState } returns MutableStateFlow(DefaultTracksUiState.ReadyList())
        every { mockViewModel.lastEditedUri } returns MutableStateFlow<String?>(null)
        every { mockViewModel.applyHint } returns MutableStateFlow<String?>(null)

        composeTestRule.setContent {
            FileListBatchScreen(
                viewModel = mockViewModel,
                filesList = files,
                onBack = {}
            )
        }

        // Assert rows and status pills exist
        composeTestRule.onNodeWithText("file1.mkv").assertExists()
        composeTestRule.onNodeWithText("Will Modify").assertExists()

        composeTestRule.onNodeWithText("file2.mkv").assertExists()
        composeTestRule.onNodeWithText("No Change").assertExists()

        composeTestRule.onNodeWithText("file3.txt").assertExists()
        composeTestRule.onNodeWithText("Non-MKV").assertExists()

        // Assert select all/none buttons
        composeTestRule.onNodeWithText("Select All").assertExists()
        composeTestRule.onNodeWithText("Select None").assertExists()

        // "Apply (1)" since only file1 is checked and isMkv is true
        composeTestRule.onNodeWithText("Apply changes (1)").assertExists()

        // "Apply to all similar files" is disabled
        composeTestRule.onNodeWithText("Apply to all similar files").assertExists()
        composeTestRule.onNodeWithText("Apply to all similar files").assertIsNotEnabled()
    }

    @Test
    fun testFileListBatchScreenApplyToSimilarButton() {
        val files = listOf(
            FileRowState("uri1", "file1.mkv", 100L, true, isChecked = true, status = "WILL_CHANGE"),
            FileRowState("uri2", "file2.mkv", 200L, true, isChecked = false, status = "UNCHANGED")
        )

        val mockViewModel = mockk<DefaultTracksViewModel>(relaxed = true)
        every { mockViewModel.filesList } returns MutableStateFlow(files)
        every { mockViewModel.uiState } returns MutableStateFlow(DefaultTracksUiState.ReadyList())
        every { mockViewModel.lastEditedUri } returns MutableStateFlow<String?>("uri1")
        every { mockViewModel.applyHint } returns MutableStateFlow<String?>(null)

        composeTestRule.setContent {
            FileListBatchScreen(
                viewModel = mockViewModel,
                filesList = files,
                onBack = {}
            )
        }

        // Assert button is enabled
        composeTestRule.onNodeWithText("Apply to all similar files").assertIsEnabled()

        // Tap the button
        composeTestRule.onNodeWithText("Apply to all similar files").performClick()

        // Verify viewModel.applyToSimilar("uri1") was called
        io.mockk.verify(exactly = 1) { mockViewModel.applyToSimilar("uri1") }
    }

    @Test
    fun testOnDeviceMatchScenarios() {
        val mockJobDao = mockk<com.splitandmerge.mkvslice.data.db.JobDao>(relaxed = true)
        val mockResultsDao = mockk<com.splitandmerge.mkvslice.data.db.DefaultTrackFileResultDao>(relaxed = true)
        val mockFileSystem = mockk<com.splitandmerge.mkvslice.platform.io.FileSystem>(relaxed = true)
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val savedStateHandle = SavedStateHandle()
        val realViewModel = DefaultTracksViewModel(
            mockJobDao,
            mockResultsDao,
            JobProgressTracker(),
            mockFileSystem,
            savedStateHandle,
            context
        )

        // Scenario 1: Untagged single audio applies
        val seedUri = "content://test/seed.mkv"
        val candidateUri = "content://test/candidate.mkv"

        val seedAudio = TrackInfo(1L, 2, "und", 1, 0, "Audio 1", "E-AC3", 0L, null, null, 0L, emptyList())
        val candidateAudio = TrackInfo(1L, 2, "und", 1, 0, "Audio 1", "E-AC3", 0L, null, null, 0L, emptyList())

        val files = listOf(
            FileRowState(
                uri = seedUri, displayName = "seed.mkv", sizeBytes = 100L, isMkv = true, isChecked = true,
                audioTracks = listOf(seedAudio), currentSpec = EditSpec(1L, null, false), chosenSpec = EditSpec(1L, null, false),
                audioChoice = AudioChoice.Track(1L), subChoice = SubChoice.KeepCurrent
            ),
            FileRowState(
                uri = candidateUri, displayName = "candidate.mkv", sizeBytes = 200L, isMkv = true, isChecked = false,
                audioTracks = listOf(candidateAudio)
            )
        )

        // Set the files list in the real VM using reflection
        val filesField = realViewModel.javaClass.getDeclaredField("_filesList")
        filesField.isAccessible = true
        (filesField.get(realViewModel) as MutableStateFlow<List<FileRowState>>).value = files

        // Call applyToSimilar
        realViewModel.applyToSimilar(seedUri)

        // Verify matches
        val candidates = realViewModel.filesList.value
        val candidate = candidates.find { it.uri == candidateUri }!!
        
        // Assert: apply-to-similar works for untagged audio, candidate matched, no refusal
        assertTrue(candidate.isChecked)
        assertEquals(1L, candidate.chosenSpec?.defaultAudioTrackNumber)
        assertNull(realViewModel.applyHint.value)

        // Scenario 2: Audio-only (audio=a track, subtitle=Keep current) -> matched files' subtitles UNCHANGED
        val candidateAudio2 = TrackInfo(1L, 2, "und", 1, 0, "Audio 1", "E-AC3", 0L, null, null, 0L, emptyList())
        val candidateSub2 = TrackInfo(2L, 17, "eng", 1, 0, "Sub 1", "SRT", 0L, null, null, 0L, emptyList())
        val files2 = listOf(
            FileRowState(
                uri = seedUri, displayName = "seed.mkv", sizeBytes = 100L, isMkv = true, isChecked = true,
                audioTracks = listOf(seedAudio), currentSpec = EditSpec(1L, 2L, false), chosenSpec = EditSpec(1L, 2L, false),
                audioChoice = AudioChoice.Track(1L), subChoice = SubChoice.KeepCurrent // subtitle Keep current
            ),
            FileRowState(
                uri = candidateUri, displayName = "candidate.mkv", sizeBytes = 200L, isMkv = true, isChecked = false,
                audioTracks = listOf(candidateAudio2), subtitleTracks = listOf(candidateSub2),
                currentSpec = EditSpec(1L, 2L, false)
            )
        )
        (filesField.get(realViewModel) as MutableStateFlow<List<FileRowState>>).value = files2
        realViewModel.applyToSimilar(seedUri)
        val candidate2 = realViewModel.filesList.value.find { it.uri == candidateUri }!!
        assertTrue(candidate2.isChecked)
        assertEquals(1L, candidate2.chosenSpec?.defaultAudioTrackNumber)
        // Subtitle default track should be preserved from currentSpec (2L)
        assertEquals(2L, candidate2.chosenSpec?.defaultSubtitleTrackNumber)

        // Scenario 3: Subtitle-only (audio=Keep current, subtitle=a track) -> matched files' audio UNCHANGED
        val seedSub3 = TrackInfo(2L, 17, "eng", 1, 0, "Sub 1", "SRT", 0L, null, null, 0L, emptyList())
        val files3 = listOf(
            FileRowState(
                uri = seedUri, displayName = "seed.mkv", sizeBytes = 100L, isMkv = true, isChecked = true,
                audioTracks = listOf(seedAudio), subtitleTracks = listOf(seedSub3),
                currentSpec = EditSpec(1L, 2L, false), chosenSpec = EditSpec(1L, 2L, false),
                audioChoice = AudioChoice.KeepCurrent, subChoice = SubChoice.Track(2L) // audio Keep current
            ),
            FileRowState(
                uri = candidateUri, displayName = "candidate.mkv", sizeBytes = 200L, isMkv = true, isChecked = false,
                audioTracks = listOf(candidateAudio2), subtitleTracks = listOf(candidateSub2),
                currentSpec = EditSpec(1L, 2L, false)
            )
        )
        (filesField.get(realViewModel) as MutableStateFlow<List<FileRowState>>).value = files3
        realViewModel.applyToSimilar(seedUri)
        val candidate3 = realViewModel.filesList.value.find { it.uri == candidateUri }!!
        assertTrue(candidate3.isChecked)
        // Audio default track should be preserved from currentSpec (1L)
        assertEquals(1L, candidate3.chosenSpec?.defaultAudioTrackNumber)
        assertEquals(2L, candidate3.chosenSpec?.defaultSubtitleTrackNumber)

        // Scenario 4: Both audio=a track, subtitle=a track -> both changed
        val seedAudio4 = TrackInfo(1L, 2, "eng", 1, 0, "Audio 1", "E-AC3", 0L, null, null, 0L, emptyList())
        val seedSub4 = TrackInfo(2L, 17, "eng", 1, 0, "Sub 1", "SRT", 0L, null, null, 0L, emptyList())
        val candidateAudio4 = TrackInfo(2L, 2, "eng", 1, 0, "Audio 1", "E-AC3", 0L, null, null, 0L, emptyList()) // audio is track 2
        val candidateSub4 = TrackInfo(3L, 17, "eng", 1, 0, "Sub 1", "SRT", 0L, null, null, 0L, emptyList()) // sub is track 3
        val files4 = listOf(
            FileRowState(
                uri = seedUri, displayName = "seed.mkv", sizeBytes = 100L, isMkv = true, isChecked = true,
                audioTracks = listOf(seedAudio4), subtitleTracks = listOf(seedSub4),
                currentSpec = EditSpec(1L, 2L, false), chosenSpec = EditSpec(1L, 2L, false),
                audioChoice = AudioChoice.Track(1L), subChoice = SubChoice.Track(2L)
            ),
            FileRowState(
                uri = candidateUri, displayName = "candidate.mkv", sizeBytes = 200L, isMkv = true, isChecked = false,
                audioTracks = listOf(candidateAudio4), subtitleTracks = listOf(candidateSub4),
                currentSpec = EditSpec(1L, 2L, false)
            )
        )
        (filesField.get(realViewModel) as MutableStateFlow<List<FileRowState>>).value = files4
        realViewModel.applyToSimilar(seedUri)
        val candidate4 = realViewModel.filesList.value.find { it.uri == candidateUri }!!
        assertTrue(candidate4.isChecked)
        // Both resolved to candidate's own track numbers
        assertEquals(2L, candidate4.chosenSpec?.defaultAudioTrackNumber)
        assertEquals(3L, candidate4.chosenSpec?.defaultSubtitleTrackNumber)
    }

    @Test
    fun chipShowsResolvedAudioAndSub() {
        val file1 = FileRowState(
            uri = "content://test/file1.mkv",
            displayName = "file1.mkv",
            sizeBytes = 1000L,
            isMkv = true,
            audioTracks = listOf(TrackInfo(1L, 2, "eng", 1, 0, "Audio 1", "E-AC3", 0L, null, null, 0L, emptyList())),
            subtitleTracks = listOf(TrackInfo(2L, 17, "fra", 0, 0, "Sub 1", "SRT", 0L, null, null, 0L, emptyList())),
            currentSpec = EditSpec(1L, null, false),
            chosenSpec = EditSpec(1L, 2L, true),
            matchState = RowState.MATCHED
        )
        val file2 = FileRowState(
            uri = "content://test/file2.mkv",
            displayName = "file2.mkv",
            sizeBytes = 1000L,
            isMkv = true,
            audioTracks = listOf(TrackInfo(3L, 2, "spa", 1, 0, "Audio 1", "E-AC3", 0L, null, null, 0L, emptyList())),
            subtitleTracks = listOf(TrackInfo(2L, 17, "fra", 0, 0, "Sub 1", "SRT", 0L, null, null, 0L, emptyList())),
            currentSpec = EditSpec(3L, 2L, false),
            chosenSpec = EditSpec(3L, null, false),
            matchState = RowState.MATCHED
        )

        composeTestRule.setContent {
            androidx.compose.foundation.layout.Column {
                FileRowItem(file = file1, onToggle = {}, onClick = {})
                FileRowItem(file = file2, onToggle = {}, onClick = {})
            }
        }

        // Assert file1 chips: "AUD: Track 1 (ENG)" and "SUB: Track 2 (FRA) [F]"
        composeTestRule.onNodeWithText("AUD: Track 1 (ENG)").assertExists()
        composeTestRule.onNodeWithText("SUB: Track 2 (FRA) [F]").assertExists()

        // Assert file2 chips: "AUD: Track 3 (SPA)" and "SUB: None"
        composeTestRule.onNodeWithText("AUD: Track 3 (SPA)").assertExists()
        composeTestRule.onNodeWithText("SUB: None").assertExists()

        // Assert old target line "→" does NOT exist
        composeTestRule.onNodeWithText("→", substring = true).assertDoesNotExist()
    }

    @Test
    fun chipShowsCurrentDefaultWhenNoChosenSpec() {
        val file1 = FileRowState(
            uri = "content://test/file1.mkv",
            displayName = "file1.mkv",
            sizeBytes = 1000L,
            isMkv = true,
            audioTracks = listOf(
                TrackInfo(1L, 2, "eng", 0, 0, "Audio 1", "E-AC3", 0L, null, null, 0L, emptyList()),
                TrackInfo(2L, 2, "eng", 1, 0, "Audio 2", "E-AC3", 0L, null, null, 0L, emptyList()) // flagDefault=1
            ),
            subtitleTracks = listOf(
                TrackInfo(3L, 17, "fra", 0, 0, "Sub 1", "SRT", 0L, null, null, 0L, emptyList()) // implied first in doc order
            ),
            currentSpec = EditSpec(2L, 3L, false),
            chosenSpec = null,
            matchState = RowState.UNCHECKED,
            status = "UNCHANGED",
            reason = "no kor subtitle"
        )

        composeTestRule.setContent {
            FileRowItem(
                file = file1,
                onToggle = {},
                onClick = {}
            )
        }

        // Should show current default audio (Track 2) and implied default subtitle (Track 3)
        composeTestRule.onNodeWithText("AUD: Track 2 (ENG)").assertExists()
        composeTestRule.onNodeWithText("SUB: Track 3 (FRA)").assertExists()

        // Assert reason text is preserved
        composeTestRule.onNodeWithText("no kor subtitle").assertExists()
    }
}
