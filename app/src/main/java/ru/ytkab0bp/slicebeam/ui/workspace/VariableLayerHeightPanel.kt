package ru.ytkab0bp.slicebeam.ui.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.SliceBeam
import ru.ytkab0bp.slicebeam.events.NeedSnackbarEvent
import ru.ytkab0bp.slicebeam.utils.ViewUtils
import ru.ytkab0bp.slicebeam.view.GLView

@Composable
internal fun VariableLayerHeightPanel(
    glView: GLView,
    onExit: () -> Unit,
    onModelChanged: () -> Unit,
) {
    var quality by remember { mutableFloatStateOf(0.5f) }
    var brushEnabled by remember { mutableStateOf(false) }
    var brushAction by remember { mutableIntStateOf(2) }
    var brushBandWidth by remember { mutableFloatStateOf(5f) }
    var brushDelta by remember { mutableFloatStateOf(0.05f) }
    var status by remember { mutableIntStateOf(R.string.NoObjectSelected) }
    var selectedObjectCount by remember { mutableIntStateOf(0) }

    SideEffect {
        glView.variableLayerHeightBrushAction = brushAction
        glView.variableLayerHeightBrushBandWidthMm = brushBandWidth.toDouble()
        glView.variableLayerHeightBrushDeltaMm = brushDelta.toDouble()
        glView.variableLayerHeightQualityFactor = quality
    }
    fun refreshStatus() {
        glView.queueEvent {
            val renderer = glView.renderer
            val objectIndex = renderer.selectedObject
            val selectedCount = renderer.selectedObjectIndices().size
            val currentStatus = when {
                objectIndex < 0 -> R.string.NoObjectSelected
                renderer.model?.hasAdaptiveLayerHeight(objectIndex) == true -> R.string.VariableLayerAdaptive
                else -> R.string.VariableLayerDefault
            }
            ViewUtils.postOnMainThread {
                status = currentStatus
                selectedObjectCount = selectedCount
            }
        }
    }

    DisposableEffect(glView) {
        glView.onVariableLayerHeightPaintComplete = {
            onModelChanged()
            refreshStatus()
        }
        onDispose {
            glView.isVariableLayerHeightBrushActive = false
            glView.onVariableLayerHeightPaintComplete = null
        }
    }

    LaunchedEffect(glView) { refreshStatus() }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.VariableLayerTitle), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.VariableLayerStatus))
        Text(stringResource(status), color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.VariableLayerQuality, quality))
        Slider(value = quality, onValueChange = { quality = it }, valueRange = 0f..1f)
        Text(stringResource(R.string.VariableLayerBrushHelp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                R.string.VariableLayerBrushMoreDetail to 1,
                R.string.VariableLayerBrushLessDetail to 0,
            ).forEach { (label, value) ->
                if (brushAction == value) Button(onClick = { brushAction = value }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(label))
                } else OutlinedButton(onClick = { brushAction = value }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(label))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                R.string.VariableLayerBrushBase to 2,
                R.string.VariableLayerBrushSmooth to 3,
            ).forEach { (label, value) ->
                if (brushAction == value) Button(onClick = { brushAction = value }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(label))
                } else OutlinedButton(onClick = { brushAction = value }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(label))
                }
            }
        }
        Text(stringResource(R.string.VariableLayerBrushBandWidth, brushBandWidth))
        Slider(value = brushBandWidth, onValueChange = { brushBandWidth = it }, valueRange = 1.5f..10f)
        Text(stringResource(R.string.VariableLayerBrushStrength, brushDelta))
        Slider(value = brushDelta, onValueChange = { brushDelta = it }, valueRange = 0.01f..0.20f)
        Button(
            onClick = {
                if (brushEnabled) {
                    brushEnabled = false
                    glView.isVariableLayerHeightBrushActive = false
                } else {
                    val configPath = runCatching {
                        SliceBeam.genCurrentConfig()
                        SliceBeam.getCurrentConfigFile().absolutePath
                    }.getOrElse { error ->
                        SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.VariableLayerFailed, error.message))
                        return@Button
                    }
                    glView.variableLayerHeightConfigPath = configPath
                    glView.queueEvent {
                        val selected = glView.renderer.selectedObject
                        val result = if (selected >= 0) runCatching {
                            glView.renderer.prepareExactSurfaceRaycast(selected)
                        } else null
                        ViewUtils.postOnMainThread {
                            if (result == null) SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.NoObjectSelected))
                            else if (result.isFailure || result.getOrDefault(false).not())
                                SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.VariableLayerRaycastUnavailable))
                            else {
                                brushEnabled = true
                                glView.isVariableLayerHeightBrushActive = true
                            }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(stringResource(if (brushEnabled) R.string.VariableLayerBrushStop else R.string.VariableLayerBrushStart)) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    glView.queueEvent {
                        val index = glView.renderer.selectedObject
                        val model = glView.renderer.model
                        val result = if (index >= 0 && model != null) {
                            runCatching { model.clearAdaptiveLayerHeight(index) }
                        } else null
                        ViewUtils.postOnMainThread {
                            if (result == null) SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.NoObjectSelected))
                            else {
                                SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.VariableLayerCleared))
                                onModelChanged()
                                refreshStatus()
                                glView.requestRender()
                            }
                        }
                    }
                },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.ActionClear)) }
            Button(
                onClick = {
                    val configPath = runCatching {
                        SliceBeam.genCurrentConfig()
                        SliceBeam.getCurrentConfigFile().absolutePath
                    }.getOrElse { error ->
                        SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.VariableLayerFailed, error.message))
                        return@Button
                    }
                    glView.queueEvent {
                        val index = glView.renderer.selectedObject
                        val model = glView.renderer.model
                        val result = if (index >= 0 && model != null) runCatching {
                            model.applyAdaptiveLayerHeight(index, configPath, quality)
                        } else null
                        ViewUtils.postOnMainThread {
                            if (result == null) SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.NoObjectSelected))
                            else if (result.isFailure) SliceBeam.EVENT_BUS.fireEvent(
                                NeedSnackbarEvent(R.string.VariableLayerFailed, result.exceptionOrNull()?.message),
                            ) else {
                                SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.VariableLayerApplied))
                                onModelChanged()
                                refreshStatus()
                                glView.requestRender()
                            }
                        }
                    }
                },
                modifier = Modifier.weight(1.2f).heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.VariableLayerAdaptive)) }
            if (selectedObjectCount > 1) {
                Button(
                    onClick = {
                        val configPath = runCatching {
                            SliceBeam.genCurrentConfig()
                            SliceBeam.getCurrentConfigFile().absolutePath
                        }.getOrElse { error ->
                            SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.VariableLayerFailed, error.message))
                            return@Button
                        }
                        glView.queueEvent {
                            val model = glView.renderer.model
                            val indices = glView.renderer.selectedObjectIndices()
                            val result = if (model != null && indices.size > 1) runCatching {
                                model.applyAdaptiveLayerHeightToObjects(indices, configPath, quality)
                            } else null
                            ViewUtils.postOnMainThread {
                                if (result == null) SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.NoObjectSelected))
                                else if (result.isFailure || result.getOrDefault(false).not()) SliceBeam.EVENT_BUS.fireEvent(
                                    NeedSnackbarEvent(R.string.VariableLayerFailed, result.exceptionOrNull()?.message),
                                ) else {
                                    SliceBeam.EVENT_BUS.fireEvent(NeedSnackbarEvent(R.string.VariableLayerMultiApplied, indices.size))
                                    onModelChanged()
                                    refreshStatus()
                                    glView.requestRender()
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1.2f).heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.VariableLayerApplySelected)) }
            }
            OutlinedButton(onClick = onExit, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Text(stringResource(R.string.ActionDone))
            }
        }
    }
}
