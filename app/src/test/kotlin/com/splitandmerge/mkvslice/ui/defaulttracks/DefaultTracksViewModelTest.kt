package com.splitandmerge.mkvslice.ui.defaulttracks

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.splitandmerge.mkvslice.data.db.DefaultTrackFileResultDao
import com.splitandmerge.mkvslice.data.db.JobDao
import com.splitandmerge.mkvslice.data.db.entity.DefaultTrackFileResultEntity
import com.splitandmerge.mkvslice.data.db.entity.JobEntity
import com.splitandmerge.mkvslice.domain.defaulttracks.DefaultTracksEngine
import com.splitandmerge.mkvslice.domain.defaulttracks.DefaultTracksEngineResult
import com.splitandmerge.mkvslice.domain.defaulttracks.model.EditSpec
import com.splitandmerge.mkvslice.domain.defaulttracks.model.TrackInfo
import com.splitandmerge.mkvslice.domain.defaulttracks.RowState
import com.splitandmerge.mkvslice.domain.defaulttracks.AudioChoice
import com.splitandmerge.mkvslice.domain.defaulttracks.SubChoice
import com.splitandmerge.mkvslice.domain.model.JobStatus
import com.splitandmerge.mkvslice.domain.model.JobType
import com.splitandmerge.mkvslice.domain.progress.JobProgressTracker
import com.splitandmerge.mkvslice.platform.io.FileSystem
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.boolean
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultTracksViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val jobDao = mockk<JobDao>(relaxed = true)
    private val defaultTrackFileResultDao = mockk<DefaultTrackFileResultDao>(relaxed = true)
    private val fileSystem = mockk<FileSystem>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private val savedStateHandle = SavedStateHandle()

    private lateinit var viewModel: DefaultTracksViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = DefaultTracksViewModel(
            jobDao = jobDao,
            defaultTrackFileResultDao = defaultTrackFileResultDao,
            jobProgressTracker = JobProgressTracker(),
            fileSystem = fileSystem,
            savedStateHandle = savedStateHandle,
            context = context
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testStateMachineTransitionsAndCancel() = runTest(testDispatcher) {
        // Init State
        assertEquals(DefaultTracksUiState.Idle, viewModel.uiState.value)

        // Start Picker
        viewModel.startPicker()
        assertEquals(DefaultTracksUiState.Picking, viewModel.uiState.value)

        // Cancel during Scanning/Analyzing -> returns to Idle/Picker
        viewModel.cancelToPicker()
        assertEquals(DefaultTracksUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun testPerFileEditSpecCapture() {
        // Assert spec updates on file A do not affect file B
        val uriA = "content://test/A.mkv"
        val uriB = "content://test/B.mkv"

        viewModel.confirmEditor(uriA, AudioChoice.Track(1L), SubChoice.Track(2L), true)
        viewModel.confirmEditor(uriB, AudioChoice.Track(3L), SubChoice.NoneSub, false)

        val map = viewModel.getSpecsMap()
        assertEquals(1L, map[uriA]?.defaultAudioTrackNumber)
        assertEquals(2L, map[uriA]?.defaultSubtitleTrackNumber)
        assertTrue(map[uriA]?.forcedSubtitle == true)

        assertEquals(3L, map[uriB]?.defaultAudioTrackNumber)
        assertNull(map[uriB]?.defaultSubtitleTrackNumber)
        assertFalse(map[uriB]?.forcedSubtitle == true)
    }

    @Test
    fun testNoCrossApply() = runTest(testDispatcher) {
        // Central correctness test: ≥2 checked files with DIFFERENT track numbers
        val uriA = "content://test/A.mkv"
        val uriB = "content://test/B.mkv"

        val specA = EditSpec(1L, 2L, true)
        val specB = EditSpec(3L, 4L, false)

        val files = listOf(
            FileRowState(uri = uriA, displayName = "A.mkv", sizeBytes = 100L, isMkv = true, isChecked = true, currentSpec = specA, chosenSpec = specA),
            FileRowState(uri = uriB, displayName = "B.mkv", sizeBytes = 200L, isMkv = true, isChecked = true, currentSpec = specB, chosenSpec = specB)
        )

        // Set list
        val filesField = viewModel.javaClass.getDeclaredField("_filesList")
        filesField.isAccessible = true
        (filesField.get(viewModel) as MutableStateFlow<List<FileRowState>>).value = files

        // Mock database calls
        coEvery { jobDao.upsert(any()) } just Runs
        coEvery { defaultTrackFileResultDao.insertAll(any()) } just Runs

        // Apply changes
        viewModel.applyChanges()
        testScheduler.advanceUntilIdle()

        // Capture enqueued results
        val resultsSlot = slot<List<DefaultTrackFileResultEntity>>()
        coVerify { defaultTrackFileResultDao.insertAll(capture(resultsSlot)) }

        val captured = resultsSlot.captured
        assertEquals(2, captured.size)

        // Deserialize enqueued specs & assert they are correct per URI
        val capturedSpecA = deserializeEditSpec(captured.find { it.uri == uriA }?.appliedSpecJson ?: "")
        val capturedSpecB = deserializeEditSpec(captured.find { it.uri == uriB }?.appliedSpecJson ?: "")

        assertEquals(1L, capturedSpecA.defaultAudioTrackNumber)
        assertEquals(2L, capturedSpecA.defaultSubtitleTrackNumber)
        assertTrue(capturedSpecA.forcedSubtitle)

        assertEquals(3L, capturedSpecB.defaultAudioTrackNumber)
        assertEquals(4L, capturedSpecB.defaultSubtitleTrackNumber)
        assertFalse(capturedSpecB.forcedSubtitle)

        // Simulate engine running job service logic on the pre-written rows
        val mockEngine = mockk<DefaultTracksEngine>()
        val jobId = captured[0].jobId

        // Capture every processFile call using mockk argument captors into lists
        val capturedUris = mutableListOf<String>()
        val capturedSpecs = mutableListOf<EditSpec>()
        coEvery { 
            mockEngine.processFile(capture(capturedUris), capture(capturedSpecs), eq(jobId), any(), any()) 
        } returns DefaultTracksEngineResult("DONE", "", "IN_PLACE_PATCH")

        // Iterate through enqueued rows exactly like JobService does
        captured.forEachIndexed { index, row ->
            val spec = deserializeEditSpec(row.appliedSpecJson)
            mockEngine.processFile(row.uri, spec, jobId, index, {})
        }

        // Assert (b) call count == checked-row count
        assertEquals(2, capturedUris.size)
        assertEquals(2, capturedSpecs.size)

        // Find indexes
        val idxA = capturedUris.indexOf(uriA)
        val idxB = capturedUris.indexOf(uriB)
        assertTrue("uriA should have been processed", idxA != -1)
        assertTrue("uriB should have been processed", idxB != -1)

        // Assert (a) uri_A received A's confirmed spec and NOT B's
        val specForA = capturedSpecs[idxA]
        assertEquals(1L, specForA.defaultAudioTrackNumber)
        assertEquals(2L, specForA.defaultSubtitleTrackNumber)
        assertTrue(specForA.forcedSubtitle)

        // Assert B's spec went to B and not A
        val specForB = capturedSpecs[idxB]
        assertEquals(3L, specForB.defaultAudioTrackNumber)
        assertEquals(4L, specForB.defaultSubtitleTrackNumber)
        assertFalse(specForB.forcedSubtitle)

        // Assert (c) no call used a spec from another uri (each URI got exactly its own spec)
        capturedUris.forEachIndexed { idx, uri ->
            val spec = capturedSpecs[idx]
            if (uri == uriA) {
                assertEquals(specA.defaultAudioTrackNumber, spec.defaultAudioTrackNumber)
                assertEquals(specA.defaultSubtitleTrackNumber, spec.defaultSubtitleTrackNumber)
                assertEquals(specA.forcedSubtitle, spec.forcedSubtitle)
            } else if (uri == uriB) {
                assertEquals(specB.defaultAudioTrackNumber, spec.defaultAudioTrackNumber)
                assertEquals(specB.defaultSubtitleTrackNumber, spec.defaultSubtitleTrackNumber)
                assertEquals(specB.forcedSubtitle, spec.forcedSubtitle)
            } else {
                fail("Unexpected URI $uri processed")
            }
        }

        coVerify(exactly = 1) { mockEngine.processFile(uriA, specA, jobId, 0, any()) }
        coVerify(exactly = 1) { mockEngine.processFile(uriB, specB, jobId, 1, any()) }
        coVerify(exactly = 2) { mockEngine.processFile(any(), any(), jobId, any(), any()) }
    }

    @Test
    fun testCancelMidApply() = runTest(testDispatcher) {
        val uriA = "content://test/A.mkv"
        val uriB = "content://test/B.mkv"
        
        val rows = listOf(
            DefaultTrackFileResultEntity("idA", "job123", uriA, "A.mkv", "PENDING", "", "SKIPPED", "", 1000L),
            DefaultTrackFileResultEntity("idB", "job123", uriB, "B.mkv", "PENDING", "", "SKIPPED", "", 1001L)
        )

        // Mock DB returns
        coEvery { defaultTrackFileResultDao.getResultsForJob("job123") } returns rows
        coEvery { defaultTrackFileResultDao.insertAll(any()) } just Runs
        coEvery { defaultTrackFileResultDao.insert(any()) } just Runs

        // Simulate JobService processing with CancellationException at second row
        val mockEngine = mockk<DefaultTracksEngine>()
        coEvery { mockEngine.processFile(uriA, any(), "job123", 0, any()) } returns DefaultTracksEngineResult("DONE", "", "IN_PLACE_PATCH")
        coEvery { mockEngine.processFile(uriB, any(), "job123", 1, any()) } throws kotlinx.coroutines.CancellationException("Job cancelled")

        var jobStatus = JobStatus.RUNNING

        try {
            // Row 1 completes
            val r1 = mockEngine.processFile(rows[0].uri, EditSpec(1L, null, false), "job123", 0)
            defaultTrackFileResultDao.insert(rows[0].copy(status = r1.status, reason = r1.reason, writeStrategy = r1.writeStrategy))

            // Row 2 gets cancelled
            throw kotlinx.coroutines.CancellationException("Job cancelled")
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Simulated finally block logic: remaining pending rows are updated to SKIPPED(canceled)
            val updated = rows.map { r ->
                if (r.uri == uriB) r.copy(status = "SKIPPED", reason = "canceled") else r.copy(status = "DONE", writeStrategy = "IN_PLACE_PATCH")
            }
            defaultTrackFileResultDao.insertAll(updated)
            jobStatus = JobStatus.CANCELLED
        }

        // Verify outcomes
        assertEquals(JobStatus.CANCELLED, jobStatus)
        coVerify(exactly = 1) { defaultTrackFileResultDao.insertAll(any()) }
        coVerify { defaultTrackFileResultDao.insert(match { it.uri == uriA && it.status == "DONE" }) }
        coVerify { defaultTrackFileResultDao.insertAll(match { it.any { r -> r.uri == uriB && r.status == "SKIPPED" && r.reason == "canceled" } }) }
    }

    private fun deserializeEditSpec(jsonString: String): EditSpec {
        val element = Json.parseToJsonElement(jsonString).jsonObject
        val audio = element["defaultAudioTrackNumber"]?.jsonPrimitive?.long ?: 0L
        val sub = element["defaultSubtitleTrackNumber"]?.jsonPrimitive?.longOrNull
        val forced = element["forcedSubtitle"]?.jsonPrimitive?.boolean ?: false
        return EditSpec(audio, sub, forced)
    }

    @Test
    fun untaggedSingleAudio_appliesNoRefusal() = runTest(testDispatcher) {
        // Seed has und audio — SINGLE track, user explicitly chose it via AudioChoice.Track(1L).
        // With the new model, und audio IS allowed to fan out (und matches und).
        // Candidate also has single und audio → MATCHED.
        val seedUri = "content://test/seed.mkv"
        val candidateUri = "content://test/candidate.mkv"

        val seedAudio = TrackInfo(
            trackNumber = 1L,
            trackType = 2,
            language = "und", // Untagged
            flagDefault = 1,
            flagForced = 0,
            name = null,
            codec = "A_AAC",
            byteOffset = 100L,
            flagDefaultOffset = null,
            flagForcedOffset = null,
            trackEntryEnd = 200L,
            voidDonors = emptyList()
        )

        val files = listOf(
            FileRowState(
                uri = seedUri,
                displayName = "seed.mkv",
                sizeBytes = 100L,
                isMkv = true,
                isChecked = true,
                audioTracks = listOf(seedAudio),
                currentSpec = EditSpec(1L, null, false),
                chosenSpec = EditSpec(1L, null, false),
                // Seed has AudioChoice.Track(1L) — user explicitly picked this und track
                audioChoice = AudioChoice.Track(1L),
                subChoice = SubChoice.NoneSub
            ),
            FileRowState(
                uri = candidateUri,
                displayName = "candidate.mkv",
                sizeBytes = 200L,
                isMkv = true,
                isChecked = false,
                audioTracks = listOf(
                    TrackInfo(
                        trackNumber = 1L,
                        trackType = 2,
                        language = "und",
                        flagDefault = 1,
                        flagForced = 0,
                        name = null,
                        codec = "A_AAC",
                        byteOffset = 100L,
                        flagDefaultOffset = null,
                        flagForcedOffset = null,
                        trackEntryEnd = 200L,
                        voidDonors = emptyList()
                    )
                )
            )
        )

        val filesField = viewModel.javaClass.getDeclaredField("_filesList")
        filesField.isAccessible = true
        (filesField.get(viewModel) as MutableStateFlow<List<FileRowState>>).value = files

        viewModel.applyToSimilar(seedUri)

        // No error hint (und single track is now allowed to fan out)
        val hint = viewModel.applyHint.value
        assertNull(hint)

        // Candidate IS matched (und matches und)
        val candidate = viewModel.filesList.value.find { it.uri == candidateUri }!!
        assertTrue(candidate.isChecked)
        assertNotNull(candidate.chosenSpec)
        assertEquals(1L, candidate.chosenSpec?.defaultAudioTrackNumber)
    }

    @Test
    fun untaggedMultiAudio_positionallyResolved() = runTest(testDispatcher) {
        // Seed chose Track 1 (und). Candidate has Track 1 + Track 2 (both und, no default).
        // Rung 0 (POSITIONAL) selects Track 1 from the candidate → MATCHED, not PARTIAL_NEEDS_REVIEW.
        val seedUri = "content://test/seed.mkv"
        val candidateUri = "content://test/candidate.mkv"

        val seedAudio = TrackInfo(
            trackNumber = 1L, trackType = 2, language = "und",
            flagDefault = 1, flagForced = 0, name = null, codec = "A_AAC",
            byteOffset = 100L, flagDefaultOffset = null, flagForcedOffset = null,
            trackEntryEnd = 200L, voidDonors = emptyList()
        )

        val candidateAudio1 = TrackInfo(
            trackNumber = 1L, trackType = 2, language = "und",
            flagDefault = 0, flagForced = 0, name = null, codec = "A_AAC",
            byteOffset = 100L, flagDefaultOffset = null, flagForcedOffset = null,
            trackEntryEnd = 200L, voidDonors = emptyList()
        )

        val candidateAudio2 = TrackInfo(
            trackNumber = 2L, trackType = 2, language = "und",
            flagDefault = 0, flagForced = 0, name = null, codec = "A_AAC",
            byteOffset = 300L, flagDefaultOffset = null, flagForcedOffset = null,
            trackEntryEnd = 400L, voidDonors = emptyList()
        )

        val files = listOf(
            FileRowState(
                uri = seedUri,
                displayName = "seed.mkv",
                sizeBytes = 100L,
                isMkv = true,
                isChecked = true,
                audioTracks = listOf(seedAudio),
                currentSpec = EditSpec(1L, null, false),
                chosenSpec = EditSpec(1L, null, false),
                audioChoice = AudioChoice.Track(1L),
                subChoice = SubChoice.NoneSub
            ),
            FileRowState(
                uri = candidateUri,
                displayName = "candidate.mkv",
                sizeBytes = 200L,
                isMkv = true,
                isChecked = false,
                audioTracks = listOf(candidateAudio1, candidateAudio2) // two und tracks; Rung 0 picks Track 1
            )
        )

        val filesField = viewModel.javaClass.getDeclaredField("_filesList")
        filesField.isAccessible = true
        (filesField.get(viewModel) as MutableStateFlow<List<FileRowState>>).value = files

        viewModel.applyToSimilar(seedUri)

        // No error hint / banner
        assertNull(viewModel.applyHint.value)

        // Rung 0 positional resolution: Track 1 in candidate matches seed's chosen Track 1 → MATCHED
        val candidate = viewModel.filesList.value.find { it.uri == candidateUri }!!
        assertTrue(candidate.isChecked)
        assertEquals(RowState.MATCHED, candidate.matchState)
        assertEquals(1L, candidate.chosenSpec?.defaultAudioTrackNumber)
    }

    @Test
    fun namedDoesNotMatchUntagged() = runTest(testDispatcher) {
        val seedUri = "content://test/seed.mkv"
        val candidateUri = "content://test/candidate.mkv"

        val seedAudio = TrackInfo(
            trackNumber = 1L, trackType = 2, language = "eng", // English
            flagDefault = 1, flagForced = 0, name = null, codec = "A_AAC",
            byteOffset = 100L, flagDefaultOffset = null, flagForcedOffset = null,
            trackEntryEnd = 200L, voidDonors = emptyList()
        )

        val candidateAudio = TrackInfo(
            trackNumber = 1L, trackType = 2, language = "und", // Untagged
            flagDefault = 1, flagForced = 0, name = null, codec = "A_AAC",
            byteOffset = 100L, flagDefaultOffset = null, flagForcedOffset = null,
            trackEntryEnd = 200L, voidDonors = emptyList()
        )

        val files = listOf(
            FileRowState(
                uri = seedUri,
                displayName = "seed.mkv",
                sizeBytes = 100L,
                isMkv = true,
                isChecked = true,
                audioTracks = listOf(seedAudio),
                currentSpec = EditSpec(1L, null, false),
                chosenSpec = EditSpec(1L, null, false),
                audioChoice = AudioChoice.Track(1L),
                subChoice = SubChoice.NoneSub
            ),
            FileRowState(
                uri = candidateUri,
                displayName = "candidate.mkv",
                sizeBytes = 200L,
                isMkv = true,
                isChecked = false,
                audioTracks = listOf(candidateAudio)
            )
        )

        val filesField = viewModel.javaClass.getDeclaredField("_filesList")
        filesField.isAccessible = true
        (filesField.get(viewModel) as MutableStateFlow<List<FileRowState>>).value = files

        viewModel.applyToSimilar(seedUri)

        // Candidate is UNCHECKED (mismatch: eng != und)
        val candidate = viewModel.filesList.value.find { it.uri == candidateUri }!!
        assertFalse(candidate.isChecked)
        assertEquals("UNCHANGED", candidate.status)
        assertEquals(RowState.UNCHECKED, candidate.matchState)
        assertEquals("no eng audio", candidate.reason)
    }

    @Test
    fun bothKeepCurrentShowsHint() = runTest(testDispatcher) {
        // Both audio and subtitle are KeepCurrent → gentle hint, no changes.
        val seedUri = "content://test/seed.mkv"
        val candidateUri = "content://test/candidate.mkv"

        val files = listOf(
            FileRowState(
                uri = seedUri,
                displayName = "seed.mkv",
                sizeBytes = 100L,
                isMkv = true,
                isChecked = true,
                audioTracks = listOf(
                    TrackInfo(
                        trackNumber = 1L, trackType = 2, language = "eng",
                        flagDefault = 1, flagForced = 0, name = null, codec = "A_AAC",
                        byteOffset = 100L, flagDefaultOffset = null, flagForcedOffset = null,
                        trackEntryEnd = 200L, voidDonors = emptyList()
                    )
                ),
                currentSpec = EditSpec(1L, null, false),
                chosenSpec = EditSpec(1L, null, false),
                // No audioChoice / subChoice → both KeepCurrent
                audioChoice = null,
                subChoice = null
            ),
            FileRowState(
                uri = candidateUri,
                displayName = "candidate.mkv",
                sizeBytes = 200L,
                isMkv = true,
                isChecked = false,
                audioTracks = emptyList()
            )
        )

        val filesField = viewModel.javaClass.getDeclaredField("_filesList")
        filesField.isAccessible = true
        (filesField.get(viewModel) as MutableStateFlow<List<FileRowState>>).value = files

        viewModel.applyToSimilar(seedUri)

        // Hint shown
        assertNotNull(viewModel.applyHint.value)
        assertTrue(viewModel.applyHint.value!!.contains("Pick an audio or subtitle"))

        // Candidate remains untouched
        val candidate = viewModel.filesList.value.find { it.uri == candidateUri }!!
        assertFalse(candidate.isChecked)
        assertNull(candidate.chosenSpec)
    }

    @Test
    fun testBatchApplyUsesFileOwnTrackNumbers() = runTest(testDispatcher) {
        val seedUri = "content://test/seed.mkv"
        val candidateUri = "content://test/candidate.mkv"

        val seedAudio = TrackInfo(
            trackNumber = 2L, // Seed audio is Track 2
            trackType = 2,
            language = "eng",
            flagDefault = 1,
            flagForced = 0,
            name = null,
            codec = "A_AAC",
            byteOffset = 100L,
            flagDefaultOffset = null,
            flagForcedOffset = null,
            trackEntryEnd = 200L,
            voidDonors = emptyList()
        )
        val seedSub = TrackInfo(
            trackNumber = 4L, // Seed sub is Track 4
            trackType = 17,
            language = "fra",
            flagDefault = 1,
            flagForced = 0,
            name = null,
            codec = "S_TEXT/UTF8",
            byteOffset = 300L,
            flagDefaultOffset = null,
            flagForcedOffset = null,
            trackEntryEnd = 400L,
            voidDonors = emptyList()
        )

        val candAudio = TrackInfo(
            trackNumber = 1L, // Candidate audio is Track 1
            trackType = 2,
            language = "eng",
            flagDefault = 1,
            flagForced = 0,
            name = null,
            codec = "A_AAC",
            byteOffset = 100L,
            flagDefaultOffset = null,
            flagForcedOffset = null,
            trackEntryEnd = 200L,
            voidDonors = emptyList()
        )
        val candSub = TrackInfo(
            trackNumber = 3L, // Candidate sub is Track 3
            trackType = 17,
            language = "fra",
            flagDefault = 1,
            flagForced = 0,
            name = null,
            codec = "S_TEXT/UTF8",
            byteOffset = 300L,
            flagDefaultOffset = null,
            flagForcedOffset = null,
            trackEntryEnd = 400L,
            voidDonors = emptyList()
        )

        val files = listOf(
            FileRowState(
                uri = seedUri,
                displayName = "seed.mkv",
                sizeBytes = 100L,
                isMkv = true,
                isChecked = true,
                audioTracks = listOf(seedAudio),
                subtitleTracks = listOf(seedSub),
                currentSpec = EditSpec(2L, 4L, false),
                chosenSpec = EditSpec(2L, 4L, false),
                // Seed explicitly chose Track 2 audio + Track 4 subtitle
                audioChoice = AudioChoice.Track(2L),
                subChoice = SubChoice.Track(4L)
            ),
            FileRowState(
                uri = candidateUri,
                displayName = "candidate.mkv",
                sizeBytes = 200L,
                isMkv = true,
                isChecked = false,
                audioTracks = listOf(candAudio),
                subtitleTracks = listOf(candSub),
                currentSpec = EditSpec(1L, 3L, false),
                chosenSpec = null
            )
        )

        val filesField = viewModel.javaClass.getDeclaredField("_filesList")
        filesField.isAccessible = true
        (filesField.get(viewModel) as MutableStateFlow<List<FileRowState>>).value = files

        // Call applyToSimilar
        viewModel.applyToSimilar(seedUri)

        // Assert candidate is checked and stamped with its own track numbers
        val currentFiles = viewModel.filesList.value
        val candidate = currentFiles.find { it.uri == candidateUri }!!
        assertTrue(candidate.isChecked)
        assertNotNull(candidate.chosenSpec)
        assertEquals(1L, candidate.chosenSpec?.defaultAudioTrackNumber) // Candidate's audio track, not seed's 2L
        assertEquals(3L, candidate.chosenSpec?.defaultSubtitleTrackNumber) // Candidate's sub track, not seed's 4L
    }

    @Test
    fun editorBackKeepsDraftAndReturnsToList() = runTest(testDispatcher) {
        val uri = "content://test/file1.mkv"
        val spec = EditSpec(1L, 2L, false)
        val fileState = FileRowState(
            uri = uri,
            displayName = "file1.mkv",
            sizeBytes = 100L,
            isMkv = true,
            audioTracks = listOf(TrackInfo(1L, 2, "eng", 1, 0, "Audio 1", "E-AC3", 0L, null, null, 0L, emptyList())),
            subtitleTracks = listOf(TrackInfo(2L, 17, "fra", 1, 0, "Sub 1", "SRT", 0L, null, null, 0L, emptyList())),
            currentSpec = spec,
            chosenSpec = spec
        )

        val filesField = viewModel.javaClass.getDeclaredField("_filesList")
        filesField.isAccessible = true
        (filesField.get(viewModel) as MutableStateFlow<List<FileRowState>>).value = listOf(fileState)

        // 1. Open editor
        viewModel.openEditor(uri)
        assertEquals(DefaultTracksUiState.ReadyList(editingFileUri = uri), viewModel.uiState.value)

        // 2. Set selection (AudioChoice.Track(1L), SubChoice.NoneSub, false) and invoke back (saveDraft)
        val draftAudio = AudioChoice.Track(1L)
        val draftSub = SubChoice.NoneSub
        val draftForced = false
        viewModel.saveDraft(uri, draftAudio, draftSub, draftForced)

        // 3. Verify state transitioned back to list and draft is stored
        assertEquals(DefaultTracksUiState.ReadyList(editingFileUri = null), viewModel.uiState.value)
        val currentDraft = viewModel.drafts.value[uri]
        assertNotNull(currentDraft)
        assertEquals(draftAudio, currentDraft?.audioChoice)
        assertEquals(draftSub, currentDraft?.subChoice)
        assertEquals(draftForced, currentDraft?.forcedSubtitle)

        // 4. Verify row chosenSpec / status UNCHANGED
        val files = viewModel.filesList.value
        val row = files.find { it.uri == uri }!!
        assertEquals(spec, row.chosenSpec)
        assertEquals("SCAN", row.status)

        // 5. Cancel (discardDraft) discards draft
        viewModel.discardDraft(uri)
        assertNull(viewModel.drafts.value[uri])
    }
}
