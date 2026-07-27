package dev.muzziknod.ui.desktop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.muzziknod.host.graph.RoutingGraph
import dev.muzziknod.host.lifecycle.ModuleRegistry
import dev.muzziknod.ui.catalog.ModuleCatalog
import dev.muzziknod.ui.catalog.defaultModuleCatalog
import dev.muzziknod.ui.controls.ModuleControls
import dev.muzziknod.ui.graph.GraphView
import dev.muzziknod.ui.state.HostViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

fun main() = application {
    val registry = ModuleRegistry()
    val graph = RoutingGraph(registry)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val viewModel = HostViewModel(registry, graph, scope)

    Window(onCloseRequest = ::exitApplication, title = "muzziknod") {
        App(viewModel)
    }
}

/** Root Composable: module catalog (US3), graph view/edit (US1), per-module controls (US2). */
@Composable
fun App(viewModel: HostViewModel) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val state by viewModel.uiState.collectAsState()
            Box(modifier = Modifier.fillMaxSize()) {
                Column {
                    ModuleCatalog(entries = defaultModuleCatalog(), onAdd = viewModel::addModule)
                    GraphView(
                        state = state,
                        onConnect = viewModel::connect,
                        onDisconnect = viewModel::disconnect,
                    )
                    for (module in state.modules) {
                        Row {
                            ModuleControls(module.instanceId, module.typeName, viewModel, module.pendingRemoval)
                            Text(
                                text = "remover módulo",
                                modifier = Modifier
                                    .testTag("remove-module-${module.instanceId}")
                                    .clickable(enabled = !module.pendingRemoval) {
                                        viewModel.removeModule(module.instanceId)
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}
