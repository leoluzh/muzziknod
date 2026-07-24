# Implementation Plan: Módulo Sequenciador MIDI

**Branch**: `002-midi-sequencer` | **Date**: 2026-07-24 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-midi-sequencer/spec.md`

## Summary

Build a MIDI sequencer as a real product module (not scaffolding) on top of the
Core Host Modular (001) contract: a step-pattern of note events, transport
(play/stop) and live pattern editing, emitting MIDI events through a declared
output port each host `processCycle()`, loadable/connectable via the existing
`ModuleRegistry`/`RoutingGraph` with zero changes to `core-host`.

## Technical Context

**Language/Version**: Kotlin 2.3.21, JVM target Java 26 (same toolchain as 001-core-host)

**Primary Dependencies**: `core-host`'s `dev.muzziknod.host.contract` (Module,
ModuleContract, PortSpec, MidiEvent, ProcessContext) as a `commonMain`
dependency, same as `reference-modules/*`; `kotlin.test` + JUnit5 platform for
tests. No coroutines/DI/serialization (nothing in scope needs them —
Constitution VII).

**Storage**: N/A — no project/pattern persistence in this feature (spec has no
save/load requirement; matches 001-core-host's own no-persistence assumption)

**Testing**: `kotlin.test` on `jvmTest` via Gradle. Contract tests mandatory
(Constitution "Fluxo de Desenvolvimento" — this module produces MIDI): subclasses
`core-host`'s `ModuleContractComplianceTests` testkit, same pattern as
`reference-modules/midi-generator` and `midi-logger`.

**Target Platform**: Desktop JVM (Java 26). `commonMain`/`jvmMain` split from
the start (Constitution IV), consistent with 001.

**Project Type**: New Gradle module in the existing Kotlin Multiplatform
multi-module project — a **product** module (per spec, not reference
scaffolding), so it lives under a new `modules/` root, not `reference-modules/`.

**Performance Goals**: No real-time audio callback in this feature — same as
001, `process()` stays alloc-free per cycle in preparation for a future
real-time driver (Constitution III), even though nothing drives it at audio
rate yet.

**Constraints**:
- Zero changes to `core-host`'s `Module`/`ModuleContract`/`ProcessContext`
  (spec FR-008) — the module's pattern-editing and transport (play/stop) API is
  exposed as additional public Kotlin methods on the concrete
  `MidiSequencerModule` class itself, called directly by whoever holds the
  instance (test harness today; a future UI/host-embedding layer later) —
  consistent with `ParameterSpec` already being declaration-only with no
  runtime setter in the 001 contract, and with Constitution V's premise that a
  module may expose its own richer surface beyond the generic host contract.
- No object allocation/blocking I/O/GC-retainable locks in `process()`
  (Constitution III, applied pre-emptively as in 001).

**Scale/Scope**: pattern of at least 16 steps, looping ≥20 repetitions without
ordering/timing drift (SC-001); single sequencer instance is monophonic per
spec Assumptions — multiple simultaneous parts means multiple module instances.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Check | Result |
|---|---|---|
| I. Modularidade em Primeiro Lugar | Sequencer is a self-contained module behind the existing contract; host (`core-host`) is untouched | PASS |
| II. Kotlin + Java 26 | Same Kotlin/JVM 26 toolchain as 001, no new language | PASS |
| III. Real-Time vs Not-Real-Time | No native DSP/audio device in this feature; `process()` alloc-free by design in preparation | PASS |
| IV. Portabilidade via KMP | `commonMain`/`jvmMain` split from the start, no JVM-only leak into pattern/transport domain model | PASS |
| V. UI Declarativa Desacoplada | No UI in this feature (spec Assumptions); module exposes a plain Kotlin API a future UI can consume | PASS (N/A) |
| VI. Contratos Explícitos | Reuses 001's `ModuleContract`/`Module`/`ProcessContext` unchanged (FR-008); new pattern/transport surface documented in `contracts/midi-sequencer-api.md` | PASS |
| VII. Simplicidade Incremental | One module, notes-only events, no swing/probability/CC automation, no cross-module transport sync (spec Assumptions) | PASS |
| Fluxo de Desenvolvimento | This module is born with its own spec (this feature) before code, per the rule 001-core-host explicitly scoped reference modules out of — this is a real product module | PASS |

No violations — Complexity Tracking table not needed.

## Project Structure

### Documentation (this feature)

```text
specs/002-midi-sequencer/
├── plan.md               # This file
├── research.md           # Phase 0 output
├── data-model.md         # Phase 1 output
├── quickstart.md         # Phase 1 output
├── contracts/
│   └── midi-sequencer-api.md
└── tasks.md               # Phase 2 output (/speckit-tasks — not created here)
```

### Source Code (repository root)

```text
settings.gradle.kts                # add `modules:midi-sequencer`

modules/
└── midi-sequencer/
    ├── build.gradle.kts           # KMP plugin, jvm() target, depends on core-host
    └── src/
        ├── commonMain/kotlin/dev/muzziknod/modules/midisequencer/
        │   ├── MidiSequencerModule.kt   # Module implementation + public pattern/transport API
        │   ├── Pattern.kt               # Pattern, Step, NoteEvent value types
        │   └── Transport.kt             # play/stop state + current step position
        └── commonTest/kotlin/dev/muzziknod/modules/midisequencer/
            ├── MidiSequencerContractTest.kt      # subclasses ModuleContractComplianceTests (T-style, per 001)
            ├── PatternPlaybackTest.kt            # US1: loop, ordering, timing
            ├── PatternEditingTest.kt             # US2: live edit while stopped/playing
            └── RoutingIntegrationTest.kt         # US3: via core-host RoutingGraph + a FakeModule/midi-logger sink
```

**Structure Decision**: New top-level `modules/` root (sibling to
`reference-modules/`) holding `modules/midi-sequencer` — a real product module,
not scaffolding, so it's kept out of `reference-modules/` to preserve that
directory's meaning (per 001-core-host's plan.md: minimal reference set proving
the contract). `midi-sequencer` depends only on `core-host`'s contract package,
same dependency direction as every reference module (Constitution I — host
never depends on a module). Package `dev.muzziknod.modules.midisequencer`
mirrors `dev.muzziknod.refmodules.*`'s naming but under a `modules` namespace
to signal "product" vs "reference/scaffolding".

## Complexity Tracking

*No violations — table not needed.*
