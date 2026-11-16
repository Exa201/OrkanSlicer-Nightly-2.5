package ru.ytkab0bp.slicebeam.fragment

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import ru.ytkab0bp.slicebeam.config.ConfigObject
import ru.ytkab0bp.slicebeam.config.ProfileSettingsRepository
import ru.ytkab0bp.slicebeam.slic3r.ConfigOptionDef

class ProfileConfigValueControllerTest {
    private val profileType = ConfigObject.PROFILE_LIST_PRINT

    @Before
    fun clearPendingEdits() {
        ProfileSettingsRepository.clearDraft(profileType)
    }

    @After
    fun leaveRepositoryClean() {
        ProfileSettingsRepository.clearDraft(profileType)
    }

    @Test
    fun readsIndexedValuesAndKeepsOrcaColorSeparator() {
        val profile = ConfigObject().apply {
            put("nozzle_diameter", "0.4,0.6")
            put("filament_colour", "#111111;#222222;#333333")
        }

        assertEquals(
            "0.6",
            ProfileConfigValueController.valueFor(
                profileType, profile, "nozzle_diameter", ConfigOptionDef.GUIType.UNDEFINED,
                "0.4", 1, ignorePending = false,
            ),
        )
        assertEquals(
            "#222222",
            ProfileConfigValueController.valueFor(
                profileType, profile, "filament_colour", ConfigOptionDef.GUIType.COLOR,
                null, 1, ignorePending = false,
            ),
        )
    }

    @Test
    fun stagesAnIndexedEditWithoutChangingOtherValues() {
        val profile = ConfigObject().apply { put("nozzle_diameter", "0.4,0.6") }

        ProfileConfigValueController.stageValue(
            profileType, profile, "nozzle_diameter", ConfigOptionDef.GUIType.UNDEFINED,
            1, "0.8",
        )

        assertEquals("0.4,0.8", ProfileSettingsRepository.pendingEntries(profileType)["nozzle_diameter"])
        assertEquals("0.8", ProfileConfigValueController.valueFor(
            profileType, profile, "nozzle_diameter", ConfigOptionDef.GUIType.UNDEFINED,
            null, 1, ignorePending = false,
        ))
        assertEquals("0.6", ProfileConfigValueController.valueFor(
            profileType, profile, "nozzle_diameter", ConfigOptionDef.GUIType.UNDEFINED,
            null, 1, ignorePending = true,
        ))
    }

    @Test
    fun perNozzleEditChangesOnlyTheSelectedProcessColumn() {
        val profile = ConfigObject().apply { put("outer_wall_speed", "42,55,61") }

        ProfileConfigValueController.stageValue(
            profileType, profile, "outer_wall_speed", ConfigOptionDef.GUIType.UNDEFINED,
            1, "48",
        )

        assertEquals("42,48,61", ProfileSettingsRepository.pendingEntries(profileType)["outer_wall_speed"])
        assertEquals("42", ProfileConfigValueController.valueFor(
            profileType, profile, "outer_wall_speed", ConfigOptionDef.GUIType.UNDEFINED,
            null, 0, ignorePending = false,
        ))
        assertEquals("48", ProfileConfigValueController.valueFor(
            profileType, profile, "outer_wall_speed", ConfigOptionDef.GUIType.UNDEFINED,
            null, 1, ignorePending = false,
        ))
        assertEquals("61", ProfileConfigValueController.valueFor(
            profileType, profile, "outer_wall_speed", ConfigOptionDef.GUIType.UNDEFINED,
            null, 2, ignorePending = false,
        ))
    }

    @Test
    fun stagesMultipleIndexedEditsWithoutDiscardingEarlierChanges() {
        val profile = ConfigObject().apply { put("nozzle_diameter", "0.4,0.6") }

        ProfileConfigValueController.stageValue(
            profileType, profile, "nozzle_diameter", ConfigOptionDef.GUIType.UNDEFINED,
            0, "0.5",
        )
        ProfileConfigValueController.stageValue(
            profileType, profile, "nozzle_diameter", ConfigOptionDef.GUIType.UNDEFINED,
            1, "0.8",
        )

        assertEquals("0.5,0.8", ProfileSettingsRepository.pendingEntries(profileType)["nozzle_diameter"])
        assertEquals("0.5", ProfileConfigValueController.valueFor(
            profileType, profile, "nozzle_diameter", ConfigOptionDef.GUIType.UNDEFINED,
            null, 0, ignorePending = false,
        ))
        assertEquals("0.8", ProfileConfigValueController.valueFor(
            profileType, profile, "nozzle_diameter", ConfigOptionDef.GUIType.UNDEFINED,
            null, 1, ignorePending = false,
        ))
    }

    @Test
    fun stagesMultipleColorSlotEditsWithoutDiscardingEarlierChanges() {
        val profile = ConfigObject().apply { put("filament_colour", "#111111;#222222") }

        ProfileConfigValueController.stageValue(
            profileType, profile, "filament_colour", ConfigOptionDef.GUIType.COLOR,
            0, "#333333",
        )
        ProfileConfigValueController.stageValue(
            profileType, profile, "filament_colour", ConfigOptionDef.GUIType.COLOR,
            1, "#444444",
        )

        assertEquals("#333333;#444444", ProfileSettingsRepository.pendingEntries(profileType)["filament_colour"])
    }

    @Test
    fun stagesMissingIndexedValueUsingTheProfileDefault() {
        val profile = ConfigObject().apply { put("nozzle_diameter", "0.4") }

        ProfileConfigValueController.stageValue(
            profileType, profile, "nozzle_diameter", ConfigOptionDef.GUIType.UNDEFINED,
            1, "0.8", defaultValue = "0.4",
        )

        assertEquals("0.4,0.8", ProfileSettingsRepository.pendingEntries(profileType)["nozzle_diameter"])
    }

    @Test
    fun stagesAnIndexedColorWhenTheStoredVectorIsShorterThanTheVisibleExtruders() {
        val profile = ConfigObject().apply { put("filament_colour", "#111111") }

        ProfileConfigValueController.stageValue(
            profileType, profile, "filament_colour", ConfigOptionDef.GUIType.COLOR,
            2, "#333333", defaultValue = "#000000;#111111;#222222",
        )

        assertEquals(
            "#111111;#111111;#333333",
            ProfileSettingsRepository.pendingEntries(profileType)["filament_colour"],
        )
    }

    @Test
    fun keepsHostTypeFallbackAndOrcaDefaultValues() {
        val profile = ConfigObject()

        assertEquals("octoprint", ProfileConfigValueController.valueFor(
            profileType, profile, "host_type", ConfigOptionDef.GUIType.UNDEFINED,
            null, -1, ignorePending = false,
        ))
        assertEquals("default", ProfileConfigValueController.valueFor(
            profileType, profile, "machine_max_speed", ConfigOptionDef.GUIType.UNDEFINED,
            "default", -1, ignorePending = false,
        ))
    }
}
