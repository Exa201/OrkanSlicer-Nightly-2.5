package ru.ytkab0bp.slicebeam.components.bed_menu

import org.junit.Assert.assertEquals
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
    fun portraitMenuUsesOneRowThroughFourActionsAndCapsVisibleRowsAtTwo() {
        assertEquals(1, BedMenuActionTileWidthPolicy.visibleRowsForActionCount(0))
        assertEquals(1, BedMenuActionTileWidthPolicy.visibleRowsForActionCount(1))
        assertEquals(1, BedMenuActionTileWidthPolicy.visibleRowsForActionCount(4))
        assertEquals(2, BedMenuActionTileWidthPolicy.visibleRowsForActionCount(5))
        assertEquals(2, BedMenuActionTileWidthPolicy.visibleRowsForActionCount(8))
        assertEquals(2, BedMenuActionTileWidthPolicy.visibleRowsForActionCount(9))
    }

    @Test
    fun portraitMenuTileHeightIsReducedByTwentyPercentWithoutFallingBelowTouchSize() {
        assertEquals(75, BedMenuActionTileWidthPolicy.reducedTileHeightDp(94))
        assertEquals(89, BedMenuActionTileWidthPolicy.reducedTileHeightDp(112))
        assertEquals(48, BedMenuActionTileWidthPolicy.reducedTileHeightDp(48))
        assertEquals(61, BedMenuActionTileWidthPolicy.reducedTileHeightDp(77))
    }

    @Test
    fun longOrcaLabelsScaleDownAtLargeSystemTextSizes() {
        assertEquals(1f, BedMenuActionTileWidthPolicy.labelScaleForFontScale(1f), 0f)
        assertEquals(1f, BedMenuActionTileWidthPolicy.labelScaleForFontScale(1.29f), 0f)
        assertEquals(0.75f, BedMenuActionTileWidthPolicy.labelScaleForFontScale(1.3f), 0f)
        assertEquals(0.6f, BedMenuActionTileWidthPolicy.labelScaleForFontScale(1.5f), 0f)
        assertEquals(0.6f, BedMenuActionTileWidthPolicy.labelScaleForFontScale(2f), 0f)
    }
}
