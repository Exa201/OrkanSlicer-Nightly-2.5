package ru.ytkab0bp.slicebeam.utils

import android.text.TextUtils
import com.loopj.android.http.AsyncHttpClient
import com.loopj.android.http.AsyncHttpResponseHandler
import cz.msebera.android.httpclient.Header
import cz.msebera.android.httpclient.message.BasicHeader
import org.json.JSONException
import org.json.JSONObject
import ru.ytkab0bp.slicebeam.SliceBeam
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.config.ConfigObject
import ru.ytkab0bp.slicebeam.config.TemporaryProjectSettings
import java.util.ArrayList

/** Reads live Moonraker MMU slots and preserves their physical gate assignment. */
class MmuFilamentSync private constructor() {
    fun interface Callback {
        fun onResult(updated: Int, error: String?)
    }

    companion object {
        data class PhysicalGate(val index: Int, val printerTool: Int, val occupied: Boolean, val filament: FilamentSlot?)
        data class AssignedGate(val modelTool: Int, val gate: Int, val printerTool: Int,
                                val material: String?, val color: Int?)
        private const val SNAPSHOT_PRINTER = "mmu_snapshot_printer"
        private const val SNAPSHOT = "mmu_snapshot"
        private const val CHOICE_PRINTER = "mmu_choice_printer"
        private const val CHOICE_GATES = "mmu_choice_gates"
        private const val PALETTE_PRINTER = "mmu_palette_printer"
        private const val PALETTE_GATES = "mmu_palette_gates"

        @JvmStatic
        fun synchronize(printer: ConfigObject, callback: Callback) {
            synchronizeInternal(printer, false, callback)
        }

        /** User-requested sync: replace the count, materials, colors and gate assignments together. */
        @JvmStatic
        fun synchronizeAll(printer: ConfigObject, callback: Callback) {
            synchronizeInternal(printer, true, callback)
        }

        private fun synchronizeInternal(printer: ConfigObject, replacePalette: Boolean, callback: Callback) {
            var host = printer.get("print_host")?.trim() ?: ""
            if (TextUtils.isEmpty(host)) {
                callback.onResult(0, message(R.string.PrinterWebHostMissing))
                return
            }

            host = host.trim()
            if (!host.startsWith("http://") && !host.startsWith("https://")) host = "http://$host"
            while (host.endsWith("/")) host = host.dropLast(1)
            val apiKey = printer.get("printhost_apikey") ?: ""
            val headers: Array<Header> = if (TextUtils.isEmpty(apiKey)) emptyArray()
                else arrayOf(BasicHeader("X-Api-Key", apiKey))

            val client = AsyncHttpClient().apply { setTimeout(15_000) }
            fun applyBody(body: ByteArray?) {
                try {
                    if (body == null || body.isEmpty()) throw JSONException(message(R.string.MmuSyncEmptyResponse))
                    val count = applySyncResponse(printer, replacePalette, String(body, Charsets.UTF_8))
                    callback.onResult(count, null)
                } catch (error: Exception) {
                    callback.onResult(0, error.message ?: message(R.string.MmuSyncFailed))
                }
            }
            fun requestCrealityBox() {
                client.get(SliceBeam.requireInstance(), "$host/printer/objects/query?box", headers, null,
                    object : AsyncHttpResponseHandler() {
                        override fun onSuccess(statusCode: Int, responseHeaders: Array<Header>?, body: ByteArray?) = applyBody(body)

                        override fun onFailure(
                            statusCode: Int,
                            responseHeaders: Array<Header>?,
                            body: ByteArray?,
                            error: Throwable?,
                        ) {
                            callback.onResult(0, error?.message?.takeIf { it.isNotBlank() }
                                ?: message(R.string.MmuSyncNotAvailable))
                        }
                    })
            }
            client.get(SliceBeam.requireInstance(), "$host/printer/objects/query?mmu", headers, null,
                object : AsyncHttpResponseHandler() {
                    override fun onSuccess(statusCode: Int, responseHeaders: Array<Header>?, body: ByteArray?) {
                        val hasMmu = try {
                            body != null && JSONObject(String(body, Charsets.UTF_8))
                                .optJSONObject("result")?.optJSONObject("status")?.optJSONObject("mmu")?.optBoolean("enabled", false) == true
                        } catch (_: Exception) { false }
                        if (hasMmu) applyBody(body) else requestCrealityBox()
                    }

                    override fun onFailure(
                        statusCode: Int,
                        responseHeaders: Array<Header>?,
                        body: ByteArray?,
                        error: Throwable?,
                    ) {
                        if (statusCode == 400 || statusCode == 404) {
                            requestCrealityBox()
                            return
                        }
                        val detail = if (body != null && body.isNotEmpty()) {
                            String(body, Charsets.UTF_8)
                        } else {
                            error?.message?.takeIf { it.isNotBlank() } ?: "HTTP $statusCode"
                        }
                        callback.onResult(0, detail)
                    }
                })
        }

        private fun applySyncResponse(printer: ConfigObject, replacePalette: Boolean, responseText: String): Int {
            val rawResponse = JSONObject(responseText)
            val rawMmu = rawResponse.optJSONObject("result")?.optJSONObject("status")?.optJSONObject("mmu")
            val response = if (rawMmu != null) rawResponse else CrealityCfsParser.toMmuResponse(rawResponse)
                ?: throw JSONException(message(R.string.MmuSyncNotAvailable))
            val normalizedResponseText = response.toString()
            val mmu = response.getJSONObject("result").getJSONObject("status").getJSONObject("mmu")
            if (!mmu.optBoolean("enabled", false)) throw JSONException(message(R.string.MmuSyncNotAvailable))
            val remote = fromMoonraker(response)
            val printerGates = gateSlots(mmu)
            val prefs = Prefs.getPrefs() ?: throw IllegalStateException("Preferences are not initialized")
            val current = Prefs.getFilamentSlots()
            val updatedSlots: List<FilamentSlot>
            val count: Int
            if (replacePalette) {
                val mapping = mmu.getJSONArray("ttg_map")
                val toolToGate = IntArray(minOf(mapping.length(), Prefs.MAX_FILAMENT_COLORS)) { mapping.optInt(it, -1) }
                val status = mmu.optJSONArray("gate_status")
                val incompleteGate = toolToGate.firstOrNull { gate ->
                    gate >= 0 && status != null && status.optInt(gate, 0) > 0 && printerGates.getOrNull(gate) == null
                }
                if (incompleteGate != null) throw JSONException(
                    SliceBeam.requireInstance().getString(R.string.MmuSyncIncompleteSlot, incompleteGate + 1),
                )
                if (toolToGate.none { printerGates.getOrNull(it) != null }) {
                    throw JSONException(message(R.string.MmuAssignmentNoLoadedSlots))
                }
                val synced = FilamentPaletteSyncPolicy.replaceFromPrinter(printerGates, toolToGate, current)
                updatedSlots = synced.slots
                count = updatedSlots.count { !it.type.isNullOrBlank() }
                setSelectedGates(printer.title.orEmpty(), synced.gates)
                if (!TemporaryProjectSettings.active) setPaletteGates(printer.title.orEmpty(), synced.gates)
            } else {
                // Once assigned, logical T1 may refer to physical gate 3 rather than gate 2.
                val choices = getSelectedGates(printer.title.orEmpty(), current.size)
                val liveTools = choices?.map { printerGates.getOrNull(it) } ?: remote
                updatedSlots = FilamentPaletteSyncPolicy.updateExisting(current, liveTools)
                count = liveTools.take(current.size).count { it != null }
            }
            if (replacePalette || !TemporaryProjectSettings.active) Prefs.setFilamentSlots(updatedSlots)
            Prefs.setMmuSyncedPrinter(printer.title)
            prefs.edit()
                .putString(MmuPrinterPreferenceKeys.forPrinter("snapshot", printer.title.orEmpty()), normalizedResponseText)
                .putString(SNAPSHOT_PRINTER, printer.title)
                .putString(SNAPSHOT, normalizedResponseText)
                .apply()
            return count
        }

        private fun message(resource: Int): String = SliceBeam.requireInstance().getString(resource)

        @JvmStatic
        fun getSnapshot(printerTitle: String): JSONObject? {
            val prefs = Prefs.getPrefs()
            if (prefs == null) return null
            val scoped = prefs.getString(MmuPrinterPreferenceKeys.forPrinter("snapshot", printerTitle), null)
            val legacy = if (printerTitle == prefs.getString(SNAPSHOT_PRINTER, null))
                prefs.getString(SNAPSHOT, null) else null
            val raw = scoped ?: legacy ?: return null
            if (scoped == null && legacy != null) {
                prefs.edit().putString(MmuPrinterPreferenceKeys.forPrinter("snapshot", printerTitle), legacy).apply()
            }
            return try {
                JSONObject(raw)
                    .getJSONObject("result").getJSONObject("status").getJSONObject("mmu")
            } catch (_: Exception) {
                null
            }
        }

        @JvmStatic
        fun getSelectedGates(printerTitle: String, toolCount: Int): IntArray? {
            if (TemporaryProjectSettings.active) return TemporaryProjectSettings.selectedGates(printerTitle, toolCount)
            val prefs = Prefs.getPrefs() ?: return null
            val scopedKey = MmuPrinterPreferenceKeys.forPrinter("choice_gates", printerTitle)
            val scoped = prefs.getString(scopedKey, null)
            val legacy = if (printerTitle == prefs.getString(CHOICE_PRINTER, null))
                prefs.getString(CHOICE_GATES, null) else null
            val raw = scoped ?: legacy
            if (TextUtils.isEmpty(raw)) return null
            if (scoped == null && legacy != null) prefs.edit().putString(scopedKey, legacy).apply()
            val gateText = raw ?: return null
            val parts = gateText.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            // A one-color model only needs the first choice of a larger synced palette.
            if (toolCount < 1 || parts.size < toolCount) return null
            return try {
                IntArray(toolCount) { index -> parts[index].toInt() }
            } catch (_: NumberFormatException) {
                null
            }
        }

        @JvmStatic
        fun setSelectedGates(printerTitle: String, gates: IntArray) {
            if (TemporaryProjectSettings.active) {
                TemporaryProjectSettings.setSelectedGates(printerTitle, gates)
                return
            }
            val value = gates.joinToString(",")
            Prefs.getPrefs()?.edit()
                ?.putString(MmuPrinterPreferenceKeys.forPrinter("choice_gates", printerTitle), value)
                ?.putString(CHOICE_PRINTER, printerTitle)
                ?.putString(CHOICE_GATES, value)
                ?.apply()
        }

        /** Physical order of the saved compact palette; later tool choices may differ. */
        internal fun setPaletteGates(printerTitle: String, gates: IntArray) {
            Prefs.getPrefs()?.edit()
                ?.putString(MmuPrinterPreferenceKeys.forPrinter("palette_gates", printerTitle), gates.joinToString(","))
                ?.putString(PALETTE_PRINTER, printerTitle)
                ?.putString(PALETTE_GATES, gates.joinToString(","))
                ?.apply()
        }

        fun paletteGates(printerTitle: String, slotCount: Int): IntArray? {
            if (slotCount <= 0) return null
            val prefs = Prefs.getPrefs() ?: return null
            val scopedKey = MmuPrinterPreferenceKeys.forPrinter("palette_gates", printerTitle)
            val scoped = prefs.getString(scopedKey, null)
            val legacy = if (prefs.getString(PALETTE_PRINTER, null) == printerTitle)
                prefs.getString(PALETTE_GATES, null) else null
            val saved = scoped ?: legacy
            if (scoped == null && legacy != null) prefs.edit().putString(scopedKey, legacy).apply()
            val raw = saved ?: getSelectedGates(printerTitle, slotCount)?.sorted()?.joinToString(",")
            val values = raw?.split(',')?.map { it.toIntOrNull() ?: return null } ?: return null
            return values.takeIf { it.size == slotCount && it.distinct().size == slotCount && it.all { gate -> gate >= 0 } }
                ?.toIntArray()
        }

        /** Removing a logical filament also removes its physical choice, so later tools stay aligned. */
        fun removeSelectedGate(index: Int, previousCount: Int) {
            if (TemporaryProjectSettings.active) {
                TemporaryProjectSettings.removeSelectedGate(index)
                return
            }
            val prefs = Prefs.getPrefs() ?: return
            val printer = prefs.getString(CHOICE_PRINTER, null) ?: return
            val gates = getSelectedGates(printer, previousCount) ?: return
            if (index !in gates.indices) return
            paletteGates(printer, previousCount)?.let { baseline ->
                setPaletteGates(printer, baseline.filterIndexed { slot, _ -> slot != index }.toIntArray())
            }
            setSelectedGates(printer, gates.filterIndexed { tool, _ -> tool != index }.toIntArray())
        }

        @JvmStatic
        fun loadedGates(printerTitle: String): IntArray {
            val status = getSnapshot(printerTitle)?.optJSONArray("gate_status") ?: return IntArray(0)
            val loaded = ArrayList<Int>()
            for (gate in 0 until status.length()) if (status.optInt(gate, 0) > 0) loaded.add(gate)
            return loaded.toIntArray()
        }

        /** The physical ACE view includes empty gates; never compress it into the slicer's tool palette. */
        @JvmStatic
        fun physicalGates(printerTitle: String): List<PhysicalGate> {
            val mmu = getSnapshot(printerTitle) ?: return emptyList()
            val status = mmu.optJSONArray("gate_status") ?: return emptyList()
            val mapping = mmu.optJSONArray("ttg_map")
            val filaments = try { gateSlots(mmu) } catch (_: JSONException) { emptyList() }
            return List(minOf(status.length(), Prefs.MAX_FILAMENT_COLORS)) { gate ->
                val tool = (0 until (mapping?.length() ?: 0)).firstOrNull {
                    mapping?.optInt(it, -1) == gate
                } ?: -1
                PhysicalGate(gate, tool, status.optInt(gate, 0) > 0, filaments.getOrNull(gate))
            }
        }

        @JvmStatic
        fun defaultGateForTool(printerTitle: String, tool: Int): Int {
            val mapping = getSnapshot(printerTitle)?.optJSONArray("ttg_map") ?: return -1
            return mapping.optInt(tool, -1)
        }

        /** Full firmware Tn→gate map. Keep entries for gates not used in the current slice. */
        @JvmStatic
        fun toolToGateMap(printerTitle: String): IntArray {
            val mapping = getSnapshot(printerTitle)?.optJSONArray("ttg_map") ?: return IntArray(0)
            return IntArray(minOf(mapping.length(), Prefs.MAX_FILAMENT_COLORS)) { tool ->
                mapping.optInt(tool, -1)
            }
        }

        @JvmStatic
        fun toolForGate(printerTitle: String, gate: Int): Int {
            val mapping = getSnapshot(printerTitle)?.optJSONArray("ttg_map") ?: return -1
            for (tool in 0 until mapping.length()) if (mapping.optInt(tool, -1) == gate) return tool
            return -1
        }

        /** Compare only gates used by this sliced job before and after the final printer refresh. */
        fun assignedGates(printerTitle: String, activeTools: IntArray): List<AssignedGate>? {
            if (getSnapshot(printerTitle) == null) return null
            val toolCount = (activeTools.maxOrNull() ?: -1) + 1
            val gates = MmuGateAssignmentPolicy.gatesForTools(
                activeTools,
                getSelectedGates(printerTitle, toolCount),
                IntArray(toolCount) { defaultGateForTool(printerTitle, it) },
            )
            return activeTools.mapIndexed { index, tool ->
                val gate = gates[index]
                val filament = gateSlot(printerTitle, gate)
                AssignedGate(tool, gate, toolForGate(printerTitle, gate),
                    filament?.takeUnless { it.unknownLoadedSpool }?.type, filament?.color)
            }
        }

        @JvmStatic
        fun gateLabel(printerTitle: String, gate: Int): String {
            val mmu = getSnapshot(printerTitle)
            val materials = mmu?.optJSONArray("gate_material")
            val colors = mmu?.optJSONArray("gate_color")
            val statuses = mmu?.optJSONArray("gate_status")
            if (materials == null || colors == null || statuses == null || gate < 0 || gate >= statuses.length()) {
                return "ACE ${gate + 1}"
            }
            if (statuses.optInt(gate, 0) <= 0) return "ACE ${gate + 1} · leer"
            val filament = gateSlots(mmu).getOrNull(gate)
            if (filament?.unknownLoadedSpool == true) {
                val color = String.format(java.util.Locale.ROOT, "#%06X", filament.color and 0xFFFFFF)
                val unknown = SliceBeam.requireInstance().getString(R.string.MmuUnknownLoadedSpool)
                return "ACE ${gate + 1} · $unknown · $color"
            }
            val material = filament?.type ?: materials.optString(gate, "Filament")
            val rawColor = filament?.let { String.format(java.util.Locale.ROOT, "#%06X", it.color and 0xFFFFFF) }.orEmpty()
            return "ACE ${gate + 1} · $material" +
                (if (TextUtils.isEmpty(rawColor)) "" else " · $rawColor")
        }

        /** Physical gate material/color, independent of the imported project's palette. */
        fun gateSlot(printerTitle: String, gate: Int): FilamentSlot? = try {
            getSnapshot(printerTitle)?.let { gateSlots(it).getOrNull(gate) }
        } catch (_: Exception) { null }

        @JvmStatic
        @Throws(JSONException::class)
        fun fromMoonraker(response: JSONObject): List<FilamentSlot?> {
            val mmu = response.getJSONObject("result").getJSONObject("status").getJSONObject("mmu")
            if (!mmu.optBoolean("enabled", false)) throw JSONException("MMU is disabled")
            val mapping = mmu.getJSONArray("ttg_map")
            if (mapping.length() == 0) throw JSONException("No MMU tool mappings available")
            val gates = gateSlots(mmu)
            return List(minOf(mapping.length(), Prefs.MAX_FILAMENT_COLORS)) { tool ->
                gates.getOrNull(mapping.optInt(tool, -1))
            }
        }

        private fun gateSlots(mmu: JSONObject): List<FilamentSlot?> {
            val colors = mmu.getJSONArray("gate_color")
            val materials = mmu.getJSONArray("gate_material")
            val gateStatus = mmu.optJSONArray("gate_status")
            val slots = ArrayList<FilamentSlot?>()
            for (gate in 0 until minOf(colors.length(), materials.length())) {
                if (gateStatus != null && gateStatus.optInt(gate, 0) <= 0) {
                    slots.add(null)
                    continue
                }
                val rawMaterial = materials.optString(gate, "").trim()
                val rawColor = colors.optString(gate, "").trim()
                val color = parseGateColor(rawColor)
                val material = rawMaterial.takeUnless {
                    it.isEmpty() || it.equals("unknown", ignoreCase = true) || it.equals("null", ignoreCase = true)
                }
                // Keep an unidentified loaded spool distinct from a real Generic PLA spool.
                // Sync preserves the configured project filament for that tool, while the
                // physical-slot UI and print-preparation check still show the spool as unknown.
                if (material == null && color != null) {
                    slots.add(FilamentSlot(color, null, unknownLoadedSpool = true))
                    continue
                }
                if (material == null || color == null) {
                    slots.add(null)
                    continue
                }
                slots.add(FilamentSlot(color, material))
            }
            return slots
        }

        /** Accepts RGB, ARGB and Creality's `0RRGGBB` color form. */
        private fun parseGateColor(raw: String): Int? {
            var digits = raw.trim().removePrefix("#").removePrefix("0x").removePrefix("0X")
            if (!digits.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
            digits = when {
                digits.length == 6 -> digits
                digits.length == 7 && digits[0] == '0' -> digits.drop(1)
                // Keep Moonraker's established eight-digit convention (RGB followed by alpha).
                digits.length == 8 -> digits.take(6)
                else -> return null
            }
            return digits.toLongOrNull(16)?.toInt()
        }
    }
}
