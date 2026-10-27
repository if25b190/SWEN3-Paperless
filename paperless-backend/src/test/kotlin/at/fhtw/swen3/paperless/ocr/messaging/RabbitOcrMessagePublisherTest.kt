package at.fhtw.swen3.paperless.ocr.messaging

import at.fhtw.swen3.paperless.exception.OcrPublishException
import at.fhtw.swen3.paperless.ocr.config.OcrMessagingProperties
import at.fhtw.swen3.paperless.ocr.model.OcrRequest
import at.fhtw.swen3.paperless.testsupport.any
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.eq
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.amqp.AmqpConnectException
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageDeliveryMode
import org.springframework.amqp.core.MessagePostProcessor
import org.springframework.amqp.core.MessageProperties
import org.springframework.amqp.core.ReturnedMessage
import org.springframework.amqp.rabbit.connection.CorrelationData
import org.springframework.amqp.rabbit.core.RabbitTemplate
import java.net.ConnectException
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeoutException

@ExtendWith(MockitoExtension::class)
class RabbitOcrMessagePublisherTest {
    @Mock
    lateinit var template: RabbitTemplate

    private val properties = OcrMessagingProperties(confirmTimeout = Duration.ofMillis(10))
    private val request = OcrRequest(UUID.randomUUID(), UUID.randomUUID(), "documents/test.pdf", "application/pdf")

    @AfterEach
    fun clear_interrupt() {
        Thread.interrupted()
    }

    @Test
    fun confirmed_message_has_stable_id_and_persistent_delivery_ok() {
        // given
        stubSend { correlation -> correlation.future.complete(CorrelationData.Confirm(true, null)) }

        // when
        publisher().publish(request)
    }

    @Test
    fun nack_is_a_messaging_exception_ko() {
        // given
        stubSend { correlation -> correlation.future.complete(CorrelationData.Confirm(false, "rejected")) }

        // then
        assertThatThrownBy { publisher().publish(request) }.isInstanceOf(OcrPublishException::class.java)
    }

    @Test
    fun unroutable_message_is_a_failure_even_when_confirmed_ko() {
        // given
        stubSend { correlation ->
            correlation.setReturned(ReturnedMessage(Message(byteArrayOf(), MessageProperties()), 312, "NO_ROUTE", properties.exchange, properties.routingKey))
            correlation.future.complete(CorrelationData.Confirm(true, null))
        }

        // then
        assertThatThrownBy { publisher().publish(request) }.isInstanceOf(OcrPublishException::class.java)
    }

    @Test
    fun confirmation_timeout_preserves_cause_ko() {
        // then
        assertThatThrownBy { publisher().publish(request) }.isInstanceOf(OcrPublishException::class.java)
            .hasCauseInstanceOf(TimeoutException::class.java)
    }

    @Test
    fun broker_connection_failure_preserves_cause_ko() {
        // given
        val failure = AmqpConnectException(ConnectException("private broker detail"))
        doThrow(failure).`when`(template).convertAndSend(
            eq(properties.exchange), eq(properties.routingKey), eq(request), any(MessagePostProcessor::class.java), any(CorrelationData::class.java)
        )

        // then
        assertThatThrownBy { publisher().publish(request) }.isInstanceOf(OcrPublishException::class.java).hasCause(failure)
    }

    @Test
    fun interrupt_flag_is_preserved_ko() {
        // given
        Thread.currentThread().interrupt()

        // then
        assertThatThrownBy { publisher().publish(request) }.isInstanceOf(OcrPublishException::class.java)
            .hasCauseInstanceOf(InterruptedException::class.java)
        assertThat(Thread.currentThread().isInterrupted).isTrue()
    }

    private fun publisher() = RabbitOcrMessagePublisher(template, properties)

    private fun stubSend(confirm: (CorrelationData) -> Unit) {
        doAnswer { invocation ->
            val postProcessor = invocation.getArgument<MessagePostProcessor>(3)
            val message = postProcessor.postProcessMessage(Message(byteArrayOf(), MessageProperties()))
            assertThat(message.messageProperties.messageId).isEqualTo(request.jobId.toString())
            assertThat(message.messageProperties.deliveryMode).isEqualTo(MessageDeliveryMode.PERSISTENT)
            confirm(invocation.getArgument(4))
            null
        }.`when`(template).convertAndSend(
            eq(properties.exchange), eq(properties.routingKey), eq(request), any(MessagePostProcessor::class.java), any(CorrelationData::class.java)
        )
    }
}
