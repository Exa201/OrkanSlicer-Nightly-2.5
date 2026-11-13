package ru.ytkab0bp.slicebeam.utils

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AnycubicExternalSpoolStatusTest {
    @Test
    fun acceptsReadyExternalSpoolOnlyWhenAceIsReportedAbsent() {
        val ready = response(extSpool = 1, ready = true, filamentPresent = 1, hubs = JSONObject.NULL)
        assertEquals(true, AnycubicExternalSpoolStatus.parse(ready)?.ready)

        val emptyHubArray = response(extSpool = 1, ready = true, filamentPresent = 1, hubs = org.json.JSONArray())
        assertNotNull(AnycubicExternalSpoolStatus.parse(emptyHubArray))

        assertNull(AnycubicExternalSpoolStatus.parse(
            response(extSpool = 1, ready = true, filamentPresent = 1, hubs = org.json.JSONArray().put(JSONObject()),
        )))
        assertNull(AnycubicExternalSpoolStatus.parse(
            response(extSpool = 1, ready = true, filamentPresent = 1, hubs = null),
        ))
        assertNull(AnycubicExternalSpoolStatus.parse(
            response(extSpool = 0, ready = true, filamentPresent = 1, hubs = JSONObject.NULL),
        ))
        assertNull(AnycubicExternalSpoolStatus.parse(
            response(extSpool = 1, ready = false, filamentPresent = 1, hubs = JSONObject.NULL),
        ))
        assertNull(AnycubicExternalSpoolStatus.parse(
            response(extSpool = 1, ready = true, filamentPresent = 0, hubs = JSONObject.NULL),
        ))
    }

    private fun response(extSpool: Int, ready: Boolean, filamentPresent: Int, hubs: Any?): String {
        val hub = JSONObject()
            .put("ext_spool", extSpool)
            .put("ext_spool_status", if (ready) "ready" else "unloading")
            .put("filament_present", filamentPresent)
        if (hubs != null) hub.put("filament_hubs", hubs)
        return JSONObject().put("result", JSONObject().put("status", JSONObject().put("filament_hub", hub))).toString()
    }
}
