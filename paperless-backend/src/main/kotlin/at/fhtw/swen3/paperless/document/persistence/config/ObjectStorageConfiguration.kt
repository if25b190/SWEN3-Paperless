package at.fhtw.swen3.paperless.document.persistence.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import java.time.Duration

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ObjectStorageProperties::class)
@ConditionalOnProperty(prefix = "paperless.storage", name = ["type"], havingValue = "s3", matchIfMissing = true)
class ObjectStorageConfiguration {
    @Bean(destroyMethod = "close")
    fun documentS3Client(properties: ObjectStorageProperties): S3Client = S3Client.builder()
        .endpointOverride(properties.endpoint)
        .region(Region.of(properties.region))
        .forcePathStyle(true)
        .credentialsProvider(
            StaticCredentialsProvider.create(AwsBasicCredentials.create(properties.accessKey, properties.secretKey))
        )
        .httpClientBuilder(
            UrlConnectionHttpClient.builder()
                .connectionTimeout(Duration.ofSeconds(5))
                .socketTimeout(Duration.ofSeconds(30))
        )
        .overrideConfiguration { it.apiCallTimeout(Duration.ofSeconds(60)) }
        .build()
}
