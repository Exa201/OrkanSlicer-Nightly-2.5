package ru.ytkab0bp.slicebeam.components.bed_menu

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.fragment.BedFragment
import ru.ytkab0bp.slicebeam.render.Camera
import ru.ytkab0bp.slicebeam.utils.Prefs
import ru.ytkab0bp.slicebeam.utils.Vec3d
import ru.ytkab0bp.slicebeam.utils.ViewUtils
import ru.ytkab0bp.slicebeam.view.GLView
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import java.util.concurrent.atomic.AtomicInteger

/** Camera controls rendered with Compose while retaining the existing renderer and camera actions. */
open class CameraMenu : BedMenu(), WorkspaceComposeBedMenu {
    private var fragment: BedFragment? = null
    private val cameraAnimationGeneration = AtomicInteger()

    override fun onSetBed(fragment: BedFragment?) {
        this.fragment = fragment
    }

    @Composable
    override fun WorkspaceContent(portrait: Boolean) {
        val context = LocalContext.current
        CameraMenuContent(
            portrait = portrait,
            initialOrthoEnabled = Prefs.isOrthoProjectionEnabled(),
            onControlModeSelected = { showControlModeDialog(context) },
            onOrthoChanged = ::setOrthoProjection,
        )
    }

    private fun showControlModeDialog(context: Context) {
        val bed = fragment ?: return
        val labels = listOf(
            context.getString(R.string.MenuCameraControlModeOne),
            context.getString(R.string.MenuCameraControlModeTwo),
            context.getString(R.string.MenuCameraControlModeThree),
        ).mapIndexed { index, label ->
            if (index == Prefs.getCameraControlMode()) "✓ $label" else label
        }
        bed.showWorkspaceComposeDialog { dismiss ->
            ModelChoiceDialog(
                title = context.getString(R.string.MenuCameraControlModeFull),
                cancelLabel = context.getString(android.R.string.cancel),
                choices = labels,
                onDismiss = dismiss,
                onSelect = { which ->
                    dismiss()
                    Prefs.setCameraControlMode(which)
                },
            )
        }
    }

    private fun setOrthoProjection(enabled: Boolean) {
        Prefs.setOrthoProjectionEnabled(enabled)
        val glView = fragment?.glView ?: return
        glView.queueEvent {
            glView.renderer.updateProjection()
            glView.requestRender()
        }
    }

    internal fun selectCameraPreset(preset: CameraPreset) {
        val bedFragment = fragment ?: return
        withBedBounds(showConfigurationError = true) { min, max, generation ->
            val target = CameraPresetGeometry.resolve(preset, min, max)
            animateTo(
                Vec3d(target.origin.x, target.origin.y, target.origin.z),
                Vec3d(target.position.x, target.position.y, target.position.z),
                generation,
            )
        }
    }

    /** Rotated view-cube drags map to a free camera direction while preserving the bed center. */
    fun selectCameraDirection(x: Double, y: Double, z: Double) {
        withBedBounds(showConfigurationError = false) { min, max, generation ->
            val center = Vec3d(
                (min.x + max.x) / 2.0,
                (min.y + max.y) / 2.0,
                (min.z + max.z) / 2.0,
            )
            val length = kotlin.math.sqrt(x * x + y * y + z * z).coerceAtLeast(0.001)
            val extent = maxOf(max.x - min.x, max.y - min.y, max.z - min.z, 10.0)
            val distance = extent * 1.8
            animateTo(center, Vec3d(
                center.x + x / length * distance,
                center.y + y / length * distance,
                center.z + z / length * distance,
            ), generation)
        }
    }

    private fun withBedBounds(
        showConfigurationError: Boolean,
        onReady: (CameraVector, CameraVector, generation: Int) -> Unit,
    ) {
        val bedFragment = fragment ?: return
        val glView: GLView = bedFragment.glView ?: return
        val generation = cameraAnimationGeneration.incrementAndGet()
        glView.queueEvent {
            if (generation != cameraAnimationGeneration.get()) return@queueEvent
            val bed = glView.renderer.bed
            val bounds = if (bed?.isValid() == true) {
                val min = bed.getVolumeMin()
                val max = bed.getVolumeMax()
                if (min != null && max != null) {
                    CameraVector(min.x, min.y, min.z) to CameraVector(max.x, max.y, max.z)
                } else null
            } else null
            ViewUtils.postOnMainThread {
                if (generation != cameraAnimationGeneration.get()) return@postOnMainThread
                if (bounds == null) {
                    if (showConfigurationError) {
                        Toast.makeText(
                            bedFragment.getContext(),
                            R.string.BedConfigurationError,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    return@postOnMainThread
                }
                onReady(bounds.first, bounds.second, generation)
            }
        }
    }

    private fun animateTo(toOrigin: Vec3d, toPosition: Vec3d, generation: Int) {
        val glView: GLView = fragment?.glView ?: return
        glView.queueEvent {
            if (generation != cameraAnimationGeneration.get()) return@queueEvent
            val camera: Camera = glView.renderer.camera
            val fromOrigin = Vec3d(camera.origin)
            val fromPosition = Vec3d(camera.position)
            val zoom = camera.zoom
            ViewUtils.postOnMainThread {
                if (generation != cameraAnimationGeneration.get()) return@postOnMainThread
                val middlePoint = fromPosition.center(toPosition)
                SpringAnimation(FloatValueHolder(0f))
                    .setMinimumVisibleChange(1 / 1000f)
                    .setSpring(SpringForce(1f).setStiffness(1000f).setDampingRatio(1f))
                    .addUpdateListener { _, value, _ ->
                        if (generation == cameraAnimationGeneration.get()) {
                            val positionX = ViewUtils.lerpd(
                                fromPosition.x,
                                if (kotlin.math.abs(toPosition.x - toOrigin.x) <= 5) middlePoint.x
                                else fromPosition.x + (toPosition.x - fromPosition.x) / 2,
                                toPosition.x,
                                value,
                            )
                            val positionY = ViewUtils.lerpd(
                                fromPosition.y,
                                if (kotlin.math.abs(toPosition.y - toOrigin.y) <= 5) middlePoint.y
                                else fromPosition.y + (toPosition.y - fromPosition.y) / 2,
                                toPosition.y,
                                value,
                            )
                            val positionZ = ViewUtils.lerpd(
                                fromPosition.z,
                                if (kotlin.math.abs(toPosition.z - toOrigin.z) <= 5) middlePoint.z
                                else fromPosition.z + (toPosition.z - fromPosition.z) / 2,
                                toPosition.z,
                                value,
                            )
                            val originX = ViewUtils.lerpd(fromOrigin.x, toOrigin.x, value)
                            val originY = ViewUtils.lerpd(fromOrigin.y, toOrigin.y, value)
                            val originZ = ViewUtils.lerpd(fromOrigin.z, toOrigin.z, value)
                            val zoomValue = ViewUtils.lerp(zoom, 1f, value)
                            glView.queueEvent {
                                if (generation != cameraAnimationGeneration.get()) return@queueEvent
                                val activeCamera = glView.renderer.camera
                                activeCamera.zoom = zoomValue
                                activeCamera.position.set(positionX, positionY, positionZ)
                                activeCamera.origin.set(originX, originY, originZ)
                                activeCamera.invalidate()
                                glView.renderer.updateProjection()
                                glView.requestRender()
                            }
                        }
                    }
                    .start()
            }
        }
    }
}

@Composable
internal fun CameraMenuContent(
    portrait: Boolean,
    initialOrthoEnabled: Boolean,
    onControlModeSelected: () -> Unit,
    onOrthoChanged: (Boolean) -> Unit,
) {
    var orthoEnabled by remember { mutableStateOf(initialOrthoEnabled) }
    val actions = listOf(
        BedComposeMenuAction(
            R.string.MenuCameraOrtho,
            R.drawable.image_format_32,
            {
                orthoEnabled = !orthoEnabled
                onOrthoChanged(orthoEnabled)
            },
            selected = orthoEnabled,
            toggleable = true,
        ),
        BedComposeMenuAction(
            R.string.MenuCameraControlMode,
            R.drawable.rectangle_hand_point_up_28,
            { onControlModeSelected() },
            sectionBreakBefore = true,
        ),
    )
    CustomizableBedMenuActionsContent("view", actions, portrait)
}
