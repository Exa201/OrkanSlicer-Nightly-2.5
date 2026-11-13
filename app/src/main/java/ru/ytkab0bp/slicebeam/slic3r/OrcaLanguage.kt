package ru.ytkab0bp.slicebeam.slic3r

/** Languages with localization catalogs shipped by the current OrcaSlicer source. */
internal object OrcaLanguage {
    data class Entry(val tag: String, val assetKey: String, val nativeName: String)

    val all: List<Entry> = listOf(
        Entry("ca", "ca", "Català"), Entry("cs", "cs", "Čeština"),
        Entry("de", "de", "Deutsch"), Entry("en", "en", "English"),
        Entry("es", "es", "Español"), Entry("eu", "eu", "Euskara"),
        Entry("fr", "fr", "Français"), Entry("hu", "hu", "Magyar"),
        Entry("it", "it", "Italiano"), Entry("ja", "ja", "日本語"),
        Entry("ko", "ko", "한국어"), Entry("lt", "lt", "Lietuvių"),
        Entry("nl", "nl", "Nederlands"), Entry("pl", "pl", "Polski"),
        Entry("pt-BR", "pt_BR", "Português (Brasil)"), Entry("ru", "ru", "Русский"),
        Entry("sv", "sv", "Svenska"), Entry("th", "th", "ไทย"),
        Entry("tr", "tr", "Türkçe"), Entry("uk", "uk", "Українська"),
        Entry("vi", "vi", "Tiếng Việt"), Entry("zh-CN", "zh_CN", "简体中文"),
        Entry("zh-TW", "zh_TW", "繁體中文"),
    )

    fun byTag(tag: String?): Entry {
        val normalized = tag?.replace('_', '-')?.lowercase(java.util.Locale.ROOT).orEmpty()
        return all.firstOrNull { it.tag.equals(normalized, ignoreCase = true) }
            ?: all.firstOrNull { it.tag.substringBefore('-').equals(normalized.substringBefore('-'), ignoreCase = true) }
            ?: all.first { it.tag == "de" }
    }
}
