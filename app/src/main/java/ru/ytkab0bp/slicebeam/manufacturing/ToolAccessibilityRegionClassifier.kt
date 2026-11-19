package ru.ytkab0bp.slicebeam.manufacturing

enum class ToolAccessRegionStatus { ACCESSIBLE, CONSTRAINED, BLOCKED, UNKNOWN }

data class ToolAccessRegion(
    val id: Int,
    val sampleIndices: IntArray,
)

data class ClassifiedToolAccessRegion(
    val id: Int,
    val sampleIndices: IntArray,
    val status: ToolAccessRegionStatus,
    val testedSamples: Int,
    val accessibleSamples: Int,
    val accessibleRatio: Double?,
)

data class ToolAccessibilityClassification(
    val regions: List<ClassifiedToolAccessRegion>,
    val rejected: ToolAccessRejection? = null,
) {
    val isAvailable: Boolean get() = rejected == null
}

/**
 * Classifies sampled visibility, not physical cutter clearance. Regions with 95%+ clear rays are
 * labeled accessible; regions with 5% or fewer are blocked; intermediate regions are constrained.
 * Empty or incomplete sample data stays unknown. These thresholds are reporting conventions only.
 */
object ToolAccessibilityRegionClassifier {
    const val ACCESSIBLE_RATIO_THRESHOLD = 0.95
    const val BLOCKED_RATIO_THRESHOLD = 0.05

    fun classify(
        raycast: ToolAccessibilityRaycastResult,
        regions: List<ToolAccessRegion>,
        sampleCount: Int,
    ): ToolAccessibilityClassification {
        if (!raycast.isAvailable) return ToolAccessibilityClassification(emptyList(), raycast.rejected)
        if (sampleCount < 0 || regions.map { it.id }.distinct().size != regions.size ||
            raycast.accessibleSampleIndices.any { it !in 0 until sampleCount } ||
            raycast.blockedSampleIndices.any { it !in 0 until sampleCount } ||
            raycast.accessibleSampleIndices.intersect(raycast.blockedSampleIndices.toSet()).isNotEmpty() ||
            regions.any { it.sampleIndices.any { index -> index !in 0 until sampleCount } }
        ) return ToolAccessibilityClassification(emptyList(), ToolAccessRejection.INVALID_SAMPLE)

        val accessible = raycast.accessibleSampleIndices.toSet()
        val blocked = raycast.blockedSampleIndices.toSet()
        val classified = regions.map { region ->
            val indices = region.sampleIndices.distinct()
            val known = indices.filter { it in accessible || it in blocked }
            val accessibleCount = known.count { it in accessible }
            val ratio = if (indices.isEmpty() || known.size != indices.size) null else accessibleCount.toDouble() / known.size
            val status = when {
                ratio == null -> ToolAccessRegionStatus.UNKNOWN
                ratio >= ACCESSIBLE_RATIO_THRESHOLD -> ToolAccessRegionStatus.ACCESSIBLE
                ratio <= BLOCKED_RATIO_THRESHOLD -> ToolAccessRegionStatus.BLOCKED
                else -> ToolAccessRegionStatus.CONSTRAINED
            }
            ClassifiedToolAccessRegion(region.id, indices.toIntArray(), status, known.size, accessibleCount, ratio)
        }
        return ToolAccessibilityClassification(classified)
    }
}
