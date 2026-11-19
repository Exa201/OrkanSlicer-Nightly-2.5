package ru.ytkab0bp.slicebeam.slic3r

import java.io.ByteArrayInputStream
import java.io.File
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Test

class Slic3rLocalizationTest {
    @Test
    fun parsesGermanEntriesMultilineValuesAndEndOfFileEntry() {
        val localization = parse(
            """# German sample
msgid "Main Extruder"
msgstr "Hauptextruder"

msgid "Long "
"source"
msgstr "Mehrzeilig "
"übersetzt\nund vollständig"

msgid "No translation"
msgstr ""

msgid "Brand"
msgstr "PrusaSlicer und slic3r"

msgid "Escapes"
msgstr "Zitat: \"Hallo\"; Pfad C:\\Temp"

msgid "Last entry"
msgstr "Dateiende"
"""
        )

        assertEquals("Hauptextruder", localization.get("Main Extruder"))
        assertEquals("Mehrzeilig übersetzt\nund vollständig", localization.get("Long source"))
        assertEquals("No translation", localization.get("No translation"))
        assertEquals("OrkanSlicer und OrkanSlicer", localization.get("Brand"))
        assertEquals("Zitat: \"Hallo\"; Pfad C:\\Temp", localization.get("Escapes"))
        assertEquals("Dateiende", localization.get("Last entry"))
    }

    @Test
    fun missingKeysFallBackToTheSourceAndNormalizeLegacyProductNames() {
        val localization = parse("")

        assertEquals("Nicht übersetzt", localization.get("Nicht übersetzt"))
        assertEquals("OrkanSlicer", localization.get("PrusaSlicer"))
    }

    @Test
    fun parsesPluralSourceAndUsesTranslatedPluralFormForPluralLookups() {
        val localization = parse(
            """msgid "%1${'$'}d connector is out of cut contour"
msgid_plural "%1${'$'}d connectors are out of cut contour"
msgstr[0] "%1${'$'}d Verbinder ist außerhalb der Schnittkontur"
msgstr[1] "%1${'$'}d Verbinder sind außerhalb der Schnittkontur"

"""
        )

        assertEquals("%1${'$'}d Verbinder ist außerhalb der Schnittkontur", localization.get("%1${'$'}d connector is out of cut contour"))
        assertEquals("%1${'$'}d Verbinder sind außerhalb der Schnittkontur", localization.get("%1${'$'}d connectors are out of cut contour"))
    }

    @Test
    fun actualGermanCatalogResolvesOrcaPluralSourceMessages() {
        val fromModule = File("src/main/assets/localization/de.po")
        val catalog = if (fromModule.isFile) fromModule else File("app/src/main/assets/localization/de.po")
        val localization = catalog.inputStream().use(Slic3rLocalization::fromPo)

        assertEquals(
            "%1${'$'}d Verbinder sind außerhalb des Schnittkontur",
            localization.get("%1${'$'}d connectors are out of cut contour"),
        )
    }

    @Test
    fun actualGermanCatalogIncludesTheMobileBridgeWidthExplanation() {
        val fromModule = File("src/main/assets/localization/de.po")
        val catalog = if (fromModule.isFile) fromModule else File("app/src/main/assets/localization/de.po")
        val localization = catalog.inputStream().use(Slic3rLocalization::fromPo)

        assertEquals("Brückenlinienbreite", localization.get("Bridge line width"))
        assertEquals(
            "Brückenlinienbreite in mm oder Prozent des Düsendurchmessers. Höchstens so breit wie die Düse. Bei 0 wird die Breite der inneren Vollfüllung verwendet.",
            localization.get("Bridge line width in mm or % of the nozzle diameter. Maximum: nozzle diameter. 0 uses the internal solid infill width."),
        )
    }

    @Test
    fun actualKoreanCatalogUsesConsistentOrcaExtruderTerminology() {
        val fromModule = File("src/main/assets/localization/ko.po")
        val catalog = if (fromModule.isFile) fromModule else File("app/src/main/assets/localization/ko.po")
        val localization = catalog.inputStream().use(Slic3rLocalization::fromPo)

        assertEquals("왼쪽 압출기", localization.get("left extruder"))
        assertEquals("오른쪽 압출기", localization.get("right extruder"))
        assertEquals("압출기", localization.get("extruder"))
        assertEquals("%s은(는) %s 압출기에서 지원되지 않습니다.", localization.get("%s is not supported by %s extruder."))
        assertEquals("습한 PVA는 유연하여 압출기에 끼일 수 있습니다. 사용 전에 건조시키세요.", localization.get("Damp PVA is flexible and may get stuck in extruder. Dry it before use."))
        assertEquals(
            "현재 챔버 온도 또는 목표 챔버 온도가 45℃를 초과합니다. 압출기 막힘을 방지하기 위해 저온 필라멘트(PLA/PETG/TPU) 로딩이 허용되지 않습니다.",
            localization.get("The current chamber temperature or the target chamber temperature exceeds 45℃. In order to avoid extruder clogging, low temperature filament(PLA/PETG/TPU) is not allowed to be loaded."),
        )
        assertEquals(
            "저온 필라멘트(PLA/PETG/TPU)가 압출기에 로드되어 있습니다. 압출기 막힘을 방지하기 위해 챔버 온도 설정이 허용되지 않습니다.",
            localization.get("Low temperature filament(PLA/PETG/TPU) is loaded in the extruder. In order to avoid extruder clogging, it is not allowed to set the chamber temperature."),
        )
        assertEquals("현재 압출기가 필라멘트 교체 중입니다.", localization.get("Current extruder is busy changing filament."))
        assertEquals("프린터 압출기 수.", localization.get("Number of extruders of the printer."))
        assertEquals("압출기 수", localization.get("Extruder count"))
    }

    private fun parse(po: String): Slic3rLocalization = Slic3rLocalization.fromPo(
        ByteArrayInputStream(po.toByteArray(StandardCharsets.UTF_8))
    )
}
