package ru.ytkab0bp.slicebeam.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.ytkab0bp.slicebeam.slic3r.Slic3rConfigWrapper

class ProfileCompatibilityTest {
    @Test
    fun missingPrinterHidesPrinterScopedProcessPresets() {
        val config = Slic3rConfigWrapper().apply {
            presets = ConfigObject()
            printConfigs += profile("Universal process")
            printConfigs += profile("fdm_process_common")
            printConfigs += profile("PLA 0.20 @Anycubic Kobra 3", "compatible_printers" to "Anycubic Kobra 3")
            printConfigs += profile("Qidi process", "compatible_printers_condition" to "printer_model=Qidi")
        }

        assertEquals(listOf("Universal process"), ProfileCompatibility.processes(config).map { it.title })
    }

    @Test
    fun missingPrinterKeepsTheAlreadySelectedScopedProcessVisible() {
        val config = Slic3rConfigWrapper().apply {
            presets = ConfigObject().apply { put("print", "Qidi process") }
            printConfigs += profile("Universal process")
            printConfigs += profile("Qidi process", "compatible_printers_condition" to "printer_model=Qidi")
            printConfigs += profile("Anycubic process", "compatible_printers" to "Anycubic Kobra 3")
        }

        assertEquals(
            listOf("Universal process", "Qidi process"),
            ProfileCompatibility.processes(config).map { it.title },
        )
    }

    @Test
    fun missingPrinterHidesVendorAndPrintScopedFilamentPresets() {
        val config = Slic3rConfigWrapper().apply {
            presets = ConfigObject()
            filamentConfigs += profile("Universal PLA")
            filamentConfigs += profile("fdm_filament_common")
            filamentConfigs += profile("Anycubic PLA @Kobra 3", "compatible_printers" to "Anycubic Kobra 3")
            filamentConfigs += profile("Specific filament", "compatible_prints" to "Fine")
        }

        assertEquals(listOf("Universal PLA"), ProfileCompatibility.filaments(config).map { it.title })
    }

    @Test
    fun missingPrinterKeepsTheAlreadySelectedVendorFilamentVisible() {
        val config = Slic3rConfigWrapper().apply {
            presets = ConfigObject().apply { put("filament", "Qidi PETG") }
            filamentConfigs += profile("Universal PLA")
            filamentConfigs += profile("Qidi PETG", "compatible_printers_condition" to "printer_model=Qidi")
            filamentConfigs += profile("Anycubic PLA", "compatible_printers" to "Anycubic Kobra 3")
        }

        assertEquals(
            listOf("Universal PLA", "Qidi PETG"),
            ProfileCompatibility.filaments(config).map { it.title },
        )
    }

    @Test
    fun parserKeepsPrinterProcessAndFilamentSectionsInTheirOwnLists() {
        val config = Slic3rConfigWrapper(
            """
            [printer:Printer A]
            printer_model = Model A
            [print:Process A]
            layer_height = 0.2
            [filament:Filament A]
            filament_type = PLA
            [presets]
            printer = Printer A
            print = Process A
            filament = Filament A
            """.trimIndent().byteInputStream(),
        )

        assertEquals(listOf("Printer A"), config.printerConfigs.map { it.title })
        assertEquals(listOf("Process A"), config.printConfigs.map { it.title })
        assertEquals(listOf("Filament A"), config.filamentConfigs.map { it.title })
        assertTrue(config.printerConfigs.single().profileListType == ConfigObject.PROFILE_LIST_PRINTER)
        assertTrue(config.printConfigs.single().profileListType == ConfigObject.PROFILE_LIST_PRINT)
        assertTrue(config.filamentConfigs.single().profileListType == ConfigObject.PROFILE_LIST_FILAMENT)
    }

    @Test
    fun internalOrcaInheritanceNodesAreNeverSelectable() {
        assertEquals(false, ProfileCompatibility.isSelectableProcess("fdm_process_common"))
        assertEquals(false, ProfileCompatibility.isSelectableFilament("fdm_filament_common"))
        assertEquals(false, ProfileCompatibility.isSelectableProcess("*Hidden process*"))
        assertEquals(true, ProfileCompatibility.isSelectableProcess("0.20mm Standard @Qidi Q2"))
        assertEquals(true, ProfileCompatibility.isSelectableFilament("QIDI PLA Rapido@Q2-Series"))
    }

    private fun profile(title: String, vararg values: Pair<String, String>) =
        ConfigObject(title).apply { values.forEach { (key, value) -> put(key, value) } }
}
