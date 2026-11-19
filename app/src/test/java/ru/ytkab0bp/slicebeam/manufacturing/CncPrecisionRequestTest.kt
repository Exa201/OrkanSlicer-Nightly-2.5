package ru.ytkab0bp.slicebeam.manufacturing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CncPrecisionRequestTest {
    @Test
    fun absentCalibrationDoesNotInventACompensation() {
        val request = CncPrecisionRequest("printer-a", 0.4, "PLA", 0.15)
        assertFalse(request.hasMeasuredCalibration)
        assertTrue(request.calibration == null)
    }

    @Test
    fun matchingMeasuredCalibrationIsAccepted() {
        val calibration = CncFitCalibration("printer-a", 0.4, "PLA", 0.08, -0.03)
        val request = CncPrecisionRequest("printer-a", 0.4, "pla", 0.15, calibration)
        assertTrue(request.hasMeasuredCalibration)
    }

    @Test(expected = IllegalArgumentException::class)
    fun calibrationFromAnotherPrinterIsRejected() {
        CncPrecisionRequest("printer-a", 0.4, "PLA", 0.15, CncFitCalibration("printer-b", 0.4, "PLA", 0.08, 0.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonFiniteCalibrationValuesAreRejected() {
        CncFitCalibration("printer-a", 0.4, "PLA", Double.NaN, 0.0)
    }
}
