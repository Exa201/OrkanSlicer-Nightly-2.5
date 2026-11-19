package ru.ytkab0bp.slicebeam.components.bed_menu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelObjectBrimFilamentPolicyTest {
    @Test
    fun choicesUseOrcaOneBasedIdsAndKeepLogicalSlotLabels() {
        val options = ModelObjectBrimFilamentPolicy.options(listOf("PLA", "PETG", "TPU"))

        assertEquals(listOf("1" to "T0 · PLA", "2" to "T1 · PETG", "3" to "T2 · TPU"), options)
        assertTrue(ModelObjectBrimFilamentPolicy.accepts("", options))
        assertTrue(ModelObjectBrimFilamentPolicy.accepts("3", options))
        assertFalse(ModelObjectBrimFilamentPolicy.accepts("4", options))
    }
}
