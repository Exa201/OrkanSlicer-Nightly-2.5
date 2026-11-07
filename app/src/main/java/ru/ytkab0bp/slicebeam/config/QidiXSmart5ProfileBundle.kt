package ru.ytkab0bp.slicebeam.config

import android.content.res.AssetManager
import org.json.JSONArray
import org.json.JSONObject
import ru.ytkab0bp.slicebeam.slic3r.Slic3rConfigWrapper
import ru.ytkab0bp.slicebeam.utils.IOUtils
import java.io.IOException

/** Loads Orca 2.5's complete, offline-bundled Qidi X-Smart 5 profile family. */
object QidiXSmart5ProfileBundle {
    const val ASSET = "orca_profiles/qidi_xsmart5_profiles.json"
    const val MODEL_NAME = "Qidi X-Smart 5"

    @Throws(IOException::class)
    fun load(assets: AssetManager): Slic3rConfigWrapper {
        val root = assets.open(ASSET).use { JSONObject(IOUtils.readString(it)) }
        val structure = JSONObject(root.getString("bundle_structure_json"))
        require(structure.optString("name") == MODEL_NAME) { "Unexpected bundled Qidi profile name" }

        val files = HashMap<String, JSONObject>()
        val fileArray = root.getJSONArray("files")
        for (index in 0 until fileArray.length()) {
            val file = fileArray.getJSONObject(index)
            files[file.getString("path")] = JSONObject(file.getString("content"))
        }

        val result = Slic3rConfigWrapper()
        readProfiles(
            structure.getJSONArray("printer_config"), files,
            Slic3rConfigWrapper.PRINTER_CONFIG_KEYS, ConfigObject.PROFILE_LIST_PRINTER,
            result.printerConfigs,
        )
        readProfiles(
            structure.getJSONArray("process_config"), files,
            Slic3rConfigWrapper.PRINT_CONFIG_KEYS, ConfigObject.PROFILE_LIST_PRINT,
            result.printConfigs,
        )
        readProfiles(
            structure.getJSONArray("filament_config"), files,
            Slic3rConfigWrapper.FILAMENT_CONFIG_KEYS, ConfigObject.PROFILE_LIST_FILAMENT,
            result.filamentConfigs,
        )

        resolveInheritance(result.printerConfigs)
        resolveInheritance(result.printConfigs)
        resolveInheritance(result.filamentConfigs)

        val model = root.getJSONObject("printer_model")
        val printerModel = ConfigObject(model.getString("name"))
        printerModel.put("name", model.getString("name"))
        printerModel.put("variants", model.getString("nozzle_diameter"))
        model.optString("default_materials").takeIf { it.isNotBlank() }?.let {
            printerModel.put("default_materials", it)
        }
        model.optString("model_id").takeIf { it.isNotBlank() }?.let {
            printerModel.put("model_id", it)
        }
        result.printerModels.add(printerModel)

        validate(result)
        return result
    }

    private fun readProfiles(
        paths: JSONArray,
        files: Map<String, JSONObject>,
        supportedKeys: List<String>,
        listType: Int,
        output: MutableList<ConfigObject>,
    ) {
        val entries = (0 until paths.length()).map { index ->
            val path = paths.getString(index)
            val json = files[path]
                ?: throw IOException("Bundled Qidi profile is missing: $path")
            Triple(path, json, json.optString("name").takeIf { it.isNotBlank() }
                ?: path.substringAfter('/').substringBeforeLast(".json"))
        }
        val bundleNames = entries.map { it.third }
        val profiles = entries.map { (_, json, title) ->
            val profile = IOUtils.configJsonToIni(json, typeFor(listType), supportedKeys, bundleNames)
            profile.setTitle(title)
            profile.profileListType = listType
            profile
        }
        output.addAll(profiles)
    }

    private fun typeFor(listType: Int): String = when (listType) {
        ConfigObject.PROFILE_LIST_PRINTER -> "machine"
        ConfigObject.PROFILE_LIST_PRINT -> "process"
        ConfigObject.PROFILE_LIST_FILAMENT -> "filament"
        else -> error("Unsupported profile list type: $listType")
    }

    private fun resolveInheritance(profiles: List<ConfigObject>) {
        val byName = profiles.mapNotNull { profile -> profile.title?.let { it to profile } }.toMap()
        val completed = HashSet<ConfigObject>()
        val resolving = HashSet<ConfigObject>()

        fun resolve(profile: ConfigObject) {
            if (profile in completed) return
            check(resolving.add(profile)) { "Cyclic Qidi profile inheritance at ${profile.title}" }
            val parentName = profile["inherits"]
            if (parentName != null) {
                val parent = byName[parentName]
                    ?: throw IOException("Missing bundled Qidi profile parent '$parentName' for ${profile.title}")
                resolve(parent)
                val resolved = HashMap(parent.values)
                resolved.putAll(profile.values)
                resolved.remove("inherits")
                profile.values = resolved
            }
            resolving.remove(profile)
            completed.add(profile)
        }

        profiles.forEach(::resolve)
    }

    private fun validate(config: Slic3rConfigWrapper) {
        val model = config.printerModels.singleOrNull { it.title == MODEL_NAME }
            ?: throw IOException("Qidi X-Smart 5 model metadata is missing")
        val variants = model["variants"].orEmpty().split(';').toSet()
        if (variants != setOf("0.2", "0.4", "0.6", "0.8")) {
            throw IOException("Unexpected Qidi X-Smart 5 nozzle variants: ${model["variants"]}")
        }
        val expectedMachines = variants.map { "$MODEL_NAME $it nozzle" }.toSet()
        if (!config.printerConfigs.mapNotNull { it.title }.containsAll(expectedMachines)) {
            throw IOException("One or more Qidi X-Smart 5 nozzle profiles are missing")
        }
        val defaultMachine = config.findPrinter("$MODEL_NAME 0.4 nozzle")
            ?: throw IOException("Qidi X-Smart 5 0.4 mm printer profile is missing")
        if (config.findPrint(defaultMachine["default_print_profile"]) == null ||
            config.findFilament(defaultMachine["default_filament_profile"]?.substringBefore(';')) == null
        ) {
            throw IOException("Qidi X-Smart 5 default process or filament profile is missing")
        }
    }

    /** Merge the built-in presets into a vendor profile or an existing private user config. */
    fun mergeInto(target: Slic3rConfigWrapper, bundled: Slic3rConfigWrapper): Boolean {
        var changed = false
        for (model in bundled.printerModels) {
            if (target.printerModels.none { it.title == model.title }) {
                target.printerModels.add(ConfigObject(model))
                changed = true
            }
        }
        changed = mergeByTitle(target.printerConfigs, bundled.printerConfigs) || changed
        changed = mergeByTitle(target.filamentConfigs, bundled.filamentConfigs) || changed
        for (profile in bundled.printConfigs) {
            if (target.printConfigs.none {
                    it.title == profile.title && it["compatible_printers"] == profile["compatible_printers"]
                }
            ) {
                target.printConfigs.add(ConfigObject(profile))
                changed = true
            }
        }
        return changed
    }

    private fun mergeByTitle(target: MutableList<ConfigObject>, incoming: List<ConfigObject>): Boolean {
        var changed = false
        for (profile in incoming) {
            if (target.none { it.title == profile.title }) {
                target.add(ConfigObject(profile))
                changed = true
            }
        }
        return changed
    }
}
