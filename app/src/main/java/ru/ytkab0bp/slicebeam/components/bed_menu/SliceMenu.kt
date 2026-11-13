package ru.ytkab0bp.slicebeam.components.bed_menu

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.SliceBeam
import ru.ytkab0bp.slicebeam.components.PrinterUploadProgressDialog
import ru.ytkab0bp.slicebeam.config.ConfigObject
import ru.ytkab0bp.slicebeam.fragment.BedFragment
import ru.ytkab0bp.slicebeam.utils.AnycubicGCodeProcessor
import ru.ytkab0bp.slicebeam.utils.IOUtils
import ru.ytkab0bp.slicebeam.utils.MmuFilamentSync
import ru.ytkab0bp.slicebeam.utils.Prefs
import ru.ytkab0bp.slicebeam.utils.QidiGCode3mfPackager
import ru.ytkab0bp.slicebeam.utils.ViewUtils
import java.io.File
import java.io.IOException

/** Coordinates the Slice menu, printer preparation and Kotlin upload controller. */
class SliceMenu : ListBedMenu() {
    private var portrait = true
    private val composeActions = mutableStateOf<List<BedComposeMenuAction>>(emptyList())
    @Volatile private var preflightAnycubicScan: AnycubicGCodeProcessor.ScanResult? = null

    override fun onCreateItems(portrait: Boolean): List<BedMenuItem> = emptyList()

    @Composable
    override fun WorkspaceContent(portrait: Boolean) {
        LaunchedEffect(this, portrait) { refreshItems(portrait) }
        CustomizableBedMenuActionsContent("slice", composeActions.value, portrait)
    }

    private fun createActions(): List<BedComposeMenuAction> {
        val bed = fragment ?: return emptyList()
        if (!bed.hasSliceResult()) {
            val hasModel = bed.currentModel?.objectsCount?.let { it > 0 } == true
            return SliceMenuItems.createBeforeSlice(bed, hasModel)
        }
        return SliceMenuItems.createAfterSlice(bed, this) { type, host, apiKey, printer, print ->
            refreshMmuAndPrepare(type, host, apiKey, printer, print)
        }
    }

    fun refreshItems(portrait: Boolean) {
        this.portrait = portrait
        composeActions.value = createActions()
    }

    fun refreshInfoItems() {
        composeActions.value = createActions()
    }

    private fun refreshMmuAndPrepare(
        type: String,
        host: String,
        apiKey: String?,
        printer: ConfigObject,
        print: Boolean,
    ) {
        val bed = fragment ?: return
        val context = bed.getContext() ?: return
        val slicingWarning = bed.glView?.renderer?.gcodeResult?.getSpiralVaseOverrideWarning()
        preflightAnycubicScan = null
        val progress = bed.showPrinterUploadProgress(
            context.getString(R.string.UploadAnalyzingGcode),
        ) ?: return
        // Scan first: an explicitly unavailable ACE may use the single TPU external-spool
        // path, which must not make either the preflight or upload synchronize the MMU.
        scanToolCountAndPrepare(type, host, apiKey, printer, print, slicingWarning, progress)
    }

    private fun scanToolCountAndPrepare(
        type: String,
        host: String,
        apiKey: String?,
        printer: ConfigObject,
        print: Boolean,
        slicingWarning: String?,
        progress: PrinterUploadProgressDialog,
    ) {
        val bed = fragment ?: return
        val gcode: File = BedFragment.tempGCodePath
        progress.setMessage(bed.getContext()?.getString(R.string.UploadAnalyzingGcode).orEmpty())
        IOUtils.IO_POOL.execute {
            val qidiPreflight = QidiGCode3mfPackager.isQidiQ2(printer)
            val preflightStartedAt = android.os.SystemClock.elapsedRealtime()
            val preflight = try {
                if (SlicePreparationTools.isAnycubicKobra3Model(printer.get("printer_model"))) {
                    val startedAt = android.os.SystemClock.elapsedRealtime()
                    val scan = AnycubicGCodeProcessor.scan(gcode)
                    preflightAnycubicScan = scan
                    android.util.Log.i(
                        "SliceUploadPerf",
                        "Kobra preflight scanned ${gcode.length()} bytes in ${android.os.SystemClock.elapsedRealtime() - startedAt} ms",
                    )
                    SlicePreparationTools.Preflight(
                        SlicePreparationTools.ToolUsage(
                            if (scan.usedTools.isEmpty()) intArrayOf(0) else scan.usedTools,
                            if (scan.firstToolAppearance.isEmpty()) intArrayOf(0) else scan.firstToolAppearance,
                        ),
                        SlicePreparationTools.PressureAdvanceCommands(
                            scan.filamentPressureAdvance,
                            scan.customPressureAdvance,
                        ),
                        scan.materials.toList(),
                    )
                } else SlicePreparationTools.readPreflight(gcode)
            } catch (_: IOException) {
                bed.dismissPrinterUploadProgress(progress)
                SliceUploadController.showPreparationFailure(bed)
                return@execute
            }
            if (qidiPreflight) {
                android.util.Log.i(
                    "SliceUploadPerf",
                    "QIDI preflight scanned ${gcode.length()} bytes in ${android.os.SystemClock.elapsedRealtime() - preflightStartedAt} ms",
                )
            }
            val toolUsage = preflight.toolUsage
            val pressureAdvance = preflight.pressureAdvance
            val sourceFilamentTypes = preflight.filamentTypes
            val activeTools = toolUsage.used
            val toolCount = maxOf(1, (activeTools.maxOrNull() ?: 0) + 1)
            val externalSpoolMaterials = MutableList(maxOf(sourceFilamentTypes.size, Prefs.getFilamentSlots().size)) { index ->
                sourceFilamentTypes.getOrNull(index).orEmpty()
            }.also { materials ->
                Prefs.getFilamentSlots().forEachIndexed { index, slot ->
                    val material = slot.type
                    if (materials[index].isBlank() && !material.isNullOrBlank()) materials[index] = material
                }
            }
            val showPreparation: (Boolean) -> Unit = { externalSpool -> ViewUtils.postOnMainThread {
                if (bed.getContext() != null) {
                    bed.dismissPrinterUploadProgress(progress)
                    SlicePrintPreparationDialog.show(
                        bed,
                        printer,
                        print,
                        toolCount,
                        activeTools,
                        toolUsage.firstAppearance,
                        sourceFilamentTypes,
                        pressureAdvance,
                        slicingWarning,
                        externalSpool,
                        this::activatePrinterProfile,
                        { updatedPrinter ->
                            refreshMmuAndPrepare(
                                updatedPrinter.get("host_type") ?: "octoprint",
                                updatedPrinter.get("print_host").orEmpty(),
                                updatedPrinter.get("printhost_apikey"),
                                updatedPrinter,
                                print,
                            )
                        },
                    ) { sendType, sendHost, sendApiKey, targetPrinter, shouldPrint, qidiBoxSlots ->
                        SliceUploadController.upload(
                            bed,
                            sendType,
                            sendHost,
                            sendApiKey,
                            shouldPrint,
                            targetPrinter,
                            activeTools,
                            preflightAnycubicScan,
                            qidiBoxSlots,
                            externalSpool,
                        )
                    }
                } else {
                    bed.dismissPrinterUploadProgress(progress)
                }
            } }
            if (SlicePrintPreparationDialog.isAnycubicKobra(printer)) {
                MmuFilamentSync.synchronize(printer) { _, error ->
                    if (error == null) {
                        if (bed.hasMmuInventoryChangedSinceSlice(printer.title.orEmpty())) {
                            bed.dismissPrinterUploadProgress(progress)
                            bed.discardSliceAfterMmuInventoryChange()
                            bed.getContext()?.let { activeContext ->
                                bed.showWorkspaceMessageDialog(
                                    title = activeContext.getString(R.string.UploadAceChecking),
                                    message = activeContext.getString(R.string.UploadAceInventoryChangedAfterSlice),
                                    confirmLabel = activeContext.getString(android.R.string.ok),
                                )
                            }
                        } else {
                            // A disconnected ACE can still leave the MMU config enabled. Use the
                            // firmware's separate filament_hub status to distinguish its explicit
                            // external-spool-ready state from a merely empty ACE inventory.
                            val externalSpoolReady = MmuFilamentSync.isExternalSpoolReady(printer.title.orEmpty())
                            if (externalSpoolReady && ru.ytkab0bp.slicebeam.utils.AnycubicAceRoutingPolicy.canUseExternalSpool(
                                    true, activeTools, externalSpoolMaterials,
                                )) showPreparation(true) else showPreparation(false)
                        }
                    } else if (ru.ytkab0bp.slicebeam.utils.AnycubicAceRoutingPolicy.canUseExternalSpool(
                            MmuFilamentSync.isMmuUnavailableError(error), activeTools, externalSpoolMaterials,
                        )) {
                        showPreparation(true)
                    } else {
                        bed.dismissPrinterUploadProgress(progress)
                        val activeContext = bed.getContext()
                        if (activeContext != null) bed.showWorkspaceMessageDialog(
                            title = activeContext.getString(R.string.MmuSyncFailed),
                            message = activeContext.getString(R.string.MmuAssignmentSyncFailed),
                            confirmLabel = activeContext.getString(android.R.string.ok),
                        )
                    }
                }
            } else showPreparation(false)
        }
    }

    private fun activatePrinterProfile(printer: ConfigObject): Boolean {
        val context = fragment?.getContext()
        if (!SliceBeam.applyProfileSelection(printer = printer.title)) {
            if (context != null) {
                android.widget.Toast.makeText(context, R.string.ConfigSaveFailed, android.widget.Toast.LENGTH_LONG).show()
            }
            return false
        }
        fragment?.refreshProfileStatus()
        return true
    }

}
