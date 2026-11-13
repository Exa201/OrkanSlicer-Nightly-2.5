package ru.ytkab0bp.slicebeam.ui.workspace

import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.ytkab0bp.slicebeam.R
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal enum class WorkspaceViewFace {
    FRONT, BACK, RIGHT, LEFT, TOP, BOTTOM,
}

internal data class ViewCubePoint(val x: Float, val y: Float)
internal data class ViewCubePolygon(val face: WorkspaceViewFace, val points: List<ViewCubePoint>, val depth: Float)

/** Small orthographic cube geometry; the face normal names follow the slicer's X/Y/Z axes. */
internal object WorkspaceViewCubeGeometry {
    private data class V3(val x: Float, val y: Float, val z: Float)
    private data class FaceDefinition(val face: WorkspaceViewFace, val normal: V3, val vertices: List<V3>)

    private val definitions = listOf(
        FaceDefinition(WorkspaceViewFace.FRONT, V3(0f, -1f, 0f), listOf(
            V3(-1f, -1f, -1f), V3(1f, -1f, -1f), V3(1f, -1f, 1f), V3(-1f, -1f, 1f),
        )),
        FaceDefinition(WorkspaceViewFace.BACK, V3(0f, 1f, 0f), listOf(
            V3(1f, 1f, -1f), V3(-1f, 1f, -1f), V3(-1f, 1f, 1f), V3(1f, 1f, 1f),
        )),
        FaceDefinition(WorkspaceViewFace.RIGHT, V3(1f, 0f, 0f), listOf(
            V3(1f, -1f, -1f), V3(1f, 1f, -1f), V3(1f, 1f, 1f), V3(1f, -1f, 1f),
        )),
        FaceDefinition(WorkspaceViewFace.LEFT, V3(-1f, 0f, 0f), listOf(
            V3(-1f, 1f, -1f), V3(-1f, -1f, -1f), V3(-1f, -1f, 1f), V3(-1f, 1f, 1f),
        )),
        FaceDefinition(WorkspaceViewFace.TOP, V3(0f, 0f, 1f), listOf(
            V3(-1f, -1f, 1f), V3(1f, -1f, 1f), V3(1f, 1f, 1f), V3(-1f, 1f, 1f),
        )),
        FaceDefinition(WorkspaceViewFace.BOTTOM, V3(0f, 0f, -1f), listOf(
            V3(-1f, 1f, -1f), V3(1f, 1f, -1f), V3(1f, -1f, -1f), V3(-1f, -1f, -1f),
        )),
    )

    fun project(width: Float, height: Float, yaw: Float, pitch: Float): List<ViewCubePolygon> {
        val centerX = width / 2f
        val centerY = height / 2f
        val scale = minOf(width, height) * 0.33f
        val view = V3(0.57735f, -0.57735f, 0.57735f)
        fun rotate(v: V3): V3 {
            val cz = cos(yaw); val sz = sin(yaw)
            val x = v.x * cz - v.y * sz
            val y = v.x * sz + v.y * cz
            val cx = cos(pitch); val sx = sin(pitch)
            return V3(x, y * cx - v.z * sx, y * sx + v.z * cx)
        }
        fun screen(v: V3): ViewCubePoint {
            val r = rotate(v)
            val sx = (r.x + r.y) * 0.7071f
            val sy = (-r.x * 0.4082f + r.y * 0.4082f + r.z * 0.8165f)
            return ViewCubePoint(centerX + sx * scale, centerY - sy * scale)
        }
        return definitions.mapNotNull { definition ->
            val normal = rotate(definition.normal)
            val depth = normal.x * view.x + normal.y * view.y + normal.z * view.z
            if (depth <= 0.01f) return@mapNotNull null
            ViewCubePolygon(definition.face, definition.vertices.map(::screen), depth)
        }.sortedBy { it.depth }
    }

    fun hitTest(polygons: List<ViewCubePolygon>, x: Float, y: Float): WorkspaceViewFace? =
        polygons.asReversed().firstOrNull { pointInPolygon(it.points, x, y) }?.face

    fun cameraDirection(yaw: Float, pitch: Float): Triple<Double, Double, Double> {
        // Inverse cube rotation applied to the fixed isometric view vector.
        val dx = 0.57735f
        val dy = -0.57735f
        val dz = 0.57735f
        val y1 = dy * cos(pitch) + dz * sin(pitch)
        val z1 = -dy * sin(pitch) + dz * cos(pitch)
        val x2 = dx * cos(yaw) + y1 * sin(yaw)
        val y2 = -dx * sin(yaw) + y1 * cos(yaw)
        val length = sqrt(x2 * x2 + y2 * y2 + z1 * z1).coerceAtLeast(0.001f)
        return Triple((x2 / length).toDouble(), (y2 / length).toDouble(), (z1 / length).toDouble())
    }

    fun orientationFor(face: WorkspaceViewFace): Pair<Float, Float> = when (face) {
        WorkspaceViewFace.FRONT -> Math.toRadians(35.0).toFloat() to Math.toRadians(-45.0).toFloat()
        WorkspaceViewFace.BACK -> Math.toRadians(-35.0).toFloat() to Math.toRadians(45.0).toFloat()
        WorkspaceViewFace.RIGHT -> Math.toRadians(55.0).toFloat() to Math.toRadians(45.0).toFloat()
        WorkspaceViewFace.LEFT -> Math.toRadians(125.0).toFloat() to Math.toRadians(45.0).toFloat()
        WorkspaceViewFace.TOP -> Math.toRadians(45.0).toFloat() to Math.toRadians(55.0).toFloat()
        WorkspaceViewFace.BOTTOM -> Math.toRadians(-135.0).toFloat() to Math.toRadians(-55.0).toFloat()
    }

    private fun pointInPolygon(points: List<ViewCubePoint>, x: Float, y: Float): Boolean {
        var inside = false
        var previous = points.lastIndex
        for (index in points.indices) {
            val a = points[index]
            val b = points[previous]
            if ((a.y > y) != (b.y > y) && x < (b.x - a.x) * (y - a.y) / (b.y - a.y) + a.x) inside = !inside
            previous = index
        }
        return inside
    }
}

@Composable
internal fun WorkspaceViewCube(
    onFaceSelected: (WorkspaceViewFace) -> Unit,
    onCameraRotated: (Double, Double, Double) -> Unit,
) {
    var yaw by remember { mutableFloatStateOf(Math.toRadians(-35.0).toFloat()) }
    var pitch by remember { mutableFloatStateOf(Math.toRadians(-25.0).toFloat()) }
    val faceAction by rememberUpdatedState(onFaceSelected)
    val rotationAction by rememberUpdatedState(onCameraRotated)
    val density = LocalDensity.current
    val front = stringResource(R.string.ViewCubeFront)
    val back = stringResource(R.string.ViewCubeBack)
    val right = stringResource(R.string.ViewCubeRight)
    val left = stringResource(R.string.ViewCubeLeft)
    val top = stringResource(R.string.ViewCubeTop)
    val bottom = stringResource(R.string.ViewCubeBottom)
    val frontShort = stringResource(R.string.ViewCubeFrontShort)
    val backShort = stringResource(R.string.ViewCubeBackShort)
    val rightShort = stringResource(R.string.ViewCubeRightShort)
    val leftShort = stringResource(R.string.ViewCubeLeftShort)
    val topShort = stringResource(R.string.ViewCubeTopShort)
    val bottomShort = stringResource(R.string.ViewCubeBottomShort)
    val rotateHint = stringResource(R.string.ViewCubeRotateHint)
    val faceColors = listOf(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer,
    )
    val labelColor = MaterialTheme.colorScheme.onSurface
    val edgeColor = MaterialTheme.colorScheme.outline
    val descriptions = mapOf(
        WorkspaceViewFace.FRONT to front,
        WorkspaceViewFace.BACK to back,
        WorkspaceViewFace.RIGHT to right,
        WorkspaceViewFace.LEFT to left,
        WorkspaceViewFace.TOP to top,
        WorkspaceViewFace.BOTTOM to bottom,
    )
    val accessibleFaceActions = WorkspaceViewFace.values().map { face ->
        val label = stringResource(R.string.ViewCubeShowFace, descriptions.getValue(face))
        CustomAccessibilityAction(label) {
            faceAction(face)
            val orientation = WorkspaceViewCubeGeometry.orientationFor(face)
            yaw = orientation.first
            pitch = orientation.second
            true
        }
    }
    val cubeDescription = stringResource(R.string.ViewCubeDescription, rotateHint)
    val shortLabels = mapOf(
        WorkspaceViewFace.FRONT to frontShort,
        WorkspaceViewFace.BACK to backShort,
        WorkspaceViewFace.RIGHT to rightShort,
        WorkspaceViewFace.LEFT to leftShort,
        WorkspaceViewFace.TOP to topShort,
        WorkspaceViewFace.BOTTOM to bottomShort,
    )
    Canvas(
        modifier = Modifier
            // Match the 48 dp action host so the drawing cannot overlap adjacent
            // printer controls while retaining the minimum accessible target.
            .size(48.dp)
            .testTag("workspace-view-cube")
            .semantics {
                contentDescription = cubeDescription
                customActions = accessibleFaceActions
            }
            .pointerInput(Unit) {
                detectTapGestures { point ->
                    val hit = WorkspaceViewCubeGeometry.hitTest(
                        WorkspaceViewCubeGeometry.project(size.width.toFloat(), size.height.toFloat(), yaw, pitch),
                        point.x,
                        point.y,
                    ) ?: return@detectTapGestures
                    faceAction(hit)
                    val orientation = WorkspaceViewCubeGeometry.orientationFor(hit)
                    yaw = orientation.first
                    pitch = orientation.second
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        val direction = WorkspaceViewCubeGeometry.cameraDirection(yaw, pitch)
                        rotationAction(direction.first, direction.second, direction.third)
                    },
                ) { change, drag ->
                    change.consume()
                    yaw += drag.x * 0.012f
                    pitch = (pitch + drag.y * 0.012f).coerceIn(-1.45f, 1.45f)
                }
            },
    ) {
        val polygons = WorkspaceViewCubeGeometry.project(size.width, size.height, yaw, pitch)
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = labelColor.toArgb()
                textAlign = Paint.Align.CENTER
                // The face letters are decorative, compact orientation marks. Keep their
                // physical size bounded so system font scaling cannot make neighbouring
                // faces overlap; the full face names remain available to accessibility.
                textSize = with(density) { 9.dp.toPx() }
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            }
            val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = edgeColor.toArgb()
                style = Paint.Style.STROKE
                strokeWidth = with(density) { 1.dp.toPx() }
            }
            polygons.forEachIndexed { index, polygon ->
                val path = Path().apply {
                    polygon.points.forEachIndexed { pointIndex, point ->
                        if (pointIndex == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                    }
                    close()
                }
                val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = faceColors[index % faceColors.size].toArgb()
                    style = Paint.Style.FILL
                }
                native.drawPath(path, fill)
                native.drawPath(path, edgePaint)
                val centerX = polygon.points.sumOf { it.x.toDouble() }.toFloat() / polygon.points.size
                val centerY = polygon.points.sumOf { it.y.toDouble() }.toFloat() / polygon.points.size
                native.drawText(shortLabels.getValue(polygon.face), centerX, centerY + labelPaint.textSize * 0.34f, labelPaint)
            }
        }
    }
}
