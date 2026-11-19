package ru.ytkab0bp.slicebeam.manufacturing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ManufacturingOperationCoordinatorTest {
    @Test
    fun disabledOrUnavailableFeaturesCannotStartJobs() {
        var emittedStates = 0
        assertNull(ManufacturingOperationCoordinator.begin(
            AdvancedManufacturingFeature.CNC_PRECISION_ENGINE,
            enabled = false,
            implemented = true,
            onStateChanged = { emittedStates++ },
            onCancelRequested = {},
        ))
        assertNull(ManufacturingOperationCoordinator.begin(
            AdvancedManufacturingFeature.CNC_PRECISION_ENGINE,
            enabled = true,
            implemented = false,
            onStateChanged = { emittedStates++ },
            onCancelRequested = {},
        ))
        assertEquals("blocked operations must not publish a start event", 0, emittedStates)
        assertEquals(0, ManufacturingOperationCoordinator.activeCount())
    }

    @Test
    fun disablingOneFeatureOnlyCancelsItsOwnOperations() {
        var cncCancelCount = 0
        var markingCancelCount = 0
        val cnc = ManufacturingOperationCoordinator.begin(
            AdvancedManufacturingFeature.CNC_PRECISION_ENGINE,
            enabled = true,
            implemented = true,
            onCancelRequested = { cncCancelCount++ },
        )!!
        val marking = ManufacturingOperationCoordinator.begin(
            AdvancedManufacturingFeature.INTELLIGENT_MARKING,
            enabled = true,
            implemented = true,
            onCancelRequested = { markingCancelCount++ },
        )!!

        ManufacturingOperationCoordinator.cancelFeature(AdvancedManufacturingFeature.CNC_PRECISION_ENGINE)

        assertEquals(1, cncCancelCount)
        assertEquals(0, markingCancelCount)
        assertEquals(ManufacturingOperationState.CancelRequested, cnc.state)
        assertTrue(marking.state is ManufacturingOperationState.Running)
        cnc.complete()
        marking.complete()
        assertEquals(0, ManufacturingOperationCoordinator.activeCount())
    }

    @Test
    fun progressIsFiniteClampedMonotonicAndTerminalStateIsStable() {
        val states = mutableListOf<ManufacturingOperationState>()
        val operation = ManufacturingOperationCoordinator.begin(
            AdvancedManufacturingFeature.LASER_CONVERTER,
            enabled = true,
            implemented = true,
            onStateChanged = states::add,
            onCancelRequested = {},
        )!!
        operation.reportProgress(0.7f)
        operation.reportProgress(0.2f)
        operation.reportProgress(Float.NaN)
        operation.reportProgress(1.7f)
        assertEquals(1f, (operation.state as ManufacturingOperationState.Running).progress)
        operation.fail(" mesh rejected ")
        operation.reportProgress(1f)
        assertEquals(ManufacturingOperationState.Failed("mesh rejected"), operation.state)
        assertFalse(states.last() is ManufacturingOperationState.Running)
        assertEquals(0, ManufacturingOperationCoordinator.activeCount())
    }

    @Test
    fun concurrentProgressCallbacksAreDeliveredInRevisionOrder() {
        val enteredOlderCallback = CountDownLatch(1)
        val releaseOlderCallback = CountDownLatch(1)
        val progress = Collections.synchronizedList(mutableListOf<Float>())
        val operation = ManufacturingOperationCoordinator.begin(
            AdvancedManufacturingFeature.CNC_PRECISION_ENGINE,
            enabled = true,
            implemented = true,
            onStateChanged = { state ->
                if (state is ManufacturingOperationState.Running) {
                    progress += state.progress
                    if (state.progress == 0.25f) {
                        enteredOlderCallback.countDown()
                        check(releaseOlderCallback.await(2, TimeUnit.SECONDS))
                    }
                }
            },
            onCancelRequested = {},
        )!!

        val older = Thread { operation.reportProgress(0.25f) }
        val newer = Thread { operation.reportProgress(0.75f) }
        older.start()
        assertTrue("older callback should enter before newer update", enteredOlderCallback.await(2, TimeUnit.SECONDS))
        newer.start()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while ((operation.state as? ManufacturingOperationState.Running)?.progress != 0.75f && System.nanoTime() < deadline) {
            Thread.yield()
        }
        assertEquals(0.75f, (operation.state as ManufacturingOperationState.Running).progress)
        releaseOlderCallback.countDown()
        older.join(2_000)
        newer.join(2_000)

        assertEquals(listOf(0f, 0.25f, 0.75f), progress.toList())
        operation.complete()
    }

    @Test
    fun experimentalFeatureRequiresExplicitConfirmation() {
        val feature = AdvancedManufacturingFeature.NON_PLANAR_SURFACE_PRINTING
        assertNull(ManufacturingOperationCoordinator.begin(
            feature, enabled = true, implemented = true, experimentalConfirmed = false, onCancelRequested = {},
        ))
        val operation = ManufacturingOperationCoordinator.begin(
            feature, enabled = true, implemented = true, experimentalConfirmed = true, onCancelRequested = {},
        )
        assertTrue(operation != null)
        operation!!.complete()
    }
}
