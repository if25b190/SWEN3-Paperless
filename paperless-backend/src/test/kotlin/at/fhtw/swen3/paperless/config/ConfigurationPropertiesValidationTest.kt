package at.fhtw.swen3.paperless.config

import at.fhtw.swen3.paperless.document.persistence.config.ObjectStorageProperties
import at.fhtw.swen3.paperless.ocr.config.OcrMessagingProperties
import jakarta.validation.Validation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration
import java.time.Duration

class ConfigurationPropertiesValidationTest {
    private val validator = Validation.buildDefaultValidatorFactory().validator

    @Test
    fun invalid_bound_property_fails_startup_ko() {
        ApplicationContextRunner()
            .withUserConfiguration(EnableOcrMessagingProperties::class.java)
            .withPropertyValues("paperless.ocr.confirm-timeout=0s")
            .run { context ->
                assertThat(context).hasFailed()
                assertThat(context.startupFailure).hasStackTraceContaining("confirmTimeout")
            }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(OcrMessagingProperties::class)
    class EnableOcrMessagingProperties

    @Test
    fun default_messaging_properties_are_valid_ok() {
        assertThat(validator.validate(OcrMessagingProperties())).isEmpty()
    }

    @Test
    fun blank_queue_name_is_rejected_ko() {
        val violations = validator.validate(OcrMessagingProperties(queue = " "))

        assertThat(violations).anySatisfy {
            assertThat(it.propertyPath.toString()).isEqualTo("queue")
        }
    }

    @Test
    fun non_positive_confirm_timeout_is_rejected_ko() {
        val violations = validator.validate(OcrMessagingProperties(confirmTimeout = Duration.ZERO))

        assertThat(violations).anySatisfy {
            assertThat(it.propertyPath.toString()).isEqualTo("confirmTimeout")
        }
    }

    @Test
    fun default_storage_properties_are_valid_ok() {
        assertThat(validator.validate(ObjectStorageProperties())).isEmpty()
    }

    @Test
    fun blank_bucket_is_rejected_ko() {
        val violations = validator.validate(ObjectStorageProperties(bucket = ""))

        assertThat(violations).anySatisfy {
            assertThat(it.propertyPath.toString()).isEqualTo("bucket")
        }
    }
}
