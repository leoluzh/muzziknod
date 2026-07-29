# Feature Specification: Sampler Module

**Feature Branch**: `005-sampler-module`

**Created**: 2026-07-28

**Status**: Draft

**Input**: User description: "Sampler module: load audio samples (WAV/AIFF, common bitrates/sample rates), trigger playback via MIDI note-on/off with velocity, support one-shot and looped sample modes, pitch-shift/transpose per note, per-sample gain and root-note mapping, polyphonic voice management. Follows the same plugável module contract as 002-midi-sequencer and 003-audio-effects."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Load and Trigger a Sample (Priority: P1)

A musician loads a single audio sample (e.g. a kick drum hit) into the sampler module and plays it by sending MIDI note-on messages, hearing the sample at the correct pitch and volume for the note and velocity played.

**Why this priority**: This is the minimum viable sampler — without load + trigger, nothing else in the module has value. It proves the module can consume audio files and respond to MIDI, which is the core contract with the host.

**Independent Test**: Load one WAV file, send a note-on for the sample's mapped root note, confirm audio output starts at unity pitch/gain; send note-off, confirm one-shot samples finish naturally while looped samples stop or release.

**Acceptance Scenarios**:

1. **Given** a valid WAV sample is loaded and mapped to root note C3, **When** a MIDI note-on for C3 at velocity 127 is received, **Then** the sample plays back at its original pitch and at full gain.
2. **Given** a sample is loaded, **When** an unsupported or corrupt audio file is provided, **Then** the module reports a load error and does not crash or leave a partially-loaded voice.
3. **Given** a one-shot sample is playing, **When** a MIDI note-off is received before playback finishes, **Then** the sample continues to play to completion (one-shot behavior).

---

### User Story 2 - Play Across the Keyboard with Correct Pitch (Priority: P2)

A musician plays notes above and below a sample's root note and hears the sample pitch-shifted up or down accordingly, so a single recorded sample can cover a full musical range without needing a new recording per note.

**Why this priority**: Pitch mapping is what turns a single audio file into a playable instrument; it's the key differentiator between a sampler and a simple one-button audio player. It builds directly on Story 1.

**Independent Test**: With the same sample mapped to root note C3, send note-on messages for C2 and C4 independently; confirm playback is audibly transposed down and up respectively (e.g. by measuring output pitch/frequency), without reloading the sample.

**Acceptance Scenarios**:

1. **Given** a sample mapped to root note C3, **When** a note-on for C4 (one octave above) is received, **Then** the sample plays back transposed up one octave.
2. **Given** a sample mapped to root note C3, **When** a note-on for C2 (one octave below) is received, **Then** the sample plays back transposed down one octave.
3. **Given** a per-sample gain setting other than unity, **When** the sample is triggered at any note, **Then** the configured gain is applied in addition to velocity scaling.

---

### User Story 3 - Play Chords and Overlapping Notes (Priority: P3)

A musician plays multiple notes at once, or retriggers the same note before the previous hit has finished, and hears all of them sound correctly without audio glitches, dropped notes, or the module crashing.

**Why this priority**: Real musical use requires polyphony — chords, fast repeated hits, sustained loops layered with new one-shots. This is a refinement on top of Stories 1-2 rather than a precondition for them.

**Independent Test**: Send multiple simultaneous note-on messages for different notes and confirm all sound together; rapidly retrigger the same note and confirm each hit is audible (voice stealing does not silently drop the newest or produce clicks).

**Acceptance Scenarios**:

1. **Given** a sample loaded, **When** 4 different notes are triggered simultaneously, **Then** all 4 voices play back concurrently and independently.
2. **Given** the module's maximum voice count is reached, **When** an additional note-on is received, **Then** the module frees the oldest/quietest voice (voice stealing) to accommodate the new note without an audible glitch.
3. **Given** a looped sample is sustaining on a held note, **When** note-off is received, **Then** the voice releases (stops or fades) rather than looping indefinitely after the key is released.

---

### Edge Cases

- What happens when no sample is loaded and a note-on is received? (Module MUST ignore the event silently, no error.)
- How does the system handle a note-on with velocity 0? (Treated as note-off, per standard MIDI convention.)
- What happens when the same note is triggered again while already sounding and the module is in one-shot mode? (A new overlapping voice starts; the previous voice keeps playing to completion, subject to voice-stealing limits.)
- How does the module handle a sample file with a sample rate or bit depth different from the host's audio engine? (Sample MUST be converted/resampled at load time so playback matches the host's operating sample rate.)
- What happens when extreme transposition (e.g. 4+ octaves from root) is requested? (Module MUST still produce audio, without imposing an artificial range limit, though quality may degrade at extreme ratios.)

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Module MUST load audio samples from WAV and AIFF files at common sample rates (44.1kHz, 48kHz, 96kHz) and bit depths (16-bit, 24-bit, 32-bit float).
- **FR-002**: Module MUST report a clear, non-crashing error when asked to load a file that is missing, unreadable, or in an unsupported format.
- **FR-003**: Module MUST trigger sample playback in response to MIDI note-on events and stop or release playback in response to matching MIDI note-off events.
- **FR-004**: Module MUST scale playback gain according to the velocity of the triggering note-on, in addition to any configured per-sample gain.
- **FR-005**: Module MUST support two playback modes per sample: one-shot (plays to completion regardless of note-off) and looped (sustains for as long as the note is held, then releases on note-off).
- **FR-006**: Module MUST allow each loaded sample to be assigned a root note, and MUST pitch-shift/transpose playback for notes above or below that root note so the sample plays at the musically correct pitch.
- **FR-007**: Module MUST allow each loaded sample to have an independent gain setting, applied on top of velocity scaling.
- **FR-008**: Module MUST support polyphonic playback, sounding multiple independently-triggered notes concurrently.
- **FR-009**: Module MUST apply voice stealing (reclaiming the oldest or quietest active voice) when the number of simultaneously active voices exceeds the module's configured maximum, without producing audible clicks or pops.
- **FR-010**: Module MUST expose its loaded samples, mappings, and playback state through the host's module contract (the same plugin/module interface used by 002-midi-sequencer and 003-audio-effects), so the host can route MIDI to it and route its audio output onward without depending on internal implementation details.
- **FR-011**: Module MUST ignore note-on events when no sample is mapped to the triggered note, without raising an error.
- **FR-012**: Module MUST treat a note-on with velocity 0 as a note-off, per standard MIDI convention.

### Key Entities

- **Sample**: A loaded audio asset (source file reference, decoded audio data, sample rate, bit depth, duration).
- **Sample Mapping**: The association between a Sample and a playable note range, including root note, gain, and playback mode (one-shot vs. looped).
- **Voice**: A single active instance of a Sample currently playing back, with its own pitch ratio, gain, and playback position; multiple Voices may reference the same Sample concurrently.
- **Voice Pool**: The bounded set of concurrently available Voices, governing polyphony limits and voice-stealing behavior.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A musician can load a sample and hear correct playback on the first attempt, with no manual configuration beyond selecting the file and root note.
- **SC-002**: Pitch-shifted playback across at least a 2-octave range above and below the root note remains recognizably the same sound (timbre-preserved transposition), verified by listening tests.
- **SC-003**: The module sustains at least 16 simultaneous voices without audible glitches, dropouts, or crashes.
- **SC-004**: Voice stealing under maximum polyphony produces no audible click or pop in at least 95% of manual test trials.
- **SC-005**: Sample load failures (missing/corrupt/unsupported file) are surfaced as a reported error within 1 second, with zero crashes across repeated failure-injection tests.

## Assumptions

- Sample files are provided from local disk (or a project's bundled asset directory); streaming samples from network sources is out of scope for this feature.
- Real-time-safety of the playback path (no allocation/blocking I/O in the audio hot path, per the project constitution) is a planning/implementation concern for `/speckit-plan`, not a user-facing requirement captured here.
- Per-sample looping uses whole-file loop points (start/end markers within the file) rather than user-defined custom loop points in v1; custom loop point editing is out of scope for this feature.
- MIDI channel/routing to the sampler module is handled by the host's existing MIDI routing (as established in 002-midi-sequencer); this spec covers the module's response to MIDI it receives, not routing itself.
- A default maximum voice count (e.g. 16-32) is configurable but ships with a reasonable built-in default; the exact number is a planning decision, not fixed here.
