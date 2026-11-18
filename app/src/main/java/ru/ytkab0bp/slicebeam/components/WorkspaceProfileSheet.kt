package ru.ytkab0bp.slicebeam.components

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.SliceBeam
import ru.ytkab0bp.slicebeam.SetupActivity
import ru.ytkab0bp.slicebeam.config.TemporaryProjectSettings
import ru.ytkab0bp.slicebeam.config.ProfileCompatibility
import ru.ytkab0bp.slicebeam.components.bed_menu.SliceChoiceField
import ru.ytkab0bp.slicebeam.ui.BeamComposeTheme
import ru.ytkab0bp.slicebeam.ui.OrkanUiTokens

/** Quick profile selection using the same Orca configuration used by slicing and profile editors. */
class WorkspaceProfileSheet(private val context: Context, private val onProfilesChanged: Runnable?) {
    private val config = SliceBeam.requireConfig()
    private val presets = requireNotNull(config.presets)
    private val printers = config.printerConfigs.filter { it.title != null }
    private val printerNames = printers.mapNotNull { it.title }
    private var filamentNames by mutableStateOf(ProfileCompatibility.filaments(config).mapNotNull { it.title })
    private var processNames by mutableStateOf(ProfileCompatibility.processes(config).mapNotNull { it.title })
    private val selectedPrinter = mutableIntStateOf(WorkspaceProfileSelectionLogic.selectedIndex(printerNames, presets["printer"]))
    private val selectedFilament = mutableIntStateOf(WorkspaceProfileSelectionLogic.selectedIndex(filamentNames, presets["filament"]))
    private val selectedProcess = mutableIntStateOf(WorkspaceProfileSelectionLogic.selectedIndex(processNames, presets["print"]))
    private var settingsSummary by mutableStateOf(createSettingsSummary(context))
    private var filamentChosen = false
    private var processChosen = false

    @Composable
    fun Content(onDismiss: () -> Unit) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                WorkspaceProfileSheetContent(
                    summary = settingsSummary,
                    printerNames = printerNames,
                    filamentNames = filamentNames,
                    processNames = processNames,
                    selectedPrinter = selectedPrinter.intValue,
                    selectedFilament = selectedFilament.intValue,
                    selectedProcess = selectedProcess.intValue,
                    onPrinterSelected = ::selectPrinter,
                    onFilamentSelected = { selectedFilament.intValue = it; filamentChosen = true },
                    onProcessSelected = ::selectProcess,
                    onAddPrinter = {
                        onDismiss()
                        context.startActivity(
                            Intent(context, SetupActivity::class.java)
                                .putExtra(SetupActivity.EXTRA_ADD_PRINTER, true),
                        )
                    },
                    onApply = { applySelection(onDismiss) },
                )
            }
        }
    }

    private fun applySelection(onDismiss: () -> Unit) {
        val chosen = WorkspaceProfileSelectionLogic.resolve(
            printerNames,
            selectedPrinter.intValue,
            filamentNames,
            selectedFilament.intValue,
            processNames,
            selectedProcess.intValue,
        )
        val temporary = TemporaryProjectSettings.active
        val printer = chosen.printer.takeIf { !temporary || it != presets["printer"] }
        val filament = chosen.filament.takeIf { !temporary || filamentChosen }
        val process = chosen.process.takeIf { !temporary || processChosen }
        if ((printer != null || filament != null || process != null) &&
            !SliceBeam.applyProfileSelection(printer, filament, process)) {
            Toast.makeText(context, R.string.ConfigSaveFailed, Toast.LENGTH_LONG).show()
            return
        }
        onProfilesChanged?.run()
        onDismiss()
    }

    /** Commit the printer as soon as it is chosen, then display the profiles it made active. */
    private fun selectPrinter(index: Int) {
        val printerName = printerNames.getOrNull(index) ?: return
        if (printerName == presets["printer"]) {
            selectedPrinter.intValue = index
            return
        }

        if (!SliceBeam.applyProfileSelection(printer = printerName)) {
            selectedPrinter.intValue = WorkspaceProfileSelectionLogic.selectedIndex(printerNames, presets["printer"])
            Toast.makeText(context, R.string.ConfigSaveFailed, Toast.LENGTH_LONG).show()
            return
        }

        selectedPrinter.intValue = index
        processNames = ProfileCompatibility.processes(config).mapNotNull { it.title }
        filamentNames = ProfileCompatibility.filaments(config).mapNotNull { it.title }
        selectedFilament.intValue = WorkspaceProfileSelectionLogic.selectedIndex(filamentNames, presets["filament"])
        selectedProcess.intValue = WorkspaceProfileSelectionLogic.selectedIndex(processNames, presets["print"])
        filamentChosen = false
        processChosen = false
        settingsSummary = createSettingsSummary(context)
        onProfilesChanged?.run()
    }

    private fun selectProcess(index: Int) {
        val process = processNames.getOrNull(index) ?: return
        selectedProcess.intValue = index
        processChosen = true
        filamentNames = ProfileCompatibility.filaments(config, selectedProcess = process).mapNotNull { it.title }
        selectedFilament.intValue = WorkspaceProfileSelectionLogic.selectedIndex(filamentNames, presets["filament"])
        settingsSummary = createSettingsSummary(context, process)
    }

    private fun createSettingsSummary(context: Context, processName: String? = null): String {
        val active = SliceBeam.buildCurrentConfigObject()
        processName?.let(config::findPrint)?.values?.let(active.values::putAll)
        val layer = value(active.get("layer_height"), "–").replace('.', ',')
        val walls = value(active.get("wall_loops"), "–")
        var infill = value(active.get("sparse_infill_density"), "–")
        if (infill != "–" && !infill.endsWith("%")) infill += "%"

        val support = when (active.get("enable_support")) {
            "1" -> context.getString(R.string.WorkspaceSettingsEnabled)
            "0" -> context.getString(R.string.WorkspaceSettingsDisabled)
            else -> context.getString(R.string.WorkspaceSettingsUnknown)
        }
        val brim = when (active.get("brim_type")) {
            null -> context.getString(R.string.WorkspaceSettingsUnknown)
            "no_brim" -> context.getString(R.string.WorkspaceSettingsDisabled)
            "auto_brim" -> context.getString(R.string.WorkspaceSettingsAutomatic)
            else -> context.getString(R.string.WorkspaceSettingsEnabled)
        }
        return context.getString(R.string.WorkspaceSettingsSummary, layer, walls, infill, support, brim)
    }

    private fun value(value: String?, fallback: String): String = value?.trim()?.takeIf { it.isNotEmpty() } ?: fallback
}

@Composable
private fun WorkspaceProfileSheetContent(
    summary: String,
    printerNames: List<String>,
    filamentNames: List<String>,
    processNames: List<String>,
    selectedPrinter: Int,
    selectedFilament: Int,
    selectedProcess: Int,
    onPrinterSelected: (Int) -> Unit,
    onFilamentSelected: (Int) -> Unit,
    onProcessSelected: (Int) -> Unit,
    onAddPrinter: () -> Unit,
    onApply: () -> Unit,
) {
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * OrkanUiTokens.sheetMaxHeightFraction
    val shape = RoundedCornerShape(
        topStart = OrkanUiTokens.sheetTopCornerRadius,
        topEnd = OrkanUiTokens.sheetTopCornerRadius,
    )
    Column(
        modifier = Modifier.fillMaxWidth()
            .heightIn(max = maxHeight)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(OrkanUiTokens.regularGap + OrkanUiTokens.compactGap),
        ) {
            Text(
                text = stringResource(R.string.WorkspaceProfilesTitle),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.WorkspaceProfilesDescription),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(OrkanUiTokens.cornerRadius),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.WorkspaceSettingsSummaryTitle),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        text = summary,
                        modifier = Modifier.testTag("workspace-profile-settings-summary"),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                }
            }
            val sections = WorkspaceProfileSelectionLogic.visibleSections(
                printerNames = printerNames,
                filamentNames = filamentNames,
                processNames = processNames,
            )
            if (sections.showMissingPrinterNotice) {
                Text(
                    text = stringResource(R.string.PrinterProfileMissingHint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
                Button(onClick = onAddPrinter, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.SettingsAddPrinter))
                }
            }
            if (sections.showPrinter) {
                SliceChoiceField(
                    label = stringResource(R.string.WorkspaceProfilePrinter),
                    selectedIndex = selectedPrinter,
                    options = printerNames,
                    onSelected = onPrinterSelected,
                )
            }
            if (sections.showPrinter && sections.showProcess) SliceChoiceField(
                label = stringResource(R.string.WorkspaceProfileProcess),
                selectedIndex = selectedProcess,
                options = processNames,
                onSelected = onProcessSelected,
            )
            if (sections.showPrinter && sections.showFilament) SliceChoiceField(
                label = stringResource(R.string.WorkspaceProfileFilament),
                selectedIndex = selectedFilament,
                options = filamentNames,
                onSelected = onFilamentSelected,
            )
            Spacer(modifier = Modifier.height(2.dp))
        }
        Surface(color = MaterialTheme.colorScheme.surface) {
            Button(
                onClick = onApply,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)
                    .heightIn(min = OrkanUiTokens.touchTarget),
                shape = RoundedCornerShape(OrkanUiTokens.cornerRadius),
            ) {
                Text(stringResource(R.string.WorkspaceProfilesApply), fontSize = 14.sp)
            }
        }
    }
}

internal data class WorkspaceProfileSelection(
    val printer: String?,
    val filament: String?,
    val process: String?,
)

/** Keeps picker fallback, selected profile names and change detection independent of Android views. */
internal object WorkspaceProfileSelectionLogic {
    internal data class VisibleSections(
        val showMissingPrinterNotice: Boolean,
        val showPrinter: Boolean,
        val showFilament: Boolean,
        val showProcess: Boolean,
    )

    fun visibleSections(
        printerNames: List<String>,
        filamentNames: List<String>,
        processNames: List<String>,
    ): VisibleSections = VisibleSections(
        showMissingPrinterNotice = printerNames.isEmpty(),
        showPrinter = printerNames.isNotEmpty(),
        showFilament = printerNames.isNotEmpty() && filamentNames.isNotEmpty(),
        showProcess = printerNames.isNotEmpty() && processNames.isNotEmpty(),
    )

    fun selectedIndex(options: List<String>, selected: String?): Int =
        options.indexOf(selected).takeIf { it >= 0 } ?: 0

    fun resolve(
        printerOptions: List<String>, printerIndex: Int,
        filamentOptions: List<String>, filamentIndex: Int,
        processOptions: List<String>, processIndex: Int,
    ): WorkspaceProfileSelection = WorkspaceProfileSelection(
        printerOptions.getOrNull(printerIndex),
        filamentOptions.getOrNull(filamentIndex),
        processOptions.getOrNull(processIndex),
    )

    fun didChange(before: WorkspaceProfileSelection, after: WorkspaceProfileSelection): Boolean = before != after
}
