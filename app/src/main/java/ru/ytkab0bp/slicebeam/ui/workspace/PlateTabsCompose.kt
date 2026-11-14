package ru.ytkab0bp.slicebeam.ui.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.ui.OrkanUiTokens
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

internal data class PlateCloseDecision(val selectedIndex: Int, val resetOnlyPlate: Boolean)

/** Normalized XY footprint of a model, computed from existing model bounds off the UI thread. */
internal data class PlateFootprint(val x: Float, val y: Float, val width: Float, val height: Float)
internal data class PlateThumbnail(val footprints: List<PlateFootprint>)

internal enum class PlatePickerLayout { INLINE, CIRCLE, GRID }

internal object PlatePickerLayoutPolicy {
    fun choose(plateCount: Int, screenWidthDp: Int, screenHeightDp: Int,
               fontScale: Float = 1f): PlatePickerLayout = when {
        plateCount <= 2 -> PlatePickerLayout.INLINE
        plateCount <= 6 && screenWidthDp >= 400 && screenHeightDp >= 480 && fontScale < 1.2f -> PlatePickerLayout.CIRCLE
        else -> PlatePickerLayout.GRID
    }
}

internal object PlateTabsPolicy {
    fun afterClose(plateCount: Int, selectedIndex: Int, closedIndex: Int): PlateCloseDecision? {
        if (plateCount <= 0 || selectedIndex !in 0 until plateCount || closedIndex !in 0 until plateCount) return null
        if (plateCount == 1) return PlateCloseDecision(0, true)
        val remainingCount = plateCount - 1
        val next = when {
            closedIndex == selectedIndex -> (closedIndex - 1).coerceIn(0, remainingCount - 1)
            closedIndex < selectedIndex -> selectedIndex - 1
            else -> selectedIndex
        }
        return PlateCloseDecision(next, false)
    }
}

/** A single control leaves the full width of the renderer available. */
@Composable
internal fun PlateTabs(
    openState: MutableState<Boolean>,
    plateCount: Int,
    selectedIndex: Int,
    objectCounts: List<Int>,
    thumbnails: List<PlateThumbnail>,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
    onClose: (Int) -> Unit,
    onAdd: () -> Unit,
) {
    var open by openState
    val closePicker = {
        open = false
        onDismiss()
    }
    val configuration = LocalConfiguration.current
    val screenHeightDp = configuration.screenHeightDp
    val fontScale = ru.ytkab0bp.slicebeam.ui.LocalBeamLayoutFontScale.current
    val estimatedGridColumns = ((configuration.screenWidthDp - 88) / 104).coerceIn(1, 4)
    val pickerLayout = PlatePickerLayoutPolicy.choose(plateCount, configuration.screenWidthDp,
        screenHeightDp, fontScale)
    val label = stringResource(R.string.PlatePickerCounterDescription, selectedIndex + 1, plateCount)
    val counterMinHeight = if (fontScale >= 1.15f) 56.dp else OrkanUiTokens.touchTarget
    Surface(
        // The two counter labels scale with system font size. Keep the 48 dp minimum
        // touch target, but let the control grow vertically instead of clipping `/count`.
        modifier = Modifier.width(OrkanUiTokens.plateCounterWidth)
            .heightIn(min = counterMinHeight)
            .semantics { contentDescription = label }
            .clickable(role = Role.Button) { open = true; onOpen() },
        shape = RoundedCornerShape(OrkanUiTokens.cornerRadius),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(stringResource(R.string.PlatePickerCounterLabel), style = MaterialTheme.typography.labelSmall,
                maxLines = 1)
            Text("${selectedIndex + 1}/$plateCount", fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
    if (open) {
        Dialog(onDismissRequest = closePicker, properties = DialogProperties(usePlatformDefaultWidth = true)) {
            Surface(shape = RoundedCornerShape(OrkanUiTokens.dialogCornerRadius), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(OrkanUiTokens.panelPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.PlatePickerTitle), style = MaterialTheme.typography.titleMedium)
                    if (pickerLayout == PlatePickerLayout.INLINE) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically) {
                            (0 until plateCount).forEach { index ->
                                PlatePickerCard(index, index == selectedIndex, objectCounts.getOrNull(index) ?: -1,
                                    { onSelect(index); closePicker() }, { onClose(index) },
                                    Modifier.padding(end = OrkanUiTokens.regularGap), thumbnails.getOrNull(index))
                            }
                            AddPlateButton { onAdd(); closePicker() }
                        }
                    } else if (pickerLayout == PlatePickerLayout.CIRCLE) {
                        Box(Modifier.size(300.dp), contentAlignment = Alignment.Center) {
                            (0 until plateCount).forEach { index ->
                                val angle = -PI / 2 + (2 * PI * index / plateCount)
                                PlatePickerCard(
                                    index, index == selectedIndex, objectCounts.getOrNull(index) ?: -1,
                                    { onSelect(index); closePicker() }, { onClose(index) },
                                    Modifier.offset((95 * cos(angle)).dp, (95 * sin(angle)).dp),
                                    thumbnails.getOrNull(index),
                                )
                            }
                            AddPlateButton { onAdd(); closePicker() }
                        }
                    } else LazyVerticalGrid(
                        columns = GridCells.Adaptive(96.dp),
                        state = rememberLazyGridState(initialFirstVisibleItemIndex = selectedIndex.coerceIn(0, (plateCount - 1).coerceAtLeast(0))),
                        modifier = Modifier.fillMaxWidth().height((
                            ((plateCount + estimatedGridColumns) / estimatedGridColumns) * 118
                        ).coerceIn(110, (screenHeightDp - 150).coerceAtLeast(110).coerceAtMost(420)).dp),
                        horizontalArrangement = Arrangement.spacedBy(OrkanUiTokens.regularGap),
                        verticalArrangement = Arrangement.spacedBy(OrkanUiTokens.regularGap),
                    ) {
                        items(plateCount, key = { it }) { index ->
                            PlatePickerCard(index, index == selectedIndex, objectCounts.getOrNull(index) ?: -1,
                                { onSelect(index); closePicker() }, { onClose(index) }, thumbnail = thumbnails.getOrNull(index))
                        }
                        item(key = "add") {
                            AddPlateButton { onAdd(); closePicker() }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = closePicker, modifier = Modifier.heightIn(min = OrkanUiTokens.touchTarget)) {
                            Text(stringResource(android.R.string.cancel))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlatePickerCard(index: Int, selected: Boolean, objectCount: Int,
                            onSelect: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier,
                            thumbnail: PlateThumbnail? = null) {
    val selectDescription = stringResource(R.string.PlatePickerSelectDescription,
        (index + 1).toString())
    val deleteDescription = stringResource(R.string.PlateCloseDescription,
        stringResource(R.string.PlateNumber, (index + 1).toString()))
    Surface(modifier = modifier.size(width = 88.dp, height = 110.dp),
        shape = RoundedCornerShape(OrkanUiTokens.cornerRadius),
        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.fillMaxWidth().height(62.dp)
                .semantics { contentDescription = selectDescription }
                .clickable(role = Role.Button, onClick = onSelect),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text((index + 1).toString(), style = MaterialTheme.typography.titleMedium)
                    PlateFootprintPreview(thumbnail, Modifier.padding(start = 4.dp).size(30.dp))
                }
                Text(if (objectCount < 0) "…" else pluralStringResource(R.plurals.PlateObjectCount, objectCount, objectCount),
                    style = MaterialTheme.typography.labelSmall)
            }
            Box(Modifier.fillMaxWidth().height(OrkanUiTokens.touchTarget)
                .semantics { contentDescription = deleteDescription }
                .clickable(role = Role.Button, onClick = onClose), contentAlignment = Alignment.Center) {
                Text("×", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun PlateFootprintPreview(thumbnail: PlateThumbnail?, modifier: Modifier) {
    val lineColor = MaterialTheme.colorScheme.outline
    val objectColor = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        drawRect(lineColor, style = Stroke(width = 1.dp.toPx()))
        thumbnail?.footprints?.forEach { footprint ->
            drawRect(objectColor,
                topLeft = Offset(footprint.x * size.width, (1f - footprint.y - footprint.height) * size.height),
                size = Size((footprint.width * size.width).coerceAtLeast(2.dp.toPx()),
                    (footprint.height * size.height).coerceAtLeast(2.dp.toPx())))
        }
    }
}

@Composable
private fun AddPlateButton(onClick: () -> Unit) {
    val description = stringResource(R.string.MenuAddPlate)
    Box(Modifier.size(OrkanUiTokens.touchTarget)
        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(OrkanUiTokens.cornerRadius))
        .semantics { contentDescription = description }
        .clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Text("+", style = MaterialTheme.typography.headlineMedium)
    }
}
