package dev.muzziknod.ui.graph

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import dev.muzziknod.host.contract.PortDirection
import dev.muzziknod.host.contract.PortSpec
import dev.muzziknod.host.contract.PortType
import dev.muzziknod.host.graph.Connection
import dev.muzziknod.host.graph.ConnectResult
import dev.muzziknod.host.lifecycle.ModuleState
import dev.muzziknod.ui.state.HostUiState
import dev.muzziknod.ui.state.ModuleUiModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun outputPort(instanceId: String, portId: String = "out", pendingRemoval: Boolean = false) =
    ModuleUiModel(
        instanceId = instanceId,
        typeName = "midi-generator",
        ports = listOf(PortSpec(id = portId, direction = PortDirection.Output, type = PortType.Midi)),
        lifecycleState = ModuleState.Active,
        pendingRemoval = pendingRemoval,
    )

private fun inputPort(instanceId: String, portId: String = "in", pendingRemoval: Boolean = false) =
    ModuleUiModel(
        instanceId = instanceId,
        typeName = "midi-logger",
        ports = listOf(PortSpec(id = portId, direction = PortDirection.Input, type = PortType.Midi)),
        lifecycleState = ModuleState.Active,
        pendingRemoval = pendingRemoval,
    )

@OptIn(ExperimentalTestApi::class)
class GraphViewTest {
    @Test
    fun rendersModulesPortsAndConnectionsFromState() = runComposeUiTest {
        val state = HostUiState(
            modules = listOf(outputPort("gen-1"), inputPort("log-1")),
            connections = listOf(Connection("conn-0", "gen-1", "out", "log-1", "in")),
        )

        setContent {
            GraphView(state = state, onConnect = { _, _, _, _ -> error("not used") }, onDisconnect = {})
        }

        onNodeWithTag("module-gen-1").assertExists()
        onNodeWithTag("module-log-1").assertExists()
        onNodeWithTag("port-gen-1-out").assertExists()
        onNodeWithTag("port-log-1-in").assertExists()
        onNodeWithTag("connection-conn-0").assertExists()
    }

    @Test
    fun selectingOutputThenInputPortInvokesOnConnectWithCorrectIds() = runComposeUiTest {
        val state = HostUiState(modules = listOf(outputPort("gen-1"), inputPort("log-1")), connections = emptyList())
        var received: List<String>? = null

        setContent {
            GraphView(
                state = state,
                onConnect = { source, sourcePort, target, targetPort ->
                    received = listOf(source, sourcePort, target, targetPort)
                    ConnectResult.Connected(Connection("conn-0", source, sourcePort, target, targetPort))
                },
                onDisconnect = {},
            )
        }

        onNodeWithTag("port-gen-1-out").performClick()
        onNodeWithTag("port-log-1-in").performClick()

        assertEquals(listOf("gen-1", "out", "log-1", "in"), received)
    }

    @Test
    fun clickingDisconnectInvokesOnDisconnectWithCorrectConnectionId() = runComposeUiTest {
        val state = HostUiState(
            modules = listOf(outputPort("gen-1"), inputPort("log-1")),
            connections = listOf(Connection("conn-0", "gen-1", "out", "log-1", "in")),
        )
        var disconnectedId: String? = null

        setContent {
            GraphView(state = state, onConnect = { _, _, _, _ -> error("not used") }, onDisconnect = { disconnectedId = it })
        }

        onNodeWithTag("disconnect-conn-0").performClick()

        assertEquals("conn-0", disconnectedId)
    }

    @Test
    fun rejectedConnectShowsReasonWithoutMutatingInputState() = runComposeUiTest {
        val state = HostUiState(modules = listOf(outputPort("gen-1"), inputPort("log-1")), connections = emptyList())

        setContent {
            GraphView(
                state = state,
                onConnect = { _, _, _, _ -> ConnectResult.Rejected("type mismatch") },
                onDisconnect = {},
            )
        }

        onNodeWithTag("port-gen-1-out").performClick()
        onNodeWithTag("port-log-1-in").performClick()

        onNodeWithTag("rejection-reason").assertTextContains("type mismatch", substring = true)
        assertEquals(emptyList(), state.connections)
        assertNull(state.connections.firstOrNull())
    }

    @Test
    fun emptyHostShowsGuidanceToAddAModuleFromTheCatalog() = runComposeUiTest {
        val state = HostUiState(modules = emptyList(), connections = emptyList())

        setContent {
            GraphView(state = state, onConnect = { _, _, _, _ -> error("not used") }, onDisconnect = {})
        }

        onNodeWithTag("empty-state").assertExists()
    }
}
