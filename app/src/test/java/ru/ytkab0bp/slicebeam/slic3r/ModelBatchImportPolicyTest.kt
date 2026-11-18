package ru.ytkab0bp.slicebeam.slic3r

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelBatchImportPolicyTest {
    @Test
    fun acceptsOrcaIndependentMeshFormatsCaseInsensitively() {
        listOf("part.stl", "part.OBJ", "part.amf", "part.oltp").forEach {
            assertTrue("Expected $it to support batch import", ModelBatchImportPolicy.supports(it))
        }
    }

    @Test
    fun keepsProjectAndOptionDrivenFormatsOutOfFlatBatchImport() {
        listOf("project.3mf", "part.step", "part.stp", "shape.svg", "preview.gcode", null, " ").forEach {
            assertFalse("Expected $it to stay on its specialized import route", ModelBatchImportPolicy.supports(it))
        }
    }

    @Test
    fun importPlacementDefaultsToCompactAndValidatesExplicitClearance() {
        val compact = ModelBatchImportPlacement()
        assertFalse(compact.usesPerObjectDistance)
        assertTrue(compact.validatedDistanceMm() == null)

        val spaced = ModelBatchImportPlacement(6.0)
        assertTrue(spaced.usesPerObjectDistance)
        assertTrue(spaced.validatedDistanceMm() == 6.0)
        listOf(0.0, -1.0, 50.1, Double.NaN, Double.POSITIVE_INFINITY).forEach {
            assertTrue("invalid clearance $it should be rejected", ModelBatchImportPlacement(it).validatedDistanceMm() == null)
        }
    }
}
