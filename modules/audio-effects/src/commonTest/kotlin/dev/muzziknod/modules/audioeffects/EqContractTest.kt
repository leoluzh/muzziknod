package dev.muzziknod.modules.audioeffects

import dev.muzziknod.host.contract.Module
import dev.muzziknod.host.contract.testkit.ModuleContractComplianceTests

class EqContractTest : ModuleContractComplianceTests() {
    override fun createModule(): Module = EqModule(instanceId = "eq-1")
}
