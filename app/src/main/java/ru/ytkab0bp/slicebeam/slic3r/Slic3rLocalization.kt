package ru.ytkab0bp.slicebeam.slic3r

import ru.ytkab0bp.slicebeam.SliceBeam
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.HashMap
import java.util.Locale
import java.util.regex.Pattern

/** Loads Orca's PO strings and applies the app name without changing native message IDs. */
open class Slic3rLocalization private constructor(strings: Map<String, String>) {
    private val translations = HashMap(strings)

    @Throws(IOException::class)
    constructor(key: String) : this(
        parsePo(SliceBeam.requireInstance().assets.open("localization/$key.po"))
    )

    open fun get(key: String): String {
        val translated = translations[key]
        if (!translated.isNullOrEmpty()) return translated
        return replaceString(key).also { translations[key] = it }
    }

    companion object {
        private val localesMap = HashMap<String, Slic3rLocalization>()
        private val escapedCharacterPattern = Pattern.compile("\\\\(.)")
        private val slic3rNamePattern = Pattern.compile("(?i)slic3r")
        private val prusaSlicerNamePattern = Pattern.compile("(?i)prusasl?i[sc]er")

        @JvmStatic
        fun getString(key: String): String = getInstance().get(key)

        @JvmStatic
        fun getInstance(key: String): Slic3rLocalization = synchronized(localesMap) {
            localesMap[key]?.let { return@synchronized it }
            val locale = try {
                Slic3rLocalization(key)
            } catch (error: IOException) {
                error.printStackTrace()
                try {
                    Slic3rLocalization("en")
                } catch (fallbackError: IOException) {
                    throw RuntimeException(fallbackError)
                }
            }
            localesMap[key] = locale
            locale
        }

        @JvmStatic
        fun getInstance(): Slic3rLocalization = getInstance(
            OrcaLanguage.byTag(ru.ytkab0bp.slicebeam.utils.Prefs.getPrefs()
                ?.getString("app_language", Locale.getDefault().language)).assetKey,
        )

        /** The same parser entry used by the asset loader, exposed internally for JVM tests. */
        internal fun fromPo(input: InputStream): Slic3rLocalization = Slic3rLocalization(parsePo(input))

        private fun parsePo(input: InputStream): Map<String, String> {
            val result = HashMap<String, String>()
            var messageId: StringBuilder? = null
            var messageString: StringBuilder? = null

            fun flushEntry() {
                val id = messageId?.toString()
                val translation = messageString?.toString()
                if (!id.isNullOrEmpty() && !translation.isNullOrEmpty()) {
                    result[id] = replaceString(translation)
                }
                messageId = null
                messageString = null
            }

            input.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    when {
                        line.startsWith("#") -> Unit
                        line.startsWith("msgid") -> messageId = StringBuilder(line.substring(7, line.length - 1))
                        line.startsWith("msgstr") -> messageString = StringBuilder(line.substring(8, line.length - 1))
                        line.isEmpty() -> flushEntry()
                        line.startsWith('"') && line.endsWith('"') -> {
                            val continuation = line.substring(1, line.length - 1)
                            if (messageString != null) messageString!!.append(continuation)
                            else messageId?.append(continuation)
                        }
                    }
                }
                // Some valid PO files end at the final quote without a separator line.
                flushEntry()
            }
            return result
        }

        private fun replaceString(value: String?): String {
            if (value == null) return ""
            val escaped = escapedCharacterPattern.matcher(value.replace("\\n", "\n")).replaceAll("\$1")
            val slic3rReplaced = slic3rNamePattern.matcher(escaped).replaceAll("OrkanSlicer")
            return prusaSlicerNamePattern.matcher(slic3rReplaced).replaceAll("OrkanSlicer")
        }
    }
}
