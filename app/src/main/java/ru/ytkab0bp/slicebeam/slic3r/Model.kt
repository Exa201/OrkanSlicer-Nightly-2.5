package ru.ytkab0bp.slicebeam.slic3r

import ru.ytkab0bp.slicebeam.SliceBeam
import ru.ytkab0bp.slicebeam.config.AdaptivePAModelValidator
import ru.ytkab0bp.slicebeam.config.ProfileStringVectorCodec
import ru.ytkab0bp.slicebeam.utils.Prefs
import ru.ytkab0bp.slicebeam.utils.SliceFilamentColorResolver
import ru.ytkab0bp.slicebeam.utils.Vec3d
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.ArrayList
import java.util.UUID

data class ModifierVolumeBoxState(
    val volumeIndex: Int,
    val type: Int,
    val position: DoubleArray,
    val size: DoubleArray,
)

/** Java-compatible wrapper for an Orca model owned by the native slicer. */
open class Model private constructor(@JvmField var pointer: Long) {
    @JvmField
    val key: String = UUID.randomUUID().toString()

    @Volatile
    private var geometryRevisionValue: Long = 0L
    @Volatile
    private var stateRevisionValue: Long = 0L

    private var boundingExact: DoubleArray? = null
    private var boundingApprox: DoubleArray? = null

    constructor() : this(Native.model_create())

    @Throws(Slic3rRuntimeError::class)
    constructor(file: File) : this(file.absolutePath, 0)

    @Throws(Slic3rRuntimeError::class)
    constructor(file: File, plateId: Int) : this(file.absolutePath, plateId)

    @Throws(Slic3rRuntimeError::class)
    constructor(path: String) : this(readModelPointer(path, 0))

    @Throws(Slic3rRuntimeError::class)
    constructor(path: String, plateId: Int) : this(readModelPointer(path, plateId))

    @Throws(Slic3rRuntimeError::class)
    constructor(file: File, plateId: Int, progress: ImportProgressCallback) : this(readModelPointer(file.absolutePath, plateId, progress))

    @Throws(Slic3rRuntimeError::class)
    constructor(file: File, plateId: Int, stepOptions: StepImportOptions, progress: ImportProgressCallback? = null) :
        this(readModelPointer(file.absolutePath, plateId, progress, stepOptions))

    open fun getBoundingBoxExact(i: Int, min: Vec3d, max: Vec3d) {
        if (!hasObject(i)) {
            min.set(0.0, 0.0, 0.0)
            max.set(0.0, 0.0, 0.0)
            return
        }
        val data = Native.model_get_bounding_box_exact(pointer, i)
        min.set(data[0], data[1], data[2])
        max.set(data[3], data[4], data[5])
    }

    open fun getBoundingBoxApprox(i: Int, min: Vec3d, max: Vec3d) {
        if (!hasObject(i)) {
            min.set(0.0, 0.0, 0.0)
            max.set(0.0, 0.0, 0.0)
            return
        }
        val data = Native.model_get_bounding_box_approx(pointer, i)
        min.set(data[0], data[1], data[2])
        max.set(data[3], data[4], data[5])
    }

    /** Minimum world-space mesh projection for a group of printable objects along a direction. */
    open fun getMinimumProjection(objectIndices: IntArray, direction: Vec3d): Double? {
        if (pointer == 0L || objectIndices.isEmpty() || objectIndices.any { !hasObject(it) } ||
            !direction.x.isFinite() || !direction.y.isFinite() || !direction.z.isFinite()
        ) return null
        val length = kotlin.math.sqrt(
            direction.x * direction.x + direction.y * direction.y + direction.z * direction.z,
        )
        if (!length.isFinite() || length <= 1e-9) return null
        return Native.model_get_minimum_projection(
            pointer,
            objectIndices,
            doubleArrayOf(direction.x / length, direction.y / length, direction.z / length),
        ).takeIf { it.isFinite() }
    }

    /** Exact minimum and maximum vertex projection of printable parts along a world-space axis. */
    open fun getProjectionRange(objectIndices: IntArray, direction: Vec3d): DoubleArray? {
        if (pointer == 0L || objectIndices.isEmpty() || objectIndices.any { !hasObject(it) } ||
            !direction.x.isFinite() || !direction.y.isFinite() || !direction.z.isFinite()
        ) return null
        val range = Native.model_get_projection_range(
            pointer,
            objectIndices,
            doubleArrayOf(direction.x, direction.y, direction.z),
        )
        return range.takeIf { it.size == 2 && it.all(Double::isFinite) && it[0] <= it[1] }
    }

    open val boundingBoxExactMin: Vec3d
        get() {
        val bounds = boundingExact ?: Native.model_get_bounding_box_exact_global(pointer).also {
            boundingExact = it
        }
        return Vec3d(bounds[0], bounds[1], bounds[2])
    }

    open val boundingBoxExactMax: Vec3d
        get() {
        val bounds = boundingExact ?: Native.model_get_bounding_box_exact_global(pointer).also {
            boundingExact = it
        }
        return Vec3d(bounds[3], bounds[4], bounds[5])
    }

    open val boundingBoxApproxMin: Vec3d
        get() {
        val bounds = boundingApprox ?: Native.model_get_bounding_box_approx_global(pointer).also {
            boundingApprox = it
        }
        return Vec3d(bounds[0], bounds[1], bounds[2])
    }

    open val boundingBoxApproxMax: Vec3d
        get() {
        val bounds = boundingApprox ?: Native.model_get_bounding_box_approx_global(pointer).also {
            boundingApprox = it
        }
        return Vec3d(bounds[3], bounds[4], bounds[5])
    }

    open fun resetBoundingBox() {
        boundingExact = null
        boundingApprox = null
        geometryRevisionValue++
        stateRevisionValue++
    }

    /** Revision for invalidating inexpensive analysis results tied to this model geometry. */
    open val geometryRevision: Long
        get() = geometryRevisionValue

    /** Revision for rejecting background edit results if any model state changed meanwhile. */
    open val stateRevision: Long
        get() = stateRevisionValue

    open fun markStateChanged() {
        stateRevisionValue++
    }

    /** Move an object in build-plate/world coordinates, independent of its rotation or scale. */
    open fun translate(i: Int, x: Double, y: Double, z: Double) {
        if (!hasObject(i) || !x.isFinite() || !y.isFinite() || !z.isFinite()) return
        Native.model_translate(pointer, i, x, y, z)
        resetBoundingBox()
    }

    open fun translate(x: Double, y: Double, z: Double) {
        if (pointer == 0L || !x.isFinite() || !y.isFinite() || !z.isFinite()) return
        Native.model_translate_global(pointer, x, y, z)
        resetBoundingBox()
    }

    open fun ensureOnBed(i: Int) {
        if (!hasObject(i)) return
        Native.model_ensure_on_bed(pointer, i)
        resetBoundingBox()
    }

    open fun scale(i: Int, x: Double, y: Double, z: Double) {
        if (!hasObject(i) || !x.isFinite() || !y.isFinite() || !z.isFinite()) return
        Native.model_scale(pointer, i, x, y, z)
        resetBoundingBox()
    }

    open fun rotate(i: Int, x: Double, y: Double, z: Double) {
        if (!hasObject(i) || !x.isFinite() || !y.isFinite() || !z.isFinite()) return
        Native.model_rotate(pointer, i, x, y, z)
        resetBoundingBox()
    }

    /** Reset rotations on all object volumes without changing their translations or scales. */
    open fun resetRotation(i: Int) {
        if (!hasObject(i)) return
        Native.model_reset_rotation(pointer, i)
        resetBoundingBox()
    }

    open fun flattenRotate(i: Int, surface: GLModel) {
        if (!hasObject(i) || surface.pointer == 0L) return
        Native.model_flatten_rotate(pointer, i, surface.pointer)
        resetBoundingBox()
    }

    open fun getPointer(): Long = pointer

    open val objectsCount: Int
        get() {
            if (pointer == 0L) {
                android.util.Log.e("Model", "CRITICAL ERROR: getObjectsCount called with pointer == 0!", Exception())
                return 0
            }
            return Native.model_get_objects_count(pointer)
        }

    open fun getObjectName(i: Int): String? {
        if (pointer == 0L || i < 0 || i >= objectsCount) return null
        val name = Native.model_get_object_name(pointer, i)
        if (name.isNullOrEmpty()) return null
        val trimmed = name.trim { it <= ' ' }
        return trimmed.takeIf { it.isNotEmpty() }
    }

    /** Number of printable mesh parts (volumes) represented by one object-list entry. */
    open fun getObjectPartCount(i: Int): Int =
        if (!hasObject(i)) 0 else Native.model_get_object_part_count(pointer, i)

    /** Returns the source/import name of a printable mesh part, or null when it has no name. */
    open fun getObjectPartName(objectIndex: Int, partIndex: Int): String? {
        if (!hasObject(objectIndex) || partIndex !in 0 until getObjectPartCount(objectIndex)) return null
        return Native.model_get_object_part_name(pointer, objectIndex, partIndex)
            ?.trim { it <= ' ' }
            ?.takeIf { it.isNotEmpty() }
    }

    /** Returns a serialized, explicitly overridden Orca object setting, or null to inherit. */
    open fun getObjectConfigOption(objectIndex: Int, key: String): String? =
        if (!hasObject(objectIndex) || key !in OBJECT_OVERRIDE_KEYS) null
        else Native.model_get_object_config_option(pointer, objectIndex, key)

    /** Atomically applies or clears supported per-object process overrides. Empty values inherit. */
    /** Returns 1 when changed, 0 when already equal, and -1 when the update is invalid. */
    open fun setObjectConfigOptions(objectIndex: Int, values: Map<String, String>): Int {
        if (!hasObject(objectIndex) || values.isEmpty() || values.keys.any { it !in OBJECT_OVERRIDE_KEYS }) return -1
        val current = values.keys.associateWith { getObjectConfigOption(objectIndex, it) ?: "" }
        if (current == values) return 0
        val keys = values.keys.toTypedArray()
        val serialized = values.values.toTypedArray()
        if (serialized.any { it.length > 256 }) return -1
        val applied = Native.model_set_object_config_options(pointer, objectIndex, keys, serialized)
        if (!applied) return -1
        markStateChanged()
        return 1
    }

    /** Returns a serialized process override on one printable object part, or null to inherit. */
    open fun getObjectPartConfigOption(objectIndex: Int, partIndex: Int, key: String): String? =
        if (!hasObject(objectIndex) || partIndex !in 0 until getObjectPartCount(objectIndex) || key !in PART_OVERRIDE_KEYS) null
        else Native.model_get_object_part_config_option(pointer, objectIndex, partIndex, key)

    /** Atomically applies or clears process overrides on one printable object part. */
    open fun setObjectPartConfigOptions(objectIndex: Int, partIndex: Int, values: Map<String, String>): Int {
        if (!hasObject(objectIndex) || partIndex !in 0 until getObjectPartCount(objectIndex) ||
            values.isEmpty() || values.keys.any { it !in PART_OVERRIDE_KEYS }) return -1
        val current = values.keys.associateWith { getObjectPartConfigOption(objectIndex, partIndex, it).orEmpty() }
        if (current == values) return 0
        val serialized = values.values.toTypedArray()
        if (serialized.any { it.length > 256 }) return -1
        if (!Native.model_set_object_part_config_options(pointer, objectIndex, partIndex, values.keys.toTypedArray(), serialized)) return -1
        markStateChanged()
        return 1
    }

    /** Adds an Orca-compatible modifier volume to the selected model object. Type: 1 negative, 2 parameter, 3 support blocker, 4 support enforcer. */
    open fun addModifierBox(objectIndex: Int, type: Int, x: Double, y: Double, z: Double, width: Double, depth: Double, height: Double): Int {
        if (!hasObject(objectIndex) || type !in 1..4) return -1
        if (listOf(x, y, z, width, depth, height).any { !it.isFinite() }) return -1
        val index = Native.model_add_modifier_box(pointer, objectIndex, type, x, y, z, width, depth, height)
        if (index >= 0) resetBoundingBox()
        return index
    }

    open fun getModifierVolumeIndices(objectIndex: Int): IntArray =
        if (!hasObject(objectIndex)) intArrayOf() else Native.model_get_modifier_volume_indices(pointer, objectIndex)

    open fun getVolumeType(objectIndex: Int, volumeIndex: Int): Int =
        if (!hasObject(objectIndex) || volumeIndex < 0) -1 else Native.model_get_volume_type(pointer, objectIndex, volumeIndex)

    open fun getModifierVolumeBoxState(objectIndex: Int, volumeIndex: Int): ModifierVolumeBoxState? {
        if (!hasObject(objectIndex) || volumeIndex < 0) return null
        val values = Native.model_get_modifier_volume_box_state(pointer, objectIndex, volumeIndex)
        if (values.size != 6 || values.any { !it.isFinite() }) return null
        return ModifierVolumeBoxState(volumeIndex, getVolumeType(objectIndex, volumeIndex), values.copyOfRange(0, 3), values.copyOfRange(3, 6))
    }

    open fun updateModifierBox(objectIndex: Int, volumeIndex: Int, x: Double, y: Double, z: Double, width: Double, depth: Double, height: Double): Boolean {
        if (!hasObject(objectIndex) || volumeIndex < 0 || listOf(x, y, z, width, depth, height).any { !it.isFinite() }) return false
        val updated = Native.model_update_modifier_box(pointer, objectIndex, volumeIndex, x, y, z, width, depth, height)
        if (updated) resetBoundingBox()
        return updated
    }

    open fun deleteModifierVolume(objectIndex: Int, volumeIndex: Int): Boolean {
        if (!hasObject(objectIndex) || volumeIndex < 0) return false
        val removed = Native.model_delete_volume(pointer, objectIndex, volumeIndex)
        if (removed) resetBoundingBox()
        return removed
    }

    /** One-based filament for the zero-based printable part index, ignoring modifier volumes. */
    open fun getObjectPartExtruder(objectIndex: Int, partIndex: Int): Int =
        if (!hasObject(objectIndex) || partIndex < 0) -1
        else Native.model_get_object_part_extruder(pointer, objectIndex, partIndex)

    /** Applies a relative transform to one printable part without detaching it from its parent object. */
    open fun transformObjectPart(
        objectIndex: Int,
        partIndex: Int,
        dx: Double,
        dy: Double,
        dz: Double,
        scaleX: Double = 1.0,
        scaleY: Double = 1.0,
        scaleZ: Double = 1.0,
        rotationXDegrees: Double = 0.0,
        rotationYDegrees: Double = 0.0,
        rotationZDegrees: Double = 0.0,
    ): Boolean {
        if (!hasObject(objectIndex) || partIndex !in 0 until getObjectPartCount(objectIndex)) return false
        if (listOf(dx, dy, dz, rotationXDegrees, rotationYDegrees, rotationZDegrees, scaleX, scaleY, scaleZ).any { !it.isFinite() } ||
            listOf(dx, dy, dz).any { kotlin.math.abs(it) > 10_000.0 } ||
            listOf(rotationXDegrees, rotationYDegrees, rotationZDegrees).any { kotlin.math.abs(it) > 36_000.0 } ||
            listOf(scaleX, scaleY, scaleZ).any { it <= 0.0 }
        ) return false
        val changed = Native.model_transform_object_part(
            pointer, objectIndex, partIndex, dx, dy, dz,
            rotationXDegrees, rotationYDegrees, rotationZDegrees, scaleX, scaleY, scaleZ,
        )
        if (changed) resetBoundingBox()
        return changed
    }

    open fun getTriangleCount(i: Int): Int =
        if (!hasObject(i)) 0 else Native.model_get_triangle_count(pointer, i)

    open fun simplify(i: Int, keepRatio: Float): Int {
        if (!hasObject(i) || !keepRatio.isFinite() || keepRatio <= 0f || keepRatio > 1f) return 0
        val result = Native.model_simplify(pointer, i, keepRatio)
        resetBoundingBox()
        return result
    }

    /** Loop-subdivides and smooths a model object while preserving painted facet assignments. */
    open fun smoothMesh(i: Int): Int {
        if (!hasObject(i)) return 0
        val result = Native.model_smooth_mesh(pointer, i)
        if (result > 0) resetBoundingBox()
        return result
    }

    open fun addObject(from: Model, i: Int) {
        if (pointer == 0L || from.pointer == 0L || i !in 0 until from.objectsCount) {
            android.util.Log.w(
                "Model",
                "Ignoring object copy with invalid native model or source index (index=$i, sourceCount=${from.objectsCount})",
            )
            return
        }
        Native.model_add_object_from_another(pointer, from.pointer, i)
        resetBoundingBox()
    }

    /** Atomically swaps this model's native contents with a prepared model on the GL thread. */
    open fun replaceContentsFrom(other: Model) {
        require(pointer != 0L && other.pointer != 0L && pointer != other.pointer)
        Native.model_replace_contents(pointer, other.pointer)
        resetBoundingBox()
        other.resetBoundingBox()
    }

    /** Adds a linked instance of an object while preserving the source instance transform. */
    open fun duplicateInstance(i: Int, sourceInstanceIndex: Int, offset: Vec3d): Int {
        if (!hasObject(i) || sourceInstanceIndex < 0 ||
            !offset.x.isFinite() || !offset.y.isFinite() || !offset.z.isFinite()
        ) return -1
        val instanceIndex = Native.model_duplicate_instance(
            pointer, i, sourceInstanceIndex, offset.x, offset.y, offset.z,
        )
        if (instanceIndex >= 0) resetBoundingBox()
        return instanceIndex
    }

    open fun deleteObject(i: Int) {
        if (!hasObject(i)) return
        Native.model_delete_object(pointer, i)
        resetBoundingBox()
    }

    /** Split object i into separate objects; returns the piece count, or 0 if nothing to split. */
    open fun split(i: Int): Int {
        if (!hasObject(i)) return 0
        val result = Native.model_split(pointer, i)
        if (result > 0) resetBoundingBox()
        return result
    }

    private fun hasObject(index: Int): Boolean = pointer != 0L && index in 0 until objectsCount

    open fun cut(
        objectIndex: Int,
        zHeight: Double,
        rotX: Double,
        rotY: Double,
        keepUpper: Boolean,
        keepLower: Boolean,
    ): Boolean {
        val result = Native.model_cut(pointer, objectIndex, zHeight, rotX, rotY, keepUpper, keepLower)
        if (result) resetBoundingBox()
        return result
    }

    /** Applies a cut and removes only newly committed, unprocessed connectors if it fails. */
    open fun cutPreservingConnectorsOnFailure(
        objectIndex: Int,
        connectorCountBeforeCommit: Int,
        zHeight: Double,
        rotX: Double,
        rotY: Double,
        keepUpper: Boolean,
        keepLower: Boolean,
    ): Boolean {
        val applied = cut(objectIndex, zHeight, rotX, rotY, keepUpper, keepLower)
        if (!applied) {
            val connectorCount = Native.model_get_cut_connector_count(pointer, objectIndex)
            for (connectorIndex in connectorCount - 1 downTo connectorCountBeforeCommit) {
                removeConnector(objectIndex, connectorIndex)
            }
        }
        return applied
    }

    open fun getCutConnectorCount(objectIndex: Int): Int =
        Native.model_get_cut_connector_count(pointer, objectIndex)

    open fun addConnector(
        objectIndex: Int,
        position: Vec3d,
        radius: Float,
        height: Float,
        type: Int,
        rotX: Double,
        rotY: Double,
    ) {
        Native.model_add_connector_on_plane(
            pointer, objectIndex, position.x, position.y, position.z, radius, height, type, rotX, rotY,
        )
        resetBoundingBox()
    }

    open fun addConnector(objectIndex: Int, position: Vec3d, radius: Float, height: Float, type: Int) {
        Native.model_add_connector(pointer, objectIndex, position.x, position.y, position.z, radius, height, type)
        resetBoundingBox()
    }

    open fun removeConnector(objectIndex: Int, connectorIndex: Int) {
        Native.model_remove_connector(pointer, objectIndex, connectorIndex)
        resetBoundingBox()
    }

    open fun clearConnectors(objectIndex: Int) {
        Native.model_clear_connectors(pointer, objectIndex)
        resetBoundingBox()
    }

    open fun getTranslation(i: Int, vec: Vec3d) {
        val translation = Native.model_get_translation(pointer, i)
        vec.set(translation[0], translation[1], translation[2])
    }

    open fun getRotation(i: Int, vec: Vec3d) {
        val rotation = Native.model_get_rotation(pointer, i)
        vec.set(rotation[0], rotation[1], rotation[2])
    }

    open fun isLeftHanded(i: Int): Boolean = Native.model_is_left_handed(pointer, i)

    open fun getScale(i: Int, vec: Vec3d) {
        val scale = Native.model_get_scale(pointer, i)
        vec.set(scale[0], scale[1], scale[2])
    }

    open fun getMirror(i: Int, vec: Vec3d) {
        val mirror = Native.model_get_mirror(pointer, i)
        vec.set(mirror[0], mirror[1], mirror[2])
    }

    /** Mirrors every volume in an object around its current world-space bounds center. */
    open fun mirror(i: Int, axis: ModelMirrorAxis) {
        require(i in 0 until objectsCount) { "Invalid model object index: $i" }
        Native.model_mirror(pointer, i, axis.nativeValue)
        resetBoundingBox()
    }

    open fun createFlattenPlanes(i: Int): List<GLModel> {
        val pointers = Native.model_create_flatten_planes(pointer, i)
        val models = ArrayList<GLModel>(pointers.size)
        for (modelPointer in pointers) models.add(GLModel(modelPointer))
        return models
    }

    open fun getExtruder(i: Int): Int = Native.model_get_extruder(pointer, i)

    /** One-based filament assigned to a specific volume, including object-level fallback. */
    open fun getVolumeExtruder(objectIndex: Int, volumeIndex: Int): Int =
        if (pointer == 0L) -1 else Native.model_get_volume_extruder(pointer, objectIndex, volumeIndex)

    /** Assigns one printable part while leaving sibling parts and their paint data untouched. */
    open fun setObjectPartExtruder(objectIndex: Int, partIndex: Int, extruder: Int): Boolean {
        if (!hasObject(objectIndex) || partIndex !in 0 until getObjectPartCount(objectIndex) || extruder !in 1..16)
            return false
        val changed = Native.model_set_object_part_extruder(pointer, objectIndex, partIndex, extruder)
        if (changed) markStateChanged()
        return changed
    }

    open fun setExtruder(i: Int, extruder: Int) {
        Native.model_set_extruder(pointer, i, extruder)
        markStateChanged()
    }

    /** One-based filament indices used by parts and painted facets of this object. */
    open fun getUsedFilaments(i: Int): IntArray = Native.model_get_used_filaments(pointer, i)

    open fun setUniformFilament(i: Int, filament: Int) {
        Native.model_set_uniform_filament(pointer, i, filament)
        markStateChanged()
    }

    /** Remaps imported logical filament IDs (one-based) throughout all model-part volumes and painted facets. */
    open fun remapFilaments(sourceFilaments: IntArray, targetFilaments: IntArray) {
        require(sourceFilaments.size == targetFilaments.size) { "Source and target filament arrays must match" }
        Native.model_remap_filaments(pointer, sourceFilaments, targetFilaments)
        markStateChanged()
    }

    open fun applyAdaptiveLayerHeight(i: Int, configPath: String, qualityFactor: Float) {
        Native.model_apply_adaptive_layer_height(pointer, i, configPath, qualityFactor)
        markStateChanged()
    }

    /** Applies Orca's finest shared adaptive layer schedule to multiple independent objects. */
    open fun applyAdaptiveLayerHeightToObjects(indices: IntArray, configPath: String, qualityFactor: Float): Boolean {
        if (pointer == 0L || indices.size < 2 || indices.any { !hasObject(it) } ||
            indices.toSet().size != indices.size || configPath.isBlank() ||
            !qualityFactor.isFinite() || qualityFactor !in 0f..1f
        ) return false
        val applied = Native.model_apply_adaptive_layer_height_to_objects(pointer, indices, configPath, qualityFactor)
        if (applied) markStateChanged()
        return applied
    }

    /** Paints the Orca layer-height profile at an exact surface hit in build-volume coordinates. */
    open fun adjustLayerHeightAtWorldPoint(
        i: Int,
        configPath: String,
        worldX: Double,
        worldY: Double,
        worldZ: Double,
        bandWidthMm: Double,
        deltaMm: Double,
        action: Int,
        qualityFactor: Float,
    ): Boolean {
        if (!hasObject(i) || !worldX.isFinite() || !worldY.isFinite() || !worldZ.isFinite() ||
            !bandWidthMm.isFinite() || !deltaMm.isFinite() || bandWidthMm <= 0.0 || deltaMm <= 0.0 ||
            action !in 0..3 || !qualityFactor.isFinite()
        ) return false
        val changed = Native.model_adjust_layer_height_at_world_point(
            pointer, i, configPath, worldX, worldY, worldZ, bandWidthMm, deltaMm, action, qualityFactor,
        )
        if (changed) markStateChanged()
        return changed
    }

    open fun clearAdaptiveLayerHeight(i: Int) {
        Native.model_clear_adaptive_layer_height(pointer, i)
        markStateChanged()
    }

    open fun hasAdaptiveLayerHeight(i: Int): Boolean = Native.model_has_adaptive_layer_height(pointer, i)

    /** Returns alternating object-local Z/height samples for diagnostics and regression tests. */
    internal fun adaptiveLayerHeightProfile(i: Int): DoubleArray =
        if (hasObject(i)) Native.model_get_layer_height_profile(pointer, i) else doubleArrayOf()

    /** Copies another object's adaptive profile and fits only its ending to this object's height. */
    internal fun fitAdaptiveLayerHeightProfileToObject(sourceIndex: Int, targetIndex: Int): Boolean {
        if (!hasObject(sourceIndex) || !hasObject(targetIndex) || sourceIndex == targetIndex) return false
        val fitted = Native.model_fit_layer_height_profile_to_object(pointer, sourceIndex, targetIndex)
        if (fitted) markStateChanged()
        return fitted
    }

    open fun embossText(
        i: Int,
        fontPath: String,
        text: String,
        size: Float,
        depth: Float,
        type: Int,
        position: Vec3d,
        normal: Vec3d,
    ) {
        if (pointer == 0L || type !in 0..1) return
        val placement = ModelEditRequestValidation.snapshotTextPlacement(position, normal)
        if (!ModelEditRequestValidation.isValidTextRequest(
                fontPath, text, size, depth, placement.position, placement.normal, i, objectsCount,
            )
        ) return
        Native.model_emboss_text(
            pointer, i, fontPath, text, size, depth, type,
            placement.position.x, placement.position.y, placement.position.z,
            placement.normal.x, placement.normal.y, placement.normal.z,
        )
        resetBoundingBox()
    }

    /** Adds a validated vector SVG as a surface-aligned raised or engraved volume. */
    open fun embossSvg(
        objectIndex: Int,
        svgFile: File,
        width: Float,
        depth: Float,
        engraved: Boolean,
        position: Vec3d,
        normal: Vec3d,
    ): Boolean {
        if (pointer == 0L || objectIndex !in 0 until objectsCount || !svgFile.isFile ||
            !svgFile.name.endsWith(".svg", ignoreCase = true) || svgFile.length() !in 1..(5L * 1024L * 1024L) ||
            !width.isFinite() || width !in 0.5f..500f || !depth.isFinite() || depth !in 0.1f..100f
        ) return false
        val placement = ModelEditRequestValidation.snapshotTextPlacement(position, normal)
        Native.model_emboss_svg(
            pointer, objectIndex, svgFile.absolutePath, width, depth, if (engraved) 1 else 0,
            placement.position.x, placement.position.y, placement.position.z,
            placement.normal.x, placement.normal.y, placement.normal.z,
        )
        resetBoundingBox()
        return true
    }

    /** Creates printable text; with a target index, conforms it to that surface and adds it as a target part. */
    open fun createTextObject(
        fontPath: String,
        text: String,
        size: Float,
        depth: Float,
        position: Vec3d,
        normal: Vec3d,
        targetObjectIndex: Int = -1,
    ): Int {
        if (pointer == 0L) return -1
        val placement = ModelEditRequestValidation.snapshotTextPlacement(position, normal)
        if (!ModelEditRequestValidation.isValidTextRequest(
                fontPath, text, size, depth, placement.position, placement.normal, targetObjectIndex, objectsCount,
            )
        ) return -1
        val index = Native.model_create_text_object(
            pointer, fontPath, text, size, depth,
            placement.position.x, placement.position.y, placement.position.z,
            placement.normal.x, placement.normal.y, placement.normal.z,
            targetObjectIndex,
        )
        if (index >= 0) resetBoundingBox()
        return index
    }

    /** Creates an Orca-style standalone primitive. Dimensions are millimetres and shape-specific. */
    open fun createPrimitive(
        type: Int,
        a: Double,
        b: Double = 0.0,
        c: Double = 0.0,
        name: String,
        centerX: Double = 0.0,
        centerY: Double = 0.0,
    ): Int {
        val dimensionsValid = when (type) {
            0, 6 -> listOf(a, b, c).all { it.isFinite() && it in 0.2..500.0 }
            1, 2, 5 -> a.isFinite() && a in 0.2..500.0 && c.isFinite() && c in 0.2..500.0
            3 -> a.isFinite() && a in 0.2..500.0
            4 -> a.isFinite() && a in 0.6..500.0 && b.isFinite() && b >= 0.2 && b < a / 2.0 && b <= 500.0
            else -> false
        }
        if (pointer == 0L || !dimensionsValid || !centerX.isFinite() || !centerY.isFinite() ||
            kotlin.math.abs(centerX) > 10_000.0 || kotlin.math.abs(centerY) > 10_000.0
        ) return -1
        if (name.isBlank() || name.length > 80) return -1
        val index = Native.model_create_primitive(pointer, type, a, b, c, name)
        if (index >= 0) {
            if (centerX != 0.0 || centerY != 0.0) translate(index, centerX, centerY, 0.0)
            resetBoundingBox()
        }
        return index
    }

    /** Combines objects, materializing every linked instance as a placed multipart volume. */
    open fun combineObjectsAsParts(objectIndices: IntArray): Int {
        if (pointer == 0L) return -1
        val stableIndices = ModelEditRequestValidation.distinctObjectIndicesSnapshot(objectIndices, objectsCount) ?: return -1
        val index = Native.model_combine_objects_as_parts(pointer, stableIndices)
        if (index >= 0) resetBoundingBox()
        return index
    }

    /**
     * Applies a validated Orca/CGAL solid operation to the selected objects, including their instances.
     * The first index is the target; remaining objects are applied in order. Returns the
     * replacement object's index, or a negative status without changing inputs.
     */
    open fun booleanObjects(objectIndices: IntArray, operation: BooleanOperation): Int {
        if (pointer == 0L) return -1
        val stableIndices = ModelEditRequestValidation.distinctObjectIndicesSnapshot(objectIndices, objectsCount) ?: return -1
        val index = Native.model_boolean_objects(pointer, stableIndices, operation.nativeValue)
        if (index >= 0) resetBoundingBox()
        return index
    }

    open fun booleanObjects(targetIndex: Int, toolIndex: Int, operation: BooleanOperation): Int =
        booleanObjects(intArrayOf(targetIndex, toolIndex), operation)

    /** Absolute mesh volume in mm³ for one object, including its instance transform. */
    open fun getObjectVolume(i: Int): Double =
        if (!hasObject(i)) 0.0 else Native.model_get_object_volume(pointer, i)

    /** True if object i has any committed multi-color painting. */
    open fun hasPaint(i: Int): Boolean = hasObject(i) && Native.model_has_paint(pointer, i)

    /** Fill a GLModel with object i's committed painting for the given filament; returns triangle count. */
    open fun buildPaintOverlay(i: Int, gl: GLModel, filamentIdx: Int): Int =
        if (!hasObject(i) || gl.pointer == 0L || filamentIdx < 1) 0
        else Native.model_build_paint_overlay(pointer, i, gl.pointer, filamentIdx)

    /** Highest filament index (1-based) painted on object i, or 0 if none. */
    open fun paintMaxFilament(i: Int): Int =
        if (!hasObject(i)) 0 else Native.model_paint_max_filament(pointer, i)

    /** Highest 1-based tool referenced by the model's base assignments or painted facets. */
    open val requiredFilamentCount: Int
        get() {
            var count = 1
            for (i in 0 until objectsCount) {
                count = maxOf(count, getUsedFilaments(i).maxOrNull() ?: 1)
            }
            return count
        }

    open fun autoOrient(i: Int) {
        if (!hasObject(i)) return
        Native.model_auto_orient(pointer, i)
        resetBoundingBox()
    }

    open fun isBigObject(i: Int): Boolean = Native.model_is_big_object(pointer, i)

    @Throws(Slic3rRuntimeError::class)
    open fun slice(configPath: String, gcodePath: String, listener: SliceListener?): GCodeProcessorResult =
        slice(configPath, gcodePath, listener, 0, 0.0, 0.0, 0.0)

    /** Slice as an OrcaSlicer calibration (calibMode = CalibMode ordinal; 0 = normal print). */
    @Throws(Slic3rRuntimeError::class)
    open fun slice(
        configPath: String,
        gcodePath: String,
        listener: SliceListener?,
        calibMode: Int,
        calibStart: Double,
        calibEnd: Double,
        calibStep: Double,
    ): GCodeProcessorResult {
        restoreAutoBrimForNative(configPath)

        // Declare enough filament slots for object assignments and painted facets so native
        // slicing emits all required multi-material tool changes.
        val numFilaments = requiredFilamentCount
        val configColors = if (numFilaments > 1) runCatching {
            File(configPath).useLines { lines ->
                lines.lastOrNull { it.startsWith("filament_colour = ") }
                    ?.substringAfter("filament_colour = ")
            }
        }.getOrNull() else null
        val colors = if (numFilaments > 1) {
            SliceFilamentColorResolver.resolve(
                configColors,
                numFilaments,
                Prefs.getFilamentPalette(),
            )
        } else {
            null
        }
        // Native slicing can emit progress before generating G-code. Keep the nullable public API
        // safe by supplying a no-op callback when callers do not need progress updates.
        val nativeListener = listener ?: SliceListener { _, _ -> }
        val resultPointer = Native.model_slice(
            pointer, configPath, gcodePath, nativeListener, numFilaments, colors ?: IntArray(0),
            calibMode, calibStart, calibEnd, calibStep,
        )
        return GCodeProcessorResult(resultPointer).also { result ->
            // Keep the effective ACE/profile palette with the result so the tool-color
            // preview shows the same colors that the native slicer actually received.
            result.previewToolColors = if (numFilaments > 1) {
                SliceFilamentColorResolver.previewColorsWithAlpha(
                    configColors,
                    numFilaments,
                    Prefs.getFilamentPalette(),
                )
            } else null
            // Invalid calibration rows are advisory only; they must not fail slicing.
            result.adaptivePAModelWarnings = runCatching {
                val config = File(configPath).useLines { lines ->
                    lines.mapNotNull { line ->
                        val separator = line.indexOf(" = ")
                        if (separator <= 0) null else line.substring(0, separator) to line.substring(separator + 3)
                    }.toMap()
                }
                val usedFilaments = (0 until objectsCount)
                    .flatMap { getUsedFilaments(it).asIterable() }
                    .filter { it > 0 }
                    .map { it - 1 }
                    .toSet()
                AdaptivePAModelValidator.sliceWarnings(config, usedFilaments)
            }.getOrDefault(emptyList())
        }
    }

    @Throws(Slic3rRuntimeError::class)
    open fun export3mf(configPath: String, _3mfPath: String) {
        restoreAutoBrimForNative(configPath)
        Native.model_export_3mf(pointer, configPath, _3mfPath)
    }

    open fun release() {
        if (pointer != 0L) {
            Native.model_release(pointer)
            pointer = 0L
            resetBoundingBox()
        }
    }

    @Suppress("deprecation")
    @Throws(Throwable::class)
    protected open fun finalize() {
        release()
    }

    private fun restoreAutoBrimForNative(configPath: String) {
        if (!SliceBeam.AUTO_BRIM_SELECTED) return
        try {
            val configFile = File(configPath)
            val config = readUtf8File(configFile)
            // Only replace outer_only → auto_brim to avoid mangling other brim_type values.
            if (config.contains("brim_type = outer_only")) {
                writeUtf8File(configFile, config.replace("brim_type = outer_only", "brim_type = auto_brim"))
            }
        } catch (error: Exception) {
            error.printStackTrace()
        }
    }

    private fun readUtf8File(file: File): String {
        val output = ByteArrayOutputStream()
        FileInputStream(file).use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count == -1) break
                output.write(buffer, 0, count)
            }
        }
        return String(output.toByteArray(), StandardCharsets.UTF_8)
    }

    private fun writeUtf8File(file: File, content: String) {
        FileOutputStream(file, false).use { output -> output.write(content.toByteArray(StandardCharsets.UTF_8)) }
    }

    companion object {
        // These PrintRegionConfig keys are applied consistently to each printable model-part volume.
        // Keep the first mobile editor focused on high-value, unambiguous controls.
        private val PART_OVERRIDE_KEYS = setOf(
            "wall_loops", "sparse_infill_density", "top_shell_layers", "sparse_infill_pattern",
            "top_surface_pattern", "bottom_surface_pattern",
        )
        private val OBJECT_OVERRIDE_KEYS = PART_OVERRIDE_KEYS + setOf("enable_support", "support_type", "seam_position", "raft_layers")

        private fun readModelPointer(
            path: String,
            plateId: Int,
            progress: ImportProgressCallback? = null,
            stepOptions: StepImportOptions? = null,
        ): Long {
            val result = OrcaModelBridge.load(path, plateId, progress, stepOptions)
            if (!result.isSuccess()) {
                throw Slic3rRuntimeError(result.errorMessage, result.cause)
            }
            return result.pointer
        }

        @JvmStatic
        fun merge(vararg models: Model): Model {
            val pointers = LongArray(models.size) { i -> models[i].pointer }
            return Model(Native.models_merge(*pointers))
        }
    }
}

enum class BooleanOperation(val nativeValue: Int) {
    UNION(0), DIFFERENCE(1), INTERSECTION(2),
}
