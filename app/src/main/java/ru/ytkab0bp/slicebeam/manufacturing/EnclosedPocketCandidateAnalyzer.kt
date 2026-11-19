package ru.ytkab0bp.slicebeam.manufacturing

/** A sample hidden from all of the tested tool approach directions; this is not proof of a cavity. */
enum class PocketSampleStatus { VISIBLE_FROM_TESTED_DIRECTION, OCCLUDED_IN_TESTED_DIRECTIONS, UNKNOWN }

data class EnclosedPocketCandidateReport(
    val sampleStatuses: List<PocketSampleStatus>,
    val testedDirectionCount: Int,
    val assumptions: List<String>,
    val rejected: ToolAccessRejection? = null,
) {
    val isAvailable: Boolean get() = rejected == null
}

/**
 * Conservative visibility report from six axis-aligned centerline approaches. It deliberately
 * reports only occlusion candidates: six directions cannot prove a sealed cavity or account for
 * cutter diameter, holder clearance, machine kinematics, or mesh self-intersections.
 */
object EnclosedPocketCandidateAnalyzer {
    const val REQUIRED_APPROACH_DIRECTIONS = 6
    const val MAX_SAMPLES = 1024

    val documentedAssumptions = listOf(
        "Six axis-aligned approach directions are sampled; other directions may be accessible.",
        "Visibility uses tool centerlines and does not account for cutter radius or holder collision.",
        "Occlusion is a candidate indicator, not proof of an enclosed cavity or machinability.",
        "The result assumes the supplied mesh is valid and does not test self-intersections.",
    )

    fun analyze(
        directionalResults: List<ToolAccessibilityRaycastResult>,
        sampleCount: Int,
    ): EnclosedPocketCandidateReport {
        if (sampleCount !in 1..MAX_SAMPLES || directionalResults.size != REQUIRED_APPROACH_DIRECTIONS ||
            directionalResults.any { !it.isAvailable }
        ) return unavailable(ToolAccessRejection.INVALID_SAMPLE)

        val statusPerDirection = directionalResults.map { result ->
            val accessible = result.accessibleSampleIndices.toSet()
            val blocked = result.blockedSampleIndices.toSet()
            if (accessible.size != result.accessibleSampleIndices.size || blocked.size != result.blockedSampleIndices.size ||
                accessible.any { it !in 0 until sampleCount } || blocked.any { it !in 0 until sampleCount } ||
                accessible.intersect(blocked).isNotEmpty() || accessible.size + blocked.size != sampleCount ||
                (0 until sampleCount).any { it !in accessible && it !in blocked }
            ) return unavailable(ToolAccessRejection.INVALID_SAMPLE)
            Pair(accessible, blocked)
        }

        val statuses = (0 until sampleCount).map { sampleIndex ->
            when {
                statusPerDirection.any { sampleIndex in it.first } -> PocketSampleStatus.VISIBLE_FROM_TESTED_DIRECTION
                statusPerDirection.all { sampleIndex in it.second } -> PocketSampleStatus.OCCLUDED_IN_TESTED_DIRECTIONS
                else -> PocketSampleStatus.UNKNOWN
            }
        }
        return EnclosedPocketCandidateReport(statuses, REQUIRED_APPROACH_DIRECTIONS, documentedAssumptions)
    }

    private fun unavailable(reason: ToolAccessRejection) =
        EnclosedPocketCandidateReport(emptyList(), 0, documentedAssumptions, reason)
}
