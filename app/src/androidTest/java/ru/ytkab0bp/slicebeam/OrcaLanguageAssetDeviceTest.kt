package ru.ytkab0bp.slicebeam

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.ytkab0bp.slicebeam.slic3r.OrcaLanguage
import ru.ytkab0bp.slicebeam.slic3r.Slic3rLocalization

@RunWith(AndroidJUnit4::class)
class OrcaLanguageAssetDeviceTest {
    @Test fun everySelectableOrcaLanguageCatalogIsPackagedAndReadable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packagedCatalogs = context.assets.list("localization")
            .orEmpty().filter { it.endsWith(".po") }.toSet()
        val expectedCatalogs = OrcaLanguage.all.map { "${it.assetKey}.po" }.toSet()

        assertEquals(expectedCatalogs, packagedCatalogs)
        OrcaLanguage.all.forEach { language ->
            val label = Slic3rLocalization.getInstance(language.assetKey).get("Main Extruder")
            assertTrue("${language.tag} catalog must return a readable string", label.isNotBlank())
        }
    }
}
