package ru.ytkab0bp.slicebeam.fragment

import android.content.Context
import android.content.res.Configuration
import android.util.SparseArray
import android.util.SparseBooleanArray
import android.view.View
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import ru.ytkab0bp.eventbus.EventHandler
import ru.ytkab0bp.slicebeam.config.TemporaryProjectSettings
import ru.ytkab0bp.slicebeam.events.TemporaryProjectSettingsChangedEvent
import ru.ytkab0bp.slicebeam.ui.settings.TemporaryProjectSettingsBar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import ru.ytkab0bp.slicebeam.MainActivity
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.SliceBeam
import ru.ytkab0bp.slicebeam.config.ConfigObject
import ru.ytkab0bp.slicebeam.config.ProfileSettingsRepository
import ru.ytkab0bp.slicebeam.config.ProfileNameValidator
import ru.ytkab0bp.slicebeam.navigation.Fragment
import ru.ytkab0bp.slicebeam.slic3r.ConfigOptionDef
import ru.ytkab0bp.slicebeam.ui.BeamComposeTheme
import ru.ytkab0bp.slicebeam.ui.OrkanSection
import ru.ytkab0bp.slicebeam.ui.settings.ComposeProfileSettingsStatus
import ru.ytkab0bp.slicebeam.ui.settings.PrinterProfileCategoryAssets
import ru.ytkab0bp.slicebeam.ui.settings.ProfileSettingsViewModel
import ru.ytkab0bp.slicebeam.ui.navigation.AdaptiveNavigationPolicy
import ru.ytkab0bp.slicebeam.view.FilamentPaletteController
import ru.ytkab0bp.slicebeam.view.ProfileDropdownContent
import ru.ytkab0bp.slicebeam.view.ProfileDropdownMenuModel
import ru.ytkab0bp.slicebeam.view.ProfileDropdownMenuPolicy
import java.util.HashMap
import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.function.IntConsumer
import java.util.function.Function
import java.util.function.Supplier

/** Shared Kotlin host for printer, process, filament, and app settings. */
abstract class ProfileListFragment : Fragment() {
    companion object {
        const val SPECIAL_TYPE_CLOUD_HEADER = 0
    }

    protected var recyclerView: ProfileSettingsComposeList? = null
    protected var filamentPaletteController: FilamentPaletteController? = null

    private var profileSettingsViewModel: ProfileSettingsViewModel? = null
    private var profileFooter: ProfileSettingsFooter.Controller? = null
    private var currentList: MutableList<ProfileOptionWrapper> = ArrayList()
    private var categoryElements: SparseArray<out List<ProfileOptionWrapper>> = SparseArray()
    private val unfolded = SparseBooleanArray()
    private var categoryTabsController: ProfileSettingsCategoryTabsController? = null
    private var unfilteredConfigItems: List<ProfileOptionElement> = emptyList()
    private var profileFilterController: ProfileSettingsFilterController? = null
    private val collapsedGroups = HashMap<String, Boolean>()
    private var themeRevision by mutableIntStateOf(0)
    private var dropdownTitle by mutableStateOf<String?>(null)
    private var dropdownMenu by mutableStateOf(ProfileDropdownMenuPolicy.create(emptyList(), -1))
    private var dropdownMenuTitle by mutableIntStateOf(R.string.SlotPrinterConfigTooltip)
    private var dropdownExpanded by mutableStateOf(false)
    private var activeOptionHelpDialog by mutableStateOf<ProfileOptionHelpDialogState?>(null)
    private var dropdownItemsSnapshot: List<ProfileListItem> = emptyList()
    private var renderedProfileTitle: String? = null
    private var renderedConfigUid: Int = -1

    /** The ConfigObject.PROFILE_LIST_* category this fragment edits, or -1 for app settings. */
    protected open fun getProfileListType(): Int = -1

    /** Optional Compose quick controls shown between the filament palette and category navigation. */
    @Composable
    protected open fun BelowFilamentPaletteContent(modifier: Modifier = Modifier) = Unit

    /** Optional route summary/actions shown at a stable position above profile settings. */
    @Composable
    protected open fun ProfileHeaderContent(modifier: Modifier = Modifier) = Unit

    /** Profile config screens override this to render categories as horizontal tabbed pages. */
    protected open fun useTabs(): Boolean = false

    /** Profile screens may show Orca categories as the same closed accordion list as SliceBeam. */
    protected open fun useAccordionCategories(): Boolean = false

    /** The print screen overrides this to show its multi-color filament palette bar. */
    protected open fun showFilamentPalette(): Boolean = false

    /** Quick controls can remain visible when their related palette is available elsewhere. */
    protected open fun showBelowFilamentPaletteContent(): Boolean = showFilamentPalette()

    override fun onCreateView(ctx: Context): View {
        refreshProfileDropdownTitle()
        if (getProfileListType() != -1 && ctx is ViewModelStoreOwner) {
            profileSettingsViewModel = ViewModelProvider(ctx).get(ProfileSettingsViewModel::class.java)
            val activeConfig = getCurrentConfig()
            profileSettingsViewModel?.bindActiveProfile(getProfileListType(), activeConfig?.title)
        }
        if (useTabs() && !useAccordionCategories() && profileSettingsViewModel != null) {
            categoryTabsController = ProfileSettingsCategoryTabsController(
                getProfileListType(),
                profileSettingsViewModel,
                IntConsumer { index -> selectTab(index) },
            )
        }

        if (showFilamentPalette()) {
            filamentPaletteController = FilamentPaletteController()
        }
        profileFilterController = ProfileSettingsFilterController(
            getProfileListType(),
            useTabs(),
            profileSettingsViewModel,
            Runnable { if (!useAccordionCategories()) resetSelectedTab() },
            Runnable { applySettingsFilter() },
        )
        if (useTabs() && !useAccordionCategories()) {
            if (categoryTabsController == null) {
                categoryTabsController = ProfileSettingsCategoryTabsController(
                    getProfileListType(),
                    profileSettingsViewModel,
                    IntConsumer { index -> selectTab(index) },
                )
            }
        }

        val profileList = ProfileSettingsComposeList(
            unfolded,
            ProfileCategoryRowsUpdater { categoryIndex, insertionIndex, expanded ->
                val rows = categoryElements.get(categoryIndex)
                if (rows != null) {
                    if (expanded) {
                        currentList.addAll(insertionIndex, rows)
                    } else {
                        currentList.removeAll(rows)
                    }
                    recyclerView?.submit(currentList)
                }
            },
            onTabletCategorySelected = { selectedCategory ->
                val viewModel = profileSettingsViewModel
                val profileType = getProfileListType()
                if (viewModel != null && profileType != -1) {
                    val profileTitle = getCurrentConfig()?.title
                    unfilteredConfigItems
                        .asSequence()
                        .filter { it.title != null && it.specialType == -1 }
                        .mapNotNull { it.title }
                        .distinct()
                        .forEach { category ->
                            viewModel.setCategoryExpanded(
                                profileType,
                                profileTitle,
                                category,
                                category == selectedCategory,
                            )
                        }
                    applySettingsFilter()
                    true
                } else {
                    false
                }
            },
        )
        recyclerView = profileList
        unfilteredConfigItems = getConfigItems()
        renderedProfileTitle = getCurrentConfig()?.title
        renderedConfigUid = SliceBeam.CONFIG_UID
        applySettingsFilter()
        val profileType = getProfileListType()
        profileFooter = ProfileSettingsFooter.create(
            Supplier { getCurrentConfig()?.title.orEmpty() },
            Supplier {
                val profile = getCurrentConfig()
                if (profileType == -1 || profile == null) ""
                else ProfileSettingsPendingSummary.build(profileType, profile)
            },
            Consumer { title ->
                if (TemporaryProjectSettings.owns(profileType)) return@Consumer
                val normalizedTitle = title.trim()
                val currentTitle = getCurrentConfig()?.title
                if (!ProfileNameValidator.isValid(normalizedTitle)) {
                    getContext()?.let { Toast.makeText(it, R.string.SettingsProfileNameInvalid, Toast.LENGTH_LONG).show() }
                    return@Consumer
                }
                if (ProfileNameValidator.conflictsWithExisting(
                        normalizedTitle,
                        getItems(false).map { it.title },
                        currentTitle,
                    )
                ) {
                    getContext()?.let { Toast.makeText(it, R.string.SettingsProfileNameDuplicate, Toast.LENGTH_LONG).show() }
                    return@Consumer
                }
                getCurrentConfig()?.let { profile ->
                    if (profileType != -1) ProfileSettingsRepository.applyPendingFor(profileType, profile)
                }
                if (!onApplyConfig(normalizedTitle)) {
                    refreshPendingActions()
                    onUpdateConfigItems()
                    return@Consumer
                }
                if (profileType != -1) ProfileSettingsRepository.clearDraft(profileType)
                refreshWorkspaceProfileStatus()
                refreshPendingActions()
                onUpdateConfigItems()
            },
            Runnable {
                onResetConfig()
                if (profileType != -1) ProfileSettingsRepository.clearDraft(profileType)
                refreshPendingActions()
                onUpdateConfigItems()
            },
        )
        refreshPendingActions()

        return ComposeView(ctx).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setContent {
                val currentThemeRevision = themeRevision
                BeamComposeTheme(themeRevision = currentThemeRevision, section = sectionForProfileType()) {
                    val project by TemporaryProjectSettings.uiState.collectAsState()
                    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                    val isPortrait = AdaptiveNavigationPolicy.isPortraitWindow(
                        configuration.screenWidthDp,
                        configuration.screenHeightDp,
                        configuration.orientation == Configuration.ORIENTATION_PORTRAIT,
                    )
                    val printProfile = getProfileListType() == ConfigObject.PROFILE_LIST_PRINT
                    val selectedDropdownTitle = if (printProfile) {
                        // Process names often repeat the active printer/nozzle suffix. The
                        // printer is already selected in the workspace header, so keep this
                        // picker focused on the actual process preset.
                        dropdownTitle?.substringBefore(" @")?.trim()?.ifBlank { dropdownTitle }
                    } else dropdownTitle
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            // Keep the profile picker, contextual controls and settings rows
                            // in one LazyColumn. This lets the entire page scroll instead of
                            // pinning a tall header over a short inner list.
                            key(getProfileListType(), selectedDropdownTitle, project.generation, project.selectedFilament) {
                                profileList.Content(headerContent = {
                                    Column {
                                        if (TemporaryProjectSettings.active && getProfileListType() >= 0) {
                                            TemporaryProjectSettingsBar(showFilamentSelector = getProfileListType() == ConfigObject.PROFILE_LIST_FILAMENT)
                                        }
                                        if (!TemporaryProjectSettings.active && getProfileListType() == ConfigObject.PROFILE_LIST_FILAMENT) {
                                            ru.ytkab0bp.slicebeam.ui.settings.OwnFilamentSlotBar(dropdownTitle) { slot ->
                                                (this@ProfileListFragment as? FilamentConfigFragment)?.selectOwnFilamentSlot(slot)
                                            }
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(
                                                start = 12.dp,
                                                top = if (isPortrait) 0.dp else 12.dp,
                                                end = 12.dp,
                                                bottom = 8.dp,
                                            ),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Box(modifier = Modifier.weight(1f)) {
                                                ProfileDropdownContent(
                                                    title = if (TemporaryProjectSettings.owns(getProfileListType()))
                                                        ctx.getString(R.string.ProjectSettingsProfileTitle)
                                                    else selectedDropdownTitle ?: if (
                                                        getProfileListType() == ConfigObject.PROFILE_LIST_PRINTER &&
                                                        SliceBeam.requireConfig().printerConfigs.isEmpty()
                                                    ) ctx.getString(R.string.PrinterProfileMissing) else null,
                                                    modifier = Modifier.fillMaxWidth(if (printProfile) 0.5f else 1f),
                                                    menu = dropdownMenu,
                                                    menuTitle = dropdownMenuTitle,
                                                    expanded = dropdownExpanded,
                                                    canManage = !TemporaryProjectSettings.owns(getProfileListType()),
                                                    onOpen = { openProfileMenu() },
                                                    onDismiss = { dropdownExpanded = false },
                                                    onSelect = ::selectProfileFromMenu,
                                                    onDelete = {
                                                        dropdownExpanded = false
                                                        if (TemporaryProjectSettings.owns(getProfileListType())) return@ProfileDropdownContent
                                                        deleteCurrentProfile()
                                                        refreshWorkspaceProfileStatus()
                                                        onUpdateConfigItems()
                                                    },
                                                    onClone = {
                                                        dropdownExpanded = false
                                                        if (TemporaryProjectSettings.owns(getProfileListType())) return@ProfileDropdownContent
                                                        cloneCurrentProfile()
                                                        refreshWorkspaceProfileStatus()
                                                        onUpdateConfigItems()
                                                    },
                                                    leadingIconRes = if (getProfileListType() == ConfigObject.PROFILE_LIST_PRINTER) {
                                                        PrinterProfileCategoryAssets.stateIcon(
                                                            R.drawable.printerprofile_druckerauswahl,
                                                            active = dropdownExpanded,
                                                        )
                                                    } else null,
                                                )
                                            }
                                            profileFilterController?.ToggleContent()
                                        }
                                        if (profileSettingsViewModel != null) {
                                            ComposeProfileSettingsStatus(profileSettingsViewModel!!)
                                        }
                                        ProfileHeaderContent(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                                        )
                                        if (showFilamentPalette() && !isPortrait) {
                                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                filamentPaletteController?.Content(
                                                    modifier = Modifier.weight(1f).heightIn(min = 64.dp).padding(bottom = 2.dp),
                                                )
                                                if (showBelowFilamentPaletteContent()) {
                                                    BelowFilamentPaletteContent(
                                                        modifier = Modifier.width(290.dp).padding(end = 12.dp, bottom = 4.dp),
                                                    )
                                                }
                                            }
                                        } else {
                                            if (showFilamentPalette()) {
                                                filamentPaletteController?.Content(
                                                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(bottom = 2.dp),
                                                )
                                            }
                                            if (showBelowFilamentPaletteContent()) {
                                                BelowFilamentPaletteContent(
                                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
                                                )
                                            }
                                        }
                                        if (useTabs()) {
                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                profileFilterController?.Content(showToggle = false)
                                                if (!useAccordionCategories()) categoryTabsController?.Content()
                                            }
                                        }
                                    }
                                })
                            }
                        }
                        profileFooter?.Content(isPortrait)
                        activeOptionHelpDialog?.let { dialog ->
                            ProfileOptionHelpDialog(dialog) { activeOptionHelpDialog = null }
                        }
                    }
                }
            }
        }
    }

    private fun sectionForProfileType(): OrkanSection = when (getProfileListType()) {
        ConfigObject.PROFILE_LIST_PRINT -> OrkanSection.PRINT
        ConfigObject.PROFILE_LIST_FILAMENT -> OrkanSection.FILAMENT
        ConfigObject.PROFILE_LIST_PRINTER -> OrkanSection.PRINTER
        else -> OrkanSection.DEFAULT
    }

    override fun onCreate() {
        super.onCreate()
        SliceBeam.EVENT_BUS.registerListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        activeOptionHelpDialog = null
        SliceBeam.EVENT_BUS.unregisterListener(this)
        unfolded.clear()
    }

    @EventHandler(runOnMainThread = true)
    fun onTemporaryProjectChanged(event: TemporaryProjectSettingsChangedEvent) {
        if (getView() == null || getProfileListType() == -1) return
        if (!TemporaryProjectSettings.active) {
            onResetConfig()
            refreshProfileDropdownTitle()
        }
        filamentPaletteController?.reload()
        refreshPendingActions()
        onUpdateConfigItems()
    }

    protected fun setConfigItems(items: List<ProfileOptionElement>) {
        val profileType = getProfileListType()
        val accordionCategories = useAccordionCategories()
        val groupToggle = BiConsumer<String, Boolean> { group, expanded ->
            collapsedGroups[group] = expanded
            if (profileSettingsViewModel != null && profileType != -1) {
                profileSettingsViewModel!!.setCollapsedGroup(profileType, group, expanded)
            }
        }
        val createWrapper = java.util.function.Function<ProfileOptionElement, ProfileOptionWrapper> {
            element -> ProfileOptionWrapper.fromOption(element)
        }
        if (accordionCategories) {
            val profileTitle = getCurrentConfig()?.title
            currentList = ArrayList(
                ProfileSettingsListBuilder.buildAccordion(
                    items = items,
                    profileType = profileType,
                    collapsedGroups = collapsedGroups,
                    viewModel = profileSettingsViewModel,
                    categoryExpanded = java.util.function.Function { category ->
                        profileSettingsViewModel?.categoryExpanded(profileType, profileTitle, category) ?: false
                    },
                    onCategoryExpansionChanged = BiConsumer { category, expanded ->
                        profileSettingsViewModel?.setCategoryExpanded(profileType, profileTitle, category, expanded)
                    },
                    createWrapper = createWrapper,
                    onGroupToggle = groupToggle,
                    onRebuild = Runnable { setConfigItems(items) },
                ),
            )
            categoryElements = SparseArray()
        } else {
            val state = ProfileSettingsListBuilder.build(
                items,
                useTabs(),
                categoryTabsController?.selectedIndex ?: 0,
                profileType,
                collapsedGroups,
                profileSettingsViewModel,
                createWrapper,
                groupToggle,
                Runnable { setConfigItems(items) },
            )
            categoryElements = state.categories
            currentList = ArrayList(state.visibleItems)
            if (useTabs() && categoryTabsController != null) {
                categoryTabsController!!.updateHeadings(
                    state.headings.map { it.title.orEmpty() },
                    state.selectedTab,
                )
            }
        }
        recyclerView?.submit(currentList)
        updateEmptyState()
    }

    private fun applySettingsFilter() {
        val controller = profileFilterController ?: return
        setConfigItems(controller.filter(unfilteredConfigItems, accordionCategories = useAccordionCategories()))
    }

    private fun selectTab(index: Int) {
        val controller = categoryTabsController ?: return
        if (!controller.select(index)) return
        currentList = ArrayList(categoryElements.get(index) ?: emptyList())
        recyclerView?.submit(currentList)
        updateEmptyState()
        recyclerView?.scrollToTop()
    }

    private fun updateEmptyState() {
        val message = if (currentList.isEmpty() && useTabs()) {
            profileFilterController?.emptyStateMessage(getContext() ?: return)
        } else null
        recyclerView?.setEmptyMessage(message)
    }

    private fun resetSelectedTab() {
        categoryTabsController?.resetSelection()
    }

    private fun refreshProfileDropdownTitle() {
        dropdownTitle = ProfileListSelection.selected(getItems(false))?.title
    }

    protected fun setProfileDropdownTitle(title: String?) {
        dropdownTitle = title
    }

    private fun openProfileMenu() {
        dropdownItemsSnapshot = getItems(true)
        dropdownMenu = ProfileDropdownMenuPolicy.create(
            dropdownItemsSnapshot.map { it.title },
            dropdownItemsSnapshot.indexOfFirst { it.isSelected() },
        )
        dropdownMenuTitle = title
        dropdownExpanded = true
    }

    private fun selectProfileFromMenu(index: Int) {
        dropdownExpanded = false
        val safeIndex = ProfileDropdownMenuPolicy.selectedIndex(dropdownMenu, index) ?: return
        val selected = dropdownItemsSnapshot.getOrNull(safeIndex) ?: return
        if (selectItem(selected)) {
            dropdownTitle = getCurrentConfig()?.title ?: selected.title
            refreshPendingActions()
            onUpdateConfigItems()
            refreshWorkspaceProfileStatus()
        } else {
            refreshProfileDropdownTitle()
        }
    }

    override fun onResume() {
        super.onResume()
        if (recyclerView == null) return

        val activeTitle = selectedProfileTitle()
        val currentTitle = getCurrentConfig()?.title
        val profileChanged = ProfileListRefreshPolicy.profileChanged(currentTitle, activeTitle)
        if (ProfileListRefreshPolicy.needsReload(
                renderedProfileTitle,
                renderedConfigUid,
                activeTitle,
                SliceBeam.CONFIG_UID,
            )
        ) {
            // Another screen may have changed the active printer, process, or filament while this
            // fragment was stopped. Rebind its source object as well as its dynamic option rows.
            onResetConfig()
            refreshProfileConfigItems(resetSelectedCategory = profileChanged)
            refreshProfileDropdownTitle()
        } else {
            refreshProfileDropdownTitle()
        }
    }

    override fun onApplyTheme() {
        super.onApplyTheme()
        themeRevision++
        recyclerView?.refreshTheme()
    }

    private fun refreshPendingActions() {
        profileFooter?.refreshPending(getProfileListType())
    }

    /** Stages a profile edit through the same Orca-aware repository path as Kotlin option rows. */
    protected open fun updateConfigField(definition: ConfigOptionDef?, index: Int, value: String) {
        val option = definition ?: return
        val profile = getCurrentConfig() ?: return
        ProfileConfigValueController.stageValue(
            getProfileListType(), profile, option.key, option.guiType, index, value, option.defaultValue,
        )
        refreshPendingActions()
    }

    protected fun onUpdateConfigItems() {
        refreshProfileConfigItems(
            resetSelectedCategory = ProfileListRefreshPolicy.profileChanged(
                renderedProfileTitle,
                getCurrentConfig()?.title,
            ),
        )
    }

    private fun selectedProfileTitle(): String? {
        val presetKey = when (getProfileListType()) {
            ConfigObject.PROFILE_LIST_PRINTER -> "printer"
            ConfigObject.PROFILE_LIST_PRINT -> "print"
            ConfigObject.PROFILE_LIST_FILAMENT -> "filament"
            else -> return null
        }
        return SliceBeam.CONFIG?.presets?.get(presetKey)
    }

    private fun refreshProfileConfigItems(resetSelectedCategory: Boolean) {
        val profileTitle = getCurrentConfig()?.title
        if (resetSelectedCategory) resetSelectedTab()
        profileSettingsViewModel?.let { viewModel ->
            viewModel.bindActiveProfile(getProfileListType(), profileTitle)
        }
        unfilteredConfigItems = getConfigItems()
        renderedProfileTitle = profileTitle
        renderedConfigUid = SliceBeam.CONFIG_UID
        applySettingsFilter()
    }

    /** Persist a user-requested profile operation and show a useful message if storage fails. */
    protected fun saveConfigOrNotify(): Boolean {
        val saved = SliceBeam.saveConfig()
        if (!saved) {
            getContext()?.let { Toast.makeText(it, R.string.ConfigSaveFailed, Toast.LENGTH_LONG).show() }
        }
        return saved
    }

    /** Keep the workspace's visible printer/profile summary in sync with shared profile changes. */
    private fun refreshWorkspaceProfileStatus() {
        val activity = getContext() as? MainActivity ?: return
        val workspace = activity.navigationDelegate.getCurrentFragment() as? BedFragment ?: return
        workspace.refreshProfileStatus()
    }

    protected abstract fun cloneCurrentProfile()
    protected abstract fun deleteCurrentProfile()
    protected abstract fun onApplyConfig(title: String): Boolean
    protected abstract fun onResetConfig()
    protected abstract fun getCurrentConfig(): ConfigObject?
    protected abstract val title: Int
    protected abstract fun selectItem(item: ProfileListItem): Boolean
    protected abstract fun getItems(filter: Boolean): List<ProfileListItem>
    protected abstract fun getConfigItems(): List<ProfileOptionElement>

    fun createOptionElement(definition: ConfigOptionDef): ProfileOptionElement =
        createOptionElement(definition, -1)

    fun createOptionElement(
        definition: ConfigOptionDef,
        elementIndex: Int,
        onValueChanged: Runnable? = null,
    ): ProfileOptionElement {
        val profileType = getProfileListType()
        return ProfileOptionElement(
            getContext(),
            definition,
            elementIndex,
            Supplier {
                val profile = getCurrentConfig()
                if (profile == null) definition.defaultValue.orEmpty()
                else ProfileConfigValueController.valueFor(
                    profileType,
                    profile,
                    definition.key,
                    definition.guiType,
                    definition.defaultValue,
                    elementIndex,
                    false,
                ).orEmpty()
            },
            Consumer { value ->
                getCurrentConfig()?.let { profile ->
                    ProfileConfigValueController.stageValue(
                        profileType,
                        profile,
                        definition.key,
                        definition.guiType,
                        elementIndex,
                        value,
                        definition.defaultValue,
                    )
                    refreshPendingActions()
                    onValueChanged?.run()
                }
            },
            IntConsumer { position ->
                if (position >= 0) recyclerView?.refreshItem(position)
            },
            Runnable {
                val controller = profileFilterController
                if (controller != null) {
                    controller.showOptionHelp(definition) { dialog ->
                        activeOptionHelpDialog = dialog
                    }
                }
            },
        )
    }
}
