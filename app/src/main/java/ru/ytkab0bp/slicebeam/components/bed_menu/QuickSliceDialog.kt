package ru.ytkab0bp.slicebeam.components.bed_menu

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.SliceBeam
import ru.ytkab0bp.slicebeam.config.ConfigObject
import ru.ytkab0bp.slicebeam.config.TemporaryProjectSettings
import ru.ytkab0bp.slicebeam.fragment.BedFragment
import java.math.BigDecimal

/** Quick slicing with larger, scrollable fields and validated profile values. */
object QuickSliceDialog {
    @JvmStatic
    fun show(fragment: BedFragment, anchor: android.view.View) {
        val context = anchor.context
        val config = SliceBeam.requireConfig()
        val presets = config.presets ?: return
        val printerNames = config.printerConfigs.mapNotNull { it.title }
        val projectFilaments = TemporaryProjectSettings.slots()
        val filamentNames = if (projectFilaments != null) emptyList()
            else config.filamentConfigs.map { it.title.orEmpty() }
        val projectFilamentSummary = projectFilaments?.mapIndexed { index, slot ->
            "${index + 1}: ${slot.type.orEmpty()} #${"%06X".format(slot.color and 0xFFFFFF)}"
        }?.joinToString(" · ")
        val qualityOptions = context.resources.getStringArray(R.array.QuickSliceQualities).toList()
        val currentPrint = SliceBeam.buildCurrentConfigObject()
        val originalLayerHeight = currentPrint?.get("layer_height") ?: "0.20"
        val options = QuickSliceDialogOptions(
            printerNames = printerNames,
            filamentNames = filamentNames,
            projectFilamentSummary = projectFilamentSummary,
            qualityOptions = qualityOptions,
            selectedPrinter = printerNames.indexOf(presets["printer"]).coerceAtLeast(0),
            selectedFilament = filamentNames.indexOf(presets["filament"]).coerceAtLeast(0),
            selectedQuality = 1.coerceAtMost((qualityOptions.size - 1).coerceAtLeast(0)),
            originalLayerHeight = originalLayerHeight,
            layerHeight = originalLayerHeight,
            infill = currentPrint?.get("fill_density")?.replace("%", "") ?: "15",
            supports = currentPrint?.get("support_material") == "1",
            brim = (currentPrint?.get("brim_width")?.toDoubleOrNull() ?: 0.0) > 0.0,
            brimWidth = currentPrint?.get("brim_width")?.takeIf { (it.toDoubleOrNull() ?: 0.0) > 0.0 } ?: "5",
        )
        fragment.showWorkspaceComposeDialog { dismiss ->
            QuickSliceDialogContent(
                options = options,
                onDismiss = dismiss,
                onStart = onStart@{ selectedPrinter, selectedFilament, settings ->
                    val selectedPrinterName = printerNames.getOrNull(selectedPrinter)
                    val selectedFilamentName = filamentNames.getOrNull(selectedFilament)
                    val temporary = TemporaryProjectSettings.active
                    val printer = selectedPrinterName.takeIf { !temporary || it != presets["printer"] }
                    val filament = selectedFilamentName.takeIf { !temporary || it != presets["filament"] }
                    if ((printer != null || filament != null) && !SliceBeam.applyProfileSelection(printer, filament)) {
                        Toast.makeText(context, R.string.ConfigSaveFailed, Toast.LENGTH_LONG).show()
                        return@onStart
                    }
                    when (QuickSliceProfileTransaction.apply(
                        config,
                        QuickSliceProfileTransaction.Values(
                            layerHeight = settings.layerHeight,
                            infillPercent = settings.infillPercent,
                            supports = settings.supports,
                            brim = settings.brim,
                            brimWidth = settings.brimWidth,
                        ),
                        SliceBeam::saveConfig,
                    )) {
                        QuickSliceProfileTransaction.Result.SAVED -> Unit
                        QuickSliceProfileTransaction.Result.PRINT_PROFILE_MISSING -> {
                            Toast.makeText(context, R.string.QuickSliceProfileMissing, Toast.LENGTH_LONG).show()
                            return@onStart
                        }
                        QuickSliceProfileTransaction.Result.SAVE_FAILED -> {
                            fragment.refreshProfileStatus()
                            Toast.makeText(context, R.string.ConfigSaveFailed, Toast.LENGTH_LONG).show()
                            return@onStart
                        }
                    }
                    fragment.refreshProfileStatus()
                    dismiss()
                    fragment.startSlicing()
                }
            )
        }
    }
}

internal data class QuickSliceDialogOptions(
    val printerNames: List<String>,
    val filamentNames: List<String>,
    val projectFilamentSummary: String?,
    val qualityOptions: List<String>,
    val selectedPrinter: Int,
    val selectedFilament: Int,
    val selectedQuality: Int,
    val originalLayerHeight: String,
    val layerHeight: String,
    val infill: String,
    val supports: Boolean,
    val brim: Boolean,
    val brimWidth: String = "5",
)

@Composable
internal fun QuickSliceDialogContent(
    options: QuickSliceDialogOptions,
    onDismiss: () -> Unit,
    onStart: (selectedPrinter: Int, selectedFilament: Int, settings: QuickSliceProfileSettings) -> Unit,
) {
    var selectedPrinter by remember(options) { mutableIntStateOf(options.selectedPrinter) }
    var selectedFilament by remember(options) { mutableIntStateOf(options.selectedFilament) }
    var selectedQuality by remember(options) { mutableIntStateOf(options.selectedQuality) }
    var layerHeight by remember(options) { mutableStateOf(options.layerHeight) }
    var infill by remember(options) { mutableStateOf(options.infill) }
    var supports by remember(options) { mutableStateOf(options.supports) }
    var brim by remember(options) { mutableStateOf(options.brim) }
    var brimWidth by remember(options) { mutableStateOf(options.brimWidth) }
    var invalidLayerHeight by remember(options) { mutableStateOf(false) }
    var invalidInfill by remember(options) { mutableStateOf(false) }
    var invalidBrimWidth by remember(options) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.QuickSliceTitle)) },
        text = {
            QuickSliceContent(
                printerNames = options.printerNames,
                filamentNames = options.filamentNames,
                projectFilamentSummary = options.projectFilamentSummary,
                qualityOptions = options.qualityOptions,
                selectedPrinter = selectedPrinter,
                selectedFilament = selectedFilament,
                selectedQuality = selectedQuality,
                onPrinterSelected = { selectedPrinter = it },
                onFilamentSelected = { selectedFilament = it },
                onQualitySelected = { selectedQuality = it },
                layerHeight = layerHeight,
                onLayerHeightChanged = { layerHeight = it; invalidLayerHeight = false },
                infill = infill,
                onInfillChanged = { infill = it; invalidInfill = false },
                supports = supports,
                onSupportsChanged = { supports = it },
                brim = brim,
                onBrimChanged = { brim = it },
                brimWidth = brimWidth,
                onBrimWidthChanged = { brimWidth = it; invalidBrimWidth = false },
                invalidLayerHeight = invalidLayerHeight,
                invalidInfill = invalidInfill,
                invalidBrimWidth = invalidBrimWidth,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val result = QuickSliceProfileValues.resolve(
                        qualityIndex = selectedQuality,
                        originalLayerHeight = options.originalLayerHeight,
                        enteredLayerHeight = layerHeight,
                        enteredInfill = infill,
                        supports = supports,
                        brim = brim,
                        brimWidth = brimWidth,
                    )
                    invalidLayerHeight = result.invalidLayerHeight
                    invalidInfill = result.invalidInfill
                    invalidBrimWidth = result.invalidBrimWidth
                    result.settings?.let { onStart(selectedPrinter, selectedFilament, it) }
                },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.QuickSliceStart)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun QuickSliceContent(
    printerNames: List<String>,
    filamentNames: List<String>,
    projectFilamentSummary: String?,
    qualityOptions: List<String>,
    selectedPrinter: Int,
    selectedFilament: Int,
    selectedQuality: Int,
    onPrinterSelected: (Int) -> Unit,
    onFilamentSelected: (Int) -> Unit,
    onQualitySelected: (Int) -> Unit,
    layerHeight: String,
    onLayerHeightChanged: (String) -> Unit,
    infill: String,
    onInfillChanged: (String) -> Unit,
    supports: Boolean,
    onSupportsChanged: (Boolean) -> Unit,
    brim: Boolean,
    onBrimChanged: (Boolean) -> Unit,
    brimWidth: String,
    onBrimWidthChanged: (String) -> Unit,
    invalidLayerHeight: Boolean,
    invalidInfill: Boolean,
    invalidBrimWidth: Boolean,
) {
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.76f
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(if (projectFilamentSummary == null) R.string.QuickSliceHelp
            else R.string.QuickSliceProjectHelp),
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        if (printerNames.isNotEmpty()) SliceChoiceField(
            stringResource(R.string.QuickSlicePrinter), selectedPrinter, printerNames, onPrinterSelected,
        )
        if (filamentNames.isNotEmpty()) SliceChoiceField(
            stringResource(R.string.QuickSliceFilament), selectedFilament, filamentNames, onFilamentSelected,
        )
        if (projectFilamentSummary != null) Text(
            stringResource(R.string.QuickSliceProjectFilaments, projectFilamentSummary),
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
        )
        SliceChoiceField(stringResource(R.string.QuickSliceQuality), selectedQuality, qualityOptions, onQualitySelected)
        OutlinedTextField(
            value = layerHeight,
            onValueChange = onLayerHeightChanged,
            modifier = Modifier.fillMaxWidth().testTag("quick-slice-layer-height"),
            label = { Text(stringResource(R.string.QuickSliceLayerHeight)) },
            supportingText = if (invalidLayerHeight) ({ Text(stringResource(R.string.QuickSliceInvalidLayerHeight)) }) else null,
            isError = invalidLayerHeight,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        OutlinedTextField(
            value = infill,
            onValueChange = onInfillChanged,
            modifier = Modifier.fillMaxWidth().testTag("quick-slice-infill"),
            label = { Text(stringResource(R.string.QuickSliceInfill)) },
            supportingText = if (invalidInfill) ({ Text(stringResource(R.string.QuickSliceInvalidInfill)) }) else null,
            isError = invalidInfill,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        QuickSliceToggle(stringResource(R.string.QuickSliceSupport), supports, onSupportsChanged)
        QuickSliceToggle(stringResource(R.string.QuickSliceBrim), brim, onBrimChanged)
        if (brim) OutlinedTextField(
            value = brimWidth,
            onValueChange = onBrimWidthChanged,
            modifier = Modifier.fillMaxWidth().testTag("quick-slice-brim-width"),
            label = { Text(stringResource(R.string.QuickSliceBrimWidth)) },
            supportingText = if (invalidBrimWidth) ({ Text(stringResource(R.string.QuickSliceInvalidBrimWidth)) }) else null,
            isError = invalidBrimWidth,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
    }
}

@Composable
private fun QuickSliceToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(if (label == stringResource(R.string.QuickSliceBrim)) "quick-slice-brim-toggle" else "quick-slice-support-toggle"),
        )
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
    }
}

internal data class QuickSliceProfileSettings(
    val layerHeight: String,
    val infillPercent: String,
    val supports: Boolean,
    val brim: Boolean,
    val brimWidth: String = "5",
)

internal data class QuickSliceProfileResolution(
    val settings: QuickSliceProfileSettings?,
    val invalidLayerHeight: Boolean,
    val invalidInfill: Boolean,
    val invalidBrimWidth: Boolean = false,
)

/** Pure validation keeps quick-slice UI values safe before they are written into Orca profiles. */
internal object QuickSliceProfileValues {
    fun resolve(
        qualityIndex: Int,
        originalLayerHeight: String,
        enteredLayerHeight: String,
        enteredInfill: String,
        supports: Boolean,
        brim: Boolean,
        brimWidth: String = "5",
    ): QuickSliceProfileResolution {
        val qualityDefault = when (qualityIndex) {
            0 -> "0.28"
            2 -> "0.12"
            else -> "0.20"
        }
        val enteredLayer = enteredLayerHeight.trim().replace(',', '.')
        val original = originalLayerHeight.trim().replace(',', '.').toBigDecimalOrNull()
        val enteredLayerNumber = enteredLayer.toBigDecimalOrNull()
        val useQualityDefault = enteredLayer.isEmpty() ||
            (original != null && enteredLayerNumber != null && original.compareTo(enteredLayerNumber) == 0)
        val layer = if (useQualityDefault) qualityDefault else enteredLayerNumber?.takeIf { it.signum() > 0 }?.canonical()
        val rawInfill = enteredInfill.trim().replace(',', '.').ifEmpty { "15" }
        val infillNumber = rawInfill.toBigDecimalOrNull()
        val infill = infillNumber?.takeIf { it >= BigDecimal.ZERO && it <= BigDecimal("100") }?.canonical()
        val rawBrim = brimWidth.trim().replace(',', '.').ifEmpty { "5" }
        val brimNumber = rawBrim.toBigDecimalOrNull()
        val validBrim = !brim || brimNumber != null && brimNumber > BigDecimal.ZERO && brimNumber <= BigDecimal("50")
        val normalizedBrim = brimNumber?.takeIf { validBrim }?.canonical() ?: "5"
        val invalidLayer = layer == null
        val invalidInfill = infill == null
        val invalidBrim = !validBrim
        val settings = if (invalidLayer || invalidInfill || invalidBrim) null else QuickSliceProfileSettings(
            layerHeight = layer!!,
            infillPercent = infill!!,
            supports = supports,
            brim = brim,
            brimWidth = normalizedBrim,
        )
        return QuickSliceProfileResolution(settings, invalidLayer, invalidInfill, invalidBrim)
    }

    private fun BigDecimal.canonical(): String = stripTrailingZeros().toPlainString()
}
