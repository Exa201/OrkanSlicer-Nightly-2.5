package ru.ytkab0bp.slicebeam.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import ru.ytkab0bp.slicebeam.R

/** Stable destinations for the model workspace and its adaptive navigation. */
internal enum class WorkspaceDestination(
    @StringRes val titleRes: Int,
    @DrawableRes val iconRes: Int,
    @DrawableRes val selectedIconRes: Int = iconRes,
    @StringRes val navigationTitleRes: Int = titleRes,
) {
    PROJECT(R.string.MenuFile, R.drawable.nav_datei, R.drawable.nav_datei_active),
    MODEL(R.string.MenuModel, R.drawable.nav_modell, R.drawable.nav_modell_active),
    CAMERA(R.string.MenuCamera, R.drawable.ui21_plate),
    EDIT(R.string.MenuToolbar, R.drawable.nav_aendern, R.drawable.nav_aendern_active, R.string.MenuToolbarNavigation),
    TOOLS(R.string.MenuOptions, R.drawable.nav_tools, R.drawable.nav_tools_active),
    SLICE(R.string.MenuSlice, R.drawable.nav_slicen, R.drawable.nav_slicen_active),

    ;

    companion object {
        /** Camera presets stay on the view cube; projection and gesture settings live under App. */
        val visibleNavigationDestinations: List<WorkspaceDestination> = values().filterNot { it == CAMERA }
    }
}
