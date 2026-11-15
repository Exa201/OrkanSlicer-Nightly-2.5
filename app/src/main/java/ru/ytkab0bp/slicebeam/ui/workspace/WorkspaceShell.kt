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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import androidx.compose.ui.platform.testTag
import ru.ytkab0bp.slicebeam.ui.OrkanUiTokens
import ru.ytkab0bp.slicebeam.ui.ColorFlow
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
    platePickerContent: @Composable () -> Unit,
    viewCubeContent: @Composable () -> Unit,
    printerWebButtonContent: @Composable () -> Unit,
    modePanelContent: (@Composable () -> Unit)?,
    modePanelSize: WorkspacePanelSize?,
    modePanelDocked: Boolean,
    modePanelAllowsBackgroundInteraction: Boolean,
    onModePanelDismiss: () -> Unit,
    transientPanelContent: (@Composable () -> Unit)?,
    snackbarController: WorkspaceSnackbarController,
) {
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
    val headerActionWeight = if (LocalConfiguration.current.screenWidthDp <= 320) 2f else 1.45f
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
            accent = ColorFlow.workspaceDestination(destination.ordinal),
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
                // Reserve the header height for the largest app text zoom. The
                // reservation depends only on Android's accessibility scale, so
                // moving the app slider cannot change the bed viewport geometry.
                // Keep a stable minimum for ordinary names, but permit the profile chip
                // to wrap full localized printer names and summaries without clipping.
                .heightIn(min = WorkspaceShellLayoutPolicy.workspaceHeaderHeightDp(LocalBeamLayoutFontScale.current).dp)
                .padding(horizontal = 6.dp, vertical = 4.dp),
        ) {
            Box(
                modifier = Modifier.weight(1f).widthIn(min = 0.dp).heightIn(min = OrkanUiTokens.touchTarget),
            ) { profileStatusContent() }
            Row(
                // Reserve enough finite width for all four 48 dp targets at 320 dp
                // and 360 dp phone widths. The printer card yields width first; the
                // weighted constraint also keeps this scroller safe in landscape.
                modifier = Modifier.weight(headerActionWeight).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(OrkanUiTokens.compactGap),
            ) {
                // Active tools own their relevant controls and may render their own
                // filament selector (color paint does). Suppress the global shortcut
                // for docked and modal hosts, so both presentations cannot expose two
                // competing filament rows during tool activation or dismissal.
                if (modePanelContent == null) {
                    WorkspaceActionControl(filamentButtonContent)
                }
                WorkspaceActionControl(platePickerContent, allowVerticalGrowth = true, targetWidth = 56.dp)
                WorkspaceActionControl(viewCubeContent)
                WorkspaceActionControl(printerWebButtonContent)
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
                                    .height(overlayHeightDp.dp)
                                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.97f))
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
            WorkspaceMenuHost(
                slot = activeMenuSlot,
                composeContentForSlot = menuComposeContentForSlot,
                modifier = Modifier.width(menuWidthDp.dp).fillMaxHeight()
                    .testTag("workspace-menu-panel"),
            )
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
          toolPanelHeightDp = toolPanelHeightDp,
          navigationHeightDp = navigationHeightDp,
          startInsetDp = if (portrait) 8 else
              AdaptiveNavigationPolicy.navigationRailWidthDp(LocalBeamLayoutFontScale.current) + menuWidthDp + 8,
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
        modifier = modifier,
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

@Composable
internal fun WorkspaceActionControl(
    content: @Composable () -> Unit,
    allowVerticalGrowth: Boolean = false,
    targetWidth: androidx.compose.ui.unit.Dp = OrkanUiTokens.touchTarget,
) {
    Box(
        // Preserve the compact target at normal text sizes while allowing content such
        // as the two-line plate counter to expand at large accessibility font scales.
        modifier = Modifier.width(targetWidth).then(
            if (allowVerticalGrowth) Modifier.heightIn(min = OrkanUiTokens.touchTarget)
            else Modifier.height(OrkanUiTokens.touchTarget),
        ),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) { content() }
}

/** Measurable policy for the portion of a compact workspace reserved for the actual build plate. */
internal object WorkspaceShellLayoutPolicy {
    const val MIN_LANDSCAPE_SIDE_PANEL_WIDTH_DP = 360
    private const val MAX_APP_TEXT_SCALE = 1.5f

    /** Stable minimum header geometry with room for the common profile title and summary. */
    @JvmStatic
    fun workspaceHeaderHeightDp(systemFontScale: Float): Int {
        val stableScale = (systemFontScale.takeIf { it.isFinite() && it > 0f } ?: 1f) * MAX_APP_TEXT_SCALE
        // Two 18sp title lines plus the 12sp summary and a font-metric safety margin,
        // then the fixed vertical padding around the header row.
        val contentHeight = ceil(37f * stableScale).toInt().coerceAtLeast(48)
        return contentHeight + 8
    }

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
        val twoRowMinimum = if (fontScale >= 1.3f) 152 else 112
        if (maxOverlay >= twoRowMinimum) return maxOverlay
        // If two rows no longer fit, retain a touch-friendly compact menu panel;
        // further actions remain vertically scrollable over the full GL viewport.
        val compactRow = if (fontScale >= 1.3f) 88 else 64
        return minOf(maxOverlay, compactRow)
    }

    @JvmStatic
    fun portraitMenuRows(availableHeightDp: Int, fontScale: Float): Int =
        if (availableHeightDp < if (fontScale >= 1.3f) 152 else 112) 1 else 2

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
