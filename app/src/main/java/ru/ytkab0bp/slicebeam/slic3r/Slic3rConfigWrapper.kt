package ru.ytkab0bp.slicebeam.slic3r

import ru.ytkab0bp.slicebeam.BuildConfig
import ru.ytkab0bp.slicebeam.config.ConfigObject
import ru.ytkab0bp.slicebeam.config.ProfileConfigLoadWarning
import ru.ytkab0bp.slicebeam.ui.settings.OrcaProcessSettingsCatalog
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.io.UnsupportedEncodingException
import java.util.ArrayList
import java.util.Arrays
import java.util.HashMap
import java.util.regex.Pattern

open class Slic3rConfigWrapper {
    companion object {
        @JvmField
        val BLACKLISTED_SYMBOLS: String = "<>[]:/\\|?*\""

        @JvmField
        val PRINT_CONFIG_KEYS: MutableList<String> = buildPrintConfigKeys()

        private fun buildPrintConfigKeys(): MutableList<String> {
            val keys = arrayListOf("layer_height",
                "first_layer_height",
                "perimeters",
                "spiral_vase",
                "slice_closing_radius",
                "slicing_mode",
                "top_solid_layers",
                "top_solid_min_thickness",
                "bottom_solid_layers",
                "bottom_solid_min_thickness",
                "extra_perimeters",
                "extra_perimeters_on_overhangs",
                "avoid_crossing_curled_overhangs",
                "avoid_crossing_perimeters",
                "thin_walls",
                "overhangs",
                "seam_position",
                "staggered_inner_seams",
                "external_perimeters_first",
                "fill_density",
                "fill_pattern",
                "top_fill_pattern",
                "bottom_fill_pattern",
                "infill_every_layers",
                "solid_infill_every_layers",
                "fill_angle",
                "bridge_angle",
                "solid_infill_below_area",
                "only_retract_when_crossing_perimeters",
                "infill_first",
                "ironing",
                "ironing_type",
                "ironing_flowrate",
                "ironing_speed",
                "ironing_spacing",
                "max_print_speed",
                "max_volumetric_speed",
                "avoid_crossing_perimeters_max_detour",
                "fuzzy_skin",
                "fuzzy_skin_thickness",
                "fuzzy_skin_point_dist",
                "max_volumetric_extrusion_rate_slope_positive",
                "max_volumetric_extrusion_rate_slope_negative",
                "perimeter_speed",
                "small_perimeter_speed",
                "external_perimeter_speed",
                "infill_speed",
                "solid_infill_speed",
                "enable_dynamic_overhang_speeds",
                "overhang_speed_0",
                "overhang_speed_1",
                "overhang_speed_2",
                "overhang_speed_3",
                "top_solid_infill_speed",
                "support_material_speed",
                "support_material_xy_spacing",
                "support_material_interface_speed",
                "bridge_speed",
                "gap_fill_speed",
                "gap_fill_enabled",
                "travel_speed",
                "travel_speed_z",
                "first_layer_speed",
                "first_layer_speed_over_raft",
                "perimeter_acceleration",
                "infill_acceleration",
                "external_perimeter_acceleration",
                "top_solid_infill_acceleration",
                "solid_infill_acceleration",
                "travel_acceleration",
                "wipe_tower_acceleration",
                "bridge_acceleration",
                "first_layer_acceleration",
                "first_layer_acceleration_over_raft",
                "default_acceleration",
                "skirts",
                "skirt_distance",
                "skirt_height",
                "draft_shield",
                "min_skirt_length",
                "brim_width",
                "brim_separation",
                "brim_type",
                "support_material",
                "support_material_auto",
                "support_material_threshold",
                "support_material_enforce_layers",
                "raft_layers",
                "raft_first_layer_density",
                "raft_first_layer_expansion",
                "raft_contact_distance",
                "raft_expansion",
                "support_material_pattern",
                "support_material_with_sheath",
                "support_material_spacing",
                "support_material_closing_radius",
                "support_material_style",
                "support_material_synchronize_layers",
                "support_material_angle",
                "support_material_interface_layers",
                "support_material_bottom_interface_layers",
                "support_material_interface_pattern",
                "support_material_interface_spacing",
                "support_material_interface_contact_loops",
                "support_material_contact_distance",
                "support_material_bottom_contact_distance",
                "support_material_buildplate_only",
                "support_tree_angle",
                "support_tree_angle_slow",
                "support_tree_branch_diameter",
                "support_tree_branch_diameter_angle",
                "support_tree_branch_diameter_double_wall",
                "support_tree_top_rate",
                "support_tree_branch_distance",
                "support_tree_tip_diameter",
                "dont_support_bridges",
                "thick_bridges",
                "notes",
                "complete_objects",
                "extruder_clearance_radius",
                "extruder_clearance_height",
                "gcode_comments",
                "gcode_label_objects",
                "output_filename_format",
                "post_process",
                "gcode_substitutions",
                "perimeter_extruder",
                "infill_extruder",
                "solid_infill_extruder",
                "support_material_extruder",
                "support_material_interface_extruder",
                "ooze_prevention",
                "standby_temperature_delta",
                "interface_shells",
                "extrusion_width",
                "first_layer_extrusion_width",
                "perimeter_extrusion_width",
                "external_perimeter_extrusion_width",
                "infill_extrusion_width",
                "solid_infill_extrusion_width",
                "top_infill_extrusion_width",
                "support_material_extrusion_width",
                "infill_overlap",
                "infill_anchor",
                "infill_anchor_max",
                "bridge_flow_ratio",
                "elefant_foot_compensation",
                "xy_size_compensation",
                "resolution",
                "gcode_resolution",
                "enable_prime_tower",
                "wipe_tower",
                "wipe_tower_x",
                "wipe_tower_y",
                "wipe_tower_width",
                "wipe_tower_cone_angle",
                "wipe_tower_rotation_angle",
                "wipe_tower_brim_width",
                "wipe_tower_bridging",
                "single_extruder_multi_material_priming",
                "mmu_segmented_region_max_width",
                "mmu_segmented_region_interlocking_depth",
                "wipe_tower_extruder",
                "wipe_tower_no_sparse_layers",
                "wipe_tower_extra_flow",
                "wipe_tower_extra_spacing",
                "flush_volumes_vector",
                "flush_volumes_matrix",
                "flush_multiplier",
                "compatible_printers",
                "compatible_printers_condition",
                "inherits",
                "perimeter_generator",
                "wall_transition_length",
                "wall_transition_filter_deviation",
                "wall_transition_angle",
                "wall_distribution_count",
                "min_feature_size",
                "min_bead_width",
                "top_one_perimeter_type",
                "only_one_perimeter_first_layer",
                "only_one_wall_top",
                "initial_layer_infill_speed",
                "elefant_foot_compensation_layers",
                "enable_arc_fitting",
                "wall_direction",
                "slowdown_for_curled_perimeters",
                "xy_hole_compensation",
                "default_jerk",
                "outer_wall_jerk",
                "inner_wall_jerk",
                "infill_jerk",
                "top_surface_jerk",
                "initial_layer_jerk",
                "travel_jerk")
            for (key in OrcaProcessSettingsCatalog.processKeys()) {
                if (!keys.contains(key)) keys.add(key)
            }
            for (key in ConfigObject.printConfigKeyAliases()) {
                if (!keys.contains(key)) keys.add(key)
            }
            return keys
        }

        @JvmField
        val FILAMENT_CONFIG_KEYS: List<String> = Arrays.asList("filament_colour",
                "filament_diameter",
                "filament_type",
                "filament_soluble",
                "filament_notes",
                "filament_max_volumetric_speed",
                "filament_max_external_volumetric_speed",
                "extrusion_multiplier",
                "filament_density",
                "filament_cost",
                "filament_loading_speed",
                "filament_loading_speed_start",
                "filament_unloading_speed",
                "filament_unloading_speed_start",
                "filament_toolchange_delay",
                "filament_cooling_moves",
                "filament_stamping_loading_speed",
                "filament_stamping_distance",
                "filament_cooling_initial_speed",
                "filament_cooling_final_speed",
                "filament_ramming_parameters",
                "filament_minimal_purge_on_wipe_tower",
                "filament_multitool_ramming",
                "filament_multitool_ramming_volume",
                "filament_multitool_ramming_flow",
                "temperature",
                "idle_temperature",
                "first_layer_temperature",
                "bed_temperature",
                "first_layer_bed_temperature",
                "fan_always_on",
                "cooling",
                "min_fan_speed",
                "max_fan_speed",
                "bridge_fan_speed",
                "disable_fan_first_layers",
                "full_fan_speed_layer",
                "additional_fan_full_speed_layer",
                "fan_below_layer_time",
                "slowdown_below_layer_time",
                "min_print_speed",
                "dont_slow_down_outer_wall",
                "start_filament_gcode",
                "end_filament_gcode",
                "chamber_temperature",
                "activate_chamber_temp_control",
                "filament_retract_length",
                "filament_retract_lift",
                "filament_retract_lift_above",
                "filament_retract_lift_below",
                "filament_retract_lift_enforce",
                "filament_retract_speed",
                "filament_deretract_speed",
                "filament_retract_restart_extra",
                "filament_retract_before_travel",
                "filament_retract_layer_change",
                "filament_wipe",
                "filament_retract_before_wipe",
                "filament_is_support",
                "temperature_vitrification",
                "nozzle_temperature_range_low",
                "nozzle_temperature_range_high",
                "filament_change_length",
                "filament_shrink",
                "close_additional_fan_first_x_layers",
                "first_x_layer_fan_speed",
                "internal_bridge_fan_speed",
                "support_material_interface_fan_speed",
                "ironing_fan_speed",
                "filament_ironing_flow",
                "filament_ironing_spacing",
                "filament_ironing_inset",
                "filament_ironing_speed",
                "activate_air_filtration",
                "activate_air_filtration_during_print",
                "activate_air_filtration_on_completion",
                "during_print_exhaust_fan_speed",
                "complete_print_exhaust_fan_speed",
                "enable_pressure_advance",
                "pressure_advance",
                "adaptive_pressure_advance",
                "adaptive_pressure_advance_model",
                "adaptive_pressure_advance_overhangs",
                "adaptive_pressure_advance_bridges",
                "filament_vendor",
                "compatible_prints",
                "compatible_prints_condition",
                "compatible_printers",
                "compatible_printers_condition",
                "inherits",
                "filament_shrinkage_compensation_z",
                "cool_plate_temp",
                "cool_plate_temp_initial_layer",
                "eng_plate_temp",
                "eng_plate_temp_initial_layer",
                "textured_plate_temp",
                "textured_plate_temp_initial_layer",
                "textured_cool_plate_temp",
                "textured_cool_plate_temp_initial_layer",
                "supertack_plate_temp",
                "supertack_plate_temp_initial_layer",
                "additional_cooling_fan_speed",
                "overhang_fan_threshold",
                "enable_overhang_bridge_fan")

        @JvmField
        val PRINTER_CONFIG_KEYS: List<String> = Arrays.asList("printer_technology",
                "bed_shape",
                "bed_exclude_volumes",
                "bed_exclude_volume_mode",
                "extruder_bed_exclude_volumes",
                "extruder_clearance_radius",
                "extruder_clearance_dist_to_rod",
                "extruder_clearance_height_to_lid",
                "extruder_clearance_height_to_rod",
                "bed_custom_texture",
                "bed_custom_model",
                "z_offset",
                "gcode_flavor",
                "use_relative_e_distances",
                "use_firmware_retraction",
                "fan_kickstart",
                "fan_speedup_time",
                "fan_speedup_overhangs",
                "machine_load_filament_time",
                "machine_unload_filament_time",
                "host_type",
                "print_host",
                "printhost_apikey",
                "printhost_cafile",
                "single_extruder_multi_material",
                "physical_extruder_map",
                "start_gcode",
                "end_gcode",
                "before_layer_gcode",
                "layer_gcode",
                "toolchange_gcode",
                "color_change_gcode",
                "change_extrusion_role_gcode",
                "pause_print_gcode",
                "template_custom_gcode",
                "printing_by_object_gcode",
                "printer_vendor",
                "printer_model",
                "printer_variant",
                "printer_notes",
                "cooling_tube_retraction",
                "cooling_tube_length",
                "high_current_on_filament_swap",
                "parking_pos_retraction",
                "extra_loading_move",
                "multimaterial_purging",
                "max_print_height",
                "default_print_profile",
                "inherits",
                "silent_mode",
                "emit_machine_limits_to_gcode",
                "thumbnails",
                "thumbnails_format",
                "machine_max_acceleration_extruding",
                "machine_max_acceleration_retracting",
                "machine_max_acceleration_travel",
                "machine_max_acceleration_x",
                "machine_max_acceleration_y",
                "machine_max_acceleration_z",
                "machine_max_acceleration_e",
                "machine_max_feedrate_x",
                "machine_max_feedrate_y",
                "machine_max_feedrate_z",
                "machine_max_feedrate_e",
                "machine_min_extruding_rate",
                "machine_min_travel_rate",
                "machine_max_junction_deviation",
                "long_retractions_when_cut",
                "retraction_distances_when_cut",
                "machine_max_jerk_x",
                "machine_max_jerk_y",
                "machine_max_jerk_z",
                "machine_max_jerk_e",
                "nozzle_diameter",
                "min_layer_height",
                "max_layer_height",
                "extruder_offset",
                "extruder_colour",
                "retract_length",
                "retract_lift",
                "retract_lift_above",
                "retract_lift_below",
                "retract_lift_enforce",
                "z_hop_types",
                "travel_slope",
                "retract_speed",
                "deretract_speed",
                "retract_before_travel",
                "retract_layer_change",
                "wipe",
                "wipe_distance",
                "retract_before_wipe",
                "retract_restart_extra",
                "retract_length_toolchange",
                "retract_restart_extra_toolchange",
                "default_filament_profile",
                "enable_filament_ramming",
                "curr_bed_type",
                "support_chamber_temp_control",
                "support_air_filtration")

        @JvmField
        val PHYSICAL_PRINTER_CONFIG_KEYS: List<String> = Arrays.asList("preset_name",
                "preset_names",
                "printer_technology",
                "host_type",
                "print_host",
                "printhost_apikey",
                "printhost_cafile",
                "printhost_port",
                "printhost_authorization_type",
                "printhost_user",
                "printhost_password",
                "printhost_ssl_ignore_revoke")
    }

    @JvmField
    var printConfigs: MutableList<ConfigObject> = ArrayList()

    @JvmField
    var printerConfigs: MutableList<ConfigObject> = ArrayList()

    @JvmField
    var filamentConfigs: MutableList<ConfigObject> = ArrayList()

    @JvmField
    var physicalPrintersConfigs: MutableList<ConfigObject> = ArrayList()

    @JvmField
    var printerModels: MutableList<ConfigObject> = ArrayList()

    @JvmField
    var presets: ConfigObject? = null

    /** Non-fatal profile bundle issues discovered while reading, such as missing inheritance parents. */
    @JvmField
    val loadWarnings: MutableList<ProfileConfigLoadWarning> = ArrayList()

    @JvmField
    var vendor: ConfigObject? = null

    private fun knownKeysForSection(category: String?): List<String>? = when (category) {
        "print" -> PRINT_CONFIG_KEYS
        "filament" -> FILAMENT_CONFIG_KEYS
        "printer" -> PRINTER_CONFIG_KEYS
        "physical_printer" -> PHYSICAL_PRINTER_CONFIG_KEYS
        else -> null
    }

    constructor()

    @Throws(IOException::class)
    constructor(file: File) : this() {
        FileInputStream(file).use(::readFromStream)
    }

    @Throws(IOException::class)
    constructor(input: InputStream) : this() {
        readFromStream(input)
    }

    open fun importPrint(obj: ConfigObject) = importInto(printConfigs, obj)

    open fun importPrinter(obj: ConfigObject) = importInto(printerConfigs, obj)

    open fun importFilament(obj: ConfigObject) = importInto(filamentConfigs, obj)

    open fun importInto(list: MutableList<ConfigObject>, obj: ConfigObject) {
        for (existing in list) {
            if (existing.title == obj.title) {
                existing.values.clear()
                existing.values.putAll(obj.values)
                return
            }
        }
        list.add(obj)
    }

    open fun findFilament(key: String?): ConfigObject? = filamentConfigs.firstOrNull { it.title == key }

    open fun findPrinterVariant(model: String?, variant: String?): ConfigObject? =
        printerConfigs.firstOrNull { it["printer_model"] == model && it["printer_variant"] == variant }

    open fun findPrint(key: String?): ConfigObject? = printConfigs.firstOrNull { it.title == key }

    open fun findPrinter(key: String?): ConfigObject? = printerConfigs.firstOrNull { it.title == key }

    private fun serializeList(builder: StringBuilder, key: String, list: List<ConfigObject>) {
        for (config in list) {
            builder.append("[").append(key).append(":").append(config.title).append("]\n")
            for ((optionKey, rawValue) in config.values) {
                val value = ConfigObject.normalizeSerializedValue(optionKey, rawValue) ?: continue
                builder.append(optionKey).append(" = ").append(value.replace("\n", "\\n")).append("\n")
            }
            builder.append("\n")
        }
    }

    open fun serialize(): String {
        val builder = StringBuilder()
        builder.append("# generated by Slice Beam ").append(BuildConfig.VERSION_NAME).append("\n\n")
        serializeList(builder, "printer", printerConfigs)
        serializeList(builder, "print", printConfigs)
        serializeList(builder, "filament", filamentConfigs)

        val selectedPresets = presets
        if (selectedPresets != null) {
            builder.append("[presets]\n")
            for ((key, rawValue) in selectedPresets.values) {
                val value = rawValue.replace("\n", "\\n")
                builder.append(key).append(" = ").append(value).append("\n")
            }
        }
        return builder.toString()
    }

    @Throws(IOException::class)
    open fun readFromStream(input: InputStream) {
        // saveConfig writes UTF-8 explicitly; do not let profile names (including the
        // printer-scoping separator) change when a saved config is reloaded on Windows.
        val reader = BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8))
        var currentPrintConfig: ConfigObject? = null
        var currentPrinterConfig: ConfigObject? = null
        var currentFilamentConfig: ConfigObject? = null
        var currentPhysicalPrinterConfig: ConfigObject? = null
        var explicitObject: ConfigObject? = null
        var recognizedConfig = false
        var ignoreUnknownSection = false
        var currentSectionCategory: String? = null
        val parentMap = HashMap<String, ConfigObject>()

        while (true) {
            val line = reader.readLine() ?: break
            if (line.startsWith("#")) continue

            if (line.startsWith("[") && line.endsWith("]")) {
                if (line == "[obsolete_presets]") {
                    explicitObject = ConfigObject()
                    ignoreUnknownSection = false
                    currentSectionCategory = null
                    recognizedConfig = true
                    continue
                }

                if (!line.contains(":") && line != "[presets]" && line != "[vendor]") {
                    throw UnsupportedEncodingException("Failed to decode config category: $line")
                }
                if (currentPrintConfig != null || currentPrinterConfig != null ||
                    currentFilamentConfig != null || currentPhysicalPrinterConfig != null
                ) {
                    throw UnsupportedEncodingException("Failed to decode config: explicit category in combined profile!")
                }

                if (line == "[presets]") {
                    explicitObject = ConfigObject().also { presets = it }
                    ignoreUnknownSection = false
                    currentSectionCategory = null
                    recognizedConfig = true
                    continue
                }
                if (line == "[vendor]") {
                    explicitObject = ConfigObject().also { vendor = it }
                    ignoreUnknownSection = false
                    currentSectionCategory = null
                    recognizedConfig = true
                    continue
                }

                val section = line.substring(1, line.length - 1)
                val split = Pattern.compile(":").split(section)
                val category = split[0]
                val name = split[1]
                currentSectionCategory = category
                explicitObject = when (category) {
                    "printer_model" -> ConfigObject(name).also {
                        printerModels.add(it)
                        recognizedConfig = true
                    }
                    "print" -> ConfigObject(name).also {
                        it.profileListType = ConfigObject.PROFILE_LIST_PRINT
                        printConfigs.add(it)
                        recognizedConfig = true
                    }
                    "printer" -> ConfigObject(name).also {
                        it.profileListType = ConfigObject.PROFILE_LIST_PRINTER
                        printerConfigs.add(it)
                        recognizedConfig = true
                    }
                    "physical_printer" -> ConfigObject(name).also {
                        physicalPrintersConfigs.add(it)
                        recognizedConfig = true
                    }
                    "filament" -> ConfigObject(name).also {
                        it.profileListType = ConfigObject.PROFILE_LIST_FILAMENT
                        filamentConfigs.add(it)
                        recognizedConfig = true
                    }
                    // Unknown sections must not continue writing into the preceding profile.
                    else -> null
                }
                ignoreUnknownSection = category !in setOf(
                    "printer_model", "print", "printer", "physical_printer", "filament"
                )
                explicitObject?.let { parentMap[name] = it }
            }

            val separator = line.indexOf(" = ")
            if (separator != -1) {
                if (ignoreUnknownSection) continue
                val key = line.substring(0, separator)
                val value = line.substring(separator + 3).trim { it <= ' ' }.replace("\\n", "\n")
                if (key == "arc_fitting") continue
                val normalizedValue = if (key == "thumbnails") {
                    value.replace(Regex(", \\d+x\\d+/COLPIC"), "")
                } else value

                val target = explicitObject
                if (target != null) {
                    val knownKeys = knownKeysForSection(currentSectionCategory)
                    val canonicalKey = ConfigObject.migrateKey(key)
                    if (knownKeys != null && key !in knownKeys && canonicalKey !in knownKeys &&
                        ConfigObject.legacyKey(canonicalKey) !in knownKeys
                    ) {
                        val warning = ProfileConfigLoadWarning.UnknownOption(target.title.orEmpty(), key)
                        if (warning !in loadWarnings) loadWarnings.add(warning)
                    }
                    target.put(key, normalizedValue)
                } else {
                    if (key == "printer_settings_id") {
                        if (currentPrinterConfig == null) currentPrinterConfig = ConfigObject()
                        currentPrinterConfig!!.setTitle(normalizedValue)
                    }
                    if (key == "print_settings_id") {
                        if (currentPrintConfig == null) currentPrintConfig = ConfigObject()
                        currentPrintConfig!!.setTitle(normalizedValue)
                    }
                    if (key == "filament_settings_id") {
                        if (currentFilamentConfig == null) currentFilamentConfig = ConfigObject()
                        currentFilamentConfig!!.setTitle(normalizedValue)
                    }

                    val legacyKey = ConfigObject.legacyKey(key)
                    if (key in PRINT_CONFIG_KEYS || legacyKey in PRINT_CONFIG_KEYS) {
                        recognizedConfig = true
                        if (currentPrintConfig == null) currentPrintConfig = ConfigObject()
                        currentPrintConfig!!.put(key, normalizedValue)
                    }
                    if (key in FILAMENT_CONFIG_KEYS || legacyKey in FILAMENT_CONFIG_KEYS) {
                        recognizedConfig = true
                        if (currentFilamentConfig == null) currentFilamentConfig = ConfigObject()
                        currentFilamentConfig!!.put(key, normalizedValue)
                    }
                    if (key in PRINTER_CONFIG_KEYS || legacyKey in PRINTER_CONFIG_KEYS) {
                        recognizedConfig = true
                        if (currentPrinterConfig == null) currentPrinterConfig = ConfigObject()
                        currentPrinterConfig!!.put(key, normalizedValue)
                    }
                    if (key in PHYSICAL_PRINTER_CONFIG_KEYS || legacyKey in PHYSICAL_PRINTER_CONFIG_KEYS) {
                        recognizedConfig = true
                        if (currentPhysicalPrinterConfig == null) currentPhysicalPrinterConfig = ConfigObject()
                        currentPhysicalPrinterConfig!!.put(key, normalizedValue)
                    }
                }
            }
        }

        if (!recognizedConfig) {
            throw UnsupportedEncodingException("Failed to decode config: no recognized profile sections or settings")
        }

        for (config in parentMap.values) {
            while (config.values.containsKey("inherits")) {
                val inherited = config.values.remove("inherits") ?: continue
                if (inherited.isEmpty()) continue
                if (inherited.contains(";")) {
                    for (parent in Pattern.compile(";").split(inherited)) {
                        val parentName = parent.trim()
                        val parentConfig = parentMap[parentName]
                        if (parentConfig == null) {
                            loadWarnings.add(
                                ProfileConfigLoadWarning.MissingParent(config.title.orEmpty(), parentName)
                            )
                            continue
                        }
                        val newValues = HashMap<String, String>()
                        newValues.putAll(parentConfig.values)
                        newValues.putAll(config.values)
                        config.values = newValues
                    }
                } else if (parentMap.containsKey(inherited)) {
                    val newValues = HashMap<String, String>()
                    newValues.putAll(parentMap[inherited]!!.values)
                    newValues.putAll(config.values)
                    config.values = newValues
                } else {
                    loadWarnings.add(
                        ProfileConfigLoadWarning.MissingParent(config.title.orEmpty(), inherited)
                    )
                }
            }
        }

        if (currentPrintConfig != null) printConfigs.add(currentPrintConfig!!)
        if (currentPrinterConfig != null) printerConfigs.add(currentPrinterConfig!!)
        if (currentFilamentConfig != null) filamentConfigs.add(currentFilamentConfig!!)
        if (currentPhysicalPrinterConfig != null) physicalPrintersConfigs.add(currentPhysicalPrinterConfig!!)

        if (presets == null) {
            val generatedPresets = ConfigObject()
            currentPrintConfig?.let { generatedPresets.put("print", it.title) }
            currentFilamentConfig?.let { generatedPresets.put("filament", it.title) }
            currentPrinterConfig?.let { generatedPresets.put("printer", it.title) }
            currentPhysicalPrinterConfig?.let { generatedPresets.put("physical_printer", it.title) }
            presets = generatedPresets
        }

        reader.close()
        input.close()
    }
}
