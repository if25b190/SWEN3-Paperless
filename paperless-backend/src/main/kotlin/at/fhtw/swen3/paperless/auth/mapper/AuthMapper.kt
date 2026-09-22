package at.fhtw.swen3.paperless.auth.mapper

import at.fhtw.swen3.paperless.auth.model.LoginCredentials
import at.fhtw.swen3.paperless.dto.LoginRequest

object AuthMapper {

    fun toCredentials(request: LoginRequest): LoginCredentials =
        LoginCredentials(
            username = request.username,
            password = request.password
        )
}
