package ru.ytkab0bp.slicebeam.utils

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.ytkab0bp.slicebeam.SliceBeam
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.config.ConfigObject
import ru.ytkab0bp.slicebeam.config.ThreeMfProjectSettings
import ru.ytkab0bp.slicebeam.config.TemporaryProjectSettings
import ru.ytkab0bp.slicebeam.components.MmuFilamentAssignments
import ru.ytkab0bp.slicebeam.components.bed_menu.SlicePreparationTools
import ru.ytkab0bp.slicebeam.slic3r.ConfigOptionDef.ConfigOptionType
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Runs real HTTP callbacks and Android JSON parsing in the isolated .slicetest application. */
@RunWith(AndroidJUnit4::class)
class MmuFilamentSyncDeviceTest {
    private val saved = LinkedHashMap<String, String?>()
    private val savedPrinterScoped = LinkedHashMap<String, Any>()
    private val keys = listOf("filament_palette", "mmu_synced_printer", "mmu_snapshot_printer", "mmu_snapshot", "mmu_choice_printer", "mmu_choice_gates", "mmu_palette_printer", "mmu_palette_gates")
    private val printerScopedPrefix = "${MmuPrinterPreferenceKeys.PREFIX}"
    private val title = "MMU sync test fixture"

    @Before fun savePreferences() {
        TemporaryProjectSettings.clear()
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as SliceBeam
        Prefs.init(application)
        val prefs = requireNotNull(Prefs.getPrefs())
        keys.forEach { saved[it] = prefs.getString(it, null) }
        savedPrinterScoped.clear()
        prefs.all.filterKeys { it.startsWith(printerScopedPrefix) }.forEach { (key, value) ->
            value?.let { savedPrinterScoped[key] = it }
        }
    }

    @After fun restorePreferences() {
        TemporaryProjectSettings.clear()
        val prefs = requireNotNull(Prefs.getPrefs())
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(printerScopedPrefix) }.forEach { editor.remove(it) }
        savedPrinterScoped.forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                else -> error("Unsupported saved MMU preference type for $key: ${value::class.java}")
            }
        }
        saved.forEach { (key, value) -> if (value == null) editor.remove(key) else editor.putString(key, value) }
        editor.commit()
    }

    @Test fun mmuSnapshotsAndGateAssignmentsRemainScopedToEachPrinterProfile() {
        val printerA = "$title A"
        val printerB = "$title B"
        val mappingA = intArrayOf(0, 1, 2, 3)
        val mappingB = intArrayOf(1, 0, 3, 2)

        val responseA = response(mappingA, IntArray(4) { 1 })
        val responseB = response(mappingA, IntArray(4) { 1 }).apply {
            getJSONObject("result").getJSONObject("status").getJSONObject("mmu")
                .put("gate_color", JSONArray(listOf("#000000", "#FFFFFF", "#0000FF", "#00FF00")))
        }
        assertEquals(4 to null, sync(responseA, printerTitle = printerA))
        MmuFilamentSync.setSelectedGates(printerA, intArrayOf(3, 1))
        MmuFilamentSync.setPaletteGates(printerA, mappingA)
        assertEquals(4 to null, sync(responseB, printerTitle = printerB))
        MmuFilamentSync.setSelectedGates(printerB, intArrayOf(2, 0))
        MmuFilamentSync.setPaletteGates(printerB, mappingB)

        assertArrayEquals(intArrayOf(3, 1), MmuFilamentSync.getSelectedGates(printerA, 2))
        assertArrayEquals(intArrayOf(2, 0), MmuFilamentSync.getSelectedGates(printerB, 2))
        assertArrayEquals(mappingA, MmuFilamentSync.paletteGates(printerA, 4))
        assertArrayEquals(mappingB, MmuFilamentSync.paletteGates(printerB, 4))
        assertArrayEquals(mappingA, MmuFilamentSync.getSnapshot(printerA)?.getJSONArray("ttg_map")?.let { values ->
            IntArray(values.length()) { values.getInt(it) }
        })
        assertArrayEquals(mappingA, MmuFilamentSync.getSnapshot(printerB)?.getJSONArray("ttg_map")?.let { values ->
            IntArray(values.length()) { values.getInt(it) }
        })
        assertEquals("#112233", MmuFilamentSync.getSnapshot(printerA)?.getJSONArray("gate_color")?.getString(0))
        assertEquals("#000000", MmuFilamentSync.getSnapshot(printerB)?.getJSONArray("gate_color")?.getString(0))
        assertTrue(Prefs.isMmuSyncedPrinter(printerA))
        assertTrue(Prefs.isMmuSyncedPrinter(printerB))
    }

    @Test fun parserAcceptsOneTwoAndEightTools() {
        for (count in listOf(1, 2, 8)) {
            val result = MmuFilamentSync.fromMoonraker(response(IntArray(count) { it }, IntArray(count) { 1 }))
            assertEquals(count, result.size)
            assertEquals("PLA", result[0]?.type)
            assertEquals(0xFF112233.toInt(), result[0]?.color)
        }
    }

    @Test fun crealityCfsSyncFallsBackToMoonrakerBoxAndPreservesPhysicalGateIndices() {
        Prefs.setFilamentSlots(listOf("PLA", "PETG", "ABS", "TPU").mapIndexed { index, type ->
            FilamentSlot(0xFF102030.toInt() + index, type)
        })
        val result = syncCrealityBox()

        assertEquals(3 to null, result)
        val palette = Prefs.getFilamentSlots()
        assertEquals(16, palette.size)
        assertNull(palette[0].type)
        assertEquals("PETG", palette[1].type)
        assertEquals("ABS", palette[2].type)
        assertEquals("PLA", palette[3].type)
        assertTrue(MmuFilamentSync.gateSlot(title, 2)?.unknownLoadedSpool == true)
        assertEquals("ACE 3 · ${SliceBeam.requireInstance().getString(R.string.MmuUnknownLoadedSpool)} · #AABBCC",
            MmuFilamentSync.gateLabel(title, 2))
        assertArrayEquals(IntArray(16) { it }, MmuFilamentSync.getSelectedGates(title, 16))
        assertArrayEquals(intArrayOf(1, 2, 3), MmuFilamentAssignments.availableGates(title, false))
        assertEquals(16, MmuFilamentSync.getSnapshot(title)?.getJSONArray("ttg_map")?.length())
    }

    @Test fun realPreferencesRoundTripLargeFilamentListsAndEnforceSixteenSlotLimit() {
        for (requestedCount in listOf(1, 4, 8, 16, 24)) {
            val requested = List(requestedCount) { index ->
                FilamentSlot(0xFF000000.toInt() or ((index + 1) * 0x010101), "Long synthetic material name $index")
            }
            Prefs.setStoredFilamentSlots(requested)
            requireNotNull(Prefs.getPrefs()).edit().commit()
            val restored = Prefs.getStoredFilamentSlots()
            val expectedCount = minOf(requestedCount, Prefs.MAX_FILAMENT_COLORS)
            assertEquals("stored count for request $requestedCount", expectedCount, restored.size)
            restored.forEachIndexed { index, slot ->
                assertEquals("Long synthetic material name $index", slot.type)
                assertEquals(requested[index].color, slot.color)
            }
        }
    }

    @Test fun parserDoesNotInventFilamentsForEmptyUnknownOrMalformedSlots() {
        val payload = response(intArrayOf(0, 1, 2, 3), intArrayOf(1, 0, 1, 1))
        val mmu = payload.getJSONObject("result").getJSONObject("status").getJSONObject("mmu")
        mmu.getJSONArray("gate_material").put(2, "unknown")
        mmu.getJSONArray("gate_color").put(3, "123456ZZ")
        val result = MmuFilamentSync.fromMoonraker(payload)
        assertNotNull(result[0])
        assertNull(result[1])
        assertNull(result[2]?.type)
        assertTrue(result[2]?.unknownLoadedSpool == true)
        assertEquals(0xFF778899.toInt(), result[2]?.color)
        assertNull(result[3])
    }

    @Test(expected = JSONException::class) fun disabledMmuIsRejected() {
        val payload = response(intArrayOf(0), intArrayOf(1))
        payload.getJSONObject("result").getJSONObject("status").getJSONObject("mmu").put("enabled", false)
        MmuFilamentSync.fromMoonraker(payload)
    }

    @Test fun fullSyncExpandsShrinksAndRefreshesTheAssignedPhysicalGates() {
        Prefs.setFilamentSlots(listOf(FilamentSlot(0, "ABS")))
        assertEquals(4 to null, sync(response(intArrayOf(0, 1, 2, 3), intArrayOf(1, 1, 1, 1))))
        assertEquals(4, Prefs.getFilamentSlots().size)

        val gapPayload = response(intArrayOf(0, 1, 2, 3), intArrayOf(1, 0, 1, 0))
        assertEquals(2 to null, sync(gapPayload))
        assertEquals(listOf("PLA", null, "PETG", null), Prefs.getFilamentSlots().map { it.type })
        assertArrayEquals(intArrayOf(0, 1, 2, 3), MmuFilamentSync.getSelectedGates(title, 4))
        assertArrayEquals(intArrayOf(0, 2), MmuFilamentSync.loadedGates(title))
        assertEquals(0, MmuFilamentSync.toolForGate(title, 0))
        assertEquals(2, MmuFilamentSync.toolForGate(title, 2))

        gapPayload.getJSONObject("result").getJSONObject("status").getJSONObject("mmu").getJSONArray("gate_color").put(2, "ABCDEF")
        assertEquals(2 to null, sync(gapPayload, full = false))
        assertEquals(4, Prefs.getFilamentSlots().size)
        assertEquals(0xFFABCDEF.toInt(), Prefs.getFilamentSlots()[2].color)

        assertEquals(1 to null, sync(response(intArrayOf(0, 1, 2, 3), intArrayOf(0, 0, 0, 1))))
        assertEquals(4, Prefs.getFilamentSlots().size)
        assertEquals(listOf(null, null, null, "TPU"), Prefs.getFilamentSlots().map { it.type })
        assertArrayEquals(intArrayOf(0, 1, 2, 3), MmuFilamentSync.getSelectedGates(title, 4))
    }

    @Test fun explicitSyncRejectsPrinterToolMapsThatWouldRewriteSlotCommands() {
        Prefs.setFilamentSlots(listOf(FilamentSlot(0xFF123456.toInt(), "ABS")))
        val mapped = response(intArrayOf(2, 0, 3, 1), intArrayOf(1, 0, 1, 0))
        val result = sync(mapped)
        assertEquals(0, result.first)
        assertTrue(result.second.orEmpty().contains("T0"))
        assertEquals(listOf("ABS"), Prefs.getFilamentSlots().map { it.type })
    }

    @Test fun emptySecondAceGateStaysVisibleWithoutShiftingTheOtherGates() {
        val payload = response(intArrayOf(0, 1, 2, 3), intArrayOf(1, 0, 1, 1))
        assertEquals(3 to null, sync(payload))
        assertEquals(listOf("PLA", null, "PETG", "TPU"), Prefs.getFilamentSlots().map { it.type })
        assertArrayEquals(intArrayOf(0, 1, 2, 3), MmuFilamentSync.getSelectedGates(title, 4))
        assertArrayEquals(intArrayOf(0, 1, 2, 3), MmuFilamentSync.paletteGates(title, 4))
        MmuFilamentSync.setSelectedGates(title, intArrayOf(3, 0, 2, 1))
        assertArrayEquals(intArrayOf(0, 1, 2, 3), MmuFilamentSync.paletteGates(title, 4))
        MmuFilamentSync.removeSelectedGate(1, 4)
        assertArrayEquals(intArrayOf(0, 2, 3), MmuFilamentSync.paletteGates(title, 3))
        assertArrayEquals(intArrayOf(3, 2, 1), MmuFilamentSync.getSelectedGates(title, 3))
        MmuFilamentSync.setSelectedGates(title, intArrayOf(0, 1, 2, 3))
        assertEquals(3 to null, sync(payload))
        val physical = MmuFilamentSync.physicalGates(title)
        assertEquals(listOf(0, 1, 2, 3), physical.map { it.index })
        assertEquals(listOf(0, 1, 2, 3), physical.map { it.printerTool })
        assertEquals(listOf(true, false, true, true), physical.map { it.occupied })
        assertNull(physical[1].filament)
        assertEquals("PETG", physical[2].filament?.type)
        assertEquals("TPU", physical[3].filament?.type)
    }

    @Test fun loadedUnknownMaterialKeepsItsColorAndPhysicalGateIndex() {
        val configured = listOf("PLA", "PETG", "ABS", "TPU").mapIndexed { index, type ->
            FilamentSlot(0xFF102030.toInt() + index, type)
        }
        Prefs.setFilamentSlots(configured)
        val payload = response(intArrayOf(0, 1, 2, 3), intArrayOf(1, 1, 1, 1))
        val mmu = payload.getJSONObject("result").getJSONObject("status").getJSONObject("mmu")
        mmu.getJSONArray("gate_material").put(1, "unknown")
        // Creality-compatible 0RRGGBB form: the leading zero is not part of the RGB value.
        mmu.getJSONArray("gate_color").put(1, "#0123456")

        assertEquals(4 to null, sync(payload, full = false))
        assertEquals(listOf("PLA", "PETG", "PETG", "TPU"), Prefs.getFilamentSlots().map { it.type })
        assertEquals(4 to null, sync(payload, full = true))
        assertEquals(listOf("PLA", "PETG", "PETG", "TPU"), Prefs.getFilamentSlots().map { it.type })
        assertEquals(0xFF123456.toInt(), MmuFilamentSync.gateSlot(title, 1)?.color)
        assertNull(MmuFilamentSync.gateSlot(title, 1)?.type)
        assertTrue(MmuFilamentSync.gateSlot(title, 1)?.unknownLoadedSpool == true)
        assertArrayEquals(intArrayOf(0, 1, 2, 3), MmuFilamentAssignments.availableGates(title, false))
        assertEquals("ACE 2 · ${SliceBeam.requireInstance().getString(R.string.MmuUnknownLoadedSpool)} · #123456",
            MmuFilamentSync.gateLabel(title, 1))
        val assigned = MmuFilamentSync.assignedGates(title, intArrayOf(1))
        assertEquals(1, assigned?.single()?.gate)
        assertNull(assigned?.single()?.material)
    }

    @Test fun unknownLoadedGateWithoutValidColorRemainsUnavailable() {
        val payload = response(intArrayOf(0, 1, 2, 3), intArrayOf(1, 1, 1, 1))
        val mmu = payload.getJSONObject("result").getJSONObject("status").getJSONObject("mmu")
        mmu.getJSONArray("gate_material").put(1, "unknown")
        mmu.getJSONArray("gate_color").put(1, "not-a-color")

        assertEquals(0 to SliceBeam.requireInstance().getString(R.string.MmuSyncIncompleteSlot, 2), sync(payload))
        assertNull(MmuFilamentSync.fromMoonraker(payload)[1])
    }

    @Test fun loadedGateWithoutFirmwareToolIsVisibleButNotSelectable() {
        assertEquals(3 to null, sync(response(intArrayOf(0, 1, 2), intArrayOf(1, 1, 1, 1))))
        assertEquals(4, MmuFilamentSync.physicalGates(title).size)
        assertArrayEquals(intArrayOf(0, 1, 2), MmuFilamentAssignments.availableGates(title, false))
    }

    @Test fun disconnectedAceUsesReadyExternalSpoolAndPreservesConfiguredMaterial() {
        assertEquals(4 to null, sync(response(intArrayOf(0, 1, 2, 3), intArrayOf(1, 1, 1, 1))))
        assertNotNull("the fixture starts with an old ACE snapshot", MmuFilamentSync.getSnapshot(title))
        val configured = listOf(FilamentSlot(0xFF2468AC.toInt(), "PETG"))
        Prefs.setFilamentSlots(configured)

        assertEquals(1 to null, syncExternalSpool())
        assertTrue(MmuFilamentSync.externalSpoolReady(title))
        assertNull("a disconnected ACE snapshot must not remain visible or routable", MmuFilamentSync.getSnapshot(title))
        assertFalse(Prefs.isMmuSyncedPrinter(title))
        val retained = Prefs.getFilamentSlots()
        assertEquals(1, retained.size)
        assertEquals(configured.single().color, retained.single().color)
        assertEquals(configured.single().type, retained.single().type)
        assertTrue("external spool mode must not manufacture ACE gates", MmuFilamentSync.physicalGates(title).isEmpty())

        assertNotNull(AnycubicExternalSpoolStatus.parse(externalSpoolResponse()))
        assertNull(AnycubicExternalSpoolStatus.parse(externalSpoolResponse(aceConnected = true)))
        assertNull(AnycubicExternalSpoolStatus.parse(externalSpoolResponse(includeHubInfo = false)))
        assertNull(AnycubicExternalSpoolStatus.parse(externalSpoolResponse(ready = false)))
        assertNull(AnycubicExternalSpoolStatus.parse(externalSpoolResponse(filamentPresent = 0)))
    }

    @Test fun freshUploadCheckRejectsASlotEmptiedAfterTheSendDialogOpened() {
        val tools = intArrayOf(0, 1, 2)
        MmuFilamentSync.setSelectedGates(title, intArrayOf(0, 1, 2))
        assertNull(sync(response(intArrayOf(0, 1, 2, 3), intArrayOf(1, 1, 1, 1)), full = false).second)
        val checkedAssignment = MmuFilamentSync.assignedGates(title, tools)
        assertNotNull(checkedAssignment)
        assertNull(SlicePreparationTools.firstUnavailableAceTool(
            tools, MmuFilamentSync.getSelectedGates(title, 3),
            MmuFilamentAssignments.availableGates(title, false)))

        assertNull(sync(response(intArrayOf(0, 1, 2, 3), intArrayOf(1, 0, 1, 1)), full = false).second)
        assertArrayEquals(intArrayOf(0, 2, 3), MmuFilamentSync.loadedGates(title))
        assertNotEquals(checkedAssignment, MmuFilamentSync.assignedGates(title, tools))
        assertEquals(1, SlicePreparationTools.firstUnavailableAceTool(
            tools, MmuFilamentSync.getSelectedGates(title, 3),
            MmuFilamentAssignments.availableGates(title, false)))
    }

    @Test fun freshUploadCheckDetectsAChangedColorOrMaterialOnALoadedGate() {
        MmuFilamentSync.setSelectedGates(title, intArrayOf(0))
        val first = response(intArrayOf(0, 1, 2, 3), intArrayOf(1, 1, 1, 1))
        assertNull(sync(first, full = false).second)
        val before = MmuFilamentSync.assignedGates(title, intArrayOf(0))
        val changed = response(intArrayOf(0, 1, 2, 3), intArrayOf(1, 1, 1, 1))
        changed.getJSONObject("result").getJSONObject("status").getJSONObject("mmu")
            .getJSONArray("gate_color").put(0, "FF0000")
        assertNull(sync(changed, full = false).second)
        assertNotEquals(before, MmuFilamentSync.assignedGates(title, intArrayOf(0)))
    }

    @Test fun sliceInventorySignatureTracksColorMaterialOccupancyAndToolMappingOnly() {
        val initial = response(intArrayOf(0, 1, 2, 3), intArrayOf(1, 1, 1, 1))
        assertNull(sync(initial, full = false).second)
        val original = requireNotNull(MmuFilamentSync.inventorySignature(title))

        val metadataOnly = response(intArrayOf(0, 1, 2, 3), intArrayOf(1, 1, 1, 1))
        metadataOnly.getJSONObject("result").getJSONObject("status").getJSONObject("mmu")
            .put("busy", true).put("last_operation", "unload")
        assertNull(sync(metadataOnly, full = false).second)
        assertEquals("volatile MMU status does not invalidate a slice", original,
            MmuFilamentSync.inventorySignature(title))

        val recolored = response(intArrayOf(0, 1, 2, 3), intArrayOf(1, 1, 1, 1))
        recolored.getJSONObject("result").getJSONObject("status").getJSONObject("mmu")
            .getJSONArray("gate_color").put(0, "0000FF")
        assertNull(sync(recolored, full = false).second)
        val afterColor = requireNotNull(MmuFilamentSync.inventorySignature(title))
        assertNotEquals("a physical color change must invalidate the old slice", original, afterColor)

        val rematerialized = response(intArrayOf(0, 1, 2, 3), intArrayOf(1, 1, 1, 1))
        rematerialized.getJSONObject("result").getJSONObject("status").getJSONObject("mmu")
            .getJSONArray("gate_material").put(0, "PETG")
        assertNull(sync(rematerialized, full = false).second)
        val afterMaterial = requireNotNull(MmuFilamentSync.inventorySignature(title))
        assertNotEquals("a physical material change must invalidate the old slice", afterColor, afterMaterial)

        val remapped = response(intArrayOf(1, 0, 2, 3), intArrayOf(1, 1, 1, 1))
        assertNull(sync(remapped, full = false).second)
        val afterMapping = requireNotNull(MmuFilamentSync.inventorySignature(title))
        assertNotEquals("a T-to-gate mapping change must invalidate the old slice", afterMaterial, afterMapping)

        val unloaded = response(intArrayOf(1, 0, 2, 3), intArrayOf(0, 1, 1, 1))
        assertNull(sync(unloaded, full = false).second)
        assertNotEquals("a slot becoming empty must invalidate the old slice",
            afterMapping, MmuFilamentSync.inventorySignature(title))
    }

    @Test fun automaticRefreshKeepsTheUsersSingleFilamentCount() {
        requireNotNull(Prefs.getPrefs()).edit().remove("mmu_choice_gates").commit()
        Prefs.setFilamentSlots(listOf(FilamentSlot(0, "ABS")))
        assertEquals(1 to null, sync(response(intArrayOf(0, 1, 2, 3), intArrayOf(1, 1, 1, 1)), full = false))
        assertEquals(1, Prefs.getFilamentSlots().size)
        assertEquals("PLA", Prefs.getFilamentSlots()[0].type)
        assertEquals(0xFF112233.toInt(), Prefs.getFilamentSlots()[0].color)
    }

    @Test fun deletingAMaterialKeepsTheRemainingPhysicalSlotsAligned() {
        MmuFilamentSync.setSelectedGates(title, intArrayOf(0, 2, 3))
        MmuFilamentSync.removeSelectedGate(0, 3)
        assertArrayEquals(intArrayOf(2, 3), MmuFilamentSync.getSelectedGates(title, 2))
        assertArrayEquals(intArrayOf(2), MmuFilamentSync.getSelectedGates(title, 1))
        assertNull(MmuFilamentSync.getSelectedGates(title, 3))
    }

    @Test fun disabledMmuReturnsAReadableExplanationAndPreservesThePalette() {
        Prefs.setFilamentSlots(listOf(FilamentSlot(0x123456, "ABS")))
        val payload = response(intArrayOf(0), intArrayOf(1))
        payload.getJSONObject("result").getJSONObject("status").getJSONObject("mmu").put("enabled", false)
        assertEquals(SliceBeam.requireInstance().getString(R.string.MmuSyncNotAvailable), sync(payload).second)
        assertEquals("ABS", Prefs.getFilamentSlots().single().type)
    }

    @Test fun explicitExternalSpoolReadyStatusIsRetainedWithEnabledMmuConfiguration() {
        val payload = response(intArrayOf(0), intArrayOf(0))
        payload.getJSONObject("result").getJSONObject("status").put("filament_hub", JSONObject()
            .put("ext_spool", 1)
            .put("ext_spool_status", "ready")
            .put("filament_hubs", JSONObject.NULL))

        assertEquals(0 to null, sync(payload, full = false))
        assertTrue(MmuFilamentSync.isExternalSpoolReady(title))
    }

    @Test fun incompleteAndEmptyPrinterResponsesLeaveThePreviousPaletteAndMappingIntact() {
        Prefs.setFilamentSlots(listOf(FilamentSlot(0x123456, "ABS")))
        MmuFilamentSync.setSelectedGates(title, intArrayOf(3))
        val invalid = response(intArrayOf(0), intArrayOf(1))
        val invalidMmu = invalid.getJSONObject("result").getJSONObject("status").getJSONObject("mmu")
        invalidMmu.getJSONArray("gate_material").put(0, "unknown")
        invalidMmu.getJSONArray("gate_color").put(0, "")
        assertNotNull(sync(invalid).second)
        assertNotNull(sync(response(intArrayOf(0), intArrayOf(0))).second)
        assertEquals(1, Prefs.getFilamentSlots().size)
        assertEquals("ABS", Prefs.getFilamentSlots()[0].type)
        assertEquals(0xFF123456.toInt(), Prefs.getFilamentSlots()[0].color)
        assertArrayEquals(intArrayOf(3), MmuFilamentSync.getSelectedGates(title, 1))
    }

    @Test fun projectAutoSyncPreservesFileColorsAndManualSyncOnlyChangesTemporaryPalette() {
        Prefs.setFilamentSlots(listOf(FilamentSlot(0xFF123456.toInt(), "ABS")))
        MmuFilamentSync.setSelectedGates(title, intArrayOf(3))
        val savedPalette = Prefs.getPrefs()!!.getString("filament_palette", null)
        TemporaryProjectSettings.activate(ThreeMfProjectSettings("project.3mf", emptyMap(), listOf(
            mapOf("filament_colour" to "#0000FF", "filament_type" to "PLA"),
            mapOf("filament_colour" to "#FF0000", "filament_type" to "PETG"),
        ), mapOf("filament_colour" to ConfigOptionType.STRINGS, "filament_type" to ConfigOptionType.STRINGS)))
        assertNull(sync(response(intArrayOf(0, 1, 2, 3), intArrayOf(1, 1, 1, 1)), full = false).second)
        assertEquals(2, Prefs.getFilamentSlots().size)
        assertEquals(0xFF0000FF.toInt(), Prefs.getFilamentSlots()[0].color)
        assertNull(sync(response(intArrayOf(0, 1, 2, 3), intArrayOf(1, 1, 1, 1)), full = true).second)
        assertEquals(4, Prefs.getFilamentSlots().size)
        assertEquals(savedPalette, Prefs.getPrefs()!!.getString("filament_palette", null))
        assertEquals("3", Prefs.getPrefs()!!.getString("mmu_choice_gates", null))
        TemporaryProjectSettings.clear()
        assertEquals("ABS", Prefs.getFilamentSlots().single().type)
        assertArrayEquals(intArrayOf(3), MmuFilamentSync.getSelectedGates(title, 1))
    }

    private fun response(mapping: IntArray, status: IntArray): JSONObject {
        val materials = listOf("PLA", "ABS", "PETG", "TPU")
        val colors = listOf("#112233", "445566", "778899FF", "AABBCC")
        val mmu = JSONObject().put("enabled", true).put("ttg_map", JSONArray(mapping.toList()))
            .put("gate_status", JSONArray(status.toList()))
            .put("gate_material", JSONArray(status.indices.map { materials[it % 4] }))
            .put("gate_color", JSONArray(status.indices.map { colors[it % 4] }))
        return JSONObject().put("result", JSONObject().put("status", JSONObject().put("mmu", mmu)))
    }

    private fun externalSpoolResponse(
        aceConnected: Boolean = false,
        ready: Boolean = true,
        filamentPresent: Int = 1,
        includeHubInfo: Boolean = true,
    ): String {
        val hub = JSONObject().put("ext_spool", 1)
            .put("ext_spool_status", if (ready) "ready" else "unloading")
            .put("filament_present", filamentPresent)
        if (includeHubInfo) hub.put("filament_hubs", if (aceConnected) JSONArray().put(JSONObject().put("id", 0)) else JSONObject.NULL)
        return JSONObject().put("result", JSONObject().put("status", JSONObject().put("filament_hub", hub))).toString()
    }

    private fun syncExternalSpool(): Pair<Int, String?> {
        val responses = listOf(
            "?mmu" to """{"result":{"status":{"mmu":{"enabled":false}}}}""",
            "?box" to """{"result":{"status":{}}}""",
            "?filament_hub" to externalSpoolResponse(),
        )
        val failure = AtomicReference<Throwable?>()
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 10_000
            val worker = Thread {
                try {
                    for ((path, text) in responses) {
                        server.accept().use { socket ->
                            socket.soTimeout = 10_000
                            val reader = socket.getInputStream().bufferedReader()
                            assertTrue(reader.readLine().startsWith("GET /printer/objects/query$path "))
                            while (!reader.readLine().isNullOrEmpty()) { }
                            val body = text.toByteArray(Charsets.UTF_8)
                            socket.getOutputStream().apply {
                                write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                                write(body)
                                flush()
                            }
                        }
                    }
                } catch (error: Throwable) { failure.set(error) }
            }.apply { start() }
            val completed = CountDownLatch(1)
            val result = AtomicReference<Pair<Int, String?>>()
            val printer = ConfigObject(title).apply { put("print_host", "http://127.0.0.1:${server.localPort}") }
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                MmuFilamentSync.synchronizeAll(printer, MmuFilamentSync.Callback { updated, error ->
                    result.set(updated to error)
                    completed.countDown()
                })
            }
            assertTrue("external spool sync callback timed out", completed.await(30, TimeUnit.SECONDS))
            worker.join(2_000)
            failure.get()?.let { throw AssertionError("External spool test Moonraker host failed", it) }
            return requireNotNull(result.get())
        }
    }

    private fun sync(response: JSONObject, full: Boolean = true, printerTitle: String = title): Pair<Int, String?> {
        val body = response.toString().toByteArray(Charsets.UTF_8)
        val failure = AtomicReference<Throwable?>()
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 10_000
            val worker = Thread {
                try {
                    server.accept().use { socket ->
                        socket.soTimeout = 10_000
                        val reader = socket.getInputStream().bufferedReader()
                        assertTrue(reader.readLine().startsWith("GET /printer/objects/query?mmu&filament_hub "))
                        while (!reader.readLine().isNullOrEmpty()) { }
                        socket.getOutputStream().apply {
                            write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            write(body)
                            flush()
                        }
                    }
                    val mmu = response.optJSONObject("result")?.optJSONObject("status")?.optJSONObject("mmu")
                    if (mmu != null && !mmu.optBoolean("enabled", false)) {
                        server.accept().use { socket ->
                            socket.soTimeout = 10_000
                            val reader = socket.getInputStream().bufferedReader()
                            assertTrue(reader.readLine().startsWith("GET /printer/objects/query?box "))
                            while (!reader.readLine().isNullOrEmpty()) { }
                            val emptyBox = """{"result":{"status":{}}}""".toByteArray(Charsets.UTF_8)
                            socket.getOutputStream().apply {
                                write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${emptyBox.size}\r\nConnection: close\r\n\r\n".toByteArray())
                                write(emptyBox)
                                flush()
                            }
                        }
                    }
                } catch (error: Throwable) { failure.set(error) }
            }.apply { start() }
            val completed = CountDownLatch(1)
            val result = AtomicReference<Pair<Int, String?>>()
            val printer = ConfigObject(printerTitle).apply { put("print_host", "http://127.0.0.1:${server.localPort}") }
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val callback = MmuFilamentSync.Callback { updated, error ->
                    result.set(updated to error)
                    completed.countDown()
                }
                if (full) MmuFilamentSync.synchronizeAll(printer, callback) else MmuFilamentSync.synchronize(printer, callback)
            }
            assertTrue("Filament sync callback timed out", completed.await(20, TimeUnit.SECONDS))
            worker.join(2_000)
            failure.get()?.let { throw AssertionError("Test printer failed", it) }
            return requireNotNull(result.get())
        }
    }
    private fun syncCrealityBox(): Pair<Int, String?> {
        val responses = listOf(
            """{"result":{"status":{"mmu":{"enabled":false}}}}""",
            """{"result":{"status":{"box":{
                "T1":{"state":"connect","material_type":["-1","000003","999999","000001"],"color_value":["-1","0FF1E1E","0AABBCC","000FF00"]},
                "T2":{"state":"None","material_type":["000002","-1","-1","-1"],"color_value":["0FFFFFF","-1","-1","-1"]}
            }}}}""",
        )
        val failure = AtomicReference<Throwable?>()
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 10_000
            val worker = Thread {
                try {
                    for ((index, response) in responses.withIndex()) {
                        server.accept().use { socket ->
                        socket.soTimeout = 10_000
                        val reader = socket.getInputStream().bufferedReader()
                            val expectedPath = if (index == 0) "?mmu&filament_hub" else "?box"
                            assertTrue(reader.readLine().startsWith("GET /printer/objects/query$expectedPath "))
                            while (!reader.readLine().isNullOrEmpty()) { }
                            val body = response.toByteArray(Charsets.UTF_8)
                            socket.getOutputStream().apply {
                                write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                                write(body)
                                flush()
                            }
                        }
                    }
                } catch (error: Throwable) { failure.set(error) }
            }.apply { start() }
            val completed = CountDownLatch(1)
            val result = AtomicReference<Pair<Int, String?>>()
            val printer = ConfigObject(title).apply { put("print_host", "http://127.0.0.1:${server.localPort}") }
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                MmuFilamentSync.synchronizeAll(printer, MmuFilamentSync.Callback { updated, error ->
                    result.set(updated to error)
                    completed.countDown()
                })
            }
            assertTrue("Creality CFS sync callback timed out", completed.await(30, TimeUnit.SECONDS))
            worker.join(2_000)
            failure.get()?.let { throw AssertionError("Test Creality Moonraker host failed", it) }
            return requireNotNull(result.get())
        }
    }
}
