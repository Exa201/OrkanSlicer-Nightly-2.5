package ru.ytkab0bp.slicebeam

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.ytkab0bp.slicebeam.ui.BeamComposeTheme
import ru.ytkab0bp.slicebeam.components.bed_menu.SliceChoiceField
import ru.ytkab0bp.slicebeam.ui.workspace.PlateTabs
import ru.ytkab0bp.slicebeam.ui.workspace.PlateThumbnail
import ru.ytkab0bp.slicebeam.components.AcePhysicalSlotsBar
import ru.ytkab0bp.slicebeam.utils.MmuFilamentSync
import ru.ytkab0bp.slicebeam.view.FilamentPaletteContent
import ru.ytkab0bp.slicebeam.view.PaletteSlotSnapshot
import ru.ytkab0bp.slicebeam.components.bed_menu.BedComposeMenuAction
import ru.ytkab0bp.slicebeam.components.bed_menu.BedMenuActionsContent
import ru.ytkab0bp.slicebeam.components.WorkspaceFilamentPaletteSheet
import ru.ytkab0bp.slicebeam.components.WorkspaceProfileSheet
import ru.ytkab0bp.slicebeam.components.FilamentSlotSheet
import ru.ytkab0bp.slicebeam.components.ColorChooserDialog
import ru.ytkab0bp.slicebeam.config.ConfigObject
import ru.ytkab0bp.slicebeam.config.ThreeMfProjectSettings
import ru.ytkab0bp.slicebeam.config.TemporaryProjectSettings
import ru.ytkab0bp.slicebeam.slic3r.ConfigOptionDef.ConfigOptionType
import ru.ytkab0bp.slicebeam.slic3r.Slic3rConfigWrapper
import ru.ytkab0bp.slicebeam.utils.Prefs
import ru.ytkab0bp.slicebeam.utils.FilamentSlot
import ru.ytkab0bp.slicebeam.view.FilamentPaletteController
import ru.ytkab0bp.slicebeam.SliceBeam
import ru.ytkab0bp.slicebeam.ui.settings.ComposeProfileColorOption

/** Exercises the real virtualized palette row with both supported and synthetic UI-only sizes. */
@RunWith(AndroidJUnit4::class)
class FilamentPaletteComposeDeviceTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun workspaceFilamentSheetRendersAndDismissesFromCancel() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as SliceBeam
        SliceBeam.INSTANCE = app
        Prefs.init(app)
        val dismissed = mutableStateOf(false)
        lateinit var sheet: WorkspaceFilamentPaletteSheet
        compose.runOnUiThread {
            sheet = WorkspaceFilamentPaletteSheet(
                context = context,
                controller = FilamentPaletteController(),
                hasModel = false,
                onAssignModel = {},
            )
        }
        compose.setContent {
            BeamComposeTheme {
                if (!dismissed.value) sheet.Content { dismissed.value = true }
            }
        }

        compose.onNodeWithText(context.getString(R.string.WorkspaceFilamentPaletteTitle)).assertIsDisplayed()
        val cancel = compose.onNodeWithText(context.getString(android.R.string.cancel))
            .assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        cancel.performClick()
        compose.runOnIdle { assertTrue(dismissed.value) }
        assertEquals(0, compose.onAllNodesWithText(
            context.getString(R.string.WorkspaceFilamentPaletteTitle),
        ).fetchSemanticsNodes().size)
    }

    @Test
    fun syncActionLivesInFilamentPaletteAndReportsCompletion() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        SliceBeam.INSTANCE = context.applicationContext as SliceBeam
        Prefs.init(context.applicationContext as SliceBeam)
        val previousConfig = SliceBeam.CONFIG
        val config = context.assets.open("orca_profiles/Anycubic.ini").use { Slic3rConfigWrapper(it) }
        val printer = requireNotNull(config.findPrinter("Anycubic Kobra 3 0.4 nozzle"))
        printer.put("print_host", "http://127.0.0.1:1")
        config.presets = ConfigObject("presets").apply {
            put("printer", printer.title)
            put("print", "0.16mm Standard")
            put("filament", "Anycubic Generic PLA")
        }
        SliceBeam.CONFIG = config
        var completed: MmuFilamentSync.Callback? = null
        var paletteChanges = 0
        val controller = FilamentPaletteController { paletteChanges++ }
        compose.setContent {
            BeamComposeTheme {
                controller.WorkspaceSheetContent(
                    hasModel = false,
                    sidePanel = false,
                    onAssignModel = {},
                    onDismiss = {},
                    onSyncFilaments = { completed = it },
                )
            }
        }

        try {
            compose.onNodeWithText(context.getString(R.string.MmuSyncButton)).assertIsDisplayed().performClick()
            compose.onNodeWithText(context.getString(R.string.MmuSyncWorkingShort)).assertIsDisplayed()
            compose.runOnUiThread { requireNotNull(completed).onResult(2, null) }
            compose.onNodeWithText(context.getString(R.string.MmuSyncSuccess, 2)).assertIsDisplayed()
            compose.runOnIdle { assertEquals(1, paletteChanges) }
        } finally {
            SliceBeam.CONFIG = previousConfig
        }
    }

    @Test
    fun addingFilamentSlotRequiresAndHonorsExplicitSave() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        SliceBeam.INSTANCE = context.applicationContext as SliceBeam
        Prefs.init(context.applicationContext as SliceBeam)
        val previousSlots = Prefs.getStoredFilamentSlots()
        Prefs.setStoredFilamentSlots(listOf(FilamentSlot(0xFF123456.toInt(), "PLA")))
        val controller = FilamentPaletteController()
        compose.setContent {
            BeamComposeTheme {
                controller.WorkspaceSheetContent(
                    hasModel = false,
                    sidePanel = false,
                    onAssignModel = {},
                    onDismiss = {},
                    onSyncFilaments = null,
                )
            }
        }

        try {
            compose.onNodeWithText(context.getString(R.string.FilamentPaletteAdd)).performClick()
            compose.onNodeWithText(context.getString(R.string.SlotFilamentConfig)).assertIsDisplayed()
            compose.onNodeWithTag("filament-slot-save").performClick()
            compose.onNodeWithText(context.getString(R.string.WorkspaceFilamentPaletteCount, 2)).assertIsDisplayed()
            compose.runOnIdle { assertEquals(2, Prefs.getStoredFilamentSlots().size) }
        } finally {
            Prefs.setStoredFilamentSlots(previousSlots)
        }
    }

    @Test
    fun workspaceProfileSheetRendersAndAndroidBackDismisses() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val app = context.applicationContext as SliceBeam
        SliceBeam.INSTANCE = app
        Prefs.init(app)
        val previousConfig = SliceBeam.CONFIG
        val config = context.assets.open("orca_profiles/Anycubic.ini").use { Slic3rConfigWrapper(it) }
        val printer = requireNotNull(config.findPrinter("Anycubic Kobra 3 0.4 nozzle"))
        val process = requireNotNull(config.findPrint("0.16mm Standard"))
        val filament = requireNotNull(config.findFilament("Anycubic Generic PLA"))
        config.presets = ConfigObject("presets").apply {
            put("printer", printer.title)
            put("print", process.title)
            put("filament", filament.title)
        }
        SliceBeam.CONFIG = config
        val dismissed = mutableStateOf(false)
        lateinit var sheet: WorkspaceProfileSheet
        compose.runOnUiThread { sheet = WorkspaceProfileSheet(context, null) }
        compose.setContent {
            BeamComposeTheme {
                if (!dismissed.value) sheet.Content { dismissed.value = true }
            }
        }

        try {
            compose.onNodeWithText(context.getString(R.string.WorkspaceProfilesTitle)).assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.WorkspaceProfilePrinter)).assertIsDisplayed()
            val apply = compose.onNodeWithText(context.getString(R.string.WorkspaceProfilesApply))
                .assertIsDisplayed().assertHeightIsAtLeast(48.dp)
            apply.assertWidthIsAtLeast(48.dp)
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            compose.waitForIdle()
            compose.runOnIdle { assertTrue(dismissed.value) }
            assertEquals(0, compose.onAllNodesWithText(
                context.getString(R.string.WorkspaceProfilesTitle),
            ).fetchSemanticsNodes().size)
        } finally {
            SliceBeam.CONFIG = previousConfig
        }
    }

    @Test
    fun workspaceProfileSheetAppliesOnlyChosenProfileGroupInsideTemporaryProject() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val app = context.applicationContext as SliceBeam
        SliceBeam.INSTANCE = app
        Prefs.init(app)
        val previousConfig = SliceBeam.CONFIG
        val config = context.assets.open("orca_profiles/Anycubic.ini").use { Slic3rConfigWrapper(it) }
        val printer = requireNotNull(config.findPrinter("Anycubic Kobra 3 0.4 nozzle"))
        val selectedProcess = requireNotNull(config.findPrint("0.16mm Standard"))
        val selectedFilament = requireNotNull(config.findFilament("Anycubic Generic PLA"))
        val nextProcess = requireNotNull(config.printConfigs.firstOrNull { it.title != selectedProcess.title })
        val nextProcessTitle = requireNotNull(nextProcess.title)
        val nextFilament = requireNotNull(config.filamentConfigs.firstOrNull { it.title != selectedFilament.title })
        val nextFilamentTitle = requireNotNull(nextFilament.title)
        config.presets = ConfigObject("presets").apply {
            put("printer", printer.title)
            put("print", selectedProcess.title)
            put("filament", selectedFilament.title)
        }
        val savedSelection = HashMap(config.presets!!.values)
        SliceBeam.CONFIG = config
        TemporaryProjectSettings.activate(
            ThreeMfProjectSettings(
                sourceName = "profile-sheet-ui-test",
                process = mapOf("layer_height" to "0.24", "wall_loops" to "9"),
                filaments = listOf(mapOf("filament_type" to "PETG", "nozzle_temperature" to "222")),
                filamentTypes = mapOf(
                    "filament_type" to ConfigOptionType.STRINGS,
                    "filament_colour" to ConfigOptionType.STRINGS,
                    "nozzle_temperature" to ConfigOptionType.INTS,
                ),
                printSettingsImported = true,
                filamentSettingsImported = true,
            ),
        )

        val dismissed = mutableStateOf(false)
        val currentSheet = mutableStateOf<WorkspaceProfileSheet?>(null)
        compose.runOnUiThread { currentSheet.value = WorkspaceProfileSheet(context, null) }
        compose.setContent {
            currentSheet.value?.let { sheet ->
                BeamComposeTheme {
                    if (!dismissed.value) sheet.Content { dismissed.value = true }
                }
            }
        }

        try {
            compose.onNodeWithText(requireNotNull(selectedProcess.title)).performScrollTo().performClick()
            compose.onNodeWithTag("slice-choice-search").performTextInput(nextProcessTitle)
            compose.onAllNodesWithText(nextProcessTitle).onLast().performClick()
            compose.onNodeWithText(context.getString(R.string.WorkspaceProfilesApply)).performClick()
            compose.waitForIdle()

            assertEquals(
                "Only the process group selected in the sheet should replace the imported process values",
                nextProcess["wall_loops"],
                TemporaryProjectSettings.valueFor(ConfigObject.PROFILE_LIST_PRINT, "wall_loops", includeEdits = false),
            )
            assertEquals(
                "The unselected filament group must keep its project-imported value",
                "222",
                TemporaryProjectSettings.valueFor(ConfigObject.PROFILE_LIST_FILAMENT, "nozzle_temperature", includeEdits = false),
            )
            assertEquals("Quick profile changes in a project must not persist profile selection", savedSelection, config.presets!!.values)
            compose.runOnIdle { assertTrue("Applying the selected profile should close the sheet", dismissed.value) }

            compose.runOnUiThread {
                dismissed.value = false
                currentSheet.value = WorkspaceProfileSheet(context, null)
            }
            compose.waitForIdle()
            compose.onNodeWithText(requireNotNull(selectedFilament.title)).performScrollTo().performClick()
            compose.onNodeWithTag("slice-choice-search").performTextInput(nextFilamentTitle)
            compose.onAllNodesWithText(nextFilamentTitle).onLast().performClick()
            compose.onNodeWithText(context.getString(R.string.WorkspaceProfilesApply)).performClick()
            compose.waitForIdle()

            assertEquals(
                "Only the filament group selected in the sheet should replace the project filament values",
                TemporaryProjectSettings.splitValue(requireNotNull(nextFilament["nozzle_temperature"]), ConfigOptionType.INTS).firstOrNull(),
                TemporaryProjectSettings.valueFor(ConfigObject.PROFILE_LIST_FILAMENT, "nozzle_temperature", includeEdits = false),
            )
            assertEquals(
                "The unselected process group must retain the previously imported project-local preset choice",
                nextProcess["wall_loops"],
                TemporaryProjectSettings.valueFor(ConfigObject.PROFILE_LIST_PRINT, "wall_loops", includeEdits = false),
            )
            assertEquals("Neither project-local profile choice may persist the saved selection", savedSelection, config.presets!!.values)
            compose.runOnIdle { assertTrue("Applying the selected filament should close the sheet", dismissed.value) }
        } finally {
            TemporaryProjectSettings.clear()
            SliceBeam.CONFIG = previousConfig
        }
    }

    @Test
    fun filamentSlotEditorAndColorPaletteStayInComposeAndCommitChosenColor() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val slot = FilamentSlot(0xFFFF0000.toInt(), "PLA")
        var changes = 0
        var dismissed = false
        val sheet = FilamentSlotSheet(
            slot = slot,
            canDelete = true,
            listener = object : FilamentSlotSheet.Listener {
                override fun onChanged(slot: FilamentSlot) { changes++ }
                override fun onDelete() = Unit
            },
        )

        compose.setContent {
            BeamComposeTheme {
                if (!dismissed) sheet.Content { dismissed = true }
            }
        }
        compose.onNodeWithText(context.getString(R.string.SlotFilamentConfig)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.FilamentSlotColor)).performClick()
        compose.onNodeWithText(context.getString(R.string.ColorChooserCommon)).assertIsDisplayed()
        val black = compose.onAllNodesWithContentDescription(
            context.getString(R.string.ColorChooserSwatchDescription, "#000000"),
        ).onFirst().assertIsDisplayed()
        black.assertWidthIsAtLeast(44.dp).assertHeightIsAtLeast(44.dp).performClick()
        compose.runOnIdle {
            assertEquals("Choosing a color is a draft until confirmed", 0xFFFF0000.toInt(), slot.color)
            assertEquals(0, changes)
        }
        compose.onNodeWithText(context.getString(R.string.SlotFilamentConfig)).assertIsDisplayed()
        compose.onNodeWithTag("filament-slot-save").performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(0xFF000000.toInt(), slot.color)
            assertEquals(1, changes)
            assertTrue(dismissed)
        }
    }

    @Test
    fun filamentSlotEditorCancelDiscardsColorDraft() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val slot = FilamentSlot(0xFFFF0000.toInt(), "PLA")
        var changes = 0
        var dismissed = false
        val sheet = FilamentSlotSheet(slot, true, object : FilamentSlotSheet.Listener {
            override fun onChanged(slot: FilamentSlot) { changes++ }
            override fun onDelete() = Unit
        })
        compose.setContent {
            BeamComposeTheme { if (!dismissed) sheet.Content { dismissed = true } }
        }

        compose.onNodeWithText(context.getString(R.string.FilamentSlotColor)).performClick()
        compose.onAllNodesWithContentDescription(
            context.getString(R.string.ColorChooserSwatchDescription, "#000000"),
        ).onFirst().performClick()
        compose.onNodeWithText(context.getString(android.R.string.cancel)).performClick()
        compose.runOnIdle {
            assertEquals("Cancel must not mutate the shared filament", 0xFFFF0000.toInt(), slot.color)
            assertEquals(0, changes)
            assertTrue(dismissed)
        }
    }

    @Test
    fun acePhysicalSlotsUseFourVisibleColumnsAndWrapFurtherGatesToRows() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val gates = (0 until 9).map { index ->
            MmuFilamentSync.Companion.PhysicalGate(
                index = index,
                printerTool = index,
                occupied = true,
                filament = FilamentSlot(0xFF000000.toInt() or (index * 0x111111), "Material-$index"),
            )
        }
        compose.setContent { BeamComposeTheme { AcePhysicalSlotsBar(gates, Modifier.width(360.dp)) } }

        val first = compose.onNodeWithText(context.getString(R.string.AcePhysicalSlotTool, 1, 0))
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val fourth = compose.onNodeWithText(context.getString(R.string.AcePhysicalSlotTool, 4, 3))
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val fifth = compose.onNodeWithText(context.getString(R.string.AcePhysicalSlotTool, 5, 4))
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val ninth = compose.onNodeWithText(context.getString(R.string.AcePhysicalSlotTool, 9, 8))
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals("first four physical gates share a row", first.top, fourth.top)
        assertTrue("gate five wraps to the next row", fifth.top > first.top)
        assertTrue("gate nine is reachable in a third row", ninth.top > fifth.top)
    }

    @Test
    fun acePhysicalSlotToolNumbersStayReadableInFourColumnsAt150PercentTextScale() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val gates = (0 until 4).map { index ->
            MmuFilamentSync.Companion.PhysicalGate(
                index = index,
                printerTool = index,
                occupied = true,
                filament = FilamentSlot(0xFF000000.toInt() or (index * 0x111111), "PETG"),
            )
        }
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.5f)) {
                BeamComposeTheme { AcePhysicalSlotsBar(gates, Modifier.width(320.dp)) }
            }
        }

        val first = compose.onNodeWithText(
            context.getString(R.string.AcePhysicalSlotCompactTool, 1, 0),
        ).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        for (index in 1 until 4) {
            compose.onNodeWithText(
                context.getString(R.string.AcePhysicalSlotCompactTool, index + 1, index),
            ).assertIsDisplayed()
        }
        val fourth = compose.onNodeWithText(
            context.getString(R.string.AcePhysicalSlotCompactTool, 4, 3),
        ).fetchSemanticsNode().boundsInRoot
        assertEquals("the ACE/tool labels remain in a single visible row at 150% text", first.top, fourth.top)
    }

    @Test
    fun profileColorSettingOpensComposePaletteAndCommitsTheChosenColor() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var selectedColor: Int? = null
        compose.setContent {
            BeamComposeTheme {
                ComposeProfileColorOption.Content(
                    title = "Profile color setting",
                    initialValue = "#FF0000",
                    onColorPicked = { selectedColor = it },
                    onLongClick = Runnable {},
                )
            }
        }

        compose.onNodeWithText("Profile color setting").performClick()
        compose.onNodeWithText(context.getString(R.string.ColorChooserCommon)).assertIsDisplayed()
        compose.onAllNodesWithContentDescription(
            context.getString(R.string.ColorChooserSwatchDescription, "#FF0000"),
        ).onFirst().performClick()
        compose.runOnIdle { assertEquals(0xFFFF0000.toInt(), selectedColor) }
    }

    @Test
    fun customColorPickerSupportsCancelChangeApplyAndExplicitReset() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val visible = mutableStateOf(true)
        val selectedColor = mutableIntStateOf(0xFFFF0000.toInt())
        compose.setContent {
            BeamComposeTheme {
                if (visible.value) {
                    ColorChooserDialog(
                        initialColor = selectedColor.intValue,
                        resetLabel = context.getString(R.string.SettingsInterfaceColorReset),
                        onDismiss = { visible.value = false },
                        onColorChosen = { selectedColor.intValue = it },
                        onReset = { selectedColor.intValue = 0xFF336699.toInt() },
                    )
                }
            }
        }

        compose.onNodeWithText(context.getString(R.string.OpenCustomColorPicker)).performClick()
        compose.onNodeWithText(context.getString(R.string.CustomColorHue)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(android.R.string.cancel)).performClick()
        compose.runOnIdle {
            assertTrue("Cancel returns to the palette", visible.value)
            assertEquals("Cancel leaves the existing color unchanged", 0xFFFF0000.toInt(), selectedColor.intValue)
        }

        compose.onNodeWithText(context.getString(R.string.OpenCustomColorPicker)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.CustomColorHue))
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                assertTrue("Hue slider accepts an accessible value", setProgress(180f))
            }
        compose.onNodeWithText(context.getString(R.string.CustomColorApply)).performClick()
        compose.runOnIdle {
            assertEquals("Hue change is applied as an opaque color", 0xFF00FFFF.toInt(), selectedColor.intValue)
            assertFalse("Applying closes the palette", visible.value)
        }

        compose.runOnIdle { visible.value = true }
        compose.waitForIdle()
        compose.onNodeWithText(context.getString(R.string.SettingsInterfaceColorReset)).performClick()
        compose.runOnIdle {
            assertEquals("Reset is an explicit action", 0xFF336699.toInt(), selectedColor.intValue)
            assertFalse("Reset closes the palette", visible.value)
        }
    }

    @Test
    fun longAceGateListShowsLastGateWithoutHorizontalScrolling() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) {
                BeamComposeTheme {
                    Box(Modifier.width(320.dp)) {
                        AcePhysicalSlotsBar(
                            gates = List(24) { index ->
                                MmuFilamentSync.Companion.PhysicalGate(
                                    index = index,
                                    printerTool = index,
                                    occupied = true,
                                    filament = FilamentSlot(0xFF000000.toInt() or (index * 0x010101), "Synthetic PLA $index"),
                                )
                            },
                        )
                    }
                }
            }
        }

        compose.onNodeWithText(context.getString(R.string.AcePhysicalSlotCompactTool, 1, 0)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.AcePhysicalSlotCompactTool, 24, 23)).assertIsDisplayed()
        compose.onNodeWithText("Synthetic PLA 23").assertIsDisplayed()
        compose.onNodeWithContentDescription(
            context.getString(R.string.AcePhysicalSlotsScrollForward),
        ).assertDoesNotExist()
    }

    @Test
    fun platePickerCancelHasAccessibleTouchTargetAndDismisses() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val open = mutableStateOf(false)
        var openCalls = 0
        var dismissCalls = 0
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.5f)) {
                BeamComposeTheme {
                    PlateTabs(
                        openState = open,
                        plateCount = 3,
                        selectedIndex = 0,
                        objectCounts = listOf(0, 1, 2),
                        thumbnails = List(3) { PlateThumbnail(emptyList()) },
                        onOpen = { openCalls++ },
                        onDismiss = { dismissCalls++ },
                        onSelect = {},
                        onClose = {},
                        onAdd = {},
                    )
                }
            }
        }

        compose.onNodeWithContentDescription(context.getString(R.string.PlateNumber, "1")).performClick()
        val cancelLabel = context.getString(android.R.string.cancel)
        compose.onNodeWithText(cancelLabel).assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertEquals(0, compose.onAllNodesWithText(cancelLabel).fetchSemanticsNodes().size)
        compose.runOnIdle {
            assertFalse(open.value)
            assertEquals(1, openCalls)
            assertEquals(1, dismissCalls)
        }
    }

    @Test
    fun plateCounterExpandsAtLargeFontScaleInsteadOfClippingItsCount() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val open = mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f, 1.5f),
                LocalConfiguration provides Configuration().apply {
                    screenWidthDp = 360
                    screenHeightDp = 800
                },
            ) {
                BeamComposeTheme {
                    PlateTabs(
                        openState = open,
                        plateCount = 1,
                        selectedIndex = 0,
                        objectCounts = listOf(0),
                        thumbnails = listOf(PlateThumbnail(emptyList())),
                        onOpen = {}, onDismiss = {}, onSelect = {}, onClose = {}, onAdd = {},
                    )
                }
            }
        }

        compose.onNodeWithContentDescription(context.getString(R.string.PlateNumber, "1"))
            .assertIsDisplayed()
            .assertHeightIsAtLeast(52.dp)
    }

    @Test
    fun platePickerSelectsLastPlateAcrossStressProjectSizes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val count = mutableIntStateOf(1)
        val selectedIndex = mutableIntStateOf(0)
        val open = mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f, 1.5f),
                LocalConfiguration provides Configuration().apply {
                    screenWidthDp = 320
                    screenHeightDp = 640
                },
            ) {
                BeamComposeTheme {
                    PlateTabs(
                        openState = open,
                        plateCount = count.intValue,
                        selectedIndex = selectedIndex.intValue,
                        objectCounts = List(count.intValue) { it },
                        thumbnails = List(count.intValue) { PlateThumbnail(emptyList()) },
                        onOpen = {},
                        onDismiss = {},
                        onSelect = { selectedIndex.intValue = it },
                        onClose = {},
                        onAdd = {},
                    )
                }
            }
        }

        listOf(1, 3, 6, 10, 20).forEach { plateCount ->
            compose.runOnUiThread {
                count.intValue = plateCount
                selectedIndex.intValue = 0
            }
            compose.waitForIdle()
            compose.onNodeWithContentDescription(context.getString(R.string.PlateNumber, "1")).performClick()
            if (plateCount > 2) {
                compose.onNode(hasScrollToIndexAction()).performScrollToIndex(plateCount - 1)
            }
            compose.onNodeWithContentDescription(
                context.getString(R.string.PlatePickerSelectDescription, plateCount.toString()),
            ).assertIsDisplayed().performClick()
            compose.runOnIdle {
                assertEquals(plateCount - 1, selectedIndex.intValue)
                assertFalse(open.value)
            }
        }
    }

    @Test
    fun paletteRowsFromOneThroughTwentyFourEntriesScrollAndSelectTheLastItem() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val count = mutableIntStateOf(1)
        val widthDp = mutableIntStateOf(320)
        val fontScale = mutableFloatStateOf(1f)
        var selectedIndex = -1

        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale.floatValue)) {
                BeamComposeTheme {
                    val slots = List(count.intValue) { index ->
                        PaletteSlotSnapshot(
                            color = 0xFF000000.toInt() or ((index * 0x010307) and 0x00FFFFFF),
                            type = "Synthetic PLA $index with a long material label",
                        )
                    }
                    Box(Modifier.width(widthDp.intValue.dp)) {
                        FilamentPaletteContent(
                            slots = slots,
                            selectedGates = null,
                            onSlotClick = { selectedIndex = it },
                            onAddClick = {},
                        )
                    }
                }
            }
        }

        listOf(320 to 1f, 360 to 1.3f, 400 to 1.5f).forEach { (screenWidth, textScale) ->
            compose.runOnUiThread {
                widthDp.intValue = screenWidth
                fontScale.floatValue = textScale
            }
            listOf(1, 4, 8, 16, 24).forEach { slotCount ->
                compose.runOnUiThread { count.intValue = slotCount }
                compose.waitForIdle()
                compose.onNode(hasScrollToIndexAction()).performScrollToIndex(slotCount - 1)
                val lastIndex = slotCount - 1
                val description = context.getString(
                    R.string.FilamentPaletteSlotDescription,
                    lastIndex,
                    "Synthetic PLA $lastIndex with a long material label",
                )
                val slot = compose.onNodeWithContentDescription(description).assertIsDisplayed()
                slot.assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
                slot.performClick()
                compose.runOnIdle { assertEquals(lastIndex, selectedIndex) }
            }
        }
    }

    @Test
    fun twoRowToolMenuKeepsLastActionReachableAtNarrowWidthAndLargeText() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val actionTitles = listOf(
            R.string.MenuCameraIsometric, R.string.MenuCameraTop, R.string.MenuCameraBottom,
            R.string.MenuCameraFront, R.string.MenuCameraBack, R.string.MenuCameraLeft,
            R.string.MenuCameraRight, R.string.MenuCameraOrtho, R.string.MenuCameraControlMode,
            R.string.MenuModel,
        )
        var selectedTitle = 0
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.5f)) {
                BeamComposeTheme {
                    Box(Modifier.width(320.dp).height(150.dp)) {
                        BedMenuActionsContent(
                            actions = actionTitles.map { title ->
                                BedComposeMenuAction(
                                    titleRes = title,
                                    iconRes = R.drawable.camera_mode_0_28,
                                    onClick = { selectedTitle = title },
                                )
                            },
                            portrait = true,
                            multiRow = true,
                        )
                    }
                }
            }
        }

        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(actionTitles.lastIndex)
        val lastTitle = context.getString(actionTitles.last())
        val lastAction = compose.onNodeWithContentDescription(lastTitle).assertIsDisplayed()
        lastAction.assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        lastAction.performClick()
        compose.runOnIdle { assertEquals(actionTitles.last(), selectedTitle) }
    }

    @Test
    fun twoRowToolMenuVisibleTilesStayInsideResponsiveViewport() {
        val actionTitles = listOf(
            R.string.WorkspaceObjects,
            R.string.ModelMenuDuplicate,
            R.string.ModelMenuMoveToPlate,
            R.string.ModelMenuCenterOnBed,
            R.string.ModelMenuFillBed,
            R.string.ModelMenuArrange,
            R.string.ModelMenuOrientAll,
            R.string.ModelMenuAutoOrientSelected,
            R.string.ModelMenuFlatten,
            R.string.ModelMenuDeleteSelected,
            R.string.ModelMenuSelectAll,
            R.string.ModelMenuDeleteAll,
        )
        var widthDp by mutableIntStateOf(320)
        var fontScale by mutableFloatStateOf(1f)
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                screenWidthDp = widthDp
                screenHeightDp = 800
            }
            CompositionLocalProvider(
                LocalDensity provides Density(1f, fontScale),
                LocalConfiguration provides configuration,
            ) {
                BeamComposeTheme {
                    Box(Modifier.width(widthDp.dp).height(160.dp).testTag("model-action-viewport")) {
                        key(widthDp, fontScale) {
                            BedMenuActionsContent(
                                actions = actionTitles.map { title ->
                                    BedComposeMenuAction(title, R.drawable.camera_mode_0_28, {})
                                },
                                portrait = true,
                                multiRow = true,
                            )
                        }
                    }
                }
            }
        }

        for ((width, scale) in listOf(320 to 1f, 360 to 1f, 400 to 1f, 320 to 1.5f, 360 to 1.3f, 400 to 1.5f)) {
            compose.runOnUiThread {
                widthDp = width
                fontScale = scale
            }
            compose.waitForIdle()
            val viewport = compose.onNodeWithTag("model-action-viewport").fetchSemanticsNode().boundsInRoot
            val visibleColumns = ru.ytkab0bp.slicebeam.components.bed_menu.BedMenuActionTileWidthPolicy
                .columnsForLayout(scale, width)
            val visibleActions = actionTitles.mapIndexedNotNull { index, title ->
                compose.onAllNodesWithContentDescription(
                    InstrumentationRegistry.getInstrumentation().targetContext.getString(title),
                ).fetchSemanticsNodes().firstOrNull()?.let { index to it.boundsInRoot }
            }
            for (index in 0 until visibleColumns * 2) {
                val actionNodes = compose.onAllNodesWithContentDescription(
                    InstrumentationRegistry.getInstrumentation().targetContext.getString(actionTitles[index]),
                ).fetchSemanticsNodes()
                assertTrue("Action $index not visible at $width dp / ${scale}x; viewport=$viewport, visible=$visibleActions",
                    actionNodes.isNotEmpty())
                compose.onNodeWithContentDescription(
                    InstrumentationRegistry.getInstrumentation().targetContext.getString(actionTitles[index]),
                ).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
                val action = actionNodes.first().boundsInRoot
                assertTrue("Action $index starts outside $width dp viewport at ${scale}x", action.left >= viewport.left)
                assertTrue("Action $index overlaps the scroll hint at $width dp / ${scale}x",
                    action.right <= viewport.right - 52f)
            }
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val firstPageMaxIndex = visibleActions.maxOf { it.first }
            compose.onNodeWithContentDescription(context.getString(R.string.WorkspaceMoreTitle))
                .assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
            compose.waitForIdle()
            val afterPaging = actionTitles.mapIndexedNotNull { index, title ->
                compose.onAllNodesWithContentDescription(context.getString(title))
                    .fetchSemanticsNodes().firstOrNull()?.let { index to it.boundsInRoot }
            }
            assertTrue("more-tools button did not reveal another action at $width dp / ${scale}x; " +
                "before=$visibleActions after=$afterPaging", afterPaging.any { it.first > firstPageMaxIndex })
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(actionTitles.lastIndex)
            compose.waitForIdle()
            val finalActionDescription = context.getString(actionTitles.last())
            val finalAction = compose.onNodeWithContentDescription(finalActionDescription)
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue("paging control must reveal complete actions at $width dp / ${scale}x",
                finalAction.right <= viewport.right - 52f)
        }
    }

    @Test
    fun profileChoiceUsesSearchAtLargeTextAndSelectsMatchingOption() {
        var selectedIndex = 0
        val options = listOf("Standard 0.20", "Fine 0.12", "Ultra fine 0.06")
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.5f)) {
                BeamComposeTheme {
                    SliceChoiceField(
                        label = "Process profile",
                        selectedIndex = selectedIndex,
                        options = options,
                        onSelected = { selectedIndex = it },
                    )
                }
            }
        }

        compose.onNodeWithTag("slice-choice-field-Process profile").performClick()
        compose.onNodeWithTag("slice-choice-search").assertIsDisplayed().performTextInput("Ultra fine")
        compose.onNodeWithText("Ultra fine 0.06").performClick()
        compose.runOnIdle { assertEquals(2, selectedIndex) }
    }

    @Test
    fun shortOptionsMenuShowsThreeReadableActionsAt320DpAnd150PercentText() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val actionTitles = listOf(
            R.string.MenuFileCalibrations,
            R.string.MenuFileImportProfiles,
            R.string.MenuFileExportProfiles,
        )
        val configuration = Configuration().apply {
            screenWidthDp = 320
            screenHeightDp = 800
        }
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f, 1.5f),
                LocalConfiguration provides configuration,
            ) {
                BeamComposeTheme {
                    Box(Modifier.width(320.dp).height(176.dp)) {
                        BedMenuActionsContent(
                            actions = actionTitles.map { title ->
                                BedComposeMenuAction(
                                    titleRes = title,
                                    iconRes = R.drawable.menu_calibrate_la_28,
                                    onClick = {},
                                )
                            },
                            portrait = true,
                        )
                    }
                }
            }
        }

        // The phone menu retains three actions in the row. Compact visible labels keep
        // them readable while content descriptions continue to expose the full names.
        for (title in actionTitles) {
            val action = compose.onNodeWithContentDescription(context.getString(title)).assertIsDisplayed()
            action.assertWidthIsAtLeast(96.dp).assertHeightIsAtLeast(48.dp)
        }
        compose.onNodeWithText(context.getString(R.string.MenuFileImportProfilesCompact)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.MenuFileExportProfilesCompact)).assertIsDisplayed()
    }
}
