package ru.ytkab0bp.slicebeam

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import android.util.SparseArray
import android.view.View
import androidx.compose.runtime.MutableIntState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference
import ru.ytkab0bp.slicebeam.config.ConfigObject
import ru.ytkab0bp.slicebeam.config.ThreeMfProjectSettings
import ru.ytkab0bp.slicebeam.config.TemporaryProjectSettings
import ru.ytkab0bp.slicebeam.fragment.SettingsFragment
import ru.ytkab0bp.slicebeam.fragment.BedFragment
import ru.ytkab0bp.slicebeam.fragment.MakerFragment
import ru.ytkab0bp.slicebeam.components.KromacutEditorMenu
import ru.ytkab0bp.slicebeam.navigation.NavigationDelegate
import ru.ytkab0bp.slicebeam.navigation.MobileNavigationDelegate
import ru.ytkab0bp.slicebeam.slic3r.Slic3rConfigWrapper
import ru.ytkab0bp.slicebeam.slic3r.OrcaLanguage
import ru.ytkab0bp.slicebeam.theme.ThemesRepo
import ru.ytkab0bp.slicebeam.render.DevicePerformancePolicy
import ru.ytkab0bp.slicebeam.utils.Prefs

/** Exercises an app-settings action through MainActivity's real workspace destination. */
@RunWith(AndroidJUnit4::class)
class SettingsMenuActionDeviceTest {
    @get:Rule
    val compose = createInitializedActivityRule()

    @Test
    fun viewportQualityCanBeChangedAndAutomaticModeUsesDevicePolicy() {
        val activity = compose.activity
        val prefs = requireNotNull(Prefs.getPrefs())
        val originalOverride = Prefs.getRenderScaleOverridePercent()
        assertTrue("The test app must retain Nightly's isolated package identity",
            activity.packageName.startsWith("com.exa201.orkanslicer.nightly25"))
        try {
            prefs.edit().putFloat("render_scale", 0.5f).commit()
            Prefs.init(activity.application as Application)
            navigateToSettings(activity)

            compose.onNodeWithText(activity.getString(R.string.SettingsInterface))
                .performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithText(activity.getString(R.string.SettingsRenderQuality))
                .assertIsDisplayed().performClick()
            compose.onNodeWithText("50%").assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 5_000) { Prefs.getRenderScaleOverridePercent() == 50 }
            assertEquals(0.5f, Prefs.getRenderScale(), 0f)
            assertEquals("The legacy override is cleared at preference initialization", false,
                prefs.contains("render_scale"))

            compose.onNodeWithText(activity.getString(R.string.SettingsRenderQuality))
                .assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 10_000) { !Prefs.getGlRendererName().isNullOrBlank() }
            val rendererName = Prefs.getGlRendererName()
            assertTrue("The active GLES renderer should be identified", !rendererName.isNullOrBlank())
            val automaticScale = DevicePerformancePolicy.defaultRenderScale(Prefs.isLowRamDevice(), rendererName)
            val automaticPercent = (automaticScale * 100).toInt()
            compose.onNodeWithText(activity.getString(R.string.SettingsRenderQualityAutomatic, automaticPercent))
                .assertIsDisplayed().performClick()
            assertEquals(
                "Automatic mode follows the current device performance policy",
                automaticScale,
                Prefs.getRenderScale(),
                0f,
            )
        } finally {
            prefs.edit().remove("render_scale").putInt("viewport_render_scale_percent", originalOverride).commit()
            Prefs.init(activity.application as Application)
        }
    }

    @Test
    fun viewportFrameRateCapCanBeChangedAndAutomaticModeUsesGpuPolicy() {
        val activity = compose.activity
        val prefs = requireNotNull(Prefs.getPrefs())
        val originalOverride = Prefs.getViewportFpsCapOverride()
        try {
            navigateToSettings(activity)
            compose.onNodeWithText(activity.getString(R.string.SettingsInterface))
                .performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithText(activity.getString(R.string.SettingsViewportFpsTitle))
                .assertIsDisplayed().performClick()
            compose.onNodeWithText(activity.getString(R.string.SettingsViewportFpsValue, 45))
                .assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 5_000) { Prefs.getViewportFpsCapOverride() == 45 }
            assertEquals(45, Prefs.getViewportFpsCap())

            compose.onNodeWithText(activity.getString(R.string.SettingsViewportFpsTitle))
                .assertIsDisplayed().performClick()
            val automaticCap = DevicePerformancePolicy.defaultViewportFpsCap(
                Prefs.isLowRamDevice(), Prefs.getGlRendererName(),
            )
            compose.onNodeWithText(activity.getString(R.string.SettingsViewportFpsAutomatic, automaticCap))
                .assertIsDisplayed().performClick()
            assertEquals(0, Prefs.getViewportFpsCapOverride())
            assertEquals(automaticCap, Prefs.getViewportFpsCap())
        } finally {
            prefs.edit().putInt("viewport_fps_cap", originalOverride).commit()
            Prefs.init(activity.application as Application)
        }
    }

    @Test
    fun translucentFilamentPreviewCanBeToggledAndPersists() {
        val activity = compose.activity
        val original = Prefs.isTranslucentFilamentPreviewEnabled()
        try {
            navigateToSettings(activity)
            compose.onNodeWithText(activity.getString(R.string.SettingsInterface))
                .performScrollTo().assertIsDisplayed().performClick()
            val setting = activity.getString(R.string.SettingsTranslucentFilamentPreview)
            compose.onNodeWithText(setting).performScrollTo().assertIsDisplayed().performClick()
            compose.waitUntil(timeoutMillis = 5_000) { !Prefs.isTranslucentFilamentPreviewEnabled() }
            compose.onNodeWithText(activity.getString(R.string.SettingsPreviewDisabled)).assertIsDisplayed()

            compose.onNodeWithText(setting).performClick()
            compose.waitUntil(timeoutMillis = 5_000) { Prefs.isTranslucentFilamentPreviewEnabled() }
            compose.onNodeWithText(activity.getString(R.string.SettingsPreviewEnabled)).assertIsDisplayed()
        } finally {
            Prefs.setTranslucentFilamentPreviewEnabled(original)
        }
    }

    @Test
    fun themeSelectionIsRemovedAndDarkModeRemainsFixed() {
        val activity = compose.activity
        navigateToSettings(activity)
        compose.onNodeWithText(activity.getString(R.string.SettingsInterface))
            .performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText(activity.getString(R.string.SettingsInterfaceTheme)).assertDoesNotExist()
        compose.onNodeWithText(activity.getString(R.string.SettingsInterfaceColor)).assertDoesNotExist()
        assertEquals(Prefs.ThemeMode.DARK, Prefs.getThemeMode())
    }

    @Test
    fun textScaleSelectionUpdatesTheLiveSettingsLayoutAndCanBeRestored() {
        val activity = compose.activity
        val originalScale = Prefs.getUiTextScale()
        val targetScale = if (originalScale < 1.5f) 1.5f else 1.4f
        val targetLabel = "${(targetScale * 100).toInt()}%"

        try {
            TemporaryProjectSettings.clear()
            TemporaryProjectSettings.activate(
                ThreeMfProjectSettings(
                    sourceName = "responsive-layout-fixture.3mf",
                    process = mapOf("layer_height" to "0.2"),
                    filaments = emptyList(),
                    filamentTypes = emptyMap(),
                    printSettingsImported = true,
                    filamentSettingsImported = false,
                    printerSettingsImported = false,
                ),
            )
            navigateToSettings(activity)
            showPreviewWorkspace(activity)
            val viewportBeforeZoom = workspaceViewportBounds(activity)
            val regionsBeforeZoom = workspaceRegionBounds()
            navigateToSettings(activity)
            compose.onNodeWithText(activity.getString(R.string.SettingsInterface))
                .performScrollTo().assertIsDisplayed().performClick()

            val workspaceRevisionBefore = workspaceThemeRevision(activity)
            val sectionHeading = compose.onNodeWithText(activity.getString(R.string.SettingsInterface))
            sectionHeading.assertIsDisplayed()
            val beforeHeight = sectionHeading.fetchSemanticsNode().size.height

            compose.onNodeWithText(activity.getString(R.string.SettingsInterfaceTextSize))
                .performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithTag("settings-ui-text-scale-slider")
                .assertIsDisplayed()
                .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                    assertTrue("Slider accepts an accessible target value", setProgress(targetScale))
                }
            compose.onNodeWithTag("settings-ui-text-scale-value").assertTextEquals(targetLabel)
            assertEquals("Previewing a value does not persist it before confirmation", originalScale, Prefs.getUiTextScale(), 0f)
            compose.onNodeWithTag("settings-ui-text-scale-preview-card").assertIsDisplayed()
            compose.onNodeWithTag("settings-ui-text-scale-reset").performClick()
            compose.onNodeWithTag("settings-ui-text-scale-value").assertTextEquals("100%")
            compose.onNodeWithTag("settings-ui-text-scale-slider")
                .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                    assertTrue("Slider accepts the selected value after reset", setProgress(targetScale))
                }
            compose.onNodeWithTag("settings-ui-text-scale-apply").performClick()
            compose.waitUntil(timeoutMillis = 5_000) { Prefs.getUiTextScale() == targetScale }
            compose.waitForIdle()

            val afterHeight = compose.onNodeWithText(activity.getString(R.string.SettingsInterface))
                .fetchSemanticsNode().size.height
            assertEquals(targetScale, Prefs.getUiTextScale(), 0f)
            showPreviewWorkspace(activity)
            val regionsAfterZoom = workspaceRegionBounds()
            assertEquals("Workspace rows must keep the same geometry at different app text zoom values",
                regionsBeforeZoom, regionsAfterZoom)
            assertEquals(
                "Changing app text zoom must not move or resize the build plate viewport. Regions before=$regionsBeforeZoom, after=$regionsAfterZoom",
                viewportBeforeZoom,
                workspaceViewportBounds(activity),
            )
            navigateToSettings(activity)
            assertTrue("The already-created workspace must receive the global theme/text-scale invalidation",
                workspaceThemeRevision(activity) > workspaceRevisionBefore)
            if (targetScale > originalScale) {
                assertTrue("The live Compose section title must grow with the selected text scale ($beforeHeight → $afterHeight)",
                    afterHeight > beforeHeight)
            } else {
                assertTrue("The live Compose section title must shrink with the selected text scale ($beforeHeight → $afterHeight)",
                    afterHeight < beforeHeight)
            }

            navigateToSettings(activity)
            compose.onNodeWithText(activity.getString(R.string.SettingsInterfaceTextSize))
                .performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithTag("settings-ui-text-scale-slider")
                .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                    assertTrue("Slider accepts a disposable preview value", setProgress(0.9f))
                }
            compose.onNodeWithTag("settings-ui-text-scale-cancel").performClick()
            compose.waitForIdle()
            assertEquals("Cancel discards the preview and preserves the applied value", targetScale, Prefs.getUiTextScale(), 0f)

            compose.onNodeWithText(activity.getString(R.string.SettingsInterfaceTextSize))
                .performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithTag("settings-ui-text-scale-slider").assertIsDisplayed()
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            compose.waitForIdle()
            assertEquals("Android Back closes the preview without changing the applied text size", targetScale, Prefs.getUiTextScale(), 0f)
            showPreviewWorkspace(activity)
            assertEquals("App text zoom must not move or resize the build plate viewport after Back navigation",
                viewportBeforeZoom, workspaceViewportBounds(activity))
        } finally {
            activity.runOnUiThread {
                TemporaryProjectSettings.clear()
                Prefs.setUiTextScale(originalScale)
                ThemesRepo.invalidate(activity)
            }
        }
    }

    @Test
    fun aboutActionLaunchesTheAboutOnlySetupRoute() {
        val mainActivity = compose.activity
        navigateToSettings(mainActivity)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val setupMonitor = instrumentation.addMonitor(SetupActivity::class.java.name, null, false)

        compose.onNodeWithText(mainActivity.getString(R.string.SettingsAppInformation))
            .performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText(mainActivity.getString(R.string.SettingsAbout))
            .performScrollTo().assertIsDisplayed().performClick()

        val aboutActivity = instrumentation.waitForMonitorWithTimeout(setupMonitor, 10_000) as? SetupActivity
            ?: throw AssertionError("About action must launch SetupActivity")
        instrumentation.removeMonitor(setupMonitor)
        assertEquals(true, aboutActivity.intent.getBooleanExtra(SetupActivity.EXTRA_ABOUT, false))
        assertTrue("The original settings route remains on the main activity",
            mainActivity.navigationDelegate.getCurrentFragment() is SettingsFragment)
    }

    @Test
    fun addPrinterActionLaunchesPresetSetupWithoutChangingTheActivePrinter() {
        val mainActivity = compose.activity
        val config = requireNotNull(SliceBeam.CONFIG)
        val activePrinter = config.presets?.get("printer")
        navigateToSettings(mainActivity)
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        compose.onNodeWithText(mainActivity.getString(R.string.SettingsDevices))
            .performScrollTo().assertIsDisplayed().performClick()
        val setupMonitor = instrumentation.addMonitor(SetupActivity::class.java.name, null, false)
        compose.onNodeWithText(mainActivity.getString(R.string.SettingsAddPrinter))
            .performScrollTo().assertIsDisplayed().performClick()

        val setupActivity = instrumentation.waitForMonitorWithTimeout(setupMonitor, 10_000) as? SetupActivity
            ?: throw AssertionError("Add printer action must launch SetupActivity")
        instrumentation.removeMonitor(setupMonitor)
        assertTrue("Add printer must enter the preset setup flow", setupActivity.intent.getBooleanExtra(
            SetupActivity.EXTRA_ADD_PRINTER, false,
        ))
        assertEquals("Opening printer setup must not switch the active printer",
            activePrinter, config.presets?.get("printer"))
    }

    @Test
    fun appResetActionCanBeCancelledOrDismissedWithBackWithoutChangingConfiguration() {
        val activity = compose.activity
        val config = requireNotNull(SliceBeam.CONFIG)
        val originalPrinter = config.presets?.get("printer")
        val originalProcess = config.presets?.get("print")
        val originalFilament = config.presets?.get("filament")
        navigateToSettings(activity)

        fun openResetConfirmation() {
            compose.onNodeWithText(activity.getString(R.string.SettingsResetToDefault))
                .performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithText(activity.getString(R.string.SettingsResetToDefaultTitle)).assertIsDisplayed()
        }
        fun assertConfigurationUnchanged() {
            assertTrue("Reset cancellation must retain the same active config", SliceBeam.CONFIG === config)
            assertEquals(originalPrinter, config.presets?.get("printer"))
            assertEquals(originalProcess, config.presets?.get("print"))
            assertEquals(originalFilament, config.presets?.get("filament"))
        }

        openResetConfirmation()
        compose.onNodeWithText(activity.getString(android.R.string.cancel)).performClick()
        compose.waitForIdle()
        assertConfigurationUnchanged()

        openResetConfirmation()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        assertConfigurationUnchanged()
        compose.onNodeWithText(activity.getString(R.string.SettingsResetToDefault)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun themeAndAccentAreFixedAndNoLongerOfferedAsSettings() {
        val activity = compose.activity
        navigateToSettings(activity)
        compose.onNodeWithText(activity.getString(R.string.SettingsInterface))
            .performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText(activity.getString(R.string.SettingsInterfaceTheme)).assertDoesNotExist()
        compose.onNodeWithText(activity.getString(R.string.SettingsInterfaceColor)).assertDoesNotExist()
        assertEquals(Prefs.ThemeMode.DARK, Prefs.getThemeMode())
        assertEquals(0xff1dbdff.toInt(), Prefs.getAccentColor())
    }

    @Test
    fun appLanguagePickerListsPackagedOrcaLanguagesAndPersistsSelection() {
        val activity = compose.activity
        val originalLanguage = Prefs.getAppLanguage()
        val selectedLanguage = OrcaLanguage.all.first { it.tag == "en" }
        try {
            navigateToSettings(activity)
            compose.onNodeWithText(activity.getString(R.string.SettingsInterface))
                .performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithText(activity.getString(R.string.SettingsInterfaceLanguage))
                .performScrollTo().assertIsDisplayed().performClick()

            OrcaLanguage.all.forEach { language ->
                val options = compose.onAllNodesWithText(language.nativeName)
                val optionCount = options.fetchSemanticsNodes().size
                assertTrue("Packaged language ${language.tag} should be listed", optionCount > 0)
                options.get(optionCount - 1).performScrollTo().assertIsDisplayed()
            }
            val selectedOptions = compose.onAllNodesWithText(selectedLanguage.nativeName)
            selectedOptions.get(selectedOptions.fetchSemanticsNodes().lastIndex).performScrollTo().performClick()
            compose.waitUntil(timeoutMillis = 5_000) { Prefs.getAppLanguage() == selectedLanguage.tag }
            assertEquals("Choosing a packaged language persists its Orca tag", selectedLanguage.tag,
                Prefs.getAppLanguage())
        } finally {
            activity.runOnUiThread {
                Prefs.setAppLanguage(originalLanguage)
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(originalLanguage))
            }
        }
    }

    @Test
    fun makerSettingsRouteOpensKromacutAndBackRestoresWorkspaceRenderer() {
        val activity = compose.activity
        navigateToSettings(activity)
        compose.onNodeWithText(activity.getString(R.string.SettingsAppMakerTitle))
            .performScrollTo().assertIsDisplayed().performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            activity.navigationDelegate.getCurrentFragment() is MakerFragment
        }
        compose.onNodeWithText(activity.getString(R.string.MakerKromacut))
            .performScrollTo().assertIsDisplayed().performClick()

        lateinit var bed: BedFragment
        var rendererBefore: Any? = null
        var modelBefore: Any? = null
        val expectedToolId = KromacutEditorMenu::class.java.name
        compose.waitUntil(timeoutMillis = 30_000) {
            val current = activity.navigationDelegate.getCurrentFragment()
            if (current !is BedFragment) return@waitUntil false
            bed = current
            val controller = BedFragment::class.java.getDeclaredField("workspaceController")
                .apply { isAccessible = true }.get(bed)
            val activeToolId = controller.javaClass.getMethod("getActiveToolId").invoke(controller) as? String
            if (activeToolId == expectedToolId) {
                rendererBefore = bed.glView?.renderer
                modelBefore = bed.glView?.renderer?.model
                bed.glView?.visibility == View.GONE
            } else false
        }

        activity.runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(timeoutMillis = 30_000) {
            val controller = BedFragment::class.java.getDeclaredField("workspaceController")
                .apply { isAccessible = true }.get(bed)
            controller.javaClass.getMethod("getActiveToolId").invoke(controller) == null &&
                bed.glView?.visibility == View.VISIBLE
        }
        assertSame("KromaCut dismissal must retain the native GL renderer", rendererBefore, bed.glView?.renderer)
        assertEquals("Returning from KromaCut must retain the same native model",
            modelBefore, bed.glView?.renderer?.model)
    }

    private fun navigateToSettings(activity: MainActivity) {
        activity.runOnUiThread {
            (activity.navigationDelegate as MobileNavigationDelegate).switchToSlot(5)
        }
        waitForSettingsContent(activity)
    }

    private fun showPreviewWorkspace(activity: MainActivity) {
        activity.runOnUiThread {
            (activity.navigationDelegate as MobileNavigationDelegate).switchToSlot(0)
        }
        compose.waitUntil(timeoutMillis = 15_000) {
            val bed = activity.navigationDelegate.getCurrentFragment() as? BedFragment ?: return@waitUntil false
            val view = bed.glView ?: return@waitUntil false
            val fragmentView = bed.getView() ?: return@waitUntil false
            // switchToSlot updates currentFragment before its spring transition has
            // finished. Measuring during that transition reports translated GLView
            // bounds and makes a stable-layout check flaky.
            val container = activity.navigationDelegate.getContainerView()
            view.visibility == View.VISIBLE && view.width > 0 && view.height > 0 &&
                container?.childCount == 1 &&
                fragmentView.translationX == 0f && fragmentView.translationY == 0f &&
                fragmentView.alpha >= 0.999f
        }
    }

    private fun waitForSettingsContent(activity: MainActivity) {
        val label = activity.getString(R.string.SettingsInterface)
        compose.waitUntil(timeoutMillis = 15_000) {
            activity.navigationDelegate.getCurrentFragment() is SettingsFragment &&
                compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
        }
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
        SliceBeam.hasUpdateInfo = false
        SliceBeam.clearLiveDiffs()

        val config = context.assets.open("orca_profiles/Anycubic.ini").use(::Slic3rConfigWrapper)
        val printer = requireNotNull(config.findPrinter("Anycubic Kobra 3 0.4 nozzle"))
        val process = requireNotNull(config.findPrint("0.16mm High Quality"))
        val filament = requireNotNull(config.findFilament("Anycubic Generic PLA"))
        config.presets = ConfigObject("presets").apply {
            put("printer", printer.title)
            put("print", process.title)
            put("filament", filament.title)
        }
        SliceBeam.CONFIG = config
        createAndroidComposeRule<MainActivity>()
    }

    private fun workspaceThemeRevision(activity: MainActivity): Int {
        val stacksField = NavigationDelegate::class.java.getDeclaredField("fragmentStack").apply {
            isAccessible = true
        }
        val stacks = stacksField.get(activity.navigationDelegate) as SparseArray<*>
        val bedStack = stacks.get(0) as java.util.Stack<*>
        val bedFragment = bedStack.peek() as BedFragment
        val revisionField = BedFragment::class.java.getDeclaredField("workspaceThemeRevision\$delegate").apply {
            isAccessible = true
        }
        return (revisionField.get(bedFragment) as MutableIntState).intValue
    }

    private fun workspaceViewportBounds(activity: MainActivity): List<Int> {
        val result = AtomicReference<List<Int>>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val stacksField = NavigationDelegate::class.java.getDeclaredField("fragmentStack").apply {
                isAccessible = true
            }
            val stacks = stacksField.get(activity.navigationDelegate) as SparseArray<*>
            val bedStack = stacks.get(0) as java.util.Stack<*>
            val bedFragment = bedStack.peek() as BedFragment
            val viewport = requireNotNull(bedFragment.glView)
            val location = IntArray(2)
            viewport.getLocationInWindow(location)
            result.set(listOf(location[0], location[1], viewport.width, viewport.height))
        }
        return requireNotNull(result.get())
    }

    private fun workspaceRegionBounds(): Map<String, String> = listOf(
        "workspace-project-settings-area",
        "workspace-header",
        "workspace-renderer-area",
        "workspace-navigation",
    ).associateWith { tag ->
        compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.toString()
    }

}
