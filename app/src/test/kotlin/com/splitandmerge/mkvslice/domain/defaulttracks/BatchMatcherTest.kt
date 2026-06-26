package com.splitandmerge.mkvslice.domain.defaulttracks

import com.splitandmerge.mkvslice.domain.defaulttracks.model.EditSpec
import com.splitandmerge.mkvslice.domain.defaulttracks.model.TrackInfo
import com.splitandmerge.mkvslice.ui.defaulttracks.FileRowState
import org.junit.Assert.*
import org.junit.Test

class BatchMatcherTest {

    private fun createTrack(
        trackNumber: Long,
        trackType: Int,
        language: String,
        flagDefault: Int = 0,
        flagForced: Int = 0,
        name: String? = null
    ): TrackInfo {
        return TrackInfo(
            trackNumber = trackNumber,
            trackType = trackType,
            language = language,
            flagDefault = flagDefault,
            flagForced = flagForced,
            name = name,
            codec = "A_AAC",
            byteOffset = 100L,
            flagDefaultOffset = 105L,
            flagForcedOffset = 110L,
            trackEntryEnd = 200L,
            voidDonors = emptyList()
        )
    }

    // ---------- helpers for Preference with typical values ----------

    private fun audioOnlyPref(
        lang: String,
        region: String? = null,
        forcedSub: Boolean = false
    ) = Preference(
        audioActive = true, defaultAudioLang = lang, defaultAudioRegion = region,
        subActive = false, subNone = false, defaultSubLang = null, defaultSubRegion = null,
        forcedSub = forcedSub
    )

    private fun audioSubPref(
        audioLang: String, subLang: String,
        audioRegion: String? = null, subRegion: String? = null,
        forcedSub: Boolean = false
    ) = Preference(
        audioActive = true, defaultAudioLang = audioLang, defaultAudioRegion = audioRegion,
        subActive = true, subNone = false, defaultSubLang = subLang, defaultSubRegion = subRegion,
        forcedSub = forcedSub
    )

    @Test
    fun directorsCutNotTreatedAsCommentary() {
        // A "Director's Cut" main track is chosen, NOT de-prioritized.
        val p = audioOnlyPref("eng")

        val files = listOf(
            FileRowState(
                uri = "content://test/1.mkv",
                displayName = "1.mkv",
                sizeBytes = 100L,
                isMkv = true,
                audioTracks = listOf(
                    createTrack(1L, 2, "eng", name = "Director's Cut"),
                    createTrack(2L, 2, "eng", name = "Commentary")
                )
            )
        )

        val results = BatchMatcher.match(files, p, "content://seed.mkv")
        assertEquals(1, results.size)
        val res = results[0]
        assertEquals(RowState.MATCHED, res.state)
        assertNotNull(res.resolvedSpec)
        assertEquals(1L, res.resolvedSpec?.defaultAudioTrackNumber)
    }

    @Test
    fun testBatchMatcherAudioMismatch() {
        val p = audioOnlyPref("eng")

        val files = listOf(
            FileRowState(
                uri = "content://test/1.mkv",
                displayName = "1.mkv",
                sizeBytes = 100L,
                isMkv = true,
                audioTracks = listOf(createTrack(1L, 2, "fra"))
            )
        )

        val results = BatchMatcher.match(files, p, "content://seed.mkv")
        assertEquals(RowState.UNCHECKED, results[0].state)
        assertEquals("no eng audio", results[0].reason)
    }

    @Test
    fun testBatchMatcherRegionStrictness() {
        // Seed region is en-US. Candidates without matching region should fail.
        val p = Preference(
            audioActive = true, defaultAudioLang = "eng", defaultAudioRegion = "us",
            subActive = false, subNone = false, defaultSubLang = null, defaultSubRegion = null,
            forcedSub = false
        )

        val files = listOf(
            FileRowState(
                uri = "content://test/region_less.mkv",
                displayName = "region_less.mkv",
                sizeBytes = 100L,
                isMkv = true,
                audioTracks = listOf(createTrack(1L, 2, "eng")) // region-less
            ),
            FileRowState(
                uri = "content://test/mismatch.mkv",
                displayName = "mismatch.mkv",
                sizeBytes = 100L,
                isMkv = true,
                audioTracks = listOf(createTrack(1L, 2, "en-GB")) // GB vs US
            ),
            FileRowState(
                uri = "content://test/match.mkv",
                displayName = "match.mkv",
                sizeBytes = 100L,
                isMkv = true,
                audioTracks = listOf(createTrack(1L, 2, "en-US")) // US vs US
            )
        )

        val results = BatchMatcher.match(files, p, "content://seed.mkv")

        // region_less.mkv: region mismatch (US vs —)
        assertEquals(RowState.UNCHECKED, results[0].state)
        assertEquals("region mismatch (US vs —)", results[0].reason)

        // mismatch.mkv: region mismatch (US vs GB)
        assertEquals(RowState.UNCHECKED, results[1].state)
        assertEquals("region mismatch (US vs GB)", results[1].reason)

        // match.mkv: MATCHED
        assertEquals(RowState.MATCHED, results[2].state)
        assertNull(results[2].reason)
    }

    @Test
    fun testBatchMatcherRegionWildcard() {
        // Seed has no region -> acts as wildcard. All eng candidates match.
        val p = audioOnlyPref("eng")

        val files = listOf(
            FileRowState(
                uri = "content://test/region_less.mkv",
                displayName = "region_less.mkv",
                sizeBytes = 100L,
                isMkv = true,
                audioTracks = listOf(createTrack(1L, 2, "eng"))
            ),
            FileRowState(
                uri = "content://test/us.mkv",
                displayName = "us.mkv",
                sizeBytes = 100L,
                isMkv = true,
                audioTracks = listOf(createTrack(1L, 2, "en-US"))
            )
        )

        val results = BatchMatcher.match(files, p, "content://seed.mkv")
        assertEquals(RowState.MATCHED, results[0].state)
        assertEquals(RowState.MATCHED, results[1].state)
    }

    @Test
    fun testBatchMatcherCommentaryGuard() {
        val p = audioOnlyPref("eng")

        val files = listOf(
            FileRowState(
                uri = "content://test/comm.mkv",
                displayName = "comm.mkv",
                sizeBytes = 100L,
                isMkv = true,
                audioTracks = listOf(createTrack(1L, 2, "eng", name = "Audio Commentary"))
            )
        )

        val results = BatchMatcher.match(files, p, "content://seed.mkv")
        val res = results[0]
        assertEquals(RowState.PARTIAL_NEEDS_REVIEW, res.state)
        assertEquals("only audio candidate is commentary — needs review", res.reason)
        assertTrue(res.matched)
    }

    @Test
    fun testBatchMatcherLadderAlreadyDefault() {
        val p = audioOnlyPref("eng")

        val files = listOf(
            FileRowState(
                uri = "content://test/ladder.mkv",
                displayName = "ladder.mkv",
                sizeBytes = 100L,
                isMkv = true,
                audioTracks = listOf(
                    createTrack(1L, 2, "eng", flagDefault = 0),
                    createTrack(2L, 2, "eng", flagDefault = 1) // already-default
                )
            )
        )

        val results = BatchMatcher.match(files, p, "content://seed.mkv")
        val res = results[0]
        assertEquals(RowState.MATCHED, res.state)
        assertEquals(2L, res.resolvedSpec?.defaultAudioTrackNumber)
        assertEquals("kept the file's existing default audio track", res.note)
    }

    // ======================================================================
    // NEW: und (untagged) dimension tests
    // ======================================================================

    @Test
    fun untaggedAudioSingleTrackApplies() {
        // Seed has und audio (only track). File also has only und audio.
        // und seed is audioActive=true, und matches und — MATCHED.
        val p = Preference(
            audioActive = true, defaultAudioLang = "und", defaultAudioRegion = null,
            subActive = false, subNone = false, defaultSubLang = null, defaultSubRegion = null,
            forcedSub = false
        )

        val files = listOf(
            FileRowState(
                uri = "content://test/und_file.mkv",
                displayName = "und_file.mkv",
                sizeBytes = 100L,
                isMkv = true,
                audioTracks = listOf(createTrack(1L, 2, "und", flagDefault = 1))
            )
        )

        val results = BatchMatcher.match(files, p, "content://seed.mkv")
        val res = results[0]
        assertEquals(RowState.MATCHED, res.state)
        assertEquals(1L, res.resolvedSpec?.defaultAudioTrackNumber)
    }

    @Test
    fun untaggedAudioMultiCandidateNeedsReview() {
        // File has two und audio tracks with no default anchor.
        // Ambiguity guard fires → PARTIAL_NEEDS_REVIEW.
        val p = Preference(
            audioActive = true, defaultAudioLang = "und", defaultAudioRegion = null,
            subActive = false, subNone = false, defaultSubLang = null, defaultSubRegion = null,
            forcedSub = false
        )

        val files = listOf(
            FileRowState(
                uri = "content://test/two_und.mkv",
                displayName = "two_und.mkv",
                sizeBytes = 100L,
                isMkv = true,
                audioTracks = listOf(
                    createTrack(1L, 2, "und", flagDefault = 0),
                    createTrack(2L, 2, "und", flagDefault = 0)
                )
            )
        )

        val results = BatchMatcher.match(files, p, "content://seed.mkv")
        val res = results[0]
        assertEquals(RowState.PARTIAL_NEEDS_REVIEW, res.state)
        assertTrue(res.reason?.contains("multiple") == true)
    }

    @Test
    fun namedLanguageDoesNotMatchUndTrack() {
        // Named pref "eng" must NOT match "und" tracks (fail-closed).
        val p = audioOnlyPref("eng")

        val files = listOf(
            FileRowState(
                uri = "content://test/und_only.mkv",
                displayName = "und_only.mkv",
                sizeBytes = 100L,
                isMkv = true,
                audioTracks = listOf(createTrack(1L, 2, "und", flagDefault = 1))
            )
        )

        val results = BatchMatcher.match(files, p, "content://seed.mkv")
        assertEquals(RowState.UNCHECKED, results[0].state)
        assertEquals("no eng audio", results[0].reason)
    }

    @Test
    fun keepCurrentAudioDimensionPreservesCurrentDefault() {
        // audioActive = false (KeepCurrent) → preserves file's existing default audio.
        // subActive = true → matches fra subtitle.
        val candidateUri = "content://test/keep_audio.mkv"
        val p = Preference(
            audioActive = false, defaultAudioLang = "und", defaultAudioRegion = null,
            subActive = true, subNone = false, defaultSubLang = "fra", defaultSubRegion = null,
            forcedSub = false
        )

        val files = listOf(
            FileRowState(
                uri = candidateUri,
                displayName = "keep_audio.mkv",
                sizeBytes = 100L,
                isMkv = true,
                audioTracks = listOf(
                    createTrack(10L, 2, "eng", flagDefault = 1)  // current default audio
                ),
                subtitleTracks = listOf(
                    createTrack(20L, 17, "fra", flagDefault = 1)
                ),
                currentSpec = EditSpec(10L, null, false)
            )
        )

        val results = BatchMatcher.match(files, p, "content://seed.mkv")
        val res = results[0]
        assertEquals(RowState.MATCHED, res.state)
        assertEquals(10L, res.resolvedSpec?.defaultAudioTrackNumber)  // preserved
        assertEquals(20L, res.resolvedSpec?.defaultSubtitleTrackNumber)  // fra matched
    }

    @Test
    fun keepCurrentSubDimensionPreservesCurrentDefault() {
        // subActive = false (KeepCurrent subtitle) → preserves file's current default subtitle.
        val candidateUri = "content://test/keep_sub.mkv"
        val p = Preference(
            audioActive = true, defaultAudioLang = "eng", defaultAudioRegion = null,
            subActive = false, subNone = false, defaultSubLang = null, defaultSubRegion = null,
            forcedSub = false
        )

        val files = listOf(
            FileRowState(
                uri = candidateUri,
                displayName = "keep_sub.mkv",
                sizeBytes = 100L,
                isMkv = true,
                audioTracks = listOf(createTrack(1L, 2, "eng", flagDefault = 1)),
                subtitleTracks = listOf(createTrack(5L, 17, "fra", flagDefault = 1)),
                currentSpec = EditSpec(1L, 5L, false)
            )
        )

        val results = BatchMatcher.match(files, p, "content://seed.mkv")
        val res = results[0]
        assertEquals(RowState.MATCHED, res.state)
        assertEquals(1L, res.resolvedSpec?.defaultAudioTrackNumber)
        assertEquals(5L, res.resolvedSpec?.defaultSubtitleTrackNumber)  // preserved
    }

    @Test
    fun subNoneSetsClearSubtitle() {
        // subNone = true → resolvedSpec.defaultSubtitleTrackNumber must be null.
        val p = Preference(
            audioActive = true, defaultAudioLang = "eng", defaultAudioRegion = null,
            subActive = false, subNone = true, defaultSubLang = null, defaultSubRegion = null,
            forcedSub = false
        )

        val files = listOf(
            FileRowState(
                uri = "content://test/nosub.mkv",
                displayName = "nosub.mkv",
                sizeBytes = 100L,
                isMkv = true,
                audioTracks = listOf(createTrack(1L, 2, "eng", flagDefault = 1)),
                subtitleTracks = listOf(createTrack(2L, 17, "fra", flagDefault = 1)),
                currentSpec = EditSpec(1L, 2L, false)
            )
        )

        val results = BatchMatcher.match(files, p, "content://seed.mkv")
        val res = results[0]
        assertEquals(RowState.MATCHED, res.state)
        assertNull(res.resolvedSpec?.defaultSubtitleTrackNumber)
        assertFalse(res.resolvedSpec?.forcedSubtitle == true)
    }

    @Test
    fun multiCandidateWithDefaultAnchorIsMatched() {
        // Two eng audio tracks, one is flagDefault=1 → resolved via Rung 1.
        // Ambiguity guard does NOT fire (there IS a default anchor).
        val p = audioOnlyPref("eng")

        val files = listOf(
            FileRowState(
                uri = "content://test/anchor.mkv",
                displayName = "anchor.mkv",
                sizeBytes = 100L,
                isMkv = true,
                audioTracks = listOf(
                    createTrack(1L, 2, "eng", flagDefault = 0),
                    createTrack(2L, 2, "eng", flagDefault = 1)
                )
            )
        )

        val results = BatchMatcher.match(files, p, "content://seed.mkv")
        val res = results[0]
        assertEquals(RowState.MATCHED, res.state)
        assertEquals(2L, res.resolvedSpec?.defaultAudioTrackNumber)
    }

    @Test
    fun multiCandidateNoDefaultAnchorIsPartialReview() {
        // Two eng audio tracks, neither flagDefault=1 → PARTIAL_NEEDS_REVIEW (ambiguity guard).
        val p = audioOnlyPref("eng")

        val files = listOf(
            FileRowState(
                uri = "content://test/ambiguous.mkv",
                displayName = "ambiguous.mkv",
                sizeBytes = 100L,
                isMkv = true,
                audioTracks = listOf(
                    createTrack(1L, 2, "eng", flagDefault = 0),
                    createTrack(2L, 2, "eng", flagDefault = 0)
                )
            )
        )

        val results = BatchMatcher.match(files, p, "content://seed.mkv")
        val res = results[0]
        assertEquals(RowState.PARTIAL_NEEDS_REVIEW, res.state)
        assertTrue(res.reason?.contains("multiple eng audio") == true)
    }

    // ======================================================================
    // NEW: Rung-0 POSITIONAL tie-breaker tests
    // ======================================================================

    @Test
    fun positionalAudioPicksExactTrackOverDefault() {
        // Seed chose Track 2 (KOR). Candidate has Track 1 (KOR, flagDefault=1) + Track 2 (KOR).
        // Rung 0 should pick Track 2 — overriding the flagDefault=1 Rung 1 result.
        val p = Preference(
            audioActive = true, defaultAudioLang = "kor", defaultAudioRegion = null,
            subActive = false, subNone = false, defaultSubLang = null, defaultSubRegion = null,
            forcedSub = false,
            seedAudioTrackNumber = 2L,
            seedSubTrackNumber = null
        )

        val files = listOf(
            FileRowState(
                uri = "content://test/ep2.mkv",
                displayName = "ep2.mkv",
                sizeBytes = 100L,
                isMkv = true,
                audioTracks = listOf(
                    createTrack(1L, 2, "kor", flagDefault = 1),  // would win Rung 1
                    createTrack(2L, 2, "kor", flagDefault = 0)   // positional winner
                )
            )
        )

        val results = BatchMatcher.match(files, p, "content://seed.mkv")
        val res = results[0]
        assertEquals(RowState.MATCHED, res.state)
        assertEquals(2L, res.resolvedSpec?.defaultAudioTrackNumber)
        assertNull(res.note) // no note when positional resolves
    }

    @Test
    fun positionalAudioPicksCorrectWhenNoDefault() {
        // Seed chose Track 4 (KOR). Candidate has Track 3 (KOR) + Track 4 (KOR), neither default.
        // Without Rung 0, both survive all rungs → AMBIGUITY (PARTIAL_NEEDS_REVIEW).
        // With Rung 0, Track 4 is selected immediately → MATCHED.
        val p = Preference(
            audioActive = true, defaultAudioLang = "kor", defaultAudioRegion = null,
            subActive = false, subNone = false, defaultSubLang = null, defaultSubRegion = null,
            forcedSub = false,
            seedAudioTrackNumber = 4L,
            seedSubTrackNumber = null
        )

        val files = listOf(
            FileRowState(
                uri = "content://test/same_layout.mkv",
                displayName = "same_layout.mkv",
                sizeBytes = 100L,
                isMkv = true,
                audioTracks = listOf(
                    createTrack(3L, 2, "kor", flagDefault = 0),
                    createTrack(4L, 2, "kor", flagDefault = 0)
                )
            )
        )

        val results = BatchMatcher.match(files, p, "content://seed.mkv")
        val res = results[0]
        assertEquals(RowState.MATCHED, res.state)
        assertEquals(4L, res.resolvedSpec?.defaultAudioTrackNumber)
    }

    @Test
    fun positionalMissingFallsToAlreadyDefault() {
        // Seed chose Track 99 (not present in candidate). Rung 0 falls through.
        // Candidate has Track 1 (KOR, flagDefault=1) + Track 2 (KOR). Rung 1 picks Track 1.
        val p = Preference(
            audioActive = true, defaultAudioLang = "kor", defaultAudioRegion = null,
            subActive = false, subNone = false, defaultSubLang = null, defaultSubRegion = null,
            forcedSub = false,
            seedAudioTrackNumber = 99L,
            seedSubTrackNumber = null
        )

        val files = listOf(
            FileRowState(
                uri = "content://test/fallback.mkv",
                displayName = "fallback.mkv",
                sizeBytes = 100L,
                isMkv = true,
                audioTracks = listOf(
                    createTrack(1L, 2, "kor", flagDefault = 1),
                    createTrack(2L, 2, "kor", flagDefault = 0)
                )
            )
        )

        val results = BatchMatcher.match(files, p, "content://seed.mkv")
        val res = results[0]
        assertEquals(RowState.MATCHED, res.state)
        assertEquals(1L, res.resolvedSpec?.defaultAudioTrackNumber) // Rung 1
        assertEquals("kept the file's existing default audio track", res.note)
    }

    @Test
    fun positionalSubtitlePicksExactTrack() {
        // Seed chose Sub Track 6 (KOR). Candidate has Track 5 (KOR, flagDefault=1) + Track 6 (KOR).
        // Rung 0 selects Track 6 for the subtitle dimension.
        val p = Preference(
            audioActive = true, defaultAudioLang = "eng", defaultAudioRegion = null,
            subActive = true, subNone = false, defaultSubLang = "kor", defaultSubRegion = null,
            forcedSub = false,
            seedAudioTrackNumber = null,
            seedSubTrackNumber = 6L
        )

        val files = listOf(
            FileRowState(
                uri = "content://test/sub_ep.mkv",
                displayName = "sub_ep.mkv",
                sizeBytes = 100L,
                isMkv = true,
                audioTracks = listOf(createTrack(1L, 2, "eng", flagDefault = 1)),
                subtitleTracks = listOf(
                    createTrack(5L, 17, "kor", flagDefault = 1),  // would win Rung 1
                    createTrack(6L, 17, "kor", flagDefault = 0)   // positional winner
                )
            )
        )

        val results = BatchMatcher.match(files, p, "content://seed.mkv")
        val res = results[0]
        assertEquals(RowState.MATCHED, res.state)
        assertEquals(6L, res.resolvedSpec?.defaultSubtitleTrackNumber)
        assertFalse(res.resolvedSpec?.forcedSubtitle == true)
    }

    @Test
    fun positionalBothAudioAndSubResolved() {
        // Seed chose Audio Track 4 (KOR) + Sub Track 7 (KOR). Same-layout pack.
        // Both dimensions resolved positionally: no AMBIGUITY, no PARTIAL_NEEDS_REVIEW.
        val p = Preference(
            audioActive = true, defaultAudioLang = "kor", defaultAudioRegion = null,
            subActive = true, subNone = false, defaultSubLang = "kor", defaultSubRegion = null,
            forcedSub = false,
            seedAudioTrackNumber = 4L,
            seedSubTrackNumber = 7L
        )

        val files = listOf(
            FileRowState(
                uri = "content://test/both_positional.mkv",
                displayName = "both_positional.mkv",
                sizeBytes = 100L,
                isMkv = true,
                audioTracks = listOf(
                    createTrack(3L, 2, "kor", flagDefault = 0),
                    createTrack(4L, 2, "kor", flagDefault = 0)
                ),
                subtitleTracks = listOf(
                    createTrack(6L, 17, "kor", flagDefault = 0),
                    createTrack(7L, 17, "kor", flagDefault = 0)
                )
            )
        )

        val results = BatchMatcher.match(files, p, "content://seed.mkv")
        val res = results[0]
        assertEquals(RowState.MATCHED, res.state)
        assertEquals(4L, res.resolvedSpec?.defaultAudioTrackNumber)
        assertEquals(7L, res.resolvedSpec?.defaultSubtitleTrackNumber)
    }
}
