package at.fhtw.swen3.paperless.ocr.config

import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Declarables
import org.springframework.amqp.core.DirectExchange
import org.springframework.amqp.core.QueueBuilder
import org.springframework.amqp.support.converter.JacksonJavaTypeMapper
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.json.JsonMapper

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OcrMessagingProperties::class, OcrProcessingProperties::class)
class OcrRabbitConfiguration {
    @Bean
    fun ocrTopology(properties: OcrMessagingProperties): Declarables {
        val exchange = DirectExchange(properties.exchange)
        val deadLetterExchange = DirectExchange(properties.deadLetterExchange)
        val queue = QueueBuilder.durable(properties.queue)
            .deadLetterExchange(properties.deadLetterExchange)
            .deadLetterRoutingKey(properties.routingKey)
            .build()
        val deletionQueue = QueueBuilder.durable(properties.deletionQueue)
            .deadLetterExchange(properties.deadLetterExchange)
            .deadLetterRoutingKey(properties.deletionRoutingKey)
            .build()
        val resultQueue = QueueBuilder.durable(properties.resultQueue)
            .deadLetterExchange(properties.deadLetterExchange)
            .deadLetterRoutingKey(properties.resultRoutingKey)
            .build()
        val deadLetterQueue = QueueBuilder.durable(properties.deadLetterQueue).build()
        return Declarables(
            exchange,
            deadLetterExchange,
            queue,
            deletionQueue,
            resultQueue,
            deadLetterQueue,
            BindingBuilder.bind(queue).to(exchange).with(properties.routingKey),
            BindingBuilder.bind(deletionQueue).to(exchange).with(properties.deletionRoutingKey),
            BindingBuilder.bind(resultQueue).to(exchange).with(properties.resultRoutingKey),
            BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(properties.resultRoutingKey),
            BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(properties.routingKey),
            BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(properties.deletionRoutingKey)
        )
    }

    @Bean
    fun ocrMessageConverter(jsonMapper: JsonMapper): JacksonJsonMessageConverter =
        JacksonJsonMessageConverter(jsonMapper, "at.fhtw.swen3.paperless.ocr.model").apply {
            typePrecedence = JacksonJavaTypeMapper.TypePrecedence.INFERRED
        }
}
