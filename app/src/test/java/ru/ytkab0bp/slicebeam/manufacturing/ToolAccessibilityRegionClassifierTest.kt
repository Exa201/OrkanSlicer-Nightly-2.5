package ru.ytkab0bp.slicebeam.manufacturing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ToolAccessibilityRegionClassifierTest {
    @Test
    fun classifiesAccessibleConstrainedAndBlockedRegionsAtDocumentedThresholds() {
        val result = ToolAccessibilityRegionClassifier.classify(
            raycast = ToolAccessibilityRaycastResult(
                accessibleSampleIndices = IntArray(19) { it },
                blockedSampleIndices = intArrayOf(19),
            ),
            regions = listOf(
                ToolAccessRegion(1, IntArray(20) { it }),
                ToolAccessRegion(2, intArrayOf(0, 19)),
                ToolAccessRegion(3, intArrayOf(19)),
            ),
            sampleCount = 21,
        )
        assertEquals(listOf(
            ToolAccessRegionStatus.ACCESSIBLE, // 19/20 = 95%; inclusive threshold
            ToolAccessRegionStatus.CONSTRAINED,
            ToolAccessRegionStatus.BLOCKED,
        ), result.regions.map { it.status })
    }

    @Test
    fun thresholdBoundariesAreInclusiveAndUncertaintyIsNotHidden() {
        val result = ToolAccessibilityRegionClassifier.classify(
            ToolAccessibilityRaycastResult(intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18), intArrayOf(19)),
            listOf(ToolAccessRegion(1, IntArray(20) { it })), 20,
        )
        assertEquals(ToolAccessRegionStatus.ACCESSIBLE, result.regions.single().status)
        assertEquals(0.95, result.regions.single().accessibleRatio!!, 0.0)

        val blocked = ToolAccessibilityRegionClassifier.classify(
            ToolAccessibilityRaycastResult(intArrayOf(0), IntArray(19) { it + 1 }),
            listOf(ToolAccessRegion(2, IntArray(20) { it })), 20,
        )
        assertEquals(ToolAccessRegionStatus.BLOCKED, blocked.regions.single().status)
        assertEquals(0.05, blocked.regions.single().accessibleRatio!!, 0.0)
    }

    @Test
    fun emptyOrPartiallyUnmeasuredRegionsRemainUnknown() {
        val result = ToolAccessibilityRegionClassifier.classify(
            ToolAccessibilityRaycastResult(intArrayOf(0), intArrayOf(1)),
            listOf(ToolAccessRegion(1, intArrayOf()), ToolAccessRegion(2, intArrayOf(0, 2))),
            sampleCount = 3,
        )
        assertEquals(listOf(ToolAccessRegionStatus.UNKNOWN, ToolAccessRegionStatus.UNKNOWN), result.regions.map { it.status })
        assertNull(result.regions.first().accessibleRatio)
    }

    @Test
    fun rejectsDuplicateRegionIdsAndConflictingRayResults() {
        assertEquals(ToolAccessRejection.INVALID_SAMPLE, ToolAccessibilityRegionClassifier.classify(
            ToolAccessibilityRaycastResult(intArrayOf(0), intArrayOf()),
            listOf(ToolAccessRegion(1, intArrayOf(0)), ToolAccessRegion(1, intArrayOf(0))), 1,
        ).rejected)
        assertEquals(ToolAccessRejection.INVALID_SAMPLE, ToolAccessibilityRegionClassifier.classify(
            ToolAccessibilityRaycastResult(intArrayOf(0), intArrayOf(0)), listOf(ToolAccessRegion(1, intArrayOf(0))), 1,
        ).rejected)
    }
}
