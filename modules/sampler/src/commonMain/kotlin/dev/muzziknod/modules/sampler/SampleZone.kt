package dev.muzziknod.modules.sampler

data class SampleZone(
    val sample: Sample,
    val rootNote: Int,
    val lowNote: Int = 0,
    val highNote: Int = 127,
    val gain: Double = 1.0,
    val loopMode: LoopMode = LoopMode.OneShot,
    /** External path this sample was loaded from, if any (006-project-persistence FR-006). */
    val sourcePath: String? = null,
) {
    fun covers(note: Int): Boolean = note in lowNote..highNote
}
