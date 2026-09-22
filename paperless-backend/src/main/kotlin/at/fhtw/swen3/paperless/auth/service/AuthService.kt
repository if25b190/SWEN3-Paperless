package at.fhtw.swen3.paperless.auth.service

import at.fhtw.swen3.paperless.auth.model.AuthenticatedUser
import at.fhtw.swen3.paperless.auth.model.LoginCredentials
import at.fhtw.swen3.paperless.user.model.User

interface AuthService {

    fun login(credentials: LoginCredentials): AuthenticatedUser

    fun getCurrentUser(token: String?): User
}
