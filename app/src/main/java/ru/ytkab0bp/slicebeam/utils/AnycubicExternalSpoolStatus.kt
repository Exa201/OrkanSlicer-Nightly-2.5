package ru.ytkab0bp.slicebeam.utils

import org.json.JSONObject

/** Parsed Anycubic status for a standalone spool; this is deliberately separate from ACE/MMU gates. */
internal data class AnycubicExternalSpoolStatus(
    val ready: Boolean,
    val filamentPresent: Boolean?,
) {
    companion object {
        fun parse(responseText: String): AnycubicExternalSpoolStatus? {
            return try {
                val status = JSONObject(responseText).optJSONObject("result")
                    ?.optJSONObject("status") ?: return null
                val hub = status.optJSONObject("filament_hub") ?: return null
                val external = when (val value = hub.opt("ext_spool")) {
                    is Number -> value.toInt() == 1
                    is Boolean -> value
                    is String -> value.equals("1") || value.equals("true", ignoreCase = true)
                    null -> false
                    else -> false
                }
                val hubs = hub.opt("filament_hubs")
                val aceMissing = hub.has("filament_hubs") && (
                    hubs == JSONObject.NULL ||
                        (hubs is org.json.JSONArray && hubs.length() == 0)
                    )
                val ready = hub.optString("ext_spool_status").equals("ready", ignoreCase = true)
                val present = when (val value = hub.opt("filament_present")) {
                    is Number -> value.toInt() != 0
                    is Boolean -> value
                    is String -> value.equals("1") || value.equals("true", ignoreCase = true)
                    null -> null
                    else -> null
                }
                AnycubicExternalSpoolStatusPolicy.resolve(
                    externalSpoolEnabled = external,
                    aceReportedAbsent = aceMissing,
                    ready = ready,
                    filamentPresent = present,
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}

/** Pure gate for local JVM tests and for keeping the hardware response parser fail-closed. */
internal object AnycubicExternalSpoolStatusPolicy {
    fun resolve(
        externalSpoolEnabled: Boolean,
        aceReportedAbsent: Boolean,
        ready: Boolean,
        filamentPresent: Boolean?,
    ): AnycubicExternalSpoolStatus? {
        if (!externalSpoolEnabled || !aceReportedAbsent || !ready || filamentPresent == false) return null
        return AnycubicExternalSpoolStatus(ready = true, filamentPresent = filamentPresent)
    }
}
