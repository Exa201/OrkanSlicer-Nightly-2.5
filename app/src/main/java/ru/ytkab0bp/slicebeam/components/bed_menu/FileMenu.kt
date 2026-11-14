package ru.ytkab0bp.slicebeam.components.bed_menu

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import androidx.core.content.FileProvider
import ru.ytkab0bp.eventbus.EventHandler
import ru.ytkab0bp.slicebeam.BeamServerData
import ru.ytkab0bp.slicebeam.BuildConfig
import ru.ytkab0bp.slicebeam.MainActivity
import ru.ytkab0bp.slicebeam.launchAppActivityResult
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.SetupActivity
import ru.ytkab0bp.slicebeam.SliceBeam
import ru.ytkab0bp.slicebeam.cloud.CloudController
import ru.ytkab0bp.slicebeam.components.GeneratorWorkspaceDialog
import ru.ytkab0bp.slicebeam.events.CloudFeaturesUpdatedEvent
import ru.ytkab0bp.slicebeam.events.CloudModelsRemainingCountUpdatedEvent
import ru.ytkab0bp.slicebeam.events.FlattenModeResetEvent
import ru.ytkab0bp.slicebeam.events.NeedDismissSnackbarEvent
import ru.ytkab0bp.slicebeam.events.NeedSnackbarEvent
import ru.ytkab0bp.slicebeam.events.ObjectsListChangedEvent
import ru.ytkab0bp.slicebeam.events.SelectedObjectChangedEvent
import ru.ytkab0bp.slicebeam.fragment.BedFragment
import ru.ytkab0bp.slicebeam.slic3r.Model
import ru.ytkab0bp.slicebeam.utils.Prefs
import ru.ytkab0bp.slicebeam.utils.ViewUtils
import ru.ytkab0bp.slicebeam.view.GLView
import ru.ytkab0bp.slicebeam.view.SnackbarsLayout
import java.io.File
import java.io.IOException

internal fun aiGenerationQuotaProgress(total: Int, generated: Int): Float =
    if (total <= 0) 0f else ((total - generated).toFloat() / total).coerceIn(0f, 1f)

/** File, object and profile actions for the model workspace. */
class FileMenu : ListBedMenu() {
    private var wasPortrait = false
    private val composeActions = mutableStateOf<List<BedComposeMenuAction>>(emptyList())
    private var observedBed: BedFragment? = null
    private var selectionObserver: ModelMenuSelectionObserver? = null

    @Composable
    override fun WorkspaceContent(portrait: Boolean) {
        wasPortrait = portrait
        LaunchedEffect(this, portrait) { refreshComposeActions() }
        CustomizableBedMenuActionsContent("project", composeActions.value, portrait)
    }

    private fun createComposeActions(portrait: Boolean): List<BedComposeMenuAction> =
        onCreateItems(portrait).map { menuItem ->
            BedComposeMenuAction(
                titleRes = menuItem.titleRes,
                iconRes = menuItem.iconRes,
                onClick = { anchor ->
                    if (menuItem.isCheckable) {
                        menuItem.isChecked = !menuItem.isChecked
                        menuItem.checkedChangeListener?.onCheckedChanged(menuItem.isChecked)
                        refreshComposeActions()
                    } else {
                        menuItem.clickListener?.invoke(anchor)
                    }
                },
                selected = menuItem.isCheckable && menuItem.isChecked,
                toggleable = menuItem.isCheckable,
                sectionBreakBefore = menuItem.sectionBreakBefore,
                enabled = menuItem.isEnabled,
            )
        }

    private fun refreshComposeActions() {
        composeActions.value = createComposeActions(wasPortrait)
    }

    private fun modelSelectionState(): ModelMenuSelectionState {
        val bed = fragment ?: return ModelMenuSelectionState()
        if (observedBed !== bed) {
            observedBed = bed
            selectionObserver = ModelMenuSelectionObserver(bed, ::refreshComposeActions)
        }
        val observer = selectionObserver ?: return ModelMenuSelectionState()
        observer.readIfNeeded()
        return observer.state.value
    }

    private fun showPositionMenu(view: View) {
        val bed = fragment ?: return
        bed.showWorkspaceComposeTool(OrientationPositionMenu(bed))
    }

    private fun showRotationMenu(view: View) {
        val bed = fragment ?: return
        bed.showWorkspaceComposeTool(OrientationRotationMenu(bed))
    }

    private fun showScaleMenu(view: View) {
        val bed = fragment ?: return
        bed.showWorkspaceComposeTool(TransformScaleMenu(bed))
    }

    private fun openModel(view: View) {
        val bed = fragment ?: return
        val glView = bed.glView ?: return
        openModelWhenBedReady(bed, glView, 0)
    }

    /** The workspace can become visible a moment before GLRenderer finishes creating its bed. */
    private fun openModelWhenBedReady(bed: BedFragment, glView: GLView, attempt: Int) {
        glView.queueEvent {
            val rendererBed = glView.renderer.bed
            val bedIsValid = rendererBed?.isValid() == true
            ViewUtils.postOnMainThread {
                if (fragment !== bed || bed.glView !== glView) return@postOnMainThread
                if (!bedIsValid && attempt < OPEN_MODEL_BED_READY_RETRIES) {
                    ViewUtils.postOnMainThread(
                        { openModelWhenBedReady(bed, glView, attempt + 1) },
                        OPEN_MODEL_BED_READY_RETRY_DELAY_MS,
                    )
                    return@postOnMainThread
                }
                val activity = bed.getContext() as? Activity ?: return@postOnMainThread
                if (!bedIsValid) {
                    Toast.makeText(activity, R.string.BedConfigurationError, Toast.LENGTH_SHORT).show()
                    return@postOnMainThread
                }
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
                activity.launchAppActivityResult(intent, MainActivity.REQUEST_CODE_OPEN_FILE)
            }
        }
    }

    private companion object {
        const val OPEN_MODEL_BED_READY_RETRIES = 50
        const val OPEN_MODEL_BED_READY_RETRY_DELAY_MS = 100L
    }

    private fun objectActions(): FileMenuObjectActions? =
        fragment?.let(::FileMenuObjectActions)

    private fun showCopyMoveDialog(view: View) = objectActions()?.showCopyMoveDialog(view)

    private fun showObjectsDialog(view: View) = objectActions()?.showObjectsDialog(view)

    private fun showSelectedObjectActions(view: View) = objectActions()?.showSelectedObjectActions(view)

    /** Assigns a whole object to an ACE/slicer tool; fine-grained color painting stays separate. */
    private fun showObjectFilamentSlotDialog(view: View) = objectActions()?.showObjectFilamentSlotDialog(view)

    private fun showDuplicateDialog(view: View) = objectActions()?.showDuplicateDialog(view)

    private fun showMoveToPlateDialog(view: View) = objectActions()?.showMoveToPlateDialog(view)

    /** Additional workspace actions shown directly in the Options destination. */
    internal fun createOptionsItems(): List<BedMenuItem> {
        val bed = fragment ?: return emptyList()
        val items = mutableListOf<BedMenuItem>()

        if (BeamServerData.isBoostyAvailable() && CloudController.needShowAIGenerator()) {
            // A static icon avoids the old sparkle animation invalidating the menu continuously.
            items += BedMenuItem(R.string.MenuFileAIGenerator, R.drawable.picture_stack_outline_28)
                .onClick { view ->
                    if (Prefs.getCloudAPIToken() == null ||
                        (CloudController.getUserInfo() != null && CloudController.getMaxGeneratedModels() == 0)
                    ) {
                        view.context.startActivity(
                            Intent(view.context, SetupActivity::class.java)
                                .putExtra(SetupActivity.EXTRA_CLOUD_PROFILE, true),
                        )
                        return@onClick
                    }
                    if (CloudController.getUserInfo() == null) {
                        SliceBeam.EVENT_BUS.fireEvent(
                            NeedSnackbarEvent(
                                SnackbarsLayout.Type.LOADING,
                                R.string.MenuFileAIGeneratorPleaseWaitSetup,
                            ).tag(CloudController.USER_INFO_AI_GEN_TAG),
                        )
                        ViewUtils.postOnMainThread({
                            if (CloudController.getUserInfo() == null) {
                                SliceBeam.EVENT_BUS.fireEvent(
                                    NeedDismissSnackbarEvent(CloudController.USER_INFO_AI_GEN_TAG),
                                )
                                SliceBeam.EVENT_BUS.fireEvent(
                                    NeedSnackbarEvent(
                                        SnackbarsLayout.Type.ERROR,
                                        R.string.MenuFileAIGeneratorErrorNotLoadedUserAccount,
                                    ),
                                )
                            } else {
                                showAIGeneratorWorkspace(bed)
                            }
                        }, 2500)
                        return@onClick
                    }
                    showAIGeneratorWorkspace(bed)
                }
        }

        items += BedMenuItem(R.string.MenuFileCalibrations, R.drawable.menu_calibrate_la_28)
            .onClick { showCalibrationWorkspace(bed) }
        items += BedMenuItem(R.string.MenuFileImportProfiles, R.drawable.folder_simple_arrow_up_outline_28)
            .onClick {
                val activity = bed.getContext() as? Activity ?: return@onClick
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(
                        Intent.EXTRA_MIME_TYPES,
                        arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream", "text/plain"),
                    )
                }
                activity.launchAppActivityResult(intent, MainActivity.REQUEST_CODE_IMPORT_PROFILES)
            }
        items += BedMenuItem(R.string.MenuFileExportProfiles, R.drawable.vk_share_external_outline_24)
            .onClick(::showProfileExportDialog)
        return items
    }

    override fun onCreateItems(portrait: Boolean): List<BedMenuItem> {
        val bed = fragment ?: return emptyList()
        wasPortrait = portrait
        return listOf(
            BedMenuItem(R.string.WorkspaceAddModelAction, R.drawable.folder_simple_plus_outline_28)
                .onClick(::openModel),
            BedMenuItem(R.string.MenuFileExport3mf, R.drawable.arrow_down_to_square_outline_28)
                .setEnabled(modelSelectionState().objectCount > 0)
                .onClick { view ->
                    val launchExport = {
                        val activity = bed.getContext() as? Activity
                        if (activity != null) {
                            val isMultiPlate = bed.plateCount > 1
                            val fileName = if (isMultiPlate) {
                                "OrkanSlicer_plate_${bed.currentPlateIndex + 1}.3mf"
                            } else {
                                "OrkanSlicer_project.3mf"
                            }
                            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                type = "application/3mf"
                                putExtra(Intent.EXTRA_TITLE, fileName)
                            }
                            activity.launchAppActivityResult(intent, MainActivity.REQUEST_CODE_EXPORT_3MF)
                        }
                    }
                    if (bed.plateCount > 1) {
                        bed.showWorkspaceMessageDialog(
                            title = view.context.getString(R.string.Project3mfExportActivePlateTitle),
                            message = view.context.getString(
                                R.string.Project3mfExportActivePlateMessage,
                                bed.currentPlateIndex + 1,
                                bed.plateCount,
                            ),
                            confirmLabel = view.context.getString(R.string.Project3mfExportActivePlateConfirm),
                            dismissLabel = view.context.getString(android.R.string.cancel),
                            onConfirm = launchExport,
                        )
                    } else {
                        launchExport()
                    }
                },
        )
    }

    private fun showProfileExportDialog(view: View) {
        val bed = fragment ?: return
        val context = view.context
        val config = SliceBeam.requireConfig()
        val printProfiles = config.printConfigs.toList()
        val filamentProfiles = config.filamentConfigs.toList()
        val printerProfiles = config.printerConfigs.toList()
        val categories = listOf(
            printProfiles.map { it.title ?: "" },
            filamentProfiles.map { it.title ?: "" },
            printerProfiles.map { it.title ?: "" },
        )
        bed.showWorkspaceComposeDialog { dismiss ->
            ProfileExportSelectionDialog(
                titles = listOf(
                    context.getString(R.string.MenuFileExportProfilesPrints),
                    context.getString(R.string.MenuFileExportProfilesFilaments),
                    context.getString(R.string.MenuFileExportProfilesPrinters),
                ),
                categories = categories,
                cancelLabel = context.getString(android.R.string.cancel),
                backLabel = context.getString(R.string.MenuOrientationPositionBack),
                nextLabel = context.getString(R.string.IntroNext),
                exportLabel = context.getString(R.string.MenuFileExportProfiles),
                onDismiss = dismiss,
                onComplete = { selectedIndices ->
                    dismiss()
                    val selectedPrints = selectedIndices[0].mapNotNull(printProfiles::getOrNull)
                    val selectedFilaments = selectedIndices[1].mapNotNull(filamentProfiles::getOrNull)
                    val selectedPrinters = selectedIndices[2].mapNotNull(printerProfiles::getOrNull)
                    if (selectedPrints.isEmpty() && selectedFilaments.isEmpty() && selectedPrinters.isEmpty()) {
                        bed.showWorkspaceMessageDialog(
                            context.getString(R.string.MenuFileExportProfiles),
                            context.getString(R.string.MenuFileExportProfilesNoProfiles),
                            context.getString(android.R.string.ok),
                        )
                        return@ProfileExportSelectionDialog
                    }

                    MainActivity.EXPORTING_PRINTS = selectedPrints
                    MainActivity.EXPORTING_FILAMENTS = selectedFilaments
                    MainActivity.EXPORTING_PRINTERS = selectedPrinters
                    val activity = bed.getContext() as? Activity ?: return@ProfileExportSelectionDialog
                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        type = "application/ini"
                        putExtra(Intent.EXTRA_TITLE, "OrcaSlicerMobile_config_bundle.ini")
                    }
                    activity.launchAppActivityResult(intent, MainActivity.REQUEST_CODE_EXPORT_PROFILES)
                },
            )
        }
    }

    @EventHandler(runOnMainThread = true)
    fun onObjectsChanged(event: ObjectsListChangedEvent) {
        selectionObserver?.invalidate()
        refreshComposeActions()
    }

    @EventHandler(runOnMainThread = true)
    fun onSelectionChanged(event: SelectedObjectChangedEvent) {
        selectionObserver?.invalidate()
        refreshComposeActions()
    }

    @EventHandler(runOnMainThread = true)
    fun onFlattenModeReset(event: FlattenModeResetEvent) {
        refreshComposeActions()
    }

    @EventHandler(runOnMainThread = true)
    fun onFeaturedUpdated(event: CloudFeaturesUpdatedEvent) {
        refreshComposeActions()
    }

}

/** Opens the optional cloud model generator through the exclusive workspace panel. */
internal fun showAIGeneratorWorkspace(bed: BedFragment) {
    val context = bed.getContext() ?: return
    val activity = context as? MainActivity ?: return
    bed.showWorkspaceComposeDialog { dismiss ->
        GeneratorWorkspaceDialog(R.string.MenuFileAIGenerator, onDismiss = dismiss) {
            val quota = remember(context) { AIGeneratorQuotaState(context) }
            DisposableEffect(quota) {
                quota.register()
                onDispose { quota.unregister() }
            }
            AIGeneratorMenuContent(
                remainingLabel = quota.remainingLabel,
                quotaProgress = quota.progress,
                onCamera = { openAIGeneratorCamera(context, activity, dismiss) },
                onGallery = { openAIGeneratorGallery(activity, dismiss) },
            )
        }
    }
}

class AIGeneratorQuotaState(private val context: Context) {
    var remainingLabel by mutableStateOf("")
        private set
    var progress by mutableStateOf(0f)
        private set

    fun register() {
        SliceBeam.EVENT_BUS.registerListener(this)
        refresh()
    }

    fun unregister() {
        SliceBeam.EVENT_BUS.unregisterListener(this)
    }

    @EventHandler(runOnMainThread = true)
    fun onFeaturedUpdated(event: CloudFeaturesUpdatedEvent) = refresh()

    @EventHandler(runOnMainThread = true)
    fun onRemainingUpdated(event: CloudModelsRemainingCountUpdatedEvent) = refresh()

    private fun refresh() {
        val total = CloudController.getMaxGeneratedModels()
        val generated = CloudController.getGeneratedModels()
        remainingLabel = context.resources.getQuantityString(
            R.plurals.MenuFileAIGeneratorRemaining,
            total,
            (total - generated).coerceAtLeast(0),
            total,
        )
        progress = aiGenerationQuotaProgress(total, generated)
    }
}

private fun canStartAIGeneration(): Boolean {
    if (CloudController.getGeneratedModels() >= CloudController.getMaxGeneratedModels()) {
        SliceBeam.EVENT_BUS.fireEvent(
            NeedSnackbarEvent(SnackbarsLayout.Type.ERROR, R.string.MenuFileAIGeneratorNoGenerationsLeft),
        )
        return false
    }
    if (MainActivity.IS_GENERATING_AI_MODEL) {
        SliceBeam.EVENT_BUS.fireEvent(
            NeedSnackbarEvent(SnackbarsLayout.Type.WARNING, R.string.MenuFileAIGeneratorAlreadyGenerating),
        )
        return false
    }
    return true
}

private fun openAIGeneratorCamera(context: Context, activity: MainActivity, dismiss: () -> Unit) {
    if (!canStartAIGeneration()) return
    try {
        val photo = File.createTempFile("ai_capture", ".jpg")
        MainActivity.aiTempFile = photo
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(
                MediaStore.EXTRA_OUTPUT,
                FileProvider.getUriForFile(
                    context,
                    "${BuildConfig.APPLICATION_ID}.slicermore.files",
                    photo,
                ),
            )
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        dismiss()
        activity.launchAppActivityResult(intent, MainActivity.REQUEST_CODE_AI_GENERATOR_TAKE_PHOTO)
    } catch (error: IOException) {
        android.util.Log.e("FileMenu", "Could not prepare a camera image for model generation", error)
        SliceBeam.EVENT_BUS.fireEvent(
            NeedSnackbarEvent(SnackbarsLayout.Type.ERROR, R.string.MenuFileOpenFileFailed),
        )
    }
}

private fun openAIGeneratorGallery(activity: MainActivity, dismiss: () -> Unit) {
    if (!canStartAIGeneration()) return
    val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
    dismiss()
    activity.launchAppActivityResult(
        Intent.createChooser(intent, ""),
        MainActivity.REQUEST_CODE_AI_GENERATOR_CHOOSE_PHOTO,
    )
}

@Composable
private fun AIGeneratorMenuContent(
    remainingLabel: String,
    quotaProgress: Float,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)
            .verticalScroll(rememberScrollState()).padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AIGeneratorActionRow(
            icon = R.drawable.vk_camera_outline_24,
            title = stringResource(R.string.MenuFileAIGeneratorFromCamera),
            testTag = "ai-generator-camera",
            onClick = onCamera,
        )
        AIGeneratorActionRow(
            icon = R.drawable.picture_outline_28,
            title = stringResource(R.string.MenuFileAIGeneratorFromGallery),
            testTag = "ai-generator-gallery",
            onClick = onGallery,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = remainingLabel,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            LinearProgressIndicator(
                progress = { quotaProgress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

@Composable
private fun AIGeneratorActionRow(icon: Int, title: String, testTag: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(shape).testTag(testTag)
            .clickable(role = Role.Button, onClick = onClick),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
