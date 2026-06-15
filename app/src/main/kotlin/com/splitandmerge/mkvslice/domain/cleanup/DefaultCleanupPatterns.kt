package com.splitandmerge.mkvslice.domain.cleanup

import com.splitandmerge.mkvslice.data.db.entity.CleanupPatternEntity

val DEFAULT_CLEANUP_PATTERNS = listOf(
    CleanupPatternEntity(
        id = "url_prefix",
        regex = "^www\\.[^\\s\\-]+\\s*[-\\u2013]\\s*",
        replacement = "",
        enabled = true,
        isBuiltIn = true,
        orderIndex = 0,
        label = "Strip leading URL prefix",
        createdAt = 1718300000000L
    ),
    CleanupPatternEntity(
        id = "resolution",
        regex = "\\b(2160p|1080p|720p|480p|UHD|4K)\\b",
        replacement = "",
        enabled = true,
        isBuiltIn = true,
        orderIndex = 1,
        label = "Strip resolution tokens",
        createdAt = 1718300000000L
    ),
    CleanupPatternEntity(
        id = "codec",
        regex = "\\b(x264|x265|HEVC|H\\.?264|H\\.?265|AVC|AV1)\\b",
        replacement = "",
        enabled = true,
        isBuiltIn = true,
        orderIndex = 2,
        label = "Strip codec tokens",
        createdAt = 1718300000000L
    ),
    CleanupPatternEntity(
        id = "audio",
        regex = "\\b(AAC(?:\\s?LC)?|AC3|EAC3|DDP(?:5\\.1)?|DD(?:5\\.1)?|DTS(?:[-\\s]?HD(?:[-\\s]?MA)?)?|TrueHD|Atmos|MP3|FLAC|OPUS)\\b",
        replacement = "",
        enabled = true,
        isBuiltIn = true,
        orderIndex = 3,
        label = "Strip audio tokens",
        createdAt = 1718300000000L
    ),
    CleanupPatternEntity(
        id = "source",
        regex = "\\b(BluRay|BDRip|BRRip|WEBRip|WEB[-\\s]?DL|NEWEB[-\\s]?DL|HDRip|DVDRip|REMUX)\\b",
        replacement = "",
        enabled = true,
        isBuiltIn = true,
        orderIndex = 4,
        label = "Strip source tokens",
        createdAt = 1718300000000L
    ),
    CleanupPatternEntity(
        id = "hdr",
        regex = "\\b(HDR10\\+?|HDR|DV|DolbyVision)\\b",
        replacement = "",
        enabled = true,
        isBuiltIn = true,
        orderIndex = 5,
        label = "Strip HDR tokens",
        createdAt = 1718300000000L
    ),
    CleanupPatternEntity(
        id = "markers",
        regex = "\\b(DUAL|MULTI|REPACK|PROPER|INTERNAL|EXTENDED|DIRECTOR\\.?'?S?\\.?CUT)\\b",
        replacement = "",
        enabled = true,
        isBuiltIn = true,
        orderIndex = 6,
        label = "Strip dual / multi / repack",
        createdAt = 1718300000000L
    ),
    CleanupPatternEntity(
        id = "release_group",
        regex = "-([A-Z][A-Za-z0-9]{2,15})$",
        replacement = "",
        enabled = true,
        isBuiltIn = true,
        orderIndex = 7,
        label = "Strip release-group trailing tokens",
        createdAt = 1718300000000L
    ),
    CleanupPatternEntity(
        id = "dots_to_spaces",
        regex = "(?<!\\()\\.(?!\\))",
        replacement = " ",
        enabled = true,
        isBuiltIn = true,
        orderIndex = 8,
        label = "Replace dots with spaces (preserve year parens)",
        createdAt = 1718300000000L
    ),
    CleanupPatternEntity(
        id = "wrap_year",
        regex = "(?<![\\(\\d])(\\b(?:19|20)\\d{2}\\b)(?!\\))",
        replacement = "($1)",
        enabled = true,
        isBuiltIn = true,
        orderIndex = 9,
        label = "Wrap unwrapped 4-digit year",
        createdAt = 1718300000000L
    ),
    CleanupPatternEntity(
        id = "collapse_ws",
        regex = "\\s+",
        replacement = " ",
        enabled = true,
        isBuiltIn = true,
        orderIndex = 10,
        label = "Collapse whitespace",
        createdAt = 1718300000000L
    ),
    CleanupPatternEntity(
        id = "trim_punct",
        regex = "[-.\\s\\\\|·]+$",
        replacement = "",
        enabled = true,
        isBuiltIn = true,
        orderIndex = 11,
        label = "Trim trailing punctuation",
        createdAt = 1718300000000L
    )
)
