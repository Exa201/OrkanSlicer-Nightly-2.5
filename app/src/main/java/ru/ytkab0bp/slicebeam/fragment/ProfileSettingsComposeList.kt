package ru.ytkab0bp.slicebeam.fragment

import android.util.SparseBooleanArray
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.cloud.CloudController
import ru.ytkab0bp.slicebeam.theme.ThemesRepo
import ru.ytkab0bp.slicebeam.ui.settings.ComposeCloudProfileHeader
import ru.ytkab0bp.slicebeam.ui.settings.ComposeProfileCategoryRow
import ru.ytkab0bp.slicebeam.ui.settings.ProfileSettingsMenuLayoutPolicy
import ru.ytkab0bp.slicebeam.utils.Prefs
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlinx.coroutines.launch

fun interface ProfileCategoryRowsUpdater {
    fun update(categoryIndex: Int, insertionIndex: Int, expanded: Boolean)
}

internal object ProfileSettingsRowRenderPolicy {
    fun usesDirectCompose(item: ProfileSettingsComposeItem?, optionSupportsDirect: Boolean): Boolean =
        optionSupportsDirect || item != null
}

/** Lazy-list keys must be Bundle-saveable and stable when settings are filtered or expanded. */
internal object ProfileSettingsRowSaveableKey {
    @JvmStatic
    fun create(index: Int, wrapper: ProfileOptionWrapper): String {
        wrapper.optionEl?.saveableRowIdentity()?.let { return "option:$it" }
        wrapper.title?.let { return "heading:${wrapper.categoryIndex}:$it" }
        if (wrapper.hasSpecialType()) return "special:${wrapper.categoryIndex}:${wrapper.optionEl?.specialType}"
        val itemType = wrapper.optionEl?.simpleItem?.javaClass?.name ?: "empty"
        return "row:${wrapper.categoryIndex}:$index:$itemType"
    }
}

/** Direct Compose content for the shared printer, process, and filament profile rows. */
class ProfileSettingsComposeList(
    private val unfolded: SparseBooleanArray,
    private val categoryRowsUpdater: ProfileCategoryRowsUpdater,
    private val onTabletCategorySelected: ((String) -> Boolean)? = null,
) {
    private var rows by mutableStateOf<List<ProfileOptionWrapper>>(emptyList())
    private var decorations by mutableStateOf<List<ProfileSettingsRowSections.Decoration>>(emptyList())
    private var rowVersions by mutableStateOf<Map<Int, Int>>(emptyMap())
    private var contentVersion by mutableIntStateOf(0)
    private var scrollToTopVersion by mutableIntStateOf(0)
    private var emptyMessage by mutableStateOf<String?>(null)
    private var listOverlayAlpha by mutableFloatStateOf(1f)

    @Composable
    fun Content(modifier: Modifier = Modifier, headerContent: (@Composable () -> Unit)? = null) {
        val currentRows = rows
        val listState = rememberLazyListState()
        var editingRowIndex by rememberSaveable { mutableIntStateOf(-1) }
        var savedScrollIndex by rememberSaveable { mutableIntStateOf(-1) }
        var savedScrollOffset by rememberSaveable { mutableIntStateOf(0) }
        val configuration = LocalConfiguration.current
        val scope = rememberCoroutineScope()
        LaunchedEffect(scrollToTopVersion) {
            if (scrollToTopVersion > 0) listState.scrollToItem(0)
        }
        LaunchedEffect(
            editingRowIndex,
            configuration.orientation,
            configuration.screenWidthDp,
            configuration.screenHeightDp,
        ) {
            if (editingRowIndex in currentRows.indices) {
                listState.scrollToItem(editingRowIndex + if (headerContent != null) 1 else 0)
            }
        }
        val onEditorStateChanged: (Int, Boolean) -> Unit = { index, open ->
            if (open && index in currentRows.indices && editingRowIndex != index) {
                if (editingRowIndex == -1) {
                    savedScrollIndex = listState.firstVisibleItemIndex
                    savedScrollOffset = listState.firstVisibleItemScrollOffset
                }
                editingRowIndex = index
                scope.launch { listState.scrollToItem(index + if (headerContent != null) 1 else 0) }
            } else if (!open && editingRowIndex == index) {
                editingRowIndex = -1
                val restoreIndex = savedScrollIndex
                val restoreOffset = savedScrollOffset
                savedScrollIndex = -1
                savedScrollOffset = 0
                if (restoreIndex in currentRows.indices) {
                    scope.launch { listState.scrollToItem(restoreIndex, restoreOffset) }
                }
            }
        }
        ProfileSettingsListContent(
            modifier = modifier,
            rows = currentRows,
            decorations = decorations,
            rowVersions = rowVersions,
            contentVersion = contentVersion,
            emptyMessage = emptyMessage,
            overlayAlpha = listOverlayAlpha,
            listState = listState,
            unfolded = unfolded,
            categoryRowsUpdater = categoryRowsUpdater,
            onEditorStateChanged = onEditorStateChanged,
            headerContent = headerContent,
            onCategorySelected = { title ->
                if (editingRowIndex != -1) {
                    editingRowIndex = -1
                    savedScrollIndex = -1
                    savedScrollOffset = 0
                }
                onTabletCategorySelected?.invoke(title) == true
            },
        )
    }

    fun submit(rows: List<ProfileOptionWrapper>) {
        this.rows = rows.toList()
        decorations = ProfileSettingsRowSections.decorate(this.rows)
        rowVersions = emptyMap()
        contentVersion++
    }

    fun refreshItem(position: Int) {
        if (position !in rows.indices) return
        rowVersions = rowVersions + (position to ((rowVersions[position] ?: contentVersion) + 1))
    }

    fun refreshTheme() {
        contentVersion++
    }

    fun setEmptyMessage(message: CharSequence?) {
        emptyMessage = message?.toString()?.takeIf { it.isNotBlank() }
    }

    fun scrollToTop() {
        scrollToTopVersion++
    }

    /** Keeps the profile-page pager's edge fade without coupling this screen to RecyclerView. */
    fun setOverlayAlpha(alpha: Float) {
        listOverlayAlpha = alpha.coerceIn(0f, 1f)
    }
}

@Composable
private fun ProfileSettingsListContent(
    modifier: Modifier,
    rows: List<ProfileOptionWrapper>,
    decorations: List<ProfileSettingsRowSections.Decoration>,
    rowVersions: Map<Int, Int>,
    contentVersion: Int,
    emptyMessage: String?,
    overlayAlpha: Float,
    listState: LazyListState,
    unfolded: SparseBooleanArray,
    categoryRowsUpdater: ProfileCategoryRowsUpdater,
    onEditorStateChanged: (Int, Boolean) -> Unit,
    onCategorySelected: (String) -> Boolean,
    headerContent: (@Composable () -> Unit)?,
) {
    val configuration = LocalConfiguration.current
    val categoryPane = ProfileSettingsMenuLayoutPolicy.usesCategoryPane(
        configuration.screenWidthDp,
        configuration.fontScale,
    )
    val categories = ProfileSettingsCategoryPanePolicy.categories(rows)
    val categoryTitles = categories.map { it.title.orEmpty() }
    var selectedCategoryTitle by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(categoryTitles, categoryPane) {
        if (categoryPane && categories.isNotEmpty() && selectedCategoryTitle !in categoryTitles) {
            val first = categories.first()
            val title = first.title.orEmpty()
            selectedCategoryTitle = title
            if (!onCategorySelected(title)) first.onClick?.run()
        }
    }
    val selectedCategory = categories.firstOrNull { it.title == selectedCategoryTitle }
        ?: categories.firstOrNull()
    val visibleRows = if (categoryPane && selectedCategory != null) {
        ProfileSettingsCategoryPanePolicy.rowsForCategory(rows, selectedCategory.categoryIndex)
    } else {
        rows.withIndex().toList()
    }

    if (categoryPane && selectedCategory != null) {
        Row(modifier = modifier.fillMaxSize()) {
            TabletProfileCategorySelector(
                categories = categories,
                selectedTitle = selectedCategoryTitle,
                onSelected = { category ->
                    val title = category.title.orEmpty()
                    selectedCategoryTitle = title
                    if (!onCategorySelected(title)) category.onClick?.run()
                },
                modifier = Modifier.width(248.dp),
            )
            Spacer(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            ProfileSettingsRowsContent(
                modifier = Modifier.weight(1f),
                rows = visibleRows,
                decorations = decorations,
                rowVersions = rowVersions,
                contentVersion = contentVersion,
                emptyMessage = emptyMessage,
                overlayAlpha = overlayAlpha,
                listState = listState,
                unfolded = unfolded,
                categoryRowsUpdater = categoryRowsUpdater,
                onEditorStateChanged = onEditorStateChanged,
                headerContent = headerContent,
            )
        }
        LaunchedEffect(selectedCategoryTitle) {
            listState.scrollToItem(0)
        }
    } else {
        ProfileSettingsRowsContent(
            modifier = modifier,
            rows = visibleRows,
            decorations = decorations,
            rowVersions = rowVersions,
            contentVersion = contentVersion,
            emptyMessage = emptyMessage,
            overlayAlpha = overlayAlpha,
            listState = listState,
            unfolded = unfolded,
            categoryRowsUpdater = categoryRowsUpdater,
            onEditorStateChanged = onEditorStateChanged,
            headerContent = headerContent,
        )
    }
}

@Composable
private fun TabletProfileCategorySelector(
    categories: List<ProfileOptionWrapper>,
    selectedTitle: String?,
    onSelected: (ProfileOptionWrapper) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(categories, key = { _, category -> category.title.orEmpty() }) { _, category ->
            val selected = category.title == selectedTitle
            val container = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
            val foreground = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(container)
                    .clickable(role = Role.Button) { onSelected(category) }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(category.icon),
                    contentDescription = null,
                    tint = foreground,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = category.title.orEmpty(),
                    modifier = Modifier.weight(1f),
                    color = foreground,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ProfileSettingsRowsContent(
    modifier: Modifier,
    rows: List<IndexedValue<ProfileOptionWrapper>>,
    decorations: List<ProfileSettingsRowSections.Decoration>,
    rowVersions: Map<Int, Int>,
    contentVersion: Int,
    emptyMessage: String?,
    overlayAlpha: Float,
    listState: LazyListState,
    unfolded: SparseBooleanArray,
    categoryRowsUpdater: ProfileCategoryRowsUpdater,
    onEditorStateChanged: (Int, Boolean) -> Unit,
    headerContent: (@Composable () -> Unit)?,
) {
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
                .testTag("profile-settings-options-list")
                .profileListFade(listState, overlayAlpha),
        ) {
            if (headerContent != null) {
                item(key = "profile-settings-scroll-header") {
                    headerContent()
                }
            }
            itemsIndexed(rows, key = { _, entry ->
                ProfileSettingsRowSaveableKey.create(entry.index, entry.value)
            }) { visibleIndex, entry ->
                val index = entry.index
                val wrapper = entry.value
                val decoration = decorations.getOrNull(index)
                    ?: ProfileSettingsRowSections.Decoration(false, false, 0)
                val bindVersion = rowVersions[index] ?: contentVersion
                ProfileSettingsRow(
                    wrapper = wrapper,
                    index = index,
                    decoration = decoration,
                    bindVersion = bindVersion,
                    unfolded = unfolded,
                    categoryRowsUpdater = categoryRowsUpdater,
                    onEditorStateChanged = { isOpen -> onEditorStateChanged(visibleIndex, isOpen) },
                )
            }
        }
        if (rows.isEmpty() && emptyMessage != null) {
            Text(
                text = emptyMessage,
                modifier = Modifier.fillMaxWidth().align(Alignment.Center).padding(28.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ProfileSettingsRow(
    wrapper: ProfileOptionWrapper,
    index: Int,
    decoration: ProfileSettingsRowSections.Decoration,
    bindVersion: Int,
    unfolded: SparseBooleanArray,
    categoryRowsUpdater: ProfileCategoryRowsUpdater,
    onEditorStateChanged: (Boolean) -> Unit,
) {
    // Keep profile pages on the shared navy palette. Section hues remain in
    // their icons and values; broad tinted panels made print/printer pages look
    // like different themes and reduced text contrast.
    val backgroundColor = MaterialTheme.colorScheme.surfaceVariant
    val shape = RoundedCornerShape(
        topStart = if (decoration.startsSection) 18.dp else 0.dp,
        topEnd = if (decoration.startsSection) 18.dp else 0.dp,
        bottomEnd = if (decoration.endsSection) 18.dp else 0.dp,
        bottomStart = if (decoration.endsSection) 18.dp else 0.dp,
    )
    val rowTestTag = wrapper.optionEl?.optionDef?.key
        ?.takeIf { it.isNotBlank() }
        ?.let { "profile-option-key-$it" }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (rowTestTag != null) Modifier.testTag(rowTestTag) else Modifier)
            .padding(top = if (decoration.startsSection) 8.dp else 0.dp)
            .background(backgroundColor, shape),
    ) {
        when {
            wrapper.hasSpecialType() -> {
                val loggedIn = Prefs.getCloudAPIToken() != null
                val info = if (loggedIn) CloudController.getUserInfo() else null
                ComposeCloudProfileHeader(
                    title = when {
                        !loggedIn -> stringResource(R.string.SettingsCloudNotLoggedIn)
                        info == null -> stringResource(R.string.SettingsCloudLoading)
                        else -> info.displayName.orEmpty()
                    },
                    subtitle = stringResource(
                        if (loggedIn) R.string.SettingsCloudTapToManage else R.string.SettingsCloudTapToShowMore,
                    ),
                    avatarUrl = info?.avatarUrl,
                    onClick = wrapper.onClick?.let { action -> { action.run() } },
                )
            }
            wrapper.title != null -> {
                val categoryIndex = wrapper.categoryIndex
                val expanded = if (wrapper.isExpandable) wrapper.isExpanded else unfolded.get(categoryIndex, false)
                ComposeProfileCategoryRow(
                    iconResource = wrapper.icon,
                    title = wrapper.title.orEmpty(),
                    expanded = expanded,
                    expandable = wrapper.isExpandable || wrapper.onClick == null,
                    noIconTint = wrapper.noTint,
                    accentColor = wrapper.color.takeIf { it != 0 }?.let(ThemesRepo::getColor),
                    onClick = {
                            if (wrapper.onClick != null) {
                            wrapper.onClick?.run()
                        } else {
                            val nextExpanded = !unfolded.get(categoryIndex, false)
                            unfolded.put(categoryIndex, nextExpanded)
                            categoryRowsUpdater.update(categoryIndex, index + 1, nextExpanded)
                        }
                    },
                )
            }
            else -> {
                val option = wrapper.optionEl
                val item = option?.simpleItem
                if (ProfileSettingsRowRenderPolicy.usesDirectCompose(item, option?.usesDirectCompose() == true)) {
                    when {
                        option?.usesDirectCompose() == true ->
                            option.DirectContent(bindVersion, index, onEditorStateChanged)
                        item != null -> {
                            SideEffect { option.bindRowPosition(index) }
                            item.Content(index, onEditorStateChanged)
                        }
                        else -> Spacer(modifier = Modifier.height(0.dp))
                    }
                } else {
                    Spacer(modifier = Modifier.height(0.dp))
                }
            }
        }
    }
}

private fun Modifier.profileListFade(listState: LazyListState, overlayAlpha: Float): Modifier =
    drawWithContent {
        drawContent()
        if (overlayAlpha <= 0f) return@drawWithContent
        val visible = listState.layoutInfo.visibleItemsInfo
        if (visible.isEmpty()) return@drawWithContent
        val fadeSize = 32.dp.toPx().coerceAtMost(size.height / 2f)
        if (fadeSize <= 0f) return@drawWithContent
        val background = Color(ThemesRepo.getColor(android.R.attr.windowBackground))
        val first = visible.first()
        val topProgress = if (first.index > 0) 1f else {
            ((listState.layoutInfo.viewportStartOffset - first.offset) / fadeSize).coerceIn(0f, 1f)
        }
        if (topProgress > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(background.copy(alpha = topProgress * overlayAlpha), Color.Transparent),
                    startY = 0f,
                    endY = fadeSize,
                ),
                topLeft = Offset.Zero,
                size = Size(size.width, fadeSize),
            )
        }
        val last = visible.last()
        val layoutInfo = listState.layoutInfo
        val bottomProgress = if (last.index < layoutInfo.totalItemsCount - 1) 1f else {
            ((last.offset + last.size - layoutInfo.viewportEndOffset) / fadeSize).coerceIn(0f, 1f)
        }
        if (bottomProgress > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, background.copy(alpha = bottomProgress * overlayAlpha)),
                    startY = size.height - fadeSize,
                    endY = size.height,
                ),
                topLeft = Offset(0f, size.height - fadeSize),
                size = Size(size.width, fadeSize),
            )
        }
    }
