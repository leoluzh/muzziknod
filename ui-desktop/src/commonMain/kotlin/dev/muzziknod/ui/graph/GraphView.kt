package dev.muzziknod.ui.graph

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.muzziknod.host.contract.PortDirection
import dev.muzziknod.host.graph.ConnectResult
import dev.muzziknod.ui.state.HostUiState

private data class SelectedPort(val instanceId: String, val portId: String)

/**
 * Grafo de roteamento do host: lista módulos/portas/conexões de [state] e permite
 * criar/remover uma conexão por seleção explícita de porta origem → porta destino
 * (research.md §3 — mecanismo mais simples que um editor drag-and-drop completo)
 * (contracts/ui-composables-contract.md; US1).
 */
@Composable
fun GraphView(
    state: HostUiState,
    onConnect: (sourceInstanceId: String, sourcePortId: String, targetInstanceId: String, targetPortId: String) -> ConnectResult,
    onDisconnect: (connectionId: String) -> Unit,
) {
    var selectedSource by remember { mutableStateOf<SelectedPort?>(null) }
    var rejectionReason by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.testTag("graph-view")) {
        if (state.modules.isEmpty()) {
            Text(
                text = "Nenhum módulo carregado. Adicione um pelo catálogo.",
                modifier = Modifier.testTag("empty-state"),
            )
            return@Column
        }

        rejectionReason?.let { reason ->
            Text(text = "Conexão recusada: $reason", modifier = Modifier.testTag("rejection-reason"))
        }

        for (module in state.modules) {
            Row(modifier = Modifier.testTag("module-${module.instanceId}")) {
                Text(text = "${module.typeName} (${module.instanceId})")
                for (port in module.ports) {
                    Text(
                        text = port.id,
                        modifier = Modifier
                            .testTag("port-${module.instanceId}-${port.id}")
                            .clickable(enabled = !module.pendingRemoval) {
                                val pending = selectedSource
                                if (pending == null) {
                                    if (port.direction == PortDirection.Output) {
                                        selectedSource = SelectedPort(module.instanceId, port.id)
                                    }
                                } else if (port.direction == PortDirection.Input) {
                                    when (
                                        val result = onConnect(
                                            pending.instanceId,
                                            pending.portId,
                                            module.instanceId,
                                            port.id,
                                        )
                                    ) {
                                        is ConnectResult.Connected -> rejectionReason = null
                                        is ConnectResult.Rejected -> rejectionReason = result.reason
                                    }
                                    selectedSource = null
                                } else {
                                    selectedSource = SelectedPort(module.instanceId, port.id)
                                }
                            },
                    )
                }
            }
        }

        for (connection in state.connections) {
            Row(modifier = Modifier.testTag("connection-${connection.id}")) {
                Text(
                    text = "${connection.sourceInstanceId}.${connection.sourcePortId} -> " +
                        "${connection.targetInstanceId}.${connection.targetPortId}",
                )
                Text(
                    text = "remover",
                    modifier = Modifier
                        .testTag("disconnect-${connection.id}")
                        .clickable { onDisconnect(connection.id) },
                )
            }
        }
    }
}
