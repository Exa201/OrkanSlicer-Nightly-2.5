package ru.ytkab0bp.slicebeam.fragment

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import ru.ytkab0bp.slicebeam.R

/** Compact, accessible shortcuts for the two print tools used directly from the workspace. */
@Composable
internal fun WorkspacePrintToolActions(
    onPrimeTower: () -> Unit,
    onFlushVolumes: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        DashboardPrintToolButton(
            icon = R.drawable.ic_top_reinigungsturm_standard,
            label = stringResource(R.string.PrimeTowerPositionShort),
            testTag = "workspace-header-prime-tower",
            onClick = onPrimeTower,
        )
        DashboardPrintToolButton(
            icon = R.drawable.ic_top_spuelvolumen_standard,
            label = stringResource(R.string.FlushVolumes),
            testTag = "workspace-header-flush-volumes",
            onClick = onFlushVolumes,
        )
    }
}

@Composable
private fun DashboardPrintToolButton(
    icon: Int,
    label: String,
    testTag: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(48.dp)
            .testTag(testTag)
            .semantics {
                contentDescription = label
                role = Role.Button
            }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
            Image(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(47.dp))
        }
    }
}
