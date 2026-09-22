package at.fhtw.swen3.paperless.auth.model

import at.fhtw.swen3.paperless.user.model.User

data class AuthenticatedUser(
    val user: User,
    val token: String
)
