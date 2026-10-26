package at.fhtw.swen3.paperless.document.persistence

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import java.nio.file.Files
import java.nio.file.Path

@Repository
@ConditionalOnProperty(prefix = "paperless.storage", name = ["type"], havingValue = "local")
class FileSystemDocumentStorage : DocumentStoragePersistence {
    private val directory = Path.of(System.getProperty("java.io.tmpdir"), "paperless-documents")

    override fun store(storageKey: String, content: ByteArray, contentType: String) {
        Files.createDirectories(directory)
        Files.write(fileFor(storageKey), content)
    }

    override fun find(storageKey: String): ByteArray? {
        val file = fileFor(storageKey)

        return if (Files.isRegularFile(file) && Files.isReadable(file)) Files.readAllBytes(file) else null
    }

    override fun delete(storageKey: String) {
        Files.deleteIfExists(fileFor(storageKey))
    }

    private fun fileFor(storageKey: String): Path = directory.resolve(storageKey.substringAfterLast('/'))
}
