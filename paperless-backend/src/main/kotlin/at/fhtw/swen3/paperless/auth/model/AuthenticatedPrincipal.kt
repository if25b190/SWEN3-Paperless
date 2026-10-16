package at.fhtw.swen3.paperless.auth.model

import at.fhtw.swen3.paperless.exception.AuthenticationRequiredException
import java.util.UUID

object AuthenticatedPrincipal {

    fun userId(name: String?): UUID {
        val id = name?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        if (name == null || id == null || !id.toString().equals(name, ignoreCase = true)) {
            throw AuthenticationRequiredException()
        }
        return id
    }
}
