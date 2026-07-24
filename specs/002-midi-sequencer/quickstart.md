# Quickstart: Módulo Sequenciador MIDI

Validates US1/US2/US3 and SC-001–SC-004 end-to-end once `modules/midi-sequencer`
is implemented.

## Prerequisites

- JDK 26 installed, `JAVA_HOME` pointing at it (same as 001-core-host).
- Gradle wrapper committed at repo root; `modules:midi-sequencer` added to
  `settings.gradle.kts`.

## Setup

```bash
./gradlew build
```

## Scenario 1 — Program a pattern and play it in loop (US1)

```bash
./gradlew :modules:midi-sequencer:jvmTest --tests "*PatternPlaybackTest*"
```

Expected: a 16-step pattern with notes programmed on a few steps emits exactly
those events, in step order, one step per `processCycle()` (research.md
"Step-advance timing model"); after the last step, playback loops back to
step 0 automatically (FR-005); stopping mid-note flushes any sustained note's
note-off on the next cycle, no stuck notes (FR-007, SC-002). Runs ≥20 loop
repetitions with no ordering drift (SC-001).

## Scenario 2 — Live pattern editing (US2)

```bash
./gradlew :modules:midi-sequencer:jvmTest --tests "*PatternEditing*" --tests "*PatternLengthWrap*"
```

Expected: editing a step, the BPM, or the pattern length while stopped takes
effect on the next `play()`; editing a not-yet-reached step while playing is
reflected the next time that step is reached, without interrupting steps
already in flight (FR-006, SC-003); reducing `length` below the current
`currentStep` wraps the position into range without throwing (FR-010, Edge
Cases).

## Scenario 3 — Connect through the routing graph (US3)

```bash
./gradlew :modules:midi-sequencer:jvmTest --tests "*RoutingIntegrationTest*"
```

Expected: loaded alongside a MIDI-sink test double (or `reference-modules/
midi-logger`), connected via `core-host`'s `RoutingGraph`, the sink receives
every event the sequencer's `process()` writes, cycle by cycle, while playing
(FR-004, FR-008); with no output connection at all, the sequencer still
advances its pattern without error (FR-009).

## Contract compliance

```bash
./gradlew :modules:midi-sequencer:jvmTest --tests "*MidiSequencerContractTest*"
```

Expected: passes the shared `ModuleContractComplianceTests` suite unchanged
from 001-core-host (see `contracts/midi-sequencer-api.md`), proving the
module satisfies the existing `core-host` `Module` contract with zero host
changes (FR-008).

## Full feature validation (SC-004)

```bash
./gradlew :modules:midi-sequencer:jvmTest
```

Expected: load → program a pattern → connect to another module → play a full
loop → stop, all in one automated flow, without restarting the application
(SC-004). Module removal itself is `core-host`'s existing, already-proven
`RoutingGraph.removeModule` (001-core-host); this module's own contract test
(`onRemoveAfterProcessDoesNotThrow`) confirms it participates in that flow
without issue.