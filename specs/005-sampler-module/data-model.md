# Phase 1 Data Model: Sampler Module

All types live in `commonMain` (`dev.muzziknod.modules.sampler`) unless noted.

## `DecodedAudio` (decoder boundary result)

Output of the platform `SampleDecoder` actual — already mono and already
resampled to the host's operating sample rate (research.md "Sample-rate
conversion at load time").

| Field | Type | Notes |
|---|---|---|
| `samples` | `FloatArray` | Mono PCM, normalized to `-1.0..1.0`, at the host's sample rate |

## `Sample`

A loaded audio asset, ready for playback.

| Field | Type | Notes |
|---|---|---|
| `id` | `String` | Caller-supplied or derived from source path; used only for lookup/removal, not host-visible |
| `data` | `FloatArray` | Mono PCM at host sample rate (from `DecodedAudio`) |
| `sourceSampleRate` | `Int` | Original file sample rate, retained for diagnostics only — playback always uses `data` as-is |

Validation: `data` must be non-empty (FR-002 — a decode failure never
produces a `Sample`, it produces a reported error instead; see
`contracts/sampler-api.md`).

## `LoopMode`

```kotlin
enum class LoopMode { OneShot, Loop }
```

- `OneShot`: plays `data` once to completion regardless of note-off (spec
  User Story 1, acceptance scenario 3).
- `Loop`: wraps `data` from its end back to index 0 while the note is held;
  releases (fades out) on note-off (spec FR-005, User Story 3 acceptance
  scenario 3).

## `SampleZone` (Sample Mapping)

The association between a loaded `Sample` and how it responds to MIDI notes.

| Field | Type | Notes | Validation |
|---|---|---|---|
| `sample` | `Sample` | The underlying audio | non-null |
| `rootNote` | `Int` | MIDI note number (0-127) that plays `sample` at its original, unshifted pitch | `0..127` |
| `lowNote` | `Int` | Lowest MIDI note this zone responds to (default `0`) | `0..127`, `<= highNote` |
| `highNote` | `Int` | Highest MIDI note this zone responds to (default `127`) | `0..127`, `>= lowNote` |
| `gain` | `Double` | Per-sample linear gain multiplier applied on top of velocity scaling (default `1.0`) | `>= 0.0` |
| `loopMode` | `LoopMode` | One-shot or looped playback (default `OneShot`) | — |

A note-on is dispatched to the first `SampleZone` whose `[lowNote,
highNote]` range contains the note (FR-006, FR-011). If no zone covers the
note, the event is ignored (FR-011, Edge Case "no sample loaded").

## `Voice`

One active (or free) playback slot inside the fixed-size `VoicePool`. Mutable,
reused across triggers — never allocated inside `process()` (Constitution
III).

| Field | Type | Notes |
|---|---|---|
| `state` | `VoiceState` (`Free`, `Playing`, `Releasing`) | `Releasing` covers both the voice-steal fade and the loop-release fade (research.md) |
| `zone` | `SampleZone?` | The zone currently sounding; `null` when `Free` |
| `position` | `Double` | Fractional read index into `zone.sample.data`; advances by `pitchRatio` per output sample |
| `pitchRatio` | `Double` | `2.0.pow((note - zone.rootNote) / 12.0)`, fixed for the life of the voice |
| `gain` | `Double` | `zone.gain * (velocity / 127.0)`, fixed for the life of the voice |
| `note` | `Int` | The MIDI note that triggered this voice — needed to match the correct note-off |
| `triggerOrder` | `Long` | Monotonic counter value at trigger time; used for oldest-first voice stealing |
| `fadeGain` | `Double` | `1.0` while `Playing`; ramps `1.0 -> 0.0` over the release window while `Releasing` |

State transitions:

```
Free --(note-on, zone found)--> Playing
Playing --(note-off, Loop mode)--> Releasing
Playing --(steal)--> Releasing
Releasing --(fade complete)--> Free
Playing --(OneShot reaches end of data)--> Free
```

## `VoicePool`

| Field | Type | Notes |
|---|---|---|
| `voices` | `Array<Voice>` | Fixed size = `maxVoices` (constructor param, default `32`); allocated once in `onLoad()` |
| `triggerCounter` | `Long` | Incremented on every `trigger()` call, source of `Voice.triggerOrder` |

Operations (all alloc-free, called from `process()`):

- `trigger(zone, note, velocity)`: claims a `Free` voice if one exists;
  otherwise steals the `Playing`/`Releasing` voice with the lowest
  `triggerOrder` (oldest) by transitioning it to `Releasing` first — the
  steal only *starts* the fade; the actual new trigger claims the next
  available `Free` voice on a subsequent audio sample once the fade
  completes, OR (chosen implementation shape) claims the stolen voice
  immediately and cross-fades: outgoing fade-out and incoming new voice both
  mix into the buffer for the short overlap window. See
  `contracts/sampler-api.md` for the exact mixing rule.
- `release(note)`: transitions all `Playing` voices matching `note` with
  `zone.loopMode == Loop` into `Releasing`. `OneShot` voices matching `note`
  are left untouched (FR ignoring note-off for one-shot).
- `renderNextSample()`: advances every non-`Free` voice by one sample
  (linear-interpolated read + fade + pitch-ratio position advance) and
  returns the summed mono output for that sample.

## Contract additions (no `core-host` changes)

`SamplerModule` (implements `core-host`'s `Module`, unchanged interface):

| Member | Kind | Notes |
|---|---|---|
| `contract` | `ModuleContract` | `typeId = "sampler"`, one Midi input port (`"in"`), one Audio output port (`"out"`) |
| `loadSample(bytes, id, rootNote, lowNote, highNote, gain, loopMode): SampleZone` | public method | Not-real-time; decodes via `SampleDecoder`, throws/returns a reported error on failure (FR-002) — never called from `process()` |
| `unloadSample(zone: SampleZone)` | public method | Removes a zone; any currently-sounding voices for it finish/fade naturally |
| `zones` | `List<SampleZone>` (read-only) | Current mapping, for host/UI introspection |
| `maxVoices` | constructor param, `Int = 32` | Sizes the `VoicePool` |
