# OrkanSlicer – CAD Navigation Icon Pack

5 eigenständige, originale Icons im technischen isometrischen 3D-CAD-Stil, inspiriert von der klaren Werkzeug-Symbolsprache moderner CAD-Software. Keine Autodesk-Originalassets.

Icons: `nav_datei`, `nav_modell`, `nav_aendern`, `nav_tools`, `nav_slicen`.

## Inhalt

- `svg/`: frei skalierbare SVG-Quellen mit transparentem Hintergrund, je 3 Zustände.
- `png/`: gerenderte PNGs 32, 48, 64, 96, 128 und 256 px, je 3 Zustände.
- `android/drawable-nodpi/`: PNG-Dateien in 128 px für direkte Verwendung in einer Android-App.
- `standard`: normale farbige Darstellung.
- `aktiv`: hellere/glühende Darstellung, keine zusätzliche Hintergrundfläche.
- `deaktiviert`: entsättigt und mit reduzierter Deckkraft.

## Für OrkanSlicer / Jetpack Compose

Beschriftung, Click-Area, Fokusrahmen und die ausgewählte Pillenform bitte **nativ im Compose-Code** zeichnen, nicht in den Assets. Dadurch bleiben Kontrast und Skalierung bei Schriftgröße 150 % stabil.

Beispiel:

```kotlin
val iconRes = when {
    !enabled -> R.drawable.nav_tools_disabled
    selected -> R.drawable.nav_tools_active
    else -> R.drawable.nav_tools
}
Image(
    painter = painterResource(iconRes),
    contentDescription = null, // Beschriftung übernimmt Semantik am gesamten Button
    modifier = Modifier.size(30.dp)
)
```

Empfehlung: Symbol 28–32 dp, Touch-Ziel >=48 dp, Text separat unterhalb des Symbols. Die erzeugten Icons enthalten keine Texte und keine Hintergrundkacheln.

Lizenz: Erstellt als benutzerdefinierte Originalassets für das Projekt OrkanSlicer. Rechte Dritter an CAD-Produktnamen oder Markenzeichen werden nicht übertragen.
