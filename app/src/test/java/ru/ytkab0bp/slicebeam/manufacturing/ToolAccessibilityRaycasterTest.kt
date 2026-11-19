package ru.ytkab0bp.slicebeam.manufacturing

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolAccessibilityRaycasterTest {
    private val target = doubleArrayOf(-1.0, -1.0, 0.0, 1.0, -1.0, 0.0, 0.0, 1.0, 0.0)
    private val centerSample = ToolSurfaceSample(0, 0.0, 0.0, 0.0)
    private val tool = ToolAccessibilityRequest(AccessibilityToolShape.FLAT_END_MILL, 1.0, 10.0, 0.0, 0.0, 1.0)

    @Test
    fun clearRayReachesTargetSample() {
        val result = ToolAccessibilityRaycaster.analyze(target, listOf(centerSample), tool)
        assertTrue(result.isAvailable)
        assertArrayEquals(intArrayOf(0), result.accessibleSampleIndices)
        assertTrue(result.blockedSampleIndices.isEmpty())
    }

    @Test
    fun interveningTriangleBlocksRayButDoesNotChangeSourceMesh() {
        val mesh = target + doubleArrayOf(-1.0, -1.0, 1.0, 1.0, -1.0, 1.0, 0.0, 1.0, 1.0)
        val before = mesh.copyOf()
        val sample = centerSample.copy(targetTriangleIndex = 0)
        val result = ToolAccessibilityRaycaster.analyze(mesh, listOf(sample), tool)
        assertArrayEquals(intArrayOf(0), result.blockedSampleIndices)
        assertTrue(result.accessibleSampleIndices.isEmpty())
        assertArrayEquals(before, mesh, 0.0)
    }

    @Test
    fun approachDirectionChangesWhichBlockerIsEncountered() {
        val mesh = target + doubleArrayOf(-1.0, -1.0, -1.0, 1.0, -1.0, -1.0, 0.0, 1.0, -1.0)
        val fromAbove = ToolAccessibilityRaycaster.analyze(mesh, listOf(centerSample), tool)
        val fromBelow = ToolAccessibilityRaycaster.analyze(mesh, listOf(centerSample), tool.copy(approachZ = -1.0))
        assertTrue(fromAbove.blockedSampleIndices.isEmpty())
        assertArrayEquals(intArrayOf(0), fromBelow.blockedSampleIndices)
    }

    @Test
    fun invalidMeshSamplesAndOversizedInputsAreUnavailable() {
        assertEquals(ToolAccessRejection.INVALID_MESH,
            ToolAccessibilityRaycaster.analyze(doubleArrayOf(0.0, Double.NaN), listOf(centerSample), tool).rejected)
        assertEquals(ToolAccessRejection.INVALID_SAMPLE,
            ToolAccessibilityRaycaster.analyze(target, listOf(centerSample.copy(targetTriangleIndex = 1)), tool).rejected)
        val largeMesh = DoubleArray((4097 * 9))
        assertEquals(ToolAccessRejection.TOO_LARGE,
            ToolAccessibilityRaycaster.analyze(largeMesh, listOf(centerSample), tool).rejected)
        assertEquals(ToolAccessRejection.TOO_LARGE,
            ToolAccessibilityRaycaster.analyze(target, List(1025) { centerSample }, tool).rejected)
        assertEquals(ToolAccessRejection.INVALID_SAMPLE,
            ToolAccessibilityRaycaster.analyze(target, listOf(centerSample.copy(xMm = 10.0)), tool).rejected)
        assertNull(ToolAccessibilityRaycaster.analyze(target, listOf(centerSample), tool).rejected)
    }
}
