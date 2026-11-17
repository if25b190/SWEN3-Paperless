package at.fhtw.swen3.paperless.ocr.service

import at.fhtw.swen3.paperless.exception.AppException
import at.fhtw.swen3.paperless.ocr.config.OcrWorkerProperties
import at.fhtw.swen3.paperless.ocr.engine.OcrEngine
import at.fhtw.swen3.paperless.ocr.engine.OcrEngineException
import at.fhtw.swen3.paperless.ocr.model.DocumentDeletion
import at.fhtw.swen3.paperless.ocr.model.OcrRequest
import at.fhtw.swen3.paperless.ocr.model.ProcessingStatus
import at.fhtw.swen3.paperless.ocr.model.WorkerDocument
import at.fhtw.swen3.paperless.ocr.persistence.DocumentFilePersistence
import at.fhtw.swen3.paperless.ocr.persistence.DocumentIndexPersistence
import at.fhtw.swen3.paperless.ocr.persistence.OcrDocumentPersistence
import at.fhtw.swen3.paperless.ocr.persistence.exception.DocumentFilePersistenceException
import at.fhtw.swen3.paperless.testsupport.any
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.mockito.InOrder
import org.mockito.Mock
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.nio.file.Files
import java.nio.file.Path
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

    @Mock
    lateinit var files: DocumentFilePersistence

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
        service = OcrProcessingServiceImpl(documents, engine, index, properties, files)
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
    fun deletion_removes_index_entry_ok() {
        // when
        service.processDeletion(DocumentDeletion(documentId))

        // then
        verify(index).delete(documentId)
    }

    @Test
    fun deletion_during_ocr_removes_index_entry_and_skips_completion_ok() {
        // given
        `when`(documents.findLockedById(documentId)).thenReturn(pendingDocument(), null)
        `when`(engine.extractText(any())).thenReturn("text")

        // when
        service.process(request)

        // then
        verify(index).index(documentId, "text")
        verify(index).delete(documentId)
        verify(documents, never()).update(completedDocument())
        verify(documents, never()).update(failedDocument())
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
        val genaiService = OcrProcessingServiceImpl(documents, engine, index, genaiProperties, files)
        `when`(documents.findLockedById(documentId)).thenReturn(pendingDocument())
        `when`(engine.extractText(any())).thenReturn("text")

        // when
        genaiService.process(request)

        // then
        verify(documents).update(completedDocument(ProcessingStatus.GENAI_IN_PROGRESS))
    }

    @Test
    fun existing_local_file_is_used_without_storage_lookup_ok(@TempDir tempDir: Path) {
        // given
        val localService =
            OcrProcessingServiceImpl(documents, engine, index, properties.copy(storageDir = tempDir), files)
        val localFile = Files.write(tempDir.resolve("abc_invoice.pdf"), byteArrayOf(9, 9, 9))
        `when`(documents.findLockedById(documentId)).thenReturn(pendingDocument())
        `when`(engine.extractText(any())).thenReturn("local text")

        // when
        localService.process(request)

        // then
        verify(engine).extractText(localFile)
        verify(files, never()).find(any())
        verify(index).index(documentId, "local text")
        verify(documents).update(claimedDocument().copy(ocrContent = "local text", status = ProcessingStatus.COMPLETED))
    }

    @Test
    fun missing_local_file_is_downloaded_from_storage_and_cleaned_up_ok(@TempDir tempDir: Path) {
        // given
        val s3Service = OcrProcessingServiceImpl(documents, engine, index, properties.copy(storageDir = tempDir), files)
        val payload = byteArrayOf(1, 2, 3)
        val downloaded = mutableListOf<Path>()
        `when`(documents.findLockedById(documentId)).thenReturn(pendingDocument())
        `when`(files.find(request.storageKey)).thenReturn(payload)
        `when`(engine.extractText(any())).thenAnswer { invocation ->
            val source = invocation.getArgument<Path>(0)
            assertThat(Files.readAllBytes(source)).isEqualTo(payload)
            downloaded.add(source)
            "s3 text"
        }

        // when
        s3Service.process(request)

        // then
        assertThat(downloaded).hasSize(1)
        assertThat(downloaded[0]).isNotEqualTo(tempDir.resolve("abc_invoice.pdf"))
        assertThat(Files.exists(downloaded[0])).isFalse()
        verify(index).index(documentId, "s3 text")
        verify(documents).update(claimedDocument().copy(ocrContent = "s3 text", status = ProcessingStatus.COMPLETED))
    }

    @Test
    fun storage_failure_marks_document_failed_and_rethrows_ko(@TempDir tempDir: Path) {
        // given
        val s3Service = OcrProcessingServiceImpl(documents, engine, index, properties.copy(storageDir = tempDir), files)
        val failure = DocumentFilePersistenceException(RuntimeException("rustfs unreachable"))
        `when`(documents.findLockedById(documentId)).thenReturn(pendingDocument())
        `when`(files.find(request.storageKey)).thenThrow(failure)

        // when / then
        assertThatThrownBy { s3Service.process(request) }.isInstanceOf(AppException::class.java).hasCause(failure)
        verifyNoInteractions(engine, index)
        verify(documents).update(failedDocument())
        verify(documents, never()).update(completedDocument())
    }

    @Test
    fun storage_miss_falls_back_to_local_path_ko(@TempDir tempDir: Path) {
        // given
        val s3Service = OcrProcessingServiceImpl(documents, engine, index, properties.copy(storageDir = tempDir), files)
        val failure = OcrEngineException("Source file not found: ${tempDir.resolve("abc_invoice.pdf")}")
        `when`(documents.findLockedById(documentId)).thenReturn(pendingDocument())
        `when`(files.find(request.storageKey)).thenReturn(null)
        doThrow(failure).`when`(engine).extractText(any())

        // when / then
        assertThatThrownBy { s3Service.process(request) }.isInstanceOf(AppException::class.java).hasCause(failure)
        verify(engine).extractText(tempDir.resolve("abc_invoice.pdf"))
        verify(documents).update(failedDocument())
    }
}
