package ru.ytkab0bp.slicebeam.components.bed_menu

import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
    private const val TILE_SIZE_REDUCTION = 0.8f

    @Suppress("UNUSED_PARAMETER")
    fun columnsForLayout(fontScale: Float, screenWidthDp: Int): Int = 4

    fun reducedTileHeightDp(sizeDp: Int): Int = (sizeDp * TILE_SIZE_REDUCTION).toInt().coerceAtLeast(48)

    /** Keep four-column Orca labels readable without splitting long words at large system text sizes. */
    fun labelScaleForFontScale(fontScale: Float): Float = when {
        fontScale >= 1.5f -> 0.6f
        fontScale >= 1.3f -> 0.75f
        else -> 1f
    }

    /** Up to four actions use one row; later rows are revealed only by vertical scrolling. */
    fun visibleRowsForActionCount(actionCount: Int): Int =
        ((actionCount.coerceAtLeast(1) + 3) / 4).coerceIn(1, 2)

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
        // Measure to the actual one/two-row grid height. The workspace overlay wraps
        // this content, so a short menu no longer reserves a large empty panel.
        BoxWithConstraints(modifier.fillMaxWidth()) {
            val visibleRows = BedMenuActionTileWidthPolicy.visibleRowsForActionCount(actions.size)
            val gridState = rememberLazyGridState()
            var gridBounds by remember { mutableStateOf(Rect.Zero) }
            val latestDragPointer = rememberUpdatedState(draggedActionPointerInRoot)
            val edgeSizePx = with(LocalDensity.current) { 44.dp.toPx() }
            LaunchedEffect(draggedActionTitleRes) {
                while (draggedActionTitleRes != null) {
                    val pointer = latestDragPointer.value
                    val bounds = gridBounds
                    if (pointer != null && bounds.height > 0f) {
                        val delta = when {
                            pointer.y < bounds.top + edgeSizePx -> -6f
                            pointer.y > bounds.bottom - edgeSizePx -> 6f
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
            val gridTileHeight = BedMenuActionTileWidthPolicy
                .reducedTileHeightDp(if (fontScale >= 1.15f) 112 else 94).dp
            val gridHeight = minOf(
                maxHeight,
                gridTileHeight * visibleRows + 2.dp * (visibleRows - 1),
            )
            // A vertical grid fills each row left-to-right. Four actions stay on one row,
            // actions five through eight occupy the second row, and only further actions scroll.
            val actionIds = remember(actions) { actions.map { it.titleRes } }
            var previousActionIds by remember { mutableStateOf<List<Int>?>(null) }
            LaunchedEffect(actionIds) {
                if (previousActionIds != null && previousActionIds != actionIds) {
                    gridState.scrollToItem(0)
                }
                previousActionIds = actionIds
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = gridState,
                modifier = Modifier.fillMaxWidth().height(gridHeight)
                    .onGloballyPositioned { gridBounds = it.boundsInRoot() }
                    .testTag("workspace-menu-actions-grid"),
                contentPadding = PaddingValues(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // Slice menus replace their whole action set after slicing. Stable action
                // identity prevents the grid from reusing an old tile and its click handler.
                items(actions, key = { action -> action.titleRes }) { action ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        BedMenuActionTile(
                            action,
                            portrait = true,
                            menuView = menuView,
                            compactPortraitGrid = false,
                            // Fill the action cell. Reducing the tile to 80% of an
                            // already compact cell left too little width for Orca's
                            // real labels on 480 dp screens.
                            modifier = Modifier.fillMaxWidth().height(gridTileHeight),
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
        return
    } else {
        // Landscape has enough horizontal room for the same 4-column action layout.
        // Keep the actions in a short strip below the renderer so the bed remains the
        // visual center instead of reserving a tall column beside it.
        BoxWithConstraints(modifier.fillMaxWidth()) {
            val columns = 4
            val gridState = rememberLazyGridState()
            // Keep wrapped Orca labels readable at large system font sizes while
            // retaining the compact two-row landscape strip.
            val tileHeight = if (fontScale >= 1.3f) 82.dp else 70.dp
            val visibleRows = BedMenuActionTileWidthPolicy.visibleRowsForActionCount(actions.size)
            val targetHeight = tileHeight * visibleRows + 2.dp * (visibleRows - 1)
            val gridHeight = minOf(maxHeight, targetHeight)
            val actionIds = remember(actions) { actions.map { it.titleRes } }
            var previousActionIds by remember { mutableStateOf<List<Int>?>(null) }
            LaunchedEffect(actionIds) {
                if (previousActionIds != null && previousActionIds != actionIds) gridState.scrollToItem(0)
                previousActionIds = actionIds
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = gridState,
                modifier = Modifier.fillMaxWidth().height(gridHeight)
                    .testTag("workspace-menu-actions-grid"),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(actions, key = { action -> action.titleRes }) { action ->
                    BedMenuActionTile(
                        action,
                        portrait = true,
                        menuView = menuView,
                        compactPortraitGrid = false,
                        modifier = Modifier.fillMaxWidth().height(tileHeight),
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
}

/** Multicolor action artwork extracted from the user-provided OrkanSlicer button sheets. */
private fun colorfulMenuActionIcon(titleRes: Int): Int? = when (titleRes) {
    R.string.WorkspaceObjects -> R.drawable.action3d_objects
    R.string.ModelMenuDuplicate -> R.drawable.action3d_duplicate
    R.string.ModelMenuMoveToPlate -> R.drawable.action3d_move_plate
    R.string.ModelMenuSplit -> R.drawable.action3d_split
    R.string.ModelMenuCenterOnBed -> R.drawable.action3d_center
    R.string.ModelScaleToBuildVolume -> R.drawable.action3d_build_volume
    R.string.ModelMenuPlaceGroupOnBed -> R.drawable.action3d_place_on_bed
    R.string.ModelMenuPlaceOnObject -> R.drawable.action3d_place_on_object
    R.string.ModelMenuPlaceOnSurface -> R.drawable.action3d_place_surface
    R.string.ModelMenuArrange, R.string.MenuOrientationArrange,
    R.string.MenuArrangeAllObjects -> R.drawable.action3d_arrange
    R.string.ModelMenuAlignSelected -> R.drawable.action3d_align_all
    R.string.ModelMenuAlignPrimaryAxes -> R.drawable.action3d_axes
    R.string.ModelMenuDistributeSelected -> R.drawable.action3d_arrange
    R.string.ModelMenuCombineSelected -> R.drawable.action3d_combine
    R.string.ModelMenuBoolean -> R.drawable.action3d_boolean
    R.string.ModelMenuFillBed, R.string.ModelContextFillBed -> R.drawable.action3d_fill_bed
    R.string.ModelMenuModifiers -> R.drawable.action3d_modifiers
    R.string.ModelMenuObjectSettings -> R.drawable.action3d_object_settings
    R.string.ModelMenuCreatePrimitive -> R.drawable.action3d_primitive
    R.string.ModelMenuOrientAll, R.string.MenuOrientAllObjects,
    R.string.ModelMenuAutoOrientSelected, R.string.MenuOrientationAutoOrient -> R.drawable.action3d_auto_orient
    R.string.ModelMenuFlatten, R.string.MenuOrientationFlatten -> R.drawable.action3d_flatten
    R.string.ModelMenuDeleteSelected -> R.drawable.action3d_delete
    R.string.ModelMenuSelectAll -> R.drawable.action3d_select_all
    R.string.ModelMenuDeleteAll -> R.drawable.action3d_delete_all
    R.string.ModelContextResetRotation -> R.drawable.action3d_reset_rotation
    R.string.ModelContextResetScale -> R.drawable.action3d_reset_scale
    R.string.ModelUndo -> R.drawable.action3d_undo
    R.string.ModelRedo -> R.drawable.action3d_redo
    R.string.MenuToolbarMove, R.string.MenuOrientationPosition -> R.drawable.action3d_move
    R.string.MenuToolbarRotate, R.string.MenuOrientationRotation -> R.drawable.action3d_rotate
    R.string.MenuTransformScale -> R.drawable.action3d_scale
    R.string.ModelSimplify -> R.drawable.action3d_simplify
    R.string.ModelSmoothMesh -> R.drawable.action3d_smooth_mesh
    R.string.MenuTransformCut -> R.drawable.action3d_cut
    R.string.MenuToolbarVariableLayerHeight -> R.drawable.action3d_variable_layer_height
    R.string.MenuToolbarSupportPaint -> R.drawable.action3d_paint_support
    R.string.MenuToolbarSeamPaint -> R.drawable.action3d_paint_seam
    R.string.MenuToolbarFuzzyPaint -> R.drawable.action3d_paint_fuzzy_skin
    R.string.MenuToolbarColorPaint -> R.drawable.action3d_paint_color
    R.string.MenuToolbarEmboss -> R.drawable.action3d_emboss
    R.string.MenuToolbarMeasure -> R.drawable.action3d_calibration
    R.string.QuickSliceTitle -> R.drawable.action3d_quick_slice
    R.string.SmartSliceTitle -> R.drawable.action3d_smart_slice
    R.string.MenuSliceCurrentSettings -> R.drawable.action3d_plate_slice
    R.string.WorkspaceAddModelAction, R.string.MenuFileOpen -> R.drawable.action3d_open_model
    R.string.RecentModelsTitle -> R.drawable.action3d_recent
    R.string.MenuFileImportProfiles -> R.drawable.action3d_import_profiles
    R.string.MenuFileExportProfiles -> R.drawable.action3d_export_profiles
    R.string.MenuFileExport3mf, R.string.MenuSliceExportToFile -> R.drawable.action3d_export_3mf
    R.string.MenuSliceSendToPrinter, R.string.MenuSliceSendToPrinterAndPrint -> R.drawable.action3d_slice
    R.string.MenuFileCalibrations -> R.drawable.action3d_calibration
    else -> null
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
    // Show OrcaSlicer's actual action name. Locally invented abbreviations hid or
    // changed the meaning of several actions, especially in the compact grids.
    val actionLabel = action.titleRes
    val background = (if (action.selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer)
        .copy(alpha = if (action.enabled) 1f else 0.38f)
    val foreground = (if (action.selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
        .copy(alpha = if (action.enabled) 1f else 0.55f)
    // The section accent is already used by the tile surface. Reusing its darker
    // shade for the glyph made icons disappear into that surface. Use the contrasting
    // foreground and scale the vector up below so its strokes remain clear at phone size.
    val iconTint = (if (action.selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onPrimaryContainer)
        .copy(alpha = if (action.enabled) 1f else 0.55f)
    val colorfulIcon = colorfulMenuActionIcon(action.titleRes)
    val iconRes = colorfulIcon ?: action.iconRes
    val iconColorFilter = if (colorfulIcon != null) null else ColorFilter.tint(iconTint)
    // At large system font sizes, keep enough tile height for the two-line Orca
    // label. The colorful artwork remains prominent but must not push the title
    // outside the fixed-height menu cell.
    val displayFontScale = ru.ytkab0bp.slicebeam.ui.LocalBeamLayoutFontScale.current
    val tileLabelScale = BedMenuActionTileWidthPolicy.labelScaleForFontScale(displayFontScale)
    val tileLabelFontSize = OrkanUiTokens.menuLabelSize * tileLabelScale
    val tileLabelLineHeight = 16.sp * tileLabelScale
    val iconSize = when {
        colorfulIcon != null && displayFontScale >= 1.3f -> 28.dp
        colorfulIcon != null -> 40.dp
        displayFontScale >= 1.3f -> 24.dp
        else -> 30.dp
    }
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
            // Lazy grids otherwise draw later cells over a translated tile.
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
                // label. This preserves its real touch target in compact portrait grids.
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
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        colorFilter = iconColorFilter,
                        modifier = Modifier.size(iconSize),
                    )
                }
                Text(
                    text = stringResource(actionLabel),
                    color = foreground,
                    fontSize = tileLabelFontSize,
                    lineHeight = tileLabelLineHeight,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    softWrap = true,
                    autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(
                        minFontSize = 8.sp,
                        maxFontSize = tileLabelFontSize,
                        stepSize = 1.sp,
                    ),
                    overflow = TextOverflow.Clip,
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
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    colorFilter = iconColorFilter,
                    modifier = Modifier.size(iconSize),
                )
                Text(
                    text = stringResource(actionLabel),
                    color = foreground,
                    fontSize = tileLabelFontSize,
                    lineHeight = tileLabelLineHeight,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    softWrap = true,
                    autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(
                        minFontSize = 8.sp,
                        maxFontSize = tileLabelFontSize,
                        stepSize = 1.sp,
                    ),
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                )
            }
        }
    }
}
