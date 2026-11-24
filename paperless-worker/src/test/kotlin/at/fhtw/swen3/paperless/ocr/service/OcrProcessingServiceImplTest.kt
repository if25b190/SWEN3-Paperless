package at.fhtw.swen3.paperless.ocr.service

import at.fhtw.swen3.paperless.ocr.engine.OcrEngine
import at.fhtw.swen3.paperless.ocr.engine.OcrEngineException
import at.fhtw.swen3.paperless.ocr.messaging.OcrResultPublisher
import at.fhtw.swen3.paperless.ocr.model.OcrRequest
import at.fhtw.swen3.paperless.ocr.model.OcrResult
import at.fhtw.swen3.paperless.ocr.model.OcrResultStatus
import at.fhtw.swen3.paperless.ocr.persistence.DocumentFilePersistence
import at.fhtw.swen3.paperless.testsupport.any
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.nio.file.Path
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class OcrProcessingServiceImplTest {
    @Mock lateinit var publisher: OcrResultPublisher

    @Mock lateinit var engine: OcrEngine

    @Mock lateinit var files: DocumentFilePersistence

    @InjectMocks lateinit var service: OcrProcessingServiceImpl

    private val source = Path.of("invoice.pdf")
    private val request = OcrRequest(
        UUID.randomUUID(),
        UUID.randomUUID(),
        "documents/invoice.pdf",
        "application/pdf",
        UUID.randomUUID(),
    )

    @Test
    fun started_then_ocr_then_success_are_published_ok() {
        // given
        provideSource()
        `when`(engine.extractText(source)).thenReturn("INVOICE #9821")
        // when
        service.process(request)
        // then
        val order = inOrder(publisher, engine)
        order.verify(publisher).publish(result(OcrResultStatus.STARTED))
        order.verify(engine).extractText(source)
        order.verify(publisher).publish(result(OcrResultStatus.SUCCEEDED, "INVOICE #9821"))
    }

    @Test
    fun ocr_failure_is_reported_for_backend_retry_ok() {
        // given
        provideSource()
        `when`(engine.extractText(source)).thenThrow(OcrEngineException("private tool output"))
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
        provideSource()
        `when`(engine.extractText(source)).thenReturn("text")
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
        provideSource()
        `when`(engine.extractText(source)).thenThrow(OcrEngineException("tool failure"))
        val failure = IllegalStateException("broker unavailable")
        doNothing().`when`(publisher).publish(result(OcrResultStatus.STARTED))
        doThrow(failure).`when`(publisher).publish(result(OcrResultStatus.FAILED))
        // when / then
        assertThatThrownBy { service.process(request) }.isSameAs(failure)
    }

    @Test
    fun missing_source_is_reported_without_running_ocr_ko() {
        // when
        service.process(request)
        // then
        verifyNoInteractions(engine)
        verify(publisher).publish(result(OcrResultStatus.FAILED))
    }

    @Test
    fun storage_failure_is_reported_without_running_ocr_ok() {
        // given
        `when`(files.withLocalFile(any<String>(), any<(Path) -> String>()))
            .thenThrow(IllegalStateException("storage unavailable"))
        // when
        service.process(request)
        // then
        verifyNoInteractions(engine)
        verify(publisher).publish(result(OcrResultStatus.FAILED))
    }

    private fun provideSource() {
        `when`(files.withLocalFile(any<String>(), any<(Path) -> String>())).thenAnswer { invocation ->
            invocation.getArgument<(Path) -> String>(1)(source)
        }
    }

    private fun result(status: OcrResultStatus, text: String? = null) =
        OcrResult(request.jobId, request.documentId, request.attemptId, status, text)
}
