package at.fhtw.swen3.paperless.document.persistence

import at.fhtw.swen3.paperless.documenttype.entity.DocumentTypeEntity
import at.fhtw.swen3.paperless.documenttype.model.DocumentType
import at.fhtw.swen3.paperless.documenttype.persistence.mapper.DocumentTypeEntityMapper
import at.fhtw.swen3.paperless.team.entity.TeamEntity
import at.fhtw.swen3.paperless.team.model.Team
import at.fhtw.swen3.paperless.team.persistence.mapper.TeamEntityMapper
import at.fhtw.swen3.paperless.team.persistence.repository.TeamRepository
import at.fhtw.swen3.paperless.user.entity.UserEntity
import jakarta.persistence.EntityManager
import java.util.UUID
import org.springframework.stereotype.Repository

@Repository
class JpaDocumentReferencesPersistence(
    private val entityManager: EntityManager,
    private val teamRepository: TeamRepository,
    private val teamEntityMapper: TeamEntityMapper,
    private val documentTypeEntityMapper: DocumentTypeEntityMapper
) : DocumentReferencesPersistence {

    override fun ownerExists(id: UUID): Boolean = entityManager.find(UserEntity::class.java, id) != null

    override fun findTeam(id: UUID): Team? =
        entityManager.find(TeamEntity::class.java, id)?.let(teamEntityMapper::toModel)

    override fun findLockedTeam(id: UUID): Team? =
        teamRepository.findLockedById(id)?.let(teamEntityMapper::toModel)

    override fun findDocumentType(id: UUID): DocumentType? =
        entityManager.find(DocumentTypeEntity::class.java, id)?.let(documentTypeEntityMapper::toModel)
}
