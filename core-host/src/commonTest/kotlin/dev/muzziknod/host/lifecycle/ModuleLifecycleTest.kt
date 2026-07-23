package dev.muzziknod.host.lifecycle

import dev.muzziknod.host.contract.PortDirection
import dev.muzziknod.host.contract.PortSpec
import dev.muzziknod.host.contract.PortType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ModuleLifecycleTest {
    @Test
    fun loadingModuleIntoEmptyHostActivatesItAndExposesPorts() {
        val registry = ModuleRegistry()
        val ports = listOf(
            PortSpec(id = "out", direction = PortDirection.Output, type = PortType.Audio, sampleRate = 48000),
        )
        val module = FakeModule.withPorts("osc-1", ports)

        val result = registry.load(module)

        assertIs<LoadResult.Loaded>(result)
        val managed = registry.get("osc-1")!!
        assertEquals(ModuleState.Active, managed.state)
        assertEquals(ports, managed.ports)
        assertEquals(1, module.loadCount)
    }
}