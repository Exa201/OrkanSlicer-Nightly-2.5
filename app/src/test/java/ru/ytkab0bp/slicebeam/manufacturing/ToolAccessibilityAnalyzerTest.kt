package ru.ytkab0bp.slicebeam.manufacturing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolAccessibilityAnalyzerTest {
    private val request = ToolAccessibilityRequest(
        shape = AccessibilityToolShape.FLAT_END_MILL,
        radiusMm = 1.0,
        reachMm = 20.0,
        approachX = 0.0,
        approachY = 0.0,
        approachZ = 1.0,
        sampleSpacingMm = 0.3,
    )

    @Test
    fun endToEndAnalysisSamplesClassifiesAndDoesNotMutateMesh() {
        val mesh = doubleArrayOf(-1.0, -1.0, 0.0, 1.0, -1.0, 0.0, 0.0, 1.0, 0.0)
        val before = mesh.copyOf()
        val result = ToolAccessibilityAnalyzer.analyze(mesh, request)

        assertTrue(result.isAvailable)
        assertTrue(result.samples.isNotEmpty())
        assertEquals(result.samples.size, result.raycast!!.accessibleSampleIndices.size)
        assertEquals(ToolAccessRegionStatus.ACCESSIBLE, result.classification!!.regions.single().status)
        assertTrue(mesh.contentEquals(before))
    }

    @Test
    fun occludedFaceIsBlockedAndElevatedFaceRemainsAccessible() {
        val lower = doubleArrayOf(-1.0, -1.0, 0.0, 1.0, -1.0, 0.0, 0.0, 1.0, 0.0)
        val upper = doubleArrayOf(-1.0, -1.0, 1.0, 1.0, -1.0, 1.0, 0.0, 1.0, 1.0)
        val result = ToolAccessibilityAnalyzer.analyze(lower + upper, request)

        assertTrue(result.isAvailable)
        val byId = result.classification!!.regions.associateBy { it.id }
        assertEquals(ToolAccessRegionStatus.BLOCKED, byId[0]!!.status)
        assertEquals(ToolAccessRegionStatus.ACCESSIBLE, byId[1]!!.status)
    }

    @Test
    fun invalidOversizedAndDegenerateInputsReturnExplicitFailures() {
        val invalid = ToolAccessibilityAnalyzer.analyze(doubleArrayOf(0.0, Double.NaN), request)
        assertEquals(ToolAccessibilityAnalysisFailure.INVALID_INPUT, invalid.failure)
        val tooLarge = ToolAccessibilityAnalyzer.analyze(DoubleArray(4097 * 9), request)
        assertEquals(ToolAccessibilityAnalysisFailure.TOO_LARGE, tooLarge.failure)
        val degenerate = ToolAccessibilityAnalyzer.analyze(
            doubleArrayOf(0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 2.0, 2.0, 2.0), request,
        )
        assertEquals(ToolAccessibilityAnalysisFailure.NO_SURFACE, degenerate.failure)
    }

    @Test
    fun boundedNativeFloatMeshUsesSameAnalysisAndRejectsLargeDataBeforeConversion() {
        val mesh = floatArrayOf(-1f, -1f, 0f, 1f, -1f, 0f, 0f, 1f, 0f)
        val result = ToolAccessibilityAnalyzer.analyze(mesh, request)
        assertTrue(result.isAvailable)
        assertEquals(ToolAccessibilityAnalysisFailure.TOO_LARGE,
            ToolAccessibilityAnalyzer.analyze(FloatArray(4097 * 9), request).failure)
        assertEquals(ToolAccessibilityAnalysisFailure.INVALID_INPUT,
            ToolAccessibilityAnalyzer.analyze(floatArrayOf(0f, Float.NaN, 0f, 1f, 0f, 0f, 0f, 1f, 0f), request).failure)
    }

    @Test
    fun sixAxisCandidateScanReusesBoundedSamplesAndReportsItsAssumptions() {
        val cube = doubleArrayOf(
            // x = -1 and x = 1
            -1.0, -1.0, -1.0, -1.0, 1.0, -1.0, -1.0, 1.0, 1.0,
            -1.0, -1.0, -1.0, -1.0, 1.0, 1.0, -1.0, -1.0, 1.0,
            1.0, -1.0, -1.0, 1.0, 1.0, 1.0, 1.0, 1.0, -1.0,
            1.0, -1.0, -1.0, 1.0, -1.0, 1.0, 1.0, 1.0, 1.0,
            // y = -1 and y = 1
            -1.0, -1.0, -1.0, 1.0, -1.0, 1.0, 1.0, -1.0, -1.0,
            -1.0, -1.0, -1.0, -1.0, -1.0, 1.0, 1.0, -1.0, 1.0,
            -1.0, 1.0, -1.0, 1.0, 1.0, -1.0, 1.0, 1.0, 1.0,
            -1.0, 1.0, -1.0, 1.0, 1.0, 1.0, -1.0, 1.0, 1.0,
            // z = -1 and z = 1
            -1.0, -1.0, -1.0, 1.0, -1.0, -1.0, 1.0, 1.0, -1.0,
            -1.0, -1.0, -1.0, 1.0, 1.0, -1.0, -1.0, 1.0, -1.0,
            -1.0, -1.0, 1.0, 1.0, 1.0, 1.0, 1.0, -1.0, 1.0,
            -1.0, -1.0, 1.0, -1.0, 1.0, 1.0, 1.0, 1.0, 1.0,
        )
        val result = ToolAccessibilityAnalyzer.analyzeAllApproaches(cube, request)

        assertTrue(result.isAvailable)
        assertEquals(6, result.directionalRaycasts.size)
        assertEquals(result.samples.size, result.pocketCandidates!!.sampleStatuses.size)
        assertEquals(6, result.pocketCandidates.testedDirectionCount)
        assertEquals(4, result.pocketCandidates.assumptions.size)
        assertTrue(result.pocketCandidates.sampleStatuses.all {
            it == PocketSampleStatus.VISIBLE_FROM_TESTED_DIRECTION
        })
        assertTrue(result.classification!!.regions.isNotEmpty())
        assertEquals(ToolAccessibilityAnalysisFailure.TOO_LARGE,
            ToolAccessibilityAnalyzer.analyzeAllApproaches(DoubleArray(4097 * 9), request).failure)
    }
}
