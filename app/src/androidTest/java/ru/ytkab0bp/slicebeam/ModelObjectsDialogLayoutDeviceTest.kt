package ru.ytkab0bp.slicebeam

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.ytkab0bp.slicebeam.components.bed_menu.ModelObjectPartRow
import ru.ytkab0bp.slicebeam.components.bed_menu.ModelObjectRow
import ru.ytkab0bp.slicebeam.components.bed_menu.ModelObjectsDialog
import ru.ytkab0bp.slicebeam.ui.BeamComposeTheme
import java.util.concurrent.atomic.AtomicInteger

/** Protects long object/part actions against narrow screens and large system text. */
@RunWith(AndroidJUnit4::class)
class ModelObjectsDialogLayoutDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun groupedObjectActionsRemainVisibleAt320DpAnd150PercentText() {
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f, 1.5f),
                LocalConfiguration provides Configuration().apply {
                    screenWidthDp = 320
                    screenHeightDp = 720
                },
            ) {
                BeamComposeTheme {
                    ModelObjectsDialog(
                        title = "Geladene Objekte", cancelLabel = "Abbrechen",
                        assignLabel = "Filament zuweisen",
                        rows = listOf(ModelObjectRow(
                            name = "Sehr langer Baugruppenname für ein mehrteiliges Testmodell",
                            tool = 1, material = "Polyethylenterephthalat Glykol", color = 0xff1976d2.toInt(),
                            selected = true, assignEnabled = true,
                            parts = listOf(
                                ModelObjectPartRow("Langer Teilname eins", 0, "PLA", 0xff00aa88.toInt()),
                                ModelObjectPartRow("Langer Teilname zwei", 1, "PETG", 0xff1976d2.toInt()),
                            ),
                        )),
                        onDismiss = {}, onSelect = {}, onAssign = {}, onTransformPart = { _, _ -> },
                        onEditPart = { _, _ -> }, onApplySelection = {},
                    )
                }
            }
        }

        compose.onNodeWithTag("model-object-row-0").assertIsDisplayed()
        compose.onNodeWithTag("model-object-assign-0").performScrollTo().assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("model-object-group-toggle-0").performScrollTo().assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("model-object-part-row-0-0").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("model-object-transform-part-0-0").performScrollTo().assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("model-object-edit-part-0-0").performScrollTo().assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun longUnicodeObjectListFiltersAndKeepsOriginalActionIndices() {
        val selectedObject = AtomicInteger(-1)
        val rows = (0 until 80).map { index ->
            ModelObjectRow(
                name = "Modellgruppe pièce spéciale-${index.toString().padStart(3, '0')} — かなり長い名前",
                tool = index % 4,
                material = if (index % 2 == 0) "Polyethylenterephthalat Glykol" else "Polylactide",
                color = if (index % 2 == 0) 0xff1976d2.toInt() else 0xff00aa88.toInt(),
                selected = false,
                assignEnabled = true,
                parts = if (index % 10 == 0) listOf(
                    ModelObjectPartRow("Teil α $index", 0, "PLA", 0xff00aa88.toInt()),
                    ModelObjectPartRow("Teil β $index", 1, "PETG", 0xff1976d2.toInt()),
                ) else emptyList(),
            )
        }
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f, 1.5f),
                LocalConfiguration provides Configuration().apply {
                    screenWidthDp = 320
                    screenHeightDp = 720
                },
            ) {
                BeamComposeTheme {
                    ModelObjectsDialog(
                        title = "Geladene Objekte", cancelLabel = "Abbrechen",
                        assignLabel = "Filament zuweisen", rows = rows,
                        onDismiss = {}, onSelect = { selectedObject.set(it) }, onAssign = {},
                        onTransformPart = { _, _ -> }, onEditPart = { _, _ -> }, onApplySelection = {},
                    )
                }
            }
        }

        compose.onNodeWithTag("model-objects-search").performClick()
            .performTextInput("spéciale-073")
        compose.onNodeWithText("1 / 80").assertIsDisplayed()
        compose.onNodeWithTag("model-object-row-73").assertIsDisplayed().performClick()
        org.junit.Assert.assertEquals("Filtered actions must retain source-model indices", 73, selectedObject.get())
        compose.onNodeWithTag("model-objects-search").performTextClearance()
        compose.onNodeWithText("80 / 80").assertIsDisplayed()
        compose.onNodeWithTag("model-objects-dialog-rows").performScrollToIndex(79)
        compose.onNodeWithTag("model-object-assign-79").assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
    }
}
