# Feature Specification: Project Persistence

**Feature Branch**: `006-project-persistence`

**Created**: 2026-07-29

**Status**: Draft

**Input**: User description: "Project persistence: save and load full project as file. Persist host graph (module instances + connections), module parameter values, transport state, and sampler module data (sample references/data) to a project file. Loading a project file restores exact prior state."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Save Current Work to a Project File (Priority: P1)

A musician has built up a session — module instances wired together, parameters tuned, samples loaded — and saves it to a project file so the work is not lost when the application closes.

**Why this priority**: Without save, no other part of this feature has value — nothing can be restored if nothing was ever captured. This is the minimum viable slice.

**Independent Test**: Build a session with at least two connected modules and non-default parameter values, save to a file, and confirm the file is created and contains the graph, connections, and parameter values.

**Acceptance Scenarios**:

1. **Given** a session with module instances, connections between them, and non-default parameter values, **When** the user saves the project, **Then** a project file is created containing the module graph, connections, and current parameter values.
2. **Given** an already-saved project file, **When** the user makes further changes and saves again to the same file, **Then** the file is overwritten with the current state.
3. **Given** an existing saved project, **When** the user chooses to save the current state under a new file name ("save as"), **Then** a new project file is created without modifying the original file.

---

### User Story 2 - Load a Saved Project and Resume Work (Priority: P1)

A musician reopens the application (or switches projects) and loads a previously saved project file, seeing the exact same module graph, connections, parameter values, transport state, and sampler content they left behind.

**Why this priority**: Save only has value if the state can be faithfully restored. Together with User Story 1 this is the complete round-trip that makes persistence usable, so it ships in the same MVP slice.

**Independent Test**: Save a project with a known graph/parameter/transport/sample configuration, close and reopen the application (or reset host state), load the file, and confirm every module, connection, parameter value, transport setting, and sample mapping matches what was saved.

**Acceptance Scenarios**:

1. **Given** a project file saved with a specific module graph and connections, **When** the user loads that file, **Then** the host reconstructs the same module instances and connections.
2. **Given** a project file saved with specific parameter values across modules, **When** the user loads that file, **Then** every module's parameters match the saved values exactly.
3. **Given** a project file saved with a specific transport state (tempo, playback position, loop range, play/pause/stop), **When** the user loads that file, **Then** the transport is restored to that exact state.
4. **Given** a project file saved with a sampler module holding loaded samples, **When** the user loads that file, **Then** the sampler module has the same samples mapped to the same notes/settings as at save time.

---

### User Story 3 - Recover Gracefully When Referenced Content Is Missing (Priority: P2)

A musician loads a project file whose sample files have since been moved, renamed, or deleted, and the application tells them exactly which samples could not be found instead of failing to load the whole project or silently producing an incomplete session.

**Why this priority**: Sample files live outside the project file and routinely move between machines or get cleaned up; without this, a single missing file could block loading an otherwise-intact project. It depends on Story 2 already working.

**Independent Test**: Save a project referencing a sample file, delete or move that sample file on disk, load the project, and confirm the rest of the project loads normally while the specific missing sample is clearly flagged.

**Acceptance Scenarios**:

1. **Given** a project file referencing a sample file that no longer exists at its saved location, **When** the user loads the project, **Then** the rest of the project (graph, connections, other modules, parameters, transport) loads normally and the missing sample is reported by name.
2. **Given** a project file referencing a module type that is not available in the current application, **When** the user loads the project, **Then** the rest of the project loads normally and the unavailable module is reported distinctly.

---

### Edge Cases

- What happens when the project file itself is corrupted, truncated, or not a recognizable project file?
- What happens when a project file was saved by a newer/older version of the application than the one loading it?
- What happens when the user saves while the transport is actively playing?
- What happens when two module instances are wired in a way that no longer matches the currently registered module contracts (e.g. a connection to a port that no longer exists)?
- What happens when the user attempts to load a file while unsaved changes exist in the current session?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST allow the user to save the current project state to a file.
- **FR-002**: A saved project file MUST capture the full module graph: every module instance, its type, and every connection between module instances.
- **FR-003**: A saved project file MUST capture the current parameter values for every module instance in the graph.
- **FR-004**: A saved project file MUST capture the transport state at save time, including tempo, playback position, loop range, and play/pause/stop status.
- **FR-005**: A saved project file MUST capture, for any sampler module in the graph, enough information (sample file references and mapping/settings) to restore the same playable sample configuration on load.
- **FR-006**: Sample audio content MUST be referenced by the project file via a path to the external sample file rather than embedded in the project file itself.
- **FR-007**: System MUST allow the user to load a previously saved project file and reconstruct the module graph, connections, parameter values, transport state, and sampler configuration to match the saved state exactly.
- **FR-008**: System MUST detect when a project file is corrupted or unreadable and report this to the user without crashing the host.
- **FR-009**: System MUST detect when a project file references a module type that is not available in the current application, report which module(s) could not be restored, and continue loading the rest of the project.
- **FR-010**: System MUST detect when a project file references a sample file that cannot be found at load time, report which sample(s) are missing by name, and continue loading the rest of the project.
- **FR-011**: A saved project file MUST record a format/schema version so future loads can detect compatibility issues between the file and the application version reading it.
- **FR-012**: System MUST allow the user to save changes to the same project file (overwrite) as well as save the current state to a new file ("save as") without altering the original file.

### Key Entities

- **Project File**: The on-disk artifact produced by save and consumed by load; carries a format/schema version, the module graph, transport state, and sampler references.
- **Module Instance**: A single instantiated module within the graph, identified by type and configuration/parameter values.
- **Connection**: A link between two module instances (or their ports) describing how signal/data flows through the graph.
- **Parameter Value**: A named, typed setting belonging to a module instance, captured at save time and reapplied at load time.
- **Transport State**: The playback-related state shared across the whole project — tempo, playback position, loop range, and play/pause/stop status.
- **Sample Reference**: A pointer from a sampler module instance to an external sample audio file, plus the mapping/settings (e.g. root note, gain) needed to reproduce playback.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can save an in-progress project and, after reloading it, find 100% of module instances, connections, and parameter values match their exact prior values.
- **SC-002**: A user can save and reload a project's transport state (tempo, position, loop range, play/pause/stop) with no manual reconfiguration needed afterward.
- **SC-003**: When a project references sample files that are still present on disk, loading restores 100% of them to a playable state without user intervention.
- **SC-004**: When a project references sample files that are missing, the user can identify every missing sample by name directly from the load result, with no missing sample silently ignored.
- **SC-005**: A user reopening a saved project after restarting the application resumes work with zero manual reconstruction of the graph, parameters, or transport.

## Assumptions

- Save and load operate on projects local to the machine running the application; no cloud sync or multi-user collaboration is in scope for this feature.
- Sample audio files themselves are not duplicated or moved by the save operation — only a reference to their location is stored, matching how the sampler module already loads samples ([005-sampler-module](../005-sampler-module/spec.md)).
- Only one project is open and edited at a time; the feature does not need to address merging or diffing two project files.
- Prompting the user about unsaved changes before loading a different project (or exiting) is expected standard behavior, though the exact UI flow is left to the implementation.
