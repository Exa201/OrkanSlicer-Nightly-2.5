package ru.ytkab0bp.slicebeam.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FillBedPlannerTest {
    @Test
    fun rejectsOnlyFootprintsThatCannotFitEvenAfterQuarterTurn() {
        assertTrue(FillBedPlanner.canFitFootprint(220.0, 220.0, 220.0, 40.0))
        assertTrue(FillBedPlanner.canFitFootprint(220.0, 100.0, 80.0, 200.0))
        assertEquals(false, FillBedPlanner.canFitFootprint(220.0, 220.0, 800.0, 800.0))
        assertEquals(false, FillBedPlanner.canFitFootprint(220.0, 100.0, 230.0, 110.0))
        assertEquals(false, FillBedPlanner.canFitFootprint(220.0, 220.0, Double.NaN, 20.0))
    }

    @Test
    fun estimatesOnlyObjectsThatFitTheBed() {
        assertEquals(4, FillBedPlanner.estimatedCapacity(220.0, 220.0, 100.0, 100.0, 6.0))
        assertEquals(0, FillBedPlanner.estimatedCapacity(220.0, 220.0, 221.0, 100.0, 6.0))
        assertEquals(0, FillBedPlanner.estimatedCapacity(220.0, 220.0, 100.0, 221.0, 6.0))
    }

    @Test
    fun invalidDimensionsDoNotCreateSpeculativeCapacity() {
        assertEquals(0, FillBedPlanner.estimatedCapacity(220.0, 220.0, 0.0, 20.0, 6.0))
        assertEquals(0, FillBedPlanner.estimatedCapacity(Double.NaN, 220.0, 20.0, 20.0, 6.0))
        assertEquals(0, FillBedPlanner.estimatedCapacity(220.0, 220.0, 20.0, 20.0, -1.0))
    }

    @Test
    fun searchesForTheLargestPackableCopyCountWithoutScanningEveryCandidate() {
        val attempted = mutableListOf<Int>()
        val result = FillBedPlanner.maximumPackableCopies(256) { count ->
            attempted += count
            count <= 173
        }

        assertEquals(173, result)
        assertEquals("search work should grow logarithmically, not with the plate capacity", 9, attempted.size)
        assertTrue(attempted.distinct().size == attempted.size)
    }

    @Test
    fun acceptsEstimatedFullBedCapacityWithOneNativeFitProbe() {
        val attempted = mutableListOf<Int>()

        val result = FillBedPlanner.maximumPackableCopies(63) { count ->
            attempted += count
            count <= 63
        }

        assertEquals(63, result)
        assertEquals(listOf(63), attempted)
    }

    @Test
    fun returnsZeroWhenNoCopyCanBePlaced() {
        assertEquals(0, FillBedPlanner.maximumPackableCopies(8) { false })
        assertEquals(0, FillBedPlanner.maximumPackableCopies(0) { error("zero limit must not call the probe") })
    }
}
