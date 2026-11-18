package ru.ytkab0bp.slicebeam.ui.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.ui.OrkanUiTokens

@Composable
internal fun PrinterWebErrorPanel(
    description: String,
    onRetry: () -> Unit,
    onOpenInBrowser: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(OrkanUiTokens.panelPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.globe_cross_outline_28),
            contentDescription = null,
            modifier = Modifier.size(42.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = description,
            modifier = Modifier.padding(vertical = 8.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val stackActions = maxWidth < 360.dp || ru.ytkab0bp.slicebeam.ui.LocalBeamLayoutFontScale.current >= 1.3f
            if (stackActions) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth().heightIn(min = OrkanUiTokens.touchTarget),
                    ) {
                        Text(stringResource(R.string.PrinterWebRetry))
                    }
                    Button(
                        onClick = onOpenInBrowser,
                        modifier = Modifier.fillMaxWidth().heightIn(min = OrkanUiTokens.touchTarget),
                    ) {
                        Text(stringResource(R.string.PrinterWebOpenInBrowser))
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onRetry,
                        modifier = Modifier.weight(1f).heightIn(min = OrkanUiTokens.touchTarget),
                    ) {
                        Text(stringResource(R.string.PrinterWebRetry))
                    }
                    Button(
                        onClick = onOpenInBrowser,
                        modifier = Modifier.weight(1f).heightIn(min = OrkanUiTokens.touchTarget),
                    ) {
                        Text(stringResource(R.string.PrinterWebOpenInBrowser))
                    }
                }
            }
        }
    }
}
