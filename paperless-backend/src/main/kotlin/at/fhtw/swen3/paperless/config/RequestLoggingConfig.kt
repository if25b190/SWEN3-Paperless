package at.fhtw.swen3.paperless.config

import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered

@Configuration
class RequestLoggingConfig {

    @Bean
    fun requestLoggingFilter(): FilterRegistrationBean<RequestLoggingFilter> =
        FilterRegistrationBean(RequestLoggingFilter()).apply {
            order = Ordered.HIGHEST_PRECEDENCE
        }
}
