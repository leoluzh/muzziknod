# MidiSequencerModule Public API (module-side, beyond `core-host`'s `Module` interface)

Per research.md "Pattern/transport control surface": `core-host`'s `Module`/
`ModuleContract`/`ProcessContext` are unchanged (FR-008). This is the
additional public Kotlin surface on the concrete `MidiSequencerModule` class,
in `modules/midi-sequencer` `commonMain`, package
`dev.muzziknod.modules.midisequencer`. Callers (tests today, a future
UI/host-embedding layer) hold a direct reference to the instance and call
these directly — there is no host-mediated command channel.

```kotlin
class MidiSequencerModule(
    override val instanceId: String,
) : Module {
    override val contract: ModuleContract = ModuleContract(
        typeId = "midi-sequencer",
        version = 1,
        ports = listOf(
            PortSpec(id = "out", direction = PortDirection.Output, type = PortType.Midi),
        ),
    )

    // -- Pattern editing (FR-001, FR-002, FR-006) --
    fun setLength(steps: Int)
    fun setBpm(bpm: Int)
    fun setStep(index: Int, notes: List<NoteEvent>)
    fun clearStep(index: Int)

    // -- Transport (FR-003, FR-005, FR-007) --
    fun play()
    fun stop()
    val isPlaying: Boolean
    val currentStep: Int

    // -- Module interface (unchanged from core-host) --
    override fun onLoad()
    override fun process(context: ProcessContext)
    override fun onRemove()
}
```

## Behavioral contract

- `setStep`/`clearStep`/`setLength`/`setBpm` are safe to call whether
  `isPlaying` is `true` or `false` (FR-006). A change to a step that
  `currentStep` has already passed in the current loop iteration does not
  retroactively re-emit; it takes effect the next time that step index is
  reached.
- `play()` while already playing is a no-op (idempotent). `stop()` while
  already stopped is a no-op.
- `stop()` always flushes pending note-offs for every currently sustained
  note on the very next `process()` call, even if `stop()` is called multiple
  times in a row or the module is immediately removed afterward (FR-007,
  Edge Cases — mid-removal note-off).
- `process(context)` behavior per cycle:
  1. If not playing and no pending note-offs to flush: no-op, no writes.
  2. Otherwise: build the MIDI event list for this cycle (note-offs for
     expired `sustainedNotes`, note-ons for `pattern.steps[currentStep]` if
     playing), write it via `context.writeMidi("out", events)`, update
     `sustainedNotes`, and — only if still playing — advance `currentStep`
     (wrapping per FR-005).
- `setLength` reducing below `currentStep` wraps `currentStep` into the new
  valid range immediately (FR-010), it does not wait for the next cycle.

## Contract test suite (mandatory per Constitution "Fluxo de Desenvolvimento")

`MidiSequencerContractTest` subclasses `core-host`'s
`ModuleContractComplianceTests` (same testkit `reference-modules/*` use),
providing `MidiSequencerModule` via `createModule()`. This proves the module
still satisfies the unchanged 001 `Module` contract (unique port ids,
`onLoad → process → onRemove` doesn't throw, `process` only touches declared
ports) — it does not cover pattern/transport behavior, which is this
feature's own scope (`PatternPlaybackTest`, `PatternEditingTest`,
`RoutingIntegrationTest`).