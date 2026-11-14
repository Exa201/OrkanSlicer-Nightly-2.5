package ru.ytkab0bp.slicebeam.slic3r

import ru.ytkab0bp.slicebeam.config.AdaptivePAModelValidator
import java.io.File

/** Owns the native analysis result for one G-code file. */
open class GCodeProcessorResult {
    /** Kept with this result across GL context recreation and screen rotation. */
    @Volatile var previewSelection: GCodePreviewSelection? = null
    @Volatile var previewLayerCount: Int = 0

    /** Exact tool palette passed to native slicing, in 0-based RGB values. */
    @Volatile var previewToolColors: IntArray? = null

    /** Invalid Adaptive PA measurements found in the used filament variants for this slice. */
    @Volatile var adaptivePAModelWarnings: List<AdaptivePAModelValidator.SliceWarning> = emptyList()

    /** Orca warning when object-level settings prevent Spiral Vase from producing one continuous wall. */
    open fun getSpiralVaseOverrideWarning(): String? =
        if (pointer == 0L) null else Native.gcoderesult_get_spiral_vase_override_warning(pointer)

    /** Seam markers parsed from the G-code preview stream. */
    open fun getSeamMarkerCount(): Int = if (pointer == 0L) 0 else Native.gcoderesult_get_seam_marker_count(pointer)

    /** Motion time spent printing the first layer, independent of machine preparation time. */
    open fun getInitialLayerTime(): Float = if (pointer == 0L) 0f else Native.gcoderesult_get_initial_layer_time(pointer)

    @JvmField
    internal var pointer: Long

    constructor(file: File) {
        pointer = Native.gcoderesult_load_file(file.absolutePath, file.name)
    }

    internal constructor(ptr: Long) {
        pointer = ptr
    }

    open fun getUsedFilamentMM(@GCodeViewer.ExtrusionRole role: Int): Double =
        Native.gcoderesult_get_used_filament_mm(pointer, role)

    open fun getUsedFilamentG(@GCodeViewer.ExtrusionRole role: Int): Double =
        Native.gcoderesult_get_used_filament_g(pointer, role)

    /** Wipe-tower volume in mm³ attributed by the native G-code analyzer to one zero-based filament index. */
    open fun getWipeTowerFilamentVolumeMm3(filamentIndex: Int): Double =
        if (pointer == 0L || filamentIndex < 0) 0.0 else Native.gcoderesult_get_wipe_tower_filament_volume(pointer, filamentIndex)

    open fun getRecommendedName(): String = Native.gcoderesult_get_recommended_name(pointer)

    /** Bitmask of native printable-area and print-height validation findings for this slice. */
    open fun getGCodeCheckErrorCode(): Int = Native.gcoderesult_get_gcode_check_error_code(pointer)

    open fun release() {
        if (pointer != 0L) {
            Native.gcoderesult_release(pointer)
            pointer = 0L
        }
    }

    @Suppress("DEPRECATION")
    @Throws(Throwable::class)
    protected open fun finalize() {
        release()
    }
}
