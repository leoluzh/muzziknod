package dev.muzziknod.modules.midisequencer

import dev.muzziknod.host.contract.Module
import dev.muzziknod.host.contract.testkit.ModuleContractComplianceTests

class MidiSequencerContractTest : ModuleContractComplianceTests() {
    override fun createModule(): Module = MidiSequencerModule("seq-1")
}