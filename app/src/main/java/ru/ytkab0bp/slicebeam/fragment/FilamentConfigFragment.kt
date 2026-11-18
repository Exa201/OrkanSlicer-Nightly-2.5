package ru.ytkab0bp.slicebeam.fragment

import android.widget.Toast
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.SliceBeam
import ru.ytkab0bp.slicebeam.config.ConfigObject
import ru.ytkab0bp.slicebeam.config.SlotFilamentProfiles
import ru.ytkab0bp.slicebeam.config.TemporaryProjectSettings
import ru.ytkab0bp.slicebeam.config.ProfileSettingsRepository
import ru.ytkab0bp.slicebeam.config.ProfileCompatibility
import ru.ytkab0bp.slicebeam.utils.Prefs

/** Filament profile screen backed by Orca's native settings definitions and compatibility checks. */
class FilamentConfigFragment : ProfileListFragment() {
    private var compatItems: List<ProfileListItem>? = null
    private var compatibilityCacheKey: ProfileCompatibilityCacheKey? = null
    private var currentConfig: ConfigObject? = null

    fun selectOwnFilamentSlot(index: Int) {
        if (TemporaryProjectSettings.active) return
        if (ProfileSettingsRepository.pendingCountFor(ConfigObject.PROFILE_LIST_FILAMENT) > 0 &&
            !SliceBeam.savePendingProfileSettings()) {
            getContext()?.let { Toast.makeText(it, R.string.ConfigSaveFailed, Toast.LENGTH_LONG).show() }
            return
        }
        val config = SliceBeam.requireConfig()
        val currentTitle = config.presets?.get("filament")
        if (!currentTitle.isNullOrBlank()) {
            for (slot in Prefs.getStoredFilamentSlots().indices) {
                if (SlotFilamentProfiles.titleFor(slot, null) == null) SlotFilamentProfiles.assign(slot, currentTitle)
            }
        }
        SlotFilamentProfiles.select(index)
        val target = SlotFilamentProfiles.titleFor(index, currentTitle)
        if (!target.isNullOrBlank() && target != currentTitle && config.findFilament(target) != null) {
            if (!SliceBeam.applyProfileSelection(filament = target)) return
        }
        onResetConfig()
        setProfileDropdownTitle(getCurrentConfig()?.title)
        onUpdateConfigItems()
    }

    override fun getProfileListType(): Int = ConfigObject.PROFILE_LIST_FILAMENT

    override fun useTabs(): Boolean = true

    override fun useAccordionCategories(): Boolean = true

    override fun onCreate() {
        super.onCreate()
        onResetConfig()
    }

    override fun getItems(filter: Boolean): List<ProfileListItem> {
        val profiles = SliceBeam.requireConfig().filamentConfigs
        if (!filter) return profiles.map { it as ProfileListItem }

        val config = SliceBeam.requireConfig()
        val cacheKey = ProfileCompatibilityCacheKey(
            printer = config.presets?.get("printer"),
            process = config.presets?.get("print"),
            filament = config.presets?.get("filament"),
            configUid = SliceBeam.CONFIG_UID,
        )
        if (compatibilityCacheKey == cacheKey && compatItems != null) {
            return compatItems.orEmpty()
        }
        val compatible = ProfileCompatibility.filaments(config)
        compatibilityCacheKey = cacheKey
        compatItems = compatible
        return compatible.map { it as ProfileListItem }
    }

    override fun getConfigItems(): List<ProfileOptionElement> =
        OrcaFilamentSettingsBuilder.build(this)

    override fun cloneCurrentProfile() {
        val current = currentConfig ?: return
        val copy = ConfigObject(
            SliceBeam.requireInstance().getString(R.string.SettingsProfileCopy, current.title),
        )
        copy.profileListType = ConfigObject.PROFILE_LIST_FILAMENT
        copy.values.putAll(current.values)
        val config = SliceBeam.requireConfig()
        val presets = config.presets ?: return
        val previousFilament = presets["filament"]
        config.filamentConfigs.add(copy)
        presets.put("filament", copy.title)
        if (!saveConfigOrNotify()) {
            config.filamentConfigs.remove(copy)
            if (previousFilament == null) presets.remove("filament") else presets.put("filament", previousFilament)
            return
        }

        currentConfig = ConfigObject(copy)
        if (!TemporaryProjectSettings.active) SlotFilamentProfiles.assign(SlotFilamentProfiles.selectedSlot, copy.title.orEmpty())
        setProfileDropdownTitle(getCurrentConfig()?.title)
        compatItems = null
    }

    override fun deleteCurrentProfile() {
        val current = currentConfig ?: return
        compatItems = null
        val profiles = SliceBeam.requireConfig().filamentConfigs
        if (profiles.size <= 1) {
            getContext()?.let { Toast.makeText(it, R.string.ProfileDeleteLastBlocked, Toast.LENGTH_LONG).show() }
            return
        }
        val config = SliceBeam.requireConfig()
        val stored = config.findFilament(current.title) ?: return
        val removedIndex = profiles.indexOf(stored)
        if (removedIndex < 0) return
        profiles.removeAt(removedIndex)
        val firstProfile = getItems(true).firstOrNull() ?: run {
            profiles.add(removedIndex, stored)
            getContext()?.let { Toast.makeText(it, R.string.ProfileDeleteLastBlocked, Toast.LENGTH_LONG).show() }
            return
        }
        if (!selectItem(firstProfile)) {
            profiles.add(removedIndex, stored)
            currentConfig = ConfigObject(stored)
            setProfileDropdownTitle(stored.title)
            compatItems = null
            return
        }
        if (!TemporaryProjectSettings.active) SlotFilamentProfiles.remove(stored.title)
        setProfileDropdownTitle(getCurrentConfig()?.title)
    }

    override fun onApplyConfig(title: String): Boolean {
        val current = currentConfig ?: return false
        compatItems = null
        val config = SliceBeam.requireConfig()
        val stored = config.findFilament(current.title) ?: return false
        val previousStored = ConfigObject(stored)
        val presets = config.presets ?: return false
        val previousFilament = presets["filament"]
        stored.setTitle(title)
        stored.values.clear()
        stored.values.putAll(current.values)
        presets.put("filament", title)
        if (!saveConfigOrNotify()) {
            stored.setTitle(previousStored.title)
            stored.values.clear()
            stored.values.putAll(previousStored.values)
            if (previousFilament == null) presets.remove("filament") else presets.put("filament", previousFilament)
            return false
        }
        current.setTitle(title)
        if (!TemporaryProjectSettings.active) SlotFilamentProfiles.rename(previousStored.title, title)
        setProfileDropdownTitle(title)
        return true
    }

    override fun onResetConfig() {
        val filament = SliceBeam.requireConfig().findFilament(SliceBeam.requireConfig().presets!!.get("filament"))
            ?: SliceBeam.requireConfig().filamentConfigs.firstOrNull()
            ?: ConfigObject.createCustomFilamentProfile()
        currentConfig = ConfigObject(filament)
    }

    override fun getCurrentConfig(): ConfigObject? = currentConfig

    override val title: Int = R.string.SlotFilamentConfigTooltip

    override fun selectItem(item: ProfileListItem): Boolean {
        if (!TemporaryProjectSettings.active &&
            ProfileSettingsRepository.pendingCountFor(ConfigObject.PROFILE_LIST_FILAMENT) > 0 &&
            !SliceBeam.savePendingProfileSettings()) {
            getContext()?.let { Toast.makeText(it, R.string.ConfigSaveFailed, Toast.LENGTH_LONG).show() }
            return false
        }
        if (!SliceBeam.applyProfileSelection(filament = item.title)) {
            getContext()?.let { Toast.makeText(it, R.string.ConfigSaveFailed, Toast.LENGTH_LONG).show() }
            return false
        }
        currentConfig = ConfigObject(item as ConfigObject)
        if (!TemporaryProjectSettings.active) SlotFilamentProfiles.assign(SlotFilamentProfiles.selectedSlot, item.title.orEmpty())
        compatItems = null
        return true
    }
}
