package ru.ytkab0bp.slicebeam.manufacturing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class AdvancedManufacturingFeatureTest {
    @Test
    fun registryContainsOnlyTheElevenRequestedStableIds() {
        assertEquals(
            listOf(1, 2, 4, 5, 6, 11, 14, 16, 17, 18, 20),
            AdvancedManufacturingFeature.all.map { it.id },
        )
        assertEquals(AdvancedManufacturingFeature.all.size, AdvancedManufacturingFeature.all.map { it.key }.toSet().size)
    }

    @Test
    fun lookupAndGroupingAreStable() {
        assertEquals(AdvancedManufacturingFeature.V_CARVE_GENERATOR, AdvancedManufacturingFeature.byId(11))
        assertNull(AdvancedManufacturingFeature.byId(3))
        assertTrue(AdvancedManufacturingFeature.inGroup(ManufacturingFeatureGroup.MODEL_EDITING)
            .contains(AdvancedManufacturingFeature.INTELLIGENT_MARKING))
        assertNotNull(AdvancedManufacturingFeature.NON_PLANAR_SURFACE_PRINTING)
    }

    @Test
    fun featuresDefaultOffAndNonPlanarIsMarkedExperimental() {
        assertTrue(AdvancedManufacturingFeature.all.none { it.enabledByDefault })
        assertEquals(ManufacturingFeatureMaturity.EXPERIMENTAL, AdvancedManufacturingFeature.NON_PLANAR_SURFACE_PRINTING.maturity)
        assertEquals(ManufacturingFeatureAvailability.EXPERIMENTAL, AdvancedManufacturingFeature.NON_PLANAR_SURFACE_PRINTING.availability(enabled = true, implemented = true))
        assertTrue(AdvancedManufacturingFeature.SMART_ASSEMBLY_CUTTER.prerequisites.isNotEmpty())
        assertEquals(ManufacturingFeatureAvailability.NOT_IMPLEMENTED, AdvancedManufacturingFeature.SMART_ASSEMBLY_CUTTER.availability(enabled = true))
        assertEquals(ManufacturingFeatureAvailability.DISABLED, AdvancedManufacturingFeature.SMART_ASSEMBLY_CUTTER.availability(enabled = false, implemented = true))
        assertEquals(ManufacturingFeatureAvailability.UNSUPPORTED, AdvancedManufacturingFeature.SMART_ASSEMBLY_CUTTER.availability(enabled = true, supported = false))
        assertEquals(ManufacturingFeatureAvailability.MISSING_PREREQUISITES, AdvancedManufacturingFeature.SMART_ASSEMBLY_CUTTER.availability(enabled = true, prerequisitesAvailable = false))
        assertEquals(ManufacturingFeatureAvailability.READY, AdvancedManufacturingFeature.SMART_ASSEMBLY_CUTTER.availability(enabled = true, implemented = true))
    }

    @Test
    fun preferenceKeysAreStableAndDefaultOff() {
        val stored = mutableMapOf<String, Boolean>()
        val feature = AdvancedManufacturingFeature.SMART_ASSEMBLY_CUTTER
        assertFalse(AdvancedManufacturingFeaturePreferences.isEnabled(feature) { key, default -> stored[key] ?: default })
        AdvancedManufacturingFeaturePreferences.setEnabled(feature, true) { key, value -> stored[key] = value }
        assertTrue(AdvancedManufacturingFeaturePreferences.isEnabled(feature) { key, default -> stored[key] ?: default })
        assertEquals("advanced_manufacturing_feature_2_smart_assembly_cutter", AdvancedManufacturingFeaturePreferences.preferenceKey(feature))
        assertFalse(AdvancedManufacturingFeaturePreferences.isEnabled(AdvancedManufacturingFeature.LASER_CONVERTER) { key, default -> stored[key] ?: default })
    }
}
