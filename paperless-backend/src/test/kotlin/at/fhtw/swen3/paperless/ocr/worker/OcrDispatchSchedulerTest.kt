package at.fhtw.swen3.paperless.ocr.worker

import at.fhtw.swen3.paperless.exception.OcrPublishException
import at.fhtw.swen3.paperless.ocr.service.OcrDispatchService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class OcrDispatchSchedulerTest {
    @Mock
    lateinit var dispatch: OcrDispatchService

    @InjectMocks
    lateinit var scheduler: OcrDispatchScheduler

    @Test
    fun publish_failure_is_absorbed_by_dispatch_loop_ok() {
        // given
        doThrow(OcrPublishException()).`when`(dispatch).dispatchNext()

        // when
        scheduler.dispatchPending()

        // then
        verify(dispatch).dispatchNext()
    }

    @Test
    fun unexpected_failure_is_absorbed_by_dispatch_loop_ok() {
        // given
        doThrow(RuntimeException("database unavailable")).`when`(dispatch).dispatchNext()

        // when
        scheduler.dispatchPending()

        // then
        verify(dispatch).dispatchNext()
    }
}
