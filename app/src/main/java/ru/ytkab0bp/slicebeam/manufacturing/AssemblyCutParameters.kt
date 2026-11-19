package ru.ytkab0bp.slicebeam.manufacturing

import kotlin.math.sqrt

/** Joint types that the assembly cutter may offer after a geometry operation is implemented. */
enum class AssemblyJointType {
    NONE,
    ALIGNMENT_PINS,
    TONGUE_AND_GROOVE,
    DOVETAIL,
    SCREW_CONNECTION,
    GLUE_SURFACE,
}

/**
 * Immutable, millimeter-based input for a future manual planar cut preview.
 * The plane normal is normalized once so consumers cannot accidentally interpret its magnitude
 * as a distance or apply different normalization rules in the UI and native geometry code.
 */
data class AssemblyCutParameters(
    val pointXmm: Double,
    val pointYmm: Double,
    val pointZmm: Double,
    val normalX: Double,
    val normalY: Double,
    val normalZ: Double,
    val jointType: AssemblyJointType = AssemblyJointType.NONE,
    val jointClearanceMm: Double = 0.0,
    val jointDepthMm: Double = 5.0,
    val jointWidthMm: Double = 3.0,
) {
    init {
        require(listOf(pointXmm, pointYmm, pointZmm).all { it.isFinite() && it in -100_000.0..100_000.0 }) {
            "Cut plane point must be finite and within the supported model range"
        }
        require(listOf(normalX, normalY, normalZ).all(Double::isFinite)) {
            "Cut plane normal must be finite"
        }
        val normalLength = sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ)
        require(normalLength.isFinite() && normalLength > 1e-8) { "Cut plane normal must not be zero" }
        require(jointClearanceMm.isFinite() && jointClearanceMm in 0.0..1.0) {
            "Joint clearance must be between 0 and 1 mm"
        }
        require(jointDepthMm.isFinite() && jointDepthMm in 0.5..50.0) {
            "Joint depth must be between 0.5 and 50 mm"
        }
        require(jointWidthMm.isFinite() && jointWidthMm in 0.5..20.0) {
            "Joint width must be between 0.5 and 20 mm"
        }
    }

    val normalizedNormal: DoubleArray
        get() {
            val length = sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ)
            return doubleArrayOf(normalX / length, normalY / length, normalZ / length)
        }
}
