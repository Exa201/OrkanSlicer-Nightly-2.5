package ru.ytkab0bp.slicebeam.components.bed_menu

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.text.TextUtils
import androidx.core.content.FileProvider
import ru.ytkab0bp.slicebeam.BuildConfig
import ru.ytkab0bp.slicebeam.MainActivity
import ru.ytkab0bp.slicebeam.launchAppActivityResult
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.SliceBeam
import ru.ytkab0bp.slicebeam.components.SmartSliceDialog
import ru.ytkab0bp.slicebeam.config.ConfigObject
import ru.ytkab0bp.slicebeam.fragment.BedFragment
import ru.ytkab0bp.slicebeam.utils.DebugUtils
import ru.ytkab0bp.slicebeam.utils.Prefs

/** Builds the visible Slice menu actions; printer workflows stay in [SliceMenu]. */
object SliceMenuItems {
    private val supportedSendTypes = setOf("octoprint", "moonraker", "klipper")

    fun interface SendAction {
        fun send(type: String, host: String, apiKey: String?, printer: ConfigObject, print: Boolean)
    }

    internal fun createBeforeSlice(fragment: BedFragment, hasModel: Boolean): List<BedComposeMenuAction> = listOf(
        BedComposeMenuAction(R.string.QuickSliceTitle, R.drawable.vk_magic_wand_outline_24, { view ->
            QuickSliceDialog.show(fragment, view)
        }, enabled = hasModel),
        BedComposeMenuAction(R.string.SmartSliceTitle, R.drawable.sparkle_28, {
            SmartSliceDialog.show(fragment)
        }, enabled = hasModel),
        BedComposeMenuAction(R.string.MenuSliceCurrentSettings, R.drawable.vk_sliders_outline_20, {
            fragment.startSlicing()
        }, enabled = hasModel),
    )

    internal fun createAfterSlice(
        fragment: BedFragment,
        parentMenu: SliceMenu,
        sendAction: SendAction,
    ): List<BedComposeMenuAction> {
        val items = mutableListOf(
            BedComposeMenuAction(R.string.MenuSliceInfo, R.drawable.clock_circle_dashed_outline_24, {
                showSliceInfoWorkspace(fragment, parentMenu)
            }),
            BedComposeMenuAction(R.string.MenuSliceLayers, R.drawable.square_stack_up_outline_28, {
                showSliceLayersWorkspace(fragment)
            }),
            BedComposeMenuAction(R.string.MenuSliceExportToFile, R.drawable.arrow_down_to_square_outline_28, {
                val activity = fragment.getContext() as? Activity ?: return@BedComposeMenuAction
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    type = "application/x-gcode"
                    putExtra(
                        Intent.EXTRA_TITLE,
                        fragment.glView!!.renderer.gcodeResult!!.getRecommendedName(),
                    )
                }
                activity.launchAppActivityResult(intent, MainActivity.REQUEST_CODE_EXPORT_GCODE)
            }),
            BedComposeMenuAction(R.string.MenuSliceShare, R.drawable.vk_share_external_outline_24, {
                val activity = fragment.getContext() as? Activity ?: return@BedComposeMenuAction
                val file = BedFragment.tempGCodePath
                val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "application/x-gcode"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        // Preserve Samsung's display-name handling for the shared temporary file.
                        val uri = FileProvider.getUriForFile(
                            activity,
                            BuildConfig.APPLICATION_ID + ".slicermore.files",
                            file,
                            BedFragment.tempFileName ?: file.name,
                        )
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(uri))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } else {
                        putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file))
                    }
                }
                activity.startActivity(Intent.createChooser(intent, null))
            }),
        )
        items += BedComposeMenuAction(
            R.string.MenuSlicePerformance,
            R.drawable.sparkle_28,
            {
                val enabled = !Prefs.isPerformanceModeEnabled()
                Prefs.setPerformanceModeEnabled(enabled)
                val viewport = fragment.glView ?: return@BedComposeMenuAction
                viewport.queueEvent {
                    viewport.renderer.viewer?.applyPerformanceMode(enabled || Prefs.isLowPerformancePreviewDevice())
                    // Rebuild the active model as well; otherwise the quality switch only
                    // affects G-code until a model is loaded again.
                    viewport.renderer.resetGlModels()
                    viewport.requestRender()
                }
                parentMenu.refreshInfoItems()
            },
            selected = Prefs.isPerformanceModeEnabled(),
            toggleable = true,
        )

        val printer = SliceBeam.requireConfig().findPrinter(SliceBeam.requireConfig().presets!!["printer"])
        DebugUtils.assertTrue(printer != null)
        if (printer != null) {
            var type = printer.get("host_type")
            if (type == null) type = "octoprint"
            val host = printer.get("print_host")
            val apiKey = printer.get("printhost_apikey")
            if (type in supportedSendTypes && !TextUtils.isEmpty(host)) {
                val selectedType = type
                items += BedComposeMenuAction(R.string.MenuSliceSendToPrinter, R.drawable.send_outline_28, {
                    sendAction.send(selectedType, host!!, apiKey, printer, false)
                }, sectionBreakBefore = true)
                items += BedComposeMenuAction(R.string.MenuSliceSendToPrinterAndPrint, R.drawable.send_28, {
                    sendAction.send(selectedType, host!!, apiKey, printer, true)
                })
            }
        }
        return items
    }
}
