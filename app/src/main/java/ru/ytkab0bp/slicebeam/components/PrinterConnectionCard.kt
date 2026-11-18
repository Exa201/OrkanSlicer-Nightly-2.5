package ru.ytkab0bp.slicebeam.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.ui.OrkanUiTokens
import ru.ytkab0bp.slicebeam.utils.PrinterWebOriginPolicy

/** Compact route summary with a fixed, accessible entry to the configured printer web UI. */
@Composable
fun PrinterConnectionCard(
    configuredHost: String?,
    onOpenWeb: () -> Unit,
    modifier: Modifier = Modifier,
    iconRes: Int = R.drawable.menu3d_printer,
) {
    val displayHost = PrinterWebOriginPolicy.dashboardUrl(configuredHost)
    val openWebDescription = stringResource(R.string.PrinterOpenWebDescription)
    val compactAction = LocalConfiguration.current.screenWidthDp < 400 ||
        ru.ytkab0bp.slicebeam.ui.LocalBeamLayoutFontScale.current >= 1.5f
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(OrkanUiTokens.compactCornerRadius),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(29.dp),
                    tint = Color.Unspecified,
                )
                Text(
                    text = stringResource(R.string.PrinterConnectionTitle),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = displayHost ?: stringResource(R.string.PrinterWebHostMissing),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            OutlinedButton(
                onClick = onOpenWeb,
                modifier = Modifier
                    .heightIn(min = OrkanUiTokens.touchTarget)
                    .semantics {
                        contentDescription = openWebDescription
                    },
                shape = RoundedCornerShape(OrkanUiTokens.compactCornerRadius),
            ) {
                Icon(
                    painter = painterResource(R.drawable.menu3d_printer_send),
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                    tint = Color.Unspecified,
                )
                if (!compactAction) {
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.PrinterOpenWebShort), maxLines = 1)
                }
            }
            }
        }
    }
}
