package dev.muzziknod.host.graph

import dev.muzziknod.host.contract.BufferFormat
import dev.muzziknod.host.contract.PortDirection
import dev.muzziknod.host.contract.PortSpec
import dev.muzziknod.host.contract.PortType
import dev.muzziknod.host.lifecycle.FakeModule
import dev.muzziknod.host.lifecycle.ModuleRegistry
import kotlin.test.Test
import kotlin.test.assertIs

class RoutingGraphFormatMismatchTest {
    @Test
    fun sampleRateMismatchRejectsConnectionWithNoConversion() {
        val registry = ModuleRegistry()
        val graph = RoutingGraph(registry)

        val source = FakeModule.withPorts(
            "source",
            listOf(
                PortSpec(
                    id = "out",
                    direction = PortDirection.Output,
                    type = PortType.Audio,
                    sampleRate = 48_000,
                    bufferFormat = BufferFormat.Float32,
                ),
            ),
        )
        val sink = FakeModule.withPorts(
            "sink",
            listOf(
                PortSpec(
                    id = "in",
                    direction = PortDirection.Input,
                    type = PortType.Audio,
                    sampleRate = 44_100,
                    bufferFormat = BufferFormat.Float32,
                ),
            ),
        )
        registry.load(source)
        registry.load(sink)

        val result = graph.connect("source", "out", "sink", "in")

        assertIs<ConnectResult.Rejected>(result)
    }
}