package ru.ytkab0bp.slicebeam.components.bed_menu

import android.view.View
import android.widget.Toast
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.SliceBeam
import ru.ytkab0bp.slicebeam.events.SelectedObjectChangedEvent
import ru.ytkab0bp.slicebeam.events.NeedSnackbarEvent
import ru.ytkab0bp.slicebeam.fragment.BedFragment
import ru.ytkab0bp.slicebeam.render.MODEL_EDIT_STALE_SELECTION
import ru.ytkab0bp.slicebeam.render.ModelSelectionSnapshot
import ru.ytkab0bp.slicebeam.utils.FilamentSlot
import ru.ytkab0bp.slicebeam.utils.Prefs
import ru.ytkab0bp.slicebeam.utils.ViewUtils
import java.util.Locale

/** Kotlin-owned object selection, duplication, filament and plate actions for the live File menu. */
class FileMenuObjectActions(private val fragment: BedFragment) {
    private fun captureSelection(
        view: View,
        allowEmpty: Boolean = false,
        onUnavailable: () -> Unit = {},
        onCaptured: (ru.ytkab0bp.slicebeam.view.GLView, ModelSelectionSnapshot) -> Unit,
    ) {
        val viewport = fragment.glView ?: return
        viewport.queueEvent {
            val snapshot = viewport.renderer.captureSelectionSnapshot(allowEmpty)
            if (snapshot == null) {
                ViewUtils.postOnMainThread(onUnavailable)
                return@queueEvent
            }
            ViewUtils.postOnMainThread {
                if (fragment.glView === viewport && fragment.canShowWorkspaceComposeDialog()) {
                    onCaptured(viewport, snapshot)
                }
            }
        }
    }

    private fun reportStaleSelection() {
        SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.ModelSelectionChanged))
    }

    fun showCopyMoveDialog(view: View) {
        val context = view.context
        captureSelection(view) { viewport, snapshot ->
            fragment.showWorkspaceComposeDialog { dismiss ->
                ModelChoiceDialog(
                    title = context.getString(R.string.MenuFileCopyMove),
                    cancelLabel = context.getString(android.R.string.cancel),
                    choices = listOf(
                        context.getString(R.string.MenuFileDuplicateCount),
                        context.getString(R.string.MenuFileMoveToPlate),
                    ),
                    onDismiss = dismiss,
                    onSelect = { which ->
                        dismiss()
                        if (which == 0) showDuplicateDialog(view, viewport, snapshot)
                        else showMoveToPlateDialog(view, snapshot)
                    },
                )
            }
        }
    }

    fun showObjectsDialog(view: View) {
        val context = view.context
        val filamentSlots = Prefs.getFilamentSlots().toList()
        captureSelection(
            view,
            allowEmpty = true,
            onUnavailable = { Toast.makeText(context, R.string.MenuFileObjectsEmpty, Toast.LENGTH_SHORT).show() },
        ) { viewport, snapshot ->
            val rows = (0 until snapshot.objectCount).map { index ->
                val name = snapshot.model.getObjectName(index)
                    ?: context.getString(R.string.MenuFileObjectNumber, (index + 1).toString())
                val tool = (snapshot.model.getExtruder(index) - 1).coerceAtLeast(0)
                val slot = filamentSlots.getOrNull(tool)
                val parts = (0 until snapshot.model.getObjectPartCount(index)).map { partIndex ->
                    val partTool = (snapshot.model.getObjectPartExtruder(index, partIndex) - 1).coerceAtLeast(0)
                    val partSlot = filamentSlots.getOrNull(partTool)
                    ModelObjectPartRow(
                        label = snapshot.model.getObjectPartName(index, partIndex)
                            ?: context.getString(R.string.ModelObjectPartNumber, (partIndex + 1).toString()),
                        tool = partTool,
                        material = partSlot?.type?.takeIf(String::isNotBlank) ?: context.getString(R.string.FilamentType),
                        color = partSlot?.color ?: 0xFF888888.toInt(),
                    )
                }
                ModelObjectRow(
                    name = name,
                    tool = tool,
                    material = slot?.type?.takeIf(String::isNotBlank) ?: context.getString(R.string.FilamentType),
                    color = slot?.color ?: 0xFF888888.toInt(),
                    selected = index in snapshot.selectedIndices,
                    assignEnabled = filamentSlots.isNotEmpty(),
                    parts = parts,
                )
            }
            if (rows.isEmpty()) {
                return@captureSelection
            }
            fragment.showWorkspaceComposeDialog { dismiss ->
                ModelObjectsDialog(
                    title = context.getString(R.string.MenuFileObjects),
                    cancelLabel = context.getString(android.R.string.cancel),
                    assignLabel = context.getString(R.string.ObjectActionAssignFilamentShort),
                    rows = rows,
                    onDismiss = dismiss,
                    onSelect = { index ->
                        dismiss()
                        viewport.selectModelIfCurrent(index, snapshot) { result ->
                            if (result == MODEL_EDIT_STALE_SELECTION) reportStaleSelection()
                            else if (result == 1) SliceBeam.EVENT_BUS.fireEvent(SelectedObjectChangedEvent())
                        }
                    },
                    onAssign = { index ->
                        dismiss()
                        showObjectFilamentSlotDialog(view, index, snapshot)
                    },
                    onAssignPart = { index, partIndex ->
                        dismiss()
                        showObjectPartFilamentSlotDialog(view, index, partIndex, snapshot)
                    },
                    onEditPartProcessSettings = { index, partIndex ->
                        dismiss()
                        showObjectPartProcessSettingsDialog(view, index, partIndex, snapshot)
                    },
                    onTransformPart = { index, partIndex ->
                        dismiss()
                        showObjectPartTransformDialog(view, index, partIndex, snapshot)
                    },
                    onEditPart = { index, partIndex ->
                        dismiss()
                        viewport.splitObjectPartIfCurrent(index, partIndex, snapshot) { result ->
                            when {
                                result == MODEL_EDIT_STALE_SELECTION -> reportStaleSelection()
                                result > 0 -> {
                                    fragment.updateModel()
                                    SliceBeam.EVENT_BUS.fireEvent(SelectedObjectChangedEvent())
                                    SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.ModelObjectPartSeparated))
                                }
                                else -> SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.MenuToolbarSplitNothing))
                            }
                        }
                    },
                    onApplySelection = { indices ->
                        dismiss()
                        viewport.selectModelsIfCurrent(indices, snapshot) { result ->
                            if (result == MODEL_EDIT_STALE_SELECTION) reportStaleSelection()
                            // Applying a selection changes renderer UI state only. The
                            // SelectedObjectChangedEvent refreshes the model menu; don't
                            // append an identical model snapshot to Undo history.
                        }
                    },
                )
            }
        }
    }

    private fun showObjectPartTransformDialog(
        view: View,
        objectIndex: Int,
        partIndex: Int,
        expectedSelection: ModelSelectionSnapshot,
    ) {
        val context = view.context
        fragment.showWorkspaceComposeDialog { dismiss ->
            ModelObjectPartTransformDialog(
                cancelLabel = context.getString(android.R.string.cancel),
                onDismiss = dismiss,
                onApply = { dx, dy, dz, rx, ry, rz, sx, sy, sz ->
                    dismiss()
                    val viewport = fragment.glView ?: return@ModelObjectPartTransformDialog
                    viewport.transformObjectPartIfCurrent(
                        objectIndex, partIndex, dx, dy, dz, sx, sy, sz, rx, ry, rz, expectedSelection,
                    ) { result ->
                        when (result) {
                            MODEL_EDIT_STALE_SELECTION -> reportStaleSelection()
                            1 -> {
                                fragment.updateModel()
                                SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.ModelObjectPartTransformApply))
                            }
                            else -> SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.ModelObjectPartTransformFailed))
                        }
                    }
                },
            )
        }
    }

    fun showSelectedObjectActions(view: View) {
        val context = view.context
        captureSelection(view) { viewport, snapshot ->
            fragment.showWorkspaceComposeDialog { dismiss ->
                ModelChoiceDialog(
                    title = context.getString(R.string.MenuFileObjectActions),
                    cancelLabel = context.getString(android.R.string.cancel),
                    choices = listOf(
                        context.getString(R.string.MenuFileDuplicateCount),
                        context.getString(R.string.MenuFileMoveToPlate),
                        context.getString(R.string.MenuFileDelete),
                    ),
                    onDismiss = dismiss,
                    onSelect = { which ->
                        dismiss()
                        when (which) {
                            0 -> showDuplicateDialog(view, viewport, snapshot)
                            1 -> showMoveToPlateDialog(view, snapshot)
                            2 -> viewport.deleteSelectedModelsIfCurrent(snapshot) { removed ->
                                when {
                                    removed == MODEL_EDIT_STALE_SELECTION -> reportStaleSelection()
                                    removed > 0 -> fragment.updateModel()
                                }
                            }
                        }
                    },
                )
            }
        }
    }

    /** Assigns a whole object to an ACE/slicer tool; fine-grained color painting stays separate. */
    fun showObjectFilamentSlotDialog(view: View) {
        captureSelection(view) { _, snapshot ->
            showObjectFilamentSlotDialog(view, snapshot.primaryObject, snapshot)
        }
    }

    private fun showObjectFilamentSlotDialog(
        view: View,
        objectIndex: Int,
        expectedSelection: ModelSelectionSnapshot,
    ) {
        val slots: List<FilamentSlot> = Prefs.getFilamentSlots()
        if (slots.isEmpty()) return
        if (objectIndex !in 0 until expectedSelection.objectCount) return

        val context = view.context
        val currentTool = expectedSelection.model.getExtruder(objectIndex) - 1
        // Match the complete persisted Slicer palette. Limiting this picker to four made
        // individually loaded objects impossible to assign to ACE Pro / larger tool palettes.
        val choices = slots.indices.map { tool ->
            val slot = slots[tool]
            val label = context.getString(
                R.string.ModelContextFilamentSlot,
                tool,
                slot.type,
                String.format(Locale.ROOT, "#%06X", slot.color and 0xFFFFFF),
            )
            if (tool == currentTool) "✓ $label" else label
        }
        fragment.showWorkspaceComposeDialog { dismiss ->
            ModelChoiceDialog(
                title = context.getString(R.string.ModelContextSelectFilamentSlot),
                cancelLabel = context.getString(android.R.string.cancel),
                choices = choices,
                onDismiss = dismiss,
                onSelect = { which ->
                    dismiss()
                    val viewport = fragment.glView ?: return@ModelChoiceDialog
                    // Slicer extruders are one-based: T0 always maps to physical/UI slot 1.
                    val filament = which + 1
                    viewport.setObjectUniformFilamentIfCurrent(objectIndex, filament, expectedSelection) { result ->
                        if (result == MODEL_EDIT_STALE_SELECTION) {
                            reportStaleSelection()
                            return@setObjectUniformFilamentIfCurrent
                        }
                        if (result != 1) return@setObjectUniformFilamentIfCurrent
                        // An explicit object assignment must replace 3MF per-volume and paint
                        // assignments too; changing only the object's base extruder leaves those
                        // imported material overrides active during preview and slicing.
                        Toast.makeText(
                            context,
                            context.getString(R.string.ModelContextFilamentSlotApplied, which),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
            )
        }
    }

    private fun showObjectPartFilamentSlotDialog(
        view: View,
        objectIndex: Int,
        partIndex: Int,
        expectedSelection: ModelSelectionSnapshot,
    ) {
        val slots = Prefs.getFilamentSlots()
        if (slots.isEmpty() || objectIndex !in 0 until expectedSelection.objectCount) return
        val model = expectedSelection.model
        if (partIndex !in 0 until model.getObjectPartCount(objectIndex)) return

        val context = view.context
        val currentTool = (model.getObjectPartExtruder(objectIndex, partIndex) - 1).coerceAtLeast(0)
        val choices = slots.indices.map { tool ->
            val slot = slots[tool]
            val label = context.getString(
                R.string.ModelContextFilamentSlot,
                tool,
                slot.type,
                String.format(Locale.ROOT, "#%06X", slot.color and 0xFFFFFF),
            )
            if (tool == currentTool) "✓ $label" else label
        }
        fragment.showWorkspaceComposeDialog { dismiss ->
            ModelChoiceDialog(
                title = context.getString(R.string.ModelObjectAssignPartFilament),
                cancelLabel = context.getString(android.R.string.cancel),
                choices = choices,
                onDismiss = dismiss,
                onSelect = { which ->
                    dismiss()
                    val viewport = fragment.glView ?: return@ModelChoiceDialog
                    viewport.setObjectPartFilamentIfCurrent(objectIndex, partIndex, which + 1, expectedSelection) { result ->
                        when (result) {
                            MODEL_EDIT_STALE_SELECTION -> reportStaleSelection()
                            1 -> {
                                fragment.updateModel()
                                SliceBeam.EVENT_BUS.fireEvent(SelectedObjectChangedEvent())
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.ModelObjectPartFilamentApplied, which),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    }
                },
            )
        }
    }

    private fun showObjectPartProcessSettingsDialog(
        view: View,
        objectIndex: Int,
        partIndex: Int,
        expectedSelection: ModelSelectionSnapshot,
    ) {
        val context = view.context
        val viewport = fragment.glView ?: return
        viewport.queueEvent {
            val renderer = viewport.renderer
            val model = expectedSelection.model
            if (renderer.model !== model || !renderer.isSelectionBindingCurrent(expectedSelection) ||
                objectIndex !in 0 until model.objectsCount || partIndex !in 0 until model.getObjectPartCount(objectIndex)) return@queueEvent
            val revision = model.stateRevision
            val values = listOf("wall_loops", "sparse_infill_density", "top_shell_layers", "sparse_infill_pattern", "top_surface_pattern", "bottom_surface_pattern")
                .associateWith { model.getObjectPartConfigOption(objectIndex, partIndex, it).orEmpty() }
            val name = "${model.getObjectName(objectIndex) ?: context.getString(R.string.MenuFileObjectNumber, (objectIndex + 1).toString())} · " +
                context.getString(R.string.ModelObjectPartNumber, (partIndex + 1).toString())
            ViewUtils.postOnMainThread {
                if (fragment.glView !== viewport || !fragment.canShowWorkspaceComposeDialog()) return@postOnMainThread
                fragment.showWorkspaceComposeDialog { dismiss ->
                    ModelObjectProcessOverridesDialog(
                        objectName = name,
                        initialValues = values,
                        includeObjectSupport = false,
                        scopeHint = R.string.ModelObjectPartProcessSettingsHint,
                        onDismiss = dismiss,
                        onApply = { overrides ->
                            dismiss()
                            viewport.queueEvent {
                                val currentRenderer = viewport.renderer
                                val result = if (currentRenderer.model === model && model.stateRevision == revision &&
                                    currentRenderer.isSelectionBindingCurrent(expectedSelection)) {
                                    model.setObjectPartConfigOptions(objectIndex, partIndex, overrides)
                                } else -1
                                if (result == 1) fragment.updateModelOnGlThread()
                                ViewUtils.postOnMainThread {
                                    SliceBeam.EVENT_BUS.fireEvent(
                                        NeedSnackbarEvent(
                                            if (result >= 0) R.string.ModelObjectProcessSettingsSaved
                                            else R.string.ModelObjectProcessSettingsStale,
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

    fun showDuplicateDialog(view: View) {
        captureSelection(view) { viewport, snapshot ->
            showDuplicateDialog(view, viewport, snapshot)
        }
    }

    private fun showDuplicateDialog(
        view: View,
        viewport: ru.ytkab0bp.slicebeam.view.GLView,
        snapshot: ModelSelectionSnapshot,
    ) {
        val context = view.context
        fragment.showWorkspaceComposeDialog { dismiss ->
            DuplicateModelDialog(
                title = context.getString(R.string.MenuFileDuplicateCount),
                hint = context.getString(R.string.MenuFileDuplicateCountHint),
                confirmLabel = context.getString(R.string.ModelContextDuplicate),
                cancelLabel = context.getString(android.R.string.cancel),
                onDismiss = dismiss,
                onConfirm = { copies ->
                    dismiss()
                    viewport.duplicateSelectedModelsIfCurrent(copies, snapshot) { addedCopies ->
                        when {
                            addedCopies == MODEL_EDIT_STALE_SELECTION -> reportStaleSelection()
                            addedCopies > 0 -> {
                                fragment.updateModel()
                                Toast.makeText(
                                    context,
                                    context.resources.getQuantityString(R.plurals.MenuFileDuplicatedCount, addedCopies, addedCopies),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                            else -> Toast.makeText(context, R.string.ModelContextDuplicateNoRoom, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
            )
        }
    }

    fun showMoveToPlateDialog(view: View) {
        captureSelection(view) { _, snapshot -> showMoveToPlateDialog(view, snapshot) }
    }

    private fun showMoveToPlateDialog(view: View, snapshot: ModelSelectionSnapshot) {
        val plateCount = fragment.plateCount
        if (plateCount < 2) {
            Toast.makeText(view.context, R.string.MenuFileMoveNoOtherPlate, Toast.LENGTH_SHORT).show()
            return
        }
        val context = view.context
        val source = fragment.currentPlateIndex
        val targets = (0 until plateCount).filter { it != source }
        val labels = targets.map { context.getString(R.string.PlateNumber, (it + 1).toString()) }
        fragment.showWorkspaceComposeDialog { dismiss ->
            ModelChoiceDialog(
                title = context.getString(R.string.MenuFileMoveToPlate),
                cancelLabel = context.getString(android.R.string.cancel),
                choices = labels,
                onDismiss = dismiss,
                onSelect = { which ->
                    dismiss()
                    val target = targets.getOrNull(which) ?: return@ModelChoiceDialog
                    if (target !in 0 until fragment.plateCount || target == fragment.currentPlateIndex) return@ModelChoiceDialog
                    fragment.moveSelectedObjectsToPlateInternal(target, Runnable {
                        Toast.makeText(
                            context,
                            context.getString(R.string.MenuFileMovedToPlate, (target + 1).toString()),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }, snapshot)
                },
            )
        }
    }
}
