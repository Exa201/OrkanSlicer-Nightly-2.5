package ru.ytkab0bp.slicebeam.components.bed_menu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BedMenuActionTileWidthPolicyTest {
    @Test
    fun workspaceMenuKeepsFourColumnsAcrossPhoneWidthsAndFontScales() {
        for (width in listOf(320, 360, 400, 411, 600)) {
            for (scale in listOf(1f, 1.15f, 1.3f, 1.5f, 2f)) {
                assertEquals("width=$width, fontScale=$scale", 4,
                    BedMenuActionTileWidthPolicy.columnsForLayout(scale, width))
            }
        }
    }

    @Test
    fun gridTilesFitViewportIncludingContentPaddingAndColumnSpacing() {
        for ((width, columns) in listOf(260 to 4, 272 to 4, 320 to 4, 366 to 4, 400 to 4, 411 to 4)) {
            val tile = BedMenuActionTileWidthPolicy.gridTileWidthDp(width, columns)
            val used = tile * columns + 8 + 2 * (columns - 1)
            assertTrue("$columns grid columns must fit in $width dp (used $used dp)", used <= width)
            assertTrue("Grid tiles should remain touchable at $width dp", tile >= 48)
        }
    }
}
