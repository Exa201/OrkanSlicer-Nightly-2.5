# UI 2.1 — Funktions-Mapping Alt → Neu

Arbeitsgrundlage: Nightly `codex/ui-2.1-redesign`; Stand 2026-11-15. Das Mapping ordnet die bisherigen Android-/View-Einstiege den neuen Compose-Oberflächen zu. Die 3D-Domäne, Profilobjekte und GL-/Slicer-Handler bleiben die fachlichen Zustandsquellen.

| Bereich / bisheriger Einstieg | UI-2.1-Oberfläche | Bestehender Zustand und Handler | Abnahmenachweis |
|---|---|---|---|
| Obere Bereichsauswahl / alte Tab-Ansicht | `AdaptiveWorkspaceNavigation` mit 3D, Druck, Filament, Drucker, App | `MobileNavigationDelegate` routet zu den existierenden Fragmenten; `WorkspaceShell` zeigt Bereichszustand und Status | Honor-Aufnahmen aller fünf Bereiche, 480 dp / 150 % |
| 3D-Bett und Profil-/Plattenstatus | `WorkspaceShell`, `WorkspaceProfileStatus`, `PlateTabs` | `BedFragment` hostet unverändert `GLView`/Renderer; Modell und Platten bleiben im Orca-Modellzustand | Honor-Workspace-Aufnahme; `WorkspaceProfileStatusDeviceTest` 1/1 |
| Projektwerkzeuge | `BedMenuComposeContent` + `FileMenu`-Aktionen | SAF-Import/Export, Projekt laden/sichern und Mehrplattenwarnung rufen existierende `MainActivity`-/`BedFragment`-Handler auf | `InitialProjectMenuDeviceTest`; import-/exportbezogene SliceTest-Läufe im UI-2.1-Backlog |
| Objektliste / „Geladene Objekte“ | `ModelObjectsDialog` mit Suche, Sortierung, Gruppen-/Teilzeilen und Overflow-Aktionen | `FileMenuObjectActions` liefert Snapshots und delegiert Auswahl, Filament, Teilwerte, Transformation und Trennen an Modell-/GL-Handler | `ModelObjectsAdaptiveDialogDeviceTest` 4/4 auf Honor; weitere Objekt-/Multipart-Prüfungen im Backlog |
| Modellaktionen | `ModelMenuItems` / Compose-Menüaktionen | `GLView` und `Model` führen Anordnen, Ausrichten, Stapeln, Flächenplatzierung, Verbinden und Boolesche Aktionen aus | Punkte 21–30 im `UI21_100_POINT_BACKLOG.md` |
| Ändern / Transformieren / Paint | Compose-Panels und existierende Transform-/Paint-Menüs | `TransformScaleMenu`, `PaintModePanel`, `VariableLayerHeightPanel` halten Zustände am Renderer; Apply/Cancel/Undo bleibt fachlich angebunden | Punkte 31–40; Workspace-Panel- und Skalierungstests |
| Tools / Kalibrierung / Profile | `BedMenuComposeContent`-Kacheln und Profileditoren | `CalibrationWorkspace`, Profilimport/-export und vorhandene Generatoren bleiben die Handler | Punkte 41–50; jeweilige Compose-/Importtests |
| Slicen / Slice-Vorschau / Upload | Compose-Slice-Aktionen und -Dialoge | Native Slicer-/`Print`-Zustand; `SliceInfoMenu`, Pre-Slice-Zuordnung, Export/Upload und Upload-only getrennt vom Druckstart | Punkte 51–60; Slicing-/Upload-Regressionen im Backlog |
| Druckprofile / Parameter | `ProfileListFragment` mit `ProfileDropdownContent` und Compose-Einstellungszeilen | Profilrepository und Orca-Konfiguration bleiben Quelle; Entwurf, Validierung, Speichern und Reset verwenden bestehende Controller | Honor-Profilpicker-Screenshot; Large-text-Test 1/1; Punkte 61–70 |
| Filament / Slots / Farben | Slotpalette und Compose-Profilseite | `FilamentPaletteController`, Slot-/ACE-Zuordnung und native Filamentkonfiguration bleiben synchronisierte Zustandsquellen | Punkte 71–80, einschließlich 1/4/8/16/24-Slotfällen |
| Druckerprofile / Verbindung | Compose-Druckerseite und Profileditor | Bestehender Druckerprofil-/Verbindungszustand; WebView/Systembrowser nur über vorhandene Hostroute | Punkte 81–90, Honor-Druckerseite |
| App-Einstellungen / Maker / About | `SettingsContent`-Kategorien und App-Compose-Routen | `Prefs`, Profile, Downloader, Changelog und bestehende Maker-Komponenten | Punkte 91–100, Honor-App-Seite |
| Android-Systemdialoge und SAF | Plattformdialoge/`ActivityResult` | Android-Berechtigungs-, SAF-, Installations- und Systemintegrationen bleiben native Plattformverantwortung | Picker-/Dialogtests in Menü 1; nicht durch Compose-Mockups ersetzt |

## Grenze des Nachweises

Dieses Mapping belegt Routen und Zuständigkeiten; es bedeutet nicht, dass jede Oberfläche in jeder Dichte, Orientierung, Sprache und Schriftgröße manuell auf Hardware abgenommen wurde. Die aktuelle Honor-Abnahme deckt die fünf Hauptbereiche, den langen Profilpicker und automatisierte Objektlisteninteraktionen ab. 200-%-Schrift, physische TalkBack-Bedienung, vollständige Querformat-Unterdialoge, alle Mehrplatten-/Komplexprojekt-Fixtures und ein echter Offline-Kaltstart mit Import und Slicing sind noch gesondert zu verifizieren.
