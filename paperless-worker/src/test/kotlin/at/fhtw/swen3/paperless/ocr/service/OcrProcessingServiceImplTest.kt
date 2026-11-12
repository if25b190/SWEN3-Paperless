package at.fhtw.swen3.paperless.ocr.service

import at.fhtw.swen3.paperless.exception.AppException
import at.fhtw.swen3.paperless.ocr.config.OcrWorkerProperties
import at.fhtw.swen3.paperless.ocr.engine.OcrEngine
import at.fhtw.swen3.paperless.ocr.engine.OcrEngineException
import at.fhtw.swen3.paperless.ocr.model.OcrRequest
import at.fhtw.swen3.paperless.ocr.model.ProcessingStatus
import at.fhtw.swen3.paperless.ocr.model.WorkerDocument
import at.fhtw.swen3.paperless.ocr.persistence.DocumentIndexPersistence
import at.fhtw.swen3.paperless.ocr.persistence.OcrDocumentPersistence
import at.fhtw.swen3.paperless.testsupport.any
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InOrder
import org.mockito.Mock
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
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

    private fun pendingDocument() = WorkerDocument(documentId, ProcessingStatus.PENDING, null, 0)

    private fun claimedDocument() = pendingDocument().copy(status = ProcessingStatus.OCR_IN_PROGRESS, version = 1)

    private fun completedDocument(status: ProcessingStatus = ProcessingStatus.COMPLETED) =
        claimedDocument().copy(status = status, ocrContent = "text")

    private fun failedDocument() = claimedDocument().copy(status = ProcessingStatus.FAILED)

    private lateinit var service: OcrProcessingServiceImpl

    @BeforeEach
    fun setUp() {
        service = OcrProcessingServiceImpl(documents, engine, index, properties)
        org.mockito.Mockito.lenient().doAnswer { invocation ->
            invocation.getArgument<WorkerDocument>(0).copy(version = 1)
        }.`when`(documents).update(any())
    }

    @Test
    fun claim_then_engine_then_complete_ok() {
        // given
        `when`(documents.findLockedById(documentId)).thenReturn(pendingDocument())
        `when`(engine.extractText(any())).thenReturn("INVOICE #9821")

        // when
        service.process(request)

        // then
        val ordered: InOrder = inOrder(documents, engine, index)
        ordered.verify(documents).findLockedById(documentId)
        ordered.verify(engine).extractText(storageDir.resolve("abc_invoice.pdf"))
        ordered.verify(index).index(documentId, "INVOICE #9821")
        ordered.verify(
            documents,
        ).update(claimedDocument().copy(ocrContent = "INVOICE #9821", status = ProcessingStatus.COMPLETED))
        verify(documents, never()).update(failedDocument())
    }

    @Test
    fun deleted_document_is_skipped_ok() {
        // given
        `when`(documents.findLockedById(documentId)).thenReturn(null)

        // when
        service.process(request)

        // then
        verifyNoInteractions(engine, index)
        verify(documents, never()).update(any())
    }

    @Test
    fun engine_failure_marks_document_failed_and_rethrows_ko() {
        // given
        `when`(documents.findLockedById(documentId)).thenReturn(pendingDocument())
        val failure = OcrEngineException("tesseract exited with code 2: no image")
        doThrow(failure).`when`(engine).extractText(any())

        // when / then
        val ordered: InOrder = inOrder(documents, engine)
        assertThatThrownBy { service.process(request) }.isInstanceOf(AppException::class.java).hasCause(failure)
        ordered.verify(engine).extractText(any())
        ordered.verify(documents).update(failedDocument())
        verify(index, never()).index(any(), any())
        verify(documents, never()).update(completedDocument())
    }

    @Test
    fun index_failure_marks_document_failed_and_rethrows_ko() {
        // given
        `when`(documents.findLockedById(documentId)).thenReturn(pendingDocument())
        `when`(engine.extractText(any())).thenReturn("text")
        val failure = IllegalStateException("elasticsearch unavailable")
        doThrow(failure).`when`(index).index(documentId, "text")

        // when / then
        val ordered: InOrder = inOrder(engine, index, documents)
        assertThatThrownBy { service.process(request) }.isInstanceOf(AppException::class.java).hasCause(failure)
        ordered.verify(index).index(documentId, "text")
        ordered.verify(documents).update(failedDocument())
        verify(documents, never()).update(completedDocument())
    }

    @Test
    fun complete_failure_propagates_for_transaction_rollback_ko() {
        // given
        `when`(documents.findLockedById(documentId)).thenReturn(pendingDocument())
        `when`(engine.extractText(any())).thenReturn("text")
        val failure = IllegalStateException("database unavailable")
        doThrow(failure).`when`(documents).update(completedDocument())

        // when / then
        assertThatThrownBy { service.process(request) }.isSameAs(failure)
        verify(documents, never()).update(failedDocument())
    }

    @Test
    fun failure_to_store_failed_status_rolls_back_attempt_ko() {
        // given
        `when`(documents.findLockedById(documentId)).thenReturn(pendingDocument())
        val failure = OcrEngineException("gs exited with code 2")
        doThrow(failure).`when`(engine).extractText(any())
        val databaseFailure = IllegalStateException("database unavailable")
        doThrow(databaseFailure).`when`(documents).update(failedDocument())

        // when / then
        assertThatThrownBy { service.process(request) }.isSameAs(databaseFailure).hasSuppressedException(failure)
    }

    @Test
    fun completed_document_is_skipped_ok() {
        // given
        `when`(documents.findLockedById(documentId)).thenReturn(completedDocument())

        // when
        service.process(request)

        // then
        verifyNoInteractions(engine, index)
        verify(documents, never()).update(any())
    }

    @Test
    fun failed_document_is_retried_ok() {
        // given
        `when`(documents.findLockedById(documentId)).thenReturn(failedDocument())
        `when`(engine.extractText(any())).thenReturn("text")

        // when
        service.process(request)

        // then
        verify(engine).extractText(storageDir.resolve("abc_invoice.pdf"))
        verify(documents).update(completedDocument())
    }

    @Test
    fun genai_final_status_is_passed_through_ok() {
        // given
        val genaiProperties = properties.copy(finalStatus = ProcessingStatus.GENAI_IN_PROGRESS)
        val genaiService = OcrProcessingServiceImpl(documents, engine, index, genaiProperties)
        `when`(documents.findLockedById(documentId)).thenReturn(pendingDocument())
        `when`(engine.extractText(any())).thenReturn("text")

        // when
        genaiService.process(request)

        // then
        verify(documents).update(completedDocument(ProcessingStatus.GENAI_IN_PROGRESS))
    }
}
