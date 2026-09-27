package at.fhtw.swen3.paperless.user.service

import at.fhtw.swen3.paperless.team.model.TeamMember
import at.fhtw.swen3.paperless.user.model.User
import java.util.UUID

interface UserService {

    fun searchUsers(): List<User>

    fun create(user: User): User

    fun getById(id: UUID): User

    fun update(id: UUID, username: String?, password: String?): User

    fun delete(id: UUID)

    fun getTeams(id: UUID): List<TeamMember>

    fun authenticate(username: String, password: String): User
}
