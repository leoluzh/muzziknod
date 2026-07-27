# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/).

Starting with the first tagged release, new entries above this point are
generated automatically by [release-please](.github/workflows/release-please.yml)
from [Conventional Commits](https://www.conventionalcommits.org/) on `main` —
merging its release PR is what cuts the version bump and the entry. Everything
below is the hand-curated history from before release-please was set up.

## Pre-release-please history

### Added

- **003-audio-effects**: `modules:audio-effects` — Reverb (Schroeder comb/
  allpass), Delay (circular buffer), Distortion (tanh soft-clip + one-pole
  lowpass), and EQ (RBJ peaking biquads), all implementing `core-host`'s
  `Module` contract unchanged and chainable via the existing `RoutingGraph`.
  Parameters are live-adjustable via public setters, smoothed and
  range-clamped.
- **002-midi-sequencer**: `modules:midi-sequencer` — step-pattern sequencer
  with play/stop transport and live pattern editing.
- **001-core-host**: `core-host` — module lifecycle, audio/MIDI routing
  graph, and the base `Module` contract every module type implements.
- CI: GitHub Actions workflow building and testing on every push to `main`
  and every pull request (JDK 26 / Temurin, Gradle caching).
- Repo governance: `CODEOWNERS`, Dependabot (weekly Gradle + GitHub Actions
  updates), a shared label set synced via `label-sync.yml`, and (later
  replaced by release-please) release-drafter for auto-drafted release
  notes.
- Experimental `devbox.json` for a reproducible Nix-based dev shell.

### Changed

- Bumped Kotlin from `2.3.21` to `2.4.10` (Dependabot).
