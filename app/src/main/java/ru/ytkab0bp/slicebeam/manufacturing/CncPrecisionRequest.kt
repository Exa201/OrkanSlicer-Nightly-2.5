package ru.ytkab0bp.slicebeam.manufacturing

/** Calibration measured by the user for one printer/nozzle/material combination. */
data class CncFitCalibration(
    val printerId: String,
    val nozzleDiameterMm: Double,
    val materialId: String,
    val innerCompensationMm: Double,
    val outerCompensationMm: Double,
) {
    init {
        require(printerId.isNotBlank())
        require(materialId.isNotBlank())
        require(nozzleDiameterMm.isFinite() && nozzleDiameterMm in 0.1..2.0)
        require(innerCompensationMm.isFinite() && innerCompensationMm in -2.0..2.0)
        require(outerCompensationMm.isFinite() && outerCompensationMm in -2.0..2.0)
    }
}

/** Immutable request; a missing calibration means analysis only, never an assumed correction. */
data class CncPrecisionRequest(
    val printerId: String,
    val nozzleDiameterMm: Double,
    val materialId: String,
    val desiredClearanceMm: Double,
    val calibration: CncFitCalibration? = null,
) {
    init {
        require(printerId.isNotBlank())
        require(materialId.isNotBlank())
        require(nozzleDiameterMm.isFinite() && nozzleDiameterMm in 0.1..2.0)
        require(desiredClearanceMm.isFinite() && desiredClearanceMm in 0.0..2.0)
        require(calibration == null || (
            calibration.printerId == printerId &&
                calibration.materialId.equals(materialId, ignoreCase = true) &&
                kotlin.math.abs(calibration.nozzleDiameterMm - nozzleDiameterMm) < 0.0001
            )) { "Calibration must match printer, nozzle and material" }
    }

    val hasMeasuredCalibration: Boolean get() = calibration != null
}
