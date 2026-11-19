package ru.ytkab0bp.slicebeam.ui.navigation

/** Chooses compact bottom navigation or an expanded rail from the current app-window width. */
object AdaptiveNavigationPolicy {
    const val EXPANDED_WIDTH_DP = 600
    const val NAVIGATION_RAIL_WIDTH_DP = 80
    const val LARGE_TEXT_NAVIGATION_RAIL_WIDTH_DP = 96
    const val COMPACT_BOTTOM_LABEL_WIDTH_DP = 440
    // Shared host height for the top and bottom bars. The additional vertical
    // room keeps the one-line caption visible below the full-size multicolor icon.
    const val NAVIGATION_BAR_HEIGHT_DP = 80
    const val COMPACT_LABEL_FONT_SCALE = 1.15f

    @JvmStatic
    fun usesCompactLabels(fontScale: Float): Boolean = fontScale >= COMPACT_LABEL_FONT_SCALE

    /** Shortens labels before destinations wrap in a phone-width window. */
    @JvmStatic
    fun usesCompactBottomLabels(windowWidthDp: Int, fontScale: Float): Boolean =
        usesCompactLabels(fontScale) || (windowWidthDp in 1 until COMPACT_BOTTOM_LABEL_WIDTH_DP)

    /** Compact labels keep each destination legible in a single row on phones. */
    @JvmStatic
    fun usesCompactWorkspaceLabels(windowWidthDp: Int, fontScale: Float, isPortrait: Boolean): Boolean =
        !isPortrait || usesCompactBottomLabels(windowWidthDp, fontScale)

    @JvmStatic
    @Suppress("UNUSED_PARAMETER")
    fun navigationBarHeightDp(fontScale: Float, itemCount: Int): Int = NAVIGATION_BAR_HEIGHT_DP

    /** Wider rail space preserves system text scaling and readable destination names. */
    @JvmStatic
    fun navigationRailWidthDp(fontScale: Float): Int =
        if (fontScale >= 1.2f) LARGE_TEXT_NAVIGATION_RAIL_WIDTH_DP else NAVIGATION_RAIL_WIDTH_DP

    @JvmStatic
    fun usesBottomNavigation(windowWidthDp: Int, isPortrait: Boolean): Boolean =
        if (windowWidthDp > 0) windowWidthDp < EXPANDED_WIDTH_DP else isPortrait

    /** Uses the app window shape so split-screen and freeform windows do not inherit device shape. */
    @JvmStatic
    fun isPortraitWindow(windowWidthDp: Int, windowHeightDp: Int, isPortrait: Boolean): Boolean =
        if (windowWidthDp > 0 && windowHeightDp > 0) windowWidthDp < windowHeightDp else isPortrait

    /** Compact vertical controls are used only when the current window is both narrow and tall. */
    @JvmStatic
    fun usesCompactPortraitLayout(windowWidthDp: Int, windowHeightDp: Int, isPortrait: Boolean): Boolean {
        val portraitWindow = isPortraitWindow(windowWidthDp, windowHeightDp, isPortrait)
        return portraitWindow && usesBottomNavigation(windowWidthDp, portraitWindow)
    }
}

/** Reserves the plate viewport by keeping portrait action menus collapsed until requested. */
object WorkspaceToolPanelPolicy {
    private const val GRID_ROW_SPACING_DP = 2

    /** Must match the tile heights used by BedMenuActionsContent. */
    @JvmStatic
    fun actionTileHeightDp(fontScale: Float): Int = when {
        fontScale >= 1.5f -> (150 * 0.8f).toInt()
        fontScale >= 1.3f -> (160 * 0.8f).toInt()
        else -> (165 * 0.8f).toInt()
    }

    @JvmStatic
    fun visibleActionRows(actionCount: Int): Int =
        ((actionCount.coerceAtLeast(1) + 3) / 4).coerceIn(1, 2)

    @JvmStatic
    fun actionGridHeightDp(actionCount: Int, fontScale: Float): Int {
        val rows = visibleActionRows(actionCount)
        return actionTileHeightDp(fontScale) * rows + GRID_ROW_SPACING_DP * (rows - 1)
    }

    @JvmStatic
    fun menuHeightDp(needsTwoRows: Boolean, fontScale: Float, additionalChromeDp: Int = 0): Int {
        val base = actionGridHeightDp(if (needsTwoRows) 5 else 4, fontScale)
        return base + additionalChromeDp.coerceAtLeast(0)
    }

    /**
     * A route can briefly receive the previous menu's action count during AnimatedContent.
     * Destination-specific layout requirements must therefore win over that stale count.
     */
    @JvmStatic
    fun menuNeedsTwoRows(actionCount: Int, destinationNeedsTwoRows: Boolean): Boolean =
        destinationNeedsTwoRows || actionCount > 4

    @JvmStatic
    fun reservedHeightDp(isPortrait: Boolean, expanded: Boolean, menuHeightDp: Int): Int =
        if (isPortrait && expanded) menuHeightDp.coerceAtLeast(0) else 0
}
