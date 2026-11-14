package ru.ytkab0bp.slicebeam

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.ytkab0bp.slicebeam.config.ConfigObject
import ru.ytkab0bp.slicebeam.slic3r.GCodeProcessorResult
import ru.ytkab0bp.slicebeam.slic3r.Model
import ru.ytkab0bp.slicebeam.slic3r.Slic3rConfigWrapper
import ru.ytkab0bp.slicebeam.utils.Prefs
import java.io.File

@RunWith(AndroidJUnit4::class)
class SingleFilamentToolStartOrderDeviceTest {
    @Test(timeout = 180_000)
    fun firstToolAndSlicerTemperaturePrecedeFilamentStartTemperature() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val process = requireNotNull(SliceBeam.CONFIG?.findPrint("0.16mm Standard"))
        val filament = requireNotNull(SliceBeam.CONFIG?.findFilament("Anycubic Generic PLA"))
        process.put("layer_height", "0.2")
        process.put("initial_layer_print_height", "0.2")
        process.put("enable_prime_tower", "0")
        filament.put("nozzle_temperature", "240")
        filament.put("nozzle_temperature_initial_layer", "240")
        filament.put("filament_start_gcode", "M104 S255")

        val source = File(context.cacheDir, "single-filament-tool-order.stl").also { target ->
            context.assets.open("models/xyz_cube.stl").use { input -> target.outputStream().use(input::copyTo) }
        }
        val output = File(context.cacheDir, "single-filament-tool-order.gcode")
        val model = Model(source).also { it.ensureOnBed(0); it.setUniformFilament(0, 1) }
        var result: GCodeProcessorResult? = null
        try {
            SliceBeam.genCurrentConfig(model)
            val configFile = SliceBeam.getCurrentConfigFile()
            var configText = configFile.readText()
            fun replaceSetting(key: String, value: String) {
                val pattern = Regex("(?m)^${Regex.escape(key)}\\s*=.*$")
                assertTrue("generated config must contain $key", pattern.containsMatchIn(configText))
                configText = pattern.replace(configText, "$key = $value")
            }
            replaceSetting("filament_diameter", "1.75,1.75")
            replaceSetting("nozzle_temperature", "240,240")
            replaceSetting("nozzle_temperature_initial_layer", "240,240")
            replaceSetting("filament_start_gcode", "M104 S255")
            replaceSetting("machine_start_gcode", "")
            replaceSetting("single_extruder_multi_material", "1")
            replaceSetting("enable_prime_tower", "0")
            configFile.writeText(configText)

            result = model.slice(configFile.absolutePath, output.absolutePath, null)
            val raw = output.readLines()
            val commands = raw.map { it.substringBefore(';').trim() }.filter(String::isNotEmpty)
            val toolIndex = commands.indexOf("T0")
            assertTrue("the two-material single-extruder fixture must select T0: ${raw.take(45)}", toolIndex >= 0)
            assertTrue("the slicer's nozzle temperature must immediately follow T0: ${raw.take(45)}",
                commands.getOrNull(toolIndex + 1) == "M104 S240")
            val customTemperatureIndex = commands.indexOf("M104 S255")
            assertTrue("the custom filament temperature must run after T0 and the slicer temperature: ${raw.take(45)}",
                customTemperatureIndex > toolIndex + 1)
        } finally {
            result?.release()
            model.release()
            source.delete()
            output.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    private inline fun withKobraProfile(block: () -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val application = context.applicationContext as Application
        SliceBeam.INSTANCE = application as SliceBeam
        Prefs.init(application)
        val previousConfig = SliceBeam.CONFIG
        try {
            context.assets.open("orca_profiles/Anycubic.ini").use { SliceBeam.CONFIG = Slic3rConfigWrapper(it) }
            val config = requireNotNull(SliceBeam.CONFIG)
            config.presets = ConfigObject("presets").apply {
                put("printer", requireNotNull(config.findPrinter("Anycubic Kobra 3 0.4 nozzle")).title)
                put("print", requireNotNull(config.findPrint("0.16mm Standard")).title)
                put("filament", requireNotNull(config.findFilament("Anycubic Generic PLA")).title)
            }
            block()
        } finally {
            SliceBeam.CONFIG = previousConfig
        }
    }
}
