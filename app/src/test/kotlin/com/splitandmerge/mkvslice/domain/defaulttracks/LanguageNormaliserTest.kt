package com.splitandmerge.mkvslice.domain.defaulttracks

import org.junit.Test
import org.junit.Assert.*

class LanguageNormaliserTest {

    @Test
    fun testSlovakAndSlovenianDistinct() {
        val slovakSk = LanguageNormaliser.normalizeLang("sk")
        val slovakSlo = LanguageNormaliser.normalizeLang("slo")
        val slovenianSl = LanguageNormaliser.normalizeLang("sl")
        val slovenianSlv = LanguageNormaliser.normalizeLang("slv")

        assertEquals("slk", slovakSk)
        assertEquals("slk", slovakSlo)
        assertEquals("slv", slovenianSl)
        assertEquals("slv", slovenianSlv)

        // Strict requirement A: assert slv != slk
        assertNotEquals("Slovak and Slovenian must not be conflated", slovakSk, slovenianSl)
        assertNotEquals("slk", slovenianSlv)
    }

    @Test
    fun testNorwegianCollapse() {
        val variants = listOf("no", "nb", "nn", "nob", "nno")
        for (v in variants) {
            assertEquals("nor", LanguageNormaliser.normalizeLang(v))
        }
    }

    @Test
    fun testChineseCollapse() {
        val variants = listOf("zh", "zh-hans", "zh_hant", "cmn", "yue")
        for (v in variants) {
            assertEquals("zho", LanguageNormaliser.normalizeLang(v))
        }
    }

    @Test
    fun testDoublets() {
        assertEquals("deu", LanguageNormaliser.normalizeLang("ger"))
        assertEquals("fra", LanguageNormaliser.normalizeLang("fre"))
        assertEquals("zho", LanguageNormaliser.normalizeLang("chi"))
    }

    @Test
    fun testJpTLDGuess() {
        val result = LanguageNormaliser.normalizeLangFlagged("jp")
        assertEquals("jpn", result.first)
        assertTrue(result.second) // should flag as guess

        val resultJpn = LanguageNormaliser.normalizeLangFlagged("jpn")
        assertEquals("jpn", resultJpn.first)
        assertFalse(resultJpn.second) // should not flag as guess
    }

    @Test
    fun testRegionExtraction() {
        assertEquals("us", LanguageNormaliser.extractRegion("en-US"))
        assertEquals("gb", LanguageNormaliser.extractRegion("en-GB"))
        assertEquals("cn", LanguageNormaliser.extractRegion("zh-Hans-CN"))
        assertEquals("419", LanguageNormaliser.extractRegion("es-419"))
        assertNull(LanguageNormaliser.extractRegion("eng"))
        assertNull(LanguageNormaliser.extractRegion("zh-Hans")) // script tag ignored, no country tag
    }

    @Test
    fun testNullAndBlank() {
        assertEquals("und", LanguageNormaliser.normalizeLang(null))
        assertEquals("und", LanguageNormaliser.normalizeLang(""))
        assertEquals("und", LanguageNormaliser.normalizeLang("   "))
        assertNull(LanguageNormaliser.extractRegion(null))
        assertNull(LanguageNormaliser.extractRegion(""))
    }
}
