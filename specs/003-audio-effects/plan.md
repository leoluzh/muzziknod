# Implementation Plan: Módulos de Efeitos de Áudio (Reverb/Delay/Distortion/EQ)

**Branch**: `003-audio-effects` | **Date**: 2026-07-25 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/003-audio-effects/spec.md`

## Summary

Four new module types on top of the Core Host Modular (001) contract —
reverb, delay, distortion, EQ — each a self-contained `Module` with one
audio input, one audio output, a wet/dry mix, and effect-specific
parameters, chainable in any order through the existing `RoutingGraph` with
zero changes to `core-host`. Each effect runs a real, basic DSP algorithm in
pure Kotlin (Schroeder reverb, single-tap delay, tanh soft-clip distortion,
3-band RBJ parametric EQ) — not a passthrough placeholder — following the
precedent `OscillatorModule` (001) already set for real signal processing
outside the not-yet-existent real-time audio path (research.md "DSP scope").

## Technical Context

**Language/Version**: Kotlin 2.3.21, JVM target Java 26 (same toolchain as
001-core-host and 002-midi-sequencer)

**Primary Dependencies**: `core-host`'s `dev.muzziknod.host.contract` (Module,
ModuleContract, PortSpec, ParameterSpec, AudioBuffer, ProcessContext) as a
`commonMain` dependency, same as `reference-modules/*` and
`modules/midi-sequencer`; `kotlin.test` + JUnit5 platform for tests. No
coroutines/DI/serialization/external DSP libraries (Constitution VII;
research.md "Dependencies").

**Storage**: N/A — no project/preset persistence in this feature (spec has
no save/load requirement; matches 001/002's own no-persistence assumption).

**Testing**: `kotlin.test` on `jvmTest` via Gradle. Contract tests
mandatory (Constitution "Fluxo de Desenvolvimento" — these modules produce
audio): four `*ContractTest` classes each subclass `core-host`'s
`ModuleContractComplianceTests` testkit, same pattern as
`reference-modules/oscillator` and `modules/midi-sequencer`.

**Target Platform**: Desktop JVM (Java 26). `commonMain`/`jvmMain` split
from the start (Constitution IV), consistent with 001/002.

**Project Type**: New Gradle module in the existing Kotlin Multiplatform
multi-module project — a **product** module (per spec, not reference
scaffolding, same category as `modules/midi-sequencer`), so it lives under
`modules/`, not `reference-modules/` (research.md "Module placement").

**Performance Goals**: No real-time audio callback in this feature — same
as 001/002, `process()` stays alloc-free per cycle (buffers, delay lines,
filter state all allocated once in `onLoad()`) in preparation for a future
real-time driver (Constitution III), even though nothing drives it at audio
rate yet.

**Constraints**:
- Zero changes to `core-host`'s `Module`/`ModuleContract`/`ProcessContext`
  (spec FR-011) — parameter setters are additional public Kotlin methods on
  each concrete module class, called directly by whoever holds the instance
  (test harness today; a future UI/host-embedding layer later) — same
  pattern 002 established (research.md "Live parameter control surface").
- No object allocation/blocking I/O/GC-retainable locks in `process()`
  (Constitution III, applied pre-emptively as in 001/002).
- Real DSP ships now, in pure Kotlin, per research.md "DSP scope" — this is
  a deliberate, documented deviation from the original spec draft's
  passthrough-only framing, resolved with the user during planning.

**Scale/Scope**: Four module types, each with 2-9 parameters (data-model.md);
a chain of all four processing continuously for ≥30 minutes without
degradation (spec SC-003); each effect independently connectable/removable
via the existing routing graph (spec US3).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Check | Result |
|---|---|---|
| I. Modularidade em Primeiro Lugar | Four self-contained modules behind the existing contract; host (`core-host`) is untouched | PASS |
| II. Kotlin + Java 26 | Same Kotlin/JVM 26 toolchain as 001/002, no new language | PASS |
| III. Real-Time vs Not-Real-Time | No native DSP/audio device in this feature; `process()` alloc-free by design (state pre-allocated in `onLoad()`); real DSP algorithms run in pure Kotlin because nothing yet drives `process()` from an actual real-time callback (research.md "DSP scope") | PASS (documented deviation from a stricter literal reading — see research.md) |
| IV. Portabilidade via KMP | `commonMain`/`jvmMain` split from the start, no JVM-only leak into DSP/parameter domain model | PASS |
| V. UI Declarativa Desacoplada | No UI in this feature (spec Assumptions); each module exposes a plain Kotlin API a future UI can consume | PASS (N/A) |
| VI. Contratos Explícitos | Reuses 001's `ModuleContract`/`Module`/`ProcessContext`/`ParameterSpec` unchanged (FR-011); new setter surface documented in `contracts/audio-effects-api.md` | PASS |
| VII. Simplicidade Incremental | Four textbook-basic algorithms (no convolution reverb, no state-variable filters), shared `WetDryMixer`/`ParameterSmoother` utilities, no new dependencies (spec Assumptions, research.md) | PASS |
| Fluxo de Desenvolvimento | This module set is born with its own spec (this feature) before code, same as 002 | PASS |

No blocking violations. One row (III) is marked PASS with an explicit
documented interpretation, not a violation requiring Complexity Tracking —
research.md "DSP scope" lays out the reasoning (Constitution III binds the
real-time path, which nothing reaches yet) and the alternative considered
(deferring all DSP) that was rejected as contradicting existing project
practice (`OscillatorModule`).

## Project Structure

### Documentation (this feature)

```text
specs/003-audio-effects/
├── plan.md                       # This file
├── research.md                   # Phase 0 output
├── data-model.md                 # Phase 1 output
├── quickstart.md                 # Phase 1 output
├── contracts/
│   └── audio-effects-api.md
└── tasks.md                      # Phase 2 output (/speckit-tasks — not created here)
```

### Source Code (repository root)

```text
settings.gradle.kts                # add `modules:audio-effects`

modules/
└── audio-effects/
    ├── build.gradle.kts           # KMP plugin, jvm() target, depends on core-host
    └── src/
        ├── commonMain/kotlin/dev/muzziknod/modules/audioeffects/
        │   ├── ParameterSmoother.kt      # shared: linear ramp toward a target value
        │   ├── WetDryMixer.kt            # shared: dry/wet crossfade
        │   ├── ReverbModule.kt           # + CombFilter/AllpassFilter internals
        │   ├── DelayModule.kt            # + DelayLine internal
        │   ├── DistortionModule.kt       # tanh soft-clip + one-pole lowpass tone
        │   └── EqModule.kt               # + Biquad internal, EqBand enum
        └── commonTest/kotlin/dev/muzziknod/modules/audioeffects/
            ├── ReverbContractTest.kt         # subclasses ModuleContractComplianceTests
            ├── DelayContractTest.kt
            ├── DistortionContractTest.kt
            ├── EqContractTest.kt
            ├── WetDryMixTest.kt              # US1: 100% dry/wet/blend, all four types
            ├── ParameterSmoothingTest.kt     # US2: no artifact-sized jumps, clamping
            ├── ReverbDspTest.kt              # US2: decayMs/roomSize behavior
            ├── DelayDspTest.kt               # US2: delayTimeMs/feedback, sample-rate correctness
            ├── DistortionDspTest.kt          # US2: drive/tone behavior
            ├── EqDspTest.kt                  # US2: per-band freq/gain/Q behavior
            └── EffectsChainRoutingTest.kt     # US3: via core-host RoutingGraph, generator→...→sink
```

**Structure Decision**: New `modules/audio-effects` Gradle module, sibling to
`modules/midi-sequencer`, holding all four module types plus their shared
DSP primitives (research.md "Module placement and Gradle structure") — kept
as one Gradle module because the four types are one cohesively-planned
feature sharing `WetDryMixer`/`ParameterSmoother`, not four independent
reference modules. Package `dev.muzziknod.modules.audioeffects` mirrors
`dev.muzziknod.modules.midisequencer`'s naming under the `modules` namespace
(product code, as opposed to `dev.muzziknod.refmodules.*` scaffolding).

## Complexity Tracking

*No blocking violations — table not needed. See Constitution Check row III
above and research.md "DSP scope" for the one documented interpretive call.*
