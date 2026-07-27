package dev.muzziknod.modules.audioeffects

import dev.muzziknod.host.contract.Module
import dev.muzziknod.host.contract.testkit.ModuleContractComplianceTests

class DistortionContractTest : ModuleContractComplianceTests() {
    override fun createModule(): Module = DistortionModule(instanceId = "distortion-1")
}
