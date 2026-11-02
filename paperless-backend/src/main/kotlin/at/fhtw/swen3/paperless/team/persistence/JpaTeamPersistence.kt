package at.fhtw.swen3.paperless.team.persistence

import at.fhtw.swen3.paperless.team.model.Team
import at.fhtw.swen3.paperless.team.persistence.mapper.TeamEntityMapper
import at.fhtw.swen3.paperless.team.persistence.repository.TeamRepository
import java.util.Optional
import java.util.UUID
import org.springframework.stereotype.Repository
import jakarta.persistence.EntityManager

@Repository
class JpaTeamPersistence(
    private val repository: TeamRepository,
    private val teamEntityMapper: TeamEntityMapper,
    private val entityManager: EntityManager,
) : TeamPersistence {

    override fun findAll(): List<Team> = repository.findAll().map(teamEntityMapper::toModel)

    override fun findById(id: UUID): Optional<Team> = repository.findById(id).map(teamEntityMapper::toModel)

    override fun findLockedById(id: UUID): Team? = repository.findLockedById(id)?.let(teamEntityMapper::toModel)

    override fun save(team: Team): Team = teamEntityMapper.toModel(repository.save(teamEntityMapper.toEntity(team)))

    override fun update(id: UUID, update: Team): Team {
        val entity = repository.findLockedById(id) ?: throw NoSuchElementException("Team $id not found")
        teamEntityMapper.applyTo(entity, update)
        entityManager.flush()

        return teamEntityMapper.toModel(entity)
    }

    override fun deleteById(id: UUID) = repository.deleteById(id)
}
