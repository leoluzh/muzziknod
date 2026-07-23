package dev.muzziknod.refmodules.midigenerator

import dev.muzziknod.host.contract.Module
import dev.muzziknod.host.contract.testkit.ModuleContractComplianceTests

class MidiGeneratorContractTest : ModuleContractComplianceTests() {
    override fun createModule(): Module = MidiGeneratorModule(instanceId = "midi-gen-test")
}