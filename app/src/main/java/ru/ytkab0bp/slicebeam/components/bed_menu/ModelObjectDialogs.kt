package ru.ytkab0bp.slicebeam.components.bed_menu

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.withFrameNanos
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.slic3r.PrintConfigDef
import ru.ytkab0bp.slicebeam.slic3r.Slic3rLocalization
import ru.ytkab0bp.slicebeam.ui.components.OrkanIconBadge
import java.util.Locale

internal data class ModelObjectRow(
    val name: String,
    val tool: Int,
    val material: String,
    val color: Int,
    val selected: Boolean,
    val assignEnabled: Boolean,
    val parts: List<ModelObjectPartRow> = emptyList(),
    val objectIndex: Int = 0,
    val heightMm: Double = 0.0,
    val sizeMm3: Double = 0.0,
    val widthMm: Double = 0.0,
    val depthMm: Double = 0.0,
    val plateNumber: Int = 1,
    val plateCount: Int = 1,
    val previewMesh: FloatArray = FloatArray(0),
)

internal enum class ModelObjectOrder(val label: String) {
    ORIGINAL("Default"), NAME("Name"), FILAMENT("Filament"), HEIGHT("Height"), SIZE("Size"),
}

internal fun orderModelObjectRows(rows: List<ModelObjectRow>, order: ModelObjectOrder): List<ModelObjectRow> =
    when (order) {
        ModelObjectOrder.ORIGINAL -> rows.sortedBy { it.objectIndex }
        ModelObjectOrder.NAME -> rows.sortedWith(compareBy<ModelObjectRow> { it.name.lowercase(Locale.ROOT) }.thenBy { it.objectIndex })
        ModelObjectOrder.FILAMENT -> rows.sortedWith(
            compareBy<ModelObjectRow> { it.tool }.thenBy { it.material.lowercase(Locale.ROOT) }
                .thenBy { it.name.lowercase(Locale.ROOT) }.thenBy { it.objectIndex },
        )
        ModelObjectOrder.HEIGHT -> rows.sortedWith(
            compareBy<ModelObjectRow> { it.heightMm }.thenBy { it.name.lowercase(Locale.ROOT) }.thenBy { it.objectIndex },
        )
        ModelObjectOrder.SIZE -> rows.sortedWith(
            compareBy<ModelObjectRow> { it.sizeMm3 }.thenBy { it.name.lowercase(Locale.ROOT) }.thenBy { it.objectIndex },
        )
    }

internal data class ModelObjectPartRow(
    val label: String,
    val tool: Int,
    val material: String,
    val color: Int,
)

private data class ModelObjectListEntry(
    val parentIndex: Int,
    val row: ModelObjectRow,
    val partIndex: Int? = null,
) {
    val key: String = if (partIndex == null) "object:${row.objectIndex}" else "part:${row.objectIndex}:$partIndex"
}

/** Uses Orca's loaded schema so the editor only offers patterns supported by this bundled core. */
internal fun modelSparseInfillPatternOptions(): List<Pair<String, String>> = runCatching {
    val definition = PrintConfigDef.getInstance().options["sparse_infill_pattern"]
    val values = definition?.enumValues.orEmpty()
    val labels = definition?.enumLabels.orEmpty()
    values.mapIndexed { index, value -> value to labels.getOrNull(index).orEmpty().ifBlank { value } }
}.getOrDefault(emptyList())

/** Uses the bundled Orca enum so support strategies remain compatible with the native core. */
internal fun modelSupportTypeOptions(): List<Pair<String, String>> = runCatching {
    val definition = PrintConfigDef.getInstance().options["support_type"]
    val values = definition?.enumValues.orEmpty()
    val labels = definition?.enumLabels.orEmpty()
    values.mapIndexed { index, value -> value to labels.getOrNull(index).orEmpty().ifBlank { value } }
}.getOrDefault(emptyList())

/** Surface pattern enums are read from the bundled Orca schema, like sparse infill patterns. */
internal fun modelSurfacePatternOptions(): List<Pair<String, String>> = runCatching {
    val definition = PrintConfigDef.getInstance().options["top_surface_pattern"]
    val values = definition?.enumValues.orEmpty()
    val labels = definition?.enumLabels.orEmpty()
    values.mapIndexed { index, value -> value to labels.getOrNull(index).orEmpty().ifBlank { value } }
}.getOrDefault(emptyList())

/** Seam positions use Orca's object-level schema and retain the profile-defined enum labels. */
internal fun modelSeamPositionOptions(): List<Pair<String, String>> = runCatching {
    val definition = PrintConfigDef.getInstance().options["seam_position"]
    val values = definition?.enumValues.orEmpty()
    val labels = definition?.enumLabels.orEmpty()
    values.mapIndexed { index, value -> value to labels.getOrNull(index).orEmpty().ifBlank { value } }
}.getOrDefault(emptyList())

@Composable
internal fun ModelObjectProcessOverridesDialog(
    objectName: String,
    initialValues: Map<String, String>,
    brimFilamentOptions: List<Pair<String, String>> = emptyList(),
    patternOptions: List<Pair<String, String>> = emptyList(),
    surfacePatternOptions: List<Pair<String, String>> = emptyList(),
    supportTypeOptions: List<Pair<String, String>> = emptyList(),
    seamPositionOptions: List<Pair<String, String>> = emptyList(),
    includeObjectSupport: Boolean = true,
    scopeHint: Int = R.string.ModelObjectProcessSettingsHint,
    onDismiss: () -> Unit,
    onApply: (Map<String, String>) -> Unit,
) {
    val availablePatternOptions = remember(patternOptions) {
        patternOptions.ifEmpty { modelSparseInfillPatternOptions() }
    }
    val availableSupportTypeOptions = remember(supportTypeOptions) {
        supportTypeOptions.ifEmpty { modelSupportTypeOptions() }
    }
    val availableSurfacePatternOptions = remember(surfacePatternOptions) {
        surfacePatternOptions.ifEmpty { modelSurfacePatternOptions() }
    }
    val availableSeamPositionOptions = remember(seamPositionOptions) {
        seamPositionOptions.ifEmpty { modelSeamPositionOptions() }
    }
    val availableBrimFilamentOptions = remember(brimFilamentOptions) { brimFilamentOptions }
    val mixedValues = remember(initialValues) {
        listOf(
            0 to "wall_loops", 1 to "sparse_infill_density", 2 to "top_shell_layers",
            3 to "sparse_infill_pattern", 6 to "top_surface_pattern", 7 to "bottom_surface_pattern",
        ).mapNotNull { (index, key) -> index.takeIf { initialValues[key] == "<mixed>" } }.toSet()
    }
    val values = remember(initialValues, includeObjectSupport) {
        mutableStateListOf(
            initialValues["wall_loops"].orEmpty().takeUnless { it == "<mixed>" }.orEmpty(),
            initialValues["sparse_infill_density"].orEmpty().takeUnless { it == "<mixed>" }.orEmpty().removeSuffix("%"),
            initialValues["top_shell_layers"].orEmpty().takeUnless { it == "<mixed>" }.orEmpty(),
            initialValues["sparse_infill_pattern"].orEmpty().takeUnless { it == "<mixed>" }.orEmpty(),
            if (includeObjectSupport) initialValues["enable_support"].orEmpty() else "",
            if (includeObjectSupport) initialValues["support_type"].orEmpty() else "",
            initialValues["top_surface_pattern"].orEmpty().takeUnless { it == "<mixed>" }.orEmpty(),
            initialValues["bottom_surface_pattern"].orEmpty().takeUnless { it == "<mixed>" }.orEmpty(),
            if (includeObjectSupport) initialValues["seam_position"].orEmpty() else "",
            if (includeObjectSupport) initialValues["raft_layers"].orEmpty() else "",
            if (includeObjectSupport) initialValues["brim_filament_id"].orEmpty().takeUnless { it == "0" || it == "<mixed>" }.orEmpty() else "",
        )
    }
    var patternExpanded by remember { mutableStateOf(false) }
    var supportExpanded by remember { mutableStateOf(false) }
    var supportTypeExpanded by remember { mutableStateOf(false) }
    var topSurfaceExpanded by remember { mutableStateOf(false) }
    var bottomSurfaceExpanded by remember { mutableStateOf(false) }
    var seamPositionExpanded by remember { mutableStateOf(false) }
    var brimFilamentExpanded by remember { mutableStateOf(false) }
    val wallLoops = values[0].trim().toIntOrNull()
    val density = values[1].trim().replace(',', '.').toDoubleOrNull()
    val topLayers = values[2].trim().toIntOrNull()
    val raftLayers = values.getOrNull(9)?.trim()?.toIntOrNull()
    val valid = (values[0].isBlank() || wallLoops != null && wallLoops in 1..20) &&
        (values[1].isBlank() || density != null && density.isFinite() && density in 0.0..100.0) &&
        (values[2].isBlank() || topLayers != null && topLayers in 0..50) &&
        (values[3].isBlank() || availablePatternOptions.any { it.first == values[3] } ||
            initialValues["sparse_infill_pattern"] == values[3]) &&
        (values[4].isBlank() || values[4] == "0" || values[4] == "1") &&
        (values[5].isBlank() || availableSupportTypeOptions.any { it.first == values[5] }) &&
        (values[6].isBlank() || availableSurfacePatternOptions.any { it.first == values[6] }) &&
        (values[7].isBlank() || availableSurfacePatternOptions.any { it.first == values[7] }) &&
        (values.getOrNull(8).isNullOrBlank() || availableSeamPositionOptions.any { it.first == values[8] }) &&
        (values.getOrNull(9).isNullOrBlank() || raftLayers != null && raftLayers in 0..20) &&
        ModelObjectBrimFilamentPolicy.accepts(values.getOrNull(10).orEmpty(), availableBrimFilamentOptions)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ModelObjectProcessSettingsTitle, objectName)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(scopeHint))
                if (!valid) Text(
                    stringResource(R.string.ModelObjectProcessSettingsInvalid),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("model-object-process-settings-error"),
                )
                val labels = listOf(
                    R.string.ModelObjectProcessWallLoops,
                    R.string.ModelObjectProcessInfillDensity,
                    R.string.ModelObjectProcessTopLayers,
                    R.string.ModelObjectProcessInfillPattern,
                )
                values.indices.forEach { index ->
                    if (index == 4) {
                        if (!includeObjectSupport) return@forEach
                        val supportLabel = when (values[index]) {
                            "1" -> stringResource(R.string.ModelObjectProcessSupportEnabled)
                            "0" -> stringResource(R.string.ModelObjectProcessSupportDisabled)
                            else -> stringResource(R.string.ModelObjectProcessInheritPattern)
                        }
                        Box {
                            TextButton(
                                onClick = { supportExpanded = true },
                                modifier = Modifier.fillMaxWidth().testTag("model-object-process-support"),
                            ) {
                                Text("${stringResource(R.string.ModelObjectProcessSupport)}: $supportLabel")
                            }
                            DropdownMenu(expanded = supportExpanded, onDismissRequest = { supportExpanded = false }) {
                                listOf(
                                    "" to R.string.ModelObjectProcessInheritPattern,
                                    "1" to R.string.ModelObjectProcessSupportEnabled,
                                    "0" to R.string.ModelObjectProcessSupportDisabled,
                                ).forEach { (value, label) ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(label)) },
                                        onClick = { values[index] = value; supportExpanded = false },
                                        modifier = Modifier.testTag("model-object-process-support-$value"),
                                    )
                                }
                            }
                        }
                        return@forEach
                    }
                    if (index == 5) {
                        if (!includeObjectSupport) return@forEach
                        val selectedLabel = if (values[index].isBlank()) {
                            stringResource(R.string.ModelObjectProcessInheritPattern)
                        } else {
                            availableSupportTypeOptions.firstOrNull { it.first == values[index] }?.second ?: values[index]
                        }
                        Box {
                            TextButton(
                                onClick = { supportTypeExpanded = true },
                                modifier = Modifier.fillMaxWidth().testTag("model-object-process-support-type"),
                            ) { Text("${stringResource(R.string.ModelObjectProcessSupportType)}: $selectedLabel") }
                            DropdownMenu(expanded = supportTypeExpanded, onDismissRequest = { supportTypeExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.ModelObjectProcessInheritPattern)) },
                                    onClick = { values[index] = ""; supportTypeExpanded = false },
                                    modifier = Modifier.testTag("model-object-process-support-type-inherit"),
                                )
                                availableSupportTypeOptions.forEach { (value, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = { values[index] = value; supportTypeExpanded = false },
                                        modifier = Modifier.testTag("model-object-process-support-type-$value"),
                                    )
                                }
                            }
                        }
                        return@forEach
                    }
                    if (index == 6 || index == 7) {
                        val expanded = if (index == 6) topSurfaceExpanded else bottomSurfaceExpanded
                        val label = if (index == 6) R.string.ModelObjectProcessTopSurfacePattern else R.string.ModelObjectProcessBottomSurfacePattern
                        val testTag = if (index == 6) "model-object-process-top-surface-pattern" else "model-object-process-bottom-surface-pattern"
                        val itemTag = if (index == 6) "model-object-process-top-surface-pattern-" else "model-object-process-bottom-surface-pattern-"
                        val selectedLabel = if (values[index].isBlank()) {
                            stringResource(R.string.ModelObjectProcessInheritPattern)
                        } else {
                            availableSurfacePatternOptions.firstOrNull { it.first == values[index] }?.second ?: values[index]
                        }
                        Box {
                            TextButton(
                                onClick = { if (index == 6) topSurfaceExpanded = true else bottomSurfaceExpanded = true },
                                modifier = Modifier.fillMaxWidth().testTag(testTag),
                            ) { Text("${stringResource(label)}: $selectedLabel") }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { if (index == 6) topSurfaceExpanded = false else bottomSurfaceExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.ModelObjectProcessInheritPattern)) },
                                    onClick = { values[index] = ""; if (index == 6) topSurfaceExpanded = false else bottomSurfaceExpanded = false },
                                    modifier = Modifier.testTag("${itemTag}inherit"),
                                )
                                availableSurfacePatternOptions.forEach { (value, optionLabel) ->
                                    DropdownMenuItem(
                                        text = { Text(optionLabel) },
                                        onClick = { values[index] = value; if (index == 6) topSurfaceExpanded = false else bottomSurfaceExpanded = false },
                                        modifier = Modifier.testTag("$itemTag$value"),
                                    )
                                }
                            }
                        }
                        if (index in mixedValues) Text(
                            stringResource(R.string.ModelObjectProcessSettingMixed),
                            modifier = Modifier.padding(start = 12.dp).testTag("model-object-process-setting-mixed-$index"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        return@forEach
                    }
                    if (index == 8) {
                        if (!includeObjectSupport) return@forEach
                        val selectedLabel = if (values[index].isBlank()) {
                            stringResource(R.string.ModelObjectProcessInheritPattern)
                        } else {
                            availableSeamPositionOptions.firstOrNull { it.first == values[index] }?.second ?: values[index]
                        }
                        Box {
                            TextButton(
                                onClick = { seamPositionExpanded = true },
                                modifier = Modifier.fillMaxWidth().testTag("model-object-process-seam-position"),
                            ) { Text("${stringResource(R.string.ModelObjectProcessSeamPosition)}: $selectedLabel") }
                            DropdownMenu(expanded = seamPositionExpanded, onDismissRequest = { seamPositionExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.ModelObjectProcessInheritPattern)) },
                                    onClick = { values[index] = ""; seamPositionExpanded = false },
                                    modifier = Modifier.testTag("model-object-process-seam-position-inherit"),
                                )
                                availableSeamPositionOptions.forEach { (value, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = { values[index] = value; seamPositionExpanded = false },
                                        modifier = Modifier.testTag("model-object-process-seam-position-$value"),
                                    )
                                }
                            }
                        }
                        return@forEach
                    }
                    if (index == 9) {
                        if (!includeObjectSupport) return@forEach
                        OutlinedTextField(
                            value = values[index],
                            onValueChange = { input -> values[index] = input.filter(Char::isDigit).take(2) },
                            label = { Text(stringResource(R.string.ModelObjectProcessRaftLayers)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().testTag("model-object-process-raft-layers"),
                        )
                        return@forEach
                    }
                    if (index == 10) {
                        if (!includeObjectSupport) return@forEach
                        val selectedLabel = if (values[index].isBlank()) {
                            stringResource(R.string.ModelObjectProcessInheritPattern)
                        } else {
                            availableBrimFilamentOptions.firstOrNull { it.first == values[index] }?.second ?: values[index]
                        }
                        Box {
                            TextButton(
                                onClick = { brimFilamentExpanded = true },
                                modifier = Modifier.fillMaxWidth().testTag("model-object-process-brim-filament"),
                            ) { Text("${stringResource(R.string.ModelObjectProcessBrimFilament)}: $selectedLabel") }
                            DropdownMenu(expanded = brimFilamentExpanded, onDismissRequest = { brimFilamentExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.ModelObjectProcessInheritPattern)) },
                                    onClick = { values[index] = ""; brimFilamentExpanded = false },
                                    modifier = Modifier.testTag("model-object-process-brim-filament-inherit"),
                                )
                                availableBrimFilamentOptions.forEach { (value, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = { values[index] = value; brimFilamentExpanded = false },
                                        modifier = Modifier.testTag("model-object-process-brim-filament-$value"),
                                    )
                                }
                            }
                        }
                        return@forEach
                    }
                    if (index == 3) {
                        val selectedLabel = if (values[index].isBlank()) {
                            stringResource(R.string.ModelObjectProcessInheritPattern)
                        } else {
                            availablePatternOptions.firstOrNull { it.first == values[index] }?.second ?: values[index]
                        }
                        Box {
                            TextButton(
                                onClick = { patternExpanded = true },
                                modifier = Modifier.fillMaxWidth().testTag("model-object-process-setting-3"),
                            ) {
                        Text("${stringResource(labels[index])}: $selectedLabel")
                            }
                            DropdownMenu(expanded = patternExpanded, onDismissRequest = { patternExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.ModelObjectProcessInheritPattern)) },
                                    onClick = { values[index] = ""; patternExpanded = false },
                                    modifier = Modifier.testTag("model-object-process-pattern-inherit"),
                                )
                                availablePatternOptions.forEach { (value, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = { values[index] = value; patternExpanded = false },
                                        modifier = Modifier.testTag("model-object-process-pattern-$value"),
                                    )
                                }
                            }
                        }
                        if (index in mixedValues) Text(
                            stringResource(R.string.ModelObjectProcessSettingMixed),
                            modifier = Modifier.padding(start = 12.dp).testTag("model-object-process-setting-mixed-$index"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        return@forEach
                    }
                    OutlinedTextField(
                        value = values[index],
                        onValueChange = { input ->
                            values[index] = input.filter { it.isDigit() || it == '.' || it == ',' }.take(8)
                        },
                        label = { Text(stringResource(labels[index])) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = if (index == 1) KeyboardType.Decimal else KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("model-object-process-setting-$index"),
                    )
                    if (index in mixedValues) Text(
                        stringResource(R.string.ModelObjectProcessSettingMixed),
                        modifier = Modifier.padding(start = 12.dp).testTag("model-object-process-setting-mixed-$index"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = { values.indices.forEach { values[it] = "" } },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("model-object-process-settings-inherit-all"),
                ) { Text(stringResource(R.string.ModelObjectProcessSettingsInheritAll)) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    val densityValue = values[1].trim().replace(',', '.')
                    val overrides = linkedMapOf(
                            "wall_loops" to (wallLoops?.toString().orEmpty()),
                            "sparse_infill_density" to if (densityValue.isEmpty()) "" else "$densityValue%",
                            "top_shell_layers" to (topLayers?.toString().orEmpty()),
                            "sparse_infill_pattern" to values[3],
                            "top_surface_pattern" to values[6],
                            "bottom_surface_pattern" to values[7],
                        )
                    if (includeObjectSupport) overrides["enable_support"] = values[4]
                    if (includeObjectSupport) overrides["support_type"] = values[5]
                    if (includeObjectSupport) overrides["seam_position"] = values.getOrNull(8).orEmpty()
                    if (includeObjectSupport) overrides["raft_layers"] = raftLayers?.toString().orEmpty()
                    if (includeObjectSupport) overrides["brim_filament_id"] = values.getOrNull(10).orEmpty()
                    onApply(overrides)
                },
                modifier = Modifier.heightIn(min = 48.dp).testTag("model-object-process-settings-apply"),
            ) { Text(stringResource(R.string.ModelObjectProcessSettingsApply)) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp).testTag("model-object-process-settings-cancel"),
            ) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun ModelObjectsDialog(
    title: String,
    cancelLabel: String,
    assignLabel: String,
    rows: List<ModelObjectRow>,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
    onAssign: (Int) -> Unit,
    onAssignPart: (objectIndex: Int, partIndex: Int) -> Unit,
    onEditPartProcessSettings: (objectIndex: Int, partIndex: Int) -> Unit = { _, _ -> },
    onTransformPart: (objectIndex: Int, partIndex: Int) -> Unit,
    onEditPart: (objectIndex: Int, partIndex: Int) -> Unit,
    onApplySelection: (IntArray) -> Unit,
) {
    // Callers often build these rows from a live native snapshot on recomposition. Key state
    // by their stable object/part identity so a fresh List instance does not reset search or
    // collapse multipart rows while the user is interacting with the dialog.
    val rowsStateKey = rows.joinToString("|") { row ->
        "${row.objectIndex}:${row.parts.joinToString(",") { it.label }}"
    }
    var groupSelectionMode by remember(rowsStateKey) { mutableStateOf(false) }
    var orderMenuExpanded by remember(rowsStateKey) { mutableStateOf(false) }
    var order by remember(rowsStateKey) { mutableStateOf(ModelObjectOrder.ORIGINAL) }
    var searchQuery by remember(rowsStateKey) { mutableStateOf("") }
    val stagedSelection = remember(rowsStateKey) {
        mutableStateListOf<Int>().apply { addAll(rows.filter { it.selected }.map { it.objectIndex }) }
    }
    val orderedRows = remember(rowsStateKey, rows, order, searchQuery) {
        val query = searchQuery.trim()
        orderModelObjectRows(rows, order).filter { row ->
            query.isEmpty() || row.name.contains(query, ignoreCase = true) ||
                row.material.contains(query, ignoreCase = true) ||
                row.parts.any { part ->
                    part.label.contains(query, ignoreCase = true) || part.material.contains(query, ignoreCase = true)
                }
        }
    }
    val maxRowsHeight = (LocalConfiguration.current.screenHeightDp * 0.56f).dp.coerceIn(220.dp, 520.dp)
    var expandedGroups by remember(rowsStateKey) {
        mutableStateOf(rows.filter { it.parts.size > 1 }.map { it.objectIndex }.toSet())
    }
    val visibleEntries = remember(orderedRows, expandedGroups) {
        buildList {
            orderedRows.forEachIndexed { parentIndex, row ->
                add(ModelObjectListEntry(parentIndex, row))
                if (row.objectIndex in expandedGroups) {
                    row.parts.indices.forEach { partIndex ->
                        add(ModelObjectListEntry(parentIndex, row, partIndex))
                    }
                }
            }
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.96f).widthIn(max = 720.dp).fillMaxHeight(0.90f),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OrkanIconBadge(R.drawable.vk_cube_box_outline_24, size = 42.dp)
                    Text(
                        title,
                        Modifier.weight(1f).padding(start = 12.dp),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Text("×", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).testTag("model-objects-search"),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.ModelObjectsSearchHint)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.print_settings_search_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    maxItemsInEachRow = 2,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                TextButton(
                    onClick = { groupSelectionMode = !groupSelectionMode },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("model-objects-group-selection-toggle"),
                ) {
                    Text(stringResource(if (groupSelectionMode) R.string.ModelMenuCancelGroupSelection else R.string.ModelMenuSelectGroup), textAlign = TextAlign.Center)
                }
                    TextButton(
                        onClick = { orderMenuExpanded = true },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("model-objects-order-menu"),
                    ) {
                        Text("${Slic3rLocalization.getString("Order By")}: ${Slic3rLocalization.getString(order.label)}", textAlign = TextAlign.Center)
                    }
                    DropdownMenu(expanded = orderMenuExpanded, onDismissRequest = { orderMenuExpanded = false }) {
                        ModelObjectOrder.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(Slic3rLocalization.getString(option.label)) },
                                onClick = { order = option; orderMenuExpanded = false },
                                modifier = Modifier.testTag("model-objects-order-${option.name.lowercase(Locale.ROOT)}"),
                            )
                        }
                    }
                }
                if (rows.any { it.parts.size > 1 }) {
                    Text(
                        stringResource(R.string.ModelObjectMultipartHelp),
                        Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("model-objects-multipart-help"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth().heightIn(min = 100.dp, max = maxRowsHeight)
                        .testTag("model-objects-dialog-rows"),
                ) {
                    if (orderedRows.isEmpty()) {
                        item(key = "search-empty") {
                            Text(
                                stringResource(R.string.ModelObjectsSearchEmpty),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                                    .testTag("model-objects-search-empty"),
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        itemsIndexed(visibleEntries, key = { _, entry -> entry.key }) { _, entry ->
                            val row = entry.row
                            val index = entry.parentIndex
                            val partIndex = entry.partIndex
                            if (partIndex == null) {
                                val selected = row.objectIndex in stagedSelection
                                Column(
                                    Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                        .background(
                                            if (selected) MaterialSelection
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
                                            RoundedCornerShape(14.dp),
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.78f)
                                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.46f),
                                                shape = RoundedCornerShape(14.dp),
                                            ),
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().heightIn(min = 64.dp)
                                            .then(
                                                if (groupSelectionMode) {
                                                    Modifier.toggleable(
                                                        value = selected,
                                                        role = Role.Checkbox,
                                                        onValueChange = { checked ->
                                                            if (checked) stagedSelection.add(row.objectIndex)
                                                            else stagedSelection.remove(row.objectIndex)
                                                        },
                                                    )
                                                } else {
                                                    Modifier.clickable(role = Role.Button) { onSelect(row.objectIndex) }
                                                },
                                            ).testTag("model-object-row-$index")
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        ObjectEnvelopePreview(
                                            widthMm = row.widthMm,
                                            depthMm = row.depthMm,
                                            heightMm = row.heightMm,
                                            color = row.color,
                                            previewMesh = row.previewMesh,
                                            modifier = Modifier.weight(0.6f).heightIn(min = 112.dp),
                                        )
                                        Column(
                                            Modifier.weight(0.4f),
                                            verticalArrangement = Arrangement.spacedBy(3.dp),
                                        ) {
                                            Text(row.name, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            Text(
                                                "T${row.tool}: ${row.material} · #${row.color.toUInt().toString(16).padStart(6, '0').takeLast(6).uppercase()}",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                stringResource(
                                                    R.string.ModelObjectDimensionsAndPlate,
                                                    formatObjectDimension(row.widthMm),
                                                    formatObjectDimension(row.depthMm),
                                                    formatObjectDimension(row.heightMm),
                                                    row.plateNumber,
                                                    row.plateCount,
                                                ),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        if (groupSelectionMode) {
                                            Checkbox(checked = selected, onCheckedChange = null)
                                        }
                                    }
                                    FlowRow(
                                        Modifier.fillMaxWidth().padding(start = 34.dp, end = 6.dp, bottom = 4.dp),
                                        maxItemsInEachRow = 2,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        TextButton(
                                            onClick = { onAssign(row.objectIndex) },
                                            enabled = row.assignEnabled,
                                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                        ) { Text(assignLabel, textAlign = TextAlign.Center) }
                                        if (row.parts.size > 1) TextButton(
                                            onClick = {
                                                expandedGroups = if (row.objectIndex in expandedGroups) expandedGroups - row.objectIndex
                                                else expandedGroups + row.objectIndex
                                            },
                                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                                .testTag("model-object-group-toggle-$index"),
                                        ) {
                                            val expanded = row.objectIndex in expandedGroups
                                            Text(
                                                stringResource(
                                                    if (expanded) R.string.ModelObjectCollapseParts else R.string.ModelObjectExpandParts,
                                                    row.parts.size,
                                                ),
                                                textAlign = TextAlign.Center,
                                            )
                                        }
                                    }
                                }
                            } else {
                                val part = row.parts[partIndex]
                                val partActionsLabel = stringResource(R.string.ModelObjectPartActions)
                                var partActionsExpanded by remember(entry.key) { mutableStateOf(false) }
                                Column(
                                    Modifier.fillMaxWidth().padding(start = 28.dp, end = 8.dp, bottom = 4.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                        .testTag("model-object-part-row-$index-$partIndex")
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().heightIn(min = 54.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Box(
                                            Modifier.size(18.dp)
                                                .background(Color(part.color), CircleShape)
                                                .semantics {
                                                    contentDescription = "${part.material}, T${part.tool}, #${part.color.toUInt().toString(16).padStart(6, '0').takeLast(6).uppercase()}"
                                                },
                                        )
                                        Column(Modifier.weight(1f)) {
                                            Text(part.label, fontWeight = FontWeight.Medium)
                                            Text("T${part.tool}: ${part.material}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Box {
                                            IconButton(
                                                onClick = { partActionsExpanded = true },
                                                modifier = Modifier.size(48.dp)
                                                    .testTag("model-object-part-actions-$index-$partIndex"),
                                            ) {
                                                Icon(
                                                    painter = painterResource(R.drawable.bed_menu_more_24),
                                                    contentDescription = "${part.label}: $partActionsLabel",
                                                    modifier = Modifier.size(31.dp),
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = partActionsExpanded,
                                                onDismissRequest = { partActionsExpanded = false },
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.ModelObjectAssignPartFilament)) },
                                                    onClick = { partActionsExpanded = false; onAssignPart(row.objectIndex, partIndex) },
                                                    enabled = row.assignEnabled,
                                                    modifier = Modifier.testTag("model-object-assign-part-$index-$partIndex"),
                                                )
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.ModelObjectPartProcessSettings)) },
                                                    onClick = { partActionsExpanded = false; onEditPartProcessSettings(row.objectIndex, partIndex) },
                                                    modifier = Modifier.testTag("model-object-part-process-settings-$index-$partIndex"),
                                                )
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.ModelObjectPartTransform)) },
                                                    onClick = { partActionsExpanded = false; onTransformPart(row.objectIndex, partIndex) },
                                                    modifier = Modifier.testTag("model-object-transform-part-$index-$partIndex"),
                                                )
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.ModelObjectEditPart)) },
                                                    onClick = { partActionsExpanded = false; onEditPart(row.objectIndex, partIndex) },
                                                    modifier = Modifier.testTag("model-object-edit-part-$index-$partIndex"),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    maxItemsInEachRow = 2,
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    if (groupSelectionMode) TextButton(
                        onClick = { onApplySelection(stagedSelection.sorted().toIntArray()) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("model-objects-selection-confirm"),
                    ) { Text(stringResource(R.string.ModelMenuApplySelection, stagedSelection.size), textAlign = TextAlign.Center) }
                    TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(cancelLabel) }
                }
            }
        }
    }
}

private fun formatObjectDimension(value: Double): String =
    String.format(Locale.getDefault(), "%.1f", value.coerceAtLeast(0.0))

/** A compact, size-accurate isometric envelope that makes similarly named parts easier to scan. */
@Composable
private fun ObjectEnvelopePreview(
    widthMm: Double,
    depthMm: Double,
    heightMm: Double,
    color: Int,
    previewMesh: FloatArray = FloatArray(0),
    modifier: Modifier = Modifier,
) {
    val tint = Color(color)
    val bounds = stringResource(
        R.string.ModelObjectPreviewDescription,
        formatObjectDimension(widthMm),
        formatObjectDimension(depthMm),
        formatObjectDimension(heightMm),
    )
    Canvas(modifier = modifier.semantics { contentDescription = bounds }) {
        if (previewMesh.size >= 9 && previewMesh.size % 9 == 0) {
            data class ProjectedTriangle(
                val points: List<androidx.compose.ui.geometry.Offset>,
                val depth: Float,
                val shade: Float,
            )
            val projected = ArrayList<Triple<Float, Float, Float>>(previewMesh.size / 3)
            var minX = Float.POSITIVE_INFINITY
            var minY = Float.POSITIVE_INFINITY
            var maxX = Float.NEGATIVE_INFINITY
            var maxY = Float.NEGATIVE_INFINITY
            for (index in previewMesh.indices step 3) {
                val x = previewMesh[index]
                val y = previewMesh[index + 1]
                val z = previewMesh[index + 2]
                val px = x - y
                val py = (x + y) * 0.5f - z
                projected += Triple(px, py, x + y + z)
                minX = minOf(minX, px); maxX = maxOf(maxX, px)
                minY = minOf(minY, py); maxY = maxOf(maxY, py)
            }
            val spanX = (maxX - minX).coerceAtLeast(1e-3f)
            val spanY = (maxY - minY).coerceAtLeast(1e-3f)
            val scale = minOf((size.width - 20f) / spanX, (size.height - 18f) / spanY).coerceAtLeast(0.01f)
            val offsetX = (size.width - spanX * scale) * 0.5f - minX * scale
            val offsetY = (size.height - spanY * scale) * 0.5f - minY * scale
            fun point(index: Int) = androidx.compose.ui.geometry.Offset(
                projected[index].first * scale + offsetX,
                projected[index].second * scale + offsetY,
            )
            val triangles = ArrayList<ProjectedTriangle>(projected.size / 3)
            for (triangle in 0 until projected.size / 3) {
                val a = triangle * 3
                val p0 = point(a); val p1 = point(a + 1); val p2 = point(a + 2)
                val ax = previewMesh[a * 3]; val ay = previewMesh[a * 3 + 1]; val az = previewMesh[a * 3 + 2]
                val bx = previewMesh[(a + 1) * 3]; val by = previewMesh[(a + 1) * 3 + 1]; val bz = previewMesh[(a + 1) * 3 + 2]
                val cx = previewMesh[(a + 2) * 3]; val cy = previewMesh[(a + 2) * 3 + 1]; val cz = previewMesh[(a + 2) * 3 + 2]
                val ux = bx - ax; val uy = by - ay
                val vx = cx - ax; val vy = cy - ay
                val normalZ = ux * vy - uy * vx
                val normalLength = kotlin.math.sqrt((ux * ux + uy * uy) * (vx * vx + vy * vy)).coerceAtLeast(1e-4f)
                val shade = (0.52f + 0.42f * kotlin.math.abs(normalZ) / normalLength).coerceIn(0.45f, 1f)
                triangles += ProjectedTriangle(
                    listOf(p0, p1, p2),
                    (projected[a].third + projected[a + 1].third + projected[a + 2].third) / 3f,
                    shade,
                )
            }
            triangles.sortedBy { it.depth }.forEach { triangle ->
                val path = Path().apply {
                    moveTo(triangle.points[0].x, triangle.points[0].y)
                    lineTo(triangle.points[1].x, triangle.points[1].y)
                    lineTo(triangle.points[2].x, triangle.points[2].y)
                    close()
                }
                drawPath(path, Color(
                    red = (tint.red * triangle.shade).coerceIn(0f, 1f),
                    green = (tint.green * triangle.shade).coerceIn(0f, 1f),
                    blue = (tint.blue * triangle.shade).coerceIn(0f, 1f),
                    alpha = 1f,
                ))
            }
            return@Canvas
        }
        val maxDimension = maxOf(widthMm, depthMm, heightMm, 1.0)
        val footprint = minOf(size.width * 0.72f, size.height * 0.58f).coerceAtLeast(12f)
        val scale = footprint / maxDimension.toFloat()
        val xLength = (widthMm.toFloat() * scale).coerceAtLeast(footprint * 0.08f)
        val yLength = (depthMm.toFloat() * scale).coerceAtLeast(footprint * 0.08f)
        val zLength = (heightMm.toFloat() * scale).coerceAtLeast(footprint * 0.12f)
        val originX = size.width * 0.5f
        val originY = size.height * 0.82f
        val x = xLength * 0.31f
        val y = yLength * 0.31f
        val lift = (zLength * 0.72f).coerceAtMost(size.height * 0.56f)
        val p0 = androidx.compose.ui.geometry.Offset(originX, originY)
        val px = androidx.compose.ui.geometry.Offset(originX + x, originY + x * 0.52f)
        val py = androidx.compose.ui.geometry.Offset(originX - y, originY + y * 0.52f)
        val pxy = androidx.compose.ui.geometry.Offset(originX + x - y, originY + (x + y) * 0.52f)
        val p0Top = androidx.compose.ui.geometry.Offset(p0.x, p0.y - lift)
        val pxTop = androidx.compose.ui.geometry.Offset(px.x, px.y - lift)
        val pyTop = androidx.compose.ui.geometry.Offset(py.x, py.y - lift)
        val pxyTop = androidx.compose.ui.geometry.Offset(pxy.x, pxy.y - lift)
        fun face(points: List<androidx.compose.ui.geometry.Offset>): Path = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
            close()
        }
        drawPath(face(listOf(p0, px, pxTop, p0Top)), tint.copy(alpha = 0.92f))
        drawPath(face(listOf(p0, py, pyTop, p0Top)), tint.copy(alpha = 0.68f))
        drawPath(face(listOf(p0Top, pxTop, pxyTop, pyTop)), tint.copy(alpha = 1f))
        val outline = Color.White.copy(alpha = 0.78f)
        drawLine(outline, p0, px, 1.4f)
        drawLine(outline, p0, py, 1.4f)
        drawLine(outline, p0, p0Top, 1.4f)
        drawLine(outline, px, pxTop, 1.4f)
        drawLine(outline, py, pyTop, 1.4f)
        drawLine(outline, pxTop, pxyTop, 1.4f)
        drawLine(outline, pyTop, pxyTop, 1.4f)
        drawCircle(tint.copy(alpha = 0.18f), radius = maxDimension.toFloat().coerceIn(8f, 20f), center = p0Top)
    }
}

@Composable
internal fun ModelObjectPartTransformDialog(
    cancelLabel: String,
    onDismiss: () -> Unit,
    onApply: (dx: Double, dy: Double, dz: Double, rx: Double, ry: Double, rz: Double, sx: Double, sy: Double, sz: Double) -> Unit,
) {
    val values = remember { mutableStateListOf("0", "0", "0", "0", "0", "0", "100", "100", "100") }
    val labels = listOf(
        R.string.ModelObjectPartMoveX,
        R.string.ModelObjectPartMoveY,
        R.string.ModelObjectPartMoveZ,
        R.string.ModelObjectPartRotateX,
        R.string.ModelObjectPartRotateY,
        R.string.ModelObjectPartRotateZ,
        R.string.ModelObjectPartScaleX,
        R.string.ModelObjectPartScaleY,
        R.string.ModelObjectPartScaleZ,
    )
    val parsed = values.map { it.trim().replace(',', '.').toDoubleOrNull() }
    val valid = parsed.all { it != null && it.isFinite() } &&
        parsed.take(3).all { kotlin.math.abs(requireNotNull(it)) <= 10_000.0 } &&
        parsed.drop(3).take(3).all { kotlin.math.abs(requireNotNull(it)) <= 36_000.0 } &&
        parsed.drop(6).all { requireNotNull(it) in 1.0..1000.0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ModelObjectPartTransformTitle)) },
        text = {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.ModelObjectPartTransformHint))
                if (!valid) Text(
                    stringResource(R.string.ModelObjectPartTransformInvalid),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("model-object-part-transform-error"),
                )
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    labels.forEachIndexed { index, label ->
                        OutlinedTextField(
                            value = values[index],
                            onValueChange = { input ->
                                values[index] = input.filter { it.isDigit() || it == '-' || it == '.' || it == ',' }.take(16)
                            },
                            label = { Text(stringResource(label)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth().testTag("model-object-part-transform-field-$index"),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    val numbers = parsed.map { requireNotNull(it) }
                    onApply(
                        numbers[0], numbers[1], numbers[2],
                        numbers[3], numbers[4], numbers[5],
                        numbers[6] / 100.0, numbers[7] / 100.0, numbers[8] / 100.0,
                    )
                },
                modifier = Modifier.heightIn(min = 48.dp).testTag("model-object-part-transform-apply"),
            ) { Text(stringResource(R.string.ModelObjectPartTransformApply)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(cancelLabel) }
        },
    )
}

@Composable
internal fun ModelChoiceDialog(
    title: String,
    cancelLabel: String,
    choices: List<String>,
    hint: String? = null,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                hint?.let { Text(it, Modifier.padding(bottom = 8.dp)) }
                LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = 480.dp)
                        .testTag("model-choice-dialog-options"),
                ) {
                    itemsIndexed(choices) { index, label ->
                        TextButton(onClick = { onSelect(index) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .testTag("model-choice-option-$index")) {
                            Text(label, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss, Modifier.heightIn(min = 48.dp)) { Text(cancelLabel) } },
    )
}

@Composable
internal fun DuplicateModelDialog(
    title: String,
    hint: String,
    confirmLabel: String,
    cancelLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var count by remember { mutableStateOf("1") }
    val countFocusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        withFrameNanos { }
        countFocusRequester.requestFocus()
        keyboard?.show()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(hint)
                OutlinedTextField(
                    value = count,
                    onValueChange = { input -> count = input.filter(Char::isDigit).take(2) },
                    label = { Text(title) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                        .focusRequester(countFocusRequester)
                        .testTag("duplicate-model-count"),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(count.toIntOrNull()?.coerceIn(1, 50) ?: 1) },
                Modifier.heightIn(min = 48.dp).testTag("duplicate-model-confirm"),
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, Modifier.heightIn(min = 48.dp)) { Text(cancelLabel) } },
    )
}

private val MaterialSelection: Color
    @Composable get() = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
