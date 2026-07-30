package dev.muzziknod.persistence.codec

import dev.muzziknod.host.contract.Module
import dev.muzziknod.modules.audioeffects.DistortionModule
import dev.muzziknod.persistence.model.ModuleSnapshot

class DistortionCodec : ModuleStateCodec {
    override val typeId: String = "distortion"

    override fun capture(module: Module): ModuleSnapshot {
        val distortion = module as DistortionModule
        return ModuleSnapshot(
            instanceId = distortion.instanceId,
            typeId = typeId,
            parameters = mapOf(
                "mix" to distortion.mix.value,
                "drive" to distortion.drive.value,
                "tone" to distortion.tone.value,
            ),
        )
    }

    override fun restore(instanceId: String, snapshot: ModuleSnapshot): Module {
        val distortion = DistortionModule(instanceId = instanceId)
        snapshot.parameters["mix"]?.let(distortion::setMix)
        snapshot.parameters["drive"]?.let(distortion::setDrive)
        snapshot.parameters["tone"]?.let(distortion::setTone)
        return distortion
    }
}
