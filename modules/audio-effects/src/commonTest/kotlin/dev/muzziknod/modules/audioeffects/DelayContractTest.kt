package dev.muzziknod.modules.audioeffects

import dev.muzziknod.host.contract.Module
import dev.muzziknod.host.contract.testkit.ModuleContractComplianceTests

class DelayContractTest : ModuleContractComplianceTests() {
    override fun createModule(): Module = DelayModule(instanceId = "delay-1")
}
