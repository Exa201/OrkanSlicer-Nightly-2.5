package ru.ytkab0bp.slicebeam.ui.settings

import androidx.annotation.DrawableRes
import ru.ytkab0bp.slicebeam.R

/** Dedicated artwork for Orca process-profile groups; other profile categories keep their own icons. */
internal object PrintProfileCategoryAssets {
    @DrawableRes
    fun iconFor(title: String, @DrawableRes fallback: Int): Int = when (title.trim().lowercase()) {
        "layers and perimeters" -> R.drawable.ic_schichten_und_wandungen
        "top/bottom layers", "top / bottom layers" -> R.drawable.ic_obere_untere_schichten
        "infill" -> R.drawable.ic_fuellung
        "skirt and brim", "skirt, brim" -> R.drawable.ic_haftung_skirt_brim
        "support material", "support" -> R.drawable.ic_stuetzstrukturen
        "speed" -> R.drawable.ic_geschwindigkeit
        "cooling", "cooling and fan" -> R.drawable.ic_kuehlung
        "multiple extruders" -> R.drawable.ic_mehrere_extruder
        "advanced" -> R.drawable.ic_erweitert
        "output options" -> R.drawable.ic_ausgabeoptionen
        else -> fallback
    }
}
