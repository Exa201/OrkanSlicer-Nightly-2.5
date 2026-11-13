package ru.ytkab0bp.slicebeam.fragment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.render.PrimeTowerDragLogic
import ru.ytkab0bp.slicebeam.render.PrimeTowerPlacementMap
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

@Composable
internal fun PrintConfigToolsBar(
    modifier: Modifier = Modifier,
    onPrimeTower: () -> Unit,
    onFlushVolumes: () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PrintToolButton(
            modifier = Modifier.weight(1f),
            icon = R.drawable.print_tower_position_24,
            label = stringResource(R.string.PrimeTowerPositionShort),
            description = stringResource(R.string.PrimeTowerPositionInlineHelp),
            onClick = onPrimeTower,
        )
        PrintToolButton(
            modifier = Modifier.weight(1f),
            icon = R.drawable.print_purge_volume_24,
            label = stringResource(R.string.FlushVolumes),
            description = stringResource(R.string.PrintConfigFlushVolumesHelp),
            onClick = onFlushVolumes,
        )
    }
}

@Composable
private fun PrintToolButton(
    modifier: Modifier,
    icon: Int,
    label: String,
    description: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 56.dp).semantics { contentDescription = "$label. $description" },
        contentPadding = PaddingValues(horizontal = 3.dp, vertical = 5.dp),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(22.dp))
            Text(label, fontSize = 12.sp, lineHeight = 14.sp, maxLines = 2, textAlign = TextAlign.Center)
        }
    }
}

@Composable
internal fun FlushVolumesEditor(
    matrix: PrintConfigFlushMatrix,
    cells: List<String>,
    error: Boolean,
    onCellChanged: (Int, String) -> Unit,
) {
    val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = screenHeight * .72f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.PrintConfigFlushVolumesHelp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 16.sp,
            lineHeight = 22.sp,
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val rowHeaderWidth = 32.dp
            val minCellWidth = 56.dp
            val tableWidth = if (matrix.side <= 4) maxWidth
            else maxOf(maxWidth, rowHeaderWidth + minCellWidth * matrix.side)
            Column(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .width(tableWidth)
                    .testTag("flush-matrix"),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.PrintConfigFlushAxis),
                        modifier = Modifier.width(rowHeaderWidth).padding(end = 2.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 14.sp,
                    )
                    repeat(matrix.side) { column ->
                        Text(
                            text = (column + 1).toString(),
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                repeat(matrix.side) { row ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = (row + 1).toString(),
                            modifier = Modifier.width(rowHeaderWidth),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Start,
                        )
                        repeat(matrix.side) { column ->
                            val index = row * matrix.side + column
                            val label = stringResource(R.string.PrintConfigFlushCell, row + 1, column + 1)
                            FlushVolumeCell(
                                value = cells.getOrElse(index) { "0" },
                                isError = error,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 1.dp, vertical = 1.dp),
                                tag = "flush-cell-$index",
                                label = label,
                                onValueChange = { onCellChanged(index, it) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FlushVolumeCell(
    value: String,
    isError: Boolean,
    modifier: Modifier = Modifier,
    tag: String,
    label: String,
    onValueChange: (String) -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val borderColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
    Box(
        modifier = modifier
            .height(42.dp)
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(1.dp, borderColor, shape)
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().testTag(tag).semantics { contentDescription = label },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, textAlign = TextAlign.Center),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        )
    }
}

@Composable
internal fun FlushVolumesDialog(
    matrix: PrintConfigFlushMatrix,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit,
) {
    val cells = remember(matrix) { mutableStateListOf<String>().apply { addAll(matrix.cells) } }
    val error = remember(matrix) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.FlushVolumes))
                if (error.value) {
                    Text(
                        text = stringResource(R.string.PrintConfigFlushVolumesInvalid),
                        modifier = Modifier
                            .testTag("flush-error")
                            .semantics { liveRegion = LiveRegionMode.Polite },
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        text = {
            FlushVolumesEditor(
                matrix = matrix,
                cells = cells.toList(),
                error = error.value,
                onCellChanged = { index, value ->
                    if (index in cells.indices) cells[index] = value
                    error.value = false
                },
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val serialized = PrintConfigToolsLogic.serializeFlushMatrix(matrix.side, cells.toList())
                    if (serialized == null) error.value = true else onApply(serialized)
                },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
internal fun PrimeTowerPositionEditor(
    positions: PrintConfigTowerPositions,
    xValues: List<String>,
    yValues: List<String>,
    error: Boolean,
    plateNumberOffset: Int = 0,
    helpTextResource: Int = R.string.PrintConfigPrimeTowerPositionHelp,
    placementMap: PrimeTowerPlacementMap? = null,
    previewPlateIndex: Int = 0,
    towerWidth: Double = 35.0,
    towerDepth: Double = 35.0,
    onXChanged: (Int, String) -> Unit,
    onYChanged: (Int, String) -> Unit,
) {
    val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
    val suggestedPosition = remember(placementMap, towerWidth, towerDepth) {
        placementMap?.let { PrimeTowerDragLogic.findFreePosition(it, towerWidth, towerDepth) }
    }
    val helpText = if (helpTextResource == R.string.PrimeTowerPositionTapHelp) {
        stringResource(helpTextResource, plateNumberOffset + 1)
    } else {
        stringResource(helpTextResource)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = screenHeight * .72f)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Keep the live bed map and free-space action in the dialog viewport while
        // the numeric keyboard is open. Scrolling the entire form used to scroll
        // this feedback off screen precisely while coordinates were being edited.
        if (placementMap != null && previewPlateIndex in xValues.indices && previewPlateIndex in yValues.indices) {
            val previewX = PrintConfigToolsLogic.parseInput(xValues[previewPlateIndex])?.toDouble() ?: 0.0
            val previewY = PrintConfigToolsLogic.parseInput(yValues[previewPlateIndex])?.toDouble() ?: 0.0
            PrimeTowerPlacementPreview(
                map = placementMap,
                x = previewX,
                y = previewY,
                towerWidth = towerWidth,
                towerDepth = towerDepth,
            )
            val suggested = suggestedPosition
            if (suggested != null) {
                OutlinedButton(
                    onClick = {
                        onXChanged(previewPlateIndex, PrintConfigToolsLogic.formatCoordinate(suggested.x))
                        onYChanged(previewPlateIndex, PrintConfigToolsLogic.formatCoordinate(suggested.y))
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).testTag("prime-tower-auto-place"),
                ) { Text(stringResource(R.string.PrimeTowerFindFreePlace)) }
            } else {
                Text(
                    stringResource(R.string.PrimeTowerNoFreePlace),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
        Text(
            helpText,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 16.sp,
            lineHeight = 22.sp,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.PrintConfigPrimePlate), modifier = Modifier.weight(.7f).alignByBaseline(), fontSize = 16.sp)
            Text(stringResource(R.string.PrintConfigPrimeX), modifier = Modifier.weight(1f).alignByBaseline(), fontSize = 16.sp)
            Text(stringResource(R.string.PrintConfigPrimeY), modifier = Modifier.weight(1f).alignByBaseline(), fontSize = 16.sp)
        }
        repeat(positions.plateCount) { plate ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.PrintConfigPrimePlateNumber, plate + plateNumberOffset + 1),
                    modifier = Modifier.weight(.7f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 16.sp,
                )
                TowerPositionField(
                    value = xValues.getOrElse(plate) { "15" },
                    label = stringResource(R.string.PrintConfigPrimeX),
                    error = error,
                    modifier = Modifier.weight(1f).testTag("prime-tower-x-$plate"),
                    onValueChanged = { onXChanged(plate, it) },
                )
                TowerPositionField(
                    value = yValues.getOrElse(plate) { "15" },
                    label = stringResource(R.string.PrintConfigPrimeY),
                    error = error,
                    modifier = Modifier.weight(1f).testTag("prime-tower-y-$plate"),
                    imeAction = if (plate == positions.plateCount - 1) ImeAction.Done else ImeAction.Next,
                    onValueChanged = { onYChanged(plate, it) },
                )
            }
        }
        if (error) {
            Text(
                stringResource(R.string.PrintConfigPrimeTowerPositionInvalid),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        }
    }
}

@Composable
internal fun PrimeTowerPositionDialog(
    positions: PrintConfigTowerPositions,
    onDismiss: () -> Unit,
    onApply: (Pair<String, String>) -> Unit,
    plateNumberOffset: Int = 0,
    helpTextResource: Int = R.string.PrintConfigPrimeTowerPositionHelp,
    placementMap: PrimeTowerPlacementMap? = null,
    previewPlateIndex: Int = 0,
    towerWidth: Double = 35.0,
    towerDepth: Double = 35.0,
) {
    val xValues = remember(positions) { mutableStateListOf<String>().apply { addAll(positions.x) } }
    val yValues = remember(positions) { mutableStateListOf<String>().apply { addAll(positions.y) } }
    val error = remember(positions) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.PrimeTowerPosition)) },
        text = {
            PrimeTowerPositionEditor(
                positions = positions,
                xValues = xValues.toList(),
                yValues = yValues.toList(),
                error = error.value,
                plateNumberOffset = plateNumberOffset,
                helpTextResource = helpTextResource,
                placementMap = placementMap,
                previewPlateIndex = previewPlateIndex,
                towerWidth = towerWidth,
                towerDepth = towerDepth,
                onXChanged = { index, value ->
                    if (index in xValues.indices) xValues[index] = value
                    error.value = false
                },
                onYChanged = { index, value ->
                    if (index in yValues.indices) yValues[index] = value
                    error.value = false
                },
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val serialized = PrintConfigToolsLogic.serializeTowerPositions(xValues.toList(), yValues.toList())
                    if (serialized == null) error.value = true else onApply(serialized)
                },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun PrimeTowerPlacementPreview(
    map: PrimeTowerPlacementMap,
    x: Double,
    y: Double,
    towerWidth: Double,
    towerDepth: Double,
) {
    val locale = LocalConfiguration.current.locale
    val withinBed = PrimeTowerDragLogic.isPositionWithinBed(map, x, y, towerWidth, towerDepth)
    val overlaps = PrimeTowerDragLogic.overlappingObstacleCount(map, x, y, towerWidth, towerDepth)
    val free = withinBed && overlaps == 0
    val status = when {
        free -> stringResource(R.string.PrimeTowerPositionFree)
        !withinBed -> stringResource(R.string.PrimeTowerPositionOutsideBed)
        else -> stringResource(R.string.PrimeTowerPositionOverlapsModels, overlaps)
    }
    val previewDescription = stringResource(
        R.string.PrimeTowerBedPreviewDescription,
        String.format(locale, "%.1f", x),
        String.format(locale, "%.1f", y),
        status,
        map.obstacles.size,
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.PrimeTowerBedPreview), style = MaterialTheme.typography.labelLarge)
        Text(
            status,
            color = if (free) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
                .testTag("prime-tower-bed-preview")
                .semantics { contentDescription = previewDescription },
        ) {
            val inset = 8.dp.toPx()
            val bedWidth = (map.bedMaxX - map.bedMinX).coerceAtLeast(1.0)
            val bedDepth = (map.bedMaxY - map.bedMinY).coerceAtLeast(1.0)
            val scale = minOf((size.width - inset * 2) / bedWidth.toFloat(), (size.height - inset * 2) / bedDepth.toFloat())
            val bedRect = androidx.compose.ui.geometry.Rect(
                inset,
                inset,
                inset + bedWidth.toFloat() * scale,
                inset + bedDepth.toFloat() * scale,
            )
            fun bedOffset(point: ru.ytkab0bp.slicebeam.render.PrimeTowerPoint) = Offset(
                inset + ((point.x - map.bedMinX) * scale).toFloat(),
                inset + ((point.y - map.bedMinY) * scale).toFloat(),
            )
            val bedPath = Path().apply {
                if (map.bedBoundary.size >= 3) {
                    moveTo(bedOffset(map.bedBoundary.first()).x, bedOffset(map.bedBoundary.first()).y)
                    map.bedBoundary.drop(1).forEach { point ->
                        val offset = bedOffset(point)
                        lineTo(offset.x, offset.y)
                    }
                    close()
                } else {
                    addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(
                            bedRect,
                            androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()),
                        ),
                    )
                }
            }
            drawPath(bedPath, color = Color(0xFFE4E1E8))
            clipPath(bedPath) {
                val gridStep = 20.0
                var gridX = kotlin.math.ceil(map.bedMinX / gridStep) * gridStep
                while (gridX < map.bedMaxX) {
                    val gx = inset + ((gridX - map.bedMinX) * scale).toFloat()
                    drawLine(Color(0x3377727D), Offset(gx, bedRect.top), Offset(gx, bedRect.bottom), 1.dp.toPx())
                    gridX += gridStep
                }
                var gridY = kotlin.math.ceil(map.bedMinY / gridStep) * gridStep
                while (gridY < map.bedMaxY) {
                    val gy = inset + ((gridY - map.bedMinY) * scale).toFloat()
                    drawLine(Color(0x3377727D), Offset(bedRect.left, gy), Offset(bedRect.right, gy), 1.dp.toPx())
                    gridY += gridStep
                }
            }
            drawPath(bedPath, color = Color(0xFF77727D), style = Stroke(1.5.dp.toPx()))
            map.obstacles.forEach { obstacle ->
                val left = inset + ((obstacle.minX - map.bedMinX) * scale).toFloat()
                val top = inset + ((obstacle.minY - map.bedMinY) * scale).toFloat()
                val right = inset + ((obstacle.maxX - map.bedMinX) * scale).toFloat()
                val bottom = inset + ((obstacle.maxY - map.bedMinY) * scale).toFloat()
                drawRect(
                    color = Color(0x553F51B5),
                    topLeft = Offset(left, top),
                    size = Size((right - left).coerceAtLeast(1f), (bottom - top).coerceAtLeast(1f)),
                )
                drawRect(
                    color = Color(0xFF3949AB),
                    topLeft = Offset(left, top),
                    size = Size((right - left).coerceAtLeast(1f), (bottom - top).coerceAtLeast(1f)),
                    style = Stroke(1.5.dp.toPx()),
                )
            }
            val towerLeft = inset + ((x - map.bedMinX) * scale).toFloat()
            val towerTop = inset + ((y - map.bedMinY) * scale).toFloat()
            drawRect(
                color = if (free) Color(0xFF00A884) else Color(0xFFD34B45),
                topLeft = Offset(towerLeft, towerTop),
                size = Size((towerWidth * scale).toFloat(), (towerDepth * scale).toFloat()),
            )
            drawRect(
                color = Color(0xFF202124),
                topLeft = Offset(towerLeft, towerTop),
                size = Size((towerWidth * scale).toFloat(), (towerDepth * scale).toFloat()),
                style = Stroke(2.dp.toPx()),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("■", color = Color(0xFF3949AB), style = MaterialTheme.typography.labelSmall)
            Text(stringResource(R.string.PrimeTowerPreviewModels), style = MaterialTheme.typography.labelSmall)
            Text("■", color = if (free) Color(0xFF00A884) else Color(0xFFD34B45), style = MaterialTheme.typography.labelSmall)
            Text(stringResource(R.string.PrimeTowerPreviewTower), style = MaterialTheme.typography.labelSmall)
        }
        Text(
            text = stringResource(R.string.PrimeTowerPositionCoordinates, x, y) + " · " + status,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun TowerPositionField(
    value: String,
    label: String,
    error: Boolean,
    modifier: Modifier,
    imeAction: ImeAction = ImeAction.Next,
    onValueChanged: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChanged,
        modifier = modifier,
        label = { Text(label, maxLines = 1, fontSize = 16.sp) },
        singleLine = true,
        isError = error,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Next) },
            onDone = { keyboard?.hide(); focusManager.clearFocus() },
        ),
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
        shape = RoundedCornerShape(8.dp),
    )
}

internal data class PrintConfigFlushMatrix(val side: Int, val cells: List<String>)

internal data class PrintConfigTowerPositions(val plateCount: Int, val x: List<String>, val y: List<String>)
internal data class PrintConfigTowerFootprint(val width: Double, val depth: Double)

/** Profile matrix and per-plate tower logic remains independent of Android views. */
internal object PrintConfigToolsLogic {
    fun towerFootprint(primeWidth: String?, legacyWidth: String?, flushMatrix: String?): PrintConfigTowerFootprint {
        val parsedWidth = parseInput(primeWidth.orEmpty()) ?: parseInput(legacyWidth.orEmpty()) ?: 35f
        val width = parsedWidth.toDouble().coerceIn(8.0, 120.0)
        val maxFlush = parseSerialized(flushMatrix).mapNotNull { parseInput(it) }.maxOrNull() ?: 280f
        val purgeVolume = maxFlush.takeIf { it > 0f } ?: 280f
        val depth = maxOf(width, (purgeVolume.toDouble() / width * 6.0).coerceAtMost(180.0))
        return PrintConfigTowerFootprint(width, depth)
    }

    fun formatCoordinate(value: Double): String = format(value.toFloat())

    fun flushMatrix(serialized: String?, slotCount: Int, maxSlots: Int): PrintConfigFlushMatrix {
        val parts = parseSerialized(serialized)
        val existingSide = if (parts.isEmpty()) 0 else ceil(sqrt(parts.size.toDouble())).toInt()
        val side = maxOf(2, min(maxSlots, slotCount), existingSide)
        val cells = List(side * side) { index ->
            val default = if (index / side == index % side) 0f else FLUSH_DEFAULT
            val stored = parts.getOrNull(index)?.toFloatOrNull()
            format(stored?.takeIf(Float::isFinite) ?: default)
        }
        return PrintConfigFlushMatrix(side, cells)
    }

    fun serializeFlushMatrix(side: Int, cells: List<String>): String? {
        if (side < 2 || cells.size != side * side) return null
        val values = cells.map { parseInput(it)?.takeIf { value -> value >= 0f } ?: return null }
        return values.joinToString(",") { format(it) }
    }

    fun towerPositions(
        xSerialized: String?,
        ySerialized: String?,
        requiredPlateCount: Int = 1,
    ): PrintConfigTowerPositions {
        val xRaw = parseStored(xSerialized, 15f)
        val yRaw = parseStored(ySerialized, 15f)
        val count = maxOf(1, requiredPlateCount, xRaw.size, yRaw.size)
        val x = List(count) { index -> format(xRaw[min(index, xRaw.lastIndex)]) }
        val y = List(count) { index -> format(yRaw[min(index, yRaw.lastIndex)]) }
        return PrintConfigTowerPositions(count, x, y)
    }

    fun serializeTowerPositions(xValues: List<String>, yValues: List<String>): Pair<String, String>? {
        if (xValues.isEmpty() || xValues.size != yValues.size) return null
        val x = xValues.map { parseInput(it) ?: return null }
        val y = yValues.map { parseInput(it) ?: return null }
        return x.joinToString(",") { format(it) } to y.joinToString(",") { format(it) }
    }

    fun updateTowerPosition(serialized: String?, plateIndex: Int, value: String): String? {
        if (plateIndex < 0) return null
        val coordinate = parseInput(value) ?: return null
        val values = parseStored(serialized, 15f).toMutableList()
        while (values.size <= plateIndex) values.add(values.lastOrNull() ?: 15f)
        values[plateIndex] = coordinate
        return values.joinToString(",") { format(it) }
    }

    fun parseInput(value: String): Float? = value.trim().replace(',', '.')
        .toFloatOrNull()?.takeIf(Float::isFinite)

    private fun parseSerialized(value: String?): List<String> =
        if (value.isNullOrBlank()) emptyList() else value.split(SERIALIZED_NUMBERS).dropLastWhile(String::isEmpty)

    private fun parseStored(value: String?, fallback: Float): List<Float> {
        val parts = parseSerialized(value)
        if (parts.isEmpty()) return listOf(fallback)
        return parts.map { it.trim().toFloatOrNull()?.takeIf(Float::isFinite) ?: fallback }
    }

    private fun format(value: Float): String {
        if (kotlin.math.abs(value - kotlin.math.round(value)) < .0001f) {
            return kotlin.math.round(value).toInt().toString()
        }
        return String.format(Locale.US, "%.3f", value)
            .replace(Regex("0+$"), "")
            .replace(Regex("\\.$"), "")
    }

    private const val FLUSH_DEFAULT = 280f
    private val SERIALIZED_NUMBERS = Regex("[,;]")
}
