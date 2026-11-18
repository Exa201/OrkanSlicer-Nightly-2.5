package ru.ytkab0bp.slicebeam.components.bed_menu

import android.view.View
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import ru.ytkab0bp.eventbus.EventHandler
import ru.ytkab0bp.slicebeam.events.FlattenModeResetEvent
import ru.ytkab0bp.slicebeam.events.ObjectsListChangedEvent
import ru.ytkab0bp.slicebeam.events.SelectedObjectChangedEvent
import ru.ytkab0bp.slicebeam.fragment.BedFragment
import java.util.function.Consumer

class OrientationMenu : ListBedMenu() {
    private val composeActions = mutableStateOf<List<BedComposeMenuAction>>(emptyList())
    private var menuItems: OrientationMenuItems? = null

    override fun onCreateItems(portrait: Boolean): List<BedMenuItem> = emptyList()

    @Composable
    override fun WorkspaceContent(portrait: Boolean) {
        val bed = fragment ?: return
        val items = remember(this, bed) {
            OrientationMenuItems(
                bed,
                Consumer<View> { bed.showWorkspaceComposeTool(PositionMenu()) },
                Consumer<View> { bed.showWorkspaceComposeTool(RotationMenu()) },
                ::refreshActionItems,
            )
        }
        LaunchedEffect(items, portrait) {
            menuItems = items
            items.invalidateSelectionState()
            composeActions.value = items.createItems()
        }
        androidx.compose.runtime.SideEffect { bed.updateWorkspaceMenuActionCount(composeActions.value.size) }
        BedMenuActionsContent(composeActions.value, portrait)
    }

    @EventHandler(runOnMainThread = true)
    fun onFlattenModeReset(event: FlattenModeResetEvent) {
        menuItems?.onFlattenModeReset()
        refreshActionItems()
    }

    @EventHandler(runOnMainThread = true)
    fun onObjectsChanged(event: ObjectsListChangedEvent) {
        menuItems?.onObjectsChanged()
        refreshActionItems()
    }

    @EventHandler(runOnMainThread = true)
    fun onSelectionChanged(event: SelectedObjectChangedEvent) {
        menuItems?.onSelectionChanged()
        refreshActionItems()
    }

    private fun refreshActionItems() {
        composeActions.value = menuItems?.createItems().orEmpty()
    }

    /** Keep Java callers source-compatible with menu.new PositionMenu(). */
    inner class PositionMenu : OrientationPositionMenu(requireNotNull(this@OrientationMenu.fragment))

    /** Keep Java callers source-compatible with menu.new RotationMenu(). */
    inner class RotationMenu : OrientationRotationMenu(requireNotNull(this@OrientationMenu.fragment))
}
