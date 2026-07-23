package dev.muzziknod.host.lifecycle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ModuleLifecycleIsolationTest {
    @Test
    fun loadingSecondModuleLeavesFirstUntouched() {
        val registry = ModuleRegistry()
        val first = FakeModule.withPorts("first", emptyList())
        val second = FakeModule.withPorts("second", emptyList())

        assertIs<LoadResult.Loaded>(registry.load(first))
        val firstManagedBefore = registry.get("first")!!
        assertEquals(ModuleState.Active, firstManagedBefore.state)

        assertIs<LoadResult.Loaded>(registry.load(second))

        val firstManagedAfter = registry.get("first")!!
        assertEquals(ModuleState.Active, firstManagedAfter.state)
        assertEquals(1, first.loadCount)
        assertTrue(first.processCount == 0 && first.removeCount == 0, "second load must not touch first")
    }
}