package ru.ytkab0bp.slicebeam.components.bed_menu

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.eventbus.EventHandler
import ru.ytkab0bp.slicebeam.events.FlattenModeResetEvent
import ru.ytkab0bp.slicebeam.events.ObjectsListChangedEvent
import ru.ytkab0bp.slicebeam.events.SelectedObjectChangedEvent

/** Model selection, placement, duplication and object-list actions. */
class ModelMenu : ListBedMenu() {
    private val composeActions = mutableStateOf<List<BedComposeMenuAction>>(emptyList())
    private val selectedScope = mutableIntStateOf(0)
    private var menuItems: ModelMenuItems? = null

    override fun onCreateItems(portrait: Boolean): List<BedMenuItem> = emptyList()

    @Composable
    override fun WorkspaceContent(portrait: Boolean) {
        val bed = fragment ?: return
        val items = remember(this, bed) { ModelMenuItems(bed, ::refreshActionItems) }
        LaunchedEffect(items, portrait) {
            menuItems = items
            items.invalidateSelectionState()
            refreshActionItems()
        }
        val selectedActions = composeActions.value.filter { ModelMenuActionScope.isSelectionScoped(it.titleRes) }
        val allActions = composeActions.value.filterNot { ModelMenuActionScope.isSelectionScoped(it.titleRes) }
        androidx.compose.runtime.SideEffect {
            bed.updateWorkspaceMenuActionCount(
                if (selectedScope.intValue == 0) selectedActions.size else allActions.size,
            )
        }
        Column(Modifier.fillMaxWidth().wrapContentHeight()) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = selectedScope.intValue == 0,
                    onClick = { selectedScope.intValue = 0 },
                    label = { Text(stringResource(R.string.ModelMenuSelectedObjectGroup)) },
                    leadingIcon = {
                        Image(
                            painterResource(if (selectedScope.intValue == 0) R.drawable.cad_auswahl_active else R.drawable.cad_auswahl),
                            contentDescription = null,
                            modifier = Modifier.size(31.dp),
                        )
                    },
                    modifier = Modifier.weight(1f).testTag("model-menu-scope-object"),
                )
                FilterChip(
                    selected = selectedScope.intValue == 1,
                    onClick = { selectedScope.intValue = 1 },
                    label = { Text(stringResource(R.string.ModelMenuAllObjectsGroup)) },
                    leadingIcon = {
                        Image(
                            painterResource(if (selectedScope.intValue == 1) R.drawable.cad_druckplatte_active else R.drawable.cad_druckplatte),
                            contentDescription = null,
                            modifier = Modifier.size(31.dp),
                        )
                    },
                    modifier = Modifier.weight(1f).testTag("model-menu-scope-all"),
                )
            }
            CustomizableBedMenuActionsContent(
                menuKey = if (selectedScope.intValue == 0) "model_selected" else "model_all",
                actions = if (selectedScope.intValue == 0) selectedActions else allActions,
                portrait = portrait,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    @EventHandler(runOnMainThread = true)
    fun onObjectsChanged(event: ObjectsListChangedEvent) {
        menuItems?.resetFlattenMode()
        menuItems?.invalidateSelectionState()
        refreshActionItems()
    }

    @EventHandler(runOnMainThread = true)
    fun onSelectionChanged(event: SelectedObjectChangedEvent) {
        menuItems?.resetFlattenMode()
        menuItems?.invalidateSelectionState()
        refreshActionItems()
    }

    @EventHandler(runOnMainThread = true)
    fun onFlattenModeReset(event: FlattenModeResetEvent) {
        menuItems?.resetFlattenMode()
        refreshActionItems()
    }

    private fun refreshActionItems() {
        composeActions.value = menuItems?.createItems().orEmpty()
    }
}

/** Separates actions for the current selection from actions affecting the full plate. */
internal object ModelMenuActionScope {
    private val selectionActions = setOf(
        R.string.WorkspaceObjects,
        R.string.ModelMenuDuplicate,
        R.string.ModelMenuMoveToPlate,
        R.string.ModelMenuSplit,
        R.string.ModelMenuPlaceOnObject,
        R.string.ModelMenuPlaceOnSurface,
        R.string.ModelMenuAlignSelected,
        R.string.ModelMenuAlignPrimaryAxes,
        R.string.ModelMenuDistributeSelected,
        R.string.ModelMenuCenterOnBed,
        R.string.ModelMenuPlaceGroupOnBed,
        R.string.ModelMenuCombineSelected,
        R.string.ModelMenuBoolean,
        R.string.ModelMenuFillBed,
        R.string.ModelMenuAutoOrientSelected,
        R.string.ModelMenuFlatten,
        R.string.ModelMenuDeleteSelected,
    )

    fun isSelectionScoped(titleRes: Int): Boolean = titleRes in selectionActions
}
