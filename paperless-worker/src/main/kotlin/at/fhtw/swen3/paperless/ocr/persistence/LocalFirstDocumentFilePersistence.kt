package at.fhtw.swen3.paperless.ocr.persistence

import at.fhtw.swen3.paperless.ocr.config.OcrWorkerProperties
import org.springframework.stereotype.Repository
import java.nio.file.Files
import java.nio.file.Path

@Repository
class LocalFirstDocumentFilePersistence(
    private val properties: OcrWorkerProperties,
    private val objectStorage: S3DocumentFilePersistence?,
) : DocumentFilePersistence {
    override fun <T : Any> withLocalFile(storageKey: String, action: (Path) -> T): T? {
        val local = properties.storageDir.resolve(storageKey.substringAfterLast('/'))
        if (Files.isRegularFile(local)) return action(local)

        val bytes = objectStorage?.find(storageKey) ?: return null
        val downloaded = Files.createTempFile("paperless-ocr-source-", ".pdf")

        return try {
            Files.write(downloaded, bytes)
            action(downloaded)
        } finally {
            runCatching { Files.deleteIfExists(downloaded) }
        }
    }
}
