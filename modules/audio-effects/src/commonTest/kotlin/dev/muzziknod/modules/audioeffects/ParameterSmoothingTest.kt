package dev.muzziknod.modules.audioeffects

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `ParameterSmoother.advance()` ramps linearly toward a new target within its window
 * and holds exactly at target once reached, no overshoot/oscillation (FR-005, FR-010;
 * SC-003).
 */
class ParameterSmoothingTest {
    @Test
    fun advanceRampsLinearlyThenHoldsExactlyAtTarget() {
        val smoother = ParameterSmoother(initial = 0.0, rampSamples = 4)
        smoother.setTarget(1.0)

        assertEquals(0.25, smoother.advance())
        assertEquals(0.5, smoother.advance())
        assertEquals(0.75, smoother.advance())
        assertEquals(1.0, smoother.advance())

        // Holds exactly at target, no overshoot or oscillation past the ramp window.
        repeat(5) {
            assertEquals(1.0, smoother.advance())
        }
    }

    @Test
    fun advanceRampMonotonicallyApproachesTargetFromAbove() {
        val smoother = ParameterSmoother(initial = 10.0, rampSamples = 5)
        smoother.setTarget(0.0)

        var previous = 10.0
        repeat(5) {
            val current = smoother.advance()
            assertTrue(current <= previous, "must move monotonically toward target, no oscillation")
            previous = current
        }
        assertEquals(0.0, smoother.advance())
    }
}
