package dev.muzziknod.modules.audioeffects

/**
 * Ramps [current] linearly toward a [setTarget] value over [rampSamples] calls to
 * [advance] instead of jumping instantly, to avoid audible clicks/zipper noise on
 * parameter changes mid-processing (spec FR-005, FR-010).
 */
class ParameterSmoother(initial: Double, private val rampSamples: Int = 64) {
    var current: Double = initial
        private set

    private var target: Double = initial
    private var stepSize: Double = 0.0
    private var remaining: Int = 0

    fun setTarget(value: Double) {
        target = value
        remaining = rampSamples
        stepSize = (target - current) / rampSamples
    }

    /** Call once per sample; returns the value to use for that sample. */
    fun advance(): Double {
        if (remaining > 0) {
            current += stepSize
            remaining--
            if (remaining == 0) current = target
        }
        return current
    }
}
