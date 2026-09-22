package at.fhtw.swen3.paperless.team.service

import at.fhtw.swen3.paperless.exception.AppErrorMessage
import at.fhtw.swen3.paperless.exception.AppException
import at.fhtw.swen3.paperless.team.entity.TeamEntity
import at.fhtw.swen3.paperless.team.entity.TeamMemberEntity
import at.fhtw.swen3.paperless.team.model.Role
import at.fhtw.swen3.paperless.team.model.Team
import at.fhtw.swen3.paperless.team.repository.TeamMemberRepository
import at.fhtw.swen3.paperless.team.repository.TeamRepository
import at.fhtw.swen3.paperless.user.entity.UserEntity
import at.fhtw.swen3.paperless.user.model.User
import at.fhtw.swen3.paperless.user.service.UserService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import java.time.Instant
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class TeamServiceImplTest {

    @Mock
    lateinit var teamRepository: TeamRepository

    @Mock
    lateinit var teamMemberRepository: TeamMemberRepository

    @Mock
    lateinit var userService: UserService

    @InjectMocks
    lateinit var service: TeamServiceImpl

    @Test
    fun get_teams_ok() {
        // given
        `when`(teamRepository.findAll()).thenReturn(listOf(teamEntity()))

        // when
        val result = service.getTeams()

        // then
        assertAll(
            { assertThat(result).hasSize(1) },
            { assertThat(result.single().name).isEqualTo("Team") }
        )
    }

    @Test
    fun create_team_ok() {
        // given
        `when`(teamRepository.save(any(TeamEntity::class.java))).thenReturn(teamEntity())

        // when
        val result = service.createTeam(team())

        // then
        assertAll(
            { assertThat(result.id).isEqualTo(1) },
            { assertThat(result.name).isEqualTo("Team") }
        )
    }

    @Test
    fun get_team_not_found_ko() {
        // given
        `when`(teamRepository.findById(42)).thenReturn(Optional.empty())

        // when / then
        assertAppError(AppErrorMessage.TEAM_NOT_FOUND) { service.getTeam(42) }
    }

    @Test
    fun update_team_preserves_unmodified_fields_ok() {
        // given
        val entity = TeamEntity(1, "Old", "Description", Instant.EPOCH, null)
        `when`(teamRepository.findById(1)).thenReturn(Optional.of(entity))
        `when`(teamRepository.save(entity)).thenReturn(entity)

        // when
        val result = service.updateTeam(1, null, "New description")

        // then
        assertAll(
            { assertThat(result.name).isEqualTo("Old") },
            { assertThat(result.description).isEqualTo("New description") },
            { assertThat(result.updatedAt).isNotNull() }
        )
    }

    @Test
    fun delete_team_ok() {
        // given
        `when`(teamRepository.findById(1)).thenReturn(Optional.of(teamEntity()))

        // when
        service.deleteTeam(1)

        // then
        assertAll(
            { verify(teamMemberRepository).deleteByTeamId(1) },
            { verify(teamRepository).deleteById(1) }
        )
    }

    @Test
    fun delete_team_not_found_ko() {
        // given
        `when`(teamRepository.findById(42)).thenReturn(Optional.empty())

        // when / then
        assertAppError(AppErrorMessage.TEAM_NOT_FOUND) { service.deleteTeam(42) }
    }

    @Test
    fun get_team_members_ok() {
        // given
        `when`(teamRepository.findById(1)).thenReturn(Optional.of(teamEntity()))
        `when`(teamMemberRepository.findByTeamId(1)).thenReturn(listOf(memberEntity()))

        // when
        val result = service.getTeamMembers(1)

        // then
        assertAll(
            { assertThat(result).hasSize(1) },
            { assertThat(result.single().user.id).isEqualTo(2) },
            { assertThat(result.single().role).isEqualTo(Role.MEMBER) }
        )
    }

    @Test
    fun add_team_member_ok() {
        // given
        `when`(teamRepository.findById(1)).thenReturn(Optional.of(teamEntity()))
        `when`(userService.getById(2)).thenReturn(user())
        `when`(teamMemberRepository.existsByTeamIdAndUserId(1, 2)).thenReturn(false)

        // when
        val result = service.addTeamMember(1, 2, Role.MEMBER)

        // then
        assertAll(
            { assertThat(result.team).isEqualTo(team()) },
            { assertThat(result.user).isEqualTo(user()) },
            { assertThat(result.role).isEqualTo(Role.MEMBER) },
            { verify(teamMemberRepository).save(any(TeamMemberEntity::class.java)) }
        )
    }

    @Test
    fun add_duplicate_team_member_ko() {
        // given
        `when`(teamRepository.findById(1)).thenReturn(Optional.of(teamEntity()))
        `when`(userService.getById(2)).thenReturn(user())
        `when`(teamMemberRepository.existsByTeamIdAndUserId(1, 2)).thenReturn(true)

        // when / then
        assertAll(
            { assertAppError(AppErrorMessage.TEAM_MEMBER_ALREADY_EXISTS) { service.addTeamMember(1, 2, Role.MEMBER) } },
            { verify(teamMemberRepository, never()).save(any(TeamMemberEntity::class.java)) }
        )
    }

    @Test
    fun update_team_member_role_ok() {
        // given
        val member = memberEntity()
        `when`(teamRepository.findById(1)).thenReturn(Optional.of(teamEntity()))
        `when`(teamMemberRepository.findByTeamIdAndUserId(1, 2)).thenReturn(member)
        `when`(teamMemberRepository.save(member)).thenReturn(member)

        // when
        val result = service.updateTeamMemberRole(1, 2, Role.ADMIN)

        // then
        assertAll(
            { assertThat(result.role).isEqualTo(Role.ADMIN) },
            { verify(teamMemberRepository).save(member) }
        )
    }

    @Test
    fun update_missing_team_member_ko() {
        // given
        `when`(teamRepository.findById(1)).thenReturn(Optional.of(teamEntity()))
        `when`(teamMemberRepository.findByTeamIdAndUserId(1, 2)).thenReturn(null)

        // when / then
        assertAll(
            { assertAppError(AppErrorMessage.TEAM_MEMBER_NOT_FOUND) { service.updateTeamMemberRole(1, 2, Role.ADMIN) } },
            { verify(teamMemberRepository, never()).save(any(TeamMemberEntity::class.java)) }
        )
    }

    @Test
    fun remove_team_member_ok() {
        // given
        val member = memberEntity()
        `when`(teamRepository.findById(1)).thenReturn(Optional.of(teamEntity()))
        `when`(teamMemberRepository.findByTeamIdAndUserId(1, 2)).thenReturn(member)

        // when
        service.removeTeamMember(1, 2)

        // then
        verify(teamMemberRepository).delete(member)
    }

    private fun assertAppError(expected: AppErrorMessage, action: () -> Unit) {
        val thrown = catchThrowable(action)
        assertAll(
            { assertThat(thrown).isInstanceOf(AppException::class.java) },
            { assertThat((thrown as AppException).error).isEqualTo(expected) }
        )
    }

    private fun team() = Team(1, "Team", null, Instant.EPOCH, null)

    private fun teamEntity() = TeamEntity(1, "Team", null, Instant.EPOCH, null)

    private fun user() = User(2, "alice", "alice@example.com", "secret", Instant.EPOCH, null)

    private fun memberEntity() = TeamMemberEntity(
        team = teamEntity(),
        user = UserEntity(2, "alice", "alice@example.com", "secret"),
        role = Role.MEMBER,
        joinedAt = Instant.EPOCH
    )
}
