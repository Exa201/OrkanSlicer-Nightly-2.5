package ru.ytkab0bp.slicebeam.components.bed_menu

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.ytkab0bp.slicebeam.R

class SliceMenuArtworkTest {
    @Test
    fun suppliedArtworkIsMappedToTheExistingSliceActionsAndStates() {
        val actions = listOf(
            R.string.MenuSliceInfo to Triple(R.drawable.ic_print_info, R.drawable.ic_print_info_active, R.drawable.ic_print_info_disabled),
            R.string.MenuSliceLayers to Triple(R.drawable.ic_layers, R.drawable.ic_layers_active, R.drawable.ic_layers_disabled),
            R.string.MenuSliceExportToFile to Triple(R.drawable.ic_export, R.drawable.ic_export_active, R.drawable.ic_export_disabled),
            R.string.MenuSliceShare to Triple(R.drawable.ic_share, R.drawable.ic_share_active, R.drawable.ic_share_disabled),
            R.string.MenuSlicePerformance to Triple(R.drawable.ic_preview_simple, R.drawable.ic_preview_simple_active, R.drawable.ic_preview_simple_disabled),
        )

        actions.forEach { (title, expected) ->
            assertEquals(expected.first, menuActionArtwork(title))
            assertEquals(expected.second, menuActionArtwork(title, selected = true))
            assertEquals(expected.third, menuActionArtwork(title, enabled = false))
        }
    }
}
