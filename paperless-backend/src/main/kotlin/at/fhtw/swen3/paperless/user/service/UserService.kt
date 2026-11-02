package at.fhtw.swen3.paperless.user.service

import at.fhtw.swen3.paperless.team.model.TeamMember
import at.fhtw.swen3.paperless.user.model.User
import java.util.UUID

interface UserService {

    fun searchUsers(): List<User>

    fun create(user: User): User

    fun getById(id: UUID): User

    fun update(actorId: UUID, id: UUID, username: String?, password: String?): User

    fun delete(actorId: UUID, id: UUID)

    fun getTeams(actorId: UUID, id: UUID): List<TeamMember>
}
