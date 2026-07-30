package dev.muzziknod.ui.state

import dev.muzziknod.host.contract.Module
import dev.muzziknod.host.graph.ConnectResult
import dev.muzziknod.host.graph.RoutingGraph
import dev.muzziknod.host.lifecycle.ModuleRegistry
import dev.muzziknod.host.transport.Transport
import dev.muzziknod.persistence.ProjectLoadException
import dev.muzziknod.persistence.ProjectReader
import dev.muzziknod.persistence.ProjectWriter
import dev.muzziknod.persistence.defaultProjectPersistenceCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * The UI's only door into the host (Constitution V) — every Composable reads
 * [uiState] and calls [connect]/[disconnect]/[addModule]/[removeModule] instead of
 * touching [ModuleRegistry]/[RoutingGraph] directly (contracts/ui-composables-contract.md).
 * Also owns the project-wide [transport] and [save]/[saveAs]/[load] project-file actions
 * (006-project-persistence).
 */
class HostViewModel(
    @PublishedApi internal val registry: ModuleRegistry,
    private val graph: RoutingGraph,
    scope: CoroutineScope,
    private val transport: Transport = Transport(),
) {
    private val pendingRemovalIds = MutableStateFlow<Set<String>>(emptySet())

    private val catalog = defaultProjectPersistenceCatalog()
    private val projectWriter = ProjectWriter(registry, graph, transport, catalog)
    private val projectReader = ProjectReader(registry, graph, transport, catalog)

    private val _projectMessage = MutableStateFlow<String?>(null)

    /** Result of the most recent [save]/[saveAs]/[load] call, for the UI to display. */
    val projectMessage: StateFlow<String?> = _projectMessage.asStateFlow()

    val uiState: StateFlow<HostUiState> = combine(
        registry.state,
        graph.state,
        pendingRemovalIds,
    ) { modules, connections, pending ->
        HostUiState(
            modules = modules.map { managed ->
                ModuleUiModel(
                    instanceId = managed.instanceId,
                    typeName = managed.contract.typeId,
                    ports = managed.ports,
                    lifecycleState = managed.state,
                    pendingRemoval = managed.instanceId in pending,
                )
            },
            connections = connections,
        )
    }.stateIn(
        scope,
        started = SharingStarted.Eagerly,
        initialValue = HostUiState(modules = emptyList(), connections = emptyList()),
    )

    fun connect(
        sourceInstanceId: String,
        sourcePortId: String,
        targetInstanceId: String,
        targetPortId: String,
    ): ConnectResult = graph.connect(sourceInstanceId, sourcePortId, targetInstanceId, targetPortId)

    fun disconnect(connectionId: String) {
        graph.disconnect(connectionId)
    }

    fun addModule(entry: ModuleCatalogEntry) {
        registry.load(entry.factory())
    }

    /**
     * Looks up the concrete [Module] instance behind [instanceId] so a Composable can
     * bind directly to its module-specific `StateFlow`s (e.g. `MidiSequencerModule
     * .transportState`, `DelayModule.mix`) — those are typed per module and don't fit a
     * single uniform interface, so this is the escape hatch `Main.kt`'s wiring uses
     * instead of `HostUiState` trying to model every module's parameter shape
     * (Constitution VII — no speculative uniform abstraction).
     */
    inline fun <reified T : Module> moduleInstance(instanceId: String): T? =
        registry.get(instanceId)?.module as? T

    /**
     * Marks [instanceId] pending before asking the host to remove it. If the host
     * removal is immediate, the instance simply vanishes from [registry].[ModuleRegistry
     * .state] on the next emission and [uiState] stops reporting it at all (the pending
     * marker becomes moot). If the host defers the removal (a cycle is in progress —
     * FR-010/FR-015), the instance stays in [registry].[ModuleRegistry.state] until the
     * cycle finishes, and [uiState] keeps reporting `pendingRemoval = true` for it in
     * the meantime. Either way, `pendingRemovalIds` only ever grows — entries for
     * instances no longer present in [registry] simply stop producing a
     * [ModuleUiModel], so a stale id is harmless (data-model.md).
     */
    fun removeModule(instanceId: String) {
        pendingRemovalIds.value += instanceId
        graph.removeModule(instanceId)
    }

    /** Overwrites [path] with the current session's state (FR-001, FR-012). */
    fun save(path: String) {
        try {
            projectWriter.save(path)
            _projectMessage.value = "Projeto salvo em $path"
        } catch (e: Exception) {
            _projectMessage.value = "Falha ao salvar projeto: ${e.message}"
        }
    }

    /** Writes the current session's state to a new [path], leaving any prior file untouched (FR-012). */
    fun saveAs(path: String) {
        try {
            projectWriter.saveAs(path)
            _projectMessage.value = "Projeto salvo como $path"
        } catch (e: Exception) {
            _projectMessage.value = "Falha ao salvar projeto: ${e.message}"
        }
    }

    /**
     * Clears the current session and rebuilds it from [path] (FR-007). Missing module
     * types/sample files are reported via [projectMessage] rather than blocking the
     * rest of the project from loading (FR-009, FR-010).
     */
    fun load(path: String) {
        registry.all().map { it.instanceId }.forEach { graph.removeModule(it) }
        try {
            val result = projectReader.load(path)
            _projectMessage.value = if (result.warnings.isEmpty()) {
                "Projeto carregado de $path"
            } else {
                "Projeto carregado de $path com avisos: ${result.warnings.joinToString()}"
            }
        } catch (e: ProjectLoadException) {
            _projectMessage.value = "Falha ao carregar projeto: ${e.message}"
        }
    }
}
