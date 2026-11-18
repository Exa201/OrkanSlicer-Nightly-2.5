package ru.ytkab0bp.slicebeam.fragment

import android.content.Context
import ru.ytkab0bp.slicebeam.config.TemporaryProjectSettings
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.recycler.SpaceItem
import ru.ytkab0bp.slicebeam.slic3r.PrintConfigDef
import ru.ytkab0bp.slicebeam.slic3r.Slic3rLocalization
import ru.ytkab0bp.slicebeam.ui.settings.OrcaFilamentSettingsCatalog
import ru.ytkab0bp.slicebeam.ui.settings.OrcaProfileGroupLocalizer
import ru.ytkab0bp.slicebeam.ui.settings.OrcaSettingsCategory
import ru.ytkab0bp.slicebeam.ui.settings.FilamentProfileCategoryAssets
import ru.ytkab0bp.slicebeam.utils.ViewUtils

/** Creates filament-profile rows from Orca metadata and the shared Kotlin catalog. */
object OrcaFilamentSettingsBuilder {
    @JvmStatic
    fun build(fragment: FilamentConfigFragment): List<ProfileOptionElement> {
        val definition = PrintConfigDef.getInstance()
        val context = fragment.getContext() ?: return emptyList()
        val items = ArrayList<ProfileOptionElement>()

        OrcaFilamentSettingsCatalog.categories().forEach { category ->
            addCategory(fragment, context, definition, items, category)
        }
        return items
    }

    private fun addCategory(
        fragment: FilamentConfigFragment,
        context: Context,
        definition: PrintConfigDef,
        items: MutableList<ProfileOptionElement>,
        category: OrcaSettingsCategory,
    ) {
        val categoryIcon = FilamentProfileCategoryAssets.iconFor(category.title, category.iconResId)
        items.add(ProfileOptionElement(categoryIcon, Slic3rLocalization.getString(category.title)).apply {
            noTint = categoryIcon != category.iconResId
        })
        category.sections.forEach { section ->
            items.add(ProfileOptionElement(ProfileSubHeader(localizedGroup(context, section.title))))
            section.keys.forEach { key ->
                val option = definition.options[key]
                if (option != null) {
                    val displayed = if (TemporaryProjectSettings.active) TemporaryProjectSettings.scalarDefinition(option) else option
                    if (TemporaryProjectSettings.active && option.nullable &&
                        option.type == ru.ytkab0bp.slicebeam.slic3r.ConfigOptionDef.ConfigOptionType.BOOLS) {
                        displayed.enumLabels = arrayOf(context.getString(R.string.ProjectBooleanInherit),
                            context.getString(R.string.ProjectBooleanOff), context.getString(R.string.ProjectBooleanOn))
                    }
                    items.add(fragment.createOptionElement(displayed))
                }
            }
            items.add(ProfileOptionElement(SpaceItem(0, ViewUtils.dp(4f))))
        }
    }

    private fun localizedGroup(context: Context, source: String): String {
        @Suppress("DEPRECATION")
        val language = context.resources.configuration.locale.language
        return OrcaProfileGroupLocalizer.filamentGroup(source, language)
    }
}
