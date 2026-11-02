package at.fhtw.swen3.paperless.common.entity

import at.fhtw.swen3.paperless.common.mapper.MappingConverters

import at.fhtw.swen3.paperless.documenttype.model.DocumentType
import at.fhtw.swen3.paperless.documenttype.persistence.DocumentTypePersistence
import at.fhtw.swen3.paperless.documenttype.persistence.JpaDocumentTypePersistence
import at.fhtw.swen3.paperless.documenttype.persistence.mapper.DocumentTypeEntityMapperImpl

import at.fhtw.swen3.paperless.team.model.Team
import at.fhtw.swen3.paperless.team.persistence.JpaTeamPersistence
import at.fhtw.swen3.paperless.team.persistence.mapper.TeamEntityMapperImpl

import at.fhtw.swen3.paperless.team.persistence.TeamPersistence
import at.fhtw.swen3.paperless.user.model.User
import at.fhtw.swen3.paperless.user.persistence.JpaUserPersistence
import at.fhtw.swen3.paperless.user.persistence.mapper.UserEntityMapperImpl

import at.fhtw.swen3.paperless.user.persistence.UserPersistence
import jakarta.persistence.EntityManager
import java.time.Instant
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import

@DataJpaTest
@Import(
    JpaUserPersistence::class,
    JpaTeamPersistence::class,
    JpaDocumentTypePersistence::class,
    UserEntityMapperImpl::class,
    TeamEntityMapperImpl::class,
    DocumentTypeEntityMapperImpl::class,
    MappingConverters::class
)
class DomainVersionPersistenceTest(@Autowired private val entityManager: EntityManager) {
    @Autowired private lateinit var users: UserPersistence
    @Autowired private lateinit var teams: TeamPersistence
    @Autowired private lateinit var types: DocumentTypePersistence

    @Test
    fun user_updates_preserve_versions_and_reject_stale_models_ok() {
        // given
        val saved = users.save(User(null, "version-user", "unused", Instant.now(), null))
        flushAndClear()
        val id = requireNotNull(saved.id)
        val initial = users.findById(id).orElseThrow()

        // when
        val updated = users.update(id, initial.copy(username = "version-user-1"))
        flushAndClear()
        val updatedAgain = users.update(id, updated.copy(username = "version-user-2"))
        flushAndClear()

        // then
        assertThat(updated.version).isEqualTo(initial.version + 1)
        assertThat(updatedAgain.version).isEqualTo(initial.version + 2)
        assertThat(users.findById(id).orElseThrow().username).isEqualTo("version-user-2")
    }

    @Test
    fun team_updates_preserve_versions_and_reject_stale_models_ok() {
        // given
        val saved = teams.save(Team(null, "Version team", null, Instant.now(), null, UUID.randomUUID()))
        flushAndClear()
        val id = requireNotNull(saved.id)
        val initial = teams.findById(id).orElseThrow()

        // when
        val updated = teams.update(id, initial.copy(name = "Version team 1"))
        flushAndClear()
        val updatedAgain = teams.update(id, updated.copy(name = "Version team 2"))
        flushAndClear()

        // then
        assertThat(updated.version).isEqualTo(initial.version + 1)
        assertThat(updatedAgain.version).isEqualTo(initial.version + 2)
        assertThat(teams.findById(id).orElseThrow().name).isEqualTo("Version team 2")
    }

    @Test
    fun document_type_updates_preserve_versions_and_reject_stale_models_ok() {
        // given
        val saved = types.save(DocumentType(null, "Version type", null))
        flushAndClear()
        val id = requireNotNull(saved.id)
        val initial = types.findById(id).orElseThrow()

        // when
        val updated = types.update(id, initial.copy(name = "Version type 1"))
        flushAndClear()
        val updatedAgain = types.update(id, updated.copy(name = "Version type 2"))
        flushAndClear()

        // then
        assertThat(updated.version).isEqualTo(initial.version + 1)
        assertThat(updatedAgain.version).isEqualTo(initial.version + 2)
        assertThat(types.findById(id).orElseThrow().name).isEqualTo("Version type 2")
    }

    private fun flushAndClear() {
        entityManager.flush()
        entityManager.clear()
    }
}
