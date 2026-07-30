package dev.muzziknod.persistence.codec

import dev.muzziknod.host.contract.Module
import dev.muzziknod.modules.audioeffects.EqBand
import dev.muzziknod.modules.audioeffects.EqModule
import dev.muzziknod.persistence.model.ModuleSnapshot

class EqCodec : ModuleStateCodec {
    override val typeId: String = "eq"

    override fun capture(module: Module): ModuleSnapshot {
        val eq = module as EqModule
        val parameters = EqBand.entries.flatMap { band ->
            val prefix = band.name.lowercase()
            listOf(
                "$prefix.freqHz" to eq.bandFrequency(band).value,
                "$prefix.gainDb" to eq.bandGain(band).value,
                "$prefix.q" to eq.bandQ(band).value,
            )
        }.toMap()
        return ModuleSnapshot(instanceId = eq.instanceId, typeId = typeId, parameters = parameters)
    }

    override fun restore(instanceId: String, snapshot: ModuleSnapshot): Module {
        val eq = EqModule(instanceId = instanceId)
        for (band in EqBand.entries) {
            val prefix = band.name.lowercase()
            snapshot.parameters["$prefix.freqHz"]?.let { eq.setBandFrequency(band, it) }
            snapshot.parameters["$prefix.gainDb"]?.let { eq.setBandGain(band, it) }
            snapshot.parameters["$prefix.q"]?.let { eq.setBandQ(band, it) }
        }
        return eq
    }
}
