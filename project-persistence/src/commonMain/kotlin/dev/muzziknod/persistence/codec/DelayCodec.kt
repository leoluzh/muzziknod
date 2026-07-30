package dev.muzziknod.persistence.codec

import dev.muzziknod.host.contract.Module
import dev.muzziknod.modules.audioeffects.DelayModule
import dev.muzziknod.persistence.model.ModuleSnapshot

class DelayCodec : ModuleStateCodec {
    override val typeId: String = "delay"

    override fun capture(module: Module): ModuleSnapshot {
        val delay = module as DelayModule
        return ModuleSnapshot(
            instanceId = delay.instanceId,
            typeId = typeId,
            parameters = mapOf(
                "mix" to delay.mix.value,
                "delayTimeMs" to delay.delayTimeMs.value,
                "feedback" to delay.feedback.value,
            ),
        )
    }

    override fun restore(instanceId: String, snapshot: ModuleSnapshot): Module {
        val delay = DelayModule(instanceId = instanceId)
        snapshot.parameters["mix"]?.let(delay::setMix)
        snapshot.parameters["delayTimeMs"]?.let(delay::setDelayTimeMs)
        snapshot.parameters["feedback"]?.let(delay::setFeedback)
        return delay
    }
}
