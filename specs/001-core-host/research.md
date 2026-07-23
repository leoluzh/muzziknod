# Phase 0 Research: Core Host Modular

## FR-011 — Module fault isolation

- **Decision**: The host wraps each module's `process()` call in a per-module fault
  boundary. A module that throws or fails during its cycle is marked `Faulted`,
  deactivated, and excluded from subsequent cycles; the rest of the graph continues
  processing unaffected. The fault and offending module id are surfaced to the host's
  caller (log/event), not swallowed silently.
- **Rationale**: Constitution I treats modules as independent and Constitution III
  isolates the real-time path — one bad module halting the entire audio graph is
  unacceptable for a live instrument and defeats the modularity premise this feature
  exists to validate (spec Why-this-priority on US1/US2).
- **Alternatives considered**:
  - *Halt all processing on any module fault*: rejected — turns one module's bug into a
    total outage, contradicts "módulos existentes continuam funcionando" (US1 AC2).
  - *Auto-restart/retry the faulted module*: rejected for MVP as premature (Constitution
    VII, YAGNI) — recovery policy can be layered on top of the fault-isolation mechanism
    once real modules exist that would benefit from it.

## FR-013 — Sample rate / buffer format compatibility

- **Decision**: No automatic conversion in MVP. A connection attempt between modules
  with incompatible sample rate or buffer format is rejected at connect-time with a
  clear error, using the same rejection path as type-incompatible ports (FR-005).
- **Rationale**: Constitution VII (Simplicidade Incremental) and spec Assumptions
  explicitly defer native DSP engine integration to a future feature; sample-rate
  conversion is non-trivial DSP work that belongs there, not in the host's routing
  layer.
- **Alternatives considered**:
  - *Auto-insert a conversion adapter module on mismatch*: rejected — real DSP work,
    premature before the native engine bridge (FFM) exists.
  - *Coerce/ignore mismatches silently*: rejected — violates Constitution VI (explicit
    contracts, no silent breakage).

## Processing cycle model

- **Decision**: `RoutingGraph` exposes a `processCycle()` that topologically sorts
  active modules and invokes each one's `process()` in dependency order. This feature
  does not bind `processCycle()` to a real audio device callback — it's invoked
  synchronously (e.g., by a test harness or, later, by a real-time callback). Ties back
  to spec Assumptions: reference modules are pure Kotlin, no native DSP yet.
- **Rationale**: Keeps the feature testable end-to-end (SC-001, SC-004) without pulling
  in real audio I/O, which is explicitly out of scope.
- **Alternatives considered**: Wiring a real JVM audio backend now — rejected, out of
  scope per spec Assumptions and unnecessary to validate lifecycle/routing/contract.

## Dependencies

- **Decision**: `kotlin.test` + JUnit5 platform only. No coroutines, DI framework, or
  serialization library added.
- **Rationale**: Nothing in this feature's scope (lifecycle, routing, contract) requires
  them. Constitution VII — introduce a dependency when a real need appears, not before.
- **Alternatives considered**: `kotlinx-coroutines` for the processing cycle — rejected,
  the cycle is synchronous by design here; revisit once a real-time scheduler feature
  needs it.

## Build structure

- **Decision**: Kotlin Multiplatform Gradle plugin from the start, JVM target only
  enabled, per Constitution's explicit "Restrições Técnicas" mandate.
- **Rationale**: Constitution is non-negotiable on this point — KMP plugin from day
  one even with a single active target, so `androidMain` can be added later without
  restructuring.
- **Alternatives considered**: Plain Kotlin/JVM Gradle plugin — rejected, contradicts
  Constitution IV/Restrições Técnicas directly.

All `NEEDS CLARIFICATION` markers from the spec are resolved above.