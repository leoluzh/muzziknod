# Implementation Plan: Core Host Modular

**Branch**: `001-core-host` | **Date**: 2026-07-23 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-core-host/spec.md`

## Summary

Build the minimal host that manages module lifecycle (load/activate/remove), exposes
each module's declared audio/MIDI ports, and maintains a routing graph that connects
port-to-port, processing it in dependency order each cycle. Validated with 2-3 pure-Kotlin
reference modules (no native DSP yet). No persistence, no UI, single desktop/JVM target —
per spec Assumptions and Constitution v1.0.0 principles I, IV, VII.

## Technical Context

**Language/Version**: Kotlin 2.x, JVM target Java 26

**Primary Dependencies**: Kotlin Multiplatform Gradle plugin (JVM target only active);
`kotlin.test` + JUnit5 platform for tests. No coroutines/DI/serialization dependency
introduced yet — nothing in this feature requires them (YAGNI, Constitution VII).

**Storage**: N/A — no project persistence in this feature (spec Assumptions)

**Testing**: `kotlin.test` on `jvmTest`, run via Gradle (`./gradlew test`). Contract tests
mandatory for the module contract itself and for every reference module, per Constitution
"Fluxo de Desenvolvimento".

**Target Platform**: Desktop JVM (Java 26). `androidMain` not created yet — `commonMain` is
structured so it can be added later without moving code (Constitution IV).

**Project Type**: Single Kotlin Multiplatform project (host library + reference module
libraries), no application entry point / no UI in this feature.

**Performance Goals**: No hard real-time audio callback in this feature (reference modules
are pure Kotlin, not driven by a real audio device — spec Assumptions). Still, the
`process()` boundary is written allocation-free per cycle so the pattern holds once a real
RT callback and native DSP engine are wired in (Constitution III).

**Constraints**:
- No object allocation, blocking I/O, or GC-retainable locks inside any module's `process()`
  call or the host's per-cycle graph walk (Constitution III, non-negotiable, applied
  pre-emptively even though no real audio device is involved yet).
- Host never depends on a module's internal implementation — only on the declared contract
  (Constitution I, VI).
- `commonMain`/`jvmMain` split from day one (Constitution IV).

**Scale/Scope**: MVP graph of ≥5 connected modules processed without ordering errors
(SC-004); single user, single host instance, desktop only.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Check | Result |
|---|---|---|
| I. Modularidade em Primeiro Lugar | Host only manages lifecycle/routing/state; module logic lives entirely inside modules behind the contract | PASS |
| II. Kotlin + Java 26 | Kotlin/JVM 26, no other language introduced | PASS |
| III. Real-Time vs Not-Real-Time | No native DSP in this feature; `process()` designed alloc-free in preparation. No blocking I/O anywhere in host or ref modules | PASS |
| IV. Portabilidade via KMP | `commonMain`/`jvmMain` split used from the start; no JVM-only API leaks into domain model | PASS |
| V. UI Declarativa Desacoplada | No UI in this feature — out of scope by spec Assumptions | PASS (N/A) |
| VI. Contratos Explícitos | `ModuleContract`/`Port`/`Connection` are the versioned interface boundary — see `contracts/module-contract.md` | PASS |
| VII. Simplicidade Incremental | 1 host module + 2-3 reference modules, no premature SRC/conversion, no coroutines/DI added speculatively | PASS |

No violations — Complexity Tracking table not needed.

## Project Structure

### Documentation (this feature)

```text
specs/001-core-host/
├── plan.md              # This file
├── research.md           # Phase 0 output
├── data-model.md         # Phase 1 output
├── quickstart.md         # Phase 1 output
├── contracts/
│   └── module-contract.md
└── tasks.md              # Phase 2 output (/speckit-tasks — not created here)
```

### Source Code (repository root)

```text
settings.gradle.kts
build.gradle.kts                  # root: shared KMP conventions
gradle/libs.versions.toml

core-host/
├── build.gradle.kts
└── src/
    ├── commonMain/kotlin/dev/muzziknod/host/
    │   ├── contract/             # Module, Port, ModuleContract, PortType
    │   ├── graph/                # RoutingGraph, Connection, topological ordering
    │   └── lifecycle/            # ModuleRegistry (load/activate/remove)
    ├── commonTest/kotlin/dev/muzziknod/host/
    │   └── contract/             # contract compliance test suite (reusable by ref modules)
    └── jvmMain/kotlin/dev/muzziknod/host/
        └── (empty for now — JVM-specific host wiring lands with real audio callback, future feature)

reference-modules/
├── oscillator/                     # audio-out only — US1 demo + US2 type-mismatch negative test
│   └── src/commonMain/kotlin/dev/muzziknod/refmodules/oscillator/
├── midi-generator/                 # MIDI-out only — source for US2 positive connect test
│   └── src/commonMain/kotlin/dev/muzziknod/refmodules/midigenerator/
├── midi-logger/                    # MIDI-in + MIDI-out passthrough/relay + sink
│   └── src/commonMain/kotlin/dev/muzziknod/refmodules/midilogger/
└── */src/commonTest/...          # each ref module runs the shared contract test suite
```

**Structure Decision**: Single Gradle multi-module KMP project. `core-host` holds the
host (contract + graph + lifecycle), with zero knowledge of any specific module.
`reference-modules/*` are separate Gradle modules that depend on `core-host`'s contract
only, proving modules are independently buildable/testable (Constitution I). Three
reference modules is the minimum needed to exercise every acceptance scenario in the spec
(US1 load/activate, US2 connect/reject/disconnect/cycle-reject, US3 mid-chain removal)
without inventing unused module types — `midi-logger` doubling as a passthrough relay is
what makes the two-instance feedback-cycle test (US2 AC4) possible with only 3 types.
JVM target only is enabled in `build.gradle.kts` for now; the source-set split already
supports adding `androidMain` later without refactoring (Constitution IV).

## Complexity Tracking

*No violations — table not needed.*