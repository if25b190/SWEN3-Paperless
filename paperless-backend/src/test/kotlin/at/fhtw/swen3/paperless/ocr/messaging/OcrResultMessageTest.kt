package at.fhtw.swen3.paperless.ocr.messaging

import at.fhtw.swen3.paperless.ocr.config.OcrRabbitConfiguration
import at.fhtw.swen3.paperless.ocr.model.OcrResult
import at.fhtw.swen3.paperless.ocr.model.OcrResultStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageProperties
import tools.jackson.databind.json.JsonMapper

class OcrResultMessageTest {
    @Test
    fun python_result_deserializes_without_java_type_headers_ok() {
        // given
        val body = """{
          "jobId":"00000000-0000-0000-0000-000000000001",
          "documentId":"00000000-0000-0000-0000-000000000002",
          "storageKey":"documents/test.pdf", "contentType":"application/pdf",
          "status":"COMPLETED", "text":"Invoice amount 12345", "extractedAt":"2026-10-26T12:00:00+00:00"
        }""".toByteArray()
        val properties = MessageProperties().apply {
            contentType = "application/json"
            inferredArgumentType = OcrResult::class.java
        }
        val mapper = JsonMapper.builder().findAndAddModules().build()

        // when
        val result = OcrRabbitConfiguration().ocrMessageConverter(mapper)
            .fromMessage(Message(body, properties)) as OcrResult

        // then
        assertThat(result.status).isEqualTo(OcrResultStatus.COMPLETED)
        assertThat(result.text).isEqualTo("Invoice amount 12345")
        assertThat(result.documentId.toString()).isEqualTo("00000000-0000-0000-0000-000000000002")
    }
}
