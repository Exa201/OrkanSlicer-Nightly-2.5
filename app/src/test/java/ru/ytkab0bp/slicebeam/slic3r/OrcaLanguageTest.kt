package ru.ytkab0bp.slicebeam.slic3r

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrcaLanguageTest {
    @Test fun supportedLanguageTagsAreUniqueAndMatchOrcaCatalogSet() {
        val tags = OrcaLanguage.all.map { it.tag }

        assertEquals(23, tags.size)
        assertEquals(tags.size, tags.toSet().size)
        assertTrue(tags.containsAll(listOf("ca", "cs", "de", "en", "es", "eu", "fr", "hu", "it", "ja", "ko", "lt", "nl", "pl", "pt-BR", "ru", "sv", "th", "tr", "uk", "vi", "zh-CN", "zh-TW")))
    }

    @Test fun regionTagsResolveToTheMatchingPoAssetNames() {
        assertEquals("pt_BR", OrcaLanguage.byTag("pt-BR").assetKey)
        assertEquals("zh_CN", OrcaLanguage.byTag("zh-CN").assetKey)
        assertEquals("zh_TW", OrcaLanguage.byTag("zh-TW").assetKey)
        assertEquals("de", OrcaLanguage.byTag("de-DE").assetKey)
    }

    @Test fun unsupportedLanguageFallsBackToGermanDefault() {
        assertEquals("de", OrcaLanguage.byTag("xx").assetKey)
        assertNotNull(OrcaLanguage.byTag(null))
    }
}
