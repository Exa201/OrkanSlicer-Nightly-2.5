package ru.ytkab0bp.slicebeam.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import ru.ytkab0bp.slicebeam.R

class FilamentProfileCategoryAssetsTest {
    @Test
    fun filamentGroupsUseFilamentSpecificArtworkWithoutChangingOtherProfileSets() {
        val expected = mapOf(
            "Filament" to R.drawable.ic_filament,
            "Cooling" to R.drawable.ic_filament_kuehlung,
            "Advanced" to R.drawable.ic_filament_erweitert,
            "Filament Overrides" to R.drawable.ic_filamentkorrekturen,
            "Custom G-code" to R.drawable.ic_filament,
            "Notes" to R.drawable.ic_info,
            "Dependencies" to R.drawable.ic_materialwarnung,
        )

        val categories = OrcaFilamentSettingsCatalog.categories()
        assertEquals(expected.keys.toList(), categories.map { it.title })
        categories.forEach { category ->
            assertEquals(expected.getValue(category.title), FilamentProfileCategoryAssets.iconFor(category.title, category.iconResId))
        }
        assertNotEquals(R.drawable.ic_filament_kuehlung, R.drawable.ic_kuehlung)
        assertNotEquals(R.drawable.ic_filament_erweitert, R.drawable.ic_erweitert)
    }

    @Test
    fun categoryOpenStateUsesTheActiveArtworkAndResetUsesDisabledArtwork() {
        assertEquals(R.drawable.ic_filamentkorrekturen_active,
            FilamentProfileCategoryAssets.stateIcon(R.drawable.ic_filamentkorrekturen, active = true))
        assertEquals(R.drawable.ic_filamentkorrekturen,
            FilamentProfileCategoryAssets.stateIcon(R.drawable.ic_filamentkorrekturen, active = false))
        assertEquals(R.drawable.ic_zuruecksetzen_disabled,
            FilamentProfileCategoryAssets.stateIcon(R.drawable.ic_zuruecksetzen, active = false, enabled = false))
        assertEquals(R.drawable.ic_zuruecksetzen,
            FilamentProfileCategoryAssets.stateIcon(R.drawable.ic_zuruecksetzen, active = false, enabled = true))
    }
}
