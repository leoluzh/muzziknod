# Contract: ModuleStateCodec

The plugin point a product module implements to participate in project persistence.
Lives in `project-persistence/src/commonMain/kotlin/dev/muzziknod/persistence/codec/`
(not in `core-host`, per research.md "Parameter capture strategy" — this keeps the
`Module`/`ModuleContract` core contract from 001-core-host completely unchanged).

## Interface

```kotlin
interface ModuleStateCodec {
    val typeId: String

    fun capture(module: Module): ModuleSnapshot

    fun restore(instanceId: String, snapshot: ModuleSnapshot): Module
}
```

- `capture` reads whatever public API the concrete module type exposes (typed getters/
  `StateFlow` values, e.g. `DelayModule.mix.value`) and returns a `ModuleSnapshot` with
  `parameters` populated (plus `moduleData` for types that need it, e.g. sampler zones).
- `restore` constructs a **new** instance of the concrete module type (`instanceId` from
  the snapshot) and applies every captured parameter/data field back onto it via the
  same public setters `capture` read from — it does not go through `ModuleRegistry`
  itself; the caller (`ProjectReader`) is responsible for calling `ModuleRegistry.load()`
  with the result.

## Registration

`ProjectPersistenceCatalog` holds `Map<String, ModuleStateCodec>` keyed by `typeId`,
built once at startup from one entry per shipped module type (mirrors `ui-desktop`'s
`ModuleCatalog.defaultModuleCatalog()` shape, but for codecs instead of instance
factories):

```kotlin
class ProjectPersistenceCatalog(private val codecs: Map<String, ModuleStateCodec>) {
    fun codecFor(typeId: String): ModuleStateCodec?
}
```

`ProjectReader.load(...)` looks up `codecFor(snapshot.typeId)`; a `null` result produces
a `LoadWarning.MissingModuleType(typeId, instanceId)` and that one module is skipped —
the rest of the project still loads (FR-009).

## Obligations for a codec implementation

1. `capture` MUST be lossless for every field `restore` consumes — a `capture` →
   `restore` → `capture` round trip on the same live module must produce an identical
   `ModuleSnapshot` (this is exactly what `ProjectRoundTripTest` asserts per module
   type).
2. `capture`/`restore` MUST only use the module type's already-public API — no
   reflection, no reaching into private state (Constitution I: this is a host service
   depending on public module APIs, same as `ui-desktop`'s `ModuleControls`, never on
   module internals).
3. A module type that references external files (today: only `sampler`, via
   `sourcePath`) MUST resolve them during `restore` and, if a referenced file cannot be
   read, MUST NOT throw — it returns as much of the module as it could restore, and the
   caller (`ProjectReader`) records a `LoadWarning.MissingSampleFile(instanceId, path)`
   for each one that failed (FR-010).
4. Adding a new field to a codec's `parameters`/`moduleData` output is additive and
   MUST NOT require a `schemaVersion` bump on its own, provided older readers ignore
   unknown keys (see `project-file-schema.md`'s compatibility rules) — only a change
   that existing readers cannot safely ignore requires bumping `schemaVersion`.

## Shipped codecs (this feature)

| `typeId` | Codec | `moduleData` |
|---|---|---|
| `"delay"` | `DelayCodec` | none — `mix`, `delayTimeMs`, `feedback` all in `parameters` |
| `"reverb"` | `ReverbCodec` | none |
| `"distortion"` | `DistortionCodec` | none |
| `"eq"` | `EqCodec` | none — composite ids like `"low.freqHz"` are flat `parameters` keys, same as the module's own `ParameterSpec` ids |
| `"midi-sequencer"` | `MidiSequencerCodec` | none — `bpm` and pattern data go in `parameters`/a dedicated `moduleData` shape as needed |
| `"sampler"` | `SamplerCodec` | `SamplerData` (zone list with `sourcePath`, see data-model.md) |

Reference module types (`oscillator`, `midi-generator`, `midi-logger` under
`:reference-modules:`) are scaffolding, not product modules — no codec ships for them
in this feature (spec Assumptions; Constitution VII).
