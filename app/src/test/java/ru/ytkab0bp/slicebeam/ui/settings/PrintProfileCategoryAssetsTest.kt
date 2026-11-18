package ru.ytkab0bp.slicebeam.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.ytkab0bp.slicebeam.R

class PrintProfileCategoryAssetsTest {
    @Test
    fun everyOrcaPrintCategoryUsesItsDedicatedArtwork() {
        val expected = mapOf(
            "Layers and perimeters" to R.drawable.ic_schichten_und_wandungen,
            "Top/bottom layers" to R.drawable.ic_obere_untere_schichten,
            "Infill" to R.drawable.ic_fuellung,
            "Skirt and brim" to R.drawable.ic_haftung_skirt_brim,
            "Support material" to R.drawable.ic_stuetzstrukturen,
            "Speed" to R.drawable.ic_geschwindigkeit,
            "Cooling" to R.drawable.ic_kuehlung,
            "Multiple Extruders" to R.drawable.ic_mehrere_extruder,
            "Advanced" to R.drawable.ic_erweitert,
            "Output options" to R.drawable.ic_ausgabeoptionen,
        )

        val categories = OrcaProcessSettingsCatalog.categories()
        assertEquals(expected.keys.toList(), categories.map { it.title })
        categories.forEach { category ->
            assertEquals(expected.getValue(category.title), PrintProfileCategoryAssets.iconFor(category.title, category.iconResId))
        }
    }

    @Test
    fun unknownPrintCategoryKeepsTheProvidedFallback() {
        assertEquals(123, PrintProfileCategoryAssets.iconFor("Unknown", 123))
    }
}
