# Contract: Project File Schema

The externally-visible artifact this feature produces and consumes — the format any
tool (this application, a future importer/exporter, a human inspecting the file)
relies on.

## Format

UTF-8 JSON, top-level object matching `ProjectSnapshot` (see
[data-model.md](../data-model.md)). Written/read via `kotlinx.serialization`'s JSON
encoder/decoder — field names below are exact wire field names.

## Shape

```json
{
  "schemaVersion": 1,
  "modules": [
    {
      "instanceId": "delay-1",
      "typeId": "delay",
      "parameters": { "mix": 0.5, "delayTimeMs": 250.0, "feedback": 0.3 },
      "moduleData": null
    },
    {
      "instanceId": "sampler-1",
      "typeId": "sampler",
      "parameters": {},
      "moduleData": {
        "zones": [
          {
            "sourcePath": "/home/user/samples/kick.wav",
            "sampleId": "kick",
            "rootNote": 60,
            "lowNote": 48,
            "highNote": 72,
            "gain": 1.0,
            "loopMode": "OneShot"
          }
        ]
      }
    }
  ],
  "connections": [
    {
      "id": "conn-1",
      "sourceInstanceId": "sampler-1",
      "sourcePortId": "out",
      "targetInstanceId": "delay-1",
      "targetPortId": "in"
    }
  ],
  "transport": {
    "tempoBpm": 120.0,
    "positionBeats": 0.0,
    "isPlaying": false,
    "loopStart": null,
    "loopEnd": null
  }
}
```

## Compatibility rules

- **Reader MUST** reject a file whose `schemaVersion` is greater than the highest
  version it implements, with a clear "unsupported project file version" error
  (FR-011) — never attempt to decode the rest of the file first.
- **Reader MUST** tolerate unknown fields in a `ModuleSnapshot.moduleData` object it
  doesn't recognize for a given `typeId` it *does* recognize (forward-compatible
  ignore-unknown-keys decoding), so a minor addition to one module's codec doesn't
  break older readers loading files from that module's newer codec — as long as
  `schemaVersion` itself hasn't incremented.
- **Reader MUST NOT** fail the entire load because one `ModuleSnapshot.typeId` is
  unrecognized or one sampler `sourcePath` can't be found — see
  `module-state-codec.md`'s `LoadWarning` contract.
- **Writer MUST** always write the current `schemaVersion` the running build
  implements; there is no "save in an older format" mode in this feature's scope.

## Consumers

- `ProjectWriter`/`ProjectReader` (`:project-persistence`) — the only code that reads
  or writes this format.
- `ui-desktop`'s save/load menu actions call into `ProjectWriter`/`ProjectReader`; they
  never construct or parse this JSON directly.
