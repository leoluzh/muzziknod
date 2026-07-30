package dev.muzziknod.persistence

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

actual fun readProjectFile(path: String): String =
    Files.readString(Path.of(path), StandardCharsets.UTF_8)

actual fun writeProjectFile(path: String, content: String) {
    Files.writeString(Path.of(path), content, StandardCharsets.UTF_8)
}

actual fun readFileBytes(path: String): ByteArray = Files.readAllBytes(Path.of(path))
