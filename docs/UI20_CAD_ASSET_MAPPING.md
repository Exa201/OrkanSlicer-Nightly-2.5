# UI 2.0 CAD-Icon-Zuordnung

Importiert aus `OrkanSlicer_UI20_Fusion_CAD_Button_Assets_56.zip` (Paketversion 1.0). Verwendet werden die beschriftungsfreien PNG-Symbole aus `android/drawable-nodpi`; UI-Texte kommen weiterhin aus den vorhandenen Android-String-Ressourcen. Handler, Aktionen, Navigation und Druck-/Rendererpfade wurden nicht geändert.

| Paket-ID | Bestehender Button/Aktions-String | Status |
|---|---|---|
| `auswahl` | Modellmenü: Auswahl-Bereich (`model-menu-scope-object`) | integriert |
| `druckplatte` | Modellmenü: Druckplatten-/Alle-Objekte-Bereich (`model-menu-scope-all`) | integriert |
| `datei` | Workspace-Navigation: Projekt | integriert |
| `modell` | Workspace-Navigation: Modell | integriert |
| `aendern` | Workspace-Navigation: Ändern | integriert |
| `tools` | Workspace-Navigation: Tools | integriert |
| `slicen` | Workspace-Navigation: Slicen | integriert |
| `modell_oeffnen` | `MenuFileOpen`, `WorkspaceAddModelAction` | integriert |
| `zuletzt_verwendet` | `RecentModelsTitle` | integriert |
| `3mf_exportieren` | `MenuFileExport3mf` | integriert |
| `profile_exportieren` | `MenuFileExportProfiles` | integriert |
| `profile_importieren` | `MenuFileImportProfiles` | integriert |
| `kalibrierung` | `MenuFileCalibrations` | integriert |
| `schnell_slicen` | `QuickSliceTitle` | integriert |
| `smart_experte` | `SmartSliceTitle` | integriert |
| `platte_slicen` | `MenuSliceCurrentSettings` | integriert |
| `drehung_zuruecksetzen` | `ModelContextResetRotation` | integriert |
| `skalierung_zuruecksetzen` | `ModelContextResetScale` | integriert |
| `rueckgaengig` | `ModelUndo` | integriert |
| `wiederholen` | `ModelRedo` | integriert |
| `verschieben` | `MenuToolbarMove`, `MenuOrientationPosition` | integriert |
| `drehen` | `MenuToolbarRotate`, `MenuOrientationRotation` | integriert |
| `skalieren` | `MenuTransformScale` | integriert |
| `modell_vereinfachen` | `ModelSimplify` | integriert |
| `modifikator_volumen` | `ModelMenuModifiers` | integriert |
| `objekt_druckeinstellungen` | `ModelMenuObjectSettings` | integriert |
| `alle_anordnen` | `ModelMenuArrange`, `MenuOrientationArrange` | integriert |
| `grundkoerper` | `ModelMenuCreatePrimitive` | integriert |
| `alle_ausrichten` | `ModelMenuAlignSelected` | integriert |
| `eigenstaendig` | `TextModelCreateSeparate` | integriert |
| `alle_auswaehlen` | `ModelMenuSelectAll` | integriert |
| `alle_loeschen` | `ModelMenuDeleteAll` | integriert |
| `netz_glaetten` | `ModelSmoothMesh` | integriert |
| `schneiden` | `MenuTransformCut` | integriert |
| `variable_schichthoehe` | `MenuToolbarVariableLayerHeight` | integriert |
| `stuetzen_malen` | `MenuToolbarSupportPaint` | integriert |
| `naht_malen` | `MenuToolbarSeamPaint` | integriert |
| `fuzzy_skin_malen` | `MenuToolbarFuzzyPaint` | integriert |
| `farbe_malen` | `MenuToolbarColorPaint` | integriert |
| `praegung` | `MenuToolbarEmboss` | integriert |
| `flach_hinlegen` | `ModelMenuFlatten`, `MenuOrientationFlatten` | integriert |
| `loeschen` | `ModelMenuDeleteSelected` | integriert |
| `auf_flaeche` | `ModelMenuPlaceOnSurface` | integriert |
| `an_achsen` | `ModelMenuAlignPrimaryAxes` | integriert |
| `als_teile_verbinden` | `ModelMenuCombineSelected` | integriert |
| `boolesche_funktion` | `ModelMenuBoolean` | integriert |
| `druckplatte_fuellen` | `ModelMenuFillBed` | integriert |
| `auto_ausrichten` | `ModelMenuOrientAll`, `ModelMenuAutoOrientSelected`, `MenuOrientationAutoOrient` | integriert |
| `objekte` | `WorkspaceObjects` | integriert |
| `duplizieren` | `ModelMenuDuplicate` | integriert |
| `auf_platte_verschieben` | `ModelMenuMoveToPlate` | integriert |
| `teile_trennen` | `ModelMenuSplit` | integriert |
| `zentrieren` | `ModelMenuCenterOnBed` | integriert |
| `objekt_ins_druckvolumen` | `ModelScaleToBuildVolume` | integriert |
| `auf_platte_platzieren` | `ModelMenuPlaceGroupOnBed` | integriert |
| `auf_objekt` | `ModelMenuPlaceOnObject` | integriert |

Die drei Darstellungen (normal/aktiv/deaktiviert) werden für Workspace-Aktionskacheln entsprechend dem bereits vorhandenen `selected`-/`enabled`-Zustand ausgewählt. Die Navigation verwendet jeweils die aktive Variante nur für das ausgewählte Ziel. Die Druckinfo-/Schichten-/Sendeaktionen und „Ausgewählte Objekte verteilen“ behalten ihre bisherigen Icons: Das Paket enthält dafür kein semantisch passendes Symbol; insbesondere wird „Verteilen“ nicht mit „Ausrichten“ verwechselt.

Die Symbole sind transparente PNG-Rasterbilder, keine Vektoren. Die Paketdateien wurden in `app/src/main/res/drawable-nodpi/` übernommen; die vollständigen beschrifteten Kacheln werden nicht verwendet.
