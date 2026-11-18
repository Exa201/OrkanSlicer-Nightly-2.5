package ru.ytkab0bp.slicebeam.fragment

import android.content.Intent
import android.app.Activity
import android.net.Uri
import android.text.TextUtils
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.SliceBeam
import ru.ytkab0bp.slicebeam.components.PrinterConnectionCard
import ru.ytkab0bp.slicebeam.config.ConfigObject
import ru.ytkab0bp.slicebeam.config.BedSurfaceTemperaturePolicy
import ru.ytkab0bp.slicebeam.config.ProfileSettingsRepository
import ru.ytkab0bp.slicebeam.utils.PrinterWebOriginPolicy

/** Printer-profile screen; the Orca settings remain defined by the shared native schema. */
class PrinterConfigFragment : ProfileListFragment() {
    private var currentConfig: ConfigObject? = null

    override fun getProfileListType(): Int = ConfigObject.PROFILE_LIST_PRINTER

    override fun useTabs(): Boolean = true

    override fun useAccordionCategories(): Boolean = true

    override fun onCreate() {
        super.onCreate()
        onResetConfig()
    }

    override fun getItems(filter: Boolean): List<ProfileListItem> =
        SliceBeam.requireConfig().printerConfigs.map { it as ProfileListItem }

    override fun getConfigItems(): List<ProfileOptionElement> {
        return OrcaPrinterSettingsBuilder.build(this)
    }

    @Composable
    override fun ProfileHeaderContent(modifier: Modifier) {
        if (SliceBeam.requireConfig().printerConfigs.isEmpty()) {
            Surface(
                modifier = modifier,
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = getContext()?.getString(R.string.PrinterProfileMissingHint).orEmpty(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                    Button(onClick = ::openPrinterSetup) {
                        Text(getContext()?.getString(R.string.SettingsAddPrinter).orEmpty())
                    }
                }
            }
        } else {
            PrinterConnectionCard(
                modifier = modifier,
                configuredHost = currentConfig?.get("print_host"),
                onOpenWeb = ::openPrinterWebUi,
                iconRes = R.drawable.printerprofile_druckerverbindung,
            )
        }
    }

    fun getConfiguredExtruderCount(): Int = currentConfig?.extruderCount ?: 0

    private fun openPrinterWebUi() {
        val selected = currentConfig?.let { ConfigObject(it) } ?: return
        ProfileSettingsRepository.applyPendingFor(getProfileListType(), selected)
        val host = PrinterWebOriginPolicy.dashboardUrl(selected.get("print_host"))
        val context = getContext() ?: return
        if (TextUtils.isEmpty(host)) {
            Toast.makeText(context, R.string.PrinterWebHostMissing, Toast.LENGTH_LONG).show()
            return
        }
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(host)))
        } catch (_: Exception) {
            Toast.makeText(context, R.string.PrinterWebOpenFailed, Toast.LENGTH_LONG).show()
        }
    }

    private fun openPrinterSetup() {
        val activity = getContext() as? Activity ?: return
        activity.startActivity(
            Intent(activity, ru.ytkab0bp.slicebeam.SetupActivity::class.java)
                .putExtra(ru.ytkab0bp.slicebeam.SetupActivity.EXTRA_ADD_PRINTER, true),
        )
    }

    override fun cloneCurrentProfile() {
        val original = currentConfig ?: return
        val copy = ConfigObject(
            SliceBeam.requireInstance().getString(R.string.SettingsProfileCopy, original.title),
        )
        copy.profileListType = ConfigObject.PROFILE_LIST_PRINTER
        copy.values.putAll(original.values)
        val config = SliceBeam.requireConfig()
        val presets = config.presets ?: return
        val previousPrinter = presets["printer"]
        config.printerConfigs.add(copy)
        presets.put("printer", copy.title)
        if (!saveConfigOrNotify()) {
            config.printerConfigs.remove(copy)
            if (previousPrinter == null) presets.remove("printer") else presets.put("printer", previousPrinter)
            return
        }

        currentConfig = ConfigObject(copy)
        setProfileDropdownTitle(getCurrentConfig()?.title)
    }

    override fun deleteCurrentProfile() {
        val current = currentConfig ?: return
        val profiles = SliceBeam.requireConfig().printerConfigs
        if (profiles.size <= 1) {
            getContext()?.let { Toast.makeText(it, R.string.ProfileDeleteLastBlocked, Toast.LENGTH_LONG).show() }
            return
        }
        val config = SliceBeam.requireConfig()
        val stored = config.findPrinter(current.title) ?: return
        val removedIndex = profiles.indexOf(stored)
        if (removedIndex < 0) return
        profiles.removeAt(removedIndex)
        val firstProfile = getItems(false).firstOrNull() ?: run {
            profiles.add(removedIndex, stored)
            return
        }
        if (!selectItem(firstProfile)) {
            profiles.add(removedIndex, stored)
            currentConfig = ConfigObject(stored)
            setProfileDropdownTitle(stored.title)
            return
        }
        setProfileDropdownTitle(getCurrentConfig()?.title)
    }

    override fun onApplyConfig(title: String): Boolean {
        val current = currentConfig ?: return false
        val config = SliceBeam.requireConfig()
        val stored = config.findPrinter(current.title) ?: return false
        val previousStored = ConfigObject(stored)
        val presets = config.presets ?: return false
        val previousPrinter = presets["printer"]
        stored.setTitle(title)
        stored.values.clear()
        stored.values.putAll(current.values)
        presets.put("printer", title)
        if (!saveConfigOrNotify()) {
            stored.setTitle(previousStored.title)
            stored.values.clear()
            stored.values.putAll(previousStored.values)
            if (previousPrinter == null) presets.remove("printer") else presets.put("printer", previousPrinter)
            return false
        }
        current.setTitle(title)
        setProfileDropdownTitle(title)
        return true
    }

    override fun onResetConfig() {
        val printer = SliceBeam.requireConfig().findPrinter(SliceBeam.requireConfig().presets!!.get("printer"))
            ?: SliceBeam.requireConfig().printerConfigs.firstOrNull()
            ?: ConfigObject.createCustomPrinterProfile()
        currentConfig = ConfigObject(printer)
        if (currentConfig?.get("curr_bed_type").isNullOrBlank()) {
            currentConfig?.put(
                "curr_bed_type",
                BedSurfaceTemperaturePolicy.printerDefaultValue(printer.get("default_bed_type")) ?: "High Temp Plate",
            )
        }
    }

    override fun getCurrentConfig(): ConfigObject? = currentConfig

    override val title: Int = R.string.SlotPrinterConfigTooltip

    override fun selectItem(item: ProfileListItem): Boolean {
        if (!SliceBeam.applyProfileSelection(printer = item.title)) {
            getContext()?.let { Toast.makeText(it, R.string.ConfigSaveFailed, Toast.LENGTH_LONG).show() }
            return false
        }
        currentConfig = ConfigObject(item as ConfigObject)
        return true
    }
}
