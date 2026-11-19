package ru.ytkab0bp.slicebeam.manufacturing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class CncCompensatedMeshPreviewBuilderTest {
    @Test
    fun previewChangesOnlySampledCylinderAndPreservesSourceArray() {
        val source = cylinder(5.0)
        val original = source.copyOf()
        val candidate = CylindricalFeatureDetector.detect(source).single()
        val unrelatedTriangle = floatArrayOf(100f, 100f, 100f, 101f, 100f, 100f, 100f, 101f, 100f)
        val sourceWithUnrelatedSurface = source + unrelatedTriangle

        val result = CncCompensatedMeshPreviewBuilder.build(sourceWithUnrelatedSurface, candidate, 10.2)

        assertNull(result.rejected)
        assertTrue(result.isReady)
        assertTrue(result.changedVertexCount > 0)
        assertNotSame(source, result.triangles)
        assertTrue("Source mesh preview input must remain byte-for-byte unchanged", source.contentEquals(original))
        assertTrue("Only affected faces should be emitted for the renderer overlay", result.triangles.size < sourceWithUnrelatedSurface.size)
        assertFalse("Unchanged distant faces must not be overdrawn", result.triangles.toList().contains(100f))
        val visibleRadius = (result.triangles.take(6 * 9).chunked(3).map { point ->
            sqrt(point[0] * point[0] + point[1] * point[1])
        }).average()
        assertTrue("The preview should move the cylindrical surface toward the 10.2 mm target", visibleRadius > 5.0)
    }

    @Test
    fun rejectsLowConfidenceInvalidTargetAndInvalidMesh() {
        val source = cylinder(5.0)
        val candidate = CylindricalFeatureDetector.detect(source).single()
        val lowConfidence = candidate.copy(confidence = 0.69)

        assertEquals(CncPreviewRejection.LOW_CONFIDENCE, CncCompensatedMeshPreviewBuilder.build(source, lowConfidence, 10.0).rejected)
        assertEquals(CncPreviewRejection.INVALID_TARGET, CncCompensatedMeshPreviewBuilder.build(source, candidate, Double.NaN).rejected)
        assertEquals(CncPreviewRejection.INVALID_MESH, CncCompensatedMeshPreviewBuilder.build(floatArrayOf(0f), candidate, 10.0).rejected)
    }

    @Test
    fun rejectsPreviewMeshesAboveBoundAndSurfacesWithoutMatchingRadius() {
        val source = cylinder(5.0)
        val candidate = CylindricalFeatureDetector.detect(source).single()
        val oversized = FloatArray((4096 + 1) * 9)
        val unrelated = FloatArray(9 * 32) { index -> if (index % 3 == 0) 100f + index else 0f }

        assertEquals(CncPreviewRejection.TOO_LARGE, CncCompensatedMeshPreviewBuilder.build(oversized, candidate, 10.0).rejected)
        assertEquals(CncPreviewRejection.NO_MATCHING_SURFACE, CncCompensatedMeshPreviewBuilder.build(unrelated, candidate, 10.0).rejected)
    }

    @Test
    fun rejectedResultContainsNoMeshAndNeverClaimsReady() {
        val result = CncCompensatedMeshPreviewBuilder.build(floatArrayOf(), dummyCandidate(), 10.0)
        assertFalse(result.isReady)
        assertEquals(0, result.triangles.size)
        val source = cylinder(5.0)
        val validCandidate = CylindricalFeatureDetector.detect(source).single()
        assertTrue(CncCompensatedMeshPreviewBuilder.build(source, validCandidate, 10.0).isReady)
    }

    private fun dummyCandidate() = CylindricalFeatureCandidate(
        kind = CylindricalFeatureKind.EXTERNAL_SHAFT,
        centerMm = doubleArrayOf(0.0, 0.0, 0.0),
        axisDirection = doubleArrayOf(0.0, 0.0, 1.0),
        radiusMm = 5.0,
        axialLengthMm = 10.0,
        axialStartMm = 0.0,
        axialEndMm = 10.0,
        confidence = 0.5,
        sampledTriangleCount = 32,
    )

    private fun cylinder(radius: Double): FloatArray {
        val output = ArrayList<Float>()
        val segments = 32
        fun point(index: Int, z: Double) = doubleArrayOf(
            radius * cos(2.0 * PI * (index % segments) / segments),
            radius * sin(2.0 * PI * (index % segments) / segments),
            z,
        )
        for (index in 0 until segments) {
            val a = point(index, 0.0); val b = point(index + 1, 0.0)
            val c = point(index + 1, 10.0); val d = point(index, 10.0)
            listOf(a, b, c, a, c, d).forEach { point -> point.forEach { output += it.toFloat() } }
        }
        return output.toFloatArray()
    }
}
