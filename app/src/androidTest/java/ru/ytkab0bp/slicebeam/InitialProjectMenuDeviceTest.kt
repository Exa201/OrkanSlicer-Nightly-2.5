package ru.ytkab0bp.slicebeam

import android.app.Application
import android.app.Activity
import android.app.Instrumentation.ActivityResult
import android.content.Intent
import android.content.IntentFilter
import android.content.ClipData
import android.net.Uri
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.FileProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.geometry.Offset
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.assertArrayEquals
import org.junit.Assume.assumeTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import android.os.SystemClock
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import ru.ytkab0bp.slicebeam.components.bed_menu.FileMenu
import ru.ytkab0bp.slicebeam.components.bed_menu.FileMenuObjectActions
import ru.ytkab0bp.slicebeam.components.bed_menu.ListBedMenu
import ru.ytkab0bp.slicebeam.components.bed_menu.CameraPreset
import ru.ytkab0bp.slicebeam.components.bed_menu.CameraPresetGeometry
import ru.ytkab0bp.slicebeam.components.bed_menu.CameraVector
import ru.ytkab0bp.slicebeam.ui.workspace.WorkspaceViewCubeGeometry
import ru.ytkab0bp.slicebeam.ui.workspace.WorkspaceViewFace
import ru.ytkab0bp.slicebeam.components.PreSliceFilamentDialog
import ru.ytkab0bp.slicebeam.config.ConfigObject
import ru.ytkab0bp.slicebeam.config.ProfileSettingsRepository
import ru.ytkab0bp.slicebeam.config.TemporaryProjectSettings
import ru.ytkab0bp.slicebeam.fragment.BedFragment
import ru.ytkab0bp.slicebeam.slic3r.BooleanOperation
import ru.ytkab0bp.slicebeam.slic3r.Model
import ru.ytkab0bp.slicebeam.slic3r.StepImportOptions
import ru.ytkab0bp.slicebeam.slic3r.GLModel
import ru.ytkab0bp.slicebeam.slic3r.PaintSession
import ru.ytkab0bp.slicebeam.slic3r.ObjectBounds3D
import ru.ytkab0bp.slicebeam.slic3r.ObjectPlacement
import ru.ytkab0bp.slicebeam.slic3r.GCodeProcessorResult
import ru.ytkab0bp.slicebeam.slic3r.SliceListener
import ru.ytkab0bp.slicebeam.render.GLRenderer
import ru.ytkab0bp.slicebeam.render.MODEL_EDIT_STALE_SELECTION
import ru.ytkab0bp.slicebeam.utils.FilamentSlot
import ru.ytkab0bp.slicebeam.utils.ModelImportPackingOrder
import ru.ytkab0bp.slicebeam.slic3r.Slic3rConfigWrapper
import ru.ytkab0bp.slicebeam.ui.navigation.WorkspaceDestination
import ru.ytkab0bp.slicebeam.ui.navigation.AdaptiveNavigationPolicy
import ru.ytkab0bp.slicebeam.utils.Prefs
import ru.ytkab0bp.slicebeam.utils.StlImportPreflight
import ru.ytkab0bp.slicebeam.utils.Vec3d
import java.util.concurrent.atomic.AtomicReference

/** Reproduces the first Project tap after a cold workspace launch. */
@RunWith(AndroidJUnit4::class)
class InitialProjectMenuDeviceTest {
    @get:Rule
    val compose = createInitializedActivityRule()
    private var previousRequestedOrientation: Int? = null

    @Test(timeout = 900_000)
    fun settlersArchiveModelsAreOrientedPackedAndMovedToOverflowPlates() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        compose.waitUntil(timeoutMillis = 15_000) { glView.renderer.bed?.isValid() == true }

        val archive = File(activity.cacheDir, "settlers-arrangement-fixture.zip")
        if (!archive.isFile) {
            runCatching {
                InstrumentationRegistry.getInstrumentation().context.assets
                    .open("models/settlers-arrangement-fixture.zip").use { input ->
                        archive.outputStream().buffered().use(input::copyTo)
                    }
            }
        }
        if (!archive.isFile) {
            runCatching {
                File(requireNotNull(activity.getExternalFilesDir(null)), "settlers-arrangement-fixture.zip")
                    .inputStream().buffered().use { source ->
                        archive.outputStream().buffered().use(source::copyTo)
                    }
            }
        }
        assumeTrue("Push the supplied Settlers archive into the Nightly test app external files", archive.isFile)
        val fixtureDir = File(activity.cacheDir, "settlers-arrangement-${System.nanoTime()}").apply { mkdirs() }
        val modelFiles = mutableListOf<File>()
        try {
            ZipInputStream(archive.inputStream().buffered()).use { input ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    val name = File(entry.name).name
                    if (!entry.isDirectory && name.endsWith(".stl", ignoreCase = true)) {
                        val destination = File(fixtureDir, name)
                        destination.outputStream().buffered().use(input::copyTo)
                        modelFiles += destination
                    }
                    input.closeEntry()
                }
            }
            val areas = modelFiles.map { StlImportPreflight.largestOrientedFootprintArea(it) ?: 0.0 }
            val selected = ModelImportPackingOrder.largestFirst(areas).map(modelFiles::get)
            assertEquals("Every STL model from the supplied archive must be present", 109, selected.size)
            assertEquals("Test starts with an empty plate", 0, requireNotNull(bed.currentModel).objectsCount)
            assertEquals("Test starts on one plate", 1, bed.plateCount)

            val completed = CountDownLatch(1)
            val error = AtomicReference<Exception?>()
            bed.loadModelBatch(selected, object : BedFragment.ModelLoadCallback {
                override fun onLoaded(loadedModel: Model, firstNewObject: Int, addedObjects: Int) {
                    android.util.Log.i("SettlersArrangeTest", "Loaded ${loadedModel.objectsCount} objects " +
                        "in one batch; plates=${bed.plateCount}")
                    completed.countDown()
                }
                override fun onFailed(failure: Exception) {
                    error.set(failure)
                    completed.countDown()
                }
            }, autoOrientImportedModels = true)
            assertTrue("Batch import of ${selected.size} Settlers STLs timed out",
                completed.await(12, TimeUnit.MINUTES))
            error.get()?.let { throw AssertionError("Could not import Settlers STL batch", it) }
            compose.waitUntil(timeoutMillis = 20_000) {
                !BedFragment::class.java.getDeclaredField("modelLoadInProgress")
                    .apply { isAccessible = true }.getBoolean(bed)
            }
            compose.waitUntil(timeoutMillis = 20_000) { bed.plateCount > 1 }
            val plates = readPlateModels(bed)
            assertEquals("All imported STL models remain independent objects", selected.size,
                plates.sumOf { it.objectsCount })
            assertTrue("Overflow models must be assigned to new plates", plates.size > 1)
            assertTrue("Arrangement must pack multiple archive models instead of using one plate per model; " +
                "plates=${plates.size}, objectsPerPlate=${plates.map { it.objectsCount }}", plates.size < selected.size)
            assertTrue("Automatic nesting should use the bed efficiently for the supplied 109-part set; " +
                "plates=${plates.size}", plates.size <= 35)

            val bedMin = requireNotNull(glView.renderer.bed?.getVolumeMin())
            val bedMax = requireNotNull(glView.renderer.bed?.getVolumeMax())
            plates.forEachIndexed { plateIndex, model ->
                repeat(model.objectsCount) { objectIndex ->
                    val bounds = readExactBoundsOnGl(glView, model, objectIndex)
                    assertTrue("Plate $plateIndex object $objectIndex (${model.getObjectName(objectIndex)}) " +
                        "is outside the bed: ${bounds.toList()}, bed=${bedMin.x},${bedMin.y}..${bedMax.x},${bedMax.y}",
                        bounds[0] >= bedMin.x - 0.2 && bounds[1] >= bedMin.y - 0.2 &&
                            bounds[3] <= bedMax.x + 0.2 && bounds[4] <= bedMax.y + 0.2)
                    assertTrue("Imported object must rest on the build surface", bounds[2] >= bedMin.z - 0.2)
                }
            }

            val checkedPairs = AtomicReference(0)
            val overlap = AtomicReference<String?>()
            val collisionCheck = CountDownLatch(1)
            glView.queueEvent {
                try {
                    plates.forEachIndexed { plateIndex, plate ->
                        for (first in 0 until plate.objectsCount) {
                            for (second in first + 1 until plate.objectsCount) {
                                val probe = Model()
                                try {
                                    probe.addObject(plate, first)
                                    probe.addObject(plate, second)
                                    val intersection = probe.booleanObjects(0, 1, BooleanOperation.INTERSECTION)
                                    checkedPairs.set(checkedPairs.get() + 1)
                                    if (intersection >= 0) {
                                        overlap.compareAndSet(null,
                                            "plate $plateIndex objects $first (${plate.getObjectName(first)}) " +
                                                "and $second (${plate.getObjectName(second)}) intersect")
                                        return@queueEvent
                                    }
                                } finally {
                                    probe.release()
                                }
                            }
                        }
                    }
                } catch (failure: Throwable) {
                    overlap.compareAndSet(null, "mesh collision check failed: ${failure.message}")
                } finally {
                    collisionCheck.countDown()
                }
            }
            assertTrue("GL thread must complete mesh-collision validation", collisionCheck.await(5, TimeUnit.MINUTES))
            assertEquals("Every same-plate model pair must have disjoint meshes", null, overlap.get())
            android.util.Log.i("SettlersArrangeTest", "Nightly verified ${selected.size} models across ${plates.size} plates " +
                "(objectsPerPlate=${plates.map { it.objectsCount }}); " +
                "${checkedPairs.get()} same-plate mesh pairs are disjoint")
        } finally {
            modelFiles.forEach(File::delete)
            fixtureDir.deleteRecursively()
        }
    }

    @Test(timeout = 240_000)
    fun multiObjectImportReusesEarlierOverflowPlatesForRemainingParts() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        compose.waitUntil(timeoutMillis = 15_000) { glView.renderer.bed?.isValid() == true }
        assertEquals("Test starts with an empty plate", 0, requireNotNull(bed.currentModel).objectsCount)

        val previousConfig = SliceBeam.CONFIG
        val previousInstance = SliceBeam.INSTANCE
        val sourceFile = File(activity.cacheDir, "batch-overflow-cube.stl")
        val projectFile = File(activity.cacheDir, "batch-overflow-parts.3mf")
        var batch: Model? = null
        var generatedConfig: File? = null
        try {
            SliceBeam.INSTANCE = activity.application as SliceBeam
            Prefs.init(activity.application)
            activity.assets.open("models/xyz_cube.stl").use { source ->
                sourceFile.outputStream().use(source::copyTo)
            }
            activity.assets.open("orca_profiles/Anycubic.ini").use { SliceBeam.CONFIG = Slic3rConfigWrapper(it) }
            SliceBeam.requireConfig().presets = ConfigObject("presets").apply {
                put("printer", "Anycubic Kobra 3 0.4 nozzle")
                put("print", "0.16mm Standard")
                put("filament", "Anycubic Generic PLA")
            }

            batch = Model(sourceFile)
            repeat(5) { batch!!.addObject(batch!!, 0) }
            val baseMin = Vec3d(); val baseMax = Vec3d()
            batch!!.getBoundingBoxExact(0, baseMin, baseMax)
            val baseWidth = baseMax.x - baseMin.x
            val bedMin = requireNotNull(glView.renderer.bed?.getVolumeMin())
            val bedMax = requireNotNull(glView.renderer.bed?.getVolumeMax())
            val bedWidth = bedMax.x - bedMin.x
            val bedDepth = bedMax.y - bedMin.y
            val largeScale = minOf(bedWidth, bedDepth) * 0.60 / baseWidth
            val smallScale = minOf(bedWidth, bedDepth) * 0.28 / baseWidth
            repeat(3) { batch!!.scale(it, largeScale, largeScale, largeScale) }
            repeat(3) { batch!!.scale(it + 3, smallScale, smallScale, smallScale) }
            SliceBeam.genCurrentConfig(batch!!)
            generatedConfig = SliceBeam.getCurrentConfigFile()
            batch!!.export3mf(generatedConfig!!.absolutePath, projectFile.absolutePath)
            generatedConfig!!.delete()
            generatedConfig = null
            assertTrue("Multi-object fixture must export as a 3MF", projectFile.isFile && projectFile.length() > 0L)

            val completed = CountDownLatch(1)
            val failure = AtomicReference<Exception?>()
            val callbackRange = AtomicReference<Pair<Int, Int>?>()
            val callbackModelObjectCount = AtomicReference(0)
            bed.loadModel(projectFile, preserveProjectLayout = false, plateCount = 1,
                callback = object : BedFragment.ModelLoadCallback {
                    override fun onLoaded(loadedModel: Model, firstNewObject: Int, addedObjects: Int) {
                        callbackRange.set(firstNewObject to addedObjects)
                        callbackModelObjectCount.set(loadedModel.objectsCount)
                        completed.countDown()
                    }
                    override fun onFailed(error: Exception) {
                        failure.set(error)
                        completed.countDown()
                    }
                }, autoOrientImportedModels = true)
            assertTrue("Multi-object import should finish", completed.await(180, TimeUnit.SECONDS))
            failure.get()?.let { throw AssertionError("Multi-object import failed", it) }

            val plates = readPlateModels(bed)
            assertEquals("All imported objects must be retained", 6, plates.sumOf { it.objectsCount })
            val (callbackFirstObject, callbackAddedObjects) = requireNotNull(callbackRange.get())
            assertTrue("The callback range must describe only objects on its reported destination plate",
                callbackFirstObject >= 0 && callbackAddedObjects > 0 &&
                    callbackFirstObject + callbackAddedObjects <= callbackModelObjectCount.get())
            val outOfBounds = AtomicReference<String?>()
            val collision = AtomicReference<String?>()
            val smallPartOnEarlierPlate = AtomicReference(false)
            val verified = CountDownLatch(1)
            glView.queueEvent {
                try {
                    plates.forEachIndexed { plateIndex, model ->
                        repeat(model.objectsCount) { objectIndex ->
                            val min = Vec3d(); val max = Vec3d()
                            model.getBoundingBoxExact(objectIndex, min, max)
                            val bounds = doubleArrayOf(min.x, min.y, min.z, max.x, max.y, max.z)
                            val maxFootprintSide = maxOf(bounds[3] - bounds[0], bounds[4] - bounds[1])
                            if (plateIndex < plates.lastIndex && maxFootprintSide < minOf(bedWidth, bedDepth) * 0.45) {
                                smallPartOnEarlierPlate.set(true)
                            }
                            if (bounds[0] < bedMin.x - 0.2 || bounds[1] < bedMin.y - 0.2 ||
                                bounds[3] > bedMax.x + 0.2 || bounds[4] > bedMax.y + 0.2 ||
                                bounds[2] < bedMin.z - 0.2) {
                                outOfBounds.compareAndSet(null, "plate $plateIndex object $objectIndex: ${bounds.toList()}")
                            }
                        }
                        for (first in 0 until model.objectsCount) for (second in first + 1 until model.objectsCount) {
                            val probe = Model()
                            try {
                                probe.addObject(model, first)
                                probe.addObject(model, second)
                                if (probe.booleanObjects(0, 1, BooleanOperation.INTERSECTION) >= 0)
                                    collision.compareAndSet(null, "plate $plateIndex objects $first and $second intersect")
                            } finally {
                                probe.release()
                            }
                        }
                    }
                } catch (error: Throwable) {
                    collision.compareAndSet(null, "mesh intersection check failed: ${error.message}")
                } finally {
                    verified.countDown()
                }
            }
            assertTrue("GL thread should verify placements", verified.await(90, TimeUnit.SECONDS))
            assertEquals("All parts should remain on the build surface", null, outOfBounds.get())
            assertEquals("Parts on each reused plate must remain collision-free", null, collision.get())
            assertTrue("A smaller imported part should reuse free space on a plate created earlier in this batch",
                smallPartOnEarlierPlate.get())
            assertTrue("Large parts must force overflow, while smaller parts reuse earlier plate space; " +
                "objectsPerPlate=${plates.map { it.objectsCount }}", plates.size in 2..3)
        } finally {
            generatedConfig?.delete()
            batch?.release()
            sourceFile.delete()
            projectFile.delete()
            SliceBeam.CONFIG = previousConfig
            SliceBeam.INSTANCE = previousInstance
        }
    }

    @Test(timeout = 300_000)
    fun settlersBaseAdapterTilesArrangeWithoutMeshOverlaps() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        compose.waitUntil(timeoutMillis = 15_000) { glView.renderer.bed?.isValid() == true }

        val archive = File(activity.cacheDir, "settlers-arrangement-fixture.zip")
        if (!archive.isFile) {
            runCatching {
                File(requireNotNull(activity.getExternalFilesDir(null)), "settlers-arrangement-fixture.zip")
                    .inputStream().buffered().use { source ->
                        archive.outputStream().buffered().use(source::copyTo)
                    }
            }
        }
        assumeTrue("Push the supplied Settlers archive into the Nightly test app external files", archive.isFile)
        val fixtureDir = File(activity.cacheDir, "settlers-adapter-arrangement-${System.nanoTime()}").apply { mkdirs() }
        val modelFiles = mutableListOf<File>()
        try {
            ZipInputStream(archive.inputStream().buffered()).use { input ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    val name = File(entry.name).name
                    if (!entry.isDirectory && name.matches(Regex("obj_(8[4-9]|9[2]|9[3]|9[4]|9[5]|9[6]|9[7]|9[8]|9[9]|10[0-9])_base_adapter_interlocking_tiles\\.stl"))) {
                        val destination = File(fixtureDir, name)
                        destination.outputStream().buffered().use(input::copyTo)
                        modelFiles += destination
                    }
                    input.closeEntry()
                }
            }
            val selected = modelFiles.sortedBy {
                Regex("^obj_(\\d+)_").find(it.name)?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE
            }
            assertEquals("All 24 base-adapter tiles from the archive must be present", 24, selected.size)
            assertEquals("Test starts with an empty plate", 0, requireNotNull(bed.currentModel).objectsCount)
            assertEquals("Test starts on one plate", 1, bed.plateCount)

            selected.forEachIndexed { ordinal, file ->
                val completed = CountDownLatch(1)
                val error = AtomicReference<Exception?>()
                bed.loadModel(file, preserveProjectLayout = false, plateCount = 1,
                    callback = object : BedFragment.ModelLoadCallback {
                        override fun onLoaded(loadedModel: Model, firstNewObject: Int, addedObjects: Int) {
                            completed.countDown()
                        }
                        override fun onFailed(failure: Exception) {
                            error.set(failure)
                            completed.countDown()
                        }
                    }, autoOrientImportedModels = true)
                assertTrue("Adapter import ${ordinal + 1}/${selected.size} timed out: ${file.name}",
                    completed.await(45, TimeUnit.SECONDS))
                error.get()?.let { throw AssertionError("Could not import ${file.name}", it) }
            }

            val plates = readPlateModels(bed)
            assertEquals("Every imported adapter remains an independent model", selected.size,
                plates.sumOf { it.objectsCount })
            val overlap = AtomicReference<String?>()
            val checkedPairs = AtomicReference(0)
            val collisionCheck = CountDownLatch(1)
            glView.queueEvent {
                try {
                    plates.forEachIndexed { plateIndex, plate ->
                        for (first in 0 until plate.objectsCount) {
                            for (second in first + 1 until plate.objectsCount) {
                                val probe = Model()
                                try {
                                    probe.addObject(plate, first)
                                    probe.addObject(plate, second)
                                    val intersection = probe.booleanObjects(0, 1, BooleanOperation.INTERSECTION)
                                    checkedPairs.set(checkedPairs.get() + 1)
                                    if (intersection >= 0) {
                                        overlap.compareAndSet(null,
                                            "plate $plateIndex objects $first (${plate.getObjectName(first)}) and " +
                                                "$second (${plate.getObjectName(second)}) intersect, " +
                                                "intersectionVolume=${probe.getObjectVolume(intersection)}")
                                        return@queueEvent
                                    }
                                } finally {
                                    probe.release()
                                }
                            }
                        }
                    }
                } catch (failure: Throwable) {
                    overlap.compareAndSet(null, "mesh intersection probe failed: ${failure.message}")
                } finally {
                    collisionCheck.countDown()
                }
            }
            assertTrue("Mesh collision validation must finish on the GL thread",
                collisionCheck.await(3, TimeUnit.MINUTES))
            assertEquals("Adapter tiles must not overlap: ${overlap.get()}", null, overlap.get())
            android.util.Log.i("SettlersArrangeTest", "Base-adapter regression: ${selected.size} objects on " +
                "${plates.size} plates, ${plates.map { it.objectsCount }} objects per plate, " +
                "$checkedPairs checked mesh pairs, no overlaps")
        } finally {
            modelFiles.forEach(File::delete)
            fixtureDir.deleteRecursively()
            archive.delete()
        }
    }

    /** Allows a true Activity configuration change for the full menu class on real devices. */
    @Before
    fun applyRequestedWorkspaceTestOrientation() {
        val requested = InstrumentationRegistry.getArguments().getString("ui2.testOrientation")
        val target = when (requested?.lowercase()) {
            "landscape" -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            "portrait" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else -> return
        }
        val activity = compose.activity
        previousRequestedOrientation = activity.requestedOrientation
        activity.runOnUiThread { activity.requestedOrientation = target }
        val expected = if (target == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
            Configuration.ORIENTATION_LANDSCAPE
        } else {
            Configuration.ORIENTATION_PORTRAIT
        }
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.activity.resources.configuration.orientation == expected
        }
    }

    @After
    fun restoreRequestedWorkspaceTestOrientation() {
        val previous = previousRequestedOrientation ?: return
        previousRequestedOrientation = null
        val activity = compose.activity
        activity.runOnUiThread { activity.requestedOrientation = previous }
        val expected = when (previous) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE -> Configuration.ORIENTATION_LANDSCAPE
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT -> Configuration.ORIENTATION_PORTRAIT
            else -> null
        }
        if (expected != null) {
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.activity.resources.configuration.orientation == expected
            }
        }
    }

    @Test
    fun externallyOpenedDetailedMulticolorSvgLoadsThroughWorkspaceImportPath() {
        val activity = compose.activity
        val source = File(activity.cacheDir, "external-multicolor-${System.nanoTime()}.svg")
        try {
            InstrumentationRegistry.getInstrumentation().context.assets
                .open("models/Beste_Oma_Kind_Herz_3D_Druck.svg").use { input ->
                source.outputStream().use(input::copyTo)
            }
            @Suppress("DEPRECATION")
            val provider = activity.packageManager.getPackageInfo(
                activity.packageName,
                PackageManager.GET_PROVIDERS,
            ).providers.orEmpty().first { it.name == FileProvider::class.java.name }
            val authority = requireNotNull(provider.authority).substringBefore(';')
            val uri = FileProvider.getUriForFile(activity, authority, source)

            activity.runOnUiThread {
                MainActivity::class.java.getDeclaredMethod("handleExternalModelOpen", Intent::class.java)
                    .apply { isAccessible = true }
                    .invoke(activity, Intent(Intent.ACTION_VIEW).setData(uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }

            compose.waitUntil(timeoutMillis = 60_000) {
                val model = (activity.navigationDelegate.getCurrentFragment() as? BedFragment)?.currentModel
                model != null && model.objectsCount == 1 && model.getTriangleCount(0) > 0
            }
            val model = requireNotNull((activity.navigationDelegate.getCurrentFragment() as BedFragment).currentModel)
            assertTrue("Imported SVG should render with its original printable width",
                model.boundingBoxApproxMax.x - model.boundingBoxApproxMin.x > 100.0)
            assertTrue("Opening an SVG must not delete the selected source document", source.isFile)
        } finally {
            source.delete()
        }
    }

    @Test
    fun compactViewCubeIsVisibleAccessibleAndAtLeast48Dp() {
        // A fresh debug install can show the in-app changelog above the workspace.
        // Dismiss it so this checks the header at the requested display size.
        val changelogTitle = compose.activity.getString(R.string.Changelog)
        val changelogOk = compose.activity.getString(R.string.ChangelogOK)
        fun cubeIsDisplayed(): Boolean = try {
            compose.onNodeWithTag("workspace-view-cube").assertIsDisplayed()
            true
        } catch (_: AssertionError) {
            false
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(changelogTitle).fetchSemanticsNodes().isNotEmpty() || cubeIsDisplayed()
        }
        if (compose.onAllNodesWithText(changelogTitle).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText(changelogOk).performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText(changelogTitle).fetchSemanticsNodes().isEmpty() && cubeIsDisplayed()
            }
        }
        val cube = compose.onNodeWithTag("workspace-view-cube").assertIsDisplayed()
        val node = cube.fetchSemanticsNode()
        val printerActionDescription = listOf(
            R.string.PrinterOpenWebDescription,
            R.string.PrinterWebConfigureDescription,
        ).map(compose.activity::getString).firstOrNull { description ->
            compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
        } ?: error("Printer header action should expose an accessibility description")
        val printerAction = compose.onNodeWithContentDescription(printerActionDescription)
            .assertIsDisplayed().fetchSemanticsNode()
        val screenRight = compose.activity.window.decorView.width.toFloat()
        assertTrue("Printer header action must remain on screen", printerAction.boundsInRoot.right <= screenRight)
        val density = compose.activity.resources.displayMetrics.density
        assertTrue("View cube must have a 48 dp touch target", node.size.width / density >= 48f)
        assertTrue("View cube must have a 48 dp touch target", node.size.height / density >= 48f)
        assertEquals(6, node.config[SemanticsActions.CustomActions].size)
    }

    @Test
    fun modelMenuSeparatesSelectionActionsFromPlateWideActions() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val menuPreferences = activity.getSharedPreferences("workspace_menu_customization", 0)
        val previousSelectionOrder = menuPreferences.getString("order_model_selected", null)
        val previousSelectionHidden = menuPreferences.getStringSet("hidden_model_selected", null)?.toSet()
        menuPreferences.edit().remove("order_model_selected").remove("hidden_model_selected").commit()
        val modelFile = File(activity.cacheDir, "model-menu-scope-test.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            val model = loadSingleTestModel(bed, modelFile)
            val prepared = CountDownLatch(1)
            glView.queueEvent {
                val minimum = Vec3d()
                val maximum = Vec3d()
                model.getBoundingBoxExact(0, minimum, maximum)
                val width = maximum.x - minimum.x
                model.addObject(model, 0)
                model.translate(1, width * 1.5, 0.0, 0.0)
                glView.renderer.setSelectedObject(1)
                glView.renderer.setSelectedObjects(intArrayOf(0, 1))
                glView.requestRender()
                prepared.countDown()
            }
            assertTrue("two temporary objects should be prepared on GL", prepared.await(10, TimeUnit.SECONDS))
            compose.runOnUiThread { bed.updateModel() }
            instrumentationWaitForGlIdle()

            openSelectedObjectMenu(activity)
            compose.onNodeWithText(activity.getString(R.string.ModelMenuSelectedObjectGroup)).assertIsDisplayed()
            revealMenuAction(activity, R.string.ModelMenuDuplicate)
            compose.onNodeWithContentDescription(activity.getString(R.string.ModelMenuDuplicate)).assertIsDisplayed()
            revealMenuAction(activity, R.string.ModelMenuSplit)
            compose.onNodeWithContentDescription(activity.getString(R.string.ModelMenuSplit))
                .assertIsDisplayed().assertIsEnabled()
            revealMenuAction(activity, R.string.ModelMenuCombineSelected)
            compose.onNodeWithContentDescription(activity.getString(R.string.ModelMenuCombineSelected))
                .assertIsDisplayed().assertIsEnabled()
            revealMenuAction(activity, R.string.ModelMenuBoolean)
            compose.onNodeWithContentDescription(activity.getString(R.string.ModelMenuBoolean))
                .assertIsDisplayed().assertIsEnabled()
            compose.onNodeWithContentDescription(activity.getString(R.string.ModelMenuArrange))
                .assertDoesNotExist()

            selectAllObjectsScope()
            compose.onNodeWithText(activity.getString(R.string.ModelMenuAllObjectsGroup)).assertIsDisplayed()
            revealMenuAction(activity, R.string.ModelMenuArrange)
            compose.onNodeWithContentDescription(activity.getString(R.string.ModelMenuArrange))
                .assertIsDisplayed()
            revealMenuAction(activity, R.string.ModelMenuSelectAll)
            compose.onNodeWithContentDescription(activity.getString(R.string.ModelMenuSelectAll))
                .assertIsDisplayed()
            compose.onNodeWithContentDescription(activity.getString(R.string.ModelMenuSplit))
                .assertDoesNotExist()
            compose.onNodeWithContentDescription(activity.getString(R.string.ModelMenuCombineSelected))
                .assertDoesNotExist()
            compose.onNodeWithContentDescription(activity.getString(R.string.ModelMenuBoolean))
                .assertDoesNotExist()

        } finally {
            modelFile.delete()
            menuPreferences.edit().apply {
                if (previousSelectionOrder == null) remove("order_model_selected")
                else putString("order_model_selected", previousSelectionOrder)
                if (previousSelectionHidden == null) remove("hidden_model_selected")
                else putStringSet("hidden_model_selected", previousSelectionHidden)
            }.commit()
        }
    }

    @Test
    fun modelMenuSelectionActionsFollowGlOwnedModelAndSelectionState() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val modelFile = File(activity.cacheDir, "model-menu-gl-state-test.stl")
        try {
            openModelMenu(activity)
            revealMenuAction(activity, R.string.ModelMenuDuplicate)
            val duplicate = compose.onNodeWithContentDescription(activity.getString(R.string.ModelMenuDuplicate))
                .performScrollTo()
            duplicate.assertIsDisplayed().assertIsNotEnabled()

            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            loadSingleTestModel(bed, modelFile)
            compose.runOnUiThread { glView.selectModel(0, Runnable {}) }
            instrumentationWaitForGlIdle()
            compose.waitUntil(timeoutMillis = 5_000) {
                runCatching {
                    compose.onNodeWithContentDescription(activity.getString(R.string.ModelMenuDuplicate))
                        .fetchSemanticsNode().config[SemanticsProperties.Disabled]
                }.getOrNull() == null
            }
            compose.onNodeWithContentDescription(activity.getString(R.string.ModelMenuDuplicate))
                .assertIsEnabled()

            compose.runOnUiThread { glView.selectModel(-1, Runnable {}) }
            instrumentationWaitForGlIdle()
            compose.waitUntil(timeoutMillis = 5_000) {
                runCatching {
                    SemanticsProperties.Disabled in compose.onNodeWithContentDescription(
                        activity.getString(R.string.ModelMenuDuplicate),
                    ).fetchSemanticsNode().config
                }.getOrDefault(false)
            }
            compose.onNodeWithContentDescription(activity.getString(R.string.ModelMenuDuplicate))
                .assertIsNotEnabled()

            selectAllObjectsScope()
            compose.onNodeWithContentDescription(activity.getString(R.string.ModelMenuArrange))
                .assertIsDisplayed().assertIsEnabled()
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun modelMenuBooleanUnionRunsForAllThreeSelectedObjects() {
        runModelMenuBooleanRoundTrip(
            operationLabel = R.string.ModelBooleanUnion,
            fileName = "model-menu-three-boolean-test.stl",
            // The bundled STL cube's faceted mesh yields a 2.027 volume ratio for this
            // overlap arrangement even though its measured union bounds are exactly 2x.
            expectedVolumeRatio = 2.027,
            expectedBoundsWidthRatio = 2.0,
        ) { model, _, width ->
            model.translate(1, width * 0.5, 0.0, 0.0)
            model.translate(2, width, 0.0, 0.0)
        }
    }

    @Test
    fun booleanUiUsesPrimarySelectedObjectAsTargetMaterial() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val modelFile = File(activity.cacheDir, "boolean-primary-target-material-test.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            val model = loadSingleTestModel(bed, modelFile)
            val prepared = CountDownLatch(1)
            glView.queueEvent {
                val renderer = glView.renderer
                val min = Vec3d(); val max = Vec3d()
                model.getBoundingBoxExact(0, min, max)
                val width = max.x - min.x
                model.addObject(model, 0)
                model.translate(1, width * 0.5, 0.0, 0.0)
                model.setUniformFilament(0, 1)
                model.setUniformFilament(1, 2)
                renderer.setSelectedObject(1)
                renderer.setSelectedObjects(intArrayOf(0, 1))
                glView.requestRender()
                prepared.countDown()
            }
            assertTrue("Boolean sources should be prepared on the GL thread", prepared.await(10, TimeUnit.SECONDS))
            compose.runOnUiThread { bed.updateModel() }
            instrumentationWaitForGlIdle()

            openModelMenu(activity)
            selectSingleObjectScope()
            revealMenuAction(activity, R.string.ModelMenuBoolean)
            compose.onNodeWithContentDescription(activity.getString(R.string.ModelMenuBoolean))
                .assertIsDisplayed().assertIsEnabled().performClick()
            compose.onNodeWithText(activity.getString(R.string.ModelBooleanUnion), useUnmergedTree = true)
                .performScrollTo().assertIsDisplayed().performClick()

            compose.waitUntil(timeoutMillis = 30_000) { readObjectCountOnGl(glView) == 1 }
            assertArrayEquals(
                "the primary selected object (index 1) must supply the Boolean result material",
                intArrayOf(2), readUsedFilamentsOnGl(glView, 0),
            )
            compose.runOnUiThread { bed.undoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) { readObjectCountOnGl(glView) == 2 }
            assertArrayEquals("Undo should restore both source material assignments", intArrayOf(1),
                readUsedFilamentsOnGl(glView, 0))
            assertArrayEquals(intArrayOf(2), readUsedFilamentsOnGl(glView, 1))
            compose.runOnUiThread { bed.redoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) { readObjectCountOnGl(glView) == 1 }
            assertArrayEquals("Redo should retain the primary target's material", intArrayOf(2),
                readUsedFilamentsOnGl(glView, 0))
            assertCurrentWorkspaceModelRoundTripsAndSlices("boolean-primary-target-material")
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun modelMenuBooleanDifferenceAppliesMultipleCuttersAndSupportsUndoRedo() {
        runModelMenuBooleanRoundTrip(
            operationLabel = R.string.ModelBooleanDifference,
            fileName = "model-menu-boolean-difference-two-cutters.stl",
            expectedVolumeRatio = 0.946,
        ) { model, _, width ->
            val min = Vec3d(); val max = Vec3d()
            model.getBoundingBoxExact(0, min, max)
            val centerX = (min.x + max.x) / 2.0
            val centerY = (min.y + max.y) / 2.0
            val centerZ = (min.z + max.z) / 2.0
            for (index in 1..2) {
                model.scale(index, 0.3, 0.3, 0.3)
                val cutterMin = Vec3d(); val cutterMax = Vec3d()
                model.getBoundingBoxExact(index, cutterMin, cutterMax)
                val offsetX = if (index == 1) -width * 0.2 else width * 0.2
                model.translate(
                    index,
                    centerX + offsetX - (cutterMin.x + cutterMax.x) / 2.0,
                    centerY - (cutterMin.y + cutterMax.y) / 2.0,
                    centerZ - (cutterMin.z + cutterMax.z) / 2.0,
                )
            }
        }
    }

    @Test
    fun modelMenuBooleanIntersectionCombinesThreeObjectsAndSupportsUndoRedo() {
        runModelMenuBooleanRoundTrip(
            operationLabel = R.string.ModelBooleanIntersection,
            fileName = "model-menu-boolean-intersection-three-objects.stl",
            expectedVolumeRatio = 0.36,
        ) { model, _, width ->
            // Keep all three solids' lower faces at the plate plane. Their 0.6-width XY
            // overlap yields a 0.36 volume intersection that is directly sliceable.
            model.translate(1, width * 0.2, width * 0.2, 0.0)
            model.translate(2, -width * 0.2, -width * 0.2, 0.0)
        }
    }

    @Test
    fun failedBooleanIntersectionKeepsAllInputsAndReportsFailureFromModelMenu() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val modelFile = File(activity.cacheDir, "model-menu-boolean-disjoint-intersection.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            val model = loadSingleTestModel(bed, modelFile)
            val prepared = CountDownLatch(1)
            val beforeVolumes = AtomicReference<List<Double>>()
            val beforeRevision = AtomicReference<Long>()
            glView.queueEvent {
                val renderer = glView.renderer
                model.addObject(model, 0)
                model.addObject(model, 0)
                // Objects 0 and 1 overlap, while object 2 is disjoint, so the three-way
                // intersection cannot produce a solid.
                model.translate(1, 10.0, 0.0, 0.0)
                model.translate(2, 40.0, 0.0, 0.0)
                renderer.selectAllObjects()
                beforeVolumes.set((0 until model.objectsCount).map(model::getObjectVolume))
                beforeRevision.set(model.geometryRevision)
                glView.requestRender()
                prepared.countDown()
            }
            assertTrue("disjoint Boolean inputs should be prepared on GL", prepared.await(10, TimeUnit.SECONDS))
            compose.runOnUiThread { bed.updateModel() }
            instrumentationWaitForGlIdle()

            // Commit the three-object fixture, make and commit one user edit, then undo it.
            // This leaves a valid Redo entry while the same three-object model is active.
            val redoBounds = AtomicReference<List<DoubleArray>>()
            val redoEditPrepared = CountDownLatch(1)
            glView.queueEvent {
                val current = requireNotNull(glView.renderer.model)
                current.translate(0, 5.0, 0.0, 0.0)
                redoBounds.set((0..2).map { index ->
                    val min = Vec3d()
                    val max = Vec3d()
                    current.getBoundingBoxExact(index, min, max)
                    doubleArrayOf(min.x, min.y, min.z, max.x, max.y, max.z)
                })
                redoEditPrepared.countDown()
            }
            assertTrue("the redo edit should be prepared on GL", redoEditPrepared.await(10, TimeUnit.SECONDS))
            compose.runOnUiThread { bed.updateModel() }
            instrumentationWaitForGlIdle()
            compose.runOnUiThread { bed.undoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) { readObjectCountOnGl(glView) == 3 }
            instrumentationWaitForGlIdle()
            beforeVolumes.set((0..2).map { readObjectVolumeOnGl(glView, it) })
            beforeRevision.set(readGeometryRevisionOnGl(glView))

            openModelMenu(activity)
            selectSingleObjectScope()
            revealMenuAction(activity, R.string.ModelMenuBoolean)
            compose.onNodeWithContentDescription(activity.getString(R.string.ModelMenuBoolean))
                .assertIsDisplayed().assertIsEnabled().performClick()
            compose.onNodeWithTag("model-boolean-intersection").performScrollTo()
                .assertIsDisplayed().performClick()

            val failureMessages = listOf(
                R.string.ModelBooleanInvalidMesh,
                R.string.ModelBooleanInvalidResult,
                R.string.ModelBooleanResultCreationFailed,
                R.string.ModelBooleanEngineFailed,
            )
            compose.waitUntil(timeoutMillis = 15_000) {
                failureMessages.any { hasSnackbarTitle(it) }
            }
            assertEquals("failed intersection must preserve all three source objects", 3,
                readObjectCountOnGl(glView))
            assertEquals("failed intersection must not advance model geometry", beforeRevision.get(),
                readGeometryRevisionOnGl(glView))
            beforeVolumes.get().forEachIndexed { index, volume ->
                assertEquals("failed intersection must preserve object $index volume", volume,
                    readObjectVolumeOnGl(glView, index), volume * 0.002)
            }

            compose.runOnUiThread { bed.redoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) { readObjectCountOnGl(glView) == 3 }
            val restoredRedoBounds = readExactBoundsOnGl(glView, 0, 1, 2)
            redoBounds.get().zip(restoredRedoBounds).forEachIndexed { index, (before, after) ->
                assertArrayEquals("failed intersection must leave Redo available for object $index",
                    before, after, 0.001)
            }
        } finally {
            modelFile.delete()
        }
    }

    private fun runModelMenuBooleanRoundTrip(
        operationLabel: Int,
        fileName: String,
        expectedVolumeRatio: Double,
        expectedBoundsWidthRatio: Double? = null,
        configure: (Model, GLRenderer, Double) -> Unit,
    ) {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val modelFile = File(activity.cacheDir, fileName)
        val projectFile = File(activity.cacheDir, fileName.removeSuffix(".stl") + ".3mf")
        val gcodeFile = File(activity.cacheDir, fileName.removeSuffix(".stl") + ".gcode")
        val previousSlots = Prefs.getStoredFilamentSlots().map { FilamentSlot(it.color, it.type) }
        val previousConfig = SliceBeam.CONFIG
        val previousInstance = SliceBeam.INSTANCE
        SliceBeam.INSTANCE = activity.applicationContext as SliceBeam
        Prefs.init(activity.applicationContext as SliceBeam)
        val configFile = SliceBeam.getCurrentConfigFile()
        val previousConfigFile = configFile.takeIf { it.isFile }?.readBytes()
        val prefs = requireNotNull(Prefs.getPrefs())
        val snapshotKeys = listOf("mmu_snapshot_printer", "mmu_snapshot")
        val previousSnapshots = snapshotKeys.associateWith { prefs.getString(it, null) }
        var roundTrippedModel: Model? = null
        var sliceResult: GCodeProcessorResult? = null
        try {
            Prefs.setStoredFilamentSlots(listOf(
                FilamentSlot(0xFF303030.toInt(), "Boolean target T0"),
                FilamentSlot(0xFF3366CC.toInt(), "Boolean cutter T1"),
                FilamentSlot(0xFFF2F2F2.toInt(), "Boolean cutter T2"),
            ))
            TemporaryProjectSettings.clear()
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            val model = loadSingleTestModel(bed, modelFile)
            val sourceVolume = AtomicReference<Double>()
            val sourceWidth = AtomicReference<Double>()
            val prepared = CountDownLatch(1)
            glView.queueEvent {
                val renderer = glView.renderer
                val min = Vec3d(); val max = Vec3d()
                model.getBoundingBoxExact(0, min, max)
                val width = max.x - min.x
                sourceVolume.set(model.getObjectVolume(0))
                sourceWidth.set(width)
                model.addObject(model, 0)
                model.addObject(model, 0)
                model.setUniformFilament(0, 1)
                model.setUniformFilament(1, 2)
                model.setUniformFilament(2, 3)
                configure(model, renderer, width)
                renderer.selectAllObjects()
                glView.requestRender()
                prepared.countDown()
            }
            assertTrue("Boolean sources should be prepared on the GL thread", prepared.await(10, TimeUnit.SECONDS))
            compose.runOnUiThread { bed.updateModel() }
            instrumentationWaitForGlIdle()

            openModelMenu(activity)
            selectSingleObjectScope()
            revealMenuAction(activity, R.string.ModelMenuBoolean)
            compose.onNodeWithContentDescription(activity.getString(R.string.ModelMenuBoolean))
                .assertIsDisplayed().assertIsEnabled().performClick()
            val operationTag = when (operationLabel) {
                R.string.ModelBooleanUnion -> "model-boolean-union"
                R.string.ModelBooleanDifference -> "model-boolean-difference"
                R.string.ModelBooleanIntersection -> "model-boolean-intersection"
                else -> error("Unsupported Boolean operation label: $operationLabel")
            }
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithTag(operationTag, useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag(operationTag, useUnmergedTree = true)
                .performScrollTo().assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 30_000) { readObjectCountOnGl(glView) == 1 }

            fun volumeOnGl(): Double {
                val value = AtomicReference<Double>()
                val finished = CountDownLatch(1)
                glView.queueEvent {
                    value.set(requireNotNull(glView.renderer.model).getObjectVolume(0))
                    finished.countDown()
                }
                assertTrue("Boolean result volume should be read on the GL thread", finished.await(10, TimeUnit.SECONDS))
                return requireNotNull(value.get())
            }

            assertEquals("the selected Boolean action should produce the expected solid volume",
                expectedVolumeRatio, volumeOnGl() / requireNotNull(sourceVolume.get()), 0.025)
            assertArrayEquals("Boolean output should keep the target material and discard cutter assignments",
                intArrayOf(1), readUsedFilamentsOnGl(glView, 0))
            compose.runOnUiThread { bed.undoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) { readObjectCountOnGl(glView) == 3 }
            assertEquals("Undo should restore all Boolean source objects", 3, readObjectCountOnGl(glView))
            compose.runOnUiThread { bed.redoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) { readObjectCountOnGl(glView) == 1 }
            assertEquals("Redo should retain the chosen Boolean solid volume", expectedVolumeRatio,
                volumeOnGl() / requireNotNull(sourceVolume.get()), 0.025)
            assertArrayEquals("Redo should keep the target material assignment", intArrayOf(1),
                readUsedFilamentsOnGl(glView, 0))
            expectedBoundsWidthRatio?.let { expectedRatio ->
                val resultBounds = readExactBoundsOnGl(glView, 0).single()
                assertEquals("Boolean union should include all three selected solids",
                    expectedRatio * requireNotNull(sourceWidth.get()), resultBounds[3] - resultBounds[0], 0.03)
            }

            val exportReady = CountDownLatch(1)
            val exportFailure = AtomicReference<Throwable?>()
            glView.queueEvent {
                try {
                    val resultModel = requireNotNull(glView.renderer.model)
                    activity.assets.open("orca_profiles/Anycubic.ini").use {
                        SliceBeam.CONFIG = Slic3rConfigWrapper(it)
                    }
                    SliceBeam.requireConfig().presets = ConfigObject("presets").apply {
                        put("printer", "Anycubic Kobra 3 0.4 nozzle")
                        put("print", "0.16mm Standard")
                        put("filament", "Anycubic Generic PLA")
                    }
                    val clearSnapshots = prefs.edit()
                    snapshotKeys.forEach(clearSnapshots::remove)
                    check(clearSnapshots.commit())
                    SliceBeam.genCurrentConfig(resultModel)
                    resultModel.export3mf(configFile.absolutePath, projectFile.absolutePath)
                } catch (error: Throwable) {
                    exportFailure.set(error)
                } finally {
                    exportReady.countDown()
                }
            }
            assertTrue("Boolean result should export after the UI action", exportReady.await(60, TimeUnit.SECONDS))
            exportFailure.get()?.let { throw AssertionError("Boolean result 3MF export failed", it) }
            assertTrue("Boolean result should create a non-empty 3MF", projectFile.isFile && projectFile.length() > 0L)
            roundTrippedModel = Model(projectFile, 1)
            assertEquals("Boolean result should remain one object after reload", 1, roundTrippedModel!!.objectsCount)
            assertArrayEquals("3MF reload should keep only the target filament", intArrayOf(1),
                roundTrippedModel!!.getUsedFilaments(0))
            SliceBeam.genCurrentConfig(roundTrippedModel!!)
            sliceResult = roundTrippedModel!!.slice(configFile.absolutePath, gcodeFile.absolutePath, null)
            assertTrue("reloaded Boolean result should produce G-code", gcodeFile.isFile && gcodeFile.length() > 0L)
            val gcode = gcodeFile.readText()
            assertTrue("reloaded Boolean result should contain deposited extrusion",
                Regex("(?m)^G1 .*\\bE[0-9.-]+").containsMatchIn(gcode))
            val emittedTools = Regex("(?m)^T(\\d+)(?:\\s|$)").findAll(gcode)
                .map { it.groupValues[1].toInt() }.toSet()
            // Orca may omit an explicit T0 when only the default first tool remains active.
            // The 3MF assertion above proves that tool; any explicit selection must still be T0.
            assertTrue("Boolean output may select only target T0; found $emittedTools",
                emittedTools.all { it == 0 })
            assertFalse("removed cutter T1 must not be used by the Boolean result", 1 in emittedTools)
            assertFalse("removed cutter T2 must not be used by the Boolean result", 2 in emittedTools)
        } finally {
            sliceResult?.release()
            roundTrippedModel?.release()
            gcodeFile.delete()
            projectFile.delete()
            val restoreSnapshots = prefs.edit()
            previousSnapshots.forEach { (key, value) ->
                if (value == null) restoreSnapshots.remove(key) else restoreSnapshots.putString(key, value)
            }
            restoreSnapshots.commit()
            SliceBeam.CONFIG = previousConfig
            SliceBeam.INSTANCE = previousInstance
            if (previousConfigFile == null) configFile.delete() else configFile.writeBytes(previousConfigFile)
            TemporaryProjectSettings.clear()
            Prefs.setStoredFilamentSlots(previousSlots)
            modelFile.delete()
        }
    }

    @Test
    fun booleanDialogRejectsAStaleObjectSelectionWithoutChangingTheModel() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val modelFile = File(activity.cacheDir, "boolean-stale-selection-test.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            val model = loadSingleTestModel(bed, modelFile)
            val prepared = CountDownLatch(1)
            glView.queueEvent {
                val current = requireNotNull(glView.renderer.model)
                current.addObject(current, 0)
                current.translate(1, 12.0, 0.0, 0.0)
                glView.renderer.setSelectedObjects(intArrayOf(0, 1))
                glView.requestRender()
                prepared.countDown()
            }
            assertTrue("Boolean fixtures should be prepared on the GL thread", prepared.await(10, TimeUnit.SECONDS))
            compose.runOnUiThread { bed.updateModel() }
            instrumentationWaitForGlIdle()
            val beforeBounds = readExactBoundsOnGl(glView, 0, 1)
            val beforeRevision = model.geometryRevision

            openModelMenu(activity)
            selectSingleObjectScope()
            revealMenuAction(activity, R.string.ModelMenuBoolean)
            compose.onNodeWithContentDescription(activity.getString(R.string.ModelMenuBoolean))
                .performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
            compose.onNodeWithText(activity.getString(R.string.ModelBooleanUnion)).assertIsDisplayed()

            val changedSelection = CountDownLatch(1)
            glView.queueEvent {
                glView.renderer.setSelectedObject(1)
                glView.requestRender()
                changedSelection.countDown()
            }
            assertTrue("selection change should complete on the GL thread", changedSelection.await(10, TimeUnit.SECONDS))
            compose.onNodeWithText(activity.getString(R.string.ModelBooleanUnion)).performClick()
            compose.waitUntil(timeoutMillis = 10_000) { hasSnackbarTitle(R.string.ModelBooleanSelectionChanged) }

            assertEquals("a stale Boolean dialog must not replace either source object", 2,
                readObjectCountOnGl(glView))
            assertEquals("stale selection rejection must not mutate native geometry", beforeRevision,
                model.geometryRevision)
            beforeBounds.zip(readExactBoundsOnGl(glView, 0, 1)).forEachIndexed { index, (before, after) ->
                assertArrayEquals("stale Boolean selection must preserve object $index", before, after, 0.001)
            }
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun booleanDialogCancelAndAndroidBackPreserveModelGeometryMaterialsAndSelection() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val modelFile = File(activity.cacheDir, "boolean-cancel-preserves-model-test.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            val model = loadSingleTestModel(bed, modelFile)
            val prepared = CountDownLatch(1)
            glView.queueEvent {
                val current = requireNotNull(glView.renderer.model)
                current.addObject(current, 0)
                current.translate(1, 12.0, 4.0, 3.0)
                current.setUniformFilament(0, 2)
                current.setUniformFilament(1, 3)
                glView.renderer.setSelectedObjects(intArrayOf(0, 1))
                glView.requestRender()
                prepared.countDown()
            }
            assertTrue("Boolean sources should be prepared on the GL thread", prepared.await(10, TimeUnit.SECONDS))
            compose.runOnUiThread { bed.updateModel() }
            instrumentationWaitForGlIdle()

            val beforeBounds = readExactBoundsOnGl(glView, 0, 1)
            val beforeFilaments = (0..1).map { readUsedFilamentsOnGl(glView, it).toList() }
            val beforeRevision = model.geometryRevision
            val selectedIndices = intArrayOf(0, 1)
            val cancelLabel = activity.getString(android.R.string.cancel)
            val unionLabel = activity.getString(R.string.ModelBooleanUnion)

            fun openBooleanDialog() {
                openModelMenu(activity)
                selectSingleObjectScope()
                revealMenuAction(activity, R.string.ModelMenuBoolean)
                compose.onNodeWithContentDescription(activity.getString(R.string.ModelMenuBoolean))
                    .assertIsDisplayed().assertIsEnabled().performClick()
                compose.onNodeWithText(unionLabel).assertIsDisplayed()
            }

            fun assertSourcesUnchanged() {
                assertEquals("cancel must retain both source objects", 2, readObjectCountOnGl(glView))
                assertEquals("cancel must not mutate model geometry", beforeRevision, model.geometryRevision)
                beforeBounds.zip(readExactBoundsOnGl(glView, 0, 1)).forEachIndexed { index, (before, after) ->
                    assertArrayEquals("cancel must preserve object $index bounds", before, after, 0.001)
                }
                assertEquals("cancel must retain source material assignments", beforeFilaments,
                    (0..1).map { readUsedFilamentsOnGl(glView, it).toList() })
                assertArrayEquals("cancel must retain the source selection", selectedIndices,
                    readSelectedIndicesOnGl(glView))
            }

            openBooleanDialog()
            compose.onNodeWithText(cancelLabel).assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodesWithText(unionLabel).fetchSemanticsNodes().isEmpty()
            }
            assertSourcesUnchanged()

            openBooleanDialog()
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodesWithText(unionLabel).fetchSemanticsNodes().isEmpty()
            }
            assertSourcesUnchanged()
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun objectListAssignmentAndDuplicateDialogsRejectStaleSelections() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val modelFile = File(activity.cacheDir, "object-actions-stale-selection-test.stl")
        val previousSlots = Prefs.getStoredFilamentSlots().map { FilamentSlot(it.color, it.type) }
        val testSlots = listOf(
            FilamentSlot(0xFFCC3333.toInt(), "Stale Guard PLA"),
            FilamentSlot(0xFF3366CC.toInt(), "Stale Guard PETG"),
        )
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            val model = loadSingleTestModel(bed, modelFile)
            val prepared = CountDownLatch(1)
            glView.queueEvent {
                model.addObject(model, 0)
                model.translate(1, 16.0, 0.0, 0.0)
                glView.renderer.setSelectedObjects(intArrayOf(0, 1))
                glView.requestRender()
                prepared.countDown()
            }
            assertTrue("object action fixtures should be prepared on the GL thread", prepared.await(10, TimeUnit.SECONDS))
            compose.runOnUiThread { bed.updateModel() }
            Prefs.setStoredFilamentSlots(testSlots)
            val actions = FileMenuObjectActions(bed)

            // Selecting a row from an object-list snapshot must not overwrite a newer selection.
            compose.runOnUiThread { actions.showObjectsDialog(glView) }
            compose.onNodeWithTag("model-object-row-0").assertIsDisplayed()
            val selectedOne = CountDownLatch(1)
            glView.queueEvent {
                glView.renderer.setSelectedObject(1)
                selectedOne.countDown()
            }
            assertTrue("object selection change should finish on GL", selectedOne.await(10, TimeUnit.SECONDS))
            compose.onNodeWithTag("model-object-row-0").performClick()
            compose.waitUntil(timeoutMillis = 10_000) { hasSnackbarTitle(R.string.ModelSelectionChanged) }
            assertEquals("stale object-list selection must preserve the newer selection", 1, readSelectedObjectOnGl(glView))

            // Assigning an individual object's filament is also bound to the opening selection.
            clearWorkspaceSnackbars(bed)
            compose.runOnUiThread { actions.showObjectsDialog(glView) }
            compose.onAllNodesWithText(activity.getString(R.string.ObjectActionAssignFilamentShort))[0]
                .assertIsDisplayed().performClick()
            compose.onNodeWithTag("model-choice-option-0").assertIsDisplayed()
            val selectedZero = CountDownLatch(1)
            glView.queueEvent {
                glView.renderer.setSelectedObject(0)
                selectedZero.countDown()
            }
            assertTrue("second object selection change should finish on GL", selectedZero.await(10, TimeUnit.SECONDS))
            compose.onNodeWithTag("model-choice-option-0").performClick()
            compose.waitUntil(timeoutMillis = 10_000) { hasSnackbarTitle(R.string.ModelSelectionChanged) }
            assertTrue("stale filament assignment must leave object 1 unchanged",
                model.getUsedFilaments(0).contentEquals(intArrayOf(1)))

            // A delayed duplicate confirmation must never duplicate the replacement selection.
            clearWorkspaceSnackbars(bed)
            compose.runOnUiThread { actions.showDuplicateDialog(glView) }
            compose.onNodeWithTag("duplicate-model-confirm").assertIsDisplayed()
            val selectedForDuplicate = CountDownLatch(1)
            glView.queueEvent {
                glView.renderer.setSelectedObject(1)
                selectedForDuplicate.countDown()
            }
            assertTrue("duplicate selection change should finish on GL", selectedForDuplicate.await(10, TimeUnit.SECONDS))
            compose.onNodeWithTag("duplicate-model-confirm").performClick()
            compose.waitUntil(timeoutMillis = 10_000) { hasSnackbarTitle(R.string.ModelSelectionChanged) }
            assertEquals("stale duplicate confirmation must not add objects", 2, readObjectCountOnGl(glView))
            assertEquals("stale duplicate confirmation must preserve newer selection", 1, readSelectedObjectOnGl(glView))
        } finally {
            Prefs.setStoredFilamentSlots(previousSlots)
            modelFile.delete()
        }
    }

    @Test
    fun objectListAndDuplicateConfirmationStillApplyToTheirCapturedSelection() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val modelFile = File(activity.cacheDir, "object-actions-current-selection-test.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            loadSingleTestModel(bed, modelFile)
            val selectInitialObject = CountDownLatch(1)
            glView.queueEvent {
                glView.renderer.setSelectedObject(0)
                selectInitialObject.countDown()
            }
            assertTrue("initial object selection should finish on GL", selectInitialObject.await(10, TimeUnit.SECONDS))
            val actions = FileMenuObjectActions(bed)

            compose.runOnUiThread { actions.showObjectsDialog(glView) }
            compose.onNodeWithTag("model-object-row-0").assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 10_000) { readSelectedObjectOnGl(glView) == 0 }

            compose.runOnUiThread { actions.showDuplicateDialog(glView) }
            compose.onNodeWithTag("duplicate-model-confirm").assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 30_000) { readObjectCountOnGl(glView) == 2 }
            assertEquals("the current confirmed selection should be duplicated once", 2, readObjectCountOnGl(glView))
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun multipartObjectListShowsPartsAndCanSeparateOneForIndependentEditing() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val modelFile = File(activity.cacheDir, "object-list-multipart-parts-test.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            val model = loadSingleTestModel(bed, modelFile)
            val prepared = CountDownLatch(1)
            glView.queueEvent {
                model.addObject(model, 0)
                model.translate(1, 24.0, 0.0, 0.0)
                model.setUniformFilament(0, 1)
                model.setUniformFilament(1, 2)
                assertEquals(0, model.combineObjectsAsParts(intArrayOf(0, 1)))
                assertEquals("combined assembly should expose both mesh parts", 2, model.getObjectPartCount(0))
                glView.renderer.setSelectedObject(0)
                glView.requestRender()
                prepared.countDown()
            }
            assertTrue("multipart test model should be prepared on GL", prepared.await(10, TimeUnit.SECONDS))
            compose.runOnUiThread { bed.updateModel() }
            instrumentationWaitForGlIdle()

            compose.runOnUiThread { FileMenuObjectActions(bed).showObjectsDialog(glView) }
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithTag("model-object-row-0").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("model-object-row-0").assertIsDisplayed()
            compose.onNodeWithTag("model-objects-multipart-help").assertIsDisplayed()
            compose.onNodeWithTag("model-object-part-row-0-0").assertIsDisplayed()
            compose.onNodeWithTag("model-object-part-row-0-1").assertIsDisplayed()
            compose.onNodeWithTag("model-object-edit-part-0-1").performClick()
            compose.waitUntil(timeoutMillis = 15_000) {
                readObjectCountOnGl(glView) == 2 && readSelectedObjectOnGl(glView) == 1
            }
            assertEquals("separating a child should leave both parts editable as separate model objects", 2,
                readObjectCountOnGl(glView))
            assertEquals("part selection must preserve the part's assigned material", listOf(1),
                readUsedFilamentsOnGl(glView, 0).toList())
            assertEquals("the selected child must preserve its own material", listOf(2),
                readUsedFilamentsOnGl(glView, 1).toList())

            // Once detached, the chosen part is the active standalone object and the
            // regular transform menu edits its native geometry independently.
            val originalScale = readScaleOnGl(glView, 1)
            openSelectedObjectMenu(activity)
            revealMenuAction(activity, R.string.MenuTransformScale)
            compose.onNodeWithContentDescription(activity.getString(R.string.MenuTransformScale))
                .performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithContentDescription("X, X =", substring = true).assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodesWithText(activity.getString(R.string.ScaleManualPercent))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            val scaleField = compose.onNodeWithText(activity.getString(R.string.ScaleManualPercent))
            scaleField.performTextClearance()
            scaleField.performTextInput("125")
            compose.onNodeWithText(activity.getString(android.R.string.ok)).performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                kotlin.math.abs(readScaleOnGl(glView, 1).x - originalScale.x * 1.25) < 0.002
            }
            assertEquals("scaling a detached part must leave the sibling part unchanged", 1.0,
                readScaleOnGl(glView, 0).x, 0.002)
            compose.runOnUiThread { bed.undoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) {
                kotlin.math.abs(readScaleOnGl(glView, 1).x - originalScale.x) < 0.002
            }
            compose.runOnUiThread { bed.redoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) {
                kotlin.math.abs(readScaleOnGl(glView, 1).x - originalScale.x * 1.25) < 0.002
            }
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun objectListShowsOriginalNamesForSplitStepBodies() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val stepFile = File(activity.cacheDir, "named-step-object-list-${System.nanoTime()}.step")
        try {
            InstrumentationRegistry.getInstrumentation().context.assets
                .open("named_compound_bodies.step").use { input ->
                    stepFile.outputStream().use(input::copyTo)
                }
            val loaded = CountDownLatch(1)
            val failure = AtomicReference<Exception?>()
            bed.loadModel(stepFile, false, 1, object : BedFragment.ModelLoadCallback {
                override fun onLoaded(loadedModel: Model, firstNewObject: Int, addedObjects: Int) {
                    loaded.countDown()
                }

                override fun onFailed(error: Exception) {
                    failure.set(error)
                    loaded.countDown()
                }
            }, StepImportOptions(0.003, 0.5, splitCompound = true))
            assertTrue("named STEP import should finish", loaded.await(90, TimeUnit.SECONDS))
            failure.get()?.let { throw AssertionError("named STEP import failed", it) }
            instrumentationWaitForGlIdle()

            compose.runOnUiThread { FileMenuObjectActions(bed).showObjectsDialog(glView) }
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithTag("model-object-row-0").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("model-object-group-toggle-0").assertIsDisplayed()
            val objectRows = compose.onNodeWithTag("model-objects-dialog-rows")
            objectRows.performScrollToNode(hasTestTag("model-object-part-row-0-0"))
            compose.onNodeWithTag("model-object-part-row-0-0").assertIsDisplayed()
            objectRows.performScrollToNode(hasText("Part10"))
            compose.onNodeWithText("Part10").assertIsDisplayed()
            objectRows.performScrollToNode(hasTestTag("model-object-part-row-0-1"))
            compose.onNodeWithTag("model-object-part-row-0-1").assertIsDisplayed()
            objectRows.performScrollToNode(hasText("Part2"))
            compose.onNodeWithText("Part2").assertIsDisplayed()
            objectRows.performScrollToNode(hasTestTag("model-object-part-row-0-2"))
            compose.onNodeWithTag("model-object-part-row-0-2").assertIsDisplayed()
            objectRows.performScrollToNode(hasText("Тело & shell"))
            compose.onNodeWithText("Тело & shell").assertIsDisplayed()
        } finally {
            stepFile.delete()
        }
    }

    @Test
    fun groupedPartTransformFromObjectListKeepsAssemblyAndSupportsUndoRedo() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val modelFile = File(activity.cacheDir, "object-list-grouped-part-transform-test.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            val model = loadSingleTestModel(bed, modelFile)
            val prepared = CountDownLatch(1)
            glView.queueEvent {
                model.addObject(model, 0)
                model.translate(1, 24.0, 0.0, 0.0)
                model.setUniformFilament(0, 1)
                model.setUniformFilament(1, 2)
                assertEquals(0, model.combineObjectsAsParts(intArrayOf(0, 1)))
                glView.renderer.setSelectedObject(0)
                glView.requestRender()
                prepared.countDown()
            }
            assertTrue("grouped transform test model should be prepared on GL", prepared.await(10, TimeUnit.SECONDS))
            compose.runOnUiThread { bed.updateModel() }
            instrumentationWaitForGlIdle()

            compose.runOnUiThread { FileMenuObjectActions(bed).showObjectsDialog(glView) }
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithTag("model-object-part-row-0-1").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("model-object-part-row-0-1").assertIsDisplayed()
            val boundsBefore = readExactBoundsOnGl(glView, 0).single()
            compose.onNodeWithTag("model-object-transform-part-0-1").performScrollTo().performClick()
            compose.onNodeWithTag("model-object-part-transform-field-0").performTextClearance()
            compose.onNodeWithTag("model-object-part-transform-field-0").performTextInput("5")
            compose.onNodeWithTag("model-object-part-transform-field-5").performTextClearance()
            compose.onNodeWithTag("model-object-part-transform-field-5").performTextInput("90")
            compose.onNodeWithTag("model-object-part-transform-field-6").performTextClearance()
            compose.onNodeWithTag("model-object-part-transform-field-6").performTextInput("150")
            compose.onNodeWithTag("model-object-part-transform-apply").performClick()

            compose.waitUntil(timeoutMillis = 15_000) {
                !readExactBoundsOnGl(glView, 0).single().contentEquals(boundsBefore)
            }
            val boundsAfter = readExactBoundsOnGl(glView, 0).single()
            assertEquals("part rotation and scale must preserve both assembly parts", 2,
                readPartCountOnGl(glView, 0))
            assertEquals("the grouped transform must preserve each part's filament", listOf(1, 2),
                readUsedFilamentsOnGl(glView, 0).toList())

            compose.runOnUiThread { bed.undoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) {
                readExactBoundsOnGl(glView, 0).single().contentEquals(boundsBefore)
            }
            compose.runOnUiThread { bed.redoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) {
                readExactBoundsOnGl(glView, 0).single().contentEquals(boundsAfter)
            }
            assertEquals("redo must preserve the multipart assembly", 2, readPartCountOnGl(glView, 0))
            assertEquals("redo must preserve per-part filament assignments", listOf(1, 2),
                readUsedFilamentsOnGl(glView, 0).toList())
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun selectedObjectActionDialogRejectsStaleSplitAndDelete() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val modelFile = File(activity.cacheDir, "selected-object-actions-stale-test.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            val model = loadSingleTestModel(bed, modelFile)
            val prepared = CountDownLatch(1)
            glView.queueEvent {
                model.addObject(model, 0)
                model.translate(1, 18.0, 0.0, 0.0)
                glView.renderer.setSelectedObject(0)
                glView.requestRender()
                prepared.countDown()
            }
            assertTrue("two selected-action test objects should be prepared on GL", prepared.await(10, TimeUnit.SECONDS))
            compose.runOnUiThread { bed.updateModel() }
            val actions = FileMenuObjectActions(bed)

            compose.runOnUiThread { actions.showSelectedObjectActions(glView) }
            compose.onNodeWithTag("model-choice-option-2").assertIsDisplayed()
            val changedForSplit = CountDownLatch(1)
            glView.queueEvent {
                glView.renderer.setSelectedObject(1)
                changedForSplit.countDown()
            }
            assertTrue("selection change before Split must complete on GL", changedForSplit.await(10, TimeUnit.SECONDS))
            compose.onNodeWithTag("model-choice-option-2").performClick()
            compose.waitUntil(timeoutMillis = 10_000) { hasSnackbarTitle(R.string.ModelSelectionChanged) }
            assertEquals("stale Split must keep both objects", 2, readObjectCountOnGl(glView))
            assertEquals("stale Split must keep the replacement selection", 1, readSelectedObjectOnGl(glView))

            clearWorkspaceSnackbars(bed)
            compose.runOnUiThread { actions.showSelectedObjectActions(glView) }
            compose.onNodeWithTag("model-choice-option-3").assertIsDisplayed()
            val changedForDelete = CountDownLatch(1)
            glView.queueEvent {
                glView.renderer.setSelectedObject(0)
                changedForDelete.countDown()
            }
            assertTrue("selection change before Delete must complete on GL", changedForDelete.await(10, TimeUnit.SECONDS))
            compose.onNodeWithTag("model-choice-option-3").performClick()
            compose.waitUntil(timeoutMillis = 10_000) { hasSnackbarTitle(R.string.ModelSelectionChanged) }
            assertEquals("stale Delete must keep both objects", 2, readObjectCountOnGl(glView))
            assertEquals("stale Delete must keep the replacement selection", 0, readSelectedObjectOnGl(glView))

            clearWorkspaceSnackbars(bed)
            compose.runOnUiThread { actions.showSelectedObjectActions(glView) }
            compose.onNodeWithTag("model-choice-option-3").performClick()
            compose.waitUntil(timeoutMillis = 10_000) { readObjectCountOnGl(glView) == 1 }
            assertEquals("confirmed Delete should remove the captured object", 1, readObjectCountOnGl(glView))
            assertEquals("confirmed Delete should clear the selected object", -1, readSelectedObjectOnGl(glView))
            compose.runOnUiThread { bed.undoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) { readObjectCountOnGl(glView) == 2 }
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun selectedObjectActionMoveRejectsStaleSelectionBeforeChangingPlate() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val modelFile = File(activity.cacheDir, "selected-object-actions-stale-move-test.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            val model = loadSingleTestModel(bed, modelFile)
            val prepared = CountDownLatch(1)
            glView.queueEvent {
                model.addObject(model, 0)
                model.translate(1, 18.0, 0.0, 0.0)
                glView.renderer.setSelectedObject(0)
                glView.requestRender()
                prepared.countDown()
            }
            assertTrue("two selected-action test objects should be prepared on GL", prepared.await(10, TimeUnit.SECONDS))
            compose.runOnUiThread { bed.updateModel() }

            compose.onNodeWithContentDescription(activity.getString(R.string.PlateNumber, "1"))
                .assertIsDisplayed().performClick()
            compose.onNodeWithContentDescription(activity.getString(R.string.MenuAddPlate))
                .assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 10_000) { bed.plateCount == 2 && bed.currentPlateIndex == 1 }
            compose.onNodeWithContentDescription(activity.getString(R.string.PlateNumber, "2"))
                .assertIsDisplayed().performClick()
            compose.onNodeWithContentDescription(
                activity.getString(R.string.PlatePickerSelectDescription, "1"),
            ).assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 10_000) { bed.currentPlateIndex == 0 && readObjectCountOnGl(glView) == 2 }
            glView.selectModel(0, Runnable {})
            instrumentationWaitForGlIdle()

            compose.runOnUiThread { FileMenuObjectActions(bed).showSelectedObjectActions(glView) }
            compose.onNodeWithTag("model-choice-option-1").assertIsDisplayed().performClick()
            compose.onNodeWithText(activity.getString(R.string.PlateNumber, "2")).assertIsDisplayed()

            val changedSelection = CountDownLatch(1)
            glView.queueEvent {
                glView.renderer.setSelectedObject(1)
                changedSelection.countDown()
            }
            assertTrue("selection change before plate confirmation must complete on GL", changedSelection.await(10, TimeUnit.SECONDS))
            compose.onNodeWithText(activity.getString(R.string.PlateNumber, "2")).performClick()
            compose.waitUntil(timeoutMillis = 10_000) { hasSnackbarTitle(R.string.ModelSelectionChanged) }
            assertEquals("stale move must stay on the source plate", 0, bed.currentPlateIndex)
            assertEquals("stale move must preserve both source objects", 2, readObjectCountOnGl(glView))

            compose.onNodeWithContentDescription(activity.getString(R.string.PlateNumber, "1"))
                .assertIsDisplayed().performClick()
            compose.onNodeWithContentDescription(
                activity.getString(R.string.PlatePickerSelectDescription, "2"),
            ).assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 10_000) { bed.currentPlateIndex == 1 }
            assertEquals("stale move must leave target plate empty", 0, readObjectCountOnGl(glView))
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun placeOnObjectDialogRejectsChangedSelectionWithoutMovingOrCombiningObjects() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val modelFile = File(activity.cacheDir, "place-object-stale-selection-test.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            val model = loadSingleTestModel(bed, modelFile)
            val prepared = CountDownLatch(1)
            glView.queueEvent {
                model.addObject(model, 0)
                model.translate(1, 18.0, 0.0, 0.0)
                glView.renderer.setSelectedObject(1)
                glView.requestRender()
                prepared.countDown()
            }
            assertTrue("placement objects should be prepared on GL", prepared.await(10, TimeUnit.SECONDS))
            compose.runOnUiThread { bed.updateModel() }
            instrumentationWaitForGlIdle()
            val boundsBefore = readExactBoundsOnGl(glView, 0, 1)
            val revisionBefore = model.geometryRevision

            openModelMenu(activity)
            selectSingleObjectScope()
            revealMenuAction(activity, R.string.ModelMenuPlaceOnObject)
            compose.onNodeWithContentDescription(activity.getString(R.string.ModelMenuPlaceOnObject))
                .assertIsDisplayed().assertIsEnabled().performClick()
            compose.onNodeWithTag("model-choice-option-0").assertIsDisplayed()

            val changedSelection = CountDownLatch(1)
            glView.queueEvent {
                glView.renderer.setSelectedObject(0)
                changedSelection.countDown()
            }
            assertTrue("selection change should complete on GL", changedSelection.await(10, TimeUnit.SECONDS))
            compose.onNodeWithTag("model-choice-option-0").performClick()
            compose.waitUntil(timeoutMillis = 10_000) { hasSnackbarTitle(R.string.ModelSelectionChanged) }

            assertEquals("stale placement must keep both source objects", 2, readObjectCountOnGl(glView))
            assertEquals("stale placement must not mutate geometry", revisionBefore, model.geometryRevision)
            boundsBefore.zip(readExactBoundsOnGl(glView, 0, 1)).forEachIndexed { index, (before, after) ->
                assertArrayEquals("object $index must stay at its original bounds", before, after, 0.001)
            }
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun placeOnObjectDialogStacksSelectedObjectWithoutJoiningTarget() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val modelFile = File(activity.cacheDir, "place-object-dialog-success-test.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            val model = loadSingleTestModel(bed, modelFile)
            val targetVolume = AtomicReference<Double>()
            val targetBounds = AtomicReference<DoubleArray>()
            val prepared = CountDownLatch(1)
            glView.queueEvent {
                model.addObject(model, 0)
                model.translate(1, 35.0, 18.0, 24.0)
                model.setUniformFilament(0, 1)
                model.setUniformFilament(1, 2)
                val min = Vec3d(); val max = Vec3d()
                model.getBoundingBoxExact(0, min, max)
                targetVolume.set(model.getObjectVolume(0))
                targetBounds.set(doubleArrayOf(min.x, min.y, min.z, max.x, max.y, max.z))
                glView.renderer.setSelectedObject(1)
                glView.requestRender()
                prepared.countDown()
            }
            assertTrue("placement fixtures should be prepared on the GL thread",
                prepared.await(10, TimeUnit.SECONDS))
            compose.runOnUiThread { bed.updateModel() }
            instrumentationWaitForGlIdle()

            openModelMenu(activity)
            selectSingleObjectScope()
            revealMenuAction(activity, R.string.ModelMenuPlaceOnObject)
            compose.onNodeWithContentDescription(activity.getString(R.string.ModelMenuPlaceOnObject))
                .assertIsDisplayed().assertIsEnabled().performClick()
            compose.onNodeWithTag("model-choice-option-0").assertIsDisplayed().performClick()

            compose.waitUntil(timeoutMillis = 30_000) { readObjectCountOnGl(glView) == 2 }
            assertEquals("target remains a separate printable solid", requireNotNull(targetVolume.get()),
                readObjectVolumeOnGl(glView, 0), targetVolume.get()!! * 0.002)
            assertEquals("moving model remains a separate printable solid", requireNotNull(targetVolume.get()),
                readObjectVolumeOnGl(glView, 1), targetVolume.get()!! * 0.002)
            assertArrayEquals("target material remains independent", intArrayOf(1), readUsedFilamentsOnGl(glView, 0))
            assertArrayEquals("moving material remains independent", intArrayOf(2), readUsedFilamentsOnGl(glView, 1))
            val target = requireNotNull(targetBounds.get())
            val targetAfter = readExactBoundsOnGl(glView, 0).single()
            val moved = readExactBoundsOnGl(glView, 1).single()
            assertArrayEquals("placement must not mutate target geometry", target, targetAfter, 0.02)
            assertEquals("the moving object should be centered on the chosen target in X",
                (target[0] + target[3]) / 2.0, (moved[0] + moved[3]) / 2.0, 0.02)
            assertEquals("the moving object should be centered on the chosen target in Y",
                (target[1] + target[4]) / 2.0, (moved[1] + moved[4]) / 2.0, 0.02)
            assertEquals("the moved model should rest on the target's top surface", target[5], moved[2], 0.02)
            assertTrue("the moved model should extend above the target", moved[5] > target[5])

            compose.runOnUiThread { bed.undoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) { readObjectCountOnGl(glView) == 2 }
            assertArrayEquals("undo should restore the original target bounds", target,
                readExactBoundsOnGl(glView, 0).single(), 0.02)
            compose.runOnUiThread { bed.redoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) { readObjectCountOnGl(glView) == 2 }
            assertEquals("redo should preserve target material", listOf(1), readUsedFilamentsOnGl(glView, 0).toList())
            assertEquals("redo should preserve moved-object material", listOf(2), readUsedFilamentsOnGl(glView, 1).toList())
            // A separately placed object sitting above the bed is intentionally still a
            // distinct object; the slicer correctly rejects it as floating until the user
            // explicitly joins it or enables suitable supports.
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun surfacePlacementCancelsWhenSelectionChangesBeforeThePick() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val modelFile = File(activity.cacheDir, "surface-placement-stale-selection-test.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            val model = loadSingleTestModel(bed, modelFile)
            val prepared = CountDownLatch(1)
            glView.queueEvent {
                model.addObject(model, 0)
                model.translate(1, 24.0, 0.0, 0.0)
                glView.renderer.setSelectedObject(1)
                glView.requestRender()
                prepared.countDown()
            }
            assertTrue("surface-placement objects should be prepared on GL", prepared.await(10, TimeUnit.SECONDS))
            compose.runOnUiThread { bed.updateModel() }
            instrumentationWaitForGlIdle()
            val boundsBefore = readExactBoundsOnGl(glView, 0, 1)
            val revisionBefore = model.geometryRevision

            openModelMenu(activity)
            selectSingleObjectScope()
            revealMenuAction(activity, R.string.ModelMenuPlaceOnSurface)
            compose.onNodeWithContentDescription(activity.getString(R.string.ModelMenuPlaceOnSurface))
                .assertIsEnabled().performClick()
            compose.onNodeWithTag("model-choice-option-0").assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 10_000) { glView.renderer.isSurfacePlacementActive }

            val changedSelection = CountDownLatch(1)
            glView.queueEvent {
                glView.renderer.setSelectedObject(0)
                glView.renderer.onClick(100f, 100f)
                glView.requestRender()
                changedSelection.countDown()
            }
            assertTrue("stale surface-pick should be rejected on GL", changedSelection.await(10, TimeUnit.SECONDS))
            compose.waitUntil(timeoutMillis = 10_000) { hasSnackbarTitle(R.string.ModelSelectionChanged) }

            assertFalse("stale surface placement must leave pick mode", glView.renderer.isSurfacePlacementActive)
            assertEquals("stale surface placement must preserve source objects", 2, readObjectCountOnGl(glView))
            assertEquals("stale surface placement must not mutate geometry", revisionBefore, model.geometryRevision)
            boundsBefore.zip(readExactBoundsOnGl(glView, 0, 1)).forEachIndexed { index, (before, after) ->
                assertArrayEquals("object $index must stay at its original bounds", before, after, 0.001)
            }
        } finally {
            if (glView.renderer.isSurfacePlacementActive) bed.onBackPressed()
            modelFile.delete()
        }
    }

    @Test
    fun alignmentAndDistributionDialogsRejectStaleModelSelections() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val modelFile = File(activity.cacheDir, "group-dialog-stale-selection-test.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            val model = loadSingleTestModel(bed, modelFile)
            val prepared = CountDownLatch(1)
            glView.queueEvent {
                model.addObject(model, 0)
                model.addObject(model, 0)
                model.translate(1, 18.0, 0.0, 0.0)
                model.translate(2, 36.0, 0.0, 0.0)
                glView.renderer.setSelectedObjects(intArrayOf(0, 1, 2))
                glView.requestRender()
                prepared.countDown()
            }
            assertTrue("group-action objects should be prepared on GL", prepared.await(10, TimeUnit.SECONDS))
            compose.runOnUiThread { bed.updateModel() }
            instrumentationWaitForGlIdle()

            openModelMenu(activity)
            selectSingleObjectScope()
            revealMenuAction(activity, R.string.ModelMenuAlignSelected)
            compose.onNodeWithContentDescription(activity.getString(R.string.ModelMenuAlignSelected))
                .assertIsEnabled().performClick()
            compose.onNodeWithTag("model-choice-option-0").assertIsDisplayed()
            val alignmentRevision = model.geometryRevision
            val alignmentBounds = readExactBoundsOnGl(glView, 0, 1, 2)

            val changeAlignmentSelection = CountDownLatch(1)
            glView.queueEvent {
                glView.renderer.setSelectedObject(2)
                changeAlignmentSelection.countDown()
            }
            assertTrue("alignment selection change should finish on GL", changeAlignmentSelection.await(10, TimeUnit.SECONDS))
            compose.onNodeWithTag("model-choice-option-0").performClick()
            compose.waitUntil(timeoutMillis = 10_000) { hasSnackbarTitle(R.string.ModelSelectionChanged) }
            assertEquals("stale alignment must not change geometry", alignmentRevision, model.geometryRevision)
            alignmentBounds.zip(readExactBoundsOnGl(glView, 0, 1, 2)).forEachIndexed { index, (before, after) ->
                assertArrayEquals("alignment must preserve object $index", before, after, 0.001)
            }

            val restoreSelection = CountDownLatch(1)
            glView.queueEvent {
                glView.renderer.setSelectedObjects(intArrayOf(0, 1, 2))
                restoreSelection.countDown()
            }
            assertTrue("distribution selection should be restored on GL", restoreSelection.await(10, TimeUnit.SECONDS))
            compose.waitForIdle()
            revealMenuAction(activity, R.string.ModelMenuDistributeSelected)
            compose.onNodeWithContentDescription(activity.getString(R.string.ModelMenuDistributeSelected))
                .assertIsEnabled().performClick()
            compose.onNodeWithTag("model-choice-option-0").assertIsDisplayed()
            val distributionRevision = model.geometryRevision
            val distributionBounds = readExactBoundsOnGl(glView, 0, 1, 2)

            val changeDistributionGeometry = CountDownLatch(1)
            glView.queueEvent {
                model.translate(0, 0.0, 1.0, 0.0)
                changeDistributionGeometry.countDown()
            }
            assertTrue("geometry change should finish on GL", changeDistributionGeometry.await(10, TimeUnit.SECONDS))
            compose.onNodeWithTag("model-choice-option-0").performClick()
            compose.waitUntil(timeoutMillis = 10_000) { hasSnackbarTitle(R.string.ModelSelectionChanged) }
            assertEquals("stale distribution must not add a second geometry change",
                distributionRevision + 1, model.geometryRevision)
            distributionBounds.zip(readExactBoundsOnGl(glView, 0, 1, 2)).forEachIndexed { index, (before, after) ->
                val expected = before.copyOf().also { if (index == 0) it[1] += 1.0; if (index == 0) it[4] += 1.0 }
                assertArrayEquals("distribution must preserve the externally moved object $index", expected, after, 0.001)
            }
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun alignmentAndDistributionDialogsCancelAndBackPreserveTheSelectedGroup() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val modelFile = File(activity.cacheDir, "group-dialog-cancel-back-test.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            val model = loadSingleTestModel(bed, modelFile)
            val prepared = CountDownLatch(1)
            glView.queueEvent {
                model.addObject(model, 0)
                model.addObject(model, 0)
                model.translate(1, 24.0, 0.0, 0.0)
                model.translate(2, 48.0, 0.0, 0.0)
                for (index in 0..2) model.setUniformFilament(index, 2)
                glView.renderer.setSelectedObjects(intArrayOf(0, 1, 2))
                glView.requestRender()
                prepared.countDown()
            }
            assertTrue("group objects should be prepared on GL", prepared.await(10, TimeUnit.SECONDS))
            compose.runOnUiThread { bed.updateModel() }
            instrumentationWaitForGlIdle()

            val initialRevision = model.geometryRevision
            val initialBounds = readExactBoundsOnGl(glView, 0, 1, 2)
            val initialFilaments = (0..2).map { readUsedFilamentsOnGl(glView, it).toList() }
            val initialSelection = intArrayOf(0, 1, 2)
            val cancelLabel = activity.getString(android.R.string.cancel)
            val choiceTag = "model-choice-option-0"

            fun openChoice(action: Int) {
                openModelMenu(activity)
                selectSingleObjectScope()
                revealMenuAction(activity, action)
                compose.onNodeWithContentDescription(activity.getString(action))
                    .assertIsDisplayed().assertIsEnabled().performClick()
                compose.onNodeWithTag(choiceTag).assertIsDisplayed()
            }

            fun assertUnchanged(actionName: String) {
                assertEquals("$actionName must not change object count", 3, readObjectCountOnGl(glView))
                assertEquals("$actionName must not change model geometry", initialRevision, model.geometryRevision)
                initialBounds.zip(readExactBoundsOnGl(glView, 0, 1, 2)).forEachIndexed { index, (before, after) ->
                    assertArrayEquals("$actionName must preserve object $index bounds", before, after, 0.001)
                }
                assertEquals("$actionName must preserve object materials", initialFilaments,
                    (0..2).map { readUsedFilamentsOnGl(glView, it).toList() })
                assertArrayEquals("$actionName must preserve the selected group", initialSelection,
                    readSelectedIndicesOnGl(glView))
            }

            for (action in listOf(R.string.ModelMenuAlignSelected, R.string.ModelMenuDistributeSelected)) {
                val name = activity.getString(action)
                openChoice(action)
                compose.onNodeWithText(cancelLabel).assertIsDisplayed().performClick()
                compose.waitUntil(timeoutMillis = 5_000) {
                    compose.onAllNodesWithTag(choiceTag).fetchSemanticsNodes().isEmpty()
                }
                assertUnchanged("$name cancel button")

                openChoice(action)
                InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                compose.waitUntil(timeoutMillis = 5_000) {
                    compose.onAllNodesWithTag(choiceTag).fetchSemanticsNodes().isEmpty()
                }
                assertUnchanged("$name Android Back")
            }
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun combineConfirmationRejectsChangedSelectionWithoutCombiningObjects() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val modelFile = File(activity.cacheDir, "combine-stale-confirmation-test.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            val model = loadSingleTestModel(bed, modelFile)
            val prepared = CountDownLatch(1)
            glView.queueEvent {
                model.addObject(model, 0)
                model.addObject(model, 0)
                model.translate(1, 15.0, 0.0, 0.0)
                model.translate(2, 30.0, 0.0, 0.0)
                glView.renderer.setSelectedObjects(intArrayOf(0, 1))
                glView.requestRender()
                prepared.countDown()
            }
            assertTrue("combine fixtures should be prepared on GL", prepared.await(10, TimeUnit.SECONDS))
            compose.runOnUiThread { bed.updateModel() }
            instrumentationWaitForGlIdle()
            val revisionBefore = model.geometryRevision
            val boundsBefore = readExactBoundsOnGl(glView, 0, 1, 2)

            openModelMenu(activity)
            selectSingleObjectScope()
            revealMenuAction(activity, R.string.ModelMenuCombineSelected)
            compose.onNodeWithContentDescription(activity.getString(R.string.ModelMenuCombineSelected))
                .performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
            compose.waitUntil(timeoutMillis = 5_000) { bed.isWorkspaceMessageDialogAttached() }

            val changed = CountDownLatch(1)
            glView.selectModels(intArrayOf(0, 2)) { changed.countDown() }
            assertTrue("selection change should finish on GL", changed.await(10, TimeUnit.SECONDS))
            compose.onNode(hasText(activity.getString(R.string.ModelMenuCombineSelected)) and hasClickAction())
                .performClick()
            compose.waitUntil(timeoutMillis = 10_000) { hasSnackbarTitle(R.string.ModelSelectionChanged) }

            assertEquals("stale confirmation must preserve every source object", 3, readObjectCountOnGl(glView))
            assertEquals("stale confirmation must not mutate geometry", revisionBefore, model.geometryRevision)
            boundsBefore.zip(readExactBoundsOnGl(glView, 0, 1, 2)).forEachIndexed { index, (before, after) ->
                assertArrayEquals("stale confirmation must preserve object $index", before, after, 0.001)
            }
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun deleteSelectedConfirmationRejectsChangedSelectionWithoutDeletingObjects() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val modelFile = File(activity.cacheDir, "delete-selection-stale-confirmation-test.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            val model = loadSingleTestModel(bed, modelFile)
            val prepared = CountDownLatch(1)
            glView.queueEvent {
                model.addObject(model, 0)
                model.translate(1, 15.0, 0.0, 0.0)
                glView.renderer.setSelectedObject(0)
                glView.requestRender()
                prepared.countDown()
            }
            assertTrue("delete fixtures should be prepared on GL", prepared.await(10, TimeUnit.SECONDS))
            compose.runOnUiThread { bed.updateModel() }
            instrumentationWaitForGlIdle()
            val revisionBefore = model.geometryRevision
            val boundsBefore = readExactBoundsOnGl(glView, 0, 1)

            openModelMenu(activity)
            selectSingleObjectScope()
            revealMenuAction(activity, R.string.ModelMenuDeleteSelected)
            compose.onNodeWithContentDescription(activity.getString(R.string.ModelMenuDeleteSelected))
                .performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
            compose.waitUntil(timeoutMillis = 5_000) { bed.isWorkspaceMessageDialogAttached() }

            val changed = CountDownLatch(1)
            glView.selectModels(intArrayOf(1)) { changed.countDown() }
            assertTrue("selection change should finish on GL", changed.await(10, TimeUnit.SECONDS))
            compose.onNode(hasText(activity.getString(R.string.ModelMenuDeleteSelected)) and hasClickAction())
                .performClick()
            compose.waitUntil(timeoutMillis = 10_000) { hasSnackbarTitle(R.string.ModelSelectionChanged) }

            assertEquals("stale confirmation must preserve both objects", 2, readObjectCountOnGl(glView))
            assertEquals("stale confirmation must not mutate geometry", revisionBefore, model.geometryRevision)
            boundsBefore.zip(readExactBoundsOnGl(glView, 0, 1)).forEachIndexed { index, (before, after) ->
                assertArrayEquals("stale confirmation must preserve object $index", before, after, 0.001)
            }
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun deleteAllConfirmationRejectsChangedModelWithoutDeletingIt() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val modelFile = File(activity.cacheDir, "delete-all-stale-confirmation-test.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            val model = loadSingleTestModel(bed, modelFile)
            compose.runOnUiThread { bed.updateModel() }
            openModelMenu(activity)
            selectAllObjectsScope()
            revealMenuAction(activity, R.string.ModelMenuDeleteAll)
            compose.onNodeWithContentDescription(activity.getString(R.string.ModelMenuDeleteAll))
                .performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
            compose.waitUntil(timeoutMillis = 5_000) { bed.isWorkspaceMessageDialogAttached() }

            val mutated = CountDownLatch(1)
            glView.queueEvent {
                model.translate(0, 7.0, 0.0, 0.0)
                glView.requestRender()
                mutated.countDown()
            }
            assertTrue("test geometry change should finish on GL", mutated.await(10, TimeUnit.SECONDS))
            val revisionAfterTestMutation = model.geometryRevision
            compose.onNode(hasText(activity.getString(R.string.ModelMenuDeleteAll)) and hasClickAction())
                .performClick()
            compose.waitUntil(timeoutMillis = 10_000) { hasSnackbarTitle(R.string.ModelSelectionChanged) }

            assertSame("stale delete-all must retain the same model", model, glView.renderer.model)
            assertEquals("stale delete-all must preserve the model object", 1, readObjectCountOnGl(glView))
            assertEquals("only the deliberate test mutation should change geometry", revisionAfterTestMutation,
                model.geometryRevision)
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun objectListPlacesGroupWithoutJoiningUntilExplicitlyCombined() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val modelFile = File(activity.cacheDir, "model-menu-group-placement-test.stl")
        val roundTripFile = File(activity.cacheDir, "model-menu-group-placement-test.3mf")
        val gcodeFile = File(activity.cacheDir, "model-menu-group-placement-test.gcode")
        val previousSlots = Prefs.getStoredFilamentSlots().map { FilamentSlot(it.color, it.type) }
        val testSlots = listOf(
            FilamentSlot(0xFF303030.toInt(), "Placement T0"),
            FilamentSlot(0xFF3366CC.toInt(), "Placement T1"),
            FilamentSlot(0xFFF2F2F2.toInt(), "Placement T2"),
        )
        Prefs.setStoredFilamentSlots(testSlots)
        SliceBeam.INSTANCE = activity.applicationContext as SliceBeam
        Prefs.init(activity.applicationContext as SliceBeam)
        TemporaryProjectSettings.clear()
        val previousConfig = SliceBeam.CONFIG
        val configFile = SliceBeam.getCurrentConfigFile()
        val previousConfigFile = configFile.takeIf { it.isFile }?.readBytes()
        val prefs = requireNotNull(Prefs.getPrefs())
        val snapshotKeys = listOf("mmu_snapshot_printer", "mmu_snapshot")
        val previousSnapshots = snapshotKeys.associateWith { prefs.getString(it, null) }
        var roundTrippedModel: Model? = null
        var sliceResult: GCodeProcessorResult? = null
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            loadSingleTestModel(bed, modelFile)
            val prepared = CountDownLatch(1)
            val width = AtomicReference<Double>()
            glView.queueEvent {
                val renderer = glView.renderer
                val model = requireNotNull(renderer.model)
                val min = Vec3d(); val max = Vec3d()
                model.getBoundingBoxExact(0, min, max)
                width.set(max.x - min.x)
                model.addObject(model, 0)
                model.addObject(model, 0)
                model.translate(1, requireNotNull(width.get()) * 1.5, 0.0, 0.0)
                model.translate(2, requireNotNull(width.get()) * 3.0, requireNotNull(width.get()) * 2.0, 0.0)
                model.setUniformFilament(0, 1)
                model.setUniformFilament(1, 2)
                model.setUniformFilament(2, 3)
                renderer.setSelectedObject(0)
                glView.requestRender()
                prepared.countDown()
            }
            assertTrue("group-placement objects should be prepared on the GL thread", prepared.await(10, TimeUnit.SECONDS))
            compose.runOnUiThread { bed.updateModel() }
            instrumentationWaitForGlIdle()
            assertArrayEquals("the source object should remain selected before opening the object list",
                intArrayOf(0), readSelectedIndicesOnGl(glView))

            openModelMenu(activity)
            selectSingleObjectScope()
            revealMenuAction(activity, R.string.WorkspaceObjects)
            compose.onNodeWithContentDescription(activity.getString(R.string.WorkspaceObjects))
                .assertIsDisplayed().performClick()
            compose.onNodeWithTag("model-objects-group-selection-toggle")
                .assertIsDisplayed().performClick()
            compose.onNodeWithTag("model-object-row-1").performClick()
            compose.runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
            compose.waitUntil(timeoutMillis = 15_000) {
                compose.onAllNodesWithTag("model-objects-selection-confirm").fetchSemanticsNodes().isEmpty()
            }
            val selectionAfterBack = AtomicReference<IntArray>()
            val backSelectionReady = CountDownLatch(1)
            glView.queueEvent {
                selectionAfterBack.set(glView.renderer.selectedObjectIndices())
                backSelectionReady.countDown()
            }
            assertTrue("cancelled group selection should be readable on GL", backSelectionReady.await(10, TimeUnit.SECONDS))
            assertArrayEquals("Android Back discards the staged group selection", intArrayOf(0),
                requireNotNull(selectionAfterBack.get()))

            openModelMenu(activity)
            revealMenuAction(activity, R.string.WorkspaceObjects)
            compose.onNodeWithContentDescription(activity.getString(R.string.WorkspaceObjects))
                .assertIsDisplayed().performClick()
            compose.onNodeWithTag("model-objects-group-selection-toggle")
                .assertIsDisplayed().performClick()
            compose.onNodeWithTag("model-object-row-1").performClick()
            compose.onNodeWithTag("model-objects-selection-confirm").performClick()
            compose.waitUntil(timeoutMillis = 10_000) { glView.renderer.selectedObjectsCount == 2 }
            val selectedIndices = AtomicReference<IntArray>()
            val selectionReady = CountDownLatch(1)
            glView.queueEvent {
                selectedIndices.set(glView.renderer.selectedObjectIndices())
                selectionReady.countDown()
            }
            assertTrue("selected group should be readable on the GL thread", selectionReady.await(10, TimeUnit.SECONDS))
            assertArrayEquals("the object list should select only the two chosen models",
                intArrayOf(0, 1), requireNotNull(selectedIndices.get()))

            val before = AtomicReference<List<DoubleArray>>()
            val boundsReady = CountDownLatch(1)
            glView.queueEvent {
                val model = requireNotNull(glView.renderer.model)
                before.set((0..2).map { index ->
                    val min = Vec3d(); val max = Vec3d()
                    model.getBoundingBoxExact(index, min, max)
                    doubleArrayOf(min.x, min.y, min.z, max.x, max.y, max.z)
                })
                boundsReady.countDown()
            }
            assertTrue("pre-placement bounds should be collected on the GL thread", boundsReady.await(10, TimeUnit.SECONDS))

            openModelMenu(activity)
            selectSingleObjectScope()
            revealMenuAction(activity, R.string.ModelMenuPlaceOnObject)
            compose.onNodeWithContentDescription(activity.getString(R.string.ModelMenuPlaceOnObject))
                .assertIsDisplayed().assertIsEnabled().performClick()
            compose.onNodeWithTag("model-choice-option-0").assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 15_000) { readObjectCountOnGl(glView) == 3 }

            val after = AtomicReference<List<DoubleArray>>()
            val afterReady = CountDownLatch(1)
            glView.queueEvent {
                val model = glView.renderer.model
                if (model != null && model.objectsCount == 3) {
                    after.set((0 until model.objectsCount).map { index ->
                        val min = Vec3d(); val max = Vec3d()
                        model.getBoundingBoxExact(index, min, max)
                        doubleArrayOf(min.x, min.y, min.z, max.x, max.y, max.z)
                    })
                }
                afterReady.countDown()
            }
            assertTrue("post-placement bounds should be collected on the GL thread", afterReady.await(15, TimeUnit.SECONDS))
            val original = requireNotNull(before.get())
            val placed = requireNotNull(after.get())
            assertEquals("placing a group on another model must keep all three objects separate", 3, placed.size)
            assertArrayEquals("placement must not change target geometry", original[2], placed[2], 0.02)
            val movingGroup = listOf(0, 1).map { original[it] }
            val groupMinX = movingGroup.minOf { it[0] }
            val groupMinY = movingGroup.minOf { it[1] }
            val groupMinZ = movingGroup.minOf { it[2] }
            val groupMaxX = movingGroup.maxOf { it[3] }
            val groupMaxY = movingGroup.maxOf { it[4] }
            val dx = (original[2][0] + original[2][3]) / 2.0 - (groupMinX + groupMaxX) / 2.0
            val dy = (original[2][1] + original[2][4]) / 2.0 - (groupMinY + groupMaxY) / 2.0
            val dz = original[2][5] - groupMinZ
            movingGroup.forEachIndexed { index, bounds ->
                val expected = doubleArrayOf(bounds[0] + dx, bounds[1] + dy, bounds[2] + dz,
                    bounds[3] + dx, bounds[4] + dy, bounds[5] + dz)
                assertArrayEquals("placed group member ${index + 1} must remain independently editable",
                    expected, placed[index], 0.02)
            }
            assertArrayEquals("the three original filament assignments remain on separate objects",
                intArrayOf(1, 2, 3), (0..2).map { readUsedFilamentsOnGl(glView, it).single() }.toIntArray())

            // Joining is a separate, explicit action. The object-list workflow is covered
            // independently above; select the verified objects here to test that the menu's
            // Combine action is the only step that turns them into one assembly.
            val explicitJoinSelectionReady = CountDownLatch(1)
            glView.queueEvent {
                glView.renderer.setSelectedObject(0)
                glView.renderer.setSelectedObjects(intArrayOf(0, 1, 2))
                glView.requestRender()
                explicitJoinSelectionReady.countDown()
            }
            assertTrue("all three objects should remain selected for an explicit join",
                explicitJoinSelectionReady.await(10, TimeUnit.SECONDS))
            openModelMenu(activity)
            selectSingleObjectScope()
            revealMenuAction(activity, R.string.ModelMenuCombineSelected)
            compose.onNodeWithContentDescription(activity.getString(R.string.ModelMenuCombineSelected))
                .assertIsDisplayed().assertIsEnabled().performClick()
            compose.onNodeWithTag("workspace-message-confirm").assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 15_000) { bed.currentModel?.objectsCount == 1 }
            assertArrayEquals("explicitly combining keeps all source object materials", intArrayOf(1, 2, 3),
                readUsedFilamentsOnGl(glView, 0))

            // Verify the exact real menu operation survives project serialization and slicing.
            activity.assets.open("orca_profiles/Anycubic.ini").use {
                SliceBeam.CONFIG = Slic3rConfigWrapper(it)
            }
            SliceBeam.requireConfig().presets = ConfigObject("presets").apply {
                put("printer", "Anycubic Kobra 3 0.4 nozzle")
                put("print", "0.16mm Standard")
                put("filament", "Anycubic Generic PLA")
            }
            val clearSnapshots = prefs.edit()
            snapshotKeys.forEach(clearSnapshots::remove)
            assertTrue("stale printer filament snapshots should be cleared for this fixture",
                clearSnapshots.commit())
            val exportReady = CountDownLatch(1)
            val exportFailure = AtomicReference<Throwable?>()
            glView.queueEvent {
                try {
                    val model = requireNotNull(glView.renderer.model)
                    SliceBeam.genCurrentConfig(model)
                    model.export3mf(configFile.absolutePath, roundTripFile.absolutePath)
                } catch (error: Throwable) {
                    exportFailure.set(error)
                } finally {
                    exportReady.countDown()
                }
            }
            assertTrue("placed group should export through the real renderer model",
                exportReady.await(60, TimeUnit.SECONDS))
            exportFailure.get()?.let { throw AssertionError("placed group 3MF export failed", it) }
            assertTrue("3MF export should produce a non-empty project", roundTripFile.isFile && roundTripFile.length() > 0L)
            roundTrippedModel = Model(roundTripFile, 1)
            assertEquals("the grouped placement should remain one multipart object after reload",
                1, roundTrippedModel!!.objectsCount)
            assertArrayEquals("the UI placement's three material assignments should survive 3MF reload",
                intArrayOf(1, 2, 3), roundTrippedModel!!.getUsedFilaments(0))
            SliceBeam.genCurrentConfig(roundTrippedModel!!)
            sliceResult = roundTrippedModel!!.slice(configFile.absolutePath, gcodeFile.absolutePath, null)
            assertTrue("the reloaded UI placement should produce non-empty G-code",
                gcodeFile.isFile && gcodeFile.length() > 0L)
            val gcode = gcodeFile.readText()
            val positiveExtrusion = Regex("""(?m)^G1[^\r\n]*\bE([0-9]+(?:\.[0-9]+)?)""")
                .findAll(gcode).any { it.groupValues[1].toDoubleOrNull()?.let { value -> value > 0.0 } == true }
            assertTrue("the reloaded UI placement should produce positive deposited extrusion", positiveExtrusion)
            val emittedTools = Regex("(?m)^T(\\d+)(?:\\s|$)").findAll(gcode)
                .map { it.groupValues[1].toInt() }.toSet()
            assertTrue("placed target filament T0 must be used, tools=$emittedTools", 0 in emittedTools)
            assertTrue("placed group filament T1 must be used, tools=$emittedTools", 1 in emittedTools)
            assertTrue("placed group filament T2 must be used, tools=$emittedTools", 2 in emittedTools)

            compose.runOnUiThread { bed.undoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) { readObjectCountOnGl(glView) == 3 }
            val restoredFilaments = AtomicReference<List<Int>>()
            val restoredReady = CountDownLatch(1)
            glView.queueEvent {
                val model = requireNotNull(glView.renderer.model)
                restoredFilaments.set((0..2).flatMap { model.getUsedFilaments(it).toList() })
                restoredReady.countDown()
            }
            assertTrue("Undo should restore source-object materials", restoredReady.await(10, TimeUnit.SECONDS))
            assertEquals(listOf(1, 2, 3), requireNotNull(restoredFilaments.get()))
            compose.runOnUiThread { bed.redoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) { readObjectCountOnGl(glView) == 1 }
            val redoneFilaments = AtomicReference<IntArray>()
            val redoReady = CountDownLatch(1)
            glView.queueEvent {
                redoneFilaments.set(glView.renderer.model!!.getUsedFilaments(0))
                redoReady.countDown()
            }
            assertTrue("Redo should restore multipart material assignments", redoReady.await(10, TimeUnit.SECONDS))
            assertArrayEquals(intArrayOf(1, 2, 3), requireNotNull(redoneFilaments.get()))
        } finally {
            sliceResult?.release()
            roundTrippedModel?.release()
            gcodeFile.delete()
            roundTripFile.delete()
            val restoreSnapshots = prefs.edit()
            previousSnapshots.forEach { (key, value) ->
                if (value == null) restoreSnapshots.remove(key) else restoreSnapshots.putString(key, value)
            }
            restoreSnapshots.commit()
            SliceBeam.CONFIG = previousConfig
            if (previousConfigFile == null) configFile.delete() else configFile.writeBytes(previousConfigFile)
            TemporaryProjectSettings.clear()
            Prefs.setStoredFilamentSlots(previousSlots)
            modelFile.delete()
        }
    }

    @Test
    fun modelMenuPlacesSelectedObjectAtTappedSideSurfaceAndSupportsUndoRedo() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val modelFile = File(activity.cacheDir, "model-menu-picked-surface-placement-test.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            val model = loadSingleTestModel(bed, modelFile)
            val prepared = CountDownLatch(1)
            val sourceBounds = AtomicReference<List<DoubleArray>>()
            glView.queueEvent {
                model.addObject(model, 0)
                model.translate(1, 38.0, 0.0, 0.0)
                model.setUniformFilament(0, 1)
                model.setUniformFilament(1, 2)
                glView.renderer.setSelectedObject(1)
                sourceBounds.set((0..1).map { index ->
                    val min = Vec3d(); val max = Vec3d()
                    model.getBoundingBoxExact(index, min, max)
                    doubleArrayOf(min.x, min.y, min.z, max.x, max.y, max.z)
                })
                glView.requestRender()
                prepared.countDown()
            }
            assertTrue("surface-placement models should be prepared on GL", prepared.await(10, TimeUnit.SECONDS))
            compose.runOnUiThread { bed.updateModel() }
            instrumentationWaitForGlIdle()

            openModelMenu(activity)
            selectSingleObjectScope()
            revealMenuAction(activity, R.string.ModelMenuPlaceOnSurface)
            compose.onNodeWithContentDescription(activity.getString(R.string.ModelMenuPlaceOnSurface))
                .assertIsDisplayed().assertIsEnabled().performClick()
            compose.onNodeWithTag("model-choice-option-0").assertIsDisplayed().performClick()

            val tappedPoint = AtomicReference<DoubleArray>()
            val tappedNormal = AtomicReference<DoubleArray>()
            val tapReady = CountDownLatch(1)
            var targetPixels = 0
            var exactTargetHits = 0
            var smallestEligibleNormalZ = 2.0
            var sampleHit: String? = null
            glView.queueEvent {
                val renderer = glView.renderer
                val glModelsField = GLRenderer::class.java.getDeclaredField("glModels").apply { isAccessible = true }
                @Suppress("UNCHECKED_CAST")
                val glModels = glModelsField.get(renderer) as List<GLModel>
                val targetHits = ArrayList<GLModel.HitResult>()
                val xStep = (renderer.viewportWidth / 64).coerceAtLeast(1)
                val yStep = (renderer.viewportHeight / 64).coerceAtLeast(1)
                loop@ for (y in yStep until renderer.viewportHeight step yStep) {
                    for (x in xStep until renderer.viewportWidth step xStep) {
                        if (renderer.raycastObjectIndex(x.toFloat(), y.toFloat()) != 0) continue
                        targetPixels++
                        targetHits.clear()
                        glModels[0].raycaster.raycast(renderer, targetHits, x.toFloat(), y.toFloat())
                        targetHits.filter { !it.approximate }.forEach { hit ->
                            exactTargetHits++
                            if (hit.normal.z >= -1e-6) smallestEligibleNormalZ = minOf(smallestEligibleNormalZ, hit.normal.z)
                            if (sampleHit == null) sampleHit = "p=${hit.position}, n=${hit.normal}"
                        }
                        val hit = targetHits.firstOrNull { !it.approximate && it.normal.z in -1e-6..0.7 } ?: continue
                        if (!renderer.onClick(x.toFloat(), y.toFloat())) continue
                        tappedPoint.set(doubleArrayOf(hit.position.x, hit.position.y, hit.position.z))
                        tappedNormal.set(doubleArrayOf(hit.normal.x, hit.normal.y, hit.normal.z))
                        break@loop
                    }
                }
                tapReady.countDown()
            }
            assertTrue("surface tap search should complete on GL", tapReady.await(20, TimeUnit.SECONDS))
            val point = requireNotNull(tappedPoint.get()) {
                "test camera should expose a precise side target; targetPixels=$targetPixels, " +
                    "exactHits=$exactTargetHits, smallestEligibleNormalZ=$smallestEligibleNormalZ, sample=$sampleHit"
            }
            compose.waitUntil(timeoutMillis = 15_000) { readObjectCountOnGl(glView) == 2 }

            val originals = requireNotNull(sourceBounds.get())
            val target = originals[0]
            val moving = originals[1]
            val placement = requireNotNull(ObjectPlacement.onSurface(
                ObjectBounds3D(moving[0], moving[1], moving[2], moving[3], moving[4], moving[5]),
                Vec3d(point[0], point[1], point[2]),
                requireNotNull(tappedNormal.get()).let { Vec3d(it[0], it[1], it[2]) },
            ))
            val expectedMovingBounds = doubleArrayOf(
                moving[0] + placement.x, moving[1] + placement.y, moving[2] + placement.z,
                moving[3] + placement.x, moving[4] + placement.y, moving[5] + placement.z,
            )
            assertEquals("surface placement must leave target as its own model object", target.toList(),
                readExactBoundsOnGl(glView, 0).single().toList())
            expectedMovingBounds.forEachIndexed { index, expected ->
                assertEquals("placement must move the selected object without merging it, axis $index",
                    expected, readExactBoundsOnGl(glView, 1).single()[index], 0.04)
            }
            assertEquals("the target material must remain independent", listOf(1), readUsedFilamentsOnGl(glView, 0).toList())
            assertEquals("the moved object material must remain independently editable", listOf(2),
                readUsedFilamentsOnGl(glView, 1).toList())

            compose.runOnUiThread { bed.undoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) { readObjectCountOnGl(glView) == 2 }
            compose.runOnUiThread { bed.redoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) { readObjectCountOnGl(glView) == 2 }
            assertEquals(listOf(1), readUsedFilamentsOnGl(glView, 0).toList())
            assertEquals(listOf(2), readUsedFilamentsOnGl(glView, 1).toList())
            val redonePlacementBounds = readExactBoundsOnGl(glView, 1).single()
            expectedMovingBounds.forEachIndexed { index, expected ->
                assertEquals("Redo must restore surface placement geometry on axis $index",
                    expected, redonePlacementBounds[index], 0.04)
            }
        } finally {
            if (glView.renderer.isSurfacePlacementActive) bed.onBackPressed()
            modelFile.delete()
        }
    }

    @Test
    fun modelMenuPlacesSelectedGroupOnTappedSurfaceAndPreservesSpacingAndMaterials() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val modelFile = File(activity.cacheDir, "model-menu-group-surface-placement-test.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            val model = loadSingleTestModel(bed, modelFile)
            val sourceBounds = AtomicReference<List<DoubleArray>>()
            val prepared = CountDownLatch(1)
            glView.queueEvent {
                model.addObject(model, 0)
                model.addObject(model, 0)
                model.translate(1, 45.0, 12.0, 7.0)
                model.translate(2, 70.0, 28.0, 16.0)
                model.setUniformFilament(0, 1)
                model.setUniformFilament(1, 2)
                model.setUniformFilament(2, 3)
                glView.renderer.setSelectedObjects(intArrayOf(1, 2))
                sourceBounds.set((0..2).map { index ->
                    val min = Vec3d(); val max = Vec3d()
                    model.getBoundingBoxExact(index, min, max)
                    doubleArrayOf(min.x, min.y, min.z, max.x, max.y, max.z)
                })
                glView.requestRender()
                prepared.countDown()
            }
            assertTrue("the target and moving group should be prepared on GL", prepared.await(10, TimeUnit.SECONDS))
            compose.runOnUiThread { bed.updateModel() }
            instrumentationWaitForGlIdle()

            openModelMenu(activity)
            selectSingleObjectScope()
            revealMenuAction(activity, R.string.ModelMenuPlaceOnSurface)
            compose.onNodeWithContentDescription(activity.getString(R.string.ModelMenuPlaceOnSurface))
                .assertIsDisplayed().assertIsEnabled().performClick()
            // Target choices are captured asynchronously on the GL queue before Compose opens
            // the dialog. Compose idleness alone does not wait for that renderer callback.
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodesWithTag("model-choice-option-0").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("model-choice-option-0").assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 10_000) { glView.renderer.isSurfacePlacementActive }

            val tappedPoint = AtomicReference<DoubleArray>()
            val tapReady = CountDownLatch(1)
            var exactTargetHits = 0
            glView.queueEvent {
                val renderer = glView.renderer
                val glModelsField = GLRenderer::class.java.getDeclaredField("glModels").apply { isAccessible = true }
                @Suppress("UNCHECKED_CAST")
                val glModels = glModelsField.get(renderer) as List<GLModel>
                val targetHits = ArrayList<GLModel.HitResult>()
                val xStep = (renderer.viewportWidth / 64).coerceAtLeast(1)
                val yStep = (renderer.viewportHeight / 64).coerceAtLeast(1)
                loop@ for (y in yStep until renderer.viewportHeight step yStep) {
                    for (x in xStep until renderer.viewportWidth step xStep) {
                        if (renderer.raycastObjectIndex(x.toFloat(), y.toFloat()) != 0) continue
                        targetHits.clear()
                        glModels[0].raycaster.raycast(renderer, targetHits, x.toFloat(), y.toFloat())
                        val hit = targetHits.firstOrNull { !it.approximate && it.normal.z >= 0.7 } ?: continue
                        exactTargetHits++
                        if (!renderer.onClick(x.toFloat(), y.toFloat())) continue
                        tappedPoint.set(doubleArrayOf(hit.position.x, hit.position.y, hit.position.z))
                        break@loop
                    }
                }
                tapReady.countDown()
            }
            assertTrue("group surface-pick search should complete on GL", tapReady.await(20, TimeUnit.SECONDS))
            val point = requireNotNull(tappedPoint.get()) {
                "the group should be placeable on an exposed upward target face; exactHits=$exactTargetHits"
            }
            compose.waitUntil(timeoutMillis = 15_000) { readObjectCountOnGl(glView) == 3 }

            val original = requireNotNull(sourceBounds.get())
            val groupMinX = minOf(original[1][0], original[2][0])
            val groupMinY = minOf(original[1][1], original[2][1])
            val groupMinZ = minOf(original[1][2], original[2][2])
            val groupMaxX = maxOf(original[1][3], original[2][3])
            val groupMaxY = maxOf(original[1][4], original[2][4])
            val groupMaxZ = maxOf(original[1][5], original[2][5])
            val dx = point[0] - (groupMinX + groupMaxX) / 2.0
            val dy = point[1] - (groupMinY + groupMaxY) / 2.0
            val dz = point[2] - groupMinZ
            val expectedMovingBounds = listOf(1, 2).map { index ->
                doubleArrayOf(
                    original[index][0] + dx, original[index][1] + dy, original[index][2] + dz,
                    original[index][3] + dx, original[index][4] + dy, original[index][5] + dz,
                )
            }
            assertEquals("the untouched target must retain its own bounds", original[0].toList(),
                readExactBoundsOnGl(glView, 0).single().toList())
            expectedMovingBounds.forEachIndexed { movingIndex, expected ->
                expected.forEachIndexed { axis, value ->
                    assertEquals("group member ${movingIndex + 1} remains an independent object at axis $axis",
                        value, readExactBoundsOnGl(glView, movingIndex + 1).single()[axis], 0.04)
                }
            }
            assertEquals(listOf(1), readUsedFilamentsOnGl(glView, 0).toList())
            assertEquals(listOf(2), readUsedFilamentsOnGl(glView, 1).toList())
            assertEquals(listOf(3), readUsedFilamentsOnGl(glView, 2).toList())

            compose.runOnUiThread { bed.undoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) { readObjectCountOnGl(glView) == 3 }
            assertEquals("Undo should restore each source object's material assignment", listOf(1, 2, 3),
                (0..2).map { readUsedFilamentsOnGl(glView, it).single() })
            compose.runOnUiThread { bed.redoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) { readObjectCountOnGl(glView) == 3 }
            assertEquals(listOf(1), readUsedFilamentsOnGl(glView, 0).toList())
            assertEquals(listOf(2), readUsedFilamentsOnGl(glView, 1).toList())
            assertEquals(listOf(3), readUsedFilamentsOnGl(glView, 2).toList())
            expectedMovingBounds.forEachIndexed { movingIndex, expected ->
                expected.forEachIndexed { axis, value ->
                    assertEquals("Redo keeps independent group member ${movingIndex + 1} placed at axis $axis",
                        value, readExactBoundsOnGl(glView, movingIndex + 1).single()[axis], 0.04)
                }
            }
        } finally {
            if (glView.renderer.isSurfacePlacementActive) bed.onBackPressed()
            modelFile.delete()
        }
    }

    @Test
    fun booleanDialogPlacesToolOnPickedTargetAndAppliesUnion() {
        assertSurfaceBooleanOperation(BooleanOperation.UNION)
    }

    @Test
    fun booleanDialogPlacesToolOnPickedTargetAndAppliesDifference() {
        assertSurfaceBooleanOperation(BooleanOperation.DIFFERENCE)
    }

    @Test
    fun booleanDialogPlacesToolOnPickedTargetAndAppliesIntersection() {
        assertSurfaceBooleanOperation(BooleanOperation.INTERSECTION)
    }

    private fun assertSurfaceBooleanOperation(operation: BooleanOperation) {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val modelFile = File(activity.cacheDir, "boolean-surface-pick-${operation.name.lowercase()}.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            val model = loadSingleTestModel(bed, modelFile)
            val baseVolume = AtomicReference<Double>()
            val prepared = CountDownLatch(1)
            glView.queueEvent {
                baseVolume.set(model.getObjectVolume(0))
                model.addObject(model, 0)
                model.scale(1, 0.25, 0.25, 0.25)
                model.translate(1, 80.0, 80.0, 50.0)
                model.setUniformFilament(0, 1)
                model.setUniformFilament(1, 2)
                glView.renderer.setSelectedObject(0)
                glView.renderer.setSelectedObjects(intArrayOf(0, 1))
                glView.requestRender()
                prepared.countDown()
            }
            assertTrue("Boolean target and tool should be prepared on the GL thread",
                prepared.await(10, TimeUnit.SECONDS))
            compose.runOnUiThread { bed.updateModel() }
            instrumentationWaitForGlIdle()

            openModelMenu(activity)
            selectSingleObjectScope()
            revealMenuAction(activity, R.string.ModelMenuBoolean)
            compose.onNodeWithContentDescription(activity.getString(R.string.ModelMenuBoolean))
                .assertIsDisplayed().assertIsEnabled().performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithTag("model-boolean-place-on-surface", useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("model-boolean-place-on-surface", useUnmergedTree = true)
                .performScrollTo().performClick()
            val operationTag = when (operation) {
                BooleanOperation.UNION -> "model-boolean-union"
                BooleanOperation.DIFFERENCE -> "model-boolean-difference"
                BooleanOperation.INTERSECTION -> "model-boolean-intersection"
            }
            compose.onNodeWithTag(operationTag, useUnmergedTree = true)
                .performScrollTo().performClick()
            compose.waitUntil(timeoutMillis = 10_000) { glView.renderer.isSurfacePlacementActive }

            val tapReady = CountDownLatch(1)
            val tappedPoint = AtomicReference<DoubleArray>()
            glView.queueEvent {
                val renderer = glView.renderer
                val glModelsField = GLRenderer::class.java.getDeclaredField("glModels").apply {
                    isAccessible = true
                }
                @Suppress("UNCHECKED_CAST")
                val glModels = glModelsField.get(renderer) as List<GLModel>
                val targetHits = ArrayList<GLModel.HitResult>()
                val xStep = (renderer.viewportWidth / 64).coerceAtLeast(1)
                val yStep = (renderer.viewportHeight / 64).coerceAtLeast(1)
                loop@ for (y in yStep until renderer.viewportHeight step yStep) {
                    for (x in xStep until renderer.viewportWidth step xStep) {
                        if (renderer.raycastObjectIndex(x.toFloat(), y.toFloat()) != 0) continue
                        targetHits.clear()
                        glModels[0].raycaster.raycast(renderer, targetHits, x.toFloat(), y.toFloat())
                        val hit = targetHits.firstOrNull { !it.approximate && it.normal.z >= 0.7 } ?: continue
                        if (!renderer.onClick(x.toFloat(), y.toFloat())) continue
                        tappedPoint.set(doubleArrayOf(hit.position.x, hit.position.y, hit.position.z))
                        break@loop
                    }
                }
                tapReady.countDown()
            }
            assertTrue("test should locate and tap an exact visible target face",
                tapReady.await(20, TimeUnit.SECONDS))
            val point = requireNotNull(tappedPoint.get()) {
                "no exact upward-facing target surface was available for the Boolean tap"
            }
            compose.waitUntil(timeoutMillis = 30_000) { readObjectCountOnGl(glView) == 1 }
            val resultVolume = readObjectVolumeOnGl(glView, 0)
            val originalVolume = requireNotNull(baseVolume.get())
            when (operation) {
                BooleanOperation.UNION -> assertTrue(
                    "surface-picked union should add the positioned tool to the target",
                    resultVolume > originalVolume,
                )
                BooleanOperation.DIFFERENCE -> assertTrue(
                    "surface-picked difference should remove the penetrating tool volume",
                    resultVolume > 0.0 && resultVolume < originalVolume,
                )
                BooleanOperation.INTERSECTION -> assertTrue(
                    "surface-picked intersection should retain only the shared solid volume",
                    resultVolume > 0.0 && resultVolume < originalVolume,
                )
            }
            assertArrayEquals("the Boolean result should retain the selected base material", intArrayOf(1),
                readUsedFilamentsOnGl(glView, 0))
            val resultBounds = readExactBoundsOnGl(glView, 0).single()
            when (operation) {
                BooleanOperation.UNION -> assertTrue(
                    "union should extend above the tapped target surface", resultBounds[5] > point[2],
                )
                BooleanOperation.DIFFERENCE -> assertTrue(
                    "difference should preserve the target's overall extent", resultBounds[5] >= point[2] - 0.01,
                )
                BooleanOperation.INTERSECTION -> assertTrue(
                    "intersection should occupy only the shallow surface overlap", resultBounds[5] <= point[2] + 0.01,
                )
            }

            compose.runOnUiThread { bed.undoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) { readObjectCountOnGl(glView) == 2 }
            compose.runOnUiThread { bed.redoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) { readObjectCountOnGl(glView) == 1 }
            assertEquals("Redo should restore the surface-picked ${operation.name.lowercase()}", resultVolume,
                readObjectVolumeOnGl(glView, 0), resultVolume * 0.002)
        } finally {
            if (glView.renderer.isSurfacePlacementActive) bed.onBackPressed()
            modelFile.delete()
        }
    }

    @Test
    fun androidBackCancelsSurfacePlacementWithoutChangingEitherObject() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val modelFile = File(activity.cacheDir, "model-menu-surface-placement-back-test.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            val model = loadSingleTestModel(bed, modelFile)
            val prepared = CountDownLatch(1)
            glView.queueEvent {
                model.addObject(model, 0)
                model.translate(1, 38.0, 0.0, 0.0)
                model.setUniformFilament(0, 1)
                model.setUniformFilament(1, 2)
                glView.renderer.setSelectedObject(1)
                glView.requestRender()
                prepared.countDown()
            }
            assertTrue("models should be prepared on the GL thread", prepared.await(10, TimeUnit.SECONDS))
            compose.runOnUiThread { bed.updateModel() }
            instrumentationWaitForGlIdle()
            val beforeBounds = readExactBoundsOnGl(glView, 0, 1)
            val beforeFilaments = (0..1).map { readUsedFilamentsOnGl(glView, it).toList() }

            openModelMenu(activity)
            selectSingleObjectScope()
            revealMenuAction(activity, R.string.ModelMenuPlaceOnSurface)
            compose.onNodeWithContentDescription(activity.getString(R.string.ModelMenuPlaceOnSurface))
                .assertIsDisplayed().assertIsEnabled().performClick()
            compose.onNodeWithTag("model-choice-option-0").assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 10_000) { glView.renderer.isSurfacePlacementActive }

            assertTrue("Android Back should cancel the waiting surface-pick operation", bed.onBackPressed())
            compose.waitUntil(timeoutMillis = 10_000) { !glView.renderer.isSurfacePlacementActive }
            assertEquals("cancellation must leave both input objects in place", 2, readObjectCountOnGl(glView))
            val afterBounds = readExactBoundsOnGl(glView, 0, 1)
            beforeBounds.zip(afterBounds).forEachIndexed { objectIndex, (before, after) ->
                before.indices.forEach { axis ->
                    assertEquals("object $objectIndex bounds must survive Back on axis $axis",
                        before[axis], after[axis], 0.001)
                }
            }
            assertEquals("cancellation must preserve both objects' material assignments", beforeFilaments,
                (0..1).map { readUsedFilamentsOnGl(glView, it).toList() })
        } finally {
            if (glView.renderer.isSurfacePlacementActive) bed.onBackPressed()
            modelFile.delete()
        }
    }

    @Test
    fun selectedGroupCanBeDuplicatedWithItsFilamentsAndDroppedTogetherOnBed() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val modelFile = File(activity.cacheDir, "model-menu-group-duplicate-drop-test.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            val model = loadSingleTestModel(bed, modelFile)
            val prepared = CountDownLatch(1)
            val bedCenter = AtomicReference<DoubleArray>()
            val bedMinZ = AtomicReference<Double>()
            glView.queueEvent {
                val renderer = glView.renderer
                val activeBed = requireNotNull(renderer.bed)
                val min = requireNotNull(activeBed.getVolumeMin())
                val max = requireNotNull(activeBed.getVolumeMax())
                bedCenter.set(doubleArrayOf((min.x + max.x) / 2.0, (min.y + max.y) / 2.0))
                bedMinZ.set(min.z)
                val widthBounds = Vec3d(); val widthMax = Vec3d()
                model.getBoundingBoxExact(0, widthBounds, widthMax)
                val width = widthMax.x - widthBounds.x
                model.translate(0, 35.0, 20.0, 8.0)
                model.addObject(model, 0)
                model.translate(1, width * 1.5, 7.0, 18.0)
                model.setUniformFilament(0, 2)
                model.setUniformFilament(1, 3)
                renderer.setSelectedObjects(intArrayOf(0, 1))
                glView.requestRender()
                prepared.countDown()
            }
            assertTrue("the selected group should be prepared on the GL thread", prepared.await(10, TimeUnit.SECONDS))
            compose.runOnUiThread { bed.updateModel() }
            instrumentationWaitForGlIdle()

            openModelMenu(activity)
            selectSingleObjectScope()
            revealMenuAction(activity, R.string.ModelMenuDuplicate)
            compose.onNodeWithText(activity.getString(R.string.ModelMenuDuplicateShort))
                .assertIsDisplayed().assertIsEnabled().performClick()
            val countField = compose.onNodeWithTag("duplicate-model-count")
            countField.performTextClearance()
            countField.performTextInput("1")
            compose.onNodeWithTag("duplicate-model-confirm").performClick()
            compose.waitUntil(timeoutMillis = 15_000) { readObjectCountOnGl(glView) == 4 }
            val duplicatedFilaments = AtomicReference<List<IntArray>>()
            val duplicateReady = CountDownLatch(1)
            glView.queueEvent {
                val current = requireNotNull(glView.renderer.model)
                duplicatedFilaments.set(listOf(current.getUsedFilaments(2), current.getUsedFilaments(3)))
                duplicateReady.countDown()
            }
            assertTrue("duplicated group filaments should be readable", duplicateReady.await(10, TimeUnit.SECONDS))
            assertArrayEquals("the first group copy keeps its source filament", intArrayOf(2),
                requireNotNull(duplicatedFilaments.get())[0])
            assertArrayEquals("the second group copy keeps its source filament", intArrayOf(3),
                requireNotNull(duplicatedFilaments.get())[1])
            assertArrayEquals("group selection survives duplication", intArrayOf(0, 1), readSelectedIndicesOnGl(glView))

            val beforeCenter = readExactBoundsOnGl(glView, 0, 1, 2, 3)
            openModelMenu(activity)
            selectSingleObjectScope()
            revealMenuAction(activity, R.string.ModelMenuCenterOnBed)
            compose.onNodeWithText(activity.getString(R.string.ModelMenuCenterOnBedShort))
                .performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
            instrumentationWaitForGlIdle()
            val centeredBounds = readExactBoundsOnGl(glView, 0, 1, 2, 3)
            val expectedCenter = requireNotNull(bedCenter.get())
            assertEquals("the selected group's combined X bounds are centered on the bed", expectedCenter[0],
                (centeredBounds[0][0] + centeredBounds[1][3]) / 2.0, 0.03)
            assertEquals("the selected group's combined Y bounds are centered on the bed", expectedCenter[1],
                (centeredBounds[0][1] + centeredBounds[1][4]) / 2.0, 0.03)
            assertEquals("group centering preserves internal X spacing",
                (beforeCenter[1][0] + beforeCenter[1][3] - beforeCenter[0][0] - beforeCenter[0][3]) / 2.0,
                (centeredBounds[1][0] + centeredBounds[1][3] - centeredBounds[0][0] - centeredBounds[0][3]) / 2.0,
                0.03)
            compose.runOnUiThread { bed.undoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) {
                beforeCenter.zip(readExactBoundsOnGl(glView, 0, 1, 2, 3))
                    .all { (before, current) -> before.contentEquals(current) }
            }
            compose.runOnUiThread { bed.redoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) {
                centeredBounds.zip(readExactBoundsOnGl(glView, 0, 1, 2, 3))
                    .all { (centered, current) -> centered.contentEquals(current) }
            }

            openModelMenu(activity)
            selectSingleObjectScope()
            revealMenuAction(activity, R.string.ModelMenuPlaceGroupOnBed)
            compose.onNodeWithText(activity.getString(R.string.ModelMenuPlaceGroupOnBedShort))
                .performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
            instrumentationWaitForGlIdle()
            val droppedBounds = readExactBoundsOnGl(glView, 0, 1, 2, 3)
            assertEquals("the lowest selected object rests on the printable bed", requireNotNull(bedMinZ.get()),
                minOf(droppedBounds[0][2], droppedBounds[1][2]), 0.03)
            assertEquals("dropping preserves the selected group's relative height",
                centeredBounds[1][2] - centeredBounds[0][2], droppedBounds[1][2] - droppedBounds[0][2], 0.03)
            assertArrayEquals("unselected group copies stay untouched", centeredBounds[2], droppedBounds[2], 0.03)
            assertArrayEquals("the second unselected group copy stays untouched", centeredBounds[3], droppedBounds[3], 0.03)
            compose.runOnUiThread { bed.undoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) {
                centeredBounds.zip(readExactBoundsOnGl(glView, 0, 1, 2, 3))
                    .all { (centered, current) -> centered.contentEquals(current) }
            }
            compose.runOnUiThread { bed.redoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) {
                droppedBounds.zip(readExactBoundsOnGl(glView, 0, 1, 2, 3))
                    .all { (dropped, current) -> dropped.contentEquals(current) }
            }
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun placeSelectedGroupOnBedRejectsSelectionChangedBeforeGlCommit() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val modelFile = File(activity.cacheDir, "place-group-on-bed-stale-selection.stl")
        try {
            activity.assets.open("models/pyramid.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            loadSingleTestModel(bed, modelFile)
            val prepared = CountDownLatch(1)
            glView.queueEvent {
                val renderer = glView.renderer
                val model = requireNotNull(renderer.model)
                model.addObject(model, 0)
                model.translate(0, 0.0, 0.0, 12.0)
                model.translate(1, 20.0, 0.0, 24.0)
                renderer.setSelectedObject(0)
                renderer.setSelectedObjects(intArrayOf(0, 1))
                glView.requestRender()
                prepared.countDown()
            }
            assertTrue("two elevated objects should be prepared", prepared.await(10, TimeUnit.SECONDS))
            val before = readExactBoundsOnGl(glView, 0, 1)
            val snapshot = AtomicReference<ru.ytkab0bp.slicebeam.render.ModelSelectionSnapshot>()
            val queued = CountDownLatch(1)
            glView.queueEvent {
                val renderer = glView.renderer
                snapshot.set(requireNotNull(renderer.captureSelectionSnapshot()))
                // Simulate the selection changing after the action's immutable request was captured
                // but before its GL operation gets a chance to run.
                renderer.setSelectedObject(1)
                glView.placeSelectedObjectsOnBed(requireNotNull(snapshot.get())) { result ->
                    assertEquals("a different live selection must be rejected", MODEL_EDIT_STALE_SELECTION, result)
                    queued.countDown()
                }
            }
            assertTrue("stale placement should complete", queued.await(10, TimeUnit.SECONDS))
            val after = readExactBoundsOnGl(glView, 0, 1)
            before.zip(after).forEachIndexed { index, (expected, actual) ->
                assertArrayEquals("stale placement must leave object $index unchanged", expected, actual, 0.02)
            }
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun centerAndAutoOrientRejectSelectionChangedBeforeGlCommit() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val modelFile = File(activity.cacheDir, "selection-bound-model-actions.stl")
        val beforeGeometryRevision = AtomicReference<Long>()
        try {
            activity.assets.open("models/pyramid.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            loadSingleTestModel(bed, modelFile)
            val prepared = CountDownLatch(1)
            glView.queueEvent {
                val renderer = glView.renderer
                val model = requireNotNull(renderer.model)
                model.addObject(model, 0)
                model.translate(0, -60.0, -30.0, 8.0)
                model.translate(1, 55.0, 28.0, 16.0)
                renderer.setSelectedObject(0)
                renderer.setSelectedObjects(intArrayOf(0, 1))
                beforeGeometryRevision.set(model.geometryRevision)
                glView.requestRender()
                prepared.countDown()
            }
            assertTrue("two positioned objects should be prepared", prepared.await(10, TimeUnit.SECONDS))
            val before = readExactBoundsOnGl(glView, 0, 1)

            val centerResult = AtomicReference<Int>()
            val centerDone = CountDownLatch(1)
            glView.queueEvent {
                val renderer = glView.renderer
                val snapshot = requireNotNull(renderer.captureSelectionSnapshot())
                renderer.setSelectedObject(1)
                glView.centerSelectedOnBed(snapshot) { result ->
                    centerResult.set(result)
                    centerDone.countDown()
                }
            }
            assertTrue("center operation should complete", centerDone.await(10, TimeUnit.SECONDS))
            assertEquals(MODEL_EDIT_STALE_SELECTION, centerResult.get())

            val orientResult = AtomicReference<Int>()
            val orientDone = CountDownLatch(1)
            glView.queueEvent {
                val renderer = glView.renderer
                renderer.setSelectedObjects(intArrayOf(0, 1))
                renderer.setSelectedObject(0)
                val snapshot = requireNotNull(renderer.captureSelectionSnapshot())
                renderer.setSelectedObject(1)
                glView.autoOrientSelectedObjects(snapshot) { result ->
                    orientResult.set(result)
                    orientDone.countDown()
                }
            }
            assertTrue("auto-orient operation should complete", orientDone.await(10, TimeUnit.SECONDS))
            assertEquals(MODEL_EDIT_STALE_SELECTION, orientResult.get())

            val after = readExactBoundsOnGl(glView, 0, 1)
            before.zip(after).forEachIndexed { index, (expected, actual) ->
                assertArrayEquals("stale selection must leave object $index unchanged", expected, actual, 0.02)
            }
            val afterGeometryRevision = AtomicReference<Long>()
            val revisionRead = CountDownLatch(1)
            glView.queueEvent {
                afterGeometryRevision.set(requireNotNull(glView.renderer.model).geometryRevision)
                revisionRead.countDown()
            }
            assertTrue("geometry revision should be readable on the GL thread", revisionRead.await(10, TimeUnit.SECONDS))
            assertEquals("stale center/auto-orient requests must not mutate geometry",
                beforeGeometryRevision.get(), afterGeometryRevision.get())
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun singleObjectBatchDuplicateCreatesRequestedCopiesWithUndoRedo() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val modelFile = File(activity.cacheDir, "single-object-batch-duplicate-test.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            val model = loadSingleTestModel(bed, modelFile)
            val prepared = CountDownLatch(1)
            glView.queueEvent {
                model.setUniformFilament(0, 2)
                glView.renderer.setSelectedObject(0)
                prepared.countDown()
            }
            assertTrue("single object should be selected on GL thread", prepared.await(10, TimeUnit.SECONDS))
            val added = AtomicReference<Int>()
            val completed = CountDownLatch(1)
            glView.duplicateSelectedModels(3) { copies ->
                added.set(copies)
                if (copies > 0) bed.updateModel()
                completed.countDown()
            }
            assertTrue("batch duplicate should complete", completed.await(20, TimeUnit.SECONDS))
            assertEquals("the requested number of copies should be created in one transaction", 3, added.get())
            assertEquals(4, readObjectCountOnGl(glView))
            (0..3).forEach { index ->
                assertEquals("each copy preserves the assigned source filament", listOf(2),
                    readUsedFilamentsOnGl(glView, index).toList())
            }

            compose.runOnUiThread { bed.undoModelEdit() }
            instrumentationWaitForGlIdle()
            assertEquals("undo removes the whole duplicate batch atomically", 1, readObjectCountOnGl(glView))
            compose.runOnUiThread { bed.redoModelEdit() }
            instrumentationWaitForGlIdle()
            assertEquals("redo restores the complete batch", 4, readObjectCountOnGl(glView))
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun failedGroupDuplicateRestoresAllObjectsAndFilamentAssignments() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val modelFile = File(activity.cacheDir, "group-duplicate-no-room-test.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            val model = loadSingleTestModel(bed, modelFile)
            val prepared = CountDownLatch(1)
            glView.queueEvent {
                val extra = Model()
                try {
                    extra.addObject(model, 0)
                    model.addObject(extra, 0)
                } finally {
                    extra.release()
                }
                model.translate(1, 18.0, 11.0, 6.0)
                model.scale(1, 40.0, 40.0, 1.0)
                model.setUniformFilament(0, 2)
                model.setUniformFilament(1, 3)
                glView.renderer.invalidateGlModel(0)
                glView.renderer.invalidateGlModel(1)
                glView.renderer.setSelectedObjects(intArrayOf(0, 1))
                glView.requestRender()
                prepared.countDown()
            }
            assertTrue("unarrangeable group should be prepared on GL thread", prepared.await(10, TimeUnit.SECONDS))
            instrumentationWaitForGlIdle()
            val beforeBounds = (0..1).map { readExactBoundsOnGl(glView, it).single() }
            val beforeFilaments = (0..1).map { readUsedFilamentsOnGl(glView, it).toList() }
            val copies = AtomicReference<Int>()
            val done = CountDownLatch(1)
            glView.queueEvent {
                copies.set(glView.renderer.duplicateSelectedObjects(2))
                glView.requestRender()
                done.countDown()
            }
            assertTrue("group duplicate should return from GL thread", done.await(15, TimeUnit.SECONDS))
            assertEquals("an unarrangeable group batch must create no partial copies", 0, copies.get())
            assertEquals(2, readObjectCountOnGl(glView))
            (0..1).forEach { index ->
                assertArrayEquals("group rollback must restore object $index", beforeBounds[index],
                    readExactBoundsOnGl(glView, index).single(), 0.03)
                assertEquals("group rollback must preserve object $index filament", beforeFilaments[index],
                    readUsedFilamentsOnGl(glView, index).toList())
            }
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun duplicateBatchDoesNotKeepPartialCopiesWhenArrangeCannotFitTheModel() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val modelFile = File(activity.cacheDir, "model-menu-duplicate-no-room-test.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            val model = loadSingleTestModel(bed, modelFile)
            val prepared = CountDownLatch(1)
            glView.queueEvent {
                model.scale(0, 40.0, 40.0, 1.0)
                glView.renderer.invalidateGlModel(0)
                glView.renderer.setSelectedObject(0)
                glView.requestRender()
                prepared.countDown()
            }
            assertTrue("oversized source should be prepared on GL thread", prepared.await(10, TimeUnit.SECONDS))
            compose.runOnUiThread { bed.updateModel() }
            instrumentationWaitForGlIdle()
            val before = readExactBoundsOnGl(glView, 0).single()

            openModelMenu(activity)
            selectSingleObjectScope()
            revealMenuAction(activity, R.string.ModelMenuDuplicate)
            compose.onNodeWithText(activity.getString(R.string.ModelMenuDuplicateShort))
                .assertIsDisplayed().assertIsEnabled().performClick()
            compose.onNodeWithTag("duplicate-model-count").performTextClearance()
            compose.onNodeWithTag("duplicate-model-count").performTextInput("3")
            compose.onNodeWithTag("duplicate-model-confirm").performClick()

            instrumentationWaitForGlIdle()
            assertEquals("a failed batch must roll back every requested copy", 1, readObjectCountOnGl(glView))
            assertArrayEquals("the failed operation must preserve the source geometry and position",
                before, readExactBoundsOnGl(glView, 0).single(), 0.03)
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun failedContextDuplicateKeepsRedoHistoryIntact() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val modelFile = File(activity.cacheDir, "model-context-duplicate-redo-test.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            loadSingleTestModel(bed, modelFile)
            glView.selectModel(0, Runnable {})
            instrumentationWaitForGlIdle()

            val duplicateCount = AtomicReference<Int>()
            val duplicated = CountDownLatch(1)
            glView.duplicateSelectedModelWithResult { count ->
                duplicateCount.set(count)
                if (count > 0) bed.updateModel()
                duplicated.countDown()
            }
            assertTrue("successful duplicate should finish", duplicated.await(15, TimeUnit.SECONDS))
            assertEquals(1, duplicateCount.get())
            assertEquals(2, readObjectCountOnGl(glView))
            compose.runOnUiThread { bed.undoModelEdit() }
            instrumentationWaitForGlIdle()
            assertEquals("undo should restore the original model", 1, readObjectCountOnGl(glView))

            val prepared = CountDownLatch(1)
            glView.queueEvent {
                val current = requireNotNull(glView.renderer.model)
                current.scale(0, 40.0, 40.0, 1.0)
                glView.renderer.invalidateGlModel(0)
                glView.renderer.setSelectedObject(0)
                glView.requestRender()
                prepared.countDown()
            }
            assertTrue("oversized model should be prepared", prepared.await(10, TimeUnit.SECONDS))
            compose.runOnUiThread { bed.showSelectedModelContextMenu() }
            compose.onNodeWithText(activity.getString(R.string.ModelContextDuplicate))
                .assertIsDisplayed().performClick()
            instrumentationWaitForGlIdle()
            Thread.sleep(300)
            assertEquals("failed duplicate must not add an object", 1, readObjectCountOnGl(glView))

            compose.runOnUiThread { bed.redoModelEdit() }
            instrumentationWaitForGlIdle()
            assertEquals("failed duplicate must not clear the previous redo snapshot", 2,
                readObjectCountOnGl(glView))
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun fillBedRejectsOversizedObjectWithoutChangingItsPlacement() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val modelFile = File(activity.cacheDir, "model-menu-fill-bed-no-room-test.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            val model = loadSingleTestModel(bed, modelFile)
            val prepared = CountDownLatch(1)
            glView.queueEvent {
                model.scale(0, 40.0, 40.0, 1.0)
                glView.renderer.invalidateGlModel(0)
                glView.renderer.setSelectedObject(0)
                glView.requestRender()
                prepared.countDown()
            }
            assertTrue("oversized source should be prepared on GL thread", prepared.await(10, TimeUnit.SECONDS))
            compose.runOnUiThread { bed.updateModel() }
            instrumentationWaitForGlIdle()
            val before = readExactBoundsOnGl(glView, 0).single()

            openModelMenu(activity)
            revealMenuAction(activity, R.string.ModelMenuFillBed)
            compose.onNodeWithText(activity.getString(R.string.ModelMenuFillBedShort))
                .assertIsDisplayed().assertIsEnabled().performClick()

            compose.waitUntil(timeoutMillis = 30_000) { !glView.isFillBedInProgress }
            instrumentationWaitForGlIdle()
            assertEquals("an oversized object must not produce speculative copies", 1, readObjectCountOnGl(glView))
            assertArrayEquals("a rejected fill action must preserve the source geometry and position",
                before, readExactBoundsOnGl(glView, 0).single(), 0.03)
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun fillBedCopiesPreserveFilamentsAndDistinctPlacementsWithUndoRedo() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val modelFile = File(activity.cacheDir, "model-menu-fill-bed-positive-test.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            val model = loadSingleTestModel(bed, modelFile)
            val selectionReady = CountDownLatch(1)
            glView.queueEvent {
                model.setUniformFilament(0, 2)
                glView.renderer.setSelectedObject(0)
                glView.requestRender()
                selectionReady.countDown()
            }
            assertTrue("source object should be selected on the GL thread", selectionReady.await(10, TimeUnit.SECONDS))
            compose.runOnUiThread { bed.updateModel() }
            instrumentationWaitForGlIdle()
            assertEquals("fixture must start with one object", 1, readObjectCountOnGl(glView))

            openModelMenu(activity)
            revealMenuAction(activity, R.string.ModelMenuFillBed)
            compose.onNodeWithText(activity.getString(R.string.ModelMenuFillBedShort))
                .performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()

            compose.waitUntil(timeoutMillis = 60_000) { !glView.isFillBedInProgress }
            instrumentationWaitForGlIdle()
            val filledCount = readObjectCountOnGl(glView)
            assertTrue("fill-bed must use remaining printable plate area", filledCount > 1)
            (0 until filledCount).forEach { index ->
                assertArrayEquals("copy $index must preserve the source filament assignment",
                    intArrayOf(2), readUsedFilamentsOnGl(glView, index))
            }
            val uniquePlacements = (0 until filledCount).map { index ->
                val translation = readTranslationOnGl(glView, index)
                listOf(translation.x, translation.y, translation.z)
            }.distinct()
            assertEquals("every copy should be arranged at a distinct position", filledCount,
                uniquePlacements.size)

            compose.runOnUiThread { bed.undoModelEdit() }
            instrumentationWaitForGlIdle()
            assertEquals("Undo must restore the single source object", 1, readObjectCountOnGl(glView))
            compose.runOnUiThread { bed.redoModelEdit() }
            instrumentationWaitForGlIdle()
            assertEquals("Redo must restore the completed plate arrangement", filledCount,
                readObjectCountOnGl(glView))
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun fillBedArrangeFailureRollsBackCopiesAndExistingPositions() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val modelFile = File(activity.cacheDir, "model-menu-fill-bed-arrange-rollback-test.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            val model = loadSingleTestModel(bed, modelFile)
            val prepared = CountDownLatch(1)
            glView.queueEvent {
                val blocker = Model()
                try {
                    blocker.addObject(model, 0)
                    model.addObject(blocker, 0)
                } finally {
                    blocker.release()
                }
                // Make the blocker larger than the active bed even if the imported STL
                // carries a non-unit base scale; the preflight must reject it before native pack.
                model.scale(1, 400.0, 400.0, 1.0)
                glView.renderer.invalidateGlModel(0)
                glView.renderer.invalidateGlModel(1)
                glView.renderer.setSelectedObject(0)
                glView.requestRender()
                prepared.countDown()
            }
            assertTrue("crowded plate should be prepared on GL thread", prepared.await(10, TimeUnit.SECONDS))
            compose.runOnUiThread { bed.updateModel() }
            instrumentationWaitForGlIdle()
            val before = (0..1).map { readExactBoundsOnGl(glView, it).single() }

            openModelMenu(activity)
            revealMenuAction(activity, R.string.ModelMenuFillBed)
            compose.onNodeWithText(activity.getString(R.string.ModelMenuFillBedShort))
                .performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()

            compose.waitUntil(timeoutMillis = 30_000) { !glView.isFillBedInProgress }
            instrumentationWaitForGlIdle()
            assertEquals("a failed native arrange must not leave speculative copies", 2, readObjectCountOnGl(glView))
            (0..1).forEach { index ->
                assertArrayEquals("failed arrange must restore object $index", before[index],
                    readExactBoundsOnGl(glView, index).single(), 0.03)
            }
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun arrangeFailureRestoresEveryObjectAndDoesNotCommitAChangedLayout() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val modelFile = File(activity.cacheDir, "model-arrange-rollback-test.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            val model = loadSingleTestModel(bed, modelFile)
            val prepared = CountDownLatch(1)
            glView.queueEvent {
                val extra = Model()
                try {
                    extra.addObject(model, 0)
                    model.addObject(extra, 0)
                } finally {
                    extra.release()
                }
                model.translate(0, 29.0, 17.0, 4.0)
                model.translate(1, -24.0, -13.0, 9.0)
                model.scale(1, 40.0, 40.0, 1.0)
                glView.renderer.invalidateGlModel(0)
                glView.renderer.invalidateGlModel(1)
                glView.requestRender()
                prepared.countDown()
            }
            assertTrue("unarrangeable plate should be prepared on GL thread", prepared.await(10, TimeUnit.SECONDS))
            instrumentationWaitForGlIdle()
            val before = (0..1).map { readExactBoundsOnGl(glView, it).single() }
            val arranged = AtomicReference<Boolean>()
            val done = CountDownLatch(1)
            glView.queueEvent {
                arranged.set(glView.renderer.arrangeObjects())
                glView.requestRender()
                done.countDown()
            }
            assertTrue("arrange result should return from GL thread", done.await(10, TimeUnit.SECONDS))
            assertEquals("oversized blocker should reject the full arrangement", false, arranged.get())
            assertEquals(2, readObjectCountOnGl(glView))
            (0..1).forEach { index ->
                assertArrayEquals("failed arrange must keep object $index unchanged", before[index],
                    readExactBoundsOnGl(glView, index).single(), 0.03)
            }
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun importedObjectsMoveToAutomaticallyCreatedPlateWhenCurrentPlateIsFull() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        compose.waitUntil(timeoutMillis = 15_000) {
            glView.isAttachedToWindow && glView.width > 0 && glView.height > 0 &&
                glView.renderer.bed != null
        }
        val modelFile = File(activity.cacheDir, "auto-plate-import-140mm.stl")
        try {
            writeScaledCubeStl(activity, modelFile, 7f)
            fun importAndWait() {
                val completed = CountDownLatch(1)
                val failure = AtomicReference<Exception?>()
                bed.loadModel(modelFile, false, 1, object : BedFragment.ModelLoadCallback {
                    override fun onLoaded(loadedModel: Model, firstNewObject: Int, addedObjects: Int) {
                        completed.countDown()
                    }

                    override fun onFailed(error: Exception) {
                        failure.set(error)
                        completed.countDown()
                    }
                })
                assertTrue("model import should finish", completed.await(30, TimeUnit.SECONDS))
                failure.get()?.let { throw AssertionError("model import failed", it) }
                compose.waitUntil(timeoutMillis = 10_000) {
                    !BedFragment::class.java.getDeclaredField("modelLoadInProgress")
                        .apply { isAccessible = true }.getBoolean(bed)
                }
            }

            importAndWait()
            assertEquals("first 140 mm object should use the initial plate", 0, bed.activePlateIndex)
            val initialModel = readPlateModels(bed).first()
            assertEquals(1, initialModel.objectsCount)
            val initialBounds = readExactBoundsOnGl(glView, 0).single()

            importAndWait()
            compose.waitUntil(timeoutMillis = 10_000) { readPlateModels(bed).size == 2 }
            assertEquals("the overflow import should select the newly created plate", 1, bed.activePlateIndex)
            importAndWait()
            compose.waitUntil(timeoutMillis = 10_000) { readPlateModels(bed).size == 3 }
            assertEquals("each additional oversized import should advance to a new plate", 2, bed.activePlateIndex)
            val models = readPlateModels(bed)
            assertEquals("the original plate must keep its object", 1, models[0].objectsCount)
            assertEquals("the second imported object should remain on plate two", 1, models[1].objectsCount)
            assertEquals("the third imported object should be placed on plate three", 1, models[2].objectsCount)
            assertArrayEquals("failed first-plate packing must not shift the existing object", initialBounds,
                readExactBoundsOnGl(glView, models[0], 0), 0.03)
            val placedBounds = readExactBoundsOnGl(glView, 0).single()
            val volumeMin = glView.renderer.bed?.getVolumeMin() ?: error("bed bounds unavailable")
            val volumeMax = glView.renderer.bed?.getVolumeMax() ?: error("bed bounds unavailable")
            assertTrue("auto-placed object must be inside the active bed X bounds",
                placedBounds[0] >= volumeMin.x - 0.1 && placedBounds[3] <= volumeMax.x + 0.1)
            assertTrue("auto-placed object must be inside the active bed Y bounds",
                placedBounds[1] >= volumeMin.y - 0.1 && placedBounds[4] <= volumeMax.y + 0.1)
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun importedObjectsStayFreelyMovableUntilUserRequestsArrange() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        compose.waitUntil(timeoutMillis = 15_000) {
            glView.isAttachedToWindow && glView.width > 0 && glView.height > 0 &&
                glView.renderer.bed != null
        }
        val firstFile = File(activity.cacheDir, "free-move-import-a-${System.nanoTime()}.stl")
        val secondFile = File(activity.cacheDir, "free-move-import-b-${System.nanoTime()}.stl")
        try {
            writeScaledCubeStl(activity, firstFile, 0.5f)
            writeScaledCubeStl(activity, secondFile, 0.5f)

            fun importAndWait(file: File) {
                val completed = CountDownLatch(1)
                val failure = AtomicReference<Exception?>()
                bed.loadModel(file, preserveProjectLayout = false, plateCount = 1,
                    callback = object : BedFragment.ModelLoadCallback {
                        override fun onLoaded(loadedModel: Model, firstNewObject: Int, addedObjects: Int) {
                            completed.countDown()
                        }

                        override fun onFailed(error: Exception) {
                            failure.set(error)
                            completed.countDown()
                        }
                    }, autoOrientImportedModels = true)
                assertTrue("model import should finish", completed.await(30, TimeUnit.SECONDS))
                failure.get()?.let { throw AssertionError("model import failed", it) }
                compose.waitUntil(timeoutMillis = 10_000) {
                    !BedFragment::class.java.getDeclaredField("modelLoadInProgress")
                        .apply { isAccessible = true }.getBoolean(bed)
                }
            }

            importAndWait(firstFile)
            importAndWait(secondFile)
            val model = requireNotNull(bed.currentModel)
            assertEquals("both small models should fit on the same plate", 2, model.objectsCount)
            val beforeMove = readExactBoundsOnGl(glView, 0, 1)

            val moveCompleted = CountDownLatch(1)
            glView.queueEvent {
                model.translate(0, 80.0, 40.0, 0.0)
                model.resetBoundingBox()
                glView.requestRender()
                moveCompleted.countDown()
            }
            assertTrue("manual object move should finish on the GL thread",
                moveCompleted.await(10, TimeUnit.SECONDS))
            val afterMove = readExactBoundsOnGl(glView, 0, 1)
            assertFalse("the selected object should keep its manually moved position",
                beforeMove[0].contentEquals(afterMove[0]))
            assertArrayEquals("moving one object must not automatically repack its neighbor",
                beforeMove[1], afterMove[1], 0.03)

            val arrangeCompleted = CountDownLatch(1)
            val arrangeResult = AtomicReference<Boolean>()
            glView.arrange { arranged ->
                arrangeResult.set(arranged)
                arrangeCompleted.countDown()
            }
            assertTrue("explicit Arrange action should complete", arrangeCompleted.await(15, TimeUnit.SECONDS))
            assertTrue("explicit Arrange action should bring the pair back into the active bed",
                arrangeResult.get() == true)
            val arranged = readExactBoundsOnGl(glView, 0, 1)
            assertFalse("explicit Arrange should separate the two model bounds",
                arranged[0][0] < arranged[1][3] && arranged[0][3] > arranged[1][0] &&
                    arranged[0][1] < arranged[1][4] && arranged[0][4] > arranged[1][1])
            val bedMin = requireNotNull(glView.renderer.bed?.getVolumeMin())
            val bedMax = requireNotNull(glView.renderer.bed?.getVolumeMax())
            arranged.forEachIndexed { index, bounds ->
                assertTrue("explicit Arrange should place object $index inside the bed",
                    bounds[0] >= bedMin.x - 0.1 && bounds[1] >= bedMin.y - 0.1 &&
                        bounds[3] <= bedMax.x + 0.1 && bounds[4] <= bedMax.y + 0.1)
            }
        } finally {
            firstFile.delete()
            secondFile.delete()
        }
    }

    @Test
    fun arrangeCompactsSeveralManuallyMovedModelsInOneAction() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        compose.waitUntil(timeoutMillis = 15_000) {
            glView.isAttachedToWindow && glView.width > 0 && glView.height > 0 &&
                glView.renderer.bed != null
        }
        val modelFiles = (0 until 4).map { index ->
            File(activity.cacheDir, "multi-arrange-moved-$index-${System.nanoTime()}.stl")
        }
        try {
            modelFiles.forEach { file ->
                writeScaledCubeStl(activity, file, 0.5f)
                val completed = CountDownLatch(1)
                val failure = AtomicReference<Exception?>()
                bed.loadModel(file, preserveProjectLayout = false, plateCount = 1,
                    callback = object : BedFragment.ModelLoadCallback {
                        override fun onLoaded(loadedModel: Model, firstNewObject: Int, addedObjects: Int) {
                            completed.countDown()
                        }

                        override fun onFailed(error: Exception) {
                            failure.set(error)
                            completed.countDown()
                        }
                    }, autoOrientImportedModels = true)
                assertTrue("all four test models must import", completed.await(30, TimeUnit.SECONDS))
                failure.get()?.let { throw AssertionError("model import failed", it) }
                compose.waitUntil(timeoutMillis = 10_000) {
                    !BedFragment::class.java.getDeclaredField("modelLoadInProgress")
                        .apply { isAccessible = true }.getBoolean(bed)
                }
            }

            val model = requireNotNull(bed.currentModel)
            assertEquals("fixture must contain four separate models", 4, model.objectsCount)
            val beforeMove = readExactBoundsOnGl(glView, 0, 1, 2, 3)
            val bedMin = requireNotNull(glView.renderer.bed?.getVolumeMin())
            val bedMax = requireNotNull(glView.renderer.bed?.getVolumeMax())
            val targetCenters = listOf(
                bedMin.x - 25.0 to bedMin.y - 25.0,
                bedMax.x + 25.0 to bedMin.y - 25.0,
                bedMin.x - 25.0 to bedMax.y + 25.0,
                bedMax.x + 25.0 to bedMax.y + 25.0,
            )
            val moveCompleted = CountDownLatch(1)
            glView.queueEvent {
                beforeMove.forEachIndexed { index, bounds ->
                    val currentCenterX = (bounds[0] + bounds[3]) / 2.0
                    val currentCenterY = (bounds[1] + bounds[4]) / 2.0
                    val target = targetCenters[index]
                    model.translate(index, target.first - currentCenterX, target.second - currentCenterY, 0.0)
                }
                model.resetBoundingBox()
                glView.requestRender()
                moveCompleted.countDown()
            }
            assertTrue("manual moves must finish on the GL thread", moveCompleted.await(10, TimeUnit.SECONDS))
            val movedBounds = readExactBoundsOnGl(glView, 0, 1, 2, 3)
            assertTrue("the fixture must reproduce multiple manually displaced objects",
                movedBounds.zip(beforeMove).all { (moved, before) -> !moved.contentEquals(before) })
            assertTrue("all four models must be outside the plate before Arrange",
                movedBounds.all { bounds -> bounds[0] < bedMin.x || bounds[1] < bedMin.y ||
                    bounds[3] > bedMax.x || bounds[4] > bedMax.y })
            val movedWidth = movedBounds.maxOf { it[3] } - movedBounds.minOf { it[0] }
            val movedDepth = movedBounds.maxOf { it[4] } - movedBounds.minOf { it[1] }
            assertTrue("models must start spread to the plate corners", movedWidth > (bedMax.x - bedMin.x) * 0.75 &&
                movedDepth > (bedMax.y - bedMin.y) * 0.75)

            compose.runOnUiThread { bed.updateModel() }
            openModelMenu(activity)
            selectAllObjectsScope()
            compose.onNodeWithTag("workspace-menu-action-${R.string.ModelMenuArrange}")
                .performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()

            compose.waitUntil(timeoutMillis = 20_000) {
                val current = readExactBoundsOnGl(glView, 0, 1, 2, 3)
                val packedWidth = current.maxOf { it[3] } - current.minOf { it[0] }
                val packedDepth = current.maxOf { it[4] } - current.minOf { it[1] }
                packedWidth < movedWidth * 0.75 && packedDepth < movedDepth * 0.75
            }
            val arranged = readExactBoundsOnGl(glView, 0, 1, 2, 3)
            assertEquals("Arrange must preserve all four models", 4, readObjectCountOnGl(glView))
            val packedWidth = arranged.maxOf { it[3] } - arranged.minOf { it[0] }
            val packedDepth = arranged.maxOf { it[4] } - arranged.minOf { it[1] }
            assertTrue("Arrange must compact the manually spread models instead of leaving them where moved",
                packedWidth < movedWidth * 0.75 && packedDepth < movedDepth * 0.75)
            arranged.forEachIndexed { index, bounds ->
                assertTrue("arranged model $index must be inside the active plate",
                    bounds[0] >= bedMin.x - 0.1 && bounds[1] >= bedMin.y - 0.1 &&
                        bounds[3] <= bedMax.x + 0.1 && bounds[4] <= bedMax.y + 0.1)
            }
            for (first in arranged.indices) for (second in first + 1 until arranged.size) {
                val overlaps = arranged[first][0] < arranged[second][3] &&
                    arranged[first][3] > arranged[second][0] &&
                    arranged[first][1] < arranged[second][4] &&
                    arranged[first][4] > arranged[second][1]
                assertFalse("Arrange must separate models $first and $second", overlaps)
            }
        } finally {
            modelFiles.forEach(File::delete)
        }
    }

    @Test(timeout = 180_000)
    fun arrangeCompactsSixModelsMovedOutsideBed() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        compose.waitUntil(timeoutMillis = 15_000) {
            glView.isAttachedToWindow && glView.width > 0 && glView.height > 0 &&
                glView.renderer.bed != null
        }
        val modelFiles = (0 until 6).map { index ->
            File(activity.cacheDir, "arrange-real-mesh-$index-${System.nanoTime()}.stl").also { file ->
                // Keep the Arrange regression independent of optional/sample assets and
                // make each imported object a separately identifiable real STL mesh.
                writeScaledCubeStl(activity, file, 0.35f + index * 0.08f)
            }
        }
        try {
            modelFiles.forEachIndexed { index, file ->
                val completed = CountDownLatch(1)
                val failure = AtomicReference<Exception?>()
                bed.loadModel(file, preserveProjectLayout = false, plateCount = 1,
                    callback = object : BedFragment.ModelLoadCallback {
                        override fun onLoaded(loadedModel: Model, firstNewObject: Int, addedObjects: Int) {
                            completed.countDown()
                        }
                        override fun onFailed(error: Exception) {
                            failure.set(error)
                            completed.countDown()
                        }
                    }, autoOrientImportedModels = true)
                assertTrue("real mesh ${index + 1}/${modelFiles.size} must import", completed.await(45, TimeUnit.SECONDS))
                failure.get()?.let { throw AssertionError("Could not import ${file.name}", it) }
                compose.waitUntil(timeoutMillis = 10_000) {
                    !BedFragment::class.java.getDeclaredField("modelLoadInProgress")
                        .apply { isAccessible = true }.getBoolean(bed)
                }
            }

            val model = requireNotNull(bed.currentModel)
            assertEquals("fixture must contain six distinct real meshes", 6, model.objectsCount)
            val indices = (0 until 6).toList()
            val before = readExactBoundsOnGl(glView, *indices.toIntArray())
            val bedMin = requireNotNull(glView.renderer.bed?.getVolumeMin())
            val bedMax = requireNotNull(glView.renderer.bed?.getVolumeMax())
            val targets = before.mapIndexed { index, bounds ->
                val halfWidth = (bounds[3] - bounds[0]) / 2.0
                val halfDepth = (bounds[4] - bounds[1]) / 2.0
                when (index) {
                    0, 4 -> bedMin.x - halfWidth - 12.0 to
                        (bedMin.y + bedMax.y) / 2.0 + if (index == 0) -24.0 else 24.0
                    1, 3 -> bedMax.x + halfWidth + 12.0 to
                        (bedMin.y + bedMax.y) / 2.0 + if (index == 1) -24.0 else 24.0
                    2 -> (bedMin.x + bedMax.x) / 2.0 - 24.0 to bedMin.y - halfDepth - 12.0
                    else -> (bedMin.x + bedMax.x) / 2.0 + 24.0 to bedMax.y + halfDepth + 12.0
                }
            }
            val moved = CountDownLatch(1)
            glView.queueEvent {
                before.forEachIndexed { index, bounds ->
                    val x = (bounds[0] + bounds[3]) / 2.0
                    val y = (bounds[1] + bounds[4]) / 2.0
                    model.translate(index, targets[index].first - x, targets[index].second - y, 0.0)
                }
                model.resetBoundingBox()
                glView.requestRender()
                moved.countDown()
            }
            assertTrue("manual moves must complete", moved.await(10, TimeUnit.SECONDS))
            val movedBounds = readExactBoundsOnGl(glView, *indices.toIntArray())
            assertTrue("all six real meshes must start completely outside the build plate",
                movedBounds.all { bounds -> bounds[0] < bedMin.x || bounds[3] > bedMax.x ||
                    bounds[1] < bedMin.y || bounds[4] > bedMax.y })

            val previewRevisionField = BedFragment::class.java.getDeclaredField("platePreviewRevision")
                .apply { isAccessible = true }
            val previewRevisionBefore = previewRevisionField.getInt(bed)
            openModelMenu(activity)
            selectAllObjectsScope()
            compose.onNodeWithText(activity.getString(R.string.ModelMenuArrangeShort))
                .performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                val bounds = readExactBoundsOnGl(glView, *indices.toIntArray())
                val width = bounds.maxOf { it[3] } - bounds.minOf { it[0] }
                val depth = bounds.maxOf { it[4] } - bounds.minOf { it[1] }
                width < (bedMax.x - bedMin.x) * 0.9 && depth < (bedMax.y - bedMin.y) * 0.9 &&
                    previewRevisionField.getInt(bed) > previewRevisionBefore
            }
            assertEquals("Arrange must keep these six models on one plate", 1, bed.plateCount)
            val after = readExactBoundsOnGl(glView, *indices.toIntArray())
            after.forEachIndexed { index, bounds ->
                assertTrue("real mesh $index must remain on the active build plate",
                    bounds[0] >= bedMin.x - 0.1 && bounds[1] >= bedMin.y - 0.1 &&
                        bounds[3] <= bedMax.x + 0.1 && bounds[4] <= bedMax.y + 0.1)
            }
        } finally {
            modelFiles.forEach(File::delete)
        }
    }

    @Test
    fun multiFileImportCreatesPlatesAndPreservesExistingModelsWhenBedFills() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        compose.waitUntil(timeoutMillis = 15_000) {
            glView.isAttachedToWindow && glView.width > 0 && glView.height > 0 &&
                glView.renderer.bed != null
        }
        val initialFile = File(activity.cacheDir, "multi-overflow-initial-${System.nanoTime()}.stl")
        val firstFile = File(activity.cacheDir, "multi-overflow-a-${System.nanoTime()}.stl")
        val secondFile = File(activity.cacheDir, "multi-overflow-b-${System.nanoTime()}.stl")
        try {
            // Each 140 mm cube occupies most of the test printer's 255 x 255 mm bed.
            // The initial cube must remain on plate one; the two selected files must then
            // be imported in order onto automatically created plates two and three.
            writeScaledCubeStl(activity, initialFile, 7f)
            writeScaledCubeStl(activity, firstFile, 7f)
            writeScaledCubeStl(activity, secondFile, 7f)

            val initialImport = CountDownLatch(1)
            val initialFailure = AtomicReference<Exception?>()
            bed.loadModel(initialFile, false, 1, object : BedFragment.ModelLoadCallback {
                override fun onLoaded(loadedModel: Model, firstNewObject: Int, addedObjects: Int) {
                    initialImport.countDown()
                }

                override fun onFailed(error: Exception) {
                    initialFailure.set(error)
                    initialImport.countDown()
                }
            })
            assertTrue("the initial model should load", initialImport.await(30, TimeUnit.SECONDS))
            initialFailure.get()?.let { throw AssertionError("initial import failed", it) }
            compose.waitUntil(timeoutMillis = 10_000) { readPlateModels(bed).size == 1 }
            val originalPlateBounds = readExactBoundsOnGl(glView, 0).single()

            val provider = activity.packageManager.getPackageInfo(
                activity.packageName,
                PackageManager.GET_PROVIDERS,
            ).providers.orEmpty().first { it.name == FileProvider::class.java.name }
            val authority = requireNotNull(provider.authority).substringBefore(';')
            val uris = listOf(firstFile, secondFile).map { file ->
                FileProvider.getUriForFile(activity, authority, file)
            }
            MainActivity::class.java.getDeclaredMethod("loadMultipleStlFiles", List::class.java)
                .apply { isAccessible = true }
                .invoke(activity, uris)

            compose.waitUntil(timeoutMillis = 60_000) {
                readPlateModels(bed).size == 3 &&
                    !BedFragment::class.java.getDeclaredField("modelLoadInProgress")
                        .apply { isAccessible = true }.getBoolean(bed)
            }
            val plates = readPlateModels(bed)
            assertEquals("every large STL should be retained on a bed", listOf(1, 1, 1),
                plates.map { it.objectsCount })
            assertEquals("the last imported model should be shown on the newest plate", 2,
                bed.activePlateIndex)
            assertArrayEquals("automatic overflow must not move the existing model", originalPlateBounds,
                readExactBoundsOnGl(glView, plates.first(), 0), 0.03)

            val bedMin = requireNotNull(glView.renderer.bed?.getVolumeMin())
            val bedMax = requireNotNull(glView.renderer.bed?.getVolumeMax())
            plates.forEachIndexed { plateIndex, model ->
                val bounds = readExactBoundsOnGl(glView, model, 0)
                assertTrue("model on plate ${plateIndex + 1} must stay inside the bed",
                    bounds[0] >= bedMin.x - 0.1 && bounds[1] >= bedMin.y - 0.1 &&
                        bounds[3] <= bedMax.x + 0.1 && bounds[4] <= bedMax.y + 0.1)
                assertTrue("model on plate ${plateIndex + 1} must rest on the build surface",
                    kotlin.math.abs(bounds[2]) <= 0.1)
            }
        } finally {
            initialFile.delete()
            firstFile.delete()
            secondFile.delete()
        }
    }

    @Test
    fun batchStlImportRetainsValidFilesWhenOneFileFails() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        compose.waitUntil(timeoutMillis = 15_000) {
            glView.isAttachedToWindow && glView.width > 0 && glView.height > 0 &&
                glView.renderer.bed != null
        }
        val validFile = File(activity.cacheDir, "batch-partial-valid-${System.nanoTime()}.stl")
        val invalidFile = File(activity.cacheDir, "batch-partial-invalid-${System.nanoTime()}.stl")
        try {
            writeScaledCubeStl(activity, validFile, 0.5f)
            invalidFile.writeText("not an STL mesh")
            val completed = CountDownLatch(1)
            val failure = AtomicReference<Exception?>()
            val partialFailures = AtomicReference(-1)
            val importedCount = AtomicReference(0)
            bed.loadModelBatch(listOf(invalidFile, validFile), object : BedFragment.ModelLoadCallback {
                override fun onPartialFailures(failedFiles: Int) {
                    partialFailures.set(failedFiles)
                }

                override fun onLoaded(loadedModel: Model, firstNewObject: Int, addedObjects: Int) {
                    importedCount.set(addedObjects)
                    completed.countDown()
                }

                override fun onFailed(error: Exception) {
                    failure.set(error)
                    completed.countDown()
                }
            }, autoOrientImportedModels = true)
            assertTrue("the valid STL should complete the batch despite one invalid file",
                completed.await(45, TimeUnit.SECONDS))
            failure.get()?.let { throw AssertionError("batch import failed", it) }
            assertEquals("the bad source should be reported once", 1, partialFailures.get())
            assertEquals("the valid source should still be imported", 1, importedCount.get())
            assertEquals("only the valid model should be on the bed", 1,
                readPlateModels(bed).sumOf { it.objectsCount })
            val bounds = readExactBoundsOnGl(glView, 0).single()
            val bedMin = requireNotNull(glView.renderer.bed?.getVolumeMin())
            val bedMax = requireNotNull(glView.renderer.bed?.getVolumeMax())
            assertTrue("the surviving model remains inside the bed",
                bounds[0] >= bedMin.x - 0.1 && bounds[1] >= bedMin.y - 0.1 &&
                    bounds[3] <= bedMax.x + 0.1 && bounds[4] <= bedMax.y + 0.1)
        } finally {
            validFile.delete()
            invalidFile.delete()
        }
    }

    @Test
    fun additionalProjectModelOverflowCreatesPlateWithoutMovingExistingModel() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        compose.waitUntil(timeoutMillis = 15_000) {
            glView.isAttachedToWindow && glView.width > 0 && glView.height > 0 &&
                glView.renderer.bed != null
        }
        val initialFile = File(activity.cacheDir, "project-model-overflow-initial-${System.nanoTime()}.stl")
        val addedFile = File(activity.cacheDir, "project-model-overflow-added-${System.nanoTime()}.stl")
        try {
            // Exercise the project-preserving import mode on a workspace that already has a
            // model. The new item must use the normal placement/overflow policy, while the
            // existing plate and its model stay intact.
            writeScaledCubeStl(activity, initialFile, 7f)
            writeScaledCubeStl(activity, addedFile, 7f)

            fun importAndWait(file: File, preserveLayout: Boolean) {
                val completed = CountDownLatch(1)
                val failure = AtomicReference<Exception?>()
                bed.loadModel(file, preserveLayout, 1, object : BedFragment.ModelLoadCallback {
                    override fun onLoaded(loadedModel: Model, firstNewObject: Int, addedObjects: Int) {
                        completed.countDown()
                    }

                    override fun onFailed(error: Exception) {
                        failure.set(error)
                        completed.countDown()
                    }
                }, autoOrientImportedModels = !preserveLayout)
                assertTrue("model import should finish", completed.await(30, TimeUnit.SECONDS))
                failure.get()?.let { throw AssertionError("model import failed", it) }
                compose.waitUntil(timeoutMillis = 15_000) {
                    !BedFragment::class.java.getDeclaredField("modelLoadInProgress")
                        .apply { isAccessible = true }.getBoolean(bed)
                }
            }

            importAndWait(initialFile, preserveLayout = false)
            val originalBounds = readExactBoundsOnGl(glView, 0).single()
            importAndWait(addedFile, preserveLayout = true)

            val plates = readPlateModels(bed)
            assertEquals("a full bed should receive one new plate for the imported project model", 2, plates.size)
            assertEquals("the existing model remains alone on its original plate", 1, plates[0].objectsCount)
            assertEquals("the imported model is placed on the new plate", 1, plates[1].objectsCount)
            assertEquals("the new plate is selected after import", 1, bed.activePlateIndex)
            assertArrayEquals("the original model must not be displaced by overflow", originalBounds,
                readExactBoundsOnGl(glView, plates[0], 0), 0.03)
            val newBounds = readExactBoundsOnGl(glView, plates[1], 0)
            val bedMin = requireNotNull(glView.renderer.bed?.getVolumeMin())
            val bedMax = requireNotNull(glView.renderer.bed?.getVolumeMax())
            assertTrue("the imported model must be arranged within the new bed bounds",
                newBounds[0] >= bedMin.x - 0.1 && newBounds[1] >= bedMin.y - 0.1 &&
                    newBounds[3] <= bedMax.x + 0.1 && newBounds[4] <= bedMax.y + 0.1)
        } finally {
            initialFile.delete()
            addedFile.delete()
        }
    }

    @Test
    fun smallMultiFileImportIsNestedTogetherOnAnEmptyPlate() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        compose.waitUntil(timeoutMillis = 15_000) { glView.renderer.bed?.isValid() == true }
        assertEquals("test starts with one empty plate", 1, bed.plateCount)
        assertEquals("test starts with no models", 0, requireNotNull(bed.currentModel).objectsCount)

        val files = listOf(
            File(activity.cacheDir, "joint-nest-0.stl"),
            File(activity.cacheDir, "joint-nest-1.stl"),
            File(activity.cacheDir, "joint-nest-2.stl"),
            File(activity.cacheDir, "joint-nest-3.stl"),
        )
        try {
            writeScaledBoxStl(activity, files[0], 0.75f, 0.50f, 0.20f)
            writeScaledBoxStl(activity, files[1], 0.50f, 0.75f, 0.20f)
            writeScaledBoxStl(activity, files[2], 0.40f, 0.35f, 0.20f)
            writeScaledBoxStl(activity, files[3], 0.35f, 0.40f, 0.20f)

            val completed = CountDownLatch(1)
            val failure = AtomicReference<Exception?>()
            bed.loadModelBatch(files, object : BedFragment.ModelLoadCallback {
                override fun onLoaded(loadedModel: Model, firstNewObject: Int, addedObjects: Int) {
                    completed.countDown()
                }

                override fun onFailed(error: Exception) {
                    failure.set(error)
                    completed.countDown()
                }
            }, autoOrientImportedModels = false)
            assertTrue("the compact multi-file batch should finish", completed.await(60, TimeUnit.SECONDS))
            failure.get()?.let { throw AssertionError("joint import nesting failed", it) }
            compose.waitUntil(timeoutMillis = 15_000) {
                !BedFragment::class.java.getDeclaredField("modelLoadInProgress")
                    .apply { isAccessible = true }.getBoolean(bed)
            }

            val plates = readPlateModels(bed)
            assertEquals("the four compact incoming models should fit together on one plate", 1, plates.size)
            assertEquals("each source remains a separate movable object", files.size, plates.single().objectsCount)
            val bounds = readExactBoundsOnGl(glView, *IntArray(files.size) { it })
            val min = requireNotNull(glView.renderer.bed?.getVolumeMin())
            val max = requireNotNull(glView.renderer.bed?.getVolumeMax())
            bounds.forEachIndexed { index, box ->
                assertTrue("object $index must remain inside the bed", box[0] >= min.x - 0.1 &&
                    box[1] >= min.y - 0.1 && box[3] <= max.x + 0.1 && box[4] <= max.y + 0.1)
            }
            for (first in bounds.indices) for (second in first + 1 until bounds.size) {
                val separated = bounds[first][3] <= bounds[second][0] - 1.7 ||
                    bounds[second][3] <= bounds[first][0] - 1.7 ||
                    bounds[first][4] <= bounds[second][1] - 1.7 ||
                    bounds[second][4] <= bounds[first][1] - 1.7
                assertTrue("jointly nested objects $first and $second must keep their clearance",
                    separated)
            }
        } finally {
            files.forEach(File::delete)
        }
    }

    @Test
    fun importedObjectsRotateInPlaneToUseAvailableBedSpace() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val firstFile = File(activity.cacheDir, "auto-arrange-140x80.stl")
        val secondFile = File(activity.cacheDir, "auto-arrange-80x140.stl")
        try {
            val activeBed = requireNotNull(glView.renderer.bed)
            val bedMin = requireNotNull(activeBed.getVolumeMin())
            val bedMax = requireNotNull(activeBed.getVolumeMax())
            val longBedSide = maxOf(bedMax.x - bedMin.x, bedMax.y - bedMin.y)
            val shortBedSide = minOf(bedMax.x - bedMin.x, bedMax.y - bedMin.y)
            // The perpendicular footprints cannot fit side-by-side or end-to-end without
            // rotation: their combined long+short side exceeds the actual test bed. After
            // rotating the second footprint, both fit in one row/column.
            val longSide = longBedSide * 0.65
            val shortSide = shortBedSide * 0.40
            writeScaledBoxStl(activity, firstFile, (longSide / 20.0).toFloat(), (shortSide / 20.0).toFloat(), 1f)
            writeScaledBoxStl(activity, secondFile, (shortSide / 20.0).toFloat(), (longSide / 20.0).toFloat(), 1f)
            fun importAndWait(file: File) {
                val completed = CountDownLatch(1)
                val failure = AtomicReference<Exception?>()
                bed.loadModel(file, false, 1, object : BedFragment.ModelLoadCallback {
                    override fun onLoaded(loadedModel: Model, firstNewObject: Int, addedObjects: Int) {
                        completed.countDown()
                    }

                    override fun onFailed(error: Exception) {
                        failure.set(error)
                        completed.countDown()
                    }
                })
                assertTrue("model import should finish", completed.await(30, TimeUnit.SECONDS))
                failure.get()?.let { throw AssertionError("model import failed", it) }
                compose.waitUntil(timeoutMillis = 10_000) {
                    !BedFragment::class.java.getDeclaredField("modelLoadInProgress")
                        .apply { isAccessible = true }.getBoolean(bed)
                }
            }

            importAndWait(firstFile)
            importAndWait(secondFile)

            val plates = readPlateModels(bed)
            assertEquals("rotated footprints should pack both parts on one plate; " +
                "bed=${bedMax.x - bedMin.x}x${bedMax.y - bedMin.y}, " +
                "plateCounts=${plates.map { it.objectsCount }}, " +
                "activeBounds=${readExactBoundsOnGl(glView, 0).map { it.contentToString() }}", 1, plates.size)
            assertEquals("both source objects must remain independent", 2, plates.single().objectsCount)
            val bounds = readExactBoundsOnGl(glView, 0, 1)
            bounds.forEachIndexed { index, box ->
                val footprintWidth = box[3] - box[0]
                val footprintDepth = box[4] - box[1]
                assertTrue("arranged object $index should use the compact rotated footprint; " +
                    "got ${footprintWidth}x${footprintDepth}, expected ${longSide}x${shortSide}",
                    kotlin.math.abs(footprintWidth - longSide) < 0.2 &&
                        kotlin.math.abs(footprintDepth - shortSide) < 0.2)
            }
            val minX = bedMin.x
            val minY = bedMin.y
            val maxX = bedMax.x
            val maxY = bedMax.y
            bounds.forEachIndexed { index, box ->
                assertTrue("object $index must remain within bed X bounds",
                    box[0] >= minX - 0.1 && box[3] <= maxX + 0.1)
                assertTrue("object $index must remain within bed Y bounds",
                    box[1] >= minY - 0.1 && box[4] <= maxY + 0.1)
            }
            val overlapX = minOf(bounds[0][3], bounds[1][3]) - maxOf(bounds[0][0], bounds[1][0])
            val overlapY = minOf(bounds[0][4], bounds[1][4]) - maxOf(bounds[0][1], bounds[1][1])
            assertTrue("the packed parts must not overlap in the XY plane", overlapX <= 0.1 || overlapY <= 0.1)
        } finally {
            firstFile.delete()
            secondFile.delete()
        }
    }

    @Test
    fun importedConcaveModelUsesItsOpenFootprintForAdditionalObjects() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val lShapeFile = File(activity.cacheDir, "auto-arrange-concave-l.stl")
        val insertFile = File(activity.cacheDir, "auto-arrange-concave-insert.stl")
        try {
            // A 150 mm L has a 125 x 125 mm open corner. A 100 mm square fits there with
            // the normal 6 mm separation, although their 150/100 mm convex bounds cannot
            // be packed together on this printer bed.
            // An open STL still has a precise projected footprint. Its open corner must remain
            // usable instead of being blocked by a conservative convex hull fallback.
            writeConcavePrismStl(activity, lShapeFile, openBottom = true)
            writeScaledBoxStl(activity, insertFile, 5f, 5f, 1f)
            fun importAndWait(file: File) {
                val completed = CountDownLatch(1)
                val failure = AtomicReference<Exception?>()
                bed.loadModel(file, false, 1, object : BedFragment.ModelLoadCallback {
                    override fun onLoaded(loadedModel: Model, firstNewObject: Int, addedObjects: Int) {
                        completed.countDown()
                    }

                    override fun onFailed(error: Exception) {
                        failure.set(error)
                        completed.countDown()
                    }
                })
                assertTrue("model import should finish", completed.await(30, TimeUnit.SECONDS))
                failure.get()?.let { throw AssertionError("model import failed", it) }
                compose.waitUntil(timeoutMillis = 10_000) {
                    !BedFragment::class.java.getDeclaredField("modelLoadInProgress")
                        .apply { isAccessible = true }.getBoolean(bed)
                }
            }

            importAndWait(lShapeFile)
            importAndWait(insertFile)
            val plates = readPlateModels(bed)
            assertEquals("the insert should use the real open area of the L footprint", 1, plates.size)
            assertEquals("both models should remain independent", 2, plates.single().objectsCount)
            val bounds = readExactBoundsOnGl(glView, 0, 1)
            val bedMin = requireNotNull(glView.renderer.bed?.getVolumeMin())
            val bedMax = requireNotNull(glView.renderer.bed?.getVolumeMax())
            bounds.forEachIndexed { index, box ->
                assertTrue("arranged model $index must remain within the build volume",
                    box[0] >= bedMin.x - 0.1 && box[1] >= bedMin.y - 0.1 &&
                        box[3] <= bedMax.x + 0.1 && box[4] <= bedMax.y + 0.1)
            }
        } finally {
            lShapeFile.delete()
            insertFile.delete()
        }
    }

    @Test
    fun detailedConcaveModelKeepsItsOpenFootprintAboveLegacyProjectionLimit() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val detailedLShapeFile = File(activity.cacheDir, "auto-arrange-detailed-concave-l.stl")
        val insertFile = File(activity.cacheDir, "auto-arrange-detailed-concave-insert.stl")
        try {
            // Subdivision keeps the same L silhouette but raises its facet count beyond the
            // former 20k projection cutoff. The 100 mm insert fits in the 125 mm open corner;
            // a convex-hull fallback would incorrectly force it to another plate.
            writeConcavePrismStl(activity, detailedLShapeFile, subdivisionLevels = 5)
            writeScaledBoxStl(activity, insertFile, 5f, 5f, 1f)
            fun importAndWait(file: File) {
                val completed = CountDownLatch(1)
                val failure = AtomicReference<Exception?>()
                bed.loadModel(file, false, 1, object : BedFragment.ModelLoadCallback {
                    override fun onLoaded(loadedModel: Model, firstNewObject: Int, addedObjects: Int) {
                        completed.countDown()
                    }

                    override fun onFailed(error: Exception) {
                        failure.set(error)
                        completed.countDown()
                    }
                })
                assertTrue("model import should finish", completed.await(30, TimeUnit.SECONDS))
                failure.get()?.let { throw AssertionError("model import failed", it) }
                compose.waitUntil(timeoutMillis = 10_000) {
                    !BedFragment::class.java.getDeclaredField("modelLoadInProgress")
                        .apply { isAccessible = true }.getBoolean(bed)
                }
            }

            importAndWait(detailedLShapeFile)
            val platesAfterL = readPlateModels(bed)
            assertEquals("the detailed L should be on the active plate", 1, platesAfterL.size)
            assertTrue("the fixture must exceed the previous 20k facet cutoff",
                platesAfterL.single().getTriangleCount(0) > 20_000)

            importAndWait(insertFile)
            val plates = readPlateModels(bed)
            assertEquals("the 100 mm insert should fit in the detailed L's 125 mm opening", 1, plates.size)
            assertEquals("both independent models should remain on the same plate", 2, plates.single().objectsCount)
            val bounds = readExactBoundsOnGl(glView, 0, 1)
            val bedMin = requireNotNull(glView.renderer.bed?.getVolumeMin())
            val bedMax = requireNotNull(glView.renderer.bed?.getVolumeMax())
            bounds.forEachIndexed { index, box ->
                assertTrue("arranged model $index must remain within the build volume",
                    box[0] >= bedMin.x - 0.1 && box[1] >= bedMin.y - 0.1 &&
                        box[3] <= bedMax.x + 0.1 && box[4] <= bedMax.y + 0.1)
            }
        } finally {
            detailedLShapeFile.delete()
            insertFile.delete()
        }
    }

    @Test
    fun objectListGroupCanBeCombinedAsPartsFromTheModelMenu() {
        var modelEditStageStartedAt = SystemClock.elapsedRealtime()
        fun logModelEditStage(stage: String, details: String = "") {
            val now = SystemClock.elapsedRealtime()
            val runtime = Runtime.getRuntime()
            android.util.Log.i(
                "ModelEditPerf",
                "stage=$stage elapsedMs=${now - modelEditStageStartedAt} $details " +
                    "nativeHeapBytes=${android.os.Debug.getNativeHeapAllocatedSize()} " +
                    "javaHeapUsedBytes=${runtime.totalMemory() - runtime.freeMemory()}",
            )
            modelEditStageStartedAt = now
        }
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val modelFile = File(activity.cacheDir, "model-menu-group-combine-test.stl")
        val roundTripFile = File(activity.cacheDir, "model-menu-group-combine-test.3mf")
        val gcodeFile = File(activity.cacheDir, "model-menu-group-combine-test.gcode")
        val menuPreferences = activity.getSharedPreferences("workspace_menu_customization", 0)
        val previousSelectionOrder = menuPreferences.getString("order_model_selected", null)
        val previousSelectionHidden = menuPreferences.getStringSet("hidden_model_selected", null)?.toSet()
        menuPreferences.edit().remove("order_model_selected").remove("hidden_model_selected").commit()
        val previousSlots = Prefs.getStoredFilamentSlots().map { FilamentSlot(it.color, it.type) }
        Prefs.setStoredFilamentSlots(listOf(
            FilamentSlot(0xFF303030.toInt(), "Join T0"),
            FilamentSlot(0xFF3366CC.toInt(), "Join T1"),
            FilamentSlot(0xFFF2F2F2.toInt(), "Join T2"),
        ))
        SliceBeam.INSTANCE = activity.applicationContext as SliceBeam
        Prefs.init(activity.applicationContext as SliceBeam)
        TemporaryProjectSettings.clear()
        val previousConfig = SliceBeam.CONFIG
        val configFile = SliceBeam.getCurrentConfigFile()
        val previousConfigFile = configFile.takeIf { it.isFile }?.readBytes()
        val prefs = requireNotNull(Prefs.getPrefs())
        val snapshotKeys = listOf("mmu_snapshot_printer", "mmu_snapshot")
        val previousSnapshots = snapshotKeys.associateWith { prefs.getString(it, null) }
        var roundTrippedModel: Model? = null
        var sliceResult: GCodeProcessorResult? = null
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            loadSingleTestModel(bed, modelFile)
            val prepared = CountDownLatch(1)
            glView.queueEvent {
                val renderer = glView.renderer
                val model = requireNotNull(renderer.model)
                val min = Vec3d(); val max = Vec3d()
                model.getBoundingBoxExact(0, min, max)
                val partWidth = max.x - min.x
                model.addObject(model, 0)
                model.translate(1, partWidth * 1.4, 0.0, 0.0)
                model.duplicateInstance(1, 0, Vec3d(partWidth * 1.4, 0.0, 0.0))
                model.addObject(model, 0)
                model.translate(2, partWidth * 4.2, 0.0, 0.0)
                model.setUniformFilament(0, 1)
                model.setUniformFilament(1, 2)
                model.setUniformFilament(2, 3)
                renderer.setSelectedObject(0)
                glView.requestRender()
                prepared.countDown()
            }
            assertTrue("multipart test objects should be prepared on the GL thread", prepared.await(10, TimeUnit.SECONDS))
            assertEquals("three selected source objects should exist before opening the list", 3,
                readObjectCountOnGl(glView))
            compose.runOnUiThread { bed.updateModel() }
            instrumentationWaitForGlIdle()

            openModelMenu(activity)
            selectSingleObjectScope()
            revealMenuAction(activity, R.string.WorkspaceObjects)
            compose.onNodeWithContentDescription(activity.getString(R.string.WorkspaceObjects))
                .assertIsDisplayed().performClick()
            compose.onNodeWithTag("model-objects-group-selection-toggle").performClick()
            compose.onNodeWithTag("model-object-row-1").performScrollTo().performClick()
            compose.onNodeWithTag("model-object-row-2").performScrollTo().performClick()
            compose.onNodeWithTag("model-objects-selection-confirm").performClick()
            compose.waitUntil(timeoutMillis = 10_000) { glView.renderer.selectedObjectsCount == 3 }
            val primaryReady = CountDownLatch(1)
            glView.queueEvent {
                // Keep object 3 active while the complete group remains selected. Joining
                // must use this primary object, even though its model index is highest.
                glView.renderer.setSelectedObject(2)
                glView.renderer.setSelectedObjects(intArrayOf(0, 1, 2))
                glView.requestRender()
                primaryReady.countDown()
            }
            assertTrue("the selected group should retain its active destination object",
                primaryReady.await(10, TimeUnit.SECONDS))
            assertEquals("the highest-index object remains the active group target", 2,
                readSelectedObjectOnGl(glView))

            openModelMenu(activity)
            selectSingleObjectScope()
            revealMenuAction(activity, R.string.ModelMenuCombineSelected)
            compose.onNodeWithContentDescription(activity.getString(R.string.ModelMenuCombineSelected))
                .assertIsDisplayed().assertIsEnabled().performClick()
            compose.onNodeWithTag("workspace-message-confirm").assertIsDisplayed().performClick()

            compose.waitUntil(timeoutMillis = 15_000) { bed.currentModel?.objectsCount == 1 }
            val combinedFilaments = AtomicReference<IntArray>()
            val targetPartFilament = AtomicReference<Int>()
            val combinedReady = CountDownLatch(1)
            glView.queueEvent {
                val combinedModel = requireNotNull(glView.renderer.model)
                combinedFilaments.set(combinedModel.getUsedFilaments(0))
                targetPartFilament.set(combinedModel.getVolumeExtruder(0, 0))
                combinedReady.countDown()
            }
            assertTrue("combined part filaments should be read on the GL thread", combinedReady.await(10, TimeUnit.SECONDS))
            assertArrayEquals("combining the selected group preserves all source materials",
                intArrayOf(1, 2, 3), requireNotNull(combinedFilaments.get()))
            assertEquals("the active source object leads the joined multipart assembly", 3,
                requireNotNull(targetPartFilament.get()))
            logModelEditStage("combine-and-undo-redo")

            compose.runOnUiThread { bed.undoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) { readObjectCountOnGl(glView) == 3 }
            assertEquals("Undo should restore the three separate source objects", 3,
                readObjectCountOnGl(glView))
            compose.runOnUiThread { bed.redoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) { readObjectCountOnGl(glView) == 1 }
            assertArrayEquals("Redo should restore all multipart filament assignments", intArrayOf(1, 2, 3),
                readUsedFilamentsOnGl(glView, 0))

            // Verify split history before slicing allocates native processing memory. The app
            // intentionally disables snapshots above its native-heap safety threshold.
            glView.selectModel(0, Runnable {})
            instrumentationWaitForGlIdle()
            // Recreate the selection menu after the model changed from three objects to one.
            // This prevents its remembered action visibility/order from a prior menu instance
            // from hiding Split in a user-customized test installation.
            menuPreferences.edit().remove("order_model_selected").remove("hidden_model_selected").commit()
            openWorkspaceMenu(activity, WorkspaceDestination.PROJECT, R.string.MenuProject)
            openSelectedObjectMenu(activity)
            revealMenuAction(activity, R.string.ModelMenuSplit)
            compose.onNodeWithContentDescription(activity.getString(R.string.ModelMenuSplit))
                .performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
            compose.waitUntil(timeoutMillis = 15_000) { readObjectCountOnGl(glView) == 4 }
            // Split posts updateModel() to Main, which then queues its history snapshot on GL.
            // Wait for that GL-owned write before inspecting the snapshot; reading the deque on
            // Main immediately after the native split races the posted history update.
            compose.waitUntil(timeoutMillis = 10_000) {
                readLastUndoSnapshotObjectCountOnGl(bed, glView) == 1
            }
            assertEquals("split undo snapshot should restore the multipart joined object", 1,
                readLastUndoSnapshotObjectCountOnGl(bed, glView))
            assertEquals("splitting should preserve the primary part first and every source filament", listOf(3, 1, 2, 2),
                (0 until 4).map { index -> readUsedFilamentsOnGl(glView, index).single() })
            compose.runOnUiThread { bed.undoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) { readObjectCountOnGl(glView) == 1 }
            assertArrayEquals("Undo split must restore the joined multipart assembly", intArrayOf(1, 2, 3),
                readUsedFilamentsOnGl(glView, 0))
            compose.runOnUiThread { bed.redoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) { readObjectCountOnGl(glView) == 4 }
            assertEquals("Redo split must restore primary-first independent source bodies", listOf(3, 1, 2, 2),
                (0 until 4).map { index -> readUsedFilamentsOnGl(glView, index).single() })
            compose.runOnUiThread { bed.undoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) { readObjectCountOnGl(glView) == 1 }
            logModelEditStage("split-and-history")

            val exportReady = CountDownLatch(1)
            val exportFailure = AtomicReference<Throwable?>()
            glView.queueEvent {
                try {
                    val model = requireNotNull(glView.renderer.model)
                    assertEquals("the joined parts should remain a single renderer object", 1, model.objectsCount)
                    assertArrayEquals("the joined object should expose all three part materials", intArrayOf(1, 2, 3),
                        model.getUsedFilaments(0))
                    activity.assets.open("orca_profiles/Anycubic.ini").use {
                        SliceBeam.CONFIG = Slic3rConfigWrapper(it)
                    }
                    SliceBeam.requireConfig().presets = ConfigObject("presets").apply {
                        put("printer", "Anycubic Kobra 3 0.4 nozzle")
                        put("print", "0.16mm Standard")
                        put("filament", "Anycubic Generic PLA")
                    }
                    val clearSnapshots = prefs.edit()
                    snapshotKeys.forEach(clearSnapshots::remove)
                    check(clearSnapshots.commit())
                    SliceBeam.genCurrentConfig(model)
                    model.export3mf(configFile.absolutePath, roundTripFile.absolutePath)
                } catch (error: Throwable) {
                    exportFailure.set(error)
                } finally {
                    exportReady.countDown()
                }
            }
            assertTrue("joined multipart object should export from the active renderer",
                exportReady.await(60, TimeUnit.SECONDS))
            exportFailure.get()?.let { throw AssertionError("joined object 3MF export failed", it) }
            assertTrue("joined object should produce a non-empty 3MF project",
                roundTripFile.isFile && roundTripFile.length() > 0L)
            logModelEditStage("3mf-export")
            roundTrippedModel = Model(roundTripFile, 1)
            assertEquals("the joined parts should remain one object after 3MF reload", 1,
                roundTrippedModel!!.objectsCount)
            assertArrayEquals("all joined part filaments should survive 3MF reload", intArrayOf(1, 2, 3),
                roundTrippedModel!!.getUsedFilaments(0))
            logModelEditStage("3mf-reload")
            SliceBeam.genCurrentConfig(roundTrippedModel!!)
            logModelEditStage("slice-config-build")
            val sliceStartedAt = SystemClock.elapsedRealtime()
            val sliceProgressEvents = mutableListOf<String>()
            var lastProgressBucket = -1
            sliceResult = roundTrippedModel!!.slice(
                configFile.absolutePath,
                gcodeFile.absolutePath,
                SliceListener { progress, text ->
                    val bucket = progress.coerceIn(0, 100) / 5
                    synchronized(sliceProgressEvents) {
                        if (bucket > lastProgressBucket) {
                            lastProgressBucket = bucket
                            sliceProgressEvents += "${SystemClock.elapsedRealtime() - sliceStartedAt}ms:$progress:${text.orEmpty()}"
                        }
                    }
                },
            )
            synchronized(sliceProgressEvents) {
                android.util.Log.i("ModelEditPerf", "native-slice-progress=${sliceProgressEvents.joinToString(" -> ")}")
            }
            logModelEditStage(
                "native-slice",
                "triangles=${roundTrippedModel!!.getTriangleCount(0)} gcodeBytes=${gcodeFile.length()}",
            )
            assertTrue("the joined object should generate G-code", gcodeFile.isFile && gcodeFile.length() > 0L)
            var hasExtrusion = false
            val emittedTools = mutableSetOf<Int>()
            gcodeFile.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (!hasExtrusion && line.startsWith("G1 ")) {
                        hasExtrusion = line.splitToSequence(' ').any { token ->
                            token.startsWith("E") && token.substring(1).toDoubleOrNull() != null
                        }
                    }
                    if (line.startsWith("T")) {
                        val command = line.takeWhile { !it.isWhitespace() }
                        if (command.length > 1 && command[0] == 'T' && command.drop(1).all(Char::isDigit)) {
                            command.drop(1).toIntOrNull()?.let(emittedTools::add)
                        }
                    }
                }
            }
            assertTrue("joined parts should produce deposited extrusion", hasExtrusion)
            assertTrue("T0 from the first joined part should be used, tools=$emittedTools", 0 in emittedTools)
            assertTrue("T1 from the second joined part should be used, tools=$emittedTools", 1 in emittedTools)
            assertTrue("T2 from the third joined part should be used, tools=$emittedTools", 2 in emittedTools)
            logModelEditStage("gcode-validation")
        } finally {
            sliceResult?.release()
            roundTrippedModel?.release()
            gcodeFile.delete()
            roundTripFile.delete()
            val restoreSnapshots = prefs.edit()
            previousSnapshots.forEach { (key, value) ->
                if (value == null) restoreSnapshots.remove(key) else restoreSnapshots.putString(key, value)
            }
            restoreSnapshots.commit()
            SliceBeam.CONFIG = previousConfig
            if (previousConfigFile == null) configFile.delete() else configFile.writeBytes(previousConfigFile)
            TemporaryProjectSettings.clear()
            Prefs.setStoredFilamentSlots(previousSlots)
            modelFile.delete()
            menuPreferences.edit().apply {
                if (previousSelectionOrder == null) remove("order_model_selected")
                else putString("order_model_selected", previousSelectionOrder)
                if (previousSelectionHidden == null) remove("hidden_model_selected")
                else putStringSet("hidden_model_selected", previousSelectionHidden)
            }.commit()
        }
    }

    @Test
    fun initialProjectRouteShowsFileActionsOnItsFirstOpen() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val controller = BedFragment::class.java.getDeclaredField("workspaceController")
            .apply { isAccessible = true }.get(bed)
        assertEquals(WorkspaceDestination.PROJECT.ordinal,
            controller.javaClass.getMethod("getSelectedDestinationSlot").invoke(controller))
        assertFalse(controller.javaClass.getMethod("getToolPanelExpanded").invoke(controller) as Boolean)

        val menuMap = BedFragment::class.java.getDeclaredField("menuMap")
            .apply { isAccessible = true }.get(bed) as android.util.SparseArray<*>
        val projectMenu = menuMap.get(WorkspaceDestination.PROJECT.ordinal)
        assertTrue(projectMenu is FileMenu)
        val boundFragment = ListBedMenu::class.java.getDeclaredField("fragment")
            .apply { isAccessible = true }.get(projectMenu)
        assertSame("the initially selected Project route must receive its BedFragment", bed, boundFragment)

        val compactPortrait = usesCompactPortraitLayout(activity)
        if (compactPortrait) {
            compose.onNodeWithContentDescription(activity.getString(R.string.MenuProject))
                .assertIsDisplayed()
                .performClick()
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(activity.getString(R.string.WorkspaceAddModelAction))
                .fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithContentDescription(activity.getString(R.string.MenuProject)).assertIsDisplayed()
        compose.onNodeWithText(activity.getString(R.string.WorkspaceAddModelAction)).assertIsDisplayed()
        if (compactPortrait) revealMenuAction(activity, R.string.MenuFileExport3mf)
        compose.onNodeWithContentDescription(activity.getString(R.string.MenuFileExport3mf)).assertIsDisplayed()
        assertEquals("only compact portrait expands Project actions as a separate panel",
            compactPortrait,
            controller.javaClass.getMethod("getToolPanelExpanded").invoke(controller) as Boolean)
    }

    @Test
    fun projectExportAvailabilityTracksModelLoadedOnGlThread() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val modelFile = File(activity.cacheDir, "project-export-gl-state-test.stl")
        try {
            openWorkspaceMenu(activity, WorkspaceDestination.PROJECT, R.string.MenuProject)
            revealMenuAction(activity, R.string.MenuFileExport3mf)
            val export = compose.onNodeWithContentDescription(activity.getString(R.string.MenuFileExport3mf))
            export.assertIsDisplayed().assertIsNotEnabled()

            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            loadSingleTestModel(bed, modelFile)
            compose.waitUntil(timeoutMillis = 5_000) {
                runCatching { export.assertIsEnabled() }.isSuccess
            }
            export.assertIsEnabled()
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun projectMenuAddModelBuildsMultiSelectOpenDocumentRequest() {
        val activity = compose.activity
        val requestCode = MainActivity.REQUEST_CODE_OPEN_FILE
        val launchersField = MainActivity::class.java.getDeclaredField("activityResultLaunchers")
            .apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val launchers = launchersField.get(activity) as MutableMap<Int, ActivityResultLauncher<Intent>>
        val previousLauncher = requireNotNull(launchers[requestCode])
        val capturedIntent = java.util.concurrent.atomic.AtomicReference<Intent?>()
        launchers[requestCode] = object : ActivityResultLauncher<Intent>() {
            override val contract: ActivityResultContract<Intent, *> =
                ActivityResultContracts.StartActivityForResult()

            override fun launch(input: Intent, options: ActivityOptionsCompat?) {
                capturedIntent.set(Intent(input))
            }

            override fun unregister() = Unit
        }

        try {
            openWorkspaceMenu(activity, WorkspaceDestination.PROJECT, R.string.MenuProject)
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodesWithText(activity.getString(R.string.WorkspaceAddModelAction))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(activity.getString(R.string.WorkspaceAddModelAction))
                .assertIsDisplayed().performClick()

            compose.waitUntil(timeoutMillis = 5_000) { capturedIntent.get() != null }
            val request = requireNotNull(capturedIntent.get())
            assertEquals(Intent.ACTION_OPEN_DOCUMENT, request.action)
            assertEquals("*/*", request.type)
            assertTrue(request.categories?.contains(Intent.CATEGORY_OPENABLE) == true)
            assertTrue("the project route must allow selecting multiple models",
                request.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false))
        } finally {
            launchers[requestCode] = previousLauncher
        }
    }

    @Test
    fun projectMenuWaitsForAnAllocatedRendererBedToBecomeValidBeforeOpeningPicker() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val requestCode = MainActivity.REQUEST_CODE_OPEN_FILE
        val launchersField = MainActivity::class.java.getDeclaredField("activityResultLaunchers")
            .apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val launchers = launchersField.get(activity) as MutableMap<Int, ActivityResultLauncher<Intent>>
        val previousLauncher = requireNotNull(launchers[requestCode])
        val capturedIntent = AtomicReference<Intent?>()
        launchers[requestCode] = object : ActivityResultLauncher<Intent>() {
            override val contract: ActivityResultContract<Intent, *> =
                ActivityResultContracts.StartActivityForResult()

            override fun launch(input: Intent, options: ActivityOptionsCompat?) {
                capturedIntent.set(Intent(input))
            }

            override fun unregister() = Unit
        }

        val originalRendererBed = AtomicReference<ru.ytkab0bp.slicebeam.slic3r.Bed3D?>()
        val invalidRendererBed = ru.ytkab0bp.slicebeam.slic3r.Bed3D()
        val bedReplaced = CountDownLatch(1)
        glView.queueEvent {
            originalRendererBed.set(glView.renderer.bed)
            glView.renderer.bed = invalidRendererBed
            bedReplaced.countDown()
        }
        try {
            assertTrue("the test must install an allocated but unconfigured GL bed",
                bedReplaced.await(10, TimeUnit.SECONDS))
            openWorkspaceMenu(activity, WorkspaceDestination.PROJECT, R.string.MenuProject)
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodesWithText(activity.getString(R.string.WorkspaceAddModelAction))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(activity.getString(R.string.WorkspaceAddModelAction))
                .assertIsDisplayed().performClick()

            // Allow the first GL readiness check to observe the invalid non-null Bed3D.
            activity.window.decorView.postDelayed({
                glView.queueEvent { glView.renderer.bed = originalRendererBed.get() }
            }, 350L)

            compose.waitUntil(timeoutMillis = 5_000) { capturedIntent.get() != null }
            assertEquals("the picker should open once the existing Bed3D becomes valid",
                Intent.ACTION_OPEN_DOCUMENT, requireNotNull(capturedIntent.get()).action)
        } finally {
            launchers[requestCode] = previousLauncher
            val restored = CountDownLatch(1)
            glView.queueEvent {
                originalRendererBed.get()?.let { glView.renderer.bed = it }
                invalidRendererBed.release()
                restored.countDown()
            }
            assertTrue("the test must restore the production bed", restored.await(10, TimeUnit.SECONDS))
        }
    }

    @Test
    fun projectMenuImportsSelectedStlThroughTheActualResultHandler() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val inputFile = File(activity.cacheDir, "menu-import-${System.nanoTime()}.stl")
        val cachedImport = File(SliceBeam.getModelCacheDir(), inputFile.name)
        val requestCode = MainActivity.REQUEST_CODE_OPEN_FILE
        val launchersField = MainActivity::class.java.getDeclaredField("activityResultLaunchers")
            .apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val launchers = launchersField.get(activity) as MutableMap<Int, ActivityResultLauncher<Intent>>
        val previousLauncher = requireNotNull(launchers[requestCode])
        val capturedIntent = java.util.concurrent.atomic.AtomicReference<Intent?>()
        launchers[requestCode] = object : ActivityResultLauncher<Intent>() {
            override val contract: ActivityResultContract<Intent, *> =
                ActivityResultContracts.StartActivityForResult()

            override fun launch(input: Intent, options: ActivityOptionsCompat?) {
                capturedIntent.set(Intent(input))
            }

            override fun unregister() = Unit
        }
        @Suppress("DEPRECATION")
        val provider = activity.packageManager.getPackageInfo(
            activity.packageName,
            PackageManager.GET_PROVIDERS,
        ).providers.orEmpty().first { it.name == FileProvider::class.java.name }
        val authority = requireNotNull(provider.authority).substringBefore(';')
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                inputFile.outputStream().use(source::copyTo)
            }
            assertEquals(0, bed.currentModel?.objectsCount ?: 0)

            openWorkspaceMenu(activity, WorkspaceDestination.PROJECT, R.string.MenuProject)
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodesWithText(activity.getString(R.string.WorkspaceAddModelAction))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(activity.getString(R.string.WorkspaceAddModelAction))
                .assertIsDisplayed().performClick()
            // FileMenu validates the print bed from the renderer's GL thread before it launches
            // the SAF picker, so the registered result launcher is intentionally asynchronous.
            compose.waitUntil(timeoutMillis = 5_000) { capturedIntent.get() != null }
            assertEquals(Intent.ACTION_OPEN_DOCUMENT, requireNotNull(capturedIntent.get()).action)

            val inputUri = FileProvider.getUriForFile(activity, authority, inputFile)
            activity.runOnUiThread {
                MainActivity::class.java.getDeclaredMethod(
                    "dispatchActivityResult", Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType, Intent::class.java,
                ).apply { isAccessible = true }
                    .invoke(activity, requestCode, android.app.Activity.RESULT_OK, Intent().setData(inputUri))
            }
            compose.waitUntil(timeoutMillis = 60_000) {
                bed.currentModel?.objectsCount == 1
            }
            assertEquals("the selected STL should become the active native model", 1,
                requireNotNull(bed.currentModel).objectsCount)
            assertTrue("the actual result handler should copy the selected file into the model cache",
                cachedImport.isFile && cachedImport.length() > 0L)
        } finally {
            launchers[requestCode] = previousLauncher
            inputFile.delete()
            cachedImport.delete()
        }
    }

    @Test
    fun projectMenuImportsSelectedMulticolorSvgThroughTheActualResultHandler() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val inputFile = File(activity.cacheDir, "menu-import-${System.nanoTime()}.svg")
        val cachedImport = File(SliceBeam.getModelCacheDir(), inputFile.name)
        val requestCode = MainActivity.REQUEST_CODE_OPEN_FILE
        val launchersField = MainActivity::class.java.getDeclaredField("activityResultLaunchers")
            .apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val launchers = launchersField.get(activity) as MutableMap<Int, ActivityResultLauncher<Intent>>
        val previousLauncher = requireNotNull(launchers[requestCode])
        val capturedIntent = AtomicReference<Intent?>()
        launchers[requestCode] = object : ActivityResultLauncher<Intent>() {
            override val contract: ActivityResultContract<Intent, *> =
                ActivityResultContracts.StartActivityForResult()

            override fun launch(input: Intent, options: ActivityOptionsCompat?) {
                capturedIntent.set(Intent(input))
            }

            override fun unregister() = Unit
        }
        @Suppress("DEPRECATION")
        val provider = activity.packageManager.getPackageInfo(
            activity.packageName,
            PackageManager.GET_PROVIDERS,
        ).providers.orEmpty().first { it.name == FileProvider::class.java.name }
        val authority = requireNotNull(provider.authority).substringBefore(';')
        try {
            InstrumentationRegistry.getInstrumentation().context.assets
                .open("models/Beste_Oma_Kind_Herz_3D_Druck.svg").use { source ->
                    inputFile.outputStream().use(source::copyTo)
                }
            assertEquals("the file picker starts with an empty workspace", 0, bed.currentModel?.objectsCount ?: 0)

            openWorkspaceMenu(activity, WorkspaceDestination.PROJECT, R.string.MenuProject)
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodesWithText(activity.getString(R.string.WorkspaceAddModelAction))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(activity.getString(R.string.WorkspaceAddModelAction))
                .assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 5_000) { capturedIntent.get() != null }
            assertEquals(Intent.ACTION_OPEN_DOCUMENT, requireNotNull(capturedIntent.get()).action)

            val inputUri = FileProvider.getUriForFile(activity, authority, inputFile)
            activity.runOnUiThread {
                MainActivity::class.java.getDeclaredMethod(
                    "dispatchActivityResult", Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType, Intent::class.java,
                ).apply { isAccessible = true }
                    .invoke(activity, requestCode, android.app.Activity.RESULT_OK, Intent().setData(inputUri))
            }
            compose.waitUntil(timeoutMillis = 60_000) {
                val model = bed.currentModel
                model != null && model.objectsCount == 1
            }

            val assignments = AtomicReference<IntArray?>()
            val triangleCount = AtomicReference(0)
            val assignmentsReady = CountDownLatch(1)
            glView.queueEvent {
                bed.currentModel?.let { model ->
                    triangleCount.set(model.getTriangleCount(0))
                    assignments.set(model.getUsedFilaments(0))
                }
                assignmentsReady.countDown()
            }
            assertTrue("SVG filament assignments should be readable on the renderer thread",
                assignmentsReady.await(10, TimeUnit.SECONDS))
            assertTrue("the imported SVG should contain printable triangle geometry", triangleCount.get() > 0)
            assertArrayEquals("all three colored SVG regions should survive the real file-picker import",
                intArrayOf(1, 2, 3), requireNotNull(assignments.get()))
            assertTrue("the actual result handler should copy the SVG into app-owned model cache",
                cachedImport.isFile && cachedImport.length() > 0L)
        } finally {
            launchers[requestCode] = previousLauncher
            inputFile.delete()
            cachedImport.delete()
        }
    }

    @Test
    fun projectMenuImportsSelectedStepAfterConfirmingMeshingOptions() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val inputFile = File(activity.cacheDir, "menu-step-import-${System.nanoTime()}.step")
        val cachedImport = File(SliceBeam.getModelCacheDir(), inputFile.name)
        val requestCode = MainActivity.REQUEST_CODE_OPEN_FILE
        val launchersField = MainActivity::class.java.getDeclaredField("activityResultLaunchers")
            .apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val launchers = launchersField.get(activity) as MutableMap<Int, ActivityResultLauncher<Intent>>
        val previousLauncher = requireNotNull(launchers[requestCode])
        val capturedIntent = java.util.concurrent.atomic.AtomicReference<Intent?>()
        launchers[requestCode] = object : ActivityResultLauncher<Intent>() {
            override val contract: ActivityResultContract<Intent, *> =
                ActivityResultContracts.StartActivityForResult()

            override fun launch(input: Intent, options: ActivityOptionsCompat?) {
                capturedIntent.set(Intent(input))
            }

            override fun unregister() = Unit
        }
        @Suppress("DEPRECATION")
        val provider = activity.packageManager.getPackageInfo(
            activity.packageName,
            PackageManager.GET_PROVIDERS,
        ).providers.orEmpty().first { it.name == FileProvider::class.java.name }
        val authority = requireNotNull(provider.authority).substringBefore(';')
        try {
            InstrumentationRegistry.getInstrumentation().context.assets
                .open("step_cube_ccby4.step").use { source ->
                inputFile.outputStream().use(source::copyTo)
            }
            assertEquals(0, bed.currentModel?.objectsCount ?: 0)

            openWorkspaceMenu(activity, WorkspaceDestination.PROJECT, R.string.MenuProject)
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodesWithText(activity.getString(R.string.WorkspaceAddModelAction))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(activity.getString(R.string.WorkspaceAddModelAction))
                .assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 5_000) { capturedIntent.get() != null }
            val request = requireNotNull(capturedIntent.get())
            assertEquals(Intent.ACTION_OPEN_DOCUMENT, request.action)
            assertEquals("*/*", request.type)

            val inputUri = FileProvider.getUriForFile(activity, authority, inputFile)
            activity.runOnUiThread {
                MainActivity::class.java.getDeclaredMethod(
                    "dispatchActivityResult", Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType, Intent::class.java,
                ).apply { isAccessible = true }
                    .invoke(activity, requestCode, android.app.Activity.RESULT_OK, Intent().setData(inputUri))
            }
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithTag("step-import-confirm").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals("STEP geometry must wait for explicit meshing confirmation", 0,
                bed.currentModel?.objectsCount ?: 0)
            compose.onNodeWithTag("step-linear-deflection").performScrollTo().performTextReplacement("0.01")
            compose.onNodeWithTag("step-angular-deflection").performScrollTo().performTextReplacement("0.7")
            compose.onNodeWithTag("step-import-confirm").performClick()

            compose.waitUntil(timeoutMillis = 90_000) {
                val model = bed.currentModel
                model != null && model.objectsCount == 1 && model.getTriangleCount(0) > 0
            }
            assertTrue("The selected STEP must be copied into app-owned cache before native import",
                cachedImport.isFile && cachedImport.length() > 0L)
            assertTrue("The imported STEP must produce renderable triangles",
                requireNotNull(bed.currentModel).getTriangleCount(0) > 0)
        } finally {
            launchers[requestCode] = previousLauncher
            inputFile.delete()
            cachedImport.delete()
        }
    }

    @Test
    fun stepImportFromFileUriFallsBackToDocumentPickerAndWaitsForWorkspaceHost() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        // Simulate providers that expose a generated display name and no STEP MIME type.
        val fileName = "menu-step-import-${System.nanoTime()}-document"
        val inputFile = File(activity.cacheDir, fileName)
        val cachedImport = File(SliceBeam.getModelCacheDir(), "$fileName.step")
        val requestCode = MainActivity.REQUEST_CODE_OPEN_FILE
        val launchersField = MainActivity::class.java.getDeclaredField("activityResultLaunchers")
            .apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val launchers = launchersField.get(activity) as MutableMap<Int, ActivityResultLauncher<Intent>>
        val previousLauncher = requireNotNull(launchers[requestCode])
        val capturedIntent = java.util.concurrent.atomic.AtomicReference<Intent?>()
        launchers[requestCode] = object : ActivityResultLauncher<Intent>() {
            override val contract: ActivityResultContract<Intent, *> =
                ActivityResultContracts.StartActivityForResult()

            override fun launch(input: Intent, options: ActivityOptionsCompat?) {
                capturedIntent.set(Intent(input))
            }

            override fun unregister() = Unit
        }
        @Suppress("DEPRECATION")
        val provider = activity.packageManager.getPackageInfo(
            activity.packageName,
            PackageManager.GET_PROVIDERS,
        ).providers.orEmpty().first { it.name == FileProvider::class.java.name }
        val authority = requireNotNull(provider.authority).substringBefore(';')
        val originalOverlay = bed.overlayLayout
        try {
            InstrumentationRegistry.getInstrumentation().context.assets
                .open("step_cube_ccby4.step").use { source ->
                    inputFile.outputStream().use(source::copyTo)
                }
            assertEquals(0, bed.currentModel?.objectsCount ?: 0)

            openWorkspaceMenu(activity, WorkspaceDestination.PROJECT, R.string.MenuProject)
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodesWithText(activity.getString(R.string.WorkspaceAddModelAction))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(activity.getString(R.string.WorkspaceAddModelAction))
                .assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 10_000) { capturedIntent.get() != null }
            assertEquals(Intent.ACTION_OPEN_DOCUMENT, requireNotNull(capturedIntent.get()).action)

            // A file manager may send a shared-storage file:// URL, which targetSdk 36 cannot read
            // without a SAF grant. The app must request a content:// document instead of failing.
            capturedIntent.set(null)
            activity.runOnUiThread {
                MainActivity::class.java.getDeclaredMethod("loadFile", Uri::class.java)
                    .apply { isAccessible = true }
                    .invoke(activity, Uri.parse("file:///sdcard/Download/step-file-manager-test.step"))
            }
            compose.waitUntil(timeoutMillis = 5_000) { capturedIntent.get() != null }
            assertEquals(Intent.ACTION_OPEN_DOCUMENT, requireNotNull(capturedIntent.get()).action)
            assertEquals("*/*", requireNotNull(capturedIntent.get()).type)
            assertEquals("The inaccessible file:// URL must not start import",
                0, bed.currentModel?.objectsCount ?: 0)

            val inputUri = FileProvider.getUriForFile(activity, authority, inputFile)
            activity.runOnUiThread {
                // Simulate the STEP file finishing its cache copy before the workspace overlay attaches.
                bed.overlayLayout = null
                MainActivity::class.java.getDeclaredMethod(
                    "dispatchActivityResult", Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType, Intent::class.java,
                ).apply { isAccessible = true }
                    .invoke(activity, requestCode, Activity.RESULT_OK, Intent().setData(inputUri))
                activity.window.decorView.postDelayed({ bed.overlayLayout = originalOverlay }, 350)
            }

            compose.waitUntil(timeoutMillis = 15_000) {
                compose.onAllNodesWithTag("step-import-confirm").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals("STEP geometry must wait for the user's meshing choice", 0,
                bed.currentModel?.objectsCount ?: 0)
            compose.onNodeWithTag("step-linear-deflection").assertIsDisplayed()
            compose.onNodeWithTag("step-angular-deflection").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("step-import-confirm").performClick()

            compose.waitUntil(timeoutMillis = 90_000) {
                val model = bed.currentModel
                model != null && model.objectsCount == 1 && model.getTriangleCount(0) > 0
            }
            assertTrue("the chosen STEP file should be cached for the native import",
                cachedImport.isFile && cachedImport.length() > 0L)
            assertTrue("the loaded model must contain renderable triangles",
                requireNotNull(bed.currentModel).getTriangleCount(0) > 0)
        } finally {
            activity.runOnUiThread { bed.overlayLayout = originalOverlay }
            launchers[requestCode] = previousLauncher
            inputFile.delete()
            cachedImport.delete()
        }
    }

    @Test
    fun selectedStepReturnedByDocumentPickerRestoresWorkspaceBeforeShowingOptions() {
        val activity = compose.activity
        val file = File(activity.cacheDir, "step-return-to-workspace-${System.nanoTime()}.step")
        val cachedImport = File(SliceBeam.getModelCacheDir(), file.name)
        try {
            InstrumentationRegistry.getInstrumentation().context.assets
                .open("step_cube_ccby4.step").use { source -> file.outputStream().use(source::copyTo) }
            val provider = activity.packageManager.getPackageInfo(
                activity.packageName,
                PackageManager.GET_PROVIDERS,
            ).providers.orEmpty().first { it.name == FileProvider::class.java.name }
            val authority = requireNotNull(provider.authority).substringBefore(';')
            val uri = FileProvider.getUriForFile(activity, authority, file)

            activity.runOnUiThread {
                (activity.navigationDelegate as ru.ytkab0bp.slicebeam.navigation.MobileNavigationDelegate)
                    .switchToSlot(ru.ytkab0bp.slicebeam.navigation.MobileNavigationPolicy.APP_SLOT)
            }
            compose.waitUntil(timeoutMillis = 10_000) {
                activity.navigationDelegate.getCurrentFragment() !is BedFragment &&
                    activity.navigationDelegate.getCurrentFragment().getView()?.isAttachedToWindow == true
            }

            activity.runOnUiThread {
                MainActivity::class.java.getDeclaredMethod(
                    "dispatchActivityResult", Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType, Intent::class.java,
                ).apply { isAccessible = true }
                    .invoke(
                        activity,
                        MainActivity.REQUEST_CODE_OPEN_FILE,
                        Activity.RESULT_OK,
                        Intent().setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                    )
            }

            compose.waitUntil(timeoutMillis = 15_000) {
                activity.navigationDelegate.getCurrentFragment() is BedFragment &&
                    compose.onAllNodesWithTag("step-import-confirm").fetchSemanticsNodes().isNotEmpty()
            }
            val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
            assertEquals("STEP must not be loaded before the meshing dialog is confirmed",
                0, bed.currentModel?.objectsCount ?: 0)
            compose.onNodeWithTag("step-linear-deflection").assertIsDisplayed()
            compose.onNodeWithTag("step-angular-deflection").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("step-import-confirm").performClick()

            compose.waitUntil(timeoutMillis = 90_000) {
                val model = bed.currentModel
                model != null && model.objectsCount == 1 && model.getTriangleCount(0) > 0
            }
            assertTrue("The STEP returned from SAF must render after options are confirmed",
                requireNotNull(bed.currentModel).getTriangleCount(0) > 0)
        } finally {
            file.delete()
            cachedImport.delete()
        }
    }

    @Test
    fun readableStepFileUriImportsFromOwnedCacheAndNeverDeletesTheSource() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val source = File(activity.cacheDir, "readable-step-uri-${System.nanoTime()}.step")
        val cached = File(SliceBeam.getModelCacheDir(), source.name)
        try {
            InstrumentationRegistry.getInstrumentation().context.assets
                .open("step_cube_ccby4.step").use { input ->
                source.outputStream().use(input::copyTo)
            }
            val openStep = {
                activity.runOnUiThread {
                    MainActivity::class.java.getDeclaredMethod("loadFile", Uri::class.java)
                        .apply { isAccessible = true }
                        .invoke(activity, Uri.fromFile(source))
                }
                compose.waitUntil(timeoutMillis = 10_000) {
                    compose.onAllNodesWithTag("step-import-confirm").fetchSemanticsNodes().isNotEmpty()
                }
            }

            // Cancel proves cleanup only targets the app-owned copy, never the selected file URI.
            openStep()
            assertTrue("The original source must still be present while options are shown", source.isFile)
            assertTrue("STEP import should operate on an app-owned cache copy", cached.isFile)
            compose.onNodeWithTag("step-import-cancel").performClick()
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodesWithTag("step-import-confirm").fetchSemanticsNodes().isEmpty()
            }
            assertTrue("Cancelling STEP import must preserve the original file", source.isFile)
            assertFalse("The cancelled app-owned copy should be cleaned up", cached.exists())

            // Confirm also imports through the cached copy while preserving the user's source.
            openStep()
            compose.onNodeWithTag("step-import-confirm").performClick()
            compose.waitUntil(timeoutMillis = 90_000) {
                val model = bed.currentModel
                model != null && model.objectsCount == 1 && model.getTriangleCount(0) > 0
            }
            assertTrue("Successful import must leave the original file untouched", source.isFile)
            assertTrue("The native importer should receive the cached STEP file", cached.isFile)
        } finally {
            source.delete()
            cached.delete()
        }
    }

    @Test
    fun externalStepDocumentUriShowsMeshingOptionsBeforeLoadingRenderableModel() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val file = File(activity.cacheDir, "external-step-view-${System.nanoTime()}.step")
        try {
            // STEP files opened from Android must work even if the app was last left on a global
            // page. External imports are routed back to the workspace, which owns the renderer
            // and the STEP settings dialog host.
            activity.runOnUiThread {
                (activity.navigationDelegate as ru.ytkab0bp.slicebeam.navigation.MobileNavigationDelegate)
                    .switchToSlot(ru.ytkab0bp.slicebeam.navigation.MobileNavigationPolicy.APP_SLOT)
            }
            compose.waitUntil(timeoutMillis = 10_000) {
                activity.navigationDelegate.getCurrentFragment() !is BedFragment &&
                    activity.navigationDelegate.getCurrentFragment().getView()?.isAttachedToWindow == true
            }
            InstrumentationRegistry.getInstrumentation().context.assets
                .open("step_cube_ccby4.step").use { source -> file.outputStream().use(source::copyTo) }
            val provider = activity.packageManager.getPackageInfo(
                activity.packageName,
                PackageManager.GET_PROVIDERS,
            ).providers.orEmpty().first { it.name == FileProvider::class.java.name }
            val authority = requireNotNull(provider.authority).substringBefore(';')
            val uri = FileProvider.getUriForFile(activity, authority, file)
            // Exercise both external actions through the same production dispatcher used by
            // onCreate/onNewIntent. Cancel VIEW first to prove no geometry is loaded prematurely.
            activity.runOnUiThread {
                MainActivity::class.java.getDeclaredMethod("handleExternalModelOpen", Intent::class.java)
                    .apply { isAccessible = true }
                    .invoke(activity, Intent(Intent.ACTION_VIEW).setData(uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithTag("step-import-confirm").fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals("External STEP open must wait for the user's meshing choice",
                0, bed.currentModel?.objectsCount ?: 0)
            compose.onNodeWithTag("step-linear-deflection").assertIsDisplayed()
            compose.onNodeWithTag("step-angular-deflection").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("step-import-cancel").performClick()
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodesWithTag("step-import-confirm").fetchSemanticsNodes().isEmpty()
            }
            assertEquals("Cancel must not load the source geometry", 0, bed.currentModel?.objectsCount ?: 0)

            activity.runOnUiThread {
                MainActivity::class.java.getDeclaredMethod("handleExternalModelOpen", Intent::class.java)
                    .apply { isAccessible = true }
                    .invoke(activity, Intent(Intent.ACTION_SEND).apply {
                        type = "model/step"
                        // Reproduce file providers that omit EXTRA_STREAM and expose the
                        // shared STEP URI only through ClipData.
                        clipData = ClipData.newRawUri("step model", uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    })
            }
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithTag("step-import-confirm").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("step-import-confirm").performClick()

            compose.waitUntil(timeoutMillis = 90_000) {
                val model = bed.currentModel
                model != null && model.objectsCount == 1 && model.getTriangleCount(0) > 0
            }
            assertTrue("The externally opened STEP must become a renderable mesh",
                requireNotNull(bed.currentModel).getTriangleCount(0) > 0)
        } finally {
            file.delete()
        }
    }

    @Test
    fun projectMenuImportsMultipleSelectedStlsSequentially() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val firstFile = File(activity.cacheDir, "menu-multi-import-${System.nanoTime()}-a.stl")
        val secondFile = File(activity.cacheDir, "menu-multi-import-${System.nanoTime()}-b.stl")
        val cachedFiles = listOf(
            File(SliceBeam.getModelCacheDir(), firstFile.name),
            File(SliceBeam.getModelCacheDir(), secondFile.name),
        )
        val requestCode = MainActivity.REQUEST_CODE_OPEN_FILE
        val launchersField = MainActivity::class.java.getDeclaredField("activityResultLaunchers")
            .apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val launchers = launchersField.get(activity) as MutableMap<Int, ActivityResultLauncher<Intent>>
        val previousLauncher = requireNotNull(launchers[requestCode])
        val capturedIntent = java.util.concurrent.atomic.AtomicReference<Intent?>()
        launchers[requestCode] = object : ActivityResultLauncher<Intent>() {
            override val contract: ActivityResultContract<Intent, *> =
                ActivityResultContracts.StartActivityForResult()

            override fun launch(input: Intent, options: ActivityOptionsCompat?) {
                capturedIntent.set(Intent(input))
            }

            override fun unregister() = Unit
        }
        @Suppress("DEPRECATION")
        val provider = activity.packageManager.getPackageInfo(
            activity.packageName,
            PackageManager.GET_PROVIDERS,
        ).providers.orEmpty().first { it.name == FileProvider::class.java.name }
        val authority = requireNotNull(provider.authority).substringBefore(';')
        try {
            listOf(firstFile, secondFile).forEach { file ->
                activity.assets.open("models/xyz_cube.stl").use { source ->
                    file.outputStream().use(source::copyTo)
                }
            }
            assertEquals(0, bed.currentModel?.objectsCount ?: 0)

            openWorkspaceMenu(activity, WorkspaceDestination.PROJECT, R.string.MenuProject)
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodesWithText(activity.getString(R.string.WorkspaceAddModelAction))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(activity.getString(R.string.WorkspaceAddModelAction))
                .assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 5_000) { capturedIntent.get() != null }
            assertTrue(requireNotNull(capturedIntent.get()).getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false))

            val firstUri = FileProvider.getUriForFile(activity, authority, firstFile)
            val secondUri = FileProvider.getUriForFile(activity, authority, secondFile)
            val clipData = android.content.ClipData.newUri(
                activity.contentResolver,
                "selected STL models",
                firstUri,
            ).apply { addItem(android.content.ClipData.Item(secondUri)) }
            val resultIntent = Intent().apply { setClipData(clipData) }
            activity.runOnUiThread {
                MainActivity::class.java.getDeclaredMethod(
                    "dispatchActivityResult", Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType, Intent::class.java,
                ).apply { isAccessible = true }
                    .invoke(activity, requestCode, android.app.Activity.RESULT_OK, resultIntent)
            }
            compose.waitUntil(timeoutMillis = 60_000) {
                bed.currentModel?.objectsCount == 2
            }
            assertEquals("both selected STLs should be imported in order", 2,
                requireNotNull(bed.currentModel).objectsCount)
            compose.waitUntil(timeoutMillis = 10_000) { cachedFiles.none(File::exists) }
            assertTrue("the sequential importer should remove each temporary cached STL after loading",
                cachedFiles.none(File::exists))
        } finally {
            launchers[requestCode] = previousLauncher
            firstFile.delete()
            secondFile.delete()
            cachedFiles.forEach(File::delete)
        }
    }

    @Test
    fun singlePlateProjectExportWritesReloadableThreeMfForTheActiveModel() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val modelFile = File(activity.cacheDir, "single-plate-export-model-${System.nanoTime()}.stl")
        val output = File(activity.cacheDir, "single-plate-export-${System.nanoTime()}.3mf")
        val currentConfigFile = SliceBeam.getCurrentConfigFile()
        val previousCurrentConfig = currentConfigFile.takeIf(File::isFile)?.readBytes()
        val requestCode = MainActivity.REQUEST_CODE_EXPORT_3MF
        val launchersField = MainActivity::class.java.getDeclaredField("activityResultLaunchers")
            .apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val launchers = launchersField.get(activity) as MutableMap<Int, ActivityResultLauncher<Intent>>
        val previousLauncher = requireNotNull(launchers[requestCode])
        val capturedIntent = java.util.concurrent.atomic.AtomicReference<Intent?>()
        launchers[requestCode] = object : ActivityResultLauncher<Intent>() {
            override val contract: ActivityResultContract<Intent, *> =
                ActivityResultContracts.StartActivityForResult()

            override fun launch(input: Intent, options: ActivityOptionsCompat?) {
                capturedIntent.set(Intent(input))
            }

            override fun unregister() = Unit
        }
        @Suppress("DEPRECATION")
        val provider = activity.packageManager.getPackageInfo(
            activity.packageName,
            PackageManager.GET_PROVIDERS,
        ).providers.orEmpty().first { it.name == FileProvider::class.java.name }
        val authority = requireNotNull(provider.authority).substringBefore(';')
        val outputUri = FileProvider.getUriForFile(activity, authority, output)
        var model: Model? = null

        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            model = loadSingleTestModel(bed, modelFile)
            assertEquals(1, requireNotNull(model).objectsCount)

            openWorkspaceMenu(activity, WorkspaceDestination.PROJECT, R.string.MenuProject)
            revealMenuAction(activity, R.string.MenuFileExport3mf)
            val exportAction = compose.onNodeWithContentDescription(activity.getString(R.string.MenuFileExport3mf))
            compose.waitUntil(timeoutMillis = 5_000) {
                try {
                    exportAction.assertIsEnabled()
                    true
                } catch (_: AssertionError) {
                    false
                }
            }
            assertSame(activity, bed.getContext())
            assertSame(bed, activity.navigationDelegate.getCurrentFragment())
            // Compose semantics dispatch is sufficient here and avoids reconnecting UiAutomation
            // during instrumentation on Android 16, which can abort the entire test process.
            exportAction.performClick()
            try {
                compose.waitUntil(timeoutMillis = 5_000) { capturedIntent.get() != null }
            } catch (error: Throwable) {
                val actionBounds = exportAction.fetchSemanticsNode().boundsInRoot
                val actionWindowBounds = exportAction.fetchSemanticsNode().boundsInWindow
                val panelNode = compose.onNodeWithTag("workspace-menu-panel").fetchSemanticsNode()
                val panelBounds = panelNode.boundsInRoot
                val panelWindowBounds = panelNode.boundsInWindow
                val rootBounds = compose.onAllNodes(isRoot()).fetchSemanticsNodes().map { it.boundsInRoot }
                throw AssertionError(
                    "Export click did not launch picker; action=$actionBounds " +
                        "windowAction=$actionWindowBounds panel=$panelBounds windowPanel=$panelWindowBounds " +
                        "root=$rootBounds",
                    error,
                )
            }

            val cancelledRequest = requireNotNull(capturedIntent.get())
            assertEquals(Intent.ACTION_CREATE_DOCUMENT, cancelledRequest.action)
            assertEquals("application/3mf", cancelledRequest.type)
            assertEquals("OrkanSlicer_project.3mf", cancelledRequest.getStringExtra(Intent.EXTRA_TITLE))
            activity.runOnUiThread {
                MainActivity::class.java.getDeclaredMethod(
                    "dispatchActivityResult", Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType, Intent::class.java,
                ).apply { isAccessible = true }
                    .invoke(activity, requestCode, android.app.Activity.RESULT_CANCELED, Intent())
            }
            assertFalse("cancelling the save picker must not create an export file", output.exists())
            capturedIntent.set(null)

            compose.onNodeWithContentDescription(activity.getString(R.string.MenuFileExport3mf)).performClick()
            compose.waitUntil(timeoutMillis = 5_000) { capturedIntent.get() != null }
            activity.runOnUiThread {
                MainActivity::class.java.getDeclaredMethod(
                    "dispatchActivityResult", Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType, Intent::class.java,
                ).apply { isAccessible = true }
                    .invoke(activity, requestCode, android.app.Activity.RESULT_OK, Intent().setData(outputUri))
            }
            compose.waitUntil(timeoutMillis = 15_000) { output.isFile && output.length() > 0 }
            java.util.zip.ZipFile(output).use { archive ->
                assertTrue("the output must contain a 3MF model entry", archive.getEntry("3D/3dmodel.model") != null)
            }
            val reloaded = Model(output, 1)
            try {
                assertEquals("exported 3MF must reload the active plate's object", 1, reloaded.objectsCount)
            } finally {
                reloaded.release()
            }
        } finally {
            launchers[requestCode] = previousLauncher
            output.delete()
            modelFile.delete()
            model?.release()
            if (previousCurrentConfig == null) currentConfigFile.delete()
            else currentConfigFile.writeBytes(previousCurrentConfig)
        }
    }

    @Test
    fun sliceMenuManualActionOpensFilamentAssignmentAndCancelOrBackPreservesModel() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val modelFile = File(activity.cacheDir, "slice-menu-filament-cancel-test.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            val model = loadSingleTestModel(bed, modelFile)
            val originalExtruder = model.getExtruder(0)
            assertFalse("test fixture must start without a slice result", bed.hasSliceResult())

            if (!usesCompactPortraitLayout(activity)) {
                // The landscape rail is a vertically scrolling Column (not a LazyColumn);
                // performScrollTo() cannot reliably bring its last child into view.
                compose.onNodeWithTag("workspace-navigation-rail")
                    .performTouchInput { swipeUp() }
            }
            openWorkspaceMenu(activity, WorkspaceDestination.SLICE, R.string.MenuSlice)
            waitForWorkspaceMenuGrid()
            revealMenuAction(activity, R.string.QuickSliceTitle)
            compose.onNodeWithText(activity.getString(R.string.QuickSliceTitle))
                .assertIsDisplayed().assertIsEnabled()
            revealMenuAction(activity, R.string.SmartSliceTitle)
            compose.onNodeWithText(activity.getString(R.string.SmartSliceTitle))
                .assertIsDisplayed().assertIsEnabled()
            revealMenuAction(activity, R.string.MenuSliceCurrentSettings)
            compose.waitForIdle()
            compose.onNodeWithContentDescription(activity.getString(R.string.MenuSliceCurrentSettings))
                .assertIsDisplayed().assertIsEnabled().performClick()
            awaitPreSliceDialog(bed, "pre-slice-title")

            compose.onNodeWithTag("pre-slice-title").assertIsDisplayed()
            compose.onNodeWithTag("pre-slice-cancel").performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("pre-slice-title").assertDoesNotExist()
            assertEquals("Cancel must not alter the model's filament tool", originalExtruder, model.getExtruder(0))
            assertFalse("Cancel must not start native slicing", bed.hasSliceResult())

            revealMenuAction(activity, R.string.MenuSliceCurrentSettings)
            compose.onNodeWithContentDescription(activity.getString(R.string.MenuSliceCurrentSettings))
                .assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 5_000) { bed.isWorkspaceMessageDialogAttached() }
            compose.onNodeWithTag("pre-slice-title").assertIsDisplayed()
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            compose.waitForIdle()
            compose.onNodeWithTag("pre-slice-title").assertDoesNotExist()
            assertEquals("Back must not alter the model's filament tool", originalExtruder, model.getExtruder(0))
            assertFalse("Back must not start native slicing", bed.hasSliceResult())
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun sliceMenuProducesResultAndExposesSafeResultActions() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val modelFile = File(activity.cacheDir, "slice-menu-result-actions-test.stl")
        val prefs = requireNotNull(Prefs.getPrefs())
        val oldPerformanceMode = Prefs.isPerformanceModeEnabled()
        val oldGatePrinter = prefs.getString("mmu_choice_printer", null)
        val oldGateChoices = prefs.getString("mmu_choice_gates", null)
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            val model = loadSingleTestModel(bed, modelFile)
            assertFalse("the fixture must start unsliced", bed.hasSliceResult())

            if (!usesCompactPortraitLayout(activity)) {
                // The workspace rail is a regular scrolling Column in landscape;
                // swipe it to reveal the last destination before invoking slicing.
                compose.onNodeWithTag("workspace-navigation-rail")
                    .performTouchInput { swipeUp() }
            }
            openWorkspaceMenu(activity, WorkspaceDestination.SLICE, R.string.MenuSlice)
            waitForWorkspaceMenuGrid()
            revealMenuAction(activity, R.string.MenuSliceCurrentSettings)
            compose.waitForIdle()
            compose.onNodeWithContentDescription(activity.getString(R.string.MenuSliceCurrentSettings))
                .assertIsDisplayed().assertIsEnabled().performClick()
            awaitPreSliceDialog(bed, "pre-slice-title")
            compose.onNodeWithTag("pre-slice-title").assertIsDisplayed()
            compose.onNodeWithTag("pre-slice-confirm").performClick()

            // The configured test printer may expose a cached physical-slot/material mismatch.
            val mismatchContinue = compose.onAllNodesWithText(
                activity.getString(R.string.PreSliceMaterialMismatchContinue),
            )
            if (mismatchContinue.fetchSemanticsNodes().isNotEmpty()) {
                mismatchContinue.get(0).assertIsDisplayed().performClick()
            }

            compose.waitUntil(timeoutMillis = 120_000) { bed.hasSliceResult() }
            assertTrue("native slicing must produce a non-empty G-code artifact",
                BedFragment.tempGCodePath.isFile && BedFragment.tempGCodePath.length() > 0L)
            val glView = requireNotNull(bed.glView)
            compose.waitUntil(timeoutMillis = 10_000) { glView.renderer.gcodeResult != null }
            val result = requireNotNull(glView.renderer.gcodeResult)
            compose.waitUntil(timeoutMillis = 10_000) { glView.renderer.viewer != null }
            assertTrue("sliced model should retain at least one assigned filament", model.getUsedFilaments(0).isNotEmpty())
            val viewer = requireNotNull(glView.renderer.viewer)
            val originalViewType = viewer.viewType
            val originalSeamsVisible = readGCodeOptionOnGl(
                glView,
                ru.ytkab0bp.slicebeam.slic3r.GCodeViewer.OPTION_TYPE_SEAMS,
            )

            // Slicing refreshes the same Slice menu from pre-slice actions to result actions.
            scrollMenuToIndex(0)
            compose.waitForIdle()
            compose.onNodeWithContentDescription(activity.getString(R.string.MenuSliceInfo))
                .performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithTag("slice-info-title").assertIsDisplayed()
            compose.onNodeWithTag("slice-info-scheme").assertIsDisplayed()
            compose.onNodeWithTag("slice-info-seams").assertIsDisplayed()
            val alternateScheme = activity.resources.getStringArray(R.array.GCodeColorSchemes).getOrNull(1)
            assertTrue("the result should offer multiple G-code color schemes", !alternateScheme.isNullOrBlank())
            compose.onNodeWithTag("slice-info-scheme").performClick()
            compose.onNodeWithText(requireNotNull(alternateScheme)).assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 10_000) { viewer.viewType != originalViewType }
            assertEquals("choosing a color scheme must update the rendered G-code view", 1, viewer.viewType)
            compose.onNodeWithTag("slice-info-seams").assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 5_000) {
                readGCodeOptionOnGl(glView, ru.ytkab0bp.slicebeam.slic3r.GCodeViewer.OPTION_TYPE_SEAMS) != originalSeamsVisible
            }
            compose.onNodeWithTag("slice-info-close").performClick()
            compose.waitUntil(timeoutMillis = 5_000) { !bed.isWorkspaceMessageDialogAttached() }
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodesWithTag("slice-info-title").fetchSemanticsNodes().isEmpty()
            }
            compose.waitForIdle()

            revealMenuAction(activity, R.string.MenuSliceLayers)
            val layersAction = compose.onNodeWithContentDescription(activity.getString(R.string.MenuSliceLayers))
                .assertIsDisplayed()
            layersAction.performClick()
            compose.waitUntil(timeoutMillis = 5_000) { bed.isWorkspaceToolPanelAttached("slice-layer-preview") }
            compose.onNodeWithTag("slice-layers-panel").assertIsDisplayed()
            compose.onNodeWithTag("workspace-docked-tool-panel").assertIsDisplayed()
            val rendererBounds = compose.onNodeWithTag("workspace-renderer-area").fetchSemanticsNode().boundsInRoot
            val controlsBounds = compose.onNodeWithTag("workspace-docked-tool-panel").fetchSemanticsNode().boundsInRoot
            val separatedHorizontally = rendererBounds.right <= controlsBounds.left + 1f ||
                controlsBounds.right <= rendererBounds.left + 1f
            val separatedVertically = rendererBounds.bottom <= controlsBounds.top + 1f ||
                controlsBounds.bottom <= rendererBounds.top + 1f
            assertTrue(
                "The layer controls must not cover the build plate renderer; " +
                    "renderer=$rendererBounds controls=$controlsBounds",
                separatedHorizontally || separatedVertically,
            )
            val layerTitleBounds = compose.onNodeWithTag("slice-layers-title")
                .fetchSemanticsNode().boundsInRoot
            assertTrue(
                "The layer controls title must be inside its visible dock; " +
                    "title=$layerTitleBounds controls=$controlsBounds",
                layerTitleBounds.width > 0f && layerTitleBounds.height > 0f &&
                    layerTitleBounds.center.x in controlsBounds.left..controlsBounds.right &&
                    layerTitleBounds.center.y in controlsBounds.top..controlsBounds.bottom,
            )
            compose.onNodeWithTag("slice-layers-range-title").assertIsDisplayed()
            compose.onNodeWithTag("slice-preview-size-25").assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 5_000) { result.previewSelection?.blockSize == 25 }
            assertEquals("layer preview control must update the active G-code range policy", 25,
                result.previewSelection?.blockSize)
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            compose.waitUntil(timeoutMillis = 5_000) { !bed.isWorkspaceToolPanelAttached("slice-layer-preview") }

            scrollMenuToIndex(4)
            compose.waitForIdle()
            compose.onNodeWithContentDescription(activity.getString(R.string.MenuSlicePerformance))
                .performScrollTo().assertIsDisplayed().performClick()
            assertEquals("performance result action must toggle the preference", !oldPerformanceMode,
                Prefs.isPerformanceModeEnabled())
            compose.onNodeWithContentDescription(activity.getString(R.string.MenuSlicePerformance))
                .assertIsDisplayed().performClick()
            assertEquals("second toggle must restore the original preference", oldPerformanceMode,
                Prefs.isPerformanceModeEnabled())
            assertTrue("result actions must not replace or release the active G-code result",
                glView.renderer.gcodeResult === result)

            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val chooserMonitor = instrumentation.addMonitor(
                IntentFilter(Intent.ACTION_CHOOSER),
                ActivityResult(Activity.RESULT_CANCELED, Intent()),
                true,
            )
            try {
                scrollMenuToIndex(3)
                compose.waitForIdle()
                compose.onNodeWithContentDescription(activity.getString(R.string.MenuSliceShare))
                    .performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
                compose.waitUntil(timeoutMillis = 5_000) { chooserMonitor.hits > 0 }
                assertTrue("Share should route through the Android system chooser", chooserMonitor.hits > 0)
                assertTrue("opening Share must keep the generated G-code available", bed.hasSliceResult() &&
                    BedFragment.tempGCodePath.isFile && BedFragment.tempGCodePath.length() > 0L)
                assertTrue("opening Share must preserve the active rendered result",
                    glView.renderer.gcodeResult === result)
            } finally {
                instrumentation.removeMonitor(chooserMonitor)
            }
        } finally {
            Prefs.setPerformanceModeEnabled(oldPerformanceMode)
            prefs.edit().apply {
                if (oldGatePrinter == null) remove("mmu_choice_printer") else putString("mmu_choice_printer", oldGatePrinter)
                if (oldGateChoices == null) remove("mmu_choice_gates") else putString("mmu_choice_gates", oldGateChoices)
            }.commit()
            modelFile.delete()
        }
    }

    @Test
    fun repeatedWorkspaceSliceReleasesOldNativeResultAfterGlViewerReplacement() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val modelFile = File(activity.cacheDir, "slice-result-replacement-${System.nanoTime()}.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            val model = loadSingleTestModel(bed, modelFile)
            if (!usesCompactPortraitLayout(activity)) {
                compose.onNodeWithTag("workspace-navigation-rail")
                    .performTouchInput { swipeUp() }
            }
            openWorkspaceMenu(activity, WorkspaceDestination.SLICE, R.string.MenuSlice)
            waitForWorkspaceMenuGrid()
            revealMenuAction(activity, R.string.MenuSliceCurrentSettings)
            compose.onNodeWithContentDescription(activity.getString(R.string.MenuSliceCurrentSettings))
                .assertIsDisplayed().assertIsEnabled().performClick()
            awaitPreSliceDialog(bed, "pre-slice-title")
            compose.onNodeWithTag("pre-slice-confirm").performClick()

            val mismatchContinue = compose.onAllNodesWithText(
                activity.getString(R.string.PreSliceMaterialMismatchContinue),
            )
            if (mismatchContinue.fetchSemanticsNodes().isNotEmpty()) {
                mismatchContinue.get(0).assertIsDisplayed().performClick()
            }

            val viewport = requireNotNull(bed.glView)
            compose.waitUntil(timeoutMillis = 120_000) {
                bed.hasSliceResult() && viewport.renderer.gcodeResult != null && viewport.renderer.viewer != null
            }
            val firstResult = requireNotNull(viewport.renderer.gcodeResult)
            val firstViewer = requireNotNull(viewport.renderer.viewer)
            assertTrue("first native result must own a live pointer", firstResult.pointer != 0L)

            // The first pre-slice confirmation prepared this exact model/profile/material state.
            // A second start therefore takes BedFragment's cached path straight into runSlice,
            // replacing the active GL viewer without another dialog.
            activity.runOnUiThread { bed.startSlicing() }
            compose.waitUntil(timeoutMillis = 120_000) {
                val current = viewport.renderer.gcodeResult
                current != null && current !== firstResult && firstResult.pointer == 0L
            }
            val secondResult = requireNotNull(viewport.renderer.gcodeResult)
            assertTrue("replacement result must remain live", secondResult.pointer != 0L)
            compose.waitUntil(timeoutMillis = 15_000) {
                viewport.renderer.viewer != null && viewport.renderer.viewer !== firstViewer
            }
            assertSame("re-slicing must preserve the editable workspace model", model, bed.currentModel)

            // Clearing the result route must detach the viewer before releasing the second native
            // result as well; this covers the matching clear path in the same real fragment.
            openWorkspaceMenu(activity, WorkspaceDestination.PROJECT, R.string.MenuProject)
            compose.waitUntil(timeoutMillis = 15_000) { secondResult.pointer == 0L }
            assertTrue("the second result should be released after leaving the slice viewer",
                secondResult.pointer == 0L)
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun sliceResultExportUsesDocumentPickerAndWritesTheGeneratedGcode() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val modelFile = File(activity.cacheDir, "slice-result-export-model-${System.nanoTime()}.stl")
        val output = File(activity.cacheDir, "slice-result-export-${System.nanoTime()}.gcode")
        val requestCode = MainActivity.REQUEST_CODE_EXPORT_GCODE
        val launchersField = MainActivity::class.java.getDeclaredField("activityResultLaunchers")
            .apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val launchers = launchersField.get(activity) as MutableMap<Int, ActivityResultLauncher<Intent>>
        val previousLauncher = requireNotNull(launchers[requestCode])
        val capturedIntent = java.util.concurrent.atomic.AtomicReference<Intent?>()
        launchers[requestCode] = object : ActivityResultLauncher<Intent>() {
            override val contract: ActivityResultContract<Intent, *> =
                ActivityResultContracts.StartActivityForResult()

            override fun launch(input: Intent, options: ActivityOptionsCompat?) {
                capturedIntent.set(Intent(input))
            }

            override fun unregister() = Unit
        }
        @Suppress("DEPRECATION")
        val provider = activity.packageManager.getPackageInfo(
            activity.packageName,
            PackageManager.GET_PROVIDERS,
        ).providers.orEmpty().first { it.name == FileProvider::class.java.name }
        val authority = requireNotNull(provider.authority).substringBefore(';')
        val outputUri = FileProvider.getUriForFile(activity, authority, output)
        val prefs = requireNotNull(Prefs.getPrefs())
        val oldPerformanceMode = Prefs.isPerformanceModeEnabled()
        val oldGatePrinter = prefs.getString("mmu_choice_printer", null)
        val oldGateChoices = prefs.getString("mmu_choice_gates", null)

        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            loadSingleTestModel(bed, modelFile)
            if (!usesCompactPortraitLayout(activity)) {
                compose.onNodeWithTag("workspace-navigation-rail")
                    .performTouchInput { swipeUp() }
            }
            openWorkspaceMenu(activity, WorkspaceDestination.SLICE, R.string.MenuSlice)
            waitForWorkspaceMenuGrid()
            revealMenuAction(activity, R.string.MenuSliceCurrentSettings)
            compose.waitForIdle()
            compose.onNodeWithContentDescription(activity.getString(R.string.MenuSliceCurrentSettings))
                .assertIsDisplayed().performClick()
            awaitPreSliceDialog(bed, "pre-slice-confirm")
            compose.onNodeWithTag("pre-slice-confirm").performClick()

            val mismatchContinue = compose.onAllNodesWithText(
                activity.getString(R.string.PreSliceMaterialMismatchContinue),
            )
            if (mismatchContinue.fetchSemanticsNodes().isNotEmpty()) {
                mismatchContinue.get(0).assertIsDisplayed().performClick()
            }
            compose.waitUntil(timeoutMillis = 120_000) {
                bed.hasSliceResult() && BedFragment.tempGCodePath.isFile && BedFragment.tempGCodePath.length() > 0
            }

            revealMenuAction(activity, R.string.MenuSliceExportToFile)
            compose.onNodeWithContentDescription(activity.getString(R.string.MenuSliceExportToFile))
                .assertIsDisplayed().assertIsEnabled().performClick()
            val request = requireNotNull(capturedIntent.get())
            assertEquals(Intent.ACTION_CREATE_DOCUMENT, request.action)
            assertEquals("application/x-gcode", request.type)
            assertEquals(requireNotNull(bed.glView).renderer.gcodeResult!!.getRecommendedName(),
                request.getStringExtra(Intent.EXTRA_TITLE))

            activity.runOnUiThread {
                MainActivity::class.java.getDeclaredMethod(
                    "dispatchActivityResult", Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType, Intent::class.java,
                ).apply { isAccessible = true }
                    .invoke(activity, requestCode, android.app.Activity.RESULT_CANCELED, Intent())
            }
            assertFalse("canceling the G-code save must not create an output file", output.exists())

            capturedIntent.set(null)
            compose.onNodeWithText(activity.getString(R.string.GCodeExportShort))
                .assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 5_000) { capturedIntent.get() != null }
            activity.runOnUiThread {
                MainActivity::class.java.getDeclaredMethod(
                    "dispatchActivityResult", Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType, Intent::class.java,
                ).apply { isAccessible = true }
                    .invoke(activity, requestCode, android.app.Activity.RESULT_OK, Intent().setData(outputUri))
            }
            compose.waitUntil(timeoutMillis = 10_000) { output.isFile && output.length() > 0L }
            assertTrue("export must preserve the generated G-code bytes",
                output.readBytes().contentEquals(BedFragment.tempGCodePath.readBytes()))
        } finally {
            launchers[requestCode] = previousLauncher
            Prefs.setPerformanceModeEnabled(oldPerformanceMode)
            prefs.edit().apply {
                if (oldGatePrinter == null) remove("mmu_choice_printer") else putString("mmu_choice_printer", oldGatePrinter)
                if (oldGateChoices == null) remove("mmu_choice_gates") else putString("mmu_choice_gates", oldGateChoices)
            }.commit()
            output.delete()
            modelFile.delete()
        }
    }

    @Test
    fun multiPlateProjectExportWarnsAndCancelDoesNotOpenDocumentPicker() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val modelFile = File(activity.cacheDir, "menu-multiplate-export-warning-test.stl")
        val output = File(activity.cacheDir, "menu-multiplate-export-warning-${System.nanoTime()}.3mf")
        val requestCode = MainActivity.REQUEST_CODE_EXPORT_3MF
        val launchersField = MainActivity::class.java.getDeclaredField("activityResultLaunchers")
            .apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val launchers = launchersField.get(activity) as MutableMap<Int, ActivityResultLauncher<Intent>>
        val previousLauncher = requireNotNull(launchers[requestCode])
        val capturedIntent = java.util.concurrent.atomic.AtomicReference<Intent?>()
        launchers[requestCode] = object : ActivityResultLauncher<Intent>() {
            override val contract: ActivityResultContract<Intent, *> =
                ActivityResultContracts.StartActivityForResult()

            override fun launch(input: Intent, options: ActivityOptionsCompat?) {
                capturedIntent.set(Intent(input))
            }

            override fun unregister() = Unit
        }
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            loadSingleTestModel(bed, modelFile)
            assertEquals(1, bed.plateCount)

            compose.onNodeWithContentDescription(activity.getString(R.string.PlateNumber, "1"))
                .assertIsDisplayed().performClick()
            compose.onNodeWithContentDescription(activity.getString(R.string.MenuAddPlate))
                .assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 10_000) { bed.plateCount == 2 && bed.currentPlateIndex == 1 }
            compose.onNodeWithContentDescription(activity.getString(R.string.PlateNumber, "2"))
                .assertIsDisplayed().performClick()
            compose.onNodeWithContentDescription(
                activity.getString(R.string.PlatePickerSelectDescription, "1"),
            ).assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 10_000) { bed.currentPlateIndex == 0 }

            openWorkspaceMenu(activity, WorkspaceDestination.PROJECT, R.string.MenuProject)
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodesWithTag("workspace-menu-panel").fetchSemanticsNodes().isNotEmpty()
            }
            compose.waitForIdle()
            revealMenuAction(activity, R.string.MenuFileExport3mf)
            val exportAction = compose.onNodeWithContentDescription(activity.getString(R.string.MenuFileExport3mf))
            exportAction.assertIsDisplayed().assertIsEnabled()
            exportAction.performClick()
            compose.waitUntil(timeoutMillis = 5_000) { bed.isWorkspaceMessageDialogAttached() }

            compose.onNodeWithText(activity.getString(R.string.Project3mfExportActivePlateTitle))
                .assertIsDisplayed()
            // Long translations at large system font sizes must remain scrollable. Assert the
            // bounded scroll viewport is visible and can scroll instead of requiring the full
            // message's tall semantics bounds to fit on screen at once.
            compose.onNodeWithTag("workspace-message-scroll")
                .assertIsDisplayed()
                .performTouchInput { swipeUp() }
            compose.waitForIdle()
            compose.onNodeWithTag("workspace-message-scroll").assertIsDisplayed()
            compose.onNodeWithText(activity.getString(R.string.Project3mfExportActivePlateConfirm))
                .assertIsDisplayed()
            compose.onNodeWithText(activity.getString(android.R.string.cancel))
                .assertIsDisplayed().performClick()
            compose.waitForIdle()

            compose.onNodeWithText(activity.getString(R.string.Project3mfExportActivePlateTitle))
                .assertDoesNotExist()
            assertEquals("cancel must preserve the two-plate workspace", 2, bed.plateCount)
            assertEquals("cancel must preserve the active plate", 0, bed.currentPlateIndex)
            assertEquals("cancel must not mutate the model", 1, bed.currentModel?.objectsCount)
            assertEquals("dismissing the warning must not launch the document picker", null, capturedIntent.get())

            compose.onNodeWithText(activity.getString(R.string.MenuFileExport3mf))
                .assertIsDisplayed().performClick()
            compose.onNodeWithText(activity.getString(R.string.Project3mfExportActivePlateConfirm))
                .assertIsDisplayed().performClick()

            val request = requireNotNull(capturedIntent.get())
            assertEquals(Intent.ACTION_CREATE_DOCUMENT, request.action)
            assertEquals("application/3mf", request.type)
            assertEquals("OrkanSlicer_plate_1.3mf", request.getStringExtra(Intent.EXTRA_TITLE))

            activity.runOnUiThread {
                MainActivity::class.java.getDeclaredMethod(
                    "dispatchActivityResult", Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType, Intent::class.java,
                ).apply { isAccessible = true }
                    .invoke(activity, requestCode, android.app.Activity.RESULT_CANCELED, Intent())
            }
            assertFalse("cancelling the document picker must not create an export file", output.exists())
            assertEquals("the confirmed export is limited to the selected plate", 2, bed.plateCount)
            assertEquals("the confirmed export must not switch the selected plate", 0, bed.currentPlateIndex)
            compose.onNodeWithContentDescription(activity.getString(R.string.PlateNumber, "1"))
                .assertIsDisplayed().performClick()
            compose.onNodeWithContentDescription(
                activity.getString(R.string.PlateCloseDescription,
                    activity.getString(R.string.PlateNumber, "2")),
            ).assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                bed.plateCount == 1 && bed.currentPlateIndex == 0
            }
            assertEquals("closing the empty second plate must retain the model on the first plate",
                1, bed.currentModel?.objectsCount)
            compose.onNodeWithContentDescription(activity.getString(R.string.PlateNumber, "1"))
                .assertIsDisplayed()
        } finally {
            launchers[requestCode] = previousLauncher
            output.delete()
            modelFile.delete()
        }
    }

    @Test
    fun toolsMenuProfileExportActionOpensComposeFlowAndAndroidBackDismissesIt() {
        val activity = compose.activity
        compose.onNodeWithContentDescription(activity.getString(R.string.MenuOptions))
            .assertIsDisplayed()
            .performClick()
        revealMenuAction(activity, R.string.MenuFileExportProfiles)
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithContentDescription(activity.getString(R.string.MenuFileExportProfiles))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription(activity.getString(R.string.MenuFileExportProfiles))
            .assertIsDisplayed()
            .performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(activity.getString(R.string.MenuFileExportProfilesPrints))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(activity.getString(R.string.MenuFileExportProfilesPrints)).assertIsDisplayed()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        compose.onNodeWithText(activity.getString(R.string.MenuFileExportProfilesPrints)).assertDoesNotExist()
    }

    @Test
    fun toolsMenuProfileImportBuildsCorrectDocumentPickerRequest() {
        val activity = compose.activity
        val requestCode = MainActivity.REQUEST_CODE_IMPORT_PROFILES
        val launchersField = MainActivity::class.java.getDeclaredField("activityResultLaunchers")
            .apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val launchers = launchersField.get(activity) as MutableMap<Int, ActivityResultLauncher<Intent>>
        val previousLauncher = requireNotNull(launchers[requestCode])
        val capturedIntent = java.util.concurrent.atomic.AtomicReference<Intent?>()
        launchers[requestCode] = object : ActivityResultLauncher<Intent>() {
            override val contract: ActivityResultContract<Intent, *> =
                ActivityResultContracts.StartActivityForResult()

            override fun launch(input: Intent, options: ActivityOptionsCompat?) {
                capturedIntent.set(Intent(input))
            }

            override fun unregister() = Unit
        }
        try {
            compose.onNodeWithContentDescription(activity.getString(R.string.MenuOptions))
                .assertIsDisplayed()
                .performClick()
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodesWithContentDescription(activity.getString(R.string.MenuFileImportProfiles))
                    .fetchSemanticsNodes().isNotEmpty()
            }

            compose.onNodeWithContentDescription(activity.getString(R.string.MenuFileImportProfiles))
                .assertIsDisplayed()
                .performClick()
            val request = requireNotNull(capturedIntent.get())
            assertEquals(Intent.ACTION_OPEN_DOCUMENT, request.action)
            assertEquals("*/*", request.type)
            assertTrue(request.categories?.contains(Intent.CATEGORY_OPENABLE) == true)
            assertFalse(request.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false))
            assertTrue(request.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)?.contains("text/plain") == true)
            compose.onNodeWithContentDescription(activity.getString(R.string.MenuFileImportProfiles)).assertIsDisplayed()
            assertFalse("opening the picker must not show an import dialog before a result is selected",
                (activity.navigationDelegate.getCurrentFragment() as BedFragment).isWorkspaceMessageDialogAttached())
        } finally {
            launchers[requestCode] = previousLauncher
        }
    }

    @Test
    fun toolsMenuProfileImportAppliesSelectedProfileAndRestoresFixtureState() {
        val activity = compose.activity
        val requestCode = MainActivity.REQUEST_CODE_IMPORT_PROFILES
        val launchersField = MainActivity::class.java.getDeclaredField("activityResultLaunchers")
            .apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val launchers = launchersField.get(activity) as MutableMap<Int, ActivityResultLauncher<Intent>>
        val previousLauncher = requireNotNull(launchers[requestCode])
        val capturedIntent = AtomicReference<Intent?>()
        val importedTitle = "Codex UI Import ${System.nanoTime()}"
        val input = File(activity.cacheDir, "codex-profile-import.ini")
        input.writeText("[print:$importedTitle]\nlayer_height = 0.17\n", StandardCharsets.UTF_8)

        val originalConfig = SliceBeam.requireConfig()
        val configSnapshot = Slic3rConfigWrapper(originalConfig.serialize().byteInputStream(StandardCharsets.UTF_8))
        val configFile = SliceBeam.getConfigFile()
        val persistedConfig = configFile.takeIf { it.isFile }?.readBytes()
        @Suppress("DEPRECATION")
        val provider = activity.packageManager.getPackageInfo(
            activity.packageName,
            PackageManager.GET_PROVIDERS,
        ).providers.orEmpty().first { it.name == FileProvider::class.java.name }
        val authority = requireNotNull(provider.authority).substringBefore(';')
        val inputUri = FileProvider.getUriForFile(activity, authority, input)

        launchers[requestCode] = object : ActivityResultLauncher<Intent>() {
            override val contract: ActivityResultContract<Intent, *> =
                ActivityResultContracts.StartActivityForResult()

            override fun launch(input: Intent, options: ActivityOptionsCompat?) {
                capturedIntent.set(Intent(input))
            }

            override fun unregister() = Unit
        }
        try {
            compose.onNodeWithContentDescription(activity.getString(R.string.MenuOptions))
                .assertIsDisplayed().performClick()
            fun chooseImportFile() {
                capturedIntent.set(null)
                compose.onNodeWithContentDescription(activity.getString(R.string.MenuFileImportProfiles))
                    .assertIsDisplayed().performClick()
                val pickerIntent = requireNotNull(capturedIntent.get())
                assertEquals(Intent.ACTION_OPEN_DOCUMENT, pickerIntent.action)
                val result = Intent().setData(inputUri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                activity.runOnUiThread {
                    MainActivity::class.java.getDeclaredMethod(
                        "dispatchActivityResult", Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType, Intent::class.java,
                    ).apply { isAccessible = true }
                        .invoke(activity, requestCode, Activity.RESULT_OK, result)
                }
            }

            assertTrue("profile must not exist before selecting the import result",
                SliceBeam.requireConfig().findPrint(importedTitle) == null)
            chooseImportFile()
            compose.waitUntil(timeoutMillis = 15_000) {
                compose.onAllNodesWithText(importedTitle).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(activity.getString(R.string.MenuFileExportProfilesPrints))
                .assertIsDisplayed()
            compose.onNodeWithText(importedTitle).assertIsDisplayed()

            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodesWithText(importedTitle).fetchSemanticsNodes().isEmpty()
            }
            assertTrue("Android Back must cancel profile import without mutation",
                SliceBeam.requireConfig().findPrint(importedTitle) == null)

            chooseImportFile()
            compose.waitUntil(timeoutMillis = 15_000) {
                compose.onAllNodesWithText(importedTitle).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(activity.getString(android.R.string.cancel))
                .assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodesWithText(importedTitle).fetchSemanticsNodes().isEmpty()
            }
            assertTrue("Cancel must discard the selected profile without mutation",
                SliceBeam.requireConfig().findPrint(importedTitle) == null)

            chooseImportFile()
            compose.waitUntil(timeoutMillis = 15_000) {
                compose.onAllNodesWithText(importedTitle).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(activity.getString(android.R.string.ok)).assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                SliceBeam.requireConfig().findPrint(importedTitle) != null
            }
            assertEquals("selected profile settings must be imported", "0.17",
                SliceBeam.requireConfig().findPrint(importedTitle)?.get("layer_height"))

            val existingProfileName = "0.16mm Standard"
            val existingProfile = requireNotNull(SliceBeam.requireConfig().findPrint(existingProfileName))
            val existingLayerHeight = existingProfile["layer_height"]
            assertTrue("the selected printer fixture should include the conflict target", existingLayerHeight != null)
            input.writeText("[print:$existingProfileName]\nlayer_height = 0.19\n", StandardCharsets.UTF_8)
            capturedIntent.set(null)
            compose.onNodeWithContentDescription(activity.getString(R.string.MenuFileImportProfiles))
                .assertIsDisplayed().performClick()
            assertEquals(Intent.ACTION_OPEN_DOCUMENT, requireNotNull(capturedIntent.get()).action)

            val conflictResult = Intent().setData(inputUri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            activity.runOnUiThread {
                MainActivity::class.java.getDeclaredMethod(
                    "dispatchActivityResult", Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType, Intent::class.java,
                ).apply { isAccessible = true }
                    .invoke(activity, requestCode, Activity.RESULT_OK, conflictResult)
            }
            val existingLabel = activity.getString(R.string.MenuFileImportProfilesExisting, existingProfileName)
            compose.waitUntil(timeoutMillis = 15_000) {
                compose.onAllNodesWithText(existingLabel).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(existingLabel).assertIsDisplayed()
            compose.onNodeWithText(activity.getString(android.R.string.ok)).assertIsDisplayed().performClick()
            compose.onNodeWithText(activity.getString(R.string.MenuFileImportProfilesConflictTitle))
                .assertIsDisplayed()
            compose.onNodeWithText(activity.getString(R.string.MenuFileImportProfilesSkipExisting))
                .assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithText(activity.getString(R.string.MenuFileImportProfilesConflictTitle))
                    .fetchSemanticsNodes().isEmpty()
            }
            assertEquals("skip existing must keep the stored process value", existingLayerHeight,
                SliceBeam.requireConfig().findPrint(existingProfileName)?.get("layer_height"))

            input.writeText("[print:$existingProfileName]\nlayer_height = 0.19\n", StandardCharsets.UTF_8)
            capturedIntent.set(null)
            compose.onNodeWithContentDescription(activity.getString(R.string.MenuFileImportProfiles))
                .assertIsDisplayed().performClick()
            assertEquals(Intent.ACTION_OPEN_DOCUMENT, requireNotNull(capturedIntent.get()).action)
            val overwriteResult = Intent().setData(inputUri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            activity.runOnUiThread {
                MainActivity::class.java.getDeclaredMethod(
                    "dispatchActivityResult", Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType, Intent::class.java,
                ).apply { isAccessible = true }
                    .invoke(activity, requestCode, Activity.RESULT_OK, overwriteResult)
            }
            compose.waitUntil(timeoutMillis = 15_000) {
                compose.onAllNodesWithText(existingLabel).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(activity.getString(android.R.string.ok)).assertIsDisplayed().performClick()
            compose.onNodeWithText(activity.getString(R.string.MenuFileImportProfilesConflictTitle))
                .assertIsDisplayed()
            compose.onNodeWithText(activity.getString(R.string.MenuFileImportProfilesOverwrite))
                .assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                SliceBeam.requireConfig().findPrint(existingProfileName)?.get("layer_height") == "0.19"
            }
            assertEquals("overwrite must apply only after the explicit overwrite choice", "0.19",
                SliceBeam.requireConfig().findPrint(existingProfileName)?.get("layer_height"))
        } finally {
            launchers[requestCode] = previousLauncher
            SliceBeam.CONFIG = configSnapshot
            if (persistedConfig == null) configFile.delete() else configFile.writeBytes(persistedConfig)
            input.delete()
        }
    }

    @Test
    fun toolsMenuProfileExportWritesSelectedProfilesToTheRequestedDocument() {
        val activity = compose.activity
        val requestCode = MainActivity.REQUEST_CODE_EXPORT_PROFILES
        val launchersField = MainActivity::class.java.getDeclaredField("activityResultLaunchers")
            .apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val launchers = launchersField.get(activity) as MutableMap<Int, ActivityResultLauncher<Intent>>
        val previousLauncher = requireNotNull(launchers[requestCode])
        val capturedIntent = java.util.concurrent.atomic.AtomicReference<Intent?>()
        launchers[requestCode] = object : ActivityResultLauncher<Intent>() {
            override val contract: ActivityResultContract<Intent, *> =
                ActivityResultContracts.StartActivityForResult()

            override fun launch(input: Intent, options: ActivityOptionsCompat?) {
                capturedIntent.set(Intent(input))
            }

            override fun unregister() = Unit
        }

        val previousPrints = MainActivity.EXPORTING_PRINTS
        val previousFilaments = MainActivity.EXPORTING_FILAMENTS
        val previousPrinters = MainActivity.EXPORTING_PRINTERS
        val output = File(activity.cacheDir, "codex-profile-export-${System.nanoTime()}.ini")
        @Suppress("DEPRECATION")
        val provider = activity.packageManager.getPackageInfo(
            activity.packageName,
            PackageManager.GET_PROVIDERS,
        ).providers.orEmpty().first { it.name == FileProvider::class.java.name }
        val authority = requireNotNull(provider.authority).substringBefore(';')
        val outputUri = FileProvider.getUriForFile(activity, authority, output)

        try {
            compose.onNodeWithContentDescription(activity.getString(R.string.MenuOptions))
                .assertIsDisplayed().performClick()
            revealMenuAction(activity, R.string.MenuFileExportProfiles)
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodesWithContentDescription(activity.getString(R.string.MenuFileExportProfiles))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription(activity.getString(R.string.MenuFileExportProfiles)).performClick()
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodesWithText(activity.getString(R.string.MenuFileExportProfilesPrints))
                    .fetchSemanticsNodes().isNotEmpty()
            }

            repeat(2) {
                compose.onNodeWithText(activity.getString(R.string.IntroNext)).performClick()
                compose.waitForIdle()
            }
            compose.onNodeWithContentDescription(activity.getString(R.string.MenuFileExportProfiles))
                .assertIsDisplayed()
            val exportDialogActions = compose.onAllNodesWithText(activity.getString(R.string.MenuFileExportProfiles))
            val exportDialogActionIndex = exportDialogActions.fetchSemanticsNodes().lastIndex
            assertTrue("the profile export dialog should expose its final action", exportDialogActionIndex >= 0)
            exportDialogActions.get(exportDialogActionIndex).assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 5_000) {
                capturedIntent.get() != null
            }

            val request = requireNotNull(capturedIntent.get())
            assertEquals(Intent.ACTION_CREATE_DOCUMENT, request.action)
            assertEquals("application/ini", request.type)
            assertEquals("OrcaSlicerMobile_config_bundle.ini", request.getStringExtra(Intent.EXTRA_TITLE))
            assertTrue(MainActivity.EXPORTING_PRINTS.orEmpty().isNotEmpty())
            assertTrue(MainActivity.EXPORTING_FILAMENTS.orEmpty().isNotEmpty())
            assertTrue(MainActivity.EXPORTING_PRINTERS.orEmpty().isNotEmpty())
            val selectedPrintCount = MainActivity.EXPORTING_PRINTS.orEmpty().size
            val selectedFilamentCount = MainActivity.EXPORTING_FILAMENTS.orEmpty().size
            val selectedPrinterCount = MainActivity.EXPORTING_PRINTERS.orEmpty().size

            activity.runOnUiThread {
                MainActivity::class.java.getDeclaredMethod(
                    "dispatchActivityResult", Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType, Intent::class.java,
                ).apply { isAccessible = true }
                    .invoke(activity, requestCode, android.app.Activity.RESULT_OK, Intent().setData(outputUri))
            }
            compose.waitUntil(timeoutMillis = 10_000) { output.isFile && output.length() > 0 }
            val exported = Slic3rConfigWrapper(output)
            assertEquals("export must contain all selected print profiles",
                selectedPrintCount, exported.printConfigs.size)
            assertEquals("export must contain all selected filament profiles",
                selectedFilamentCount, exported.filamentConfigs.size)
            assertEquals("export must contain all selected printer profiles",
                selectedPrinterCount, exported.printerConfigs.size)
        } finally {
            launchers[requestCode] = previousLauncher
            MainActivity.EXPORTING_PRINTS = previousPrints
            MainActivity.EXPORTING_FILAMENTS = previousFilaments
            MainActivity.EXPORTING_PRINTERS = previousPrinters
            output.delete()
        }
    }

    @Test
    fun cameraMenuActionsUpdateProjectionAndCameraControlPreference() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val initialGlView = requireNotNull(bed.glView)
        val projectionBefore = readProjectionMatrixOnGl(initialGlView)
        val previousProjection = Prefs.isOrthoProjectionEnabled()
        val previousControlMode = Prefs.getCameraControlMode()
        try {
            // Camera options moved from the removed workspace destination into App settings;
            // the view cube remains the persistent way to change standard camera direction.
            activity.runOnUiThread {
                (activity.navigationDelegate as ru.ytkab0bp.slicebeam.navigation.MobileNavigationDelegate).switchToSlot(5)
            }
            compose.waitUntil(timeoutMillis = 10_000) {
                activity.navigationDelegate.getCurrentFragment() is ru.ytkab0bp.slicebeam.fragment.SettingsFragment &&
                    compose.onAllNodesWithText(activity.getString(R.string.SettingsInterface))
                        .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(activity.getString(R.string.MenuCamera))
                .performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithText(activity.getString(R.string.SettingsCameraProjection))
                .performScrollTo().assertIsDisplayed()

            compose.onNodeWithText(activity.getString(R.string.SettingsCameraProjection))
                .performClick()
            compose.waitUntil(timeoutMillis = 5_000) {
                Prefs.isOrthoProjectionEnabled() != previousProjection
            }
            activity.runOnUiThread {
                (activity.navigationDelegate as ru.ytkab0bp.slicebeam.navigation.MobileNavigationDelegate)
                    .switchToSlot(0)
            }
            compose.waitUntil(timeoutMillis = 10_000) {
                activity.navigationDelegate.getCurrentFragment() is BedFragment
            }
            val resumedGlView = requireNotNull(
                (activity.navigationDelegate.getCurrentFragment() as BedFragment).glView,
            )
            compose.waitUntil(timeoutMillis = 5_000) {
                val projectionAfter = readProjectionMatrixOnGl(resumedGlView)
                projectionAfter.indices.any { index ->
                    kotlin.math.abs(projectionAfter[index] - projectionBefore[index]) > 1e-9
                }
            }
            assertEquals("App camera projection setting should update the persisted preference",
                !previousProjection, Prefs.isOrthoProjectionEnabled())

            activity.runOnUiThread {
                (activity.navigationDelegate as ru.ytkab0bp.slicebeam.navigation.MobileNavigationDelegate)
                    .switchToSlot(5)
            }
            compose.waitUntil(timeoutMillis = 10_000) {
                activity.navigationDelegate.getCurrentFragment() is ru.ytkab0bp.slicebeam.fragment.SettingsFragment
            }
            if (compose.onAllNodesWithText(activity.getString(R.string.SettingsCameraProjection))
                    .fetchSemanticsNodes().isEmpty()
            ) {
                compose.onNodeWithText(activity.getString(R.string.MenuCamera))
                    .performScrollTo().assertIsDisplayed().performClick()
            }
            compose.onNodeWithText(activity.getString(R.string.MenuCameraControlMode))
                .performScrollTo().assertIsDisplayed().performClick()
            val targetMode = (previousControlMode + 1) % 3
            val targetLabel = activity.getString(
                when (targetMode) {
                    0 -> R.string.MenuCameraControlModeOne
                    1 -> R.string.MenuCameraControlModeTwo
                    else -> R.string.MenuCameraControlModeThree
                },
            )
            compose.onNodeWithText(targetLabel, substring = true).assertIsDisplayed().performClick()
            compose.waitForIdle()
            assertEquals("App camera control setting should update its preference",
                targetMode, Prefs.getCameraControlMode())
        } finally {
            Prefs.setOrthoProjectionEnabled(previousProjection)
            Prefs.setCameraControlMode(previousControlMode)
            val restoreGlView = (activity.navigationDelegate.getCurrentFragment() as? BedFragment)?.glView
                ?: initialGlView
            restoreGlView.queueEvent {
                restoreGlView.renderer.updateProjection()
                restoreGlView.requestRender()
            }
        }
    }

    @Test
    fun cameraPresetTilesUpdateTheRendererPoseForEveryPreset() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        compose.onNodeWithTag("workspace-view-cube").assertIsDisplayed()

        val glView = requireNotNull(bed.glView)
        val modelBed = glView.renderer.bed!!
        val min = requireNotNull(modelBed.getVolumeMin())
        val max = requireNotNull(modelBed.getVolumeMax())
        val minVector = CameraVector(min.x, min.y, min.z)
        val maxVector = CameraVector(max.x, max.y, max.z)
        val presets = listOf(
            R.string.ViewCubeTop to CameraPreset.TOP,
            R.string.ViewCubeBottom to CameraPreset.BOTTOM,
            R.string.ViewCubeFront to CameraPreset.FRONT,
            R.string.ViewCubeBack to CameraPreset.BACK,
            R.string.ViewCubeLeft to CameraPreset.LEFT,
            R.string.ViewCubeRight to CameraPreset.RIGHT,
        )
        presets.forEach { (faceLabel, preset) ->
            val target = CameraPresetGeometry.resolve(preset, minVector, maxVector)
            performViewCubeFace(faceLabel)
            compose.waitUntil(timeoutMillis = 5_000) {
                val pose = readCameraPoseOnGl(glView)
                kotlin.math.abs(pose[0] - target.origin.x) < 0.1 &&
                    kotlin.math.abs(pose[1] - target.origin.y) < 0.1 &&
                    kotlin.math.abs(pose[2] - target.origin.z) < 0.1 &&
                    kotlin.math.abs(pose[3] - target.position.x) < 0.1 &&
                    kotlin.math.abs(pose[4] - target.position.y) < 0.1 &&
                    kotlin.math.abs(pose[5] - target.position.z) < 0.1
            }
            // Let the renderer's spring animation settle before requesting the next preset.
            SystemClock.sleep(900L)
            compose.waitForIdle()
            val pose = readCameraPoseOnGl(glView)
            assertTrue(
                "${activity.getString(faceLabel)} must apply its configured renderer camera pose",
                kotlin.math.abs(pose[0] - target.origin.x) < 0.1 &&
                    kotlin.math.abs(pose[1] - target.origin.y) < 0.1 &&
                    kotlin.math.abs(pose[2] - target.origin.z) < 0.1 &&
                    kotlin.math.abs(pose[3] - target.position.x) < 0.1 &&
                    kotlin.math.abs(pose[4] - target.position.y) < 0.1 &&
                    kotlin.math.abs(pose[5] - target.position.z) < 0.1,
            )
        }
    }

    @Test
    fun rapidCameraPresetChangesKeepTheLatestSelection() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val modelBed = requireNotNull(glView.renderer.bed)
        val min = requireNotNull(modelBed.getVolumeMin())
        val max = requireNotNull(modelBed.getVolumeMax())
        val target = CameraPresetGeometry.resolve(
            CameraPreset.RIGHT,
            CameraVector(min.x, min.y, min.z),
            CameraVector(max.x, max.y, max.z),
        )

        compose.onNodeWithTag("workspace-view-cube").assertIsDisplayed()
        performViewCubeFace(R.string.ViewCubeTop)
        performViewCubeFace(R.string.ViewCubeRight)

        compose.waitUntil(timeoutMillis = 5_000) {
            val pose = readCameraPoseOnGl(glView)
            kotlin.math.abs(pose[0] - target.origin.x) < 0.1 &&
                kotlin.math.abs(pose[1] - target.origin.y) < 0.1 &&
                kotlin.math.abs(pose[2] - target.origin.z) < 0.1 &&
                kotlin.math.abs(pose[3] - target.position.x) < 0.1 &&
                kotlin.math.abs(pose[4] - target.position.y) < 0.1 &&
                kotlin.math.abs(pose[5] - target.position.z) < 0.1
        }
        SystemClock.sleep(900L)
        val finalPose = readCameraPoseOnGl(glView)
        assertTrue(
            "a queued earlier preset must not replace the latest camera selection",
            kotlin.math.abs(finalPose[3] - target.position.x) < 0.1 &&
                kotlin.math.abs(finalPose[4] - target.position.y) < 0.1 &&
                kotlin.math.abs(finalPose[5] - target.position.z) < 0.1,
        )
    }

    @Test
    fun viewCubeFaceTapSelectsThatViewAndDragRotatesTheRendererCamera() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val modelBed = requireNotNull(glView.renderer.bed)
        val min = requireNotNull(modelBed.getVolumeMin())
        val max = requireNotNull(modelBed.getVolumeMax())
        val target = CameraPresetGeometry.resolve(
            CameraPreset.FRONT,
            CameraVector(min.x, min.y, min.z),
            CameraVector(max.x, max.y, max.z),
        )
        val cube = compose.onNodeWithTag("workspace-view-cube").assertIsDisplayed()
        val size = cube.fetchSemanticsNode().size
        val polygons = WorkspaceViewCubeGeometry.project(
            size.width.toFloat(), size.height.toFloat(),
            Math.toRadians(-35.0).toFloat(), Math.toRadians(-25.0).toFloat(),
        )
        val front = requireNotNull(polygons.firstOrNull { it.face == WorkspaceViewFace.FRONT })
        val localX = front.points.map { it.x }.average().toFloat()
        val localY = front.points.map { it.y }.average().toFloat()
        assertEquals(WorkspaceViewFace.FRONT,
            WorkspaceViewCubeGeometry.hitTest(polygons, localX, localY))

        cube.performTouchInput {
            down(Offset(localX, localY))
            up()
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            val pose = readCameraPoseOnGl(glView)
            kotlin.math.abs(pose[3] - target.position.x) < 0.1 &&
                kotlin.math.abs(pose[4] - target.position.y) < 0.1 &&
                kotlin.math.abs(pose[5] - target.position.z) < 0.1
        }
        SystemClock.sleep(900L)
        val beforeDrag = readCameraPoseOnGl(glView)
        cube.performTouchInput {
            val start = Offset(size.width * 0.5f, size.height * 0.5f)
            down(start)
            moveTo(Offset(size.width * 0.82f, size.height * 0.68f), delayMillis = 250)
            up()
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            val pose = readCameraPoseOnGl(glView)
            (3..5).any { kotlin.math.abs(pose[it] - beforeDrag[it]) > 0.1 }
        }
    }

    private fun performViewCubeFace(faceLabelRes: Int) {
        val activity = compose.activity
        val label = activity.getString(
            R.string.ViewCubeShowFace,
            activity.getString(faceLabelRes),
        )
        val action = requireNotNull(
            compose.onNodeWithTag("workspace-view-cube")
                .fetchSemanticsNode().config[SemanticsActions.CustomActions]
                .firstOrNull { it.label == label },
        ) {
                "View cube should expose accessibility action '$label'"
        }
        compose.runOnIdle { action.action() }
    }

    @Test
    fun modelMenuDuplicateActionAppliesRequestedCopyCountToNativeModel() {
        val activity = compose.activity
        val context = activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val modelFile = File(context.cacheDir, "menu-duplicate-action-test.stl")
        context.assets.open("models/xyz_cube.stl").use { source ->
            modelFile.outputStream().use(source::copyTo)
        }
        val loadDone = CountDownLatch(1)
        var loaded: Model? = null
        var loadError: Exception? = null
        bed.loadModel(modelFile, false, 1, object : BedFragment.ModelLoadCallback {
            override fun onLoaded(loadedModel: Model, firstNewObject: Int, addedObjects: Int) {
                loaded = loadedModel
                loadDone.countDown()
            }

            override fun onFailed(error: Exception) {
                loadError = error
                loadDone.countDown()
            }
        })
        assertTrue("temporary STL should load for the menu action", loadDone.await(60, TimeUnit.SECONDS))
        assertEquals("temporary STL should load without error: $loadError", 1, requireNotNull(loaded).objectsCount)

        val viewport = requireNotNull(bed.glView)
        viewport.queueEvent {
            requireNotNull(viewport.renderer.model).setUniformFilament(0, 2)
            viewport.requestRender()
        }
        instrumentationWaitForGlIdle()
        assertArrayEquals("source filament assignment should apply on the GL thread", intArrayOf(2),
            readUsedFilamentsOnGl(viewport, 0))
        compose.runOnUiThread { bed.updateModel() }
        instrumentationWaitForGlIdle()
        viewport.selectModel(0, Runnable {})
        instrumentationWaitForGlIdle()
        compose.onNodeWithContentDescription(activity.getString(R.string.MenuModel))
            .assertIsDisplayed().performClick()
        revealMenuAction(activity, R.string.ModelMenuDuplicate)
        compose.onNodeWithText(activity.getString(R.string.ModelMenuDuplicateShort))
            .assertIsDisplayed().assertIsEnabled().performClick()
        val duplicateCount = compose.onNodeWithTag("duplicate-model-count").assertIsDisplayed()
        duplicateCount.performTextClearance()
        duplicateCount.performTextInput("2")
        compose.onNodeWithTag("duplicate-model-confirm").performClick()
        compose.waitUntil(timeoutMillis = 15_000) {
            requireNotNull(bed.currentModel).objectsCount == 3
        }
        instrumentationWaitForGlIdle()
        assertEquals("two requested copies should be added to the original", 3,
            requireNotNull(bed.currentModel).objectsCount)
        assertFalse("successful duplication should close its dialog", bed.isWorkspaceMessageDialogAttached())
        selectAllObjectsScope()
        revealMenuAction(activity, R.string.ModelMenuSelectAll)
        compose.onNodeWithContentDescription(activity.getString(R.string.ModelMenuSelectAll))
            .assertIsDisplayed().assertIsEnabled().performClick()
        val selectedCount = CountDownLatch(1)
        val selectedObjects = AtomicReference<Int>()
        viewport.queueEvent {
            selectedObjects.set(viewport.renderer.selectedObjectsCount)
            selectedCount.countDown()
        }
        assertTrue("GL thread should report the select-all result", selectedCount.await(10, TimeUnit.SECONDS))
        assertEquals("Select All should select all three native models", 3, requireNotNull(selectedObjects.get()))
        for (index in 0 until 3) {
            assertArrayEquals("duplicate $index should retain the source material", intArrayOf(2),
                readUsedFilamentsOnGl(viewport, index))
        }
        compose.runOnUiThread { bed.undoModelEdit() }
        compose.waitUntil(timeoutMillis = 10_000) { readObjectCountOnGl(viewport) == 1 }
        assertArrayEquals("Undo duplication should restore the assigned source material", intArrayOf(2),
            readUsedFilamentsOnGl(viewport, 0))
        compose.runOnUiThread { bed.redoModelEdit() }
        compose.waitUntil(timeoutMillis = 10_000) { readObjectCountOnGl(viewport) == 3 }
        for (index in 0 until 3) {
            assertArrayEquals("Redo duplication should restore material on copy $index", intArrayOf(2),
                readUsedFilamentsOnGl(viewport, index))
        }
        assertCurrentWorkspaceModelRoundTripsAndSlices("duplicate")
        modelFile.delete()
    }

    @Test
    fun modelMenuCenterOnBedRunsNativeCenteringForSelectedObject() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val modelFile = File(activity.cacheDir, "menu-center-on-bed-test.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            val model = loadSingleTestModel(bed, modelFile)
            val glView = requireNotNull(bed.glView)
            glView.selectModel(0, Runnable {})

            // Move only this disposable fixture to a point clearly away from common printer bed
            // centers before using the real menu action. The previous +73/-41 offset happened
            // to land exactly at the Kobra 3 bed center, making the test pass/fail depend on
            // fixture placement instead of the Center on Bed action.
            glView.queueEvent {
                model.translate(0, 40.0, 30.0, 0.0)
                model.setUniformFilament(0, 2)
                glView.requestRender()
            }
            instrumentationWaitForGlIdle()
            // Record the controlled starting placement so Undo returns here rather than to the
            // STL fixture's initial position.
            compose.runOnUiThread { bed.updateModel() }
            val translationBefore = readTranslationOnGl(glView, 0)
            val activeBed = glView.renderer.bed
            val bedMin = activeBed?.getVolumeMin()
            val bedMax = activeBed?.getVolumeMax()
            val bedCenter = if (bedMin != null && bedMax != null) bedMin.center(bedMax) else null

            openModelMenu(activity)
            revealMenuAction(activity, R.string.ModelMenuCenterOnBed)
            // Keep the lazy grid's scroll target deterministic at large text scales where one
            // action can occupy nearly the entire visible page.
            scrollMenuToIndex(3)
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodesWithText(activity.getString(R.string.ModelMenuCenterOnBedShort))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            val centerAction = compose.onNodeWithText(activity.getString(R.string.ModelMenuCenterOnBedShort))
                .performScrollTo().assertIsDisplayed().assertIsEnabled()
            centerAction.performClick()

            val centered = runCatching {
                compose.waitUntil(timeoutMillis = 10_000) {
                    val after = readTranslationOnGl(glView, 0)
                    kotlin.math.abs(after.x - translationBefore.x) > 1.0 ||
                        kotlin.math.abs(after.y - translationBefore.y) > 1.0
                }
            }.isSuccess
            val translationAfter = readTranslationOnGl(glView, 0)
            assertTrue(
                "Center on Bed did not update native placement; " +
                    "before=(${translationBefore.x},${translationBefore.y},${translationBefore.z}), " +
                    "after=(${translationAfter.x},${translationAfter.y},${translationAfter.z}), " +
                    "bedValid=${activeBed?.isValid()}, bedCenter=${bedCenter?.let { "(${it.x},${it.y},${it.z})" }}, " +
                    "selected=${readSelectedObjectOnGl(glView)}, " +
                    "selectionCount=${readSelectedObjectsCountOnGl(glView)}, " +
                    "bounds=${readBoundsOnGl(glView, 0)}",
                centered,
            )
            assertTrue(
                "Center on Bed must alter the selected disposable model's native placement",
                kotlin.math.abs(translationAfter.x - translationBefore.x) > 1.0 ||
                    kotlin.math.abs(translationAfter.y - translationBefore.y) > 1.0,
            )
            assertEquals("centering must not add or remove models", 1, readObjectCountOnGl(glView))
            assertArrayEquals("Center on Bed must preserve the assigned filament", intArrayOf(2),
                readUsedFilamentsOnGl(glView, 0))
            compose.runOnUiThread { bed.undoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) {
                val restored = readTranslationOnGl(glView, 0)
                kotlin.math.abs(restored.x - translationBefore.x) < 0.01 &&
                    kotlin.math.abs(restored.y - translationBefore.y) < 0.01
            }
            assertArrayEquals("Undo Center on Bed must preserve the assigned filament", intArrayOf(2),
                readUsedFilamentsOnGl(glView, 0))
            compose.runOnUiThread { bed.redoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) {
                val restored = readTranslationOnGl(glView, 0)
                kotlin.math.abs(restored.x - translationAfter.x) < 0.01 &&
                    kotlin.math.abs(restored.y - translationAfter.y) < 0.01
            }
            assertArrayEquals("Redo Center on Bed must preserve the assigned filament", intArrayOf(2),
                readUsedFilamentsOnGl(glView, 0))
            assertCurrentWorkspaceModelRoundTripsAndSlices("center-on-bed")
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun modelMenuFlattenToggleEntersAndLeavesRendererModeWithoutEditingModel() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val modelFile = File(activity.cacheDir, "menu-flatten-toggle-test.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            loadSingleTestModel(bed, modelFile)
            val glView = requireNotNull(bed.glView)
            glView.selectModel(0, Runnable {})
            instrumentationWaitForGlIdle()
            assertEquals("the disposable model must be selected before centering", 0,
                readSelectedObjectOnGl(glView))
            instrumentationWaitForGlIdle()
            val triangleCount = readTriangleCountOnGl(glView, 0)

            openSelectedObjectMenu(activity)
            scrollMenuToIndex(6)
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodesWithText(activity.getString(R.string.ModelMenuFlattenShort))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            val flatten = compose.onNodeWithText(activity.getString(R.string.ModelMenuFlattenShort))
                .performScrollTo().assertIsDisplayed().assertIsEnabled()
            flatten.performClick()
            compose.waitUntil(timeoutMillis = 5_000) {
                readRendererBooleanOnGl(glView, "isInFlattenMode")
            }
            assertTrue("Flatten action should activate the renderer's flatten interaction mode",
                readRendererBooleanOnGl(glView, "isInFlattenMode"))
            assertEquals("entering Flatten mode must not edit the mesh", triangleCount,
                readTriangleCountOnGl(glView, 0))

            compose.onNodeWithText(activity.getString(R.string.ModelMenuFlattenShort))
                .performScrollTo().performClick()
            compose.waitUntil(timeoutMillis = 5_000) {
                !readRendererBooleanOnGl(glView, "isInFlattenMode")
            }
            assertFalse("second Flatten action should leave the renderer interaction mode",
                readRendererBooleanOnGl(glView, "isInFlattenMode"))
            assertEquals("leaving Flatten mode must preserve the mesh", triangleCount,
                readTriangleCountOnGl(glView, 0))
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun flatteningAnAngledFaceIsUndoableAndRedoable() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val modelFile = File(activity.cacheDir, "menu-flatten-history-test.stl")
        try {
            activity.assets.open("models/pyramid.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            loadSingleTestModel(bed, modelFile)
            val glView = requireNotNull(bed.glView)
            glView.selectModel(0, Runnable {})
            instrumentationWaitForGlIdle()

            openSelectedObjectMenu(activity)
            revealMenuAction(activity, R.string.ModelMenuFlatten)
            compose.onNodeWithContentDescription(activity.getString(R.string.ModelMenuFlatten))
                .assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 5_000) { readRendererBooleanOnGl(glView, "isInFlattenMode") }
            val rotationBefore = readRotationOnGl(glView, 0)

            val applied = AtomicReference(false)
            val completed = CountDownLatch(1)
            val failure = AtomicReference<Throwable?>()
            glView.queueEvent {
                try {
                    val renderer = glView.renderer
                    val flattenModeField = GLRenderer::class.java.getDeclaredField("isInFlattenMode")
                        .apply { isAccessible = true }
                    val planesField = GLRenderer::class.java.getDeclaredField("flattenPlanes")
                        .apply { isAccessible = true }
                    @Suppress("UNCHECKED_CAST")
                    val planes = planesField.get(renderer) as List<GLModel>
                    check(planes.isNotEmpty()) { "Flatten mode did not produce any face planes" }
                    val hits = ArrayList<GLModel.HitResult>()
                    planes.forEach { plane ->
                        plane.raycaster.raycast(
                            renderer, hits,
                            renderer.viewportWidth / 2f,
                            renderer.viewportHeight / 2f,
                        )
                    }
                    check(flattenModeField.getBoolean(renderer)) {
                        "raycast smoke check unexpectedly left Flatten mode"
                    }
                    // Plane 1 is a sloped face on the asymmetric pyramid fixture. The
                    // renderer's production tap path calls this same commit after picking.
                    applied.set(renderer.applyFlattenPlane(1))
                } catch (error: Throwable) {
                    failure.set(error)
                } finally {
                    completed.countDown()
                }
            }
            assertTrue("GL thread did not finish face-plane hit search", completed.await(30, TimeUnit.SECONDS))
            failure.get()?.let { throw AssertionError("Could not apply flatten rotation", it) }
            assertTrue("applying the angled plane should commit flatten rotation", applied.get())
            compose.waitUntil(timeoutMillis = 10_000) { readModelHistorySize(bed, "undoHistory") > 0 }
            val rotationAfter = readRotationOnGl(glView, 0)
            assertTrue("flattening should change the native object rotation",
                kotlin.math.abs(rotationAfter.x - rotationBefore.x) > 1e-4 ||
                    kotlin.math.abs(rotationAfter.y - rotationBefore.y) > 1e-4 ||
                    kotlin.math.abs(rotationAfter.z - rotationBefore.z) > 1e-4)

            compose.runOnUiThread { bed.undoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) {
                val rotation = readRotationOnGl(glView, 0)
                kotlin.math.abs(rotation.x - rotationBefore.x) < 1e-4 &&
                    kotlin.math.abs(rotation.y - rotationBefore.y) < 1e-4 &&
                    kotlin.math.abs(rotation.z - rotationBefore.z) < 1e-4
            }
            compose.runOnUiThread { bed.redoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) {
                val rotation = readRotationOnGl(glView, 0)
                kotlin.math.abs(rotation.x - rotationAfter.x) < 1e-4 &&
                    kotlin.math.abs(rotation.y - rotationAfter.y) < 1e-4 &&
                    kotlin.math.abs(rotation.z - rotationAfter.z) < 1e-4
            }
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun modelMenuAutoOrientActionsRunForAllObjectsAndSelectedObject() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val modelFile = File(activity.cacheDir, "menu-auto-orient-test.stl")
        try {
            activity.assets.open("models/pyramid.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            var model = loadSingleTestModel(bed, modelFile)
            val glView = requireNotNull(bed.glView)
            val rotateDone = CountDownLatch(1)
            glView.queueEvent {
                model.rotate(0, Math.toRadians(23.0), Math.toRadians(31.0), Math.toRadians(17.0))
                rotateDone.countDown()
            }
            assertTrue("temporary object should finish rotating", rotateDone.await(15, TimeUnit.SECONDS))
            glView.selectModel(0, Runnable {})
            val duplicateDone = CountDownLatch(1)
            glView.duplicateSelectedModel(1, Runnable { duplicateDone.countDown() })
            assertTrue("second temporary object should finish duplicating", duplicateDone.await(15, TimeUnit.SECONDS))
            instrumentationWaitForGlIdle()
            assertEquals(2, readObjectCountOnGl(glView))
            compose.runOnUiThread { bed.updateModel() }

            openModelMenu(activity)
            selectAllObjectsScope()
            val beforeOrientAll = model.geometryRevision
            val historyBeforeOrientAll = readModelHistorySize(bed, "undoHistory")
            val beforeOrientAllBounds = readExactBoundsOnGl(glView, 0, 1)
            fun boundsMatch(expected: DoubleArray, actual: DoubleArray) =
                expected.indices.all { kotlin.math.abs(expected[it] - actual[it]) <= 0.02 }
            revealMenuAction(activity, R.string.ModelMenuOrientAll)
            compose.onNodeWithText(activity.getString(R.string.ModelMenuOrientAllShort))
                .performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
            compose.waitUntil(timeoutMillis = 10_000) { model.geometryRevision > beforeOrientAll }
            compose.waitUntil(timeoutMillis = 10_000) {
                readModelHistorySize(bed, "undoHistory") > historyBeforeOrientAll
            }
            val afterOrientAllBounds = readExactBoundsOnGl(glView, 0, 1)
            beforeOrientAllBounds.zip(afterOrientAllBounds).forEachIndexed { index, (before, after) ->
                assertFalse("Orient All should change object $index geometry", before.contentEquals(after))
                for (axis in 0..2) {
                    assertEquals("Orient All should preserve object $index center on axis $axis",
                        (before[axis] + before[axis + 3]) / 2.0,
                        (after[axis] + after[axis + 3]) / 2.0, 0.02)
                }
            }
            compose.runOnUiThread { bed.undoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) {
                beforeOrientAllBounds.zip(readExactBoundsOnGl(glView, 0, 1))
                    .all { (before, current) -> boundsMatch(before, current) }
            }
            compose.runOnUiThread { bed.redoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) {
                afterOrientAllBounds.zip(readExactBoundsOnGl(glView, 0, 1))
                    .all { (after, current) -> boundsMatch(after, current) }
            }
            model = requireNotNull(bed.currentModel)

            selectSingleObjectScope()
            glView.selectModels(intArrayOf(0, 1)) { bed.updateModel() }
            instrumentationWaitForGlIdle()
            scrollMenuToIndex(5)
            revealMenuAction(activity, R.string.ModelMenuAutoOrientSelected)
            val beforeOrientSelected = model.geometryRevision
            val beforeSelectedOrientBounds = readExactBoundsOnGl(glView, 0, 1)
            compose.onNodeWithContentDescription(activity.getString(R.string.ModelMenuAutoOrientSelected))
                .performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
            compose.waitUntil(timeoutMillis = 10_000) { model.geometryRevision >= beforeOrientSelected + 2 }
            assertTrue("Auto Orient Selected should update the selected group",
                model.geometryRevision >= beforeOrientSelected + 2)
            assertEquals("orientation actions must preserve the object count", 2, readObjectCountOnGl(glView))
            val afterSelectedOrientBounds = readExactBoundsOnGl(glView, 0, 1)
            beforeSelectedOrientBounds.zip(afterSelectedOrientBounds).forEachIndexed { index, (before, after) ->
                for (axis in 0..2) {
                    assertEquals("auto-orient should preserve object $index center on axis $axis",
                        (before[axis] + before[axis + 3]) / 2.0,
                        (after[axis] + after[axis + 3]) / 2.0, 0.02)
                }
            }
            compose.runOnUiThread { bed.undoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) {
                beforeSelectedOrientBounds.zip(readExactBoundsOnGl(glView, 0, 1))
                    .all { (before, current) -> boundsMatch(before, current) }
            }
            compose.runOnUiThread { bed.redoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) {
                afterSelectedOrientBounds.zip(readExactBoundsOnGl(glView, 0, 1))
                    .all { (after, current) -> boundsMatch(after, current) }
            }
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun modelMenuArrangeBringsOffBedObjectBackIntoWorkspace() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val modelFile = File(activity.cacheDir, "menu-arrange-test.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            val model = loadSingleTestModel(bed, modelFile)
            val glView = requireNotNull(bed.glView)
            glView.selectModel(0, Runnable {})
            glView.queueEvent {
                model.setUniformFilament(0, 2)
                model.translate(0, 2_000.0, 2_000.0, 0.0)
                glView.requestRender()
            }
            instrumentationWaitForGlIdle()
            compose.runOnUiThread { bed.updateModel() }
            val before = readTranslationOnGl(glView, 0)
            assertTrue("test object should begin clearly outside a typical build volume",
                before.x > 1_000.0 && before.y > 1_000.0)
            compose.runOnUiThread { bed.updateModel() }

            openModelMenu(activity)
            selectAllObjectsScope()
            compose.onNodeWithText(activity.getString(R.string.ModelMenuArrangeShort))
                .performScrollTo().assertIsDisplayed().assertIsEnabled()
            val beforeExplicitArrange = readTranslationOnGl(glView, 0)
            assertEquals("opening the model menu must not auto-arrange a manually moved object",
                before.x, beforeExplicitArrange.x, 0.01)
            assertEquals("only the explicit Arrange action may replace the user's placement",
                before.y, beforeExplicitArrange.y, 0.01)
            compose.onNodeWithText(activity.getString(R.string.ModelMenuArrangeShort)).performClick()
            compose.waitUntil(timeoutMillis = 15_000) {
                val after = readTranslationOnGl(glView, 0)
                kotlin.math.abs(after.x - before.x) > 10.0 || kotlin.math.abs(after.y - before.y) > 10.0
            }
            val after = readTranslationOnGl(glView, 0)
            assertTrue("Arrange should change the off-bed XY placement",
                kotlin.math.abs(after.x - before.x) > 10.0 || kotlin.math.abs(after.y - before.y) > 10.0)
            assertEquals("Arrange must preserve the single temporary object", 1, readObjectCountOnGl(glView))
            assertArrayEquals("Arrange must preserve the object's assigned filament", intArrayOf(2),
                readUsedFilamentsOnGl(glView, 0))
            compose.runOnUiThread { bed.undoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) {
                val restored = readTranslationOnGl(glView, 0)
                kotlin.math.abs(restored.x - before.x) < 0.01 && kotlin.math.abs(restored.y - before.y) < 0.01
            }
            val afterUndo = readTranslationOnGl(glView, 0)
            assertEquals("Undo after Arrange must restore the exact pre-arrange X", before.x, afterUndo.x, 0.01)
            assertEquals("Undo after Arrange must restore the exact pre-arrange Y", before.y, afterUndo.y, 0.01)
            assertArrayEquals("Undo Arrange must preserve the object's assigned filament", intArrayOf(2),
                readUsedFilamentsOnGl(glView, 0))
            compose.runOnUiThread { bed.redoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) {
                val restored = readTranslationOnGl(glView, 0)
                kotlin.math.abs(restored.x - after.x) < 0.01 && kotlin.math.abs(restored.y - after.y) < 0.01
            }
            val afterRedo = readTranslationOnGl(glView, 0)
            assertEquals("Redo after Arrange must restore the arranged X", after.x, afterRedo.x, 0.01)
            assertEquals("Redo after Arrange must restore the arranged Y", after.y, afterRedo.y, 0.01)
            assertArrayEquals("Redo Arrange must preserve the object's assigned filament", intArrayOf(2),
                readUsedFilamentsOnGl(glView, 0))
            assertCurrentWorkspaceModelRoundTripsAndSlices("arrange")
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun modelMenuArrangeRepacksSeveralMovedObjectsInsteadOfFillingWithCopies() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val modelFile = File(activity.cacheDir, "menu-arrange-several-off-bed-test.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            val model = loadSingleTestModel(bed, modelFile)
            val prepared = CountDownLatch(1)
            glView.queueEvent {
                repeat(3) {
                    val part = Model()
                    try {
                        part.addObject(model, 0)
                        model.addObject(part, 0)
                    } finally {
                        part.release()
                    }
                }
                for (index in 0 until model.objectsCount) {
                    model.translate(index, 2_000.0 + index * 30.0, 2_000.0 + index * 25.0, 0.0)
                    glView.renderer.invalidateGlModel(index)
                }
                glView.renderer.setSelectedObject(0)
                glView.requestRender()
                prepared.countDown()
            }
            assertTrue("four off-bed objects should be prepared on the GL thread", prepared.await(10, TimeUnit.SECONDS))
            compose.runOnUiThread { bed.updateModel() }
            instrumentationWaitForGlIdle()
            assertEquals("fixture must contain four separate objects", 4, readObjectCountOnGl(glView))
            assertTrue("all objects should begin outside the bed", (0..3).all { index ->
                val position = readTranslationOnGl(glView, index)
                position.x > 1_000.0 && position.y > 1_000.0
            })

            openModelMenu(activity)
            selectAllObjectsScope()
            val arrangeLabel = activity.getString(R.string.ModelMenuArrangeShort)
            compose.onNodeWithText(arrangeLabel).performScrollTo().assertIsDisplayed().assertIsEnabled()
                .performClick()
            compose.waitUntil(timeoutMillis = 20_000) {
                (0..3).all { index ->
                    val position = readTranslationOnGl(glView, index)
                    position.x < 1_000.0 && position.y < 1_000.0
                }
            }
            assertEquals("Arrange must reposition the four existing objects, not duplicate any", 4,
                readObjectCountOnGl(glView))
            assertTrue("every object must be moved back into the bed area", (0..3).all { index ->
                val position = readTranslationOnGl(glView, index)
                position.x < 1_000.0 && position.y < 1_000.0
            })
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun byObjectArrangeUsesActiveClearanceAndKeepsObjectsApart() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val modelFile = File(activity.cacheDir, "by-object-arrange-test.stl")
        val printProfile = ConfigObject.PROFILE_LIST_PRINT
        val previousDraft = ProfileSettingsRepository.pendingEntries(printProfile)
        try {
            assertFalse("test fixture must not inherit a temporary 3MF project", TemporaryProjectSettings.active)
            ProfileSettingsRepository.clearDraft(printProfile)
            // Deliberately make this 20 mm fixture taller than the synthetic nozzle height so
            // the By-Object branch must honor the active 30 mm gantry clearance. By-Layer's
            // existing 6 mm arrangement gap would not satisfy this assertion.
            ProfileSettingsRepository.setPendingValues(printProfile, mapOf(
                "print_sequence" to "by object",
                "nozzle_height" to "0.1",
                "extruder_clearance_radius" to "30",
            ))
            val effectiveArrangeConfig = SliceBeam.buildCurrentConfigObject()
            assertEquals("by object", effectiveArrangeConfig["print_sequence"])
            assertEquals("0.1", effectiveArrangeConfig["nozzle_height"])
            assertEquals("30", effectiveArrangeConfig["extruder_clearance_radius"])
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            val model = loadSingleTestModel(bed, modelFile)
            val prepared = CountDownLatch(1)
            glView.queueEvent {
                model.addObject(model, 0)
                glView.renderer.setSelectedObject(0)
                glView.renderer.setSelectedObjects(intArrayOf(0, 1))
                glView.requestRender()
                prepared.countDown()
            }
            assertTrue("two overlapping test objects should be prepared on GL", prepared.await(10, TimeUnit.SECONDS))
            instrumentationWaitForGlIdle()
            compose.runOnUiThread { bed.updateModel() }
            val before = readExactBoundsOnGl(glView, 0, 1)
            assertTrue("fixture objects should overlap before arranging",
                maxOf(before[0][0], before[1][0]) < minOf(before[0][3], before[1][3]) &&
                    maxOf(before[0][1], before[1][1]) < minOf(before[0][4], before[1][4]))

            val arrangeFinished = CountDownLatch(1)
            val arrangeResult = AtomicReference<Boolean?>()
            glView.queueEvent {
                arrangeResult.set(requireNotNull(glView.renderer.bed).arrange(model))
                glView.requestRender()
                arrangeFinished.countDown()
            }
            assertTrue("native By-Object arrangement should return", arrangeFinished.await(15, TimeUnit.SECONDS))
            assertEquals("both models should fit with the active clearance", true, arrangeResult.get())
            val after = readExactBoundsOnGl(glView, 0, 1)
            val xGap = maxOf(after[0][0], after[1][0]) - minOf(after[0][3], after[1][3])
            val yGap = maxOf(after[0][1], after[1][1]) - minOf(after[0][4], after[1][4])
            assertTrue("By-Object arrangement must apply the active 30 mm gantry clearance (x=$xGap, y=$yGap)",
                maxOf(xGap, yGap) >= 29.5)
            assertEquals("arranging must preserve both models", 2, readObjectCountOnGl(glView))
        } finally {
            ProfileSettingsRepository.clearDraft(printProfile)
            if (previousDraft.isNotEmpty()) ProfileSettingsRepository.setPendingValues(printProfile, previousDraft)
            modelFile.delete()
        }
    }

    @Test
    fun modelMenuFillBedCreatesCopiesWithinRendererObjectLimit() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val modelFile = File(activity.cacheDir, "menu-fill-bed-test.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            val model = loadSingleTestModel(bed, modelFile)
            val glView = requireNotNull(bed.glView)
            glView.selectModel(0, Runnable {})
            glView.queueEvent {
                model.setUniformFilament(0, 2)
                glView.requestRender()
            }
            instrumentationWaitForGlIdle()
            compose.runOnUiThread { bed.updateModel() }

            openModelMenu(activity)
            revealMenuAction(activity, R.string.ModelMenuFillBed)
            compose.onNodeWithText(activity.getString(R.string.ModelMenuFillBedShort))
                .performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()

            // Packing performs native bed arrangement on the renderer GL thread. Wait on the
            // volatile operation flag rather than queueing object-count reads behind that work.
            compose.waitUntil(timeoutMillis = 120_000) { !glView.isFillBedInProgress }
            val resultingCount = readObjectCountOnGl(glView, timeoutMillis = 120_000)
            assertTrue("Fill Bed should add at least one copy of the disposable test model", resultingCount > 1)
            assertTrue("Fill Bed must respect the renderer's hard object cap", resultingCount <= 256)
            for (index in 0 until resultingCount) {
                assertArrayEquals("Fill Bed copy $index must inherit the source filament", intArrayOf(2),
                    readUsedFilamentsOnGl(glView, index))
            }
            compose.runOnUiThread { bed.undoModelEdit() }
            compose.waitUntil(timeoutMillis = 120_000) { readObjectCountOnGl(glView, timeoutMillis = 120_000) == 1 }
            assertArrayEquals("Undo Fill Bed must restore the source filament", intArrayOf(2),
                readUsedFilamentsOnGl(glView, 0))
            compose.runOnUiThread { bed.redoModelEdit() }
            compose.waitUntil(timeoutMillis = 120_000) {
                readObjectCountOnGl(glView, timeoutMillis = 120_000) == resultingCount
            }
            for (index in 0 until resultingCount) {
                assertArrayEquals("Redo Fill Bed copy $index must retain its filament", intArrayOf(2),
                    readUsedFilamentsOnGl(glView, index))
            }
            // Filling a small cube plate creates dozens of printable instances. Round-trip the
            // complete generated project here; slice validation is covered by the focused
            // single-object and multi-object editing tests, avoiding a minutes-long stress slice.
            assertCurrentWorkspaceModelRoundTripsAndSlices("fill-bed", sliceModel = false)
        } finally {
            modelFile.delete()
        }
    }

    /** Export the actual post-action renderer model, reload the 3MF, and slice that reloaded model. */
    private fun assertCurrentWorkspaceModelRoundTripsAndSlices(
        name: String,
        sliceModel: Boolean = true,
        expectedObjectFilaments: List<IntArray>? = null,
        allowedGcodeTools: Set<Int>? = null,
        prepareForSlice: (Model) -> Unit = {},
    ) {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val projectFile = File(activity.cacheDir, "placement-$name-${System.nanoTime()}.3mf")
        val gcodeFile = File(activity.cacheDir, "placement-$name-${System.nanoTime()}.gcode")
        val previousSlots = Prefs.getStoredFilamentSlots().map { FilamentSlot(it.color, it.type) }
        val previousConfig = SliceBeam.CONFIG
        val previousInstance = SliceBeam.INSTANCE
        val configFile = SliceBeam.getCurrentConfigFile()
        val previousConfigFile = configFile.takeIf { it.isFile }?.readBytes()
        val prefs = requireNotNull(Prefs.getPrefs())
        val snapshotKeys = listOf("mmu_snapshot_printer", "mmu_snapshot")
        val previousSnapshots = snapshotKeys.associateWith { prefs.getString(it, null) }
        val objectCount = readObjectCountOnGl(glView)
        val expectedPositions = (0 until objectCount).map { readTranslationOnGl(glView, it) }
        val expectedFilaments = (0 until objectCount).map { readUsedFilamentsOnGl(glView, it) }
        var restoredModel: Model? = null
        var sliceResult: GCodeProcessorResult? = null
        try {
            assertTrue("$name should leave at least one printable object", objectCount > 0)
            if (expectedObjectFilaments == null) {
                assertTrue("$name should retain at least one assigned material",
                    expectedFilaments.all { it.contentEquals(intArrayOf(2)) })
            } else {
                assertEquals("$name expected filament fixtures should cover every object",
                    objectCount, expectedObjectFilaments.size)
                expectedObjectFilaments.forEachIndexed { index, expected ->
                    assertArrayEquals("$name source object $index filament", expected, expectedFilaments[index])
                }
            }
            SliceBeam.INSTANCE = activity.applicationContext as SliceBeam
            Prefs.init(activity.applicationContext as SliceBeam)
            Prefs.setStoredFilamentSlots(listOf(
                FilamentSlot(0xFF303030.toInt(), "Placement T0"),
                FilamentSlot(0xFF3366CC.toInt(), "Placement T1"),
                FilamentSlot(0xFFF2F2F2.toInt(), "Placement T2"),
            ))
            TemporaryProjectSettings.clear()
            activity.assets.open("orca_profiles/Anycubic.ini").use {
                SliceBeam.CONFIG = Slic3rConfigWrapper(it)
            }
            SliceBeam.requireConfig().presets = ConfigObject("presets").apply {
                put("printer", "Anycubic Kobra 3 0.4 nozzle")
                put("print", "0.16mm Standard")
                put("filament", "Anycubic Generic PLA")
            }
            val clearSnapshots = prefs.edit()
            snapshotKeys.forEach(clearSnapshots::remove)
            check(clearSnapshots.commit())

            val exported = CountDownLatch(1)
            val exportFailure = AtomicReference<Throwable?>()
            glView.queueEvent {
                try {
                    val model = requireNotNull(glView.renderer.model)
                    assertEquals("$name renderer object count before export", objectCount, model.objectsCount)
                    SliceBeam.genCurrentConfig(model)
                    model.export3mf(configFile.absolutePath, projectFile.absolutePath)
                } catch (error: Throwable) {
                    exportFailure.set(error)
                } finally {
                    exported.countDown()
                }
            }
            assertTrue("$name renderer export should finish", exported.await(60, TimeUnit.SECONDS))
            exportFailure.get()?.let { throw AssertionError("$name 3MF export failed", it) }
            assertTrue("$name should create a non-empty 3MF", projectFile.isFile && projectFile.length() > 0L)

            restoredModel = Model(projectFile, 1)
            val restored = requireNotNull(restoredModel)
            assertEquals("$name 3MF should retain every object", objectCount, restored.objectsCount)
            for (index in 0 until objectCount) {
                val position = Vec3d()
                restored.getTranslation(index, position)
                assertEquals("$name reloaded object $index X placement", expectedPositions[index].x, position.x, 0.02)
                assertEquals("$name reloaded object $index Y placement", expectedPositions[index].y, position.y, 0.02)
                assertEquals("$name reloaded object $index Z placement", expectedPositions[index].z, position.z, 0.02)
                assertArrayEquals("$name reloaded object $index filament", expectedFilaments[index],
                    restored.getUsedFilaments(index))
            }
            if (sliceModel) {
                prepareForSlice(restored)
                assertTrue("$name should leave a printable sample after preparation", restored.objectsCount > 0)
                SliceBeam.genCurrentConfig(restored)
                sliceResult = restored.slice(configFile.absolutePath, gcodeFile.absolutePath, null)
                assertTrue("$name reloaded model should produce non-empty G-code",
                    gcodeFile.isFile && gcodeFile.length() > 0L)
                val gcode = gcodeFile.readText()
                assertTrue("$name G-code should contain deposited extrusion",
                    Regex("(?m)^G1 .*\\bE[0-9.-]+").containsMatchIn(gcode))
                val emittedTools = Regex("(?m)^T(\\d+)(?:\\s|$)").findAll(gcode)
                    .map { it.groupValues[1].toInt() }.toSet()
                if (allowedGcodeTools == null) {
                    assertTrue("$name G-code should use assigned tool T1; found $emittedTools", 1 in emittedTools)
                    assertFalse("$name G-code must not use the unassigned T2", 2 in emittedTools)
                } else {
                    assertTrue("$name G-code must not use unassigned tools; found $emittedTools",
                        emittedTools.all { it in allowedGcodeTools })
                }
            }
        } finally {
            sliceResult?.release()
            restoredModel?.release()
            gcodeFile.delete()
            projectFile.delete()
            val restoreSnapshots = prefs.edit()
            previousSnapshots.forEach { (key, value) ->
                if (value == null) restoreSnapshots.remove(key) else restoreSnapshots.putString(key, value)
            }
            restoreSnapshots.commit()
            SliceBeam.CONFIG = previousConfig
            SliceBeam.INSTANCE = previousInstance
            if (previousConfigFile == null) configFile.delete() else configFile.writeBytes(previousConfigFile)
            TemporaryProjectSettings.clear()
            Prefs.setStoredFilamentSlots(previousSlots)
        }
    }

    @Test
    fun modelMenuMoveToPlateTransfersSelectedObjectBetweenWorkspacePlates() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val modelFile = File(activity.cacheDir, "menu-move-to-plate-test.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            loadSingleTestModel(bed, modelFile)
            val glView = requireNotNull(bed.glView)
            glView.selectModel(0, Runnable {})
            assertEquals(1, bed.plateCount)

            compose.onNodeWithContentDescription(activity.getString(R.string.PlateNumber, "1"))
                .assertIsDisplayed().performClick()
            compose.onNodeWithContentDescription(activity.getString(R.string.MenuAddPlate))
                .assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                bed.plateCount == 2 && bed.currentPlateIndex == 1
            }
            compose.onNodeWithContentDescription(activity.getString(R.string.PlateNumber, "2"))
                .assertIsDisplayed().performClick()
            compose.onNodeWithContentDescription(
                activity.getString(R.string.PlatePickerSelectDescription, "1"),
            ).assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                bed.currentPlateIndex == 0 && readObjectCountOnGl(glView) == 1
            }
            glView.selectModel(0, Runnable {})
            instrumentationWaitForGlIdle()

            openModelMenu(activity)
            revealMenuAction(activity, R.string.ModelMenuMoveToPlate)
            compose.onNodeWithText(activity.getString(R.string.ModelMenuMoveToPlateShort))
                .assertIsDisplayed().assertIsEnabled().performClick()
            compose.onNodeWithText(activity.getString(R.string.PlateNumber, "2"))
                .assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 15_000) {
                bed.currentPlateIndex == 1 && readObjectCountOnGl(glView) == 1
            }
            assertEquals("moving the selected object should keep the workspace's two plates", 2, bed.plateCount)

            compose.onNodeWithContentDescription(activity.getString(R.string.PlateNumber, "2"))
                .assertIsDisplayed().performClick()
            compose.onNodeWithContentDescription(
                activity.getString(R.string.PlatePickerSelectDescription, "1"),
            ).assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                bed.currentPlateIndex == 0 && readObjectCountOnGl(glView) == 0
            }
            assertEquals("the moved object must no longer exist on the source plate", 0,
                readObjectCountOnGl(glView))
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun modelMenuMoveSelectedGroupToAnotherPlatePreservesObjectsAndFilaments() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val modelFile = File(activity.cacheDir, "menu-move-selected-group-to-plate-test.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            val model = loadSingleTestModel(bed, modelFile)
            val glView = requireNotNull(bed.glView)
            val prepared = CountDownLatch(1)
            glView.queueEvent {
                model.addObject(model, 0)
                model.addObject(model, 0)
                model.setUniformFilament(0, 2)
                model.setUniformFilament(1, 3)
                model.setUniformFilament(2, 4)
                model.translate(1, 42.0, 0.0, 0.0)
                glView.renderer.setSelectedObjects(intArrayOf(0, 1))
                glView.requestRender()
                prepared.countDown()
            }
            assertTrue("both objects should be prepared on the GL thread", prepared.await(10, TimeUnit.SECONDS))
            compose.runOnUiThread { bed.updateModel() }
            instrumentationWaitForGlIdle()
            assertEquals(3, readObjectCountOnGl(glView))
            val sourceBounds = readExactBoundsOnGl(glView, 0, 1)
            val unselectedBounds = readExactBoundsOnGl(glView, 2).single()
            assertArrayEquals(intArrayOf(2), readUsedFilamentsOnGl(glView, 0))
            assertArrayEquals(intArrayOf(3), readUsedFilamentsOnGl(glView, 1))
            assertArrayEquals(intArrayOf(4), readUsedFilamentsOnGl(glView, 2))

            compose.onNodeWithContentDescription(activity.getString(R.string.PlateNumber, "1"))
                .assertIsDisplayed().performClick()
            compose.onNodeWithContentDescription(activity.getString(R.string.MenuAddPlate))
                .assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                bed.plateCount == 2 && bed.currentPlateIndex == 1
            }
            compose.onNodeWithContentDescription(activity.getString(R.string.PlateNumber, "2"))
                .assertIsDisplayed().performClick()
            compose.onNodeWithContentDescription(
                activity.getString(R.string.PlatePickerSelectDescription, "1"),
            ).assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                bed.currentPlateIndex == 0 && readObjectCountOnGl(glView) == 3
            }
            glView.selectModels(intArrayOf(0, 1))
            compose.waitUntil(timeoutMillis = 5_000) {
                readSelectedIndicesOnGl(glView).contentEquals(intArrayOf(0, 1))
            }

            openModelMenu(activity)
            revealMenuAction(activity, R.string.ModelMenuMoveToPlate)
            compose.onNodeWithText(activity.getString(R.string.ModelMenuMoveToPlateShort))
                .assertIsDisplayed().assertIsEnabled().performClick()
            compose.onNodeWithText(activity.getString(R.string.PlateNumber, "2"))
                .assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 15_000) {
                bed.currentPlateIndex == 1 && readObjectCountOnGl(glView) == 2 &&
                    readSelectedIndicesOnGl(glView).contentEquals(intArrayOf(0, 1))
            }

            assertEquals("moving a group must keep the workspace plate count", 2, bed.plateCount)
            assertArrayEquals("the first moved model keeps its filament", intArrayOf(2),
                readUsedFilamentsOnGl(glView, 0))
            assertArrayEquals("the second moved model keeps its own filament", intArrayOf(3),
                readUsedFilamentsOnGl(glView, 1))
            val targetBounds = readExactBoundsOnGl(glView, 0, 1)
            assertEquals("the transfer must preserve the objects' relative X placement",
                sourceBounds[1][0] - sourceBounds[0][0], targetBounds[1][0] - targetBounds[0][0], 0.03)

            compose.onNodeWithContentDescription(activity.getString(R.string.PlateNumber, "2"))
                .assertIsDisplayed().performClick()
            compose.onNodeWithContentDescription(
                activity.getString(R.string.PlatePickerSelectDescription, "1"),
            ).assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                bed.currentPlateIndex == 0 && readObjectCountOnGl(glView) == 1
            }
            assertArrayEquals("the unselected source-plate object keeps its filament", intArrayOf(4),
                readUsedFilamentsOnGl(glView, 0))
            assertArrayEquals("the unselected source-plate object keeps its position", unselectedBounds,
                readExactBoundsOnGl(glView, 0).single(), 0.03)
            glView.selectModels(intArrayOf(0))
            compose.waitUntil(timeoutMillis = 5_000) {
                readSelectedIndicesOnGl(glView).contentEquals(intArrayOf(0))
            }
            openModelMenu(activity)
            revealMenuAction(activity, R.string.ModelMenuMoveToPlate)
            compose.onNodeWithText(activity.getString(R.string.ModelMenuMoveToPlateShort))
                .assertIsDisplayed().assertIsEnabled().performClick()
            compose.onNodeWithText(activity.getString(R.string.PlateNumber, "2"))
                .assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 15_000) {
                bed.currentPlateIndex == 1 && readObjectCountOnGl(glView) == 3 &&
                    readSelectedIndicesOnGl(glView).contentEquals(intArrayOf(2))
            }
            assertArrayEquals("moving one selected object preserves its filament", intArrayOf(4),
                readUsedFilamentsOnGl(glView, 2))
            assertArrayEquals("moving one selected object preserves its position", unselectedBounds,
                readExactBoundsOnGl(glView, 2).single(), 0.03)
            compose.onNodeWithContentDescription(activity.getString(R.string.PlateNumber, "2"))
                .assertIsDisplayed().performClick()
            compose.onNodeWithContentDescription(
                activity.getString(R.string.PlatePickerSelectDescription, "1"),
            ).assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                bed.currentPlateIndex == 0 && readObjectCountOnGl(glView) == 0
            }
            compose.onNodeWithContentDescription(activity.getString(R.string.PlateNumber, "1"))
                .assertIsDisplayed().performClick()
            compose.onNodeWithContentDescription(
                activity.getString(R.string.PlatePickerSelectDescription, "2"),
            ).assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                bed.currentPlateIndex == 1 && readObjectCountOnGl(glView) == 3
            }
            assertArrayEquals("both per-object material assignments survive a plate reload", intArrayOf(2),
                readUsedFilamentsOnGl(glView, 0))
            assertArrayEquals("the second material assignment survives a plate reload", intArrayOf(3),
                readUsedFilamentsOnGl(glView, 1))
            assertArrayEquals("the individually moved object survives a plate reload", intArrayOf(4),
                readUsedFilamentsOnGl(glView, 2))
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun combiningObjectsOnSecondaryPlateKeepsHistoryAndOtherPlateUnchanged() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val modelFile = File(activity.cacheDir, "menu-combine-secondary-plate-test.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            loadSingleTestModel(bed, modelFile)
            val glView = requireNotNull(bed.glView)
            val sourceBounds = readExactBoundsOnGl(glView, 0).single()
            val sourceFilaments = readUsedFilamentsOnGl(glView, 0)

            compose.onNodeWithContentDescription(activity.getString(R.string.PlateNumber, "1"))
                .assertIsDisplayed().performClick()
            compose.onNodeWithContentDescription(activity.getString(R.string.MenuAddPlate))
                .assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                bed.plateCount == 2 && bed.currentPlateIndex == 1
            }

            // Load a controlled model on the secondary plate. A second import creates a separate
            // object while avoiding self-copying a native model after a plate switch.
            loadSingleTestModel(bed, modelFile)
            loadSingleTestModel(bed, modelFile)
            compose.waitUntil(timeoutMillis = 10_000) { readObjectCountOnGl(glView) == 2 }

            val prepared = CountDownLatch(1)
            glView.queueEvent {
                val model = requireNotNull(glView.renderer.model)
                model.setUniformFilament(0, 1)
                model.translate(1, 38.0, 0.0, 0.0)
                model.setUniformFilament(1, 2)
                glView.renderer.setSelectedObjects(intArrayOf(0, 1))
                glView.requestRender()
                prepared.countDown()
            }
            assertTrue("secondary plate objects should be prepared on the GL thread",
                prepared.await(10, TimeUnit.SECONDS))
            compose.runOnUiThread { bed.updateModel() }
            instrumentationWaitForGlIdle()
            assertEquals("both objects should remain on the secondary plate before combine", 2,
                readObjectCountOnGl(glView))

            openModelMenu(activity)
            selectSingleObjectScope()
            revealMenuAction(activity, R.string.ModelMenuCombineSelected)
            compose.onNodeWithContentDescription(activity.getString(R.string.ModelMenuCombineSelected))
                .assertIsDisplayed().assertIsEnabled().performClick()
            compose.onNodeWithTag("workspace-message-confirm").assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 15_000) { readObjectCountOnGl(glView) == 1 }
            assertArrayEquals("secondary plate combine retains both source materials", intArrayOf(1, 2),
                readUsedFilamentsOnGl(glView, 0))
            val joinedBounds = readExactBoundsOnGl(glView, 0).single()
            assertTrue("joined parts should preserve the moved component's world extent",
                joinedBounds[3] - joinedBounds[0] > sourceBounds[3] - sourceBounds[0] + 30.0)

            compose.runOnUiThread { bed.undoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) { readObjectCountOnGl(glView) == 2 }
            assertArrayEquals("Undo restores the first secondary plate material", intArrayOf(1),
                readUsedFilamentsOnGl(glView, 0))
            assertArrayEquals("Undo restores the second secondary plate material", intArrayOf(2),
                readUsedFilamentsOnGl(glView, 1))
            compose.runOnUiThread { bed.redoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) { readObjectCountOnGl(glView) == 1 }

            compose.onNodeWithContentDescription(activity.getString(R.string.PlateNumber, "2"))
                .assertIsDisplayed().performClick()
            compose.onNodeWithContentDescription(
                activity.getString(R.string.PlatePickerSelectDescription, "1"),
            ).assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                bed.currentPlateIndex == 0 && readObjectCountOnGl(glView) == 1
            }
            assertArrayEquals("the primary plate's original filament assignment remains unchanged",
                sourceFilaments, readUsedFilamentsOnGl(glView, 0))
            assertArrayEquals("the primary plate's original object bounds remain unchanged",
                sourceBounds, readExactBoundsOnGl(glView, 0).single(), 0.03)

            compose.onNodeWithContentDescription(activity.getString(R.string.PlateNumber, "1"))
                .assertIsDisplayed().performClick()
            compose.onNodeWithContentDescription(
                activity.getString(R.string.PlatePickerSelectDescription, "2"),
            ).assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                bed.currentPlateIndex == 1 && readObjectCountOnGl(glView) == 1
            }
            assertArrayEquals("the combined assembly and both materials survive returning to plate 2",
                intArrayOf(1, 2), readUsedFilamentsOnGl(glView, 0))
            assertArrayEquals("the combined assembly retains its position after plate switching",
                joinedBounds, readExactBoundsOnGl(glView, 0).single(), 0.03)
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun modelMenuDeleteAllCancelAndBackPreserveTheLoadedModel() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val modelFile = File(activity.cacheDir, "menu-delete-all-cancel-test.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            loadSingleTestModel(bed, modelFile)
            val glView = requireNotNull(bed.glView)
            assertEquals(1, readObjectCountOnGl(glView))
            compose.onNodeWithContentDescription(activity.getString(R.string.MenuModel))
                .assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodesWithText(activity.getString(R.string.ModelMenuObjectsShort))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            selectAllObjectsScope()
            revealMenuAction(activity, R.string.ModelMenuDeleteAll)

            fun assertDeleteAllConfirmationShown() {
                compose.waitUntil(timeoutMillis = 5_000) { bed.isWorkspaceMessageDialogAttached() }
                compose.waitUntil(timeoutMillis = 5_000) {
                    compose.onAllNodesWithText(activity.getString(R.string.ModelDeleteAllConfirm))
                        .fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithText(activity.getString(R.string.ModelDeleteAllConfirm)).assertIsDisplayed()
            }

            val deleteAll = compose.onNodeWithContentDescription(activity.getString(R.string.ModelMenuDeleteAll))
                .assertIsDisplayed().assertIsEnabled()
            deleteAll.performClick()
            compose.waitUntil(timeoutMillis = 5_000) {
                bed.isWorkspaceMessageDialogAttached() || readObjectCountOnGl(glView) == 0
            }
            assertTrue(
                "Delete all should open a confirmation without mutating the model; " +
                    "objects=${readObjectCountOnGl(glView)}, " +
                    "selectedScope=${compose.onNodeWithTag("model-menu-scope-all").fetchSemanticsNode().config}",
                bed.isWorkspaceMessageDialogAttached(),
            )
            assertDeleteAllConfirmationShown()
            compose.onNodeWithText(activity.getString(android.R.string.cancel)).assertIsDisplayed().performClick()
            compose.waitForIdle()
            assertFalse("cancel must dismiss the workspace confirmation", bed.isWorkspaceMessageDialogAttached())
            assertEquals("cancel must not delete the loaded model", 1, readObjectCountOnGl(glView))

            compose.onNodeWithContentDescription(activity.getString(R.string.ModelMenuDeleteAll)).performClick()
            assertDeleteAllConfirmationShown()
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            compose.waitForIdle()
            assertFalse("Back must dismiss the workspace confirmation", bed.isWorkspaceMessageDialogAttached())
            assertEquals("Back must not delete the loaded model", 1, readObjectCountOnGl(glView))

            compose.onNodeWithContentDescription(activity.getString(R.string.ModelMenuDeleteAll)).performClick()
            assertDeleteAllConfirmationShown()
            compose.onNode(
                hasText(activity.getString(R.string.ModelMenuDeleteAll)) and hasClickAction(),
            ).assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 5_000) { !bed.isWorkspaceMessageDialogAttached() }
            val modelRemoved = CountDownLatch(1)
            val rendererIsEmpty = AtomicReference<Boolean>()
            glView.queueEvent {
                rendererIsEmpty.set(glView.renderer.model == null)
                modelRemoved.countDown()
            }
            assertTrue("GL thread should confirm delete-all completion", modelRemoved.await(10, TimeUnit.SECONDS))
            assertTrue("confirm should remove the model from the native renderer", requireNotNull(rendererIsEmpty.get()))

            compose.runOnUiThread { bed.undoModelEdit() }
            val undoWaitFailure = runCatching {
                compose.waitUntil(timeoutMillis = 10_000) { readObjectCountOnGl(glView) == 1 }
            }.exceptionOrNull()
            if (undoWaitFailure != null) {
                val disabled = BedFragment::class.java.getDeclaredField("modelHistoryDisabledForMemory")
                    .apply { isAccessible = true }.getBoolean(bed)
                @Suppress("UNCHECKED_CAST")
                val undoHistory = BedFragment::class.java.getDeclaredField("undoHistory")
                    .apply { isAccessible = true }.get(bed) as java.util.ArrayDeque<Model>
                val stableSnapshot = BedFragment::class.java.getDeclaredField("stableModelSnapshot")
                    .apply { isAccessible = true }.get(bed) as? Model
                val diagnostics = "Delete All Undo timed out: historyDisabled=$disabled, " +
                    "undoSnapshots=${undoHistory.size}, stableObjects=${stableSnapshot?.objectsCount}, " +
                    "nativeHeap=${android.os.Debug.getNativeHeapAllocatedSize()}, " +
                    "rendererObjects=${readObjectCountOnGl(glView)}"
                android.util.Log.e("DeleteAllUndoDiag", diagnostics, undoWaitFailure)
                assertTrue(diagnostics, false)
            }
            assertEquals("Undo should restore the object removed by Delete All", 1,
                readObjectCountOnGl(glView))
            compose.runOnUiThread { bed.redoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) { readObjectCountOnGl(glView) == 0 }
            assertEquals("Redo should restore the empty state after Delete All", 0,
                readObjectCountOnGl(glView))
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun modelMenuDeleteSelectedCancelBackAndConfirmUseTheNativeSelection() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val modelFile = File(activity.cacheDir, "menu-delete-selected-test.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            loadSingleTestModel(bed, modelFile)
            val glView = requireNotNull(bed.glView)
            glView.selectModel(0, Runnable {})
            instrumentationWaitForGlIdle()
            val copiesAdded = CountDownLatch(1)
            val addedCopies = AtomicReference<Int>()
            glView.duplicateSelectedModels(2) {
                addedCopies.set(it)
                bed.updateModel()
                copiesAdded.countDown()
            }
            assertTrue("GL thread should create the two selected test copies", copiesAdded.await(10, TimeUnit.SECONDS))
            assertEquals(2, requireNotNull(addedCopies.get()))
            glView.selectModels(intArrayOf(0, 2))
            instrumentationWaitForGlIdle()
            assertEquals("the disposable test group should contain three objects", 3, readObjectCountOnGl(glView))
            compose.runOnUiThread { bed.updateModel() }
            val controller = BedFragment::class.java.getDeclaredField("workspaceController")
                .apply { isAccessible = true }.get(bed)
            val modelSlot = WorkspaceDestination.MODEL.ordinal
            val shouldNavigate = controller.javaClass.getMethod("getSelectedDestinationSlot")
                .invoke(controller) != modelSlot
            val compactPortrait = usesCompactPortraitLayout(activity)
            val shouldExpand = compactPortrait && !(controller.javaClass.getMethod("getToolPanelExpanded")
                .invoke(controller) as Boolean)
            if (shouldNavigate || shouldExpand) {
                compose.onNodeWithContentDescription(activity.getString(R.string.MenuModel))
                    .assertIsDisplayed().performClick()
            }
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodesWithText(activity.getString(R.string.ModelMenuObjectsShort))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            revealMenuAction(activity, R.string.ModelMenuDeleteSelected)
            val deleteSelected = compose.onNodeWithContentDescription(
                activity.getString(R.string.ModelMenuDeleteSelected),
            ).performScrollTo().assertIsDisplayed().assertIsEnabled()

            deleteSelected.performClick()
            compose.waitUntil(timeoutMillis = 5_000) { bed.isWorkspaceMessageDialogAttached() }
            compose.onNodeWithText(activity.getString(R.string.ModelDeleteConfirm)).assertIsDisplayed()
            compose.onNodeWithText(activity.getString(android.R.string.cancel)).performClick()
            compose.waitForIdle()
            assertFalse("cancel should dismiss the selected-model confirmation", bed.isWorkspaceMessageDialogAttached())
            assertEquals("cancel should preserve all objects", 3, readObjectCountOnGl(glView))

            compose.onNodeWithContentDescription(activity.getString(R.string.ModelMenuDeleteSelected))
                .performScrollTo().performClick()
            compose.onNodeWithText(activity.getString(R.string.ModelDeleteConfirm)).assertIsDisplayed()
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            compose.waitForIdle()
            assertFalse("Back should dismiss the selected-model confirmation", bed.isWorkspaceMessageDialogAttached())
            assertEquals("Back should preserve all objects", 3, readObjectCountOnGl(glView))

            compose.onNodeWithContentDescription(activity.getString(R.string.ModelMenuDeleteSelected)).performClick()
            compose.onNodeWithText(activity.getString(R.string.ModelDeleteConfirm)).assertIsDisplayed()
            compose.onNode(
                hasText(activity.getString(R.string.ModelMenuDeleteSelected)) and hasClickAction(),
            ).performClick()
            compose.waitUntil(timeoutMillis = 5_000) { !bed.isWorkspaceMessageDialogAttached() }
            val removed = CountDownLatch(1)
            val remainingCount = AtomicReference<Int>()
            glView.queueEvent {
                remainingCount.set(glView.renderer.model?.objectsCount ?: 0)
                removed.countDown()
            }
            assertTrue("GL thread should confirm selected-group deletion", removed.await(10, TimeUnit.SECONDS))
            assertEquals("confirm should delete both selected objects and keep the unselected one",
                1, requireNotNull(remainingCount.get()))

            val undoHistory = BedFragment::class.java.getDeclaredField("undoHistory")
                .apply { isAccessible = true }.get(bed) as java.util.ArrayDeque<*>
            assertTrue("a committed group deletion should be undoable", undoHistory.isNotEmpty())
            assertEquals("undo snapshot should contain the pre-delete group", 3,
                (undoHistory.peekLast() as Model).objectsCount)
            compose.runOnUiThread { bed.undoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) { readObjectCountOnGl(glView) != 1 }
            assertEquals("undo should restore the deleted selection", 3, readObjectCountOnGl(glView))
            compose.runOnUiThread { bed.redoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) { readObjectCountOnGl(glView) == 1 }

            // Deleting the final remaining object produces a null renderer model. Undo and Redo
            // must still round-trip between that empty state and the restored object.
            glView.selectModel(0, Runnable {})
            instrumentationWaitForGlIdle()
            openSelectedObjectMenu(activity)
            revealMenuAction(activity, R.string.ModelMenuDeleteSelected)
            compose.onNodeWithContentDescription(activity.getString(R.string.ModelMenuDeleteSelected))
                .performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithText(activity.getString(R.string.ModelDeleteConfirm)).assertIsDisplayed()
            compose.onNode(hasText(activity.getString(R.string.ModelMenuDeleteSelected)) and hasClickAction())
                .performClick()
            compose.waitUntil(timeoutMillis = 10_000) { readObjectCountOnGl(glView) == 0 }
            compose.runOnUiThread { bed.undoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) { readObjectCountOnGl(glView) == 1 }
            compose.runOnUiThread { bed.redoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) { readObjectCountOnGl(glView) == 0 }
            assertEquals("Redo should delete the final restored object again", 0,
                readObjectCountOnGl(glView))
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun editScaleActionCancelsWithoutMutationAndAppliesOnlyAfterConfirmation() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val modelFile = File(activity.cacheDir, "menu-scale-action-test.stl")
        val previousLinked = Prefs.isScaleLinked()
        val previousMillimeters = Prefs.isScaleInputInMM()
        Prefs.setScaleLinked(false)
        Prefs.setScaleInputInMM(false)
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            val loadDone = CountDownLatch(1)
            val loaded = AtomicReference<Model?>()
            val loadError = AtomicReference<Exception?>()
            bed.loadModel(modelFile, false, 1, object : BedFragment.ModelLoadCallback {
                override fun onLoaded(loadedModel: Model, firstNewObject: Int, addedObjects: Int) {
                    loaded.set(loadedModel)
                    loadDone.countDown()
                }

                override fun onFailed(error: Exception) {
                    loadError.set(error)
                    loadDone.countDown()
                }
            })
            assertTrue("temporary scale model should finish loading", loadDone.await(60, TimeUnit.SECONDS))
            val model = requireNotNull(loaded.get()) { "temporary scale model failed: ${loadError.get()}" }
            assertEquals(1, model.objectsCount)
            val glView = requireNotNull(bed.glView)
            glView.selectModel(0, Runnable {})
            instrumentationWaitForGlIdle()

            openSelectedObjectMenu(activity)
            revealMenuAction(activity, R.string.MenuTransformScale)
            compose.onNodeWithContentDescription(activity.getString(R.string.MenuTransformScale))
                .performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithContentDescription("X, X =", substring = true).assertIsDisplayed()
            val original = readScaleOnGl(glView, 0)

            compose.onNodeWithContentDescription("X, X =", substring = true).performClick()
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodesWithText(activity.getString(R.string.ScaleManualPercent))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            val cancelledScaleField = compose.onNodeWithText(activity.getString(R.string.ScaleManualPercent))
            cancelledScaleField.performTextClearance()
            cancelledScaleField.performTextInput("150")
            compose.onNodeWithText(activity.getString(android.R.string.cancel)).performClick()
            compose.waitForIdle()
            val afterCancel = readScaleOnGl(glView, 0)
            assertEquals("cancelling the manual editor must preserve X", original.x, afterCancel.x, 0.0001)
            assertEquals("cancelling the manual editor must preserve Y", original.y, afterCancel.y, 0.0001)
            assertEquals("cancelling the manual editor must preserve Z", original.z, afterCancel.z, 0.0001)

            compose.onNodeWithContentDescription("X, X =", substring = true).performClick()
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodesWithText(activity.getString(R.string.ScaleManualPercent))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            val confirmedScaleField = compose.onNodeWithText(activity.getString(R.string.ScaleManualPercent))
            confirmedScaleField.performTextClearance()
            confirmedScaleField.performTextInput("125")
            compose.onNodeWithText(activity.getString(android.R.string.ok)).performClick()
            val afterApply = readScaleOnGl(glView, 0)
            assertEquals("confirming X=125% should scale only X", original.x * 1.25, afterApply.x, 0.002)
            assertEquals("unlinked X scaling should preserve Y", original.y, afterApply.y, 0.002)
            assertEquals("unlinked X scaling should preserve Z", original.z, afterApply.z, 0.002)

            // A committed scale advances the model geometry revision. The same still-open
            // scale tool must remain able to preview and commit another value.
            compose.onNodeWithContentDescription("X, X =", substring = true).performClick()
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodesWithText(activity.getString(R.string.ScaleManualPercent))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            val secondScaleField = compose.onNodeWithText(activity.getString(R.string.ScaleManualPercent))
            secondScaleField.performTextClearance()
            secondScaleField.performTextInput("150")
            compose.onNodeWithText(activity.getString(android.R.string.ok)).performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                kotlin.math.abs(readScaleOnGl(glView, 0).x - original.x * 1.5) < 0.002
            }
            assertEquals("a second confirmed scale must update the native model", original.x * 1.5,
                readScaleOnGl(glView, 0).x, 0.002)

            compose.runOnUiThread { bed.undoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) {
                kotlin.math.abs(readScaleOnGl(glView, 0).x - original.x * 1.25) < 0.002
            }
            compose.runOnUiThread { bed.undoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) {
                kotlin.math.abs(readScaleOnGl(glView, 0).x - original.x) < 0.002
            }
            val afterUndo = readScaleOnGl(glView, 0)
            assertEquals("Undo should restore the pre-scale model", original.x, afterUndo.x, 0.002)
            compose.runOnUiThread { bed.redoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) {
                kotlin.math.abs(readScaleOnGl(glView, 0).x - original.x * 1.25) < 0.002
            }
            compose.runOnUiThread { bed.redoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) {
                kotlin.math.abs(readScaleOnGl(glView, 0).x - original.x * 1.5) < 0.002
            }
            val afterRedo = readScaleOnGl(glView, 0)
            assertEquals("Redo should restore the latest confirmed scale", original.x * 1.5, afterRedo.x, 0.002)

            compose.onNodeWithContentDescription("Z, Z =", substring = true).assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodesWithText(activity.getString(R.string.ScaleManualPercent))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            val zScaleField = compose.onNodeWithText(activity.getString(R.string.ScaleManualPercent))
            zScaleField.performTextClearance()
            zScaleField.performTextInput("140")
            compose.onNodeWithText(activity.getString(android.R.string.ok)).performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                kotlin.math.abs(readScaleOnGl(glView, 0).z - original.z * 1.4) < 0.002
            }
            val afterZScale = readScaleOnGl(glView, 0)
            assertEquals("confirmed Z=140% must scale native Z", original.z * 1.4, afterZScale.z, 0.002)
            assertEquals("unlinked Z scaling must preserve X", original.x * 1.5, afterZScale.x, 0.002)
            assertEquals("unlinked Z scaling must preserve Y", original.y, afterZScale.y, 0.002)
            compose.runOnUiThread { bed.undoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) {
                kotlin.math.abs(readScaleOnGl(glView, 0).z - original.z) < 0.002
            }
            compose.runOnUiThread { bed.redoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) {
                kotlin.math.abs(readScaleOnGl(glView, 0).z - original.z * 1.4) < 0.002
            }

            val workspaceController = BedFragment::class.java.getDeclaredField("workspaceController")
                .apply { isAccessible = true }.get(bed)
            assertTrue("workspace host should consume Back while the scale tool is active", bed.onBackPressed())
            compose.waitUntil(timeoutMillis = 5_000) {
                workspaceController.javaClass.getMethod("getActiveToolId").invoke(workspaceController) == null
            }
            assertEquals("Android Back should clear the active scale tool", null,
                workspaceController.javaClass.getMethod("getActiveToolId").invoke(workspaceController))
            assertEquals("closing the scale tool should preserve the Model destination",
                WorkspaceDestination.MODEL.ordinal,
                workspaceController.javaClass.getMethod("getSelectedDestinationSlot").invoke(workspaceController))
            assertEquals("closing the tool must keep the committed scale", original.x * 1.5,
                readScaleOnGl(glView, 0).x, 0.002)

            openSelectedObjectMenu(activity)
            revealMenuAction(activity, R.string.ModelContextResetScale)
            compose.onNodeWithContentDescription(activity.getString(R.string.ModelContextResetScale))
                .performScrollTo().assertIsDisplayed().performClick()
            compose.waitForIdle()
            val afterReset = readScaleOnGl(glView, 0)
            assertEquals("Reset Scale should restore the original native X scale", original.x,
                afterReset.x, 0.002)
            assertEquals("Reset Scale should restore the original native Z scale", original.z,
                afterReset.z, 0.002)
            compose.runOnUiThread { bed.undoModelEdit() }
            assertEquals("Undo should restore the explicitly confirmed scale after reset", original.x * 1.5,
                readScaleOnGl(glView, 0).x, 0.002)
            assertEquals("Undo of reset should restore the explicitly confirmed Z scale", original.z * 1.4,
                readScaleOnGl(glView, 0).z, 0.002)
            compose.runOnUiThread { bed.redoModelEdit() }
            assertEquals("Redo should restore the reset scale", original.x,
                readScaleOnGl(glView, 0).x, 0.002)
            assertEquals("Redo of reset should restore original Z scale", original.z,
                readScaleOnGl(glView, 0).z, 0.002)
        } finally {
            Prefs.setScaleLinked(previousLinked)
            Prefs.setScaleInputInMM(previousMillimeters)
            modelFile.delete()
        }
    }

    @Test
    fun scaleLinkSwitchOffKeepsYAxisIndependentForManualEntry() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val modelFile = File(activity.cacheDir, "scale-unlinked-y-axis-test.stl")
        val previousLinked = Prefs.isScaleLinked()
        val previousMillimeters = Prefs.isScaleInputInMM()
        Prefs.setScaleLinked(true)
        Prefs.setScaleInputInMM(false)
        try {
            activity.assets.open("models/pa_test.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            val model = loadSingleTestModel(bed, modelFile)
            val glView = requireNotNull(bed.glView)
            glView.selectModel(0, Runnable {})
            instrumentationWaitForGlIdle()
            val original = readScaleOnGl(glView, 0)
            val boundsBefore = readExactBoundsOnGl(glView, 0).single()

            openSelectedObjectMenu(activity)
            revealMenuAction(activity, R.string.MenuTransformScale)
            compose.onNodeWithContentDescription(activity.getString(R.string.MenuTransformScale))
                .performScrollTo().assertIsDisplayed().performClick()
            val linkSwitch = compose.onNodeWithTag("scale-linked-switch").performScrollTo().assertIsDisplayed()
            assertTrue("fixture must start with linked scaling enabled", Prefs.isScaleLinked())
            linkSwitch.performClick()
            compose.waitUntil(timeoutMillis = 5_000) { !Prefs.isScaleLinked() }

            compose.onNodeWithContentDescription("Y, Y =", substring = true).performClick()
            val scaleField = compose.onNodeWithText(activity.getString(R.string.ScaleManualPercent))
            scaleField.performTextClearance()
            scaleField.performTextInput("150")
            compose.onNodeWithText(activity.getString(android.R.string.ok)).performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                kotlin.math.abs(readScaleOnGl(glView, 0).y - original.y * 1.5) < 0.002
            }

            val afterY = readScaleOnGl(glView, 0)
            assertEquals("Y=150% should scale only native Y", original.y * 1.5, afterY.y, 0.002)
            assertEquals("switching Keep proportions off must preserve native X", original.x, afterY.x, 0.002)
            assertEquals("switching Keep proportions off must preserve native Z", original.z, afterY.z, 0.002)
            val boundsAfterY = readExactBoundsOnGl(glView, 0).single()
            assertEquals("unlinked Y scaling must not change the model's X bounds",
                boundsBefore[3] - boundsBefore[0], boundsAfterY[3] - boundsAfterY[0], 0.02)
            assertEquals("unlinked Y scaling must change the model's Y bounds by 50%",
                (boundsBefore[4] - boundsBefore[1]) * 1.5, boundsAfterY[4] - boundsAfterY[1], 0.02)
            assertEquals("unlinked Y scaling must preserve the model's Z bounds",
                boundsBefore[5] - boundsBefore[2], boundsAfterY[5] - boundsAfterY[2], 0.02)
            compose.runOnUiThread { bed.undoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) {
                kotlin.math.abs(readScaleOnGl(glView, 0).y - original.y) < 0.002
            }
            compose.runOnUiThread { bed.redoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) {
                kotlin.math.abs(readScaleOnGl(glView, 0).y - original.y * 1.5) < 0.002
            }
            val afterRedo = readScaleOnGl(glView, 0)
            assertEquals("redo preserves the independent X factor", original.x, afterRedo.x, 0.002)
            assertEquals("redo preserves the independent Z factor", original.z, afterRedo.z, 0.002)
        } finally {
            Prefs.setScaleLinked(previousLinked)
            Prefs.setScaleInputInMM(previousMillimeters)
            modelFile.delete()
        }
    }

    @Test
    fun unlinkedYAxisScaleStaysIndependentAfterObjectRotation() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val modelFile = File(activity.cacheDir, "scale-rotated-y-axis-test.stl")
        val previousLinked = Prefs.isScaleLinked()
        val previousMillimeters = Prefs.isScaleInputInMM()
        Prefs.setScaleLinked(false)
        Prefs.setScaleInputInMM(false)
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            loadSingleTestModel(bed, modelFile)
            val glView = requireNotNull(bed.glView)
            glView.selectModel(0, Runnable {})
            instrumentationWaitForGlIdle()

            val rotated = CountDownLatch(1)
            val rotationFailure = AtomicReference<Throwable?>()
            glView.queueEvent {
                try {
                    check(glView.renderer.rotateSelectedObjects(0.0, 0.0, Math.toRadians(90.0)))
                } catch (error: Throwable) {
                    rotationFailure.set(error)
                } finally {
                    rotated.countDown()
                }
            }
            assertTrue("GL thread did not rotate the test model", rotated.await(10, TimeUnit.SECONDS))
            rotationFailure.get()?.let { throw AssertionError("Could not rotate the test model", it) }
            instrumentationWaitForGlIdle()
            val original = readScaleOnGl(glView, 0)
            val rotatedBoundsBefore = readExactBoundsOnGl(glView, 0).single()

            openSelectedObjectMenu(activity)
            revealMenuAction(activity, R.string.MenuTransformScale)
            compose.onNodeWithContentDescription(activity.getString(R.string.MenuTransformScale))
                .performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithContentDescription("Y, Y =", substring = true).performScrollTo().performClick()
            val scaleField = compose.onNodeWithText(activity.getString(R.string.ScaleManualPercent))
            scaleField.performTextClearance()
            scaleField.performTextInput("150")
            compose.onNodeWithText(activity.getString(android.R.string.ok)).performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                kotlin.math.abs(readScaleOnGl(glView, 0).y - original.y * 1.5) < 0.002
            }

            val scaled = readScaleOnGl(glView, 0)
            assertEquals("after 90-degree object rotation, Y=150% must still change only native Y",
                original.y * 1.5, scaled.y, 0.002)
            assertEquals("rotated Y scaling must leave native X unchanged", original.x, scaled.x, 0.002)
            assertEquals("rotated Y scaling must leave native Z unchanged", original.z, scaled.z, 0.002)
            val rotatedBoundsAfter = readExactBoundsOnGl(glView, 0).single()
            assertEquals("after rotation, unlinked Y scaling must preserve plate-space X bounds",
                rotatedBoundsBefore[3] - rotatedBoundsBefore[0],
                rotatedBoundsAfter[3] - rotatedBoundsAfter[0], 0.02)
            assertEquals("after rotation, unlinked Y scaling must change plate-space Y bounds by 50%",
                (rotatedBoundsBefore[4] - rotatedBoundsBefore[1]) * 1.5,
                rotatedBoundsAfter[4] - rotatedBoundsAfter[1], 0.02)
            assertEquals("after rotation, unlinked Y scaling must preserve plate-space Z bounds",
                rotatedBoundsBefore[5] - rotatedBoundsBefore[2],
                rotatedBoundsAfter[5] - rotatedBoundsAfter[2], 0.02)
        } finally {
            Prefs.setScaleLinked(previousLinked)
            Prefs.setScaleInputInMM(previousMillimeters)
            modelFile.delete()
        }
    }

    @Test
    fun unlinkedScaleRulerChangesOnlyTheChosenPlateAxis() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val modelFile = File(activity.cacheDir, "scale-unlinked-y-ruler-test.stl")
        val previousLinked = Prefs.isScaleLinked()
        val previousMillimeters = Prefs.isScaleInputInMM()
        Prefs.setScaleLinked(false)
        Prefs.setScaleInputInMM(false)
        try {
            activity.assets.open("models/pa_test.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            loadSingleTestModel(bed, modelFile)
            val glView = requireNotNull(bed.glView)
            glView.selectModel(0, Runnable {})
            instrumentationWaitForGlIdle()
            val originalScale = readScaleOnGl(glView, 0)
            val boundsBefore = readExactBoundsOnGl(glView, 0).single()

            openSelectedObjectMenu(activity)
            revealMenuAction(activity, R.string.MenuTransformScale)
            compose.onNodeWithContentDescription(activity.getString(R.string.MenuTransformScale))
                .performScrollTo().assertIsDisplayed().performClick()
            assertFalse("the ruler case must use independent axis scaling", Prefs.isScaleLinked())
            compose.onNodeWithTag("scale-Y").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) {
                setProgress -> assertTrue("Y scale ruler accepts 140%", setProgress(140f))
            }
            compose.waitUntil(timeoutMillis = 10_000) {
                kotlin.math.abs(readScaleOnGl(glView, 0).y - originalScale.y * 1.4) < 0.002
            }

            val actualScale = readScaleOnGl(glView, 0)
            assertEquals("unlinked ruler Y must preserve the X factor", originalScale.x, actualScale.x, 0.002)
            assertEquals("unlinked ruler Y must preserve the Z factor", originalScale.z, actualScale.z, 0.002)
            val boundsAfter = readExactBoundsOnGl(glView, 0).single()
            assertEquals("unlinked ruler Y must preserve plate-space X bounds",
                boundsBefore[3] - boundsBefore[0], boundsAfter[3] - boundsAfter[0], 0.02)
            assertEquals("unlinked ruler Y must scale plate-space Y bounds by 40%",
                (boundsBefore[4] - boundsBefore[1]) * 1.4, boundsAfter[4] - boundsAfter[1], 0.02)
            assertEquals("unlinked ruler Y must preserve plate-space Z bounds",
                boundsBefore[5] - boundsBefore[2], boundsAfter[5] - boundsAfter[2], 0.02)

            val filamentAssigned = CountDownLatch(1)
            glView.queueEvent {
                requireNotNull(glView.renderer.model).setUniformFilament(0, 2)
                glView.requestRender()
                filamentAssigned.countDown()
            }
            assertTrue("test model filament assignment should finish", filamentAssigned.await(10, TimeUnit.SECONDS))
            assertCurrentWorkspaceModelRoundTripsAndSlices(
                "scale-unlinked-y-ruler",
                expectedObjectFilaments = listOf(intArrayOf(2)),
            )
        } finally {
            Prefs.setScaleLinked(previousLinked)
            Prefs.setScaleInputInMM(previousMillimeters)
            modelFile.delete()
        }
    }

    @Test
    fun unlinkedXAxisAndZAxisManualScaleChangeOnlyTheirOwnAxes() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val modelFile = File(activity.cacheDir, "scale-unlinked-x-z-axis-test.stl")
        val previousLinked = Prefs.isScaleLinked()
        val previousMillimeters = Prefs.isScaleInputInMM()
        Prefs.setScaleLinked(false)
        Prefs.setScaleInputInMM(false)
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            loadSingleTestModel(bed, modelFile)
            val glView = requireNotNull(bed.glView)
            glView.selectModel(0, Runnable {})
            instrumentationWaitForGlIdle()
            val original = readScaleOnGl(glView, 0)

            openSelectedObjectMenu(activity)
            revealMenuAction(activity, R.string.MenuTransformScale)
            compose.onNodeWithContentDescription(activity.getString(R.string.MenuTransformScale))
                .performScrollTo().assertIsDisplayed().performClick()

            compose.onNodeWithContentDescription("X, X =", substring = true).performScrollTo().performClick()
            var scaleField = compose.onNodeWithText(activity.getString(R.string.ScaleManualPercent))
            scaleField.performTextClearance()
            scaleField.performTextInput("125")
            compose.onNodeWithText(activity.getString(android.R.string.ok)).performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                kotlin.math.abs(readScaleOnGl(glView, 0).x - original.x * 1.25) < 0.002
            }
            val afterX = readScaleOnGl(glView, 0)
            assertEquals("X edit must preserve native Y", original.y, afterX.y, 0.002)
            assertEquals("X edit must preserve native Z", original.z, afterX.z, 0.002)

            compose.onNodeWithContentDescription("Z, Z =", substring = true).performScrollTo().performClick()
            scaleField = compose.onNodeWithText(activity.getString(R.string.ScaleManualPercent))
            scaleField.performTextClearance()
            scaleField.performTextInput("75")
            compose.onNodeWithText(activity.getString(android.R.string.ok)).performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                kotlin.math.abs(readScaleOnGl(glView, 0).z - original.z * 0.75) < 0.002
            }
            val afterZ = readScaleOnGl(glView, 0)
            assertEquals("following Z edit must retain the earlier X change", original.x * 1.25, afterZ.x, 0.002)
            assertEquals("Z edit must preserve native Y", original.y, afterZ.y, 0.002)

            compose.runOnUiThread { bed.undoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) {
                kotlin.math.abs(readScaleOnGl(glView, 0).z - original.z) < 0.002
            }
            compose.runOnUiThread { bed.undoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) {
                kotlin.math.abs(readScaleOnGl(glView, 0).x - original.x) < 0.002
            }
            compose.runOnUiThread { bed.redoModelEdit() }
            compose.runOnUiThread { bed.redoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) {
                val redone = readScaleOnGl(glView, 0)
                kotlin.math.abs(redone.x - original.x * 1.25) < 0.002 &&
                    kotlin.math.abs(redone.y - original.y) < 0.002 &&
                    kotlin.math.abs(redone.z - original.z * 0.75) < 0.002
            }
        } finally {
            Prefs.setScaleLinked(previousLinked)
            Prefs.setScaleInputInMM(previousMillimeters)
            modelFile.delete()
        }
    }

    @Test
    fun mirrorPanelReflectsSelectedObjectOnEachAxisAndRecordsUndoRedo() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val modelFile = File(activity.cacheDir, "menu-mirror-action-test.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            val model = loadSingleTestModel(bed, modelFile)
            assertEquals(1, model.objectsCount)
            val glView = requireNotNull(bed.glView)
            glView.queueEvent {
                requireNotNull(glView.renderer.model).setUniformFilament(0, 2)
                glView.requestRender()
            }
            instrumentationWaitForGlIdle()
            compose.runOnUiThread { bed.updateModel() }
            glView.selectModel(0, Runnable {})
            instrumentationWaitForGlIdle()
            val initialBounds = readExactBoundsOnGl(glView, 0).single()
            fun center(bounds: DoubleArray, axis: Int) = (bounds[axis] + bounds[axis + 3]) / 2.0
            val initialCenter = (0..2).map { center(initialBounds, it) }

            openSelectedObjectMenu(activity)
            revealMenuAction(activity, R.string.MenuTransformScale)
            compose.onNodeWithContentDescription(activity.getString(R.string.MenuTransformScale))
                .performScrollTo().assertIsDisplayed().performClick()
            // The compact phone panel keeps all three scale axes and the mirror controls in
            // one scroll column. On shorter devices the mirror row starts below the viewport.
            compose.onNodeWithTag("scale-mirror-x").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("scale-mirror-y").assertIsDisplayed()
            compose.onNodeWithTag("scale-mirror-z").assertIsDisplayed()

            val undoHistory = BedFragment::class.java.getDeclaredField("undoHistory")
                .apply { isAccessible = true }.get(bed) as java.util.ArrayDeque<*>
            val initialUndoSize = undoHistory.size
            val mirrorButton = compose.onNodeWithTag("scale-mirror-x")
            compose.waitUntil(timeoutMillis = 10_000) {
                runCatching { mirrorButton.assertIsEnabled() }.isSuccess
            }
            mirrorButton.performScrollTo().assertIsDisplayed().assertIsEnabled().performTouchInput { click() }
            compose.waitUntil(timeoutMillis = 10_000) {
                hasExpectedLinearTransform(glView, 0, doubleArrayOf(-1.0, 1.0, 1.0)) &&
                    undoHistory.size > initialUndoSize
            }
            val afterXBounds = readExactBoundsOnGl(glView, 0).single()
            (0..2).forEach { axis -> assertEquals("X reflection keeps world center", initialCenter[axis], center(afterXBounds, axis), 0.01) }
            assertArrayEquals("X reflection preserves the object's filament", intArrayOf(2),
                readUsedFilamentsOnGl(glView, 0))

            compose.runOnUiThread { bed.undoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) {
                hasExpectedLinearTransform(glView, 0, doubleArrayOf(1.0, 1.0, 1.0))
            }
            assertArrayEquals("Undo reflection preserves the object's filament", intArrayOf(2),
                readUsedFilamentsOnGl(glView, 0))
            compose.runOnUiThread { bed.redoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) {
                hasExpectedLinearTransform(glView, 0, doubleArrayOf(-1.0, 1.0, 1.0))
            }
            assertArrayEquals("Redo reflection preserves the object's filament", intArrayOf(2),
                readUsedFilamentsOnGl(glView, 0))

            compose.onNodeWithTag("scale-mirror-y").performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                hasExpectedLinearTransform(glView, 0, doubleArrayOf(-1.0, -1.0, 1.0))
            }
            compose.onNodeWithTag("scale-mirror-z").performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                hasExpectedLinearTransform(glView, 0, doubleArrayOf(-1.0, -1.0, -1.0))
            }
            assertArrayEquals("XYZ reflections preserve the object's filament", intArrayOf(2),
                readUsedFilamentsOnGl(glView, 0))
            assertTrue("mirrored object must remain renderable", readTriangleCountOnGl(glView, 0) > 0)
            assertCurrentWorkspaceModelRoundTripsAndSlices("mirror-xyz")
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun mirrorPanelReflectsTheWholeSelectedGroupAndKeepsPlacementAndFilaments() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val modelFile = File(activity.cacheDir, "menu-mirror-group-test.stl")
        try {
            activity.assets.open("models/pyramid.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            val model = loadSingleTestModel(bed, modelFile)
            val glView = requireNotNull(bed.glView)
            val prepared = CountDownLatch(1)
            glView.queueEvent {
                model.addObject(model, 0)
                model.translate(1, 37.0, 19.0, 0.0)
                model.setUniformFilament(0, 1)
                model.setUniformFilament(1, 2)
                glView.renderer.setSelectedObjects(intArrayOf(0, 1))
                glView.requestRender()
                prepared.countDown()
            }
            assertTrue("both disposable objects should be prepared as a GL selection", prepared.await(10, TimeUnit.SECONDS))
            compose.runOnUiThread { bed.updateModel() }
            instrumentationWaitForGlIdle()

            val initialBounds = readExactBoundsOnGl(glView, 0, 1)
            val initialCenters = initialBounds.map { bounds ->
                (0..2).map { axis -> (bounds[axis] + bounds[axis + 3]) / 2.0 }
            }
            val initialFilaments = (0..1).map { readUsedFilamentsOnGl(glView, it).toList() }
            openSelectedObjectMenu(activity)
            revealMenuAction(activity, R.string.MenuTransformScale)
            compose.onNodeWithContentDescription(activity.getString(R.string.MenuTransformScale))
                .performScrollTo().assertIsDisplayed().performClick()

            val undoHistory = BedFragment::class.java.getDeclaredField("undoHistory")
                .apply { isAccessible = true }.get(bed) as java.util.ArrayDeque<*>
            val initialUndoSize = undoHistory.size
            compose.onNodeWithTag("scale-mirror-x").performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
            val mirrorApplied = runCatching {
                compose.waitUntil(timeoutMillis = 10_000) {
                    (0..1).all { objectIndex ->
                        hasExpectedLinearTransform(glView, objectIndex, doubleArrayOf(-1.0, 1.0, 1.0))
                    }
                }
            }.isSuccess
            val historyRecorded = runCatching {
                compose.waitUntil(timeoutMillis = 10_000) { undoHistory.size > initialUndoSize }
            }.isSuccess
            assertTrue(
                "group X mirror should apply and be undoable; applied=$mirrorApplied, " +
                    "historyRecorded=$historyRecorded, history=${undoHistory.size}/$initialUndoSize, " +
                    "selection=${readSelectedIndicesOnGl(glView).contentToString()}, " +
                    "mirrors=${(0..1).map { index -> readVectorOnGl(glView, index) { model, vector -> model.getMirror(index, vector) }.let { doubleArrayOf(it.x, it.y, it.z).contentToString() } }}, " +
                    "scales=${(0..1).map { index -> readScaleOnGl(glView, index).let { doubleArrayOf(it.x, it.y, it.z).contentToString() } }}, " +
                    "rotations=${(0..1).map { index -> readRotationOnGl(glView, index).let { doubleArrayOf(it.x, it.y, it.z).contentToString() } }}",
                mirrorApplied && historyRecorded,
            )
            compose.runOnUiThread { bed.undoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) {
                (0..1).all { objectIndex ->
                    hasExpectedLinearTransform(glView, objectIndex, doubleArrayOf(1.0, 1.0, 1.0))
                }
            }
            compose.runOnUiThread { bed.redoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) {
                (0..1).all { objectIndex ->
                    hasExpectedLinearTransform(glView, objectIndex, doubleArrayOf(-1.0, 1.0, 1.0))
                }
            }
            compose.onNodeWithTag("scale-mirror-y").performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                (0..1).all { objectIndex ->
                    hasExpectedLinearTransform(glView, objectIndex, doubleArrayOf(-1.0, -1.0, 1.0))
                }
            }
            compose.onNodeWithTag("scale-mirror-z").performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                (0..1).all { objectIndex ->
                    hasExpectedLinearTransform(glView, objectIndex, doubleArrayOf(-1.0, -1.0, -1.0))
                }
            }

            val finalBounds = readExactBoundsOnGl(glView, 0, 1)
            finalBounds.forEachIndexed { objectIndex, bounds ->
                (0..2).forEach { axis ->
                    assertEquals(
                        "mirroring must retain object $objectIndex's world-space center on axis $axis",
                        initialCenters[objectIndex][axis], (bounds[axis] + bounds[axis + 3]) / 2.0, 0.05,
                    )
                }
            }
            assertEquals("all selected objects must keep their own filament assignment",
                initialFilaments, (0..1).map { readUsedFilamentsOnGl(glView, it).toList() })
            assertEquals("group mirroring must not add or remove objects", 2, readObjectCountOnGl(glView))
            assertTrue("both mirrored objects must remain renderable",
                (0..1).all { readTriangleCountOnGl(glView, it) > 0 })
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun editPositionActionIsUndoableAndRedoableAfterConfirmation() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val modelFile = File(activity.cacheDir, "menu-position-action-test.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            val loaded = loadSingleTestModel(bed, modelFile)
            assertEquals(1, loaded.objectsCount)
            val glView = requireNotNull(bed.glView)
            glView.selectModel(0, Runnable {})
            instrumentationWaitForGlIdle()
            val original = readTranslationOnGl(glView, 0)

            openSelectedObjectMenu(activity)
            revealMenuAction(activity, R.string.MenuToolbarMove)
            compose.onNodeWithContentDescription(activity.getString(R.string.MenuToolbarMove))
                .assertIsDisplayed().performClick()
            compose.onNodeWithText("X =", substring = true).assertIsDisplayed().performClick()
            val positionField = compose.onNodeWithText(activity.getString(R.string.OrientationManualValue))
            positionField.assertIsDisplayed()
            positionField.performTextClearance()
            positionField.performTextInput("12.5")
            compose.onNodeWithText(activity.getString(android.R.string.ok)).performClick()
            val moved = readTranslationOnGl(glView, 0)
            assertEquals("confirming position X should move the native object", 12.5, moved.x, 0.01)
            assertEquals("position editing should preserve Y", original.y, moved.y, 0.01)

            compose.runOnUiThread { bed.undoModelEdit() }
            val afterUndo = readTranslationOnGl(glView, 0)
            assertEquals("Undo should restore the original X position", original.x, afterUndo.x, 0.01)
            assertEquals("Undo should restore the original Y position", original.y, afterUndo.y, 0.01)
            compose.runOnUiThread { bed.redoModelEdit() }
            assertEquals("Redo should restore the confirmed X position", 12.5,
                readTranslationOnGl(glView, 0).x, 0.01)
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun editPositionZIsVisibleCancelableAndUndoable() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val modelFile = File(activity.cacheDir, "menu-position-z-test.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            val loaded = loadSingleTestModel(bed, modelFile)
            assertEquals(1, loaded.objectsCount)
            val glView = requireNotNull(bed.glView)
            glView.selectModel(0, Runnable {})
            instrumentationWaitForGlIdle()
            val original = readTranslationOnGl(glView, 0)

            openSelectedObjectMenu(activity)
            revealMenuAction(activity, R.string.MenuToolbarMove)
            compose.onNodeWithContentDescription(activity.getString(R.string.MenuToolbarMove))
                .assertIsDisplayed().performClick()
            compose.onNodeWithText("Z =", substring = true).assertIsDisplayed().performClick()
            val positionField = compose.onNodeWithText(activity.getString(R.string.OrientationManualValue))
            positionField.assertIsDisplayed()
            positionField.performTextClearance()
            positionField.performTextInput("18.75")
            compose.onNodeWithText(activity.getString(android.R.string.cancel)).performClick()
            assertEquals("Cancelling the Z editor must preserve native Z", original.z,
                readTranslationOnGl(glView, 0).z, 0.01)

            compose.onNodeWithText("Z =", substring = true).assertIsDisplayed().performClick()
            positionField.assertIsDisplayed()
            positionField.performTextClearance()
            positionField.performTextInput("18.75")
            compose.onNodeWithText(activity.getString(android.R.string.ok)).performClick()
            val moved = readTranslationOnGl(glView, 0)
            assertEquals("Confirming position Z should move the native object", 18.75, moved.z, 0.01)
            assertEquals("Z editing must preserve X", original.x, moved.x, 0.01)
            assertEquals("Z editing must preserve Y", original.y, moved.y, 0.01)

            compose.runOnUiThread { bed.undoModelEdit() }
            assertEquals("Undo should restore the original Z position", original.z,
                readTranslationOnGl(glView, 0).z, 0.01)
            compose.runOnUiThread { bed.redoModelEdit() }
            assertEquals("Redo should restore the confirmed Z position", 18.75,
                readTranslationOnGl(glView, 0).z, 0.01)

            compose.onNodeWithContentDescription("Z")
                .performSemanticsAction(SemanticsActions.SetProgress) { setProgress -> setProgress(24f) }
            val rulerMoved = readTranslationOnGl(glView, 0)
            assertEquals("The Z ruler must preview and commit a Z translation", 24.0, rulerMoved.z, 0.01)
            assertEquals("The Z ruler must not change X", original.x, rulerMoved.x, 0.01)
            assertEquals("The Z ruler must not change Y", original.y, rulerMoved.y, 0.01)
            compose.runOnUiThread { bed.undoModelEdit() }
            assertEquals("Undo should restore the previous Z after ruler movement", 18.75,
                readTranslationOnGl(glView, 0).z, 0.01)
            compose.runOnUiThread { bed.redoModelEdit() }
            assertEquals("Redo should restore the ruler-selected Z", 24.0,
                readTranslationOnGl(glView, 0).z, 0.01)
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun positionEditorMovesEverySelectedObjectTogetherOnXYZAndKeepsUndoRedo() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val modelFile = File(activity.cacheDir, "menu-position-selected-group-test.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            loadSingleTestModel(bed, modelFile)
            val prepared = CountDownLatch(1)
            glView.queueEvent {
                val nativeModel = requireNotNull(glView.renderer.model)
                val min = Vec3d()
                val max = Vec3d()
                nativeModel.getBoundingBoxExact(0, min, max)
                nativeModel.addObject(nativeModel, 0)
                nativeModel.translate(1, (max.x - min.x) * 2.0, 0.0, 0.0)
                glView.renderer.setSelectedObjects(intArrayOf(0, 1))
                glView.requestRender()
                prepared.countDown()
            }
            assertTrue("two selected objects should be prepared on the GL thread", prepared.await(10, TimeUnit.SECONDS))
            compose.runOnUiThread { bed.updateModel() }
            instrumentationWaitForGlIdle()
            val before = (0..1).map { readTranslationOnGl(glView, it) }

            openSelectedObjectMenu(activity)
            revealMenuAction(activity, R.string.MenuToolbarMove)
            compose.onNodeWithContentDescription(activity.getString(R.string.MenuToolbarMove))
                .performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()

            fun enterAxisValue(axis: String, value: Double, confirm: Boolean) {
                compose.onNodeWithText("$axis =", substring = true).assertIsDisplayed().performClick()
                val field = compose.onNodeWithText(activity.getString(R.string.OrientationManualValue))
                field.assertIsDisplayed().performTextClearance()
                field.performTextInput(value.toString())
                compose.onNodeWithText(activity.getString(if (confirm) android.R.string.ok else android.R.string.cancel))
                    .assertIsDisplayed().performClick()
                compose.waitForIdle()
            }

            enterAxisValue("X", before[0].x + 11.0, confirm = false)
            assertEquals("cancelling the active-object position field must preserve the group",
                before[0].x, readTranslationOnGl(glView, 0).x, 0.01)
            assertEquals("cancelling must not move the second selected object",
                before[1].x, readTranslationOnGl(glView, 1).x, 0.01)

            enterAxisValue("X", before[0].x + 11.0, confirm = true)
            val afterX = (0..1).map { readTranslationOnGl(glView, it) }
            enterAxisValue("Y", before[0].y + 13.0, confirm = true)
            val afterY = (0..1).map { readTranslationOnGl(glView, it) }
            enterAxisValue("Z", before[0].z + 17.0, confirm = true)
            val after = (0..1).map { readTranslationOnGl(glView, it) }
            for (index in 0..1) {
                assertEquals("X entry should apply the same group delta to object $index",
                    before[index].x + 11.0, after[index].x, 0.02)
                assertEquals("Y entry should apply the same group delta to object $index",
                    before[index].y + 13.0, after[index].y, 0.02)
                assertEquals("Z entry should apply the same group delta to object $index",
                    before[index].z + 17.0, after[index].z, 0.02)
            }
            assertEquals("group move must preserve X spacing", before[1].x - before[0].x,
                after[1].x - after[0].x, 0.02)

            fun assertGroupTranslations(expected: List<Vec3d>, action: String) {
                compose.waitUntil(timeoutMillis = 10_000) {
                    (0..1).all { index ->
                        val actual = readTranslationOnGl(glView, index)
                        kotlin.math.abs(actual.x - expected[index].x) < 0.02 &&
                            kotlin.math.abs(actual.y - expected[index].y) < 0.02 &&
                            kotlin.math.abs(actual.z - expected[index].z) < 0.02
                    }
                }
                (0..1).forEach { index ->
                    val actual = readTranslationOnGl(glView, index)
                    assertEquals("$action should restore object $index X", expected[index].x, actual.x, 0.02)
                    assertEquals("$action should restore object $index Y", expected[index].y, actual.y, 0.02)
                    assertEquals("$action should restore object $index Z", expected[index].z, actual.z, 0.02)
                }
            }

            compose.runOnUiThread { bed.undoModelEdit() }
            assertGroupTranslations(afterY, "Undo Z")
            compose.runOnUiThread { bed.undoModelEdit() }
            assertGroupTranslations(afterX, "Undo Y")
            compose.runOnUiThread { bed.undoModelEdit() }
            assertGroupTranslations(before, "Undo X")

            compose.runOnUiThread { bed.redoModelEdit() }
            assertGroupTranslations(afterX, "Redo X")
            compose.runOnUiThread { bed.redoModelEdit() }
            assertGroupTranslations(afterY, "Redo Y")
            compose.runOnUiThread { bed.redoModelEdit() }
            assertGroupTranslations(after, "Redo Z")
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun modelMenuAlignsSelectedGroupOnXYZEdgesAndCentersWithUndoRedo() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val modelFile = File(activity.cacheDir, "menu-group-align-test.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            loadSingleTestModel(bed, modelFile)
            val prepared = CountDownLatch(1)
            glView.queueEvent {
                val model = requireNotNull(glView.renderer.model)
                model.addObject(model, 0)
                model.scale(1, 0.7, 0.8, 0.55)
                model.translate(1, 43.0, 29.0, 18.0)
                model.setUniformFilament(0, 2)
                model.setUniformFilament(1, 2)
                glView.renderer.setSelectedObjects(intArrayOf(0, 1))
                glView.requestRender()
                prepared.countDown()
            }
            assertTrue("two objects should be prepared for alignment on GL", prepared.await(10, TimeUnit.SECONDS))
            compose.runOnUiThread { bed.updateModel() }
            instrumentationWaitForGlIdle()

            fun bounds() = readExactBoundsOnGl(glView, 0, 1)
            fun centersX(value: List<DoubleArray>) = value.map { (it[0] + it[3]) / 2.0 }
            fun centersY(value: List<DoubleArray>) = value.map { (it[1] + it[4]) / 2.0 }
            fun centersZ(value: List<DoubleArray>) = value.map { (it[2] + it[5]) / 2.0 }
            fun invokeAlignment(label: Int) {
                openSelectedObjectMenu(activity)
                revealMenuAction(activity, R.string.ModelMenuAlignSelected)
                compose.onNodeWithContentDescription(activity.getString(R.string.ModelMenuAlignSelected))
                    .performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
                val option = activity.getString(label)
                // Z options are lower in the dialog's LazyColumn; scroll them into view
                // before asserting/clicking so the test exercises the real scrollable UI.
                compose.onNodeWithText(option).performScrollTo().assertIsDisplayed().performClick()
                compose.waitUntil(timeoutMillis = 10_000) {
                    val current = bounds()
                    when (label) {
                        R.string.ModelMenuAlignCenterX -> kotlin.math.abs(centersX(current)[0] - centersX(current)[1]) < 0.02
                        R.string.ModelMenuAlignCenterY -> kotlin.math.abs(centersY(current)[0] - centersY(current)[1]) < 0.02
                        R.string.ModelMenuAlignMinX -> kotlin.math.abs(current[0][0] - current[1][0]) < 0.02
                        R.string.ModelMenuAlignMaxX -> kotlin.math.abs(current[0][3] - current[1][3]) < 0.02
                        R.string.ModelMenuAlignMinY -> kotlin.math.abs(current[0][1] - current[1][1]) < 0.02
                        R.string.ModelMenuAlignMaxY -> kotlin.math.abs(current[0][4] - current[1][4]) < 0.02
                        R.string.ModelMenuAlignCenterZ -> kotlin.math.abs(centersZ(current)[0] - centersZ(current)[1]) < 0.02
                        R.string.ModelMenuAlignMaxZ -> kotlin.math.abs(current[0][5] - current[1][5]) < 0.02
                        else -> kotlin.math.abs(current[0][2] - current[1][2]) < 0.02
                    }
                }
            }

            val initial = bounds()
            invokeAlignment(R.string.ModelMenuAlignCenterX)
            val afterX = bounds()
            assertEquals("X alignment must only change the secondary object's X center",
                centersX(afterX)[0], centersX(afterX)[1], 0.02)
            assertEquals("X alignment must preserve Y", centersY(initial)[1], centersY(afterX)[1], 0.02)
            assertEquals("X alignment must preserve Z", initial[1][2], afterX[1][2], 0.02)
            compose.runOnUiThread { bed.undoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) { bounds().zip(initial).all { (a, b) -> a.contentEquals(b) } }
            compose.runOnUiThread { bed.redoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) { kotlin.math.abs(centersX(bounds())[0] - centersX(bounds())[1]) < 0.02 }

            val beforeY = bounds()
            invokeAlignment(R.string.ModelMenuAlignCenterY)
            val afterY = bounds()
            assertEquals("Y alignment must preserve X", centersX(beforeY)[1], centersX(afterY)[1], 0.02)
            compose.runOnUiThread { bed.undoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) { kotlin.math.abs(centersY(bounds())[0] - centersY(bounds())[1]) > 0.02 }
            compose.runOnUiThread { bed.redoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) { kotlin.math.abs(centersY(bounds())[0] - centersY(bounds())[1]) < 0.02 }

            val beforeZ = bounds()
            invokeAlignment(R.string.ModelMenuAlignBaseZ)
            val afterZ = bounds()
            assertEquals("Z-base alignment must preserve X", centersX(beforeZ)[1], centersX(afterZ)[1], 0.02)
            assertEquals("Z-base alignment must preserve Y", centersY(beforeZ)[1], centersY(afterZ)[1], 0.02)
            assertEquals("Z-base alignment must align both minimum heights", afterZ[0][2], afterZ[1][2], 0.02)
            compose.runOnUiThread { bed.undoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) { kotlin.math.abs(beforeZ[0][2] - bounds()[0][2]) < 0.02 }
            compose.runOnUiThread { bed.redoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) { kotlin.math.abs(bounds()[0][2] - bounds()[1][2]) < 0.02 }

            val beforeCenterZ = bounds()
            invokeAlignment(R.string.ModelMenuAlignCenterZ)
            val afterCenterZ = bounds()
            assertEquals("Z-center alignment must align both object centers",
                centersZ(afterCenterZ)[0], centersZ(afterCenterZ)[1], 0.02)
            assertEquals("Z-center alignment must preserve X", centersX(beforeCenterZ)[1], centersX(afterCenterZ)[1], 0.02)
            assertEquals("Z-center alignment must preserve Y", centersY(beforeCenterZ)[1], centersY(afterCenterZ)[1], 0.02)
            compose.runOnUiThread { bed.undoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) {
                bounds().zip(beforeCenterZ).all { (a, b) ->
                    a.indices.all { axis -> kotlin.math.abs(a[axis] - b[axis]) < 0.02 }
                }
            }
            compose.runOnUiThread { bed.redoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) {
                kotlin.math.abs(centersZ(bounds())[0] - centersZ(bounds())[1]) < 0.02
            }

            val beforeMaxZ = bounds()
            invokeAlignment(R.string.ModelMenuAlignMaxZ)
            val afterMaxZ = bounds()
            assertEquals("top-edge alignment must align both maximum heights",
                afterMaxZ[0][5], afterMaxZ[1][5], 0.02)
            assertEquals("top-edge alignment must preserve X", centersX(beforeMaxZ)[1], centersX(afterMaxZ)[1], 0.02)
            assertEquals("top-edge alignment must preserve Y", centersY(beforeMaxZ)[1], centersY(afterMaxZ)[1], 0.02)
            compose.runOnUiThread { bed.undoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) {
                bounds().zip(beforeMaxZ).all { (a, b) ->
                    a.indices.all { axis -> kotlin.math.abs(a[axis] - b[axis]) < 0.02 }
                }
            }
            compose.runOnUiThread { bed.redoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) { kotlin.math.abs(bounds()[0][5] - bounds()[1][5]) < 0.02 }

            val edgeAlignments = listOf(
                R.string.ModelMenuAlignMinX to { value: DoubleArray -> value[0] },
                R.string.ModelMenuAlignMaxX to { value: DoubleArray -> value[3] },
                R.string.ModelMenuAlignMinY to { value: DoubleArray -> value[1] },
                R.string.ModelMenuAlignMaxY to { value: DoubleArray -> value[4] },
            )
            for ((label, edge) in edgeAlignments) {
                val before = bounds()
                invokeAlignment(label)
                val after = bounds()
                assertEquals("$label must align the selected objects' corresponding edges",
                    edge(after[0]), edge(after[1]), 0.02)
                val preservedAxis = if (label == R.string.ModelMenuAlignMinX ||
                    label == R.string.ModelMenuAlignMaxX) 1 else 0
                val getPreservedCenter = if (preservedAxis == 0) ::centersX else ::centersY
                assertEquals("$label must leave the other horizontal axis unchanged",
                    getPreservedCenter(before)[1], getPreservedCenter(after)[1], 0.02)
                compose.runOnUiThread { bed.undoModelEdit() }
                compose.waitUntil(timeoutMillis = 10_000) {
                    bounds().zip(before).all { (actual, expected) ->
                        actual.indices.all { axis -> kotlin.math.abs(actual[axis] - expected[axis]) < 0.02 }
                    }
                }
                compose.runOnUiThread { bed.redoModelEdit() }
                compose.waitUntil(timeoutMillis = 10_000) {
                    val redone = bounds()
                    kotlin.math.abs(edge(redone[0]) - edge(redone[1])) < 0.02
                }
            }
            // Restore printable bed contact after checking center/top Z alignments.
            invokeAlignment(R.string.ModelMenuAlignBaseZ)
            assertCurrentWorkspaceModelRoundTripsAndSlices("align-selected-group")
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun modelMenuAlignsAlongRotatedPrimaryObjectAxesWithUndoRedoAndSlice() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val modelFile = File(activity.cacheDir, "menu-primary-axis-align-test.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            loadSingleTestModel(bed, modelFile)
            val prepared = CountDownLatch(1)
            glView.queueEvent {
                val model = requireNotNull(glView.renderer.model)
                model.addObject(model, 0)
                model.rotate(0, 0.0, 0.0, Math.PI / 2.0)
                model.scale(1, 0.7, 0.8, 0.55)
                model.translate(1, 42.0, 37.0, 0.0)
                model.setUniformFilament(0, 2)
                model.setUniformFilament(1, 2)
                glView.renderer.setSelectedObjects(intArrayOf(0, 1))
                glView.requestRender()
                prepared.countDown()
            }
            assertTrue("The rotated multi-object fixture must be prepared on GL", prepared.await(10, TimeUnit.SECONDS))
            compose.runOnUiThread { bed.updateModel() }
            instrumentationWaitForGlIdle()

            fun projectionRanges(): List<DoubleArray> {
                val output = AtomicReference<List<DoubleArray>>()
                val done = CountDownLatch(1)
                glView.queueEvent {
                    val model = requireNotNull(glView.renderer.model)
                    val localX = Vec3d(0.0, 1.0, 0.0) // primary object's X after a +90° Z rotation
                    output.set(listOf(0, 1).map { index ->
                        requireNotNull(model.getProjectionRange(intArrayOf(index), localX))
                    })
                    done.countDown()
                }
                assertTrue("The native projection ranges must be readable on GL", done.await(10, TimeUnit.SECONDS))
                return requireNotNull(output.get())
            }

            val before = projectionRanges()
            val beforeWorldBounds = readExactBoundsOnGl(glView, 0, 1)
            assertTrue("The fixture must start unaligned in the rotated primary X axis",
                kotlin.math.abs((before[0][0] + before[0][1]) - (before[1][0] + before[1][1])) > 1.0)

            openSelectedObjectMenu(activity)
            revealMenuAction(activity, R.string.ModelMenuAlignPrimaryAxes)
            compose.onNodeWithContentDescription(activity.getString(R.string.ModelMenuAlignPrimaryAxes))
                .performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
            val centerX = activity.getString(R.string.ModelMenuAlignCenterX)
            compose.onNodeWithText(centerX).performScrollTo().assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                val current = projectionRanges()
                kotlin.math.abs((current[0][0] + current[0][1]) - (current[1][0] + current[1][1])) < 0.02
            }

            val after = projectionRanges()
            val afterWorldBounds = readExactBoundsOnGl(glView, 0, 1)
            assertEquals("Primary-axis alignment must preserve the secondary object's world X bounds",
                beforeWorldBounds[1][0], afterWorldBounds[1][0], 0.02)
            assertEquals(beforeWorldBounds[1][3], afterWorldBounds[1][3], 0.02)
            assertTrue("A +90° primary rotation must apply local-X alignment along world Y",
                kotlin.math.abs(beforeWorldBounds[1][1] - afterWorldBounds[1][1]) > 1.0)

            compose.runOnUiThread { bed.undoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) {
                projectionRanges().zip(before).all { (actual, expected) ->
                    kotlin.math.abs(actual[0] - expected[0]) < 0.02 && kotlin.math.abs(actual[1] - expected[1]) < 0.02
                }
            }
            compose.runOnUiThread { bed.redoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) {
                val current = projectionRanges()
                kotlin.math.abs((current[0][0] + current[0][1]) - (current[1][0] + current[1][1])) < 0.02
            }
            assertCurrentWorkspaceModelRoundTripsAndSlices("align-primary-object-axes")
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun modelMenuDistributesSelectedGroupOnXYZWithUndoRedoAndWarnsForZ() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val modelFile = File(activity.cacheDir, "menu-group-distribute-test.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            loadSingleTestModel(bed, modelFile)
            val prepared = CountDownLatch(1)
            glView.queueEvent {
                val model = requireNotNull(glView.renderer.model)
                val min = ru.ytkab0bp.slicebeam.utils.Vec3d()
                val max = ru.ytkab0bp.slicebeam.utils.Vec3d()
                model.getBoundingBoxExact(0, min, max)
                val size = doubleArrayOf(max.x - min.x, max.y - min.y, max.z - min.z)
                model.addObject(model, 0)
                model.addObject(model, 0)
                for (index in 0..2) model.setUniformFilament(index, 2)
                model.translate(1, size[0] * 2.0, size[1] * 2.0, size[2] * 2.0)
                model.translate(2, size[0] * 6.0, size[1] * 6.0, size[2] * 6.0)
                glView.renderer.setSelectedObjects(intArrayOf(0, 1, 2))
                glView.requestRender()
                prepared.countDown()
            }
            assertTrue("three objects should be prepared on the GL thread", prepared.await(10, TimeUnit.SECONDS))
            compose.runOnUiThread { bed.updateModel() }
            instrumentationWaitForGlIdle()

            fun bounds() = readExactBoundsOnGl(glView, 0, 1, 2)
            fun start(item: DoubleArray, axis: Int) = item[axis]
            fun end(item: DoubleArray, axis: Int) = item[axis + 3]
            val axes = listOf(
                R.string.ModelMenuDistributeX to 0,
                R.string.ModelMenuDistributeY to 1,
                R.string.ModelMenuDistributeZ to 2,
            )
            for ((label, axis) in axes) {
                val before = bounds()
                openSelectedObjectMenu(activity)
                revealMenuAction(activity, R.string.ModelMenuDistributeSelected)
                compose.onNodeWithContentDescription(activity.getString(R.string.ModelMenuDistributeSelected))
                    .performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
                compose.onNodeWithText(activity.getString(R.string.ModelMenuDistributeHint)).assertIsDisplayed()
                compose.onNodeWithTag("model-choice-dialog-options").performScrollToIndex(axis)
                compose.onNodeWithText(activity.getString(label))
                    .performScrollTo().assertIsDisplayed().performClick()
                compose.waitUntil(timeoutMillis = 10_000) {
                    val current = bounds()
                    val firstGap = start(current[1], axis) - end(current[0], axis)
                    val secondGap = start(current[2], axis) - end(current[1], axis)
                    kotlin.math.abs(firstGap - secondGap) < 0.02 && firstGap >= -0.02
                }
                val after = bounds()
                assertEquals("distribution must preserve first outer object on axis $axis",
                    start(before[0], axis), start(after[0], axis), 0.02)
                assertEquals("distribution must preserve last outer object on axis $axis",
                    end(before[2], axis), end(after[2], axis), 0.02)
                for (index in 0..2) for (otherAxis in 0..2) {
                    if (otherAxis != axis) {
                        assertEquals("distribution on $axis must preserve axis $otherAxis for object $index",
                            before[index][otherAxis], after[index][otherAxis], 0.02)
                    }
                }
                compose.runOnUiThread { bed.undoModelEdit() }
                compose.waitUntil(timeoutMillis = 10_000) {
                    val current = bounds()
                    current.indices.all { index -> (0..5).all { component ->
                        kotlin.math.abs(current[index][component] - before[index][component]) < 0.02
                    } }
                }
                compose.runOnUiThread { bed.redoModelEdit() }
                compose.waitUntil(timeoutMillis = 10_000) {
                    val current = bounds()
                    val firstGap = start(current[1], axis) - end(current[0], axis)
                    val secondGap = start(current[2], axis) - end(current[1], axis)
                    kotlin.math.abs(firstGap - secondGap) < 0.02
                }
            }
            assertCurrentWorkspaceModelRoundTripsAndSlices("distribute-selected-group") { slicedModel ->
                // Z distribution intentionally may leave parts floating. The 3MF position is
                // verified above; place the disposable slice copy on the bed before asking Orca
                // to produce printable G-code, matching the warning shown by the UI.
                for (index in 0 until slicedModel.objectsCount) slicedModel.ensureOnBed(index)
            }
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun editMeasureMenuActionOpensComposeModeAndAndroidBackClosesIt() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val workspaceController = BedFragment::class.java.getDeclaredField("workspaceController")
            .apply { isAccessible = true }.get(bed)

        assertFalse("measurement mode should start disabled", glView.renderer.isInMeasureMode)
        openEditMenu(activity)
        revealMenuAction(activity, R.string.MenuToolbarMeasure)
        compose.onNodeWithContentDescription(activity.getString(R.string.MenuToolbarMeasure))
            .assertIsDisplayed()
            .performClick()

        compose.waitUntil(timeoutMillis = 5_000) {
            workspaceController.javaClass.getMethod("getActiveToolId").invoke(workspaceController) == "measure-mode" &&
                compose.onAllNodesWithText(activity.getString(R.string.MeasureTitle))
                    .fetchSemanticsNodes().isNotEmpty() && glView.renderer.isInMeasureMode
        }
        compose.onNodeWithText(activity.getString(R.string.MeasureDistance)).assertIsDisplayed()
        assertSame("opening a GL tool must retain the workspace renderer view", glView, bed.glView)

        activity.onBackPressedDispatcher.onBackPressed()
        compose.waitUntil(timeoutMillis = 5_000) {
            workspaceController.javaClass.getMethod("getActiveToolId").invoke(workspaceController) == null &&
                !glView.renderer.isInMeasureMode
        }
            assertEquals("Back should leave the user in the Edit destination", WorkspaceDestination.EDIT.ordinal,
            workspaceController.javaClass.getMethod("getSelectedDestinationSlot").invoke(workspaceController))
        assertSame("closing the tool must retain GLView", glView, bed.glView)
        compose.onNodeWithText(activity.getString(R.string.MeasureTitle)).assertDoesNotExist()
    }

    @Test
    fun editRotationActionIsUndoableAndResetChangesNativeModel() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val modelFile = File(activity.cacheDir, "menu-rotation-action-test.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            val loaded = loadSingleTestModel(bed, modelFile)
            assertEquals(1, loaded.objectsCount)
            val glView = requireNotNull(bed.glView)
            glView.selectModel(0, Runnable {})
            instrumentationWaitForGlIdle()
            assertEquals("test object should be selected before rotation", 0, readSelectedObjectOnGl(glView))

            openSelectedObjectMenu(activity)
            revealMenuAction(activity, R.string.MenuToolbarRotate)
            val rotateAction = compose.onNodeWithContentDescription(activity.getString(R.string.MenuToolbarRotate))
            if (!usesCompactPortraitLayout(activity)) rotateAction.performScrollTo()
            val rotateNode = rotateAction.fetchSemanticsNode()
            val rotateVisibility = runCatching { rotateAction.assertIsDisplayed() }.exceptionOrNull()
            assertTrue(
                "Rotate action should be displayed after paging; " +
                    "bounds=${rotateNode.boundsInWindow}, root=${rotateNode.boundsInRoot}, " +
                    "panel=${compose.onNodeWithTag("workspace-menu-panel").fetchSemanticsNode().boundsInWindow}, " +
                    "grid=${compose.onNodeWithTag("workspace-menu-actions-grid").fetchSemanticsNode().boundsInWindow}, " +
                    "semantics=${rotateNode.config}, visibility=$rotateVisibility",
                rotateVisibility == null,
            )
            rotateAction.performClick()
            compose.onNodeWithText("X =", substring = true).assertIsDisplayed().performClick()
            val previewField = compose.onNodeWithText(activity.getString(R.string.OrientationManualValue))
            previewField.performTextClearance()
            previewField.performTextInput("45")
            compose.onNodeWithText(activity.getString(android.R.string.ok)).performClick()
            assertEquals("rotation editing should remain a preview until Apply", 0.0,
                readRotationOnGl(glView, 0).x, 0.002)
            val workspaceController = BedFragment::class.java.getDeclaredField("workspaceController")
                .apply { isAccessible = true }.get(bed)
            assertTrue("Workspace host should consume Back while the rotation tool is active", bed.onBackPressed())
            compose.waitUntil(timeoutMillis = 5_000) {
                workspaceController.javaClass.getMethod("getActiveToolId").invoke(workspaceController) == null
            }
            assertEquals("Back should discard the uncommitted rotation preview", 0.0,
                readRotationOnGl(glView, 0).x, 0.002)
            openSelectedObjectMenu(activity)
            revealMenuAction(activity, R.string.MenuToolbarRotate)
            val applyRotateAction = compose.onNodeWithContentDescription(activity.getString(R.string.MenuToolbarRotate))
            if (!usesCompactPortraitLayout(activity)) applyRotateAction.performScrollTo()
            applyRotateAction.assertIsDisplayed().performClick()
            compose.onNodeWithText("X =", substring = true).assertIsDisplayed().performClick()
            val rotationField = compose.onNodeWithText(activity.getString(R.string.OrientationManualValue))
            rotationField.assertIsDisplayed()
            rotationField.performTextClearance()
            rotationField.performTextInput("90")
            compose.onNodeWithText(activity.getString(android.R.string.ok)).performClick()
            compose.onNodeWithText(activity.getString(R.string.MenuOrientationRotationApply))
                .assertIsDisplayed().performClick()
            val rotated = readRotationOnGl(glView, 0)
            assertEquals("confirmed X rotation should reach the native model", Math.PI / 2, rotated.x, 0.002)

            assertTrue("Back should close the rotation tool before history actions", bed.onBackPressed())
            compose.waitUntil(timeoutMillis = 5_000) {
                workspaceController.javaClass.getMethod("getActiveToolId").invoke(workspaceController) == null
            }
            openEditMenu(activity)
            revealMenuAction(activity, R.string.ModelUndo)
            compose.onNodeWithContentDescription(activity.getString(R.string.ModelUndo))
                .assertIsDisplayed().performClick()
            assertEquals("Undo should restore the original X rotation", 0.0,
                readRotationOnGl(glView, 0).x, 0.002)
            revealMenuAction(activity, R.string.ModelRedo)
            compose.onNodeWithContentDescription(activity.getString(R.string.ModelRedo))
                .assertIsDisplayed().performClick()
            assertEquals("Redo should restore the confirmed X rotation", Math.PI / 2,
                readRotationOnGl(glView, 0).x, 0.002)

            glView.selectModel(0, Runnable {})
            instrumentationWaitForGlIdle()
            assertEquals("test object should remain selected before reset", 0, readSelectedObjectOnGl(glView))
            assertEquals("history actions should leave no active tool", null,
                workspaceController.javaClass.getMethod("getActiveToolId").invoke(workspaceController))
            openSelectedObjectMenu(activity)
            val snackbarControllerField = BedFragment::class.java.getDeclaredField("snackbarController")
                .apply { isAccessible = true }
            val snackbarController = snackbarControllerField.get(bed)
            snackbarController.javaClass.getMethod("clear").invoke(snackbarController)
            compose.waitForIdle()
            revealMenuAction(activity, R.string.ModelContextResetRotation)
            compose.onNodeWithContentDescription(activity.getString(R.string.ModelContextResetRotation))
                .assertIsDisplayed().assertIsEnabled().performClick()
            compose.waitForIdle()
            val snackbarEntries = snackbarController.javaClass.getMethod("getEntries").invoke(snackbarController) as List<*>
            val resetSnackbarShown = snackbarEntries.any { entry ->
                val snackbar = entry?.javaClass?.getMethod("getSnackbar")?.invoke(entry)
                snackbar?.javaClass?.getField("title")?.get(snackbar) ==
                    activity.getString(R.string.ModelContextResetRotationDone)
            }
            assertTrue("Reset Rotation action should execute its completion callback", resetSnackbarShown)
            val afterReset = readRotationOnGl(glView, 0)
            assertEquals("Reset Rotation should clear the native model rotation", 0.0, afterReset.x, 0.002)
            openEditMenu(activity)
            revealMenuAction(activity, R.string.ModelUndo)
            compose.onNodeWithContentDescription(activity.getString(R.string.ModelUndo))
                .assertIsDisplayed().performClick()
            assertEquals("Undo should restore rotation after Reset Rotation", Math.PI / 2,
                readRotationOnGl(glView, 0).x, 0.002)
            revealMenuAction(activity, R.string.ModelRedo)
            compose.onNodeWithContentDescription(activity.getString(R.string.ModelRedo))
                .assertIsDisplayed().performClick()
            assertEquals("Redo should reapply Reset Rotation", 0.0, readRotationOnGl(glView, 0).x, 0.002)
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun rotationEditorRotatesSelectedGroupAsOneAssemblyAndSupportsUndoRedo() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val modelFile = File(activity.cacheDir, "menu-group-rotation-test.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            loadSingleTestModel(bed, modelFile)
            val prepared = CountDownLatch(1)
            glView.queueEvent {
                val model = requireNotNull(glView.renderer.model)
                val min = Vec3d(); val max = Vec3d()
                model.getBoundingBoxExact(0, min, max)
                val spacing = (max.x - min.x) * 2.5
                model.addObject(model, 0)
                model.translate(1, spacing, 0.0, 0.0)
                glView.renderer.setSelectedObjects(intArrayOf(0, 1))
                glView.requestRender()
                prepared.countDown()
            }
            assertTrue("selected group should be prepared on GL", prepared.await(10, TimeUnit.SECONDS))
            compose.runOnUiThread { bed.updateModel() }
            instrumentationWaitForGlIdle()
            val before = readExactBoundsOnGl(glView, 0, 1)
            fun center(bounds: DoubleArray, axis: Int) = (bounds[axis] + bounds[axis + 3]) / 2.0
            val beforeDx = center(before[1], 0) - center(before[0], 0)
            val beforeDy = center(before[1], 1) - center(before[0], 1)

            openSelectedObjectMenu(activity)
            revealMenuAction(activity, R.string.MenuToolbarRotate)
            compose.onNodeWithContentDescription(activity.getString(R.string.MenuToolbarRotate))
                .performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithText("Z =", substring = true).assertIsDisplayed().performClick()
            val angleField = compose.onNodeWithText(activity.getString(R.string.OrientationManualValue))
            angleField.performTextClearance()
            angleField.performTextInput("90")
            compose.onNodeWithText(activity.getString(android.R.string.ok)).performClick()
            compose.onNodeWithText(activity.getString(R.string.MenuOrientationRotationApply))
                .assertIsDisplayed().performClick()

            compose.waitUntil(timeoutMillis = 10_000) {
                kotlin.math.abs(readRotationOnGl(glView, 0).z - Math.PI / 2) < 0.01 &&
                    kotlin.math.abs(readRotationOnGl(glView, 1).z - Math.PI / 2) < 0.01
            }
            val after = readExactBoundsOnGl(glView, 0, 1)
            val afterDx = center(after[1], 0) - center(after[0], 0)
            val afterDy = center(after[1], 1) - center(after[0], 1)
            assertEquals("group rotation should rotate the selected layout around its common pivot", -beforeDy, afterDx, 0.1)
            assertEquals("group rotation should retain spacing as the rotated Y offset", beforeDx, afterDy, 0.1)

            compose.runOnUiThread { bed.undoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) {
                kotlin.math.abs(readRotationOnGl(glView, 0).z) < 0.01 &&
                    kotlin.math.abs(readRotationOnGl(glView, 1).z) < 0.01
            }
            val undone = readExactBoundsOnGl(glView, 0, 1)
            assertEquals("Undo restores the original first-object center", center(before[0], 0), center(undone[0], 0), 0.1)
            assertEquals("Undo restores the original second-object center", center(before[1], 0), center(undone[1], 0), 0.1)
            compose.runOnUiThread { bed.redoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) {
                kotlin.math.abs(readRotationOnGl(glView, 1).z - Math.PI / 2) < 0.01
            }
            val redone = readExactBoundsOnGl(glView, 0, 1)
            assertEquals("Redo restores the rotated group layout", center(after[1], 1) - center(after[0], 1),
                center(redone[1], 1) - center(redone[0], 1), 0.1)
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun scaleToBuildVolumeShrinksCentersAndPlacesSingleObject() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val modelFile = File(activity.cacheDir, "scale-to-build-volume-test.stl")
        try {
            writeScaledCubeStl(activity, modelFile, 20f)
            val model = loadSingleTestModel(bed, modelFile)
            val selected = CountDownLatch(1)
            glView.queueEvent {
                glView.renderer.setSelectedObjects(intArrayOf(0))
                selected.countDown()
            }
            assertTrue("test model selection should be set on GL", selected.await(10, TimeUnit.SECONDS))
            compose.runOnUiThread { bed.updateModel() }
            instrumentationWaitForGlIdle()

            val before = readExactBoundsOnGl(glView, 0).single()
            val bedBounds = readBuildVolumeBoundsOnGl(glView)
            assertTrue("fixture should begin larger than the build volume",
                before[3] - before[0] > bedBounds[3] - bedBounds[0])

            openSelectedObjectMenu(activity)
            revealMenuAction(activity, R.string.ModelScaleToBuildVolume)
            compose.onNodeWithContentDescription(activity.getString(R.string.ModelScaleToBuildVolume))
                .performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()

            compose.waitUntil(timeoutMillis = 15_000) {
                val bounds = readExactBoundsOnGl(glView, 0).single()
                bounds[3] - bounds[0] <= bedBounds[3] - bedBounds[0] + 0.05 &&
                    bounds[4] - bounds[1] <= bedBounds[4] - bedBounds[1] + 0.05 &&
                    bounds[5] - bounds[2] <= bedBounds[5] - bedBounds[2] + 0.05
            }
            val after = readExactBoundsOnGl(glView, 0).single()
            val scaleX = (after[3] - after[0]) / (before[3] - before[0])
            val scaleY = (after[4] - after[1]) / (before[4] - before[1])
            val scaleZ = (after[5] - after[2]) / (before[5] - before[2])
            assertTrue("oversized object should be proportionally shrunk", scaleX < 1.0)
            assertEquals("X and Y should use the same scale factor", scaleX, scaleY, 0.01)
            assertEquals("X and Z should use the same scale factor", scaleX, scaleZ, 0.01)
            assertEquals("object should be centered on X", (bedBounds[0] + bedBounds[3]) / 2.0,
                (after[0] + after[3]) / 2.0, 0.05)
            assertEquals("object should be centered on Y", (bedBounds[1] + bedBounds[4]) / 2.0,
                (after[1] + after[4]) / 2.0, 0.05)
            assertEquals("object should rest at the build-volume minimum Z", bedBounds[2], after[2], 0.05)

            compose.runOnUiThread { bed.undoModelEdit() }
            compose.waitUntil(timeoutMillis = 15_000) {
                val current = readExactBoundsOnGl(glView, 0).single()
                kotlin.math.abs((current[3] - current[0]) - (before[3] - before[0])) < 0.1
            }
            compose.runOnUiThread { bed.redoModelEdit() }
            compose.waitUntil(timeoutMillis = 15_000) {
                val current = readExactBoundsOnGl(glView, 0).single()
                kotlin.math.abs((current[3] - current[0]) - (after[3] - after[0])) < 0.1
            }
            assertEquals("model instance remains present after undo/redo", 1,
                requireNotNull(bed.currentModel).objectsCount)
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun scaleToBuildVolumeFitsSelectedGroupAndPreservesRelativeLayout() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val modelFile = File(activity.cacheDir, "scale-group-to-build-volume-test.stl")
        try {
            writeScaledCubeStl(activity, modelFile, 20f)
            val model = loadSingleTestModel(bed, modelFile)
            val prepared = CountDownLatch(1)
            glView.queueEvent {
                val minimum = Vec3d()
                val maximum = Vec3d()
                model.getBoundingBoxExact(0, minimum, maximum)
                val width = maximum.x - minimum.x
                model.addObject(model, 0)
                model.scale(0, 12.0, 12.0, 12.0)
                model.scale(1, 12.0, 12.0, 12.0)
                model.translate(1, width * 1.8, 0.0, 0.0)
                glView.renderer.setSelectedObject(1)
                glView.renderer.setSelectedObjects(intArrayOf(0, 1))
                glView.requestRender()
                prepared.countDown()
            }
            assertTrue("oversized two-object group should be prepared on GL", prepared.await(10, TimeUnit.SECONDS))
            compose.runOnUiThread { bed.updateModel() }
            instrumentationWaitForGlIdle()
            val before = readExactBoundsOnGl(glView, 0, 1)
            val beforeWidth = before[0][3] - before[0][0]
            val beforeCenterDistance = kotlin.math.abs(
                ((before[1][0] + before[1][3]) - (before[0][0] + before[0][3])) / 2.0,
            )

            openSelectedObjectMenu(activity)
            revealMenuAction(activity, R.string.ModelScaleToBuildVolume)
            compose.onNodeWithContentDescription(activity.getString(R.string.ModelScaleToBuildVolume))
                .performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()

            val bedBounds = readBuildVolumeBoundsOnGl(glView)
            compose.waitUntil(timeoutMillis = 20_000) {
                val bounds = readExactBoundsOnGl(glView, 0, 1)
                val minX = bounds.minOf { it[0] }
                val minY = bounds.minOf { it[1] }
                val minZ = bounds.minOf { it[2] }
                val maxX = bounds.maxOf { it[3] }
                val maxY = bounds.maxOf { it[4] }
                val maxZ = bounds.maxOf { it[5] }
                maxX - minX <= bedBounds[3] - bedBounds[0] + 0.1 &&
                    maxY - minY <= bedBounds[4] - bedBounds[1] + 0.1 &&
                    maxZ - minZ <= bedBounds[5] - bedBounds[2] + 0.1 &&
                    kotlin.math.abs(minZ - bedBounds[2]) <= 0.1
            }

            val after = readExactBoundsOnGl(glView, 0, 1)
            val afterCenterDistance = kotlin.math.abs(
                ((after[1][0] + after[1][3]) - (after[0][0] + after[0][3])) / 2.0,
            )
            val factor = (after[0][3] - after[0][0]) / beforeWidth
            assertTrue("the oversized selection should shrink as one group", factor > 0.0 && factor < 1.0)
            for (index in 0..1) {
                for (axis in 0..2) {
                    val minIndex = axis
                    val maxIndex = axis + 3
                    val beforeSize = before[index][maxIndex] - before[index][minIndex]
                    val afterSize = after[index][maxIndex] - after[index][minIndex]
                    assertEquals("object $index axis $axis should use the group's uniform factor",
                        factor, afterSize / beforeSize, 0.01)
                }
            }
            assertTrue("each selected object should remain a separate object", requireNotNull(bed.currentModel).objectsCount == 2)
            assertEquals("the relative object spacing should scale uniformly", beforeCenterDistance * factor,
                afterCenterDistance, 0.2)
            assertEquals("the whole group should be centered on X", (bedBounds[0] + bedBounds[3]) / 2.0,
                (after.minOf { it[0] } + after.maxOf { it[3] }) / 2.0, 0.1)
            assertEquals("the whole group should be centered on Y", (bedBounds[1] + bedBounds[4]) / 2.0,
                (after.minOf { it[1] } + after.maxOf { it[4] }) / 2.0, 0.1)

            compose.runOnUiThread { bed.undoModelEdit() }
            compose.waitUntil(timeoutMillis = 15_000) {
                val current = readExactBoundsOnGl(glView, 0, 1)
                kotlin.math.abs((current[0][3] - current[0][0]) - beforeWidth) < 0.1
            }
            compose.runOnUiThread { bed.redoModelEdit() }
            compose.waitUntil(timeoutMillis = 15_000) {
                val current = readExactBoundsOnGl(glView, 0, 1)
                kotlin.math.abs((current[0][3] - current[0][0]) - (after[0][3] - after[0][0])) < 0.1
            }
            assertEquals("both selected objects remain present after undo/redo", 2,
                requireNotNull(bed.currentModel).objectsCount)
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun nativeBedRecognizesOffsetCircularBuildVolumeAndExposesMillimeters() {
        val activity = compose.activity
        val glView = requireNotNull((activity.navigationDelegate.getCurrentFragment() as BedFragment).glView)
        val configFile = SliceBeam.getCurrentConfigFile()
        val originalConfig = configFile.readBytes()
        val circularConfigFile = File(activity.cacheDir, "circular-bed-test.ini")
        val bedPointer = AtomicReference<Long>()
        val actualCircle = AtomicReference<DoubleArray?>()
        val isCircular = AtomicReference<Boolean>()
        val nativeFailure = AtomicReference<Throwable?>()
        val nativeResult = CountDownLatch(1)
        try {
            val circleCenterX = 17.0
            val circleCenterY = -11.0
            val radius = 100.0
            val circlePoints = (0 until 72).joinToString(",") { index ->
                val angle = 2.0 * Math.PI * index / 72.0
                String.format(
                    java.util.Locale.US,
                    "%.6fx%.6f",
                    circleCenterX + radius * kotlin.math.cos(angle),
                    circleCenterY + radius * kotlin.math.sin(angle),
                )
            }
            val currentConfigText = String(originalConfig, StandardCharsets.UTF_8)
            val areaRegex = Regex("(?m)^printable_area\\s*=.*$")
            val heightRegex = Regex("(?m)^printable_height\\s*=.*$")
            assertTrue("active config should expose printable_area", areaRegex.containsMatchIn(currentConfigText))
            assertTrue("active config should expose printable_height", heightRegex.containsMatchIn(currentConfigText))
            val circularConfig = heightRegex.replace(
                areaRegex.replace(currentConfigText, "printable_area = $circlePoints"),
                "printable_height = 180",
            )
            circularConfigFile.writeText(circularConfig, StandardCharsets.UTF_8)

            glView.queueEvent {
                val modelPointers = LongArray(3)
                try {
                    bedPointer.set(ru.ytkab0bp.slicebeam.slic3r.Native.bed_create(modelPointers))
                    ru.ytkab0bp.slicebeam.slic3r.Native.bed_configure(
                        requireNotNull(bedPointer.get()), circularConfigFile.absolutePath,
                    )
                    isCircular.set(ru.ytkab0bp.slicebeam.slic3r.Native.bed_is_circular_build_volume(
                        requireNotNull(bedPointer.get()),
                    ))
                    actualCircle.set(ru.ytkab0bp.slicebeam.slic3r.Native.bed_get_circular_build_volume(
                        requireNotNull(bedPointer.get()),
                    ))
                } catch (failure: Throwable) {
                    nativeFailure.set(failure)
                } finally {
                    bedPointer.getAndSet(0L)?.takeIf { it != 0L }?.let {
                        ru.ytkab0bp.slicebeam.slic3r.Native.bed_release(it)
                    }
                    nativeResult.countDown()
                }
            }
            assertTrue("native circular bed configuration should finish on the GL thread",
                nativeResult.await(15, TimeUnit.SECONDS))
            nativeFailure.get()?.let { throw AssertionError("native circle configuration failed", it) }
            assertEquals("native build volume should classify the generated profile as circular", true, isCircular.get())
            assertArrayEquals("native circle center and radius should be returned in millimeters",
                doubleArrayOf(circleCenterX, circleCenterY, radius), requireNotNull(actualCircle.get()), 0.02)
        } finally {
            circularConfigFile.delete()
            configFile.writeBytes(originalConfig)
        }
    }

    @Test
    fun rendererFitsOversizedObjectToOffsetCircularBedAndSupportsUndoRedo() {
        val activity = compose.activity
        val bedFragment = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bedFragment.glView)
        val configFile = SliceBeam.getCurrentConfigFile()
        val circularConfigFile = File(activity.cacheDir, "circular-renderer-fit-test.ini")
        val modelFile = File(activity.cacheDir, "circular-renderer-fit-object.stl")
        val originalConfig = configFile.readBytes()
        var circleBedConfigured = false
        try {
            // Load into the normal profile first, then replace only the active renderer bed.
            // That isolates the fit behavior from importer packing and tests the actual renderer path.
            writeScaledCubeStl(activity, modelFile, 20f)
            loadSingleTestModel(bedFragment, modelFile)
            val before = readExactBoundsOnGl(glView, 0).single()
            assertTrue("fixture must be larger than the circular bed diameter", before[3] - before[0] > 200.0)

            val circleCenterX = 17.0
            val circleCenterY = -11.0
            val radius = 100.0
            val circlePoints = (0 until 72).joinToString(",") { index ->
                val angle = 2.0 * Math.PI * index / 72.0
                String.format(
                    java.util.Locale.US,
                    "%.6fx%.6f",
                    circleCenterX + radius * kotlin.math.cos(angle),
                    circleCenterY + radius * kotlin.math.sin(angle),
                )
            }
            val areaRegex = Regex("(?m)^printable_area\\s*=.*$")
            val heightRegex = Regex("(?m)^printable_height\\s*=.*$")
            val currentConfigText = String(originalConfig, StandardCharsets.UTF_8)
            assertTrue("profile must provide printable_area", areaRegex.containsMatchIn(currentConfigText))
            assertTrue("profile must provide printable_height", heightRegex.containsMatchIn(currentConfigText))
            circularConfigFile.writeText(
                heightRegex.replace(
                    areaRegex.replace(currentConfigText, "printable_area = $circlePoints"),
                    "printable_height = 180",
                ),
                StandardCharsets.UTF_8,
            )

            val editResult = AtomicReference<Int>()
            val editFailure = AtomicReference<Throwable?>()
            val configured = CountDownLatch(1)
            glView.queueEvent {
                try {
                    val renderer = glView.renderer
                    val activeBed = requireNotNull(renderer.bed)
                    activeBed.configure(circularConfigFile)
                    circleBedConfigured = true
                    check(activeBed.supportsScaleToFit()) { "renderer should expose circular scale-to-fit" }
                    val circle = requireNotNull(activeBed.getCircularBuildVolume())
                    assertArrayEquals(
                        "renderer bed should expose the configured circle in millimeters",
                        doubleArrayOf(circleCenterX, circleCenterY, radius), circle, 0.02,
                    )
                    renderer.setSelectedObjects(intArrayOf(0))
                    val selection = requireNotNull(renderer.captureSelectionSnapshot())
                    editResult.set(renderer.scaleSelectedObjectToBuildVolume(selection))
                    if (editResult.get() == 1) bedFragment.updateModelOnGlThread()
                } catch (failure: Throwable) {
                    editResult.set(-99)
                    editFailure.set(failure)
                } finally {
                    configured.countDown()
                }
            }
            assertTrue("renderer fit must finish on the GL thread", configured.await(20, TimeUnit.SECONDS))
            editFailure.get()?.let { throw AssertionError("renderer fit should not throw", it) }
            assertEquals("renderer should fit the oversized object", 1, editResult.get())

            val after = readExactBoundsOnGl(glView, 0).single()
            val centerX = (after[0] + after[3]) / 2.0
            val centerY = (after[1] + after[4]) / 2.0
            val halfX = (after[3] - after[0]) / 2.0
            val halfY = (after[4] - after[1]) / 2.0
            assertEquals("model should be centered on the circular bed X", circleCenterX, centerX, 0.05)
            assertEquals("model should be centered on the circular bed Y", circleCenterY, centerY, 0.05)
            assertTrue(
                "the fitted model's XY bounding-box circumcircle must stay inside the plate",
                kotlin.math.hypot(halfX, halfY) <= radius - 0.02 + 0.05,
            )
            assertTrue("model should stay below the configured circular bed height", after[5] <= 180.0 + 0.05)

            compose.runOnUiThread { bedFragment.undoModelEdit() }
            compose.waitUntil(timeoutMillis = 15_000) {
                val current = readExactBoundsOnGl(glView, 0).single()
                kotlin.math.abs((current[3] - current[0]) - (before[3] - before[0])) < 0.1
            }
            compose.runOnUiThread { bedFragment.redoModelEdit() }
            compose.waitUntil(timeoutMillis = 15_000) {
                val current = readExactBoundsOnGl(glView, 0).single()
                kotlin.math.abs((current[3] - current[0]) - (after[3] - after[0])) < 0.1
            }
        } finally {
            // Restore the active bed profile before returning to other instrumentation tests.
            if (circleBedConfigured) {
                configFile.writeBytes(originalConfig)
                val restored = CountDownLatch(1)
                glView.queueEvent {
                    runCatching { glView.renderer.bed?.configure(configFile) }
                    glView.requestRender()
                    restored.countDown()
                }
                assertTrue("the original renderer bed should be restored", restored.await(20, TimeUnit.SECONDS))
            }
            circularConfigFile.delete()
            modelFile.delete()
        }
    }

    @Test
    fun scaleEditorScalesSelectedGroupGeometryAndSpacingTogetherWithUndoRedo() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val modelFile = File(activity.cacheDir, "menu-group-scale-test.stl")
        val previousLinked = Prefs.isScaleLinked()
        val previousMillimeters = Prefs.isScaleInputInMM()
        Prefs.setScaleLinked(false)
        Prefs.setScaleInputInMM(false)
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            loadSingleTestModel(bed, modelFile)
            val prepared = CountDownLatch(1)
            glView.queueEvent {
                val model = requireNotNull(glView.renderer.model)
                val min = Vec3d(); val max = Vec3d()
                model.getBoundingBoxExact(0, min, max)
                val spacing = (max.x - min.x) * 2.5
                model.addObject(model, 0)
                model.translate(1, spacing, 0.0, 0.0)
                glView.renderer.setSelectedObjects(intArrayOf(0, 1))
                glView.requestRender()
                prepared.countDown()
            }
            assertTrue("selected group should be prepared on GL", prepared.await(10, TimeUnit.SECONDS))
            compose.runOnUiThread { bed.updateModel() }
            instrumentationWaitForGlIdle()
            val before = readExactBoundsOnGl(glView, 0, 1)
            fun width(bounds: DoubleArray) = bounds[3] - bounds[0]
            fun centerX(bounds: DoubleArray) = (bounds[0] + bounds[3]) / 2.0
            val originalWidth = width(before[0])
            val originalSpacing = centerX(before[1]) - centerX(before[0])

            openSelectedObjectMenu(activity)
            revealMenuAction(activity, R.string.MenuTransformScale)
            compose.onNodeWithContentDescription(activity.getString(R.string.MenuTransformScale))
                .performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithContentDescription("X, X =", substring = true).assertIsDisplayed().performClick()
            val scaleField = compose.onNodeWithText(activity.getString(R.string.ScaleManualPercent))
            scaleField.performTextClearance()
            scaleField.performTextInput("150")
            compose.onNodeWithText(activity.getString(android.R.string.ok)).performClick()

            compose.waitUntil(timeoutMillis = 10_000) {
                kotlin.math.abs(width(readExactBoundsOnGl(glView, 0, 1)[0]) - originalWidth * 1.5) < 0.1
            }
            val after = readExactBoundsOnGl(glView, 0, 1)
            assertEquals("each selected object's geometry should scale by the shared X factor",
                originalWidth * 1.5, width(after[0]), 0.1)
            assertEquals("the second selected object's geometry should scale by the same factor",
                originalWidth * 1.5, width(after[1]), 0.1)
            assertEquals("group spacing should scale about the common pivot",
                originalSpacing * 1.5, centerX(after[1]) - centerX(after[0]), 0.1)

            compose.runOnUiThread { bed.undoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) {
                kotlin.math.abs(width(readExactBoundsOnGl(glView, 0, 1)[0]) - originalWidth) < 0.1
            }
            val undone = readExactBoundsOnGl(glView, 0, 1)
            assertEquals("Undo restores both selected object sizes", originalWidth, width(undone[1]), 0.1)
            assertEquals("Undo restores their original spacing", originalSpacing,
                centerX(undone[1]) - centerX(undone[0]), 0.1)
            compose.runOnUiThread { bed.redoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) {
                kotlin.math.abs(width(readExactBoundsOnGl(glView, 0, 1)[1]) - originalWidth * 1.5) < 0.1
            }
        } finally {
            Prefs.setScaleLinked(previousLinked)
            Prefs.setScaleInputInMM(previousMillimeters)
            modelFile.delete()
        }
    }

    @Test
    fun selectedGroupRotationAndScaleSurviveThreeMfReloadAndProduceGcode() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val modelFile = File(activity.cacheDir, "group-transform-slice-test.stl")
        val projectFile = File(activity.cacheDir, "group-transform-slice-test.3mf")
        val gcodeFile = File(activity.cacheDir, "group-transform-slice-test.gcode")
        SliceBeam.INSTANCE = activity.applicationContext as SliceBeam
        Prefs.init(activity.applicationContext as SliceBeam)
        TemporaryProjectSettings.clear()
        val previousConfig = SliceBeam.CONFIG
        val configFile = SliceBeam.getCurrentConfigFile()
        val previousConfigFile = configFile.takeIf { it.isFile }?.readBytes()
        val prefs = requireNotNull(Prefs.getPrefs())
        val snapshotKeys = listOf("mmu_snapshot_printer", "mmu_snapshot")
        val previousSnapshots = snapshotKeys.associateWith { prefs.getString(it, null) }
        var restoredModel: Model? = null
        var sliceResult: GCodeProcessorResult? = null
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            loadSingleTestModel(bed, modelFile)

            val prepared = CountDownLatch(1)
            val prepareFailure = AtomicReference<Throwable?>()
            glView.queueEvent {
                try {
                    val renderer = glView.renderer
                    val model = requireNotNull(renderer.model)
                    val min = Vec3d(); val max = Vec3d()
                    model.getBoundingBoxExact(0, min, max)
                    val gap = (max.x - min.x) * 2.5
                    model.addObject(model, 0)
                    model.translate(1, gap, 0.0, 0.0)
                    model.setUniformFilament(0, 1)
                    model.setUniformFilament(1, 2)
                    renderer.setSelectedObjects(intArrayOf(0, 1))
                    val rotated = renderer.rotateSelectedObjects(0.0, 0.0, Math.toRadians(37.0))
                    val scaled = rotated && renderer.scaleSelectedObjects(1.2, 1.2, 1.2)
                    check(scaled) { "selected group rotation and scale should apply" }
                    glView.requestRender()
                } catch (error: Throwable) {
                    prepareFailure.set(error)
                } finally {
                    prepared.countDown()
                }
            }
            assertTrue("group transform should complete on the GL thread", prepared.await(30, TimeUnit.SECONDS))
            prepareFailure.get()?.let { throw AssertionError("group transform failed", it) }
            compose.runOnUiThread { bed.updateModel() }
            instrumentationWaitForGlIdle()

            val exportReady = CountDownLatch(1)
            val exportFailure = AtomicReference<Throwable?>()
            glView.queueEvent {
                try {
                    val model = requireNotNull(glView.renderer.model)
                    assertEquals(2, model.objectsCount)
                    assertEquals(listOf(1), model.getUsedFilaments(0).toList())
                    assertEquals(listOf(2), model.getUsedFilaments(1).toList())
                    for (index in 0 until model.objectsCount) {
                        val min = Vec3d(); val max = Vec3d()
                        model.getBoundingBoxExact(index, min, max)
                        assertTrue("transformed object must have finite printable bounds",
                            listOf(min.x, min.y, min.z, max.x, max.y, max.z).all { it.isFinite() } && max.z > min.z)
                    }
                    activity.assets.open("orca_profiles/Anycubic.ini").use {
                        SliceBeam.CONFIG = Slic3rConfigWrapper(it)
                    }
                    SliceBeam.requireConfig().presets = ConfigObject("presets").apply {
                        put("printer", "Anycubic Kobra 3 0.4 nozzle")
                        put("print", "0.16mm Standard")
                        put("filament", "Anycubic Generic PLA")
                    }
                    val clearSnapshots = prefs.edit()
                    snapshotKeys.forEach(clearSnapshots::remove)
                    check(clearSnapshots.commit())
                    SliceBeam.genCurrentConfig(model)
                    model.export3mf(configFile.absolutePath, projectFile.absolutePath)
                } catch (error: Throwable) {
                    exportFailure.set(error)
                } finally {
                    exportReady.countDown()
                }
            }
            assertTrue("transformed group should export from the active renderer model",
                exportReady.await(60, TimeUnit.SECONDS))
            exportFailure.get()?.let { throw AssertionError("group transformation 3MF export failed", it) }
            assertTrue("3MF export should create a project", projectFile.isFile && projectFile.length() > 0L)

            restoredModel = Model(projectFile, 1)
            assertEquals("both transformed objects should survive 3MF reload", 2, restoredModel!!.objectsCount)
            assertEquals("first object's filament assignment should survive reload", listOf(1),
                restoredModel!!.getUsedFilaments(0).toList())
            assertEquals("second object's filament assignment should survive reload", listOf(2),
                restoredModel!!.getUsedFilaments(1).toList())
            val firstBounds = DoubleArray(6).also { bounds ->
                val min = Vec3d(); val max = Vec3d()
                restoredModel!!.getBoundingBoxExact(0, min, max)
                bounds[0] = min.x; bounds[1] = min.y; bounds[2] = min.z
                bounds[3] = max.x; bounds[4] = max.y; bounds[5] = max.z
            }
            assertTrue("reloaded group object should remain above the build plate", firstBounds[2] >= -0.05)
            SliceBeam.genCurrentConfig(restoredModel!!)
            sliceResult = restoredModel!!.slice(configFile.absolutePath, gcodeFile.absolutePath, null)
            assertTrue("reloaded transformed group should produce non-empty G-code",
                gcodeFile.isFile && gcodeFile.length() > 0L)
            val gcode = gcodeFile.readText()
            assertTrue("reloaded transformed group should produce deposited extrusion",
                Regex("(?m)^G1 .*\\bE[0-9.-]+").containsMatchIn(gcode))
            assertTrue("both object tool assignments should reach G-code",
                Regex("(?m)^T0(?:\\s|$)").containsMatchIn(gcode) &&
                    Regex("(?m)^T1(?:\\s|$)").containsMatchIn(gcode))
        } finally {
            sliceResult?.release()
            restoredModel?.release()
            gcodeFile.delete()
            projectFile.delete()
            val restoreSnapshots = prefs.edit()
            previousSnapshots.forEach { (key, value) ->
                if (value == null) restoreSnapshots.remove(key) else restoreSnapshots.putString(key, value)
            }
            restoreSnapshots.commit()
            SliceBeam.CONFIG = previousConfig
            if (previousConfigFile == null) configFile.delete() else configFile.writeBytes(previousConfigFile)
            TemporaryProjectSettings.clear()
            modelFile.delete()
        }
    }

    @Test
    fun editCutAndVariableLayerActionsOpenAndCancelWithoutChangingModel() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val modelFile = File(activity.cacheDir, "menu-cut-action-test.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            val loaded = loadSingleTestModel(bed, modelFile)
            assertEquals(1, loaded.objectsCount)
            val glView = requireNotNull(bed.glView)
            glView.selectModel(0, Runnable {})
            instrumentationWaitForGlIdle()

            openSelectedObjectMenu(activity)
            revealMenuAction(activity, R.string.MenuTransformCut)
            compose.onNodeWithContentDescription(activity.getString(R.string.MenuTransformCut))
                .performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithText(activity.getString(R.string.CutApply))
                .performScrollTo().assertIsDisplayed().assertIsEnabled()
            val workspaceController = BedFragment::class.java.getDeclaredField("workspaceController")
                .apply { isAccessible = true }.get(bed)
            assertEquals("Cut should be the active workspace tool",
                "ru.ytkab0bp.slicebeam.components.bed_menu.TransformCutMenu",
                workspaceController.javaClass.getMethod("getActiveToolId").invoke(workspaceController))
            assertEquals("opening Cut must not mutate the model", 1, readObjectCountOnGl(glView))
            assertTrue("Cut preview should be visible while the tool is open", readRendererBooleanOnGl(glView, "cutPlaneVisible"))

            assertTrue("Back should cancel and close the Cut tool", bed.onBackPressed())
            compose.waitUntil(timeoutMillis = 5_000) {
                workspaceController.javaClass.getMethod("getActiveToolId").invoke(workspaceController) == null
            }
            assertFalse("Back should hide the uncommitted cut preview", readRendererBooleanOnGl(glView, "cutPlaneVisible"))
            assertEquals("cancelling Cut must preserve the object", 1, readObjectCountOnGl(glView))

            openSelectedObjectMenu(activity)
            revealMenuAction(activity, R.string.MenuToolbarVariableLayerHeight)
            compose.onNodeWithContentDescription(activity.getString(R.string.MenuToolbarVariableLayerHeight))
                .performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithText(activity.getString(R.string.VariableLayerTitle)).assertIsDisplayed()
            assertTrue("Variable Layer action should activate its workspace mode", bed.isVariableLayerHeightMode)
            compose.onNodeWithText(activity.getString(R.string.ActionDone)).assertIsDisplayed().performClick()
            compose.waitForIdle()
            assertFalse("Done should close Variable Layer mode", bed.isVariableLayerHeightMode)

            glView.selectModel(0, Runnable {})
            instrumentationWaitForGlIdle()
            openSelectedObjectMenu(activity)
            revealMenuAction(activity, R.string.ModelMenuSplit)
            compose.onNodeWithContentDescription(activity.getString(R.string.ModelMenuSplit))
                .performScrollTo().assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 5_000) {
                hasSnackbarTitle(R.string.MenuToolbarSplitNothing)
            }
            assertEquals("Split on a single connected test mesh should preserve its object", 1,
                readObjectCountOnGl(glView))
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun adaptiveLayerHeightApplyAndClearSupportUndoRedo() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val modelFile = File(activity.cacheDir, "menu-adaptive-layer-height-test.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            loadSingleTestModel(bed, modelFile)
            val glView = requireNotNull(bed.glView)
            glView.selectModel(0, Runnable {})
            instrumentationWaitForGlIdle()
            assertFalse("the fixture should begin with constant layer height",
                readAdaptiveLayerHeightOnGl(glView, 0))

            openSelectedObjectMenu(activity)
            revealMenuAction(activity, R.string.MenuToolbarVariableLayerHeight)
            compose.onNodeWithContentDescription(activity.getString(R.string.MenuToolbarVariableLayerHeight))
                .performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithText(activity.getString(R.string.VariableLayerTitle)).assertIsDisplayed()
            compose.onNodeWithText(activity.getString(R.string.VariableLayerAdaptive))
                .assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 30_000) { readAdaptiveLayerHeightOnGl(glView, 0) }

            compose.runOnUiThread { bed.undoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) { !readAdaptiveLayerHeightOnGl(glView, 0) }
            compose.runOnUiThread { bed.redoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) { readAdaptiveLayerHeightOnGl(glView, 0) }

            compose.onNodeWithText(activity.getString(R.string.ActionClear))
                .assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 10_000) { !readAdaptiveLayerHeightOnGl(glView, 0) }
            compose.runOnUiThread { bed.undoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) { readAdaptiveLayerHeightOnGl(glView, 0) }
            compose.runOnUiThread { bed.redoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) { !readAdaptiveLayerHeightOnGl(glView, 0) }
            assertSame("adaptive profile edits should retain the renderer host", glView, bed.glView)
        } finally {
            if (bed.isVariableLayerHeightMode) bed.onBackPressed()
            modelFile.delete()
        }
    }

    @Test
    fun applyingCutRunsOnGlThreadAndSplitsTheSelectedModel() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val modelFile = File(activity.cacheDir, "menu-cut-apply-test.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            val model = loadSingleTestModel(bed, modelFile)
            assertEquals(1, model.objectsCount)
            val glView = requireNotNull(bed.glView)
            glView.queueEvent {
                requireNotNull(glView.renderer.model).setUniformFilament(0, 2)
                glView.requestRender()
            }
            instrumentationWaitForGlIdle()
            compose.runOnUiThread { bed.updateModel() }
            glView.selectModel(0, Runnable {})
            instrumentationWaitForGlIdle()

            openSelectedObjectMenu(activity)
            revealMenuAction(activity, R.string.MenuTransformCut)
            compose.onNodeWithContentDescription(activity.getString(R.string.MenuTransformCut))
                .performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithText(activity.getString(R.string.CutApply))
                .performScrollTo().assertIsDisplayed().performClick()

            compose.waitUntil(timeoutMillis = 30_000) {
                readObjectCountOnGl(glView) == 2 &&
                    !readRendererBooleanOnGl(glView, "cutPlaneVisible")
            }
            assertEquals("applying a cut should split the model into two native objects", 2,
                readObjectCountOnGl(glView))
            assertFalse("the cut preview should close after applying",
                readRendererBooleanOnGl(glView, "cutPlaneVisible"))

            compose.runOnUiThread { bed.undoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) { readObjectCountOnGl(glView) == 1 }
            compose.runOnUiThread { bed.redoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) { readObjectCountOnGl(glView) == 2 }
            assertArrayEquals("cut half 0 should retain the source material", intArrayOf(2),
                readUsedFilamentsOnGl(glView, 0))
            assertArrayEquals("cut half 1 should retain the source material", intArrayOf(2),
                readUsedFilamentsOnGl(glView, 1))
            assertCurrentWorkspaceModelRoundTripsAndSlices("cut") { cutModel ->
                // The upper cut half is intentionally floating at this horizontal cut plane.
                // Keep the lower, bed-contacting half for a valid G-code check after verifying
                // that both halves survived Undo/Redo and the 3MF round trip above.
                fun lowerZ(index: Int): Double {
                    val min = Vec3d(); val max = Vec3d()
                    cutModel.getBoundingBoxExact(index, min, max)
                    return min.z
                }
                val lowerHalf = if (lowerZ(0) <= lowerZ(1)) 0 else 1
                cutModel.deleteObject(1 - lowerHalf)
            }
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun cutConnectorTapCreatesDiscardablePreviewWithoutMutatingModel() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val modelFile = File(activity.cacheDir, "menu-cut-connector-cancel-test.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            val loaded = loadSingleTestModel(bed, modelFile)
            assertEquals(1, loaded.objectsCount)
            val glView = requireNotNull(bed.glView)
            glView.selectModel(0, Runnable {})
            instrumentationWaitForGlIdle()

            openSelectedObjectMenu(activity)
            revealMenuAction(activity, R.string.MenuTransformCut)
            compose.onNodeWithContentDescription(activity.getString(R.string.MenuTransformCut))
                .performScrollTo().assertIsDisplayed().performClick()
            val cutReady = runCatching {
                compose.waitUntil(timeoutMillis = 10_000) {
                    runCatching {
                        compose.onNodeWithText(activity.getString(R.string.CutApply)).assertIsEnabled()
                    }.isSuccess
                }
            }.isSuccess
            if (!cutReady) {
                val menus = BedFragment::class.java.getDeclaredField("menuMap")
                    .apply { isAccessible = true }.get(bed) as android.util.SparseArray<*>
                val cutMenu = (0 until menus.size()).mapNotNull(menus::valueAt)
                    .firstOrNull { it.javaClass.simpleName == "TransformCutMenu" }
                val cutObject = cutMenu?.javaClass?.getDeclaredField("cutObject")
                    ?.apply { isAccessible = true }?.getInt(cutMenu)
                val ready = cutMenu?.javaClass?.getDeclaredField("cutReady")
                    ?.apply { isAccessible = true }?.get(cutMenu)
                    ?.let { it.javaClass.getMethod("getValue").invoke(it) }
                throw AssertionError(
                    "Cut tool did not become ready; menu=${cutMenu?.javaClass?.name}, " +
                        "cutObject=$cutObject, cutReady=$ready, " +
                        "rendererSelected=${readRendererIntOnGl(glView, "selectedObject")}, " +
                        "modelObjects=${readObjectCountOnGl(glView)}",
                )
            }
            assertTrue("the GL viewport must stay measurable while the action menu is open", glView.height > 0)
            compose.onNodeWithText(activity.getString(R.string.CutAddConnector))
                .performScrollTo().assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                readRendererBooleanOnGl(glView, "cutConnectorPlacement")
            }

            val connectorPoint = findUncoveredPreciseSurfacePoint(glView, bed)
            tapGlViewAt(glView, connectorPoint.first, connectorPoint.second)
            compose.waitUntil(timeoutMillis = 10_000) {
                readCutConnectorCountOnGl(glView, 0) == 1 &&
                    compose.onAllNodesWithText(activity.getString(R.string.CutConnectorCount, 1))
                        .fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals("placing a cut connector should only create a preview", 1,
                readObjectCountOnGl(glView))

            assertTrue("Back should close the cut tool", bed.onBackPressed())
            compose.waitUntil(timeoutMillis = 10_000) {
                !readRendererBooleanOnGl(glView, "cutPlaneVisible") &&
                    readCutConnectorCountOnGl(glView, 0) == 0
            }
            assertEquals("cancelling the connector preview must preserve the model", 1,
                readObjectCountOnGl(glView))
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun cutConnectorEditingStaysBoundToItsObjectAndRejectsStaleCut() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val modelFile = File(activity.cacheDir, "menu-cut-connector-selection-binding.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            val model = loadSingleTestModel(bed, modelFile)
            val prepared = CountDownLatch(1)
            glView.queueEvent {
                model.addObject(model, 0)
                model.translate(1, 30.0, 0.0, 0.0)
                glView.renderer.setSelectedObject(0)
                glView.requestRender()
                prepared.countDown()
            }
            assertTrue("two cut targets should be prepared on GL", prepared.await(10, TimeUnit.SECONDS))
            compose.runOnUiThread { bed.updateModel() }
            instrumentationWaitForGlIdle()
            val originalTriangles = intArrayOf(
                readTriangleCountOnGl(glView, 0),
                readTriangleCountOnGl(glView, 1),
            )

            openSelectedObjectMenu(activity)
            revealMenuAction(activity, R.string.MenuTransformCut)
            compose.onNodeWithContentDescription(activity.getString(R.string.MenuTransformCut))
                .performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithText(activity.getString(R.string.CutAddConnector))
                .performScrollTo().assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                readRendererBooleanOnGl(glView, "cutConnectorPlacement")
            }

            val connectorPoint = findUncoveredPreciseSurfacePoint(glView, bed)
            tapGlViewAt(glView, connectorPoint.first, connectorPoint.second)
            compose.waitUntil(timeoutMillis = 10_000) {
                readCutConnectorCountOnGl(glView, 0) == 1 &&
                    compose.onAllNodesWithText(activity.getString(R.string.CutConnectorCount, 1))
                        .fetchSemanticsNodes().isNotEmpty()
            }

            // Exercise Undo before checking that later selection changes cannot redirect the preview.
            compose.onNodeWithText(activity.getString(R.string.CutUndoConnector))
                .performScrollTo().assertIsEnabled().performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                readCutConnectorCountOnGl(glView, 0) == 0
            }

            // Even if the active selection changes during background connector placement,
            // connector previews remain attached to the object whose plane is being edited.
            glView.selectModel(1, Runnable {})
            instrumentationWaitForGlIdle()
            tapGlViewAt(glView, connectorPoint.first, connectorPoint.second)
            compose.waitUntil(timeoutMillis = 10_000) {
                readCutConnectorCountOnGl(glView, 0) == 1 &&
                    compose.onAllNodesWithText(activity.getString(R.string.CutConnectorCount, 1))
                        .fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals("selection changes must not redirect connectors to another object", 0,
                readCutConnectorCountOnGl(glView, 1))

            compose.onNodeWithText(activity.getString(R.string.CutApply))
                .performScrollTo().assertIsEnabled().performClick()
            val workspaceController = BedFragment::class.java.getDeclaredField("workspaceController")
                .apply { isAccessible = true }.get(bed)
            compose.waitUntil(timeoutMillis = 10_000) {
                workspaceController.javaClass.getMethod("getActiveToolId").invoke(workspaceController) == null &&
                    !readRendererBooleanOnGl(glView, "cutPlaneVisible")
            }
            assertEquals("a stale cut must preserve both source objects", 2, readObjectCountOnGl(glView))
            assertEquals("the changed selection should remain active", 1, readSelectedObjectOnGl(glView))
            assertEquals("a stale cut must not alter source mesh 0", originalTriangles[0],
                readTriangleCountOnGl(glView, 0))
            assertEquals("a stale cut must not alter source mesh 1", originalTriangles[1],
                readTriangleCountOnGl(glView, 1))
            assertEquals("connector previews must be discarded with the stale operation", 0,
                readCutConnectorCountOnGl(glView, 0))
            assertEquals("no connector may leak onto the newly selected model", 0,
                readCutConnectorCountOnGl(glView, 1))
            assertEquals("preview connectors must never be committed to the model", 0,
                model.getCutConnectorCount(0) + model.getCutConnectorCount(1))
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun editSplitActionSeparatesDisconnectedMeshComponentsIntoNativeObjects() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val modelFile = File(activity.cacheDir, "menu-split-disconnected-mesh-${System.nanoTime()}.stl")
        try {
            writeTwoDisconnectedCubesStl(activity, modelFile)
            val loaded = loadSingleTestModel(bed, modelFile)
            assertEquals("the importer should keep the compound STL as one selectable model", 1,
                loaded.objectsCount)
            val importedTriangleCount = loaded.getTriangleCount(0)
            assertTrue("both components should contain valid mesh triangles",
                importedTriangleCount > 0 && importedTriangleCount % 2 == 0)
            val trianglesPerComponent = importedTriangleCount / 2

            val glView = requireNotNull(bed.glView)
            glView.queueEvent {
                requireNotNull(glView.renderer.model).setUniformFilament(0, 2)
                glView.requestRender()
            }
            instrumentationWaitForGlIdle()
            compose.runOnUiThread { bed.updateModel() }
            glView.selectModel(0, Runnable {})
            instrumentationWaitForGlIdle()
            openSelectedObjectMenu(activity)
            revealMenuAction(activity, R.string.ModelMenuSplit)
            compose.onNodeWithContentDescription(activity.getString(R.string.ModelMenuSplit))
                .performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()

            compose.waitUntil(timeoutMillis = 30_000) { readObjectCountOnGl(glView) == 2 }
            assertEquals("native split should create exactly two independent model objects", 2,
                readObjectCountOnGl(glView))
            assertEquals("first split object should preserve its triangles", trianglesPerComponent,
                readTriangleCountOnGl(glView, 0))
            assertEquals("second split object should preserve its triangles", trianglesPerComponent,
                readTriangleCountOnGl(glView, 1))

            compose.runOnUiThread { bed.undoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) { readObjectCountOnGl(glView) == 1 }
            assertEquals("undo should restore the original compound mesh", importedTriangleCount,
                readTriangleCountOnGl(glView, 0))
            compose.runOnUiThread { bed.redoModelEdit() }
            compose.waitUntil(timeoutMillis = 10_000) { readObjectCountOnGl(glView) == 2 }
            assertEquals("redo should restore both disconnected components", trianglesPerComponent,
                readTriangleCountOnGl(glView, 1))
            for (index in 0 until 2) {
                assertArrayEquals("split component $index should keep the original material", intArrayOf(2),
                    readUsedFilamentsOnGl(glView, index))
            }
            assertCurrentWorkspaceModelRoundTripsAndSlices("split-disconnected")
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun groupedDisconnectedImportArrangesAnotherModelWithoutBreakingTheGroup() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val compoundFile = File(activity.cacheDir, "arrange-grouped-islands-${System.nanoTime()}.stl")
        val insertFile = File(activity.cacheDir, "arrange-grouped-islands-insert-${System.nanoTime()}.stl")
        var insertSource: Model? = null
        try {
            // One STL/object containing two printable solids with a real open gap between them.
            writeTwoDisconnectedCubesStl(activity, compoundFile, separationMm = 50f, scaleY = 1.8f)
            writeScaledCubeStl(activity, insertFile, 0.1f)
            val grouped = loadSingleTestModel(bed, compoundFile)
            assertEquals("disconnected STL islands should remain one logical model", 1, grouped.objectsCount)
            insertSource = Model(insertFile)
            val glView = requireNotNull(bed.glView)
            val arranged = AtomicReference<Boolean>()
            val groupBounds = DoubleArray(6)
            val insertBounds = DoubleArray(6)
            val plateBounds = DoubleArray(4)
            val complete = CountDownLatch(1)
            glView.queueEvent {
                try {
                    val model = requireNotNull(glView.renderer.model)
                    model.addObject(requireNotNull(insertSource), 0)
                    arranged.set(requireNotNull(glView.renderer.bed).arrange(model))
                    if (arranged.get() == true) {
                        val plate = requireNotNull(glView.renderer.bed)
                        val plateMin = requireNotNull(plate.getVolumeMin())
                        val plateMax = requireNotNull(plate.getVolumeMax())
                        plateBounds[0] = plateMin.x; plateBounds[1] = plateMin.y
                        plateBounds[2] = plateMax.x; plateBounds[3] = plateMax.y
                        val min = Vec3d(); val max = Vec3d()
                        model.getBoundingBoxExact(0, min, max)
                        groupBounds[0] = min.x; groupBounds[1] = min.y
                        groupBounds[2] = max.x; groupBounds[3] = max.y
                        model.getBoundingBoxExact(1, min, max)
                        insertBounds[0] = min.x; insertBounds[1] = min.y
                        insertBounds[2] = max.x; insertBounds[3] = max.y
                    }
                    model.resetBoundingBox()
                    glView.requestRender()
                } finally {
                    complete.countDown()
                }
            }
            assertTrue("grouped-island arrangement should complete on GL", complete.await(15, TimeUnit.SECONDS))
            assertEquals("the grouped model and a separate insert should fit on one plate", true, arranged.get())
            val insertCenterX = (insertBounds[0] + insertBounds[2]) / 2.0
            val groupCenterX = (groupBounds[0] + groupBounds[2]) / 2.0
            val insertCenterY = (insertBounds[1] + insertBounds[3]) / 2.0
            assertTrue("the insert should use the open gap inside the grouped model's bounds; " +
                "group=${groupBounds.contentToString()}, insert=${insertBounds.contentToString()}",
                kotlin.math.abs(insertCenterX - groupCenterX) < 10.0 &&
                    insertCenterY >= groupBounds[1] - 0.1 && insertCenterY <= groupBounds[3] + 0.1)
            assertTrue("both arranged models should remain inside the active plate",
                groupBounds[0] >= plateBounds[0] - 0.1 && groupBounds[1] >= plateBounds[1] - 0.1 &&
                    groupBounds[2] <= plateBounds[2] + 0.1 && groupBounds[3] <= plateBounds[3] + 0.1 &&
                    insertBounds[0] >= plateBounds[0] - 0.1 && insertBounds[1] >= plateBounds[1] - 0.1 &&
                    insertBounds[2] <= plateBounds[2] + 0.1 && insertBounds[3] <= plateBounds[3] + 0.1)
        } finally {
            compoundFile.delete()
            insertFile.delete()
            insertSource?.release()
        }
    }

    @Test
    fun editEmbossActionEntersComposeGlModeAndBackExitsWithoutMeshMutation() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val modelFile = File(activity.cacheDir, "menu-emboss-action-test.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            val loaded = loadSingleTestModel(bed, modelFile)
            assertEquals(1, loaded.objectsCount)
            val glView = requireNotNull(bed.glView)
            glView.selectModel(0, Runnable {})
            instrumentationWaitForGlIdle()
            val trianglesBefore = readTriangleCountOnGl(glView, 0)

            openSelectedObjectMenu(activity)
            revealMenuAction(activity, R.string.MenuToolbarEmboss)
            compose.onNodeWithContentDescription(activity.getString(R.string.MenuToolbarEmboss))
                .performScrollTo().assertIsDisplayed().performClick()
            val workspaceController = BedFragment::class.java.getDeclaredField("workspaceController")
                .apply { isAccessible = true }.get(bed)
            assertEquals("Emboss should be the active workspace tool", "emboss-mode",
                workspaceController.javaClass.getMethod("getActiveToolId").invoke(workspaceController))
            assertTrue("The active GL tool must publish its Compose panel content",
                workspaceController.javaClass.getMethod("getActiveToolPanelContent").invoke(workspaceController) != null)
            assertTrue("Opening the menu action should activate renderer emboss mode",
                readRendererBooleanOnGl(glView, "isInEmbossMode"))
            assertEquals("Entering emboss mode must not mutate the mesh", trianglesBefore,
                readTriangleCountOnGl(glView, 0))

            assertTrue("Android Back should close the active emboss tool", bed.onBackPressed())
            compose.waitUntil(timeoutMillis = 5_000) {
                workspaceController.javaClass.getMethod("getActiveToolId").invoke(workspaceController) == null
            }
            assertFalse("Back must leave renderer emboss mode", readRendererBooleanOnGl(glView, "isInEmbossMode"))
            assertFalse("Back must clear the active emboss tool", bed.isEmbossMode)
            assertEquals("Cancelling the emboss panel must preserve the mesh", trianglesBefore,
                readTriangleCountOnGl(glView, 0))
            assertTrue("Closing the tool must retain the existing GLView", bed.glView === glView)
        } finally {
            if (bed.isEmbossMode) bed.onBackPressed()
            modelFile.delete()
        }
    }

    @Test
    fun modelMenuCanCreateStandaloneTextDirectlyOnTheBuildPlate() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val previousSlots = Prefs.getStoredFilamentSlots().map { FilamentSlot(it.color, it.type) }
        Prefs.setStoredFilamentSlots(listOf(
            FilamentSlot(0xFF222222.toInt(), "Text Black"),
            FilamentSlot(0xFF3366CC.toInt(), "Text Blue"),
        ))
        try {
        assertEquals("the test starts with an empty build plate", 0, readObjectCountOnGl(glView))

        openModelMenu(activity)
        selectAllObjectsScope()
        val createTextAction = compose.onNodeWithContentDescription(activity.getString(R.string.TextModelCreateSeparate))
            .performScrollTo().assertIsDisplayed().assertIsEnabled().fetchSemanticsNode()
        val actionCenter = createTextAction.boundsInRoot.center
        val workspaceRoot = requireNotNull(bed.getView())
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN,
            actionCenter.x, actionCenter.y, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
        val up = MotionEvent.obtain(downTime, downTime + 80L, MotionEvent.ACTION_UP,
            actionCenter.x, actionCenter.y, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
        try {
            compose.runOnUiThread {
                workspaceRoot.dispatchTouchEvent(down)
                workspaceRoot.dispatchTouchEvent(up)
            }
        } finally {
            down.recycle()
            up.recycle()
        }
        assertTrue("standalone text placement must activate emboss click handling",
            readRendererBooleanOnGl(glView, "isInEmbossMode"))
        assertTrue("standalone placement must allow bed hits without selecting a model",
            readRendererBooleanOnGl(glView, "isStandaloneTextPlacementMode"))
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("emboss-text-filament-2").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("emboss-text-filament-2").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("emboss-text-filament-2").assertIsSelected()

        val hit = AtomicReference<FloatArray>()
        val hitReady = CountDownLatch(1)
        glView.queueEvent {
            val renderer = glView.renderer
            val raycastBed = GLRenderer::class.java.getDeclaredMethod(
                "raycastBed", Float::class.javaPrimitiveType, Float::class.javaPrimitiveType,
            ).apply { isAccessible = true }
            val xFractions = floatArrayOf(.5f, .35f, .65f, .2f, .8f)
            val yFractions = floatArrayOf(.5f, .35f, .65f, .2f, .8f)
            for (yf in yFractions) {
                for (xf in xFractions) {
                    val x = renderer.viewportWidth * xf
                    val y = renderer.viewportHeight * yf
                    if (raycastBed.invoke(renderer, x, y) != null) {
                        hit.set(floatArrayOf(x, y))
                        break
                    }
                }
                if (hit.get() != null) break
            }
            hitReady.countDown()
        }
        assertTrue("renderer should find a visible point on the empty build plate", hitReady.await(10, TimeUnit.SECONDS))
        val bedPoint = requireNotNull(hit.get())
        val clickHandled = AtomicReference<Boolean>()
        val clickReady = CountDownLatch(1)
        glView.queueEvent {
            clickHandled.set(glView.renderer.onClick(bedPoint[0], bedPoint[1]))
            clickReady.countDown()
        }
        assertTrue("GL click should finish", clickReady.await(10, TimeUnit.SECONDS))
        assertTrue("build-plate click should be consumed by standalone placement", requireNotNull(clickHandled.get()))
        val textCreationDeadline = SystemClock.uptimeMillis() + 30_000L
        var createdObjectCount = readObjectCountOnGl(glView)
        while (createdObjectCount != 1 && SystemClock.uptimeMillis() < textCreationDeadline) {
            // Avoid Compose idle synchronization while the GL renderer continues to draw.
            SystemClock.sleep(25L)
            createdObjectCount = readObjectCountOnGl(glView)
        }
        assertEquals("clicking the plate should create one independent printable object", 1,
            createdObjectCount)
        assertTrue("generated text must be real printable geometry", readTriangleCountOnGl(glView, 0) > 0)
        assertEquals("created text should become selected", 0, glView.renderer.selectedObject)
        val textTriangleCount = readTriangleCountOnGl(glView, 0)
        val textBounds = readExactBoundsOnGl(glView, 0).single()

        // Exercise the complete user path: standalone text created from the Model menu must
        // retain its material through 3MF export/reload and remain printable by the slicer.
        assertCurrentWorkspaceModelRoundTripsAndSlices(
            name = "standalone-text-menu",
            expectedObjectFilaments = listOf(intArrayOf(2)),
            allowedGcodeTools = setOf(1),
        )

        bed.onBackPressed()
        assertFalse("exiting the tool should clear standalone placement mode",
            readRendererBooleanOnGl(glView, "isStandaloneTextPlacementMode"))

        val historyDisabledByMemoryGuard = AtomicReference<Boolean>()
        compose.runOnUiThread {
            historyDisabledByMemoryGuard.set(
                BedFragment::class.java.getDeclaredField("modelHistoryDisabledForMemory")
                    .apply { isAccessible = true }.getBoolean(bed),
            )
        }
        compose.runOnUiThread { bed.undoModelEdit() }
        if (requireNotNull(historyDisabledByMemoryGuard.get())) {
            assertEquals("Memory protection should keep the generated object when history is disabled", 1,
                readObjectCountOnGl(glView))
            return
        }
        val undoDeadline = SystemClock.uptimeMillis() + 10_000L
        while (readObjectCountOnGl(glView) != 0 && SystemClock.uptimeMillis() < undoDeadline) {
            SystemClock.sleep(25L)
        }
        assertEquals("Undo should remove only the generated text object", 0, readObjectCountOnGl(glView))
        compose.runOnUiThread { bed.redoModelEdit() }
        val redoDeadline = SystemClock.uptimeMillis() + 10_000L
        while (readObjectCountOnGl(glView) != 1 && SystemClock.uptimeMillis() < redoDeadline) {
            SystemClock.sleep(25L)
        }
        assertEquals("Redo should restore the printable text mesh", textTriangleCount,
            readTriangleCountOnGl(glView, 0))
        val redoneTextBounds = readExactBoundsOnGl(glView, 0).single()
        textBounds.forEachIndexed { axis, expected ->
            assertEquals("Redo should preserve generated text bounds on axis $axis", expected,
                redoneTextBounds[axis], 0.02)
        }
        } finally {
            if (bed.isEmbossMode) bed.onBackPressed()
            Prefs.setStoredFilamentSlots(previousSlots)
        }
    }

    @Test
    fun editPaintActionsOpenTheirGlModesAndBackCancelsWithoutMeshMutation() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val modelFile = File(activity.cacheDir, "menu-paint-actions-test.stl")
        val previousSlots = Prefs.getStoredFilamentSlots().map { FilamentSlot(it.color, it.type) }
        Prefs.setStoredFilamentSlots(listOf(
            FilamentSlot(0xFFCC3333.toInt(), "UI Paint Base"),
            FilamentSlot(0xFF3366CC.toInt(), "UI Paint Accent"),
        ))
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            val model = loadSingleTestModel(bed, modelFile)
            assertEquals(1, model.objectsCount)
            val glView = requireNotNull(bed.glView)
            glView.selectModel(0, Runnable {})
            instrumentationWaitForGlIdle()
            val trianglesBefore = readTriangleCountOnGl(glView, 0)
            assertFalse("The fixture starts without paint overrides", readModelHasPaintOnGl(glView, 0))

            val paintActions = listOf(
                R.string.MenuToolbarSupportPaint to GLRenderer.PAINT_MODE_SUPPORT,
                R.string.MenuToolbarSeamPaint to GLRenderer.PAINT_MODE_SEAM,
                R.string.MenuToolbarFuzzyPaint to GLRenderer.PAINT_MODE_FUZZY,
                R.string.MenuToolbarColorPaint to GLRenderer.PAINT_MODE_COLOR,
            )
            paintActions.forEach { (labelRes, expectedMode) ->
                openSelectedObjectMenu(activity)
                revealMenuAction(activity, labelRes)
                compose.onNodeWithContentDescription(activity.getString(labelRes))
                    .performScrollTo().assertIsDisplayed().performClick()
                val paintStarted = runCatching {
                    compose.waitUntil(timeoutMillis = 10_000) {
                        bed.isPaintMode && readRendererBooleanOnGl(glView, "isPaintMode")
                    }
                }
                assertTrue(
                    "Paint mode $expectedMode did not remain active: bed=${bed.isPaintMode}, " +
                        "renderer=${readRendererBooleanOnGl(glView, "isPaintMode")}, " +
                        "rendererMode=${readRendererIntOnGl(glView, "paintModeKind")}",
                    paintStarted.isSuccess,
                )
                compose.onNodeWithText(activity.getString(R.string.PaintWith)).assertIsDisplayed()
                assertEquals("The selected menu action must activate its matching GL paint mode",
                    expectedMode, readRendererIntOnGl(glView, "paintModeKind"))
                assertFalse("Opening or cancelling paint must not add model paint",
                    readModelHasPaintOnGl(glView, 0))

                assertTrue("Android Back should cancel the active paint tool", bed.onBackPressed())
                compose.waitUntil(timeoutMillis = 10_000) { !bed.isPaintMode }
                compose.waitUntil(timeoutMillis = 10_000) {
                    !readRendererBooleanOnGl(glView, "isPaintMode")
                }
                assertFalse("Cancellation should preserve the original unpainted mesh",
                    readModelHasPaintOnGl(glView, 0))
                assertEquals("Cancellation should preserve mesh topology", trianglesBefore,
                    readTriangleCountOnGl(glView, 0))
                assertTrue("Paint tool exit should retain the renderer view", bed.glView === glView)
            }

            openSelectedObjectMenu(activity)
            revealMenuAction(activity, R.string.MenuToolbarColorPaint)
            compose.onNodeWithContentDescription(activity.getString(R.string.MenuToolbarColorPaint))
                .performScrollTo().assertIsDisplayed().performClick()
            val secondPaintStart = runCatching {
                compose.waitUntil(timeoutMillis = 10_000) {
                    bed.isPaintMode && readRendererBooleanOnGl(glView, "isPaintMode")
                }
            }
            assertTrue(
                "Second color paint did not start: bed=${bed.isPaintMode}, " +
                    "renderer=${readRendererBooleanOnGl(glView, "isPaintMode")}, " +
                    "rendererMode=${readRendererIntOnGl(glView, "paintModeKind")}, " +
                    "selected=${readSelectedObjectOnGl(glView)}",
                secondPaintStart.isSuccess,
            )
            assertTrue("the GL viewport must stay measurable while painting", glView.height > 0)
            assertTrue("the renderer must retain a valid build plate while painting", readBedValidOnGl(glView))
            assertEquals("paint mode must expose one copy of each filament swatch", 1,
                compose.onAllNodesWithTag("paint-filament-swatch-1").fetchSemanticsNodes().size)
            assertEquals("paint mode must expose one copy of each filament swatch", 1,
                compose.onAllNodesWithTag("paint-filament-swatch-2").fetchSemanticsNodes().size)
            compose.onNodeWithText(activity.getString(R.string.PaintBaseFilament)).assertIsDisplayed()
            compose.onNodeWithTag("workspace-docked-tool-panel").assertIsDisplayed()
            val paintViewportBounds = compose.onNodeWithTag("workspace-renderer-area")
                .fetchSemanticsNode().boundsInRoot
            val paintControlsBounds = compose.onNodeWithTag("workspace-docked-tool-panel")
                .fetchSemanticsNode().boundsInRoot
            assertTrue(
                "The paint controls must leave at least 60% of the workspace viewport for the plate; " +
                    "renderer=$paintViewportBounds controls=$paintControlsBounds",
                paintViewportBounds.height >= (paintViewportBounds.height + paintControlsBounds.height) * 0.60f &&
                    paintViewportBounds.bottom <= paintControlsBounds.top + 1f,
            )
            compose.onNodeWithTag("paint-palette-role-toggle").performClick()
            compose.onNodeWithText(activity.getString(R.string.PaintBaseFilament)).assertIsDisplayed()
            compose.onAllNodesWithContentDescription(
                activity.getString(R.string.FilamentPaletteSlotDescription, 2, "UI Paint Accent")
            ).get(0).assertIsDisplayed().performClick()
            assertEquals("the alternate base filament should be staged for an immediate color preview", 2,
                readRendererNullableIntOnGl(glView, "paintBaseFilamentPreview"))
            assertArrayEquals("a base-filament choice remains a draft until confirmed", intArrayOf(1),
                readUsedFilamentsOnGl(glView, 0))
            compose.onNodeWithTag("workspace-docked-tool-panel").performTouchInput { swipeUp() }
            compose.onNodeWithText(activity.getString(android.R.string.cancel)).performScrollTo().assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 10_000) { !bed.isPaintMode }
            instrumentationWaitForGlIdle()
            assertEquals("cancelling must clear the staged base color preview", null,
                readRendererNullableIntOnGl(glView, "paintBaseFilamentPreview"))
            assertArrayEquals(
                "cancelling a paint session must restore the model's original base filament",
                intArrayOf(1), readUsedFilamentsOnGl(glView, 0),
            )

            openSelectedObjectMenu(activity)
            revealMenuAction(activity, R.string.MenuToolbarColorPaint)
            compose.onNodeWithContentDescription(activity.getString(R.string.MenuToolbarColorPaint))
                .performScrollTo().assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                bed.isPaintMode && readRendererBooleanOnGl(glView, "isPaintMode")
            }
            compose.onNodeWithText(activity.getString(R.string.PaintWith)).assertIsDisplayed()
            compose.onNodeWithTag("workspace-docked-tool-panel").assertIsDisplayed()
            compose.onNodeWithTag("paint-palette-role-toggle").performClick()
            compose.onNodeWithText(activity.getString(R.string.PaintBaseFilament)).assertIsDisplayed()
            compose.onAllNodesWithContentDescription(
                activity.getString(R.string.FilamentPaletteSlotDescription, 2, "UI Paint Accent")
            ).get(0).assertIsDisplayed().performClick()
            assertEquals("the selected base filament should preview before confirmation", 2,
                readRendererNullableIntOnGl(glView, "paintBaseFilamentPreview"))
            assertArrayEquals("choosing a base filament must not mutate the model before Done", intArrayOf(1),
                readUsedFilamentsOnGl(glView, 0))
            compose.onNodeWithTag("workspace-docked-tool-panel").performTouchInput { swipeUp() }
            compose.onNodeWithText(activity.getString(R.string.ActionDone))
                .performScrollTo().assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 10_000) { !bed.isPaintMode }
            instrumentationWaitForGlIdle()
            assertEquals("committing must clear the staged preview after applying the chosen base", null,
                readRendererNullableIntOnGl(glView, "paintBaseFilamentPreview"))
            assertArrayEquals("the confirmed color-panel base-filament action should update the native model",
                intArrayOf(2), readUsedFilamentsOnGl(glView, 0))
            assertFalse("changing the base filament should not fabricate facet paint",
                readModelHasPaintOnGl(glView, 0))
            assertEquals("changing the base filament must preserve mesh topology", trianglesBefore,
                readTriangleCountOnGl(glView, 0))
        } finally {
            if (bed.isPaintMode) bed.onBackPressed()
            modelFile.delete()
            Prefs.setStoredFilamentSlots(previousSlots)
        }
    }

    @Test
    fun colorPaintDraftIsVisibleBeforeConfirmAndCancelPreservesModel() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val modelFile = File(activity.cacheDir, "menu-paint-live-preview-test.stl")
        val previousSlots = Prefs.getStoredFilamentSlots().map { FilamentSlot(it.color, it.type) }
        Prefs.setStoredFilamentSlots(listOf(
            FilamentSlot(0xFFCC3333.toInt(), "Live Paint Base"),
            FilamentSlot(0xFF3366CC.toInt(), "Live Paint Accent"),
        ))
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            loadSingleTestModel(bed, modelFile)
            val glView = requireNotNull(bed.glView)
            glView.selectModel(0, Runnable {})
            instrumentationWaitForGlIdle()
            openSelectedObjectMenu(activity)
            revealMenuAction(activity, R.string.MenuToolbarColorPaint)
            compose.onNodeWithContentDescription(activity.getString(R.string.MenuToolbarColorPaint))
                .performScrollTo().assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                bed.isPaintMode && readRendererBooleanOnGl(glView, "isPaintMode")
            }
            compose.onNodeWithContentDescription(
                activity.getString(R.string.WorkspaceFilamentButtonDescription, 2)
            ).assertDoesNotExist()
            assertEquals("color painting must use exactly one filament palette row", 1,
                compose.onAllNodesWithTag("paint-palette-row").fetchSemanticsNodes().size)
            assertEquals("each paint slot must have one visible swatch, not duplicate filament rows", 1,
                compose.onAllNodesWithTag("paint-filament-swatch-1").fetchSemanticsNodes().size)
            assertEquals("each paint slot must have one visible swatch, not duplicate filament rows", 1,
                compose.onAllNodesWithTag("paint-filament-swatch-2").fetchSemanticsNodes().size)
            assertEquals("the workspace filament shortcut must not duplicate the paint palette", 0,
                compose.onAllNodesWithContentDescription(
                    activity.getString(R.string.WorkspaceFilamentButtonDescription, 2),
                ).fetchSemanticsNodes().size)
            compose.onNodeWithTag("workspace-docked-tool-panel").assertIsDisplayed()
            val paintViewportBounds = compose.onNodeWithTag("workspace-renderer-area")
                .fetchSemanticsNode().boundsInRoot
            val paintControlsBounds = compose.onNodeWithTag("workspace-docked-tool-panel")
                .fetchSemanticsNode().boundsInRoot
            val combinedHeight = paintViewportBounds.height + paintControlsBounds.height
            assertTrue(
                "color controls must leave at least 70% of the tool area for the model; " +
                    "renderer=$paintViewportBounds controls=$paintControlsBounds",
                paintViewportBounds.height >= combinedHeight * 0.70f &&
                    paintViewportBounds.bottom <= paintControlsBounds.top + 1f,
            )
            val paletteBounds = compose.onNodeWithTag("paint-palette-row").fetchSemanticsNode().boundsInRoot
            assertTrue(
                "the compact paint dock must expose its palette row without scrolling; " +
                    "renderer=$paintViewportBounds controls=$paintControlsBounds",
                paletteBounds.top >= paintControlsBounds.top - 1f &&
                    paletteBounds.bottom <= paintControlsBounds.bottom + 1f,
            )
            val point = findUncoveredPreciseSurfacePoint(glView, bed)
            val beforeBasePreview = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            compose.onNodeWithTag("paint-palette-role-toggle").performClick()
            compose.onNodeWithText(activity.getString(R.string.PaintBaseFilament)).assertIsDisplayed()
            revealPaintFilamentSwatch(activity, 2, "Live Paint Accent", occurrence = 0)
                .performClick()
            instrumentationWaitForGlIdle()
            SystemClock.sleep(200)
            val afterBasePreview = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            assertPaintColorVisibleAtPoint(
                beforeBasePreview,
                afterBasePreview,
                point,
                0xFF3366CC.toInt(),
                "selecting a base filament must recolor the live model before Done",
            )
            assertArrayEquals("a live base color remains a draft until Done", intArrayOf(1),
                readUsedFilamentsOnGl(glView, 0))

            compose.onNodeWithTag("paint-palette-role-toggle").performClick()
            compose.onNodeWithText(activity.getString(R.string.PaintWith)).assertIsDisplayed()
            // Restore the model's red base preview, then paint the blue accent so the
            // held-stroke screenshot can distinguish the preview from its source surface.
            compose.onNodeWithTag("paint-palette-role-toggle").performClick()
            compose.onNodeWithText(activity.getString(R.string.PaintBaseFilament)).assertIsDisplayed()
            revealPaintFilamentSwatch(activity, 1, "Live Paint Base", occurrence = 0).performClick()
            instrumentationWaitForGlIdle()
            compose.onNodeWithTag("paint-palette-role-toggle").performClick()
            compose.onNodeWithText(activity.getString(R.string.PaintWith)).assertIsDisplayed()
            revealPaintFilamentSwatch(activity, 2, "Live Paint Accent", occurrence = 0)
                .performClick()
            glView.queueEvent { glView.renderer.paintBrushRadius = 1.0 }
            instrumentationWaitForGlIdle()
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.waitForIdleSync()
            SystemClock.sleep(250)
            val beforePaintFrame = instrumentation.uiAutomation.takeScreenshot()
            val frameBeforeStroke = readRendererLastDrawOnGl(glView)
            val overlayRefreshBeforeStroke = readRendererLongOnGl(glView, "lastPaintOverlayRebuildAtMs")
            val previewPoint = findUncoveredPreciseSurfacePoint(
                glView, bed, avoid = point, minimumAvoidDistancePx = 24f,
            )
            val beforeHeldStroke = instrumentation.uiAutomation.takeScreenshot()
            injectCanvasStroke(glView, point.first, point.second, previewPoint = previewPoint) {
                // Keep the pointer down after the coalesced input has reached GL. A
                // throttled overlay rebuild must schedule its own refresh instead of
                // waiting for finger-up/Done to make the draft visible.
                paintAtScreenPointWhileHeld(glView, previewPoint)
                instrumentationWaitForGlIdle()
                assertTrue("a throttled brush update must remain pending, scheduled, or already refreshed",
                    readRendererBooleanOnGl(glView, "paintOverlayRebuildPending") ||
                        readRendererBooleanOnGl(glView, "paintOverlayRefreshScheduled") ||
                        readRendererLongOnGl(glView, "lastPaintOverlayRebuildAtMs") > overlayRefreshBeforeStroke)
                compose.waitUntil(timeoutMillis = 2_000) {
                    !readRendererBooleanOnGl(glView, "paintOverlayRebuildPending") &&
                        !readRendererBooleanOnGl(glView, "paintOverlayRefreshScheduled") &&
                        readRendererLongOnGl(glView, "lastPaintOverlayRebuildAtMs") > overlayRefreshBeforeStroke
                }
                SystemClock.sleep(80)
                val whileFingerDown = instrumentation.uiAutomation.takeScreenshot()
                assertPaintColorVisibleAtPoint(
                    beforeHeldStroke, whileFingerDown, previewPoint, 0xFF3366CC.toInt(),
                    "the selected blue filament should become visible before pointer-up or Done",
                )
                assertFalse("live paint must remain an uncommitted draft while the pointer is down",
                    readModelHasPaintOnGl(glView, 0))
            }
            // Touch handlers enqueue paint work on the GL thread. Drain that queue before
            // asserting a frame, especially on slower GPUs where per-stroke overlay builds
            // can take longer than one arbitrary sleep interval.
            instrumentationWaitForGlIdle()
            val frameWaitStarted = SystemClock.elapsedRealtime()
            compose.waitUntil(timeoutMillis = 5_000) {
                readRendererLastDrawOnGl(glView) > frameBeforeStroke
            }
            val frameWaitMs = SystemClock.elapsedRealtime() - frameWaitStarted
            assertTrue("live brush preview should reach a rendered frame promptly; waited ${frameWaitMs}ms",
                frameWaitMs < 1_500L)
            SystemClock.sleep(100)
            val afterPaintFrame = instrumentation.uiAutomation.takeScreenshot()
            val livePaintTriangles = readPaintPreviewTriangleCountOnGl(glView, 2)
            assertTrue("the canvas touch should assign facets to the live paint draft; triangles=$livePaintTriangles",
                livePaintTriangles > 0)
            assertPaintPreviewIsVisibleInFrame(
                beforePaintFrame, afterPaintFrame, previewPoint,
                liveOverlayHits = "draftTriangles=$livePaintTriangles",
            )
            assertFalse("live color remains a draft until confirmation", readModelHasPaintOnGl(glView, 0))

            assertTrue("Android Back should cancel the uncommitted paint draft", bed.onBackPressed())
            compose.waitUntil(timeoutMillis = 10_000) { !bed.isPaintMode }
            instrumentationWaitForGlIdle()
            assertFalse("cancel must discard the paint draft", readModelHasPaintOnGl(glView, 0))
            assertArrayEquals("cancel preserves the model's base filament", intArrayOf(1),
                readUsedFilamentsOnGl(glView, 0))
            assertTrue("paint mode cancellation must retain the renderer view", bed.glView === glView)
            assertTrue("live paint must not drop the configured build plate",
                readBedValidOnGl(glView))
        } finally {
            if (bed.isPaintMode) bed.onBackPressed()
            modelFile.delete()
            Prefs.setStoredFilamentSlots(previousSlots)
        }
    }

    @Test
    fun confirmingColorPaintKeepsOnePaletteAndThePlateHostUntilGlCommitCompletes() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val modelFile = File(activity.cacheDir, "menu-paint-commit-transition-test.stl")
        val previousSlots = Prefs.getStoredFilamentSlots().map { FilamentSlot(it.color, it.type) }
        Prefs.setStoredFilamentSlots(listOf(
            FilamentSlot(0xFFCC3333.toInt(), "Commit Transition Base"),
            FilamentSlot(0xFF3366CC.toInt(), "Commit Transition Accent"),
        ))
        val glBusy = CountDownLatch(1)
        val releaseGl = CountDownLatch(1)
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            loadSingleTestModel(bed, modelFile)
            val glView = requireNotNull(bed.glView)
            glView.selectModel(0, Runnable {})
            instrumentationWaitForGlIdle()
            openSelectedObjectMenu(activity)
            revealMenuAction(activity, R.string.MenuToolbarColorPaint)
            compose.onNodeWithContentDescription(activity.getString(R.string.MenuToolbarColorPaint))
                .performScrollTo().assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                bed.isPaintMode && readRendererBooleanOnGl(glView, "isPaintMode")
            }
            compose.onNodeWithTag("workspace-docked-tool-panel").assertIsDisplayed()

            // Hold the GL queue so the Done callback cannot commit immediately. The workspace
            // must keep the paint controls as the sole palette owner until this queue resumes.
            glView.queueEvent {
                glBusy.countDown()
                releaseGl.await(60, TimeUnit.SECONDS)
            }
            assertTrue("the test must acquire the renderer GL queue", glBusy.await(5, TimeUnit.SECONDS))
            compose.onNodeWithText(activity.getString(R.string.ActionDone))
                .performScrollTo().assertIsDisplayed().performClick()

            compose.onNodeWithTag("workspace-docked-tool-panel").assertIsDisplayed()
            assertEquals("the dock retains its single palette until GL commit finishes", 1,
                compose.onAllNodesWithTag("paint-palette-row").fetchSemanticsNodes().size)
            assertEquals("the global filament shortcut must not return while the GL paint session is active", 0,
                compose.onAllNodesWithContentDescription(
                    activity.getString(R.string.WorkspaceFilamentButtonDescription, 2),
                ).fetchSemanticsNodes().size)
            assertTrue("the renderer view and its plate must remain attached during the transition",
                bed.glView === glView)
            releaseGl.countDown()
            compose.waitUntil(timeoutMillis = 10_000) { !bed.isPaintMode }
            instrumentationWaitForGlIdle()
            assertEquals("after GL commit there is exactly one normal filament entry", 1,
                compose.onAllNodesWithContentDescription(
                    activity.getString(R.string.WorkspaceFilamentButtonDescription, 2),
                ).fetchSemanticsNodes().size)
            assertEquals("the paint palette is removed after the renderer has committed", 0,
                compose.onAllNodesWithTag("paint-palette-row").fetchSemanticsNodes().size)
            assertTrue("the build plate renderer remains attached after paint confirmation",
                bed.glView === glView && readBedValidOnGl(glView))
        } finally {
            releaseGl.countDown()
            if (bed.isPaintMode) bed.onBackPressed()
            modelFile.delete()
            Prefs.setStoredFilamentSlots(previousSlots)
        }
    }

    @Test
    fun importedMulticolorProjectShowsLivePaintBeforeConfirmWithoutDuplicatePaletteOrPlateLoss() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val modelFile = File(activity.cacheDir, "menu-paint-imported-multicolor-test.3mf")
        val previousSlots = Prefs.getStoredFilamentSlots().map { FilamentSlot(it.color, it.type) }
        Prefs.setStoredFilamentSlots(listOf(
            FilamentSlot(0xFFCC3333.toInt(), "Imported Paint Base"),
            FilamentSlot(0xFF3366CC.toInt(), "Imported Paint Accent"),
        ))
        try {
            InstrumentationRegistry.getInstrumentation().context.assets
                .open("models/two-color-project.3mf").use { source ->
                    modelFile.outputStream().use(source::copyTo)
                }
            val loadedLatch = CountDownLatch(1)
            val loaded = AtomicReference<Model?>()
            val loadFailure = AtomicReference<Exception?>()
            bed.loadModel(modelFile, false, 1, object : BedFragment.ModelLoadCallback {
                override fun onLoaded(loadedModel: Model, firstNewObject: Int, addedObjects: Int) {
                    loaded.set(loadedModel)
                    loadedLatch.countDown()
                }

                override fun onFailed(error: Exception) {
                    loadFailure.set(error)
                    loadedLatch.countDown()
                }
            })
            assertTrue("the multicolor 3MF fixture should finish loading", loadedLatch.await(60, TimeUnit.SECONDS))
            val model = requireNotNull(loaded.get()) { "multicolor fixture load failed: ${loadFailure.get()}" }
            assertEquals(2, model.objectsCount)
            glView.selectModel(0, Runnable {})
            instrumentationWaitForGlIdle()
            val zoomReady = CountDownLatch(1)
            glView.queueEvent {
                glView.renderer.camera.zoom = 2f
                zoomReady.countDown()
            }
            assertTrue("GL thread should apply the pre-paint zoom", zoomReady.await(5, TimeUnit.SECONDS))

            openSelectedObjectMenu(activity)
            revealMenuAction(activity, R.string.MenuToolbarColorPaint)
            compose.onNodeWithContentDescription(activity.getString(R.string.MenuToolbarColorPaint))
                .performScrollTo().assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                bed.isPaintMode && readRendererBooleanOnGl(glView, "isPaintMode")
            }
            assertEquals("the paint panel must show one palette row", 1,
                compose.onAllNodesWithTag("paint-palette-row").fetchSemanticsNodes().size)
            assertEquals("paint mode must not duplicate the compact workspace filament row", 0,
                compose.onAllNodesWithContentDescription(
                    activity.getString(R.string.WorkspaceFilamentButtonDescription, 2),
                ).fetchSemanticsNodes().size)
            assertTrue("the build plate renderer must remain valid in paint mode", readBedValidOnGl(glView))
            compose.onNodeWithTag("workspace-docked-tool-panel").assertIsDisplayed()
            val viewport = compose.onNodeWithTag("workspace-renderer-area").fetchSemanticsNode().boundsInRoot
            val controls = compose.onNodeWithTag("workspace-docked-tool-panel").fetchSemanticsNode().boundsInRoot
            assertTrue("paint controls must not cover the plate viewport: viewport=$viewport controls=$controls",
                viewport.bottom <= controls.top + 1f && viewport.height >= (viewport.height + controls.height) * 0.60f)
            val plateNdc = readBuildPlateNdcBoundsOnGl(glView)
            assertTrue("the full build plate must fit inside the resized paint viewport; projected bounds=$plateNdc",
                plateNdc[0] >= -1f && plateNdc[1] <= 1f && plateNdc[2] >= -1f && plateNdc[3] <= 1f)
            assertEquals("shrinking the paint viewport should refit the camera so the bed stays visible",
                1f, readRendererCameraZoomOnGl(glView), 0.001f)
            compose.onNodeWithTag("paint-controls-scroll").performTouchInput { swipeUp() }
            compose.onNodeWithTag("paint-palette-row").assertIsDisplayed()

            val point = findUncoveredPreciseSurfacePoint(glView, bed)
            val before = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            revealPaintFilamentSwatch(activity, 2, "Imported Paint Accent", occurrence = 0).performClick()
            injectCanvasStroke(
                glView,
                point.first,
                point.second,
                previewPoint = point,
                whileFingerDown = {
                    paintAtScreenPointWhileHeld(glView, point)
                    instrumentationWaitForGlIdle()
                    compose.waitUntil(timeoutMillis = 2_000) {
                        !readRendererBooleanOnGl(glView, "paintOverlayRebuildPending") &&
                            !readRendererBooleanOnGl(glView, "paintOverlayRefreshScheduled")
                    }
                    val whileHeld = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                    val heldBaseline = before.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                    assertPaintColorVisibleAtPoint(
                        heldBaseline,
                        whileHeld,
                        point,
                        0xFF3366CC.toInt(),
                        "an imported 3MF color must be visible while the finger is still down, before confirmation",
                    )
                    assertFalse("the imported model must stay unchanged during the held paint draft",
                        readModelHasPaintOnGl(glView, 0))
                    assertTrue("the held paint preview must keep the entire build plate in view",
                        readBuildPlateNdcBoundsOnGl(glView).let { it[0] >= -1f && it[1] <= 1f && it[2] >= -1f && it[3] <= 1f })
                },
            )
            instrumentationWaitForGlIdle()
            compose.waitUntil(timeoutMillis = 5_000) { readPaintPreviewTriangleCountOnGl(glView, 2) > 0 }
            val after = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            assertPaintPreviewIsVisibleInFrame(before, after, point,
                liveOverlayHits = "imported 3MF draft triangles=${readPaintPreviewTriangleCountOnGl(glView, 2)}")
            assertFalse("the imported project remains unmodified until paint confirmation",
                readModelHasPaintOnGl(glView, 0))
            assertArrayEquals("the untouched second object's imported filament must remain intact", intArrayOf(2),
                readUsedFilamentsOnGl(glView, 1))

            assertTrue("Android Back should cancel the imported-project paint draft", bed.onBackPressed())
            compose.waitUntil(timeoutMillis = 10_000) { !bed.isPaintMode }
            instrumentationWaitForGlIdle()
            assertArrayEquals("cancel must preserve the first object's base filament", intArrayOf(1),
                readUsedFilamentsOnGl(glView, 0))
            assertArrayEquals("cancel must preserve the second object's imported filament", intArrayOf(2),
                readUsedFilamentsOnGl(glView, 1))
            assertTrue("cancel must leave the plate renderer available", bed.glView === glView && readBedValidOnGl(glView))
        } finally {
            if (bed.isPaintMode) bed.onBackPressed()
            modelFile.delete()
            Prefs.setStoredFilamentSlots(previousSlots)
        }
    }

    @Test
    fun userKromaCutFixtureShowsLivePaintWithOnePaletteAndVisibleBuildPlate() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val glView = requireNotNull(bed.glView)
        val fixture = File(activity.cacheDir, "kromacut-live-paint-fixture.3mf")
        val previousSlots = Prefs.getStoredFilamentSlots().map { FilamentSlot(it.color, it.type) }
        Prefs.setStoredFilamentSlots(listOf(
            FilamentSlot(0xFFCC3333.toInt(), "Kroma Paint Base"),
            FilamentSlot(0xFF3366CC.toInt(), "Kroma Paint Accent"),
        ))
        try {
            val fixturePipe = InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("cat /data/local/tmp/kromacut.3mf")
            android.os.ParcelFileDescriptor.AutoCloseInputStream(fixturePipe).use { input ->
                fixture.outputStream().use { output -> input.copyTo(output) }
            }
            assumeTrue(
                "Push the provided KromaCut 3MF to /data/local/tmp/kromacut.3mf",
                fixture.length() > 1_000_000L,
            )

            val loadedLatch = CountDownLatch(1)
            val loaded = AtomicReference<Model?>()
            val loadFailure = AtomicReference<Exception?>()
            bed.loadModel(fixture, false, 1, object : BedFragment.ModelLoadCallback {
                override fun onLoaded(loadedModel: Model, firstNewObject: Int, addedObjects: Int) {
                    loaded.set(loadedModel)
                    loadedLatch.countDown()
                }

                override fun onFailed(error: Exception) {
                    loadFailure.set(error)
                    loadedLatch.countDown()
                }
            })
            assertTrue("KromaCut 3MF load should complete", loadedLatch.await(90, TimeUnit.SECONDS))
            val model = requireNotNull(loaded.get()) { "KromaCut load failed: ${loadFailure.get()}" }
            assertEquals("the supplied KromaCut project is one assembled multicolor object", 1, model.objectsCount)
            assertEquals("both original imported colors should remain assigned", listOf(1, 2),
                model.getUsedFilaments(0).toList())
            glView.selectModel(0, Runnable {})
            instrumentationWaitForGlIdle()

            openSelectedObjectMenu(activity)
            revealMenuAction(activity, R.string.MenuToolbarColorPaint)
            compose.onNodeWithContentDescription(activity.getString(R.string.MenuToolbarColorPaint))
                .performScrollTo().assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 20_000) {
                bed.isPaintMode && readRendererBooleanOnGl(glView, "isPaintMode")
            }
            assertEquals("KromaCut painting must expose exactly one filament palette row", 1,
                compose.onAllNodesWithTag("paint-palette-row").fetchSemanticsNodes().size)
            assertEquals("the header filament palette must stay hidden during paint mode", 0,
                compose.onAllNodesWithContentDescription(
                    activity.getString(R.string.WorkspaceFilamentButtonDescription, 2),
                ).fetchSemanticsNodes().size)
            compose.onNodeWithTag("workspace-docked-tool-panel").assertIsDisplayed()
            val viewport = compose.onNodeWithTag("workspace-renderer-area").fetchSemanticsNode().boundsInRoot
            val controls = compose.onNodeWithTag("workspace-docked-tool-panel").fetchSemanticsNode().boundsInRoot
            assertTrue("paint controls must not cover the KromaCut plate viewport", viewport.bottom <= controls.top + 1f)
            assertTrue("the plate geometry must remain inside the resized viewport",
                readBuildPlateNdcBoundsOnGl(glView).let { it[0] >= -1f && it[1] <= 1f && it[2] >= -1f && it[3] <= 1f })

            val point = findUncoveredPreciseSurfacePoint(glView, bed)
            val before = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            revealPaintFilamentSwatch(activity, 2, "Kroma Paint Accent", occurrence = 0).performClick()
            injectCanvasStroke(
                glView,
                point.first,
                point.second,
                previewPoint = point,
                whileFingerDown = {
                    paintAtScreenPointWhileHeld(glView, point)
                    instrumentationWaitForGlIdle()
                    compose.waitUntil(timeoutMillis = 3_000) {
                        !readRendererBooleanOnGl(glView, "paintOverlayRebuildPending") &&
                            !readRendererBooleanOnGl(glView, "paintOverlayRefreshScheduled")
                    }
                    assertPaintColorVisibleAtPoint(
                        before.copy(android.graphics.Bitmap.Config.ARGB_8888, false),
                        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot(),
                        point,
                        0xFF3366CC.toInt(),
                        "KromaCut brush color should be rendered before the user confirms the paint session",
                    )
                    assertFalse("the visible KromaCut paint remains a draft until confirmation",
                        readModelHasPaintOnGl(glView, 0))
                },
            )
            assertTrue("KromaCut paint preview should leave the plate available", bed.glView === glView)
        } finally {
            if (bed.isPaintMode) bed.onBackPressed()
            fixture.delete()
            Prefs.setStoredFilamentSlots(previousSlots)
        }
    }

    @Test
    fun colorPaintPanelAcceptsRealCanvasTouchAndUndoRestoresFacetAssignments() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val modelFile = File(activity.cacheDir, "menu-paint-touch-test.stl")
        val previousSlots = Prefs.getStoredFilamentSlots().map { FilamentSlot(it.color, it.type) }
        Prefs.setStoredFilamentSlots(listOf(
            FilamentSlot(0xFFCC3333.toInt(), "Touch Paint Base"),
            FilamentSlot(0xFF3366CC.toInt(), "Touch Paint Accent"),
        ))
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            val model = loadSingleTestModel(bed, modelFile)
            val glView = requireNotNull(bed.glView)
            glView.selectModel(0, Runnable {})
            instrumentationWaitForGlIdle()
            assertFalse("paint touch fixture starts without facet assignments", readModelHasPaintOnGl(glView, 0))
            openSelectedObjectMenu(activity)
            revealMenuAction(activity, R.string.MenuToolbarColorPaint)
            compose.onNodeWithContentDescription(activity.getString(R.string.MenuToolbarColorPaint))
                .performScrollTo().assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                bed.isPaintMode && readRendererBooleanOnGl(glView, "isPaintMode")
            }
            val baseFilamentPreviewBeforeStrokes = readPaintPreviewTriangleCountOnGl(glView, 1)
            val accentFilamentPreviewBeforeStrokes = readPaintPreviewTriangleCountOnGl(glView, 2)
            compose.onNodeWithContentDescription(
                activity.getString(R.string.WorkspaceFilamentButtonDescription, 2)
            ).assertDoesNotExist()
            revealPaintFilamentSwatch(activity, 2, "Touch Paint Accent", occurrence = 0)
                .performClick()

            val point = findUncoveredPreciseSurfacePoint(glView, bed)
            injectCanvasStroke(glView, point.first, point.second)
            val previewTriangles = readPaintPreviewTriangleCountOnGl(glView, 2)
            assertTrue("an injected touch on a precise GL surface hit must create a paint preview; point=$point",
                previewTriangles > 0)
            val firstStrokeBaseTriangles = readPaintPreviewTriangleCountOnGl(glView, 1)
            val firstStrokeAccentTriangles = previewTriangles
            assertFalse("painting remains a preview until Done", readModelHasPaintOnGl(glView, 0))

            // Two distinct color changes are separate reversible strokes while still inside the
            // draft session. Undoing the second must restore the first; Undo again must restore
            // the original unpainted selector. Redo must replay those states in order.
            val secondPoint = findUncoveredPreciseSurfacePoint(glView, bed, avoid = point)
            revealPaintFilamentSwatch(activity, 1, "Touch Paint Base", occurrence = 0).performClick()
            injectCanvasStroke(glView, secondPoint.first, secondPoint.second)
            assertTrue("the second stroke should create a preview for the first filament",
                readPaintPreviewTriangleCountOnGl(glView, 1) > 0)
            assertTrue("the second stroke should not erase the first stroke's separate facets",
                readPaintPreviewTriangleCountOnGl(glView, 2) > 0)
            val secondStrokeBaseTriangles = readPaintPreviewTriangleCountOnGl(glView, 1)
            val secondStrokeAccentTriangles = readPaintPreviewTriangleCountOnGl(glView, 2)

            fun clickPaintHistoryAction(label: Int) {
                compose.onNodeWithText(activity.getString(label)).performScrollTo().assertIsEnabled().performClick()
                instrumentationWaitForGlIdle()
            }
            clickPaintHistoryAction(R.string.PaintUndoStroke)
            assertEquals("undoing stroke two should restore the first-stroke base-filament overlay",
                firstStrokeBaseTriangles,
                readPaintPreviewTriangleCountOnGl(glView, 1))
            assertEquals("undoing stroke two should restore stroke one's accent-filament overlay",
                firstStrokeAccentTriangles,
                readPaintPreviewTriangleCountOnGl(glView, 2))
            clickPaintHistoryAction(R.string.PaintUndoStroke)
            assertEquals("undoing stroke one should restore the pre-paint base-filament overlay",
                baseFilamentPreviewBeforeStrokes,
                readPaintPreviewTriangleCountOnGl(glView, 1))
            assertEquals("undoing stroke one should restore the pre-paint accent-filament overlay",
                accentFilamentPreviewBeforeStrokes,
                readPaintPreviewTriangleCountOnGl(glView, 2))
            clickPaintHistoryAction(R.string.PaintRedoStroke)
            assertEquals("redo should restore stroke one's accent facets", firstStrokeAccentTriangles,
                readPaintPreviewTriangleCountOnGl(glView, 2))
            assertEquals("redo should restore stroke one's base facets", firstStrokeBaseTriangles,
                readPaintPreviewTriangleCountOnGl(glView, 1))
            clickPaintHistoryAction(R.string.PaintRedoStroke)
            assertEquals("redo should restore stroke two's base-filament facets", secondStrokeBaseTriangles,
                readPaintPreviewTriangleCountOnGl(glView, 1))
            assertEquals("redo should restore stroke two's accent-filament facets", secondStrokeAccentTriangles,
                readPaintPreviewTriangleCountOnGl(glView, 2))

            compose.onNodeWithTag("workspace-mode-panel").performTouchInput { swipeUp() }
            compose.onNodeWithText(activity.getString(R.string.ActionDone))
                .performScrollTo().assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 10_000) { !bed.isPaintMode }
            compose.waitUntil(timeoutMillis = 10_000) { readModelHasPaintOnGl(glView, 0) }
            val usedAfterPaint = readUsedFilamentsOnGl(glView, 0)
            assertTrue("the actual canvas stroke must assign the selected second filament: ${usedAfterPaint.contentToString()}",
                usedAfterPaint.contains(2))
            assertTrue("the committed color brush must produce painted facets",
                readPaintMaxFilamentOnGl(glView, 0) >= 2)

            openEditMenu(activity)
            compose.onNodeWithContentDescription(activity.getString(R.string.ModelUndo))
                .performScrollTo().assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 10_000) { !readModelHasPaintOnGl(glView, 0) }
            assertArrayEquals("Undo restores the original base filament", intArrayOf(1), readUsedFilamentsOnGl(glView, 0))
            if (usesCompactPortraitLayout(activity)) {
                compose.onNodeWithTag("workspace-menu-actions-grid").performScrollToIndex(3)
            }
            compose.onNodeWithContentDescription(activity.getString(R.string.ModelRedo))
                .performScrollTo().assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 10_000) { readModelHasPaintOnGl(glView, 0) }
            assertTrue("Redo restores the committed painted filament", readUsedFilamentsOnGl(glView, 0).contains(2))
        } finally {
            if (bed.isPaintMode) bed.onBackPressed()
            modelFile.delete()
            Prefs.setStoredFilamentSlots(previousSlots)
        }
    }

    @Test
    fun supportSeamAndFuzzyPaintCommitRealCanvasStrokesAndUndoRedo() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val modelFile = File(activity.cacheDir, "menu-special-paint-touch-test.stl")
        try {
            activity.assets.open("models/xyz_cube.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            loadSingleTestModel(bed, modelFile)
            val glView = requireNotNull(bed.glView)
            glView.selectModel(0, Runnable {})
            instrumentationWaitForGlIdle()
            val paintActions = listOf(
                Triple(R.string.MenuToolbarSupportPaint, GLRenderer.PAINT_MODE_SUPPORT, R.string.PaintEnforce),
                Triple(R.string.MenuToolbarSeamPaint, GLRenderer.PAINT_MODE_SEAM, R.string.PaintEnforce),
                Triple(R.string.MenuToolbarFuzzyPaint, GLRenderer.PAINT_MODE_FUZZY, R.string.PaintFuzzy),
            )
            paintActions.forEach { (actionLabel, expectedMode, paintState) ->
                val state = if (paintState == R.string.PaintBlock) 2 else 1
                assertEquals("mode $expectedMode should start with an empty native annotation channel", 0,
                    readCommittedPaintModeTriangleCountOnGl(glView, expectedMode, state))
                openSelectedObjectMenu(activity)
                revealMenuAction(activity, actionLabel)
                compose.onNodeWithContentDescription(activity.getString(actionLabel))
                    .performScrollTo().assertIsDisplayed().performClick()
                compose.waitUntil(timeoutMillis = 10_000) {
                    bed.isPaintMode && readRendererBooleanOnGl(glView, "isPaintMode")
                }
                assertEquals("the menu action must activate its expected GL paint mode",
                    expectedMode, readRendererIntOnGl(glView, "paintModeKind"))
                compose.onNodeWithText(activity.getString(paintState)).assertIsDisplayed().performClick()
                instrumentationWaitForGlIdle()

                val point = findUncoveredPreciseSurfacePoint(glView, bed)
                injectCanvasStroke(glView, point.first, point.second)
                val previewTriangles = readPaintPreviewTriangleCountOnGl(glView, state)
                assertTrue("$expectedMode stroke should create a native preview; point=$point", previewTriangles > 0)
                assertEquals("special paint remains a preview until it is confirmed", 0,
                    readCommittedPaintModeTriangleCountOnGl(glView, expectedMode, state))

                compose.onNodeWithText(activity.getString(R.string.PaintUndoStroke))
                    .performScrollTo().assertIsEnabled().performClick()
                instrumentationWaitForGlIdle()
                assertEquals("undo must remove the active mode-$expectedMode draft stroke", 0,
                    readPaintPreviewTriangleCountOnGl(glView, state))
                compose.onNodeWithText(activity.getString(R.string.PaintRedoStroke))
                    .performScrollTo().assertIsEnabled().performClick()
                instrumentationWaitForGlIdle()
                assertTrue("redo must restore the active mode-$expectedMode draft stroke",
                    readPaintPreviewTriangleCountOnGl(glView, state) > 0)

                assertTrue("Android Back should cancel a staged mode-$expectedMode paint stroke",
                    bed.onBackPressed())
                compose.waitUntil(timeoutMillis = 10_000) { !bed.isPaintMode }
                assertEquals("cancelling mode-$expectedMode paint must discard the staged stroke", 0,
                    readCommittedPaintModeTriangleCountOnGl(glView, expectedMode, state))

                openSelectedObjectMenu(activity)
                revealMenuAction(activity, actionLabel)
                compose.onNodeWithContentDescription(activity.getString(actionLabel))
                    .performScrollTo().assertIsDisplayed().performClick()
                compose.waitUntil(timeoutMillis = 10_000) {
                    bed.isPaintMode && readRendererBooleanOnGl(glView, "isPaintMode")
                }
                compose.onNodeWithText(activity.getString(paintState)).assertIsDisplayed().performClick()
                instrumentationWaitForGlIdle()
                val confirmPoint = findUncoveredPreciseSurfacePoint(glView, bed)
                injectCanvasStroke(glView, confirmPoint.first, confirmPoint.second)
                assertTrue("mode-$expectedMode should rebuild its preview after reopening",
                    readPaintPreviewTriangleCountOnGl(glView, state) > 0)
                assertEquals("reopening a mode after Back must not restore the cancelled draft", 0,
                    readCommittedPaintModeTriangleCountOnGl(glView, expectedMode, state))

                compose.onNodeWithTag("workspace-mode-panel").performTouchInput { swipeUp() }
                compose.onNodeWithText(activity.getString(R.string.ActionDone))
                    .performScrollTo().assertIsDisplayed().performClick()
                compose.waitUntil(timeoutMillis = 10_000) { !bed.isPaintMode }
                compose.waitUntil(timeoutMillis = 10_000) {
                    readCommittedPaintModeTriangleCountOnGl(glView, expectedMode, state) > 0
                }
                compose.runOnUiThread { bed.undoModelEdit() }
                compose.waitUntil(timeoutMillis = 10_000) {
                    readCommittedPaintModeTriangleCountOnGl(glView, expectedMode, state) == 0
                }
                compose.runOnUiThread { bed.redoModelEdit() }
                compose.waitUntil(timeoutMillis = 10_000) {
                    readCommittedPaintModeTriangleCountOnGl(glView, expectedMode, state) > 0
                }
                compose.runOnUiThread { bed.undoModelEdit() }
                compose.waitUntil(timeoutMillis = 10_000) {
                    readCommittedPaintModeTriangleCountOnGl(glView, expectedMode, state) == 0
                }
            }
            assertTrue("finishing special paint tools should preserve the live renderer", bed.glView === glView)
        } finally {
            if (bed.isPaintMode) bed.onBackPressed()
            modelFile.delete()
        }
    }

    @Test
    fun editSimplifyRequiresConfirmationAndCanBeUndoneAndRedone() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val modelFile = File(activity.cacheDir, "menu-simplify-action-test.stl")
        try {
            activity.assets.open("models/sphere.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            val loaded = loadSingleTestModel(bed, modelFile)
            assertEquals(1, loaded.objectsCount)
            val glView = requireNotNull(bed.glView)
            glView.selectModel(0, Runnable {})
            instrumentationWaitForGlIdle()
            val originalTriangles = readTriangleCountOnGl(glView, 0)
            assertTrue("sphere fixture must be large enough for simplification", originalTriangles > 4)

            openSelectedObjectMenu(activity)
            revealMenuAction(activity, R.string.ModelSimplify)
            compose.onNodeWithContentDescription(activity.getString(R.string.ModelSimplify))
                .performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithText(activity.getString(android.R.string.cancel)).assertIsDisplayed().performClick()
            assertEquals("cancelling simplify must preserve the model", originalTriangles,
                readTriangleCountOnGl(glView, 0))

            openSelectedObjectMenu(activity)
            revealMenuAction(activity, R.string.ModelSimplify)
            compose.onNodeWithContentDescription(activity.getString(R.string.ModelSimplify))
                .performScrollTo().assertIsDisplayed().performClick()
            val startAction = compose.onAllNodesWithText(activity.getString(R.string.ModelSimplifyStart))
            val startNodeIndex = startAction.fetchSemanticsNodes().lastIndex
            assertTrue("simplify confirmation button should be present", startNodeIndex >= 0)
            startAction.get(startNodeIndex).assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 30_000) {
                readTriangleCountOnGl(glView, 0) < originalTriangles
            }
            val simplifiedTriangles = readTriangleCountOnGl(glView, 0)
            assertTrue("simplify should produce a smaller native mesh", simplifiedTriangles in 4 until originalTriangles)

            compose.runOnUiThread { bed.undoModelEdit() }
            assertEquals("Undo should restore the imported mesh", originalTriangles,
                readTriangleCountOnGl(requireNotNull(bed.glView), 0))
            compose.runOnUiThread { bed.redoModelEdit() }
            assertEquals("Redo should restore the simplified mesh", simplifiedTriangles,
                readTriangleCountOnGl(requireNotNull(bed.glView), 0))
        } finally {
            modelFile.delete()
        }
    }

    @Test
    fun editSimplifyRejectsSelectionChangedAfterTargetCapture() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val modelFile = File(activity.cacheDir, "menu-simplify-stale-selection-${System.nanoTime()}.stl")
        try {
            activity.assets.open("models/sphere.stl").use { source ->
                modelFile.outputStream().use(source::copyTo)
            }
            val model = loadSingleTestModel(bed, modelFile)
            val glView = requireNotNull(bed.glView)
            glView.selectModel(0, Runnable {})
            val duplicateDone = CountDownLatch(1)
            glView.duplicateSelectedModel(1, Runnable { duplicateDone.countDown() })
            assertTrue("second test object should finish duplicating", duplicateDone.await(15, TimeUnit.SECONDS))
            instrumentationWaitForGlIdle()
            assertEquals(2, readObjectCountOnGl(glView))
            val trianglesBefore = (0..1).map { readTriangleCountOnGl(glView, it) }

            glView.selectModel(0, Runnable {})
            instrumentationWaitForGlIdle()
            val captured = AtomicReference<Pair<ru.ytkab0bp.slicebeam.render.ModelSelectionSnapshot, Int>?>()
            val capturedDone = CountDownLatch(1)
            bed.captureSelectedObjectForSimplification { snapshot, triangleCount ->
                captured.set(snapshot to triangleCount)
                capturedDone.countDown()
            }
            assertTrue("simplify dialog should capture its native target on GL",
                capturedDone.await(10, TimeUnit.SECONDS))
            assertTrue("captured simplify selection should refer to the first object",
                captured.get()!!.first.primaryObject == 0 && captured.get()!!.second == trianglesBefore[0])

            // The delayed confirmation is tied to object 0. Switching selection must not redirect
            // the native edit to object 1, even though both objects are valid and have same mesh.
            val switchedSelection = CountDownLatch(1)
            glView.selectModel(1, Runnable { switchedSelection.countDown() })
            assertTrue("selection should switch while the simplify prompt is open",
                switchedSelection.await(10, TimeUnit.SECONDS))
            assertEquals("the newer selection should be object 1", 1, readSelectedObjectOnGl(glView))
            bed.simplifySelectedObject(requireNotNull(captured.get()).first, 0.5f)
            instrumentationWaitForGlIdle()
            assertEquals("stale simplify confirmation must leave object 0 unchanged",
                trianglesBefore[0], readTriangleCountOnGl(glView, 0))
            assertEquals("stale simplify confirmation must not mutate newly selected object 1",
                trianglesBefore[1], readTriangleCountOnGl(glView, 1))
            assertEquals("stale selection must not change object count", 2, model.objectsCount)
        } finally {
            modelFile.delete()
        }
    }

    private fun loadSingleTestModel(bed: BedFragment, modelFile: File): Model {
        val testGlView = requireNotNull(bed.glView)
        compose.waitUntil(timeoutMillis = 45_000) {
            testGlView.isAttachedToWindow && testGlView.width > 0 && testGlView.height > 0 &&
                testGlView.renderer.bed != null
        }
        val done = CountDownLatch(1)
        val loaded = AtomicReference<Model?>()
        val error = AtomicReference<Exception?>()
        bed.loadModel(modelFile, false, 1, object : BedFragment.ModelLoadCallback {
            override fun onLoaded(loadedModel: Model, firstNewObject: Int, addedObjects: Int) {
                loaded.set(loadedModel)
                done.countDown()
            }

            override fun onFailed(failure: Exception) {
                error.set(failure)
                done.countDown()
            }
        })
        assertTrue("temporary test model should finish loading", done.await(60, TimeUnit.SECONDS))
        return requireNotNull(loaded.get()) { "temporary model failed: ${error.get()}" }
    }

    private fun awaitPreSliceDialog(bed: BedFragment, tag: String) {
        val waitFailure = runCatching {
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
            }
        }.exceptionOrNull() ?: return
        val controller = BedFragment::class.java.getDeclaredField("workspaceController")
            .apply { isAccessible = true }.get(bed)
        val prepared = BedFragment::class.java.getDeclaredField("preparedFilamentModel")
            .apply { isAccessible = true }.get(bed)
        val visibleTags = listOf("pre-slice-title", "pre-slice-confirm", "workspace-mode-panel")
            .associateWith { name -> compose.onAllNodesWithTag(name).fetchSemanticsNodes().size }
        val descriptions = compose.onAllNodes(
            androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription),
            useUnmergedTree = true,
        ).fetchSemanticsNodes().mapNotNull { node ->
            runCatching { node.config[SemanticsProperties.ContentDescription].joinToString() }.getOrNull()
        }
        throw AssertionError(
            "Pre-slice UI did not appear: target=$tag activePanel=${controller.javaClass
                .getMethod("getActivePanel").invoke(controller)} hasSlice=${bed.hasSliceResult()} " +
                "preparedCurrentModel=${prepared === bed.currentModel} tags=$visibleTags " +
                "descriptions=$descriptions",
            waitFailure,
        )
    }

    private fun awaitWorkspaceDialogTag(bed: BedFragment, tag: String) {
        val waitFailure = runCatching {
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
            }
        }.exceptionOrNull() ?: return
        val controller = BedFragment::class.java.getDeclaredField("workspaceController")
            .apply { isAccessible = true }.get(bed)
        val activePanel = controller.javaClass.getMethod("getActivePanel").invoke(controller)
        val activeContent = controller.javaClass.getMethod("getActivePanelContent").invoke(controller)
        val tags = listOf("slice-info-title", "slice-layers-title", "slice-layers-range-title")
            .associateWith { name -> compose.onAllNodesWithTag(name).fetchSemanticsNodes().size }
        val descriptions = compose.onAllNodes(
            androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription),
            useUnmergedTree = true,
        ).fetchSemanticsNodes().mapNotNull { node ->
            runCatching { node.config[SemanticsProperties.ContentDescription].joinToString() }.getOrNull()
        }
        throw AssertionError(
            "Workspace dialog '$tag' did not appear: activePanel=$activePanel " +
                "hasContent=${activeContent != null} tags=$tags descriptions=$descriptions",
            waitFailure,
        )
    }

    private fun waitForWorkspaceMenuGrid() {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("workspace-menu-actions-grid").fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Creates a standard binary STL containing two closed copies separated by 30 mm on X. */
    private fun writeTwoDisconnectedCubesStl(
        activity: MainActivity,
        destination: File,
        separationMm: Float = 30f,
        scaleY: Float = 1f,
    ) {
        val source = activity.assets.open("models/xyz_cube.stl").use { it.readBytes() }
        assertTrue("bundled STL must have a binary STL header", source.size >= 84)
        val triangleCount = ByteBuffer.wrap(source, 80, 4).order(ByteOrder.LITTLE_ENDIAN).int
        assertTrue("bundled STL triangle count must match its binary payload", triangleCount > 0 &&
            source.size == 84 + triangleCount * 50)

        val output = ByteBuffer.allocate(84 + triangleCount * 2 * 50).order(ByteOrder.LITTLE_ENDIAN)
        output.put(source, 0, 80)
        output.putInt(triangleCount * 2)
        output.put(source, 84, triangleCount * 50)
        val firstCube = output.array()
        val firstData = ByteBuffer.wrap(firstCube).order(ByteOrder.LITTLE_ENDIAN)
        repeat(triangleCount) { triangleIndex ->
            val base = 84 + triangleIndex * 50
            listOf(12, 24, 36).forEach { vertexOffset ->
                firstData.putFloat(base + vertexOffset + 4, firstData.getFloat(base + vertexOffset + 4) * scaleY)
            }
        }
        repeat(triangleCount) { triangleIndex ->
            val triangleOffset = 84 + triangleIndex * 50
            val translated = source.copyOfRange(triangleOffset, triangleOffset + 50)
            val record = ByteBuffer.wrap(translated).order(ByteOrder.LITTLE_ENDIAN)
            listOf(12, 24, 36).forEach { vertexXOffset ->
                record.putFloat(vertexXOffset, record.getFloat(vertexXOffset) + separationMm)
                record.putFloat(vertexXOffset + 4, record.getFloat(vertexXOffset + 4) * scaleY)
            }
            output.put(translated)
        }
        destination.writeBytes(output.array())
    }

    private fun writeScaledCubeStl(activity: MainActivity, destination: File, scale: Float) {
        val source = activity.assets.open("models/xyz_cube.stl").use { it.readBytes() }
        val triangleCount = ByteBuffer.wrap(source, 80, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val bytes = source.copyOf()
        val data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        repeat(triangleCount) { triangle ->
            val base = 84 + triangle * 50
            listOf(12, 24, 36).forEach { vertex ->
                repeat(3) { axis ->
                    val offset = base + vertex + axis * 4
                    data.putFloat(offset, data.getFloat(offset) * scale)
                }
            }
        }
        destination.writeBytes(bytes)
    }

    private fun writeScaledBoxStl(
        activity: MainActivity,
        destination: File,
        scaleX: Float,
        scaleY: Float,
        scaleZ: Float,
    ) {
        val source = activity.assets.open("models/xyz_cube.stl").use { it.readBytes() }
        val triangleCount = ByteBuffer.wrap(source, 80, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val bytes = source.copyOf()
        val data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val scales = floatArrayOf(scaleX, scaleY, scaleZ)
        repeat(triangleCount) { triangle ->
            val base = 84 + triangle * 50
            listOf(12, 24, 36).forEach { vertex ->
                repeat(3) { axis ->
                    val offset = base + vertex + axis * 4
                    data.putFloat(offset, data.getFloat(offset) * scales[axis])
                }
            }
        }
        destination.writeBytes(bytes)
    }

    /** Writes a watertight 150 x 150 x 20 mm L-shaped prism as binary STL. */
    private fun writeConcavePrismStl(
        activity: MainActivity,
        destination: File,
        openBottom: Boolean = false,
        subdivisionLevels: Int = 0,
    ) {
        val points = listOf(
            0f to 0f, 150f to 0f, 150f to 25f,
            25f to 25f, 25f to 150f, 0f to 150f,
        )
        val remaining = points.indices.toMutableList()
        val capTriangles = ArrayList<Triple<Int, Int, Int>>()
        fun cross(a: Int, b: Int, c: Int): Float {
            val abx = points[b].first - points[a].first
            val aby = points[b].second - points[a].second
            val acx = points[c].first - points[a].first
            val acy = points[c].second - points[a].second
            return abx * acy - aby * acx
        }
        fun insideTriangle(p: Int, a: Int, b: Int, c: Int): Boolean =
            cross(a, b, p) >= -0.001f && cross(b, c, p) >= -0.001f && cross(c, a, p) >= -0.001f

        while (remaining.size > 3) {
            val ear = remaining.indices.firstOrNull { position ->
                val a = remaining[(position - 1 + remaining.size) % remaining.size]
                val b = remaining[position]
                val c = remaining[(position + 1) % remaining.size]
                cross(a, b, c) > 0f && remaining.none { candidate ->
                    candidate != a && candidate != b && candidate != c && insideTriangle(candidate, a, b, c)
                }
            } ?: error("L polygon triangulation failed")
            val a = remaining[(ear - 1 + remaining.size) % remaining.size]
            val b = remaining[ear]
            val c = remaining[(ear + 1) % remaining.size]
            capTriangles += Triple(a, b, c)
            remaining.removeAt(ear)
        }
        capTriangles += Triple(remaining[0], remaining[1], remaining[2])

        data class Point3(val x: Float, val y: Float, val z: Float)
        fun point(index: Int, top: Boolean): Point3 {
            val p = points[index]
            return Point3(p.first, p.second, if (top) 20f else 0f)
        }
        val triangles = ArrayList<Triple<Point3, Point3, Point3>>()
        capTriangles.forEach { (a, b, c) ->
            if (!openBottom) triangles += Triple(point(a, false), point(c, false), point(b, false))
            triangles += Triple(point(a, true), point(b, true), point(c, true))
        }
        points.indices.forEach { a ->
            val b = (a + 1) % points.size
            val bottomA = point(a, false)
            val bottomB = point(b, false)
            val topA = point(a, true)
            val topB = point(b, true)
            triangles += Triple(bottomA, bottomB, topB)
            triangles += Triple(bottomA, topB, topA)
        }

        fun midpoint(a: Point3, b: Point3) = Point3(
            (a.x + b.x) * 0.5f, (a.y + b.y) * 0.5f, (a.z + b.z) * 0.5f,
        )
        var detailedTriangles: List<Triple<Point3, Point3, Point3>> = triangles
        repeat(subdivisionLevels.coerceIn(0, 6)) {
            detailedTriangles = detailedTriangles.flatMap { (a, b, c) ->
                val ab = midpoint(a, b)
                val bc = midpoint(b, c)
                val ca = midpoint(c, a)
                listOf(
                    Triple(a, ab, ca), Triple(ab, b, bc),
                    Triple(ca, bc, c), Triple(ab, bc, ca),
                )
            }
        }

        val output = ByteBuffer.allocate(84 + detailedTriangles.size * 50).order(ByteOrder.LITTLE_ENDIAN)
        output.put("Orkan concave footprint test".toByteArray().copyOf(80))
        output.putInt(detailedTriangles.size)
        detailedTriangles.forEach { (a, b, c) ->
            repeat(3) { output.putFloat(0f) }
            listOf(a, b, c).forEach { p ->
                output.putFloat(p.x)
                output.putFloat(p.y)
                output.putFloat(p.z)
            }
            output.putShort(0)
        }
        destination.writeBytes(output.array())
    }

    @Suppress("UNCHECKED_CAST")
    private fun readPlateModels(bed: BedFragment): List<Model> =
        BedFragment::class.java.getDeclaredField("platesModels").apply { isAccessible = true }
            .get(bed).let { it as List<Model?> }.filterNotNull()

    private fun scrollMenuToIndex(index: Int) {
        // Compact portrait menus use a two-row LazyHorizontalGrid. Wide and landscape
        // layouts use a vertically scrollable action column, where individual actions
        // are brought into view with performScrollTo() at the call site.
        if (usesCompactPortraitLayout(compose.activity)) {
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(index)
        }
    }

    /** Uses the visible horizontal action paging, matching how users reach off-screen menu tiles. */
    private fun revealMenuAction(activity: MainActivity, titleRes: Int) {
        val title = activity.getString(titleRes)
        if (!usesCompactPortraitLayout(activity)) {
            compose.onNodeWithContentDescription(title).performScrollTo().assertIsDisplayed()
            return
        }
        val tileTag = "workspace-menu-action-$titleRes"
        repeat(24) {
            val action = compose.onAllNodesWithContentDescription(title)
                .fetchSemanticsNodes().firstOrNull()
            val tile = compose.onAllNodesWithTag(tileTag).fetchSemanticsNodes().firstOrNull()
            val panel = compose.onAllNodesWithTag("workspace-menu-panel")
                .fetchSemanticsNodes().firstOrNull()
            val pagingAffordanceVisible = compose.onAllNodesWithContentDescription(
                activity.getString(R.string.WorkspaceMoreTitle),
            ).fetchSemanticsNodes().isNotEmpty()
            val visiblePanelRight = panel?.boundsInWindow?.right?.minus(
                if (pagingAffordanceVisible) 52f * activity.resources.displayMetrics.density else 0f,
            ) ?: Float.NEGATIVE_INFINITY
            val geometryLooksVisible = action != null && tile != null && panel != null &&
                tile.boundsInWindow.left >= panel.boundsInWindow.left &&
                tile.boundsInWindow.top >= panel.boundsInWindow.top &&
                tile.boundsInWindow.width >= 48f * activity.resources.displayMetrics.density &&
                tile.boundsInWindow.height >= 48f * activity.resources.displayMetrics.density &&
                // Reserve space only while the paging affordance is actually present. When a
                // menu fits on one page, the final action may use the panel's right-hand area.
                tile.boundsInWindow.right <= visiblePanelRight &&
                tile.boundsInWindow.bottom <= panel.boundsInWindow.bottom
            if (geometryLooksVisible && runCatching {
                    compose.onNodeWithContentDescription(title).assertIsDisplayed()
                }.isSuccess
            ) return
            val grid = compose.onNodeWithTag("workspace-menu-actions-grid")
            val pageDescription = activity.getString(R.string.WorkspaceMoreTitle)
            if (compose.onAllNodesWithContentDescription(pageDescription)
                    .fetchSemanticsNodes().isNotEmpty()
            ) {
                // Use the same page-sized accessible control exposed to users. Small synthetic
                // swipes can stop inside a large-font tile and leave the target clipped behind
                // the fixed paging affordance.
                compose.onNodeWithContentDescription(pageDescription).performClick()
            } else {
                val gridWidth = grid.fetchSemanticsNode().size.width.toFloat()
                grid.performTouchInput {
                    swipeLeft(startX = gridWidth * 0.65f, endX = gridWidth * 0.35f)
                }
            }
            compose.waitForIdle()
        }
        val action = compose.onAllNodesWithContentDescription(title)
            .fetchSemanticsNodes().firstOrNull()
        val tile = compose.onAllNodesWithTag(tileTag).fetchSemanticsNodes().firstOrNull()
        val panel = compose.onAllNodesWithTag("workspace-menu-panel")
            .fetchSemanticsNodes().firstOrNull()
        val descriptions = compose.onAllNodes(
            androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription),
            useUnmergedTree = true,
        ).fetchSemanticsNodes().mapNotNull { node ->
            runCatching { node.config[SemanticsProperties.ContentDescription].joinToString() }.getOrNull()
        }
        throw AssertionError(
            "Menu action '$title' did not become fully visible inside its panel; " +
            "action=${action?.boundsInWindow}, tile=${tile?.boundsInWindow}, panel=${panel?.boundsInWindow}, " +
                "grid=${compose.onAllNodesWithTag("workspace-menu-actions-grid").fetchSemanticsNodes().size}, " +
                "visible descriptions=$descriptions",
        )
    }

    private fun revealPaintFilamentSwatch(
        activity: MainActivity,
        index: Int,
        material: String,
        occurrence: Int,
    ): androidx.compose.ui.test.SemanticsNodeInteraction {
        val description = activity.getString(R.string.FilamentPaletteSlotDescription, index, material)
        val swatches = compose.onAllNodesWithContentDescription(description)
        compose.waitUntil(timeoutMillis = 5_000) { swatches.fetchSemanticsNodes().size > occurrence }
        repeat(6) {
            val target = swatches.get(occurrence)
            if (runCatching { target.assertIsDisplayed() }.isSuccess) return target
            compose.onNodeWithTag("workspace-mode-panel")
                .performTouchInput { swipeUp(startY = height * 0.8f, endY = height * 0.3f) }
            compose.waitForIdle()
        }
        swatches.get(occurrence).assertIsDisplayed()
        return swatches.get(occurrence)
    }

    private fun openWorkspaceMenu(
        activity: MainActivity,
        destination: WorkspaceDestination,
        labelRes: Int,
    ) {
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val controller = BedFragment::class.java.getDeclaredField("workspaceController")
            .apply { isAccessible = true }.get(bed)
        val destinationSlot = destination.ordinal
        val compactPortrait = usesCompactPortraitLayout(activity)
        compose.waitUntil(timeoutMillis = 5_000) {
            !(controller.javaClass.getMethod("isNavigationTransitionInProgress").invoke(controller) as Boolean)
        }
        val needsSelection = controller.javaClass.getMethod("getSelectedDestinationSlot")
            .invoke(controller) != destinationSlot
        val needsExpansion = compactPortrait &&
            !(controller.javaClass.getMethod("getToolPanelExpanded").invoke(controller) as Boolean)
        if (needsSelection || needsExpansion) {
            val destinationNode = compose.onNodeWithContentDescription(activity.getString(labelRes))
            val destinationVisible = runCatching {
                destinationNode.assertIsDisplayed()
                true
            }.getOrDefault(false)
            if (!compactPortrait && !destinationVisible) {
                destinationNode.performScrollTo()
            }
            destinationNode.assertIsDisplayed().performClick()
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            controller.javaClass.getMethod("getSelectedDestinationSlot").invoke(controller) == destinationSlot &&
                (!compactPortrait || controller.javaClass.getMethod("getToolPanelExpanded").invoke(controller) as Boolean) &&
                !(controller.javaClass.getMethod("isNavigationTransitionInProgress").invoke(controller) as Boolean)
        }
        compose.waitForIdle()
    }

    private fun openEditMenu(activity: MainActivity) {
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val controller = BedFragment::class.java.getDeclaredField("workspaceController")
            .apply { isAccessible = true }.get(bed)
        compose.waitUntil(timeoutMillis = 5_000) {
            !(controller.javaClass.getMethod("isNavigationTransitionInProgress").invoke(controller) as Boolean)
        }
        val editSlot = WorkspaceDestination.EDIT.ordinal
        val shouldNavigate = controller.javaClass.getMethod("getSelectedDestinationSlot")
            .invoke(controller) != editSlot
        val compactPortrait = usesCompactPortraitLayout(activity)
        val shouldExpand = compactPortrait && !(controller.javaClass.getMethod("getToolPanelExpanded").invoke(controller) as Boolean)
        if (shouldNavigate || shouldExpand) {
            val destinationNode = compose.onNodeWithContentDescription(
                activity.getString(R.string.MenuToolbarNavigation),
            )
            if (!compactPortrait) destinationNode.performScrollTo()
            destinationNode.assertIsDisplayed().performClick()
            try {
                compose.waitUntil(timeoutMillis = 5_000) {
                    controller.javaClass.getMethod("getSelectedDestinationSlot").invoke(controller) == editSlot &&
                        (!compactPortrait || controller.javaClass.getMethod("getToolPanelExpanded").invoke(controller) == true) &&
                        !(controller.javaClass.getMethod("isNavigationTransitionInProgress").invoke(controller) as Boolean)
                }
            } catch (error: androidx.compose.ui.test.ComposeTimeoutException) {
                val selected = controller.javaClass.getMethod("getSelectedDestinationSlot").invoke(controller)
                val expanded = controller.javaClass.getMethod("getToolPanelExpanded").invoke(controller)
                val transitioning = controller.javaClass.getMethod("isNavigationTransitionInProgress").invoke(controller)
                val activeTool = controller.javaClass.getMethod("getActiveToolId").invoke(controller)
                throw AssertionError(
                    "Edit navigation did not settle after painting: selected=$selected expected=$editSlot " +
                        "expanded=$expanded compactPortrait=$compactPortrait transitioning=$transitioning activeTool=$activeTool",
                    error,
                )
            }
        }
        if (compactPortrait) scrollMenuToIndex(1)
    }

    private fun openModelMenu(activity: MainActivity) {
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val controller = BedFragment::class.java.getDeclaredField("workspaceController")
            .apply { isAccessible = true }.get(bed)
        compose.waitUntil(timeoutMillis = 5_000) {
            !(controller.javaClass.getMethod("isNavigationTransitionInProgress").invoke(controller) as Boolean)
        }
        val modelSlot = WorkspaceDestination.MODEL.ordinal
        val shouldNavigate = controller.javaClass.getMethod("getSelectedDestinationSlot")
            .invoke(controller) != modelSlot
        val compactPortrait = usesCompactPortraitLayout(activity)
        val shouldExpand = compactPortrait && !(controller.javaClass.getMethod("getToolPanelExpanded").invoke(controller) as Boolean)
        if (shouldNavigate || shouldExpand) {
            val destinationNode = compose.onNodeWithContentDescription(activity.getString(R.string.MenuModel))
            if (!compactPortrait) destinationNode.performScrollTo()
            destinationNode.assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 15_000) {
                controller.javaClass.getMethod("getSelectedDestinationSlot").invoke(controller) == modelSlot &&
                    (!compactPortrait || controller.javaClass.getMethod("getToolPanelExpanded").invoke(controller) == true) &&
                    !(controller.javaClass.getMethod("isNavigationTransitionInProgress").invoke(controller) as Boolean)
            }
        }
    }

    private fun usesCompactPortraitLayout(activity: MainActivity): Boolean {
        val configuration = activity.resources.configuration
        return AdaptiveNavigationPolicy.usesCompactPortraitLayout(
            configuration.screenWidthDp,
            configuration.screenHeightDp,
            configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT,
        )
    }

    private fun selectAllObjectsScope() {
        compose.onNodeWithTag("model-menu-scope-all").performClick()
        compose.waitForIdle()
    }

    private fun selectSingleObjectScope() {
        compose.onNodeWithTag("model-menu-scope-object").performClick()
        compose.waitForIdle()
    }

    private fun openSelectedObjectMenu(activity: MainActivity) {
        openModelMenu(activity)
        selectSingleObjectScope()
    }

    @Test
    fun modelObjectsMenuAssignmentOverridesImportedThreeMfMaterial() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val file = File(activity.cacheDir, "menu-filament-assignment-test.3mf")
        val assignedProject = File(activity.cacheDir, "menu-filament-assignment-roundtrip.3mf")
        InstrumentationRegistry.getInstrumentation().context.assets.open("models/two-color-project.3mf").use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        val previousSlots = Prefs.getStoredFilamentSlots().map { FilamentSlot(it.color, it.type) }
        val testSlots = listOf(
            FilamentSlot(0xFFCC3333.toInt(), "UI Test PLA"),
            FilamentSlot(0xFF3366CC.toInt(), "UI Test PETG"),
            FilamentSlot(0xFF33AA66.toInt(), "UI Test ABS"),
            FilamentSlot(0xFFFFCC22.toInt(), "UI Test TPU"),
            FilamentSlot(0xFF9933CC.toInt(), "UI Test Slot 5"),
            FilamentSlot(0xFFFF6633.toInt(), "UI Test Slot 6"),
            FilamentSlot(0xFF33CCCC.toInt(), "UI Test Slot 7"),
            FilamentSlot(0xFFF2F2F2.toInt(), "UI Test Slot 8"),
        )
        Prefs.setStoredFilamentSlots(testSlots)
        SliceBeam.INSTANCE = activity.applicationContext as SliceBeam
        Prefs.init(activity.applicationContext as SliceBeam)
        TemporaryProjectSettings.clear()
        val previousConfig = SliceBeam.CONFIG
        val configFile = SliceBeam.getCurrentConfigFile()
        val previousConfigFile = configFile.takeIf { it.isFile }?.readBytes()
        val prefs = requireNotNull(Prefs.getPrefs())
        val snapshotKeys = listOf("mmu_snapshot_printer", "mmu_snapshot")
        val previousSnapshots = snapshotKeys.associateWith { prefs.getString(it, null) }
        val output = File(activity.cacheDir, "menu-multi-object-filament-${System.nanoTime()}.gcode")
        var sliceResult: GCodeProcessorResult? = null
        var roundTrippedModel: Model? = null
        try {
            val loadedLatch = CountDownLatch(1)
            var loaded: Model? = null
            var loadFailure: Exception? = null
            bed.loadModel(file, true, 1, object : BedFragment.ModelLoadCallback {
                override fun onLoaded(loadedModel: Model, firstNewObject: Int, addedObjects: Int) {
                    loaded = loadedModel
                    loadedLatch.countDown()
                }

                override fun onFailed(error: Exception) {
                    loadFailure = error
                    loadedLatch.countDown()
                }
            })
            assertTrue("color 3MF fixture should load", loadedLatch.await(60, TimeUnit.SECONDS))
            assertEquals("color fixture load failure: $loadFailure", 2, requireNotNull(loaded).objectsCount)
            assertTrue("fixture begins with imported tool 1", loaded!!.getUsedFilaments(0).contentEquals(intArrayOf(1)))

            fun assignObject(index: Int, slot: Int) {
                openModelMenu(activity)
                compose.onNodeWithText(activity.getString(R.string.ModelMenuObjectsShort))
                    .assertIsDisplayed().performClick()
                compose.onAllNodesWithText(activity.getString(R.string.ObjectActionAssignFilamentShort))[index]
                    .assertIsDisplayed().performClick()
                val targetLabel = activity.getString(
                    R.string.ModelContextFilamentSlot,
                    slot,
                    testSlots[slot].type,
                    String.format(java.util.Locale.ROOT, "#%06X", testSlots[slot].color and 0xFFFFFF),
                )
                compose.onNodeWithTag("model-choice-dialog-options")
                    .performScrollToNode(hasText(targetLabel))
                compose.onNodeWithText(targetLabel).assertIsDisplayed().performClick()
                compose.waitUntil(timeoutMillis = 10_000) {
                    bed.currentModel?.getUsedFilaments(index)?.contentEquals(intArrayOf(slot + 1)) == true
                }
            }

            // Assign different own slots through the real object-list UI, one object at a time.
            assignObject(index = 0, slot = 6)
            assertArrayEquals("assigning object 1 must leave object 2 on its imported tool",
                intArrayOf(2), requireNotNull(bed.currentModel).getUsedFilaments(1))
            assignObject(index = 1, slot = 3)
            val assignedModel = requireNotNull(bed.currentModel)
            assertArrayEquals("object 1 should independently use own T6", intArrayOf(7), assignedModel.getUsedFilaments(0))
            assertArrayEquals("object 2 should independently use own T3", intArrayOf(4), assignedModel.getUsedFilaments(1))
            assertFalse("uniform reassignment removes old per-volume paint overrides",
                assignedModel.hasPaint(0))

            // Export the UI-assigned renderer model and verify the per-object slots survive reload
            // before checking the G-code tools and declared palette.
            activity.assets.open("orca_profiles/Anycubic.ini").use { SliceBeam.CONFIG = Slic3rConfigWrapper(it) }
            SliceBeam.requireConfig().presets = ConfigObject("presets").apply {
                put("printer", "Anycubic Kobra 3 0.4 nozzle")
                put("print", "0.16mm Standard")
                put("filament", "Anycubic Generic PLA")
            }
            val editor = prefs.edit()
            snapshotKeys.forEach(editor::remove)
            editor.commit()
            SliceBeam.genCurrentConfig(assignedModel)
            assignedModel.export3mf(configFile.absolutePath, assignedProject.absolutePath)
            assertTrue("per-object filament assignment should export as a non-empty 3MF",
                assignedProject.isFile && assignedProject.length() > 0L)
            roundTrippedModel = Model(assignedProject, 1)
            val slicedModel = requireNotNull(roundTrippedModel)
            assertEquals("the assignment round-trip should keep both model objects", 2, slicedModel.objectsCount)
            assertArrayEquals("the first object's selected T6 should survive project reload", intArrayOf(7),
                slicedModel.getUsedFilaments(0))
            assertArrayEquals("the second object's selected T3 should survive project reload", intArrayOf(4),
                slicedModel.getUsedFilaments(1))
            SliceBeam.genCurrentConfig(slicedModel)
            val expectedPalette = testSlots.take(7).joinToString(";") {
                String.format(java.util.Locale.ROOT, "#%06X", it.color and 0xFFFFFF)
            }
            val generatedConfig = configFile.readText()
            assertTrue("slice config must include all seven slots through assigned T6",
                Regex("(?m)^filament_colour = ${Regex.escape(expectedPalette)}$").containsMatchIn(generatedConfig))
            assertFalse("bundled Anycubic Generic PLA start G-code must not contain unsupported, commented placeholders",
                generatedConfig.contains("filament_start_gcode = ") &&
                    Regex("(?m)^filament_start_gcode = .*\\{if").containsMatchIn(generatedConfig))
            sliceResult = slicedModel.slice(configFile.absolutePath, output.absolutePath, null)
            assertTrue("the multicolor object slice should create non-empty G-code", output.isFile && output.length() > 0)
            val gcode = output.readText()
            val emittedTools = Regex("(?m)^T(\\d+)(?:\\s|$)").findAll(gcode)
                .map { it.groupValues[1].toInt() }.toSet()
            assertTrue("G-code must emit object 1's assigned T6, tools=$emittedTools", 6 in emittedTools)
            assertTrue("G-code must emit object 2's assigned T3, tools=$emittedTools", 3 in emittedTools)
            assertTrue("G-code preview palette must match all seven required slot colors",
                Regex("(?m)^; filament_colour = ${Regex.escape(expectedPalette)}$").containsMatchIn(gcode))
        } finally {
            sliceResult?.release()
            roundTrippedModel?.release()
            output.delete()
            assignedProject.delete()
            val editor = prefs.edit()
            previousSnapshots.forEach { (key, value) -> if (value == null) editor.remove(key) else editor.putString(key, value) }
            editor.commit()
            SliceBeam.CONFIG = previousConfig
            if (previousConfigFile == null) configFile.delete() else configFile.writeBytes(previousConfigFile)
            TemporaryProjectSettings.clear()
            Prefs.setStoredFilamentSlots(previousSlots)
            file.delete()
        }
    }

    @Test
    fun preSliceCanChooseDifferentSlotsForEachObject() {
        val activity = compose.activity
        val bed = activity.navigationDelegate.getCurrentFragment() as BedFragment
        val file = File(activity.cacheDir, "pre-slice-per-object-assignment.3mf")
        InstrumentationRegistry.getInstrumentation().context.assets.open("models/two-color-project.3mf").use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        val previousSlots = Prefs.getStoredFilamentSlots().map { FilamentSlot(it.color, it.type) }
        val testSlots = listOf(
            FilamentSlot(0xFFCC3333.toInt(), "Test PLA"),
            FilamentSlot(0xFF3366CC.toInt(), "Test PETG"),
            FilamentSlot(0xFF33AA66.toInt(), "Test ABS"),
            FilamentSlot(0xFFFFCC22.toInt(), "Test TPU"),
        )
        Prefs.setStoredFilamentSlots(testSlots)
        try {
            val loadedLatch = CountDownLatch(1)
            val loadFailure = AtomicReference<Exception?>()
            bed.loadModel(file, true, 1, object : BedFragment.ModelLoadCallback {
                override fun onLoaded(loadedModel: Model, firstNewObject: Int, addedObjects: Int) {
                    loadedLatch.countDown()
                }

                override fun onFailed(error: Exception) {
                    loadFailure.set(error)
                    loadedLatch.countDown()
                }
            })
            assertTrue("two-color project import should finish", loadedLatch.await(60, TimeUnit.SECONDS))
            assertEquals("two-color project import failure: ${loadFailure.get()}", 2,
                requireNotNull(bed.currentModel).objectsCount)

            val selectedSlots = AtomicReference<IntArray?>()
            PreSliceFilamentDialog.show(
                bed,
                requireNotNull(bed.currentModel),
                PreSliceFilamentDialog.Callback { selectedSlots.set(it) },
            )
            compose.onNodeWithTag("pre-slice-title").assertIsDisplayed()
            compose.onNodeWithText(activity.getString(R.string.PreSliceIndividualMode))
                .performScrollTo().assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 5_000) {
                compose.onAllNodesWithTag("pre-slice-object-slot-0").fetchSemanticsNodes().isNotEmpty()
            }

            // Object 1 -> slot 4/T3, object 2 -> slot 2/T1.
            compose.onNodeWithTag("pre-slice-object-slot-0").performScrollTo().performClick()
            compose.onNodeWithTag("pre-slice-object-0-filament-3").performScrollTo().performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("pre-slice-object-slot-1").performScrollTo().performClick()
            compose.onNodeWithTag("pre-slice-object-1-filament-1").performScrollTo().performClick()
            compose.onNodeWithTag("pre-slice-confirm").performClick()
            assertArrayEquals("first object maps to slot 4", intArrayOf(3, 1), selectedSlots.get())
            assertArrayEquals("choosing assignments is staged until the user confirms the slice",
                intArrayOf(1), requireNotNull(bed.currentModel).getUsedFilaments(0))
        } finally {
            Prefs.setStoredFilamentSlots(previousSlots)
            file.delete()
        }
    }

    private fun instrumentationWaitForGlIdle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        SystemClock.sleep(250L)
    }

    private fun readGCodeOptionOnGl(glView: ru.ytkab0bp.slicebeam.view.GLView, option: Int): Boolean {
        val finished = CountDownLatch(1)
        val visible = AtomicReference<Boolean>()
        glView.queueEvent {
            visible.set(requireNotNull(glView.renderer.viewer).isOptionVisible(option))
            finished.countDown()
        }
        assertTrue("GL thread did not return G-code option visibility", finished.await(10, TimeUnit.SECONDS))
        return requireNotNull(visible.get())
    }

    private fun readScaleOnGl(glView: ru.ytkab0bp.slicebeam.view.GLView, index: Int): Vec3d {
        val finished = CountDownLatch(1)
        val scale = AtomicReference<Vec3d?>()
        val failure = AtomicReference<Throwable?>()
        glView.queueEvent {
            try {
                val model = requireNotNull(glView.renderer.model)
                check(model.pointer != 0L) { "Renderer returned a released native model" }
                check(index in 0 until model.objectsCount) {
                    "Renderer model has ${model.objectsCount} objects; requested scale for index $index"
                }
                scale.set(Vec3d().also { model.getScale(index, it) })
            } catch (error: Throwable) {
                failure.set(error)
            } finally {
                finished.countDown()
            }
        }
        assertTrue("GL thread did not return model scale", finished.await(10, TimeUnit.SECONDS))
        failure.get()?.let { throw AssertionError("Could not read model scale on GL thread", it) }
        return requireNotNull(scale.get())
    }

    private fun readTranslationOnGl(glView: ru.ytkab0bp.slicebeam.view.GLView, index: Int): Vec3d =
        readVectorOnGl(glView, index) { model, vector -> model.getTranslation(index, vector) }

    private fun readRotationOnGl(glView: ru.ytkab0bp.slicebeam.view.GLView, index: Int): Vec3d =
        readVectorOnGl(glView, index) { model, vector -> model.getRotation(index, vector) }

    private fun readSelectedObjectOnGl(glView: ru.ytkab0bp.slicebeam.view.GLView): Int {
        val finished = CountDownLatch(1)
        val selected = AtomicReference<Int>()
        glView.queueEvent {
            selected.set(glView.renderer.selectedObject)
            finished.countDown()
        }
        assertTrue("GL thread did not return selected object", finished.await(10, TimeUnit.SECONDS))
        return requireNotNull(selected.get())
    }

    private fun readSelectedObjectsCountOnGl(glView: ru.ytkab0bp.slicebeam.view.GLView): Int {
        val finished = CountDownLatch(1)
        val selected = AtomicReference<Int>()
        glView.queueEvent {
            selected.set(glView.renderer.selectedObjectsCount)
            finished.countDown()
        }
        assertTrue("GL thread did not return selected object count", finished.await(10, TimeUnit.SECONDS))
        return requireNotNull(selected.get())
    }

    private fun readSelectedIndicesOnGl(glView: ru.ytkab0bp.slicebeam.view.GLView): IntArray {
        val finished = CountDownLatch(1)
        val selected = AtomicReference<IntArray>()
        glView.queueEvent {
            selected.set(glView.renderer.selectedObjectIndices())
            finished.countDown()
        }
        assertTrue("GL thread did not return selected object indices", finished.await(10, TimeUnit.SECONDS))
        return requireNotNull(selected.get())
    }

    private fun readExactBoundsOnGl(
        glView: ru.ytkab0bp.slicebeam.view.GLView,
        vararg indices: Int,
    ): List<DoubleArray> {
        val finished = CountDownLatch(1)
        val bounds = AtomicReference<List<DoubleArray>>()
        glView.queueEvent {
            val model = requireNotNull(glView.renderer.model)
            bounds.set(indices.map { index ->
                val min = ru.ytkab0bp.slicebeam.utils.Vec3d()
                val max = ru.ytkab0bp.slicebeam.utils.Vec3d()
                model.getBoundingBoxExact(index, min, max)
                doubleArrayOf(min.x, min.y, min.z, max.x, max.y, max.z)
            })
            finished.countDown()
        }
        assertTrue("GL thread did not return exact bounds", finished.await(10, TimeUnit.SECONDS))
        return requireNotNull(bounds.get())
    }

    private fun readBuildVolumeBoundsOnGl(
        glView: ru.ytkab0bp.slicebeam.view.GLView,
    ): DoubleArray {
        val finished = CountDownLatch(1)
        val bounds = AtomicReference<DoubleArray>()
        glView.queueEvent {
            val bed = requireNotNull(glView.renderer.bed)
            val min = requireNotNull(bed.getVolumeMin())
            val max = requireNotNull(bed.getVolumeMax())
            bounds.set(doubleArrayOf(min.x, min.y, min.z, max.x, max.y, max.z))
            finished.countDown()
        }
        assertTrue("GL thread did not return build-volume bounds", finished.await(10, TimeUnit.SECONDS))
        return requireNotNull(bounds.get())
    }

    private fun readExactBoundsOnGl(
        glView: ru.ytkab0bp.slicebeam.view.GLView,
        model: Model,
        index: Int,
    ): DoubleArray {
        val finished = CountDownLatch(1)
        val bounds = AtomicReference<DoubleArray>()
        glView.queueEvent {
            val min = ru.ytkab0bp.slicebeam.utils.Vec3d()
            val max = ru.ytkab0bp.slicebeam.utils.Vec3d()
            model.getBoundingBoxExact(index, min, max)
            bounds.set(doubleArrayOf(min.x, min.y, min.z, max.x, max.y, max.z))
            finished.countDown()
        }
        assertTrue("GL thread did not return exact bounds for the requested plate model",
            finished.await(10, TimeUnit.SECONDS))
        return requireNotNull(bounds.get())
    }

    private fun readBoundsOnGl(glView: ru.ytkab0bp.slicebeam.view.GLView, index: Int): String {
        val finished = CountDownLatch(1)
        val bounds = AtomicReference<String>()
        glView.queueEvent {
            val min = ru.ytkab0bp.slicebeam.utils.Vec3d()
            val max = ru.ytkab0bp.slicebeam.utils.Vec3d()
            glView.renderer.model?.getBoundingBoxExact(index, min, max)
            bounds.set("min=(${min.x},${min.y},${min.z}), max=(${max.x},${max.y},${max.z})")
            finished.countDown()
        }
        assertTrue("GL thread did not return model bounds", finished.await(10, TimeUnit.SECONDS))
        return requireNotNull(bounds.get())
    }

    private fun readObjectCountOnGl(
        glView: ru.ytkab0bp.slicebeam.view.GLView,
        timeoutMillis: Long = 10_000,
    ): Int {
        val finished = CountDownLatch(1)
        val count = AtomicReference<Int>()
        glView.queueEvent {
            count.set(glView.renderer.model?.objectsCount ?: 0)
            finished.countDown()
        }
        assertTrue("GL thread did not return model object count", finished.await(timeoutMillis, TimeUnit.MILLISECONDS))
        return requireNotNull(count.get())
    }

    private fun readGeometryRevisionOnGl(glView: ru.ytkab0bp.slicebeam.view.GLView): Long {
        val finished = CountDownLatch(1)
        val revision = AtomicReference<Long>()
        glView.queueEvent {
            revision.set(requireNotNull(glView.renderer.model).geometryRevision)
            finished.countDown()
        }
        assertTrue("GL thread did not return model geometry revision", finished.await(10, TimeUnit.SECONDS))
        return requireNotNull(revision.get())
    }

    private fun readObjectVolumeOnGl(glView: ru.ytkab0bp.slicebeam.view.GLView, index: Int): Double {
        val finished = CountDownLatch(1)
        val volume = AtomicReference<Double>()
        glView.queueEvent {
            volume.set(requireNotNull(glView.renderer.model).getObjectVolume(index))
            finished.countDown()
        }
        assertTrue("GL thread did not return object volume", finished.await(10, TimeUnit.SECONDS))
        return requireNotNull(volume.get())
    }

    private fun readCutConnectorCountOnGl(glView: ru.ytkab0bp.slicebeam.view.GLView, index: Int): Int {
        val finished = CountDownLatch(1)
        val count = AtomicReference<Int>()
        glView.queueEvent {
            count.set(glView.renderer.getCutConnectorCount(index))
            finished.countDown()
        }
        assertTrue("GL thread did not return cut connector count", finished.await(10, TimeUnit.SECONDS))
        return requireNotNull(count.get())
    }

    private fun tapGlViewCenter(glView: ru.ytkab0bp.slicebeam.view.GLView) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val downTime = SystemClock.uptimeMillis()
            val x = glView.width / 2f
            val y = glView.height / 2f
            val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
            val up = MotionEvent.obtain(downTime, downTime + 80L, MotionEvent.ACTION_UP, x, y, 0)
            try {
                glView.dispatchTouchEvent(down)
                glView.dispatchTouchEvent(up)
            } finally {
                down.recycle()
                up.recycle()
            }
        }
    }

    private fun readTriangleCountOnGl(glView: ru.ytkab0bp.slicebeam.view.GLView, index: Int): Int {
        val finished = CountDownLatch(1)
        val count = AtomicReference<Int>()
        glView.queueEvent {
            count.set(requireNotNull(glView.renderer.model).getTriangleCount(index))
            finished.countDown()
        }
        assertTrue("GL thread did not return native triangle count", finished.await(10, TimeUnit.SECONDS))
        return requireNotNull(count.get())
    }

    private fun readBedValidOnGl(glView: ru.ytkab0bp.slicebeam.view.GLView): Boolean {
        val finished = CountDownLatch(1)
        val valid = AtomicReference<Boolean>()
        glView.queueEvent {
            valid.set(glView.renderer.bed?.isValid() == true)
            finished.countDown()
        }
        assertTrue("GL thread did not report the build plate state", finished.await(10, TimeUnit.SECONDS))
        return requireNotNull(valid.get())
    }

    /** Returns minX, maxX, minY, maxY after projecting all four build plate corners. */
    private fun readBuildPlateNdcBoundsOnGl(glView: ru.ytkab0bp.slicebeam.view.GLView): FloatArray {
        val finished = CountDownLatch(1)
        val bounds = AtomicReference<FloatArray>()
        glView.queueEvent {
            val renderer = glView.renderer
            val bed = requireNotNull(renderer.bed)
            val min = requireNotNull(bed.getVolumeMin())
            val max = requireNotNull(bed.getVolumeMax())
            val viewProjection = DoubleArray(16)
            ru.ytkab0bp.slicebeam.utils.DoubleMatrix.multiplyMM(
                viewProjection, 0, renderer.projectionMatrix, 0,
                renderer.camera.getViewModelMatrix(), 0,
            )
            var minX = Double.POSITIVE_INFINITY
            var maxX = Double.NEGATIVE_INFINITY
            var minY = Double.POSITIVE_INFINITY
            var maxY = Double.NEGATIVE_INFINITY
            for (x in doubleArrayOf(min.x, max.x)) {
                for (y in doubleArrayOf(min.y, max.y)) {
                    val clip = DoubleArray(4)
                    ru.ytkab0bp.slicebeam.utils.DoubleMatrix.multiplyMV(
                        clip, 0, viewProjection, 0, doubleArrayOf(x, y, min.z, 1.0), 0,
                    )
                    val ndcX = clip[0] / clip[3]
                    val ndcY = clip[1] / clip[3]
                    minX = minOf(minX, ndcX); maxX = maxOf(maxX, ndcX)
                    minY = minOf(minY, ndcY); maxY = maxOf(maxY, ndcY)
                }
            }
            bounds.set(floatArrayOf(minX.toFloat(), maxX.toFloat(), minY.toFloat(), maxY.toFloat()))
            finished.countDown()
        }
        assertTrue("GL thread did not return projected build plate bounds", finished.await(10, TimeUnit.SECONDS))
        return requireNotNull(bounds.get())
    }

    private fun readRendererCameraZoomOnGl(glView: ru.ytkab0bp.slicebeam.view.GLView): Float {
        val finished = CountDownLatch(1)
        val zoom = AtomicReference<Float>()
        glView.queueEvent {
            zoom.set(glView.renderer.camera.zoom)
            finished.countDown()
        }
        assertTrue("GL thread did not return camera zoom", finished.await(5, TimeUnit.SECONDS))
        return requireNotNull(zoom.get())
    }

    private fun readRendererBooleanOnGl(glView: ru.ytkab0bp.slicebeam.view.GLView, fieldName: String): Boolean {
        val finished = CountDownLatch(1)
        val value = AtomicReference<Boolean>()
        glView.queueEvent {
            val field = glView.renderer.javaClass.getDeclaredField(fieldName).apply { isAccessible = true }
            value.set(field.getBoolean(glView.renderer))
            finished.countDown()
        }
        assertTrue("GL thread did not return renderer state $fieldName", finished.await(10, TimeUnit.SECONDS))
        return requireNotNull(value.get())
    }

    private fun readProjectionMatrixOnGl(glView: ru.ytkab0bp.slicebeam.view.GLView): DoubleArray {
        val finished = CountDownLatch(1)
        val matrix = AtomicReference<DoubleArray>()
        glView.queueEvent {
            matrix.set(glView.renderer.projectionMatrix.clone())
            finished.countDown()
        }
        assertTrue("GL thread did not return the projection matrix", finished.await(10, TimeUnit.SECONDS))
        return requireNotNull(matrix.get())
    }

    private fun readCameraPoseOnGl(glView: ru.ytkab0bp.slicebeam.view.GLView): DoubleArray {
        val finished = CountDownLatch(1)
        val pose = AtomicReference<DoubleArray>()
        glView.queueEvent {
            val camera = glView.renderer.camera
            pose.set(doubleArrayOf(
                camera.origin.x, camera.origin.y, camera.origin.z,
                camera.position.x, camera.position.y, camera.position.z,
            ))
            finished.countDown()
        }
        assertTrue("GL thread did not return the camera pose", finished.await(10, TimeUnit.SECONDS))
        return requireNotNull(pose.get())
    }

    private fun readRendererIntOnGl(glView: ru.ytkab0bp.slicebeam.view.GLView, fieldName: String): Int {
        val finished = CountDownLatch(1)
        val value = AtomicReference<Int>()
        glView.queueEvent {
            val field = glView.renderer.javaClass.getDeclaredField(fieldName).apply { isAccessible = true }
            value.set(field.getInt(glView.renderer))
            finished.countDown()
        }
        assertTrue("GL thread did not return renderer state $fieldName", finished.await(10, TimeUnit.SECONDS))
        return requireNotNull(value.get())
    }

    private fun readRendererNullableIntOnGl(
        glView: ru.ytkab0bp.slicebeam.view.GLView,
        fieldName: String,
    ): Int? {
        val finished = CountDownLatch(1)
        val value = AtomicReference<Int?>()
        glView.queueEvent {
            val field = glView.renderer.javaClass.getDeclaredField(fieldName).apply { isAccessible = true }
            value.set(field.get(glView.renderer) as? Int)
            finished.countDown()
        }
        assertTrue("GL thread did not return renderer state $fieldName", finished.await(10, TimeUnit.SECONDS))
        return value.get()
    }

    private fun readModelHasPaintOnGl(glView: ru.ytkab0bp.slicebeam.view.GLView, index: Int): Boolean {
        val finished = CountDownLatch(1)
        val value = AtomicReference<Boolean>()
        glView.queueEvent {
            value.set(requireNotNull(glView.renderer.model).hasPaint(index))
            finished.countDown()
        }
        assertTrue("GL thread did not return model paint state", finished.await(10, TimeUnit.SECONDS))
        return requireNotNull(value.get())
    }

    private fun readAdaptiveLayerHeightOnGl(
        glView: ru.ytkab0bp.slicebeam.view.GLView,
        index: Int,
    ): Boolean {
        val finished = CountDownLatch(1)
        val value = AtomicReference<Boolean>()
        glView.queueEvent {
            value.set(requireNotNull(glView.renderer.model).hasAdaptiveLayerHeight(index))
            finished.countDown()
        }
        assertTrue("GL thread did not return adaptive layer-height state", finished.await(10, TimeUnit.SECONDS))
        return requireNotNull(value.get())
    }

    private fun readUsedFilamentsOnGl(glView: ru.ytkab0bp.slicebeam.view.GLView, index: Int): IntArray {
        val finished = CountDownLatch(1)
        val value = AtomicReference<IntArray>()
        glView.queueEvent {
            value.set(requireNotNull(glView.renderer.model).getUsedFilaments(index))
            finished.countDown()
        }
        assertTrue("GL thread did not return used filament assignments", finished.await(10, TimeUnit.SECONDS))
        return requireNotNull(value.get())
    }

    private fun readPartCountOnGl(glView: ru.ytkab0bp.slicebeam.view.GLView, index: Int): Int {
        val finished = CountDownLatch(1)
        val value = AtomicReference<Int>()
        glView.queueEvent {
            value.set(requireNotNull(glView.renderer.model).getObjectPartCount(index))
            finished.countDown()
        }
        assertTrue("GL thread did not return grouped object part count", finished.await(10, TimeUnit.SECONDS))
        return requireNotNull(value.get())
    }

    private fun readPaintMaxFilamentOnGl(glView: ru.ytkab0bp.slicebeam.view.GLView, index: Int): Int {
        val finished = CountDownLatch(1)
        val value = AtomicReference<Int>()
        glView.queueEvent {
            value.set(requireNotNull(glView.renderer.model).paintMaxFilament(index))
            finished.countDown()
        }
        assertTrue("GL thread did not return maximum painted filament", finished.await(10, TimeUnit.SECONDS))
        return requireNotNull(value.get())
    }

    private fun readPaintPreviewTriangleCountOnGl(
        glView: ru.ytkab0bp.slicebeam.view.GLView,
        filament: Int,
    ): Int {
        val finished = CountDownLatch(1)
        val value = AtomicReference<Int>()
        val failure = AtomicReference<Throwable?>()
        glView.queueEvent {
            var overlay: GLModel? = null
            try {
                val renderer = glView.renderer
                val session = GLRenderer::class.java.getDeclaredField("paintSession")
                    .apply { isAccessible = true }.get(renderer) as? PaintSession
                if (session == null) error("Paint session ended before preview could be inspected")
                overlay = GLModel()
                value.set(session.buildOverlay(overlay, filament))
            } catch (error: Throwable) {
                failure.set(error)
            } finally {
                overlay?.release()
                finished.countDown()
            }
        }
        assertTrue("GL thread did not return paint preview triangles", finished.await(10, TimeUnit.SECONDS))
        failure.get()?.let { throw AssertionError("Could not inspect active paint preview", it) }
        return requireNotNull(value.get())
    }

    private fun readRendererLastDrawOnGl(glView: ru.ytkab0bp.slicebeam.view.GLView): Long {
        val done = CountDownLatch(1)
        val value = AtomicReference<Long>()
        glView.queueEvent {
            value.set(GLRenderer::class.java.getDeclaredField("lastDraw")
                .apply { isAccessible = true }.getLong(glView.renderer))
            done.countDown()
        }
        assertTrue("GL thread did not return its last rendered frame", done.await(10, TimeUnit.SECONDS))
        return requireNotNull(value.get())
    }

    private fun readRendererLongOnGl(glView: ru.ytkab0bp.slicebeam.view.GLView, fieldName: String): Long {
        val done = CountDownLatch(1)
        val value = AtomicReference<Long>()
        glView.queueEvent {
            value.set(GLRenderer::class.java.getDeclaredField(fieldName)
                .apply { isAccessible = true }.getLong(glView.renderer))
            done.countDown()
        }
        assertTrue("GL thread did not return renderer state $fieldName", done.await(10, TimeUnit.SECONDS))
        return requireNotNull(value.get())
    }

    private fun readPaintOverlayRayHitsOnGl(
        glView: ru.ytkab0bp.slicebeam.view.GLView,
        screenPoint: Pair<Float, Float>,
    ): String {
        val location = IntArray(2)
        glView.getLocationOnScreen(location)
        val done = CountDownLatch(1)
        val value = AtomicReference<String>()
        glView.queueEvent {
            try {
                val overlays = GLRenderer::class.java.getDeclaredField("paintOverlays")
                    .apply { isAccessible = true }.get(glView.renderer) as List<*>
                val hits = ArrayList<ru.ytkab0bp.slicebeam.slic3r.GLModel.HitResult>()
                val scale = Prefs.getRenderScale()
                for (overlay in overlays) {
                    val model = overlay!!.javaClass.getDeclaredField("model")
                        .apply { isAccessible = true }.get(overlay) as ru.ytkab0bp.slicebeam.slic3r.GLModel
                    model.raycaster.raycast(glView.renderer, hits,
                        (screenPoint.first - location[0]) * scale,
                        (screenPoint.second - location[1]) * scale)
                }
                value.set("overlayCount=${overlays.size} initialized=${overlays.mapNotNull { overlay ->
                    overlay?.javaClass?.getDeclaredField("model")?.apply { isAccessible = true }
                        ?.get(overlay) as? ru.ytkab0bp.slicebeam.slic3r.GLModel
                }.map { it.isInitialized() }} hits=${hits.size}")
            } catch (error: Throwable) {
                value.set("error=${error.javaClass.simpleName}:${error.message}")
            } finally {
                done.countDown()
            }
        }
        assertTrue("GL thread did not return live paint overlay hits", done.await(10, TimeUnit.SECONDS))
        return requireNotNull(value.get())
    }

    /** Reads a persisted non-color facet channel by opening a disposable native paint session. */
    private fun readCommittedPaintModeTriangleCountOnGl(
        glView: ru.ytkab0bp.slicebeam.view.GLView,
        mode: Int,
        state: Int,
    ): Int {
        val finished = CountDownLatch(1)
        val value = AtomicReference<Int>()
        val failure = AtomicReference<Throwable?>()
        glView.queueEvent {
            var session: PaintSession? = null
            var overlay: GLModel? = null
            try {
                session = PaintSession(requireNotNull(glView.renderer.model), 0, mode)
                if (!session.isValid()) error("Could not inspect committed paint mode $mode")
                overlay = GLModel()
                value.set(session.buildOverlay(overlay, state))
            } catch (error: Throwable) {
                failure.set(error)
            } finally {
                overlay?.release()
                session?.end()
                finished.countDown()
            }
        }
        assertTrue("GL thread did not return committed paint mode $mode", finished.await(10, TimeUnit.SECONDS))
        failure.get()?.let { throw AssertionError("Could not inspect committed paint mode $mode", it) }
        return requireNotNull(value.get())
    }

    /** Finds an exact mesh hit in the visible portion of the GL view, outside the paint sheet. */
    private fun findUncoveredPreciseSurfacePoint(
        glView: ru.ytkab0bp.slicebeam.view.GLView,
        bed: BedFragment,
        avoid: Pair<Float, Float>? = null,
        minimumAvoidDistancePx: Float? = null,
        requireUpwardFace: Boolean = false,
    ): Pair<Float, Float> {
        val modePanelBounds = compose.onAllNodesWithTag("workspace-mode-panel")
            .fetchSemanticsNodes().firstOrNull()?.boundsInRoot
        val menuPanelBounds = compose.onAllNodesWithTag("workspace-menu-panel")
            .fetchSemanticsNodes().firstOrNull()?.boundsInRoot
        val glLocation = IntArray(2)
        val composeLocation = IntArray(2)
        compose.runOnUiThread {
            glView.getLocationOnScreen(glLocation)
            requireNotNull(bed.overlayLayout).getLocationOnScreen(composeLocation)
        }
        val result = AtomicReference<Pair<Float, Float>?>(null)
        val rendererDiagnostic = AtomicReference<String>("not available")
        val finished = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        glView.queueEvent {
            try {
                val renderer = glView.renderer
                val model = renderer.model
                val min = Vec3d()
                val max = Vec3d()
                val bedMin = Vec3d()
                val bedMax = Vec3d()
                if (model != null && model.objectsCount > 0) model.getBoundingBoxExact(0, min, max)
                renderer.bed?.getVolumeMin()?.let(bedMin::set)
                renderer.bed?.getVolumeMax()?.let(bedMax::set)
                rendererDiagnostic.set(
                    "triangles=${model?.getTriangleCount(0)} viewport=${renderer.viewportWidth}x${renderer.viewportHeight} " +
                        "renderScale=${Prefs.getRenderScale()} camera=${renderer.camera.position.x}," +
                        "${renderer.camera.position.y},${renderer.camera.position.z} origin=${renderer.camera.origin.x}," +
                        "${renderer.camera.origin.y},${renderer.camera.origin.z} " +
                        "bedBounds=${bedMin.x},${bedMin.y},${bedMin.z}..${bedMax.x},${bedMax.y},${bedMax.z} " +
                        "objectBounds=${min.x},${min.y},${min.z}..${max.x},${max.y},${max.z}",
                )
                @Suppress("UNCHECKED_CAST")
                val models = GLRenderer::class.java.getDeclaredField("glModels")
                    .apply { isAccessible = true }.get(renderer) as ArrayList<GLModel>
                val mesh = models.firstOrNull() ?: error("Paint mode did not initialize the selected GL mesh")
                val hits = ArrayList<GLModel.HitResult>()
                val renderScale = Prefs.getRenderScale()
                val centerX = renderer.viewportWidth / 2
                val centerY = renderer.viewportHeight / 2
                // Search around the projected center first, where the loaded cube is framed.
                for (radius in listOf(0, 16, 32, 48, 64, 96, 128, 176)) {
                    val offsets = if (radius == 0) listOf(0 to 0) else buildList {
                        for (offset in -radius..radius step 8) {
                            add(offset to -radius)
                            add(offset to radius)
                            add(-radius to offset)
                            add(radius to offset)
                        }
                    }
                    for ((dx, dy) in offsets) {
                        val x = centerX + dx
                        val y = centerY + dy
                        if (x !in 0 until renderer.viewportWidth || y !in 0 until renderer.viewportHeight) continue
                        val localX = x / renderScale
                        val localY = y / renderScale
                        val rootX = glLocation[0] + localX - composeLocation[0]
                        val rootY = glLocation[1] + localY - composeLocation[1]
                        if (avoid != null) {
                            val distanceX = kotlin.math.abs(glLocation[0] + localX - avoid.first)
                            val distanceY = kotlin.math.abs(glLocation[1] + localY - avoid.second)
                            val tooClose = minimumAvoidDistancePx?.let { minimum ->
                                kotlin.math.hypot(distanceX, distanceY) < minimum
                            } ?: (distanceX < 20f && distanceY < 20f)
                            if (tooClose) continue
                        }
                        if (modePanelBounds?.contains(Offset(rootX, rootY)) == true ||
                            menuPanelBounds?.contains(Offset(rootX, rootY)) == true
                        ) continue
                        mesh.raycaster.raycast(renderer, hits, x.toFloat(), y.toFloat())
                        if (hits.any { !it.approximate && (!requireUpwardFace || it.normal.z > 0.7) }) {
                            result.set((glLocation[0] + localX) to (glLocation[1] + localY))
                            return@queueEvent
                        }
                    }
                }
                // Imported raster/relief meshes may not be centered like the bundled cube.
                // If the center-first search misses, scan the remaining visible GL viewport
                // before concluding that the model has no paintable surface.
                for (y in 8 until renderer.viewportHeight step 16) {
                    for (x in 8 until renderer.viewportWidth step 16) {
                        val localX = x / renderScale
                        val localY = y / renderScale
                        val rootX = glLocation[0] + localX - composeLocation[0]
                        val rootY = glLocation[1] + localY - composeLocation[1]
                        if (avoid != null) {
                            val distanceX = kotlin.math.abs(glLocation[0] + localX - avoid.first)
                            val distanceY = kotlin.math.abs(glLocation[1] + localY - avoid.second)
                            val tooClose = minimumAvoidDistancePx?.let { minimum ->
                                kotlin.math.hypot(distanceX, distanceY) < minimum
                            } ?: (distanceX < 20f && distanceY < 20f)
                            if (tooClose) continue
                        }
                        if (modePanelBounds?.contains(Offset(rootX, rootY)) == true ||
                            menuPanelBounds?.contains(Offset(rootX, rootY)) == true
                        ) continue
                        mesh.raycaster.raycast(renderer, hits, x.toFloat(), y.toFloat())
                        if (hits.any { !it.approximate && (!requireUpwardFace || it.normal.z > 0.7) }) {
                            result.set((glLocation[0] + localX) to (glLocation[1] + localY))
                            return@queueEvent
                        }
                    }
                }
            } catch (error: Throwable) {
                failure.set(error)
            } finally {
                finished.countDown()
            }
        }
        assertTrue("GL thread did not locate a visible exact surface hit", finished.await(15, TimeUnit.SECONDS))
        failure.get()?.let { throw AssertionError("Could not locate visible paint target", it) }
        return requireNotNull(result.get()) {
            "No precise model surface point was reachable outside workspace panels; " +
                "modePanel=$modePanelBounds, menuPanel=$menuPanelBounds, ${rendererDiagnostic.get()}"
        }
    }

    private fun paintAtScreenPointWhileHeld(
        glView: ru.ytkab0bp.slicebeam.view.GLView,
        screenPoint: Pair<Float, Float>,
    ) {
        val location = IntArray(2)
        glView.getLocationOnScreen(location)
        val done = CountDownLatch(1)
        glView.queueEvent {
            try {
                val renderer = glView.renderer
                // Deterministically exercise the throttled branch on the following brush
                // sample, regardless of how long the instrumentation input dispatch took.
                GLRenderer::class.java.getDeclaredField("lastPaintOverlayRebuildAtMs")
                    .apply { isAccessible = true }.setLong(renderer, SystemClock.uptimeMillis())
                val scale = Prefs.getRenderScale()
                renderer.paintAt(
                    (screenPoint.first - location[0]) * scale,
                    (screenPoint.second - location[1]) * scale,
                )
                glView.requestRender()
            } finally {
                done.countDown()
            }
        }
        assertTrue("GL thread did not apply the held brush sample", done.await(10, TimeUnit.SECONDS))
    }

    /** Confirms the GL paint draft changes actual displayed pixels before the user confirms it. */
    private fun assertPaintPreviewIsVisibleInFrame(
        before: android.graphics.Bitmap,
        after: android.graphics.Bitmap,
        screenPoint: Pair<Float, Float>,
        liveOverlayHits: String,
    ) {
        try {
            assertEquals("paint preview screenshots must have identical dimensions", before.width, after.width)
            assertEquals("paint preview screenshots must have identical heights", before.height, after.height)
            val centerX = screenPoint.first.toInt()
            val centerY = screenPoint.second.toInt()
            val radius = 18
            val left = (centerX - radius).coerceIn(0, before.width - 1)
            val right = (centerX + radius).coerceIn(0, before.width - 1)
            val top = (centerY - radius).coerceIn(0, before.height - 1)
            val bottom = (centerY + radius).coerceIn(0, before.height - 1)
            var changedPixels = 0
            var totalColorDelta = 0L
            for (y in top..bottom) for (x in left..right) {
                val oldColor = before.getPixel(x, y)
                val newColor = after.getPixel(x, y)
                val delta = kotlin.math.abs(android.graphics.Color.red(oldColor) - android.graphics.Color.red(newColor)) +
                    kotlin.math.abs(android.graphics.Color.green(oldColor) - android.graphics.Color.green(newColor)) +
                    kotlin.math.abs(android.graphics.Color.blue(oldColor) - android.graphics.Color.blue(newColor))
                if (delta >= 80) changedPixels++
                totalColorDelta += delta
            }
            assertTrue(
                "The canvas should visibly change at the painted mesh point before Done; " +
                    "changedPixels=$changedPixels totalDelta=$totalColorDelta point=$screenPoint liveOverlayHits=$liveOverlayHits",
                changedPixels >= 8 && totalColorDelta >= 2_000,
            )
        } finally {
            before.recycle()
            after.recycle()
        }
    }

    /** Checks the actual live pixel hue, rather than treating any visual change as a color preview. */
    private fun assertPaintColorVisibleAtPoint(
        before: android.graphics.Bitmap,
        after: android.graphics.Bitmap,
        screenPoint: Pair<Float, Float>,
        expectedColor: Int,
        message: String,
    ) {
        try {
            assertEquals("color preview screenshots must have matching dimensions", before.width, after.width)
            assertEquals("color preview screenshots must have matching heights", before.height, after.height)
            val centerX = screenPoint.first.toInt()
            val centerY = screenPoint.second.toInt()
            val radius = 12
            var changedPixels = 0
            var correctlyColoredPixels = 0
            val expectedRed = android.graphics.Color.red(expectedColor)
            val expectedGreen = android.graphics.Color.green(expectedColor)
            val expectedBlue = android.graphics.Color.blue(expectedColor)
            for (y in (centerY - radius).coerceAtLeast(0)..(centerY + radius).coerceAtMost(after.height - 1)) {
                for (x in (centerX - radius).coerceAtLeast(0)..(centerX + radius).coerceAtMost(after.width - 1)) {
                    val previous = before.getPixel(x, y)
                    val current = after.getPixel(x, y)
                    val delta = kotlin.math.abs(android.graphics.Color.red(previous) - android.graphics.Color.red(current)) +
                        kotlin.math.abs(android.graphics.Color.green(previous) - android.graphics.Color.green(current)) +
                        kotlin.math.abs(android.graphics.Color.blue(previous) - android.graphics.Color.blue(current))
                    if (delta >= 36) changedPixels++
                    val red = android.graphics.Color.red(current)
                    val green = android.graphics.Color.green(current)
                    val blue = android.graphics.Color.blue(current)
                    val dominantChannelMatches = when {
                        expectedRed > expectedGreen && expectedRed > expectedBlue -> red > green * 1.12 && red > blue * 1.12
                        expectedGreen > expectedRed && expectedGreen > expectedBlue -> green > red * 1.12 && green > blue * 1.12
                        else -> blue > red * 1.12 && blue > green * 1.04
                    }
                    if (dominantChannelMatches && red + green + blue > 150) correctlyColoredPixels++
                }
            }
            assertTrue(
                "$message; changedPixels=$changedPixels correctlyColoredPixels=$correctlyColoredPixels " +
                    "expected=#${expectedColor.toUInt().toString(16)} point=$screenPoint",
                changedPixels >= 8 && correctlyColoredPixels >= 4,
            )
        } finally {
            before.recycle()
            after.recycle()
        }
    }

    private fun tapGlViewAt(glView: ru.ytkab0bp.slicebeam.view.GLView, screenX: Float, screenY: Float) {
        val canvasLocation = IntArray(2)
        glView.getLocationOnScreen(canvasLocation)
        assertTrue("connector test point must be visible inside the GL canvas",
            screenX >= canvasLocation[0] && screenX < canvasLocation[0] + glView.width &&
                screenY >= canvasLocation[1] && screenY < canvasLocation[1] + glView.height)
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(
            downTime, downTime, MotionEvent.ACTION_DOWN,
            screenX - canvasLocation[0], screenY - canvasLocation[1], 0,
        ).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
        val up = MotionEvent.obtain(
            downTime, downTime + 80L, MotionEvent.ACTION_UP,
            screenX - canvasLocation[0], screenY - canvasLocation[1], 0,
        ).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
        try {
            var downHandled = false
            var upHandled = false
            compose.runOnUiThread {
                downHandled = glView.dispatchTouchEvent(down)
                upHandled = glView.dispatchTouchEvent(up)
            }
            assertTrue("the GL canvas rejected the connector touch", downHandled && upHandled)
        } finally {
            down.recycle()
            up.recycle()
        }
    }

    private fun injectCanvasStroke(
        glView: ru.ytkab0bp.slicebeam.view.GLView,
        screenX: Float,
        screenY: Float,
        previewPoint: Pair<Float, Float>? = null,
        whileFingerDown: (() -> Unit)? = null,
    ) {
        val canvasLocation = IntArray(2)
        glView.getLocationOnScreen(canvasLocation)
        assertTrue(
            "paint test point must be inside the GL canvas; " +
                "screen=($screenX,$screenY), canvasOrigin=(${canvasLocation[0]},${canvasLocation[1]}), " +
                "canvas=${glView.width}x${glView.height}, renderScale=${Prefs.getRenderScale()}",
            screenX >= canvasLocation[0] && screenX < canvasLocation[0] + glView.width &&
                screenY >= canvasLocation[1] && screenY < canvasLocation[1] + glView.height)
        val downTime = SystemClock.uptimeMillis()
        fun inject(action: Int, deltaX: Float, deltaY: Float, eventTime: Long) {
            val event = MotionEvent.obtain(
                downTime, eventTime, action,
                screenX - canvasLocation[0] + deltaX,
                screenY - canvasLocation[1] + deltaY,
                0,
            ).apply {
                source = InputDevice.SOURCE_TOUCHSCREEN
            }
            try {
                var handled = false
                compose.runOnUiThread { handled = glView.dispatchTouchEvent(event) }
                assertTrue("the GL canvas rejected a touch event at the selected surface point", handled)
            } finally {
                event.recycle()
            }
        }
        inject(MotionEvent.ACTION_DOWN, 0f, 0f, downTime)
        // Flood the input path with more moves than the renderer can repaint. Live color
        // preview must stay current without queuing a native mesh rebuild for every event.
        try {
            repeat(24) { step ->
                inject(MotionEvent.ACTION_MOVE, step * 0.45f, step * 0.45f, SystemClock.uptimeMillis())
            }
            previewPoint?.let { target ->
                inject(
                    MotionEvent.ACTION_MOVE,
                    target.first - screenX,
                    target.second - screenY,
                    SystemClock.uptimeMillis(),
                )
            }
            whileFingerDown?.invoke()
        } finally {
            inject(MotionEvent.ACTION_UP, 10f, 10f, SystemClock.uptimeMillis())
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun hasSnackbarTitle(titleRes: Int): Boolean {
        val expectedTitle = compose.activity.getString(titleRes)
        // Read the rendered state through Compose semantics. Reflectively iterating the
        // controller's SnapshotStateList races snackbar animations and can throw CME.
        return compose.onAllNodesWithText(expectedTitle, useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()
    }

    private fun clearWorkspaceSnackbars(bed: BedFragment) {
        val snackbarController = BedFragment::class.java.getDeclaredField("snackbarController")
            .apply { isAccessible = true }.get(bed)
        snackbarController.javaClass.getMethod("clear").invoke(snackbarController)
    }

    private fun readVectorOnGl(
        glView: ru.ytkab0bp.slicebeam.view.GLView,
        index: Int,
        read: (Model, Vec3d) -> Unit,
    ): Vec3d {
        val finished = CountDownLatch(1)
        val value = AtomicReference<Vec3d?>()
        val failure = AtomicReference<Throwable?>()
        glView.queueEvent {
            try {
                val model = requireNotNull(glView.renderer.model)
                check(model.pointer != 0L) { "Renderer returned a released native model" }
                check(index in 0 until model.objectsCount) {
                    "Renderer model has ${model.objectsCount} objects; requested vector for index $index"
                }
                value.set(Vec3d().also { read(model, it) })
            } catch (error: Throwable) {
                failure.set(error)
            } finally {
                finished.countDown()
            }
        }
        assertTrue("GL thread did not return model vector", finished.await(10, TimeUnit.SECONDS))
        failure.get()?.let { throw AssertionError("Could not read native model vector on GL thread", it) }
        return requireNotNull(value.get())
    }

    private fun readModelHistorySize(bed: BedFragment, fieldName: String): Int {
        val history = BedFragment::class.java.getDeclaredField(fieldName).apply { isAccessible = true }
            .get(bed) as java.util.ArrayDeque<*>
        return history.size
    }

    private fun readLastUndoSnapshotObjectCountOnGl(
        bed: BedFragment,
        glView: ru.ytkab0bp.slicebeam.view.GLView,
    ): Int {
        val finished = CountDownLatch(1)
        val objectCount = AtomicReference<Int>()
        glView.queueEvent {
            @Suppress("UNCHECKED_CAST")
            val history = BedFragment::class.java.getDeclaredField("undoHistory")
                .apply { isAccessible = true }.get(bed) as java.util.ArrayDeque<Model>
            objectCount.set(history.peekLast()?.objectsCount ?: 0)
            finished.countDown()
        }
        assertTrue("GL thread did not return the latest undo snapshot", finished.await(10, TimeUnit.SECONDS))
        return requireNotNull(objectCount.get())
    }

    private fun hasExpectedLinearTransform(
        glView: ru.ytkab0bp.slicebeam.view.GLView,
        index: Int,
        expectedDiagonal: DoubleArray,
    ): Boolean {
        val rotation = readVectorOnGl(glView, index) { model, vector -> model.getRotation(index, vector) }
        val scale = readVectorOnGl(glView, index) { model, vector -> model.getScale(index, vector) }
        val mirror = readVectorOnGl(glView, index) { model, vector -> model.getMirror(index, vector) }
        val sx = scale.x * mirror.x; val sy = scale.y * mirror.y; val sz = scale.z * mirror.z
        val cx = kotlin.math.cos(rotation.x); val cy = kotlin.math.cos(rotation.y); val cz = kotlin.math.cos(rotation.z)
        val sinX = kotlin.math.sin(rotation.x); val sinY = kotlin.math.sin(rotation.y); val sinZ = kotlin.math.sin(rotation.z)
        val matrix = arrayOf(
            doubleArrayOf(cz * cy * sx, (cz * sinY * sinX - sinZ * cx) * sy, (cz * sinY * cx + sinZ * sinX) * sz),
            doubleArrayOf(sinZ * cy * sx, (sinZ * sinY * sinX + cz * cx) * sy, (sinZ * sinY * cx - cz * sinX) * sz),
            doubleArrayOf(-sinY * sx, cy * sinX * sy, cy * cx * sz),
        )
        for (row in 0..2) for (column in 0..2) {
            val expected = if (row == column) expectedDiagonal[row] else 0.0
            if (kotlin.math.abs(matrix[row][column] - expected) > 1e-4) return false
        }
        return true
    }

    private fun createInitializedActivityRule() = run {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val application = context.applicationContext as Application
        SliceBeam.INSTANCE = application as SliceBeam
        // Menu customizations are intentionally persisted by the app. Give this broad
        // integration class a stable default order so a prior customization test run
        // cannot hide actions that these tests expect in their baseline menu.
        context.getSharedPreferences("workspace_menu_customization", 0).edit().clear().commit()
        Prefs.init(application)
        Prefs.getPrefs()?.edit()
            ?.remove("crash")
            ?.putBoolean("converter_storage_first_prompt", true)
            ?.commit()
        Prefs.setLastCommit()
        SliceBeam.hasUpdateInfo = false
        SliceBeam.clearLiveDiffs()

        val config = context.assets.open("orca_profiles/Anycubic.ini").use(::Slic3rConfigWrapper)
        val printer = requireNotNull(config.findPrinter("Anycubic Kobra 3 0.4 nozzle"))
        val process = requireNotNull(config.findPrint("0.16mm Standard"))
        val filament = requireNotNull(config.findFilament("Anycubic Generic PLA"))
        config.presets = ConfigObject("presets").apply {
            put("printer", printer.title)
            put("print", process.title)
            put("filament", filament.title)
        }
        SliceBeam.CONFIG = config
        createAndroidComposeRule<MainActivity>()
    }

    private fun <T> onInstrumentationMain(block: () -> T): T {
        val result = AtomicReference<Any?>()
        val failure = AtomicReference<Throwable?>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try {
                result.set(block())
            } catch (error: Throwable) {
                failure.set(error)
            }
        }
        failure.get()?.let { throw AssertionError("Workspace semantics operation failed", it) }
        @Suppress("UNCHECKED_CAST")
        return result.get() as T
    }

    private fun workspaceComposeRootViews(activity: MainActivity): List<View> {
        val bed = requireNotNull(activity.navigationDelegate.getCurrentFragment() as? BedFragment)
        val fragmentRoot = requireNotNull(bed.getView())
        val composeRoots = mutableListOf<View>()
        fun findComposeRoots(view: View) {
            if (view.javaClass.name == "androidx.compose.ui.platform.AndroidComposeView") {
                composeRoots += view
                return
            }
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) {
                    findComposeRoots(view.getChildAt(index))
                }
            }
        }
        findComposeRoots(fragmentRoot)
        require(composeRoots.isNotEmpty()) {
            "Workspace AndroidComposeView is not attached"
        }
        return composeRoots
    }

    private fun workspaceSemanticsOwners(activity: MainActivity): List<SemanticsOwner> =
        workspaceComposeRootViews(activity).map { composeRoot ->
            composeRoot.javaClass.getMethod("getSemanticsOwner").invoke(composeRoot) as SemanticsOwner
        }

    private fun findWorkspaceSemanticsNode(node: SemanticsNode, tag: String): SemanticsNode? {
        if (semanticsValue(node.config, SemanticsProperties.TestTag) == tag) return node
        node.children.forEach { child -> findWorkspaceSemanticsNode(child, tag)?.let { return it } }
        return null
    }

    private fun <T> semanticsValue(config: SemanticsConfiguration, key: SemanticsPropertyKey<T>): T? =
        if (config.contains(key)) config[key] else null

    private fun readWorkspaceSemanticsNode(activity: MainActivity, tag: String): SemanticsNode =
        onInstrumentationMain {
            val owners = workspaceSemanticsOwners(activity)
            owners.firstNotNullOfOrNull { owner ->
                findWorkspaceSemanticsNode(owner.unmergedRootSemanticsNode, tag)
            } ?: error("Workspace semantics node '$tag' is not present in ${owners.size} Compose roots")
        }

    private fun awaitWorkspaceSemanticsNode(activity: MainActivity, tag: String, timeoutMillis: Long = 5_000) {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        do {
            val found = runCatching { readWorkspaceSemanticsNode(activity, tag) }.isSuccess
            if (found) return
            SystemClock.sleep(50L)
        } while (SystemClock.uptimeMillis() < deadline)
        readWorkspaceSemanticsNode(activity, tag)
    }

    private fun awaitWorkspaceSelected(activity: MainActivity, tag: String, timeoutMillis: Long = 5_000) {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        do {
            val selected = runCatching {
                semanticsValue(readWorkspaceSemanticsNode(activity, tag).config, SemanticsProperties.Selected)
            }.getOrNull() == true
            if (selected) return
            SystemClock.sleep(50L)
        } while (SystemClock.uptimeMillis() < deadline)
        assertTrue("workspace node '$tag' should become selected", runCatching {
            semanticsValue(readWorkspaceSemanticsNode(activity, tag).config, SemanticsProperties.Selected)
        }.getOrNull() == true)
    }

    private fun performWorkspaceClick(activity: MainActivity, tag: String) = onInstrumentationMain {
        val node = requireNotNull(workspaceSemanticsOwners(activity).asSequence()
                .map { it.unmergedRootSemanticsNode }
                .firstNotNullOfOrNull { findWorkspaceSemanticsNode(it, tag) }
        ) { "Workspace click target '$tag' is not present in attached Compose roots" }
        val click = requireNotNull(semanticsValue(node.config, SemanticsActions.OnClick)?.action) {
            "Workspace semantics node '$tag' has no click action"
        }
        assertTrue("Workspace click action '$tag' should be accepted", click.invoke())
    }

    private fun performWorkspaceSetText(activity: MainActivity, tag: String, value: String) =
        onInstrumentationMain {
            val node = requireNotNull(workspaceSemanticsOwners(activity).asSequence()
                .map { it.unmergedRootSemanticsNode }
                .firstNotNullOfOrNull { findWorkspaceSemanticsNode(it, tag) }
            ) { "Workspace text field '$tag' is not present in attached Compose roots" }
            val setText = requireNotNull(semanticsValue(node.config, SemanticsActions.SetText)?.action) {
                "Workspace semantics node '$tag' has no set-text action"
            }
            assertTrue("Workspace text field '$tag' should accept text input",
                setText.invoke(AnnotatedString(value)))
        }

    private fun awaitWorkspaceSemanticsText(activity: MainActivity, expected: String, timeoutMillis: Long = 5_000) {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        do {
            val visible = onInstrumentationMain {
                fun contains(node: SemanticsNode): Boolean {
                    val text = semanticsValue(node.config, SemanticsProperties.Text).orEmpty()
                        .any { it.text == expected }
                    val description = semanticsValue(node.config, SemanticsProperties.ContentDescription).orEmpty()
                        .any { it == expected }
                    return text || description || node.children.any(::contains)
                }
                workspaceSemanticsOwners(activity).any { owner ->
                    contains(owner.unmergedRootSemanticsNode)
                }
            }
            if (visible) return
            SystemClock.sleep(50L)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError("Workspace semantic text '$expected' did not appear")
    }
}
