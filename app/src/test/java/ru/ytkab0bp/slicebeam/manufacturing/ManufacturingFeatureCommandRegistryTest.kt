package ru.ytkab0bp.slicebeam.manufacturing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ManufacturingFeatureCommandRegistryTest {
    @Test
    fun unregisteredFeatureCannotAppearOrLaunchEvenWhenEnabled() {
        val feature = AdvancedManufacturingFeature.CNC_PRECISION_ENGINE
        var stateEvents = 0
        assertFalse(ManufacturingFeatureCommandRegistry.visibleFeatures { it == feature }.contains(feature))
        assertEquals(
            ManufacturingFeatureAvailability.NOT_IMPLEMENTED,
            ManufacturingFeatureCommandRegistry.availability(feature, enabled = true),
        )
        val result = ManufacturingFeatureCommandRegistry.launch(
            feature = feature,
            enabled = true,
            onStateChanged = { stateEvents++ },
            onCancelRequested = {},
        )
        assertEquals(ManufacturingFeatureAvailability.NOT_IMPLEMENTED, result.availability)
        assertNull(result.operation)
        assertEquals(0, stateEvents)
    }

    @Test
    fun visibilityAndLaunchUseSameEnabledAndPrerequisiteGates() {
        val targetFeature = AdvancedManufacturingFeature.LASER_CONVERTER
        var executed = false
        val registration = ManufacturingFeatureCommandRegistry.register(object : ManufacturingFeatureCommand {
            override val feature = targetFeature
            override fun prerequisitesAvailable() = false
            override fun execute(operation: ManufacturingOperationCoordinator.Operation) {
                executed = true
            }
        })
        try {
            assertFalse(ManufacturingFeatureCommandRegistry.visibleFeatures { false }.contains(targetFeature))
            assertFalse(ManufacturingFeatureCommandRegistry.visibleFeatures { true }.contains(targetFeature))
            assertEquals(
                ManufacturingFeatureAvailability.MISSING_PREREQUISITES,
                ManufacturingFeatureCommandRegistry.availability(targetFeature, enabled = true),
            )
            val disabled = ManufacturingFeatureCommandRegistry.launch(targetFeature, enabled = false, onCancelRequested = {})
            assertEquals(ManufacturingFeatureAvailability.DISABLED, disabled.availability)
            assertNull(disabled.operation)
            assertFalse(executed)
            val blocked = ManufacturingFeatureCommandRegistry.launch(targetFeature, enabled = true, onCancelRequested = {})
            assertEquals(ManufacturingFeatureAvailability.MISSING_PREREQUISITES, blocked.availability)
            assertNull(blocked.operation)
            assertFalse(executed)
        } finally {
            registration.close()
        }
    }

    @Test
    fun enabledRegisteredCommandRunsThroughSharedLifecycleAndCleansUp() {
        val targetFeature = AdvancedManufacturingFeature.CNC_PRECISION_ENGINE
        val registration = ManufacturingFeatureCommandRegistry.register(object : ManufacturingFeatureCommand {
            override val feature = targetFeature
            override fun execute(operation: ManufacturingOperationCoordinator.Operation) {
                operation.reportProgress(0.5f)
                operation.complete()
            }
        })
        try {
            val events = mutableListOf<ManufacturingOperationState>()
            val result = ManufacturingFeatureCommandRegistry.launch(
                feature = targetFeature,
                enabled = true,
                onStateChanged = events::add,
                onCancelRequested = {},
            )
            assertEquals(ManufacturingFeatureAvailability.READY, result.availability)
            assertNotNull(result.operation)
            assertEquals(ManufacturingOperationState.Completed, result.operation?.state)
            assertEquals(0, ManufacturingOperationCoordinator.activeCount(targetFeature))
            assertTrue(events.any { it == ManufacturingOperationState.Running(0.5f) })
            assertEquals(ManufacturingOperationState.Completed, events.last())
        } finally {
            registration.close()
        }
    }

    @Test
    fun experimentalCommandsStillRequireExplicitConfirmation() {
        val targetFeature = AdvancedManufacturingFeature.NON_PLANAR_SURFACE_PRINTING
        val registration = ManufacturingFeatureCommandRegistry.register(object : ManufacturingFeatureCommand {
            override val feature = targetFeature
            override fun execute(operation: ManufacturingOperationCoordinator.Operation) = Unit
        })
        try {
            val blocked = ManufacturingFeatureCommandRegistry.launch(targetFeature, enabled = true, onCancelRequested = {})
            assertEquals(ManufacturingFeatureAvailability.EXPERIMENTAL, blocked.availability)
            assertNull(blocked.operation)
            val allowed = ManufacturingFeatureCommandRegistry.launch(
                targetFeature,
                enabled = true,
                experimentalConfirmed = true,
                onCancelRequested = {},
            )
            assertNotNull(allowed.operation)
            allowed.operation?.complete()
        } finally {
            registration.close()
        }
    }

    @Test
    fun asyncCommandRunsOnWorkerAndReportsTerminalState() {
        val targetFeature = AdvancedManufacturingFeature.CNC_PRECISION_ENGINE
        val worker = Executors.newSingleThreadExecutor { Thread(it, "manufacturing-test-worker") }
        val finished = CountDownLatch(1)
        var executionThread: String? = null
        val registration = ManufacturingFeatureCommandRegistry.register(object : ManufacturingFeatureCommand {
            override val feature = targetFeature
            override fun execute(operation: ManufacturingOperationCoordinator.Operation) {
                executionThread = Thread.currentThread().name
                operation.reportProgress(0.75f)
                finished.countDown()
            }
        })
        try {
            val result = ManufacturingFeatureCommandRegistry.launchAsync(
                feature = targetFeature,
                enabled = true,
                executor = worker,
                onCancelRequested = {},
            )
            assertNotNull(result.operation)
            assertTrue("command must finish on the worker", finished.await(2, TimeUnit.SECONDS))
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            while (result.operation?.state !is ManufacturingOperationState.Completed && System.nanoTime() < deadline) {
                Thread.yield()
            }
            assertEquals("manufacturing-test-worker", executionThread)
            assertEquals(ManufacturingOperationState.Completed, result.operation?.state)
            assertEquals(0, ManufacturingOperationCoordinator.activeCount(targetFeature))
        } finally {
            registration.close()
            worker.shutdownNow()
        }
    }

    @Test
    fun asyncCommandCancelledBeforeWorkerStartsNeverExecutes() {
        val targetFeature = AdvancedManufacturingFeature.SMART_ASSEMBLY_CUTTER
        val queuedTasks = mutableListOf<Runnable>()
        val executor = Executor { queuedTasks += it }
        var executed = false
        val registration = ManufacturingFeatureCommandRegistry.register(object : ManufacturingFeatureCommand {
            override val feature = targetFeature
            override fun execute(operation: ManufacturingOperationCoordinator.Operation) {
                executed = true
                operation.complete()
            }
        })
        try {
            val result = ManufacturingFeatureCommandRegistry.launchAsync(
                feature = targetFeature,
                enabled = true,
                executor = executor,
                onCancelRequested = {},
            )
            ManufacturingOperationCoordinator.cancelFeature(targetFeature)
            queuedTasks.single().run()
            assertFalse(executed)
            assertEquals(ManufacturingOperationState.Cancelled, result.operation?.state)
            assertEquals(0, ManufacturingOperationCoordinator.activeCount(targetFeature))
        } finally {
            registration.close()
        }
    }
}
