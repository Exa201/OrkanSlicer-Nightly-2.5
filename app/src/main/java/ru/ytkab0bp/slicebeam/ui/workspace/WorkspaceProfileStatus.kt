package ru.ytkab0bp.slicebeam.ui.workspace

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.ui.OrkanUiTokens
import ru.ytkab0bp.slicebeam.ui.ColorFlow
import ru.ytkab0bp.slicebeam.ui.LocalBeamLayoutFontScale

@Composable
internal fun WorkspaceProfileStatus(
    title: String,
    summary: String,
    accessibilityDescription: String,
    onOpen: () -> Unit,
    onTitleTextLayout: (TextLayoutResult) -> Unit = {},
    onSummaryTextLayout: (TextLayoutResult) -> Unit = {},
) {
    val visibleTitle = if (title.isBlank()) stringResource(R.string.WorkspaceProfilesOpenLabel) else title
    // The workspace profile chip sits beside fixed plate controls. Keep this compact
    // status at system accessibility scale, independent of the app's text-only zoom,
    // so changing that setting cannot push the renderer down.
    val currentDensity = LocalDensity.current
    val systemTextDensity = Density(
        currentDensity.density,
        LocalBeamLayoutFontScale.current.coerceAtLeast(0.1f),
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = OrkanUiTokens.touchTarget)
            .semantics { contentDescription = accessibilityDescription }
            .clickable(role = Role.Button, onClick = onOpen),
        shape = RoundedCornerShape(OrkanUiTokens.cornerRadius),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = OrkanUiTokens.controlHorizontalPadding,
            ),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                CompositionLocalProvider(LocalDensity provides systemTextDensity) {
                    Text(
                        text = visibleTitle,
                        modifier = Modifier.testTag("workspace-profile-title"),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        // The workspace chip is narrow next to the bed, plate and printer
                        // controls. Wrap the full profile name within the reserved header.
                        onTextLayout = onTitleTextLayout,
                    )
                    Text(
                        text = summary,
                        modifier = Modifier.testTag("workspace-profile-summary"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        onTextLayout = onSummaryTextLayout,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            androidx.compose.material3.Icon(
                painter = androidx.compose.ui.res.painterResource(R.drawable.dropdown_24),
                contentDescription = null,
                tint = ColorFlow.printer,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
