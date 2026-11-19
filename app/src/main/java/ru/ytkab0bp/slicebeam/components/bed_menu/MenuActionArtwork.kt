package ru.ytkab0bp.slicebeam.components.bed_menu

import androidx.annotation.DrawableRes
import ru.ytkab0bp.slicebeam.R

/** Existing workspace actions mapped to the supplied, label-free CAD artwork. */
@DrawableRes
internal fun menuActionArtwork(titleRes: Int, selected: Boolean = false, enabled: Boolean = true): Int? {
    val base = when (titleRes) {
        R.string.WorkspaceObjects -> R.drawable.cad_objekte
        R.string.ModelMenuDuplicate -> R.drawable.cad_duplizieren
        R.string.ModelMenuMoveToPlate -> R.drawable.cad_auf_platte_verschieben
        R.string.ModelMenuSplit -> R.drawable.cad_teile_trennen
        R.string.ModelMenuModifiers -> R.drawable.cad_modifikator_volumen
        R.string.ModelMenuObjectSettings -> R.drawable.cad_objekt_druckeinstellungen
        R.string.ModelMenuCenterOnBed -> R.drawable.cad_zentrieren
        R.string.ModelScaleToBuildVolume -> R.drawable.cad_objekt_ins_druckvolumen
        R.string.ModelMenuPlaceGroupOnBed -> R.drawable.cad_auf_platte_platzieren
        R.string.ModelMenuPlaceOnObject -> R.drawable.cad_auf_objekt
        R.string.ModelMenuPlaceOnSurface -> R.drawable.cad_auf_flaeche
        R.string.ModelMenuAlignSelected -> R.drawable.cad_alle_ausrichten
        R.string.ModelMenuAlignPrimaryAxes -> R.drawable.cad_an_achsen
        R.string.ModelMenuCombineSelected -> R.drawable.cad_als_teile_verbinden
        R.string.ModelMenuBoolean -> R.drawable.cad_boolesche_funktion
        R.string.AdvancedFeatureAccessibility -> R.drawable.cad_tools
        R.string.ToolAccessibilityPreviewClear -> R.drawable.cad_tools
        R.string.ModelMenuFillBed -> R.drawable.cad_druckplatte_fuellen
        R.string.ModelMenuArrange,
        R.string.MenuOrientationArrange -> R.drawable.cad_alle_anordnen
        R.string.ModelMenuCreatePrimitive -> R.drawable.cad_grundkoerper
        R.string.TextModelCreateSeparate -> R.drawable.cad_eigenstaendig
        R.string.ModelMenuOrientAll,
        R.string.MenuOrientAllObjects,
        R.string.MenuOrientationAutoOrient,
        R.string.ModelMenuAutoOrientSelected -> R.drawable.cad_auto_ausrichten
        R.string.ModelMenuFlatten,
        R.string.MenuOrientationFlatten -> R.drawable.cad_flach_hinlegen
        R.string.ModelMenuDeleteSelected -> R.drawable.cad_loeschen
        R.string.ModelMenuSelectAll -> R.drawable.cad_alle_auswaehlen
        R.string.ModelMenuDeleteAll -> R.drawable.cad_alle_loeschen
        R.string.ModelContextResetRotation -> R.drawable.cad_drehung_zuruecksetzen
        R.string.ModelContextResetScale -> R.drawable.cad_skalierung_zuruecksetzen
        R.string.ModelUndo -> R.drawable.cad_rueckgaengig
        R.string.ModelRedo -> R.drawable.cad_wiederholen
        R.string.MenuToolbarMove,
        R.string.MenuOrientationPosition -> R.drawable.cad_verschieben
        R.string.MenuToolbarRotate,
        R.string.MenuOrientationRotation -> R.drawable.cad_drehen
        R.string.MenuTransformScale -> R.drawable.cad_skalieren
        R.string.ModelSimplify -> R.drawable.cad_modell_vereinfachen
        R.string.ModelSmoothMesh -> R.drawable.cad_netz_glaetten
        R.string.MenuTransformCut -> R.drawable.cad_schneiden
        R.string.MenuToolbarVariableLayerHeight -> R.drawable.cad_variable_schichthoehe
        R.string.MenuToolbarSupportPaint -> R.drawable.cad_stuetzen_malen
        R.string.MenuToolbarSeamPaint -> R.drawable.cad_naht_malen
        R.string.MenuToolbarFuzzyPaint -> R.drawable.cad_fuzzy_skin_malen
        R.string.MenuToolbarColorPaint -> R.drawable.cad_farbe_malen
        R.string.MenuToolbarEmboss -> R.drawable.cad_praegung
        R.string.WorkspaceAddModelAction,
        R.string.MenuFileOpen -> R.drawable.cad_modell_oeffnen
        R.string.RecentModelsTitle -> R.drawable.cad_zuletzt_verwendet
        R.string.MenuFileExport3mf -> R.drawable.cad_3mf_exportieren
        R.string.MenuFileCalibrations -> R.drawable.cad_kalibrierung
        R.string.MenuFileImportProfiles -> R.drawable.cad_profile_importieren
        R.string.MenuFileExportProfiles -> R.drawable.cad_profile_exportieren
        R.string.QuickSliceTitle -> R.drawable.cad_schnell_slicen
        R.string.SmartSliceTitle -> R.drawable.cad_smart_experte
        R.string.MenuSliceCurrentSettings -> R.drawable.cad_platte_slicen
        R.string.MenuSliceInfo -> R.drawable.ic_print_info
        R.string.MenuSliceLayers -> R.drawable.ic_layers
        R.string.MenuSliceExportToFile -> R.drawable.ic_export
        R.string.MenuSliceShare -> R.drawable.ic_share
        R.string.MenuSlicePerformance -> R.drawable.ic_preview_simple
        else -> null
    } ?: return null

    return when {
        !enabled -> disabledVariant(base)
        selected -> activeVariant(base)
        else -> base
    }
}

@DrawableRes
private fun activeVariant(@DrawableRes base: Int): Int = when (base) {
    R.drawable.cad_objekte -> R.drawable.cad_objekte_active
    R.drawable.cad_duplizieren -> R.drawable.cad_duplizieren_active
    R.drawable.cad_auf_platte_verschieben -> R.drawable.cad_auf_platte_verschieben_active
    R.drawable.cad_teile_trennen -> R.drawable.cad_teile_trennen_active
    R.drawable.cad_modifikator_volumen -> R.drawable.cad_modifikator_volumen_active
    R.drawable.cad_objekt_druckeinstellungen -> R.drawable.cad_objekt_druckeinstellungen_active
    R.drawable.cad_zentrieren -> R.drawable.cad_zentrieren_active
    R.drawable.cad_objekt_ins_druckvolumen -> R.drawable.cad_objekt_ins_druckvolumen_active
    R.drawable.cad_auf_platte_platzieren -> R.drawable.cad_auf_platte_platzieren_active
    R.drawable.cad_auf_objekt -> R.drawable.cad_auf_objekt_active
    R.drawable.cad_auf_flaeche -> R.drawable.cad_auf_flaeche_active
    R.drawable.cad_alle_ausrichten -> R.drawable.cad_alle_ausrichten_active
    R.drawable.cad_an_achsen -> R.drawable.cad_an_achsen_active
    R.drawable.cad_als_teile_verbinden -> R.drawable.cad_als_teile_verbinden_active
    R.drawable.cad_boolesche_funktion -> R.drawable.cad_boolesche_funktion_active
    R.drawable.cad_druckplatte_fuellen -> R.drawable.cad_druckplatte_fuellen_active
    R.drawable.cad_alle_anordnen -> R.drawable.cad_alle_anordnen_active
    R.drawable.cad_grundkoerper -> R.drawable.cad_grundkoerper_active
    R.drawable.cad_eigenstaendig -> R.drawable.cad_eigenstaendig_active
    R.drawable.cad_auto_ausrichten -> R.drawable.cad_auto_ausrichten_active
    R.drawable.cad_flach_hinlegen -> R.drawable.cad_flach_hinlegen_active
    R.drawable.cad_loeschen -> R.drawable.cad_loeschen_active
    R.drawable.cad_alle_auswaehlen -> R.drawable.cad_alle_auswaehlen_active
    R.drawable.cad_alle_loeschen -> R.drawable.cad_alle_loeschen_active
    R.drawable.cad_drehung_zuruecksetzen -> R.drawable.cad_drehung_zuruecksetzen_active
    R.drawable.cad_skalierung_zuruecksetzen -> R.drawable.cad_skalierung_zuruecksetzen_active
    R.drawable.cad_rueckgaengig -> R.drawable.cad_rueckgaengig_active
    R.drawable.cad_wiederholen -> R.drawable.cad_wiederholen_active
    R.drawable.cad_verschieben -> R.drawable.cad_verschieben_active
    R.drawable.cad_drehen -> R.drawable.cad_drehen_active
    R.drawable.cad_skalieren -> R.drawable.cad_skalieren_active
    R.drawable.cad_modell_vereinfachen -> R.drawable.cad_modell_vereinfachen_active
    R.drawable.cad_netz_glaetten -> R.drawable.cad_netz_glaetten_active
    R.drawable.cad_schneiden -> R.drawable.cad_schneiden_active
    R.drawable.cad_variable_schichthoehe -> R.drawable.cad_variable_schichthoehe_active
    R.drawable.cad_stuetzen_malen -> R.drawable.cad_stuetzen_malen_active
    R.drawable.cad_naht_malen -> R.drawable.cad_naht_malen_active
    R.drawable.cad_fuzzy_skin_malen -> R.drawable.cad_fuzzy_skin_malen_active
    R.drawable.cad_farbe_malen -> R.drawable.cad_farbe_malen_active
    R.drawable.cad_praegung -> R.drawable.cad_praegung_active
    R.drawable.cad_modell_oeffnen -> R.drawable.cad_modell_oeffnen_active
    R.drawable.cad_zuletzt_verwendet -> R.drawable.cad_zuletzt_verwendet_active
    R.drawable.cad_3mf_exportieren -> R.drawable.cad_3mf_exportieren_active
    R.drawable.cad_kalibrierung -> R.drawable.cad_kalibrierung_active
    R.drawable.cad_profile_importieren -> R.drawable.cad_profile_importieren_active
    R.drawable.cad_profile_exportieren -> R.drawable.cad_profile_exportieren_active
    R.drawable.cad_schnell_slicen -> R.drawable.cad_schnell_slicen_active
    R.drawable.cad_smart_experte -> R.drawable.cad_smart_experte_active
    R.drawable.cad_platte_slicen -> R.drawable.cad_platte_slicen_active
    R.drawable.ic_print_info -> R.drawable.ic_print_info_active
    R.drawable.ic_layers -> R.drawable.ic_layers_active
    R.drawable.ic_export -> R.drawable.ic_export_active
    R.drawable.ic_share -> R.drawable.ic_share_active
    R.drawable.ic_preview_simple -> R.drawable.ic_preview_simple_active
    else -> base
}

@DrawableRes
private fun disabledVariant(@DrawableRes base: Int): Int = when (base) {
    R.drawable.cad_objekte -> R.drawable.cad_objekte_disabled
    R.drawable.cad_duplizieren -> R.drawable.cad_duplizieren_disabled
    R.drawable.cad_auf_platte_verschieben -> R.drawable.cad_auf_platte_verschieben_disabled
    R.drawable.cad_teile_trennen -> R.drawable.cad_teile_trennen_disabled
    R.drawable.cad_modifikator_volumen -> R.drawable.cad_modifikator_volumen_disabled
    R.drawable.cad_objekt_druckeinstellungen -> R.drawable.cad_objekt_druckeinstellungen_disabled
    R.drawable.cad_zentrieren -> R.drawable.cad_zentrieren_disabled
    R.drawable.cad_objekt_ins_druckvolumen -> R.drawable.cad_objekt_ins_druckvolumen_disabled
    R.drawable.cad_auf_platte_platzieren -> R.drawable.cad_auf_platte_platzieren_disabled
    R.drawable.cad_auf_objekt -> R.drawable.cad_auf_objekt_disabled
    R.drawable.cad_auf_flaeche -> R.drawable.cad_auf_flaeche_disabled
    R.drawable.cad_alle_ausrichten -> R.drawable.cad_alle_ausrichten_disabled
    R.drawable.cad_an_achsen -> R.drawable.cad_an_achsen_disabled
    R.drawable.cad_als_teile_verbinden -> R.drawable.cad_als_teile_verbinden_disabled
    R.drawable.cad_boolesche_funktion -> R.drawable.cad_boolesche_funktion_disabled
    R.drawable.cad_druckplatte_fuellen -> R.drawable.cad_druckplatte_fuellen_disabled
    R.drawable.cad_alle_anordnen -> R.drawable.cad_alle_anordnen_disabled
    R.drawable.cad_grundkoerper -> R.drawable.cad_grundkoerper_disabled
    R.drawable.cad_eigenstaendig -> R.drawable.cad_eigenstaendig_disabled
    R.drawable.cad_auto_ausrichten -> R.drawable.cad_auto_ausrichten_disabled
    R.drawable.cad_flach_hinlegen -> R.drawable.cad_flach_hinlegen_disabled
    R.drawable.cad_loeschen -> R.drawable.cad_loeschen_disabled
    R.drawable.cad_alle_auswaehlen -> R.drawable.cad_alle_auswaehlen_disabled
    R.drawable.cad_alle_loeschen -> R.drawable.cad_alle_loeschen_disabled
    R.drawable.cad_drehung_zuruecksetzen -> R.drawable.cad_drehung_zuruecksetzen_disabled
    R.drawable.cad_skalierung_zuruecksetzen -> R.drawable.cad_skalierung_zuruecksetzen_disabled
    R.drawable.cad_rueckgaengig -> R.drawable.cad_rueckgaengig_disabled
    R.drawable.cad_wiederholen -> R.drawable.cad_wiederholen_disabled
    R.drawable.cad_verschieben -> R.drawable.cad_verschieben_disabled
    R.drawable.cad_drehen -> R.drawable.cad_drehen_disabled
    R.drawable.cad_skalieren -> R.drawable.cad_skalieren_disabled
    R.drawable.cad_modell_vereinfachen -> R.drawable.cad_modell_vereinfachen_disabled
    R.drawable.cad_netz_glaetten -> R.drawable.cad_netz_glaetten_disabled
    R.drawable.cad_schneiden -> R.drawable.cad_schneiden_disabled
    R.drawable.cad_variable_schichthoehe -> R.drawable.cad_variable_schichthoehe_disabled
    R.drawable.cad_stuetzen_malen -> R.drawable.cad_stuetzen_malen_disabled
    R.drawable.cad_naht_malen -> R.drawable.cad_naht_malen_disabled
    R.drawable.cad_fuzzy_skin_malen -> R.drawable.cad_fuzzy_skin_malen_disabled
    R.drawable.cad_farbe_malen -> R.drawable.cad_farbe_malen_disabled
    R.drawable.cad_praegung -> R.drawable.cad_praegung_disabled
    R.drawable.cad_modell_oeffnen -> R.drawable.cad_modell_oeffnen_disabled
    R.drawable.cad_zuletzt_verwendet -> R.drawable.cad_zuletzt_verwendet_disabled
    R.drawable.cad_3mf_exportieren -> R.drawable.cad_3mf_exportieren_disabled
    R.drawable.cad_kalibrierung -> R.drawable.cad_kalibrierung_disabled
    R.drawable.cad_profile_importieren -> R.drawable.cad_profile_importieren_disabled
    R.drawable.cad_profile_exportieren -> R.drawable.cad_profile_exportieren_disabled
    R.drawable.cad_schnell_slicen -> R.drawable.cad_schnell_slicen_disabled
    R.drawable.cad_smart_experte -> R.drawable.cad_smart_experte_disabled
    R.drawable.cad_platte_slicen -> R.drawable.cad_platte_slicen_disabled
    R.drawable.ic_print_info -> R.drawable.ic_print_info_disabled
    R.drawable.ic_layers -> R.drawable.ic_layers_disabled
    R.drawable.ic_export -> R.drawable.ic_export_disabled
    R.drawable.ic_share -> R.drawable.ic_share_disabled
    R.drawable.ic_preview_simple -> R.drawable.ic_preview_simple_disabled
    else -> base
}
