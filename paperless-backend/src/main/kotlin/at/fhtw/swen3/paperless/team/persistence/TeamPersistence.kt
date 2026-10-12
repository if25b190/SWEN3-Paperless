package at.fhtw.swen3.paperless.team.persistence

import at.fhtw.swen3.paperless.team.model.Team
import java.util.Optional
import java.util.UUID

interface TeamPersistence {
    fun findAll(): List<Team>
    fun findById(id: UUID): Optional<Team>
    fun findLockedById(id: UUID): Team?
    fun save(team: Team): Team
    fun deleteById(id: UUID)
}
