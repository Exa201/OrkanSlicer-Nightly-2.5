package ru.ytkab0bp.slicebeam.ui.workspace

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.ui.OrkanUiTokens

@Composable
internal fun WorkspaceProfileStatus(
    title: String,
    summary: String,
    accessibilityDescription: String,
    onOpen: () -> Unit,
) {
    val visibleTitle = if (title.isBlank()) stringResource(R.string.WorkspaceProfilesOpenLabel) else title
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = OrkanUiTokens.touchTarget)
            .semantics { contentDescription = accessibilityDescription }
            .clickable(role = Role.Button, onClick = onOpen),
        shape = RoundedCornerShape(OrkanUiTokens.cornerRadius),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = OrkanUiTokens.controlHorizontalPadding,
            ),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.vk_printer_outline_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(OrkanUiTokens.menuIconSize),
            )
            Spacer(Modifier.width(OrkanUiTokens.controlGap))
            Column(Modifier.weight(1f)) {
                Text(
                    text = visibleTitle,
                    modifier = Modifier.testTag("workspace-profile-title"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    // The caller supplies the concise printer model name; two fixed
                    // lines keep it readable without changing the workspace header.
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    text = summary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp,
                    lineHeight = 9.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                painter = painterResource(R.drawable.dropdown_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(23.dp),
            )
        }
    }
}
