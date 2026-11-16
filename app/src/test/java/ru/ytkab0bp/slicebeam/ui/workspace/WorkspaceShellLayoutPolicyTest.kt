package ru.ytkab0bp.slicebeam.ui.workspace

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.ytkab0bp.slicebeam.ui.settings.TemporaryProjectSettingsBarLayoutPolicy

class WorkspaceShellLayoutPolicyTest {
    @Test
    fun workspaceHeaderGeometryDoesNotChangeWithTextZoom() {
        assertEquals(80, WorkspaceShellLayoutPolicy.workspaceHeaderHeightDp())
    }

    @Test
    fun temporaryProjectBannerReservesTwoExplanationLinesAtTheMaximumAppZoom() {
        assertEquals(96, TemporaryProjectSettingsBarLayoutPolicy.heightDp(1f))
        assertEquals(116, TemporaryProjectSettingsBarLayoutPolicy.heightDp(1.3f))
        assertEquals(96, TemporaryProjectSettingsBarLayoutPolicy.heightDp(Float.NaN))
    }

    @Test
    fun landscapeMenuWidthPreservesReadableActionsAndRendererSpace() {
        assertEquals(256, WorkspaceShellLayoutPolicy.menuPanelWidthDp(800, 1f))
        assertEquals(200, WorkspaceShellLayoutPolicy.menuPanelWidthDp(600, 1f))
        assertEquals(256, WorkspaceShellLayoutPolicy.menuPanelWidthDp(800, 1.5f))
        assertEquals(320, WorkspaceShellLayoutPolicy.menuPanelWidthDp(1400, 1.5f))
    }

    @Test
    fun landscapeActionMenuReservesTwoReadableRowsBelowTheRenderer() {
        assertEquals(152, WorkspaceShellLayoutPolicy.landscapeMenuPanelMaxHeightDp(1f))
        assertEquals(168, WorkspaceShellLayoutPolicy.landscapeMenuPanelMaxHeightDp(1.5f))
    }

    @Test
    fun collapsedPanelKeepsViewportAndFixedControlsVisible() {
        assertEquals(
            640,
            WorkspaceShellLayoutPolicy.plateViewportHeightDp(
                availableHeightDp = 800,
                projectBarHeightDp = 40,
                workspaceHeaderHeightDp = 64,
                navigationHeightDp = 56,
                toolPanelHeightDp = 140,
                toolPanelExpanded = false,
            ),
        )
    }

    @Test
    fun expandedPanelConsumesOnlyItsReservedSpace() {
        assertEquals(
            500,
            WorkspaceShellLayoutPolicy.plateViewportHeightDp(
                availableHeightDp = 800,
                projectBarHeightDp = 40,
                workspaceHeaderHeightDp = 64,
                navigationHeightDp = 56,
                toolPanelHeightDp = 140,
                toolPanelExpanded = true,
            ),
        )
    }

    @Test
    fun shortAndInvalidMeasurementsNeverProduceNegativeViewport() {
        assertEquals(
            0,
            WorkspaceShellLayoutPolicy.plateViewportHeightDp(
                availableHeightDp = 100,
                projectBarHeightDp = 40,
                workspaceHeaderHeightDp = 64,
                navigationHeightDp = 56,
                toolPanelHeightDp = 140,
                toolPanelExpanded = true,
            ),
        )
        assertEquals(
            0,
            WorkspaceShellLayoutPolicy.plateViewportHeightDp(-1, -1, -1, -1, -1, true),
        )
    }

    @Test
    fun portraitMenuUsesTwoThreeColumnRowsWhenTheViewportCanFitThem() {
        assertEquals(112, WorkspaceShellLayoutPolicy.portraitMenuOverlayHeightDp(200, 236, 1.5f))
        assertEquals(1, WorkspaceShellLayoutPolicy.portraitMenuRows(160, 1.5f))
        assertEquals(80, WorkspaceShellLayoutPolicy.portraitMenuOverlayHeightDp(100, 236, 1.5f))
        assertEquals(1, WorkspaceShellLayoutPolicy.portraitMenuRows(80, 1.5f))
        assertEquals(2, WorkspaceShellLayoutPolicy.portraitMenuRows(224, 1.15f))
        assertEquals(1, WorkspaceShellLayoutPolicy.portraitMenuRows(200, 1.15f))
        assertEquals(112, WorkspaceShellLayoutPolicy.portraitMenuOverlayHeightDp(200, 236, 1.15f))
        assertEquals(192, WorkspaceShellLayoutPolicy.portraitMenuOverlayHeightDp(320, 192, 1f))
        assertEquals(2, WorkspaceShellLayoutPolicy.portraitMenuRows(192, 1f))
    }

    @Test
    fun portraitMenuNeverConsumesMoreThanItsRendererViewport() {
        assertEquals(0, WorkspaceShellLayoutPolicy.portraitMenuOverlayHeightDp(0, 176, 1.5f))
        assertEquals(40, WorkspaceShellLayoutPolicy.portraitMenuOverlayHeightDp(50, 176, 1.5f))
    }

    @Test
    fun dockedLayerControlsReserveSpaceOutsideThePlateRenderer() {
        assertEquals(200, WorkspaceShellLayoutPolicy.dockedPortraitPanelHeightDp(
            500, WorkspacePanelDimension.Fraction(0.4f), 1f,
        ))
        assertEquals(100, WorkspaceShellLayoutPolicy.dockedPortraitPanelHeightDp(
            250, WorkspacePanelDimension.Fraction(0.8f), 1f,
        ))
        assertEquals("a short paint dock must keep at least 60% of its workspace for the plate", 200,
            WorkspaceShellLayoutPolicy.dockedPortraitPanelHeightDp(
                500, WorkspacePanelDimension.Fraction(0.9f), 1f,
            ))
        assertEquals(0, WorkspaceShellLayoutPolicy.dockedPortraitPanelHeightDp(
            120, WorkspacePanelDimension.Fixed(240f), 1.5f,
        ))
        assertEquals(184, WorkspaceShellLayoutPolicy.dockedLandscapePanelWidthDp(
            464, WorkspacePanelDimension.Fixed(380f),
        ))
        assertEquals(0, WorkspaceShellLayoutPolicy.dockedLandscapePanelWidthDp(
            240, WorkspacePanelDimension.Fixed(180f),
        ))
        assertEquals(360, WorkspaceShellLayoutPolicy.MIN_LANDSCAPE_SIDE_PANEL_WIDTH_DP)
        assertEquals(200, WorkspaceShellLayoutPolicy.dockedLandscapeBottomPanelHeightDp(300))
        assertEquals(0, WorkspaceShellLayoutPolicy.dockedLandscapeBottomPanelHeightDp(100))
    }

    @Test
    fun compactPaintDockKeepsPaletteVisibleAndReservesMoreRoomForPlate() {
        val paintDock = WorkspacePanelDimension.ClampedFraction(
            fraction = 0.24f,
            minDp = 72f,
            maxDp = 136f,
        )
        assertEquals(72, WorkspaceShellLayoutPolicy.dockedPortraitPanelHeightDp(300, paintDock, 1f))
        assertEquals(120, WorkspaceShellLayoutPolicy.dockedPortraitPanelHeightDp(500, paintDock, 1f))
        assertEquals(136, WorkspaceShellLayoutPolicy.dockedPortraitPanelHeightDp(800, paintDock, 1f))
        // Below this workspace height the panel is clamped away so it cannot consume the
        // renderer's absolute 120 dp minimum; the activity still has no usable split space.
        assertEquals(0, WorkspaceShellLayoutPolicy.dockedPortraitPanelHeightDp(120, paintDock, 1.5f))
    }
}
