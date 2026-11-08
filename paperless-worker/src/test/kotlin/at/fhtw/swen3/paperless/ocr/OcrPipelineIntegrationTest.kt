package at.fhtw.swen3.paperless.ocr

import at.fhtw.swen3.paperless.ocr.config.OcrWorkerProperties
import at.fhtw.swen3.paperless.ocr.engine.OcrEngineException
import at.fhtw.swen3.paperless.ocr.engine.TesseractOcrEngine
import at.fhtw.swen3.paperless.ocr.engine.TesseractOcrEngineTest
import at.fhtw.swen3.paperless.ocr.entity.WorkerDocumentEntity
import at.fhtw.swen3.paperless.ocr.model.OcrRequest
import at.fhtw.swen3.paperless.ocr.model.ProcessingStatus
import at.fhtw.swen3.paperless.ocr.model.WorkerDocument
import at.fhtw.swen3.paperless.ocr.persistence.JpaOcrDocumentPersistence
import at.fhtw.swen3.paperless.ocr.persistence.mapper.WorkerDocumentEntityMapper
import at.fhtw.swen3.paperless.ocr.persistence.mapper.WorkerDocumentEntityMapperImpl
import at.fhtw.swen3.paperless.ocr.service.OcrProcessingServiceImpl
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

// Runs the real service, the real Ghostscript/Tesseract engine, and the real
// persistence transitions against one scanned-PDF fixture. Skipped when the
// OCR toolchain is not installed.
@DataJpaTest
@Import(JpaOcrDocumentPersistence::class, WorkerDocumentEntityMapperImpl::class)
@EnabledIf("at.fhtw.swen3.paperless.ocr.engine.TesseractOcrEngineTest#pipelineAvailable")
class OcrPipelineIntegrationTest(
    @Autowired private val persistence: JpaOcrDocumentPersistence,
    @Autowired private val entityManager: EntityManager,
    @Autowired private val mapper: WorkerDocumentEntityMapper
) {
    private var storageDir: Path = Files.createTempDirectory("paperless-pipeline-test")
    private lateinit var service: OcrProcessingServiceImpl
    private var documentId: UUID = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        storageDir = Files.createTempDirectory("paperless-pipeline-test")
        Files.copy(TesseractOcrEngineTest.fixture("sample-invoice.pdf"), storageDir.resolve("sample-invoice.pdf"))
        val properties = OcrWorkerProperties(enabled = true, storageDir = storageDir)
        service = OcrProcessingServiceImpl(persistence, TesseractOcrEngine(properties), properties)
        documentId = UUID.randomUUID()
        saveDocument(ProcessingStatus.PENDING)
    }

    @Test
    fun scanned_pdf_transitions_to_completed_with_extracted_text_ok() {
        // given
        val request = OcrRequest(UUID.randomUUID(), documentId, "documents/sample-invoice.pdf", "application/pdf")

        // when
        service.process(request)

        // then
        val loaded = loadDocument()
        val content = loaded.ocrContent
        assertThat(loaded.status).isEqualTo(ProcessingStatus.COMPLETED)
        assertThat(content).contains("INVOICE #9821")
        assertThat(content).contains("142.50")
        // Both pages are present and in order.
        assertThat(content!!.indexOf("9821")).isLessThan(content!!.indexOf("73459"))
    }

    @Test
    fun duplicate_delivery_does_not_reprocess_ok() {
        // given
        val request = OcrRequest(UUID.randomUUID(), documentId, "documents/sample-invoice.pdf", "application/pdf")
        service.process(request)
        val first = loadDocument()

        // when
        service.process(request)

        // then
        val loaded = loadDocument()
        assertThat(loaded.status).isEqualTo(ProcessingStatus.COMPLETED)
        assertThat(loaded.ocrContent).isEqualTo(first.ocrContent)
        assertThat(loaded.version).isEqualTo(first.version)
    }

    @Test
    fun missing_source_file_transitions_to_failed_ko() {
        // given
        val request = OcrRequest(UUID.randomUUID(), documentId, "documents/absent.pdf", "application/pdf")

        // when / then
        assertThatThrownBy { service.process(request) }.isInstanceOf(OcrEngineException::class.java)
        val loaded = loadDocument()
        assertThat(loaded.status).isEqualTo(ProcessingStatus.FAILED)
        assertThat(loaded.ocrContent).isNull()
    }

    private fun saveDocument(status: ProcessingStatus) {
        entityManager.persist(mapper.toEntity(WorkerDocument(documentId, status, null, 0)))
        entityManager.flush()
    }

    private fun loadDocument(): WorkerDocumentEntity {
        val entity = requireNotNull(entityManager.find(WorkerDocumentEntity::class.java, documentId))
        entityManager.refresh(entity)
        return entity
    }
}
