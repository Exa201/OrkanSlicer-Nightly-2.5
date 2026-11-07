package ru.ytkab0bp.slicebeam.utils

import android.net.Uri
import android.provider.MediaStore
import android.text.TextUtils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import ru.ytkab0bp.slicebeam.SliceBeam
import ru.ytkab0bp.slicebeam.config.ConfigObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Shared file and profile-import helpers. Static entry points preserve the Java call sites. */
class IOUtils {
    companion object {
    @JvmField
    // Generators and slicing can hold large model/bitmap objects. Keep concurrent native
    // work bounded so a burst of user actions cannot create an unbounded number of threads.
    var IO_POOL: ExecutorService = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors().coerceIn(2, 3),
    ) { task ->
        Thread(task, "OrkanSlicer-IO").apply { priority = Thread.NORM_PRIORITY - 1 }
    }

    @JvmStatic
    fun getDisplayName(uri: Uri?): String? {
        if (uri == null) return null
        val resolver = SliceBeam.requireInstance().contentResolver
        try {
            val cursor = resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
            if (cursor != null) {
                try {
                    val nameColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    if (nameColumn >= 0 && cursor.moveToFirst()) {
                        val fileName = cursor.getString(nameColumn)
                        if (!fileName.isNullOrBlank()) return fileName
                    }
                } finally {
                    cursor.close()
                }
            }
        } catch (_: RuntimeException) {
            // Some content providers and file URIs do not support metadata queries.
        }

        var segment = uri.lastPathSegment ?: return null
        if (segment.isEmpty()) return null
        val separator = segment.lastIndexOf('/')
        if (separator >= 0) segment = segment.substring(separator + 1)
        return segment
    }

    @JvmStatic
    @Throws(IOException::class)
    fun readString(input: InputStream): String = readString(input, false)

    @JvmStatic
    @Throws(IOException::class)
    fun readString(input: InputStream, close: Boolean): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(10_240)
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            output.write(buffer, 0, count)
        }
        if (close) input.close()
        return String(output.toByteArray(), StandardCharsets.UTF_8)
    }

    private fun configJsonToString(value: Any): String {
        if (value is JSONArray) {
            val result = StringBuilder()
            for (index in 0 until value.length()) {
                if (result.isNotEmpty()) result.append(',')
                result.append(value.getString(index))
            }
            return result.toString()
        }
        return value.toString()
    }

    private fun mapOrcaConfigKey(key: String): String = when (key) {
        // Orca's bundled JSONs use the plural form; the engine key (and whitelist) is singular.
        "chamber_temperatures" -> "chamber_temperature"
        else -> ConfigObject.legacyKey(key)
    }

    @Throws(IOException::class, JSONException::class, MissingProfileException::class)
    private fun downloadProfilesRecursively(
        vendor: String,
        type: String,
        profile: String,
        supportedKeys: List<String>
    ): ConfigObject {
        val connection = URL(
            "https://raw.githubusercontent.com/SoftFever/OrcaSlicer/main/resources/profiles/$vendor/$type/$profile.json"
        ).openConnection() as HttpURLConnection
        try {
            if (connection.responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                throw MissingProfileException(profile)
            }
            val json = JSONObject(readString(connection.inputStream, true))
            val config = ConfigObject()
            val inherit = json.optString("inherits")
            if (!TextUtils.isEmpty(inherit)) {
                val parentProfile = json.getString("inherits") ?: throw JSONException("Profile parent is not a string")
                val parent = downloadProfilesRecursively(vendor, type, parentProfile, supportedKeys)
                for ((key, value) in parent.values) {
                    if (supportedKeys.contains(key) && key != "thumbnails") {
                        config.values[key] = value
                    }
                }
            }

            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key == "print_settings_id" || key == "filament_settings_id" || key == "printer_settings_id") {
                    config.setTitle(json.getString(key))
                } else if (key != "inherits") {
                    config.put(key, configJsonToString(json.get(key)))
                }
            }
            return config
        } finally {
            connection.disconnect()
        }
    }

    @JvmStatic
    @Throws(JSONException::class, IOException::class, MissingProfileException::class)
    fun configJsonToIni(
        json: JSONObject,
        type: String,
        supportedKeys: List<String>,
        inBundle: List<String>
    ): ConfigObject {
        val config = ConfigObject()
        when (type) {
            "process" -> config.profileListType = ConfigObject.PROFILE_LIST_PRINT
            "filament" -> config.profileListType = ConfigObject.PROFILE_LIST_FILAMENT
            "machine" -> config.profileListType = ConfigObject.PROFILE_LIST_PRINTER
        }

        val inherit = json.optString("inherits")
        if (!TextUtils.isEmpty(inherit)) {
            if (inBundle.contains(inherit)) {
                // Resolve bundle parents after all profiles have been read.
                config.put("inherits", inherit)
            } else if (!inherit.contains(' ')) {
                throw MissingProfileException(inherit)
            } else {
                val vendor = when {
                    inherit.contains("@BBL") -> "BBL"
                    type == "process" -> {
                        val start = inherit.indexOf('@') + 1
                        val end = inherit.indexOf(' ', start).let { if (it == -1) inherit.length else it }
                        inherit.substring(start, end)
                    }
                    else -> inherit.substring(0, inherit.indexOf(' '))
                }.let { if (it == "Generic" || inherit.startsWith("Bambu Lab")) "BBL" else it }

                var inherited: ConfigObject? = null
                try {
                    inherited = downloadProfilesRecursively(vendor, type, inherit, supportedKeys)
                } catch (error: MissingProfileException) {
                    if (!inherit.endsWith("@System")) throw error
                }

                if (inherited != null) {
                    for ((key, inheritedValue) in inherited.values) {
                        val mappedKey = mapOrcaConfigKey(key)
                        if (supportedKeys.contains(mappedKey)) {
                            if (mappedKey == "start_filament_gcode" || mappedKey == "end_filament_gcode" ||
                                mappedKey == "start_gcode" || mappedKey == "end_gcode") {
                                var value = inheritedValue
                                if (mappedKey == "start_filament_gcode" && config.values.containsKey("start_filament_gcode")) {
                                    value = config.get("start_filament_gcode") + "\n" + value
                                }
                                value = value.replace("nozzle_temperature_initial_layer", "first_layer_temperature")
                                    .replace("bed_temperature_initial_layer_single", "first_layer_bed_temperature")
                                config.values[mappedKey] = value
                            } else if (mappedKey != "thumbnails") {
                                config.values[mappedKey] = inheritedValue
                            }
                        }
                    }
                }
            }
        }

        val keys = json.keys()
        // Orca's source profile files use `name`; exported config bundles may also carry
        // *_settings_id. The human-readable preset name must win (printer_settings_id is
        // commonly only the vendor name, e.g. "Qidi").
        json.optString("name").takeIf { it.isNotBlank() }?.let(config::setTitle)
        while (keys.hasNext()) {
            val key = keys.next()
            if (key == "print_settings_id" || key == "filament_settings_id" || key == "printer_settings_id") {
                var title = json.getString(key)
                if (title.length > 3 && title.startsWith("[\"") && title.endsWith("\"]")) {
                    title = title.substring(2, title.length - 2)
                }
                config.setTitle(title)
            } else if (key != "inherits") {
                val mappedKey = mapOrcaConfigKey(key)
                if (supportedKeys.contains(mappedKey)) {
                    var value = configJsonToString(json.get(key))
                    if (mappedKey == "start_filament_gcode" || mappedKey == "end_filament_gcode" ||
                        mappedKey == "start_gcode" || mappedKey == "end_gcode") {
                        value = value.replace("nozzle_temperature_initial_layer", "first_layer_temperature")
                            .replace("bed_temperature_initial_layer_single", "first_layer_bed_temperature")
                    }
                    config.put(mappedKey, value)
                }
            }
        }
        return config
    }

    }

    open class MissingProfileException(@JvmField val profile: String) : Exception() {
        override fun toString(): String = "MissingProfileException{profile='$profile'}"
    }
}
