package ru.ytkab0bp.slicebeam.manufacturing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CncFitCalibrationStoreTest {
    private val values = mutableMapOf<String, String>()

    @Test
    fun missingRecordNeverReturnsAnAssumedCalibration() {
        assertNull(CncFitCalibrationStore.get("printer", 0.4, "PLA", values::get))
    }

    @Test
    fun roundTripsMeasuredCalibrationForMatchingIdentity() {
        val expected = CncFitCalibration("Printer Ω", 0.4, "PETG", 0.12, -0.04)
        CncFitCalibrationStore.put(expected) { key, value -> values[key] = value }

        assertEquals(expected, CncFitCalibrationStore.get("printer ω", 0.4, "petg", values::get))
    }

    @Test
    fun calibrationsAreSeparatedByPrinterNozzleAndMaterial() {
        CncFitCalibrationStore.put(CncFitCalibration("p1", 0.4, "PLA", 0.1, 0.2)) { key, value -> values[key] = value }
        CncFitCalibrationStore.put(CncFitCalibration("p1", 0.6, "PLA", 0.3, 0.4)) { key, value -> values[key] = value }
        CncFitCalibrationStore.put(CncFitCalibration("p1", 0.4, "PETG", 0.5, 0.6)) { key, value -> values[key] = value }
        CncFitCalibrationStore.put(CncFitCalibration("p2", 0.4, "PLA", 0.7, 0.8)) { key, value -> values[key] = value }

        assertEquals(4, values.size)
        assertEquals(0.3, CncFitCalibrationStore.get("p1", 0.6, "PLA", values::get)?.innerCompensationMm!!, 0.0)
        assertEquals(0.5, CncFitCalibrationStore.get("p1", 0.4, "PETG", values::get)?.innerCompensationMm!!, 0.0)
        assertEquals(0.7, CncFitCalibrationStore.get("p2", 0.4, "PLA", values::get)?.innerCompensationMm!!, 0.0)
    }

    @Test
    fun malformedOrMismatchedStoredRecordIsIgnored() {
        var key = ""
        CncFitCalibrationStore.put(CncFitCalibration("p", 0.4, "PLA", 0.1, 0.2)) { k, v -> key = k; values[k] = v }
        values[key] = "corrupt"
        assertNull(CncFitCalibrationStore.get("p", 0.4, "PLA", values::get))

        values[key] = "1|71|0.4|504c41|0.1,0.2" // Record identity belongs to a different printer.
        assertNull(CncFitCalibrationStore.get("p", 0.4, "PLA", values::get))
    }

    @Test
    fun removingOneCalibrationLeavesOtherIdentitiesUntouched() {
        CncFitCalibrationStore.put(CncFitCalibration("p", 0.4, "PLA", 0.1, 0.2)) { key, value -> values[key] = value }
        CncFitCalibrationStore.put(CncFitCalibration("p", 0.6, "PLA", 0.3, 0.4)) { key, value -> values[key] = value }
        CncFitCalibrationStore.remove("p", 0.4, "PLA", values::remove)

        assertNull(CncFitCalibrationStore.get("p", 0.4, "PLA", values::get))
        assertTrue(CncFitCalibrationStore.get("p", 0.6, "PLA", values::get) != null)
    }
}
