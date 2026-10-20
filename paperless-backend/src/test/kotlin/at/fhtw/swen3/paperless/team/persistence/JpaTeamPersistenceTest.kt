package at.fhtw.swen3.paperless.team.persistence

import at.fhtw.swen3.paperless.common.mapper.MappingConverters
import at.fhtw.swen3.paperless.team.entity.TeamEntity
import at.fhtw.swen3.paperless.team.entity.TeamMemberEntity
import at.fhtw.swen3.paperless.team.model.Role
import at.fhtw.swen3.paperless.team.persistence.mapper.TeamEntityMapperImpl
import at.fhtw.swen3.paperless.team.persistence.mapper.TeamMemberEntityMapperImpl
import at.fhtw.swen3.paperless.team.persistence.repository.TeamMemberRepository
import at.fhtw.swen3.paperless.team.persistence.repository.TeamRepository
import at.fhtw.swen3.paperless.user.entity.UserEntity
import at.fhtw.swen3.paperless.user.persistence.mapper.UserEntityMapperImpl
import jakarta.persistence.EntityManager
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import

@DataJpaTest
@Import(
    JpaTeamPersistence::class,
    JpaTeamMemberPersistence::class,
    TeamEntityMapperImpl::class,
    TeamMemberEntityMapperImpl::class,
    UserEntityMapperImpl::class,
    MappingConverters::class
)
class JpaTeamPersistenceTest(
    @Autowired private val persistence: JpaTeamPersistence,
    @Autowired private val teamRepository: TeamRepository,
    @Autowired private val memberRepository: TeamMemberRepository,
    @Autowired private val entityManager: EntityManager
) {
    @Test
    fun delete_team_cascades_to_memberships_ok() {
        // given
        val owner = UserEntity(username = "cascade-owner", password = "hash", createdAt = Instant.now())
        entityManager.persist(owner)
        val member = UserEntity(username = "cascade-member", password = "hash", createdAt = Instant.now())
        entityManager.persist(member)
        val team = TeamEntity(name = "Cascade team", ownerId = requireNotNull(owner.id), createdAt = Instant.now())
        entityManager.persist(team)
        entityManager.persist(TeamMemberEntity(team = team, user = owner, role = Role.ADMIN, joinedAt = Instant.now()))
        entityManager.persist(TeamMemberEntity(team = team, user = member, role = Role.READ_WRITE, joinedAt = Instant.now()))
        entityManager.flush()
        val teamId = requireNotNull(team.id)

        // when
        persistence.deleteById(teamId)
        entityManager.flush()

        // then
        assertAll(
            { assertThat(teamRepository.findById(teamId)).isEmpty() },
            { assertThat(memberRepository.count()).isZero() }
        )
    }
}
