package ru.ytkab0bp.slicebeam.manufacturing

/** Parses bounded UI measurements without silently substituting defaults. */
object CncFitCalibrationInput {
    fun parse(
        printerId: String,
        nozzleDiameterMm: Double,
        materialId: String,
        innerCorrection: String,
        outerCorrection: String,
    ): CncFitCalibration? {
        val inner = innerCorrection.trim().replace(',', '.').toDoubleOrNull() ?: return null
        val outer = outerCorrection.trim().replace(',', '.').toDoubleOrNull() ?: return null
        if (!inner.isFinite() || !outer.isFinite() || inner !in -2.0..2.0 || outer !in -2.0..2.0) return null
        return runCatching {
            CncFitCalibration(printerId, nozzleDiameterMm, materialId, inner, outer)
        }.getOrNull()
    }
}
