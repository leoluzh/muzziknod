# Quickstart: Sampler Module

Validates US1/US2/US3 and SC-001-SC-005 end-to-end once `modules/sampler` is
implemented.

## Prerequisites

- JDK 26 installed, `JAVA_HOME` pointing at it (same as 001-003).
- Gradle wrapper committed at repo root; `modules:sampler` added to
  `settings.gradle.kts`.
- Small WAV/AIFF test fixtures under `modules/sampler/src/jvmTest/resources/`
  (e.g. a short sine-tone or drum-hit sample at a couple of sample rates/bit
  depths) for decoder tests.

## Setup

```bash
./gradlew build
```

## Scenario 1 — Load and trigger a sample (US1)

```bash
./gradlew :modules:sampler:jvmTest --tests "*SampleDecoderWavTest*" --tests "*SampleDecoderAiffTest*" --tests "*SampleDecoderErrorTest*" --tests "*OneShotVsLoopTest*"
```

Expected: a valid WAV/AIFF fixture loads successfully and is mapped to a root
note (FR-001, FR-002); a note-on for that exact root note plays back at
unity pitch/gain; a missing/corrupt/unsupported file returns
`SampleLoadResult.Failed` without throwing or crashing (FR-002, SC-005); a
one-shot sample continues to completion after an early note-off (FR-005,
User Story 1 acceptance scenario 3).

## Scenario 2 — Pitch mapping across the keyboard (US2)

```bash
./gradlew :modules:sampler:jvmTest --tests "*PitchRatioTest*" --tests "*VelocityGainTest*"
```

Expected: the same loaded sample, triggered at notes above/below its root
note, plays back transposed by the correct `2^(semitones/12)` ratio (FR-006,
SC-002) without reloading; per-sample `gain` and note velocity both scale
output amplitude multiplicatively (FR-004, FR-007).

## Scenario 3 — Polyphony and voice stealing (US3)

```bash
./gradlew :modules:sampler:jvmTest --tests "*VoicePoolTest*"
```

Expected: multiple simultaneous notes sound concurrently up to `maxVoices`
(FR-008, SC-003 at ≥16 voices); triggering beyond `maxVoices` steals the
oldest active voice via a short fade rather than an instant cut (FR-009,
SC-004); a held looped voice releases (fades out) on note-off instead of
looping forever (FR-005, User Story 3 acceptance scenario 3).

## Contract compliance

```bash
./gradlew :modules:sampler:jvmTest --tests "*ContractTest*"
```

Expected: `SamplerContractTest` passes the shared
`ModuleContractComplianceTests` suite unchanged from 001-core-host (see
`contracts/sampler-api.md`), proving the module satisfies the existing
`core-host` `Module` contract with zero host changes.

## Full feature validation

```bash
./gradlew :modules:sampler:jvmTest
```

Expected: load a sample, map it across multiple zones/root notes, trigger
chords and rapid repeated notes, hold and release looped notes, and inject
load failures — all pass in one automated run, with zero crashes across the
full suite (SC-001-SC-005).
