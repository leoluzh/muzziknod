# Implementation Plan: Sampler Module

**Branch**: `005-sampler-module` | **Date**: 2026-07-28 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/005-sampler-module/spec.md`

## Summary

A new `sampler` module type on top of the Core Host Modular (001) contract: one
MIDI input port, one mono audio output port. Loads WAV/AIFF sample files
(decoded and resampled to the host's operating sample rate at load time via a
JVM `AudioSystem`-backed decoder behind an `expect`/`actual` boundary), maps
each loaded sample to a root note with a configurable gain and one-shot/loop
playback mode, and plays them back polyphonically in response to MIDI
note-on/note-off, transposing pitch per note via linear-interpolated
resampling (`ratio = 2^((note - rootNote)/12)`) — the same "basic, textbook
algorithm, no external DSP library" precedent 003-audio-effects set. A fixed
`VoicePool` (allocated once in `onLoad()`) provides polyphony and
oldest-voice-first stealing with a short linear fade to avoid clicks, mirroring
003's `ParameterSmoother` ramp-based approach to avoiding audible discontinuities.

## Technical Context

**Language/Version**: Kotlin 2.4.10, JVM target Java 26 (same toolchain as
001-core-host, 002-midi-sequencer, 003-audio-effects)

**Primary Dependencies**: `core-host`'s `dev.muzziknod.host.contract` (Module,
ModuleContract, PortSpec, ParameterSpec, AudioBuffer, MidiEvent,
ProcessContext) as a `commonMain` dependency, same as every other module;
`javax.sound.sampled` (JDK-bundled, no new Gradle dependency) for WAV/AIFF
decoding on the JVM actual; `kotlin.test` + JUnit5 platform for tests, same as
001/002/003. No coroutines-based audio path, no external DSP/codec library
(Constitution VII; research.md "Dependencies").

**Storage**: Local filesystem only — sample files are read from disk paths
supplied by the caller at `loadSample()` time; no project/preset persistence
in this feature (spec Assumptions; matches 001/002/003's own no-persistence
scope).

**Testing**: `kotlin.test` on `commonTest` (voice allocation, pitch ratio,
mixing, stealing — pure Kotlin, no file I/O) and `jvmTest` (real WAV/AIFF
fixture decoding through the `SampleDecoder` actual). Contract test mandatory
(Constitution "Fluxo de Desenvolvimento"): `SamplerContractTest` subclasses
`core-host`'s `ModuleContractComplianceTests` testkit, same pattern as every
existing module.

**Target Platform**: Desktop JVM (Java 26). This is the first product module
to need a real `commonMain`/`jvmMain` `expect`/`actual` split (Constitution
IV) — file decoding is inherently platform-specific (`javax.sound.sampled` on
JVM desktop; a future Android target would need its own `actual`), unlike
001-003's pure-Kotlin-only DSP which needed no platform boundary yet.

**Project Type**: New Gradle module in the existing Kotlin Multiplatform
multi-module project — a **product** module (per spec, not reference
scaffolding), so it lives under `modules/`, alongside `modules/midi-sequencer`
and `modules/audio-effects` (research.md "Module placement").

**Performance Goals**: No real-time audio callback in this feature — same as
001-003, `process()` stays alloc-free per cycle (the `Voice` array, output
buffer, and per-voice working state are all allocated once in `onLoad()`), in
preparation for a future real-time driver (Constitution III). Sample
*loading* (`loadSample()`) is explicitly a not-real-time, control-plane call —
it may allocate and block on file I/O, the same way `onLoad()` itself is
allowed to (Constitution III only binds the sample-accurate `process()` hot
path).

**Constraints**:
- Zero changes to `core-host`'s `Module`/`ModuleContract`/`ProcessContext`
  (mirrors 003 FR-011) — sample loading/mapping is an additional public
  Kotlin API on `SamplerModule`, called directly by whoever holds the
  instance, same pattern 002/003 established (research.md "Live parameter
  control surface").
- No object allocation/blocking I/O/GC-retainable locks inside `process()`
  (Constitution III) — file decoding, resampling-to-host-rate, and `Voice`
  array sizing all happen in `loadSample()`/`onLoad()`, never per audio
  cycle.
- Platform-specific decoding isolated behind `expect fun decodeSample(bytes:
  ByteArray): DecodedAudio` in `commonMain`, `actual` in `jvmMain` using
  `javax.sound.sampled.AudioSystem` (Constitution IV).
- Real pitch-shifting/mixing DSP ships now, in pure Kotlin (linear
  interpolation resampling), per the 003 "DSP scope" precedent — nothing in
  the codebase yet drives `process()` from an actual real-time callback, so
  Constitution III's native-bridge requirement doesn't bind here (YAGNI).

**Scale/Scope**: One module type (`sampler`); polyphony of at least 16
concurrent voices without glitches (spec SC-003), configurable maximum voice
count with a default in the 16-32 range (spec Assumptions); sample loading
supports WAV and AIFF at 16/24-bit and 32-bit float, 44.1/48/96kHz (spec
FR-001).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Check | Result |
|---|---|---|
| I. Modularidade em Primeiro Lugar | Self-contained module behind the existing contract; host (`core-host`) is untouched | PASS |
| II. Kotlin + Java 26 | Same Kotlin/JVM 26 toolchain as 001-003, no new language | PASS |
| III. Real-Time vs Not-Real-Time | `process()` alloc-free by design (Voice array/output buffer pre-allocated in `onLoad()`); file decoding and resampling-to-host-rate happen only in `loadSample()`, a not-real-time control-plane call, never in `process()`; real pitch/mix DSP runs in pure Kotlin because nothing yet drives `process()` from an actual real-time callback (research.md "DSP scope", carried over from 003) | PASS |
| IV. Portabilidade via KMP | First product module with a genuine `commonMain`/`jvmMain` `expect`/`actual` split — file decoding isolated behind `SampleDecoder`, everything else (Voice, VoicePool, SamplerModule, pitch math) stays 100% `commonMain` | PASS |
| V. UI Declarativa Desacoplada | No UI in this feature (spec Assumptions); module exposes a plain Kotlin API (`loadSample`, `unloadSample`) a future UI can consume, plus `StateFlow` observability mirroring 002/003 | PASS (N/A) |
| VI. Contratos Explícitos | Reuses 001's `ModuleContract`/`Module`/`ProcessContext`/`MidiEvent`/`AudioBuffer` unchanged; new mapping/loading surface documented in `contracts/sampler-api.md` | PASS |
| VII. Simplicidade Incremental | Linear-interpolation resampling for both pitch-shift and load-time sample-rate conversion (no polyphase/sinc resampler), whole-file loop points only, no new dependencies beyond JDK-bundled `javax.sound.sampled` (spec Assumptions, research.md) | PASS |
| Fluxo de Desenvolvimento | This module is born with its own spec (this feature) before code, same as 002/003 | PASS |

No blocking violations.

## Project Structure

### Documentation (this feature)

```text
specs/005-sampler-module/
├── plan.md                       # This file
├── research.md                   # Phase 0 output
├── data-model.md                 # Phase 1 output
├── quickstart.md                 # Phase 1 output
├── contracts/
│   └── sampler-api.md
└── tasks.md                      # Phase 2 output (/speckit-tasks — not created here)
```

### Source Code (repository root)

```text
settings.gradle.kts                # add `modules:sampler`

modules/
└── sampler/
    ├── build.gradle.kts           # KMP plugin, jvm() target, depends on core-host
    └── src/
        ├── commonMain/kotlin/dev/muzziknod/modules/sampler/
        │   ├── SampleDecoder.kt        # expect fun decodeSample(bytes: ByteArray, targetSampleRate: Int): DecodedAudio
        │   ├── Sample.kt               # decoded, host-sample-rate-converted, mono PCM data
        │   ├── SampleZone.kt           # sample + rootNote + note range + gain + LoopMode
        │   ├── Voice.kt                # one active playback instance (position, pitch ratio, gain, fade state)
        │   ├── VoicePool.kt            # fixed-size Voice array, allocation + oldest-first stealing
        │   └── SamplerModule.kt        # Module impl: MIDI in -> Voice triggers, mixes VoicePool -> audio out
        ├── jvmMain/kotlin/dev/muzziknod/modules/sampler/
        │   └── SampleDecoder.jvm.kt    # actual decodeSample() via javax.sound.sampled.AudioSystem
        ├── commonTest/kotlin/dev/muzziknod/modules/sampler/
        │   ├── SamplerContractTest.kt      # subclasses ModuleContractComplianceTests
        │   ├── PitchRatioTest.kt           # US2: root-note-relative transposition math
        │   ├── VoicePoolTest.kt            # US3: polyphony + oldest-first stealing, no click (fade ramp)
        │   ├── OneShotVsLoopTest.kt        # US1/US3: one-shot ignores note-off, loop releases on note-off
        │   └── VelocityGainTest.kt         # US1/US2: velocity + per-sample gain scaling
        └── jvmTest/kotlin/dev/muzziknod/modules/sampler/
            ├── SampleDecoderWavTest.kt     # real WAV fixture: 16/24/32f-bit, 44.1/48/96kHz
            ├── SampleDecoderAiffTest.kt    # real AIFF fixture
            └── SampleDecoderErrorTest.kt   # missing/corrupt/unsupported file -> reported error, no crash
```

**Structure Decision**: New `modules/sampler` Gradle module, sibling to
`modules/midi-sequencer` and `modules/audio-effects` (research.md "Module
placement"). Package `dev.muzziknod.modules.sampler` mirrors the existing
`dev.muzziknod.modules.*` product-code namespace. This is the first module to
use a real `jvmMain`/`jvmTest` split rather than putting everything in
`commonMain`/`commonTest`, because sample-file decoding is the first genuinely
platform-specific concern any product module has needed (research.md
"Platform split for decoding").

## Complexity Tracking

*No blocking violations — table not needed.*
