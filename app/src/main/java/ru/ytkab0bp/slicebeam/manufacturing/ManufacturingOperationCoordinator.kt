package ru.ytkab0bp.slicebeam.manufacturing

import java.util.concurrent.atomic.AtomicLong

/** State emitted by a long-running, cancellable manufacturing analysis or geometry job. */
sealed interface ManufacturingOperationState {
    data class Running(val progress: Float) : ManufacturingOperationState
    data object CancelRequested : ManufacturingOperationState
    data object Cancelled : ManufacturingOperationState
    data object Completed : ManufacturingOperationState
    data class Failed(val message: String) : ManufacturingOperationState
}

/** Tracks opt-in jobs by feature and keeps cancellation scoped to the feature being disabled. */
object ManufacturingOperationCoordinator {
    private val nextId = AtomicLong(1L)
    private val lock = Any()
    private val active = linkedMapOf<Long, Operation>()

    /** Returns null unless the feature is opted in and its complete handler is available. */
    fun begin(
        feature: AdvancedManufacturingFeature,
        enabled: Boolean,
        implemented: Boolean,
        supported: Boolean = true,
        prerequisitesAvailable: Boolean = true,
        experimentalConfirmed: Boolean = false,
        onStateChanged: (ManufacturingOperationState) -> Unit = {},
        onCancelRequested: () -> Unit,
    ): Operation? {
        val availability = feature.availability(enabled, implemented, supported, prerequisitesAvailable)
        val canRun = availability == ManufacturingFeatureAvailability.READY ||
            (availability == ManufacturingFeatureAvailability.EXPERIMENTAL && experimentalConfirmed)
        if (!canRun) return null
        val operation = Operation(nextId.getAndIncrement(), feature, onStateChanged, onCancelRequested)
        synchronized(lock) { active[operation.id] = operation }
        operation.emitCurrentState()
        return operation
    }

    /** Requests cancellation only for jobs owned by the specified feature. */
    fun cancelFeature(feature: AdvancedManufacturingFeature) {
        val operations = synchronized(lock) { active.values.filter { it.feature == feature } }
        operations.forEach(Operation::requestCancellation)
    }

    fun activeCount(feature: AdvancedManufacturingFeature? = null): Int = synchronized(lock) {
        if (feature == null) active.size else active.values.count { it.feature == feature }
    }

    class Operation internal constructor(
        internal val id: Long,
        val feature: AdvancedManufacturingFeature,
        private val onStateChanged: (ManufacturingOperationState) -> Unit,
        private val onCancelRequested: () -> Unit,
    ) {
        private val stateLock = Any()
        private val eventLock = Any()
        private var revision = 0L

        @Volatile
        var state: ManufacturingOperationState = ManufacturingOperationState.Running(0f)
            private set

        val isCancellationRequested: Boolean
            get() = state == ManufacturingOperationState.CancelRequested

        /** Progress is normalized and monotonic so phase changes cannot move the indicator back. */
        fun reportProgress(progress: Float) {
            if (!progress.isFinite()) return
            val update = synchronized(stateLock) {
                val current = state as? ManufacturingOperationState.Running ?: return
                ManufacturingOperationState.Running(maxOf(current.progress, progress.coerceIn(0f, 1f)))
                    .also { state = it; revision++ } to revision
            }
            emit(update.first, update.second)
        }

        fun complete() = finish(ManufacturingOperationState.Completed)

        fun cancel() = finish(ManufacturingOperationState.Cancelled)

        fun fail(message: String) {
            finish(ManufacturingOperationState.Failed(message.trim().ifEmpty { "Operation failed" }))
        }

        internal fun requestCancellation() {
            val cancelRevision = synchronized(stateLock) {
                if (state !is ManufacturingOperationState.Running) null
                else {
                    state = ManufacturingOperationState.CancelRequested
                    revision++
                    revision
                }
            }
            if (cancelRevision == null) return
            emit(ManufacturingOperationState.CancelRequested, cancelRevision)
            val stillCancelling = synchronized(stateLock) {
                revision == cancelRevision && state == ManufacturingOperationState.CancelRequested
            }
            if (!stillCancelling) return
            try {
                onCancelRequested()
            } catch (error: Exception) {
                finish(ManufacturingOperationState.Failed(error.message ?: "Cancellation failed"))
            }
        }

        private fun finish(finalState: ManufacturingOperationState) {
            val finalRevision = synchronized(stateLock) {
                if (state is ManufacturingOperationState.Completed || state is ManufacturingOperationState.Cancelled ||
                    state is ManufacturingOperationState.Failed
                ) null
                else {
                    state = finalState
                    revision++
                    revision
                }
            }
            if (finalRevision == null) return
            synchronized(lock) { active.remove(id) }
            emit(finalState, finalRevision)
        }

        internal fun emitCurrentState() {
            val snapshot = synchronized(stateLock) { state to revision }
            emit(snapshot.first, snapshot.second)
        }

        private fun emit(next: ManufacturingOperationState, expectedRevision: Long) {
            synchronized(eventLock) {
                val isCurrent = synchronized(stateLock) { revision == expectedRevision && state == next }
                if (!isCurrent) return
                try {
                    onStateChanged(next)
                } catch (_: Exception) {
                    // A reporting callback must not destabilize native geometry cleanup.
                }
            }
        }
    }
}
