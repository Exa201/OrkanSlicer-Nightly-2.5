package ru.ytkab0bp.slicebeam.components.bed_menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ru.ytkab0bp.slicebeam.R

private data class PrimitiveChoice(val id: Int, val label: Int, val dimensions: List<Int>)

private val primitiveChoices = listOf(
    PrimitiveChoice(0, R.string.PrimitiveCube, listOf(R.string.PrimitiveWidth, R.string.PrimitiveDepth, R.string.PrimitiveHeight)),
    PrimitiveChoice(1, R.string.PrimitiveCylinder, listOf(R.string.PrimitiveDiameter, R.string.PrimitiveHeight)),
    PrimitiveChoice(2, R.string.PrimitiveCone, listOf(R.string.PrimitiveDiameter, R.string.PrimitiveHeight)),
    PrimitiveChoice(3, R.string.PrimitiveSphere, listOf(R.string.PrimitiveDiameter)),
    PrimitiveChoice(4, R.string.PrimitiveTorus, listOf(R.string.PrimitiveOuterDiameter, R.string.PrimitiveTubeDiameter)),
    PrimitiveChoice(5, R.string.PrimitiveDisc, listOf(R.string.PrimitiveDiameter, R.string.PrimitiveThickness)),
    PrimitiveChoice(6, R.string.PrimitiveSlab, listOf(R.string.PrimitiveWidth, R.string.PrimitiveDepth, R.string.PrimitiveThickness)),
)

@Composable
internal fun PrimitiveModelDialog(onDismiss: () -> Unit, onCreate: (type: Int, a: Double, b: Double, c: Double, name: String) -> Unit) {
    var selected by remember { mutableStateOf(primitiveChoices.first()) }
    var expanded by remember { mutableStateOf(false) }
    var first by remember { mutableStateOf("20") }
    var second by remember { mutableStateOf("20") }
    var third by remember { mutableStateOf("20") }
    val values = listOf(first, second, third).map(String::toDoubleOrNull)
    val primitiveName = stringResource(selected.label)
    val parsed = values.map { it ?: Double.NaN }
    val valid = when (selected.id) {
        0, 6 -> parsed.all { it.isFinite() && it in 0.2..500.0 }
        1, 2, 5 -> parsed[0].isFinite() && parsed[0] in 0.2..500.0 && parsed[1].isFinite() && parsed[1] in 0.2..500.0
        3 -> parsed[0].isFinite() && parsed[0] in 0.2..500.0
        4 -> parsed[0].isFinite() && parsed[0] in 0.6..500.0 && parsed[1].isFinite() && parsed[1] >= 0.2 && parsed[1] < parsed[0] / 2.0
        else -> false
    }
    val dimensions = selected.dimensions
    val arguments = when (selected.id) {
        0, 6 -> parsed
        1, 2 -> listOf(parsed[0], 0.0, parsed[1])
        3 -> listOf(parsed[0], 0.0, 0.0)
        4 -> listOf(parsed[0], parsed[1], 0.0)
        else -> listOf(parsed[0], 0.0, parsed[1])
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ModelMenuCreatePrimitive)) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.PrimitiveHelp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(selected.label))
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    primitiveChoices.forEach { choice ->
                        DropdownMenuItem(
                            text = { Text(stringResource(choice.label)) },
                        onClick = {
                            selected = choice
                            if (choice.id == 6) third = "2"
                            expanded = false
                        },
                        )
                    }
                }
                dimensions.forEachIndexed { index, label ->
                    val value = when (index) { 0 -> first; 1 -> second; else -> third }
                    OutlinedTextField(
                        value = value,
                        onValueChange = { raw ->
                            if (raw.length <= 10 && raw.matches(Regex("\\d{0,3}(?:[.,]\\d{0,3})?"))) {
                                when (index) { 0 -> first = raw.replace(',', '.'); 1 -> second = raw.replace(',', '.'); else -> third = raw.replace(',', '.') }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
                Text(stringResource(R.string.PrimitiveUnits), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onCreate(selected.id, arguments[0], arguments[1], arguments[2], primitiveName)
                },
                modifier = Modifier.heightIn(min = 48.dp).padding(horizontal = 4.dp),
            ) { Text(stringResource(R.string.PrimitiveCreate)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
