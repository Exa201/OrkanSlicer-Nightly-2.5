package ru.ytkab0bp.slicebeam.ui.setup

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.ui.components.GlideBitmapShape
import ru.ytkab0bp.slicebeam.ui.components.rememberGlideBitmap

@Composable
internal fun SetupPrinterSelectionScreen(
    sections: List<SetupProfileSection>,
    loading: Boolean,
    useCustomProfile: Boolean,
    overlayAlpha: Float,
    onToggle: (String, Boolean) -> Unit,
    onContinue: () -> Unit,
) {
    var printerQuery by remember { mutableStateOf("") }
    val visibleSections = sections.mapNotNull { section ->
        val matching = section.options.filter { option ->
            printerQuery.isBlank() || option.title.contains(printerQuery.trim(), ignoreCase = true) ||
                section.title.contains(printerQuery.trim(), ignoreCase = true)
        }
        section.takeIf { matching.isNotEmpty() }?.copy(options = matching)
    }
    Box(modifier = Modifier.fillMaxSize()) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 52.dp, bottom = 84.dp)
                    .graphicsLayer { alpha = overlayAlpha },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "printer_title") {
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                        Text(
                            text = stringResource(R.string.SetupSelectPrintersTitle),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.SetupSelectPrintersSubtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (!useCustomProfile) {
                    item(key = "printer_search") {
                        OutlinedTextField(
                            value = printerQuery,
                            onValueChange = { printerQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text(stringResource(R.string.SetupSearchPrinter)) },
                        )
                    }
                }
                if (useCustomProfile) {
                    item(key = "custom_profile") {
                        SetupCustomProfileCard()
                    }
                } else {
                    visibleSections.forEach { section ->
                        item(key = "printer_vendor_${section.key}") {
                            SetupSectionHeading(section.title)
                        }
                        items(section.options, key = { it.key }) { option ->
                            SetupProfileOptionRow(
                                option = option,
                                iconRes = R.drawable.printer_outline_28,
                                onToggle = { onToggle(option.key, it) },
                            )
                        }
                    }
                    if (visibleSections.isEmpty()) {
                        item(key = "printer_search_empty") {
                            Text(
                                text = stringResource(R.string.SetupSearchPrinterEmpty),
                                modifier = Modifier.padding(vertical = 16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Button(
                onClick = onContinue,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 16.dp).heightIn(min = 52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(text = stringResource(R.string.IntroNext), fontSize = 16.sp)
            }
        }
    }
}

@Composable
internal fun SetupFilamentSelectionScreen(
    sections: List<SetupProfileSection>,
    hasFilaments: Boolean,
    overlayAlpha: Float,
    onToggle: (String, Boolean) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onContinue: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 52.dp, bottom = 84.dp)
                .graphicsLayer { alpha = overlayAlpha },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "filament_title") {
                Text(
                    text = stringResource(R.string.IntroFilamentsHeader),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (hasFilaments) {
                item(key = "filament_selection_actions") {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onSelectAll, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                            Text(text = stringResource(R.string.IntroFilamentsSelectAll))
                        }
                        TextButton(onClick = onDeselectAll, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                            Text(text = stringResource(R.string.IntroFilamentsDeselectAll))
                        }
                    }
                }
                sections.forEach { section ->
                    item(key = "filament_vendor_${section.key}") {
                        SetupSectionHeading(section.title)
                    }
                    items(section.options, key = { it.key }) { option ->
                        SetupProfileOptionRow(
                            option = option,
                            iconRes = R.drawable.slot_filament_28,
                            onToggle = { onToggle(option.key, it) },
                        )
                    }
                }
            } else {
                item(key = "filament_empty") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.IntroFilamentsNone),
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
        Button(
            onClick = onContinue,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 16.dp).heightIn(min = 52.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(text = stringResource(R.string.IntroNext), fontSize = 16.sp)
        }
    }
}

@Composable
private fun SetupSectionHeading(title: String) {
    Text(
        text = title,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun SetupCustomProfileCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.printer_outline_28),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(47.dp),
            )
            Text(
                text = stringResource(R.string.IntroCustomProfileName),
                modifier = Modifier.padding(start = 16.dp),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun SetupProfileOptionRow(
    option: SetupProfileOption,
    iconRes: Int,
    onToggle: (Boolean) -> Unit,
) {
    val thumbnail = rememberGlideBitmap(
        model = option.thumbnailUrl,
        size = 44.dp,
        shape = GlideBitmapShape.ROUNDED,
        cornerRadius = 12.dp,
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = option.selected, role = Role.Switch, onValueChange = onToggle)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (thumbnail == null) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(47.dp),
                )
            } else {
                Image(
                    bitmap = thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)),
                )
            }
            Text(
                text = option.title,
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Switch(checked = option.selected, onCheckedChange = null)
        }
    }
}
