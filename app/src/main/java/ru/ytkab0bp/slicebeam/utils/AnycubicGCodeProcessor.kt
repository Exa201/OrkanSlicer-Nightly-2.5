package ru.ytkab0bp.slicebeam.utils

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.regex.Pattern
import java.util.regex.Matcher

/** Pure file transformation for the G-code metadata expected by Anycubic Kobra 3 firmware. */
class AnycubicGCodeProcessor private constructor() {
    class ScanResult private constructor(
        @JvmField val maxTool: Int,
        @JvmField val density: Double,
        @JvmField val densities: DoubleArray,
        @JvmField val volume: Double,
        @JvmField val totalMass: Double,
        @JvmField val hasReportedTotalMass: Boolean,
        @JvmField val computedFilamentMasses: DoubleArray,
        @JvmField val needsMassBreakdownRewrite: Boolean,
        @JvmField val hasFilamentMass: Boolean,
        @JvmField val hasFilamentMap: Boolean,
        @JvmField val hasFilamentTypes: Boolean,
        @JvmField val hasFilamentColors: Boolean,
        @JvmField val hasToolSelection: Boolean,
        @JvmField val materials: Array<String>,
        @JvmField val usedTools: IntArray,
        @JvmField val firstToolAppearance: IntArray,
        @JvmField val filamentPressureAdvance: List<String>,
        @JvmField val customPressureAdvance: List<String>,
        private val sourcePath: String,
        private val sourceLength: Long,
        private val sourceLastModified: Long,
    ) {
        /** Cached scan data is reusable only for the exact file version it inspected. */
        fun matchesSource(file: File): Boolean =
            file.isFile && file.absolutePath == sourcePath && file.length() == sourceLength &&
                file.lastModified() == sourceLastModified

        companion object {
            internal fun create(
                maxTool: Int,
                density: Double,
                densities: DoubleArray,
                volume: Double,
                totalMass: Double,
                hasReportedTotalMass: Boolean,
                computedFilamentMasses: DoubleArray,
                needsMassBreakdownRewrite: Boolean,
                hasFilamentMass: Boolean,
                hasFilamentMap: Boolean,
                hasFilamentTypes: Boolean,
                hasFilamentColors: Boolean,
                hasToolSelection: Boolean,
                materials: Array<String>,
                usedTools: IntArray,
                firstToolAppearance: IntArray,
                filamentPressureAdvance: List<String>,
                customPressureAdvance: List<String>,
                source: File,
            ) = ScanResult(maxTool, density, densities, volume, totalMass, hasReportedTotalMass,
                computedFilamentMasses, needsMassBreakdownRewrite, hasFilamentMass, hasFilamentMap,
                hasFilamentTypes, hasFilamentColors, hasToolSelection, materials, usedTools, firstToolAppearance, filamentPressureAdvance,
                customPressureAdvance, source.absolutePath, source.length(), source.lastModified())
        }
    }

    class Options {
        @JvmField var remappedTools: IntArray? = null
        @JvmField var gates: IntArray? = null
        @JvmField var modelInstances: Int = 1
        @JvmField var versionName: String = ""
        @JvmField var paintInfo: String = "[]"
        @JvmField var colors: String = "#19C8A0"
        @JvmField var materials: String = "PLA"
        @JvmField var externalSpool: Boolean = false
        @JvmField var smallThumbnail: String = ""
        @JvmField var largeThumbnail: String = ""
    }

    companion object {
        private val LIST_SEPARATOR = Pattern.compile("[,;]")
        private val KLIPPER_PA = Pattern.compile("^\\s*SET_PRESSURE_ADVANCE\\b.*?\\bADVANCE=([0-9]+(?:\\.[0-9]+)?)", Pattern.CASE_INSENSITIVE)
        private val MARLIN_PA = Pattern.compile("^\\s*M900\\s+K([0-9]+(?:\\.[0-9]+)?)", Pattern.CASE_INSENSITIVE)
        private val HEATER_TOOL_SELECTOR = Pattern.compile("(?i)(\\bM(?:104|109)\\b[^;]*?\\bT)(\\d+)(?=\\s|$)")
        private val TOOL_SELECTOR = Pattern.compile("(?i)(?<![A-Z0-9_])T[0-9]{1,2}(?![0-9])")
        private val EXTRUSION_MOVE = Pattern.compile("(?i)^G[0-3](?=\\s|$).*\\sE[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:\\s|$).*")
        private const val DEFAULT_FILAMENT_DENSITY = 1.24

        /** Formats Anycubic's base64 thumbnail block with 76-character data rows. */
        @JvmStatic
        fun formatThumbnailBlock(width: Int, height: Int, encoded: String?, top: Boolean): String {
            require(width > 0 && height > 0 && encoded != null) {
                "Thumbnail dimensions and data are required"
            }
            val data = encoded
            val block = StringBuilder()
            block.append("; THUMBNAIL_BLOCK_START\n")
            block.append("; thumbnail begin ").append(width).append('x').append(height).append(' ')
                .append(data.length).append(if (top) " top" else "").append('\n')
            var offset = 0
            while (offset < data.length) {
                block.append("; ").append(data, offset, minOf(offset + 76, data.length)).append('\n')
                offset += 76
            }
            return block.append("; thumbnail end\n; THUMBNAIL_BLOCK_END\n").toString()
        }

        /** AnycubicSlicerNext uses semicolons between filament palette entries. */
        @JvmStatic
        fun formatMappedPalette(mappedTools: IntArray, slots: List<FilamentSlot>, colors: Boolean): String {
            val highest = maxOf(0, mappedTools.maxOrNull() ?: 0)
            val mappedSlots = HashMap<Int, FilamentSlot>()
            mappedTools.forEachIndexed { logicalTool, printerTool ->
                slots.getOrNull(logicalTool)?.let { mappedSlots.putIfAbsent(printerTool, it) }
            }
            return (0..highest).joinToString(";") { tool ->
                val slot = mappedSlots[tool]
                if (colors) slot?.let { String.format(Locale.ROOT, "#%06X", it.color and 0xFFFFFF) } ?: "#19C8A0"
                else slot?.type ?: "PLA"
            }
        }

        @JvmStatic
        @Throws(IOException::class)
        fun scan(source: File): ScanResult {
            var densities = doubleArrayOf(DEFAULT_FILAMENT_DENSITY)
            var volumesByTool = DoubleArray(0)
            var massesByTool = DoubleArray(0)
            var reportedTotalMass = 0.0
            var hasReportedTotalMass = false
            var maxTool = -1
            var hasFilamentMass = false
            var hasFilamentMap = false
            var hasFilamentTypes = false
            var hasFilamentColors = false
            var hasToolSelection = false
            val usedTools = sortedSetOf<Int>()
            val firstToolAppearance = linkedSetOf<Int>()
            val filamentPressureAdvance = linkedSetOf<String>()
            val customPressureAdvance = linkedSetOf<String>()
            var materials = arrayOf("PLA")
            BufferedReader(InputStreamReader(FileInputStream(source), StandardCharsets.UTF_8), 64 * 1024).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    when {
                        line.startsWith("; filament_density = ") || line.startsWith("; filament_density:") -> {
                            val separator = maxOf(line.indexOf('='), line.indexOf(':'))
                            val parsed = parseNumbers(line.substring(separator + 1))
                            if (parsed.any { it.isFinite() && it > 0 }) densities = parsed
                        }
                        line.startsWith("; filament used [cm3] = ") -> {
                            volumesByTool = parseNumbers(line.substring(line.indexOf('=') + 1))
                        }
                        line.startsWith("; filament used [g] = ") -> {
                            massesByTool = parseNumbers(line.substring(line.indexOf('=') + 1))
                            hasFilamentMass = true
                        }
                        line.startsWith("; total filament used [g] = ") -> {
                            reportedTotalMass = parseFirstPositiveNumber(line.substring(line.indexOf('=') + 1))
                            hasReportedTotalMass = reportedTotalMass > 0
                        }
                        line.startsWith("; filament_type = ") -> {
                            materials = LIST_SEPARATOR.split(line.substring(line.indexOf('=') + 1).trim())
                            hasFilamentTypes = materials.any(String::isNotBlank)
                        }
                        line.startsWith("; filament_colour = ") -> hasFilamentColors = true
                    }
                    hasFilamentMap = hasFilamentMap || GCodeToolMapper.isFilamentMapLine(line)
                    val tool = GCodeToolMapper.parseToolCommand(line)
                    if (tool >= 0) {
                        hasToolSelection = true
                        maxTool = maxOf(maxTool, tool)
                        usedTools.add(tool)
                        firstToolAppearance.add(tool)
                    }
                    val pressureAdvance = when {
                        startsWithCommand(line, "SET_PRESSURE_ADVANCE") ->
                            KLIPPER_PA.matcher(line).takeIf { it.find() }?.group(1)
                        startsWithCommand(line, "M900") ->
                            MARLIN_PA.matcher(line).takeIf { it.find() }?.group(1)
                        else -> null
                    }
                    if (pressureAdvance != null) {
                        val target = if (line.contains("Override pressure advance value", ignoreCase = true))
                            filamentPressureAdvance else customPressureAdvance
                        if (target.size < 16) target.add(pressureAdvance)
                    }
                }
            }
            val normalizedDensities = normalizeDensities(densities)
            val massCount = maxOf(massesByTool.size, volumesByTool.size)
            val computedMasses = DoubleArray(massCount) { index ->
                val reportedMass = massesByTool.getOrNull(index)?.takeIf { it.isFinite() && it > 0 }
                if (reportedMass != null) reportedMass else {
                    val volume = volumesByTool.getOrNull(index)?.takeIf { it.isFinite() && it > 0 } ?: 0.0
                    volume * normalizedDensities[index.coerceAtMost(normalizedDensities.lastIndex)]
                }
            }
            val needsMassBreakdownRewrite = computedMasses.isNotEmpty() &&
                (!hasFilamentMass || volumesByTool.indices.any { index ->
                    val volume = volumesByTool[index].takeIf { it.isFinite() && it > 0 } ?: 0.0
                    volume > 0 && (massesByTool.getOrNull(index)?.takeIf { it.isFinite() && it > 0 } == null)
                })
            val volume = volumesByTool.sumOf { if (it.isFinite() && it > 0) it else 0.0 }
            val totalMass = if (hasReportedTotalMass) reportedTotalMass else computedMasses.sum()
            return ScanResult.create(maxTool, normalizedDensities.first(), normalizedDensities, volume,
                totalMass, hasReportedTotalMass, computedMasses, needsMassBreakdownRewrite,
                hasFilamentMass, hasFilamentMap, hasFilamentTypes, hasFilamentColors, hasToolSelection, materials,
                usedTools.toIntArray(), firstToolAppearance.toIntArray(), filamentPressureAdvance.toList(),
                customPressureAdvance.toList(), source)
        }

        /** Writes vendor-compatible G-code. Returns false when no header block was found. */
        @JvmStatic
        @Throws(IOException::class)
        fun rewrite(source: File, destination: File, scan: ScanResult, options: Options): Boolean {
            val remappedTools = options.remappedTools
                ?: throw IllegalArgumentException("Tool routing and physical gates are required")
            val gates = options.gates
                ?: throw IllegalArgumentException("Tool routing and physical gates are required")

            val mass = String.format(Locale.ROOT, "%.2f", scan.totalMass)
            val sharedPhysicalTools = remappedTools.distinct().size < remappedTools.size
            val mappedMasses = DoubleArray((remappedTools.maxOrNull() ?: -1) + 1)
            scan.computedFilamentMasses.forEachIndexed { sourceTool, mass ->
                val physicalTool = remappedTools.getOrNull(sourceTool)
                if (physicalTool != null && physicalTool in mappedMasses.indices) mappedMasses[physicalTool] += mass
            }
            val massBreakdown = (if (sharedPhysicalTools) mappedMasses else scan.computedFilamentMasses).joinToString(",") {
                String.format(Locale.ROOT, "%.2f", it)
            }
            val rewriteMassBreakdown = scan.needsMassBreakdownRewrite || sharedPhysicalTools
            val densityList = scan.densities.joinToString(",") { formatDensity(it) }
            var headerClosed = false
            var toolSelectionInserted = false
            try {
                BufferedReader(InputStreamReader(FileInputStream(source), StandardCharsets.UTF_8), 64 * 1024).use { reader ->
                    BufferedWriter(OutputStreamWriter(FileOutputStream(destination, false), StandardCharsets.UTF_8), 64 * 1024).use { writer ->
                        while (true) {
                            var line = reader.readLine() ?: break
                            if (line.trim().startsWith("; filament_colour_type")) continue
                            if (!headerClosed && line.startsWith("; generated by Slice Beam ")) {
                                line = line.replaceFirst("; generated by Slice Beam ", "; generated by OrcaSlicer ")
                            }
                            if (line.startsWith("; filament_density:")) {
                                line = "; filament_density: $densityList"
                            } else if (line.startsWith("; filament_density = ")) {
                                line = "; filament_density = $densityList"
                            } else if (line.startsWith("; total filament used [g] = ") && scan.totalMass > 0) {
                                if (rewriteMassBreakdown && !scan.hasFilamentMass && massBreakdown.isNotEmpty()) {
                                    writer.write("; filament used [g] = $massBreakdown\n")
                                }
                                if (!scan.hasReportedTotalMass) line = "; total filament used [g] = $mass"
                            }
                            if (!headerClosed && line == "; HEADER_BLOCK_END") {
                                writer.write("; model_instances: ${maxOf(1, options.modelInstances)}\n")
                                writer.write("; source_info: {\"models\":[{\"file_source\":0,\"mo_file_id\":-1,\"name\":\"OrkanSlicer_model\",\"referrer_source\":-1}],\"models_from\":0,\"plate_index\":1,\"slice_paras_process\":1,\"software_version\":\"OrkanSlicer ${options.versionName}\"}\n")
                                if (options.externalSpool && !scan.hasFilamentTypes) {
                                    writer.write("; filament_type = ${options.materials}\n")
                                }
                                if (options.externalSpool && !scan.hasFilamentColors) {
                                    writer.write("; filament_colour = ${options.colors}\n")
                                }
                                if (!options.externalSpool) writer.write("; paint_info = ${options.paintInfo}\n")
                                if (!options.externalSpool && !scan.hasFilamentMap) {
                                    writer.write("; filament: ${GCodeToolMapper.formatGateList(gates)}\n")
                                }
                                writer.write("; project_info = {\"flush_multiplier\":1.0,\"flush_volumes_chan_multipliers\":[1.0,1.0,1.0,1.0],\"flush_volumes_matrix\":[0.0],\"flush_volumes_vector\":[140.0,140.0,140.0,140.0,140.0,140.0,140.0,140.0]}\n")
                                writer.write("; flush_multiplier_calculate_by_acnext: 1\n")
                                writer.write("; test_mode: 0\n")
                                headerClosed = true
                            }
                            if (options.externalSpool && GCodeToolMapper.isFilamentMapLine(line)) {
                                continue
                            } else if (options.externalSpool) {
                                // A single external spool has no logical-to-ACE tool routing.
                                // Kobra firmware can still enter its ACE path for T<n>, even
                                // when the selector is embedded in G9111 or M104/M109.
                                line = stripToolSelectors(line)
                            } else if (GCodeToolMapper.parseToolCommand(line) >= 0) {
                                line = GCodeToolMapper.remapToolLine(line, remappedTools)
                            } else if (GCodeToolMapper.isFilamentMapLine(line)) {
                                line = "; filament: ${GCodeToolMapper.formatGateList(gates)}"
                            } else {
                                line = remapHeaterToolSelector(line, remappedTools)
                            }
                            if (line.startsWith("; filament_colour = ")) {
                                line = "; filament_colour = ${options.colors}"
                            } else if (line.startsWith("; filament_type = ")) {
                                line = "; filament_type = ${options.materials}"
                            } else if (line.startsWith("; filament used [g] = ") &&
                                rewriteMassBreakdown && massBreakdown.isNotEmpty()) {
                                line = "; filament used [g] = $massBreakdown"
                            }
                            // Custom start scripts may omit M106 S0. Select the physical
                            // slot before their first extrusion/retraction in that case.
                            if (headerClosed && !options.externalSpool && !scan.hasToolSelection && !toolSelectionInserted &&
                                (line.trim() == "M106 S0" || EXTRUSION_MOVE.matcher(line.substringBefore(';').trim()).matches())) {
                                val initialTool = remappedTools.firstOrNull()
                                    ?: throw IllegalArgumentException("No ACE slot selected for the initial tool")
                                writer.write("; filament start gcode\nT$initialTool\n")
                                toolSelectionInserted = true
                            }
                            writer.write(line)
                            writer.newLine()
                            if (headerClosed && line == "; HEADER_BLOCK_END") {
                                writer.newLine()
                                writer.write(options.smallThumbnail)
                                writer.newLine()
                                writer.write(options.largeThumbnail)
                            }
                        }
                    }
                }
            } catch (error: IOException) {
                destination.delete()
                throw error
            } catch (error: RuntimeException) {
                destination.delete()
                throw error
            }
            if (!headerClosed) {
                destination.delete()
                return false
            }
            return true
        }

        /** Prepares single-material output for the physical external spool without ACE metadata/tool calls. */
        @JvmStatic
        @Throws(IOException::class)
        fun rewriteForExternalSpool(source: File, destination: File): Boolean {
            var headerClosed = false
            try {
                BufferedReader(InputStreamReader(FileInputStream(source), StandardCharsets.UTF_8), 64 * 1024).use { reader ->
                    BufferedWriter(OutputStreamWriter(FileOutputStream(destination, false), StandardCharsets.UTF_8), 64 * 1024).use { writer ->
                        while (true) {
                            var line = reader.readLine() ?: break
                            val normalized = line.trim()
                            // On Kobra 3 the firmware interprets even T0 as a request to
                            // initialize the detached ACE hub. A single external spool is
                            // already selected physically, so remove tool changes and ACE-only
                            // mapping metadata from the upload copy.
                            if (GCodeToolMapper.parseToolCommand(normalized) >= 0 ||
                                normalized.startsWith("; paint_info =", ignoreCase = true) ||
                                normalized.startsWith("; filament:", ignoreCase = true) ||
                                normalized.startsWith("; filament_colour_type", ignoreCase = true)) {
                                continue
                            }
                            if (!headerClosed && line.startsWith("; generated by Slice Beam ")) {
                                line = line.replaceFirst("; generated by Slice Beam ", "; generated by OrcaSlicer ")
                            }
                            writer.write(line)
                            writer.newLine()
                            if (line == "; HEADER_BLOCK_END") headerClosed = true
                        }
                    }
                }
            } catch (error: IOException) {
                destination.delete()
                throw error
            } catch (error: RuntimeException) {
                destination.delete()
                throw error
            }
            if (!headerClosed) {
                destination.delete()
                return false
            }
            return true
        }

        /** M104/M109 can name a logical tool explicitly; route it to the selected physical gate too. */
        @JvmStatic
        fun remapHeaterToolSelector(line: String, remappedTools: IntArray): String {
            val matcher = HEATER_TOOL_SELECTOR.matcher(line)
            if (!matcher.find()) return line
            val sourceTool = matcher.group(2)?.toIntOrNull() ?: return line
            val physicalTool = remappedTools.getOrNull(sourceTool) ?: return line
            return matcher.replaceFirst(Matcher.quoteReplacement("${matcher.group(1)}$physicalTool"))
        }

        /** Removes all G-code tool selectors while retaining comments and the rest of each command. */
        @JvmStatic
        fun stripToolSelectors(line: String): String {
            val commentStart = line.indexOf(';')
            val code = if (commentStart >= 0) line.substring(0, commentStart) else line
            val comment = if (commentStart >= 0) line.substring(commentStart) else ""
            return TOOL_SELECTOR.matcher(code).replaceAll(" ").trimEnd() + comment
        }

        private fun parseFirstPositiveNumber(value: String): Double {
            return try {
                val parsed = LIST_SEPARATOR.split(value.trim()).firstOrNull()?.trim()?.toDouble() ?: 0.0
                if (parsed.isFinite() && parsed > 0) parsed else 0.0
            } catch (_: NumberFormatException) {
                0.0
            }
        }

        private fun startsWithCommand(line: String, command: String): Boolean {
            var start = 0
            while (start < line.length && Character.isWhitespace(line[start])) start++
            return line.regionMatches(start, command, 0, command.length, ignoreCase = true)
        }

        private fun parseNumbers(value: String): DoubleArray = LIST_SEPARATOR.split(value.trim()).map { part ->
            try {
                part.trim().toDouble().takeIf { it.isFinite() && it > 0 } ?: 0.0
            } catch (_: NumberFormatException) {
                0.0
            }
        }.toDoubleArray()

        private fun normalizeDensities(values: DoubleArray): DoubleArray {
            val fallback = values.firstOrNull { it.isFinite() && it > 0 } ?: DEFAULT_FILAMENT_DENSITY
            return if (values.isEmpty()) doubleArrayOf(DEFAULT_FILAMENT_DENSITY)
            else DoubleArray(values.size) { index -> values[index].takeIf { it.isFinite() && it > 0 } ?: fallback }
        }

        private fun formatDensity(value: Double): String =
            java.lang.Double.toString(value)
    }
}
