package ru.ytkab0bp.slicebeam.manufacturing

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

enum class CylindricalFeatureKind { INTERNAL_BORE, EXTERNAL_SHAFT }

/** A detected candidate only. It is not a manufacturing instruction or an applied correction. */
data class CylindricalFeatureCandidate(
    val kind: CylindricalFeatureKind,
    val centerMm: DoubleArray,
    val axisDirection: DoubleArray,
    val radiusMm: Double,
    val axialLengthMm: Double,
    val axialStartMm: Double,
    val axialEndMm: Double,
    val confidence: Double,
    val sampledTriangleCount: Int,
)

/**
 * Conservative cylinder patch detector for a flattened triangle list (xyz per vertex).
 * The input is expected to be a bounded preview mesh, so detections are candidates with confidence,
 * not proof of a through-hole or a mating fit. No mesh or source model is modified.
 */
object CylindricalFeatureDetector {
    private const val MAX_TRIANGLES = 256
    private const val MIN_TRIANGLES = 8
    private const val MAX_AXIS_CANDIDATES = 32
    private const val NORMAL_AXIS_TOLERANCE = 0.28

    /** Analyze a bounded native preview of one model object; null handles invalid native inputs. */
    fun detectModel(modelPointer: Long, objectIndex: Int): List<CylindricalFeatureCandidate>? {
        if (modelPointer == 0L || objectIndex < 0) return null
        return detect(ru.ytkab0bp.slicebeam.slic3r.Native.model_get_object_preview_mesh(modelPointer, objectIndex, MAX_TRIANGLES))
    }

    fun detect(flatTriangleVertices: FloatArray): List<CylindricalFeatureCandidate> {
        if (flatTriangleVertices.isEmpty() || flatTriangleVertices.size % 9 != 0) return emptyList()
        val triangleCount = flatTriangleVertices.size / 9
        if (triangleCount > MAX_TRIANGLES) return emptyList()

        val triangles = ArrayList<Triangle>(triangleCount)
        for (i in 0 until triangleCount) {
            val offset = i * 9
            val a = Vec(flatTriangleVertices[offset].toDouble(), flatTriangleVertices[offset + 1].toDouble(), flatTriangleVertices[offset + 2].toDouble())
            val b = Vec(flatTriangleVertices[offset + 3].toDouble(), flatTriangleVertices[offset + 4].toDouble(), flatTriangleVertices[offset + 5].toDouble())
            val c = Vec(flatTriangleVertices[offset + 6].toDouble(), flatTriangleVertices[offset + 7].toDouble(), flatTriangleVertices[offset + 8].toDouble())
            if (!a.finite() || !b.finite() || !c.finite()) return emptyList()
            val cross = (b - a).cross(c - a)
            val area2 = cross.length()
            if (area2 <= 1e-8) continue
            triangles += Triangle((a + b + c) / 3.0, cross / area2, listOf(a, b, c))
        }
        if (triangles.size < MIN_TRIANGLES) return emptyList()

        val candidates = ArrayList<CylindricalFeatureCandidate>()
        for (axis in candidateAxes(triangles)) {
            val members = triangles.filter { abs(it.normal.dot(axis)) <= NORMAL_AXIS_TOLERANCE }
            if (members.size < MIN_TRIANGLES) continue
            val fraction = members.size.toDouble() / triangles.size
            // A usable bore/shaft can be a small part of a larger model. Keep enough support to
            // avoid treating incidental edges as an axis, but do not require most of the model
            // to be cylindrical. Evaluate more than one axis so a dominant planar surface cannot
            // hide a smaller cylindrical feature elsewhere in the mesh.
            if (fraction < 0.18) continue
            val axisCandidates = detectOnAxis(AxisGroup(axis, members), triangles.size)
            for (candidate in axisCandidates) {
                val duplicate = candidates.any { existing ->
                    existing.kind == candidate.kind &&
                        abs(existing.radiusMm - candidate.radiusMm) <= max(0.12, candidate.radiusMm * 0.025) &&
                        abs(existing.axisDirection.zip(candidate.axisDirection).sumOf { (a, b) -> a * b }) >= cos(PI / 72.0) &&
                        existing.centerMm.zip(candidate.centerMm).sumOf { (a, b) -> (a - b) * (a - b) } <= 0.25
                }
                if (!duplicate) candidates += candidate
            }
        }
        return candidates.sortedByDescending { it.confidence }
    }

    private fun detectOnAxis(group: AxisGroup, totalTriangles: Int): List<CylindricalFeatureCandidate> {
        val basisU = perpendicular(group.axis)
        val basisV = group.axis.cross(basisU).normalized() ?: return emptyList()
        val projected = group.members.mapNotNull { triangle ->
            val n = Vec(triangle.normal.dot(basisU), triangle.normal.dot(basisV), 0.0).normalized() ?: return@mapNotNull null
            val p = Vec(triangle.center.dot(basisU), triangle.center.dot(basisV), 0.0)
            ProjectedTriangle(triangle, p, n)
        }
        if (projected.size < MIN_TRIANGLES) return emptyList()
        val center2 = fitCenter(projected) ?: return emptyList()
        val center3 = basisU * center2.x + basisV * center2.y

        val radial = projected.map { item ->
            val point = item.point - center2
            RadialTriangle(item, point.length(), atan2(point.y, point.x))
        }.filter { it.radius.isFinite() && it.radius > 0.05 }
        if (radial.size < MIN_TRIANGLES) return emptyList()

        val sorted = radial.sortedBy { it.radius }
        val candidates = ArrayList<CylindricalFeatureCandidate>()
        var start = 0
        while (start < sorted.size) {
            val seed = sorted[start].radius
            val tolerance = max(0.12, seed * 0.025)
            var end = start + 1
            while (end < sorted.size && sorted[end].radius - seed <= tolerance) end++
            val cluster = sorted.subList(start, end)
            if (cluster.size >= MIN_TRIANGLES) {
                val candidate = scoreCluster(cluster, totalTriangles, center3, group.axis, basisU, basisV)
                if (candidate != null && candidate.confidence >= 0.60) candidates += candidate
            }
            start = end
        }
        return candidates
    }

    private fun candidateAxes(triangles: List<Triangle>): List<Vec> {
        val candidates = ArrayList<Pair<Vec, Int>>()
        // Bound the quadratic search when the preview has its full triangle budget.
        val stride = max(1, triangles.size / 128)
        val sampled = triangles.filterIndexed { index, _ -> index % stride == 0 }
        for (i in sampled.indices) for (j in i + 1 until sampled.size) {
            val axis = sampled[i].normal.cross(sampled[j].normal).normalized() ?: continue
            val existing = candidates.indexOfFirst { abs(it.first.dot(axis)) >= cos(PI / 72.0) }
            if (existing >= 0) {
                val old = candidates[existing]
                candidates[existing] = old.first to (old.second + 1)
            } else {
                candidates += axis to 1
            }
        }
        return candidates.sortedByDescending { it.second }.take(MAX_AXIS_CANDIDATES).map { it.first }
    }

    private fun perpendicular(axis: Vec): Vec {
        val reference = if (abs(axis.x) < 0.8) Vec(1.0, 0.0, 0.0) else Vec(0.0, 1.0, 0.0)
        return axis.cross(reference).normalized() ?: Vec(0.0, 0.0, 1.0)
    }

    private fun fitCenter(samples: List<ProjectedTriangle>): Vec? {
        var a00 = 0.0; var a01 = 0.0; var a11 = 0.0
        var b0 = 0.0; var b1 = 0.0
        for (sample in samples) {
            val nx = sample.normal.x; val ny = sample.normal.y
            val m00 = 1.0 - nx * nx
            val m01 = -nx * ny
            val m11 = 1.0 - ny * ny
            a00 += m00; a01 += m01; a11 += m11
            b0 += m00 * sample.point.x + m01 * sample.point.y
            b1 += m01 * sample.point.x + m11 * sample.point.y
        }
        val determinant = a00 * a11 - a01 * a01
        if (!determinant.isFinite() || abs(determinant) < 1e-8) return null
        return Vec((b0 * a11 - b1 * a01) / determinant, (a00 * b1 - a01 * b0) / determinant, 0.0)
    }

    private fun scoreCluster(
        cluster: List<RadialTriangle>,
        totalTriangles: Int,
        center: Vec,
        axis: Vec,
        basisU: Vec,
        basisV: Vec,
    ): CylindricalFeatureCandidate? {
        val radius = cluster.map { it.radius }.average()
        if (!radius.isFinite() || radius <= 0.05) return null
        val variance = cluster.map { (it.radius - radius) * (it.radius - radius) }.average()
        val radialError = sqrt(variance) / radius
        val bins = BooleanArray(24)
        var normalAgreement = 0
        var minAlong = Double.POSITIVE_INFINITY
        var maxAlong = Double.NEGATIVE_INFINITY
        for (sample in cluster) {
            val bin = (((sample.angle + PI) / (2.0 * PI) * bins.size).toInt()).coerceIn(0, bins.lastIndex)
            bins[bin] = true
            val radialDirection = basisU * cos(sample.angle) + basisV * sin(sample.angle)
            if (sample.projected.triangle.normal.dot(radialDirection) > 0.25) normalAgreement++
            for (vertex in sample.projected.triangle.vertices) {
                val along = vertex.dot(axis)
                minAlong = min(minAlong, along); maxAlong = max(maxAlong, along)
            }
        }
        val coverage = bins.count { it }.toDouble() / bins.size
        val oriented = max(normalAgreement, cluster.size - normalAgreement).toDouble() / cluster.size
        val consistency = (1.0 - radialError * 4.0).coerceIn(0.0, 1.0)
        val support = cluster.size.toDouble() / totalTriangles
        val confidence = (0.35 * coverage + 0.30 * consistency + 0.20 * oriented + 0.15 * min(1.0, support * 2.0)).coerceIn(0.0, 1.0)
        if (coverage < 0.55 || oriented < 0.85 || maxAlong - minAlong <= 0.05) return null
        val majorityPointsOut = normalAgreement * 2 >= cluster.size
        return CylindricalFeatureCandidate(
            kind = if (majorityPointsOut) CylindricalFeatureKind.EXTERNAL_SHAFT else CylindricalFeatureKind.INTERNAL_BORE,
            centerMm = doubleArrayOf(center.x, center.y, center.z),
            axisDirection = doubleArrayOf(axis.x, axis.y, axis.z),
            radiusMm = radius,
            axialLengthMm = maxAlong - minAlong,
            axialStartMm = minAlong,
            axialEndMm = maxAlong,
            confidence = confidence,
            sampledTriangleCount = cluster.size,
        )
    }

    private data class Triangle(val center: Vec, val normal: Vec, val vertices: List<Vec>)
    private data class AxisGroup(val axis: Vec, val members: List<Triangle>)
    private data class ProjectedTriangle(val triangle: Triangle, val point: Vec, val normal: Vec)
    private data class RadialTriangle(val projected: ProjectedTriangle, val radius: Double, val angle: Double)

    private data class Vec(val x: Double, val y: Double, val z: Double) {
        operator fun plus(other: Vec) = Vec(x + other.x, y + other.y, z + other.z)
        operator fun minus(other: Vec) = Vec(x - other.x, y - other.y, z - other.z)
        operator fun times(factor: Double) = Vec(x * factor, y * factor, z * factor)
        operator fun div(factor: Double) = Vec(x / factor, y / factor, z / factor)
        fun dot(other: Vec) = x * other.x + y * other.y + z * other.z
        fun cross(other: Vec) = Vec(y * other.z - z * other.y, z * other.x - x * other.z, x * other.y - y * other.x)
        fun length() = sqrt(dot(this))
        fun normalized(): Vec? = length().takeIf { it.isFinite() && it > 1e-8 }?.let { this / it }
        fun finite() = x.isFinite() && y.isFinite() && z.isFinite()
    }
}
