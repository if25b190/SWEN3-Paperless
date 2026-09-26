package at.fhtw.swen3.paperless.auth.model

import at.fhtw.swen3.paperless.exception.AppErrorMessage
import at.fhtw.swen3.paperless.exception.AppException
import java.util.UUID

object AuthenticatedPrincipal {

    fun userId(name: String?): UUID {
        val id = name?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        if (name == null || id == null || !id.toString().equals(name, ignoreCase = true)) {
            throw AppException(AppErrorMessage.AUTHENTICATION_REQUIRED)
        }
        return id
    }
}
