package at.fhtw.swen3.paperless.ocr

import at.fhtw.swen3.paperless.ocr.config.OcrWorkerProperties
import at.fhtw.swen3.paperless.ocr.engine.TesseractOcrEngine
import at.fhtw.swen3.paperless.ocr.engine.TesseractOcrEngineTest
import at.fhtw.swen3.paperless.ocr.messaging.OcrResultPublisher
import at.fhtw.swen3.paperless.ocr.model.OcrRequest
import at.fhtw.swen3.paperless.ocr.model.OcrResult
import at.fhtw.swen3.paperless.ocr.model.OcrResultStatus
import at.fhtw.swen3.paperless.ocr.persistence.LocalFirstDocumentFilePersistence
import at.fhtw.swen3.paperless.ocr.service.OcrProcessingServiceImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

@EnabledIf("at.fhtw.swen3.paperless.ocr.engine.TesseractOcrEngineTest#pipelineAvailable")
class OcrPipelineIntegrationTest {
    @TempDir lateinit var storageDir: Path
    private val results = mutableListOf<OcrResult>()
    private lateinit var service: OcrProcessingServiceImpl

    @BeforeEach
    fun setUp() {
        Files.copy(TesseractOcrEngineTest.fixture("sample-invoice.pdf"), storageDir.resolve("sample-invoice.pdf"))
        val properties = OcrWorkerProperties(storageDir = storageDir)
        val publisher = object : OcrResultPublisher {
            override fun publish(result: OcrResult) {
                results += result
            }
        }
        val files = LocalFirstDocumentFilePersistence(properties, objectStorage = null)
        service = OcrProcessingServiceImpl(publisher, TesseractOcrEngine(properties), files)
    }

    @Test
    fun scanned_pdf_publishes_both_pages_in_order_ok() {
        // when
        service.process(request("sample-invoice.pdf"))
        // then
        assertThat(results.map { it.status }).containsExactly(OcrResultStatus.STARTED, OcrResultStatus.SUCCEEDED)
        val content = requireNotNull(results.last().ocrContent)
        assertThat(content).contains("INVOICE #9821", "142.50", "73459")
        assertThat(content.indexOf("9821")).isLessThan(content.indexOf("73459"))
    }

    @Test
    fun missing_file_publishes_failure_ok() {
        // when
        service.process(request("absent.pdf"))
        // then
        assertThat(results.map { it.status }).containsExactly(OcrResultStatus.STARTED, OcrResultStatus.FAILED)
    }

    private fun request(filename: String) =
        OcrRequest(UUID.randomUUID(), UUID.randomUUID(), "documents/$filename", "application/pdf", UUID.randomUUID())
}
