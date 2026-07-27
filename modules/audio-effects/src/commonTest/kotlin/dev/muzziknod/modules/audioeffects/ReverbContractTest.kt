package dev.muzziknod.modules.audioeffects

import dev.muzziknod.host.contract.Module
import dev.muzziknod.host.contract.testkit.ModuleContractComplianceTests

class ReverbContractTest : ModuleContractComplianceTests() {
    override fun createModule(): Module = ReverbModule(instanceId = "reverb-1")
}
