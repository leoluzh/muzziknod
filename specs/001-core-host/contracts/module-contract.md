# Module Contract (host ↔ module interface boundary)

This is the versioned interface (Constitution VI) every module must implement to be
loadable by the host. Lives in `core-host` `commonMain`, package
`dev.muzziknod.host.contract`. Signatures below are the authoritative shape for Phase 2
tasks — not full implementation.

```kotlin
enum class PortDirection { Input, Output }
enum class PortType { Audio, Midi }

data class PortSpec(
    val id: String,
    val direction: PortDirection,
    val type: PortType,
    val sampleRate: Int? = null,      // null = not applicable (e.g. MIDI ports)
    val bufferFormat: BufferFormat? = null,
)

data class ParameterSpec(
    val id: String,
    val label: String,
    val range: ClosedFloatingPointRange<Double>,
    val default: Double,
)

data class ModuleContract(
    val typeId: String,
    val version: Int,
    val ports: List<PortSpec>,
    val parameters: List<ParameterSpec> = emptyList(),
)

interface Module {
    val instanceId: String
    val contract: ModuleContract

    /** Called once after the host accepts this instance; before the first processCycle(). */
    fun onLoad()

    /** One processing cycle. Must not allocate, block, or throw for control flow. */
    fun process(context: ProcessContext)

    /** Called before the host drops this instance (after any in-flight cycle completes). */
    fun onRemove()
}

interface ProcessContext {
    fun readAudio(portId: String): AudioBuffer
    fun writeAudio(portId: String, buffer: AudioBuffer)
    fun readMidi(portId: String): List<MidiEvent>
    fun writeMidi(portId: String, events: List<MidiEvent>)
}
```

## Host-side contract obligations

- The host validates a `ModuleContract` before accepting a load (FR-003): rejects on
  duplicate `typeId`+port id, unknown `PortType`, or missing required fields — with a
  clear rejection reason returned to the caller.
- The host rejects loading a second instance with an already-active `instanceId`
  (FR-012).
- The host guarantees `onLoad()` runs before any `process()` call, and `onRemove()` runs
  only after the module's last in-flight `process()` returns (FR-010).
- If `process()` throws, the host catches it at the call site, transitions that module
  to `Faulted`, and continues the cycle for all other modules (FR-011 — see
  `research.md`).

## Module-side contract obligations

- `process()` must be side-effect-free with respect to global/shared mutable state
  outside the ports it declares — the host does not police this beyond the exception
  boundary above; violating it breaks Constitution I/III guarantees.
- A module declaring an `Audio` port must declare `sampleRate`/`bufferFormat`; the host
  uses these to reject incompatible connections (FR-013 — no auto-conversion).

## Contract test suite (mandatory per Constitution "Fluxo de Desenvolvimento")

An abstract test class `ModuleContractComplianceTests` in `core-host` `commonMain`
(`dev.muzziknod.host.contract.testkit`) that every reference module subclasses in its own
`commonTest`, providing a `createModule(): Module` factory. It asserts, on a single module
instance in isolation:
1. Declared port ids in `contract.ports` are unique.
2. `onLoad()` → `process()` → `onRemove()` completes without throwing.
3. Declared ports in `contract.ports` match the ports the module actually reads/writes
   in `process()`.

It lives in `commonMain`, not `commonTest`, because Gradle/KMP has no mechanism for one
module's test source set to depend on another module's test source set.

Two host-side obligations from above are **not** single-module properties and are instead
covered by graph/registry-level integration tests, not this suite:
- "Throwing inside `process()` results in `Faulted` state, rest of the graph keeps
  running" → `RoutingGraphFaultIsolationTest`.
- "`onRemove()` only runs after the last in-flight `process()` completes" →
  `ModuleRemovalDeferralTest`.