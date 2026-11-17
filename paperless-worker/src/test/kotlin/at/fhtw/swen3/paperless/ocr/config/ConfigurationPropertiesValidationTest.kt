package at.fhtw.swen3.paperless.ocr.config

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
            .withUserConfiguration(EnableOcrWorkerProperties::class.java)
            .withPropertyValues("paperless.worker.dpi=0")
            .run { context ->
                assertThat(context).hasFailed()
                assertThat(context.startupFailure).hasStackTraceContaining("dpi")
            }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(OcrWorkerProperties::class)
    class EnableOcrWorkerProperties

    @Test
    fun default_worker_properties_are_valid_ok() {
        assertThat(validator.validate(OcrWorkerProperties())).isEmpty()
    }

    @Test
    fun non_positive_dpi_is_rejected_ko() {
        val violations = validator.validate(OcrWorkerProperties(dpi = 0))

        assertThat(violations).anySatisfy {
            assertThat(it.propertyPath.toString()).isEqualTo("dpi")
        }
    }

    @Test
    fun non_positive_process_timeout_is_rejected_ko() {
        val violations = validator.validate(OcrWorkerProperties(processTimeout = Duration.ZERO))

        assertThat(violations).anySatisfy {
            assertThat(it.propertyPath.toString()).isEqualTo("processTimeout")
        }
    }

    @Test
    fun blank_index_name_is_rejected_ko() {
        val violations = validator.validate(OcrWorkerProperties(indexName = " "))

        assertThat(violations).anySatisfy {
            assertThat(it.propertyPath.toString()).isEqualTo("indexName")
        }
    }

    @Test
    fun default_messaging_properties_are_valid_ok() {
        assertThat(validator.validate(OcrMessagingProperties())).isEmpty()
    }

    @Test
    fun blank_exchange_name_is_rejected_ko() {
        val violations = validator.validate(OcrMessagingProperties(exchange = ""))

        assertThat(violations).anySatisfy {
            assertThat(it.propertyPath.toString()).isEqualTo("exchange")
        }
    }

    @Test
    fun default_storage_properties_are_valid_ok() {
        assertThat(validator.validate(ObjectStorageProperties())).isEmpty()
    }

    @Test
    fun blank_access_key_is_rejected_ko() {
        val violations = validator.validate(ObjectStorageProperties(accessKey = " "))

        assertThat(violations).anySatisfy {
            assertThat(it.propertyPath.toString()).isEqualTo("accessKey")
        }
    }
}
