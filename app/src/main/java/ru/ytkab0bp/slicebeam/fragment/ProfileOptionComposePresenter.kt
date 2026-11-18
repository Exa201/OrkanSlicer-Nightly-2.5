package ru.ytkab0bp.slicebeam.fragment

import android.content.Context
import androidx.compose.runtime.Composable
import ru.ytkab0bp.slicebeam.R
import ru.ytkab0bp.slicebeam.SliceBeam
import ru.ytkab0bp.slicebeam.config.ProfilePointValueValidator
import ru.ytkab0bp.slicebeam.config.ProfileStringVectorCodec
import ru.ytkab0bp.slicebeam.config.LineWidthConversion
import ru.ytkab0bp.slicebeam.config.ProfileVectorValueValidator
import ru.ytkab0bp.slicebeam.config.PhysicalExtruderMapPolicy
import ru.ytkab0bp.slicebeam.slic3r.ConfigOptionDef
import ru.ytkab0bp.slicebeam.slic3r.Slic3rLocalization
import ru.ytkab0bp.slicebeam.config.ProfileOpenOptionResolver
import ru.ytkab0bp.slicebeam.ui.settings.ComposeProfileBooleanOption
import ru.ytkab0bp.slicebeam.ui.settings.ComposeProfileColorOption
import ru.ytkab0bp.slicebeam.ui.settings.ComposeProfileEnumOption
import ru.ytkab0bp.slicebeam.ui.settings.ComposeProfileEnumVectorOption
import ru.ytkab0bp.slicebeam.ui.settings.ComposeProfileFallbackOption
import ru.ytkab0bp.slicebeam.ui.settings.ComposeProfileNumericOption
import ru.ytkab0bp.slicebeam.ui.settings.ComposeLineWidthOption
import ru.ytkab0bp.slicebeam.ui.settings.ComposeProfileOpenOption
import ru.ytkab0bp.slicebeam.ui.settings.ComposeProfilePointOption
import ru.ytkab0bp.slicebeam.ui.settings.ComposeProfileStringOption
import ru.ytkab0bp.slicebeam.ui.settings.ComposeProfileStringVectorOption
import ru.ytkab0bp.slicebeam.ui.settings.ComposeProfileVectorOption
import java.util.Arrays
import java.util.function.Consumer
import java.util.function.Supplier

/** Builds Compose rows for every Orca setting type shown in profile screens. */
object ProfileOptionComposePresenter {
    internal fun isPasswordOption(def: ConfigOptionDef): Boolean =
        def.guiType == ConfigOptionDef.GUIType.PASSWORD || def.key == "printhost_apikey"

    @Suppress("UNUSED_PARAMETER")
    @JvmStatic
    fun supportsDirect(def: ConfigOptionDef): Boolean = true

    /** Draws every Orca option type in the parent LazyColumn without a nested ComposeView. */
    @Composable
    fun DirectContent(
        context: Context,
        def: ConfigOptionDef,
        elementIndex: Int,
        initialValue: String,
        onChanged: Consumer<String>,
        onLongClick: Runnable,
        onEditorStateChanged: (Boolean) -> Unit = {},
    ) {
        if (def.key in LineWidthConversion.keys &&
            def.type == ConfigOptionDef.ConfigOptionType.FLOAT_OR_PERCENT) {
            val printer = SliceBeam.CONFIG?.let { config ->
                config.presets?.get("printer")?.let(config::findPrinter)
            }
            val nozzle = LineWidthConversion.nozzle(printer?.get("nozzle_diameter"))
            if (nozzle != null) {
                ComposeLineWidthOption.Content(optionLabel(def), initialValue, nozzle,
                    LineWidthConversion.maxPercent(def.key), onChanged)
                return
            }
        }
        when (ProfileOptionDirectComposePolicy.kind(def, elementIndex, initialValue)) {
            ProfileOptionDirectComposePolicy.Kind.BOOLEAN -> ComposeProfileBooleanOption.Content(
                optionLabel(def),
                initialValue == "1",
                Consumer { checked -> onChanged.accept(if (checked) "1" else "0") },
                onLongClick,
            )

            ProfileOptionDirectComposePolicy.Kind.NUMBER -> ComposeProfileNumericOption.Content(
                optionLabel(def),
                initialValue,
                localized(def.sidetext),
                localized(def.tooltip),
                def.min,
                def.max,
                def.type == ConfigOptionDef.ConfigOptionType.INT,
                def.type == ConfigOptionDef.ConfigOptionType.PERCENT ||
                    def.type == ConfigOptionDef.ConfigOptionType.FLOAT_OR_PERCENT,
                def.type == ConfigOptionDef.ConfigOptionType.PERCENT,
                onChanged,
                onLongClick,
                onEditorStateChanged,
            )

            ProfileOptionDirectComposePolicy.Kind.OPEN_ENUM -> {
                val integerEnum = def.guiType == ConfigOptionDef.GUIType.I_ENUM_OPEN &&
                    def.type == ConfigOptionDef.ConfigOptionType.INT
                val choices = ProfileOpenOptionResolver.choices(
                    localizedCopy(def.enumLabels), def.enumValues, integerEnum,
                )
                ComposeProfileOpenOption.Content(
                    optionLabel(def),
                    initialValue,
                    localized(def.tooltip),
                    localized(def.sidetext),
                    choices,
                    def.type == ConfigOptionDef.ConfigOptionType.INT,
                    def.type == ConfigOptionDef.ConfigOptionType.PERCENT,
                    def.min,
                    def.max,
                    onChanged,
                    onLongClick,
                    onEditorStateChanged,
                )
            }

            ProfileOptionDirectComposePolicy.Kind.COLOR -> ComposeProfileColorOption.Content(
                optionLabel(def),
                initialValue,
                colorPicker(onChanged),
                onLongClick,
            )

            ProfileOptionDirectComposePolicy.Kind.ENUM -> {
                val labels: Array<String>
                val values: Array<String>
                if (def.key == "host_type") {
                    labels = arrayOf(
                        context.getString(R.string.PrinterHostOctoPrint),
                        context.getString(R.string.PrinterHostMoonraker),
                    )
                    values = arrayOf("octoprint", "moonraker")
                } else {
                    val sourceLabels = def.enumLabels.orEmpty()
                    val sourceValues = def.enumValues.orEmpty()
                    val count = minOf(sourceLabels.size, sourceValues.size)
                    labels = Array(count) { localized(sourceLabels[it]) }
                    values = Arrays.copyOf(sourceValues, count)
                }
                ComposeProfileEnumOption.Content(
                    optionLabel(def), initialValue, labels, values, onChanged, onLongClick,
                    onEditorStateChanged,
                )
            }

            ProfileOptionDirectComposePolicy.Kind.ENUM_VECTOR -> {
                val sourceLabels = def.enumLabels.orEmpty()
                val sourceValues = def.enumValues.orEmpty()
                val count = minOf(sourceLabels.size, sourceValues.size)
                ComposeProfileEnumVectorOption.Content(
                    optionLabel(def),
                    initialValue,
                    Array(count) { localized(sourceLabels[it]) },
                    Arrays.copyOf(sourceValues, count),
                    onChanged,
                    onLongClick,
                    onEditorStateChanged,
                )
            }

            ProfileOptionDirectComposePolicy.Kind.VECTOR -> {
                val vectorKind = when (def.type) {
                    ConfigOptionDef.ConfigOptionType.INTS -> ProfileVectorValueValidator.Kind.INTS
                    ConfigOptionDef.ConfigOptionType.PERCENTS -> ProfileVectorValueValidator.Kind.PERCENTS
                    ConfigOptionDef.ConfigOptionType.FLOATS_OR_PERCENTS ->
                        ProfileVectorValueValidator.Kind.FLOATS_OR_PERCENTS
                    else -> ProfileVectorValueValidator.Kind.FLOATS
                }
                val logicalCount = physicalExtruderLogicalCount(def)
                val vectorValue = physicalExtruderDisplayValue(def, initialValue, logicalCount)
                ComposeProfileVectorOption.Content(
                    optionLabel(def), vectorValue, localized(def.sidetext),
                    physicalExtruderDescription(def, localized(def.tooltip)),
                    if (def.key == "physical_extruder_map") 0f else def.min,
                    if (def.key == "physical_extruder_map") (logicalCount - 1).coerceAtLeast(0).toFloat() else def.max,
                    vectorKind, onChanged, onLongClick, def.nullable,
                    onEditorStateChanged,
                    additionalValidation = if (def.key == "physical_extruder_map") {
                        { value -> PhysicalExtruderMapPolicy.isValid(value, logicalCount) }
                    } else null,
                    validationMessage = if (def.key == "physical_extruder_map")
                        context.getString(R.string.PhysicalExtruderMapInvalid) else null,
                )
            }

            ProfileOptionDirectComposePolicy.Kind.POINT -> {
                val pointKind = when (def.type) {
                    ConfigOptionDef.ConfigOptionType.POINT -> ProfilePointValueValidator.Kind.POINT
                    ConfigOptionDef.ConfigOptionType.POINTS -> ProfilePointValueValidator.Kind.POINTS
                    else -> ProfilePointValueValidator.Kind.POINT3
                }
                ComposeProfilePointOption.Content(
                    optionLabel(def),
                    initialValue,
                    localized(def.tooltip),
                    pointKind,
                    def.type == ConfigOptionDef.ConfigOptionType.POINTS && elementIndex == -1,
                    onChanged,
                    onLongClick,
                    onEditorStateChanged,
                )
            }

            ProfileOptionDirectComposePolicy.Kind.STRING_VECTOR -> ComposeProfileStringVectorOption.Content(
                optionLabel(def),
                initialValue,
                localized(def.tooltip),
                def.multiline,
                def.height.takeIf { it > 0 } ?: 8,
                localizedCopy(def.enumLabels),
                def.enumValues,
                onChanged,
                onLongClick,
                onEditorStateChanged,
                def.key,
            )

            ProfileOptionDirectComposePolicy.Kind.STRING -> ComposeProfileStringOption.Content(
                optionLabel(def),
                initialValue,
                localized(def.tooltip),
                isPasswordOption(def),
                def.multiline,
                def.height.takeIf { it > 0 } ?: 8,
                localizedCopy(def.enumLabels),
                def.enumValues,
                onChanged,
                onLongClick,
                onEditorStateChanged,
            )

            ProfileOptionDirectComposePolicy.Kind.FALLBACK -> ComposeProfileFallbackOption.Content(
                optionLabel(def),
                localized(def.getFullLabel()),
                localized(def.tooltip),
                def,
                initialValue,
                fallbackChoices(context, def),
                onChanged,
                onLongClick,
                onEditorStateChanged,
            )

        }
    }

    @JvmStatic
    fun create(
        context: Context,
        def: ConfigOptionDef,
        elementIndex: Int,
        valueProvider: Supplier<String>,
        onChanged: Consumer<String>,
        onLongClick: Runnable,
    ): ProfileSettingsComposeItem {
        if (def.type == ConfigOptionDef.ConfigOptionType.BOOL ||
            def.type == ConfigOptionDef.ConfigOptionType.BOOLS
        ) {
            return ComposeProfileBooleanOption.create(
                localized(def.label),
                { valueProvider.get() == "1" },
                Consumer { checked -> onChanged.accept(if (checked) "1" else "0") },
                onLongClick,
            )
        }

        val integerOpenEnum = def.guiType == ConfigOptionDef.GUIType.I_ENUM_OPEN &&
            def.type == ConfigOptionDef.ConfigOptionType.INT
        val floatOpenEnum = def.guiType == ConfigOptionDef.GUIType.F_ENUM_OPEN &&
            (def.type == ConfigOptionDef.ConfigOptionType.FLOAT ||
                def.type == ConfigOptionDef.ConfigOptionType.PERCENT ||
                def.type == ConfigOptionDef.ConfigOptionType.FLOAT_OR_PERCENT)
        if (integerOpenEnum || floatOpenEnum) {
            val labels = localizedCopy(def.enumLabels)
            return ComposeProfileOpenOption.create(
                optionLabel(def), normalizedProvider(valueProvider), localized(def.tooltip),
                localized(def.sidetext), labels, def.enumValues,
                integerOpenEnum, integerOpenEnum,
                def.type == ConfigOptionDef.ConfigOptionType.PERCENT,
                def.min, def.max, onChanged, onLongClick,
            )
        }

        if (def.guiType == ConfigOptionDef.GUIType.COLOR) {
            return ComposeProfileColorOption.create(
                optionLabel(def),
                normalizedProvider(valueProvider),
                colorPicker(onChanged),
                onLongClick,
            )
        }

        if (def.type == ConfigOptionDef.ConfigOptionType.ENUM) {
            val labels: Array<String>
            val values: Array<String>
            if (def.key == "host_type") {
                labels = arrayOf(
                    context.getString(R.string.PrinterHostOctoPrint),
                    context.getString(R.string.PrinterHostMoonraker),
                )
                values = arrayOf("octoprint", "moonraker")
            } else {
                val sourceLabels = def.enumLabels ?: return fallback(
                    context, def, valueProvider, onChanged, onLongClick,
                )
                val sourceValues = def.enumValues ?: return fallback(
                    context, def, valueProvider, onChanged, onLongClick,
                )
                val count = minOf(sourceLabels.size, sourceValues.size)
                if (count == 0) return fallback(context, def, valueProvider, onChanged, onLongClick)
                labels = Array(count) { localized(sourceLabels[it]) }
                values = Arrays.copyOf(sourceValues, count)
            }
            return ComposeProfileEnumOption.create(
                optionLabel(def), normalizedProvider(valueProvider), labels, values, onChanged, onLongClick,
            )
        }

        if (def.type == ConfigOptionDef.ConfigOptionType.ENUMS) {
            val sourceLabels = def.enumLabels ?: return fallback(context, def, valueProvider, onChanged, onLongClick)
            val sourceValues = def.enumValues ?: return fallback(context, def, valueProvider, onChanged, onLongClick)
            val count = minOf(sourceLabels.size, sourceValues.size)
            if (count == 0) return fallback(context, def, valueProvider, onChanged, onLongClick)
            val labels = Array(count) { localized(sourceLabels[it]) }
            val values = Arrays.copyOf(sourceValues, count)
            return ComposeProfileEnumVectorOption.create(
                optionLabel(def), normalizedProvider(valueProvider), labels, values, onChanged, onLongClick,
            )
        }

        val vectorKind = when (def.type) {
            ConfigOptionDef.ConfigOptionType.INTS -> ProfileVectorValueValidator.Kind.INTS
            ConfigOptionDef.ConfigOptionType.PERCENTS -> ProfileVectorValueValidator.Kind.PERCENTS
            ConfigOptionDef.ConfigOptionType.FLOATS_OR_PERCENTS ->
                ProfileVectorValueValidator.Kind.FLOATS_OR_PERCENTS
            ConfigOptionDef.ConfigOptionType.FLOATS -> ProfileVectorValueValidator.Kind.FLOATS
            else -> null
        }
        if (vectorKind != null) {
            val logicalCount = physicalExtruderLogicalCount(def)
            val provider = if (def.key == "physical_extruder_map") Supplier {
                physicalExtruderDisplayValue(def, valueProvider.get().orEmpty(), logicalCount)
            } else normalizedProvider(valueProvider)
            return ComposeProfileVectorOption.create(
                optionLabel(def), provider, localized(def.sidetext),
                physicalExtruderDescription(def, localized(def.tooltip)),
                if (def.key == "physical_extruder_map") 0f else def.min,
                if (def.key == "physical_extruder_map") (logicalCount - 1).coerceAtLeast(0).toFloat() else def.max,
                vectorKind, onChanged, onLongClick, def.nullable,
                additionalValidation = if (def.key == "physical_extruder_map") {
                    { value -> PhysicalExtruderMapPolicy.isValid(value, logicalCount) }
                } else null,
                validationMessage = if (def.key == "physical_extruder_map")
                    context.getString(R.string.PhysicalExtruderMapInvalid) else null,
            )
        }

        val pointKind = when (def.type) {
            ConfigOptionDef.ConfigOptionType.POINT -> ProfilePointValueValidator.Kind.POINT
            ConfigOptionDef.ConfigOptionType.POINTS -> ProfilePointValueValidator.Kind.POINTS
            ConfigOptionDef.ConfigOptionType.POINT3 -> ProfilePointValueValidator.Kind.POINT3
            else -> null
        }
        if (pointKind != null) {
            return ComposeProfilePointOption.create(
                optionLabel(def), normalizedProvider(valueProvider), localized(def.tooltip), pointKind,
                def.type == ConfigOptionDef.ConfigOptionType.POINTS && elementIndex == -1,
                onChanged, onLongClick,
            )
        }

        if (def.type == ConfigOptionDef.ConfigOptionType.FLOAT ||
            def.type == ConfigOptionDef.ConfigOptionType.INT ||
            def.type == ConfigOptionDef.ConfigOptionType.PERCENT ||
            def.type == ConfigOptionDef.ConfigOptionType.FLOAT_OR_PERCENT
        ) {
            return ComposeProfileNumericOption.create(
                optionLabel(def), normalizedProvider(valueProvider), localized(def.sidetext),
                localized(def.tooltip), def.min, def.max,
                def.type == ConfigOptionDef.ConfigOptionType.INT,
                def.type == ConfigOptionDef.ConfigOptionType.PERCENT ||
                    def.type == ConfigOptionDef.ConfigOptionType.FLOAT_OR_PERCENT,
                def.type == ConfigOptionDef.ConfigOptionType.PERCENT,
                onChanged, onLongClick,
            )
        }

        if (def.type == ConfigOptionDef.ConfigOptionType.STRINGS &&
            def.guiType != ConfigOptionDef.GUIType.ONE_STRING &&
            elementIndex == -1 && ProfileStringVectorCodec.parse(valueProvider.get().orEmpty()) != null
        ) {
            return ComposeProfileStringVectorOption.create(
                optionLabel(def), normalizedProvider(valueProvider), localized(def.tooltip), def.multiline,
                def.height.takeIf { it > 0 } ?: 8,
                localizedCopy(def.enumLabels), def.enumValues, onChanged, onLongClick, def.key,
            )
        }

        if ((def.type == ConfigOptionDef.ConfigOptionType.STRING ||
                (def.type == ConfigOptionDef.ConfigOptionType.STRINGS &&
                    def.guiType == ConfigOptionDef.GUIType.ONE_STRING)) &&
            elementIndex == -1 && def.guiType != ConfigOptionDef.GUIType.COLOR
        ) {
            return ComposeProfileStringOption.create(
                optionLabel(def), normalizedProvider(valueProvider), localized(def.tooltip),
                isPasswordOption(def),
                def.multiline, def.height.takeIf { it > 0 } ?: 8,
                localizedCopy(def.enumLabels), def.enumValues, onChanged, onLongClick,
            )
        }

        return fallback(context, def, valueProvider, onChanged, onLongClick)
    }

    private fun physicalExtruderLogicalCount(def: ConfigOptionDef): Int {
        if (def.key != "physical_extruder_map") return 1
        val config = SliceBeam.CONFIG ?: return 1
        val printer = config.presets?.get("printer")?.let(config::findPrinter)
        return printer?.extruderCount?.coerceAtLeast(1) ?: 1
    }

    private fun physicalExtruderDisplayValue(def: ConfigOptionDef, value: String, logicalCount: Int): String {
        if (def.key != "physical_extruder_map") return value
        return if (PhysicalExtruderMapPolicy.isValid(value, logicalCount)) value
        else PhysicalExtruderMapPolicy.identity(logicalCount)
    }

    private fun physicalExtruderDescription(def: ConfigOptionDef, description: String): String =
        if (def.key == "physical_extruder_map") {
            SliceBeam.requireInstance().getString(R.string.PhysicalExtruderMapHelp)
        } else description

    private fun fallback(
        context: Context,
        def: ConfigOptionDef,
        valueProvider: Supplier<String>,
        onChanged: Consumer<String>,
        onLongClick: Runnable,
    ): ProfileSettingsComposeItem {
        val choices = fallbackChoices(context, def)
        return ComposeProfileFallbackOption.create(
            optionLabel(def),
            localized(def.getFullLabel()),
            localized(def.tooltip),
            def,
            normalizedProvider(valueProvider),
            choices,
            onChanged,
            onLongClick,
        )
    }

    private fun fallbackChoices(
        context: Context,
        def: ConfigOptionDef,
    ): List<ProfileOpenOptionResolver.Choice> = if (def.type != ConfigOptionDef.ConfigOptionType.ENUM) {
            emptyList()
        } else if (def.key == "host_type") {
            listOf(
                ProfileOpenOptionResolver.Choice(context.getString(R.string.PrinterHostOctoPrint), "octoprint"),
                ProfileOpenOptionResolver.Choice(context.getString(R.string.PrinterHostMoonraker), "moonraker"),
            )
        } else {
            val labels = localizedCopy(def.enumLabels)
            val values = def.enumValues
            val count = minOf(labels?.size ?: 0, values?.size ?: 0)
            (0 until count).map { index ->
                ProfileOpenOptionResolver.Choice(labels?.get(index).orEmpty(), values?.get(index).orEmpty())
            }
        }

    private fun colorPicker(onChanged: Consumer<String>): (Int) -> Unit = { color ->
        onChanged.accept(String.format("#%06X", color and 0xFFFFFF))
    }

    private fun optionLabel(def: ConfigOptionDef): String {
        val label = localized(def.getLabel()).takeIf { it.isNotBlank() }
            ?: localized(def.getFullLabel()).takeIf { it.isNotBlank() }
        if (label != null) return label
        val keyLabel = localized(def.key).takeIf { it.isNotBlank() && it != def.key }
            ?: def.key.replace('_', ' ').replaceFirstChar { it.uppercase() }
        return keyLabel
    }
    private fun localized(value: String?): String =
        if (value.isNullOrEmpty()) "" else Slic3rLocalization.getString(value)

    private fun normalizedProvider(provider: Supplier<String>): Supplier<String> =
        Supplier { provider.get().orEmpty() }

    private fun localizedCopy(values: Array<String>?): Array<String>? =
        values?.map(::localized)?.toTypedArray()
}
