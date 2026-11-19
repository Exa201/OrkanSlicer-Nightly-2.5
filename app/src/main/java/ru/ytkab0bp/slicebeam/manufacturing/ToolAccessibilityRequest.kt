package ru.ytkab0bp.slicebeam.manufacturing

import kotlin.math.sqrt

/** Geometric cutter families supported by the offline accessibility analyzer. */
enum class AccessibilityToolShape {
    FLAT_END_MILL,
    BALL_END_MILL,
    V_BIT,
    DRILL,
}

/**
 * Immutable input for a tool access query. Values use millimeters and an approach direction
 * in model coordinates; this type does not assume machine kinematics or claim collision safety.
 */
data class ToolAccessibilityRequest(
    val shape: AccessibilityToolShape,
    val radiusMm: Double,
    val reachMm: Double,
    val approachX: Double,
    val approachY: Double,
    val approachZ: Double,
    val sampleSpacingMm: Double = 0.5,
    val vBitIncludedAngleDegrees: Double? = null,
) {
    init {
        require(radiusMm.isFinite() && radiusMm in 0.05..100.0) {
            "Tool radius must be between 0.05 and 100 mm"
        }
        require(reachMm.isFinite() && reachMm in 0.1..500.0) {
            "Tool reach must be between 0.1 and 500 mm"
        }
        require(listOf(approachX, approachY, approachZ).all(Double::isFinite)) {
            "Tool approach direction must be finite"
        }
        val approachLength = sqrt(approachX * approachX + approachY * approachY + approachZ * approachZ)
        require(approachLength.isFinite() && approachLength > 1e-8) {
            "Tool approach direction must not be zero"
        }
        require(sampleSpacingMm.isFinite() && sampleSpacingMm in 0.05..5.0) {
            "Sample spacing must be between 0.05 and 5 mm"
        }
        if (shape == AccessibilityToolShape.V_BIT) {
            require(vBitIncludedAngleDegrees != null && vBitIncludedAngleDegrees.isFinite() &&
                vBitIncludedAngleDegrees in 10.0..180.0) {
                "V-bit included angle must be between 10 and 180 degrees"
            }
        } else {
            require(vBitIncludedAngleDegrees == null) {
                "Included angle is only valid for a V-bit"
            }
        }
    }

    val normalizedApproachDirection: DoubleArray
        get() {
            val length = sqrt(approachX * approachX + approachY * approachY + approachZ * approachZ)
            return doubleArrayOf(approachX / length, approachY / length, approachZ / length)
        }
}
