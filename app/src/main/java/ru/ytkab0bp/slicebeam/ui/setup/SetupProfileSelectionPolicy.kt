package ru.ytkab0bp.slicebeam.ui.setup

internal data class SetupProfileOption(
    val key: String,
    val title: String,
    val thumbnailUrl: String?,
    val selected: Boolean,
)

internal data class SetupProfileSection(
    val key: String,
    val title: String,
    val options: List<SetupProfileOption>,
)

/** Small, UI-independent selection rules shared by the setup screens. */
internal object SetupProfileSelectionPolicy {
    fun shouldShowFilamentVendor(hasSelectedPrinter: Boolean, independentLibrary: Boolean): Boolean =
        hasSelectedPrinter || independentLibrary

    fun toggle(selectedKeys: Set<String>, key: String, selected: Boolean): Set<String> =
        LinkedHashSet(selectedKeys).apply {
            if (selected) add(key) else remove(key)
        }

    fun selectAll(keys: Collection<String>): Set<String> = LinkedHashSet(keys)

    /** Orca model presets may expose nozzle sizes as `nozzle_diameter` instead
     * of the optional `variants` field. */
    fun printerVariants(variants: String?, nozzleDiameters: String?): List<String> =
        (variants?.takeIf { it.isNotBlank() } ?: nozzleDiameters.orEmpty())
            .split(';')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()

    fun isSelectableFilament(title: String?): Boolean =
        !title.isNullOrBlank() &&
            !(title.startsWith("*") && title.endsWith("*")) &&
            !title.startsWith("fdm_filament_", ignoreCase = true)

    /** Match a printer model's default material to Orca's series-scoped names. */
    fun matchesDefaultMaterial(defaultMaterial: String?, profileTitle: String?, printerModel: String?): Boolean {
        if (defaultMaterial.isNullOrBlank() || !isSelectableFilament(profileTitle)) return false
        if (defaultMaterial.equals(profileTitle, ignoreCase = true)) return true

        fun normalizedBase(value: String) = value.substringBefore('@').trim().replace(Regex("\\s+"), " ")
        if (!normalizedBase(defaultMaterial).equals(normalizedBase(profileTitle!!), ignoreCase = true)) return false

        val scope = profileTitle.substringAfter('@', "").lowercase().replace(" ", "")
        if (scope.isBlank()) return true
        val model = printerModel.orEmpty().lowercase().replace(" ", "")
        return when {
            "q2c-series" in scope -> "q2c" in model
            "q2-series" in scope -> "q2" in model && "q2c" !in model
            "q1pro-series" in scope -> "q1pro" in model
            else -> {
                val modelName = printerModel.orEmpty().lowercase().replace(" ", "")
                if (scope.contains("series")) modelName.isNotBlank() && modelName in scope
                else true
            }
        }
    }

    fun canContinue(useCustomProfile: Boolean, selectedPrinterCount: Int): Boolean =
        useCustomProfile || selectedPrinterCount > 0
}
