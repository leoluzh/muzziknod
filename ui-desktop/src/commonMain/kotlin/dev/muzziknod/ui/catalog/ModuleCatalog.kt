package dev.muzziknod.ui.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.muzziknod.modules.audioeffects.DelayModule
import dev.muzziknod.modules.audioeffects.DistortionModule
import dev.muzziknod.modules.audioeffects.EqModule
import dev.muzziknod.modules.audioeffects.ReverbModule
import dev.muzziknod.modules.midisequencer.MidiSequencerModule
import dev.muzziknod.refmodules.midigenerator.MidiGeneratorModule
import dev.muzziknod.refmodules.midilogger.MidiLoggerModule
import dev.muzziknod.refmodules.oscillator.OscillatorModule
import dev.muzziknod.ui.state.ModuleCatalogEntry
import kotlin.random.Random

/**
 * The module types available for the user to add to the host (US3; data-model.md).
 * Sampler/synth from the original feature description don't exist as modules yet, so
 * they're out of scope here (spec Assumptions) until they're specified.
 */
fun defaultModuleCatalog(): List<ModuleCatalogEntry> {
    fun newId(prefix: String) = "$prefix-${Random.nextInt(100_000, 999_999)}"
    return listOf(
        ModuleCatalogEntry("oscillator") { OscillatorModule(instanceId = newId("oscillator")) },
        ModuleCatalogEntry("midi-generator") { MidiGeneratorModule(instanceId = newId("midi-generator")) },
        ModuleCatalogEntry("midi-logger") { MidiLoggerModule(instanceId = newId("midi-logger")) },
        ModuleCatalogEntry("midi-sequencer") { MidiSequencerModule(instanceId = newId("midi-sequencer")) },
        ModuleCatalogEntry("delay") { DelayModule(instanceId = newId("delay")) },
        ModuleCatalogEntry("reverb") { ReverbModule(instanceId = newId("reverb")) },
        ModuleCatalogEntry("distortion") { DistortionModule(instanceId = newId("distortion")) },
        ModuleCatalogEntry("eq") { EqModule(instanceId = newId("eq")) },
    )
}

/**
 * Lists the available module types and lets the user add one to the host
 * (contracts/ui-composables-contract.md; US3 AC1).
 */
@Composable
fun ModuleCatalog(entries: List<ModuleCatalogEntry>, onAdd: (ModuleCatalogEntry) -> Unit) {
    Column(modifier = Modifier.testTag("module-catalog")) {
        for (entry in entries) {
            Text(
                text = entry.typeName,
                modifier = Modifier
                    .testTag("catalog-entry-${entry.typeName}")
                    .clickable { onAdd(entry) },
            )
        }
    }
}
