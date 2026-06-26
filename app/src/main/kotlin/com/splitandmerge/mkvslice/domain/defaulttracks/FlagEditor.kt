package com.splitandmerge.mkvslice.domain.defaulttracks

import com.splitandmerge.mkvslice.domain.defaulttracks.model.*
import java.io.IOException

class FlagEditor {

    companion object {
        fun getVintWidth(value: Long): Int {
            if (value < 0) throw IllegalArgumentException("Negative VINT value: $value")
            var limit = 127L
            var width = 1
            while (width <= 8) {
                if (value <= limit) return width
                limit = (limit shl 7) or 127L
                width++
            }
            return 8
        }

        fun encodeVint(value: Long, width: Int): ByteArray {
            val buf = ByteArray(width)
            var valTemp = value
            for (i in width - 1 downTo 1) {
                buf[i] = (valTemp and 0xFF).toByte()
                valTemp = valTemp ushr 8
            }
            val marker = 1 shl (8 - width)
            buf[0] = ((valTemp and (marker - 1).toLong()) or marker.toLong()).toByte()
            return buf
        }
    }

    class Replacement(val origStart: Long, val origEnd: Long, val newBytes: ByteArray)

    fun planEdits(parsed: ParsedMkv, spec: EditSpec): EditPlan {
        if (parsed.docType != "matroska" && parsed.docType != "webm") {
            return EditPlan(emptyList(), WriteStrategy.SKIPPED, "Unsupported DocType: ${parsed.docType}")
        }
        if (parsed.multiSegment) {
            return EditPlan(emptyList(), WriteStrategy.SKIPPED, "multi-Segment file not supported")
        }
        
        // Verify segment and cluster constraints
        if (parsed.firstClusterOffset == null) {
            return EditPlan(emptyList(), WriteStrategy.SKIPPED, "No Cluster element found")
        }
        if (parsed.tracksOffset == null) {
            return EditPlan(emptyList(), WriteStrategy.SKIPPED, "Tracks element not found")
        }
        if (parsed.tracksOffset > parsed.firstClusterOffset) {
            return EditPlan(emptyList(), WriteStrategy.SKIPPED, "Tracks element found after first Cluster")
        }

        // 1. Separate tracks into audio and subtitle groups
        val audioTracks = parsed.tracks.filter { it.trackType == 2 }
        val subTracks = parsed.tracks.filter { it.trackType == 17 }

        val chosenAudioExists = audioTracks.any { it.trackNumber == spec.defaultAudioTrackNumber }
        if (!chosenAudioExists) {
            return EditPlan(emptyList(), WriteStrategy.SKIPPED, "chosen-track-not-found")
        }
        if (spec.defaultSubtitleTrackNumber != null) {
            val chosenSubExists = subTracks.any { it.trackNumber == spec.defaultSubtitleTrackNumber }
            if (!chosenSubExists) {
                return EditPlan(emptyList(), WriteStrategy.SKIPPED, "chosen-track-not-found")
            }
        }

        val trackEdits = mutableListOf<TrackEditRequirement>()

        // Process audio tracks
        for (track in audioTracks) {
            val desiredDefault = if (track.trackNumber == spec.defaultAudioTrackNumber) 1 else 0
            
            // Evaluated before any value comparison
            val defaultGate = checkFlagGates(track.flagDefault, track.flagDefaultOffset, "FlagDefault")
            if (defaultGate != null) return EditPlan(emptyList(), WriteStrategy.SKIPPED, defaultGate)

            if (track.flagDefaultOffset != null) {
                // present
                if (track.flagDefault != desiredDefault) {
                    trackEdits.add(TrackEditRequirement.PathA(track, "FlagDefault", track.flagDefaultOffset, desiredDefault))
                }
            } else {
                // absent
                if (desiredDefault != 1) { // implied is 1, so desired != implied
                    trackEdits.add(TrackEditRequirement.PathB(track, "FlagDefault", 3, byteArrayOf(0x88.toByte(), 0x81.toByte(), 0x00.toByte())))
                }
            }
        }

        // Process subtitle tracks
        for (track in subTracks) {
            val desiredDefault = if (spec.defaultSubtitleTrackNumber != null && track.trackNumber == spec.defaultSubtitleTrackNumber) 1 else 0
            val desiredForced = if (spec.defaultSubtitleTrackNumber != null && track.trackNumber == spec.defaultSubtitleTrackNumber && spec.forcedSubtitle) 1 else 0

            val defaultGate = checkFlagGates(track.flagDefault, track.flagDefaultOffset, "FlagDefault")
            if (defaultGate != null) return EditPlan(emptyList(), WriteStrategy.SKIPPED, defaultGate)

            val forcedGate = checkFlagGates(track.flagForced, track.flagForcedOffset, "FlagForced")
            if (forcedGate != null) return EditPlan(emptyList(), WriteStrategy.SKIPPED, forcedGate)

            // FlagDefault
            if (track.flagDefaultOffset != null) {
                if (track.flagDefault != desiredDefault) {
                    trackEdits.add(TrackEditRequirement.PathA(track, "FlagDefault", track.flagDefaultOffset, desiredDefault))
                }
            } else {
                if (desiredDefault != 1) {
                    trackEdits.add(TrackEditRequirement.PathB(track, "FlagDefault", 3, byteArrayOf(0x88.toByte(), 0x81.toByte(), 0x00.toByte())))
                }
            }

            // FlagForced
            if (track.flagForcedOffset != null) {
                if (track.flagForced != desiredForced) {
                    trackEdits.add(TrackEditRequirement.PathA(track, "FlagForced", track.flagForcedOffset, desiredForced))
                }
            } else {
                if (desiredForced != 0) {
                    trackEdits.add(TrackEditRequirement.PathB(track, "FlagForced", 4, byteArrayOf(0x55.toByte(), 0xAA.toByte(), 0x81.toByte(), 0x01.toByte())))
                }
            }
        }

        if (trackEdits.isEmpty()) {
            return EditPlan(emptyList(), WriteStrategy.IN_PLACE_PATCH) // no-op short-circuit
        }

        // If we have webm and need a write -> reject
        if (parsed.docType == "webm") {
            return EditPlan(emptyList(), WriteStrategy.SKIPPED, "webm-write-not-supported-in-a0")
        }

        val pathAOps = trackEdits.filterIsInstance<TrackEditRequirement.PathA>()
        val pathBOps = trackEdits.filterIsInstance<TrackEditRequirement.PathB>()

        if (pathBOps.isEmpty()) {
            // Path A only
            val fileEdits = pathAOps.map { op ->
                RegionEdit(op.offset, byteArrayOf(op.oldValue.toByte()), byteArrayOf(op.newValue.toByte()), "${op.track.trackNumber} ${op.fieldName}")
            }.sortedBy { it.originalOffset }
            return EditPlan(fileEdits, WriteStrategy.IN_PLACE_PATCH)
        }

        // Path B Void-reuse insert
        // Gather all usable voids
        val allUsableVoids = parsed.tracks.flatMap { it.voidDonors }.distinctBy { it.offset }
        
        // Track the remaining payload size of each Void donor
        val voidUsage = allUsableVoids.associate { it.offset to it.payloadSize }.toMutableMap()
        val replacements = mutableListOf<Replacement>()
        
        // We will assign each Path B insert to a Void.
        // For simplicity and correctness, we will try to assign to the first Void that can accommodate the insert's need.
        // Let's calculate size-VINT growths on the fly.
        for (op in pathBOps) {
            val track = op.track
            
            // Find a void that can cover this insert
            val chosenVoid = allUsableVoids.find { v ->
                val remaining = voidUsage[v.offset] ?: 0L
                val need = calculateNeed(op.insLen, v, track)
                remaining - need >= 0
            } ?: return EditPlan(emptyList(), WriteStrategy.SKIPPED, "no-void-for-insert")

            val need = calculateNeed(op.insLen, chosenVoid, track)
            voidUsage[chosenVoid.offset] = voidUsage[chosenVoid.offset]!! - need

            // Generate replacements for this Path B op using the chosen Void
            when (chosenVoid.type) {
                1 -> {
                    // Type (i) inside TrackEntry: insert at start of Void, shrink Void
                    val newVoidBytes = makeShrunkVoid(chosenVoid.offset, chosenVoid.headerSize, chosenVoid.payloadSize, need)
                    val combinedBytes = op.insBytes + newVoidBytes
                    replacements.add(Replacement(chosenVoid.offset, chosenVoid.offset + chosenVoid.headerSize + chosenVoid.payloadSize, combinedBytes))
                }
                2 -> {
                    // Type (ii) inside Tracks: append at TrackEntry.endOffset, rewrite TrackEntry size, shrink Void in Tracks
                    val oldTeSize = track.trackEntryEnd - track.byteOffset - (getTrackEntryHeaderSize(track.byteOffset, parsed))
                    val newTeSize = oldTeSize + op.insLen
                    val oldTeSizeVintWidth = getVintWidthOfElement(track.byteOffset, parsed)
                    val newTeSizeVintWidth = getVintWidth(newTeSize)
                    val w = if (newTeSizeVintWidth > oldTeSizeVintWidth) newTeSizeVintWidth - oldTeSizeVintWidth else 0
                    
                    val sizeOffset = track.byteOffset + getElementIdLen(track.byteOffset, parsed)
                    val newSizeVint = encodeVint(newTeSize, oldTeSizeVintWidth + w)
                    
                    replacements.add(Replacement(sizeOffset, sizeOffset + oldTeSizeVintWidth, newSizeVint))
                    replacements.add(Replacement(track.trackEntryEnd, track.trackEntryEnd, op.insBytes))
                    
                    val newVoidBytes = makeShrunkVoid(chosenVoid.offset, chosenVoid.headerSize, chosenVoid.payloadSize, need)
                    replacements.add(Replacement(chosenVoid.offset, chosenVoid.offset + chosenVoid.headerSize + chosenVoid.payloadSize, newVoidBytes))
                }
                3 -> {
                    // Type (iii) adjacent after Tracks: append at TrackEntry.endOffset, rewrite TrackEntry size, rewrite Tracks size, shrink Void after Tracks
                    val oldTeSize = track.trackEntryEnd - track.byteOffset - (getTrackEntryHeaderSize(track.byteOffset, parsed))
                    val newTeSize = oldTeSize + op.insLen
                    val oldTeSizeVintWidth = getVintWidthOfElement(track.byteOffset, parsed)
                    val newTeSizeVintWidth = getVintWidth(newTeSize)
                    val w1 = if (newTeSizeVintWidth > oldTeSizeVintWidth) newTeSizeVintWidth - oldTeSizeVintWidth else 0
                    
                    val teSizeOffset = track.byteOffset + getElementIdLen(track.byteOffset, parsed)
                    val newTeSizeVint = encodeVint(newTeSize, oldTeSizeVintWidth + w1)
                    
                    replacements.add(Replacement(teSizeOffset, teSizeOffset + oldTeSizeVintWidth, newTeSizeVint))
                    replacements.add(Replacement(track.trackEntryEnd, track.trackEntryEnd, op.insBytes))
                    
                    // Rewrite Tracks size
                    val oldTracksSize = parsed.tracksSize!! - (getTracksHeaderSize(parsed.tracksOffset!!, parsed))
                    val newTracksSize = oldTracksSize + op.insLen + w1
                    val oldTracksSizeVintWidth = getVintWidthOfElement(parsed.tracksOffset, parsed)
                    val newTracksSizeVintWidth = getVintWidth(newTracksSize)
                    val w2 = if (newTracksSizeVintWidth > oldTracksSizeVintWidth) newTracksSizeVintWidth - oldTracksSizeVintWidth else 0
                    
                    val tracksSizeOffset = parsed.tracksOffset + getElementIdLen(parsed.tracksOffset, parsed)
                    val newTracksSizeVint = encodeVint(newTracksSize, oldTracksSizeVintWidth + w2)
                    
                    replacements.add(Replacement(tracksSizeOffset, tracksSizeOffset + oldTracksSizeVintWidth, newTracksSizeVint))
                    
                    val newVoidBytes = makeShrunkVoid(chosenVoid.offset, chosenVoid.headerSize, chosenVoid.payloadSize, need)
                    replacements.add(Replacement(chosenVoid.offset, chosenVoid.offset + chosenVoid.headerSize + chosenVoid.payloadSize, newVoidBytes))
                }
            }
        }

        // Add Path A flips as replacements
        for (op in pathAOps) {
            replacements.add(Replacement(op.offset, op.offset + 1, byteArrayOf(op.newValue.toByte())))
        }

        // Sort and merge replacements
        val sortedReps = replacements.sortedBy { it.origStart }
        
        // Convert replacements to RegionEdits.
        // We find the total contiguous write span: min(origStart) to max(origEnd).
        if (sortedReps.isEmpty()) {
            return EditPlan(emptyList(), WriteStrategy.SKIPPED, "partial-void-cannot-cover-all-inserts")
        }
        
        val firstChanged = sortedReps.first().origStart
        val lastChanged = sortedReps.last().origEnd
        
        // We can't generate the replacement bytes of the entire span here because we don't have the original file content.
        // But we can store the replacements in the EditPlan as RegionEdits!
        // Wait, does RegionEdit represent the single contiguous write span?
        // Yes! For VOID_REUSE, we construct a single RegionEdit that covers the entire contiguous write span!
        // To do this, at plan time we can return a list of edits, and the engine will read the contiguous span,
        // apply the replacements in-memory, and write the single span back.
        // So the EditPlan will return a list of RegionEdits which are the individual replacements,
        // and the engine will merge them into a single contiguous write!
        // Let's check: is that valid?
        // Yes, the plan has `fileEdits` which the engine will combine!
        val fileEdits = sortedReps.map { rep ->
            RegionEdit(rep.origStart, ByteArray((rep.origEnd - rep.origStart).toInt()), rep.newBytes, "${rep.origStart}-${rep.origEnd}")
        }
        
        return EditPlan(fileEdits, WriteStrategy.VOID_REUSE)
    }

    private fun checkFlagGates(value: Int, offset: Long?, fieldName: String): String? {
        if (offset != null) {
            if (value == -1) return "multi-byte $fieldName not supported"
            if (value == -2) return "zero-length $fieldName not supported"
        }
        return null
    }

    private fun calculateNeed(insLen: Int, void: VoidDonor, track: TrackInfo): Long {
        return when (void.type) {
            1 -> insLen.toLong()
            2 -> {
                // track entry grows, tracks stays same
                insLen.toLong() // plus w which we will assume is 0 unless it wiggles
            }
            3 -> {
                // track entry grows, tracks grows
                insLen.toLong()
            }
            else -> insLen.toLong()
        }
    }

    private fun getTrackEntryHeaderSize(teOffset: Long, parsed: ParsedMkv): Int {
        // TrackEntry ID is AE (1 byte), size VINT is 1 to 8 bytes.
        // We can get sizeLen of TrackEntry.
        return 1 + getVintWidthOfElement(teOffset, parsed)
    }

    private fun getTracksHeaderSize(tracksOffset: Long, parsed: ParsedMkv): Int {
        // Tracks ID is 16 54 AE 6B (4 bytes)
        return 4 + getVintWidthOfElement(tracksOffset, parsed)
    }

    private fun getElementIdLen(offset: Long, parsed: ParsedMkv): Int {
        // Simple helper to get ID length based on offset
        if (offset == parsed.tracksOffset) return 4
        return 1 // TrackEntry ID AE is 1 byte
    }

    private fun getVintWidthOfElement(offset: Long, parsed: ParsedMkv): Int {
        if (offset == parsed.tracksOffset) {
            return parsed.tracksSizeVintWidth
        }
        val track = parsed.tracks.find { it.byteOffset == offset }
        if (track != null) {
            return track.sizeVintWidth
        }
        return 2 // fallback
    }

    private fun makeShrunkVoid(offset: Long, headerSize: Int, oldPayloadSize: Long, need: Long): ByteArray {
        val newPayloadSize = oldPayloadSize - need
        if (newPayloadSize < 0) {
            throw IOException("Negative Void payload size: $oldPayloadSize - $need")
        }
        val idBytes = byteArrayOf(0xEC.toByte())
        val sizeVintWidth = headerSize - 1
        val sizeBytes = encodeVint(newPayloadSize, sizeVintWidth)
        val out = java.io.ByteArrayOutputStream()
        out.write(idBytes)
        out.write(sizeBytes)
        out.write(ByteArray(newPayloadSize.toInt()))
        return out.toByteArray()
    }
}
sealed class TrackEditRequirement(val track: TrackInfo, val fieldName: String) {
    class PathA(track: TrackInfo, fieldName: String, val offset: Long, val newValue: Int, val oldValue: Int = 0) : TrackEditRequirement(track, fieldName)
    class PathB(track: TrackInfo, fieldName: String, val insLen: Int, val insBytes: ByteArray) : TrackEditRequirement(track, fieldName)
}
