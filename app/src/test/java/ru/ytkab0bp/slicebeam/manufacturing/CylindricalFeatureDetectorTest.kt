package ru.ytkab0bp.slicebeam.manufacturing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class CylindricalFeatureDetectorTest {
    @Test
    fun detectsAnOrientedExternalCylindricalPatchAndItsDimensions() {
        val candidates = CylindricalFeatureDetector.detect(cylinder(5.0, reverseWinding = false))
        val result = candidates.single()

        assertEquals(CylindricalFeatureKind.EXTERNAL_SHAFT, result.kind)
        assertEquals(5.0, result.radiusMm, 0.2)
        assertEquals(10.0, result.axialLengthMm, 0.1)
        assertEquals(0.0, result.axialStartMm, 0.1)
        assertEquals(10.0, result.axialEndMm, 0.1)
        assertEquals(0.0, result.centerMm[0], 0.1)
        assertEquals(0.0, result.centerMm[1], 0.1)
        assertTrue(result.confidence >= 0.8)
    }

    @Test
    fun detectsAnOrientedInternalBoreWithoutChangingInput() {
        val input = cylinder(3.0, reverseWinding = true)
        val before = input.copyOf()
        val result = CylindricalFeatureDetector.detect(input).single()

        assertEquals(CylindricalFeatureKind.INTERNAL_BORE, result.kind)
        assertEquals(3.0, result.radiusMm, 0.2)
        assertTrue(input.contentEquals(before))
    }

    @Test
    fun findsABorePatchInsideABoxLikeHostInsteadOfReportingTheHostCorners() {
        val hostWithBoreWall = cube() + cylinder(3.0, reverseWinding = true)
        val result = CylindricalFeatureDetector.detect(hostWithBoreWall)

        assertEquals(1, result.size)
        assertEquals(CylindricalFeatureKind.INTERNAL_BORE, result.single().kind)
        assertEquals(3.0, result.single().radiusMm, 0.2)
    }

    @Test
    fun findsABoreWhenItsSurfaceIsOnlyAPartOfTheModelMesh() {
        val unrelatedFlatSurfaces = ArrayList<Float>()
        repeat(120) {
            listOf(30f to 0f, 31f to 0f, 30f to 1f).forEach { (x, y) ->
                unrelatedFlatSurfaces += x
                unrelatedFlatSurfaces += y
                unrelatedFlatSurfaces += 0f
            }
        }
        val complexHost = cube() + cylinder(3.0, reverseWinding = true) + unrelatedFlatSurfaces
        val candidates = CylindricalFeatureDetector.detect(complexHost)

        assertEquals(1, candidates.size)
        assertEquals(CylindricalFeatureKind.INTERNAL_BORE, candidates.single().kind)
        assertEquals(3.0, candidates.single().radiusMm, 0.2)
        assertTrue("A partial-mesh candidate still reports confidence", candidates.single().confidence >= 0.60)
    }

    @Test
    fun rejectsBoxLikeMeshAndUnboundedOrMalformedData() {
        assertTrue(CylindricalFeatureDetector.detect(cube()).isEmpty())
        assertTrue(CylindricalFeatureDetector.detect(FloatArray(9)).isEmpty())
        assertTrue(CylindricalFeatureDetector.detect(FloatArray(257 * 9)).isEmpty())
        assertTrue(CylindricalFeatureDetector.detect(floatArrayOf(Float.NaN, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f)).isEmpty())
    }

    private fun cylinder(radius: Double, reverseWinding: Boolean): FloatArray {
        val output = ArrayList<Float>()
        val segments = 32
        fun point(i: Int, z: Double): DoubleArray {
            val angle = 2.0 * PI * (i % segments) / segments
            return doubleArrayOf(radius * cos(angle), radius * sin(angle), z)
        }
        fun add(a: DoubleArray, b: DoubleArray, c: DoubleArray) {
            val ordered = if (reverseWinding) listOf(a, c, b) else listOf(a, b, c)
            ordered.forEach { p -> p.forEach { output += it.toFloat() } }
        }
        for (i in 0 until segments) {
            val bottomA = point(i, 0.0); val bottomB = point(i + 1, 0.0)
            val topA = point(i, 10.0); val topB = point(i + 1, 10.0)
            add(bottomA, bottomB, topB)
            add(bottomA, topB, topA)
        }
        return output.toFloatArray()
    }

    private fun cube(): FloatArray {
        val output = ArrayList<Float>()
        val v = arrayOf(
            doubleArrayOf(0.0,0.0,0.0), doubleArrayOf(1.0,0.0,0.0), doubleArrayOf(1.0,1.0,0.0), doubleArrayOf(0.0,1.0,0.0),
            doubleArrayOf(0.0,0.0,1.0), doubleArrayOf(1.0,0.0,1.0), doubleArrayOf(1.0,1.0,1.0), doubleArrayOf(0.0,1.0,1.0),
        )
        val faces = arrayOf(
            intArrayOf(0,1,2,3), intArrayOf(4,7,6,5), intArrayOf(0,4,5,1),
            intArrayOf(1,5,6,2), intArrayOf(2,6,7,3), intArrayOf(3,7,4,0),
        )
        for (face in faces) for (tri in arrayOf(intArrayOf(face[0],face[1],face[2]), intArrayOf(face[0],face[2],face[3])))
            for (index in tri) for (coordinate in v[index]) output += coordinate.toFloat()
        return output.toFloatArray()
    }
}
