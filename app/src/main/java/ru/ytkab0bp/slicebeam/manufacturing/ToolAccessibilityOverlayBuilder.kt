package ru.ytkab0bp.slicebeam.manufacturing

/** Triangle-list markers for a transient GL overlay; these are never model or export geometry. */
data class ToolAccessibilityOverlayMeshes(
    val accessibleTriangles: FloatArray,
    val constrainedTriangles: FloatArray,
    val blockedTriangles: FloatArray,
)

object ToolAccessibilityOverlayBuilder {
    private const val MAX_SAMPLES = 1024
    private const val COMPONENTS_PER_TRIANGLE = 9

    /** Builds small octahedron markers at measured sample points, grouped by reported status. */
    fun build(
        samples: List<ToolSurfaceSample>,
        classification: ToolAccessibilityClassification,
        markerRadiusMm: Double,
    ): ToolAccessibilityOverlayMeshes? {
        if (!classification.isAvailable || samples.isEmpty() || samples.size > MAX_SAMPLES ||
            !markerRadiusMm.isFinite() || markerRadiusMm !in 0.1..2.0
        ) return null
        val statusBySample = HashMap<Int, ToolAccessRegionStatus>()
        for (region in classification.regions) {
            if (region.testedSamples < 0 || region.accessibleSamples !in 0..region.testedSamples) return null
            // A region's reported status applies uniformly to its sampled surface markers.
            for (sampleIndex in region.sampleIndices) {
                if (sampleIndex !in samples.indices || statusBySample.put(sampleIndex, region.status) != null) return null
            }
        }
        if (statusBySample.isEmpty()) return null
        val accessible = ArrayList<Float>()
        val constrained = ArrayList<Float>()
        val blocked = ArrayList<Float>()
        for ((index, status) in statusBySample) {
            val sample = samples[index]
            if (!sample.xMm.isFinite() || !sample.yMm.isFinite() || !sample.zMm.isFinite()) return null
            val target = when (status) {
                ToolAccessRegionStatus.ACCESSIBLE -> accessible
                ToolAccessRegionStatus.CONSTRAINED -> constrained
                ToolAccessRegionStatus.BLOCKED -> blocked
                ToolAccessRegionStatus.UNKNOWN -> continue
            }
            appendOctahedron(target, sample, markerRadiusMm)
        }
        return ToolAccessibilityOverlayMeshes(accessible.toFloatArray(), constrained.toFloatArray(), blocked.toFloatArray())
            .takeIf { it.accessibleTriangles.isNotEmpty() || it.constrainedTriangles.isNotEmpty() || it.blockedTriangles.isNotEmpty() }
    }

    private fun appendOctahedron(output: MutableList<Float>, sample: ToolSurfaceSample, radius: Double) {
        val center = doubleArrayOf(sample.xMm, sample.yMm, sample.zMm)
        val points = arrayOf(
            doubleArrayOf(center[0] + radius, center[1], center[2]),
            doubleArrayOf(center[0] - radius, center[1], center[2]),
            doubleArrayOf(center[0], center[1] + radius, center[2]),
            doubleArrayOf(center[0], center[1] - radius, center[2]),
            doubleArrayOf(center[0], center[1], center[2] + radius),
            doubleArrayOf(center[0], center[1], center[2] - radius),
        )
        val faces = arrayOf(
            intArrayOf(4, 0, 2), intArrayOf(4, 2, 1), intArrayOf(4, 1, 3), intArrayOf(4, 3, 0),
            intArrayOf(5, 2, 0), intArrayOf(5, 1, 2), intArrayOf(5, 3, 1), intArrayOf(5, 0, 3),
        )
        for (face in faces) for (vertex in face) for (axis in 0..2) output += points[vertex][axis].toFloat()
    }
}
