# Phase 0 Research: Project Persistence

## Parameter capture strategy

**Decision**: Per-module-type `ModuleStateCodec`s registered in a new
`ProjectPersistenceCatalog` (`typeId -> ModuleStateCodec`), not a generic
get/set-by-id API added to `core-host`'s `Module`/`ModuleContract`.

**Rationale**: `ParameterSpec` in `ModuleContract.parameters` is UI-metadata only
(id/label/range/default) — there is no runtime get/set-by-id path anywhere today.
Every existing module (`DelayModule`, `ReverbModule`, `DistortionModule`, `EqModule`,
`MidiSequencerModule`) instead exposes typed setters (`setMix(Double)`, `setBpm(Int)`,
...) and `StateFlow<Double>` mirrors, and `ui-desktop`'s `ModuleControls.kt` already
dispatches on `typeName` to call them directly. A codec-per-type mirrors that existing,
working pattern exactly instead of inventing a second, parallel generic-parameter
mechanism that would have to be retrofitted onto five already-shipped modules.

**Alternatives considered**:
- *Add `Module.getParameter(id)/setParameter(id, value)` to the core contract*: touches
  `core-host` and every existing module (001-005), a breaking contract change requiring
  the explicit migration plan Constitution VI demands, for a payoff (generic capture)
  this feature can get for free from the catalog pattern already in use.
- *Reflection over each module's properties*: not viable in Kotlin/Native or with R8/
  minification later, and violates Constitution IV's portability intent.

## Transport

**Decision**: Introduce a new, minimal host-level `Transport` in `core-host`
(`transport/Transport.kt`): pure state — `tempoBpm: Double`, `positionBeats: Double`,
`isPlaying: Boolean`, `loopStart: Double?`, `loopEnd: Double?` — with
`play()/pause()/stop()/setTempo()/setPosition()/setLoopRange()` and a
`StateFlow<TransportState>`, alongside `ModuleRegistry` and `RoutingGraph` as the third
piece of host-owned, control-plane state.

**Rationale**: No host-wide transport exists today — only `MidiSequencerModule` tracks
its own local `bpm`/`currentStep`/`isPlaying`, and there is no loop-range concept
anywhere. Spec FR-004 requires a project-wide tempo/position/loop-range/play-state,
which nothing in the codebase currently owns. Confirmed with the user
(2026-07-29): the smaller scope of "persist whatever midi-sequencer already tracks,
drop loop range" was rejected in favor of adding this minimal transport now, since the
spec is explicit about loop range and a project-wide position. `Transport` is pure data
plus control-plane methods — it never participates in `process()`, so Constitution III
does not bind it, and it needs no knowledge of any module type, so it belongs in
`core-host` next to `ModuleRegistry`/`RoutingGraph` rather than in
`:project-persistence`.

**Alternatives considered**:
- *Derive project-level transport from `MidiSequencerModule`'s state*: rejected by the
  user — doesn't cover projects with no sequencer instance, and can't represent loop
  range at all.
- *Put `Transport` inside `:project-persistence` instead of `core-host`*: rejected —
  `Transport` is live, mutable host state a running session needs regardless of
  save/load (e.g. for future transport UI), not persistence-specific; `core-host` is
  where `ModuleRegistry`/`RoutingGraph` already live for the same reason.

## Sample file path tracking

**Decision**: Add an optional `sourcePath: String?` to `modules/sampler`'s
`SampleZone`, and a matching optional `sourcePath` parameter to
`SamplerModule.loadSample(...)`, defaulting to `null` (fully backward compatible with
existing call sites).

**Rationale**: FR-005/FR-006 require persisting a *reference* to each sample's source
file, but `SamplerModule.loadSample(bytes: ByteArray, ...)` and `Sample`/`SampleZone`
today store only decoded PCM data and an arbitrary `id` string — no file path survives
past the initial decode. Since 005-sampler-module was never wired into `ui-desktop`
(absent from `ModuleCatalog`), there is no existing "load from disk path" call site to
preserve compatibility with beyond the additive default.

**Alternatives considered**:
- *Store only the sample `id` and require callers to re-resolve it to a path
  out-of-band*: pushes the missing-file detection problem (FR-010) onto every caller
  instead of solving it once in the codec; rejected as more code for no benefit.
- *Track paths entirely inside `:project-persistence` in a side table keyed by
  `id`*: works but duplicates state that belongs on the zone itself and drifts if a
  zone is reloaded independently of a save/load cycle; rejected in favor of the
  single-source-of-truth additive field.

## Serialization format & library

**Decision**: `kotlinx.serialization` with the JSON format, added as a new dependency
(`org.jetbrains.kotlinx:kotlinx-serialization-json`) plus its Gradle plugin.

**Rationale**: KMP-native (works identically across `commonMain` targets, no JVM
reflection), compile-time-checked `@Serializable` DTOs catch schema drift at build
time, and JSON keeps project files human-readable/diffable — useful for debugging and
matches how most DAW-adjacent tools expose project files. No serialization library
exists in the project yet (`gradle/libs.versions.toml` confirmed empty of one), so this
is a net-new addition, not a replacement.

**Alternatives considered**:
- *Hand-rolled JSON via string building*: error-prone, no compile-time schema
  checking; rejected.
- *Binary format (protobuf/CBOR)*: smaller/faster but opaque to hand inspection and
  adds a schema-definition step with no benefit at this project's current scale
  (spec Scale/Scope: single-machine, tens of modules); rejected per Constitution VII
  (YAGNI) — can be revisited if file size ever becomes a real problem.

## Missing module type / missing sample handling

**Decision**: `ProjectReader.load(...)` returns a `ProjectLoadResult(warnings: List<
LoadWarning>)` where `LoadWarning` is a sealed type (`MissingModuleType(typeId,
instanceId)`, `MissingSampleFile(instanceId, path)`); the reader always finishes
building whatever it *can* resolve rather than throwing on the first unresolvable
reference.

**Rationale**: Directly implements FR-009/FR-010 ("report which module(s)/sample(s)
could not be restored... continue loading the rest of the project"). A warnings list on
the result type keeps the reader's control flow linear (no partial-exception-then-
resume logic) and gives `ui-desktop` a single place to surface a "N items could not be
restored" summary to the user.

**Alternatives considered**:
- *Throw on first missing reference*: violates FR-009/FR-010 directly (spec requires
  the rest of the project to still load); rejected.
- *Silently skip missing references with no report*: violates FR-010's explicit "report
  which sample(s) are missing by name"; rejected.

## Schema versioning

**Decision**: `ProjectSnapshot.schemaVersion: Int`, starting at `1`. `ProjectReader`
checks it before attempting to decode the rest of the file; a version newer than the
reader understands produces a clear "unsupported project file version" error (FR-011)
rather than a confusing deserialization failure.

**Rationale**: Simplest mechanism that satisfies FR-011 without speculative
forward-compatibility machinery this feature doesn't yet need (Constitution VII).
