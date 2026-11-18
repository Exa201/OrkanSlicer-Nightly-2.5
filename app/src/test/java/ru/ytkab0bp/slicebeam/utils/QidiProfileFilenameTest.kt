package ru.ytkab0bp.slicebeam.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class QidiProfileFilenameTest {
    private fun qidiProfile(): String {
        val profile = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .map { File(it, "app/src/main/assets/orca_profiles/Qidi.ini") }
            .firstOrNull(File::isFile)
        assertTrue("Qidi profile asset must be available from the project checkout", profile != null)
        return requireNotNull(profile).readText()
    }

    @Test
    fun qidiFilenameUsesTheInitiallySelectedToolInsteadOfAlwaysT0() {
        val filenameFormats = qidiProfile().lineSequence()
            .filter { it.startsWith("filename_format =") || it.startsWith("output_filename_format =") }
            .filter { it.contains("{filament_type[") }
            .toList()

        assertTrue("Qidi profile should include material-aware output filenames", filenameFormats.isNotEmpty())
        assertFalse(
            "Qidi filenames must not force material slot T0 when another tool starts the print",
            filenameFormats.any { it.contains("{filament_type[0]}") },
        )
        assertTrue(
            "Qidi material-aware filenames should use the initial active tool",
            filenameFormats.all { it.contains("{filament_type[initial_tool]}") },
        )
    }

    @Test
    fun qidiQ2AndQ2cProfilesUseSelectedBedTemperatureAndInitialTool() {
        val sections = qidiProfile().split(Regex("(?=^\\[)", RegexOption.MULTILINE))
        val qidiMultiMaterialPrinters = sections.filter { section ->
            val header = section.lineSequence().firstOrNull().orEmpty()
            header.startsWith("[printer:Qidi Q2 ") || header.startsWith("[printer:Qidi Q2C ")
        }

        assertTrue("Qidi Q2/Q2C nozzle variants should be present", qidiMultiMaterialPrinters.size >= 8)
        qidiMultiMaterialPrinters.forEach { section ->
            val header = section.lineSequence().first()
            val startGcode = section.lineSequence()
                .firstOrNull { it.startsWith("start_gcode =") || it.startsWith("machine_start_gcode =") }
                .orEmpty()
            assertTrue("$header must heat the selected plate type", startGcode.contains("BED=[bed_temperature_initial_layer_single]"))
            assertTrue("$header must set the selected plate temperature", startGcode.contains("M140 S[bed_temperature_initial_layer_single]"))
            assertTrue("$header must start with the initial model tool", startGcode.contains("T[initial_tool]"))
            assertTrue("$header must initialize Qidi Box tool mapping", startGcode.contains("INIT_MAPPING_VALUE"))
        }
    }

    @Test
    fun qidiLegacyProfilesUseTheSelectedBedTypeTemperature() {
        val affectedFamilies = listOf(
            "[printer:Qidi Q1 Pro ",
            "[printer:Qidi X-CF Pro ",
            "[printer:Qidi X-Max 3 ",
            "[printer:Qidi X-Plus 3 ",
            "[printer:Qidi X-Smart 3 ",
        )
        val affectedSections = qidiProfile()
            .split(Regex("(?=^\\[)", RegexOption.MULTILINE))
            .filter { section -> affectedFamilies.any { section.startsWith(it) } }

        assertTrue("All 17 Q1/X-CF/X-Max/X-Plus/X-Smart nozzle profiles should be covered", affectedSections.size == 17)
        affectedSections.forEach { section ->
            val header = section.lineSequence().first()
            val startGcode = section.lineSequence()
                .firstOrNull { it.startsWith("start_gcode =") || it.startsWith("machine_start_gcode =") }
                .orEmpty()
            assertTrue(
                "$header must heat the selected plate type",
                startGcode.contains("M140 S[bed_temperature_initial_layer_single]") ||
                    startGcode.contains("M190 S[bed_temperature_initial_layer_single]"),
            )
            assertFalse("$header must not force the high-temperature plate setting", startGcode.contains("hot_plate_temp_initial_layer"))
        }
    }
}
