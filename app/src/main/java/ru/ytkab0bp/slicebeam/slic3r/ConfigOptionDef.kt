package ru.ytkab0bp.slicebeam.slic3r

import androidx.annotation.Keep

/** Kotlin representation of the option metadata populated directly by Orca's JNI bridge. */
@Keep
class ConfigOptionDef @Keep constructor() {
    @JvmField var key: String = ""

    // What type? bool, int, string etc.
    @JvmField var type: ConfigOptionType = ConfigOptionType.NONE

    // Usually empty. Special values select specialized profile editors.
    @JvmField var guiType: GUIType? = null

    // Short labels are used in grouped rows; full labels are used for standalone options.
    @JvmField var label: String = ""
    @JvmField var fullLabel: String = ""

    @JvmField var printerTechnology: PrinterTechnology = PrinterTechnology.UNKNOWN

    // Category of a configuration field from the GUI perspective.
    @JvmField var category: String = ""
    @JvmField var tooltip: String = ""
    @JvmField var sidetext: String = ""

    @JvmField var multiline: Boolean = false
    @JvmField var fullWidth: Boolean = false
    @JvmField var readonly: Boolean = false
    /** Orca marks process options whose vector entries belong to individual tool/variant columns. */
    @JvmField var perNozzle: Boolean = false
    @JvmField var nullable: Boolean = false
    @JvmField var height: Int = -1
    @JvmField var width: Int = -1

    // Unset bounds retain Orca's original sentinel values.
    @JvmField var min: Float = Float.MIN_VALUE
    @JvmField var max: Float = Float.MAX_VALUE

    @JvmField var mode: ConfigOptionMode = ConfigOptionMode.SIMPLE
    @JvmField var defaultValue: String? = null
    @JvmField var enumLabels: Array<String>? = null
    @JvmField var enumValues: Array<String>? = null

    fun getLabel(): String = label.ifBlank {
        fullLabel.ifBlank {
            // Orca defines this numeric setting with a commented-out GUI label.
            if (key == "fan_speedup_time") "Fan speed-up time" else ""
        }
    }

    fun getFullLabel(): String = if (fullLabel.isEmpty()) getLabel() else fullLabel

    @Keep
    enum class ConfigOptionType(@JvmField val list: Boolean = false) {
        NONE,
        FLOAT,
        FLOATS(true),
        INT,
        INTS(true),
        STRING,
        STRINGS(true),
        PERCENT,
        PERCENTS(true),
        FLOAT_OR_PERCENT,
        FLOATS_OR_PERCENTS(true),
        POINT,
        POINTS(true),
        POINT3,
        BOOL,
        BOOLS(true),
        ENUM,
        ENUMS,
    }

    @Keep
    enum class GUIType {
        UNDEFINED,
        I_ENUM_OPEN,
        F_ENUM_OPEN,
        SELECT_OPEN,
        COLOR,
        SLIDER,
        LEGEND,
        ONE_STRING,
        SELECT_CLOSE,
        PASSWORD,
    }

    @Keep
    enum class PrinterTechnology {
        FFF,
        SLA,
        UNKNOWN,
        ANY,
    }

    @Keep
    enum class ConfigOptionMode {
        SIMPLE,
        ADVANCED,
        EXPERT,
        UNDEFINED,
    }
}
