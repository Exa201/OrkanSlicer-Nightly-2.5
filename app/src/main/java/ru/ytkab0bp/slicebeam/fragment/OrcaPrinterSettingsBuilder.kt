package ru.ytkab0bp.slicebeam.fragment

import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.recycler.SpaceItem
import ru.ytkab0bp.slicebeam.slic3r.ConfigOptionDef
import ru.ytkab0bp.slicebeam.slic3r.PrintConfigDef
import ru.ytkab0bp.slicebeam.slic3r.Slic3rLocalization
import ru.ytkab0bp.slicebeam.ui.settings.OrcaPrinterSettingsCatalog
import ru.ytkab0bp.slicebeam.ui.settings.PrinterProfileCategoryAssets
import ru.ytkab0bp.slicebeam.ui.settings.OrcaSettingsCategory
import ru.ytkab0bp.slicebeam.ui.settings.OrcaSettingsSection
import ru.ytkab0bp.slicebeam.utils.ViewUtils

/** Builds printer-profile rows while expanding Orca's per-extruder settings for the active printer. */
object OrcaPrinterSettingsBuilder {
    @JvmStatic
    fun build(fragment: PrinterConfigFragment): List<ProfileOptionElement> {
        val definition = PrintConfigDef.getInstance()
        val items = ArrayList<ProfileOptionElement>()

        OrcaPrinterSettingsCatalog.leadingCategories().forEach { category ->
            addCategory(fragment, definition, items, category)
        }

        val extruderCount = fragment.getConfiguredExtruderCount()
        repeat(extruderCount) { index ->
            val optionIndex = if (extruderCount == 1) -1 else index
            val title = String.format(Slic3rLocalization.getString("Extruder %d"), index + 1)
            items.add(ProfileOptionElement(PrinterProfileCategoryAssets.iconFor(title, R.drawable.printerprofile_extruder_1), title).apply { noTint = true })
            OrcaPrinterSettingsCatalog.extruderSections().forEach { section ->
                addSection(fragment, definition, items, section, optionIndex)
            }
        }

        OrcaPrinterSettingsCatalog.trailingCategories().forEach { category ->
            addCategory(fragment, definition, items, category)
        }
        return items
    }

    private fun addCategory(
        fragment: PrinterConfigFragment,
        definition: PrintConfigDef,
        items: MutableList<ProfileOptionElement>,
        category: OrcaSettingsCategory,
    ) {
        val categoryIcon = PrinterProfileCategoryAssets.iconFor(category.title, category.iconResId)
        items.add(ProfileOptionElement(categoryIcon, Slic3rLocalization.getString(category.title)).apply {
            noTint = categoryIcon != category.iconResId
        })
        category.sections.forEach { section ->
            addSection(fragment, definition, items, section, -1)
        }
    }

    private fun addSection(
        fragment: PrinterConfigFragment,
        definition: PrintConfigDef,
        items: MutableList<ProfileOptionElement>,
        section: OrcaSettingsSection,
        extruderIndex: Int,
    ) {
        if (section.keys == listOf("physical_extruder_map") && fragment.getConfiguredExtruderCount() <= 1) return
        items.add(ProfileOptionElement(ProfileSubHeader(localized(section.title))))
        section.keys.forEach { key ->
            val option = definition.options[key]
            if (option != null) addOption(items, fragment, option, extruderIndex)
        }
        items.add(ProfileOptionElement(SpaceItem(0, ViewUtils.dp(4f))))
    }

    private fun addOption(
        items: MutableList<ProfileOptionElement>,
        fragment: PrinterConfigFragment,
        option: ConfigOptionDef,
        extruderIndex: Int,
    ) {
        items.add(fragment.createOptionElement(option, extruderIndex))
    }

    private fun localized(source: String): String {
        val translated = Slic3rLocalization.getString(source)
        return if (translated.isNullOrEmpty()) source else translated
    }
}
