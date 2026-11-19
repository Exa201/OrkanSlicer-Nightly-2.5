package ru.ytkab0bp.slicebeam.manufacturing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnclosedPocketCandidateAnalyzerTest {
    @Test
    fun reportsOnlySamplesOccludedFromAllSixDirectionsAsCandidates() {
        val directions = List(6) { direction ->
            if (direction == 0) ToolAccessibilityRaycastResult(intArrayOf(0), intArrayOf(1))
            else ToolAccessibilityRaycastResult(intArrayOf(), intArrayOf(0, 1))
        }
        val report = EnclosedPocketCandidateAnalyzer.analyze(directions, 2)
        assertTrue(report.isAvailable)
        assertEquals(listOf(
            PocketSampleStatus.VISIBLE_FROM_TESTED_DIRECTION,
            PocketSampleStatus.OCCLUDED_IN_TESTED_DIRECTIONS,
        ), report.sampleStatuses)
        assertEquals(6, report.testedDirectionCount)
        assertTrue(report.assumptions.any { it.contains("not proof") })
    }

    @Test
    fun rejectsIncompleteOrInvalidDirectionalEvidence() {
        val allVisible = ToolAccessibilityRaycastResult(intArrayOf(0), intArrayOf())
        assertFalse(EnclosedPocketCandidateAnalyzer.analyze(List(5) { allVisible }, 1).isAvailable)
        assertFalse(EnclosedPocketCandidateAnalyzer.analyze(List(6) { allVisible }, 0).isAvailable)
        val incomplete = List(6) { ToolAccessibilityRaycastResult(intArrayOf(), intArrayOf()) }
        assertEquals(ToolAccessRejection.INVALID_SAMPLE,
            EnclosedPocketCandidateAnalyzer.analyze(incomplete, 1).rejected)
    }

    @Test
    fun actualAxisRaysDistinguishSixBlockedApproachesFromAnOpenAxis() {
        val target = triangle(
            -0.5, -0.5, 0.0,
            0.5, -0.5, 0.0,
            0.0, 0.5, 0.0,
        )
        val sample = ToolSurfaceSample(0, 0.0, -1.0 / 6.0, 0.0)
        val blockers = listOf(
            triangle(1.0, -10.0, -10.0, 1.0, 10.0, -10.0, 1.0, 0.0, 10.0),
            triangle(-1.0, -10.0, -10.0, -1.0, 10.0, -10.0, -1.0, 0.0, 10.0),
            triangle(-10.0, 1.0, -10.0, 10.0, 1.0, -10.0, 0.0, 1.0, 10.0),
            triangle(-10.0, -1.0, -10.0, 10.0, -1.0, -10.0, 0.0, -1.0, 10.0),
            triangle(-10.0, -10.0, 1.0, 10.0, -10.0, 1.0, 0.0, 10.0, 1.0),
            triangle(-10.0, -10.0, -1.0, 10.0, -10.0, -1.0, 0.0, 10.0, -1.0),
        )
        val directions = listOf(
            doubleArrayOf(1.0, 0.0, 0.0), doubleArrayOf(-1.0, 0.0, 0.0),
            doubleArrayOf(0.0, 1.0, 0.0), doubleArrayOf(0.0, -1.0, 0.0),
            doubleArrayOf(0.0, 0.0, 1.0), doubleArrayOf(0.0, 0.0, -1.0),
        )

        fun report(blockingTriangles: List<DoubleArray>) = EnclosedPocketCandidateAnalyzer.analyze(
            directions.map { direction ->
                ToolAccessibilityRaycaster.analyze(
                    target + blockingTriangles.fold(doubleArrayOf()) { acc, tri -> acc + tri },
                    listOf(sample),
                    ToolAccessibilityRequest(
                        AccessibilityToolShape.FLAT_END_MILL,
                        radiusMm = 0.1,
                        reachMm = 5.0,
                        approachX = direction[0], approachY = direction[1], approachZ = direction[2],
                        sampleSpacingMm = 1.0,
                    ),
                )
            },
            sampleCount = 1,
        )

        val allBlocked = report(blockers)
        assertTrue(allBlocked.isAvailable)
        assertEquals(PocketSampleStatus.OCCLUDED_IN_TESTED_DIRECTIONS, allBlocked.sampleStatuses.single())

        val oneOpenAxis = report(blockers.dropLast(1))
        assertTrue(oneOpenAxis.isAvailable)
        assertEquals(PocketSampleStatus.VISIBLE_FROM_TESTED_DIRECTION, oneOpenAxis.sampleStatuses.single())
    }

    private fun triangle(vararg coordinates: Double) = coordinates
}
