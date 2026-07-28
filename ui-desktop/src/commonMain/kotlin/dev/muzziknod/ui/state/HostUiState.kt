package dev.muzziknod.ui.state

import dev.muzziknod.host.contract.Module
import dev.muzziknod.host.contract.PortSpec
import dev.muzziknod.host.graph.Connection
import dev.muzziknod.host.lifecycle.ModuleState

/**
 * UI-facing snapshot of the host, combined from [dev.muzziknod.host.lifecycle.ModuleRegistry.state]
 * and [dev.muzziknod.host.graph.RoutingGraph.state] (data-model.md). The UI never reads
 * those observables directly — only through [HostViewModel.uiState] (Constitution V).
 */
data class HostUiState(
    val modules: List<ModuleUiModel>,
    val connections: List<Connection>,
)

/**
 * [pendingRemoval] is true from the moment [HostViewModel.removeModule] is called for
 * this instance until the host actually drops it — the host itself has no explicit
 * "removal pending" lifecycle state distinct from [ModuleState.Removed], so this flag is
 * tracked locally by [HostViewModel] (data-model.md).
 */
data class ModuleUiModel(
    val instanceId: String,
    val typeName: String,
    val ports: List<PortSpec>,
    val lifecycleState: ModuleState,
    val pendingRemoval: Boolean,
)

/** One entry in the module catalog offered by [dev.muzziknod.ui.catalog.ModuleCatalog] (US3). */
data class ModuleCatalogEntry(
    val typeName: String,
    val factory: () -> Module,
)
