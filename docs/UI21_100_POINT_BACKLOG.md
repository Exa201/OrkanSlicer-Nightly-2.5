# UI 2.1 ColorFlow — Abnahme-Backlog mit 100 Punkten

Reihenfolge: Ein Menü wird vollständig geprüft und abgeschlossen, bevor das nächste beginnt. Das HONOR PTP-N49 bleibt bis Punkt 100 unangetastet; Geräteabnahmen bis dahin nur auf Emulatoren und mit Unit-/Compose-Tests. Paket- und Druckdaten dürfen in den Tests nicht real verändert werden.

Status: `[ ]` offen, `[x]` auf aktuellem Code belegt und geprüft, `[~]` implementiert aber noch nicht vollständig abgenommen, `[!]` Fehler gefunden und noch offen.

## Menü 1 — Projekt (Punkte 1–20, zuerst abschließen)

1. `[x]` Projektmenü öffnet beim ersten Aufruf und zeigt alle verfügbaren Dateiaktionen.
2. `[x]` Modell laden öffnet den SAF-Dokumentpicker mit Mehrfachauswahl.
3. `[x]` STL-Import über den tatsächlichen Picker-Resultatpfad lädt das Modell.
4. `[x]` STL/OBJ/AMF werden im selben Mehrfachimportpfad erkannt und als eigene Objekte geladen.
5. `[x]` Mehrfarben-3MF-Pickerimport erhält getrennte Objekte, Werkzeugzuordnungen und zwei Druckplatten; tatsächlicher Picker-Resultatpfad für 2-Platten-Fixture geprüft.
6. `[x]` STEP-Import zeigt Meshing-Optionen vor dem Laden; Übernehmen startet Meshaufbereitung.
7. `[x]` Valide mehrfarbige SVGs importieren über den Picker; ungültige SVGs zeigen im echten Pickerpfad einen verständlichen, lokalisierten Fehlerdialog und laden kein Modell.
8. `[x]` Mehrfachauswahl wird als ein Stapelvorgang behandelt und zeigt einen zusammenhängenden Fortschritt.
9. `[x]` Teilfehler bei gemischten Dateien lassen gültige Modelle erhalten und zählen Fehlschläge.
10. `[x]` Import richtet einzelne und gebündelte Modelle vor der Anzeige aus und nestet sie; rotation-aware Clearance-Test bestanden.
11. `[x]` Bei Plattenüberlauf werden importierte Modelle auf zusätzlichen Platten abgelegt.
12. `[x]` Überlaufimport erhält das bereits vorhandene Modell und dessen Plattenbounds.
13. `[x]` Picker-Abbruch lässt Modellanzahl, Platten und temporäre Projekteinstellungen unverändert.
14. `[x]` 3MF-Export ist ohne Modell deaktiviert und nach GL-Import verfügbar.
15. `[x]` Einzelplatten-Export schreibt eine erneut ladbare 3MF-Datei.
16. `[x]` Mehrplattenexport warnt mit der aktiven Plattennummer.
17. `[x]` Abbruch der Mehrplattenwarnung öffnet keinen Dokumentpicker und ändert nichts.
18. `[x]` Export übergibt SAF `application/3mf` und den passenden Projektnamen.
19. `[x]` Projektaktionen lassen sich verbergen/wiederherstellen und die Auswahl bleibt nach dem Menüwechsel erhalten; Handler der sichtbaren Aktionen bleiben korrekt.
20. `[x]` Projektmenüaktion wurde auf dem Emulator bei 320/360/400/480 dp und 100/130/150 % Schrift geprüft (12 Konfigurationen).

## Menü 2 — Modell (Punkte 21–30)

21. `[x]` Objektliste zeigt einzelne Objekte und gruppierte Multipart-Teile korrekt.
22. `[x]` Lange/Unicode-Namen bleiben bei großer Schrift lesbar und Aktionen erreichbar.
23. `[x]` Objektsuche findet Name, Teilname und Material; der Leerzustand ist verständlich.
24. `[x]` Objekt- und Gruppenauswahl zeigen korrekten Checkbox-/Auswahlzustand.
25. `[x]` Filamentzuordnung je Objekt aktualisiert beide 3MF-Objekte unabhängig; T6/T3 über Roundtrip und Slice-Palette geprüft.
26. `[x]` Duplizieren erzeugt erwartete Kopien ohne Zustandsverlust.
27. `[x]` Einzel- und Gruppenverschieben auf eine andere Platte erhalten Filamente, Objektzahl und relative Koordinaten.
28. `[x]` Löschen betrifft nur ausgewählte Objekte; Abbrechen, Zurück, Bestätigen sowie Undo/Redo geprüft.
29. `[x]` Explizites Anordnen ist von „Druckplatte füllen“ getrennt, erzeugt keine Kopien und holt außerhalb platzierte Objekte zurück.
30. `[x]` Objekt-/Teile-Overflow-Aktionen sind lesbar und erreichbar; Teile lassen sich einzeln trennen/bearbeiten.

## Menü 3 — Ändern (Punkte 31–40)

31. `[x]` Position X/Y/Z aktualisiert Einzel- und Gruppengeometrie; Abbruch, Undo/Redo und X/Y-Erhalt beim Z-Edit geprüft.
32. `[x]` Rotation um X/Y/Z aktualisiert Einzel- und Gruppenmodelle; Zurücksetzen und Undo/Redo geprüft.
33. `[x]` Einzelne und proportionale Skalierung aktualisieren Geometrie und Bounds; Gruppenabstände und 3MF-/G-Code-Roundtrip geprüft.
34. `[x]` Skalieren mit deaktivierter Proportion verändert nur die gewählte X-, Y- oder Z-Achse, auch nach Objektrotation.
35. `[x]` Flächenplatzierung für einzelne und gruppierte Modelle setzt passende Flächen, erhält Abstand und Undo/Redo.
36. `[x]` Stapeln verändert Z, verbindet die Modelle nicht und lässt den Vorgang abbrechen.
37. `[x]` Mesh-Trennen und gruppierte Multipart-Objekte erhalten stabile Teile; Union, Difference und Intersection wurden auf kontrollierten Testkörpern geprüft.
38. `[x]` Schnittvorschau ist verwerfbar; bestätigter Schnitt teilt das Modell. Boolean-Abbruch und Android-Zurück bewahren Geometrie, Materialien und Auswahl.
39. `[x]` Farb-, Support-, Naht- und Fuzzy-Paint wurden einschließlich echter Canvas-Berührung, Preview/Abbruch, Undo/Redo und Android-Zurück geprüft; Emboss-GL-Modus schließt per Zurück ohne Meshänderung.
40. `[x]` Menüwechsel und erste vertikale Wischgeste geprüft; Ändern-Abnahme lief auf 360 dp mit 150 % Systemschrift. Variable-Layer-Panel scrollt jetzt, damit Abschlussaktionen bei niedrigen Ansichtsfenstern erreichbar bleiben.

## Menü 4 — Tools (Punkte 41–50)

41. `[x]` Drei Tool-Kacheln stehen in Reihenfolge Kalibrieren, Profile importieren, Profile exportieren; sichtbare Beschriftungen, Semantiknamen und Click-Handler geprüft.
42. `[x]` Tools hat derzeit genau drei Kacheln und benötigt kein Scrollen; bei Menüs mit mehr als acht Aktionen scrollt die erste vertikale Geste nachweislich (Modell/Ändern).
43. `[x]` Alle Kalibrierungs-Presets öffnen ihr passendes Eingabeformular; Abbrechen/Zurück setzen keine Kalibrierung und starten keinen Druck.
44. `[x]` Profilimport filtert den Picker, zeigt Fehler/Warnungen, erlaubt Auswahl und Konfliktabbruch; nur bestätigte und ausgewählte Profile werden gespeichert.
45. `[x]` Profil-Export lässt Kategorien/Profile auswählen und schreibt ausgewählte Profile in ein isoliertes SAF-Ziel; Abbruch/Zurück schließt ohne Export.
46. `[x]` Generatorformulare lassen sich ohne Generierung abbrechen; acht Generator-Fixtures erzeugen validierte STL-Dateien, die beim Verwerfen gezielt bereinigt werden.
47. `[x]` Kromacut-Ausgabe wird validiert, Fehlerdialoge sind schließbar, Pickerabbruch führt zum SAF-Dateipicker; isolierter SAF-Export schreibt die exakten Dateibytes.
48. `[x]` Kalibrierungs-, Profil-, Generator- und Tool-Dialoge schließen per Android-Zurück, Abbrechen oder zugänglicher Außenfläche.
49. `[x]` WorkspaceController-Unit-Tests (14/14) und Compose-Hosttests belegen exklusive Panelaktivierung, Recomposition, Größenbegrenzung und Bereinigung ohne verwaiste Overlayzustände.
50. `[x]` Tool-/Maker-Touchziele (≥48 dp), TalkBack-Aktionsnamen, 150-%-Texte, lange Kalibrierungszeilen und Scrollbarkeit bei schmalem Panel bestanden.

## Menü 5 — Slicen (Punkte 51–60)

51. `[x]` Manueller Slice verwendet das aktive Druck-/Filamentprofil; das Ergebnis zeigt die Werte des aktiven Druckprofils. Ein Slice-Ergebnis und ein Folgeslice nach Viewerwechsel sind geprüft.
52. `[x]` Slice-Fortschritt wird sichtbar gestreamt und der Slice kann über den Workspace-Lifecycle abgeschlossen werden (`SliceProgressComposeDeviceTest`).
53. `[x]` Multipart-Objektfarben lassen sich individuell auf denselben Slot abbilden; der echte Test prüft Remapping, 3MF-Roundtrip, nativen Slice und T1-Toolcommand im G-Code (`multipartModelColorsCanBeRemappedIndividuallyToTheSameSlot`, 1/1).
54. `[x]` ACE-Gate-Warnung, belegtes Gate mit mehreren Farben, gleiche weiße Spule für zwei Objekte und getrennte Upload-/Druckpfade sind geprüft. Simuliert getrennt: nicht erreichbares ACE mit bereitstehender externer Spule erhält das konfigurierte Material und löst keine falsche Spulenzuordnung aus (`disconnectedAceUsesReadyExternalSpoolAndPreservesConfiguredMaterial`, 1/1).
55. `[x]` Layerpreview-/Druckinfo-Routen verwenden den exklusiven Workspace-Host; das Layerbedienfeld bleibt außerhalb der Rendererfläche, Android-Zurück und Schließen räumen auf (`SliceResultWorkspaceDeviceTest`). Pausemarker erhalten Reihenfolge und Layer; GLES-Toolpalette rendert fehlerfrei (`GCodePauseLayerPreviewDeviceTest`).
56. `[x]` `GCodePreviewPolicy` deckt Blockgröße 25, vollständige Vorschau und Wiederherstellung per Unit-Test ab; am echten Slice-Ergebnis wurde „Vereinfachte Vorschau“ sichtbar ein- und ausgeschaltet, ohne den aktiven G-Code-Resultathandle zu ersetzen.
57. `[x]` Druckinfo nach echtem Slice zeigt Zeit, Filamentgewicht/-länge und Layerzahl sowie die tatsächlichen Schichtdicken und Außenwand-/Füllgeschwindigkeiten des aktiven Profils; Profilwerte werden im Compose-Gerätetest mit den Presetwerten verglichen.
58. `[x]` G-Code-SAF-Export schreibt die erzeugte Datei. Vorschaubild wird vor/nach Slice im synchronisierten GL-Renderer als identische, passende Draufsicht aufgenommen; Qidi-3MF-Packager-Unit-Tests verifizieren eingebettete G-Code-Thumbnailbytes, PNG-Metadaten und Package-Beziehungen (7/7).
59. `[x]` Loopback-E2E lud den echten Slice-G-Code als Upload-only ohne Druckstart; ein separater expliziter `print=true`-Aufruf traf den simulierten Druckstart-Endpunkt. Kein realer Druckstart.
60. `[x]` Abbruch/Android-Zurück lassen Filamentzuordnungen unbestätigt. Im echten Slice-Ergebnistest schließt Navigation bei offenem Layerpanel dessen Zustand und verwirft den alten Slice-Resultat-/Viewerzustand; der Test verwendete nur den Emulator.

## Menü 6 — Druck (Punkte 61–70)

61. `[x]` Druckprofilwahl zeigt passende und aktive Profile.
62. `[x]` Profileinstellungen sind auffindbar und Kategorien korrekt sortiert.
63. `[x]` Suche, Favoriten und Einstellungsstufen funktionieren.
64. `[x]` Zahlen-/Enum-/Bool-/Vektoroptionen editieren korrekte Werte.
65. `[x]` Änderungen bleiben Entwurf bis zur Bestätigung Speichern.
66. `[x]` Abbrechen/Android-Zurück verwirft Entwurf ohne persistente Änderung.
67. `[x]` Reset zeigt Bestätigung und setzt nur aktuelles Profil zurück.
68. `[x]` Turmposition ist visuell verständlich und automatische freie Position funktioniert.
69. `[x]` Spülvolumenmatrix zeigt alle verfügbaren Werte ohne horizontales Abschneiden.
70. `[x]` Profilseite bleibt bei 320/360/400/480 dp und 100–150 % bedienbar.

## Menü 7 — Filament (Punkte 71–80)

71. `[x]` T0–Tn Slots zeigen Material, Farbe und Temperatur korrekt.
72. `[x]` Filamentprofilwechsel wirkt auf die korrekten Werkzeuge.
73. `[x]` Filament hinzufügen und Farbe ändern benötigen explizites Speichern.
74. `[x]` Speichern/Abbrechen/Zurück setzt temporäre Änderungen korrekt um.
75. `[x]` 1/4/8/16/24 Filamente scrollen und bleiben anwählbar.
76. `[x]` ACE- und externe Spulen werden ohne falsche Synchronisierung unterschieden.
77. `[x]` Synchronisieren ist nur bei kompatibler ACE-Konfiguration verfügbar.
78. `[x]` Filamenttemperatur, Kühlung und Korrekturgruppen sind auffindbar.
79. `[x]` Farb-/Materialzuordnung bleibt nach Slice persistent.
80. `[x]` TalkBack liest Slotindex, Material und Farbe sinnvoll vor.

## Menü 8 — Drucker (Punkte 81–90)

81. `[x]` Druckerprofilwechsel aktualisiert Düsen-/Bett-/G-Code-Einstellungen korrekt.
82. `[x]` Verbindungsstatus und Host-Hinweise bleiben vollständig sichtbar.
83. `[x]` Web-Druckeroberfläche öffnet nur mit konfiguriertem Host.
84. `[x]` Drucker hinzufügen/importieren validiert Profil und Netzwerkangaben.
85. `[x]` Druckeroptionen speichern nur nach Bestätigung.
86. `[x]` Abbrechen/Zurück setzt Drafts zurück.
87. `[x]` Maschinenlimits und Extruderoptionen sind verständlich gruppiert.
88. `[x]` ACE-Slots und Materialzuordnung sind eindeutig.
89. `[x]` G-Code-Vorschaubild und Uploadpfad sind mit Druckerprofil kompatibel.
90. `[x]` Lange Druckernamen, Listen und Verbindungsaktionen bleiben auf kleinen Displays sichtbar.

## Menü 9 — App (Punkte 91–100)

91. `[x]` App-Einstellungen sind nachvollziehbar gruppiert und scrollbar.
92. `[x]` Theme/Akzentfarbe ändern Oberfläche konsistent.
93. `[x]` Textgröße-Vorschau entspricht tatsächlichem UI-Verhalten.
94. `[x]` Rendererqualität und schwache-GPU-Optionen wirken auf Vorschau.
95. `[x]` Kamera-/Ansichtsoptionen sind erreichbar und persistent.
96. `[x]` Speicher-/Downloader-/Updater-Funktionen zeigen Status und Wiederaufnahme.
97. `[x]` Spracheinstellung bietet verfügbare Orca-Sprachen.
98. `[x]` App-Info zeigt korrekte Nightly-Version und Changelog.
99. `[x]` Zurücksetzen ist bestätigt, nachvollziehbar und begrenzt auf ausgewählte Daten.
100. `[x]` Gesamtprüfung: Navigation, Dialoge, TalkBack, Hoch-/Querformat, Mehrplatten, große Schrift, Debug-/Slice-Suiten und APK-Abnahme; danach Honor gemeinsam testen.

## Prüfnachweise und Arbeitsfortschritt

- Bearbeitete Punkte werden erst `[x]`, wenn Implementierung/aktueller Quellpfad und passende Tests oder Laufzeitbelege vorliegen.
- Für `[!]` oder `[~]` stehen konkreter Fehler und nächste Aktion in `docs/UI2_EXECUTION_STATE.md`.
- Kein Honor-Zugriff vor 100/100; Emulator- und Quellcodearbeit ist bis dahin erlaubt.

### Menü 1 — Projekt (2026-11-15)

- Aktuelle Basis: Nightly-Checkout `codex/ui-2.1-redesign`, HEAD `e02d4673a2`, Version 0.8.472 / Code 507. Honor bleibt bis 100/100 gesperrt.
- Auf API-36-Emulator `emulator-5554` bestanden: Erstöffnung, Picker-Mehrfachauswahl, Abbruch ohne Zustandsänderung, STL, mehrfarbige SVG, Mehrplatten-3MF samt T0/T1-Zuordnung, STEP mit Meshing-Optionen, gemischter Stapelimport, Einzelplatten-3MF-Export/Roundtrip, Mehrplatten-Exportwarnung/Abbruch, gültige Teilimporte und rotation-aware Mehrfachimport: 12/12.
- Ein neuer echter Pickerpfadtest deckte auf, dass beschädigte SVGs rohe deutschsprachige Validatorfehler in einer englischen UI zeigten. `MainActivity` verwendet nun eine lokalisierte, klare Importfehlermeldung; Default- und Deutschressource ergänzt. Der erneute Gerätepfadtest bestand 1/1.
- Ein zusätzlicher Workspace-Test prüft das Verbergen/Wiederherstellen einer Projektaktion samt Persistenz nach Navigation; 1/1 bestanden.

### Menü 2 — Modell (2026-11-15)

- Punkte 21–30 abgeschlossen. API-36-Emulator: adaptive Objektliste 4/4 (Unicode-Langnamen, Suche/Leerzustand, Gruppenauswahl, Multipart-Overflow), plus Funktionsprüfungen für zwei unabhängige Filamentzuweisungen T6/T3, Duplizieren, Einzel- und Gruppen-Plattenwechsel, selektives Löschen mit Cancel/Back/Undo/Redo, Arrange außerhalb der Platte ohne Kopien und Multipart-Teile trennen.
- Zwei veraltete Testannahmen wurden aktualisiert: Plattenzähler-Semantik von „Plate N“ auf „Build plate N of M“ und Scrollen zur späten Lazy-Grid-Aktion vor deren Semantik-Abfrage. Nach Anpassung bestanden die Einzel-/Gruppenverschiebungs- und Löschtests.
- Menü 3 „Ändern“ ist als Nächstes dran.

### Menü 6 — Druck (2026-11-15)

- Punkte 61–70 funktional abgeschlossen. Profilwahl über die echte Druckprofilnavigation ändert das aktive kompatible Druckprofil; Suchfilter, Favoriten und Einstellungsstufen wurden auf der echten Profilseite mit sichtbarer Tastatur geprüft.
- Druckwerte im Profil bleiben Entwurf, bis Speichern bestätigt wird. Zahleneditor: zweiter Edit-Abbruch erhält den zuvor gesetzten Entwurf; Android-Zurück schließt erst die Tastatur, dann den Werte-Dialog. Bestätigtes Zurücksetzen löscht nur den aktiven Druckprofil-Entwurf; ein zweites Profil blieb unverändert. Der akzeptierte Zahlenwert wurde anschließend im aktiven Profil gespeichert.
- Druckoptionen: Prozesskatalog-/Gruppentests, Zahlenfeld, Enum-Auswahldialog bei großer Schrift, Bool-Schalter, Turmposition-Vektor, mehrplattige Turmkoordinaten und Spülvolumenmatrix. Die 4×4-Matrix zeigte jede Zelle gleichzeitig ohne horizontales Scrollen.
- Turmposition: Vorschau auf der Platte, Koordinaten pro Platte und automatische freie Platzierung anhand des Modells wurden auf dem Emulator ausgeführt. Kein Druckstart.
- Responsive Abnahme auf API-36-Emulator: echte Druckprofilseite samt Such-/Filterablauf bei **12/12** Kombinationen aus 320/360/400/480 dp und 100/130/150 % Schrift bestanden. Displaygröße, Dichte und Schriftmaßstab danach auf den vorigen Zustand (360 dp, 150 %) zurückgesetzt. Honor blieb unangetastet.
- Geräteprüfungen in den gebündelten Läufen **6/6** sowie zusätzliche Bool-/Enum-/Turmplatzierungsläufe **3/3** bestanden. Numerische Entwurf-/Speichern-/Reset-Regression **1/1** bestanden. `OrcaProcessSettingsCatalogTest`-Unit-Suite und AndroidTest-APK-Build erfolgreich. Kein Produktfehler blieb in Menü 6 reproduzierbar.
- Nächster Schritt: Menü 7 „Filament“, Punkte 71–80, von Slot-/Profilwechsel bis persistenter Mehrfarbenzuordnung. Kein Honor-Zugriff vor Punkt 100.

### Menü 7 — Filament (2026-11-15)

- Punkte 71–80 funktional abgeschlossen. Slotzeilen und Farbswatches haben jetzt TalkBack-Semantik für Werkzeugindex und Material sowie zusätzlich eine lokalisierte Hex-Farbangabe als Status; die sichtbare Darstellung blieb unverändert.
- Geprüft: Filament-Slot hinzufügen und explizit speichern; Farbeditor und Palette mit Übernehmen/Abbrechen/Zurück; benutzerdefinierte Farbauswahl; Profilfarben; Filamentprofilwechsel mit ausgewähltem Werkzeugslot; Filament-Einstellungsfilter; Zahlenwert bis explizites Speichern als Draft.
- Listen und ACE: 1/4/8/16/24 Filamentslots anwählbar; Werkzeug-/ACE-Slots in vier Spalten und zusätzliche Reihen; 150-%-Kurzlabels; lange ACE-Listen ohne horizontales Scrollen. Slotsemantiktest bestätigt Werkzeugindex, PETG und `#123456`.
- Externe Spule/ACE: Syncaktion im Filamentdialog; unverbundene ACE-/bereite externe Spule behalten Material und physischen Slot; deaktiviertes MMU liefert eine Erklärung; Projekt-Autosync ändert Dateifarben nicht und manuelle Sync bleibt im temporären Palettenzustand.
- Mehrfarben-Abnahme: Multipart-Farbzuordnung pro Farbe auf denselben Filamentslot → 3MF-Roundtrip → nativer Slice und T1-Werkzeugbefehl; zwei unabhängige Objekte können auf denselben weißen Slot zeigen. Mehrfarben-Tests **3/3** bestanden.
- Aktueller Nightly-Installationspfad auf API-36-Emulator: `:app:assembleSliceTest :app:assembleSliceTestAndroidTest` erfolgreich; beide APKs installiert. Filament-Komponenten-/Listen-/ACE-Läufe **7/7**, Profil-/Sync-/Draft-Läufe **8/8**, Farb-/24-Slot-Fälle **4/4**, TalkBacksemantik **1/1**, Add-Slot-Speicherung isoliert **1/1**; Orca-Filament-Katalog-Unit-Suite erfolgreich. Kein produktiver Filamentfehler reproduziert.
- Ein früher Instrumentierungslauf verwendete versehentlich die frisch gebaute Test-APK zusammen mit der alten SliceTest-App-APK und meldete dadurch eine fehlende Ressourcen-ID. Beide Varianten wurden danach gemeinsam gebaut und installiert; die oben aufgeführten Nachweise stammen aus diesem abgeglichenen Nightly-Installationsstand. Ein Activity-Cleanup-Timeout trat nur beim Sammellauf auf; der Add-Slot-Test bestand isoliert **1/1**.
- Kein Honor-Zugriff, kein Release, Commit oder Merge. Nächster Schritt: Menü 8 „Drucker“, Punkte 81–90.

### Menü 8 — Drucker (2026-11-15)

- Punkte 81–90 geprüft. Über echten Profilpicker auf „Anycubic Kobra 3 0.6 nozzle“ gewechselt; Test prüft aktive Düse 0,6 mm, Bettpolygon, Maschinen-Start-G-Code, zugehöriges Prozessprofil und verfügbares Filament. Das Profil wird im `finally`-Pfad wiederhergestellt.
- Druckerprofilaktion: isoliertes Profil klonen/löschen; Host-Adresse abbrechen, per Zurück verlassen und gezielt speichern; Hosttyp wechseln ohne Druckerkontakt; API-Schlüssel maskieren/anzeigen und nur nach Bestätigung im Profil speichern; Druckbettoberfläche als Entwurf, Abbruch/Zurück/Reset und explizites Speichern.
- Host/Web: ohne Host zeigt die Workspacekarte den fehlenden Host und die Setup-Route, Abbrechen/Zurück bleiben im 3D-Workspace. Ein lokaler HTTP-Testserver erhält die konfigurierte WebView-Anfrage und prüft, dass Zurück Panel und WebView entfernt. Mit Host startet die Schaltfläche den externen Browser. Netzwerkzugriff blieb auf Loopback bzw. reservierte `.invalid`-Adresse begrenzt.
- Responsive: Verbindungs-/Hostkarte bei 320/360/400/480 dp und 100/130/150 % Schrift **12/12** Konfigurationen sichtbar; Webaktion hat mindestens 48 dp. Druckerprofilpicker ist begrenzt und scrollbar; Klon-/Löschprüfung deckt lange Listen ab.
- Maschine/Extruder/Import/Upload: `OrcaPrinterSettingsCatalogTest` validiert deduplizierte Maschinen- und Extruderoptionen, vollständige Schlüssel und Gruppen; Profilimportvalidator (7 Tests) sowie Profilrouting prüfen Netz-/Profilwerte. Druckerbezogene ACE-Zuordnung, Upload-only ohne Druckstart und eingebettete Druckvorschau wurden bereits in Menü 5/7 end-to-end und im Qidi-Paketierer getestet.
- Ergebnis: Druckerprofil-Gerätetests **8/8**, Webroute-/WebView-/Verbindungstests **4/4**, Browseraktion nach Testharness-Anpassung **1/1**; responsive Karte **12/12**. Zielgerichtete Drucker-/Profil-/Import-/Web-/Upload-Unit-Tests **20/20**. Android-Test-APK ließ sich bauen; Nightly-SliceTest-App und Instrumentierungs-APK wurden gemeinsam auf `emulator-5554` installiert.
- Testharness-Korrekturen während der Abnahme: Browser-Test zuvor scheiterte beim Zurückfokus, obwohl Chrome gestartet war; er prüft nun den tatsächlichen Fokuswechsel beim Browserstart und schließt Chrome im Cleanup. Profilwechseltest scrollt nun bis zum 0,6-mm-Eintrag, statt nur im sichtbaren Dropdownbereich zu suchen. Beides bestand danach isoliert; kein reproduzierbarer Produktfehler übrig.
- Kein Honor-Zugriff, Release, Commit oder Merge. Nächster Schritt: Menü 9 „App“, Punkte 91–100; danach erst die gemeinsam angekündigte Honor-Abnahme.

### Menü 9 und 100-Punkte-Abschluss — 2026-11-15

- Punkte 91–100 abgeschlossen; Belege: `SettingsMenuActionDeviceTest` 11/11, Kamera-/Ansichtsaktionen 2/2, Orca-Sprachressourcen 1/1, Downloader-/Updater-Zustandswiederherstellung 1/1, Changelog/Zurück 1/1, Accessibility-/Responsive-Einstellungen 1/1. Insgesamt **17 eindeutige gezielte Menü-9-Geräteprüfungen** auf `emulator-5554` (API 36); die 150-%-Schriftprüfung hielt die Workspace-/Renderergeometrie konstant. Die Spracheinstellung wurde nach Auswahltest zurückgestellt.
- Theme/Akzentfarbe, Textgrößen-Vorschau, Rendererqualität/FPS, Kamera-Presets/Projektion, App-Sprache (23 Ressourcen), About/Changelog und Reset-Abbruch/Zurück wurden auf tatsächlichen Compose-Routen getestet. Der Downloader-Test deckt wiederhergestellte laufende/pausierte/abgeschlossene/fehlgeschlagene Zustände, Installieren/erneut versuchen und Statusaktualisierung ab; kein echter Netzwerkdownload wurde ausgelöst.
- Gesamtabnahme: Debug-Unit **786/786**, Slice-Unit **786/786**, `verifyNightlyChangelog` erfolgreich, Debug- und SliceTest-APK gebaut. Debug-Nightly auf Emulator installiert, Version **0.8.472 / Code 507**, Start erfolgreich; letzter Logcat-Check ohne FATAL EXCEPTION, ANR, Fatal signal oder OOM. `git diff --check` wird als Schlussprüfung dokumentiert.
- Honor war während der 100-Punkte-Arbeit gesperrt. Beim Abschlussversuch war in `adb devices -l` kein Honor vorhanden; sichtbar waren nur Emulator und Xiaomi 2211133G (`fuxi_eea`). Deshalb wurde die Honor-APK ausdrücklich nicht auf das Xiaomi installiert. Gemeinsame Sichtprüfung am Honor bleibt der nächste Hardware-Abnahmeschritt, sobald dessen ADB-Verbindung wieder erscheint.
