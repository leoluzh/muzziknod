# Implementation Plan: Project Persistence

**Branch**: `006-project-persistence` | **Date**: 2026-07-29 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/006-project-persistence/spec.md`

## Summary

A new `:project-persistence` module that captures the live host state — `RoutingGraph`
connections, `ModuleRegistry` instances, each module's parameter values, a new host-level
`Transport`, and sampler sample-zone mappings — into a versioned JSON project file, and
rebuilds that exact state on load. Per-module-type `ModuleStateCodec`s (one per product
module, registered in a new `ProjectPersistenceCatalog`) capture/restore parameters and
any module-specific data (e.g. sampler zones + their source file paths), mirroring the
existing `ModuleCatalogEntry` factory pattern in `ui-desktop` rather than adding a generic
reflective parameter API to `core-host`'s `Module` contract. Two small additive changes
ship alongside: `core-host` gains a `Transport` (tempo/position/loop-range/play-state,
pure control-plane state, no `process()` involvement) since no host-wide transport exists
today; `modules/sampler`'s `SampleZone`/`loadSample` gains an optional `sourcePath`
so FR-006's external-reference requirement has something to serialize. Missing module
types or missing sample files are reported as load warnings, never abort the load
(FR-009/FR-010).

## Technical Context

**Language/Version**: Kotlin 2.4.10, JVM target Java 26 (same toolchain as
001-core-host through 005-sampler-module)

**Primary Dependencies**: `core-host`'s `dev.muzziknod.host.contract`/`graph`/`lifecycle`
packages (`Module`, `ModuleRegistry`, `RoutingGraph`, `Connection`) as a `commonMain`
dependency; `kotlinx.serialization` (JSON) — new to this project, added for typed,
KMP-native (no reflection) encode/decode of the project-file DTOs; `kotlin.test` +
JUnit5 platform for tests, same as every prior module.

**Storage**: Local filesystem — one project file per save (JSON, versioned schema);
sample audio itself stays on disk at its original path and is referenced, not embedded
(spec FR-006). No database, no cloud sync (spec Assumptions).

**Testing**: `kotlin.test` on `commonTest` (round-trip serialization of DTOs, catalog
codec capture/restore per module type, missing-module/missing-sample warning paths,
`Transport` state transitions — all pure Kotlin, no file I/O) and `jvmTest` (real
file-based save→load round trip through the `expect`/`actual` file I/O boundary).
Contract-style test mandatory (Constitution "Fluxo de Desenvolvimento"): a
`ProjectRoundTripTest` that builds a graph across every existing module type, saves,
resets the host, loads, and asserts full equality — the persistence-specific analog of
the `ModuleContractComplianceTests` other modules subclass.

**Target Platform**: Desktop JVM (Java 26), matching every module shipped so far. File
read/write is the only genuinely platform-specific piece, isolated behind
`expect fun readProjectFile(path): String` / `expect fun writeProjectFile(path, content)`
in `commonMain` with a `jvmMain` `actual` over `java.nio.file` — the same
`expect`/`actual` shape 005-sampler-module established for `SampleDecoder`.

**Project Type**: New root-level Gradle module in the existing Kotlin Multiplatform
multi-module project. It lives as `:project-persistence` (sibling to `:core-host` and
`:ui-desktop`, **not** under `:modules:`) because it is not itself a pluggable
audio/MIDI module — it has no ports and never appears in a routing graph; it is a
host-facing service that happens to need concrete knowledge of every product module
type (`midi-sequencer`, `audio-effects`, `sampler`) to build their codecs, the same way
`ui-desktop` already does for its `ModuleCatalog`.

**Performance Goals**: No real-time audio-callback involvement at all — save/load are
purely control-plane, user-initiated actions. No specific latency target beyond "feels
instant" for typical project sizes (tens of modules); not a hot-path concern under
Constitution III.

**Constraints**:
- Zero changes to `core-host`'s `Module`/`ModuleContract`/`ProcessContext`/`PortSpec`
  (mirrors 003/005 precedent) — parameter capture/restore is per-module-type, not a
  generic contract addition, because today's `ParameterSpec` is UI-metadata-only and
  every module already exposes typed setters + `StateFlow<Double>` mirrors instead of a
  generic get/set surface (research.md "Parameter capture strategy").
- The one core-host addition is purely additive and orthogonal to modules: a new
  `transport/Transport.kt` (state only, no `process()` hook, no port), since no
  host-wide transport exists yet and spec FR-004 requires one (research.md "Transport").
- `modules/sampler`'s public API gains one additive field/parameter (`sourcePath`) —
  non-breaking, existing call sites unaffected — per Constitution VI's requirement that
  contract changes be explicit and documented (this plan + `contracts/`), not silent.
- `:project-persistence` may depend on every product module's public API (same as
  `ui-desktop` already does) but never reaches into a module's internals — codecs only
  call already-public methods/fields (`zones`, `setMix`, etc.), so Constitution I's
  "no module depends on another module's internals" is not implicated (this is a host
  service depending on public module APIs, not module-to-module coupling).

**Scale/Scope**: Persists an entire project in one file: N module instances, their
connections, per-module parameter values, one host `Transport`, and all sampler sample
zones (with source paths) across however many sampler instances exist. No partial
save/load, no multi-project merge (spec Assumptions).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Check | Result |
|---|---|---|
| I. Modularidade em Primeiro Lugar | `:project-persistence` is a host-facing service, not a pluggable module (no ports, never enters the graph); it only calls other modules' already-public APIs, never their internals | PASS |
| II. Kotlin + Java 26 | Same toolchain as 001-005 | PASS |
| III. Real-Time vs Not-Real-Time | Save/load and the new `Transport` are pure control-plane state/I-O, never touched from `process()`; file I/O and JSON encode/decode only happen on explicit user action | PASS |
| IV. Portabilidade via KMP | DTOs, catalog, codecs, `Transport`, and orchestration all in `commonMain`; only raw file read/write isolated behind `expect`/`actual` (mirrors 005's `SampleDecoder`) | PASS |
| V. UI Declarativa Desacoplada | `ui-desktop` triggers save/load and reads results via `ProjectPersistence`'s plain Kotlin API + `StateFlow`, same pattern as `HostViewModel`; no UI logic in `:project-persistence` | PASS (N/A UI) |
| VI. Contratos Explícitos | New `ModuleStateCodec` contract documented in `contracts/module-state-codec.md`; the two additive changes (sampler `sourcePath`, new `Transport`) are explicit, non-breaking, and documented here rather than silent | PASS |
| VII. Simplicidade Incremental | Per-module-type codec catalog (no reflection, no generic serialization of arbitrary module internals); JSON via `kotlinx.serialization` (no custom binary format); no autosave/versioned history/undo — just save, save-as, load (spec scope) | PASS |
| Fluxo de Desenvolvimento | This module is born with its own spec (this feature) before code, same as every prior module | PASS |

No blocking violations.

## Project Structure

### Documentation (this feature)

```text
specs/006-project-persistence/
├── plan.md                       # This file
├── research.md                   # Phase 0 output
├── data-model.md                 # Phase 1 output
├── quickstart.md                 # Phase 1 output
├── contracts/
│   ├── project-file-schema.md
│   └── module-state-codec.md
└── tasks.md                      # Phase 2 output (/speckit-tasks — not created here)
```

### Source Code (repository root)

```text
settings.gradle.kts                # add `:project-persistence`

core-host/
└── src/commonMain/kotlin/dev/muzziknod/host/
    └── transport/
        └── Transport.kt           # NEW: TransportState (tempoBpm, positionBeats,
                                    #      isPlaying, loopStart/loopEnd), Transport class
                                    #      with play()/pause()/stop()/setTempo()/
                                    #      setPosition()/setLoopRange(), StateFlow<TransportState>

modules/sampler/
└── src/commonMain/kotlin/dev/muzziknod/modules/sampler/
    ├── SampleZone.kt               # MODIFIED: add `sourcePath: String?`
    └── SamplerModule.kt            # MODIFIED: loadSample(..., sourcePath: String? = null)

project-persistence/
├── build.gradle.kts                # KMP plugin, jvm() target; depends on core-host,
│                                    # modules:midi-sequencer, modules:audio-effects,
│                                    # modules:sampler; kotlinx-serialization-json
└── src/
    ├── commonMain/kotlin/dev/muzziknod/persistence/
    │   ├── model/
    │   │   ├── ProjectSnapshot.kt      # @Serializable root DTO (schemaVersion, modules,
    │   │   │                           # connections, transport, createdWith)
    │   │   ├── ModuleSnapshot.kt       # @Serializable (instanceId, typeId, parameters: Map<String,Double>, moduleData: JsonElement?)
    │   │   ├── ConnectionSnapshot.kt   # @Serializable mirror of core-host's Connection
    │   │   ├── TransportSnapshot.kt    # @Serializable mirror of Transport.TransportState
    │   │   └── SamplerData.kt          # @Serializable zones (rootNote, lowNote, highNote, gain, loopMode, sourcePath)
    │   ├── codec/
    │   │   ├── ModuleStateCodec.kt     # interface: capture(Module): ModuleSnapshot; restore(instanceId, ModuleSnapshot): Module
    │   │   ├── DelayCodec.kt, ReverbCodec.kt, DistortionCodec.kt, EqCodec.kt   # modules:audio-effects
    │   │   ├── MidiSequencerCodec.kt   # modules:midi-sequencer
    │   │   └── SamplerCodec.kt         # modules:sampler — also captures/restores sourcePath-based zones
    │   ├── ProjectPersistenceCatalog.kt # typeId -> ModuleStateCodec registry (mirrors ui-desktop's ModuleCatalog)
    │   ├── ProjectWriter.kt            # (ModuleRegistry, RoutingGraph, Transport, Catalog) -> ProjectSnapshot -> JSON string
    │   ├── ProjectReader.kt            # JSON string -> ProjectSnapshot -> rebuilds registry/graph/transport; returns ProjectLoadResult
    │   ├── ProjectLoadResult.kt        # data class(warnings: List<LoadWarning>) ; LoadWarning sealed: MissingModuleType, MissingSampleFile
    │   └── ProjectFileIo.kt            # expect fun readProjectFile(path: String): String / writeProjectFile(path: String, content: String)
    ├── jvmMain/kotlin/dev/muzziknod/persistence/
    │   └── ProjectFileIo.jvm.kt        # actual over java.nio.file.Files
    ├── commonTest/kotlin/dev/muzziknod/persistence/
    │   ├── ProjectRoundTripTest.kt     # US1+US2: full graph -> save -> reset -> load -> assert equality
    │   ├── TransportPersistenceTest.kt # US2: tempo/position/loop/play-state round trip
    │   ├── MissingModuleWarningTest.kt # US3: unknown typeId -> warning, rest of project loads
    │   ├── MissingSampleWarningTest.kt # US3: sourcePath not found -> warning, rest of project loads
    │   └── CorruptFileTest.kt          # edge case: unreadable/invalid JSON -> reported error, no crash
    └── jvmTest/kotlin/dev/muzziknod/persistence/
        └── ProjectFileIoTest.kt        # real file save/overwrite/save-as round trip on disk
```

**Structure Decision**: New root-level `:project-persistence` Gradle module (sibling to
`:core-host`/`:ui-desktop`, not under `:modules:`) since it is a host service, not an
audio/MIDI module. Two existing modules get small additive changes (`core-host` gains
`transport/Transport.kt`; `modules/sampler` gains `sourcePath`), documented as explicit
contract changes per Constitution VI rather than silent modifications. `ui-desktop` wires
save/load menu actions to `project-persistence`'s `ProjectWriter`/`ProjectReader` in a
follow-up integration (calling `HostViewModel`'s existing `registry`/`graph`, plus the new
`Transport`), consistent with how `ui-desktop` already owns all cross-module wiring.

## Complexity Tracking

*No blocking violations — table not needed.*
