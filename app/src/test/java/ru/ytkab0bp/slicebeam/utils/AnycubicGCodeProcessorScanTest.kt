package ru.ytkab0bp.slicebeam.utils

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AnycubicGCodeProcessorScanTest {
    @Test
    fun scanCollectsPreflightDataAndIsBoundToTheScannedFileVersion() {
        val source = File.createTempFile("kobra-preflight", ".gcode")
        try {
            source.writeText(
                "; filament_type = PETG;PLA\n" +
                "SET_PRESSURE_ADVANCE ADVANCE=0.042 ; Override pressure advance value\n" +
                    "M900 K0.03\n" +
                    "T1\nT0\nT1\n",
            )

            val scan = AnycubicGCodeProcessor.scan(source)

            assertTrue(scan.matchesSource(source))
            assertArrayEquals(arrayOf("PETG", "PLA"), scan.materials)
            assertArrayEquals(intArrayOf(0, 1), scan.usedTools)
            assertArrayEquals(intArrayOf(1, 0), scan.firstToolAppearance)
            assertEquals(listOf("0.042"), scan.filamentPressureAdvance)
            assertEquals(listOf("0.03"), scan.customPressureAdvance)

            source.appendText("; changed after the preflight scan\n")
            assertFalse("A cached scan must not be used after its source changes", scan.matchesSource(source))
        } finally {
            source.delete()
        }
    }

    @Test
    fun externalSpoolCopyRemovesAceToolCommandsAndMappingMetadata() {
        val source = File.createTempFile("kobra-external", ".gcode")
        val destination = File.createTempFile("kobra-external-out", ".gcode")
        try {
            source.writeText(
                "; HEADER_BLOCK_START\n" +
                    "; filament_type = PETG\n" +
                    "; filament_colour_type = 0\n" +
                    "; filament: 1\n" +
                    "; paint_info = [{\"material_type\":\"PETG\"}]\n" +
                    "; HEADER_BLOCK_END\n" +
                    "T0 ; select ACE slot\n" +
                    " G1 X1 Y1 E1\n",
            )

            assertTrue(AnycubicGCodeProcessor.rewriteForExternalSpool(source, destination))
            val output = destination.readText()
            assertTrue(output.contains("; filament_type = PETG"))
            assertTrue(output.contains("G1 X1 Y1 E1"))
            assertFalse(output.contains("T0"))
            assertFalse(output.contains("filament_colour_type"))
            assertFalse(output.contains("; filament:"))
            assertFalse(output.contains("paint_info"))
        } finally {
            source.delete()
            destination.delete()
        }
    }
}
