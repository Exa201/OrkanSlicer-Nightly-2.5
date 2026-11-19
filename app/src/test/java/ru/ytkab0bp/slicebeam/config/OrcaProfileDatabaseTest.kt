package ru.ytkab0bp.slicebeam.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.ytkab0bp.slicebeam.slic3r.Slic3rConfigWrapper
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile

class OrcaProfileDatabaseTest {
    @Test
    fun bundledDatabaseIsCurrentCompleteAndParseable() {
        val profilesDirectory = listOf(
            File("src/main/assets/orca_profiles"),
            File("app/src/main/assets/orca_profiles"),
        ).firstOrNull { it.isDirectory } ?: error("Bundled Orca profile database is missing")
        val version = File(profilesDirectory, "UPSTREAM_VERSION.txt").readText().trim()
        val files = profilesDirectory.listFiles { file -> file.extension == "ini" }.orEmpty()

        assertEquals("OrcaSlicer profiles main @ 0c80c412a1fd3236f1e753433c29d5050683da6b", version)
        assertTrue("Expected the full Orca vendor catalog", files.size >= 70)

        var printerCount = 0
        var processCount = 0
        var filamentCount = 0
        files.forEach { file ->
            val bundle = Slic3rConfigWrapper(file)
            printerCount += bundle.printerConfigs.size
            processCount += bundle.printConfigs.size
            filamentCount += bundle.filamentConfigs.size
            assertTrue(
                "${file.name} has unresolved inheritance",
                bundle.loadWarnings.none { it is ProfileConfigLoadWarning.MissingParent },
            )
        }

        val anycubic = Slic3rConfigWrapper(File(profilesDirectory, "Anycubic.ini"))
        val qidi = Slic3rConfigWrapper(File(profilesDirectory, "Qidi.ini"))
        assertNotNull(anycubic.findPrinter("Anycubic Kobra 3 V2 0.4 nozzle"))
        assertTrue(anycubic.printConfigs.any { it.title?.contains("Kobra 3 V2") == true })
        assertNotNull(qidi.findPrinter("Qidi X-Smart 5 0.4 nozzle"))
        assertTrue(qidi.printConfigs.any { it.title?.contains("X-Smart 5") == true })
        assertTrue("Printer profiles must retain the file-start header template",
            "file_start_gcode" in Slic3rConfigWrapper.PRINTER_CONFIG_KEYS)
        assertEquals("Anker M5 family presets must use total print duration in their file header", 12,
            Slic3rConfigWrapper(profilesDirectory.resolve("Anker.ini")).printerConfigs.count { printer ->
                printer.title?.startsWith("Anker M5") == true &&
                    printer["file_start_gcode"]?.startsWith(";TIME:{print_time_total_sec}") == true
            })
        assertTrue("Expected current printer catalog", printerCount >= 1200)
        assertTrue("Expected current print and filament presets", processCount + filamentCount >= 10_000)
    }

    @Test
    fun prusaCoreOneOverhangSpeedsUseOrcaSeveritySlots() {
        val profilesDirectory = listOf(
            File("src/main/assets/orca_profiles"),
            File("app/src/main/assets/orca_profiles"),
        ).firstOrNull { it.isDirectory } ?: error("Bundled Orca profile database is missing")
        val prusa = Slic3rConfigWrapper(File(profilesDirectory, "Prusa.ini"))

        val speed04 = checkNotNull(prusa.findPrint("0.15mm SPEED @CORE One 0.4"))
        assertEquals("50", speed04["overhang_2_4_speed"])
        assertEquals("25", speed04["overhang_3_4_speed"])

        val speed06 = checkNotNull(prusa.findPrint("0.20mm SPEED @CORE One 0.6"))
        assertEquals("70%", speed06["overhang_1_4_speed"])
        assertEquals("30", speed06["overhang_2_4_speed"])
        assertEquals("25", speed06["overhang_3_4_speed"])
        assertEquals("15", speed06["overhang_4_4_speed"])
    }

    @Test
    fun existingQidiFactoryProfilesAreReplacedByCurrentOrcaDatabase() {
        val profilesDirectory = listOf(
            File("src/main/assets/orca_profiles"),
            File("app/src/main/assets/orca_profiles"),
        ).firstOrNull { it.isDirectory } ?: error("Bundled Orca profile database is missing")
        val archive = ZipFile(File(profilesDirectory, "legacy_factory_profiles.zip"))
        val previous = archive.use { zip ->
            val entry = checkNotNull(zip.getEntry("Qidi.ini"))
            zip.getInputStream(entry).use(::Slic3rConfigWrapper)
        }
        val installed = Slic3rConfigWrapper(
            ByteArrayInputStream(previous.serialize().toByteArray(StandardCharsets.UTF_8))
        )
        val latest = Slic3rConfigWrapper(File(profilesDirectory, "Qidi.ini"))
        val printerTitle = "Qidi Q2 0.6 nozzle"
        assertEquals("4", installed.findPrinter(printerTitle)?.get("machine_max_jerk_e"))
        assertEquals("4,4", latest.findPrinter(printerTitle)?.get("machine_max_jerk_e"))

        val changed = FactoryProfileDatabaseUpdater.refresh(installed, latest, previous)

        assertTrue("Expected old factory profiles to refresh", changed)
        assertEquals("4,4", installed.findPrinter(printerTitle)?.get("machine_max_jerk_e"))
        assertTrue(latest.printerConfigs.all { current -> installed.printerConfigs.any { it.title == current.title } })
        assertTrue(latest.printConfigs.all { current -> installed.printConfigs.any { it.title == current.title } })
        assertTrue(latest.filamentConfigs.all { current -> installed.filamentConfigs.any { it.title == current.title } })
    }

    @Test
    fun existingHonorKobraV2ProfileMigratesToCurrentOrcaPrinterName() {
        val profilesDirectory = listOf(
            File("src/main/assets/orca_profiles"),
            File("app/src/main/assets/orca_profiles"),
        ).firstOrNull { it.isDirectory } ?: error("Bundled Orca profile database is missing")
        val archive = ZipFile(File(profilesDirectory, "legacy_factory_profiles.zip"))
        val previous = archive.use { zip ->
            val entry = checkNotNull(zip.getEntry("Anycubic.ini"))
            zip.getInputStream(entry).use(::Slic3rConfigWrapper)
        }
        val oldTitle = "Anycubic Kobra 3 v2 0.4 nozzle - Stock"
        val installed = Slic3rConfigWrapper(
            ByteArrayInputStream(
                (previous.serialize() + "\n[printer:$oldTitle]\n" +
                    "printer_model = Anycubic Kobra 3 V2\nprinter_variant = 0.4\nnozzle_diameter = 0.4\n")
                    .toByteArray(StandardCharsets.UTF_8)
            )
        )
        installed.presets = ConfigObject().apply { put("printer", oldTitle) }
        val latest = Slic3rConfigWrapper(File(profilesDirectory, "Anycubic.ini"))

        val changed = FactoryProfileDatabaseUpdater.refresh(installed, latest, previous)

        assertTrue("Expected the old installed factory profile to be migrated", changed)
        assertEquals(null, installed.findPrinter(oldTitle))
        assertNotNull(installed.findPrinter("Anycubic Kobra 3 V2 0.4 nozzle"))
        assertEquals("Anycubic Kobra 3 V2 0.4 nozzle", installed.presets?.get("printer"))
    }
}
