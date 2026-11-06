package ru.ytkab0bp.slicebeam.components.bed_menu

import android.graphics.Paint
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.utils.Prefs
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Shared accessible horizontal ruler. A 10 dp drag advances one integer step. */
@Composable
internal fun SteppedRuler(
    key: String,
    title: String,
    stateDescription: String,
    value: Int,
    color: Color,
    enabled: Boolean = true,
    min: Int = Int.MIN_VALUE,
    max: Int = Int.MAX_VALUE,
    onValueChanged: (Int) -> Unit,
    onCommit: () -> Unit,
) {
    var dragValue by remember(key) { mutableIntStateOf(value) }
    var progress by remember(key) { mutableStateOf(0f) }
    var dragging by remember(key) { mutableStateOf(false) }
    val latestValue by rememberUpdatedState(value)
    val latestOnValueChanged by rememberUpdatedState(onValueChanged)
    val latestOnCommit by rememberUpdatedState(onCommit)
    val latestEnabled by rememberUpdatedState(enabled)
    val density = LocalDensity.current.density
    val view = LocalView.current
    val inactiveColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val accessibilityValue = stringResource(R.string.ScaleCurrentPercent, value)

    LaunchedEffect(value) {
        if (!dragging) {
            dragValue = value
            progress = 0f
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .testTag(key)
            .pointerInput(key, density, enabled, min, max) {
                if (!enabled || min > max) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragging = true
                        dragValue = latestValue.coerceIn(min, max)
                        progress = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        val result = TransformScalePolicy.drag(
                            currentValue = dragValue,
                            progress = progress,
                            dragAmountPx = dragAmount,
                            density = density,
                            min = min,
                            max = max,
                        )
                        progress = result.progress
                        if (result.value != dragValue) {
                            dragValue = result.value
                            latestOnValueChanged(result.value)
                            if (Prefs.isVibrationEnabled()) view.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE)
                        }
                    },
                    onDragEnd = {
                        val settled = TransformScalePolicy.settle(dragValue, progress, min, max)
                        progress = 0f
                        dragging = false
                        if (settled != dragValue) {
                            dragValue = settled
                            latestOnValueChanged(settled)
                        }
                        latestOnCommit()
                    },
                    onDragCancel = {
                        progress = 0f
                        dragging = false
                        latestOnCommit()
                    },
                )
            }
            .semantics {
                contentDescription = title
                this.stateDescription = stateDescription.ifBlank { accessibilityValue }
                if (min < max) progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), min.toFloat()..max.toFloat())
                setProgress { requested ->
                    if (!latestEnabled || min > max || !requested.isFinite()) return@setProgress false
                    val next = requested.roundToInt().coerceIn(min, max)
                    latestOnValueChanged(next)
                    latestOnCommit()
                    true
                }
            },
    ) {
        drawRuler(value.coerceIn(min, max), progress, color, inactiveColor, min, max)
    }
}

private fun DrawScope.drawRuler(
    value: Int,
    progress: Float,
    active: Color,
    inactive: Color,
    min: Int,
    max: Int,
) {
    val step = TransformScalePolicy.PIXELS_PER_PERCENT_DP * density
    val centerX = size.width / 2f + progress * step
    val top = 6.dp.toPx()
    val bottom = size.height - 16.dp.toPx()
    val count = ceil(size.width / step).toInt() + 2
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12.dp.toPx()
        textAlign = Paint.Align.CENTER
    }
    drawIntoCanvas { canvas ->
        val nativeCanvas = canvas.nativeCanvas
        for (offset in -count..count) {
            val x = centerX + offset * step
            if (x < -step || x > size.width + step) continue
            val position = value.toLong() + offset
            if (position !in min.toLong()..max.toLong()) continue
            val major = offset == 0 || position % 5L == 0L
            drawLine(
                color = if (offset == 0) active else inactive,
                start = androidx.compose.ui.geometry.Offset(x, if (major) top else top + 12.dp.toPx()),
                end = androidx.compose.ui.geometry.Offset(x, bottom),
                strokeWidth = if (offset == 0) 2.5.dp.toPx() else 1.dp.toPx(),
                cap = StrokeCap.Round,
            )
            if (major) {
                paint.alpha = if (offset == 0) 255 else 190
                paint.color = if (offset == 0) active.toArgb() else inactive.toArgb()
                nativeCanvas.drawText(position.toString(), x, size.height - 2.dp.toPx(), paint)
            }
        }
    }
}
