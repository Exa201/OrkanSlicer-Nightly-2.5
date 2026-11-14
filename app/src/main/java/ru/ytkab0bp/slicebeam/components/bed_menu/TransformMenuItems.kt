package ru.ytkab0bp.slicebeam.components.bed_menu

import android.view.View
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.SliceBeam
import ru.ytkab0bp.slicebeam.events.NeedSnackbarEvent
import ru.ytkab0bp.slicebeam.fragment.BedFragment
import ru.ytkab0bp.slicebeam.render.GLRenderer
import ru.ytkab0bp.slicebeam.render.ModelSelectionSnapshot
import java.util.function.Consumer
import kotlin.math.max
import kotlin.math.roundToInt

/** Builds Compose actions for the edit menu and retains every model operation and submenu route. */
internal class TransformMenuItems(
    private val fragment: BedFragment,
    private val showPositionMenu: Consumer<View>,
    private val showRotationMenu: Consumer<View>,
    private val showScaleMenu: Consumer<View>,
    private val showCutMenu: Consumer<View>,
    private val onItemsChanged: () -> Unit = {},
) {
    private val selectionObserver = ModelMenuSelectionObserver(fragment, onItemsChanged)

    fun createItems(): List<BedComposeMenuAction> {
        selectionObserver.readIfNeeded()
        val selection = selectionObserver.state.value
        val availability = modelSelectionAvailability(selection.modelLoaded, selection.selectedObject)
        return listOf(
            selectionAction(R.string.ModelContextResetRotation, R.drawable.menu_orientation_rotation_28, availability) {
                // Resolve the active object only after the operation reaches the GL queue.
                fragment.resetModelRotation {
                    SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.ModelContextResetRotationDone))
                }
            },
            selectionAction(R.string.ModelContextResetScale, R.drawable.arrow_up_right_corner_outline_24, availability) {
                // Resolve the active object only after the operation reaches the GL queue.
                fragment.resetModelScale {
                    SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.ModelContextResetScaleDone))
                }
            },
            BedComposeMenuAction(R.string.ModelUndo, R.drawable.vk_arrow_uturn_left_outline_24, {
                fragment.undoModelEdit()
            }),
            BedComposeMenuAction(R.string.ModelRedo, R.drawable.vk_arrow_uturn_right_outline_24, {
                fragment.redoModelEdit()
            }),
            selectionAction(R.string.MenuToolbarMove, R.drawable.menu_orientation_position_28, availability) {
                showPositionMenu.accept(it)
            },
            selectionAction(R.string.MenuToolbarRotate, R.drawable.menu_orientation_rotation_28, availability) {
                showRotationMenu.accept(it)
            },
            selectionAction(R.string.MenuTransformScale, R.drawable.menu_scale_28, availability) {
                showScaleMenu.accept(it)
            },
            selectionAction(R.string.ModelSimplify, R.drawable.vk_magic_wand_outline_24, availability, ::showSimplifyDialog),
            selectionAction(R.string.ModelSmoothMesh, R.drawable.vk_brush_outline_24, availability, ::showSmoothMeshDialog),
            selectionAction(R.string.MenuTransformCut, R.drawable.menu_transform_cut_or_mirror_28, availability) {
                showCutMenu.accept(it)
            },
            selectionAction(R.string.MenuToolbarVariableLayerHeight, R.drawable.print_layers_28, availability) {
                fragment.enterVariableLayerHeightMode()
            },
            paintAction(R.string.MenuToolbarSupportPaint, GLRenderer.PAINT_MODE_SUPPORT, availability, R.drawable.vk_cube_box_outline_24),
            paintAction(R.string.MenuToolbarSeamPaint, GLRenderer.PAINT_MODE_SEAM, availability, R.drawable.vk_brush_outline_24),
            paintAction(R.string.MenuToolbarFuzzyPaint, GLRenderer.PAINT_MODE_FUZZY, availability, R.drawable.stars_outline_28),
            paintAction(R.string.MenuToolbarColorPaint, GLRenderer.PAINT_MODE_COLOR, availability, R.drawable.vk_palette_outline_24),
            selectionAction(R.string.MenuToolbarEmboss, R.drawable.edit_outline_28, availability) {
                fragment.enterEmbossMode()
            },
            BedComposeMenuAction(R.string.MenuToolbarMeasure, R.drawable.wrench_outline_28, {
                fragment.enterMeasureMode()
            }),
        )
    }

    fun invalidateSelectionState() = selectionObserver.invalidate()

    private fun selectionAction(
        titleRes: Int,
        iconRes: Int,
        availability: ModelSelectionAvailability,
        action: (View) -> Unit,
    ): BedComposeMenuAction = BedComposeMenuAction(
        titleRes = titleRes,
        iconRes = iconRes,
        onClick = action,
        enabled = availability.hasSelectedObject,
    )

    private fun paintAction(titleRes: Int, mode: Int, availability: ModelSelectionAvailability, iconRes: Int) =
        selectionAction(titleRes, iconRes, availability) {
            fragment.enterPaintModeForCurrentSelection(mode)
        }

    private fun showSimplifyDialog(view: View) {
        val context = view.context
        fragment.captureSelectedObjectForSimplification { snapshot, triangles ->
            if (triangles <= 4) return@captureSelectedObjectForSimplification
            val ratios = floatArrayOf(0.75f, 0.50f, 0.25f)
            val targetCounts = ratios.map { max(4, (triangles * it).roundToInt()) }
            val choices = listOf(
                context.resources.getQuantityString(R.plurals.ModelSimplifyLight, targetCounts[0], targetCounts[0]),
                context.resources.getQuantityString(R.plurals.ModelSimplifyMedium, targetCounts[1], targetCounts[1]),
                context.resources.getQuantityString(R.plurals.ModelSimplifyStrong, targetCounts[2], targetCounts[2]),
                context.getString(R.string.ModelSimplifyCustom),
            )
            fragment.showWorkspaceComposeDialog { dismiss ->
                ModelSimplifyChoiceDialog(
                    title = context.getString(R.string.ModelSimplify),
                    currentTriangles = context.resources.getQuantityString(R.plurals.ModelSimplifyCurrent, triangles, triangles),
                    choices = choices,
                    startLabel = context.getString(R.string.ModelSimplifyStart),
                    cancelLabel = context.getString(android.R.string.cancel),
                    onDismiss = dismiss,
                    onStart = { choice ->
                        dismiss()
                        if (choice < ratios.size) {
                            fragment.simplifySelectedObject(snapshot, ratios[choice])
                        } else {
                            showCustomSimplifyDialog(view, snapshot, triangles)
                        }
                    },
                )
            }
        }
    }

    private fun showCustomSimplifyDialog(view: View, snapshot: ModelSelectionSnapshot, triangles: Int) {
        val context = view.context
        fragment.showWorkspaceComposeDialog { dismiss ->
            ModelSimplifyCustomDialog(
                title = context.getString(R.string.ModelSimplifyCustom),
                hint = context.getString(R.string.ModelSimplifyCustomHint),
                initialTriangleCount = triangles,
                minimumTriangleCount = 4,
                maximumTriangleCount = triangles - 1,
                startLabel = context.getString(R.string.ModelSimplifyStart),
                cancelLabel = context.getString(android.R.string.cancel),
                onDismiss = dismiss,
                onStart = { target ->
                    dismiss()
                    fragment.simplifySelectedObject(snapshot, target.toFloat() / triangles.toFloat())
                },
            )
        }
    }

    private fun showSmoothMeshDialog(view: View) {
        val context = view.context
        fragment.captureSelectedObjectForSimplification { snapshot, triangles ->
            if (triangles <= 0 || triangles > 80_000) {
                fragment.showWorkspaceMessageDialog(
                    context.getString(R.string.ModelSmoothMesh),
                    context.getString(R.string.ModelSmoothMeshTooLarge),
                    context.getString(android.R.string.ok),
                )
                return@captureSelectedObjectForSimplification
            }
            fragment.showWorkspaceComposeDialog { dismiss ->
                ModelSmoothMeshDialog(
                    triangleCount = triangles,
                    onDismiss = dismiss,
                    onStart = {
                        dismiss()
                        fragment.smoothSelectedObject(snapshot)
                    },
                )
            }
        }
    }
}

/** Shared callbacks let the selected-object editor collect transforms without a second action host. */
internal fun createTransformMenuItems(
    fragment: BedFragment,
    onItemsChanged: () -> Unit = {},
): TransformMenuItems = TransformMenuItems(
    fragment,
    Consumer<View> { fragment.showWorkspaceComposeTool(OrientationPositionMenu(fragment)) },
    Consumer<View> { fragment.showWorkspaceComposeTool(OrientationRotationMenu(fragment)) },
    Consumer<View> { fragment.showWorkspaceComposeTool(TransformScaleMenu(fragment)) },
    Consumer<View> { fragment.showWorkspaceComposeTool(TransformCutMenu(fragment)) },
    onItemsChanged,
)
