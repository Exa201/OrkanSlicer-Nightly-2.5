package ru.ytkab0bp.slicebeam.components.bed_menu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BedMenuActionTileWidthPolicyTest {
    @Test
    fun narrowPhonesAndLargeTextGetFewerWiderColumns() {
        assertEquals(3, BedMenuActionTileWidthPolicy.columnsForLayout(1f, 366))
        assertEquals(4, BedMenuActionTileWidthPolicy.columnsForLayout(1f, 411))
        assertEquals(3, BedMenuActionTileWidthPolicy.columnsForLayout(1.15f, 411))
        assertEquals(2, BedMenuActionTileWidthPolicy.columnsForLayout(1.3f, 320))
    }

    @Test
    fun phoneWidthsAtOneHundredFiftyPercentUseWideActionTiles() {
        assertEquals(1, BedMenuActionTileWidthPolicy.columnsForLayout(1.5f, 320))
        assertEquals(1, BedMenuActionTileWidthPolicy.columnsForLayout(1.5f, 360))
        assertEquals(1, BedMenuActionTileWidthPolicy.columnsForLayout(1.5f, 400))
        assertEquals(2, BedMenuActionTileWidthPolicy.columnsForLayout(1.5f, 401))
        assertEquals(2, BedMenuActionTileWidthPolicy.columnsForLayout(1.3f, 320))
    }

    @Test
    fun fourReadableActionsFitOnAnOrdinaryPhone() {
        for (width in listOf(360, 365, 411)) {
            val tile = BedMenuActionTileWidthPolicy.widthDp(width, 10)
            assertTrue(tile * 4 + 8 <= width)
            assertTrue(tile >= 72)
        }
    }

    @Test
    fun narrowPhonesKeepReadableActionTargets() {
        assertTrue(BedMenuActionTileWidthPolicy.widthDp(320, 10) >= 72)
    }

    @Test
    fun shortMenusKeepAllActionsVisibleWithRoomForTheirLabels() {
        val tile = BedMenuActionTileWidthPolicy.widthDp(411, 3)
        assertTrue(tile * 3 + 8 <= 411)
        assertTrue(tile >= 100)
    }

    @Test
    fun tabletTilesStayCompact() {
        assertTrue(BedMenuActionTileWidthPolicy.widthDp(840, 5) <= 128)
    }

    @Test
    fun gridTilesFitViewportIncludingContentPaddingAndColumnSpacing() {
        for ((width, columns) in listOf(320 to 3, 366 to 3, 400 to 4, 411 to 4)) {
            val tile = BedMenuActionTileWidthPolicy.gridTileWidthDp(width, columns)
            val used = tile * columns + 8 + 2 * (columns - 1)
            assertTrue("$columns grid columns must fit in $width dp (used $used dp)", used <= width)
            assertTrue("Grid tiles should remain touchable at $width dp", tile >= 72)
        }
    }

    @Test
    fun largeTextGridUsesTheFullPanelWidthWithoutPagingAffordance() {
        val width = 320
        val columns = 2
        val tile = BedMenuActionTileWidthPolicy.gridTileWidthDp(
            screenWidthDp = width,
            columns = columns,
            horizontalPaddingDp = 4,
            spacingDp = 2,
            reservedEndDp = 4,
        )
        val used = tile * columns + 8 + 2 * (columns - 1)
        assertTrue("grid should use nearly the full panel width", used <= width && width - used <= columns + 4)
        assertTrue("large text should not shrink targets below 48 dp", tile >= 48)
    }

    @Test
    fun actionMenuUsesSecondRowWhenActionsWouldBeClipped() {
        assertFalse(BedMenuActionTileWidthPolicy.usesTwoRows(3, true, 411, 1f))
        assertTrue(BedMenuActionTileWidthPolicy.usesTwoRows(5, true, 411, 1f))
        assertTrue(BedMenuActionTileWidthPolicy.usesTwoRows(4, true, 320, 1f))
        assertTrue(BedMenuActionTileWidthPolicy.usesTwoRows(4, true, 411, 1.5f))
        assertTrue(BedMenuActionTileWidthPolicy.usesTwoRows(3, true, 320, 1.5f))
        assertFalse(BedMenuActionTileWidthPolicy.usesTwoRows(8, false, 411, 1.5f))
    }
}
