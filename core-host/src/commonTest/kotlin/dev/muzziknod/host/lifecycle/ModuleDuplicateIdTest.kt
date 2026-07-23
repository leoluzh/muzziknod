package dev.muzziknod.host.lifecycle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ModuleDuplicateIdTest {
    @Test
    fun secondLoadWithSameInstanceIdIsRejected() {
        val registry = ModuleRegistry()
        val first = FakeModule.withPorts("dup-id", emptyList())
        val second = FakeModule.withPorts("dup-id", emptyList())

        assertIs<LoadResult.Loaded>(registry.load(first))
        val result = registry.load(second)

        assertIs<LoadResult.Rejected>(result)
        assertEquals(0, second.loadCount)
        assertEquals(ModuleState.Active, registry.get("dup-id")!!.state)
    }
}