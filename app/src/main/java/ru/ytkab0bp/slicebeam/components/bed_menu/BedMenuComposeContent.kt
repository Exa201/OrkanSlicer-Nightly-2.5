package ru.ytkab0bp.slicebeam.components.bed_menu

import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.ui.OrkanUiTokens
import ru.ytkab0bp.slicebeam.ui.workspace.WorkspaceShellLayoutPolicy
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** A menu route whose visible workspace content is hosted directly by WorkspaceShell. */
internal interface WorkspaceComposeBedMenu {
    @Composable
    fun WorkspaceContent(portrait: Boolean)

    fun onWorkspaceMenuVisible() = Unit

    fun onWorkspaceMenuHidden() = Unit
}

@Composable
internal fun WorkspaceComposeBedMenuHost(menu: WorkspaceComposeBedMenu, portrait: Boolean) {
    androidx.compose.runtime.DisposableEffect(menu) {
        menu.onWorkspaceMenuVisible()
        onDispose { menu.onWorkspaceMenuHidden() }
    }
    menu.WorkspaceContent(portrait)
}

/** A menu action that can be shared by responsive Compose menus. */
internal data class BedComposeMenuAction(
    val titleRes: Int,
    val iconRes: Int,
    val onClick: (View) -> Unit,
    val selected: Boolean = false,
    val toggleable: Boolean = false,
    val sectionBreakBefore: Boolean = false,
    val enabled: Boolean = true,
)

/** Keeps phone portrait action menus consistent with four tiles in each of two rows. */
internal object BedMenuActionTileWidthPolicy {
    @Suppress("UNUSED_PARAMETER")
    fun columnsForLayout(fontScale: Float, screenWidthDp: Int): Int = 4

    /** Fits the fixed-column horizontal grid after accounting for both paddings and inter-column gaps. */
    fun gridTileWidthDp(
        screenWidthDp: Int,
        columns: Int,
        horizontalPaddingDp: Int = 4,
        spacingDp: Int = 2,
        reservedEndDp: Int = horizontalPaddingDp,
    ): Int {
        val safeColumns = columns.coerceAtLeast(1)
        val available = screenWidthDp.coerceAtLeast(0) - horizontalPaddingDp -
            reservedEndDp - spacingDp * (safeColumns - 1)
        return (available / safeColumns).coerceAtLeast(48)
    }
}

@Composable
internal fun BedMenuActionsContent(
    actions: List<BedComposeMenuAction>,
    portrait: Boolean,
    modifier: Modifier = Modifier,
    onActionPositioned: ((Int, Rect) -> Unit)? = null,
    onActionDragStart: ((Int, Offset) -> Unit)? = null,
    onActionDrag: ((Int, Offset) -> Unit)? = null,
    onActionDragEnd: (() -> Unit)? = null,
    onMoveAction: ((Int, Int) -> Unit)? = null,
    draggedActionTitleRes: Int? = null,
    dropTargetTitleRes: Int? = null,
    draggedActionTranslation: Offset = Offset.Zero,
    draggedActionPointerInRoot: Offset? = null,
    onDragAutoScroll: ((Float) -> Unit)? = null,
) {
    val menuView = LocalView.current
    val widthDp = LocalConfiguration.current.screenWidthDp
    val fontScale = ru.ytkab0bp.slicebeam.ui.LocalBeamLayoutFontScale.current
    if (portrait) {
        val columns = BedMenuActionTileWidthPolicy.columnsForLayout(fontScale, widthDp)
        BoxWithConstraints(modifier.fillMaxSize()) {
            val rows = WorkspaceShellLayoutPolicy.portraitMenuRows(
                availableHeightDp = maxHeight.value.toInt(),
                fontScale = fontScale,
            )
            val tileWidth = BedMenuActionTileWidthPolicy.gridTileWidthDp(
                screenWidthDp = maxWidth.value.toInt(),
                columns = columns,
                horizontalPaddingDp = 4,
                spacingDp = 2,
                reservedEndDp = 4,
            ).dp
            val gridState = rememberLazyGridState()
            var gridBounds by remember { mutableStateOf(Rect.Zero) }
            val latestDragPointer = rememberUpdatedState(draggedActionPointerInRoot)
            val edgeSizePx = with(LocalDensity.current) { 44.dp.toPx() }
            LaunchedEffect(draggedActionTitleRes) {
                while (draggedActionTitleRes != null) {
                    val pointer = latestDragPointer.value
                    val bounds = gridBounds
                    if (pointer != null && bounds.width > 0f) {
                        val delta = when {
                            pointer.x < bounds.left + edgeSizePx -> -6f
                            pointer.x > bounds.right - edgeSizePx -> 6f
                            else -> 0f
                        }
                        if (delta != 0f) {
                            val consumed = gridState.scrollBy(delta)
                            if (consumed != 0f) {
                                // Wait for the lazy grid to publish its new item bounds before
                                // recalculating the highlighted drop target under a stationary finger.
                                withFrameNanos { }
                                onDragAutoScroll?.invoke(consumed)
                            }
                        }
                    }
                    delay(16)
                }
            }
            val gridTileHeight = if (fontScale >= 1.15f) 112.dp else 96.dp
            // LazyHorizontalGrid fills cells top-to-bottom. Reorder each 4×2 page so the
            // user's action order reads left-to-right across the first row, then the second.
            val rowMajorActions = remember(actions, columns, rows) {
                actions.chunked(columns * rows).flatMap { page ->
                    page.indices.sortedWith(compareBy({ it % columns }, { it / columns })).map(page::get)
                }
            }
            val actionIds = remember(rowMajorActions) { rowMajorActions.map { it.titleRes } }
            var previousActionIds by remember { mutableStateOf<List<Int>?>(null) }
            LaunchedEffect(rows, actionIds) {
                if (previousActionIds != null && previousActionIds != actionIds) {
                    gridState.scrollToItem(0)
                }
                previousActionIds = actionIds
            }
            LazyHorizontalGrid(
                rows = GridCells.Fixed(rows),
                state = gridState,
                flingBehavior = rememberSnapFlingBehavior(gridState),
                modifier = Modifier.fillMaxSize()
                    .onGloballyPositioned { gridBounds = it.boundsInRoot() }
                    .testTag("workspace-menu-actions-grid"),
                contentPadding = PaddingValues(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // Slice menus replace their whole action set after slicing. Stable action
                // identity prevents the grid from reusing an old tile and its click handler.
                items(rowMajorActions, key = { action -> action.titleRes }) { action ->
                    BedMenuActionTile(
                        action,
                        portrait = true,
                        menuView = menuView,
                        // At large text sizes the icon yields its space to the full label.
                        compactPortraitGrid = fontScale >= 1.3f,
                        modifier = Modifier.width(tileWidth).height(gridTileHeight),
                        onActionPositioned = onActionPositioned,
                        onActionDragStart = onActionDragStart,
                        onActionDrag = onActionDrag,
                        onActionDragEnd = onActionDragEnd,
                        onMoveAction = onMoveAction,
                        draggedActionTitleRes = draggedActionTitleRes,
                        dropTargetTitleRes = dropTargetTitleRes,
                        draggedActionTranslation = draggedActionTranslation,
                        draggedActionPointerInRoot = draggedActionPointerInRoot,
                        onDragAutoScroll = onDragAutoScroll,
                    )
                }
            }
        }
        return
    } else {
        Column(
            // A horizontal Android parent can temporarily measure MATCH_PARENT children
            // with an unbounded height while the navigation layout is being rebuilt.
            // Give the scroll container a finite outer cap before applying verticalScroll.
            modifier = modifier
                .heightIn(max = LocalConfiguration.current.screenHeightDp.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 4.dp)
                .testTag("workspace-menu-actions-grid"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            actions.forEach { action ->
                if (action.sectionBreakBefore) Spacer(Modifier.heightIn(min = 6.dp))
                BedMenuActionTile(
                    action, portrait = false, menuView = menuView, modifier = Modifier.fillMaxWidth(),
                    onActionPositioned = onActionPositioned,
                    onActionDragStart = onActionDragStart,
                    onActionDrag = onActionDrag,
                    onActionDragEnd = onActionDragEnd,
                    onMoveAction = onMoveAction,
                    draggedActionTitleRes = draggedActionTitleRes,
                    dropTargetTitleRes = dropTargetTitleRes,
                    draggedActionTranslation = draggedActionTranslation,
                    draggedActionPointerInRoot = draggedActionPointerInRoot,
                    onDragAutoScroll = onDragAutoScroll,
                )
            }
        }
    }
}

@Composable
private fun BedMenuActionTile(
    action: BedComposeMenuAction,
    portrait: Boolean,
    menuView: View,
    compactPortraitGrid: Boolean = false,
    modifier: Modifier,
    onActionPositioned: ((Int, Rect) -> Unit)?,
    onActionDragStart: ((Int, Offset) -> Unit)?,
    onActionDrag: ((Int, Offset) -> Unit)?,
    onActionDragEnd: (() -> Unit)?,
    onMoveAction: ((Int, Int) -> Unit)?,
    draggedActionTitleRes: Int?,
    dropTargetTitleRes: Int?,
    draggedActionTranslation: Offset,
    draggedActionPointerInRoot: Offset?,
    onDragAutoScroll: ((Float) -> Unit)?,
) {
    val fullTitle = stringResource(action.titleRes)
    val fontScale = ru.ytkab0bp.slicebeam.ui.LocalBeamLayoutFontScale.current
    val compactLabel = when (action.titleRes) {
        R.string.MenuCameraIsometric -> if (fontScale >= 1.3f) R.string.MenuCameraIsometricCompact else action.titleRes
        R.string.MenuFileImportProfiles -> R.string.MenuFileImportProfilesCompact
        R.string.MenuFileExportProfiles -> R.string.MenuFileExportProfilesCompact
        R.string.ModelUndo -> R.string.EditUndoShort
        R.string.ModelRedo -> R.string.EditRedoShort
        R.string.MenuArrangeAllObjects -> R.string.EditArrangeAllShort
        R.string.MenuOrientAllObjects -> R.string.EditOrientShort
        R.string.MenuOrientationArrange -> R.string.EditPackPlateShort
        R.string.MenuFileMoveToPlate -> R.string.EditMovePlateShort
        R.string.ModelContextFillBed -> R.string.EditFillBedShort
        R.string.ModelContextCenterOnBed -> R.string.EditCenterBedShort
        R.string.ModelMenuDuplicate -> R.string.ModelMenuDuplicateShort
        R.string.ModelMenuMoveToPlate -> R.string.ModelMenuMoveToPlateShort
        R.string.ModelMenuCenterOnBed -> R.string.ModelMenuCenterOnBedShort
        R.string.ModelMenuPlaceGroupOnBed -> R.string.ModelMenuPlaceGroupOnBedShort
        R.string.ModelMenuFillBed -> R.string.ModelMenuFillBedShort
        R.string.ModelMenuArrange -> R.string.ModelMenuArrangeShort
        R.string.ModelMenuOrientAll -> R.string.ModelMenuOrientAllShort
        R.string.ModelMenuAutoOrientSelected -> R.string.ModelMenuAutoOrientSelectedShort
        R.string.ModelMenuFlatten -> R.string.ModelMenuFlattenShort
        R.string.ModelMenuPlaceOnObject -> R.string.ModelMenuPlaceOnObjectShort
        R.string.ModelMenuCombineSelected -> R.string.ModelMenuCombineSelectedShort
        R.string.ModelMenuDeleteSelected -> R.string.ModelMenuDeleteSelectedShort
        R.string.ModelMenuSelectAll -> R.string.ModelMenuSelectAllShort
        R.string.ModelMenuDeleteAll -> R.string.ModelMenuDeleteAllShort
        R.string.WorkspaceObjects -> R.string.ModelMenuObjectsShort
        R.string.ModelContextResetRotation -> R.string.EditResetRotationShort
        R.string.ModelContextResetScale -> R.string.EditResetScaleShort
        R.string.ModelContextSetOnFace -> R.string.EditLayFaceShort
        R.string.ModelContextSelectAll -> R.string.EditSelectAllShort
        R.string.ModelContextDeleteAll -> R.string.EditDeleteAllShort
        R.string.ModelColorSlotsAction -> R.string.ModelColorSlotsShort
        R.string.MenuSlicePerformance -> R.string.GCodePerformanceShort
        R.string.MenuSliceExportToFile -> R.string.GCodeExportShort
        R.string.MenuCameraOrtho -> R.string.MenuCameraOrthoShort
        R.string.MenuOrientationAutoOrient -> R.string.MenuActionAutoOrientShort
        R.string.ModelSimplify -> R.string.MenuActionSimplifyShort
        R.string.ModelSmoothMesh -> R.string.ModelSmoothMeshShort
        R.string.MenuOrientationFlatten -> R.string.MenuActionLayFlatShort
        R.string.MenuToolbarSupportPaint -> R.string.MenuActionSupportShort
        R.string.MenuToolbarSeamPaint -> R.string.MenuActionSeamShort
        R.string.MenuToolbarFuzzyPaint -> R.string.MenuActionTextureShort
        R.string.MenuToolbarColorPaint -> R.string.MenuActionColorShort
        R.string.MenuToolbarVariableLayerHeight -> R.string.MenuActionLayerHeightShort
        R.string.MenuToolbarEmboss -> R.string.MenuActionEmbossShort
        R.string.MenuToolbarMeasure -> R.string.MenuActionMeasureShort
        else -> action.titleRes
    }
    val background = (if (action.selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        .copy(alpha = if (action.enabled) 1f else 0.38f)
    val foreground = (if (action.selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
        .copy(alpha = if (action.enabled) 1f else 0.55f)
    val interaction = if (action.toggleable) {
        Modifier.toggleable(
            value = action.selected,
            enabled = action.enabled,
            role = Role.Switch,
            onValueChange = { action.onClick(menuView) },
        )
    } else {
        Modifier.clickable(enabled = action.enabled, role = Role.Button) { action.onClick(menuView) }
    }
    val latestDragStart = rememberUpdatedState(onActionDragStart)
    val latestDrag = rememberUpdatedState(onActionDrag)
    val latestDragEnd = rememberUpdatedState(onActionDragEnd)
    val moveUpDescription = stringResource(R.string.MenuCustomizeMoveUp)
    val moveDownDescription = stringResource(R.string.MenuCustomizeMoveDown)
    val dragModifier = if (onActionPositioned != null || onActionDragStart != null || onActionDrag != null) {
        Modifier
            .then(if (onActionPositioned != null) Modifier.onGloballyPositioned {
                onActionPositioned(action.titleRes, it.boundsInRoot())
            } else Modifier)
            .then(if (onActionDragStart != null && onActionDrag != null) Modifier.pointerInput(action.titleRes) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { latestDragStart.value?.invoke(action.titleRes, it) },
                    onDragEnd = { latestDragEnd.value?.invoke() },
                    onDragCancel = { latestDragEnd.value?.invoke() },
                    onDrag = { change, amount ->
                        change.consume()
                        latestDrag.value?.invoke(action.titleRes, amount)
                    },
                )
            } else Modifier)
    } else Modifier
    val isDragged = draggedActionTitleRes == action.titleRes
    val isDropTarget = !isDragged && dropTargetTitleRes == action.titleRes
    val dragAccent = MaterialTheme.colorScheme.primary
    Surface(
        modifier = modifier
            .padding(3.dp)
            .then(if (isDragged || isDropTarget) Modifier.border(
                width = if (isDragged) 2.dp else 1.5.dp,
                color = dragAccent,
                shape = RoundedCornerShape(OrkanUiTokens.cornerRadius),
            ) else Modifier)
            // Keep the lifted item above its neighbours while it follows the pointer.
            // LazyRow/LazyHorizontalGrid otherwise draw later cells over a translated tile.
            .zIndex(if (isDragged) 1f else 0f)
            .graphicsLayer {
                if (isDragged) {
                    translationX = draggedActionTranslation.x
                    translationY = draggedActionTranslation.y
                    scaleX = 1.1f
                    scaleY = 1.1f
                    rotationZ = -1.5f
                    shadowElevation = 30.dp.toPx()
                    alpha = 1f
                } else {
                    rotationZ = 0f
                }
            }
            .then(dragModifier)
            .then(interaction)
            // Keep the accessible node on the whole clickable tile, not just its short
            // label. This preserves its real touch target in compact horizontal grids.
            .semantics(mergeDescendants = true) {
                contentDescription = fullTitle
                if (onMoveAction != null) {
                    customActions = listOf(
                        CustomAccessibilityAction(moveUpDescription) {
                            onMoveAction(action.titleRes, -1)
                            true
                        },
                        CustomAccessibilityAction(moveDownDescription) {
                            onMoveAction(action.titleRes, 1)
                            true
                        },
                    )
                }
            }
            .testTag("workspace-menu-action-${action.titleRes}"),
        shape = RoundedCornerShape(OrkanUiTokens.cornerRadius),
        color = when {
            isDragged -> MaterialTheme.colorScheme.primaryContainer
            isDropTarget -> MaterialTheme.colorScheme.secondaryContainer
            else -> background
        },
        contentColor = when {
            isDragged -> MaterialTheme.colorScheme.onPrimaryContainer
            isDropTarget -> MaterialTheme.colorScheme.onSecondaryContainer
            else -> foreground
        },
    ) {
        if (portrait) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (!compactPortraitGrid) {
                    Image(
                        painter = painterResource(action.iconRes),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(foreground),
                        modifier = Modifier.size(OrkanUiTokens.menuIconSize),
                    )
                }
                Text(
                    text = stringResource(compactLabel),
                    color = foreground,
                    fontSize = OrkanUiTokens.menuLabelSize,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.Center,
                    maxLines = if (portrait) 4 else Int.MAX_VALUE,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(min = OrkanUiTokens.touchTarget).padding(horizontal = 3.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Image(
                    painter = painterResource(action.iconRes),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(foreground),
                    modifier = Modifier.size(OrkanUiTokens.menuIconSize),
                )
                Text(
                    text = stringResource(compactLabel),
                    color = foreground,
                    fontSize = OrkanUiTokens.menuLabelSize,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                )
            }
        }
    }
}
