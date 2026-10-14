package at.fhtw.swen3.paperless.common.entity

import at.fhtw.swen3.paperless.document.entity.DocumentEntity
import at.fhtw.swen3.paperless.documenttype.entity.DocumentTypeEntity
import at.fhtw.swen3.paperless.team.entity.TeamEntity
import at.fhtw.swen3.paperless.team.entity.TeamMemberEntity
import at.fhtw.swen3.paperless.user.entity.UserEntity
import jakarta.persistence.EntityManager
import jakarta.persistence.OptimisticLockException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest

@DataJpaTest
class AbstractEntityPersistenceTest(@Autowired private val entityManager: EntityManager) {

    @Test
    fun inherited_identity_audit_and_version_mapping_ok() {
        val user = UserEntity(username = "audit-user", password = "unused")
        entityManager.persist(user)
        val team = TeamEntity(name = "Audit team", ownerId = requireNotNull(user.id))
        val type = DocumentTypeEntity(name = "Audit type")
        entityManager.persist(team)
        entityManager.persist(type)
        val member = TeamMemberEntity(team = team, user = user)
        val document = DocumentEntity(
            title = "Audit document",
            originalFilename = "audit.txt",
            contentType = "text/plain",
            owner = user,
            team = team,
            documentType = type
        )
        entityManager.persist(member)
        entityManager.persist(document)
        entityManager.flush()

        assertAll(
            { assertAudit(user) },
            { assertAudit(team) },
            { assertAudit(type) },
            { assertAudit(member) },
            { assertAudit(document) }
        )
        val createdAt = team.createdAt
        val originalVersion = team.version
        team.name = "Updated team"
        entityManager.flush()

        assertAll(
            { assertThat(team.createdAt).isEqualTo(createdAt) },
            { assertThat(team.updatedAt).isAfterOrEqualTo(createdAt) },
            { assertThat(team.version).isEqualTo(originalVersion + 1) }
        )
    }

    @Test
    fun stale_detached_entity_update_ko() {
        val team = TeamEntity(name = "Original", ownerId = java.util.UUID.randomUUID())
        entityManager.persist(team)
        entityManager.flush()
        val stale = TeamEntity(
            id = team.id,
            name = "Stale update",
            ownerId = team.ownerId,
            createdAt = team.createdAt,
            updatedAt = team.updatedAt,
            version = team.version
        )
        team.name = "Concurrent update"
        entityManager.flush()
        entityManager.clear()

        assertThatThrownBy { entityManager.merge(stale) }.isInstanceOf(OptimisticLockException::class.java)
    }

    private fun assertAudit(entity: AbstractEntity) {
        assertAll(
            { assertThat(entity.id).isNotNull() },
            { assertThat(entity.createdAt).isAfter(java.time.Instant.EPOCH) },
            { assertThat(entity.updatedAt).isNotNull() },
            { assertThat(entity.version).isZero() }
        )
    }
}
