package dev.muzziknod.modules.sampler

import dev.muzziknod.host.contract.Module
import dev.muzziknod.host.contract.testkit.ModuleContractComplianceTests

class SamplerContractTest : ModuleContractComplianceTests() {
    override fun createModule(): Module = SamplerModule(instanceId = "sampler-1")
}
