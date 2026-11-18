package ru.ytkab0bp.slicebeam.slic3r

import java.util.Locale

/** Formats whose independent meshes can safely be merged into one import transaction. */
internal object ModelBatchImportPolicy {
    private val supportedExtensions = setOf("stl", "obj", "amf", "oltp")

    fun supports(fileName: String?): Boolean {
        if (fileName.isNullOrBlank()) return false
        val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return extension in supportedExtensions
    }
}

/** Optional explicit clearance for multi-file imports; null keeps Orkan's compact nesting policy. */
data class ModelBatchImportPlacement(
    val minimumObjectDistanceMm: Double? = null,
) {
    val usesPerObjectDistance: Boolean get() = minimumObjectDistanceMm != null

    fun validatedDistanceMm(): Double? = minimumObjectDistanceMm
        ?.takeIf { it.isFinite() && it in MIN_DISTANCE_MM..MAX_DISTANCE_MM }

    companion object {
        const val MIN_DISTANCE_MM = 0.5
        const val MAX_DISTANCE_MM = 50.0
    }
}
