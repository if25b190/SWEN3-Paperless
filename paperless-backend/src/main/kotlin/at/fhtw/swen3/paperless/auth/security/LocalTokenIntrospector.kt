package at.fhtw.swen3.paperless.auth.security

import at.fhtw.swen3.paperless.auth.service.AuthService
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector
import org.springframework.stereotype.Component

@Component
class LocalTokenIntrospector(private val authService: AuthService) : OpaqueTokenIntrospector {
    override fun introspect(token: String): OAuth2AuthenticatedPrincipal {
        val userId = authService.resolveAccessToken(token)
            ?: throw BadOpaqueTokenException("Invalid bearer token")
        return DefaultOAuth2AuthenticatedPrincipal(
            userId.toString(),
            mapOf("user_id" to userId),
            emptyList<SimpleGrantedAuthority>()
        )
    }
}
