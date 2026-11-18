package ru.ytkab0bp.slicebeam.ui.workspace

import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.ui.OrkanUiTokens
import ru.ytkab0bp.slicebeam.utils.FilamentSlot

/** Compact palette entry point; the existing assignment dialog remains the source of truth. */
@Composable
internal fun WorkspaceFilamentButton(
    slots: List<FilamentSlot>,
    enabled: Boolean,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val occupiedSlots = slots.filter { !it.type.isNullOrBlank() }
    val label = stringResource(R.string.WorkspaceFilamentButtonDescription, occupiedSlots.size)
    Surface(
        modifier = Modifier
            .size(OrkanUiTokens.touchTarget)
            .alpha(if (enabled) 1f else 0.55f)
            .testTag("workspace-filament-button")
            .semantics { contentDescription = label }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(OrkanUiTokens.cornerRadius),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Image(
                painter = painterResource(
                    when {
                        !enabled -> R.drawable.ic_filament_synchronisierung_disabled
                        active -> R.drawable.ic_filament_synchronisierung_active
                        else -> R.drawable.ic_filament_synchronisierung
                    },
                ),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                occupiedSlots.take(4).forEach { slot ->
                    Surface(
                        modifier = Modifier.size(6.dp),
                        shape = CircleShape,
                        color = Color(slot.color),
                        content = {},
                    )
                }
                Text(
                    text = occupiedSlots.size.toString(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp,
                    lineHeight = 10.sp,
                    maxLines = 1,
                )
            }
        }
    }
}
