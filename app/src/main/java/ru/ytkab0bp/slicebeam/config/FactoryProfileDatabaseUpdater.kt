package ru.ytkab0bp.slicebeam.config

import ru.ytkab0bp.slicebeam.slic3r.Slic3rConfigWrapper

/** Replaces unchanged bundled presets with the newest factory data while preserving user edits. */
object FactoryProfileDatabaseUpdater {
    private const val LEGACY_KOBRA_3_V2_TITLE = "Anycubic Kobra 3 v2 0.4 nozzle - Stock"

    fun refresh(
        installed: Slic3rConfigWrapper,
        latest: Slic3rConfigWrapper,
        previousFactory: Slic3rConfigWrapper?,
    ): Boolean {
        var changed = false
        changed = refreshList(
            installed.printerConfigs,
            latest.printerConfigs,
            previousFactory?.printerConfigs.orEmpty(),
            ConfigObject.PROFILE_LIST_PRINTER,
            installed.presets?.get("printer"),
            installed,
        ) || changed
        changed = refreshList(
            installed.printConfigs,
            latest.printConfigs,
            previousFactory?.printConfigs.orEmpty(),
            ConfigObject.PROFILE_LIST_PRINT,
            installed.presets?.get("print"),
            installed,
        ) || changed
        changed = refreshList(
            installed.filamentConfigs,
            latest.filamentConfigs,
            previousFactory?.filamentConfigs.orEmpty(),
            ConfigObject.PROFILE_LIST_FILAMENT,
            installed.presets?.get("filament"),
            installed,
        ) || changed
        return changed
    }

    private fun refreshList(
        installed: MutableList<ConfigObject>,
        latest: List<ConfigObject>,
        previousFactory: List<ConfigObject>,
        type: Int,
        selectedTitle: String?,
        config: Slic3rConfigWrapper,
    ): Boolean {
        var changed = false
        val latestByTitle = latest.associateBy { it.title }
        val previousByTitle = previousFactory.associateBy { it.title }

        installed.toList().forEach { current ->
            val title = current.title ?: return@forEach
            val isLegacyKobraAlias = type == ConfigObject.PROFILE_LIST_PRINTER && title == LEGACY_KOBRA_3_V2_TITLE
            val oldFactory = previousByTitle[title]
            val replacement = latestByTitle[title] ?: if (isLegacyKobraAlias) {
                latest.firstOrNull {
                    it["printer_model"] == "Anycubic Kobra 3 V2" &&
                        it["printer_variant"] == "0.4" &&
                        it["nozzle_diameter"]?.contains("0.4") == true
                }
            } else null
            val unchangedFactoryProfile = oldFactory != null && equivalent(current, oldFactory)

            if (replacement != null && (unchangedFactoryProfile || isLegacyKobraAlias)) {
                val replacementTitle = replacement.title
                if (replacementTitle != null && replacementTitle != title &&
                    installed.any { it !== current && it.title == replacementTitle }
                ) {
                    installed.remove(current)
                } else {
                    current.setTitle(replacementTitle)
                    current.values.clear()
                    current.values.putAll(replacement.values)
                    current.profileListType = type
                }
                if (selectedTitle == title) {
                    config.presets?.put(
                        when (type) {
                            ConfigObject.PROFILE_LIST_PRINTER -> "printer"
                            ConfigObject.PROFILE_LIST_PRINT -> "print"
                            else -> "filament"
                        },
                        replacementTitle,
                    )
                }
                changed = true
            } else if (unchangedFactoryProfile && !isSelectedPreset(config, type, title)) {
                installed.remove(current)
                changed = true
            }
        }

        latest.forEach { factoryProfile ->
            val title = factoryProfile.title ?: return@forEach
            if (installed.none { it.title == title }) {
                installed.add(ConfigObject(factoryProfile).apply { profileListType = type })
                changed = true
            }
        }
        return changed
    }

    private fun isSelectedPreset(config: Slic3rConfigWrapper, type: Int, title: String): Boolean {
        val key = when (type) {
            ConfigObject.PROFILE_LIST_PRINTER -> "printer"
            ConfigObject.PROFILE_LIST_PRINT -> "print"
            else -> "filament"
        }
        return config.presets?.get(key) == title
    }

    private fun equivalent(first: ConfigObject, second: ConfigObject): Boolean =
        normalizedValues(first) == normalizedValues(second)

    private fun normalizedValues(profile: ConfigObject): Map<String, String> = profile.values.mapValues { (key, value) ->
        ConfigObject.normalizeSerializedValue(key, value) ?: value
    }
}
