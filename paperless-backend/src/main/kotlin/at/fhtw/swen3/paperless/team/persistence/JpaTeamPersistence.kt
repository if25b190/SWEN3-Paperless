package at.fhtw.swen3.paperless.team.persistence

import at.fhtw.swen3.paperless.team.model.Team
import at.fhtw.swen3.paperless.team.persistence.mapper.TeamEntityMapper
import at.fhtw.swen3.paperless.team.persistence.repository.TeamRepository
import java.util.Optional
import java.util.UUID
import org.springframework.stereotype.Repository

@Repository
class JpaTeamPersistence(private val repository: TeamRepository) : TeamPersistence {

    override fun findAll(): List<Team> = repository.findAll().map(TeamEntityMapper::toModel)

    override fun findById(id: UUID): Optional<Team> = repository.findById(id).map(TeamEntityMapper::toModel)

    override fun findLockedById(id: UUID): Team? = repository.findLockedById(id)?.let(TeamEntityMapper::toModel)

    override fun save(team: Team): Team = TeamEntityMapper.toModel(repository.save(TeamEntityMapper.toEntity(team)))

    override fun deleteById(id: UUID) = repository.deleteById(id)
}
