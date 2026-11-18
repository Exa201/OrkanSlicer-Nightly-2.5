package ru.ytkab0bp.slicebeam.ui.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupProfileSelectionPolicyTest {
    @Test
    fun independentMaterialLibrariesAreShownWithoutSelectingTheirPrinterVendor() {
        assertFalse(SetupProfileSelectionPolicy.shouldShowFilamentVendor(
            hasSelectedPrinter = false,
            independentLibrary = false,
        ))
        assertTrue(SetupProfileSelectionPolicy.shouldShowFilamentVendor(
            hasSelectedPrinter = true,
            independentLibrary = false,
        ))
        assertTrue(SetupProfileSelectionPolicy.shouldShowFilamentVendor(
            hasSelectedPrinter = false,
            independentLibrary = true,
        ))
    }

    @Test
    fun togglingChangesOnlyTheRequestedProfile() {
        val selected = SetupProfileSelectionPolicy.toggle(setOf("printer-a", "printer-b"), "printer-a", false)
        assertEquals(setOf("printer-b"), selected)
        assertEquals(setOf("printer-b", "printer-c"), SetupProfileSelectionPolicy.toggle(selected, "printer-c", true))
    }

    @Test
    fun selectingAllRetainsStableUniqueKeys() {
        assertEquals(listOf("filament-a", "filament-b"), SetupProfileSelectionPolicy.selectAll(listOf("filament-a", "filament-b", "filament-a")).toList())
    }

    @Test
    fun printerVariantsFallsBackToOrcaNozzleDiameters() {
        assertEquals(
            listOf("0.4", "0.2", "0.6", "0.8"),
            SetupProfileSelectionPolicy.printerVariants(null, "0.4;0.2;0.6;0.8"),
        )
    }

    @Test
    fun explicitPrinterVariantsTakePrecedenceAndAreNormalized() {
        assertEquals(
            listOf("0.6", "0.4"),
            SetupProfileSelectionPolicy.printerVariants(" 0.6 ; 0.4 ;0.6", "0.2"),
        )
    }

    @Test
    fun qidiQ2DefaultsMatchQ2SeriesButNotQ2C() {
        assertTrue(SetupProfileSelectionPolicy.matchesDefaultMaterial(
            "QIDI PLA Rapido", "QIDI PLA Rapido@Q2-Series", "Qidi Q2",
        ))
        assertFalse(SetupProfileSelectionPolicy.matchesDefaultMaterial(
            "QIDI PLA Rapido", "QIDI PLA Rapido@Q2C-Series", "Qidi Q2",
        ))
        assertTrue(SetupProfileSelectionPolicy.matchesDefaultMaterial(
            "QIDI PLA Rapido", "QIDI PLA Rapido@Q2C-Series", "Qidi Q2C",
        ))
    }

    @Test
    fun internalFilamentInheritanceProfilesAreNotSelectable() {
        assertFalse(SetupProfileSelectionPolicy.isSelectableFilament("fdm_filament_common"))
        assertTrue(SetupProfileSelectionPolicy.isSelectableFilament("Generic PLA@Q2-Series"))
    }

    @Test
    fun printerPageRequiresASelectionExceptForCustomProfiles() {
        assertFalse(SetupProfileSelectionPolicy.canContinue(useCustomProfile = false, selectedPrinterCount = 0))
        assertTrue(SetupProfileSelectionPolicy.canContinue(useCustomProfile = false, selectedPrinterCount = 1))
        assertTrue(SetupProfileSelectionPolicy.canContinue(useCustomProfile = true, selectedPrinterCount = 0))
    }
}
