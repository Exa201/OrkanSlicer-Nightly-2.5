package ru.ytkab0bp.slicebeam.ui.navigation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.ui.OrkanUiTokens

data class WorkspaceNavigationItem(
    val slot: Int,
    val label: String,
    @DrawableRes val icon: Int,
    val accessibilityLabel: String,
    val compactLabel: String? = null,
    val accent: Color? = null,
    val onAccent: Color? = null,
)

/** Compact destinations keep their labels and full touch areas in both window shapes. */
@Composable
fun AdaptiveWorkspaceNavigation(
    modifier: Modifier = Modifier,
    items: List<WorkspaceNavigationItem>,
    selectedSlot: Int,
    isBottomNavigation: Boolean,
    toolsExpanded: Boolean = true,
    panelOpenDescription: String = "Tools open",
    panelClosedDescription: String = "Tools closed",
    announcePanelState: Boolean = false,
    showPanelToggleIndicator: Boolean = false,
    railTestTag: String? = null,
    onSelected: (Int) -> Unit,
) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface) {
        if (isBottomNavigation) {
            Row(Modifier.fillMaxWidth().fillMaxHeight().selectableGroup()) {
                items.forEach { item ->
                    Destination(item, item.slot == selectedSlot, Modifier.weight(1f).fillMaxHeight(), onSelected,
                        bottomLabel = item.compactLabel ?: item.label,
                        panelOpen = toolsExpanded, panelOpenDescription = panelOpenDescription,
                        panelClosedDescription = panelClosedDescription, announcePanelState = announcePanelState,
                        showPanelToggleIndicator = showPanelToggleIndicator)
                }
            }
        } else {
            val railScrollState = rememberScrollState()
            Box(
                Modifier.width(AdaptiveNavigationPolicy.navigationRailWidthDp(ru.ytkab0bp.slicebeam.ui.LocalBeamLayoutFontScale.current).dp)
                    .heightIn(max = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp)
                    .fillMaxHeight(),
            ) {
                Column(
                    Modifier.fillMaxSize()
                        .then(railTestTag?.let { Modifier.testTag(it) } ?: Modifier)
                        .padding(
                            top = if (railScrollState.canScrollBackward) 34.dp else 0.dp,
                            bottom = if (railScrollState.maxValue > 0) 34.dp else 0.dp,
                        )
                        .verticalScroll(railScrollState)
                        .selectableGroup(),
                ) {
                    items.forEach { item ->
                        Destination(item, item.slot == selectedSlot, Modifier.fillMaxWidth(), onSelected,
                            compactRail = true, panelOpen = toolsExpanded, panelOpenDescription = panelOpenDescription,
                            panelClosedDescription = panelClosedDescription, announcePanelState = announcePanelState,
                            showPanelToggleIndicator = item.slot == selectedSlot && announcePanelState)
                    }
                }
                if (railScrollState.canScrollForward) {
                    RailScrollHint(
                        modifier = Modifier.align(Alignment.BottomCenter),
                        description = stringResource(R.string.NavigationScrollMoreBelow),
                        rotation = 0f,
                    )
                }
                if (railScrollState.canScrollBackward) {
                    RailScrollHint(
                        modifier = Modifier.align(Alignment.TopCenter),
                        description = stringResource(R.string.NavigationScrollMoreAbove),
                        rotation = 180f,
                    )
                }
            }
        }
    }
}

@Composable
private fun RailScrollHint(modifier: Modifier, description: String, rotation: Float) {
    Box(
        modifier.fillMaxWidth().height(34.dp)
            .background(
                Brush.verticalGradient(
                    colors = if (rotation == 0f) {
                        listOf(Color.Transparent, MaterialTheme.colorScheme.surface)
                    } else {
                        listOf(MaterialTheme.colorScheme.surface, Color.Transparent)
                    },
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)) {
            Icon(
                painter = painterResource(R.drawable.dropdown_24),
                contentDescription = description,
                modifier = Modifier.padding(4.dp).size(18.dp).rotate(rotation),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Destination(
    item: WorkspaceNavigationItem,
    selected: Boolean,
    modifier: Modifier,
    onSelected: (Int) -> Unit,
    compactRail: Boolean = false,
    bottomLabel: String = item.label,
    panelOpen: Boolean = true,
    panelOpenDescription: String,
    panelClosedDescription: String,
    announcePanelState: Boolean,
    showPanelToggleIndicator: Boolean,
) {
    // Navigation keeps a one-line label and stable row height at large text scales.
    // The full localized name remains in the selectable's accessibility semantics.
    val displayFontScale = LocalDensity.current.fontScale.coerceAtLeast(0.1f)
    val navigationLabelSize = (14f / displayFontScale).sp
    val accent = item.accent ?: MaterialTheme.colorScheme.primary
    val onAccent = item.onAccent ?: MaterialTheme.colorScheme.onPrimary
    Column(
        modifier.heightIn(min = AdaptiveNavigationPolicy.NAVIGATION_BAR_HEIGHT_DP.dp)
            .selectable(selected = selected, role = Role.Tab,
                onClick = { onSelected(item.slot) })
            .semantics {
                contentDescription = item.accessibilityLabel
                if (selected && announcePanelState) {
                    stateDescription = if (panelOpen) panelOpenDescription else panelClosedDescription
                }
            }
            .padding(horizontal = 2.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Surface(
            shape = RoundedCornerShape(OrkanUiTokens.compactCornerRadius),
            color = if (selected) accent.copy(alpha = 0.20f)
                else androidx.compose.ui.graphics.Color.Transparent,
        ) {
            Box(Modifier.padding(horizontal = 10.dp, vertical = 3.dp).size(OrkanUiTokens.navigationIconSize)) {
                Icon(
                    painterResource(item.icon), contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    // Workspace assets are deliberately multicolor; applying a selected-state
                    // tint erased their shape cues. The accent now stays in the backing chip.
                    tint = Color.Unspecified,
                )
                if (selected && showPanelToggleIndicator) {
                    Icon(
                        painter = painterResource(R.drawable.dropdown_24),
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.BottomEnd).size(12.dp)
                            .rotate(if (panelOpen) 180f else 0f),
                        tint = if (selected) onAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Text(
            if (compactRail) item.compactLabel ?: item.label else bottomLabel,
            maxLines = 1,
            softWrap = false, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            fontSize = navigationLabelSize,
            // Text's default Material line height scales with system font size even
            // though this compact label is deliberately normalized to one line.
            lineHeight = navigationLabelSize * 1.2f,
            modifier = Modifier.fillMaxWidth(),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
        )
    }
}
