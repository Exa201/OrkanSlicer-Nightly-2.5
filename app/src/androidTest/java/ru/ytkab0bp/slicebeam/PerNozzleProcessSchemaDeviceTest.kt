package ru.ytkab0bp.slicebeam

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.ytkab0bp.slicebeam.slic3r.PrintConfigDef
import ru.ytkab0bp.slicebeam.fragment.ProfileToolColumnSelector

@RunWith(AndroidJUnit4::class)
class PerNozzleProcessSchemaDeviceTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun nativeOrcaMetadataMarksPerNozzleProcessOptionsAndLeavesSharedOptionsAlone() {
        val options = PrintConfigDef.getInstance().options

        assertTrue("outer_wall_speed should be an Orca per-nozzle option", options.getValue("outer_wall_speed").perNozzle)
        assertTrue("print_extruder_id should identify process columns", options.getValue("print_extruder_id").perNozzle)
        assertFalse("layer_height is shared between nozzles", options.getValue("layer_height").perNozzle)
    }

    @Test
    fun toolColumnPickerSwitchesBetweenSharedAndIndividualColumns() {
        var selectedIndex = -1
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sharedLabel = context.getString(R.string.ProfileToolColumnShared)
        val toolLabel = "T1 · Direct Drive Standard"

        composeRule.setContent {
            MaterialTheme {
                ProfileToolColumnSelector(-1, listOf("T0 · Direct Drive Standard", toolLabel)) {
                    selectedIndex = it
                }.Content(0) {}
            }
        }

        composeRule.onNodeWithText(sharedLabel).assertIsDisplayed().performClick()
        composeRule.onNodeWithText(toolLabel).performClick()
        composeRule.runOnIdle { assertEquals(1, selectedIndex) }
    }
}
