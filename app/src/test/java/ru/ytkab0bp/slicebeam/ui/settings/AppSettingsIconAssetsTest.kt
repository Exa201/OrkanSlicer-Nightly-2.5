package ru.ytkab0bp.slicebeam.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.ytkab0bp.slicebeam.R

class AppSettingsIconAssetsTest {
    @Test
    fun allEightAppSettingsIconsAreRecognizedAsFullColorAssets() {
        val assets = listOf(
            R.drawable.ic_settings,
            R.drawable.ic_maker_open,
            R.drawable.ic_user_interface,
            R.drawable.ic_printer_profiles,
            R.drawable.ic_converter_storage,
            R.drawable.ic_view,
            R.drawable.ic_app_information,
            R.drawable.ic_reset,
        )
        assertEquals(8, assets.distinct().size)
        assets.forEach { assertTrue(AppSettingsIconAssets.isPackAsset(it)) }
    }

    @Test
    fun unrelatedArtworkIsNotTreatedAsAnAppSettingsPackIcon() {
        assertFalse(AppSettingsIconAssets.isPackAsset(R.drawable.menu3d_tools))
        assertFalse(AppSettingsIconAssets.isPackAsset(R.drawable.ic_speichern))
    }
}
