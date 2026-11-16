package ru.ytkab0bp.slicebeam.fragment

import ru.ytkab0bp.slicebeam.recycler.SpaceItem
import ru.ytkab0bp.slicebeam.slic3r.ConfigOptionDef
import ru.ytkab0bp.slicebeam.slic3r.PrintConfigDef
import ru.ytkab0bp.slicebeam.ui.settings.OrcaProcessSettingsCatalog
import ru.ytkab0bp.slicebeam.ui.settings.ProfileCategoryAssets
import ru.ytkab0bp.slicebeam.ui.settings.OrcaConditionalSettingsPolicy
import ru.ytkab0bp.slicebeam.ui.settings.OrcaSettingsCategory
import ru.ytkab0bp.slicebeam.utils.ViewUtils

/** Builds process-profile rows from the Kotlin category catalog and Orca's native option schema. */
object OrcaPrintSettingsBuilder {
    @JvmStatic
    fun build(fragment: PrintConfigFragment): List<ProfileOptionElement> {
        val definition = PrintConfigDef.getInstance()
        val addedKeys = HashSet<String>()
        val items = ArrayList<ProfileOptionElement>()
        fragment.toolColumnSelector()?.let(items::add)
        val showLightningOptions = OrcaConditionalSettingsPolicy.showLightningOptions(
            fragment.currentSettingValue("sparse_infill_density"),
            fragment.currentSettingValue("sparse_infill_pattern"),
        )
        val showInfillAnchorOptions = OrcaConditionalSettingsPolicy.showInfillAnchorOptions(
            fragment.currentSettingValue("sparse_infill_density"),
            fragment.currentSettingValue("sparse_infill_pattern"),
        )

        OrcaProcessSettingsCatalog.categories().forEach { category ->
            items.addAll(buildCategory(
                fragment, definition, addedKeys, category, showLightningOptions, showInfillAnchorOptions,
            ))
        }
        return items
    }

    private fun buildCategory(
        fragment: PrintConfigFragment,
        definition: PrintConfigDef,
        addedKeys: MutableSet<String>,
        category: OrcaSettingsCategory,
        showLightningOptions: Boolean,
        showInfillAnchorOptions: Boolean,
    ): List<ProfileOptionElement> {
        val items = ArrayList<ProfileOptionElement>()
        val categoryIcon = ProfileCategoryAssets.iconFor(category.title, category.iconResId)
        items.add(ProfileOptionElement(categoryIcon, category.title).apply { noTint = categoryIcon != category.iconResId })

        category.sections.forEach { section ->
            if (!OrcaConditionalSettingsPolicy.showSection(
                    section.title, showLightningOptions, showInfillAnchorOptions,
                )) return@forEach
            items.add(ProfileOptionElement(ProfileSubHeader(section.title)))
            section.keys.forEach { key ->
                val option = definition.options[key]
                // Keep a bad catalog edit from showing one Orca setting twice.
                if (option != null && addedKeys.add(key)) {
                    val visibilityDependency = key == "sparse_infill_density" || key == "sparse_infill_pattern"
                    items.add(fragment.createOptionElement(
                        option,
                        if (option.perNozzle) fragment.selectedToolColumnIndex() else -1,
                        if (visibilityDependency) Runnable { fragment.refreshConditionalSettings() } else null,
                    ))
                }
            }
            items.add(ProfileOptionElement(SpaceItem(0, ViewUtils.dp(4f))))
        }
        return items
    }
}
