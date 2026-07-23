package dev.muzziknod.host.lifecycle

import dev.muzziknod.host.contract.Module
import dev.muzziknod.host.contract.ModuleContract
import dev.muzziknod.host.contract.PortSpec

enum class ModuleState { Loaded, Active, Faulted, Removed }

class ManagedModule internal constructor(val module: Module) {
    val instanceId: String get() = module.instanceId
    val contract: ModuleContract get() = module.contract
    val ports: List<PortSpec> get() = module.contract.ports

    var state: ModuleState = ModuleState.Loaded
        internal set
}

sealed class LoadResult {
    data class Loaded(val managedModule: ManagedModule) : LoadResult()
    data class Rejected(val reason: String) : LoadResult()
}

/**
 * Host-side bookkeeping for loaded module instances. Knows nothing about any specific
 * module's behavior — only the declared [ModuleContract] boundary (Constitution I, VI).
 */
class ModuleRegistry {
    private val instances = mutableMapOf<String, ManagedModule>()

    fun load(module: Module): LoadResult {
        validateContract(module.contract)?.let { reason -> return LoadResult.Rejected(reason) }
        if (instances.containsKey(module.instanceId)) {
            return LoadResult.Rejected("Duplicate instanceId '${module.instanceId}' is already loaded")
        }

        val managed = ManagedModule(module)
        instances[module.instanceId] = managed
        module.onLoad()
        managed.state = ModuleState.Active
        return LoadResult.Loaded(managed)
    }

    fun get(instanceId: String): ManagedModule? = instances[instanceId]

    fun all(): Collection<ManagedModule> = instances.values

    fun removeImmediately(instanceId: String) {
        val managed = instances.remove(instanceId) ?: return
        managed.state = ModuleState.Removed
        managed.module.onRemove()
    }

    private fun validateContract(contract: ModuleContract): String? {
        val duplicatePortIds = contract.ports.groupingBy { it.id }.eachCount().filterValues { it > 1 }
        if (duplicatePortIds.isNotEmpty()) {
            return "Module contract '${contract.typeId}' declares duplicate port ids: ${duplicatePortIds.keys}"
        }
        return null
    }
}