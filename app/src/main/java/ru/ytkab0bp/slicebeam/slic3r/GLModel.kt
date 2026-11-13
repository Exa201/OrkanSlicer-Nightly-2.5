package ru.ytkab0bp.slicebeam.slic3r

import android.graphics.Color
import ru.ytkab0bp.slicebeam.render.Camera
import ru.ytkab0bp.slicebeam.render.GLRenderer
import ru.ytkab0bp.slicebeam.utils.DebugUtils
import ru.ytkab0bp.slicebeam.utils.Prefs
import ru.ytkab0bp.slicebeam.utils.Vec3d
import java.util.ArrayList

open class GLModel internal constructor(
    @JvmField var pointer: Long,
) {
    @Volatile private var raycasterCache: MeshRaycaster? = null
    @Volatile private var approximatePickBounds: Pair<Vec3d, Vec3d>? = null
    @Volatile private var fallbackPickBounds: Pair<Vec3d, Vec3d>? = null

    @JvmField var hoverProgress: Float = 0f
    @JvmField var isHovering: Boolean = false

    constructor() : this(Native.glmodel_create())

    constructor(model: Model) : this() {
        initFrom(model)
    }

    @Synchronized
    open fun initFrom(model: Model) {
        clearPicking()
        Native.glmodel_init_from_model(pointer, model.pointer)
    }

    @Synchronized
    open fun initFrom(model: Model, i: Int) {
        updatePickBounds(model, i)
        Native.glmodel_init_from_model_object(pointer, model.pointer, i)
    }

    @Synchronized
    open fun initFromVolume(model: Model, objectIndex: Int, volumeIndex: Int) {
        clearPicking()
        Native.glmodel_init_from_model_object_volume(pointer, model.pointer, objectIndex, volumeIndex)
    }

    /** Initialize the full-resolution mesh and a precise raycast tree for an active paint session. */
    @Synchronized
    open fun initForPaint(model: Model, i: Int): Boolean {
        // Surface tools can ask before every pointer sample. Reuse an existing exact
        // mesh/tree instead of rebuilding it for every move event.
        if (raycasterCache != null && approximatePickBounds == null) return true
        if (!LargeModelPick.canUsePrecisePaintRaycast(model.getTriangleCount(i))) return false
        updatePickBounds(model, i, LargeModelPick.PAINT_RAYCAST_TRIANGLE_LIMIT)
        Native.glmodel_init_from_model_object(pointer, model.pointer, i)
        if (approximatePickBounds != null) return false
        val ready = try {
            Native.glmodel_init_raycast_data(pointer)
        } catch (error: RuntimeException) {
            android.util.Log.w("GLModel", "Could not initialize precise paint raycast", error)
            false
        }
        if (ready) raycasterCache = MeshRaycaster()
        else approximatePickBounds = fallbackPickBounds
        return ready
    }

    @Synchronized
    open fun initPreviewFrom(model: Model, i: Int, maxTriangles: Int) {
        updatePickBounds(model, i)
        Native.glmodel_init_from_model_object_preview(pointer, model.pointer, i, maxTriangles)
    }

    private fun updatePickBounds(
        model: Model,
        i: Int,
        exactTriangleLimit: Int = LargeModelPick.EXACT_RAYCAST_TRIANGLE_LIMIT,
    ) {
        clearPicking()
        val min = Vec3d()
        val max = Vec3d()
        model.getBoundingBoxApprox(i, min, max)
        fallbackPickBounds = min to max
        approximatePickBounds = fallbackPickBounds.takeIf {
            model.getTriangleCount(i) > exactTriangleLimit
        }
    }

    /** Build this model from facets painted with the given filament in a paint session (mesh-local coords). */
    @Synchronized
    open fun initFromPaint(paintSessionPtr: Long, filamentIdx: Int, baseFilamentPreview: Int = 0): Int {
        clearPicking()
        return Native.glmodel_init_from_paint(pointer, paintSessionPtr, filamentIdx, baseFilamentPreview)
    }

    open fun setColor(color: Int) {
        Native.glmodel_set_color(
            pointer,
            Color.red(color) / 0xFF.toFloat(),
            Color.green(color) / 0xFF.toFloat(),
            Color.blue(color) / 0xFF.toFloat(),
            Color.alpha(color) / 0xFF.toFloat(),
        )
    }

    open fun stilizedArrow(tipRadius: Float, tipLength: Float, stemRadius: Float, stemLength: Float) {
        clearPicking()
        Native.glmodel_stilized_arrow(pointer, tipRadius, tipLength, stemRadius, stemLength)
    }

    open fun initBackgroundTriangles() {
        clearPicking()
        Native.glmodel_init_background_triangles(pointer)
    }

    /** Build a simple rectangular prism with origin at the front-left-bottom corner. */
    @Synchronized
    open fun initBox(width: Float, depth: Float, height: Float) {
        clearPicking()
        Native.glmodel_init_box(pointer, width, depth, height)
    }

    @Synchronized
    open fun initBoundingBox(model: Model, i: Int) {
        clearPicking()
        Native.glmodel_init_bounding_box(pointer, model.pointer, i)
    }

    open fun isInitialized(): Boolean = Native.glmodel_is_initialized(pointer)

    /** Current native geometry bounds for checking that the selected outline tracks its mesh. */
    open fun getBoundingBox(): DoubleArray = Native.glmodel_get_bounding_box(pointer)

    open fun isEmpty(): Boolean = Native.glmodel_is_empty(pointer)

    open fun render() {
        Native.glmodel_render(pointer)
    }

    @Synchronized
    open fun reset() {
        Native.glmodel_reset(pointer)
        clearPicking()
    }

    @Synchronized
    open fun release() {
        if (pointer != 0L) {
            Native.glmodel_release(pointer)
            pointer = 0L
        }
        clearPicking()
    }

    private fun clearPicking() {
        raycasterCache = null
        approximatePickBounds = null
        fallbackPickBounds = null
    }

    open val raycaster: MeshRaycaster
        get() = synchronized(this) {
        var current = raycasterCache
        if (current == null) {
            // Renderer teardown may release a mesh while a queued touch callback is still
            // waiting to resolve its raycaster. A released handle must never reach JNI.
            if (pointer == 0L) {
                current = MeshRaycaster()
                raycasterCache = current
                return@synchronized current
            }
            // Build the lightweight triangle AABB tree only for meshes within the explicit
            // memory limit. KromaCut meshes can exceed a million facets and must stay on bounds.
            if (approximatePickBounds == null && !Native.glmodel_init_raycast_data(pointer))
                approximatePickBounds = fallbackPickBounds
            current = MeshRaycaster()
            raycasterCache = current
        }
        current
    }

    inner class MeshRaycaster {
        fun raycast(renderer: GLRenderer?, list: ArrayList<HitResult>?, x: Float, y: Float) {
            // `release`, mesh replacement and raycasts all share the GLModel monitor, so a
            // touch already in flight can never call JNI with a freed native handle.
            synchronized(this@GLModel) {
            list?.clear()
            if (pointer == 0L) return
            DebugUtils.assertTrue(renderer != null)
            DebugUtils.assertTrue(list != null)
            val safeRenderer = renderer!!
            val safeList = list!!
            safeList.clear()

            val camera: Camera = safeRenderer.camera
            val point = Slic3rUtils.unproject(
                camera.getViewModelMatrix(),
                safeRenderer.projectionMatrix,
                safeRenderer.viewportWidth,
                safeRenderer.viewportHeight,
                x.toDouble(),
                y.toDouble(),
            )
            var direction = camera.getDirForward().clone()
            if (!Prefs.isOrthoProjectionEnabled()) {
                direction = point.clone().add(camera.position.clone().negate()).normalize()
            }
            approximatePickBounds?.let { (min, max) ->
                LargeModelPick.hit(point, direction, min, max)?.let(safeList::add)
                return
            }
            val hits = Native.glmodel_raycast_closest_hit(pointer, point.asDoubleArray(), direction.asDoubleArray())
            safeList.ensureCapacity(hits.size / 6)
            var i = 0
            while (i < hits.size) {
                safeList.add(
                    HitResult(
                        Vec3d(hits[i], hits[i + 1], hits[i + 2]),
                        Vec3d(hits[i + 3], hits[i + 4], hits[i + 5]),
                    ),
                )
                i += 6
            }
            }
        }
    }

    open class HitResult(
        @JvmField val position: Vec3d,
        @JvmField val normal: Vec3d,
        /** True for a selection-box hit; unsuitable for edits needing a real surface point. */
        @JvmField val approximate: Boolean = false,
    )
}
