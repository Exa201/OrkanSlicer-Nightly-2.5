package ru.ytkab0bp.slicebeam.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.navigation.MobileNavigationPolicy
import ru.ytkab0bp.slicebeam.ui.BeamComposeTheme

@RunWith(AndroidJUnit4::class)
class AdaptiveWorkspaceNavigationDeviceTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun compactNavigationKeepsAllDestinationsReachableAndReportsSelection() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val selected = mutableIntStateOf(WorkspaceDestination.PROJECT.ordinal)
        val items = WorkspaceDestination.visibleNavigationDestinations.map { destination ->
            WorkspaceNavigationItem(
                slot = destination.ordinal,
                label = context.getString(destination.navigationTitleRes),
                icon = destination.iconRes,
                selectedIcon = destination.selectedIconRes,
                accessibilityLabel = context.getString(destination.navigationTitleRes),
            )
        }

        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                BeamComposeTheme {
                    Box(Modifier.width(320.dp)) {
                        AdaptiveWorkspaceNavigation(
                            items = items,
                            selectedSlot = selected.intValue,
                            isBottomNavigation = true,
                            onSelected = { selected.intValue = it },
                        )
                    }
                }
            }
        }

        val editLabel = context.getString(WorkspaceDestination.EDIT.navigationTitleRes)
        WorkspaceDestination.visibleNavigationDestinations.forEach { destination ->
            compose.onNodeWithContentDescription(context.getString(destination.navigationTitleRes))
                .assertIsDisplayed()
                .assertHeightIsAtLeast(48.dp)
        }
        compose.onNodeWithContentDescription(context.getString(WorkspaceDestination.CAMERA.navigationTitleRes)).assertDoesNotExist()
        compose.onNodeWithContentDescription(editLabel).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(WorkspaceDestination.EDIT.ordinal, selected.intValue) }
    }

    @Test
    fun largeTextKeepsAllDestinationsOnOneStableRowAndPreservesAccessibilityLabels() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val items = WorkspaceDestination.visibleNavigationDestinations.map { destination ->
            WorkspaceNavigationItem(
                slot = destination.ordinal,
                label = context.getString(destination.navigationTitleRes),
                icon = destination.iconRes,
                selectedIcon = destination.selectedIconRes,
                accessibilityLabel = context.getString(destination.navigationTitleRes),
                compactLabel = context.getString(
                    if (destination == WorkspaceDestination.TOOLS) R.string.MenuOptionsCompact
                    else destination.titleRes,
                ),
            )
        }

        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.5f)) {
                BeamComposeTheme {
                    Box(Modifier.width(320.dp).height(56.dp)) {
                        AdaptiveWorkspaceNavigation(
                            items = items,
                            selectedSlot = WorkspaceDestination.PROJECT.ordinal,
                            isBottomNavigation = true,
                            onSelected = {},
                        )
                    }
                }
            }
        }

        val destinationNodes = WorkspaceDestination.visibleNavigationDestinations.map { destination ->
            compose.onNodeWithContentDescription(context.getString(destination.navigationTitleRes))
                .assertIsDisplayed()
                .assertHeightIsAtLeast(48.dp)
        }
        val firstTop = destinationNodes.first().fetchSemanticsNode().boundsInRoot.top
        destinationNodes.drop(1).forEach { node ->
            assertEquals("navigation items must share one row", firstTop,
                node.fetchSemanticsNode().boundsInRoot.top, 0.5f)
        }
        items.forEach { item ->
            val label = requireNotNull(item.compactLabel)
            val bounds = compose.onNodeWithText(label).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue("large-text navigation label '$label' must stay inside its 56 dp row", bounds.top >= 0f && bounds.bottom <= 56f)
        }
    }

    @Test
    fun globalNavigationStaysInOneRowAtPhoneWidthAndLargeFontScale() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val labels = mapOf(
            MobileNavigationPolicy.PREVIEW_SLOT to (R.string.NavigationPreviewCompact to R.string.NavigationPreview),
            MobileNavigationPolicy.PRINT_SLOT to (R.string.NavigationPrintCompact to R.string.SlotPrintConfig),
            MobileNavigationPolicy.FILAMENT_SLOT to (R.string.NavigationFilamentCompact to R.string.NavigationFilament),
            MobileNavigationPolicy.PRINTER_SLOT to (R.string.NavigationPrinterCompact to R.string.SlotPrinterConfig),
            MobileNavigationPolicy.APP_SLOT to (R.string.NavigationAppCompact to R.string.NavigationApp),
        )
        val items = MobileNavigationPolicy.destinations().map { slot ->
            val (compact, full) = requireNotNull(labels[slot])
            WorkspaceNavigationItem(
                slot = slot,
                label = context.getString(compact),
                icon = R.drawable.view_in_ar_24,
                accessibilityLabel = context.getString(full),
            )
        }

        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.5f)) {
                BeamComposeTheme {
                    Box(Modifier.width(320.dp).height(56.dp)) {
                        AdaptiveWorkspaceNavigation(
                            items = items,
                            selectedSlot = MobileNavigationPolicy.PREVIEW_SLOT,
                            isBottomNavigation = true,
                            onSelected = {},
                        )
                    }
                }
            }
        }

        val destinationNodes = items.map { item ->
            compose.onNodeWithContentDescription(item.accessibilityLabel)
                .assertIsDisplayed()
                .assertHeightIsAtLeast(48.dp)
        }
        val firstTop = destinationNodes.first().fetchSemanticsNode().boundsInRoot.top
        destinationNodes.drop(1).forEach { node ->
            assertEquals("global navigation items must remain on one row", firstTop,
                node.fetchSemanticsNode().boundsInRoot.top, 0.5f)
        }
    }

    @Test
    fun shortNavigationRailShowsScrollHintAndCanReachLastDestination() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val selected = mutableIntStateOf(WorkspaceDestination.PROJECT.ordinal)
        val items = WorkspaceDestination.visibleNavigationDestinations.map { destination ->
            WorkspaceNavigationItem(
                slot = destination.ordinal,
                label = context.getString(destination.navigationTitleRes),
                icon = destination.iconRes,
                selectedIcon = destination.selectedIconRes,
                accessibilityLabel = context.getString(destination.navigationTitleRes),
            )
        }

        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) {
                BeamComposeTheme {
                    Box(Modifier.width(100.dp).height(180.dp)) {
                        AdaptiveWorkspaceNavigation(
                            items = items,
                            selectedSlot = selected.intValue,
                            isBottomNavigation = false,
                            onSelected = { selected.intValue = it },
                        )
                    }
                }
            }
        }

        val lastDestination = WorkspaceDestination.SLICE
        val lastLabel = context.getString(lastDestination.navigationTitleRes)
        compose.onNodeWithContentDescription(context.getString(R.string.NavigationScrollMoreBelow))
            .assertIsDisplayed()

        compose.onNodeWithContentDescription(lastLabel)
            .performScrollTo()
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .assertIsNotSelected()
            .performClick()
        compose.onNodeWithContentDescription(lastLabel).assertIsSelected()
        compose.runOnIdle { assertEquals(lastDestination.ordinal, selected.intValue) }
        compose.onNodeWithContentDescription(context.getString(R.string.NavigationScrollMoreAbove))
            .assertIsDisplayed()
    }
}
