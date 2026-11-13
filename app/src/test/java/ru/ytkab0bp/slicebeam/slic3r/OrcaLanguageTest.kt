package ru.ytkab0bp.slicebeam.slic3r

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

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

    @Test fun additionalOrcaLanguagesShipAndroidWorkspaceAndLanguageSettingsLabels() {
        val resourceRoot = listOf(File("src/main/res"), File("app/src/main/res"))
            .firstOrNull { it.isDirectory }
            ?: error("Android resources directory not found")
        val additionalLanguages = OrcaLanguage.all.filter { it.tag !in setOf("de", "en", "ru") }

        assertEquals(20, additionalLanguages.size)
        additionalLanguages.forEach { language ->
            val qualifier = language.tag.replaceFirst("-", "-r")
            val file = File(resourceRoot, "values-$qualifier/strings.xml")
            assertTrue("Missing Android catalog for ${language.tag}", file.isFile)
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            val translatedNames = (0 until document.getElementsByTagName("string").length)
                .map { document.getElementsByTagName("string").item(it).attributes.getNamedItem("name").nodeValue }
                .toSet()

            assertTrue("${language.tag} must translate the workspace Preview tab", "NavigationPreview" in translatedNames)
            assertTrue("${language.tag} must translate the language setting", "SettingsInterfaceLanguage" in translatedNames)
            assertTrue("${language.tag} catalog unexpectedly has too few strings", translatedNames.size >= 150)
        }
    }
}
