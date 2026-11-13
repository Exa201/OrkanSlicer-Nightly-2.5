package ru.ytkab0bp.slicebeam.components.bed_menu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun ModelSimplifyChoiceDialog(
    title: String,
    currentTriangles: String,
    choices: List<String>,
    startLabel: String,
    cancelLabel: String,
    onDismiss: () -> Unit,
    onStart: (Int) -> Unit,
) {
    var selected by remember(choices) { mutableIntStateOf(1.coerceAtMost(choices.lastIndex)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(currentTriangles)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    itemsIndexed(choices) { index, choice ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { selected = index }
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selected == index, onClick = { selected = index })
                            Text(choice, Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onStart(selected) }, Modifier.heightIn(min = 48.dp)) { Text(startLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss, Modifier.heightIn(min = 48.dp)) { Text(cancelLabel) } },
    )
}

@Composable
internal fun ModelSimplifyCustomDialog(
    title: String,
    hint: String,
    initialTriangleCount: Int,
    minimumTriangleCount: Int,
    maximumTriangleCount: Int,
    startLabel: String,
    cancelLabel: String,
    onDismiss: () -> Unit,
    onStart: (Int) -> Unit,
) {
    var target by remember(initialTriangleCount) { mutableStateOf(maxOf(minimumTriangleCount, initialTriangleCount / 2).toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(hint)
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it.filter(Char::isDigit).take(10) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val value = (target.toIntOrNull() ?: initialTriangleCount / 2)
                        .coerceIn(minimumTriangleCount, maximumTriangleCount)
                    onStart(value)
                },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(startLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss, Modifier.heightIn(min = 48.dp)) { Text(cancelLabel) } },
    )
}

@Composable
internal fun ModelSmoothMeshDialog(
    triangleCount: Int,
    onDismiss: () -> Unit,
    onStart: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(androidx.compose.ui.res.stringResource(ru.ytkab0bp.slicebeam.R.string.ModelSmoothMesh)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(androidx.compose.ui.res.stringResource(ru.ytkab0bp.slicebeam.R.string.ModelSmoothMeshHint))
                Text(androidx.compose.ui.res.pluralStringResource(
                    ru.ytkab0bp.slicebeam.R.plurals.ModelSimplifyCurrent, triangleCount, triangleCount))
                Text(androidx.compose.ui.res.pluralStringResource(
                    ru.ytkab0bp.slicebeam.R.plurals.ModelSmoothMeshDone, triangleCount * 4, triangleCount * 4))
            }
        },
        confirmButton = {
            TextButton(onClick = onStart, Modifier.heightIn(min = 48.dp)) {
                Text(androidx.compose.ui.res.stringResource(ru.ytkab0bp.slicebeam.R.string.ModelSmoothMeshStart))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, Modifier.heightIn(min = 48.dp)) {
                Text(androidx.compose.ui.res.stringResource(android.R.string.cancel))
            }
        },
    )
}
