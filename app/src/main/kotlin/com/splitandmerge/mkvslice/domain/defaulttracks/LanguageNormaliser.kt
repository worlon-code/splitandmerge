package com.splitandmerge.mkvslice.domain.defaulttracks

import java.util.Locale

object LanguageNormaliser {

    private val ISO_639_1_TO_2_T = mapOf(
        "en" to "eng", "ja" to "jpn", "de" to "deu", "fr" to "fra", "es" to "spa",
        "pt" to "por", "it" to "ita", "ru" to "rus", "ko" to "kor", "zh" to "zho",
        "nl" to "nld", "sv" to "swe", "da" to "dan", "fi" to "fin", "pl" to "pol",
        "tr" to "tur", "ar" to "ara", "hi" to "hin", "th" to "tha", "vi" to "vie",
        "cs" to "ces", "el" to "ell", "he" to "heb", "hu" to "hun", "id" to "ind",
        "ro" to "ron", "uk" to "ukr", "sl" to "slv", "sk" to "slk"
    )

    private val ISO_639_2_B_TO_T = mapOf(
        "ger" to "deu", "fre" to "fra", "chi" to "zho", "dut" to "nld", "cze" to "ces",
        "gre" to "ell", "ice" to "isl", "mac" to "mkd", "may" to "msa", "bur" to "mya",
        "per" to "fas", "rum" to "ron", "slo" to "slk", "tib" to "bod", "wel" to "cym",
        "arm" to "hye", "baq" to "eus", "geo" to "kat", "alb" to "sqi"
    )

    // Norwegian Bokmål/Nynorsk variants map to "nor" in v1
    private val NORWEGIAN_VARIANTS = setOf("no", "nb", "nn", "nob", "nno")

    // Chinese variants map to "zho" in v1
    private val CHINESE_VARIANTS = setOf("zh", "cmn", "yue")

    fun normalizeLang(raw: String?): String {
        return normalizeLangFlagged(raw).first
    }

    fun normalizeLangFlagged(raw: String?): Pair<String, Boolean> {
        if (raw.isNullOrBlank()) {
            return Pair("und", false)
        }
        val clean = raw.lowercase(Locale.ROOT).trim()
        val parts = clean.split('-', '_')
        val primary = parts.firstOrNull() ?: return Pair("und", false)

        if (primary == "jp") {
            return Pair("jpn", true)
        }

        if (NORWEGIAN_VARIANTS.contains(primary)) {
            return Pair("nor", false)
        }

        if (CHINESE_VARIANTS.contains(primary) || primary.startsWith("zh")) {
            return Pair("zho", false)
        }

        // Distinct Slovak and Slovenian check
        if (primary == "sl") return Pair("slv", false)
        if (primary == "sk" || primary == "slo") return Pair("slk", false)
        if (primary == "slv") return Pair("slv", false)

        ISO_639_1_TO_2_T[primary]?.let {
            return Pair(it, false)
        }

        ISO_639_2_B_TO_T[primary]?.let {
            return Pair(it, false)
        }

        if (primary.length == 3 && primary.all { it in 'a'..'z' }) {
            return Pair(primary, false)
        }

        return Pair("und", false)
    }

    fun extractRegion(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val clean = raw.lowercase(Locale.ROOT).trim()
        val parts = clean.split('-', '_')
        if (parts.size <= 1) return null

        for (i in 1 until parts.size) {
            val sub = parts[i]
            // Ignore 4-letter script tags (e.g. hant, hans, latn)
            if (sub.length == 4) continue
            // Check if 2-letter country code
            if (sub.length == 2 && sub.all { it in 'a'..'z' }) {
                return sub
            }
            // Check if 3-digit UN M.49 code
            if (sub.length == 3 && sub.all { it in '0'..'9' }) {
                return sub
            }
        }
        return null
    }
}
