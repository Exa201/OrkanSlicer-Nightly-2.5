package ru.ytkab0bp.slicebeam.manufacturing

import kotlin.math.abs
import kotlin.math.sqrt

enum class CncPreviewRejection {
    INVALID_MESH,
    TOO_LARGE,
    INVALID_FEATURE,
    LOW_CONFIDENCE,
    INVALID_TARGET,
    NO_MATCHING_SURFACE,
    DEGENERATE_RESULT,
}

/**
 * A transient, sampled visualization mesh. It is never accepted by slicing or export APIs and
 * must not be presented as a validated printable repair of the complete source mesh.
 */
data class CncCompensatedMeshPreview(
    val triangles: FloatArray,
    val changedVertexCount: Int,
    val rejected: CncPreviewRejection? = null,
) {
    val isReady: Boolean get() = rejected == null
}

/** Builds a bounded visualization-only radial preview without changing the caller's mesh. */
object CncCompensatedMeshPreviewBuilder {
    private const val COMPONENTS_PER_TRIANGLE = 9
    private const val MAX_TRIANGLES = 4096
    private const val MIN_CONFIDENCE = 0.70

    fun build(
        sourceTriangles: FloatArray,
        candidate: CylindricalFeatureCandidate,
        targetDiameterMm: Double,
    ): CncCompensatedMeshPreview {
        if (sourceTriangles.isEmpty() || sourceTriangles.size % COMPONENTS_PER_TRIANGLE != 0 ||
            sourceTriangles.any { !it.isFinite() }
        ) return rejected(CncPreviewRejection.INVALID_MESH)
        if (sourceTriangles.size / COMPONENTS_PER_TRIANGLE > MAX_TRIANGLES)
            return rejected(CncPreviewRejection.TOO_LARGE)
        if (candidate.centerMm.size != 3 || candidate.axisDirection.size != 3 ||
            candidate.centerMm.any { !it.isFinite() } || candidate.axisDirection.any { !it.isFinite() } ||
            !candidate.radiusMm.isFinite() || candidate.radiusMm <= 0.05 ||
            !candidate.axialStartMm.isFinite() || !candidate.axialEndMm.isFinite() ||
            candidate.axialEndMm - candidate.axialStartMm <= 0.05 ||
            !candidate.axialLengthMm.isFinite() || candidate.axialLengthMm <= 0.05
        ) return rejected(CncPreviewRejection.INVALID_FEATURE)
        if (!candidate.confidence.isFinite() || candidate.confidence !in MIN_CONFIDENCE..1.0)
            return rejected(CncPreviewRejection.LOW_CONFIDENCE)
        if (!targetDiameterMm.isFinite() || targetDiameterMm !in 0.1..1000.0 ||
            abs(targetDiameterMm - 2.0 * candidate.radiusMm) > 2.0 * candidate.radiusMm
        ) return rejected(CncPreviewRejection.INVALID_TARGET)

        val axisLength = sqrt(candidate.axisDirection.sumOf { it * it })
        if (!axisLength.isFinite() || axisLength <= 1e-8) return rejected(CncPreviewRejection.INVALID_FEATURE)
        val axis = DoubleArray(3) { candidate.axisDirection[it] / axisLength }
        val output = sourceTriangles.copyOf()
        val changed = BooleanArray(sourceTriangles.size / 3)
        val center = candidate.centerMm
        val targetRadius = targetDiameterMm / 2.0
        val radialBand = (candidate.radiusMm * 0.20).coerceAtLeast(0.12)
        val axialBlend = (candidate.radiusMm * 0.15).coerceAtLeast(0.15)
        val axialStart = candidate.axialStartMm
        val axialEnd = candidate.axialEndMm
        var changedVertices = 0

        for (offset in output.indices step 3) {
            val dx = output[offset].toDouble() - center[0]
            val dy = output[offset + 1].toDouble() - center[1]
            val dz = output[offset + 2].toDouble() - center[2]
            val along = dx * axis[0] + dy * axis[1] + dz * axis[2]
            val axialWeight = when {
                along < axialStart - axialBlend || along > axialEnd + axialBlend -> 0.0
                along < axialStart -> ((along - (axialStart - axialBlend)) / axialBlend).coerceIn(0.0, 1.0)
                along > axialEnd -> (((axialEnd + axialBlend) - along) / axialBlend).coerceIn(0.0, 1.0)
                else -> 1.0
            }
            if (axialWeight <= 0.0) continue
            val rx = dx - along * axis[0]
            val ry = dy - along * axis[1]
            val rz = dz - along * axis[2]
            val radius = sqrt(rx * rx + ry * ry + rz * rz)
            if (!radius.isFinite() || radius <= 1e-8) continue
            val radialWeight = (1.0 - abs(radius - candidate.radiusMm) / radialBand).coerceIn(0.0, 1.0)
            val weight = radialWeight * axialWeight
            if (weight <= 1e-4) continue
            val adjustedRadius = radius + (targetRadius - candidate.radiusMm) * weight
            if (!adjustedRadius.isFinite() || adjustedRadius <= 0.0) return rejected(CncPreviewRejection.INVALID_TARGET)
            val scale = adjustedRadius / radius
            val nx = (center[0] + along * axis[0] + rx * scale).toFloat()
            val ny = (center[1] + along * axis[1] + ry * scale).toFloat()
            val nz = (center[2] + along * axis[2] + rz * scale).toFloat()
            if (!nx.isFinite() || !ny.isFinite() || !nz.isFinite()) return rejected(CncPreviewRejection.INVALID_TARGET)
            output[offset] = nx
            output[offset + 1] = ny
            output[offset + 2] = nz
            changed[offset / 3] = true
            changedVertices++
        }

        if (changedVertices == 0) return rejected(CncPreviewRejection.NO_MATCHING_SURFACE)
        val affected = ArrayList<Float>()
        for (offset in output.indices step COMPONENTS_PER_TRIANGLE) {
            val ax = output[offset].toDouble(); val ay = output[offset + 1].toDouble(); val az = output[offset + 2].toDouble()
            val abx = output[offset + 3] - ax; val aby = output[offset + 4] - ay; val abz = output[offset + 5] - az
            val acx = output[offset + 6] - ax; val acy = output[offset + 7] - ay; val acz = output[offset + 8] - az
            val cx = aby * acz - abz * acy
            val cy = abz * acx - abx * acz
            val cz = abx * acy - aby * acx
            if (!cx.isFinite() || !cy.isFinite() || !cz.isFinite() || cx * cx + cy * cy + cz * cz <= 1e-16)
                return rejected(CncPreviewRejection.DEGENERATE_RESULT)
            val firstVertex = offset / 3
            if (changed[firstVertex] || changed[firstVertex + 1] || changed[firstVertex + 2]) {
                for (component in 0 until COMPONENTS_PER_TRIANGLE) affected += output[offset + component]
            }
        }
        if (affected.isEmpty()) return rejected(CncPreviewRejection.NO_MATCHING_SURFACE)
        return CncCompensatedMeshPreview(affected.toFloatArray(), changedVertices)
    }

    private fun rejected(reason: CncPreviewRejection) = CncCompensatedMeshPreview(FloatArray(0), 0, reason)
}
