package ru.ytkab0bp.slicebeam.manufacturing

enum class ToolAccessibilityAnalysisFailure {
    INVALID_INPUT,
    TOO_LARGE,
    NO_SURFACE,
    RAYCAST_REJECTED,
}

data class ToolAccessibilityAnalysisResult(
    val samples: List<ToolSurfaceSample>,
    val raycast: ToolAccessibilityRaycastResult?,
    val classification: ToolAccessibilityClassification?,
    val failure: ToolAccessibilityAnalysisFailure? = null,
) {
    val isAvailable: Boolean get() = failure == null && raycast?.isAvailable == true && classification?.isAvailable == true
}

data class ToolAccessibilityMultiApproachAnalysisResult(
    val samples: List<ToolSurfaceSample>,
    val directionalRaycasts: List<ToolAccessibilityRaycastResult>,
    val pocketCandidates: EnclosedPocketCandidateReport?,
    val classification: ToolAccessibilityClassification?,
    val failure: ToolAccessibilityAnalysisFailure? = null,
) {
    val isAvailable: Boolean get() = failure == null && pocketCandidates?.isAvailable == true && classification?.isAvailable == true
}

/** Bounded offline pipeline: sample model faces, raycast the configured approach, classify each face. */
object ToolAccessibilityAnalyzer {
    private const val VALUES_PER_TRIANGLE = 9
    private const val MAX_TRIANGLES = 4096

    /** Converts bounded native float mesh data without allocating for unsupported large meshes. */
    fun analyze(triangleVerticesMm: FloatArray, request: ToolAccessibilityRequest): ToolAccessibilityAnalysisResult {
        if (triangleVerticesMm.isEmpty() || triangleVerticesMm.size % VALUES_PER_TRIANGLE != 0 ||
            triangleVerticesMm.size / VALUES_PER_TRIANGLE > MAX_TRIANGLES ||
            triangleVerticesMm.any { !it.isFinite() || kotlin.math.abs(it) > 1_000_000f }
        ) {
            val failure = if (triangleVerticesMm.size / VALUES_PER_TRIANGLE > MAX_TRIANGLES)
                ToolAccessibilityAnalysisFailure.TOO_LARGE else ToolAccessibilityAnalysisFailure.INVALID_INPUT
            return ToolAccessibilityAnalysisResult(emptyList(), null, null, failure)
        }
        return analyze(DoubleArray(triangleVerticesMm.size) { triangleVerticesMm[it].toDouble() }, request)
    }

    /** Runs bounded centerline checks from all six axis directions over one shared surface sample set. */
    fun analyzeAllApproaches(triangleVerticesMm: FloatArray, request: ToolAccessibilityRequest): ToolAccessibilityMultiApproachAnalysisResult {
        if (triangleVerticesMm.isEmpty() || triangleVerticesMm.size % VALUES_PER_TRIANGLE != 0 ||
            triangleVerticesMm.size / VALUES_PER_TRIANGLE > MAX_TRIANGLES ||
            triangleVerticesMm.any { !it.isFinite() || kotlin.math.abs(it) > 1_000_000f }
        ) {
            val failure = if (triangleVerticesMm.size / VALUES_PER_TRIANGLE > MAX_TRIANGLES)
                ToolAccessibilityAnalysisFailure.TOO_LARGE else ToolAccessibilityAnalysisFailure.INVALID_INPUT
            return unavailableMulti(failure)
        }
        return analyzeAllApproaches(DoubleArray(triangleVerticesMm.size) { triangleVerticesMm[it].toDouble() }, request)
    }

    fun analyzeAllApproaches(triangleVerticesMm: DoubleArray, request: ToolAccessibilityRequest): ToolAccessibilityMultiApproachAnalysisResult {
        val sampling = ToolAccessibilitySurfaceSampler.sample(triangleVerticesMm, request.sampleSpacingMm)
        if (!sampling.isAvailable) {
            val failure = when (sampling.rejected) {
                ToolSurfaceSamplingRejection.TOO_LARGE -> ToolAccessibilityAnalysisFailure.TOO_LARGE
                ToolSurfaceSamplingRejection.NO_SURFACE -> ToolAccessibilityAnalysisFailure.NO_SURFACE
                ToolSurfaceSamplingRejection.INVALID_MESH, null -> ToolAccessibilityAnalysisFailure.INVALID_INPUT
            }
            return unavailableMulti(failure)
        }
        val directions = listOf(
            doubleArrayOf(1.0, 0.0, 0.0), doubleArrayOf(-1.0, 0.0, 0.0),
            doubleArrayOf(0.0, 1.0, 0.0), doubleArrayOf(0.0, -1.0, 0.0),
            doubleArrayOf(0.0, 0.0, 1.0), doubleArrayOf(0.0, 0.0, -1.0),
        )
        val raycasts = directions.map { direction ->
            ToolAccessibilityRaycaster.analyze(
                triangleVerticesMm,
                sampling.samples,
                request.copy(approachX = direction[0], approachY = direction[1], approachZ = direction[2]),
            )
        }
        if (raycasts.any { !it.isAvailable })
            return unavailableMulti(ToolAccessibilityAnalysisFailure.RAYCAST_REJECTED, sampling.samples, raycasts)
        val pockets = EnclosedPocketCandidateAnalyzer.analyze(raycasts, sampling.samples.size)
        if (!pockets.isAvailable)
            return unavailableMulti(ToolAccessibilityAnalysisFailure.RAYCAST_REJECTED, sampling.samples, raycasts)

        val accessibleFlags = BooleanArray(sampling.samples.size)
        raycasts.forEach { result -> result.accessibleSampleIndices.forEach { accessibleFlags[it] = true } }
        val accessibleFromAny = accessibleFlags.indices.filter { accessibleFlags[it] }.toIntArray()
        val blockedFromAll = accessibleFlags.indices.filterNot { accessibleFlags[it] }.toIntArray()
        val merged = ToolAccessibilityRaycastResult(accessibleFromAny, blockedFromAll)
        val regions = sampling.samples.indices.groupBy { sampling.samples[it].targetTriangleIndex }
            .map { (triangleIndex, indices) -> ToolAccessRegion(triangleIndex, indices.toIntArray()) }
        val classification = ToolAccessibilityRegionClassifier.classify(merged, regions, sampling.samples.size)
        if (!classification.isAvailable)
            return unavailableMulti(ToolAccessibilityAnalysisFailure.INVALID_INPUT, sampling.samples, raycasts)
        return ToolAccessibilityMultiApproachAnalysisResult(sampling.samples, raycasts, pockets, classification)
    }

    fun analyze(triangleVerticesMm: DoubleArray, request: ToolAccessibilityRequest): ToolAccessibilityAnalysisResult {
        val sampling = ToolAccessibilitySurfaceSampler.sample(triangleVerticesMm, request.sampleSpacingMm)
        if (!sampling.isAvailable) {
            val failure = when (sampling.rejected) {
                ToolSurfaceSamplingRejection.TOO_LARGE -> ToolAccessibilityAnalysisFailure.TOO_LARGE
                ToolSurfaceSamplingRejection.NO_SURFACE -> ToolAccessibilityAnalysisFailure.NO_SURFACE
                ToolSurfaceSamplingRejection.INVALID_MESH, null -> ToolAccessibilityAnalysisFailure.INVALID_INPUT
            }
            return ToolAccessibilityAnalysisResult(emptyList(), null, null, failure)
        }

        val raycast = ToolAccessibilityRaycaster.analyze(triangleVerticesMm, sampling.samples, request)
        if (!raycast.isAvailable)
            return ToolAccessibilityAnalysisResult(sampling.samples, raycast, null, ToolAccessibilityAnalysisFailure.RAYCAST_REJECTED)

        val indicesByTriangle = sampling.samples.indices.groupBy { sampling.samples[it].targetTriangleIndex }
        val regions = indicesByTriangle.entries.map { (triangleIndex, sampleIndices) ->
            ToolAccessRegion(triangleIndex, sampleIndices.toIntArray())
        }
        val classification = ToolAccessibilityRegionClassifier.classify(raycast, regions, sampling.samples.size)
        if (!classification.isAvailable)
            return ToolAccessibilityAnalysisResult(sampling.samples, raycast, classification, ToolAccessibilityAnalysisFailure.INVALID_INPUT)
        return ToolAccessibilityAnalysisResult(sampling.samples, raycast, classification)
    }

    private fun unavailableMulti(
        failure: ToolAccessibilityAnalysisFailure,
        samples: List<ToolSurfaceSample> = emptyList(),
        raycasts: List<ToolAccessibilityRaycastResult> = emptyList(),
    ) = ToolAccessibilityMultiApproachAnalysisResult(samples, raycasts, null, null, failure)
}
