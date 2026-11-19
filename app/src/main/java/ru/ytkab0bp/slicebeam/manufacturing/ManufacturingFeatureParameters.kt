package ru.ytkab0bp.slicebeam.manufacturing

import kotlin.math.sqrt

/** Supported manufacturing feature intents. These are parameters only, not generated geometry. */
enum class ManufacturingFeatureKind {
    THROUGH_HOLE,
    BLIND_HOLE,
    COUNTERSINK,
    COUNTERBORE,
    HEAT_SET_INSERT_POCKET,
    MOUNTING_BOSS,
    REINFORCEMENT_RIB,
}

/** Finite 3D point in model coordinates, in millimeters. */
data class ManufacturingPoint3d(val xMm: Double, val yMm: Double, val zMm: Double) {
    init {
        require(listOf(xMm, yMm, zMm).all(Double::isFinite)) { "Feature position must be finite" }
        require(listOf(xMm, yMm, zMm).all { it in -100_000.0..100_000.0 }) {
            "Feature position is outside the supported model coordinate range"
        }
    }
}

data class ManufacturingDirection3d(val x: Double, val y: Double, val z: Double)

/** Normalized model-space face frame; tangent fixes the rotation of the feature on the face. */
data class ManufacturingFaceFrame(
    val origin: ManufacturingPoint3d,
    val normalX: Double,
    val normalY: Double,
    val normalZ: Double,
    val tangentX: Double,
    val tangentY: Double,
    val tangentZ: Double,
) {
    val normal: ManufacturingDirection3d = normalized(normalX, normalY, normalZ, "Face normal")
    val tangent: ManufacturingDirection3d = normalized(tangentX, tangentY, tangentZ, "Face tangent")

    init {
        val dot = normal.x * tangent.x + normal.y * tangent.y + normal.z * tangent.z
        require(kotlin.math.abs(dot) <= 1e-4) { "Face tangent must be perpendicular to the face normal" }
    }

    private fun normalized(x: Double, y: Double, z: Double, label: String): ManufacturingDirection3d {
        require(listOf(x, y, z).all(Double::isFinite)) { "$label must be finite" }
        val length = sqrt(x * x + y * y + z * z)
        require(length.isFinite() && length > 1e-8) { "$label must not be zero" }
        return ManufacturingDirection3d(x / length, y / length, z / length)
    }
}

/**
 * UI-independent, bounded request for a future geometry operation. Construction rejects
 * malformed dimensions before they can reach native code; this type does not modify a mesh.
 */
data class ManufacturingFeatureParameters(
    val kind: ManufacturingFeatureKind,
    val face: ManufacturingFaceFrame,
    val diameterMm: Double,
    val depthMm: Double? = null,
    val outerDiameterMm: Double? = null,
    val angleDegrees: Double? = null,
    val wallThicknessMm: Double? = null,
) {
    init {
        require(diameterMm.isFinite() && diameterMm in 0.2..500.0) {
            "Feature diameter must be between 0.2 and 500 mm"
        }
        depthMm?.let { require(it.isFinite() && it in 0.1..100_000.0) { "Feature depth must be positive and finite" } }
        outerDiameterMm?.let { require(it.isFinite() && it in 0.2..500.0) { "Outer diameter must be between 0.2 and 500 mm" } }
        angleDegrees?.let { require(it.isFinite() && it in 30.0..150.0) { "Feature angle must be between 30 and 150 degrees" } }
        wallThicknessMm?.let { require(it.isFinite() && it in 0.2..100.0) { "Wall thickness must be between 0.2 and 100 mm" } }

        when (kind) {
            ManufacturingFeatureKind.THROUGH_HOLE -> require(depthMm == null && outerDiameterMm == null && angleDegrees == null)
            ManufacturingFeatureKind.BLIND_HOLE -> require(depthMm != null && outerDiameterMm == null && angleDegrees == null)
            ManufacturingFeatureKind.COUNTERSINK -> require(depthMm != null && outerDiameterMm != null && angleDegrees != null)
            ManufacturingFeatureKind.COUNTERBORE -> require(depthMm != null && outerDiameterMm != null && angleDegrees == null)
            ManufacturingFeatureKind.HEAT_SET_INSERT_POCKET -> require(depthMm != null && outerDiameterMm != null && angleDegrees == null)
            ManufacturingFeatureKind.MOUNTING_BOSS -> require(depthMm != null && wallThicknessMm != null && outerDiameterMm != null)
            ManufacturingFeatureKind.REINFORCEMENT_RIB -> require(depthMm != null && wallThicknessMm != null && outerDiameterMm == null)
        }

        outerDiameterMm?.let { outer ->
            require(outer > diameterMm) { "Outer diameter must be larger than the feature diameter" }
        }
        if (kind == ManufacturingFeatureKind.MOUNTING_BOSS) {
            require(outerDiameterMm!! - diameterMm >= 2.0 * wallThicknessMm!!) {
                "Boss wall thickness does not fit inside the outer diameter"
            }
        }
        if (kind == ManufacturingFeatureKind.REINFORCEMENT_RIB) {
            require(diameterMm >= 2.0 * wallThicknessMm!!) { "Rib width must be at least twice its wall thickness" }
        }
    }
}
