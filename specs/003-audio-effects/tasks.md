# Tasks: Módulos de Efeitos de Áudio (Reverb/Delay/Distortion/EQ)

**Input**: Design documents from `/specs/003-audio-effects/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md,
contracts/audio-effects-api.md, quickstart.md

**Tests**: Included and REQUIRED — Constitution "Fluxo de Desenvolvimento" mandates
contract tests for any module that produces or consumes audio; these four modules
process audio, and this is a real product module set (not reference scaffolding), so
each also gets full behavioral coverage per user story.

**Organization**: All three user stories are P1 (spec has no P2/P3 here). Ordered
US1 → US2 → US3 since US2's live-parameter behavior extends the per-effect DSP US1
builds, and US3's routing-chain test needs US1's `process()` output to exist first —
same dependency reasoning 002-midi-sequencer used for its own P1 stories.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Maps task to US1, US2, or US3
- File paths are exact, relative to repo root

---

## Phase 1: Setup (Shared Infrastructure)

- [ ] T001 Add `modules:audio-effects` to root `settings.gradle.kts`; create
      `modules/audio-effects/` directory skeleton (`src/commonMain/kotlin/dev/muzziknod/modules/audioeffects/`,
      `src/commonTest/kotlin/dev/muzziknod/modules/audioeffects/`)
- [ ] T002 [P] Create `modules/audio-effects/build.gradle.kts` — KMP plugin, `jvm()`
      target (same JVM target as `core-host`/`modules/midi-sequencer`), depends on
      `core-host`, `kotlin.test` + `kotlin-test-junit5` on `commonTest`,
      `useJUnitPlatform()`

**Checkpoint**: `./gradlew build` runs (empty module compiles alongside existing ones)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared DSP primitives and the four module skeletons (contract-compliant,
passthrough `process()`) every user story builds on. No user story work starts before
this phase is done.

- [ ] T003 [P] Implement `ParameterSmoother` (linear ramp of a `Double` toward a
      target over a fixed sample window, per data-model.md) in
      `modules/audio-effects/src/commonMain/kotlin/dev/muzziknod/modules/audioeffects/ParameterSmoother.kt`
- [ ] T004 [P] Implement `WetDryMixer` (stateless `dry * (1 - mix) + wet * mix`
      crossfade, per data-model.md) in
      `modules/audio-effects/src/commonMain/kotlin/dev/muzziknod/modules/audioeffects/WetDryMixer.kt`
- [ ] T005 [P] `ReverbModule` skeleton implementing `core-host`'s `Module` unchanged:
      `instanceId`, `contract` (`typeId = "reverb"`, `in`/`out` Audio `PortSpec`s,
      `mix`/`decayMs`/`roomSize` `ParameterSpec`s per data-model.md), `onLoad()`
      allocates buffers, `process()` copies `in` straight to `out` (passthrough
      placeholder), `onRemove()` no-op, in
      `modules/audio-effects/src/commonMain/kotlin/dev/muzziknod/modules/audioeffects/ReverbModule.kt`
- [ ] T006 [P] `DelayModule` skeleton, same shape as T005 (`typeId = "delay"`,
      `mix`/`delayTimeMs`/`feedback` `ParameterSpec`s, passthrough `process()`), in
      `modules/audio-effects/src/commonMain/kotlin/dev/muzziknod/modules/audioeffects/DelayModule.kt`
- [ ] T007 [P] `DistortionModule` skeleton, same shape as T005 (`typeId =
      "distortion"`, `mix`/`drive`/`tone` `ParameterSpec`s, passthrough `process()`),
      in
      `modules/audio-effects/src/commonMain/kotlin/dev/muzziknod/modules/audioeffects/DistortionModule.kt`
- [ ] T008 [P] `EqModule` skeleton with `EqBand` enum (`Low`, `Mid`, `High`), same
      shape as T005 (`typeId = "eq"`, 9 `ParameterSpec`s — freq/gain/Q per band per
      data-model.md, no `mix` parameter, passthrough `process()`), in
      `modules/audio-effects/src/commonMain/kotlin/dev/muzziknod/modules/audioeffects/EqModule.kt`
- [ ] T009 [P] Subclass `core-host`'s `ModuleContractComplianceTests` for
      `ReverbModule` (depends on T005) in
      `modules/audio-effects/src/commonTest/kotlin/dev/muzziknod/modules/audioeffects/ReverbContractTest.kt`
- [ ] T010 [P] Subclass `ModuleContractComplianceTests` for `DelayModule` (depends on
      T006) in
      `modules/audio-effects/src/commonTest/kotlin/dev/muzziknod/modules/audioeffects/DelayContractTest.kt`
- [ ] T011 [P] Subclass `ModuleContractComplianceTests` for `DistortionModule`
      (depends on T007) in
      `modules/audio-effects/src/commonTest/kotlin/dev/muzziknod/modules/audioeffects/DistortionContractTest.kt`
- [ ] T012 [P] Subclass `ModuleContractComplianceTests` for `EqModule` (depends on
      T008) in
      `modules/audio-effects/src/commonTest/kotlin/dev/muzziknod/modules/audioeffects/EqContractTest.kt`

**Checkpoint**: All four module types compile, satisfy the `Module` contract, and
pass compliance tests as inert passthroughs. Ready for all user stories.

---

## Phase 3: User Story 1 - Aplicar efeitos em cadeia a um sinal de áudio (Priority: P1) 🎯 MVP

**Goal**: Each of the four effects runs its real DSP algorithm and blends it against
the dry signal via wet/dry mix — 100% dry is untouched input, 100% wet is fully
processed, in between is a proportional blend. An unconnected input processes
silently without error.

**Independent Test**: Feed a test signal into one effect module at a time with the
default `mix`, assert `mix = 0.0` output equals input exactly, `mix = 1.0` output
contains no trace of the dry signal, `mix = 0.5` is a proportional blend.

### Tests for User Story 1

- [ ] T013 [P] [US1] Integration test: for each of the four module types, `mix = 0.0`
      output is sample-identical to input, `mix = 1.0` output has no dry-signal
      component, `mix = 0.5` output is a proportional blend (FR-003, FR-004, FR-005;
      SC-001, SC-002; US1 AC1-3) in
      `modules/audio-effects/src/commonTest/kotlin/dev/muzziknod/modules/audioeffects/WetDryMixTest.kt`
- [ ] T014 [P] [US1] Integration test: for each of the four module types, no input
      connected (`readAudio` returns an empty buffer) produces silent output with no
      exception (FR-014; Edge Cases) in
      `modules/audio-effects/src/commonTest/kotlin/dev/muzziknod/modules/audioeffects/SilentInputTest.kt`

### Implementation for User Story 1

- [ ] T015 [US1] Implement `DelayLine` circular buffer and replace `DelayModule`'s
      passthrough `process()` with the real single-tap delay (write, read at
      `delayTimeMs`-derived offset, apply `feedback`, blend via `WetDryMixer` using
      current `mix`) (FR-006, FR-009) in `DelayModule.kt` (depends on T004, T006)
- [ ] T016 [US1] Implement `CombFilter`/`AllpassFilter` and replace `ReverbModule`'s
      passthrough `process()` with the real Schroeder reverb (4 parallel combs → 2
      series allpass, `roomSize`/`decayMs`-derived coefficients), blend via
      `WetDryMixer` (FR-007, FR-009) in `ReverbModule.kt` (depends on T004, T005)
- [ ] T017 [US1] Replace `DistortionModule`'s passthrough `process()` with the real
      soft-clip waveshaper (`tanh(drive * x) / tanh(drive)`) followed by a one-pole
      lowpass `tone` filter, blend via `WetDryMixer` (FR-008) in
      `DistortionModule.kt` (depends on T004, T007)
- [ ] T018 [US1] Implement `Biquad` (RBJ peaking-EQ coefficients) and replace
      `EqModule`'s passthrough `process()` with 3 bands run in series (no `mix`
      crossfade — 0 dB gain on every band is already passthrough) (FR-009) in
      `EqModule.kt` (depends on T008)

**Checkpoint**: US1 fully functional and independently testable —
`./gradlew :modules:audio-effects:jvmTest --tests "*WetDryMixTest*" --tests "*SilentInputTest*"`.

---

## Phase 4: User Story 2 - Ajustar parâmetros de cada efeito individualmente (Priority: P1)

**Goal**: Every effect's parameters can be changed in real time via public setters,
clamped to their declared range and smoothed to avoid audible artifacts, without
interrupting in-flight processing.

**Independent Test**: Process a continuous test signal through an effect, call a
setter mid-stream (e.g. `DelayModule.setDelayTimeMs`), and verify the change is
reflected within the smoothing window with no sample-to-sample jump beyond it.

### Tests for User Story 2

- [ ] T019 [P] [US2] Unit test: `ParameterSmoother.advance()` ramps linearly toward
      a new target within its window and holds exactly at target once reached, no
      overshoot/oscillation (FR-005, FR-010; SC-003) in
      `modules/audio-effects/src/commonTest/kotlin/dev/muzziknod/modules/audioeffects/ParameterSmoothingTest.kt`
- [ ] T020 [P] [US2] Integration test: `setDecayMs`/`setRoomSize` on `ReverbModule`
      change output behavior across subsequent cycles without interrupting
      processing (US2 AC2) in
      `modules/audio-effects/src/commonTest/kotlin/dev/muzziknod/modules/audioeffects/ReverbDspTest.kt`
- [ ] T021 [P] [US2] Integration test: `setDelayTimeMs`/`setFeedback` on
      `DelayModule` change output behavior; delay timing stays correct across
      44.1kHz/48kHz/96kHz `sampleRate` constructor values (FR-009; US2 AC1) in
      `modules/audio-effects/src/commonTest/kotlin/dev/muzziknod/modules/audioeffects/DelayDspTest.kt`
- [ ] T022 [P] [US2] Integration test: `setDrive`/`setTone` on `DistortionModule`
      change output behavior across subsequent cycles (US2 AC3) in
      `modules/audio-effects/src/commonTest/kotlin/dev/muzziknod/modules/audioeffects/DistortionDspTest.kt`
- [ ] T023 [P] [US2] Integration test: `setBandFrequency`/`setBandGain`/`setBandQ`
      per `EqBand` on `EqModule` change output behavior across subsequent cycles
      (US2 AC4) in
      `modules/audio-effects/src/commonTest/kotlin/dev/muzziknod/modules/audioeffects/EqDspTest.kt`
- [ ] T024 [P] [US2] Integration test: out-of-range setter arguments are clamped to
      the declared `ParameterSpec.range` on all four module types, never throw
      (FR-013; US2 AC5, Edge Cases) in
      `modules/audio-effects/src/commonTest/kotlin/dev/muzziknod/modules/audioeffects/ParameterClampingTest.kt`

### Implementation for User Story 2

- [ ] T025 [US2] Add `setMix`/`setDelayTimeMs`/`setFeedback` to `DelayModule`,
      each clamping to its `ParameterSpec.range` and routing through a
      `ParameterSmoother` (FR-005, FR-010, FR-013) in `DelayModule.kt` (depends on
      T003, T015)
- [ ] T026 [US2] Add `setMix`/`setDecayMs`/`setRoomSize` to `ReverbModule`, same
      clamp+smooth pattern as T025 in `ReverbModule.kt` (depends on T003, T016)
- [ ] T027 [US2] Add `setMix`/`setDrive`/`setTone` to `DistortionModule`, same
      clamp+smooth pattern as T025 in `DistortionModule.kt` (depends on T003, T017)
- [ ] T028 [US2] Add `setBandFrequency`/`setBandGain`/`setBandQ` to `EqModule`
      (recomputes the affected band's `Biquad` coefficients), clamped and smoothed
      per parameter, same pattern as T025 in `EqModule.kt` (depends on T003, T018)

**Checkpoint**: US1 + US2 both independently functional.

---

## Phase 5: User Story 3 - Encadear múltiplos efeitos via grafo de roteamento (Priority: P1)

**Goal**: The four effect types connect in sequence through `core-host`'s existing
`RoutingGraph`, with zero changes to `core-host`; a fully processed signal reaches
the chain's sink every cycle, in connection order.

**Independent Test**: Load a signal-generator test double, one instance of each of
the four effect types, and a sink test double into a `RoutingGraph`, connect them
generator → EQ → distortion → delay → reverb → sink, and verify the sink receives
the fully processed signal each cycle.

### Tests for User Story 3

- [ ] T029 [US3] Integration test: load a generator test double + `EqModule` +
      `DistortionModule` + `DelayModule` + `ReverbModule` + a sink test double into a
      `core-host` `RoutingGraph`/`ModuleRegistry`, connect in sequence, run several
      cycles, verify the sink's recorded signal reflects processing by all four
      effects in connection order (FR-001, FR-011, FR-012; SC-004; US3 AC1) in
      `modules/audio-effects/src/commonTest/kotlin/dev/muzziknod/modules/audioeffects/EffectsChainRoutingTest.kt`
      (depends on T015, T016, T017, T018)
- [ ] T030 [P] [US3] Integration test: removing one effect module from an active
      chain does not auto-reconnect its neighbors — same deferred-removal behavior
      already proven by `core-host` (FR-012; US3 AC2, mirrors 001-core-host FR-009)
      in
      `modules/audio-effects/src/commonTest/kotlin/dev/muzziknod/modules/audioeffects/EffectsChainRemovalTest.kt`
      (depends on T029)

### Implementation for User Story 3

- No new implementation needed: `core-host`'s `RoutingGraph` already routes any
  module satisfying the `Module` contract (confirmed by reading `RoutingGraph.kt`);
  T015-T018's `process()` implementations from US1 satisfy FR-001/FR-011/FR-012 as
  written. This phase is tests-only, proving the existing implementation against
  the real routing graph with all four effect types chained together.

**Checkpoint**: US1 + US2 + US3 all independently functional —
`./gradlew :modules:audio-effects:jvmTest --tests "*EffectsChain*"`.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T031 [P] Stress test: a 4-effect chain processing continuously for a large
      number of cycles (proxy for SC-003's 30-minute continuous-processing claim)
      with no exception/crash and stable output levels (no runaway feedback), in
      `modules/audio-effects/src/commonTest/kotlin/dev/muzziknod/modules/audioeffects/EffectsChainStressTest.kt`
- [ ] T032 Run `quickstart.md` scenarios 1-5 end-to-end; correct any command drift
      (actual Gradle task/test-class names) the same way 001-core-host's T046 and
      002-midi-sequencer's T023 did
- [ ] T033 [P] README: add a `003-audio-effects` row to the "Features especificadas"
      table and mention `modules:audio-effects` in the "Build & testes" section, in
      `README.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies
- **Foundational (Phase 2)**: depends on Setup — blocks all user stories
- **US1 (Phase 3)**: depends on Foundational only
- **US2 (Phase 4)**: depends on Foundational + US1's per-effect `process()`
  implementations (T015-T018), since live parameters drive the same DSP US1 builds
- **US3 (Phase 5)**: depends on Foundational + US1's `process()` implementations
  (T015-T018) — it tests the existing `RoutingGraph` against real processed
  signal, doesn't add new effect logic
- **Polish (Phase 6)**: depends on US1 + US2 + US3 complete

### Within Each Story

- Tests (T013-T014, T019-T024, T029-T030) written first, must fail before their
  corresponding implementation task
- Shared primitives → module skeletons → contract tests → real DSP → live
  parameter setters → routing integration

### Parallel Opportunities

- T001-T002 (Setup) — T002 only after T001's directory exists
- T003-T012 (Foundational) — T003, T004 in parallel; T005-T008 in parallel with
  each other and with T003/T004; T009-T012 each in parallel once their respective
  skeleton (T005-T008) exists
- Within US1: T013-T014 in parallel with each other; T015-T018 in parallel with
  each other (different files, independent effects)
- Within US2: T019-T024 in parallel with each other; T025-T028 in parallel with
  each other (different files, independent effects)
- Within US3: T030 in parallel with nothing (depends on T029)

---

## Parallel Example: User Story 1

```bash
# Launch both US1 tests together:
Task: "Wet/dry identity + blend test across all four types in WetDryMixTest.kt"
Task: "Silent-input test across all four types in SilentInputTest.kt"

# Launch all four DSP implementations together (independent files):
Task: "Real delay algorithm in DelayModule.kt"
Task: "Real Schroeder reverb in ReverbModule.kt"
Task: "Real soft-clip distortion in DistortionModule.kt"
Task: "Real 3-band EQ in EqModule.kt"
```

---

## Implementation Strategy

### MVP First

1. Phase 1 (Setup) → Phase 2 (Foundational) → Phase 3 (US1)
2. **STOP and VALIDATE**: all four effects process real audio with correct
   wet/dry behavior — matches SC-001/SC-002.

### Incremental Delivery

1. Setup + Foundational → foundation ready (four contract-compliant passthrough
   skeletons)
2. US1 → validate independently (MVP: real DSP + wet/dry)
3. US2 → validate independently (adds SC-003, live parameter control)
4. US3 → validate independently (adds SC-004, proves the full chain via the real
   `RoutingGraph`)
5. Polish → stress test + quickstart full run + README

## Notes

- [P] tasks touch different files with no unfinished dependency between them
- Commit after each task or logical group (worktree-per-feature workflow — this
  all happens on the `003-audio-effects` branch/worktree, not on `main`)
- Verify tests fail before implementing
- Avoid: vague tasks, same-file conflicts, cross-story dependencies that break
  independence
