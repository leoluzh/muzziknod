package dev.muzziknod.refmodules.midilogger

import dev.muzziknod.host.contract.Module
import dev.muzziknod.host.contract.testkit.ModuleContractComplianceTests

class MidiLoggerContractTest : ModuleContractComplianceTests() {
    override fun createModule(): Module = MidiLoggerModule(instanceId = "midi-logger-test")
}