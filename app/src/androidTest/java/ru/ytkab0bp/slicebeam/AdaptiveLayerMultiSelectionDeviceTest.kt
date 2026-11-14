package ru.ytkab0bp.slicebeam

import android.app.Application
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.ytkab0bp.slicebeam.config.ConfigObject
import ru.ytkab0bp.slicebeam.slic3r.GCodeProcessorResult
import ru.ytkab0bp.slicebeam.slic3r.Model
import ru.ytkab0bp.slicebeam.slic3r.Slic3rConfigWrapper
import ru.ytkab0bp.slicebeam.utils.Prefs
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
class AdaptiveLayerMultiSelectionDeviceTest {
    @Test(timeout = 180_000)
    fun selectedObjectsReceiveSharedAdaptiveLayersAndSliceOnTheirAssignedTools() = withToolChangerProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val process = requireNotNull(SliceBeam.CONFIG?.findPrint("0.20mm Standard"))
        process.put("layer_height", "0.2")
        process.put("initial_layer_print_height", "0.2")
        process.put("precise_z_height", "1")
        process.put("enable_prime_tower", "1")

        val tallSource = copyCube(context, "adaptive-multiselect-tall.stl", 1f)
        val shortSource = copyCube(context, "adaptive-multiselect-short.stl", 0.55f)
        val output = File(context.cacheDir, "adaptive-multiselect-prime-tower.gcode")
        val model = Model(tallSource)
        val secondObject = Model(shortSource)
        var result: GCodeProcessorResult? = null
        try {
            model.addObject(secondObject, 0)
            model.translate(1, 35.0, 0.0, 0.0)
            model.setUniformFilament(0, 1)
            model.setUniformFilament(1, 2)
            SliceBeam.genCurrentConfig(model)
            val configFile = SliceBeam.getCurrentConfigFile()

            assertTrue("selected objects must receive a shared adaptive profile",
                model.applyAdaptiveLayerHeightToObjects(intArrayOf(0, 1), configFile.absolutePath, 0.5f))
            assertTrue("both objects must retain adaptive profiles", model.hasAdaptiveLayerHeight(0) && model.hasAdaptiveLayerHeight(1))
            val tallProfile = model.adaptiveLayerHeightProfile(0)
            val shortProfile = model.adaptiveLayerHeightProfile(1)
            assertTrue("adaptive profiles must contain Z/height samples", tallProfile.size >= 4)
            assertTrue("short-object profile must end at its own mesh top",
                shortProfile[shortProfile.size - 2] < tallProfile[tallProfile.size - 2])
            for (sample in 0 until shortProfile.size - 2)
                assertEquals("common adaptive sample $sample", tallProfile[sample], shortProfile[sample], 0.000001)

            SliceBeam.genCurrentConfig(model)
            result = model.slice(configFile.absolutePath, output.absolutePath, null)
            val gcode = output.readText()
            assertTrue("slice must contain extrusion", gcode.contains("G1") && gcode.contains(" E"))
            assertTrue("slice must use both assigned filaments",
                Regex("(?m)^T0(?:\\s|$)").containsMatchIn(gcode) && Regex("(?m)^T1(?:\\s|$)").containsMatchIn(gcode))
            assertTrue("shared adaptive profiles must slice with a prime tower", gcode.contains("Prime tower", ignoreCase = true))
        } finally {
            result?.release()
            model.release()
            secondObject.release()
            tallSource.delete()
            shortSource.delete()
            output.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    private inline fun withToolChangerProfile(block: () -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val application = context.applicationContext as Application
        SliceBeam.INSTANCE = application as SliceBeam
        Prefs.init(application)
        val previousConfig = SliceBeam.CONFIG
        try {
            context.assets.open("orca_profiles/Custom.ini").use { SliceBeam.CONFIG = Slic3rConfigWrapper(it) }
            val config = requireNotNull(SliceBeam.CONFIG)
            val printer = requireNotNull(config.findPrinter("MyToolChanger 0.4 nozzle"))
            val process = requireNotNull(config.findPrint("0.20mm Standard"))
            val filament = requireNotNull(config.findFilament("Generic PLA"))
            config.presets = ConfigObject("presets").apply {
                put("printer", printer.title)
                put("print", process.title)
                put("filament", filament.title)
            }
            block()
        } finally {
            SliceBeam.CONFIG = previousConfig
        }
    }

    private fun copyCube(context: Context, name: String, zScale: Float): File {
        require(zScale.isFinite() && zScale > 0f)
        val bytes = context.assets.open("models/xyz_cube.stl").use { it.readBytes() }
        require(bytes.size >= 84)
        val data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val triangles = data.getInt(80)
        require(triangles >= 1 && bytes.size == 84 + triangles * 50)
        repeat(triangles) { triangle ->
            repeat(3) { vertex ->
                val zOffset = 84 + triangle * 50 + 12 + vertex * 12 + 8
                data.putFloat(zOffset, data.getFloat(zOffset) * zScale)
            }
        }
        return File(context.cacheDir, name).also { it.writeBytes(bytes) }
    }
}
