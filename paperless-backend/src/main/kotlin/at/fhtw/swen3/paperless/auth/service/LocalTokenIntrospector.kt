package at.fhtw.swen3.paperless.auth.service

import at.fhtw.swen3.paperless.auth.repository.AccessTokenRepository
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class LocalTokenIntrospector(private val tokens: AccessTokenRepository) : OpaqueTokenIntrospector {
    @Transactional(readOnly = true)
    override fun introspect(token: String): OAuth2AuthenticatedPrincipal {
        val stored = tokens.findById(TokenDigests.sha256(token))
            .orElseThrow { BadOpaqueTokenException("Invalid bearer token") }
        if (!stored.expiresAt.isAfter(Instant.now())) throw BadOpaqueTokenException("Expired bearer token")
        return DefaultOAuth2AuthenticatedPrincipal(
            requireNotNull(stored.user.id).toString(),
            mapOf("user_id" to requireNotNull(stored.user.id)),
            emptyList<SimpleGrantedAuthority>()
        )
    }
}
