package ru.ytkab0bp.slicebeam.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ProcessExtruderVariantPolicyTest {
    @Test
    fun addsMissingToolsFromMatchingToolOneVariantAndLeavesSharedOptionsUntouched() {
        val input = mapOf(
            "print_extruder_id" to "1,1",
            "print_extruder_variant" to ProfileStringVectorCodec.serialize(
                listOf("Direct Drive Standard", "Direct Drive High Flow"),
            ),
            "outer_wall_speed" to "42,55",
            "layer_height" to "0.2",
        )

        val result = ProcessExtruderVariantPolicy.addMissingToolColumns(
            input,
            variantsByTool = listOf("Direct Drive Standard, Direct Drive High Flow", "Direct Drive Standard"),
            perNozzleOptionKeys = setOf("outer_wall_speed", "layer_height"),
        )

        assertEquals("1,1,2", result["print_extruder_id"])
        assertEquals(
            ProfileStringVectorCodec.serialize(
                listOf("Direct Drive Standard", "Direct Drive High Flow", "Direct Drive Standard"),
            ),
            result["print_extruder_variant"],
        )
        assertEquals("42,55,42", result["outer_wall_speed"])
        assertEquals("0.2", result["layer_height"])
    }

    @Test
    fun fallsBackToFirstColumnWhenToolOneDoesNotExposeTheRequestedVariant() {
        val result = ProcessExtruderVariantPolicy.addMissingToolColumns(
            mapOf(
                "print_extruder_id" to "1,1",
                "print_extruder_variant" to ProfileStringVectorCodec.serialize(
                    listOf("Direct Drive Standard", "Direct Drive High Flow"),
                ),
                "retraction_length" to "0.8,1.1",
            ),
            variantsByTool = listOf("Direct Drive Standard", "Bowden Standard", "Bowden Standard"),
            perNozzleOptionKeys = setOf("retraction_length"),
        )

        assertEquals("1,1,2,3", result["print_extruder_id"])
        assertEquals("0.8,1.1,0.8,0.8", result["retraction_length"])
    }

    @Test
    fun singleColumnRemainsSharedAndMalformedHeadersAreIgnored() {
        val single = mapOf("print_extruder_id" to "1", "print_extruder_variant" to "Direct Drive Standard")
        assertSame(single, ProcessExtruderVariantPolicy.addMissingToolColumns(single, listOf("Direct Drive Standard", "Direct Drive Standard"), setOf()))

        val malformed = mapOf("print_extruder_id" to "1,2", "print_extruder_variant" to "Direct Drive Standard")
        assertSame(malformed, ProcessExtruderVariantPolicy.addMissingToolColumns(malformed, listOf("Direct Drive Standard"), setOf()))
    }
}
