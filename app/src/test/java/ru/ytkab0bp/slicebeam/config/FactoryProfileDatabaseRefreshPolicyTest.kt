package ru.ytkab0bp.slicebeam.config

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FactoryProfileDatabaseRefreshPolicyTest {
    @Test
    fun doesNotTreatRevisionAsInstalledWhenNoPrinterBundleWasApplicable() {
        assertFalse(FactoryProfileDatabaseRefreshPolicy.shouldSkip("rev-1", "rev-1", 0))
    }

    @Test
    fun skipsOnlyAfterAnApplicableBundleWasLoadedForThisRevision() {
        assertTrue(FactoryProfileDatabaseRefreshPolicy.shouldSkip("rev-1", "rev-1", 1))
        assertFalse(FactoryProfileDatabaseRefreshPolicy.shouldSkip("rev-0", "rev-1", 1))
    }
}
