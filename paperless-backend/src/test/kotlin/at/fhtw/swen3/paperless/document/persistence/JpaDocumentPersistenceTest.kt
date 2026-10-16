package at.fhtw.swen3.paperless.document.persistence

import at.fhtw.swen3.paperless.common.mapper.MappingConverters

import at.fhtw.swen3.paperless.document.model.Document
import at.fhtw.swen3.paperless.document.model.ProcessingStatus
import at.fhtw.swen3.paperless.document.persistence.mapper.DocumentEntityMapperImpl

import at.fhtw.swen3.paperless.documenttype.entity.DocumentTypeEntity
import at.fhtw.swen3.paperless.documenttype.persistence.mapper.DocumentTypeEntityMapperImpl

import at.fhtw.swen3.paperless.team.model.Role
import at.fhtw.swen3.paperless.team.model.TeamMember
import at.fhtw.swen3.paperless.team.persistence.JpaTeamMemberPersistence
import at.fhtw.swen3.paperless.team.entity.TeamEntity
import at.fhtw.swen3.paperless.team.persistence.mapper.TeamEntityMapper
import at.fhtw.swen3.paperless.team.persistence.mapper.TeamEntityMapperImpl
import at.fhtw.swen3.paperless.team.persistence.mapper.TeamMemberEntityMapperImpl

import at.fhtw.swen3.paperless.team.persistence.repository.TeamMemberRepository
import at.fhtw.swen3.paperless.user.entity.UserEntity
import at.fhtw.swen3.paperless.user.persistence.mapper.UserEntityMapper
import at.fhtw.swen3.paperless.user.persistence.mapper.UserEntityMapperImpl

import jakarta.persistence.EntityManager
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import jakarta.persistence.OptimisticLockException

@DataJpaTest
@Import(
    JpaDocumentPersistence::class,
    JpaDocumentReferencesPersistence::class,
    JpaTeamMemberPersistence::class,
    DocumentEntityMapperImpl::class,
    DocumentTypeEntityMapperImpl::class,
    TeamEntityMapperImpl::class,
    UserEntityMapperImpl::class,
    TeamMemberEntityMapperImpl::class,
    MappingConverters::class
)
class JpaDocumentPersistenceTest(
    @Autowired private val persistence: DocumentPersistence,
    @Autowired private val references: DocumentReferencesPersistence,
    @Autowired private val memberships: JpaTeamMemberPersistence,
    @Autowired private val memberRepository: TeamMemberRepository,
    @Autowired private val entityManager: EntityManager,
    @Autowired private val teamEntityMapper: TeamEntityMapper,
    @Autowired private val userEntityMapper: UserEntityMapper
) {
    @Test
    fun document_mapping_resolves_associations_and_preserves_version_ok() {
        val user = UserEntity(username = "adapter-user", password = "hash", createdAt = Instant.now())
        val type = DocumentTypeEntity(name = "Invoice", description = "Bills")
        entityManager.persist(user)
        entityManager.persist(type)
        val document = document(requireNotNull(user.id)).copy(
            documentType = references.findDocumentType(requireNotNull(type.id))
        )

        val saved = persistence.save(document)
        persistence.flush()
        entityManager.clear()
        val loaded = persistence.findById(requireNotNull(saved.id)).orElseThrow()

        assertAll(
            { assertThat(saved.id).isNotNull() },
            { assertThat(saved.documentType?.name).isEqualTo("Invoice") },
            { assertThat(loaded.documentType?.description).isEqualTo("Bills") },
            { assertThat(loaded.ownerId).isEqualTo(user.id) }
        )
        persistence.save(loaded.copy(title = "Updated"))
        persistence.flush()
        entityManager.clear()
        val updated = persistence.findById(requireNotNull(saved.id)).orElseThrow()

        assertAll(
            { assertThat(updated.version).isEqualTo(loaded.version + 1) },
            { assertThat(updated.title).isEqualTo("Updated") }
        )
    }

    @Test
    fun refreshed_document_with_changed_version_ko() {
        val user = UserEntity(username = "stale-user", password = "hash", createdAt = Instant.now())
        entityManager.persist(user)
        val saved = persistence.save(document(requireNotNull(user.id)))
        persistence.flush()
        val initial = persistence.findById(requireNotNull(saved.id)).orElseThrow()
        persistence.save(initial.copy(title = "Concurrent update"))
        persistence.flush()

        assertThatThrownBy { persistence.refreshUnchanged(initial) }
            .isInstanceOf(OptimisticLockException::class.java)
    }

    @Test
    fun membership_role_update_keeps_identity_and_full_domain_relationships_ok() {
        val user = UserEntity(username = "member-user", password = "hash", createdAt = Instant.now())
        entityManager.persist(user)
        val team = TeamEntity(name = "Adapter team", ownerId = requireNotNull(user.id), createdAt = Instant.now())
        entityManager.persist(team)
        val member = TeamMember(
            teamEntityMapper.toModel(team), userEntityMapper.toModel(user), Role.READ_WRITE, Instant.now()
        )
        val created = memberships.save(member)
        entityManager.flush()
        val teamId = requireNotNull(team.id)
        val userId = requireNotNull(user.id)
        val originalId = requireNotNull(memberRepository.findByTeamIdAndUserId(teamId, userId)).id

        val updated = memberships.save(created.copy(role = Role.ADMIN))
        entityManager.flush()
        val stored = requireNotNull(memberRepository.findByTeamIdAndUserId(teamId, userId))

        assertAll(
            { assertThat(created.team.name).isEqualTo("Adapter team") },
            { assertThat(created.user.username).isEqualTo("member-user") },
            { assertThat(updated.role).isEqualTo(Role.ADMIN) },
            { assertThat(stored.id).isEqualTo(originalId) },
            { assertThat(memberRepository.count()).isEqualTo(1) }
        )
    }

    private fun document(ownerId: java.util.UUID) = Document(
        id = null,
        title = "Invoice",
        originalFilename = "invoice.txt",
        contentType = "text/plain",
        fileSize = 10,
        status = ProcessingStatus.PENDING,
        ocrContent = null,
        summary = null,
        storageKey = "documents/invoice.txt",
        documentType = null,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        ownerId = ownerId
    )
}
