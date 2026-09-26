package at.fhtw.swen3.paperless.config

import at.fhtw.swen3.paperless.auth.service.LocalTokenIntrospector
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.http.HttpHeaders
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.password.DelegatingPasswordEncoder
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.http.HttpMethod

@Configuration
@EnableWebSecurity
class SecurityConfig(private val introspector: LocalTokenIntrospector) {

    @Bean
    fun passwordEncoder(): PasswordEncoder {
        val id = "pbkdf2@SpringSecurity_v5_8"
        return DelegatingPasswordEncoder(id, mapOf(id to Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8()))
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(HttpMethod.POST, "/auth/login", "/users").permitAll()
                    .requestMatchers(
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**",
                        "/v3/api-docs.yaml"
                    ).permitAll()
                    .anyRequest().authenticated()
            }
            .oauth2ResourceServer {
                it.authenticationEntryPoint { _, response, _ -> problem(response, 401, "Unauthorized", "Authentication is required.") }
                it.opaqueToken { opaque -> opaque.introspector(introspector) }
            }
            .exceptionHandling {
                it.authenticationEntryPoint { _, response, _ -> problem(response, 401, "Unauthorized", "Authentication is required.") }
                it.accessDeniedHandler { _, response, _ -> problem(response, 403, "Forbidden", "Access is denied.") }
            }
        return http.build()
    }

    private fun problem(response: HttpServletResponse, status: Int, title: String, detail: String) {
        response.status = status
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        if (status == 401) response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
        response.writer.write("""{"type":"urn:problem-type:$status","title":"$title","status":$status,"detail":"$detail"}""")
    }
}
