package ru.ytkab0bp.slicebeam.manufacturing

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class AssemblyCutParametersTest {
    @Test
    fun normalIsNormalizedIndependentlyOfInputMagnitude() {
        val params = AssemblyCutParameters(
            pointXmm = 12.5,
            pointYmm = -4.0,
            pointZmm = 20.0,
            normalX = 0.0,
            normalY = 0.0,
            normalZ = -7.0,
            jointType = AssemblyJointType.ALIGNMENT_PINS,
            jointClearanceMm = 0.18,
            jointDepthMm = 8.0,
            jointWidthMm = 4.0,
        )

        assertArrayEquals(doubleArrayOf(0.0, 0.0, -1.0), params.normalizedNormal, 1e-9)
        assertEquals(12.5, params.pointXmm, 0.0)
        assertEquals(AssemblyJointType.ALIGNMENT_PINS, params.jointType)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroNormal() {
        AssemblyCutParameters(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonFinitePlane() {
        AssemblyCutParameters(Double.NaN, 0.0, 0.0, 1.0, 0.0, 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsClearanceOutsidePhysicalBounds() {
        AssemblyCutParameters(0.0, 0.0, 0.0, 1.0, 0.0, 0.0, jointClearanceMm = 1.01)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsJointDimensionsBelowPrintableMinimum() {
        AssemblyCutParameters(0.0, 0.0, 0.0, 1.0, 0.0, 0.0, jointDepthMm = 0.2)
    }
}
