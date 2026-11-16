package ru.ytkab0bp.slicebeam.ui.workspace

import android.content.res.Configuration
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.ui.BeamComposeTheme
import ru.ytkab0bp.slicebeam.ui.navigation.WorkspaceDestination
import ru.ytkab0bp.slicebeam.view.SnackbarsLayout

@RunWith(AndroidJUnit4::class)
class WorkspaceModePanelHostDeviceTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun dashboardHeaderKeepsFilamentAssignmentVisibleWhileToolPanelIsOpen() {
        compose.setContent {
            CompositionLocalProvider(
                LocalConfiguration provides Configuration().apply {
                    screenWidthDp = 480
                    screenHeightDp = 800
                },
            ) {
                BeamComposeTheme {
                    Box(Modifier.requiredSize(480.dp, 800.dp)) {
                        WorkspaceShell(
                            portrait = true,
                            toolPanelExpanded = false,
                            toolPanelNeedsTwoRows = false,
                            navigationItemCount = WorkspaceDestination.visibleNavigationDestinations.size,
                            menuWidthDp = 220,
                            rendererView = android.view.View(LocalContext.current),
                            selectedNavigationSlot = WorkspaceDestination.PROJECT.ordinal,
                            toolsExpanded = false,
                            onNavigationSelected = {},
                            activeMenuSlot = WorkspaceDestination.PROJECT.ordinal,
                            menuComposeContentForSlot = {},
                            onMenuTransitionSettled = {},
                            projectSettingsContent = {},
                            profileStatusContent = { Text("Printer profile") },
                            filamentButtonContent = { Text("Filament") },
                            platePickerContent = { Text("Plate") },
                            printerWebButtonContent = { Text("Connection") },
                            modePanelContent = { Text("Other tool panel") },
                            modePanelSize = null,
                            modePanelDocked = true,
                            modePanelAllowsBackgroundInteraction = false,
                            onModePanelDismiss = {},
                            transientPanelContent = null,
                            snackbarController = WorkspaceSnackbarController(),
                        )
                    }
                }
            }
        }

        val actionArea = compose.onNodeWithTag("workspace-header-actions")
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("workspace-header-filament-action").assertIsDisplayed()
        val plate = compose.onNodeWithTag("workspace-header-plate-action")
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val connection = compose.onNodeWithTag("workspace-header-printer-action")
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("connection control should reach the right side of the dashboard header", connection.right >= actionArea.right - 1f)
        assertTrue("the space between plate and connection controls should be visible", connection.left > plate.right)
    }

    @Test
    fun landscapeMenuUsesFullWidthStripBelowTheRenderer() {
        compose.setContent {
            CompositionLocalProvider(
                LocalConfiguration provides Configuration().apply {
                    screenWidthDp = 900
                    screenHeightDp = 600
                },
            ) {
                BeamComposeTheme {
                    Box(Modifier.requiredSize(900.dp, 600.dp)) {
                        WorkspaceShell(
                            portrait = false,
                            toolPanelExpanded = true,
                            toolPanelNeedsTwoRows = false,
                            navigationItemCount = WorkspaceDestination.visibleNavigationDestinations.size,
                            menuWidthDp = 240,
                            rendererView = android.view.View(LocalContext.current),
                            selectedNavigationSlot = WorkspaceDestination.PROJECT.ordinal,
                            toolsExpanded = true,
                            onNavigationSelected = {},
                            activeMenuSlot = WorkspaceDestination.PROJECT.ordinal,
                            menuComposeContentForSlot = {
                                Box(Modifier.fillMaxWidth().heightIn(min = 96.dp)
                                    .testTag("landscape-menu-test-content"))
                            },
                            onMenuTransitionSettled = {},
                            projectSettingsContent = {},
                            profileStatusContent = { Text("Printer profile") },
                            filamentButtonContent = { Text("Filament") },
                            platePickerContent = { Text("Plate") },
                            printerWebButtonContent = { Text("Connection") },
                            modePanelContent = null,
                            modePanelSize = null,
                            modePanelDocked = false,
                            modePanelAllowsBackgroundInteraction = false,
                            onModePanelDismiss = {},
                            transientPanelContent = null,
                            snackbarController = WorkspaceSnackbarController(),
                        )
                    }
                }
            }
        }

        val renderer = compose.onNodeWithTag("workspace-renderer-area")
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val panel = compose.onNodeWithTag("workspace-menu-panel")
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("the landscape action panel should be below the renderer", panel.top >= renderer.bottom - 1f)
        assertTrue("the action panel should use the workspace width", panel.width >= renderer.width - 1f)
    }

    @Test
    fun importProgressUsesVisibleMenuHeightInsteadOfTwoRowReservation() {
        val snackbars = WorkspaceSnackbarController().apply {
            show(SnackbarsLayout.Snackbar(SnackbarsLayout.Type.LOADING, "Import in progress").tag("import"))
        }
        compose.setContent {
            CompositionLocalProvider(
                LocalConfiguration provides Configuration().apply {
                    screenWidthDp = 480
                    screenHeightDp = 800
                },
            ) {
                BeamComposeTheme {
                    Box(Modifier.requiredSize(480.dp, 800.dp)) {
                        WorkspaceShell(
                            portrait = true,
                            toolPanelExpanded = true,
                            toolPanelNeedsTwoRows = true,
                            navigationItemCount = WorkspaceDestination.visibleNavigationDestinations.size,
                            menuWidthDp = 220,
                            rendererView = android.view.View(LocalContext.current),
                            selectedNavigationSlot = WorkspaceDestination.PROJECT.ordinal,
                            toolsExpanded = true,
                            onNavigationSelected = {},
                            activeMenuSlot = WorkspaceDestination.PROJECT.ordinal,
                            menuComposeContentForSlot = {
                                Box(Modifier.requiredSize(480.dp, 80.dp).testTag("visible-menu-actions"))
                            },
                            onMenuTransitionSettled = {},
                            projectSettingsContent = {},
                            profileStatusContent = { Text("Printer profile") },
                            filamentButtonContent = {},
                            platePickerContent = {},
                            printerWebButtonContent = {},
                            modePanelContent = null,
                            modePanelSize = null,
                            modePanelDocked = false,
                            modePanelAllowsBackgroundInteraction = false,
                            onModePanelDismiss = {},
                            transientPanelContent = null,
                            snackbarController = snackbars,
                        )
                    }
                }
            }
        }

        val menu = compose.onNodeWithTag("workspace-menu-panel")
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val progress = compose.onNodeWithText("Import in progress")
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val gap = menu.top - progress.bottom
        assertTrue("progress should stay above menu actions", gap >= 0f)
        assertTrue("progress should sit close to the visible actions, not far above the reserved panel", gap < 100f)
    }

    @Test
    fun dockedPaintPanelKeepsGlobalFilamentShortcutAvailable() {
        val controller = WorkspaceController()
        controller.selectToolWithPanel(
            toolId = "paint-mode",
            dismiss = {},
            panelContent = { Text("Paint filament row", Modifier.testTag("paint-filament-row")) },
            panelSize = WorkspacePanelSize(
                portrait = WorkspacePanelDimension.Fixed(120f),
                landscape = WorkspacePanelDimension.Fixed(280f),
            ),
            dockPanel = true,
        )
        compose.setContent {
            BeamComposeTheme {
                Box(Modifier.requiredSize(360.dp, 720.dp)) {
                    WorkspaceShell(
                        portrait = true,
                        toolPanelExpanded = false,
                        toolPanelNeedsTwoRows = false,
                        navigationItemCount = WorkspaceDestination.visibleNavigationDestinations.size,
                        menuWidthDp = 220,
                        rendererView = android.view.View(LocalContext.current),
                        selectedNavigationSlot = WorkspaceDestination.PROJECT.ordinal,
                        toolsExpanded = false,
                        onNavigationSelected = {},
                        activeMenuSlot = WorkspaceDestination.PROJECT.ordinal,
                        menuComposeContentForSlot = {},
                        onMenuTransitionSettled = {},
                        projectSettingsContent = {},
                        profileStatusContent = { Text("Profiles") },
                        filamentButtonContent = {
                            Text("Global filament shortcut", Modifier.testTag("global-filament-shortcut"))
                        },
                        platePickerContent = {},
                        printerWebButtonContent = {},
                        modePanelContent = controller.activeToolPanelContent,
                        modePanelSize = controller.activeToolPanelSize,
                        modePanelDocked = controller.activeToolPanelDocked,
                        modePanelAllowsBackgroundInteraction = controller.activeToolAllowsBackgroundInteraction,
                        onModePanelDismiss = { controller.dismissActiveTool() },
                        transientPanelContent = controller.activePanelContent,
                        snackbarController = WorkspaceSnackbarController(),
                    )
                }
            }
        }

        compose.onNodeWithTag("paint-filament-row").assertIsDisplayed()
        compose.onAllNodesWithTag("global-filament-shortcut").assertCountEquals(1)
        compose.onNodeWithTag("workspace-renderer-area").assertIsDisplayed()

        // Color paint can also be hosted modally on compact layouts. Its palette must
        // still be the only filament control while the global workspace remains visible.
        controller.clearTool("paint-mode")
        controller.selectToolWithPanel(
            toolId = "paint-mode",
            dismiss = {},
            panelContent = { Text("Modal paint filament row", Modifier.testTag("modal-paint-filament-row")) },
        )
        compose.onNodeWithTag("modal-paint-filament-row").assertIsDisplayed()
        compose.onAllNodesWithTag("global-filament-shortcut").assertCountEquals(1)
    }

    @Test
    fun controllerToolActivationRecomposesTheWorkspacePanel() {
        val controller = WorkspaceController()
        compose.setContent {
            MaterialTheme {
                Box(Modifier.requiredSize(360.dp, 720.dp)) {
                    Button(
                        modifier = Modifier.testTag("activate-test-tool"),
                        onClick = {
                            controller.selectToolWithPanel(
                                toolId = "test-tool",
                                dismiss = {},
                                panelContent = { Text("Controller panel", Modifier.testTag("controller-panel-content")) },
                            )
                        },
                    ) { Text("Open tool") }
                    controller.activeToolPanelContent?.let { panel ->
                        WorkspaceModePanelHost(onDismiss = {}) { panel() }
                    }
                }
            }
        }

        compose.onNodeWithTag("activate-test-tool").performClick()
        compose.onNodeWithTag("workspace-mode-panel").assertIsDisplayed()
        compose.onNodeWithTag("controller-panel-content").assertIsDisplayed()
    }

    @Test
    fun controllerToolActivationRendersPanelThroughWorkspaceShell() {
        val controller = WorkspaceController()
        compose.setContent {
            BeamComposeTheme {
                WorkspaceShell(
                    portrait = false,
                    toolPanelExpanded = true,
                    toolPanelNeedsTwoRows = false,
                    navigationItemCount = WorkspaceDestination.visibleNavigationDestinations.size,
                    menuWidthDp = 220,
                    rendererView = android.view.View(LocalContext.current),
                    selectedNavigationSlot = WorkspaceDestination.PROJECT.ordinal,
                    toolsExpanded = true,
                    onNavigationSelected = {},
                    activeMenuSlot = WorkspaceDestination.PROJECT.ordinal,
                    menuComposeContentForSlot = {},
                    onMenuTransitionSettled = {},
                    projectSettingsContent = {},
                    profileStatusContent = {
                        Button(
                            modifier = Modifier.testTag("shell-activate-test-tool"),
                            onClick = {
                                controller.selectToolWithPanel(
                                    toolId = "test-tool",
                                    dismiss = {},
                                    panelContent = {
                                        Text("Shell panel", Modifier.testTag("shell-panel-content"))
                                    },
                                    panelSize = WorkspacePanelSize(
                                        portrait = WorkspacePanelDimension.Fixed(300f),
                                        landscape = WorkspacePanelDimension.Fixed(300f),
                                    ),
                                )
                            },
                        ) { Text("Open tool") }
                    },
                    filamentButtonContent = {},
                    platePickerContent = {},
                    printerWebButtonContent = {},
                    modePanelContent = controller.activeToolPanelContent,
                    modePanelSize = controller.activeToolPanelSize,
                    modePanelDocked = controller.activeToolPanelDocked,
                    modePanelAllowsBackgroundInteraction = controller.activeToolAllowsBackgroundInteraction,
                    onModePanelDismiss = { controller.dismissActivePanel() },
                    transientPanelContent = controller.activePanelContent,
                    snackbarController = WorkspaceSnackbarController(),
                )
            }
        }

        compose.onNodeWithTag("shell-activate-test-tool").performClick()
        compose.onNodeWithTag("workspace-mode-panel").assertIsDisplayed()
        compose.onNodeWithTag("shell-panel-content").assertIsDisplayed()
    }

    @Test
    fun portraitToolPanelUsesComposeDpConstraints() {
        compose.setContent {
            MaterialTheme {
                Box(Modifier.requiredSize(360.dp, 720.dp)) {
                    WorkspaceModePanelHost(
                        onDismiss = {},
                        panelSize = WorkspacePanelSize(
                            portrait = WorkspacePanelDimension.Fixed(316f),
                            landscape = WorkspacePanelDimension.Fraction(0.5f),
                        ),
                    ) { Box(Modifier.fillMaxSize()) }
                }
            }
        }

        compose.onNodeWithTag("workspace-mode-panel").assertHeightIsEqualTo(316.dp)
    }

    @Test
    fun landscapeToolPanelUsesComposeAvailableWidth() {
        compose.setContent {
            MaterialTheme {
                Box(Modifier.requiredSize(720.dp, 360.dp)) {
                    WorkspaceModePanelHost(
                        onDismiss = {},
                        panelSize = WorkspacePanelSize(
                            portrait = WorkspacePanelDimension.Fixed(316f),
                            landscape = WorkspacePanelDimension.Fraction(0.5f),
                        ),
                    ) { Box(Modifier.fillMaxSize()) }
                }
            }
        }

        compose.onNodeWithTag("workspace-mode-panel").assertWidthIsEqualTo(360.dp)
    }

    @Test
    fun landscapeToolPanelCapsHeightWhenAndroidParentMeasuresUnbounded() {
        compose.setContent {
            CompositionLocalProvider(
                LocalConfiguration provides Configuration().apply {
                    screenWidthDp = 720
                    screenHeightDp = 360
                },
            ) {
                MaterialTheme {
                    Column(
                        Modifier.requiredSize(720.dp, 360.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        WorkspaceModePanelHost(
                            onDismiss = {},
                            panelSize = WorkspacePanelSize(
                                portrait = WorkspacePanelDimension.Fixed(300f),
                                landscape = WorkspacePanelDimension.Fixed(300f),
                            ),
                        ) {
                            Column(
                                Modifier.fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                repeat(12) { Text("Cut setting $it") }
                                Text("Cut apply action", Modifier.testTag("cut-apply-action"))
                            }
                        }
                    }
                }
            }
        }

        compose.onNodeWithTag("workspace-mode-panel").assertIsDisplayed()
        compose.onNodeWithTag("cut-apply-action").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun portraitToolPanelKeepsItsLastActionScrollableAtSmallWidthsAndLargeText() {
        var widthDp by mutableIntStateOf(320)
        var heightDp by mutableIntStateOf(640)
        var fontScale by mutableFloatStateOf(1f)

        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f, fontScale),
                LocalConfiguration provides Configuration().apply {
                    screenWidthDp = widthDp
                    screenHeightDp = heightDp
                },
            ) {
                MaterialTheme {
                    Box(Modifier.requiredSize(widthDp.dp, heightDp.dp)) {
                        WorkspaceModePanelHost(
                            onDismiss = {},
                            panelSize = WorkspacePanelSize(
                                portrait = WorkspacePanelDimension.Fixed(heightDp * 0.6f),
                                landscape = WorkspacePanelDimension.Fixed(widthDp * 0.6f),
                            ),
                        ) {
                            Column(
                                Modifier.fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .testTag("tool-panel-scroll"),
                            ) {
                                Text("Tool panel heading")
                                repeat(18) { Text("Tool setting $it") }
                                Text("Last tool action")
                            }
                        }
                    }
                }
            }
        }

        val configurations = listOf(
            Triple(320, 640, 1f), Triple(320, 640, 1.3f), Triple(320, 640, 1.5f),
            Triple(360, 720, 1f), Triple(360, 720, 1.3f), Triple(360, 720, 1.5f),
            Triple(400, 800, 1f), Triple(400, 800, 1.3f), Triple(400, 800, 1.5f),
        )
        configurations.forEach { (width, height, scale) ->
            compose.runOnUiThread {
                widthDp = width
                heightDp = height
                fontScale = scale
            }
            compose.waitForIdle()
            compose.onNodeWithTag("workspace-mode-panel")
                .assertIsDisplayed()
                .assertHeightIsAtLeast(48.dp)
            compose.onNodeWithText("Last tool action")
                .performScrollTo()
                .assertIsDisplayed()
        }
    }

    @Test
    fun largeFontPlateCounterGrowsWithoutStretchingAdjacentHeaderControls() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f, 1.5f),
                LocalConfiguration provides Configuration().apply {
                    screenWidthDp = 360
                    screenHeightDp = 800
                },
            ) {
                BeamComposeTheme {
                    Row {
                        WorkspaceActionControl(content = {
                            PlateTabs(
                                openState = androidx.compose.runtime.mutableStateOf(false),
                                plateCount = 1,
                                selectedIndex = 0,
                                objectCounts = listOf(0),
                                thumbnails = listOf(PlateThumbnail(emptyList())),
                                onOpen = {}, onDismiss = {}, onSelect = {}, onClose = {}, onAdd = {},
                            )
                        }, allowVerticalGrowth = true)
                        WorkspaceActionControl(content = {
                            Box(Modifier.requiredSize(48.dp).testTag("fixed-header-action"))
                        })
                    }
                }
            }
        }

        compose.onNodeWithContentDescription(context.getString(R.string.PlatePickerCounterDescription, 1, 1))
            .assertHeightIsAtLeast(52.dp)
        compose.onNodeWithTag("fixed-header-action").assertHeightIsEqualTo(48.dp)
    }
}
