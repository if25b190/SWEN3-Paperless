package at.fhtw.swen3.paperless.ocr.service

import at.fhtw.swen3.paperless.ocr.config.OcrWorkerProperties
import at.fhtw.swen3.paperless.ocr.engine.OcrEngine
import at.fhtw.swen3.paperless.ocr.engine.OcrEngineException
import at.fhtw.swen3.paperless.ocr.messaging.OcrResultPublisher
import at.fhtw.swen3.paperless.ocr.model.OcrRequest
import at.fhtw.swen3.paperless.ocr.model.OcrResult
import at.fhtw.swen3.paperless.ocr.model.OcrResultStatus
import at.fhtw.swen3.paperless.ocr.persistence.DocumentFilePersistence
import at.fhtw.swen3.paperless.testsupport.any
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class OcrProcessingServiceImplTest {
    @Mock lateinit var publisher: OcrResultPublisher

    @Mock lateinit var engine: OcrEngine

    @Mock lateinit var files: DocumentFilePersistence

    @TempDir lateinit var storageDir: Path
    private val request = OcrRequest(
        UUID.randomUUID(),
        UUID.randomUUID(),
        "documents/invoice.pdf",
        "application/pdf",
        UUID.randomUUID(),
    )
    private lateinit var service: OcrProcessingServiceImpl

    @BeforeEach
    fun setUp() {
        service = OcrProcessingServiceImpl(publisher, engine, files, OcrWorkerProperties(storageDir = storageDir))
    }

    @Test
    fun started_then_ocr_then_success_are_published_ok() {
        // given
        `when`(engine.extractText(any())).thenReturn("INVOICE #9821")
        // when
        service.process(request)
        // then
        val order = inOrder(publisher, engine)
        order.verify(publisher).publish(result(OcrResultStatus.STARTED))
        order.verify(engine).extractText(storageDir.resolve("invoice.pdf"))
        order.verify(publisher).publish(result(OcrResultStatus.SUCCEEDED, "INVOICE #9821"))
    }

    @Test
    fun ocr_failure_is_reported_for_backend_retry_ok() {
        // given
        `when`(engine.extractText(any())).thenThrow(OcrEngineException("private tool output"))
        // when
        service.process(request)
        // then
        verify(publisher).publish(result(OcrResultStatus.FAILED))
        verify(publisher, never()).publish(result(OcrResultStatus.SUCCEEDED))
    }

    @Test
    fun started_publish_failure_prevents_work_ko() {
        // given
        val failure = IllegalStateException("broker unavailable")
        doThrow(failure).`when`(publisher).publish(result(OcrResultStatus.STARTED))
        // when / then
        assertThatThrownBy { service.process(request) }.isSameAs(failure)
        verifyNoInteractions(engine, files)
    }

    @Test
    fun success_publish_failure_propagates_without_false_failure_result_ko() {
        // given
        `when`(engine.extractText(any())).thenReturn("text")
        val failure = IllegalStateException("broker unavailable")
        doNothing().`when`(publisher).publish(result(OcrResultStatus.STARTED))
        doThrow(failure).`when`(publisher).publish(result(OcrResultStatus.SUCCEEDED, "text"))
        // when / then
        assertThatThrownBy { service.process(request) }.isSameAs(failure)
        verify(publisher, never()).publish(result(OcrResultStatus.FAILED))
    }

    @Test
    fun failure_result_publish_failure_propagates_for_redelivery_ko() {
        // given
        `when`(engine.extractText(any())).thenThrow(OcrEngineException("tool failure"))
        val failure = IllegalStateException("broker unavailable")
        doNothing().`when`(publisher).publish(result(OcrResultStatus.STARTED))
        doThrow(failure).`when`(publisher).publish(result(OcrResultStatus.FAILED))
        // when / then
        assertThatThrownBy { service.process(request) }.isSameAs(failure)
    }

    @Test
    fun local_source_does_not_require_object_storage_ok() {
        // given
        Files.write(storageDir.resolve("invoice.pdf"), byteArrayOf(1))
        `when`(engine.extractText(any())).thenReturn("text")
        // when
        service.process(request)
        // then
        verifyNoInteractions(files)
        assertThat(storageDir.resolve("invoice.pdf")).exists()
    }

    @Test
    fun downloaded_source_is_removed_after_success_ok() {
        // given
        `when`(files.find(request.storageKey)).thenReturn(byteArrayOf(1, 2))
        `when`(engine.extractText(any())).thenReturn("text")
        // when
        service.process(request)
        // then
        val source = ArgumentCaptor.forClass(Path::class.java)
        verify(engine).extractText(source.capture() ?: storageDir)
        assertThat(source.value).doesNotExist()
        verify(publisher).publish(result(OcrResultStatus.SUCCEEDED, "text"))
    }

    @Test
    fun downloaded_source_is_removed_after_ocr_failure_ok() {
        // given
        `when`(files.find(request.storageKey)).thenReturn(byteArrayOf(1, 2))
        `when`(engine.extractText(any())).thenThrow(OcrEngineException("tool failure"))
        // when
        service.process(request)
        // then
        val source = ArgumentCaptor.forClass(Path::class.java)
        verify(engine).extractText(source.capture() ?: storageDir)
        assertThat(source.value).doesNotExist()
        verify(publisher).publish(result(OcrResultStatus.FAILED))
    }

    @Test
    fun storage_failure_is_reported_without_running_ocr_ok() {
        // given
        `when`(files.find(request.storageKey)).thenThrow(IllegalStateException("storage unavailable"))
        // when
        service.process(request)
        // then
        verifyNoInteractions(engine)
        verify(publisher).publish(result(OcrResultStatus.FAILED))
    }

    private fun result(status: OcrResultStatus, text: String? = null) =
        OcrResult(request.jobId, request.documentId, request.attemptId, status, text)
}
