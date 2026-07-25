# Quickstart: Módulos de Efeitos de Áudio (Reverb/Delay/Distortion/EQ)

Validates US1/US2/US3 and SC-001–SC-004 end-to-end once `modules/audio-effects`
is implemented.

## Prerequisites

- JDK 26 installed, `JAVA_HOME` pointing at it (same as 001/002).
- Gradle wrapper committed at repo root; `modules:audio-effects` added to
  `settings.gradle.kts`.

## Setup

```bash
./gradlew build
```

## Scenario 1 — Wet/dry mix (US1)

```bash
./gradlew :modules:audio-effects:jvmTest --tests "*WetDryMixTest*"
```

Expected: for each of the four module types, `mix = 0.0` produces output
identical to input (FR-003, SC-001); `mix = 1.0` produces output containing
no trace of the original signal (FR-004, SC-002); intermediate `mix` values
produce a proportional blend (FR-005). An unconnected input produces silent
output without error (FR-014).

## Scenario 2 — Live parameter changes (US2)

```bash
./gradlew :modules:audio-effects:jvmTest --tests "*ParameterSmoothingTest*" --tests "*ReverbDspTest*" --tests "*DelayDspTest*" --tests "*DistortionDspTest*" --tests "*EqDspTest*"
```

Expected: changing any setter (e.g. `DelayModule.setDelayTimeMs`,
`EqModule.setBandGain`) while `process()` runs continuously never produces a
sample-to-sample jump larger than the smoothing window allows (FR-005,
FR-010, SC-003); out-of-range values are clamped, not rejected (FR-013,
Edge Cases).

## Scenario 3 — Chain multiple effects via the routing graph (US3)

```bash
./gradlew :modules:audio-effects:jvmTest --tests "*EffectsChainRoutingTest*"
```

Expected: a generator module, one instance of each of the four effect types,
and a sink module, connected in sequence (generator → EQ → distortion →
delay → reverb → sink) via `core-host`'s `RoutingGraph`, deliver the fully
processed signal to the sink every cycle, in connection order (FR-001,
FR-011, FR-012). Removing one effect from the chain does not auto-reconnect
its neighbors — same deferred-removal behavior as `core-host` (FR-009/FR-010
of 001-core-host).

## Contract compliance

```bash
./gradlew :modules:audio-effects:jvmTest --tests "*ContractTest*"
```

Expected: all four `*ContractTest` classes pass the shared
`ModuleContractComplianceTests` suite unchanged from 001-core-host (see
`contracts/audio-effects-api.md`), proving each module satisfies the
existing `core-host` `Module` contract with zero host changes (FR-011).

## Full feature validation (SC-004)

```bash
./gradlew :modules:audio-effects:jvmTest
```

Expected: load all four effect types → connect them in a chain → process
continuously → alter parameters mid-stream → remove a module from the
chain, all in one automated flow, without restarting the application
(SC-004).
