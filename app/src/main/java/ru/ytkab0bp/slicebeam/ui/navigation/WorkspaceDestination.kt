package ru.ytkab0bp.slicebeam.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import ru.ytkab0bp.slicebeam.R

/** Stable destinations for the model workspace and its adaptive navigation. */
internal enum class WorkspaceDestination(
    @StringRes val titleRes: Int,
    @DrawableRes val iconRes: Int,
    @StringRes val navigationTitleRes: Int = titleRes,
) {
    PROJECT(R.string.MenuFile, R.drawable.menu3d_file_import),
    MODEL(R.string.MenuModel, R.drawable.menu3d_objects),
    CAMERA(R.string.MenuCamera, R.drawable.menu3d_layers),
    EDIT(R.string.MenuToolbar, R.drawable.menu3d_tune, R.string.MenuToolbarNavigation),
    TOOLS(R.string.MenuOptions, R.drawable.menu3d_tools),
    SLICE(R.string.MenuSlice, R.drawable.menu3d_quick_slice),

    ;

    companion object {
        /** Camera presets stay on the view cube; projection and gesture settings live under App. */
        val visibleNavigationDestinations: List<WorkspaceDestination> = values().filterNot { it == CAMERA }
    }
}
