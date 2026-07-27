package dev.muzziknod.modules.audioeffects

/** Linear dry/wet crossfade (spec FR-002-FR-005): `mix = 0.0` is 100% dry, `mix = 1.0` is 100% wet. */
object WetDryMixer {
    fun mix(dry: Float, wet: Float, mix: Double): Float =
        (dry * (1.0 - mix) + wet * mix).toFloat()
}
