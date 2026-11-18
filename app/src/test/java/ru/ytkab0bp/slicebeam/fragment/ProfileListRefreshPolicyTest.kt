package ru.ytkab0bp.slicebeam.fragment

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileListRefreshPolicyTest {
    @Test
    fun changingSelectedProcessInvalidatesCompatibilityListsWithoutPrinterChange() {
        val before = ProfileCompatibilityCacheKey(null, "Process A", null, 12)
        val after = ProfileCompatibilityCacheKey(null, "Process B", null, 12)

        assertFalse(before == after)
    }

    @Test
    fun changingSelectedFilamentInvalidatesFilamentCompatibilityList() {
        val before = ProfileCompatibilityCacheKey(null, "Process A", "Filament A", 12)
        val after = ProfileCompatibilityCacheKey(null, "Process A", "Filament B", 12)

        assertFalse(before == after)
    }

    @Test
    fun switchingPrinterProfileRequiresARebindAndCategoryReset() {
        assertTrue(ProfileListRefreshPolicy.needsReload("Printer A", 10, "Printer B", 11))
        assertTrue(ProfileListRefreshPolicy.profileChanged("Printer A", "Printer B"))
    }

    @Test
    fun externalProfileSaveRequiresRefreshingOptionsEvenWhenTitleIsUnchanged() {
        assertTrue(ProfileListRefreshPolicy.needsReload("Printer A", 10, "Printer A", 11))
        assertFalse(ProfileListRefreshPolicy.profileChanged("Printer A", "Printer A"))
    }

    @Test
    fun unchangedProfileAndConfigKeepTheCurrentScreenState() {
        assertFalse(ProfileListRefreshPolicy.needsReload("Printer A", 10, "Printer A", 10))
    }

    @Test
    fun missingActiveSelectionDoesNotReplaceTheVisibleProfileByAccident() {
        assertFalse(ProfileListRefreshPolicy.needsReload("Printer A", 10, null, 10))
    }
}
