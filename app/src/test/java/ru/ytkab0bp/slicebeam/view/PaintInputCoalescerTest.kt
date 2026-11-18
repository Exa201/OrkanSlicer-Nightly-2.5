package ru.ytkab0bp.slicebeam.view

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaintInputCoalescerTest {
    @Test
    fun rapidMovesKeepTheWholePendingSegmentAndScheduleOneDrain() {
        val coalescer = PaintInputCoalescer()

        assertTrue(coalescer.submit(1f, 2f))
        repeat(100) { index ->
            assertFalse(coalescer.submit(index.toFloat(), (index * 2).toFloat()))
        }

        val path = requireNotNull(coalescer.takePending()).points
        assertEquals("the coalesced stroke must preserve every delivered move", 101, path.size)
        assertEquals(PaintInputCoalescer.Point(1f, 2f), path.first())
        assertEquals(PaintInputCoalescer.Point(99f, 198f), path.last())
        assertFalse(coalescer.finishDrain())
        assertTrue("a later gesture must be able to schedule another GL drain", coalescer.submit(3f, 4f))
    }

    @Test
    fun flushReturnsTheLastPointBeforeStrokeCompletionAndResetsScheduling() {
        val coalescer = PaintInputCoalescer()
        assertTrue(coalescer.submit(1f, 1f))
        assertFalse(coalescer.submit(8f, 9f))

        assertEquals(
            PaintInputCoalescer.Segment(listOf(
                PaintInputCoalescer.Point(1f, 1f),
                PaintInputCoalescer.Point(8f, 9f),
            )),
            coalescer.flush(),
        )
        assertTrue("input after flush must queue a fresh drain", coalescer.submit(10f, 11f))
        assertEquals(
            PaintInputCoalescer.Segment(listOf(PaintInputCoalescer.Point(10f, 11f))),
            coalescer.takePending(),
        )
        assertFalse(coalescer.finishDrain())
    }

    @Test
    fun aMoveQueuedWhileThePreviousDrainRunsStartsTheNextSegmentAtTheFirstNewPoint() {
        val coalescer = PaintInputCoalescer()
        assertTrue(coalescer.submit(0f, 0f))
        assertEquals(
            PaintInputCoalescer.Segment(listOf(PaintInputCoalescer.Point(0f, 0f))),
            coalescer.takePending(),
        )
        assertFalse(coalescer.submit(5f, 6f))
        assertFalse(coalescer.submit(20f, 24f))

        assertTrue(coalescer.finishDrain())
        assertEquals(
            PaintInputCoalescer.Segment(listOf(
                PaintInputCoalescer.Point(5f, 6f),
                PaintInputCoalescer.Point(20f, 24f),
            )),
            coalescer.takePending(),
        )
        assertFalse(coalescer.finishDrain())
    }
}
