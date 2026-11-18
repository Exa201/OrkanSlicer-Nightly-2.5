package ru.ytkab0bp.slicebeam.ui.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveNavigationPolicyTest {
    @Test
    fun largeTextUsesCompactVisibleNavigationLabels() {
        assertFalse(AdaptiveNavigationPolicy.usesCompactLabels(1.14f))
        assertTrue(AdaptiveNavigationPolicy.usesCompactLabels(1.15f))
        assertTrue(AdaptiveNavigationPolicy.usesCompactLabels(1.5f))
    }

    @Test
    fun phoneWidthUsesCompactWorkspaceLabelsButLargeWindowsKeepFullLabels() {
        assertTrue(AdaptiveNavigationPolicy.usesCompactBottomLabels(393, 1f))
        assertFalse(AdaptiveNavigationPolicy.usesCompactBottomLabels(480, 1f))
        assertTrue(AdaptiveNavigationPolicy.usesCompactBottomLabels(720, 1.3f))
    }

    @Test
    fun largeTextKeepsCompactLabelsInTwoRowWorkspaceNavigation() {
        assertTrue(AdaptiveNavigationPolicy.usesCompactWorkspaceLabels(320, 1.5f, true))
        assertTrue(AdaptiveNavigationPolicy.usesCompactWorkspaceLabels(400, 1.5f, true))
        assertFalse(AdaptiveNavigationPolicy.usesCompactWorkspaceLabels(480, 1f, true))
        assertTrue(AdaptiveNavigationPolicy.usesCompactWorkspaceLabels(720, 1f, false))
    }

    @Test
    fun bottomNavigationAlwaysRemainsOneRowAndKeepsStableHeight() {
        assertEquals(80, AdaptiveNavigationPolicy.navigationBarHeightDp(1.3f, 6))
        assertEquals(80, AdaptiveNavigationPolicy.navigationBarHeightDp(1.5f, 6))
        assertEquals(80, AdaptiveNavigationPolicy.navigationBarHeightDp(1f, 6))
    }

    @Test
    fun compactLabelsAreUsedAtPhoneWidthsAndLargeTextScales() {
        assertTrue(AdaptiveNavigationPolicy.usesCompactBottomLabels(320, 1f))
        assertTrue(AdaptiveNavigationPolicy.usesCompactBottomLabels(400, 1f))
        assertTrue(AdaptiveNavigationPolicy.usesCompactBottomLabels(480, 1.5f))
        assertFalse(AdaptiveNavigationPolicy.usesCompactBottomLabels(480, 1f))
    }

    @Test
    fun expandedNavigationRailAdaptsWidthForLargeSystemText() {
        assertEquals(80, AdaptiveNavigationPolicy.navigationRailWidthDp(1f))
        assertEquals(96, AdaptiveNavigationPolicy.navigationRailWidthDp(1.2f))
        assertEquals(96, AdaptiveNavigationPolicy.navigationRailWidthDp(1.5f))
    }

    @Test
    fun compactWindowUsesBottomNavigationRegardlessOfOrientation() {
        assertTrue(AdaptiveNavigationPolicy.usesBottomNavigation(599, true))
        assertTrue(AdaptiveNavigationPolicy.usesBottomNavigation(599, false))
    }

    @Test
    fun expandedWindowUsesRailNavigationRegardlessOfOrientation() {
        assertFalse(AdaptiveNavigationPolicy.usesBottomNavigation(600, true))
        assertFalse(AdaptiveNavigationPolicy.usesBottomNavigation(600, false))
        assertFalse(AdaptiveNavigationPolicy.usesBottomNavigation(840, true))
    }

    @Test
    fun missingWindowWidthFallsBackToDeviceOrientation() {
        assertTrue(AdaptiveNavigationPolicy.usesBottomNavigation(0, true))
        assertFalse(AdaptiveNavigationPolicy.usesBottomNavigation(-1, false))
    }

    @Test
    fun windowShapeOverridesDeviceOrientationForResizableWindows() {
        assertFalse(AdaptiveNavigationPolicy.isPortraitWindow(720, 540, true))
        assertTrue(AdaptiveNavigationPolicy.isPortraitWindow(540, 720, false))
    }

    @Test
    fun missingWindowBoundsFallBackToDeviceOrientation() {
        assertTrue(AdaptiveNavigationPolicy.isPortraitWindow(0, 720, true))
        assertFalse(AdaptiveNavigationPolicy.isPortraitWindow(600, 0, false))
    }

    @Test
    fun compactPortraitLayoutRequiresNarrowAndTallWindow() {
        assertTrue(AdaptiveNavigationPolicy.usesCompactPortraitLayout(540, 720, false))
        assertFalse(AdaptiveNavigationPolicy.usesCompactPortraitLayout(720, 900, true))
        assertFalse(AdaptiveNavigationPolicy.usesCompactPortraitLayout(540, 480, true))
    }
}
