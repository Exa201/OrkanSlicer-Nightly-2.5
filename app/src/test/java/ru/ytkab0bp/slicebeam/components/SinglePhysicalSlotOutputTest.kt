package ru.ytkab0bp.slicebeam.components

import java.io.File
import org.junit.Assert.*
import org.junit.Test
import ru.ytkab0bp.slicebeam.utils.AnycubicGCodeProcessor

class SinglePhysicalSlotOutputTest {
    @Test fun implicitFirstToolIsExplicitlyRoutedToSlotFourWithoutDroppingLayers() {
        val source = File.createTempFile("slot-source", ".gcode")
        val output = File.createTempFile("slot-output", ".gcode")
        try {
            source.writeText("; HEADER_BLOCK_START\n; HEADER_BLOCK_END\nG28\n;LAYER_CHANGE\nG1 X10 E1\n;LAYER_CHANGE\nG1 X20 E2\nM400\n")
            assertFalse(AnycubicGCodeProcessor.scan(source).hasToolSelection)
            AnycubicGCodeProcessor.rewrite(source, output, AnycubicGCodeProcessor.scan(source), AnycubicGCodeProcessor.Options().apply {
                remappedTools = intArrayOf(3)
                gates = intArrayOf(3)
                versionName = "test"
            })
            val result = output.readText()
            assertTrue("Slot four must explicitly select printer tool T3", result.lines().contains("T3"))
            assertTrue("Slot selection must precede extrusion", result.indexOf("T3\n") < result.indexOf("G1 X10 E1"))
            assertTrue(result.contains("; filament: 4"))
            assertEquals(2, result.lines().count { it == ";LAYER_CHANGE" })
            assertTrue(result.contains("M400"))
        } finally { source.delete(); output.delete() }
    }
    @Test fun canonicalFanCommandSelectsTheSlotOnceBeforeFirstRetraction() {
        val source = File.createTempFile("slot-source", ".gcode")
        val output = File.createTempFile("slot-output", ".gcode")
        try {
            source.writeText("; HEADER_BLOCK_END\nG28\nM106 S0\n;LAYER_CHANGE\nG1 E-1 F3600\nG1 X10 E1\nM400\n")
            assertTrue(AnycubicGCodeProcessor.rewrite(source, output, AnycubicGCodeProcessor.scan(source), AnycubicGCodeProcessor.Options().apply {
                remappedTools = intArrayOf(1)
                gates = intArrayOf(1)
            }))
            val result = output.readText()
            assertEquals(1, result.lines().count { it == "T1" })
            assertTrue(result.indexOf("T1\n") < result.indexOf("M106 S0"))
        } finally { source.delete(); output.delete() }
    }

    @Test fun extrusionMentionedOnlyInACommentDoesNotActivateTheSlot() {
        val source = File.createTempFile("slot-source", ".gcode")
        val output = File.createTempFile("slot-output", ".gcode")
        try {
            source.writeText("; HEADER_BLOCK_END\nG1 X10 ; E1 is only a comment\nG1 E-1 F3600\nM400\n")
            AnycubicGCodeProcessor.rewrite(source, output, AnycubicGCodeProcessor.scan(source), AnycubicGCodeProcessor.Options().apply {
                remappedTools = intArrayOf(2)
                gates = intArrayOf(2)
            })
            val result = output.readText()
            assertEquals(1, result.lines().count { it == "T2" })
            assertTrue(result.indexOf("T2\n") > result.indexOf("G1 X10"))
            assertTrue(result.indexOf("T2\n") < result.indexOf("G1 E-1"))
        } finally { source.delete(); output.delete() }
    }

    @Test fun externalSpoolKeepsKnownMaterialAndColorWithoutAceMetadata() {
        val source = File.createTempFile("external-spool-source", ".gcode")
        val output = File.createTempFile("external-spool-output", ".gcode")
        try {
            source.writeText("; HEADER_BLOCK_START\n; HEADER_BLOCK_END\nT0\nG9111 bedTemp=60 T0 M117\nM104 T0 S210\nG1 X10 E1\n; comment T0 stays documentation\n")
            val scan = AnycubicGCodeProcessor.scan(source)
            assertFalse(scan.hasFilamentTypes)
            assertTrue(AnycubicGCodeProcessor.rewrite(source, output, scan, AnycubicGCodeProcessor.Options().apply {
                remappedTools = intArrayOf(0)
                gates = intArrayOf(-1)
                externalSpool = true
                materials = "PETG"
                colors = "#0055FF"
            }))

            val result = output.readText()
            assertTrue(result.contains("; filament_type = PETG"))
            assertTrue(result.contains("; filament_colour = #0055FF"))
            assertFalse(result.contains("; paint_info ="))
            assertFalse(result.contains("; filament: "))
            val aceSelector = Regex("(?i)(?<![A-Z0-9_])T[0-9]{1,2}(?![0-9])")
            assertFalse("ACE tool selectors must be absent in external spool mode",
                result.lines().any { aceSelector.containsMatchIn(it.substringBefore(';')) })
            assertTrue("embedded G9111 tool selector must be removed", Regex("G9111 bedTemp=60\\s+M117").containsMatchIn(result))
            assertTrue("single-nozzle heater command must remain usable without a tool selector", Regex("M104\\s+S210").containsMatchIn(result))
            assertTrue("tool text in comments must remain unchanged", result.contains("; comment T0 stays documentation"))
        } finally { source.delete(); output.delete() }
    }

    @Test fun externalSpoolDoesNotInsertAnImplicitFirstAceTool() {
        val source = File.createTempFile("external-spool-no-tool-source", ".gcode")
        val output = File.createTempFile("external-spool-no-tool-output", ".gcode")
        try {
            source.writeText("; HEADER_BLOCK_END\nG28\nG1 X10 E1\n")
            val scan = AnycubicGCodeProcessor.scan(source)
            assertFalse(scan.hasToolSelection)
            assertTrue(AnycubicGCodeProcessor.rewrite(source, output, scan, AnycubicGCodeProcessor.Options().apply {
                remappedTools = intArrayOf(0)
                gates = intArrayOf(-1)
                externalSpool = true
                materials = "PLA"
            }))
            val result = output.readText()
            assertFalse("external spool mode must not synthesize T0", Regex("(?m)^\\s*T[0-9]+(?:\\s|$)").containsMatchIn(result))
            assertTrue("the model extrusion must be retained", result.contains("G1 X10 E1"))
        } finally { source.delete(); output.delete() }
    }
}
