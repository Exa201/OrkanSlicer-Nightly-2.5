package ru.ytkab0bp.slicebeam.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceToolPanelPolicyTest {
    @Test
    fun collapsedPortraitPanelDoesNotReserveViewportHeight() {
        assertEquals(0, WorkspaceToolPanelPolicy.reservedHeightDp(true, false, 124))
    }

    @Test
    fun expandedPortraitPanelReservesItsActualHeight() {
        assertEquals(192, WorkspaceToolPanelPolicy.reservedHeightDp(true, true, 192))
    }

    @Test
    fun twoRowActionsGrowForLargeSystemText() {
        assertEquals(266, WorkspaceToolPanelPolicy.menuHeightDp(true, 1f))
        assertEquals(266, WorkspaceToolPanelPolicy.menuHeightDp(true, 1.15f))
        assertEquals(258, WorkspaceToolPanelPolicy.menuHeightDp(true, 1.3f))
        assertEquals(120, WorkspaceToolPanelPolicy.menuHeightDp(false, 1.5f))
    }

    @Test
    fun menuHeightIncludesExtraChromeBeforeScrollableActions() {
        assertEquals(322, WorkspaceToolPanelPolicy.menuHeightDp(true, 1f, additionalChromeDp = 56))
        assertEquals(314, WorkspaceToolPanelPolicy.menuHeightDp(true, 1.5f, additionalChromeDp = 72))
    }

    @Test
    fun destinationRowRequirementWinsWhilePreviousMenuCountIsStale() {
        // File has only a few actions; switching File -> Model must not use that old count.
        assertEquals(true, WorkspaceToolPanelPolicy.menuNeedsTwoRows(actionCount = 3, destinationNeedsTwoRows = true))
        // A destination without a two-row requirement still grows for a genuinely long menu.
        assertEquals(true, WorkspaceToolPanelPolicy.menuNeedsTwoRows(actionCount = 6, destinationNeedsTwoRows = false))
        assertEquals(false, WorkspaceToolPanelPolicy.menuNeedsTwoRows(actionCount = 3, destinationNeedsTwoRows = false))
    }

    @Test
    fun tileAndHostHeightsStayInSyncForHonorLargeText() {
        assertEquals(120, WorkspaceToolPanelPolicy.actionTileHeightDp(1.5f))
        assertEquals(120, WorkspaceToolPanelPolicy.actionGridHeightDp(4, 1.5f))
        assertEquals(242, WorkspaceToolPanelPolicy.actionGridHeightDp(8, 1.5f))
        assertEquals(1, WorkspaceToolPanelPolicy.visibleActionRows(4))
        assertEquals(2, WorkspaceToolPanelPolicy.visibleActionRows(5))
    }

    @Test
    fun landscapeSidePanelDoesNotReservePortraitBottomSpace() {
        assertEquals(0, WorkspaceToolPanelPolicy.reservedHeightDp(false, false, 124))
    }
}
