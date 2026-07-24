# Phase 0 Research: Módulo Sequenciador MIDI

## Step-advance timing model (FR-004, FR-005)

- **Decision**: One pattern step advances per host `processCycle()` invocation —
  there is no wall-clock/BPM-to-real-time conversion in this feature. BPM is
  stored and exposed as declared configuration (for a future real-time driver
  to use when pacing calls to `processCycle()`), but does not change how many
  cycles elapse per step today.
- **Rationale**: 001-core-host's own `research.md` ("Processing cycle model")
  already established that `processCycle()` is not bound to a real audio
  device callback — it's invoked synchronously by a test harness (or, later, a
  real-time callback). The host has no notion of elapsed time or sample count
  per cycle (`ProcessContext` in `Module.kt` exposes no clock), so a module
  cannot derive real BPM timing without a host contract change — which FR-008
  explicitly rules out. One-step-per-cycle keeps step ordering and loop
  behavior deterministic and fully testable (SC-001, SC-003) without inventing
  a fake clock.
- **Alternatives considered**:
  - *Extend `ProcessContext` with elapsed time/sample count*: rejected — a
    `core-host` contract change needs its own migration plan (Constitution VI)
    and is out of scope for a module-only feature (FR-008).
  - *Module maintains an internal wall-clock timer (`System.nanoTime()`) and
    self-paces which cycles actually advance a step*: rejected — introduces
    non-deterministic, real-time-dependent behavior into a `process()` call
    that Constitution III requires to stay simple/alloc-free and 001 already
    established as synchronous/cycle-driven, not wall-clock-driven; also makes
    SC-001's "no perceptible timing deviation" untestable deterministically.

## Pattern/transport control surface (FR-002, FR-003, FR-006, spec Assumption on host API)

- **Decision**: `MidiSequencerModule` exposes plain public Kotlin methods
  beyond the minimal `Module` interface — e.g. `setStep(index, notes)`,
  `setBpm(value)`, `setLength(steps)`, `play()`, `stop()` — called directly by
  whoever holds the instance reference. `core-host`'s `Module` contract itself
  is not extended.
- **Rationale**: The 001 contract's `ParameterSpec` is already
  declaration-only with no runtime setter defined anywhere in `Module`/
  `ProcessContext`; there is no host-mediated command channel into a module
  today. Constitution V's premise — "cada módulo pode expor sua própria UI...
  sem que o host precise conhecer detalhes visuais" — implies modules already
  own a richer surface beyond the generic host contract. Satisfies FR-008
  ("sem exigir mudanças no host") exactly.
- **Alternatives considered**:
  - *Add a generic `sendCommand(id, value)` to the `Module` interface*:
    rejected — a `core-host` contract change (needs migration plan per
    Constitution VI), unnecessary since nothing else in the host needs to
    route commands generically yet (YAGNI, Constitution VII).
  - *Route pattern edits through MIDI Control Change events on an input port*:
    rejected — conflates user-facing editing with the audio/MIDI data plane,
    and would require a MIDI input port with no corresponding user story
    needing external MIDI control in this feature.

## Pending note-off flush on stop (FR-007, Edge Cases)

- **Decision**: `stop()` immediately computes and queues note-off events for
  every currently-sustained note; those events are written to the MIDI output
  port on the very next `process()` call (the one that observes the
  now-stopped transport state), then playback state is fully idle.
- **Rationale**: Matches 001's own module-removal precedent (`ModuleRemoval
  DeferralTest`) of never dropping in-flight state silently — a "stuck note"
  is a real, observable bug class in MIDI hosts, and SC-002 requires 100%
  note-off delivery on stop.
- **Alternatives considered**:
  - *Emit note-off synchronously inside `stop()` itself, bypassing
    `process()`*: rejected — `process()`/`ProcessContext.writeMidi` is the
    only channel a module has to reach connected modules (Constitution VI);
    writing MIDI outside a cycle would bypass the routing graph entirely.

## Module placement (`modules/` vs `reference-modules/`)

- **Decision**: New top-level `modules/midi-sequencer` Gradle module, sibling
  to `reference-modules/`, not inside it.
- **Rationale**: 001-core-host's plan.md is explicit that `reference-modules/`
  holds the *minimum* scaffolding needed to validate the host contract itself,
  and that "módulos de produto" get their own spec + live outside that
  scaffolding set. This is the first such product module.
- **Alternatives considered**:
  - *Add it under `reference-modules/`*: rejected — conflates scaffolding
    (proves the contract) with product functionality (a real sequencer),
    contradicting 001's own stated structure decision.

## Dependencies

- **Decision**: `kotlin.test` + JUnit5 platform only, same as 001. No
  coroutines/DI/serialization.
- **Rationale**: Nothing in scope (pattern data, transport state, MIDI event
  emission) requires them; Constitution VII.
- **Alternatives considered**: `kotlinx-coroutines` for transport
  play/stop — rejected, transport is a plain state flag flipped by direct
  method calls, no concurrency primitive needed at this scope.

All `NEEDS CLARIFICATION` markers from the spec are resolved above (none were
present — see spec.md Assumptions for the scope defaults chosen at
`/speckit-specify` time).