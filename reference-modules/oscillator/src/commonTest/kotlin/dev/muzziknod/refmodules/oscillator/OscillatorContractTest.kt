package dev.muzziknod.refmodules.oscillator

import dev.muzziknod.host.contract.Module
import dev.muzziknod.host.contract.testkit.ModuleContractComplianceTests

class OscillatorContractTest : ModuleContractComplianceTests() {
    override fun createModule(): Module = OscillatorModule(instanceId = "osc-test")
}
