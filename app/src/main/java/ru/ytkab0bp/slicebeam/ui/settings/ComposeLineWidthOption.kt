package ru.ytkab0bp.slicebeam.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.ytkab0bp.slicebeam.config.LineWidthConversion
import java.util.function.Consumer

/** Both editors write one Orca percentage, so changing the nozzle keeps the same ratio. */
object ComposeLineWidthOption {
    @Composable
    fun Content(title: String, initialValue: String, nozzleMm: Double, maxPercent: Double,
                onChanged: Consumer<String>) {
        val initialPercent = LineWidthConversion.percent(initialValue, nozzleMm) ?: 100.0
        var percentFocused by rememberSaveable(nozzleMm) { mutableStateOf(false) }
        var millimetresFocused by rememberSaveable(nozzleMm) { mutableStateOf(false) }
        var percent by rememberSaveable(nozzleMm) {
            mutableStateOf(LineWidthConversion.formatPercent(initialPercent))
        }
        var millimetres by rememberSaveable(nozzleMm) {
            mutableStateOf(LineWidthConversion.formatMillimetres(
                LineWidthConversion.millimetres(initialPercent, nozzleMm)))
        }
        LaunchedEffect(initialValue, nozzleMm) {
            if (!percentFocused && !millimetresFocused) {
                val externalPercent = LineWidthConversion.percent(initialValue, nozzleMm) ?: 100.0
                percent = LineWidthConversion.formatPercent(externalPercent)
                millimetres = LineWidthConversion.formatMillimetres(
                    LineWidthConversion.millimetres(externalPercent, nozzleMm),
                )
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(value = percent, onValueChange = { input ->
                        percent = input
                        val parsed = input.replace(',', '.').toDoubleOrNull()
                        if (parsed != null && parsed.isFinite() && parsed in 20.0..maxPercent) {
                            millimetres = LineWidthConversion.formatMillimetres(
                                LineWidthConversion.millimetres(parsed, nozzleMm))
                            onChanged.accept("${LineWidthConversion.formatPercent(parsed)}%")
                        }
                    }, modifier = Modifier.width(88.dp).heightIn(min = 56.dp)
                        .onFocusChanged { percentFocused = it.isFocused },
                        shape = RoundedCornerShape(12.dp),
                        label = { Text("%", fontSize = 12.sp) }, singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(value = millimetres, onValueChange = { input ->
                        millimetres = input
                        val parsed = input.replace(',', '.').toDoubleOrNull()
                        if (parsed != null && parsed.isFinite() && parsed > 0.0) {
                            val derived = LineWidthConversion.percentFromMillimetres(parsed, nozzleMm)
                            percent = LineWidthConversion.formatPercent(derived)
                            if (derived in 20.0..maxPercent) {
                                onChanged.accept("${LineWidthConversion.formatPercent(derived)}%")
                            }
                        } else {
                            percent = ""
                        }
                    }, modifier = Modifier.width(88.dp).heightIn(min = 56.dp)
                        .onFocusChanged { millimetresFocused = it.isFocused },
                        shape = RoundedCornerShape(12.dp),
                        label = { Text("mm", fontSize = 12.sp) }, singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
            }
        }
    }
}
