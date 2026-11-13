package ru.ytkab0bp.slicebeam

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.ytkab0bp.slicebeam.config.ConfigObject
import ru.ytkab0bp.slicebeam.slic3r.Slic3rConfigWrapper
import ru.ytkab0bp.slicebeam.slic3r.Model
import java.io.File

/** Native regression for material identity when the cut pipeline merges solid parts. */
@RunWith(AndroidJUnit4::class)
class CutColorPreservationDeviceTest {
    @Test fun planarCutWithConnectorKeepsDistinctPartFilamentsInBothHalves() {
        val model = Model()
        try {
            assertEquals(0, model.createPrimitive(0, 10.0, 10.0, 10.0, "Cut left part", centerX = -5.0))
            assertEquals(1, model.createPrimitive(0, 10.0, 10.0, 10.0, "Cut right part", centerX = 5.0))
            assertEquals(0, model.combineObjectsAsParts(intArrayOf(0, 1)))
            assertEquals(1, model.objectsCount)
            assertEquals(2, model.getObjectPartCount(0))
            assertTrue(model.setObjectPartExtruder(0, 0, 1))
            assertTrue(model.setObjectPartExtruder(0, 1, 2))

            // The object is ten millimetres tall with its bottom at Z=0. Place a dowel on
            // the interface so this exercises the connector-preserving cut branch too.
            model.addConnector(0, ru.ytkab0bp.slicebeam.utils.Vec3d(-5.0, 0.0, 5.0), 1.0f, 3.0f, 1)
            assertEquals(1, model.getCutConnectorCount(0))
            assertTrue(model.cut(0, 5.0, 0.0, 0.0, keepUpper = true, keepLower = true))

            val coloredHalves = (0 until model.objectsCount)
                .filter { model.getObjectPartCount(it) >= 2 }
            assertEquals("both cut halves must retain the two independently assigned parts", 2, coloredHalves.size)
            coloredHalves.forEach { objectIndex ->
                val filaments = (0 until model.getObjectPartCount(objectIndex))
                    .map { model.getObjectPartExtruder(objectIndex, it) }
                    .sorted()
                assertArrayEquals("cut model $objectIndex must keep both filament identities",
                    intArrayOf(1, 2), filaments.toIntArray())
            }
        } finally {
            model.release()
        }
    }

    @Test(timeout = 120_000)
    fun slicedCutHalvesEmitBothPreservedFilamentTools() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        SliceBeam.INSTANCE = context.applicationContext as SliceBeam
        val previousConfig = SliceBeam.CONFIG
        val configFile = SliceBeam.getCurrentConfigFile()
        val previousConfigFile = configFile.takeIf { it.isFile }?.readBytes()
        val output = File(context.cacheDir, "cut-colors-${System.nanoTime()}.gcode")
        val model = Model()
        try {
            model.createPrimitive(0, 10.0, 10.0, 10.0, "Cut left part", centerX = -5.0)
            model.createPrimitive(0, 10.0, 10.0, 10.0, "Cut right part", centerX = 5.0)
            assertEquals(0, model.combineObjectsAsParts(intArrayOf(0, 1)))
            assertTrue(model.setObjectPartExtruder(0, 0, 1))
            assertTrue(model.setObjectPartExtruder(0, 1, 2))
            model.addConnector(0, ru.ytkab0bp.slicebeam.utils.Vec3d(-5.0, 0.0, 5.0), 1.0f, 3.0f, 1)
            assertTrue(model.cut(0, 5.0, 0.0, 0.0, keepUpper = true, keepLower = true))

            SliceBeam.CONFIG = Slic3rConfigWrapper(context.assets.open("orca_profiles/Anycubic.ini")).apply {
                presets = ConfigObject("presets").apply {
                    put("printer", "Anycubic Kobra 3 0.4 nozzle")
                    put("print", "0.16mm Standard")
                    put("filament", "Anycubic Generic PLA")
                }
            }
            SliceBeam.genCurrentConfig(model)
            val result = model.slice(configFile.absolutePath, output.absolutePath, null)
            try {
                assertTrue("the post-cut two-tool model must complete a real native slice", output.isFile && output.length() > 0)
                val selectedTools = Regex("(?m)^T(\\d+)\\s*(?:;.*)?$").findAll(output.readText())
                    .map { it.groupValues[1].toInt() }.toSet()
                assertTrue("the sliced cut model must retain tool T0", 0 in selectedTools)
                assertTrue("the sliced cut model must retain tool T1", 1 in selectedTools)
            } finally {
                result.release()
            }
        } finally {
            model.release()
            output.delete()
            if (previousConfig != null) SliceBeam.CONFIG = previousConfig else SliceBeam.CONFIG = null
            if (previousConfigFile != null) configFile.writeBytes(previousConfigFile) else configFile.delete()
        }
    }
}
