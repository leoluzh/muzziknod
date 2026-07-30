# Quickstart: Validating Project Persistence

Prerequisites: JDK 26 on `PATH`, repository built once (`./gradlew build`) so all
existing modules resolve.

## 1. Round-trip a graph (User Story 1 + 2)

```bash
./gradlew :project-persistence:jvmTest --tests "*ProjectRoundTripTest*"
```

Expected: builds an in-memory host (`ModuleRegistry` + `RoutingGraph` + `Transport`)
with at least one instance of every codec-backed module type, connects them, sets
non-default parameter values and a non-default transport state, saves via
`ProjectWriter` to a temp file, constructs a **fresh** `ModuleRegistry`/`RoutingGraph`/
`Transport`, loads via `ProjectReader`, and asserts:
- every module instance, type, and parameter value matches the pre-save state exactly
- every connection matches
- `Transport` state (tempo, position, loop range, play state) matches
- `ProjectLoadResult.warnings` is empty

## 2. Save, overwrite, save-as (User Story 1)

```bash
./gradlew :project-persistence:jvmTest --tests "*ProjectFileIoTest*"
```

Expected:
- saving to a new path creates the file
- saving again to the same path overwrites it (content reflects latest state, no
  duplicate/append)
- "save as" to a different path leaves the original file's content unchanged

## 3. Missing module type / missing sample (User Story 3)

```bash
./gradlew :project-persistence:jvmTest --tests "*MissingModuleWarningTest*" --tests "*MissingSampleWarningTest*"
```

Expected:
- a hand-crafted `ProjectSnapshot` JSON containing one unrecognized `typeId` loads
  successfully for every *other* module in the file, and `ProjectLoadResult.warnings`
  contains exactly one `LoadWarning.MissingModuleType` naming that `typeId`/`instanceId`
- a sampler zone whose `sourcePath` points at a deleted temp file loads the rest of the
  project normally, and `warnings` contains exactly one `LoadWarning.MissingSampleFile`
  naming that path

## 4. Corrupted file (Edge Case)

```bash
./gradlew :project-persistence:jvmTest --tests "*CorruptFileTest*"
```

Expected: loading a file with invalid JSON, or a `schemaVersion` newer than the build
understands, returns a clear, typed error/result (never an uncaught exception, never a
partially-applied host state).

## 5. Manual end-to-end check (once `ui-desktop` wiring lands)

1. Run the desktop app, build a small graph (e.g. sampler → delay), load a real WAV
   sample, tweak a couple of parameters, hit Save.
2. Close the app.
3. Reopen, hit Load, pick the saved file.
4. Confirm the graph, connections, parameter values, and loaded sample are all back
   exactly as left.
5. Move/rename the sample file on disk, repeat step 3, and confirm the app reports the
   specific missing sample instead of failing to open the project.
