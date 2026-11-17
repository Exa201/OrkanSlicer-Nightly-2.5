package ru.ytkab0bp.slicebeam.ui.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrcaFilamentSettingsCatalogTest {
    @Test
    fun eachFilamentOptionHasOnePresentationEntry() {
        val keys = OrcaFilamentSettingsCatalog.categories()
            .flatMap { category -> category.sections.flatMap { it.keys } }
        val duplicates = keys.groupingBy { it }.eachCount().filterValues { it > 1 }

        assertTrue("Repeated filament options: $duplicates", duplicates.isEmpty())
        assertTrue(OrcaFilamentSettingsCatalog.hasUniqueFilamentKeys())
        assertTrue(OrcaFilamentSettingsCatalog.categories().all { category ->
            category.title.isNotBlank() && category.sections.isNotEmpty() &&
                category.sections.all { section -> section.title.isNotBlank() && section.keys.isNotEmpty() }
        })
    }

    @Test
    fun filamentOnlyValuesStayOutOfTheProcessCatalog() {
        val filamentKeys = OrcaFilamentSettingsCatalog.filamentKeys().toSet()
        val processKeys = OrcaProcessSettingsCatalog.processKeys().toSet()
        val filamentOnly = setOf(
            "enable_pressure_advance",
            "pressure_advance",
            "adaptive_pressure_advance",
            "adaptive_pressure_advance_model",
            "adaptive_pressure_advance_overhangs",
            "adaptive_pressure_advance_bridges",
            "hot_plate_temp",
            "hot_plate_temp_initial_layer",
            "activate_chamber_temp_control",
            "enable_overhang_bridge_fan",
            "dont_slow_down_outer_wall",
            "additional_fan_full_speed_layer",
            "support_material_interface_fan_speed",
            "ironing_fan_speed",
        )

        filamentOnly.forEach { key ->
            assertTrue("$key should be shown for filament", key in filamentKeys)
            assertFalse("$key must not be duplicated in the process profile", key in processKeys)
        }
    }

    @Test
    fun printerExtruderToolchangeValuesAreNotShownAsEmptyFilamentControls() {
        val filamentKeys = OrcaFilamentSettingsCatalog.filamentKeys().toSet()

        assertFalse("retract_length_toolchange belongs to the printer extruder", "retract_length_toolchange" in filamentKeys)
        assertFalse("retract_restart_extra_toolchange belongs to the printer extruder", "retract_restart_extra_toolchange" in filamentKeys)
    }

    @Test
    fun chamberAndCoolingOverridesAreGroupedByTheirFilamentPurpose() {
        val categories = OrcaFilamentSettingsCatalog.categories()
        val filament = categories.single { it.title == "Filament" }
        val cooling = categories.single { it.title == "Cooling" }

        assertTrue("Chamber control belongs beside the filament chamber temperature", "activate_chamber_temp_control" in filament.sections.single { it.title == "Chamber" }.keys)
        assertTrue("Outer-wall cooling behavior belongs in the cooling controls", "dont_slow_down_outer_wall" in cooling.sections.single { it.title == "Enable" }.keys)
        assertTrue("Overhang, support and ironing fans belong in fan-speed controls", setOf("enable_overhang_bridge_fan", "support_material_interface_fan_speed", "ironing_fan_speed").all { it in cooling.sections.single { section -> section.title == "Fan speed" }.keys })
        assertTrue("Auxiliary-fan ramp-up belongs in the auxiliary-fan group", "additional_fan_full_speed_layer" in cooling.sections.single { it.title == "Additional / auxiliary fan" }.keys)
    }

    @Test
    fun plateTemperaturesAreExposedAndDeveloperOnlyControlsStayHidden() {
        val filamentKeys = OrcaFilamentSettingsCatalog.filamentKeys().toSet()
        val plateTemperatures = setOf("cool_plate", "eng_plate", "hot_plate", "textured_plate", "textured_cool_plate", "supertack_plate")
            .flatMap { surface -> listOf("${surface}_temp", "${surface}_temp_initial_layer") }

        plateTemperatures.forEach { key ->
            assertTrue("$key must be independently editable for Orca's selected bed type", key in filamentKeys)
        }
        val filamentCategory = OrcaFilamentSettingsCatalog.categories().first { it.title == "Filament" }
        val plateSections = filamentCategory.sections.filter { section ->
            section.keys.any { it in plateTemperatures }
        }
        assertEquals(6, plateSections.size)
        assertTrue("Every bed temperature must be grouped under a surface name", plateSections.all { it.title.isNotBlank() })
        assertFalse(
            "The native adaptive volumetric-speed option is marked experimental and incomplete",
            "filament_adaptive_volumetric_speed" in filamentKeys,
        )
    }

    @Test
    fun externalVolumetricLimitIsEditableInFilamentPrintSpeedSettings() {
        val section = OrcaFilamentSettingsCatalog.categories()
            .single { it.title == "Advanced" }
            .sections.single { it.title == "Print speed override" }
        assertTrue("The visible-feature flow ceiling belongs to its filament profile",
            "filament_max_external_volumetric_speed" in section.keys)
    }
}
