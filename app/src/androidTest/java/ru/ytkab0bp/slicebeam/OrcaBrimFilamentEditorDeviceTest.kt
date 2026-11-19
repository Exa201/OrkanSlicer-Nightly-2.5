package ru.ytkab0bp.slicebeam

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.ytkab0bp.slicebeam.components.bed_menu.ModelObjectProcessOverridesDialog
import ru.ytkab0bp.slicebeam.ui.BeamComposeTheme

@RunWith(AndroidJUnit4::class)
class OrcaBrimFilamentEditorDeviceTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun objectSettingsPersistSelectedBrimFilamentUsingOrcaIndex() {
        var applied: Map<String, String>? = null
        compose.setContent {
            BeamComposeTheme {
                ModelObjectProcessOverridesDialog(
                    objectName = "Fixture",
                    initialValues = mapOf("brim_filament_id" to ""),
                    brimFilamentOptions = listOf("1" to "T0 · PLA", "2" to "T1 · PETG"),
                    onDismiss = {},
                    onApply = { applied = it },
                )
            }
        }

        compose.onNodeWithTag("model-object-process-brim-filament").performClick()
        compose.onNodeWithTag("model-object-process-brim-filament-2").performClick()
        compose.onNodeWithTag("model-object-process-settings-apply").performClick()
        compose.runOnIdle {
            assertEquals("2", applied?.get("brim_filament_id"))
        }
    }
}
