# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project intends to adhere to [Semantic Versioning](https://semver.org/)
once the first release is tagged. Until then, everything lives under
[Unreleased].

Machine-generated release notes for each tagged release are drafted
automatically by [release-drafter](.github/release-drafter.yml) from merged
PR labels; this file is the human-curated summary.

## [Unreleased]

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
  updates), a shared label set synced via `label-sync.yml`, and
  release-drafter for auto-drafted release notes.
- Experimental `devbox.json` for a reproducible Nix-based dev shell.

### Changed

- Bumped Kotlin from `2.3.21` to `2.4.10` (Dependabot).

[Unreleased]: https://github.com/leoluzh/muzziknod/commits/main
