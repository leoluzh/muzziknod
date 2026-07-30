package dev.muzziknod.persistence.codec

import dev.muzziknod.host.contract.Module
import dev.muzziknod.modules.sampler.LoopMode
import dev.muzziknod.modules.sampler.SamplerModule
import dev.muzziknod.persistence.model.ModuleSnapshot
import dev.muzziknod.persistence.model.SampleZoneSnapshot
import dev.muzziknod.persistence.model.SamplerData
import dev.muzziknod.persistence.readFileBytes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

class SamplerCodec : ModuleStateCodec {
    override val typeId: String = "sampler"

    /**
     * Source paths that couldn't be read during the most recent [restore] call
     * (contracts/module-state-codec.md obligation 3) — [dev.muzziknod.persistence.ProjectReader]
     * reads this immediately after calling [restore] to append `LoadWarning.MissingSampleFile`
     * entries (US3). Reset at the start of every [restore] call.
     */
    var lastRestoreMissingPaths: List<String> = emptyList()
        private set

    override fun capture(module: Module): ModuleSnapshot {
        val sampler = module as SamplerModule
        val zones = sampler.zones.map { zone ->
            SampleZoneSnapshot(
                sourcePath = zone.sourcePath,
                sampleId = zone.sample.id,
                rootNote = zone.rootNote,
                lowNote = zone.lowNote,
                highNote = zone.highNote,
                gain = zone.gain,
                loopMode = zone.loopMode.name,
            )
        }
        return ModuleSnapshot(
            instanceId = sampler.instanceId,
            typeId = typeId,
            moduleData = Json.encodeToJsonElement(SamplerData(zones)),
        )
    }

    override fun restore(instanceId: String, snapshot: ModuleSnapshot): Module {
        val sampler = SamplerModule(instanceId = instanceId)
        val missing = mutableListOf<String>()
        val data = snapshot.moduleData?.let { Json.decodeFromJsonElement<SamplerData>(it) }
        if (data != null) {
            for (zone in data.zones) {
                val path = zone.sourcePath
                if (path == null) continue
                val bytes = try {
                    readFileBytes(path)
                } catch (e: Exception) {
                    missing += path
                    continue
                }
                sampler.loadSample(
                    bytes = bytes,
                    id = zone.sampleId,
                    rootNote = zone.rootNote,
                    lowNote = zone.lowNote,
                    highNote = zone.highNote,
                    gain = zone.gain,
                    loopMode = LoopMode.valueOf(zone.loopMode),
                    sourcePath = path,
                )
            }
        }
        lastRestoreMissingPaths = missing
        return sampler
    }
}
