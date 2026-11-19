package ru.ytkab0bp.slicebeam.manufacturing

enum class CncFitFeature { INTERNAL, EXTERNAL }

/** A calculated dimension only; this does not modify a mesh or create a manufacturing file. */
data class CncFitDimensionResult(
    val nominalDiameterMm: Double,
    val targetDiameterMm: Double,
    val appliedCalibrationMm: Double,
    val appliedClearanceMm: Double,
    val feature: CncFitFeature,
)

object CncFitCompensation {
    /**
     * Calculates a diameter target from a diametral clearance and an explicitly measured,
     * matching calibration. Internal clearance increases the bore; external clearance reduces
     * the shaft. Calibration values are signed diameter corrections for the matching feature.
     * Missing calibration returns null instead of assuming a machine/material correction.
     */
    fun calculate(
        nominalDiameterMm: Double,
        feature: CncFitFeature,
        request: CncPrecisionRequest,
    ): CncFitDimensionResult? {
        if (!nominalDiameterMm.isFinite() || nominalDiameterMm !in 0.1..500.0) return null
        val calibration = request.calibration ?: return null
        val calibrationMm = when (feature) {
            CncFitFeature.INTERNAL -> calibration.innerCompensationMm
            CncFitFeature.EXTERNAL -> calibration.outerCompensationMm
        }
        val signedClearance = when (feature) {
            CncFitFeature.INTERNAL -> request.desiredClearanceMm
            CncFitFeature.EXTERNAL -> -request.desiredClearanceMm
        }
        val target = nominalDiameterMm + calibrationMm + signedClearance
        if (!target.isFinite() || target <= 0.0 || target > 500.0) return null
        return CncFitDimensionResult(
            nominalDiameterMm = nominalDiameterMm,
            targetDiameterMm = target,
            appliedCalibrationMm = calibrationMm,
            appliedClearanceMm = signedClearance,
            feature = feature,
        )
    }
}
