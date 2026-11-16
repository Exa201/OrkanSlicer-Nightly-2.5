package ru.ytkab0bp.slicebeam.fragment

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.ytkab0bp.slicebeam.R

/** Selects which Orca per-nozzle process vector entry is edited; shared values remain the default. */
class ProfileToolColumnSelector(
    private val selectedIndex: Int,
    private val columns: List<String>,
    private val onSelected: (Int) -> Unit,
) : ProfileSettingsComposeItem {
    @Composable
    override fun Content(rowPosition: Int, onEditorStateChanged: (Boolean) -> Unit) {
        val expanded = remember { mutableStateOf(false) }
        val selectedLabel = columns.getOrNull(selectedIndex)
            ?: stringResource(R.string.ProfileToolColumnShared)
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(
                text = stringResource(R.string.ProfileToolColumnLabel),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics { stateDescription = selectedLabel }
                    .clickable(role = Role.Button) { expanded.value = true }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selectedLabel,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("▾", color = MaterialTheme.colorScheme.primary, fontSize = 18.sp)
                DropdownMenu(expanded = expanded.value, onDismissRequest = { expanded.value = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.ProfileToolColumnShared)) },
                        onClick = { expanded.value = false; onSelected(-1) },
                    )
                    columns.forEachIndexed { index, label ->
                        DropdownMenuItem(
                            text = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            onClick = { expanded.value = false; onSelected(index) },
                        )
                    }
                }
            }
        }
    }
}
