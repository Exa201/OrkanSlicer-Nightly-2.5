package ru.ytkab0bp.slicebeam.utils

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnycubicAceRoutingPolicyTest {
    @Test fun keepsUnusedHolesAndRoutesUsedToolsToTheirDefaultLoadedGates() {
        val routing = AnycubicAceRoutingPolicy.resolve(
            toolCount = 3,
            usedTools = intArrayOf(0, 2),
            loadedGates = intArrayOf(0, 2, 3),
            toolToGate = intArrayOf(0, 1, 2),
        )

        assertNull(routing.unavailableTool)
        assertArrayEquals(intArrayOf(0, 1, 2), routing.toolCommands)
        assertArrayEquals(intArrayOf(0, 1, 2), routing.gates)
    }

    @Test fun neverMovesAToolToAnotherLoadedGateWhenItsMatchingSlotIsEmpty() {
        val routing = AnycubicAceRoutingPolicy.resolve(
            toolCount = 3,
            usedTools = intArrayOf(1, 2),
            loadedGates = intArrayOf(0, 2),
            toolToGate = intArrayOf(0, 1, 2),
        )

        assertEquals(1, routing.unavailableTool)
        assertArrayEquals(intArrayOf(0, 1, 2), routing.toolCommands)
        assertArrayEquals(intArrayOf(0, 1, 2), routing.gates)
    }

    @Test fun routesSelectedPhysicalGatesThroughFirmwareToolMap() {
        val routing = AnycubicAceRoutingPolicy.resolve(
            toolCount = 4,
            usedTools = intArrayOf(0, 1),
            loadedGates = intArrayOf(0, 1, 2, 3),
            // Firmware tool order differs from physical ACE gate order.
            toolToGate = intArrayOf(0, 2, 3, 1),
            selectedGates = intArrayOf(1, 3, 2, 0),
        )

        assertNull(routing.unavailableTool)
        assertNull(routing.sharedGateTool)
        assertArrayEquals(intArrayOf(3, 2, 1, 0), routing.toolCommands)
        assertArrayEquals(intArrayOf(1, 3, 2, 0), routing.gates)
    }

    @Test fun rejectsDistinctActiveToolsAssignedToTheSamePhysicalGate() {
        val routing = AnycubicAceRoutingPolicy.resolve(
            toolCount = 2,
            usedTools = intArrayOf(0, 1),
            loadedGates = intArrayOf(0, 1, 2, 3),
            toolToGate = intArrayOf(0, 1, 2, 3),
            selectedGates = intArrayOf(3, 3),
        )

        assertNull(routing.unavailableTool)
        assertEquals(1, routing.sharedGateTool)
    }

    @Test fun allowsSharedGateWhenMaterialAndTemperaturesMatch() {
        val incompatible = AnycubicAceRoutingPolicy.firstIncompatibleSharedGateTool(
            intArrayOf(0, 1), intArrayOf(2, 2), listOf("PETG", "petg"),
            AnycubicAceRoutingPolicy.toolValues("80;80"), listOf("240", "240.0"),
        )
        assertNull(incompatible)
    }

    @Test fun detectsSharedGateWithDifferentMaterialOrTemperature() {
        assertEquals(1, AnycubicAceRoutingPolicy.firstIncompatibleSharedGateTool(
            intArrayOf(0, 1), intArrayOf(2, 2), listOf("PETG", "PLA"),
            listOf("80", "80"), listOf("240", "240"),
        ))
        assertEquals(1, AnycubicAceRoutingPolicy.firstIncompatibleSharedGateTool(
            intArrayOf(0, 1), intArrayOf(2, 2), listOf("PETG", "PETG"),
            listOf("80", "80"), listOf("240", "210"),
        ))
    }

    @Test fun rejectsASelectedGateThatIsNoLongerLoadedInsteadOfSubstitutingAnother() {
        val routing = AnycubicAceRoutingPolicy.resolve(
            toolCount = 2,
            usedTools = intArrayOf(0, 1),
            loadedGates = intArrayOf(0, 1),
            toolToGate = intArrayOf(0, 1, 2, 3),
            selectedGates = intArrayOf(0, 3),
        )

        assertEquals(1, routing.unavailableTool)
    }

    @Test fun routesOneThroughSixteenLogicalToolsUsingTheFullFirmwareGateMap() {
        val fullFirmwareMap = IntArray(16) { it }
        for (count in 1..16) {
            val selectedGates = IntArray(count) { count - it - 1 }
            val routing = AnycubicAceRoutingPolicy.resolve(
                toolCount = count,
                usedTools = IntArray(count) { it },
                loadedGates = IntArray(16) { it },
                toolToGate = fullFirmwareMap,
                selectedGates = selectedGates,
            )

            assertNull("unavailable tool count=$count", routing.unavailableTool)
            assertArrayEquals("physical gates count=$count", selectedGates, routing.gates)
            assertArrayEquals("printer commands count=$count", selectedGates, routing.toolCommands)
        }
    }
}
