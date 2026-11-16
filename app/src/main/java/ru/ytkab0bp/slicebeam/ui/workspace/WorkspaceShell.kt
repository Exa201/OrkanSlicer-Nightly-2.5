package ru.ytkab0bp.slicebeam.ui.workspace

import android.view.View
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.ceil
import androidx.compose.ui.platform.testTag
import ru.ytkab0bp.slicebeam.ui.OrkanUiTokens
import ru.ytkab0bp.slicebeam.ui.LocalBeamLayoutFontScale
import androidx.compose.material3.MaterialTheme
import ru.ytkab0bp.slicebeam.ui.navigation.AdaptiveNavigationPolicy
import ru.ytkab0bp.slicebeam.ui.navigation.AdaptiveWorkspaceNavigation
import ru.ytkab0bp.slicebeam.ui.navigation.WorkspaceDestination
import ru.ytkab0bp.slicebeam.ui.navigation.WorkspaceNavigationItem
import ru.ytkab0bp.slicebeam.ui.navigation.WorkspaceToolPanelPolicy
import kotlinx.coroutines.delay
import androidx.compose.animation.core.tween

/** Compose owns the workspace geometry; legacy views are mounted only at explicit boundaries. */
@Composable
internal fun WorkspaceShell(
    portrait: Boolean,
    toolPanelExpanded: Boolean,
    toolPanelNeedsTwoRows: Boolean,
    toolPanelAdditionalChromeDp: Int = 0,
    navigationItemCount: Int,
    menuWidthDp: Int,
    rendererView: View,
    selectedNavigationSlot: Int,
    toolsExpanded: Boolean,
    onNavigationSelected: (Int) -> Unit,
    activeMenuSlot: Int,
    menuComposeContentForSlot: @Composable (Int) -> Unit,
    onMenuTransitionSettled: () -> Unit,
    projectSettingsContent: @Composable () -> Unit,
    profileStatusContent: @Composable () -> Unit,
    filamentButtonContent: @Composable () -> Unit,
    dashboardPrintToolsContent: @Composable () -> Unit = {},
    platePickerContent: @Composable () -> Unit,
    printerWebButtonContent: @Composable () -> Unit,
    modePanelContent: (@Composable () -> Unit)?,
    modePanelSize: WorkspacePanelSize?,
    modePanelDocked: Boolean,
    modePanelAllowsBackgroundInteraction: Boolean,
    onModePanelDismiss: () -> Unit,
    transientPanelContent: (@Composable () -> Unit)?,
    snackbarController: WorkspaceSnackbarController,
) {
    var visibleMenuHeightDp by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val toolPanelHeightDp = WorkspaceToolPanelPolicy.menuHeightDp(
        toolPanelNeedsTwoRows,
        LocalBeamLayoutFontScale.current,
        toolPanelAdditionalChromeDp,
    )
    val navigationHeightDp = AdaptiveNavigationPolicy.navigationBarHeightDp(
        LocalBeamLayoutFontScale.current,
        navigationItemCount,
    )
    val background = MaterialTheme.colorScheme.background
    val compactLabels = AdaptiveNavigationPolicy.usesCompactWorkspaceLabels(
        LocalConfiguration.current.screenWidthDp,
        LocalBeamLayoutFontScale.current,
        portrait,
    )
    val headerWidthDp = LocalConfiguration.current.screenWidthDp
    // Keep the important assignment/sync entry visible at every width. Duplicated
    // dashboard print shortcuts give way on compact screens; they remain in Print.
    val showHeaderFilamentShortcut = true
    val showHeaderPrintTools = headerWidthDp >= 560
    val headerActionWeight = when {
        headerWidthDp < 360 -> 1.85f
        headerWidthDp < 480 -> 1.4f
        else -> 1.15f
    }
    val navigationItems = WorkspaceDestination.visibleNavigationDestinations.map { destination ->
        val compactTitle = when (destination) {
            WorkspaceDestination.PROJECT -> ru.ytkab0bp.slicebeam.R.string.MenuFileCompact
            WorkspaceDestination.MODEL -> ru.ytkab0bp.slicebeam.R.string.MenuModelCompact
            WorkspaceDestination.EDIT -> ru.ytkab0bp.slicebeam.R.string.MenuToolbarNavigationCompact
            WorkspaceDestination.TOOLS -> ru.ytkab0bp.slicebeam.R.string.MenuOptionsCompact
            else -> destination.navigationTitleRes
        }
        WorkspaceNavigationItem(
            slot = destination.ordinal,
            label = stringResource(if (compactLabels) compactTitle else destination.navigationTitleRes),
            icon = destination.iconRes,
            accessibilityLabel = stringResource(destination.navigationTitleRes),
            compactLabel = if (compactLabels) stringResource(compactTitle) else null,
        )
    }
    LaunchedEffect(activeMenuSlot, portrait, toolPanelExpanded) {
        // A programmatic route change can happen while the compact portrait menu is
        // collapsed (for example after loading G-code). In that layout the animated
        // menu host is absent, so transition cleanup must be owned by the shell itself.
        if (portrait && !toolPanelExpanded) {
            onMenuTransitionSettled()
        } else {
            delay(260)
            onMenuTransitionSettled()
        }
    }
    val header: @Composable (Modifier) -> Unit = { modifier ->
        Row(
            modifier = modifier
            .fillMaxWidth()
                .testTag("workspace-header")
                // Keep the renderer viewport stationary while app/system text zoom
                // changes. The concise model label wraps within a fixed-height row;
                // TalkBack receives the complete selected profile description.
                .height(WorkspaceShellLayoutPolicy.workspaceHeaderHeightDp().dp)
                .padding(horizontal = 6.dp, vertical = 4.dp),
        ) {
            Box(
                modifier = Modifier.weight(1f).widthIn(min = 0.dp)
                    .heightIn(min = OrkanUiTokens.touchTarget)
                    .testTag("workspace-header-profile-status"),
            ) { profileStatusContent() }
            Row(
                // Spread the compact controls through the available dashboard header
                // instead of leaving a large unused strip after the last icon.
                // Their fixed touch targets still fit the smallest supported width.
                modifier = Modifier.weight(headerActionWeight).fillMaxWidth()
                    .testTag("workspace-header-actions"),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Keep filament assignment/sync available from the workspace header
                // while other tool panels are open. Color paint owns its palette and
                // its content lambda intentionally renders no duplicate shortcut.
                if (showHeaderFilamentShortcut) {
                    WorkspaceActionControl(filamentButtonContent, testTag = "workspace-header-filament-action")
                }
                if (showHeaderPrintTools && modePanelContent == null && transientPanelContent == null) {
                    dashboardPrintToolsContent()
                }
                WorkspaceActionControl(
                    platePickerContent,
                    targetWidth = OrkanUiTokens.plateCounterWidth,
                    testTag = "workspace-header-plate-action",
                )
                WorkspaceActionControl(printerWebButtonContent, testTag = "workspace-header-printer-action")
            }
        }
    }

    Box(Modifier.fillMaxSize().background(background)) {
      if (portrait) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().heightIn(min = 0.dp).testTag("workspace-project-settings-area")) { projectSettingsContent() }
            header(Modifier)
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val availableHeightDp = maxHeight.value.toInt()
                val dockHeight = if (modePanelDocked && modePanelContent != null) {
                    WorkspaceShellLayoutPolicy.dockedPortraitPanelHeightDp(
                        availableHeightDp, modePanelSize?.portrait, LocalBeamLayoutFontScale.current,
                    )
                } else 0
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().weight(1f).testTag("workspace-renderer-area")) {
                        AndroidView(factory = { rendererView }, modifier = Modifier.fillMaxSize())
                        // Once a GL-bound mode owns the interaction, keep the workspace
                        // action grid out of the canvas hit area. It returns with the mode
                        // panel when the tool closes.
                        if (toolPanelExpanded && modePanelContent == null) {
                            val overlayHeightDp = WorkspaceShellLayoutPolicy.portraitMenuOverlayHeightDp(
                                viewportHeightDp = availableHeightDp,
                                preferredHeightDp = toolPanelHeightDp,
                                fontScale = LocalBeamLayoutFontScale.current,
                            )
                            WorkspaceMenuHost(
                                slot = activeMenuSlot,
                                composeContentForSlot = menuComposeContentForSlot,
                                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                                    .heightIn(max = overlayHeightDp.dp)
                                    .wrapContentHeight(align = Alignment.Bottom)
                                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.97f))
                                    .onGloballyPositioned { coordinates ->
                                        val measuredHeight = with(density) {
                                            coordinates.size.height.toDp().value.toInt()
                                        }
                                        if (measuredHeight != visibleMenuHeightDp) {
                                            visibleMenuHeightDp = measuredHeight
                                        }
                                    }
                                    .testTag("workspace-menu-panel"),
                            )
                        }
                    }
                    if (dockHeight > 0) {
                        Box(Modifier.fillMaxWidth().height(dockHeight.dp).testTag("workspace-mode-panel")) {
                            androidx.compose.material3.Surface(
                                modifier = Modifier.fillMaxSize().testTag("workspace-docked-tool-panel"),
                                color = MaterialTheme.colorScheme.surface,
                                shadowElevation = 4.dp,
                            ) { modePanelContent?.invoke() }
                        }
                    }
                }
            }
            AdaptiveWorkspaceNavigation(
                modifier = Modifier.fillMaxWidth().height(navigationHeightDp.dp).testTag("workspace-navigation"),
                items = navigationItems,
                selectedSlot = selectedNavigationSlot,
                isBottomNavigation = true,
                toolsExpanded = toolsExpanded,
                panelOpenDescription = stringResource(ru.ytkab0bp.slicebeam.R.string.WorkspaceToolsOpen),
                panelClosedDescription = stringResource(ru.ytkab0bp.slicebeam.R.string.WorkspaceToolsClosed),
                announcePanelState = true,
                showPanelToggleIndicator = true,
                onSelected = onNavigationSelected,
            )
        }
      } else {
        Row(Modifier.fillMaxSize()) {
            AdaptiveWorkspaceNavigation(
                modifier = Modifier.width(AdaptiveNavigationPolicy.navigationRailWidthDp(LocalBeamLayoutFontScale.current).dp).fillMaxHeight(),
                items = navigationItems,
                selectedSlot = selectedNavigationSlot,
                isBottomNavigation = false,
                toolsExpanded = true,
                panelOpenDescription = stringResource(ru.ytkab0bp.slicebeam.R.string.WorkspaceToolsOpen),
                panelClosedDescription = stringResource(ru.ytkab0bp.slicebeam.R.string.WorkspaceToolsClosed),
                announcePanelState = true,
                showPanelToggleIndicator = false,
                railTestTag = "workspace-navigation-rail",
                onSelected = onNavigationSelected,
            )
            Spacer(Modifier.width(1.dp).fillMaxHeight().background(background.copy(alpha = 0.35f)))
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Box(Modifier.fillMaxWidth().heightIn(min = 0.dp)) { projectSettingsContent() }
                header(Modifier)
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                    val dockWidth = if (modePanelDocked && modePanelContent != null) {
                        WorkspaceShellLayoutPolicy.dockedLandscapePanelWidthDp(maxWidth.value.toInt(), modePanelSize?.landscape)
                    } else 0
                    val dockAtBottom = modePanelDocked && modePanelContent != null &&
                        dockWidth < WorkspaceShellLayoutPolicy.MIN_LANDSCAPE_SIDE_PANEL_WIDTH_DP
                    if (dockAtBottom) {
                        val dockHeight = WorkspaceShellLayoutPolicy.dockedLandscapeBottomPanelHeightDp(
                            maxHeight.value.toInt(),
                        )
                        Column(Modifier.fillMaxSize()) {
                            AndroidView(
                                factory = { rendererView },
                                modifier = Modifier.fillMaxWidth().weight(1f).testTag("workspace-renderer-area"),
                            )
                            if (dockHeight > 0) {
                                Box(Modifier.fillMaxWidth().height(dockHeight.dp).testTag("workspace-mode-panel")) {
                                    androidx.compose.material3.Surface(
                                        modifier = Modifier.fillMaxSize().testTag("workspace-docked-tool-panel"),
                                        color = MaterialTheme.colorScheme.surface,
                                        shadowElevation = 4.dp,
                                    ) { modePanelContent?.invoke() }
                                }
                            }
                        }
                    } else {
                        Row(Modifier.fillMaxSize()) {
                            AndroidView(
                                factory = { rendererView },
                                modifier = Modifier.weight(1f).fillMaxHeight().testTag("workspace-renderer-area"),
                            )
                            if (dockWidth > 0) {
                                Box(Modifier.width(dockWidth.dp).fillMaxHeight().testTag("workspace-mode-panel")) {
                                    androidx.compose.material3.Surface(
                                        modifier = Modifier.fillMaxSize().testTag("workspace-docked-tool-panel"),
                                        color = MaterialTheme.colorScheme.surface,
                                        shadowElevation = 4.dp,
                                    ) { modePanelContent?.invoke() }
                                }
                            }
                        }
                    }
                }
                // In landscape the action menu belongs below the build plate. Keeping
                // it in a full-width bottom strip leaves the renderer centered instead
                // of squeezing it beside a tall, narrow action column.
                WorkspaceMenuHost(
                    slot = activeMenuSlot,
                    composeContentForSlot = menuComposeContentForSlot,
                    modifier = Modifier.fillMaxWidth()
                        .heightIn(max = WorkspaceShellLayoutPolicy.landscapeMenuPanelMaxHeightDp(
                            LocalBeamLayoutFontScale.current,
                        ).dp)
                        .wrapContentHeight(align = Alignment.Bottom)
                        .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.97f))
                        .testTag("workspace-menu-panel"),
                )
            }
        }
      }
      if (modePanelContent != null && !modePanelDocked) {
          WorkspaceModePanelHost(
              onDismiss = onModePanelDismiss,
              panelSize = modePanelSize,
              allowBackgroundInteraction = modePanelAllowsBackgroundInteraction,
              content = modePanelContent,
          )
      }
      transientPanelContent?.let { panelContent ->
          // Replacing one transient dialog with another must dispose the old Compose
          // Dialog window instead of reusing its back callback for the new content.
          key(panelContent) { panelContent() }
      }
      WorkspaceSnackbarHost(
          controller = snackbarController,
          portrait = portrait,
          toolPanelExpanded = toolPanelExpanded,
          // Use the measured visible rows, not the maximum two-row reservation. This
          // brings progress snackbars lower while keeping them clear of menu actions.
          toolPanelHeightDp = visibleMenuHeightDp.takeIf { it > 0 } ?: toolPanelHeightDp,
          navigationHeightDp = navigationHeightDp,
          startInsetDp = if (portrait) 8 else
              AdaptiveNavigationPolicy.navigationRailWidthDp(LocalBeamLayoutFontScale.current) + 8,
      )
    }
}

@Composable
private fun WorkspaceMenuHost(
    slot: Int,
    composeContentForSlot: @Composable (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = slot,
        // Keep the full panel in Compose's hit-test chain so empty padding cannot start a
        // GL gesture. The observer below deliberately never consumes; Lazy grids and tile
        // click/long-press gestures must arbitrate touch normally, as in the regular app.
        modifier = modifier.observeMenuPointerEvents(),
        transitionSpec = {
            val direction = if (targetState > initialState) 1 else -1
            (slideInHorizontally(tween(220)) { direction * it / 3 } + fadeIn(tween(180))) togetherWith
                (slideOutHorizontally(tween(220)) { -direction * it / 3 } + fadeOut(tween(160)))
        },
        label = "workspace-menu-route",
    ) { targetSlot ->
        key(targetSlot) {
            composeContentForSlot(targetSlot)
        }
    }
}

/** Keeps this overlay in the hit-test chain without claiming any gesture from its children. */
internal fun Modifier.observeMenuPointerEvents(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Final)
        }
    }
}

@Composable
internal fun WorkspaceActionControl(
    content: @Composable () -> Unit,
    allowVerticalGrowth: Boolean = false,
    targetWidth: androidx.compose.ui.unit.Dp = OrkanUiTokens.touchTarget,
    testTag: String? = null,
) {
    Box(
        // Preserve the compact target at normal text sizes while allowing content such
        // as the two-line plate counter to expand at large accessibility font scales.
        modifier = Modifier.width(targetWidth).then(
            if (allowVerticalGrowth) Modifier.heightIn(min = OrkanUiTokens.touchTarget)
            else Modifier.height(OrkanUiTokens.touchTarget),
        ).then(testTag?.let(Modifier::testTag) ?: Modifier),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) { content() }
}

/** Measurable policy for the portion of a compact workspace reserved for the actual build plate. */
internal object WorkspaceShellLayoutPolicy {
    const val MIN_LANDSCAPE_SIDE_PANEL_WIDTH_DP = 360
    private const val WORKSPACE_HEADER_HEIGHT_DP = 80

    /** Fixed workspace geometry sized to keep the profile text readable at maximum zoom. */
    @JvmStatic
    fun workspaceHeaderHeightDp(): Int = WORKSPACE_HEADER_HEIGHT_DP

    /** Keeps the renderer at full viewport size while fitting the expanded action menu over it. */
    @JvmStatic
    fun portraitMenuOverlayHeightDp(viewportHeightDp: Int, preferredHeightDp: Int, fontScale: Float): Int {
        val viewport = viewportHeightDp.coerceAtLeast(0)
        if (viewport == 0) return 0
        // The action host needs enough height for its chrome plus at least two
        // accessible rows at large text. The renderer still occupies the full
        // viewport behind this overlay, so reserving only 55% clipped actions.
        val maxOverlay = (viewport * 0.80f).toInt().coerceIn(1, viewport)
        val preferred = preferredHeightDp.coerceAtLeast(0)
        if (preferred <= maxOverlay) return preferred
        val twoRowMinimum = if (fontScale >= 1.15f) 224 else 176
        if (maxOverlay >= twoRowMinimum) return maxOverlay
        // If two rows no longer fit, retain one touch-friendly row. Its actions
        // remain horizontally scrollable and the full GL view continues behind it.
        val compactRow = if (fontScale >= 1.15f) 112 else 88
        return minOf(maxOverlay, compactRow)
    }

    @JvmStatic
    fun portraitMenuRows(availableHeightDp: Int, fontScale: Float): Int =
        if (availableHeightDp < if (fontScale >= 1.15f) 224 else 176) 1 else 2

    /** Keeps inspection controls outside the GL surface and leaves a visible plate viewport. */
    @JvmStatic
    fun dockedPortraitPanelHeightDp(availableHeightDp: Int, dimension: WorkspacePanelDimension?, fontScale: Float): Int {
        val available = availableHeightDp.coerceAtLeast(0)
        // A fixed 150 dp minimum still let the renderer shrink to a narrow strip on a
        // short phone. Reserve most of the workspace for the bed, scaling the guarantee
        // with the available height while retaining an absolute minimum for compact views.
        val minimumRenderer = maxOf(
            if (fontScale >= 1.3f) 120 else 150,
            (available * 0.60f).toInt(),
        )
        val requested = dimension?.let { WorkspacePanelSizePolicy.resolve(it, available.toFloat()).toInt() } ?: 0
        return minOf(requested, (available - minimumRenderer).coerceAtLeast(0))
    }

    /** Side docks leave a useful minimum width for the live renderer. */
    @JvmStatic
    fun dockedLandscapePanelWidthDp(availableWidthDp: Int, dimension: WorkspacePanelDimension?): Int {
        val available = availableWidthDp.coerceAtLeast(0)
        val requested = dimension?.let { WorkspacePanelSizePolicy.resolve(it, available.toFloat()).toInt() } ?: 0
        return minOf(requested, (available - 280).coerceAtLeast(0))
    }

    /** Narrow landscape workspaces dock inspection controls below the renderer instead of clipping them. */
    @JvmStatic
    fun dockedLandscapeBottomPanelHeightDp(availableHeightDp: Int): Int {
        val available = availableHeightDp.coerceAtLeast(0)
        val minimumRenderer = 100
        val maximumPanel = 300
        return minOf(maximumPanel, (available - minimumRenderer).coerceAtLeast(0))
    }

    /** Reserve a usable action menu while leaving most wide-window space to the renderer. */
    @JvmStatic
    fun menuPanelWidthDp(availableWidthDp: Int, fontScale: Float): Int {
        val minimum = if (fontScale >= 1.3f) 224 else 200
        val maximum = if (fontScale >= 1.3f) 320 else 280
        val preferred = (availableWidthDp.coerceAtLeast(0) * 0.32f).toInt().coerceIn(minimum, maximum)
        val rendererMinimum = 280
        val widthLimited = availableWidthDp -
            AdaptiveNavigationPolicy.navigationRailWidthDp(fontScale) - rendererMinimum
        return minOf(preferred, widthLimited.coerceAtLeast(minimum))
    }

    /** Two action rows fit below the landscape renderer while preserving touch targets. */
    @JvmStatic
    fun landscapeMenuPanelMaxHeightDp(fontScale: Float): Int =
        if (fontScale >= 1.3f) 244 else 208

    /** availableHeightDp is the content height after the activity's system-bar insets. */
    @JvmStatic
    fun plateViewportHeightDp(
        availableHeightDp: Int,
        projectBarHeightDp: Int,
        workspaceHeaderHeightDp: Int,
        navigationHeightDp: Int,
        toolPanelHeightDp: Int,
        toolPanelExpanded: Boolean,
    ): Int {
        val reserved = projectBarHeightDp.coerceAtLeast(0) +
            workspaceHeaderHeightDp.coerceAtLeast(0) +
            navigationHeightDp.coerceAtLeast(0) +
            if (toolPanelExpanded) toolPanelHeightDp.coerceAtLeast(0) else 0
        return (availableHeightDp.coerceAtLeast(0) - reserved).coerceAtLeast(0)
    }
}
