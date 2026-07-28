package dev.muzziknod.host.lifecycle

import dev.muzziknod.host.contract.PortSpec
import kotlin.test.Test
import kotlin.test.assertEquals

class ModuleRegistryStateTest {
    @Test
    fun stateMatchesAllImmediatelyAfterLoad() {
        val registry = ModuleRegistry()
        val module = FakeModule.withPorts("a", emptyList<PortSpec>())

        registry.load(module)

        assertEquals(registry.all().toList(), registry.state.value)
    }

    @Test
    fun stateMatchesAllImmediatelyAfterRemoveImmediately() {
        val registry = ModuleRegistry()
        val module = FakeModule.withPorts("a", emptyList<PortSpec>())
        registry.load(module)

        registry.removeImmediately("a")

        assertEquals(registry.all().toList(), registry.state.value)
        assertEquals(emptyList(), registry.state.value)
    }
}
