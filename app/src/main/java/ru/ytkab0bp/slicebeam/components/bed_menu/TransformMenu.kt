package ru.ytkab0bp.slicebeam.components.bed_menu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import ru.ytkab0bp.eventbus.EventHandler
import ru.ytkab0bp.slicebeam.events.ObjectsListChangedEvent
import ru.ytkab0bp.slicebeam.events.SelectedObjectChangedEvent

/** Compose edit-menu host; selection-dependent action state follows model events. */
class TransformMenu : ListBedMenu() {
    private val composeActions = mutableStateOf<List<BedComposeMenuAction>>(emptyList())
    private var menuItems: TransformMenuItems? = null

    override fun onCreateItems(portrait: Boolean): List<BedMenuItem> = emptyList()

    @Composable
    override fun WorkspaceContent(portrait: Boolean) {
        val bed = fragment ?: return
        val items = remember(this, bed) { createTransformMenuItems(bed, ::refreshActionItems) }
        LaunchedEffect(items, portrait) {
            menuItems = items
            items.invalidateSelectionState()
            composeActions.value = items.createItems()
        }
        CustomizableBedMenuActionsContent("edit", composeActions.value, portrait)
    }

    @EventHandler(runOnMainThread = true)
    fun onObjectsChanged(event: ObjectsListChangedEvent) {
        menuItems?.invalidateSelectionState()
        refreshActionItems()
    }

    @EventHandler(runOnMainThread = true)
    fun onSelectionChanged(event: SelectedObjectChangedEvent) {
        menuItems?.invalidateSelectionState()
        refreshActionItems()
    }

    private fun refreshActionItems() {
        composeActions.value = menuItems?.createItems().orEmpty()
    }
}
