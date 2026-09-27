package at.fhtw.swen3.paperless.config

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.security.SecurityScheme
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "opaque")
class OpenApiConfig {

    @Bean
    fun customOpenAPI(): OpenAPI =
        OpenAPI().info(
            Info()
                .title("Paperless REST API")
                .description("Document management and processing system API for SWEN3 Paperless project.")
                .version("1.0.0")
                .contact(
                    Contact()
                        .name("SWEN3 Paperless Team")
                )
                .license(
                    License()
                        .name("Apache 2.0")
                        .url("https://www.apache.org/licenses/LICENSE-2.0")
                )
        )
}
