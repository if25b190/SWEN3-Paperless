package at.fhtw.swen3.paperless.auth.service

import at.fhtw.swen3.paperless.auth.persistence.AccessTokenPersistence
import java.time.Instant
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal
import org.springframework.security.oauth2.server.resource.introspection.BadOpaqueTokenException
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class LocalTokenIntrospector(private val tokens: AccessTokenPersistence) : OpaqueTokenIntrospector {
    @Transactional(readOnly = true)
    override fun introspect(token: String): OAuth2AuthenticatedPrincipal {
        val stored = tokens.findById(TokenDigests.sha256(token))
            .orElseThrow { BadOpaqueTokenException("Invalid bearer token") }
        if (!stored.expiresAt.isAfter(Instant.now())) throw BadOpaqueTokenException("Expired bearer token")
        return DefaultOAuth2AuthenticatedPrincipal(
            stored.userId.toString(),
            mapOf("user_id" to stored.userId),
            emptyList<SimpleGrantedAuthority>()
        )
    }
}
