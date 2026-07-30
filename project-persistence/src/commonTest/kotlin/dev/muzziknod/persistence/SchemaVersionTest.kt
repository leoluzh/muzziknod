package dev.muzziknod.persistence

import dev.muzziknod.host.graph.RoutingGraph
import dev.muzziknod.host.lifecycle.ModuleRegistry
import dev.muzziknod.host.transport.Transport
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** `ProjectReader` rejects a newer-than-supported `schemaVersion` before decoding the rest (FR-011). */
class SchemaVersionTest {

    @Test
    fun rejectsNewerSchemaVersionWithoutDecodingRest() {
        val registry = ModuleRegistry()
        val reader = ProjectReader(registry, RoutingGraph(registry), Transport(), ProjectPersistenceCatalog(emptyMap()))
        val futureVersionContent = """
            {"schemaVersion": 999, "this is not valid ProjectSnapshot shape": true}
        """.trimIndent()

        val error = assertFailsWith<UnsupportedProjectFileVersionException> {
            reader.loadFromContent(futureVersionContent)
        }
        assert(error.fileVersion == 999)
    }
}
