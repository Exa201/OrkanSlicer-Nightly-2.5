package ru.ytkab0bp.slicebeam.manufacturing

/** Feature families from the advanced manufacturing brief. */
enum class ManufacturingFeatureGroup {
    MODEL_EDITING,
    ANALYSIS,
    SPECIAL_TOOLS,
    ADVANCED_PRINTING,
}

enum class ManufacturingFeatureMaturity { PLANNED, PARTIAL, STABLE, EXPERIMENTAL }

/** Stable IDs are retained for preferences, serialized projects, and diagnostics. */
enum class AdvancedManufacturingFeature(
    val id: Int,
    val key: String,
    val group: ManufacturingFeatureGroup,
    val prerequisites: Set<String> = emptySet(),
    val maturity: ManufacturingFeatureMaturity = ManufacturingFeatureMaturity.PLANNED,
) {
    CNC_PRECISION_ENGINE(1, "cnc_precision_engine", ManufacturingFeatureGroup.ANALYSIS, setOf("closed_mesh", "calibration_data")),
    SMART_ASSEMBLY_CUTTER(2, "smart_assembly_cutter", ManufacturingFeatureGroup.MODEL_EDITING, setOf("closed_mesh", "native_boolean")),
    TOOL_ACCESSIBILITY_ANALYZER(4, "tool_accessibility_analyzer", ManufacturingFeatureGroup.ANALYSIS, setOf("mesh_raycast", "tool_dimensions")),
    MANUFACTURING_FEATURE_GENERATOR(5, "manufacturing_feature_generator", ManufacturingFeatureGroup.MODEL_EDITING, setOf("face_selection", "native_boolean")),
    LASER_CONVERTER(6, "laser_converter", ManufacturingFeatureGroup.SPECIAL_TOOLS, setOf("section_geometry", "svg_export")),
    V_CARVE_GENERATOR(11, "v_carve_generator", ManufacturingFeatureGroup.MODEL_EDITING, setOf("path_import", "native_boolean")),
    INTELLIGENT_MARKING(14, "intelligent_marking", ManufacturingFeatureGroup.MODEL_EDITING, setOf("bundled_font", "mesh_projection")),
    NON_PLANAR_SURFACE_PRINTING(16, "non_planar_surface_printing", ManufacturingFeatureGroup.ADVANCED_PRINTING, setOf("machine_validation", "collision_checks"), ManufacturingFeatureMaturity.EXPERIMENTAL),
    ROBOT_MOTION_OPTIMIZER(17, "robot_motion_optimizer", ManufacturingFeatureGroup.ADVANCED_PRINTING, setOf("structured_toolpath", "firmware_validation")),
    ROBOTIC_SUPPORT_DESIGNER(18, "robotic_support_designer", ManufacturingFeatureGroup.SPECIAL_TOOLS, setOf("support_mesh", "accessibility_analysis")),
    PRINTER_DIGITAL_TWIN(20, "printer_digital_twin", ManufacturingFeatureGroup.ANALYSIS, setOf("machine_envelope", "toolpath_simulation")),
    ;

    /** New functionality must never affect ordinary slicing until deliberately enabled. */
    val enabledByDefault: Boolean = false

    /** No feature is callable until its complete handler and safety checks are implemented. */
    fun availability(
        enabled: Boolean,
        implemented: Boolean = false,
        supported: Boolean = true,
        prerequisitesAvailable: Boolean = true,
    ): ManufacturingFeatureAvailability = when {
        !enabled -> ManufacturingFeatureAvailability.DISABLED
        !supported -> ManufacturingFeatureAvailability.UNSUPPORTED
        !prerequisitesAvailable -> ManufacturingFeatureAvailability.MISSING_PREREQUISITES
        !implemented -> ManufacturingFeatureAvailability.NOT_IMPLEMENTED
        maturity == ManufacturingFeatureMaturity.EXPERIMENTAL -> ManufacturingFeatureAvailability.EXPERIMENTAL
        else -> ManufacturingFeatureAvailability.READY
    }

    companion object {
        val all: List<AdvancedManufacturingFeature> = entries.toList()

        fun byId(id: Int): AdvancedManufacturingFeature? = all.firstOrNull { it.id == id }

        fun inGroup(group: ManufacturingFeatureGroup): List<AdvancedManufacturingFeature> =
            all.filter { it.group == group }
    }
}

/** Describes why a feature may not run; UI and handlers will share this state model. */
enum class ManufacturingFeatureAvailability {
    DISABLED,
    READY,
    PARTIAL,
    UNSUPPORTED,
    EXPERIMENTAL,
    MISSING_PREREQUISITES,
    NOT_IMPLEMENTED,
}

/** Persisted opt-in flags are deliberately isolated from slicer/profile preferences. */
object AdvancedManufacturingFeaturePreferences {
    private const val PREFIX = "advanced_manufacturing_feature_"

    fun preferenceKey(feature: AdvancedManufacturingFeature): String = "$PREFIX${feature.id}_${feature.key}"

    fun isEnabled(feature: AdvancedManufacturingFeature, readBoolean: (String, Boolean) -> Boolean): Boolean =
        readBoolean(preferenceKey(feature), feature.enabledByDefault)

    fun setEnabled(feature: AdvancedManufacturingFeature, enabled: Boolean, writeBoolean: (String, Boolean) -> Unit) {
        writeBoolean(preferenceKey(feature), enabled)
    }
}
