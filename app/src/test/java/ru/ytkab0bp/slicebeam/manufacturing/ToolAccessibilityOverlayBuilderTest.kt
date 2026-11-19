package ru.ytkab0bp.slicebeam.manufacturing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolAccessibilityOverlayBuilderTest {
    @Test
    fun createsSeparateColoredMarkerMeshesAtSampleCoordinates() {
        val samples = listOf(
            ToolSurfaceSample(0, 1.0, 2.0, 3.0),
            ToolSurfaceSample(0, 4.0, 5.0, 6.0),
            ToolSurfaceSample(0, 7.0, 8.0, 9.0),
            ToolSurfaceSample(0, 10.0, 11.0, 12.0),
        )
        val classification = ToolAccessibilityRegionClassifier.classify(
            ToolAccessibilityRaycastResult(intArrayOf(0, 1), intArrayOf(2, 3)),
            listOf(
                ToolAccessRegion(1, intArrayOf(0)),
                ToolAccessRegion(2, intArrayOf(1, 2)),
                ToolAccessRegion(3, intArrayOf(3)),
            ), sampleCount = samples.size,
        )
        val meshes = ToolAccessibilityOverlayBuilder.build(samples, classification, markerRadiusMm = 0.5)
        assertNotNull(meshes)
        assertEquals(8 * 9, meshes!!.accessibleTriangles.size)
        assertEquals(2 * 8 * 9, meshes.constrainedTriangles.size)
        assertEquals(8 * 9, meshes.blockedTriangles.size)
        assertTrue(meshes.accessibleTriangles.any { it == 1.5f })
        assertTrue(meshes.constrainedTriangles.any { it == 5.5f })
        assertTrue(meshes.blockedTriangles.any { it == 12.5f })
    }

    @Test
    fun rejectsInvalidAndUnboundedOverlayInputs() {
        val sample = ToolSurfaceSample(0, 0.0, 0.0, 0.0)
        val classification = ToolAccessibilityRegionClassifier.classify(
            ToolAccessibilityRaycastResult(intArrayOf(0), intArrayOf()),
            listOf(ToolAccessRegion(1, intArrayOf(0))), 1,
        )
        assertNull(ToolAccessibilityOverlayBuilder.build(listOf(sample), classification, Double.NaN))
        assertNull(ToolAccessibilityOverlayBuilder.build(listOf(sample), classification, 0.01))
        assertNull(ToolAccessibilityOverlayBuilder.build(List(1025) { sample }, classification, 0.5))
        assertNull(ToolAccessibilityOverlayBuilder.build(listOf(sample), classification.copy(rejected = ToolAccessRejection.INVALID_MESH), 0.5))
    }
}
