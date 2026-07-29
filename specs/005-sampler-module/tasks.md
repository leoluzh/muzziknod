# Tasks: Sampler Module

**Input**: Design documents from `/specs/005-sampler-module/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md,
contracts/sampler-api.md, quickstart.md

**Tests**: Included and REQUIRED — Constitution "Fluxo de Desenvolvimento" mandates
contract tests for any module that produces or consumes audio/MIDI; this module does
both, and it's a real product module (not reference scaffolding), so it also gets full
behavioral coverage per user story.

**Organization**: Ordered US1 (P1) → US2 (P2) → US3 (P3), matching spec.md's
priorities. US2's pitch-shift math extends the trigger flow US1 builds; US3's
polyphony/stealing/release-fade work extends the single-voice playback US1/US2
establish — same incremental-extension shape 003-audio-effects used for its own
three P1 stories.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Maps task to US1, US2, or US3
- File paths are exact, relative to repo root

---

## Phase 1: Setup (Shared Infrastructure)

- [X] T001 Add `modules:sampler` to root `settings.gradle.kts`; create
      `modules/sampler/` directory skeleton (`src/commonMain/kotlin/dev/muzziknod/modules/sampler/`,
      `src/jvmMain/kotlin/dev/muzziknod/modules/sampler/`,
      `src/commonTest/kotlin/dev/muzziknod/modules/sampler/`,
      `src/jvmTest/kotlin/dev/muzziknod/modules/sampler/`)
- [X] T002 [P] Create `modules/sampler/build.gradle.kts` — KMP plugin, `jvm()` target
      (same JVM target as `core-host`/`modules/midi-sequencer`/`modules/audio-effects`),
      depends on `core-host`, `kotlin.test` + `kotlin-test-junit5` on `commonTest`/
      `jvmTest`, `useJUnitPlatform()`. No new library coordinates needed —
      `javax.sound.sampled` ships with the JDK (research.md "Dependencies").

**Checkpoint**: `./gradlew build` runs (empty module compiles alongside existing ones)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared entities, the platform decoder boundary, and a contract-compliant
but not-yet-MIDI-wired module skeleton every user story builds on. No user story work
starts before this phase is done.

- [X] T003 [P] Implement `LoopMode` enum (`OneShot`, `Loop`), `DecodedAudio` data class
      (`samples: FloatArray`), and `Sample` data class (`id`, `data: FloatArray`,
      `sourceSampleRate: Int`), per data-model.md, in
      `modules/sampler/src/commonMain/kotlin/dev/muzziknod/modules/sampler/Sample.kt`
- [X] T004 Implement `SampleZone` data class (`sample`, `rootNote`, `lowNote = 0`,
      `highNote = 127`, `gain = 1.0`, `loopMode = LoopMode.OneShot`), per
      data-model.md, in
      `modules/sampler/src/commonMain/kotlin/dev/muzziknod/modules/sampler/SampleZone.kt`
      (depends on T003)
- [X] T005 [P] Declare `expect fun decodeSample(bytes: ByteArray, targetSampleRate: Int): DecodedAudio`
      in
      `modules/sampler/src/commonMain/kotlin/dev/muzziknod/modules/sampler/SampleDecoder.kt`
      (research.md "Platform split for decoding") (depends on T003)
- [X] T006 Implement the `actual decodeSample()` using
      `javax.sound.sampled.AudioSystem`: read WAV/AIFF via `AudioInputStream`, convert
      16-bit/24-bit PCM and 32-bit float frames to normalized `Float32` (`-1.0..1.0`),
      downmix multi-channel frames to mono by averaging channels, linear-interpolation
      resample from the source frame rate to `targetSampleRate`, and throw
      (`IOException`/`UnsupportedAudioFileException`) on a missing, corrupt, or
      unsupported file (FR-001, FR-002; research.md "Sample-rate conversion at load
      time") in
      `modules/sampler/src/jvmMain/kotlin/dev/muzziknod/modules/sampler/SampleDecoder.jvm.kt`
      (depends on T005)
- [X] T007 [P] Implement `Voice`: `VoiceState` enum (`Free`, `Playing`, `Releasing`),
      fields `state`, `zone: SampleZone?`, `position: Int`, `note: Int`,
      `gain: Double`, `triggerOrder: Long`; `renderNextSample()` reads
      `zone.sample.data[position]` directly (unity-ratio playback for now — pitch
      shifting lands in US2), advances `position` by 1, and transitions `Playing ->
      Free` once `position` reaches the end of `zone.sample.data` (data-model.md
      "Voice") in
      `modules/sampler/src/commonMain/kotlin/dev/muzziknod/modules/sampler/Voice.kt`
      (depends on T004)
- [X] T008 Implement `VoicePool`: fixed-size `Array<Voice>(maxVoices)` allocated once
      at construction, `triggerCounter: Long`; `trigger(zone, note, velocity)` claims
      the first `Free` voice and no-ops if none is free (stealing lands in US3);
      `release(note)` is a no-op stub for now (loop-release lands in US3);
      `renderNextSample()` sums every non-`Free` voice's `Voice.renderNextSample()`
      (data-model.md "VoicePool") in
      `modules/sampler/src/commonMain/kotlin/dev/muzziknod/modules/sampler/VoicePool.kt`
      (depends on T007)
- [X] T009 Implement the `SamplerModule` skeleton: `Module` interface
      (`instanceId`, `contract` with `typeId = "sampler"`, one Midi `"in"` port, one
      Audio `"out"` port at the constructor `sampleRate`, no `ParameterSpec`s per
      contracts/sampler-api.md), `onLoad()` allocates a `VoicePool(maxVoices)` and a
      mono output `AudioBuffer` once, `process()` currently only renders
      `voicePool.renderNextSample()` into the output buffer (MIDI dispatch lands in
      US1), `onRemove()` no-op, plus an empty `zones: MutableList<SampleZone>` (no
      `loadSample()`/`unloadSample()` wiring yet — lands in US1) in
      `modules/sampler/src/commonMain/kotlin/dev/muzziknod/modules/sampler/SamplerModule.kt`
      (depends on T004, T006, T008)
- [X] T010 [P] Subclass `core-host`'s `ModuleContractComplianceTests` for
      `SamplerModule` (depends on T009) in
      `modules/sampler/src/commonTest/kotlin/dev/muzziknod/modules/sampler/SamplerContractTest.kt`

**Checkpoint**: All four module types compile on both `commonMain`/`jvmMain`, satisfy
the `Module` contract as an inert (no real MIDI-triggered playback yet) module, and
pass compliance tests. Ready for all user stories.

---

## Phase 3: User Story 1 - Load and Trigger a Sample (Priority: P1) 🎯 MVP

**Goal**: A musician loads a sample, maps it to a root note, triggers it via MIDI
note-on/velocity, and hears correct one-shot playback that finishes even if note-off
arrives early; bad loads and unmapped notes are handled gracefully.

**Independent Test**: Load one WAV via `loadSample()`, send a note-on for its root
note at velocity 127, assert the rendered output matches the sample data at unity
gain; send a note-on for an unmapped note and assert nothing triggers; feed a corrupt
file to `loadSample()` and assert `SampleLoadResult.Failed` with no crash.

### Tests for User Story 1

- [X] T011 [P] [US1] Integration test: `decodeSample()` on a real 16-bit/44.1kHz WAV
      fixture and a real 24-bit/48kHz WAV fixture each resolves to a non-empty mono
      `FloatArray` at the requested target sample rate (FR-001) in
      `modules/sampler/src/jvmTest/kotlin/dev/muzziknod/modules/sampler/SampleDecoderWavTest.kt`
- [X] T012 [P] [US1] Integration test: `decodeSample()` on a real 16-bit AIFF fixture
      resolves correctly, same assertions as T011 (FR-001) in
      `modules/sampler/src/jvmTest/kotlin/dev/muzziknod/modules/sampler/SampleDecoderAiffTest.kt`
- [X] T013 [P] [US1] Integration test: `SamplerModule.loadSample()` with a missing
      file path, a truncated/corrupt byte buffer, and an unsupported format each
      return `SampleLoadResult.Failed` (never throw) within 1 second of wall-clock
      time per call, and `zones` stays unchanged after each failure; repeat each
      failure case 20x in a loop to confirm zero crashes across repeated
      failure-injection (FR-002; SC-005) in
      `modules/sampler/src/jvmTest/kotlin/dev/muzziknod/modules/sampler/SampleDecoderErrorTest.kt`
- [X] T014 [P] [US1] Integration test: build a `SamplerModule` with a synthetic
      in-memory `Sample` (bypassing the decoder) mapped `OneShot` at root note 60; a
      note-on for 60 at velocity 127 triggers a voice whose rendered output matches
      the source data at unity gain; a note-on for note 61 (no covering zone)
      triggers nothing; a note-on with velocity 0 is treated as note-off; a note-off
      received before playback ends does not stop the `OneShot` voice (FR-003,
      FR-011, FR-012; US1 AC1-3) in
      `modules/sampler/src/commonTest/kotlin/dev/muzziknod/modules/sampler/OneShotVsLoopTest.kt`
- [X] T015 [P] [US1] Integration test: the same triggered voice's output amplitude
      scales linearly with note-on velocity (127 vs. 64 vs. 1), and an additional
      per-zone `gain` multiplies on top of velocity scaling (FR-004, FR-007) in
      `modules/sampler/src/commonTest/kotlin/dev/muzziknod/modules/sampler/VelocityGainTest.kt`

### Implementation for User Story 1

- [X] T016 [US1] Wire `SamplerModule.loadSample()`/`unloadSample()`: call
      `decodeSample(bytes, sampleRate)`, wrap the result into a `Sample` +
      `SampleZone`, append/remove it from the internal `zones` list, and catch decode
      failures into `SampleLoadResult.Failed(reason)` without mutating `zones`
      (FR-001, FR-002) in
      `modules/sampler/src/commonMain/kotlin/dev/muzziknod/modules/sampler/SamplerModule.kt`
      (depends on T009, T006)
- [X] T017 [US1] Implement MIDI dispatch in `SamplerModule.process()`: for each
      `MidiEvent` read from `"in"`, treat status `0x90` with `data2 == 0` (or status
      `0x80`) as note-off; on note-on, look up the first `SampleZone` whose
      `[lowNote, highNote]` contains `data1` and call `voicePool.trigger(zone, data1,
      data2)`, silently ignoring note-on when no zone matches (FR-003, FR-006,
      FR-011, FR-012) in
      `modules/sampler/src/commonMain/kotlin/dev/muzziknod/modules/sampler/SamplerModule.kt`
      (depends on T016)
- [X] T018 [US1] Implement velocity/gain math in `VoicePool.trigger()`: the claimed
      `Voice.gain = zone.gain * (velocity / 127.0)` (FR-004, FR-007) in
      `modules/sampler/src/commonMain/kotlin/dev/muzziknod/modules/sampler/VoicePool.kt`
      (depends on T008)
- [X] T019 [US1] Implement one-shot completion and note-off-ignoring: confirm
      `Voice.renderNextSample()` transitions `Playing -> Free` once `position`
      reaches the end of `zone.sample.data`, and make `VoicePool.release(note)` a
      no-op for any `Voice` whose `zone.loopMode == OneShot` (FR-005 partial; US1
      AC3) in
      `modules/sampler/src/commonMain/kotlin/dev/muzziknod/modules/sampler/Voice.kt`
      and
      `modules/sampler/src/commonMain/kotlin/dev/muzziknod/modules/sampler/VoicePool.kt`
      (depends on T007, T008)

**Checkpoint**: User Story 1 is fully functional and testable independently — load,
map, trigger, one-shot completion, graceful error handling all work.

---

## Phase 4: User Story 2 - Play Across the Keyboard with Correct Pitch (Priority: P2)

**Goal**: The same loaded sample plays back transposed up or down when triggered at
notes other than its root note, without reloading.

**Independent Test**: With a sample mapped to root note C3, trigger C4 and C2
independently and confirm the rendered output is transposed up/down one octave
respectively (`pitchRatio` == 2.0 / 0.5).

### Tests for User Story 2

- [X] T020 [P] [US2] Integration test: with a synthetic single-cycle-sine `Sample`
      mapped to root note 60, triggering note 72 (one octave up), note 48 (one
      octave down), note 84 (two octaves up), and note 36 (two octaves down)
      produces a `pitchRatio` of exactly `2.0`, `0.5`, `4.0`, and `0.25`
      respectively, and each rendered output's period is scaled accordingly,
      covering the full ±2-octave range required by SC-002 (FR-006; SC-002; US2
      AC1-2) in
      `modules/sampler/src/commonTest/kotlin/dev/muzziknod/modules/sampler/PitchRatioTest.kt`
- [X] T021 [P] [US2] Integration test: triggering a transposed (non-root) note still
      applies velocity scaling and per-zone `gain` identically to the root-note case
      (FR-004, FR-007; US2 AC3) in
      `modules/sampler/src/commonTest/kotlin/dev/muzziknod/modules/sampler/VelocityGainTest.kt`
      (extends T015's file)

### Implementation for User Story 2

- [X] T022 [US2] Add a `pitchRatio: Double` field to `Voice`, computed in
      `VoicePool.trigger()` as `2.0.pow((note - zone.rootNote) / 12.0)` (FR-006) in
      `modules/sampler/src/commonMain/kotlin/dev/muzziknod/modules/sampler/VoicePool.kt`
      (depends on T018)
- [X] T023 [US2] Replace `Voice`'s integer-index playback with a fractional
      `position: Double` that advances by `pitchRatio` per output sample, reading via
      linear interpolation between `floor(position)` and `floor(position) + 1` in
      `zone.sample.data` (research.md "Pitch-shifting algorithm") in
      `modules/sampler/src/commonMain/kotlin/dev/muzziknod/modules/sampler/Voice.kt`
      (depends on T019, T022)

**Checkpoint**: User Stories 1 AND 2 both work independently — playback is correctly
transposed across the keyboard.

---

## Phase 5: User Story 3 - Play Chords and Overlapping Notes (Priority: P3)

**Goal**: Multiple notes sound concurrently, exceeding the voice limit steals the
oldest voice via a click-free fade instead of an instant cut, and looped samples
release (fade out) on note-off instead of sustaining forever.

**Independent Test**: Trigger 4 different notes at once and confirm 4 concurrent
voices; exceed `maxVoices` and confirm the oldest voice fades rather than clicking;
hold a `Loop`-mode note, release it, and confirm the voice fades out instead of
looping indefinitely.

### Tests for User Story 3

- [X] T024 [P] [US3] Integration test: triggering 4 different notes at once produces
      4 independently-advancing active voices in the `VoicePool` at the same time;
      a second assertion triggers 16 different notes at once (default `maxVoices` =
      32) and confirms all 16 render concurrently without dropouts, crashes, or a
      stolen voice (FR-008; SC-003; US3 AC1) in
      `modules/sampler/src/commonTest/kotlin/dev/muzziknod/modules/sampler/VoicePoolTest.kt`
- [X] T025 [P] [US3] Integration test: with `maxVoices` set to a small number (e.g.
      2), triggering a 3rd note steals the voice with the lowest `triggerOrder`,
      which fades its output to zero over the release window instead of jumping
      instantly to silence; repeat the trigger-steal sequence across 40 independent
      trials (varying which voice is stolen) and assert at least 95% of trials show
      no sample-to-sample jump larger than the fade ramp allows, i.e. no click
      (FR-009; SC-004; US3 AC2) in
      `modules/sampler/src/commonTest/kotlin/dev/muzziknod/modules/sampler/VoicePoolTest.kt`
- [X] T026 [P] [US3] Integration test: a `Loop`-mode voice sustaining on a held note
      transitions to `Releasing` (fading to zero) on note-off, and reaches `Free`
      instead of looping indefinitely after the fade completes (FR-005; US3 AC3) in
      `modules/sampler/src/commonTest/kotlin/dev/muzziknod/modules/sampler/OneShotVsLoopTest.kt`
      (extends T014's file)

### Implementation for User Story 3

- [X] T027 [US3] Implement `Loop`-mode wraparound in `Voice.renderNextSample()`: when
      `position` advances past the end of `zone.sample.data` and `zone.loopMode ==
      Loop`, wrap `position` back to `0` (whole-file loop, per data-model.md
      Assumptions) in
      `modules/sampler/src/commonMain/kotlin/dev/muzziknod/modules/sampler/Voice.kt`
      (depends on T023)
- [X] T028 [US3] Add a `fadeGain: Double` ramp to `Voice`: entering `Releasing` starts
      a fixed-length (~32-sample) linear ramp of `fadeGain` from `1.0` to `0.0`,
      applied as an extra multiplier in `renderNextSample()`; reaching `0.0`
      transitions the `Voice` to `Free` (research.md "Polyphony and voice stealing")
      in
      `modules/sampler/src/commonMain/kotlin/dev/muzziknod/modules/sampler/Voice.kt`
      (depends on T027)
- [X] T029 [US3] Implement `VoicePool.release(note)` for `Loop`-mode voices:
      transition matching `Playing` voices with `zone.loopMode == Loop` into
      `Releasing` (starts the T028 fade); `OneShot` voices remain untouched (FR-005)
      in
      `modules/sampler/src/commonMain/kotlin/dev/muzziknod/modules/sampler/VoicePool.kt`
      (depends on T028, T019)
- [X] T030 [US3] Implement oldest-first voice stealing in `VoicePool.trigger()`: when
      no `Free` voice exists, select the non-`Free` voice with the lowest
      `triggerOrder` and transition it into `Releasing` (starting its T028 fade)
      before claiming the newly-freed voice for the new note (FR-009; research.md
      "Polyphony and voice stealing") in
      `modules/sampler/src/commonMain/kotlin/dev/muzziknod/modules/sampler/VoicePool.kt`
      (depends on T028, T029)

**Checkpoint**: All three user stories are independently functional — full polyphony,
click-free voice stealing, and loop-release behavior are in place.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T031 [P] Add WAV/AIFF fixture generation for jvmTest (16/24-bit, 44.1/48/96kHz
      WAV; 16-bit AIFF), referenced by T011/T012/T013. Implemented as
      `TestAudioFixtures` (in-memory `javax.sound.sampled` encoder) in
      `modules/sampler/src/jvmTest/kotlin/dev/muzziknod/modules/sampler/TestAudioFixtures.kt`
      rather than committed binary assets under `resources/` — avoids adding binary
      files to the repo while still exercising the real decode path end-to-end
- [X] T032 Run `quickstart.md`'s full validation
      (`./gradlew :modules:sampler:jvmTest`) and confirm every scenario and SC-001
      through SC-005 pass in one run
- [X] T033 Audit `SamplerModule.process()`, `VoicePool.renderNextSample()`, and
      `Voice.renderNextSample()` for hidden allocation (boxing, list/array creation,
      string building) to confirm Constitution III's alloc-free hot-path rule holds;
      only `loadSample()`/`onLoad()` may allocate. Found and fixed one: MIDI dispatch's
      zone lookup used `mutableZones.firstOrNull { ... }` on a `MutableList`, which
      allocates an `Iterator` per note-on; replaced with an index-based loop in
      `SamplerModule.kt`. Everything else (VoicePool's `Array<Voice>` iteration,
      `Voice`'s primitive-only render path) was already allocation-free.
- [X] T034 [P] Add `modules:sampler` to the root `README.md`'s module list, matching
      the existing `modules/midi-sequencer` and `modules/audio-effects` entries

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion — BLOCKS all user stories
- **User Stories (Phase 3+)**: All depend on Foundational phase completion
  - US2 extends the trigger flow US1 builds (same `Voice`/`VoicePool` files); US3
    extends both — implement in priority order (P1 → P2 → P3) rather than in
    parallel, since later stories edit the same files earlier stories create
- **Polish (Phase 6)**: Depends on all three user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) — no dependency on
  other stories
- **User Story 2 (P2)**: Can start after Foundational, but its implementation tasks
  (T022-T023) edit `Voice.kt`/`VoicePool.kt` files US1 already wrote — sequence after
  US1 to avoid merge conflicts, even though the two stories are conceptually
  independent
- **User Story 3 (P3)**: Same file-overlap reasoning — sequence after US2

### Within Each User Story

- Tests written before implementation, and should fail until the story's
  implementation tasks land
- Entities/decoder before trigger dispatch before pitch/polyphony refinements
- Story complete (checkpoint) before moving to the next priority

### Parallel Opportunities

- T001/T002 (Setup) have no cross-dependency but touch related files — safe to run
  together
- T003, T005, T007 (Foundational) are `[P]` — different files, no shared dependency
  at the time each starts
- All test tasks within a single user story phase marked `[P]` can run in parallel
  (different files)
- T031 and T034 (Polish) are independent of each other and of T032/T033

---

## Parallel Example: User Story 1

```bash
# Launch all tests for User Story 1 together:
Task: "SampleDecoderWavTest in modules/sampler/src/jvmTest/.../SampleDecoderWavTest.kt"
Task: "SampleDecoderAiffTest in modules/sampler/src/jvmTest/.../SampleDecoderAiffTest.kt"
Task: "SampleDecoderErrorTest in modules/sampler/src/jvmTest/.../SampleDecoderErrorTest.kt"
Task: "OneShotVsLoopTest in modules/sampler/src/commonTest/.../OneShotVsLoopTest.kt"
Task: "VelocityGainTest in modules/sampler/src/commonTest/.../VelocityGainTest.kt"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL — blocks all stories)
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: run the US1 quickstart scenario independently
5. Deploy/demo if ready — a fixed-pitch, one-shot sample player is already a usable
   MVP

### Incremental Delivery

1. Complete Setup + Foundational → decoder + inert module skeleton ready
2. Add User Story 1 → test independently → deploy/demo (MVP!)
3. Add User Story 2 → test independently → deploy/demo (full-keyboard instrument)
4. Add User Story 3 → test independently → deploy/demo (chords, fast retriggers,
   sustained loops)
5. Each story adds value without breaking the previous stories' checkpoints

---

## Notes

- `[P]` tasks = different files, no dependencies
- `[Story]` label maps task to specific user story for traceability
- US2/US3 intentionally edit the same `Voice.kt`/`VoicePool.kt` files US1 creates —
  implement in priority order, not in parallel across stories
- Verify tests fail before implementing
- Commit after each task or logical group
- Stop at any checkpoint to validate a story independently
