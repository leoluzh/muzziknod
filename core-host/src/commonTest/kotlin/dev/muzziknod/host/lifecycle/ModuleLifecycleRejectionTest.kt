package dev.muzziknod.host.lifecycle

import dev.muzziknod.host.contract.PortDirection
import dev.muzziknod.host.contract.PortSpec
import dev.muzziknod.host.contract.PortType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ModuleLifecycleRejectionTest {
    @Test
    fun invalidContractIsRejectedWithoutAffectingActiveModules() {
        val registry = ModuleRegistry()
        val valid = FakeModule.withPorts("valid", emptyList())
        assertIs<LoadResult.Loaded>(registry.load(valid))

        val duplicatePortIds = listOf(
            PortSpec(id = "dup", direction = PortDirection.Output, type = PortType.Audio),
            PortSpec(id = "dup", direction = PortDirection.Output, type = PortType.Audio),
        )
        val invalid = FakeModule.withPorts("invalid", duplicatePortIds)

        val result = registry.load(invalid)

        assertIs<LoadResult.Rejected>(result)
        assertEquals(ModuleState.Active, registry.get("valid")!!.state)
        assertEquals(null, registry.get("invalid"))
    }
}