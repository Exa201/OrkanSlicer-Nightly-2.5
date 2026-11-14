package ru.ytkab0bp.slicebeam.components.bed_menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.ytkab0bp.slicebeam.R

/** Per-install menu customization. Action handlers stay owned by their original menus. */
@Composable
internal fun CustomizableBedMenuActionsContent(
    menuKey: String,
    actions: List<BedComposeMenuAction>,
    portrait: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val menuCustomizeLabel = stringResource(R.string.MenuCustomize)
    val actionKeys = remember(actions) { actions.map { context.resources.getResourceEntryName(it.titleRes) } }
    val preferences = remember(context) { context.getSharedPreferences("workspace_menu_customization", 0) }
    fun orderFromPreferences(keys: List<String>): List<String> {
        val saved = preferences.getString("order_$menuKey", null).orEmpty().split('|').filter(String::isNotBlank)
        return saved.filter { it in keys } + keys.filter { it !in saved }
    }
    var order by remember(menuKey, actionKeys) { mutableStateOf(orderFromPreferences(actionKeys)) }
    var hidden by remember(menuKey, actionKeys) {
        mutableStateOf(preferences.getStringSet("hidden_$menuKey", emptySet()).orEmpty().intersect(actionKeys.toSet()))
    }
    var editing by remember(menuKey) { mutableStateOf(false) }
    var draftOrder by remember(menuKey) { mutableStateOf(emptyList<String>()) }
    var draftHidden by remember(menuKey) { mutableStateOf(emptySet<String>()) }
    val dragState = remember(menuKey) { MenuActionDragState() }
    val actionByKey = remember(actions, actionKeys) { actionKeys.zip(actions).toMap() }
    val visibleActions = order.filterNot { it in hidden }.mapNotNull(actionByKey::get)

    fun persistOrder(next: List<String>) {
        order = next
        preferences.edit().putString("order_$menuKey", next.joinToString("|")).apply()
    }

    fun swapVisibleAction(titleRes: Int, direction: Int) {
        val key = context.resources.getResourceEntryName(titleRes)
        val visibleKeys = order.filterNot { it in hidden }
        val index = visibleKeys.indexOf(key)
        val targetKey = visibleKeys.getOrNull(index + direction) ?: return
        val from = order.indexOf(key)
        val target = order.indexOf(targetKey)
        if (from < 0 || target < 0) return
        persistOrder(order.toMutableList().also {
            val current = it[from]
            it[from] = it[target]
            it[target] = current
        })
    }

    fun moveToVisibleTarget(sourceKey: String, targetTitleRes: Int) {
        val targetKey = context.resources.getResourceEntryName(targetTitleRes)
        if (sourceKey == targetKey || sourceKey !in order || targetKey !in order) return
        val visibleKeys = order.filterNot { it in hidden }
        val sourceVisibleIndex = visibleKeys.indexOf(sourceKey)
        val targetVisibleIndex = visibleKeys.indexOf(targetKey)
        if (sourceVisibleIndex < 0 || targetVisibleIndex < 0) return
        val next = order.toMutableList()
        next.remove(sourceKey)
        val targetIndex = next.indexOf(targetKey)
        if (targetIndex < 0) return
        // Drop after the crossed item when moving toward the end, and before it
        // when moving toward the start. Always inserting before the target made
        // a drag from the first to the second item visually complete but leave
        // the saved order unchanged.
        val insertionIndex = if (sourceVisibleIndex < targetVisibleIndex) targetIndex + 1 else targetIndex
        next.add(insertionIndex.coerceAtMost(next.size), sourceKey)
        order = next
        dragState.hasMoved = true
    }

    fun updateDropTarget(pointer: Offset?) {
        val source = dragState.activeTitleRes ?: return
        val visibleTitleIds = visibleActions.map { it.titleRes }.toSet()
        dragState.lastTargetTitleRes = pointer?.let { point ->
            dragState.bounds.entries.firstOrNull { (candidate, bounds) ->
                candidate != source && candidate in visibleTitleIds && bounds.contains(point)
            }?.key
        }
    }

    val onActionPositioned: (Int, Rect) -> Unit = { id, bounds -> dragState.bounds[id] = bounds }
    val onActionDragStart: (Int, Offset) -> Unit = { id, localOffset ->
        dragState.hasMoved = false
        dragState.lastTargetTitleRes = null
        dragState.activeTitleRes = id
        dragState.pointerInRoot = dragState.bounds[id]?.topLeft?.plus(localOffset)
        dragState.dragTranslation = Offset.Zero
    }
    val onActionDrag: (Int, Offset) -> Unit = { id, delta ->
        if (dragState.activeTitleRes == id) {
            dragState.dragTranslation += delta
            dragState.pointerInRoot = dragState.pointerInRoot?.plus(delta)
            updateDropTarget(dragState.pointerInRoot)
        }
    }
    val onActionDragEnd: () -> Unit = {
        val sourceTitle = dragState.activeTitleRes
        // Resolve once more on release so the drop uses the latest item positions,
        // including positions changed by horizontal auto-scroll near the rail edge.
        updateDropTarget(dragState.pointerInRoot)
        val targetTitle = dragState.lastTargetTitleRes
        if (sourceTitle != null && targetTitle != null) {
            moveToVisibleTarget(context.resources.getResourceEntryName(sourceTitle), targetTitle)
        }
        if (dragState.hasMoved) {
            preferences.edit().putString("order_$menuKey", order.joinToString("|")).apply()
        }
        dragState.hasMoved = false
        dragState.lastTargetTitleRes = null
        dragState.activeTitleRes = null
        dragState.pointerInRoot = null
        dragState.dragTranslation = Offset.Zero
    }

    val openCustomization = {
        draftOrder = order.toList()
        draftHidden = hidden.toSet()
        editing = true
    }
    if (portrait) {
        // Keep the customization control beside the horizontal action strip. A separate
        // header row consumed most of the short workspace panel on compact phones and
        // reduced every action to an unusable touch target.
        Row(modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            BedMenuActionsContent(
                visibleActions, portrait, Modifier.weight(1f),
                onActionPositioned = onActionPositioned,
                onActionDragStart = onActionDragStart,
                onActionDrag = onActionDrag,
                onActionDragEnd = onActionDragEnd,
                onMoveAction = ::swapVisibleAction,
                draggedActionTitleRes = dragState.activeTitleRes,
                dropTargetTitleRes = dragState.lastTargetTitleRes,
                draggedActionTranslation = dragState.dragTranslation,
                draggedActionPointerInRoot = dragState.pointerInRoot,
                onDragAutoScroll = { amount ->
                    dragState.dragTranslation += Offset(amount, 0f)
                    updateDropTarget(dragState.pointerInRoot)
                },
            )
            TextButton(
                onClick = openCustomization,
                modifier = Modifier.width(48.dp).fillMaxSize().testTag("menu-customize-$menuKey"),
            ) {
                Text("⋮", style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { contentDescription = menuCustomizeLabel })
            }
        }
    } else {
        Column(modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = openCustomization,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("menu-customize-$menuKey"),
                ) { Text(stringResource(R.string.MenuCustomize)) }
            }
            BedMenuActionsContent(
                visibleActions, portrait, Modifier.weight(1f),
                onActionPositioned = onActionPositioned,
                onActionDragStart = onActionDragStart,
                onActionDrag = onActionDrag,
                onActionDragEnd = onActionDragEnd,
                onMoveAction = ::swapVisibleAction,
                draggedActionTitleRes = dragState.activeTitleRes,
                dropTargetTitleRes = dragState.lastTargetTitleRes,
                draggedActionTranslation = dragState.dragTranslation,
                draggedActionPointerInRoot = dragState.pointerInRoot,
                onDragAutoScroll = { amount ->
                    dragState.dragTranslation += Offset(amount, 0f)
                    updateDropTarget(dragState.pointerInRoot)
                },
            )
        }
    }

    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(stringResource(R.string.MenuCustomize)) },
            text = {
                Column(Modifier.heightIn(max = 520.dp)) {
                    Text(
                        stringResource(R.string.MenuCustomizeDragHint),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    LazyColumn(Modifier.heightIn(max = 460.dp).testTag("menu-customize-list-$menuKey")) {
                        items(draftOrder, key = { key -> key }) { key ->
                            val action = actionByKey[key] ?: return@items
                            val actionTitle = stringResource(action.titleRes)
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = key !in draftHidden,
                                    onCheckedChange = { checked ->
                                        draftHidden = if (checked) draftHidden - key else draftHidden + key
                                    },
                                    modifier = Modifier
                                        .semantics { contentDescription = actionTitle }
                                        .testTag("menu-customize-visible-$menuKey-$key"),
                                )
                                Text(
                                    actionTitle, Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    order = draftOrder
                    hidden = draftHidden
                    preferences.edit()
                        .putString("order_$menuKey", draftOrder.joinToString("|"))
                        .putStringSet("hidden_$menuKey", draftHidden)
                        .apply()
                    editing = false
                }, modifier = Modifier.heightIn(min = 48.dp).testTag("menu-customize-save-$menuKey")) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        draftOrder = actionKeys
                        draftHidden = emptySet()
                    }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.MenuCustomizeReset)) }
                    TextButton(onClick = { editing = false }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            },
        )
    }
}

private class MenuActionDragState {
    val bounds = mutableStateMapOf<Int, Rect>()
    var activeTitleRes by mutableStateOf<Int?>(null)
    var pointerInRoot by mutableStateOf<Offset?>(null)
    var dragTranslation by mutableStateOf(Offset.Zero)
    var lastTargetTitleRes by mutableStateOf<Int?>(null)
    var hasMoved: Boolean = false
}
