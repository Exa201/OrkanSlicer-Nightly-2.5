package ru.ytkab0bp.slicebeam.components.bed_menu

import ru.ytkab0bp.slicebeam.config.ProfileStringVectorCodec

/** Resolves the selectable project filaments for Orca calibration jobs. */
internal object CalibrationFilamentSelectionLogic {
    private val vectorKeys = listOf(
        "filament_type",
        "filament_colour",
        "filament_settings_id",
        "filament_diameter",
        "filament_map",
    )

    fun count(config: Map<String, String>, requiredFilamentCount: Int): Int {
        val configured = vectorKeys.maxOfOrNull { key ->
            ProfileStringVectorCodec.parse(config[key].orEmpty())?.size ?: 0
        } ?: 0
        return maxOf(1, configured, requiredFilamentCount.coerceAtLeast(1)).coerceAtMost(16)
    }

    fun clamp(filamentId: Int, count: Int): Int = filamentId.coerceIn(0, count.coerceIn(1, 16) - 1)
}
