package ru.ytkab0bp.slicebeam.components.bed_menu

import android.app.Application
import android.view.View
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import ru.ytkab0bp.slicebeam.MainActivity
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.SliceBeam
import ru.ytkab0bp.slicebeam.components.PreSliceFilamentDialog
import ru.ytkab0bp.slicebeam.components.MmuAssignmentContent
import ru.ytkab0bp.slicebeam.config.ConfigObject
import ru.ytkab0bp.slicebeam.fragment.BedFragment
import ru.ytkab0bp.slicebeam.slic3r.Model
import ru.ytkab0bp.slicebeam.slic3r.GCodeProcessorResult
import ru.ytkab0bp.slicebeam.slic3r.Slic3rConfigWrapper
import ru.ytkab0bp.slicebeam.utils.FilamentSlot
import ru.ytkab0bp.slicebeam.utils.Prefs
import ru.ytkab0bp.slicebeam.utils.QidiBoxToolMappingPolicy
import ru.ytkab0bp.slicebeam.utils.QidiBoxToolMappingStore
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.io.File

/** Exercises the real workspace pre-send confirmation without issuing network or printer actions. */
@RunWith(AndroidJUnit4::class)
class SlicePrintPreparationComposeDeviceTest {
    @get:Rule
    val compose = createInitializedActivityRule()

    @Test
    fun kobra3V2StockStartGcodeDoesNotForcePressureAdvance() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val profile = context.assets.open(
            "orca_profiles/kobra3v2/printer/Anycubic Kobra 3 v2 0.4 nozzle - Stock.json",
        ).bufferedReader().use { JSONObject(it.readText()) }
        val startGcode = profile.getString("machine_start_gcode")
        assertFalse(
            "The stock Kobra 3 V2 profile must not override the filament Pressure Advance setting",
            Regex("(?im)^\\s*(?:M900\\s+K|SET_PRESSURE_ADVANCE\\b|M572\\b).*").containsMatchIn(startGcode),
        )
    }

    @Test
    fun sharedAceGateWarningResolvesItsPluralResource() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val acknowledged = AtomicInteger()
        compose.waitUntil(timeoutMillis = 15_000) { bed.canShowWorkspaceComposeDialog() }
        activity.runOnUiThread {
            bed.showWorkspaceComposeDialog {
                PrintPreparationWarningDialog(
                    warning = PrintPreparationWarning.SharedAceGate(tool = 1),
                    onDismiss = {},
                    onAcknowledge = { acknowledged.incrementAndGet() },
                    onUploadOnly = {},
                    onFirmwareChecked = {},
                )
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) { bed.isWorkspaceMessageDialogAttached() }
        val expectedMessage = activity.resources.getQuantityString(
            R.plurals.MmuAssignmentSharedAceGate,
            2,
            2,
        )
        compose.onNodeWithText(expectedMessage).assertIsDisplayed()
        compose.onNodeWithText(activity.getString(android.R.string.ok)).performClick()
        compose.waitUntil(timeoutMillis = 5_000) { !bed.isWorkspaceMessageDialogAttached() }
        assertEquals(1, acknowledged.get())
    }

    @Test
    fun selectingAnOccupiedAceGateAllowsTwoColorsToShareOnePhysicalFilament() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val mapping = mutableStateOf(listOf(0, 1, 2, 3))
        val palette = listOf(
            FilamentSlot(0xFF212721.toInt(), "PETG"),
            FilamentSlot(0xFFFFFFFF.toInt(), "PETG"),
            FilamentSlot(0xFF00BA4B.toInt(), "PETG"),
            FilamentSlot(0xFF0047BB.toInt(), "PLA"),
        )
        activity.runOnUiThread {
            bed.showWorkspaceComposeDialog {
                MmuAssignmentContent(
                    printerTitle = "synthetic ACE mapping test",
                    toolCount = 4,
                    loadedGates = intArrayOf(0, 1, 2, 3),
                    palette = palette,
                    mapping = mapping.value,
                    showHelp = false,
                    onMappingChange = { mapping.value = it },
                )
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) { bed.isWorkspaceMessageDialogAttached() }

        compose.onNodeWithTag("mmu-assignment-tool-1").assertIsDisplayed().performClick()
        compose.onNodeWithTag("mmu-assignment-gate-3").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals("Choosing ACE 4 for T1 must leave T3 on ACE 4 so two colors can share white filament",
                listOf(0, 3, 2, 3), mapping.value)
        }
        compose.onNodeWithTag("mmu-assignment-tool-0").assertIsDisplayed()
    }

    @Test
    fun preparationCancelDoesNotDispatchAndUploadOnlyUsesInjectedAction() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val printer = requireNotNull(SliceBeam.requireConfig().findPrinter("Anycubic Kobra 3 0.4 nozzle"))
        printer.put("host_type", "octoprint")
        printer.put("print_host", "http://printer.invalid")
        val sends = AtomicInteger()
        val printFlags = AtomicReference<Boolean>()
        val sentHost = AtomicReference<String>()

        fun showPreparation() {
            SlicePrintPreparationDialog.show(
                fragment = bed,
                printer = printer,
                print = false,
                gcodeToolCount = 1,
                activeTools = intArrayOf(),
                firstToolUse = intArrayOf(),
                sourceFilamentTypes = emptyList(),
                pressureAdvance = SlicePreparationTools.PressureAdvanceCommands(emptyList(), emptyList()),
                activateProfile = SlicePrintPreparationDialog.PrinterProfileAction { true },
                reopenAfterReslice = {},
                sendAction = SlicePrintPreparationDialog.PrinterSendAction { _, host, _, _, shouldPrint, _ ->
                    sends.incrementAndGet()
                    sentHost.set(host)
                    printFlags.set(shouldPrint)
                },
            )
        }

        activity.runOnUiThread(::showPreparation)
        compose.waitUntil(timeoutMillis = 5_000) { bed.isWorkspaceMessageDialogAttached() }
        compose.onNodeWithTag("slice-preparation-title").assertIsDisplayed()
        compose.onNodeWithTag("slice-preparation-cancel").performClick()
        compose.waitUntil(timeoutMillis = 5_000) { !bed.isWorkspaceMessageDialogAttached() }
        assertEquals("Cancel must not dispatch upload or print", 0, sends.get())

        activity.runOnUiThread(::showPreparation)
        compose.waitUntil(timeoutMillis = 5_000) { bed.isWorkspaceMessageDialogAttached() }
        compose.onNodeWithTag("slice-preparation-confirm").assertIsDisplayed().performClick()
        compose.waitUntil(timeoutMillis = 5_000) { !bed.isWorkspaceMessageDialogAttached() }
        assertEquals("Only the injected test action should be called", 1, sends.get())
        assertEquals("http://printer.invalid", sentHost.get())
        assertEquals("The upload-only choice must never request a print start", false, printFlags.get())
        assertFalse(activity.isFinishing)
    }

    @Test
    fun qidiBoxMappingIsSelectableAndPersistedPerPrinterBeforePrintDispatch() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val qidiConfig = activity.assets.open("orca_profiles/Qidi.ini").use { Slic3rConfigWrapper(it) }
        val printer = requireNotNull(qidiConfig.findPrinter("Qidi Q2 0.4 nozzle"))
        printer.put("printer_model", "Q2")
        printer.put("host_type", "moonraker")
        printer.put("print_host", "http://qidi-box-mapping-test.local")
        val prefs = requireNotNull(Prefs.getPrefs())
        val key = QidiBoxToolMappingPolicy.preferenceKey(printer.title.orEmpty(), printer.get("print_host").orEmpty())
        val oldValue = prefs.getString(key, null)
        val sentMapping = AtomicReference<IntArray?>()
        try {
            prefs.edit().remove(key).commit()
            SlicePrintPreparationDialog.show(
                    fragment = bed,
                    printer = printer,
                    print = true,
                    gcodeToolCount = 3,
                    activeTools = intArrayOf(0, 1, 2),
                    firstToolUse = intArrayOf(0, 1, 2),
                    sourceFilamentTypes = listOf("PLA", "PETG", "PLA"),
                    pressureAdvance = SlicePreparationTools.PressureAdvanceCommands(emptyList(), emptyList()),
                    activateProfile = SlicePrintPreparationDialog.PrinterProfileAction { true },
                    reopenAfterReslice = {},
                    sendAction = SlicePrintPreparationDialog.PrinterSendAction { _, _, _, _, shouldPrint, slots ->
                        assertTrue("Qidi mapping should only accompany an explicit print action", shouldPrint)
                        sentMapping.set(slots)
                    },
                )
            compose.waitUntil(timeoutMillis = 5_000) { bed.isWorkspaceMessageDialogAttached() }
            compose.onNodeWithTag("qidi-box-mapping-tool-0").assertIsDisplayed().performClick()
            compose.onNodeWithTag("qidi-box-mapping-slot-0-4").assertIsDisplayed().performClick()
            compose.onNodeWithTag("qidi-box-mapping-tool-1").performClick()
            compose.onNodeWithTag("qidi-box-mapping-slot-1-2").assertIsDisplayed().performClick()
            compose.onNodeWithTag("qidi-box-mapping-tool-2").performClick()
            compose.onNodeWithTag("qidi-box-mapping-slot-2-4").assertIsDisplayed().performClick()
            compose.onNodeWithTag("slice-preparation-confirm").performClick()
            compose.waitUntil(timeoutMillis = 5_000) { !bed.isWorkspaceMessageDialogAttached() }
            assertArrayEquals("Two G-code tools may intentionally use the same physical Box slot",
                intArrayOf(3, 1, 3), sentMapping.get())
            assertArrayEquals("The confirmed mapping should persist for this printer only",
                intArrayOf(3, 1, 3), QidiBoxToolMappingStore.load(printer, 3))
        } finally {
            if (oldValue == null) prefs.edit().remove(key).commit()
            else prefs.edit().putString(key, oldValue).commit()
        }
    }

    @Test
    fun cancellingQidiBoxMappingDoesNotPersistOrDispatch() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val qidiConfig = activity.assets.open("orca_profiles/Qidi.ini").use { Slic3rConfigWrapper(it) }
        val printer = requireNotNull(qidiConfig.findPrinter("Qidi Q2 0.4 nozzle"))
        printer.put("printer_model", "Q2")
        printer.put("host_type", "moonraker")
        printer.put("print_host", "http://qidi-box-cancel-test.local")
        val prefs = requireNotNull(Prefs.getPrefs())
        val key = QidiBoxToolMappingPolicy.preferenceKey(printer.title.orEmpty(), printer.get("print_host").orEmpty())
        val oldValue = prefs.getString(key, null)
        var dispatched = false
        try {
            prefs.edit().remove(key).commit()
            activity.runOnUiThread {
                SlicePrintPreparationDialog.show(
                    fragment = bed,
                    printer = printer,
                    print = true,
                    gcodeToolCount = 1,
                    activeTools = intArrayOf(0),
                    firstToolUse = intArrayOf(0),
                    sourceFilamentTypes = listOf("PLA"),
                    pressureAdvance = SlicePreparationTools.PressureAdvanceCommands(emptyList(), emptyList()),
                    activateProfile = SlicePrintPreparationDialog.PrinterProfileAction { true },
                    reopenAfterReslice = {},
                    sendAction = SlicePrintPreparationDialog.PrinterSendAction { _, _, _, _, _, _ -> dispatched = true },
                )
            }
            compose.waitUntil(timeoutMillis = 5_000) { bed.isWorkspaceMessageDialogAttached() }
            compose.onNodeWithTag("qidi-box-mapping-tool-0").assertIsDisplayed().performClick()
            compose.onNodeWithTag("qidi-box-mapping-slot-0-4").assertIsDisplayed().performClick()
            compose.onNodeWithTag("slice-preparation-cancel").performClick()
            compose.waitUntil(timeoutMillis = 5_000) { !bed.isWorkspaceMessageDialogAttached() }
            assertFalse("Cancel must not dispatch an upload or print", dispatched)
            assertNull("Unconfirmed Box selections must not be persisted", prefs.getString(key, null))
        } finally {
            if (oldValue == null) prefs.edit().remove(key).commit()
            else prefs.edit().putString(key, oldValue).commit()
        }
    }

    @Test
    fun emptyAceWarningKeepsUploadAndConfirmedPrintChoicesDistinct() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val action = AtomicReference<String?>()

        fun showWarning() {
            bed.showWorkspaceComposeDialog { dismiss ->
                PrintPreparationWarningDialog(
                    warning = PrintPreparationWarning.EmptyAceSlots(listOf(2)),
                    onDismiss = dismiss,
                    onAcknowledge = dismiss,
                    onUploadOnly = { action.set("upload-only"); dismiss() },
                    onFirmwareChecked = { action.set("firmware-checked-print"); dismiss() },
                )
            }
        }

        activity.runOnUiThread(::showWarning)
        compose.waitUntil(timeoutMillis = 5_000) { bed.isWorkspaceMessageDialogAttached() }
        compose.onNodeWithTag("ace-gap-cancel").assertIsDisplayed().performClick()
        compose.waitUntil(timeoutMillis = 5_000) { !bed.isWorkspaceMessageDialogAttached() }
        assertNull("Cancel must not dispatch either upload or print", action.get())

        activity.runOnUiThread(::showWarning)
        compose.waitUntil(timeoutMillis = 5_000) { bed.isWorkspaceMessageDialogAttached() }
        compose.onNodeWithTag("ace-gap-upload-only").assertIsDisplayed().performClick()
        compose.waitUntil(timeoutMillis = 5_000) { !bed.isWorkspaceMessageDialogAttached() }
        assertEquals("upload-only", action.get())

        action.set(null)
        activity.runOnUiThread(::showWarning)
        compose.waitUntil(timeoutMillis = 5_000) { bed.isWorkspaceMessageDialogAttached() }
        compose.onNodeWithTag("ace-gap-confirm-print").assertIsDisplayed().performClick()
        compose.waitUntil(timeoutMillis = 5_000) { !bed.isWorkspaceMessageDialogAttached() }
        assertEquals("Only the separate firmware-checked confirmation maps to the print branch",
            "firmware-checked-print", action.get())
        assertFalse("The test callback must not finish or print from the device", activity.isFinishing)
    }

    @Test
    fun preSliceAssignmentCancelAndBackDoNotApplyAssignments() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val selected = AtomicReference<IntArray?>()

        fun showAssignment() {
            PreSliceFilamentDialog.show(
                fragment = bed,
                model = null,
                callback = PreSliceFilamentDialog.Callback { selected.set(it) },
                positiveLabel = ru.ytkab0bp.slicebeam.R.string.ModelColorSlotsApply,
                titleLabel = ru.ytkab0bp.slicebeam.R.string.ModelColorSlotsAction,
            )
        }

        activity.runOnUiThread(::showAssignment)
        compose.waitUntil(timeoutMillis = 5_000) { bed.isWorkspaceMessageDialogAttached() }
        compose.onNodeWithTag("pre-slice-title").assertIsDisplayed()
        compose.onNodeWithTag("pre-slice-cancel").performClick()
        compose.waitUntil(timeoutMillis = 5_000) { !bed.isWorkspaceMessageDialogAttached() }
        assertNull("Cancel must not commit a filament assignment", selected.get())

        activity.runOnUiThread(::showAssignment)
        compose.waitUntil(timeoutMillis = 5_000) { bed.isWorkspaceMessageDialogAttached() }
        activity.onBackPressedDispatcher.onBackPressed()
        compose.waitUntil(timeoutMillis = 5_000) { !bed.isWorkspaceMessageDialogAttached() }
        assertNull("Workspace Back must not commit a filament assignment", selected.get())
        assertFalse(activity.isFinishing)
    }

    @Test
    fun twoObjectsCanBeAssignedToTheSameWhiteFilamentSlot() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val previousSlots = Prefs.getFilamentSlots()
        val slots = listOf(
            FilamentSlot(0xFF212721.toInt(), "PETG"),
            FilamentSlot(0xFFFFFFFF.toInt(), "PLA"),
            FilamentSlot(0xFFFFFFFF.toInt(), "PLA"),
        )
        val source = File(activity.cacheDir, "pre-slice-shared-white.3mf")
        InstrumentationRegistry.getInstrumentation().context.assets
            .open("models/three-color-project.3mf").use { input -> source.outputStream().use(input::copyTo) }
        var model: Model? = null
        try {
            Prefs.setFilamentSlots(slots)
            model = Model(source, 1)
            val selected = AtomicReference<IntArray?>()
            activity.runOnUiThread {
                PreSliceFilamentDialog.show(
                    fragment = bed,
                    model = model,
                    callback = PreSliceFilamentDialog.Callback { selected.set(it) },
                    positiveLabel = R.string.ModelColorSlotsApply,
                    titleLabel = R.string.ModelColorSlotsAction,
                )
            }
            compose.waitUntil(timeoutMillis = 5_000) { bed.isWorkspaceMessageDialogAttached() }
            compose.onNodeWithText(activity.getString(R.string.PreSliceIndividualMode)).performClick()

            compose.onNodeWithTag("pre-slice-object-slot-0").performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithTag("pre-slice-object-0-filament-1").assertIsDisplayed().performClick()
            compose.onNodeWithTag("pre-slice-object-slot-1").performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithTag("pre-slice-object-1-filament-1").assertIsDisplayed().performClick()
            compose.onNodeWithTag("pre-slice-confirm").performClick()

            compose.waitUntil(timeoutMillis = 5_000) { !bed.isWorkspaceMessageDialogAttached() }
            assertArrayEquals(
                "Two independently selected objects may reuse the same white filament slot",
                intArrayOf(1, 1, 2),
                requireNotNull(selected.get()),
            )
        } finally {
            model?.release()
            source.delete()
            Prefs.setFilamentSlots(previousSlots)
        }
    }

    @Test
    fun multipartModelColorsCanBeRemappedIndividuallyToTheSameSlot() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val previousSlots = Prefs.getFilamentSlots()
        val configFile = SliceBeam.getCurrentConfigFile()
        val previousConfigFile = configFile.takeIf { it.isFile }?.readBytes()
        val slots = listOf(
            FilamentSlot(0xFF212721.toInt(), "PETG"),
            FilamentSlot(0xFFFFFFFF.toInt(), "PLA"),
            FilamentSlot(0xFF0047BB.toInt(), "PLA"),
        )
        val source = File(activity.cacheDir, "pre-slice-remap-colors.stl")
        val project = File(activity.cacheDir, "pre-slice-remap-colors-roundtrip.3mf")
        val gcodeFile = File(activity.cacheDir, "pre-slice-remap-colors.gcode")
        activity.assets
            .open("models/xyz_cube.stl").use { input -> source.outputStream().use(input::copyTo) }
        var model: Model? = null
        var restored: Model? = null
        var sliceResult: GCodeProcessorResult? = null
        try {
            Prefs.setFilamentSlots(slots)
            model = Model(source)
            model.addObject(model, 0)
            model.addObject(model, 0)
            model.translate(1, 30.0, 0.0, 0.0)
            model.translate(2, 60.0, 0.0, 0.0)
            model.setUniformFilament(0, 1)
            model.setUniformFilament(1, 2)
            model.setUniformFilament(2, 3)
            val objectIndices = IntArray(model.objectsCount) { it }
            assertEquals(0, model.combineObjectsAsParts(objectIndices))
            assertEquals(1, model.objectsCount)
            val sourceTools = model.getUsedFilaments(0).map { it - 1 }.toIntArray()
            assertTrue("combined project should retain at least two imported colors", sourceTools.size >= 2)

            val result = AtomicReference<IntArray?>()
            val mappedSources = AtomicReference<IntArray?>()
            val mappedTargets = AtomicReference<IntArray?>()
            activity.runOnUiThread {
                PreSliceFilamentDialog.show(
                    fragment = bed,
                    model = model,
                    callback = object : PreSliceFilamentDialog.Callback {
                        override fun onSelected(slotIndices: IntArray) { result.set(slotIndices) }
                        override fun onSourceColorsSelected(slotIndices: IntArray, sourceFilaments: IntArray, targetFilaments: IntArray) {
                            result.set(slotIndices)
                            mappedSources.set(sourceFilaments)
                            mappedTargets.set(targetFilaments)
                        }
                    },
                    positiveLabel = R.string.ModelColorSlotsApply,
                    titleLabel = R.string.ModelColorSlotsAction,
                )
            }
            compose.waitUntil(timeoutMillis = 5_000) { bed.isWorkspaceMessageDialogAttached() }
            compose.onNodeWithText(activity.getString(R.string.PreSliceRemapModelColors)).performScrollTo().performClick()
            sourceTools.forEach { sourceTool ->
                compose.onNodeWithTag("pre-slice-source-tool-$sourceTool").performScrollTo().performClick()
                compose.onNodeWithTag("pre-slice-source-$sourceTool-slot-1").performScrollTo().performClick()
            }
            compose.onNodeWithTag("pre-slice-confirm").performClick()
            compose.waitUntil(timeoutMillis = 5_000) { !bed.isWorkspaceMessageDialogAttached() }

            assertArrayEquals("color remapping preserves object-level overrides", intArrayOf(-1), requireNotNull(result.get()))
            assertArrayEquals("every model color is passed as a one-based source tool",
                sourceTools.map { it + 1 }.toIntArray(), requireNotNull(mappedSources.get()))
            val targetTools = requireNotNull(mappedTargets.get())
            assertEquals("every model color may intentionally map to the same destination slot",
                sourceTools.size, targetTools.count { it == 2 })

            // Exercise the same remap callback the workspace uses, then verify the chosen
            // single white slot through the actual 3MF and G-code pipeline.
            val remapped = CountDownLatch(1)
            val remapFailure = AtomicReference<Throwable?>()
            requireNotNull(bed.glView).queueEvent {
                try {
                    requireNotNull(model).remapFilaments(requireNotNull(mappedSources.get()), targetTools)
                } catch (error: Throwable) {
                    remapFailure.set(error)
                } finally {
                    remapped.countDown()
                }
            }
            assertTrue("the remap must finish on the renderer thread", remapped.await(10, TimeUnit.SECONDS))
            remapFailure.get()?.let { throw AssertionError("model-color remapping failed", it) }
            assertEquals("all source volumes must now use the selected slot", listOf(2),
                requireNotNull(model).getUsedFilaments(0).toList())
            repeat(requireNotNull(model).objectsCount) { volume ->
                assertEquals("source volume $volume must be remapped to one-based slot 2", 2,
                    requireNotNull(model).getVolumeExtruder(0, volume))
            }
            SliceBeam.genCurrentConfig(requireNotNull(model))
            requireNotNull(model).export3mf(SliceBeam.getCurrentConfigFile().absolutePath, project.absolutePath)
            restored = Model(project, 1)
            assertEquals("the one-slot mapping must survive 3MF reload", listOf(2),
                requireNotNull(restored).getUsedFilaments(0).toList())
            SliceBeam.genCurrentConfig(requireNotNull(restored))
            sliceResult = requireNotNull(restored).slice(
                SliceBeam.getCurrentConfigFile().absolutePath, gcodeFile.absolutePath, null,
            )
            val gcode = gcodeFile.readText()
            assertTrue("the remapped model must extrude and emit the selected slot as T1",
                Regex("(?m)^G1 .*\\bE[0-9.-]+\\b").containsMatchIn(gcode) &&
                    Regex("(?m)^T1(?:\\s|$)").containsMatchIn(gcode))
            assertFalse("the original source colors must not reappear as T0 or T2",
                Regex("(?m)^T(?:0|2)(?:\\s|$)").containsMatchIn(gcode))
        } finally {
            sliceResult?.release()
            restored?.release()
            model?.release()
            source.delete()
            project.delete()
            gcodeFile.delete()
            if (previousConfigFile == null) configFile.delete() else configFile.writeBytes(previousConfigFile)
            Prefs.setFilamentSlots(previousSlots)
        }
    }

    @Test
    fun sliceMenuPrinterActionsRequireSupportedHostAndDispatchOnlyInjectedCallback() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val config = SliceBeam.requireConfig()
        val printerTitle = requireNotNull(config.presets).get("printer")
        val printer = requireNotNull(config.findPrinter(printerTitle))
        val originalType = printer.get("host_type")
        val originalHost = printer.get("print_host")
        val originalKey = printer.get("printhost_apikey")
        val anchor = View(activity)
        val dispatched = mutableListOf<Triple<String, String, Boolean>>()

        fun actionsFor(type: String, host: String): List<BedComposeMenuAction> {
            printer.put("host_type", type)
            printer.put("print_host", host)
            printer.put("printhost_apikey", "synthetic-test-key")
            return SliceMenuItems.createAfterSlice(bed, SliceMenu()) { selectedType, selectedHost, _, _, print ->
                dispatched += Triple(selectedType, selectedHost, print)
            }
        }

        try {
            listOf("octoprint", "moonraker", "klipper").forEach { supportedType ->
                val actions = actionsFor(supportedType, "https://printer.invalid")
                val upload = actions.single { it.titleRes == R.string.MenuSliceSendToPrinter }
                val print = actions.single { it.titleRes == R.string.MenuSliceSendToPrinterAndPrint }
                upload.onClick(anchor)
                print.onClick(anchor)
            }
            assertEquals(
                listOf(
                    Triple("octoprint", "https://printer.invalid", false),
                    Triple("octoprint", "https://printer.invalid", true),
                    Triple("moonraker", "https://printer.invalid", false),
                    Triple("moonraker", "https://printer.invalid", true),
                    Triple("klipper", "https://printer.invalid", false),
                    Triple("klipper", "https://printer.invalid", true),
                ),
                dispatched,
            )

            assertTrue("unsupported host types must expose no printer actions",
                actionsFor("unsupported", "https://printer.invalid").none {
                    it.titleRes == R.string.MenuSliceSendToPrinter ||
                        it.titleRes == R.string.MenuSliceSendToPrinterAndPrint
                })
            assertTrue("blank printer hosts must expose no printer actions",
                actionsFor("moonraker", "").none {
                    it.titleRes == R.string.MenuSliceSendToPrinter ||
                        it.titleRes == R.string.MenuSliceSendToPrinterAndPrint
                })
            assertEquals("configuration filtering must not dispatch any hidden action", 6, dispatched.size)
        } finally {
            if (originalType == null) printer.values.remove("host_type") else printer.put("host_type", originalType)
            if (originalHost == null) printer.values.remove("print_host") else printer.put("print_host", originalHost)
            if (originalKey == null) printer.values.remove("printhost_apikey") else printer.put("printhost_apikey", originalKey)
        }
    }

    private fun createInitializedActivityRule() = run {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val application = context.applicationContext as Application
        SliceBeam.INSTANCE = application as SliceBeam
        Prefs.init(application)
        Prefs.getPrefs()?.edit()
            ?.remove("crash")
            ?.putBoolean("converter_storage_first_prompt", true)
            ?.commit()
        Prefs.setLastCommit()
        SliceBeam.clearLiveDiffs()

        val config = context.assets.open("orca_profiles/Anycubic.ini").use(::Slic3rConfigWrapper)
        val printer = requireNotNull(config.findPrinter("Anycubic Kobra 3 0.4 nozzle"))
        val process = requireNotNull(config.findPrint("0.16mm Standard"))
        val filament = requireNotNull(config.findFilament("Anycubic Generic PLA"))
        config.presets = ConfigObject("presets").apply {
            put("printer", printer.title)
            put("print", process.title)
            put("filament", filament.title)
        }
        SliceBeam.CONFIG = config
        createAndroidComposeRule<MainActivity>()
    }
}
