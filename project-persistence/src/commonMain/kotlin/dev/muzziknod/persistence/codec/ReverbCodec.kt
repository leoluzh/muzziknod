package dev.muzziknod.persistence.codec

import dev.muzziknod.host.contract.Module
import dev.muzziknod.modules.audioeffects.ReverbModule
import dev.muzziknod.persistence.model.ModuleSnapshot

class ReverbCodec : ModuleStateCodec {
    override val typeId: String = "reverb"

    override fun capture(module: Module): ModuleSnapshot {
        val reverb = module as ReverbModule
        return ModuleSnapshot(
            instanceId = reverb.instanceId,
            typeId = typeId,
            parameters = mapOf(
                "mix" to reverb.mix.value,
                "decayMs" to reverb.decayMs.value,
                "roomSize" to reverb.roomSize.value,
            ),
        )
    }

    override fun restore(instanceId: String, snapshot: ModuleSnapshot): Module {
        val reverb = ReverbModule(instanceId = instanceId)
        snapshot.parameters["mix"]?.let(reverb::setMix)
        snapshot.parameters["decayMs"]?.let(reverb::setDecayMs)
        snapshot.parameters["roomSize"]?.let(reverb::setRoomSize)
        return reverb
    }
}
