# Tasks: Módulo Sequenciador MIDI

**Input**: Design documents from `/specs/002-midi-sequencer/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md,
contracts/midi-sequencer-api.md, quickstart.md

**Tests**: Included and REQUIRED — Constitution "Fluxo de Desenvolvimento" mandates
contract tests for any module that produces or consumes audio/MIDI; this module emits
MIDI, and it's a real product module (not reference scaffolding), so it also gets full
behavioral coverage per user story.

**Organization**: Tasks are grouped by user story (US1 and US3 are both P1, US1 first
since US3 depends on US1's `process()` output; US2 is P2) to enable independent
implementation and testing of each.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Maps task to US1, US2, or US3
- File paths are exact, relative to repo root

---

## Phase 1: Setup (Shared Infrastructure)

- [X] T001 Add `modules:midi-sequencer` to root `settings.gradle.kts`; create
      `modules/midi-sequencer/` directory skeleton (`src/commonMain/kotlin/dev/muzziknod/modules/midisequencer/`,
      `src/commonTest/kotlin/dev/muzziknod/modules/midisequencer/`)
- [X] T002 [P] Create `modules/midi-sequencer/build.gradle.kts` — KMP plugin, `jvm()`
      target with `jvmTarget.set(JvmTarget.JVM_25)` (same as `core-host` and every
      `reference-modules/*`), depends on `core-host`, `kotlin.test` +
      `kotlin-test-junit5` on `commonTest`, `useJUnitPlatform()`

**Checkpoint**: `./gradlew build` runs (empty module compiles alongside existing ones)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Pattern/transport value types and the module skeleton every user story
builds on. No user story work starts before this phase is done.

- [X] T003 [P] Define `NoteEvent` and `Step` value types in
      `modules/midi-sequencer/src/commonMain/kotlin/dev/muzziknod/modules/midisequencer/Pattern.kt`
      (data-model.md)
- [X] T004 Define `Pattern` data class with `length`/`bpm`/`steps` and validation
      (`length >= 1`, BPM clamped to a sane range, per data-model.md) in the same file
      (depends on T003)
- [X] T005 [P] Define `Transport` internal state (`isPlaying`, `currentStep`,
      `sustainedNotes`) in
      `modules/midi-sequencer/src/commonMain/kotlin/dev/muzziknod/modules/midisequencer/Transport.kt`
- [X] T006 Implement `MidiSequencerModule` skeleton implementing `core-host`'s `Module`
      unchanged: `instanceId`, `contract` (`typeId = "midi-sequencer"`, one
      `Midi`/`Output` `PortSpec` named `"out"`), `onLoad()`/`onRemove()` no-ops, holds a
      `Pattern` and `Transport` field, in
      `modules/midi-sequencer/src/commonMain/kotlin/dev/muzziknod/modules/midisequencer/MidiSequencerModule.kt`
      (depends on T004, T005)
- [X] T007 [P] Subclass `core-host`'s `ModuleContractComplianceTests` (mandatory per
      Constitution "Fluxo de Desenvolvimento"), providing `MidiSequencerModule` via
      `createModule()`, in
      `modules/midi-sequencer/src/commonTest/kotlin/dev/muzziknod/modules/midisequencer/MidiSequencerContractTest.kt`
      (depends on T006)

**Checkpoint**: Module skeleton compiles; contract-compliance test passes with a
no-op `process()`. Ready for all user stories.

---

## Phase 3: User Story 1 - Programar e reproduzir um padrão de sequência (Priority: P1) 🎯 MVP

**Goal**: Program a step pattern, play it, and have the module emit the programmed
MIDI note events in step order every `processCycle()`, looping continuously until
stopped, with no stuck notes on stop.

**Independent Test**: Load the module, program a small pattern, `play()`, and observe
via `process()`'s `ProcessContext.writeMidi` output that events match the pattern in
order/timing across multiple loop iterations, with a clean `stop()`.

### Tests for User Story 1

- [X] T008 [P] [US1] Integration test: program a pattern (incl. an empty step that
      emits nothing), `play()`, verify events emitted in step order across ≥20 loop
      repetitions with no ordering drift, loop wraps correctly to step 0 (FR-001,
      FR-004, FR-005; SC-001; US1 AC1, AC2) in
      `modules/midi-sequencer/src/commonTest/kotlin/dev/muzziknod/modules/midisequencer/PatternPlaybackTest.kt`
- [X] T009 [P] [US1] Integration test: `stop()` while a note is sustained flushes its
      note-off on the very next `process()` call, no stuck notes (FR-007; SC-002; US1
      AC3) in
      `modules/midi-sequencer/src/commonTest/kotlin/dev/muzziknod/modules/midisequencer/PatternStopTest.kt`

### Implementation for User Story 1

- [X] T010 [US1] Implement `setLength(steps)`, `setBpm(bpm)`, `setStep(index, notes)`,
      `clearStep(index)` pattern-editing methods (FR-001, FR-002) in
      `MidiSequencerModule.kt` (depends on T006)
- [X] T011 [US1] Implement `play()`, `stop()`, `isPlaying`, `currentStep` transport
      control, idempotent per contracts/midi-sequencer-api.md (FR-003) in
      `MidiSequencerModule.kt` (depends on T006)
- [X] T012 [US1] Implement `process(context)`: emit note-offs for expired
      `sustainedNotes`, note-ons for `pattern.steps[currentStep]` when playing via
      `context.writeMidi("out", events)`, advance `currentStep` with loop-wrap (FR-004,
      FR-005) in `MidiSequencerModule.kt` (depends on T010, T011)
- [X] T013 [US1] Implement pending note-off flush on `stop()`, delivered on the next
      `process()` call regardless of repeated `stop()` calls (FR-007) in
      `MidiSequencerModule.kt` (depends on T012)

**Checkpoint**: US1 fully functional and independently testable —
`./gradlew :modules:midi-sequencer:jvmTest --tests "*PatternPlayback*" --tests "*PatternStop*"`.

---

## Phase 4: User Story 3 - Conectar o sequenciador a outros módulos via o grafo de roteamento (Priority: P1)

**Goal**: The sequencer's MIDI output reaches another module through `core-host`'s
existing `RoutingGraph`/`ModuleRegistry`, with zero changes to `core-host`; playback
keeps working with no output connections at all.

**Independent Test**: Load the sequencer plus a MIDI-sink test double into a
`RoutingGraph`, connect `sequencer.out → sink.in`, play, and verify the sink receives
every emitted event in cycle order.

### Tests for User Story 3

- [X] T014 [P] [US3] Add local `FakeMidiSink` module test double (one `Midi`/`Input`
      port, records every event it receives via `readMidi`) — mirrors `core-host`'s own
      `FakeModule` pattern rather than depending on `reference-modules:*` (wrong
      dependency direction), in
      `modules/midi-sequencer/src/commonTest/kotlin/dev/muzziknod/modules/midisequencer/FakeMidiSink.kt`
- [X] T015 [US3] Integration test: load `MidiSequencerModule` + `FakeMidiSink` into a
      `core-host` `RoutingGraph`/`ModuleRegistry`, connect `out → in`, `play()`, run
      several cycles, verify the sink's recorded events match exactly what the
      sequencer emitted, in cycle order (FR-004, FR-008; US3 AC1) in
      `modules/midi-sequencer/src/commonTest/kotlin/dev/muzziknod/modules/midisequencer/RoutingIntegrationTest.kt`
      (depends on T012, T014)
- [X] T016 [P] [US3] Integration test: sequencer with no output connections still
      advances its pattern/`currentStep` across multiple `process()` calls without
      throwing (FR-009; US3 AC2) in
      `modules/midi-sequencer/src/commonTest/kotlin/dev/muzziknod/modules/midisequencer/UnconnectedPlaybackTest.kt`
      (depends on T012)

### Implementation for User Story 3

- No new implementation needed: `core-host`'s `RoutingGraph.HostProcessContext.writeMidi`
  already stores by `(instanceId, portId)` unconditionally (works whether or not the
  port has an outgoing connection) — confirmed by reading `RoutingGraph.kt`; T012's
  `process()` from US1 satisfies FR-004/FR-008/FR-009 as-is. This phase is
  tests-only, proving the existing implementation against the real routing graph.

**Checkpoint**: US1 + US3 both independently functional —
`./gradlew :modules:midi-sequencer:jvmTest --tests "*Routing*" --tests "*Unconnected*"`.

---

## Phase 5: User Story 2 - Editar o padrão em tempo real (Priority: P2)

**Goal**: Pattern edits (notes, BPM, step count) apply correctly whether the module is
stopped or playing, without corrupting in-flight playback, including the length-reduction
edge case wrapping the current position into range.

**Independent Test**: Program an initial pattern, play it, edit a step not yet reached
in the current loop, and verify the next pass through that step reflects the edit (not
the original).

### Tests for User Story 2

- [X] T017 [P] [US2] Integration test: editing a step/BPM/length while stopped is
      reflected on the next `play()` (FR-006; US2 AC1) in
      `modules/midi-sequencer/src/commonTest/kotlin/dev/muzziknod/modules/midisequencer/PatternEditingStoppedTest.kt`
- [X] T018 [P] [US2] Integration test: editing a not-yet-reached step while playing is
      reflected the next time that step is reached, without interrupting steps already
      in flight (FR-006; SC-003; US2 AC2) in
      `modules/midi-sequencer/src/commonTest/kotlin/dev/muzziknod/modules/midisequencer/PatternEditingLiveTest.kt`
- [X] T019 [P] [US2] Integration test: changing BPM during playback doesn't disrupt
      playback; reducing `length` below the current `currentStep` wraps the position
      into the new valid range without throwing (FR-010; US2 AC3; Edge Cases) in
      `modules/midi-sequencer/src/commonTest/kotlin/dev/muzziknod/modules/midisequencer/PatternLengthWrapTest.kt`

### Implementation for User Story 2

- [X] T020 [US2] Implement the `setLength` position-wrap: if the new `length` is `<=`
      the current `currentStep`, wrap `currentStep` into the new valid range immediately,
      not on the next cycle (FR-010) in `MidiSequencerModule.kt` (depends on T010)
- [X] T021 [US2] Review/harden `setStep`/`setBpm`/`setLength` against being called
      mid-`process()`-cycle state (e.g. mutating `pattern.steps` must not affect a step
      already read this cycle) (FR-006) in `MidiSequencerModule.kt` (depends on T010,
      T012)

**Checkpoint**: All 3 user stories independently functional.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T022 [P] Stress test: 16-step pattern (SC-001 baseline size), 50 repeated loop
      cycles, verify deterministic ordering with no drift/crashes, in
      `modules/midi-sequencer/src/commonTest/kotlin/dev/muzziknod/modules/midisequencer/PatternStressTest.kt`
- [X] T023 Run `quickstart.md` scenarios 1–4 end-to-end; correct any command drift
      (e.g. actual Gradle task/test-class names) the same way 001-core-host's T046 did.
      Fixed Scenario 2's test-class filter (was `*PatternEditingTest*`, matched nothing
      — corrected to `*PatternEditing*` + `*PatternLengthWrap*`) and softened Scenario
      4's "remove" claim (removal itself is 001-core-host's `RoutingGraph.removeModule`,
      not new logic here). All scenarios pass.
- [X] T024 [P] README: add a `002-midi-sequencer` row to the "Features especificadas"
      table and mention `modules:midi-sequencer` in the "Build & testes" section, in
      `README.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies
- **Foundational (Phase 2)**: depends on Setup — blocks all user stories
- **US1 (Phase 3)**: depends on Foundational only
- **US3 (Phase 4)**: depends on Foundational + US1's `process()` implementation (T012) —
  it tests the existing `RoutingGraph` against real emitted events, doesn't add new
  sequencer logic
- **US2 (Phase 5)**: depends on Foundational + US1's pattern-editing methods (T010) and
  `process()` (T012), since it hardens/extends behavior US1 already implements
- **Polish (Phase 6)**: depends on US1 + US2 + US3 complete

### Within Each Story

- Tests (T008-T009, T015-T016, T017-T019) written first, must fail before their
  corresponding implementation task
- Value types → module skeleton → transport/pattern methods → `process()` → edge-case
  hardening

### Parallel Opportunities

- T001-T002 (Setup) — T002 in parallel with T001 only after directory exists
- T003, T005 (Foundational value types) in parallel; T007 in parallel with nothing
  (needs T006 first)
- Within US1: T008-T009 in parallel with each other; T010-T011 both depend only on
  T006 so can run in parallel with each other, T012 needs both
- Within US3: T014 in parallel with Foundational leftovers; T016 in parallel with T015
- Within US2: T017-T019 in parallel with each other

---

## Parallel Example: User Story 1

```bash
# Launch both US1 tests together:
Task: "Integration test pattern playback/loop in PatternPlaybackTest.kt"
Task: "Integration test stop() note-off flush in PatternStopTest.kt"

# Launch the two independent method groups together:
Task: "Implement pattern-editing methods in MidiSequencerModule.kt"
Task: "Implement play()/stop() transport control in MidiSequencerModule.kt"
```

---

## Implementation Strategy

### MVP First

1. Phase 1 (Setup) → Phase 2 (Foundational) → Phase 3 (US1)
2. **STOP and VALIDATE**: a programmed pattern plays in loop, stops cleanly with no
   stuck notes — matches SC-001/SC-002.

### Incremental Delivery

1. Setup + Foundational → foundation ready
2. US1 → validate independently (MVP)
3. US3 → validate independently (adds SC-004's "connect" half, proves FR-008 against
   the real `RoutingGraph`)
4. US2 → validate independently (adds SC-003, the length-wrap edge case)
5. Polish → stress test + quickstart full run + README

## Notes

- [P] tasks touch different files (or independent regions with no unfinished
  dependency) with no unfinished dependency between them
- Commit after each task or logical group (worktree-per-feature workflow — this all
  happens on the `002-midi-sequencer` branch/worktree, not on `main`)
- Verify tests fail before implementing
- Avoid: vague tasks, same-file conflicts, cross-story dependencies that break
  independence