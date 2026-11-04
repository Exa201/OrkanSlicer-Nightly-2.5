package ru.ytkab0bp.slicebeam.components

import java.io.File
import org.junit.Assert.*
import org.junit.Test
import ru.ytkab0bp.slicebeam.utils.AnycubicGCodeProcessor

/** Empty ACE slots keep their index; later tools are never shifted onto a different gate. */
class SparseAceRoutingOutputTest {
    @Test fun laterToolKeepsItsSlotAfterAnEmptyGateAndKeepsAllLayers() {
        val source = File.createTempFile("ace-gap-source", ".gcode")
        val output = File.createTempFile("ace-gap-output", ".gcode")
        try {
            source.writeText(
                "; HEADER_BLOCK_START\n" +
                    "; filament: 1,2,3\n" +
                    "; filament_colour = #111111;#222222;#333333\n" +
                    "; filament_type = PETG;PETG;PETG\n" +
                    "; HEADER_BLOCK_END\n" +
                    ";LAYER_CHANGE\nT0\nG1 X1 E1\n" +
                    ";LAYER_CHANGE\nT2\nG1 X3 E3\n" +
                    "M400\n",
            )
            val scan = AnycubicGCodeProcessor.scan(source)
            assertEquals(2, scan.maxTool)
            assertArrayEquals(intArrayOf(0, 2), scan.usedTools)
            assertArrayEquals(intArrayOf(0, 2), scan.firstToolAppearance)
            assertTrue(AnycubicGCodeProcessor.rewrite(source, output, scan,
                AnycubicGCodeProcessor.Options().apply {
                    remappedTools = intArrayOf(0, 1, 2)
                    gates = intArrayOf(0, 1, 2)
                    colors = "#111111;#19C8A0;#333333"
                    materials = "PETG;PLA;PETG"
                }))
            val lines = output.readLines()
            assertEquals(listOf("T0", "T2"), lines.filter { it.matches(Regex("T[0-9]+")) })
            assertTrue(lines.contains("; filament: 1,2,3"))
            assertTrue(lines.contains("; filament_colour = #111111;#19C8A0;#333333"))
            assertEquals(2, lines.count { it == ";LAYER_CHANGE" })
            assertTrue(lines.contains("G1 X3 E3"))
            assertEquals("M400", lines.last())
        } finally {
            source.delete()
            output.delete()
        }
    }

    @Test fun selectedBlueAndWhiteGatesDriveToolCommandsAndGcodePalette() {
        val source = File.createTempFile("ace-color-source", ".gcode")
        val output = File.createTempFile("ace-color-output", ".gcode")
        try {
            source.writeText(
                "; HEADER_BLOCK_START\n" +
                    "; filament: 1,2\n" +
                    "; filament_colour = #212721;#FFFFFF\n" +
                    "; filament_type = PETG;PETG\n" +
                    "; HEADER_BLOCK_END\nT0\nG1 X1 E1\nT1\nG1 X2 E2\nM400\n",
            )
            val scan = AnycubicGCodeProcessor.scan(source)
            val routing = ru.ytkab0bp.slicebeam.utils.AnycubicAceRoutingPolicy.resolve(
                toolCount = 2,
                usedTools = intArrayOf(0, 1),
                loadedGates = intArrayOf(0, 1, 2, 3),
                toolToGate = intArrayOf(0, 1, 2, 3),
                selectedGates = intArrayOf(1, 3),
            )
            assertNull(routing.unavailableTool)
            val physical = listOf(
                ru.ytkab0bp.slicebeam.utils.FilamentSlot(0xFFFFFF, "PETG"),
                ru.ytkab0bp.slicebeam.utils.FilamentSlot(0x0047BB, "PLA"),
            )
            val outputTools = routing.toolCommands
            val paletteColors = AnycubicGCodeProcessor.formatMappedPalette(outputTools, physical, true)
            val paletteTypes = AnycubicGCodeProcessor.formatMappedPalette(outputTools, physical, false)
            assertTrue(AnycubicGCodeProcessor.rewrite(source, output, scan,
                AnycubicGCodeProcessor.Options().apply {
                    remappedTools = outputTools
                    gates = routing.gates
                    colors = paletteColors
                    materials = paletteTypes
                }))

            val lines = output.readLines()
            assertEquals(listOf("T1", "T3"), lines.filter { it.matches(Regex("T[0-9]+")) })
            assertTrue(lines.contains("; filament: 2,4"))
            assertTrue(lines.contains("; filament_colour = #19C8A0;#FFFFFF;#19C8A0;#0047BB"))
            assertTrue(lines.contains("; filament_type = PLA;PETG;PLA;PLA"))
        } finally {
            source.delete()
            output.delete()
        }
    }

    @Test fun sharedWhiteGateCollapsesColorPaletteAndRemapsTemperatureSelectors() {
        val source = File.createTempFile("ace-shared-source", ".gcode")
        val output = File.createTempFile("ace-shared-output", ".gcode")
        try {
            source.writeText(
                "; HEADER_BLOCK_START\n; filament: 1,2\n" +
                    "; filament_colour = #111111;#FFFFFF\n; filament_type = PETG;PETG\n" +
                    "; filament used [g] = 1.25,2.75\n; total filament used [g] = 4\n" +
                    "; HEADER_BLOCK_END\nM104 S240 T0\nM109 S240 T1\nT0\nG1 X1 E1\nT1\nG1 X2 E2\nM400\n",
            )
            val scan = AnycubicGCodeProcessor.scan(source)
            val routing = ru.ytkab0bp.slicebeam.utils.AnycubicAceRoutingPolicy.resolve(
                toolCount = 2,
                usedTools = intArrayOf(0, 1),
                loadedGates = intArrayOf(0, 1, 2, 3),
                toolToGate = intArrayOf(0, 1, 2, 3),
                selectedGates = intArrayOf(3, 3),
            )
            assertNull(routing.unavailableTool)
            assertEquals(1, routing.sharedGateTool)
            assertNull(ru.ytkab0bp.slicebeam.utils.AnycubicAceRoutingPolicy.firstIncompatibleSharedGateTool(
                intArrayOf(0, 1), routing.gates, scan.materials.toList(), listOf("80", "80"), listOf("240", "240"),
            ))
            val physicalSlots = listOf(
                ru.ytkab0bp.slicebeam.utils.FilamentSlot(0xFFFFFF, "PETG"),
                ru.ytkab0bp.slicebeam.utils.FilamentSlot(0xFFFFFF, "PETG"),
            )
            val outputPalette = AnycubicGCodeProcessor.formatMappedPalette(routing.toolCommands, physicalSlots, true)
            assertEquals("#19C8A0;#19C8A0;#19C8A0;#FFFFFF", outputPalette)
            assertTrue(AnycubicGCodeProcessor.rewrite(source, output, scan,
                AnycubicGCodeProcessor.Options().apply {
                    remappedTools = routing.toolCommands
                    gates = routing.gates
                    colors = outputPalette
                    materials = AnycubicGCodeProcessor.formatMappedPalette(routing.toolCommands, physicalSlots, false)
                }))
            val lines = output.readLines()
            assertTrue(lines.contains("M104 S240 T3"))
            assertTrue(lines.contains("M109 S240 T3"))
            assertEquals(listOf("T3", "T3"), lines.filter { it.matches(Regex("T[0-9]+")) })
            assertTrue(lines.contains("; filament_colour = #19C8A0;#19C8A0;#19C8A0;#FFFFFF"))
            assertTrue(lines.contains("; filament_type = PLA;PLA;PLA;PETG"))
            assertTrue(lines.contains("; filament used [g] = 0.00,0.00,0.00,4.00"))
        } finally {
            source.delete()
            output.delete()
        }
    }

    @Test fun rewrittenGcodeUsesSelectedPhysicalGateForOneThroughSixteenTools() {
        for (count in 1..16) {
            val source = File.createTempFile("ace-count-source", ".gcode")
            val output = File.createTempFile("ace-count-output", ".gcode")
            try {
                val commands = (0 until count).joinToString("\n") { "T$it\nG1 X$it E1" }
                source.writeText("; HEADER_BLOCK_START\n; HEADER_BLOCK_END\n$commands\nM400\n")
                val scan = AnycubicGCodeProcessor.scan(source)
                val selectedGates = IntArray(count) { count - it - 1 }
                val firmwareToolToGate = IntArray(16) { 15 - it }
                val routing = ru.ytkab0bp.slicebeam.utils.AnycubicAceRoutingPolicy.resolve(
                    toolCount = count,
                    usedTools = scan.usedTools,
                    loadedGates = IntArray(16) { it },
                    toolToGate = firmwareToolToGate,
                    selectedGates = selectedGates,
                )
                assertNull("unavailable tool count=$count", routing.unavailableTool)
                assertTrue("rewrite count=$count", AnycubicGCodeProcessor.rewrite(source, output, scan,
                    AnycubicGCodeProcessor.Options().apply {
                        remappedTools = routing.toolCommands
                        gates = routing.gates
                    }))
                val expectedFirmwareCommands = selectedGates.map { gate -> "T${firmwareToolToGate.indexOf(gate)}" }
                assertEquals("commands count=$count", expectedFirmwareCommands,
                    output.readLines().filter { it.matches(Regex("T[0-9]+")) })
            } finally {
                source.delete()
                output.delete()
            }
        }
    }

    @Test fun oneSlicerToolAssignedToSecondAceGateEmitsFirmwareToolT1() {
        val source = File.createTempFile("ace-single-source", ".gcode")
        val output = File.createTempFile("ace-single-output", ".gcode")
        try {
            source.writeText("; HEADER_BLOCK_START\n; HEADER_BLOCK_END\nT0\nG1 X1 E1\nM400\n")
            val scan = AnycubicGCodeProcessor.scan(source)
            val routing = ru.ytkab0bp.slicebeam.utils.AnycubicAceRoutingPolicy.resolve(
                toolCount = 1,
                usedTools = scan.usedTools,
                loadedGates = intArrayOf(0, 1, 2, 3),
                toolToGate = intArrayOf(0, 1, 2, 3),
                selectedGates = intArrayOf(1),
            )

            assertNull(routing.unavailableTool)
            assertEquals(1, routing.toolCommands.single())
            assertTrue(AnycubicGCodeProcessor.rewrite(source, output, scan,
                AnycubicGCodeProcessor.Options().apply {
                    remappedTools = routing.toolCommands
                    gates = routing.gates
                }))
            assertEquals(listOf("T1"), output.readLines().filter { it.matches(Regex("T[0-9]+")) })
        } finally {
            source.delete()
            output.delete()
        }
    }
}
