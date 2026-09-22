package at.fhtw.swen3.paperless.user.service

import at.fhtw.swen3.paperless.team.model.TeamMember
import at.fhtw.swen3.paperless.user.model.User

interface UserService {

    fun searchUsers(): List<User>

    fun create(user: User): User

    fun getById(id: Long): User

    fun update(user: User): User

    fun delete(id: Long)

    fun getTeams(id: Long): List<TeamMember>

    fun authenticate(username: String, password: String): User
}
