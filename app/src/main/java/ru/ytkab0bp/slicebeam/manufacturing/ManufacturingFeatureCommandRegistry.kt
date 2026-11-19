package ru.ytkab0bp.slicebeam.manufacturing

import java.util.concurrent.Executor

/** A real feature command and the runtime conditions required before it may run. */
interface ManufacturingFeatureCommand {
    val feature: AdvancedManufacturingFeature
    fun isSupported(): Boolean = true
    fun prerequisitesAvailable(): Boolean = true
    fun execute(operation: ManufacturingOperationCoordinator.Operation)
}

data class ManufacturingCommandLaunch(
    val availability: ManufacturingFeatureAvailability,
    val operation: ManufacturingOperationCoordinator.Operation? = null,
)

/**
 * Single entry point for manufacturing actions. The same registry controls command visibility
 * and execution, so toggling a feature on cannot make a placeholder action appear runnable.
 */
object ManufacturingFeatureCommandRegistry {
    private val lock = Any()
    private val commands = linkedMapOf<AdvancedManufacturingFeature, ManufacturingFeatureCommand>()

    /** Registers one concrete handler. Duplicate handlers fail fast rather than shadowing each other. */
    fun register(command: ManufacturingFeatureCommand): AutoCloseable {
        synchronized(lock) {
            check(commands.putIfAbsent(command.feature, command) == null) {
                "A manufacturing command is already registered for ${command.feature.key}"
            }
        }
        return AutoCloseable {
            synchronized(lock) {
                if (commands[command.feature] === command) commands.remove(command.feature)
            }
        }
    }

    /** Only enabled features with an actual command handler are eligible for a menu action. */
    fun visibleFeatures(isEnabled: (AdvancedManufacturingFeature) -> Boolean): List<AdvancedManufacturingFeature> {
        val registered = synchronized(lock) { commands.keys.toSet() }
        return AdvancedManufacturingFeature.all.filter { feature ->
            if (feature !in registered || !isEnabled(feature)) return@filter false
            availability(feature, enabled = true) in setOf(
                ManufacturingFeatureAvailability.READY,
                ManufacturingFeatureAvailability.EXPERIMENTAL,
            )
        }
    }

    fun availability(
        feature: AdvancedManufacturingFeature,
        enabled: Boolean,
    ): ManufacturingFeatureAvailability {
        val command = synchronized(lock) { commands[feature] }
            ?: return feature.availability(enabled = enabled, implemented = false)
        return feature.availability(
            enabled = enabled,
            implemented = true,
            supported = command.isSupported(),
            prerequisitesAvailable = command.prerequisitesAvailable(),
        )
    }

    /**
     * Starts only registered commands and routes every operation through the shared lifecycle gate.
     * Experimental commands require explicit confirmation on every launch.
     */
    fun launch(
        feature: AdvancedManufacturingFeature,
        enabled: Boolean,
        experimentalConfirmed: Boolean = false,
        onStateChanged: (ManufacturingOperationState) -> Unit = {},
        onCancelRequested: () -> Unit,
    ): ManufacturingCommandLaunch {
        val command = synchronized(lock) { commands[feature] }
            ?: return ManufacturingCommandLaunch(
                feature.availability(enabled = enabled, implemented = false),
            )
        val supported = command.isSupported()
        val prerequisitesAvailable = command.prerequisitesAvailable()
        val operation = ManufacturingOperationCoordinator.begin(
            feature = feature,
            enabled = enabled,
            implemented = true,
            supported = supported,
            prerequisitesAvailable = prerequisitesAvailable,
            experimentalConfirmed = experimentalConfirmed,
            onStateChanged = onStateChanged,
            onCancelRequested = onCancelRequested,
        ) ?: return ManufacturingCommandLaunch(
            feature.availability(
                enabled = enabled,
                implemented = true,
                supported = supported,
                prerequisitesAvailable = prerequisitesAvailable,
            ),
        )

        return try {
            command.execute(operation)
            ManufacturingCommandLaunch(
                feature.availability(
                    enabled = enabled,
                    implemented = true,
                    supported = supported,
                    prerequisitesAvailable = prerequisitesAvailable,
                ),
                operation,
            )
        } catch (error: Exception) {
            operation.fail(error.message ?: "Manufacturing operation failed")
            ManufacturingCommandLaunch(
                feature.availability(
                    enabled = enabled,
                    implemented = true,
                    supported = supported,
                    prerequisitesAvailable = prerequisitesAvailable,
                ),
                operation,
            )
        }
    }

    /**
     * Schedules a blocking geometry/analysis handler on the supplied worker executor. UI callers
     * should use this API instead of [launch]; wrap [onStateChanged] with a main-thread dispatcher
     * when it updates Compose state. Commands must poll [ManufacturingOperationCoordinator.Operation.isCancellationRequested]
     * at safe checkpoints and may report normalized progress through the operation.
     */
    fun launchAsync(
        feature: AdvancedManufacturingFeature,
        enabled: Boolean,
        executor: Executor,
        experimentalConfirmed: Boolean = false,
        onStateChanged: (ManufacturingOperationState) -> Unit = {},
        onCancelRequested: () -> Unit,
    ): ManufacturingCommandLaunch {
        val command = synchronized(lock) { commands[feature] }
            ?: return ManufacturingCommandLaunch(feature.availability(enabled = enabled, implemented = false))
        val supported = command.isSupported()
        val prerequisitesAvailable = command.prerequisitesAvailable()
        val availability = feature.availability(
            enabled = enabled,
            implemented = true,
            supported = supported,
            prerequisitesAvailable = prerequisitesAvailable,
        )
        val operation = ManufacturingOperationCoordinator.begin(
            feature = feature,
            enabled = enabled,
            implemented = true,
            supported = supported,
            prerequisitesAvailable = prerequisitesAvailable,
            experimentalConfirmed = experimentalConfirmed,
            onStateChanged = onStateChanged,
            onCancelRequested = onCancelRequested,
        ) ?: return ManufacturingCommandLaunch(availability)

        try {
            executor.execute {
                if (operation.isCancellationRequested) {
                    operation.cancel()
                    return@execute
                }
                try {
                    command.execute(operation)
                    when (operation.state) {
                        ManufacturingOperationState.CancelRequested -> operation.cancel()
                        is ManufacturingOperationState.Running -> operation.complete()
                        else -> Unit
                    }
                } catch (error: Exception) {
                    if (operation.isCancellationRequested) operation.cancel()
                    else operation.fail(error.message ?: "Manufacturing operation failed")
                }
            }
        } catch (error: RuntimeException) {
            operation.fail(error.message ?: "Could not schedule manufacturing operation")
        }
        return ManufacturingCommandLaunch(availability, operation)
    }
}
