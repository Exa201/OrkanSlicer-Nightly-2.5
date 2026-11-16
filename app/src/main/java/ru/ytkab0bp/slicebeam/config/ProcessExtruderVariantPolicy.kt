package ru.ytkab0bp.slicebeam.config

/** Orca per-nozzle process-column expansion, adapted for OrkanSlicer's profile value maps. */
object ProcessExtruderVariantPolicy {
    private const val TOOL_IDS = "print_extruder_id"
    private const val TOOL_VARIANTS = "print_extruder_variant"

    /**
     * Adds missing printer-tool/variant columns to a multi-column process profile.
     * A single existing column intentionally remains shared by every tool, matching Orca.
     * Values for a new tool are copied from tool 1's matching variant, or the first column
     * when tool 1 has no such variant. Only option keys declared per-nozzle are expanded.
     */
    fun addMissingToolColumns(
        processValues: Map<String, String>,
        variantsByTool: List<String>,
        perNozzleOptionKeys: Set<String>,
    ): Map<String, String> {
        val ids = split(processValues[TOOL_IDS])
        val variants = ProfileStringVectorCodec.parse(processValues[TOOL_VARIANTS].orEmpty()) ?: return processValues
        if (ids.size < 2 || ids.size != variants.size || variantsByTool.isEmpty()) return processValues

        val updatedIds = ids.toMutableList()
        val updatedVariants = variants.toMutableList()
        val sources = mutableListOf<Int>()
        variantsByTool.forEachIndexed { toolIndex, rawVariants ->
            val toolId = toolIndex + 1
            if (updatedIds.any { it.toIntOrNull() == toolId }) return@forEachIndexed
            rawVariants.split(',').map(String::trim).filter(String::isNotBlank).forEach { variant ->
                val source = ids.indices.firstOrNull { ids[it].toIntOrNull() == 1 && variants[it] == variant } ?: 0
                updatedIds += toolId.toString()
                updatedVariants += variant
                sources += source
            }
        }
        if (sources.isEmpty()) return processValues

        val result = processValues.toMutableMap()
        result[TOOL_IDS] = updatedIds.joinToString(",")
        result[TOOL_VARIANTS] = ProfileStringVectorCodec.serialize(updatedVariants)
        perNozzleOptionKeys.forEach { key ->
            val values = split(processValues[key])
            if (values.size == ids.size) result[key] = (values + sources.map(values::get)).joinToString(",")
        }
        return result
    }

    private fun split(value: String?): List<String> = value
        ?.split(',')
        ?.map(String::trim)
        .orEmpty()
}
