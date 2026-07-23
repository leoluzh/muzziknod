package dev.muzziknod.host.lifecycle

import dev.muzziknod.host.contract.Module
import dev.muzziknod.host.contract.ModuleContract
import dev.muzziknod.host.contract.PortSpec
import dev.muzziknod.host.contract.ProcessContext

/** Minimal [Module] test double for `core-host`'s own registry/graph tests. */
class FakeModule(
    override val instanceId: String,
    override val contract: ModuleContract,
) : Module {
    var loadCount = 0
        private set
    var processCount = 0
        private set
    var removeCount = 0
        private set
    var throwOnProcess = false

    /** Test-supplied behavior run on every [process] call, after the throw check. */
    var onProcessAction: ((ProcessContext) -> Unit)? = null

    override fun onLoad() {
        loadCount++
    }

    override fun process(context: ProcessContext) {
        processCount++
        if (throwOnProcess) error("FakeModule($instanceId) forced failure")
        onProcessAction?.invoke(context)
    }

    override fun onRemove() {
        removeCount++
    }

    companion object {
        fun withPorts(instanceId: String, ports: List<PortSpec>) = FakeModule(
            instanceId = instanceId,
            contract = ModuleContract(typeId = "fake", version = 1, ports = ports),
        )
    }
}