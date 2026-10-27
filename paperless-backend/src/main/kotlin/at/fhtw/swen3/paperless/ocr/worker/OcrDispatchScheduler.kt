package at.fhtw.swen3.paperless.ocr.worker

import at.fhtw.swen3.paperless.exception.OcrPublishException
import at.fhtw.swen3.paperless.ocr.service.OcrDispatchService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "paperless.ocr", name = ["dispatch-enabled"], havingValue = "true", matchIfMissing = true)
class OcrDispatchScheduler(private val dispatch: OcrDispatchService) {
    @Scheduled(fixedDelayString = "\${paperless.ocr.dispatch-delay:1000}")
    fun dispatchPending() {
        try {
            dispatch.dispatchNext()
        } catch (exception: OcrPublishException) {
            logger.warn("[OCR] - ACTION: dispatchPending: exceptionType: {}", exception.javaClass.name)
        } catch (exception: Exception) {
            logger.error(
                "[OCR] - ACTION: dispatchPending: exceptionType: {}, stackTrace: {}",
                exception.javaClass.name,
                exception.stackTrace.contentToString()
            )
        }
    }

    private companion object {
        val logger = LoggerFactory.getLogger(OcrDispatchScheduler::class.java)
    }
}
