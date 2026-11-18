package ru.ytkab0bp.slicebeam.fragment

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.config.ConfigObject
import ru.ytkab0bp.slicebeam.slic3r.ConfigOptionDef
import ru.ytkab0bp.slicebeam.slic3r.Slic3rLocalization
import ru.ytkab0bp.slicebeam.ui.settings.ProfileFilterBar
import ru.ytkab0bp.slicebeam.ui.settings.ProfileSettingsFilter
import ru.ytkab0bp.slicebeam.ui.settings.ProfileSettingsViewModel
import ru.ytkab0bp.slicebeam.ui.settings.PrinterProfileCategoryAssets
import ru.ytkab0bp.slicebeam.utils.Prefs
import java.util.HashSet
import java.util.function.BooleanSupplier
import java.util.function.Predicate

/** Owns the persisted search, visibility level, and favorites state for profile settings. */
class ProfileSettingsFilterController(
    private val profileType: Int,
    private val tabsEnabled: Boolean,
    private val viewModel: ProfileSettingsViewModel?,
    private val resetSelectedTab: Runnable,
    private val applyFilter: Runnable,
) {
    private var query = if (viewModel != null && profileType != -1) {
        viewModel.searchQuery(profileType)
    } else ""
    private var level = Prefs.getPrefs()?.getInt(PREF_SETTINGS_LEVEL, STANDARD_LEVEL) ?: STANDARD_LEVEL
    private var favoritesOnly = if (viewModel != null && profileType != -1) {
        viewModel.favoritesOnly(profileType)
    } else false
    private var filtersExpanded by mutableStateOf(query.isNotBlank() || favoritesOnly)
    private val favoriteKeys = HashSet<String>(
        Prefs.getPrefs()?.getStringSet(PREF_SETTING_FAVORITES, emptySet()) ?: emptySet(),
    )

    val searchQuery: String
        get() = query

    private fun toggleFilters() {
        filtersExpanded = !filtersExpanded
        if (filtersExpanded || (query.isBlank() && !favoritesOnly)) return
        // A collapsed search must not leave an invisible filter on the settings list.
        query = ""
        favoritesOnly = false
        if (viewModel != null && profileType != -1) {
            viewModel.setSearchQuery(profileType, "")
            viewModel.setFavoritesOnly(profileType, false)
        }
        resetSelectedTab.run()
        applyFilter.run()
    }

    fun emptyStateMessage(context: Context): CharSequence = context.getString(
        when {
            query.isNotBlank() -> R.string.ProfileSettingsEmptySearch
            favoritesOnly -> R.string.ProfileSettingsEmptyFavorites
            level == SIMPLE_LEVEL -> R.string.ProfileSettingsEmptySimple
            else -> R.string.ProfileSettingsEmptyGeneral
        },
    )

    @Composable
    fun ToggleContent() {
        OutlinedIconButton(
            onClick = ::toggleFilters,
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(10.dp),
        ) {
            Icon(
                painterResource(
                    if (profileType == ConfigObject.PROFILE_LIST_PRINTER) {
                        PrinterProfileCategoryAssets.stateIcon(
                            R.drawable.printerprofile_profilsuche,
                            active = filtersExpanded,
                        )
                    } else R.drawable.print_settings_search_24,
                ),
                contentDescription = stringResource(R.string.ProfileSettingsSearchAndFilter),
                modifier = Modifier.size(29.dp),
                tint = if (profileType == ConfigObject.PROFILE_LIST_PRINTER) Color.Unspecified
                    else MaterialTheme.colorScheme.onSurface,
            )
        }
    }

    @Composable
    fun Content(showToggle: Boolean = true) {
        ProfileFilterBar(
            initialQuery = query,
            initialLevel = level,
            initialFavoritesOnly = favoritesOnly,
            expanded = filtersExpanded,
            onToggle = ::toggleFilters,
            showToggle = showToggle,
            onQueryChanged = { updatedQuery ->
                query = updatedQuery
                if (viewModel != null && profileType != -1) {
                    viewModel.setSearchQuery(profileType, updatedQuery)
                }
                resetSelectedTab.run()
                applyFilter.run()
            },
            onLevelChanged = { updatedLevel ->
                if (level != updatedLevel) {
                    level = updatedLevel
                    Prefs.getPrefs()?.edit()?.putInt(PREF_SETTINGS_LEVEL, updatedLevel)?.apply()
                    resetSelectedTab.run()
                    applyFilter.run()
                }
            },
            onFavoritesChanged = { showFavoritesOnly ->
                favoritesOnly = showFavoritesOnly
                if (viewModel != null && profileType != -1) {
                    viewModel.setFavoritesOnly(profileType, showFavoritesOnly)
                }
                resetSelectedTab.run()
                applyFilter.run()
            },
        )
    }

    fun matches(
        definition: ConfigOptionDef?,
        visibleInAdvanced: Boolean,
        queryText: String,
        searchableText: String,
    ): Boolean = ProfileSettingsFilter.matches(
        definition?.key,
        visibleInAdvanced,
        definition != null,
        definition != null && favoriteKeys.contains(definition.key),
        favoritesOnly,
        level,
        queryText,
        searchableText,
    )

    fun filter(items: List<ProfileOptionElement>, accordionCategories: Boolean = false): List<ProfileOptionElement> {
        if (!tabsEnabled || items.isEmpty()) return items
        val optionMatches = Predicate<ProfileOptionElement> { element ->
            val definition = element.optionDef
            val searchableText = if (definition == null) {
                ""
            } else {
                definition.key + " " +
                    Slic3rLocalization.getString(definition.getLabel()) + " " +
                    Slic3rLocalization.getString(definition.getFullLabel()) + " " +
                    Slic3rLocalization.getString(definition.tooltip)
            }
            matches(definition, element.visibleInAdvanced, query, searchableText)
        }
        if (accordionCategories) {
            return ProfileOptionSectionFilter.filterHierarchical(
                items,
                Predicate { element -> element.title != null || element.specialType != -1 },
                Predicate { element -> element.simpleItem is ProfileSubHeader },
                optionMatches,
            )
        }
        return ProfileOptionSectionFilter.filter(
            items,
            Predicate<ProfileOptionElement> { element -> element.title != null || element.specialType != -1 },
            optionMatches,
        )
    }

    internal fun showOptionHelp(
        definition: ConfigOptionDef,
        onShowDialog: (ProfileOptionHelpDialogState) -> Unit,
    ): Boolean =
        ProfileOptionHelpController.show(
            definition,
            favoriteKeys.contains(definition.key),
            favoriteKeys,
            PREF_SETTING_FAVORITES,
            BooleanSupplier { favoritesOnly },
            applyFilter,
            onShowDialog,
        )

    private companion object {
        const val PREF_SETTINGS_LEVEL = "profile_settings_level"
        const val PREF_SETTING_FAVORITES = "profile_setting_favorites"
        const val SIMPLE_LEVEL = 0
        const val STANDARD_LEVEL = 1
    }
}
