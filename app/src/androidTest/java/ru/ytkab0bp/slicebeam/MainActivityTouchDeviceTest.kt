package ru.ytkab0bp.slicebeam

import android.app.Application
import android.os.SystemClock
import android.view.MotionEvent
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.ytkab0bp.slicebeam.config.ConfigObject
import ru.ytkab0bp.slicebeam.components.bed_menu.FileMenuObjectActions
import ru.ytkab0bp.slicebeam.components.bed_menu.FileMenu
import ru.ytkab0bp.slicebeam.components.bed_menu.TransformMenuItems
import ru.ytkab0bp.slicebeam.components.bed_menu.OrientationPositionMenu
import ru.ytkab0bp.slicebeam.components.bed_menu.ModelMenuItems
import ru.ytkab0bp.slicebeam.components.bed_menu.CameraMenu
import ru.ytkab0bp.slicebeam.components.bed_menu.SliceUploadController
import ru.ytkab0bp.slicebeam.components.FastenerFormLogic
import ru.ytkab0bp.slicebeam.utils.FastenerStandards
import ru.ytkab0bp.slicebeam.utils.FastenerStlWriter
import ru.ytkab0bp.slicebeam.fragment.BedFragment
import ru.ytkab0bp.slicebeam.navigation.Fragment
import ru.ytkab0bp.slicebeam.slic3r.Model
import ru.ytkab0bp.slicebeam.slic3r.GLModel
import ru.ytkab0bp.slicebeam.render.GLRenderer
import ru.ytkab0bp.slicebeam.slic3r.LargeModelPick
import ru.ytkab0bp.slicebeam.slic3r.Slic3rConfigWrapper
import ru.ytkab0bp.slicebeam.utils.Prefs
import ru.ytkab0bp.slicebeam.utils.Vec3d
import ru.ytkab0bp.slicebeam.view.GLView

/** Exercises the real GLView tap path on an Android device with a loaded model. */
@RunWith(AndroidJUnit4::class)
class MainActivityTouchDeviceTest {
    @Test
    fun tappingLoadedM12SelectsItThroughTheGlTouchPath() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val application = context.applicationContext as SliceBeam
        SliceBeam.INSTANCE = application
        Prefs.init(application)
        val originalRenderScale = Prefs.getRenderScaleOverridePercent()
        Prefs.getPrefs()?.edit()
            ?.remove("crash")
            ?.putBoolean("converter_storage_first_prompt", true)
            ?.commit()
        Prefs.setLastCommit()
        SliceBeam.clearLiveDiffs()
        val config = context.assets.open("orca_profiles/Anycubic.ini").use { Slic3rConfigWrapper(it) }
        val printer = requireNotNull(config.findPrinter("Anycubic Kobra 3 0.4 nozzle"))
        val process = requireNotNull(config.findPrint("0.16mm Standard"))
        val filament = requireNotNull(config.findFilament("Anycubic Generic PLA"))
        config.presets = ConfigObject("presets").apply {
            put("printer", printer.title)
            put("print", process.title)
            put("filament", filament.title)
        }
        SliceBeam.CONFIG = config

        val modelFile = File(context.cacheDir, "m12-gl-touch-selection.stl")
        val values = FastenerFormLogic.defaults(size = FastenerStandards.count() - 1)
        FastenerStlWriter.writeBolt(
            modelFile,
            FastenerStandards.diameter(values.size),
            FastenerFormLogic.parse(values.second),
            FastenerFormLogic.parse(values.first),
            FastenerFormLogic.parse(values.fourth),
            FastenerFormLogic.parse(values.third),
        )
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            val bedRef = AtomicReference<BedFragment>()
            scenario.onActivity { activity ->
                bedRef.set(activity.navigationDelegate.getCurrentFragment() as BedFragment)
            }
            val bed = requireNotNull(bedRef.get())
            val glViewRef = AtomicReference<GLView>()
            scenario.onActivity { glViewRef.set(requireNotNull(bed.glView)) }
            val glView = requireNotNull(glViewRef.get())
            val loaded = CountDownLatch(1)
            val loadFailure = AtomicReference<Exception?>()
            scenario.onActivity {
                bed.loadModel(modelFile, false, 1, object : BedFragment.ModelLoadCallback {
                    override fun onLoaded(loadedModel: Model, firstNewObject: Int, addedObjects: Int) {
                        loaded.countDown()
                    }

                    override fun onFailed(error: Exception) {
                        loadFailure.set(error)
                        loaded.countDown()
                    }
                })
            }
            assertTrue("Generated M12 should load", loaded.await(60, TimeUnit.SECONDS))
            loadFailure.get()?.let { throw AssertionError("Generated M12 could not load", it) }

            assertTrue("The real GL renderer must be identified before selecting automatic viewport quality",
                !Prefs.getGlRendererName().isNullOrBlank())
            for (qualityPercent in listOf(0, 50, 65, 100)) {
                if (qualityPercent != Prefs.getRenderScaleOverridePercent()) {
                    scenario.onActivity {
                        Prefs.setRenderScaleOverridePercent(qualityPercent)
                        SliceBeam.EVENT_BUS.fireEvent(ru.ytkab0bp.slicebeam.events.ViewportRenderScaleChangedEvent())
                    }
                }
                val expectedScale = if (qualityPercent == 0) Prefs.getAutomaticRenderScale()
                    else qualityPercent / 100f
                val surfaceDeadline = SystemClock.uptimeMillis() + 10_000L
                while (SystemClock.uptimeMillis() < surfaceDeadline &&
                    glView.renderer.viewportWidth != (glView.width * expectedScale).toInt()
                ) {
                    glView.requestRender()
                    SystemClock.sleep(50L)
                }
                val expectedWidth = (glView.width * expectedScale).toInt()
                assertTrue(
                    "The live GL surface should use the selected ${qualityPercent}% preview resolution " +
                        "(view=${glView.width}, surface=${glView.renderer.viewportWidth})",
                    kotlin.math.abs(glView.renderer.viewportWidth - expectedWidth) <= 2,
                )

                var hit: Pair<Float, Float>? = null
                val hitDeadline = SystemClock.uptimeMillis() + 15_000L
                while (hit == null && SystemClock.uptimeMillis() < hitDeadline) {
                    glView.requestRender()
                    val scanFinished = CountDownLatch(1)
                    val candidate = AtomicReference<Pair<Float, Float>?>()
                    glView.queueEvent {
                        val renderScale = Prefs.getRenderScale()
                        if (glView.width > 0 && glView.height > 0 &&
                            glView.renderer.viewportWidth > 0 && glView.renderer.viewportHeight > 0
                        ) {
                            for (row in 1..63 step 2) {
                                for (column in 1..63 step 2) {
                                    val x = glView.width * column / 64f
                                    val y = glView.height * row / 64f
                                    if (glView.renderer.raycastObjectIndex(x * renderScale, y * renderScale) == 0) {
                                        candidate.set(x to y)
                                        break
                                    }
                                }
                                if (candidate.get() != null) break
                            }
                        }
                        scanFinished.countDown()
                    }
                    assertTrue("GL raycast scan should finish", scanFinished.await(10, TimeUnit.SECONDS))
                    hit = candidate.get()
                    if (hit == null) SystemClock.sleep(100L)
                }
                val tapPoint = hit ?: throw AssertionError(
                    "Could not raycast the generated M12 at ${qualityPercent}% preview resolution",
                )

                scenario.onActivity { tap(glView, tapPoint.first, tapPoint.second) }
                awaitRendererQueue(glView)
                val selectionRead = CountDownLatch(1)
                val selected = AtomicInteger(Int.MIN_VALUE)
                glView.queueEvent {
                    selected.set(glView.renderer.selectedObject)
                    selectionRead.countDown()
                }
                assertTrue("GL selection should settle after the real touch", selectionRead.await(10, TimeUnit.SECONDS))
                assertEquals("A tap should select the model at ${qualityPercent}% resolution", 0, selected.get())
            }
            assertFalse("Selecting the M12 must not open a modal", bed.isModelContextMenuAttached())
            assertFalse("Selecting the M12 must not open a workspace dialog", bed.isWorkspaceMessageDialogAttached())
        } finally {
            scenario.close()
            modelFile.delete()
            Prefs.setRenderScaleOverridePercent(originalRenderScale)
        }
    }

    @Test
    fun generatedM12SupportsPreciseSurfacePlacementWithoutTopologyIndexing() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val application = context.applicationContext as SliceBeam
        SliceBeam.INSTANCE = application
        Prefs.init(application)
        Prefs.getPrefs()?.edit()
            ?.remove("crash")
            ?.putBoolean("converter_storage_first_prompt", true)
            ?.commit()
        Prefs.setLastCommit()
        SliceBeam.clearLiveDiffs()
        val config = context.assets.open("orca_profiles/Anycubic.ini").use { Slic3rConfigWrapper(it) }
        val printer = requireNotNull(config.findPrinter("Anycubic Kobra 3 0.4 nozzle"))
        val process = requireNotNull(config.findPrint("0.16mm Standard"))
        val filament = requireNotNull(config.findFilament("Anycubic Generic PLA"))
        config.presets = ConfigObject("presets").apply {
            put("printer", printer.title)
            put("print", process.title)
            put("filament", filament.title)
        }
        SliceBeam.CONFIG = config

        val modelFile = File(context.cacheDir, "m12-surface-placement-safety.stl")
        val values = FastenerFormLogic.defaults(size = FastenerStandards.count() - 1)
        FastenerStlWriter.writeBolt(
            modelFile,
            FastenerStandards.diameter(values.size),
            FastenerFormLogic.parse(values.second),
            FastenerFormLogic.parse(values.first),
            FastenerFormLogic.parse(values.fourth),
            FastenerFormLogic.parse(values.third),
        )
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            val bedRef = AtomicReference<BedFragment>()
            scenario.onActivity { activity ->
                bedRef.set(activity.navigationDelegate.getCurrentFragment() as BedFragment)
            }
            val bed = bedRef.get()
            val loaded = AtomicReference<Model?>()
            val failure = AtomicReference<Exception?>()
            val loadFinished = CountDownLatch(1)
            scenario.onActivity {
                bed.loadModel(modelFile, false, 1, object : BedFragment.ModelLoadCallback {
                    override fun onLoaded(loadedModel: Model, firstNewObject: Int, addedObjects: Int) {
                        loaded.set(loadedModel)
                        loadFinished.countDown()
                    }

                    override fun onFailed(error: Exception) {
                        failure.set(error)
                        loadFinished.countDown()
                    }
                })
            }
            assertTrue("M12 target should load", loadFinished.await(60, TimeUnit.SECONDS))
            val model = requireNotNull(loaded.get()) { "M12 load failed: ${failure.get()}" }
            val glView = requireNotNull(bed.glView)
            val prepared = CountDownLatch(1)
            val result = AtomicInteger(Int.MIN_VALUE)
            val modelRevisionBeforePlacement = AtomicLong(-1L)
            glView.queueEvent {
                // Keep a second M12 as the moving group and target the first one.
                model.addObject(model, 0)
                model.translate(1, 30.0, 0.0, 0.0)
                glView.renderer.setSelectedObject(1)
                modelRevisionBeforePlacement.set(model.geometryRevision)
                val selection = glView.renderer.captureSelectionSnapshot()
                result.set(glView.renderer.beginSurfacePlacement(0, selection))
                glView.requestRender()
                prepared.countDown()
            }
            assertTrue("surface placement safety check should finish on GL", prepared.await(10, TimeUnit.SECONDS))
            val triangleCount = model.getTriangleCount(0)
            assertTrue("the generated M12 should stay within the exact raycast ceiling (triangles=$triangleCount)",
                LargeModelPick.canUsePreciseSurfaceTools(triangleCount))
            assertFalse("M12 should not fall back to approximate bounds picking",
                LargeModelPick.needsBoundsOnlyPicking(triangleCount))
            assertFalse("the generated M12 should not require paint-topology simplification (triangles=$triangleCount)",
                LargeModelPick.needsPreciseSurfaceSimplification(triangleCount))
            assertEquals("precise M12 surface placement should start", 1, result.get())
            assertTrue("the exact surface target should enter tap-to-place mode", glView.renderer.isSurfacePlacementActive)
            assertEquals("starting placement must not change source objects", 2, model.objectsCount)
            assertEquals("starting placement must not mutate model geometry", modelRevisionBeforePlacement.get(),
                model.geometryRevision)
            glView.cancelSurfacePlacement()
            val cancelled = CountDownLatch(1)
            glView.queueEvent { cancelled.countDown() }
            assertTrue("cancel should be processed on GL", cancelled.await(10, TimeUnit.SECONDS))
            assertFalse("Android-side cancel should clear the active target", glView.renderer.isSurfacePlacementActive)

            // The generated M12 stays below the precise-picking and paint-topology limits,
            // so surface-aware embossing should open without an unnecessary simplify step.
            val selectionReady = CountDownLatch(1)
            glView.queueEvent {
                glView.renderer.setSelectedObject(1)
                selectionReady.countDown()
            }
            assertTrue("M12 emboss selection should be set on GL", selectionReady.await(10, TimeUnit.SECONDS))
            assertTrue("M12 should be selected below the paint-topology threshold",
                !LargeModelPick.needsPreciseSurfaceSimplification(triangleCount))
            bed.enterEmbossMode()
            assertTrue("precise M12 embossing should not require unnecessary simplification", bed.isEmbossMode)
            assertTrue("emboss mode should be enabled on GL", glView.renderer.isInEmbossMode)
            assertTrue("Android Back should close the M12 emboss tool", bed.onBackPressed())
            val embossStopped = CountDownLatch(1)
            glView.queueEvent { embossStopped.countDown() }
            assertTrue("emboss shutdown should finish on the GL thread", embossStopped.await(10, TimeUnit.SECONDS))
            assertFalse("closing emboss should disable the renderer mode", glView.renderer.isInEmbossMode)
        } finally {
            scenario.close()
            modelFile.delete()
        }
    }

    @Test
    fun tappingGeneratedM12AndEmptyPlateDoesNotCrashAndLongPressKeepsActionsCollapsed() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val application = context.applicationContext as Application
        SliceBeam.INSTANCE = application as SliceBeam
        Prefs.init(application)
        Prefs.getPrefs()?.edit()
            ?.remove("crash")
            ?.putBoolean("converter_storage_first_prompt", true)
            ?.commit()
        Prefs.setLastCommit()
        SliceBeam.clearLiveDiffs()

        val config = context.assets.open("orca_profiles/Anycubic.ini").use { Slic3rConfigWrapper(it) }
        val printer = requireNotNull(config.findPrinter("Anycubic Kobra 3 0.4 nozzle"))
        val process = requireNotNull(config.findPrint("0.16mm Standard"))
        val filament = requireNotNull(config.findFilament("Anycubic Generic PLA"))
        config.presets = ConfigObject("presets").apply {
            put("printer", printer.title)
            put("print", process.title)
            put("filament", filament.title)
        }
        SliceBeam.CONFIG = config

        val modelFile = File(context.cacheDir, "touch-test-m12-bolt.stl")
        val loadedModelRef = AtomicReference<Model?>()
        val loadFailure = AtomicReference<Exception?>()
        val loadFinished = CountDownLatch(1)
        val fastenerValues = FastenerFormLogic.defaults(size = FastenerStandards.count() - 1)
        assertEquals(null, FastenerFormLogic.validate(fastenerValues))
        val expectedFacets = FastenerStlWriter.writeBolt(
            modelFile,
            FastenerStandards.diameter(fastenerValues.size),
            FastenerFormLogic.parse(fastenerValues.second),
            FastenerFormLogic.parse(fastenerValues.first),
            FastenerFormLogic.parse(fastenerValues.fourth),
            FastenerFormLogic.parse(fastenerValues.third),
        )

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            var bed: BedFragment? = null
            scenario.onActivity { activity ->
                val current = activity.navigationDelegate.getCurrentFragment()
                assertTrue("The model workspace should be active", current is BedFragment)
                bed = current as BedFragment
                bed.loadModel(modelFile, false, 1, object : BedFragment.ModelLoadCallback {
                    override fun onLoaded(loadedModel: Model, firstNewObject: Int, addedObjects: Int) {
                        loadedModelRef.set(loadedModel)
                        loadFinished.countDown()
                    }

                    override fun onFailed(error: Exception) {
                        loadFailure.set(error)
                        loadFinished.countDown()
                    }
                })
            }

            assertTrue("The touch test model should finish loading", loadFinished.await(60, TimeUnit.SECONDS))
            val model = loadedModelRef.get() ?: throw AssertionError("The touch test model failed to load", loadFailure.get())
            assertEquals("The workspace should contain the touch target", 1, model.objectsCount)
            assertEquals("The M12 touch target should retain its generated facets", expectedFacets,
                model.getTriangleCount(0))
            val cameraGlViewRef = AtomicReference<GLView>()
            scenario.onActivity {
                cameraGlViewRef.set(requireNotNull(bed?.glView))
                cameraGlViewRef.get().requestRender()
            }
            val cameraGlView = requireNotNull(cameraGlViewRef.get())
            val meshesField = cameraGlView.renderer.javaClass.getDeclaredField("glModels").apply { isAccessible = true }
            val raycasterField = GLModel::class.java.getDeclaredField("raycasterCache").apply { isAccessible = true }
            var generatedMesh: GLModel? = null
            val meshDeadline = SystemClock.uptimeMillis() + 10_000L
            while (generatedMesh == null && SystemClock.uptimeMillis() < meshDeadline) {
                val meshesReady = CountDownLatch(1)
                scenario.onActivity {
                    cameraGlView.queueEvent {
                        generatedMesh = (meshesField.get(cameraGlView.renderer) as? List<*>)
                            ?.firstOrNull() as? GLModel
                        meshesReady.countDown()
                    }
                }
                assertTrue("The M12 GL mesh should initialize", meshesReady.await(5, TimeUnit.SECONDS))
                if (generatedMesh == null) SystemClock.sleep(50L)
            }
            val m12Mesh = requireNotNull(generatedMesh) { "The renderer did not initialize the loaded M12 mesh" }
            val cameraModeBefore = Prefs.getCameraControlMode()
            try {
                Prefs.setCameraControlMode(Prefs.CAMERA_CONTROL_MODE_ROTATE_MOVE)
                assertTrue("The generated M12 should not build a pick tree before user interaction",
                    raycasterField.get(m12Mesh) == null)
                val cameraBeforeDrag = readCameraPose(cameraGlView)
                val downTime = SystemClock.uptimeMillis()
                val centerX = cameraGlView.width / 2f
                val centerY = cameraGlView.height / 2f
                scenario.onActivity {
                    dispatch(cameraGlView, MotionEvent.ACTION_DOWN, centerX, centerY, downTime, downTime)
                    dispatch(cameraGlView, MotionEvent.ACTION_MOVE, centerX + 70f, centerY + 40f,
                        downTime, downTime + 35L)
                    dispatch(cameraGlView, MotionEvent.ACTION_MOVE, centerX + 145f, centerY + 95f,
                        downTime, downTime + 70L)
                    dispatch(cameraGlView, MotionEvent.ACTION_UP, centerX + 145f, centerY + 95f,
                        downTime, downTime + 90L)
                }
                awaitRendererQueue(cameraGlView)
                val cameraAfterDrag = readCameraPose(cameraGlView)
                assertTrue("A one-finger orbit gesture should update the camera",
                    cameraBeforeDrag.indices.any { kotlin.math.abs(cameraBeforeDrag[it] - cameraAfterDrag[it]) > 1e-6 })
                assertTrue("A normal camera orbit must not lazily build a model raycast tree",
                    raycasterField.get(m12Mesh) == null)
            } finally {
                Prefs.setCameraControlMode(cameraModeBefore)
            }

            var hitPoint: Pair<Float, Float>? = null
            var viewport: GLView? = null
            var viewportDescription = "not measured"
            val hitDeadline = SystemClock.uptimeMillis() + 10_000L
            while (hitPoint == null && SystemClock.uptimeMillis() < hitDeadline) {
                instrumentation.waitForIdleSync()
                scenario.onActivity {
                    val glView = requireNotNull(bed?.glView)
                    viewport = glView
                    if (glView.width > 0 && glView.height > 0) {
                        // GLSurfaceView draws only when requested. A completed model-load callback
                        // can run before the first frame has lazily built the GLModel raycaster.
                        glView.requestRender()
                        val renderScale = Prefs.getRenderScale()
                        val modelCount = glView.renderer.model?.objectsCount ?: -1
                        val meshCount = try {
                            val field = glView.renderer.javaClass.getDeclaredField("glModels")
                            field.isAccessible = true
                            (field.get(glView.renderer) as? List<*>)?.size ?: -1
                        } catch (_: ReflectiveOperationException) {
                            -1
                        }
                        viewportDescription = "view=${glView.width}x${glView.height}, renderer=${glView.renderer.viewportWidth}x${glView.renderer.viewportHeight}, renderScale=$renderScale, models=$modelCount, meshes=$meshCount"
                        if (glView.renderer.viewportWidth <= 0 || glView.renderer.viewportHeight <= 0) return@onActivity
                        // A 20 mm test cube can cover less than one eighth of a tall phone
                        // viewport, so coarse samples can miss it on different aspect ratios.
                        for (row in 1..63 step 2) {
                            for (column in 1..63 step 2) {
                                val x = glView.width * column / 64f
                                val y = glView.height * row / 64f
                                if (glView.renderer.raycastObjectIndex(x * renderScale, y * renderScale) == 0) {
                                    hitPoint = x to y
                                    break
                                }
                            }
                            if (hitPoint != null) break
                        }
                    }
                }
                if (hitPoint == null) SystemClock.sleep(100L)
            }

            val hit = hitPoint ?: throw AssertionError("Could not find the loaded model in the 3D viewport ($viewportDescription)")
            val glView = requireNotNull(viewport)
            scenario.onActivity {
                val meshesField = glView.renderer.javaClass.getDeclaredField("glModels").apply { isAccessible = true }
                val generatedMesh = (meshesField.get(glView.renderer) as List<*>).first() as GLModel
                val triangleCount = model.getTriangleCount(0)
                assertTrue(
                    "the generated M12 must have triangles",
                    triangleCount > 0,
                )
                assertEquals("paint topology must remain capped at the configured triangle threshold",
                    triangleCount > LargeModelPick.TRIANGLE_LIMIT,
                    !LargeModelPick.canUsePrecisePaintRaycast(triangleCount))
                val pickHits = arrayListOf<GLModel.HitResult>()
                generatedMesh.raycaster.raycast(
                    glView.renderer,
                    pickHits,
                    hit.first * Prefs.getRenderScale(),
                    hit.second * Prefs.getRenderScale(),
                )
                assertEquals(
                    "A standard M12 must use exact raycasts below the cap and bounds-only picks above it " +
                        "(triangles=$triangleCount)",
                    LargeModelPick.needsBoundsOnlyPicking(triangleCount),
                    pickHits.firstOrNull()?.approximate,
                )
                assertEquals("The GL renderer must track the actual surface width", glView.width, glView.renderer.viewportWidth)
                assertEquals("The GL renderer must track the actual surface height", glView.height, glView.renderer.viewportHeight)
                tap(glView, hit.first, hit.second)
                tap(glView, hit.first, hit.second)
                awaitRendererQueue(glView)
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertSameModelSelection(bed, model)
                assertModelSelectedWithoutModal(requireNotNull(bed))
            }

            val emptyPlatePointRef = AtomicReference<Pair<Float, Float>?>()
            scenario.onActivity { emptyPlatePointRef.set(findEmptyPlatePoint(glView)) }
            val emptyPlatePoint = requireNotNull(emptyPlatePointRef.get())
            scenario.onActivity {
                tap(glView, emptyPlatePoint.first, emptyPlatePoint.second)
                tap(glView, emptyPlatePoint.first, emptyPlatePoint.second)
                awaitRendererQueue(glView)
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertEquals("Tapping an empty plate area should clear the model selection", -1, glView.renderer.selectedObject)
                assertFalse("Tapping the empty plate must not open the model context menu",
                    requireNotNull(bed).isModelContextMenuAttached())
                assertFalse("Tapping the empty plate must not open a workspace message dialog",
                    requireNotNull(bed).isWorkspaceMessageDialogAttached())
            }

            // A deliberate tap on the model restores its selection without opening a dialog.
            scenario.onActivity {
                tap(glView, hit.first, hit.second)
                awaitRendererQueue(glView)
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertSameModelSelection(bed, model)
                assertModelSelectedWithoutModal(requireNotNull(bed))
            }

            // Exercise the real Edit-menu Move action with a loaded model. The preview is
            // intentionally cancelled with Android Back; persisted model coordinates must stay put.
            val translationBeforeEdit = Vec3d().also { model.getTranslation(0, it) }
            val positionToolRef = AtomicReference<OrientationPositionMenu?>()
            val selectionSnapshotReady = CountDownLatch(1)
            val transformItemsRef = AtomicReference<TransformMenuItems?>()
            scenario.onActivity {
                val workspace = requireNotNull(bed)
                val openPosition: java.util.function.Consumer<android.view.View> =
                    java.util.function.Consumer {
                        OrientationPositionMenu(workspace).also { tool ->
                            positionToolRef.set(tool)
                            workspace.showWorkspaceComposeTool(tool)
                        }
                    }
                val noOp: java.util.function.Consumer<android.view.View> = java.util.function.Consumer { }
                val items = TransformMenuItems(workspace, openPosition, noOp, noOp, noOp) {
                    selectionSnapshotReady.countDown()
                }
                transformItemsRef.set(items)
                // The selection summary is read asynchronously on GL; wait for the real
                // renderer snapshot before asserting the action's enabled state.
                items.createItems()
            }
            assertTrue(
                "The edit action should receive the selected model snapshot from GL",
                selectionSnapshotReady.await(5, TimeUnit.SECONDS),
            )
            scenario.onActivity {
                val moveAction = requireNotNull(transformItemsRef.get())
                    .createItems().single { it.titleRes == R.string.MenuToolbarMove }
                assertTrue("Move should be enabled for the selected model", moveAction.enabled)
                moveAction.onClick(requireNotNull(viewport))
            }
            instrumentation.waitForIdleSync()
            val positionTool = requireNotNull(positionToolRef.get())
            val attachDeadline = SystemClock.uptimeMillis() + 3_000L
            while (!positionTool.isAttached() && SystemClock.uptimeMillis() < attachDeadline) {
                instrumentation.waitForIdleSync()
                SystemClock.sleep(40L)
            }
            assertTrue("The Edit menu Move action should open its Compose tool", positionTool.isAttached())
            scenario.onActivity {
                positionTool.translateVisual(
                    translationBeforeEdit.x + 12.0,
                    translationBeforeEdit.y + 7.0,
                    translationBeforeEdit.z,
                )
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                val controller = BedFragment::class.java.getDeclaredField("workspaceController")
                    .apply { isAccessible = true }.get(requireNotNull(bed))
                assertEquals("Move should be the only active workspace tool",
                    OrientationPositionMenu::class.java.name,
                    controller.javaClass.getMethod("getActiveToolId").invoke(controller))
                assertTrue("Android Back should cancel the Move preview", requireNotNull(bed).onBackPressed())
            }
            instrumentation.waitForIdleSync()
            val translationAfterCancel = Vec3d().also { model.getTranslation(0, it) }
            assertEquals("Cancelling Move must preserve model X", translationBeforeEdit.x, translationAfterCancel.x, 0.0001)
            assertEquals("Cancelling Move must preserve model Y", translationBeforeEdit.y, translationAfterCancel.y, 0.0001)
            assertEquals("Cancelling Move must preserve model Z", translationBeforeEdit.z, translationAfterCancel.z, 0.0001)
            scenario.onActivity {
                assertFalse("Cancelled Move should clear its active tool state",
                    requireNotNull(positionToolRef.get()).isAttached())
            }
            SystemClock.sleep(350L)

            val objectCountBeforeDeleteCancel = model.objectsCount
            val deleteSelectionReady = CountDownLatch(1)
            val modelItemsRef = AtomicReference<ModelMenuItems?>()
            scenario.onActivity {
                val items = ModelMenuItems(requireNotNull(bed)) { deleteSelectionReady.countDown() }
                modelItemsRef.set(items)
                items.createItems()
            }
            assertTrue(
                "The model menu should receive the selected model snapshot from GL",
                deleteSelectionReady.await(5, TimeUnit.SECONDS),
            )
            scenario.onActivity {
                val menuItems = requireNotNull(modelItemsRef.get()).createItems()
                val deleteSelected = requireNotNull(menuItems.firstOrNull {
                    it.titleRes == R.string.ModelMenuDeleteSelected
                })
                assertTrue("Delete selected should be available for the selected object", deleteSelected.enabled)
                deleteSelected.onClick(requireNotNull(viewport))
            }
            val deleteDialogDeadline = SystemClock.uptimeMillis() + 5_000L
            while (!requireNotNull(bed).isWorkspaceMessageDialogAttached() &&
                SystemClock.uptimeMillis() < deleteDialogDeadline
            ) {
                instrumentation.waitForIdleSync()
                SystemClock.sleep(40L)
            }
            assertTrue("Model menu delete confirmation should use the Compose workspace dialog",
                requireNotNull(bed).isWorkspaceMessageDialogAttached())
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity -> activity.onBackPressedDispatcher.onBackPressed() }
            val deleteDismissDeadline = SystemClock.uptimeMillis() + 5_000L
            while (requireNotNull(bed).isWorkspaceMessageDialogAttached() &&
                SystemClock.uptimeMillis() < deleteDismissDeadline
            ) {
                instrumentation.waitForIdleSync()
                SystemClock.sleep(40L)
            }
            scenario.onActivity { activity ->
                assertTrue("Back cancellation must keep the workspace active", !activity.isFinishing)
                assertFalse("Back should cancel model deletion", requireNotNull(bed).isWorkspaceMessageDialogAttached())
                assertSameModelSelection(bed, model)
                assertEquals("Cancel must preserve all model objects", objectCountBeforeDeleteCancel,
                    requireNotNull(bed).currentModel?.objectsCount)
            }
            SystemClock.sleep(350L)

            val transformedModel = CountDownLatch(1)
            glView.queueEvent {
                // Exercise the user-visible drag path with a transform that makes local-space
                // translation differ from build-plate/world-space translation.
                model.rotate(0, 0.0, 0.0, Math.toRadians(37.0))
                model.scale(0, 1.7, 0.65, 1.25)
                glView.renderer.invalidateGlModel(0)
                glView.requestRender()
                transformedModel.countDown()
            }
            assertTrue("The transformed model must be ready before the drag", transformedModel.await(5, TimeUnit.SECONDS))

            val downTime = SystemClock.uptimeMillis()
            val translationBeforeLongPressDrag = Vec3d()
            val boundsMinBeforeLongPressDrag = Vec3d()
            val boundsMaxBeforeLongPressDrag = Vec3d()
            val expectedDragDelta = Vec3d()
            val dragEndScreen = FloatArray(2)
            val translationReady = CountDownLatch(1)
            glView.queueEvent {
                model.getTranslation(0, translationBeforeLongPressDrag)
                model.getBoundingBoxExact(0, boundsMinBeforeLongPressDrag, boundsMaxBeforeLongPressDrag)
                val sx = glView.renderer.viewportWidth.toFloat() / glView.width
                val sy = glView.renderer.viewportHeight.toFloat() / glView.height
                val startX = hit.first * sx
                val startY = hit.second * sy
                val dragEndX = (hit.first + 45f) * sx
                val dragEndY = (hit.second + 35f) * sy
                dragEndScreen[0] = dragEndX
                dragEndScreen[1] = dragEndY
                val surfaceHit = glView.renderer.raycastObjectHit(0, startX, startY)
                val surfaceZ = requireNotNull(surfaceHit).position.z
                val startPoint = requireNotNull(glView.renderer.intersectHorizontalPlane(startX, startY, surfaceZ))
                val endPoint = requireNotNull(glView.renderer.intersectHorizontalPlane(dragEndX, dragEndY, surfaceZ))
                expectedDragDelta.set(endPoint.x - startPoint.x, endPoint.y - startPoint.y, 0.0)
                translationReady.countDown()
            }
            assertTrue("Model translation should be readable before long-press drag",
                translationReady.await(5, TimeUnit.SECONDS))
            scenario.onActivity { dispatch(glView, MotionEvent.ACTION_DOWN, hit.first, hit.second, downTime, downTime) }
            // The model raycast is deferred until this deliberate long press, then drag it.
            SystemClock.sleep(650L)
            val longClickGestureField = GLView::class.java.getDeclaredField("longClickGesture")
                .apply { isAccessible = true }
            val longClickDeadline = SystemClock.uptimeMillis() + 5_000L
            while (!longClickGestureField.getBoolean(glView) && SystemClock.uptimeMillis() < longClickDeadline) {
                instrumentation.waitForIdleSync()
                SystemClock.sleep(30L)
            }
            assertTrue("A held model touch should activate drag after the deferred precise pick",
                longClickGestureField.getBoolean(glView))
            scenario.onActivity {
                val now = SystemClock.uptimeMillis()
                dispatch(glView, MotionEvent.ACTION_MOVE, hit.first + 25f, hit.second + 20f, downTime, now)
                dispatch(glView, MotionEvent.ACTION_MOVE, hit.first + 45f, hit.second + 35f,
                    downTime, now + 40L)
            }
            SystemClock.sleep(80L)
            scenario.onActivity {
                val now = SystemClock.uptimeMillis()
                dispatch(glView, MotionEvent.ACTION_UP, hit.first + 45f, hit.second + 35f, downTime, now)
            }
            awaitRendererQueue(glView)
            val translationAfterLongPressDrag = Vec3d()
            val boundsMinAfterLongPressDrag = Vec3d()
            val boundsMaxAfterLongPressDrag = Vec3d()
            val moved = CountDownLatch(1)
            glView.queueEvent {
                model.getTranslation(0, translationAfterLongPressDrag)
                model.getBoundingBoxExact(0, boundsMinAfterLongPressDrag, boundsMaxAfterLongPressDrag)
                moved.countDown()
            }
            assertTrue("Long-press drag should commit the moved model on GL", moved.await(5, TimeUnit.SECONDS))
            assertTrue("Long-press dragging should change the model position",
                kotlin.math.abs(translationBeforeLongPressDrag.x - translationAfterLongPressDrag.x) > 1e-4 ||
                    kotlin.math.abs(translationBeforeLongPressDrag.y - translationAfterLongPressDrag.y) > 1e-4)
            assertEquals("Dragged mesh X must follow the released screen point at the picked surface height",
                translationBeforeLongPressDrag.x + expectedDragDelta.x, translationAfterLongPressDrag.x, 0.05)
            assertEquals("Dragged mesh Y must follow the released screen point at the picked surface height",
                translationBeforeLongPressDrag.y + expectedDragDelta.y, translationAfterLongPressDrag.y, 0.05)
            assertEquals("The object's displayed bounds must follow the released point on X",
                expectedDragDelta.x, boundsMinAfterLongPressDrag.x - boundsMinBeforeLongPressDrag.x, 0.05)
            assertEquals("The object's displayed bounds must follow the released point on Y",
                expectedDragDelta.y, boundsMinAfterLongPressDrag.y - boundsMinBeforeLongPressDrag.y, 0.05)
            assertEquals("Dragging must not change the transformed object's X footprint",
                boundsMaxBeforeLongPressDrag.x - boundsMinBeforeLongPressDrag.x,
                boundsMaxAfterLongPressDrag.x - boundsMinAfterLongPressDrag.x, 0.01)
            assertEquals("Dragging must not change the transformed object's Y footprint",
                boundsMaxBeforeLongPressDrag.y - boundsMinBeforeLongPressDrag.y,
                boundsMaxAfterLongPressDrag.y - boundsMinAfterLongPressDrag.y, 0.01)
            // Verify the native outline geometry drawn as the selection tile, not only the
            // model's calculated bounds. It owns a separate cache from the rendered mesh.
            val selectionOutlineField = GLRenderer::class.java.getDeclaredField("selectionModel")
                .apply { isAccessible = true }
            glView.requestRender()
            SystemClock.sleep(200L)
            val outlineBounds = DoubleArray(6)
            val outlineReady = CountDownLatch(1)
            glView.queueEvent {
                val outline = selectionOutlineField.get(glView.renderer) as? GLModel
                if (outline != null && outline.isInitialized()) outline.getBoundingBox().copyInto(outlineBounds)
                outlineReady.countDown()
            }
            assertTrue("The moved selection outline should be rebuilt on the GL thread",
                outlineReady.await(5, TimeUnit.SECONDS))
            assertEquals("The selection tile minimum X must match the moved mesh",
                boundsMinAfterLongPressDrag.x, outlineBounds[0], 0.01)
            assertEquals("The selection tile minimum Y must match the moved mesh",
                boundsMinAfterLongPressDrag.y, outlineBounds[1], 0.01)
            assertEquals("The selection tile maximum X must match the moved mesh",
                boundsMaxAfterLongPressDrag.x, outlineBounds[3], 0.01)
            assertEquals("The selection tile maximum Y must match the moved mesh",
                boundsMaxAfterLongPressDrag.y, outlineBounds[4], 0.01)
            val releasePick = AtomicInteger(-2)
            val releasePickReady = CountDownLatch(1)
            glView.queueEvent {
                // Verify the renderer's rebuilt picking mesh follows the committed model, not just
                // the native bounding box and the temporary selection-translation preview.
                releasePick.set(glView.renderer.raycastObjectIndex(dragEndScreen[0], dragEndScreen[1]))
                releasePickReady.countDown()
            }
            assertTrue("The committed mesh should be pickable at the released finger position",
                releasePickReady.await(5, TimeUnit.SECONDS))
            assertEquals("The rendered/pickable object must land under the released finger", 0, releasePick.get())
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertSameModelSelection(bed, model)
                assertModelSelectedWithoutModal(requireNotNull(bed))
            }

            // Explicitly opening the model action list should use the modal Compose surface;
            // system Back must dismiss that surface before leaving the workspace.
            scenario.onActivity { activity ->
                requireNotNull(bed).showSelectedModelContextMenu()
                assertTrue("The workspace activity should still be active", activity.isFinishing.not())
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertTrue("The model action dialog should attach to the workspace", requireNotNull(bed).isModelContextMenuAttached())
                assertEquals("The selected model should remain active behind its actions", 0,
                    requireNotNull(bed).glView!!.renderer.selectedObject)
            }
            scenario.onActivity { activity -> activity.onBackPressedDispatcher.onBackPressed() }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertTrue("Back should leave the workspace activity active", activity.isFinishing.not())
                assertFalse("Back should dismiss the model action dialog", requireNotNull(bed).isModelContextMenuAttached())
            }
            SystemClock.sleep(350L)

            // Model object workflows now use the exclusive Compose workspace-dialog host.
            scenario.onActivity {
                val workspace = requireNotNull(bed)
                val actions = FileMenuObjectActions(workspace)
                actions.showObjectsDialog(requireNotNull(viewport))
            }
            val objectListDialogDeadline = SystemClock.uptimeMillis() + 5_000L
            while (!requireNotNull(bed).isWorkspaceMessageDialogAttached() &&
                SystemClock.uptimeMillis() < objectListDialogDeadline
            ) {
                instrumentation.waitForIdleSync()
                SystemClock.sleep(40L)
            }
            assertTrue("The object list should attach as a Compose workspace dialog",
                requireNotNull(bed).isWorkspaceMessageDialogAttached())
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity -> activity.onBackPressedDispatcher.onBackPressed() }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertTrue("Closing the object list must keep the workspace active", !activity.isFinishing)
                assertFalse("Back should close the object list", requireNotNull(bed).isWorkspaceMessageDialogAttached())
            }

            scenario.onActivity {
                FileMenuObjectActions(requireNotNull(bed)).showDuplicateDialog(requireNotNull(viewport))
            }
            val duplicateDialogDeadline = SystemClock.uptimeMillis() + 5_000L
            while (!requireNotNull(bed).isWorkspaceMessageDialogAttached() &&
                SystemClock.uptimeMillis() < duplicateDialogDeadline
            ) {
                instrumentation.waitForIdleSync()
                SystemClock.sleep(40L)
            }
            assertTrue("Duplicate count input should attach as a Compose workspace dialog",
                requireNotNull(bed).isWorkspaceMessageDialogAttached())
            instrumentation.waitForIdleSync()
            var duplicateKeyboardVisible = false
            val duplicateImeDeadline = SystemClock.uptimeMillis() + 5_000L
            while (!duplicateKeyboardVisible && SystemClock.uptimeMillis() < duplicateImeDeadline) {
                scenario.onActivity { activity ->
                    duplicateKeyboardVisible = ViewCompat.getRootWindowInsets(activity.window.decorView)
                        ?.isVisible(WindowInsetsCompat.Type.ime()) == true
                }
                if (!duplicateKeyboardVisible) SystemClock.sleep(50L)
            }
            if (duplicateKeyboardVisible) {
                // On devices with a software keyboard, system Back hides the IME first.
                instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                instrumentation.waitForIdleSync()
                scenario.onActivity { assertTrue("First Back hides the keyboard and keeps duplicate input open",
                    requireNotNull(bed).isWorkspaceMessageDialogAttached()) }
                var duplicateKeyboardHidden = false
                val hiddenImeDeadline = SystemClock.uptimeMillis() + 5_000L
                while (!duplicateKeyboardHidden && SystemClock.uptimeMillis() < hiddenImeDeadline) {
                    scenario.onActivity { activity ->
                        duplicateKeyboardHidden = ViewCompat.getRootWindowInsets(activity.window.decorView)
                            ?.isVisible(WindowInsetsCompat.Type.ime()) != true
                    }
                    if (!duplicateKeyboardHidden) SystemClock.sleep(50L)
                }
                assertTrue("First Back should hide the duplicate input keyboard", duplicateKeyboardHidden)
            } else {
                // AVDs with an attached hardware keyboard may suppress the software IME.
                android.util.Log.i("MainActivityTouchDeviceTest", "IME visibility check unavailable on this AVD")
            }
            scenario.onActivity { activity -> activity.onBackPressedDispatcher.onBackPressed() }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertTrue("Closing duplicate input must keep the workspace active", !activity.isFinishing)
                assertFalse("Back should close duplicate input", requireNotNull(bed).isWorkspaceMessageDialogAttached())
            }
            // The Compose AlertDialog is backed by its own Android dialog window. Let its
            // dismissal finish before replacing it with the next transient dialog.
            SystemClock.sleep(350L)

            scenario.onActivity {
                val fileMenu = FileMenu().apply { onSetBed(requireNotNull(bed)) }
                val export = FileMenu::class.java.getDeclaredMethod("showProfileExportDialog", android.view.View::class.java)
                    .apply { isAccessible = true }
                export.invoke(fileMenu, requireNotNull(viewport))
                assertTrue("Profile export selection should attach as a Compose workspace dialog",
                    requireNotNull(bed).isWorkspaceMessageDialogAttached())
            }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(250L)
            scenario.onActivity { activity -> activity.onBackPressedDispatcher.onBackPressed() }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertTrue("Closing profile selection must keep the workspace active", !activity.isFinishing)
                assertFalse("Back should close profile selection", requireNotNull(bed).isWorkspaceMessageDialogAttached())
            }
            SystemClock.sleep(350L)

            scenario.onActivity {
                val workspace = requireNotNull(bed)
                val noOp = java.util.function.Consumer<android.view.View> { }
                val items = TransformMenuItems(workspace, noOp, noOp, noOp, noOp)
                val simplify = TransformMenuItems::class.java
                    .getDeclaredMethod("showSimplifyDialog", android.view.View::class.java)
                    .apply { isAccessible = true }
                simplify.invoke(items, requireNotNull(viewport))
            }
            val simplifyDialogDeadline = SystemClock.uptimeMillis() + 5_000L
            while (!requireNotNull(bed).isWorkspaceMessageDialogAttached() &&
                SystemClock.uptimeMillis() < simplifyDialogDeadline
            ) {
                instrumentation.waitForIdleSync()
                SystemClock.sleep(40L)
            }
            assertTrue("Simplification choices should attach as a Compose workspace dialog",
                requireNotNull(bed).isWorkspaceMessageDialogAttached())
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity -> activity.onBackPressedDispatcher.onBackPressed() }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertTrue("Closing simplification choices must keep the workspace active", !activity.isFinishing)
                assertFalse("Back should close simplification choices", requireNotNull(bed).isWorkspaceMessageDialogAttached())
                assertEquals("Cancel must leave the model geometry unchanged", model.getTriangleCount(0),
                    requireNotNull(bed).currentModel?.getTriangleCount(0))
            }
            SystemClock.sleep(350L)

            val cameraControlModeBefore = Prefs.getCameraControlMode()
            scenario.onActivity { activity ->
                val cameraMenu = CameraMenu().apply { onSetBed(requireNotNull(bed)) }
                val controlMode = CameraMenu::class.java
                    .getDeclaredMethod("showControlModeDialog", android.content.Context::class.java)
                    .apply { isAccessible = true }
                controlMode.invoke(cameraMenu, activity)
                assertTrue("Camera control mode should attach as a Compose workspace dialog",
                    requireNotNull(bed).isWorkspaceMessageDialogAttached())
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity -> activity.onBackPressedDispatcher.onBackPressed() }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertTrue("Closing camera control mode must keep the workspace active", !activity.isFinishing)
                assertFalse("Back should close camera control mode", requireNotNull(bed).isWorkspaceMessageDialogAttached())
                assertEquals("Cancel must not change the stored camera control mode", cameraControlModeBefore,
                    Prefs.getCameraControlMode())
            }
            SystemClock.sleep(350L)

            val workspaceDialogDismissed = java.util.concurrent.atomic.AtomicBoolean(false)
            scenario.onActivity { activity ->
                val workspace = requireNotNull(bed)
                workspace.showWorkspaceMessageDialog(
                    title = "Test confirmation",
                    message = "Confirm route",
                    confirmLabel = "OK",
                    dismissLabel = "Cancel",
                    onDismiss = { workspaceDialogDismissed.set(true) },
                )
                assertTrue("Compose confirmation should attach to the workspace", workspace.isWorkspaceMessageDialogAttached())
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity -> activity.onBackPressedDispatcher.onBackPressed() }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertTrue("Back should leave the workspace activity active", activity.isFinishing.not())
                assertFalse("Back should close the Compose confirmation", requireNotNull(bed).isWorkspaceMessageDialogAttached())
                assertTrue("Back should resolve the Compose confirmation as dismissed", workspaceDialogDismissed.get())
            }
            SystemClock.sleep(350L)

            scenario.onActivity {
                SliceUploadController.showPreparationFailure(requireNotNull(bed), "Test preparation failure")
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                assertTrue(
                    "Slice preparation failures should use the Compose workspace dialog",
                    requireNotNull(bed).isWorkspaceMessageDialogAttached(),
                )
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity -> activity.onBackPressedDispatcher.onBackPressed() }
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                assertFalse(
                    "Back should dismiss the slice preparation failure dialog",
                    requireNotNull(bed).isWorkspaceMessageDialogAttached(),
                )
            }
            SystemClock.sleep(350L)

            assertImportPromptDismissesWithoutApproval(
                scenario, instrumentation, requireNotNull(bed), "confirmImportSimplification",
                emptyArray<Class<*>>(), emptyArray<Any>(),
            )
            assertImportPromptDismissesWithoutApproval(
                scenario,
                instrumentation,
                requireNotNull(bed),
                "confirmLargeStlImport",
                arrayOf(Long::class.javaPrimitiveType!!),
                arrayOf(1_000_000L),
            )

            // Dense objects still support safe object-to-object placement without asking the
            // native surface raycaster for its exact triangle tree. The placement
            // uses exact object bounds, then combines the assembly as printable multipart data.
            val cubeFile = File(context.cacheDir, "touch-test-placement-cube.stl")
            val cubeSourceFile = File(context.cacheDir, "touch-test-placement-cube-source.stl")
            var cubeSource: Model? = null
            try {
                context.assets.open("models/xyz_cube.stl").use { input ->
                    cubeSourceFile.outputStream().use(input::copyTo)
                }
                cubeSource = Model(cubeSourceFile)
                val placementReady = CountDownLatch(1)
                val placementResult = AtomicReference<Int>()
                val selectionAccepted = AtomicReference<Boolean>()
                val targetBefore = AtomicReference<DoubleArray>()
                val targetAfter = AtomicReference<DoubleArray>()
                val movingHeight = AtomicReference<Double>()
                val placedBounds = AtomicReference<DoubleArray>()
                val assignedFilaments = AtomicReference<Pair<IntArray, IntArray>>()
                val objectCountAfterPlacement = AtomicInteger(-1)
                val glView = requireNotNull(bed).glView!!
                glView.queueEvent {
                    val renderer = glView.renderer
                    val activeModel = requireNotNull(renderer.model)
                    val min = Vec3d(); val max = Vec3d()
                    activeModel.getBoundingBoxExact(0, min, max)
                    targetBefore.set(doubleArrayOf(min.x, min.y, min.z, max.x, max.y, max.z))
                    val sourceMin = Vec3d(); val sourceMax = Vec3d()
                    requireNotNull(cubeSource).getBoundingBoxExact(0, sourceMin, sourceMax)
                    movingHeight.set(sourceMax.z - sourceMin.z)
                    activeModel.addObject(requireNotNull(cubeSource), 0)
                    val movingIndex = activeModel.objectsCount - 1
                    activeModel.setUniformFilament(0, 1)
                    activeModel.setUniformFilament(movingIndex, 2)
                    selectionAccepted.set(renderer.setSelectedObject(movingIndex))
                    placementResult.set(renderer.placeSelectedObjectsOnObject(0))
                    val resultMin = Vec3d(); val resultMax = Vec3d()
                    activeModel.getBoundingBoxExact(0, resultMin, resultMax)
                    targetAfter.set(doubleArrayOf(resultMin.x, resultMin.y, resultMin.z,
                        resultMax.x, resultMax.y, resultMax.z))
                    activeModel.getBoundingBoxExact(movingIndex, resultMin, resultMax)
                    placedBounds.set(doubleArrayOf(resultMin.x, resultMin.y, resultMin.z,
                        resultMax.x, resultMax.y, resultMax.z))
                    assignedFilaments.set(activeModel.getUsedFilaments(0) to activeModel.getUsedFilaments(movingIndex))
                    objectCountAfterPlacement.set(activeModel.objectsCount)
                    glView.requestRender()
                    placementReady.countDown()
                }
                assertTrue("dense-target placement should finish on the GL thread",
                    placementReady.await(20, TimeUnit.SECONDS))
                assertTrue("the generated object should become the selected moving object",
                    requireNotNull(selectionAccepted.get()))
                assertEquals("placing the selected cube on the M12 should succeed", 1,
                    requireNotNull(placementResult.get()))
                assertEquals("surface placement must leave the models independently selectable", 2,
                    objectCountAfterPlacement.get())
                val before = requireNotNull(targetBefore.get())
                val unchangedTarget = requireNotNull(targetAfter.get())
                val after = requireNotNull(placedBounds.get())
                assertArrayEquals("surface placement must not alter the target M12", before, unchangedTarget, 0.03)
                assertEquals("the cube bottom must touch the M12 top", before[5], after[2], 0.03)
                assertEquals("the cube must retain its full height on top of the M12",
                    before[5] + requireNotNull(movingHeight.get()), after[5], 0.03)
                val materials = requireNotNull(assignedFilaments.get())
                assertArrayEquals("the target keeps its original filament", intArrayOf(1), materials.first)
                assertArrayEquals("the placed cube keeps its own filament", intArrayOf(2), materials.second)
                scenario.onActivity { requireNotNull(bed).updateModel() }
            } finally {
                cubeSource?.release()
                cubeFile.delete()
                cubeSourceFile.delete()
            }
        } finally {
            scenario.close()
            modelFile.delete()
        }
    }

    private fun assertImportPromptDismissesWithoutApproval(
        scenario: ActivityScenario<MainActivity>,
        instrumentation: android.app.Instrumentation,
        bed: BedFragment,
        methodName: String,
        parameterTypes: Array<Class<*>>,
        arguments: Array<Any>,
    ) {
        val decision = AtomicReference<Boolean?>()
        val method = BedFragment::class.java.getDeclaredMethod(methodName, *parameterTypes).apply { isAccessible = true }
        val worker = Thread { decision.set(method.invoke(bed, *arguments) as Boolean) }
        worker.start()

        val deadline = SystemClock.uptimeMillis() + 5_000L
        var attached = false
        while (!attached && SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            scenario.onActivity { attached = bed.isWorkspaceMessageDialogAttached() }
            if (!attached) SystemClock.sleep(50L)
        }
        assertTrue("$methodName should show the Compose workspace dialog", attached)
        scenario.onActivity { activity -> activity.onBackPressedDispatcher.onBackPressed() }
        worker.join(5_000L)
        assertTrue("$methodName should release its waiting import worker", !worker.isAlive)
        assertEquals("Back must refuse $methodName unless the user explicitly confirms", false, decision.get())
        // The worker is released from the dialog callback before Compose has finished
        // disposing the Dialog window. Let that dismissal settle before the next prompt.
        SystemClock.sleep(350L)
        instrumentation.waitForIdleSync()
    }

    private fun assertSameModelSelection(bed: BedFragment?, model: Model) {
        val current = requireNotNull(bed)
        assertTrue("The test model should still be on the active plate", current.currentModel === model)
        assertEquals("The touched model should remain the selected object", 0, current.glView!!.renderer.selectedObject)
    }

    private fun assertModelSelectedWithoutModal(bed: BedFragment) {
        // Workspace panels are authoritative; Activity focus may be owned by instrumentation.
        assertFalse("Tapping the model must not open the model context menu", bed.isModelContextMenuAttached())
        assertFalse("Tapping the model must not open a workspace message dialog", bed.isWorkspaceMessageDialogAttached())
    }

    private fun tap(view: GLView, x: Float, y: Float) {
        val downTime = SystemClock.uptimeMillis()
        dispatch(view, MotionEvent.ACTION_DOWN, x, y, downTime, downTime)
        SystemClock.sleep(50L)
        dispatch(view, MotionEvent.ACTION_UP, x, y, downTime, SystemClock.uptimeMillis())
    }

    private fun awaitRendererQueue(view: GLView) {
        val rendered = CountDownLatch(1)
        view.queueEvent { rendered.countDown() }
        assertTrue("Queued GL touch events should finish", rendered.await(5, TimeUnit.SECONDS))
    }

    private fun readCameraPose(view: GLView): DoubleArray {
        val finished = CountDownLatch(1)
        val pose = AtomicReference<DoubleArray>()
        view.queueEvent {
            val camera = view.renderer.camera
            pose.set(doubleArrayOf(
                camera.origin.x, camera.origin.y, camera.origin.z,
                camera.position.x, camera.position.y, camera.position.z,
            ))
            finished.countDown()
        }
        assertTrue("The camera pose should be readable from the GL thread", finished.await(5, TimeUnit.SECONDS))
        return requireNotNull(pose.get())
    }

    private fun findEmptyPlatePoint(view: GLView): Pair<Float, Float> {
        val renderScale = Prefs.getRenderScale()
        // Stay away from the model's center and scan inside the actual GL surface rather than
        // using an arbitrary screen coordinate that could hit a toolbar or an overlay.
        for (row in 2..22 step 2) {
            for (column in 2..22 step 2) {
                val x = view.width * column / 24f
                val y = view.height * row / 24f
                if (view.renderer.raycastObjectIndex(x * renderScale, y * renderScale) == -1) {
                    return x to y
                }
            }
        }
        throw AssertionError("Could not find an empty area on the visible build plate")
    }

    private fun dispatch(view: GLView, action: Int, x: Float, y: Float, downTime: Long, eventTime: Long) {
        val event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
        try {
            assertTrue("GLView should consume the test touch sequence", view.dispatchTouchEvent(event))
        } finally {
            event.recycle()
        }
    }

}
