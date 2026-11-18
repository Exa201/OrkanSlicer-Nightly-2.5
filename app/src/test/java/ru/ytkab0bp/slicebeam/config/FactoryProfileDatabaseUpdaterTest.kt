package ru.ytkab0bp.slicebeam.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.ytkab0bp.slicebeam.slic3r.Slic3rConfigWrapper
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

class FactoryProfileDatabaseUpdaterTest {
    @Test
    fun refreshesUnchangedFactoryProfilesAndAddsNewOnes() {
        val previous = config(
            "[printer:Machine]\nprinter_model = Test\nnozzle_diameter = 0.4\n\n" +
                "[print:Standard]\nlayer_height = 0.2\n\n" +
                "[print:Obsolete]\nlayer_height = 0.3\n\n" +
                "[filament:PLA]\nfilament_colour = #ffffff\n"
        )
        val installed = config(previous.serialize())
        installed.presets?.put("print", "Standard")
        installed.presets?.put("filament", "PLA")
        val latest = config(
            "[printer:Machine]\nprinter_model = Test\nnozzle_diameter = 0.4\n\n" +
                "[print:Standard]\nlayer_height = 0.24\n\n" +
                "[print:Fast]\nlayer_height = 0.28\n\n" +
                "[filament:PLA]\nfilament_colour = #ffffff\n"
        )

        val changed = FactoryProfileDatabaseUpdater.refresh(installed, latest, previous)

        assertTrue(changed)
        assertEquals("0.24", installed.findPrint("Standard")?.get("layer_height"))
        assertNotNull(installed.findPrint("Fast"))
        assertNull(installed.findPrint("Obsolete"))
        assertEquals("Standard", installed.presets?.get("print"))
    }

    @Test
    fun preservesUserModifiedFactoryProfile() {
        val previous = config("[filament:PLA]\nfilament_colour = #ffffff\nfilament_density = 1.24\n")
        val installed = config(previous.serialize())
        installed.findFilament("PLA")?.put("filament_colour", "#123456")
        val latest = config("[filament:PLA]\nfilament_colour = #eeeeee\nfilament_density = 1.30\n")

        FactoryProfileDatabaseUpdater.refresh(installed, latest, previous)

        assertEquals("#123456", installed.findFilament("PLA")?.get("filament_colour"))
        assertEquals("1.24", installed.findFilament("PLA")?.get("filament_density"))
    }

    @Test
    fun migratesLegacyKobraV2StockNameToUpstreamPreset() {
        val previous = config("[printer:Anycubic Kobra 3 0.4 nozzle]\nprinter_model = Anycubic Kobra 3\nnozzle_diameter = 0.4\n")
        val installed = config(
            "[printer:Anycubic Kobra 3 v2 0.4 nozzle - Stock]\n" +
                "printer_model = Anycubic Kobra 3 V2\nprinter_variant = 0.4\nnozzle_diameter = 0.4\n"
        )
        installed.presets?.put("printer", "Anycubic Kobra 3 v2 0.4 nozzle - Stock")
        val latest = config("[printer:Anycubic Kobra 3 V2 0.4 nozzle]\nprinter_model = Anycubic Kobra 3 V2\nprinter_variant = 0.4\nnozzle_diameter = 0.4\n")

        FactoryProfileDatabaseUpdater.refresh(installed, latest, previous)

        assertNull(installed.findPrinter("Anycubic Kobra 3 v2 0.4 nozzle - Stock"))
        assertNotNull(installed.findPrinter("Anycubic Kobra 3 V2 0.4 nozzle"))
        assertEquals("Anycubic Kobra 3 V2 0.4 nozzle", installed.presets?.get("printer"))
        assertFalse(installed.printerConfigs.isEmpty())
    }

    private fun config(text: String) = Slic3rConfigWrapper(
        ByteArrayInputStream(text.toByteArray(StandardCharsets.UTF_8))
    )
}
