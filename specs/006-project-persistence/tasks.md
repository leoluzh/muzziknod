# Tasks: Project Persistence

**Input**: Design documents from `/specs/006-project-persistence/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md,
contracts/project-file-schema.md, contracts/module-state-codec.md, quickstart.md

**Tests**: Included and REQUIRED — Constitution "Fluxo de Desenvolvimento" mandates
contract-style tests for anything the host's state model depends on, and FR-007/SC-001
require save→load fidelity to be provably exact; this feature also touches two existing
product modules' public APIs (sampler, and indirectly every effects/sequencer module via
codecs), so round-trip coverage per module type is required, not optional.

**Organization**: US1 (P1, save) and US2 (P1, load) are the two halves of one round
trip and ship together as the MVP; US1 builds the capture/write side (and every codec's
`capture()`), US2 builds the restore/read side (`restore()` for the same codecs) plus
`ProjectReader`. US3 (P2) extends `ProjectReader`/`SamplerCodec` with graceful
degradation on missing module types/sample files, so it depends on US2's `ProjectReader`
already existing. Ordered US1 → US2 → US3, matching spec.md's priorities.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Maps task to US1, US2, or US3
- File paths are exact, relative to repo root

---

## Phase 1: Setup (Shared Infrastructure)

- [X] T001 Add `:project-persistence` to root `settings.gradle.kts`; create directory
      skeleton (`project-persistence/src/commonMain/kotlin/dev/muzziknod/persistence/`,
      `.../model/`, `.../codec/`, `project-persistence/src/jvmMain/kotlin/dev/muzziknod/persistence/`,
      `project-persistence/src/commonTest/kotlin/dev/muzziknod/persistence/`,
      `.../codec/`, `project-persistence/src/jvmTest/kotlin/dev/muzziknod/persistence/`)
- [X] T002 [P] Add `kotlinx-serialization-json` version/library entry to
      `gradle/libs.versions.toml`, and the `org.jetbrains.kotlin.plugin.serialization`
      plugin coordinate (`apply false`) to root `build.gradle.kts` (research.md
      "Serialization format & library")
- [X] T003 [P] Create `project-persistence/build.gradle.kts` — KMP plugin, `jvm()`
      target (same JVM target as every other module), `kotlin.plugin.serialization`
      plugin applied, depends on `core-host`, `modules:midi-sequencer`,
      `modules:audio-effects`, `modules:sampler`; `kotlinx-serialization-json` on
      `commonMain`; `kotlin.test` + `kotlin-test-junit5` on `commonTest`/`jvmTest`

**Checkpoint**: `./gradlew build` runs (empty `:project-persistence` module compiles
alongside existing ones; serialization plugin resolves)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The DTOs every codec/writer/reader serializes against, the codec contract
itself, the file I/O boundary, the new host `Transport`, and sampler's `sourcePath`
field — shared groundwork no user story can build on top of until it exists.

- [X] T004 [P] Implement `Transport` in `core-host`: `TransportState` data class
      (`tempoBpm: Double`, `positionBeats: Double`, `isPlaying: Boolean`,
      `loopStart: Double?`, `loopEnd: Double?`), `Transport` class with
      `play()/pause()/stop()/setTempo(Double)/setPosition(Double)/setLoopRange(Double?,
      Double?)` (rejecting a half-specified loop range), `state: StateFlow<TransportState>`
      (research.md "Transport"; data-model.md "TransportSnapshot") in
      `core-host/src/commonMain/kotlin/dev/muzziknod/host/transport/Transport.kt`
- [X] T005 [P] Add an optional `sourcePath: String?` to `modules/sampler`'s
      `SampleZone`, and a matching optional `sourcePath: String? = null` parameter to
      `SamplerModule.loadSample(...)`, defaulting to `null` so every existing call site
      stays source-compatible (research.md "Sample file path tracking"; FR-006) in
      `modules/sampler/src/commonMain/kotlin/dev/muzziknod/modules/sampler/SampleZone.kt`
      and
      `modules/sampler/src/commonMain/kotlin/dev/muzziknod/modules/sampler/SamplerModule.kt`
- [X] T006 [P] Implement `@Serializable` DTOs per data-model.md: `ProjectSnapshot`
      (`schemaVersion`, `modules`, `connections`, `transport`), `ModuleSnapshot`
      (`instanceId`, `typeId`, `parameters: Map<String, Double>`,
      `moduleData: JsonElement?`), `ConnectionSnapshot`, `TransportSnapshot` in
      `project-persistence/src/commonMain/kotlin/dev/muzziknod/persistence/model/ProjectSnapshot.kt`,
      `ModuleSnapshot.kt`, `ConnectionSnapshot.kt`, `TransportSnapshot.kt` (depends on
      T003)
- [X] T007 [P] Implement `@Serializable` `SamplerData` (`zones: List<SampleZoneSnapshot>`)
      and `SampleZoneSnapshot` (`sourcePath`, `sampleId`, `rootNote`, `lowNote`,
      `highNote`, `gain`, `loopMode`) per data-model.md in
      `project-persistence/src/commonMain/kotlin/dev/muzziknod/persistence/model/SamplerData.kt`
      (depends on T003)
- [X] T008 [P] Implement `ProjectLoadResult(warnings: List<LoadWarning>)` and sealed
      `LoadWarning` (`MissingModuleType(typeId, instanceId)`,
      `MissingSampleFile(instanceId, sourcePath)`) per data-model.md in
      `project-persistence/src/commonMain/kotlin/dev/muzziknod/persistence/ProjectLoadResult.kt`
      (depends on T003)
- [X] T009 Implement the `ModuleStateCodec` interface (`typeId`,
      `capture(module: Module): ModuleSnapshot`,
      `restore(instanceId: String, snapshot: ModuleSnapshot): Module`) per
      contracts/module-state-codec.md in
      `project-persistence/src/commonMain/kotlin/dev/muzziknod/persistence/codec/ModuleStateCodec.kt`
      (depends on T006)
- [X] T010 Implement `ProjectPersistenceCatalog(codecs: Map<String, ModuleStateCodec>)`
      with `codecFor(typeId): ModuleStateCodec?`, constructed empty for now (codec
      registration lands per-codec in US1/US2) in
      `project-persistence/src/commonMain/kotlin/dev/muzziknod/persistence/ProjectPersistenceCatalog.kt`
      (depends on T009)
- [X] T011 [P] Declare `expect fun readProjectFile(path: String): String` and
      `expect fun writeProjectFile(path: String, content: String)` in
      `project-persistence/src/commonMain/kotlin/dev/muzziknod/persistence/ProjectFileIo.kt`
      (depends on T003)
- [X] T012 Implement the `actual` file I/O over `java.nio.file.Files` (read/write UTF-8,
      overwrite on write) in
      `project-persistence/src/jvmMain/kotlin/dev/muzziknod/persistence/ProjectFileIo.jvm.kt`
      (depends on T011)

**Checkpoint**: DTOs, the codec contract, the empty catalog, file I/O, the new
`Transport`, and sampler's `sourcePath` all compile. Ready for all user stories.

---

## Phase 3: User Story 1 - Save Current Work to a Project File (Priority: P1) 🎯 MVP (half)

**Goal**: A musician builds a session (module graph, connections, parameter values,
loaded samples, transport) and saves it to a project file; saving again overwrites the
same file, and "save as" writes a new one without touching the original.

**Independent Test**: Build a session with at least two connected modules and
non-default parameter values, save to a file, and confirm the file is created and
its decoded `ProjectSnapshot` contains the graph, connections, and parameter values.

### Tests for User Story 1

- [X] T013 [P] [US1] Integration test: `ProjectWriter.save()` on a session with a
      connected delay+reverb graph (non-default `mix`/`delayTimeMs`/`feedback` params)
      and a non-default `Transport` writes a JSON file whose decoded `ProjectSnapshot`
      contains matching modules, connections, parameters, and transport (FR-001,
      FR-002, FR-003, FR-004; US1 AC1) in
      `project-persistence/src/jvmTest/kotlin/dev/muzziknod/persistence/ProjectWriterSaveTest.kt`
- [X] T014 [P] [US1] Integration test: saving twice to the same path overwrites (second
      save's content fully replaces the first, no duplication/append); "save as" to a
      different path leaves the original file's bytes unchanged (FR-012; US1 AC2-3) in
      `project-persistence/src/jvmTest/kotlin/dev/muzziknod/persistence/ProjectFileIoTest.kt`
- [X] T015 [P] [US1] Unit test: `SamplerCodec.capture()` on a `SamplerModule` with
      loaded zones (including a `sourcePath`) produces a `ModuleSnapshot` whose
      `moduleData` decodes to a `SamplerData` with matching zones, and never contains
      raw sample audio bytes anywhere in the encoded output (FR-005, FR-006) in
      `project-persistence/src/commonTest/kotlin/dev/muzziknod/persistence/codec/SamplerCodecTest.kt`

### Implementation for User Story 1

- [X] T016 [P] [US1] Implement `DelayCodec`/`ReverbCodec`/`DistortionCodec`/`EqCodec`
      `capture()`: read each module's `StateFlow<Double>` parameter mirrors into
      `ModuleSnapshot.parameters` keyed by the same ids as their `ParameterSpec`s
      (FR-003) in
      `project-persistence/src/commonMain/kotlin/dev/muzziknod/persistence/codec/DelayCodec.kt`,
      `ReverbCodec.kt`, `DistortionCodec.kt`, `EqCodec.kt` (depends on T009)
- [X] T017 [P] [US1] Implement `MidiSequencerCodec.capture()`: bpm and pattern data
      into `parameters`/`moduleData` (FR-003) in
      `project-persistence/src/commonMain/kotlin/dev/muzziknod/persistence/codec/MidiSequencerCodec.kt`
      (depends on T009)
- [X] T018 [US1] Implement `SamplerCodec.capture()`: reads `SamplerModule.zones`, maps
      each `SampleZone` (including `sourcePath`) to a `SampleZoneSnapshot`, wraps them
      in `SamplerData`, and sets it as `ModuleSnapshot.moduleData` (FR-005, FR-006) in
      `project-persistence/src/commonMain/kotlin/dev/muzziknod/persistence/codec/SamplerCodec.kt`
      (depends on T007, T009, T005)
- [X] T019 [US1] Implement `ProjectWriter`: given `ModuleRegistry`, `RoutingGraph`,
      `Transport`, and `ProjectPersistenceCatalog`, builds a `ProjectSnapshot` (one
      `ModuleSnapshot` per registered instance via its type's codec, `connections` from
      `RoutingGraph.connections()`, `transport` from `Transport.state.value`), encodes
      it to JSON, and exposes `save(path: String)`/`saveAs(path: String)` over
      `writeProjectFile()` (FR-001, FR-002, FR-003, FR-004, FR-012) in
      `project-persistence/src/commonMain/kotlin/dev/muzziknod/persistence/ProjectWriter.kt`
      (depends on T016, T017, T018, T012, T004)
- [X] T020 [US1] Register `DelayCodec`/`ReverbCodec`/`DistortionCodec`/`EqCodec`/
      `MidiSequencerCodec`/`SamplerCodec` in a `defaultProjectPersistenceCatalog()`
      factory (mirrors `ui-desktop`'s `defaultModuleCatalog()`) in
      `project-persistence/src/commonMain/kotlin/dev/muzziknod/persistence/ProjectPersistenceCatalog.kt`
      (depends on T010, T016, T017, T018)

**Checkpoint**: Save half of User Story 1 is fully functional and independently
testable — a session can be saved, overwritten, or saved-as, producing a correct,
inspectable project file. (Full story value — reopening that file — completes with
User Story 2 below, since both are P1 and ship together.)

---

## Phase 4: User Story 2 - Load a Saved Project and Resume Work (Priority: P1) 🎯 MVP (other half)

**Goal**: A musician loads a previously saved project file and sees the exact same
module graph, connections, parameter values, transport state, and sampler content they
left behind.

**Independent Test**: Save a project with a known graph/parameter/transport/sample
configuration (via User Story 1's `ProjectWriter`), reset host state, load the file,
and confirm every module, connection, parameter value, transport setting, and sample
mapping matches what was saved.

### Tests for User Story 2

- [X] T021 [P] [US2] Integration test `ProjectRoundTripTest`: build a graph spanning
      every codec-backed module type, connect them, set non-default parameters and
      transport state, save via `ProjectWriter`, build a **fresh** `ModuleRegistry`/
      `RoutingGraph`/`Transport`, load via `ProjectReader`, and assert every module,
      connection, parameter value, and transport field matches exactly, with zero
      warnings (FR-007; SC-001, SC-002; US2 AC1-3) in
      `project-persistence/src/commonTest/kotlin/dev/muzziknod/persistence/ProjectRoundTripTest.kt`;
      the sampler-with-real-`sourcePath` half of AC4 needs actual file I/O, so it lives
      separately in
      `project-persistence/src/jvmTest/kotlin/dev/muzziknod/persistence/SamplerProjectRoundTripTest.kt`
      (SC-003; US2 AC4)
- [X] T022 [P] [US2] Unit test `TransportPersistenceTest`: capture/restore round trip
      of tempo, position, loop range, and play state via `TransportSnapshot` in
      isolation (FR-004; SC-002; US2 AC3) in
      `project-persistence/src/commonTest/kotlin/dev/muzziknod/persistence/TransportPersistenceTest.kt`
- [X] T023 [P] [US2] Unit test: `ProjectReader` rejects a `ProjectSnapshot` whose
      `schemaVersion` is newer than this build supports with a clear, typed error,
      without attempting to decode the rest of the file (FR-011) in
      `project-persistence/src/commonTest/kotlin/dev/muzziknod/persistence/SchemaVersionTest.kt`

### Implementation for User Story 2

- [X] T024 [P] [US2] Implement `DelayCodec`/`ReverbCodec`/`DistortionCodec`/`EqCodec`
      `restore()`: construct a new instance with the snapshot's `instanceId` and apply
      every captured parameter via the module's typed setters (FR-007) in
      `project-persistence/src/commonMain/kotlin/dev/muzziknod/persistence/codec/DelayCodec.kt`,
      `ReverbCodec.kt`, `DistortionCodec.kt`, `EqCodec.kt` (depends on T016)
- [X] T025 [P] [US2] Implement `MidiSequencerCodec.restore()` (FR-007) in
      `project-persistence/src/commonMain/kotlin/dev/muzziknod/persistence/codec/MidiSequencerCodec.kt`
      (depends on T017)
- [X] T026 [US2] Implement `SamplerCodec.restore()`: constructs a new `SamplerModule`
      and calls `loadSample(...)` for each `SampleZoneSnapshot`, reading bytes from
      `sourcePath` via `readProjectFile`-adjacent raw file read (FR-005, FR-007) in
      `project-persistence/src/commonMain/kotlin/dev/muzziknod/persistence/codec/SamplerCodec.kt`
      (depends on T018, T012)
- [X] T027 [US2] Add `Transport.restore(snapshot: TransportState)` wiring a
      `TransportSnapshot`/`TransportState` back onto a live `Transport` via
      `setTempo`/`setPosition`/`setLoopRange`/`play`/`pause`/`stop` (FR-004, FR-007) in
      `core-host/src/commonMain/kotlin/dev/muzziknod/host/transport/Transport.kt`
      (depends on T004)
- [X] T028 [US2] Implement `ProjectReader`: reads the file via `readProjectFile`,
      decodes and validates `schemaVersion` (FR-011) before decoding the rest, decodes
      the `ProjectSnapshot`, rebuilds each module via `ProjectPersistenceCatalog` +
      `ModuleRegistry.load()`, rebuilds connections via `RoutingGraph.connect()`,
      restores `Transport`, and returns a `ProjectLoadResult` (FR-007) in
      `project-persistence/src/commonMain/kotlin/dev/muzziknod/persistence/ProjectReader.kt`
      (depends on T024, T025, T026, T027, T012, T008)

**Checkpoint**: User Stories 1 AND 2 together form the full MVP — save and load
round-trip a project exactly, independently testable end to end.

---

## Phase 5: User Story 3 - Recover Gracefully When Referenced Content Is Missing (Priority: P2)

**Goal**: Loading a project whose sample files moved/were deleted, or whose module
types are no longer available, still loads everything it can and reports exactly
what's missing instead of failing the whole load.

**Independent Test**: Save a project referencing a sample file, delete or move that
sample file on disk, load the project, and confirm the rest of the project loads
normally while the specific missing sample is clearly flagged (and same for an
unrecognized module type).

### Tests for User Story 3

- [X] T029 [P] [US3] Integration test: a `ProjectSnapshot` JSON containing one module
      with an unrecognized `typeId` still loads every other module successfully, and
      `ProjectLoadResult.warnings` contains exactly one `MissingModuleType` naming that
      `typeId`/`instanceId` (FR-009; US3 AC2) in
      `project-persistence/src/commonTest/kotlin/dev/muzziknod/persistence/MissingModuleWarningTest.kt`
- [X] T030 [P] [US3] Integration test: a sampler zone whose `sourcePath` points at a
      deleted file loads the rest of the project normally, and `warnings` contains
      exactly one `MissingSampleFile` naming that path (FR-010; US3 AC1) in
      `project-persistence/src/jvmTest/kotlin/dev/muzziknod/persistence/MissingSampleWarningTest.kt`

### Implementation for User Story 3

- [X] T031 [US3] In `ProjectReader`, when `catalog.codecFor(typeId)` returns `null`,
      skip that `ModuleSnapshot` (don't add it to the registry, don't abort) and append
      a `LoadWarning.MissingModuleType` to the result instead (FR-009) in
      `project-persistence/src/commonMain/kotlin/dev/muzziknod/persistence/ProjectReader.kt`
      (depends on T028)
- [X] T032 [US3] In `SamplerCodec.restore()`, catch a file-read failure per
      `SampleZoneSnapshot` and skip that zone instead of throwing; surface the failed
      `sourcePath`s to `ProjectReader`, which appends one `LoadWarning.MissingSampleFile`
      per path (FR-010) in
      `project-persistence/src/commonMain/kotlin/dev/muzziknod/persistence/codec/SamplerCodec.kt`
      and
      `project-persistence/src/commonMain/kotlin/dev/muzziknod/persistence/ProjectReader.kt`
      (depends on T026, T031)

**Checkpoint**: All three user stories are independently functional — save, load, and
graceful degradation on missing module types/sample files all work.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T033 [P] Integration test `CorruptFileTest`: invalid JSON and a truncated file
      each produce a clear, typed load error — never an uncaught exception, never a
      partially-applied mutation to the live `ModuleRegistry`/`RoutingGraph`/
      `Transport` (FR-008) in
      `project-persistence/src/jvmTest/kotlin/dev/muzziknod/persistence/CorruptFileTest.kt`
- [ ] T034 Wire `ui-desktop`'s `HostViewModel` to own a `Transport` instance and add
      Save / Save As / Load actions calling `ProjectWriter`/`ProjectReader`, surfacing
      `ProjectLoadResult.warnings` to the user (plan.md "Structure Decision" follow-up
      integration) in `ui-desktop/src/jvmMain/kotlin/dev/muzziknod/ui/desktop/Main.kt`
      and `ui-desktop/src/commonMain/kotlin/dev/muzziknod/ui/state/HostViewModel.kt`
- [ ] T035 Run `quickstart.md`'s full validation
      (`./gradlew :project-persistence:jvmTest`) and confirm every scenario and
      SC-001 through SC-005 pass in one run
- [ ] T036 [P] Add `:project-persistence` to the root `README.md`'s module list

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion — BLOCKS all user stories
- **User Stories (Phase 3+)**: All depend on Foundational phase completion
  - US2's `restore()` implementations edit the same codec files US1's `capture()`
    implementations create — implement in priority order (US1 → US2), not in parallel,
    to avoid merge conflicts
  - US3 extends `ProjectReader`/`SamplerCodec`, both created in US2 — sequence after
    US2
- **Polish (Phase 6)**: Depends on all three user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) — no dependency on
  other stories; delivers the save half of the round trip on its own
- **User Story 2 (P1)**: Can start after Foundational, but its `restore()` tasks
  (T024-T028) edit the same codec files US1 creates (T016-T018) — sequence after US1;
  together US1+US2 are the MVP
- **User Story 3 (P2)**: Depends on `ProjectReader` (T028) and `SamplerCodec` (T026)
  from US2 already existing — sequence after US2

### Within Each User Story

- Tests written before implementation, and should fail (or fail to compile against
  not-yet-written codecs) until the story's implementation tasks land
- DTOs/contract/file-I-O before codecs before writer/reader orchestration
- Story complete (checkpoint) before moving to the next priority

### Parallel Opportunities

- T002/T003 (Setup) touch different files — safe to run together
- T004, T005, T006, T007, T008, T011 (Foundational) are `[P]` — different files, no
  shared dependency at the time each starts
- All test tasks within a single user story phase marked `[P]` can run in parallel
  (different files)
- T016/T017 (US1) and T024/T025 (US2) are `[P]` across the four effects codecs plus
  the sequencer codec — different files each
- T033 and T036 (Polish) are independent of each other and of T034/T035

---

## Parallel Example: User Story 1

```bash
# Launch all tests for User Story 1 together:
Task: "ProjectWriterSaveTest in project-persistence/src/jvmTest/.../ProjectWriterSaveTest.kt"
Task: "ProjectFileIoTest in project-persistence/src/jvmTest/.../ProjectFileIoTest.kt"
Task: "SamplerCodecTest in project-persistence/src/commonTest/.../codec/SamplerCodecTest.kt"

# Launch the independent capture() codecs together:
Task: "DelayCodec/ReverbCodec/DistortionCodec/EqCodec capture() in .../codec/*.kt"
Task: "MidiSequencerCodec capture() in .../codec/MidiSequencerCodec.kt"
```

---

## Implementation Strategy

### MVP First (User Stories 1 + 2)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL — blocks all stories)
3. Complete Phase 3: User Story 1 (save)
4. Complete Phase 4: User Story 2 (load)
5. **STOP and VALIDATE**: run `ProjectRoundTripTest` (T021) independently — a full
   save→load round trip is already the usable MVP
6. Deploy/demo if ready

### Incremental Delivery

1. Complete Setup + Foundational → DTOs, codec contract, file I/O, `Transport`, and
   sampler's `sourcePath` all ready
2. Add User Story 1 → save works, inspectable via decoded JSON → not yet demoable alone
   (nothing to load it back with)
3. Add User Story 2 → load works → test the full round trip independently → deploy/demo
   (MVP!)
4. Add User Story 3 → test independently → deploy/demo (projects survive moved samples
   and removed module types instead of failing to open)
5. Each story adds value without breaking the previous stories' checkpoints

---

## Notes

- `[P]` tasks = different files, no dependencies
- `[Story]` label maps task to specific user story for traceability
- US1 and US2 intentionally edit the same codec files (`capture()` then `restore()`) —
  implement in priority order, not in parallel across stories
- Verify tests fail (or fail to compile) before implementing
- Commit after each task or logical group
- Stop at any checkpoint to validate a story independently
