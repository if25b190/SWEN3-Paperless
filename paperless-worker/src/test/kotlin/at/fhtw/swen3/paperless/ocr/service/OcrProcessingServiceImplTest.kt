package at.fhtw.swen3.paperless.ocr.service

import at.fhtw.swen3.paperless.ocr.config.OcrWorkerProperties
import at.fhtw.swen3.paperless.ocr.engine.OcrEngine
import at.fhtw.swen3.paperless.ocr.engine.OcrEngineException
import at.fhtw.swen3.paperless.ocr.model.OcrRequest
import at.fhtw.swen3.paperless.ocr.model.ProcessingStatus
import at.fhtw.swen3.paperless.ocr.model.WorkerDocument
import at.fhtw.swen3.paperless.ocr.persistence.DocumentIndexPersistence
import at.fhtw.swen3.paperless.ocr.persistence.OcrDocumentPersistence
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import at.fhtw.swen3.paperless.testsupport.any
import org.mockito.InOrder
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import java.nio.file.Paths
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class OcrProcessingServiceImplTest {
    @Mock
    lateinit var documents: OcrDocumentPersistence

    @Mock
    lateinit var engine: OcrEngine

    @Mock
    lateinit var index: DocumentIndexPersistence

    private val storageDir = Paths.get("/tmp/paperless-documents")
    private val properties = OcrWorkerProperties(enabled = true, storageDir = storageDir)
    private val documentId = UUID.randomUUID()
    private val request = OcrRequest(UUID.randomUUID(), documentId, "documents/abc_invoice.pdf", "application/pdf")

    private fun claimedDocument() = WorkerDocument(documentId, ProcessingStatus.OCR_IN_PROGRESS, null, 1)

    private lateinit var service: OcrProcessingServiceImpl

    @BeforeEach
    fun setUp() {
        service = OcrProcessingServiceImpl(documents, engine, index, properties)
    }

    @Test
    fun claim_then_engine_then_complete_ok() {
        // given
        `when`(documents.claim(documentId)).thenReturn(claimedDocument())
        `when`(engine.extractText(any())).thenReturn("INVOICE #9821")

        // when
        service.process(request)

        // then
        val ordered: InOrder = inOrder(documents, engine, index)
        ordered.verify(documents).claim(documentId)
        ordered.verify(engine).extractText(storageDir.resolve("abc_invoice.pdf"))
        ordered.verify(index).index(documentId, "INVOICE #9821")
        ordered.verify(documents).complete(documentId, "INVOICE #9821", ProcessingStatus.COMPLETED)
        verify(documents, never()).fail(documentId)
    }

    @Test
    fun duplicate_message_is_skipped_ok() {
        // given
        `when`(documents.claim(documentId)).thenReturn(null)

        // when
        service.process(request)

        // then
        verifyNoInteractions(engine)
        verifyNoInteractions(index)
        verify(documents, never()).complete(any(), any(), any())
        verify(documents, never()).fail(documentId)
    }

    @Test
    fun engine_failure_marks_document_failed_and_rethrows_ko() {
        // given
        `when`(documents.claim(documentId)).thenReturn(claimedDocument())
        val failure = OcrEngineException("tesseract exited with code 2: no image")
        doThrow(failure).`when`(engine).extractText(any())

        // when / then
        val ordered: InOrder = inOrder(documents, engine)
        assertThatThrownBy { service.process(request) }.isInstanceOf(OcrEngineException::class.java).isSameAs(failure)
        ordered.verify(engine).extractText(any())
        ordered.verify(documents).fail(documentId)
        verify(index, never()).index(any(), any())
        verify(documents, never()).complete(any(), any(), any())
    }

    @Test
    fun index_failure_marks_document_failed_and_rethrows_ko() {
        // given
        `when`(documents.claim(documentId)).thenReturn(claimedDocument())
        `when`(engine.extractText(any())).thenReturn("text")
        val failure = IllegalStateException("elasticsearch unavailable")
        doThrow(failure).`when`(index).index(documentId, "text")

        // when / then
        val ordered: InOrder = inOrder(engine, index, documents)
        assertThatThrownBy { service.process(request) }.isInstanceOf(IllegalStateException::class.java).isSameAs(failure)
        ordered.verify(index).index(documentId, "text")
        ordered.verify(documents).fail(documentId)
        verify(documents, never()).complete(any(), any(), any())
    }

    @Test
    fun complete_failure_marks_document_failed_and_rethrows_ko() {
        // given
        `when`(documents.claim(documentId)).thenReturn(claimedDocument())
        `when`(engine.extractText(any())).thenReturn("text")
        val failure = IllegalStateException("database unavailable")
        doThrow(failure).`when`(documents).complete(documentId, "text", ProcessingStatus.COMPLETED)

        // when / then
        assertThatThrownBy { service.process(request) }.isInstanceOf(IllegalStateException::class.java).isSameAs(failure)
        verify(documents).fail(documentId)
    }

    @Test
    fun mark_failed_swallows_persistence_error_ko() {
        // given
        `when`(documents.claim(documentId)).thenReturn(claimedDocument())
        val failure = OcrEngineException("gs exited with code 2")
        doThrow(failure).`when`(engine).extractText(any())
        doThrow(IllegalStateException("database unavailable")).`when`(documents).fail(documentId)

        // when / then
        assertThatThrownBy { service.process(request) }.isInstanceOf(OcrEngineException::class.java).isSameAs(failure)
    }

    @Test
    fun genai_final_status_is_passed_through_ok() {
        // given
        val genaiProperties = properties.copy(finalStatus = ProcessingStatus.GENAI_IN_PROGRESS)
        val genaiService = OcrProcessingServiceImpl(documents, engine, index, genaiProperties)
        `when`(documents.claim(documentId)).thenReturn(claimedDocument())
        `when`(engine.extractText(any())).thenReturn("text")

        // when
        genaiService.process(request)

        // then
        verify(documents).complete(documentId, "text", ProcessingStatus.GENAI_IN_PROGRESS)
    }
}
