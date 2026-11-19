package ru.ytkab0bp.slicebeam.manufacturing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CncFitCalibrationInputTest {
    @Test
    fun parsesSignedCorrectionsAndGermanDecimalSeparators() {
        val result = CncFitCalibrationInput.parse("Printer", 0.4, "PLA", " -0,12 ", "0.08")!!
        assertEquals(-0.12, result.innerCompensationMm, 1e-9)
        assertEquals(0.08, result.outerCompensationMm, 1e-9)
    }

    @Test
    fun acceptsCorrectionRangeBoundaries() {
        assertEquals(-2.0, CncFitCalibrationInput.parse("P", 0.4, "PLA", "-2", "2")!!.innerCompensationMm, 0.0)
        assertEquals(2.0, CncFitCalibrationInput.parse("P", 0.4, "PLA", "-2", "2")!!.outerCompensationMm, 0.0)
    }

    @Test
    fun rejectsMissingMalformedNonFiniteAndOutOfRangeMeasurements() {
        assertNull(CncFitCalibrationInput.parse("P", 0.4, "PLA", "", "0"))
        assertNull(CncFitCalibrationInput.parse("P", 0.4, "PLA", "bad", "0"))
        assertNull(CncFitCalibrationInput.parse("P", 0.4, "PLA", "NaN", "0"))
        assertNull(CncFitCalibrationInput.parse("P", 0.4, "PLA", "2.01", "0"))
        assertNull(CncFitCalibrationInput.parse("P", 0.4, "PLA", "0", "-2.01"))
    }

    @Test
    fun rejectsInvalidProfileIdentity() {
        assertNull(CncFitCalibrationInput.parse("", 0.4, "PLA", "0", "0"))
        assertNull(CncFitCalibrationInput.parse("P", 3.0, "PLA", "0", "0"))
        assertNull(CncFitCalibrationInput.parse("P", 0.4, "", "0", "0"))
    }
}
