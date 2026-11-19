package ru.ytkab0bp.slicebeam.manufacturing

import kotlin.math.abs

data class ToolSurfaceSample(
    val targetTriangleIndex: Int,
    val xMm: Double,
    val yMm: Double,
    val zMm: Double,
)

enum class ToolAccessRejection { INVALID_MESH, TOO_LARGE, INVALID_SAMPLE }

data class ToolAccessibilityRaycastResult(
    val accessibleSampleIndices: IntArray,
    val blockedSampleIndices: IntArray,
    val rejected: ToolAccessRejection? = null,
) {
    val isAvailable: Boolean get() = rejected == null
}

/** Bounded centerline ray analysis. It does not model cutter-body or machine collisions. */
object ToolAccessibilityRaycaster {
    private const val VALUES_PER_TRIANGLE = 9
    private const val MAX_TRIANGLES = 4096
    private const val MAX_SAMPLES = 1024
    private const val EPSILON = 1e-8
    private const val ENDPOINT_EPSILON_MM = 1e-5

    /**
     * For every supplied point, cast a ray from outside the part along the selected tool approach.
     * A sample is accessible when no other triangle intersects the open ray segment before its target.
     * Caller supplies samples in model coordinates, typically from a surface sampler.
     */
    fun analyze(
        triangleVerticesMm: DoubleArray,
        samples: List<ToolSurfaceSample>,
        request: ToolAccessibilityRequest,
    ): ToolAccessibilityRaycastResult {
        if (triangleVerticesMm.isEmpty() || triangleVerticesMm.size % VALUES_PER_TRIANGLE != 0 ||
            triangleVerticesMm.any { !it.isFinite() }
        ) return rejected(ToolAccessRejection.INVALID_MESH)
        val triangleCount = triangleVerticesMm.size / VALUES_PER_TRIANGLE
        if (triangleCount > MAX_TRIANGLES || samples.size > MAX_SAMPLES)
            return rejected(ToolAccessRejection.TOO_LARGE)
        if (samples.any {
                it.targetTriangleIndex !in 0 until triangleCount ||
                    !it.xMm.isFinite() || !it.yMm.isFinite() || !it.zMm.isFinite() ||
                    (it.targetTriangleIndex in 0 until triangleCount && !liesOnTargetTriangle(triangleVerticesMm, it))
            }
        ) return rejected(ToolAccessRejection.INVALID_SAMPLE)

        val direction = request.normalizedApproachDirection
        val accessible = ArrayList<Int>(samples.size)
        val blocked = ArrayList<Int>()
        for ((sampleIndex, sample) in samples.withIndex()) {
            val originX = sample.xMm + direction[0] * request.reachMm
            val originY = sample.yMm + direction[1] * request.reachMm
            val originZ = sample.zMm + direction[2] * request.reachMm
            var obstructed = false
            for (triangleIndex in 0 until triangleCount) {
                if (triangleIndex == sample.targetTriangleIndex) continue
                if (intersectsOpenSegment(
                        triangleVerticesMm, triangleIndex,
                        originX, originY, originZ,
                        -direction[0], -direction[1], -direction[2],
                        request.reachMm - ENDPOINT_EPSILON_MM,
                    )
                ) {
                    obstructed = true
                    break
                }
            }
            if (obstructed) blocked += sampleIndex else accessible += sampleIndex
        }
        return ToolAccessibilityRaycastResult(accessible.toIntArray(), blocked.toIntArray())
    }

    private fun intersectsOpenSegment(
        mesh: DoubleArray,
        triangleIndex: Int,
        originX: Double, originY: Double, originZ: Double,
        directionX: Double, directionY: Double, directionZ: Double,
        maxDistance: Double,
    ): Boolean {
        val offset = triangleIndex * VALUES_PER_TRIANGLE
        val v0x = mesh[offset]; val v0y = mesh[offset + 1]; val v0z = mesh[offset + 2]
        val edge1x = mesh[offset + 3] - v0x; val edge1y = mesh[offset + 4] - v0y; val edge1z = mesh[offset + 5] - v0z
        val edge2x = mesh[offset + 6] - v0x; val edge2y = mesh[offset + 7] - v0y; val edge2z = mesh[offset + 8] - v0z
        val hx = directionY * edge2z - directionZ * edge2y
        val hy = directionZ * edge2x - directionX * edge2z
        val hz = directionX * edge2y - directionY * edge2x
        val determinant = edge1x * hx + edge1y * hy + edge1z * hz
        if (!determinant.isFinite() || abs(determinant) <= EPSILON) return false
        val inverse = 1.0 / determinant
        val sx = originX - v0x; val sy = originY - v0y; val sz = originZ - v0z
        val u = inverse * (sx * hx + sy * hy + sz * hz)
        if (u < -EPSILON || u > 1.0 + EPSILON) return false
        val qx = sy * edge1z - sz * edge1y
        val qy = sz * edge1x - sx * edge1z
        val qz = sx * edge1y - sy * edge1x
        val v = inverse * (directionX * qx + directionY * qy + directionZ * qz)
        if (v < -EPSILON || u + v > 1.0 + EPSILON) return false
        val distance = inverse * (edge2x * qx + edge2y * qy + edge2z * qz)
        return distance > ENDPOINT_EPSILON_MM && distance < maxDistance
    }

    private fun liesOnTargetTriangle(mesh: DoubleArray, sample: ToolSurfaceSample): Boolean {
        val offset = sample.targetTriangleIndex * VALUES_PER_TRIANGLE
        val ax = mesh[offset]; val ay = mesh[offset + 1]; val az = mesh[offset + 2]
        val abx = mesh[offset + 3] - ax; val aby = mesh[offset + 4] - ay; val abz = mesh[offset + 5] - az
        val acx = mesh[offset + 6] - ax; val acy = mesh[offset + 7] - ay; val acz = mesh[offset + 8] - az
        val apx = sample.xMm - ax; val apy = sample.yMm - ay; val apz = sample.zMm - az
        val d00 = abx * abx + aby * aby + abz * abz
        val d01 = abx * acx + aby * acy + abz * acz
        val d11 = acx * acx + acy * acy + acz * acz
        val d20 = apx * abx + apy * aby + apz * abz
        val d21 = apx * acx + apy * acy + apz * acz
        val denominator = d00 * d11 - d01 * d01
        if (!denominator.isFinite() || denominator <= EPSILON) return false
        val v = (d11 * d20 - d01 * d21) / denominator
        val w = (d00 * d21 - d01 * d20) / denominator
        val u = 1.0 - v - w
        if (u < -EPSILON || v < -EPSILON || w < -EPSILON) return false
        val projectedX = ax + v * abx + w * acx
        val projectedY = ay + v * aby + w * acy
        val projectedZ = az + v * abz + w * acz
        val dx = sample.xMm - projectedX
        val dy = sample.yMm - projectedY
        val dz = sample.zMm - projectedZ
        return dx * dx + dy * dy + dz * dz <= 0.05 * 0.05
    }

    private fun rejected(reason: ToolAccessRejection) =
        ToolAccessibilityRaycastResult(IntArray(0), IntArray(0), reason)
}
