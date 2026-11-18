package ru.ytkab0bp.slicebeam.boot

import android.util.Log
import org.json.JSONObject
import ru.ytkab0bp.slicebeam.SliceBeam
import ru.ytkab0bp.slicebeam.config.ConfigObject
import ru.ytkab0bp.slicebeam.config.FactoryProfileDatabaseUpdater
import ru.ytkab0bp.slicebeam.config.FactoryProfileDatabaseRefreshPolicy
import ru.ytkab0bp.slicebeam.config.Kobra3V2ProfileMigration
import ru.ytkab0bp.slicebeam.config.ProfileConfigSchemaMigrator
import ru.ytkab0bp.slicebeam.config.ProfileConfigSnapshot
import ru.ytkab0bp.slicebeam.slic3r.Slic3rConfigWrapper
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream

open class LoadSlic3rConfigTask : BootTask(Runnable {
    val configFile = SliceBeam.recoverConfigFile()
    SliceBeam.getCurrentConfigFile().delete()
    if (configFile.exists()) {
        try {
            val migration = ProfileConfigSchemaMigrator.migrate(configFile)
            if (migration.migrated) {
                Log.i(
                    "LoadSlic3rConfigTask",
                    "Migrated stored profiles to schema ${migration.schemaVersion}; backup: ${migration.backupFile?.name}"
                )
            }
        } catch (e: Exception) {
            Log.e("LoadSlic3rConfigTask", "Stored profile migration failed; loading the existing file", e)
        }
        try {
            SliceBeam.CONFIG = Slic3rConfigWrapper(configFile)
            SliceBeam.CONFIG?.loadWarnings?.forEach { warning -> Log.w("LoadSlic3rConfigTask", warning.toString()) }
            refreshFactoryProfileDatabase()
            sanitizeKobra3V2StartGcode()
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }
}) {
    init {
        onWorker()
    }

    private companion object {
        private const val PROFILE_DATABASE_REVISION_ASSET = "orca_profiles/UPSTREAM_VERSION.txt"
        private const val PROFILE_DATABASE_INDEX_ASSET = "orca_profiles/model_vendor_index.json"
        private const val PROFILE_DATABASE_LEGACY_ASSET = "orca_profiles/legacy_factory_profiles.zip"
        private const val PROFILE_DATABASE_REVISION_KEY = "orca_profile_database_revision"

        /** Updates factory profiles for configured printer families; edited/custom profiles are retained. */
        private fun refreshFactoryProfileDatabase() {
            val config = SliceBeam.CONFIG ?: return
            try {
                val assets = SliceBeam.requireInstance().assets
                val revision = assets.open(PROFILE_DATABASE_REVISION_ASSET).bufferedReader().use { it.readText().trim() }
                val modelVendors = assets.open(PROFILE_DATABASE_INDEX_ASSET).bufferedReader().use {
                    JSONObject(it.readText())
                }
                val activeModels = config.printerConfigs.mapNotNull { it["printer_model"] }.toSet()
                val vendorFiles = activeModels
                    .mapNotNull { model -> modelVendors.optString(model).takeIf(String::isNotBlank) }
                    .toSet()
                if (vendorFiles.isEmpty()) {
                    // A fresh profile has no configured printer yet. Do not mark the database
                    // current before SetupActivity has installed a printer bundle, or the next
                    // launch will skip the import and leave the process list effectively empty.
                    val previousRevision = config.presets?.get(PROFILE_DATABASE_REVISION_KEY)
                    config.presets?.values?.remove(PROFILE_DATABASE_REVISION_KEY)
                    if (previousRevision != null && !SliceBeam.saveConfig()) {
                        config.presets?.put(PROFILE_DATABASE_REVISION_KEY, previousRevision)
                        Log.w("LoadSlic3rConfigTask", "Could not defer Orca profile refresh until a printer is configured")
                    }
                    return
                }
                if (FactoryProfileDatabaseRefreshPolicy.shouldSkip(
                        config.presets?.get(PROFILE_DATABASE_REVISION_KEY),
                        revision,
                        vendorFiles.size,
                    )
                ) return

                val previousBundles = readLegacyFactoryBundles(vendorFiles)
                val snapshot = ProfileConfigSnapshot(config)
                var changed = false
                try {
                    vendorFiles.forEach { filename ->
                        val latest = assets.open("orca_profiles/$filename").use(::Slic3rConfigWrapper)
                        val previous = previousBundles[filename]?.let { bytes -> Slic3rConfigWrapper(bytes.inputStream()) }
                        changed = FactoryProfileDatabaseUpdater.refresh(config, latest, previous) || changed
                    }
                    if (config.presets == null) config.presets = ConfigObject()
                    config.presets?.put(PROFILE_DATABASE_REVISION_KEY, revision)
                    if (!SliceBeam.saveConfig()) {
                        snapshot.restore()
                        Log.w("LoadSlic3rConfigTask", "Orca profile database update could not be saved; restored previous profiles")
                        return
                    }
                    Log.i("LoadSlic3rConfigTask", "Updated configured printer profiles from Orca $revision (profile data changed: $changed)")
                } catch (error: Exception) {
                    snapshot.restore()
                    throw error
                }
            } catch (error: Exception) {
                Log.w("LoadSlic3rConfigTask", "Could not refresh factory profiles from the bundled Orca database", error)
            }
        }

        private fun readLegacyFactoryBundles(filenames: Set<String>): Map<String, ByteArray> {
            val found = HashMap<String, ByteArray>()
            ZipInputStream(SliceBeam.requireInstance().assets.open(PROFILE_DATABASE_LEGACY_ASSET)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory && entry.name in filenames) {
                        val output = ByteArrayOutputStream()
                        zip.copyTo(output)
                        found[entry.name] = output.toByteArray()
                    }
                    zip.closeEntry()
                }
            }
            return found
        }

        /** Keep the Kobra 3 V2's historical fixed PA command out of its generated start G-code. */
        private fun sanitizeKobra3V2StartGcode() {
            val config = SliceBeam.CONFIG ?: return
            val previous = mutableListOf<Pair<ConfigObject, String>>()
            config.printerConfigs.forEach { printer ->
                if (!Kobra3V2ProfileMigration.isKobra3V2Profile(printer.title, printer.get("printer_model"))) return@forEach
                val old = printer.get("machine_start_gcode").orEmpty()
                val cleaned = Kobra3V2ProfileMigration.removeLegacyPressureAdvance(old)
                if (cleaned != old) {
                    previous += printer to old
                    printer.put("machine_start_gcode", cleaned)
                }
            }
            if (previous.isNotEmpty() && !SliceBeam.saveConfig()) {
                previous.forEach { (printer, startGcode) -> printer.put("machine_start_gcode", startGcode) }
                Log.w("LoadSlic3rConfigTask", "Could not save the Kobra 3 V2 Pressure Advance profile migration")
            } else if (previous.isNotEmpty()) {
                Log.i("LoadSlic3rConfigTask", "Removed fixed legacy pressure advance overrides from Kobra 3 V2 profiles")
            }
        }
    }
}
