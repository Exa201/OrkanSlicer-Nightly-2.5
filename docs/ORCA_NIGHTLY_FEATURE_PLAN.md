# Aktueller Stand — Nightly Batch 15, 6/10 — 2026-11-07

Batch 14 bleibt mit 10/10 Punkten veröffentlicht unter `v0.8.452-nightly.1`. Batch 15 umfasst drei Tree-Support-Punkte `d4e57d8606`, `5250597380`, `6d40ec93f6`, SV08-Profile `ca9204927e`, Flashforge-Standardprofile `b46bf81396` und die Legacy-Druckbett-Polygonvalidierung `c01ac8ff44`. Die letzten beiden wurden mit API-36-Instrumentierung je 1/1 geprüft. Batch 15 steht bei **6/10**; kein APK/Release vor 10/10. Native Texture-Displacement-Persistenz bleibt ungezählt, weil Compose-Workflow und produktiver Bake-/Abbruch-/Undo-Pfad fehlen. Nächster Schritt: einen weiteren Orca-2.5-Änderungskandidaten gegen den Android-Aufrufpfad prüfen und eine nicht bereits enthaltene, funktionsrelevante Änderung mit passender Regression portieren.

### Batch 15, Punkt 6/10 — ungültige Legacy-Druckbett-Ausschlussflächen verwerfen

Port von Orca `c01ac8ff44`: `bed_exclude_area` wird vor der Erzeugung von Ausschluss- und Kollisionspolygonen validiert. Die Punkte müssen endlich sein; das Polygon muss mindestens drei eindeutige Punkte, eine von null verschiedene Fläche und keine Selbstüberschneidung haben. Ungültige gespeicherte Werte werden beim Android-Arrangement und bei der Druckbereichsberechnung ignoriert, statt fehlerhafte Geometrie an Clipper/Arrangement weiterzugeben.

`NativeSliceDeviceTest#legacyBedExcludeAreaRejectsMalformedPolygonsFromProductionGeometry` besteht auf Android 16/API 36 (1/1): gültige Fläche akzeptiert, selbstschneidende, doppelte und nicht endliche Punktfolgen abgewiesen; `get_bed_excluded_area()` und `get_bed_excluded_regions()` liefern für die ungültige Fixture keine Fläche/Region. Der erste ARM64-Build fand einen fehlenden direkten `Geometry.hpp`-Include; nach Ergänzung bestanden der Slice-Instrumentierungsbuild und Gerätetest. Vollständiges Arrange ist mit diesem Test nicht abgedeckt. Kein APK/Release vor 10/10.

### Batch 15, Punkt 3/10 — Tree-Support-Pfade und lokale Verstärkung

Port von Orca `6d40ec93f6`: Die alte pauschale Doppelwandregel ab 100 mm wurde entfernt. `draw_circles()` ermittelt stattdessen die lokalen Astflächen, deren Bewegung zwischen Schichten mehr als die halbe Support-Linienbreite beträgt, und verstärkt nur überlappende Supportbereiche. Leere Extrusionsgruppen werden nicht mehr in die Pfadliste übernommen.

Auf Android 16/API 36 bestanden drei native Slice-Regressionen je 1/1: die neue 101-mm-Stamm-/Kappen-Fixture erzeugt endliche ausführbare Supportpfade direkt unter der Kappe; wiederholte Organic-Tree-Slices bleiben deterministisch; untere Kontakt-Interfaces behalten ihre ausführbaren Pfade. ARM64-Nativbuild und Instrumentierungs-Kotlin-Build erfolgreich. Der erste Testlauf verwendete eine ungültige 0,4-mm-Schichthöhe und meldete `Too small line width`; das war ein Fixture-Parameter, wurde auf die gültigen 0,2 mm gesetzt und der Slice-Test bestand danach. Kein APK/Release; Batch 15 ist 3/10.

### Batch 15, Punkt 1/10 — Tree-Support MST mit indexierten Arrays

Orca `d4e57d8606`: Prim hält Kandidaten, kürzeste Distanzen und Vorgänger jetzt in Arrays nach Vertexindex statt in pointer-keyed Hash-Maps. Gleichstände werden nach Koordinaten entschieden. Der Algorithmus bleibt O(V²), reduziert aber Hash-Lookups/Allokationen in einem tatsächlich aktiven Tree-Support-Pfad.

`NativeSliceDeviceTest#organicTreeSupportPathsAreStableAcrossRepeatedSlices` bestand auf API 36 (1/1; drei echte Slices mit identischen geordneten Support-Extrusionspfaden). `#treeSupportBottomContactCachePreservesInterfaceToolpaths` bestand zusätzlich 1/1 und erhält Kontakt-Interface-Layer samt ausführbaren G-Code-Bewegungen. Der ARM64 Slice-Nativbuild ist Bestandteil beider erfolgreichen `connectedSliceTestAndroidTest`-Läufe. Kein Timing-Benchmark oder quantifizierte Beschleunigung behauptet.

### Batch 15, Punkt 2/10 — unnötigen Tree-Support-Schrumpftest überspringen

Orca `5250597380`: `offset(poly, -scale_(support_spacing * 1.5))` entscheidet nur, ob ohne konfigurierte Wände eine einzelne Mindestwand ergänzt werden muss. Bei gesetzter Wandzahl wird das Ergebnis von `std::max(min_wall_count, wall_count)` ohnehin überstimmt; der Offset-Test wird daher nur noch bei `wall_count == 0` ausgewertet.

`NativeSliceDeviceTest#treeSupportBottomContactCachePreservesInterfaceToolpaths` bestand auf Android 16/API 36 (1/1). Die zweistufige Fixture wurde mit `tree_support_wall_count=0` und `=1` je zweimal gesliced; alle vier G-Codes enthalten Extrusion und die zwei konfigurierten unteren Kontakt-Interface-Schichten. ARM64 Slice-Nativbuild und Instrumentierungs-Kotlin-Build erfolgreich. Kein Performance-Benchmark behauptet.

## Batch 14 — 10/10 abgeschlossen; letzter Release v0.8.452-nightly.1

Batch 13 wurde mit 10/10 Punkten verifiziert und als `v0.8.450-nightly.1` veröffentlicht; der vorherige Nightly war 0.8.451. Batch 14 Punkt 1 portiert sechs Recycling-Filamentprofile und ist mit echtem Kobra-3-Slice auf Android 16/API 36 geprüft. Punkt 2 portiert `b5c4ccd9c5`: Nach dem Laden eines Mehrwerkzeug-Projekts werden Farb-, Farbart-, Mehrfarben- und Mapping-Arrays zusammen mit den Filamentprofilen auf die Werkzeugzahl erweitert. Punkt 3 gleicht 23 Flashforge-Druckervarianten mit Orca-Standardprozessen ab und aktiviert die Creator-5-Pro-Kammersteuerung. Punkt 4 korrigiert die SV08-Standardprozesse für 0,4- und 0,8-mm-Düsen. Punkt 5 ergänzt die 0,2- und 1,0-mm-Düsenvarianten für den SV08 MAX samt passenden Prozessen. Punkt 6 schließt abgeschnittene Gyroid-/TPMS-Ringe nach Polygon-Clipping; die Regression prüft 54 Muster-/Linien-/Phasenfälle. Punkt 7 korrigiert die Prusa-XL-Deretraktionswerte nach Orca `0edd6693a6`; alle 12 XL-Varianten und ein echter Profil-Slice bestehen auf Android 16/API 36. Punkt 8 verwirft alten G-Code, wenn sich das physische ACE-Inventar seit dem Slice ändert. Punkt 9 ergänzt für Flashforge-0,32-mm die Orca-Linienbreite 0,82 mm, damit die bestehende Düsenfilterung es bei 0,6-mm-Düsen ausblendet und bei 0,8 mm anbietet. Punkt 10 portiert Orca `9612260bb5`: nach dem Clipping werden abgeschnittene Multiline-Infill-Ringe in sechs aktiven Generatoren wieder verbunden. Die API-36-Regressionsprüfung bestand 90 Muster-/Loch-/Phasenkombinationen plus die bestehende Gyroid/TPMS-Prüfung. Damit steht Batch 14 bei **10/10 fokussiert verifizierten Punkten**; Debug-/Slice-Unit-Suiten (je 764/764), APK-Build, API-36-Installation/Start und Logprüfung sind erfolgreich. Release-Gate abgeschlossen: `v0.8.452-nightly.1` veröffentlicht, APK 240,670,708 Byte, SHA-256 `A6D9A8582CF71EE89AED1A60017B48544FDD59D199E87598CD64A7D6CD1D9601`; die fünf jüngsten Releases 0.8.452 bis 0.8.448 bleiben erhalten. Ein separater Skalierungsfehler bei gedrehten Modellen zählt nicht als Orca-Batch-Punkt.

### Batch 14, Punkt 10/10 — abgeschnittene Multiline-Infill-Ringe verbinden

Port von Orca `9612260bb5`: Nach `intersection_pl()` werden die Enden von geschlossenen Multiline-Konturen innerhalb `SCALED_EPSILON` wieder verbunden. Der Helfer wirkt nur bei `multiline > 1`. Aktiviert wurde er in Adaptive-Cubic, Cross-Hatch, Honeycomb, Lightning, Plane-Path und Rectilinear. Rectilinear führt die Verbindung nach dem Entfernen überlappender Lateral-Lattice-Linien aus; die abweichende Android-Trapezfüllung Grid/Triangles/Stars führt sie direkt nach ihrem eigenen Clipping aus.

`NativeSliceDeviceTest#multilineInfillClippedRingEndpointsStayOnTheBoundary` verwendet die produktiven Fill-Generatoren mit passender Objekt-Bounding-Box und prüft 15 Muster, mit/ohne Innenloch und drei Z-Phasen: 90 Kombinationen, 0 fehlgeschlagen. Die bestehende `gyroidAndTpmsClippedRingsKeepTheirEndpointsOnTheBoundary`-Prüfung bestand zusätzlich 1/1. Ein erster Fixturelauf deckte auf, dass vier Muster die im normalen Slice gesetzte Objekt-Bounding-Box benötigen; diese wurde in der Fixture ergänzt. Kein Produktfehler wird aus dem fehlenden Fixturezustand abgeleitet.

### Batch 14, Punkt 9/10 — Flashforge 0,32-mm-Profil an 0,8-mm-Düse binden

Orca `e5eb684a99` ergänzt die explizite Linienbreite für den Creator-5-Prozess `0.32mm Standard @FF C5 0.8 nozzle`. In der Android-INI-Datei war die `line_width`-Option leer, daher konnte die vorhandene Kompatibilitätsprüfung das Profil nicht zuverlässig von einer 0,6-mm-Düse fernhalten. Das gebündelte Profil nennt nun `line_width = 0.82`; die bereits verwendete Düsenprüfung lehnt es bei 0,6 mm ab und lässt es bei 0,8 mm zu.

`NativeSliceDeviceTest#flashforge032ProcessIsLimitedToNozzlesThatFitItsLineWidth` lädt das reale `Flashforge.ini`-Profil und prüft Layerhöhe, Linienbreite sowie beide Düsenfälle. API 36: 1/1 bestanden. Kein Druckerzugriff und kein Druckstart.

### Batch 14, Punkt 8/10 — ACE-Inventaränderungen entwerten alten Slice

Nach Orcas AMS-Inventaränderungsschutz (`6839c9d344`) merkt sich Nightly beim Erzeugen eines Kobra-3-Slices die stabile physische Inventarprojektion der ACE-Fächer. Vor dem Upload wird sie nach der Live-Synchronisierung erneut geprüft. Änderungen an Farbe, Material, Belegung oder T→Fach-Abbildung verwerfen G-Code und Vorschau und verlangen einen neuen Slice; volatile Statusfelder lösen keine ungültige Sperre aus. Unbekannte/fehlende Ausgangsdaten werden gemäß Orcas Unknown-Payload-Verhalten nicht als nachgewiesene Änderung bewertet.

`SlicePreparationToolsTest#onlyKnownChangedMmuInventoryInvalidatesASlice`: SliceTest-JVM 1/1. `MmuFilamentSyncDeviceTest#sliceInventorySignatureTracksColorMaterialOccupancyAndToolMappingOnly`: Android 16/API 36 1/1; prüft stabile volatile Statusdaten und echte Farb-, Material-, Mapping- und Belegungsänderungen. Kein tatsächlicher Druckstart. Der neue Punkt ist damit fokussiert verifiziert; die Batch-Gesamtsuiten sowie APK/Release warten weiterhin auf Punkt 10.

### Batch 14, Punkt 7/10 — Prusa XL Deretraktionsgeschwindigkeit

Port von Orca `0edd6693a6`: Die Prusa-XL-Profile verwenden für Deretraktion die Orca-Werte statt des veralteten pauschalen 40-mm/s-Werts. Die sechs 0.25–0.6-mm-Varianten (XL und XL 5T) verwenden 25 mm/s; beide 0.8-mm-Varianten verwenden den spezifischen Wert 15 mm/s.

`NativeSliceDeviceTest#orcaPrusaXlNozzleProfilesUseValidDeretractionSpeedAndSlice` prüft alle zwölf Profilwerte, den effektiven nativen Wert `deretraction_speed=25` in einer XL-0.25-Konfiguration und einen tatsächlichen Slice mit Extrusion. Android 16/API 36: 1/1 bestanden. Der Test ergänzt nur in seiner temporären Slice-Konfiguration `G92 E0` in `layer_gcode`, weil der aktuelle native Validator den vorhandenen Reset in Prusas `before_layer_gcode` nicht berücksichtigt; die ausgelieferte Profilkonfiguration wird dadurch nicht verändert.

### Batch 14, Punkt 3/10 — Flashforge-Standardprozesse und Kammersteuerung

Abgleich mit Orca `b46bf81396`: Die 23 im Android-Paket enthaltenen AD5X-, Adventurer-3-, Creator-5- und Guider-4-Varianten verwiesen teilweise auf falsche oder gar nicht gebündelte Standardprozessprofile. Sie verwenden jetzt den passenden vorhandenen Prozess nach Düsendurchmesser. Creator-5-Pro-Start-G-Code enthält kein festes `M191 S0` mehr; die Kammer-Temperatursteuerung ist für die 0.8-mm-Variante aktiviert. Die in diesem Nightly nicht enthaltene Creator-5-Pro-0.25-mm-Variante wurde nicht neu erfunden.

`NativeSliceDeviceTest#flashforgeBundledNozzleDefaultsResolveAndCreator5ProChamberControlIsAvailable` bestand auf Android 16/API 36 (1/1): alle 23 Standardprozessnamen werden über `Slic3rConfigWrapper.findPrint()` aufgelöst, alle drei Creator-5-Pro-Start-G-Codes lassen Kammerbefehle zu, und die 0.8-mm-Variante meldet `support_chamber_temp_control=1`. Keine Druck-/Hardwareaktion ausgeführt.

### Batch 14, Punkt 4/10 — Sovol SV08-Standardprozesse

Port von Orca `ca9204927e`: Die 0.4-mm- und 0.8-mm-SV08-Varianten wählten fehlerhafte Standardprozesse. In der gebündelten Android-Bibliothek zeigen sie nun auf die vorhandenen `0.20mm Standard`- und `0.40mm Standard`-Prozesse. `NativeSliceDeviceTest#sovolSv08BundledDefaultsResolveForFourAndEightTenthNozzles` bestand auf Android 16/API 36 (1/1) und prüft beide Düsenvarianten sowie die tatsächliche Auflösung der ausgewählten Prozesse.

### Batch 14, Punkt 5/10 — Neue Sovol SV08 MAX Düsenvarianten

Port von Orca `1f7e195d88`: Der SV08 MAX unterstützt jetzt die 0.2-mm- und 1.0-mm-Düsenvarianten mit den passenden Minimal-/Maximallayerhöhen und Orca-Prozessen (`0.10mm Standard` bzw. `0.50mm Standard`). Der Druckermodell-Eintrag listet jetzt die Düsen 0.2, 0.4, 0.6, 0.8 und 1.0 mm. Die neuen Prozessprofile behalten Orcas passende Düsenbreiten, Layerhöhen und Druckereignung.

`NativeSliceDeviceTest#sovolSv08MaxNewNozzleVariantsLoadAndProduceSlices` bestand auf Android 16/API 36 (1/1). Der Test prüft Modellvarianten, Drucker- und Prozessauflösung, Druckereignung der Prozessprofile und effektiven Düsendurchmesser. Anschließend erzeugt er mit beiden neuen Profilen jeweils einen echten Slice mit extrudierenden G-Code-Bewegungen.

### Batch 14, Punkt 6/10 — Gyroid- und TPMS-Ringe nach Clipping schließen

Port von Orca `4cfdccfe45`: `intersection_pl()` kann einen geschlossenen Gyroid-/TPMS-Ring an einem beliebigen Punkt aufschneiden. Gyroid, TPMS-D und TPMS-FK rufen nach dem Clipping jetzt `join_close_ends(..., SCALED_EPSILON, true)` auf, damit offene Pfade wieder an der Randwand enden und vollständige innere Ringe nicht fälschlich am Rand beginnen.

`NativeSliceDeviceTest#gyroidAndTpmsClippedRingsKeepTheirEndpointsOnTheBoundary` prüft die Produktions-Fill-Generatoren mit drei Mustern, multiline 1/2 und neun Z-Phasen (54 Konfigurationen). Mit dem alten Code verletzten 247 erzeugte Pfade die Rand-/Ring-Invariante; mit dem Fix bestanden alle 54 Konfigurationen. API-36-Emulator, 1/1 Test bestanden. Der Android-Fill-Pfad enthält die klassische/optimierte Gyroid- und TPMS-Variante; Orcas zusätzliche adaptive-infill-Modusvarianten sind in dieser Android-Version nicht vorhanden und werden nicht als geprüft behauptet.

### Batch 14, Punkt 2/10 — Mehrwerkzeug-Projekte mit kurzen Filamentarrays

Port von Orca `b5c4ccd9c5`: Nach `update_multi_material_filament_presets()` kann die Liste der Filamentprofile bereits auf die Werkzeugzahl aufgefüllt sein, während die parallel gespeicherten Projektarrays noch kürzer sind. Der Importpfad gleicht nun `filament_colour`, `filament_colour_type`, `filament_multi_colour`, `filament_map` und Spülmatrix vor dem nächsten Sync/Slice über `set_num_filaments()` an.

`NativeSliceDeviceTest#projectWithFewerFilamentsThanToolsKeepsAllSlotArraysInSync` besteht auf Android 16/API 36 (1/1). Der Produktions-`PresetBundle.load_config_model()`-Pfad lädt ein Vierwerkzeug-Projekt mit zwei Farben, bestätigt die beiden importierten Farben und Array-/Spülmatrixgrößen und führt danach einen Überschreib-Sync mit vier tatsächlichen Materialfächern aus. Die Test-JVM-Suiten bestanden mit 763/763 Debug und 763/763 Slice. Ein anfänglicher Crash stammte aus einem unvollständig initialisierten `full_config()`-Fixture im Test-Harness; der Harness wurde auf eine vollständige native Standardkonfiguration umgestellt und der Test danach erneut bestanden. Keine APK/Veröffentlichung vor dem 10/10-Gate.

Batch 12 ist vollständig mit 10/10 Punkten verifiziert und als `v0.8.435-nightly.1` veröffentlicht. Batch 13: Punkt 1 portiert Orcas natives `Spiral Inset`-Füllmuster; Punkt 2 portiert Orcas Kopierreihenfolge für `By layer` + `As object list`; Punkt 3 portiert Orcas Warnung für Objekt-/Höhenbereichs-Overrides, die Spiral-Vase kontinuierliche Extrusion verhindern; Punkt 4 schützt leere Compose-Menüflächen davor, Gesten an den 3D-Renderer durchzureichen; Punkt 5 optimiert die Tree-Support-Suche nach unteren Kontaktflächen; Punkt 6 gruppiert Tree-Support-Polygone vor der Clipper-Union. API-36-Tests für jeden Punkt sind in den Abschnitten unten vermerkt. Das APK-/Release-Gate bleibt bei 10/10 Punkten plus gebündelter Regression.

Punkt 7/8/9 sind in `docs/UI2_EXECUTION_STATE.md` nachgewiesen. Punkt 9 portiert Orcas By-Object-Kopierreihenfolge für gedrehte Instanzen: Androids sequenzielle Kollisionsprüfung sortiert nun nach der sichtbaren Objekt- und Instanzposition statt nach der transformabhängigen `PrintObject`-Gruppierung. Android 16/API 36: `NativeSliceDeviceTest#byObjectPrintingKeepsRotatedCopiesInTheirListOrder` 1/1 bestanden; vier wechselnd gedrehte Kopien ergeben die Produktionsreihenfolge `[0, 1, 2, 3]`. Ein gezieltes Nightly-Prerelease 0.8.448 wurde für den aktuellen Arrange-Follow-up gebaut; dies zählt Batch 13 nicht als abgeschlossen. Das Batch-Gate bleibt ein neuer Punkt 10 plus gebündelte Regression.

### Batch 13, Punkt 5/10 — Tree-Support-Kontaktflächen einmal sammeln und begrenzen

Port von Orca `96ec6164c6`: Tree-Support sammelt die oberen und unteren Objektflächen einmal je Slice statt sie für jede Supportfläche erneut je darunterliegender Objektschicht zu konstruieren. Die Suche verwirft Schichten außerhalb der Kontakt-Bounding-Box und beendet den Abstieg, sobald eine noch tiefere Kontaktfläche außerhalb des Bottom-Gap- und Interface-Bereichs liegt. Dadurch bleibt die Android-Slice-Logik fachlich auf echte Modellkontakte begrenzt und spart wiederholte Polygonarbeit.

`NativeSliceDeviceTest#treeSupportBottomContactCachePreservesInterfaceToolpaths` slicet ein zweistufiges STL zweimal mit Tree-Support, 0,2-mm-Bottom-Gap und zwei unteren Interface-Schichten. Der Test bestätigt, dass die Konfiguration in den Native-Slice gelangt und dass beide Slices genau zwei untere Kontakt-Interface-Z-Höhen mit ausführbaren G-Code-Bewegungen erzeugen. Android 16/API 36: 1/1 bestanden. Die ursprüngliche Idee eines byte-/reihenfolgegleichen G-Code-Vergleichs wurde verworfen, weil Tree-Supports parallel angeordnete Pfade nicht deterministisch sortieren; die fachliche Kontaktlayer-Prüfung ist der verwendete Nachweis. Noch kein APK/Release; Batch-Gate bleibt 10/10 plus gebündelte Regression.

### Batch 13, Punkt 6/10 — Überlappende Tree-Support-Basisflächen gruppiert vereinigen

Port von Orca `09f8ee54df`: `union_overlapping_ex()` ordnet große Polygonmengen nach X-Bounds, vereinigt sie in Gruppen von 16 und führt die Gruppen paarweise zusammen. `TreeSupport::draw_circles()` nutzt den Helper vor dem Abzug der Dachflächen, damit Clipper bei dichten Astkreisen weniger innere Kanten gleichzeitig verarbeitet. Bis zu 16 Polygone behalten exakt den bisherigen Union-Pfad; Rundungsabweichungen bei größeren Mengen sind unter einem Quadratmikrometer begrenzt.

Verifikation auf Android 16/API 36: `NativeSliceDeviceTest#treeSupportGroupedUnionPreservesDensePolygonRegions` 1/1 bestanden (576 synthetische Kreis-Polygone; Topologie, symmetrische Differenz unter 1 µm², kleiner und leerer Input geprüft); `#treeSupportBottomContactCachePreservesInterfaceToolpaths` 1/1 und `#organicTreeSupportPathsAreStableAcrossRepeatedSlices` 1/1 ebenfalls nach Integration bestanden. ARM64-Nativbuild und Instrumentierungsbuild erfolgreich. Kein Timing-Benchmark oder APK/Release in diesem Zwischenpunkt; Batch-Gate bleibt 10/10 plus gebündelte Regression.

Nächster Schritt: Orca-Upstream-Punkt 7 nach einem nicht bereits vorhandenen, Android-erreichbaren Verhalten untersuchen.

### Batch 13, Punkt 3/10 — Warnung bei Spiral-Vase-Overrides

- Orca-Quelle: `70c12f5cba` — `Add warning for spiral vase mode with object settings overrides`.
- Der Native Slice-Aufruf bewahrt die `Print::validate()`-Warnung für `sparse_infill_density`, `wall_loops` und `top_shell_layers` am `GCodeResultRef`.
- Die Druckvorbereitung zeigt die Warnung vor dem Upload/Print. Bestätigen merkt die Warnung für den aktuellen Dialoglauf als gelesen; beim nächsten Klick auf „Senden“ wird die Warnung nicht erneut blockierend wiederholt.
- Der Regressionstest erzeugt einen gültigen Höhenbereich mit eigener Layerhöhe und 25 % Füllung. Die explizite Layerhöhe ist notwendig, damit Orcas Validierung die Layerhöhenkurve sicher berechnen kann.
- Verifikation auf API 36 (Android 16): `NativeSliceDeviceTest#spiralVaseReportsObjectRangeOverridesBeforePrinterSend` 1/1 bestanden; `SlicePrintPreparationComposeDeviceTest#spiralVaseOverrideWarningIsShownAndRequiresAcknowledgement` 1/1 bestanden; Slice-/Instrumentierungsbuild und Kotlin-Kompilierung erfolgreich.

### Batch 13, Punkt 4/10 — Leere Workspace-Menüflächen fangen keine 3D-Gesten ab

Port von Orca `b2978d1c73`: Touches auf leerem Werkzeugleisten-/Menübereich dürfen nicht als Szenenauswahl, Kamerageste oder Malaktion beim 3D-Viewport ankommen. Der Android-Workspace versieht den über der Druckplatte liegenden Compose-Menühost mit einem Pointer-Handler im Main-Pass. Interaktive Kinder bearbeiten Klicks und Scrolls zuerst; der Host konsumiert nur danach weiterhin unbeanspruchte Eingaben.

API-36-Gerätetest `WorkspaceMenuTouchShieldDeviceTest#unclaimedMenuGestureDoesNotReachRendererAndMenuButtonsStillWork` bestanden (1/1): eine Touch-/Move-Geste auf freier Menüfläche erreicht die darunterliegende AndroidView/Renderer-Testfläche nicht; ein Menübutton bleibt klickbar. Ein zusätzlicher manueller Scrollassertion-Prototyp war selbst ohne Shield fehlerhaft und wird nicht als Scrollbeleg gezählt. Es wird kein Scrollverhalten durch diese Änderung behauptet.

### Batch 13, Punkt 2/10 — Kopienreihenfolge bei By-layer / As-object-list

Port von Orca `a620d804ef`: `sort_object_instances_by_model_order()` nummeriert Kopien nach ihrer Position in den Modell-Objekt- und Instanzlisten. Dadurch behalten verschieden gedrehte Kopien eines Objekts ihre Listenreihenfolge, obwohl `Print::apply()` sie in getrennte `PrintObject`s gruppiert.

Verifikation: `:app:assembleSliceTest :app:assembleSliceTestAndroidTest` erfolgreich (ARM64). `NativeSliceDeviceTest#byLayerAsObjectListKeepsRotatedCopiesInTheirListOrder` auf Android 16/API 36 bestanden (1/1, 0 Fehler/Skips); `Print::validate()` und die Produktions-Sortierfunktion wurden mit 20 abwechselnd gedrehten Kopien geprüft. Noch kein APK-/Release-Gate.

## Batch 12 – abgeschlossen

Batch 11 ist mit 10/10 als `v0.8.434-nightly.1` veröffentlicht. Batch 12 hat sechs unabhängig verifizierte Orca-Verhaltenspunkte. Es gibt noch keine APK oder Veröffentlichung für Batch 12; das Release-Gate bleibt bei 10/10 plus gebündelter Regression.

Punkt 5 portiert Orca `3bda653448`: Prime-Tower-Statistik folgt dem Drucker-G-Code-Tagdialekt. Punkt 6 portiert Orca `d607d7f4ad`: klassisches Gap-Fill wählt das aktive Wandfilament abhängig von der Zahl tatsächlich gedruckter Wände. Beide Slice-Regressions liefen auf Android 16/API 36 erfolgreich (je 1/1, 0 Fehler/Skips). Es gibt noch keine APK oder Veröffentlichung für Batch 12.

### Punkt 4/10 — Gelöschtes Filament bei Shared-Nozzle-MMU nicht wiederherstellen

Port von Orca `e2590f7559`: Die Mindestzahl der Filament-Presets richtet sich bei `single_extruder_multi_material` nach einem geteilten Düsenpfad nach **einem** Filament statt nach der physischen Düsenanzahl. Damit fügt die Preset-Bundle-Normalisierung ein bewusst entferntes Filament nicht erneut hinzu; unabhängige Tool-Changer-Düsen behalten weiterhin ein Preset je Düse.

Verifikation: ARM64-Nativbuild und Instrumentierungskompilierung bestanden. Android-16/API-36-Gerätetest `NativeSliceDeviceTest#sharedNozzleMultiMaterialDoesNotRestoreDeletedFilamentSlot` 1/1 bestanden. Der Test ruft `PresetBundle.update_filament_count()` und `update_multi_material_filament_presets()` mit zwei Düsen auf, prüft **1** Filament bei aktiviertem Shared-Nozzle-MMU und danach **2** im Tool-Changer-Modus. `git diff --check` sauber. Kein APK-/Release-Gate ausgelöst.

### Punkt 3/10 — Support-Malen nur auf Überhängen

Orca `e105d3c587` schließt die eingestellte Überhang-Winkelgrenze ein, indem die Facettenauswahl einen Grad Toleranz erhält. Android bietet jetzt im Support-Malmodus einen optionalen „Nur Überhänge bemalen“-Filter mit einstellbarem Winkel. Der Filter gilt für Pinsel, Bucket und Höhenbereich; die Bucket-Ausbreitung stoppt an Facetten außerhalb des Overhang-Winkels. Ohne aktivierten Filter bleibt das bisherige Verhalten unverändert.

Verifikation: ARM64-SliceTest- und Instrumentierungs-APK erfolgreich erstellt; API-36-Gerätetest `supportPaintOverhangFilterRestrictsBrushBucketAndHeightToDownwardFaces` 1/1 und Compose-Test `supportPaintShowsOverhangFilterAndThresholdOnlyWhenEnabled` 1/1 bestanden. Der native Grenzwinkel-Probe prüft eine 45°-Fläche bei 45° (angenommen) und 43,5° (abgelehnt). `git diff --check` erfolgreich. Code-Commit `bcdac02ba2` ist auf `nightly25/codex/nightly-batch11-next` gepusht. Batch 12: **3/10**.

### Punkt 2/10 — Ungültige Layer-Sequenzkonfiguration sicher ignorieren

Port von Orca `7e3d5d4d04` in `get_other_layers_print_sequence`: nichtpositive Gruppenzähler, nicht teilbare Wertevektoren und Gruppen ohne Layerbereich plus Extruder werden vor jedem Indexzugriff verworfen. Damit kann ein inkonsistenter 3MF-/Projektwert den nativen Tool-Ordering-/Slice-Pfad nicht mehr über leere oder unvollständige Gruppen lesen lassen.

`NativeSliceDeviceTest#malformedOtherLayerPrintSequenceIsIgnoredAndStillSlices` bestand auf Android 16/API 36 (1/1). Der Produktionshelper wurde direkt mit Zähler 0/negativ, zu kurzen und nicht teilbaren Gruppen sowie gültigen Gruppen geprüft; zusätzlich slicet ein Würfel mit absichtlich inkonsistentem gespeicherten Zähler und erzeugt weiterhin gültigen extrudierenden G-Code. Debug- und Slice-Unit-Suiten: jeweils 749/749, 0 Fehler/Skips. Code-Commit `9ac5d504e0` ist in den gepushten Branch integriert. Kein APK-/Release-Gate ausgelöst.

### Punkt 1/10 — Orca-Infill-Verbindungsoptimierung

Port von Orca `69193d5681` für multiline-bewusste Infill-Verknüpfung, stabile Wahl gleich langer Konturabschnitte und Hook-Platzierung zum nächsten offenen Pfadende. API-36-Slice-Regression, Geometriegrenzen und beide JVM-Suiten bestanden; Commit `8ab43b6ed5` ist auf `nightly25` gepusht.

## Nightly Batch 11 — Punkt 8/10 (Stand 2026-11-04)

> **Aktueller Fortschrittshinweis (2026-11-04):** Die folgenden Batch-11-Abschnitte sind historische Arbeitsnotizen und ihr jeweiliger Punktestand ist nicht mehr der aktuelle. Batch 11 wurde mit 10/10 Punkten als `v0.8.434-nightly.1` veröffentlicht. Batch 12 steht bei **3/10**; aktueller gepushter Code-HEAD `bcdac02ba266330f20c81a2016df2a106bc860da`. Die maßgebliche Fortsetzung und jüngsten Prüfungen stehen in `docs/UI2_EXECUTION_STATE.md`.

Der Nightly-Branch enthält bereits die geprüften Orca-Kernänderungen `533d4c3e9e` (Bridge-Speed bei Curling-Slowdown), `fa5f7e3db4` (Körpernamen beim STEP/AMF/3MF-Import), `896557bce5` (Spiralvase mit Höhenbereichen), `041df4031e`/`05a92cd0cf`/`8cdc68a6a0`/`a30ca5e54a` (By-Object-Anordnung und Kollisionsreihenfolge), `5191a5efeb` (stabile Tree-Support-Reihenfolge) und `1c9f6f9318` (Prime-Tower-Sliver bei präzisem Z). Diese bereits vorhandenen Implementierungen werden nicht erneut als Batch-Punkte gezählt.

### Punkt 8/10 — Wipe-Inward mit korrekter Retraktionsaufteilung und Sandwich-Wandversatz

Port von Orca `0b041740b1`: Der Android-Slicer übernimmt die Wipe-Inward-Einstellungen in den nativen
Prozesskonfigurationspfad und erzeugt die angepasste Wipe-Geometrie auch bei `inner wall/outer wall`
und `inner-outer-inner wall`. Die neue gemeinsame Geometriehilfe wird vom nativen G-Code-Pfad verwendet;
die Option invalidiert die betroffenen Slice-/Export-Schritte und ist im Prozessprofil-Katalog verfügbar.

`NativeSliceDeviceTest#wipeInwardChangesOnlyTheGeneratedWipePathAndKeepsGcodeValid` führt echte
ARM64-Slices des Würfelfixtures mit Option aus/an für beide Wandreihenfolgen durch. Er prüft, dass die
Option im nativen Config-Export ankommt, Wipe-Extrusionssegmente erhalten bleiben, der resultierende
G-Code Extrusion enthält und die markierten Wipe-Bewegungen sich ändern. API 36: 1/1 bestanden, 0
Fehler/Skips. Zusätzlich: ARM64-Native-Build, Profilkatalog-/Import-Routingtests 15/15 in Debug und
15/15 in Slice sowie Android-Instrumentierungsquellen kompiliert. Kein physischer Druckertest.

Batch 11 steht damit bei **8/10**. Noch zwei unabhängige Android-erreichbare Orca-Verhaltenspunkte,
gebündelte Regression, Debug-APK und Nightly-Prerelease sind offen.

### Punkt 7/10 — Bounds-Schutz bei bemalter Multi-Material-Segmentierung

Teilportierung von Orca `08dcc0adfc`: Der native Slice-Pfad begrenzt den Zugriff auf
bemalte Segmentierungskanäle pro Layer auf die tatsächlich vorhandenen Zeilenkanäle.
Dadurch bleibt der Pfad sicher, falls importierte Filament-Presetvektoren länger sind als
die Segmentierung, die aus einer unvollständigen Farbpalette aufgebaut wurde. Die Bounds-
Policy liegt in einem gemeinsam genutzten nativen Helper.

`NativeSliceDeviceTest#paintedSegmentationUsesFilamentCountWhenColorPaletteIsShorter`
prüft beide Größenrichtungen direkt über die Produktions-Policy und slicet anschließend
ein bemaltes Modell trotz verkürzter Projektpalette bis zu T1-Extrusion. Android 16/API 36:
1/1 bestanden, 0 Fehler/Skips. ARM64-C++- und Instrumentierungsbuild sowie `git diff --check`
bestanden. Die JNI-Vorbereitung normalisiert übliche Importpaletten; deshalb wird der
inkonsistente Zeilenfall durch den direkten Policy-Assert und der normale Slice-Roundtrip
separat abgedeckt.

### Vorherige Punkte 4–6/10

### Punkt 4/10 — Reihenfolge gleichartiger Kopien beim sequenziellen Drucken

Android-Port von Orca `8cdc68a6a0`. Die Kahn-Sortierung in `sequential_print_clearance_valid`
bricht Gleichstände nicht länger nur anhand des gemeinsamen Objektindex, sondern anhand der
ursprünglichen Instanzlistenposition. So bleibt die Reihenfolge von Kopien desselben Objekts
deterministisch, solange keine Kollisionsbedingung eine sichere Umordnung erfordert.

`NativeSliceDeviceTest#sequentialPrintKeepsCopiesOfOneObjectInTheirListOrder` erzeugt und slicet
200 Kopien desselben Modells, liest die ersten XY-Extrusionsbewegungen aus dem G-Code und
bestätigt die ursprüngliche 15-Spalten-Reihenfolge auf Android 16/API 36 (1/1 bestanden).
Der erste Testlauf deckte einen zu strengen Test-Assert auf (E-only-Prime wurde fälschlich als
XY-Bewegung behandelt); nach Präzisierung bestand der Test. `git diff --check` bestanden.
Noch kein APK/Release; Batch 11 steht bei 4/10.

### Punkt 5/10 — By-object-Rod-Kollisionen anhand der wirklich erreichbaren Geometrie

Teilportierung von Orca `05a92cd0cf`: Beim sequenziellen Drucken berücksichtigt die
Rod-Kollisionsprüfung nur Mesh-Flächen, die tatsächlich oberhalb der Führungsstange liegen.
Draft-Shield, ausreichend hohe Skirts und Raft-Höhe bleiben Teil der Sicherheitsprüfung.
Der native Konfigurationswert `extruder_clearance_dist_to_rod` wird als Druckerparameter
gespeichert und in den Druckeroptionen angeboten.

`NativeSliceDeviceTest#byObjectClearanceUsesOnlyGeometryAboveTheRod` slicet getrennte
Finnenkörper mit `print_sequence=by object`, prüft den Wert in der nativen Konfiguration
und erzeugt echten G-Code; API-36-AVD 1/1 bestanden. Fokussierte Profil-/Katalog- und
Rundlauftests 10/10; `git diff --check` sauber.

### Punkt 6/10 — Precise-Z-Sliver-Layer am Prime Tower zusammenführen

Teilportierung von Orca `1c9f6f9318`: Zu dünne Tower-Layer werden dem vorherigen Tower-Z
zugeordnet. Tower-Planung und G-Code-Ausgabe verwenden die gemeinsame Höhe, bewahren den
Tower-Finish-Zustand und verhindern einen Abstieg über das Modell.

`NativeSliceDeviceTest#preciseZSliverLayersJoinThePrimeTowerLayerBelowAndStillSlice`
bestand auf dem API-36-x86_64-AVD mit ARM64-Übersetzung (1/1, 0 Fehler/Skips). Der echte
Slice prüft T0/T1, Tower-Extrusion, vollständigen G-Code und dass Tower-Extrusions-Z-Werte
an tatsächlich gedruckte Objekt-Layer-Z-Werte angepasst sind. ARM64-Native- und
Instrumentierungs-APK-Builds, Slice-JVM-Suite und `git diff --check` bestanden.

Nächster Schritt: Punkt 7 aus einem fehlenden Orca-Verhalten mit aktivem Android-Aufrufpfad
auswählen und mit einem eigenen Verhaltensregressionstest absichern. Keine Wiederholung
bereits portierter Änderungen; vollständige Batch-Regression, APK und Release erst bei 10/10.

---

## Vorheriger Stand — Punkt 3/10

### Punkt 3/10 — Originale 3MF-Körpernamen in importierten Baugruppen

Teilportierung von Orca `fa5f7e3db`. Beim generischen Modellimport werden Namen aus den
eingebetteten 3MF-Geometriekörpern als Volume-Namen verwendet, falls die Importdaten für das
Volume keinen eigenen Namen enthalten. Bereits gespeicherte Volume-Metadaten bleiben maßgeblich;
Projektkonfigurationen schalten die Importoption nicht ein.

`TwoColorProjectDeviceTest#genericThreeMfAssemblyImportKeepsOriginalPartNames` erzeugt aus dem
zwei-farbigen 3MF-Fixture eine einzelne Assembly, die zwei separat benannte Geometriekörper
referenziert. Android 16/API 36 bestand die vollständige Geräteklasse 7/7: ein Assembly-Eintrag,
der Projektname „Assembly Parent“ sowie die ursprünglichen Teilnamen „Testwuerfel 1“ und
„Testwuerfel 2“ bleiben erhalten. ARM64-Native-Build, Android-Testkompilierung und
`git diff --check` bestanden. Keine APK/Release vor Batch 11 10/10.

### Punkt 2/10 — Spiral-Vase-Slicing bei Höhenbereichs-Overrides

Android-Port von Orca `896557bce`. Die Auswahl des größten Außenkontur-Schnitts und die
Schwelle für reguläre Bodenschichten werden nun auch bei mehreren Slice-Regionen aus
`layer_ranges` ermittelt. Damit bleibt die Vase-Geometrie korrekt, wenn ein Objekt lokale
Höhenbereichs-Overrides besitzt.

`NativeSliceDeviceTest#spiralVaseAcceptsSingleFilamentHeightRangeOverrideAndProducesGcode`
bestand auf Android 16/API 36 (1/1, 0 Fehler/Skips): tatsächlicher G-Code enthält Extrusionen
und mehrere Layer, aber keine inneren Wände oberhalb der Vase-Bodenschicht. Der bisherige
Android-Pfad setzte den Vase-Schnittmodus nur bei genau einer Slice-Region.

### Punkt 1/10 — Adaptive Pressure Advance ohne Messwerte

Orca `2cb2f0d2e710931b835d1b31282479686d961b47` trennt bei Adaptive Pressure Advance
den Fall eines leeren beziehungsweise ausschließlich aus Nullwerten bestehenden Modells
als fehlende Messungen ab. Android prüft jetzt vor dem Slice die tatsächlich verwendeten
Filamente und die zugehörigen Variantenspalten und zeigt dafür eine filamentbezogene
Fortsetzungswarnung. Ein echter Slice-Test auf API 36 bestätigt sowohl die Warnung als auch
die weiterhin erzeugte G-Code-Datei; Unit-Tests: 8/8, Instrumentierung: 1/1. Das ist Punkt
1/10. Batch-APK und Nightly-Release bleiben bis zum vollständigen 10/10-Regressionsgate offen.

## Nightly Batch 10 — 10/10 fokussiert verifizierte Punkte (Stand 2026-11-03)

Der gültige Zähler steht auch in `docs/UI2_EXECUTION_STATE.md`. Die erste Fassung dieser
Roadmap blieb bei 6/10 stehen, obwohl danach die STEP-Importoption und die isolierte
CGAL-Vertex-Prüfung separat implementiert und verifiziert wurden. Nicht als neue Punkte
gezählt werden die Bridge-Speed-Regression (Batch 9) und die Z-Schrumpfung über
Höhenbereiche (Batch 7), obwohl sie im selben 3/3-Testlauf erneut mitliefen.

Die zehn gezählten, voneinander abgrenzbaren Verhaltensänderungen sind: STEP-Körpernamen
erhalten; organische Tree-Support-Spitzen deterministisch sortieren; Temperaturen des
tatsächlich zuerst verwendeten Werkzeugs in den G-Code-Header schreiben; Arrange bei
nahezu bettgroßen Teilen; bemalte Facetten nach Mesh-Glättung erhalten; Variantenwerte
bei Filament-Slot-Änderungen bewahren; STEP-Baugruppen standardmäßig in benannte
Körper teilen; CGAL-Volumenprüfungen gegen isolierte Vertices absichern; und die
Grundkörperauflösung abhängig von ihrer Größe anpassen; sowie die gemeinsame PrintaLot-
PLA-Materialbibliothek ohne Druckerprofil laden und slicen.

Die zehn Punkte sind fokussiert verifiziert. Das gebündelte Regressionstor ist bestanden,
und der Nightly-Prerelease `v0.8.432-nightly.1` wurde veröffentlicht:
https://github.com/Exa201/OrkanSlicer-Nightly-2.5/releases/tag/v0.8.432-nightly.1 . Debug
743/743, Slice 743/743 und fünf
fokussierte API-36-Gerätetests. Debug-APK 0.8.432 / Code 467 wurde auf einem API-36-AVD
installiert und gestartet; frisches Logcat ohne Crash, ANR, OOM oder Fatal Signal. APK:
`OrkanSlicerNightly25_86b89bde81.apk`, 240.359.789 Bytes, SHA-256
`0EA04F2617545FFE11FCEFCC3D62F82DACA5AFEACEA1A54B9BA7D6C1A5963AB5`. Xiaomi nicht
angeschlossen; der physische Xiaomi-Test bleibt offen.

Als nächster Schritt wird Orca `nightly-builds` am Upstream-Stand
`26a39377713c774d52ca7d7ff7242188df4de63e` systematisch gegen Android-Aufrufpfade geprüft.
Die bereits enthaltenen Änderungen `6c96e4116` (Scarf-Ramp-Ende) und `533d4c3e9`
(Bridge-Speed bei Curl-Slowdown) werden nicht als neue Punkte gezählt. Batch 11 beginnt erst
mit einem noch fehlenden Android-Pfad und einem eigenständigen Verhaltenstest.

### Punkt 10/10 — PrintaLot-PLA-Materialbibliothek

Das gemeinsame Orca-Profil aus `PrintaLot.ini` wird im Android-Profilordner als
`filament_library` geladen, ohne Druckerprofile hinzuzufügen. Der Test prüft warnungsfreien
Import, die materialbezogenen Werte und einen nativen Slice auf einem Kobra-3-Profil; die
PLA-Temperatur erreicht dabei dessen `G9111`-Startmakro mit 220 °C. Der erste Testlauf
zeigte nicht unterstützte Profiloptionen, die danach auf die vom Android-Importer
unterstützten Schlüssel korrigiert wurden. `NativeSliceDeviceTest#orcaPrintaLotPlaLibraryLoadsIndependentlyAndSlices`
bestand auf Android 16/API 36 (1/1). APK/Release-Gate: Batch 10 wurde mit `v0.8.432-nightly.1` veröffentlicht.

### Punkt 9/10 — Größenabhängige Grundkörperauflösung

Orca `0ef9015904` führte beim Erzeugen bemaßbarer Grundkörper eine an den Radius
angepasste Winkelauflösung ein. Der Android-Dialog für Quader, Zylinder, Kegel, Kugel,
Torus und Scheibe war bereits vorhanden, der JNI-Meshgenerator verwendete jedoch feste
Winkel. Zylinder/Kegel/Scheiben/Kugeln/Tori nutzen nun eine 0,002-mm-Sehnenabweichung
mit 16 bis 360 Segmenten. Die 360er-Obergrenze ist die mobile Speicher-/Geometriegrenze;
der native Gerätetest bestätigt minimale Detailauflösung, höhere Auflösung für große
Formen und die Facettenobergrenze. `primitiveCircularMeshResolutionAdaptsToSizeWithinMobileFacetBound`
und der bestehende Ende-zu-Ende-Test `orcaPrimitiveModelsHaveExpectedBoundsAndProduceExtrusion`
bestanden auf Android 16/API 36. Noch kein APK/Release für diesen Teilbatch.

### Punkt 7/10 — STEP-Baugruppen standardmäßig in benannte Körper teilen

Portiert das Android-relevante Importverhalten aus Orca `fa5f7e3db4`. STEP/STP-Baugruppen
werden standardmäßig zu separat bearbeitbaren, benannten Modellteilen. Persistierte
Benutzerauswahl kann die Option weiterhin abschalten. `StepImportOptionsTest` 1/1,
`StepModelImportDeviceTest` 3/3 und `StepImportOptionsDialogDeviceTest` 2/2 bestanden auf
API 36.

### Punkt 8/10 — CGAL-Boolean ignoriert isolierte Mesh-Vertices

Portiert Orca `b3f8d0af15`: nicht referenzierte Mesh-Vertices dürfen die geschlossene
Volumenprüfung vor einer Boolean-Operation nicht scheitern lassen. Der Android-JNI-Test
`NativeSliceDeviceTest#cgalBooleanVolumeChecksIgnoreUnreferencedVertices` wurde im
3/3-Lauf auf API 36 gezielt bestanden. Die übrigen beiden Tests dieses Laufs gehören zu
älteren Batches und zählen nicht für Batch 10.

Hinweis zur Nummerierung: Die Punkte werden hier in Implementierungsreihenfolge benannt.
Die Ausführungsstatusdatei listet sie chronologisch; beide Dateien bezeichnen denselben
8/10-Stand. Die STEP-Körpernamen und das Aufteilen von Baugruppen sind getrennte
Nutzerverhalten aus demselben Upstream-Commit und müssen auch künftig getrennt getestet
bleiben.

### Punkt 6/10 — Variantenspezifische Filamentwerte bei Slotänderungen erhalten

Portiert Orca `45a29d391b8d`: `DynamicPrintConfig::set_num_filaments()` darf Optionen aus `filament_options_with_variant` nicht auf die Anzahl logischer Filamentslots verkürzen. Ein JNI-Regressionstest ließ einen Slot mit zwei Varianten durch den echten Resize-Aufruf laufen und bestätigte beide Variantennamen und `volumetric_speed_coefficients` danach unverändert.

API-36 Instrumentierung 1/1; ARM64-Nativbuild, Debug-APK-Build und SliceTest-Instrumentierungsbuild erfolgreich. Debug-/Slice-JVM-Suiten jeweils 743/743 ohne Fehler oder Skips. Der Punkt ist in `20fb536a` committed und in 0.8.430 enthalten; [Pre-Release v0.8.430-nightly.1](https://github.com/Exa201/OrkanSlicer-Nightly-2.5/releases/tag/v0.8.430-nightly.1). Vier weitere verifizierte Punkte stehen vor dem vollständigen Batch-10-Abschluss aus.

### Punkt 4/10 — Arrange nutzt die verfügbare Bettfläche für nahezu bettgroße Modelle

Android-Port des relevanten Orca-Fixes `664f734e22`: für nicht-sequenzielles Arrange wird die standardmäßige Bettinnenmarge nur so weit reduziert, wie es die größte ausgewählte Kontur samt Inflation erfordert. Konfigurierte Skirt-/Brim-Abstände bleiben erhalten; sequentieller Druck bleibt unverändert.

`NativeSliceDeviceTest#arrangementUsesAvailableBedAreaForNearBedSizeObjects` bestand auf API 36 (1/1): ein 197-mm-Objekt samt 1-mm-Brim passt auf eine 200-mm-Platte, bleibt innerhalb der physischen Platte und erhält die erforderliche 0,5-mm-Restmarge. Keine Behauptung über global optimales Nesting oder physische Drucker.

Zusammen mit STEP-Körpernamen, deterministischen Organic-Tree-Spitzen und tatsächlichen Startwerkzeug-Temperaturen sind **4/10** unabhängige Orca-Punkte verifiziert. Die Batch-APK und Nightly-Veröffentlichung bleiben bis 10/10 offen.

## Nightly Batch 9 — Punkt 9/10 verifiziert (2026-11-03)

### Mehrfarben-Segmentierung nach Filamentanzahl statt Farblistenlänge

Portiert Orca `21d2856429`: die Facettenzustände der gemalten Mehrmaterial-Segmentierung werden anhand der konfigurierten Filamentanzahl dimensioniert, nicht anhand der Farbliste. Importierte/angepasste Profile können eine kürzere oder unvollständige `filament_colour`-Liste besitzen, obwohl mehrere Filamente samt Durchmesser konfiguriert sind. In diesem Fall bleibt gemalte Segmentierung für alle Filamente verfügbar.

`NativeSliceDeviceTest#paintedSegmentationUsesFilamentCountWhenColorPaletteIsShorter` bestand auf API 36: der native Policy-Probe bestätigt für zwei Filamente und eine Farbe drei Facettenzustände; zusätzlich wurde ein Modell mit dem zweiten Filament bemalt, die Palette für den Slice auf eine Farbe gekürzt und der G-Code auf Werkzeug `T1` samt tatsächlicher Extrusion geprüft. Orkans JNI-Slice normalisiert die Farbpalette vor dem Engine-Aufruf; der Policy-Probe prüft deshalb separat die Orca-Kernregel, die der Ende-zu-Ende-Pfad maskieren würde. Keine physische Druckerprüfung.

Batch 9: **9/10**. Noch ein verifiziertes Android-taugliches Orca-Feature und die gebündelte Regression bis zum APK-/Nightly-Release-Gate.

## Nightly Batch 9 — Punkt 8/10 verifiziert (2026-11-03)

### Gekräuselte Extrusionen nur bei aktivem Bedarf schätzen

Portiert Orca `90ac58d3cd`: die aufwendige Abschätzung von Support-Kräuselungen läuft nur, wenn sowohl Überhanggeschwindigkeit als auch die Verlangsamung gekräuselter Wandungen aktiviert sind. Die Prüfung fasst Regionen gemeinsam auf, sodass die beiden Einstellungen auch in unterschiedlichen Materialregionen erkannt werden. Wird die Abschätzung übersprungen, werden alte `curled_lines` in wiederverwendeten Layern geleert, damit keine Ergebnisse einer vorherigen Konfiguration stehen bleiben.

`NativeSliceDeviceTest#curledExtrusionEstimationIsSkippedUnlessBothFeaturesAreEnabled` bestand auf API 36; es prüft beide Einzeloptionen, keine Option und beide Optionen in derselben oder in verschiedenen Regionen. Die bestehende produktive G-Code-Regression `bridgeInfillKeepsBridgeSpeedWithCurledPerimeterSlowdown` bestand ebenfalls. Debug-Unit- und Slice-Unit-Suiten bestanden; Ergebnisse aus den XML-Berichten werden im Fortsetzungsstatus festgehalten. Keine physische Druckerprüfung.

Batch 9: **8/10**. Noch zwei verifizierte Android-taugliche Orca-Punkte, danach gebündelte Regression und APK-/Nightly-Release-Gate.

# OrkanSlicer Nightly 2.5 — Portierungsplan

## Nightly Batch 9 — Punkt 6/10 verifiziert (2026-11-02)

### Benachbarte Sparse-Infill-Bahnen ohne Leerfahrt verbinden

Portiert Orcas `join_close_ends` aus `69193d5681fddf8ed3d63bf510bb307c21f5a0d` in den nativen `Fill::fill_surface_extrusion`-Pfad. Nur sortierbare Innenfüllung wird angepasst; Endpunkte werden kürzeste zuerst verbunden. Eine lokale Linienprüfung verwirft Verbindungen, die vorhandene Bahnen kreuzen würden; Union-Find verhindert geschlossene Schleifen. Bei nicht umkehrbaren Bahnen werden nur End-zu-Anfang-Verbindungen zugelassen. Wandungen, massive Füllung, unsortierte Muster und Arachne-Breitenpfade bleiben unberührt.

`NativeSliceDeviceTest#sparseInfillJoiningAvoidsLoopsAndCrossings` bestand 1/1 auf API 36 und prüft Verbindungsabstand, offene Pfadenden, eine dreiteilige Kette ohne Verlust der Originallinien, Kreuzungsvermeidung sowie feste Extrusionsrichtung. `multilineGridInfillUsesMiterJoinsAndPreservesGeometry` bestand 1/1 mit dem bestehenden 7.461-Bewegungen-Geometrie-Fingerprint. Eine anfängliche Versuchsmessung zählte Extrusionssegmente statt Leerfahrten und wurde verworfen: eine Verbindung fügt selbst ein kurzes Extrusionssegment hinzu, sodass diese Kennzahl den Optimierungsnutzen nicht misst. ARM64-Nativ-/Instrumentation-Paket erfolgreich gebaut. Keine physische Druckerprüfung.

Batch 9: **6/10**. Noch vier unabhängig verifizierte Punkte sowie gebündelte Regression und APK-/Nightly-Release-Gate.

## Nightly Batch 9 — Punkt 5/10 verifiziert (2026-11-02)

### Slicing-Status-Callbacks thread-safe austauschen

Portiert den gemeinsamen Orca-Core-Fix aus `19b8d2138e`: der geteilte Print-State-Zeitstempel ist atomar, und `PrintBase` schützt Status-Callback-Registrierung mit einem Mutex. Statusmeldungen rufen eine lokale Callback-Kopie erst nach Freigabe des Mutex auf, sodass eine laufende Slice-Statusmeldung keinen Datenwettlauf mit Callback-Austausch hat und ein Callback sich selbst ersetzen kann. Desktop-Plater-Lifecycle-Code wurde nicht übernommen; der Android-JNI-Slice-Aufruf bleibt synchron und seine lokale Listener-Lebensdauer ist unverändert.

`NativeSliceDeviceTest#printStatusCallbacksCanBeReplacedFromCallbackAndConcurrently` bestand auf API 36 (1/1). Der Test ersetzt den Callback innerhalb seiner Ausführung, prüft den Ersatzaufruf, stress-testet parallele Statusmeldungen/Registrierungen und verifiziert den abschließend installierten Callback. Keine physischen Druckstarts.

Batch 9: **5/10**. Keine APK/Veröffentlichung vor 10/10.

## Nightly Batch 9 — Punkt 4/10 verifiziert (2026-11-02)

### Spiralvase mit mehreren Höhenbereichen

Portiert Orca `896557bce5`: `PositiveLargestContour` und die reguläre Behandlung der unteren Schale werden jetzt auch dann auf die Modellkontur angewendet, wenn ein Objekt mehrere Layer-Range-Regionen hat. Zuvor galt die Vase-Slicing-Strategie nur bei genau einem Höhenbereich; ein Objekt mit Höhenbereich-Override fiel dadurch auf reguläres Konturslicing zurück.

`NativeSliceDeviceTest#spiralVaseAcceptsSingleFilamentHeightRangeOverrideAndProducesGcode` bestand auf API 36 (1/1). Der produktive Slice erzeugt Extrusion und Außenwand; oberhalb der konfigurierten unteren Schale entstehen keine inneren Wandbahnen, obwohl der Höhenbereich mehrere Regionen erzeugt. Kein physischer Drucktest.

Batch 9: **4/10**. Der Catan-Packungstest bleibt ein separater Anordnungsnachweis. Keine APK/Veröffentlichung vor 10/10.

## Nightly Batch 9 — Punkt 3/10 verifiziert (2026-11-02)

### Z-/Düsenbewusste Hindernisse bei Arrange

Portiert den Android-relevanten Orca-Teil für feste Werkzeugkopf-Kollisionsvolumen in die automatische Anordnung: gemeinsame, düsenversetzte und düsenindividuelle Profileinstellungen bleiben im Druckerprofil erhalten; optionale Z-Bereiche und die physische Düsenauflösung filtern Kollisionen vor der NFP-Erzeugung. Ein Hindernis bleibt bettfest, abschließendes Zentrieren wird bei festen Kollisionsvolumen deaktiviert. `bed_exclude_area` behält seine historische, bettfeste Bedeutung. Dies ist ausdrücklich nur die Anordnungsintegration, keine vollständige Portierung von Orcas Vorschau-, G-Code-Prüfungs- und Travel-Rerouting-Pipeline.

Verifikation: Parser-/Düsenraum- und echte Arrange-Regressionsinstrumentierung auf API 36 bestanden; Profil-Rundlauf und Drucker-Einstellungsrouten in Debug- und Slice-Unit-Tests bestanden. Die vorhandene `short_object_arrangement_regression` besteht ebenfalls. Mit dem mitgelieferten Catan-ZIP wurden alle 109 STLs frisch geladen und automatisch ausgerichtet: 60 Platten, sämtliche Objekte innerhalb der Bettgrenzen und auf der Druckfläche, 139 gleichplatten Mesh-Paare ohne Überschneidung. Der Catan-Lauf dauerte 4:10 auf dem API-36-Emulator. Ein zusätzlicher Regressionstest importiert alle 24 identischen Basisadapter: 10 Platten, 23 gleichplatten Mesh-Paarungen ohne Überschneidung. Das Packen ist korrekt, aber konservativ und nicht optimal dicht. Ein 2-mm-Importversuch senkte den Catan-Plattenbedarf nur auf 56 und erzeugte eine Mesh-Überschneidung; dieser Code wurde verworfen. Kein physischer Druckertest.

Batch 9: **3/10**. Der Catan-Fixturetest ist ein separater Anordnungsnachweis und zählt nicht als weiterer Orca-Funktionspunkt.

## Nightly Batch 9 — Punkt 2/10 verifiziert (2026-11-02)

### Unbekannte, aber belegte Spulenmaterialien bei ACE/CFS

Ein belegter Drucker-Slot mit RFID-Farbe, aber unbekanntem Material wird nicht länger fälschlich als `Generic PLA` dargestellt. Der physische Slot behält Farbe und Gate-Index, wird sichtbar als „Material unbekannt“ markiert und überschreibt bei automatischer oder expliziter Synchronisierung keinen bereits konfigurierten Projektwerkstoff. Ein leerer oder nicht parsebarer Slot bleibt weiterhin nicht auswählbar. Der bestehende Java-Konstruktor von `FilamentSlot(int, String)` bleibt erhalten.

Verifikation: `MmuFilamentSyncDeviceTest` 18/18 auf API 36; Debug-Unit und Slice-Unit zusammen 739/739 je Variante, ohne Fehler, Ausnahmen oder Skips; `git diff --check` sauber. Geprüft wurden beide Synchronisierungsarten, Farbparse `0RRGGBB`, Slotzuordnung, sichtbares Label, Unknown-Zustand und der erhaltene Projektwerkstoff. Kein physischer Druckertest.

### Creality CFS über Moonraker `box`

Die Filamentsynchronisierung fragt nun nach dem generischen `?mmu`-Status bei leerer MMU-Antwort die Creality-CFS-Daten über `?box` ab. T1–T4 werden auf 16 physische Gate-Indizes abgebildet; getrennte oder leere Trays verschieben dadurch keine ACE-Fächer. Generische und in `same_material` aufgelöste Material-IDs sowie Creality-Farbwerte bleiben erhalten. Eine belegte Spule mit unbekanntem Material und lesbarer Farbe wird als unbekannt gekennzeichnet statt einem falschen Material zugeordnet.

Verifikation: Parser-Gerätetests 3/3 und vollständige `MmuFilamentSyncDeviceTest` 19/19 auf API 36. Der End-to-End-Test simuliert `?mmu` mit `enabled=false`, danach `?box`, und prüft physische Gate-Indizes, unbekannte Spule, Auswahl und Snapshot. Debug-Unit und Slice-Unit bestanden jeweils 739/739, ohne Fehler, Ausnahmen oder Skips; `git diff --check` sauber. Keine physische Creality-Hardware getestet.

Batch 9: **2/10**. Noch acht unabhängig verifizierte Punkte bis zur gebündelten Regression und dem APK-/Nightly-Release-Gate. Der Catan-Anordnungsnachweis ist separat und zählt nicht als Orca-Funktionspunkt.

## Nightly Batch 8 — 10/10 verifiziert (2026-11-02)

### Punkt 10/10 — Raft-Schichten pro Objekt

Der Modell-Einstellungsdialog kann `raft_layers` nun objektbezogen setzen (0–20) oder vom Profil erben. Die Einstellung wird erst nach Bestätigung angewendet, im Objekt-Override gespeichert und beim nativen Slice berücksichtigt.

`ModelObjectProcessOverridesComposeDeviceTest#objectRaftLayerCountValidatesAndAppliesOnlyAfterConfirmation` bestand 1/1. `NativeSliceDeviceTest#perObjectRaftLayerOverridePersistsAndProducesRaftGcode` bestand 1/1 auf API 36: 3MF-Rundlauf, Objektisolation sowie Änderung der tatsächlichen Support-/Raft-Ausgabepfade geprüft. Der Dialoghinweis für ungültige Werte wurde nach oben verschoben, damit er bei größeren Dialogen sichtbar bleibt; danach bestand die gesamte Compose-Testklasse 10/10. Zusätzlich bestanden Debug- und Slice-Unit-Suiten mit je 738/738.

Batch 8 ist **10/10**. Catan-Anordnungsfixture (109 Modelle, 60 Platten, 139 kollisionsfreie gleichplatten-Paare) bleibt ein separater Import-/Packing-Nachweis. Debug-APK und Nightly-Prerelease sind im Release-Gate-Eintrag dokumentiert.

## Release-Gate-Stand — Nightly 0.8.427 (Code 462)

- Debug-APK `OrkanSlicerNightly25_bb2f1e8e9a.apk`, 240,156,107 Bytes, SHA-256 `D4FF0755168DF5FB43CA4B0A259072062EB4EF74C8F3B00EF32C099F063FEF6E`; Paket `com.exa201.orkanslicer.nightly25`, Version 0.8.427 / Code 462.
- Commit `bb2f1e8e9a09d75e98ef08979d586d85530d8161` wurde auf `nightly25/nightly-2.5` gepusht. Debug-APK erfolgreich auf dem API-36-Emulator installiert und gestartet; Prozess blieb aktiv. Im geprüften Logcat-Ausschnitt keine Fatal Exception, kein natives Fatal Signal, ANR oder OOM.
- Veröffentlicht als GitHub-Prerelease [v0.8.427-nightly.1](https://github.com/Exa201/OrkanSlicer-Nightly-2.5/releases/tag/v0.8.427-nightly.1), APK-Asset `OrkanSlicerNightly25_bb2f1e8e9a.apk`.
- Batch 8: 10/10. Catan-Anordnungsprüfung separat: 109 Modelle auf 60 Platten innerhalb der Bettgrenzen; 139 gleichplatten Mesh-Paare ohne Überschneidung; Instrumentierung 1/1. Der 1.000-Facetten-Projektionsschutz bleibt bestehen, weil höhere Grenzwerte reproduzierbare Clipper-Crashes verursacht haben. Kein physischer Druckertest.

## Nightly Batch 8 — Punkt 9/10 verifiziert (2026-11-02)

### Nahtposition pro Objekt

Der Modell-Einstellungsdialog stellt jetzt Orcas Objektoption `seam_position` bereit. Die Optionen und Bezeichnungen werden aus dem geladenen Orca-Schema gelesen; „Profilvorgabe“ entfernt einen vorhandenen Objekt-Override. Die Option bleibt auf Objektebene und erscheint nicht im Teile-Dialog.

Verifikation auf API 36: `ModelObjectProcessOverridesComposeDeviceTest#objectSeamPositionUsesOrcaChoicesAndAppliesOnlyAfterConfirmation` sowie `NativeSliceDeviceTest#perObjectSeamPositionOverridePersistsAndChangesNativeSlice` bestanden (je 1/1). Der native Lauf weist nach, dass nur das ausgewählte Objekt `back` erhält, der Geschwisterwert aus dem Profil stammt, beide Werte den 3MF-Rundlauf überleben und die echten extrudierenden Wandpfade im G-Code wechseln. ARM64 Native-/Instrumentierungs-APK-Kompilierung erfolgreich. Ein erster Fixturelauf verwendete den alten Orca-Wert `rear`; die Enumprüfung lehnte ihn korrekt ab. Nach Umstellung auf den aktuellen Orca-Schemawert `back` bestand der Test.

Batch 8: **9/10**. Noch ein eigenständiger Produktpunkt und gebündelte Regression bis zum APK-/Nightly-Release-Gate.

## Nightly Batch 8 — Punkt 8/10 verifiziert (2026-11-02)

### Bauteilbezogene obere und untere Oberflächenmuster

Ergänzt die fehlenden Orca-`top_surface_pattern`- und `bottom_surface_pattern`-Overrides in den bestehenden Modell-/Bauteil-Einstellungsdialogen. Die Auswahllisten stammen aus dem nativen Profil-Schema und unterstützen Profilvererbung; die Werte werden erst mit „Übernehmen“ angewendet. Die native Schnittkonfiguration erhält die Overrides pro Bauteil.

Verifikation auf API 36: `ModelObjectProcessOverridesComposeDeviceTest` **8/8**; `NativeSliceDeviceTest#perPartProcessOverridesRemainIsolatedAcrossThreeMfAndNativeSlice` **1/1**. Der Native-Test prüft unabhängige Werte für zwei Bauteile, 3MF-Export und erneuten Import, Vererbung beim jeweils anderen Bauteil sowie tatsächliche obere und untere Extrusionspfade im erzeugten G-Code. `:app:assembleSliceTestAndroidTest` und `git diff --check` erfolgreich. Dies ist Emulator-/Instrumentierungsverifikation, kein physischer Druckertest.

Batch 8: **8/10**. Kein APK-/Nightly-Release vor 10/10 und gebündelter Regression. Nächster Schritt: einen weiteren eigenständigen, Android-erreichbaren Orca-Funktionspunkt auswählen und mit Verhaltenstest nachweisen.

## Nightly Batch 8 — Punkt 7/10 verifiziert (2026-11-02)

7. Objektbezogener Support-Typ ergänzt. Das Modellmenü bietet die im geladenen Orca-Schema definierten Supportstrategien samt Profilvererbung an; die Auswahl wird bis zur Bestätigung lokal gehalten. `Model` erlaubt `support_type` nur als Objekt-Override, nicht als Teil-Override.

`ModelObjectProcessOverridesComposeDeviceTest` bestand 7/7 auf API 36. `NativeSliceDeviceTest#supportOverrideIsIsolatedPerObjectAndChangesThreeMfSlice` bestand 1/1: `enable_support` bleibt für das ausgewählte Objekt isoliert und überlebt den 3MF-Rundlauf; echte Supportbahnen für Normal- und Tree-Strategie unterscheiden sich. Für die Strategie-G-Code-Prüfung wurde globaler Support aktiviert, damit beide Strategien tatsächlich Werkzeugwege erzeugen. Null-Längen-E-/Prime-Marker werden nicht als Supportgeometrie gezählt. ARM64-Native-Testbuild und `git diff --check` erfolgreich. Batch 8: **7/10**; kein APK/Release vor 10/10. Nächster Schritt: einen weiteren app-erreichbaren Orca-Punkt mit eigenständigem Verhaltensnachweis auswählen.

## Nightly Batch 8 — Punkt 6/10 verifiziert (2026-11-02)

Objektoptionen aus nativen Orca-3MF-Projekten werden nun auch durch den klassischen 3MF-Importer übernommen. Dieser ließ zuvor nur eine feste Liste generischer Metadaten durch und verwarf gültige `PrintObjectConfig`-Optionen. Der Modell-/Werkzeugdialog unterstützt zusätzlich die Support-Erzeugung pro Objekt mit den Zuständen Profil übernehmen, ein und aus; `enable_support` bleibt auf Objektebene und kann nicht versehentlich als Einzelteil-Option gespeichert werden.

Verifikation: `NativeSliceDeviceTest#supportOverrideIsIsolatedPerObjectAndChangesThreeMfSlice` bestand 1/1 auf API 36, einschließlich exportierter `model_settings.config`, 3MF-Rundlauf und G-Code-Nachweis: ein Objekt erzeugt Support, das Geschwisterobjekt übernimmt das deaktivierte Profil, nach Aktivierung beider Objekte erzeugen beide Support. `ModelObjectProcessOverridesComposeDeviceTest` bestand 6/6; der bestehende native Einzelteil-Override-Test bestand 1/1. ARM64-Native-Build und `git diff --check` erfolgreich. Batch 8 steht bei 6/10; kein APK/Release vor 10/10.

## Nightly Batch 8 — 5/10 verifiziert (2026-11-02)

### Punkt 5/10 — Einzelteil-Füllmuster aus Orca-Profilen überschreiben

Die vorhandene Objekt-/Bauteil-Einstellung deckt jetzt zusätzlich Orcas `sparse_infill_pattern` ab. Das Auswahlmenü liest Werte und Anzeigenamen aus der nativen Orca-Konfigurationsdefinition, lässt Profilvererbung zu und schreibt den Override über denselben 3MF-/Undo-fähigen Modellpfad wie die übrigen Einstellungen. Damit kann ein Bauteil innerhalb eines gruppierten Mehrteiler-Objekts ein eigenes Füllmuster erhalten.

API-36: `NativeSliceDeviceTest#perPartProcessOverridesRemainIsolatedAcrossThreeMfAndNativeSlice` bestanden (1/1); Gyroid-Override bleibt nach 3MF-Export/Reload nur auf dem ausgewählten Teil und ändert dessen tatsächliche extrudierende G-Code-Bewegungen gegenüber dem Profilmuster. `ModelObjectProcessOverridesComposeDeviceTest` bestanden (5/5), inklusive Musterwahl, Speichern, ungültiger Eingaben und Abbruch. Kein physischer Drucktest; kein APK/Release bis 10/10.

Batch 8: **5/10**. Noch fünf unabhängig verifizierte Punkte bis zum APK-/Nightly-Release-Gate.

## Nightly Batch 8 — 4/10 verifiziert (2026-11-02)

### Punkt 4/10 — Per-Objekt-Düsen-Layergrenzen und Prime-Tower-Kompatibilität

Der Orca-Port `fcd96ad91c` war im Android-Kern vorhanden, aber der Laufzeitnachweis fehlte. Die Instrumentierungsprüfung ruft jetzt zusätzlich direkt `SlicingParameters::create_from_config` mit drei verschieden begrenzten Düsen auf und bestätigt für Filament/Düse 1 die Grenzen 0,04–0,48 mm sowie für Filament/Düse 3 0,12–0,24 mm. Anschließend werden zwei Würfel unterschiedlichen Werkzeugen zugewiesen und mit aktivem Prime Tower tatsächlich gesliced; beide Werkzeugwechsel und abgeschlossene Layer-G-Code-Ausgabe werden geprüft.

`NativeSliceDeviceTest#objectsWithDifferentNozzleLayerLimitsCanSharePrimeTower` bestand auf API 36 (1/1). Native ARM64-Build, SliceTest- und Instrumentierungsbuild sowie tatsächlicher G-Code-Slice erfolgreich. Kein physischer Drucktest; kein APK/Release bis 10/10.

Batch 8: **4/10**. Noch sechs unabhängig verifizierte Produkt-/Portierungspunkte bis zum APK-/Nightly-Release-Gate.

### Punkt 3/10 — Orca-Eckenglättung auf Hilbert- und Octagram-Sparse-Infill

Portiert Orcas gleichmäßig abgewinkelte, auf die G-Code-Auflösung abgeflachte Corner-Smoothing-Routine (`9b37da699b`). Die vorhandene Einstellung `sparse_infill_smooth_factor` wirkt jetzt zusätzlich zu Hilbert Curve auch auf Octagram Spiral. Glättung erfolgt vor dem Clipping; andere Muster bleiben unverändert. Die bestehende Einstellung und Profilserialisierung werden weiterverwendet.

`NativeSliceDeviceTest#supportedSparseInfillSmoothingChangesOnlyHilbertAndOctagramToolpaths` bestand auf API 36 (1/1): echte 0-%-/100-%-Slices zeigen geänderte Sparse-Infill-Züge und zusätzliche Kurvensegmente für beide Muster; Außen-/Innenwände und obere/untere Flächen bleiben unverändert. Grid dient als Negativkontrolle und bleibt unverändert. `:app:testDebugUnitTest` und `:app:testSliceTestUnitTest`: je 738/738, keine Fehler/Skips. ARM64-Native-/SliceTest-/Instrumentierungsbuilds erfolgreich; Profilrouten-Audit: 497/497 sichtbare Settings zu nativer Definition und Profilparser aufgelöst; `git diff --check` sauber. Kein Distributionsbuild/Release.

Batch 8: **3/10** zu diesem Zwischenstand. Noch sieben unabhängig verifizierte Orca-Funktionspunkte bis zum APK-/Nightly-Release-Gate.

## Nightly Batch 8 — 2/10 verifiziert (2026-11-02)

### Punkt 2/10 — Maßhaltige Orca-Grundkörper direkt auf der Platte erzeugen

Der Modell-Menüpunkt „Grundkörper hinzufügen“ adaptiert Orcas Primitive-Dialogfunktion (`0ef9015904`) als Android-Compose-Dialog für Quader, Zylinder, Kegel, Kugel, Torus, Scheibe und flache Platte. Die Eingabefelder richten sich nach der Form; Maße werden vor JNI-Aufruf begrenzt, der Torus verhindert eine selbstüberschneidende Rohrgeometrie. Der Native-Kern erzeugt die druckbare Mesh-Geometrie und fügt sie als separates Modellobjekt in der Mitte des konfigurierten Betts hinzu (auch bei positiv beginnenden Druckbettkoordinaten). Nach dem Erstellen wird das Objekt ausgewählt, damit es direkt weiterbearbeitet oder verschoben werden kann.

`PrimitiveModelDialogDeviceTest`: 2/2 auf API 36 (Form-/Maßrückgabe und Torus-Selbstüberschneidungsvalidierung). `NativeSliceDeviceTest#orcaPrimitiveModelsHaveExpectedBoundsAndProduceExtrusion`: 1/1 auf API 36; alle sieben Formen geprüft, je einschließlich Dreiecks-/Bounding-Box-Prüfung, 3MF-Export und -Reload sowie echtem G-Code-Slice mit Extrusion. `:app:assembleSliceTest :app:assembleSliceTestAndroidTest` erfolgreich. Kein Hardwaredruck und keine Distribution-APK.

Batch 8: **2/10** zu diesem Zwischenstand. Noch acht getrennte, app-relevante Orca-Funktionspunkte und ihre fokussierten Verifikationen bis zum APK-/Nightly-Release-Gate.

## Nightly Batch 8 — 1/10 verifiziert (2026-11-02)

### Punkt 1/10 — Druckbettrand an Elephant-Foot-Kompensation anpassen

Die Orca-Nativeinstellung `brim_use_efc_outline` war im mobilen Prozesskatalog nicht erreichbar, obwohl der Android-Native-Kern die Funktion bereits unterstützt. Sie ist jetzt in „Skirt and brim → Brim“ im Prozessprofil-Editor sichtbar und bleibt eine Experteneinstellung. Der Native-Kern nutzt die kompensierte Erstschichtkontur nur, wenn die Option aktiv ist, Elephant-Foot-Kompensation und deren Layerzahl größer null sind und kein Raft verwendet wird.

`OrcaProcessSettingsCatalogTest#compensatedOutlineBrimControlIsExposedInBrimSection`: Teil der Klasse mit 11/11 erfolgreichen Tests. `scripts/check_profile_setting_routes.py`: 497 sichtbare Settings auf native Definitionen und passende Profilparser aufgelöst, 107 Gruppennamen eindeutig. `NativeSliceDeviceTest#brimCanFollowElephantFootCompensatedFirstLayerOutline` bestand auf Android API 36 (1/1): derselbe 20-mm-Würfel wurde mit 0,6-mm Elephant-Foot-Kompensation jeweils mit deaktivierter und aktivierter Einstellung gesliced; beide G-Codes enthalten Brim-Extrusion und die XY-Brim-Pfade unterscheiden sich. SliceTest- und Instrumentierungs-APK wurden gebaut und auf dem Emulator installiert. Kein physischer Drucktest.

Batch 8 steht bei **1/10**. Keine Distribution-APK oder Nightly-Veröffentlichung bis zehn neue Produktänderungen unabhängig verifiziert sind.

## Nightly Batch 7 — 10/10 (2026-11-02)

Der Batch hat das 10/10-Gate erreicht. `:app:testDebugUnitTest` und `:app:testSliceTestUnitTest` bestanden mit je 737/737 Tests. Vier gezielte API-36-Instrumentierungstests (Changelog/Back, Profilfiltersuche/Back+Escape, Z-Schrumpfung und Spiralvase) bestanden 4/4. Der 553-Test-Vollauf wurde nach 8 Tests kontrolliert abgebrochen: der alte Changelog-Text zeigte 0.8.420 statt der Build-Version; Text und Back-Behandlung wurden behoben und der Test danach gezielt bestanden. Nightly 0.8.426 wurde gebaut und auf dem AVD gestartet. Prerelease: [v0.8.426-nightly.1](https://github.com/Exa201/OrkanSlicer-Nightly-2.5/releases/tag/v0.8.426-nightly.1). Der Vollauf und ein physischer Xiaomi-Test bleiben offen.

### Punkt 8 — Android-Back/Escape schließt die Profilfiltersuche

- Orca `06bccd5570` schließt Suchoberflächen beim Escape-Tastendruck. In OrkanSlicer schließen Android-Zurück und Escape die geöffnete Profilfiltersuche, ohne den Profil-Host oder die Einstellungen zu verlassen.
- `ProfileFilterAccessibilityDeviceTest#backClosesProfileSearchWithoutClosingItsHost` wurde auf API 36 (`emulator-5554`) ausgeführt: 1/1 bestanden. Der Test öffnet die Suche, prüft Android-Zurück, öffnet sie erneut und prüft Escape.
- Der Back/Escape-Punkt ist als Punkt 8 verifiziert. Frühere Zwischenstände zählten 7/10; die hierunter dokumentierten Punkte 9 und 10 vervollständigen den Batch.
- Der frühere Fixture-Vorbehalt zu Vase/Höhenbereich wurde durch einen gezielten nativen Fixture-Test abgelöst; siehe Punkt 10. Der Test prüft die Höhenbereichskonfiguration und erzeugten G-Code im selben Slice.

### Punkt 9/10 — Z-Schrumpfung skaliert Höhenbereiche konsistent

- Portiert Orca `5fd0edf5cc`: `LayerRanges` berücksichtigt den Z-Schrumpfungsfaktor; nach Änderung der skalierten Grenzen werden abhängige PrintObjectRegions neu aufgebaut. Beim Clipping wird die unkompensierte Objekthöhe verwendet.
- `NativeSliceDeviceTest#zShrinkageCompensationKeepsHeightRangeAlignedAfterPrintConfigChanges` bestand auf API 36 (1/1): dieselbe native Print-Instanz wird mit 100 % und danach 95 % Z-Schrumpfung verarbeitet; der konfigurierte dünne/dichte Höhenbereich bleibt mit der skalierten Geometrie ausgerichtet.
- Der erste Testlauf scheiterte an einer vom Orca-Fixture abweichenden Testkonfiguration; nach Anpassung an Orcas tatsächliche Schichthöhen- und Kontureinstellungen bestand der Test. Kein Hardwaredrucktest.

### Punkt 10/10 — Spiralvase akzeptiert gleichfarbige Höhenbereich-Overrides

- Orca `2a8abac43d`: Materialprüfung im Spiralvasenmodus darf einen Geschwindigkeits-/Höhenbereich-Override derselben Filamentzuordnung nicht fälschlich als Mehrmaterialkonflikt einstufen. Die Android-Produktlogik entsprach dem bereits; neu ist der explizite native Regressionstest.
- `NativeSliceDeviceTest#spiralVaseAcceptsSingleFilamentHeightRangeOverrideAndProducesGcode` bestand auf API 36 (1/1) und erzeugte realen G-Code für Spiralvase mit einem Filament und aktivem Höhenbereich-Override.
- Voriger Fixture-Vorbehalt ist damit ersetzt. Kein physischer Drucktest.

### Zusatzprüfung — Bridge-Speed bei Curling-Verlangsamung (bereits Batch 6, nicht erneut gezählt)

- Orca `533d4c3e9e` war bereits Punkt 8/10 in Batch 6; seine Android-Portierung und der erste echte G-Code-Test sind im Batch-6-Protokoll unten dokumentiert.
- `NativeSliceDeviceTest#bridgeInfillKeepsBridgeSpeedWithCurledPerimeterSlowdown` wurde erneut auf API 36 / `emulator-5554` ausgeführt: 1/1 bestanden; die inneren Bridge-Infill-Bahnen bei Z=5,2 mm verwenden weiterhin 45 mm/s.
- Diese Wiederholungsprüfung ändert den Batch-7-Zähler nicht. Batch 7 bleibt bei **10/10**; die gezielte Release-Verifikation steht noch aus.

### Punkt 7 — Ausgewählte Objektgruppe ins Druckvolumen einpassen

- Orca `b3819e7c9e` dokumentiert die Verfügbarkeitsregel für „Scale to build volume“: nur bei unterstütztem rechteckigem oder rundem Bett, nicht für eine Volumenauswahl. OrkanSlicer hat keine separate Volumenauswahl; die Aktion ist für ein oder mehrere ausgewählte Modellobjekte verfügbar.
- Die mobile Implementierung berechnet die gemeinsame Begrenzungsbox, skaliert alle ausgewählten Objekte mit demselben Faktor und erhält relative Abstände. Die Gruppe wird auf den Bettmittelpunkt ausgerichtet und auf Z=Minimum gesetzt; pro Objekt werden GL-Mesh und Vorschau erneuert. Die vorhandenen Modellhistoriepfade erhalten Undo/Redo.
- `InitialProjectMenuDeviceTest#scaleToBuildVolumeFitsSelectedGroupAndPreservesRelativeLayout` bestand auf API 36 / `emulator-5554` (1/1): zwei getrennte, absichtlich zu große Objekte werden gemeinsam eingepasst; relative Distanz und Objektzahl bleiben erhalten, Undo/Redo funktionieren. `:app:connectedSliceTestAndroidTest` war erfolgreich.
- Kein physischer Drucktest. Batch 7 steht bei **10/10**; APK-/Release-Gate folgt nach gezielter Instrumentierungsprüfung.

### Punkt 6 — Scarf-Rampenendpunkt stabilisieren

- Orca `6c96e41162`: auf fast geraden Segmenten wird ein knapp hinter der exakten Rampenlänge liegender Knotenpunkt übersprungen. Dadurch bleibt der Seam-Endpunkt über Layer konsistent und ein kurzer Planer-Stummel entfällt; echte Ecken dürfen den Endpunkt auf den Eckknoten setzen.
- Android-Port in `ExtrusionEntity.cpp`. `scarfRampRemainsStableAcrossLayerVertexShiftsAndCorners` prüft den Produktionspfad auf API 36 mit zwei verschobenen Layerknoten und einer Ecke: 1/1 bestanden. ARM64 Native- und Instrumentierungsbuild erfolgreich.
- Kein echter Druck-/Firmwaretest. Batch 7 steht bei **6/10**; APK-/Release-Gate folgt bei 10/10.

### Punkt 5 — Filament-für-Funktion überschreibt keine anderen Feature-Zuordnungen

- Orca `304924a69a` trennt die Filamentwahl für Sparse Infill, interne massive Füllung sowie obere/untere Oberfläche. Ein Feature mit Wert `Default` verwendet weiter das Objektfilament; eine andere explizite Feature-Auswahl darf es nicht verändern.
- Diese Upstream-Semantik ist im aktuellen Android-Port bereits vorhanden. `NativeSliceDeviceTest#oneFeatureFilamentOverrideDoesNotCascadeToOtherInfillOrSurfaceFeatures` wurde frisch auf API 36 ausgeführt und bestand 1/1. Der Test erstellt vier echte G-Code-Slices und kontrolliert die erzeugten Config-Werte und Feature-Werkzeugnummern.
- Keine Produktionsänderung erforderlich. Batch 7 steht bei **5/10**; APK-/Release-Gate erst bei 10/10.

## Nightly Batch 6 — 10/10 (2026-11-01)

- Orca-Upstream `27cafaa06c` korrigiert Düsengrößen, die unter einer UI-Locale mit Dezimalkomma ankommen. Die Android-Profilfilterung hatte denselben Fehler: `0,6` wurde vor dem Zahlenvergleich am Komma getrennt und als `0` interpretiert.
- Android liest jetzt ein einzelnes Dezimalkomma als Dezimalpunkt, erhält aber die Auswahl des ersten Werts aus Punkt-/Komma-getrennten Düsengrößenlisten und unterstützt Semikolonlisten mit lokal formatiertem Einzelwert. Profilnamen, Layerhöhen- und Linienbreitenprüfungen normalisieren ebenfalls Dezimalkomma.
- `Slic3rUtilsNozzleLocaleTest`: 4/4 bestanden (Einzeldüse, Mehrdüsenlisten, Profil-Kompatibilität, Layerhöhe/Linienbreite). `:app:testDebugUnitTest --tests ru.ytkab0bp.slicebeam.slic3r.Slic3rUtilsNozzleLocaleTest` erfolgreich; `git diff --check` erfolgreich. Das ist ein gezielter Android-Profilpfad-Test, kein vollständiger Build oder Geräteslice.
- Batch 6 Punkt 1/10: Düsengrößen mit Dezimalkomma, siehe Nachweis oben.

### Punkt 2/10 — Experimentelle hochpräzise G-Code-Ausgabe

- Portiert Orca `887a8f1806`: optionale Prozessprofileinstellung `high_precision_gcode` (standardmäßig aus, Expertenmodus). Bei Aktivierung werden XYZ/IJ mit vier statt drei und E mit sechs statt fünf Dezimalstellen geschrieben. Relative E-Rundungsreste werden in Folgebewegungen übernommen. Supports behalten Standardpräzision; Support-Ironing bleibt präzise. FanMover, PressureEqualizer, Spiral Vase und G-Code-Vorschau lesen bzw. bewahren die gewählte Präzision.
- Einstellung ist in der mobilen Prozessprofilgruppe „Precision“ sichtbar und wird in Profilen gespeichert; die Änderung invalidiert die betroffenen Slicing-Schritte.
- `NativeSliceDeviceTest#experimentalHighPrecisionSettingChangesRealSliceCoordinatesAndExtrusionDigits`: 1/1 auf beiden verfügbaren API-16-Emulatoren bestanden. Der echte ARM64-Slice bestätigt die serialisierte Option und die tatsächlichen Ausgabeformate (XYZ 3→4, relatives E 5→6 Stellen). `scripts/check_profile_setting_routes.py` bestanden: 496 sichtbare Einstellungen mit nativer Definition und Parser verknüpft. ARM64-Native- und Instrumentierungsbuild erfolgreich; `git diff --check` sauber.
- Physischer Drucker-/Firmwaretest steht aus. Die Option bleibt experimentell und standardmäßig deaktiviert; eine erhöhte G-Code-Dateigröße ist zu erwarten.

### Punkt 3/10 — Transparente Filamente in der G-Code-Vorschau

- Android-Port von Orca `7319a2c54c26a7683e6d14ae7694753936f9a27f`: Die G-Code-Werkzeugpfadvorschau besitzt jetzt die gespeicherte Einstellung `preview_translucent_filaments` (standardmäßig an). Sie übernimmt den Alpha-Kanal der Slice-Farbpalette im GLES-Viewer und aktualisiert die Darstellung direkt beim Umschalten. Native Slice-Palette und G-Code bleiben unverändert. Der Port umfasst nur die mobile G-Code-Werkzeugpfadvorschau; Orkas separates Desktop-Modell-Viewport-/Glanzverhalten ist nicht enthalten.
- `SliceFilamentColorResolverTest`: 10/10 bestanden, darunter unveränderte native RGB-Palette, Alpha-Erhalt, RGB-Extraktion im opaken Modus und ungültiger Alpha-Fallback. Die API-36-Gerätetests `GCodePauseLayerPreviewDeviceTest#translucentToolPaletteRendersThroughGlesWithoutErrors` und `SettingsMenuActionDeviceTest#translucentFilamentPreviewCanBeToggledAndPersists` bestanden auf beiden verfügbaren Emulatoren (4 Ausführungen, 0 Fehler/Skips). Kein physischer Gerätetest behauptet.
- Ein erster Test im opaken Modus entdeckte eine fehlerhafte RGBA-zu-RGB-Extraktion. Diese wurde korrigiert; anschließend bestand die gezielte Testsuite. Noch keine APK oder Release für Batch 6.

### Punkt 4/10 — G-Code-Platzhalter nach Filament- und Druckkopfindex auflösen

- Android-Port von Orca `e52c735d1a`: unindizierte Vektor-Platzhalter verwenden den Filamentindex für Filament-Overrides, aber den nullbasierten physischen Druckkopfindex für andere Vektoren. `filament_map` selbst ist 1-basiert; ungültige/fehlende Zuordnungen fallen auf den aktuellen Filamentindex zurück. Float-Or-Percent-Vektoren verwenden dieselbe Auswahl und lösen Prozent-Abhängigkeiten über denselben Vektorindex auf.
- Native Probe `placeholder_filament_map_regression` wird über `NativeSliceDeviceTest#placeholderVectorsUseFilamentOrMappedExtruderIndexAsAppropriate` ausgeführt. Der Test bestätigt mit Filamenten 0/1 auf gemeinsamem Druckkopf 0: individuelle Rückzugswerte `0.4`/`3`, Düsenwert `0.4` für beide, Float-Or-Percent-Druckkopfdaten `0.2` und den Fallbackwert `0.6` bei ungültiger Map.
- API-36-Instrumentierung: 1/1 auf beiden verfügbaren Emulatoren bestanden. ARM64-Native- und Slice-Instrumentierungsbuild erfolgreich. Zwei Zwischenläufe deckten zunächst einen falschen Test-Fixture-Namen und danach eine falsche Erwartung für Druckkopf 0 auf; Fixture/Erwartung wurden korrigiert und der finale Lauf bestand. Es wurde kein tatsächlicher Druck gestartet.

### Punkt 5/10 — Prime-Tower-Normalisierung bei wiederverwendetem Print verifiziert

- Orca-Upstream `4c7928d8aa` korrigiert, dass `Print::apply()` den Prime-Tower-Zustand zunächst anhand des vorherigen Modells normalisieren konnte. Der Android-Port war bereits im Quellcode vorhanden; der fehlende Nachweis für ein wiederverwendetes `Print` wurde jetzt ergänzt.
- Der Test wendet dieselbe native `Print`-Instanz nacheinander auf ein Ein- und ein Zweifilamentmodell an und ruft nach jeder Anwendung `Print::process()` auf. Er prüft, dass beim ersten Modell kein Tower erzeugt wird und der Tower für das zweite Modell mit zwei Werkzeugen wieder aktiviert und tatsächlich generiert wird.
- `:app:assembleSliceTest :app:assembleSliceTestAndroidTest` erfolgreich. `NativeSliceDeviceTest#reusedPrintReenablesPrimeTowerWhenNextModelUsesAnotherFilament` bestand direkt per ADB auf beiden API-36-Emulatoren (je 1/1). Das belegt den nativen Print-/Slice-Zustand, aber keinen G-Code-Export oder physischen Druck.

### Punkt 6/10 — Adaptive-PA-Messungen nur für geänderte Varianten validieren

- Android-Anpassung von Orca `2cb2f0d2e7`: `adaptive_pressure_advance_model` prüft beim Bearbeiten nur Varianten, deren Text tatsächlich geändert wurde. Unveränderte `0,0,0`-Platzhalter für andere Filamente blockieren das Speichern nicht; Leerzeichen um Kommawerte und mehrzeilige Messreihen sind zulässig. Fehler werden mit Filament- und Zeilennummer gezeigt und sperren Speichern bis zur Korrektur.
- `AdaptivePAModelValidatorTest`: 4/4 bestanden (Whitespace/mehrzeilige Reihen, geänderte Variante, unveränderter Platzhalter und Grenz-/Formatfehler). `AdaptivePAModelEditorDeviceTest`: 2/2 auf `solidcraft_port_test_clean_20261101` / API 36; echter Dialogtest bestätigt zeilenbezogene Fehlermeldung plus deaktiviertes Speichern und danach erfolgreiches Speichern des Whitespace-Modells unter Erhalt des unveränderten Platzhalters.
- `:app:assembleSliceTestAndroidTest` erfolgreich. Der parallele Lauf auf `solidcraft_touch_isolated_api36` scheiterte in beiden Tests vor Semantikzugriff mit „No compose hierarchies found“; dessen Systemlog zeigt mehrsekündige Graphics-Composer-/Surface-Sync-Verzögerungen. Die gezielte Wiederholung auf dem sauberen Port-AVD bestand 2/2. Kein physischer Drucker-, Xiaomi- oder echter Drucktest.

### Punkt 7/10 — Boolesche Mesh-Konvertierung entfernt isolierte CGAL-Vertices

- Portiert Orca `b3f8d0af15`: die CGAL-Surface-Mesh-Konvertierung entfernt unbenutzte Vertices und kompaktifiziert die Indizes, bevor CGAL Volumenprädikate auswertet. Das verhindert den Upstream-Crash bei geschlossenen Meshes mit zusätzlichen isolierten Vertices.
- `NativeSliceDeviceTest#cgalBooleanVolumeChecksIgnoreUnreferencedVertices` verwendet den produktiven Konvertierungspfad mit drei isolierten Vertices (vor, zwischen und nach benutzten Indizes). Der API-36-Test bestätigte geschlossene Volumenhülle, keine Selbstüberschneidung, Volumen 1000, gültige kompaktierte Indizes und alle 12 Dreiecke.
- `:app:assembleSliceTest :app:assembleSliceTestAndroidTest` erfolgreich; gezielte API-36-Instrumentierung 1/1 erfolgreich. Das ist ein Native-/CGAL-Regressionsnachweis, kein Boolean-UI-Test, vollständiger Slice-Suite-Lauf oder physischer Druckertest.

### Punkt 8/10 — Bridge-Speed bei Curling-Verlangsamung

- Portiert Orca `533d4c3e9e`: bei `slowdown_for_curled_perimeters` behält nicht unterstütztes Bridge Infill die konfigurierte `bridge_speed`, statt den Wert für vollständig überhängende Perimeter zu übernehmen.
- `NativeSliceDeviceTest#bridgeInfillKeepsBridgeSpeedWithCurledPerimeterSlowdown` erzeugt einen echten nativen Slice und kontrolliert die G-Code-Geschwindigkeit der Bridge-Infill-Linien. Der API-36-Emulatorlauf bestand 1/1 (0 Fehler/Skips; 10,943 s Testlaufzeit).
- Batch 6: **8/10**. Noch keine APK oder Release; vor dem 10/10-Gate folgen zwei neue Android-relevante Orca-Punkte mit reproduzierbarem Slice- oder UI-Effekt.

### Punkt 9/10 — 3D-Fuel-PCTG-ESD-Profil mit Herstellername

- Orca `bb4c092ae4` führt den 3D-Fuel-Herstellernamen im ESD-PCTG-Profilnamen und verwendet die aktualisierte Profil-ID. Das Android-Profilasset zeigte bisher `3D-Fuel Pro PCTG-ESD` mit abweichender Schreibweise; es heißt jetzt wie im Orca-Profilkatalog `3D-Fuel Pro PCTG ESD`.
- `NativeSliceDeviceTest#orca3DFuelMaterialLibraryLoadsIndependentlyAndSlicesRepresentativeFilaments` prüft, dass der neue Markenname vorhanden und der alte Name nicht mehr gelistet ist. Es lädt das ESD-Profil, verifiziert 270 °C und erzeugt einen echten Slice mit ausführbarer Filamenttemperatur im G-Code. Der gezielte API-36-Lauf bestand 1/1; ARM64-Native- und Test-APK-Builds waren erfolgreich.
- Batch 6: **9/10**. Kein APK-/Release-Gate bis zum Abschluss eines weiteren geeigneten Orca-Ports und dessen fokussierter Runtime-Verifikation.

### Punkt 10/10 — Lightning-Infill-Stützproben außerhalb der Infill-Grenze

- Portiert Orca `4b3e4194ff`: geöffnete Überhangproben werden in die Such-Bounding-Box des Lightning-Distanzfelds aufgenommen. `update()` kann dadurch jede erzeugte Probe erreichen und löschen; zuvor konnte eine Probe knapp außerhalb der Infill-Kontur endlos erneut in den Support-Baum aufgenommen werden.
- `NativeSliceDeviceTest#lightningDistanceFieldExpandsBoundsToContainOpenedOverhangSamples` bestand 1/1 und reproduziert Proben außerhalb der ursprünglichen Kontur ohne doppelte Ausgabe. `NativeSliceDeviceTest#lightningInfillCompletesWhenOpenedOverhangExtendsPastInfillBounds` bestand 1/1 mit einem vollständigen nativen Slice samt Sparse-Infill-G-Code.
- ARM64-Slice- und Instrumentierungsbuild erfolgreich. Batch 6 ist damit 10/10 funktional verifiziert; APK-/Release-Gate folgt nach vollständigen Debug-/Slice-Unit-Tests und Release-Build.

## Workspace-Import-Fortsetzung — 2026-11-02

- Import-Arrange ist jetzt atomar: bei Überlauf bleiben vorhandene Modelle an ihren Positionen. Aktivierte Auto-Ausrichtung wird vor dem Arrange angewendet. Nicht passende importierte Objekte werden auf automatisch erzeugten und ausgewählten Druckplatten platziert; einzeln zu große Objekte werden zentriert und gewarnt.
- Die Arrange-Parameter erlauben jetzt zusätzlich XY-Rotationen. API-36-Test mit zwei rechtwinkligen Modellen auf dem 255×255-mm-Bett bewies Platzgewinn, gemeinsame Plattenbelegung und überlappungsfreie Bounds. Import-Overflow und atomarer Arrange-Rollback bestanden ebenfalls direkt auf API 36.
- Mehrmodell-Importe versuchen zuerst ein gemeinsames Arrange; nur bei Platzmangel folgt der atomare pro-Objekt-Overflow-Fallback.
- Android-16-Regressionsprüfung: automatische Plattenerzeugung bei voller Platte, XY-Drehung, Nutzung der konkaven L-Freifläche und Mehrfachauswahl großer STL-Dateien mit Erhalt der vorhandenen Platte bestanden. Der Mehrfachimporttest bestätigt drei belegte Platten, aktive Auswahl der letzten Platte sowie innerhalb der Bettgrenzen platzierte, auf Z=0 liegende Modelle.
- Erneute API-36-Ausführung im aktuellen Arbeitsbaum: `detailedConcaveModelKeepsItsOpenFootprintAboveLegacyProjectionLimit`, `importedObjectsMoveToAutomaticallyCreatedPlateWhenCurrentPlateIsFull`, `importedObjectsRotateInPlaneToUseAvailableBedSpace` und `additionalProjectModelOverflowCreatesPlateWithoutMovingExistingModel` jeweils 1/1 bestanden. `git diff --check` ebenfalls erfolgreich. Xiaomi-Hardware wurde nicht geprüft; diese Gegenprüfung erstellt weder APK noch Release.
- Diese Workspace-Änderung ist kein Orca-Upstream-Port und kein Batch-Zähler. `ModelInstance::get_arrange_polygon()` verwendet für geeignete Meshes bis 50.000 Facetten die vereinigte konkave Projektion; bei ungültigen, zu großen oder nicht projizierbaren Meshes bleibt die konvexe Hülle als sichere Rückfallebene. Die Bounds belegen gedrehte Instanzen; `Model.getRotation()` liest dagegen den ModelObject-Transform, nicht die ModelInstance-Rotation, und ist daher für diesen Test nicht aussagekräftig.

## Fortsetzung nach Batch 5 — Stand 2026-11-01

- Der Stable- und Nightly-Fix für das Kobra-3-V2-Pressure-Advance-Problem ist verifiziert: die erneuerte v2-Migration entfernt gespeichertes `M900 K0.051`, erhält den Filamentwert und wurde durch einen echten API-36-Slice für PA `0.075` sowie deaktiviertes PA geprüft.
- Die vom Benutzer gewünschte Ausrichtung entlang der gedrehten Achsen des Hauptobjekts wurde als Nightly-Ergänzung umgesetzt (`75c87032fa`); UI-Auswahlgruppe, JNI-Projektion, Undo/Redo, 3MF-Rundlauf und Slice sind getestet. Diese zusätzliche Workspace-Funktion zählt nicht als Orca-Upstream-Port und nicht als Batch-6-Item.
- Upstream-Referenz aktualisiert auf `b5517b693e898261a6e28f940b110cd62683fede` (2026-11-01). Die relevanten neueren `libslic3r`-Fixes wurden gegengeprüft: Lightning-Grenzfall `7ac46c9a6` ist bereits in `DistanceField.cpp`; Extruderindex-/Layervergleich aus `fcd96ad91c` ist bereits portiert; Subtypkosten aus `8e37d7d4b` sind im Android-Matcher vorhanden, während geladene Drucker-Tray-Daten weiterhin den vollständigen App-Pfad blockieren. Desktop-Agent-/PresetBundle-Änderung `913c622574` hat auf Android keinen passenden Hostpfad; per-Drucker-MMU-Zustand ist separat bereits implementiert.
- Der Xiaomi-ADB-Installationsversuch der Stable-APK wurde mit `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user` beendet; nicht umgangen. Der Nightly-Objektachsen-Gerätetest bestand 1/1 auf API 36 und `PrimaryObjectAxesTest` bestand.
- Nächster konkreter Nightly-Schritt: die konvexe Arrange-Hülle gegen ein konkaves/mehrteiliges Testmodell messen, danach den präzisen Runtime-Nachweis für Orca #14 weiterentwickeln und einen weiteren Android-relevanten Upstream-Punkt auswählen. Keine Batch-6-APK oder Release, bevor zehn neue, tatsächlich Android-relevante Upstream-Punkte jeweils umgesetzt und verifiziert sind.

### Orca #11 — direkter Bounds-Invariant-Nachweis (2026-11-01)

- `lightning_distance_field_bounds_probe()` baut das produktive `DistanceField` mit einer 10-mm-Infill-Begrenzung und einem 20-mm-Überhang auf und zählt dessen tatsächlich erzeugte Samples. Die Regression belegt zugleich, dass mindestens ein Sample außerhalb der ursprünglichen Grenzen liegt und nach dem produktiven Bounds-Merge kein Sample außerhalb der aktualisierten Grenzen bleibt.
- `NativeSliceDeviceTest#lightningDistanceFieldExpandsBoundsToContainOpenedOverhangSamples`: 1/1 auf API 36; ergänzend `#lightningInfillCompletesWhenOpenedOverhangExtendsPastInfillBounds`: echter Lightning-G-Code-Slice 1/1 auf API 36. Produktcode des Orca-Fixes war bereits portiert; diese Nachweisverbesserung ist kein neuer Batch-6-Featurepunkt.
- Codecommit `6e411be2d6` ist auf `nightly-2.5` gepusht. Der direkte #11-Proben-/Bounds-Blocker ist geschlossen. #14s exakter Abstand zwischen Supportpfad und Mesh bleibt offen.

## Nightly Batch 5 — abgeschlossen: 10/10 Änderungen (2026-11-01)

### Änderung 10/10: Hilbert-Curve-Sparse-Infill glätten

Port Orca `fb36d5e73b`'s configurable Hilbert corner smoothing. `sparse_infill_smooth_factor` (0–100%) is a native process-profile option, appears in the mobile Infill settings, participates in preset save/load and invalidates sparse-infill slicing state. At 0% the original Hilbert path is preserved; higher values round only sparse Hilbert infill corners. Wall, top and bottom toolpaths are unaffected.

`NativeSliceDeviceTest#hilbertSparseInfillSmoothFactorChangesOnlySparseInfillToolpaths` passed 1/1 on an API-36 emulator using real native slices and comparing G-code moves at 0% and 100%. The test confirms the setting reaches the serialized native config, sparse-infill moves change and add flattened segments, and outer/inner walls plus top/bottom surfaces remain byte-for-byte equal. ARM64 native and instrumentation APK builds passed. Full Debug and Slice JVM suites both passed 712/712, zero failures/errors/skips. The complete batch has 10/10 verified changes and its isolated Nightly APK/release are published below.

Batch gate: code commit `4867891309e46c796a70b8bd710131792ce29889`; APK `OrkanSlicerNightly25_4867891309.apk`, 239,951,714 bytes, SHA-256 `96DB4552AEADCBFAEC0FD3715E8460D0E0C76A235AF7361678A43C8F70C6F536`; version 0.8.424 / code 459; package `com.exa201.orkanslicer.nightly25`. It installed and launched on the API-36 emulator. GitHub prerelease: [v0.8.424-nightly.1](https://github.com/Exa201/OrkanSlicer-Nightly-2.5/releases/tag/v0.8.424-nightly.1). Xiaomi `2211133G` installation remains user-restricted by MIUI (`INSTALL_FAILED_USER_RESTRICTED`); no physical test is claimed.

### Änderung 9/10: lange Extrusionsketten lokal verfeinern (2026-11-01)

Port Orca `69193d5681`'s bounded travel refinement into the active extrusion-entity ordering path. Chains longer than eight paths now refine the existing greedy chain with reversible 2-opt and short/long or-opt moves, using a uniform endpoint grid and a capped work queue. A nearest-next chain is also refined and selected only when its measured travel is shorter. Chains without a fixed nozzle start keep the existing behavior; paths that cannot reverse are never flipped. This changes path order only, not geometry or extrusion values.

`NativeSliceDeviceTest#longExtrusionChainsAreRefinedWithoutIncreasingTravel` passed 1/1 on API 36. Its deterministic 12-segment adversarial route verifies that each segment occurs once and that the refined travel is shorter. ARM64 native build and Slice instrumentation APK compilation passed. Full Debug and Slice JVM suites passed 712/712 each, with no failed or skipped tests. Xiaomi physical installation remains unavailable due to `INSTALL_FAILED_USER_RESTRICTED`.

Change 9 verification details follow; Batch 5 completion is recorded above.

## Nightly Batch 5 — Änderung 8/10: Kobra-3-V2-Pressure-Advance-Startwert entfernt (2026-11-01)

Das eingebaute Stockprofil setzte fest `M900 K0.051` im Drucker-Start-G-Code. Damit blieb Pressure Advance aktiv und wurde zusätzlich zum Filamentwert ausgegeben, selbst wenn der Benutzer den Filament-PA-Wert anders einstellte oder ausschaltete. Der feste Befehl wurde aus dem Profil entfernt. Beim ersten Start nach dem Update entfernt eine einmalige Migration exakt diese alte Zeile aus dem gespeicherten Stockprofil `Anycubic Kobra 3 v2 0.4 nozzle - Stock`; andere Profilwerte und manuelle Änderungen bleiben erhalten.

`Kobra3V2ProfileMigrationTest` besteht 2/2. `SlicePrintPreparationComposeDeviceTest#kobra3V2StockStartGcodeDoesNotForcePressureAdvance` prüft das ausgelieferte Profil und bestand 1/1 auf API 36. Debug-Unit- und Slice-Unit-Gesamtsuiten laufen am Batch-Gate; Xiaomi-Installationen bleiben wegen `INSTALL_FAILED_USER_RESTRICTED` ungeprüft. Stable-Ausgabe wurde separat als 0.8.409 (`e28e071`) gebaut. Nightly 5 steht bei 8/10; noch keine APK aus diesem Teilbatch.

Stand: 2026-11-01. Upstream-Vergleich: OrcaSlicer `main` at
`3aedd50bcb94221db79ccbf8d6a7985e795fe25d`; the official OrcaSlicer 2.5.0 Beta notes were also
reviewed. Nightly Batch 4 is at **10/10 implemented**: five native slicer ports, Qidi selected-bed start
temperatures, safe By-Object ordering for tall objects, and Orca's sparse-infill-only arc fitting.
Changes 1–8 were already on `nightly-2.5`; changes 9–10 are now focused-test verified locally.
The batch APK gate follows full Debug/Slice JVM regression and commit/push. This is a selective
Android port, not the complete upstream engine.

Batch-4 build verification: Debug and Slice JVM suites both passed 708/708 (0 failures, errors,
or skips). The two new native slice instrumentations passed 1/1 each on API 36. Nightly APK
`OrkanSlicerNightly25_a717018b7e.apk` (version 0.8.423 / code 458, application ID
`com.exa201.orkanslicer.nightly25`) built from code commit `a717018b7e31f871f3903916bc0a391ac786d10e`,
239,853,168 bytes, SHA-256 `9C8E1340613FDB6C3CCC0C77E631524AC7B28315379BE4FC9CD67ED764C26042`. It installed and
started on the API-36 emulator without FATAL EXCEPTION, native fatal signal, ANR, or OOM in the
captured logcat window. Xiaomi 2211133G installation remains blocked by Android's
`INSTALL_FAILED_USER_RESTRICTED`; this is not a Xiaomi test pass.

## Verified preview-performance finding

Desktop Orca exposes OpenGL MSAA sample counts (off/2/4/8/16), optional FXAA, and an FPS
cap. The Android workspace instead uses a GLES3 `GLSurfaceView` whose EGL config and
renderer lifetime are established by the existing host. Directly changing the desktop MSAA
setting is not compatible without redesigning that surface setup. OrkanSlicer already scales
the GL surface and maps touch coordinates to it, so the safe Android equivalent is a user
selectable viewport render scale. It changes only display pixels: native model data, slicing,
G-code, and export are untouched. Automatic mode retains the low-RAM device policy.

The Android adaptation now uses 70% for low-RAM devices and 65% for recognized legacy/software GL renderers, with 100% for unknown/current renderers. Users can override it at 50/65/80/100%. Recognized low-performance devices also use a 35-layer initial G-code preview block, simplified inactive plate previews, and a 180,000-triangle active-preview cap; low-RAM keeps stricter caps. These limits affect display previews only, never model geometry or slicing. Policy tests passed; GL surface/picking passed at 50/65/100% on API 15/16 emulators. In this turn the focused policy JVM test and 1/1 GL/touch test passed again on an API-36 emulator. Xiaomi 2211133G rejected instrumentation installation (`INSTALL_FAILED_USER_RESTRICTED`), so physical-device verification remains open. The renderer-name classifier is a conservative heuristic, not a benchmark.

The official Orca 2.5.0 Beta notes do not describe a dedicated weak-GPU preview-quality switch; they highlight slicing, travel, infill, toolhead-clearance, and translucent-preview changes. This Android render-scale/preview-budget policy is the compatible adaptation, not a direct port of a desktop graphics preference.

## Product identity / data isolation

Nightly uses Android application ID `com.exa201.orkanslicer.nightly25` and display name
`OrkanSlicer Nightly 2.5`. Android scopes private preferences, files, cache, and app-specific
external storage to the application ID; therefore Nightly installs beside the stable app with
separate data. The Kotlin namespace remains `ru.ytkab0bp.slicebeam` to avoid a needless source
and JNI package migration. SAF files explicitly selected by the user remain shared documents.
The Nightly updater queries the Nightly repository and selects published prereleases rather
than the stable app's `/releases/latest` endpoint.

Side-by-side installation was exercised on the API-36 emulator with Stable 0.8.414 and Nightly
0.8.452 both installed. Android assigned separate UIDs and private data roots, and both packages
resolved their own FileProvider authorities. `NightlyIdentityDeviceTest` now guards the Nightly
package ID, config/model-cache roots, default preference file, and provider authority (1/1 passed).

## Existing Android parity verified from source

Already present: Move, Rotate, Scale, Mirror, auto-orient, flatten/lay-on-face, arrange,
duplicate/fill/center/delete/select, plane cut and connectors, color/support/seam/fuzzy paint,
text emboss/engrave, measure, variable layer-height profile, split to objects/parts, mesh
simplification, and Boolean union/difference/intersection. Do not reimplement these as new
features; extend them only where a demonstrated parity gap remains. The older
`ORCA_FEATURE_AUDIT.md` Boolean row is stale relative to `ModelBooleanDialog`,
`GLRenderer`, and the current Boolean workspace path.

Profile parity is substantial: the current audit records 887 parsed native options and 492
visible routed profile rows (319 process, 101 filament, 72 printer). Those figures measure
source routing, not desktop completeness or runtime verification.

## Prioritized feature backlog

| # | Candidate | Current evidence / gap | Acceptance evidence |
|---:|---|---|---|
| 1 | Low-GPU preview quality control | Implemented: automatic mode uses Android low-RAM status plus a conservative `GL_RENDERER` classifier for known software/legacy GPU families (70% on low-RAM, 65% on recognized low-end GL, 100% for unknown/current hardware); explicit 50/65/80/100% viewport choices remain available. Recognized low-performance devices use a 35-layer initial G-code preview block, simplified inactive plate previews, and 180,000-triangle active-preview cap; low-RAM remains stricter. Renderer identity is runtime-only. | Policy JVM tests passed; settings interaction passed on API 15 and API 16 emulators on rerun (initial API 15 attempt failed while locating Settings before exercising the target action); GL surface/picking passed at 50%, 65%, and 100% on both emulators. Physical-device performance benchmarking remains pending. This is a conservative renderer-name heuristic, not a measured GPU benchmark. |
| 2 | Compound 3MF paint ownership | Implemented: each model-part volume owns a stable heap-allocated mesh, selector and AABB raycaster; world-space hit testing returns the source volume. Paint, undo/redo, clear and commit address the selected part without overwriting other parts. | Emulator ARM64 `TwoColorProjectDeviceTest` 5/5 passed, including independent painting of two combined 3MF parts, live overlays, undo/redo, 3MF export/reload and generated G-code with the expected T1 tool change. Physical-device validation remains outstanding. |
| 3 | Qidi Box slot mapping | Implemented: active G-code tools can map to Qidi Box slots 1–16; repeated slots are allowed, unmapped tools retain the printer setting, and confirmed choices persist per printer/host in Nightly-private preferences. Orca 2.5 Qidi agent semantics use `SAVE_VARIABLE value_tN` before `/printer/print/start`; the guarded Moonraker sequence is implemented. | Policy tests 5/5, dialog selection/persistence/cancel emulator tests 2/2, source compilation. Actual Qidi firmware/network behavior remains unverified without a Qidi device. |
| 4 | Orca logical-to-physical extruder map | Implemented: the printer profile exposes Orca's `physical_extruder_map`; the vector editor supplies an identity default, validates one zero-based target per logical extruder, and permits repeated targets. Sparse custom multi-nozzle profiles now skip absent/empty variant vectors instead of dereferencing them during native slicing. | Policy/catalog JVM tests 8/8; Android 16 ARM64 native slice class 51/51; filament/MMU sync regression 15/15. A real MMU/AFC device workflow remains unverified. |
| 5 | Multipart per-part filament reassignment | Implemented: the expanded object list exposes a dedicated filament action for each printable part. The native GL-thread edit changes only that volume, clears only its facet-level material map, refreshes the live mesh, and preserves sibling volumes. | Compose action test 1/1; Android 16 ARM64 `TwoColorProjectDeviceTest` 6/6, including slot swap, 3MF save/reload and native slice. Generic arbitrary process/filament setting overrides remain a later gap. |
| 6 | General modifier volumes | Implemented: the Model menu can add, position, resize and remove Orca-native negative volumes, support blockers and support enforcers on the selected object. Their typed meshes render as translucent, color-coded overlays and remain separate from printable parts. Existing modifier volumes can be inspected, resized and removed. The Orca 3MF importer now accepts the type strings emitted by its own exporter; box geometry is centered consistently so coordinates survive round-trip. | Android 16 ARM64 tests passed: Compose type/creation input 1/1; native add/edit/delete plus undo/redo snapshots and 3MF position/size/type round-trip 1/1; native slice from the reloaded project 1/1, with changed extrusion toolpaths and printable output. Per-volume arbitrary parameter-setting editing is not included in this geometry feature. |
| 7 | SVG surface emboss/engrave | Implemented: SAF selection and bounded/secure validation, width/depth controls, GL surface-tap placement, raised printable part or native negative volume; SVG curve tessellation uses a 0.05 mm target error. | `suppliedSvgCanBeRaisedOrEngravedOnASurfaceAndStillSlicesAfterThreeMfReload` passed 1/1 on API-35 x86_64 emulator with Android's ARM64 native bridge. It checks both modes, 3MF reload and changed extrusion paths. Manual live placement preview/cancel-before-commit remains open; commit-on-tap is undoable. |
| 8 | 3MF transform and parser-size parity | Ported Orca fixes `67a976e0` (preserve valid mirrored matrices in Prusa/BBS importers) and `d35ea27e` (reject oversized XML ZIP entries before Expat allocation). | Mirrored Prusa/BBS round-trip passed 1/1 and oversized ZIP64 XML rejection passed 1/1 on API-35 x86_64 emulator with ARM64 native bridge. |
| 9 | Qidi profile parity | Q2/Q2C profiles use selected filament bed temperature for `PRINT_START`/`M140`, initialize Qidi Box mapping, select the initial logical tool and use material-aware filenames. | Qidi Q2 native slice/G-code test passed 1/1 on API-35 x86_64 emulator with ARM64 native bridge. It verified 67 °C in `PRINT_START`/`M140` and resolved the executable initial-tool command. Profile transmission to a physical printer remains unverified. |
| 10 | Orca profile-option gap refresh | Re-ran the route audit against 887 native options and 492 visible setting rows. Found `physical_extruder_map` in the visible printer catalog but missing from `PRINTER_CONFIG_KEYS`, which controls import/export routing; added it to the printer key set and a persistence/import regression test. Updated the route checker to follow the current Compose render-scale, navigation, workspace sync, color-editor and profile-import APIs instead of stale pre-migration names. | `check_profile_setting_routes.py` passes: all 492 visible rows resolve to native definitions/profile parsers and 107 German group labels are unique. JVM key persistence test passes 1/1. |

### Later candidates

- Review upstream 2.5 fixes in bounded, pinned groups, each with a concrete regression and native
  or device evidence; do not bulk-copy commits or advance the engine lock prematurely.
- Validate frame pacing/FPS cap against Android battery/latency behavior after render-scale
  control is measured. Desktop preference semantics are not presumed to transfer to Android.
- Reassess other Orca desktop functions only after inspecting their native dependencies, Android
  UI feasibility, mobile memory cost, and license/build requirements.

### Latest-upstream feature triage (2026-10-31)

The Orca 2.5.0 Beta notes emphasize toolhead collision volumes, shorter travel moves, rounded
infill corners, translucent-filament preview and slicing performance on detailed/multipart models.
The alpha feature set additionally includes Texture Displacement, experimental parametric Design,
Python plugins, color mixing and IDEX/IQEX parallel printing. These are not all suitable Android
ports: collision geometry needs machine-profile/runtime validation; translucency is a render-only
candidate; rounded infill and travel optimization need native G-code comparisons; Texture
Displacement requires mesh/3MF ownership and memory/cancellation work; the Design tab, arbitrary
Python execution, color mixing and IDEX require substantial independent subsystems or hardware
contracts and stay deferred until those prerequisites exist.

The fetched `main` delta after `5cc5fb7f` is profile/localization work rather than new engine code.
New vendor profiles are useful only if the Android setup wizard can consume their profile index and
inheritance chain; do not count copied JSON assets as a working printer feature without testing
selection and slicing. Keep the current implementation order: complete the open native regression
gaps in batch 2, then prototype a bounded, cancelable Texture Displacement path; investigate
translucent preview and collision-volume support as separately testable follow-up candidates.

### OrcaSlicer 2.5 Alpha capability review (2026-10-31)

The official 2.5.0 Alpha notes describe a large desktop release, not a drop-in Android package.
The following candidates are ranked by whether they can safely use the existing native engine and
mobile interaction model. Do not count a candidate as implemented until its Android action and
regression evidence exist.

| Upstream capability | Android evidence / feasibility | Port decision |
|---|---|---|
| Texture Displacement | Native mesh displacement and texture sampling could run on Android, but it can greatly increase triangle count and RAM. | High-value later candidate; prototype bounded resolution, cancellation, memory guard, undo/redo, and slice parity before exposing. |
| Parametric Design tab | Requires a sketch/CAD constraint solver, feature tree, replay, and 3MF persistence; it is a separate CAD subsystem. | Do not imitate with static mesh generators. First scope solver dependencies and an independently versioned parametric project format. |
| Python plugins | Arbitrary code, filesystem, network, and process access create a large security and lifecycle surface on Android. | Not a direct port. Consider only a sandboxed, permissioned extension API after threat modeling; no arbitrary plugin execution in this plan. |
| Color mixing | Depends on compatible multi-input hardware and firmware semantics, unlike ordinary single-nozzle MMU tool changes. | Defer until a supported mixer hardware profile and firmware-safe G-code contract are identified. |
| IDEX/IQEX parallel printing | Requires independent carriage collision planning and printer-specific G-code/start sequencing. | Defer pending a complete, testable dual-carriage printer profile; never infer support from generic extruder count. |
| Belt printers | Coordinate transforms, bed geometry, and print sequencing differ from planar beds. | Defer until belt printer machine limits and G-code are ported as a coherent profile family. |
| Speed Dial / action launcher | Mobile already has searchable menus and configurable bottom actions; parity should not duplicate those entry points. | Revisit only if the menu audit finds a measured discoverability gap. |
| Faster startup, slicing, and G-code export | Upstream contains many independent native changes and fixes. | Port individually against pinned commits with Android ARM64 regression tests; do not mass-update the engine lock. |
| Precise Seam modifiers and new infill options | Some native engine support may already exist; UI coverage and serialization need verification. | Audit current keys/enum paths first, then expose only safe, tested gaps in backlog item 10. |
| Mixed nozzle sizes / new printer support | Depends on per-tool nozzle geometry, profile definitions, and G-code validation. | Treat as profile-family work with fixture-based export tests, not a generic UI toggle. |

Primary upstream source: [OrcaSlicer V2.5.0 Alpha release notes](https://github.com/OrcaSlicer/OrcaSlicer/releases).
The existing backlog items 7–10 remain the active implementation sequence; this review adds candidates
for prioritization after those items and is not itself counted as a shipped feature.

## Delivery cadence and gates

1. Keep a numbered implementation log for the ten backlog items above.
2. For each item, inspect pinned upstream source and Android dependencies, implement only a safe
   adaptation, and add focused regression tests.
3. After each group of ten completed feature items, run grouped JVM/native/device verification,
   create a Nightly APK, inspect package identity and APK metadata/hash, then publish a Nightly
   GitHub release and update the changelog. Do not claim unsupported device tests passed.
4. Task #1 has code, policy/settings-flow tests and a live Android 16 emulator interaction: GL surface
   size and M12 ray picking passed at 50% and 100% render scales; physical-device check is pending. Task #2 passes
   `TwoColorProjectDeviceTest` 5/5 on the Android 16 ARM64 emulator, including distinct-volume
   painting, undo/redo, 3MF reload and generated G-code with the expected T1 tool change. Task #3
   passes mapping policy tests 5/5 and Qidi dialog emulator tests 2/2 (selection, persistence, cancel);
   the upstream `SAVE_VARIABLE` command and fail-closed-before-print path compile, but a live Qidi
   firmware/network exchange is not verified. Three items (#1–#3) have implementation and focused emulator coverage (3/10); physical-device
   and physical-printer validation remain outstanding.
   Items #4 and #5 are implemented and covered by focused tests: 5/10 feature items. The physical extruder-map work passed policy/catalog JVM 8/8, `NativeSliceDeviceTest` 51/51, and filament/MMU sync 15/15; the part-level assignment passed its Compose action test 1/1 and `TwoColorProjectDeviceTest` 6/6 on Android 16 ARM64, including 3MF round-trip and native slicing. Generic arbitrary process/filament overrides and real MMU/AFC device validation remain open. No APK or release is created from this partial batch.

## Source audit documents

- `docs/ORCA_FEATURE_AUDIT.md` — profile and model-tool inventory; update stale rows when the
  current source proves the implementation exists.
- `docs/ORCASLICER_2_5_DELTA_AUDIT.md` — pinned upstream fixes and Android-port constraints.
- `ORCA_NIGHTLY_LOCK.md` — do not advance until engine identity and native test evidence justify it.

## Next Orca 2.5 parity batch (items 11–20)

Candidate selection is based on upstream commits present in `orcaslicer-upstream/main` as of
2026-10-31. These are scoped targets, not completion claims. Each requires Android-specific
regression evidence before it can count toward an APK/release batch.

| # | Upstream change | Android adaptation / acceptance evidence | State |
|---|---|---|---|
| 11 | `7ac46c9a6` Lightning overhang samples outside the infill bounds can make tree generation loop indefinitely. | Ported in `Fill/Lightning/DistanceField`: expand the searchable box to all unsupported sample points. In addition to the Bunny regression, an API-35 instrumented test now slices a stepped prism with a 10 mm stem and a 20 mm cap, deliberately placing the opened overhang beyond the stem's infill bounds; slicing must finish within the 90 s test limit and emit Lightning extrusion. Passed 1/1 on each of two API-35 emulators. | Ported; targeted outside-infill-bounds geometry runtime regression passed on two emulators |
| 12 | `9b4f84a92` Preserve every extruder's values when a custom printer has more nozzles than its parent. | Native preset code extends inherited nozzle vectors in local/system, project, and cloud paths. Android currently loads flattened profile `.ini` data and has no active `PresetBundle` call site; the source port compiles, but no live Nightly app route exercises this behavior. | Core port present; not counted as Android feature until an app call path exists |
| 13 | `fcd96ad91` Apply each object's own extruder layer-height limits and compare actual layer heights for prime-tower compatibility. | Corrected zero-based object extruder to one-based nozzle lookup. API-35 ARM64-bridge test slices two objects on nozzles with different min/max limits and the same actual layer sequence through one prime tower; deliberately unequal adaptive profiles remain correctly incompatible. | Implemented and runtime-verified (1/1) |
| 14 | `a4a510087` Keep build-plate-only tree supports inside XY clearance. | API-36 ARM64 slice of a synthetic 10 mm square stem / 20 mm cap. The test excludes stationary E-only prime commands, measures every positive-length support extrusion between Z+0.5 and Z+9.5 against the exact analytic stem cross-section, and asserts no path crosses the solid. Minimum path-centerline gaps at configured 0.2 and 0.8 mm were 0.4845 and 0.6370 mm (+0.1525 mm); 1/1 passed. This proves cross-section non-intersection and a monotonic response, not that the deposited bead surface maintains the full configured clearance. | Existing port; exact synthetic model cross-section path check passed 1/1 on API 36; deposited-bead clearance remains open |
| 15 | `98b539c3e` Use the printing filament's retraction-lift settings. | Android reads thresholds/Z-hop and enforcement from the active logical filament ID. Two-filament temporary-project G-code regression passed 1/1 on API-35 with ARM64 bridge; its parser excludes the normal end-of-print head-raise command. | Equivalent path implemented and runtime-verified (1/1) |
| 16 | `b7ecf1b29` Avoid false out-of-printable-range warnings for an extruder area inset on multiple sides. | Added a JNI diagnostic getter for the native G-code validation error bitmask and an API-35 ARM64-bridge slice regression using a real tool-changer profile. The test slices the model inside a small inset toolhead polygon without the toolhead-area warning, then moves that polygon away and confirms the same mapped toolhead is rejected. | Existing port; asymmetric area false-positive and positive-control runtime test passed (1/1) |
| 17 | `29e189957` Ensure sparse layers exist during a tool change. | Both Android wipe-tower generators restore model Z before the travel-capable macro. The compacted-tower multicolor G-code regression passed 1/1 on API-35 with ARM64 bridge, checking toolchange Z against the active model layer. | Implemented and runtime-verified (1/1) |
| 18 | `4c7928d8a` Decide prime-tower need from the current model's filament set rather than the preceding model. | `Print::apply` now retains pre-normalized settings and re-evaluates after current model regions/filaments are rebuilt, so normalization can re-enable the tower. ARM64 native build and instrumented Kotlin compilation pass; a reused-Print runtime regression is still needed. | Ported; runtime test open |
| 19 | `59f0cc3806ab6424d081978fde331088c55d9f0f` Avoid indexing empty layer lists for objects too thin to create any layer when a raft is enabled. | Both guards are present in brim grouping and precise-Z layer alignment. The raft 0/2 × precise-Z off/on regression returned the expected normal “No layers were detected” error instead of crashing; passed 1/1 on API-35 with ARM64 bridge. | Equivalent guard runtime-verified (1/1) |
| 20 | `d28c71c48` Preserve enum-list config keys in default maps. | JNI serializes normalized `coEnums` defaults through `ConfigOptionDef::create_default_option`; enum values and nullable `nozzle_type=undefine` survive the native catalog, and a basic native slice still emits extrusion. Passed 1/1 on API-35 with ARM64 bridge. | Ported and runtime-verified (1/1) |
| 21 | `27cafaa06` Read nozzle diameters correctly with a decimal-comma UI locale. | Desktop-only nozzle/device UI routes are absent from Android. In the shared Bambu 3MF parser, replace locale-sensitive `atof` with `string_to_double_decimal_point`. The Android importer already enters a C numeric-locale guard, so this is explicit format-parsing parity and defense in depth, not a demonstrated Android locale bug. ARM64 native build passes; no dedicated import fixture assertion yet. | Defensive parity port; not counted as a completed feature group |
| 22 | `7b6c12cf7` Match grouped filaments to those loaded in dual-nozzle printers. | Relevant only after loaded physical-tray identity reaches the Android `Print` object. Current `MachineFilamentInfo` has no filament ID; `build_full_machine_filaments` reads color/type/tray name only; `Print::set_extruder_filament_info` has no Android call site. The model-side `filament_ids` already exist, but that alone cannot match to loaded trays. Defer algorithm changes until this data path and a generated-tool regression are implemented. | Audited; plumbing prerequisite missing, no partial port |
| 23 | `8e37d7d4b` Match closer colours and the same filament subtype in Convenience mode. | Latest upstream delta since `7ac46c9a6`. This modifies the color-distance assignment solver to prefer matching `filament_id` subtypes only after color distance, without letting subtype outweigh a color-distance step. Android's solver has neither machine-slot IDs nor a populated printer-filament list in `Print`; make the data path and a deterministic multi-tray G-code regression prerequisite. | Audited; blocked on loaded-tray data plumbing |
| 24 | `aa66e41e6` Group a filament onto the nozzle that has it loaded in Filament-Saving mode. | Latest upstream delta since `7ac46c9a6`. Candidate generation uses loaded per-nozzle trays and flush-cost budget to prefer fewer nozzle changes. Android exposes a model-side logical-to-physical map, but that is not loaded-tray identity. Requires real per-nozzle filament configs, test fixtures, and generated tool-sequence assertions before porting. | Audited; blocked on loaded-tray data plumbing |

### Current continuation — 2026-10-31

- The low-GPU viewport option is already implemented: automatic device policy uses low-RAM and known renderer hints; users can override it in App settings. It changes only GL surface resolution and preview mesh budgets, not slice geometry. Policy JVM tests and live emulator touch-coordinate checks are recorded in `UI2_EXECUTION_STATE.md`; physical GPU benchmarking remains open.
- Orca 2.5 candidates 11, 13, 14, 15, 17, 19, and 20 have focused runtime evidence using the emulator's ARM64 native bridge. Candidate 11's direct probe/bounds assertion is additionally covered by API-36 tests. Candidate 14 now checks the exact analytic cross-section of its synthetic solid and verifies that no positive-length tree-support extrusion enters it; the deposited-bead surface gap remains unverified. Candidate 15's initial failure was a test false positive: the end-of-print head raise was being counted as an extrusion Z-hop. Candidate 13's deliberately different adaptive profiles were correctly rejected; its final regression checks unequal nozzle limits with the same actual layer sequence.
- Candidate 16's asymmetric inset-area false-positive and excluded-area positive control now pass through the real Android native slice path (1/1). The test-only evidence uses a native validation error bitmask getter exposed to Kotlin as a general slice diagnostic. Candidate 18's `Print::apply` re-normalization port compiles, but Android's JNI creates a new `Print` for each `model_slice` call; a reused-Print runtime scenario does not currently occur in the app. Candidate 12's preset inheritance similarly has no active Android `PresetBundle` call path.
- Candidate 19 is the thin-object/raft guard, not mixed-filament validation. Orca's separate mixed-filament component-expansion fix is outside current scope because this Android fork has no mixed-filament composition model or `FilamentMixer`; it must not be partially ported as a bed-check-only change.
- ARM64 native builds, focused instrumentation, and the complete Debug/Slice JVM suites pass. On 2026-10-31, both JVM suites passed 704/704 with 0 failures, 0 errors, and 0 skips; the focused #16 device regression passed 1/1 on API 35 x86_64 with ARM64 translation. Physical GPU benchmarking and direct Qidi firmware/Moonraker communication remain hardware-only follow-ups.
- Orca upstream advanced to `27cafaa06` (2026-10-31). Candidate #21's locale-safe nozzle parser is applied to the shared Bambu 3MF reader; its existing outer `CNumericLocalesSetter` already protects Android import, so the change is defensive parity and does not count as a newly verified feature. ARM64 native build passed; direct metadata round-trip coverage remains open.
- Next: refine #14 from a sampled G-code wall-path median to the exact minimum clearance against the adjacent model cross-section. #11's exact sample-outside-original-bounds and inclusion-in-expanded-bounds invariant now has a direct native probe as well as the production Lightning slice regression. Do not count #12 or #18 as app-visible feature completions without an Android call path; replace unsuitable candidates from the pinned upstream backlog before the ten-item APK/release gate.
- Additional focused API-35 ARM64-bridge runtime evidence: `NativeSliceDeviceTest#lightningInfillCompletesOnOverhangRichBunnyFixture` passed 1/1 after explicitly placing the imported Bunny on the bed; generated G-code contains sparse-infill extrusion. `#buildPlateOnlyTreeSupportsSliceWithConfiguredObjectClearance` passed 1/1 and verified that changing build-plate-only tree-support clearance from 0.2 to 0.8 mm changes generated support extrusion paths. A first AVD attempt ended when the emulator went offline; the repeat passed after restarting the Android-15 ARM64-translation AVD. One initial Lightning attempt exposed an improperly placed test fixture and was corrected with `ensureOnBed(0)`. These failed attempts are not counted as passes.
- The Lightning test now directly asserts that the expanded bounds include samples outside the original infill bounds, in addition to exercising actual native slicing. The tree-clearance test still does not measure minimum support-to-model mesh distance; keep that narrower diagnostic open.

After each ten-item group reaches its explicit code and runtime gates, build and inspect the isolated
Nightly APK and publish the corresponding Nightly GitHub prerelease. Items 1–10 now meet their
defined test gates; hardware follow-ups are not represented as tested.

## Latest upstream delta reviewed — 2026-10-31

`orcaslicer-upstream/main` now points to `5cc5fb7f9c40c42ca47fc13969756c4e53119046`. Its three
new commits were checked individually:

- `5cc5fb7f9` stops Bambu LAN SSDP discovery before destroying the desktop network agent. Nightly has
  no Bambu desktop plugin or equivalent agent lifecycle; its Moonraker path has no matching API.
- `913c62257` remembers printers per desktop printer-agent and keeps AMS system filaments visible.
  The desktop `DeviceManager`/`PresetBundle` path is absent on Android, so no direct port is warranted.
  The related Android MMU state-isolation gap is handled by the adaptation below.
- `7af399554` links libjpeg for the Linux Arch build and does not apply to Android.

The upstream reference was refreshed; the native engine lock was not advanced. Each Android port
still needs an app route and its own runtime evidence.

## Android adaptation — per-printer MMU synchronization state (2026-10-31)

| # | Upstream basis | Android change and evidence | State |
|---|---|---|---|
| 25 | `913c62257` printer-specific persistence concept | MMU synced markers, selected tool-to-gate choices, palette-to-gate order, and Moonraker snapshots are stored under SHA-256 preference keys derived from the printer profile title. Existing single-printer keys remain as migration fallbacks and are copied to scoped keys on read. The global filament palette remains the user's active palette; only printer-specific MMU metadata is scoped. `MmuFilamentSyncDeviceTest` passed 16/16 on each of two API-35 emulators, including A→B→A switching with distinct gate choices and snapshots. `FilamentPaletteSyncPolicyTest` passed 10/10; Android test Kotlin compilation and `git diff --check` passed. | Implemented and emulator-verified; physical-printer validation remains open |

## Orca `304924a69` — Default-Zuweisung für Feature-Filamente (2026-10-31)

Der upstream-Fix entfernt die automatische Kaskade, durch die das Auswählen eines Infill-/Oberflächen-Filaments auch andere Features vom Default-Objektfilament wegzog. Der Nightly-Kern enthält die entsprechende Korrektur bereits in `6d8f78d9d`, und dieser Commit liegt vor dem Release-Commit `0db4f11d3` für `v0.8.420-nightly.1`; sie ist daher keine neue Implementierung für den folgenden Zehner-Batch. Der vorhandene Android-Regressionstest `oneFeatureFilamentOverrideDoesNotCascadeToOtherInfillOrSurfaceFeatures` wurde am 2026-10-31 erneut auf Android 16 ausgeführt: 1/1 bestanden, einschließlich nativer Slice-Ausgabe und Prüfung der vier Feature-T-Werkzeuge. Diese Prüfung bestätigt die G-Code-Zuweisung für die getesteten Standard-/Feature-Fälle; sie ersetzt keine Prüfung einer beliebigen privaten farbigen 3MF-Datei oder physischer MMU-Slotzuordnung.

## Orca `ffdb5da10` — BenFab 450 Druckerprofile (2026-10-31)

Übernommen wurden Orcas BenFab-450-Maschinenfamilie mit 426×426×480-mm-Druckbereich, Klipper-Start-/End-G-Code, vier Düsenvarianten (0,4/0,6/0,8/1,0 mm) und 18 dazugehörigen Prozessprofilen. Die Profile werden wie die anderen Vendor-INIs vom Setup-Assistenten automatisch eingelesen. Der Instrumentierungstest prüft alle vier Varianten und führt für jede einen nativen Slice aus; der erzeugte G-Code muss Extrusion und die konfigurierte `G28 ;home`-Startsequenz enthalten. Der gezielte Lauf bestand auf API 35 und API 36 (je 1/1 Test; vier Varianten pro Testlauf). Das ist Profil-/Slice-Parität, kein Test mit einem physischen BenFab-Drucker. Orcas zugehöriges Bed-Mesh/Cover ist derzeit nicht Teil der App-Assets; die Android-Workspace-Druckplatte nutzt die native `printable_area`-Geometrie.

## Nächster APK-Batch nach `v0.8.420-nightly.1`

Der zweite Nightly-Batch steht nach dem BenFab-Port bei **4/10** eigenständigen Produktänderungen:

1. `24ee77b1f` — Orca-Enumlisten-Defaults in den Android-Profilkatalog übernehmen.
2. `a8dd87ba4` — MMU-/Druckersynchronisierungszustand je Druckerprofil isolieren.
3. `8e5c25773` — Low-GPU-/Low-RAM-Vorschau anpassen, ohne Modell oder Slice zu verändern.
4. `6899a7fed` — BenFab-450-Profile einschließlich vier echter Slice-Varianten ergänzen.

## Orca `eac3ba441` — BLOCKS RF50 0,6-mm-Start-G-Code (2026-10-31)

Der in Orca reparierte Profilfehler war auch in `orca_profiles/Blocks.ini` vorhanden: `SET_PRESSURE_ADVANCE` stand hinter einem Semikolon in der `PRINT_START`-Zeile und war damit ein G-Code-Kommentar. Die Android-Profilzeile trennt die Befehle jetzt durch einen Zeilenumbruch. `NativeSliceDeviceTest#orcaBlocksRf50SixTenthNozzleKeepsPressureAdvanceAsExecutableGcode` lädt das echte RF50-0,6-mm-Profil, slice't einen Testwürfel und prüft, dass `SET_PRESSURE_ADVANCE ADVANCE=0.046` als eigene ausführbare G-Code-Zeile und nicht innerhalb des Kommentars erzeugt wird. API 35 und API 36: je 1/1 bestanden. Kein physischer RF50-Drucker wurde getestet.

Der Batchstand nach diesem Fix beträgt **5/10**. Änderung 5: `6dacbf757` macht den Pressure-Advance-Befehl im RF50-0,6-mm-G-Code ausführbar. Noch fünf unabhängig verifizierte Änderungen bis zur APK-/Release-Schwelle. Es wurde keine APK aus dem Teilbatch gebaut oder veröffentlicht.

## Orca `3ca96a089` — unabhängige 3D-Fuel-Filamentbibliothek (2026-10-31)

Orca hat 13 3D-Fuel-Materialprofile ergänzt. Nightly übernimmt sie als `app/src/main/assets/orca_profiles/3D-Fuel.ini`, ohne einen nicht unterstützten 3D-Fuel-Drucker vorzutäuschen. Das Vendor-Metadatum `filament_library = 1` lässt den Setup-Assistenten die Profile auch dann anbieten, wenn ein anderer Drucker ausgewählt ist; normale druckerspezifische Filamentvendors bleiben weiterhin an die Druckerauswahl gebunden.

`SetupProfileSelectionPolicyTest#independentMaterialLibrariesAreShownWithoutSelectingTheirPrinterVendor` prüft beide Auswahlregeln. `NativeSliceDeviceTest#orca3DFuelMaterialLibraryLoadsIndependentlyAndSlicesRepresentativeFilaments` lädt das echte Bibliotheksasset, prüft alle 13 Profile und slicet Standard-PLA, Pro-PETG und PET-CF mit einem isolierten Anycubic-Testprofil. Der G-Code muss Extrusion und die jeweilige Temperatur im ausführbaren `G9111`-Startmakro enthalten. API 35 und API 36 bestanden je 1/1; der gemeinsame Lauf mit dem Policy-Unit-Test endete `BUILD SUCCESSFUL`. Keine physischen 3D-Fuel-Drucke.

Damit beträgt der zweite Nightly-Batch **6/10**. Die nächste APK wird erst nach vier weiteren unabhängigen, fokussiert verifizierten Produktänderungen gebaut. Die Nightly-App-ID `com.exa201.orkanslicer.nightly25` und der Anzeigename `OrkanSlicer Nightly 2.5` sind bereits gegenüber der Stable-App getrennt; ihre privaten App-Daten/Einstellungen werden nicht übernommen.

## Orca `efd65b733` — OpenEYE Peacock V2 Moonraker-Host (2026-10-31)

Orca änderte den OpenEYE-Peacock-V2-Host von OctoPrint auf Moonraker. Im Android-Profil fehlte `host_type` bisher; OrkanSlicer fiel dadurch auf den OctoPrint-Standard zurück, obwohl die Maschine Klipper nutzt. Alle vier Düsenvarianten (0,2/0,4/0,6/0,8 mm) geben nun explizit `host_type = moonraker` vor. Das ist der von Androids Upload-Auswahl verwendete Wert; das desktopbezogene `printer_agent` wird nicht als Android-Verhalten portiert.

`NativeSliceDeviceTest#orcaOpenEyePeacockV2ProfilesUseMoonrakerAndSlice` lädt das echte Profilasset, prüft alle vier Host-Zuordnungen gegen die Upload-Policy und slicet mit 0,4-mm-Düse und Generic PLA. Extrusions-G-Code und `START_PRINT` müssen erhalten bleiben. API 35 und API 36 bestanden je 1/1. Ein erster Testentwurf prüfte zusätzlich das hier nicht verwendete `printer_agent`-Metadatum und scheiterte an dieser Testannahme; nach Ausrichtung auf den tatsächlichen App-Datenpfad bestanden beide Geräte.

Der zweite Nightly-Batch steht damit bei **7/10**. Drei weitere unabhängig verifizierte Produktänderungen sind erforderlich, bevor die APK gebaut und als Nightly-Prerelease veröffentlicht wird.

## Orca `efd65b733` — OpenEYE Peacock V2 Prozessprofil-Parität (2026-10-31)

Die bestehenden 13 OpenEYE-Prozessprofile übernehmen nun die Werte aus Orcas aktualisiertem gemeinsamen bzw. OpenEYE-spezifischen Prozessprofil: deaktivierte Fuzzy Skin als expliziter Default, Fuzzy-Abstands-/Dickenwerte, Erstschicht-Reisebeschleunigung 6000, deaktivierte bedingte/Innenwand-Seam-Slope mit 10 mm Mindestlänge und 10% Start-Höhe sowie Support-Ironing-Abstand 0,15 mm. Die Einstellungen sind an allen 13 Profilen vorhanden, statt nur an einem einzelnen Düsentyp.

Der bestehende `orcaOpenEyePeacockV2ProfilesUseMoonrakerAndSlice`-Ende-zu-Ende-Test prüft diese Werte für alle 13 Prozessprofile, kontrolliert die generierte native Konfiguration und slicet anschließend auf dem 0,4-mm-Profil. Extrusions-G-Code und `START_PRINT` bleiben vorhanden. API 35 und API 36 bestanden je 1/1.

Der zweite Nightly-Batch steht jetzt bei **8/10**. Zwei weitere geprüfte Produktänderungen sind erforderlich, bevor die APK gebaut und als Nightly-Prerelease veröffentlicht wird.

## Orca `efd65b733` — OpenEYE modellbezogene G-Code-Dateinamen (2026-10-31)

Alle 13 OpenEYE-Prozessprofile verwenden jetzt Orcas Dateinamentemplate mit Modellname (oder STL-Basisname als Fallback), optionalem Plattennamen, Materialtyp und Druckzeit. OrkanSlicer übernimmt den resultierenden nativen empfohlenen Dateinamen für Senden/Export bereits über `GCodeProcessorResult.getRecommendedName()`; daher wirkt die Profiländerung unmittelbar auf den Nutzerpfad.

Der instrumentierte OpenEYE-Slice prüft den tatsächlichen empfohlenen Namen: Modellbasis vorhanden, Material `PLA`, nichtleerer Druckzeitanteil, `.gcode`-Endung und keine ungelösten Platzhalter. Zusätzlich müssen Extrusionswege und `START_PRINT` bestehen bleiben. API 35 und API 36 bestanden je 1/1.

Der zweite Nightly-Batch steht damit bei **9/10**. Eine weitere abgeschlossene Änderung vor dem nächsten APK-/Nightly-Prerelease-Build.

## Orca `f273fe8ac` — tatsächlich verwendete Materialtypen im Dateinamen (2026-10-31)

Der Orca-Platzhalter `{printing_filament_types}` wird jetzt im nativen Slice-Export befüllt: verwendete Werkzeug-/Filamenttypen werden in Tool-Reihenfolge gesammelt, doppelte Typen entfernt und mit Unterstrichen verbunden. Er steht sowohl im Placeholder-Kontext für G-Code-Templates als auch in den fertigen Print-Statistiken bereit. Alle 13 OpenEYE-Prozessprofile verwenden ihn nun statt nur den Typ des Startwerkzeugs; Mehrfarben-Dateinamen können dadurch beispielsweise `PLA_PETG` enthalten.

`NativeSliceDeviceTest#recommendedFilenameListsDistinctFilamentTypesActuallyUsedByThePlate` baut ein zusammengesetztes Zweifilamentmodell, setzt für den Slice kontrolliert `PLA` und `PETG`, prüft echte Extrusion und den nativen empfohlenen Dateinamen und wiederholt den Slice mit zweimal `PLA`, um die Deduplizierung nachzuweisen. API 35 und API 36 bestanden je 1/1. ARM64-Native-Build und Instrumentierungs-Kotlin-Kompilierung bestanden ebenfalls.

Damit ist Batch 2 bei **10/10 fokussiert verifizierten Änderungen**. Vor APK und Nightly-Prerelease laufen jetzt noch die gebündelten Debug-/Slice-JVM-Regressionen; danach folgen APK-Identitäts-/Hashprüfung, Installation und Start auf dem verfügbaren Testemulator sowie Veröffentlichung.

## Nightly Batch 4 — 10/10 implementation complete (2026-11-01)

1. Orca `2e64ab3db5` — Beschleunigung der Brim-Gruppierung für zusammengesetzte Objekte mit vielen Volumenteilen. Die Android-Portierung erstellt nach dem Offset pro Layer Begrenzungsboxen und führt Polygonüberschneidungen nur bei überlappenden Boxen aus. Die redundante TBB-Parallelisierung für jedes einzelne Volumenpaar entfällt; der serielle Paar-Scan beendet sich beim ersten Treffer. Das 12-Teile-Instrumentierungsfixture slice't auf API 35 erfolgreich und erhält beide Materialzuweisungen sowie Schicht-G-Code (1/1). Der bestehende Zwei-Teil-3MF-Roundtrip-/Slice-Test und der auf den Slot-Konfigurationsparser verstärkte Auto-for-Match-Test bestanden ebenfalls (je 1/1). ARM64-Native-Build, Android-Instrumentierungs-Kompilierung und `git diff --check` bestanden. Der Android-Lauf ist eine Funktions-/Regressionsprüfung, kein kontrollierter Vorher-Nachher-Leistungstest; ein absoluter Speedup wird nicht behauptet. APK/Release erst nach 10/10.

Der strengere Auto-for-Match-Test baut jetzt synthetische Drucker-Slot-`DynamicPrintConfig`s und ruft `build_machine_filaments()` auf, statt `MachineFilamentInfo` direkt auszufüllen. Das prüft den realen nativen Slot-Parser für `filament_colour`, `filament_type`, `tray_name` und `filament_id`. Es ist Testverstärkung, keine neue Produktfunktion. Der Android-App-Pfad übergibt weiterhin keine geladenen Maschinenfilamente an das native `Print`; deshalb gilt die Orca-Dual-Nozzle-Ladezuordnung nicht als appweit integriert. Orcas Kandidaten 22–24 bleiben bis zu einer passenden, tatsächlich genutzten Druckerroute zurückgestellt.

2. Orca `b1846e3408` — einstellbare obere und untere Farbdurchdringung für gemalte Modellflächen. Die neuen Prozessprofiloptionen `top_color_penetration_layers` und `bottom_color_penetration_layers` reichen bis in die native `PrintRegionConfig` und Mehrmaterial-Segmentierung. Der Standardwert `0` erhält bisheriges Verhalten und folgt der jeweiligen Schalenstärke. Änderungen invalidieren den Slice; beide Optionen sind im vorhandenen Abschnitt „Obere / untere Schichten“ editierbar.

`OrcaProcessSettingsCatalogTest#paintPenetrationControlsAreExposedWithTopAndBottomShellSettings` bestand zusammen mit dem Katalog 10/10. `NativeSliceDeviceTest#paintedTopAndBottomColorPenetrationChangesTheActualSlicedToolpathDepth` bemalt gezielt nur obere bzw. untere Flächen und vergleicht echte G-Code-Werkzeuglayer bei 2 gegen 8 Penetrationslagen; API 35 bestand 1/1. Initial zeigte ein ungenauer Höhenmal-Fixture auch angrenzende Seitenfacetten an; der Test wurde auf Raycast plus coplanaren Flächen-Bucket umgestellt und besteht nun für Ober- und Unterseite. Kein Druckstart.

3. Orca `0ad175c42b` — dichte interne Brücken werden über `diff_expanded()` beschleunigt; bei nicht vollständig dichten unteren Bereichen bleibt die genaue Geometrieberechnung erhalten. API-35-Dichtefixture und API-16-Rückfallfixture bestanden.

4. Orca `e8d35fadd4` — interne Brücken von Hilbert-Curve- und Octagram-Spiral-Infill folgen der tatsächlichen Rotation der tragenden Schicht. API-16-Test maß die resultierenden G-Code-Brückenrichtungen mit 0°/45° Rotation und bestand.

5. Orca `25fd549d17` — mehrliniges Infill verwendet Clipper2-Miter-Verbindungen. Der API-16-Vergleich ergab 11.427 runde gegenüber 7.461 Miter-Sparse-Extrusionsbewegungen; der gezielte Regressionstest bestand 1/1. Commit `dabcf408c7` ist gepusht.

6. Orca `546b9c621d` — 17 Qidi-Q1-/X-CF-Pro-/X-Max-3-/X-Plus-3-/X-Smart-3-Varianten heizen nach dem ausgewählten Plattentyp statt fest nach der High-Temp-Platte. `QidiProfileFilenameTest` bestand 3/3 und prüfte alle betroffenen Startmakros. Der native Q1-Slice auf API 16 verwendete bei gewähltem Textured PEI exakt 71 °C (`PRINT_START BED=71`, `M140 S71`) und nicht die Cool-Plate-Temperatur 31 °C (1/1). Q2/Q2C waren bereits korrekt und blieben unverändert.

7. Orca `a30ca5e54a` — By-Object-Druck ordnet hohe Modelle anhand tatsächlicher Y-Überlappung und Querträger-/Deckelhöhenbedingungen um. Sichere ursprüngliche Reihenfolgen bleiben erhalten; für notwendige Änderungen wird eine Warnung erzeugt, bei unlösbaren Bedingungen bleibt die bestehende Kollisionsvalidierung maßgeblich. Der native G-Code-Test mit absichtlich zuerst gelistetem hohen Modell prüft, dass ein kurzes überlappendes Modell zuerst und das hohe Modell danach ausgegeben wird. API 36 Emulator 1/1 bestanden. Xiaomi 2211133G: Testinstallation durch Android blockiert (`INSTALL_FAILED_USER_RESTRICTED`).

8. Orca `373ffaa13f` — Arc-Fitting-Modus erweitert: aus, überall oder nur Sparse-Infill. Alte boolesche Profilwerte werden vor dem Android-Enum-Fallback migriert, damit vorhandene Profile ihre bisherige Ein/Aus-Wirkung behalten.

Verifikation Änderung 8: ARM64-Native-Build erfolgreich; `NativeSliceDeviceTest#sparseInfillArcFittingDoesNotFitWallsAndLegacyBooleanProfilesRemainCompatible` auf API 36 bestanden (1/1), `NativeEnumValueSanitizerTest` 5/5. Der Test liest den echten G-Code und bestätigt, dass im Sparse-only-Modus ausschließlich `Sparse infill` G2/G3-Bögen ausgibt; alte Werte 1/0 erhalten die vormalige volle Ein/Aus-Wirkung. Der erste Testdurchlauf fand einen Android-Sanitizer-Compatibilitätsfehler, der vor dem erfolgreichen Lauf behoben wurde. Xiaomi-Installation bleibt wegen `INSTALL_FAILED_USER_RESTRICTED` ungeprüft.

9. Orca `01ae2dd49a` — bei monotoner Oberflächenfüllung werden Gap-Fill-Pfade anhand der kürzesten Anschlusswege zwischen Oberflächenlinien einsortiert; Pfadorientierung wird bei Bedarf umgedreht. Der Android-Port bleibt auf `no_sort`-Ausgaben beschränkt und behält die vorherige Reihenfolge als Rückfall, falls greedy Interleaving keinen kürzeren Weg erzeugt. Ein echter API-36-Slice mit Orcas A-Form-Fixture bestätigt unveränderte Oberflächenlinien-Endpunkte und Gap-Fill zwischen benachbarten Oberflächenlinien (1/1).

10. Flow-Ratio-Validierung — verwendete Filamente mit Flow-Ratio `<= 0` oder ungültigem NaN-Wert werden vor G-Code-Erzeugung mit einer verständlichen filamentbezogenen Validierung abgewiesen. Die E-pro-mm-Rechnung verwendet nun direkt den Querschnitt, sodass eine Nullrate nicht mehr den bisherigen `0/0`-Pfad erzeugt. API-36-Instrumentierung bestätigt die verständliche Ablehnung und dass kein NaN-/Overflow-G-Code geschrieben wird (1/1).

Verifikation der Änderungen 9 und 10: ARM64-Native-Build, Slice-Instrumentierungskompilierung und Instrumentierungs-APK erfolgreich; beide gezielten Tests auf dem API-36-Emulator bestanden. Xiaomi 2211133G war erreichbar, Android verweigerte jedoch die Test-APK-Installation erneut mit `INSTALL_FAILED_USER_RESTRICTED`; keine physische Xiaomi-Verifikation behauptet. `git diff --check` sauber. Die vollständigen Debug-/Slice-JVM-Suiten und anschließendes Nightly-APK-/Prerelease-Gate sind noch offen.

## OrcaSlicer 2.5.0 Beta — Portierungsprioritäten

Die offizielle Beta nennt Werkzeugkopf-Kollisionsvolumen, kürzere Travel-Wege, abgerundetes
Sparse-Infill, Sparse-only Arc-Fitting, hochpräzisen G-Code, transparente Filamentvorschau und
schnelleres Slicing. Die Alpha ergänzt Texture Displacement, parametrisches Design, Python-Plugins,
Farbmischung, Belt- und IDEX/IQEX-Druck.

| Priorität | Kandidat | Android-Bewertung | Nächster Nachweis |
|---|---|---|---|
| P1 | Kürzere Travel-Wege | Native Android-Slice-Strecke vorhanden; G-Code ändert sich bewusst und braucht Bewegungs-/Kollisionsregressionen. | Portumfang aus Beta-Commits abgrenzen, dann Vorher-/Nachher-G-Code auf repräsentativen Ein- und Mehrmodellplatten vergleichen. |
| P1 | Werkzeugkopf-Kollisionsvolumen | Hoher Nutzen, benötigt Profildefinition, 3D-Vorschau, Arrange-, Support- und Travel-Integration; nur als vollständige Funktion portieren. | Native Android-Aufrufkette und Datenmodell erfassen; keine Teilportierung nur in Arrange zählen. |
| P2 | Abgerundete Sparse-Infill-Ecken / Sparse-only Arc-Fitting | Native Slice-Funktionen sind portierbar, falls nicht bereits enthalten; beide brauchen G-Code- und Extrusionstests. | Beta-Diff gegen Android-Quelle prüfen und Gyroid/Hilbert/Octagram instrumentiert vergleichen. |
| P2 | Hochpräziser G-Code | Benötigt konsistente XYZ/IJ/E-Formatierung, Rundungsfehlerfortschreibung und Ausnahmen für Support/Turm. | Vollständige Writer-Abhängigkeiten und Firmwareverhalten prüfen, dann Standard-/Präzisions-G-Code vergleichen. |
| P2 | Transparente Filamentvorschau | Android hat einen getrennten GLES/libvgcode-Renderer; möglicher Preview-only-Port. | Blend-/Sortierpfad prototypisch prüfen und Slice/G-Code-Unverändertheit absichern. |
| Zurückgestellt | Texture Displacement | Mesh-Bake, RAM, Abbruch und 3MF-Rundlauf erfordern einen eigenen begrenzten Arbeitsstrang. | Nach den unmittelbar portierbaren Beta-Funktionen. |
| Nicht direkt portieren | Python-Plugins, parametrisches Design, Farbmischung, Belt, IDEX/IQEX | Erfordern neue Ausführungs-, CAD- oder Hardwareverträge; Plugins führen ausführbaren Fremdcode ein. | Als eigenständige Produktziele mit Sicherheits-, Hardware- und UX-Konzept neu bewerten. |

Bereits im Android-Fork vorhandene Beta-Parität (u. a. Paint-Penetration, Infill-Verbindungen,
Qidi-Profile und GL-Preview-Reduktion) wird nicht erneut als neuer Port gezählt.

## Orca-Upstream-Funktionsprüfung nach `v0.8.421-nightly.1` — 2026-10-31

Der Upstream-Zeiger wurde bis `1093ff0714e26ca8d81f3d7fe15d782cc047346f` aktualisiert. Die drei neuen Orca-Änderungen wurden gegen aktive Android-Aufrufpfade geprüft:

- `041df4031` verbessert die Anordnung kurzer Modelle bei „Drucken nach Objekt“. Der native Arrange-Kern enthält bereits die nötigen sequenziellen Druckparameter, aber Androids `Native.bed_arrange` erhält aktuell nur Bett und Modell; die aktive Druckkonfiguration wird nicht übergeben. Kein isolierter Kernpatch wird gezählt. Nächster Schritt für diesen Kandidaten: Konfigurationsübergabe über Anordnen, Duplizieren, Bettfüllen und Import auf dem GL-Thread entwerfen und mit echter By-Object-Platzierungs-/Clearance-Regression testen.
- `c77020e65` behandelt bei Orca unbekannte, aber farblich bekannte Creality-CFS-Spulen. Android besitzt keinen Creality-CFS-Agenten, liest aber ein kompatibles generisches Moonraker-MMU-Schema. Die Anpassung hält ein belegtes Fach mit gültiger Farbe unter seinem physischen Index verfügbar, verwendet sichtbar `Generic PLA` als Orca-Fallback und lässt unbekannte/ungültige Farbe weiterhin durch die bestehende Material-/Uploadwarnung beziehungsweise den Slotfehler absichern. Sie verändert nicht die Zuordnung der übrigen Fächer.
- `1093ff071` repariert Retry-Zustände der Desktop-`PostProcessor`-/Plugin-Pipeline nach Fehlern. Android registriert keine Desktop-Skripte/Plugins und hat keinen entsprechenden Ausführungspfad; der Fix ist aktuell nicht portierbar.

### Nightly Batch 3 — 1/10

1. Unbekannte geladene MMU-Spule sichtbar und indexstabil halten; Farbformen RGB, RGB+Alpha und Creality `0RRGGBB` parsen. Leere und ungültige Slots bleiben nicht auswählbar; Materialabweichungen bleiben im Zuordnungsdialog erkennbar.

`MmuFilamentSyncDeviceTest` bestand 18/18 auf Android API 35 und 18/18 auf API 36. Abgedeckt sind Slotindex, Farbe, Verfügbarkeit, `Generic PLA`-Fallback, Materialabweichung, leere Fächer und ungültige Farben. Debug-Unit und Slice-Unit bestanden jeweils 705/705 (0 failures, errors oder skips). `git diff --check` bestanden. Keine physische Creality-/Moonraker-Hardware wurde für dieses Paket getestet. Der APK-/Release-Gate wird erst bei 10/10 erreicht.

## Orca `041df4031` — By-Object-Clearance in Android-Anordnung (2026-10-31)

Die Android-Anordnung übergibt jetzt die aktuelle Druckkonfiguration über `Bed3D` und JNI an den nativen Arrange-Kern, während die Build-Volume-Geometrie weiter aus dem aktiven Druckbett stammt. Für By-Object werden Instanzpolygone mit Orcas konfigurationsbewusster `get_instance_arrange_poly`-Routine aufgebaut. Das ist wichtig, weil der generische Android-Helfer keine Instanzhöhe lieferte und Orcas Arrange-Logik damit alle Objekte fälschlich als kurz einstufte. Nun können aktive Düsen-/Gantry-Clearance, Objekt-/Skirt-Abstand und Sequenzmodus sicher in die Platzierung einfließen. By-Layer behält den bisherigen 6-mm-Abstand.

`byObjectArrangeUsesActiveClearanceAndKeepsObjectsApart` setzt ein isoliertes 20-mm-Testmodell, By-Object, 0,1-mm-Düsenhöhe und 30-mm-Clearance und prüft, dass die zwei Modelle nach echter nativer Anordnung mindestens 29,5 mm Abstand haben. Der bestehende `modelMenuArrangeBringsOffBedObjectBackIntoWorkspace` deckt den By-Layer-Weg einschließlich Undo/Redo und Workspace-Wiederherstellung ab. Beide Tests bestanden auf Android API 35 und API 36 (je 1/1). Debug- und Slice-Unit-Suiten jeweils 705/705 ohne Fehler oder Skips; ARM64-Native-Build und `git diff --check` bestanden.

Batch 3 steht jetzt bei **2/10**. Die Änderung ist noch lokal, nicht committet oder gepusht. APK/Prerelease wird erst nach zehn fokussiert verifizierten Änderungen erstellt.

## Orca `43c76f1f5` — Layer-Pausen bleiben in der korrekten Vorschau-Schicht (2026-10-31)

Beim Verarbeiten einer Pause flusht der G-Code-Prozessor den Zeitplan und fügt gegebenenfalls Actual-Speed-Moves in die Move-Liste ein. Dadurch konnte der gespeicherte Index des Pause-/Farbmarkers veralten; die Vorschau ordnete danach Moves der falschen Layer-ID zu und konnte die Layerreihenfolge verletzen. Androids native `GCodeProcessor`-Port führt den Markerindex nun nach Move-Einfügungen nach. Beim Entfernen des Markers werden noch wartende Zeitblöcke ebenfalls angepasst. Der erzeugte G-Code und die Druckparameter werden nicht verändert.

`GCodePauseLayerPreviewDeviceTest#pauseAfterTimeFlushRemainsOnItsOwnLayerAndPreviewLayersStayOrdered` verarbeitet einen dreischichtigen G-Code mit Rückzug, Pause und Extrusion auf der Folgeschicht, lädt die Moves in libvgcode unter einem GLES3-Kontext und prüft, dass die Vorschau bei drei fortlaufenden Schichten bleibt. API-35- und API-36-Emulatoren bestanden je 1/1; ARM64-Debug-Build erfolgreich. Die vollständigen JVM-Suiten werden am Batch-Gate erneut ausgeführt.

Nightly-Batch 3: **3/10**. Kein APK/Release vor 10/10 fokussiert verifizierten Änderungen.

## Nightly Batch 5 — Änderung 6/10: exakte kurze Verfahrwegketten (2026-11-01)

Orca `69193d5681` enthält exakte dynamische Programmierung für kurze Extrusionspfadketten. Android minimiert jetzt die Verfahrdistanz für Gruppen mit bis zu acht Segmenten, wenn ein fester Startpunkt existiert. Das gilt für die aktive Kettenbildung der Extrusionspfade und für die zweite Greedy-Kettenvariante der Infill-Polylinien. Bei fehlendem Startpunkt bleibt das bisherige Greedy-Verhalten erhalten. Lange Kettenverfeinerung, geschlossene Objekt-/Insel-Touren und verbundene Infill-Linien aus demselben Orca-Commit sind **nicht** übernommen.

`NativeSliceDeviceTest#shortExtrusionChainsUseTheMinimumTravelOrder` prüft den produktiven nativen Pfad mit einer Vier-Segment-Geometrie. Das Ergebnis stimmt mit einer vollständigen Enumeration aller Reihenfolgen und erlaubten Orientierungen überein und ist kürzer als eine einfache Nearest-Neighbor-Kette. ARM64-SliceTest-Native-Build und APK-Paketierung bestanden; gezielter Test 1/1 auf API-36-Emulator. Xiaomi 2211133G blieb wach und per ADB online, doch MIUI verweigerte die Testinstallation mit `INSTALL_FAILED_USER_RESTRICTED`; kein physischer Xiaomi-Testpass. Ein vollständiger Instrumentierungslauf wurde wegen eines bestehenden Release-Notes-Testfehlers und der Geräteinstallationssperre nicht als bestanden gewertet.

Batch 5 steht bei **6/10 fokussiert verifizierten Produktänderungen**. Es wurde kein APK/Release aus diesem Teilbatch erstellt.

## Nightly Batch 5 — Änderung 7/10: exakte Default-Objekttour (2026-11-01)

Für `PrintOrder::Default` wird bei bis zu zehn Objektinstanzen die Reihenfolge nun als exakt minimale geschlossene Tour optimiert. Das nutzt Orcas TSP-Dynamic-Programming aus `69193d5681`; bei mehr als zehn Instanzen bleibt die vorhandene Kettenheuristik aktiv. Der By-Object-Pfad mit übergebenem Düsen-/Wipe-Tower-Startpunkt bleibt unverändert.

`NativeSliceDeviceTest#defaultObjectOrderUsesTheMinimumClosedTourForSmallProjects` ruft den Tour-DP mit sechs Objektmittelpunkten auf und vergleicht die geschlossene Distanz gegen vollständige Enumeration aller 120 Touren sowie eine Nearest-Neighbor-Tour. ARM64-SliceTest-Native-Build, Test-APK-Paketierung und direkter API-36-Emulatorlauf bestanden (1/1). Der Xiaomi 2211133G war per ADB online und auf `stay_on_while_plugged_in=15`, aber MIUI verweigerte erneut die Testinstallation (`INSTALL_FAILED_USER_RESTRICTED`); kein physischer Gerätetest.

Batch 5 steht bei **7/10 fokussiert verifizierten Produktänderungen**. Kein APK/Release aus diesem Teilbatch.

## Batch 11 Fortsetzung — Status 2026-11-04

Punkte 9 und 10 sind jetzt auch zur Laufzeit verifiziert. Orca `2bd868ecd0` vermeidet unnötige Kopien der `PrintConfig` an drei Slicing-Hotspots (Layer-/Region-Pfadvereinfachung und Tree-Support-Trimmung). Orca `6aa613ccf0` behebt Z-Konturierung für mehrere Instanzen eines PrintObjects.

Auf dem Android-16/API-36-x86_64-AVD wurden ARM64-Slice-Tests über die vorhandene `libndk_translation`-Native-Bridge ausgeführt: `wipeInwardChangesOnlyTheGeneratedWipePathAndKeepsGcodeValid`, `buildPlateOnlyTreeSupportsSliceWithConfiguredObjectClearance` und `zContouringSlicesMultipleInstancesOfOneObject` jeweils 1/1 bestanden. Damit ist der zuvor angenommene Geräteblocker widerlegt.

Gebündelte JVM-Regression auf HEAD `f0fb07af9fbd36f55ebc70bdf16c3b45d21e5f5c`: Debug 749/749 und Slice 749/749, jeweils ohne Fehler/Errors/Skips. Die ACE-Routingklassen `SparseAceRoutingOutputTest`, `MmuColorAssignmentPolicyTest` und `SlicePreparationToolsTest` bestehen ebenfalls; darunter der direkte Nachweis Slicer-T0 → ACE-Fach 2 → ausgegebener T1-Befehl. Batch 11 ist damit **10/10 verifiziert**. APK und Nightly-Prerelease stehen noch aus und bilden den nächsten Release-Gate-Schritt.

## Nightly Batch 12 — Punkt 1/10 — Orca-Infill-Verbindungsoptimierung

Portiert wurde Orca `69193d5681` für die native Android-Funktion `Fill::connect_infill`: multiline-bewusste Join-Distanz, deterministische Sortierung gleich langer Konturverbindungen nach erwarteter Zahl zusammenhängender Infill-Pfade und Hook-Platzierung zum nächsten offenen Pfadende. Bei mehrlinigen Füllmustern wird der Hook vor der Nachbarkopie gekürzt, damit keine unbeabsichtigte Überdeckung entsteht.

Verifikation: ARM64-Native-Build erfolgreich; API-36-Emulator `multilineGridInfillUsesMiterJoinsAndPreservesGeometry` 1/1 und `perPartProcessOverridesRemainIsolatedAcrossThreeMfAndNativeSlice` 1/1 bestanden. Multiline-Grid-Fixture: 7.547 reproduzierbare Sparse-Infill-Bewegungen; geprüfte X/Y-Koordinaten liegen innerhalb der 40×30-mm-Modellgrenzen. Debug-Unit und Slice-Unit jeweils 749/749 bestanden; keine Fehler/Skips. Commit `8ab43b6ed5` ist auf `nightly25` gepusht. Batch 12: **1/10**.

Der weitere Vergleich am 2026-11-04 prüfte Orca `5fd0edf5cc` (Z-Schrumpfung von Höhenbereichen) und `59f0cc3806` (leere Layer-Serie bei dünnem Objekt mit Raft). Beide relevanten Standardpfade sind bereits portiert; der verbleibende Belt-Printer-Zweig aus `5fd0edf5cc` ist nicht als Android-Funktion zählbar, da Nightly keinen unterstützten Belt-Printer-Profil-/Slicingpfad besitzt. Ein isolierter Testversuch mit synthetischem `belt_printer`-Key war damit kein gültiger Produktpfad und wurde entfernt. Diese Kandidaten erhöhen Batch 12 nicht.
