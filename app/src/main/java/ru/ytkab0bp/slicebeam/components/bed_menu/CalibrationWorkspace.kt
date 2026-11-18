package ru.ytkab0bp.slicebeam.components.bed_menu

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.SliceBeam
import ru.ytkab0bp.slicebeam.config.ConfigObject
import ru.ytkab0bp.slicebeam.config.ProfileStringVectorCodec
import ru.ytkab0bp.slicebeam.config.ProfileSettingsRepository
import ru.ytkab0bp.slicebeam.events.NeedSnackbarEvent
import ru.ytkab0bp.slicebeam.events.ObjectsListChangedEvent
import ru.ytkab0bp.slicebeam.fragment.BedFragment
import ru.ytkab0bp.slicebeam.fragment.ProfileConfigValueController
import ru.ytkab0bp.slicebeam.ui.BeamComposeTheme
import ru.ytkab0bp.slicebeam.utils.ViewUtils
import java.io.File
import java.io.FileOutputStream

/** Opens calibration actions, result entry and bundled models in one workspace-owned route. */
internal fun showCalibrationWorkspace(bed: BedFragment) {
    if (bed.getContext() == null) return
    bed.showWorkspaceComposeDialog { dismiss ->
        BeamComposeTheme {
            CalibrationWorkspaceDialog(bed, dismiss)
        }
    }
}

private enum class CalibrationWorkspacePage { ACTIONS, RESULT, MODELS }

@Composable
private fun CalibrationWorkspaceDialog(bed: BedFragment, dismissWorkspace: () -> Unit) {
    val context = bed.getContext() ?: return
    var page by remember { mutableStateOf(CalibrationWorkspacePage.ACTIONS) }
    var selectedPreset by remember { mutableStateOf<CalibrationPreset?>(null) }
    val models = remember(context) {
        listOf(
            CalibrationModel("Orca Cube", R.drawable.model_thumb_orcacube_v2, listOf("OrcaCube_v2.stl", "OrcaPlug_v2.stl")),
            CalibrationModel("Orca Tolerance Test", R.drawable.model_thumb_orcatolerancetest, listOf("OrcaToleranceTest.stl")),
            CalibrationModel(context.getString(R.string.MenuFileCalibrationsModels3DBenchy), R.drawable.model_thumb_3dbenchy, listOf("3dbenchy.stl")),
            CalibrationModel("Cali Cat", R.drawable.model_thumb_calicat, listOf("calicat.stl")),
            CalibrationModel("Autodesk FDM Test", R.drawable.model_thumb_ksr_fdmtest_v4, listOf("ksr_fdmtest_v4.stl")),
            CalibrationModel("Voron Cube", R.drawable.model_thumb_voron_design_cube_v7, listOf("Voron_Design_Cube_v7.stl")),
            CalibrationModel("Stanford Bunny", R.drawable.model_thumb_stanford_bunny, listOf("Stanford_Bunny.stl")),
            CalibrationModel("Orca String Hell", R.drawable.model_thumb_orca_stringhell, listOf("Orca_stringhell.stl")),
        )
    }
    val titleRes = when {
        selectedPreset != null -> selectedPreset!!.titleRes
        page == CalibrationWorkspacePage.RESULT -> R.string.CalibrationRecordResult
        page == CalibrationWorkspacePage.MODELS -> R.string.MenuFileCalibrationsModels
        else -> R.string.MenuFileCalibrations
    }

    AlertDialog(
        onDismissRequest = {
            if (selectedPreset != null || page != CalibrationWorkspacePage.ACTIONS) {
                selectedPreset = null
                page = CalibrationWorkspacePage.ACTIONS
            } else {
                dismissWorkspace()
            }
        },
        title = { Text(stringResource(titleRes), modifier = Modifier.testTag("calibration-dialog-title")) },
        text = {
            when {
                selectedPreset != null -> CalibrationConfigurationContent(
                    preset = selectedPreset!!,
                    requiredFilamentCount = bed.currentModel?.requiredFilamentCount ?: 1,
                    onStart = { start, endOrStep, step, filamentId ->
                        val preset = selectedPreset ?: return@CalibrationConfigurationContent
                        selectedPreset = null
                        startCalibration(context, bed, preset, start, endOrStep, step, filamentId, dismissWorkspace)
                    },
                )
                page == CalibrationWorkspacePage.RESULT -> CalibrationResultContent(
                    onSave = { mode, rawValue ->
                        val target = CalibrationResultPolicy.targetFor(mode)
                        val value = target?.let { CalibrationResultPolicy.parseValue(rawValue, it) }
                        if (target == null || value == null) {
                            false
                        } else {
                            val config = SliceBeam.requireConfig()
                            val profileKey = if (target.profileType == ConfigObject.PROFILE_LIST_FILAMENT) "filament" else "print"
                            val profileName = config.presets?.get(profileKey)
                            val profile = when (target.profileType) {
                                ConfigObject.PROFILE_LIST_FILAMENT -> config.findFilament(profileName)
                                ConfigObject.PROFILE_LIST_PRINT -> config.findPrint(profileName)
                                else -> null
                            } ?: return@CalibrationResultContent false
                            ProfileConfigValueController.stageValue(
                                target.profileType,
                                profile,
                                target.key,
                                guiType = null,
                                index = target.vectorIndex,
                                editedValue = value,
                                defaultValue = profile.get(target.key),
                            )
                            Toast.makeText(context, R.string.CalibrationResultStaged, Toast.LENGTH_LONG).show()
                            page = CalibrationWorkspacePage.ACTIONS
                            true
                        }
                    },
                )
                page == CalibrationWorkspacePage.MODELS -> CalibrationModelsContent(
                    models = models,
                    onSelect = { model -> loadCalibrationModels(bed, model.assets, dismissWorkspace) },
                )
                else -> CalibrationActionsContent(
                    onCalibration = { selectedPreset = it },
                    onRecordResult = { page = CalibrationWorkspacePage.RESULT },
                    onModels = { page = CalibrationWorkspacePage.MODELS },
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            val isMainPage = selectedPreset == null && page == CalibrationWorkspacePage.ACTIONS
            TextButton(
                onClick = {
                    if (isMainPage) dismissWorkspace()
                    else {
                        selectedPreset = null
                        page = CalibrationWorkspacePage.ACTIONS
                    }
                },
                modifier = Modifier.heightIn(min = 48.dp).testTag("calibration-dialog-back"),
            ) {
                Text(stringResource(if (isMainPage) android.R.string.cancel else R.string.MenuOrientationPositionBack))
            }
        },
    )
}

private fun startCalibration(
    context: Context,
    bed: BedFragment,
    preset: CalibrationPreset,
    start: Double,
    endOrStep: Double,
    step: Double?,
    filamentId: Int,
    dismissWorkspace: () -> Unit,
) {
    val bedGeometry = bed.glView?.renderer?.bed
    if (bedGeometry == null || !bedGeometry.isValid()) {
        Toast.makeText(context, R.string.BedConfigurationError, Toast.LENGTH_SHORT).show()
        return
    }
    SliceBeam.PENDING_CALIB_MODE = preset.mode
    SliceBeam.PENDING_CALIB_START = start
    SliceBeam.PENDING_CALIB_END = if (preset.inputMode != CalibrationInputMode.START_AND_STEP) endOrStep else 0.0
    SliceBeam.PENDING_CALIB_STEP = step ?: preset.step ?: endOrStep
    SliceBeam.PENDING_CALIB_FILAMENT = filamentId
    // The plate model is committed to BedFragment before the renderer has finished its GL
    // handoff. Prefer that authoritative plate state and use the renderer as a fallback for
    // edits which have not yet been committed back to the plate list.
    val modelAvailable = bed.currentModel?.objectsCount?.let { it > 0 } == true ||
        bed.glView?.renderer?.model?.objectsCount?.let { it > 0 } == true
    if (!modelAvailable) {
        if (preset.mode != CalibrationPresets.PA_LINE) {
            clearPendingCalibration()
            Toast.makeText(context, R.string.CalibrationNeedsModel, Toast.LENGTH_LONG).show()
            return
        }
        ensureCalibrationPlaceholderModel(bed) { finishCalibrationSelection(context, dismissWorkspace) }
    } else {
        finishCalibrationSelection(context, dismissWorkspace)
    }
}

private fun finishCalibrationSelection(context: Context, dismissWorkspace: () -> Unit) {
    Toast.makeText(context, R.string.CalibrationReadyToSlice, Toast.LENGTH_LONG).show()
    dismissWorkspace()
}

private fun clearPendingCalibration() {
    SliceBeam.PENDING_CALIB_MODE = 0
    SliceBeam.PENDING_CALIB_START = 0.0
    SliceBeam.PENDING_CALIB_END = 0.0
    SliceBeam.PENDING_CALIB_STEP = 0.0
    SliceBeam.PENDING_CALIB_FILAMENT = 0
}

private fun ensureCalibrationPlaceholderModel(bed: BedFragment, onLoaded: () -> Unit) {
    val file = File(SliceBeam.getModelCacheDir(), "calibration_pa_test.stl")
    Thread({
        try {
            SliceBeam.requireInstance().assets.open("models/pa_test.stl").use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            }
            ViewUtils.postOnMainThread {
                bed.loadModel(file, false, 1, object : BedFragment.ModelLoadCallback {
                    override fun onLoaded(loadedModel: ru.ytkab0bp.slicebeam.slic3r.Model, firstNewObject: Int, addedObjects: Int) {
                        ViewUtils.postOnMainThread {
                            SliceBeam.EVENT_BUS.fireEvent(ObjectsListChangedEvent())
                            onLoaded()
                        }
                    }

                    override fun onFailed(error: Exception) {
                        clearPendingCalibration()
                        showCalibrationLoadError(bed, error)
                    }
                })
            }
        } catch (error: Exception) {
            android.util.Log.e("FileMenu", "Failed to load PA placeholder model", error)
            clearPendingCalibration()
            ViewUtils.postOnMainThread { showCalibrationLoadError(bed, error) }
        }
    }, "CalibrationPlaceholderLoader").start()
}

private fun loadCalibrationModels(bed: BedFragment, assetNames: List<String>, dismissWorkspace: () -> Unit) {
    dismissWorkspace()
    ViewUtils.postOnMainThread({
        Thread({
            val files = mutableListOf<File>()
            try {
                assetNames.forEach { assetName ->
                    val file = File(SliceBeam.getModelCacheDir(), "calibration_model_$assetName")
                    SliceBeam.requireInstance().assets.open("models/$assetName").use { input ->
                        FileOutputStream(file).use { output -> input.copyTo(output) }
                    }
                    files += file
                }
                ViewUtils.postOnMainThread { loadNextCalibrationModel(bed, files, 0) }
            } catch (error: Exception) {
                files.forEach(File::delete)
                ViewUtils.postOnMainThread { showCalibrationLoadError(bed, error) }
            }
        }, "CalibrationModelAssetLoader").start()
    }, 180)
}

/** Loads multi-part presets one at a time; BedFragment rejects overlapping native imports. */
private fun loadNextCalibrationModel(bed: BedFragment, files: List<File>, index: Int) {
    if (index >= files.size) {
        files.forEach(File::delete)
        SliceBeam.EVENT_BUS.fireEvent(ObjectsListChangedEvent())
        SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.MenuFileOpenFileLoaded))
        return
    }
    try {
        bed.loadModel(files[index], false, 1, object : BedFragment.ModelLoadCallback {
            override fun onLoaded(loadedModel: ru.ytkab0bp.slicebeam.slic3r.Model, firstNewObject: Int, addedObjects: Int) {
                ViewUtils.postOnMainThread { loadNextCalibrationModel(bed, files, index + 1) }
            }

            override fun onFailed(error: Exception) {
                files.forEach(File::delete)
                showCalibrationLoadError(bed, error)
            }
        })
    } catch (error: Exception) {
        files.forEach(File::delete)
        showCalibrationLoadError(bed, error)
    }
}

private fun showCalibrationLoadError(bed: BedFragment, error: Exception) {
    val context = bed.getContext() ?: return
    bed.showWorkspaceMessageDialog(
        title = context.getString(R.string.MenuFileOpenFileFailed),
        message = error.toString(),
        confirmLabel = context.getString(android.R.string.ok),
    )
}

private data class CalibrationModel(val title: String, val icon: Int, val assets: List<String>)

@Composable
private fun CalibrationActionsContent(
    onCalibration: (CalibrationPreset) -> Unit,
    onRecordResult: () -> Unit,
    onModels: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp).testTag("calibration-actions-list"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(CalibrationPresets.supported()) { preset ->
            CalibrationActionRow(
                icon = if (preset.mode == CalibrationPresets.PA_LINE || preset.mode == CalibrationPresets.PA_TOWER) R.drawable.menu_calibrate_la_28 else R.drawable.deployed_code_24,
                title = stringResource(preset.titleRes),
                subtitle = stringResource(preset.descriptionRes),
                testTag = "calibration-preset-${preset.mode}",
                onClick = { onCalibration(preset) },
            )
        }
        item {
            CalibrationActionRow(
                icon = R.drawable.deployed_code_24,
                title = stringResource(R.string.CalibrationRecordResult),
                subtitle = stringResource(R.string.CalibrationRecordResultDescription),
                testTag = "calibration-record-result",
                onClick = onRecordResult,
            )
        }
        item {
            CalibrationActionRow(
                icon = R.drawable.deployed_code_24,
                title = stringResource(R.string.MenuFileCalibrationsModels),
                subtitle = stringResource(R.string.MenuFileCalibrationsModelsDescription),
                testTag = "calibration-models-open",
                onClick = onModels,
            )
        }
    }
}

@Composable
private fun CalibrationConfigurationContent(
    preset: CalibrationPreset,
    requiredFilamentCount: Int,
    onStart: (Double, Double, Double?, Int) -> Unit,
) {
    val config = remember(preset.mode) { SliceBeam.buildCurrentConfigObject() }
    val profileTemp = config["nozzle_temperature"]?.substringBefore(';')?.toDoubleOrNull()
        ?: config["nozzle_temperature_initial_layer"]?.substringBefore(';')?.toDoubleOrNull()
        ?: preset.start
    val initialStart = if (preset.mode == CalibrationPresets.TEMP_TOWER) profileTemp else preset.start
    val initialEnd = if (preset.mode == CalibrationPresets.TEMP_TOWER) profileTemp - 20.0 else preset.endOrStep
    var start by remember(preset.mode) { mutableStateOf(initialStart.toString()) }
    var endOrStep by remember(preset.mode) { mutableStateOf(initialEnd.toString()) }
    var step by remember(preset.mode) { mutableStateOf((preset.step ?: 0.0).toString()) }
    var invalid by remember(preset.mode) { mutableStateOf(false) }
    val filamentTypes = remember(config) { ProfileStringVectorCodec.parse(config["filament_type"].orEmpty()).orEmpty() }
    val filamentCount = remember(config, preset.mode) {
        CalibrationFilamentSelectionLogic.count(config.values, requiredFilamentCount)
    }
    var selectedFilament by remember(preset.mode) { mutableStateOf(0) }
    var filamentMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(if (preset.mode == CalibrationPresets.TEMP_TOWER) R.string.CalibrationTemperatureProfileHint else R.string.CalibrationParameterHint),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
        if (filamentCount > 1) {
            Text(stringResource(R.string.CalibrationFilament), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box {
                TextButton(
                    onClick = { filamentMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("calibration-filament-selector"),
                ) {
                    val material = filamentTypes.getOrNull(selectedFilament).orEmpty().ifBlank { "PLA" }
                    Text("T$selectedFilament · $material", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                DropdownMenu(expanded = filamentMenuExpanded, onDismissRequest = { filamentMenuExpanded = false }) {
                    (0 until filamentCount).forEach { index ->
                        DropdownMenuItem(
                            text = { Text("T$index · ${filamentTypes.getOrNull(index).orEmpty().ifBlank { "PLA" }}") },
                            onClick = { selectedFilament = index; filamentMenuExpanded = false },
                        )
                    }
                }
            }
        }
        CalibrationNumberField(stringResource(R.string.CalibrationStart), start, { start = it; invalid = false })
        if (preset.inputMode != CalibrationInputMode.START_AND_STEP) {
            CalibrationNumberField(stringResource(R.string.CalibrationEnd), endOrStep, { endOrStep = it; invalid = false })
        }
        if (preset.inputMode == CalibrationInputMode.RANGE_AND_STEP) {
            CalibrationNumberField(stringResource(R.string.CalibrationStep), step, { step = it; invalid = false })
        } else if (preset.inputMode == CalibrationInputMode.START_AND_STEP) {
            CalibrationNumberField(stringResource(R.string.CalibrationIncrement), endOrStep, { endOrStep = it; invalid = false })
        }
        if (invalid) Text(stringResource(R.string.CalibrationInvalidParameters), color = MaterialTheme.colorScheme.error)
        TextButton(
            onClick = {
                val startValue = start.trim().replace(',', '.').toDoubleOrNull()
                val endValue = endOrStep.trim().replace(',', '.').toDoubleOrNull()
                val stepValue = if (preset.inputMode == CalibrationInputMode.RANGE_AND_STEP) step.trim().replace(',', '.').toDoubleOrNull() else null
                if (startValue == null || endValue == null ||
                    (preset.inputMode == CalibrationInputMode.RANGE_AND_STEP && stepValue == null) ||
                    !CalibrationPresets.validate(preset, startValue, endValue, stepValue)) {
                    invalid = true
                } else onStart(startValue, endValue, stepValue, selectedFilament)
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("calibration-prepare"),
        ) { Text(stringResource(R.string.CalibrationPrepare)) }
    }
}

@Composable
private fun CalibrationResultContent(onSave: (Int, String) -> Boolean) {
    val modes = CalibrationPresets.supported().map { it.mode }
        .distinctBy { CalibrationResultPolicy.targetFor(it) }
    var mode by remember { mutableStateOf(CalibrationPresets.PA_LINE) }
    var expanded by remember { mutableStateOf(false) }
    var value by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.CalibrationRecordResultDescription),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
        Box {
            TextButton(onClick = { expanded = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(calibrationTitle(mode)))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                modes.forEach { candidate ->
                    DropdownMenuItem(
                        text = { Text(stringResource(calibrationTitle(candidate))) },
                        onClick = { mode = candidate; expanded = false; invalid = false },
                    )
                }
            }
        }
        CalibrationNumberField(stringResource(R.string.CalibrationMeasuredValue), value, {
            value = it
            invalid = false
        }, testTag = "calibration-result-value")
        if (invalid) Text(stringResource(R.string.CalibrationResultInvalid), color = MaterialTheme.colorScheme.error)
        TextButton(
            onClick = { if (!onSave(mode, value)) invalid = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("calibration-save-result"),
        ) { Text(stringResource(R.string.CalibrationSaveResult)) }
    }
}

private fun calibrationTitle(mode: Int): Int = when (mode) {
    CalibrationPresets.PA_LINE, CalibrationPresets.PA_TOWER -> R.string.CalibrationPressureAdvanceShort
    CalibrationPresets.TEMP_TOWER -> R.string.CalibrationTemperatureTower
    CalibrationPresets.VOLUMETRIC_SPEED_TOWER -> R.string.CalibrationVolumetricSpeedTower
    CalibrationPresets.VFA_TOWER -> R.string.CalibrationVfaTower
    CalibrationPresets.RETRACTION_TOWER -> R.string.CalibrationRetractionTower
    else -> R.string.CalibrationPressureAdvanceShort
}

@Composable
private fun CalibrationNumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    testTag: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { candidate ->
            if (candidate.length <= 12 && candidate.all { it.isDigit() || it == '.' || it == ',' || it == '-' }) onValueChange(candidate)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth().then(testTag?.let(Modifier::testTag) ?: Modifier),
    )
}

@Composable
internal fun CalibrationActionRow(
    icon: Int,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val largeText = ru.ytkab0bp.slicebeam.ui.LocalBeamLayoutFontScale.current >= 1.3f
    Surface(
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp).testTag(testTag)
            .semantics { contentDescription = "$title. $subtitle" }
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
                modifier = Modifier.size(31.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = if (largeText) 3 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = if (largeText) 4 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CalibrationModelsContent(models: List<CalibrationModel>, onSelect: (CalibrationModel) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(models) { model ->
            Surface(
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("calibration-model-${model.title}")
                    .clickable(role = Role.Button) { onSelect(model) },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Image(
                        painter = painterResource(model.icon),
                        contentDescription = model.title,
                        modifier = Modifier.size(31.dp).clip(RoundedCornerShape(8.dp)),
                    )
                    Text(
                        text = model.title,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
