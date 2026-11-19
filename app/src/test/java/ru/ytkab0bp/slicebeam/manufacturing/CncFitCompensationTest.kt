package ru.ytkab0bp.slicebeam.manufacturing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CncFitCompensationTest {
    private val calibration = CncFitCalibration("printer", 0.4, "PLA", 0.08, -0.03)

    @Test
    fun internalAndExternalDimensionsUseTheirOwnMeasuredCorrectionAndClearanceSign() {
        val request = CncPrecisionRequest("printer", 0.4, "pla", 0.12, calibration)

        val bore = CncFitCompensation.calculate(10.0, CncFitFeature.INTERNAL, request)!!
        val shaft = CncFitCompensation.calculate(10.0, CncFitFeature.EXTERNAL, request)!!

        assertEquals(10.20, bore.targetDiameterMm, 1e-9)
        assertEquals(0.08, bore.appliedCalibrationMm, 1e-9)
        assertEquals(0.12, bore.appliedClearanceMm, 1e-9)
        assertEquals(9.85, shaft.targetDiameterMm, 1e-9)
        assertEquals(-0.03, shaft.appliedCalibrationMm, 1e-9)
        assertEquals(-0.12, shaft.appliedClearanceMm, 1e-9)
    }

    @Test
    fun noCalibrationProducesNoCompensatedDimension() {
        val request = CncPrecisionRequest("printer", 0.4, "PLA", 0.12)
        assertNull(CncFitCompensation.calculate(10.0, CncFitFeature.INTERNAL, request))
    }

    @Test
    fun invalidOrNonPositiveResultIsRejected() {
        val request = CncPrecisionRequest(
            "printer", 0.4, "PLA", 0.1,
            CncFitCalibration("printer", 0.4, "PLA", -2.0, 0.0),
        )
        assertNull(CncFitCompensation.calculate(1.0, CncFitFeature.INTERNAL, request))
        assertNull(CncFitCompensation.calculate(Double.NaN, CncFitFeature.INTERNAL, request))
    }
}
