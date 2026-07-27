# Changelog

## [1.0.1](https://github.com/leoluzh/muzziknod/compare/v1.0.0...v1.0.1) (2026-07-27)


### Bug Fixes

* Update action inputs from hyphens to underscores for actions/first-interaction@v3 ([4748fd3](https://github.com/leoluzh/muzziknod/commit/4748fd3555128f31ce068622785cd0c347cfa4b5))

## 1.0.0 (2026-07-27)


### Features

* add audio effects module (003-audio-effects) ([cfabff5](https://github.com/leoluzh/muzziknod/commit/cfabff50b3b3b5a9cf4b7a4a9f7ede2377dfd636))
* add audio effects module (003-audio-effects) ([c39eea5](https://github.com/leoluzh/muzziknod/commit/c39eea5b9da6859f0d52641a09ec20e0ade52f52))
* add MIDI sequencer module (002-midi-sequencer) ([52b04ea](https://github.com/leoluzh/muzziknod/commit/52b04eaa4e2511068c151465e9c318ea1377e93b))
* MIDI sequencer module (002-midi-sequencer) ([f962dfd](https://github.com/leoluzh/muzziknod/commit/f962dfd8443555f4b287896ea649cf67f8e1799f))


### Bug Fixes

* run release-drafter only on push to main ([d995c17](https://github.com/leoluzh/muzziknod/commit/d995c17a6eba07fd48c774b84e6721850a7267df))
* run release-drafter only on push to main ([227c564](https://github.com/leoluzh/muzziknod/commit/227c5645362b951bb093ea67077b6114c7adf264))

## Changelog

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
