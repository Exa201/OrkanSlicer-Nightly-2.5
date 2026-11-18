package ru.ytkab0bp.slicebeam.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.ytkab0bp.slicebeam.R

class PrinterProfileCategoryAssetsTest {
    @Test
    fun printerProfileCategoriesUseTheirOwnAssets() {
        assertEquals(R.drawable.printerprofile_allgemein,
            PrinterProfileCategoryAssets.iconFor("General", R.drawable.printer_outline_28))
        assertEquals(R.drawable.printerprofile_maschinengrenzen,
            PrinterProfileCategoryAssets.iconFor("Machine limits", R.drawable.note_pen_outline_96))
        assertEquals(R.drawable.printerprofile_drucker,
            PrinterProfileCategoryAssets.iconFor("Printer", R.drawable.menu3d_printer))
        assertEquals(R.drawable.printerprofile_druckerhost_einrichten,
            PrinterProfileCategoryAssets.iconFor("Physical Printer", R.drawable.power_socket_outline_28))
        assertEquals(R.drawable.printerprofile_extruder_1,
            PrinterProfileCategoryAssets.iconFor("Extruder 1", R.drawable.printerprofile_extruder_1))
        assertEquals(R.drawable.ic_custom_gcode_normal,
            PrinterProfileCategoryAssets.iconFor("Custom G-code", R.drawable.settings_outline_28))
        assertEquals(R.drawable.ic_notes_normal,
            PrinterProfileCategoryAssets.iconFor("Notes", R.drawable.note_pen_outline_96))
        assertEquals(R.drawable.menu3d_tools,
            PrinterProfileCategoryAssets.iconFor("Unknown", R.drawable.menu3d_tools))
    }

    @Test
    fun printerProfileStatesStaySeparateFromOtherProfilePacks() {
        assertEquals(R.drawable.printerprofile_profilsuche_active,
            PrinterProfileCategoryAssets.stateIcon(R.drawable.printerprofile_profilsuche, active = true))
        assertEquals(R.drawable.printerprofile_zuruecksetzen_disabled,
            PrinterProfileCategoryAssets.stateIcon(R.drawable.printerprofile_zuruecksetzen, enabled = false))
        assertEquals(R.drawable.ic_custom_gcode_active,
            PrinterProfileCategoryAssets.stateIcon(R.drawable.ic_custom_gcode_normal, active = true))
        assertEquals(R.drawable.ic_notes_disabled,
            PrinterProfileCategoryAssets.stateIcon(R.drawable.ic_notes_normal, enabled = false))
        assertTrue(PrinterProfileCategoryAssets.isPackAsset(R.drawable.ic_custom_gcode_active))
        assertTrue(PrinterProfileCategoryAssets.isPackAsset(R.drawable.ic_notes_disabled))
        assertTrue(PrinterProfileCategoryAssets.isPackAsset(R.drawable.printerprofile_druckerprofil_active))
        assertNotEquals(R.drawable.printerprofile_speichern, R.drawable.ic_speichern)
        assertNotEquals(R.drawable.printerprofile_zuruecksetzen, R.drawable.ic_zuruecksetzen)
        assertNotEquals(R.drawable.printerprofile_profilsuche, R.drawable.ic_profilsuche)
    }

    @Test
    fun sharedCategoryRowResolvesTheMatchingProfilePackState() {
        assertEquals(
            R.drawable.printerprofile_allgemein_active,
            PrinterProfileCategoryAssets.stateIconFor(R.drawable.printerprofile_allgemein, active = true),
        )
        assertEquals(
            R.drawable.ic_filament_kuehlung_active,
            PrinterProfileCategoryAssets.stateIconFor(R.drawable.ic_filament_kuehlung, active = true),
        )
        assertEquals(
            R.drawable.menu3d_tools,
            PrinterProfileCategoryAssets.stateIconFor(R.drawable.menu3d_tools, active = true),
        )
    }
}
