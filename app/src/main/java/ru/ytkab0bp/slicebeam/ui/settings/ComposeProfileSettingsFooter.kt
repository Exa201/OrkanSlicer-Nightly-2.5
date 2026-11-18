package ru.ytkab0bp.slicebeam.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.ytkab0bp.slicebeam.config.ProfileNameValidator
import ru.ytkab0bp.slicebeam.R

/** Adaptive Compose controls and dialogs for applying or discarding profile edits. */
@Composable
fun ComposeProfileSettingsFooter(
    portrait: Boolean,
    resetEnabled: Boolean,
    useFilamentAssets: Boolean = false,
    usePrinterAssets: Boolean = false,
    currentTitle: () -> String,
    pendingSummary: () -> String,
    onApply: (String) -> Unit,
    onReset: () -> Unit,
) {
    var showSaveDialog by rememberSaveable { mutableStateOf(false) }
    var showResetDialog by rememberSaveable { mutableStateOf(false) }
    val resetAlpha by animateFloatAsState(
        targetValue = if (resetEnabled) 1f else 0.45f,
        label = "profile-reset-enabled",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 12.dp,
                top = if (portrait) 4.dp else 12.dp,
                end = 12.dp,
                bottom = if (portrait) 4.dp else 18.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = { showSaveDialog = true },
            modifier = Modifier.weight(1f).heightIn(min = if (portrait) 44.dp else 48.dp),
            contentPadding = PaddingValues(
                horizontal = if (portrait) 16.dp else 21.dp,
                vertical = if (portrait) 8.dp else 12.dp,
            ),
        ) {
            if (useFilamentAssets || usePrinterAssets) Icon(
                painter = painterResource(
                    if (usePrinterAssets) R.drawable.printerprofile_speichern else R.drawable.ic_speichern,
                ),
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp).size(20.dp),
                tint = Color.Unspecified,
            )
            Text(stringResource(R.string.SettingsSave))
        }
        IconButton(
            onClick = { showResetDialog = true },
            enabled = resetEnabled,
            modifier = Modifier.alpha(resetAlpha),
        ) {
            if (useFilamentAssets || usePrinterAssets) Icon(
                painter = painterResource(
                    if (usePrinterAssets) {
                        PrinterProfileCategoryAssets.stateIcon(
                            R.drawable.printerprofile_zuruecksetzen,
                            active = false,
                            enabled = resetEnabled,
                        )
                    } else {
                        FilamentProfileCategoryAssets.stateIcon(R.drawable.ic_zuruecksetzen, active = false, enabled = resetEnabled)
                    },
                ),
                contentDescription = stringResource(R.string.SettingsResetProfileAction),
                tint = Color.Unspecified,
            ) else Icon(
                painter = painterResource(R.drawable.refresh_outline_28),
                contentDescription = stringResource(R.string.SettingsResetProfileAction),
                modifier = Modifier.graphicsLayer(scaleX = -1f),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }

    if (showSaveDialog) {
        ProfileSaveDialog(
            initialTitle = currentTitle(),
            changeSummary = pendingSummary(),
            onDismiss = { showSaveDialog = false },
            onSave = { name ->
                showSaveDialog = false
                onApply(name)
            },
        )
    }
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.SettingsResetProfileTitle)) },
            text = { Text(stringResource(R.string.SettingsResetProfileDescription)) },
            confirmButton = {
                TextButton(onClick = {
                    showResetDialog = false
                    onReset()
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun ProfileSaveDialog(
    initialTitle: String,
    changeSummary: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var title by rememberSaveable(initialTitle) { mutableStateOf(initialTitle) }
    val isValid = ProfileNameValidator.isValid(title)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.SettingsSaveTitle)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (changeSummary.isNotBlank()) {
                    Text(
                        text = changeSummary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    HorizontalDivider()
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.SettingsProfileName)) },
                    singleLine = true,
                    isError = !isValid,
                    supportingText = if (isValid) null else {
                        { Text(stringResource(R.string.SettingsProfileNameInvalid)) }
                    },
                )
            }
        },
        confirmButton = {
            TextButton(enabled = isValid, onClick = { onSave(title) }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
