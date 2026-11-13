package ru.ytkab0bp.slicebeam.components.bed_menu

import org.junit.Assert.assertEquals
import org.junit.Test

class TransformScaleValuesTest {
    @Test
    fun changingOneUnlinkedAxisKeepsOtherScaleValues() {
        val values = resolveTransformScaleValues(
            currentX = 1.0,
            currentY = 0.8,
            currentZ = 1.25,
            newValue = 1.05,
            editX = true,
            editY = false,
            editZ = false,
            linked = false,
        )

        assertEquals(1.05, values.x, 0.000001)
        assertEquals(0.8, values.y, 0.000001)
        assertEquals(1.25, values.z, 0.000001)
    }

    @Test
    fun changingUnlinkedYDoesNotChangeXOrZ() {
        val values = resolveTransformScaleValues(
            currentX = 1.0,
            currentY = 0.8,
            currentZ = 1.25,
            newValue = 1.5,
            editX = false,
            editY = true,
            editZ = false,
            linked = false,
        )

        assertEquals(1.0, values.x, 0.000001)
        assertEquals(1.5, values.y, 0.000001)
        assertEquals(1.25, values.z, 0.000001)
    }

    @Test
    fun changingUnlinkedZDoesNotChangeXOrY() {
        val values = resolveTransformScaleValues(
            currentX = 1.0,
            currentY = 0.8,
            currentZ = 1.25,
            newValue = 1.5,
            editX = false,
            editY = false,
            editZ = true,
            linked = false,
        )

        assertEquals(1.0, values.x, 0.000001)
        assertEquals(0.8, values.y, 0.000001)
        assertEquals(1.5, values.z, 0.000001)
    }

    @Test
    fun changingAnAxisWithLinkEnabledUpdatesAllAxes() {
        val values = resolveTransformScaleValues(
            currentX = 1.0,
            currentY = 0.8,
            currentZ = 1.25,
            newValue = 0.95,
            editX = true,
            editY = false,
            editZ = false,
            linked = true,
        )

        assertEquals(0.95, values.x, 0.000001)
        assertEquals(0.95, values.y, 0.000001)
        assertEquals(0.95, values.z, 0.000001)
    }

    @Test
    fun sequentialUnlinkedEditsKeepEachAppliedDimensionIndependent() {
        val afterY = resolveTransformScaleValues(
            1.0, 1.0, 1.0, 1.4, false, true, false, false,
        )
        val afterX = resolveTransformScaleValues(
            afterY.x, afterY.y, afterY.z, 0.7, true, false, false, false,
        )
        val afterZ = resolveTransformScaleValues(
            afterX.x, afterX.y, afterX.z, 1.8, false, false, true, false,
        )

        assertEquals(0.7, afterZ.x, 0.000001)
        assertEquals(1.4, afterZ.y, 0.000001)
        assertEquals(1.8, afterZ.z, 0.000001)
    }
}
