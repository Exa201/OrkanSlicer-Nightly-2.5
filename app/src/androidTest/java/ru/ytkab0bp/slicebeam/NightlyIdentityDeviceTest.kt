package ru.ytkab0bp.slicebeam

import android.content.Context
import android.content.pm.PackageManager
import android.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.ytkab0bp.slicebeam.utils.Prefs
import java.io.File

/** Guards Nightly's application identity and the private roots used for its state. */
@RunWith(AndroidJUnit4::class)
class NightlyIdentityDeviceTest {
    @Test
    fun applicationDataPreferencesModelCacheAndFileProviderArePackageScoped() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val nightlyId = "com.exa201.orkanslicer.nightly25"
        assertTrue(
            "The shipped Nightly ID, or its isolated instrumentation variant, must be used: ${context.packageName}",
            context.packageName == nightlyId || context.packageName == "$nightlyId.slicetest",
        )

        val dataRoot = File(context.applicationInfo.dataDir).canonicalFile
        assertTrue("Android's private data root must end in Nightly's package ID: $dataRoot",
            dataRoot.path.endsWith("/${context.packageName}"))
        assertEquals(dataRoot, context.filesDir.canonicalFile.parentFile)
        assertEquals(dataRoot, context.cacheDir.canonicalFile.parentFile)
        assertEquals(context.filesDir.canonicalFile, SliceBeam.getConfigFile().canonicalFile.parentFile)
        assertEquals(File(context.cacheDir, "model").canonicalFile, SliceBeam.getModelCacheDir().canonicalFile)

        val providerAuthority = "${context.packageName}.slicermore.files"
        val provider = context.packageManager.resolveContentProvider(providerAuthority, PackageManager.MATCH_DISABLED_COMPONENTS)
        assertNotNull("Nightly must resolve its own FileProvider authority", provider)
        assertEquals(context.packageName, provider?.packageName)

        val application = context.applicationContext as android.app.Application
        Prefs.init(application)
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val marker = "nightly_identity_device_test"
        val previous = preferences.getBoolean(marker, false)
        try {
            assertTrue("Preference marker must be persisted", preferences.edit().putBoolean(marker, true).commit())
            val preferenceFile = File(dataRoot, "shared_prefs/${context.packageName}_preferences.xml")
            assertTrue("Default preferences must be inside Nightly's private data root: $preferenceFile",
                preferenceFile.isFile)
        } finally {
            preferences.edit().putBoolean(marker, previous).commit()
        }
    }
}
