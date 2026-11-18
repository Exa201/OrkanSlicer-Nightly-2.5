package ru.ytkab0bp.slicebeam.components.bed_menu

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import ru.ytkab0bp.eventbus.EventHandler
import ru.ytkab0bp.slicebeam.events.CloudFeaturesUpdatedEvent
import ru.ytkab0bp.slicebeam.events.CloudModelsRemainingCountUpdatedEvent
import ru.ytkab0bp.slicebeam.events.ObjectsListChangedEvent
import ru.ytkab0bp.slicebeam.events.SelectedObjectChangedEvent

/** Exposes workspace tools directly instead of hiding them behind the File menu's More dialog. */
class OptionsMenu : ListBedMenu() {
    private val actionSource = FileMenu()
    private val composeActions = mutableStateOf<List<BedComposeMenuAction>>(emptyList())

    override fun onCreateItems(portrait: Boolean): List<BedMenuItem> = emptyList()

    @Composable
    override fun WorkspaceContent(portrait: Boolean) {
        // Re-read selection and availability after an orientation change as well as
        // after changing the hosted fragment. Otherwise a landscape recreation can
        // retain the previous action snapshot and make still-valid actions appear disabled.
        LaunchedEffect(this, fragment, portrait) { refreshActions() }
        androidx.compose.runtime.SideEffect {
            fragment?.updateWorkspaceMenuActionCount(composeActions.value.size)
        }
        CustomizableBedMenuActionsContent("tools", composeActions.value, portrait)
    }

    private fun refreshActions() {
        actionSource.onSetBed(fragment)
        composeActions.value = actionSource.createOptionsItems().map { item ->
            BedComposeMenuAction(
                titleRes = item.titleRes,
                iconRes = item.iconRes,
                onClick = { anchor ->
                    if (item.isCheckable) {
                        item.isChecked = !item.isChecked
                        item.checkedChangeListener?.onCheckedChanged(item.isChecked)
                        refreshActions()
                    } else {
                        item.clickListener?.invoke(anchor)
                    }
                },
                selected = item.isCheckable && item.isChecked,
                toggleable = item.isCheckable,
                sectionBreakBefore = item.sectionBreakBefore,
                enabled = item.isEnabled,
            )
        }
    }

    @EventHandler(runOnMainThread = true)
    fun onObjectsChanged(event: ObjectsListChangedEvent) = refreshActions()

    @EventHandler(runOnMainThread = true)
    fun onSelectionChanged(event: SelectedObjectChangedEvent) = refreshActions()

    @EventHandler(runOnMainThread = true)
    fun onCloudFeaturesUpdated(event: CloudFeaturesUpdatedEvent) = refreshActions()

    @EventHandler(runOnMainThread = true)
    fun onCloudModelsRemainingUpdated(event: CloudModelsRemainingCountUpdatedEvent) = refreshActions()
}
