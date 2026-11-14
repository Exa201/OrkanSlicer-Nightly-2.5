package ru.ytkab0bp.slicebeam.components.bed_menu

import android.widget.Toast
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.SliceBeam
import ru.ytkab0bp.slicebeam.events.NeedSnackbarEvent
import ru.ytkab0bp.slicebeam.fragment.BedFragment
import ru.ytkab0bp.slicebeam.utils.ViewUtils
import ru.ytkab0bp.slicebeam.slic3r.BooleanOperation
import ru.ytkab0bp.slicebeam.render.ModelAlignmentAxis
import ru.ytkab0bp.slicebeam.render.ModelAlignmentCoordinateSystem
import ru.ytkab0bp.slicebeam.render.ModelSelectionSnapshot
import ru.ytkab0bp.slicebeam.render.MODEL_EDIT_STALE_SELECTION
import ru.ytkab0bp.slicebeam.render.MODEL_EDIT_UNSUPPORTED_BUILD_VOLUME
import ru.ytkab0bp.slicebeam.render.MODEL_EDIT_SURFACE_NEEDS_SIMPLIFICATION
import ru.ytkab0bp.slicebeam.render.MODEL_EDIT_OPERATION_FAILED
import ru.ytkab0bp.slicebeam.render.MODEL_EDIT_ROLLBACK_FAILED
import ru.ytkab0bp.slicebeam.slic3r.ObjectDistributionAxis

/** Model selection, placement and object operations; geometry editing stays in TransformMenuItems. */
internal class ModelMenuItems(
    private val fragment: BedFragment,
    private val onItemsChanged: () -> Unit,
) {
    private var flattenEnabled = false
    private val selectionObserver = ModelMenuSelectionObserver(fragment, onItemsChanged)

    /** Selection and model metadata are owned by GLRenderer and must be sampled on its thread. */
    fun invalidateSelectionState() {
        selectionObserver.invalidate()
    }

    fun createItems(): List<BedComposeMenuAction> {
        selectionObserver.readIfNeeded()
        val currentSelection = selectionObserver.state.value
        val availability = modelSelectionAvailability(currentSelection.modelLoaded, currentSelection.selectedObject)
        val objectActions = FileMenuObjectActions(fragment)
        val selectedCount = currentSelection.selectedObjectCount
        val objectCount = currentSelection.objectCount
        val glView = fragment.glView
        val renderer = glView?.renderer
        return listOf(
            BedComposeMenuAction(R.string.WorkspaceObjects, R.drawable.square_stack_up_outline_28, {
                objectActions.showObjectsDialog(it)
            }, enabled = availability.canArrange),
            BedComposeMenuAction(R.string.ModelMenuDuplicate, R.drawable.vk_copy_outline_24, {
                objectActions.showDuplicateDialog(it)
            }, enabled = availability.hasSelectedObject),
            BedComposeMenuAction(R.string.ModelMenuMoveToPlate, R.drawable.vk_folder_move_outline_24, {
                objectActions.showMoveToPlateDialog(it)
            }, enabled = availability.hasSelectedObject && fragment.plateCount > 1),
            BedComposeMenuAction(R.string.ModelMenuSplit, R.drawable.folder_simple_arrow_right_outline_28, {
                val activeGlView = fragment.glView ?: return@BedComposeMenuAction
                captureSelectionSnapshot(activeGlView) { snapshot ->
                    fragment.splitSelectedObjectInternal(snapshot)
                }
            }, enabled = availability.hasSelectedObject),
            BedComposeMenuAction(R.string.ModelMenuModifiers, R.drawable.sliders_outline_28, { anchor ->
                showModifierVolumesDialog(anchor, currentSelection.selectedObject)
            }, enabled = availability.hasSelectedObject),
            BedComposeMenuAction(R.string.ModelMenuObjectSettings, R.drawable.settings_outline_28, { anchor ->
                showObjectProcessSettingsDialog(anchor)
            }, enabled = availability.hasSelectedObject),
            BedComposeMenuAction(R.string.ModelMenuCenterOnBed, R.drawable.vk_scan_viewfinder_outline_24, { anchor ->
                val activeGlView = fragment.glView ?: return@BedComposeMenuAction
                captureSelectionSnapshot(activeGlView) { snapshot ->
                    activeGlView.centerSelectedOnBed(snapshot) { result ->
                        when (result) {
                            1 -> {
                                fragment.updateModel()
                                Toast.makeText(anchor.context, R.string.ModelContextCentered, Toast.LENGTH_SHORT).show()
                            }
                            MODEL_EDIT_STALE_SELECTION ->
                                SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.ModelSelectionChanged))
                        }
                    }
                }
            }, enabled = availability.hasSelectedObject),
            BedComposeMenuAction(R.string.ModelScaleToBuildVolume, R.drawable.arrow_up_right_corner_outline_24, { anchor ->
                val activeGlView = fragment.glView ?: return@BedComposeMenuAction
                captureSelectionSnapshot(activeGlView) { snapshot ->
                    activeGlView.scaleSelectedObjectToBuildVolume(snapshot) { result ->
                        when (result) {
                            1 -> {
                                fragment.updateModel()
                                Toast.makeText(anchor.context, R.string.ModelScaleToBuildVolumeDone, Toast.LENGTH_SHORT).show()
                            }
                            MODEL_EDIT_STALE_SELECTION ->
                                SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.ModelSelectionChanged))
                            MODEL_EDIT_UNSUPPORTED_BUILD_VOLUME ->
                                SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.ModelScaleToBuildVolumeUnsupported))
                        }
                    }
                }
            }, enabled = availability.hasSelectedObject && renderer?.bed?.supportsScaleToFit() == true),
            BedComposeMenuAction(R.string.ModelMenuPlaceGroupOnBed, R.drawable.vk_cube_box_outline_24, { anchor ->
                val activeGlView = fragment.glView ?: return@BedComposeMenuAction
                captureSelectionSnapshot(activeGlView) { snapshot ->
                    activeGlView.placeSelectedObjectsOnBed(snapshot) { result ->
                        when (result) {
                            1 -> {
                                fragment.updateModel()
                                Toast.makeText(anchor.context, R.string.ModelMenuPlaceGroupOnBedDone, Toast.LENGTH_SHORT).show()
                            }
                            MODEL_EDIT_STALE_SELECTION ->
                                SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.ModelSelectionChanged))
                            else -> Toast.makeText(
                                anchor.context,
                                R.string.ModelMenuPlaceGroupOnBedFailed,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                }
            }, enabled = availability.hasSelectedObject),
            BedComposeMenuAction(R.string.ModelMenuPlaceOnObject, R.drawable.vk_cube_box_outline_24, { anchor ->
                showPlaceOnObjectDialog(anchor)
            }, enabled = availability.hasSelectedObject && objectCount > selectedCount),
            BedComposeMenuAction(R.string.ModelMenuPlaceOnSurface, R.drawable.vk_scan_viewfinder_outline_24, { anchor ->
                showPlaceOnSurfaceDialog(anchor)
            }, enabled = availability.hasSelectedObject && objectCount > selectedCount),
            BedComposeMenuAction(R.string.ModelMenuAlignSelected, R.drawable.vk_grid_layout_outline_28, { anchor ->
                showAlignmentDialog(anchor)
            }, enabled = selectedCount >= 2),
            BedComposeMenuAction(R.string.ModelMenuAlignPrimaryAxes, R.drawable.hashtag_outline_28, { anchor ->
                showAlignmentDialog(anchor, ModelAlignmentCoordinateSystem.PRIMARY_OBJECT)
            }, enabled = selectedCount >= 2),
            BedComposeMenuAction(R.string.ModelMenuDistributeSelected, R.drawable.vk_grid_layout_outline_28, { anchor ->
                showDistributionDialog(anchor)
            }, enabled = selectedCount >= 3),
            BedComposeMenuAction(R.string.ModelMenuCombineSelected, R.drawable.square_stack_up_outline_28, { anchor ->
                val activeGlView = glView ?: return@BedComposeMenuAction
                captureSelectionSnapshot(activeGlView) { snapshot ->
                    if (snapshot.selectedIndices.size < 2) return@captureSelectionSnapshot
                    fragment.showWorkspaceMessageDialog(
                        anchor.context.getString(R.string.ModelMenuCombineSelected),
                        anchor.context.getString(R.string.ModelMenuCombineWarning),
                        anchor.context.getString(R.string.ModelMenuCombineSelected),
                        anchor.context.getString(android.R.string.cancel),
                    ) {
                        activeGlView.combineSelectedObjectsAsParts(snapshot) { result ->
                            when {
                                result >= 0 -> {
                                    fragment.updateModel()
                                    SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.ModelMenuCombineDone))
                                }
                                result == MODEL_EDIT_STALE_SELECTION ->
                                    SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.ModelSelectionChanged))
                                result == MODEL_EDIT_OPERATION_FAILED ->
                                    SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.ModelMenuCombineFailed))
                                else -> SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.ModelMenuCombineUnsupported))
                            }
                        }
                    }
                }
            }, enabled = selectedCount >= 2),
            BedComposeMenuAction(R.string.ModelMenuBoolean, R.drawable.vk_cube_box_outline_24, { anchor ->
                showBooleanDialog(anchor)
            }, enabled = selectedCount >= 2),
            BedComposeMenuAction(R.string.ModelMenuFillBed, R.drawable.picture_stack_outline_28, { anchor ->
                fragment.glView!!.fillBedWithSelectedModel { addedCopies ->
                    // A concurrent Arrange invalidates an in-flight copy trial. Its old
                    // callback is not a capacity result and must not show the no-room toast.
                    if (addedCopies < 0) return@fillBedWithSelectedModel
                    if (addedCopies > 0) fragment.updateModel()
                    Toast.makeText(
                        anchor.context,
                        if (addedCopies > 0) anchor.resources.getQuantityString(
                            R.plurals.ModelContextFillBedFinished, addedCopies, addedCopies,
                        ) else anchor.context.getString(R.string.ModelContextFillBedNoRoom),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }, enabled = availability.hasSelectedObject),
            BedComposeMenuAction(R.string.ModelMenuArrange, R.drawable.grid_layout_outline_28, { anchor ->
                val activeGlView = glView ?: return@BedComposeMenuAction
                activeGlView.arrange { arranged ->
                    if (!arranged) {
                        Toast.makeText(anchor.context, R.string.ModelContextArrangeFailed, Toast.LENGTH_LONG).show()
                        return@arrange
                    }
                    activeGlView.queueEvent {
                        if (renderer?.invalidateFlattenMode() == true) activeGlView.requestRender()
                    }
                    fragment.updateModel()
                    SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.MenuOrientationArrangeFinished))
                }
            }, enabled = availability.canArrange),
            BedComposeMenuAction(R.string.ModelMenuCreatePrimitive, R.drawable.vk_cube_box_outline_24, { anchor ->
                val activeGlView = fragment.glView ?: return@BedComposeMenuAction
                fragment.showWorkspaceComposeDialog { dismiss ->
                    PrimitiveModelDialog(
                        onDismiss = dismiss,
                        onCreate = { type, a, b, c, name ->
                            dismiss()
                            activeGlView.queueEvent {
                                val activeModel = activeGlView.renderer.model ?: return@queueEvent
                                val bed = activeGlView.renderer.bed ?: return@queueEvent
                                val min = bed.getVolumeMin() ?: return@queueEvent
                                val max = bed.getVolumeMax() ?: return@queueEvent
                                val created = activeModel.createPrimitive(
                                    type, a, b, c, name,
                                    centerX = (min.x + max.x) / 2.0,
                                    centerY = (min.y + max.y) / 2.0,
                                )
                                if (created < 0) return@queueEvent
                                activeGlView.renderer.resetGlModels()
                                activeGlView.renderer.setSelectedObject(created)
                                activeGlView.requestRender()
                                fragment.updateModelOnGlThread()
                            }
                        },
                    )
                }
            }, enabled = availability.canArrange),
            BedComposeMenuAction(R.string.ModelMenuOrientAll, R.drawable.menu_orientation_auto_28, {
                val activeGlView = fragment.glView ?: return@BedComposeMenuAction
                activeGlView.autoOrientAllObjects { orientedCount ->
                    if (orientedCount > 0) {
                        fragment.updateModel()
                        SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.MenuOrientAllObjectsDone))
                    }
                }
            }, enabled = availability.canArrange),
            BedComposeMenuAction(R.string.TextModelCreateSeparate, R.drawable.edit_outline_28, {
                fragment.enterEmbossMode(standaloneText = true)
            }, enabled = availability.canArrange),
            BedComposeMenuAction(R.string.ModelMenuAutoOrientSelected, R.drawable.menu_orientation_auto_28, {
                val activeGlView = fragment.glView ?: return@BedComposeMenuAction
                captureSelectionSnapshot(activeGlView) { snapshot ->
                    activeGlView.autoOrientSelectedObjects(snapshot) { orientedCount ->
                        when {
                            orientedCount > 0 -> {
                                fragment.updateModel()
                                SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(
                                    if (orientedCount == 1) R.string.MenuOrientationAutoOrientDone
                                    else R.string.ModelMenuAutoOrientGroupDone,
                                ))
                            }
                            orientedCount == MODEL_EDIT_STALE_SELECTION ->
                                SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.ModelSelectionChanged))
                        }
                    }
                }
            }, enabled = availability.hasSelectedObject),
            BedComposeMenuAction(R.string.ModelMenuFlatten, R.drawable.menu_orientation_flatten_28, {
                flattenEnabled = !flattenEnabled
                val flatten = flattenEnabled
                val activeGlView = fragment.glView ?: return@BedComposeMenuAction
                activeGlView.queueEvent {
                    val activeRenderer = activeGlView.renderer
                    val model = activeRenderer.model
                    if (flatten && (model == null || activeRenderer.selectedObject !in 0 until model.objectsCount)) {
                        ViewUtils.postOnMainThread {
                            flattenEnabled = false
                            onItemsChanged()
                        }
                        return@queueEvent
                    }
                    activeRenderer.setInFlattenMode(flatten)
                    activeGlView.requestRender()
                }
                onItemsChanged()
            }, selected = flattenEnabled, toggleable = true, enabled = availability.hasSelectedObject),
            BedComposeMenuAction(R.string.ModelMenuDeleteSelected, R.drawable.vk_delete_outline_24, { anchor ->
                val glView = fragment.glView ?: return@BedComposeMenuAction
                captureSelectionSnapshot(glView) { snapshot ->
                    fragment.showWorkspaceMessageDialog(
                        anchor.context.getString(R.string.ModelMenuDeleteSelected),
                        anchor.context.getString(R.string.ModelDeleteConfirm),
                        anchor.context.getString(R.string.ModelMenuDeleteSelected),
                        anchor.context.getString(android.R.string.cancel),
                    ) {
                        glView.deleteSelectedModelsIfCurrent(snapshot) { removed ->
                            if (removed == MODEL_EDIT_STALE_SELECTION) {
                                SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.ModelSelectionChanged))
                            } else if (removed > 0) {
                                fragment.updateModel()
                            }
                        }
                    }
                }
            }, enabled = availability.hasSelectedObject),
            BedComposeMenuAction(R.string.ModelMenuSelectAll, R.drawable.square_stack_up_outline_28, {
                // Selection is renderer UI state, not a model edit. Recording it through
                // updateModel() would add an identical model snapshot and consume the next Undo.
                fragment.glView!!.selectAllModels {}
            }, enabled = availability.canArrange),
            BedComposeMenuAction(R.string.ModelMenuDeleteAll, R.drawable.vk_delete_outline_24, { anchor ->
                val activeGlView = glView ?: return@BedComposeMenuAction
                captureSelectionSnapshot(activeGlView, allowEmptySelection = true) { snapshot ->
                    fragment.showWorkspaceMessageDialog(
                        anchor.context.getString(R.string.ModelMenuDeleteAll),
                        anchor.context.getString(R.string.ModelDeleteAllConfirm),
                        anchor.context.getString(R.string.ModelMenuDeleteAll),
                        anchor.context.getString(android.R.string.cancel),
                    ) {
                        activeGlView.deleteAllModelsIfCurrent(snapshot) { removed ->
                            if (removed == MODEL_EDIT_STALE_SELECTION) {
                                SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.ModelSelectionChanged))
                            } else if (removed > 0) {
                                fragment.updateModel()
                            }
                        }
                    }
                }
            }, enabled = availability.canArrange),
        )
    }

    fun resetFlattenMode() {
        flattenEnabled = false
    }

    private fun showPlaceOnObjectDialog(anchor: android.view.View) {
        val glView = fragment.glView ?: return
        captureTargetChoices { snapshot, targetIndices, names ->
            val choices = names.mapIndexed { index, name ->
                name ?: anchor.context.getString(R.string.MenuFileObjectNumber, (targetIndices[index] + 1).toString())
            }
            fragment.showWorkspaceComposeDialog { dismiss ->
                ModelChoiceDialog(
                    title = anchor.context.getString(R.string.ModelMenuPlaceOnObject),
                    cancelLabel = anchor.context.getString(android.R.string.cancel),
                    choices = choices,
                    onDismiss = dismiss,
                    onSelect = { choice ->
                        dismiss()
                        val target = targetIndices.getOrNull(choice) ?: return@ModelChoiceDialog
                        glView.placeSelectedObjectsOnObject(target, snapshot) { result ->
                            when {
                                result >= 0 -> {
                                    fragment.updateModel()
                                    SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.ModelMenuPlaceOnObjectDone))
                                }
                                result == MODEL_EDIT_STALE_SELECTION ->
                                    SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.ModelSelectionChanged))
                                result == MODEL_EDIT_OPERATION_FAILED ->
                                    SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.ModelMenuPlaceFailed))
                                result == MODEL_EDIT_ROLLBACK_FAILED ->
                                    SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.ModelMenuPlaceRollbackFailed))
                                else -> SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.ModelMenuCombineUnsupported))
                            }
                        }
                    },
                )
            }
        }
    }

    private fun showAlignmentDialog(
        anchor: android.view.View,
        coordinateSystem: ModelAlignmentCoordinateSystem = ModelAlignmentCoordinateSystem.WORLD,
    ) {
        val glView = fragment.glView ?: return
        val labels = listOf(
            anchor.context.getString(R.string.ModelMenuAlignMinX),
            anchor.context.getString(R.string.ModelMenuAlignCenterX),
            anchor.context.getString(R.string.ModelMenuAlignMaxX),
            anchor.context.getString(R.string.ModelMenuAlignMinY),
            anchor.context.getString(R.string.ModelMenuAlignCenterY),
            anchor.context.getString(R.string.ModelMenuAlignMaxY),
            anchor.context.getString(R.string.ModelMenuAlignBaseZ),
            anchor.context.getString(R.string.ModelMenuAlignCenterZ),
            anchor.context.getString(R.string.ModelMenuAlignMaxZ),
        )
        captureSelectionSnapshot(glView) { snapshot ->
            fragment.showWorkspaceComposeDialog { dismiss ->
                ModelChoiceDialog(
                    title = anchor.context.getString(
                        if (coordinateSystem == ModelAlignmentCoordinateSystem.PRIMARY_OBJECT) {
                            R.string.ModelMenuAlignPrimaryAxes
                        } else {
                            R.string.ModelMenuAlignSelected
                        },
                    ),
                    cancelLabel = anchor.context.getString(android.R.string.cancel),
                    choices = labels,
                    onDismiss = dismiss,
                    onSelect = { choice ->
                        dismiss()
                        val axis = when (choice) {
                            0 -> ModelAlignmentAxis.MIN_X
                            1 -> ModelAlignmentAxis.CENTER_X
                            2 -> ModelAlignmentAxis.MAX_X
                            3 -> ModelAlignmentAxis.MIN_Y
                            4 -> ModelAlignmentAxis.CENTER_Y
                            5 -> ModelAlignmentAxis.MAX_Y
                            6 -> ModelAlignmentAxis.BASE_Z
                            7 -> ModelAlignmentAxis.CENTER_Z
                            8 -> ModelAlignmentAxis.MAX_Z
                            else -> return@ModelChoiceDialog
                        }
                        glView.alignSelectedObjects(axis, snapshot, coordinateSystem) { result ->
                            when (result) {
                                1 -> {
                                    fragment.updateModel()
                                    SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.ModelMenuAlignDone))
                                }
                                MODEL_EDIT_STALE_SELECTION ->
                                    SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.ModelSelectionChanged))
                            }
                        }
                    },
                )
            }
        }
    }

    private fun showDistributionDialog(anchor: android.view.View) {
        val glView = fragment.glView ?: return
        val labels = listOf(
            anchor.context.getString(R.string.ModelMenuDistributeX),
            anchor.context.getString(R.string.ModelMenuDistributeY),
            anchor.context.getString(R.string.ModelMenuDistributeZ),
        )
        captureSelectionSnapshot(glView) { snapshot ->
            fragment.showWorkspaceComposeDialog { dismiss ->
                ModelChoiceDialog(
                    title = anchor.context.getString(R.string.ModelMenuDistributeSelected),
                    hint = anchor.context.getString(R.string.ModelMenuDistributeHint),
                    cancelLabel = anchor.context.getString(android.R.string.cancel),
                    choices = labels,
                    onDismiss = dismiss,
                    onSelect = { choice ->
                        dismiss()
                        val axis = when (choice) {
                            0 -> ObjectDistributionAxis.X
                            1 -> ObjectDistributionAxis.Y
                            2 -> ObjectDistributionAxis.Z
                            else -> return@ModelChoiceDialog
                        }
                        glView.distributeSelectedObjects(axis, snapshot) { result ->
                            when (result) {
                                1 -> {
                                    fragment.updateModel()
                                    SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.ModelMenuDistributeDone))
                                }
                                MODEL_EDIT_STALE_SELECTION ->
                                    SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.ModelSelectionChanged))
                                else -> SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.ModelMenuDistributeNoSpace))
                            }
                        }
                    },
                )
            }
        }
    }

    private fun showPlaceOnSurfaceDialog(anchor: android.view.View) {
        val glView = fragment.glView ?: return
        captureTargetChoices { snapshot, targetIndices, names ->
            val choices = names.mapIndexed { index, name ->
                name ?: anchor.context.getString(R.string.MenuFileObjectNumber, (targetIndices[index] + 1).toString())
            }
            fragment.showWorkspaceComposeDialog { dismiss ->
                ModelChoiceDialog(
                    title = anchor.context.getString(R.string.ModelMenuPlaceOnSurface),
                    cancelLabel = anchor.context.getString(android.R.string.cancel),
                    choices = choices,
                    onDismiss = dismiss,
                    onSelect = { choice ->
                        dismiss()
                        val target = targetIndices.getOrNull(choice) ?: return@ModelChoiceDialog
                        glView.beginSurfacePlacement(target, snapshot) { result ->
                            SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(
                                when (result) {
                                    1 -> R.string.ModelSurfacePlacementInstruction
                                    MODEL_EDIT_STALE_SELECTION -> R.string.ModelSelectionChanged
                                    MODEL_EDIT_SURFACE_NEEDS_SIMPLIFICATION -> R.string.ModelSurfacePlacementNeedsSimplification
                                    else -> R.string.ModelSurfacePlacementTargetUnavailable
                                },
                            ))
                        }
                    },
                )
            }
        }
    }

    private fun captureSelectionSnapshot(
        glView: ru.ytkab0bp.slicebeam.view.GLView,
        allowEmptySelection: Boolean = false,
        onCaptured: (ModelSelectionSnapshot) -> Unit,
    ) {
        glView.queueEvent {
            val snapshot = glView.renderer.captureSelectionSnapshot(allowEmptySelection) ?: return@queueEvent
            ViewUtils.postOnMainThread {
                if (fragment.canShowWorkspaceComposeDialog()) onCaptured(snapshot)
            }
        }
    }

    private fun showObjectProcessSettingsDialog(anchor: android.view.View) {
        val activeGlView = fragment.glView ?: return
        activeGlView.queueEvent {
            val activeRenderer = activeGlView.renderer
            val snapshot = activeRenderer.captureSelectionSnapshot() ?: return@queueEvent
            val model = snapshot.model
            val objectIndex = snapshot.primaryObject
            if (objectIndex !in 0 until model.objectsCount) return@queueEvent
            val revision = model.stateRevision
            val values = listOf("wall_loops", "sparse_infill_density", "top_shell_layers", "sparse_infill_pattern", "enable_support", "support_type", "top_surface_pattern", "bottom_surface_pattern", "seam_position", "raft_layers")
                .associateWith { model.getObjectConfigOption(objectIndex, it).orEmpty() }
            val name = model.getObjectName(objectIndex)
                    ?: anchor.context.getString(R.string.MenuFileObjectNumber, (objectIndex + 1).toString())
            ViewUtils.postOnMainThread {
                if (!fragment.canShowWorkspaceComposeDialog()) return@postOnMainThread
                fragment.showWorkspaceComposeDialog { dismiss ->
                    ModelObjectProcessOverridesDialog(
                        objectName = name,
                        initialValues = values,
                        onDismiss = dismiss,
                        onApply = { overrides ->
                            dismiss()
                            activeGlView.queueEvent {
                                val renderer = activeGlView.renderer
                                val result = if (renderer.model === model && model.stateRevision == revision &&
                                    renderer.isSelectionBindingCurrent(snapshot)) {
                                    model.setObjectConfigOptions(objectIndex, overrides)
                                } else -1
                                if (result == 1) fragment.updateModelOnGlThread()
                                ViewUtils.postOnMainThread {
                                    SliceBeam.EVENT_BUS.fireEvent(
                                        NeedSnackbarEvent(
                                            when (result) {
                                                1, 0 -> R.string.ModelObjectProcessSettingsSaved
                                                else -> R.string.ModelObjectProcessSettingsStale
                                            },
                                        ),
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    private fun captureTargetChoices(
        onCaptured: (ModelSelectionSnapshot, List<Int>, List<String?>) -> Unit,
    ) {
        val glView = fragment.glView ?: return
        glView.queueEvent {
            val renderer = glView.renderer
            val snapshot = renderer.captureSelectionSnapshot() ?: return@queueEvent
            val targetIndices = (0 until snapshot.objectCount).filter { it !in snapshot.selectedIndices }
            val names = targetIndices.map { snapshot.model.getObjectName(it) }
            ViewUtils.postOnMainThread {
                if (fragment.canShowWorkspaceComposeDialog()) onCaptured(snapshot, targetIndices, names)
            }
        }
    }

    private fun showBooleanDialog(anchor: android.view.View) {
        val glView = fragment.glView ?: return
        glView.queueEvent {
            val renderer = glView.renderer
            val model = renderer.model ?: return@queueEvent
            val snapshot = renderer.captureSelectionSnapshot() ?: return@queueEvent
            val selected = renderer.selectedObjectIndices()
            if (selected.size < 2) return@queueEvent
            val targetIndex = renderer.selectedObject.takeIf { it in selected } ?: selected[0]
            val orderedIndices = intArrayOf(targetIndex) + selected.filter { it != targetIndex }
            val targetName = model.getObjectName(targetIndex)
            val toolNames = orderedIndices.drop(1).map { index -> index to model.getObjectName(index) }
            val sourceRevision = model.geometryRevision
            val sourceObjectCount = model.objectsCount

            ViewUtils.postOnMainThread {
                if (!fragment.canShowWorkspaceComposeDialog()) return@postOnMainThread
                val displayTargetName = targetName
                    ?: anchor.context.getString(R.string.MenuFileObjectNumber, (targetIndex + 1).toString())
                val displayToolNames = toolNames.map { (index, name) ->
                    name ?: anchor.context.getString(R.string.MenuFileObjectNumber, (index + 1).toString())
                }
                fragment.showWorkspaceComposeDialog { dismiss ->
                    ModelBooleanDialog(
                        targetName = displayTargetName,
                        toolNames = displayToolNames,
                        onDismiss = dismiss,
                        onChoose = { operation, placeOnSurface ->
                            dismiss()
                            if (placeOnSurface) {
                                glView.beginSurfaceBoolean(targetIndex, operation, snapshot) { started ->
                                    val message = when (started) {
                                        1 -> R.string.ModelBooleanSurfaceInstruction
                                        MODEL_EDIT_STALE_SELECTION -> R.string.ModelBooleanSelectionChanged
                                        MODEL_EDIT_SURFACE_NEEDS_SIMPLIFICATION -> R.string.ModelSurfacePlacementNeedsSimplification
                                        else -> R.string.ModelBooleanSurfaceUnavailable
                                    }
                                    SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(message))
                                }
                                return@ModelBooleanDialog
                            }
                            glView.queueEvent {
                                val currentRenderer = glView.renderer
                                val currentModel = currentRenderer.model
                                val selectionUnchanged = currentModel != null && currentModel === model &&
                                    currentModel.objectsCount == sourceObjectCount &&
                                    currentModel.geometryRevision == sourceRevision &&
                                    currentRenderer.selectedObject == targetIndex &&
                                    currentRenderer.selectedObjectIndices().contentEquals(selected)
                                if (!selectionUnchanged) {
                                    ViewUtils.postOnMainThread {
                                        SliceBeam.EVENT_BUS.fireEvent(
                                            NeedSnackbarEvent(R.string.ModelBooleanSelectionChanged),
                                        )
                                    }
                                    return@queueEvent
                                }
                                val result = currentModel.booleanObjects(orderedIndices, operation)
                                if (result >= 0) {
                                    currentRenderer.resetGlModels()
                                    currentRenderer.setSelectedObject(result)
                                    glView.requestRender()
                                    ViewUtils.postOnMainThread {
                                        fragment.updateModel()
                                        SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.ModelBooleanDone))
                                    }
                                } else {
                                    ViewUtils.postOnMainThread {
                                        val specificMessage = ModelBooleanFailureMessage.resourceFor(result)
                                        val message = specificMessage ?: R.string.ModelBooleanFailed
                                        SliceBeam.EVENT_BUS.fireEvent(
                                            if (specificMessage == null) {
                                                NeedSnackbarEvent(message, displayTargetName, displayToolNames.joinToString())
                                            } else {
                                                NeedSnackbarEvent(message)
                                            },
                                        )
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    private fun showModifierVolumesDialog(anchor: android.view.View, objectIndex: Int) {
        val glView = fragment.glView ?: return
        glView.queueEvent {
            val renderer = glView.renderer
            val model = renderer.model
            if (model == null || objectIndex !in 0 until model.objectsCount || renderer.selectedObject != objectIndex) return@queueEvent
            val modelRevision = model.geometryRevision
            val objectCount = model.objectsCount
            val existing = model.getModifierVolumeIndices(objectIndex).asSequence()
                .mapNotNull { model.getModifierVolumeBoxState(objectIndex, it) }.toList()
            ViewUtils.postOnMainThread {
                if (!fragment.canShowWorkspaceComposeDialog()) return@postOnMainThread
                fragment.showWorkspaceComposeDialog { dismiss ->
                    ModelModifierVolumesDialog(
                        existing = existing,
                        onDismiss = dismiss,
                        onAdd = { type, position, size ->
                            dismiss()
                            glView.queueEvent {
                                val activeRenderer = glView.renderer
                                val activeModel = activeRenderer.model
                                if (activeModel !== model || activeModel.objectsCount != objectCount ||
                                    activeModel.geometryRevision != modelRevision || activeRenderer.selectedObject != objectIndex) return@queueEvent
                                val volumeIndex = activeModel.addModifierBox(
                                    objectIndex, type, position[0], position[1], position[2], size[0], size[1], size[2],
                                )
                                if (volumeIndex >= 0) {
                                    activeRenderer.resetGlModels()
                                    activeRenderer.setSelectedObject(objectIndex, false)
                                    glView.requestRender()
                                    fragment.updateModelOnGlThread()
                                    ViewUtils.postOnMainThread {
                                        Toast.makeText(anchor.context, R.string.ModelModifierAdded, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        onUpdate = { volumeIndex, position, size ->
                            dismiss()
                            glView.queueEvent {
                                val activeRenderer = glView.renderer
                                val activeModel = activeRenderer.model
                                if (activeModel !== model || activeModel.objectsCount != objectCount ||
                                    activeModel.geometryRevision != modelRevision || activeRenderer.selectedObject != objectIndex) return@queueEvent
                                if (activeModel.updateModifierBox(
                                        objectIndex, volumeIndex, position[0], position[1], position[2], size[0], size[1], size[2],
                                    )) {
                                    activeRenderer.resetGlModels()
                                    glView.requestRender()
                                    fragment.updateModelOnGlThread()
                                }
                            }
                        },
                        onDelete = { volumeIndex ->
                            dismiss()
                            glView.queueEvent {
                                val activeRenderer = glView.renderer
                                val activeModel = activeRenderer.model
                                if (activeModel !== model || activeModel.objectsCount != objectCount ||
                                    activeModel.geometryRevision != modelRevision || activeRenderer.selectedObject != objectIndex) return@queueEvent
                                if (activeModel.deleteModifierVolume(objectIndex, volumeIndex)) {
                                    activeRenderer.resetGlModels()
                                    activeRenderer.setSelectedObject(objectIndex, false)
                                    glView.requestRender()
                                    fragment.updateModelOnGlThread()
                                    ViewUtils.postOnMainThread {
                                        Toast.makeText(anchor.context, R.string.ModelModifierRemoved, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}
