package ru.ytkab0bp.slicebeam.config

/** The database revision is only authoritative after at least one printer bundle was applicable. */
internal object FactoryProfileDatabaseRefreshPolicy {
    fun shouldSkip(storedRevision: String?, bundledRevision: String, applicableBundleCount: Int): Boolean =
        applicableBundleCount > 0 && storedRevision == bundledRevision
}
