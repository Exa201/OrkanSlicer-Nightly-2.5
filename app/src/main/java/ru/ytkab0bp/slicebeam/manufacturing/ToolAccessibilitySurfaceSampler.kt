package ru.ytkab0bp.slicebeam.manufacturing

import kotlin.math.ceil
import kotlin.math.sqrt

enum class ToolSurfaceSamplingRejection { INVALID_MESH, TOO_LARGE, NO_SURFACE }

data class ToolSurfaceSamplingResult(
    val samples: List<ToolSurfaceSample>,
    val rejected: ToolSurfaceSamplingRejection? = null,
) {
    val isAvailable: Boolean get() = rejected == null && samples.isNotEmpty()
}

/** Deterministic, bounded, area-weighted sampling for the tool-accessibility raycaster. */
object ToolAccessibilitySurfaceSampler {
    private const val VALUES_PER_TRIANGLE = 9
    private const val MAX_TRIANGLES = 4096
    private const val MAX_SAMPLES = 1024
    private const val MAX_COORDINATE_MM = 1_000_000.0
    private const val MIN_DOUBLE_AREA = 1e-20

    /**
     * Selects points over triangle-list geometry in model millimeters. The requested spacing is
     * treated as a target, while both mesh and result sizes are capped for mobile workloads.
     * Degenerate triangles are skipped; no input geometry is changed.
     */
    fun sample(triangleVerticesMm: DoubleArray, spacingMm: Double): ToolSurfaceSamplingResult {
        if (triangleVerticesMm.isEmpty() || triangleVerticesMm.size % VALUES_PER_TRIANGLE != 0 ||
            triangleVerticesMm.any { !it.isFinite() || kotlin.math.abs(it) > MAX_COORDINATE_MM } ||
            !spacingMm.isFinite() || spacingMm !in 0.05..5.0
        ) return ToolSurfaceSamplingResult(emptyList(), ToolSurfaceSamplingRejection.INVALID_MESH)

        val triangleCount = triangleVerticesMm.size / VALUES_PER_TRIANGLE
        if (triangleCount > MAX_TRIANGLES)
            return ToolSurfaceSamplingResult(emptyList(), ToolSurfaceSamplingRejection.TOO_LARGE)

        val areas = DoubleArray(triangleCount)
        var totalArea = 0.0
        for (triangle in 0 until triangleCount) {
            val area = triangleArea(triangleVerticesMm, triangle)
            if (area.isFinite() && area > MIN_DOUBLE_AREA) {
                areas[triangle] = area
                totalArea += area
            }
        }
        if (!totalArea.isFinite() || totalArea <= MIN_DOUBLE_AREA)
            return ToolSurfaceSamplingResult(emptyList(), ToolSurfaceSamplingRejection.NO_SURFACE)

        val sampleCount = ceil(totalArea / (spacingMm * spacingMm)).toInt().coerceIn(1, MAX_SAMPLES)
        val cumulativeAreas = DoubleArray(triangleCount)
        var cumulative = 0.0
        for (triangle in areas.indices) {
            cumulative += areas[triangle]
            cumulativeAreas[triangle] = cumulative
        }

        val output = ArrayList<ToolSurfaceSample>(sampleCount)
        var triangle = 0
        for (sampleIndex in 0 until sampleCount) {
            val areaPosition = (sampleIndex + 0.5) * totalArea / sampleCount
            while (triangle < triangleCount - 1 && cumulativeAreas[triangle] <= areaPosition) triangle++
            while (triangle < triangleCount - 1 && areas[triangle] == 0.0) triangle++

            // Low-discrepancy barycentric points avoid a regular-grid bias while remaining repeatable.
            val u = radicalInverse(sampleIndex + 1, 2)
            val v = radicalInverse(sampleIndex + 1, 3)
            val root = sqrt(u)
            val wa = 1.0 - root
            val wb = root * (1.0 - v)
            val wc = root * v
            val offset = triangle * VALUES_PER_TRIANGLE
            val x = triangleVerticesMm[offset] * wa + triangleVerticesMm[offset + 3] * wb + triangleVerticesMm[offset + 6] * wc
            val y = triangleVerticesMm[offset + 1] * wa + triangleVerticesMm[offset + 4] * wb + triangleVerticesMm[offset + 7] * wc
            val z = triangleVerticesMm[offset + 2] * wa + triangleVerticesMm[offset + 5] * wb + triangleVerticesMm[offset + 8] * wc
            if (!x.isFinite() || !y.isFinite() || !z.isFinite())
                return ToolSurfaceSamplingResult(emptyList(), ToolSurfaceSamplingRejection.INVALID_MESH)
            output += ToolSurfaceSample(triangle, x, y, z)
        }
        return ToolSurfaceSamplingResult(output)
    }

    private fun triangleArea(mesh: DoubleArray, triangle: Int): Double {
        val offset = triangle * VALUES_PER_TRIANGLE
        val abx = mesh[offset + 3] - mesh[offset]
        val aby = mesh[offset + 4] - mesh[offset + 1]
        val abz = mesh[offset + 5] - mesh[offset + 2]
        val acx = mesh[offset + 6] - mesh[offset]
        val acy = mesh[offset + 7] - mesh[offset + 1]
        val acz = mesh[offset + 8] - mesh[offset + 2]
        val crossX = aby * acz - abz * acy
        val crossY = abz * acx - abx * acz
        val crossZ = abx * acy - aby * acx
        return 0.5 * sqrt(crossX * crossX + crossY * crossY + crossZ * crossZ)
    }

    private fun radicalInverse(value: Int, base: Int): Double {
        var index = value
        var factor = 1.0 / base
        var result = 0.0
        while (index > 0) {
            result += (index % base) * factor
            index /= base
            factor /= base
        }
        return result
    }
}
