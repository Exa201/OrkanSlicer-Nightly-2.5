package ru.ytkab0bp.slicebeam.manufacturing

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ToolAccessibilityRequestTest {
    @Test
    fun normalizesApproachDirection() {
        val request = ToolAccessibilityRequest(AccessibilityToolShape.FLAT_END_MILL, 3.0, 20.0, 0.0, 3.0, 4.0)
        assertArrayEquals(doubleArrayOf(0.0, 0.6, 0.8), request.normalizedApproachDirection, 1e-9)
    }

    @Test
    fun acceptsVBitAngleOnlyForVBit() {
        val vBit = ToolAccessibilityRequest(AccessibilityToolShape.V_BIT, 2.0, 15.0, 0.0, 0.0, 2.0,
            vBitIncludedAngleDegrees = 60.0)
        assertEquals(60.0, vBit.vBitIncludedAngleDegrees!!, 0.0)
        assertThrows(IllegalArgumentException::class.java) {
            ToolAccessibilityRequest(AccessibilityToolShape.FLAT_END_MILL, 2.0, 15.0, 0.0, 0.0, 1.0,
                vBitIncludedAngleDegrees = 60.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ToolAccessibilityRequest(AccessibilityToolShape.V_BIT, 2.0, 15.0, 0.0, 0.0, 1.0)
        }
    }

    @Test
    fun rejectsInvalidToolDimensionsAndSampling() {
        assertThrows(IllegalArgumentException::class.java) {
            ToolAccessibilityRequest(AccessibilityToolShape.DRILL, 0.0, 10.0, 0.0, 0.0, 1.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ToolAccessibilityRequest(AccessibilityToolShape.BALL_END_MILL, 2.0, Double.POSITIVE_INFINITY, 0.0, 0.0, 1.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ToolAccessibilityRequest(AccessibilityToolShape.FLAT_END_MILL, 2.0, 10.0, 0.0, 0.0, 1.0, sampleSpacingMm = 0.01)
        }
    }

    @Test
    fun rejectsZeroOrNonFiniteApproachDirection() {
        assertThrows(IllegalArgumentException::class.java) {
            ToolAccessibilityRequest(AccessibilityToolShape.DRILL, 2.0, 10.0, 0.0, 0.0, 0.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ToolAccessibilityRequest(AccessibilityToolShape.DRILL, 2.0, 10.0, Double.NaN, 0.0, 1.0)
        }
    }
}
