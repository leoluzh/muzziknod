package dev.muzziknod.host.contract

interface ProcessContext {
    fun readAudio(portId: String): AudioBuffer
    fun writeAudio(portId: String, buffer: AudioBuffer)
    fun readMidi(portId: String): List<MidiEvent>
    fun writeMidi(portId: String, events: List<MidiEvent>)
}

interface Module {
    val instanceId: String
    val contract: ModuleContract

    /** Called once after the host accepts this instance, before the first [process]. */
    fun onLoad()

    /** One processing cycle. Must not allocate, block, or throw for control flow. */
    fun process(context: ProcessContext)

    /** Called before the host drops this instance, after any in-flight [process] completes. */
    fun onRemove()
}