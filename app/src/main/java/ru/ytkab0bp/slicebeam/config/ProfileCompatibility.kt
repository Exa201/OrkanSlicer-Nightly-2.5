package ru.ytkab0bp.slicebeam.config

import ru.ytkab0bp.slicebeam.slic3r.Slic3rUtils
import ru.ytkab0bp.slicebeam.slic3r.Slic3rConfigWrapper
import java.util.Locale

/** Keeps profile pickers scoped to the selected printer and never mixes vendor presets. */
object ProfileCompatibility {
    /** Orca inheritance nodes are storage parents, not profiles a user can select. */
    @JvmStatic
    fun isSelectableProcess(title: String?): Boolean =
        !title.isNullOrBlank() &&
            !(title.startsWith("*") && title.endsWith("*")) &&
            !title.startsWith("fdm_process_", ignoreCase = true)

    @JvmStatic
    fun isSelectableFilament(title: String?): Boolean =
        !title.isNullOrBlank() &&
            !(title.startsWith("*") && title.endsWith("*")) &&
            !title.startsWith("fdm_filament_", ignoreCase = true)

    @JvmStatic
    fun processes(
        config: Slic3rConfigWrapper,
        selectedPrinter: String? = config.presets?.get("printer"),
        selectedProcess: String? = config.presets?.get("print"),
    ): List<ConfigObject> {
        val printerName = selectedPrinter
        val printer = config.findPrinter(printerName)
            ?: return config.printConfigs.filter { profile ->
                isSelectableProcess(profile.title) &&
                    (isPrinterAgnostic(profile) || profile.title == selectedProcess)
            }.distinctBy { it.title }
        val model = printer["printer_model"]
        val nozzle = printer["printer_variant"] ?: Slic3rUtils.firstNozzleDiameter(printer["nozzle_diameter"])
        val checker = Slic3rUtils.ConfigChecker(printer.serialize())
        val seen = HashSet<String>()
        return try {
            config.printConfigs.filter { profile ->
                isSelectableProcess(profile.title) && Slic3rUtils.isPrinterCompatible(
                    profile.title,
                    profile["compatible_printers"].orEmpty(),
                    profile["compatible_printers_condition"].orEmpty(),
                    printerName,
                    model,
                    nozzle,
                    checker,
                ) && Slic3rUtils.layerHeightFitsNozzle(profile["layer_height"], nozzle) &&
                    Slic3rUtils.lineWidthFitsNozzle(profile["line_width"], nozzle) &&
                    seen.add(profile.title.orEmpty())
            }
        } finally {
            checker.release()
        }
    }

    @JvmStatic
    fun filaments(
        config: Slic3rConfigWrapper,
        selectedPrinter: String? = config.presets?.get("printer"),
        selectedProcess: String? = config.presets?.get("print"),
    ): List<ConfigObject> {
        val printerName = selectedPrinter
        val printer = config.findPrinter(printerName)
        if (printer == null) {
            return config.filamentConfigs.filter { profile ->
                isSelectableFilament(profile.title) && (
                    profile.title == config.presets?.get("filament") ||
                        isPrinterAgnostic(profile) && profile["compatible_prints"].isNullOrBlank() &&
                        profile["compatible_prints_condition"].isNullOrBlank()
                    )
            }.distinctBy { it.title }
        }

        val model = printer["printer_model"]
        val nozzle = printer["printer_variant"] ?: Slic3rUtils.firstNozzleDiameter(printer["nozzle_diameter"])
        val printerChecker = Slic3rUtils.ConfigChecker(printer.serialize())
        val printConfig = config.findPrint(selectedProcess)
        val printChecker = printConfig?.let { Slic3rUtils.ConfigChecker(it.serialize()) }
        val seen = HashSet<String>()
        return try {
            config.filamentConfigs.filter { profile ->
                if (!isSelectableFilament(profile.title)) return@filter false
                val printerCompatible = Slic3rUtils.isPrinterCompatible(
                    profile.title,
                    profile["compatible_printers"].orEmpty(),
                    profile["compatible_printers_condition"].orEmpty(),
                    printerName,
                    model,
                    nozzle,
                    printerChecker,
                )
                val printCompatible = selectedProcess.isNullOrBlank() || Slic3rUtils.isCompatible(
                    profile["compatible_prints"].orEmpty(),
                    profile["compatible_prints_condition"].orEmpty(),
                    selectedProcess,
                    printChecker,
                )
                printerCompatible && printCompatible && seen.add(profile.title.orEmpty())
            }
        } finally {
            printChecker?.release()
            printerChecker.release()
        }
    }

    private fun isPrinterAgnostic(profile: ConfigObject): Boolean =
        profile["compatible_printers"].isNullOrBlank() &&
            profile["compatible_printers_condition"].isNullOrBlank() &&
            profile.title.orEmpty().lowercase(Locale.ROOT).let { title -> " @" !in title }
}
