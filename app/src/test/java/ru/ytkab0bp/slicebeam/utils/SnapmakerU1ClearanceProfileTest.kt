package ru.ytkab0bp.slicebeam.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SnapmakerU1ClearanceProfileTest {
    private fun u1Profiles(): List<String> {
        val profile = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .map { File(it, "app/src/main/assets/orca_profiles/Snapmaker.ini") }
            .firstOrNull(File::isFile)
        assertTrue("Snapmaker profile asset must be available from the project checkout", profile != null)
        return requireNotNull(profile).readText()
            .split(Regex("(?=^\\[)", RegexOption.MULTILINE))
            .filter { it.startsWith("[printer:Snapmaker U1 ") }
    }

    @Test
    fun allBundledU1NozzleVariantsUseTheCadRodClearance() {
        val profiles = u1Profiles()
        assertEquals("The bundled U1 profile has three nozzle variants", 3, profiles.size)
        profiles.forEach { section ->
            val name = section.lineSequence().first()
            val values = section.lineSequence()
                .filter { '=' in it }
                .associate { line ->
                    val (key, value) = line.split('=', limit = 2)
                    key.trim() to value.trim()
                }
            assertEquals("$name rod height", "26", values["extruder_clearance_height_to_rod"])
            assertEquals("$name rod distance", "41.2", values["extruder_clearance_dist_to_rod"])
            assertEquals("$name clearance radius", "72.5", values["extruder_clearance_radius"])
        }
    }
}
