# Phase 1 Data Model: Project Persistence

All DTOs below live in `project-persistence/src/commonMain/kotlin/dev/muzziknod/persistence/model/`
and are `@Serializable` (kotlinx.serialization). They are pure data — no behavior — kept
separate from the live host types (`Module`, `Connection`, `Transport`) they snapshot.

## ProjectSnapshot

The root of a project file.

| Field | Type | Notes |
|---|---|---|
| `schemaVersion` | `Int` | `1` for this feature. Checked before decoding the rest (FR-011). |
| `modules` | `List<ModuleSnapshot>` | One per live `ManagedModule` in `ModuleRegistry` at save time (FR-002, FR-003). |
| `connections` | `List<ConnectionSnapshot>` | One per `Connection` in `RoutingGraph` at save time (FR-002). |
| `transport` | `TransportSnapshot` | Host `Transport` state at save time (FR-004). |

**Validation**: `schemaVersion` must be a version this build's `ProjectReader`
recognizes; every `ConnectionSnapshot.sourceInstanceId`/`targetInstanceId` should
reference an `instanceId` present in `modules` (violations are tolerated at load —
see `LoadWarning` — not treated as a fatal parse error, since a hand-edited or
partially-migrated file shouldn't block loading everything else it *can* resolve).

## ModuleSnapshot

One module instance.

| Field | Type | Notes |
|---|---|---|
| `instanceId` | `String` | Matches `Module.instanceId` (FR-002). |
| `typeId` | `String` | Matches `ModuleContract.typeId`, e.g. `"delay"`, `"sampler"` (FR-002). Used to look up a `ModuleStateCodec` in `ProjectPersistenceCatalog` on load. |
| `parameters` | `Map<String, Double>` | Every module-defined parameter id → its value at save time (FR-003). All parameter values are `Double` (research.md, confirmed by every existing module's `ParameterSpec`/setter shape). |
| `moduleData` | `JsonElement?` | Optional module-type-specific payload beyond flat parameters — used today only by `sampler` to carry its `SamplerData` (zone list); `null`/absent for modules with no extra state. |

## ConnectionSnapshot

Mirrors `core-host`'s `Connection` one-to-one; kept as a separate `@Serializable` type
so `core-host` itself never needs a serialization dependency.

| Field | Type | Notes |
|---|---|---|
| `id` | `String` | Matches `Connection.id`. |
| `sourceInstanceId` | `String` | Matches `Connection.sourceInstanceId`. |
| `sourcePortId` | `String` | Matches `Connection.sourcePortId`. |
| `targetInstanceId` | `String` | Matches `Connection.targetInstanceId`. |
| `targetPortId` | `String` | Matches `Connection.targetPortId`. |

## TransportSnapshot

Mirrors `core-host`'s new `Transport.TransportState` one-to-one (FR-004).

| Field | Type | Notes |
|---|---|---|
| `tempoBpm` | `Double` | Project tempo at save time. |
| `positionBeats` | `Double` | Playback position at save time. |
| `isPlaying` | `Boolean` | Play/pause/stop collapses to this boolean + position (stopped = not playing and position reset by whoever calls `stop()`, same as today's `MidiSequencerModule` convention). |
| `loopStart` | `Double?` | `null` when no loop range is set. |
| `loopEnd` | `Double?` | `null` when no loop range is set. |

**Validation**: `loopStart`/`loopEnd` must both be present or both `null` (a
half-specified loop range is rejected the same way `Transport.setLoopRange` rejects it
at runtime).

## SamplerData (module-specific `moduleData` payload for `typeId = "sampler"`)

| Field | Type | Notes |
|---|---|---|
| `zones` | `List<SampleZoneSnapshot>` | One per live `SampleZone` on the instance (FR-005). |

### SampleZoneSnapshot

| Field | Type | Notes |
|---|---|---|
| `sourcePath` | `String?` | External path to the sample audio file (FR-006). `null` only for zones loaded before this feature shipped or loaded without a path (e.g. programmatically, in tests). |
| `sampleId` | `String` | Matches `Sample.id`, kept for stable identity across a resave even if a path is later found missing. |
| `rootNote` | `Int` | Matches `SampleZone.rootNote`. |
| `lowNote` | `Int` | Matches `SampleZone.lowNote`. |
| `highNote` | `Int` | Matches `SampleZone.highNote`. |
| `gain` | `Double` | Matches `SampleZone.gain`. |
| `loopMode` | `String` | Serialized name of `SampleZone.loopMode` (`LoopMode.OneShot`/`Loop`). |

## LoadWarning (not part of the file format — a load-time result type)

Returned from `ProjectReader.load(...)` inside `ProjectLoadResult`, never written to
disk.

| Variant | Fields | When produced |
|---|---|---|
| `MissingModuleType` | `typeId: String`, `instanceId: String` | `ModuleSnapshot.typeId` has no entry in `ProjectPersistenceCatalog` (FR-009). |
| `MissingSampleFile` | `instanceId: String`, `sourcePath: String` | A `SampleZoneSnapshot.sourcePath` can't be read from disk at load time (FR-010). |

## Relationships

```text
ProjectSnapshot
├── modules: List<ModuleSnapshot>
│     └── moduleData (sampler only) → SamplerData
│                                        └── zones: List<SampleZoneSnapshot>
├── connections: List<ConnectionSnapshot>  ──references──> modules[].instanceId
└── transport: TransportSnapshot
```

## Mapping to live host types

| DTO | Captured from | Restored into |
|---|---|---|
| `ModuleSnapshot` | `ModuleRegistry.all()` + each module's `ModuleStateCodec.capture()` | `ProjectPersistenceCatalog` factory + `ModuleStateCodec.restore()` → `ModuleRegistry.load()` |
| `ConnectionSnapshot` | `RoutingGraph.connections()` | `RoutingGraph.connect(...)` per snapshot |
| `TransportSnapshot` | `Transport.state.value` | `Transport.setTempo/setPosition/setLoopRange/play|pause|stop` |
| `SampleZoneSnapshot` | `SamplerModule.zones` (+ new `sourcePath`) | `SamplerModule.loadSample(bytes = <read from sourcePath>, ...)` |
