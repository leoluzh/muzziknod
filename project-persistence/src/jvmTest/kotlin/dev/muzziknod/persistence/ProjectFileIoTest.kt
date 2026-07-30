package dev.muzziknod.persistence

import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Saving twice to the same path overwrites; "save as" to a new path leaves the
 * original untouched (FR-012; US1 AC2-3).
 */
class ProjectFileIoTest {
    private val pathA = Files.createTempFile("project-file-io-test-a", ".json")
    private val pathB = Files.createTempFile("project-file-io-test-b", ".json")

    @AfterTest
    fun cleanup() {
        Files.deleteIfExists(pathA)
        Files.deleteIfExists(pathB)
    }

    @Test
    fun writingTwiceToSamePathOverwrites() {
        writeProjectFile(pathA.toString(), "first")
        writeProjectFile(pathA.toString(), "second")

        assertEquals("second", Files.readString(pathA))
    }

    @Test
    fun saveAsToNewPathLeavesOriginalUntouched() {
        writeProjectFile(pathA.toString(), "original")
        writeProjectFile(pathB.toString(), "different content")

        assertEquals("original", Files.readString(pathA))
        assertEquals("different content", Files.readString(pathB))
        assertNotEquals(Files.readString(pathA), Files.readString(pathB))
    }
}
