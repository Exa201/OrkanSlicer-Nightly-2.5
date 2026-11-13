package ru.ytkab0bp.slicebeam.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MmuExternalSpoolStatusTest {
    @Test fun requiresExplicitReadyExternalSpoolAndMissingAceHub() {
        assertTrue(MmuFilamentSync.isExternalSpoolReadyStatus(true, "ready", true, true))

        assertFalse("an empty-but-connected ACE is not an external-spool signal",
            MmuFilamentSync.isExternalSpoolReadyStatus(true, "ready", true, false))
        assertFalse(MmuFilamentSync.isExternalSpoolReadyStatus(false, "ready", true, true))
        assertFalse(MmuFilamentSync.isExternalSpoolReadyStatus(true, "loading", true, true))
        assertFalse("missing hub state is unknown, not proof ACE is disconnected",
            MmuFilamentSync.isExternalSpoolReadyStatus(true, "ready", false, true))
    }
}
