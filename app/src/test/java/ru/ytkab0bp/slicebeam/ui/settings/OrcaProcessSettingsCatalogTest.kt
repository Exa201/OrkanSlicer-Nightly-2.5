package ru.ytkab0bp.slicebeam.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrcaProcessSettingsCatalogTest {
    @Test
    fun processCategoriesUseTheSliceBeamOrderAndGermanLabels() {
        val categories = OrcaProcessSettingsCatalog.categories()
        assertEquals(
            listOf(
                "Layers and perimeters",
                "Top/bottom layers",
                "Infill",
                "Skirt and brim",
                "Support material",
                "Speed",
                "Cooling",
                "Multiple Extruders",
                "Advanced",
                "Output options",
            ),
            categories.map { it.title },
        )
        assertEquals(
            listOf(
                "Schichten und Wandungen",
                "Obere / untere Schichten",
                "Füllung",
                "Haftung, Skirt und Brim",
                "Stützstrukturen",
                "Geschwindigkeit",
                "Kühlung",
                "Mehrere Extruder",
                "Erweitert",
                "Ausgabeoptionen",
            ),
            categories.map { category ->
                OrcaProfileGroupLocalizer.mobileGroup(category.title, "de")
            },
        )
    }

    @Test
    fun eachProcessOptionHasOnePresentationEntry() {
        val keys = OrcaProcessSettingsCatalog.categories()
            .flatMap { category -> category.sections.flatMap { it.keys } }
        val duplicates = keys.groupingBy { it }.eachCount().filterValues { it > 1 }

        assertTrue("Repeated process options: $duplicates", duplicates.isEmpty())
        assertTrue(OrcaProcessSettingsCatalog.hasUniqueProcessKeys())
    }

    @Test
    fun wipeInwardOptionsAreVisibleAndRoutedToProcessProfiles() {
        val seam = OrcaProcessSettingsCatalog.categories()
            .single { it.title == "Layers and perimeters" }
            .sections.single { it.title == "Seam" }
        assertTrue("Wipe Inward toggle must be configurable", "wipe_inward" in seam.keys)
        assertTrue("Wipe Inward distance must be configurable", "wipe_inward_distance" in seam.keys)
    }

    @Test
    fun filamentAndBedValuesAreNotPresentedAsProcessSettings() {
        val processKeys = OrcaProcessSettingsCatalog.processKeys().toSet()
        val filamentOnlyKeys = setOf(
            "enable_pressure_advance",
            "pressure_advance",
            "adaptive_pressure_advance",
            "adaptive_pressure_advance_model",
            "adaptive_pressure_advance_overhangs",
            "adaptive_pressure_advance_bridges",
            "hot_plate_temp",
            "hot_plate_temp_initial_layer",
        )

        filamentOnlyKeys.forEach { key -> assertFalse("$key must stay in its owning profile", key in processKeys) }
        assertTrue("Process catalog should not be empty", processKeys.isNotEmpty())
    }

    @Test
    fun beamInterlockingOptionsAreGroupedAndGermanLabelIsAvailable() {
        val options = setOf(
            "interlocking_beam",
            "interlocking_beam_width",
            "interlocking_orientation",
            "interlocking_beam_layer_count",
            "interlocking_depth",
            "interlocking_boundary_avoidance",
        )
        val categories = OrcaProcessSettingsCatalog.categories()
        val multimaterial = categories.single { it.title == "Multiple Extruders" }
        val beamSection = multimaterial.sections.single { it.title == "Beam interlocking" }

        assertTrue("Every Orca beam-interlocking option should be visible", beamSection.keys.toSet() == options)
        assertTrue("Beam interlocking settings must be in the process profile", options.all { it in OrcaProcessSettingsCatalog.processKeys() })
        assertTrue(
            "The section heading should be translated for German users",
            OrcaProfileGroupLocalizer.mobileGroup("Beam interlocking", "de") == "Mehrmaterial-Verzahnung",
        )
    }

    @Test
    fun supportAndOverhangFlowControlsAreVisibleInTheirOwningGroups() {
        val categories = OrcaProcessSettingsCatalog.categories()
        val support = categories.single { it.title == "Support material" }
        val supportFlow = support.sections.single { it.title == "Support flow" }
        val basicSupport = support.sections.single { it.title == "Support" }
        val advancedSupport = support.sections.single { it.title == "Advanced" }
        val overhangs = categories.single { it.title == "Layers and perimeters" }.sections.single { it.title == "Overhangs" }

        assertTrue(supportFlow.keys.toSet() == setOf("support_flow_ratio", "support_interface_flow_ratio"))
        assertTrue("support_threshold_overlap" in basicSupport.keys)
        assertTrue("support_bottom_interface_spacing" in advancedSupport.keys)
        assertTrue("overhang_flow_ratio" in overhangs.keys)
        assertTrue(
            "The support-flow group should be translated for German users",
            OrcaProfileGroupLocalizer.mobileGroup("Support flow", "de") == "Stützfluss",
        )
    }

    @Test
    fun firstLayerTravelJerkIsGroupedWithItsBaseTravelSetting() {
        val speed = OrcaProcessSettingsCatalog.categories().single { it.title == "Speed" }
        val jerk = speed.sections.single { it.title == "Jerk (XY)" }

        assertTrue("Travel jerk should be available", "travel_jerk" in jerk.keys)
        assertTrue("First-layer travel jerk should use Orca's native profile option", "initial_layer_travel_jerk" in jerk.keys)
        assertTrue("The first-layer override must be unique and routed", "initial_layer_travel_jerk" in OrcaProcessSettingsCatalog.processKeys())
    }

    @Test
    fun advancedIroningControlsUseProcessProfileWithoutDuplicatingFilamentFanSpeed() {
        val processKeys = OrcaProcessSettingsCatalog.processKeys().toSet()
        val ironing = OrcaProcessSettingsCatalog.categories()
            .single { it.title == "Layers and perimeters" }
            .sections.single { it.title == "Ironing" }
        val filamentKeys = OrcaFilamentSettingsCatalog.filamentKeys().toSet()

        assertTrue("Fixed ironing angle should be available in the process profile", "ironing_angle_fixed" in ironing.keys)
        assertTrue("Ironing expansion should be available in the process profile", "ironing_expansion" in ironing.keys)
        assertTrue("Ironing fan speed remains owned by the filament profile", "ironing_fan_speed" in filamentKeys)
        assertFalse("Filament-owned ironing fan speed must not be duplicated", "ironing_fan_speed" in processKeys)
        assertTrue("The German group label should remain available", OrcaProfileGroupLocalizer.mobileGroup("Ironing", "de") == "Glätten")
    }

    @Test
    fun bridgeWidthAndDirectionStayTogetherInTheProcessProfile() {
        val bridging = OrcaProcessSettingsCatalog.categories()
            .single { it.title == "Layers and perimeters" }
            .sections.single { it.title == "Bridging" }

        assertTrue(bridging.keys.containsAll(listOf(
            "bridge_line_width", "bridge_flow", "bridge_density",
            "bridge_angle", "internal_bridge_angle", "relative_bridge_angle",
        )))
        assertTrue(OrcaProcessSettingsCatalog.hasUniqueProcessKeys())
    }

    @Test
    fun supportedPrecisionAndInfillOptionsAreExposedInTheirExistingGroups() {
        val categories = OrcaProcessSettingsCatalog.categories()
        val precision = categories.single { it.title == "Layers and perimeters" }
            .sections.single { it.title == "Precision" }
        val infill = categories.single { it.title == "Infill" }
            .sections.single { it.title == "Infill" }

        assertTrue("Elephant-foot layer density uses the native expert option", "elefant_foot_layers_density" in precision.keys)
        assertTrue("Infill locking remains in the Orca infill settings", "infill_lock_depth" in infill.keys)
        assertTrue("Hilbert smoothing must be editable in the infill settings", "sparse_infill_smooth_factor" in infill.keys)
        assertTrue(OrcaProcessSettingsCatalog.hasUniqueProcessKeys())
    }

    @Test
    fun paintPenetrationControlsAreExposedWithTopAndBottomShellSettings() {
        val section = OrcaProcessSettingsCatalog.categories()
            .single { it.title == "Top/bottom layers" }
            .sections.single { it.title == "Top/bottom layers" }

        assertTrue(section.keys.containsAll(listOf(
            "top_color_penetration_layers",
            "bottom_color_penetration_layers",
        )))
        assertTrue(OrcaProcessSettingsCatalog.hasUniqueProcessKeys())
    }

    @Test
    fun compensatedOutlineBrimControlIsExposedInBrimSection() {
        val brim = OrcaProcessSettingsCatalog.categories()
            .single { it.title == "Skirt and brim" }
            .sections.single { it.title == "Brim" }

        assertTrue("Orca's EFC-aware brim option must be available with other brim controls",
            "brim_use_efc_outline" in brim.keys)
        assertTrue(OrcaProcessSettingsCatalog.hasUniqueProcessKeys())
    }

    @Test
    fun volumetricFeatureSpeedsAreExposedWithTheirEnableSwitch() {
        val categories = OrcaProcessSettingsCatalog.categories()
        val speed = categories.single { it.title == "Speed" }
        val speedSection = speed.sections.single { it.title == "Speed" }
        val flowSection = speed.sections.single { it.title == "Volumetric feature speeds" }

        assertTrue("Feature flows must stay opt-in", "enable_volumetric_speeds" in speedSection.keys)
        assertTrue(flowSection.keys.containsAll(listOf(
            "outer_wall_volumetric_flow", "inner_wall_volumetric_flow", "sparse_infill_volumetric_flow",
            "internal_solid_infill_volumetric_flow", "top_surface_volumetric_flow", "gap_infill_volumetric_flow",
            "support_volumetric_flow", "support_interface_volumetric_flow", "bridge_volumetric_flow",
            "internal_bridge_volumetric_flow", "initial_layer_volumetric_flow", "initial_layer_infill_volumetric_flow",
        )))
        assertTrue(OrcaProcessSettingsCatalog.hasUniqueProcessKeys())
    }
}
