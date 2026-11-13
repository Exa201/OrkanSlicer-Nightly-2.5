package ru.ytkab0bp.slicebeam.utils

import java.util.Locale

/** Maps slicer tools through the selected physical gate and Klipper's tool-to-gate map. */
object AnycubicAceRoutingPolicy {
    data class Routing(
        val toolCommands: IntArray,
        val gates: IntArray,
        val unavailableTool: Int?,
        val sharedGateTool: Int? = null,
    )

    /** A single active tool with known material can use the printer's external spool when ACE is absent. */
    @JvmStatic
    fun canUseExternalSpool(externalSpoolReady: Boolean, activeTools: IntArray, materials: List<String>): Boolean {
        if (!externalSpoolReady || activeTools.distinct().size != 1) return false
        val tool = activeTools.single()
        return tool >= 0 && materials.getOrNull(tool)?.trim()?.isNotEmpty() == true
    }

    @JvmStatic
    @JvmOverloads
    fun resolve(
        toolCount: Int,
        usedTools: IntArray,
        loadedGates: IntArray,
        toolToGate: IntArray,
        selectedGates: IntArray? = null,
    ): Routing {
        val count = toolCount.coerceAtLeast(1)
        val validLoadedGates = loadedGates.filter { gate -> toolToGate.indexOf(gate) >= 0 }.distinct()
        val fallbackGate = validLoadedGates.firstOrNull() ?: -1
        val requestedGates = IntArray(count) { tool ->
            selectedGates?.getOrNull(tool) ?: toolToGate.getOrNull(tool) ?: -1
        }
        val gates = IntArray(count) { tool ->
            val requested = requestedGates[tool]
            if (toolToGate.indexOf(requested) >= 0) requested else fallbackGate
        }
        val commands = IntArray(count) { tool -> toolToGate.indexOf(gates[tool]) }
        val unavailable = usedTools.firstOrNull { tool ->
            tool !in 0 until count || (requestedGates.getOrNull(tool) ?: -1) !in loadedGates ||
                commands.getOrNull(tool) == -1
        }
        val shared = MmuGateAssignmentPolicy.firstSharedGateTool(usedTools, gates)
        return Routing(commands, gates, unavailable, shared)
    }

    /** A shared spool is safe only when every slicer tool using it has matching material and temperatures. */
    @JvmStatic
    fun firstIncompatibleSharedGateTool(
        activeTools: IntArray,
        gates: IntArray,
        materials: List<String>,
        initialTemperatures: List<String>,
        temperatures: List<String>,
    ): Int? {
        data class Signature(val material: String, val initial: String, val normal: String)
        val firstByGate = HashMap<Int, Pair<Int, Signature>>()
        for (tool in activeTools) {
            val gate = gates.getOrNull(tool) ?: continue
            val signature = Signature(
                materials.getOrNull(tool).orEmpty().trim().uppercase(),
                normalizeTemperature(initialTemperatures.getOrNull(tool)),
                normalizeTemperature(temperatures.getOrNull(tool)),
            )
            val previous = firstByGate.putIfAbsent(gate, tool to signature)
            if (previous != null && previous.second != signature) return tool
        }
        return null
    }

    @JvmStatic
    fun toolValues(value: String?): List<String> = value.orEmpty()
        .split(',', ';')
        .map(String::trim)

    private fun normalizeTemperature(value: String?): String = value.orEmpty().trim()
        .toDoubleOrNull()?.let { java.lang.Double.toString(it) } ?: value.orEmpty().trim()
}
