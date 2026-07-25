# Phase 0 Research: Módulos de Efeitos de Áudio (Reverb/Delay/Distortion/EQ)

## DSP scope (spec FR-015, revised during planning)

- **Decision**: Ship real (if simple) DSP for all four effects in pure Kotlin —
  not a passthrough placeholder. Each effect implements a well-known, basic
  algorithm (see "Per-effect algorithm choice" below), alloc-free in
  `process()` per Constitution III's general hot-path rule.
- **Rationale**: `OscillatorModule` (001-core-host) already synthesizes a real
  sine wave in pure Kotlin (`kotlin.math.sin`), and 001/002's own `research.md`
  establish that `processCycle()` is not yet bound to a real-time audio device
  callback — cycles are driven synchronously by a test harness. Constitution
  III's native-bridge requirement targets the *real-time* audio path (a
  low-latency callback thread); nothing in the codebase reaches that path yet,
  so the principle doesn't bind here (YAGNI, Constitution VII — no FFM bridge
  until a real driver creates the actual need). The original spec draft's
  passthrough-only framing (`FR-015`, pre-revision) assumed reverb/delay DSP
  was inherently "critical-path," which the oscillator precedent contradicts.
- **Alternatives considered**:
  - *Keep passthrough-only, per the original FR-015*: rejected after
    discovering the oscillator precedent — would make four otherwise-complete
    modules functionally inert for no benefit, and contradicts existing
    project practice.
  - *Build the FFM/native bridge now to be "safe"*: rejected — no real-time
    driver exists to justify it yet (YAGNI); would also block this feature on
    unrelated native-toolchain work.

## Per-effect algorithm choice (FR-006–FR-009)

- **Decision**:
  - **Delay**: single-tap delay line over a circular `FloatArray` sized for a
    generous max delay time (2000 ms) at the port's sample rate; `feedback`
    clamped to `0.0..0.95` to guarantee decay (never sustains/clips forever).
  - **Reverb**: classic Schroeder/Moorer topology — 4 parallel comb filters
    feeding 2 series allpass filters. `roomSize` maps to comb feedback gain,
    `decayMs` maps comb delay-line lengths' decay envelope.
  - **Distortion**: soft-clip waveshaper (`tanh(drive * x) / tanh(drive)`
    normalization keeps unity gain at `drive = 1`) followed by a one-pole
    lowpass as `tone`.
  - **EQ**: 3-band parametric EQ using RBJ (Audio EQ Cookbook) peaking biquad
    filters in series, each band exposing frequency/gain/Q.
- **Rationale**: All four are textbook "basic" algorithms — exactly what the
  spec asks for ("basic multi-effects chain") and small enough to implement
  and reason about in pure Kotlin without external DSP libraries
  (Constitution VII — smallest architecture that validates the feature, no
  speculative sophistication like FDN reverb or multi-mode filters).
- **Alternatives considered**:
  - *Convolution reverb*: rejected — needs an impulse-response asset and FFT,
    disproportionate to "basic" scope (spec Assumptions).
  - *State-variable filter for EQ*: rejected — RBJ biquads are simpler to
    implement/verify per-band and are the standard reference algorithm for
    parametric EQ.

## Wet/dry representation (FR-002–FR-005)

- **Decision**: `mix` is a `Double` in `0.0..1.0` (fraction), declared via
  `ParameterSpec` the same way as every other parameter — not a separate
  `0-100` integer type.
- **Rationale**: `ParameterSpec.range` is already `ClosedFloatingPointRange<Double>`
  (001-core-host contract); `0.0..1.0` is the natural unit for a linear
  crossfade (`output = dry * (1 - mix) + wet * mix`) and avoids an extra
  percent↔fraction conversion at every call site. Spec language ("100%
  wet/dry") maps directly: `1.0` = 100% wet, `0.0` = 100% dry.
- **Alternatives considered**: `0-100` `Int` — rejected, forces a conversion
  before every use in the linear-crossfade formula for no behavioral gain.

## Live parameter control surface (FR-003, FR-010)

- **Decision**: Same pattern 002-midi-sequencer established — each concrete
  module class exposes plain public Kotlin setters beyond the `Module`
  interface (e.g. `setMix`, `setDelayTimeMs`, `setFeedback`), called directly
  by whoever holds the instance. `core-host`'s `Module`/`ModuleContract`/
  `ProcessContext` stay unchanged (mirrors FR-011).
- **Rationale**: `ParameterSpec` is declaration-only (no runtime setter in the
  001 contract); there's no host-mediated command channel today, and adding
  one is an unrelated `core-host` contract change (Constitution VI migration
  plan) with no other consumer yet (YAGNI).
- **Alternatives considered**: generic `setParameter(id, value)` on `Module`
  — rejected, same reasoning 002 already rejected it: a host contract change
  nothing else needs yet.

## Parameter smoothing (FR-005 wording: "aplicadas... sem interromper", edge case on rapid changes)

- **Decision**: Every setter routes the new target through a small shared
  `ParameterSmoother` (linear ramp toward the target over a fixed short
  window, e.g. 64 samples) instead of applying the new value instantaneously
  at the start of the next `process()` call.
- **Rationale**: Directly satisfies FR-005 ("suavizar mudanças de parâmetro
  para evitar artefatos audíveis") and the edge case about rapid repeated
  changes — a shared, tiny, alloc-free ramp is simpler than a per-effect
  bespoke solution and keeps SC-002's "no perceptible artifacts" testable
  deterministically (assert no sample-to-sample delta exceeds a threshold).
- **Alternatives considered**: no smoothing, direct assignment — rejected,
  fails FR-005 outright (audible zipper noise on parameter jumps).

## Sample-rate handling (FR-009)

- **Decision**: Each module takes `sampleRate: Int` as a constructor
  parameter (same as `OscillatorModule`) and converts every time-based
  parameter (delay time, decay time) to samples via `(ms / 1000.0 *
  sampleRate).toInt()` at the point of use/`onLoad()`, never a fixed sample
  count.
- **Rationale**: Directly satisfies FR-009 and the sample-rate Edge Case;
  matches the existing `OscillatorModule` precedent for sample-rate-aware
  reference modules.
- **Alternatives considered**: fixed-size buffers tuned for 48kHz only —
  rejected, breaks FR-009 outright at 44.1/96kHz.

## Module placement and Gradle structure (FR-001, FR-016)

- **Decision**: One new Gradle module, `modules:audio-effects`, containing
  all four `Module` implementations (`ReverbModule`, `DelayModule`,
  `DistortionModule`, `EqModule`) plus shared DSP primitives
  (`WetDryMixer`, `ParameterSmoother`), package
  `dev.muzziknod.modules.audioeffects`. Not four separate Gradle modules.
- **Rationale**: FR-016 already resolved that these are four separate
  *module types* (host-facing `typeId`s) — that's a contract-level decision,
  independent of Gradle module boundaries. Bundling them in one Gradle module
  avoids four near-duplicate `build.gradle.kts` files for closely related,
  co-designed code that shares `WetDryMixer`/`ParameterSmoother`
  (Constitution VII — smallest structure that validates the feature); this
  mirrors 002-midi-sequencer's placement under `modules/` (a real product
  feature, not `reference-modules/` scaffolding), just as one module hosting
  several cohesive classes (`MidiSequencerModule` + `Pattern` + `Transport`).
- **Alternatives considered**:
  - *Four separate Gradle modules (`modules:reverb`, `modules:delay`, ...)*:
    rejected — the `reference-modules/*` one-type-per-module convention exists
    because each reference module is independently minimal and unrelated;
    these four effects are one cohesively-planned feature sharing utility
    code, so splitting the build graph four ways adds ceremony with no
    isolation benefit (YAGNI).

## Dependencies

- **Decision**: `kotlin.test` + JUnit5 platform only, same as 001/002. No new
  dependencies (no DSP library, no coroutines/DI/serialization).
- **Rationale**: Nothing in scope needs them; all four algorithms are
  implementable with `kotlin.math` alone (Constitution VII).

All `NEEDS CLARIFICATION` markers from the spec are resolved above (none were
present at `/speckit-specify` time; FR-015's DSP-scope decision was revisited
here per the "DSP scope" section above).
