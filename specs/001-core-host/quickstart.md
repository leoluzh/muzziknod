# Quickstart: Core Host Modular

Validates US1/US2/US3 and SC-001–SC-004 end-to-end. All three reference modules
(`oscillator`, `midi-generator`, `midi-logger`) and `core-host` are implemented.

## Prerequisites

- JDK 26 installed, `JAVA_HOME` pointing at it.
- Gradle wrapper committed at repo root.

## Setup

```bash
./gradlew build
```

## Scenario 1 — Load a module into an empty host (US1)

```bash
./gradlew :core-host:jvmTest --tests "*ModuleLifecycle*" --tests "*ModuleDuplicateIdTest*"
```

Expected: a module loads into an empty host, `state == Active`, its ports from
`contract.ports` are visible via the host's registry; loading a second module leaves
the first untouched; invalid/duplicate-id loads are rejected. See spec US1 AC1–AC3.
Proven against the real `oscillator` module by
`:reference-modules:oscillator:jvmTest --tests "*OscillatorContractTest*"`.

## Scenario 2 — Connect two modules and process a cycle (US2)

```bash
./gradlew :core-host:jvmTest --tests "*RoutingGraph*"
```

Expected:
- Connecting a MIDI generator's output to a MIDI logger's input succeeds;
  `processCycle()` delivers events in emitted order (US2 AC1).
- Connecting an Audio output to a MIDI input is rejected with a reason (US2 AC2, type
  mismatch).
- Disconnecting one link stops flow between those two modules only, the rest of the
  graph is unaffected (US2 AC3).
- With two MIDI in+out modules L1 and L2 connected `L1.out → L2.in`, attempting
  `L2.out → L1.in` is rejected as an unsupported feedback cycle (US2 AC4).
- A sample-rate/buffer-format mismatch rejects the connection, no auto-conversion
  (FR-013, research.md resolution).

Proven against the real `midi-generator` and `midi-logger` modules by
`:reference-modules:midi-generator:jvmTest --tests "*MidiGeneratorContractTest*"` and
`:reference-modules:midi-logger:jvmTest --tests "*MidiLoggerContractTest*"`.

## Scenario 3 — Remove a module mid-chain (US3)

```bash
./gradlew :core-host:jvmTest --tests "*ModuleRemoval*"
```

Expected: with 3 modules chained, removing the middle one clears its connections while
the other two remain `Active` and loaded (US3 AC1); removal requested mid-cycle is
deferred until the in-flight `processCycle()` completes (US3 AC2).

## Scenario 4 — Fault isolation (FR-011 resolution)

```bash
./gradlew :core-host:jvmTest --tests "*RoutingGraphFaultIsolationTest*"
```

Expected: a module whose `process()` throws transitions to `Faulted`; the same
`processCycle()` still runs every other module to completion, and the faulted module
stays excluded from all later cycles.

## Scenario 5 — 7-module chain, no ordering errors (SC-004)

```bash
./gradlew :core-host:jvmTest --tests "*GraphOrderingStressTest*"
```

Expected: a 7-module chain (exceeds the ≥5 required by SC-004) runs 50 repeated
`processCycle()` calls with every module processed in dependency order every time, no
crashes, no ordering violations.

## Contract compliance (all reference modules)

```bash
./gradlew :reference-modules:oscillator:jvmTest :reference-modules:midi-generator:jvmTest :reference-modules:midi-logger:jvmTest --tests "*ContractTest*"
```

Expected: all three reference modules pass the shared `ModuleContractComplianceTests`
suite defined in `contracts/module-contract.md`.