package at.fhtw.swen3.paperless.ocr.persistence

import at.fhtw.swen3.paperless.ocr.config.OcrWorkerProperties
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.nio.file.Files
import java.nio.file.Path

@ExtendWith(MockitoExtension::class)
class LocalFirstDocumentFilePersistenceTest {
    @Mock lateinit var objectStorage: S3DocumentFilePersistence

    @TempDir lateinit var storageDir: Path

    @Test
    fun local_source_takes_precedence_and_is_retained_ok() {
        // given
        val local = Files.write(storageDir.resolve("invoice.pdf"), byteArrayOf(1))

        // when
        val result = persistence().withLocalFile(STORAGE_KEY, Files::readAllBytes)

        // then
        assertThat(result).isEqualTo(byteArrayOf(1))
        assertThat(local).exists()
        verifyNoInteractions(objectStorage)
    }

    @Test
    fun downloaded_source_is_removed_after_use_ok() {
        // given
        val bytes = byteArrayOf(1, 2)
        `when`(objectStorage.find(STORAGE_KEY)).thenReturn(bytes)
        lateinit var downloaded: Path

        // when
        val result = persistence().withLocalFile(STORAGE_KEY) { source ->
            downloaded = source
            Files.readAllBytes(source)
        }

        // then
        assertThat(result).isEqualTo(bytes)
        assertThat(downloaded).doesNotExist()
    }

    @Test
    fun downloaded_source_is_removed_after_callback_failure_ko() {
        // given
        `when`(objectStorage.find(STORAGE_KEY)).thenReturn(byteArrayOf(1))
        val failure = IllegalStateException("OCR failed")
        lateinit var downloaded: Path

        // when / then
        assertThatThrownBy {
            persistence().withLocalFile<Unit>(STORAGE_KEY) { source ->
                downloaded = source
                throw failure
            }
        }.isSameAs(failure)
        assertThat(downloaded).doesNotExist()
    }

    private fun persistence() = LocalFirstDocumentFilePersistence(
        OcrWorkerProperties(storageDir = storageDir),
        objectStorage,
    )

    private companion object {
        const val STORAGE_KEY = "documents/invoice.pdf"
    }
}
