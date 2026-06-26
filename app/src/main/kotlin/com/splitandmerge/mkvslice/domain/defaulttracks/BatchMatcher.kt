package com.splitandmerge.mkvslice.domain.defaulttracks

import com.splitandmerge.mkvslice.domain.defaulttracks.model.EditSpec
import com.splitandmerge.mkvslice.domain.defaulttracks.model.TrackInfo
import com.splitandmerge.mkvslice.ui.defaulttracks.FileRowState
import java.util.Locale

// ---------- Dimension choices (UI/VM concept resolved to concrete EditSpec) ----------

sealed class AudioChoice {
    object KeepCurrent : AudioChoice()
    data class Track(val trackNumber: Long) : AudioChoice()
}

sealed class SubChoice {
    object KeepCurrent : SubChoice()
    object NoneSub : SubChoice()
    data class Track(val trackNumber: Long) : SubChoice()
}

// ---------- Preference: active dimensions only ----------

data class Preference(
    val audioActive: Boolean,         // seed chose a specific audio track
    val defaultAudioLang: String,     // normalized; meaningful only if audioActive
    val defaultAudioRegion: String?,  // null if seed audio is region-less
    val subActive: Boolean,           // seed chose a specific subtitle track
    val subNone: Boolean,             // seed explicitly chose "None" subtitle (active, not KeepCurrent)
    val defaultSubLang: String?,      // meaningful only if subActive
    val defaultSubRegion: String?,
    val forcedSub: Boolean,           // meaningful only when subActive
    // Rung 0 — POSITIONAL: seed's exact track numbers (null = positional not applicable)
    val seedAudioTrackNumber: Long? = null,
    val seedSubTrackNumber: Long? = null
)

enum class RowState {
    MATCHED,
    UNCHECKED,
    PARTIAL_NEEDS_REVIEW
}

data class MatchResult(
    val uri: String,
    val matched: Boolean,
    val state: RowState,
    val reason: String?,
    val resolvedSpec: EditSpec?,
    val note: String?
)

object BatchMatcher {

    // Frozen de-prioritization tokens — matches commentary, SDH, descriptions
    private val DEPRIORITIZE_TOKENS = setOf("commentary", "sdh", "description", "descriptive")

    fun match(files: List<FileRowState>, p: Preference, seedUri: String): List<MatchResult> {
        return files.map { f ->
            // Seed always MATCHED with its own spec
            if (f.uri == seedUri) {
                val spec = f.chosenSpec ?: f.currentSpec ?: EditSpec(0, null, false)
                return@map MatchResult(f.uri, true, RowState.MATCHED, null, spec, null)
            }

            if (!f.isMkv) {
                val reasonText = if (f.reason == "not-mkv") "Non-MKV" else "skipped"
                return@map MatchResult(f.uri, false, RowState.UNCHECKED, reasonText, null, null)
            }

            if (f.status == "SKIPPED" && (f.reason.startsWith("analyze-failed") || f.reason.startsWith("read-failed"))) {
                return@map MatchResult(f.uri, false, RowState.UNCHECKED, "analysis failed", null, null)
            }

            // ---------- AUDIO CHECK (active dimension only) ----------
            val audioSameRegion: List<TrackInfo>
            if (p.audioActive) {
                val audioSameLang = f.audioTracks.filter {
                    LanguageNormaliser.normalizeLang(it.language) == p.defaultAudioLang
                }
                if (audioSameLang.isEmpty()) {
                    return@map MatchResult(f.uri, false, RowState.UNCHECKED, "no ${p.defaultAudioLang} audio", null, null)
                }
                val regionFiltered = audioSameLang.filter { regionMatches(it, p.defaultAudioRegion) }
                if (regionFiltered.isEmpty()) {
                    val firstCandidateRegion = LanguageNormaliser.extractRegion(audioSameLang.first().language)
                        ?.uppercase(Locale.ROOT) ?: "—"
                    val seedRegion = p.defaultAudioRegion?.uppercase(Locale.ROOT) ?: "—"
                    return@map MatchResult(f.uri, false, RowState.UNCHECKED,
                        "region mismatch ($seedRegion vs $firstCandidateRegion)", null, null)
                }
                audioSameRegion = regionFiltered
            } else {
                audioSameRegion = emptyList()
            }

            // ---------- SUBTITLE CHECK (active dimension only) ----------
            val subSameRegion: List<TrackInfo>
            if (p.subActive) {
                val subSameLang = f.subtitleTracks.filter {
                    LanguageNormaliser.normalizeLang(it.language) == p.defaultSubLang
                }
                if (subSameLang.isEmpty()) {
                    return@map MatchResult(f.uri, false, RowState.UNCHECKED, "no ${p.defaultSubLang} subtitle", null, null)
                }
                val regionFiltered = subSameLang.filter { regionMatches(it, p.defaultSubRegion) }
                if (regionFiltered.isEmpty()) {
                    val firstCandidateRegion = LanguageNormaliser.extractRegion(subSameLang.first().language)
                        ?.uppercase(Locale.ROOT) ?: "—"
                    val seedRegion = p.defaultSubRegion?.uppercase(Locale.ROOT) ?: "—"
                    return@map MatchResult(f.uri, false, RowState.UNCHECKED,
                        "region mismatch ($seedRegion vs $firstCandidateRegion)", null, null)
                }
                subSameRegion = regionFiltered
            } else {
                // subNone → subOk but no candidates; KeepCurrent → also no active candidates
                subSameRegion = emptyList()
            }

            // ---------- RESOLVE AUDIO ----------
            val resolvedAudioTrackNumber: Long
            var audioNote: String? = null

            if (p.audioActive) {
                // COMMENTARY GUARD (sole commentary — no non-commentary fallback)
                if (audioSameRegion.size == 1 && isCommentaryTrack(audioSameRegion.first())) {
                    return@map MatchResult(
                        uri = f.uri, matched = true,
                        state = RowState.PARTIAL_NEEDS_REVIEW,
                        reason = "only audio candidate is commentary — needs review",
                        resolvedSpec = null, note = null
                    )
                }
                val (track, note) = runLadder(audioSameRegion, false, p, p.seedAudioTrackNumber)
                if (track == null) {
                    // AMBIGUITY GUARD: multiple survivors after ladder, no default anchor
                    return@map MatchResult(
                        uri = f.uri, matched = true,
                        state = RowState.PARTIAL_NEEDS_REVIEW,
                        reason = "multiple ${p.defaultAudioLang} audio tracks — needs review",
                        resolvedSpec = null, note = null
                    )
                }
                resolvedAudioTrackNumber = track.trackNumber
                audioNote = note
            } else {
                // KeepCurrent: preserve file's current default audio
                resolvedAudioTrackNumber = f.audioTracks.find { it.flagDefault == 1 }?.trackNumber
                    ?: f.audioTracks.firstOrNull()?.trackNumber
                    ?: f.currentSpec?.defaultAudioTrackNumber
                    ?: 0L
            }

            // ---------- RESOLVE SUBTITLE ----------
            val resolvedSubTrackNumber: Long?
            val resolvedForced: Boolean
            var subNote: String? = null

            when {
                p.subNone -> {
                    // Explicit "no default subtitle"
                    resolvedSubTrackNumber = null
                    resolvedForced = false
                }
                p.subActive -> {
                    // COMMENTARY GUARD (sole commentary subtitle)
                    if (subSameRegion.size == 1 && isCommentaryTrack(subSameRegion.first())) {
                        var noteText = "chosen subtitle looks like commentary/SDH — verify"
                        resolvedSubTrackNumber = subSameRegion.first().trackNumber
                        resolvedForced = p.forcedSub
                        subNote = noteText
                    } else {
                        val (track, note) = runLadder(subSameRegion, true, p, p.seedSubTrackNumber)
                        if (track == null) {
                            // AMBIGUITY GUARD: multiple survivors after ladder
                            return@map MatchResult(
                                uri = f.uri, matched = true,
                                state = RowState.PARTIAL_NEEDS_REVIEW,
                                reason = "multiple ${p.defaultSubLang} subtitle tracks — needs review",
                                resolvedSpec = null, note = null
                            )
                        }
                        resolvedSubTrackNumber = track.trackNumber
                        resolvedForced = p.forcedSub
                        subNote = note
                    }
                }
                else -> {
                    // KeepCurrent subtitle: preserve file's current default subtitle + forced state
                    val currentDefaultSub = f.subtitleTracks.find { it.flagDefault == 1 }
                    resolvedSubTrackNumber = currentDefaultSub?.trackNumber
                        ?: f.currentSpec?.defaultSubtitleTrackNumber
                    resolvedForced = currentDefaultSub?.let { it.flagForced == 1 }
                        ?: f.currentSpec?.forcedSubtitle
                        ?: false
                }
            }

            val resolvedSpec = EditSpec(
                defaultAudioTrackNumber = resolvedAudioTrackNumber,
                defaultSubtitleTrackNumber = resolvedSubTrackNumber,
                forcedSubtitle = resolvedForced
            )

            MatchResult(
                uri = f.uri,
                matched = true,
                state = RowState.MATCHED,
                reason = null,
                resolvedSpec = resolvedSpec,
                note = audioNote ?: subNote
            )
        }
    }

    private fun regionMatches(track: TrackInfo, prefRegion: String?): Boolean {
        if (prefRegion == null) return true
        val trackRegion = LanguageNormaliser.extractRegion(track.language) ?: return false
        return trackRegion.equals(prefRegion, ignoreCase = true)
    }

    private fun isCommentaryTrack(track: TrackInfo): Boolean {
        val nameLower = (track.name ?: "").lowercase(Locale.ROOT)
        return DEPRIORITIZE_TOKENS.any { token -> nameLower.contains(token) }
    }

    private fun runLadder(
        candidates: List<TrackInfo>,
        isSubtitle: Boolean,
        pref: Preference,
        seedTrackNumber: Long? = null
    ): Pair<TrackInfo?, String?> {
        // Rung 0: POSITIONAL — seed's chosen track number present in candidate → pick it immediately
        if (seedTrackNumber != null) {
            val positional = candidates.find { it.trackNumber == seedTrackNumber }
            if (positional != null) {
                return Pair(positional, null)
            }
        }

        // Rung 1: ALREADY-DEFAULT
        val defaults = candidates.filter { it.flagDefault == 1 }
        if (defaults.size == 1) {
            val typeLabel = if (isSubtitle) "subtitle" else "audio"
            val note = if (candidates.size > 1) "kept the file's existing default $typeLabel track" else null
            return Pair(defaults.first(), note)
        }

        // Rung 2: FORCED-ALIGNMENT (subtitles only)
        var survivors = candidates
        if (isSubtitle) {
            if (pref.forcedSub) {
                val forcedOnly = candidates.filter { it.flagForced == 1 }
                if (forcedOnly.isNotEmpty()) survivors = forcedOnly
            } else {
                val nonForced = candidates.filter { it.flagForced != 1 }
                if (nonForced.isNotEmpty()) survivors = nonForced
            }
        }

        // Rung 3: COMMENTARY/SDH DE-PRIORITIZATION
        val nonCommentary = survivors.filter { candidate ->
            val nameLower = (candidate.name ?: "").lowercase(Locale.ROOT)
            DEPRIORITIZE_TOKENS.none { token -> nameLower.contains(token) }
        }
        if (nonCommentary.isNotEmpty()) survivors = nonCommentary

        // Rung 4: FIRST in document order — only if exactly one survivor remains
        // Multiple survivors here → ambiguous, signal caller with null
        if (survivors.size > 1) return Pair(null, null)
        return Pair(survivors.first(), null)
    }
}
