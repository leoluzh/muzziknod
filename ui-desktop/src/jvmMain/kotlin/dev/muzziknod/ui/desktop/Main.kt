package dev.muzziknod.ui.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.muzziknod.host.graph.RoutingGraph
import dev.muzziknod.host.lifecycle.ModuleRegistry
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

/**
 * Root Composable. Wired up incrementally as each user story lands: US1 wires in
 * [GraphView] below; US3 (T040) adds [dev.muzziknod.ui.catalog.ModuleCatalog] for the
 * empty-host state.
 */
@Composable
fun App(viewModel: HostViewModel) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val state by viewModel.uiState.collectAsState()
            Box(modifier = Modifier.fillMaxSize()) {
                GraphView(
                    state = state,
                    onConnect = viewModel::connect,
                    onDisconnect = viewModel::disconnect,
                )
            }
        }
    }
}
