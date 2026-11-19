package ru.ytkab0bp.slicebeam.manufacturing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThroughHoleFaceValidatorTest {
    private val topFace = ManufacturingFaceFrame(
        origin = ManufacturingPoint3d(0.0, 0.0, 1.0),
        normalX = 0.0, normalY = 0.0, normalZ = 1.0,
        tangentX = 1.0, tangentY = 0.0, tangentZ = 0.0,
    )

    @Test
    fun acceptsHoleInsideTriangulatedPlanarFaceAndReportsRemainingWall() {
        val result = ThroughHoleFaceValidator.validate(squareFace(-10.0, 10.0), topFace, 4.0, 2.0)

        assertTrue(result.isSupported)
        assertEquals(8.0, result.minimumWallMm!!, 1e-3)
        assertNull(result.rejected)
    }

    @Test
    fun rejectsHoleOutsideFaceOrWithInsufficientRemainingWall() {
        val outside = ThroughHoleFaceValidator.validate(squareFace(-10.0, 10.0), topFace.copy(origin = ManufacturingPoint3d(11.0, 0.0, 1.0)), 4.0, 0.2)
        val tooClose = ThroughHoleFaceValidator.validate(squareFace(-10.0, 10.0), topFace.copy(origin = ManufacturingPoint3d(8.0, 0.0, 1.0)), 4.0, 1.0)

        assertEquals(ThroughHolePlacementRejection.CENTER_OUTSIDE_FACE, outside.rejected)
        assertEquals(ThroughHolePlacementRejection.INSUFFICIENT_WALL, tooClose.rejected)
        assertEquals(0.0, tooClose.minimumWallMm!!, 1e-3)
    }

    @Test
    fun rejectsNonPlanarAndMalformedInputs() {
        val nonPlanar = squareFace(-10.0, 10.0).also { it[8] = 2f }
        val malformed = floatArrayOf(0f, 0f, 0f)

        assertTrue(ThroughHoleFaceValidator.validate(nonPlanar, topFace, 4.0, 1.0).rejected != null)
        assertEquals(ThroughHolePlacementRejection.INVALID_MESH,
            ThroughHoleFaceValidator.validate(malformed, topFace, 4.0, 1.0).rejected)
        assertNotNull(ThroughHoleFaceValidator.validate(squareFace(-10.0, 10.0), topFace, 4.0, 1.0).minimumWallMm)
    }

    @Test
    fun rejectsUnclosedTriangulatedFaceBoundary() {
        val nonManifold = floatArrayOf(
            -10f, -10f, 1f, 10f, -10f, 1f, 0f, 10f, 1f,
            10f, -10f, 1f, -10f, -10f, 1f, 0f, 0f, 1f,
            -10f, -10f, 1f, 10f, -10f, 1f, 0f, -20f, 1f,
        )
        assertEquals(ThroughHolePlacementRejection.INVALID_FACE_BOUNDARY,
            ThroughHoleFaceValidator.validate(nonManifold, topFace, 2.0, 0.2).rejected)
    }

    @Test
    fun rejectsCenterInsideExistingOpeningInPlanarFacePatch() {
        val ring = planarRing(-10.0, 10.0, -2.0, 2.0)

        val result = ThroughHoleFaceValidator.validate(ring, topFace, 1.0, 0.2)

        assertEquals(ThroughHolePlacementRejection.CENTER_OUTSIDE_FACE, result.rejected)
    }

    private fun squareFace(min: Double, max: Double): FloatArray {
        val a = floatArrayOf(min.toFloat(), min.toFloat(), 1f)
        val b = floatArrayOf(max.toFloat(), min.toFloat(), 1f)
        val c = floatArrayOf(max.toFloat(), max.toFloat(), 1f)
        val d = floatArrayOf(min.toFloat(), max.toFloat(), 1f)
        return (a + b + c + a + c + d)
    }

    private fun planarRing(outerMin: Double, outerMax: Double, innerMin: Double, innerMax: Double): FloatArray {
        val out = ArrayList<Float>()
        fun p(x: Double, y: Double) = floatArrayOf(x.toFloat(), y.toFloat(), 1f)
        fun quad(a: FloatArray, b: FloatArray, c: FloatArray, d: FloatArray) {
            out.addAll((a + b + c + a + c + d).toList())
        }
        val xs = listOf(outerMin, innerMin, innerMax, outerMax)
        val ys = listOf(outerMin, innerMin, innerMax, outerMax)
        for (yi in 0..2) for (xi in 0..2) {
            if (yi == 1 && xi == 1) continue
            quad(p(xs[xi], ys[yi]), p(xs[xi + 1], ys[yi]), p(xs[xi + 1], ys[yi + 1]), p(xs[xi], ys[yi + 1]))
        }
        return out.toFloatArray()
    }
}
