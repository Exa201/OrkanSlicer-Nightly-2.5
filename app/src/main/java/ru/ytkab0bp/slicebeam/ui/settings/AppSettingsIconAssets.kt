package ru.ytkab0bp.slicebeam.ui.settings

import androidx.annotation.DrawableRes
import ru.ytkab0bp.slicebeam.R

/** Full-color app-settings artwork from the supplied settings icon pack. */
internal object AppSettingsIconAssets {
    fun isPackAsset(@DrawableRes icon: Int): Boolean = icon in setOf(
        R.drawable.ic_settings,
        R.drawable.ic_maker_open,
        R.drawable.ic_user_interface,
        R.drawable.ic_printer_profiles,
        R.drawable.ic_converter_storage,
        R.drawable.ic_view,
        R.drawable.ic_app_information,
        R.drawable.ic_reset,
    )
}
