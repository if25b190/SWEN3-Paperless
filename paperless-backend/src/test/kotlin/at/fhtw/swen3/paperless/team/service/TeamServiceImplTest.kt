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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.time.Instant
import java.util.Optional
import java.util.UUID

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

    @BeforeEach
    fun setUpAuthentication() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(fixtureId(1).toString(), "password", emptyList())
    }

    @AfterEach
    fun clearAuthentication() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun get_teams_ok() {
        // given
        `when`(teamMemberRepository.findByUserId(fixtureId(1))).thenReturn(listOf(adminMemberEntity()))

        // when
        val result = service.getTeams()

        // then
        assertAll(
            { assertThat(result).hasSize(1) },
            { assertThat(result.single().name).isEqualTo("Team") },
            { assertThat(result.single().ownerId).isEqualTo(fixtureId(1)) }
        )
    }

    @Test
    fun create_team_ok() {
        // given
        `when`(teamRepository.save(any(TeamEntity::class.java))).thenReturn(teamEntity())
        `when`(userService.getById(fixtureId(1))).thenReturn(user(fixtureId(1)))

        // when
        val result = service.createTeam(team())

        // then
        val teamCaptor = ArgumentCaptor.forClass(TeamEntity::class.java)
        verify(teamRepository).save(teamCaptor.capture())
        val membershipCaptor = ArgumentCaptor.forClass(TeamMemberEntity::class.java)
        verify(teamMemberRepository).save(membershipCaptor.capture())
        assertAll(
            { assertThat(result.id).isEqualTo(fixtureId(1)) },
            { assertThat(result.name).isEqualTo("Team") },
            { assertThat(teamCaptor.value.ownerId).isEqualTo(fixtureId(1)) },
            { assertThat(result.ownerId).isEqualTo(fixtureId(1)) },
            { assertThat(membershipCaptor.value.team.id).isEqualTo(fixtureId(1)) },
            { assertThat(membershipCaptor.value.user.id).isEqualTo(fixtureId(1)) },
            { assertThat(membershipCaptor.value.role).isEqualTo(Role.ADMIN) }
        )
    }

    @Test
    fun get_team_not_found_ko() {
        // given
        `when`(teamRepository.findById(fixtureId(42))).thenReturn(Optional.empty())

        // when / then
        assertAppError(AppErrorMessage.TEAM_NOT_FOUND) { service.getTeam(fixtureId(42)) }
    }

    @Test
    fun non_member_cannot_read_team_or_members_ko() {
        `when`(teamRepository.findById(fixtureId(1))).thenReturn(Optional.of(teamEntity(ownerId = fixtureId(3))))
        `when`(teamMemberRepository.existsByTeamIdAndUserId(fixtureId(1), fixtureId(1))).thenReturn(false)

        assertAll(
            { assertAccessDenied { service.getTeam(fixtureId(1)) } },
            { assertAccessDenied { service.getTeamMembers(fixtureId(1)) } }
        )
    }

    @Test
    fun owner_can_read_team_ok() {
        `when`(teamRepository.findById(fixtureId(1))).thenReturn(Optional.of(teamEntity(ownerId = fixtureId(1))))

        val result = service.getTeam(fixtureId(1))

        assertThat(result.ownerId).isEqualTo(fixtureId(1))
        verify(teamMemberRepository, never()).existsByTeamIdAndUserId(fixtureId(1), fixtureId(1))
    }

    @Test
    fun update_team_preserves_unmodified_fields_ok() {
        // given
        val entity = TeamEntity(fixtureId(1), "Old", "Description", Instant.EPOCH, null)
        `when`(teamRepository.findById(fixtureId(1))).thenReturn(Optional.of(entity))
        `when`(teamRepository.save(entity)).thenReturn(entity)
        stubAdminMembership()

        // when
        val result = service.updateTeam(fixtureId(1), null, "New description")

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
        `when`(teamRepository.findLockedById(fixtureId(1))).thenReturn(teamEntity())

        // when
        service.deleteTeam(fixtureId(1))

        // then
        assertAll(
            { verify(teamMemberRepository).deleteByTeamId(fixtureId(1)) },
            { verify(teamRepository).deleteById(fixtureId(1)) }
        )
    }

    @Test
    fun delete_team_not_found_ko() {
        // given
        `when`(teamRepository.findLockedById(fixtureId(42))).thenReturn(null)

        // when / then
        assertAppError(AppErrorMessage.TEAM_NOT_FOUND) { service.deleteTeam(fixtureId(42)) }
    }

    @Test
    fun get_team_members_ok() {
        // given
        `when`(teamRepository.findById(fixtureId(1))).thenReturn(Optional.of(teamEntity()))
        `when`(teamMemberRepository.findByTeamId(fixtureId(1))).thenReturn(listOf(memberEntity()))

        // when
        val result = service.getTeamMembers(fixtureId(1))

        // then
        assertAll(
            { assertThat(result).hasSize(1) },
            { assertThat(result.single().user.id).isEqualTo(fixtureId(2)) },
            { assertThat(result.single().role).isEqualTo(Role.READ_WRITE) }
        )
    }

    @Test
    fun role_for_uses_current_membership_and_returns_null_when_removed() {
        `when`(teamMemberRepository.findByTeamIdAndUserId(fixtureId(1), fixtureId(2))).thenReturn(memberEntity(userId = fixtureId(2)))
        `when`(teamMemberRepository.findByTeamIdAndUserId(fixtureId(1), fixtureId(3))).thenReturn(null)

        assertAll(
            { assertThat(service.roleFor(fixtureId(1), fixtureId(2))).isEqualTo(Role.READ_WRITE) },
            { assertThat(service.roleFor(fixtureId(1), fixtureId(3))).isNull() }
        )
    }

    @Test
    fun visible_team_ids_comes_from_current_memberships() {
        `when`(teamMemberRepository.findByUserId(fixtureId(7))).thenReturn(
            listOf(memberEntity(teamId = fixtureId(4), userId = fixtureId(7)), memberEntity(teamId = fixtureId(9), userId = fixtureId(7)))
        )

        assertThat(service.visibleTeamIds(fixtureId(7))).containsExactlyInAnyOrder(fixtureId(4), fixtureId(9))
    }

    @Test
    fun owner_without_membership_has_no_visible_team_ids_or_role() {
        `when`(teamMemberRepository.findByTeamIdAndUserId(fixtureId(1), fixtureId(3))).thenReturn(null)
        `when`(teamMemberRepository.findByUserId(fixtureId(3))).thenReturn(emptyList())

        assertAll(
            { assertThat(service.roleFor(fixtureId(1), fixtureId(3))).isNull() },
            { assertThat(service.visibleTeamIds(fixtureId(3))).isEmpty() }
        )
    }

    @Test
    fun malformed_principal_fails_closed() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken("not-a-uuid", "password", emptyList())

        assertAppError(AppErrorMessage.AUTHENTICATION_REQUIRED) { service.getTeams() }
    }

    @Test
    fun locked_roles_lock_sorted_teams_before_reading_current_membership() {
        val currentMembership = adminMemberEntity(teamId = fixtureId(3), userId = fixtureId(7), ownerId = fixtureId(99))
        `when`(teamRepository.findLockedById(fixtureId(3))).thenReturn(teamEntity(fixtureId(3), ownerId = fixtureId(99)))
        `when`(teamRepository.findLockedById(fixtureId(9))).thenReturn(teamEntity(fixtureId(9), ownerId = fixtureId(99)))
        `when`(teamMemberRepository.findByTeamIdAndUserId(fixtureId(3), fixtureId(7))).thenReturn(currentMembership)
        `when`(teamMemberRepository.findByTeamIdAndUserId(fixtureId(9), fixtureId(7))).thenReturn(null)

        val result = service.lockedRoles(setOf(fixtureId(9), fixtureId(3)), fixtureId(7))

        val sortedTeamIds = listOf(fixtureId(3), fixtureId(9)).sorted()
        assertThat(result.keys).containsExactlyElementsOf(sortedTeamIds)
        assertThat(result[fixtureId(3)]).isEqualTo(Role.ADMIN)
        assertThat(result[fixtureId(9)]).isNull()

        val ordered = inOrder(teamRepository, teamMemberRepository)
        sortedTeamIds.forEach { ordered.verify(teamRepository).findLockedById(it) }
        sortedTeamIds.forEach { ordered.verify(teamMemberRepository).findByTeamIdAndUserId(it, fixtureId(7)) }
    }

    @Test
    fun locked_roles_do_not_grant_owner_role_without_membership() {
        `when`(teamRepository.findLockedById(fixtureId(1))).thenReturn(teamEntity(ownerId = fixtureId(7)))
        `when`(teamMemberRepository.findByTeamIdAndUserId(fixtureId(1), fixtureId(7))).thenReturn(null)

        assertThat(service.lockedRoles(setOf(fixtureId(1)), fixtureId(7))).containsEntry(fixtureId(1), null)
    }

    @Test
    fun locked_roles_reports_missing_team_distinctly() {
        `when`(teamRepository.findLockedById(fixtureId(1))).thenReturn(null)

        assertAppError(AppErrorMessage.TEAM_NOT_FOUND) { service.lockedRoles(setOf(fixtureId(1)), fixtureId(7)) }

        verify(teamMemberRepository, never()).findByTeamIdAndUserId(fixtureId(1), fixtureId(7))
    }

    @Test
    fun add_team_member_ok() {
        // given
        `when`(teamRepository.findLockedById(fixtureId(1))).thenReturn(teamEntity())
        `when`(userService.getById(fixtureId(2))).thenReturn(user())
        `when`(teamMemberRepository.existsByTeamIdAndUserId(fixtureId(1), fixtureId(2))).thenReturn(false)

        // when
        val result = service.addTeamMember(fixtureId(1), fixtureId(2), Role.READ_WRITE)

        // then
        assertAll(
            { assertThat(result.team).isEqualTo(team().copy(ownerId = fixtureId(1))) },
            { assertThat(result.user).isEqualTo(user()) },
            { assertThat(result.role).isEqualTo(Role.READ_WRITE) },
            { verify(teamRepository).findLockedById(fixtureId(1)) },
            { verify(teamMemberRepository).save(any(TeamMemberEntity::class.java)) }
        )
    }

    @Test
    fun add_duplicate_team_member_ko() {
        // given
        `when`(teamRepository.findLockedById(fixtureId(1))).thenReturn(teamEntity())
        `when`(userService.getById(fixtureId(2))).thenReturn(user())
        `when`(teamMemberRepository.existsByTeamIdAndUserId(fixtureId(1), fixtureId(2))).thenReturn(true)

        // when / then
        assertAll(
            { assertAppError(AppErrorMessage.TEAM_MEMBER_ALREADY_EXISTS) { service.addTeamMember(fixtureId(1), fixtureId(2), Role.READ_WRITE) } },
            { verify(teamMemberRepository, never()).save(any(TeamMemberEntity::class.java)) }
        )
    }

    @Test
    fun update_team_member_role_ok() {
        // given
        val member = memberEntity()
        `when`(teamRepository.findLockedById(fixtureId(1))).thenReturn(teamEntity())
        `when`(teamMemberRepository.findByTeamIdAndUserId(fixtureId(1), fixtureId(2))).thenReturn(member)
        `when`(teamMemberRepository.save(member)).thenReturn(member)

        // when
        val result = service.updateTeamMemberRole(fixtureId(1), fixtureId(2), Role.ADMIN)

        // then
        assertAll(
            { assertThat(result.role).isEqualTo(Role.ADMIN) },
            { verify(teamMemberRepository).save(member) }
        )
    }

    @Test
    fun update_missing_team_member_ko() {
        // given
        `when`(teamRepository.findLockedById(fixtureId(1))).thenReturn(teamEntity())
        `when`(teamMemberRepository.findByTeamIdAndUserId(fixtureId(1), fixtureId(2))).thenReturn(null)

        // when / then
        assertAll(
            { assertAppError(AppErrorMessage.TEAM_MEMBER_NOT_FOUND) { service.updateTeamMemberRole(fixtureId(1), fixtureId(2), Role.ADMIN) } },
            { verify(teamMemberRepository, never()).save(any(TeamMemberEntity::class.java)) }
        )
    }

    @Test
    fun remove_team_member_ok() {
        // given
        val member = memberEntity()
        `when`(teamRepository.findLockedById(fixtureId(1))).thenReturn(teamEntity())
        `when`(teamMemberRepository.findByTeamIdAndUserId(fixtureId(1), fixtureId(2))).thenReturn(member)

        // when
        service.removeTeamMember(fixtureId(1), fixtureId(2))

        // then
        verify(teamMemberRepository).delete(member)
    }

    @Test
    fun admin_of_another_team_cannot_mutate_target_team_ko() {
        // given
        val adminMemberships = mapOf(fixtureId(2) to adminMemberEntity(fixtureId(2)))
        `when`(teamRepository.findById(fixtureId(1))).thenReturn(Optional.of(teamEntity(ownerId = fixtureId(3))))
        `when`(teamRepository.findLockedById(fixtureId(1))).thenReturn(teamEntity(ownerId = fixtureId(3)))
        `when`(teamMemberRepository.findByTeamIdAndUserId(fixtureId(1), fixtureId(1))).thenAnswer {
            adminMemberships[it.getArgument<UUID>(0)]
        }

        // when / then
        assertAll(
            { assertAccessDenied { service.updateTeam(fixtureId(1), "Updated", null) } },
            { assertAccessDenied { service.deleteTeam(fixtureId(1)) } },
            { assertAccessDenied { service.addTeamMember(fixtureId(1), fixtureId(2), Role.READ_WRITE) } },
            { assertAccessDenied { service.updateTeamMemberRole(fixtureId(1), fixtureId(2), Role.ADMIN) } },
            { assertAccessDenied { service.removeTeamMember(fixtureId(1), fixtureId(2)) } },
            { verify(teamRepository, never()).save(any(TeamEntity::class.java)) },
            { verify(teamRepository, never()).deleteById(fixtureId(1)) },
            { verify(teamMemberRepository, never()).save(any(TeamMemberEntity::class.java)) },
            { verify(teamMemberRepository, never()).deleteByTeamId(fixtureId(1)) },
            { verify(teamMemberRepository, never()).delete(any(TeamMemberEntity::class.java)) }
        )
    }

    @Test
    fun last_admin_cannot_be_demoted_or_removed_ko() {
        // given
        val lastAdmin = adminMemberEntity(ownerId = fixtureId(3))
        `when`(teamRepository.findLockedById(fixtureId(1))).thenReturn(teamEntity(ownerId = fixtureId(3)))
        `when`(teamMemberRepository.findByTeamIdAndUserId(fixtureId(1), fixtureId(1))).thenReturn(lastAdmin)
        `when`(teamMemberRepository.findByTeamId(fixtureId(1))).thenReturn(listOf(lastAdmin))

        // when / then
        assertAll(
            { assertAccessDenied { service.updateTeamMemberRole(fixtureId(1), fixtureId(1), Role.READ_WRITE) } },
            { assertAccessDenied { service.removeTeamMember(fixtureId(1), fixtureId(1)) } },
            { verify(teamMemberRepository, never()).save(any(TeamMemberEntity::class.java)) },
            { verify(teamMemberRepository, never()).delete(any(TeamMemberEntity::class.java)) }
        )
    }

    @Test
    fun admin_can_be_demoted_when_another_admin_exists_ok() {
        // given
        val actor = adminMemberEntity(ownerId = fixtureId(3))
        val target = adminMemberEntity(userId = fixtureId(2), ownerId = fixtureId(3))
        val anotherAdmin = adminMemberEntity(userId = fixtureId(3), ownerId = fixtureId(3))
        `when`(teamRepository.findLockedById(fixtureId(1))).thenReturn(teamEntity(ownerId = fixtureId(3)))
        `when`(teamMemberRepository.findByTeamIdAndUserId(fixtureId(1), fixtureId(1))).thenReturn(actor)
        `when`(teamMemberRepository.findByTeamIdAndUserId(fixtureId(1), fixtureId(2))).thenReturn(target)
        `when`(teamMemberRepository.findByTeamId(fixtureId(1))).thenReturn(listOf(actor, target, anotherAdmin))
        `when`(teamMemberRepository.save(target)).thenReturn(target)

        // when
        val result = service.updateTeamMemberRole(fixtureId(1), fixtureId(2), Role.READ_WRITE)

        // then
        assertThat(result.role).isEqualTo(Role.READ_WRITE)
        verify(teamMemberRepository).save(target)
    }

    @Test
    fun admin_can_be_removed_when_another_admin_exists_ok() {
        // given
        val actor = adminMemberEntity(ownerId = fixtureId(3))
        val target = adminMemberEntity(userId = fixtureId(2), ownerId = fixtureId(3))
        val anotherAdmin = adminMemberEntity(userId = fixtureId(3), ownerId = fixtureId(3))
        `when`(teamRepository.findLockedById(fixtureId(1))).thenReturn(teamEntity(ownerId = fixtureId(3)))
        `when`(teamMemberRepository.findByTeamIdAndUserId(fixtureId(1), fixtureId(1))).thenReturn(actor)
        `when`(teamMemberRepository.findByTeamIdAndUserId(fixtureId(1), fixtureId(2))).thenReturn(target)
        `when`(teamMemberRepository.findByTeamId(fixtureId(1))).thenReturn(listOf(actor, target, anotherAdmin))

        // when
        service.removeTeamMember(fixtureId(1), fixtureId(2))

        // then
        verify(teamMemberRepository).delete(target)
    }

    @Test
    fun non_owner_admin_cannot_demote_or_remove_owner_ko() {
        `when`(teamRepository.findLockedById(fixtureId(1))).thenReturn(teamEntity(ownerId = fixtureId(3)))
        `when`(teamMemberRepository.findByTeamIdAndUserId(fixtureId(1), fixtureId(1))).thenReturn(adminMemberEntity(ownerId = fixtureId(3)))

        assertAll(
            { assertAccessDenied { service.updateTeamMemberRole(fixtureId(1), fixtureId(3), Role.READ_WRITE) } },
            { assertAccessDenied { service.removeTeamMember(fixtureId(1), fixtureId(3)) } },
            { verify(teamMemberRepository, never()).save(any(TeamMemberEntity::class.java)) },
            { verify(teamMemberRepository, never()).delete(any(TeamMemberEntity::class.java)) }
        )
    }

    @Test
    fun non_owner_admin_cannot_delete_team_ko() {
        `when`(teamRepository.findLockedById(fixtureId(1))).thenReturn(teamEntity(ownerId = fixtureId(3)))

        assertAccessDenied { service.deleteTeam(fixtureId(1)) }

        verify(teamRepository, never()).deleteById(fixtureId(1))
        verify(teamMemberRepository, never()).deleteByTeamId(fixtureId(1))
    }

    @Test
    fun readonly_member_cannot_mutate_team_ko() {
        val readOnlyMember = memberEntity(userId = fixtureId(1), role = Role.READONLY, ownerId = fixtureId(3))
        `when`(teamRepository.findById(fixtureId(1))).thenReturn(Optional.of(teamEntity(ownerId = fixtureId(3))))
        `when`(teamRepository.findLockedById(fixtureId(1))).thenReturn(teamEntity(ownerId = fixtureId(3)))
        `when`(teamMemberRepository.findByTeamIdAndUserId(fixtureId(1), fixtureId(1))).thenReturn(readOnlyMember)

        assertAll(
            { assertAccessDenied { service.updateTeam(fixtureId(1), "Updated", null) } },
            { assertAccessDenied { service.addTeamMember(fixtureId(1), fixtureId(2), Role.READ_WRITE) } },
            { assertAccessDenied { service.updateTeamMemberRole(fixtureId(1), fixtureId(2), Role.ADMIN) } },
            { assertAccessDenied { service.removeTeamMember(fixtureId(1), fixtureId(2)) } },
            { assertAccessDenied { service.deleteTeam(fixtureId(1)) } },
            { verify(teamRepository, never()).save(any(TeamEntity::class.java)) },
            { verify(teamRepository, never()).deleteById(fixtureId(1)) },
            { verify(teamMemberRepository, never()).save(any(TeamMemberEntity::class.java)) },
            { verify(teamMemberRepository, never()).delete(any(TeamMemberEntity::class.java)) }
        )
    }

    @Test
    fun readonly_member_can_read_team_and_members_ok() {
        `when`(teamRepository.findById(fixtureId(1))).thenReturn(Optional.of(teamEntity(ownerId = fixtureId(3))))
        `when`(teamMemberRepository.existsByTeamIdAndUserId(fixtureId(1), fixtureId(1))).thenReturn(true)
        `when`(teamMemberRepository.findByTeamId(fixtureId(1))).thenReturn(listOf(memberEntity()))

        assertAll(
            { assertThat(service.getTeam(fixtureId(1)).ownerId).isEqualTo(fixtureId(3)) },
            { assertThat(service.getTeamMembers(fixtureId(1))).hasSize(1) }
        )
    }

    private fun assertAccessDenied(action: () -> Unit) {
        val thrown = catchThrowable(action)
        assertThat(thrown).isInstanceOf(AccessDeniedException::class.java)
    }

    private fun assertAppError(expected: AppErrorMessage, action: () -> Unit) {
        val thrown = catchThrowable(action)
        assertAll(
            { assertThat(thrown).isInstanceOf(AppException::class.java) },
            { assertThat((thrown as AppException).error).isEqualTo(expected) }
        )
    }

    private fun team() = Team(fixtureId(1), "Team", null, Instant.EPOCH, null, ownerId = fixtureId(99))

    private fun fixtureId(value: Int): UUID = UUID.nameUUIDFromBytes("team-fixture-$value".toByteArray())

    private fun teamEntity(id: UUID = fixtureId(1), ownerId: UUID = fixtureId(1)) = TeamEntity(
        id = id,
        name = "Team",
        description = null,
        createdAt = Instant.EPOCH,
        updatedAt = null,
        ownerId = ownerId
    )

    private fun user(id: UUID = fixtureId(2)) = User(id, "alice", "secret", Instant.EPOCH, null)

    private fun stubAdminMembership() {
        `when`(teamMemberRepository.findByTeamIdAndUserId(fixtureId(1), fixtureId(1))).thenReturn(adminMemberEntity())
    }

    private fun adminMemberEntity(
        teamId: UUID = fixtureId(1),
        userId: UUID = fixtureId(1),
        ownerId: UUID = fixtureId(1)
    ) = TeamMemberEntity(
        team = teamEntity(teamId, ownerId),
        user = UserEntity(userId, "admin", "secret"),
        role = Role.ADMIN,
        joinedAt = Instant.EPOCH
    )

    private fun memberEntity(
        teamId: UUID = fixtureId(1),
        userId: UUID = fixtureId(2),
        role: Role = Role.READ_WRITE,
        ownerId: UUID = fixtureId(1)
    ) = TeamMemberEntity(
        team = teamEntity(id = teamId, ownerId = ownerId),
        user = UserEntity(userId, "alice", "secret"),
        role = role,
        joinedAt = Instant.EPOCH
    )
}
