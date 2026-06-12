package com.splitandmerge.mkvslice.domain.cleanup

import javax.inject.Inject

class TitleCleaner @Inject constructor() {

    fun cleanTitle(filename: String): String {
        var baseName = filename.substringBeforeLast(".")
        
        // Strip leading URL prefix
        baseName = Regex("""^www\.[^\s\-]+\s*[-\u2013]\s*""", RegexOption.IGNORE_CASE).replace(baseName, "")
        
        // Strip resolution
        baseName = Regex("""\b(2160p|1080p|720p|480p|UHD|4K)\b""", RegexOption.IGNORE_CASE).replace(baseName, "")
        
        // Strip codec
        baseName = Regex("""\b(x264|x265|HEVC|H\.?264|H\.?265|AVC|AV1)\b""", RegexOption.IGNORE_CASE).replace(baseName, "")
        
        // Strip audio
        baseName = Regex("""\b(AAC(?:\s?LC)?|AC3|EAC3|DDP(?:5\.1)?|DD(?:5\.1)?|DTS(?:[-\s]?HD(?:[-\s]?MA)?)?|TrueHD|Atmos|MP3|FLAC|OPUS)\b""", RegexOption.IGNORE_CASE).replace(baseName, "")
        
        // Strip source
        baseName = Regex("""\b(BluRay|BDRip|BRRip|WEBRip|WEB[-\s]?DL|NEWEB[-\s]?DL|HDRip|DVDRip|REMUX)\b""", RegexOption.IGNORE_CASE).replace(baseName, "")
        
        // Strip HDR
        baseName = Regex("""\b(HDR10\+?|HDR|DV|DolbyVision)\b""", RegexOption.IGNORE_CASE).replace(baseName, "")
        
        // Strip markers
        baseName = Regex("""\b(DUAL|MULTI|REPACK|PROPER|INTERNAL|EXTENDED|TRUE|REAL|DIRECTOR\.?'?S?\.?CUT)\b""", RegexOption.IGNORE_CASE).replace(baseName, "")
        
        // Strip size strings like "4.5GB" that are common
        baseName = Regex("""\b\d+(?:\.\d+)?[GM]B\b""", RegexOption.IGNORE_CASE).replace(baseName, "")

        // Strip release groups at the end
        baseName = Regex("""[-.\s][A-Za-z0-9]{3,}$""", RegexOption.IGNORE_CASE).replace(baseName, "")
        
        // Dots to spaces
        baseName = Regex("""(?<!\()\.(?!\))""", RegexOption.IGNORE_CASE).replace(baseName, " ")
        
        // Wrap year
        baseName = Regex("""(?<![\(\d])(\b(?:19|20)\d{2}\b)(?!\))""", RegexOption.IGNORE_CASE).replace(baseName, "($1)")
        
        // Strip bracket tags like [Telugu + Tamil]
        baseName = Regex("""\[.*?\]""", RegexOption.IGNORE_CASE).replace(baseName, "")

        // Collapse whitespace
        baseName = Regex("""\s+""").replace(baseName, " ")
        
        // Trim punctuation
        baseName = Regex("""[-.\s\\|·]+$""").replace(baseName, "")
        
        val fallback = filename.substringBeforeLast(".")
        return if (baseName.length >= 2) baseName.trim() else fallback.trim()
    }
}
