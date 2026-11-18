package ru.ytkab0bp.slicebeam

import android.app.Application
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.ytkab0bp.slicebeam.config.ConfigObject
import ru.ytkab0bp.slicebeam.config.ProfileCompatibility
import ru.ytkab0bp.slicebeam.config.ProfileSettingsRepository
import ru.ytkab0bp.slicebeam.config.PrinterScopedProcessProfile
import ru.ytkab0bp.slicebeam.fragment.PrinterConfigFragment
import ru.ytkab0bp.slicebeam.fragment.ProfileConfigValueController
import ru.ytkab0bp.slicebeam.fragment.PrintConfigFragment
import ru.ytkab0bp.slicebeam.fragment.FilamentConfigFragment
import ru.ytkab0bp.slicebeam.fragment.PrintConfigToolsLogic
import ru.ytkab0bp.slicebeam.navigation.MobileNavigationDelegate
import ru.ytkab0bp.slicebeam.slic3r.PrintConfigDef
import ru.ytkab0bp.slicebeam.slic3r.ConfigOptionDef
import ru.ytkab0bp.slicebeam.slic3r.Slic3rConfigWrapper
import ru.ytkab0bp.slicebeam.slic3r.Slic3rLocalization
import ru.ytkab0bp.slicebeam.config.SlotFilamentProfiles
import ru.ytkab0bp.slicebeam.fragment.BedFragment
import ru.ytkab0bp.slicebeam.utils.Prefs
import ru.ytkab0bp.slicebeam.utils.PrinterProfileSelectionStore
import ru.ytkab0bp.slicebeam.ui.settings.OrcaProfileGroupLocalizer

/** Checks that the active printer-compatible process preset is marked in the real dropdown model. */
@RunWith(AndroidJUnit4::class)
class PrintProfileDropdownIntegrationDeviceTest {
    @get:Rule
    val compose = createInitializedActivityRule()

    @Test
    fun filamentProfileCanBeClonedAndDeletedFromTheScrolledDropdown() {
        val activity = compose.activity
        val config = SliceBeam.requireConfig()
        val presets = requireNotNull(config.presets)
        val originalFilament = requireNotNull(presets["filament"])
        val originalPrinter = presets["printer"]
        val originalProcess = presets["print"]
        val originalCount = config.filamentConfigs.size
        val copyTitle = activity.getString(R.string.SettingsProfileCopy, originalFilament)
        assertNull(config.findFilament(copyTitle))
        val preferences = requireNotNull(Prefs.getPrefs())
        val previousSlot = SlotFilamentProfiles.selectedSlot
        val previousSlotProfiles = preferences.getString("filament_slot_profiles", null)
        val firstFilamentTitle = requireNotNull(ProfileCompatibility.filaments(config).first().title)

        try {
            activity.runOnUiThread {
                (activity.navigationDelegate as MobileNavigationDelegate).switchToSlot(3)
            }
            compose.waitUntil(timeoutMillis = 15_000) {
                activity.navigationDelegate.getCurrentFragment() is FilamentConfigFragment
            }

            compose.onNodeWithText(originalFilament).assertIsDisplayed().performClick()
            compose.onNodeWithText(activity.getString(R.string.SettingsCloneProfile))
                .performScrollTo().assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 5_000) {
                config.filamentConfigs.size == originalCount + 1
            }
            assertEquals(originalCount + 1, config.filamentConfigs.size)
            assertEquals(copyTitle, presets["filament"])
            assertEquals("Cloning preserves the complete filament profile values",
                config.findFilament(originalFilament)?.values,
                config.findFilament(copyTitle)?.values)
            assertEquals("Cloning assigns the active palette slot to the copy", copyTitle,
                SlotFilamentProfiles.titleFor(SlotFilamentProfiles.selectedSlot, null))

            compose.onNodeWithText(copyTitle).assertIsDisplayed().performClick()
            compose.onNodeWithText(activity.getString(R.string.SettingsDeleteProfile))
                .performScrollTo().assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                config.findFilament(copyTitle) == null && config.filamentConfigs.size == originalCount
            }
            assertEquals("Deleting the clone selects a remaining filament profile", firstFilamentTitle,
                presets["filament"])
            assertEquals("The active palette slot follows the fallback profile after deletion",
                firstFilamentTitle,
                SlotFilamentProfiles.titleFor(SlotFilamentProfiles.selectedSlot, null))
        } finally {
            config.findFilament(copyTitle)?.let(config.filamentConfigs::remove)
            if (originalPrinter == null) presets.remove("printer") else presets.put("printer", originalPrinter)
            if (originalProcess == null) presets.remove("print") else presets.put("print", originalProcess)
            presets.put("filament", originalFilament)
            if (previousSlotProfiles == null) {
                preferences.edit().remove("filament_slot_profiles").commit()
            } else {
                preferences.edit().putString("filament_slot_profiles", previousSlotProfiles).commit()
            }
            SlotFilamentProfiles.select(previousSlot)
            ProfileSettingsRepository.clearDraft(ConfigObject.PROFILE_LIST_FILAMENT)
            check(SliceBeam.saveConfig()) { "Could not restore the isolated filament profile fixture" }
        }
    }

    @Test
    fun processProfileCanBeClonedAndDeletedFromTheScrolledDropdown() {
        val activity = compose.activity
        val config = SliceBeam.requireConfig()
        val presets = requireNotNull(config.presets)
        val originalPrinter = presets["printer"]
        val originalProcess = requireNotNull(presets["print"])
        val originalFilament = presets["filament"]
        val originalTitles = config.printConfigs.mapNotNull { it.title }.toSet()
        var copyTitle: String? = null
        val printerSelections = config.printerConfigs.mapNotNull { profile ->
            profile.title?.let { it to PrinterProfileSelectionStore.read(it) }
        }.toMap()

        try {
            activity.runOnUiThread {
                (activity.navigationDelegate as MobileNavigationDelegate).switchToSlot(2)
            }
            compose.waitUntil(timeoutMillis = 15_000) {
                activity.navigationDelegate.getCurrentFragment() is PrintConfigFragment
            }

            val activeProcess = requireNotNull(presets["print"])
            val clonedTitle = activity.getString(R.string.SettingsProfileCopy, activeProcess)
            copyTitle = clonedTitle
            assertNull(config.findPrint(clonedTitle))
            compose.onNodeWithText(activeProcess).assertIsDisplayed().performClick()
            compose.onNodeWithText(activity.getString(R.string.SettingsCloneProfile))
                .performScrollTo().assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 5_000) { config.findPrint(clonedTitle) != null }
            assertEquals(clonedTitle, presets["print"])
            assertEquals("Cloning preserves all process profile values",
                config.findPrint(activeProcess)?.values,
                config.findPrint(clonedTitle)?.values)

            compose.onNodeWithText(clonedTitle).assertIsDisplayed().performClick()
            compose.onNodeWithText(activity.getString(R.string.SettingsDeleteProfile))
                .performScrollTo().assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                config.findPrint(clonedTitle) == null
            }
            val selectedAfterDelete = presets["print"]
            assertTrue("Deleting the clone selects a remaining process preset",
                !selectedAfterDelete.isNullOrBlank() && selectedAfterDelete != clonedTitle)
            assertNotNull("The selected process preset remains available", config.findPrint(selectedAfterDelete))
        } finally {
            val clonedTitle = copyTitle
            config.printConfigs.removeAll { it.title !in originalTitles || it.title == clonedTitle }
            if (originalPrinter == null) presets.remove("printer") else presets.put("printer", originalPrinter)
            presets.put("print", originalProcess)
            if (originalFilament == null) presets.remove("filament") else presets.put("filament", originalFilament)
            printerSelections.forEach { (title, selection) ->
                if (selection == null) PrinterProfileSelectionStore.write(title, null, null)
                else PrinterProfileSelectionStore.write(title, selection.process, selection.filament)
            }
            ProfileSettingsRepository.clearDraft(ConfigObject.PROFILE_LIST_PRINT)
            check(SliceBeam.saveConfig()) { "Could not restore the isolated process profile fixture" }
        }
    }

    @Test
    fun printerProfileCanBeClonedAndDeletedFromTheScrolledDropdown() {
        val activity = compose.activity
        val config = SliceBeam.requireConfig()
        val presets = requireNotNull(config.presets)
        val originalPrinter = requireNotNull(presets["printer"])
        val originalProcess = presets["print"]
        val originalFilament = presets["filament"]
        val originalCount = config.printerConfigs.size
        val copyTitle = activity.getString(R.string.SettingsProfileCopy, originalPrinter)
        assertNull(config.findPrinter(copyTitle))
        val firstPrinterTitle = requireNotNull(config.printerConfigs.first().title)
        val printerSelections = config.printerConfigs.mapNotNull { profile ->
            profile.title?.let { it to PrinterProfileSelectionStore.read(it) }
        }.toMap()

        try {
            activity.runOnUiThread {
                (activity.navigationDelegate as MobileNavigationDelegate).switchToSlot(4)
            }
            compose.waitUntil(timeoutMillis = 15_000) {
                activity.navigationDelegate.getCurrentFragment() is PrinterConfigFragment
            }
            compose.onAllNodes(
                hasText(originalPrinter) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button),
            ).onFirst().assertIsDisplayed().performClick()
            compose.onNodeWithText(activity.getString(R.string.SettingsCloneProfile))
                .performScrollTo().assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 5_000) { config.printerConfigs.size == originalCount + 1 }
            assertEquals(originalCount + 1, config.printerConfigs.size)
            assertEquals(copyTitle, config.presets?.get("printer"))
            assertEquals(config.findPrinter(originalPrinter)?.get("print_host"),
                config.findPrinter(copyTitle)?.get("print_host"))

            compose.onNodeWithText(copyTitle).assertIsDisplayed().performClick()
            compose.onNodeWithText(activity.getString(R.string.SettingsDeleteProfile))
                .performScrollTo().assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                config.findPrinter(copyTitle) == null && config.printerConfigs.size == originalCount
            }
            assertEquals("Deleting the clone selects a remaining printer preset", firstPrinterTitle,
                config.presets?.get("printer"))
        } finally {
            config.findPrinter(copyTitle)?.let(config.printerConfigs::remove)
            presets.put("printer", originalPrinter)
            if (originalProcess == null) presets.remove("print") else presets.put("print", originalProcess)
            if (originalFilament == null) presets.remove("filament") else presets.put("filament", originalFilament)
            printerSelections.forEach { (title, selection) ->
                if (selection == null) PrinterProfileSelectionStore.write(title, null, null)
                else PrinterProfileSelectionStore.write(title, selection.process, selection.filament)
            }
            ProfileSettingsRepository.clearDraft(ConfigObject.PROFILE_LIST_PRINTER)
            assertTrue(SliceBeam.saveConfig())
        }
    }

    @Test
    fun printHostStringSettingCancelBackAndSaveOnlyChangeTheSelectedPrinterProfile() {
        val activity = compose.activity
        val config = SliceBeam.requireConfig()
        val printerTitle = requireNotNull(config.presets?.get("printer"))
        val storedPrinter = requireNotNull(config.findPrinter(printerTitle))
        val originalHost = storedPrinter.get("print_host")
        val option = requireNotNull(PrintConfigDef.getInstance().options["print_host"])
        val optionTitle = Slic3rLocalization.getString(option.getLabel())
        val requestedHost = "http://profile-edit.invalid:7125"
        assertTrue("The fixture must allow an isolated value different from its stored host",
            originalHost != requestedHost)

        try {
            activity.runOnUiThread {
                (activity.navigationDelegate as MobileNavigationDelegate).switchToSlot(4)
            }
            compose.waitUntil(timeoutMillis = 15_000) {
                activity.navigationDelegate.getCurrentFragment() is PrinterConfigFragment
            }
            val draft = activity.runOnUiThreadAndWait {
                val fragment = activity.navigationDelegate.getCurrentFragment() as PrinterConfigFragment
                PrinterConfigFragment::class.java.getDeclaredMethod("getCurrentConfig")
                    .apply { isAccessible = true }
                    .invoke(fragment) as ConfigObject
            }
            val originalDraftHost = ProfileConfigValueController.valueFor(
                ConfigObject.PROFILE_LIST_PRINTER,
                draft,
                option.key,
                option.guiType,
                option.defaultValue,
                -1,
                false,
            ).orEmpty()

            ensureProfileCategoryExpanded("Physical Printer")
            scrollToProfileSetting(option.key, optionTitle)

            fun openPrintHostEditor() {
                val rows = compose.onAllNodesWithText(optionTitle, substring = true).fetchSemanticsNodes()
                assertTrue("The print host profile option should be visible", rows.isNotEmpty())
                val rowIndex = rows.indices.maxByOrNull { rows[it].boundsInRoot.top }
                    ?: throw AssertionError("The print host row is not displayed")
                compose.onAllNodesWithText(optionTitle, substring = true)[rowIndex].performClick()
                scrollToDialogTextEditor()
            }

            openPrintHostEditor()
            compose.onNode(hasSetTextAction()).performTextClearance()
            compose.onNode(hasSetTextAction()).performTextInput(requestedHost)
            compose.onNodeWithText(activity.getString(android.R.string.cancel)).performClick()
            compose.waitForIdle()
            assertEquals("Cancel must leave the printer host draft unchanged", originalDraftHost,
                ProfileConfigValueController.valueFor(
                    ConfigObject.PROFILE_LIST_PRINTER, draft, option.key, option.guiType,
                    option.defaultValue, -1, false,
                ))
            assertEquals(0, ProfileSettingsRepository.pendingCountFor(ConfigObject.PROFILE_LIST_PRINTER))
            assertEquals(originalHost, storedPrinter.get("print_host"))

            openPrintHostEditor()
            compose.onNode(hasSetTextAction()).performTextClearance()
            compose.onNode(hasSetTextAction()).performTextInput(requestedHost)
            InstrumentationRegistry.getInstrumentation()
                .sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            compose.waitForIdle()
            assertEquals("Android Back must discard the unconfirmed string edit", originalDraftHost,
                ProfileConfigValueController.valueFor(
                    ConfigObject.PROFILE_LIST_PRINTER, draft, option.key, option.guiType,
                    option.defaultValue, -1, false,
                ))
            assertEquals(0, ProfileSettingsRepository.pendingCountFor(ConfigObject.PROFILE_LIST_PRINTER))
            assertEquals(originalHost, storedPrinter.get("print_host"))

            openPrintHostEditor()
            compose.onNode(hasSetTextAction()).performTextClearance()
            compose.onNode(hasSetTextAction()).performTextInput(requestedHost)
            compose.onNodeWithText(activity.getString(android.R.string.ok)).performClick()
            compose.waitUntil(timeoutMillis = 5_000) {
                ProfileSettingsRepository.pendingEntries(ConfigObject.PROFILE_LIST_PRINTER)
                    .containsKey(option.key)
            }
            assertEquals("Apply stages the host only in the profile draft", requestedHost,
                ProfileSettingsRepository.valueFor(
                    ConfigObject.PROFILE_LIST_PRINTER, draft, option.key, true,
                ))
            assertEquals("Applying a dialog must not mutate the stored printer profile", originalHost,
                storedPrinter.get("print_host"))

            compose.onNodeWithText(activity.getString(R.string.SettingsSave)).assertIsDisplayed().performClick()
            compose.onNodeWithText(activity.getString(R.string.SettingsSaveTitle)).assertIsDisplayed()
            compose.onNodeWithText(activity.getString(android.R.string.ok)).performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                ProfileSettingsRepository.pendingCountFor(ConfigObject.PROFILE_LIST_PRINTER) == 0 &&
                    storedPrinter.get("print_host") == requestedHost
            }
            assertEquals("Confirmed profile save persists the selected host", requestedHost,
                storedPrinter.get("print_host"))
            assertEquals("Editing print_host does not change the active printer profile", printerTitle,
                config.presets?.get("printer"))
        } finally {
            ProfileSettingsRepository.clearDraft(ConfigObject.PROFILE_LIST_PRINTER)
            if (originalHost == null) storedPrinter.values.remove("print_host")
            else storedPrinter.put("print_host", originalHost)
            config.presets?.put("printer", printerTitle)
            check(SliceBeam.saveConfig()) { "Restore the isolated printer host fixture" }
        }
    }

    @Test
    fun printerHostTypeCanBeSelectedAndSavedWithoutContactingThePrinter() {
        val activity = compose.activity
        val config = SliceBeam.requireConfig()
        val printerTitle = requireNotNull(config.presets?.get("printer"))
        val storedPrinter = requireNotNull(config.findPrinter(printerTitle))
        val originalHostType = storedPrinter.get("host_type")
        val targetHostType = if (originalHostType == "moonraker") "octoprint" else "moonraker"
        val targetLabel = activity.getString(
            if (targetHostType == "moonraker") R.string.PrinterHostMoonraker else R.string.PrinterHostOctoPrint,
        )
        val hostTypeOption = requireNotNull(PrintConfigDef.getInstance().options["host_type"])
        val hostTypeTitle = Slic3rLocalization.getString(hostTypeOption.getLabel())

        try {
            activity.runOnUiThread {
                (activity.navigationDelegate as MobileNavigationDelegate).switchToSlot(4)
            }
            compose.waitUntil(timeoutMillis = 15_000) {
                activity.navigationDelegate.getCurrentFragment() is PrinterConfigFragment
            }
            val draft = activity.runOnUiThreadAndWait {
                val fragment = activity.navigationDelegate.getCurrentFragment() as PrinterConfigFragment
                PrinterConfigFragment::class.java.getDeclaredMethod("getCurrentConfig")
                    .apply { isAccessible = true }
                    .invoke(fragment) as ConfigObject
            }
            ensureProfileCategoryExpanded("Physical Printer")
            scrollToProfileSetting(hostTypeOption.key, hostTypeTitle)

            val rows = compose.onAllNodesWithText(hostTypeTitle).fetchSemanticsNodes()
            assertTrue("The host type setting should be visible", rows.isNotEmpty())
            val rowIndex = rows.indices.maxByOrNull { rows[it].boundsInRoot.top }
                ?: throw AssertionError("The host type row is not displayed")
            compose.onAllNodesWithText(hostTypeTitle)[rowIndex].performClick()
            compose.onNodeWithText(targetLabel).assertIsDisplayed().performClick()

            assertEquals("Selecting a host type changes only the active printer draft", targetHostType,
                ProfileConfigValueController.valueFor(
                    ConfigObject.PROFILE_LIST_PRINTER, draft, hostTypeOption.key,
                    hostTypeOption.guiType, hostTypeOption.defaultValue, -1, false,
                ))
            assertEquals("Host type selection does not mutate the stored profile before Save",
                originalHostType, storedPrinter.get("host_type"))
            compose.onNodeWithText(activity.getString(R.string.SettingsSave)).performClick()
            compose.onNodeWithText(activity.getString(R.string.SettingsSaveTitle)).assertIsDisplayed()
            compose.onNodeWithText(activity.getString(android.R.string.ok)).performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                ProfileSettingsRepository.pendingCountFor(ConfigObject.PROFILE_LIST_PRINTER) == 0 &&
                    storedPrinter.get("host_type") == targetHostType
            }
            assertEquals("Confirmed Save persists the selected host type", targetHostType,
                storedPrinter.get("host_type"))
            assertEquals("Changing transport type preserves the selected printer profile", printerTitle,
                config.presets?.get("printer"))
        } finally {
            ProfileSettingsRepository.clearDraft(ConfigObject.PROFILE_LIST_PRINTER)
            if (originalHostType == null) storedPrinter.values.remove("host_type")
            else storedPrinter.put("host_type", originalHostType)
            config.presets?.put("printer", printerTitle)
            check(SliceBeam.saveConfig()) { "Restore the isolated printer transport fixture" }
        }
    }

    @Test
    fun printerApiKeyEditorMasksRevealsAndKeepsSyntheticEditsInTheDraft() {
        val activity = compose.activity
        val config = SliceBeam.requireConfig()
        val printerTitle = requireNotNull(config.presets?.get("printer"))
        val storedPrinter = requireNotNull(config.findPrinter(printerTitle))
        val originalApiKey = storedPrinter.get("printhost_apikey")
        val option = requireNotNull(PrintConfigDef.getInstance().options["printhost_apikey"])
        val optionTitle = Slic3rLocalization.getString(option.getLabel())
        val syntheticStoredApiKey = "stored-test-api-key"
        val syntheticApiKey = "not-a-real-api-key"
        var originalDraftApiKey: String? = null
        ProfileSettingsRepository.clearDraft(ConfigObject.PROFILE_LIST_PRINTER)
        storedPrinter.put(option.key, syntheticStoredApiKey)
        check(SliceBeam.saveConfig()) { "Could not prepare the isolated API key fixture" }

        try {
            activity.runOnUiThread {
                (activity.navigationDelegate as MobileNavigationDelegate).switchToSlot(4)
            }
            compose.waitUntil(timeoutMillis = 15_000) {
                activity.navigationDelegate.getCurrentFragment() is PrinterConfigFragment
            }
            val draft = activity.runOnUiThreadAndWait {
                val fragment = activity.navigationDelegate.getCurrentFragment() as PrinterConfigFragment
                PrinterConfigFragment::class.java.getDeclaredMethod("getCurrentConfig")
                    .apply { isAccessible = true }
                    .invoke(fragment) as ConfigObject
            }
            originalDraftApiKey = ProfileConfigValueController.valueFor(
                ConfigObject.PROFILE_LIST_PRINTER, draft, option.key, option.guiType,
                option.defaultValue, -1, false,
            )
            ensureProfileCategoryExpanded("Physical Printer")
            scrollToProfileSetting(option.key, optionTitle)
            assertTrue("The saved API key must not be exposed in the profile row",
                compose.onAllNodesWithText(syntheticStoredApiKey).fetchSemanticsNodes().isEmpty())

            fun openApiKeyEditor() {
                val rows = compose.onAllNodesWithText(optionTitle, substring = true).fetchSemanticsNodes()
                assertTrue("The printer API key profile option should be visible", rows.isNotEmpty())
                val rowIndex = rows.indices.maxByOrNull { rows[it].boundsInRoot.top }
                    ?: throw AssertionError("The printer API key row is not displayed")
                compose.onAllNodesWithText(optionTitle, substring = true)[rowIndex].performClick()
                scrollToDialogTextEditor()
                compose.onNodeWithText(activity.getString(R.string.ProfileStringInputLabel)).assertIsDisplayed()
            }

            openApiKeyEditor()
            val showPasswordLabel = listOf(activity.getString(R.string.ProfileStringShowPassword), "Show")
                .firstOrNull { compose.onAllNodesWithText(it, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
                ?: throw AssertionError("The password reveal button should expose a localized label")
            val hidePasswordLabel = if (showPasswordLabel == "Show") "Hide"
                else activity.getString(R.string.ProfileStringHidePassword)
            compose.onNodeWithText(showPasswordLabel, useUnmergedTree = true).assertIsDisplayed()
            compose.onNode(hasSetTextAction()).performTextClearance()
            compose.onNode(hasSetTextAction()).performTextInput(syntheticApiKey)
            // Landscape leaves little height once the IME is open. Dismiss it before
            // exercising the trailing visibility action so the dialog can expose it.
            InstrumentationRegistry.getInstrumentation()
                .sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            compose.waitForIdle()
            compose.onNodeWithText(showPasswordLabel, useUnmergedTree = true).performClick()
            compose.onNodeWithText(hidePasswordLabel, useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithText(syntheticApiKey).assertIsDisplayed()
            compose.onNodeWithText(hidePasswordLabel, useUnmergedTree = true).performClick()
            compose.onNodeWithText(showPasswordLabel, useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithText(activity.getString(android.R.string.cancel)).performClick()
            compose.waitForIdle()
            assertEquals("Cancel keeps the API key profile draft unchanged", originalDraftApiKey,
                ProfileConfigValueController.valueFor(
                    ConfigObject.PROFILE_LIST_PRINTER, draft, option.key, option.guiType,
                    option.defaultValue, -1, false,
                ))
            assertEquals(0, ProfileSettingsRepository.pendingCountFor(ConfigObject.PROFILE_LIST_PRINTER))
            assertEquals("Cancel never persists the edited key", syntheticStoredApiKey,
                storedPrinter.get("printhost_apikey"))

            openApiKeyEditor()
            compose.onNode(hasSetTextAction()).performTextClearance()
            compose.onNode(hasSetTextAction()).performTextInput(syntheticApiKey)
            InstrumentationRegistry.getInstrumentation()
                .sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            compose.waitForIdle()
            assertEquals("Android Back must discard the unconfirmed API key edit", originalDraftApiKey,
                ProfileConfigValueController.valueFor(
                    ConfigObject.PROFILE_LIST_PRINTER, draft, option.key, option.guiType,
                    option.defaultValue, -1, false,
                ))
            assertEquals(0, ProfileSettingsRepository.pendingCountFor(ConfigObject.PROFILE_LIST_PRINTER))
            assertEquals("Back never persists the edited key", syntheticStoredApiKey,
                storedPrinter.get("printhost_apikey"))

            openApiKeyEditor()
            compose.onNode(hasSetTextAction()).performTextClearance()
            compose.onNode(hasSetTextAction()).performTextInput(syntheticApiKey)
            compose.onNodeWithText(activity.getString(android.R.string.ok)).performClick()
            compose.waitUntil(timeoutMillis = 5_000) {
                ProfileSettingsRepository.pendingEntries(ConfigObject.PROFILE_LIST_PRINTER)
                    .containsKey(option.key)
            }
            assertEquals("Apply stages the synthetic key only in the profile draft", syntheticApiKey,
                ProfileSettingsRepository.valueFor(
                    ConfigObject.PROFILE_LIST_PRINTER, draft, option.key, true,
                ))
            assertEquals("Apply does not persist or send the edited key", syntheticStoredApiKey,
                storedPrinter.get("printhost_apikey"))

            compose.onNodeWithText(activity.getString(R.string.SettingsSave)).assertIsDisplayed().performClick()
            compose.onNodeWithText(activity.getString(R.string.SettingsSaveTitle)).assertIsDisplayed()
            compose.onNodeWithText(activity.getString(android.R.string.ok)).performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                ProfileSettingsRepository.pendingCountFor(ConfigObject.PROFILE_LIST_PRINTER) == 0 &&
                    storedPrinter.get("printhost_apikey") == syntheticApiKey
            }
            assertEquals("Confirmed profile Save persists the API key to the selected printer only",
                syntheticApiKey, storedPrinter.get("printhost_apikey"))
            assertEquals("Saving API key does not change the active printer profile", printerTitle,
                config.presets?.get("printer"))
        } finally {
            ProfileSettingsRepository.clearDraft(ConfigObject.PROFILE_LIST_PRINTER)
            if (originalApiKey == null) storedPrinter.values.remove("printhost_apikey")
            else storedPrinter.put("printhost_apikey", originalApiKey)
            config.presets?.put("printer", printerTitle)
            check(SliceBeam.saveConfig()) { "Restore the isolated printer API key fixture" }
        }
    }

    @Test
    fun printerDashboardButtonOpensExternalBrowserForTheConfiguredProfile() {
        val activity = compose.activity
        val config = SliceBeam.requireConfig()
        val printerTitle = requireNotNull(config.presets?.get("printer"))
        val printer = requireNotNull(config.findPrinter(printerTitle))
        val originalHost = printer.get("print_host")
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        try {
            printer.put("print_host", "http://printer.invalid:7125")
            check(SliceBeam.saveConfig()) { "Could not prepare the isolated printer dashboard fixture" }
            val navigation = activity.navigationDelegate as MobileNavigationDelegate
            activity.runOnUiThread {
                // The profile fragment is cached across workspace destinations. Force a
                // leave/return so onResume rebinds its editor snapshot to this saved fixture.
                navigation.switchToSlot(0)
            }
            compose.waitUntil(timeoutMillis = 10_000) {
                navigation.getCurrentFragment() !is PrinterConfigFragment
            }
            activity.runOnUiThread {
                navigation.switchToSlot(4)
            }
            compose.waitUntil(timeoutMillis = 15_000) {
                activity.navigationDelegate.getCurrentFragment() is PrinterConfigFragment
            }
            val dashboardAction = activity.getString(R.string.PrinterOpenWebDescription)
            // Fragment switching is asynchronous: the cached printer profile can be
            // current and its semantics node can exist before Compose has laid the
            // header out in the visible window. Wait for the actionable button to be
            // displayed, not merely present in the semantics tree.
            compose.waitUntil(timeoutMillis = 10_000) {
                runCatching {
                    compose.onNodeWithContentDescription(dashboardAction).assertIsDisplayed()
                }.isSuccess
            }
            compose.onNodeWithContentDescription(dashboardAction)
                .assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 5_000) { !activity.hasWindowFocus() }
            assertTrue("The configured dashboard action opens the external browser",
                !activity.hasWindowFocus())
        } finally {
            if (!activity.hasWindowFocus()) {
                instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            }
            if (originalHost == null) printer.values.remove("print_host")
            else printer.put("print_host", originalHost)
            config.presets?.put("printer", printerTitle)
            check(SliceBeam.saveConfig()) { "Could not restore the isolated printer dashboard fixture" }
        }
    }

    @Test
    fun activeCompatibleProcessPresetIsSelectedInFilteredMenuSnapshot() {
        val activity = compose.activity
        val config = SliceBeam.requireConfig()
        val initialPrinter = requireNotNull(config.findPrinter(config.presets?.get("printer")))
        assertEquals(
            "The fixture starts from the selected printer's current factory default",
            initialPrinter["default_print_profile"],
            config.presets?.get("print"),
        )

        activity.runOnUiThread {
            (activity.navigationDelegate as MobileNavigationDelegate).switchToSlot(2)
        }
        compose.waitUntil(timeoutMillis = 15_000) {
            activity.navigationDelegate.getCurrentFragment() is PrintConfigFragment
        }
        val activeTitle = requireNotNull(SliceBeam.requireConfig().presets?.get("print"))

        val fragment = activity.navigationDelegate.getCurrentFragment() as PrintConfigFragment
        val visibleItems = activity.runOnUiThreadAndWait {
            PrintConfigFragment::class.java
                .getDeclaredMethod("getItems", Boolean::class.javaPrimitiveType)
                .apply { isAccessible = true }
                .invoke(fragment, true) as List<*>
        }
        val visibleTitles = visibleItems.mapNotNull { item ->
            item?.javaClass?.getMethod("getTitle")?.invoke(item) as? String
        }
        val selectedItems = visibleItems.filter { item ->
            item?.javaClass?.getMethod("isSelected")?.invoke(item) as? Boolean == true
        }

        assertNotNull("The active preset must be present in the printer-compatible menu", visibleTitles.firstOrNull { it == activeTitle })
        assertEquals("Exactly one visible process preset should be selected", 1, selectedItems.size)
        assertEquals(
            "The selected menu row must match the active preset",
            activeTitle,
            selectedItems.single()?.javaClass?.getMethod("getTitle")?.invoke(selectedItems.single()),
        )
        assertEquals("Inspecting the filtered list must not change the active preset", activeTitle, SliceBeam.requireConfig().presets?.get("print"))
    }

    @Test
    fun selectingCompatibleProcessPresetThroughMenuChangesActiveSelection() {
        val activity = compose.activity
        val config = SliceBeam.requireConfig()
        activity.runOnUiThread {
            (activity.navigationDelegate as MobileNavigationDelegate).switchToSlot(2)
        }
        compose.waitUntil(timeoutMillis = 15_000) {
            activity.navigationDelegate.getCurrentFragment() is PrintConfigFragment
        }

        val activeTitle = requireNotNull(config.presets?.get("print"))
        val fragment = activity.navigationDelegate.getCurrentFragment() as PrintConfigFragment
        val alternateTitle = activity.runOnUiThreadAndWait {
            val items = PrintConfigFragment::class.java
                .getDeclaredMethod("getItems", Boolean::class.javaPrimitiveType)
                .apply { isAccessible = true }
                .invoke(fragment, true) as List<*>
            items.asSequence()
                .mapNotNull { item -> item?.javaClass?.getMethod("getTitle")?.invoke(item) as? String }
                .firstOrNull { it != activeTitle }
        }
        assertNotNull("The bundled printer fixture needs another compatible process profile", alternateTitle)
        val target = requireNotNull(alternateTitle)
        val printer = requireNotNull(config.presets?.get("printer"))
        val expectedScopedTitle = PrinterScopedProcessProfile.title(target, printer)

        compose.onNodeWithText(activeTitle).assertIsDisplayed().performClick()
        compose.onNodeWithText(target).assertIsDisplayed().performClick()
        compose.waitUntil(timeoutMillis = 10_000) { config.presets?.get("print") == expectedScopedTitle }

        assertEquals("Selecting a bundled menu row activates its printer-scoped copy", expectedScopedTitle, config.presets?.get("print"))
        assertNotNull("The source preset remains available", config.findPrint(target))
        assertNotNull("The active printer owns the selected process copy", config.findPrint(expectedScopedTitle))
        val visibleItems = activity.runOnUiThreadAndWait {
            PrintConfigFragment::class.java
                .getDeclaredMethod("getItems", Boolean::class.javaPrimitiveType)
                .apply { isAccessible = true }
                .invoke(fragment, true) as List<*>
        }
        val selectedTitles = visibleItems.filter { item ->
            item?.javaClass?.getMethod("isSelected")?.invoke(item) as? Boolean == true
        }.mapNotNull { item -> item?.javaClass?.getMethod("getTitle")?.invoke(item) as? String }
        assertEquals("Exactly the printer-scoped active profile is marked in the filtered list", listOf(expectedScopedTitle), selectedTitles)
    }

    @Test
    fun selectingPrinterProfileThroughMenuScopesItsActiveProcess() {
        val activity = compose.activity
        val config = SliceBeam.requireConfig()
        val previousPrinter = requireNotNull(config.presets?.get("printer"))
        val targetPrinter = "Anycubic Kobra 3 0.6 nozzle"
        assertNotNull("The bundled test configuration must contain the target printer", config.findPrinter(targetPrinter))
        val previousPrinterSelection = PrinterProfileSelectionStore.read(previousPrinter)
        val previousTargetSelection = PrinterProfileSelectionStore.read(targetPrinter)

        try {
            activity.runOnUiThread {
                (activity.navigationDelegate as MobileNavigationDelegate).switchToSlot(4)
            }
            compose.waitUntil(timeoutMillis = 15_000) {
                activity.navigationDelegate.getCurrentFragment() is PrinterConfigFragment
            }

            // Target the profile list's own dropdown, not the similarly labeled global
            // workspace profile-status button behind this screen.
            compose.onNodeWithTag("profile-dropdown").assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithText(targetPrinter).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(targetPrinter).performScrollTo().assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 15_000) {
                SliceBeam.requireConfig().presets?.get("printer") == targetPrinter
            }

            val selectedConfig = SliceBeam.requireConfig()
            val selectedPrinter = requireNotNull(selectedConfig.findPrinter(targetPrinter))
            assertEquals("Printer selection must activate the selected nozzle profile", "0.6",
                selectedPrinter.get("nozzle_diameter"))
            assertTrue("The selected printer contributes its printable bed bounds",
                selectedPrinter.get("printable_area").orEmpty().isNotBlank())
            assertTrue("The selected printer contributes its own start G-code",
                selectedPrinter.get("machine_start_gcode").orEmpty().isNotBlank())
            val selectedProcess = requireNotNull(selectedConfig.presets?.get("print"))
            val scopedProcess = requireNotNull(selectedConfig.findPrint(selectedProcess))
            assertTrue(
                "Changing printers must scope the active process to the selected printer",
                PrinterScopedProcessProfile.belongsTo(scopedProcess, targetPrinter),
            )
            assertNotNull("The synchronized active filament must remain present", selectedConfig.findFilament(selectedConfig.presets?.get("filament")))
        } finally {
            previousPrinterSelection?.let { PrinterProfileSelectionStore.write(previousPrinter, it.process, it.filament) }
                ?: PrinterProfileSelectionStore.write(previousPrinter, null, null)
            previousTargetSelection?.let { PrinterProfileSelectionStore.write(targetPrinter, it.process, it.filament) }
                ?: PrinterProfileSelectionStore.write(targetPrinter, null, null)
            // This is a real profile-selection action, so restore the active
            // printer as well as its scoped selection fixtures. Otherwise later
            // tests inherit the Anycubic profile and depend on execution order.
            SliceBeam.requireConfig().presets?.put("printer", previousPrinter)
            check(SliceBeam.saveConfig()) { "Could not restore the original active printer profile" }
        }
    }

    @Test
    fun printerBedSurfaceEditIsDraftUntilSavedAndCanBeDiscardedWithBackOrReset() {
        val activity = compose.activity
        val config = SliceBeam.requireConfig()
        val printerTitle = requireNotNull(config.presets?.get("printer"))
        val storedPrinter = requireNotNull(config.findPrinter(printerTitle))
        val storedBedSurface = storedPrinter.get("curr_bed_type")
        val option = requireNotNull(PrintConfigDef.getInstance().options["curr_bed_type"])
        val optionTitle = Slic3rLocalization.getString(option.getLabel())
        val optionValues = requireNotNull(option.enumValues)
        val optionLabels = requireNotNull(option.enumLabels).map(Slic3rLocalization::getString)

        activity.runOnUiThread {
            (activity.navigationDelegate as MobileNavigationDelegate).switchToSlot(4)
        }
        compose.waitUntil(timeoutMillis = 15_000) {
            activity.navigationDelegate.getCurrentFragment() is PrinterConfigFragment
        }

        val draft = activity.runOnUiThreadAndWait {
            val fragment = activity.navigationDelegate.getCurrentFragment() as PrinterConfigFragment
            PrinterConfigFragment::class.java.getDeclaredMethod("getCurrentConfig")
                .apply { isAccessible = true }
                .invoke(fragment) as ConfigObject
        }
        val currentValue = requireNotNull(draft.get("curr_bed_type"))
        val targetIndex = optionValues.indices.first { optionValues[it] != currentValue }
        val targetValue = optionValues[targetIndex]
        val targetLabel = optionLabels[targetIndex]

        ensureProfileCategoryExpanded("General")
        scrollToProfileSetting(option.key, optionTitle)
        compose.onNodeWithText(optionTitle).assertIsDisplayed().performClick()
        compose.onNodeWithText(targetLabel).assertIsDisplayed().performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            ProfileSettingsRepository.pendingCountFor(ConfigObject.PROFILE_LIST_PRINTER) > 0
        }

        assertEquals("The user-selected surface is reflected in the open profile draft", targetValue,
            ProfileConfigValueController.valueFor(ConfigObject.PROFILE_LIST_PRINTER, draft, "curr_bed_type",
                option.guiType, option.defaultValue, -1, false))
        assertEquals("Editing a profile must not write to the stored printer preset", storedBedSurface,
            storedPrinter.get("curr_bed_type"))

        // Android Back dismisses the settings route. The uncommitted edit remains a draft, while
        // the persisted preset continues to retain its original value.
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            activity.onBackPressedDispatcher.onBackPressed()
        }
        compose.waitUntil(timeoutMillis = 10_000) {
            activity.navigationDelegate.getCurrentFragment() !is PrinterConfigFragment
        }
        assertEquals(storedBedSurface, storedPrinter.get("curr_bed_type"))

        activity.runOnUiThread {
            (activity.navigationDelegate as MobileNavigationDelegate).switchToSlot(4)
        }
        compose.waitUntil(timeoutMillis = 15_000) {
            activity.navigationDelegate.getCurrentFragment() is PrinterConfigFragment
        }
        val resetDescription = activity.getString(R.string.SettingsResetProfileAction)
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithContentDescription(resetDescription).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription(resetDescription)
            .assertIsDisplayed().performClick()
        compose.onNodeWithText(activity.getString(R.string.SettingsResetProfileTitle)).assertIsDisplayed()
        // Compose AlertDialog owns a separate platform dialog window, so exercise its real
        // Android Back dismissal instead of invoking the activity dispatcher directly.
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        assertTrue("Dismissing reset must leave the edit draft pending",
            ProfileSettingsRepository.pendingCountFor(ConfigObject.PROFILE_LIST_PRINTER) > 0)
        assertEquals("Dismissing reset keeps the stored printer preset unchanged", storedBedSurface,
            storedPrinter.get("curr_bed_type"))

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithContentDescription(resetDescription).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription(resetDescription)
            .performClick()
        compose.onNodeWithText(activity.getString(android.R.string.ok)).assertIsDisplayed().performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            ProfileSettingsRepository.pendingCountFor(ConfigObject.PROFILE_LIST_PRINTER) == 0
        }
        assertEquals("Confirmed reset discards the draft without persisting it", storedBedSurface,
            storedPrinter.get("curr_bed_type"))
    }

    @Test
    fun savingPrinterBedSurfaceDraftPersistsOnlyAfterSaveConfirmation() {
        val activity = compose.activity
        val config = SliceBeam.requireConfig()
        val printerTitle = requireNotNull(config.presets?.get("printer"))
        val storedPrinter = requireNotNull(config.findPrinter(printerTitle))
        val storedBedSurface = storedPrinter.get("curr_bed_type")
        val option = requireNotNull(PrintConfigDef.getInstance().options["curr_bed_type"])
        val optionTitle = Slic3rLocalization.getString(option.getLabel())
        val optionValues = requireNotNull(option.enumValues)
        val optionLabels = requireNotNull(option.enumLabels).map(Slic3rLocalization::getString)

        try {
            activity.runOnUiThread {
                (activity.navigationDelegate as MobileNavigationDelegate).switchToSlot(4)
            }
            compose.waitUntil(timeoutMillis = 15_000) {
                activity.navigationDelegate.getCurrentFragment() is PrinterConfigFragment
            }

            val draft = activity.runOnUiThreadAndWait {
                val fragment = activity.navigationDelegate.getCurrentFragment() as PrinterConfigFragment
                PrinterConfigFragment::class.java.getDeclaredMethod("getCurrentConfig")
                    .apply { isAccessible = true }
                    .invoke(fragment) as ConfigObject
            }
            val currentValue = requireNotNull(draft.get("curr_bed_type"))
            val targetIndex = optionValues.indices.first { optionValues[it] != currentValue }
            val targetValue = optionValues[targetIndex]
            val targetLabel = optionLabels[targetIndex]

            ensureProfileCategoryExpanded("General")
            scrollToProfileSetting(option.key, optionTitle)
            compose.onNodeWithText(optionTitle).assertIsDisplayed().performClick()
            compose.onNodeWithText(targetLabel).assertIsDisplayed().performClick()
            compose.onNodeWithText(activity.getString(R.string.SettingsSave)).assertIsDisplayed().performClick()
            compose.onNodeWithText(activity.getString(R.string.SettingsSaveTitle)).assertIsDisplayed()
            compose.onNodeWithText(activity.getString(android.R.string.ok)).performClick()

            compose.waitUntil(timeoutMillis = 10_000) {
                ProfileSettingsRepository.pendingCountFor(ConfigObject.PROFILE_LIST_PRINTER) == 0 &&
                    storedPrinter.get("curr_bed_type") == targetValue
            }
            assertEquals("The confirmed profile save applies the selected surface", targetValue,
                storedPrinter.get("curr_bed_type"))
            assertEquals("Saving the current profile leaves printer selection intact", printerTitle,
                config.presets?.get("printer"))
        } finally {
            ProfileSettingsRepository.clearDraft(ConfigObject.PROFILE_LIST_PRINTER)
            storedPrinter.put("curr_bed_type", storedBedSurface)
            assertTrue("Restore the bundled printer fixture after the test", SliceBeam.saveConfig())
        }
    }

    @Test
    fun printNumericSettingRemainsDraftUntilSaveAndCancelKeepsTheAppliedDraft() {
        val activity = compose.activity
        val config = SliceBeam.requireConfig()
        val profile = requireNotNull(config.findPrint(config.presets?.get("print")))
        val originalLayerHeight = profile.get("layer_height")
        val option = requireNotNull(PrintConfigDef.getInstance().options["layer_height"])
        val optionTitle = Slic3rLocalization.getString(option.getLabel())
        val appliedValue = if (originalLayerHeight == "0.18") "0.2" else "0.18"
        val cancelledValue = if (appliedValue == "0.18") "0.22" else "0.24"
        ProfileSettingsRepository.clearDraft(ConfigObject.PROFILE_LIST_PRINT)

        try {
            activity.runOnUiThread { (activity.navigationDelegate as MobileNavigationDelegate).switchToSlot(2) }
            compose.waitUntil(timeoutMillis = 15_000) {
                activity.navigationDelegate.getCurrentFragment() is PrintConfigFragment
            }
            ensureProfileCategoryExpanded("Layers and perimeters")
            compose.onNodeWithTag("profile-settings-options-list")
                .performScrollToNode(hasTestTag("profile-option-key-${option.key}"))
            val layerHeightRows = compose.onAllNodesWithText(optionTitle, substring = true)
            layerHeightRows[layerHeightRows.fetchSemanticsNodes().lastIndex].performClick()
            val numericInput = hasSetTextAction() and hasText(activity.getString(R.string.ProfileNumericInputLabel))
            compose.onNode(numericInput).performTextClearance()
            compose.onNode(numericInput).performTextInput(appliedValue)
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            compose.onNodeWithText(activity.getString(android.R.string.ok)).performClick()
            compose.waitUntil(timeoutMillis = 5_000) {
                ProfileSettingsRepository.pendingEntries(ConfigObject.PROFILE_LIST_PRINT)
                    .containsKey(option.key)
            }
            assertEquals("Changing a print numeric value must not write the stored profile before Save",
                originalLayerHeight, profile.get(option.key))
            assertEquals(appliedValue, ProfileSettingsRepository.valueFor(
                ConfigObject.PROFILE_LIST_PRINT, profile, option.key, true,
            ))

            compose.onNodeWithTag("profile-settings-options-list")
                .performScrollToNode(hasTestTag("profile-option-key-${option.key}"))
            val updatedLayerHeightRows = compose.onAllNodesWithText(optionTitle, substring = true)
            updatedLayerHeightRows[updatedLayerHeightRows.fetchSemanticsNodes().lastIndex].performClick()
            compose.onNode(numericInput).performTextClearance()
            compose.onNode(numericInput).performTextInput(cancelledValue)
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            compose.onNodeWithText(activity.getString(android.R.string.cancel)).performClick()
            assertEquals("Cancel must discard only the second numeric edit", appliedValue,
                ProfileSettingsRepository.valueFor(ConfigObject.PROFILE_LIST_PRINT, profile, option.key, true))
            assertEquals(originalLayerHeight, profile.get(option.key))

            val otherProcess = config.printConfigs.firstOrNull { it.title != profile.title }
            val otherLayerHeight = otherProcess?.get(option.key)
            compose.onNodeWithTag("profile-settings-options-list")
                .performScrollToNode(hasTestTag("profile-option-key-${option.key}"))
            val resetRows = compose.onAllNodesWithText(optionTitle, substring = true)
            resetRows[resetRows.fetchSemanticsNodes().lastIndex].performClick()
            compose.onNode(numericInput).performTextClearance()
            compose.onNode(numericInput).performTextInput(cancelledValue)
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            compose.onNodeWithText(activity.getString(android.R.string.ok)).performClick()
            compose.onNodeWithContentDescription(activity.getString(R.string.SettingsResetProfileAction))
                .performClick()
            compose.onNodeWithText(activity.getString(R.string.SettingsResetProfileTitle)).assertIsDisplayed()
            compose.onNodeWithText(activity.getString(android.R.string.ok)).performClick()
            compose.waitUntil(timeoutMillis = 5_000) {
                ProfileSettingsRepository.pendingCountFor(ConfigObject.PROFILE_LIST_PRINT) == 0
            }
            assertEquals("Reset must discard the active process draft only", originalLayerHeight,
                config.findPrint(config.presets?.get("print"))?.get(option.key))
            if (otherProcess != null) assertEquals("Reset must not modify another process preset",
                otherLayerHeight, otherProcess.get(option.key))

            compose.onNodeWithTag("profile-settings-options-list")
                .performScrollToNode(hasTestTag("profile-option-key-${option.key}"))
            val saveRows = compose.onAllNodesWithText(optionTitle, substring = true)
            saveRows[saveRows.fetchSemanticsNodes().lastIndex].performClick()
            compose.onNode(numericInput).performTextClearance()
            compose.onNode(numericInput).performTextInput(appliedValue)
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            compose.onNodeWithText(activity.getString(android.R.string.ok)).performClick()

            compose.onNodeWithText(activity.getString(R.string.SettingsSave)).performClick()
            compose.onNodeWithText(activity.getString(R.string.SettingsSaveTitle)).assertIsDisplayed()
            compose.onNodeWithText(activity.getString(android.R.string.ok)).performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                ProfileSettingsRepository.pendingCountFor(ConfigObject.PROFILE_LIST_PRINT) == 0
            }
            val savedProfileTitle = config.presets?.get("print")
            val savedProfile = requireNotNull(config.findPrint(savedProfileTitle))
            assertEquals("Only explicit profile Save persists the accepted numeric edit", appliedValue,
                savedProfile.get(option.key))
        } finally {
            ProfileSettingsRepository.clearDraft(ConfigObject.PROFILE_LIST_PRINT)
            profile.put(option.key, originalLayerHeight)
            check(SliceBeam.saveConfig()) { "Could not restore the print profile layer-height fixture" }
        }
    }

    @Test
    fun primeTowerToolStagesAppliedValuesAndCancelKeepsTheExistingDraft() {
        val activity = compose.activity
        val config = SliceBeam.requireConfig()
        val presets = requireNotNull(config.presets)
        val originalPrinter = requireNotNull(presets["printer"])
        val originalProcess = requireNotNull(presets["print"])
        val originalTitles = config.printConfigs.mapNotNull { it.title }.toSet()
        val printerSelections = config.printerConfigs.mapNotNull { profile ->
            profile.title?.let { it to PrinterProfileSelectionStore.read(it) }
        }.toMap()
        ProfileSettingsRepository.clearDraft(ConfigObject.PROFILE_LIST_PRINT)

        try {
            activity.runOnUiThread {
                (activity.navigationDelegate as MobileNavigationDelegate).switchToSlot(0)
            }
            compose.waitUntil(timeoutMillis = 15_000) {
                activity.navigationDelegate.getCurrentFragment() is BedFragment
            }
            val activeProcessTitle = requireNotNull(presets["print"])
            val activeProcess = requireNotNull(config.findPrint(activeProcessTitle))
            val originalX = activeProcess.get("wipe_tower_x")
            val originalY = activeProcess.get("wipe_tower_y")
            val initialPositions = PrintConfigToolsLogic.towerPositions(originalX, originalY)
            val changedX = initialPositions.x.toMutableList().also { it[0] = "27.5" }
            val changedY = initialPositions.y.toMutableList().also { it[0] = "42.25" }
            val expected = requireNotNull(PrintConfigToolsLogic.serializeTowerPositions(changedX, changedY))

            compose.onNodeWithTag("workspace-header-prime-tower").assertIsDisplayed().performClick()
            compose.onNodeWithTag("prime-tower-x-0").performTextClearance()
            compose.onNodeWithTag("prime-tower-x-0").performTextInput("27.5")
            compose.onNodeWithTag("prime-tower-y-0").performTextClearance()
            compose.onNodeWithTag("prime-tower-y-0").performTextInput("42.25")
            compose.onNodeWithText(activity.getString(android.R.string.ok)).performClick()
            compose.waitUntil(timeoutMillis = 5_000) {
                ProfileSettingsRepository.pendingEntries(ConfigObject.PROFILE_LIST_PRINT)
                    .containsKey("wipe_tower_x")
            }

            assertEquals(expected.first, ProfileSettingsRepository.valueFor(
                ConfigObject.PROFILE_LIST_PRINT, activeProcess, "wipe_tower_x", true,
            ))
            assertEquals(expected.second, ProfileSettingsRepository.valueFor(
                ConfigObject.PROFILE_LIST_PRINT, activeProcess, "wipe_tower_y", true,
            ))
            assertEquals("Applying the tool edits only the profile draft", originalX, activeProcess.get("wipe_tower_x"))
            assertEquals(originalY, activeProcess.get("wipe_tower_y"))

            compose.onNodeWithTag("workspace-header-prime-tower").assertIsDisplayed().performClick()
            compose.onNodeWithTag("prime-tower-x-0").performTextClearance()
            compose.onNodeWithTag("prime-tower-x-0").performTextInput("35")
            compose.onNodeWithText(activity.getString(android.R.string.cancel)).performClick()
            compose.waitForIdle()
            assertEquals("Cancel preserves the already applied draft value", expected.first,
                ProfileSettingsRepository.valueFor(
                    ConfigObject.PROFILE_LIST_PRINT, activeProcess, "wipe_tower_x", true,
                ))
            assertEquals("The stored profile remains unchanged until the profile Save action",
                originalX, activeProcess.get("wipe_tower_x"))
        } finally {
            ProfileSettingsRepository.clearDraft(ConfigObject.PROFILE_LIST_PRINT)
            config.printConfigs.removeAll { it.title !in originalTitles }
            presets.put("printer", originalPrinter)
            presets.put("print", originalProcess)
            printerSelections.forEach { (title, selection) ->
                if (selection == null) PrinterProfileSelectionStore.write(title, null, null)
                else PrinterProfileSelectionStore.write(title, selection.process, selection.filament)
            }
            check(SliceBeam.saveConfig()) { "Could not restore the isolated process profile fixture" }
        }
    }

    @Test
    fun flushVolumesToolStagesAppliedMatrixAndCancelKeepsTheExistingDraft() {
        val activity = compose.activity
        val config = SliceBeam.requireConfig()
        val presets = requireNotNull(config.presets)
        val originalPrinter = requireNotNull(presets["printer"])
        val originalProcess = requireNotNull(presets["print"])
        val originalTitles = config.printConfigs.mapNotNull { it.title }.toSet()
        val printerSelections = config.printerConfigs.mapNotNull { profile ->
            profile.title?.let { it to PrinterProfileSelectionStore.read(it) }
        }.toMap()
        ProfileSettingsRepository.clearDraft(ConfigObject.PROFILE_LIST_PRINT)

        try {
            activity.runOnUiThread {
                (activity.navigationDelegate as MobileNavigationDelegate).switchToSlot(0)
            }
            compose.waitUntil(timeoutMillis = 15_000) {
                activity.navigationDelegate.getCurrentFragment() is BedFragment
            }
            val activeProcess = requireNotNull(config.findPrint(requireNotNull(presets["print"])))
            val originalMatrix = activeProcess.get("flush_volumes_matrix")
            val dialogMatrix = originalMatrix ?: requireNotNull(
                PrintConfigDef.getInstance().options["flush_volumes_matrix"]?.defaultValue,
            )
            val matrix = PrintConfigToolsLogic.flushMatrix(
                dialogMatrix,
                Prefs.getFilamentSlots().size,
                Prefs.MAX_FILAMENT_COLORS,
            )
            val changedCells = matrix.cells.toMutableList().also { it[1] = "315" }
            val expected = requireNotNull(
                PrintConfigToolsLogic.serializeFlushMatrix(matrix.side, changedCells),
            )

            compose.onNodeWithTag("workspace-header-flush-volumes").assertIsDisplayed().performClick()
            compose.onNodeWithTag("flush-cell-1").performTextClearance()
            compose.onNodeWithTag("flush-cell-1").performTextInput("315")
            compose.onNodeWithText(activity.getString(android.R.string.ok)).performClick()
            compose.waitUntil(timeoutMillis = 5_000) {
                ProfileSettingsRepository.pendingEntries(ConfigObject.PROFILE_LIST_PRINT)
                    .containsKey("flush_volumes_matrix")
            }

            assertEquals(expected, ProfileSettingsRepository.valueFor(
                ConfigObject.PROFILE_LIST_PRINT, activeProcess, "flush_volumes_matrix", true,
            ))
            assertEquals("Applying the matrix updates only the profile draft",
                originalMatrix, activeProcess.get("flush_volumes_matrix"))

            compose.onNodeWithTag("workspace-header-flush-volumes").assertIsDisplayed().performClick()
            compose.onNodeWithTag("flush-cell-1").performTextClearance()
            compose.onNodeWithTag("flush-cell-1").performTextInput("400")
            compose.onNodeWithText(activity.getString(android.R.string.cancel)).performClick()
            compose.waitForIdle()
            assertEquals("Cancel keeps the confirmed matrix and discards the second edit", expected,
                ProfileSettingsRepository.valueFor(
                    ConfigObject.PROFILE_LIST_PRINT, activeProcess, "flush_volumes_matrix", true,
                ))
            assertEquals("The stored profile remains unchanged until Save",
                originalMatrix, activeProcess.get("flush_volumes_matrix"))
        } finally {
            ProfileSettingsRepository.clearDraft(ConfigObject.PROFILE_LIST_PRINT)
            config.printConfigs.removeAll { it.title !in originalTitles }
            presets.put("printer", originalPrinter)
            presets.put("print", originalProcess)
            printerSelections.forEach { (title, selection) ->
                if (selection == null) PrinterProfileSelectionStore.write(title, null, null)
                else PrinterProfileSelectionStore.write(title, selection.process, selection.filament)
            }
            check(SliceBeam.saveConfig()) { "Could not restore the isolated process profile fixture" }
        }
    }

    @Test
    fun printerProfilePickerBackDismissesWithoutChangingAnyActivePreset() {
        val activity = compose.activity
        val config = SliceBeam.requireConfig()
        val printerBefore = requireNotNull(config.presets?.get("printer"))
        val processBefore = requireNotNull(config.presets?.get("print"))
        val filamentBefore = requireNotNull(config.presets?.get("filament"))
        val targetPrinter = "Anycubic 4Max Pro 0.4 nozzle"
        assertNotNull(config.findPrinter(targetPrinter))

        activity.runOnUiThread {
            (activity.navigationDelegate as MobileNavigationDelegate).switchToSlot(4)
        }
        compose.waitUntil(timeoutMillis = 15_000) {
            activity.navigationDelegate.getCurrentFragment() is PrinterConfigFragment
        }

        val profileButtonRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
        val printerProfileButton = compose.onAllNodes(hasText(printerBefore) and profileButtonRole).onFirst()
        // The fragment can be current before its Compose content has completed the workspace
        // destination transition. Wait for the profile control to actually be laid out before
        // interacting; otherwise this test flakes when it runs after heavier profile editors.
        compose.waitUntil(timeoutMillis = 5_000) {
            runCatching { printerProfileButton.assertIsDisplayed() }.isSuccess
        }
        printerProfileButton.assertIsDisplayed().performClick()
        compose.onAllNodesWithText(targetPrinter).onFirst().assertIsDisplayed()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()

        assertEquals("Back dismisses the printer picker without applying its highlighted alternative",
            printerBefore, config.presets?.get("printer"))
        assertEquals("Dismissing printer selection leaves the process preset unchanged",
            processBefore, config.presets?.get("print"))
        assertEquals("Dismissing printer selection leaves the filament preset unchanged",
            filamentBefore, config.presets?.get("filament"))
        compose.onAllNodes(hasText(printerBefore) and profileButtonRole).onFirst().assertIsDisplayed()
    }

    @Test
    fun selectingFilamentProfileThroughMenuUpdatesActiveProfileAndSelectedSlot() {
        val activity = compose.activity
        val config = SliceBeam.requireConfig()
        val printer = requireNotNull(config.presets?.get("printer"))
        val previousFilament = requireNotNull(config.presets?.get("filament"))
        val previousProcess = requireNotNull(config.presets?.get("print"))
        val previousSlot = SlotFilamentProfiles.selectedSlot
        val preferences = requireNotNull(Prefs.getPrefs())
        val previousSlotProfiles = preferences.getString("filament_slot_profiles", null)
        val previousPrinterSelection = PrinterProfileSelectionStore.read(printer)

        try {
            activity.runOnUiThread {
                (activity.navigationDelegate as MobileNavigationDelegate).switchToSlot(3)
            }
            compose.waitUntil(timeoutMillis = 15_000) {
                activity.navigationDelegate.getCurrentFragment() is FilamentConfigFragment
            }
            val fragment = activity.navigationDelegate.getCurrentFragment() as FilamentConfigFragment
            val target = activity.runOnUiThreadAndWait {
                val items = FilamentConfigFragment::class.java
                    .getDeclaredMethod("getItems", Boolean::class.javaPrimitiveType)
                    .apply { isAccessible = true }
                    .invoke(fragment, true) as List<*>
                items.asSequence()
                    .mapNotNull { item -> item?.javaClass?.getMethod("getTitle")?.invoke(item) as? String }
                    .firstOrNull { it != previousFilament }
            }
            assertNotNull("The bundled fixture should expose a second compatible filament profile", target)

            compose.onNodeWithText(previousFilament).assertIsDisplayed().performClick()
            compose.onNodeWithText(requireNotNull(target)).assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                config.presets?.get("filament") == target
            }

            assertEquals("Selecting a filament row updates the active profile", target, config.presets?.get("filament"))
            assertNotNull("The selected material remains a real profile in the configuration", config.findFilament(target))
            assertEquals("The active user palette slot records the selected profile", target,
                SlotFilamentProfiles.titleFor(previousSlot, null))
            assertEquals("Applying a profile choice leaves no editor draft behind", 0,
                ProfileSettingsRepository.pendingCountFor(ConfigObject.PROFILE_LIST_FILAMENT))
            compose.onNodeWithText(requireNotNull(target)).assertIsDisplayed()
        } finally {
            config.presets?.put("filament", previousFilament)
            config.presets?.put("print", previousProcess)
            if (previousSlotProfiles == null) {
                preferences.edit().remove("filament_slot_profiles").commit()
            } else {
                preferences.edit().putString("filament_slot_profiles", previousSlotProfiles).commit()
            }
            SlotFilamentProfiles.select(previousSlot)
            previousPrinterSelection?.let {
                PrinterProfileSelectionStore.write(printer, it.process, it.filament)
            } ?: PrinterProfileSelectionStore.write(printer, null, null)
            ProfileSettingsRepository.clearDraft(ConfigObject.PROFILE_LIST_FILAMENT)
            check(SliceBeam.saveConfig()) { "Could not restore the isolated test printer configuration" }
        }
    }

    @Test
    fun filamentNumericSettingCancelBackAndApplyOnlyChangeTheProfileDraft() {
        val activity = compose.activity
        val config = SliceBeam.requireConfig()
        val filamentTitle = requireNotNull(config.presets?.get("filament"))
        val storedFilament = requireNotNull(config.findFilament(filamentTitle))
        // filament_diameter is a basic numeric setting and is visible without enabling advanced filters.
        val option = requireNotNull(PrintConfigDef.getInstance().options["filament_diameter"])
        val optionTitle = Slic3rLocalization.getString(option.getLabel())
        val originalStoredValue = storedFilament.get(option.key)

        activity.runOnUiThread {
            (activity.navigationDelegate as MobileNavigationDelegate).switchToSlot(3)
        }
        compose.waitUntil(timeoutMillis = 15_000) {
            activity.navigationDelegate.getCurrentFragment() is FilamentConfigFragment
        }
        val draft = activity.runOnUiThreadAndWait {
            val fragment = activity.navigationDelegate.getCurrentFragment() as FilamentConfigFragment
            FilamentConfigFragment::class.java.getDeclaredMethod("getCurrentConfig")
                .apply { isAccessible = true }
                .invoke(fragment) as ConfigObject
        }
        val originalValue = ProfileConfigValueController.valueFor(
            ConfigObject.PROFILE_LIST_FILAMENT,
            draft,
            option.key,
            option.guiType,
            option.defaultValue,
            -1,
            false,
        ).orEmpty()
        val numericValue = originalValue.toDoubleOrNull()
        assertNotNull("The bundled filament profile must provide a numeric diameter", numericValue)
        val value = requireNotNull(numericValue)
        val candidate = when {
            value + 1 <= option.max -> value + 1
            value - 1 >= option.min -> value - 1
            else -> throw AssertionError("No nearby valid value is available for ${option.key}")
        }
        val changedValue = if (option.type == ConfigOptionDef.ConfigOptionType.INT) {
            candidate.toInt().toString()
        } else {
            candidate.toString()
        }

        try {
            ensureProfileCategoryExpanded("Filament")
            scrollToProfileSetting(option.key, optionTitle)

            fun clickSettingRow() {
                val matches = compose.onAllNodesWithText(optionTitle).fetchSemanticsNodes()
                assertTrue("The visible numeric option row should be present", matches.isNotEmpty())
                val rowIndex = matches.indices.maxByOrNull { matches[it].boundsInRoot.top }
                    ?: throw AssertionError("The numeric option row is not displayed")
                compose.onAllNodesWithText(optionTitle)[rowIndex].performClick()
                scrollToDialogTextEditor()
            }

            clickSettingRow()
            compose.onNode(hasSetTextAction()).performTextClearance()
            compose.onNode(hasSetTextAction()).performTextInput(changedValue)
            compose.onNodeWithText(activity.getString(android.R.string.cancel)).performClick()
            compose.waitForIdle()
            assertEquals("Cancel must leave the saved filament untouched", originalStoredValue,
                storedFilament.get(option.key))
            assertEquals("Cancel must not create a profile draft", 0,
                ProfileSettingsRepository.pendingCountFor(ConfigObject.PROFILE_LIST_FILAMENT))

            clickSettingRow()
            compose.onNode(hasSetTextAction()).performTextClearance()
            compose.onNode(hasSetTextAction()).performTextInput(changedValue)
            InstrumentationRegistry.getInstrumentation()
                .sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            compose.waitForIdle()
            assertEquals("Android Back must discard the unconfirmed edit", 0,
                ProfileSettingsRepository.pendingCountFor(ConfigObject.PROFILE_LIST_FILAMENT))
            assertEquals(originalStoredValue, storedFilament.get(option.key))

            clickSettingRow()
            compose.onNode(hasSetTextAction()).performTextClearance()
            compose.onNode(hasSetTextAction()).performTextInput(changedValue)
            compose.onNodeWithText(activity.getString(android.R.string.ok)).performClick()
            compose.waitUntil(timeoutMillis = 5_000) {
                ProfileSettingsRepository.pendingCountFor(ConfigObject.PROFILE_LIST_FILAMENT) > 0
            }
            val staged = ProfileSettingsRepository.valueFor(
                ConfigObject.PROFILE_LIST_FILAMENT, draft, option.key, true,
            )
            assertEquals("Apply updates the in-memory profile draft", changedValue, staged)
            assertEquals("Apply must not write the profile until the profile Save action is used",
                originalStoredValue, storedFilament.get(option.key))
        } finally {
            ProfileSettingsRepository.clearDraft(ConfigObject.PROFILE_LIST_FILAMENT)
        }
    }

    @Test
    fun savingFilamentNumericDraftPersistsOnlyAfterSaveConfirmation() {
        val activity = compose.activity
        val config = SliceBeam.requireConfig()
        val filamentTitle = requireNotNull(config.presets?.get("filament"))
        val storedFilament = requireNotNull(config.findFilament(filamentTitle))
        val storedDiameter = storedFilament.get("filament_diameter")
        val option = requireNotNull(PrintConfigDef.getInstance().options["filament_diameter"])
        val optionTitle = Slic3rLocalization.getString(option.getLabel())

        try {
            activity.runOnUiThread {
                (activity.navigationDelegate as MobileNavigationDelegate).switchToSlot(3)
            }
            compose.waitUntil(timeoutMillis = 15_000) {
                activity.navigationDelegate.getCurrentFragment() is FilamentConfigFragment
            }
            val draft = activity.runOnUiThreadAndWait {
                val fragment = activity.navigationDelegate.getCurrentFragment() as FilamentConfigFragment
                FilamentConfigFragment::class.java.getDeclaredMethod("getCurrentConfig")
                    .apply { isAccessible = true }
                    .invoke(fragment) as ConfigObject
            }
            val original = requireNotNull(draft.get("filament_diameter")).toDoubleOrNull()
            assertNotNull("The bundled filament profile must have a numeric diameter", original)
            val candidate = if (original!! + 1 <= option.max) original + 1 else original - 1
            val changed = if (option.type == ConfigOptionDef.ConfigOptionType.INT) {
                candidate.toInt().toString()
            } else {
                candidate.toString()
            }

            ensureProfileCategoryExpanded("Filament")
            scrollToProfileSetting(option.key, optionTitle)
            compose.onNodeWithText(optionTitle).assertIsDisplayed().performClick()
            scrollToDialogTextEditor()
            compose.onNode(hasSetTextAction()).performTextClearance()
            compose.onNode(hasSetTextAction()).performTextInput(changed)
            compose.onNodeWithText(activity.getString(android.R.string.ok)).performClick()
            compose.waitUntil(timeoutMillis = 5_000) {
                ProfileSettingsRepository.pendingCountFor(ConfigObject.PROFILE_LIST_FILAMENT) > 0
            }

            assertEquals("Editing should remain a draft until the profile Save action is confirmed",
                storedDiameter, storedFilament.get("filament_diameter"))
            compose.onNodeWithText(activity.getString(R.string.SettingsSave)).assertIsDisplayed().performClick()
            compose.onNodeWithText(activity.getString(R.string.SettingsSaveTitle)).assertIsDisplayed()
            compose.onNodeWithText(activity.getString(android.R.string.ok)).performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                ProfileSettingsRepository.pendingCountFor(ConfigObject.PROFILE_LIST_FILAMENT) == 0 &&
                    storedFilament.get("filament_diameter") == changed
            }

            assertEquals("Confirmed save applies the changed filament value", changed,
                storedFilament.get("filament_diameter"))
            assertEquals("Saving the filament profile keeps the active preset", filamentTitle,
                config.presets?.get("filament"))
        } finally {
            ProfileSettingsRepository.clearDraft(ConfigObject.PROFILE_LIST_FILAMENT)
            storedFilament.put("filament_diameter", storedDiameter)
            assertTrue("Restore the bundled filament fixture after the test", SliceBeam.saveConfig())
        }
    }

    private fun ensureProfileCategoryExpanded(categoryKey: String) {
        val localizedTitle = Slic3rLocalization.getString(categoryKey)
        val titles = listOf(
            categoryKey,
            localizedTitle,
            OrcaProfileGroupLocalizer.mobileGroup(categoryKey, "de"),
            OrcaProfileGroupLocalizer.mobileGroup(localizedTitle, "de"),
        ).distinct()
        val scrollableNodes = compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes()
        assertTrue("The profile settings list should expose a scroll action", scrollableNodes.isNotEmpty())
        for (scrollIndex in scrollableNodes.indices) {
            for (title in titles) {
                try {
                    compose.onAllNodes(hasScrollAction())[scrollIndex].performScrollToNode(hasText(title))
                } catch (_: AssertionError) {
                    // Category selectors and their settings rows can be separate scroll containers.
                }
            }
        }
        val expanded = compose.activity.getString(R.string.ProfileGroupStateExpanded)
        val collapsed = compose.activity.getString(R.string.ProfileGroupStateCollapsed)
        val candidates = titles.flatMap { title ->
            compose.onAllNodesWithText(title).fetchSemanticsNodes().map { title to it }
        }
        val stateful = candidates.firstOrNull {
            it.second.config.getOrElse(SemanticsProperties.StateDescription) { "" } in setOf(expanded, collapsed)
        }
        if (stateful != null) {
            if (stateful.second.config.getOrElse(SemanticsProperties.StateDescription) { "" } == expanded) return
            val matchingNodes = compose.onAllNodesWithText(stateful.first).fetchSemanticsNodes()
            val index = matchingNodes.indexOfFirst {
                it.config.getOrElse(SemanticsProperties.StateDescription) { "" } == collapsed
            }
            assertTrue("The collapsed profile category should remain in the UI", index >= 0)
            compose.onAllNodesWithText(stateful.first)[index].performScrollTo().performClick()
        } else {
            val category = candidates.firstOrNull { (_, node) ->
                node.config.contains(androidx.compose.ui.semantics.SemanticsActions.OnClick)
            } ?: throw AssertionError("Profile category '$categoryKey' was not present in the active category selector")
            compose.onNode(hasText(category.first).and(hasClickAction())).performClick()
        }
        if (stateful != null) {
            compose.waitUntil(timeoutMillis = 5_000) {
                titles.flatMap { compose.onAllNodesWithText(it).fetchSemanticsNodes() }.any {
                    it.config.getOrElse(SemanticsProperties.StateDescription) { "" } == expanded
                }
            }
        }
    }

    private fun scrollToProfileSetting(optionKey: String, title: String) {
        compose.onNodeWithTag("profile-settings-options-list")
            .performScrollToNode(hasTestTag("profile-option-key-$optionKey"))
        compose.onNodeWithText(title, substring = true).assertIsDisplayed()
    }

    private fun scrollToDialogTextEditor() {
        compose.onNode(hasSetTextAction()).performScrollTo().assertIsDisplayed()
    }

    private fun createInitializedActivityRule() = run {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val application = context.applicationContext as Application
        SliceBeam.INSTANCE = application as SliceBeam
        Prefs.init(application)
        Prefs.getPrefs()?.edit()
            ?.remove("crash")
            ?.putBoolean("converter_storage_first_prompt", true)
            ?.commit()
        Prefs.setLastCommit()
        SliceBeam.clearLiveDiffs()

        val config = context.assets.open("orca_profiles/Anycubic.ini").use(::Slic3rConfigWrapper)
        val printer = requireNotNull(config.findPrinter("Anycubic Kobra 3 0.4 nozzle"))
        // Orca renames factory presets over time. Seed the fixture from the printer's
        // own defaults so this integration test follows the current bundled database.
        val process = requireNotNull(config.findPrint(printer["default_print_profile"]))
        val filament = requireNotNull(config.findFilament(printer["default_filament_profile"]))
        config.presets = ConfigObject("presets").apply {
            put("printer", printer.title)
            put("print", process.title)
            put("filament", filament.title)
        }
        SliceBeam.CONFIG = config
        createAndroidComposeRule<MainActivity>()
    }
}

private fun <T> MainActivity.runOnUiThreadAndWait(block: () -> T): T {
    val result = java.util.concurrent.atomic.AtomicReference<T>()
    val failure = java.util.concurrent.atomic.AtomicReference<Throwable?>()
    val latch = java.util.concurrent.CountDownLatch(1)
    runOnUiThread {
        try {
            result.set(block())
        } catch (error: Throwable) {
            failure.set(error)
        } finally {
            latch.countDown()
        }
    }
    check(latch.await(10, java.util.concurrent.TimeUnit.SECONDS)) { "Timed out waiting for the UI thread" }
    failure.get()?.let { throw AssertionError("Could not inspect the print profile dropdown", it) }
    return requireNotNull(result.get())
}
