package ru.ytkab0bp.slicebeam

import android.app.Application
import android.content.Context
import android.util.Log
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.ytkab0bp.slicebeam.config.ConfigObject
import ru.ytkab0bp.slicebeam.config.AdaptivePAModelValidator
import ru.ytkab0bp.slicebeam.config.Kobra3V2ProfileMigration
import ru.ytkab0bp.slicebeam.config.QidiXSmart5ProfileBundle
import ru.ytkab0bp.slicebeam.slic3r.GCodeProcessorResult
import ru.ytkab0bp.slicebeam.slic3r.GCodeViewer
import ru.ytkab0bp.slicebeam.slic3r.BooleanOperation
import ru.ytkab0bp.slicebeam.slic3r.GLModel
import ru.ytkab0bp.slicebeam.slic3r.Model
import ru.ytkab0bp.slicebeam.slic3r.Native
import ru.ytkab0bp.slicebeam.slic3r.PaintSession
import ru.ytkab0bp.slicebeam.slic3r.PrintConfigDef
import ru.ytkab0bp.slicebeam.slic3r.Slic3rConfigWrapper
import ru.ytkab0bp.slicebeam.slic3r.Slic3rRuntimeError
import ru.ytkab0bp.slicebeam.render.GLRenderer
import ru.ytkab0bp.slicebeam.utils.Prefs
import ru.ytkab0bp.slicebeam.slic3r.Slic3rUtils
import ru.ytkab0bp.slicebeam.slic3r.ObjectBounds3D
import ru.ytkab0bp.slicebeam.slic3r.ObjectDistributionAxis
import ru.ytkab0bp.slicebeam.slic3r.ObjectGroupDistribution
import ru.ytkab0bp.slicebeam.slic3r.ObjectPlacement
import ru.ytkab0bp.slicebeam.render.FillBedModelOperation
import ru.ytkab0bp.slicebeam.components.bed_menu.SlicePreparationTools
import ru.ytkab0bp.slicebeam.utils.Vec3d
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import java.util.zip.CRC32

/** Runs an ARM64 native slice in an isolated app id, away from production app data. */
@RunWith(AndroidJUnit4::class)
class NativeSliceDeviceTest {
    private data class TbbWorkerSliceMeasurement(
        val cpuTicks: Map<String, Long>,
        val processorIds: Set<Int>,
    )

    private data class TbbWorkerSnapshot(val cpuTicks: Long, val processorId: Int?)

    private fun readTbbWorkerSnapshots(): Map<String, TbbWorkerSnapshot> =
        File("/proc/self/task").listFiles().orEmpty().mapNotNull { task ->
            val name = runCatching { File(task, "comm").readText().trim() }.getOrNull()
                ?.takeIf { it.startsWith("slic3r_tbb_") } ?: return@mapNotNull null
            val fields = runCatching {
                File(task, "stat").readText().substringAfterLast(')').trim().split(Regex("\\s+"))
            }.getOrNull() ?: return@mapNotNull null
            val userTicks = fields.getOrNull(11)?.toLongOrNull() ?: return@mapNotNull null
            val systemTicks = fields.getOrNull(12)?.toLongOrNull() ?: return@mapNotNull null
            name to TbbWorkerSnapshot(userTicks + systemTicks, fields.getOrNull(36)?.toIntOrNull())
        }.toMap()

    private fun measureTbbWorkerCpuDuring(block: () -> Unit): TbbWorkerSliceMeasurement {
        val sampling = AtomicBoolean(true)
        val previous = ConcurrentHashMap<String, Long>()
        val accumulated = ConcurrentHashMap<String, AtomicLong>()
        val processorIds = ConcurrentHashMap.newKeySet<Int>()
        val sampler = Thread({
            while (sampling.get()) {
                val current = readTbbWorkerSnapshots()
                current.forEach { (name, snapshot) ->
                    snapshot.processorId?.let(processorIds::add)
                    val old = previous.put(name, snapshot.cpuTicks)
                    if (old != null && snapshot.cpuTicks > old) {
                        accumulated.computeIfAbsent(name) { AtomicLong() }.addAndGet(snapshot.cpuTicks - old)
                    }
                }
                try {
                    Thread.sleep(5)
                } catch (_: InterruptedException) {
                    break
                }
            }
            readTbbWorkerSnapshots().forEach { (name, snapshot) ->
                snapshot.processorId?.let(processorIds::add)
                val old = previous.put(name, snapshot.cpuTicks)
                if (old != null && snapshot.cpuTicks > old) {
                    accumulated.computeIfAbsent(name) { AtomicLong() }.addAndGet(snapshot.cpuTicks - old)
                }
            }
        }, "slice-tbb-cpu-monitor")
        sampler.start()
        try {
            block()
        } finally {
            sampling.set(false)
            sampler.join(2_000)
        }
        return TbbWorkerSliceMeasurement(
            cpuTicks = accumulated.mapValues { it.value.get() },
            processorIds = processorIds.toSet(),
        )
    }

    @Test(timeout = 30_000)
    fun slicingTbbRuntimeCanFanOutAcrossVisibleCpus() {
        val result = Native.tbb_slicing_worker_probe()
        assertEquals("the native probe reports CPU count, TBB capacity and used worker count", 3, result.size)
        assertTrue("the runtime must report at least one visible CPU", result[0] >= 1)
        assertTrue("the TBB arena must expose at least one worker slot", result[1] >= 1)
        assertTrue("observed TBB workers cannot exceed arena capacity", result[2] in 1..result[1])
        assertTrue("TBB should respect the process-visible CPU count when it is reported", result[0] == 0 || result[1] <= result[0])
        if (result[1] > 1) {
            assertTrue("a multi-CPU TBB arena must schedule work on more than one worker", result[2] > 1)
        }
        Log.i(
            "NativeSliceDeviceTest",
            "TBB slicing concurrency probe: hardwareConcurrency=${result[0]}, arenaMax=${result[1]}, workersUsed=${result[2]}",
        )
    }

    @Test(timeout = 30_000)
    fun reprapFirmwareG10TemperatureIsShownInGcodePreviewWithoutRetract() {
        val result = Native.reprapfirmware_g10_preview_probe()
        assertEquals("G10 P0 S220 must not create a fake retract in RepRapFirmware", 0, result[0])
        assertEquals("the following extrusion must carry the G10 temperature", 220, result[1])
        assertTrue("G10 must remain a retract for Marlin", result[2] > 0)
    }

    @Test(timeout = 30_000)
    fun clipper2EarlyOutPreservesDisjointAndIntersectingPolygonResults() {
        val result = Native.clipper2_scanbeam_sort_regression()
        assertEquals("all separated polygons must survive the no-crossing path", 128.0, result[0], 0.0)
        assertEquals("disjoint polygon area must remain exact", 2048.0, result[1], 0.0)
        assertEquals("the crossing fixture must produce one intersection polygon", 1.0, result[2], 0.0)
        assertEquals("crossing-edge sort must retain the exact intersection area", 92.0, result[3], 0.0)
    }

    @Test(timeout = 180_000)
    fun paintedBridgeAnchorsOnNeighborFilament() {
        val result = Native.painted_bridge_anchor_regression()
        assertEquals("both neighboring pillar strips must be handed to the painted bridge", 2, result[0])
        assertTrue("the bridge fixture must produce bridge infill", result[1] > 0)
        assertEquals("the fixture must retain separate filament identities", 1, result[2])
    }

    @Test(timeout = 180_000)
    fun paintedModifierSlicesOnlyThePaintedHostSide() {
        val result = Native.painted_modifier_slice_regression()
        assertEquals("painted modifier probe result shape", 7, result.size)
        assertEquals("painted +X face applies its settings to about half the cube layer", 200.0, result[0], 4.0)
        assertEquals("the unpainted host area keeps its original settings", 200.0, result[1], 4.0)
        assertTrue("painted region stays on the +X half", result[2] >= -0.5)
        assertTrue("painted region reaches the +X outer edge", result[3] <= 0.5)
        assertTrue("the layer must retain separate print regions", result[4] >= 2.0)
        assertEquals("unchanged painted modifier data must not invalidate the print", 0.0, result[5], 0.0)
        assertEquals("a painted modifier depth edit must invalidate the print", 1.0, result[6], 0.0)
    }

    @Test(timeout = 60_000)
    fun paintedModifierBbs3mfRoundtripPreservesTypePaintAndSettings() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "painted-modifier-roundtrip-${System.nanoTime()}.3mf")
        try {
            val result = Native.painted_modifier_bbs_roundtrip(file.absolutePath)

            assertEquals("BBS and classic roundtrip result shape", 12, result.size)
            assertEquals("BBS retains all +X cube facets", 12, result[0])
            assertEquals(12, result[1])
            assertEquals("BBS painted facet mask is identical", 1, result[2])
            assertEquals("BBS modifier host reference is restored", 1, result[3])
            assertEquals("BBS modifier depth is restored", 1, result[4])
            assertEquals("BBS modifier config is restored", 1, result[5])
            assertEquals("classic 3MF retains all +X cube facets", 12, result[6])
            assertEquals(12, result[7])
            assertEquals("classic painted facet mask is identical", 1, result[8])
            assertEquals("classic modifier host reference is restored", 1, result[9])
            assertEquals("classic modifier depth is restored", 1, result[10])
            assertEquals("classic modifier config is restored", 1, result[11])
        } finally {
            file.delete()
            File(file.parentFile, file.name + ".classic.3mf").delete()
            File(file.parentFile, file.name + ".tmp").delete()
            File(file.parentFile, file.name + ".classic.3mf.tmp").delete()
        }
    }

    @Test
    fun postSlicePrintableAreaMaskSupportsExtruder64() {
        assertArrayEquals(
            "the post-slice validator must preserve bit 63 without setting a lower extruder bit",
            intArrayOf(1, 1, 1, 1),
            Native.gcode_extruder_64_area_mask_regression(),
        )
    }

    @Test(timeout = 180_000)
    fun estimatedFirstLayerCommentReportsLayerMotionTime() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = copyCube(context, "first-layer-time-comment.stl")
        val model = Model(source).also { it.ensureOnBed(0) }
        val configFile = SliceBeam.getCurrentConfigFile()
        val gcodeFile = File(context.cacheDir, "first-layer-time-comment.gcode")
        try {
            val process = requireNotNull(SliceBeam.CONFIG?.findPrint("0.16mm Standard"))
            process.put("layer_height", "0.2")
            process.put("initial_layer_print_height", "0.2")
            process.put("skirt_loops", "0")
            process.put("brim_type", "no_brim")
            SliceBeam.genCurrentConfig(model)
            val generated = model.slice(configFile.absolutePath, gcodeFile.absolutePath, null)
            try {
                val gcode = gcodeFile.readText()
                val reported = Regex("(?m)^; estimated first layer printing time \\(normal mode\\) = (.+)$")
                    .find(gcode)?.groupValues?.get(1)
                assertNotNull("the slicer must emit the first-layer estimate", reported)
                val parsed = GCodeProcessorResult(gcodeFile)
                try {
                    val expected = formatGCodeDuration(parsed.getInitialLayerTime())
                    assertEquals("the comment must report first-layer motion time, not preparation time", expected, reported)
                    assertTrue("first-layer motion time must be positive", parsed.getInitialLayerTime() > 0f)
                } finally {
                    parsed.release()
                }
            } finally {
                generated.release()
            }
        } finally {
            model.release()
            source.delete()
            configFile.delete()
            gcodeFile.delete()
        }
    }

    @Test(timeout = 180_000)
    fun directVariableLayerHeightBrushChangesTheRealSliceProfile() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = copyCube(context, "variable-layer-brush.stl")
        val model = Model(source).also {
            it.ensureOnBed(0)
            it.translate(0, 37.0, 31.0, 0.0)
        }
        val configFile = SliceBeam.getCurrentConfigFile()
        val process = requireNotNull(SliceBeam.CONFIG?.findPrint("0.16mm Standard"))
        val gcodeFile = File(context.cacheDir, "variable-layer-brush.gcode")
        var sliced: GCodeProcessorResult? = null
        try {
            process.put("layer_height", "0.2")
            process.put("initial_layer_print_height", "0.2")
            process.put("skirt_loops", "0")
            process.put("brim_type", "no_brim")
            SliceBeam.genCurrentConfig(model)

            model.applyAdaptiveLayerHeight(0, configFile.absolutePath, 0.5f)
            val originalProfile = model.adaptiveLayerHeightProfile(0)
            assertTrue("adaptive profile must be generated before painting", originalProfile.size >= 4)

            val minimum = Vec3d()
            val maximum = Vec3d()
            model.getBoundingBoxExact(0, minimum, maximum)
            val worldZ = minimum.z + (maximum.z - minimum.z) * 0.5
            fun profileHeightAt(profile: DoubleArray, z: Double): Double =
                profile.indices.step(2).minByOrNull { kotlin.math.abs(profile[it] - z) }
                    ?.let { profile[it + 1] } ?: Double.NaN

            sliced = model.slice(configFile.absolutePath, gcodeFile.absolutePath, null)
            val baselineLayerZs = layerZValues(gcodeFile.readText())
            sliced?.release()
            sliced = null
            gcodeFile.delete()

            assertTrue(
                "the brush should map a translated world-space hit back into the model profile",
                model.adjustLayerHeightAtWorldPoint(
                    0, configFile.absolutePath,
                    minimum.x, (minimum.y + maximum.y) * 0.5, worldZ,
                    4.0, 0.05, 1, 0.5f,
                ),
            )
            assertTrue("a surface stroke must create a persistent object layer profile", model.hasAdaptiveLayerHeight(0))
            val profile = model.adaptiveLayerHeightProfile(0)
            assertTrue("the persisted layer-height profile must contain Z/height samples", profile.size >= 4 && profile.size % 2 == 0)
            val originalHeight = profileHeightAt(originalProfile, worldZ)
            val paintedHeight = profileHeightAt(profile, worldZ)
            assertTrue(
                "the brush must lower the adaptive profile at Z=$worldZ from $originalHeight to $paintedHeight",
                paintedHeight <= originalHeight - 0.045,
            )

            sliced = model.slice(configFile.absolutePath, gcodeFile.absolutePath, null)
            val gcode = gcodeFile.readText()
            assertHasExtrusion(gcode)
            val layerZs = layerZValues(gcode)
            val layerHeights = layerZs.zipWithNext { lower, upper -> upper - lower }
            assertTrue("the edited model must slice into multiple layers", layerHeights.size > 3)
            val baselineHeightsNearPaint = baselineLayerZs.zipWithNext { lower, upper -> lower to (upper - lower) }
                .filter { it.first in (worldZ - 2.0)..(worldZ + 2.0) }
                .map { it.second }
            val paintedHeightsNearPaint = layerZs.zipWithNext { lower, upper -> lower to (upper - lower) }
                .filter { it.first in (worldZ - 2.0)..(worldZ + 2.0) }
                .map { it.second }
            assertTrue("baseline slice must include layers in the painted band", baselineHeightsNearPaint.isNotEmpty())
            assertTrue("painted slice must include layers in the painted band", paintedHeightsNearPaint.isNotEmpty())
            assertTrue(
                "the directly painted band must change G-code layer heights; profile=${profile.contentToString()}, baseline=$baselineHeightsNearPaint, painted=$paintedHeightsNearPaint",
                paintedHeightsNearPaint.min() < baselineHeightsNearPaint.min() - 0.02,
            )
        } finally {
            sliced?.release()
            model.release()
            source.delete()
            configFile.delete()
            gcodeFile.delete()
        }
    }

    @Test(timeout = 180_000)
    fun differentHeightObjectsWithAdaptiveProfilesSharePrimeTowerLayers() = withToolChangerProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = requireNotNull(SliceBeam.CONFIG)
        val process = requireNotNull(config.findPrint("0.20mm Standard"))
        process.put("layer_height", "0.2")
        process.put("initial_layer_print_height", "0.2")
        process.put("precise_z_height", "1")
        process.put("enable_prime_tower", "1")

        val source = copyCube(context, "adaptive-prime-tower-tall.stl")
        val shortSource = copyCubeScaledZ(context, "adaptive-prime-tower-short.stl", 0.55f)
        val output = File(context.cacheDir, "adaptive-prime-tower-different-heights.gcode")
        val model = Model(source)
        val shortModel = Model(shortSource)
        var result: GCodeProcessorResult? = null
        try {
            model.addObject(shortModel, 0)
            model.translate(1, 35.0, 0.0, 0.0)
            model.setUniformFilament(0, 1)
            model.setUniformFilament(1, 2)
            SliceBeam.genCurrentConfig(model)
            val configFile = SliceBeam.getCurrentConfigFile()
            model.applyAdaptiveLayerHeight(0, configFile.absolutePath, 0.5f)
            assertTrue("the tall adaptive profile must fit to the shorter object", model.fitAdaptiveLayerHeightProfileToObject(0, 1))
            assertTrue("both separate objects must keep their own variable layer profile", model.hasAdaptiveLayerHeight(0) && model.hasAdaptiveLayerHeight(1))
            val tallProfile = model.adaptiveLayerHeightProfile(0)
            val shortProfile = model.adaptiveLayerHeightProfile(1)
            assertTrue("the shorter profile must end at its own top", shortProfile[shortProfile.size - 2] < tallProfile[tallProfile.size - 2])
            for (sample in 0 until shortProfile.size - 2)
                assertEquals("shared adaptive profile sample $sample", tallProfile[sample], shortProfile[sample], 0.000001)
            SliceBeam.genCurrentConfig(model)

            result = model.slice(configFile.absolutePath, output.absolutePath, null)
            val gcode = output.readText()
            assertHasExtrusion(gcode)
            assertTrue("both assigned tools must be emitted", Regex("(?m)^T0(?:\\s|$)").containsMatchIn(gcode) &&
                Regex("(?m)^T1(?:\\s|$)").containsMatchIn(gcode))
            assertTrue("variable layer heights on differently tall objects must slice with the prime tower", gcode.contains("; total layer number:") &&
                gcode.contains("Prime tower", ignoreCase = true))
            result?.release()
            result = null

            model.applyAdaptiveLayerHeight(1, configFile.absolutePath, 0.5f)
            val independentlyAdaptiveShortProfile = model.adaptiveLayerHeightProfile(1)
            assertTrue("independent profiles must differ in the common-height region",
                independentlyAdaptiveShortProfile.contentEquals(shortProfile).not())
            SliceBeam.genCurrentConfig(model)
            val rejected = try {
                model.slice(configFile.absolutePath, output.absolutePath, null).release()
                false
            } catch (error: Slic3rRuntimeError) {
                error.message?.contains("same variable layer height") == true
            }
            assertTrue("different effective layers must remain rejected for a prime tower", rejected)
        } finally {
            result?.release()
            model.release()
            shortModel.release()
            source.delete()
            shortSource.delete()
            output.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    private fun formatGCodeDuration(seconds: Float): String {
        val days = (seconds / 86_400f).toInt()
        var remaining = seconds - days * 86_400f
        val hours = (remaining / 3_600f).toInt()
        remaining -= hours * 3_600f
        val minutes = (remaining / 60f).toInt()
        remaining -= minutes * 60f
        return when {
            days > 0 -> "${days}d ${hours}h ${minutes}m ${remaining.toInt()}s"
            hours > 0 -> "${hours}h ${minutes}m ${remaining.toInt()}s"
            minutes > 0 -> "${minutes}m ${remaining.toInt()}s"
            remaining > 1f -> "${remaining.toInt()}s"
            else -> String.format(java.util.Locale.US, "%fs", remaining)
        }
    }

    @Test
    fun spiralInsetArachneKeepsSolidSurfaceWallPaths() {
        assertArrayEquals(intArrayOf(1, 1, 1), Native.spiral_inset_solid_regression())
    }

    @Test
    fun dovetailCutPreservesPerPartFilamentAndMaterialIdentity() {
        assertArrayEquals(
            "Dovetail merge must retain T1/T2 and material-separated parts while merging identical settings",
            intArrayOf(2, 2, 3, 2, 2, 3, 2, 1, 1),
            Native.cut_preserves_part_filaments_regression(),
        )
    }

    @Test(timeout = 120_000)
    fun objectsWithDifferentHeightRangeOverridesDoNotShareSliceCache() {
        withKobraProfile {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val source = copyCube(context, "layer-range-cache-sharing.stl")
            val configFile = SliceBeam.getCurrentConfigFile()
            var model: Model? = null
            try {
                model = Model(source).also {
                    it.addObject(it, 0)
                    it.ensureOnBed(0)
                    it.ensureOnBed(1)
                }
                SliceBeam.genCurrentConfig(requireNotNull(model))
                assertTrue("height range must attach to the first copied mesh object",
                    Native.model_add_layer_range_test_config(model.pointer, 0.4, 1.2, 0.1, 35.0))
                assertArrayEquals(
                    "A copied mesh with a range override must not inherit the other object's cached layers",
                    intArrayOf(2, 0),
                    Native.layer_range_cache_isolation_regression(model.pointer, configFile.absolutePath),
                )
            } finally {
                model?.release()
                source.delete()
                configFile.delete()
            }
        }
    }

    @Test(timeout = 120_000)
    fun objectsWithDifferentCenterOffsetsDoNotShareSliceCache() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = copyCube(context, "center-offset-cache-source.stl")
        val configFile = SliceBeam.getCurrentConfigFile()
        var model: Model? = null
        try {
            model = Model(source).also { it.ensureOnBed(0) }
            SliceBeam.genCurrentConfig(requireNotNull(model))
            assertArrayEquals(
                "Objects with the same transform but different center offsets must not reuse shifted slice layers",
                intArrayOf(1, 1, 0),
                Native.center_offset_slice_cache_regression(configFile.absolutePath),
            )
        } finally {
            model?.release()
            source.delete()
            configFile.delete()
        }
    }

    @Test
    fun sequentialPrintRodClearanceIncludesRaftHeight() {
        assertArrayEquals(
            "A raft that lifts 24.8 mm objects above the 25 mm rod must be rejected; the same objects without a raft fit",
            intArrayOf(1, 0),
            Native.sequential_raft_top_clearance_regression(),
        )
    }

    @Test
    fun sequentialPrintShortObjectClearanceIncludesRaftHeight() {
        assertArrayEquals(
            "Two 2 mm objects fit the short-object clearance without a raft; a three-layer raft lifts them above nozzle height and requires full clearance",
            intArrayOf(1, 0, 0, 1),
            Native.sequential_raft_short_clearance_regression(),
        )
    }


    @Test
    fun textureDisplacementBakeMovesPaintedMeshAndCancellationCannotBeCommitted() {
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.WHITE)
        }
        val png = java.io.ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        }
        bitmap.recycle()

        val result = Native.texture_displacement_bake_regression(png)
        assertEquals("The classic bake preserves the mesh vertex count", result[0].toInt(), result[1].toInt())
        assertTrue("White height texture should displace the painted plane", result[2] > 0.1)
        assertEquals("Cancellation must return an empty, uncommittable mesh", 1.0, result[3], 0.0)
    }

    @Test(timeout = 120_000)
    fun textureDisplacementLayersPaintMasksAndImageRoundTripThroughThreeMf() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = copyCube(context, "texture-displacement-roundtrip.stl")
        val project = File(context.cacheDir, "texture-displacement-roundtrip.3mf")
        var model: Model? = null
        var restored: Model? = null
        var undoSnapshot: Model? = null
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.WHITE)
        }
        val png = java.io.ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        }
        bitmap.recycle()
        try {
            model = Model(source).also { it.ensureOnBed(0) }
            assertTrue("Native test layer must attach to the fixture volume",
                Native.texture_displacement_roundtrip_attach(model.pointer, png))
            val before = Native.texture_displacement_roundtrip_inspect(model.pointer, 0)
            val secondVolumeBefore = Native.texture_displacement_roundtrip_inspect(model.pointer, 1)
            val clonedVolumeBefore = Native.texture_displacement_roundtrip_inspect(model.pointer, 2)
            assertEquals("3MF fixture has one model object", 1, before[6])
            assertEquals("3MF fixture has two distinct volumes and one copied volume", 3, before[7])
            assertEquals("3MF fixture object has an exportable instance", 1, before[8])
            assertEquals("two displacement layers are present", 2, before[0])
            assertEquals("the layer remains in slot 2", 2, before[1])
            assertEquals("active slot is persisted", 2, before[2])
            assertEquals("panel mode is persisted", 1, before[3])
            assertTrue("paint mask covers the test volume", before[4] > 0)
            assertEquals("PNG bytes are attached", png.size, before[5])
            assertEquals("the second volume keeps one layer in the same slot", 1, secondVolumeBefore[0])
            assertEquals("the second volume keeps slot 2", 2, secondVolumeBefore[1])
            assertEquals("the second volume keeps its own panel mode", 0, secondVolumeBefore[3])
            assertArrayEquals("cloning a volume copies the texture stack, image and paint mask",
                before.copyOfRange(0, 6), clonedVolumeBefore.copyOfRange(0, 6))

            // BedFragment's undo history stores Model.merge() snapshots and restores them via
            // replaceContentsFrom(). Exercise that exact Android snapshot path around a mutation.
            undoSnapshot = Model.merge(requireNotNull(model))
            assertArrayEquals("Model.merge undo snapshot copies texture state",
                before, Native.texture_displacement_roundtrip_inspect(requireNotNull(undoSnapshot).pointer, 0))
            assertTrue("fixture mutation clears texture data before restore",
                Native.texture_displacement_roundtrip_clear(requireNotNull(model).pointer, 0))
            assertEquals("mutation actually removed texture layers", 0,
                Native.texture_displacement_roundtrip_inspect(requireNotNull(model).pointer, 0)[0])
            requireNotNull(model).replaceContentsFrom(requireNotNull(undoSnapshot))
            assertArrayEquals("model restore path recovers the complete texture state",
                before, Native.texture_displacement_roundtrip_inspect(requireNotNull(model).pointer, 0))

            SliceBeam.genCurrentConfig(requireNotNull(model))
            assertEquals("model survives native config preparation", 1, requireNotNull(model).objectsCount)
            assertEquals("model remains a single-instance object", 1,
                Native.texture_displacement_roundtrip_inspect(model.pointer, 0)[8])
            model.export3mf(SliceBeam.getCurrentConfigFile().absolutePath, project.absolutePath)
            val archiveEntries = ZipFile(project).use { archive ->
                val names = java.util.Collections.list(archive.entries()).map { it.name }
                  val modelXml = archive.getEntry("3D/3dmodel.model")?.let { entry ->
                      archive.getInputStream(entry).bufferedReader().use { it.readText() }
                  }.orEmpty()
                val modelConfig = archive.getEntry("Metadata/Slic3r_PE_model.config")?.let { entry ->
                    archive.getInputStream(entry).bufferedReader().use { it.readText() }
                }.orEmpty()
                assertTrue("3MF volume config must reference texture JSON; objects=${requireNotNull(model).objectsCount}; model=$modelXml; config=$modelConfig",
                      modelConfig.contains("key=\"texture_displacement\""))
                assertTrue("3MF includes the layer JSON; archive entries=$names", names.any { it.startsWith("Metadata/texture_displacement/") && it.endsWith(".json") })
                assertEquals("each volume and layer has its own embedded PNG", 5,
                    names.count { it.startsWith("Metadata/texture_displacement/") && it.endsWith(".png") })
                names
            }
            assertTrue("exported 3MF has project configuration", archiveEntries.any { it == "Metadata/Slic3r_PE_model.config" })

            restored = Model(project)
            assertArrayEquals("layer stack, image, paint mask and options survive reload",
                before, Native.texture_displacement_roundtrip_inspect(restored.pointer, 0))
            assertArrayEquals("same-slot data in a second volume survives reload",
                secondVolumeBefore, Native.texture_displacement_roundtrip_inspect(restored.pointer, 1))
            assertArrayEquals("copied volume texture data survives reload",
                clonedVolumeBefore, Native.texture_displacement_roundtrip_inspect(restored.pointer, 2))
        } finally {
            model?.release()
            restored?.release()
            undoSnapshot?.release()
            source.delete()
            project.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test
    fun flashforge032ProcessIsLimitedToNozzlesThatFitItsLineWidth() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val profile = context.assets.open("orca_profiles/Flashforge.ini").use {
            Slic3rConfigWrapper(it).findPrint("0.32mm Standard")
        }
        assertNotNull("The bundled Flashforge 0.32 mm process must remain available", profile)
        val settings = requireNotNull(profile)
        assertEquals("0.32", settings.get("layer_height"))
        assertEquals("0.82", settings.get("line_width"))
        assertFalse("0.82 mm line width must not be offered for a 0.6 mm nozzle",
            Slic3rUtils.lineWidthFitsNozzle(settings.get("line_width"), "0.6"))
        assertTrue("0.82 mm line width must remain available for a 0.8 mm nozzle",
            Slic3rUtils.lineWidthFitsNozzle(settings.get("line_width"), "0.8"))
    }

    @Test(timeout = 120_000)
    fun malformedOtherLayerPrintSequenceIsIgnoredAndStillSlices() = withKobraProfile {
        assertArrayEquals(
            "Malformed layer-sequence groups must be ignored while valid groups still parse",
            intArrayOf(1, 1, 1, 1, 1),
            Native.other_layers_print_sequence_regression_probe(),
        )

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val process = requireNotNull(SliceBeam.CONFIG?.findPrint("0.16mm Standard"))
        process.put("other_layers_print_sequence_nums", "2")
        process.put("other_layers_print_sequence", "1")
        val source = copyCube(context, "malformed-other-layer-sequence.stl")
        val configFile = SliceBeam.getCurrentConfigFile()
        var model: Model? = null
        try {
            model = Model(source).also { it.ensureOnBed(0) }
            SliceBeam.genCurrentConfig(model)
            val generatedConfig = configFile.readText()
            assertTrue("The malformed group count must reach the native slice config", generatedConfig.contains("other_layers_print_sequence_nums = 2"))
            assertTrue("The mismatched sequence values must reach the native slice config", generatedConfig.contains("other_layers_print_sequence = 1"))

            val gcode = sliceToGcode(context, model, "malformed-other-layer-sequence.gcode")
            assertHasExtrusion(gcode)
        } finally {
            model?.release()
            source.delete()
            configFile.delete()
        }
    }

    @Test
    fun sharedNozzleMultiMaterialDoesNotRestoreDeletedFilamentSlot() {
        assertArrayEquals(
            "Shared-nozzle MMU should keep one filament after deletion; tool changers still need one per nozzle",
            intArrayOf(1, 2),
            Native.shared_nozzle_filament_count_regression(),
        )
    }

    @Test
    fun nullableConfigArrayNilChecksMirrorFirstValueForMissingToolSlots() {
        assertArrayEquals(
            "Out-of-range nullable reads must mirror ConfigOptionVector.get_at, and empty arrays must remain nil",
            IntArray(10) { 1 },
            Native.config_nil_bounds_regression(),
        )
    }

    @Test
    fun amsSyncPreservesInheritedUserFilamentOverrides() {
        assertArrayEquals(
            "AMS sync must retain the inherited custom PLA preset and its speed override while updating tray color",
            intArrayOf(1, 75, 1, 1),
            Native.ams_sync_preserves_inherited_filament_regression(),
        )
    }

    @Test
    fun projectWithFewerFilamentsThanToolsKeepsAllSlotArraysInSync() {
        assertArrayEquals(
            "Project load must extend parallel slot arrays, preserve imported colors, and complete a four-tray overwrite sync",
            intArrayOf(4, 4, 4, 4, 4, 4, 64, 1, 4, 4, 1, 64),
            Native.short_project_filament_arrays_regression(),
        )
    }

    @Test
    fun treeSupportGroupedUnionPreservesDensePolygonRegions() {
        assertArrayEquals(
            "Grouped tree-support unions must preserve topology and stay within sub-micron rounding",
            intArrayOf(1, 1, 1, 1),
            Native.tree_support_grouped_union_regression(),
        )
    }

    @Test(timeout = 120_000)
    fun wipeInwardChangesOnlyTheGeneratedWipePathAndKeepsGcodeValid() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = requireNotNull(SliceBeam.CONFIG)
        val process = requireNotNull(config.findPrint("0.16mm Standard"))
        val filament = requireNotNull(config.findFilament("Anycubic Generic PLA"))
        process.put("wall_loops", "3")
        process.put("wipe_inward_distance", "50%")
        process.put("gcode_comments", "1")
        filament.put("wipe", "1")
        filament.put("wipe_distance", "3")

        val source = copyCube(context, "wipe-inward-source.stl")
        val configFile = SliceBeam.getCurrentConfigFile()
        var model: Model? = null
        try {
            model = Model(source).also { it.ensureOnBed(0) }
            fun wipeMoves(gcode: String): List<String> {
                var active = false
                return gcode.lineSequence().mapNotNull { line ->
                    if (line.contains("WIPE_START")) active = true
                    val move = if (active && line.startsWith("G1 ") &&
                        Regex("\\b[XY][+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)").containsMatchIn(line) &&
                        Regex("\\bE[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)").containsMatchIn(line)) line else null
                    if (line.contains("WIPE_END")) active = false
                    move
                }.toList()
            }

            fun assertWipeDiffersForWallOrder(wallOrder: String, suffix: String) {
                process.put("wall_sequence", wallOrder)
                process.put("wipe_inward", "0")
                SliceBeam.genCurrentConfig(model)
                val disabledConfig = configFile.readText()
                assertTrue("Disabled state must reach native config for $wallOrder", disabledConfig.contains("wipe_inward = 0"))
                assertTrue("Wipe distance must reach native config", disabledConfig.contains("wipe_inward_distance = 50%"))
                val ordinaryGcode = sliceToGcode(context, model, "wipe-inward-$suffix-disabled.gcode")
                assertHasExtrusion(ordinaryGcode)
                assertTrue("Fixture must exercise actual wipe paths for $wallOrder", ordinaryGcode.contains("WIPE_START"))

                process.put("wipe_inward", "1")
                SliceBeam.genCurrentConfig(model)
                val enabledConfig = configFile.readText()
                assertTrue("Enabled state must reach native config for $wallOrder", enabledConfig.contains("wipe_inward = 1"))
                val inwardGcode = sliceToGcode(context, model, "wipe-inward-$suffix-enabled.gcode")
                assertHasExtrusion(inwardGcode)
                assertTrue("Enabled Wipe Inward must keep executable wipe segments for $wallOrder", inwardGcode.contains("WIPE_START"))

                val regularWipes = wipeMoves(ordinaryGcode)
                val inwardWipes = wipeMoves(inwardGcode)
                assertTrue("Both $wallOrder slices must contain measurable wipe moves", regularWipes.isNotEmpty() && inwardWipes.isNotEmpty())
                assertNotEquals("Inward setting must alter emitted wipe geometry for $wallOrder", regularWipes, inwardWipes)
                Log.i("NativeSliceDeviceTest", "PASS Wipe Inward $wallOrder: ${regularWipes.size} regular and ${inwardWipes.size} inward wipe moves")
            }

            assertWipeDiffersForWallOrder("inner wall/outer wall", "inner-outer")
            assertWipeDiffersForWallOrder("inner-outer-inner wall", "sandwich")
        } finally {
            model?.release()
            source.delete()
            configFile.delete()
        }
    }

    @Test
    fun worldTranslationKeepsRotatedAndScaledObjectBoundsAlignedWithDragDelta() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelFile = copyCube(context, "nightly-world-translation-transform.stl")
        val model = Model(modelFile)
        try {
            model.rotate(0, 0.0, 0.0, Math.toRadians(37.0))
            model.scale(0, 1.7, 0.65, 1.25)
            val beforeMin = Vec3d()
            val beforeMax = Vec3d()
            model.getBoundingBoxExact(0, beforeMin, beforeMax)
            val beforeCenter = Vec3d(
                (beforeMin.x + beforeMax.x) / 2.0,
                (beforeMin.y + beforeMax.y) / 2.0,
                (beforeMin.z + beforeMax.z) / 2.0,
            )

            val delta = Vec3d(18.25, -9.5, 4.0)
            model.translate(0, delta.x, delta.y, delta.z)

            val afterMin = Vec3d()
            val afterMax = Vec3d()
            model.getBoundingBoxExact(0, afterMin, afterMax)
            val reportedPosition = Vec3d()
            model.getTranslation(0, reportedPosition)
            assertEquals("translated model bounds follow the world-space X drag", delta.x,
                (afterMin.x + afterMax.x) / 2.0 - beforeCenter.x, 0.01)
            assertEquals("translated model bounds follow the world-space Y drag", delta.y,
                (afterMin.y + afterMax.y) / 2.0 - beforeCenter.y, 0.01)
            assertEquals("translated model bounds follow the world-space Z drag", delta.z,
                (afterMin.z + afterMax.z) / 2.0 - beforeCenter.z, 0.01)
            assertEquals("object-list X position matches the rendered bounds", beforeCenter.x + delta.x,
                reportedPosition.x, 0.01)
            assertEquals("object-list Y position matches the rendered bounds", beforeCenter.y + delta.y,
                reportedPosition.y, 0.01)
            assertEquals("object-list Z position matches the rendered bounds", beforeCenter.z + delta.z,
                reportedPosition.z, 0.01)
        } finally {
            model.release()
            modelFile.delete()
        }
    }

    @Test
    fun sparseInfillJoiningAvoidsLoopsAndCrossings() {
        assertArrayEquals("Orca sparse-infill joining must connect nearby lines, avoid closed loops, and reject crossings",
            intArrayOf(1, 1, 1, 1), Native.joined_infill_regression())
    }

    @Test(timeout = 120_000)
    fun orcaPrimitiveModelsHaveExpectedBoundsAndProduceExtrusion() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val shapes = listOf(
            Triple(0, doubleArrayOf(20.0, 15.0, 10.0), "Cube"),
            Triple(1, doubleArrayOf(20.0, 0.0, 10.0), "Cylinder"),
            Triple(2, doubleArrayOf(20.0, 0.0, 10.0), "Cone"),
            Triple(3, doubleArrayOf(20.0, 0.0, 0.0), "Sphere"),
            Triple(4, doubleArrayOf(24.0, 4.0, 0.0), "Torus"),
            Triple(5, doubleArrayOf(20.0, 0.0, 2.0), "Disc"),
            Triple(6, doubleArrayOf(20.0, 15.0, 2.0), "Slab"),
        )
        shapes.forEachIndexed { ordinal, (type, dims, name) ->
            val model = Model()
            val project = File(context.cacheDir, "orca-primitive-$ordinal.3mf")
            var restored: Model? = null
            try {
                val index = model.createPrimitive(type, dims[0], dims[1], dims[2], name, centerX = 100.0, centerY = 100.0)
                assertEquals("$name should produce one independent printable object", 0, index)
                assertEquals(1, model.objectsCount)
                assertEquals(name, model.getObjectName(index))
                assertTrue("$name should have a nonempty mesh", model.getTriangleCount(index) > 0)
                val min = Vec3d(); val max = Vec3d()
                model.getBoundingBoxExact(index, min, max)
                assertEquals("$name should rest on the bed", 0.0, min.z, 1e-3)
                assertTrue("$name should have positive height", max.z - min.z >= 1.9)
                assertTrue("$name should fit the test bed", max.x - min.x <= 24.1 && max.y - min.y <= 24.1)
                assertTrue("$name should be placed on the positive-coordinate bed", min.x >= 0.0 && min.y >= 0.0)
                SliceBeam.genCurrentConfig(model)
                model.export3mf(SliceBeam.getCurrentConfigFile().absolutePath, project.absolutePath)
                val roundTrip = Model(project)
                restored = roundTrip
                assertEquals("$name should survive 3MF project storage", name, roundTrip.getObjectName(0))
                assertEquals("$name should retain its mesh", model.getTriangleCount(0), roundTrip.getTriangleCount(0))
                val gcode = sliceToGcode(context, roundTrip, "orca-primitive-$ordinal.gcode")
                assertHasExtrusion(gcode)
            } finally {
                restored?.release()
                model.release()
                project.delete()
                SliceBeam.getCurrentConfigFile().delete()
            }
        }
    }

    @Test
    fun primitiveCircularMeshResolutionAdaptsToSizeWithinMobileFacetBound() {
        val model = Model()
        try {
            val smallCylinder = model.createPrimitive(1, 0.2, 0.0, 10.0, "Small cylinder")
            assertEquals(0, smallCylinder)
            val smallTriangles = model.getTriangleCount(smallCylinder)
            assertEquals("minimum circular detail is retained", 64, smallTriangles)

            val largeCylinder = model.createPrimitive(1, 500.0, 0.0, 10.0, "Large cylinder")
            assertEquals(1, largeCylinder)
            val largeTriangles = model.getTriangleCount(largeCylinder)
            assertTrue("large circular primitives gain detail", largeTriangles > smallTriangles)
            assertEquals("360-segment mobile ceiling bounds the cylinder mesh", 1440, largeTriangles)

            val smallSphere = model.createPrimitive(3, 2.0, 0.0, 0.0, "Small sphere")
            assertEquals(2, smallSphere)
            val largeSphere = model.createPrimitive(3, 500.0, 0.0, 0.0, "Large sphere")
            assertEquals(3, largeSphere)
            assertTrue("sphere tessellation follows requested size",
                model.getTriangleCount(largeSphere) > model.getTriangleCount(smallSphere))
            assertTrue("large sphere stays within the 360-by-180 mobile angular budget",
                model.getTriangleCount(largeSphere) <= 360 * 180 * 2)
        } finally {
            model.release()
        }
    }

    @Test
    fun cgalBooleanVolumeChecksIgnoreUnreferencedVertices() {
        val result = Native.mesh_boolean_isolated_vertices_probe()
        assertEquals("the converted closed cube must bound a volume", 1.0, result[0], 0.0)
        assertEquals("the converted cube must remain non-self-intersecting", 0.0, result[1], 0.0)
        assertEquals("isolated vertices must be removed without changing volume", 1000.0, result[2], 1e-3)
        assertEquals("compacted triangle indices must remain valid", 1.0, result[3], 0.0)
        assertEquals("all twelve cube triangles must survive conversion", 12.0, result[4], 0.0)
    }

    @Test
    fun gyroidAndTpmsClippedRingsKeepTheirEndpointsOnTheBoundary() {
        val result = Native.tpms_ring_endpoints_probe()
        assertEquals("all pattern, multiline, and phase cases must execute", 54, result[0])
        assertEquals("closed rings must not begin on a clipped wall; open paths must end on the wall", 0, result[1])
    }

    @Test
    fun multilineInfillClippedRingEndpointsStayOnTheBoundary() {
        val result = Native.multiline_infill_ring_endpoints_probe()
        assertEquals("all multiline pattern, hole, and phase cases must execute: ${result.contentToString()}", 90, result[0])
        assertTrue("pattern failures by order (Gyroid, TPMS-D/FK, Honeycomb, Quarter Cubic, Lateral Lattice/Honeycomb, " +
            "Rectilinear/Aligned, Grid, Triangles, Stars, Cubic, Cross-Hatch, Hilbert): ${result.contentToString()}",
            result.sliceArray(1..15).all { it == 0 })
        assertArrayEquals("Grid failure categories (empty fill, short closed path, boundary-cut ring)",
            intArrayOf(0, 0, 0), result.sliceArray(16..18))
    }

    @Test
    fun changingFilamentCountPreservesEveryNozzleVariantValue() {
        val result = Native.filament_variant_resize_regression_probe()
        assertArrayEquals(
            "variant-bearing values are sized by the preset's slot/variant layout, not slot count",
            intArrayOf(1, 2, 2, 1),
            result,
        )
    }

    @Test
    fun curledExtrusionEstimationIsSkippedUnlessBothFeaturesAreEnabled() {
        assertArrayEquals(
            "curl estimation is only needed when overhang speed and curled-perimeter slowdown are both active",
            intArrayOf(0, 0, 0, 1, 1),
            Native.curled_extrusion_estimation_policy_probe(),
        )
    }

    @Test(timeout = 120_000)
    fun bridgeInfillKeepsBridgeSpeedWithCurledPerimeterSlowdown() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val process = requireNotNull(SliceBeam.CONFIG?.findPrint("0.16mm Standard"))
        val keys = listOf(
            "initial_layer_print_height", "layer_height", "enable_overhang_speed",
            "slowdown_for_curled_perimeters", "overhang_1_4_speed", "overhang_2_4_speed",
            "overhang_3_4_speed", "overhang_4_4_speed", "bridge_speed",
            "filament_max_volumetric_speed", "slow_down_for_layer_cooling", "slow_down_layers",
        )
        val previous = keys.associateWith { process[it] }
        val source = File(context.cacheDir, "orca-bridge-curled-speed.obj")
        val output = File(context.cacheDir, "orca-bridge-curled-speed.gcode")
        val model = try {
            InstrumentationRegistry.getInstrumentation().context.assets.open("models/orca-bridge-speed.obj").use { input -> source.outputStream().use(input::copyTo) }
            Model(source).also { it.ensureOnBed(0) }
        } catch (error: Exception) {
            source.delete()
            throw error
        }

        try {
            mapOf(
                "initial_layer_print_height" to "0.2", "layer_height" to "0.2",
                "enable_overhang_speed" to "1", "slowdown_for_curled_perimeters" to "1",
                "overhang_1_4_speed" to "0", "overhang_2_4_speed" to "50",
                "overhang_3_4_speed" to "30", "overhang_4_4_speed" to "10",
                "bridge_speed" to "45", "filament_max_volumetric_speed" to "1000",
                "slow_down_for_layer_cooling" to "0", "slow_down_layers" to "0",
            ).forEach { (key, value) -> process.put(key, value) }
            SliceBeam.genCurrentConfig(model)
            val config = SliceBeam.getCurrentConfigFile()
            output.delete()
            val result = model.slice(config.absolutePath, output.absolutePath, null)
            val gcode = try {
                assertTrue("The bridge fixture must produce a native G-code slice", output.isFile && output.length() > 0L)
                output.readText()
            } finally {
                result.release()
            }

            val bridgeMoves = mutableListOf<Triple<Double, Double, Double>>()
            var isBridge = false
            var x = 0.0
            var z = 0.0
            var e = 0.0
            var feed = 0.0
            val parameter = Regex("([XYZEF])-?\\d+(?:\\.\\d+)?")
            for (line in gcode.lineSequence()) {
                val comment = line.substringAfter(';', "")
                if ("FEATURE:" in comment || "TYPE:" in comment)
                    isBridge = "Bridge" in comment && "Internal Bridge" !in comment
                if (!line.startsWith("G0 ") && !line.startsWith("G1 ")) continue
                val values = parameter.findAll(line.substringBefore(';')).associate { match ->
                    match.groupValues[1] to match.value.substring(1).toDouble()
                }
                val nextX = values["X"] ?: x
                val nextZ = values["Z"] ?: z
                val nextE = values["E"] ?: e
                feed = values["F"]?.div(60.0) ?: feed
                if (isBridge && nextZ in 5.19..5.21 && nextE > e && kotlin.math.abs(nextX - x) > 1.0)
                    bridgeMoves += Triple(x, nextX, feed)
                x = nextX
                z = nextZ
                e = nextE
            }
            assertTrue("The fixture must emit bridge infill at Z=5.2", bridgeMoves.isNotEmpty())
            val minX = bridgeMoves.minOf { minOf(it.first, it.second) }
            val maxX = bridgeMoves.maxOf { maxOf(it.first, it.second) }
            val margin = (maxX - minX) * 0.25
            val centerSpeeds = bridgeMoves.filter { (it.first + it.second) * 0.5 > minX + margin &&
                (it.first + it.second) * 0.5 < maxX - margin }.map { it.third }
            assertTrue("the bridge fixture must contain interior strands", centerSpeeds.isNotEmpty())
            centerSpeeds.forEach { speed ->
                assertEquals("fully unsupported bridge center must use configured bridge speed", 45.0, speed, 0.5)
            }
        } finally {
            keys.forEach { key ->
                val value = previous[key]
                if (value == null) process.remove(key) else process.put(key, value)
            }
            model.release()
            source.delete()
            output.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test
    fun placeholderVectorsUseFilamentOrMappedExtruderIndexAsAppropriate() {
        assertArrayEquals(
            arrayOf("0.4", "3", "0.4", "0.4", "0.2", "0.6"),
            Native.placeholder_filament_map_regression(),
        )
    }

    @Test
    fun inheritedPrinterPresetRetainsNozzleValuesBeyondItsParentCount() {
        assertArrayEquals(
            doubleArrayOf(2.0, 4.0, 0.4, 0.5, 0.6, 0.8),
            Native.preset_parent_nozzle_extension_regression(),
            0.0001,
        )
    }

    @Test(timeout = 120_000)
    fun experimentalHighPrecisionSettingChangesRealSliceCoordinatesAndExtrusionDigits() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val process = requireNotNull(SliceBeam.CONFIG?.findPrint("0.16mm Standard"))
        val previousValue = process["high_precision_gcode"]
        val previousRelativeE = process["use_relative_e_distances"]
        val source = createThinCubeFixture("high-precision-gcode.stl", 20.0, 20.0, 12.0)
        val output = File(context.cacheDir, "high-precision-gcode.gcode")
        val model = Model(source).also { it.ensureOnBed(0) }

        fun slice(enabled: Boolean): String {
            process.put("high_precision_gcode", if (enabled) "1" else "0")
            SliceBeam.genCurrentConfig(model)
            val config = SliceBeam.getCurrentConfigFile()
            val serialized = config.readText()
            assertTrue("The precision option must reach native slicing", Regex("(?m)^high_precision_gcode\\s*=\\s*${if (enabled) "1" else "0"}\\s*$").containsMatchIn(serialized))
            output.delete()
            val result = model.slice(config.absolutePath, output.absolutePath, null)
            try {
                assertTrue("Both precision modes must produce a complete native slice", output.isFile && output.length() > 0L)
                return output.readText()
            } finally {
                result.release()
            }
        }

        try {
            process.put("use_relative_e_distances", "1")
            val standard = slice(enabled = false)
            val precise = slice(enabled = true)
            val extrusionMoves = Regex("(?m)^G1 .*\\bE-?\\d+\\.\\d+").findAll(standard).map { it.value }.toList()
            val preciseExtrusionMoves = Regex("(?m)^G1 .*\\bE-?\\d+\\.\\d+").findAll(precise).map { it.value }.toList()
            assertTrue("The test slice must contain extrusion moves", extrusionMoves.isNotEmpty())
            assertTrue("Standard export must retain three decimal places for XY", Regex("(?m)^G1 .*\\bX-?\\d+\\.\\d{3}(?:\\s|$)").containsMatchIn(standard))
            assertTrue("High precision export must write four decimal places for XY", Regex("(?m)^G1 .*\\bX-?\\d+\\.\\d{4}(?:\\s|$)").containsMatchIn(precise))
            assertTrue("Standard export must write five decimal places for relative E", extrusionMoves.any { Regex("\\bE-?\\d+\\.\\d{5}(?:\\s|$)").containsMatchIn(it) })
            assertTrue("High precision export must write six decimal places for relative E", preciseExtrusionMoves.any { Regex("\\bE-?\\d+\\.\\d{6}(?:\\s|$)").containsMatchIn(it) })
        } finally {
            process.put("high_precision_gcode", previousValue)
            process.put("use_relative_e_distances", previousRelativeE)
            model.release()
            source.delete()
            output.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test(timeout = 180_000)
    fun overhangingOuterWallsKeepTheirSeamMarkersInGeneratedAndReopenedGcode() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val process = requireNotNull(SliceBeam.CONFIG?.findPrint("0.16mm Standard"))
        val source = copyAsset(context, "models/sphere.stl", "overhang-outer-seam.stl")
        val model = Model(source).also { it.ensureOnBed(0) }
        val configFile = SliceBeam.getCurrentConfigFile()
        val gcodeFile = File(context.cacheDir, "overhang-outer-seam.gcode")
        val baselineFile = File(context.cacheDir, "overhang-outer-seam-without-tags.gcode")
        try {
            process.put("detect_overhang_wall", "1")
            process.put("layer_height", "0.2")
            process.put("initial_layer_print_height", "0.2")
            process.put("wall_loops", "1")
            process.put("sparse_infill_density", "0%")
            process.put("top_shell_layers", "0")
            process.put("bottom_shell_layers", "0")
            process.put("skirt_loops", "0")
            process.put("brim_type", "no_brim")
            process.put("seam_gap", "10%")

            for (seamSlope in listOf("none", "all")) {
                process.put("seam_slope_type", seamSlope)
                SliceBeam.genCurrentConfig(model)
                val nativeResult = model.slice(configFile.absolutePath, gcodeFile.absolutePath, null)
                try {
                    val gcode = gcodeFile.readText()
                    assertTrue("outer overhangs must be tagged for seam preview", gcode.contains("; OUTER_OVERHANG_WALL"))
                    val reopenedResult = GCodeProcessorResult(gcodeFile)
                    baselineFile.writeText(gcode.replace(Regex("(?m)^; OUTER_OVERHANG_WALL\\r?\\n"), ""))
                    val baselineResult = GCodeProcessorResult(baselineFile)
                    try {
                        assertTrue("the generated sphere must contain seam preview markers", nativeResult.getSeamMarkerCount() > 0)
                        assertEquals(
                            "Reopening generated G-code must preserve seam markers (seam_slope_type=$seamSlope)",
                            nativeResult.getSeamMarkerCount(),
                            reopenedResult.getSeamMarkerCount(),
                        )
                        assertTrue(
                            "outer-overhang tags must add seam markers over the untagged G-code baseline (seam_slope_type=$seamSlope)",
                            nativeResult.getSeamMarkerCount() > baselineResult.getSeamMarkerCount(),
                        )
                    } finally {
                        reopenedResult.release()
                        baselineResult.release()
                    }
                } finally {
                    nativeResult.release()
                }
            }
        } finally {
            model.release()
            source.delete()
            configFile.delete()
            gcodeFile.delete()
            baselineFile.delete()
        }
    }
    @Test(timeout = 120_000)
    fun layerCoolingKeepsExtrusionSpeedsEquivalentForRelativeAndAbsoluteE() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val process = requireNotNull(SliceBeam.CONFIG?.findPrint("0.16mm Standard"))
        val filament = requireNotNull(SliceBeam.CONFIG?.findFilament("Anycubic Generic PLA"))
        val processKeys = listOf("layer_height", "initial_layer_print_height", "use_relative_e_distances")
        val filamentKeys = listOf(
            "filament_max_volumetric_speed", "slow_down_for_layer_cooling", "slow_down_layer_time", "slow_down_min_speed",
        )
        val previousProcess = processKeys.associateWith(process::get)
        val previousFilament = filamentKeys.associateWith(filament::get)
        val source = createThinCubeFixture("relative-e-layer-cooling.stl", 20.0, 20.0, 12.0)
        val output = File(context.cacheDir, "relative-e-layer-cooling.gcode")
        val model = Model(source).also { it.ensureOnBed(0) }

        fun extrusionSpeeds(relativeE: Boolean): List<Double> {
            process.put("layer_height", "0.3")
            process.put("initial_layer_print_height", "0.3")
            process.put("use_relative_e_distances", if (relativeE) "1" else "0")
            filament.put("filament_max_volumetric_speed", "100")
            filament.put("slow_down_for_layer_cooling", "1")
            filament.put("slow_down_layer_time", "10")
            filament.put("slow_down_min_speed", "1")
            SliceBeam.genCurrentConfig(model)
            val config = SliceBeam.getCurrentConfigFile()
            val serialized = config.readText()
            assertTrue("Relative/absolute E mode must reach the real slicer config", Regex("(?m)^use_relative_e_distances\\s*=\\s*${if (relativeE) "1" else "0"}\\s*$").containsMatchIn(serialized))
            output.delete()
            val result = model.slice(config.absolutePath, output.absolutePath, null)
            val gcode = try {
                assertTrue("The cooling regression fixture must produce G-code", output.isFile && output.length() > 0L)
                output.readText()
            } finally {
                result.release()
            }

            var modalFeedrate = 0.0
            var relativeExtrusion = relativeE
            var x = 0.0
            var y = 0.0
            var e = 0.0
            val speeds = mutableListOf<Double>()
            // Match GCodeReader semantics: recognize omitted leading zero and count only positive XY extrusion.
            val parameter = Regex("([XYEF])-?(?:\\d+(?:\\.\\d*)?|\\.\\d+)")
            for (line in gcode.lineSequence()) {
                val command = line.substringBefore(';').trim()
                if (command == "M82") { relativeExtrusion = false; continue }
                if (command == "M83") { relativeExtrusion = true; continue }
                if (command.startsWith("G92 ")) {
                    parameter.findAll(command).forEach { match ->
                        if (match.groupValues[1] == "E") e = match.value.drop(1).toDouble()
                    }
                    continue
                }
                if (!command.startsWith("G0 ") && !command.startsWith("G1 ")) continue
                val words = parameter.findAll(command).associate { it.groupValues[1] to it.value.drop(1).toDouble() }
                words["F"]?.let { modalFeedrate = it }
                val nextX = words["X"] ?: x
                val nextY = words["Y"] ?: y
                val extrusionDelta = words["E"]?.let { value -> if (relativeExtrusion) value else value - e } ?: 0.0
                if (kotlin.math.hypot(nextX - x, nextY - y) > 1e-6 && extrusionDelta > 1e-6) speeds += modalFeedrate
                words["E"]?.let { e = if (relativeExtrusion) e + it else it }
                x = nextX
                y = nextY
            }
            return speeds
        }

        try {
            val absolute = extrusionSpeeds(relativeE = false)
            val relative = extrusionSpeeds(relativeE = true)
            assertTrue(
                "Both slices must contain XY extrusion moves with identical counts (absolute=${absolute.size}, relative=${relative.size})",
                absolute.isNotEmpty() && relative.size == absolute.size,
            )
            val largestDifference = absolute.zip(relative).maxOf { (left, right) -> kotlin.math.abs(left - right) }
            assertTrue(
                "Layer cooling must not change speeds in relative-E mode; largest feed-rate difference was $largestDifference mm/min",
                largestDifference <= 1.0,
            )
        } finally {
            processKeys.forEach { process.put(it, previousProcess[it]) }
            filamentKeys.forEach { filament.put(it, previousFilament[it]) }
            model.release()
            source.delete()
            output.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test
    fun lightningDistanceFieldExpandsBoundsToContainOpenedOverhangSamples() = withKobraProfile {
        val probe = Native.lightning_distance_field_bounds_probe()
        assertEquals("probe returns sample count and old/new bounds violations", 3, probe.size)
        assertTrue("the opened-overhang fixture must produce unsupported samples", probe[0] > 0)
        assertTrue("the fixture must reproduce samples beyond the original infill bounds", probe[1] > 0)
        assertEquals("the expanded Lightning bounds must contain every sample", 0, probe[2])
    }

    @Test(timeout = 120_000)
    fun supportedSparseInfillSmoothingChangesOnlyHilbertAndOctagramToolpaths() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val process = requireNotNull(SliceBeam.CONFIG?.findPrint("0.16mm Standard"))
        val source = createThinCubeFixture("sparse-infill-smooth-factor.stl", 20.0, 20.0, 12.0)
        val output = File(context.cacheDir, "sparse-infill-smooth-factor.gcode")
        val model = Model(source).also { it.ensureOnBed(0) }
        try {
            process.put("sparse_infill_density", "25")
            process.put("wall_loops", "2")
            process.put("top_shell_layers", "3")
            process.put("bottom_shell_layers", "3")
            process.put("resolution", "0.005")

            fun featureMoves(pattern: String, smoothPercent: Int): Map<String, List<String>> {
                process.put("sparse_infill_pattern", pattern)
                process.put("sparse_infill_smooth_factor", smoothPercent.toString())
                SliceBeam.genCurrentConfig(model)
                val config = SliceBeam.getCurrentConfigFile()
                val serialized = config.readText()
                assertTrue(
                    "The Orca smoothing setting must reach native slicing",
                    Regex("(?m)^sparse_infill_smooth_factor\\s*=\\s*$smoothPercent(?:\\.0+)?\\s*$")
                        .containsMatchIn(serialized),
                )
                output.delete()
                val result = model.slice(config.absolutePath, output.absolutePath, null)
                try {
                    val byFeature = linkedMapOf<String, MutableList<String>>()
                    var feature = ""
                    output.forEachLine { line ->
                        if (line.startsWith(";TYPE:")) feature = line.removePrefix(";TYPE:")
                        else if (line.startsWith("G1 ") && line.contains('E') &&
                            (line.contains(" X") || line.contains(" Y"))) {
                            byFeature.getOrPut(feature) { mutableListOf() }.add(line)
                        }
                    }
                    return byFeature.mapValues { it.value.toList() }
                } finally {
                    result.release()
                }
            }

            for (pattern in listOf("hilbertcurve", "octagramspiral")) {
                val sharp = featureMoves(pattern, 0)
                val smooth = featureMoves(pattern, 100)
                assertTrue("Both $pattern slices must contain sparse infill", (sharp["Sparse infill"]?.size ?: 0) > 0)
                assertTrue("Smoothing must change the real sliced $pattern toolpath", sharp["Sparse infill"] != smooth["Sparse infill"])
                assertTrue("Smooth corners should add flattened path segments for $pattern",
                    (smooth["Sparse infill"]?.size ?: 0) > (sharp["Sparse infill"]?.size ?: 0))
                for (feature in listOf("Outer wall", "Inner wall", "Top surface", "Bottom surface"))
                    assertEquals("Smoothing is scoped to sparse infill; $feature must remain unchanged for $pattern",
                        sharp[feature], smooth[feature])
            }
            val gridSharp = featureMoves("grid", 0)
            val gridConfigured = featureMoves("grid", 100)
            assertTrue("The control Grid slices must contain sparse infill", (gridSharp["Sparse infill"]?.size ?: 0) > 0)
            assertEquals("Unsupported patterns must ignore the smoothing option", gridSharp, gridConfigured)
        } finally {
            model.release()
            source.delete()
            output.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test(timeout = 180_000)
    fun spiralInsetSparsePatternProducesADistinctNativeToolpath() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val process = requireNotNull(SliceBeam.CONFIG?.findPrint("0.16mm Standard"))
        val source = createThinCubeFixture("spiral-inset-sparse.stl", 20.0, 20.0, 12.0)
        val output = File(context.cacheDir, "spiral-inset-sparse.gcode")
        val model = Model(source).also { it.ensureOnBed(0) }
        val keys = listOf("sparse_infill_pattern", "sparse_infill_density", "top_surface_pattern",
            "top_surface_fill_order", "wall_loops", "top_shell_layers", "bottom_shell_layers",
            "brim_type", "skirt_loops")
        val previous = keys.associateWith { process[it] }
        try {
            val optionCatalog = PrintConfigDef.getInstance().options
            for (key in listOf("sparse_infill_pattern", "top_surface_pattern", "bottom_surface_pattern",
                    "internal_solid_infill_pattern", "support_interface_pattern")) {
                val option = requireNotNull(optionCatalog[key])
                val index = requireNotNull(option.enumValues).indexOf("spiralinset")
                assertTrue("$key must expose the native Spiral Inset value", index >= 0)
                assertEquals("$key must pair Spiral Inset with its label", "Spiral Inset",
                    requireNotNull(option.enumLabels).getOrNull(index))
            }
            for (key in listOf("top_surface_fill_order", "bottom_surface_fill_order")) {
                val option = requireNotNull(optionCatalog[key])
                assertArrayEquals(arrayOf("default", "outward", "inward"), option.enumValues)
                assertArrayEquals(arrayOf("Default", "Outward", "Inward"), option.enumLabels)
            }

            process.put("sparse_infill_density", "25%")
            process.put("wall_loops", "2")
            process.put("top_shell_layers", "3")
            process.put("bottom_shell_layers", "3")
            process.put("brim_type", "no_brim")
            process.put("skirt_loops", "0")

            fun sparseMoves(pattern: String): List<String> {
                process.put("sparse_infill_pattern", pattern)
                SliceBeam.genCurrentConfig(model)
                output.delete()
                val result = model.slice(SliceBeam.getCurrentConfigFile().absolutePath, output.absolutePath, null)
                try {
                    val moves = ArrayList<String>()
                    var sparse = false
                    output.forEachLine { line ->
                        if (line.startsWith(";TYPE:")) sparse = line == ";TYPE:Sparse infill"
                        else if (sparse && line.startsWith("G1 ") && line.contains('E') &&
                            (line.contains(" X") || line.contains(" Y"))) moves += line
                    }
                    return moves
                } finally {
                    result.release()
                }
            }

            val spiral = sparseMoves("spiralinset")
            assertTrue("Spiral Inset must produce sparse infill extrusion paths", spiral.isNotEmpty())
            assertHasExtrusion(output.readText())
            val concentric = sparseMoves("concentric")
            assertTrue("The control pattern must also produce sparse infill", concentric.isNotEmpty())
            assertNotEquals("Spiral Inset must not silently fall back to Concentric", concentric, spiral)

            fun topSurfaceMoves(order: String): List<String> {
                process.put("sparse_infill_pattern", "concentric")
                process.put("top_surface_pattern", "spiralinset")
                process.put("top_surface_fill_order", order)
                SliceBeam.genCurrentConfig(requireNotNull(model))
                output.delete()
                val result = requireNotNull(model).slice(
                    SliceBeam.getCurrentConfigFile().absolutePath, output.absolutePath, null)
                try {
                    val moves = ArrayList<String>()
                    var topSurface = false
                    output.forEachLine { line ->
                        if (line.startsWith(";TYPE:")) topSurface = line == ";TYPE:Top surface"
                        else if (topSurface && line.startsWith("G1 ") && line.contains('E') &&
                            (line.contains(" X") || line.contains(" Y"))) moves += line
                    }
                    return moves
                } finally {
                    result.release()
                }
            }

            val outward = topSurfaceMoves("outward")
            val inward = topSurfaceMoves("inward")
            assertTrue("Spiral Inset must emit top surface infill", outward.isNotEmpty())
            assertNotEquals("Top surface fill order must change the generated path", outward, inward)
        } finally {
            previous.forEach { (key, value) -> if (value != null) process.put(key, value) }
            model.release()
            source.delete()
            output.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test
    fun sparseInfillArcFittingDoesNotFitWallsAndLegacyBooleanProfilesRemainCompatible() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = requireNotNull(SliceBeam.CONFIG)
        val process = requireNotNull(config.findPrint("0.16mm Standard"))
        val source = createCylinderFixture("arc-fitting-sparse-only.stl", 25.0, 10.0, 360)
        val output = File(context.cacheDir, "arc-fitting-sparse-only.gcode")
        var model: Model? = null
        try {
            val option = requireNotNull(PrintConfigDef.getInstance().options["enable_arc_fitting"])
            assertArrayEquals(arrayOf("disabled", "enabled", "sparse_infill"), option.enumValues)
            model = Model(source).also { it.ensureOnBed(0) }
            SliceBeam.genCurrentConfig(model)

            fun arcsByType(mode: String): Map<String, Int> {
                process.put("enable_arc_fitting", mode)
                process.put("sparse_infill_pattern", "concentric")
                process.put("sparse_infill_density", "20%")
                process.put("z_hop", "0")
                SliceBeam.genCurrentConfig(requireNotNull(model))
                output.delete()
                val result = requireNotNull(model).slice(
                    SliceBeam.getCurrentConfigFile().absolutePath,
                    output.absolutePath,
                    null,
                )
                try {
                    assertTrue("Slicing $mode must emit extrusion", output.isFile && output.length() > 0L)
                    val counts = mutableMapOf<String, Int>()
                    var type = ""
                    output.forEachLine { line ->
                        if (line.startsWith(";TYPE:")) type = line.removePrefix(";TYPE:")
                        else if (line.startsWith("G2 ") || line.startsWith("G3 ")) {
                            counts[type] = (counts[type] ?: 0) + 1
                        }
                    }
                    return counts
                } finally {
                    result.release()
                }
            }

            val enabled = arcsByType("enabled")
            assertTrue("Full arc fitting should fit the circular outer wall: $enabled", (enabled["Outer wall"] ?: 0) > 0)
            val sparseOnly = arcsByType("sparse_infill")
            assertTrue("Sparse-only mode should fit concentric sparse infill: $sparseOnly", (sparseOnly["Sparse infill"] ?: 0) > 0)
            assertTrue("Sparse-only mode must not fit walls or surfaces: $sparseOnly",
                sparseOnly.keys.all { it == "Sparse infill" })
            val legacyEnabled = arcsByType("1")
            assertTrue("Legacy boolean 1 must retain full arc fitting: $legacyEnabled",
                (legacyEnabled["Outer wall"] ?: 0) > 0)
            assertTrue("Legacy boolean 0 must disable arc fitting", arcsByType("0").isEmpty())
        } finally {
            model?.release()
            source.delete()
            output.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test
    fun perObjectProcessOverridesRoundTripThroughThreeMfAndChangeNativeSliceToolpaths() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val process = requireNotNull(SliceBeam.CONFIG?.findPrint("0.16mm Standard"))
        process.put("layer_height", "0.2")
        process.put("initial_layer_print_height", "0.2")
        process.put("wall_loops", "3")
        process.put("sparse_infill_density", "25%")
        process.put("top_shell_layers", "4")
        process.put("infill_wall_overlap", "0")
        val source = createThinCubeFixture("object-process-overrides.stl", 20.0, 20.0, 12.0)
        val project = File(context.cacheDir, "object-process-overrides-roundtrip.3mf")
        val output = File(context.cacheDir, "object-process-overrides.gcode")
        var baseline: Model? = null
        var edited: Model? = null
        var restored: Model? = null

        fun sliceMetrics(model: Model): Map<String, Int> {
            SliceBeam.genCurrentConfig(model)
            output.delete()
            val result = model.slice(SliceBeam.getCurrentConfigFile().absolutePath, output.absolutePath, null)
            try {
                assertTrue("The object settings must produce a real native G-code slice", output.isFile && output.length() > 0L)
                val counts = mutableMapOf<String, Int>()
                var feature = ""
                val extrusion = Regex("(?:^|\\s)E(-?\\d+(?:\\.\\d+)?)")
                output.forEachLine { line ->
                    if (line.startsWith(";TYPE:")) feature = line.removePrefix(";TYPE:").trim()
                    else if (line.startsWith("G1 ")) {
                        val amount = extrusion.find(line)?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: 0.0
                        if (amount > 0.0) counts[feature] = (counts[feature] ?: 0) + 1
                    }
                }
                return counts
            } finally {
                result.release()
            }
        }

        try {
            baseline = Model(source).also { it.ensureOnBed(0) }
            val baselineMetrics = sliceMetrics(requireNotNull(baseline))
            edited = Model(source).also { it.ensureOnBed(0) }
            val objectIndex = 0
            assertEquals(null, edited.getObjectConfigOption(objectIndex, "wall_loops"))
            assertEquals(1, edited.setObjectConfigOptions(objectIndex, mapOf(
                "wall_loops" to "1",
                "sparse_infill_density" to "0%",
                "top_shell_layers" to "1",
            )))
            assertEquals(0, edited.setObjectConfigOptions(objectIndex, mapOf(
                "wall_loops" to "1",
                "sparse_infill_density" to "0%",
                "top_shell_layers" to "1",
            )))
            assertEquals("1", edited.getObjectConfigOption(objectIndex, "wall_loops"))
            assertFalse("Unsupported settings must not be written into model objects",
                edited.setObjectConfigOptions(objectIndex, mapOf("machine_start_gcode" to "G28")) >= 0)
            SliceBeam.genCurrentConfig(requireNotNull(edited))
            edited.export3mf(SliceBeam.getCurrentConfigFile().absolutePath, project.absolutePath)
            val modelConfigXml = ZipFile(project).use { archive ->
                val entry = archive.getEntry("Metadata/Slic3r_PE_model.config")
                assertNotNull("The exported 3MF must include model configuration metadata", entry)
                archive.getInputStream(entry).bufferedReader().use { it.readText() }
            }
            assertTrue("The exported 3MF must persist wall-loop overrides on its model volumes", modelConfigXml.contains("key=\"wall_loops\""))
            restored = Model(project)
            assertEquals("1", restored.getObjectConfigOption(objectIndex, "wall_loops"))
            assertEquals("0%", restored.getObjectConfigOption(objectIndex, "sparse_infill_density"))
            assertEquals("1", restored.getObjectConfigOption(objectIndex, "top_shell_layers"))
            val overrideMetrics = sliceMetrics(requireNotNull(restored))
            assertTrue("Zero sparse infill density must remove sparse-infill extrusion; base=$baselineMetrics override=$overrideMetrics",
                (overrideMetrics["Sparse infill"] ?: 0) < (baselineMetrics["Sparse infill"] ?: 0))

            assertEquals(1, restored.setObjectConfigOptions(objectIndex, mapOf(
                "wall_loops" to "", "sparse_infill_density" to "", "top_shell_layers" to "",
            )))
            assertEquals(null, restored.getObjectConfigOption(objectIndex, "wall_loops"))
            val inheritedAgain = sliceMetrics(requireNotNull(restored))
            assertEquals("Clearing object overrides restores the profile toolpath", baselineMetrics, inheritedAgain)
        } finally {
            baseline?.release()
            edited?.release()
            restored?.release()
            source.delete()
            project.delete()
            output.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test
    fun supportOverrideIsIsolatedPerObjectAndChangesThreeMfSlice() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val process = requireNotNull(SliceBeam.CONFIG?.findPrint("0.16mm Standard"))
        process.put("enable_support", "0")
        process.put("support_type", "tree(auto)")
        process.put("support_on_build_plate_only", "1")
        val source = createSteppedOverhangFixture("object-support-override.stl")
        val project = File(context.cacheDir, "object-support-override.3mf")
        val treeProject = File(context.cacheDir, "object-support-override-tree.3mf")
        val output = File(context.cacheDir, "object-support-override.gcode")
        var model: Model? = null
        var restored: Model? = null
        var treeModel: Model? = null

        fun objectCenters(current: Model): List<Pair<Double, Double>> = (0 until current.objectsCount).map { index ->
            val min = Vec3d()
            val max = Vec3d()
            current.getBoundingBoxExact(index, min, max)
            (min.x + max.x) / 2.0 to (min.y + max.y) / 2.0
        }
        fun supportPaths(current: Model): List<GCodePathSegment> {
            SliceBeam.genCurrentConfig(current)
            output.delete()
            val result = current.slice(SliceBeam.getCurrentConfigFile().absolutePath, output.absolutePath, null)
            try {
                val gcode = output.readText()
                assertHasExtrusion(gcode)
                // Retract/prime records can contain E with the current XY position and
                // inherit the last support type marker. They are not toolpath geometry.
                return supportExtrusionSegments(gcode).filter {
                    kotlin.math.hypot(it.x1 - it.x0, it.y1 - it.y0) > 1e-6
                }
            } finally { result.release() }
        }
        fun hasSupportNear(paths: List<GCodePathSegment>, center: Pair<Double, Double>): Boolean = paths.any { path ->
            kotlin.math.hypot((path.x0 + path.x1) / 2.0 - center.first, (path.y0 + path.y1) / 2.0 - center.second) < 18.0
        }

        try {
            model = Model(source).also { it.ensureOnBed(0); it.addObject(it, 0); it.translate(1, 50.0, 0.0, 0.0); it.ensureOnBed(1) }
            assertEquals(2, model.objectsCount)
            val centers = objectCenters(model)
            assertEquals(1, model.setObjectConfigOptions(0, mapOf("enable_support" to "1", "support_type" to "normal(auto)")))
            assertEquals("1", model.getObjectConfigOption(0, "enable_support"))
            assertEquals("normal(auto)", model.getObjectConfigOption(0, "support_type"))
            assertEquals("the other object must keep inheriting the profile", null,
                model.getObjectConfigOption(1, "enable_support"))
            assertEquals("support is an object setting, not a printable-part setting", -1,
                model.setObjectPartConfigOptions(0, 0, mapOf("enable_support" to "1")))

            SliceBeam.genCurrentConfig(model)
            model.export3mf(SliceBeam.getCurrentConfigFile().absolutePath, project.absolutePath)
            val (modelConfigName, modelConfigXml) = ZipFile(project).use { zip ->
                val entry = zip.entries().asSequence().firstOrNull { it.name.endsWith("model_settings.config", ignoreCase = true) }
                    ?: zip.entries().asSequence().firstOrNull { it.name.endsWith("Slic3r_PE_model.config", ignoreCase = true) }
                entry?.name.orEmpty() to entry?.let { zip.getInputStream(it).bufferedReader().use { reader -> reader.readText() } }.orEmpty()
            }
            restored = Model(project)
            assertEquals("enable_support override should be written to the model config '$modelConfigName': $modelConfigXml", true,
                modelConfigXml.contains("key=\"enable_support\" value=\"1\""))
            assertEquals("round-trip object configs: " + (0 until restored.objectsCount).map { restored.getObjectName(it) to restored.getObjectConfigOption(it, "enable_support") },
                "1", restored.getObjectConfigOption(0, "enable_support"))
            assertEquals("normal(auto)", restored.getObjectConfigOption(0, "support_type"))
            assertEquals(null, restored.getObjectConfigOption(1, "enable_support"))
            val restoredCenters = objectCenters(restored)
            val normalSupport = supportPaths(restored)
            assertTrue("the selected overhang object must emit support G-code", hasSupportNear(normalSupport, restoredCenters[0]))
            assertFalse("the profile-disabled sibling must not emit support G-code; centers=$restoredCenters, nearby=${normalSupport.filter { kotlin.math.hypot((it.x0 + it.x1) / 2.0 - restoredCenters[1].first, (it.y0 + it.y1) / 2.0 - restoredCenters[1].second) < 18.0 }.take(8)}",
                hasSupportNear(normalSupport, restoredCenters[1]))

            // With global support enabled, the explicit per-object strategy is compared
            // against the normal-support G-code above. This avoids conflating the engine's
            // global support gate with its object-level strategy selection.
            process.put("enable_support", "1")
            assertEquals(1, restored.setObjectConfigOptions(0, mapOf("support_type" to "tree(auto)")))
            assertEquals("tree(auto)", restored.getObjectConfigOption(0, "support_type"))
            // Reopen after persisting the changed strategy so the native slicer builds
            // a fresh PrintObject rather than reusing its already-sliced support cache.
            SliceBeam.genCurrentConfig(restored)
            restored.export3mf(SliceBeam.getCurrentConfigFile().absolutePath, treeProject.absolutePath)
            treeModel = Model(treeProject)
            assertEquals("tree(auto)", treeModel.getObjectConfigOption(0, "support_type"))
            val treeSupport = supportPaths(treeModel)
            fun pathsNear(paths: List<GCodePathSegment>, center: Pair<Double, Double>) = paths.filter {
                kotlin.math.hypot((it.x0 + it.x1) / 2.0 - center.first, (it.y0 + it.y1) / 2.0 - center.second) < 18.0
            }
            val normalGeometry = pathsNear(normalSupport, restoredCenters[0])
            val treeCenters = objectCenters(treeModel)
            val treeGeometry = pathsNear(treeSupport, treeCenters[0])
            assertTrue("Tree support must remain enabled on the selected object; supportSegments=${treeSupport.size}, " +
                "centers=$treeCenters, enable=${treeModel.getObjectConfigOption(0, "enable_support")}, " +
                "type=${treeModel.getObjectConfigOption(0, "support_type")}, " +
                "profile=${SliceBeam.getCurrentConfigFile().readText().lines().filter { it.startsWith("enable_support =") || it.startsWith("support_type =") }}",
                treeGeometry.isNotEmpty())
            assertNotEquals("Normal and tree strategy overrides must change the selected object's support toolpath",
                normalGeometry, treeGeometry)
            assertTrue("global support is enabled for the strategy comparison, so both objects emit support",
                hasSupportNear(treeSupport, treeCenters[0]) && hasSupportNear(treeSupport, treeCenters[1]))

            assertEquals(1, restored.setObjectConfigOptions(1, mapOf("enable_support" to "1", "support_type" to "normal(auto)")))
            val bothSupports = supportPaths(restored)
            assertTrue("the sibling can be enabled independently", hasSupportNear(bothSupports, restoredCenters[0]) &&
                hasSupportNear(bothSupports, restoredCenters[1]))
        } finally {
            treeModel?.release(); restored?.release(); model?.release()
            source.delete(); project.delete(); treeProject.delete(); output.delete(); SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test
    fun perPartProcessOverridesRemainIsolatedAcrossThreeMfAndNativeSlice() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = createThinCubeFixture("per-part-process-overrides.stl", 20.0, 20.0, 12.0)
        val project = File(context.cacheDir, "per-part-process-overrides.3mf")
        val output = File(context.cacheDir, "per-part-process-overrides.gcode")
        val process = requireNotNull(SliceBeam.CONFIG?.findPrint("0.16mm Standard"))
        process.put("layer_height", "0.2")
        process.put("initial_layer_print_height", "0.2")
        process.put("sparse_infill_density", "25%")
        process.put("sparse_infill_pattern", "grid")
        process.put("top_shell_layers", "3")
        process.put("bottom_shell_layers", "3")
        process.put("top_surface_pattern", "monotonicline")
        process.put("bottom_surface_pattern", "monotonic")
        process.put("infill_wall_overlap", "0")
        var model: Model? = null
        var restored: Model? = null
        var surfaceRestored: Model? = null
        fun sparseInfillMoves(current: Model): Int {
            SliceBeam.genCurrentConfig(current)
            output.delete()
            val result = current.slice(SliceBeam.getCurrentConfigFile().absolutePath, output.absolutePath, null)
            try {
                var sparse = false
                var count = 0
                val extrusion = Regex("(?:^|\\s)E(-?\\d+(?:\\.\\d+)?)")
                output.forEachLine { line ->
                    if (line.startsWith(";TYPE:")) sparse = line.removePrefix(";TYPE:").trim() == "Sparse infill"
                    else if (sparse && line.startsWith("G1 ") && (extrusion.find(line)?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: 0.0) > 0.0)
                        count++
                }
                return count
            } finally { result.release() }
        }
        fun sparseInfillToolpaths(current: Model): List<String> {
            SliceBeam.genCurrentConfig(current)
            output.delete()
            val result = current.slice(SliceBeam.getCurrentConfigFile().absolutePath, output.absolutePath, null)
            try {
                val moves = mutableListOf<String>()
                val extrusion = Regex("(?:^|\\s)E(-?\\d+(?:\\.\\d+)?)")
                output.forEachLine { line ->
                    if (line.startsWith("G1 ") && (line.contains(" X") || line.contains(" Y")) &&
                        (extrusion.find(line)?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: 0.0) > 0.0)
                        moves += line.substringBefore(';').trim()
                }
                return moves
            } finally { result.release() }
        }
        fun surfaceToolpaths(current: Model): Map<String, List<String>> {
            SliceBeam.genCurrentConfig(current)
            output.delete()
            val result = current.slice(SliceBeam.getCurrentConfigFile().absolutePath, output.absolutePath, null)
            try {
                val roles = setOf("Top surface", "Bottom surface")
                val paths = roles.associateWith { mutableListOf<String>() }
                var activeRole: String? = null
                val extrusion = Regex("(?:^|\\s)E(-?(?:\\d+(?:\\.\\d*)?|\\.\\d+))")
                output.forEachLine { line ->
                    if (line.startsWith(";TYPE:")) {
                        val type = line.removePrefix(";TYPE:").trim()
                        activeRole = type.takeIf { it in roles }
                    } else if (activeRole != null && line.startsWith("G1 ") &&
                        (line.contains(" X") || line.contains(" Y")) &&
                        (extrusion.find(line)?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: 0.0) > 0.0) {
                        paths.getValue(requireNotNull(activeRole)).add(line.substringBefore(';').trim())
                    }
                }
                return paths.mapValues { it.value.toList() }
            } finally { result.release() }
        }
        try {
            model = Model(source).also { it.ensureOnBed(0); it.addObject(it, 0); it.translate(1, 25.0, 0.0, 0.0) }
            assertEquals(2, model.objectsCount)
            assertEquals(0, model.combineObjectsAsParts(intArrayOf(0, 1)))
            assertEquals(2, model.getObjectPartCount(0))
            val baseline = sparseInfillMoves(model)
            assertTrue("both printable parts should generate baseline sparse infill", baseline > 0)

            assertEquals(1, model.setObjectPartConfigOptions(0, 0, mapOf(
                "wall_loops" to "1", "sparse_infill_density" to "0%", "top_shell_layers" to "1",
            )))
            assertEquals("1", model.getObjectPartConfigOption(0, 0, "wall_loops"))
            assertEquals("0%", model.getObjectPartConfigOption(0, 0, "sparse_infill_density"))
            assertEquals("1", model.getObjectPartConfigOption(0, 0, "top_shell_layers"))
            assertEquals("the adjacent part must remain profile-inherited", null,
                model.getObjectPartConfigOption(0, 1, "sparse_infill_density"))
            assertEquals("<mixed>", model.getObjectConfigOption(0, "sparse_infill_density"))
            assertEquals("same part settings must be a no-op", 0, model.setObjectPartConfigOptions(0, 0, mapOf(
                "wall_loops" to "1", "sparse_infill_density" to "0%", "top_shell_layers" to "1",
            )))

            SliceBeam.genCurrentConfig(model)
            model.export3mf(SliceBeam.getCurrentConfigFile().absolutePath, project.absolutePath)
            restored = Model(project).also { it.ensureOnBed(0) }
            assertEquals(2, restored.getObjectPartCount(0))
            assertEquals("1", restored.getObjectPartConfigOption(0, 0, "wall_loops"))
            assertEquals("0%", restored.getObjectPartConfigOption(0, 0, "sparse_infill_density"))
            assertEquals(null, restored.getObjectPartConfigOption(0, 1, "sparse_infill_density"))
            val edited = sparseInfillMoves(restored)
            assertTrue("the one-part zero-density override must change actual slice paths; baseline=$baseline edited=$edited",
                edited < baseline)

            assertEquals("Clear the earlier density test before comparing infill paths", 1,
                restored.setObjectPartConfigOptions(0, 0, mapOf(
                    "wall_loops" to "", "sparse_infill_density" to "", "top_shell_layers" to "",
                )))
            assertEquals("1 patterned part and 1 inherited part", 1,
                restored.setObjectPartConfigOptions(0, 0, mapOf("sparse_infill_pattern" to "gyroid")))
            assertEquals("gyroid", restored.getObjectPartConfigOption(0, 0, "sparse_infill_pattern"))
            assertEquals(null, restored.getObjectPartConfigOption(0, 1, "sparse_infill_pattern"))
            SliceBeam.genCurrentConfig(restored)
            restored.export3mf(SliceBeam.getCurrentConfigFile().absolutePath, project.absolutePath)
            val patternConfigXml = ZipFile(project).use { archive ->
                val entry = archive.getEntry("Metadata/Slic3r_PE_model.config")
                assertNotNull(entry)
                archive.getInputStream(entry).bufferedReader().use { it.readText() }
            }
            assertTrue("3MF must retain the per-part Orca infill-pattern enum",
                patternConfigXml.contains("key=\"sparse_infill_pattern\"") && patternConfigXml.contains("gyroid"))
            val patternRestored = Model(project).also { it.ensureOnBed(0) }
            try {
                assertEquals("gyroid", patternRestored.getObjectPartConfigOption(0, 0, "sparse_infill_pattern"))
                assertEquals(null, patternRestored.getObjectPartConfigOption(0, 1, "sparse_infill_pattern"))
                val gridToolpaths = sparseInfillToolpaths(patternRestored.apply {
                    setObjectPartConfigOptions(0, 0, mapOf("sparse_infill_pattern" to ""))
                })
                val gyroidToolpaths = sparseInfillToolpaths(patternRestored.apply {
                    setObjectPartConfigOptions(0, 0, mapOf("sparse_infill_pattern" to "gyroid"))
                })
                assertTrue("Both patterns should generate actual extruding G-code", gridToolpaths.isNotEmpty() && gyroidToolpaths.isNotEmpty())
                assertNotEquals("The selected part's infill pattern must change real G-code toolpaths",
                    gridToolpaths, gyroidToolpaths)
            } finally {
                patternRestored.release()
            }

            val inheritedSurfacePaths = surfaceToolpaths(restored)
            assertTrue("both parts should produce top and bottom surface extrusion paths",
                inheritedSurfacePaths.getValue("Top surface").isNotEmpty() &&
                    inheritedSurfacePaths.getValue("Bottom surface").isNotEmpty())
            assertEquals("only part 0 receives the top surface pattern", 1,
                restored.setObjectPartConfigOptions(0, 0, mapOf("top_surface_pattern" to "concentric")))
            assertEquals("only part 1 receives the bottom surface pattern", 1,
                restored.setObjectPartConfigOptions(0, 1, mapOf("bottom_surface_pattern" to "hilbertcurve")))
            assertEquals("concentric", restored.getObjectPartConfigOption(0, 0, "top_surface_pattern"))
            assertEquals(null, restored.getObjectPartConfigOption(0, 1, "top_surface_pattern"))
            assertEquals("hilbertcurve", restored.getObjectPartConfigOption(0, 1, "bottom_surface_pattern"))
            assertEquals(null, restored.getObjectPartConfigOption(0, 0, "bottom_surface_pattern"))
            SliceBeam.genCurrentConfig(restored)
            restored.export3mf(SliceBeam.getCurrentConfigFile().absolutePath, project.absolutePath)
            surfaceRestored = Model(project).also { it.ensureOnBed(0) }
            assertEquals("concentric", surfaceRestored.getObjectPartConfigOption(0, 0, "top_surface_pattern"))
            assertEquals(null, surfaceRestored.getObjectPartConfigOption(0, 1, "top_surface_pattern"))
            assertEquals("hilbertcurve", surfaceRestored.getObjectPartConfigOption(0, 1, "bottom_surface_pattern"))
            assertEquals(null, surfaceRestored.getObjectPartConfigOption(0, 0, "bottom_surface_pattern"))
            val overriddenSurfacePaths = surfaceToolpaths(surfaceRestored)
            assertNotEquals("per-part top surface pattern must change real sliced paths",
                inheritedSurfacePaths.getValue("Top surface"), overriddenSurfacePaths.getValue("Top surface"))
            assertNotEquals("per-part bottom surface pattern must change real sliced paths",
                inheritedSurfacePaths.getValue("Bottom surface"), overriddenSurfacePaths.getValue("Bottom surface"))

            assertEquals(1, restored.setObjectPartConfigOptions(0, 0, mapOf(
                "wall_loops" to "", "sparse_infill_density" to "", "top_shell_layers" to "", "sparse_infill_pattern" to "",
            )))
            assertEquals(null, restored.getObjectPartConfigOption(0, 0, "wall_loops"))
            assertEquals(null, restored.getObjectPartConfigOption(0, 1, "wall_loops"))
            assertEquals(baseline, sparseInfillMoves(restored))
        } finally {
            surfaceRestored?.release(); restored?.release(); model?.release()
            source.delete(); project.delete(); output.delete(); SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test
    fun perObjectSeamPositionOverridePersistsAndChangesNativeSlice() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = createThinCubeFixture("object-seam-position.stl", 20.0, 20.0, 12.0)
        val project = File(context.cacheDir, "object-seam-position.3mf")
        val output = File(context.cacheDir, "object-seam-position.gcode")
        val process = requireNotNull(SliceBeam.CONFIG?.findPrint("0.16mm Standard"))
        process.put("layer_height", "0.2")
        process.put("initial_layer_print_height", "0.2")
        process.put("seam_position", "aligned")
        var model: Model? = null
        var restored: Model? = null
        fun perimeterMoves(current: Model): List<String> {
            SliceBeam.genCurrentConfig(current)
            output.delete()
            val result = current.slice(SliceBeam.getCurrentConfigFile().absolutePath, output.absolutePath, null)
            try {
                val moves = mutableListOf<String>()
                val extrusion = Regex("(?:^|\\s)E(-?(?:\\d+(?:\\.\\d*)?|\\.\\d+))")
                var perimeter = false
                output.forEachLine { line ->
                    if (line.startsWith(";TYPE:")) {
                        perimeter = line.removePrefix(";TYPE:").trim() in setOf("Outer wall", "Inner wall", "Overhang wall")
                    } else if (perimeter && line.startsWith("G1 ") && (line.contains(" X") || line.contains(" Y")) &&
                        (extrusion.find(line)?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: 0.0) > 0.0) {
                        moves += line.substringBefore(';').trim()
                    }
                }
                return moves
            } finally { result.release() }
        }
        try {
            model = Model(source).also { it.ensureOnBed(0); it.addObject(it, 0); it.translate(1, 25.0, 0.0, 0.0) }
            assertEquals(2, model.objectsCount)
            val aligned = perimeterMoves(model)
            assertTrue("the fixture should emit perimeter paths", aligned.isNotEmpty())

            assertEquals(1, model.setObjectConfigOptions(0, mapOf("seam_position" to "back")))
            assertEquals("back", model.getObjectConfigOption(0, "seam_position"))
            assertEquals("the sibling object should inherit the process profile", null,
                model.getObjectConfigOption(1, "seam_position"))
            SliceBeam.genCurrentConfig(model)
            model.export3mf(SliceBeam.getCurrentConfigFile().absolutePath, project.absolutePath)
            restored = Model(project).also { it.ensureOnBed(0) }
            assertEquals("back", restored.getObjectConfigOption(0, "seam_position"))
            assertEquals("the sibling should remain unmodified after the 3MF round trip", null,
                restored.getObjectConfigOption(1, "seam_position"))
            val rear = perimeterMoves(restored)
            assertTrue("the object-specific seam choice must change real perimeter G-code",
                rear.isNotEmpty() && rear != aligned)
        } finally {
            restored?.release(); model?.release(); source.delete(); project.delete(); output.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test
    fun perObjectRaftLayerOverridePersistsAndProducesRaftGcode() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = createThinCubeFixture("object-raft-layers.stl", 20.0, 20.0, 12.0)
        val project = File(context.cacheDir, "object-raft-layers.3mf")
        val output = File(context.cacheDir, "object-raft-layers.gcode")
        val process = requireNotNull(SliceBeam.CONFIG?.findPrint("0.16mm Standard"))
        process.put("layer_height", "0.2")
        process.put("initial_layer_print_height", "0.2")
        process.put("raft_layers", "0")
        var model: Model? = null
        var restored: Model? = null
        fun sliceText(current: Model): String {
            SliceBeam.genCurrentConfig(current)
            output.delete()
            val result = current.slice(SliceBeam.getCurrentConfigFile().absolutePath, output.absolutePath, null)
            try { return output.readText() } finally { result.release() }
        }
        try {
            model = Model(source).also { it.ensureOnBed(0); it.addObject(it, 0); it.translate(1, 25.0, 0.0, 0.0) }
            assertEquals(2, model.objectsCount)
            val baseline = sliceText(model)
            assertFalse("the zero-raft profile baseline should not emit support or raft paths", baseline.contains(";TYPE:Support"))
            assertEquals(1, model.setObjectConfigOptions(0, mapOf("raft_layers" to "2")))
            assertEquals("2", model.getObjectConfigOption(0, "raft_layers"))
            assertEquals("sibling object should inherit the zero-layer profile", null,
                model.getObjectConfigOption(1, "raft_layers"))
            SliceBeam.genCurrentConfig(model)
            model.export3mf(SliceBeam.getCurrentConfigFile().absolutePath, project.absolutePath)
            restored = Model(project).also { it.ensureOnBed(0) }
            assertEquals("2", restored.getObjectConfigOption(0, "raft_layers"))
            assertEquals(null, restored.getObjectConfigOption(1, "raft_layers"))

            val raftGcode = sliceText(restored)
            assertTrue("the selected object's nonzero raft override must generate support/raft extrusion paths",
                raftGcode.contains(";TYPE:Support") && raftGcode != baseline)
        } finally {
            restored?.release(); model?.release(); source.delete(); project.delete(); output.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test
    fun autoForMatchPrefersClosestColorAndSameFilamentSubtype() {
        // Same-colour sub-type preference; then exact colour wins over a sub-type match;
        // finer distance resolves a former whole-unit tie; finally a colour step outweighs id.
        assertArrayEquals(intArrayOf(1, 0, 1, 1), Native.filament_group_match_regression())
    }

    @Test
    fun flushGroupingCanTradeOneFilamentChangeToMatchLoadedSlots() {
        // The minimum-flush split separates two filaments whose combined transition costs 100 mm³.
        // A one-change candidate should let AMS matching keep both on the extruder where they are loaded.
        assertArrayEquals(intArrayOf(0, 0), Native.filament_group_flush_candidate_regression())
    }

    @Test
    fun autoForMatchPenalizesEveryFilamentAssignedToAnEmptyLoadedSlotGroup() {
        // The second split leaves only one filament unmatched and exactly maps three colors
        // to loaded slots. A constant per-group penalty incorrectly makes the first split win.
        assertArrayEquals(intArrayOf(0, 1, 1, 1), Native.filament_group_empty_loaded_slot_penalty_regression())
    }

    @Test
    fun scarfRampRemainsStableAcrossLayerVertexShiftsAndCorners() {
        assertArrayEquals(
            doubleArrayOf(
                20.0, 20.0, 5.0, 30.0,
                20.0, 20.0, 5.0, 30.0,
                20.3, 20.3, 5.0, 28.96,
                20.0, 5.29797, 3.0,
                20.3, 0.3, -1.0,
                20.0, 5.0, 30.0,
            ),
            Native.scarf_ramp_regression(),
            0.001,
        )
    }

    @Test
    fun bedCollisionVolumesParseHeightRangesAndResolveEachNozzleSpace() {
        assertArrayEquals(
            doubleArrayOf(2.0, 0.0, 10.0, 20.0, 30.0, 100.0, 1.0, 0.0, 30.0, 1.0, 1.0, 1.0, 11.0, 1.0),
            Native.bed_exclude_volumes_regression(),
            0.001,
        )
    }

    @Test
    fun legacyBedExcludeAreaRejectsMalformedPolygonsFromProductionGeometry() {
        assertArrayEquals(
            intArrayOf(1, 0, 0, 0, 1, 0, 0),
            Native.bed_exclude_area_validation_regression(),
        )
    }

    @Test
    fun perExtruderPrintableAreaIntersectionHandlesEmptyAndTouchingAreas() {
        assertArrayEquals(
            intArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1),
            Native.bed_shared_extruder_area_regression(),
        )
    }

    @Test
    fun bedCollisionVolumesConstrainArrangementOnlyForIntersectingHeightAndNozzle() {
        val actual = Native.bed_exclude_arrangement_regression()
        assertArrayEquals("blocked, Z-disjoint, nozzle-disjoint, nozzle-match: ${actual.contentToString()}",
            doubleArrayOf(0.0, 0.0, 0.0, 0.0), actual, 0.01)
    }

    @Test
    fun byObjectArrangementKeepsShortObjectsInsideBedAndSeparated() {
        val result = Native.short_object_arrangement_regression()
        assertEquals("a short object filling the bed must stay within its physical bounds: ${result.contentToString()}",
            1.0, result[0], 0.0)
        assertEquals("a rotated short object filling the bed must stay within its physical bounds: ${result.contentToString()}",
            1.0, result[6], 0.0)
        assertTrue("the crowded plate should still pack multiple models: ${result.contentToString()}", result[12] > 1.0)
        assertEquals("no arranged short object may be outside the physical bed: ${result.contentToString()}",
            0.0, result[13], 0.0)
        assertEquals("the required by-object clearance must be preserved: ${result.contentToString()}",
            0.0, result[14], 0.0)
    }

    @Test
    fun arrangementUsesAvailableBedAreaForNearBedSizeObjects() {
        val result = Native.near_bed_size_arrangement_regression()
        assertEquals("a 197 mm object with a 1 mm brim must fit on a 200 mm bed: ${result.contentToString()}",
            1.0, result[0], 0.0)
        assertEquals("the object must be placed on the physical bed", 0.0, result[1], 0.0)
        assertEquals("the packer should reduce only the unused default margin", 0.5, result[2], 0.01)
        assertEquals("the square footprint remains intact", 197.0, result[4], 0.01)
        assertEquals("the square footprint remains intact", 197.0, result[5], 0.01)
    }

    @Test(timeout = 30_000)
    fun printStatusCallbacksCanBeReplacedFromCallbackAndConcurrently() {
        val result = Native.print_status_callback_regression()
        assertEquals("the initial callback must run once", 1, result[0])
        assertEquals("the callback installed by the initial callback must run next", 1, result[1])
        assertTrue("the stress callback should run both before and during concurrent reports",
            result[2] >= 1)
        assertEquals("the callback installed after concurrent updates must remain callable", 1, result[3])
    }

    @Test
    fun presetTransferCanCreateAndClearSerializedPostProcessLists() {
        assertArrayEquals(
            "preset transfer must safely create a new script list and clear a target when the source list is empty",
            intArrayOf(1, 1),
            Native.config_apply_empty_serialized_list_regression(),
        )
    }

    @Test
    fun shortExtrusionChainsUseTheMinimumTravelOrder() {
        val segments = listOf(
            Pair(doubleArrayOf(14.0, 3.0), doubleArrayOf(6.0, 19.0)),
            Pair(doubleArrayOf(15.0, 2.0), doubleArrayOf(13.0, 4.0)),
            Pair(doubleArrayOf(0.0, 12.0), doubleArrayOf(19.0, 9.0)),
            Pair(doubleArrayOf(1.0, 18.0), doubleArrayOf(17.0, 18.0)),
        )
        val encoded = Native.shortest_path_exact_chain_regression()
        assertEquals(segments.size, encoded.size)
        assertEquals(segments.indices.toSet().size, encoded.map { it / 2 }.toSet().size)

        var x = 0.0
        var y = 0.0
        var actual = 0.0
        encoded.forEach { item ->
            val (first, second) = segments[item / 2]
            val entry = if (item % 2 == 0) first else second
            val exit = if (item % 2 == 0) second else first
            actual += kotlin.math.hypot(entry[0] - x, entry[1] - y)
            x = exit[0]
            y = exit[1]
        }

        var optimum = Double.POSITIVE_INFINITY
        fun enumerate(remaining: List<Int>, px: Double, py: Double, cost: Double) {
            if (remaining.isEmpty()) {
                optimum = minOf(optimum, cost)
                return
            }
            for (index in remaining) {
                val (first, second) = segments[index]
                val rest = remaining - index
                for ((entry, exit) in listOf(first to second, second to first)) {
                    enumerate(
                        rest, exit[0], exit[1],
                        cost + kotlin.math.hypot(entry[0] - px, entry[1] - py),
                    )
                }
            }
        }
        enumerate(segments.indices.toList(), 0.0, 0.0, 0.0)

        // A simple nearest-neighbor chain picks the closest next endpoint greedily.
        val remaining = segments.indices.toMutableSet()
        var gx = 0.0
        var gy = 0.0
        var greedy = 0.0
        while (remaining.isNotEmpty()) {
            var bestDistance = Double.POSITIVE_INFINITY
            var bestIndex = -1
            var bestReversed = false
            for (index in remaining) {
                val (first, second) = segments[index]
                for ((reversed, entry) in listOf(false to first, true to second)) {
                    val distance = kotlin.math.hypot(entry[0] - gx, entry[1] - gy)
                    if (distance < bestDistance) {
                        bestDistance = distance
                        bestIndex = index
                        bestReversed = reversed
                    }
                }
            }
            greedy += bestDistance
            val segment = segments[bestIndex]
            val exit = if (bestReversed) segment.first else segment.second
            gx = exit[0]
            gy = exit[1]
            remaining.remove(bestIndex)
        }
        assertEquals(optimum, actual, 1e-8)
        assertTrue("exact travel $actual must improve on nearest-neighbor $greedy", actual < greedy)
    }

    @Test
    fun longExtrusionChainsAreRefinedWithoutIncreasingTravel() {
        val encoded = Native.shortest_path_long_chain_refinement_regression()
        val segmentCount = 12
        assertEquals(segmentCount * 2, encoded.size)
        val segments = (0 until segmentCount).map { index ->
            Pair(doubleArrayOf(index * 10.0, 0.0), doubleArrayOf(index * 10.0 + 1.0, 0.0))
        }
        fun chainLength(order: IntArray): Double {
            assertEquals(segmentCount, order.size)
            assertEquals("each segment must appear exactly once", (0 until segmentCount).toSet(), order.map { it / 2 }.toSet())
            var x = 0.0
            var y = 0.0
            var length = 0.0
            for (item in order) {
                val (first, second) = segments[item / 2]
                val entry = if (item % 2 == 0) first else second
                val exit = if (item % 2 == 0) second else first
                length += kotlin.math.hypot(entry[0] - x, entry[1] - y)
                x = exit[0]
                y = exit[1]
            }
            return length
        }

        val greedy = encoded.copyOfRange(0, segmentCount)
        val refined = encoded.copyOfRange(segmentCount, encoded.size)
        val greedyLength = chainLength(greedy)
        val refinedLength = chainLength(refined)
        assertTrue("the long-chain local search should shorten the adversarial chain: $greedyLength -> $refinedLength",
            refinedLength < greedyLength)
        assertTrue(refined.all { it in 0 until segmentCount * 2 })
    }

    @Test
    fun defaultObjectOrderUsesTheMinimumClosedTourForSmallProjects() {
        val points = listOf(
            doubleArrayOf(3.0, 15.0), doubleArrayOf(9.0, 6.0),
            doubleArrayOf(7.0, 10.0), doubleArrayOf(10.0, 14.0),
            doubleArrayOf(19.0, 0.0), doubleArrayOf(6.0, 8.0),
        )
        val order = Native.shortest_path_exact_object_tour_regression()
        assertEquals(points.size, order.size)
        assertEquals(points.indices.toSet(), order.toSet())

        fun distance(a: Int, b: Int) = kotlin.math.hypot(
            points[a][0] - points[b][0], points[a][1] - points[b][1],
        )
        val actual = order.indices.sumOf { distance(order[it], order[(it + 1) % order.size]) }
        var optimum = Double.POSITIVE_INFINITY
        fun enumerate(path: MutableList<Int>, remaining: Set<Int>, cost: Double) {
            if (remaining.isEmpty()) {
                optimum = minOf(optimum, cost + distance(path.last(), path.first()))
                return
            }
            for (next in remaining) {
                path.add(next)
                enumerate(path, remaining - next, cost + distance(path[path.lastIndex - 1], next))
                path.removeAt(path.lastIndex)
            }
        }
        enumerate(mutableListOf(0), points.indices.drop(1).toSet(), 0.0)

        val remaining = points.indices.drop(1).toMutableSet()
        val greedyOrder = mutableListOf(0)
        while (remaining.isNotEmpty()) {
            val next = remaining.minWith(compareBy<Int> { distance(greedyOrder.last(), it) }.thenBy { it })
            greedyOrder += next
            remaining.remove(next)
        }
        val greedy = greedyOrder.indices.sumOf { distance(greedyOrder[it], greedyOrder[(it + 1) % greedyOrder.size]) }
        assertEquals(optimum, actual, 1e-8)
        assertTrue("exact tour $actual must improve on nearest-neighbor tour $greedy", actual < greedy)
    }

    @Test
    fun arachneSlicesPinchedTubeWithOnlyOneWallOnTop() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = requireNotNull(SliceBeam.CONFIG)
        val process = requireNotNull(config.findPrint("0.16mm Standard"))
        process.put("wall_generator", "arachne")
        process.put("wall_loops", "3")
        process.put("wall_sequence", "inner-outer-inner wall")
        process.put("layer_height", "0.2")
        process.put("initial_layer_print_height", "0.2")
        process.put("only_one_wall_top", "1")
        process.put("top_shell_layers", "3")
        process.put("top_surface_density", "100%")
        process.put("top_surface_expansion", "0")
        val source = copyAsset(context, "models/orca-arachne-pinched-tube.stl", "arachne-pinched-tube.stl")
        val output = File(context.cacheDir, "arachne-pinched-tube.gcode")
        var model: Model? = null
        var result: GCodeProcessorResult? = null
        try {
            model = Model(source)
            model.ensureOnBed(0)
            SliceBeam.genCurrentConfig(model)
            val nativeConfig = SliceBeam.getCurrentConfigFile().readText()
            assertTrue(nativeConfig.contains("wall_generator = arachne"))
            assertTrue(nativeConfig.contains("only_one_wall_top = 1"))
            result = model.slice(SliceBeam.getCurrentConfigFile().absolutePath, output.absolutePath, null)
            val gcode = output.readText()
            assertTrue("Arachne must produce perimeter paths", gcode.contains("TYPE:Outer wall") && gcode.contains("TYPE:Inner wall"))
            var layerIndex = -1
            val pathsByLayer = mutableMapOf<Int, MutableList<String>>()
            gcode.lineSequence().forEach { line ->
                if (line.startsWith(";LAYER_CHANGE")) ++layerIndex
                if (line.startsWith(";TYPE:"))
                    pathsByLayer.getOrPut(layerIndex) { mutableListOf() } += line.removePrefix(";TYPE:")
            }
            assertTrue("The model should contain at least three sliced layers", pathsByLayer.size >= 3)
            val representativeLayer = pathsByLayer[1].orEmpty()
            assertTrue("The pinched tube must keep outer walls in the toolpath", representativeLayer.contains("Outer wall"))
            assertTrue("The pinched tube must keep inner walls in the toolpath", representativeLayer.contains("Inner wall"))
        } finally {
            result?.release()
            model?.release()
            source.delete()
            output.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test
    fun orcaOpenEyePeacockV2ProfilesUseMoonrakerAndSlice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val application = context.applicationContext as Application
        SliceBeam.INSTANCE = application as SliceBeam
        Prefs.init(application)
        val previousConfig = SliceBeam.CONFIG
        val source = copyCube(context, "openeye-peacock-v2-cube.stl")
        val output = File(context.cacheDir, "openeye-peacock-v2-cube.gcode")
        var model: Model? = null
        try {
            val config = context.assets.open("orca_profiles/OpenEYE.ini").use { Slic3rConfigWrapper(it) }
            SliceBeam.CONFIG = config
            val printers = config.printerConfigs.filter { it.title?.startsWith("OpenEYE Peacock V2") == true }
            assertEquals(4, printers.size)
            for (printer in printers) {
                assertEquals("moonraker", printer["host_type"])
                assertTrue(SlicePreparationTools.isSendTarget(printer["host_type"], "printer.local"))
            }
            val printer = requireNotNull(config.findPrinter("OpenEYE Peacock V2 0.4 nozzle"))
            val process = requireNotNull(config.findPrint("0.16mm Standard"))
            val openEyeProcesses = config.printConfigs.filter { it.title?.startsWith("0.") == true }
            assertEquals(13, openEyeProcesses.size)
            for (openEyeProcess in openEyeProcesses) {
                assertEquals("none", openEyeProcess["fuzzy_skin"])
                assertEquals("6000", openEyeProcess["initial_layer_travel_acceleration"])
                assertEquals("0", openEyeProcess["seam_slope_conditional"])
                assertEquals("0", openEyeProcess["seam_slope_inner_walls"])
                assertEquals("10", openEyeProcess["seam_slope_min_length"])
                assertEquals("10%", openEyeProcess["seam_slope_start_height"])
                assertEquals("0.15", openEyeProcess["support_ironing_spacing"])
                assertTrue(requireNotNull(openEyeProcess["filename_format"]).contains("{printing_filament_types}"))
            }
            val filament = requireNotNull(config.findFilament("Generic PLA"))
            config.presets = ConfigObject("presets").apply {
                put("printer", printer.title)
                put("print", process.title)
                put("filament", filament.title)
            }
            val activeModel = Model(source)
            model = activeModel
            activeModel.ensureOnBed(0)
            SliceBeam.genCurrentConfig(activeModel)
            val generatedConfig = SliceBeam.getCurrentConfigFile().readText()
            assertTrue(generatedConfig.contains("initial_layer_travel_acceleration = 6000"))
            assertTrue(generatedConfig.contains("seam_slope_conditional = 0"))
            assertTrue(generatedConfig.contains("support_ironing_spacing = 0.15"))
            val sliceResult = activeModel.slice(
                SliceBeam.getCurrentConfigFile().absolutePath, output.absolutePath, null
            )
            try {
                val gcode = output.readText()
                assertHasExtrusion(gcode)
                assertTrue("The OpenEYE profile must retain its configured start macro", gcode.contains("START_PRINT"))
                val recommendedName = sliceResult.getRecommendedName()
                assertTrue("The recommended filename must begin with the imported model name: $recommendedName",
                    recommendedName.startsWith("openeye-peacock-v2-cube"))
                assertTrue("The recommended filename must include material and print time: $recommendedName",
                    recommendedName.contains("_PLA_") && recommendedName.endsWith(".gcode"))
                assertTrue("The recommended filename must include a non-empty print-time value: $recommendedName",
                    recommendedName.substringAfter("_PLA_").removeSuffix(".gcode").isNotBlank())
                assertFalse("The recommended name must not contain unresolved placeholders: $recommendedName",
                    recommendedName.contains('{') || recommendedName.contains('}'))
            } finally {
                sliceResult.release()
            }
        } finally {
            model?.release()
            source.delete()
            output.delete()
            SliceBeam.getCurrentConfigFile().delete()
            SliceBeam.CONFIG = previousConfig
        }
    }

    @Test
    fun orcaPrusaXlNozzleProfilesUseValidDeretractionSpeedAndSlice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val application = context.applicationContext as Application
        SliceBeam.INSTANCE = application as SliceBeam
        Prefs.init(application)
        val previousConfig = SliceBeam.CONFIG
        val source = copyCube(context, "prusa-xl-deretraction-speed.stl")
        val output = File(context.cacheDir, "prusa-xl-deretraction-speed.gcode")
        var model: Model? = null
        try {
            val config = context.assets.open("orca_profiles/Prusa.ini").use { Slic3rConfigWrapper(it) }
            SliceBeam.CONFIG = config
            val expectedPrinters = listOf("0.25", "0.3", "0.4", "0.5", "0.6", "0.8")
                .flatMap { nozzle -> listOf("Prusa XL $nozzle nozzle", "Prusa XL 5T $nozzle nozzle") }
            assertEquals(12, expectedPrinters.size)
            for (printerName in expectedPrinters) {
                val printer = requireNotNull(config.findPrinter(printerName))
                val expectedSpeed = if (printerName.contains("0.8 nozzle")) "15" else "25"
                assertEquals("$printerName must use Orca's valid deretraction key value", expectedSpeed, printer["deretract_speed"])
            }

            val printer = requireNotNull(config.findPrinter("Prusa XL 0.25 nozzle"))
            val process = requireNotNull(config.findPrint("0.16mm Standard"))
            val filament = requireNotNull(config.findFilament("Prusa Generic PLA"))
            // The current native validator requires the reset in layer_gcode; this test focuses on
            // the machine-key migration and supplies that required reset without changing the preset.
            printer.put("layer_gcode", "G92 E0")
            config.presets = ConfigObject("presets").apply {
                put("printer", printer.title)
                put("print", process.title)
                put("filament", filament.title)
            }
            model = Model(source).also { it.ensureOnBed(0) }
            SliceBeam.genCurrentConfig(model!!)
            val generatedConfig = SliceBeam.getCurrentConfigFile().readText()
            assertTrue("The corrected machine value must reach the effective native slice config",
                Regex("(?m)^deretraction_speed\\s*=\\s*25\\s*$").containsMatchIn(generatedConfig))
            val slice = model!!.slice(SliceBeam.getCurrentConfigFile().absolutePath, output.absolutePath, null)
            try {
                assertHasExtrusion(output.readText())
            } finally {
                slice.release()
            }
        } finally {
            SliceBeam.CONFIG = previousConfig
            model?.release()
            source.delete()
            output.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test
    fun orca3DFuelMaterialLibraryLoadsIndependentlyAndSlicesRepresentativeFilaments() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = requireNotNull(SliceBeam.CONFIG)
        val library = context.assets.open("orca_profiles/3D-Fuel.ini").use { Slic3rConfigWrapper(it) }
        assertEquals("3D-Fuel", library.vendor?.get("name"))
        assertEquals("1", library.vendor?.get("filament_library"))
        assertTrue("The library must not add a pretend printer", library.printerModels.isEmpty())
        assertEquals(13, library.filamentConfigs.size)
        assertTrue("3D-Fuel profiles should not contain unknown-option warnings", library.loadWarnings.isEmpty())
        assertTrue("The ESD profile should use Orca's branded display name",
            library.filamentConfigs.any { it.title == "3D-Fuel Pro PCTG ESD" })
        assertFalse("The previous unbranded ESD profile name should not remain",
            library.filamentConfigs.any { it.title == "3D-Fuel Pro PCTG-ESD" })
        config.filamentConfigs.addAll(library.filamentConfigs)

        val source = copyCube(context, "3d-fuel-profile-cube.stl")
        val output = File(context.cacheDir, "3d-fuel-profile-cube.gcode")
        var model: Model? = null
        try {
            val printer = requireNotNull(config.findPrinter("Anycubic Kobra 3 0.4 nozzle"))
            val process = requireNotNull(config.findPrint("0.16mm Standard"))
            val activeModel = Model(source)
            model = activeModel
            activeModel.ensureOnBed(0)
            val representatives = listOf(
                "3D-Fuel Standard PLA" to "230",
                "3D-Fuel Pro PETG" to "255",
                "3D-Fuel PET CF" to "255",
                "3D-Fuel Pro PCTG ESD" to "270",
            )
            for ((profileName, expectedTemperature) in representatives) {
                val filament = requireNotNull(config.findFilament(profileName))
                assertEquals("3D-Fuel", filament["filament_vendor"])
                assertEquals(expectedTemperature, filament["first_layer_temperature"])
                config.presets = ConfigObject("presets").apply {
                    put("printer", printer.title)
                    put("print", process.title)
                    put("filament", filament.title)
                }
                SliceBeam.genCurrentConfig(activeModel)
                output.delete()
                val sliceResult = activeModel.slice(
                    SliceBeam.getCurrentConfigFile().absolutePath, output.absolutePath, null
                )
                try {
                    val gcode = output.readText()
                    assertHasExtrusion(gcode)
                    val expectedHeatCommand = Regex(
                        "G9111 .*extruderTemp=$expectedTemperature(?:\\.0+)?(?:\\s|$)"
                    )
                    val heatLines = gcode.lineSequence().filter {
                        it.contains("G9111") || it.startsWith("M104") || it.startsWith("M109")
                    }.toList()
                    assertTrue(
                        "$profileName temperature must reach the printer's actual G9111 start macro; heat lines=$heatLines",
                        gcode.lineSequence().any(expectedHeatCommand::containsMatchIn),
                    )
                } finally {
                    sliceResult.release()
                }
            }
        } finally {
            model?.release()
            source.delete()
            output.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test
    fun orcaPrintaLotPlaLibraryLoadsIndependentlyAndSlices() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = requireNotNull(SliceBeam.CONFIG)
        val library = context.assets.open("orca_profiles/PrintaLot.ini").use { Slic3rConfigWrapper(it) }
        assertEquals("PrintaLot", library.vendor?.get("name"))
        assertEquals("1", library.vendor?.get("filament_library"))
        assertTrue("A material library must not add a placeholder printer", library.printerModels.isEmpty())
        assertEquals("The PrintaLot material library must retain both PLA and PETG", 2, library.filamentConfigs.size)
        assertTrue("PrintaLot's profile must parse without unsupported settings: ${library.loadWarnings}", library.loadWarnings.isEmpty())
        config.filamentConfigs.addAll(library.filamentConfigs)

        val filament = requireNotNull(config.findFilament("PrintaLot PLA"))
        assertEquals("PrintaLot", filament["filament_vendor"])
        assertEquals("PLA", filament["filament_type"])
        assertEquals("220", filament["first_layer_temperature"])
        assertEquals("19", filament["filament_max_volumetric_speed"])
        val printer = requireNotNull(config.findPrinter("Anycubic Kobra 3 0.4 nozzle"))
        val process = requireNotNull(config.findPrint("0.16mm Standard"))
        config.presets = ConfigObject("presets").apply {
            put("printer", printer.title)
            put("print", process.title)
            put("filament", filament.title)
        }

        val source = copyCube(context, "printalot-pla-library-cube.stl")
        val output = File(context.cacheDir, "printalot-pla-library-cube.gcode")
        var model: Model? = null
        try {
            val activeModel = Model(source)
            model = activeModel
            activeModel.ensureOnBed(0)
            SliceBeam.genCurrentConfig(activeModel)
            val sliceResult = activeModel.slice(
                SliceBeam.getCurrentConfigFile().absolutePath, output.absolutePath, null
            )
            try {
                val gcode = output.readText()
                assertHasExtrusion(gcode)
                assertTrue("PrintaLot PLA's 220 C first-layer temperature reaches the printer start macro",
                    Regex("G9111 .*extruderTemp=220(?:\\.0+)?(?:\\s|$)").containsMatchIn(gcode))
            } finally {
                sliceResult.release()
            }
        } finally {
            model?.release()
            source.delete()
            output.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test
    fun orcaPrintaLotPetgLibraryLoadsIndependentlyAndSlices() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = requireNotNull(SliceBeam.CONFIG)
        val library = context.assets.open("orca_profiles/PrintaLot.ini").use { Slic3rConfigWrapper(it) }
        assertEquals("PrintaLot", library.vendor?.get("name"))
        assertEquals("02.04.00.25", library.vendor?.get("version"))
        assertTrue("The material library must not create a printer", library.printerModels.isEmpty())
        assertEquals(2, library.filamentConfigs.size)
        assertTrue("PrintaLot profiles must load without unknown options: ${library.loadWarnings}", library.loadWarnings.isEmpty())
        config.filamentConfigs.addAll(library.filamentConfigs)

        val filament = requireNotNull(config.findFilament("PrintaLot PETG"))
        assertEquals("PrintaLot", filament["filament_vendor"])
        assertEquals("PETG", filament["filament_type"])
        assertEquals("0.95", filament["filament_flow_ratio"])
        assertEquals("17", filament["filament_max_volumetric_speed"])
        assertEquals("255", filament["first_layer_temperature"])
        val printer = requireNotNull(config.findPrinter("Anycubic Kobra 3 0.4 nozzle"))
        val process = requireNotNull(config.findPrint("0.16mm Standard"))
        config.presets = ConfigObject("presets").apply {
            put("printer", printer.title)
            put("print", process.title)
            put("filament", filament.title)
        }

        val source = copyCube(context, "printalot-petg-library-cube.stl")
        val output = File(context.cacheDir, "printalot-petg-library-cube.gcode")
        val model = Model(source)
        try {
            model.ensureOnBed(0)
            SliceBeam.genCurrentConfig(model)
            val result = model.slice(SliceBeam.getCurrentConfigFile().absolutePath, output.absolutePath, null)
            try {
                val gcode = output.readText()
                assertHasExtrusion(gcode)
                assertTrue("PrintaLot PETG's 255 C first-layer temperature must reach the printer start macro",
                    Regex("G9111 .*extruderTemp=255(?:\\.0+)?(?:\\s|$)").containsMatchIn(gcode))
            } finally {
                result.release()
            }
        } finally {
            model.release()
            source.delete()
            output.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test
    fun orcaRecycledFilamentLibrariesLoadAndReachRealSlices() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = requireNotNull(SliceBeam.CONFIG)
        val libraries = listOf(
            "EverLoop.ini" to "EverLoop",
            "Recycling Fabrik.ini" to "Recycling Fabrik",
        )
        for ((file, vendorName) in libraries) {
            val library = context.assets.open("orca_profiles/$file").use { Slic3rConfigWrapper(it) }
            assertEquals(vendorName, library.vendor?.get("name"))
            assertEquals("02.04.00.25", library.vendor?.get("version"))
            assertEquals("1", library.vendor?.get("filament_library"))
            assertTrue("A material library must not add a printer", library.printerModels.isEmpty())
            assertTrue("$vendorName profiles must parse without unsupported settings: ${library.loadWarnings}",
                library.loadWarnings.isEmpty())
            config.filamentConfigs.addAll(library.filamentConfigs)
        }
        assertEquals(
            setOf("EverLoop rPETG Premium", "EverLoop rPLA Premium", "EverLoop rPLA Premium Matte",
                "Recycling Fabrik rABS", "Recycling Fabrik rPETG", "Recycling Fabrik rPLA"),
            listOf(
                "EverLoop rPETG Premium", "EverLoop rPLA Premium", "EverLoop rPLA Premium Matte",
                "Recycling Fabrik rABS", "Recycling Fabrik rPETG", "Recycling Fabrik rPLA",
            ).filter { config.findFilament(it) != null }.toSet(),
        )

        val printer = requireNotNull(config.findPrinter("Anycubic Kobra 3 0.4 nozzle"))
        val process = requireNotNull(config.findPrint("0.16mm Standard"))
        val source = copyCube(context, "recycled-filament-library-cube.stl")
        val model = Model(source)
        try {
            model.ensureOnBed(0)
            for ((profileName, expectedType, expectedTemperature) in listOf(
                Triple("EverLoop rPETG Premium", "PETG", 245),
                Triple("Recycling Fabrik rPLA", "PLA", 220),
            )) {
                val filament = requireNotNull(config.findFilament(profileName))
                assertEquals(expectedType, filament["filament_type"])
                assertEquals(expectedTemperature.toString(), filament["first_layer_temperature"])
                config.presets = ConfigObject("presets").apply {
                    put("printer", printer.title)
                    put("print", process.title)
                    put("filament", filament.title)
                }
                SliceBeam.genCurrentConfig(model)
                val output = File(context.cacheDir, "${profileName.replace(' ', '-')}.gcode")
                try {
                    val result = model.slice(SliceBeam.getCurrentConfigFile().absolutePath, output.absolutePath, null)
                    try {
                        val gcode = output.readText()
                        assertHasExtrusion(gcode)
                        assertTrue("$profileName first-layer temperature must reach the printer start macro",
                            Regex("G9111 .*extruderTemp=$expectedTemperature(?:\\.0+)?(?:\\s|$)")
                                .containsMatchIn(gcode))
                    } finally {
                        result.release()
                    }
                } finally {
                    output.delete()
                    SliceBeam.getCurrentConfigFile().delete()
                }
            }
        } finally {
            model.release()
            source.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test
    fun orcaBlocksRf50SixTenthNozzleKeepsPressureAdvanceAsExecutableGcode() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val application = context.applicationContext as Application
        SliceBeam.INSTANCE = application as SliceBeam
        Prefs.init(application)
        val previousConfig = SliceBeam.CONFIG
        val source = copyCube(context, "blocks-rf50-06-profile-cube.stl")
        val output = File(context.cacheDir, "blocks-rf50-06-profile-cube.gcode")
        var model: Model? = null
        try {
            context.assets.open("orca_profiles/Blocks.ini").use {
                SliceBeam.CONFIG = Slic3rConfigWrapper(it)
            }
            val config = requireNotNull(SliceBeam.CONFIG)
            val printer = requireNotNull(config.findPrinter("BLOCKS RF50 0.6 nozzle"))
            val process = requireNotNull(config.findPrint("0.26mm Standard 0.6 nozzle"))
            val filament = requireNotNull(config.findFilament("Blocks Generic PLA"))
            val profileStart = printer["machine_start_gcode"].orEmpty()
            assertTrue(profileStart.contains("PRINT_START EXTRUDER="))
            assertTrue(profileStart.contains("\nSET_PRESSURE_ADVANCE ADVANCE=0.046"))
            assertFalse(profileStart.contains(";SET_PRESSURE_ADVANCE"))
            config.presets = ConfigObject("presets").apply {
                put("printer", printer.title)
                put("print", process.title)
                put("filament", filament.title)
            }

            val activeModel = Model(source)
            model = activeModel
            activeModel.ensureOnBed(0)
            val effectiveStart = requireNotNull(
                SliceBeam.buildCurrentConfigObject(activeModel.requiredFilamentCount)["machine_start_gcode"]
            )
            assertTrue(effectiveStart.contains("\nSET_PRESSURE_ADVANCE ADVANCE=0.046"))
            SliceBeam.genCurrentConfig(activeModel)
            val sliceResult = activeModel.slice(
                SliceBeam.getCurrentConfigFile().absolutePath, output.absolutePath, null
            )
            try {
                val gcode = output.readText()
                assertHasExtrusion(gcode)
                assertTrue("Pressure advance must be emitted as an executable G-code line",
                    gcode.lineSequence().any { it.trim() == "SET_PRESSURE_ADVANCE ADVANCE=0.046" })
                assertFalse("Pressure advance must not be swallowed by the preceding comment",
                    gcode.lineSequence().any { it.contains(";SET_PRESSURE_ADVANCE ADVANCE=0.046") })
            } finally {
                sliceResult.release()
            }
            Log.i("NativeSliceDeviceTest", "PASS Orca Blocks RF50 0.6 nozzle start G-code")
        } finally {
            model?.release()
            source.delete()
            output.delete()
            SliceBeam.getCurrentConfigFile().delete()
            SliceBeam.CONFIG = previousConfig
        }
    }

    @Test
    fun orca25BenFab450NozzleProfilesLoadAndSliceOnTheirConfiguredBed() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val application = context.applicationContext as Application
        SliceBeam.INSTANCE = application as SliceBeam
        Prefs.init(application)
        val previousConfig = SliceBeam.CONFIG
        val source = copyCube(context, "benfab450-profile-cube.stl")
        val output = File(context.cacheDir, "benfab450-profile-cube.gcode")
        var model: Model? = null
        try {
            context.assets.open("orca_profiles/BenFab.ini").use {
                SliceBeam.CONFIG = Slic3rConfigWrapper(it)
            }
            val config = requireNotNull(SliceBeam.CONFIG)
            val modelProfile = config.printerModels.single { it.title == "BenFab 450" }
            assertEquals("0.4;0.6;0.8;1.0", modelProfile["nozzle_diameter"])
            assertTrue(modelProfile["default_materials"].orEmpty().contains("Generic PLA @System"))

            val expectedNozzles = mapOf("0.4" to "0.20mm Standard", "0.6" to "0.30mm Standard",
                "0.8" to "0.40mm Standard", "1.0" to "0.50mm Standard")
            for ((nozzle, processName) in expectedNozzles) {
                val printer = requireNotNull(config.findPrinterVariant("BenFab 450", nozzle))
                assertEquals("480", printer["printable_height"])
                assertTrue("$nozzle nozzle must retain the Orca bed polygon",
                    printer["printable_area"].orEmpty().contains("426x426"))
                assertEquals("$processName @BenFab 450 $nozzle nozzle", printer["default_print_profile"])
                assertTrue("The default process for $nozzle must be available",
                    config.findPrint(printer["default_print_profile"]) != null)
            }

            // System materials are bundled under BBL and shared by vendor profiles in Orca.
            context.assets.open("orca_profiles/BBL.ini").use { bbl ->
                val materials = Slic3rConfigWrapper(bbl)
                config.filamentConfigs.addAll(materials.filamentConfigs.filter {
                    it.title == "Generic PLA"
                })
            }
            val filament = requireNotNull(config.findFilament("Generic PLA"))
            val activeModel = Model(source)
            model = activeModel
            activeModel.ensureOnBed(0)
            for ((nozzle, _) in expectedNozzles) {
                val printer = requireNotNull(config.findPrinter("BenFab 450 $nozzle nozzle"))
                val process = requireNotNull(config.findPrint(printer["default_print_profile"]))
                assertTrue("$nozzle nozzle must inherit the Orca Klipper start sequence",
                    printer["machine_start_gcode"].orEmpty().contains("G28 ;home"))
                config.presets = ConfigObject("presets").apply {
                    put("printer", printer.title)
                    put("print", process.title)
                    put("filament", filament.title)
                }

                val expectedStartCode = requireNotNull(
                    SliceBeam.buildCurrentConfigObject(activeModel.requiredFilamentCount)["machine_start_gcode"]
                )
                assertTrue("The active $nozzle mm slice config must use BenFab start code",
                    expectedStartCode.contains("G28 ;home"))
                SliceBeam.genCurrentConfig(activeModel)
                output.delete()
                val sliceResult = activeModel.slice(
                    SliceBeam.getCurrentConfigFile().absolutePath, output.absolutePath, null
                )
                try {
                    val gcode = output.readText()
                    assertHasExtrusion(gcode)
                    assertTrue("The $nozzle mm G-code must contain the selected BenFab start sequence",
                        gcode.contains("G28 ;home"))
                } finally {
                    sliceResult.release()
                }
                Log.i("NativeSliceDeviceTest", "PASS Orca 2.5 BenFab 450 $nozzle mm slice bytes=${output.length()}")
            }
        } finally {
            model?.release()
            source.delete()
            output.delete()
            SliceBeam.getCurrentConfigFile().delete()
            SliceBeam.CONFIG = previousConfig
        }
    }

    @Test
    fun flashforgeBundledNozzleDefaultsResolveAndCreator5ProChamberControlIsAvailable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val previousConfig = SliceBeam.CONFIG
        try {
            context.assets.open("orca_profiles/Flashforge.ini").use {
                SliceBeam.CONFIG = Slic3rConfigWrapper(it)
            }
            val config = requireNotNull(SliceBeam.CONFIG)
            val expectedDefaults = mapOf(
                "Flashforge AD5X 0.25 nozzle" to "0.12mm Standard @FF AD5X 0.25 nozzle",
                "Flashforge AD5X 0.4 nozzle" to "0.20mm Standard @FF AD5X",
                "Flashforge AD5X 0.6 nozzle" to "0.30mm Standard @FF AD5X 0.6 nozzle",
                "Flashforge AD5X 0.8 nozzle" to "0.40mm Standard @FF AD5X 0.8 nozzle",
                "Flashforge Adventurer 3 Series 0.6 Nozzle" to "0.30mm Standard @Flashforge AD3 0.6 Nozzle",
                "Flashforge Creator 5 0.4 nozzle" to "0.20mm Standard @FF C5",
                "Flashforge Creator 5 0.6 nozzle" to "0.30mm Standard @FF C5 0.6 nozzle",
                "Flashforge Creator 5 0.8 nozzle" to "0.4mm Standard @FF C5 0.8 nozzle",
                "Flashforge Creator 5 Pro 0.4 nozzle" to "0.20mm Standard @FF C5",
                "Flashforge Creator 5 Pro 0.6 nozzle" to "0.30mm Standard @FF C5 0.6 nozzle",
                "Flashforge Creator 5 Pro 0.8 nozzle" to "0.4mm Standard @FF C5 0.8 nozzle",
                "Flashforge Guider4 0.25 nozzle" to "0.12mm Standard @FF G4 0.25 nozzle",
                "Flashforge Guider4 0.4 HF nozzle" to "0.20mm Standard @FF G4 HF",
                "Flashforge Guider4 0.4 nozzle" to "0.20mm Standard @FF G4",
                "Flashforge Guider4 0.6 HF nozzle" to "0.3mm Standard @FF G4 0.6 HF nozzle",
                "Flashforge Guider4 0.6 nozzle" to "0.30mm Standard @FF G4 0.6 nozzle",
                "Flashforge Guider4 0.8 HF nozzle" to "0.40mm Standard @FF G4 0.8 HF nozzle",
                "Flashforge Guider4 Pro 0.25 nozzle" to "0.12mm Standard @FF G4P 0.25 nozzle",
                "Flashforge Guider4 Pro 0.4 HF nozzle" to "0.20mm Standard @FF G4P HF",
                "Flashforge Guider4 Pro 0.4 nozzle" to "0.20mm Standard @FF G4P",
                "Flashforge Guider4 Pro 0.6 HF nozzle" to "0.3mm Standard @FF G4P 0.6 HF nozzle",
                "Flashforge Guider4 Pro 0.6 nozzle" to "0.30mm Standard @FF G4P 0.6 nozzle",
                "Flashforge Guider4 Pro 0.8 HF nozzle" to "0.40mm Standard @FF G4P 0.8 HF nozzle"
            )
            val supportedNames = expectedDefaults.keys
            val printers = config.printerConfigs.filter { it.title in supportedNames }
            assertEquals("All bundled printer variants must be present", supportedNames.size, printers.size)
            for (printer in printers) {
                val expectedProcess = expectedDefaults.getValue(printer.title!!)
                assertEquals("${printer.title} must use its Orca 2.5 nozzle-matched default",
                    expectedProcess, printer["default_print_profile"])
                val process = requireNotNull(config.findPrint(expectedProcess))
                assertTrue("$expectedProcess must be compatible with ${printer.title}",
                    process["compatible_printers"].orEmpty().split(';').contains(printer.title))
            }

            val creator5Pro = printers.filter { it.title.orEmpty().startsWith("Flashforge Creator 5 Pro ") }
            assertEquals(3, creator5Pro.size)
            assertTrue("Creator 5 Pro must leave chamber temperature available to generated start G-code",
                creator5Pro.all { !it["start_gcode"].orEmpty().contains("M191 S0") })
            assertTrue("Every Creator 5 Pro nozzle must expose chamber control",
                creator5Pro.all { it["support_chamber_temp_control"] == "1" })
            val creator5EightTenthWideProcess = requireNotNull(config.findPrint("0.32mm Standard @FF C5 0.8 nozzle"))
            val compatibleWithWideNozzles = creator5EightTenthWideProcess["compatible_printers"].orEmpty().split(';')
            assertEquals(setOf("Flashforge Creator 5 0.8 nozzle", "Flashforge Creator 5 Pro 0.8 nozzle"),
                compatibleWithWideNozzles.toSet())
        } finally {
            SliceBeam.CONFIG = previousConfig
        }
    }

    @Test
    fun sovolSv08BundledDefaultsResolveForFourAndEightTenthNozzles() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val previousConfig = SliceBeam.CONFIG
        try {
            context.assets.open("orca_profiles/Sovol.ini").use {
                SliceBeam.CONFIG = Slic3rConfigWrapper(it)
            }
            val config = requireNotNull(SliceBeam.CONFIG)
            val expectedDefaults = mapOf(
                "0.4" to ("0.20mm Standard @Sovol SV08 0.4 nozzle" to "0.4"),
                "0.8" to ("0.40mm Standard @Sovol SV08 0.8 nozzle" to "0.8")
            )
            for ((nozzle, expected) in expectedDefaults) {
                val printer = requireNotNull(config.findPrinterVariant("Sovol SV08", nozzle))
                val expectedProcess = expected.first
                assertEquals("SV08 $nozzle mm printer selects its nozzle-matched process",
                    expectedProcess, printer["default_print_profile"])
                val process = requireNotNull(config.findPrint(expectedProcess))
                assertEquals("SV08 $nozzle mm process uses the matching line width", expected.second,
                    process["line_width"])
                assertTrue("SV08 process is only offered for its matching nozzle variant",
                    process["compatible_printers"].orEmpty().split(';').contains(printer.title))
            }
        } finally {
            SliceBeam.CONFIG = previousConfig
        }
    }

    @Test
    fun sovolSv08MaxNewNozzleVariantsLoadAndProduceSlices() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val application = context.applicationContext as Application
        SliceBeam.INSTANCE = application as SliceBeam
        Prefs.init(application)
        val previousConfig = SliceBeam.CONFIG
        val source = copyCube(context, "blocks-rf50-06-profile-cube.stl")
        val output = File(context.cacheDir, "sovol-sv08-max-nozzle-test.gcode")
        var model: Model? = null
        try {
            context.assets.open("orca_profiles/Sovol.ini").use {
                SliceBeam.CONFIG = Slic3rConfigWrapper(it)
            }
            val config = requireNotNull(SliceBeam.CONFIG)
            val modelProfile = config.printerModels.single { it.title == "Sovol SV08 MAX" }
            assertEquals("0.2;0.4;0.6;0.8;1.0", modelProfile["variants"])
            context.assets.open("orca_profiles/BBL.ini").use { input ->
                val bbl = Slic3rConfigWrapper(input)
                config.filamentConfigs.addAll(bbl.filamentConfigs.filter { it.title == "Generic PLA" })
            }
            val filament = requireNotNull(config.findFilament("Generic PLA"))
            val cases = listOf(
                Triple("0.2", "0.10mm Standard @Sovol SV08 MAX 0.2 nozzle", "0.22"),
                Triple("1.0", "0.50mm Standard @Sovol SV08 MAX 1.0 nozzle", "1.1")
            )
            for ((nozzle, processName, expectedLayerWidth) in cases) {
                val printer = requireNotNull(config.findPrinterVariant("Sovol SV08 MAX", nozzle))
                val process = requireNotNull(config.findPrint(processName))
                assertEquals(nozzle, printer["nozzle_diameter"])
                assertEquals(expectedLayerWidth, process["line_width"])
                assertTrue(process["compatible_printers"].orEmpty().contains(printer.title.orEmpty()))
                config.presets = ConfigObject("presets").apply {
                    put("printer", printer.title)
                    put("print", process.title)
                    put("filament", filament.title)
                }

                val activeModel = Model(source)
                model = activeModel
                activeModel.ensureOnBed(0)
                val effectiveConfig = SliceBeam.buildCurrentConfigObject(activeModel.requiredFilamentCount)
                assertEquals(nozzle, effectiveConfig["nozzle_diameter"])
                SliceBeam.genCurrentConfig(activeModel)
                val sliceResult = activeModel.slice(
                    SliceBeam.getCurrentConfigFile().absolutePath, output.absolutePath, null
                )
                try {
                    assertHasExtrusion(output.readText())
                } finally {
                    sliceResult.release()
                }
                activeModel.release()
                model = null
                output.delete()
            }
        } finally {
            model?.release()
            source.delete()
            output.delete()
            SliceBeam.getCurrentConfigFile().delete()
            SliceBeam.CONFIG = previousConfig
        }
    }

    @Test
    fun enumListDefaultsRetainNamedValuesInTheNativeOptionCatalog() = withKobraProfile {
        val enumListOptions = PrintConfigDef.getInstance().options.values.filter {
            it.type == ru.ytkab0bp.slicebeam.slic3r.ConfigOptionDef.ConfigOptionType.ENUMS
        }
        assertTrue("The native option catalog must expose enum-list defaults", enumListOptions.isNotEmpty())

        val nozzleType = requireNotNull(PrintConfigDef.getInstance().options["nozzle_type"])
        assertEquals("ENUMS", nozzleType.type.name)
        assertTrue("Nozzle enum names must remain available", requireNotNull(nozzleType.enumValues).contains("undefine"))
        assertEquals("The nullable enum-list default must serialize to its Orca key", "undefine", nozzleType.defaultValue)

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelFile = copyCube(context, "enum-list-defaults.stl")
        val gcodeFile = File(context.cacheDir, "enum-list-defaults.gcode")
        val model = Model(modelFile)
        var result: GCodeProcessorResult? = null
        try {
            SliceBeam.genCurrentConfig(model)
            result = model.slice(SliceBeam.getCurrentConfigFile().absolutePath, gcodeFile.absolutePath, null)
            assertHasExtrusion(gcodeFile.readText())
        } finally {
            result?.release()
            model.release()
            modelFile.delete()
            gcodeFile.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test
    fun lightningInfillSlicesAndProducesExtrusion() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = requireNotNull(SliceBeam.CONFIG)
        val process = requireNotNull(config.findPrint("0.16mm Standard"))
        val source = copyCube(context, "lightning-infill-source.stl")
        val output = File(context.cacheDir, "lightning-infill.gcode")
        val model = Model(source)
        var result: GCodeProcessorResult? = null
        try {
            process.put("sparse_infill_pattern", "lightning")
            process.put("sparse_infill_density", "20")
            SliceBeam.genCurrentConfig(model)
            result = model.slice(SliceBeam.getCurrentConfigFile().absolutePath, output.absolutePath, null)
            val gcode = output.readText()
            assertHasExtrusion(gcode)
            assertTrue(
                "Lightning must emit sparse-infill moves",
                gcode.contains(";TYPE:Sparse infill", ignoreCase = true) ||
                    gcode.contains(";TYPE:Internal infill", ignoreCase = true)
            )
        } finally {
            result?.release()
            model.release()
            source.delete()
            output.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test
    fun lightningInfillCompletesOnOverhangRichBunnyFixture() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = requireNotNull(SliceBeam.CONFIG)
        val process = requireNotNull(config.findPrint("0.16mm Standard"))
        process.put("sparse_infill_pattern", "lightning")
        process.put("sparse_infill_density", "15")
        process.put("top_shell_layers", "3")
        val modelFile = copyAsset(context, "models/bunny.stl", "orca25-lightning-overhang-bunny.stl")
        val output = File(context.cacheDir, "orca25-lightning-overhang-bunny.gcode")
        val model = Model(modelFile)
        var result: GCodeProcessorResult? = null
        try {
            model.ensureOnBed(0)
            SliceBeam.genCurrentConfig(model)
            result = model.slice(SliceBeam.getCurrentConfigFile().absolutePath, output.absolutePath, null)
            val gcode = output.readText()
            assertHasExtrusion(gcode)
            assertTrue(
                "the overhang-rich Lightning fixture must emit sparse infill",
                gcode.contains(";TYPE:Sparse infill", ignoreCase = true) ||
                    gcode.contains(";TYPE:Internal infill", ignoreCase = true),
            )
            Log.i("NativeSliceDeviceTest", "PASS Lightning overhang stress G-code bytes=${output.length()}")
        } finally {
            result?.release()
            model.release()
            modelFile.delete()
            output.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test(timeout = 90_000)
    fun denseInfillBridgeDetectionCompletesOnSteppedOverhangFixture() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = requireNotNull(SliceBeam.CONFIG)
        val process = requireNotNull(config.findPrint("0.16mm Standard"))
        process.put("sparse_infill_density", "100")
        process.put("top_shell_layers", "3")
        val modelFile = createSteppedOverhangFixture("orca25-dense-bridge-stepped.stl")
        val output = File(context.cacheDir, "orca25-dense-bridge-stepped.gcode")
        val model = Model(modelFile)
        var result: GCodeProcessorResult? = null
        try {
            model.ensureOnBed(0)
            SliceBeam.genCurrentConfig(model)
            result = model.slice(SliceBeam.getCurrentConfigFile().absolutePath, output.absolutePath, null)
            val gcode = output.readText()
            assertHasExtrusion(gcode)
            assertTrue("the dense overhang slice must retain multiple layers",
                Regex("(?m)^;LAYER_CHANGE\\s*$").findAll(gcode).count() > 3)
            assertTrue("the dense overhang slice must include infill paths",
                gcode.contains("TYPE:Internal infill", ignoreCase = true) ||
                    gcode.contains("TYPE:Solid infill", ignoreCase = true) ||
                    gcode.contains("TYPE:Top surface", ignoreCase = true))
            Log.i("NativeSliceDeviceTest", "PASS dense-infill bridge detection G-code bytes=${output.length()}")
        } finally {
            result?.release()
            model.release()
            modelFile.delete()
            output.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test(timeout = 120_000)
    fun hilbertInternalBridgeDirectionFollowsSupportingLayerRotationTemplate() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = requireNotNull(SliceBeam.CONFIG)
        val process = requireNotNull(config.findPrint("0.16mm Standard"))
        process.put("sparse_infill_density", "15")
        process.put("sparse_infill_smooth_factor", "100%")
        process.put("fill_multiline", "1")
        process.put("top_shell_layers", "3")
        process.put("bottom_shell_layers", "2")
        process.put("top_shell_thickness", "0")
        process.put("bottom_shell_thickness", "0")
        process.put("layer_height", "0.2")
        process.put("initial_layer_print_height", "0.2")
        process.put("thick_internal_bridges", "1")
        process.put("infill_direction", "45")
        process.put("internal_bridge_angle", "0")
        process.put("align_infill_direction_to_model", "0")
        val modelFile = createInternalBridgeStepFixture("orca25-hilbert-bridge-rotation.stl")
        val output = File(context.cacheDir, "orca25-hilbert-bridge-rotation.gcode")

        fun bridgeAnglesFor(pattern: String, rotationTemplate: String): List<Double> {
            process.put("sparse_infill_pattern", pattern)
            process.put("sparse_infill_rotate_template", rotationTemplate)
            val model = Model(modelFile)
            var result: GCodeProcessorResult? = null
            try {
                model.ensureOnBed(0)
                SliceBeam.genCurrentConfig(model)
                val nativeConfig = SliceBeam.getCurrentConfigFile().readText()
                assertTrue("the lower-layer rotation template must reach the native slicer",
                    nativeConfig.contains("sparse_infill_rotate_template") && nativeConfig.contains(rotationTemplate))
                result = model.slice(SliceBeam.getCurrentConfigFile().absolutePath, output.absolutePath, null)
                val gcode = output.readText()
                val gcodeTypes = gcode.lineSequence().filter { it.startsWith(";TYPE:") }.distinct().toList()
                assertTrue("Slicing must keep bridge paths; generated types=$gcodeTypes", gcodeTypes.any { it.contains("Bridge", ignoreCase = true) })
                var bridgeRole = false
                var x: Double? = null
                var y: Double? = null
                val coordinate = Regex("([XY])(-?\\d+(?:\\.\\d+)?)")
                val angles = mutableListOf<Double>()
                for (line in gcode.lineSequence()) {
                    if (line.startsWith(";TYPE:")) bridgeRole = line.contains("Bridge", ignoreCase = true)
                    if (!bridgeRole || !line.startsWith("G1 ") || !line.contains('E')) continue
                    var nextX = x
                    var nextY = y
                    coordinate.findAll(line).forEach { match ->
                        val value = match.groupValues[2].toDouble()
                        when (match.groupValues[1]) {
                            "X" -> nextX = value
                            "Y" -> nextY = value
                        }
                    }
                    if (x != null && y != null && nextX != null && nextY != null) {
                        val dx = nextX - x
                        val dy = nextY - y
                        if (kotlin.math.hypot(dx, dy) > 0.2) {
                            var angle = kotlin.math.atan2(dy, dx) % Math.PI
                            if (angle < 0.0) angle += Math.PI
                            angles += angle
                        }
                    }
                    x = nextX
                    y = nextY
                }
                assertTrue("Bridge role should contain measurable extruding XY segments", angles.isNotEmpty())
                return angles
            } finally {
                result?.release()
                model.release()
                output.delete()
                SliceBeam.getCurrentConfigFile().delete()
            }
        }

        fun meanUndirectedAngle(angles: List<Double>): Double {
            val sin = angles.sumOf { kotlin.math.sin(2.0 * it) }
            val cos = angles.sumOf { kotlin.math.cos(2.0 * it) }
            var mean = 0.5 * kotlin.math.atan2(sin, cos)
            if (mean < 0.0) mean += Math.PI
            return mean
        }

        try {
            for (pattern in listOf("hilbertcurve", "octagramspiral")) {
                val unrotated = meanUndirectedAngle(bridgeAnglesFor(pattern, "0"))
                val rotated = meanUndirectedAngle(bridgeAnglesFor(pattern, "45"))
                var difference = (rotated - unrotated) % Math.PI
                if (difference < 0.0) difference += Math.PI
                val circularDifference = minOf(difference, Math.PI - difference)
                assertTrue(
                    "$pattern bridges should follow the lower-layer rotation template (unrotated=$unrotated, rotated=$rotated)",
                    kotlin.math.abs(circularDifference - Math.PI / 4.0) < Math.toRadians(15.0),
                )
                Log.i("NativeSliceDeviceTest", "PASS $pattern internal bridge direction follows lower-layer rotation template")
            }
        } finally {
            modelFile.delete()
            output.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test
    fun multilineGridInfillUsesMiterJoinsAndPreservesGeometry() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = requireNotNull(SliceBeam.CONFIG)
        val process = requireNotNull(config.findPrint("0.16mm Standard"))
        process.put("fill_multiline", "3")
        process.put("sparse_infill_pattern", "grid")
        process.put("sparse_infill_density", "35")
        process.put("top_shell_layers", "1")
        process.put("bottom_shell_layers", "1")
        process.put("layer_height", "0.2")
        process.put("initial_layer_print_height", "0.2")
        val modelFile = createThinCubeFixture("orca25-multiline-infill-rectangle.stl", 40.0, 30.0, 6.0)
        val output = File(context.cacheDir, "orca25-multiline-infill-rectangle.gcode")
        val model = Model(modelFile)
        var result: GCodeProcessorResult? = null
        try {
            model.ensureOnBed(0)
            SliceBeam.genCurrentConfig(model)
            result = model.slice(SliceBeam.getCurrentConfigFile().absolutePath, output.absolutePath, null)
            val gcode = output.readText()
            assertHasExtrusion(gcode)
            var sparseInfill = false
            val moves = mutableListOf<String>()
            for (line in gcode.lineSequence()) {
                if (line.startsWith(";TYPE:")) sparseInfill = line.contains("Sparse infill", ignoreCase = true)
                if (sparseInfill && line.startsWith("G1 ") && line.contains('E') && (line.contains(" X") || line.contains(" Y")))
                    moves += line
            }
            // Orca's join-order optimizer and multiline anchor hookups intentionally alter this path count.
            // Keep a deterministic fingerprint, and separately verify the optimized paths stay on the part.
            assertEquals("Orca's optimized miter-connected multiline Grid geometry", 7547, moves.size)
            val xy = moves.flatMap { line ->
                Regex("(?:^|\\s)([XY])(-?\\d+(?:\\.\\d+)?)").findAll(line)
                    .map { it.groupValues[1] to it.groupValues[2].toDouble() }
                    .toList()
            }
            assertTrue("The Grid fixture must contain XY path coordinates", xy.isNotEmpty())
            val xs = xy.filter { it.first == "X" }.map { it.second }
            val ys = xy.filter { it.first == "Y" }.map { it.second }
            assertTrue("Optimized Grid paths must stay within the model's X bounds: ${xs.minOrNull()}..${xs.maxOrNull()}",
                xs.minOrNull()!! >= -0.1 && xs.maxOrNull()!! <= 40.1)
            assertTrue("Optimized Grid paths must stay within the model's Y bounds: ${ys.minOrNull()}..${ys.maxOrNull()}",
                ys.minOrNull()!! >= -0.1 && ys.maxOrNull()!! <= 30.1)
        } finally {
            result?.release()
            model.release()
            modelFile.delete()
            output.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test(timeout = 120_000)
    fun quarterCubicMultilineUsesNonCrossingNativeToolpaths() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val process = requireNotNull(SliceBeam.CONFIG?.findPrint("0.16mm Standard"))
        val keys = listOf("sparse_infill_pattern", "sparse_infill_density", "fill_multiline",
            "wall_loops", "top_shell_layers", "bottom_shell_layers", "layer_height",
            "initial_layer_print_height", "brim_type", "skirt_loops")
        val previous = keys.associateWith { process[it] }
        val source = createThinCubeFixture("quarter-cubic-multiline.stl", 20.0, 20.0, 12.0)
        val output = File(context.cacheDir, "quarter-cubic-multiline.gcode")
        val model = Model(source).also { it.ensureOnBed(0) }

        fun sparseMoves(multiline: Int): List<String> {
            process.put("sparse_infill_pattern", "quartercubic")
            process.put("sparse_infill_density", "25%")
            process.put("fill_multiline", multiline.toString())
            SliceBeam.genCurrentConfig(model)
            val serialized = SliceBeam.getCurrentConfigFile().readText()
            assertTrue("fill_multiline=$multiline must reach the generated native config",
                Regex("(?m)^fill_multiline\\s*=\\s*$multiline(?:\\.0+)?\\s*$").containsMatchIn(serialized))
            output.delete()
            val result = model.slice(SliceBeam.getCurrentConfigFile().absolutePath, output.absolutePath, null)
            try {
                val moves = mutableListOf<String>()
                var sparse = false
                output.forEachLine { line ->
                    if (line.startsWith(";TYPE:")) sparse = line.removePrefix(";TYPE:").trim() == "Sparse infill"
                    else if (sparse && line.startsWith("G1 ") && line.contains('E') &&
                        (line.contains(" X") || line.contains(" Y"))) moves += line
                }
                assertTrue("Quarter Cubic multiline=$multiline must produce sparse infill", moves.isNotEmpty())
                return moves
            } finally {
                result.release()
            }
        }

        try {
            process.put("wall_loops", "2")
            process.put("top_shell_layers", "2")
            process.put("bottom_shell_layers", "2")
            process.put("layer_height", "0.2")
            process.put("initial_layer_print_height", "0.2")
            process.put("brim_type", "no_brim")
            process.put("skirt_loops", "0")

            val singleLine = sparseMoves(1)
            val doubleLine = sparseMoves(2)
            assertNotEquals("multiline Quarter Cubic must use its dedicated non-crossing path generator",
                singleLine, doubleLine)
            assertTrue("multiline Quarter Cubic must produce the paired paths", doubleLine.size > singleLine.size)

            val xy = doubleLine.flatMap { line ->
                Regex("(?:^|\\s)([XY])(-?\\d+(?:\\.\\d+)?)").findAll(line)
                    .map { it.groupValues[1] to it.groupValues[2].toDouble() }.toList()
            }
            assertTrue("multiline Quarter Cubic must emit measurable XY extrusion coordinates", xy.isNotEmpty())
            val xs = xy.filter { it.first == "X" }.map { it.second }
            val ys = xy.filter { it.first == "Y" }.map { it.second }
            assertTrue("sparse infill must stay on the 20 mm fixture in X: ${xs.minOrNull()}..${xs.maxOrNull()}",
                xs.minOrNull()!! >= -0.1 && xs.maxOrNull()!! <= 20.1)
            assertTrue("sparse infill must stay on the 20 mm fixture in Y: ${ys.minOrNull()}..${ys.maxOrNull()}",
                ys.minOrNull()!! >= -0.1 && ys.maxOrNull()!! <= 20.1)
        } finally {
            keys.forEach { process.put(it, previous[it]) }
            model.release()
            source.delete()
            output.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test(timeout = 90_000)
    fun lightningInfillCompletesWhenOpenedOverhangExtendsPastInfillBounds() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = requireNotNull(SliceBeam.CONFIG)
        val process = requireNotNull(config.findPrint("0.16mm Standard"))
        process.put("sparse_infill_pattern", "lightning")
        process.put("sparse_infill_density", "15")
        process.put("top_shell_layers", "2")
        val modelFile = createSteppedOverhangFixture("orca25-lightning-outside-infill-bounds.stl")
        val output = File(context.cacheDir, "orca25-lightning-outside-infill-bounds.gcode")
        val model = Model(modelFile)
        var result: GCodeProcessorResult? = null
        try {
            model.ensureOnBed(0)
            SliceBeam.genCurrentConfig(model)
            result = model.slice(SliceBeam.getCurrentConfigFile().absolutePath, output.absolutePath, null)
            val gcode = output.readText()
            assertHasExtrusion(gcode)
            assertTrue(
                "the stepped overhang must generate Lightning infill",
                gcode.contains(";TYPE:Sparse infill", ignoreCase = true) ||
                    gcode.contains(";TYPE:Internal infill", ignoreCase = true),
            )
            Log.i("NativeSliceDeviceTest", "PASS Lightning stepped overhang beyond infill bounds bytes=${output.length()}")
        } finally {
            result?.release()
            model.release()
            modelFile.delete()
            output.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test
    fun zContouringSlicesMultipleInstancesOfOneObject() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = requireNotNull(SliceBeam.CONFIG)
        val process = requireNotNull(config.findPrint("0.16mm Standard"))
        process.put("zaa_enabled", "1")
        process.put("gcode_label_objects", "1")
        val source = copyAsset(context, "models/bunny.stl", "orca25-z-contour-multi-instance-bunny.stl")
        val output = File(context.cacheDir, "orca25-z-contour-multi-instance-bunny.gcode")
        val model = Model(source)
        var result: GCodeProcessorResult? = null
        try {
            model.ensureOnBed(0)
            assertTrue("fixture must contain an object to duplicate", model.objectsCount > 0)
            assertTrue(
                "the linked instance must share the source object's transform group",
                model.duplicateInstance(0, 0, Vec3d(45.0, 0.0, 0.0)) > 0,
            )
            SliceBeam.genCurrentConfig(model)
            val generatedConfig = SliceBeam.getCurrentConfigFile().readText()
            assertTrue("Z contouring must reach native print configuration", generatedConfig.contains("zaa_enabled = 1"))
            result = model.slice(SliceBeam.getCurrentConfigFile().absolutePath, output.absolutePath, null)
            val gcode = output.readText()
            assertHasExtrusion(gcode)
            assertTrue(
                "the slice must retain both linked instances",
                gcode.lineSequence().count { it.startsWith("; printing object ") } >= 2,
            )
        } finally {
            result?.release()
            model.release()
            source.delete()
            output.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test
    fun buildPlateOnlyTreeSupportsSliceWithConfiguredObjectClearance() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = requireNotNull(SliceBeam.CONFIG)
        val process = requireNotNull(config.findPrint("0.16mm Standard"))
        process.put("enable_support", "1")
        process.put("support_type", "tree(auto)")
        process.put("support_on_build_plate_only", "1")
        process.put("support_object_xy_distance", "0.8")
        val modelFile = copyAsset(context, "models/bunny.stl", "orca25-tree-clearance-bunny.stl")
        val output = File(context.cacheDir, "orca25-tree-clearance-bunny.gcode")
        val model = Model(modelFile)
        var result: GCodeProcessorResult? = null
        try {
            val supportMovesByClearance = mutableMapOf<String, List<String>>()
            for (clearance in listOf("0.2", "0.8")) {
                process.put("support_object_xy_distance", clearance)
                SliceBeam.genCurrentConfig(model)
                val configFile = SliceBeam.getCurrentConfigFile()
                val generatedConfig = configFile.readText()
                assertTrue("tree auto support must reach the native slice config", generatedConfig.contains("support_type = tree(auto)"))
                assertTrue("build-plate-only support must reach the native slice config", generatedConfig.contains("support_on_build_plate_only = 1"))
                assertTrue(
                    "the requested object clearance must reach the native slice config",
                    generatedConfig.contains("support_object_xy_distance = $clearance"),
                )
                result = model.slice(configFile.absolutePath, output.absolutePath, null)
                val gcode = output.readText()
                assertHasExtrusion(gcode)
                assertTrue(
                    "the overhang fixture must produce support paths with build-plate-only tree support enabled",
                    Regex("(?im)^;TYPE:.*support").containsMatchIn(gcode),
                )
                var readingSupport = false
                supportMovesByClearance[clearance] = gcode.lineSequence().mapNotNull { line ->
                    if (line.startsWith(";TYPE:", ignoreCase = true)) {
                        readingSupport = line.contains("support", ignoreCase = true)
                        null
                    } else if (readingSupport && line.startsWith("G1 ") && line.contains(" E") &&
                        (line.contains(" X") || line.contains(" Y"))
                    ) line.substringBefore(';').trim() else null
                }.toList()
                assertTrue("tree-support extrusion moves must be present at clearance $clearance", supportMovesByClearance.getValue(clearance).isNotEmpty())
                result?.release()
                result = null
                output.delete()
                configFile.delete()
            }
            assertFalse(
                "changing the tree-support object clearance must change generated support paths",
                supportMovesByClearance.getValue("0.2") == supportMovesByClearance.getValue("0.8"),
            )
            Log.i("NativeSliceDeviceTest", "PASS tree-support clearance changes generated paths at 0.2 and 0.8 mm")
        } finally {
            result?.release()
            model.release()
            modelFile.delete()
            output.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test(timeout = 180_000)
    fun variableLayerGapKeepsTopSupportInterfaceUnderBothSidesOfAnOverhang() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val process = requireNotNull(SliceBeam.CONFIG?.findPrint("0.16mm Standard"))
        process.put("layer_height", "0.2")
        process.put("initial_layer_print_height", "0.2")
        process.put("enable_support", "1")
        process.put("support_type", "normal(auto)")
        process.put("support_on_build_plate_only", "0")
        process.put("support_top_z_distance", "0.2")
        process.put("support_bottom_z_distance", "0.2")
        process.put("support_object_xy_distance", "0.35")
        process.put("support_interface_spacing", "0")
        process.put("support_interface_top_layers", "2")
        process.put("support_interface_bottom_layers", "0")

        val modelFile = createSupportCapitalFixture("orca25-support-variable-gap-capital.stl")
        val output = File(context.cacheDir, "orca25-support-variable-gap-capital.gcode")
        val model = Model(modelFile)
        var result: GCodeProcessorResult? = null
        try {
            model.ensureOnBed(0)
            SliceBeam.genCurrentConfig(model)
            val configFile = SliceBeam.getCurrentConfigFile()
            model.applyAdaptiveLayerHeight(0, configFile.absolutePath, 0.5f)
            val boundsMin = model.boundingBoxExactMin
            val boundsMax = model.boundingBoxExactMax
            val centerX = (boundsMin.x + boundsMax.x) / 2.0
            val centerY = (boundsMin.y + boundsMax.y) / 2.0
            assertTrue(
                "a finer variable layer-height schedule must be applied below the cap",
                model.adjustLayerHeightAtWorldPoint(
                    0, configFile.absolutePath, centerX, centerY, boundsMin.z + 10.0,
                    24.0, 0.04, 1, 0.5f,
                ),
            )
            SliceBeam.genCurrentConfig(model)
            val generated = configFile.readText()
            assertTrue("the variable profile must reach the native slice config", generated.contains("adaptive_layer_height = 1"))
            result = model.slice(configFile.absolutePath, output.absolutePath, null)
            val gcode = output.readText()
            assertHasExtrusion(gcode)
            val interfaceSegments = supportExtrusionSegments(gcode).filter {
                it.type.contains("interface", ignoreCase = true) && it.z in 11.5..12.05
            }
            assertTrue("top-contact support interface must exist below the cap", interfaceSegments.isNotEmpty())
            for (offsetX in listOf(-10.0, 10.0)) {
                val left = centerX + offsetX - 2.0
                val right = centerX + offsetX + 2.0
                val bottom = centerY - 2.0
                val top = centerY + 2.0
                val overlapping = interfaceSegments.count { segment ->
                    maxOf(segment.x0, segment.x1) >= left && minOf(segment.x0, segment.x1) <= right &&
                        maxOf(segment.y0, segment.y1) >= bottom && minOf(segment.y0, segment.y1) <= top
                }
                assertTrue(
                    "synchronized variable-layer gaps must preserve support contact on cap side x=$offsetX",
                    overlapping > 0,
                )
            }
            Log.i("NativeSliceDeviceTest", "PASS top-contact interface retained on both cap sides segments=${interfaceSegments.size}")
        } finally {
            result?.release()
            model.release()
            modelFile.delete()
            output.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test(timeout = 180_000)
    fun synchronizedVariableLayerGapPreservesBottomSupportInterfaces() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val process = requireNotNull(SliceBeam.CONFIG?.findPrint("0.16mm Standard"))
        process.put("layer_height", "0.2")
        process.put("initial_layer_print_height", "0.2")
        process.put("enable_support", "1")
        process.put("support_type", "normal(auto)")
        process.put("support_on_build_plate_only", "0")
        process.put("independent_support_layer_height", "0")
        process.put("support_top_z_distance", "0.2")
        process.put("support_bottom_z_distance", "0.2")
        process.put("support_object_xy_distance", "0.35")
        process.put("support_interface_top_layers", "0")
        process.put("support_interface_bottom_layers", "2")

        val modelFile = createSupportCapitalFixture("orca25-support-variable-gap-bottom-capital.stl")
        val output = File(context.cacheDir, "orca25-support-variable-gap-bottom-capital.gcode")
        val model = Model(modelFile)
        var result: GCodeProcessorResult? = null
        try {
            model.ensureOnBed(0)
            SliceBeam.genCurrentConfig(model)
            val configFile = SliceBeam.getCurrentConfigFile()
            model.applyAdaptiveLayerHeight(0, configFile.absolutePath, 0.5f)
            val boundsMin = model.boundingBoxExactMin
            val boundsMax = model.boundingBoxExactMax
            val centerX = (boundsMin.x + boundsMax.x) / 2.0
            val centerY = (boundsMin.y + boundsMax.y) / 2.0
            assertTrue(
                "a finer synchronized layer schedule must be applied at the base-to-stem transition",
                model.adjustLayerHeightAtWorldPoint(
                    0, configFile.absolutePath, centerX, centerY, boundsMin.z + 2.08,
                    8.0, 0.04, 1, 0.5f,
                ),
            )
            val profile = model.adaptiveLayerHeightProfile(0)
            assertTrue("the variable profile must include a layer thinner than the nominal 0.2 mm",
                profile.toList().chunked(2).any { it.size == 2 && it[0] in 1.6..2.4 && it[1] < 0.2 - 1e-6 })

            SliceBeam.genCurrentConfig(model)
            val generated = configFile.readText()
            assertTrue("support bottom interface layers must reach native slicing",
                generated.contains("support_interface_bottom_layers = 2"))
            assertTrue("support and object layer heights must be synchronized",
                generated.contains("independent_support_layer_height = 0"))
            result = model.slice(configFile.absolutePath, output.absolutePath, null)
            val gcode = output.readText()
            assertHasExtrusion(gcode)
            val interfaceZs = supportExtrusionSegments(gcode)
                .filter { it.type.contains("support interface", ignoreCase = true) }
                .map { kotlin.math.round(it.z * 1000.0).toLong() }
                .distinct()
            assertEquals("two bottom-contact interface layers must survive the rounded variable gap", 2, interfaceZs.size)
            Log.i("NativeSliceDeviceTest", "PASS synchronized bottom support contact interface layers=$interfaceZs")
        } finally {
            result?.release()
            model.release()
            modelFile.delete()
            output.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test(timeout = 180_000)
    fun organicTreeSupportPathsAreStableAcrossRepeatedSlices() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = requireNotNull(SliceBeam.CONFIG)
        val process = requireNotNull(config.findPrint("0.16mm Standard"))
        process.put("enable_support", "1")
        process.put("support_type", "tree(auto)")
        process.put("support_on_build_plate_only", "1")
        process.put("support_object_xy_distance", "0.2")

        val modelFile = copyAsset(context, "models/bunny.stl", "orca25-tree-order-bunny.stl")
        val output = File(context.cacheDir, "orca25-tree-order-bunny.gcode")
        val model = Model(modelFile)
        var result: GCodeProcessorResult? = null
        try {
            model.ensureOnBed(0)
            val supportPaths = (1..3).map { run ->
                SliceBeam.genCurrentConfig(model)
                val configFile = SliceBeam.getCurrentConfigFile()
                result = model.slice(configFile.absolutePath, output.absolutePath, null)
                val gcode = output.readText()
                assertHasExtrusion(gcode)
                val paths = mutableListOf<String>()
                var readingSupport = false
                for (line in gcode.lineSequence()) {
                    if (line.startsWith(";TYPE:", ignoreCase = true)) {
                        readingSupport = line.contains("support", ignoreCase = true)
                    } else if (readingSupport && line.startsWith("G1 ") && line.contains(" E") &&
                        (line.contains(" X") || line.contains(" Y"))
                    ) {
                        paths += line.substringBefore(';').trim()
                    }
                }
                assertTrue("tree support must produce extrusion paths on run $run", paths.isNotEmpty())
                result?.release()
                result = null
                output.delete()
                configFile.delete()
                paths
            }
            assertEquals(
                "parallel organic-tree tip collection must produce the same ordered support paths on repeated slices",
                supportPaths.first(), supportPaths[1],
            )
            assertEquals(
                "the deterministic support result must remain stable on a third slice",
                supportPaths.first(), supportPaths[2],
            )
            Log.i("NativeSliceDeviceTest", "PASS repeatable organic tree-support paths moves=${supportPaths.first().size}")
        } finally {
            result?.release()
            model.release()
            modelFile.delete()
            output.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test(timeout = 180_000)
    fun treeSupportsKeepEveryOverhangThatJoinsAClusterOnTheSameLayer() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val process = requireNotNull(SliceBeam.CONFIG?.findPrint("0.16mm Standard"))
        process.put("layer_height", "0.2")
        process.put("enable_support", "1")
        process.put("support_type", "tree(auto)")
        process.put("support_style", "tree_hybrid")
        process.put("support_on_build_plate_only", "1")
        process.put("support_remove_small_overhang", "0")
        process.put("line_width", "0.45")

        val modelFile = createTreeClusterSameLayerFixture("orca25-tree-cluster-same-layer.stl")
        val output = File(context.cacheDir, "orca25-tree-cluster-same-layer.gcode")
        val model = Model(modelFile)
        var result: GCodeProcessorResult? = null
        try {
            model.ensureOnBed(0)
            val boundsMin = model.boundingBoxExactMin
            val boundsMax = model.boundingBoxExactMax
            val centerY = (boundsMin.y + boundsMax.y) / 2.0
            SliceBeam.genCurrentConfig(model)
            val nativeConfig = SliceBeam.getCurrentConfigFile().readText()
            assertTrue("tree hybrid support must reach native slicing", nativeConfig.contains("support_style = tree_hybrid"))
            result = model.slice(SliceBeam.getCurrentConfigFile().absolutePath, output.absolutePath, null)
            val gcode = output.readText()
            assertHasExtrusion(gcode)
            val supportAtWingHeight = supportExtrusionSegments(gcode).filter { it.z in 4.0..5.25 }
            val supportYs = supportAtWingHeight.flatMap { listOf(it.y0, it.y1) }
            assertTrue("the fixture must produce tree-support extrusion below the front wing", supportYs.any { it < centerY - 6.0 })
            assertTrue("the same-layer cluster must preserve the back-wing overhang and its support", supportYs.any { it > centerY + 6.0 })
            Log.i("NativeSliceDeviceTest", "PASS tree support retained both same-layer wing contacts centerY=$centerY")
        } finally {
            result?.release()
            model.release()
            modelFile.delete()
            output.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test(timeout = 180_000)
    fun tallOrganicTreeSupportUsesMovementAwareReinforcementAndKeepsPathsValid() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val process = requireNotNull(SliceBeam.CONFIG?.findPrint("0.16mm Standard"))
        process.put("layer_height", "0.2")
        process.put("initial_layer_print_height", "0.2")
        process.put("enable_support", "1")
        process.put("support_type", "tree(auto)")
        process.put("support_on_build_plate_only", "1")
        process.put("support_style", "tree_hybrid")
        process.put("tree_support_wall_count", "0")
        process.put("support_interface_top_layers", "0")
        process.put("support_interface_bottom_layers", "0")
        val modelFile = createTallStemCapFixture("orca25-tree-movement-aware-reinforcement.stl", 101.0)
        val output = File(context.cacheDir, "orca25-tree-movement-aware-reinforcement.gcode")
        val model = Model(modelFile)
        var result: GCodeProcessorResult? = null
        try {
            model.ensureOnBed(0)
            SliceBeam.genCurrentConfig(model)
            result = model.slice(SliceBeam.getCurrentConfigFile().absolutePath, output.absolutePath, null)
            val gcode = output.readText()
            assertHasExtrusion(gcode)
            val support = supportExtrusionSegments(gcode)
            assertTrue("a 101 mm tree support must retain executable support toolpaths", support.isNotEmpty())
            assertTrue("the tall fixture must exercise support immediately below its cap",
                support.any { it.z in 99.5..100.9 })
            assertTrue("generated support paths must contain no non-finite coordinates",
                support.all { listOf(it.x0, it.y0, it.x1, it.y1, it.z).all(Double::isFinite) })
            Log.i("NativeSliceDeviceTest", "PASS movement-aware tall organic tree support segments=${support.size}")
        } finally {
            result?.release()
            model.release()
            modelFile.delete()
            output.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test(timeout = 180_000)
    fun treeSupportBottomContactCachePreservesInterfaceToolpaths() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val process = requireNotNull(SliceBeam.CONFIG?.findPrint("0.16mm Standard"))
        process.put("enable_support", "1")
        process.put("support_type", "tree(auto)")
        process.put("support_style", "tree_slim")
        process.put("support_on_build_plate_only", "0")
        process.put("support_interface_top_layers", "2")
        process.put("support_interface_bottom_layers", "2")
        process.put("support_bottom_z_distance", "0.2")
        process.put("layer_height", "0.2")

        val modelFile = createTwoTierTreeSupportFixture("orca25-tree-bottom-contact-cache.stl")
        val output = File(context.cacheDir, "orca25-tree-bottom-contact-cache.gcode")
        val model = Model(modelFile)
        var result: GCodeProcessorResult? = null
        try {
            model.ensureOnBed(0)
            val interfacePathCounts = listOf("0", "1").associateWith { wallCount ->
                process.put("tree_support_wall_count", wallCount)
                (1..2).map { run ->
                    SliceBeam.genCurrentConfig(model)
                    val nativeConfig = SliceBeam.getCurrentConfigFile().readText()
                    assertTrue("two bottom interface layers must reach native slicing", nativeConfig.contains("support_interface_bottom_layers = 2"))
                    assertTrue("the 0.2 mm object/support gap must reach native slicing", nativeConfig.contains("support_bottom_z_distance = 0.2"))
                    assertTrue("tree support wall count $wallCount must reach native slicing",
                        nativeConfig.contains("tree_support_wall_count = $wallCount"))
                    result = model.slice(SliceBeam.getCurrentConfigFile().absolutePath, output.absolutePath, null)
                    val gcode = output.readText()
                    assertHasExtrusion(gcode)
                    val interfaceSegments = supportExtrusionSegments(gcode).filter { it.type.contains("support interface") }
                    val bottomContactZs = interfaceSegments.map { it.z }.filter { it > 3.0 && it < 4.5 }
                        .map { kotlin.math.round(it * 1000.0).toLong() }.distinct().sorted()
                    assertEquals("tree support must retain exactly the configured two bottom-contact interface layers (walls=$wallCount run=$run)",
                        2, bottomContactZs.size)
                    result?.release()
                    result = null
                    output.delete()
                    SliceBeam.getCurrentConfigFile().delete()
                    bottomContactZs.size
                }
            }
            Log.i("NativeSliceDeviceTest", "PASS tree bottom-contact slices retained interface paths for wall counts 0/1: $interfacePathCounts")
        } finally {
            result?.release()
            model.release()
            modelFile.delete()
            output.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test
    fun brimCanFollowElephantFootCompensatedFirstLayerOutline() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val process = requireNotNull(SliceBeam.CONFIG?.findPrint("0.16mm Standard"))
        val modelFile = createThinCubeFixture("efc-aware-brim.stl", 20.0, 20.0, 5.0)
        val model = Model(modelFile)
        val output = File(context.cacheDir, "efc-aware-brim.gcode")
        var result: GCodeProcessorResult? = null
        try {
            model.ensureOnBed(0)
            process.put("brim_type", "outer_only")
            process.put("brim_width", "6")
            process.put("brim_object_gap", "0")
            process.put("skirt_loops", "0")
            process.put("elefant_foot_compensation", "0.6")
            process.put("elefant_foot_compensation_layers", "1")
            process.put("support_on_build_plate_only", "0")

            fun sliceWithBrimUsesCompensatedOutline(enabled: Boolean): List<String> {
                process.put("brim_use_efc_outline", if (enabled) "1" else "0")
                SliceBeam.genCurrentConfig(model)
                val configFile = SliceBeam.getCurrentConfigFile()
                val configText = configFile.readText()
                assertTrue("brim outline control must reach the native slice config",
                    Regex("(?m)^brim_use_efc_outline\\s*=\\s*${if (enabled) 1 else 0}\\s*$").containsMatchIn(configText))
                output.delete()
                result = model.slice(configFile.absolutePath, output.absolutePath, null)
                val gcode = output.readText()
                assertHasExtrusion(gcode)
                val brimSegments = mutableListOf<String>()
                var inBrim = false
                var x: String? = null
                var y: String? = null
                val axis = Regex("(?:^|\\s)([XY])(-?(?:\\d+(?:\\.\\d*)?|\\.\\d+))(?=\\s|$)")
                for (line in gcode.lineSequence()) {
                    if (line.startsWith(";TYPE:", ignoreCase = true)) {
                        inBrim = line.contains("brim", ignoreCase = true)
                        continue
                    }
                    if (!line.startsWith("G0 ") && !line.startsWith("G1 ")) continue
                    val clean = line.substringBefore(';')
                    val axes = axis.findAll(clean).associate { it.groupValues[1] to it.groupValues[2] }
                    val nextX = axes["X"] ?: x
                    val nextY = axes["Y"] ?: y
                    if (inBrim && clean.contains(" E") && nextX != null && nextY != null)
                        brimSegments += "$nextX,$nextY"
                    x = nextX
                    y = nextY
                }
                assertTrue("the cube must produce brim extrusion", brimSegments.isNotEmpty())
                result?.release()
                result = null
                configFile.delete()
                return brimSegments
            }

            val rawOutlineBrim = sliceWithBrimUsesCompensatedOutline(false)
            val compensatedOutlineBrim = sliceWithBrimUsesCompensatedOutline(true)
            assertFalse("the brim toolpath must follow the post-EFC outline when enabled",
                rawOutlineBrim == compensatedOutlineBrim)
            Log.i("NativeSliceDeviceTest", "PASS EFC-aware brim emitted distinct first-layer brim paths")
        } finally {
            result?.release()
            model.release()
            modelFile.delete()
            output.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test
    fun treeSupportOverhangClearanceUsesReducedMinimumAndExtrusionWidth() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = requireNotNull(SliceBeam.CONFIG)
        val process = requireNotNull(config.findPrint("0.16mm Standard"))
        process.put("enable_support", "1")
        process.put("support_type", "tree(auto)")
        process.put("support_on_build_plate_only", "1")
        process.put("support_line_width", "0.4")
        process.put("outer_wall_line_width", "0.4")
        val modelFile = createSteppedOverhangFixture("orca25-tree-gap-stepped-model.stl")
        val output = File(context.cacheDir, "orca25-tree-gap-stepped-model.gcode")
        val model = Model(modelFile)
        var result: GCodeProcessorResult? = null
        try {
            model.ensureOnBed(0)
            val minGapsByClearance = mutableMapOf<String, Double>()
            val min = Vec3d()
            val max = Vec3d()
            model.getBoundingBoxExact(0, min, max)
            assertTrue("the synthetic stepped model must be 20 mm wide", max.x - min.x in 19.9..20.1)
            assertTrue("the synthetic stepped model must be 20 mm deep", max.y - min.y in 19.9..20.1)
            assertTrue("the synthetic stepped model must be 20 mm tall", max.z - min.z in 19.9..20.1)
            val centerX = (min.x + max.x) / 2.0
            val centerY = (min.y + max.y) / 2.0
            val baseZ = min.z
            val halfStem = 5.0
            val stemMinX = centerX - halfStem
            val stemMaxX = centerX + halfStem
            val stemMinY = centerY - halfStem
            val stemMaxY = centerY + halfStem
            for (clearance in listOf("0.2", "0.8")) {
                process.put("support_object_xy_distance", clearance)
                SliceBeam.genCurrentConfig(model)
                val generatedConfig = SliceBeam.getCurrentConfigFile().readText()
                assertTrue("support extrusion width must reach native slicing", generatedConfig.contains("support_line_width = 0.4"))
                assertTrue("the external wall width used for reduced overhang clearance must be controlled",
                    generatedConfig.contains("outer_wall_line_width = 0.4"))
                assertTrue("requested XY clearance must reach native slicing",
                    generatedConfig.contains("support_object_xy_distance = $clearance"))
                result = model.slice(SliceBeam.getCurrentConfigFile().absolutePath, output.absolutePath, null)
                val gcode = output.readText()
                assertHasExtrusion(gcode)
                assertTrue("the stepped cap must generate tree-support paths", Regex("(?im)^;TYPE:.*support").containsMatchIn(gcode))
                val supportSegments = supportExtrusionSegments(gcode).filter {
                    it.z > baseZ + 0.5 && it.z < baseZ + 9.5 &&
                        kotlin.math.hypot(it.x1 - it.x0, it.y1 - it.y0) > 1e-6
                }
                assertTrue("support paths must exist alongside the stem cross-section", supportSegments.isNotEmpty())
                // The stem is a constant 10 x 10 mm cross-section throughout this Z range.
                // Measure the deposited bead edge, not only its centerline, against the
                // exact rectangular stem cross-section. Tree contacts intentionally use the
                // smaller of object XY distance and half the external-wall width near an
                // overhang; the fixture fixes that reduced distance at 0.2 mm.
                val closest = supportSegments.minBy { support ->
                    pathSegmentRectangleDistance(support, stemMinX, stemMinY, stemMaxX, stemMaxY) - support.width / 2.0
                }
                val minGap = pathSegmentRectangleDistance(closest, stemMinX, stemMinY, stemMaxX, stemMaxY) - closest.width / 2.0
                val reducedOverhangClearance = minOf(clearance.toDouble(), 0.2)
                assertTrue(
                    "extruded support bead must maintain the reduced tree-overhang XY clearance from the solid stem " +
                        "(clearance=$clearance mm, minimum bead-surface gap=$minGap mm, " +
                        "lineWidth=${closest.width}, type=${closest.type}, path=${closest.x0},${closest.y0}->${closest.x1},${closest.y1})",
                    minGap + 0.02 >= reducedOverhangClearance,
                )
                minGapsByClearance[clearance] = minGap
                Log.i("NativeSliceDeviceTest", "tree clearance=$clearance mm minimum support-to-stem cross-section gap=$minGap mm " +
                    "segments=${supportSegments.size}")
                result.release()
                result = null
                output.delete()
                SliceBeam.getCurrentConfigFile().delete()
            }
            assertEquals(
                "when both requested clearances exceed half the 0.4 mm external wall, tree support should use the same reduced overhang gap",
                minGapsByClearance.getValue("0.2"), minGapsByClearance.getValue("0.8"), 0.02,
            )
        } finally {
            result?.release()
            model.release()
            modelFile.delete()
            output.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test
    fun pressureAdvanceLineCalibrationUsesAssignedFilamentFlowAndVolumetricLimit() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = copyCube(context, "orca25-pa-line-filament-source.stl")
        val output = File(context.cacheDir, "orca25-pa-line-filament.gcode")
        val model = Model(source)
        var result: GCodeProcessorResult? = null
        try {
            model.setUniformFilament(0, 2)
            val process = requireNotNull(SliceBeam.CONFIG?.findPrint("0.16mm Standard"))
            process.put("outer_wall_speed", "200")
            process.put("initial_layer_print_height", "0.2")
            SliceBeam.genCurrentConfig(model)
            val configFile = SliceBeam.getCurrentConfigFile()
            var configText = configFile.readText()
            fun setConfig(key: String, value: String) {
                val line = Regex("(?m)^${Regex.escape(key)}\\s*=.*$")
                configText = if (line.containsMatchIn(configText)) {
                    configText.replace(line, "$key = $value")
                } else {
                    "$configText\n$key = $value\n"
                }
            }
            // Filament 2 has a distinct diameter, flow ratio, and volumetric cap from filament 1.
            setConfig("filament_diameter", "1.75,2.85")
            setConfig("filament_flow_ratio", "1,0.9")
            setConfig("filament_max_volumetric_speed", "21,4")
            setConfig("filament_map", "1,1")
            configFile.writeText(configText)

            result = model.slice(configFile.absolutePath, output.absolutePath, null, 1, 0.0, 0.02, 0.002)
            val gcode = output.readText()
            val marker = maxOf(gcode.lastIndexOf(";LAYER_CHANGE"), gcode.lastIndexOf(";_LAYER_CHANGE"))
            assertTrue("PA calibration output must contain its own layer marker", marker >= 0)
            val calibrationStartLine = gcode.substring(0, marker).count { it == '\n' }

            data class Extrusion(val feedrate: Double, val ePerMm: Double)
            val extrusions = mutableListOf<Extrusion>()
            val wordPattern = Regex("([XYEF])([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+))")
            var x = 0.0
            var y = 0.0
            var e = 0.0
            var feedrate = 0.0
            var relativeE = false
            for ((lineIndex, line) in gcode.lineSequence().withIndex()) {
                if (line.startsWith("M83")) relativeE = true
                if (line.startsWith("M82")) relativeE = false
                val command = line.substringBefore(';').trim()
                if (!command.startsWith("G0 ") && !command.startsWith("G1 ") && !command.startsWith("G92 ")) continue
                var nextX = x
                var nextY = y
                var nextE: Double? = null
                for (match in wordPattern.findAll(command)) {
                    val value = match.groupValues[2].toDouble()
                    when (match.groupValues[1]) {
                        "X" -> nextX = value
                        "Y" -> nextY = value
                        "E" -> nextE = value
                        "F" -> feedrate = value
                    }
                }
                if (command.startsWith("G92 ")) {
                    nextE?.let { e = it }
                    continue
                }
                val length = kotlin.math.hypot(nextX - x, nextY - y)
                if (lineIndex > calibrationStartLine && nextE != null && length > 0.0) {
                    val deltaE = if (relativeE) nextE else nextE - e
                    if (feedrate > 0.0) extrusions += Extrusion(feedrate, deltaE / length)
                }
                nextE?.let { e = if (relativeE) e + it else it }
                x = nextX
                y = nextY
            }
            assertTrue("calibration must emit extruding XY moves", extrusions.isNotEmpty())
            // The numeric labels use a fixed 3600 mm/min speed; exclude those when checking the PA lines.
            val fastest = extrusions.filter { kotlin.math.abs(it.feedrate - 3600.0) > 1.0 }.maxBy { it.feedrate }
            // A 0.6 mm x 0.2 mm line uses ~0.1114 mm³/mm, so the selected 4 mm³/s cap
            // gives about 35 mm/s (2100 mm/min). Filament 1 would instead run near 188 mm/s.
            assertTrue("fast PA lines must respect filament 2's volumetric limit; got ${fastest.feedrate} mm/min; " +
                "all feedrates=${extrusions.map { it.feedrate }.distinct().sorted()}",
                fastest.feedrate in 1950.0..2250.0)
            val expectedEPerMm = (0.2 * 0.4 + Math.PI * 0.2 * 0.2 / 4.0) * 0.9 /
                (Math.PI * 2.85 * 2.85 / 4.0)
            assertTrue("PA line extrusion must use filament 2's diameter and flow ratio; got ${fastest.ePerMm}, expected $expectedEPerMm",
                kotlin.math.abs(fastest.ePerMm - expectedEPerMm) <= expectedEPerMm * 0.03)
        } finally {
            result?.release()
            model.release()
            source.delete()
            output.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test
    fun objectTooThinForAnyLayerWithRaftOrPreciseZReportsSlicingErrorInsteadOfCrashing() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val process = requireNotNull(SliceBeam.CONFIG?.findPrint("0.16mm Standard"))
        val thinHeight = 0.002
        val source = createThinCubeFixture("thin-object-no-layers.stl", 20.0, 20.0, thinHeight)
        val output = File(context.cacheDir, "thin-object-no-layers.gcode")
        val model = Model(source)
        try {
            process.put("layer_height", "0.2")
            process.put("initial_layer_print_height", "0.2")
            process.put("min_layer_height", "0.07")
            for (raftLayers in listOf(0, 2)) {
                for (preciseZ in listOf(false, true)) {
                    process.put("raft_layers", raftLayers.toString())
                    process.put("precise_z_height", if (preciseZ) "1" else "0")
                    SliceBeam.genCurrentConfig(model)
                    val configFile = SliceBeam.getCurrentConfigFile()
                    try {
                        val failure = runCatching {
                            model.slice(configFile.absolutePath, output.absolutePath, null)
                        }.exceptionOrNull()
                        assertNotNull("a ${thinHeight}mm object must not generate any 0.2mm print layers", failure)
                        assertTrue(
                            "raft=$raftLayers preciseZ=$preciseZ must report the normal no-layers error; got $failure",
                            failure.toString().contains("No layers were detected"),
                        )
                    } finally {
                        configFile.delete()
                        output.delete()
                    }
                }
            }
        } finally {
            model.release()
            source.delete()
        }
    }
    @Test
    fun insetMultiSideExtruderAreaDoesNotRejectPathInsideItsPrintablePolygon() = withToolChangerProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val printer = requireNotNull(SliceBeam.CONFIG?.findPrinter("MyToolChanger 0.4 nozzle"))
        printer.put("physical_extruder_map", "1,0,0,0,0")
        printer.put("layer_change_gcode", "; VALIDATION_PHYSICAL=[most_used_physical_extruder_id]")
        val source = copyCube(context, "orca25-inset-extruder-area.stl")
        val output = File(context.cacheDir, "orca25-inset-extruder-area.gcode")
        val model = Model(source)
        var result: GCodeProcessorResult? = null
        try {
            model.ensureOnBed(0)
            // STL fixtures are centered around the origin; this test printer uses a 0..350 mm bed.
            model.translate(175.0, 175.0, 0.0)
            model.setUniformFilament(0, 1)
            val min = Vec3d()
            val max = Vec3d()
            model.getBoundingBoxExact(0, min, max)
            val pad = 4.0
            val x0 = (min.x - pad).coerceAtLeast(0.0)
            val y0 = (min.y - pad).coerceAtLeast(0.0)
            val x1 = (max.x + pad).coerceAtMost(350.0)
            val y1 = (max.y + pad).coerceAtMost(350.0)
            assertTrue("the test printable polygon must have positive width", x1 > x0)
            assertTrue("the test printable polygon must have positive height", y1 > y0)

            SliceBeam.genCurrentConfig(model)
            val configFile = SliceBeam.getCurrentConfigFile()
            val fullBed = "0x0,350x0,350x350,0x350"
            val insetArea = "$x0 x $y0,$x1 x $y0,$x1 x $y1,$x0 x $y1".replace(" ", "")
            val groupedAreas = (listOf(insetArea) + List(4) { fullBed }).joinToString("#")
            val generated = configFile.readText()
            val configured = if (Regex("(?m)^extruder_printable_area\\s*=").containsMatchIn(generated)) {
                generated.replace(Regex("(?m)^extruder_printable_area\\s*=.*$"), "extruder_printable_area = $groupedAreas")
            } else {
                "$generated\nextruder_printable_area = $groupedAreas\n"
            }
            configFile.writeText(configured)
            assertTrue("the inset, multi-side toolhead area must reach native slicing",
                configFile.readText().contains("extruder_printable_area = $groupedAreas"))

            result = model.slice(configFile.absolutePath, output.absolutePath, null)
            val gcode = output.readText()
            assertHasExtrusion(gcode)
            assertTrue("the test model must exercise the first physical toolhead",
                gcode.contains("VALIDATION_PHYSICAL=1"))
            val errorCode = result.getGCodeCheckErrorCode()
            assertEquals("paths fully inside the toolhead's inset printable area must not be rejected by its surrounding polygon bounds",
                0, errorCode and 1)
            assertEquals("the fixture remains below the toolhead height limit", 0, errorCode and (1 shl 1))
            Log.i("NativeSliceDeviceTest", "PASS inset multi-side extruder area errorCode=$errorCode bounds=$insetArea")

            result.release()
            result = null
            val excludedArea = "300x300,340x300,340x340,300x340"
            val excludedGroups = (listOf(excludedArea) + List(4) { fullBed }).joinToString("#")
            configFile.writeText(configured.replace(groupedAreas, excludedGroups))
            result = model.slice(configFile.absolutePath, output.absolutePath, null)
            val excludedErrorCode = result.getGCodeCheckErrorCode()
            assertTrue("the same physical toolhead must be rejected when its actual printable polygon excludes the model",
                excludedErrorCode and 1 != 0)
        } finally {
            result?.release()
            model.release()
            source.delete()
            output.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test(timeout = 120_000)
    fun disjointExtruderPrintableAreasReturnPrimeTowerValidationInsteadOfCrashing() = withToolChangerProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = requireNotNull(SliceBeam.CONFIG)
        val process = requireNotNull(config.findPrint("0.20mm Standard"))
        val previousPrimeTower = process["enable_prime_tower"]
        process.put("enable_prime_tower", "1")

        val source = copyCube(context, "disjoint-extruder-printable-areas.stl")
        val output = File(context.cacheDir, "disjoint-extruder-printable-areas.gcode")
        val model = Model(source)
        try {
            val secondObject = Model(source)
            try {
                model.addObject(secondObject, 0)
            } finally {
                secondObject.release()
            }
            model.translate(1, 35.0, 0.0, 0.0)
            model.setUniformFilament(0, 1)
            model.setUniformFilament(1, 2)
            SliceBeam.genCurrentConfig(model)

            val configFile = SliceBeam.getCurrentConfigFile()
            val generated = configFile.readText()
            val left = "0x0,100x0,100x350,0x350"
            val right = "200x0,350x0,350x350,200x350"
            val fullBed = "0x0,350x0,350x350,0x350"
            val groupedAreas = (listOf(left, right) + List(3) { fullBed }).joinToString("#")
            val configured = if (Regex("(?m)^extruder_printable_area\\s*=").containsMatchIn(generated)) {
                generated.replace(Regex("(?m)^extruder_printable_area\\s*=.*$"), "extruder_printable_area = $groupedAreas")
            } else {
                "$generated\\nextruder_printable_area = $groupedAreas\\n"
            }
            configFile.writeText(configured)

            val failure = runCatching {
                model.slice(configFile.absolutePath, output.absolutePath, null)
            }.exceptionOrNull()
            assertNotNull("disjoint physical printable areas with a required prime tower must be rejected", failure)
            assertTrue(
                "the slicer should report the missing common printable area instead of crashing: $failure",
                failure.toString().contains("share no common area", ignoreCase = true),
            )
        } finally {
            process.put("enable_prime_tower", previousPrimeTower)
            model.release()
            source.delete()
            output.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test
    fun modifierVolumesSurviveThreeMfAndChangeNativeSliceOutput() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = copyCube(context, "modifier-slice-source.stl")
        val project = File(context.cacheDir, "modifier-slice-roundtrip.3mf")
        val baselineFile = File(context.cacheDir, "modifier-slice-baseline.gcode")
        val modifiedFile = File(context.cacheDir, "modifier-slice-modified.gcode")
        val model = Model(source)
        var restored: Model? = null
        var baselineResult: GCodeProcessorResult? = null
        var modifiedResult: GCodeProcessorResult? = null
        try {
            model.setUniformFilament(0, 1)
            SliceBeam.genCurrentConfig(model)
            baselineResult = model.slice(SliceBeam.getCurrentConfigFile().absolutePath, baselineFile.absolutePath, null)
            val baselineExtrusions = baselineFile.readLines().count { it.startsWith("G1") && it.contains(" E") }
            assertTrue("the baseline test cube should produce extrusion", baselineExtrusions > 0)
            baselineResult.release()
            baselineResult = null

            val min = Vec3d(); val max = Vec3d(); val translation = Vec3d()
            model.getBoundingBoxExact(0, min, max)
            model.getTranslation(0, translation)
            val sizeX = max.x - min.x
            val sizeY = max.y - min.y
            val sizeZ = max.z - min.z
            val cutWidth = sizeX * 0.35
            val cutDepth = sizeY * 0.35
            assertTrue("native negative volume is created", model.addModifierBox(
                0, 1,
                min.x - translation.x + sizeX * 0.325,
                min.y - translation.y + sizeY * 0.325,
                min.z - translation.z - 0.5,
                cutWidth, cutDepth, sizeZ + 1.0,
            ) >= 0)
            assertTrue("native support blocker is created", model.addModifierBox(
                0, 3,
                min.x - translation.x + sizeX * 0.1,
                min.y - translation.y + sizeY * 0.1,
                min.z - translation.z,
                sizeX * 0.2, sizeY * 0.2, sizeZ,
            ) >= 0)
            SliceBeam.genCurrentConfig(model)
            model.export3mf(SliceBeam.getCurrentConfigFile().absolutePath, project.absolutePath)

            restored = Model(project)
            val modifierVolumes = restored.getModifierVolumeIndices(0)
            assertEquals("the slice input must retain both modifier types", 2, modifierVolumes.size)
            assertEquals(1, restored.getVolumeType(0, modifierVolumes[0]))
            assertEquals(3, restored.getVolumeType(0, modifierVolumes[1]))
            SliceBeam.genCurrentConfig(restored)
            modifiedResult = restored.slice(SliceBeam.getCurrentConfigFile().absolutePath, modifiedFile.absolutePath, null)
            val modifiedGcode = modifiedFile.readLines()
            assertTrue("modifier project should still produce printable extrusion",
                modifiedGcode.any { it.startsWith("G1") && it.contains(" E") })
            val modifiedExtrusions = modifiedGcode.count { it.startsWith("G1") && it.contains(" E") }
            assertTrue("the negative volume must affect generated toolpaths", modifiedExtrusions != baselineExtrusions)
        } finally {
            baselineResult?.release()
            modifiedResult?.release()
            restored?.release()
            model.release()
            source.delete(); project.delete(); baselineFile.delete(); modifiedFile.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test
    fun physicalExtruderMapReachesNativeSliceAndLayerChangeGcode() = withToolChangerProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = requireNotNull(SliceBeam.CONFIG)
        val printer = requireNotNull(config.findPrinter("MyToolChanger 0.4 nozzle"))
        printer.put("physical_extruder_map", "1,0,0,0,0")
        printer.put("layer_change_gcode", "; PHYSICAL=[most_used_physical_extruder_id]")

        val source = copyCube(context, "physical-extruder-map-source.stl")
        val output = File(context.cacheDir, "physical-extruder-map.gcode")
        val model = Model(source)
        var firstResult: GCodeProcessorResult? = null
        var secondResult: GCodeProcessorResult? = null
        try {
            model.setUniformFilament(0, 1)
            SliceBeam.genCurrentConfig(model)
            val nativeConfig = SliceBeam.getCurrentConfigFile().readText()
            assertTrue("the logical-to-physical map must reach the native slice config",
                nativeConfig.contains("physical_extruder_map = 1,0,0,0,0"))

            firstResult = model.slice(SliceBeam.getCurrentConfigFile().absolutePath, output.absolutePath, null)
            val firstGcode = output.readText()
            assertHasExtrusion(firstGcode)
            assertTrue("logical T0 must report mapped physical toolhead 1", firstGcode.contains("PHYSICAL=1"))
            firstResult.release()
            firstResult = null

            model.setUniformFilament(0, 2)
            SliceBeam.genCurrentConfig(model)
            secondResult = model.slice(SliceBeam.getCurrentConfigFile().absolutePath, output.absolutePath, null)
            val secondGcode = output.readText()
            assertHasExtrusion(secondGcode)
            assertTrue("logical T1 must report mapped physical toolhead 0", secondGcode.contains("PHYSICAL=0"))
        } finally {
            firstResult?.release()
            secondResult?.release()
            model.release()
            source.delete()
            output.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test
    fun temperatureTowerTargetsTheAssignedToolOnEveryLayer() = withToolChangerProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = copyCube(context, "orca25-temperature-tower-tool-source.stl")
        val output = File(context.cacheDir, "orca25-temperature-tower-tool.gcode")
        val model = Model(source)
        var result: GCodeProcessorResult? = null
        try {
            // T1 is a different heated tool from the printer's first nozzle.
            model.setUniformFilament(0, 2)
            SliceBeam.genCurrentConfig(model)
            result = model.slice(
                SliceBeam.getCurrentConfigFile().absolutePath,
                output.absolutePath,
                null,
                6, // CalibMode::Calib_Temp_Tower
                200.0,
                220.0,
                5.0,
            )
            val temperatureChanges = output.readLines().filter { it.startsWith("M104 S") }
            assertTrue("the calibration must emit changing layer temperatures", temperatureChanges.size >= 2)
            assertTrue(
                "each temperature tower step must explicitly address assigned tool T1: $temperatureChanges",
                temperatureChanges.all { Regex("\\bT1\\b").containsMatchIn(it) },
            )
        } finally {
            result?.release()
            model.release()
            source.delete()
            output.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test
    fun objectsWithDifferentNozzleLayerLimitsCanSharePrimeTower() = withToolChangerProfile {
        val limits = Native.object_nozzle_layer_limits_regression()
        assertEquals("first tool uses its own minimum layer limit", 0.04, limits[0], 1e-6)
        assertEquals("first tool uses its own maximum layer limit", 0.48, limits[1], 1e-6)
        assertEquals("third tool uses its own minimum layer limit", 0.12, limits[2], 1e-6)
        assertEquals("third tool uses its own maximum layer limit", 0.24, limits[3], 1e-6)

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = requireNotNull(SliceBeam.CONFIG)
        val printer = requireNotNull(config.findPrinter("MyToolChanger 0.4 nozzle"))
        val process = requireNotNull(config.findPrint("0.20mm Standard"))
        // The first and third logical nozzles intentionally have different layer limits.
        printer.put("min_layer_height", "0.04,0.08,0.12,0.16,0.20")
        printer.put("max_layer_height", "0.48,0.32,0.24,0.16,0.60")
        process.put("layer_height", "0.12")
        process.put("initial_layer_print_height", "0.12")
        process.put("enable_prime_tower", "1")

        val source = copyCube(context, "orca25-object-nozzle-layer-limits.stl")
        val output = File(context.cacheDir, "orca25-object-nozzle-layer-limits.gcode")
        val model = Model(source)
        var result: GCodeProcessorResult? = null
        try {
            model.addObject(model, 0)
            model.translate(1, 35.0, 0.0, 0.0)
            model.rotate(1, 0.0, 0.0, 12.0)
            model.setExtruder(0, 1)
            model.setExtruder(1, 3)
            SliceBeam.genCurrentConfig(model)
            val configFile = SliceBeam.getCurrentConfigFile()
            assertTrue(configFile.readText().contains("min_layer_height = 0.04,0.08,0.12,0.16,0.20"))
            result = model.slice(configFile.absolutePath, output.absolutePath, null)
            val gcode = output.readText()
            assertTrue("Both assigned filaments must be emitted", Regex("(?m)^T0(?:\\s|$)").containsMatchIn(gcode) &&
                Regex("(?m)^T2(?:\\s|$)").containsMatchIn(gcode))
            assertTrue("Prime-tower-compatible objects must complete slicing", gcode.contains("; total layer number:"))
            assertHasExtrusion(gcode)
        } finally {
            result?.release()
            model.release()
            source.delete()
            output.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test(timeout = 180_000)
    fun preciseZSliverLayersJoinThePrimeTowerLayerBelowAndStillSlice() = withToolChangerProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = requireNotNull(SliceBeam.CONFIG)
        val printer = requireNotNull(config.findPrinter("MyToolChanger 0.4 nozzle"))
        val process = requireNotNull(config.findPrint("0.20mm Standard"))
        printer.put("min_layer_height", "0.07,0.07,0.07,0.07,0.07")
        process.put("layer_height", "0.2")
        process.put("initial_layer_print_height", "0.2")
        process.put("precise_z_height", "1")
        process.put("enable_prime_tower", "1")

        val source = copyCube(context, "orca25-precise-z-prime-tower-source.stl")
        val output = File(context.cacheDir, "orca25-precise-z-prime-tower.gcode")
        val model = Model(source)
        var result: GCodeProcessorResult? = null
        try {
            model.addObject(model, 0)
            // The second copy ends at 10.004 mm. Its final precise-Z layers sit only microns
            // above the first object's regular layer grid and are below the 0.07 mm nozzle floor.
            model.scale(1, 1.0, 1.0, 0.5002)
            model.translate(1, 35.0, 0.0, 0.0)
            model.setUniformFilament(0, 1)
            model.setUniformFilament(1, 2)
            SliceBeam.genCurrentConfig(model)
            val generatedConfig = SliceBeam.getCurrentConfigFile()
            assertTrue("precise-Z regression must reach the native slicer", generatedConfig.readText().contains("precise_z_height = 1"))
            result = model.slice(generatedConfig.absolutePath, output.absolutePath, null)

            val gcode = output.readText()
            assertHasExtrusion(gcode)
            assertTrue("both assigned tools must be used", Regex("(?m)^T0(?:\\s|$)").containsMatchIn(gcode) &&
                Regex("(?m)^T1(?:\\s|$)").containsMatchIn(gcode))
            assertTrue("the slice must contain prime-tower purge output", gcode.contains("Prime tower", ignoreCase = true))
            assertTrue("the complete layer table must be emitted", gcode.contains("; total layer number:"))
            assertTrue("purge tower generation must not report an out-of-bed or invalid-toolchange error",
                !gcode.contains("partially outside the printable area", ignoreCase = true) &&
                    !gcode.contains("asked to do a toolchange it didn't expect", ignoreCase = true))
            val objectLayerZ = layerZValues(gcode)
            var inPrimeTower = false
            var currentZ = 0.0
            val primeTowerExtrusionZ = mutableListOf<Double>()
            val zToken = Regex("(?:^|\\s)Z(-?[0-9]+(?:\\.[0-9]+)?)")
            val extrusionToken = Regex("(?:^|\\s)E[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)")
            gcode.lineSequence().forEach { line ->
                if (line.startsWith(";TYPE:") || line.startsWith("; FEATURE:"))
                    inPrimeTower = line.contains("prime tower", ignoreCase = true)
                zToken.find(line)?.let { currentZ = it.groupValues[1].toDouble() }
                if (inPrimeTower && line.startsWith("G1 ") && extrusionToken.containsMatchIn(line) &&
                    (line.contains(" X") || line.contains(" Y")))
                    primeTowerExtrusionZ += currentZ
            }
            assertTrue("the emitted G-code must contain actual prime-tower extrusion moves", primeTowerExtrusionZ.isNotEmpty())
            primeTowerExtrusionZ.forEach { towerZ ->
                assertTrue("tower extrusion at Z=$towerZ must sit on an object layer, not on a precise-Z sliver",
                    objectLayerZ.any { kotlin.math.abs(it - towerZ) <= 0.002 })
            }
            Log.i("NativeSliceDeviceTest", "PASS precise-Z prime-tower sliver layers joined; output=${output.length()} bytes")
            generatedConfig.delete()
        } finally {
            result?.release()
            model.release()
            source.delete()
            output.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test(timeout = 120_000)
    fun toolchangerSkipsFinalWipeTowerUnloadWhenTheTowerStopsBelowTheModel() {
        assertArrayEquals(
            "SEMM, or a tower reaching the final layer can emit a final purge without ramming",
            intArrayOf(0, 1, 1, 1, 1),
            Native.short_wipe_tower_final_unload_regression(),
        )
    }

    @Test(timeout = 120_000)
    fun rammingToolChangerRunsFinalUnloadAtTheTopWhenItsTowerStopsEarly() = withToolChangerProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val process = requireNotNull(SliceBeam.CONFIG?.findPrint("0.20mm Standard"))
        val filament = requireNotNull(SliceBeam.CONFIG?.findFilament("Generic PLA"))
        process.put("enable_prime_tower", "1")
        process.put("wipe_tower_type", "type2")
        filament.put("filament_multitool_ramming", "1")
        filament.put("filament_multitool_ramming_volume", "5")
        filament.put("filament_multitool_ramming_flow", "15")
        val source = copyCube(context, "type2-short-tower-ramming-source.stl")
        val model = Model(source)
        val output = File(context.cacheDir, "type2-short-tower-ramming.gcode")
        var result: GCodeProcessorResult? = null
        try {
            model.addObject(model, 0)
            model.translate(1, 35.0, 0.0, 0.0)
            model.scale(1, 1.0, 1.0, 0.25)
            model.ensureOnBed(1)
            model.setUniformFilament(0, 1)
            model.setUniformFilament(1, 2)
            SliceBeam.genCurrentConfig(model)
            result = model.slice(SliceBeam.getCurrentConfigFile().absolutePath, output.absolutePath, null)
            val gcode = output.readText()
            assertHasExtrusion(gcode)
            assertTrue("the type-2 prime tower must still be emitted", gcode.contains("WIPE_TOWER_START"))
            val layerHeights = layerZValues(gcode)
            val zToken = Regex("(?:^|\\s)Z(-?[0-9]+(?:\\.[0-9]+)?)")
            val eToken = Regex("(?:^|\\s)E[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)")
            var currentZ = 0.0
            val finalLayerTowerExtrusions = mutableListOf<Double>()
            var currentFeatureIsTower = false
            gcode.lineSequence().forEach { line ->
                if (line.startsWith(";TYPE:") || line.startsWith("; FEATURE:"))
                    currentFeatureIsTower = line.contains("prime tower", ignoreCase = true)
                zToken.find(line)?.let { currentZ = it.groupValues[1].toDouble() }
                if (currentFeatureIsTower && line.startsWith("G1 ") && eToken.containsMatchIn(line) &&
                    (line.contains(" X") || line.contains(" Y"))) {
                    finalLayerTowerExtrusions += currentZ
                }
            }
            val highestModelLayer = layerHeights.maxOrNull()!!
            assertTrue("the model must continue above the early-ending tower", highestModelLayer > 2.0)
            assertTrue(
                "ramming must add the final tower unload at the model top; tower Zs=${finalLayerTowerExtrusions.takeLast(8)}, top=$highestModelLayer",
                finalLayerTowerExtrusions.any { kotlin.math.abs(it - highestModelLayer) <= 1e-3 },
            )
        } finally {
            result?.release()
            model.release()
            source.delete()
            output.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test
    fun reusedPrintReenablesPrimeTowerWhenNextModelUsesAnotherFilament() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val process = requireNotNull(SliceBeam.CONFIG?.findPrint("0.16mm Standard"))
        process.put("enable_prime_tower", "1")
        val source = copyCube(context, "prime-tower-reused-print-source.stl")
        val previousModel = Model(source)
        val nextModel = Model(source)
        try {
            previousModel.setUniformFilament(0, 1)
            nextModel.addObject(nextModel, 0)
            nextModel.translate(1, 35.0, 0.0, 0.0)
            nextModel.setUniformFilament(0, 1)
            nextModel.setUniformFilament(1, 2)
            SliceBeam.genCurrentConfig(nextModel)

            val states = Native.reused_print_prime_tower_regression(
                previousModel.pointer,
                nextModel.pointer,
                SliceBeam.getCurrentConfigFile().absolutePath,
            )
            assertEquals("the first single-filament model should normalize the tower off", 0, states[0])
            assertEquals("the first model should use one tool", 1, states[1])
            assertEquals("the first model should slice without a tower", 0, states[2])
            assertEquals("the next two-filament model should re-enable its configured tower", 1, states[3])
            assertEquals("the second model's rebuilt regions should expose both tools", 2, states[4])
            assertEquals("slicing the next model should generate its prime tower", 1, states[5])
        } finally {
            previousModel.release()
            nextModel.release()
            source.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test(timeout = 120_000)
    fun wipeTowerUsageIsCountedForThePrinterTagDialectOnBothTowerTypes() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val process = requireNotNull(SliceBeam.CONFIG?.findPrint("0.16mm Standard"))
        process.put("enable_prime_tower", "1")
        val source = copyCube(context, "wipe-tower-printer-tag-source.stl")
        val model = Model(source)
        val configFile = SliceBeam.getCurrentConfigFile()
        try {
            model.addObject(model, 0)
            model.translate(1, 35.0, 0.0, 0.0)
            model.setUniformFilament(0, 1)
            model.setUniformFilament(1, 2)

            for (towerType in listOf("type1", "type2")) {
                process.put("wipe_tower_type", towerType)
                SliceBeam.genCurrentConfig(model)
                val gcodeFile = File(context.cacheDir, "wipe-tower-printer-tag-$towerType.gcode")
                var result: GCodeProcessorResult? = null
                try {
                    result = model.slice(configFile.absolutePath, gcodeFile.absolutePath, null)
                    val gcode = gcodeFile.readText()
                    assertHasExtrusion(gcode)
                    assertTrue("$towerType must emit prime-tower extrusion", gcode.contains("Prime tower", ignoreCase = true))
                    val wipeTowerUsage = result.getUsedFilamentMM(GCodeViewer.EXTRUSION_ROLE_WIPE_TOWER)
                    assertTrue(
                        "$towerType tower filament must be classified using the non-Bambu printer tag dialect; usage=$wipeTowerUsage",
                        wipeTowerUsage > 0.0,
                    )
                } finally {
                    result?.release()
                    gcodeFile.delete()
                }
            }
        } finally {
            model.release()
            source.delete()
            configFile.delete()
        }
    }

    @Test(timeout = 120_000)
    fun type2ToolChangerDoesNotPurgeAboveShortPrimeTower() = withToolChangerProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val process = requireNotNull(SliceBeam.CONFIG?.findPrint("0.20mm Standard"))
        process.put("enable_prime_tower", "1")
        process.put("wipe_tower_type", "type2")
        val source = copyCube(context, "type2-short-tower-source.stl")
        val model = Model(source)
        val output = File(context.cacheDir, "type2-short-tower.gcode")
        var result: GCodeProcessorResult? = null
        try {
            model.addObject(model, 0)
            model.translate(1, 35.0, 0.0, 0.0)
            model.scale(1, 1.0, 1.0, 0.25)
            model.ensureOnBed(1)
            model.setUniformFilament(0, 1)
            model.setUniformFilament(1, 2)
            SliceBeam.genCurrentConfig(model)
            result = model.slice(SliceBeam.getCurrentConfigFile().absolutePath, output.absolutePath, null)
            val gcode = output.readText()
            assertHasExtrusion(gcode)
            assertTrue("both assigned tools must be used", Regex("(?m)^T0(?:\\s|$)").containsMatchIn(gcode) &&
                Regex("(?m)^T1(?:\\s|$)").containsMatchIn(gcode))
            assertTrue("the type-2 prime tower must still be emitted for required toolchanges", gcode.contains("Prime tower", ignoreCase = true))
            val layerHeights = layerZValues(gcode)
            val zToken = Regex("(?:^|\\s)Z(-?[0-9]+(?:\\.[0-9]+)?)")
            val eToken = Regex("(?:^|\\s)E[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)")
            var currentZ = 0.0
            val towerExtrusionZ = mutableListOf<Double>()
            var currentFeatureIsTower = false
            gcode.lineSequence().forEach { line ->
                if (line.startsWith(";TYPE:") || line.startsWith("; FEATURE:"))
                    currentFeatureIsTower = line.contains("prime tower", ignoreCase = true)
                zToken.find(line)?.let { currentZ = it.groupValues[1].toDouble() }
                if (currentFeatureIsTower && line.startsWith("G1 ") && eToken.containsMatchIn(line) &&
                    (line.contains(" X") || line.contains(" Y"))) towerExtrusionZ += currentZ
            }
            assertTrue("tower feature must include toolchange extrusion", towerExtrusionZ.isNotEmpty())
            val highestTowerZ = towerExtrusionZ.maxOrNull()!!
            val highestModelLayer = layerHeights.maxOrNull()!!
            assertTrue("short type-2 tower must not receive a final purge above its last physical layer: tower=$highestTowerZ model=$highestModelLayer",
                highestTowerZ <= highestModelLayer + 1e-3)
            assertTrue("output must contain final object layers above the short tower", highestModelLayer > 2.0)
        } finally {
            result?.release()
            model.release()
            source.delete()
            output.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test(timeout = 180_000)
    fun qidiCompatibleType1AndType2TowersReportPerFilamentPurgeVolumes() = withToolChangerProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val process = requireNotNull(SliceBeam.CONFIG?.findPrint("0.20mm Standard"))
        process.put("enable_prime_tower", "1")
        val source = copyCube(context, "wipe-tower-filament-statistics.stl")
        val model = Model(source)
        val output = File(context.cacheDir, "wipe-tower-filament-statistics.gcode")
        val configFile = SliceBeam.getCurrentConfigFile()
        try {
            model.addObject(model, 0)
            model.translate(1, 35.0, 0.0, 0.0)
            model.setUniformFilament(0, 1)
            model.setUniformFilament(1, 2)
            for (towerType in listOf("type1", "type2")) {
                process.put("wipe_tower_type", towerType)
                SliceBeam.genCurrentConfig(model)
                val result = model.slice(configFile.absolutePath, output.absolutePath, null)
                try {
                    val gcode = output.readText()
                    assertHasExtrusion(gcode)
                    assertTrue("$towerType must generate a wipe tower", gcode.contains("WIPE_TOWER_START"))
                    for (filamentIndex in 0..1) {
                        val volume = result.getWipeTowerFilamentVolumeMm3(filamentIndex)
                        assertTrue("$towerType purge tower must report filament ${filamentIndex + 1}: $volume", volume > 0.0)
                    }
                } finally {
                    result.release()
                }
            }
        } finally {
            model.release()
            source.delete()
            output.delete()
            configFile.delete()
        }
    }
    @Test(timeout = 120_000)
    fun klipperWipeTowerOmitsNonessentialPlannerStopsButKeepsToolchangeSync() = withToolChangerProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = requireNotNull(SliceBeam.CONFIG)
        val process = requireNotNull(config.findPrint("0.20mm Standard"))
        process.put("enable_prime_tower", "1")
        process.put("change_filament_gcode", "; TOOLCHANGE_MARK\n")
        val source = copyCube(context, "klipper-wipe-tower-sync-source.stl")
        val model = Model(source)
        val configFile = SliceBeam.getCurrentConfigFile()
        val output = File(context.cacheDir, "klipper-wipe-tower-sync.gcode")
        try {
            model.addObject(model, 0)
            model.translate(1, 35.0, 0.0, 0.0)
            model.setUniformFilament(0, 1)
            model.setUniformFilament(1, 2)

            for (towerType in listOf("type1", "type2")) {
                process.put("wipe_tower_type", towerType)
                SliceBeam.genCurrentConfig(model)
                val result = model.slice(configFile.absolutePath, output.absolutePath, null)
                try {
                    val gcode = output.readText()
                    assertHasExtrusion(gcode)
                    assertTrue("$towerType must emit a wipe-tower toolchange", gcode.contains("; CP TOOLCHANGE END"))
                    assertTrue("$towerType must run the custom toolchange marker", gcode.contains("; TOOLCHANGE_MARK"))
                    val towerStart = gcode.indexOf("WIPE_TOWER_START")
                    val towerEnd = gcode.indexOf("WIPE_TOWER_END", towerStart)
                    assertTrue("$towerType must emit a complete wipe-tower region", towerStart >= 0 && towerEnd > towerStart)
                    val tower = gcode.substring(towerStart, towerEnd)
                    assertFalse("$towerType must not retain non-Klipper G4 S0 planner syncs", tower.contains("G4 S0"))
                    assertFalse("$towerType tower travel lines must remain separated for coordinate post-processing",
                        Regex("(?m)^G1 Y").containsMatchIn(tower))
                    assertFalse(
                        "$towerType must not emit a redundant planner sync after the wipe-tower toolchange",
                        Regex("(?:G4 S0|M400)\\s*G92 E0\\s*; CP TOOLCHANGE END").containsMatchIn(gcode),
                    )
                    if (towerType == "type2") {
                        val towerStart = gcode.indexOf("WIPE_TOWER_START")
                        assertTrue("type2 must emit the wipe tower", towerStart >= 0)
                        val marker = gcode.indexOf("; TOOLCHANGE_MARK", towerStart)
                        assertTrue("type2 tower must contain a custom toolchange marker", marker >= 0)
                        val previousSync = gcode.lastIndexOf("M400", marker)
                        assertTrue("type2 must synchronize the unload before custom toolchange G-code; previousSync=$previousSync marker=$marker nearby=" +
                            gcode.substring((marker - 260).coerceAtLeast(0), marker.coerceAtLeast(0)),
                            previousSync >= 0 && marker - previousSync < 600)
                    }
                } finally {
                    result.release()
                }
            }
        } finally {
            model.release()
            source.delete()
            output.delete()
            configFile.delete()
        }
    }

    @Test
    fun tooThinObjectWithRaftReturnsSlicingErrorInsteadOfCrashing() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = requireNotNull(SliceBeam.CONFIG)
        val process = requireNotNull(config.findPrint("0.16mm Standard"))
        val modelFile = copyCube(context, "orca25-too-thin-with-raft.stl")
        val gcodeFile = File(context.cacheDir, "orca25-too-thin-with-raft.gcode")
        var model: Model? = null
        var generatedConfig: File? = null
        var sliceResult: GCodeProcessorResult? = null
        try {
            val loadedModel = Model(modelFile)
            model = loadedModel
            loadedModel.scale(0, 1.0, 1.0, 0.0001) // 20 mm cube -> 0.002 mm tall, below min layer height.
            process.put("layer_height", "0.2")
            process.put("initial_layer_print_height", "0.2")
            process.put("min_layer_height", "0.07")

            for (raftLayers in listOf(0, 2)) {
                for (preciseZHeight in listOf(false, true)) {
                    process.put("raft_layers", raftLayers.toString())
                    process.put("precise_z_height", if (preciseZHeight) "1" else "0")
                    SliceBeam.genCurrentConfig(loadedModel)
                    val currentConfig = SliceBeam.getCurrentConfigFile()
                    generatedConfig = currentConfig
                    val failure = try {
                        sliceResult = loadedModel.slice(currentConfig.absolutePath, gcodeFile.absolutePath, null)
                        null
                    } catch (error: Slic3rRuntimeError) {
                        error
                    }
                    sliceResult?.release()
                    sliceResult = null
                    assertNotNull("empty layers should produce a normal slicing error (raft=$raftLayers, preciseZ=$preciseZHeight)", failure)
                    assertTrue("unexpected error: ${failure?.message}", failure?.message?.contains("No layers were detected") == true)
                    gcodeFile.delete()
                    currentConfig.delete()
                    generatedConfig = null
                }
            }
        } finally {
            sliceResult?.release()
            model?.release()
            modelFile.delete()
            generatedConfig?.delete()
            gcodeFile.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test
    fun zeroFlowRatioForUsedFilamentFailsClearlyWithoutInvalidGcode() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = requireNotNull(SliceBeam.CONFIG)
        val filament = requireNotNull(config.findFilament("Anycubic Generic PLA"))
        val originalFlowRatio = requireNotNull(filament["filament_flow_ratio"])
        val modelFile = copyCube(context, "zero-flow-ratio-validation.stl")
        val gcodeFile = File(context.cacheDir, "zero-flow-ratio-validation.gcode")
        var model: Model? = null
        var result: GCodeProcessorResult? = null
        var generatedConfig: File? = null
        try {
            filament.put("filament_flow_ratio", "0")
            model = Model(modelFile)
            SliceBeam.genCurrentConfig(model)
            val currentConfig = SliceBeam.getCurrentConfigFile()
            generatedConfig = currentConfig
            assertTrue("the regression must reach native slicing with a zero ratio",
                Regex("(?m)^filament_flow_ratio\\s*=\\s*0(?:\\.0*)?(?:,|$)").containsMatchIn(currentConfig.readText()))

            val failure = try {
                result = model.slice(currentConfig.absolutePath, gcodeFile.absolutePath, null)
                null
            } catch (error: Slic3rRuntimeError) {
                error
            }
            assertNotNull("a used zero-flow filament must be rejected before G-code generation", failure)
            assertTrue("the validation message should explain the invalid flow ratio: ${failure?.message}",
                failure?.message?.contains("must be greater than 0", ignoreCase = true) == true)
            if (gcodeFile.isFile) {
                val gcode = gcodeFile.readText()
                assertFalse("invalid NaN extrusion must never be written", gcode.contains("NaN", ignoreCase = true))
                assertFalse("the prior integer-overflow extrusion must never be written", gcode.contains("92233720368547"))
            }
        } finally {
            result?.release()
            model?.release()
            filament.put("filament_flow_ratio", originalFlowRatio)
            modelFile.delete()
            generatedConfig?.delete()
            gcodeFile.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test
    fun adaptivePressureAdvanceUsesWallSpeedWhenWallInheritsTravelFeedrate() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = requireNotNull(SliceBeam.CONFIG)
        val process = requireNotNull(config.findPrint("0.16mm Standard"))
        val filament = requireNotNull(config.findFilament("Anycubic Generic PLA"))
        process.put("wall_loops", "1")
        process.put("sparse_infill_density", "0%")
        process.put("top_shell_layers", "0")
        process.put("bottom_shell_layers", "0")
        process.put("skirt_loops", "0")
        process.put("brim_type", "no_brim")
        process.put("outer_wall_speed", "150")
        process.put("inner_wall_speed", "150")
        process.put("initial_layer_speed", "30")
        process.put("slow_down_layers", "0")
        process.put("travel_speed", "150")
        process.put("initial_layer_travel_speed", "150")
        process.put("enable_overhang_speed", "0")
        process.put("wipe", "0")
        process.put("retraction_length", "0")
        process.put("gcode_comments", "1")
        filament.put("enable_pressure_advance", "1")
        filament.put("adaptive_pressure_advance", "1")
        filament.put("adaptive_pressure_advance_model", "0.08,1,4000\\n0.04,20,4000\\n0.08,1,100000\\n0.04,20,100000")
        filament.put("pressure_advance", "0.08")
        filament.put("filament_max_volumetric_speed", "200")
        filament.put("slow_down_for_layer_cooling", "0")
        filament.put("slow_down_layer_time", "0")

        val modelFile = copyCube(context, "adaptive-pa-modal-feedrate.stl")
        val gcodeFile = File(context.cacheDir, "adaptive-pa-modal-feedrate.gcode")
        var model: Model? = null
        var result: GCodeProcessorResult? = null
        var generatedConfig: File? = null
        try {
            model = Model(modelFile)
            model.ensureOnBed(0)
            SliceBeam.genCurrentConfig(model)
            val currentConfig = SliceBeam.getCurrentConfigFile()
            generatedConfig = currentConfig
            val nativeConfig = currentConfig.readText()
            assertTrue(nativeConfig.contains("adaptive_pressure_advance = 1"))
            assertTrue(nativeConfig.contains("outer_wall_speed = 150"))
            assertTrue(nativeConfig.contains("initial_layer_speed = 30"))
            assertTrue(nativeConfig.contains("travel_speed = 150"))
            result = model.slice(currentConfig.absolutePath, gcodeFile.absolutePath, null)
            val gcode = gcodeFile.readText()
            var currentType = ""
            var modalFeedrate = 0
            var pendingAdaptiveSpeed: Double? = null
            val inheritedWallSpeeds = mutableListOf<Double>()
            gcode.lineSequence().forEach { line ->
                if (line.startsWith(";TYPE:")) currentType = line.removePrefix(";TYPE:")
                if (line.startsWith("; APA Speed Used: "))
                    pendingAdaptiveSpeed = line.substringAfter(": ").toDouble()
                if (line.startsWith("G0 ") || line.startsWith("G1 ") || line.startsWith("G2 ") || line.startsWith("G3 ")) {
                    Regex("(?:^| )F([0-9]+)(?: |$)").find(line)?.groupValues?.get(1)?.toIntOrNull()?.let {
                        modalFeedrate = it
                    }
                    if (currentType == "Outer wall" && line.startsWith("G1 ") &&
                        line.contains(" X") && line.contains(" Y") && line.contains(" E") && !line.contains(" F") &&
                        modalFeedrate == 9000 && pendingAdaptiveSpeed != null) {
                        inheritedWallSpeeds += pendingAdaptiveSpeed!!
                        pendingAdaptiveSpeed = null
                    }
                }
            }
            assertTrue("fixture must contain an outer-wall extrusion inheriting the travel feedrate", inheritedWallSpeeds.isNotEmpty())
            assertTrue("adaptive PA speed must match the inherited 150 mm/s travel feedrate: $inheritedWallSpeeds",
                inheritedWallSpeeds.all { kotlin.math.abs(it - 150.0) < 0.01 })
        } finally {
            result?.release()
            model?.release()
            modelFile.delete()
            generatedConfig?.delete()
            gcodeFile.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test
    fun adaptivePressureAdvanceSliceWarnsWhenEnabledFilamentHasNoMeasurements() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val filament = requireNotNull(SliceBeam.CONFIG?.findFilament("Anycubic Generic PLA"))
        filament.put("enable_pressure_advance", "1")
        filament.put("adaptive_pressure_advance", "1")
        filament.put("adaptive_pressure_advance_model", "")

        val modelFile = copyCube(context, "adaptive-pa-empty-measurements.stl")
        val gcodeFile = File(context.cacheDir, "adaptive-pa-empty-measurements.gcode")
        var model: Model? = null
        var result: GCodeProcessorResult? = null
        var generatedConfig: File? = null
        try {
            model = Model(modelFile)
            model.ensureOnBed(0)
            SliceBeam.genCurrentConfig(model)
            generatedConfig = SliceBeam.getCurrentConfigFile()
            result = model.slice(generatedConfig.absolutePath, gcodeFile.absolutePath, null)

            assertEquals(
                listOf(AdaptivePAModelValidator.SliceWarning(
                    filamentNumber = 1,
                    lineNumber = null,
                    problem = AdaptivePAModelValidator.Problem.NO_MEASUREMENTS,
                )),
                result.adaptivePAModelWarnings,
            )
            assertTrue("missing calibration data remains advisory; slicing still produces G-code",
                gcodeFile.isFile && gcodeFile.length() > 0L)
        } finally {
            result?.release()
            model?.release()
            modelFile.delete()
            generatedConfig?.delete()
            gcodeFile.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test
    fun cutDowelToleranceDoesNotShiftTheCutPlaneAnchor() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelFile = copyCube(context, "orca25-centered-dowel-cut.stl")
        val model = Model(modelFile)
        try {
            // Cylinder geometry starts at its local Z origin; place its lower end 2 mm
            // below the 10 mm cut plane so both cut halves contain connector geometry.
            Native.model_add_connector(model.pointer, 0, 10.0, 10.0, 8.0, 2f, 4f, 1)
            assertEquals(1, model.getCutConnectorCount(0))
            assertTrue("the cube should split through the centered dowel", model.cut(0, 10.0, 0.0, 0.0, true, true))
            assertEquals("the cut should retain both halves and the generated dowel", 3, model.objectsCount)

            val upperConnector = model.getModifierVolumeIndices(0).single()
            val lowerConnector = model.getModifierVolumeIndices(1).single()
            val upperHeight = requireNotNull(model.getModifierVolumeBoxState(0, upperConnector)).size[2]
            val lowerHeight = requireNotNull(model.getModifierVolumeBoxState(1, lowerConnector)).size[2]
            assertTrue("both halves of a cut-plane dowel must keep positive depth", upperHeight > 1.5 && lowerHeight > 1.5)
            assertEquals("dowel holes should break through the cut face without shortening", 2.05, lowerHeight, 0.02)
            assertEquals("the opposite dowel hole should retain its far-end tolerance", 2.15, upperHeight, 0.02)
        } finally {
            model.release()
            modelFile.delete()
        }
    }

    @Test
    fun nativeDeleteTranslateAndBedPlacementRejectInvalidObjectsAndValues() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelFile = copyAsset(context, "models/sphere.stl", "native-invalid-edit-requests.stl")
        val model = Model(modelFile)
        try {
            val beforeMin = Vec3d()
            val beforeMax = Vec3d()
            model.getBoundingBoxExact(0, beforeMin, beforeMax)
            val beforeBounds = doubleArrayOf(
                beforeMin.x, beforeMin.y, beforeMin.z,
                beforeMax.x, beforeMax.y, beforeMax.z,
            )

            // Exercise the JNI boundary itself; the Kotlin wrapper already filters these calls,
            // but stale native indices must remain harmless even if reached below that layer.
            Native.model_delete_object(model.pointer, -1)
            Native.model_delete_object(model.pointer, model.objectsCount)
            Native.model_ensure_on_bed(model.pointer, -1)
            Native.model_ensure_on_bed(model.pointer, model.objectsCount)
            Native.model_translate(model.pointer, -1, 10.0, 20.0, 30.0)
            Native.model_translate(model.pointer, model.objectsCount, 10.0, 20.0, 30.0)
            Native.model_translate(model.pointer, 0, Double.NaN, 0.0, 0.0)
            Native.model_translate(model.pointer, 0, 0.0, Double.POSITIVE_INFINITY, 0.0)

            Native.model_delete_object(0L, 0)
            Native.model_ensure_on_bed(0L, 0)
            Native.model_translate(0L, 0, 1.0, 2.0, 3.0)

            assertEquals("invalid native edits must preserve object count", 1, model.objectsCount)
            val afterMin = Vec3d()
            val afterMax = Vec3d()
            model.getBoundingBoxExact(0, afterMin, afterMax)
            assertArrayEquals("invalid native edits must preserve geometry and placement", beforeBounds,
                doubleArrayOf(afterMin.x, afterMin.y, afterMin.z, afterMax.x, afterMax.y, afterMax.z), 0.0)

            // Valid native entry points still perform their intended operation.
            Native.model_translate(model.pointer, 0, 0.0, 0.0, 5.0)
            model.getBoundingBoxExact(0, afterMin, afterMax)
            assertEquals("valid native Z translation remains available", beforeBounds[2] + 5.0, afterMin.z, 0.01)
            Native.model_ensure_on_bed(model.pointer, 0)
            model.getBoundingBoxExact(0, afterMin, afterMax)
            assertEquals("valid native bed placement seats the model at Z zero", 0.0, afterMin.z, 0.01)
        } finally {
            model.release()
            modelFile.delete()
        }
    }

    @Test
    fun nativeCutAndConnectorEditsRejectInvalidInputsAndKeepValidRoundTripPrintable() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelFile = copyCube(context, "native-connector-validation.stl")
        val projectFile = File(context.cacheDir, "native-connector-validation.3mf")
        val model = Model(modelFile)
        var restored: Model? = null
        var configFile: File? = null
        try {
            val min = Vec3d()
            val max = Vec3d()
            model.getBoundingBoxExact(0, min, max)
            val beforeBounds = doubleArrayOf(min.x, min.y, min.z, max.x, max.y, max.z)
            val beforeVolume = model.getObjectVolume(0)
            val pointer = model.pointer

            // Cut must reject invalid heights/angles and an empty keep-mask before mutating input.
            assertFalse(Native.model_cut(pointer, 0, Double.NaN, 0.0, 0.0, true, true))
            assertFalse(Native.model_cut(pointer, 0, 10.0, Double.POSITIVE_INFINITY, 0.0, true, true))
            assertFalse(Native.model_cut(pointer, 0, 10.0, 0.0, 0.0, false, false))
            assertFalse(Native.model_cut(pointer, model.objectsCount, 10.0, 0.0, 0.0, true, true))
            assertFalse(Native.model_cut(0L, 0, 10.0, 0.0, 0.0, true, true))

            // Connector add/remove/clear calls are exercised directly at JNI, below Kotlin guards.
            Native.model_add_connector(pointer, 0, Double.NaN, 0.0, 0.0, 2f, 4f, 0)
            Native.model_add_connector(pointer, 0, 0.0, 0.0, 0.0, Float.POSITIVE_INFINITY, 4f, 0)
            Native.model_add_connector(pointer, model.objectsCount, 0.0, 0.0, 0.0, 2f, 4f, 0)
            Native.model_add_connector(0L, 0, 0.0, 0.0, 0.0, 2f, 4f, 0)
            Native.model_add_connector_on_plane(pointer, 0, 0.0, 0.0, 0.0, 2f, 4f, 0,
                0.0, Double.NEGATIVE_INFINITY)
            Native.model_add_connector_on_plane(pointer, model.objectsCount, 0.0, 0.0, 0.0, 2f, 4f, 0,
                0.0, 0.0)
            Native.model_add_connector_on_plane(0L, 0, 0.0, 0.0, 0.0, 2f, 4f, 0, 0.0, 0.0)
            Native.model_remove_connector(pointer, 0, -1)
            Native.model_remove_connector(pointer, model.objectsCount, 0)
            Native.model_remove_connector(0L, 0, 0)
            Native.model_clear_connectors(pointer, model.objectsCount)
            Native.model_clear_connectors(0L, 0)

            assertEquals("invalid requests must not create connector volumes", 0, model.getCutConnectorCount(0))
            assertEquals("invalid requests must preserve object count", 1, model.objectsCount)
            model.getBoundingBoxExact(0, min, max)
            assertArrayEquals("invalid requests must preserve exact object bounds", beforeBounds,
                doubleArrayOf(min.x, min.y, min.z, max.x, max.y, max.z), 0.001)
            assertEquals("invalid requests must preserve printable volume", beforeVolume, model.getObjectVolume(0), 0.001)

            // Valid connector add/remove/clear behavior remains intact for both placement modes.
            Native.model_add_connector(pointer, 0, 10.0, 10.0, 10.0, 2f, 4f, 1)
            assertEquals(1, model.getCutConnectorCount(0))
            Native.model_add_connector_on_plane(pointer, 0, 10.0, 10.0, 10.0, 2f, 4f, 2, 0.2, -0.1)
            assertEquals(2, model.getCutConnectorCount(0))
            Native.model_remove_connector(pointer, 0, 0)
            assertEquals(1, model.getCutConnectorCount(0))
            Native.model_clear_connectors(pointer, 0)
            assertEquals(0, model.getCutConnectorCount(0))
            assertEquals("removing connectors restores the original solid volume", beforeVolume,
                model.getObjectVolume(0), 0.001)

            configFile = SliceBeam.getCurrentConfigFile()
            model.export3mf(configFile!!.absolutePath, projectFile.absolutePath)
            restored = Model(projectFile, 1)
            assertEquals("connector cleanup must survive 3MF reload", 0, restored.getCutConnectorCount(0))
            assertHasExtrusion(sliceToGcode(context, restored, "native-connector-validation.gcode"))
        } finally {
            configFile?.delete()
            restored?.release()
            model.release()
            modelFile.delete()
            projectFile.delete()
        }
    }

    @Test
    fun invalidTransformAndSimplifyRequestsPreserveNativeModel() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelFile = copyAsset(context, "models/sphere.stl", "invalid-transform-edits.stl")
        val model = Model(modelFile)
        try {
            val originalTriangles = model.getTriangleCount(0)
            val originalVolume = model.getObjectVolume(0)
            val min = Vec3d()
            val max = Vec3d()
            model.getBoundingBoxExact(0, min, max)
            val originalBounds = doubleArrayOf(min.x, min.y, min.z, max.x, max.y, max.z)

            model.translate(-1, 10.0, 0.0, 0.0)
            model.translate(0, Double.NaN, 0.0, 0.0)
            model.scale(model.objectsCount, 2.0, 2.0, 2.0)
            model.scale(0, 1.0, Double.POSITIVE_INFINITY, 1.0)
            model.rotate(-1, 0.0, 0.0, 1.0)
            model.rotate(0, 0.0, Double.NaN, 0.0)
            model.ensureOnBed(model.objectsCount)
            model.resetRotation(-1)
            model.autoOrient(model.objectsCount)
            model.translate(Double.NaN, 0.0, 0.0)
            model.simplify(-1, 0.5f)
            assertEquals("invalid simplify ratio is rejected", 0, model.simplify(0, Float.NaN))
            assertEquals("out-of-range simplify ratio is rejected", 0, model.simplify(0, 1.1f))

            // Exercise the transformation and surface-orientation JNI boundaries directly.
            Native.model_scale(0L, 0, 2.0, 2.0, 2.0)
            Native.model_scale(model.pointer, model.objectsCount, 2.0, 2.0, 2.0)
            Native.model_scale(model.pointer, 0, Double.NaN, 2.0, 2.0)
            Native.model_rotate(0L, 0, 0.1, 0.2, 0.3)
            Native.model_rotate(model.pointer, model.objectsCount, 0.1, 0.2, 0.3)
            Native.model_rotate(model.pointer, 0, Double.POSITIVE_INFINITY, 0.0, 0.0)
            Native.model_auto_orient(0L, 0)
            Native.model_auto_orient(model.pointer, model.objectsCount)
            Native.model_flatten_rotate(0L, 0, 0L)
            Native.model_flatten_rotate(model.pointer, model.objectsCount, 1L)
            Native.model_flatten_rotate(model.pointer, 0, 0L)
            assertEquals("invalid split handle is rejected", 0, Native.model_split(0L, 0))
            assertEquals("invalid split index is rejected", 0, Native.model_split(model.pointer, model.objectsCount))
            assertFalse("invalid handedness query is safe", Native.model_is_left_handed(0L, 0))
            assertFalse("out-of-range handedness query is safe",
                Native.model_is_left_handed(model.pointer, model.objectsCount))
            assertEquals("invalid plane source returns an empty plane list", 0,
                Native.model_create_flatten_planes(0L, 0).size)
            assertEquals("out-of-range plane source returns an empty plane list", 0,
                Native.model_create_flatten_planes(model.pointer, model.objectsCount).size)

            assertEquals("invalid object triangle query is safe", 0, model.getTriangleCount(model.objectsCount))
            assertEquals("invalid object volume query is safe", 0.0, model.getObjectVolume(-1), 0.0)
            assertFalse("invalid object paint query is safe", model.hasPaint(model.objectsCount))
            assertEquals("invalid object paint-tool query is safe", 0, model.paintMaxFilament(-1))
            model.getBoundingBoxExact(model.objectsCount, min, max)
            assertArrayEquals("invalid exact bounds return an empty box", DoubleArray(6),
                doubleArrayOf(min.x, min.y, min.z, max.x, max.y, max.z), 0.0)
            model.getBoundingBoxApprox(-1, min, max)
            assertArrayEquals("invalid approximate bounds return an empty box", DoubleArray(6),
                doubleArrayOf(min.x, min.y, min.z, max.x, max.y, max.z), 0.0)
            val overlay = ru.ytkab0bp.slicebeam.slic3r.GLModel()
            try {
                assertEquals("invalid object paint overlay is safe", 0,
                    model.buildPaintOverlay(model.objectsCount, overlay, 1))
            } finally {
                overlay.release()
            }

            assertEquals(1, model.objectsCount)
            assertEquals(originalTriangles, model.getTriangleCount(0))
            assertEquals(originalVolume, model.getObjectVolume(0), 1e-6)
            model.getBoundingBoxExact(0, min, max)
            doubleArrayOf(min.x, min.y, min.z, max.x, max.y, max.z).forEachIndexed { axis, value ->
                assertEquals("invalid edits preserve bounds on axis $axis", originalBounds[axis], value, 1e-6)
            }
        } finally {
            model.release()
            modelFile.delete()
        }
    }

    @Test
    fun nativeSimplifyCopyAndTransformQueriesRejectInvalidRequestsSafely() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelFile = copyCube(context, "native-copy-query-validation.stl")
        val model = Model(modelFile)
        try {
            val pointer = model.pointer
            val originalVolume = model.getObjectVolume(0)
            val originalTriangles = model.getTriangleCount(0)

            // Call JNI directly so these guards protect against stale indices below Kotlin wrappers.
            assertEquals(0, Native.model_simplify(0L, 0, 0.5f))
            assertEquals(0, Native.model_simplify(pointer, model.objectsCount, 0.5f))
            assertEquals(0, Native.model_simplify(pointer, 0, Float.NaN))
            assertEquals(0, Native.model_simplify(pointer, 0, Float.POSITIVE_INFINITY))
            assertEquals(0, Native.model_simplify(pointer, 0, 0f))
            assertEquals(0, Native.model_simplify(pointer, 0, 1.01f))

            Native.model_add_object_from_another(0L, pointer, 0)
            Native.model_add_object_from_another(pointer, 0L, 0)
            Native.model_add_object_from_another(pointer, pointer, -1)
            Native.model_add_object_from_another(pointer, pointer, model.objectsCount)

            val emptyVector = DoubleArray(3)
            for (query in listOf(
                Native.model_get_rotation(0L, 0),
                Native.model_get_mirror(0L, 0),
                Native.model_get_scale(0L, 0),
                Native.model_get_translation(0L, 0),
                Native.model_get_rotation(pointer, -1),
                Native.model_get_mirror(pointer, model.objectsCount),
                Native.model_get_scale(pointer, -1),
                Native.model_get_translation(pointer, model.objectsCount),
            )) {
                assertArrayEquals(emptyVector, query, 0.0)
            }

            assertEquals("invalid copy/simplify requests must not append or alter objects", 1, model.objectsCount)
            assertEquals(originalTriangles, model.getTriangleCount(0))
            assertEquals(originalVolume, model.getObjectVolume(0), 1e-6)
            for (query in listOf(
                Native.model_get_rotation(pointer, 0),
                Native.model_get_mirror(pointer, 0),
                Native.model_get_scale(pointer, 0),
                Native.model_get_translation(pointer, 0),
            )) {
                assertEquals(3, query.size)
                assertTrue("valid transform queries must return finite values", query.all { it.isFinite() })
            }

            // Valid native object-copy behavior remains available after rejecting bad requests.
            Native.model_add_object_from_another(pointer, pointer, 0)
            assertEquals(2, model.objectsCount)
            assertEquals(originalVolume, model.getObjectVolume(1), 1e-6)
        } finally {
            model.release()
            modelFile.delete()
        }
    }

    @Test
    fun invalidDuplicateDeleteAndSplitRequestsPreserveNativeModel() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelFile = copyCube(context, "invalid-object-index-edits.stl")
        val model = Model(modelFile)
        try {
            val originalTriangles = model.getTriangleCount(0)
            val originalVolume = model.getObjectVolume(0)
            val originalBounds = DoubleArray(6).also { bounds ->
                val min = Vec3d()
                val max = Vec3d()
                model.getBoundingBoxExact(0, min, max)
                bounds[0] = min.x; bounds[1] = min.y; bounds[2] = min.z
                bounds[3] = max.x; bounds[4] = max.y; bounds[5] = max.z
            }

            assertEquals("linked duplication rejects a missing object", -1,
                model.duplicateInstance(model.objectsCount, 0, Vec3d(10.0, 0.0, 0.0)))
            assertEquals("linked duplication rejects a negative source instance", -1,
                model.duplicateInstance(0, -1, Vec3d(10.0, 0.0, 0.0)))
            model.deleteObject(model.objectsCount)
            assertEquals("split rejects a negative object index", 0, model.split(-1))

            assertEquals("invalid edits preserve the object count", 1, model.objectsCount)
            assertEquals("invalid edits preserve the mesh", originalTriangles, model.getTriangleCount(0))
            assertEquals("invalid edits preserve solid volume", originalVolume, model.getObjectVolume(0), 1e-6)
            val min = Vec3d()
            val max = Vec3d()
            model.getBoundingBoxExact(0, min, max)
            val after = doubleArrayOf(min.x, min.y, min.z, max.x, max.y, max.z)
            originalBounds.forEachIndexed { axis, value ->
                assertEquals("invalid edits preserve bounds on axis $axis", value, after[axis], 1e-6)
            }
        } finally {
            model.release()
            modelFile.delete()
        }
    }

    @Test
    fun invalidJoinBooleanAndTextRequestsLeaveModelUntouched() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelFile = copyCube(context, "invalid-model-edit-requests.stl")
        val fontFile = copyAsset(context, "font/roboto_medium.ttf", "invalid-model-edit-font.ttf")
        val model = Model(modelFile)
        try {
            model.addObject(model, 0)
            model.setUniformFilament(0, 1)
            model.setUniformFilament(1, 2)
            val originalCount = model.objectsCount
            val originalFilaments = (0 until originalCount).map { model.getUsedFilaments(it) }
            val min = Vec3d()
            val max = Vec3d()
            val originalBounds = (0 until originalCount).map { index ->
                model.getBoundingBoxExact(index, min, max)
                doubleArrayOf(min.x, min.y, min.z, max.x, max.y, max.z)
            }

            // Protect native callers too; Kotlin validation is not the JNI safety boundary.
            assertEquals("invalid native join handle is rejected", -1,
                Native.model_combine_objects_as_parts(0L, intArrayOf(0, 1)))
            assertEquals("invalid native Boolean handle is rejected", -1,
                Native.model_boolean_objects(0L, intArrayOf(0, 1), 0))
            assertEquals("unsupported native Boolean operation is rejected", -1,
                Native.model_boolean_objects(model.pointer, intArrayOf(0, 1), 3))
            Native.model_emboss_text(0L, 0, fontFile.absolutePath, "Label", 12f, 1.2f, 0,
                90.0, 90.0, 0.0, 0.0, 0.0, 1.0)
            Native.model_emboss_text(model.pointer, originalCount, fontFile.absolutePath, "Label", 12f, 1.2f, 0,
                90.0, 90.0, 0.0, 0.0, 0.0, 1.0)
            Native.model_emboss_text(model.pointer, 0, fontFile.absolutePath, "Label", Float.NaN, 1.2f, 0,
                90.0, 90.0, 0.0, 0.0, 0.0, 1.0)
            Native.model_emboss_text(model.pointer, 0, fontFile.absolutePath, "Label", 12f, 1.2f, 2,
                90.0, 90.0, 0.0, 0.0, 0.0, 1.0)
            Native.model_emboss_text(model.pointer, 0, fontFile.absolutePath, "Label", 12f, 1.2f, 0,
                90.0, 90.0, 0.0, 0.0, 0.0, 0.0)

            assertEquals("duplicate join indices are rejected", -1,
                model.combineObjectsAsParts(intArrayOf(0, 0)))
            assertEquals("out-of-range Boolean indices are rejected", -1,
                model.booleanObjects(intArrayOf(0, originalCount), BooleanOperation.UNION))
            assertEquals("blank standalone text is rejected", -1, model.createTextObject(
                fontFile.absolutePath, "  ", 12f, 1.2f,
                Vec3d(90.0, 90.0, 0.0), Vec3d(0.0, 0.0, 1.0),
            ))
            assertEquals("an invalid surface target is rejected", -1, model.createTextObject(
                fontFile.absolutePath, "Label", 12f, 1.2f,
                Vec3d(90.0, 90.0, 0.0), Vec3d(0.0, 0.0, 1.0), targetObjectIndex = originalCount,
            ))

            assertEquals("invalid model edits must not add, remove, or combine objects", originalCount,
                model.objectsCount)
            (0 until originalCount).forEach { index ->
                assertArrayEquals("invalid edits preserve object $index filament assignments",
                    originalFilaments[index], model.getUsedFilaments(index))
                model.getBoundingBoxExact(index, min, max)
                val after = doubleArrayOf(min.x, min.y, min.z, max.x, max.y, max.z)
                originalBounds[index].forEachIndexed { axis, value ->
                    assertEquals("invalid edits preserve object $index bounds on axis $axis", value,
                        after[axis], 0.001)
                }
            }
        } finally {
            model.release()
            modelFile.delete()
            fontFile.delete()
        }
    }

    @Test
    fun copyingAnObjectFromEmptyOrReleasedModelIsIgnoredSafely() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val targetFile = copyCube(context, "safe-invalid-object-copy-target.stl")
        val sourceFile = copyCube(context, "safe-invalid-object-copy-source.stl")
        val target = Model(targetFile)
        val empty = Model()
        val released = Model(sourceFile)
        try {
            empty.addObject(empty, 0)
            assertEquals("an empty model must not self-copy an unavailable object", 0, empty.objectsCount)

            target.addObject(empty, 0)
            assertEquals("an empty source must not append a native object", 1, target.objectsCount)

            released.release()
            target.addObject(released, 0)
            assertEquals("a released source must not append a native object", 1, target.objectsCount)

            target.addObject(target, target.objectsCount)
            assertEquals("an out-of-range index must not append a native object", 1, target.objectsCount)
        } finally {
            target.release()
            empty.release()
            released.release()
            targetFile.delete()
            sourceFile.delete()
        }
    }

    @Test
    fun xAndYDistributedObjectsRemainOnBedAndProduceExtrusionGcode() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelFile = copyCube(context, "distributed-group-slice.stl")
        val model = Model(modelFile)
        try {
            val min = Vec3d()
            val max = Vec3d()
            model.getBoundingBoxExact(0, min, max)
            val width = max.x - min.x
            val depth = max.y - min.y
            model.addObject(model, 0)
            model.addObject(model, 0)
            model.translate(1, width * 2.0, depth * 2.0, 0.0)
            model.translate(2, width * 6.0, depth * 6.0, 0.0)

            for (axis in listOf(ObjectDistributionAxis.X, ObjectDistributionAxis.Y)) {
                val bounds = linkedMapOf<Int, ObjectBounds3D>()
                for (index in 0 until model.objectsCount) {
                    model.getBoundingBoxExact(index, min, max)
                    bounds[index] = ObjectBounds3D(min.x, min.y, min.z, max.x, max.y, max.z)
                }
                val deltas = requireNotNull(ObjectGroupDistribution.plan(bounds, axis))
                deltas.forEach { (index, delta) -> model.translate(index, delta.x, delta.y, delta.z) }
                val dimension = if (axis == ObjectDistributionAxis.X) 0 else 1
                val gapA = run {
                    model.getBoundingBoxExact(0, min, max); val endFirst = if (dimension == 0) max.x else max.y
                    model.getBoundingBoxExact(1, min, max); val startMiddle = if (dimension == 0) min.x else min.y
                    startMiddle - endFirst
                }
                val gapB = run {
                    model.getBoundingBoxExact(1, min, max); val endMiddle = if (dimension == 0) max.x else max.y
                    model.getBoundingBoxExact(2, min, max); val startLast = if (dimension == 0) min.x else min.y
                    startLast - endMiddle
                }
                assertEquals("equal clear gaps along $axis", gapA, gapB, 1e-6)
                for (index in 0 until model.objectsCount) {
                    model.getBoundingBoxExact(index, min, max)
                    assertTrue("XY distribution must keep object $index on the bed", min.z >= -0.05)
                }
            }

            val gcode = sliceToGcode(context, model, "distributed-group-slice.gcode")
            assertHasExtrusion(gcode)
        } finally {
            model.release()
            modelFile.delete()
        }
    }

    @Test
    fun splitMultipartObjectPreservesEachFilamentAndStillSlices() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelFile = copyCube(context, "split-multipart-slice.stl")
        val model = Model(modelFile)
        try {
            model.addObject(model, 0)
            model.translate(1, 30.0, 0.0, 0.0)
            model.setUniformFilament(0, 1)
            model.setUniformFilament(1, 2)
            assertEquals(0, model.combineObjectsAsParts(intArrayOf(0, 1)))
            assertEquals(1, model.objectsCount)
            assertEquals(listOf(1, 2), model.getUsedFilaments(0).toList())

            assertEquals("Split should report both restored source bodies", 2, model.split(0))
            assertEquals("Split should restore two separately editable objects", 2, model.objectsCount)
            assertEquals(setOf(1, 2), (0 until model.objectsCount).flatMap { model.getUsedFilaments(it).toList() }.toSet())

            val gcode = sliceToGcode(context, model, "split-multipart-slice.gcode")
            assertHasExtrusion(gcode)
            assertTrue("the split T1 body must still reach G-code", Regex("(?m)^T1(?:\\s|$)").containsMatchIn(gcode))
        } finally {
            model.release()
            modelFile.delete()
        }
    }

    @Test
    fun successfulPlaneCutKeepsBothPrintablePiecesAndStillSlices() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelFile = copyCube(context, "successful-plane-cut-slice.stl")
        val model = Model(modelFile)
        try {
            model.setUniformFilament(0, 1)
            val min = Vec3d()
            val max = Vec3d()
            model.getBoundingBoxExact(0, min, max)
            val originalVolume = model.getObjectVolume(0)
            val originalFilaments = model.getUsedFilaments(0).toSet()
            val cutHeight = (min.z + max.z) / 2.0

            assertTrue("a centered plane through the cube should produce both cut pieces",
                model.cut(0, cutHeight, 0.0, 0.0, keepUpper = true, keepLower = true))
            assertEquals("the original object is replaced with upper and lower pieces", 2, model.objectsCount)

            val pieceVolumes = (0 until model.objectsCount).map { index -> model.getObjectVolume(index) }
            assertTrue("both cut pieces must contain printable volume", pieceVolumes.all { it > 0.0 })
            val pieceBottoms = (0 until model.objectsCount).map { index ->
                model.getBoundingBoxExact(index, min, max)
                min.z
            }
            assertTrue("every cut piece must rest on the print bed, bottoms=$pieceBottoms",
                pieceBottoms.all { it >= -0.05 && it <= 0.05 })
            assertEquals("the cut should preserve the source solid volume", originalVolume,
                pieceVolumes.sum(), originalVolume * 0.01)
            assertEquals("cut pieces should preserve the source filament assignment", originalFilaments,
                (0 until model.objectsCount).flatMap { model.getUsedFilaments(it).toList() }.toSet())

            val gcode = sliceToGcode(context, model, "successful-plane-cut-slice.gcode")
            assertHasExtrusion(gcode)
        } finally {
            model.release()
            modelFile.delete()
        }
    }

    @Test
    fun surfacePlacedUnionDifferenceAndIntersectionRemainPrintable() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (operation in BooleanOperation.entries) {
            val modelFile = copyCube(context, "surface-boolean-${operation.name.lowercase()}.stl")
            val model = Model(modelFile)
            try {
                model.addObject(model, 0)
                model.scale(1, 0.25, 0.25, 0.25)
                model.translate(1, 100.0, 100.0, 100.0)
                model.setUniformFilament(0, 1)
                model.setUniformFilament(1, 2)

                val targetMin = Vec3d(); val targetMax = Vec3d()
                val toolMin = Vec3d(); val toolMax = Vec3d()
                model.getBoundingBoxExact(0, targetMin, targetMax)
                model.getBoundingBoxExact(1, toolMin, toolMax)
                val targetVolume = model.getObjectVolume(0)
                val delta = requireNotNull(ObjectPlacement.forSurfaceBoolean(
                    ObjectBounds3D(toolMin.x, toolMin.y, toolMin.z, toolMax.x, toolMax.y, toolMax.z),
                    Vec3d((targetMin.x + targetMax.x) / 2.0, (targetMin.y + targetMax.y) / 2.0, targetMax.z),
                    Vec3d(0.0, 0.0, 1.0),
                    penetrationMm = 0.8,
                ))
                model.translate(1, delta.x, delta.y, delta.z)

                val result = model.booleanObjects(intArrayOf(0, 1), operation)
                assertEquals("$operation should produce a surface-placed solid", 0, result)
                assertEquals("the Boolean should replace target and tool atomically", 1, model.objectsCount)
                assertEquals("the result should retain the base filament", listOf(1), model.getUsedFilaments(result).toList())
                val resultVolume = model.getObjectVolume(result)
                assertTrue("$operation should leave printable volume", resultVolume > 0.0)
                when (operation) {
                    BooleanOperation.UNION -> assertTrue(resultVolume > targetVolume)
                    BooleanOperation.DIFFERENCE -> assertTrue(resultVolume < targetVolume)
                    BooleanOperation.INTERSECTION -> {
                        assertTrue(resultVolume < targetVolume)
                        val intersectionMin = Vec3d(); val intersectionMax = Vec3d()
                        model.getBoundingBoxExact(result, intersectionMin, intersectionMax)
                        assertTrue("intersection should stay at the picked surface until explicitly arranged",
                            intersectionMin.z > 0.0)
                        // Intersection keeps its exact world-space location. As with any floating
                        // result, arrange it on the bed before asking the slicer to emit G-code.
                        model.ensureOnBed(result)
                    }
                }

                val gcode = sliceToGcode(context, model, "surface-boolean-${operation.name.lowercase()}.gcode")
                assertHasExtrusion(gcode)
            } finally {
                model.release()
                modelFile.delete()
            }
        }
    }

    @Test
    fun simplifiedMeshRemainsSolidAndProducesExtrusionGcode() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelFile = copyAsset(context, "models/sphere.stl", "simplified-sphere-slice.stl")
        val model = Model(modelFile)
        try {
            val originalTriangles = model.getTriangleCount(0)
            val originalVolume = model.getObjectVolume(0)
            assertTrue("sphere fixture should contain enough triangles to simplify", originalTriangles > 8)

            val simplifiedTriangles = model.simplify(0, 0.5f)
            assertTrue("simplification should reduce triangle count", simplifiedTriangles in 4 until originalTriangles)
            assertTrue("simplification should retain a positive solid volume", model.getObjectVolume(0) > 0.0)
            assertTrue("the simplified mesh should remain a substantial part of its original volume",
                model.getObjectVolume(0) / originalVolume in 0.7..1.3)

            val gcode = sliceToGcode(context, model, "simplified-sphere-slice.gcode")
            assertHasExtrusion(gcode)
        } finally {
            model.release()
            modelFile.delete()
        }
    }

    @Test
    fun autoOrientedModelRemainsOnBedAndProducesExtrusionGcode() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelFile = copyAsset(context, "models/pyramid.stl", "auto-oriented-pyramid-slice.stl")
        val model = Model(modelFile)
        try {
            model.rotate(0, Math.toRadians(23.0), Math.toRadians(31.0), Math.toRadians(17.0))
            model.autoOrient(0)
            val min = ru.ytkab0bp.slicebeam.utils.Vec3d()
            val max = ru.ytkab0bp.slicebeam.utils.Vec3d()
            model.getBoundingBoxExact(0, min, max)
            assertTrue("auto-orient should leave a finite, positive-height object", min.z.isFinite() && max.z > min.z)
            assertTrue("auto-oriented model should remain on or above the build plate", min.z >= -0.05)
            assertTrue("auto-orient should preserve the printable solid", model.getObjectVolume(0) > 0.0)

            val gcode = sliceToGcode(context, model, "auto-oriented-pyramid-slice.gcode")
            assertHasExtrusion(gcode)
        } finally {
            model.release()
            modelFile.delete()
        }
    }

    @Test
    fun separateObjectsAssignedToDifferentFilamentsEmitToolChange() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelFile = copyCube(context, "separate-filaments-slice.stl")
        val model = Model(modelFile)
        try {
            model.addObject(model, 0)
            model.translate(1, 30.0, 0.0, 0.0)
            model.setUniformFilament(0, 1)
            model.setUniformFilament(1, 2)
            assertEquals(listOf(1), model.getUsedFilaments(0).toList())
            assertEquals(listOf(2), model.getUsedFilaments(1).toList())

            val gcode = sliceToGcode(context, model, "separate-filaments-slice.gcode")

            assertHasExtrusion(gcode)
            assertTrue("the second object's T1 assignment must reach emitted G-code", Regex("(?m)^T1(?:\\s|$)").containsMatchIn(gcode))
        } finally {
            model.release()
            modelFile.delete()
        }
    }

    @Test
    fun oneFeatureFilamentOverrideDoesNotCascadeToOtherInfillOrSurfaceFeatures() = withToolChangerProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = requireNotNull(SliceBeam.CONFIG)
        val process = requireNotNull(config.findPrint("0.20mm Standard"))
        val modelFile = copyCube(context, "independent-feature-filament-slice.stl")
        val model = Model(modelFile)
        val featureSettings = listOf(
            "sparse_infill_filament_id" to "Sparse infill",
            "internal_solid_filament_id" to "Internal solid infill",
            "top_surface_filament_id" to "Top surface",
            "bottom_surface_filament_id" to "Bottom surface",
        )
        try {
            // A second object filament makes both tools genuinely active in the generated config.
            // Feature overrides then move only the selected role back to T0.
            model.setUniformFilament(0, 2)
            process.put("sparse_infill_density", "20")
            process.put("wall_loops", "2")
            process.put("top_shell_layers", "3")
            process.put("bottom_shell_layers", "3")

            for ((selectedKey, selectedFeature) in featureSettings) {
                featureSettings.forEach { (key, _) -> process.put(key, if (key == selectedKey) "1" else "0") }
                SliceBeam.genCurrentConfig(model)
                val generatedConfig = SliceBeam.getCurrentConfigFile()
                val configText = generatedConfig.readText()
                featureSettings.forEach { (key, _) ->
                    val expectedValue = if (key == selectedKey) 1 else 0
                    assertTrue(
                        "$selectedKey must not rewrite $key during native config normalization",
                        Regex("(?m)^${Regex.escape(key)}\\s*=\\s*$expectedValue\\s*$").containsMatchIn(configText),
                    )
                }

                val gcodeFile = File(context.cacheDir, "independent-feature-${selectedKey}.gcode")
                var result: GCodeProcessorResult? = null
                try {
                    result = model.slice(generatedConfig.absolutePath, gcodeFile.absolutePath, null)
                    val gcode = gcodeFile.readText()
                    assertHasExtrusion(gcode)
                    featureSettings.forEach { (key, feature) ->
                        val expectedTool = if (key == selectedKey) 0 else 1
                        assertEquals(
                            "$selectedKey must affect only $selectedFeature; $feature must keep the object's T1 tool",
                            setOf(expectedTool),
                            toolsForFeature(gcode, feature),
                        )
                    }
                } finally {
                    result?.release()
                    generatedConfig.delete()
                    gcodeFile.delete()
                }
            }
        } finally {
            model.release()
            modelFile.delete()
        }
    }

    @Test
    fun assignedInnerWallFilamentIsUsedForOverhangSegments() = withToolChangerProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val process = requireNotNull(SliceBeam.CONFIG?.findPrint("0.20mm Standard"))
        val modelFile = createOverhangWallFixture("inner-wall-overhang-filament.stl")
        val model = Model(modelFile)
        try {
            // Keep both tools active in the object config while the feature-specific wall IDs are tested.
            model.setUniformFilament(0, 2)
            process.put("wall_loops", "2")
            process.put("outer_wall_filament_id", "1")
            process.put("inner_wall_filament_id", "2")
            process.put("sparse_infill_filament_id", "1")
            process.put("internal_solid_filament_id", "1")
            process.put("top_surface_filament_id", "1")
            process.put("bottom_surface_filament_id", "1")
            process.put("detect_overhang_wall", "1")
            process.put("extra_perimeters_on_overhangs", "0")
            process.put("enable_support", "0")
            process.put("skirt_loops", "0")
            process.put("brim_type", "no_brim")

            for (wallGenerator in listOf("classic", "arachne")) {
                process.put("wall_generator", wallGenerator)
                SliceBeam.genCurrentConfig(model)
                val configFile = SliceBeam.getCurrentConfigFile()
                val gcodeFile = File(context.cacheDir, "inner-wall-overhang-$wallGenerator.gcode")
                var result: GCodeProcessorResult? = null
                try {
                    val configText = configFile.readText()
                    assertTrue("the generated slice config must retain both explicit wall tools",
                        Regex("(?m)^outer_wall_filament_id\\s*=\\s*1\\s*$").containsMatchIn(configText) &&
                            Regex("(?m)^inner_wall_filament_id\\s*=\\s*2\\s*$").containsMatchIn(configText))
                    result = model.slice(configFile.absolutePath, gcodeFile.absolutePath, null)
                    val gcode = gcodeFile.readText()
                    assertHasExtrusion(gcode)
                    assertEquals("$wallGenerator outer walls must use T0", setOf(0), toolsForFeature(gcode, "Outer wall"))
                    assertEquals("$wallGenerator inner walls, including overhang segments, must use T1; tools=${toolsForFeature(gcode, "Inner wall")}", setOf(1), toolsForFeature(gcode, "Inner wall"))
                    assertEquals("$wallGenerator overhang segments must preserve both wall levels; tools=${toolsForFeature(gcode, "Overhang wall")}", setOf(0, 1), toolsForFeature(gcode, "Overhang wall"))
                } finally {
                    result?.release()
                    configFile.delete()
                    gcodeFile.delete()
                }
            }
        } finally {
            model.release()
            modelFile.delete()
        }
    }

    @Test
    fun gapFillBetweenWallsDoesNotScheduleTheSparseInfillFilament() = withToolChangerProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val process = requireNotNull(SliceBeam.CONFIG?.findPrint("0.20mm Standard"))
        val modelFile = createThinCubeFixture("gap-fill-between-walls.stl", 20.0, 1.9, 5.0)
        val model = Model(modelFile)
        try {
            model.setUniformFilament(0, 1)
            process.put("wall_generator", "classic")
            process.put("wall_loops", "2")
            process.put("outer_wall_filament_id", "1")
            process.put("inner_wall_filament_id", "1")
            process.put("sparse_infill_filament_id", "2")
            process.put("internal_solid_filament_id", "1")
            process.put("top_surface_filament_id", "1")
            process.put("bottom_surface_filament_id", "1")
            process.put("skirt_loops", "0")
            process.put("brim_type", "no_brim")
            SliceBeam.genCurrentConfig(model)
            val configFile = SliceBeam.getCurrentConfigFile()
            val gcodeFile = File(context.cacheDir, "gap-fill-between-walls.gcode")
            var result: GCodeProcessorResult? = null
            try {
                result = model.slice(configFile.absolutePath, gcodeFile.absolutePath, null)
                val gcode = gcodeFile.readText()
                assertHasExtrusion(gcode)
                assertTrue("the narrow model must contain wall-to-wall gap fill", toolsForFeature(gcode, "Gap infill").isNotEmpty())
                assertEquals("gap fill between walls must use the wall filament T0", setOf(0), toolsForFeature(gcode, "Gap infill"))
                assertTrue("there must not be a sparse infill path/tool change", toolsForFeature(gcode, "Sparse infill").isEmpty())
                assertFalse("unused sparse-infill filament T1 must not trigger a tool change", Regex("(?m)^T1(?:\\s|$)").containsMatchIn(gcode))
            } finally {
                result?.release()
                configFile.delete()
                gcodeFile.delete()
            }
        } finally {
            model.release()
            modelFile.delete()
        }
    }

    @Test
    fun classicWallGapFillUsesAnActiveWallFilament() = withToolChangerProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val process = requireNotNull(SliceBeam.CONFIG?.findPrint("0.20mm Standard"))
        val modelFile = createThinCubeFixture("classic-gap-fill-wall-filament.stl", 20.0, 1.9, 5.0)
        val model = Model(modelFile)
        try {
            model.setUniformFilament(0, 2)
            process.put("wall_generator", "classic")
            process.put("wall_loops", "2")
            process.put("outer_wall_filament_id", "2")
            process.put("inner_wall_filament_id", "1")
            process.put("sparse_infill_filament_id", "2")
            process.put("skirt_loops", "0")
            process.put("brim_type", "no_brim")
            SliceBeam.genCurrentConfig(model)
            val configFile = SliceBeam.getCurrentConfigFile()
            val gcodeFile = File(context.cacheDir, "classic-gap-fill-two-walls.gcode")
            var result: GCodeProcessorResult? = null
            try {
                result = model.slice(configFile.absolutePath, gcodeFile.absolutePath, null)
                val gcode = gcodeFile.readText()
                assertHasExtrusion(gcode)
                assertTrue("the two-wall fixture must contain classic gap fill", toolsForFeature(gcode, "Gap infill").isNotEmpty())
                assertEquals("gap fill between two walls must use the inner-wall filament T0", setOf(0), toolsForFeature(gcode, "Gap infill"))
                assertEquals("outer walls must retain their separately assigned filament T1", setOf(1), toolsForFeature(gcode, "Outer wall"))
            } finally {
                result?.release()
                configFile.delete()
                gcodeFile.delete()
            }

            // A single classic wall has no active inner-wall tool; even if one is configured,
            // its narrow gap fill must remain on the outer-wall filament.
            val singleWallFile = createThinCubeFixture("classic-gap-fill-single-wall.stl", 20.0, 1.1, 5.0)
            val singleWallModel = Model(singleWallFile)
            try {
                singleWallModel.setUniformFilament(0, 2)
                process.put("wall_loops", "1")
                process.put("outer_wall_filament_id", "1")
                process.put("inner_wall_filament_id", "2")
                SliceBeam.genCurrentConfig(singleWallModel)
                val configFile = SliceBeam.getCurrentConfigFile()
                val gcodeFile = File(context.cacheDir, "classic-gap-fill-single-wall.gcode")
                var result: GCodeProcessorResult? = null
                try {
                    result = singleWallModel.slice(configFile.absolutePath, gcodeFile.absolutePath, null)
                    val gcode = gcodeFile.readText()
                    assertHasExtrusion(gcode)
                    assertTrue("the single-wall fixture must contain gap fill", toolsForFeature(gcode, "Gap infill").isNotEmpty())
                    assertEquals("single-wall gap fill must use the active outer-wall filament T0", setOf(0), toolsForFeature(gcode, "Gap infill"))
                    assertFalse("the unused inner-wall filament must not trigger a T1 tool change", Regex("(?m)^T1(?:\\s|$)").containsMatchIn(gcode))
                } finally {
                    result?.release()
                    configFile.delete()
                    gcodeFile.delete()
                }
            } finally {
                singleWallModel.release()
                singleWallFile.delete()
            }
        } finally {
            model.release()
            modelFile.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test
    fun liftedNozzleDescendsOnlyAfterReachingTravelDestination() = withToolChangerProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = requireNotNull(SliceBeam.CONFIG)
        val process = requireNotNull(config.findPrint("0.20mm Standard"))
        val filament = requireNotNull(config.findFilament("Generic PLA"))
        filament.put("filament_retract_lift", "0.4")
        process.put("enable_prime_tower", "1")
        process.put("z_hop_types", "Normal Lift")
        process.put("skirt_loops", "0")
        process.put("brim_type", "no_brim")

        val source = copyCube(context, "orca25-lifted-travel-source.stl")
        val output = File(context.cacheDir, "orca25-lifted-travel.gcode")
        val model = Model(source)
        var result: GCodeProcessorResult? = null
        try {
            model.addObject(model, 0)
            model.translate(1, 35.0, 0.0, 0.0)
            model.setUniformFilament(0, 1)
            model.setUniformFilament(1, 2)
            SliceBeam.genCurrentConfig(model)
            val configFile = SliceBeam.getCurrentConfigFile()
            val configText = configFile.readText()
            assertTrue("the real slice must use a 0.4 mm retraction lift", Regex("(?m)^filament_z_hop\\s*=\\s*0.4(?:,0.4)*\\s*$").containsMatchIn(configText))
            assertTrue("the real slice must use normal lift: ${configText.lines().filter { it.contains("z_hop_types") }}",
                Regex("(?m)^z_hop_types\\s*=\\s*Normal Lift(?:,Normal Lift)*\\s*$").containsMatchIn(configText))
            result = model.slice(configFile.absolutePath, output.absolutePath, null)
            val gcode = output.readText()
            assertHasExtrusion(gcode)
            assertTrue("the fixture must switch between both assigned tools",
                Regex("(?m)^T0(?:\\s|$)").containsMatchIn(gcode) && Regex("(?m)^T1(?:\\s|$)").containsMatchIn(gcode))
            val descendingTravels = descendingXyTravelMoves(gcode)
            val gcodeLines = gcode.lines()
            val travelContexts = descendingTravels.joinToString("\n") { move ->
                val index = Regex("line=(\\d+)").find(move)!!.groupValues[1].toInt() - 1
                "$move\n" + gcodeLines.subList((index - 16).coerceAtLeast(0), (index + 17).coerceAtMost(gcodeLines.size)).joinToString("\n")
            }
            assertEquals("travel must stay at the lifted height until its destination: $travelContexts", 0, descendingTravels.size)
        } finally {
            result?.release()
            model.release()
            source.delete()
            output.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test
    fun liftedNozzleStaysUpAcrossAvoidCrossingDetours() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = requireNotNull(SliceBeam.CONFIG)
        val process = requireNotNull(config.findPrint("0.16mm Standard"))
        val filament = requireNotNull(config.findFilament("Anycubic Generic PLA"))
        process.put("reduce_crossing_wall", "1")
        process.put("retract_layer_change", "1")
        process.put("retract_before_travel", "1")
        process.put("retract_lift_above", "0.0")
        process.put("retract_lift_below", "0.0")
        filament.put("filament_retract_lift", "0.4")
        process.put("z_hop_types", "Normal Lift")
        process.put("seam_position", "random")
        process.put("skirt_loops", "0")
        process.put("brim_type", "no_brim")

        val source = copyCube(context, "orca25-lifted-detour-cube.stl")
        val output = File(context.cacheDir, "orca25-lifted-detour-cube.gcode")
        val model = Model(source)
        var result: GCodeProcessorResult? = null
        try {
            // Keep this travel-focused fixture short so avoid-crossing routing stays fast on mobile CPUs.
            model.scale(0, 1.0, 1.0, 0.25)
            model.addObject(model, 0)
            model.scale(1, 1.0, 1.0, 0.25)
            model.translate(1, 8.0, 0.0, 0.0)
            process.put("layer_height", "0.2")
            SliceBeam.genCurrentConfig(model)
            val configFile = SliceBeam.getCurrentConfigFile()
            val configText = configFile.readText()
            assertTrue("the detour slice must enable avoid-crossing walls",
                Regex("(?m)^reduce_crossing_wall\\s*=\\s*1\\s*$").containsMatchIn(configText))
            assertTrue("the detour slice must use 0.4 mm lift",
                Regex("(?m)^filament_z_hop\\s*=\\s*0.4(?:,0.4)*\\s*$").containsMatchIn(configText))
            result = model.slice(configFile.absolutePath, output.absolutePath, null)
            val gcode = output.readText()
            assertHasExtrusion(gcode)
            assertTrue("the multi-object fixture should generate many travels",
                Regex("(?m)^G[01]\\s+[^;\\n]*(?:X|Y)").findAll(gcode).count() > 20)
            val descendingTravels = descendingXyTravelMoves(gcode)
            assertEquals("a lifted detour must not descend while crossing XY: ${descendingTravels.take(5)}",
                0, descendingTravels.size)
        } finally {
            result?.release()
            model.release()
            source.delete()
            output.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test
    fun liftedNozzleStaysUpOnConcaveLShapeAvoidCrossingTravel() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = requireNotNull(SliceBeam.CONFIG)
        val process = requireNotNull(config.findPrint("0.16mm Standard"))
        val filament = requireNotNull(config.findFilament("Anycubic Generic PLA"))
        process.put("reduce_crossing_wall", "1")
        process.put("retract_layer_change", "1")
        process.put("retract_before_travel", "1")
        process.put("retract_lift_above", "0.0")
        process.put("retract_lift_below", "0.0")
        filament.put("filament_retract_lift", "0.4")
        process.put("z_hop_types", "Normal Lift")
        process.put("seam_position", "random")
        process.put("layer_height", "0.2")
        process.put("skirt_loops", "0")
        process.put("brim_type", "no_brim")

        val source = createLPrismFixture("orca25-lifted-concave-l.stl", 26.0, 6.0, 2.4)
        val output = File(context.cacheDir, "orca25-lifted-concave-l.gcode")
        val model = Model(source)
        var result: GCodeProcessorResult? = null
        try {
            SliceBeam.genCurrentConfig(model)
            val configFile = SliceBeam.getCurrentConfigFile()
            val configText = configFile.readText()
            assertTrue("the L-shape slice must enable avoid-crossing walls",
                Regex("(?m)^reduce_crossing_wall\\s*=\\s*1\\s*$").containsMatchIn(configText))
            assertTrue("the L-shape slice must use 0.4 mm lift",
                Regex("(?m)^filament_z_hop\\s*=\\s*0.4(?:,0.4)*\\s*$").containsMatchIn(configText))
            result = model.slice(configFile.absolutePath, output.absolutePath, null)
            val gcode = output.readText()
            assertHasExtrusion(gcode)
            val travelMoves = Regex("(?m)^G[01]\\s+[^;\\n]*(?:X|Y)").findAll(gcode).count()
            assertTrue("the concave fixture must generate enough path travel to exercise routing: $travelMoves",
                travelMoves > 80)
            val liftMoves = Regex("(?m)^G[01]\\s+Z[0-9.]+(?:\\s|$)").findAll(gcode).count()
            assertTrue("layer-change retraction must produce real Z lift moves", liftMoves > 3)
            val elevatedTravelRun = maxConsecutiveElevatedTravelXyMoves(gcode)
            assertTrue("the L fixture must exercise a multi-waypoint travel while lifted; longest elevated XY run=$elevatedTravelRun",
                elevatedTravelRun >= 2)
            val descendingTravels = descendingXyTravelMoves(gcode)
            assertEquals("the lifted nozzle must stay high across the concave L detour: ${descendingTravels.take(5)}",
                0, descendingTravels.size)
        } finally {
            result?.release()
            model.release()
            source.delete()
            output.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test(timeout = 300_000)
    fun orderSensitiveGapFillConfigurationsSliceWithGcode() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val process = requireNotNull(SliceBeam.CONFIG?.findPrint("0.16mm Standard"))
        val scenarios = listOf(
            Triple("internal_solid_infill_pattern", "spiralinset", "everywhere"),
            Triple("top_surface_pattern", "concentric", "topbottom"),
            Triple("top_surface_pattern", "monotonic", "topbottom"),
            Triple("top_surface_pattern", "monotonicline", "topbottom"),
        )
        val fixtures = listOf("orca-gap-order-v.obj", "orca-gap-order-cube-with-hole.obj")
        val configKeys = listOf(
            "internal_solid_infill_pattern", "top_surface_pattern", "gap_fill_target",
            "filter_out_gap_fill", "top_shell_layers", "bottom_shell_layers",
            "top_surface_density", "skirt_loops", "brim_type", "gcode_comments",
        )
        val previous = configKeys.associateWith { process[it] }
        val configFile = SliceBeam.getCurrentConfigFile()
        var verifiedGapFillCases = 0
        try {
            process.put("filter_out_gap_fill", "0")
            process.put("top_shell_layers", "3")
            process.put("bottom_shell_layers", "0")
            process.put("top_surface_density", "100%")
            process.put("skirt_loops", "0")
            process.put("brim_type", "no_brim")
            process.put("gcode_comments", "1")
            for (fixture in fixtures) {
                val source = File(context.cacheDir, fixture)
                InstrumentationRegistry.getInstrumentation().context.assets
                    .open("models/$fixture").use { input -> source.outputStream().use(input::copyTo) }
                val model = Model(source)
                try {
                    model.ensureOnBed(0)
                    for ((key, pattern, target) in scenarios) {
                        process.put(key, pattern)
                        process.put("gap_fill_target", target)
                        SliceBeam.genCurrentConfig(model)
                        val output = sliceToGcode(context, model, "ordered-gap-${fixture.substringBefore('.')}-${pattern}.gcode")
                        assertHasExtrusion(output)
                        if (!output.contains(";TYPE:Gap infill")) continue
                        verifiedGapFillCases++
                    }
                } finally {
                    model.release()
                    source.delete()
                }
            }
        } finally {
            configKeys.forEach { key ->
                process.values.remove(key)
                previous[key]?.let { process.put(key, it) }
            }
            configFile.delete()
        }
        assertTrue("order-sensitive fixture matrix must exercise real gap-fill output; cases=$verifiedGapFillCases",
            verifiedGapFillCases >= 4)
    }

    @Test
    fun monotonicTopSurfaceGapFillPreservesSurfacePathOrder() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val process = requireNotNull(SliceBeam.CONFIG?.findPrint("0.16mm Standard"))
        val modelFile = File(context.cacheDir, "orca-gap-fill-letter-a.obj")
        InstrumentationRegistry.getInstrumentation().context.assets.open("models/orca-gap-fill-letter-a.obj").use { input ->
            modelFile.outputStream().use(input::copyTo)
        }
        val model = Model(modelFile)
        val output = File(context.cacheDir, "monotonic-surface-gap-fill.gcode")
        try {
            model.ensureOnBed(0)
            process.put("top_surface_pattern", "monotonicline")
            process.put("top_shell_layers", "3")
            process.put("bottom_shell_layers", "0")
            process.put("top_surface_density", "100%")
            process.put("filter_out_gap_fill", "0")
            process.put("skirt_loops", "0")
            process.put("brim_type", "no_brim")

            fun slice(gapTarget: String): Pair<List<String>, List<String>> {
                process.put("gap_fill_target", gapTarget)
                SliceBeam.genCurrentConfig(model)
                val configFile = SliceBeam.getCurrentConfigFile()
                output.delete()
                val result = model.slice(configFile.absolutePath, output.absolutePath, null)
                try {
                    val topEndpoints = mutableListOf<String>()
                    val orderedSurfaceRoles = mutableListOf<String>()
                    var currentRole = ""
                    output.forEachLine { line ->
                        if (line.startsWith(";TYPE:")) {
                            currentRole = line.removePrefix(";TYPE:").trim()
                        } else if (line.trimStart().let { it.startsWith("G0 ") || it.startsWith("G1 ") || it.startsWith("G2 ") || it.startsWith("G3 ") } && line.contains(" E")) {
                            if (currentRole == "Top surface") {
                                val x = Regex("(?:^|\\s)X(-?\\d+(?:\\.\\d+)?)").find(line)?.groupValues?.get(1)
                                val y = Regex("(?:^|\\s)Y(-?\\d+(?:\\.\\d+)?)").find(line)?.groupValues?.get(1)
                                if (x != null && y != null) topEndpoints += "$x,$y"
                            }
                            if (currentRole == "Top surface" || currentRole == "Gap infill")
                                orderedSurfaceRoles += currentRole
                        }
                    }
                    return topEndpoints to orderedSurfaceRoles
                } finally {
                    result.release()
                    configFile.delete()
                }
            }

            val withoutGapFill = slice("nowhere").first
            val withGapFill = slice("topbottom")
            val observedTypes = output.readText().lineSequence().filter { it.startsWith(";TYPE:") }.distinct().toList()
            assertTrue(
                "the letter fixture must generate top-surface extrusion; types=$observedTypes",
                withoutGapFill.isNotEmpty(),
            )
            assertTrue("gap interleaving must preserve every top-surface endpoint", withGapFill.first == withoutGapFill)
            assertTrue("the fixture must generate top-surface gap fill", withGapFill.second.contains("Gap infill"))
        } finally {
            model.release()
            modelFile.delete()
            output.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test
    fun topAndBottomGapFillUseTheirSurfaceFilaments() = withToolChangerProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val process = requireNotNull(SliceBeam.CONFIG?.findPrint("0.20mm Standard"))
        val modelFile = createCylinderFixture("surface-gap-fill-filaments.stl", 10.0, 10.0, 96)
        val model = Model(modelFile)
        try {
            // Keep all four configured tools within the generated filament arrays.
            model.setUniformFilament(0, 4)
            process.put("wall_generator", "arachne")
            process.put("gap_fill_target", "topbottom")
            process.put("outer_wall_filament_id", "1")
            process.put("inner_wall_filament_id", "1")
            process.put("sparse_infill_filament_id", "1")
            process.put("internal_solid_filament_id", "2")
            process.put("top_surface_filament_id", "3")
            process.put("bottom_surface_filament_id", "4")
            process.put("skirt_loops", "0")
            process.put("brim_type", "no_brim")
            SliceBeam.genCurrentConfig(model)
            val configFile = SliceBeam.getCurrentConfigFile()
            val gcodeFile = File(context.cacheDir, "surface-gap-fill-filaments.gcode")
            var result: GCodeProcessorResult? = null
            try {
                val configText = configFile.readText()
                assertTrue("generated config must retain surface filament assignments",
                    Regex("(?m)^top_surface_filament_id\\s*=\\s*3\\s*$").containsMatchIn(configText) &&
                        Regex("(?m)^bottom_surface_filament_id\\s*=\\s*4\\s*$").containsMatchIn(configText))
                result = model.slice(configFile.absolutePath, gcodeFile.absolutePath, null)
                val gcode = gcodeFile.readText()
                assertHasExtrusion(gcode)
                assertEquals("top/bottom surface gap fill must use the associated surface tools", setOf(2, 3), toolsForFeature(gcode, "Gap infill"))
                assertEquals("top surfaces must use T2", setOf(2), toolsForFeature(gcode, "Top surface"))
                assertEquals("bottom surfaces must use T3", setOf(3), toolsForFeature(gcode, "Bottom surface"))
                assertEquals("tool changes must not leave a tool idle on the layer", 0, idleToolChanges(gcode))
            } finally {
                result?.release()
                configFile.delete()
                gcodeFile.delete()
            }
        } finally {
            model.release()
            modelFile.delete()
        }
    }

    @Test
    fun fillBedCopiesPreserveMaterialThroughThreeMfReloadAndSlice() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelFile = copyCube(context, "fill-bed-slice-cube.stl")
        val projectFile = File(context.cacheDir, "fill-bed-slice-roundtrip.3mf")
        val gcodeFile = File(context.cacheDir, "fill-bed-slice-roundtrip.gcode")
        val model = Model(modelFile)
        var restored: Model? = null
        var result: GCodeProcessorResult? = null
        try {
            model.translate(0, 13.0, 17.0, 5.0)
            model.setUniformFilament(0, 2)

            val added = FillBedModelOperation.run(
                activeModel = model,
                objectIndex = 0,
                viewerEnabled = false,
                estimatedCapacity = 3,
            ) { candidate ->
                for (index in 0 until candidate.objectsCount) {
                    val current = Vec3d()
                    candidate.getTranslation(index, current)
                    candidate.translate(index, index * 28.0 - current.x, -current.y, -current.z)
                }
                candidate.objectsCount <= 3
            }
            assertEquals("fill-bed should commit both accepted copies", 2, added)
            assertEquals(3, model.objectsCount)
            for (index in 0 until model.objectsCount) {
                assertEquals("every arranged copy should retain the selected T1 material", listOf(2),
                    model.getUsedFilaments(index).toList())
            }

            SliceBeam.genCurrentConfig(model)
            model.export3mf(SliceBeam.getCurrentConfigFile().absolutePath, projectFile.absolutePath)
            restored = Model(projectFile, 1)
            assertEquals("all packed objects should survive a 3MF reload", 3, restored.objectsCount)
            for (index in 0 until restored.objectsCount) {
                assertEquals("3MF reload should preserve T1 on packed object $index", listOf(2),
                    restored.getUsedFilaments(index).toList())
            }

            result = restored.slice(SliceBeam.getCurrentConfigFile().absolutePath, gcodeFile.absolutePath, null)
            val gcode = gcodeFile.readText()
            assertHasExtrusion(gcode)
            assertTrue("the selected T1 material should reach the generated G-code",
                Regex("(?m)^T1(?:\\s|$)").containsMatchIn(gcode))
            assertFalse("the packed objects must not unexpectedly select T0",
                Regex("(?m)^T0(?:\\s|$)").containsMatchIn(gcode))
        } finally {
            result?.release()
            restored?.release()
            model.release()
            modelFile.delete()
            projectFile.delete()
            gcodeFile.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test
    fun combinedMultipartObjectKeepsBothFilamentsThroughSlicing() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelFile = copyCube(context, "combined-filaments-slice.stl")
        val roundTripFile = File(context.cacheDir, "combined-filaments-roundtrip.3mf")
        val model = Model(modelFile)
        var restored: Model? = null
        try {
            model.addObject(model, 0)
            model.scale(1, 1.3, 0.8, 1.2)
            model.rotate(1, 0.35, -0.22, 0.48)
            model.translate(1, 50.0, 50.0, 0.0)
            model.setUniformFilament(0, 1)
            model.setUniformFilament(1, 2)

            assertEquals(0, model.combineObjectsAsParts(intArrayOf(0, 1)))
            assertEquals("both part assignments must remain on the combined object", listOf(1, 2), model.getUsedFilaments(0).toList())

            SliceBeam.genCurrentConfig(model)
            model.export3mf(SliceBeam.getCurrentConfigFile().absolutePath, roundTripFile.absolutePath)
            ZipFile(roundTripFile).use { archive ->
                val entry = archive.getEntry("Metadata/Slic3r_PE_model.config")
                val metadata = entry?.let { archive.getInputStream(it).bufferedReader().use { reader -> reader.readText() } }
                assertTrue("second part's filament must be present in the 3MF volume metadata",
                    metadata.orEmpty().contains("type=\"volume\" key=\"extruder\" value=\"2\""))
            }
            val roundTrip = Model(roundTripFile, 1).also { restored = it }
            assertEquals(1, roundTrip.objectsCount)
            assertEquals("both part assignments must survive 3MF save/reload", listOf(1, 2), roundTrip.getUsedFilaments(0).toList())

            val gcode = sliceToGcode(context, roundTrip, "combined-filaments-slice.gcode")

            assertHasExtrusion(gcode)
            assertTrue("the second part's T1 assignment must reach emitted G-code", Regex("(?m)^T1(?:\\s|$)").containsMatchIn(gcode))
        } finally {
            restored?.release()
            model.release()
            modelFile.delete()
            roundTripFile.delete()
        }
    }

    @Test
    fun manyPartMultipartObjectSlicesWithBrimGroupingAndKeepsMaterials() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelFile = copyCube(context, "many-part-brim-source.stl")
        val output = File(context.cacheDir, "many-part-brim.gcode")
        val model = Model(modelFile, 1)
        try {
            val partCount = 12
            for (part in 1 until partCount) {
                model.addObject(model, 0)
                val column = part % 3
                val row = part / 3
                model.translate(part, column * 25.0, row * 25.0, 0.0)
            }
            model.ensureOnBed(0)
            for (part in 0 until partCount) model.setUniformFilament(part, if (part % 2 == 0) 1 else 2)
            assertEquals("all synthetic source objects should combine into one multipart model", 0,
                model.combineObjectsAsParts(IntArray(partCount) { it }))
            assertEquals("the fixture must exercise twelve distinct printable volume parts", partCount,
                model.getObjectPartCount(0))
            assertEquals("both synthetic materials must remain assigned to distinct parts", listOf(1, 2),
                model.getUsedFilaments(0).toList())
            SliceBeam.genCurrentConfig(model)
            val workerCpuTicks = measureTbbWorkerCpuDuring {
                val result = model.slice(SliceBeam.getCurrentConfigFile().absolutePath, output.absolutePath, null)
                try {
                    val gcode = output.readText()
                    assertHasExtrusion(gcode)
                    assertTrue("the twelve-part slice must retain the second filament tool", Regex("(?m)^T1(?:\\s|$)").containsMatchIn(gcode))
                    assertTrue("the synthetic multipart G-code must contain layer output", gcode.contains(";LAYER_CHANGE"))
                } finally {
                    result.release()
                }
            }
            val activeTbbWorkers = workerCpuTicks.cpuTicks.filterValues { it > 0L }
            Log.i(
                "NativeSliceDeviceTest",
                "Real multipart slice TBB workers with CPU ticks: ${activeTbbWorkers.entries.sortedBy { it.key }.joinToString { "${it.key}=${it.value}" }}; observed processor IDs=${workerCpuTicks.processorIds.sorted()}",
            )
            if (Runtime.getRuntime().availableProcessors() > 1) {
                assertTrue(
                    "a real multi-part slice should execute work on at least two TBB workers when multiple CPUs are available; observed=$activeTbbWorkers",
                    activeTbbWorkers.size >= 2,
                )
            }
        } finally {
            model.release()
            modelFile.delete()
            output.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test
    fun recommendedFilenameListsDistinctFilamentTypesActuallyUsedByThePlate() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelFile = copyCube(context, "filename-filament-types.stl")
        val output = File(context.cacheDir, "filename-filament-types.gcode")
        val config = requireNotNull(SliceBeam.CONFIG)
        val process = requireNotNull(config.findPrint("0.16mm Standard"))
        process.put("filename_format", "{input_filename_base}_{printing_filament_types}_{print_time}.gcode")
        val model = Model(modelFile, 1)
        var result: GCodeProcessorResult? = null
        try {
            model.addObject(model, 0)
            model.translate(1, 35.0, 0.0, 0.0)
            model.setUniformFilament(0, 1)
            model.setUniformFilament(1, 2)
            assertEquals("two explicit source assignments must be preserved by grouping", 0,
                model.combineObjectsAsParts(intArrayOf(0, 1)))
            assertEquals("fixture contains both assigned source materials", listOf(1, 2),
                model.getUsedFilaments(0).toList())
            SliceBeam.genCurrentConfig(model)
            val generatedConfig = SliceBeam.getCurrentConfigFile()
            val configText = generatedConfig.readText()
            val filamentTypeLine = Regex("(?m)^filament_type\\s*=.*$")
            assertTrue("the generated native config must serialize the active filament vector",
                filamentTypeLine.containsMatchIn(configText))
            generatedConfig.writeText(filamentTypeLine.replace(configText, "filament_type = PLA; PETG"))

            result = model.slice(generatedConfig.absolutePath, output.absolutePath, null)
            val gcode = output.readText()
            assertHasExtrusion(gcode)
            assertTrue("both source tools must be present in the emitted multicolor G-code",
                Regex("(?m)^T1(?:\\s|$)").containsMatchIn(gcode))
            val recommendedName = requireNotNull(result).getRecommendedName()
            assertTrue("filename must list both distinct used materials in tool order: $recommendedName",
                recommendedName.contains("_PLA_PETG_"))
            assertFalse("filename must not leave its native placeholder unresolved: $recommendedName",
                recommendedName.contains("{"))

            result?.release()
            result = null
            output.delete()
            SliceBeam.genCurrentConfig(model)
            val duplicateConfig = SliceBeam.getCurrentConfigFile()
            val duplicateText = duplicateConfig.readText()
            duplicateConfig.writeText(filamentTypeLine.replace(duplicateText, "filament_type = PLA; PLA"))
            result = model.slice(duplicateConfig.absolutePath, output.absolutePath, null)
            val deduplicatedName = requireNotNull(result).getRecommendedName()
            assertTrue("identical material types must appear only once: $deduplicatedName",
                deduplicatedName.contains("_PLA_") && !deduplicatedName.contains("_PLA_PLA_"))
        } finally {
            result?.release()
            model.release()
            modelFile.delete()
            output.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test
    fun combiningLinkedCopiesWithAnotherObjectMaterializesAllInstancesAndStillSlices() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelFile = copyCube(context, "combine-linked-copies.stl")
        val sourceFile = copyCube(context, "combine-linked-source.stl")
        val projectFile = File(context.cacheDir, "combine-linked-roundtrip.3mf")
        val outputFile = File(context.cacheDir, "combine-linked-roundtrip.gcode")
        val model = Model(modelFile)
        val linkedSource = Model(sourceFile)
        var restored: Model? = null
        var sliceResult: GCodeProcessorResult? = null
        try {
            val initialMin = Vec3d(); val initialMax = Vec3d()
            model.getBoundingBoxExact(0, initialMin, initialMax)
            val width = initialMax.x - initialMin.x
            val trianglesPerCopy = model.getTriangleCount(0)
            assertTrue("the linked copy should be created", model.duplicateInstance(
                0, 0, Vec3d(width + 7.0, 0.0, 0.0),
            ) >= 0)
            linkedSource.setUniformFilament(0, 2)
            model.addObject(linkedSource, 0)
            model.translate(1, width * 2.0 + 14.0, 0.0, 0.0)
            model.setUniformFilament(0, 1)

            fun combinedWorldBounds(source: Model, index: Int): Pair<DoubleArray, DoubleArray> {
                val min = Vec3d(); val max = Vec3d()
                source.getBoundingBoxExact(index, min, max)
                return doubleArrayOf(min.x, min.y, min.z) to doubleArrayOf(max.x, max.y, max.z)
            }
            val sourceBounds = listOf(combinedWorldBounds(model, 0), combinedWorldBounds(model, 1))
            val expectedMin = DoubleArray(3) { axis -> sourceBounds.minOf { it.first[axis] } }
            val expectedMax = DoubleArray(3) { axis -> sourceBounds.maxOf { it.second[axis] } }

            assertEquals("linked object and separately placed object should combine", 0,
                model.combineObjectsAsParts(intArrayOf(0, 1)))
            assertEquals("all three world copies should become one printable object", 1, model.objectsCount)
            assertEquals("the linked copy must be materialized as its own part",
                trianglesPerCopy * 3, model.getTriangleCount(0))
            assertEquals("each source tool assignment should survive grouping", listOf(1, 2),
                model.getUsedFilaments(0).toList())
            val joinedBounds = combinedWorldBounds(model, 0)
            for (axis in 0..2) {
                assertEquals("joined world minimum on axis $axis", expectedMin[axis], joinedBounds.first[axis], 0.02)
                assertEquals("joined world maximum on axis $axis", expectedMax[axis], joinedBounds.second[axis], 0.02)
            }

            SliceBeam.genCurrentConfig(model)
            model.export3mf(SliceBeam.getCurrentConfigFile().absolutePath, projectFile.absolutePath)
            restored = Model(projectFile, 1)
            assertEquals("the grouped project should reload as one object", 1, restored.objectsCount)
            assertEquals("both materials should survive the 3MF round trip", listOf(1, 2),
                restored.getUsedFilaments(0).toList())
            val restoredBounds = combinedWorldBounds(restored, 0)
            for (axis in 0..2) {
                assertEquals("reloaded world minimum on axis $axis", expectedMin[axis], restoredBounds.first[axis], 0.02)
                assertEquals("reloaded world maximum on axis $axis", expectedMax[axis], restoredBounds.second[axis], 0.02)
            }
            sliceResult = restored.slice(SliceBeam.getCurrentConfigFile().absolutePath, outputFile.absolutePath, null)
            val gcode = outputFile.readText()
            assertHasExtrusion(gcode)
            assertTrue("the original target should emit T0", Regex("(?m)^T0(?:\\s|$)").containsMatchIn(gcode))
            assertTrue("the joined source should emit T1", Regex("(?m)^T1(?:\\s|$)").containsMatchIn(gcode))
        } finally {
            sliceResult?.release()
            restored?.release()
            linkedSource.release()
            model.release()
            modelFile.delete()
            sourceFile.delete()
            projectFile.delete()
            outputFile.delete()
        }
    }

    @Test
    fun uniformReassignmentOfMultipart3mfSurvivesReloadAndSlicesWithNewSlot() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sourceFile = copyCube(context, "multipart-reassignment-source.stl")
        val originalProject = File(context.cacheDir, "multipart-reassignment-original.3mf")
        val remappedProject = File(context.cacheDir, "multipart-reassignment-remapped.3mf")
        val output = File(context.cacheDir, "multipart-reassignment-remapped.gcode")
        val model = Model(sourceFile)
        var originalReload: Model? = null
        var remappedReload: Model? = null
        var result: GCodeProcessorResult? = null
        try {
            model.addObject(model, 0)
            model.translate(1, 30.0, 0.0, 0.0)
            model.setUniformFilament(0, 1)
            model.setUniformFilament(1, 2)
            assertEquals(0, model.combineObjectsAsParts(intArrayOf(0, 1)))
            assertEquals("fixture starts as one object with two material volumes", listOf(1, 2),
                model.getUsedFilaments(0).toList())

            SliceBeam.genCurrentConfig(model)
            model.export3mf(SliceBeam.getCurrentConfigFile().absolutePath, originalProject.absolutePath)
            originalReload = Model(originalProject, 1)
            assertEquals("the source 3MF must reload both material assignments", listOf(1, 2),
                originalReload.getUsedFilaments(0).toList())

            // This is the same explicit uniform reassignment used by the model-to-filament UI.
            originalReload.setUniformFilament(0, 2)
            assertEquals("choosing slot 2 must replace the imported per-volume slot 1", listOf(2),
                originalReload.getUsedFilaments(0).toList())
            SliceBeam.genCurrentConfig(originalReload)
            originalReload.export3mf(SliceBeam.getCurrentConfigFile().absolutePath, remappedProject.absolutePath)
            remappedReload = Model(remappedProject, 1)
            assertEquals("the chosen slot must survive the remapped 3MF round trip", listOf(2),
                remappedReload.getUsedFilaments(0).toList())

            SliceBeam.genCurrentConfig(remappedReload)
            result = remappedReload.slice(SliceBeam.getCurrentConfigFile().absolutePath, output.absolutePath, null)
            val gcode = output.readText()
            assertHasExtrusion(gcode)
            assertTrue("the chosen second slot must reach G-code as T1",
                Regex("(?m)^T1(?:\\s|$)").containsMatchIn(gcode))
        } finally {
            result?.release()
            remappedReload?.release()
            originalReload?.release()
            model.release()
            sourceFile.delete()
            originalProject.delete()
            remappedProject.delete()
            output.delete()
        }
    }

    @Test
    fun perSourceColorRemappingSurvives3mfReloadAndEmitsMappedTools() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sourceFile = copyCube(context, "per-color-remap-source.stl")
        val firstRoundTrip = File(context.cacheDir, "per-color-remap-first.3mf")
        val sharedRoundTrip = File(context.cacheDir, "per-color-remap-shared.3mf")
        val firstOutput = File(context.cacheDir, "per-color-remap-first.gcode")
        val sharedOutput = File(context.cacheDir, "per-color-remap-shared.gcode")
        val model = Model(sourceFile)
        var firstReload: Model? = null
        var sharedReload: Model? = null
        var firstResult: GCodeProcessorResult? = null
        var sharedResult: GCodeProcessorResult? = null
        try {
            model.addObject(model, 0)
            model.addObject(model, 0)
            model.translate(1, 30.0, 0.0, 0.0)
            model.translate(2, 60.0, 0.0, 0.0)
            model.setUniformFilament(0, 1)
            model.setUniformFilament(1, 2)
            model.setUniformFilament(2, 3)
            assertEquals(0, model.combineObjectsAsParts(intArrayOf(0, 1, 2)))
            assertEquals(listOf(1, 2, 3), model.getUsedFilaments(0).toList())

            model.remapFilaments(intArrayOf(1, 2, 3), intArrayOf(2, 3, 3))
            assertEquals("each 3MF material must map independently, including two sharing a target",
                listOf(2, 3), model.getUsedFilaments(0).toList())
            SliceBeam.genCurrentConfig(model)
            model.export3mf(SliceBeam.getCurrentConfigFile().absolutePath, firstRoundTrip.absolutePath)
            firstReload = Model(firstRoundTrip, 1)
            assertEquals("source color assignments must survive 3MF reload", listOf(2, 3),
                firstReload.getUsedFilaments(0).toList())
            firstResult = firstReload.slice(SliceBeam.getCurrentConfigFile().absolutePath,
                firstOutput.absolutePath, null)
            val firstGcode = firstOutput.readText()
            assertHasExtrusion(firstGcode)
            assertTrue("mapped source colors must use output tools T1 and T2",
                Regex("(?m)^T1(?:\\s|$)").containsMatchIn(firstGcode) &&
                    Regex("(?m)^T2(?:\\s|$)").containsMatchIn(firstGcode))
            assertFalse("unmapped T0 must not be emitted", Regex("(?m)^T0(?:\\s|$)").containsMatchIn(firstGcode))

            firstReload.remapFilaments(intArrayOf(2, 3), intArrayOf(2, 2))
            assertEquals("different model colors may intentionally share one filament slot", listOf(2),
                firstReload.getUsedFilaments(0).toList())
            SliceBeam.genCurrentConfig(firstReload)
            firstReload.export3mf(SliceBeam.getCurrentConfigFile().absolutePath, sharedRoundTrip.absolutePath)
            sharedReload = Model(sharedRoundTrip, 1)
            assertEquals(listOf(2), sharedReload.getUsedFilaments(0).toList())
            sharedResult = sharedReload.slice(SliceBeam.getCurrentConfigFile().absolutePath,
                sharedOutput.absolutePath, null)
            val sharedGcode = sharedOutput.readText()
            assertHasExtrusion(sharedGcode)
            assertTrue("the shared assignment must slice with target tool T1",
                Regex("(?m)^T1(?:\\s|$)").containsMatchIn(sharedGcode))
            assertFalse("the shared assignment must not retain target tool T2",
                Regex("(?m)^T2(?:\\s|$)").containsMatchIn(sharedGcode))
        } finally {
            firstResult?.release()
            sharedResult?.release()
            firstReload?.release()
            sharedReload?.release()
            model.release()
            sourceFile.delete()
            firstRoundTrip.delete()
            sharedRoundTrip.delete()
            firstOutput.delete()
            sharedOutput.delete()
        }
    }

    @Test
    fun kromacutColorFixtureCanMapBothSourceColorsToOneSlotAndSliceAfterReload() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val fixture = File(context.cacheDir, "kromacut-color-fixture-input.3mf")
        val fixturePipe = instrumentation.uiAutomation.executeShellCommand("cat /data/local/tmp/kromacut.3mf")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(fixturePipe).use { input ->
            fixture.outputStream().use { output -> input.copyTo(output) }
        }
        val fixtureAvailable = fixture.length() > 1_000_000L
        if (!fixtureAvailable) fixture.delete()
        assumeTrue(
            "Push the user-provided KromaCut fixture to /data/local/tmp/kromacut.3mf to run this integration test",
            fixtureAvailable,
        )
        val remappedProject = File(context.cacheDir, "kromacut-shared-filament.3mf")
        val originalGcodeFile = File(context.cacheDir, "kromacut-original-colors.gcode")
        val gcodeFile = File(context.cacheDir, "kromacut-shared-filament.gcode")
        val model = Model(fixture, 1)
        var reloaded: Model? = null
        var originalSliceResult: GCodeProcessorResult? = null
        var sliceResult: GCodeProcessorResult? = null
        try {
            assertEquals("KromaCut's two colored layers should import as one assembled object", 1, model.objectsCount)
            assertEquals("the source project should retain both imported colors", listOf(1, 2),
                model.getUsedFilaments(0).toList())
            assertEquals("the first source color should use its first logical filament", 1,
                model.getVolumeExtruder(0, 0))
            assertEquals("the second source color should use its second logical filament", 2,
                model.getVolumeExtruder(0, 1))

            SliceBeam.genCurrentConfig(model)
            originalSliceResult = model.slice(
                SliceBeam.getCurrentConfigFile().absolutePath,
                originalGcodeFile.absolutePath,
                null,
            )
            val originalGcode = originalGcodeFile.readText()
            assertHasExtrusion(originalGcode)
            assertTrue("the original two-color KromaCut slice must switch to its second tool",
                Regex("(?m)^T1(?:\\s|$)").containsMatchIn(originalGcode))
            assertFalse("the original KromaCut project must not reference a third tool",
                Regex("(?m)^T2(?:\\s|$)").containsMatchIn(originalGcode))

            // A user may intentionally print both source colors with one physical slot.
            model.remapFilaments(intArrayOf(1, 2), intArrayOf(2, 2))
            assertEquals("both source colors should now share the chosen logical filament", listOf(2),
                model.getUsedFilaments(0).toList())
            assertEquals(2, model.getVolumeExtruder(0, 0))
            assertEquals(2, model.getVolumeExtruder(0, 1))

            SliceBeam.genCurrentConfig(model)
            model.export3mf(SliceBeam.getCurrentConfigFile().absolutePath, remappedProject.absolutePath)
            reloaded = Model(remappedProject, 1)
            assertEquals("the chosen filament must survive the 3MF round trip", listOf(2),
                reloaded.getUsedFilaments(0).toList())
            assertEquals(2, reloaded.getVolumeExtruder(0, 0))
            assertEquals(2, reloaded.getVolumeExtruder(0, 1))

            SliceBeam.genCurrentConfig(reloaded)
            sliceResult = reloaded.slice(SliceBeam.getCurrentConfigFile().absolutePath, gcodeFile.absolutePath, null)
            val gcode = gcodeFile.readText()
            assertHasExtrusion(gcode)
            assertTrue("the selected slot 2 must be emitted as G-code tool T1",
                Regex("(?m)^T1(?:\\s|$)").containsMatchIn(gcode))
            assertFalse("the original slot 1 must not reappear after slicing",
                Regex("(?m)^T0(?:\\s|$)").containsMatchIn(gcode))
            assertFalse("unassigned slot 3 must not be emitted",
                Regex("(?m)^T2(?:\\s|$)").containsMatchIn(gcode))
        } finally {
            originalSliceResult?.release()
            sliceResult?.release()
            reloaded?.release()
            model.release()
            fixture.delete()
            remappedProject.delete()
            originalGcodeFile.delete()
            gcodeFile.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test
    fun placedObjectGroupRemainsPrintableAfterSurfacePlacement() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelFile = copyCube(context, "placed-group-slice.stl")
        val projectFile = File(context.cacheDir, "placed-group-roundtrip.3mf")
        val model = Model(modelFile)
        var restored: Model? = null
        try {
            val trianglesPerCopy = model.getTriangleCount(0)
            model.addObject(model, 0)
            model.addObject(model, 0)
            model.translate(1, 30.0, -10.0, 8.0)
            model.translate(2, 65.0, 12.0, 14.0)
            assertTrue("the moving group should include a linked copy", model.duplicateInstance(
                1, 0, Vec3d(24.0, 6.0, 0.0),
            ) >= 0)
            model.setUniformFilament(0, 1)
            model.setUniformFilament(1, 2)
            model.setUniformFilament(2, 3)

            fun bounds(index: Int): ObjectBounds3D {
                val min = Vec3d(); val max = Vec3d()
                model.getBoundingBoxExact(index, min, max)
                return ObjectBounds3D(min.x, min.y, min.z, max.x, max.y, max.z)
            }
            val target = bounds(0)
            val movingGroup = requireNotNull(ObjectPlacement.enclosing(listOf(bounds(1), bounds(2))))
            val surfacePoint = Vec3d(
                (target.minX + target.maxX) / 2.0,
                (target.minY + target.maxY) / 2.0,
                target.maxZ,
            )
            val delta = requireNotNull(ObjectPlacement.onSurface(movingGroup, surfacePoint))
            model.translate(1, delta.x, delta.y, delta.z)
            model.translate(2, delta.x, delta.y, delta.z)

            val placed = requireNotNull(ObjectPlacement.enclosing(listOf(bounds(1), bounds(2))))
            assertEquals(target.maxZ, placed.minZ, 0.001)
            // Orca rejects separate ModelObjects whose first printable layer is above
            // the bed. A stacked assembly must become multipart geometry before slicing.
            assertEquals(0, model.combineObjectsAsParts(intArrayOf(0, 1, 2)))
            assertEquals(1, model.objectsCount)
            assertEquals("the placed assembly should materialize all four printable copies",
                trianglesPerCopy * 4, model.getTriangleCount(0))
            assertEquals("surface placement assembly must retain target and placed-part tools",
                listOf(1, 2, 3), model.getUsedFilaments(0).toList())
            SliceBeam.genCurrentConfig(model)
            val exportConfig = SliceBeam.getCurrentConfigFile()
            try {
                model.export3mf(exportConfig.absolutePath, projectFile.absolutePath)
            } finally {
                exportConfig.delete()
            }
            restored = Model(projectFile, 1)
            assertEquals("surface-placed multipart object must survive project reload", 1, restored.objectsCount)
            assertEquals("surface placement must retain the target and placed-part tools after reload",
                listOf(1, 2, 3), restored.getUsedFilaments(0).toList())
            val gcode = sliceToGcode(context, restored, "placed-group-slice.gcode")
            assertHasExtrusion(gcode)
            assertTrue("the placed assembly's second material must reach G-code",
                Regex("(?m)^T1(?:\\s|$)").containsMatchIn(gcode))
            assertTrue("the placed assembly's third material must reach G-code",
                Regex("(?m)^T2(?:\\s|$)").containsMatchIn(gcode))
        } finally {
            restored?.release()
            model.release()
            modelFile.delete()
            projectFile.delete()
        }
    }

    @Test
    fun tiltedSurfacePlacementUsesExactMeshSupportAndIncludesLinkedInstances() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelFile = copyAsset(context, "models/sphere.stl", "tilted-surface-support.stl")
        val model = Model(modelFile)
        try {
            model.translate(0, 13.0, -7.0, 4.0)
            val direction = Vec3d(1.0, 0.0, 1.0)
            val minimumProjection = requireNotNull(model.getMinimumProjection(intArrayOf(0), direction))
            val unit = 1.0 / kotlin.math.sqrt(2.0)
            val min = Vec3d(); val max = Vec3d()
            model.getBoundingBoxExact(0, min, max)
            val boundsMinimum = min.x * unit + min.z * unit
            assertTrue("sphere mesh support should be tighter than its diagonal AABB corner",
                minimumProjection > boundsMinimum + 0.05)

            val targetPoint = Vec3d(40.0, 12.0, 25.0)
            val bounds = ObjectBounds3D(min.x, min.y, min.z, max.x, max.y, max.z)
            val delta = requireNotNull(ObjectPlacement.onSurface(bounds, targetPoint, direction, minimumProjection))
            val targetProjection = targetPoint.x * unit + targetPoint.z * unit
            assertEquals("the actual sphere mesh must touch the picked plane",
                targetProjection, minimumProjection + delta.x * unit + delta.z * unit, 0.001)

            val beforeLinkedInstance = minimumProjection
            assertTrue(model.duplicateInstance(0, 0, Vec3d(-20.0, 0.0, 0.0)) >= 0)
            val withLinkedInstance = requireNotNull(model.getMinimumProjection(intArrayOf(0), direction))
            assertEquals("support should include linked instance transforms", beforeLinkedInstance - 20.0 * unit,
                withLinkedInstance, 0.01)
        } finally {
            model.release()
            modelFile.delete()
        }
    }

    @Test
    fun transformedMultiObjectGroupUsesLowestExactSupportAcrossInstances() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelFile = copyAsset(context, "models/sphere.stl", "transformed-group-support.stl")
        val model = Model(modelFile)
        try {
            model.rotate(0, 17.0, -11.0, 29.0)
            model.scale(0, 1.4, 0.75, 1.2)
            assertTrue("a linked copy should be added to the transformed target",
                model.duplicateInstance(0, 0, Vec3d(-23.0, 8.0, 3.0)) >= 0)
            model.addObject(model, 0)
            assertEquals("a second transformed object should be added", 2, model.objectsCount)
            model.translate(1, 41.0, -13.0, 19.0)

            val normal = Vec3d(1.0, 0.7, 1.4)
            val targetProjection = requireNotNull(model.getMinimumProjection(intArrayOf(0), normal))
            val secondProjection = requireNotNull(model.getMinimumProjection(intArrayOf(1), normal))
            val expectedGroupSupport = minOf(targetProjection, secondProjection)
            val groupSupport = requireNotNull(model.getMinimumProjection(intArrayOf(0, 1), normal))
            assertEquals("group support must use the lowest point across transformed objects and instances",
                expectedGroupSupport, groupSupport, 1e-6)

            val min0 = Vec3d(); val max0 = Vec3d()
            val min1 = Vec3d(); val max1 = Vec3d()
            model.getBoundingBoxExact(0, min0, max0)
            model.getBoundingBoxExact(1, min1, max1)
            val bounds = requireNotNull(ObjectPlacement.enclosing(listOf(
                ObjectBounds3D(min0.x, min0.y, min0.z, max0.x, max0.y, max0.z),
                ObjectBounds3D(min1.x, min1.y, min1.z, max1.x, max1.y, max1.z),
            )))
            val point = Vec3d(12.0, 31.0, 44.0)
            val delta = requireNotNull(ObjectPlacement.onSurface(bounds, point, normal, groupSupport))
            val normalLength = kotlin.math.sqrt(normal.x * normal.x + normal.y * normal.y + normal.z * normal.z)
            val unit = Vec3d(normal.x / normalLength, normal.y / normalLength, normal.z / normalLength)
            val placedSupport = groupSupport + delta.x * unit.x + delta.y * unit.y + delta.z * unit.z
            val planeProjection = point.x * unit.x + point.y * unit.y + point.z * unit.z
            assertEquals("surface placement must seat the transformed group on the picked plane",
                planeProjection, placedSupport, 1e-5)
        } finally {
            model.release()
            modelFile.delete()
        }
    }

    @Test
    fun sideSurfacePlacedObjectsRemainPrintableAfterThreeMfReload() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelFile = copyCube(context, "placed-side-surface.stl")
        val projectFile = File(context.cacheDir, "placed-side-surface-roundtrip.3mf")
        val model = Model(modelFile)
        var restored: Model? = null
        try {
            model.addObject(model, 0)
            model.translate(1, 40.0, 0.0, 0.0)
            model.setUniformFilament(0, 1)
            model.setUniformFilament(1, 2)

            fun bounds(index: Int): ObjectBounds3D {
                val min = Vec3d(); val max = Vec3d()
                model.getBoundingBoxExact(index, min, max)
                return ObjectBounds3D(min.x, min.y, min.z, max.x, max.y, max.z)
            }
            val target = bounds(0)
            val moving = bounds(1)
            val point = Vec3d(
                target.maxX,
                (target.minY + target.maxY) / 2.0,
                (target.minZ + target.maxZ) / 2.0,
            )
            val delta = requireNotNull(ObjectPlacement.onSurface(moving, point, Vec3d(1.0, 0.0, 0.0)))
            model.translate(1, delta.x, delta.y, delta.z)
            val placed = bounds(1)
            assertEquals("the part should touch the selected side face", target.maxX, placed.minX, 0.001)
            assertEquals("the part should center on the tapped face point",
                (target.minY + target.maxY) / 2.0, (placed.minY + placed.maxY) / 2.0, 0.001)
            assertEquals("side placement should retain bed contact", target.minZ, placed.minZ, 0.001)

            assertEquals("side-by-side pieces should become one multipart printable object",
                0, model.combineObjectsAsParts(intArrayOf(0, 1)))
            assertEquals(listOf(1, 2), model.getUsedFilaments(0).toList())
            val exportConfig = SliceBeam.getCurrentConfigFile()
            try {
                model.export3mf(exportConfig.absolutePath, projectFile.absolutePath)
            } finally {
                exportConfig.delete()
            }
            restored = Model(projectFile, 1)
            assertEquals(1, restored.objectsCount)
            assertEquals("3MF reload must preserve both side-joined materials", listOf(1, 2),
                restored.getUsedFilaments(0).toList())
            val gcode = sliceToGcode(context, restored, "placed-side-surface.gcode")
            assertHasExtrusion(gcode)
            assertTrue("the side-placed second material must reach G-code",
                Regex("(?m)^T1(?:\\s|$)").containsMatchIn(gcode))
        } finally {
            restored?.release()
            model.release()
            modelFile.delete()
            projectFile.delete()
        }
    }

    @Test
    fun standaloneTextCanBePlacedJoinedAndSlicedAsItsOwnFilamentPart() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cubeFile = copyCube(context, "text-side-join-cube.stl")
        val fontFile = copyAsset(context, "font/roboto_medium.ttf", "text-side-join-font.ttf")
        val projectFile = File(context.cacheDir, "text-side-join-roundtrip.3mf")
        val model = Model(cubeFile)
        var restored: Model? = null
        var generatedConfig: File? = null
        try {
            val textIndex = model.createTextObject(
                fontFile.absolutePath, "A\nB", 6f, 1.2f,
                Vec3d(100.0, 100.0, 0.0), Vec3d(0.0, 0.0, 1.0),
            )
            assertEquals("standalone text should be a separate editable object", 1, textIndex)
            model.setUniformFilament(0, 1)
            model.setUniformFilament(textIndex, 2)

            fun bounds(index: Int): ObjectBounds3D {
                val min = Vec3d(); val max = Vec3d()
                model.getBoundingBoxExact(index, min, max)
                return ObjectBounds3D(min.x, min.y, min.z, max.x, max.y, max.z)
            }
            val target = bounds(0)
            val text = bounds(textIndex)
            assertTrue("both text lines must exist before placement and join", text.maxY - text.minY > 8.0)
            val point = Vec3d(
                target.maxX,
                (target.minY + target.maxY) / 2.0,
                (target.minZ + target.maxZ) / 2.0,
            )
            val delta = requireNotNull(ObjectPlacement.onSurface(text, point, Vec3d(1.0, 0.0, 0.0)))
            model.translate(textIndex, delta.x, delta.y, delta.z)
            val placedText = bounds(textIndex)
            assertEquals("text should meet the chosen side face", target.maxX, placedText.minX, 0.001)
            assertEquals("text should be centered at the picked face location", point.y,
                (placedText.minY + placedText.maxY) / 2.0, 0.001)

            assertEquals("text and model should become one multipart object", 0,
                model.combineObjectsAsParts(intArrayOf(0, textIndex)))
            assertEquals("text and base material assignments should both survive joining", listOf(1, 2),
                model.getUsedFilaments(0).toList())

            SliceBeam.genCurrentConfig(model)
            generatedConfig = SliceBeam.getCurrentConfigFile()
            model.export3mf(generatedConfig.absolutePath, projectFile.absolutePath)
            generatedConfig.delete()
            generatedConfig = null
            restored = Model(projectFile, 1)
            assertEquals(1, restored.objectsCount)
            assertEquals(listOf(1, 2), restored.getUsedFilaments(0).toList())
            val gcode = sliceToGcode(context, restored, "text-side-join.gcode")
            assertHasExtrusion(gcode)
            assertTrue("base material should be emitted", Regex("(?m)^T0(?:\\s|$)").containsMatchIn(gcode))
            assertTrue("joined standalone text material should be emitted", Regex("(?m)^T1(?:\\s|$)").containsMatchIn(gcode))
        } finally {
            restored?.release()
            model.release()
            cubeFile.delete()
            fontFile.delete()
            projectFile.delete()
            generatedConfig?.delete()
        }
    }

    @Test
    fun booleanResultUsesTargetObjectsFilamentAndStillSlices() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelFile = copyCube(context, "boolean-filament-slice.stl")
        val projectFile = File(context.cacheDir, "boolean-filament-roundtrip.3mf")
        val model = Model(modelFile)
        var restored: Model? = null
        try {
            model.addObject(model, 0)
            model.translate(1, 10.0, 0.0, 0.0)
            model.setUniformFilament(0, 1)
            model.setUniformFilament(1, 2)

            assertEquals(0, model.booleanObjects(0, 1, BooleanOperation.UNION))
            assertEquals("a single Boolean solid should keep the target material", listOf(1), model.getUsedFilaments(0).toList())

            SliceBeam.genCurrentConfig(model)
            val exportConfig = SliceBeam.getCurrentConfigFile()
            try {
                model.export3mf(exportConfig.absolutePath, projectFile.absolutePath)
            } finally {
                exportConfig.delete()
            }
            restored = Model(projectFile, 1)
            assertEquals("the Boolean result should survive project reload", 1, restored.objectsCount)
            assertEquals("reload must keep the target material and omit the removed cutter material",
                listOf(1), restored.getUsedFilaments(0).toList())
            val gcode = sliceToGcode(context, restored, "boolean-filament-slice.gcode")

            assertHasExtrusion(gcode)
            assertFalse("the removed Boolean tool's T1 must not remain assigned", Regex("(?m)^T1(?:\\s|$)").containsMatchIn(gcode))
        } finally {
            restored?.release()
            model.release()
            modelFile.delete()
            projectFile.delete()
        }
    }

    @Test
    fun booleanOperationsPreservePaintedTargetFilamentsThroughThreeMfAndSlice() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (operation in listOf(BooleanOperation.UNION, BooleanOperation.DIFFERENCE, BooleanOperation.INTERSECTION)) {
            val modelFile = copyCube(context, "boolean-painted-${operation.name.lowercase()}.stl")
            val projectFile = File(context.cacheDir, "boolean-painted-${operation.name.lowercase()}.3mf")
            val model = Model(modelFile)
            val paintOverlay = GLModel()
            var restored: Model? = null
            var generatedConfig: File? = null
            try {
                model.setUniformFilament(0, 1)
                model.rotate(0, 0.0, 0.0, Math.toRadians(17.0))
                model.translate(0, 10.0, 10.0, 0.0)
                val min = Vec3d()
                val max = Vec3d()
                model.getBoundingBoxExact(0, min, max)
                val paint = PaintSession(model, 0, GLRenderer.PAINT_MODE_COLOR)
                try {
                    assertTrue("the target cube should support facet painting", paint.isValid())
                    paint.height(min.z + (max.z - min.z) * 0.25, min.z + (max.z - min.z) * 0.75, 2)
                    paint.commit()
                } finally {
                    paint.end()
                }
                assertTrue("the target should contain a painted region before Boolean", model.hasPaint(0))
                assertTrue(model.getUsedFilaments(0).contains(2))
                val oneInstancePaintTriangles = model.buildPaintOverlay(0, paintOverlay, 2)
                assertTrue("the target instance should render its painted facets", oneInstancePaintTriangles > 0)

                val targetWidth = max.x - min.x
                assertTrue("the painted target should accept a second linked instance",
                    model.duplicateInstance(0, 0, Vec3d(targetWidth * 3.0, 0.0, 0.0)) >= 0)
                model.addObject(model, 0)
                model.translate(1, targetWidth * 0.25, 0.0, 0.0)
                assertEquals("${operation.name} should succeed", 0, model.booleanObjects(0, 1, operation))
                assertEquals(1, model.objectsCount)
                assertTrue("${operation.name} must preserve target facet paint", model.hasPaint(0))
                assertTrue("${operation.name} must retain target painted filament 2", model.getUsedFilaments(0).contains(2))
                assertTrue("${operation.name} should remap painted facets from both linked instances",
                    model.buildPaintOverlay(0, paintOverlay, 2) > oneInstancePaintTriangles)

                SliceBeam.genCurrentConfig(model)
                generatedConfig = SliceBeam.getCurrentConfigFile()
                model.export3mf(generatedConfig.absolutePath, projectFile.absolutePath)
                restored = Model(projectFile, 1)
                assertTrue("${operation.name} paint must survive 3MF reload", restored.hasPaint(0))
                assertTrue("${operation.name} filament 2 must survive 3MF reload", restored.getUsedFilaments(0).contains(2))
                val gcode = sliceToGcode(context, restored, "boolean-painted-${operation.name.lowercase()}.gcode")
                assertHasExtrusion(gcode)
                assertTrue("${operation.name} G-code must select painted target tool T1",
                    Regex("(?m)^T1(?:\\s|$)").containsMatchIn(gcode))
            } finally {
                generatedConfig?.delete()
                restored?.release()
                model.release()
                paintOverlay.release()
                modelFile.delete()
                projectFile.delete()
            }
        }
    }

    @Test
    fun paintedSegmentationUsesFilamentCountWhenColorPaletteIsShorter() = withToolChangerProfile {
        assertArrayEquals("Facet state count follows filaments; layer access is bounded by its one-color row",
            intArrayOf(2, 1, 3, 1), Native.painted_segmentation_state_count_probe(2, 1))
        assertArrayEquals("Extra palette entries must not exceed configured filament channels",
            intArrayOf(2, 4, 3, 2), Native.painted_segmentation_state_count_probe(2, 4))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelFile = copyCube(context, "short-color-palette-painted-cube.stl")
        val model = Model(modelFile)
        var generatedConfig: File? = null
        try {
            model.setUniformFilament(0, 1)
            model.ensureOnBed(0)
            val min = Vec3d()
            val max = Vec3d()
            model.getBoundingBoxExact(0, min, max)
            val paint = PaintSession(model, 0, GLRenderer.PAINT_MODE_COLOR)
            try {
                assertTrue("The fixture must support facet painting", paint.isValid())
                paint.height(min.z + (max.z - min.z) * 0.25, min.z + (max.z - min.z) * 0.75, 2)
                paint.commit()
            } finally {
                paint.end()
            }
            assertTrue("Paint must reference filament 2", model.getUsedFilaments(0).contains(2))

            SliceBeam.genCurrentConfig(model)
            generatedConfig = SliceBeam.getCurrentConfigFile()
            val configText = generatedConfig!!.readText()
            assertTrue("The test profile must configure two filament diameters",
                Regex("(?m)^filament_diameter\\s*=\\s*[^,]+,[^,]+\\s*$").containsMatchIn(configText))
            val shortPalette = configText.replace(
                Regex("(?m)^filament_colour\\s*=.*$"),
                "filament_colour = #FF0000",
            )
            assertNotEquals("The generated config must contain a color palette to shorten", configText, shortPalette)
            assertTrue("The test must leave the two-filament configuration intact",
                Regex("(?m)^filament_diameter\\s*=\\s*[^,]+,[^,]+\\s*$").containsMatchIn(shortPalette))
            generatedConfig!!.writeText(shortPalette)

            val output = File(context.cacheDir, "short-color-palette-painted-cube.gcode")
            var result: GCodeProcessorResult? = null
            try {
                result = model.slice(generatedConfig!!.absolutePath, output.absolutePath, null)
                val gcode = output.readText()
                assertHasExtrusion(gcode)
                assertTrue(
                    "The painted second filament must survive slicing when the color palette is shorter than the filament list",
                    Regex("(?m)^T1(?:\\s|$)").containsMatchIn(gcode),
                )
                var activeTool = 0
                val secondToolExtrudes = gcode.lineSequence().any { line ->
                    Regex("^T(\\d+)(?:\\s|$)").find(line)?.let { activeTool = it.groupValues[1].toInt() }
                    activeTool == 1 && line.startsWith("G1 ") &&
                        Regex("\\bE[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)").containsMatchIn(line)
                }
                assertTrue("The second filament must receive deposited toolpath", secondToolExtrudes)
            } finally {
                result?.release()
                output.delete()
            }
        } finally {
            generatedConfig?.delete()
            model.release()
            modelFile.delete()
        }
    }

    @Test
    fun paintedTopAndBottomColorPenetrationChangesTheActualSlicedToolpathDepth() = withToolChangerProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val process = requireNotNull(SliceBeam.CONFIG?.findPrint("0.20mm Standard"))
        val modelFile = copyCube(context, "paint-penetration-cube.stl")
        try {
            fun slicePaintedFace(top: Boolean, penetration: Int): Set<Int> {
                val model = Model(modelFile)
                val paintOverlay = GLModel()
                var generatedConfig: File? = null
                try {
                    model.setUniformFilament(0, 1)
                    model.ensureOnBed(0)
                    val min = Vec3d()
                    val max = Vec3d()
                    model.getBoundingBoxExact(0, min, max)
                    val paint = PaintSession(model, 0, GLRenderer.PAINT_MODE_COLOR)
                    try {
                        assertTrue("Surface fixture must create a paint session", paint.isValid())
                        // The bucket paints only the coplanar top/bottom facets. Height paint also
                        // touches adjacent side facets and cannot isolate penetration depth.
                        val originZ = if (top) max.z + 10.0 else min.z - 10.0
                        val rayZ = if (top) -1.0 else 1.0
                        val hit = requireNotNull(
                            paint.raycast(
                                doubleArrayOf((min.x + max.x) / 2.0, (min.y + max.y) / 2.0, originZ),
                                doubleArrayOf(0.0, 0.0, rayZ),
                            ),
                        ) { "The test ray must hit the ${if (top) "top" else "bottom"} face" }
                        paint.bucket(hit, hit[1].toInt(), 2, true, 1.0)
                        paint.commit()
                    } finally {
                        paint.end()
                    }
                    assertTrue("Surface paint must be present", model.hasPaint(0))
                    assertTrue("Surface paint must assign the second tool", model.getUsedFilaments(0).contains(2))
                    assertTrue("Surface paint must be renderable", model.buildPaintOverlay(0, paintOverlay, 2) > 0)

                    process.put("layer_height", "0.20")
                    process.put("initial_layer_print_height", "0.20")
                    process.put("top_shell_layers", "3")
                    process.put("bottom_shell_layers", "3")
                    process.put("top_color_penetration_layers", if (top) penetration.toString() else "2")
                    process.put("bottom_color_penetration_layers", if (top) "2" else penetration.toString())
                    SliceBeam.genCurrentConfig(model)
                    generatedConfig = SliceBeam.getCurrentConfigFile()
                    val configText = generatedConfig!!.readText()
                    val option = if (top) "top_color_penetration_layers" else "bottom_color_penetration_layers"
                    assertTrue(
                        "Generated config must preserve $option=$penetration",
                        Regex("(?m)^${Regex.escape(option)}\\s*=\\s*$penetration\\s*$").containsMatchIn(configText),
                    )
                    val output = File(context.cacheDir, "paint-penetration-${if (top) "top" else "bottom"}-$penetration.gcode")
                    var result: GCodeProcessorResult? = null
                    try {
                        result = model.slice(generatedConfig!!.absolutePath, output.absolutePath, null)
                        val gcode = output.readText()
                        assertHasExtrusion(gcode)
                        var activeTool = 0
                        var layer = -1
                        val paintedToolLayers = mutableSetOf<Int>()
                        gcode.lineSequence().forEach { line ->
                            if (line.startsWith(";LAYER_CHANGE")) ++layer
                            Regex("^T(\\d+)(?:\\s|$)").find(line)?.let { activeTool = it.groupValues[1].toInt() }
                            if (activeTool == 1 && line.startsWith("G1 ") && Regex("\\bE[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)").containsMatchIn(line)) {
                                if (layer >= 0) paintedToolLayers += layer
                            }
                        }
                        assertTrue("Painted tool must appear in the sliced layers", paintedToolLayers.isNotEmpty())
                        return paintedToolLayers
                    } finally {
                        result?.release()
                        generatedConfig?.delete()
                        generatedConfig = null
                        output.delete()
                    }
                } finally {
                    generatedConfig?.delete()
                    model.release()
                    paintOverlay.release()
                }
            }

            val topLayers = listOf(2, 8).map { slicePaintedFace(top = true, penetration = it) }
            assertTrue("More top paint layers should color deeper below the surface: ${topLayers.map { it.sorted() }}", topLayers[1].min() < topLayers[0].min())
            assertTrue("Extended top penetration must add toolpath layers", topLayers[1].size > topLayers[0].size)

            val bottomLayers = listOf(2, 8).map { slicePaintedFace(top = false, penetration = it) }
            assertTrue("More bottom paint layers should color farther above the surface: ${bottomLayers.map { it.sorted() }}", bottomLayers[1].max() > bottomLayers[0].max())
            assertTrue("Extended bottom penetration must add toolpath layers", bottomLayers[1].size > bottomLayers[0].size)
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun supportPaintOverhangFilterRestrictsBrushBucketAndHeightToDownwardFaces() = withKobraProfile {
        assertArrayEquals(
            "The overhang-angle tolerance includes a face on the configured threshold but not one 1.5 degrees below it",
            intArrayOf(1, 0),
            Native.paint_overhang_boundary_probe(),
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = copyCube(context, "support-overhang-paint-cube.stl")
        val model = Model(source)
        try {
            model.ensureOnBed(0)
            val min = Vec3d(); val max = Vec3d()
            model.getBoundingBoxExact(0, min, max)
            val paint = PaintSession(model, 0, GLRenderer.PAINT_MODE_SUPPORT)
            try {
                assertTrue("The cube must initialize a support paint session", paint.isValid())
                fun ray(z: Double, direction: Double): DoubleArray = requireNotNull(
                    paint.raycast(
                        doubleArrayOf((min.x + max.x) / 2.0, (min.y + max.y) / 2.0, z),
                        doubleArrayOf(0.0, 0.0, direction),
                    ),
                ) { "The vertical ray must hit the cube surface" }

                val top = ray(max.z + 5.0, -1.0)
                paint.brush(top, -1, 3.0, 1, doubleArrayOf(top[2], top[3], max.z + 5.0), false, 45.0)
                assertTrue("The overhang filter must reject the upward-facing top for brush paint", paint.getMesh(1).isEmpty())
                paint.bucket(top, -1, 1, false, 30.0, 45.0)
                assertTrue("The overhang filter must reject the upward-facing top for bucket paint", paint.getMesh(1).isEmpty())

                paint.clear()
                val bottom = ray(min.z - 5.0, 1.0)
                paint.bucket(bottom, -1, 1, false, 30.0, 45.0)
                assertTrue("The overhang filter must accept the downward-facing bottom for bucket paint", paint.getMesh(1).isNotEmpty())

                paint.clear()
                paint.height(min.z - 0.1, max.z + 0.1, 1)
                val unfilteredHeightMeshSize = paint.getMesh(1).size
                assertTrue("The unfiltered height band must select the cube surfaces", unfilteredHeightMeshSize > 0)
                paint.clear()
                paint.height(min.z - 0.1, min.z + 0.1, 1, 45.0)
                val filteredHeightMeshSize = paint.getMesh(1).size
                assertTrue("Height paint must retain bottom overhang facets", filteredHeightMeshSize > 0)
                assertTrue("Height paint must reject the vertical sides selected by its unfiltered range",
                    filteredHeightMeshSize < unfilteredHeightMeshSize)

                paint.clear()
                paint.brush(top, -1, 3.0, 1, doubleArrayOf(top[2], top[3], max.z + 5.0), false, 0.0)
                assertTrue("A disabled angle filter must preserve ordinary top-face painting", paint.getMesh(1).isNotEmpty())
            } finally {
                paint.end()
            }
        } finally {
            model.release()
            source.delete()
        }
    }

    @Test
    fun booleanUnionPreservesExistingEngravedNegativeVolumeThroughReloadAndSlice() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cubeFile = copyCube(context, "boolean-negative-volume-cube.stl")
        val fontFile = copyAsset(context, "font/roboto_medium.ttf", "boolean-negative-volume-font.ttf")
        val projectFile = File(context.cacheDir, "boolean-negative-volume-roundtrip.3mf")
        val plain = Model(cubeFile)
        val engraved = Model(cubeFile)
        var restored: Model? = null
        var generatedConfig: File? = null
        try {
            fun addDisjointCutter(model: Model) {
                val min = Vec3d(); val max = Vec3d()
                model.getBoundingBoxExact(0, min, max)
                val width = max.x - min.x
                model.addObject(model, 0)
                model.translate(1, width * 3.0, 0.0, 0.0)
            }

            addDisjointCutter(plain)
            assertEquals("plain control union should succeed", 0,
                plain.booleanObjects(intArrayOf(0, 1), BooleanOperation.UNION))
            val plainUnionVolume = plain.getObjectVolume(0)
            assertTrue("plain union should have a valid solid volume", plainUnionVolume > 0.0)

            val min = Vec3d(); val max = Vec3d()
            engraved.getBoundingBoxExact(0, min, max)
            engraved.setUniformFilament(0, 1)
            engraved.embossText(
                0, fontFile.absolutePath, "B", 5f, 0.8f, 1,
                Vec3d((min.x + max.x) / 2.0, (min.y + max.y) / 2.0, max.z),
                Vec3d(0.0, 0.0, 1.0),
            )
            addDisjointCutter(engraved)
            assertEquals("union with a previously engraved target should succeed", 0,
                engraved.booleanObjects(intArrayOf(0, 1), BooleanOperation.UNION))
            assertTrue("Boolean union must retain the existing engraved cut instead of restoring the solid surface",
                engraved.getObjectVolume(0) < plainUnionVolume - 0.01)

            SliceBeam.genCurrentConfig(engraved)
            generatedConfig = SliceBeam.getCurrentConfigFile()
            engraved.export3mf(generatedConfig.absolutePath, projectFile.absolutePath)
            generatedConfig.delete()
            generatedConfig = null
            restored = Model(projectFile, 1)
            assertEquals("Boolean result should remain one printable object after reload", 1, restored.objectsCount)
            assertEquals("Boolean result should keep the target filament assignment", listOf(1),
                restored.getUsedFilaments(0).toList())
            assertTrue("the engraved cut should remain in the reloaded Boolean mesh",
                restored.getObjectVolume(0) < plainUnionVolume - 0.01)
            val gcode = sliceToGcode(context, restored, "boolean-negative-volume-roundtrip.gcode")
            assertHasExtrusion(gcode)
        } finally {
            restored?.release()
            plain.release()
            engraved.release()
            cubeFile.delete()
            fontFile.delete()
            projectFile.delete()
            generatedConfig?.delete()
        }
    }

    @Test
    fun booleanDifferenceAndIntersectionRespectExistingEngravedNegativeVolume() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cubeFile = copyCube(context, "boolean-negative-volume-ops-cube.stl")
        val fontFile = copyAsset(context, "font/roboto_medium.ttf", "boolean-negative-volume-ops-font.ttf")
        try {
            fun centerCutter(target: Model, cutter: Model, operation: BooleanOperation) {
                val targetMin = Vec3d(); val targetMax = Vec3d()
                target.getBoundingBoxExact(0, targetMin, targetMax)
                val targetCenterX = (targetMin.x + targetMax.x) / 2.0
                val targetCenterY = (targetMin.y + targetMax.y) / 2.0
                val targetCenterZ = (targetMin.z + targetMax.z) / 2.0
                when (operation) {
                    BooleanOperation.DIFFERENCE -> cutter.scale(0, 0.35, 0.35, 0.35)
                    // Keep the intersection connected to the build plate while still crossing
                    // the engraved top face, so the resulting part can be sliced normally.
                    BooleanOperation.INTERSECTION -> cutter.scale(0, 0.45, 0.45, 1.2)
                    else -> error("Only targeted difference and intersection cases are expected")
                }
                val cutterMin = Vec3d(); val cutterMax = Vec3d()
                cutter.getBoundingBoxExact(0, cutterMin, cutterMax)
                val cutterCenterX = if (operation == BooleanOperation.DIFFERENCE) targetMax.x - 2.0 else targetCenterX
                val cutterCenterY = targetCenterY
                val cutterCenterZ = targetCenterZ
                cutter.translate(
                    0,
                    cutterCenterX - (cutterMin.x + cutterMax.x) / 2.0,
                    cutterCenterY - (cutterMin.y + cutterMax.y) / 2.0,
                    cutterCenterZ - (cutterMin.z + cutterMax.z) / 2.0,
                )
            }

            for (operation in listOf(BooleanOperation.DIFFERENCE, BooleanOperation.INTERSECTION)) {
                val plainTarget = Model(cubeFile)
                val plainCutter = Model(cubeFile)
                val engravedTarget = Model(cubeFile)
                val engravedCutter = Model(cubeFile)
                try {
                    centerCutter(plainTarget, plainCutter, operation)
                    plainTarget.addObject(plainCutter, 0)
                    assertEquals("plain ${operation.name} control should succeed", 0,
                        plainTarget.booleanObjects(intArrayOf(0, 1), operation))
                    val plainVolume = plainTarget.getObjectVolume(0)

                    val min = Vec3d(); val max = Vec3d()
                    engravedTarget.getBoundingBoxExact(0, min, max)
                    engravedTarget.setUniformFilament(0, 1)
                    engravedTarget.embossText(
                        0, fontFile.absolutePath, "B", 5f, 0.8f, 1,
                        Vec3d((min.x + max.x) / 2.0, (min.y + max.y) / 2.0, max.z),
                        Vec3d(0.0, 0.0, 1.0),
                    )
                    centerCutter(engravedTarget, engravedCutter, operation)
                    engravedTarget.addObject(engravedCutter, 0)
                    assertEquals("engraved ${operation.name} should succeed", 0,
                        engravedTarget.booleanObjects(intArrayOf(0, 1), operation))
                    assertTrue("${operation.name} must apply the engraved negative volume before its operation",
                        engravedTarget.getObjectVolume(0) < plainVolume - 0.01)
                    assertEquals("${operation.name} should retain the target material", listOf(1),
                        engravedTarget.getUsedFilaments(0).toList())
                    assertHasExtrusion(sliceToGcode(
                        context, engravedTarget, "boolean-negative-${operation.name.lowercase()}.gcode",
                    ))
                } finally {
                    plainTarget.release()
                    plainCutter.release()
                    engravedTarget.release()
                    engravedCutter.release()
                }
            }
        } finally {
            cubeFile.delete()
            fontFile.delete()
        }
    }

    @Test
    fun booleanOperationsRespectEngravedNegativeVolumesOnToolAndPreserveTargetFilament() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cubeFile = copyCube(context, "boolean-negative-tool-cube.stl")
        val fontFile = copyAsset(context, "font/roboto_medium.ttf", "boolean-negative-tool-font.ttf")
        try {
            for (operation in listOf(
                BooleanOperation.UNION,
                BooleanOperation.DIFFERENCE,
                BooleanOperation.INTERSECTION,
            )) {
                val plain = Model(cubeFile)
                val engravedTool = Model(cubeFile)
                val projectFile = File(context.cacheDir, "boolean-negative-tool-${operation.name.lowercase()}.3mf")
                var restored: Model? = null
                var generatedConfig: File? = null
                try {
                    fun configureOperands(model: Model, engraveTool: Boolean) {
                        val min = Vec3d(); val max = Vec3d()
                        model.getBoundingBoxExact(0, min, max)
                        val width = max.x - min.x
                        model.addObject(model, 0)
                        model.setUniformFilament(0, 2)
                        model.setUniformFilament(1, 3)
                        if (engraveTool) {
                            model.embossText(
                                1, fontFile.absolutePath, "B", 5f, 0.8f, 1,
                                Vec3d((min.x + max.x) / 2.0, (min.y + max.y) / 2.0, max.z),
                                Vec3d(0.0, 0.0, 1.0),
                            )
                        }
                        // Keep the union disjoint so the target cannot fill the tool's engraved
                        // cutout. Difference/intersection overlap at the engraved text to expose
                        // whether the Boolean operation evaluates the tool's negative volume.
                        val toolOffsetX = if (operation == BooleanOperation.UNION) width * 3.0 else width / 2.0
                        model.translate(1, toolOffsetX, 0.0, 0.0)
                    }

                    configureOperands(plain, engraveTool = false)
                    configureOperands(engravedTool, engraveTool = true)
                    assertEquals("plain ${operation.name} control should succeed", 0,
                        plain.booleanObjects(intArrayOf(0, 1), operation))
                    assertEquals("${operation.name} with an engraved tool should succeed", 0,
                        engravedTool.booleanObjects(intArrayOf(0, 1), operation))

                    val plainVolume = plain.getObjectVolume(0)
                    val engravedVolume = engravedTool.getObjectVolume(0)
                    assertTrue("plain ${operation.name} control must remain printable", plainVolume > 0.0)
                    when (operation) {
                        BooleanOperation.UNION, BooleanOperation.INTERSECTION -> assertTrue(
                            "${operation.name} must preserve the tool cutout instead of filling it",
                            engravedVolume < plainVolume - 0.01,
                        )
                        BooleanOperation.DIFFERENCE -> assertTrue(
                            "DIFFERENCE must subtract only the tool's printable shape, leaving its cutout in the target",
                            engravedVolume > plainVolume + 0.01,
                        )
                    }
                    assertEquals("Boolean output must use the target's second material, not the tool's third",
                        listOf(2), engravedTool.getUsedFilaments(0).toList())

                    SliceBeam.genCurrentConfig(engravedTool)
                    generatedConfig = SliceBeam.getCurrentConfigFile()
                    engravedTool.export3mf(generatedConfig.absolutePath, projectFile.absolutePath)
                    generatedConfig.delete()
                    generatedConfig = null
                    restored = Model(projectFile, 1)
                    assertEquals("${operation.name} tool-side result should survive 3MF reload", 1,
                        restored.objectsCount)
                    assertEquals("3MF reload must retain the target material only", listOf(2),
                        restored.getUsedFilaments(0).toList())
                    assertEquals("${operation.name} volume must survive 3MF reload", engravedVolume,
                        restored.getObjectVolume(0), maxOf(0.05, engravedVolume * 0.001))
                    val gcode = sliceToGcode(
                        context, restored, "boolean-negative-tool-${operation.name.lowercase()}.gcode",
                    )
                    assertHasExtrusion(gcode)
                    assertTrue("sliced output must select the target's T1 slot",
                        Regex("(?m)^T1(?:\\s|$)").containsMatchIn(gcode))
                    assertFalse("sliced output must not select the removed tool's T2 slot",
                        Regex("(?m)^T2(?:\\s|$)").containsMatchIn(gcode))
                } finally {
                    restored?.release()
                    plain.release()
                    engravedTool.release()
                    projectFile.delete()
                    generatedConfig?.delete()
                }
            }
        } finally {
            cubeFile.delete()
            fontFile.delete()
        }
    }

    @Test
    fun multipartJoinPreservesExistingEngravedNegativeVolumeThroughReloadAndSlice() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cubeFile = copyCube(context, "join-negative-volume-cube.stl")
        val fontFile = copyAsset(context, "font/roboto_medium.ttf", "join-negative-volume-font.ttf")
        val projectFile = File(context.cacheDir, "join-negative-volume-roundtrip.3mf")
        val target = Model(cubeFile)
        val part = Model(cubeFile)
        var restored: Model? = null
        var generatedConfig: File? = null
        try {
            val min = Vec3d(); val max = Vec3d()
            target.getBoundingBoxExact(0, min, max)
            target.setUniformFilament(0, 1)
            target.embossText(
                0, fontFile.absolutePath, "B", 5f, 0.8f, 1,
                Vec3d((min.x + max.x) / 2.0, (min.y + max.y) / 2.0, max.z),
                Vec3d(0.0, 0.0, 1.0),
            )
            part.setUniformFilament(0, 2)
            val partMin = Vec3d(); val partMax = Vec3d()
            part.getBoundingBoxExact(0, partMin, partMax)
            target.addObject(part, 0)
            target.translate(1, (max.x - min.x) * 1.5, 0.0, 0.0)

            assertEquals("joining the engraved object and another printable part should succeed", 0,
                target.combineObjectsAsParts(intArrayOf(0, 1)))
            assertEquals("joined result should retain both assigned materials", listOf(1, 2),
                target.getUsedFilaments(0).toList())
            SliceBeam.genCurrentConfig(target)
            generatedConfig = SliceBeam.getCurrentConfigFile()
            target.export3mf(generatedConfig.absolutePath, projectFile.absolutePath)
            generatedConfig.delete()
            generatedConfig = null
            restored = Model(projectFile, 1)
            assertEquals(1, restored.objectsCount)
            assertEquals("joined 3MF should retain both material parts", listOf(1, 2),
                restored.getUsedFilaments(0).toList())
            val configText = ZipFile(projectFile).use { archive ->
                val entry = archive.getEntry("Metadata/model_settings.config")
                    ?: archive.getEntry("Metadata/Slic3r_PE_model.config")
                    ?: error("3MF model config is missing")
                archive.getInputStream(entry).bufferedReader().use { it.readText() }
            }
            assertTrue("multipart join should keep the engraved negative volume", configText.contains("negative_part"))
            assertHasExtrusion(sliceToGcode(context, restored, "join-negative-volume-roundtrip.gcode"))
        } finally {
            restored?.release()
            target.release()
            part.release()
            cubeFile.delete()
            fontFile.delete()
            projectFile.delete()
            generatedConfig?.delete()
        }
    }

    @Test
    fun booleanOnMultipartBaseUsesItsFirstPartMaterialAndSlices() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelFile = copyCube(context, "boolean-multipart-base.stl")
        val projectFile = File(context.cacheDir, "boolean-multipart-base-roundtrip.3mf")
        val model = Model(modelFile)
        var restored: Model? = null
        try {
            model.addObject(model, 0)
            model.addObject(model, 0)
            // The first base volume is deliberately not the lowest used filament.
            model.setUniformFilament(0, 2)
            model.setUniformFilament(1, 1)
            model.setUniformFilament(2, 3)
            model.translate(1, 50.0, 0.0, 0.0)
            model.translate(2, 55.0, 0.0, 0.0)

            assertEquals("the target should combine into a two-material multipart object", 0,
                model.combineObjectsAsParts(intArrayOf(0, 1)))
            assertEquals(listOf(1, 2), model.getUsedFilaments(0).toList())
            assertEquals("the cutter remains a separate third object", 2, model.objectsCount)

            assertEquals("Boolean union should accept the disconnected multipart base", 0,
                model.booleanObjects(0, 1, BooleanOperation.UNION))
            assertEquals("Boolean uses the first base volume's material, even when it is not the lowest slot",
                listOf(2), model.getUsedFilaments(0).toList())
            assertTrue("multipart-base Boolean output remains printable", model.getObjectVolume(0) > 0.0)

            SliceBeam.genCurrentConfig(model)
            val exportConfig = SliceBeam.getCurrentConfigFile()
            try {
                model.export3mf(exportConfig.absolutePath, projectFile.absolutePath)
            } finally {
                exportConfig.delete()
            }
            restored = Model(projectFile, 1)
            assertEquals("the CSG output survives 3MF reload", 1, restored.objectsCount)
            assertEquals("the documented first base-part material survives reload", listOf(2),
                restored.getUsedFilaments(0).toList())
            val gcode = sliceToGcode(context, restored, "boolean-multipart-base.gcode")
            assertHasExtrusion(gcode)
            assertTrue("the first base volume's filament should use T1",
                Regex("(?m)^T1(?:\\s|$)").containsMatchIn(gcode))
            assertFalse("other base-part and cutter tools must not be emitted",
                Regex("(?m)^(?:T0|T2)(?:\\s|$)").containsMatchIn(gcode))
        } finally {
            restored?.release()
            model.release()
            modelFile.delete()
            projectFile.delete()
        }
    }

    @Test
    fun booleanUnionConsumesEverySelectedToolAndSlicesTheCombinedResult() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelFile = copyCube(context, "boolean-multiple-tools.stl")
        val projectFile = File(context.cacheDir, "boolean-multiple-tools.3mf")
        val model = Model(modelFile)
        var restored: Model? = null
        var generatedConfig: File? = null
        try {
            val min = Vec3d(); val max = Vec3d()
            model.getBoundingBoxExact(0, min, max)
            val width = max.x - min.x
            val sourceVolume = model.getObjectVolume(0)
            assertTrue("fixture must have a finite printable volume", sourceVolume > 0.0)

            model.addObject(model, 0)
            model.addObject(model, 0)
            model.translate(1, width / 2.0, 0.0, 0.0)
            model.translate(2, width, 0.0, 0.0)
            model.setUniformFilament(0, 1)
            model.setUniformFilament(1, 2)
            model.setUniformFilament(2, 3)

            assertEquals("one target and both selected tools should be unioned", 0,
                model.booleanObjects(intArrayOf(0, 1, 2), BooleanOperation.UNION))
            assertEquals("every selected tool should be consumed", 1, model.objectsCount)
            assertEquals("Boolean result should keep only the target object's material", listOf(1),
                model.getUsedFilaments(0).toList())
            assertEquals("three overlapping cubes should form a two-cube-wide solid", 2.0,
                model.getObjectVolume(0) / sourceVolume, 0.04)

            SliceBeam.genCurrentConfig(model)
            generatedConfig = SliceBeam.getCurrentConfigFile()
            model.export3mf(generatedConfig.absolutePath, projectFile.absolutePath)
            generatedConfig.delete()
            generatedConfig = null
            restored = Model(projectFile, 1)
            assertEquals(1, restored.objectsCount)
            assertEquals(listOf(1), restored.getUsedFilaments(0).toList())
            val gcode = sliceToGcode(context, restored, "boolean-multiple-tools.gcode")
            assertHasExtrusion(gcode)
            val toolChanges = Regex("(?m)^T\\d+.*$").findAll(gcode).map { it.value }.distinct().toList()
            // A one-tool slice can use T0 implicitly without a redundant T0 command.
            assertFalse("consumed tool materials must not leak into G-code; tools=$toolChanges",
                Regex("(?m)^(?:T1|T2)(?:\\s|$)").containsMatchIn(gcode))
        } finally {
            restored?.release()
            model.release()
            modelFile.delete()
            projectFile.delete()
            generatedConfig?.delete()
        }
    }

    @Test
    fun joiningLinkedInstancesSurvivesThreeMfAndSlicesEveryCopyWithItsMaterial() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelFile = copyCube(context, "join-linked-instance-slice-cube.stl")
        val projectFile = File(context.cacheDir, "join-linked-instance-slice.3mf")
        val gcodeFile = File(context.cacheDir, "join-linked-instance-slice.gcode")
        val model = Model(modelFile)
        var restored: Model? = null
        var generatedConfig: File? = null
        var sliceResult: GCodeProcessorResult? = null
        try {
            val min = Vec3d(); val max = Vec3d()
            model.getBoundingBoxExact(0, min, max)
            val width = max.x - min.x
            val sourceVolume = model.getObjectVolume(0)
            val sourceTriangles = model.getTriangleCount(0)
            assertTrue("test cube must have positive size and volume", width > 0.0 && sourceVolume > 0.0)
            assertEquals(1, model.duplicateInstance(0, 0, Vec3d(width + 8.0, 0.0, 0.0)))
            model.addObject(model, 0)
            model.setUniformFilament(0, 1)
            model.setUniformFilament(1, 2)
            model.translate(1, width * 3.0 + 24.0, 0.0, 0.0)

            assertEquals("join should merge two two-instance objects", 0,
                model.combineObjectsAsParts(intArrayOf(0, 1)))
            assertEquals(1, model.objectsCount)
            assertEquals("each linked instance must become a separately printable part",
                sourceTriangles * 4, model.getTriangleCount(0))
            assertEquals("all four copies must contribute printable volume", sourceVolume * 4.0,
                model.getObjectVolume(0), sourceVolume * 0.001)
            assertEquals("both source materials must stay mapped", listOf(1, 2), model.getUsedFilaments(0).toList())

            val joinedMin = Vec3d(); val joinedMax = Vec3d()
            model.getBoundingBoxExact(0, joinedMin, joinedMax)
            val expectedMaxX = width * 5.0 + 32.0
            assertEquals("first copy stays at the original world-space origin", 0.0, joinedMin.x, 0.02)
            assertEquals("last linked copy keeps its translated world-space position", expectedMaxX, joinedMax.x, 0.02)

            SliceBeam.genCurrentConfig(model)
            generatedConfig = SliceBeam.getCurrentConfigFile()
            model.export3mf(generatedConfig.absolutePath, projectFile.absolutePath)
            generatedConfig.delete()
            generatedConfig = null
            restored = Model(projectFile, 1)
            assertEquals(1, restored.objectsCount)
            assertEquals("3MF round-trip keeps all four converted parts", sourceTriangles * 4,
                restored.getTriangleCount(0))
            assertEquals("3MF round-trip retains both source filaments", listOf(1, 2),
                restored.getUsedFilaments(0).toList())

            SliceBeam.genCurrentConfig(restored)
            generatedConfig = SliceBeam.getCurrentConfigFile()
            sliceResult = restored.slice(generatedConfig.absolutePath, gcodeFile.absolutePath, null)
            val gcode = gcodeFile.readText()
            assertHasExtrusion(gcode)
            assertTrue("first joined material must reach G-code as T0",
                Regex("(?m)^T0(?:\\s|$)").containsMatchIn(gcode))
            assertTrue("second joined material must reach G-code as T1",
                Regex("(?m)^T1(?:\\s|$)").containsMatchIn(gcode))
        } finally {
            sliceResult?.release()
            restored?.release()
            model.release()
            modelFile.delete()
            projectFile.delete()
            gcodeFile.delete()
            generatedConfig?.delete()
        }
    }

    @Test
    fun booleanDifferenceAndIntersectionSurviveThreeMfReloadAndStillSlice() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelFile = copyCube(context, "boolean-operations-roundtrip.stl")
        try {
            listOf(BooleanOperation.DIFFERENCE, BooleanOperation.INTERSECTION).forEach { operation ->
                val projectFile = File(context.cacheDir, "boolean-${operation.name.lowercase()}-roundtrip.3mf")
                val model = Model(modelFile)
                var restored: Model? = null
                try {
                    val min = Vec3d(); val max = Vec3d()
                    model.getBoundingBoxExact(0, min, max)
                    val sourceVolume = model.getObjectVolume(0)
                    model.addObject(model, 0)
                    model.translate(1, (max.x - min.x) / 2.0, 0.0, 0.0)
                    model.setUniformFilament(0, 1)
                    model.setUniformFilament(1, 2)

                    val result = model.booleanObjects(0, 1, operation)
                    assertEquals("$operation should replace both operands", 0, result)
                    assertEquals("$operation should leave one printable object", 1, model.objectsCount)
                    assertEquals("$operation should produce the expected half-volume overlap", 0.5,
                        model.getObjectVolume(result) / sourceVolume, 0.03)
                    assertEquals("the Boolean output should use the target object's material",
                        listOf(1), model.getUsedFilaments(result).toList())

                    SliceBeam.genCurrentConfig(model)
                    val exportConfig = SliceBeam.getCurrentConfigFile()
                    try {
                        model.export3mf(exportConfig.absolutePath, projectFile.absolutePath)
                    } finally {
                        exportConfig.delete()
                    }
                    val modelXml = ZipFile(projectFile).use { archive ->
                        archive.getInputStream(archive.getEntry("3D/3dmodel.model"))
                            .bufferedReader().use { it.readText() }
                    }
                    assertFalse(
                        "$operation's generated mesh must not retain stale source object metadata",
                        listOf("key=\"source_file\"", "key=\"source_object_id\"", "key=\"source_volume_id\"")
                            .any(modelXml::contains),
                    )
                    restored = Model(projectFile, 1)
                    assertEquals("$operation result should survive 3MF reload", 1, restored.objectsCount)
                    assertEquals("$operation reload must not restore the removed cutter's material",
                        listOf(1), restored.getUsedFilaments(0).toList())
                    assertEquals("$operation geometry volume should survive reload", 0.5,
                        restored.getObjectVolume(0) / sourceVolume, 0.03)

                    val gcode = sliceToGcode(context, restored, "boolean-${operation.name.lowercase()}-roundtrip.gcode")
                    assertHasExtrusion(gcode)
                    assertFalse("$operation G-code must not use the removed cutter's T1",
                        Regex("(?m)^T1(?:\\s|$)").containsMatchIn(gcode))
                } finally {
                    restored?.release()
                    model.release()
                    projectFile.delete()
                }
            }
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun booleanDifferenceAndIntersectionConsumeMultipleSelectedTools() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelFile = copyCube(context, "boolean-multiple-cutters.stl")
        try {
            for (operation in listOf(BooleanOperation.DIFFERENCE, BooleanOperation.INTERSECTION)) {
                val projectFile = File(context.cacheDir, "boolean-multiple-${operation.name.lowercase()}.3mf")
                val model = Model(modelFile)
                var restored: Model? = null
                var generatedConfig: File? = null
                try {
                    val targetMin = Vec3d(); val targetMax = Vec3d()
                    model.getBoundingBoxExact(0, targetMin, targetMax)
                    val depth = targetMax.y - targetMin.y
                    val targetVolume = model.getObjectVolume(0)
                    model.addObject(model, 0)
                    model.addObject(model, 0)

                    val halfWidth = (targetMax.x - targetMin.x) / 2.0
                    when (operation) {
                        BooleanOperation.DIFFERENCE -> {
                            // The first cutter removes the right half. The second removes a
                            // smaller overlapping quarter from the remaining half.
                            model.translate(1, halfWidth, 0.0, 0.0)
                            model.translate(2, halfWidth / 2.0, depth / 2.0, 0.0)
                        }
                        BooleanOperation.INTERSECTION -> {
                            // Intersect the target with its right half and upper half.
                            model.translate(1, halfWidth, 0.0, 0.0)
                            model.translate(2, 0.0, depth / 2.0, 0.0)
                        }
                        BooleanOperation.UNION -> error("not exercised by this test")
                    }
                    model.setUniformFilament(0, 1)
                    model.setUniformFilament(1, 2)
                    model.setUniformFilament(2, 3)

                    assertEquals("$operation should apply both selected cutters in order", 0,
                        model.booleanObjects(intArrayOf(0, 1, 2), operation))
                    assertEquals("$operation should consume both tools", 1, model.objectsCount)
                    assertEquals("$operation should retain the target's filament", listOf(1),
                        model.getUsedFilaments(0).toList())
                    val resultVolume = model.getObjectVolume(0)
                    assertTrue("$operation should leave a non-empty changed solid; volume=$resultVolume",
                        resultVolume > 0.0 && resultVolume < targetVolume)

                    SliceBeam.genCurrentConfig(model)
                    generatedConfig = SliceBeam.getCurrentConfigFile()
                    model.export3mf(generatedConfig.absolutePath, projectFile.absolutePath)
                    generatedConfig.delete()
                    generatedConfig = null
                    restored = Model(projectFile, 1)
                    assertEquals("$operation result should survive 3MF reload", 1, restored.objectsCount)
                    assertEquals(listOf(1), restored.getUsedFilaments(0).toList())
                    val gcode = sliceToGcode(context, restored, "boolean-multiple-${operation.name.lowercase()}.gcode")
                    assertHasExtrusion(gcode)
                    assertFalse("$operation must not emit consumed cutter tools",
                        Regex("(?m)^(?:T1|T2)(?:\\s|$)").containsMatchIn(gcode))
                } finally {
                    restored?.release()
                    model.release()
                    projectFile.delete()
                    generatedConfig?.delete()
                }
            }
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun importedSvgSolidProducesExtrusionGcode() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val application = context.applicationContext as Application
        SliceBeam.INSTANCE = application as SliceBeam
        Prefs.init(application)

        val previousConfig = SliceBeam.CONFIG
        context.assets.open("orca_profiles/Anycubic.ini").use { SliceBeam.CONFIG = Slic3rConfigWrapper(it) }
        val config = requireNotNull(SliceBeam.CONFIG)
        config.presets = ConfigObject("presets").apply {
            put("printer", requireNotNull(config.findPrinter("Anycubic Kobra 3 0.4 nozzle")).title)
            put("print", requireNotNull(config.findPrint("0.16mm Standard")).title)
            put("filament", requireNotNull(config.findFilament("Anycubic Generic PLA")).title)
        }

        val svgFile = File(context.cacheDir, "native-svg-slice-test.svg")
        val gcodeFile = File(context.cacheDir, "native-svg-slice-test.gcode")
        var model: Model? = null
        var sliceResult: GCodeProcessorResult? = null
        var generatedConfig: File? = null
        try {
            svgFile.writeText(
                """<svg xmlns="http://www.w3.org/2000/svg" width="24mm" height="18mm" viewBox="0 0 24 18">
                    <path id="frame" fill="#202020" d="M1 1 H23 V17 H1 Z M7 6 H17 V12 H7 Z"/>
                </svg>""".trimIndent(),
            )
            model = Model(svgFile)
            assertEquals("SVG import must expose printable mesh geometry", 1, model.objectsCount)
            assertTrue(model.getTriangleCount(0) > 0)
            assertTrue(model.getObjectVolume(0) > 0.0)

            SliceBeam.genCurrentConfig(model)
            generatedConfig = SliceBeam.getCurrentConfigFile()
            sliceResult = model.slice(generatedConfig.absolutePath, gcodeFile.absolutePath, null)
            val gcode = gcodeFile.readText()
            assertTrue("Imported SVG should produce non-empty G-code", gcode.isNotBlank())
            assertTrue("Imported SVG should produce deposited toolpaths", Regex("(?m)^G1 .*\\bE[0-9.-]+").containsMatchIn(gcode))
            Log.i("NativeSliceDeviceTest", "PASS SVG G-code bytes=${gcodeFile.length()}")
        } finally {
            sliceResult?.release()
            model?.release()
            svgFile.delete()
            gcodeFile.delete()
            generatedConfig?.delete()
            SliceBeam.CONFIG = previousConfig
        }
    }

    @Test
    fun suppliedMulticolorOmaSvgRoundTripsAndProducesExtrusionGcode() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val svgFile = File(context.cacheDir, "supplied-oma-heart-slice.svg")
        InstrumentationRegistry.getInstrumentation().context.assets
            .open("models/Beste_Oma_Kind_Herz_3D_Druck.svg").use { input ->
                svgFile.outputStream().use(input::copyTo)
            }
        val projectFile = File(context.cacheDir, "supplied-oma-heart-roundtrip.3mf")
        val gcodeFile = File(context.cacheDir, "supplied-oma-heart.gcode")
        var model: Model? = null
        var restored: Model? = null
        var generatedConfig: File? = null
        var sliceResult: GCodeProcessorResult? = null
        try {
            model = Model(svgFile)
            assertEquals("the source SVG should load as one workspace model", 1, model.objectsCount)
            assertTrue("the source SVG should contain printable mesh facets", model.getTriangleCount(0) > 0)
            assertTrue("the source SVG should create a closed printable body", model.getObjectVolume(0) > 0.0)
            assertEquals("the SVG's red, white, and black fill groups should map to three tools",
                listOf(1, 2, 3), model.getUsedFilaments(0).toList())

            SliceBeam.genCurrentConfig(model)
            generatedConfig = SliceBeam.getCurrentConfigFile()
            model.export3mf(generatedConfig.absolutePath, projectFile.absolutePath)
            generatedConfig.delete()
            generatedConfig = null

            restored = Model(projectFile, 1)
            assertEquals("the exported project should retain the SVG model", 1, restored.objectsCount)
            assertTrue("the 3MF round trip should retain printable geometry", restored.getTriangleCount(0) > 0)
            assertEquals("the SVG's filament assignments should survive the project round trip",
                model.getUsedFilaments(0).toList(), restored.getUsedFilaments(0).toList())
            assertEquals("the 3MF round trip must preserve all three SVG color tools",
                listOf(1, 2, 3), restored.getUsedFilaments(0).toList())

            SliceBeam.genCurrentConfig(restored)
            generatedConfig = SliceBeam.getCurrentConfigFile()
            sliceResult = restored.slice(generatedConfig.absolutePath, gcodeFile.absolutePath, null)
            val gcode = gcodeFile.readText()
            assertHasExtrusion(gcode)
            for (tool in 0..2) {
                assertTrue("the multicolor SVG should emit tool T$tool", Regex("(?m)^T$tool(?:\\s|$)").containsMatchIn(gcode))
            }
            Log.i("NativeSliceDeviceTest", "PASS supplied multicolor SVG G-code bytes=${gcodeFile.length()}, " +
                "facets=${restored.getTriangleCount(0)}, filaments=${restored.getUsedFilaments(0).toList()}")
        } finally {
            sliceResult?.release()
            model?.release()
            restored?.release()
            svgFile.delete()
            projectFile.delete()
            gcodeFile.delete()
            generatedConfig?.delete()
        }
    }

    @Test
    fun suppliedSvgCanBeRaisedOrEngravedOnASurfaceAndStillSlicesAfterThreeMfReload() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val svgFile = File(context.cacheDir, "surface-emboss-oma.svg")
        InstrumentationRegistry.getInstrumentation().context.assets
            .open("models/Beste_Oma_Kind_Herz_3D_Druck.svg").use { input ->
                svgFile.outputStream().use(input::copyTo)
            }
        val cubeFile = copyCube(context, "surface-emboss-target.stl")
        val projectFile = File(context.cacheDir, "surface-emboss-roundtrip.3mf")
        val baselineFile = File(context.cacheDir, "surface-emboss-baseline.gcode")
        val gcodeFile = File(context.cacheDir, "surface-emboss-roundtrip.gcode")
        try {
            for (engraved in listOf(false, true)) {
                val model = Model(cubeFile)
                var restored: Model? = null
                var baselineResult: GCodeProcessorResult? = null
                var result: GCodeProcessorResult? = null
                try {
                    model.ensureOnBed(0)
                    SliceBeam.genCurrentConfig(model)
                    baselineResult = model.slice(
                        SliceBeam.getCurrentConfigFile().absolutePath,
                        baselineFile.absolutePath,
                        null,
                    )
                    val baselineToolpaths = baselineFile.readLines().filter { it.startsWith("G1") && it.contains(" E") }
                    assertTrue("the unmodified cube should produce extrusion", baselineToolpaths.isNotEmpty())
                    baselineResult.release()
                    baselineResult = null

                    val min = Vec3d(); val max = Vec3d()
                    model.getBoundingBoxExact(0, min, max)
                    val tap = Vec3d((min.x + max.x) / 2.0, (min.y + max.y) / 2.0, max.z)
                    val trianglesBefore = model.getTriangleCount(0)
                    model.embossSvg(0, svgFile, 12f, 0.8f, engraved, tap, Vec3d(0.0, 0.0, 1.0))
                    assertEquals("surface SVG editing must keep the target object", 1, model.objectsCount)
                    if (engraved) {
                        assertTrue("engraving must be represented as a native negative volume",
                            model.getModifierVolumeIndices(0).isNotEmpty())
                    } else {
                        assertTrue("raised SVG must add printable mesh geometry",
                            model.getTriangleCount(0) > trianglesBefore)
                    }

                    SliceBeam.genCurrentConfig(model)
                    model.export3mf(SliceBeam.getCurrentConfigFile().absolutePath, projectFile.absolutePath)
                    restored = Model(projectFile, 1)
                    assertEquals("the target remains one object after 3MF reload", 1, restored.objectsCount)
                    if (engraved) {
                        assertTrue("negative SVG volume must survive 3MF reload",
                            restored.getModifierVolumeIndices(0).isNotEmpty())
                    } else {
                        assertTrue("raised SVG triangles must survive 3MF reload",
                            restored.getTriangleCount(0) > trianglesBefore)
                    }

                    SliceBeam.genCurrentConfig(restored)
                    result = restored.slice(SliceBeam.getCurrentConfigFile().absolutePath, gcodeFile.absolutePath, null)
                    val gcode = gcodeFile.readText()
                    assertHasExtrusion(gcode)
                    assertTrue("the G-code must contain the original model extrusion",
                        Regex("(?m)^G1 .*\\bE[0-9.-]+").containsMatchIn(gcode))
                    val modifiedToolpaths = gcode.lineSequence()
                        .filter { it.startsWith("G1") && it.contains(" E") }
                        .toList()
                    assertTrue(
                        if (engraved) "engraving must alter the sliced extrusion path"
                        else "raised SVG must alter the sliced extrusion path",
                        modifiedToolpaths != baselineToolpaths,
                    )
                } finally {
                    baselineResult?.release()
                    result?.release()
                    restored?.release()
                    model.release()
                }
                projectFile.delete()
                gcodeFile.delete()
            }
        } finally {
            svgFile.delete()
            cubeFile.delete()
            baselineFile.delete()
            projectFile.delete()
            gcodeFile.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test
    fun mirroredThreeMfBuildTransformsSurviveOrcaAndPrusaImporterRoundTrips() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cube = copyCube(context, "mirrored-3mf-source.stl")
        val prusaBase = File(context.cacheDir, "mirrored-prusa-base.3mf")
        val bbsBase = File(context.cacheDir, "mirrored-bbs-base.3mf")
        InstrumentationRegistry.getInstrumentation().context.assets
            .open("models/two-color-project.3mf").use { input ->
                bbsBase.outputStream().use(input::copyTo)
            }
        val prusaInput = File(context.cacheDir, "mirrored-prusa-input.3mf")
        val bbsInput = File(context.cacheDir, "mirrored-bbs-input.3mf")
        val roundTrip = File(context.cacheDir, "mirrored-roundtrip.3mf")
        val mirroredTransform = "4.4408921e-16 1 -5.55111512e-17 " +
            "0.819152044 -4.4408921e-16 -0.573576436 " +
            "0.573576436 -1.11022302e-16 0.819152044 70.41477 30.069061 23.392571"
        try {
            val sourceModel = Model(cube)
            try {
                sourceModel.ensureOnBed(0)
                SliceBeam.genCurrentConfig(sourceModel)
                sourceModel.export3mf(SliceBeam.getCurrentConfigFile().absolutePath, prusaBase.absolutePath)
            } finally {
                sourceModel.release()
            }

            rewriteThreeMfBuildTransform(prusaBase, prusaInput, mirroredTransform)
            rewriteThreeMfBuildTransform(bbsBase, bbsInput, mirroredTransform)

            for (input in listOf(prusaInput, bbsInput)) {
                val restored = Model(input, 1)
                try {
                    SliceBeam.genCurrentConfig(restored)
                    restored.export3mf(SliceBeam.getCurrentConfigFile().absolutePath, roundTrip.absolutePath)
                    val serialized = ZipFile(roundTrip).use { archive ->
                        archive.getInputStream(requireNotNull(archive.getEntry("3D/3dmodel.model")))
                            .bufferedReader().use { it.readText() }
                    }
                    val values = Regex("""<item[^>]*transform="([^"]+)"""")
                        .find(serialized)?.groupValues?.get(1)
                        ?.split(Regex("\\s+"))?.map(String::toDouble)
                    assertNotNull("re-exported 3MF must include its build transform", values)
                    val m = requireNotNull(values)
                    assertEquals("3MF affine transform has 12 coefficients", 12, m.size)
                    val determinant = m[0] * (m[4] * m[8] - m[7] * m[5]) -
                        m[3] * (m[1] * m[8] - m[7] * m[2]) +
                        m[6] * (m[1] * m[5] - m[4] * m[2])
                    assertTrue("the mirrored transform determinant must remain negative", determinant < 0.0)
                } finally {
                    restored.release()
                    roundTrip.delete()
                }
            }
        } finally {
            cube.delete(); prusaBase.delete(); bbsBase.delete(); prusaInput.delete(); bbsInput.delete()
            roundTrip.delete(); SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test
    fun oversizedZip64ThreeMfXmlIsRejectedWithoutAllocatingItsDeclaredSize() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixture = File(context.cacheDir, "oversized-xml-entry.3mf")
        try {
            val xml = "<?xml version=\"1.0\"?><model xmlns=\"http://schemas.microsoft.com/3dmanufacturing/core/2015/02\"><resources/><build/></model>"
                .toByteArray(Charsets.UTF_8)
            val declaredSize = Int.MAX_VALUE.toLong() + 1L
            writeZip64EntryWithDeclaredSize(
                fixture,
                "3D/3dmodel.model",
                xml,
                declaredSize,
            )
            ZipFile(fixture).use { archive ->
                val entry = requireNotNull(archive.getEntry("3D/3dmodel.model"))
                assertEquals("ZIP64 entry declares the intended oversized XML length", declaredSize, entry.size)
                assertEquals("the hostile fixture itself remains tiny", xml.size.toLong(), entry.compressedSize)
            }

            val failure = runCatching { Model(fixture, 0) }.exceptionOrNull()
            assertTrue(
                "An XML entry whose ZIP64 uncompressed size exceeds Expat's int limit must fail as a normal import error",
                failure is Slic3rRuntimeError,
            )
        } finally {
            fixture.delete()
        }
    }

    @Test
    fun qidiQ2SliceResolvesSelectedBedTemperatureAndInitialToolInGeneratedGcode() = withQidiQ2Profile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = copyCube(context, "qidi-q2-start-gcode-source.stl")
        val model = Model(source)
        try {
            model.ensureOnBed(0)
            val config = requireNotNull(SliceBeam.CONFIG)
            val filament = requireNotNull(config.presets?.get("filament")?.let(config::findFilament))
            filament.put("first_layer_bed_temperature", "67")
            filament.put("nozzle_temperature_initial_layer", "223")
            val gcode = sliceToGcode(context, model, "qidi-q2-start-gcode.gcode")
            assertTrue("Qidi start sequence should pass the selected filament bed temperature to PRINT_START",
                gcode.contains("PRINT_START BED=67"))
            assertTrue("Qidi start sequence should heat the selected bed temperature",
                Regex("(?m)^M140 S67(?:\\s|$)").containsMatchIn(gcode))
            assertTrue("Qidi start sequence should resolve the initial logical tool",
                Regex("(?m)^T\\d+(?:\\s|$)").containsMatchIn(gcode))
            assertFalse("Qidi start G-code must not retain an unresolved bed-temperature placeholder",
                gcode.contains("M140 S[bed_temperature_initial_layer_single]"))
            assertFalse("Executable Qidi start G-code must not retain an unresolved initial-tool placeholder",
                gcode.lineSequence().filterNot { it.trimStart().startsWith(";") }
                    .any { it.contains("T[initial_tool]") })
            assertTrue("G-code header should report the selected first-layer bed temperature",
                Regex("(?m)^; first_layer_bed_temperature = 67\\s*$").containsMatchIn(gcode))
            assertTrue("G-code header should report the selected filament's first-layer nozzle temperature",
                Regex("(?m)^; first_layer_temperature = 223\\s*$").containsMatchIn(gcode))
            assertEquals("first-layer nozzle temperature should be emitted only once",
                1, gcode.lineSequence().count { it.startsWith("; first_layer_temperature =") })
        } finally {
            model.release()
            source.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test
    fun compatibilityHeaderUsesTheInitialToolTemperatureForMultitoolPrints() = withToolChangerProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = copyCube(context, "initial-tool-temperature-source.stl")
        val model = Model(source)
        val gcodeFile = File(context.cacheDir, "initial-tool-temperature.gcode")
        var result: GCodeProcessorResult? = null
        try {
            model.setUniformFilament(0, 2)
            SliceBeam.genCurrentConfig(model)
            val generatedConfig = SliceBeam.getCurrentConfigFile()
            val configText = generatedConfig.readText()
            val temperatureLine = Regex("(?m)^nozzle_temperature_initial_layer\\s*=\\s*([^\\r\\n]+)")
                .find(configText)
            assertNotNull("tool-changer slice config should contain initial nozzle temperatures", temperatureLine)
            val temperatures = requireNotNull(temperatureLine).groupValues[1].split(',').map(String::trim)
            assertTrue("test needs distinct entries for both physical tools: $temperatures", temperatures.size >= 2)
            val updatedTemperatures = temperatures.toMutableList().apply { this[1] = "237" }.joinToString(",")
            val updatedConfig = configText.replaceRange(
                temperatureLine.range,
                "nozzle_temperature_initial_layer = $updatedTemperatures",
            )
            generatedConfig.writeText(updatedConfig)

            result = model.slice(generatedConfig.absolutePath, gcodeFile.absolutePath, null)
            val gcode = gcodeFile.readText()
            assertHasExtrusion(gcode)
            assertTrue("the model's initial filament must be T1", Regex("(?m)^T1(?:\\s|$)").containsMatchIn(gcode))
            assertTrue("the compatibility header must read T1's value, not the T0 value",
                Regex("(?m)^; first_layer_temperature = 237\\s*$").containsMatchIn(gcode))
            assertEquals("the compatibility value must not be duplicated later in the file",
                1, gcode.lineSequence().count { it.startsWith("; first_layer_temperature =") })
        } finally {
            result?.release()
            model.release()
            source.delete()
            gcodeFile.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test
    fun qidiQ1StartGcodeUsesTemperatureOfTheSelectedPlateType() = withQidiProfile("Qidi Q1 Pro 0.4 nozzle") {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = copyCube(context, "qidi-q1-selected-bed-type-source.stl")
        val model = Model(source)
        try {
            model.ensureOnBed(0)
            val config = requireNotNull(SliceBeam.CONFIG)
            val printer = requireNotNull(config.presets?.get("printer")?.let(config::findPrinter))
            val filament = requireNotNull(config.presets?.get("filament")?.let(config::findFilament))
            printer.put("curr_bed_type", "Textured PEI Plate")
            filament.put("textured_plate_temp_initial_layer", "71")
            filament.put("cool_plate_temp_initial_layer", "31")

            val gcode = sliceToGcode(context, model, "qidi-q1-selected-bed-type.gcode")
            assertTrue("Q1 PRINT_START should receive the selected textured-plate temperature", gcode.contains("PRINT_START BED=71"))
            assertTrue("Q1 should heat the selected textured plate", Regex("(?m)^M140 S71(?:\\s|$)").containsMatchIn(gcode))
            assertFalse("Q1 must not use the unselected cool-plate temperature", Regex("(?m)^M140 S31(?:\\s|$)").containsMatchIn(gcode))
            assertFalse("Q1 start G-code must not retain an unresolved selected-bed placeholder", gcode.contains("[bed_temperature_initial_layer_single]"))
        } finally {
            model.release()
            source.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test(timeout = 120_000)
    fun qidiXSmart5Orca25ProfileFamilyMergesAndSlicesOnAndroid() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val application = context.applicationContext as Application
        SliceBeam.INSTANCE = application as SliceBeam
        Prefs.init(application)
        val previousConfig = SliceBeam.CONFIG
        val source = copyCube(context, "qidi-xsmart5-orca25-profile-source.stl")
        val gcodeFile = File(context.cacheDir, "qidi-xsmart5-orca25-profile.gcode")
        var model: Model? = null
        var result: GCodeProcessorResult? = null
        try {
            context.assets.open("orca_profiles/Qidi.ini").use {
                SliceBeam.CONFIG = Slic3rConfigWrapper(it)
            }
            val config = requireNotNull(SliceBeam.CONFIG)
            val bundle = QidiXSmart5ProfileBundle.load(context.assets)

            assertEquals("all four X-Smart 5 nozzle profiles are bundled", 4,
                bundle.printerConfigs.count { it.title?.startsWith("Qidi X-Smart 5 ") == true })
            assertEquals("all Orca X-Smart 5 print presets are bundled", 19, bundle.printConfigs.size)
            assertEquals("all Orca X-Smart 5 filament presets and parents are bundled", 248,
                bundle.filamentConfigs.size)
            assertTrue("the machine model is part of the bundle", QidiXSmart5ProfileBundle.mergeInto(config, bundle))
            assertTrue("repeat installation is idempotent", !QidiXSmart5ProfileBundle.mergeInto(config, bundle))

            for (nozzle in listOf("0.2", "0.4", "0.6", "0.8")) {
                val variant = requireNotNull(config.findPrinter("Qidi X-Smart 5 $nozzle nozzle"))
                assertEquals("$nozzle-mm variant retains its nozzle", nozzle, variant["nozzle_diameter"])
                assertNotNull("$nozzle-mm variant resolves its default process",
                    config.findPrint(variant["default_print_profile"]))
                assertNotNull("$nozzle-mm variant resolves its default filament",
                    config.findFilament(variant["default_filament_profile"]))
            }
            val printer = requireNotNull(config.findPrinter("Qidi X-Smart 5 0.4 nozzle"))
            val process = requireNotNull(config.findPrint(printer["default_print_profile"]))
            val filament = requireNotNull(config.findFilament(printer["default_filament_profile"]))
            assertEquals("Orca's X-Smart 5 0.4-mm nozzle is retained", "0.4", printer["nozzle_diameter"])
            assertEquals("Orca's 180-mm square build plate is retained",
                "0x0,180x0,180x180,0x180", printer["printable_area"]?.replace(" ", ""))
            assertEquals("Orca's 180-mm build height is retained", "180", printer["printable_height"])
            assertTrue("the default process is compatible with the selected printer",
                process["compatible_printers"].orEmpty().split(',').contains(printer.title))
            config.presets = ConfigObject("presets").apply {
                put("printer", requireNotNull(printer.title))
                put("print", requireNotNull(process.title))
                put("filament", requireNotNull(filament.title))
            }

            model = Model(source).also { it.ensureOnBed(0) }
            SliceBeam.genCurrentConfig(model)
            val generatedConfig = SliceBeam.getCurrentConfigFile()
            val generatedText = generatedConfig.readText()
            assertTrue("generated native config selects the X-Smart 5 bed", generatedText.contains("printable_height = 180"))
            assertTrue("generated native config selects a 0.4-mm nozzle", generatedText.contains("nozzle_diameter = 0.4"))
            assertTrue("generated native config retains the Qidi start macro", generatedText.contains("BOX_PRINT_START"))
            result = model.slice(generatedConfig.absolutePath, gcodeFile.absolutePath, null)
            val gcode = gcodeFile.readText()
            assertHasExtrusion(gcode)
        } finally {
            result?.release()
            model?.release()
            source.delete()
            gcodeFile.delete()
            SliceBeam.getCurrentConfigFile().delete()
            SliceBeam.CONFIG = previousConfig
        }
    }

    @Test
    fun disjointBooleanUnionKeepsItsInstanceBoundsAndProducesGcode() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val application = context.applicationContext as Application
        SliceBeam.INSTANCE = application as SliceBeam
        Prefs.init(application)

        val previousConfig = SliceBeam.CONFIG
        context.assets.open("orca_profiles/Anycubic.ini").use { SliceBeam.CONFIG = Slic3rConfigWrapper(it) }
        val config = requireNotNull(SliceBeam.CONFIG)
        val printer = requireNotNull(config.findPrinter("Anycubic Kobra 3 0.4 nozzle"))
        val process = requireNotNull(config.findPrint("0.16mm Standard"))
        val filament = requireNotNull(config.findFilament("Anycubic Generic PLA"))
        config.presets = ConfigObject("presets").apply {
            put("printer", printer.title)
            put("print", process.title)
            put("filament", filament.title)
        }

        val modelFile = File(context.cacheDir, "boolean-disjoint-slice.stl")
        val gcodeFile = File(context.cacheDir, "boolean-disjoint-slice.gcode")
        var model: Model? = null
        var sliceResult: GCodeProcessorResult? = null
        var generatedConfig: File? = null
        try {
            context.assets.open("models/xyz_cube.stl").use { input -> modelFile.outputStream().use(input::copyTo) }
            model = Model(modelFile)
            val initialMin = Vec3d(); val initialMax = Vec3d()
            model.getBoundingBoxExact(0, initialMin, initialMax)
            model.addObject(model, 0)
            model.translate(1, (initialMax.x - initialMin.x) * 1.5, 0.0, 0.0)

            val result = model.booleanObjects(0, 1, BooleanOperation.UNION)
            assertEquals("Boolean union should leave one valid model object", 0, result)
            val resultMin = Vec3d(); val resultMax = Vec3d()
            model.getBoundingBoxExact(result, resultMin, resultMax)
            assertEquals((initialMax.x - initialMin.x) * 2.5, resultMax.x - resultMin.x, 0.02)

            SliceBeam.genCurrentConfig(model)
            generatedConfig = SliceBeam.getCurrentConfigFile()
            sliceResult = model.slice(generatedConfig.absolutePath, gcodeFile.absolutePath, null)
            val gcode = gcodeFile.readText()
            assertTrue("Native slicer should emit G-code for the disjoint Boolean result", gcode.isNotBlank())
            assertTrue("G-code should include at least one deposited move", Regex("(?m)^G1 .*\\bE[0-9.-]+").containsMatchIn(gcode))
            Log.i("NativeSliceDeviceTest", "PASS disjoint Boolean G-code bytes=${gcodeFile.length()}")
        } finally {
            sliceResult?.release()
            model?.release()
            modelFile.delete()
            gcodeFile.delete()
            generatedConfig?.delete()
            SliceBeam.CONFIG = previousConfig
        }
    }

    @Test
    fun booleanUnionHandlesLinkedTargetAndCutterCopiesAndStillSlices() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val targetFile = copyCube(context, "boolean-linked-target.stl")
        val cutterFile = copyCube(context, "boolean-linked-cutter.stl")
        val outputFile = File(context.cacheDir, "boolean-linked-copies.gcode")
        val model = Model(targetFile)
        val cutter = Model(cutterFile)
        var sliceResult: GCodeProcessorResult? = null
        try {
            val min = Vec3d(); val max = Vec3d()
            model.getBoundingBoxExact(0, min, max)
            val width = max.x - min.x
            val oneCubeVolume = model.getObjectVolume(0)
            assertTrue("the target should have a second linked placement", model.duplicateInstance(
                0, 0, Vec3d(width * 2.0, 0.0, 0.0),
            ) >= 0)
            cutter.translate(0, width * 0.5, 0.0, 0.0)
            assertTrue("the cutter should have a second linked placement", cutter.duplicateInstance(
                0, 0, Vec3d(width * 2.0, 0.0, 0.0),
            ) >= 0)
            model.addObject(cutter, 0)

            val targetMin = Vec3d(); val targetMax = Vec3d()
            val cutterMin = Vec3d(); val cutterMax = Vec3d()
            model.getBoundingBoxExact(0, targetMin, targetMax)
            model.getBoundingBoxExact(1, cutterMin, cutterMax)
            val expectedMinX = minOf(targetMin.x, cutterMin.x)
            val expectedMaxX = maxOf(targetMax.x, cutterMax.x)

            val result = model.booleanObjects(0, 1, BooleanOperation.UNION)
            assertEquals("all selected linked copies should be combined", 0, result)
            assertEquals("Boolean output should be one printable object", 1, model.objectsCount)
            assertEquals("two half-overlapping pairs should preserve the combined volume",
                oneCubeVolume * 3.0, model.getObjectVolume(result), oneCubeVolume * 0.03)
            val resultMin = Vec3d(); val resultMax = Vec3d()
            model.getBoundingBoxExact(result, resultMin, resultMax)
            assertEquals("Boolean result should retain all linked-copy bounds", expectedMinX,
                resultMin.x, 0.03)
            assertEquals("Boolean result should retain all linked-copy bounds", expectedMaxX,
                resultMax.x, 0.03)

            SliceBeam.genCurrentConfig(model)
            val configFile = SliceBeam.getCurrentConfigFile()
            sliceResult = model.slice(configFile.absolutePath, outputFile.absolutePath, null)
            val gcode = outputFile.readText()
            assertTrue("the linked-copy Boolean result should slice", gcode.isNotBlank())
            assertHasExtrusion(gcode)
        } finally {
            sliceResult?.release()
            cutter.release()
            model.release()
            targetFile.delete()
            cutterFile.delete()
            outputFile.delete()
        }
    }

    @Test
    fun booleanDifferenceAndIntersectionApplyAcrossLinkedCopiesAndStillSlice() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val targetCube = copyCube(context, "boolean-linked-pair-target.stl")
        val cutterCube = copyCube(context, "boolean-linked-pair-cutter.stl")
        try {
            for (operation in listOf(BooleanOperation.DIFFERENCE, BooleanOperation.INTERSECTION)) {
                val suffix = if (operation == BooleanOperation.DIFFERENCE) "difference" else "intersection"
                val projectFile = File(context.cacheDir, "boolean-linked-$suffix.3mf")
                val gcodeFile = File(context.cacheDir, "boolean-linked-$suffix.gcode")
                val model = Model(targetCube)
                val cutter = Model(cutterCube)
                var restored: Model? = null
                var sliceResult: GCodeProcessorResult? = null
                try {
                    val min = Vec3d(); val max = Vec3d()
                    model.getBoundingBoxExact(0, min, max)
                    val width = max.x - min.x
                    val oneCubeVolume = model.getObjectVolume(0)
                    assertTrue("target should contain two linked copies", model.duplicateInstance(
                        0, 0, Vec3d(width * 2.0, 0.0, 0.0),
                    ) >= 0)
                    cutter.translate(0, width * 0.5, 0.0, 0.0)
                    assertTrue("cutter should contain two corresponding linked copies", cutter.duplicateInstance(
                        0, 0, Vec3d(width * 2.0, 0.0, 0.0),
                    ) >= 0)
                    model.setUniformFilament(0, 1)
                    cutter.setUniformFilament(0, 2)
                    model.addObject(cutter, 0)

                    val result = model.booleanObjects(0, 1, operation)
                    assertEquals("$suffix should apply to both linked pairs", 0, result)
                    assertEquals("$suffix should replace the two inputs with one result", 1, model.objectsCount)
                    assertEquals("$suffix should keep one half of each target copy",
                        oneCubeVolume, model.getObjectVolume(result), oneCubeVolume * 0.03)
                    val resultMin = Vec3d(); val resultMax = Vec3d()
                    model.getBoundingBoxExact(result, resultMin, resultMax)
                    val resultSpan = resultMax.x - resultMin.x
                    assertTrue("$suffix should preserve both distant result components; x span=$resultSpan",
                        resultSpan >= width * 2.0 && resultSpan <= width * 3.5)
                    assertEquals("Boolean output should keep the target material", listOf(1),
                        model.getUsedFilaments(result).toList())

                    SliceBeam.genCurrentConfig(model)
                    val exportConfig = SliceBeam.getCurrentConfigFile()
                    try {
                        model.export3mf(exportConfig.absolutePath, projectFile.absolutePath)
                    } finally {
                        exportConfig.delete()
                    }
                    restored = Model(projectFile, 1)
                    assertEquals("$suffix 3MF reload should contain one result", 1, restored.objectsCount)
                    assertEquals("$suffix 3MF reload should retain the target material", listOf(1),
                        restored.getUsedFilaments(0).toList())
                    SliceBeam.genCurrentConfig(restored)
                    val sliceConfig = SliceBeam.getCurrentConfigFile()
                    try {
                        sliceResult = restored.slice(sliceConfig.absolutePath, gcodeFile.absolutePath, null)
                    } finally {
                        sliceConfig.delete()
                    }
                    val gcode = gcodeFile.readText()
                    assertHasExtrusion(gcode)
                    assertFalse("$suffix result should not use cutter T1", Regex("(?m)^T1(?:\\s|$)")
                        .containsMatchIn(gcode))
                } finally {
                    sliceResult?.release()
                    restored?.release()
                    cutter.release()
                    model.release()
                    projectFile.delete()
                    gcodeFile.delete()
                }
            }
        } finally {
            targetCube.delete()
            cutterCube.delete()
        }
    }

    @Test
    fun raisedEngravedAndMultilineStandaloneTextSurviveThreeMfAndSliceWithTheirFilaments() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cubeFile = copyCube(context, "text-edit-workflow-cube.stl")
        val fontFile = copyAsset(context, "font/roboto_medium.ttf", "text-edit-workflow-font.ttf")
        val projectFile = File(context.cacheDir, "text-edit-workflow.3mf")
        val gcodeFile = File(context.cacheDir, "text-edit-workflow.gcode")
        var model: Model? = null
        var restored: Model? = null
        var result: GCodeProcessorResult? = null
        var generatedConfig: File? = null
        try {
            model = Model(cubeFile)
            val originalTriangles = model.getTriangleCount(0)
            val originalVolume = model.getObjectVolume(0)
            val min = Vec3d(); val max = Vec3d()
            model.getBoundingBoxExact(0, min, max)
            val centerX = (min.x + max.x) / 2.0
            val centerY = (min.y + max.y) / 2.0
            val originalTopZ = max.z

            // Exercise the two in-place text modes on the selected cube's top face.
            model.embossText(
                0, fontFile.absolutePath, "A", 4f, 0.8f, 0,
                Vec3d(centerX - 4.0, centerY, originalTopZ),
                Vec3d(0.0, 0.0, 1.0),
            )
            val raisedTriangles = model.getTriangleCount(0)
            val raisedVolume = model.getObjectVolume(0)
            assertTrue("raised text should add native geometry", raisedTriangles > originalTriangles)
            assertTrue("raised text should add printable solid volume", raisedVolume > originalVolume)

            model.embossText(
                0, fontFile.absolutePath, "B", 4f, 0.8f, 1,
                Vec3d(centerX + 4.0, centerY, originalTopZ),
                Vec3d(0.0, 0.0, 1.0),
            )
            assertEquals("in-place text edits must not create extra model objects", 1, model.objectsCount)

            // A separate label is a second printable object and keeps an independent tool assignment.
            val standaloneIndex = model.createTextObject(
                fontFile.absolutePath, "C\n3D", 6f, 1f,
                Vec3d(100.0, 100.0, 0.0), Vec3d(0.0, 0.0, 1.0),
            )
            assertEquals(1, standaloneIndex)
            model.setUniformFilament(standaloneIndex, 2)
            assertTrue("standalone text should contain printable triangles", model.getTriangleCount(standaloneIndex) > 0)
            val textMin = Vec3d(); val textMax = Vec3d()
            model.getBoundingBoxExact(standaloneIndex, textMin, textMax)
            assertTrue("standalone text should keep its second line as real mesh geometry",
                textMax.y - textMin.y > 8.0)

            SliceBeam.genCurrentConfig(model)
            generatedConfig = SliceBeam.getCurrentConfigFile()
            model.export3mf(generatedConfig.absolutePath, projectFile.absolutePath)
            generatedConfig.delete()
            generatedConfig = null
            val modelXml = ZipFile(projectFile).use { archive ->
                val configEntry = archive.getEntry("Metadata/model_settings.config")
                    ?: archive.getEntry("Metadata/Slic3r_PE_model.config")
                    ?: error("3MF model config is missing; entries: ${archive.entries().asSequence().map { it.name }.toList()}")
                archive.getInputStream(configEntry)
                    .bufferedReader().use { it.readText() }
            }
            assertTrue("engraving must be serialized as a negative 3MF volume",
                modelXml.contains("negative_part"))

            restored = Model(projectFile, 1)
            assertEquals("raised/engraved base plus standalone text should remain two objects", 2, restored.objectsCount)
            assertTrue("3MF reload should preserve the modified base geometry",
                restored.getTriangleCount(0) > originalTriangles)
            assertTrue("3MF reload should preserve the standalone text mesh", restored.getTriangleCount(1) > 0)
            assertEquals("standalone text should retain its assigned tool", listOf(2),
                restored.getUsedFilaments(1).toList())

            SliceBeam.genCurrentConfig(restored)
            generatedConfig = SliceBeam.getCurrentConfigFile()
            result = restored.slice(generatedConfig.absolutePath, gcodeFile.absolutePath, null)
            val gcode = gcodeFile.readText()
            assertHasExtrusion(gcode)
            assertTrue("base model with raised and engraved text should use target T0",
                Regex("(?m)^T0(?:\\s|$)").containsMatchIn(gcode))
            assertTrue("separate text should use its assigned T1",
                Regex("(?m)^T1(?:\\s|$)").containsMatchIn(gcode))
            assertFalse("no unrelated filament should be emitted",
                Regex("(?m)^T2(?:\\s|$)").containsMatchIn(gcode))
        } finally {
            result?.release()
            restored?.release()
            model?.release()
            cubeFile.delete()
            fontFile.delete()
            projectFile.delete()
            gcodeFile.delete()
            generatedConfig?.delete()
        }
    }

    @Test
    fun standalonePrintableTextProducesExtrusionGcode() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val application = context.applicationContext as Application
        SliceBeam.INSTANCE = application as SliceBeam
        Prefs.init(application)

        val previousConfig = SliceBeam.CONFIG
        context.assets.open("orca_profiles/Anycubic.ini").use { SliceBeam.CONFIG = Slic3rConfigWrapper(it) }
        val config = requireNotNull(SliceBeam.CONFIG)
        val printer = requireNotNull(config.findPrinter("Anycubic Kobra 3 0.4 nozzle"))
        val process = requireNotNull(config.findPrint("0.16mm Standard"))
        val filament = requireNotNull(config.findFilament("Anycubic Generic PLA"))
        config.presets = ConfigObject("presets").apply {
            put("printer", printer.title)
            put("print", process.title)
            put("filament", filament.title)
        }

        val fontFile = File(context.cacheDir, "standalone-text-slice-font.ttf")
        val projectFile = File(context.cacheDir, "standalone-text-roundtrip.3mf")
        val gcodeFile = File(context.cacheDir, "standalone-text-slice.gcode")
        var model: Model? = null
        var restored: Model? = null
        var sliceResult: GCodeProcessorResult? = null
        var generatedConfig: File? = null
        try {
            context.assets.open("font/roboto_medium.ttf").use { input -> fontFile.outputStream().use(input::copyTo) }
            model = Model()
            val textIndex = model.createTextObject(
                fontFile.absolutePath,
                "ORKAN",
                18f,
                1.2f,
                Vec3d(90.0, 90.0, 0.0),
                Vec3d(0.0, 0.0, 1.0),
            )
            assertEquals(0, textIndex)
            assertTrue("standalone text should contain printable triangles", model.getTriangleCount(textIndex) > 0)
            val secondTextIndex = model.createTextObject(
                fontFile.absolutePath,
                "3D",
                18f,
                1.2f,
                Vec3d(90.0, 125.0, 0.0),
                Vec3d(0.0, 0.0, 1.0),
            )
            assertEquals("a second label should remain a separate object", 1, secondTextIndex)
            assertTrue("the second standalone text should contain printable triangles",
                model.getTriangleCount(secondTextIndex) > 0)
            model.setUniformFilament(textIndex, 1)
            model.setUniformFilament(secondTextIndex, 2)

            SliceBeam.genCurrentConfig(model)
            generatedConfig = SliceBeam.getCurrentConfigFile()
            model.export3mf(generatedConfig.absolutePath, projectFile.absolutePath)
            generatedConfig.delete()
            generatedConfig = null
            restored = Model(projectFile, 1)
            assertEquals("both standalone labels should remain separate after 3MF reload", 2, restored.objectsCount)
            assertEquals("first text label should survive project reload", "ORKAN", restored.getObjectName(0))
            assertEquals("second text label should survive project reload", "3D", restored.getObjectName(1))
            assertEquals("the first text should keep its own filament", listOf(1),
                restored.getUsedFilaments(0).toList())
            assertEquals("the second text should keep its own filament", listOf(2),
                restored.getUsedFilaments(1).toList())
            assertTrue("reloaded first text should remain printable", restored.getTriangleCount(0) > 0)
            assertTrue("reloaded second text should remain printable", restored.getTriangleCount(1) > 0)

            SliceBeam.genCurrentConfig(restored)
            generatedConfig = SliceBeam.getCurrentConfigFile()
            sliceResult = restored.slice(generatedConfig.absolutePath, gcodeFile.absolutePath, null)
            val gcode = gcodeFile.readText()
            assertTrue("native slicer should emit G-code for standalone text", gcode.isNotBlank())
            assertTrue("text G-code should contain deposited moves", Regex("(?m)^G1 .*\\bE[0-9.-]+").containsMatchIn(gcode))
            assertTrue("first text should use T0", Regex("(?m)^T0(?:\\s|$)").containsMatchIn(gcode))
            assertTrue("second text should use T1", Regex("(?m)^T1(?:\\s|$)").containsMatchIn(gcode))
            Log.i("NativeSliceDeviceTest", "PASS standalone text G-code bytes=${gcodeFile.length()}")
        } finally {
            sliceResult?.release()
            restored?.release()
            model?.release()
            fontFile.delete()
            projectFile.delete()
            gcodeFile.delete()
            generatedConfig?.delete()
            SliceBeam.CONFIG = previousConfig
        }
    }

    @Test
    fun booleanTextPlacementAndMultipartJoinKeepBothFilamentsThroughThreeMfAndSlice() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cubeFile = copyCube(context, "boolean-text-placement-cube.stl")
        val fontFile = copyAsset(context, "font/roboto_medium.ttf", "boolean-text-placement-font.ttf")
        val projectFile = File(context.cacheDir, "boolean-text-placement-project.3mf")
        val gcodeFile = File(context.cacheDir, "boolean-text-placement.gcode")
        var model: Model? = null
        var restored: Model? = null
        var result: GCodeProcessorResult? = null
        var generatedConfig: File? = null
        try {
            model = Model(cubeFile)
            model.addObject(model, 0)
            model.setUniformFilament(0, 1)
            model.setUniformFilament(1, 2)
            val baseMin = Vec3d(); val baseMax = Vec3d()
            model.getBoundingBoxExact(0, baseMin, baseMax)
            model.translate(1, (baseMax.x - baseMin.x) / 2.0, 0.0, 0.0)

            val booleanIndex = model.booleanObjects(0, 1, BooleanOperation.UNION)
            assertEquals("Boolean union should replace the overlapping solids", 0, booleanIndex)
            assertEquals("Boolean result should retain target material", listOf(1), model.getUsedFilaments(0).toList())
            assertTrue("Boolean union should create a printable solid", model.getObjectVolume(0) > 0.0)

            val textIndex = model.createTextObject(
                fontFile.absolutePath, "A", 8f, 1.2f,
                Vec3d(80.0, 80.0, 0.0), Vec3d(0.0, 0.0, 1.0),
            )
            assertEquals("standalone text should be a distinct object", 1, textIndex)
            model.setUniformFilament(textIndex, 2)
            val textMin = Vec3d(); val textMax = Vec3d()
            model.getBoundingBoxExact(textIndex, textMin, textMax)
            val targetMin = Vec3d(); val targetMax = Vec3d()
            model.getBoundingBoxExact(0, targetMin, targetMax)
            val textBounds = ObjectBounds3D(textMin.x, textMin.y, textMin.z, textMax.x, textMax.y, textMax.z)
            val targetBounds = ObjectBounds3D(targetMin.x, targetMin.y, targetMin.z, targetMax.x, targetMax.y, targetMax.z)
            val delta = requireNotNull(ObjectPlacement.onTop(textBounds, targetBounds))
            model.translate(textIndex, delta.x, delta.y, delta.z)

            assertEquals("text should join the printable target as a separate material part", 0,
                model.combineObjectsAsParts(intArrayOf(0, textIndex)))
            assertEquals(1, model.objectsCount)
            assertTrue("joined Boolean body and text retain both assigned materials",
                model.getUsedFilaments(0).toSet().containsAll(setOf(1, 2)))

            SliceBeam.genCurrentConfig(model)
            generatedConfig = SliceBeam.getCurrentConfigFile()
            model.export3mf(generatedConfig.absolutePath, projectFile.absolutePath)
            generatedConfig.delete()
            generatedConfig = null
            restored = Model(projectFile, 1)
            assertEquals("round-trip keeps the joined assembly as one printable object", 1, restored.objectsCount)
            assertTrue("round-trip retains body and text material assignments",
                restored.getUsedFilaments(0).toSet().containsAll(setOf(1, 2)))
            assertTrue("round-trip retains printable geometry", restored.getObjectVolume(0) > 0.0)

            SliceBeam.genCurrentConfig(restored)
            generatedConfig = SliceBeam.getCurrentConfigFile()
            result = restored.slice(generatedConfig.absolutePath, gcodeFile.absolutePath, null)
            val gcode = gcodeFile.readText()
            assertHasExtrusion(gcode)
            assertTrue("the Boolean body material should use extruder T0", Regex("(?m)^T0(?:\\s|$)").containsMatchIn(gcode))
            assertTrue("the placed text material should use extruder T1", Regex("(?m)^T1(?:\\s|$)").containsMatchIn(gcode))
        } finally {
            result?.release()
            restored?.release()
            model?.release()
            cubeFile.delete()
            fontFile.delete()
            projectFile.delete()
            gcodeFile.delete()
            generatedConfig?.delete()
        }
    }

    @Test
    fun curvedSurfaceTextSurvivesThreeMfReloadAndProducesExtrusionGcode() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cylinderFile = createCylinderFixture("curved-text-slice-cylinder.stl", 25.0, 40.0, 96)
        val fontFile = copyAsset(context, "font/roboto_medium.ttf", "curved-text-slice-font.ttf")
        val projectFile = File(context.cacheDir, "curved-text-surface-project.3mf")
        val gcodeFile = File(context.cacheDir, "curved-text-surface.gcode")
        var model: Model? = null
        var restored: Model? = null
        var result: GCodeProcessorResult? = null
        var generatedConfig: File? = null
        try {
            model = Model(cylinderFile)
            val min = Vec3d(); val max = Vec3d()
            model.getBoundingBoxExact(0, min, max)
            val cylinderTriangles = model.getTriangleCount(0)
            val textIndex = model.createTextObject(
                fontFile.absolutePath,
                "ORCA",
                10f,
                1.2f,
                Vec3d(max.x, (min.y + max.y) / 2.0, (min.z + max.z) / 2.0),
                Vec3d(1.0, 0.0, 0.0),
                targetObjectIndex = 0,
            )
            assertEquals("surface-conforming text should join the target printable object", 0, textIndex)
            assertEquals("the text operation must not add a floating object", 1, model.objectsCount)
            assertTrue(model.getTriangleCount(0) - cylinderTriangles > cylinderTriangles)

            SliceBeam.genCurrentConfig(model)
            generatedConfig = SliceBeam.getCurrentConfigFile()
            model.export3mf(generatedConfig.absolutePath, projectFile.absolutePath)
            generatedConfig.delete()
            generatedConfig = null
            restored = Model(projectFile, 1)
            assertEquals("surface text stays a part of its printable target", 1, restored.objectsCount)
            assertTrue("the conforming text volume must survive 3MF reload",
                restored.getTriangleCount(0) - cylinderTriangles > cylinderTriangles)

            SliceBeam.genCurrentConfig(restored)
            generatedConfig = SliceBeam.getCurrentConfigFile()
            result = restored.slice(generatedConfig.absolutePath, gcodeFile.absolutePath, null)
            assertHasExtrusion(gcodeFile.readText())
            Log.i("NativeSliceDeviceTest", "PASS curved surface text G-code bytes=${gcodeFile.length()}")
        } finally {
            result?.release()
            restored?.release()
            model?.release()
            cylinderFile.delete()
            fontFile.delete()
            projectFile.delete()
            gcodeFile.delete()
            generatedConfig?.delete()
        }
    }

    @Test
    fun curvedTextInheritsTheTappedMultipartVolumeFilament() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cylinderFile = createCylinderFixture("curved-text-filament-cylinder.stl", 25.0, 30.0, 96)
        val fontFile = copyAsset(context, "font/roboto_medium.ttf", "curved-text-filament-font.ttf")
        val projectFile = File(context.cacheDir, "curved-text-filament-project.3mf")
        var model: Model? = null
        var restored: Model? = null
        var generatedConfig: File? = null
        try {
            model = Model(cylinderFile)
            model.addObject(model, 0)
            model.translate(1, 60.0, 0.0, 0.0)
            model.setUniformFilament(0, 1)
            model.setUniformFilament(1, 2)
            assertEquals(0, model.combineObjectsAsParts(intArrayOf(0, 1)))
            assertEquals(listOf(1, 2), model.getUsedFilaments(0).toList())

            val textIndex = model.createTextObject(
                fontFile.absolutePath,
                "ORCA",
                8f,
                1.0f,
                Vec3d(85.0, 0.0, 15.0),
                Vec3d(1.0, 0.0, 0.0),
                targetObjectIndex = 0,
            )
            assertEquals("curved text remains part of the tapped multipart object", 0, textIndex)

            SliceBeam.genCurrentConfig(model)
            generatedConfig = SliceBeam.getCurrentConfigFile()
            model.export3mf(generatedConfig.absolutePath, projectFile.absolutePath)
            generatedConfig.delete()
            generatedConfig = null

            val volumeFilamentTwoEntries = ZipFile(projectFile).use { archive ->
                val entry = archive.getEntry("Metadata/Slic3r_PE_model.config")
                val metadata = requireNotNull(entry).let {
                    archive.getInputStream(it).bufferedReader().use { reader -> reader.readText() }
                }
                Regex("type=\\\"volume\\\" key=\\\"extruder\\\" value=\\\"2\\\"")
                    .findAll(metadata).count()
            }
            assertEquals("the source part and its surface text must both use filament 2", 2, volumeFilamentTwoEntries)

            restored = Model(projectFile, 1)
            assertEquals(listOf(1, 2), restored.getUsedFilaments(0).toList())
            val gcodeFile = File(context.cacheDir, "curved-text-filament-project.gcode")
            try {
                val gcode = sliceToGcode(context, restored, gcodeFile.name)
                assertTrue("the tapped part's filament must reach emitted tool changes",
                    Regex("(?m)^T1(?:\\s|$)").containsMatchIn(gcode))
                assertHasExtrusion(gcode)
            } finally {
                gcodeFile.delete()
            }
        } finally {
            model?.release()
            restored?.release()
            cylinderFile.delete()
            fontFile.delete()
            projectFile.delete()
            generatedConfig?.delete()
        }
    }

    @Test
    fun bundledKobraProfileSlicesBoxLocally() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val application = context.applicationContext as Application
        SliceBeam.INSTANCE = application as SliceBeam
        Prefs.init(application)

        context.assets.open("orca_profiles/Anycubic.ini").use { input ->
            SliceBeam.CONFIG = Slic3rConfigWrapper(input)
        }
        val config = requireNotNull(SliceBeam.CONFIG)
        val printer: ConfigObject? = config.findPrinter("Anycubic Kobra 3 0.4 nozzle")
        val process: ConfigObject? = config.findPrint("0.16mm Standard")
        val filament: ConfigObject? = config.findFilament("Anycubic Generic PLA")
        assertNotNull("Bundled Kobra 3 printer preset", printer)
        assertNotNull("Bundled print preset", process)
        assertNotNull("Bundled PLA filament preset", filament)

        val selectedPresets = ConfigObject("presets")
        config.presets = selectedPresets
        selectedPresets.put("printer", requireNotNull(printer).title)
        selectedPresets.put("print", requireNotNull(process).title)
        selectedPresets.put("filament", requireNotNull(filament).title)

        val modelFile = File(context.cacheDir, "native-slice-device-test.stl")
        val gcodeFile = File(context.cacheDir, "native-slice-device-test.gcode")
        var model: Model? = null
        var result: GCodeProcessorResult? = null
        try {
            context.assets.open("models/box.stl").use { input ->
                FileOutputStream(modelFile).use { output -> input.copyTo(output) }
            }
            model = Model(modelFile)
            assertTrue("The bundled STL must load as one object", model.objectsCount == 1)
            SliceBeam.genCurrentConfig(model)
            result = model.slice(
                SliceBeam.getCurrentConfigFile().absolutePath,
                gcodeFile.absolutePath,
                null,
            )
            assertTrue(
                "Native Orca/libslic3r must produce non-empty G-code",
                gcodeFile.isFile && gcodeFile.length() > 0,
            )
            Log.i("NativeSliceDeviceTest", "PASS local G-code bytes=${gcodeFile.length()}")
        } finally {
            result?.release()
            model?.release()
            modelFile.delete()
            SliceBeam.getCurrentConfigFile().delete()
            gcodeFile.delete()
        }
    }

    @Test
    fun bridgeSettingsReachNativeConfigAndGenerateBridgeGcode() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val application = context.applicationContext as Application
        SliceBeam.INSTANCE = application as SliceBeam
        Prefs.init(application)

        context.assets.open("orca_profiles/Anycubic.ini").use { input ->
            SliceBeam.CONFIG = Slic3rConfigWrapper(input)
        }
        val config = requireNotNull(SliceBeam.CONFIG)
        val printer = requireNotNull(config.findPrinter("Anycubic Kobra 3 0.4 nozzle"))
        val process = requireNotNull(config.findPrint("0.16mm Standard"))
        val filament = requireNotNull(config.findFilament("Anycubic Generic PLA"))
        process.put("bridge_line_width", "0.35")
        process.put("bridge_flow", "1.05")
        process.put("bridge_angle", "37")
        process.put("internal_bridge_angle", "61")
        process.put("relative_bridge_angle", "1")
        filament.put("enable_pressure_advance", "1")
        filament.put("pressure_advance", "0.042")

        config.presets = ConfigObject("presets").apply {
            put("printer", printer.title)
            put("print", process.title)
            put("filament", filament.title)
        }

        val modelFile = File(context.cacheDir, "bridge-settings-device-test.stl")
        val gcodeFile = File(context.cacheDir, "bridge-settings-device-test.gcode")
        var model: Model? = null
        var result: GCodeProcessorResult? = null
        try {
            context.assets.open("models/OrcaCube_v2.stl").use { input ->
                FileOutputStream(modelFile).use { output -> input.copyTo(output) }
            }
            model = Model(modelFile)
            assertTrue("The Orca Cube fixture must load", model.objectsCount == 1)
            SliceBeam.genCurrentConfig(model)
            val nativeConfig = SliceBeam.getCurrentConfigFile().readText()
            assertTrue(nativeConfig.contains("bridge_line_width = 0.35"))
            assertTrue(nativeConfig.contains("bridge_flow = 1.05"))
            assertTrue(nativeConfig.contains("bridge_angle = 37"))
            assertTrue(nativeConfig.contains("internal_bridge_angle = 61"))
            assertTrue(nativeConfig.contains("relative_bridge_angle = 1"))
            assertTrue(nativeConfig.contains("enable_pressure_advance = 1"))
            assertTrue(nativeConfig.contains("pressure_advance = 0.042"))

            result = model.slice(
                SliceBeam.getCurrentConfigFile().absolutePath,
                gcodeFile.absolutePath,
                null,
            )
            assertTrue("Native Orca/libslic3r must produce bridge G-code",
                gcodeFile.isFile && gcodeFile.length() > 0)
            val gcode = gcodeFile.readText()
            assertTrue("The Orca Cube slice must contain a bridge role", gcode.contains("TYPE:Bridge"))
            assertTrue("A staged filament pressure-advance value must reach Klipper G-code",
                gcode.contains("SET_PRESSURE_ADVANCE ADVANCE=0.042"))
            Log.i("NativeSliceDeviceTest", "PASS bridge G-code bytes=${gcodeFile.length()}")
        } finally {
            result?.release()
            model?.release()
            modelFile.delete()
            SliceBeam.getCurrentConfigFile().delete()
            gcodeFile.delete()
        }
    }

    @Test
    fun kobraLegacyStartPressureAdvanceDoesNotOverrideFilamentSetting() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = requireNotNull(SliceBeam.CONFIG)
        val printer = requireNotNull(config.findPrinter("Anycubic Kobra 3 0.4 nozzle"))
        val filament = requireNotNull(config.findFilament("Anycubic Generic PLA"))
        val originalStart = printer.get("machine_start_gcode")
        val originalPrinterModel = printer.get("printer_model")
        val originalEnabled = filament.get("enable_pressure_advance")
        val originalAdvance = filament.get("pressure_advance")
        val modelFile = copyAsset(context, "models/box.stl", "kobra-pa-migration-device-test.stl")
        var model: Model? = null
        try {
            // Recreate the stale stock profile that shipped the fixed value shown in the report.
            val staleStart = "G9111 bedTemp=[first_layer_bed_temperature] extruderTemp=[first_layer_temperature[initial_tool]]\nM117\nM900 K0.051"
            printer.put("machine_start_gcode", staleStart)
            printer.put("printer_model", "Anycubic Kobra 3 V2")
            filament.put("enable_pressure_advance", "1")
            filament.put("pressure_advance", "0.075")

            val effectiveConfig = SliceBeam.buildCurrentConfigObject()
            assertFalse(
                "The generated Kobra 3 V2 slice config must strip the stale fixed PA command",
                effectiveConfig["machine_start_gcode"].orEmpty().contains("M900 K0.051"),
            )

            model = Model(modelFile)
            val enabledGcode = sliceToGcode(context, model, "kobra-pa-enabled-device-test.gcode")
            assertTrue("The selected filament value must reach the sliced G-code", enabledGcode.contains("SET_PRESSURE_ADVANCE ADVANCE=0.075"))
            assertFalse("The stale printer value must not override the selected filament", enabledGcode.contains("M900 K0.051"))

            filament.put("enable_pressure_advance", "0")
            val disabledGcode = sliceToGcode(context, model, "kobra-pa-disabled-device-test.gcode")
            assertFalse(
                "Disabling filament Pressure Advance must remove all executable PA commands",
                Regex("(?im)^\\s*(?:M900\\s+K|SET_PRESSURE_ADVANCE\\b|M572\\b).*").containsMatchIn(disabledGcode),
            )
        } finally {
            model?.release()
            modelFile.delete()
            if (originalStart == null) printer.values.remove("machine_start_gcode") else printer.put("machine_start_gcode", originalStart)
            if (originalPrinterModel == null) printer.values.remove("printer_model") else printer.put("printer_model", originalPrinterModel)
            if (originalEnabled == null) filament.values.remove("enable_pressure_advance") else filament.put("enable_pressure_advance", originalEnabled)
            if (originalAdvance == null) filament.values.remove("pressure_advance") else filament.put("pressure_advance", originalAdvance)
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test(timeout = 120_000)
    fun spiralVaseSlicesSingleFilamentObject() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = requireNotNull(SliceBeam.CONFIG)
        val process = requireNotNull(config.findPrint("0.16mm Standard"))
        process.put("spiral_mode", "1")
        process.put("top_shell_layers", "0")
        process.put("bottom_shell_layers", "1")

        val source = copyCube(context, "spiral-vase-source.stl")
        val configFile = SliceBeam.getCurrentConfigFile()
        var model: Model? = null
        try {
            model = Model(source).also { it.ensureOnBed(0) }
            SliceBeam.genCurrentConfig(model)
            val nativeConfig = configFile.readText()
            assertTrue("The actual native slice config must enable spiral vase mode", nativeConfig.contains("spiral_mode = 1"))
            val gcode = sliceToGcode(context, model, "spiral-vase-single-filament.gcode")
            assertHasExtrusion(gcode)
            assertTrue("The sliced output must keep the vase-mode object perimeter", gcode.contains("TYPE:Outer wall") || gcode.contains("TYPE:External perimeter"))
            Log.i("NativeSliceDeviceTest", "PASS single-filament spiral vase slice")
        } finally {
            model?.release()
            source.delete()
            configFile.delete()
        }
    }

    @Test(timeout = 120_000)
    fun spiralVaseByObjectAllowsSeparateSingleFilamentObjects() = withToolChangerProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = requireNotNull(SliceBeam.CONFIG)
        val process = requireNotNull(config.findPrint("0.20mm Standard"))
        process.put("spiral_mode", "1")
        process.put("print_sequence", "by object")
        process.put("top_shell_layers", "0")
        process.put("bottom_shell_layers", "1")

        val source = copyCube(context, "spiral-vase-by-object-source.stl")
        val output = File(context.cacheDir, "spiral-vase-by-object.gcode")
        val configFile = SliceBeam.getCurrentConfigFile()
        var model: Model? = null
        var result: GCodeProcessorResult? = null
        try {
            model = Model(source).also {
                it.ensureOnBed(0)
                it.addObject(it, 0)
                it.translate(1, 100.0, 0.0, 0.0)
                it.ensureOnBed(1)
                it.setUniformFilament(0, 1)
                it.setUniformFilament(1, 2)
            }
            assertEquals("the fixture must contain two independent objects", 2, model.objectsCount)
            assertEquals("each object must have exactly one filament", listOf(1), model.getUsedFilaments(0).toList())
            assertEquals("each object must have exactly one filament", listOf(2), model.getUsedFilaments(1).toList())

            SliceBeam.genCurrentConfig(model)
            val generatedConfig = configFile.readText()
            assertTrue("vase mode must stay enabled", generatedConfig.contains("spiral_mode = 1"))
            assertTrue("multiple vase objects must use sequential printing", generatedConfig.contains("print_sequence = by object"))
            result = model.slice(configFile.absolutePath, output.absolutePath, null)
            val gcode = output.readText()
            assertHasExtrusion(gcode)
            assertTrue("the first object's tool must be present in G-code", Regex("(?m)^T0(?:\\s|$)").containsMatchIn(gcode))
            assertTrue("the second object's tool must be present in G-code", Regex("(?m)^T1(?:\\s|$)").containsMatchIn(gcode))
            Log.i("NativeSliceDeviceTest", "PASS by-object spiral vase with one filament per object")
        } finally {
            result?.release()
            model?.release()
            source.delete()
            output.delete()
            configFile.delete()
        }
    }

    @Test(timeout = 120_000)
    fun zShrinkageCompensationKeepsHeightRangeAlignedAfterPrintConfigChanges() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = copyCube(context, "z-shrinkage-height-range-source.stl")
        val configFile = SliceBeam.getCurrentConfigFile()
        var model: Model? = null
        try {
            model = Model(source).also { it.ensureOnBed(0) }
            SliceBeam.genCurrentConfig(model)

            // The JNI regression applies a 10–15 mm thin, dense range and processes the same
            // model/Print at 100% and then 95% Z scale, exercising region-cache invalidation.
            val values = Native.z_shrinkage_height_range_regression(
                model.pointer,
                configFile.absolutePath,
                100.0,
                95.0,
            )
            assertEquals("Every thin layer must use the dense range and vice versa at 100%", 0.0, values[0], 0.0)
            assertEquals("The dense range must start at 10 mm", 10.0, values[1], 0.2)
            assertEquals("The 20 mm model must end at 20 mm", 20.0, values[2], 0.2)
            assertEquals("Every thin layer must follow the new compensated range after config change", 0.0, values[3], 0.0)
            assertEquals("The range start must scale with 95% Z compensation", 10.0 * 100.0 / 95.0, values[4], 0.2)
            assertEquals("The model height must scale with 95% Z compensation", 20.0 * 100.0 / 95.0, values[5], 0.2)
        } finally {
            model?.release()
            source.delete()
            configFile.delete()
        }
    }

    @Test(timeout = 120_000)
    fun spiralVaseAcceptsMultipleHeightRangeOverridesAndProducesGcode() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val process = requireNotNull(SliceBeam.CONFIG?.findPrint("0.16mm Standard"))
        process.put("spiral_mode", "1")
        process.put("top_shell_layers", "0")
        process.put("bottom_shell_layers", "1")

        val source = copyCube(context, "spiral-height-range-source.stl")
        val configFile = SliceBeam.getCurrentConfigFile()
        var model: Model? = null
        try {
            model = Model(source).also { it.ensureOnBed(0) }
            assertTrue("The native model must accept an object-local height-range override",
                Native.model_add_layer_range_test_config(model.pointer, 0.4, 1.2, 0.2, 42.0))
            assertTrue("The vase regression must also create a second, disjoint height range",
                Native.model_add_layer_range_test_config(model.pointer, 1.2, 2.0, 0.2, 48.0))

            val gcode = sliceToGcode(context, model, "spiral-height-range-override.gcode")
            assertHasExtrusion(gcode)
            assertTrue("Spiral vase output must contain the object's exterior perimeter",
                gcode.contains("TYPE:Outer wall") || gcode.contains("TYPE:External perimeter"))
            val wallTypesByLayer = mutableMapOf<Int, MutableSet<String>>()
            var layerIndex = -1
            gcode.lineSequence().forEach { line ->
                if (line.startsWith(";LAYER_CHANGE")) ++layerIndex
                if (line.startsWith(";TYPE:"))
                    wallTypesByLayer.getOrPut(layerIndex) { mutableSetOf() } += line.removePrefix(";TYPE:").trim()
            }
            assertTrue("The layer-range vase fixture must slice multiple layers", wallTypesByLayer.size > 3)
            assertTrue("Vase-mode layers above the configured bottom shell must not generate inner walls: $wallTypesByLayer",
                wallTypesByLayer.filterKeys { it >= 1 }.values.none { "Inner wall" in it })
            Log.i("NativeSliceDeviceTest", "PASS single-filament spiral vase with multiple height-range overrides")
        } finally {
            model?.release()
            source.delete()
            configFile.delete()
        }
    }

    @Test(timeout = 120_000)
    fun spiralVaseReportsObjectRangeOverridesBeforePrinterSend() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val process = requireNotNull(SliceBeam.CONFIG?.findPrint("0.16mm Standard"))
        process.put("spiral_mode", "1")
        process.put("top_shell_layers", "0")
        process.put("bottom_shell_layers", "1")
        process.put("wall_loops", "1")
        process.put("sparse_infill_density", "0%")

        val source = copyCube(context, "spiral-vase-range-warning.stl")
        val output = File(context.cacheDir, "spiral-vase-range-warning.gcode")
        val configFile = SliceBeam.getCurrentConfigFile()
        var model: Model? = null
        var result: GCodeProcessorResult? = null
        try {
            model = Model(source).also { it.ensureOnBed(0) }
            assertTrue("the fixture must add an infill override that breaks vase mode",
                Native.model_add_spiral_vase_override_test_config(model.pointer))
            SliceBeam.genCurrentConfig(model)
            result = model.slice(configFile.absolutePath, output.absolutePath, null)

            val warning = result.getSpiralVaseOverrideWarning()
            assertNotNull("the slice result must carry Orca's non-fatal warning into printer preparation", warning)
            assertTrue(requireNotNull(warning).contains("Spiral vase mode needs no sparse infill"))
            assertTrue("the warning should identify the object with the override", requireNotNull(warning).contains("spiral-vase-range-warning.stl"))
            assertHasExtrusion(output.readText())
        } finally {
            result?.release()
            model?.release()
            source.delete()
            output.delete()
            configFile.delete()
        }
    }

    @Test
    fun selectedBedSurfaceTemperatureReachesNativeGcode() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val application = context.applicationContext as Application
        SliceBeam.INSTANCE = application as SliceBeam
        Prefs.init(application)

        context.assets.open("orca_profiles/Anycubic.ini").use { input ->
            SliceBeam.CONFIG = Slic3rConfigWrapper(input)
        }
        val config = requireNotNull(SliceBeam.CONFIG)
        val printer = requireNotNull(config.findPrinter("Anycubic Kobra 3 0.4 nozzle"))
        val process = requireNotNull(config.findPrint("0.16mm Standard"))
        val filament = requireNotNull(config.findFilament("Anycubic Generic PLA"))
        printer.put("curr_bed_type", "Textured PEI Plate")
        printer.put("support_multi_bed_types", "1")
        printer.put("machine_start_gcode", "M140 S[bed_temperature_initial_layer_single]\\nM190 S[bed_temperature_initial_layer_single]")
        filament.put("hot_plate_temp_initial_layer", "81")
        filament.put("hot_plate_temp", "80")
        filament.put("textured_plate_temp_initial_layer", "62")
        filament.put("textured_plate_temp", "61")
        filament.put("cool_plate_temp_initial_layer", "43")
        filament.put("cool_plate_temp", "42")
        config.presets = ConfigObject("presets").apply {
            put("printer", printer.title)
            put("print", process.title)
            put("filament", filament.title)
        }

        val modelFile = File(context.cacheDir, "bed-surface-temperature-device-test.stl")
        val gcodeFile = File(context.cacheDir, "bed-surface-temperature-device-test.gcode")
        var model: Model? = null
        var result: GCodeProcessorResult? = null
        try {
            context.assets.open("models/box.stl").use { input -> modelFile.outputStream().use { input.copyTo(it) } }
            model = Model(modelFile)
            SliceBeam.genCurrentConfig(model)
            val nativeConfig = SliceBeam.getCurrentConfigFile().readText()
            assertTrue(nativeConfig.contains("curr_bed_type = Textured PEI Plate"))
            assertTrue(nativeConfig.contains("textured_plate_temp_initial_layer = 62"))
            assertTrue(nativeConfig.contains("hot_plate_temp_initial_layer = 81"))

            result = model.slice(SliceBeam.getCurrentConfigFile().absolutePath, gcodeFile.absolutePath, null)
            val gcode = gcodeFile.readText()
            assertTrue("G-code must heat the selected Textured PEI surface at 62 C", gcode.contains("M140 S62"))
            assertTrue("G-code must not substitute the high-temperature value", !gcode.contains("M140 S81"))
            Log.i("NativeSliceDeviceTest", "PASS selected bed-surface G-code bytes=${gcodeFile.length()}")
        } finally {
            result?.release()
            model?.release()
            modelFile.delete()
            SliceBeam.getCurrentConfigFile().delete()
            gcodeFile.delete()
        }
    }

    private data class GCodePathSegment(
        val x0: Double, val y0: Double, val x1: Double, val y1: Double, val z: Double, val type: String,
        val width: Double = 0.0,
    )

    private fun supportExtrusionSegments(gcode: String): List<GCodePathSegment> {
        val axisRegex = Regex("(?:^|\\s)([XYZE])(-?(?:\\d+(?:\\.\\d*)?|\\.\\d+))(?=\\s|$)")
        val toolRegex = Regex("^T(\\d+)(?:\\s|$)")
        var type = ""
        var x: Double? = null
        var y: Double? = null
        var z = 0.0
        var width = 0.0
        var relativeExtrusion = false
        var activeTool = 0
        val extrusionByTool = HashMap<Int, Double>()
        val supports = mutableListOf<GCodePathSegment>()
        for (line in gcode.lineSequence()) {
            when (line.trim().uppercase()) {
                "M82" -> relativeExtrusion = false
                "M83" -> relativeExtrusion = true
            }
            toolRegex.find(line.trim())?.let { activeTool = it.groupValues[1].toInt() }
            if (line.startsWith(";TYPE:", ignoreCase = true)) {
                type = line.substringAfter(':').trim().lowercase()
                continue
            }
            if (line.startsWith(";WIDTH:", ignoreCase = true)) {
                width = line.substringAfter(':').trim().toDoubleOrNull() ?: width
                continue
            }
            if (!line.startsWith("G0 ") && !line.startsWith("G1 ") && !line.startsWith("G92 ")) continue
            val axes = axisRegex.findAll(line.substringBefore(';')).associate { match ->
                match.groupValues[1] to match.groupValues[2].toDouble()
            }
            if (line.startsWith("G92 ")) {
                axes["E"]?.let { extrusionByTool[activeTool] = it }
                continue
            }
            axes["Z"]?.let { z = it }
            val nextX = axes["X"] ?: x
            val nextY = axes["Y"] ?: y
            val extrusion = axes["E"]
            val depositing = extrusion?.let { value ->
                val previous = extrusionByTool[activeTool] ?: 0.0
                extrusionByTool[activeTool] = if (relativeExtrusion) previous + value else value
                if (relativeExtrusion) value > 0.0 else value > previous
            } == true
            if (depositing && x != null && y != null && nextX != null && nextY != null) {
                val segment = GCodePathSegment(x!!, y!!, nextX, nextY, z, type, width)
                if (type.contains("support")) supports += segment
            }
            x = nextX
            y = nextY
        }
        return supports
    }

    private fun pathSegmentDistance(a: GCodePathSegment, b: GCodePathSegment): Double {
        fun cross(ax: Double, ay: Double, bx: Double, by: Double) = ax * by - ay * bx
        fun pointDistance(px: Double, py: Double, segment: GCodePathSegment): Double {
            val dx = segment.x1 - segment.x0
            val dy = segment.y1 - segment.y0
            val lengthSquared = dx * dx + dy * dy
            if (lengthSquared <= 1e-12) return kotlin.math.hypot(px - segment.x0, py - segment.y0)
            val t = (((px - segment.x0) * dx + (py - segment.y0) * dy) / lengthSquared).coerceIn(0.0, 1.0)
            return kotlin.math.hypot(px - (segment.x0 + t * dx), py - (segment.y0 + t * dy))
        }
        val abx = a.x1 - a.x0
        val aby = a.y1 - a.y0
        val cdx = b.x1 - b.x0
        val cdy = b.y1 - b.y0
        val denominator = cross(abx, aby, cdx, cdy)
        if (kotlin.math.abs(denominator) > 1e-12) {
            val t = cross(b.x0 - a.x0, b.y0 - a.y0, cdx, cdy) / denominator
            val u = cross(b.x0 - a.x0, b.y0 - a.y0, abx, aby) / denominator
            if (t in 0.0..1.0 && u in 0.0..1.0) return 0.0
        }
        return minOf(
            pointDistance(a.x0, a.y0, b), pointDistance(a.x1, a.y1, b),
            pointDistance(b.x0, b.y0, a), pointDistance(b.x1, b.y1, a),
        )
    }

    private fun pathSegmentRectangleDistance(
        path: GCodePathSegment,
        minX: Double,
        minY: Double,
        maxX: Double,
        maxY: Double,
    ): Double {
        // Liang-Barsky segment clipping also catches an entire support segment running inside
        // the model cross-section; measuring only distance to the four edges could miss that.
        val dx = path.x1 - path.x0
        val dy = path.y1 - path.y0
        var enter = 0.0
        var leave = 1.0
        val p = doubleArrayOf(-dx, dx, -dy, dy)
        val q = doubleArrayOf(path.x0 - minX, maxX - path.x0, path.y0 - minY, maxY - path.y0)
        for (index in p.indices) {
            if (kotlin.math.abs(p[index]) <= 1e-12) {
                if (q[index] < 0.0) {
                    return rectangleEdgeDistance(path, minX, minY, maxX, maxY)
                }
                continue
            }
            val ratio = q[index] / p[index]
            if (p[index] < 0.0) {
                if (ratio > leave) return rectangleEdgeDistance(path, minX, minY, maxX, maxY)
                enter = maxOf(enter, ratio)
            } else {
                if (ratio < enter) return rectangleEdgeDistance(path, minX, minY, maxX, maxY)
                leave = minOf(leave, ratio)
            }
        }
        return if (enter <= leave) 0.0 else rectangleEdgeDistance(path, minX, minY, maxX, maxY)
    }

    private fun rectangleEdgeDistance(
        path: GCodePathSegment,
        minX: Double,
        minY: Double,
        maxX: Double,
        maxY: Double,
    ): Double = minOf(
        pathSegmentDistance(path, GCodePathSegment(minX, minY, maxX, minY, path.z, path.type)),
        pathSegmentDistance(path, GCodePathSegment(maxX, minY, maxX, maxY, path.z, path.type)),
        pathSegmentDistance(path, GCodePathSegment(maxX, maxY, minX, maxY, path.z, path.type)),
        pathSegmentDistance(path, GCodePathSegment(minX, maxY, minX, minY, path.z, path.type)),
    )

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

    private inline fun withQidiQ2Profile(block: () -> Unit) = withQidiProfile("Qidi Q2 0.4 nozzle", block)

    private inline fun withQidiProfile(printerName: String, block: () -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val application = context.applicationContext as Application
        SliceBeam.INSTANCE = application as SliceBeam
        Prefs.init(application)
        val previousConfig = SliceBeam.CONFIG
        try {
            context.assets.open("orca_profiles/Qidi.ini").use { SliceBeam.CONFIG = Slic3rConfigWrapper(it) }
            val config = requireNotNull(SliceBeam.CONFIG)
            val printer = requireNotNull(config.findPrinter(printerName))
            val process = requireNotNull(
                config.findPrint(printer.get("default_print_profile"))
                    ?: config.findPrint("0.20mm Standard"),
            )
            val filament = requireNotNull(config.findFilament(printer.get("default_filament_profile")))
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

    private fun copyCube(context: Context, name: String): File = File(context.cacheDir, name).also { target ->
        context.assets.open("models/xyz_cube.stl").use { input -> target.outputStream().use(input::copyTo) }
    }

    private fun copyCubeScaledZ(context: Context, name: String, zScale: Float): File {
        require(zScale.isFinite() && zScale > 0f)
        val bytes = context.assets.open("models/xyz_cube.stl").use { it.readBytes() }
        require(bytes.size >= 84) { "Expected a binary STL test cube" }
        val data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val triangles = data.getInt(80)
        require(triangles >= 1 && bytes.size == 84 + triangles * 50) { "Expected a binary STL test cube" }
        repeat(triangles) { triangle ->
            val triangleOffset = 84 + triangle * 50
            repeat(3) { vertex ->
                val zOffset = triangleOffset + 12 + vertex * 12 + 8
                data.putFloat(zOffset, data.getFloat(zOffset) * zScale)
            }
        }
        return File(context.cacheDir, name).also { it.writeBytes(bytes) }
    }

    private fun copyAsset(context: Context, assetPath: String, name: String): File =
        File(context.cacheDir, name).also { target ->
            context.assets.open(assetPath).use { input -> target.outputStream().use(input::copyTo) }
        }

    private fun rewriteThreeMfBuildTransform(source: File, destination: File, transform: String) {
        val itemPattern = Regex("""<item\b[^>]*>""")
        val transformPattern = Regex("""transform="[^"]*"""")
        var changed = false
        ZipFile(source).use { archive ->
            ZipOutputStream(destination.outputStream()).use { output ->
                archive.entries().asSequence().forEach { entry ->
                    val original = archive.getInputStream(entry).use { it.readBytes() }
                    val bytes = if (entry.name == "3D/3dmodel.model") {
                        val xml = original.toString(Charsets.UTF_8)
                        val item = requireNotNull(itemPattern.find(xml))
                        val transformAttribute = requireNotNull(transformPattern.find(item.value))
                        val replacedItem = item.value.replaceRange(
                            transformAttribute.range,
                            "transform=\"$transform\"",
                        )
                        val updated = xml.replaceRange(item.range, replacedItem)
                        changed = replacedItem != item.value
                        updated.toByteArray(Charsets.UTF_8)
                    } else original
                    output.putNextEntry(ZipEntry(entry.name))
                    output.write(bytes)
                    output.closeEntry()
                }
            }
        }
        check(changed) { "3MF model XML did not contain an item build transform" }
    }

    /** Writes one small ZIP member whose central directory claims a ZIP64-sized XML payload. */
    private fun writeZip64EntryWithDeclaredSize(file: File, entryName: String, content: ByteArray, declaredSize: Long) {
        require(declaredSize > Int.MAX_VALUE)
        val name = entryName.toByteArray(Charsets.UTF_8)
        val crc = CRC32().apply { update(content) }.value
        val zip64ExtraSize = 12 // ZIP64 extra id + data length + uncompressed size
        val localHeaderSize = 30 + name.size + zip64ExtraSize
        val centralOffset = localHeaderSize + content.size
        val centralSize = 46 + name.size + zip64ExtraSize
        val output = FileOutputStream(file)

        fun write16(value: Long) {
            output.write((value and 0xff).toInt())
            output.write(((value ushr 8) and 0xff).toInt())
        }
        fun write32(value: Long) {
            repeat(4) { shift -> output.write(((value ushr (shift * 8)) and 0xff).toInt()) }
        }
        fun write64(value: Long) {
            repeat(8) { shift -> output.write(((value ushr (shift * 8)) and 0xff).toInt()) }
        }

        output.use {
            // Local file header; the payload itself remains tiny and uncompressed.
            write32(0x04034b50L); write16(45); write16(0); write16(0)
            write16(0); write16(0); write32(crc)
            write32(content.size.toLong()); write32(0xffffffffL)
            write16(name.size.toLong()); write16(zip64ExtraSize.toLong()); output.write(name)
            write16(0x0001); write16(8); write64(declaredSize); output.write(content)

            // Central directory: only the uncompressed size is represented in ZIP64 extra data.
            write32(0x02014b50L); write16(45); write16(45); write16(0); write16(0)
            write16(0); write16(0); write32(crc); write32(content.size.toLong())
            write32(0xffffffffL); write16(name.size.toLong()); write16(zip64ExtraSize.toLong())
            write16(0); write16(0); write16(0); write32(0); write32(0)
            output.write(name); write16(0x0001); write16(8); write64(declaredSize)

            // Classic EOCD is sufficient because archive and central-directory offsets remain ZIP32-sized.
            write32(0x06054b50L); write16(0); write16(0); write16(1); write16(1)
            write32(centralSize.toLong()); write32(centralOffset.toLong()); write16(0)
        }
    }

    private fun createThinCubeFixture(name: String, width: Double, depth: Double, height: Double): File {
        val points = arrayOf(
            doubleArrayOf(0.0, 0.0, 0.0), doubleArrayOf(width, 0.0, 0.0),
            doubleArrayOf(width, depth, 0.0), doubleArrayOf(0.0, depth, 0.0),
            doubleArrayOf(0.0, 0.0, height), doubleArrayOf(width, 0.0, height),
            doubleArrayOf(width, depth, height), doubleArrayOf(0.0, depth, height),
        )
        val faces = arrayOf(
            intArrayOf(0, 2, 1), intArrayOf(0, 3, 2), intArrayOf(4, 5, 6), intArrayOf(4, 6, 7),
            intArrayOf(0, 1, 5), intArrayOf(0, 5, 4), intArrayOf(1, 2, 6), intArrayOf(1, 6, 5),
            intArrayOf(2, 3, 7), intArrayOf(2, 7, 6), intArrayOf(3, 0, 4), intArrayOf(3, 4, 7),
        )
        fun vertexLine(index: Int) = "vertex ${points[index][0]} ${points[index][1]} ${points[index][2]}\n"
        val stl = buildString {
            append("solid thin_cube\n")
            faces.forEach { face ->
                append("facet normal 0 0 0\nouter loop\n")
                face.forEach { append(vertexLine(it)) }
                append("endloop\nendfacet\n")
            }
            append("endsolid thin_cube\n")
        }
        return File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, name).also { it.writeText(stl) }
    }

    private fun createLPrismFixture(name: String, size: Double, arm: Double, height: Double): File {
        require(size > arm * 2 && arm > 0.0 && height > 0.0)
        val polygon = arrayOf(
            doubleArrayOf(0.0, 0.0), doubleArrayOf(size, 0.0), doubleArrayOf(size, arm),
            doubleArrayOf(arm, arm), doubleArrayOf(arm, size), doubleArrayOf(0.0, size),
        )
        val capTriangles = arrayOf(intArrayOf(0, 1, 2), intArrayOf(0, 2, 3), intArrayOf(0, 3, 5), intArrayOf(3, 4, 5))
        fun vertex(index: Int, z: Double) = "vertex ${polygon[index][0]} ${polygon[index][1]} $z\n"
        val stl = buildString {
            append("solid concave_l_prism\n")
            capTriangles.forEach { triangle ->
                append("facet normal 0 0 -1\nouter loop\n")
                triangle.reversed().forEach { append(vertex(it, 0.0)) }
                append("endloop\nendfacet\n")
                append("facet normal 0 0 1\nouter loop\n")
                triangle.forEach { append(vertex(it, height)) }
                append("endloop\nendfacet\n")
            }
            polygon.indices.forEach { index ->
                val next = (index + 1) % polygon.size
                append("facet normal 0 0 0\nouter loop\n")
                append(vertex(index, 0.0)); append(vertex(next, 0.0)); append(vertex(next, height))
                append("endloop\nendfacet\n")
                append("facet normal 0 0 0\nouter loop\n")
                append(vertex(index, 0.0)); append(vertex(next, height)); append(vertex(index, height))
                append("endloop\nendfacet\n")
            }
            append("endsolid concave_l_prism\n")
        }
        return File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, name).also { it.writeText(stl) }
    }

    private fun createOverhangWallFixture(name: String): File {
        val faces = arrayOf(
            intArrayOf(0, 2, 1), intArrayOf(0, 3, 2), intArrayOf(4, 5, 6), intArrayOf(4, 6, 7),
            intArrayOf(0, 1, 5), intArrayOf(0, 5, 4), intArrayOf(1, 2, 6), intArrayOf(1, 6, 5),
            intArrayOf(2, 3, 7), intArrayOf(2, 7, 6), intArrayOf(3, 0, 4), intArrayOf(3, 4, 7),
        )
        val boxes = listOf(
            doubleArrayOf(8.0, 8.0, 0.0, 12.0, 12.0, 5.0),
            doubleArrayOf(0.0, 0.0, 5.0, 20.0, 20.0, 7.0),
        )
        val stl = buildString {
            append("solid overhang_walls\n")
            boxes.forEach { box ->
                val x0 = box[0]
                val y0 = box[1]
                val z0 = box[2]
                val x1 = box[3]
                val y1 = box[4]
                val z1 = box[5]
                val points = arrayOf(
                    doubleArrayOf(x0, y0, z0), doubleArrayOf(x1, y0, z0),
                    doubleArrayOf(x1, y1, z0), doubleArrayOf(x0, y1, z0),
                    doubleArrayOf(x0, y0, z1), doubleArrayOf(x1, y0, z1),
                    doubleArrayOf(x1, y1, z1), doubleArrayOf(x0, y1, z1),
                )
                faces.forEach { face ->
                    append("facet normal 0 0 0\nouter loop\n")
                    face.forEach { index ->
                        val point = points[index]
                        append("vertex ${point[0]} ${point[1]} ${point[2]}\n")
                    }
                    append("endloop\nendfacet\n")
                }
            }
            append("endsolid overhang_walls\n")
        }
        return File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, name).also { it.writeText(stl) }
    }

    private fun createCylinderFixture(name: String, radius: Double, height: Double, segments: Int): File {
        val target = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, name)
        fun point(angle: Double, z: Double) = doubleArrayOf(radius * kotlin.math.cos(angle), radius * kotlin.math.sin(angle), z)
        fun vertexLine(point: DoubleArray) = "vertex ${point[0]} ${point[1]} ${point[2]}\n"
        val stl = StringBuilder("solid curved_text_cylinder\n")
        for (index in 0 until segments) {
            val a0 = 2.0 * Math.PI * index / segments
            val a1 = 2.0 * Math.PI * (index + 1) / segments
            val b0 = point(a0, 0.0); val b1 = point(a1, 0.0)
            val t0 = point(a0, height); val t1 = point(a1, height)
            val bottomCenter = doubleArrayOf(0.0, 0.0, 0.0)
            val topCenter = doubleArrayOf(0.0, 0.0, height)
            listOf(
                arrayOf(b0, b1, t1), arrayOf(b0, t1, t0),
                arrayOf(bottomCenter, b1, b0), arrayOf(topCenter, t0, t1),
            ).forEach { triangle ->
                stl.append("facet normal 0 0 0\nouter loop\n")
                triangle.forEach { stl.append(vertexLine(it)) }
                stl.append("endloop\nendfacet\n")
            }
        }
        stl.append("endsolid curved_text_cylinder\n")
        target.writeText(stl.toString())
        return target
    }

    /** A 10 mm square stem supports a 20 mm square cap, creating a clear unsupported ledge. */
    private fun createSteppedOverhangFixture(name: String): File {
        val target = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, name)
        data class P(val x: Double, val y: Double, val z: Double)
        val stl = StringBuilder("solid stepped_lightning_overhang\n")
        fun triangle(a: P, b: P, c: P) {
            stl.append("facet normal 0 0 0\nouter loop\n")
            listOf(a, b, c).forEach { stl.append("vertex ${it.x} ${it.y} ${it.z}\n") }
            stl.append("endloop\nendfacet\n")
        }
        fun quad(a: P, b: P, c: P, d: P) {
            triangle(a, b, c)
            triangle(a, c, d)
        }
        fun square(half: Double, z: Double) = listOf(
            P(-half, -half, z), P(half, -half, z), P(half, half, z), P(-half, half, z),
        )
        val bottom = square(5.0, 0.0)
        val stemTop = square(5.0, 10.0)
        val capBottom = square(10.0, 10.0)
        val capTop = square(10.0, 20.0)
        for (side in 0..3) {
            val next = (side + 1) % 4
            quad(bottom[side], bottom[next], stemTop[next], stemTop[side])
            quad(capBottom[side], capBottom[next], capTop[next], capTop[side])
            val stemA = stemTop[side]
            val stemB = stemTop[next]
            val capA = capBottom[side]
            val capB = capBottom[next]
            quad(stemA, stemB, capB, capA)
        }
        quad(bottom[0], bottom[3], bottom[2], bottom[1])
        quad(capTop[0], capTop[1], capTop[2], capTop[3])
        stl.append("endsolid stepped_lightning_overhang\n")
        target.writeText(stl.toString())
        return target
    }

    /** A lower shelf, narrow connecting column and upper shelf exercise tree contacts on the model. */
    private fun createTwoTierTreeSupportFixture(name: String): File {
        val target = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, name)
        data class P(val x: Double, val y: Double, val z: Double)
        val stl = StringBuilder("solid tree_bottom_contact_tiers\n")
        fun triangle(a: P, b: P, c: P) {
            stl.append("facet normal 0 0 0\nouter loop\n")
            listOf(a, b, c).forEach { stl.append("vertex ${it.x} ${it.y} ${it.z}\n") }
            stl.append("endloop\nendfacet\n")
        }
        fun box(x0: Double, y0: Double, z0: Double, x1: Double, y1: Double, z1: Double) {
            val p = listOf(
                P(x0, y0, z0), P(x1, y0, z0), P(x1, y1, z0), P(x0, y1, z0),
                P(x0, y0, z1), P(x1, y0, z1), P(x1, y1, z1), P(x0, y1, z1),
            )
            val faces = arrayOf(
                intArrayOf(0, 2, 1), intArrayOf(0, 3, 2), intArrayOf(4, 5, 6), intArrayOf(4, 6, 7),
                intArrayOf(0, 1, 5), intArrayOf(0, 5, 4), intArrayOf(1, 2, 6), intArrayOf(1, 6, 5),
                intArrayOf(2, 3, 7), intArrayOf(2, 7, 6), intArrayOf(3, 0, 4), intArrayOf(3, 4, 7),
            )
            faces.forEach { face -> triangle(p[face[0]], p[face[1]], p[face[2]]) }
        }
        box(-15.0, -15.0, 0.0, 15.0, 15.0, 3.0)
        box(-4.0, -4.0, 2.0, 4.0, 4.0, 17.0)
        box(-25.0, -25.0, 16.0, 25.0, 25.0, 19.0)
        stl.append("endsolid tree_bottom_contact_tiers\n")
        target.writeText(stl.toString())
        return target
    }

    /** One slab overhang and two wings join a single tree-support cluster on the same slice. */
    private fun createTreeClusterSameLayerFixture(name: String): File {
        val target = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, name)
        data class P(val x: Double, val y: Double, val z: Double)
        val stl = StringBuilder("solid tree_cluster_same_layer\n")
        fun triangle(a: P, b: P, c: P) {
            stl.append("facet normal 0 0 0\nouter loop\n")
            listOf(a, b, c).forEach { stl.append("vertex ${it.x} ${it.y} ${it.z}\n") }
            stl.append("endloop\nendfacet\n")
        }
        fun box(x0: Double, y0: Double, z0: Double, x1: Double, y1: Double, z1: Double) {
            val p = listOf(
                P(x0, y0, z0), P(x1, y0, z0), P(x1, y1, z0), P(x0, y1, z0),
                P(x0, y0, z1), P(x1, y0, z1), P(x1, y1, z1), P(x0, y1, z1),
            )
            val faces = arrayOf(
                intArrayOf(0, 2, 1), intArrayOf(0, 3, 2), intArrayOf(4, 5, 6), intArrayOf(4, 6, 7),
                intArrayOf(0, 1, 5), intArrayOf(0, 5, 4), intArrayOf(1, 2, 6), intArrayOf(1, 6, 5),
                intArrayOf(2, 3, 7), intArrayOf(2, 7, 6), intArrayOf(3, 0, 4), intArrayOf(3, 4, 7),
            )
            faces.forEach { face -> triangle(p[face[0]], p[face[1]], p[face[2]]) }
        }
        // Match Orca's regression geometry: three same-layer overhangs connect through one cluster.
        box(-5.0, -5.0, 0.0, 5.0, 5.0, 5.1)       // column
        box(-7.0, -5.0, 5.0, 7.0, 5.0, 10.0)      // slab
        box(8.0, -9.05, 5.2, 12.0, -2.95, 10.0)  // front wing
        box(8.0, 6.85, 5.2, 12.0, 12.95, 10.0)   // back wing
        stl.append("endsolid tree_cluster_same_layer\n")
        target.writeText(stl.toString())
        return target
    }

    /** Wide supported cap over a centered stem; mirrors Orca's synchronized-gap support regression. */
    private fun createSupportCapitalFixture(name: String): File {
        val target = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, name)
        data class P(val x: Double, val y: Double, val z: Double)
        val stl = StringBuilder("solid support_capital\n")
        fun triangle(a: P, b: P, c: P) {
            stl.append("facet normal 0 0 0\nouter loop\n")
            listOf(a, b, c).forEach { stl.append("vertex ${it.x} ${it.y} ${it.z}\n") }
            stl.append("endloop\nendfacet\n")
        }
        fun box(x0: Double, y0: Double, z0: Double, x1: Double, y1: Double, z1: Double) {
            val p = listOf(
                P(x0, y0, z0), P(x1, y0, z0), P(x1, y1, z0), P(x0, y1, z0),
                P(x0, y0, z1), P(x1, y0, z1), P(x1, y1, z1), P(x0, y1, z1),
            )
            val faces = arrayOf(
                intArrayOf(0, 2, 1), intArrayOf(0, 3, 2), intArrayOf(4, 5, 6), intArrayOf(4, 6, 7),
                intArrayOf(0, 1, 5), intArrayOf(0, 5, 4), intArrayOf(1, 2, 6), intArrayOf(1, 6, 5),
                intArrayOf(2, 3, 7), intArrayOf(2, 7, 6), intArrayOf(3, 0, 4), intArrayOf(3, 4, 7),
            )
            faces.forEach { face -> triangle(p[face[0]], p[face[1]], p[face[2]]) }
        }
        box(0.0, 0.0, 0.0, 40.0, 40.0, 2.0)   // base
        box(16.0, 16.0, 1.0, 24.0, 24.0, 13.0) // stem
        box(0.0, 0.0, 12.0, 40.0, 40.0, 14.0) // cap
        stl.append("endsolid support_capital\n")
        target.writeText(stl.toString())
        return target
    }

    /** Tall stem plus a broad cap: the upper ledge exercises the former blanket >100 mm wall rule. */
    private fun createTallStemCapFixture(name: String, stemHeight: Double): File {
        val target = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, name)
        data class P(val x: Double, val y: Double, val z: Double)
        val stl = StringBuilder("solid tree_tall_stem_cap\n")
        fun triangle(a: P, b: P, c: P) {
            stl.append("facet normal 0 0 0\nouter loop\n")
            listOf(a, b, c).forEach { stl.append("vertex ${it.x} ${it.y} ${it.z}\n") }
            stl.append("endloop\nendfacet\n")
        }
        fun box(x0: Double, y0: Double, z0: Double, x1: Double, y1: Double, z1: Double) {
            val p = listOf(
                P(x0, y0, z0), P(x1, y0, z0), P(x1, y1, z0), P(x0, y1, z0),
                P(x0, y0, z1), P(x1, y0, z1), P(x1, y1, z1), P(x0, y1, z1),
            )
            val faces = arrayOf(
                intArrayOf(0, 2, 1), intArrayOf(0, 3, 2), intArrayOf(4, 5, 6), intArrayOf(4, 6, 7),
                intArrayOf(0, 1, 5), intArrayOf(0, 5, 4), intArrayOf(1, 2, 6), intArrayOf(1, 6, 5),
                intArrayOf(2, 3, 7), intArrayOf(2, 7, 6), intArrayOf(3, 0, 4), intArrayOf(3, 4, 7),
            )
            faces.forEach { face -> triangle(p[face[0]], p[face[1]], p[face[2]]) }
        }
        box(-4.0, -4.0, 0.0, 4.0, 4.0, stemHeight)
        box(-16.0, -16.0, stemHeight, 16.0, 16.0, stemHeight + 3.0)
        stl.append("endsolid tree_tall_stem_cap\n")
        target.writeText(stl.toString())
        return target
    }

    /** Orca's small stepped body creates internal bridge areas above sparse infill. */
    private fun createInternalBridgeStepFixture(name: String): File {
        val target = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, name)
        val faces = arrayOf(
            intArrayOf(0, 2, 1), intArrayOf(0, 3, 2), intArrayOf(4, 5, 6), intArrayOf(4, 6, 7),
            intArrayOf(0, 1, 5), intArrayOf(0, 5, 4), intArrayOf(1, 2, 6), intArrayOf(1, 6, 5),
            intArrayOf(2, 3, 7), intArrayOf(2, 7, 6), intArrayOf(3, 0, 4), intArrayOf(3, 4, 7),
        )
        val boxes = listOf(
            doubleArrayOf(0.0, 0.0, 0.0, 30.0, 24.0, 3.0),
            doubleArrayOf(8.0, 7.0, 3.0, 22.0, 17.0, 4.0),
        )
        val stl = buildString {
            append("solid internal_bridge_step\n")
            boxes.forEach { box ->
                val points = arrayOf(
                    doubleArrayOf(box[0], box[1], box[2]), doubleArrayOf(box[3], box[1], box[2]),
                    doubleArrayOf(box[3], box[4], box[2]), doubleArrayOf(box[0], box[4], box[2]),
                    doubleArrayOf(box[0], box[1], box[5]), doubleArrayOf(box[3], box[1], box[5]),
                    doubleArrayOf(box[3], box[4], box[5]), doubleArrayOf(box[0], box[4], box[5]),
                )
                faces.forEach { face ->
                    append("facet normal 0 0 0\nouter loop\n")
                    face.forEach { index ->
                        val point = points[index]
                        append("vertex ${point[0]} ${point[1]} ${point[2]}\n")
                    }
                    append("endloop\nendfacet\n")
                }
            }
            append("endsolid internal_bridge_step\n")
        }
        target.writeText(stl)
        return target
    }

    @Test
    fun byObjectClearanceUsesOnlyGeometryAboveTheRod() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = requireNotNull(SliceBeam.CONFIG)
        val printer = requireNotNull(config.findPrinter("Anycubic Kobra 3 0.4 nozzle"))
        val process = requireNotNull(config.findPrint("0.16mm Standard"))
        val printerKeys = listOf(
            "extruder_clearance_dist_to_rod",
            "extruder_clearance_height_to_lid",
            "extruder_clearance_height_to_rod",
            "extruder_clearance_radius",
        )
        val processKeys = listOf("print_sequence", "gcode_label_objects", "skirt_loops", "layer_height", "initial_layer_print_height")
        val previousPrinter = printerKeys.associateWith { printer[it] }
        val previousProcess = processKeys.associateWith { process[it] }
        val files = mutableListOf<File>()
        val output = File(context.cacheDir, "by-object-rod-reach.gcode")
        var model: Model? = null
        var addedModel: Model? = null
        var result: GCodeProcessorResult? = null

        fun slabWithFin(name: String, finAtBack: Boolean): File {
            val target = File(context.cacheDir, name).also(files::add)
            val faces = arrayOf(
                intArrayOf(0, 2, 1), intArrayOf(0, 3, 2), intArrayOf(4, 5, 6), intArrayOf(4, 6, 7),
                intArrayOf(0, 1, 5), intArrayOf(0, 5, 4), intArrayOf(1, 2, 6), intArrayOf(1, 6, 5),
                intArrayOf(2, 3, 7), intArrayOf(2, 7, 6), intArrayOf(3, 0, 4), intArrayOf(3, 4, 7),
            )
            val finY = if (finAtBack) 28.0 else 0.0
            val boxes = listOf(
                doubleArrayOf(0.0, 0.0, 0.0, 40.0, 30.0, 2.0),
                doubleArrayOf(16.0, finY, 0.0, 24.0, finY + 2.0, 29.5),
            )
            target.writeText(buildString {
                append("solid ${name.substringBeforeLast('.')}\n")
                boxes.forEach { box ->
                    val points = arrayOf(
                        doubleArrayOf(box[0], box[1], box[2]), doubleArrayOf(box[3], box[1], box[2]),
                        doubleArrayOf(box[3], box[4], box[2]), doubleArrayOf(box[0], box[4], box[2]),
                        doubleArrayOf(box[0], box[1], box[5]), doubleArrayOf(box[3], box[1], box[5]),
                        doubleArrayOf(box[3], box[4], box[5]), doubleArrayOf(box[0], box[4], box[5]),
                    )
                    faces.forEach { face ->
                        append("facet normal 0 0 0\nouter loop\n")
                        face.forEach { index ->
                            val point = points[index]
                            append("vertex ${point[0]} ${point[1]} ${point[2]}\n")
                        }
                        append("endloop\nendfacet\n")
                    }
                }
                append("endsolid ${name.substringBeforeLast('.')}\n")
            })
            return target
        }

        try {
            printer.put("extruder_clearance_dist_to_rod", "20")
            printer.put("extruder_clearance_height_to_lid", "120")
            printer.put("extruder_clearance_height_to_rod", "25")
            printer.put("extruder_clearance_radius", "40")
            process.put("print_sequence", "by object")
            process.put("gcode_label_objects", "1")
            process.put("skirt_loops", "0")
            process.put("layer_height", "0.2")
            process.put("initial_layer_print_height", "0.2")

            val activeModel = Model(slabWithFin("rod-fin-away-first.stl", finAtBack = true))
            model = activeModel
            activeModel.ensureOnBed(0)
            val sourceModel = Model(slabWithFin("rod-fin-away-second.stl", finAtBack = false))
            addedModel = sourceModel
            activeModel.addObject(sourceModel, 0)
            activeModel.ensureOnBed(1)
            for ((index, target) in listOf(40.0 to 85.0, 170.0 to 45.0).withIndex()) {
                val min = Vec3d()
                val max = Vec3d()
                activeModel.getBoundingBoxExact(index, min, max)
                activeModel.translate(index, target.first - min.x, target.second - min.y, -min.z)
            }
            assertEquals(2, activeModel.objectsCount)
            SliceBeam.genCurrentConfig(activeModel)
            val generatedConfig = SliceBeam.getCurrentConfigFile()
            assertTrue("the printer profile's rod reach must reach native slicing",
                Regex("(?m)^extruder_clearance_dist_to_rod\\s*=\\s*20(?:\\.0*)?(?:$|\\s)").containsMatchIn(generatedConfig.readText()))
            result = activeModel.slice(generatedConfig.absolutePath, output.absolutePath, null)
            assertTrue("the separated raised fins must produce real G-code", output.readText().contains("G1 "))
        } finally {
            result?.release()
            model?.release()
            addedModel?.release()
            output.delete()
            files.forEach(File::delete)
            SliceBeam.getCurrentConfigFile().delete()
            printerKeys.forEach { key ->
                printer.values.remove(key)
                previousPrinter[key]?.let { printer.put(key, it) }
            }
            processKeys.forEach { key ->
                process.values.remove(key)
                previousProcess[key]?.let { process.put(key, it) }
            }
        }
    }

    @Test
    fun byObjectSlicePrintsTallObjectAfterShortOverlappingObject() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = requireNotNull(SliceBeam.CONFIG)
        val printer = requireNotNull(config.findPrinter("Anycubic Kobra 3 0.4 nozzle"))
        val process = requireNotNull(config.findPrint("0.16mm Standard"))
        printer.put("extruder_clearance_height_to_rod", "30")
        printer.put("extruder_clearance_height_to_lid", "200")
        printer.put("extruder_clearance_radius", "5")
        process.put("print_sequence", "by object")

        val tallFile = copyCube(context, "by-object-tall-first.stl")
        val shortFile = copyCube(context, "by-object-short-second.stl")
        val output = File(context.cacheDir, "by-object-clearance-order.gcode")
        var tallFirstModel: Model? = null
        var shortSecondModel: Model? = null
        var result: GCodeProcessorResult? = null
        try {
            tallFirstModel = Model(tallFile)
            shortSecondModel = Model(shortFile)
            tallFirstModel.scale(0, 1.0, 1.0, 4.0)
            tallFirstModel.ensureOnBed(0)
            tallFirstModel.addObject(shortSecondModel, 0)
            tallFirstModel.translate(1, 50.0, 0.0, 0.0)
            tallFirstModel.ensureOnBed(1)
            assertEquals(2, tallFirstModel.objectsCount)

            SliceBeam.genCurrentConfig(tallFirstModel)
            val generatedConfig = SliceBeam.getCurrentConfigFile().readText()
            assertTrue(generatedConfig.contains("print_sequence = by object"))
            assertTrue(generatedConfig.contains("extruder_clearance_height_to_rod = 30"))
            result = tallFirstModel.slice(SliceBeam.getCurrentConfigFile().absolutePath, output.absolutePath, null)

            val gcode = output.readText()
            assertHasExtrusion(gcode)
            val shortObjectStart = gcode.indexOf("; printing object by-object-short-second.stl")
            val tallObjectStart = gcode.indexOf("; printing object by-object-tall-first.stl")
            assertTrue("The short object must remain before the tall object in the generated by-object G-code", shortObjectStart >= 0 && tallObjectStart > shortObjectStart)
            Log.i("NativeSliceDeviceTest", "PASS by-object clearance order short=$shortObjectStart tall=$tallObjectStart")
        } finally {
            result?.release()
            tallFirstModel?.release()
            shortSecondModel?.release()
            tallFile.delete()
            shortFile.delete()
            output.delete()
            SliceBeam.getCurrentConfigFile().delete()
        }
    }

    @Test(timeout = 120_000)
    fun sequentialPrintKeepsCopiesOfOneObjectInTheirListOrder() = withKobraProfile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = requireNotNull(SliceBeam.CONFIG)
        val printer = requireNotNull(config.findPrinter("Anycubic Kobra 3 0.4 nozzle"))
        val process = requireNotNull(config.findPrint("0.16mm Standard"))
        val printerKeys = listOf(
            "extruder_clearance_height_to_lid",
            "extruder_clearance_height_to_rod",
            "extruder_clearance_radius",
        )
        val processKeys = listOf("print_sequence", "gcode_label_objects", "layer_height", "initial_layer_print_height")
        val previousPrinter = printerKeys.associateWith { printer[it] }
        val previousProcess = processKeys.associateWith { process[it] }
        val model = Model()
        val output = File(context.cacheDir, "sequential-copy-list-order.gcode")
        var result: GCodeProcessorResult? = null
        try {
            printer.put("extruder_clearance_height_to_lid", "100")
            printer.put("extruder_clearance_height_to_rod", "10")
            printer.put("extruder_clearance_radius", "5")
            process.put("print_sequence", "by object")
            process.put("gcode_label_objects", "1")
            process.put("layer_height", "0.2")
            process.put("initial_layer_print_height", "0.2")

            assertEquals(0, model.createPrimitive(0, 5.0, 5.0, 0.2, "Sequential copies", 10.0, 10.0))
            model.ensureOnBed(0)
            val copyCount = 200
            for (copy in 1 until copyCount) {
                val dx = (copy % 15) * 12.0
                val dy = (copy / 15) * 12.0
                assertEquals("copy $copy must be added as a separate instance", copy,
                    model.duplicateInstance(0, 0, Vec3d(dx, dy, 0.0)))
            }

            SliceBeam.genCurrentConfig(model)
            val generatedConfig = SliceBeam.getCurrentConfigFile()
            result = model.slice(generatedConfig.absolutePath, output.absolutePath, null)
            val lines = output.readLines()
            val objectStarts = lines.indices.filter { lines[it].startsWith("; printing object Sequential copies id:") }
            assertEquals("one-layer by-object G-code must identify every copy exactly once", copyCount, objectStarts.size)

            val firstExtrusionByCopy = objectStarts.mapIndexed { index, start ->
                val end = objectStarts.getOrNull(index + 1) ?: lines.size
                val extrusion = lines.subList(start + 1, end).firstOrNull { line ->
                    val command = line.substringBefore(';').trim()
                    command.startsWith("G1 ") &&
                        Regex("(?:^|\\s)E[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)").containsMatchIn(command) &&
                        Regex("(?:^|\\s)[XY][+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)").containsMatchIn(command)
                }
                assertNotNull("copy at sequential print position $index must extrude", extrusion)
                val values = Regex("([XY])([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+))")
                    .findAll(requireNotNull(extrusion).substringBefore(';'))
                    .associate { it.groupValues[1] to it.groupValues[2].toDouble() }
                assertTrue("copy $index extrusion must include XY coordinates: $extrusion", "X" in values && "Y" in values)
                values.getValue("X") to values.getValue("Y")
            }
            assertEquals(copyCount, firstExtrusionByCopy.size)
            val first = firstExtrusionByCopy.first()
            for (copy in 0 until copyCount) {
                val expectedDx = (copy % 15) * 12.0
                val expectedDy = (copy / 15) * 12.0
                val actual = firstExtrusionByCopy[copy]
                assertEquals("copy list order must be preserved in G-code at index $copy (X)",
                    expectedDx, actual.first - first.first, 0.5)
                assertEquals("copy list order must be preserved in G-code at index $copy (Y)",
                    expectedDy, actual.second - first.second, 0.5)
            }
        } finally {
            result?.release()
            model.release()
            output.delete()
            SliceBeam.getCurrentConfigFile().delete()
            printerKeys.forEach { key ->
                printer.values.remove(key)
                previousPrinter[key]?.let { printer.put(key, it) }
            }
            processKeys.forEach { key ->
                process.values.remove(key)
                previousProcess[key]?.let { process.put(key, it) }
            }
        }
    }

    @Test(timeout = 120_000)
    fun byLayerAsObjectListKeepsRotatedCopiesInTheirListOrder() = withKobraProfile {
        val model = Model()
        val configFile = SliceBeam.getCurrentConfigFile()
        try {
            assertEquals(0, model.createPrimitive(0, 5.0, 5.0, 0.2, "By-layer copies", 10.0, 10.0))
            model.ensureOnBed(0)
            val copyCount = 20
            for (copy in 1 until copyCount) {
                val dx = (copy % 5) * 12.0
                val dy = (copy / 5) * 12.0
                assertEquals("copy $copy must be linked to the same model object", copy,
                    model.duplicateInstance(0, 0, Vec3d(dx, dy, 0.0)))
            }

            SliceBeam.genCurrentConfig(model)
            val order = Native.by_layer_copy_order_regression(model.pointer, configFile.absolutePath)
            assertEquals("all copies must be returned", copyCount, order[0])
            assertArrayEquals(
                "By-layer G-code order must follow the model's instance list even when alternating copies have different rotations",
                IntArray(copyCount) { it },
                order.copyOfRange(1, order.size),
            )
        } finally {
            model.release()
            configFile.delete()
        }
    }

    @Test(timeout = 120_000)
    fun byObjectPrintingKeepsRotatedCopiesInTheirListOrder() = withKobraProfile {
        val model = Model()
        val configFile = SliceBeam.getCurrentConfigFile()
        try {
            assertEquals(0, model.createPrimitive(0, 5.0, 5.0, 0.2, "By-object copies", 10.0, 10.0))
            for (copy in 1 until 4) {
                assertEquals("copy $copy must remain linked to the same model object", copy,
                    model.duplicateInstance(0, 0, Vec3d(copy * 15.0, 0.0, 0.0)))
            }
            SliceBeam.genCurrentConfig(model)
            val order = Native.by_object_copy_order_regression(model.pointer, configFile.absolutePath)
            assertEquals("all four by-object copies must be included", 4, order[0])
            assertArrayEquals(
                "By-object safety validation and G-code order must follow the model's instance list for alternating rotations",
                IntArray(4) { it },
                order.copyOfRange(1, order.size),
            )
        } finally {
            model.release()
            configFile.delete()
        }
    }

    private fun sliceToGcode(context: Context, model: Model, name: String): String {
        val gcodeFile = File(context.cacheDir, name)
        var generatedConfig: File? = null
        var result: GCodeProcessorResult? = null
        try {
            SliceBeam.genCurrentConfig(model)
            generatedConfig = SliceBeam.getCurrentConfigFile()
            result = model.slice(generatedConfig.absolutePath, gcodeFile.absolutePath, null)
            return gcodeFile.readText()
        } finally {
            result?.release()
            gcodeFile.delete()
            generatedConfig?.delete()
        }
    }

    private fun descendingXyTravelMoves(gcode: String): List<String> {
        var absolute = true
        var x = 0.0
        var y = 0.0
        var z = 0.0
        var slicingLayersStarted = false
        var inWipeTower = false
        var lineNumber = 0
        val descendingMoves = mutableListOf<String>()
        val token = Regex("([XYZ])(-?\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE)
        for (rawLine in gcode.lineSequence()) {
            lineNumber++
            val line = rawLine.substringBefore(';').trim().uppercase()
            if (rawLine.substringAfter(';', "").trim().equals("LAYER_CHANGE", ignoreCase = true)) {
                slicingLayersStarted = true
                continue
            }
            val comment = rawLine.substringAfter(';', "").trim()
            if (comment.equals("WIPE_TOWER_START", ignoreCase = true)) {
                inWipeTower = true
                continue
            }
            // Printer start/purge macros may deliberately wipe diagonally down to the bed.
            // The Orca behavior under test applies to generated model travels, not that macro.
            if (!slicingLayersStarted) continue
            when (line.substringBefore(' ')) {
                "G90" -> { absolute = true; continue }
                "G91" -> { absolute = false; continue }
                "G92" -> {
                    val values = token.findAll(line).associate { it.groupValues[1] to it.groupValues[2].toDouble() }
                    values["X"]?.let { x = it }
                    values["Y"]?.let { y = it }
                    values["Z"]?.let { z = it }
                    continue
                }
            }
            val command = line.substringBefore(' ')
            if (inWipeTower && Regex("T\\d+").matches(command)) {
                inWipeTower = false
                continue
            }
            if (command !in setOf("G0", "G00", "G1", "G01", "G2", "G02", "G3", "G03")) continue
            val values = token.findAll(line).associate { it.groupValues[1] to it.groupValues[2].toDouble() }
            val nextX = values["X"]?.let { if (absolute) it else x + it } ?: x
            val nextY = values["Y"]?.let { if (absolute) it else y + it } ?: y
            val nextZ = values["Z"]?.let { if (absolute) it else z + it } ?: z
            val hasXyMove = kotlin.math.abs(nextX - x) > 1e-6 || kotlin.math.abs(nextY - y) > 1e-6
            // The wipe-tower generator intentionally ramps through Z while shaping its purge path.
            // This Orca regression concerns travel back from the tower to model geometry.
            if (!inWipeTower && !line.contains(Regex("(?:^|\\s)E-?\\d")) && hasXyMove && nextZ < z - 1e-6)
                descendingMoves += "line=$lineNumber z=$z->$nextZ ${rawLine.trim()}"
            x = nextX
            y = nextY
            z = nextZ
        }
        return descendingMoves
    }

    private fun maxConsecutiveElevatedTravelXyMoves(gcode: String): Int {
        var absolute = true
        var x = 0.0
        var y = 0.0
        var z = 0.0
        var nominalLayerZ = 0.0
        var currentRun = 0
        var longestRun = 0
        val token = Regex("([XYZ])(-?\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE)
        for (rawLine in gcode.lineSequence()) {
            Regex(";Z:([0-9]+(?:\\.[0-9]+)?)").find(rawLine)?.groupValues?.get(1)?.toDoubleOrNull()?.let {
                nominalLayerZ = it
            }
            val line = rawLine.substringBefore(';').trim().uppercase()
            when (line.substringBefore(' ')) {
                "G90" -> { absolute = true; continue }
                "G91" -> { absolute = false; continue }
                "G92" -> {
                    val values = token.findAll(line).associate { it.groupValues[1] to it.groupValues[2].toDouble() }
                    values["X"]?.let { x = it }
                    values["Y"]?.let { y = it }
                    values["Z"]?.let { z = it }
                    continue
                }
            }
            val command = line.substringBefore(' ')
            if (command !in setOf("G0", "G00", "G1", "G01", "G2", "G02", "G3", "G03")) continue
            val values = token.findAll(line).associate { it.groupValues[1] to it.groupValues[2].toDouble() }
            val nextX = values["X"]?.let { if (absolute) it else x + it } ?: x
            val nextY = values["Y"]?.let { if (absolute) it else y + it } ?: y
            val nextZ = values["Z"]?.let { if (absolute) it else z + it } ?: z
            val hasXyMove = kotlin.math.abs(nextX - x) > 1e-6 || kotlin.math.abs(nextY - y) > 1e-6
            if (!line.contains(Regex("(?:^|\\s)E-?\\d")) && hasXyMove && nextZ > nominalLayerZ + 0.15) {
                currentRun++
                longestRun = maxOf(longestRun, currentRun)
            } else {
                currentRun = 0
            }
            x = nextX
            y = nextY
            z = nextZ
        }
        return longestRun
    }

    private fun layerZValues(gcode: String): List<Double> =
        Regex("(?m)^;Z:([0-9]+(?:\\.[0-9]+)?)$")
            .findAll(gcode)
            .map { it.groupValues[1].toDouble() }
            .toList()

    private fun assertHasExtrusion(gcode: String) {
        assertTrue("Slicing should produce non-empty G-code", gcode.isNotBlank())
        assertTrue("Slicing should produce deposited moves", Regex("(?m)^G1 .*\\bE[0-9.-]+").containsMatchIn(gcode))
    }

    private fun toolsForFeature(gcode: String, feature: String): Set<Int> {
        var activeTool = 0
        var activeFeature = ""
        val tools = mutableSetOf<Int>()
        val toolChange = Regex("^T(\\d+)(?:\\s|$)")
        val extrusion = Regex("\\bE[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)")
        for (line in gcode.lineSequence()) {
            toolChange.find(line)?.let { activeTool = it.groupValues[1].toInt() }
            if (line.startsWith(";TYPE:")) activeFeature = line.substringAfter(";TYPE:").trim()
            if (activeFeature == feature && line.startsWith("G1 ") && extrusion.containsMatchIn(line)) {
                tools += activeTool
            }
        }
        return tools
    }

    private fun idleToolChanges(gcode: String): Int {
        var inLayers = false
        var changedTool = false
        var extrudedAfterChange = false
        var idleChanges = 0
        val toolChange = Regex("^T\\d+(?:\\s|$)")
        for (line in gcode.lineSequence()) {
            val isToolChange = toolChange.containsMatchIn(line)
            val isLayerChange = line.contains(";LAYER_CHANGE")
            if (isToolChange || isLayerChange) {
                if (changedTool && !extrudedAfterChange) idleChanges++
                inLayers = inLayers || isLayerChange
                changedTool = isToolChange && inLayers
                extrudedAfterChange = false
            } else if (changedTool && line.startsWith("G1 ") &&
                Regex("\\bE[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)").containsMatchIn(line) &&
                (Regex("\\bX[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)").containsMatchIn(line) ||
                    Regex("\\bY[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)").containsMatchIn(line))) {
                extrudedAfterChange = true
            }
        }
        if (changedTool && !extrudedAfterChange) idleChanges++
        return idleChanges
    }
}
