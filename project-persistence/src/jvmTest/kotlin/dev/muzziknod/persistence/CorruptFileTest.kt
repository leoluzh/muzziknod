package dev.muzziknod.persistence

import dev.muzziknod.host.graph.RoutingGraph
import dev.muzziknod.host.lifecycle.ModuleRegistry
import dev.muzziknod.host.transport.Transport
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Invalid JSON and a truncated file each fail with a clear, typed error — never an
 * uncaught exception, never a partially-applied mutation to the live host state
 * (FR-008).
 */
class CorruptFileTest {
    private val tempFile = Files.createTempFile("corrupt-file-test", ".json")

    @AfterTest
    fun cleanup() {
        Files.deleteIfExists(tempFile)
    }

    private fun newReader(): Triple<ModuleRegistry, RoutingGraph, ProjectReader> {
        val registry = ModuleRegistry()
        val graph = RoutingGraph(registry)
        val reader = ProjectReader(registry, graph, Transport(), defaultProjectPersistenceCatalog())
        return Triple(registry, graph, reader)
    }

    @Test
    fun invalidJsonFailsWithTypedErrorAndNoMutation() {
        val (registry, _, reader) = newReader()
        Files.writeString(tempFile, "{ this is not valid json ")

        assertFailsWith<ProjectFileCorruptException> { reader.load(tempFile.toString()) }
        assertTrue(registry.all().isEmpty(), "registry must stay untouched after a failed load")
    }

    @Test
    fun truncatedFileFailsWithTypedErrorAndNoMutation() {
        val (registry, _, reader) = newReader()
        Files.writeString(tempFile, """{"schemaVersion": 1, "modules": [{"instanceId": "d""")

        assertFailsWith<ProjectFileCorruptException> { reader.load(tempFile.toString()) }
        assertTrue(registry.all().isEmpty(), "registry must stay untouched after a failed load")
    }

    @Test
    fun emptyFileFailsWithTypedError() {
        val (_, _, reader) = newReader()
        Files.writeString(tempFile, "")

        assertFailsWith<ProjectFileCorruptException> { reader.load(tempFile.toString()) }
    }
}
