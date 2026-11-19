package ru.ytkab0bp.slicebeam.manufacturing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshTopologyAuditTest {
    @Test
    fun closedIndexedMeshIsEligibleForPrecisionAnalysis() {
        val report = MeshTopologyAudit.fromNative(intArrayOf(1, 1, 1, 1, 0, 0, 0, 12))
        assertTrue(report!!.hasClosedManifoldTopology)
    }

    @Test
    fun openOrNonManifoldMeshIsNotEligible() {
        assertFalse(MeshTopologyAudit.fromNative(intArrayOf(1, 1, 1, 0, 3, 0, 0, 3))!!.hasClosedManifoldTopology)
        assertFalse(MeshTopologyAudit.fromNative(intArrayOf(1, 1, 1, 0, 0, 1, 0, 4))!!.hasClosedManifoldTopology)
    }

    @Test
    fun disconnectedClosedShellsAreReportedButRemainEligible() {
        val report = MeshTopologyAudit.fromNative(intArrayOf(1, 1, 2, 2, 0, 0, 0, 24))
        assertTrue(report!!.hasClosedManifoldTopology)
        assertTrue(report.connectedComponents == 2)
    }

    @Test
    fun oversizedMeshIsReportedAsUnavailableInsteadOfInvalid() {
        val report = MeshTopologyAudit.fromNative(intArrayOf(0, 0, 0, 0, 0, 0, 0, 500_001))!!
        assertFalse(report.analysisAvailable)
        assertFalse(report.hasClosedManifoldTopology)
        assertTrue(report.triangleCount == 500_001)
    }

    @Test
    fun malformedNativeResponsesAreRejected() {
        assertNull(MeshTopologyAudit.fromNative(intArrayOf(1, 1, 0)))
        assertNull(MeshTopologyAudit.fromNative(intArrayOf(1, 1, -1, 0, 0, 0, 0, 1)))
        assertNull(MeshTopologyAudit.fromNative(intArrayOf(2, 1, 0, 0, 0, 0, 0, 1)))
    }
}
