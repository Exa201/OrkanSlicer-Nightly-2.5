package ru.ytkab0bp.slicebeam.view
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Region
import android.graphics.Typeface
import android.opengl.GLES30
import android.opengl.GLException
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.ViewConfiguration
import java.nio.IntBuffer
import java.util.ArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicLong
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.SliceBeam
import ru.ytkab0bp.slicebeam.events.LongClickTranslationEvent
import ru.ytkab0bp.slicebeam.events.NeedSnackbarEvent
import ru.ytkab0bp.slicebeam.render.DevicePerformancePolicy
import ru.ytkab0bp.slicebeam.render.GLRenderer
import ru.ytkab0bp.slicebeam.render.FillBedCallbackResultPolicy
import ru.ytkab0bp.slicebeam.render.MODEL_EDIT_STALE_SELECTION
import ru.ytkab0bp.slicebeam.render.ModelAlignmentAxis
import ru.ytkab0bp.slicebeam.render.ModelAlignmentCoordinateSystem
import ru.ytkab0bp.slicebeam.render.ModelSelectionSnapshot
import ru.ytkab0bp.slicebeam.slic3r.Bed3D
import ru.ytkab0bp.slicebeam.slic3r.BooleanOperation
import ru.ytkab0bp.slicebeam.slic3r.ObjectDistributionAxis
import ru.ytkab0bp.slicebeam.slic3r.GLModel
import ru.ytkab0bp.slicebeam.slic3r.Model
import ru.ytkab0bp.slicebeam.events.ObjectsListChangedEvent
import ru.ytkab0bp.slicebeam.theme.IThemeView
import ru.ytkab0bp.slicebeam.theme.ThemesRepo
import ru.ytkab0bp.slicebeam.utils.Prefs
import ru.ytkab0bp.slicebeam.utils.FillBedPlanner
import ru.ytkab0bp.slicebeam.utils.Vec3d
import ru.ytkab0bp.slicebeam.utils.ViewUtils
class GLView(context:Context):GLSurfaceView(context), IThemeView {
lateinit var renderer: GLRenderer
private val renderRequestLock = Any()
private val renderRequestHandler = Handler(Looper.getMainLooper())
private var nextRenderAllowedAtMs = 0L
private var delayedRenderPending = false
private val delayedRenderRequest = Runnable {
    synchronized(renderRequestLock) { delayedRenderPending = false }
    requestRender()
}
private var lastX:Float = 0.toFloat()
private var lastY:Float = 0.toFloat()
private var lastLength:Float = 0.toFloat()
private var touchSlop:Int = 0
// Invalidates asynchronous Fill-Bed snapshots whenever an explicit plate-wide
// placement action starts. Model geometry revisions remain the primary guard;
// this token also covers actions that preserve the current transforms.
private val plateLayoutOperationRevision = AtomicLong(0L)
private var fromTwoPointers:Boolean = false
private var onePointerGesture:Boolean = false
private var twoPointerGesture:Boolean = false
private var longClickGesture:Boolean = false
private var longClickMoved:Boolean = false
@Volatile private var touchGestureGeneration:Long = 0L
@Volatile private var pointerDownForLongClick:Boolean = false
@Volatile private var longClickActivationPending:Boolean = false
private var isScaling:Boolean = false
@Volatile var isFillBedInProgress:Boolean = false
    private set
private var primeTowerGesture:Boolean = false
private var primeTowerMoved:Boolean = false
private var primeTowerDownX:Float = 0f
private var primeTowerDownY:Float = 0f
var onPrimeTowerTap:(()->Unit)? = null
private var longClickTargetObject = -1
private var longClickStartX = 0.0
private var longClickStartY = 0.0
private var longClickPlaneZ = Double.NaN
private var longClickUsesSurfacePlane = false
private val tapGestureGate = TapGestureGate()
// When true and the renderer is in paint mode, a single finger paints instead of orbiting.
 var isPaintBrushActive:Boolean = false
/** One-finger Orca variable-layer-height brush; two-finger camera gestures remain enabled. */
@Volatile var isVariableLayerHeightBrushActive: Boolean = false
@Volatile var variableLayerHeightConfigPath: String? = null
@Volatile var variableLayerHeightBrushBandWidthMm: Double = 5.0
@Volatile var variableLayerHeightBrushDeltaMm: Double = 0.05
@Volatile var variableLayerHeightBrushAction: Int = 2
@Volatile var variableLayerHeightQualityFactor: Float = 0.5f
@Volatile var onVariableLayerHeightPaintComplete: (() -> Unit)? = null
private var paintStrokeActive = false
private var variableLayerHeightStrokeActive = false
private var variableLayerHeightStrokeChanged = false
private var lastVariableLayerHeightBrushAtMs = 0L
private val variableLayerHeightUnsupportedObjects = HashSet<Int>()
private val paintInputCoalescer = PaintInputCoalescer()
private var lastPaintPoint: PaintInputCoalescer.Point? = null
private var tempVec = Vec3d()
private var longClickOffset = Vec3d()
private var longClickTranslation = Vec3d()
private var longClickHitResults = ArrayList<GLModel.HitResult>()
private var lastActionTime = System.currentTimeMillis()
// A few devices report a very short accessibility long-press threshold; keep ordinary presses from opening actions.
private val longPressTimeoutMs = maxOf(ViewConfiguration.getLongPressTimeout(), 500).toLong()
private val longClick = Runnable {
    val generation = touchGestureGeneration
    if (!pointerDownForLongClick) return@Runnable
    longClickActivationPending = true
    val x = surfaceTouchX(lastX)
    val y = surfaceTouchY(lastY)
    // Only a stationary long press needs an object pick. Running this on ACTION_DOWN
    // used to build native raycast data before every camera orbit, stalling the GL queue
    // behind potentially expensive mesh topology work.
    queueEvent {
        var hitFound = false
        var target = -1
        if (touchGestureGeneration == generation && pointerDownForLongClick) {
            val activeModel = renderer.model
            val bed = renderer.bed
            if (renderer.gcodeResult == null && activeModel != null && bed != null) {
                target = renderer.raycastObjectIndex(x, y)
                if (target in 0 until activeModel.objectsCount) {
                    val objectHit = renderer.raycastObjectHit(target, x, y)
                    val surfacePoint = objectHit?.let {
                        renderer.intersectHorizontalPlane(x, y, it.position.z)
                    }
                    if (surfacePoint != null) {
                        longClickStartX = surfacePoint.x
                        longClickStartY = surfacePoint.y
                        longClickPlaneZ = surfacePoint.z
                        longClickUsesSurfacePlane = true
                        if (renderer.focusForDrag(target)) requestRender()
                        hitFound = true
                    } else {
                        // Near-horizontal camera angles can make the picked-height plane
                        // parallel to the view ray. Preserve the bed-plane fallback then.
                        bed.raycaster.raycast(renderer, longClickHitResults, x, y)
                        val bedHit = longClickHitResults.firstOrNull()
                        if (bedHit != null) {
                            longClickStartX = bedHit.position.x
                            longClickStartY = bedHit.position.y
                            longClickPlaneZ = bedHit.position.z
                            longClickUsesSurfacePlane = false
                            activeModel.getTranslation(target, tempVec)
                            longClickOffset.x = bedHit.position.x - tempVec.x
                            longClickOffset.y = bedHit.position.y - tempVec.y
                            if (renderer.focusForDrag(target)) requestRender()
                            hitFound = true
                        }
                    }
                }
            }
        }
        ViewUtils.postOnMainThread {
            if (touchGestureGeneration != generation || !pointerDownForLongClick) return@postOnMainThread
            longClickActivationPending = false
            if (!hitFound) return@postOnMainThread
            longClickTargetObject = target
            tapGestureGate.invalidate()
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            longClickGesture = true
            longClickMoved = false
            longClickTranslation.set(0.0, 0.0, 0.0)
        }
    }
}
private var path = Path()
private var paint = Paint(Paint.ANTI_ALIAS_FLAG)
private var xferPaint = Paint(Paint.ANTI_ALIAS_FLAG)
private var invalidBedText = TextPaint(Paint.ANTI_ALIAS_FLAG)
private var invalidBedDescriptionLayout: StaticLayout? = null
private var lastScale:Float = 0.toFloat()
@Volatile private var glSurfaceWidth = 0
@Volatile private var glSurfaceHeight = 0
private fun surfaceTouchX(x: Float): Float =
    DevicePerformancePolicy.mapTouchCoordinate(x, width, glSurfaceWidth)
private fun surfaceTouchY(y: Float): Float =
    DevicePerformancePolicy.mapTouchCoordinate(y, height, glSurfaceHeight)

/** Throttle viewport redraw requests without delaying input/model updates on the GL queue. */
override fun requestRender() {
    val fpsCap = Prefs.getViewportFpsCap()
    if (fpsCap <= 0) {
        synchronized(renderRequestLock) {
            delayedRenderPending = false
            renderRequestHandler.removeCallbacks(delayedRenderRequest)
            nextRenderAllowedAtMs = SystemClock.uptimeMillis()
        }
        super.requestRender()
        return
    }

    var renderNow = false
    synchronized(renderRequestLock) {
        val now = SystemClock.uptimeMillis()
        val delay = DevicePerformancePolicy.frameRequestDelayMs(now, nextRenderAllowedAtMs, fpsCap)
        if (delay == 0L) {
            delayedRenderPending = false
            renderRequestHandler.removeCallbacks(delayedRenderRequest)
            nextRenderAllowedAtMs = DevicePerformancePolicy.nextAllowedFrameAtMs(now, fpsCap)
            renderNow = true
        } else if (!delayedRenderPending) {
            delayedRenderPending = true
            renderRequestHandler.postDelayed(delayedRenderRequest, delay)
        }
    }
    if (renderNow) super.requestRender()
}
private fun paintAtScreen(x:Float, y:Float) {
if (paintInputCoalescer.submit(surfaceTouchX(x), surfaceTouchY(y))) {
    queueEvent { drainPaintInput() }
}
}
private fun paintVariableLayerHeightAtScreen(x: Float, y: Float) {
    val screenX = surfaceTouchX(x)
    val screenY = surfaceTouchY(y)
    queueEvent {
        val now = SystemClock.uptimeMillis()
        if (lastVariableLayerHeightBrushAtMs != 0L && now - lastVariableLayerHeightBrushAtMs < 55L) return@queueEvent
        lastVariableLayerHeightBrushAtMs = now
        val activeModel = renderer.model ?: return@queueEvent
        val objectIndex = renderer.raycastObjectIndex(screenX, screenY)
        if (objectIndex !in 0 until activeModel.objectsCount) return@queueEvent
        if (!renderer.prepareExactSurfaceRaycast(objectIndex)) {
            if (variableLayerHeightUnsupportedObjects.add(objectIndex)) {
                SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.VariableLayerRaycastUnavailable))
            }
            return@queueEvent
        }
        val hit = renderer.raycastObjectHit(objectIndex, screenX, screenY) ?: return@queueEvent
        if (hit.approximate) return@queueEvent
        val configPath = variableLayerHeightConfigPath ?: return@queueEvent
        val changed = runCatching {
            activeModel.adjustLayerHeightAtWorldPoint(
                objectIndex,
                configPath,
                hit.position.x,
                hit.position.y,
                hit.position.z,
                variableLayerHeightBrushBandWidthMm,
                variableLayerHeightBrushDeltaMm,
                variableLayerHeightBrushAction,
                variableLayerHeightQualityFactor,
            )
        }.getOrElse { error ->
            SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.VariableLayerFailed, error.message))
            false
        }
        if (changed) {
            variableLayerHeightStrokeChanged = true
            requestRender()
        }
    }
}
private fun finishVariableLayerHeightStrokeOnGlThread() {
    lastVariableLayerHeightBrushAtMs = 0L
    if (!variableLayerHeightStrokeChanged) return
    variableLayerHeightStrokeChanged = false
    requestRender()
    ViewUtils.postOnMainThread {
        SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.VariableLayerPaintApplied))
        onVariableLayerHeightPaintComplete?.invoke()
    }
}
private fun drainPaintInput() {
    val segment = paintInputCoalescer.takePending()
    if (segment != null) {
        applyPaintSegment(segment)
        requestRender()
    }
    if (paintInputCoalescer.finishDrain()) queueEvent { drainPaintInput() }
}
private fun endPaintStrokeOnGlThread() {
    // A finger-up can be queued while a native preview build is still running. Apply the
    // latest coalesced point before ending the native transaction so neither the preview nor
    // the committed brush stroke loses the final section of a quick gesture.
    paintInputCoalescer.flush()?.let(::applyPaintSegment)
    renderer.endPaintStroke()
    lastPaintPoint = null
    requestRender()
}
private fun applyPaintSegment(segment: PaintInputCoalescer.Segment) {
    // If the GL queue was busy when a stroke began, submit() may coalesce DOWN and several
    // MOVE events together. Paint the first point before interpolating to the latest point so
    // a fast initial swipe cannot lose its leading section.
    if (lastPaintPoint == null) applyPaintPoint(segment.start)
    if (segment.end != segment.start) applyPaintPoint(segment.end)
}
private fun applyPaintPoint(point: PaintInputCoalescer.Point) {
    val previous = lastPaintPoint
    if (previous == null || renderer.paintTool != GLRenderer.PAINT_TOOL_BRUSH) {
        renderer.paintAt(point.x, point.y)
    } else {
        // Coalescing prevents a slow native preview from building a queue of stale events.
        // Interpolate the skipped brush path at screen-space intervals so a quick drag still
        // covers the same continuous surface instead of leaving gaps between samples.
        val distance = kotlin.math.hypot(
            (point.x - previous.x).toDouble(),
            (point.y - previous.y).toDouble(),
        )
        val steps = kotlin.math.ceil(distance / 8.0).toInt().coerceAtLeast(1)
        for (step in 1..steps) {
            val fraction = step.toFloat() / steps
            renderer.paintAt(
                previous.x + (point.x - previous.x) * fraction,
                previous.y + (point.y - previous.y) * fraction,
                rebuildPreview = step == steps,
            )
        }
    }
    lastPaintPoint = point
}
fun interface FillBedCallback {
// A negative value means the asynchronous fill-bed result became stale while another
// workspace action changed the model. Callers must not report that as "no room".
fun onFillBedComplete(addedCopies:Int)
}
init{
xferPaint.setXfermode(PorterDuffXfermode(PorterDuff.Mode.CLEAR))
setEGLContextClientVersion(3)
// Keeping the GLES context avoids rebuilding shaders, textures and model
 // buffers whenever Android briefly pauses the activity (app switch,
 // permission dialog, screen off). This noticeably reduces return-time
 // stutter for larger plates without increasing per-frame work.
 setPreserveEGLContextOnPause(true)
renderer = GLRenderer(this)
setRenderer(renderer)
setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY)
touchSlop = ViewConfiguration.get(context).getScaledTouchSlop()
setWillNotDraw(false)
}
fun arrange(onComplete: ((Boolean) -> Unit)? = null) {
    if (renderer.model == null) return
    plateLayoutOperationRevision.incrementAndGet()
    queueEvent {
        val arranged = renderer.arrangeObjects()
        requestRender()
        if (onComplete != null) ViewUtils.postOnMainThread { onComplete(arranged) }
    }
}
fun fillBedWithSelectedModel(callback:FillBedCallback) {
if (isFillBedInProgress) return
isFillBedInProgress = true
queueEvent {
    val activeModel = renderer.model
    val activeBed = renderer.bed
    val selectedObject = renderer.selectedObject
    val viewerEnabled = renderer.isViewerEnabled
    if (activeModel == null || activeBed == null || !activeBed.isValid() || viewerEnabled ||
        selectedObject !in 0 until activeModel.objectsCount
    ) {
        ViewUtils.postOnMainThread {
            isFillBedInProgress = false
            callback.onFillBedComplete(0)
        }
        return@queueEvent
    }
    val bedMin = activeBed.getVolumeMin()
    val bedMax = activeBed.getVolumeMax()
    if (bedMin == null || bedMax == null) {
        ViewUtils.postOnMainThread {
            isFillBedInProgress = false
            callback.onFillBedComplete(0)
        }
        return@queueEvent
    }
    val bedWidth = kotlin.math.abs(bedMax.x - bedMin.x)
    val bedDepth = kotlin.math.abs(bedMax.y - bedMin.y)
    val hasUnplaceableFootprint = (0 until activeModel.objectsCount).any { index ->
        val min = Vec3d()
        val max = Vec3d()
        // The approximate object box omits per-instance scaling in some Orca model
        // configurations; use exact world bounds so oversized objects are rejected
        // before the expensive native packing search.
        activeModel.getBoundingBoxExact(index, min, max)
        !FillBedPlanner.canFitFootprint(
            bedWidth, bedDepth, kotlin.math.abs(max.x - min.x), kotlin.math.abs(max.y - min.y),
        )
    }
    if (hasUnplaceableFootprint) {
        // Avoid repeatedly entering the native packer for an object that cannot fit
        // regardless of rotation; pathological oversized meshes otherwise stall GL.
        ViewUtils.postOnMainThread {
            isFillBedInProgress = false
            callback.onFillBedComplete(0)
        }
        return@queueEvent
    }
    val modelRevision = activeModel.geometryRevision
    val modelStateRevision = activeModel.stateRevision
    val bedRevision = activeBed.configurationRevision
    val layoutOperationRevision = plateLayoutOperationRevision.get()
    val estimatedBedCapacity = renderer.estimateBedCapacity(activeModel, activeBed, selectedObject)
    val selectedFilaments = (0 until activeModel.objectsCount).map { activeModel.getUsedFilaments(it) }
    val selectedPaintState = (0 until activeModel.objectsCount).map {
        activeModel.hasPaint(it) to activeModel.paintMaxFilament(it)
    }
    val workingModel = try {
        Model.merge(activeModel)
    } catch (error: Throwable) {
        android.util.Log.e("GLView", "Failed to create fill-bed model snapshot", error)
        ViewUtils.postOnMainThread {
            isFillBedInProgress = false
            callback.onFillBedComplete(0)
        }
        return@queueEvent
    }
    fillBedExecutor.execute {
        val addedCopies = try {
            renderer.fillBedModel(workingModel, selectedObject, viewerEnabled, estimatedBedCapacity) { candidate ->
                arrangeFillBedCandidateOnGlThread(
                    activeModel, activeBed, bedRevision, layoutOperationRevision, candidate,
                )
            }
        } catch (error: Throwable) {
            android.util.Log.e("GLView", "Failed to arrange model copies", error)
            0
        }
        queueEvent {
            val currentModel = renderer.model
            val stillCurrent = currentModel === activeModel && renderer.bed === activeBed &&
                activeBed.configurationRevision == bedRevision &&
                plateLayoutOperationRevision.get() == layoutOperationRevision &&
                renderer.isViewerEnabled == viewerEnabled &&
                renderer.selectedObject == selectedObject &&
                activeModel.geometryRevision == modelRevision &&
                activeModel.stateRevision == modelStateRevision &&
                activeModel.objectsCount == selectedFilaments.size &&
                (0 until activeModel.objectsCount).all { index ->
                    activeModel.getUsedFilaments(index).contentEquals(selectedFilaments[index]) &&
                        (activeModel.hasPaint(index) to activeModel.paintMaxFilament(index)) == selectedPaintState[index]
                }
            var committedCopies = 0
            try {
                if (addedCopies > 0 && stillCurrent) {
                    activeModel.replaceContentsFrom(workingModel)
                    renderer.resetGlModels()
                    SliceBeam.EVENT_BUS.fireEvent(ObjectsListChangedEvent())
                    committedCopies = addedCopies
                }
            } catch (error: Throwable) {
                android.util.Log.e("GLView", "Failed to apply fill-bed result", error)
            } finally {
                workingModel.release()
                requestRender()
                ViewUtils.postOnMainThread {
                    isFillBedInProgress = false
                    callback.onFillBedComplete(
                        FillBedCallbackResultPolicy.forModelState(
                            stillCurrent = stillCurrent,
                            addedCopies = committedCopies,
                            expectedLayoutOperationRevision = layoutOperationRevision,
                            currentLayoutOperationRevision = plateLayoutOperationRevision.get(),
                        ),
                    )
                }
            }
        }
    }
}
}

/** Bed3D's native arrange path shares renderer configuration and must only run on the GL thread. */
private fun arrangeFillBedCandidateOnGlThread(
    expectedModel: Model,
    expectedBed: Bed3D,
    expectedBedRevision: Long,
    expectedLayoutOperationRevision: Long,
    candidateModel: Model,
): Boolean {
    val gate = Any()
    val cancelled = AtomicBoolean(false)
    val result = AtomicReference<Boolean?>(null)
    val failure = AtomicReference<Throwable?>(null)
    val completed = CountDownLatch(1)
    queueEvent {
        synchronized(gate) {
            if (cancelled.get()) return@queueEvent
            if (renderer.model !== expectedModel || renderer.bed !== expectedBed ||
                expectedBed.configurationRevision != expectedBedRevision ||
                plateLayoutOperationRevision.get() != expectedLayoutOperationRevision ||
                !expectedBed.isValid()) {
                result.set(false)
                completed.countDown()
                return@queueEvent
            }
            try {
                result.set(expectedBed.arrange(candidateModel))
            } catch (error: Throwable) {
                failure.set(error)
            } finally {
                completed.countDown()
            }
        }
    }
    val finished = try {
        completed.await(60, TimeUnit.SECONDS)
    } catch (error: InterruptedException) {
        cancelled.set(true)
        Thread.currentThread().interrupt()
        return false
    }
    if (!finished) {
        synchronized(gate) { cancelled.set(true) }
        android.util.Log.e("GLView", "Timed out arranging fill-bed candidate on GL thread")
    }
    failure.get()?.let { android.util.Log.e("GLView", "Native fill-bed arrangement failed", it) }
    return finished && failure.get() == null && result.get() == true
}
fun duplicateSelectedModel(onComplete: Runnable) {
    duplicateSelectedModelWithResult { if (onComplete != null) onComplete.run() }
}
fun duplicateSelectedModel(copies: Int, onComplete: Runnable) {
    duplicateSelectedModels(copies) { if (onComplete != null) onComplete.run() }
}
fun duplicateSelectedModelWithResult(onComplete: (Int) -> Unit) {
    duplicateSelectedModels(1, onComplete)
}
fun duplicateSelectedModels(copies: Int, onComplete: (Int) -> Unit) {
    queueEvent {
        val count = copies.coerceIn(1, 50)
        val added = renderer.duplicateSelectedObjects(count)
        requestRender()
        ViewUtils.postOnMainThread { onComplete(added) }
    }
}
internal fun duplicateSelectedModelsIfCurrent(
    copies: Int,
    expectedSelection: ModelSelectionSnapshot,
    onComplete: (Int) -> Unit,
) {
    queueEvent {
        val added = renderer.duplicateSelectedObjects(copies.coerceIn(1, 50), expectedSelection)
        requestRender()
        ViewUtils.postOnMainThread { onComplete(added) }
    }
}
fun selectModel(objectIndex:Int, onComplete:Runnable) {
queueEvent({ renderer.setSelectedObject(objectIndex)
requestRender()
ViewUtils.postOnMainThread({ if (onComplete != null) onComplete.run() }) })
}
internal fun selectModelIfCurrent(
    objectIndex: Int,
    expectedSelection: ModelSelectionSnapshot,
    onComplete: (Int) -> Unit,
) {
    queueEvent {
        val result = when {
            !renderer.isSelectionSnapshotCurrent(expectedSelection) -> MODEL_EDIT_STALE_SELECTION
            objectIndex !in 0 until (renderer.model?.objectsCount ?: 0) -> 0
            else -> { renderer.setSelectedObject(objectIndex); 1 }
        }
        requestRender()
        ViewUtils.postOnMainThread { onComplete(result) }
    }
}
fun deleteSelectedModel(onComplete:Runnable) {
queueEvent({ val sel = renderer.selectedObject
if (sel != -1)
{
renderer.deleteObject(sel)
}
requestRender()
ViewUtils.postOnMainThread({ if (onComplete != null) onComplete.run() }) })
}
/** Delete the complete current selection on the GL thread and report completion on main. */
fun deleteSelectedModels(onComplete: Runnable? = null) {
    queueEvent {
        renderer.deleteSelectedObjects()
        requestRender()
        ViewUtils.postOnMainThread { onComplete?.run() }
    }
}
internal fun deleteSelectedModelsIfCurrent(
    expectedSelection: ModelSelectionSnapshot,
    onComplete: (Int) -> Unit,
) {
    queueEvent {
        val removed = renderer.deleteSelectedObjects(expectedSelection)
        requestRender()
        ViewUtils.postOnMainThread { onComplete(removed) }
    }
}
fun selectAllModels(onComplete:Runnable) {
queueEvent({ renderer.selectAllObjects()
requestRender()
ViewUtils.postOnMainThread({ if (onComplete != null) onComplete.run() }) })
}
fun selectModels(objectIndices: IntArray, onComplete: Runnable? = null) {
queueEvent({ renderer.setSelectedObjects(objectIndices)
requestRender()
ViewUtils.postOnMainThread({ onComplete?.run() }) })
}
internal fun selectModelsIfCurrent(
    objectIndices: IntArray,
    expectedSelection: ModelSelectionSnapshot,
    onComplete: (Int) -> Unit,
) {
    queueEvent {
        val model = renderer.model
        val result = when {
            !renderer.isSelectionSnapshotCurrent(expectedSelection) -> MODEL_EDIT_STALE_SELECTION
            objectIndices.any { it !in 0 until (model?.objectsCount ?: 0) } -> 0
            else -> { renderer.setSelectedObjects(objectIndices); 1 }
        }
        requestRender()
        ViewUtils.postOnMainThread { onComplete(result) }
    }
}
internal fun setObjectUniformFilamentIfCurrent(
    objectIndex: Int,
    filament: Int,
    expectedSelection: ModelSelectionSnapshot,
    onComplete: (Int) -> Unit,
) {
    queueEvent {
        val result = renderer.setObjectUniformFilamentIfCurrent(objectIndex, filament, expectedSelection)
        if (result == 1) requestRender()
        ViewUtils.postOnMainThread { onComplete(result) }
    }
}
internal fun setObjectPartFilamentIfCurrent(
    objectIndex: Int,
    partIndex: Int,
    filament: Int,
    expectedSelection: ModelSelectionSnapshot,
    onComplete: (Int) -> Unit,
) {
    queueEvent {
        val result = renderer.setObjectPartFilamentIfCurrent(
            objectIndex, partIndex, filament, expectedSelection,
        )
        if (result == 1) requestRender()
        ViewUtils.postOnMainThread { onComplete(result) }
    }
}
internal fun combineSelectedObjectsAsParts(
    expectedSelection: ModelSelectionSnapshot? = null,
    onComplete:(Int)->Unit,
) {
queueEvent({
    val result = renderer.combineSelectedObjectsAsParts(expectedSelection)
    requestRender()
    ViewUtils.postOnMainThread { onComplete(result) }
})
}
internal fun splitObjectPartIfCurrent(
    objectIndex: Int,
    partIndex: Int,
    expectedSelection: ModelSelectionSnapshot,
    onComplete: (Int) -> Unit,
) {
    queueEvent {
        val result = renderer.splitObjectPartIfCurrent(objectIndex, partIndex, expectedSelection)
        if (result > 0) requestRender()
        ViewUtils.postOnMainThread { onComplete(result) }
    }
}
internal fun transformObjectPartIfCurrent(
    objectIndex: Int,
    partIndex: Int,
    dx: Double,
    dy: Double,
    dz: Double,
    scaleX: Double,
    scaleY: Double,
    scaleZ: Double,
    rotationXDegrees: Double = 0.0,
    rotationYDegrees: Double = 0.0,
    rotationZDegrees: Double = 0.0,
    expectedSelection: ModelSelectionSnapshot,
    onComplete: (Int) -> Unit,
) {
    queueEvent {
        val result = renderer.transformObjectPartIfCurrent(
            objectIndex, partIndex, dx, dy, dz,
            scaleX, scaleY, scaleZ,
            rotationXDegrees, rotationYDegrees, rotationZDegrees, expectedSelection,
        )
        if (result == 1) requestRender()
        ViewUtils.postOnMainThread { onComplete(result) }
    }
}
internal fun placeSelectedObjectsOnObject(
    targetIndex: Int,
    expectedSelection: ModelSelectionSnapshot? = null,
    onComplete: (Int) -> Unit,
) {
    queueEvent {
        val result = renderer.placeSelectedObjectsOnObject(targetIndex, expectedSelection)
        requestRender()
        ViewUtils.postOnMainThread { onComplete(result) }
    }
}
internal fun beginSurfacePlacement(
    targetIndex: Int,
    expectedSelection: ModelSelectionSnapshot? = null,
    onStarted: (Int) -> Unit,
) {
    queueEvent {
        val started = renderer.beginSurfacePlacement(targetIndex, expectedSelection)
        requestRender()
        ViewUtils.postOnMainThread { onStarted(started) }
    }
}
internal fun beginSurfaceBoolean(
    targetIndex: Int,
    operation: BooleanOperation,
    expectedSelection: ModelSelectionSnapshot? = null,
    onStarted: (Int) -> Unit,
) {
    queueEvent {
        val started = renderer.beginSurfaceBoolean(targetIndex, operation, expectedSelection)
        requestRender()
        ViewUtils.postOnMainThread { onStarted(started) }
    }
}
fun cancelSurfacePlacement() {
    queueEvent {
        renderer.cancelSurfacePlacement()
        requestRender()
    }
}
fun placeSelectedObjectsOnSurface(
    targetIndex: Int,
    position: Vec3d,
    normal: Vec3d,
    booleanOperation: BooleanOperation? = null,
    onComplete: (Int) -> Unit,
) {
    queueEvent {
        val result = renderer.placeSelectedObjectsOnSurface(
            targetIndex, position, normal, booleanOperation = booleanOperation,
        )
        requestRender()
        ViewUtils.postOnMainThread { onComplete(result) }
    }
}
fun centerSelectedOnBed(onComplete:Runnable) {
queueEvent({ renderer.centerSelectionOnBed()
requestRender()
ViewUtils.postOnMainThread({ if (onComplete != null) onComplete.run() }) })
}
internal fun centerSelectedOnBed(
    expectedSelection: ModelSelectionSnapshot,
    onComplete: (Int) -> Unit,
) {
    queueEvent {
        val centered = renderer.centerSelectionOnBed(expectedSelection)
        requestRender()
        ViewUtils.postOnMainThread { onComplete(centered) }
    }
}
internal fun scaleSelectedObjectToBuildVolume(
    expectedSelection: ModelSelectionSnapshot,
    onComplete: (Int) -> Unit,
) {
    queueEvent {
        val result = renderer.scaleSelectedObjectToBuildVolume(expectedSelection)
        requestRender()
        ViewUtils.postOnMainThread { onComplete(result) }
    }
}
internal fun placeSelectedObjectsOnBed(
    expectedSelection: ModelSelectionSnapshot,
    onComplete: (Int) -> Unit,
) {
    queueEvent {
        val placed = renderer.placeSelectedObjectsOnBed(expectedSelection)
        requestRender()
        ViewUtils.postOnMainThread { onComplete(placed) }
    }
}
internal fun alignSelectedObjects(
    axis: ModelAlignmentAxis,
    expectedSelection: ModelSelectionSnapshot? = null,
    coordinateSystem: ModelAlignmentCoordinateSystem = ModelAlignmentCoordinateSystem.WORLD,
    onComplete: (Int) -> Unit,
) {
    queueEvent {
        val aligned = when {
            expectedSelection != null && !renderer.isSelectionSnapshotCurrent(expectedSelection) -> -2
            renderer.alignSelectedObjects(axis, expectedSelection, coordinateSystem) -> 1
            else -> 0
        }
        requestRender()
        ViewUtils.postOnMainThread { onComplete(aligned) }
    }
}
internal fun distributeSelectedObjects(
    axis: ObjectDistributionAxis,
    expectedSelection: ModelSelectionSnapshot? = null,
    onComplete: (Int) -> Unit,
) {
    queueEvent {
        val distributed = when {
            expectedSelection != null && !renderer.isSelectionSnapshotCurrent(expectedSelection) -> -2
            renderer.distributeSelectedObjects(axis, expectedSelection) -> 1
            else -> 0
        }
        requestRender()
        ViewUtils.postOnMainThread { onComplete(distributed) }
    }
}
fun autoOrientSelectedObjects(onComplete: (Int) -> Unit) {
    queueEvent {
        val oriented = renderer.autoOrientSelectedObjects()
        requestRender()
        ViewUtils.postOnMainThread { onComplete(oriented) }
    }
}
internal fun autoOrientSelectedObjects(
    expectedSelection: ModelSelectionSnapshot,
    onComplete: (Int) -> Unit,
) {
    queueEvent {
        val oriented = renderer.autoOrientSelectedObjects(expectedSelection)
        requestRender()
        ViewUtils.postOnMainThread { onComplete(oriented) }
    }
}
fun autoOrientAllObjects(onComplete: (Int) -> Unit) {
    queueEvent {
        if (renderer.invalidateFlattenMode()) requestRender()
        val oriented = renderer.autoOrientAllObjects()
        requestRender()
        ViewUtils.postOnMainThread { onComplete(oriented) }
    }
}
fun deleteAllModels(onComplete:Runnable) {
queueEvent({ renderer.deleteAllObjects()
requestRender()
ViewUtils.postOnMainThread({ if (onComplete != null) onComplete.run() }) })
}
internal fun deleteAllModelsIfCurrent(
    expectedModel: ModelSelectionSnapshot,
    onComplete: (Int) -> Unit,
) {
    queueEvent {
        val removed = renderer.deleteAllObjects(expectedModel)
        requestRender()
        ViewUtils.postOnMainThread { onComplete(removed) }
    }
}
private val previewBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
fun drawOverlay(canvas:Canvas, toBitmap:Boolean) {
val rad = ViewUtils.dp(16)
val offsetX = getTranslationX()
val offsetY = -getTranslationY()
if (toBitmap)
{
paint.setColor(ThemesRepo.getColor(android.R.attr.windowBackground))
canvas.drawRect(offsetX, offsetY, getWidth() - offsetX, getHeight() - offsetY, paint)
canvas.drawRoundRect(offsetX, offsetY, getWidth() - offsetX, getHeight() - offsetY, (rad).toFloat(), (rad).toFloat(), xferPaint)
}
else
{
path.rewind()
path.addRoundRect(offsetX, offsetY, getWidth() - offsetX, getHeight() - offsetY, (rad).toFloat(), (rad).toFloat(), Path.Direction.CW)
canvas.save()
canvas.clipPath(path, Region.Op.DIFFERENCE)
canvas.drawColor(ThemesRepo.getColor(android.R.attr.windowBackground))
canvas.restore()
val previewResult = renderer.gcodeResult
val preview = previewResult?.previewSelection
if (previewResult != null && preview != null && previewResult.previewLayerCount > 0) {
    val badge = context.getString(R.string.GCodePreviewBadge, preview.from, preview.to, previewResult.previewLayerCount)
    val margin = ViewUtils.dp(8).toFloat()
    val padding = ViewUtils.dp(6).toFloat()
    val available = (width - margin * 2 - padding * 2).coerceAtLeast(1f)
    previewBadgePaint.textSize = ViewUtils.dp(13).toFloat()
    val measured = previewBadgePaint.measureText(badge)
    if (measured > available) previewBadgePaint.textSize *= available / measured
    val metrics = previewBadgePaint.fontMetrics
    val badgeWidth = previewBadgePaint.measureText(badge) + padding * 2
    previewBadgePaint.color = ThemesRepo.getColor(android.R.attr.windowBackground)
    canvas.drawRoundRect(margin, margin, margin + badgeWidth,
        margin + metrics.descent - metrics.ascent + padding * 2, padding, padding, previewBadgePaint)
    previewBadgePaint.color = ThemesRepo.getColor(android.R.attr.textColorPrimary)
    canvas.drawText(badge, margin + padding, margin + padding - metrics.ascent, previewBadgePaint)
}
if (renderer.bedConfigurationFailed)
{
paint.setColor(ThemesRepo.getColor(android.R.attr.windowBackground))
val size = ViewUtils.dp(200)
canvas.drawRect((0).toFloat(), (getHeight() - size) / 2f, (getWidth()).toFloat(), (getHeight() + size) / 2f, paint)
val angle = Math.toRadians((60).toDouble())
val stableWidth = ViewUtils.dp(16)
val lineHeight = ViewUtils.dp(16)
val lineWidth = ViewUtils.dp(16 + ((32 * Math.sin(angle))).toFloat())
val linePath = Path()
linePath.moveTo((0).toFloat(), (0).toFloat())
linePath.lineTo((stableWidth).toFloat(), (0).toFloat())
linePath.lineTo((lineWidth).toFloat(), (lineHeight).toFloat())
linePath.lineTo((lineWidth - stableWidth).toFloat(), (lineHeight).toFloat())
linePath.lineTo((0).toFloat(), (0).toFloat())
linePath.close()
paint.setColor(ThemesRepo.getColor(android.R.attr.colorAccent))
// Keep the invalid-bed warning static. The old moving stripes called
 // invalidate() forever, defeating RENDERMODE_WHEN_DIRTY on idle devices.
 var x = -lineWidth
while (x < getWidth())
{
canvas.save()
canvas.translate((x).toFloat(), (getHeight() - size) / 2f)
canvas.drawPath(linePath, paint)
canvas.translate((stableWidth).toFloat(), (0).toFloat())
val alpha = paint.getAlpha()
paint.setAlpha((alpha * 0.5f).toInt())
canvas.drawPath(linePath, paint)
canvas.restore()
paint.setAlpha(alpha)
x += stableWidth * 2
}
x = getWidth() + lineWidth
while (x >= -lineWidth)
{
canvas.save()
canvas.translate((x).toFloat(), (getHeight() + size) / 2f - lineHeight)
canvas.drawPath(linePath, paint)
canvas.translate((-stableWidth).toFloat(), (0).toFloat())
val alpha = paint.getAlpha()
paint.setAlpha((alpha * 0.5f).toInt())
canvas.drawPath(linePath, paint)
canvas.restore()
paint.setAlpha(alpha)
x -= stableWidth * 2
}
if (invalidBedDescriptionLayout == null)
{
invalidBedText.setTextSize((ViewUtils.dp(16)).toFloat())
invalidBedText.setColor(ThemesRepo.getColor(android.R.attr.textColorSecondary))
invalidBedText.setTypeface(Typeface.DEFAULT)
invalidBedDescriptionLayout = StaticLayout(getContext().getString(R.string.BedConfigurationErrorDesc), invalidBedText, getWidth() - ViewUtils.dp(32), Layout.Alignment.ALIGN_CENTER, (1).toFloat(), (0).toFloat(), false)
}
val realTextSize = ViewUtils.dp(22)
val padding = ViewUtils.dp(12)
val totalHeight = realTextSize + invalidBedDescriptionLayout!!.getHeight()
invalidBedText.setTextSize((ViewUtils.dp(18)).toFloat())
invalidBedText.setColor(ThemesRepo.getColor(android.R.attr.textColorPrimary))
invalidBedText.setTypeface(ViewUtils.getTypeface(ViewUtils.ROBOTO_MEDIUM))
val errString = getContext().getString(R.string.BedConfigurationError)
canvas.drawText(errString, 0, errString.length, (getWidth() - invalidBedText.measureText(errString)) / 2f, getHeight() / 2f - totalHeight / 2f + realTextSize - padding / 2f, invalidBedText)
invalidBedText.setTextSize((ViewUtils.dp(16)).toFloat())
invalidBedText.setColor(ThemesRepo.getColor(android.R.attr.textColorSecondary))
invalidBedText.setTypeface(Typeface.DEFAULT)
canvas.save()
canvas.translate((getWidth() - invalidBedDescriptionLayout!!.getWidth()) / 2f, getHeight() / 2f + totalHeight / 2f - invalidBedDescriptionLayout!!.getHeight() + padding / 2f)
invalidBedDescriptionLayout!!.draw(canvas)
canvas.restore()
}
}
}
override fun draw(canvas:Canvas) {
super.draw(canvas)
drawOverlay(canvas, false)
}
fun snapshotBitmap():Bitmap {
val w = getWidth()
val h = getHeight()
val bitmapBuffer = IntArray(w * h)
val bitmapSource = IntArray(w * h)
val intBuffer = IntBuffer.wrap(bitmapBuffer)
intBuffer.position(0)
try
{
GLES30.glReadPixels(0, 0, w, h, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, intBuffer)
var offset1:Int
var offset2:Int
for (i in 0 until h)
{
offset1 = i * w
offset2 = (h - i - 1) * w
for (j in 0 until w)
{
val texturePixel = bitmapBuffer[offset1 + j]
val blue = (texturePixel shr 16) and 0xff
val red = (texturePixel shl 16) and 0x00ff0000
val pixel = (texturePixel and -0xff0100) or red or blue
bitmapSource[offset2 + j] = pixel
}
}
}
catch (e:GLException) {
throw RuntimeException(e)
}
return Bitmap.createBitmap(bitmapSource, w, h, Bitmap.Config.ARGB_8888)
}
protected override fun onConfigurationChanged(newConfig:Configuration) {
super.onConfigurationChanged(newConfig)
touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop()
}
private fun calcStartFocus(e:MotionEvent) {
lastX = (e.getX(0) + e.getX(1)) / 2f
lastY = (e.getY(0) + e.getY(1)) / 2f
val x = e.getX(0) - e.getX(1)
val y = e.getY(0) - e.getY(1)
lastLength = (Math.sqrt((x * x + y * y).toDouble())).toFloat()
}
override fun onHoverEvent(event:MotionEvent):Boolean {
if (if (event.getActionMasked() == MotionEvent.ACTION_HOVER_EXIT) renderer.stopHover() else renderer.hover(surfaceTouchX(event.getX()), surfaceTouchY(event.getY())))
{
queueEvent(Runnable { requestRender() })
}
return super.onHoverEvent(event)
}
@SuppressLint("ClickableViewAccessibility")
override fun onTouchEvent(e:MotionEvent):Boolean {
val deltaMs = System.currentTimeMillis() - lastActionTime
lastActionTime = System.currentTimeMillis()
val action = e.getActionMasked()
if (variableLayerHeightStrokeActive && (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL ||
        action == MotionEvent.ACTION_POINTER_DOWN)) {
    variableLayerHeightStrokeActive = false
    queueEvent { finishVariableLayerHeightStrokeOnGlThread() }
    if (action != MotionEvent.ACTION_POINTER_DOWN) return true
}
if (paintStrokeActive && (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL ||
        action == MotionEvent.ACTION_POINTER_DOWN)) {
    paintStrokeActive = false
    queueEvent { endPaintStrokeOnGlThread() }
}
if (renderer.isPaintMode && isPaintBrushActive && e.getPointerCount() == 1 && action == MotionEvent.ACTION_DOWN) {
    paintStrokeActive = true
    queueEvent { renderer.beginPaintStroke() }
}
if (action == MotionEvent.ACTION_CANCEL) {
tapGestureGate.invalidate()
onePointerGesture = false
pointerDownForLongClick = false
longClickActivationPending = false
touchGestureGeneration++
removeCallbacks(longClick)
longClickTargetObject = -1
}
if (e.getPointerCount() > 2)
{
tapGestureGate.invalidate()
pointerDownForLongClick = false
longClickActivationPending = false
touchGestureGeneration++
removeCallbacks(longClick)
longClickGesture = false
longClickMoved = false
longClickTargetObject = -1
fromTwoPointers = false
isScaling = false
twoPointerGesture = false
onePointerGesture = false
return true
}
// Painting variable layer height consumes one-finger gestures, while two fingers retain the
// normal orbit/zoom path. Exact surface hits are resolved on the GL thread with the model mesh.
if (isVariableLayerHeightBrushActive && e.getPointerCount() == 1 &&
    (action == MotionEvent.ACTION_DOWN || (action == MotionEvent.ACTION_MOVE && variableLayerHeightStrokeActive))) {
    tapGestureGate.invalidate()
    if (action == MotionEvent.ACTION_DOWN) {
        variableLayerHeightStrokeActive = true
        queueEvent {
            variableLayerHeightStrokeChanged = false
            lastVariableLayerHeightBrushAtMs = 0L
        }
    }
    paintVariableLayerHeightAtScreen(e.getX(), e.getY())
    return true
}
// Paint mode: a single finger paints; two fingers still orbit/zoom via the normal path.
 if ((renderer.isPaintMode && isPaintBrushActive && e.getPointerCount() == 1 &&
        (action == MotionEvent.ACTION_DOWN || (action == MotionEvent.ACTION_MOVE && paintStrokeActive))))
{
tapGestureGate.invalidate()
paintAtScreen(e.getX(), e.getY())
return true
}
if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN)
{
    tapGestureGate.begin(e.getPointerCount(), e.getX(0), e.getY(0), e.getDownTime())
if (action == MotionEvent.ACTION_DOWN) {
    onePointerGesture = false
    pointerDownForLongClick = true
    longClickActivationPending = false
    touchGestureGeneration++
}
if (e.getPointerCount() == 2)
{
pointerDownForLongClick = false
longClickActivationPending = false
touchGestureGeneration++
if (primeTowerGesture)
{
renderer.finishPrimeTowerDrag(false)
primeTowerGesture = false
primeTowerMoved = false
requestRender()
}
removeCallbacks(longClick)
longClickGesture = false
calcStartFocus(e)
fromTwoPointers = true
}
else
{
lastX = e.getX()
lastY = e.getY()
longClickTargetObject = -1
longClickMoved = false
if (renderer.tryStartPrimeTowerDrag(surfaceTouchX(lastX), surfaceTouchY(lastY)))
{
tapGestureGate.invalidate()
removeCallbacks(longClick)
primeTowerGesture = true
primeTowerMoved = false
primeTowerDownX = lastX
primeTowerDownY = lastY
requestRender()
return true
}
    // Defer object picking until the user actually holds still. Camera orbit gestures
    // leave this callback cancelled when they cross touch slop, so the GL queue stays free.
    postDelayed(longClick, longPressTimeoutMs)
}
return true
}
if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL)
{
removeCallbacks(longClick)
pointerDownForLongClick = false
longClickActivationPending = false
touchGestureGeneration++
if (primeTowerGesture)
{
tapGestureGate.invalidate()
val openExactPosition = action == MotionEvent.ACTION_UP && !primeTowerMoved
renderer.finishPrimeTowerDrag(action != MotionEvent.ACTION_CANCEL && primeTowerMoved)
primeTowerGesture = false
primeTowerMoved = false
onePointerGesture = false
requestRender()
if (openExactPosition) onPrimeTowerTap?.invoke()
return true
}
if (longClickGesture)
{
tapGestureGate.invalidate()
if (longClickMoved)
{
queueEvent({
// Commit the drag to the whole selection (group move when multiple are selected).
 renderer.translateSelectedObjects(longClickTranslation.x, longClickTranslation.y, (0).toDouble())
requestRender()
SliceBeam.EVENT_BUS.fireEvent(LongClickTranslationEvent(longClickTranslation.x, longClickTranslation.y, false)) })
}
// A long press is reserved for picking up and moving a model. Opening a dialog here
// makes ordinary touches on the build plate feel like repeated taps. Model actions
// remain available from the explicit, visible "Mehr" control.
longClickGesture = false
longClickMoved = false
longClickTargetObject = -1
onePointerGesture = false
return true
}
longClickGesture = false
if (fromTwoPointers)
{
tapGestureGate.invalidate()
if (e.getPointerCount() == 1 || action == MotionEvent.ACTION_CANCEL)
{
fromTwoPointers = false
isScaling = false
twoPointerGesture = false
lastActionTime = 0
}
return true
}
if (e.getPointerCount() == 1)
{
val dispatchTap = tapGestureGate.finish(
    pointerCount = e.getPointerCount(),
    moved = onePointerGesture || tapGestureGate.movedBeyondTouchSlop(
        e.getX(0), e.getY(0), touchSlop,
    ),
    cancelled = action == MotionEvent.ACTION_CANCEL,
    eventTimeMs = e.getEventTime(),
    x = e.getX(0),
    y = e.getY(0),
    duplicateWindowMs = ViewConfiguration.getDoubleTapTimeout().toLong(),
    duplicateRadiusPx = touchSlop * 2f,
     maxTapDurationMs = longPressTimeoutMs,
)
if (dispatchTap)
{
val x = surfaceTouchX(e.getX())
val y = surfaceTouchY(e.getY())
queueEvent {
if (renderer.onClick(x, y)) requestRender()
}
}
lastX = e.getX(0)
lastY = e.getY(0)
onePointerGesture = false
}
// TODO: Rotate with inertia?
 return true
}
if (action == MotionEvent.ACTION_MOVE)
{
if (primeTowerGesture && e.getPointerCount() == 1)
{
if (!primeTowerMoved)
{
val deltaX = e.getX() - primeTowerDownX
val deltaY = e.getY() - primeTowerDownY
if (deltaX * deltaX + deltaY * deltaY <= touchSlop.toFloat() * touchSlop.toFloat())
{
lastX = e.getX()
lastY = e.getY()
return true
}
primeTowerMoved = true
performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
}
if (renderer.dragPrimeTower(surfaceTouchX(e.getX()), surfaceTouchY(e.getY())))
{
requestRender()
}
lastX = e.getX()
lastY = e.getY()
return true
}
if (e.getPointerCount() == 2)
{
val x = (e.getX(0) + e.getX(1)) / 2f
val y = (e.getY(0) + e.getY(1)) / 2f
val lenX = e.getX(0) - e.getX(1)
val lenY = e.getY(0) - e.getY(1)
val len = (Math.sqrt((lenX * lenX + lenY * lenY).toDouble())).toFloat()
val distanceX = lastX - x
val distanceY = lastY - y
if (deltaMs > 128)
{
isScaling = false
twoPointerGesture = false
}
var startingGesture = false
if (!isScaling && !twoPointerGesture)
{
if (Math.abs(distanceX) < touchSlop && Math.abs(distanceY) < touchSlop && Math.abs(len - lastLength) > touchSlop * 1.5f)
{
isScaling = true
startingGesture = true
}
else if (Math.sqrt((distanceX * distanceX + distanceY * distanceY).toDouble()) >= touchSlop)
{
twoPointerGesture = true
startingGesture = true
}
}
if (isScaling)
{
val delta = len - lastLength
lastLength = len
if (!startingGesture)
{
renderer.camera.zoom(delta / touchSlop * Prefs.getCameraSensitivity())
renderer.updateProjection()
requestRender()
}
lastX = x
lastY = y
}
else if (twoPointerGesture)
{
if (!startingGesture)
{
val mode = Prefs.getCameraControlMode()
if (mode == Prefs.CAMERA_CONTROL_MODE_ROTATE_MOVE || mode == Prefs.CAMERA_CONTROL_MODE_MOVE_ONLY)
{
renderer.camera.move(distanceX / touchSlop * Prefs.getCameraSensitivity(), distanceY / touchSlop * Prefs.getCameraSensitivity())
}
else
{
renderer.camera.rotateAround((distanceX / touchSlop * Prefs.getCameraSensitivity()).toDouble(), (distanceY / touchSlop * Prefs.getCameraSensitivity()).toDouble())
}
requestRender()
}
lastX = x
lastY = y
}
}
else if (!fromTwoPointers)
{
val distanceX = lastX - e.getX()
val distanceY = lastY - e.getY()
var startingGesture = false
if (!onePointerGesture)
{
if (tapGestureGate.movedBeyondTouchSlop(e.getX(), e.getY(), touchSlop))
{
onePointerGesture = true
tapGestureGate.invalidate()
startingGesture = true
removeCallbacks(longClick)
if (!longClickGesture && !longClickActivationPending) touchGestureGeneration++
}
}
if (onePointerGesture)
{
if (!startingGesture)
{
if (longClickGesture)
{
longClickMoved = true
val x = surfaceTouchX(e.getX())
val y = surfaceTouchY(e.getY())
queueEvent {
    val activeModel = renderer.model ?: return@queueEvent
    val selected = renderer.selectedObject
    val bed = renderer.bed ?: return@queueEvent
    if (selected !in 0 until activeModel.objectsCount) return@queueEvent
    activeModel.getTranslation(selected, tempVec)
    if (longClickUsesSurfacePlane) {
        val point = renderer.intersectHorizontalPlane(x, y, longClickPlaneZ) ?: return@queueEvent
        longClickTranslation.x = point.x - longClickStartX
        longClickTranslation.y = point.y - longClickStartY
    } else {
        bed.raycaster.raycast(renderer, longClickHitResults, x, y)
        val result = longClickHitResults.firstOrNull() ?: return@queueEvent
        longClickTranslation.x = result.position.x - tempVec.x - longClickOffset.x
        longClickTranslation.y = result.position.y - tempVec.y - longClickOffset.y
    }
    renderer.setSelectionTranslation(longClickTranslation.x, longClickTranslation.y, 0.0)
    SliceBeam.EVENT_BUS.fireEvent(LongClickTranslationEvent(longClickTranslation.x, longClickTranslation.y, true))
    requestRender()
}
}
else
{
val mode = Prefs.getCameraControlMode()
if (mode == Prefs.CAMERA_CONTROL_MODE_ROTATE_MOVE)
{
renderer.camera.rotateAround((distanceX / touchSlop * Prefs.getCameraSensitivity()).toDouble(), (distanceY / touchSlop * Prefs.getCameraSensitivity()).toDouble())
}
else
{
renderer.camera.move(distanceX / touchSlop * Prefs.getCameraSensitivity(), distanceY / touchSlop * Prefs.getCameraSensitivity())
}
requestRender()
}
}
lastX = e.getX()
lastY = e.getY()
}
}
}
return true
}
fun applyRenderScale() {
val w = getWidth()
val h = getHeight()
if (w <= 0 || h <= 0) return
lastScale = Prefs.getRenderScale()
val realScale = Math.round(w * lastScale) / w.toFloat()
getHolder().setFixedSize((w * realScale).toInt(), (h * realScale).toInt())
requestRender()
}
protected override fun onSizeChanged(w:Int, h:Int, oldw:Int, oldh:Int) {
super.onSizeChanged(w, h, oldw, oldh)
if (w != 0 && h != 0)
{
applyRenderScale()
}
}
protected override fun onAttachedToWindow() {
super.onAttachedToWindow()
if (getWidth() > 0 && getHeight() > 0 && lastScale != Prefs.getRenderScale())
{
applyRenderScale()
}
}
override fun onDetachedFromWindow() {
    synchronized(renderRequestLock) {
        delayedRenderPending = false
        renderRequestHandler.removeCallbacks(delayedRenderRequest)
    }
    super.onDetachedFromWindow()
}
override fun surfaceCreated(holder:SurfaceHolder) {
super.surfaceCreated(holder)
requestRender()
}
override fun surfaceChanged(holder:SurfaceHolder, format:Int, w:Int, h:Int) {
glSurfaceWidth = w
glSurfaceHeight = h
super.surfaceChanged(holder, format, w, h)
requestRender()
}
override fun surfaceDestroyed(holder:SurfaceHolder) {
super.surfaceDestroyed(holder)
renderer.onDestroy()
}
override fun onApplyTheme() {
requestRender()
}
companion object {
private val FILL_BED_STEP_DELAY_MS = 16L
private val fillBedExecutor = Executors.newSingleThreadExecutor { task ->
    Thread(task, "OrkanSlicer-FillBed").apply { isDaemon = true }
}
}
}
