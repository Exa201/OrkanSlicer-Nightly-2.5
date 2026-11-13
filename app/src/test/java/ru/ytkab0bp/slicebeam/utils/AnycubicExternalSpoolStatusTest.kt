package ru.ytkab0bp.slicebeam.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnycubicExternalSpoolStatusTest {
    @Test
    fun acceptsReadyExternalSpoolOnlyWhenAceIsReportedAbsent() {
        assertEquals(
            AnycubicExternalSpoolStatus(ready = true, filamentPresent = true),
            AnycubicExternalSpoolStatusPolicy.resolve(
                externalSpoolEnabled = true,
                aceReportedAbsent = true,
                ready = true,
                filamentPresent = true,
            ),
        )
        assertEquals(
            AnycubicExternalSpoolStatus(ready = true, filamentPresent = null),
            AnycubicExternalSpoolStatusPolicy.resolve(
                externalSpoolEnabled = true,
                aceReportedAbsent = true,
                ready = true,
                filamentPresent = null,
            ),
        )
    }

    @Test
    fun rejectsMissingAceEvidenceOrUnavailableSpool() {
        val unavailable = listOf(
            Triple(false, true, true),
            Triple(true, false, true),
            Triple(true, true, false),
        )
        unavailable.forEach { (external, aceAbsent, ready) ->
            assertNull(
                AnycubicExternalSpoolStatusPolicy.resolve(
                    externalSpoolEnabled = external,
                    aceReportedAbsent = aceAbsent,
                    ready = ready,
                    filamentPresent = true,
                ),
            )
        }
        assertNull(
            AnycubicExternalSpoolStatusPolicy.resolve(
                externalSpoolEnabled = true,
                aceReportedAbsent = true,
                ready = true,
                filamentPresent = false,
            ),
        )
    }
}
