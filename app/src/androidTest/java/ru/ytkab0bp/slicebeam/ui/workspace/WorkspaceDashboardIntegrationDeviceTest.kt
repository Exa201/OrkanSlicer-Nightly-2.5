package ru.ytkab0bp.slicebeam.ui.workspace

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.ytkab0bp.slicebeam.MainActivity
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.SliceBeam
import ru.ytkab0bp.slicebeam.config.ConfigObject
import ru.ytkab0bp.slicebeam.fragment.BedFragment
import ru.ytkab0bp.slicebeam.slic3r.Slic3rConfigWrapper
import ru.ytkab0bp.slicebeam.utils.Prefs

/** Verifies dashboard controls through the actual BedFragment -> WorkspaceShell call path. */
@RunWith(AndroidJUnit4::class)
class WorkspaceDashboardIntegrationDeviceTest {
    private var previousConfig: Slic3rConfigWrapper? = null
    private var previousPreferences: Map<String, Any?> = emptyMap()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule
    val compose = createInitializedActivityRule()

    @Test
    fun printToolShortcutsAreVisibleAndOpenTheirActualEditors() {
        val activity = compose.activity
        assertTrue("the app should open on the real workspace", activity.navigationDelegate.getCurrentFragment() is BedFragment)

        listOf(
            "workspace-header-filament-action",
            "workspace-header-prime-tower",
            "workspace-header-flush-volumes",
            "workspace-header-plate-action",
            "workspace-header-printer-action",
        ).forEach { tag -> compose.onNodeWithTag(tag).assertIsDisplayed() }

        compose.onNodeWithTag("workspace-header-prime-tower").performClick()
        compose.onNodeWithText(context.getString(R.string.PrimeTowerPosition)).assertIsDisplayed()
        compose.runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()

        compose.onNodeWithTag("workspace-header-flush-volumes").performClick()
        compose.onNodeWithText(context.getString(R.string.FlushVolumes)).assertIsDisplayed()
    }

    private fun createInitializedActivityRule() = run {
        val application = context.applicationContext as Application
        SliceBeam.INSTANCE = application as SliceBeam
        Prefs.init(application)
        val preferences = requireNotNull(Prefs.getPrefs())
        previousPreferences = preferences.all.filterKeys {
            it == "crash" || it == "converter_storage_first_prompt" || it == "last_commit"
        }
        preferences.edit()
            ?.remove("crash")
            ?.putBoolean("converter_storage_first_prompt", true)
            ?.commit()
        Prefs.setLastCommit()
        previousConfig = SliceBeam.CONFIG
        SliceBeam.clearLiveDiffs()
        val config = context.assets.open("orca_profiles/Anycubic.ini").use(::Slic3rConfigWrapper)
        val printer = requireNotNull(config.findPrinter("Anycubic Kobra 3 0.4 nozzle"))
        // The factory database is versioned upstream and process/filament preset
        // titles are printer-scoped. Seed the workspace from the selected printer's
        // own defaults instead of names from the previous database snapshot.
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

    @After
    fun restoreApplicationState() {
        SliceBeam.CONFIG = previousConfig
        val preferences = Prefs.getPrefs() ?: return
        val editor = preferences.edit()
            .remove("crash")
            .remove("converter_storage_first_prompt")
            .remove("last_commit")
        previousPreferences.forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        editor.commit()
    }
}
