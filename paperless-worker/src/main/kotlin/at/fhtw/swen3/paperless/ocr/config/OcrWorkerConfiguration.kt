package at.fhtw.swen3.paperless.ocr.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OcrWorkerProperties::class)
class OcrWorkerConfiguration
