package ru.ytkab0bp.slicebeam.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.SliceBeam
import ru.ytkab0bp.slicebeam.config.SlotFilamentProfiles
import ru.ytkab0bp.slicebeam.utils.Prefs

/** Selects which of the user's own T-slots is being edited in the normal filament screen. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun OwnFilamentSlotBar(profileTitle: String?, onSelect: (Int) -> Unit) {
    val slots = Prefs.getFilamentSlots()
    var selected by remember { mutableIntStateOf(SlotFilamentProfiles.selectedSlot.coerceIn(0, slots.lastIndex.coerceAtLeast(0))) }
    var helpExpanded by remember { androidx.compose.runtime.mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
        ) {
            Column(Modifier.fillMaxWidth().padding(start = 8.dp, top = 4.dp, end = 4.dp, bottom = 8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(R.drawable.ic_filamentslot),
                        contentDescription = null,
                        modifier = Modifier.size(31.dp),
                        tint = Color.Unspecified,
                    )
                    Text(
                        text = stringResource(R.string.SlotFilamentConfig),
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    IconButton(onClick = { helpExpanded = !helpExpanded }, modifier = Modifier.heightIn(min = 48.dp).size(48.dp)) {
                        Icon(
                            painterResource(R.drawable.ic_info),
                            contentDescription = stringResource(R.string.OwnFilamentSlotHelp),
                            tint = Color.Unspecified,
                        )
                    }
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    maxItemsInEachRow = 4,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    slots.forEachIndexed { index, slot ->
                        val isSelected = selected == index
                        Surface(
                            modifier = Modifier.weight(1f).heightIn(min = 54.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(role = Role.Button) { selected = index; onSelect(index) }
                                .semantics {
                                    this.selected = isSelected
                                    contentDescription = "T$index, ${slot.type.orEmpty()}"
                                },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Column(
                                Modifier.fillMaxWidth().widthIn(min = 48.dp).padding(horizontal = 4.dp, vertical = 7.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(Color(slot.color)))
                                Text("T$index", fontWeight = FontWeight.Bold, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, maxLines = 1)
                                Text(
                                    slot.type.orEmpty().ifBlank { "—" },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    lineHeight = 14.sp,
                                    maxLines = 2,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (helpExpanded) Text(stringResource(R.string.OwnFilamentSlotHelp), modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        val material = slots.getOrNull(selected)?.type.orEmpty()
        val profileMaterial = SliceBeam.CONFIG?.findFilament(profileTitle)?.get("filament_type")
            ?.substringBefore(';')?.substringBefore(',').orEmpty()
        if (material.isNotBlank() && profileMaterial.isNotBlank() &&
            !material.equals(profileMaterial, ignoreCase = true)) {
            Text(stringResource(R.string.OwnFilamentMaterialNotice, material, profileMaterial),
                color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
        }
    }
}
