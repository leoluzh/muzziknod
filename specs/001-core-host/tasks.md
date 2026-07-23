# Tasks: Core Host Modular

**Input**: Design documents from `/specs/001-core-host/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/module-contract.md, quickstart.md

**Tests**: Included and REQUIRED — Constitution "Fluxo de Desenvolvimento" mandates
contract tests for any module that produces or consumes audio/MIDI; this feature's
module contract itself is the audio/MIDI boundary.

**Organization**: Tasks are grouped by user story (US1/US2/US3, both P1 stories first)
to enable independent implementation and testing of each.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Maps task to US1, US2, or US3
- File paths are exact, relative to repo root

---

## Phase 1: Setup (Shared Infrastructure)

- [X] T001 Create Gradle wrapper + root `settings.gradle.kts` (include `core-host`,
      `reference-modules:oscillator`, `reference-modules:midi-generator`,
      `reference-modules:midi-logger`) and root `build.gradle.kts` (Kotlin Multiplatform
      plugin, JVM target only, per Constitution "Restrições Técnicas")
- [X] T002 [P] Create `gradle/libs.versions.toml` with Kotlin, kotlin.test, and JUnit5
      platform versions
- [X] T003 [P] Create `core-host/build.gradle.kts` (KMP plugin, `jvm()` target only,
      `commonMain` exposes `kotlin.test` as `api` — needed by the shared contract-
      compliance abstract test class reference modules subclass, since Gradle/KMP has no
      cross-module test-sourceSet dependency)
- [X] T004 [P] Create `reference-modules/oscillator/build.gradle.kts` depending on
      `core-host`
- [X] T005 [P] Create `reference-modules/midi-generator/build.gradle.kts` depending on
      `core-host`
- [X] T006 [P] Create `reference-modules/midi-logger/build.gradle.kts` depending on
      `core-host`

**Checkpoint**: `./gradlew build` runs (no source yet, empty modules compile)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Contract types, buffer/event value types, and the module registry skeleton
that every user story builds on. No user story work starts before this phase is done.

- [X] T007 [P] Define `PortDirection`, `PortType`, `BufferFormat` enums/value types in
      `core-host/src/commonMain/kotlin/dev/muzziknod/host/contract/PortSpec.kt`
- [X] T008 [P] Define `PortSpec` data class (id, direction, type, sampleRate,
      bufferFormat) in the same file as T007
- [X] T009 [P] Define `ParameterSpec` and `ModuleContract` data classes in
      `core-host/src/commonMain/kotlin/dev/muzziknod/host/contract/ModuleContract.kt`
- [X] T010 [P] Define `AudioBuffer` and `MidiEvent` value types in
      `core-host/src/commonMain/kotlin/dev/muzziknod/host/contract/Buffers.kt`
- [X] T011 [US-shared] Define `Module` and `ProcessContext` interfaces (per
      `contracts/module-contract.md`) in
      `core-host/src/commonMain/kotlin/dev/muzziknod/host/contract/Module.kt`
      (depends on T007–T010)
- [X] T012 Implement `ModuleRegistry` (load/query/remove-instance bookkeeping,
      `Module` state enum `Loaded|Active|Faulted|Removed` from data-model.md) in
      `core-host/src/commonMain/kotlin/dev/muzziknod/host/lifecycle/ModuleRegistry.kt`
      (depends on T011). Implemented cohesively with contract validation and
      duplicate-id rejection already in place — see T018/T019/T020/T021 notes below.
- [X] T013 [P] Implement shared contract-compliance suite as an **abstract** test class
      (per `contracts/module-contract.md` §"Contract test suite") with an abstract
      `createModule(): Module` factory method, in
      `core-host/src/commonMain/kotlin/dev/muzziknod/host/contract/testkit/ModuleContractComplianceTests.kt`
      — lives in `commonMain` (not `commonTest`) because Gradle/KMP has no way for one
      module's test source set to depend on another module's test source set; reference
      modules subclass this in their own `commonTest` instead (depends on T011). Scope
      narrowed to what's testable on a single module instance in isolation (unique port
      ids, onLoad→process→onRemove doesn't throw, process only touches declared ports);
      fault-isolation and deferred-removal are graph/registry behaviors, covered instead
      by `RoutingGraphFaultIsolationTest` (T029) and `ModuleRemovalDeferralTest` (T042).

**Checkpoint**: Contract types + registry skeleton compile and are ready for all stories.

---

## Phase 3: User Story 1 - Carregar e ativar um módulo no host (Priority: P1) 🎯 MVP

**Goal**: Load a module into the host at runtime; it becomes `Active` with its ports
visible, without disturbing already-loaded modules; invalid modules are rejected.

**Independent Test**: Load one reference module (`oscillator`) into an empty host and
verify it is `Active` with correctly exposed ports, with no other module present.

### Tests for User Story 1

- [X] T014 [P] [US1] Integration test: load module into empty host → `Active`, ports
      match `contract.ports` (FR-001, FR-002; US1 AC1) in
      `core-host/src/commonTest/kotlin/dev/muzziknod/host/lifecycle/ModuleLifecycleTest.kt`
- [X] T015 [P] [US1] Integration test: loading a second module leaves the first
      untouched (FR-001; US1 AC2) in
      `core-host/src/commonTest/kotlin/dev/muzziknod/host/lifecycle/ModuleLifecycleIsolationTest.kt`
- [X] T016 [P] [US1] Integration test: invalid/incompatible contract is rejected with a
      reason, active modules unaffected (FR-003; US1 AC3) in
      `core-host/src/commonTest/kotlin/dev/muzziknod/host/lifecycle/ModuleLifecycleRejectionTest.kt`
- [X] T017 [P] [US1] Integration test: loading a second instance with a duplicate
      `instanceId` is rejected (FR-012) in
      `core-host/src/commonTest/kotlin/dev/muzziknod/host/lifecycle/ModuleDuplicateIdTest.kt`

### Implementation for User Story 1

- [X] T018 [US1] Implement contract validation on load (reject duplicate port ids;
      malformed `ModuleContract`) in `ModuleRegistry.kt` (FR-003; depends on T012) —
      implemented together with T012 in the Foundational phase
- [X] T019 [US1] Implement duplicate-`instanceId` rejection in `ModuleRegistry.kt`
      (FR-012; depends on T012) — implemented together with T012
- [X] T020 [US1] Implement `Loaded → Active` transition on successful load, without
      touching other registered instances, in `ModuleRegistry.kt` (FR-001; depends on
      T018, T019) — implemented together with T012
- [X] T021 [US1] Expose a query API for a loaded module's declared ports in
      `ModuleRegistry.kt` (FR-002; depends on T020) — implemented together with T012
      (`ManagedModule.ports`)
- [X] T022 [P] [US1] Implement reference `oscillator` module (audio-out only, no real
      DSP — spec Assumptions) in
      `reference-modules/oscillator/src/commonMain/kotlin/dev/muzziknod/refmodules/oscillator/OscillatorModule.kt`
- [X] T023 [P] [US1] Subclass the shared `ModuleContractComplianceTests` (T013),
      providing `OscillatorModule` via `createModule()`, in
      `reference-modules/oscillator/src/commonTest/kotlin/dev/muzziknod/refmodules/oscillator/OscillatorContractTest.kt`

**Checkpoint**: US1 fully functional and independently testable — `./gradlew
:core-host:test :reference-modules:oscillator:test`.

---

## Phase 4: User Story 2 - Conectar módulos através de um grafo de roteamento (Priority: P1)

**Goal**: Connect module ports into a routing graph; the host validates type/format
compatibility and cycle-safety, then processes the graph in dependency order each cycle.

**Independent Test**: Connect a MIDI event generator to a module that only logs
received events, and verify events arrive in emitted order/timing.

### Tests for User Story 2

- [X] T024 [P] [US2] Integration test: connect two modules' Midi ports,
      `processCycle()` delivers events in emitted order (FR-004, FR-007; US2 AC1) in
      `core-host/src/commonTest/kotlin/dev/muzziknod/host/graph/RoutingGraphConnectTest.kt`
      — uses `FakeModule` test doubles shaped like midi-generator/midi-logger, not the
      real reference module classes: `core-host` cannot depend on `reference-modules:*`
      (dependency direction is the reverse), so its own tests use local doubles; the
      *real* modules are proven via T039/T040's contract-compliance subclasses instead
- [X] T025 [P] [US2] Integration test: connecting an Audio output to a Midi input (and
      the mirrored Midi output to Audio input) is rejected with a reason (FR-005, SC-002;
      US2 AC2) in
      `core-host/src/commonTest/kotlin/dev/muzziknod/host/graph/RoutingGraphTypeMismatchTest.kt`
      (same `FakeModule`-doubles caveat as T024)
- [X] T026 [P] [US2] Integration test: disconnecting one link stops flow between those
      two modules only, rest of graph unaffected (FR-008; US2 AC3) in
      `core-host/src/commonTest/kotlin/dev/muzziknod/host/graph/RoutingGraphDisconnectTest.kt`
- [X] T027 [P] [US2] Integration test: with two Midi in+out `FakeModule` instances L1→L2
      connected, attempting `L2.out → L1.in` is rejected as a feedback cycle (FR-006;
      US2 AC4) in
      `core-host/src/commonTest/kotlin/dev/muzziknod/host/graph/RoutingGraphCycleRejectionTest.kt`
- [X] T028 [P] [US2] Integration test: sample-rate/buffer-format mismatch rejects the
      connection, no auto-conversion (FR-013, per research.md resolution) in
      `core-host/src/commonTest/kotlin/dev/muzziknod/host/graph/RoutingGraphFormatMismatchTest.kt`
- [X] T029 [P] [US2] Integration test: a module whose `process()` throws is marked
      `Faulted` and excluded from later cycles while the rest of the graph keeps running
      (FR-011, per research.md resolution) in
      `core-host/src/commonTest/kotlin/dev/muzziknod/host/graph/RoutingGraphFaultIsolationTest.kt`

### Implementation for User Story 2

- [X] T030 [P] [US2] Define `Connection` data class in
      `core-host/src/commonMain/kotlin/dev/muzziknod/host/graph/Connection.kt`
- [X] T031 [US2] Implement `RoutingGraph` with `connections` map + port-type validation
      (Audio↔Audio, Midi↔Midi only) in
      `core-host/src/commonMain/kotlin/dev/muzziknod/host/graph/RoutingGraph.kt` (FR-004,
      FR-005; depends on T030)
- [X] T032 [US2] Add sample-rate/buffer-format compatibility check to `connect()`,
      rejecting mismatches (FR-013) in `RoutingGraph.kt` (depends on T031) — implemented
      together with T031
- [X] T033 [US2] Add feedback-cycle detection to `connect()`, rejecting connections that
      would close a cycle (FR-006) in `RoutingGraph.kt` (depends on T031) — implemented
      together with T031
- [X] T034 [US2] Implement `disconnect(connectionId)` removing a single connection
      without affecting others (FR-008) in `RoutingGraph.kt` (depends on T031) —
      implemented together with T031
- [X] T035 [US2] Implement topological sort (Kahn's algorithm) + `processCycle()`
      invoking each `Active` module's `process()` in dependency order (FR-007) in
      `RoutingGraph.kt` (depends on T031, T012) — implemented together with T031
- [X] T036 [US2] Wrap each module's `process()` call in a per-module fault boundary:
      catch, transition to `Faulted`, continue the cycle for the rest of the graph
      (FR-011) in `RoutingGraph.kt` (depends on T035) — implemented together with T031
- [X] T037 [P] [US2] Implement reference `midi-generator` module (MIDI-out only, emits a
      fixed event sequence per cycle) in
      `reference-modules/midi-generator/src/commonMain/kotlin/dev/muzziknod/refmodules/midigenerator/MidiGeneratorModule.kt`
- [X] T038 [P] [US2] Implement reference `midi-logger` module (MIDI-in + MIDI-out
      passthrough, records received events for test assertions) in
      `reference-modules/midi-logger/src/commonMain/kotlin/dev/muzziknod/refmodules/midilogger/MidiLoggerModule.kt`
- [X] T039 [P] [US2] Subclass `ModuleContractComplianceTests` (T013), providing
      `MidiGeneratorModule` via `createModule()`, in
      `reference-modules/midi-generator/src/commonTest/kotlin/dev/muzziknod/refmodules/midigenerator/MidiGeneratorContractTest.kt`
- [X] T040 [P] [US2] Subclass `ModuleContractComplianceTests` (T013), providing
      `MidiLoggerModule` via `createModule()`, in
      `reference-modules/midi-logger/src/commonTest/kotlin/dev/muzziknod/refmodules/midilogger/MidiLoggerContractTest.kt`

**Checkpoint**: US1 + US2 both independently functional — `./gradlew :core-host:test
:reference-modules:oscillator:test :reference-modules:midi-generator:test
:reference-modules:midi-logger:test`.

---

## Phase 5: User Story 3 - Remover um módulo sem afetar o restante do projeto (Priority: P2)

**Goal**: Remove a module; its connections are torn down cleanly, resources freed, rest
of the graph stays intact; removal is deferred until any in-flight cycle finishes.

**Independent Test**: Load 3 modules chained (`midi-generator → midi-logger#1 →
midi-logger#2`), remove the middle one, verify its connections are gone while the other
two remain loaded.

### Tests for User Story 3

- [X] T041 [P] [US3] Integration test: removing the middle module of a 3-module chain
      clears its connections, other two remain `Active`/loaded (FR-009; US3 AC1) in
      `core-host/src/commonTest/kotlin/dev/muzziknod/host/lifecycle/ModuleRemovalTest.kt`
- [X] T042 [P] [US3] Integration test: removal requested mid-`processCycle()` is deferred
      until the current cycle completes before taking effect (FR-010; US3 AC2) in
      `core-host/src/commonTest/kotlin/dev/muzziknod/host/lifecycle/ModuleRemovalDeferralTest.kt`

### Implementation for User Story 3

- [X] T043 [US3] Implement `removeModule(instanceId)` on `RoutingGraph`: remove all
      connections touching the module, then the module itself via
      `ModuleRegistry.removeImmediately` (FR-009) (depends on T012, T031)
- [X] T044 [US3] Defer `removeModule()` effect until the in-flight `processCycle()`
      returns, calling the module's `onRemove()` only after its last `process()`
      completes (FR-010; depends on T036, T043) — implemented together with T043 via a
      `cycleInProgress` flag + `pendingRemovals` queue drained at the end of
      `processCycle()`

**Checkpoint**: All 3 user stories independently functional.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T045 [P] Stress test: 7 connected `FakeModule` instances (exceeds the ≥5 required)
      processed across 50 repeated cycles with no ordering errors or crashes (SC-004) in
      `core-host/src/commonTest/kotlin/dev/muzziknod/host/graph/GraphOrderingStressTest.kt`
- [X] T046 Ran `quickstart.md` scenarios 1–5 end-to-end — all pass. Corrected the
      quickstart commands along the way: the real KMP test task is `jvmTest`, not
      `test` (`:core-host:test` doesn't exist), and test-class name filters were fixed
      to match the classes actually created (`*RoutingGraph*`, `*ModuleRemoval*`, etc.)
- [X] T047 [P] README: replaced "Próximos passos sugeridos" (it pointed at
      `/speckit-clarify` → `/speckit-plan` → `/speckit-tasks`/`/speckit-implement`, all
      now done) with a Build & Testes section and next steps pointing at
      `/speckit-analyze` and starting the next feature in its own worktree, in
      `README.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies
- **Foundational (Phase 2)**: depends on Setup — blocks all user stories
- **US1 (Phase 3)**: depends on Foundational only
- **US2 (Phase 4)**: depends on Foundational only (independently testable from US1, but
  reuses `ModuleRegistry` from Phase 2 the same way US1 does)
- **US3 (Phase 5)**: depends on Foundational + the `RoutingGraph`/`connect` machinery
  built in US2 (Phase 4), since removal must tear down connections created there
- **Polish (Phase 6)**: depends on US1 + US2 + US3 complete

### Within Each Story

- Tests (T014-T017, T024-T029, T041-T042) written first, must fail before their
  corresponding implementation task
- Contract/value types → registry/graph logic → reference module implementations →
  contract-compliance run per module

### Parallel Opportunities

- T002-T006 (Setup) in parallel
- T007-T010 (Foundational value types) in parallel; T013 in parallel with T012
- Within US1: T014-T017 in parallel; T022-T023 in parallel with each other (not with
  T018-T021, same `ModuleRegistry.kt` file)
- Within US2: T024-T029 in parallel; T030 parallel with Foundational leftovers;
  T037-T040 in parallel with each other
- Within US3: T041-T042 in parallel

---

## Parallel Example: User Story 2

```bash
# Launch all US2 tests together:
Task: "Integration test connect midi-generator→midi-logger in RoutingGraphConnectTest.kt"
Task: "Integration test type-mismatch rejection in RoutingGraphTypeMismatchTest.kt"
Task: "Integration test disconnect in RoutingGraphDisconnectTest.kt"
Task: "Integration test feedback-cycle rejection in RoutingGraphCycleRejectionTest.kt"
Task: "Integration test format-mismatch rejection in RoutingGraphFormatMismatchTest.kt"
Task: "Integration test fault isolation in RoutingGraphFaultIsolationTest.kt"

# Launch the two new reference modules together:
Task: "Implement MidiGeneratorModule.kt"
Task: "Implement MidiLoggerModule.kt"
```

---

## Implementation Strategy

### MVP First

1. Phase 1 (Setup) → Phase 2 (Foundational) → Phase 3 (US1)
2. **STOP and VALIDATE**: `oscillator` loads into an empty host, exposes ports, rejects
   an invalid module — matches SC-001's "load" half.

### Incremental Delivery

1. Setup + Foundational → foundation ready
2. US1 → validate independently (MVP)
3. US2 → validate independently (adds SC-001's "connect" half, SC-002)
4. US3 → validate independently (adds SC-001's "remove" half, SC-003)
5. Polish → SC-004 stress test + quickstart full run

## Notes

- [P] tasks touch different files with no unfinished dependency between them
- Commit after each task or logical group (per user's worktree-per-feature workflow,
  this all happens on the `001-core-host` branch/worktree, not on `main`)
- Verify tests fail before implementing
- Avoid: vague tasks, same-file conflicts, cross-story dependencies that break
  independence