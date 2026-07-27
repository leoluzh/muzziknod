package dev.muzziknod.ui.controls

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import dev.muzziknod.modules.audioeffects.DelayModule
import dev.muzziknod.modules.audioeffects.DistortionModule
import dev.muzziknod.modules.audioeffects.EqBand
import dev.muzziknod.modules.audioeffects.EqModule
import dev.muzziknod.modules.audioeffects.ReverbModule
import dev.muzziknod.modules.midisequencer.MidiSequencerModule
import dev.muzziknod.ui.parameters.ParameterControl
import dev.muzziknod.ui.state.HostViewModel
import dev.muzziknod.ui.transport.TransportControls

/**
 * Renders the transport/parameter controls for a single loaded module instance, if its
 * type exposes any (US2). Each module's `StateFlow`s are typed per module (there's no
 * single uniform "parameter bag" contract — see [HostViewModel.moduleInstance]'s
 * doc), so this dispatches on `typeName` rather than a generic abstraction.
 *
 * [pendingRemoval] disables every control while the host has a deferred removal in
 * flight for this instance (FR-015).
 */
@Composable
fun ModuleControls(instanceId: String, typeName: String, viewModel: HostViewModel, pendingRemoval: Boolean) {
    val enabled = !pendingRemoval
    when (typeName) {
        "midi-sequencer" -> {
            val module = viewModel.moduleInstance<MidiSequencerModule>(instanceId) ?: return
            val transportState by module.transportState.collectAsState()
            TransportControls(transportState = transportState, onPlay = module::play, onStop = module::stop, enabled = enabled)
        }

        "delay" -> {
            val module = viewModel.moduleInstance<DelayModule>(instanceId) ?: return
            val specs = module.contract.parameters.associateBy { it.id }
            val mix by module.mix.collectAsState()
            val delayTimeMs by module.delayTimeMs.collectAsState()
            val feedback by module.feedback.collectAsState()
            Column {
                ParameterControl(specs.getValue("mix"), mix, module::setMix, enabled)
                ParameterControl(specs.getValue("delayTimeMs"), delayTimeMs, module::setDelayTimeMs, enabled)
                ParameterControl(specs.getValue("feedback"), feedback, module::setFeedback, enabled)
            }
        }

        "reverb" -> {
            val module = viewModel.moduleInstance<ReverbModule>(instanceId) ?: return
            val specs = module.contract.parameters.associateBy { it.id }
            val mix by module.mix.collectAsState()
            val decayMs by module.decayMs.collectAsState()
            val roomSize by module.roomSize.collectAsState()
            Column {
                ParameterControl(specs.getValue("mix"), mix, module::setMix, enabled)
                ParameterControl(specs.getValue("decayMs"), decayMs, module::setDecayMs, enabled)
                ParameterControl(specs.getValue("roomSize"), roomSize, module::setRoomSize, enabled)
            }
        }

        "distortion" -> {
            val module = viewModel.moduleInstance<DistortionModule>(instanceId) ?: return
            val specs = module.contract.parameters.associateBy { it.id }
            val mix by module.mix.collectAsState()
            val drive by module.drive.collectAsState()
            val tone by module.tone.collectAsState()
            Column {
                ParameterControl(specs.getValue("mix"), mix, module::setMix, enabled)
                ParameterControl(specs.getValue("drive"), drive, module::setDrive, enabled)
                ParameterControl(specs.getValue("tone"), tone, module::setTone, enabled)
            }
        }

        "eq" -> {
            val module = viewModel.moduleInstance<EqModule>(instanceId) ?: return
            val specs = module.contract.parameters.associateBy { it.id }
            Column {
                for (band in EqBand.entries) {
                    val prefix = band.name.lowercase()
                    val freqHz by module.bandFrequency(band).collectAsState()
                    val gainDb by module.bandGain(band).collectAsState()
                    val q by module.bandQ(band).collectAsState()
                    ParameterControl(specs.getValue("$prefix.freqHz"), freqHz, { module.setBandFrequency(band, it) }, enabled)
                    ParameterControl(specs.getValue("$prefix.gainDb"), gainDb, { module.setBandGain(band, it) }, enabled)
                    ParameterControl(specs.getValue("$prefix.q"), q, { module.setBandQ(band, it) }, enabled)
                }
            }
        }
    }
}
