# Audio Effects Modules — Public API (module-side, beyond `core-host`'s `Module` interface)

Per research.md "Live parameter control surface": `core-host`'s `Module`/
`ModuleContract`/`ProcessContext` are unchanged (FR-011). This is the
additional public Kotlin surface on each of the four concrete module
classes, in `modules/audio-effects` `commonMain`, package
`dev.muzziknod.modules.audioeffects`. Callers (tests today, a future
UI/host-embedding layer) hold a direct reference to the instance and call
these directly — there is no host-mediated command channel.

All setters clamp their argument to the parameter's declared
`ParameterSpec.range` (FR-013) and route it through a `ParameterSmoother`
(research.md "Parameter smoothing") rather than applying it instantly
(FR-005, FR-010).

```kotlin
class ReverbModule(
    override val instanceId: String,
    sampleRate: Int = 48_000,
) : Module {
    override val contract: ModuleContract = ModuleContract(
        typeId = "reverb",
        version = 1,
        ports = listOf(
            PortSpec(id = "in", direction = PortDirection.Input, type = PortType.Audio,
                sampleRate = sampleRate, bufferFormat = BufferFormat.Float32),
            PortSpec(id = "out", direction = PortDirection.Output, type = PortType.Audio,
                sampleRate = sampleRate, bufferFormat = BufferFormat.Float32),
        ),
        parameters = listOf(
            ParameterSpec(id = "mix", label = "Mix", range = 0.0..1.0, default = 0.5),
            ParameterSpec(id = "decayMs", label = "Decay Time", range = 50.0..5000.0, default = 1500.0),
            ParameterSpec(id = "roomSize", label = "Room Size", range = 0.0..1.0, default = 0.5),
        ),
    )

    fun setMix(value: Double)
    fun setDecayMs(value: Double)
    fun setRoomSize(value: Double)

    override fun onLoad()
    override fun process(context: ProcessContext)
    override fun onRemove()
}

class DelayModule(
    override val instanceId: String,
    sampleRate: Int = 48_000,
) : Module {
    override val contract: ModuleContract = ModuleContract(
        typeId = "delay",
        version = 1,
        ports = listOf(/* same in/out Audio shape as ReverbModule */),
        parameters = listOf(
            ParameterSpec(id = "mix", label = "Mix", range = 0.0..1.0, default = 0.5),
            ParameterSpec(id = "delayTimeMs", label = "Delay Time", range = 1.0..2000.0, default = 375.0),
            ParameterSpec(id = "feedback", label = "Feedback", range = 0.0..0.95, default = 0.3),
        ),
    )

    fun setMix(value: Double)
    fun setDelayTimeMs(value: Double)
    fun setFeedback(value: Double)

    override fun onLoad()
    override fun process(context: ProcessContext)
    override fun onRemove()
}

class DistortionModule(
    override val instanceId: String,
    sampleRate: Int = 48_000,
) : Module {
    override val contract: ModuleContract = ModuleContract(
        typeId = "distortion",
        version = 1,
        ports = listOf(/* same in/out Audio shape as ReverbModule */),
        parameters = listOf(
            ParameterSpec(id = "mix", label = "Mix", range = 0.0..1.0, default = 1.0),
            ParameterSpec(id = "drive", label = "Drive", range = 1.0..20.0, default = 4.0),
            ParameterSpec(id = "tone", label = "Tone", range = 200.0..12000.0, default = 6000.0),
        ),
    )

    fun setMix(value: Double)
    fun setDrive(value: Double)
    fun setTone(value: Double)

    override fun onLoad()
    override fun process(context: ProcessContext)
    override fun onRemove()
}

enum class EqBand { Low, Mid, High }

class EqModule(
    override val instanceId: String,
    sampleRate: Int = 48_000,
) : Module {
    override val contract: ModuleContract = ModuleContract(
        typeId = "eq",
        version = 1,
        ports = listOf(/* same in/out Audio shape as ReverbModule */),
        parameters = listOf(
            // one freqHz/gainDb/q triplet per EqBand — see data-model.md for exact ranges/defaults
        ),
    )

    fun setBandFrequency(band: EqBand, hz: Double)
    fun setBandGain(band: EqBand, db: Double)
    fun setBandQ(band: EqBand, q: Double)

    override fun onLoad()
    override fun process(context: ProcessContext)
    override fun onRemove()
}
```

## Behavioral contract (all four modules)

- `mix = 0.0` (100% dry) ⇒ `out` is sample-for-sample identical to `in`
  (FR-003, SC-001). `mix = 1.0` (100% wet) ⇒ `out` is exclusively the
  processed signal (FR-004, SC-002). `EqModule` has no `mix` parameter — see
  data-model.md; its own passthrough case is "all bands at 0 dB gain."
- Any setter call takes effect within the smoothing window (a handful of
  samples, well under one `process()` buffer), never instantly and never by
  interrupting in-flight processing (FR-005, FR-010, SC-003).
- Out-of-range setter arguments are clamped to the declared `ParameterSpec.range`,
  never thrown (FR-013).
- No input connected (`context.readAudio("in")` returns an empty buffer) ⇒
  `out` is silence, no error (FR-014).
- Delay/decay time parameters are converted from ms to samples using the
  module's own `sampleRate`, correct at any sample rate (FR-009).

## Contract test suite (mandatory per Constitution "Fluxo de Desenvolvimento")

Four small test classes — `ReverbContractTest`, `DelayContractTest`,
`DistortionContractTest`, `EqContractTest` — each subclass `core-host`'s
`ModuleContractComplianceTests` (same testkit `reference-modules/*` and
`midi-sequencer` use), providing their respective module via
`createModule()`. This proves each of the four types satisfies the unchanged
001 `Module` contract (unique port ids, `onLoad → process → onRemove`
doesn't throw, `process` only touches declared ports) — it does not cover
DSP correctness or wet/dry behavior, which belong to this feature's own
scope (`WetDryMixTest`, per-effect DSP tests, `ParameterSmoothingTest`,
`EffectsChainRoutingTest`).
