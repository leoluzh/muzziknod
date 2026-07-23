package dev.muzziknod.host.lifecycle

import dev.muzziknod.host.graph.RoutingGraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ModuleRemovalDeferralTest {
    @Test
    fun removalRequestedMidCycleIsDeferredUntilCycleCompletes() {
        val registry = ModuleRegistry()
        val graph = RoutingGraph(registry)

        val victim = FakeModule.withPorts("victim", emptyList())
        registry.load(victim)

        var stateSeenDuringCycle: ModuleState? = null
        val requester = FakeModule.withPorts("requester", emptyList()).apply {
            onProcessAction = {
                graph.removeModule("victim")
                // Still inside the cycle: the removal must not have applied yet.
                stateSeenDuringCycle = registry.get("victim")?.state
            }
        }
        registry.load(requester)

        graph.processCycle()

        assertEquals(ModuleState.Active, stateSeenDuringCycle, "removal must be deferred while the cycle is in progress")
        assertNotNull(stateSeenDuringCycle)
        assertEquals(null, registry.get("victim"), "removal must apply once the cycle has completed")
        assertEquals(1, victim.removeCount)
    }
}