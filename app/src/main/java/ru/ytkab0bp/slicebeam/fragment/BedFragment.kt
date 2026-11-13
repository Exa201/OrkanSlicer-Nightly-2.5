package ru.ytkab0bp.slicebeam.fragment
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Process
import android.text.TextUtils
import android.util.Log
import android.util.SparseArray
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import java.io.File
import java.io.IOException
import java.util.ArrayDeque
import java.util.HashSet
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import ru.ytkab0bp.eventbus.EventHandler
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.SliceBeam
import ru.ytkab0bp.slicebeam.components.ModelContextMenuDialog
import ru.ytkab0bp.slicebeam.components.PreSliceFilamentDialog
import ru.ytkab0bp.slicebeam.components.SliceProgressDialog
import ru.ytkab0bp.slicebeam.components.PrinterUploadProgressDialog
import ru.ytkab0bp.slicebeam.render.ModelSelectionSnapshot
import ru.ytkab0bp.slicebeam.components.WorkspaceComposeToolRoute
import ru.ytkab0bp.slicebeam.components.KromacutEditorMenu
import ru.ytkab0bp.slicebeam.components.WorkspaceFilamentPaletteSheet
import ru.ytkab0bp.slicebeam.components.WorkspaceProfileSheet
import ru.ytkab0bp.slicebeam.components.bed_menu.BedMenu
import ru.ytkab0bp.slicebeam.components.bed_menu.WorkspaceComposeBedMenu
import ru.ytkab0bp.slicebeam.components.bed_menu.WorkspaceComposeBedMenuHost
import ru.ytkab0bp.slicebeam.components.bed_menu.CameraMenu
import ru.ytkab0bp.slicebeam.components.bed_menu.CameraPreset
import ru.ytkab0bp.slicebeam.components.bed_menu.FileMenu
import ru.ytkab0bp.slicebeam.components.bed_menu.ModelMenu
import ru.ytkab0bp.slicebeam.components.bed_menu.OptionsMenu
import ru.ytkab0bp.slicebeam.components.bed_menu.SliceMenu
import ru.ytkab0bp.slicebeam.components.bed_menu.SlicePreparationTools
import ru.ytkab0bp.slicebeam.components.bed_menu.TransformMenu
import ru.ytkab0bp.slicebeam.config.ConfigObject
import ru.ytkab0bp.slicebeam.config.AdaptivePAModelValidator
import ru.ytkab0bp.slicebeam.config.ProfileSettingsRepository
import ru.ytkab0bp.slicebeam.events.FlattenModeResetEvent
import ru.ytkab0bp.slicebeam.events.CameraProjectionChangedEvent
import ru.ytkab0bp.slicebeam.events.ViewportRenderScaleChangedEvent
import ru.ytkab0bp.slicebeam.events.TranslucentFilamentPreviewChangedEvent
import ru.ytkab0bp.slicebeam.events.CameraPresetRequestedEvent
import ru.ytkab0bp.slicebeam.events.ObjectSurfacePlacementTapEvent
import ru.ytkab0bp.slicebeam.events.ModelGeometryChangedEvent
import ru.ytkab0bp.slicebeam.events.NeedDismissSnackbarEvent
import ru.ytkab0bp.slicebeam.events.NeedSnackbarEvent
import ru.ytkab0bp.slicebeam.events.SlicingProgressEvent
import ru.ytkab0bp.slicebeam.navigation.Fragment
import ru.ytkab0bp.slicebeam.slic3r.Bed3D
import ru.ytkab0bp.slicebeam.slic3r.GCodeProcessorResult
import ru.ytkab0bp.slicebeam.slic3r.replaceGCodeResult
import ru.ytkab0bp.slicebeam.slic3r.Model
import ru.ytkab0bp.slicebeam.slic3r.BooleanOperation
import ru.ytkab0bp.slicebeam.slic3r.LargeModelPick
import ru.ytkab0bp.slicebeam.slic3r.ImportProgressCallback
import ru.ytkab0bp.slicebeam.slic3r.StepImportOptions
import ru.ytkab0bp.slicebeam.slic3r.Slic3rRuntimeError
import ru.ytkab0bp.slicebeam.ui.BeamComposeTheme
import ru.ytkab0bp.slicebeam.ui.navigation.AdaptiveNavigationPolicy
import ru.ytkab0bp.slicebeam.ui.navigation.WorkspaceDestination
import ru.ytkab0bp.slicebeam.ui.workspace.MeasureModePanel
import ru.ytkab0bp.slicebeam.ui.workspace.PlateTabs
import ru.ytkab0bp.slicebeam.ui.workspace.PlateTabsPolicy
import ru.ytkab0bp.slicebeam.ui.workspace.PlateFootprint
import ru.ytkab0bp.slicebeam.ui.workspace.PlateThumbnail
import ru.ytkab0bp.slicebeam.ui.workspace.WorkspaceProfileStatus
import ru.ytkab0bp.slicebeam.ui.workspace.PrinterWebOpenButton
import ru.ytkab0bp.slicebeam.ui.workspace.WorkspaceFilamentButton
import ru.ytkab0bp.slicebeam.ui.workspace.WorkspaceShell
import ru.ytkab0bp.slicebeam.ui.workspace.WorkspaceShellLayoutPolicy
import ru.ytkab0bp.slicebeam.ui.workspace.WorkspaceViewCube
import ru.ytkab0bp.slicebeam.ui.workspace.WorkspaceViewFace
import ru.ytkab0bp.slicebeam.ui.workspace.WorkspaceSnackbarController
import ru.ytkab0bp.slicebeam.render.PrimeTowerObstacle
import ru.ytkab0bp.slicebeam.render.PrimeTowerDragLogic
import ru.ytkab0bp.slicebeam.render.PrimeTowerPlacementMap
import ru.ytkab0bp.slicebeam.render.GLRenderer
import ru.ytkab0bp.slicebeam.ui.workspace.RendererAdapter
import ru.ytkab0bp.slicebeam.ui.workspace.WorkspaceController
import ru.ytkab0bp.slicebeam.ui.workspace.WorkspacePanelDimension
import ru.ytkab0bp.slicebeam.ui.workspace.WorkspacePanelSize
import ru.ytkab0bp.slicebeam.ui.workspace.WorkspaceOverlayPanel
import ru.ytkab0bp.slicebeam.ui.workspace.PrinterWebWorkspacePanel
import ru.ytkab0bp.slicebeam.ui.workspace.WorkspaceMessageDialog
import ru.ytkab0bp.slicebeam.ui.workspace.PaintModePanel
import ru.ytkab0bp.slicebeam.ui.workspace.VariableLayerHeightPanel
import ru.ytkab0bp.slicebeam.ui.workspace.EmbossModePanel
import ru.ytkab0bp.slicebeam.utils.Vec3d
import ru.ytkab0bp.slicebeam.utils.ModelImportPackingOrder
import ru.ytkab0bp.slicebeam.utils.Prefs
import ru.ytkab0bp.slicebeam.config.TemporaryProjectSettings
import ru.ytkab0bp.slicebeam.events.TemporaryProjectSettingsChangedEvent
import ru.ytkab0bp.slicebeam.ui.settings.TemporaryProjectSettingsBar
import ru.ytkab0bp.slicebeam.utils.MmuFilamentSync
import ru.ytkab0bp.slicebeam.utils.QidiGCode3mfPackager
import ru.ytkab0bp.slicebeam.utils.StlImportPreflight
import ru.ytkab0bp.slicebeam.utils.PrinterWebOriginPolicy
import ru.ytkab0bp.slicebeam.utils.ViewUtils
import ru.ytkab0bp.slicebeam.view.GLView
import ru.ytkab0bp.slicebeam.view.FilamentPaletteController
import ru.ytkab0bp.slicebeam.view.EmbossModeController
import ru.ytkab0bp.slicebeam.view.SnackbarsLayout
class BedFragment:Fragment() {
var overlayLayout: FrameLayout? = null
private val snackbarController = WorkspaceSnackbarController()
var glView: GLView? = null
private var rendererAdapter: RendererAdapter? = null
private var rendererVisibilityBeforeKromacut: Int? = null
private var workspaceThemeRevision by mutableIntStateOf(0)
private val workspaceController = WorkspaceController()
private var workspaceNavigationTransitionGeneration = 0
private var activeSliceProgressDialog by mutableStateOf<SliceProgressDialog?>(null)
private var activePrinterUploadProgress: PrinterUploadProgressDialog? = null
private var menuToolsExpanded:Boolean
get() = workspaceController.toolPanelExpanded
set(value) { workspaceController.toolPanelExpanded = value }
private var filamentPaletteRevision by mutableIntStateOf(0)
private var currentMenuSlot:Int
get() = workspaceController.selectedDestinationSlot
set(value) { workspaceController.selectDestination(value) }
private var menuMap = object:SparseArray<BedMenu>() {
override fun get(key:Int):BedMenu {
var menu = super.get(key)
if (menu == null)
{
when (WorkspaceDestination.values()[key]) {
WorkspaceDestination.PROJECT -> menu = FileMenu()
WorkspaceDestination.MODEL -> menu = ModelMenu()
WorkspaceDestination.CAMERA -> menu = CameraMenu()
WorkspaceDestination.EDIT -> menu = TransformMenu()
WorkspaceDestination.TOOLS -> menu = OptionsMenu()
WorkspaceDestination.SLICE -> menu = SliceMenu()
}
put(key, menu)
}
return menu
}
}
private var contentView: View? = null
// Refactored plate management
 private var platesModels = ArrayList<Model?>()
private val projectPlateIds = java.util.ArrayList<Int>()
var currentPlateIndex: Int
get() = workspaceController.activePlateIndex
set(value) { workspaceController.setActivePlate(value) }
private var gCodeResult: GCodeProcessorResult? = null
private var slicedMmuPrinterTitle: String? = null
private var slicedMmuInventorySignature: String? = null
private var totalPlates = 1 // used for importing
private var currentProjectFile: File? = null
private var sourceProjectPlateCount:Int = 0
private var plateTabCount by mutableIntStateOf(1)
private var plateObjectCounts by mutableStateOf<List<Int>>(emptyList())
private var plateThumbnails by mutableStateOf<List<PlateThumbnail>>(emptyList())
@Volatile private var platePreviewRevision = 0
private var platePreviewBuiltRevision = -1
private var plateTabsThemeRevision by mutableIntStateOf(0)
private val platePickerOpen = mutableStateOf(false)
private var printerWebAvailable by mutableStateOf(false)
private var printerWebThemeRevision by mutableIntStateOf(0)
private var profileStatusTitle by mutableStateOf("")
private var profileStatusSummary by mutableStateOf("")
private var profileStatusDescription by mutableStateOf("")
private var profileStatusThemeRevision by mutableIntStateOf(0)
private var compactPortraitLayout:Boolean = false
private var workspaceFontScale: Float = 1f
private var sessionRestored:Boolean = false
private val undoHistory = ArrayDeque<Model>()
private val redoHistory = ArrayDeque<Model>()
private var stableModelSnapshot: Model? = null
private var modelHistoryDisabledForMemory:Boolean = false
private var largeModelWarningShown:Boolean = false
@Volatile private var modelLoadInProgress:Boolean = false
@Volatile private var modelImportGeneration:Int = 0
@Volatile private var importSimplifyDecision: Boolean? = null
@Volatile private var plateLoadGeneration:Int = 0
@Volatile private var destroyed:Boolean = false
private val pendingPlateModelsLock = Object()
private val pendingPlateModels = HashSet<Model>()
private val plateLoadExecutor = Executors.newSingleThreadExecutor({ r-> Thread(r, "OrcaPlateLoader") })
val currentModel:Model?
get() {
if (platesModels.isEmpty()) return null
return platesModels.get(currentPlateIndex)
}
val activePlateIndex: Int get() = currentPlateIndex

/** Snapshot active plate bounds for the print-profile prime-tower preview and free-space action. */
internal fun primeTowerPlacementMap(): PrimeTowerPlacementMap? {
    val rendererBed = glView?.renderer?.bed
    val minimum = rendererBed?.getVolumeMin()
    val maximum = rendererBed?.getVolumeMax()
    val model = currentModel
    val obstacles = ArrayList<PrimeTowerObstacle>()
    if (model != null) {
        val objectMin = Vec3d()
        val objectMax = Vec3d()
        for (index in 0 until model.objectsCount) {
            model.getBoundingBoxApprox(index, objectMin, objectMax)
            if (objectMin.x.isFinite() && objectMin.y.isFinite() && objectMax.x.isFinite() && objectMax.y.isFinite()) {
                obstacles.add(PrimeTowerObstacle(objectMin.x, objectMin.y, objectMax.x, objectMax.y))
            }
        }
    }
    val configuredOutline = runCatching {
        PrimeTowerDragLogic.fromBedShape(SliceBeam.buildCurrentConfigObject().get("bed_shape"), obstacles)
    }.getOrNull()
    if (minimum == null || maximum == null) return configuredOutline
    val boundary = configuredOutline?.bedBoundary.orEmpty()
    return PrimeTowerPlacementMap(minimum.x, minimum.y, maximum.x, maximum.y, obstacles, boundary)
}
val plateCount:Int
get() {
return platesModels.size
}
val selectedTriangleCount:Int
get() {
val model = currentModel
val selected = if (glView != null) glView!!.renderer.selectedObject else -1
return if (model == null || selected < 0) 0 else model.getTriangleCount(selected)
}
private val paintFinishPending = AtomicBoolean(false)
private var paintModeStarting by mutableStateOf(false)
private var embossModeController: EmbossModeController? = null
private var hasWebError:Boolean = false
private var panelWebView: WebView? = null
private var panelWebViewContainer: FrameLayout? = null
private var configuredWebHost: String? = null
private var webViewErrorDescription by mutableStateOf<String?>(null)
private var webViewIsLoading by mutableStateOf(false)
val isPaintMode:Boolean
get() {
return workspaceController.activeToolId == "paint-mode"
}
val isMeasureMode:Boolean
get() {
return workspaceController.activeToolId == "measure-mode"
}
val isVariableLayerHeightMode:Boolean
get() {
return workspaceController.activeToolId == "variable-layer-height"
}
val isEmbossMode:Boolean
get() {
return workspaceController.activeToolId == "emboss-mode"
}
private fun isLargeModelInMemory(model: Model?): Boolean {
 // The process-wide native heap also contains the renderer, fonts and other app state.
 // Using that total as a model-size proxy disabled Undo on devices that had a busy
 // renderer even for a tiny mesh. Bound history by the active mesh instead: every
 // retained snapshot duplicates the triangle data, so include the active model and
 // the maximum number of undo snapshots in the estimate.
 if (model == null) return false
 var triangleCount = 0L
 for (index in 0 until model.objectsCount) {
     triangleCount += model.getTriangleCount(index).coerceAtLeast(0)
 }
 return modelHistoryWouldExceedBudget(triangleCount)
}
private fun getProjectPlateId(index:Int):Int {
return if (index >= 0 && index < projectPlateIds.size) projectPlateIds.get(index) else 0
}
fun hasSliceResult():Boolean {
return gCodeResult != null
}
/** Whether the live physical ACE inventory differs from the one present when this G-code was sliced. */
fun hasMmuInventoryChangedSinceSlice(printerTitle: String): Boolean {
    if (slicedMmuPrinterTitle != printerTitle) return false
    return SlicePreparationTools.didMmuInventoryChange(
        slicedMmuInventorySignature,
        MmuFilamentSync.inventorySignature(printerTitle),
    )
}

/** Discard stale grouped G-code so its preview cannot be mistaken for the current filament setup. */
fun discardSliceAfterMmuInventoryChange() {
    val previous = gCodeResult ?: return
    gCodeResult = null
    slicedMmuPrinterTitle = null
    slicedMmuInventorySignature = null
    tempFileName = null
    tempExportingFile = null
    val viewport = glView
    if (viewport != null) {
        viewport.queueEvent {
            replaceGCodeResult(previous, null) { viewport.renderer.setGCodeViewer(null) }
            viewport.requestRender()
        }
    } else previous.release()
    val ctx = getContext()
    if (ctx != null) (menuMap.get(WorkspaceDestination.SLICE.ordinal) as? SliceMenu)
        ?.refreshItems(usesCompactPortraitLayout(ctx))
}
private var preparedFilamentModel: Model? = null
private var preparedFilamentObjectCount = -1
private var preparedFilamentPrinter = ""
private var preparedFilamentPalette = ""
private var preparedFilamentGateMapping = ""
private var preparedFilamentSettingsRevision = -1L
private var preparedFilamentConfigUid = -1

private fun filamentPaletteSignature(): String = Prefs.getFilamentSlots().joinToString("|") {
    "${it.color}:${it.type.orEmpty()}"
}

private fun filamentGateMappingSignature(): String {
    val config = SliceBeam.CONFIG ?: return ""
    val printer = config.presets?.get("printer")?.let(config::findPrinter)
    val title = printer?.title ?: config.presets?.get("printer").orEmpty()
    val count = maxOf(Prefs.getFilamentSlots().size, currentModel?.requiredFilamentCount ?: 1).coerceAtLeast(1)
    val gates = MmuFilamentSync.getSelectedGates(title, count)
        ?: IntArray(count) { MmuFilamentSync.defaultGateForTool(title, it) }
    return gates.joinToString(",")
}

/** Apply one assignment from the shared model/pre-slice dialog and refresh the model preview. */
private fun applyFilamentAssignment(
    model: Model,
    selectedSlots: IntArray,
    sourceFilaments: IntArray? = null,
    targetFilamentSlots: IntArray? = null,
    after: () -> Unit,
) {
    val viewport = glView ?: return
    viewport.queueEvent {
        if (currentModel !== model) return@queueEvent
        selectedSlots.forEachIndexed { index, slot ->
            if (slot >= 0) viewport.renderer.setObjectUniformFilament(index, slot + 1)
        }
        if (sourceFilaments != null && targetFilamentSlots != null) {
            model.remapFilaments(sourceFilaments, targetFilamentSlots)
            repeat(model.objectsCount) { viewport.renderer.invalidateGlModel(it) }
        }
        val config = SliceBeam.CONFIG
        val printerTitle = config?.presets?.get("printer")?.let(config::findPrinter)?.title
            ?: config?.presets?.get("printer").orEmpty()
        val colors = Prefs.getFilamentPalette()
        val gates = MmuFilamentSync.getSelectedGates(printerTitle,
            maxOf(colors.size, model.requiredFilamentCount))
        val preview = gates?.let { mapping -> colors.copyOf().also { displayed ->
            displayed.indices.forEach { tool ->
                MmuFilamentSync.gateSlot(printerTitle, mapping[tool])?.color?.let { displayed[tool] = it }
            }
        } }
        viewport.renderer.setAssignedSlotPreview(model, preview)
        viewport.requestRender()
        ViewUtils.postOnMainThread {
            if (destroyed || currentModel !== model || getContext() == null) return@postOnMainThread
            preparedFilamentModel = model
            preparedFilamentObjectCount = model.objectsCount
            preparedFilamentPrinter = printerTitle
            preparedFilamentPalette = filamentPaletteSignature()
            preparedFilamentGateMapping = filamentGateMappingSignature()
            preparedFilamentSettingsRevision = TemporaryProjectSettings.sliceRevision
            preparedFilamentConfigUid = SliceBeam.CONFIG_UID
            after()
        }
    }
}

/** Uses the same assignment as slicing, including a preview of the selected slot colors. */
fun showModelColorSlots(synchronize: Boolean = false) {
    val context = getContext() ?: return
    val model = currentModel ?: return
    val config = SliceBeam.CONFIG ?: return
    val printer = config.presets?.get("printer")?.let(config::findPrinter) ?: return
    if (synchronize && PreSliceFilamentDialog.supportsPhysicalSlots(printer)) {
        MmuFilamentSync.synchronize(printer) { _, error ->
            ViewUtils.postOnMainThread {
                if (destroyed || currentModel !== model || getContext() == null) return@postOnMainThread
                if (error == null) showModelColorSlots()
                else showWorkspaceMessageDialog(
                    context.getString(R.string.MmuSyncFailed),
                    error,
                    context.getString(android.R.string.ok),
                )
            }
        }
        return
    }
    PreSliceFilamentDialog.show(this, model, object : PreSliceFilamentDialog.Callback {
        override fun onSelected(slotIndices: IntArray) {
            applyFilamentAssignment(model, slotIndices) {
                Toast.makeText(context, R.string.ModelColorSlotsSaved, Toast.LENGTH_SHORT).show()
            }
        }

        override fun onSourceColorsSelected(slotIndices: IntArray, sourceTools: IntArray, targetSlots: IntArray) {
            applyFilamentAssignment(model, slotIndices, sourceTools, targetSlots) {
                Toast.makeText(context, R.string.ModelColorSlotsSaved, Toast.LENGTH_SHORT).show()
            }
        }
    }, R.string.ModelColorSlotsApply, R.string.ModelColorSlotsAction)
}
private fun showWorkspaceFilamentPalette() {
    val context = getContext() ?: return
    workspaceController.dismissActivePanel()
    val controller = FilamentPaletteController(::onFilamentPaletteChanged)
    val panel = WorkspaceFilamentPaletteSheet(
        context = context,
        controller = controller,
        hasModel = currentModel?.objectsCount?.let { it > 0 } == true,
        onAssignModel = ::showModelColorSlots,
        onSyncFilaments = { callback ->
            val config = SliceBeam.CONFIG
            val printer = config?.presets?.get("printer")?.let(config::findPrinter)
            if (printer == null) {
                callback.onResult(0, context.getString(R.string.PrinterWebHostMissing))
            } else {
                MmuFilamentSync.synchronizeAll(printer) { updated, error ->
                    ViewUtils.postOnMainThread {
                        if (destroyed || getContext() == null) return@postOnMainThread
                        callback.onResult(updated, error)
                    }
                }
            }
        },
    )
    fun dismiss() = workspaceController.clearPanel(WorkspaceOverlayPanel.FILAMENT_PALETTE)
    workspaceController.activatePanel(
        panel = WorkspaceOverlayPanel.FILAMENT_PALETTE,
        content = { BeamComposeTheme { panel.Content(::dismiss) } },
        dismiss = {},
    )
}

/** Palette changes must explicitly schedule a frame because GLView renders on demand. */
private fun onFilamentPaletteChanged() {
    filamentPaletteRevision++
    val viewport = glView ?: return
    val model = currentModel ?: return
    viewport.queueEvent {
        if (viewport.renderer.model === model) {
            // Drop the previous ACE preview snapshot so freshly saved palette colors win.
            viewport.renderer.setAssignedSlotPreview(model, null)
            viewport.requestRender()
        }
    }
}
private fun showWorkspaceProfileSheet(context: Context) {
    workspaceController.dismissActivePanel()
    val panel = WorkspaceProfileSheet(context, Runnable { refreshProfileStatus() })
    fun dismiss() = workspaceController.clearPanel(WorkspaceOverlayPanel.PROFILE_PICKER)
    workspaceController.activatePanel(
        panel = WorkspaceOverlayPanel.PROFILE_PICKER,
        content = { BeamComposeTheme { panel.Content(::dismiss) } },
        dismiss = {},
    )
}
/** Selects model filament slots first, then slices and runs the continuation on success. */
 @JvmOverloads fun startSlicing(onSuccess:Runnable? = null) {
val ctx = getContext()
if (ctx == null || glView == null) return
val model = glView!!.renderer.model
if (model == null)
{
showWorkspaceMessageDialog(
    ctx.getString(R.string.SliceFailed),
    ctx.getString(R.string.SliceFailedNoModels),
    ctx.getString(android.R.string.ok),
)
return
}
val activeModel = model ?: return
if (preparedFilamentModel === activeModel &&
    preparedFilamentObjectCount == activeModel.objectsCount &&
    preparedFilamentPrinter == SliceBeam.CONFIG?.presets?.get("printer").orEmpty() &&
    preparedFilamentPalette == filamentPaletteSignature() &&
    preparedFilamentGateMapping == filamentGateMappingSignature() &&
    preparedFilamentSettingsRevision == TemporaryProjectSettings.sliceRevision &&
    preparedFilamentConfigUid == SliceBeam.CONFIG_UID) {
    runSlice(activeModel, onSuccess)
    return
}
PreSliceFilamentDialog.show(this, activeModel, object : PreSliceFilamentDialog.Callback {
    override fun onSelected(slotIndices: IntArray) {
        applyFilamentAssignment(activeModel, slotIndices) { runSlice(activeModel, onSuccess) }
    }

    override fun onSourceColorsSelected(slotIndices: IntArray, sourceTools: IntArray, targetSlots: IntArray) {
        applyFilamentAssignment(activeModel, slotIndices, sourceTools, targetSlots) {
            runSlice(activeModel, onSuccess)
        }
    }
})
}
private fun showMmuSyncFailure(context:Context, onContinue: Runnable) {
if (getContext() == null) return
showWorkspaceMessageDialog(
    context.getString(R.string.MmuSyncFailed),
    context.getString(R.string.MmuAssignmentSyncFailed),
    context.getString(R.string.PreSliceOffline),
    context.getString(android.R.string.cancel),
) { onContinue.run() }
}
private fun runSlice(model:Model, onSuccess:Runnable? = null) {
val ctx = getContext()
if (ctx == null || glView == null || model !== currentModel) return
tempExportingFile = null
val cfg = SliceBeam.getCurrentConfigFile()
val gcode = tempGCodePath
val settingsRevision = TemporaryProjectSettings.sliceRevision
val sliceConfig = SliceBeam.CONFIG
val slicePrinter = sliceConfig?.presets?.get("printer")?.let(sliceConfig::findPrinter)
val mmuPrinterTitle = slicePrinter?.title.orEmpty()
val mmuInventoryAtSlice = if (SlicePreparationTools.isAnycubicKobra3Model(slicePrinter?.get("printer_model")))
    MmuFilamentSync.inventorySignature(mmuPrinterTitle) else null
if (!DEBUG_VIEWER) showSliceProgress(ctx)
Thread({ try
{
Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT)
SliceBeam.ensureCompatibleSelection()
val activeConfig = SliceBeam.CONFIG
val activePrinter = if (activeConfig == null) null
else activeConfig.findPrinter(activeConfig.presets?.get("printer"))
SliceBeam.genCurrentConfig(model, QidiGCode3mfPackager.isQidiQ2(activePrinter))
val produced = if (!DEBUG_VIEWER)
{
val result = model.slice(cfg.getAbsolutePath(), gcode.getAbsolutePath(),
{ progress, text-> SliceBeam.EVENT_BUS.fireEvent(SlicingProgressEvent(progress, text)) },
SliceBeam.PENDING_CALIB_MODE, SliceBeam.PENDING_CALIB_START,
SliceBeam.PENDING_CALIB_END, SliceBeam.PENDING_CALIB_STEP)
SliceBeam.PENDING_CALIB_MODE = 0
SliceBeam.EVENT_BUS.fireEvent(SlicingProgressEvent(100, ""))
result
}
else
{
GCodeProcessorResult(gcode)
}
ViewUtils.postOnMainThread({
val viewport = glView
if (destroyed || viewport == null || model !== currentModel || settingsRevision != TemporaryProjectSettings.sliceRevision) {
    produced.release()
    if (!destroyed && getContext() != null) SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.ProjectSettingsReslice))
    return@postOnMainThread
}
val previousResult = gCodeResult
gCodeResult = produced
slicedMmuPrinterTitle = mmuPrinterTitle.takeIf { mmuInventoryAtSlice != null }
slicedMmuInventorySignature = mmuInventoryAtSlice
viewport.queueEvent({ replaceGCodeResult(previousResult, produced) {
    viewport.renderer.setGCodeViewer(produced)
}
viewport.requestRender() })
tempFileName = produced.getRecommendedName()
tempExportingFile = null
val portrait = usesCompactPortraitLayout(ctx)
(menuMap.get(WorkspaceDestination.SLICE.ordinal) as SliceMenu)
.refreshItems(portrait)
val warnings = produced.adaptivePAModelWarnings
if (warnings.isNotEmpty()) {
    val messages = warnings.map { warning ->
        if (warning.problem == AdaptivePAModelValidator.Problem.NO_MEASUREMENTS) {
            return@map ctx.getString(R.string.AdaptivePAModelNoMeasurements, warning.filamentNumber)
        }
        val problem = when (warning.problem) {
            AdaptivePAModelValidator.Problem.INVALID_CHARACTERS -> R.string.AdaptivePAModelInvalidCharacters
            AdaptivePAModelValidator.Problem.VALUE_COUNT -> R.string.AdaptivePAModelValueCount
            AdaptivePAModelValidator.Problem.INVALID_NUMBER -> R.string.AdaptivePAModelInvalidNumber
            AdaptivePAModelValidator.Problem.PA_TOO_HIGH -> R.string.AdaptivePAModelPaTooHigh
            AdaptivePAModelValidator.Problem.FLOW_NOT_GREATER_THAN_PA -> R.string.AdaptivePAModelFlowTooLow
            AdaptivePAModelValidator.Problem.ACCELERATION_NOT_GREATER_THAN_FLOW -> R.string.AdaptivePAModelAccelerationTooLow
            AdaptivePAModelValidator.Problem.NO_MEASUREMENTS -> R.string.AdaptivePAModelNoMeasurements
        }
        ctx.getString(
            R.string.AdaptivePAModelValidationLine,
            warning.filamentNumber,
            requireNotNull(warning.lineNumber),
            ctx.getString(problem),
        )
    }.distinct().joinToString("\n")
    showWorkspaceMessageDialog(
        ctx.getString(R.string.AdaptivePAModelSliceWarningTitle),
        ctx.getString(R.string.AdaptivePAModelSliceWarningBody, messages),
        ctx.getString(R.string.AdaptivePAModelSliceWarningContinue),
        ctx.getString(android.R.string.cancel),
        onConfirm = { onSuccess?.run() },
    )
} else if (onSuccess != null) onSuccess.run()
})
}
catch (e:Exception) {
Log.e("BedFragment", "Slice failed", e)
ViewUtils.postOnMainThread({
    SliceBeam.EVENT_BUS.fireEvent(SlicingProgressEvent(100, ""))
    showWorkspaceMessageDialog(
        ctx.getString(R.string.SliceFailed),
        e.message ?: ctx.getString(R.string.SliceFailed),
        ctx.getString(android.R.string.ok),
    )
})
}
}).start()
}
internal fun captureSelectedObjectForSimplification(onCaptured: (ModelSelectionSnapshot, Int) -> Unit) {
    val viewport = glView ?: return
    viewport.queueEvent {
        val snapshot = viewport.renderer.captureSelectionSnapshot() ?: return@queueEvent
        val triangles = runCatching { snapshot.model.getTriangleCount(snapshot.primaryObject) }
            .getOrElse { error ->
                Log.e("BedFragment", "Could not inspect selected model for simplification", error)
                return@queueEvent
            }
        ViewUtils.postOnMainThread {
            if (!destroyed && glView === viewport && canShowWorkspaceComposeDialog()) {
                onCaptured(snapshot, triangles)
            }
        }
    }
}

internal fun smoothSelectedObject(expectedSelection: ModelSelectionSnapshot) {
    val viewport = glView ?: return
    viewport.queueEvent {
        if (!viewport.renderer.isSelectionSnapshotCurrent(expectedSelection)) {
            ViewUtils.postOnMainThread {
                SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.ModelSelectionChanged))
            }
            return@queueEvent
        }
        val view = viewport
        val snapshot = expectedSelection
        val tag = "model-smooth-mesh"
        if (destroyed || view !== glView) return@queueEvent
        SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(
            SnackbarsLayout.Type.LOADING, R.string.ModelSmoothMeshRunning).tag(tag))
        try {
            val smoothedTriangles = snapshot.model.smoothMesh(snapshot.primaryObject)
            if (smoothedTriangles <= 0) {
                ViewUtils.postOnMainThread {
                    SliceBeam.EVENT_BUS.fireEvent(NeedDismissSnackbarEvent(tag))
                    SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.ModelSmoothMeshFailed))
                }
                return@queueEvent
            }
            view.renderer.invalidateGlModel(snapshot.primaryObject)
            view.requestRender()
            ViewUtils.postOnMainThread {
                updateModel()
                SliceBeam.EVENT_BUS.fireEvent(NeedDismissSnackbarEvent(tag))
                SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(
                    SliceBeam.INSTANCE!!.getResources().getQuantityString(
                        R.plurals.ModelSmoothMeshDone, smoothedTriangles, smoothedTriangles)))
            }
        } catch (error: Exception) {
            Log.e("BedFragment", "Could not smooth selected model", error)
            ViewUtils.postOnMainThread {
                SliceBeam.EVENT_BUS.fireEvent(NeedDismissSnackbarEvent(tag))
                SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.ModelSmoothMeshFailed))
            }
        }
    }
}

fun simplifySelectedObject(keepRatio:Float, maxTriangles:Int? = null) {
    val viewport = glView ?: return
    viewport.queueEvent {
        val snapshot = viewport.renderer.captureSelectionSnapshot() ?: return@queueEvent
        simplifySelectedObjectOnGl(viewport, snapshot, keepRatio, maxTriangles)
    }
}

internal fun simplifySelectedObject(
    expectedSelection: ModelSelectionSnapshot,
    keepRatio: Float,
    maxTriangles: Int? = null,
) {
    val viewport = glView ?: return
    viewport.queueEvent {
        if (!viewport.renderer.isSelectionSnapshotCurrent(expectedSelection)) {
            ViewUtils.postOnMainThread {
                SliceBeam.EVENT_BUS.fireEvent(NeedDismissSnackbarEvent("model-simplify"))
                SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.ModelSelectionChanged))
            }
            return@queueEvent
        }
        simplifySelectedObjectOnGl(viewport, expectedSelection, keepRatio, maxTriangles)
    }
}

private fun simplifySelectedObjectOnGl(
    view: GLView,
    snapshot: ModelSelectionSnapshot,
    keepRatio: Float,
    maxTriangles: Int?,
) {
    val model = snapshot.model
    val selected = snapshot.primaryObject
    val tag = "model-simplify"
    if (destroyed || view !== glView || !view.renderer.isSelectionSnapshotCurrent(snapshot)) {
        ViewUtils.postOnMainThread {
            SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.ModelSelectionChanged))
        }
        return
    }
    SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(
        SnackbarsLayout.Type.LOADING, R.string.ModelSimplifyRunning).tag(tag))
    try {
        val before = model.getTriangleCount(selected)
        var after = model.simplify(selected, keepRatio)
        if (maxTriangles != null) {
            repeat(2) {
                if (after > maxTriangles) {
                    after = model.simplify(
                        selected,
                        LargeModelPick.suggestedKeepRatio(after, maxTriangles),
                    )
                }
            }
        }
        view.renderer.invalidateGlModel(selected)
        view.requestRender()
        ViewUtils.postOnMainThread {
            updateModel()
            SliceBeam.EVENT_BUS.fireEvent(NeedDismissSnackbarEvent(tag))
            if (maxTriangles != null && after > maxTriangles) {
                SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(
                    SnackbarsLayout.Type.ERROR, R.string.LargeModelSurfaceStillDense))
            } else {
                SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(
                    SliceBeam.INSTANCE!!.getResources().getQuantityString(
                        R.plurals.ModelSimplifyDone, after, before, after)))
            }
        }
    } catch (e: Exception) {
        Log.e("BedFragment", "Could not simplify model", e)
        ViewUtils.postOnMainThread {
            SliceBeam.EVENT_BUS.fireEvent(NeedDismissSnackbarEvent(tag))
            SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(
                SnackbarsLayout.Type.ERROR, R.string.ModelSimplifyFailed))
        }
    }
}
fun moveSelectedObjectToPlate(targetPlateIndex:Int, onComplete:Runnable) =
    moveSelectedObjectsToPlate(targetPlateIndex, onComplete)

/** Move the complete current selection to another workspace plate without flattening its objects. */
fun moveSelectedObjectsToPlate(targetPlateIndex: Int, onComplete: Runnable) =
    moveSelectedObjectsToPlateInternal(targetPlateIndex, onComplete, null)

internal fun moveSelectedObjectsToPlateInternal(
    targetPlateIndex: Int,
    onComplete: Runnable,
    expectedSelection: ModelSelectionSnapshot?,
) {
    val viewport = glView ?: return
    val sourcePlateIndex = currentPlateIndex
    val sourceModel = currentModel ?: return
    if (targetPlateIndex !in platesModels.indices || targetPlateIndex == sourcePlateIndex) return

    // Selection is renderer state and must be read on the GL thread. Keep a stable copy while
    // switchPlate clears the selection and may lazily load a project plate.
    viewport.queueEvent {
        if (viewport.renderer.model !== sourceModel ||
            (expectedSelection != null && !viewport.renderer.isSelectionSnapshotCurrent(expectedSelection))
        ) {
            if (expectedSelection != null) ViewUtils.postOnMainThread {
                SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.ModelSelectionChanged))
            }
            return@queueEvent
        }
        val selectedIndices = viewport.renderer.selectedObjectIndices()
            .filter { it in 0 until sourceModel.objectsCount }
            .toIntArray()
        if (selectedIndices.isEmpty()) return@queueEvent
        ViewUtils.postOnMainThread {
            if (destroyed || glView !== viewport || currentPlateIndex != sourcePlateIndex ||
                currentModel !== sourceModel || targetPlateIndex !in platesModels.indices) {
                return@postOnMainThread
            }
            switchPlate(targetPlateIndex, true, object : ModelLoadCallback {
                override fun onLoaded(loadedModel: Model, firstNewObject: Int, addedObjects: Int) {
                    if (destroyed || glView !== viewport || viewport.renderer.model !== loadedModel ||
                        selectedIndices.any { it !in 0 until sourceModel.objectsCount }) {
                        if (!destroyed) ViewUtils.postOnMainThread { switchPlate(sourcePlateIndex) }
                        return
                    }

                    val firstMovedIndex = loadedModel.objectsCount
                    try {
                        selectedIndices.forEach { loadedModel.addObject(sourceModel, it) }
                    } catch (error: Exception) {
                        while (loadedModel.objectsCount > firstMovedIndex) {
                            loadedModel.deleteObject(loadedModel.objectsCount - 1)
                        }
                        loadedModel.resetBoundingBox()
                        viewport.renderer.resetGlModels()
                        viewport.requestRender()
                        android.util.Log.e("BedFragment", "Could not transfer selected objects to plate", error)
                        ViewUtils.postOnMainThread { switchPlate(sourcePlateIndex) }
                        return
                    }

                    selectedIndices.sortedDescending().forEach(sourceModel::deleteObject)
                    sourceModel.resetBoundingBox()
                    viewport.renderer.resetGlModels()
                    viewport.renderer.setSelectedObjects(IntArray(selectedIndices.size) { firstMovedIndex + it })
                    viewport.requestRender()
                    ViewUtils.postOnMainThread {
                        if (destroyed || glView !== viewport || currentModel !== loadedModel) return@postOnMainThread
                        updateModel()
                        onComplete.run()
                    }
                }

                override fun onFailed(error: Exception) {
                    android.util.Log.e("BedFragment", "Could not load target plate for object transfer", error)
                    ViewUtils.postOnMainThread { if (!destroyed) switchPlate(sourcePlateIndex) }
                }

                override fun onCancelled() {
                    // A newer plate choice superseded the lazy load. No model objects have been
                    // moved yet, so preserve that newer navigation instead of forcing the source plate.
                }
            })
        }
    }
}
override fun onCreate() {
super.onCreate()
SliceBeam.EVENT_BUS.registerListener(this)
}
@EventHandler(runOnMainThread = true)
fun onNeedSnackbar(e:NeedSnackbarEvent) {
val s = SnackbarsLayout.Snackbar(e.type, e.title ?: "")
if (e.tag != null)
{
s.tag(e.tag!!)
}
s.updateTaggedEntryInPlace = e.updateTaggedEntryInPlace

if (e.buttonTitle != null)
{
s.lifetime = 0
s.buttonTitle = e.buttonTitle
s.buttonClick = e.buttonClick
}
snackbarController.show(s)
}
@EventHandler(runOnMainThread = true)
fun onCameraProjectionChanged(event: CameraProjectionChangedEvent) {
    val view = glView ?: return
    view.queueEvent {
        view.renderer.updateProjection()
        view.requestRender()
    }
}
@EventHandler(runOnMainThread = true)
fun onViewportRenderScaleChanged(event: ViewportRenderScaleChangedEvent) {
    glView?.applyRenderScale()
}
@EventHandler(runOnMainThread = true)
fun onTranslucentFilamentPreviewChanged(event: TranslucentFilamentPreviewChangedEvent) {
    val view = glView ?: return
    view.queueEvent {
        view.renderer.updateToolPreviewColors()
    }
}
@EventHandler(runOnMainThread = true)
fun onObjectSurfacePlacementTap(event: ObjectSurfacePlacementTapEvent) {
    val viewport = glView ?: return
    viewport.placeSelectedObjectsOnSurface(
        event.targetObjectIndex, event.position, event.normal, event.booleanOperation,
    ) { result ->
        if (result >= 0) {
            updateModel()
            SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(
                if (event.booleanOperation == null) R.string.ModelMenuPlaceOnSurfaceDone else R.string.ModelBooleanDone,
            ))
        } else if (result == ru.ytkab0bp.slicebeam.render.MODEL_EDIT_STALE_SELECTION) {
            SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.ModelSelectionChanged))
        } else if (result == ru.ytkab0bp.slicebeam.render.MODEL_EDIT_OPERATION_FAILED) {
            SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(
                if (event.booleanOperation == null) R.string.ModelMenuPlaceFailed else R.string.ModelBooleanFailed,
            ))
        } else if (result == ru.ytkab0bp.slicebeam.render.MODEL_EDIT_ROLLBACK_FAILED) {
            SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(
                if (event.booleanOperation == null) R.string.ModelMenuPlaceRollbackFailed else R.string.ModelBooleanFailed,
            ))
        } else {
            val specificMessage = if (event.booleanOperation != null &&
                result != ru.ytkab0bp.slicebeam.render.MODEL_EDIT_OPERATION_FAILED &&
                result != ru.ytkab0bp.slicebeam.render.MODEL_EDIT_ROLLBACK_FAILED
            ) {
                ru.ytkab0bp.slicebeam.components.bed_menu.ModelBooleanFailureMessage.resourceFor(result)
            } else null
            SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(
                specificMessage ?: if (event.booleanOperation != null) R.string.ModelBooleanFailed
                else R.string.ModelMenuPlaceFailed,
            ))
        }
    }
}
@EventHandler(runOnMainThread = true)
fun onModelGeometryChanged(event: ModelGeometryChangedEvent) {
    updateModel()
}
@EventHandler(runOnMainThread = true)
fun onCameraPresetRequested(event: CameraPresetRequestedEvent) {
    (menuMap.get(WorkspaceDestination.CAMERA.ordinal) as? CameraMenu)?.let { cameraMenu ->
        cameraMenu.onSetBed(this)
        cameraMenu.selectCameraPreset(event.preset)
    }
}
@EventHandler(runOnMainThread = true)
fun onTemporaryProjectSettingsChanged(event: TemporaryProjectSettingsChangedEvent) {
if (getView() == null || glView == null) return
refreshProfileStatus()
filamentPaletteRevision++
if (event.settingsChanged && gCodeResult != null) {
    val previous = gCodeResult
    gCodeResult = null
    val viewport = glView!!
    viewport.queueEvent {
        replaceGCodeResult(previous, null) { viewport.renderer.setGCodeViewer(null) }
        viewport.requestRender()
    }
    tempExportingFile = null
    (menuMap.get(WorkspaceDestination.SLICE.ordinal) as? SliceMenu)?.refreshItems(usesCompactPortraitLayout(getContext()!!))
}
glView?.requestRender()
}
@EventHandler(runOnMainThread = true)
fun onDismissSnackbar(e:NeedDismissSnackbarEvent) {
snackbarController.dismiss(e.tag)
}
private fun showPrimeTowerPositionDialogForCurrentPlate() {
val context = getContext() ?: return
val config = SliceBeam.buildCurrentConfigObject()
val positions = PrintConfigToolsLogic.towerPositions(
    config.get("wipe_tower_x"),
    config.get("wipe_tower_y"),
)
val plateIndex = currentPlateIndex.coerceIn(0, positions.plateCount - 1)
val editorPositions = PrintConfigTowerPositions(1, listOf(positions.x[plateIndex]), listOf(positions.y[plateIndex]))
val footprint = PrintConfigToolsLogic.towerFootprint(
    config.get("prime_tower_width"),
    config.get("wipe_tower_width"),
    config.get("flush_volumes_matrix"),
)
showWorkspaceComposeDialog { dismiss ->
    PrimeTowerPositionDialog(
        positions = editorPositions,
        onDismiss = dismiss,
        plateNumberOffset = plateIndex,
        helpTextResource = R.string.PrimeTowerPositionTapHelp,
        placementMap = primeTowerPlacementMap(),
        towerWidth = footprint.width,
        towerDepth = footprint.depth,
        onApply = apply@{ values ->
            val current = SliceBeam.buildCurrentConfigObject()
            val x = PrintConfigToolsLogic.updateTowerPosition(current.get("wipe_tower_x"), plateIndex, values.first)
            val y = PrintConfigToolsLogic.updateTowerPosition(current.get("wipe_tower_y"), plateIndex, values.second)
            if (x == null || y == null) return@apply
            val source = SliceBeam.CONFIG
            val base = source?.let { currentConfig ->
                currentConfig.presets?.get("print")?.let(currentConfig::findPrint)
            }
            ProfileSettingsRepository.setPendingValue(
                ConfigObject.PROFILE_LIST_PRINT,
                "wipe_tower_x",
                base?.get("wipe_tower_x"),
                x,
            )
            ProfileSettingsRepository.setPendingValue(
                ConfigObject.PROFILE_LIST_PRINT,
                "wipe_tower_y",
                base?.get("wipe_tower_y"),
                y,
            )
            glView?.requestRender()
            dismiss()
        },
    )
}
}
/** Opens a Compose-owned workspace tool in the exclusive tool panel. */
fun showWorkspaceComposeTool(menu: WorkspaceComposeToolRoute) {
    val context = getContext() ?: return
    val portrait = context.resources.configuration.orientation ==
        android.content.res.Configuration.ORIENTATION_PORTRAIT
    val routeContent = menu.createWorkspaceContent(portrait, this) ?: return
    val toolId = menu.javaClass.name
    val panelSize = menu.workspacePanelSize()
    val renderer = glView
    val hideRendererForWebEditor = menu is KromacutEditorMenu
    var rendererRestored = false
    fun restoreRenderer() {
        if (!hideRendererForWebEditor || rendererRestored) return
        rendererRestored = true
        val previousVisibility = rendererVisibilityBeforeKromacut ?: View.VISIBLE
        rendererVisibilityBeforeKromacut = null
        renderer?.visibility = previousVisibility
        if (previousVisibility == View.VISIBLE) renderer?.requestRender()
    }
    // GLSurfaceView owns a separate Surface. On device GPUs that surface can be composed
    // above the Compose/WebView tool sheet and black out the editor even though its DOM
    // and CSS report visible controls. Hide only for this full-screen web editor; keep
    // the same GLView and native model instance, then restore it when the route closes.
    menu.setWorkspaceDismiss {
        workspaceController.clearTool(toolId)
        restoreRenderer()
    }
    menu.setOnDismiss {
        if (!menu.isAttached()) {
            workspaceController.clearTool(toolId)
            restoreRenderer()
        }
    }
    workspaceController.selectToolWithPanel(
        toolId = toolId,
        dismiss = { menu.dismiss() },
        panelContent = { menu.WorkspaceContent(routeContent) },
        panelSize = panelSize,
    )
    if (hideRendererForWebEditor) {
        if (rendererVisibilityBeforeKromacut == null) {
            rendererVisibilityBeforeKromacut = renderer?.visibility ?: View.VISIBLE
        }
        renderer?.visibility = View.GONE
    }
}
/** Opens a lightweight workspace tool panel while keeping the renderer visible for inspection. */
internal fun showWorkspaceToolPanel(
    toolId: String,
    panelSize: WorkspacePanelSize,
    allowBackgroundInteraction: Boolean = true,
    dockPanel: Boolean = false,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    workspaceController.selectToolWithPanel(
        toolId = toolId,
        dismiss = onDismiss,
        panelContent = content,
        panelSize = panelSize,
        dockPanel = dockPanel,
    )
    workspaceController.setToolAllowsBackgroundInteraction(toolId, allowBackgroundInteraction)
}
internal fun dismissWorkspaceToolPanel() {
    workspaceController.dismissActiveTool()
}
internal fun clearWorkspaceToolPanel(toolId: String) {
    workspaceController.clearTool(toolId)
}
internal fun isWorkspaceToolPanelAttached(toolId: String): Boolean =
    workspaceController.activeToolId == toolId
internal fun setWorkspaceToolAllowsBackgroundInteraction(toolId: String, allowed: Boolean) {
    workspaceController.setToolAllowsBackgroundInteraction(toolId, allowed)
}
private fun showModelContextMenu(objectIndex:Int) {
val ctx = getContext()
if (ctx == null || glView == null || objectIndex == -1) return
workspaceController.dismissActiveTool()
if (workspaceController.activePanel == WorkspaceOverlayPanel.MODEL_CONTEXT_MENU) return
val actions = listOf(
    ctx.getString(R.string.ModelContextDuplicate),
    ctx.getString(R.string.MenuToolbarSplit),
    ctx.getString(R.string.MenuFileMoveToPlate),
    ctx.getString(R.string.ModelContextDelete),
    ctx.getString(R.string.ModelContextFillBed),
    ctx.getString(R.string.ModelContextCenterOnBed),
    ctx.getString(R.string.ModelContextAutoOrient),
    ctx.getString(R.string.ModelContextResetRotation),
    ctx.getString(R.string.ModelContextResetScale),
    ctx.getString(R.string.ModelContextSetOnFace),
    ctx.getString(R.string.ModelContextSelectAll),
    ctx.getString(R.string.ModelContextDeleteAll),
)
val handlers = listOf<() -> Unit>(
    { glView?.duplicateSelectedModelWithResult { addedCopies ->
        if (addedCopies > 0) {
            updateModel()
            Toast.makeText(ctx, R.string.ModelContextDuplicated, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(ctx, R.string.ModelContextDuplicateNoRoom, Toast.LENGTH_SHORT).show()
        }
    }
    },
    { splitSelectedObject() },
    { glView?.let { ru.ytkab0bp.slicebeam.components.bed_menu.FileMenuObjectActions(this).showMoveToPlateDialog(it) } },
    { glView?.deleteSelectedModel({ updateModel()
Toast.makeText(ctx, R.string.ModelContextDeleted, Toast.LENGTH_SHORT).show() })
    },
    { glView?.fillBedWithSelectedModel({ addedCopies->
if (addedCopies < 0) return@fillBedWithSelectedModel
if (addedCopies > 0) updateModel()
Toast.makeText(ctx,
if (addedCopies > 0)
ctx.getResources().getQuantityString(
R.plurals.ModelContextFillBedFinished,
addedCopies, addedCopies)
else
ctx.getString(R.string.ModelContextFillBedNoRoom),
Toast.LENGTH_SHORT).show() }) },
    { glView?.centerSelectedOnBed({ updateModel()
Toast.makeText(ctx, R.string.ModelContextCentered, Toast.LENGTH_SHORT).show() })
    },
    {
        val viewport = glView
        if (viewport != null) viewport.queueEvent({
val model = viewport.renderer.model ?: return@queueEvent
model.autoOrient(objectIndex)
model.ensureOnBed(objectIndex)
viewport.renderer.invalidateGlModel(objectIndex)
viewport.requestRender()
ViewUtils.postOnMainThread({ updateModel()
Toast.makeText(ctx, R.string.ModelContextAutoOriented, Toast.LENGTH_SHORT).show() }) })
    },
    {
        resetModelRotation(objectIndex) {
            Toast.makeText(ctx, R.string.ModelContextResetRotationDone, Toast.LENGTH_SHORT).show()
        }
    },
    {
        resetModelScale(objectIndex) {
            Toast.makeText(ctx, R.string.ModelContextResetScaleDone, Toast.LENGTH_SHORT).show()
        }
    },
    {
        glView?.let { viewport -> viewport.queueEvent({ viewport.renderer.setInFlattenMode(true)
viewport.requestRender() }) }
    },
    { glView?.selectAllModels({ updateModel()
Toast.makeText(ctx, R.string.ModelContextSelectedAll, Toast.LENGTH_SHORT).show() })
    },
    { glView?.deleteAllModels({ updateModel()
Toast.makeText(ctx, R.string.ModelContextDeletedAll, Toast.LENGTH_SHORT).show() })
    },
)
workspaceController.activatePanel(
    panel = WorkspaceOverlayPanel.MODEL_CONTEXT_MENU,
    dismiss = { workspaceController.clearPanel(WorkspaceOverlayPanel.MODEL_CONTEXT_MENU) },
    content = {
        BeamComposeTheme {
            ModelContextMenuDialog(
                title = ctx.getString(R.string.ModelContextMenuTitle),
                cancelLabel = ctx.getString(android.R.string.cancel),
                actions = actions,
                onDismiss = { workspaceController.clearPanel(WorkspaceOverlayPanel.MODEL_CONTEXT_MENU) },
                onSelect = { index ->
                    workspaceController.clearPanel(WorkspaceOverlayPanel.MODEL_CONTEXT_MENU)
                    handlers.getOrNull(index)?.invoke()
                },
            )
        }
    },
)
}
/** Reset the selected object's committed native scale and record the action for Undo/Redo. */
fun resetModelScale(objectIndex: Int? = null, onReset: () -> Unit = {}) {
    val viewport = glView ?: return
    viewport.queueEvent {
        val renderer = viewport.renderer
        val index = objectIndex ?: renderer.selectedObject
        val model = renderer.model ?: return@queueEvent
        if (index !in 0 until model.objectsCount) return@queueEvent
        // Native Model.scale accepts an absolute scale, not a multiplicative delta.
        model.scale(index, 1.0, 1.0, 1.0)
        model.ensureOnBed(index)
        renderer.setSelectionScale(1.0, 1.0, 1.0)
        renderer.invalidateGlModel(index)
        viewport.requestRender()
        ViewUtils.postOnMainThread {
            updateModel()
            onReset()
        }
    }
}

/** Reset committed native rotation for one object and record the action for Undo/Redo. */
fun resetModelRotation(objectIndex: Int? = null, onReset: () -> Unit = {}) {
    val viewport = glView ?: return
    viewport.queueEvent {
        val renderer = viewport.renderer
        val index = objectIndex ?: renderer.selectedObject
        val model = renderer.model ?: return@queueEvent
        if (index !in 0 until model.objectsCount) return@queueEvent
        model.resetRotation(index)
        model.ensureOnBed(index)
        renderer.setSelectionRotation(0.0, 0.0, 0.0)
        renderer.invalidateGlModel(index)
        viewport.requestRender()
        ViewUtils.postOnMainThread {
            updateModel()
            onReset()
        }
    }
}
internal fun showWorkspaceMessageDialog(
    title: String?,
    message: String,
    confirmLabel: String,
    dismissLabel: String? = null,
    onDismiss: () -> Unit = {},
    onConfirm: () -> Unit = {},
) {
    if (getContext() == null) return
    workspaceController.dismissActivePanel()
    workspaceController.dismissActiveTool()
    var resolved = false
    fun close(confirmed: Boolean) {
        if (resolved) return
        resolved = true
        workspaceController.clearPanel(WorkspaceOverlayPanel.MESSAGE_DIALOG)
        if (confirmed) onConfirm() else onDismiss()
    }
    val content: @Composable () -> Unit = {
        BeamComposeTheme {
            WorkspaceMessageDialog(
                title = title,
                message = message,
                confirmLabel = confirmLabel,
                dismissLabel = dismissLabel,
                onDismiss = { close(false) },
                onConfirm = { close(true) },
            )
        }
    }
    workspaceController.activatePanel(
        panel = WorkspaceOverlayPanel.MESSAGE_DIALOG,
        content = content,
        dismiss = { close(false) },
    )
}
/** Hosts a workspace-owned Compose dialog under the same exclusive overlay/back handling. */
internal fun showWorkspaceComposeDialog(
    onExternalDismiss: () -> Unit = {},
    content: @Composable (dismiss: () -> Unit) -> Unit,
): () -> Unit {
    if (getContext() == null) return {}
    workspaceController.dismissActivePanel()
    workspaceController.dismissActiveTool()
    var resolved = false
    fun close() {
        if (resolved) return
        resolved = true
        workspaceController.clearPanel(WorkspaceOverlayPanel.MESSAGE_DIALOG)
    }
    workspaceController.activatePanel(
        panel = WorkspaceOverlayPanel.MESSAGE_DIALOG,
        content = { BeamComposeTheme { content(::close) } },
        dismiss = {
            if (!resolved) onExternalDismiss()
            close()
        },
    )
    return ::close
}
fun showSelectedModelContextMenu() {
    showModelContextMenu(glView?.renderer?.selectedObject ?: -1)
}
internal fun isModelContextMenuAttached(): Boolean =
    workspaceController.activePanel == WorkspaceOverlayPanel.MODEL_CONTEXT_MENU
internal fun isWorkspaceMessageDialogAttached(): Boolean =
    workspaceController.activePanel == WorkspaceOverlayPanel.MESSAGE_DIALOG
internal fun canShowWorkspaceComposeDialog(): Boolean =
    getContext() != null && overlayLayout != null
private fun showSliceProgress(context: Context) {
    lateinit var dialog: SliceProgressDialog
    dialog = SliceProgressDialog(context) {
        if (activeSliceProgressDialog === dialog) {
            activeSliceProgressDialog = null
            workspaceController.clearPanel(WorkspaceOverlayPanel.SLICE_PROGRESS)
        }
    }
    activeSliceProgressDialog = dialog
    workspaceController.activatePanel(
        panel = WorkspaceOverlayPanel.SLICE_PROGRESS,
        content = { dialog.Content() },
        dismiss = {
            if (activeSliceProgressDialog === dialog) activeSliceProgressDialog = null
        },
    )
}
internal fun showPrinterUploadProgress(initialMessage: String): PrinterUploadProgressDialog? {
    val context = getContext() ?: return null
    workspaceController.dismissActivePanel()
    workspaceController.dismissActiveTool()
    val progress = PrinterUploadProgressDialog(context, initialMessage)
    activePrinterUploadProgress = progress
    workspaceController.activatePanel(
        panel = WorkspaceOverlayPanel.PRINTER_UPLOAD_PROGRESS,
        content = { progress.Content() },
        dismiss = {
            progress.detachFromWorkspace()
            if (activePrinterUploadProgress === progress) activePrinterUploadProgress = null
        },
    )
    return progress
}
internal fun dismissPrinterUploadProgress(progress: PrinterUploadProgressDialog) {
    progress.detachFromWorkspace()
    if (activePrinterUploadProgress === progress) {
        activePrinterUploadProgress = null
        workspaceController.clearPanel(WorkspaceOverlayPanel.PRINTER_UPLOAD_PROGRESS)
    }
}
internal fun isPrinterUploadProgressAttached(): Boolean =
    workspaceController.activePanel == WorkspaceOverlayPanel.PRINTER_UPLOAD_PROGRESS
@JvmOverloads fun enterPaintMode(objectIndex:Int, mode:Int = ru.ytkab0bp.slicebeam.render.GLRenderer.PAINT_MODE_COLOR) {
requestPaintMode(expectedObjectIndex = objectIndex, mode = mode)
}
/** Starts painting the active selection without reading native model state from the UI thread. */
fun enterPaintModeForCurrentSelection(mode: Int) = requestPaintMode(expectedObjectIndex = null, mode = mode)

private fun requestPaintMode(expectedObjectIndex: Int?, mode: Int) {
    val view = glView ?: return
    if (getContext() == null || overlayLayout == null || isPaintMode || paintModeStarting) return
    workspaceController.dismissActivePanel()
    view.queueEvent {
        val renderer = view.renderer
        val model = renderer.model ?: return@queueEvent
        val selectionBound = expectedObjectIndex == null
        val objectIndex = expectedObjectIndex ?: renderer.selectedObject
        if (objectIndex !in 0 until model.objectsCount) return@queueEvent
        val triangleCount = model.getTriangleCount(objectIndex)
        ViewUtils.postOnMainThread {
            if (destroyed || glView !== view || overlayLayout == null || isPaintMode) return@postOnMainThread
            if (LargeModelPick.needsPreciseSurfaceSimplification(triangleCount)) {
                val ctx = getContext() ?: return@postOnMainThread
                showWorkspaceMessageDialog(
                    ctx.getString(R.string.LargeModelSurfaceTitle),
                    ctx.getString(R.string.LargeModelSurfaceMessage, triangleCount, LargeModelPick.TRIANGLE_LIMIT),
                    ctx.getString(R.string.ModelSimplifyStart),
                    ctx.getString(android.R.string.cancel),
                ) {
                    view.queueEvent {
                        val activeRenderer = view.renderer
                        if (activeRenderer.model !== model ||
                            (selectionBound && activeRenderer.selectedObject != objectIndex)) {
                            ViewUtils.postOnMainThread {
                                SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.ModelSelectionChanged))
                            }
                            return@queueEvent
                        }
                        try {
                            var after = model.simplify(objectIndex, LargeModelPick.suggestedKeepRatio(triangleCount))
                            repeat(2) {
                                if (after > LargeModelPick.TRIANGLE_LIMIT) {
                                    after = model.simplify(objectIndex, LargeModelPick.suggestedKeepRatio(after))
                                }
                            }
                            activeRenderer.invalidateGlModel(objectIndex)
                            view.requestRender()
                            ViewUtils.postOnMainThread {
                                updateModel()
                                if (after > LargeModelPick.TRIANGLE_LIMIT) {
                                    SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.LargeModelSurfaceStillDense))
                                } else {
                                    val message = ctx.resources.getQuantityString(
                                        R.plurals.ModelSimplifyDone, after, triangleCount, after,
                                    )
                                    SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(message))
                                }
                            }
                        } catch (error: Exception) {
                            Log.e("BedFragment", "Could not simplify paint target", error)
                            ViewUtils.postOnMainThread {
                                SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(
                                    SnackbarsLayout.Type.ERROR, R.string.ModelSimplifyFailed,
                                ))
                            }
                        }
                    }
                }
            } else {
                // Hide the compact global filament entry before the GL session is visible.
                // This avoids briefly showing a second filament row during the mode transition.
                paintModeStarting = true
                view.queueEvent {
                    val activeRenderer = view.renderer
                    if (activeRenderer.model !== model ||
                        (selectionBound && activeRenderer.selectedObject != objectIndex)) {
                        ViewUtils.postOnMainThread {
                            paintModeStarting = false
                            SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.ModelSelectionChanged))
                        }
                        return@queueEvent
                    }
                    if (!activeRenderer.beginPaint(objectIndex, mode)) {
                        ViewUtils.postOnMainThread {
                            paintModeStarting = false
                            SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.PaintModeStartFailed))
                        }
                        return@queueEvent
                    }
                    view.requestRender()
                    ViewUtils.postOnMainThread {
                        if (!destroyed && glView === view && overlayLayout != null) {
                            view.isPaintBrushActive = true
                            paintFinishPending.set(false)
                            showPaintModePanel(view, mode)
                            paintModeStarting = false
                        } else {
                            paintModeStarting = false
                            view.queueEvent {
                                view.renderer.endPaint(false)
                                view.requestRender()
                            }
                        }
                    }
                }
            }
        }
    }
}
private fun showPaintModePanel(currentGlView: GLView, mode: Int) {
    val ctx = getContext() ?: return
    showWorkspaceModePanel(
        "paint-mode",
        ::cancelPaintMode,
        WorkspacePanelSize(
            portrait = WorkspacePanelDimension.ClampedFraction(
                // The color selector itself is a single 48 dp palette row. Reserve enough
                // height to keep it visible, then let secondary brush controls scroll. A
                // large 30–32% dock made the plate feel cropped on short phones even though
                // the renderer technically retained 60% of its viewport.
                fraction = if (ctx.resources.configuration.fontScale >= 1.3f) 0.26f else 0.24f,
                minDp = 72f,
                maxDp = if (ctx.resources.configuration.fontScale >= 1.3f) 148f else 136f,
            ),
            landscape = WorkspacePanelDimension.Fixed(280f),
        ),
        dockPanel = mode == GLRenderer.PAINT_MODE_COLOR,
    ) {
        PaintModePanel(
            glView = currentGlView,
            mode = mode,
            onCommit = { baseFilament -> finishPaintMode(commit = true, uniformFilament = baseFilament) },
            onCancel = { cancelPaintMode() },
        )
    }
}
private fun finishPaintMode(commit: Boolean, uniformFilament: Int? = null) {
    if (!paintFinishPending.compareAndSet(false, true)) return
    val currentGlView = glView
    if (currentGlView == null) {
        exitPaintMode()
        return
    }
    currentGlView.isPaintBrushActive = false
    currentGlView.queueEvent {
        if (commit && uniformFilament != null) {
            val objectIndex = currentGlView.renderer.paintObject
            if (objectIndex >= 0) currentGlView.renderer.setObjectUniformFilament(objectIndex, uniformFilament)
        }
        currentGlView.renderer.endPaint(commit)
        currentGlView.requestRender()
        ViewUtils.postOnMainThread {
            // Keep the paint panel as the single owner of the filament controls until the
            // GL thread has committed/discarded the draft and rendered the resulting model.
            // Closing it before this callback let the global filament shortcut return while
            // the paint renderer was still active and resized the bed during that transition.
            exitPaintMode()
            if (commit) updateModel()
        }
    }
}
private fun cancelPaintMode() = finishPaintMode(commit = false)
private fun exitPaintMode() {
if (isPaintMode)
{
removeModePanelHost("paint-mode")
}
}
fun enterMeasureMode() {
val currentGlView = glView
if (currentGlView == null || overlayLayout == null || isMeasureMode) return
workspaceController.dismissActivePanel()
currentGlView.queueEvent {
currentGlView.renderer.isInMeasureMode = true
currentGlView.requestRender()
}
showWorkspaceModePanel("measure-mode", ::exitMeasureMode) {
    MeasureModePanel(glView = currentGlView, onExit = ::exitMeasureMode)
}
}
private fun exitMeasureMode() {
if (isMeasureMode)
{
val currentGlView = glView
currentGlView?.queueEvent {
currentGlView.renderer.isInMeasureMode = false
currentGlView.renderer.clearMeasurePoints()
currentGlView.requestRender()
}
removeModePanelHost("measure-mode")
}
}
fun enterVariableLayerHeightMode() {
val ctx = getContext()
val currentGlView = glView
if (ctx == null || currentGlView == null || overlayLayout == null || isVariableLayerHeightMode) return
workspaceController.dismissActivePanel()
showWorkspaceModePanel("variable-layer-height", ::exitVariableLayerHeightMode) {
    VariableLayerHeightPanel(currentGlView, ::exitVariableLayerHeightMode, ::updateModel)
}
}
private fun exitVariableLayerHeightMode() {
if (isVariableLayerHeightMode)
{
removeModePanelHost("variable-layer-height")
}
}
fun enterEmbossMode(standaloneText: Boolean = false) {
val ctx = getContext()
val currentGlView = glView
if (ctx == null || currentGlView == null || overlayLayout == null || isEmbossMode) return
workspaceController.dismissActivePanel()
val selected = currentGlView.renderer.selectedObject
if (!standaloneText && selected >= 0 && !requirePreciseSurfaceModel(selected)) return
currentGlView.queueEvent({ currentGlView.renderer.isInEmbossMode = true
currentGlView.renderer.isStandaloneTextPlacementMode = standaloneText
currentGlView.requestRender() })
embossModeController = EmbossModeController(ctx, currentGlView, { updateModel() }, standaloneText)
// Register the surface-click callback before the panel is presented. A tap can arrive
// as soon as its Compose content becomes visible, especially during a landscape resize.
embossModeController?.start()
showWorkspaceToolPanel(
    toolId = "emboss-mode",
    panelSize = WorkspacePanelSize(
        portrait = WorkspacePanelDimension.ClampedFraction(0.42f, minDp = 280f, maxDp = 380f),
        landscape = WorkspacePanelDimension.ClampedFraction(0.48f, minDp = 300f, maxDp = 460f),
    ),
    // Emboss and text placement are driven by tapping the GL model. Keep a compact
    // control sheet so its scrim and panel do not swallow the surface-selection gesture.
    allowBackgroundInteraction = true,
    onDismiss = ::exitEmbossMode,
) {
    EmbossModePanel(
        requireNotNull(embossModeController),
        startInStandaloneMode = standaloneText,
        onExit = ::exitEmbossMode,
    )
}
}
private fun showWorkspaceModePanel(
    toolId: String,
    onDismiss: () -> Unit,
    panelSize: WorkspacePanelSize? = null,
    dockPanel: Boolean = false,
    content: @Composable () -> Unit,
): Unit {
    workspaceController.selectToolWithPanel(toolId, onDismiss, content, panelSize, dockPanel)
}
private fun removeModePanelHost(toolId: String) {
    workspaceController.clearTool(toolId)
}
private fun requirePreciseSurfaceModel(objectIndex:Int):Boolean {
val ctx = getContext() ?: return false
val model = currentModel ?: return false
if (objectIndex < 0 || objectIndex >= model.objectsCount) return false
val triangles = model.getTriangleCount(objectIndex)
if (LargeModelPick.canUsePreciseSurfaceTools(triangles)) return true
showWorkspaceMessageDialog(
    ctx.getString(R.string.LargeModelEmbossTitle),
    ctx.getString(R.string.LargeModelEmbossMessage, triangles, LargeModelPick.EXACT_RAYCAST_TRIANGLE_LIMIT),
    ctx.getString(R.string.ModelSimplifyStart),
    ctx.getString(android.R.string.cancel),
) {
        if (currentModel === model && glView?.renderer?.selectedObject == objectIndex) {
            simplifySelectedObject(
                LargeModelPick.suggestedKeepRatio(triangles, LargeModelPick.EXACT_RAYCAST_TRIANGLE_LIMIT),
                LargeModelPick.EXACT_RAYCAST_TRIANGLE_LIMIT,
            )
        }
    }
return false
}
private fun exitEmbossMode() {
if (isEmbossMode)
{
embossModeController?.stop()
embossModeController = null
glView?.queueEvent({ glView?.renderer?.isInEmbossMode = false
glView?.renderer?.isStandaloneTextPlacementMode = false
glView?.requestRender() })
removeModePanelHost("emboss-mode")
}
}
fun loadGCode(f:File) {
val loaded = GCodeProcessorResult(f)
val previous = gCodeResult
gCodeResult = loaded
slicedMmuPrinterTitle = null
slicedMmuInventorySignature = null
ViewUtils.postOnMainThread({ glView!!.queueEvent({ replaceGCodeResult(previous, loaded) {
    glView!!.renderer.setGCodeViewer(loaded)
}
glView!!.requestRender() })
tempFileName = loaded.getRecommendedName()
tempExportingFile = f
val ctx = getContext() ?: return@postOnMainThread
selectMenu(WorkspaceDestination.SLICE.ordinal) })
}
override fun onBackPressed():Boolean {
if (workspaceController.activePanel == WorkspaceOverlayPanel.PRINTER_UPLOAD_PROGRESS) return true
if (glView?.renderer?.isSurfacePlacementActive == true) {
    glView?.cancelSurfacePlacement()
    SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.ModelSurfacePlacementCancelled))
    return true
}
if (workspaceController.dismissActivePanel()) return true
if (isEmbossMode)
{
exitEmbossMode()
return true
}
if (isVariableLayerHeightMode)
{
exitVariableLayerHeightMode()
return true
}
if (isMeasureMode)
{
exitMeasureMode()
return true
}
if (isPaintMode)
{
cancelPaintMode()
return true
}
if (workspaceController.dismissActiveTool())
                    return true
if (workspaceController.activePanel == WorkspaceOverlayPanel.PRINTER_WEB)
{
destroyPrinterWebView()
return true
}
if (currentMenuSlot != 0)
{
    onMenuNavigationSelected(compactPortraitLayout, 0)
    return true
}
return super.onBackPressed()
}
override fun onDestroy() {
super.onDestroy()
snackbarController.clear()
workspaceController.clearPanel(WorkspaceOverlayPanel.MODEL_CONTEXT_MENU)
if (workspaceController.activePanel == WorkspaceOverlayPanel.PRINTER_UPLOAD_PROGRESS) {
    workspaceController.dismissActivePanel()
}
if (workspaceController.activePanel == WorkspaceOverlayPanel.MESSAGE_DIALOG) {
    workspaceController.dismissActivePanel()
}
destroyed = true
modelImportGeneration++
plateLoadGeneration++
plateLoadExecutor.shutdownNow()
synchronized (pendingPlateModelsLock) {
for (model in pendingPlateModels)
{
model.release()
}
pendingPlateModels.clear()
}
destroyPrinterWebView()
SliceBeam.EVENT_BUS.unregisterListener(this)
if (!(getContext() is Activity && (getContext() as Activity).isChangingConfigurations()))
{
clearModelHistory()
for (m in platesModels)
{
if (m != null) m.release()
}
platesModels.clear()
if (gCodeResult != null)
{
gCodeResult!!.release()
gCodeResult = null
}
}
}
override fun onResume() {
super.onResume()
glView!!.onResume()
// App settings can change camera projection while this fragment's view is detached,
// so its event listener is not registered. Re-apply the persisted preference as the
// workspace renderer resumes, on the GL thread.
glView!!.queueEvent {
    glView!!.renderer.updateProjection()
    glView!!.requestRender()
}
updateProfileStatus()
filamentPaletteRevision++
updatePrinterWebViewAvailability()
}
private fun updatePrinterWebViewAvailability() {
val cfg = SliceBeam.CONFIG!!.findPrinter(SliceBeam.CONFIG!!.presets!!.get("printer"))
var enable = cfg != null && !TextUtils.isEmpty(cfg.get("print_host"))
configuredWebHost = null
if (enable)
{
configuredWebHost = PrinterWebOriginPolicy.dashboardUrl(cfg!!.get("print_host"))
enable = !TextUtils.isEmpty(configuredWebHost)
}
printerWebAvailable = enable
if (!enable) destroyPrinterWebView()
}
private fun openPrinterWebView() {
updatePrinterWebViewAvailability()
if (TextUtils.isEmpty(configuredWebHost) || panelWebViewContainer == null || getContext() == null)
{
if (getContext() is ru.ytkab0bp.slicebeam.MainActivity)
{
showWorkspaceMessageDialog(
    getContext()!!.getString(R.string.PrinterWebSetupTitle),
    getContext()!!.getString(R.string.PrinterWebSetupMessage),
    getContext()!!.getString(R.string.PrinterWebSetupAction),
    getContext()!!.getString(android.R.string.cancel),
) { ((getContext() as ru.ytkab0bp.slicebeam.MainActivity).navigationDelegate as ru.ytkab0bp.slicebeam.navigation.MobileNavigationDelegate)
.switchToSlot(4) }
}
else if (getContext() != null)
{
SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.PrinterWebHostMissing))
}
return
}
workspaceController.dismissActivePanel()
val panelContainer = panelWebViewContainer!!
workspaceController.activatePanel(
    panel = WorkspaceOverlayPanel.PRINTER_WEB,
    content = {
        BeamComposeTheme(themeRevision = printerWebThemeRevision) {
            PrinterWebWorkspacePanel(
                panelView = panelContainer,
                loading = webViewIsLoading,
                errorDescription = webViewErrorDescription,
                onRetry = ::openPrinterWebView,
                onOpenInBrowser = {
                    val host = configuredWebHost ?: return@PrinterWebWorkspacePanel
                    try {
                        getContext()?.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(host)))
                    } catch (error: Exception) {
                        Log.w("BedFragment", "Could not open printer page in external browser", error)
                        getContext()?.let { Toast.makeText(it, R.string.PrinterWebOpenFailed, Toast.LENGTH_LONG).show() }
                    }
                },
                onClose = ::destroyPrinterWebView,
            )
        }
    },
    dismiss = { destroyPrinterWebView() },
)
panelContainer.setVisibility(View.VISIBLE)
if (panelWebView != null)
{
 if (!hasWebError) return
 // A renderer that reported an error may be dead or holding a failed navigation state.
 // Recreate it so the next open actually retries the printer page.
 val failedWebView = panelWebView ?: return
 panelWebView = null
 failedWebView.stopLoading()
 panelWebViewContainer!!.removeView(failedWebView)
 failedWebView.destroy()
 hasWebError = false
 webViewErrorDescription = null
}
try
{
val webView = WebView(getContext()!!)
panelWebView = webView
configurePrinterWebView(webView)
webView.setAlpha(0f)
webView.setWebViewClient(object:WebViewClient() {
override fun shouldOverrideUrlLoading(view:WebView, url:String):Boolean {
return !isAllowedPrinterWebUrl(url)
}
override fun shouldOverrideUrlLoading(view:WebView, request:android.webkit.WebResourceRequest):Boolean {
return !isAllowedPrinterWebUrl(request.getUrl().toString())
}
override fun onPageStarted(view:WebView, url:String, favicon:android.graphics.Bitmap?) {
if (view !== panelWebView) return
hasWebError = false
view.setVisibility(View.VISIBLE)
view.setAlpha(0f)
webViewErrorDescription = null
webViewIsLoading = true
}
override fun onReceivedError(view:WebView, request:android.webkit.WebResourceRequest, error:android.webkit.WebResourceError) {
if (request.isForMainFrame) showPrinterWebLoadError(view, error.description?.toString() ?: "Page load failed")
}
override fun onReceivedError(view:WebView, errorCode:Int, description:String, failingUrl:String) {
if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) showPrinterWebLoadError(view, description)
}
override fun onReceivedHttpError(view:WebView, request:android.webkit.WebResourceRequest, response:android.webkit.WebResourceResponse) {
if (request.isForMainFrame) showPrinterWebLoadError(view, "HTTP ${response.statusCode}: ${response.reasonPhrase}")
}
override fun onPageFinished(view:WebView, url:String) {
if (view === panelWebView && !hasWebError)
{
view.setVisibility(View.VISIBLE)
view.animate().alpha((1).toFloat()).setDuration(150).start()
webViewIsLoading = false
}
}
override fun onRenderProcessGone(view:WebView, detail:android.webkit.RenderProcessGoneDetail):Boolean {
if (view === panelWebView) showPrinterWebLoadError(view, "The printer page renderer stopped.")
return true
}
private fun showPrinterWebLoadError(view:WebView, description:String) {
if (view !== panelWebView || hasWebError) return
hasWebError = true
webViewErrorDescription = description
webViewIsLoading = false
view.animate().alpha((0).toFloat()).setDuration(150).withEndAction {
    if (view === panelWebView) view.setVisibility(View.GONE)
}.start()
}
})
panelWebViewContainer!!.addView(webView, 0, FrameLayout.LayoutParams(
ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
webViewIsLoading = true
hasWebError = false
configuredWebHost?.let { webView.loadUrl(it) }
}
catch (error:Exception) {
Log.w("BedFragment", "Could not open printer web interface", error)
hasWebError = true
webViewErrorDescription = getContext()?.getString(R.string.PrinterWebOpenFailed)
webViewIsLoading = false
}
}
@SuppressLint("SetJavaScriptEnabled")
private fun configurePrinterWebView(webView:WebView) {
// Printer dashboards need JavaScript, but must stay on the user-configured printer origin.
 val settings = webView.getSettings()
settings.setJavaScriptEnabled(true)
settings.setDomStorageEnabled(true)
settings.setJavaScriptCanOpenWindowsAutomatically(false)
settings.setSupportMultipleWindows(false)
settings.setAllowFileAccess(false)
settings.setAllowContentAccess(false)
}
private fun isAllowedPrinterWebUrl(targetUrl:String):Boolean {
return PrinterWebOriginPolicy.allows(configuredWebHost, targetUrl)
}
private fun destroyPrinterWebView() {
workspaceController.clearPanel(WorkspaceOverlayPanel.PRINTER_WEB)
val webView = panelWebView
panelWebView = null
hasWebError = false
webViewErrorDescription = null
webViewIsLoading = false
if (webView != null)
{
webView.stopLoading()
if (panelWebViewContainer != null) panelWebViewContainer!!.removeView(webView)
webView.destroy()
}
if (panelWebViewContainer != null) panelWebViewContainer!!.setVisibility(View.GONE)
}
override fun onPause() {
super.onPause()
destroyPrinterWebView()
// Free render meshes while Android hosts another activity (for example, the file
 // picker). Keep the native model; GLRenderer recreates the mesh buffers on demand.
  glView!!.queueEvent({
      // The current model stays in memory while backgrounded; cold starts begin cleanly.
      if (isLargeModelInMemory(glView!!.renderer.model)) warnLargeModelLimitOnce()
      glView!!.renderer.releaseMeshResources()
  })
glView!!.onPause()
}
private fun updateProfileStatus() {
val context = getContext() ?: return
if (SliceBeam.CONFIG == null) return
val printer = SliceBeam.CONFIG!!.findPrinter(SliceBeam.CONFIG!!.presets!!.get("printer"))
val unavailable = context.getString(R.string.WorkspaceSettingsUnknown)
val printerName = if (printer != null) printer.title else unavailable
var nozzle = if (printer != null) printer.get("printer_variant") else null
if (TextUtils.isEmpty(nozzle) && printer != null) nozzle = printer.get("nozzle_diameter")
if (TextUtils.isEmpty(nozzle)) nozzle = unavailable
val projectSlots = TemporaryProjectSettings.slots()
val filament = if (!projectSlots.isNullOrEmpty()) {
    val materials = projectSlots.mapNotNull { it.type?.takeIf(String::isNotBlank) }.distinct()
    context.getString(R.string.WorkspaceProjectFilaments,
        materials.joinToString("/"), projectSlots.size)
} else SliceBeam.CONFIG!!.presets!!.get("filament")
val process = SliceBeam.CONFIG!!.presets!!.get("print")
profileStatusTitle = printerName ?: unavailable
profileStatusSummary = context.getString(
R.string.WorkspaceProfileStatusSummary,
nozzle,
if (TextUtils.isEmpty(filament)) unavailable else filament)
profileStatusDescription = context.getString(
R.string.WorkspaceProfilesStatusDescription,
printerName, nozzle, if (TextUtils.isEmpty(filament)) unavailable else filament,
if (TextUtils.isEmpty(process)) unavailable else process)
}
fun refreshProfileStatus() {
updateProfileStatus()
updatePrinterWebViewAvailability()
}
private fun restoreSession() {
if (sessionRestored || getContext() == null) return
sessionRestored = true
// Previous session files must never load implicitly after an app restart.
 // Printer and profile selections are persisted independently.
 Prefs.getPrefs()!!.edit().putInt(SESSION_PLATE_COUNT, 0).apply()
}
@SuppressLint("SetJavaScriptEnabled")
override fun onCreateView(ctx:Context):View {
workspaceFontScale = ctx.resources.configuration.fontScale
if (platesModels.isEmpty()) platesModels.add(Model())
restoreSession()
glView = GLView(ctx)
rendererAdapter = RendererAdapter(ctx, glView!!)
glView!!.onPrimeTowerTap = { showPrimeTowerPositionDialogForCurrentPlate() }
glView!!.renderer.model = currentModel
resetModelHistory()
glView!!.renderer.setGCodeViewer(gCodeResult)
overlayLayout = FrameLayout(ctx)
updatePlateIndicator()
updateProfileStatus()
val portrait = usesCompactPortraitLayout(ctx)
compactPortraitLayout = portrait
panelWebViewContainer = FrameLayout(ctx)
panelWebViewContainer!!.setVisibility(View.GONE)
// PROJECT is the initial destination, so bind its controller before its first
// Compose composition. Otherwise FileMenu builds an empty action list until the
// user navigates away and back to this route.
menuMap.get(currentMenuSlot).onSetBed(this)
contentView = createWorkspaceContent(ctx, portrait)
overlayLayout!!.addView(contentView, FrameLayout.LayoutParams(
ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
return overlayLayout!!
}
override fun onConfigurationChanged(newConfig:Configuration) {
super.onConfigurationChanged(newConfig)
val ctx = getContext()
if (ctx == null || overlayLayout == null || glView == null) return
val portrait = usesCompactPortraitLayout(ctx)
val fontScale = ctx.resources.configuration.fontScale
val fontScaleChanged = fontScale != workspaceFontScale
workspaceFontScale = fontScale
if (portrait == compactPortraitLayout && !fontScaleChanged) {
    filamentPaletteRevision++
    return
}
if (workspaceController.isNavigationTransitionInProgress)
{
overlayLayout!!.postDelayed({ onConfigurationChanged(newConfig) }, 300)
return
}
syncCurrentModel()
rebuildAdaptiveLayout(ctx, portrait)
}
private fun onMenuNavigationSelected(bottomNavigation: Boolean, slot: Int) {
    if (slot !in WorkspaceDestination.values().indices || workspaceController.isNavigationTransitionInProgress) return
    if (slot == currentMenuSlot) {
        if (bottomNavigation) setPortraitToolPanelExpanded(!menuToolsExpanded)
        return
    }
    val routeChanged = slot != workspaceController.selectedDestinationSlot
    menuMap.get(slot).onSetBed(this)
    // Keep route, selected tool and transient workspace surfaces in one state
    // transition before the Compose route transition starts.
    if (!workspaceController.navigateToDestination(slot)) return
    if (bottomNavigation && !menuToolsExpanded) setPortraitToolPanelExpanded(true)
    if (slot != WorkspaceDestination.SLICE.ordinal) {
        val previousResult = gCodeResult
        gCodeResult = null
        glView?.queueEvent {
            replaceGCodeResult(previousResult, null) { glView?.renderer?.setGCodeViewer(null) }
            glView?.requestRender()
        }
    } else {
        (menuMap.get(WorkspaceDestination.SLICE.ordinal) as SliceMenu).refreshItems(bottomNavigation)
    }
    selectMenu(slot, routeChanged)
}

private fun setPortraitToolPanelExpanded(expanded: Boolean) {
    menuToolsExpanded = expanded
}
/** Keep the model viewport clear; object actions are available from the Edit menu. */
private fun createWorkspaceContent(ctx: Context, portrait: Boolean): View {
    rendererAdapter?.let(::detachFromParent)
    return ComposeView(ctx).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent {
            BeamComposeTheme(themeRevision = workspaceThemeRevision) {
                WorkspaceShell(
                    portrait = portrait,
                    toolPanelExpanded = menuToolsExpanded,
                    toolPanelNeedsTwoRows = currentMenuSlot == WorkspaceDestination.MODEL.ordinal ||
                        currentMenuSlot == WorkspaceDestination.EDIT.ordinal ||
                        currentMenuSlot == WorkspaceDestination.CAMERA.ordinal ||
                        currentMenuSlot == WorkspaceDestination.TOOLS.ordinal,
                    toolPanelAdditionalChromeDp = if (currentMenuSlot == WorkspaceDestination.MODEL.ordinal) {
                        if (ctx.resources.configuration.fontScale >= 1.3f) 72 else 56
                    } else 0,
                    navigationItemCount = WorkspaceDestination.visibleNavigationDestinations.size,
                    menuWidthDp = WorkspaceShellLayoutPolicy.menuPanelWidthDp(
                        ctx.resources.configuration.screenWidthDp,
                        ctx.resources.configuration.fontScale,
                    ),
                    rendererView = rendererAdapter!!,
                    selectedNavigationSlot = workspaceController.selectedDestinationSlot,
                    toolsExpanded = menuToolsExpanded,
                    onNavigationSelected = { slot -> onMenuNavigationSelected(portrait, slot) },
                    activeMenuSlot = workspaceController.selectedDestinationSlot,
                    menuComposeContentForSlot = { slot ->
                        val menu = menuMap.get(slot)
                        check(menu is WorkspaceComposeBedMenu) {
                            "Workspace destination ${WorkspaceDestination.values()[slot]} must use Compose"
                        }
                        WorkspaceComposeBedMenuHost(menu, portrait)
                    },
                    onMenuTransitionSettled = { workspaceController.updateNavigationTransitionInProgress(false) },
                    projectSettingsContent = { BeamComposeTheme { TemporaryProjectSettingsBar() } },
                    profileStatusContent = {
                        BeamComposeTheme(themeRevision = profileStatusThemeRevision) {
                            WorkspaceProfileStatus(
                                title = profileStatusTitle,
                                summary = profileStatusSummary,
                                accessibilityDescription = profileStatusDescription.ifBlank {
                                    ctx.getString(R.string.WorkspaceProfilesOpenDescription)
                                },
                                onOpen = { showWorkspaceProfileSheet(ctx) },
                            )
                        }
                    },
                    filamentButtonContent = {
                        // Color painting already presents its own full filament
                        // swatches. Hiding the compact header palette during that
                        // mode avoids two competing filament rows and preserves the
                        // selected paint color as the single visible source.
                        if (workspaceController.activeToolId != "paint-mode" && !paintModeStarting) {
                            key(filamentPaletteRevision) {
                                BeamComposeTheme {
                                    WorkspaceFilamentButton(
                                        slots = Prefs.getFilamentSlots(),
                                        enabled = true,
                                        onClick = ::showWorkspaceFilamentPalette,
                                    )
                                }
                            }
                        }
                    },
                    platePickerContent = {
                        val revision = plateTabsThemeRevision
                        if (revision >= 0) BeamComposeTheme(themeRevision = revision) {
                            PlateTabs(
                                openState = platePickerOpen,
                                plateCount = plateTabCount,
                                selectedIndex = workspaceController.activePlateIndex,
                                objectCounts = plateObjectCounts,
                                thumbnails = plateThumbnails,
                                onOpen = {
                                    workspaceController.activatePanel(WorkspaceOverlayPanel.PLATE_PICKER) {
                                        platePickerOpen.value = false
                                    }
                                    platePreviewRevision++
                                    refreshPlateThumbnails()
                                },
                                onDismiss = { workspaceController.clearPanel(WorkspaceOverlayPanel.PLATE_PICKER) },
                                onSelect = { index -> if (index != currentPlateIndex) switchPlate(index) },
                                onClose = { index -> removePlate(index) },
                                onAdd = { addNewPlate() },
                            )
                        }
                    },
                    viewCubeContent = {
                        WorkspaceViewCube(
                            onFaceSelected = { face ->
                                val preset = when (face) {
                                    WorkspaceViewFace.FRONT -> CameraPreset.FRONT
                                    WorkspaceViewFace.BACK -> CameraPreset.BACK
                                    WorkspaceViewFace.RIGHT -> CameraPreset.RIGHT
                                    WorkspaceViewFace.LEFT -> CameraPreset.LEFT
                                    WorkspaceViewFace.TOP -> CameraPreset.TOP
                                    WorkspaceViewFace.BOTTOM -> CameraPreset.BOTTOM
                                }
                                (menuMap.get(WorkspaceDestination.CAMERA.ordinal) as? CameraMenu)?.let { cameraMenu ->
                                    // The cube lives in the persistent workspace header and can be
                                    // used before the Camera destination has ever been opened.
                                    cameraMenu.onSetBed(this@BedFragment)
                                    cameraMenu.selectCameraPreset(preset)
                                }
                            },
                            onCameraRotated = { x, y, z ->
                                (menuMap.get(WorkspaceDestination.CAMERA.ordinal) as? CameraMenu)?.let { cameraMenu ->
                                    cameraMenu.onSetBed(this@BedFragment)
                                    cameraMenu.selectCameraDirection(x, y, z)
                                }
                            },
                        )
                    },
                    printerWebButtonContent = {
                        BeamComposeTheme(themeRevision = printerWebThemeRevision) {
                            PrinterWebOpenButton(hostConfigured = printerWebAvailable, onOpen = ::openPrinterWebView)
                        }
                    },
                    modePanelContent = workspaceController.activeToolPanelContent,
                    modePanelSize = workspaceController.activeToolPanelSize,
                    modePanelDocked = workspaceController.activeToolPanelDocked,
                    modePanelAllowsBackgroundInteraction = workspaceController.activeToolAllowsBackgroundInteraction ||
                        workspaceController.activeToolId == "paint-mode",
                    onModePanelDismiss = { workspaceController.dismissActivePanel() },
                    transientPanelContent = workspaceController.activePanelContent,
                    snackbarController = snackbarController,
                )
            }
        }
    }
}
private fun rebuildAdaptiveLayout(ctx:Context, portrait:Boolean) {
contentView?.let(::detachFromParent)
contentView = createWorkspaceContent(ctx, portrait)
compactPortraitLayout = portrait
overlayLayout!!.addView(contentView, 0, FrameLayout.LayoutParams(
ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
glView!!.requestRender()
}
private fun selectMenu(slot: Int, routeChanged: Boolean = slot != workspaceController.selectedDestinationSlot) {
if (glView!!.renderer.resetFlattenMode())
{
glView!!.requestRender()
SliceBeam.EVENT_BUS.fireEvent(FlattenModeResetEvent())
}
val transitionGeneration = ++workspaceNavigationTransitionGeneration
workspaceController.updateNavigationTransitionInProgress(routeChanged)
currentMenuSlot = slot
if (routeChanged) {
    // Compose normally clears this guard after its route animation. Keep a bounded fallback so a
    // cancelled/replaced composition cannot leave every workspace menu permanently unclickable.
    ViewUtils.postOnMainThread({
        if (!destroyed && transitionGeneration == workspaceNavigationTransitionGeneration &&
            workspaceController.isNavigationTransitionInProgress
        ) workspaceController.updateNavigationTransitionInProgress(false)
    }, 1_000L)
}
}
fun interface ModelLoadCallback {
fun onLoaded(loadedModel:Model, firstNewObject:Int, addedObjects:Int)
fun onFailed(error:Exception) {}
fun onCancelled() {}
fun onProgress(fraction:Float):Boolean = true
fun onPartialFailures(failedFiles:Int) {}
}
private class ImportCancelledException : Exception()

private fun confirmLargeStlImport(triangles: Long): Boolean {
    val approved = java.util.concurrent.atomic.AtomicBoolean(false)
    val timedOut = java.util.concurrent.atomic.AtomicBoolean(false)
    val completed = CountDownLatch(1)
    ViewUtils.postOnMainThread {
        val ctx = getContext()
        if (destroyed || ctx == null || timedOut.get()) { completed.countDown(); return@postOnMainThread }
        showWorkspaceMessageDialog(
            title = ctx.getString(R.string.ImportLargeStlTitle),
            message = ctx.getString(R.string.ImportLargeStlMessage,
                java.text.NumberFormat.getIntegerInstance().format(triangles)),
            confirmLabel = ctx.getString(R.string.ImportLargeStlContinue),
            dismissLabel = ctx.getString(android.R.string.cancel),
            onDismiss = { completed.countDown() },
            onConfirm = { approved.set(true); completed.countDown() },
        )
    }
    if (!completed.await(2, TimeUnit.MINUTES)) {
        timedOut.set(true)
        ViewUtils.postOnMainThread {
            if (workspaceController.activePanel == WorkspaceOverlayPanel.MESSAGE_DIALOG) {
                workspaceController.dismissActivePanel()
            }
        }
        return false
    }
    return approved.get() && !destroyed
}
private data class ImportReduction(val before: Long, val after: Long)

/** All import paths pass here before the renderer sees a large mesh. A dismissal preserves it. */
private fun simplifyImportedModel(model: Model): ImportReduction? {
    val oversized = (0 until model.objectsCount).any { model.getTriangleCount(it) > IMPORT_TRIANGLE_LIMIT }
    if (!oversized || !confirmImportSimplification()) return null
    var beforeTotal = 0L
    var afterTotal = 0L
    for (index in 0 until model.objectsCount) {
        val original = model.getTriangleCount(index)
        if (original <= IMPORT_TRIANGLE_LIMIT) continue
        var current = original
        repeat(2) {
            if (current > IMPORT_TRIANGLE_LIMIT) {
                val ratio = (IMPORT_TRIANGLE_TARGET.toDouble() / current).toFloat().coerceIn(0.01f, 0.99f)
                current = model.simplify(index, ratio)
            }
        }
        if (current > IMPORT_TRIANGLE_LIMIT || current < 4) {
            throw IllegalStateException("Import could not reduce object $index below $IMPORT_TRIANGLE_LIMIT triangles")
        }
        beforeTotal += original
        afterTotal += current
    }
    return if (beforeTotal > 0L) ImportReduction(beforeTotal, afterTotal) else null
}

private fun confirmImportSimplification(): Boolean {
    importSimplifyDecision?.let { return it }
    val decision = java.util.concurrent.atomic.AtomicBoolean(false)
    val timedOut = java.util.concurrent.atomic.AtomicBoolean(false)
    val completed = CountDownLatch(1)
    ViewUtils.postOnMainThread {
        val ctx = getContext()
        if (destroyed || ctx == null || timedOut.get()) {
            completed.countDown()
            return@postOnMainThread
        }
        showWorkspaceMessageDialog(
            title = ctx.getString(R.string.ImportSimplifyTitle),
            message = ctx.getString(R.string.ImportSimplifyMessage),
            confirmLabel = ctx.getString(R.string.ImportSimplifyAccept),
            dismissLabel = ctx.getString(R.string.ImportSimplifyKeep),
            onDismiss = { decision.set(false); completed.countDown() },
            onConfirm = { decision.set(true); completed.countDown() },
        )
    }
    // Import runs off the main thread. A lost activity must not strand its worker.
    if (!completed.await(2, TimeUnit.MINUTES)) {
        timedOut.set(true)
        ViewUtils.postOnMainThread {
            if (workspaceController.activePanel == WorkspaceOverlayPanel.MESSAGE_DIALOG) {
                workspaceController.dismissActivePanel()
            }
        }
    }
    val approved = !timedOut.get() && decision.get()
    importSimplifyDecision = approved
    return approved
}

private fun reportImportReduction(reduction: ImportReduction?) {
    if (reduction != null) {
        ViewUtils.postOnMainThread({
            val formatter = java.text.NumberFormat.getIntegerInstance()
            SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(
                SnackbarsLayout.Type.INFO, R.string.MenuFileOpenSimplifiedLoaded,
                formatter.format(reduction.before), formatter.format(reduction.after)))
        })
    }
}
private fun addNewPlate() {
platePreviewRevision++
platesModels.add(Model())
while (projectPlateIds.size < platesModels.size - 1) projectPlateIds.add(0)
projectPlateIds.add(0)
totalPlates = platesModels.size
switchPlate(platesModels.size - 1)
updatePlateIndicator()
}
private fun removePlate(plateIndex:Int) {
if (plateIndex !in platesModels.indices) return
val hasModels = platesModels[plateIndex]?.objectsCount?.let { it > 0 } ?: (getProjectPlateId(plateIndex) > 0)
if (hasModels) {
    val ctx = getContext() ?: return
    showWorkspaceMessageDialog(
        title = null,
        message = ctx.getString(R.string.PlateDeleteConfirm),
        confirmLabel = ctx.getString(android.R.string.ok),
        dismissLabel = ctx.getString(android.R.string.cancel),
    ) { removePlateConfirmed(plateIndex) }
    return
}
removePlateConfirmed(plateIndex)
}
private fun removePlateConfirmed(plateIndex:Int) {
platePreviewRevision++
val closeDecision = PlateTabsPolicy.afterClose(platesModels.size, currentPlateIndex, plateIndex) ?: return
if (closeDecision.resetOnlyPlate)
{
TemporaryProjectSettings.clear()
val removed = platesModels.set(0, Model())
projectPlateIds.clear()
projectPlateIds.add(0)
currentPlateIndex = 0
currentProjectFile = null
sourceProjectPlateCount = 1
totalPlates = 1
switchPlate(0, false)
if (removed != null) glView!!.queueEvent(Runnable { removed.release() })
updatePlateIndicator()
return
}
val removed = platesModels.removeAt(plateIndex)
if (plateIndex < projectPlateIds.size) projectPlateIds.remove(plateIndex)
totalPlates = platesModels.size
currentPlateIndex = closeDecision.selectedIndex
// Re-select through the common path so any in-flight lazy load is invalidated and
// its progress indicator is dismissed after an index shift.
switchPlate(currentPlateIndex, false)
if (removed != null) glView!!.queueEvent(Runnable { removed.release() })
updatePlateIndicator()
if (platesModels.all { it != null && it.objectsCount == 0 }) TemporaryProjectSettings.clear()
}
/**
 * Hand the renderer every plate except the active one, with grid offsets relative to the
 * active plate (which always renders at the origin), so all plates are visible at once.
 */
 private fun pushPlatesToRenderer() {
val active = currentPlateIndex
val models = java.util.ArrayList(platesModels)
glView!!.queueEvent(object:Runnable {
override fun run() {
val bed = glView!!.renderer.bed
if (bed == null)
{
ViewUtils.postOnMainThread({ glView!!.queueEvent(this) })
return
}
val inactive = java.util.ArrayList<Model>()
val offsets = java.util.ArrayList<DoubleArray>()
if (models.size > 1)
{
val cols = computePlateCols(models.size)
val activeOrigin = DoubleArray(2)
val tmp = DoubleArray(2)
computePlateOrigin(active, cols, bed.getVolumeMin()!!, bed.getVolumeMax()!!, activeOrigin)
for (i in 0 until models.size)
{
if (i == active || models.get(i) == null) continue
computePlateOrigin(i, cols, bed.getVolumeMin()!!, bed.getVolumeMax()!!, tmp)
val inactiveModel = models.get(i) ?: continue
inactive.add(inactiveModel)
offsets.add(doubleArrayOf(tmp[0] - activeOrigin[0], tmp[1] - activeOrigin[1]))
}
}
glView!!.renderer.setCurrentPlateIndex(active)
glView!!.renderer.setInactivePlates(inactive, offsets)
glView!!.requestRender()
}
})
}
private fun switchPlate(newPlateIndex:Int, saveCurrent:Boolean = true, loadCallback:ModelLoadCallback? = null) {
if (destroyed || modelLoadInProgress || newPlateIndex < 0 || newPlateIndex >= platesModels.size) return
val loadGeneration = ++plateLoadGeneration
SliceBeam.EVENT_BUS.fireEvent(NeedDismissSnackbarEvent("plate-load"))
// Save current model only if requested
 if (saveCurrent)
{
syncCurrentModel()
}
currentPlateIndex = newPlateIndex
updatePlateIndicator()
var nextModel = platesModels.get(currentPlateIndex)
// Lazy load from 3MF project if it's null (not yet loaded)
 val sourcePlateId = getProjectPlateId(currentPlateIndex)
if (nextModel == null && currentProjectFile != null && sourcePlateId > 0)
{
val requestedPlate = currentPlateIndex
val projectFile = currentProjectFile ?: return
SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(
SnackbarsLayout.Type.LOADING, R.string.MenuFileOpenFileLoading).tag("plate-load"))
glView!!.queueEvent({ glView!!.renderer.clearSelectionForPlateSwitch()
glView!!.renderer.model = null
glView!!.renderer.resetGlModels()
glView!!.requestRender() })
pushPlatesToRenderer()
plateLoadExecutor.execute({ if (loadGeneration != plateLoadGeneration) return@execute
val projectBed = DoubleArray(2)
val haveProjectBed = readProjectBedSize(projectFile, projectBed)
val loaded:Model
val reduction:ImportReduction?
try
{
loaded = Model(projectFile, sourcePlateId)
reduction = try { simplifyImportedModel(loaded) } catch (e: Exception) { loaded.release(); throw e }
}
catch (e:Exception) {
android.util.Log.e("BedFragment", "Failed to load plate " + (requestedPlate + 1), e)
ViewUtils.postOnMainThread({
    if (destroyed) return@postOnMainThread
    if (loadGeneration == plateLoadGeneration && requestedPlate == currentPlateIndex && getContext() != null) {
        SliceBeam.EVENT_BUS.fireEvent(NeedDismissSnackbarEvent("plate-load"))
        if (loadCallback != null) loadCallback.onFailed(e)
        else SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.MenuFileOpenFileFailed))
    } else {
        loadCallback?.onCancelled()
    }
})
return@execute
}
synchronized (pendingPlateModelsLock) {
if (destroyed || loadGeneration != plateLoadGeneration)
{
loaded.release()
return@execute
}
pendingPlateModels.add(loaded)
}
glView!!.queueEvent(object:Runnable {
override fun run() {
synchronized (pendingPlateModelsLock) {
if ((destroyed || loadGeneration != plateLoadGeneration || requestedPlate != currentPlateIndex
|| requestedPlate >= platesModels.size
|| currentProjectFile == null
|| !projectFile!!.equals(currentProjectFile)))
{
pendingPlateModels.remove(loaded)
loaded.release()
if (!destroyed) ViewUtils.postOnMainThread { loadCallback?.onCancelled() }
return
}
val bed = glView!!.renderer.bed
if (bed == null)
{
ViewUtils.postOnMainThread({ glView!!.queueEvent(this) })
return
}
val bedW = if (haveProjectBed) projectBed[0] else bed.getVolumeMax()!!.x - bed.getVolumeMin()!!.x
val bedD = if (haveProjectBed) projectBed[1] else bed.getVolumeMax()!!.y - bed.getVolumeMin()!!.y
val origin = DoubleArray(2)
computePlateOrigin(sourcePlateId - 1, computePlateCols(sourceProjectPlateCount), bedW, bedD, origin)
if (origin[0] != 0.0 || origin[1] != 0.0)
{
for (i in 0 until loaded.objectsCount)
{
loaded.translate(i, -origin[0], -origin[1], (0).toDouble())
}
loaded.resetBoundingBox()
}
pendingPlateModels.remove(loaded)
platesModels.set(requestedPlate, loaded)
platePreviewRevision++
updatePlateIndicator()
glView!!.renderer.model = loaded
glView!!.renderer.resetGlModels()
resetModelHistory()
glView!!.requestRender()
SliceBeam.EVENT_BUS.fireEvent(NeedDismissSnackbarEvent("plate-load"))
if (loadCallback != null)
{
loadCallback.onLoaded(loaded, 0, loaded.objectsCount)
}
reportImportReduction(reduction)
}
}
}) })
return
}
// If it's already loaded or freshly created fallback
 if (nextModel == null)
{
nextModel = Model()
platesModels.set(currentPlateIndex, nextModel)
}
val finalNext = nextModel
android.util.Log.e("BedFragment", "Queueing setModel with finalNext pointer: " + (if (finalNext != null) finalNext.pointer else "null"))
glView!!.queueEvent({ android.util.Log.e("BedFragment", "Executing setModel with finalNext pointer: " + (if (finalNext != null) finalNext.pointer else "null"))
glView!!.renderer.clearSelectionForPlateSwitch()
glView!!.renderer.setCurrentPlateIndex(currentPlateIndex)
glView!!.renderer.model = finalNext
resetModelHistory()
glView!!.requestRender() })
pushPlatesToRenderer()
if (loadCallback != null)
{
    // Match the lazy-load path: completion is reported only after the renderer has
    // switched to the requested plate, and always from the GL thread.
    glView!!.queueEvent({ loadCallback.onLoaded(finalNext, 0, finalNext.objectsCount) })
}
}
private fun updatePlateIndicator() {
ViewUtils.postOnMainThread({ if (destroyed || getContext() == null) return@postOnMainThread
filamentPaletteRevision++
plateTabCount = platesModels.size
if (platesModels.isNotEmpty()) currentPlateIndex = currentPlateIndex.coerceIn(0, platesModels.size - 1)
plateObjectCounts = platesModels.map { it?.objectsCount ?: -1 }
})
}
/** Import commits happen on the GL thread after the earlier placeholder count was published. */
private fun onModelImportCommitted() {
    platePreviewRevision++
    // Import replaces or extends the active model outside the user's edit history.
    // Seed history from the committed model so the first subsequent edit can be undone
    // without restoring the empty workspace snapshot captured during fragment setup.
    resetModelHistory()
    updatePlateIndicator()
    // Menu action enablement (for example Project → Export 3MF) is derived from
    // the renderer model. Notify the visible Compose menu after the GL-thread
    // import commit so an initially open menu cannot retain its empty-state actions.
    SliceBeam.EVENT_BUS.fireEvent(ru.ytkab0bp.slicebeam.events.ObjectsListChangedEvent())
}
/** Keep import-time adjustments (such as auto-orientation) as the new undo baseline. */
internal fun rebaseModelHistoryAfterImportAdjustment() {
    resetModelHistory()
}
/** Reuse the native model bounds to draw tiny XY footprints; never render a second 3D scene. */
private fun refreshPlateThumbnails() {
    val viewport = glView ?: return
    val revision = platePreviewRevision
    if (platePreviewBuiltRevision == revision && plateThumbnails.size == platesModels.size) return
    val models = platesModels.toList()
    viewport.queueEvent {
        val bed = viewport.renderer.bed
        val minimum = bed?.getVolumeMin()
        val maximum = bed?.getVolumeMax()
        val width = if (minimum != null && maximum != null) maximum.x - minimum.x else 0.0
        val depth = if (minimum != null && maximum != null) maximum.y - minimum.y else 0.0
        val previews = models.map { model ->
            val footprints = ArrayList<PlateFootprint>()
            if (model != null && minimum != null && width > 0.0 && depth > 0.0) try {
                val lower = Vec3d()
                val upper = Vec3d()
                val previewLimit = if (models.size > 6) 6 else 24
                for (objectIndex in 0 until model.objectsCount.coerceAtMost(previewLimit)) {
                    // A tiny picker image needs only the transformed extent, not a full mesh walk.
                    model.getBoundingBoxApprox(objectIndex, lower, upper)
                    val left = ((lower.x - minimum.x) / width).toFloat().coerceIn(0f, 1f)
                    val bottom = ((lower.y - minimum.y) / depth).toFloat().coerceIn(0f, 1f)
                    val right = ((upper.x - minimum.x) / width).toFloat().coerceIn(0f, 1f)
                    val top = ((upper.y - minimum.y) / depth).toFloat().coerceIn(0f, 1f)
                    if (right > left && top > bottom) footprints.add(PlateFootprint(left, bottom, right - left, top - bottom))
                }
            } catch (error: Exception) {
                Log.w("BedFragment", "Plate thumbnail unavailable", error)
            }
            PlateThumbnail(footprints)
        }
        ViewUtils.postOnMainThread {
            if (destroyed || revision != platePreviewRevision || models.size != platesModels.size) return@postOnMainThread
            plateThumbnails = previews
            platePreviewBuiltRevision = revision
        }
    }
}
@Throws(Slic3rRuntimeError::class)
@JvmOverloads fun loadModel(f:File, preserveProjectLayout:Boolean = false, plateCount:Int = 1, callback:ModelLoadCallback? = null, stepOptions:StepImportOptions? = null, autoOrientImportedModels:Boolean = false) {
if (destroyed || modelLoadInProgress)
{
if (callback != null)
    ViewUtils.postOnMainThread({ callback?.onFailed(
IllegalStateException("Ein anderes Modell wird gerade geladen.")) })
return
}
modelLoadInProgress = true
val importGeneration = ++modelImportGeneration
platePreviewRevision++
importSimplifyDecision = null
plateLoadGeneration++
Thread({ try
{
StlImportPreflight.largeTriangleCount(f, IMPORT_TRIANGLE_LIMIT.toLong())?.let {
    if (!confirmLargeStlImport(it)) throw ImportCancelledException()
}
loadModelBlocking(f, preserveProjectLayout, plateCount, importGeneration, callback, stepOptions, autoOrientImportedModels)
}
catch (e:Exception) {
if (e is ImportCancelledException || e is java.util.concurrent.CancellationException) {
    ViewUtils.postOnMainThread { if (!destroyed && importGeneration == modelImportGeneration) callback?.onCancelled() }
    return@Thread
}
Log.e("BedFragment", "Could not load model", e)
if (plateCount > 1) resetFailedMultiPlateImport(importGeneration)
ViewUtils.postOnMainThread({ if (destroyed || importGeneration != modelImportGeneration) return@postOnMainThread
if (callback == null) SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.MenuFileOpenFileFailed))
if (callback != null) callback?.onFailed(e) })
}
finally
{
modelLoadInProgress = false
} }, "OrcaModelLoader").start()
}
private fun resetFailedMultiPlateImport(importGeneration:Int) {
ViewUtils.postOnMainThread({ if (destroyed || importGeneration != modelImportGeneration) return@postOnMainThread
val failedModels = java.util.ArrayList(platesModels)
val emptyModel = Model()
platesModels.clear()
platesModels.add(emptyModel)
projectPlateIds.clear()
projectPlateIds.add(0)
currentPlateIndex = 0
totalPlates = 1
currentProjectFile = null
sourceProjectPlateCount = 1
updatePlateIndicator()
glView!!.queueEvent({ glView!!.renderer.clearSelectionForPlateSwitch()
glView!!.renderer.releaseMeshResources()
glView!!.renderer.setCurrentPlateIndex(0)
glView!!.renderer.model = emptyModel
for (model in failedModels)
{
if (model != null && model !== emptyModel) model.release()
}
glView!!.requestRender() }) })
}
@Throws(Slic3rRuntimeError::class)
private fun loadModelBlocking(f:File, preserveProjectLayout:Boolean, plateCount:Int, importGeneration:Int, callback:ModelLoadCallback?, stepOptions:StepImportOptions?, autoOrientImportedModels:Boolean) {
if (destroyed) return
if (importGeneration != modelImportGeneration) return
this.totalPlates = plateCount
if (plateCount > 1)
{
this.currentProjectFile = f
this.sourceProjectPlateCount = plateCount
// Re-initialize for new 3MF project
 val oldModels = java.util.ArrayList(platesModels)
platesModels.clear()
projectPlateIds.clear()
glView!!.queueEvent({ glView!!.renderer.clearSelectionForPlateSwitch()
glView!!.renderer.model = null
glView!!.renderer.resetGlModels()
for (m in oldModels)
{
if (m != null) m.release()
} })
for (i in 0 until plateCount)
{
platesModels.add(null)
projectPlateIds.add(i + 1)
}
this.currentPlateIndex = 0
// Load only the active plate. A multi-plate project can contain several large meshes;
 // keeping every plate's native model alive during import needlessly multiplies the
 // peak memory use. Other plates are loaded by switchPlate() when the user selects one.
 // The renderer still displays the complete plate grid while unloaded plates remain
 // empty, and their models keep the original 3MF plate coordinates until normalized.
 val cols = computePlateCols(plateCount)
val fileBed = DoubleArray(2)
val haveFileBed = readProjectBedSize(f, fileBed)
// Let the normal load-failure path report a corrupt or unsupported active plate.
 // Replacing it with an empty model made a failed multi-plate import look successful.
 val firstPlate = Model(f, 1)
 val reduction = try { simplifyImportedModel(firstPlate) } catch (e: Exception) { firstPlate.release(); throw e }
 platesModels.set(0, firstPlate)
platePreviewRevision++
val finalCallback = callback
glView!!.queueEvent(object:Runnable {
override fun run() {
val bed = glView!!.renderer.bed
if (bed == null)
{
ViewUtils.postOnMainThread({ glView!!.queueEvent(this) })
return
}
val bedW = if (haveFileBed) fileBed[0] else bed.getVolumeMax()!!.x - bed.getVolumeMin()!!.x
val bedD = if (haveFileBed) fileBed[1] else bed.getVolumeMax()!!.y - bed.getVolumeMin()!!.y
val origin = DoubleArray(2)
val first = platesModels.get(0)!!
computePlateOrigin(0, cols, bedW, bedD, origin)
if (origin[0] != 0.0 || origin[1] != 0.0)
{
for (o in 0 until first.objectsCount)
{
first.translate(o, -origin[0], -origin[1], (0).toDouble())
}
}
first.resetBoundingBox()
glView!!.renderer.model = first
glView!!.renderer.resetGlModels()
onModelImportCommitted()
if (finalCallback != null)
{
finalCallback.onLoaded(first, 0, first.objectsCount)
}
reportImportReduction(reduction)
}
})
pushPlatesToRenderer()
}
else
{
// Loading an STL or 1-plate 3MF. Just load into current plate.
 if (platesModels.size <= 1)
{
currentProjectFile = null
sourceProjectPlateCount = 1
projectPlateIds.clear()
}
if (platesModels.isEmpty())
{
platesModels.add(Model())
currentPlateIndex = 0
}
if (platesModels.get(currentPlateIndex) == null)
{
platesModels.set(currentPlateIndex, Model())
}
loadModelInternal(f, preserveProjectLayout, callback, stepOptions, autoOrientImportedModels)
}
updatePlateIndicator()
}
@Throws(Slic3rRuntimeError::class)
private fun loadModelInternal(
    f: File,
    preserveProjectLayout: Boolean,
    callback: ModelLoadCallback?,
    stepOptions: StepImportOptions?,
    autoOrientImportedModels: Boolean,
) {
    val imported = if (stepOptions != null)
        Model(f, currentPlateIndex + 1, stepOptions, callback?.let { progress -> ImportProgressCallback { fraction -> progress.onProgress(fraction) } })
    else if (callback != null && f.name.endsWith(".stl", ignoreCase = true))
        Model(f, currentPlateIndex + 1, ImportProgressCallback { fraction -> callback.onProgress(fraction) })
    else Model(f, currentPlateIndex + 1)
    val reduction = try { simplifyImportedModel(imported) } catch (e: Exception) { imported.release(); throw e }
    loadImportedModelOnRenderer(imported, reduction, preserveProjectLayout, callback, autoOrientImportedModels, f)
}

/** Import a multi-file STL selection as one model batch so the native nest runs once. */
@JvmOverloads
fun loadModelBatch(files:List<File>, callback:ModelLoadCallback? = null, autoOrientImportedModels:Boolean = true) {
    if (files.isEmpty()) {
        callback?.onFailed(IllegalArgumentException("No STL files selected"))
        return
    }
    if (destroyed || modelLoadInProgress) {
        callback?.onFailed(IllegalStateException("Ein anderes Modell wird gerade geladen."))
        return
    }
    modelLoadInProgress = true
    val importGeneration = ++modelImportGeneration
    platePreviewRevision++
    importSimplifyDecision = null
    plateLoadGeneration++
    Thread({
        val batch = Model()
        var failedFiles = 0
        var reducedBefore = 0L
        var reducedAfter = 0L
        try {
            files.forEachIndexed { fileIndex, file ->
                if (destroyed || importGeneration != modelImportGeneration) throw ImportCancelledException()
                StlImportPreflight.largeTriangleCount(file, IMPORT_TRIANGLE_LIMIT.toLong())?.let {
                    if (!confirmLargeStlImport(it)) throw ImportCancelledException()
                }
                var source:Model? = null
                try {
                    source = Model(file, currentPlateIndex + 1, ImportProgressCallback { fraction ->
                        callback?.onProgress((fileIndex + fraction.coerceIn(0f, 1f)) / files.size)
                            ?: true
                    })
                    val reduction = simplifyImportedModel(source)
                    if (reduction != null) {
                        reducedBefore += reduction.before
                        reducedAfter += reduction.after
                    }
                    repeat(source.objectsCount) { batch.addObject(source, it) }
                } catch (error:Exception) {
                    if (error is ImportCancelledException || error is java.util.concurrent.CancellationException) throw error
                    failedFiles++
                    Log.e("BedFragment", "Could not import STL batch item ${file.name}", error)
                } finally {
                    source?.release()
                }
            }
            if (batch.objectsCount == 0) throw IllegalStateException("No STL file in the selection could be imported.")
            if (destroyed || importGeneration != modelImportGeneration) throw ImportCancelledException()
            val reduction = if (reducedBefore > 0L) ImportReduction(reducedBefore, reducedAfter) else null
            loadImportedModelOnRenderer(batch, reduction, false, object:ModelLoadCallback {
                override fun onLoaded(loadedModel:Model, firstNewObject:Int, addedObjects:Int) {
                    callback?.onPartialFailures(failedFiles)
                    callback?.onLoaded(loadedModel, firstNewObject, addedObjects)
                }
                override fun onFailed(error:Exception) = callback?.onFailed(error) ?: Unit
                override fun onCancelled() = callback?.onCancelled() ?: Unit
                override fun onProgress(fraction:Float):Boolean = callback?.onProgress(fraction) ?: true
            }, autoOrientImportedModels, null)
        } catch (error:Exception) {
            batch.release()
            if (error is ImportCancelledException || error is java.util.concurrent.CancellationException) {
                ViewUtils.postOnMainThread { if (!destroyed && importGeneration == modelImportGeneration) callback?.onCancelled() }
            } else {
                Log.e("BedFragment", "Could not load STL batch", error)
                ViewUtils.postOnMainThread { if (!destroyed && importGeneration == modelImportGeneration) callback?.onFailed(error) }
            }
        } finally {
            modelLoadInProgress = false
        }
    }, "OrcaStlBatchLoader").start()
}

private fun loadImportedModelOnRenderer(
    imported:Model,
    reduction:ImportReduction?,
    preserveProjectLayout:Boolean,
    callback:ModelLoadCallback?,
    autoOrientImportedModels:Boolean,
    sourceFile:File?,
) {

    glView!!.queueEvent(object : Runnable {
        override fun run() {
            val viewport = glView ?: run { imported.release(); return }
            val bed = viewport.renderer.bed
            if (bed == null) {
                ViewUtils.postOnMainThread { if (!destroyed) viewport.queueEvent(this) else imported.release() }
                return
            }

            val existing = platesModels.getOrNull(currentPlateIndex)
            // Preserve authored plate coordinates when opening a project onto an empty bed.
            // If a 3MF is imported into an already populated workspace, it is an additional
            // model import: run it through the same orient/arrange/overflow path as STL/STEP so
            // it cannot overlap existing objects or end up at an arbitrary source coordinate.
            val preserveLoadedProjectLayout = preserveProjectLayout &&
                (existing == null || existing.objectsCount == 0)
            if (preserveLoadedProjectLayout) {
                val selectedModel = existing ?: Model()
                if (selectedModel.objectsCount > 0) {
                    val firstNewObject = selectedModel.objectsCount
                    repeat(imported.objectsCount) { selectedModel.addObject(imported, it) }
                    imported.release()
                    selectedModel.resetBoundingBox()
                    viewport.renderer.model = selectedModel
                    viewport.renderer.resetGlModels()
                    onModelImportCommitted()
                    callback?.onLoaded(selectedModel, firstNewObject, selectedModel.objectsCount - firstNewObject)
                } else {
                    if (existing != null && existing !== imported) existing.release()
                    if (totalPlates > 1) {
                        val fileBed = DoubleArray(2)
                        val haveProjectBed = sourceFile?.let { readProjectBedSize(it, fileBed) } ?: false
                        val bedW = if (haveProjectBed) fileBed[0] else bed.getVolumeMax()!!.x - bed.getVolumeMin()!!.x
                        val bedD = if (haveProjectBed) fileBed[1] else bed.getVolumeMax()!!.y - bed.getVolumeMin()!!.y
                        val origin = DoubleArray(2)
                        computePlateOrigin(currentPlateIndex, computePlateCols(totalPlates), bedW, bedD, origin)
                        if (origin[0] != 0.0 || origin[1] != 0.0)
                            repeat(imported.objectsCount) { imported.translate(it, -origin[0], -origin[1], 0.0) }
                        imported.resetBoundingBox()
                    }
                    platesModels.set(currentPlateIndex, imported)
                    viewport.renderer.model = imported
                    viewport.renderer.resetGlModels()
                    onModelImportCommitted()
                    callback?.onLoaded(imported, 0, imported.objectsCount)
                }
                reportImportReduction(reduction)
                viewport.requestRender()
                return
            }

            // Orient and put each incoming object on the bed before packing. Previously the
            // post-import callback rotated models after Arrange had already reserved their old
            // footprint, which could leave an apparently oversized selection box or move the
            // geometry outside its packed location.
            val sourceCount = imported.objectsCount
            val min = Vec3d()
            val max = Vec3d()
            val translation = Vec3d()
            val footprintAreas = DoubleArray(sourceCount)
            for (index in 0 until sourceCount) {
                if (autoOrientImportedModels || preserveProjectLayout) imported.autoOrient(index)
                imported.getTranslation(index, translation)
                imported.getBoundingBoxExact(index, min, max)
                footprintAreas[index] = (max.x - min.x) * (max.y - min.y)
                // Imported STL vertices may keep a distant source-CAD origin. Normalize the
                // geometry center before packing so that translation is not applied twice.
                imported.translate(index, -translation.x, -translation.y,
                    -translation.z + (max.z - min.z) / 2.0)
            }

            // A sequence of one-object placements can lock an early greedy choice even when
            // the complete incoming set would fit more cleanly as a group. On an empty plate,
            // first try a bounded batch nest on a temporary model so failure cannot change the
            // fallback import transforms. Keep this attempt small in both object and triangle
            // count; large imports retain the predictable incremental overflow path below.
            val currentPlateIsEmpty = existing == null || existing.objectsCount == 0
            val batchTriangleCount = (0 until sourceCount).sumOf { imported.getTriangleCount(it).toLong() }
            val bedMin = bed.getVolumeMin()
            val bedMax = bed.getVolumeMax()
            val bedBoundsArea = if (bedMin != null && bedMax != null)
                (bedMax.x - bedMin.x) * (bedMax.y - bedMin.y) else 0.0
            val batchFootprintArea = footprintAreas.sum()
            val shouldTryJointBatch = currentPlateIsEmpty && sourceCount in 2..24 &&
                batchTriangleCount in 1..100_000 && bedBoundsArea > 0.0 &&
                batchFootprintArea <= bedBoundsArea * 0.70
            if (shouldTryJointBatch) {
                val jointCandidate = Model()
                var accepted = false
                try {
                    repeat(sourceCount) { jointCandidate.addObject(imported, it) }
                    accepted = jointCandidate.objectsCount == sourceCount &&
                        bed.arrangeCompact(jointCandidate) &&
                        isModelInsideBuildVolume(jointCandidate, bed) &&
                        (0 until sourceCount).none { hasMeshIntersectionForObject(jointCandidate, it) }
                    if (accepted) {
                        jointCandidate.resetBoundingBox()
                        existing?.takeIf { it !== jointCandidate }?.release()
                        platesModels.set(currentPlateIndex, jointCandidate)
                        imported.release()
                        viewport.renderer.model = jointCandidate
                        viewport.renderer.resetGlModels()
                        onModelImportCommitted()
                        callback?.onLoaded(jointCandidate, 0, sourceCount)
                        reportImportReduction(reduction)
                        viewport.requestRender()
                        return
                    }
                } finally {
                    if (!accepted) jointCandidate.release()
                }
            }

            val originalPlateCount = platesModels.size
            var target = existing ?: Model()
            var targetPlateIndex: Int? = currentPlateIndex
            if (existing == null) platesModels.set(currentPlateIndex, target)
            val originalTargetObjectCount = target.objectsCount
            val importPlacementOrder = ModelImportPackingOrder.largestFirst(footprintAreas.toList())
            var finalFirstNew = originalTargetObjectCount
            var finalAddedCount = 0
            var finalTarget: Model? = null
            val automaticallyCreated = ArrayList<Model>()
            var objectsTooLargeForBed = 0

            // Place the largest incoming parts first. Existing objects are passed to libnest2d
            // as fixed obstacles so each placement is O(n), does not copy/repack the full plate,
            // and never shifts a model the user already positioned.
            for (sourceIndex in importPlacementOrder) {
                var destinationIndex = -1
                var destinationPlateIndex: Int? = null
                var destinationPlate: Model? = null

                fun tryPlaceOn(plate:Model, plateIndex:Int?):Boolean {
                    val objectIndex = plate.objectsCount
                    plate.addObject(imported, sourceIndex)
                    val placed = plate.objectsCount == objectIndex + 1 &&
                        bed.arrangeImportedObject(plate, objectIndex, 2.0) &&
                        isObjectInsideBuildVolume(plate, objectIndex, bed) &&
                        !hasMeshIntersectionForObject(plate, objectIndex)
                    if (!placed) {
                        if (plate.objectsCount > objectIndex) plate.deleteObject(objectIndex)
                        return false
                    }
                    destinationIndex = objectIndex
                    destinationPlateIndex = plateIndex
                    destinationPlate = plate
                    return true
                }

                var placed = tryPlaceOn(target, targetPlateIndex)
                if (!placed) {
                    // Keep project-owned plates fixed, but reuse free room on prior import plates.
                    for (plateIndex in platesModels.indices.reversed()) {
                        if (plateIndex == targetPlateIndex || getProjectPlateId(plateIndex) > 0) continue
                        val plate = platesModels.getOrNull(plateIndex) ?: continue
                        if (tryPlaceOn(plate, plateIndex)) { placed = true; break }
                    }
                }
                if (!placed) {
                    for (pendingIndex in automaticallyCreated.indices.reversed()) {
                        val plate = automaticallyCreated[pendingIndex]
                        if (tryPlaceOn(plate, originalPlateCount + pendingIndex)) { placed = true; break }
                    }
                }
                if (!placed) {
                    val nextPlate = Model().also { it.addObject(imported, sourceIndex) }
                    if (!bed.arrangeCompact(nextPlate) ||
                        !isModelInsideBuildVolume(nextPlate, bed)) {
                        centerObjectOnBed(nextPlate, 0, bed)
                        if (!isObjectInsideBuildVolume(nextPlate, 0, bed)) objectsTooLargeForBed++
                    }
                    automaticallyCreated.add(nextPlate)
                    destinationPlate = nextPlate
                    destinationPlateIndex = null
                    destinationIndex = 0
                }

                target = requireNotNull(destinationPlate)
                targetPlateIndex = destinationPlateIndex
                if (finalTarget !== target) {
                    finalTarget = target
                    finalFirstNew = destinationIndex
                    finalAddedCount = 1
                } else {
                    finalAddedCount++
                }
            }
            imported.release()
            target.resetBoundingBox()
            val completedTarget = target
            val completedFirstNew = finalFirstNew
            val completedAddedCount = finalAddedCount
            val completedTooLargeCount = objectsTooLargeForBed
            val completedTargetPlateIndex = targetPlateIndex
                ?: (originalPlateCount + automaticallyCreated.indexOfFirst { it === completedTarget })

            fun finishOnGlThread() {
                viewport.renderer.setCurrentPlateIndex(completedTargetPlateIndex)
                viewport.renderer.model = completedTarget
                viewport.renderer.resetGlModels()
                resetModelHistory()
                onModelImportCommitted()
                callback?.onLoaded(completedTarget, completedFirstNew, completedAddedCount)
                if (completedTooLargeCount > 0)
                    SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(SnackbarsLayout.Type.WARNING,
                        R.string.ImportedObjectsTooLargeForBed, completedTooLargeCount))
                reportImportReduction(reduction)
                viewport.requestRender()
            }

            if (automaticallyCreated.isEmpty()) {
                if (completedTargetPlateIndex == currentPlateIndex) {
                    finishOnGlThread()
                } else {
                    ViewUtils.postOnMainThread {
                        if (destroyed || viewport !== glView || completedTargetPlateIndex !in platesModels.indices) return@postOnMainThread
                        currentPlateIndex = completedTargetPlateIndex
                        platePreviewRevision++
                        updatePlateIndicator()
                        pushPlatesToRenderer()
                        viewport.queueEvent { finishOnGlThread() }
                    }
                }
            } else {
                ViewUtils.postOnMainThread {
                    if (destroyed || viewport !== glView) {
                        automaticallyCreated.forEach { it.release() }
                        return@postOnMainThread
                    }
                    automaticallyCreated.forEach { newPlate ->
                        platesModels.add(newPlate)
                        projectPlateIds.add(0)
                    }
                    totalPlates = platesModels.size
                    if (completedTargetPlateIndex !in platesModels.indices) return@postOnMainThread
                    currentPlateIndex = completedTargetPlateIndex
                    platePreviewRevision++
                    updatePlateIndicator()
                    SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(SnackbarsLayout.Type.INFO,
                        R.string.ImportedObjectsMovedToNewPlates, automaticallyCreated.size))
                    pushPlatesToRenderer()
                    viewport.queueEvent { finishOnGlThread() }
                }
            }
        }
    })
}

private fun centerObjectOnBed(model: Model, objectIndex: Int, bed: Bed3D) {
    val bedMin = bed.getVolumeMin() ?: return
    val bedMax = bed.getVolumeMax() ?: return
    val objectMin = Vec3d()
    val objectMax = Vec3d()
    model.getBoundingBoxExact(objectIndex, objectMin, objectMax)
    model.translate(objectIndex,
        (bedMin.x + bedMax.x - objectMin.x - objectMax.x) / 2.0,
        (bedMin.y + bedMax.y - objectMin.y - objectMax.y) / 2.0,
        0.0)
}

/** Exact mesh check for only the newly imported object, with an AABB broad phase. */
private fun hasMeshIntersectionForObject(model:Model, objectIndex:Int):Boolean {
    if (objectIndex !in 0 until model.objectsCount) return true
    val objectMin = Vec3d()
    val objectMax = Vec3d()
    val otherMin = Vec3d()
    val otherMax = Vec3d()
    model.getBoundingBoxExact(objectIndex, objectMin, objectMax)
    for (otherIndex in 0 until model.objectsCount) {
        if (otherIndex == objectIndex) continue
        model.getBoundingBoxExact(otherIndex, otherMin, otherMax)
        val aabbOverlaps = objectMin.x < otherMax.x - 0.001 && objectMax.x > otherMin.x + 0.001 &&
            objectMin.y < otherMax.y - 0.001 && objectMax.y > otherMin.y + 0.001 &&
            objectMin.z < otherMax.z - 0.001 && objectMax.z > otherMin.z + 0.001
        if (!aabbOverlaps) continue
        val probe = Model()
        try {
            probe.addObject(model, objectIndex)
            probe.addObject(model, otherIndex)
            if (probe.booleanObjects(0, 1, BooleanOperation.INTERSECTION) >= 0) return true
        } finally {
            probe.release()
        }
    }
    return false
}

private fun isObjectInsideBuildVolume(
    model:Model,
    objectIndex:Int,
    bed:Bed3D,
    tolerance:Double = 0.2,
):Boolean {
    if (objectIndex !in 0 until model.objectsCount) return false
    val bedMin = bed.getVolumeMin() ?: return false
    val bedMax = bed.getVolumeMax() ?: return false
    val objectMin = Vec3d()
    val objectMax = Vec3d()
    model.getBoundingBoxExact(objectIndex, objectMin, objectMax)
    return objectMin.x >= bedMin.x - tolerance && objectMin.y >= bedMin.y - tolerance &&
        objectMin.z >= bedMin.z - tolerance && objectMax.x <= bedMax.x + tolerance &&
        objectMax.y <= bedMax.y + tolerance && objectMax.z <= bedMax.z + tolerance
}

private fun isModelInsideBuildVolume(model: Model, bed: Bed3D, tolerance: Double = 0.2): Boolean {
    val bedMin = bed.getVolumeMin() ?: return false
    val bedMax = bed.getVolumeMax() ?: return false
    val objectMin = Vec3d()
    val objectMax = Vec3d()
    repeat(model.objectsCount) { index ->
        model.getBoundingBoxExact(index, objectMin, objectMax)
        if (objectMin.x < bedMin.x - tolerance || objectMin.y < bedMin.y - tolerance ||
            objectMin.z < bedMin.z - tolerance || objectMax.x > bedMax.x + tolerance ||
            objectMax.y > bedMax.y + tolerance || objectMax.z > bedMax.z + tolerance
        ) return false
    }
    return model.objectsCount > 0
}
override fun onApplyTheme() {
super.onApplyTheme()
plateTabsThemeRevision++
profileStatusThemeRevision++
printerWebThemeRevision++
workspaceThemeRevision++
}
fun updateModel() {
if (platesModels.isEmpty()) return
platePreviewRevision++
val viewport = glView ?: return
val targetPlate = currentPlateIndex
viewport.queueEvent {
    commitModelUpdateOnGlThread(viewport, targetPlate)
}
viewport.requestRender()
}
/** Commit a model edit that has already been applied on the renderer's GL queue. */
internal fun updateModelOnGlThread() {
    val viewport = glView ?: return
    if (platesModels.isEmpty()) return
    platePreviewRevision++
    commitModelUpdateOnGlThread(viewport, currentPlateIndex)
    viewport.requestRender()
}

private fun commitModelUpdateOnGlThread(viewport: GLView, targetPlate: Int) {
    if (destroyed || targetPlate !in platesModels.indices) return
    val currentModel = viewport.renderer.model
    platesModels.set(targetPlate, currentModel)
    if (isLargeModelInMemory(currentModel)) {
        clearModelHistory()
        modelHistoryDisabledForMemory = true
        warnLargeModelLimitOnce()
    } else {
        modelHistoryDisabledForMemory = false
        val undoSnapshot = stableModelSnapshot ?: if (currentModel != null) Model() else null
        if (undoSnapshot != null) {
            // A fresh workspace has no native Model pointer, but the empty workspace is still a
            // real undo state. Capture it when the first generated/added object is committed so
            // Undo can remove that first object instead of treating it as the new baseline.
            undoHistory.addLast(undoSnapshot)
            while (undoHistory.size > MAX_HISTORY) undoHistory.removeFirst().release()
        }
        clearHistoryDeque(redoHistory)
        stableModelSnapshot = if (currentModel != null) Model.merge(currentModel) else null
    }
    val shouldClearTemporarySettings = !modelLoadInProgress &&
        platesModels.all { it != null && it.objectsCount == 0 }
    ViewUtils.postOnMainThread {
        if (destroyed) return@postOnMainThread
        updatePlateIndicator()
        if (shouldClearTemporarySettings) TemporaryProjectSettings.clear()
    }
}
private fun syncCurrentModel() {
if (!platesModels.isEmpty() && glView != null)
{
platesModels.set(currentPlateIndex, glView!!.renderer.model)
}
}
private fun resetModelHistory() {
clearModelHistory()
val current = if (glView != null) glView!!.renderer.model else null
modelHistoryDisabledForMemory = isLargeModelInMemory(current)
if (modelHistoryDisabledForMemory)
{
warnLargeModelLimitOnce()
return
}
stableModelSnapshot = if (current != null) Model.merge(current) else null
}
private fun warnLargeModelLimitOnce() {
if (largeModelWarningShown) return
largeModelWarningShown = true
ViewUtils.postOnMainThread({ SliceBeam.EVENT_BUS.fireEvent(
NeedSnackbarEvent(R.string.ModelLargeMemoryProtection)) })
}
private fun clearModelHistory() {
clearHistoryDeque(undoHistory)
clearHistoryDeque(redoHistory)
if (stableModelSnapshot != null)
{
stableModelSnapshot!!.release()
stableModelSnapshot = null
}
}
private fun clearHistoryDeque(history:ArrayDeque<Model>) {
while (!history.isEmpty()) history.removeFirst().release()
}
fun undoModelEdit() {
applyModelHistory(undoHistory, redoHistory, R.string.ModelNothingToUndo)
}
fun redoModelEdit() {
applyModelHistory(redoHistory, undoHistory, R.string.ModelNothingToRedo)
}
private fun applyModelHistory(source:ArrayDeque<Model>, destination:ArrayDeque<Model>, emptyMessage:Int) {
val viewport = glView ?: return
// updateModel() commits history snapshots on the GL queue. Check availability there too,
// so an immediate Undo cannot race ahead of a just-committed edit.
viewport.queueEvent({
if (modelHistoryDisabledForMemory)
{
warnLargeModelLimitOnce()
return@queueEvent
}
if (source.isEmpty())
{
ViewUtils.postOnMainThread { SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(emptyMessage)) }
return@queueEvent
}
val current = viewport.renderer.model
// Keep an explicit empty-model snapshot as well. Deleting the final object sets the
// renderer model to null; without an empty snapshot Undo could restore that object,
// but Redo had nothing to restore and silently stopped at the non-empty state.
destination.addLast(current?.let { Model.merge(it) } ?: Model())
while (destination.size > MAX_HISTORY) destination.removeFirst().release()
val restored = source.removeLast()
viewport.renderer.model = restored
platesModels.set(currentPlateIndex, restored)
if (current != null) current.release()
if (stableModelSnapshot != null) stableModelSnapshot!!.release()
stableModelSnapshot = Model.merge(restored)
viewport.requestRender()
ViewUtils.postOnMainThread({ SliceBeam.EVENT_BUS.fireEvent(ru.ytkab0bp.slicebeam.events.ObjectsListChangedEvent()) }) })
}
/** Split the selected object into separate objects while preserving the imported layout. */
fun splitSelectedObject() = splitSelectedObjectInternal(null)

internal fun splitSelectedObjectInternal(expectedSelection: ModelSelectionSnapshot?) {
    val viewport = glView ?: return
    viewport.queueEvent {
        val renderer = viewport.renderer
        val snapshot = expectedSelection ?: renderer.captureSelectionSnapshot() ?: return@queueEvent
        if (!renderer.isSelectionSnapshotCurrent(snapshot)) {
            ViewUtils.postOnMainThread {
                SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.ModelSelectionChanged))
            }
            return@queueEvent
        }
        val count = runCatching { snapshot.model.split(snapshot.primaryObject) }.getOrElse { error ->
            Log.e("BedFragment", "Could not split selected model", error)
            0
        }
        if (count <= 1) {
            SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.MenuToolbarSplitNothing))
            return@queueEvent
        }
        renderer.setSelectedObject(-1)
        renderer.resetGlModels()
        viewport.requestRender()
        ViewUtils.postOnMainThread {
            updateModel()
            SliceBeam.EVENT_BUS.fireEvent(ru.ytkab0bp.slicebeam.events.ObjectsListChangedEvent())
            SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(
                SliceBeam.INSTANCE!!.getResources().getQuantityString(
                    R.plurals.MenuToolbarSplitDone, count, count,
                ),
            ))
        }
    }
}
companion object {
/** Printer-profile fallback used before the workspace GL surface has configured its native bed. */
internal fun configuredPrimeTowerPlacementMap(): PrimeTowerPlacementMap? = runCatching {
    PrimeTowerDragLogic.fromBedShape(SliceBeam.buildCurrentConfigObject().get("bed_shape"))
}.getOrNull()
private val DEBUG_VIEWER = false
private fun usesCompactPortraitLayout(context:Context):Boolean {
val configuration = context.getResources().getConfiguration()
return AdaptiveNavigationPolicy.usesCompactPortraitLayout(
configuration.screenWidthDp,
configuration.screenHeightDp,
configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
}
private val SESSION_PLATE_COUNT = "last_session_plate_count"
private val MAX_HISTORY = 5
private fun modelHistoryWouldExceedBudget(triangleCount: Long): Boolean =
    triangleCount.coerceAtLeast(0L) > MODEL_HISTORY_MEMORY_BUDGET_BYTES /
        ESTIMATED_BYTES_PER_TRIANGLE / (MAX_HISTORY + 1)
private const val ESTIMATED_BYTES_PER_TRIANGLE = 64L
private const val MODEL_HISTORY_MEMORY_BUDGET_BYTES = 128L * 1024L * 1024L
private const val IMPORT_TRIANGLE_LIMIT = 500_000
private const val IMPORT_TRIANGLE_TARGET = 400_000
var tempFileName: String? = null
private var tempExportingFile: File? = null
val tempGCodePath:File
get() {
return tempExportingFile ?: File(SliceBeam.INSTANCE!!.getCacheDir(), "temp.gcode")
}
private fun detachFromParent(view:View) {
if (view != null && view.getParent() is ViewGroup)
{
(view.getParent() as ViewGroup).removeView(view)
}
}
/** Desktop OrcaSlicer plate grid column count (PartPlateList::compute_colum_count). */
 private fun computePlateCols(count:Int):Int {
val value = (Math.sqrt((count).toDouble())).toFloat()
val round = Math.round(value)
return if (value > round) round.toInt() + 1 else round.toInt()
}
/**
 * Plate origin in project world space, matching desktop PartPlateList::compute_origin:
 * stride = bed size * (1 + LOGICAL_PART_PLATE_GAP=1/5), columns extend +X, rows extend -Y.
 */
 private fun computePlateOrigin(index:Int, cols:Int, bedWidth:Double, bedDepth:Double, out:DoubleArray) {
out[0] = (index % cols).toDouble() * bedWidth * 1.2
out[1] = (-(index / cols)).toDouble() * bedDepth * 1.2
}
private fun computePlateOrigin(index:Int, cols:Int, bedMin:Vec3d, bedMax:Vec3d, out:DoubleArray) {
computePlateOrigin(index, cols, bedMax.x - bedMin.x, bedMax.y - bedMin.y, out)
}
/**
 * Bed size the project file was laid out for, from its embedded printable_area. The world
 * offsets of objects on plate 2+ are multiples of THIS bed's stride â€” which may differ from
 * the app's configured bed. Returns true and fills out[0]=width, out[1]=depth on success.
 */
 private fun readProjectBedSize(f:File, out:DoubleArray):Boolean {
try
{
java.util.zip.ZipFile(f).use({ zip->
val en = zip.getEntry("Metadata/project_settings.config")
if (en == null) return false
val cfg = org.json.JSONObject(
ru.ytkab0bp.slicebeam.utils.IOUtils.readString(zip.getInputStream(en), true))
val area = cfg.optJSONArray("printable_area")
if (area == null || area.length() == 0) return false
var minX = Double.MAX_VALUE
var maxX = -Double.MAX_VALUE
var minY = Double.MAX_VALUE
var maxY = -Double.MAX_VALUE
for (i in 0 until area.length())
{
val pt = area.getString(i).split("x")
if (pt.size != 2) return false
val x = java.lang.Double.parseDouble(pt[0])
val y = java.lang.Double.parseDouble(pt[1])
minX = Math.min(minX, x)
maxX = Math.max(maxX, x)
minY = Math.min(minY, y)
maxY = Math.max(maxY, y)
}
if (maxX <= minX || maxY <= minY) return false
out[0] = maxX - minX
out[1] = maxY - minY
return true })
}
catch (e:Exception) {
android.util.Log.w("BedFragment", "Could not read project bed size", e)
return false
}
}
}
}
