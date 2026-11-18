package ru.ytkab0bp.slicebeam.ui.settings

import androidx.annotation.DrawableRes
import ru.ytkab0bp.slicebeam.R

/** Printer-profile artwork, isolated from same-named print and filament assets. */
internal object PrinterProfileCategoryAssets {
    @DrawableRes
    fun iconFor(title: String, @DrawableRes fallback: Int): Int = when (title.trim().lowercase()) {
        "general" -> R.drawable.printerprofile_allgemein
        "machine limits" -> R.drawable.printerprofile_maschinengrenzen
        "extruder 1" -> R.drawable.printerprofile_extruder_1
        "printer" -> R.drawable.printerprofile_drucker
        "physical printer" -> R.drawable.printerprofile_druckerhost_einrichten
        "custom g-code" -> R.drawable.ic_custom_gcode_normal
        "notes" -> R.drawable.ic_notes_normal
        else -> fallback
    }

    @DrawableRes
    fun stateIcon(@DrawableRes icon: Int, active: Boolean = false, enabled: Boolean = true): Int {
        val suffix = when {
            !enabled -> "disabled"
            active -> "active"
            else -> return icon
        }
        return when (icon) {
            R.drawable.printerprofile_druckerprofil -> if (suffix == "active") R.drawable.printerprofile_druckerprofil_active else R.drawable.printerprofile_druckerprofil_disabled
            R.drawable.printerprofile_druckerverbindung -> if (suffix == "active") R.drawable.printerprofile_druckerverbindung_active else R.drawable.printerprofile_druckerverbindung_disabled
            R.drawable.printerprofile_allgemein -> if (suffix == "active") R.drawable.printerprofile_allgemein_active else R.drawable.printerprofile_allgemein_disabled
            R.drawable.printerprofile_maschinengrenzen -> if (suffix == "active") R.drawable.printerprofile_maschinengrenzen_active else R.drawable.printerprofile_maschinengrenzen_disabled
            R.drawable.printerprofile_extruder_1 -> if (suffix == "active") R.drawable.printerprofile_extruder_1_active else R.drawable.printerprofile_extruder_1_disabled
            R.drawable.printerprofile_drucker -> if (suffix == "active") R.drawable.printerprofile_drucker_active else R.drawable.printerprofile_drucker_disabled
            R.drawable.printerprofile_profilsuche -> if (suffix == "active") R.drawable.printerprofile_profilsuche_active else R.drawable.printerprofile_profilsuche_disabled
            R.drawable.printerprofile_druckerhost_einrichten -> if (suffix == "active") R.drawable.printerprofile_druckerhost_einrichten_active else R.drawable.printerprofile_druckerhost_einrichten_disabled
            R.drawable.printerprofile_speichern -> if (suffix == "active") R.drawable.printerprofile_speichern_active else R.drawable.printerprofile_speichern_disabled
            R.drawable.printerprofile_zuruecksetzen -> if (suffix == "active") R.drawable.printerprofile_zuruecksetzen_active else R.drawable.printerprofile_zuruecksetzen_disabled
            R.drawable.printerprofile_druckerauswahl -> if (suffix == "active") R.drawable.printerprofile_druckerauswahl_active else R.drawable.printerprofile_druckerauswahl_disabled
            R.drawable.ic_custom_gcode_normal -> when (suffix) {
                "active" -> R.drawable.ic_custom_gcode_active
                else -> R.drawable.ic_custom_gcode_disabled
            }
            R.drawable.ic_notes_normal -> when (suffix) {
                "active" -> R.drawable.ic_notes_active
                else -> R.drawable.ic_notes_disabled
            }
            else -> icon
        }
    }

    fun isPackAsset(@DrawableRes icon: Int): Boolean = icon in setOf(
        R.drawable.printerprofile_druckerprofil, R.drawable.printerprofile_druckerprofil_active, R.drawable.printerprofile_druckerprofil_disabled,
        R.drawable.printerprofile_druckerverbindung, R.drawable.printerprofile_druckerverbindung_active, R.drawable.printerprofile_druckerverbindung_disabled,
        R.drawable.printerprofile_allgemein, R.drawable.printerprofile_allgemein_active, R.drawable.printerprofile_allgemein_disabled,
        R.drawable.printerprofile_maschinengrenzen, R.drawable.printerprofile_maschinengrenzen_active, R.drawable.printerprofile_maschinengrenzen_disabled,
        R.drawable.printerprofile_extruder_1, R.drawable.printerprofile_extruder_1_active, R.drawable.printerprofile_extruder_1_disabled,
        R.drawable.printerprofile_drucker, R.drawable.printerprofile_drucker_active, R.drawable.printerprofile_drucker_disabled,
        R.drawable.printerprofile_profilsuche, R.drawable.printerprofile_profilsuche_active, R.drawable.printerprofile_profilsuche_disabled,
        R.drawable.printerprofile_druckerhost_einrichten, R.drawable.printerprofile_druckerhost_einrichten_active, R.drawable.printerprofile_druckerhost_einrichten_disabled,
        R.drawable.printerprofile_speichern, R.drawable.printerprofile_speichern_active, R.drawable.printerprofile_speichern_disabled,
        R.drawable.printerprofile_zuruecksetzen, R.drawable.printerprofile_zuruecksetzen_active, R.drawable.printerprofile_zuruecksetzen_disabled,
        R.drawable.printerprofile_druckerauswahl, R.drawable.printerprofile_druckerauswahl_active, R.drawable.printerprofile_druckerauswahl_disabled,
        R.drawable.ic_custom_gcode_normal, R.drawable.ic_custom_gcode_active, R.drawable.ic_custom_gcode_disabled,
        R.drawable.ic_notes_normal, R.drawable.ic_notes_active, R.drawable.ic_notes_disabled,
    )

    /** Resolves state artwork for either profile pack without crossing their resource namespaces. */
    @DrawableRes
    fun stateIconFor(@DrawableRes icon: Int, active: Boolean): Int = when {
        isPackAsset(icon) -> stateIcon(icon, active = active)
        FilamentProfileCategoryAssets.isPackAsset(icon) ->
            FilamentProfileCategoryAssets.stateIcon(icon, active = active)
        else -> icon
    }
}
