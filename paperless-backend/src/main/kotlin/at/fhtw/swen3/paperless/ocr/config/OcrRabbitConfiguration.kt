package at.fhtw.swen3.paperless.ocr.config

import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Declarables
import org.springframework.amqp.core.DirectExchange
import org.springframework.amqp.core.QueueBuilder
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.json.JsonMapper

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OcrMessagingProperties::class)
class OcrRabbitConfiguration {
    @Bean
    fun ocrTopology(properties: OcrMessagingProperties): Declarables {
        val exchange = DirectExchange(properties.exchange)
        val deadLetterExchange = DirectExchange(properties.deadLetterExchange)
        val queue = QueueBuilder.durable(properties.queue)
            .deadLetterExchange(properties.deadLetterExchange)
            .deadLetterRoutingKey(properties.routingKey)
            .build()
        val deadLetterQueue = QueueBuilder.durable(properties.deadLetterQueue).build()
        return Declarables(
            exchange,
            deadLetterExchange,
            queue,
            deadLetterQueue,
            BindingBuilder.bind(queue).to(exchange).with(properties.routingKey),
            BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(properties.routingKey)
        )
    }

    @Bean
    fun ocrMessageConverter(jsonMapper: JsonMapper): JacksonJsonMessageConverter =
        JacksonJsonMessageConverter(jsonMapper, "at.fhtw.swen3.paperless.ocr.model")
}
