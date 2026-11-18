package ru.ytkab0bp.slicebeam.ui

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color

/** Shared dimensions for workspace controls and menus. Colors and type come from MaterialTheme. */
object OrkanUiTokens {
    // ColorFlow surfaces are shared by all Compose destinations. Theme-specific
    // colors remain in MaterialTheme; these values are the dark-mode foundations.
    val darkBackground = Color(0xFF0B1020)
    val darkSurface = Color(0xFF151A2F)
    val darkSurfaceElevated = Color(0xFF202740)
    val textSecondaryDark = Color(0xFFBCC7DA)
    val workspaceCyan = Color(0xFF1DBDFF)
    val printOrange = Color(0xFFFF9C34)
    val filamentViolet = Color(0xFFB267FF)
    val printerGreen = Color(0xFF39D978)
    val appBlueViolet = Color(0xFF7C6CFF)
    val warningAmber = Color(0xFFFFBF47)
    val errorCoral = Color(0xFFFF6574)

    val touchTarget = 48.dp
    val plateCounterWidth = 72.dp
    val cornerRadius = 18.dp
    val compactCornerRadius = 14.dp
    val dialogCornerRadius = 20.dp
    val compactGap = 4.dp
    val regularGap = 8.dp
    val controlGap = 6.dp
    val controlHorizontalPadding = 8.dp
    val controlVerticalPadding = 6.dp
    val panelPadding = 16.dp
    val sheetTopCornerRadius = 26.dp
    val sheetMaxHeightFraction = 0.82f
    val workspaceSidePanelWidth = 420.dp
    val filamentRowHeight = 56.dp
    // Asset glyphs were visually undersized inside their existing controls.
    // Increase glyph bounds by ~30% without changing button hit areas.
    val menuIconSize = 34.dp
    val menuLabelSize = 13.sp
    val menuTileHeight = 62.dp
    val navigationIconSize = 39.dp
    val navigationLabelSize = 12.sp
}
