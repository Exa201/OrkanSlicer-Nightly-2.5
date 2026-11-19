package ru.ytkab0bp.slicebeam.manufacturing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ToolAccessibilitySurfaceSamplerTest {
    @Test
    fun samplesAreDeterministicAndRemainInsideTheirSourceTriangles() {
        val mesh = doubleArrayOf(
            0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0, 0.0,
            2.0, 0.0, 1.0, 4.0, 0.0, 1.0, 2.0, 2.0, 1.0,
        )
        val before = mesh.copyOf()
        val first = ToolAccessibilitySurfaceSampler.sample(mesh, spacingMm = 0.2)
        val second = ToolAccessibilitySurfaceSampler.sample(mesh, spacingMm = 0.2)

        assertTrue(first.isAvailable)
        assertEquals(first.samples, second.samples)
        val invalidSample = first.samples.firstOrNull { sample ->
            sample.targetTriangleIndex !in 0..1 ||
                (sample.targetTriangleIndex == 0 && (sample.xMm < 0.0 || sample.yMm < 0.0 || sample.xMm + sample.yMm > 1.0 + 1e-9 || abs(sample.zMm) > 1e-9)) ||
                (sample.targetTriangleIndex == 1 && (sample.xMm < 2.0 || sample.yMm < 0.0 || sample.xMm + sample.yMm > 4.0 + 1e-9 || abs(sample.zMm - 1.0) > 1e-9))
        }
        assertTrue("sample outside source face: $invalidSample", invalidSample == null)
        assertTrue(first.samples.any { it.targetTriangleIndex == 0 })
        assertTrue(first.samples.any { it.targetTriangleIndex == 1 })
        assertTrue(first.samples.size <= 1024)
        assertTrue(mesh.contentEquals(before))
    }

    @Test
    fun sampleBudgetIsAreaWeightedAndBounded() {
        val small = doubleArrayOf(0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0, 0.0)
        val large = doubleArrayOf(0.0, 0.0, 0.0, 10.0, 0.0, 0.0, 0.0, 10.0, 0.0)
        val result = ToolAccessibilitySurfaceSampler.sample(small + large, spacingMm = 0.05)
        val smallCount = result.samples.count { it.targetTriangleIndex == 0 }
        val largeCount = result.samples.count { it.targetTriangleIndex == 1 }

        assertTrue(result.isAvailable)
        assertEquals(1024, result.samples.size)
        assertTrue("large surface should receive proportionally more samples", largeCount > smallCount * 50)
    }

    @Test
    fun degenerateFacesAreSkippedAndAllDegenerateInputIsUnavailable() {
        val degenerate = doubleArrayOf(0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 2.0, 2.0, 2.0)
        val valid = doubleArrayOf(0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0, 0.0)
        val mixed = ToolAccessibilitySurfaceSampler.sample(degenerate + valid, spacingMm = 0.5)

        assertTrue(mixed.isAvailable)
        assertTrue(mixed.samples.all { it.targetTriangleIndex == 1 })
        assertFalse(ToolAccessibilitySurfaceSampler.sample(degenerate, spacingMm = 0.5).isAvailable)
        assertEquals(ToolSurfaceSamplingRejection.NO_SURFACE,
            ToolAccessibilitySurfaceSampler.sample(degenerate, spacingMm = 0.5).rejected)
    }

    @Test
    fun malformedSpacingAndOversizedMeshesAreRejected() {
        val triangle = doubleArrayOf(0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0, 0.0)
        assertEquals(ToolSurfaceSamplingRejection.INVALID_MESH,
            ToolAccessibilitySurfaceSampler.sample(triangle + Double.NaN, 0.5).rejected)
        assertEquals(ToolSurfaceSamplingRejection.INVALID_MESH,
            ToolAccessibilitySurfaceSampler.sample(triangle, Double.POSITIVE_INFINITY).rejected)
        assertEquals(ToolSurfaceSamplingRejection.TOO_LARGE,
            ToolAccessibilitySurfaceSampler.sample(DoubleArray(4097 * 9), 0.5).rejected)
    }
}
