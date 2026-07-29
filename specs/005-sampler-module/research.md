# Phase 0 Research: Sampler Module

## Platform split for decoding (FR-001, FR-002, Constitution IV)

- **Decision**: Introduce the project's first genuine `expect`/`actual`
  boundary in a product module: `expect fun decodeSample(bytes: ByteArray,
  targetSampleRate: Int): DecodedAudio` in `commonMain`, with a JVM `actual`
  in `jvmMain` implemented via `javax.sound.sampled.AudioSystem` (JDK-bundled,
  no new Gradle dependency). `DecodedAudio` (mono `FloatArray` + sample count)
  is a plain `commonMain` data class.
- **Rationale**: 001-003 never needed a platform boundary because all DSP was
  pure Kotlin (`kotlin.math`) with no file I/O. Sample loading is the first
  concern that is inherently platform-specific — `javax.sound.sampled` doesn't
  exist outside the JVM, and a future Android target (Constitution IV) would
  need its own decoder (`MediaExtractor`/NDK). Isolating it now behind
  `expect`/`actual` keeps `SamplerModule`, `Voice`, `VoicePool`, and the pitch
  math 100% `commonMain` and portable, exactly as Constitution IV requires
  ("Diferenças de plataforma... ficam isoladas atrás de expect/actual").
- **Alternatives considered**:
  - *Put everything in `commonMain` and call `javax.sound.sampled` directly*
    (as `core-host`'s current single-jvm-target setup technically allows,
    since commonMain/jvmMain compile together with only one target active):
    rejected — works today but bakes a JVM-only API into code that Constitution
    IV says must stay platform-neutral; the first file-I/O-shaped feature is
    the right place to establish the boundary, not a later, more disruptive
    retrofit.
  - *Write a hand-rolled WAV/AIFF parser in pure Kotlin*: rejected — pure
    reinvention of what the JDK already provides correctly (chunk parsing,
    all the PCM bit-depth variants), adds risk and code for no benefit
    (Constitution VII, YAGNI).

## Sample-rate conversion at load time (FR-001, Edge Case "different sample rate")

- **Decision**: `decodeSample()` takes the *target* (host) sample rate and
  performs a one-time linear-interpolation resample from the file's native
  rate to the target rate, before the `Sample` is ever handed to a `Voice`.
  Multi-channel files are downmixed to mono by averaging channels at decode
  time (this module's audio output port is mono, matching every existing
  audio module).
- **Rationale**: Directly satisfies the spec edge case ("sample MUST be
  converted/resampled at load time so playback matches the host's operating
  sample rate"). Doing this once at load time (not per-voice, per-cycle) keeps
  `process()` doing only the per-note pitch-ratio resample, which is exactly
  the alloc-free, already-established hot-path shape from 003.
- **Alternatives considered**:
  - *Resample per-voice at playback time based on sample's native rate vs.
    host rate*: rejected — doubles the resampling math per active voice per
    sample for no benefit, since the conversion factor is fixed per loaded
    Sample and can be folded in once.
  - *Reject/error on sample-rate mismatch instead of converting*: rejected —
    contradicts the spec edge case, which requires conversion, not rejection.

## Pitch-shifting algorithm (FR-006, User Story 2)

- **Decision**: Per-voice playback position is a `Double` sample index that
  advances by `pitchRatio = 2.0.pow((note - rootNote) / 12.0)` each output
  sample; the actual sample value is linear-interpolated between the two
  nearest source samples (`floor(position)` and `floor(position) + 1`).
- **Rationale**: Textbook-basic algorithm (same "basic, no external DSP
  library" bar 003-audio-effects set for reverb/delay/distortion/EQ) that
  correctly transposes pitch across an arbitrary range (spec SC-002's 2-octave
  requirement and the "extreme transposition" edge case, which explicitly
  allows quality degradation at extreme ratios rather than imposing a hard
  range limit). No pitch-formant correction — out of scope (spec doesn't ask
  for timbre-independent pitch-shifting, only recognizable transposition).
- **Alternatives considered**:
  - *Higher-order (cubic/sinc) interpolation*: rejected for v1 — meaningfully
    more code/CPU for audio quality beyond what the spec's success criteria
    ask for (Constitution VII).
  - *Granular/formant-preserving pitch-shifting*: rejected — disproportionate
    complexity for a feature whose spec explicitly only requires
    "recognizably the same sound," not studio-grade time-stretching.

## Polyphony and voice stealing (FR-008, FR-009, User Story 3)

- **Decision**: `VoicePool` holds a fixed-size `Array<Voice>` (size =
  `maxVoices`, default 32) allocated once in `onLoad()`. Triggering a note
  when a free voice exists claims it; when none is free, the oldest
  currently-triggered voice (by trigger order, tracked via a monotonic
  counter, not wall-clock time) is stolen. A stolen or released voice is not
  silenced instantly — it's put into a short (e.g. 32-sample) linear
  fade-to-zero ramp before being freed, reusing the same "ramp instead of
  instant jump" idea 003's `ParameterSmoother` established for avoiding
  audible discontinuities.
- **Rationale**: Directly satisfies FR-009 (voice stealing without audible
  clicks) and spec SC-004 (≥95% of steal trials click-free). A fixed
  pre-sized array keeps `process()` alloc-free (Constitution III). Using a
  trigger-order counter instead of "quietest voice" (amplitude-based) keeps
  the stealing rule deterministic and trivially testable, while still meeting
  the spec's literal requirement ("oldest/quietest voice" — spec treats these
  as an equivalent pair of acceptable strategies, oldest is the simpler
  deterministic choice, Constitution VII).
- **Alternatives considered**:
  - *Quietest-voice stealing (RMS/peak tracking per voice)*: rejected for
    v1 — requires extra per-voice bookkeeping and a subjective loudness
    comparison for a benefit the spec doesn't specifically demand over the
    simpler oldest-first rule.
  - *Instant silence on steal/release (no fade)*: rejected — directly fails
    FR-009's "without an audible glitch" and SC-004.
  - *Unbounded voice list (no stealing, just grow)*: rejected — spec FR-009
    explicitly requires stealing behavior once a configured maximum is
    reached; unbounded growth also risks unbounded `process()` cost, which
    would violate the spirit of Constitution III even without allocation.

## One-shot vs. loop release behavior (FR-005, Edge Cases)

- **Decision**: One-shot voices ignore note-off entirely and play to natural
  completion (freeing themselves when they reach end-of-sample). Looped
  voices treat note-off as the start of the same short linear fade-to-zero
  ramp used for voice stealing, rather than looping indefinitely or cutting
  instantly.
- **Rationale**: Matches spec User Story 1's acceptance scenario 3 (one-shot
  continues after note-off) and User Story 3's acceptance scenario 3 (loop
  releases on note-off, doesn't sustain forever). Reusing the existing
  fade-ramp mechanism for release avoids a second bespoke envelope system
  (Constitution VII) while still avoiding a click on release.
- **Alternatives considered**:
  - *Full ADSR envelope*: rejected — spec doesn't ask for attack/decay/sustain
    shaping, only that loops stop after note-off and one-shots aren't cut
    short; a full envelope is unrequested scope.

## Loop points (Assumptions)

- **Decision**: Looped playback always loops the entire decoded sample
  (position wraps from end back to index 0), matching the spec's own
  Assumptions section (custom loop-point editing is explicitly out of scope
  for v1).
- **Rationale**: Directly matches spec Assumptions; no ambiguity to resolve.

## Velocity and gain (FR-004, FR-007)

- **Decision**: A triggered voice's effective gain is `zone.gain *
  (velocity / 127.0)`. `zone.gain` defaults to `1.0` and is a plain `Double`
  set via `SampleZone`, following the same declaration style as
  `ParameterSpec.default` elsewhere in the project (linear, not decibel,
  scale — consistent with `WetDryMixer`'s linear mix fraction in 003).
- **Rationale**: Simplest linear scaling that satisfies FR-004 and FR-007
  without introducing a second gain unit/curve into the codebase.
- **Alternatives considered**: Logarithmic/decibel velocity curve — rejected
  for v1, no spec requirement for perceptual loudness curves; linear is the
  textbook-basic default (Constitution VII).

## Live mapping/loading control surface (FR-010, mirrors 002/003 "Live parameter control surface")

- **Decision**: `SamplerModule` exposes plain public Kotlin methods
  (`loadSample(path/bytes, rootNote, lowNote, highNote, gain, loopMode):
  SampleZone`, `unloadSample(zone)`) beyond the `Module` interface, the same
  pattern 002 and 003 established. `core-host`'s `Module`/`ModuleContract`/
  `ProcessContext` stay unchanged.
- **Rationale**: No host-mediated command channel exists today for
  module-specific control surfaces (a `core-host` contract change), and
  nothing else needs one yet (YAGNI) — consistent with 002/003's own
  resolution of the identical question.
- **Alternatives considered**: generic `setParameter(id, value)` on `Module` —
  rejected for the same reason 002/003 already rejected it.

## Module placement and Gradle structure (FR-010)

- **Decision**: One new Gradle module, `modules:sampler`, containing
  `SamplerModule`, `Voice`, `VoicePool`, `Sample`, `SampleZone`, and the
  `SampleDecoder` expect/actual pair, package
  `dev.muzziknod.modules.sampler`.
- **Rationale**: Sibling to `modules/midi-sequencer` and `modules/audio-effects`
  — a real product feature (not `reference-modules/` scaffolding), one
  cohesively-planned Gradle module rather than splitting decoder/voice-pool/
  module into separate Gradle modules for no isolation benefit (Constitution
  VII, mirrors 003's identical reasoning).
- **Alternatives considered**: Separate `modules:sampler-decoder` Gradle
  module — rejected, no other consumer exists for the decoder in isolation;
  unnecessary ceremony (YAGNI).

## Dependencies

- **Decision**: `kotlin.test` + JUnit5 platform (existing), plus
  JDK-bundled `javax.sound.sampled` on `jvmMain` only. No new Gradle
  dependency declarations needed in `libs.versions.toml`.
- **Rationale**: `javax.sound.sampled` ships with the JDK; nothing else in
  scope needs an external codec or resampling library (Constitution VII).

All `NEEDS CLARIFICATION` markers: none were present at `/speckit-specify`
time (the spec's own Assumptions section pre-resolved the three ambiguous
points — sample source, loop points, default voice count — so no
clarification round was needed before planning).
