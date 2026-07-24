# Phase 1 Data Model: Módulo Sequenciador MIDI

## NoteEvent

Single note occupying a step.

| Field | Type | Notes |
|---|---|---|
| `note` | `Int` | MIDI note number (0-127) |
| `velocity` | `Int` | 1-127 (0 is treated as note-off in MIDI, not a valid step note) |
| `gateSteps` | `Int` | Duration in steps the note stays sustained before its note-off (≥1) |

## Step

| Field | Type | Notes |
|---|---|---|
| `index` | `Int` | Position within the pattern, `0 until pattern.length` |
| `notes` | `List<NoteEvent>` | Zero or more — empty step emits nothing (Edge Cases) |

## Pattern

| Field | Type | Notes |
|---|---|---|
| `length` | `Int` | Number of steps (default 16 — FR-002) |
| `bpm` | `Int` | Declared tempo metadata (default 120 — FR-002; see research.md "Step-advance timing model" for why this doesn't drive cycle pacing in this feature) |
| `steps` | `List<Step>` | Size == `length`; edited via `MidiSequencerModule.setStep`/`setLength` |

**Validation rules**:
- `length >= 1`.
- `bpm` within a reasonable positive range (e.g. 20-300); out-of-range values
  are clamped, not rejected (no user-facing "invalid BPM" flow needed for a
  metadata-only field — see research.md).
- Reducing `length` below the current playback position wraps the position
  into the new valid range (FR-010, Edge Cases) — never throws.

## Transport

| Field | Type | Notes |
|---|---|---|
| `isPlaying` | `Boolean` | `false` initially — module starts stopped |
| `currentStep` | `Int` | Index of the step last processed; wraps to `0` after `pattern.length - 1` (FR-005) |
| `sustainedNotes` | `Set<NoteEvent>` (internal) | Notes currently sounding, tracked so `stop()` can flush note-offs (FR-007) |

**State transitions**:
- `Stopped → Playing` (`play()`): resumes/starts advancing `currentStep` one
  step per `process()` call.
- `Playing → Stopped` (`stop()`): queues note-off for every entry in
  `sustainedNotes`, flushed on the next `process()` call, then transport goes
  idle (no further step advance) — see research.md "Pending note-off flush".
- `Playing → Playing`, step advance: on each `process()` call, emit note-on
  for the current step's `notes` (and register them in `sustainedNotes`),
  emit note-off for any `sustainedNotes` entries whose `gateSteps` has
  elapsed, then advance `currentStep` (looping per FR-005).

## MidiSequencerModule (host-facing)

Implements `core-host`'s `Module` interface unchanged (FR-008):

| Field | Type | Notes |
|---|---|---|
| `instanceId` | `String` | Per `Module` interface |
| `contract` | `ModuleContract` | `typeId = "midi-sequencer"`, one `Midi`/`Output` `PortSpec`, no input ports |
| `pattern` | `Pattern` | Mutable, edited live (FR-006) |
| `transport` | `Transport` | Internal playback state |

Public API beyond the `Module` interface (research.md "Pattern/transport
control surface"), documented fully in
[`contracts/midi-sequencer-api.md`](./contracts/midi-sequencer-api.md).

## Relationships

```text
MidiSequencerModule 1 ── 1 Pattern
MidiSequencerModule 1 ── 1 Transport
Pattern              1 ── * Step
Step                  1 ── * NoteEvent
Transport             * ── * NoteEvent   (sustainedNotes, transient)
```