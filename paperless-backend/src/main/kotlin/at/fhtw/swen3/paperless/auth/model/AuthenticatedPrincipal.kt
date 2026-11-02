package at.fhtw.swen3.paperless.auth.model

import at.fhtw.swen3.paperless.exception.AuthenticationRequiredException
import java.util.UUID
import org.springframework.security.core.Authentication

object AuthenticatedPrincipal {

    /**
     * Resolves the authenticated user's ID from Spring Security's injected `Authentication`.
     * The parameter is nullable because permitAll endpoints (login, registration) run without one.
     */
    fun userId(authentication: Authentication?): UUID = userId(authentication?.name)

    fun userId(name: String?): UUID {
        val id = name?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        if (name == null || id == null || !id.toString().equals(name, ignoreCase = true)) {
            throw AuthenticationRequiredException()
        }
        return id
    }
}
