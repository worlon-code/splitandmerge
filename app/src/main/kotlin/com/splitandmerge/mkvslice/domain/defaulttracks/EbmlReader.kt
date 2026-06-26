package com.splitandmerge.mkvslice.domain.defaulttracks

import com.splitandmerge.mkvslice.domain.defaulttracks.model.ParsedMkv
import com.splitandmerge.mkvslice.domain.defaulttracks.model.ParsedSeekHead
import com.splitandmerge.mkvslice.domain.defaulttracks.model.SeekEntry
import com.splitandmerge.mkvslice.domain.defaulttracks.model.TrackInfo
import com.splitandmerge.mkvslice.domain.defaulttracks.model.VoidDonor
import com.splitandmerge.mkvslice.platform.io.FileDescriptorWrapper
import java.io.IOException

class EbmlReader {

    companion object {
        const val EBML_HEADER_ID = 0x1A45DFA3L
        const val DOCTYPE_ID = 0x4282L
        const val SEGMENT_ID = 0x18538067L
        const val SEEKHEAD_ID = 0x114D9B74L
        const val SEEK_ID = 0x4DBBL
        const val SEEKID_ID = 0x53ABL
        const val SEEKPOSITION_ID = 0x53ACL
        const val VOID_ID = 0xECL
        const val TRACKS_ID = 0x1654AE6BL
        const val TRACK_ENTRY_ID = 0xAEL
        const val TRACK_NUMBER_ID = 0xD7L
        const val TRACK_TYPE_ID = 0x83L
        const val LANGUAGE_ID = 0x22B59CL
        const val LANGUAGE_IETF_ID = 0x22B59DL
        const val CODEC_ID_ID = 0x86L
        const val NAME_ID = 0x536EL
        const val FLAG_DEFAULT_ID = 0x88L
        const val FLAG_FORCED_ID = 0x55AAL
        const val FLAG_ENABLED_ID = 0xB9L
        const val CUES_ID = 0x1C53BB6BL
        const val CLUSTER_ID = 0x1F43B675L
    }

    private class ElementHeader(
        val id: Long,
        val idLen: Int,
        val size: Long,
        val sizeLen: Int,
        val offset: Long
    ) {
        val payloadOffset: Long get() = offset + idLen + sizeLen
        val totalSize: Long get() = idLen + sizeLen + (if (size == -1L) 0L else size)
        val endOffset: Long get() = offset + idLen + sizeLen + (if (size == -1L) 0L else size)
    }

    fun parse(fd: FileDescriptorWrapper): ParsedMkv {
        val fileLength = fd.size()
        
        // Read EBML Header first
        val header = readElementHeader(fd, 0L, fileLength)
            ?: throw IOException("Failed to read EBML header")
        if (header.id != EBML_HEADER_ID) {
            throw IOException("Not a Matroska/EBML file: invalid header ID 0x${header.id.toString(16)}")
        }
        
        val docType = readDocType(fd, header.payloadOffset, header.endOffset, fileLength)
        
        // Find Segment
        var offset = header.endOffset
        var segmentHeader: ElementHeader? = null
        while (offset < fileLength) {
            val el = readElementHeader(fd, offset, fileLength) ?: break
            if (el.id == SEGMENT_ID) {
                segmentHeader = el
                break
            }
            offset = el.endOffset
        }
        
        val segment = segmentHeader ?: throw IOException("Segment element not found")
        val segmentDataOffset = segment.payloadOffset
        val segmentEnd = if (segment.size == -1L) fileLength else segment.endOffset
        if (segmentEnd > fileLength) {
            throw IOException("Segment size exceeds file length")
        }

        var multiSegment = false
        var nextOffset = segmentEnd
        while (nextOffset < fileLength) {
            val el = readElementHeader(fd, nextOffset, fileLength) ?: break
            if (el.id == SEGMENT_ID) {
                multiSegment = true
                break
            }
            nextOffset = el.endOffset
        }

        val seekHeads = mutableListOf<ParsedSeekHead>()
        val trackEntriesList = mutableListOf<TrackInfo>()
        val allVoids = mutableListOf<VoidInfo>()
        
        var tracksOffset: Long? = null
        var tracksSize: Long? = null
        var tracksSizeVintWidth = 2
        var cuesOffset: Long? = null
        var firstClusterOffset: Long? = null
        
        // Walk Segment children up to first Cluster
        var currentOffset = segmentDataOffset
        while (currentOffset < segmentEnd) {
            val el = readElementHeader(fd, currentOffset, segmentEnd) ?: break
            
            if (el.id == CLUSTER_ID) {
                firstClusterOffset = el.offset
                break
            }
            
            when (el.id) {
                SEEKHEAD_ID -> {
                    val entries = parseSeekHead(fd, el.payloadOffset, el.endOffset, segmentEnd)
                    seekHeads.add(ParsedSeekHead(el.offset, el.size, el.idLen, el.sizeLen, entries))
                }
                TRACKS_ID -> {
                    tracksOffset = el.offset
                    tracksSize = el.totalSize
                    tracksSizeVintWidth = el.sizeLen
                    val (tracksList, voidsInTracks) = parseTracks(fd, el.payloadOffset, el.endOffset, segmentEnd)
                    trackEntriesList.addAll(tracksList)
                    allVoids.addAll(voidsInTracks)
                }
                CUES_ID -> {
                    cuesOffset = el.offset
                }
                VOID_ID -> {
                    allVoids.add(VoidInfo(el.offset, el.idLen + el.sizeLen, el.size))
                }
            }
            
            currentOffset = el.endOffset
        }
        
        // Categorize Voids
        val finalTracks = trackEntriesList
        
        // Usable Type (ii) Voids are directly inside Tracks but outside any TrackEntry
        val tracksPayloadOffset = if (tracksOffset != null) {
            val tracksHeader = readElementHeader(fd, tracksOffset, fileLength)!!
            tracksHeader.payloadOffset
        } else 0L
        val tracksEndOffset = if (tracksOffset != null && tracksSize != null) tracksOffset + tracksSize else 0L
        
        val typeIiVoids = allVoids.filter { v ->
            tracksOffset != null && v.offset >= tracksPayloadOffset && (v.offset + v.headerSize + v.payloadSize) <= tracksEndOffset &&
                    finalTracks.none { track -> v.offset >= track.byteOffset && (v.offset + v.headerSize + v.payloadSize) <= track.trackEntryEnd }
        }.map { v ->
            VoidDonor(v.offset, v.headerSize, v.payloadSize, 2)
        }
        
        // Type (iii) Void is immediately after Tracks and before next externally-referenced element
        val typeIiiVoids = mutableListOf<VoidDonor>()
        if (tracksEndOffset > 0L) {
            val adjacentVoid = allVoids.find { it.offset == tracksEndOffset }
            if (adjacentVoid != null) {
                // Confirm it is before the first Cluster or any other externally referenced element
                val extRefOffsets = mutableListOf<Long>()
                if (firstClusterOffset != null) extRefOffsets.add(firstClusterOffset)
                if (cuesOffset != null) extRefOffsets.add(cuesOffset)
                seekHeads.forEach { extRefOffsets.add(it.offset) }
                val nextExtOffset = extRefOffsets.filter { it > tracksEndOffset }.minOrNull()
                
                if (nextExtOffset == null || (adjacentVoid.offset + adjacentVoid.headerSize + adjacentVoid.payloadSize) <= nextExtOffset) {
                    typeIiiVoids.add(VoidDonor(adjacentVoid.offset, adjacentVoid.headerSize, adjacentVoid.payloadSize, 3))
                }
            }
        }
        
        // Attach all usable voids of type ii/iii to the track infos so they can access them
        val tracksWithAllVoids = finalTracks.map { track ->
            val combinedVoids = track.voidDonors.toMutableList()
            combinedVoids.addAll(typeIiVoids)
            combinedVoids.addAll(typeIiiVoids)
            track.copy(voidDonors = combinedVoids)
        }
        
        return ParsedMkv(
            docType = docType,
            segmentDataOffset = segmentDataOffset,
            firstClusterOffset = firstClusterOffset,
            tracksOffset = tracksOffset,
            tracksSize = tracksSize,
            seekHeads = seekHeads,
            tracks = tracksWithAllVoids,
            cuesOffset = cuesOffset,
            segmentSizeVintWidth = segment.sizeLen,
            segmentSize = segment.size,
            multiSegment = multiSegment,
            tracksSizeVintWidth = tracksSizeVintWidth
        )
    }

    private data class VoidInfo(
        val offset: Long,
        val headerSize: Int,
        val payloadSize: Long
    )

    private fun readElementHeader(fd: FileDescriptorWrapper, offset: Long, limit: Long): ElementHeader? {
        if (offset >= limit) return null
        
        // Read ID VINT
        fd.seek(offset, 0)
        val b0 = readByteOrNull(fd) ?: return null
        val idLen = getVintLength(b0)
        if (idLen > 4 || offset + idLen > limit) return null
        
        var idVal = b0.toLong() and 0xFF
        for (i in 1 until idLen) {
            val b = readByteOrNull(fd) ?: return null
            idVal = (idVal shl 8) or (b.toLong() and 0xFF)
        }
        
        // Read Size VINT
        val sb0 = readByteOrNull(fd) ?: return null
        val sizeLen = getVintLength(sb0)
        if (sizeLen > 8 || offset + idLen + sizeLen > limit) return null
        
        var sizeVal = (sb0.toInt() and (getVintMask(sizeLen) - 1)).toLong()
        for (i in 1 until sizeLen) {
            val b = readByteOrNull(fd) ?: return null
            sizeVal = (sizeVal shl 8) or (b.toLong() and 0xFF)
        }
        
        // Check unknown size sentinel
        val unknownSentinel = (1L shl (7 * sizeLen)) - 1
        val finalSize = if (sizeVal == unknownSentinel) -1L else sizeVal
        if (finalSize != -1L && offset + idLen + sizeLen + finalSize > limit) {
            throw IOException("Element size exceeds boundary")
        }
        
        return ElementHeader(idVal, idLen, finalSize, sizeLen, offset)
    }

    private fun getVintLength(firstByte: Byte): Int {
        val b = firstByte.toInt() and 0xFF
        if (b == 0) return 9 // invalid, but lets return 9 to trigger error check
        var mask = 0x80
        var len = 1
        while (mask > 0) {
            if ((b and mask) != 0) break
            mask = mask shr 1
            len++
        }
        return len
    }

    private fun getVintMask(length: Int): Int {
        return 1 shl (8 - length)
    }

    private fun readByteOrNull(fd: FileDescriptorWrapper): Byte? {
        val buf = ByteArray(1)
        val r = fd.read(buf, 0, 1)
        return if (r == 1) buf[0] else null
    }

    private fun readDocType(fd: FileDescriptorWrapper, start: Long, end: Long, fileLength: Long): String {
        var offset = start
        while (offset < end && offset < fileLength) {
            val el = readElementHeader(fd, offset, end) ?: break
            if (el.id == DOCTYPE_ID && el.size > 0 && el.size < 64) {
                val buf = ByteArray(el.size.toInt())
                fd.seek(el.payloadOffset, 0)
                fd.read(buf, 0, buf.size)
                return String(buf).trimEnd { it == '\u0000' }
            }
            offset = el.endOffset
        }
        return "matroska" // default to matroska if absent
    }

    private fun parseSeekHead(fd: FileDescriptorWrapper, start: Long, end: Long, limit: Long): List<SeekEntry> {
        val entries = mutableListOf<SeekEntry>()
        var offset = start
        while (offset < end && offset < limit) {
            val el = readElementHeader(fd, offset, end) ?: break
            if (el.id == SEEK_ID) {
                var seekOffset = el.payloadOffset
                var seekId: Long? = null
                var seekPos: Long? = null
                while (seekOffset < el.endOffset && seekOffset < limit) {
                    val child = readElementHeader(fd, seekOffset, el.endOffset) ?: break
                    if (child.id == SEEKID_ID) {
                        seekId = readInt(fd, child.payloadOffset, child.size.toInt())
                    } else if (child.id == SEEKPOSITION_ID) {
                        seekPos = readInt(fd, child.payloadOffset, child.size.toInt())
                    }
                    seekOffset = child.endOffset
                }
                if (seekId != null && seekPos != null) {
                    entries.add(SeekEntry(seekId, seekPos))
                }
            }
            offset = el.endOffset
        }
        return entries
    }

    private fun parseTracks(fd: FileDescriptorWrapper, start: Long, end: Long, limit: Long): Pair<List<TrackInfo>, List<VoidInfo>> {
        val tracksList = mutableListOf<TrackInfo>()
        val voidsList = mutableListOf<VoidInfo>()
        var offset = start
        while (offset < end && offset < limit) {
            val el = readElementHeader(fd, offset, end) ?: break
            if (el.id == TRACK_ENTRY_ID) {
                tracksList.add(parseTrackEntry(fd, el.offset, el.sizeLen, el.payloadOffset, el.endOffset, limit))
            } else if (el.id == VOID_ID) {
                voidsList.add(VoidInfo(el.offset, el.idLen + el.sizeLen, el.size))
            }
            offset = el.endOffset
        }
        return Pair(tracksList, voidsList)
    }

    private fun parseTrackEntry(fd: FileDescriptorWrapper, trackEntryStart: Long, sizeVintWidth: Int, start: Long, end: Long, limit: Long): TrackInfo {
        var offset = start
        var trackNum = 0L
        var trackType = 0
        var language = "und"
        var flagDefault = 1 // implied
        var flagForced = 0 // implied
        var flagDefaultOffset: Long? = null
        var flagForcedOffset: Long? = null
        var name: String? = null
        var codec = ""
        val voidsInTrackEntry = mutableListOf<VoidDonor>()
        
        while (offset < end && offset < limit) {
            val el = readElementHeader(fd, offset, end) ?: break
            when (el.id) {
                VOID_ID -> {
                    voidsInTrackEntry.add(VoidDonor(el.offset, el.idLen + el.sizeLen, el.size, 1))
                }
                TRACK_NUMBER_ID -> {
                    trackNum = readInt(fd, el.payloadOffset, el.size.toInt())
                }
                TRACK_TYPE_ID -> {
                    trackType = readInt(fd, el.payloadOffset, el.size.toInt()).toInt()
                }
                LANGUAGE_ID, LANGUAGE_IETF_ID -> {
                    if (el.size > 0 && el.size < 64) {
                        val buf = ByteArray(el.size.toInt())
                        fd.seek(el.payloadOffset, 0)
                        fd.read(buf, 0, buf.size)
                        language = String(buf).trimEnd { it == '\u0000' }
                    }
                }
                CODEC_ID_ID -> {
                    if (el.size > 0 && el.size < 128) {
                        val buf = ByteArray(el.size.toInt())
                        fd.seek(el.payloadOffset, 0)
                        fd.read(buf, 0, buf.size)
                        codec = String(buf).trimEnd { it == '\u0000' }
                    }
                }
                NAME_ID -> {
                    if (el.size > 0 && el.size < 256) {
                        val buf = ByteArray(el.size.toInt())
                        fd.seek(el.payloadOffset, 0)
                        fd.read(buf, 0, buf.size)
                        name = String(buf).trimEnd { it == '\u0000' }
                    }
                }
                FLAG_DEFAULT_ID -> {
                    if (el.size == 1L) {
                        flagDefault = readInt(fd, el.payloadOffset, 1).toInt()
                        flagDefaultOffset = el.payloadOffset
                    } else if (el.size > 1L) {
                        flagDefault = -1 // flag as invalid size for multi-byte check
                        flagDefaultOffset = el.payloadOffset
                    } else {
                        flagDefault = -2 // dataSize == 0
                        flagDefaultOffset = el.payloadOffset
                    }
                }
                FLAG_FORCED_ID -> {
                    if (el.size == 1L) {
                        flagForced = readInt(fd, el.payloadOffset, 1).toInt()
                        flagForcedOffset = el.payloadOffset
                    } else if (el.size > 1L) {
                        flagForced = -1
                        flagForcedOffset = el.payloadOffset
                    } else {
                        flagForced = -2
                        flagForcedOffset = el.payloadOffset
                    }
                }
            }
            offset = el.endOffset
        }
        
        return TrackInfo(
            trackNumber = trackNum,
            trackType = trackType,
            language = language,
            flagDefault = flagDefault,
            flagForced = flagForced,
            name = name,
            codec = codec,
            byteOffset = trackEntryStart,
            flagDefaultOffset = flagDefaultOffset,
            flagForcedOffset = flagForcedOffset,
            trackEntryEnd = end,
            voidDonors = voidsInTrackEntry,
            sizeVintWidth = sizeVintWidth
        )
    }

    private fun readInt(fd: FileDescriptorWrapper, offset: Long, size: Int): Long {
        if (size == 0) return 0L
        fd.seek(offset, 0)
        val buf = ByteArray(size)
        fd.read(buf, 0, size)
        var value = 0L
        for (i in 0 until size) {
            value = (value shl 8) or (buf[i].toLong() and 0xFF)
        }
        return value
    }
}
