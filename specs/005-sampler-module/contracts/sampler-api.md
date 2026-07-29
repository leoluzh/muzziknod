# Sampler Module — Public API (module-side, beyond `core-host`'s `Module` interface)

Per research.md "Live mapping/loading control surface": `core-host`'s
`Module`/`ModuleContract`/`ProcessContext` are unchanged (mirrors 003
FR-011). This is the additional public Kotlin surface on `SamplerModule`, in
`modules/sampler` `commonMain`, package `dev.muzziknod.modules.sampler`.
Callers (tests today, a future UI/host-embedding layer) hold a direct
reference to the instance and call these directly — there is no
host-mediated command channel.

```kotlin
enum class LoopMode { OneShot, Loop }

data class Sample internal constructor(
    val id: String,
    val data: FloatArray,
    val sourceSampleRate: Int,
)

data class SampleZone(
    val sample: Sample,
    val rootNote: Int,
    val lowNote: Int = 0,
    val highNote: Int = 127,
    val gain: Double = 1.0,
    val loopMode: LoopMode = LoopMode.OneShot,
)

sealed class SampleLoadResult {
    data class Loaded(val zone: SampleZone) : SampleLoadResult()
    data class Failed(val reason: String) : SampleLoadResult()
}

class SamplerModule(
    override val instanceId: String,
    private val sampleRate: Int = 48_000,
    private val maxVoices: Int = 32,
) : Module {
    override val contract: ModuleContract = ModuleContract(
        typeId = "sampler",
        version = 1,
        ports = listOf(
            PortSpec(id = "in", direction = PortDirection.Input, type = PortType.Midi),
            PortSpec(id = "out", direction = PortDirection.Output, type = PortType.Audio,
                sampleRate = sampleRate, bufferFormat = BufferFormat.Float32),
        ),
        // No ParameterSpec entries — mapping is via loadSample()/unloadSample(),
        // not host-driven parameters (research.md "Live mapping/loading control surface").
    )

    /** Not-real-time: decodes [bytes], resamples to [sampleRate], never called from process(). */
    fun loadSample(
        bytes: ByteArray,
        id: String,
        rootNote: Int,
        lowNote: Int = 0,
        highNote: Int = 127,
        gain: Double = 1.0,
        loopMode: LoopMode = LoopMode.OneShot,
    ): SampleLoadResult

    fun unloadSample(zone: SampleZone)

    val zones: List<SampleZone>

    override fun onLoad()
    override fun process(context: ProcessContext)
    override fun onRemove()
}
```

## Behavioral contract

- **Trigger (FR-003, FR-006)**: a `MidiEvent` read from the `"in"` port with
  status `0x90` (note-on) and `data2 > 0` (velocity) looks up the first
  `SampleZone` whose `[lowNote, highNote]` contains `data1` (the note). If
  found, a `Voice` is triggered with `pitchRatio = 2.0.pow((data1 -
  zone.rootNote) / 12.0)` and `gain = zone.gain * (data2 / 127.0)`. If no
  zone covers the note, the event is silently ignored (FR-011).
- **Velocity-zero note-on (FR-012)**: a note-on with `data2 == 0` is treated
  identically to a note-off for that note.
- **Note-off (FR-003, FR-005)**: a `MidiEvent` with status `0x80` (or `0x90`
  with velocity 0) releases matching `Loop`-mode voices for that note (starts
  their fade-out); matching `OneShot` voices are left untouched and keep
  playing to completion (User Story 1 acceptance scenario 3).
- **Polyphony (FR-008, FR-009)**: up to `maxVoices` voices play concurrently.
  Triggering beyond that count steals the voice with the oldest
  `triggerOrder`, fading it out over a short (~32-sample) ramp rather than
  cutting it instantly (no audible click, spec SC-004).
- **Gain (FR-004, FR-007)**: output sample = sum over active voices of
  `interpolatedSample * voice.gain * voice.fadeGain`.
- **No sample mapped (FR-011, Edge Case)**: a note-on for a note with no
  covering zone — or when `zones` is empty — produces no voice and no error.
- **Load failure (FR-002)**: `loadSample()` returns `SampleLoadResult.Failed`
  for a missing, unreadable, or unsupported file/format — never throws,
  never leaves a partially-registered `SampleZone` in `zones`.
- **Sample-rate/bit-depth normalization (FR-001, Edge Case)**: any
  successfully loaded sample's `data` is already mono PCM at this module's
  `sampleRate`, regardless of the source file's original rate/depth/channel
  count — callers and `Voice` playback never need to know the source format.
- **No input connected**: if `context.readMidi("in")` returns an empty list,
  `process()` still renders whatever voices are already active (sustain/
  release/one-shot completion continues); it does not require new MIDI
  input to keep producing audio.

## Contract test suite (mandatory per Constitution "Fluxo de Desenvolvimento")

`SamplerContractTest` subclasses `core-host`'s `ModuleContractComplianceTests`
testkit (same as every other module), providing `SamplerModule` via
`createModule()`. This proves the module satisfies the unchanged 001 `Module`
contract (unique port ids, `onLoad -> process -> onRemove` doesn't throw,
`process` only touches declared ports) — it does not cover sampler-specific
DSP/voice behavior, which belongs to this feature's own scope
(`PitchRatioTest`, `VoicePoolTest`, `OneShotVsLoopTest`, `VelocityGainTest`,
plus the `jvmTest` decoder fixtures).
