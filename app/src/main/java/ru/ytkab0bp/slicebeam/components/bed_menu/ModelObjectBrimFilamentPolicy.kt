package ru.ytkab0bp.slicebeam.components.bed_menu

/** Uses Orca's 1-based filament IDs while labeling each slot with its UI index. */
internal object ModelObjectBrimFilamentPolicy {
    fun options(materials: List<String>): List<Pair<String, String>> =
        materials.mapIndexed { index, material ->
            (index + 1).toString() to "T$index · ${material.ifBlank { "Filament" }}"
        }

    fun accepts(value: String, options: List<Pair<String, String>>): Boolean =
        value.isBlank() || options.any { (key, _) -> key == value }
}
