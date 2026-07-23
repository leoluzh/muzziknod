# Phase 1 Data Model: Core Host Modular

## ModuleContract

Declared, versioned shape a module must satisfy to be accepted by the host
(Constitution VI).

| Field | Type | Notes |
|---|---|---|
| `id` | `String` | Unique module type identifier (not the instance id) |
| `version` | `Int` | Contract version for this module type |
| `ports` | `List<PortSpec>` | Declared input/output ports, audio and/or MIDI |
| `parameters` | `List<ParameterSpec>` | Configurable parameters exposed to host/UI (definition only — no UI in this feature) |

## PortSpec / Port

| Field | Type | Notes |
|---|---|---|
| `id` | `String` | Unique within the owning module |
| `direction` | `Input \| Output` | |
| `type` | `Audio \| Midi` | Used to validate connection compatibility (FR-004/FR-005) |
| `sampleRate` / `bufferFormat` | value object | Used to validate compatibility (FR-013) — mismatch rejects connection, no conversion (see research.md) |

## Module (instance)

| Field | Type | Notes |
|---|---|---|
| `instanceId` | `String` | Unique per loaded instance (FR-012: host rejects a second load with the same id) |
| `contract` | `ModuleContract` | The declared contract this instance satisfies |
| `state` | `Loaded \| Active \| Faulted \| Removed` | Lifecycle (spec Key Entities; Faulted added per FR-011 resolution) |
| `ports` | `List<Port>` | Bound instances of the contract's declared `PortSpec`s |

**State transitions**:
`Loaded → Active` (on successful validation, FR-001/FR-003) → `Removed` (FR-009/FR-010,
deferred until current cycle completes) or `Active → Faulted` (FR-011, excluded from
future cycles, does not transition to `Removed` automatically — removal is still an
explicit user action).

## Connection

| Field | Type | Notes |
|---|---|---|
| `id` | `String` | |
| `sourcePort` | `Port` (Output) | |
| `targetPort` | `Port` (Input) | |

**Validation rules** (enforced at creation, FR-004/FR-005/FR-006/FR-013):
- `sourcePort.direction == Output`, `targetPort.direction == Input`.
- `sourcePort.type == targetPort.type` (Audio↔Audio or MIDI↔MIDI only).
- `sourcePort.sampleRate/bufferFormat == targetPort.sampleRate/bufferFormat` (no
  auto-conversion — see research.md).
- Adding this connection must not introduce an unsupported feedback cycle in the graph
  (FR-006).

## RoutingGraph

| Field | Type | Notes |
|---|---|---|
| `modules` | `Set<Module>` | Active + Faulted + Loaded instances currently held by the host |
| `connections` | `Set<Connection>` | |

**Behavior**:
- `processCycle()`: topologically sorts modules by connection dependency and invokes
  `process()` on each `Active` module in order; a module that faults during its own
  `process()` transitions to `Faulted` and is skipped in all subsequent cycles, without
  aborting the current cycle for the rest of the graph (FR-007, FR-011 resolution).
- `connect(sourcePort, targetPort)` / `disconnect(connectionId)`: mutate `connections`
  under the validation rules above (FR-004–FR-008).
- `removeModule(instanceId)`: deferred until the in-flight `processCycle()` completes,
  then removes the module and all connections touching it (FR-009, FR-010).

## Relationships

```text
ModuleContract 1 ── * PortSpec
Module        * ── 1 ModuleContract   (instance satisfies a contract)
Module         1 ── * Port            (bound port instances)
Connection     * ── 1 Port (source, Output)
Connection     * ── 1 Port (target, Input)
RoutingGraph    1 ── * Module
RoutingGraph    1 ── * Connection
```