package ru.ytkab0bp.slicebeam.ui.settings

import androidx.annotation.DrawableRes
import ru.ytkab0bp.slicebeam.R

/** Dedicated art for filament profiles, kept separate from print and printer profile assets. */
internal object FilamentProfileCategoryAssets {
    @DrawableRes
    fun iconFor(title: String, @DrawableRes fallback: Int): Int = when (title.trim().lowercase()) {
        "filament" -> R.drawable.ic_filament
        "cooling", "cooling and fan" -> R.drawable.ic_filament_kuehlung
        "advanced" -> R.drawable.ic_filament_erweitert
        "filament overrides" -> R.drawable.ic_filamentkorrekturen
        "notes" -> R.drawable.ic_info
        "dependencies" -> R.drawable.ic_materialwarnung
        "custom g-code" -> R.drawable.ic_filament
        else -> fallback
    }

    @DrawableRes
    fun stateIcon(@DrawableRes icon: Int, active: Boolean, enabled: Boolean = true): Int {
        val state = when {
            !enabled -> "disabled"
            active -> "active"
            else -> return icon
        }
        return when (icon) {
            R.drawable.ic_filament -> if (state == "active") R.drawable.ic_filament_active else R.drawable.ic_filament_disabled
            R.drawable.ic_filament_kuehlung -> if (state == "active") R.drawable.ic_filament_kuehlung_active else R.drawable.ic_filament_kuehlung_disabled
            R.drawable.ic_filament_erweitert -> if (state == "active") R.drawable.ic_filament_erweitert_active else R.drawable.ic_filament_erweitert_disabled
            R.drawable.ic_filamentkorrekturen -> if (state == "active") R.drawable.ic_filamentkorrekturen_active else R.drawable.ic_filamentkorrekturen_disabled
            R.drawable.ic_filamentprofil -> if (state == "active") R.drawable.ic_filamentprofil_active else R.drawable.ic_filamentprofil_disabled
            R.drawable.ic_filamentslot -> if (state == "active") R.drawable.ic_filamentslot_active else R.drawable.ic_filamentslot_disabled
            R.drawable.ic_info -> if (state == "active") R.drawable.ic_info_active else R.drawable.ic_info_disabled
            R.drawable.ic_profilsuche -> if (state == "active") R.drawable.ic_profilsuche_active else R.drawable.ic_profilsuche_disabled
            R.drawable.ic_speichern -> if (state == "active") R.drawable.ic_speichern_active else R.drawable.ic_speichern_disabled
            R.drawable.ic_zuruecksetzen -> if (state == "active") R.drawable.ic_zuruecksetzen_active else R.drawable.ic_zuruecksetzen_disabled
            R.drawable.ic_materialwarnung -> if (state == "active") R.drawable.ic_materialwarnung_active else R.drawable.ic_materialwarnung_disabled
            else -> icon
        }
    }

    fun isPackAsset(@DrawableRes icon: Int): Boolean = when (icon) {
        R.drawable.ic_filament, R.drawable.ic_filament_kuehlung, R.drawable.ic_filament_erweitert,
        R.drawable.ic_filamentkorrekturen, R.drawable.ic_filamentprofil, R.drawable.ic_filamentslot,
        R.drawable.ic_info, R.drawable.ic_profilsuche, R.drawable.ic_speichern,
        R.drawable.ic_zuruecksetzen, R.drawable.ic_materialwarnung -> true
        else -> false
    }
}
