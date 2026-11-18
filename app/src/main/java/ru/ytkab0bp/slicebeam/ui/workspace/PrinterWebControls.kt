package ru.ytkab0bp.slicebeam.ui.workspace

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.ytkab0bp.slicebeam.R

@Composable
internal fun PrinterWebOpenButton(
    hostConfigured: Boolean,
    connected: Boolean,
    onOpen: () -> Unit,
) {
    val baseDescription = stringResource(
        if (hostConfigured) R.string.PrinterOpenWebDescription else R.string.PrinterWebConfigureDescription,
    )
    val connectionDescription = stringResource(
        if (connected) R.string.PrinterWebConnected else R.string.PrinterWebDisconnected,
    )
    Button(
        onClick = onOpen,
        modifier = Modifier
            .fillMaxSize()
            .testTag("printer-web-connection")
            .semantics {
                contentDescription = "$baseDescription. $connectionDescription"
                stateDescription = connectionDescription
            },
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painterResource(if (connected) R.drawable.ic_printer_online else R.drawable.ic_printer_offline),
                contentDescription = null,
                modifier = Modifier.size(46.dp),
                tint = Color.Unspecified,
            )
        }
    }
}

@Composable
internal fun PrinterWebCloseButton(onClose: () -> Unit) {
    val description = stringResource(R.string.ClosePanel)
    Surface(
        modifier = Modifier
            .size(48.dp)
            .semantics { contentDescription = description }
            .clickable(role = Role.Button, onClick = onClose),
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.35f),
        contentColor = Color.White,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = "×", fontSize = 28.sp, color = Color.White)
        }
    }
}
