package ru.ytkab0bp.slicebeam.manufacturing

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

enum class ThroughHolePlacementRejection {
    INVALID_MESH,
    TOO_LARGE,
    NO_PLANAR_FACE,
    INVALID_FACE_BOUNDARY,
    CENTER_OUTSIDE_FACE,
    INSUFFICIENT_WALL,
}

data class ThroughHolePlacementResult(
    val minimumWallMm: Double? = null,
    val rejected: ThroughHolePlacementRejection? = null,
) {
    val isSupported: Boolean get() = rejected == null
}

/**
 * Conservative placement check for a through-hole preview. Input triangles are an immutable,
 * flattened mesh in the same model coordinates as [ManufacturingFeatureParameters.face].
 * This validates only the selected planar face and its boundary; it does not generate or mutate
 * geometry and must not be treated as proof that a Boolean result is printable.
 */
object ThroughHoleFaceValidator {
    private const val COMPONENTS_PER_TRIANGLE = 9
    private const val MAX_TRIANGLES = 50_000
    private const val EDGE_QUANTIZATION = 10_000.0
    private const val PLANAR_ANGLE_COS = 0.9993908270190958 // cos(2 degrees)
    private const val MIN_FACE_AREA = 1e-8

    fun validate(
        triangles: FloatArray,
        face: ManufacturingFaceFrame,
        diameterMm: Double,
        minimumWallMm: Double,
    ): ThroughHolePlacementResult {
        if (triangles.isEmpty() || triangles.size % COMPONENTS_PER_TRIANGLE != 0 || triangles.any { !it.isFinite() })
            return rejected(ThroughHolePlacementRejection.INVALID_MESH)
        val triangleCount = triangles.size / COMPONENTS_PER_TRIANGLE
        if (triangleCount > MAX_TRIANGLES) return rejected(ThroughHolePlacementRejection.TOO_LARGE)
        if (!diameterMm.isFinite() || diameterMm !in 0.2..500.0 ||
            !minimumWallMm.isFinite() || minimumWallMm !in 0.2..100.0
        ) return rejected(ThroughHolePlacementRejection.INVALID_MESH)

        val n = Point3(face.normal.x, face.normal.y, face.normal.z)
        val u = Point3(face.tangent.x, face.tangent.y, face.tangent.z)
        val v = cross(n, u)
        val origin = face.origin
        val planeTolerance = maxOf(0.03, (diameterMm * 0.002).coerceAtMost(0.1))
        val edgeCounts = HashMap<EdgeKey, Int>()
        var selectedTriangles = 0

        fun project(x: Double, y: Double, z: Double): Point2 {
            val dx = x - origin.xMm
            val dy = y - origin.yMm
            val dz = z - origin.zMm
            return Point2(dx * u.x + dy * u.y + dz * u.z, dx * v.x + dy * v.y + dz * v.z)
        }

        for (offset in triangles.indices step COMPONENTS_PER_TRIANGLE) {
            val points = Array(3) { corner ->
                val base = offset + corner * 3
                Point3(triangles[base].toDouble(), triangles[base + 1].toDouble(), triangles[base + 2].toDouble())
            }
            val ab = points[1] - points[0]
            val ac = points[2] - points[0]
            val normal = cross(ab, ac)
            val normalLength = length(normal)
            if (!normalLength.isFinite() || normalLength <= MIN_FACE_AREA) continue
            val unitNormal = normal / normalLength
            if (abs(dot(unitNormal, n)) < PLANAR_ANGLE_COS) continue
            if (points.any { abs((it.x - origin.xMm) * n.x + (it.y - origin.yMm) * n.y + (it.z - origin.zMm) * n.z) > planeTolerance }) continue

            val projected = points.map { project(it.x, it.y, it.z) }
            if (abs(cross2(projected[1] - projected[0], projected[2] - projected[0])) <= MIN_FACE_AREA) continue
            selectedTriangles++
            for (edgeIndex in 0..2) {
                val a = quantize(projected[edgeIndex])
                val b = quantize(projected[(edgeIndex + 1) % 3])
                if (a == b) return rejected(ThroughHolePlacementRejection.INVALID_FACE_BOUNDARY)
                val edge = if (a <= b) EdgeKey(a, b) else EdgeKey(b, a)
                val count = (edgeCounts[edge] ?: 0) + 1
                if (count > 2) return rejected(ThroughHolePlacementRejection.INVALID_FACE_BOUNDARY)
                edgeCounts[edge] = count
            }
        }
        if (selectedTriangles == 0) return rejected(ThroughHolePlacementRejection.NO_PLANAR_FACE)

        val boundary = edgeCounts.filterValues { it == 1 }.keys
        if (boundary.size < 3) return rejected(ThroughHolePlacementRejection.INVALID_FACE_BOUNDARY)
        val adjacency = HashMap<PointKey, MutableList<PointKey>>()
        for (edge in boundary) {
            adjacency.getOrPut(edge.a) { mutableListOf() }.add(edge.b)
            adjacency.getOrPut(edge.b) { mutableListOf() }.add(edge.a)
        }
        if (adjacency.values.any { it.size != 2 }) return rejected(ThroughHolePlacementRejection.INVALID_FACE_BOUNDARY)

        val loops = mutableListOf<List<Point2>>()
        val unused = boundary.toMutableSet()
        while (unused.isNotEmpty()) {
            val first = unused.first()
            val start = first.a
            var previous: PointKey? = null
            var current = start
            val loop = mutableListOf<Point2>()
            var closed = false
            var steps = 0
            while (steps++ <= boundary.size + 1) {
                loop += current.toPoint2()
                val next = adjacency[current].orEmpty().firstOrNull { it != previous }
                    ?: break
                val edge = if (current <= next) EdgeKey(current, next) else EdgeKey(next, current)
                if (!unused.remove(edge) && next != start) break
                previous = current
                current = next
                if (current == start) {
                    closed = true
                    break
                }
            }
            if (!closed || loop.size < 3) return rejected(ThroughHolePlacementRejection.INVALID_FACE_BOUNDARY)
            loops += loop
        }

        val center = Point2(0.0, 0.0)
        val isInsideFace = loops.count { contains(it, center) } % 2 == 1
        if (!isInsideFace) return rejected(ThroughHolePlacementRejection.CENTER_OUTSIDE_FACE)
        val nearestBoundary = loops.asSequence().flatMap { loop ->
            loop.indices.asSequence().map { index -> distanceToSegment(center, loop[index], loop[(index + 1) % loop.size]) }
        }.minOrNull() ?: return rejected(ThroughHolePlacementRejection.INVALID_FACE_BOUNDARY)
        val remainingWall = nearestBoundary - diameterMm / 2.0
        if (!remainingWall.isFinite() || remainingWall + 1e-6 < minimumWallMm)
            return ThroughHolePlacementResult(minimumWallMm = remainingWall, rejected = ThroughHolePlacementRejection.INSUFFICIENT_WALL)
        return ThroughHolePlacementResult(minimumWallMm = remainingWall)
    }

    private fun contains(loop: List<Point2>, point: Point2): Boolean {
        var inside = false
        var j = loop.lastIndex
        for (i in loop.indices) {
            val a = loop[i]; val b = loop[j]
            if ((a.y > point.y) != (b.y > point.y) &&
                point.x < (b.x - a.x) * (point.y - a.y) / (b.y - a.y) + a.x
            ) inside = !inside
            j = i
        }
        return inside
    }

    private fun distanceToSegment(point: Point2, a: Point2, b: Point2): Double {
        val dx = b.x - a.x; val dy = b.y - a.y
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared <= 1e-16) return hypot(point.x - a.x, point.y - a.y)
        val t = (((point.x - a.x) * dx + (point.y - a.y) * dy) / lengthSquared).coerceIn(0.0, 1.0)
        return hypot(point.x - (a.x + t * dx), point.y - (a.y + t * dy))
    }

    private fun quantize(point: Point2) = PointKey(
        kotlin.math.round(point.x * EDGE_QUANTIZATION).toLong(),
        kotlin.math.round(point.y * EDGE_QUANTIZATION).toLong(),
    )

    private fun cross(a: Point3, b: Point3) = Point3(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x)
    private fun dot(a: Point3, b: Point3) = a.x * b.x + a.y * b.y + a.z * b.z
    private fun length(point: Point3) = sqrt(dot(point, point))
    private fun cross2(a: Point2, b: Point2) = a.x * b.y - a.y * b.x
    private fun rejected(reason: ThroughHolePlacementRejection) = ThroughHolePlacementResult(rejected = reason)

    private data class Point2(val x: Double, val y: Double) {
        operator fun minus(other: Point2) = Point2(x - other.x, y - other.y)
    }
    private data class Point3(val x: Double, val y: Double, val z: Double) {
        operator fun minus(other: Point3) = Point3(x - other.x, y - other.y, z - other.z)
        operator fun div(value: Double) = Point3(x / value, y / value, z / value)
    }
    private data class PointKey(val x: Long, val y: Long) : Comparable<PointKey> {
        override fun compareTo(other: PointKey): Int = compareValuesBy(this, other, PointKey::x, PointKey::y)
        fun toPoint2() = Point2(x / EDGE_QUANTIZATION, y / EDGE_QUANTIZATION)
    }
    private data class EdgeKey(val a: PointKey, val b: PointKey)
}
