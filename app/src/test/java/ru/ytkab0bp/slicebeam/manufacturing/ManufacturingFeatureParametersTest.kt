package ru.ytkab0bp.slicebeam.manufacturing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ManufacturingFeatureParametersTest {
    private val face = ManufacturingFaceFrame(
        origin = ManufacturingPoint3d(10.0, -2.0, 3.0),
        normalX = 0.0, normalY = 0.0, normalZ = 2.0,
        tangentX = 3.0, tangentY = 0.0, tangentZ = 0.0,
    )

    @Test
    fun normalizesFaceFrameAndAcceptsBoundedThroughHoleRequest() {
        assertEquals(1.0, face.normal.z, 0.0)
        assertEquals(1.0, face.tangent.x, 0.0)
        assertEquals(
            ManufacturingFeatureKind.THROUGH_HOLE,
            ManufacturingFeatureParameters(ManufacturingFeatureKind.THROUGH_HOLE, face, diameterMm = 8.0).kind,
        )
    }

    @Test
    fun rejectsNonFiniteAndOutOfRangeCoordinatesAndDirections() {
        assertThrows(IllegalArgumentException::class.java) { ManufacturingPoint3d(Double.NaN, 0.0, 0.0) }
        assertThrows(IllegalArgumentException::class.java) { ManufacturingPoint3d(100_001.0, 0.0, 0.0) }
        assertThrows(IllegalArgumentException::class.java) {
            ManufacturingFaceFrame(ManufacturingPoint3d(0.0, 0.0, 0.0), 0.0, 0.0, 0.0, 1.0, 0.0, 0.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ManufacturingFaceFrame(ManufacturingPoint3d(0.0, 0.0, 0.0), 0.0, 0.0, 1.0, 1.0, 0.0, 1.0)
        }
    }

    @Test
    fun requiresTheDimensionsSpecificToEachFeatureType() {
        for (kind in ManufacturingFeatureKind.entries.filter { it != ManufacturingFeatureKind.THROUGH_HOLE }) {
            assertThrows("$kind requires its parameters", IllegalArgumentException::class.java) {
                ManufacturingFeatureParameters(kind, face, diameterMm = 4.0)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            ManufacturingFeatureParameters(ManufacturingFeatureKind.THROUGH_HOLE, face, 4.0, depthMm = 2.0)
        }
    }

    @Test
    fun acceptsRepresentativeValidRequestsForAllFeatureKinds() {
        val requests = listOf(
            ManufacturingFeatureParameters(ManufacturingFeatureKind.THROUGH_HOLE, face, 4.0),
            ManufacturingFeatureParameters(ManufacturingFeatureKind.BLIND_HOLE, face, 4.0, depthMm = 6.0),
            ManufacturingFeatureParameters(ManufacturingFeatureKind.COUNTERSINK, face, 4.0, 2.0, 8.0, 90.0),
            ManufacturingFeatureParameters(ManufacturingFeatureKind.COUNTERBORE, face, 4.0, 2.0, 8.0),
            ManufacturingFeatureParameters(ManufacturingFeatureKind.HEAT_SET_INSERT_POCKET, face, 4.0, 6.0, 5.0),
            ManufacturingFeatureParameters(ManufacturingFeatureKind.MOUNTING_BOSS, face, 3.0, 8.0, 8.0, wallThicknessMm = 2.0),
            ManufacturingFeatureParameters(ManufacturingFeatureKind.REINFORCEMENT_RIB, face, 4.0, 10.0, wallThicknessMm = 1.0),
        )
        assertEquals(ManufacturingFeatureKind.entries.size, requests.size)
    }

    @Test
    fun rejectsInvalidDimensionsAndInsufficientBossOrRibWalls() {
        assertThrows(IllegalArgumentException::class.java) {
            ManufacturingFeatureParameters(ManufacturingFeatureKind.THROUGH_HOLE, face, 0.19)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ManufacturingFeatureParameters(ManufacturingFeatureKind.BLIND_HOLE, face, 4.0, depthMm = Double.POSITIVE_INFINITY)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ManufacturingFeatureParameters(ManufacturingFeatureKind.COUNTERBORE, face, 4.0, 2.0, 3.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ManufacturingFeatureParameters(ManufacturingFeatureKind.MOUNTING_BOSS, face, 5.0, 8.0, 8.0, wallThicknessMm = 2.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ManufacturingFeatureParameters(ManufacturingFeatureKind.REINFORCEMENT_RIB, face, 2.0, 10.0, wallThicknessMm = 1.5)
        }
    }
}
