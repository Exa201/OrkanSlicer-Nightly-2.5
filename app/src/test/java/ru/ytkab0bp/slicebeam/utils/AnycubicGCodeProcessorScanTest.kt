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
            assertTrue(scan.hasFilamentTypes)
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
    fun scanMarksAbsentFilamentPaletteMetadataAsUnknownRatherThanAssumingDefaultPla() {
        val source = File.createTempFile("kobra-no-filament-meta", ".gcode")
        try {
            source.writeText("; HEADER_BLOCK_START\n; HEADER_BLOCK_END\nT0\nG1 X1 E1\n")
            val scan = AnycubicGCodeProcessor.scan(source)
            assertFalse(scan.hasFilamentTypes)
            assertFalse(scan.hasFilamentColors)
            assertArrayEquals(arrayOf("PLA"), scan.materials)
        } finally {
            source.delete()
        }
    }
}
