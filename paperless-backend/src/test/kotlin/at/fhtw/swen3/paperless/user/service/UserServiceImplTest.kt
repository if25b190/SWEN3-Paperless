package at.fhtw.swen3.paperless.user.service

import at.fhtw.swen3.paperless.auth.repository.AccessTokenRepository
import at.fhtw.swen3.paperless.exception.AppErrorMessage
import at.fhtw.swen3.paperless.exception.AppException
import at.fhtw.swen3.paperless.team.entity.TeamEntity
import at.fhtw.swen3.paperless.team.entity.TeamMemberEntity
import at.fhtw.swen3.paperless.team.model.Role
import at.fhtw.swen3.paperless.team.repository.TeamMemberRepository
import at.fhtw.swen3.paperless.team.repository.TeamRepository
import at.fhtw.swen3.paperless.user.entity.UserEntity
import at.fhtw.swen3.paperless.user.model.User
import at.fhtw.swen3.paperless.user.repository.UserRepository
import jakarta.validation.ConstraintViolationException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Spy
import org.mockito.Mockito.`when`
import org.mockito.Mockito.never
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Instant
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class UserServiceImplTest {

    @Mock
    lateinit var userRepository: UserRepository

    @Mock
    lateinit var teamMemberRepository: TeamMemberRepository

    @Mock lateinit var teamRepository: TeamRepository

    @Mock lateinit var tokenRepository: AccessTokenRepository

    @Spy val encoder: PasswordEncoder = org.springframework.security.crypto.password.DelegatingPasswordEncoder(
        "pbkdf2@SpringSecurity_v5_8",
        mapOf("pbkdf2@SpringSecurity_v5_8" to org.springframework.security.crypto.password.Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8())
    )

    @BeforeEach
    fun actor() {
        SecurityContextHolder.getContext().authentication = TestingAuthenticationToken(fixtureId(1).toString(), null, "ROLE_USER")
    }

    @AfterEach
    fun clearActor() = SecurityContextHolder.clearContext()

    @InjectMocks
    lateinit var service: UserServiceImpl

    @Test
    fun search_users_ok() {
        // given
        `when`(userRepository.findAll()).thenReturn(listOf(entity()))

        // when
        val result = service.searchUsers()

        // then
        assertAll(
            { assertThat(result).hasSize(1) },
            { assertThat(result.single().username).isEqualTo("alice") }
        )
    }

    @Test
    fun create_user_ok() {
        // given
        val user = user()
        `when`(userRepository.findByUsername(user.username)).thenReturn(null)
        `when`(userRepository.save(any(UserEntity::class.java))).thenAnswer { it.arguments[0] as UserEntity }

        // when
        val result = service.create(user)

        // then
        assertAll(
            { assertThat(result.id).isEqualTo(fixtureId(1)) },
            { assertThat(result.username).isEqualTo(user.username) },
            { assertThat(result.password).startsWith("{pbkdf2@SpringSecurity_v5_8}") },
            { assertThat(encoder.matches(user.password, result.password)).isTrue() }
        )
    }

    @Test
    fun create_user_accepts_minimum_username_and_password_lengths() {
        val user = user(username = "abcd", password = "12345678")
        `when`(userRepository.findByUsername(user.username)).thenReturn(null)
        `when`(userRepository.save(any(UserEntity::class.java))).thenAnswer { it.arguments[0] as UserEntity }

        val result = service.create(user)

        assertAll(
            { assertThat(result.username).isEqualTo("abcd") },
            { assertThat(encoder.matches("12345678", result.password)).isTrue() }
        )
    }

    @Test
    fun create_user_rejects_invalid_username_and_password_lengths() {
        assertConstraintViolation { service.create(user(username = "abc")) }
        assertConstraintViolation { service.create(user(username = "   ")) }
        assertConstraintViolation { service.create(user(username = "u".repeat(51))) }
        assertConstraintViolation { service.create(user(password = "1234567")) }
        assertConstraintViolation { service.create(user(password = "p".repeat(101))) }

        verify(userRepository, never()).save(any(UserEntity::class.java))
    }

    @Test
    fun create_duplicate_username_ko() {
        // given
        val user = user()
        `when`(userRepository.findByUsername(user.username)).thenReturn(entity())

        // when / then
        assertAll(
            { assertAppError(AppErrorMessage.USERNAME_ALREADY_EXISTS) { service.create(user) } },
            { verify(userRepository, never()).save(any(UserEntity::class.java)) }
        )
    }

    @Test
    fun get_user_ok() {
        // given
        `when`(userRepository.findById(fixtureId(1))).thenReturn(Optional.of(entity()))

        // when
        val result = service.getById(fixtureId(1))

        // then
        assertAll(
            { assertThat(result.id).isEqualTo(fixtureId(1)) },
            { assertThat(result.username).isEqualTo("alice") }
        )
    }

    @Test
    fun get_user_not_found_ko() {
        // given
        `when`(userRepository.findById(fixtureId(42))).thenReturn(Optional.empty())

        // when / then
        assertAppError(AppErrorMessage.USER_NOT_FOUND) { service.getById(fixtureId(42)) }
    }

    @Test
    fun update_without_username_or_password_preserves_profile_and_password() {
        // given
        val originalHash = encoder.encode("stored password")!!
        `when`(userRepository.findLockedById(fixtureId(1))).thenReturn(entity(originalHash))
        `when`(userRepository.save(any(UserEntity::class.java))).thenAnswer { it.arguments[0] as UserEntity }

        // when
        val result = service.update(fixtureId(1), null, null)

        // then
        assertAll(
            { assertThat(result.username).isEqualTo("alice") },
            { assertThat(result.password).isEqualTo(originalHash) },
            { assertThat(result.updatedAt).isNotNull() }
        )
    }

    @Test
    fun update_username_changes_profile_without_changing_password_or_revoking_tokens() {
        val originalHash = encoder.encode("stored password")!!
        `when`(userRepository.findLockedById(fixtureId(1))).thenReturn(entity(originalHash))
        `when`(userRepository.findByUsername("alice-renamed")).thenReturn(null)
        `when`(userRepository.save(any(UserEntity::class.java))).thenAnswer { it.arguments[0] as UserEntity }

        val result = service.update(fixtureId(1), "alice-renamed", null)

        assertAll(
            { assertThat(result.username).isEqualTo("alice-renamed") },
            { assertThat(result.password).isEqualTo(originalHash) },
            { verify(userRepository).findByUsername("alice-renamed") },
            { verify(tokenRepository, never()).deleteByUserId(fixtureId(1)) }
        )
    }

    @Test
    fun update_accepts_minimum_username_and_password_lengths() {
        `when`(userRepository.findLockedById(fixtureId(1))).thenReturn(entity())
        `when`(userRepository.findByUsername("abcd")).thenReturn(null)
        `when`(userRepository.save(any(UserEntity::class.java))).thenAnswer { it.arguments[0] as UserEntity }

        val result = service.update(fixtureId(1), "abcd", "12345678")

        assertAll(
            { assertThat(result.username).isEqualTo("abcd") },
            { assertThat(encoder.matches("12345678", result.password)).isTrue() }
        )
    }

    @Test
    fun update_rejects_invalid_provided_username_and_password_lengths() {
        assertConstraintViolation { service.update(fixtureId(1), "abc", null) }
        assertConstraintViolation { service.update(fixtureId(1), "u".repeat(51), null) }
        assertConstraintViolation { service.update(fixtureId(1), "   ", null) }
        assertConstraintViolation { service.update(fixtureId(1), null, "1234567") }
        assertConstraintViolation { service.update(fixtureId(1), null, "p".repeat(101)) }

        verify(userRepository, never()).findLockedById(fixtureId(1))
        verify(userRepository, never()).save(any(UserEntity::class.java))
    }

    @Test
    fun update_duplicate_username_is_rejected_without_saving_or_revoking_tokens() {
        `when`(userRepository.findLockedById(fixtureId(1))).thenReturn(entity())
        `when`(userRepository.findByUsername("bobby")).thenReturn(UserEntity(fixtureId(2), "bobby", "hash"))

        assertAll(
            { assertAppError(AppErrorMessage.USERNAME_ALREADY_EXISTS) { service.update(fixtureId(1), "bobby", null) } },
            { verify(userRepository, never()).save(any(UserEntity::class.java)) },
            { verify(tokenRepository, never()).deleteByUserId(fixtureId(1)) }
        )
    }

    @Test
    fun update_user_not_found_ko() {
        SecurityContextHolder.getContext().authentication = TestingAuthenticationToken(fixtureId(42).toString(), null, "ROLE_USER")
        // given
        `when`(userRepository.findLockedById(fixtureId(42))).thenReturn(null)

        // when / then
        assertAll(
            { assertAppError(AppErrorMessage.USER_NOT_FOUND) { service.update(fixtureId(42), null, null) } },
            { verify(userRepository, never()).save(any(UserEntity::class.java)) }
        )
    }

    @Test
    fun update_password_hashes_and_revokes_tokens() {
        `when`(userRepository.findLockedById(fixtureId(1))).thenReturn(entity())
        `when`(userRepository.save(any(UserEntity::class.java))).thenAnswer { it.arguments[0] as UserEntity }

        val result = service.update(fixtureId(1), null, "new secret")

        assertAll(
            { assertThat(encoder.matches("new secret", result.password)).isTrue() },
            { assertThat(result.username).isEqualTo("alice") },
            { verify(encoder).encode("new secret") },
            { verify(tokenRepository).deleteByUserId(fixtureId(1)) }
        )
    }

    @Test
    fun delete_user_ok() {
        // given
        `when`(userRepository.findLockedById(fixtureId(1))).thenReturn(entity())
        `when`(teamMemberRepository.findByUserId(fixtureId(1))).thenReturn(emptyList())

        // when
        service.delete(fixtureId(1))

        // then
        verify(userRepository).deleteById(fixtureId(1))
    }

    @Test
    fun delete_user_not_found_ko() {
        SecurityContextHolder.getContext().authentication = TestingAuthenticationToken(fixtureId(42).toString(), null, "ROLE_USER")
        // given
        `when`(userRepository.findLockedById(fixtureId(42))).thenReturn(null)

        // when / then
        assertAppError(AppErrorMessage.USER_NOT_FOUND) { service.delete(fixtureId(42)) }
    }

    @Test
    fun delete_last_team_admin_ko() {
        val membership = TeamMemberEntity(team = TeamEntity(fixtureId(3), "Team"), user = entity(), role = Role.ADMIN)
        `when`(userRepository.findLockedById(fixtureId(1))).thenReturn(entity())
        `when`(teamMemberRepository.findByUserId(fixtureId(1))).thenReturn(listOf(membership))
        `when`(teamRepository.findLockedById(fixtureId(3))).thenReturn(TeamEntity(fixtureId(3), "Team"))
        `when`(teamMemberRepository.findByTeamId(fixtureId(3))).thenReturn(listOf(membership))

        assertAppError(AppErrorMessage.LAST_TEAM_ADMIN) { service.delete(fixtureId(1)) }

        verify(userRepository, never()).deleteById(fixtureId(1))
    }

    @Test
    fun delete_team_owner_is_rejected_even_with_other_admins_and_no_membership() {
        `when`(userRepository.findLockedById(fixtureId(1))).thenReturn(entity())
        `when`(teamMemberRepository.findByUserId(fixtureId(1))).thenReturn(emptyList())
        `when`(teamRepository.findAll()).thenReturn(listOf(TeamEntity(fixtureId(3), "Owned", ownerId = fixtureId(1))))
        `when`(teamRepository.findLockedById(fixtureId(3))).thenReturn(TeamEntity(fixtureId(3), "Owned", ownerId = fixtureId(1)))
        `when`(teamMemberRepository.findByTeamId(fixtureId(3))).thenReturn(
            listOf(
                TeamMemberEntity(
                    team = TeamEntity(fixtureId(3), "Owned", ownerId = fixtureId(1)),
                    user = UserEntity(fixtureId(2), "bob", "hash"),
                    role = Role.ADMIN
                ),
                TeamMemberEntity(
                    team = TeamEntity(fixtureId(3), "Owned", ownerId = fixtureId(1)),
                    user = UserEntity(fixtureId(4), "carol", "hash"),
                    role = Role.ADMIN
                )
            )
        )

        assertAppError(AppErrorMessage.USER_OWNS_TEAM) { service.delete(fixtureId(1)) }

        verify(userRepository, never()).deleteById(fixtureId(1))
        verify(teamMemberRepository, never()).deleteAll(any())
    }

    @Test
    fun delete_locks_associated_teams_in_sorted_order() {
        val firstMembership = TeamMemberEntity(team = TeamEntity(fixtureId(9), "Nine"), user = entity(), role = Role.READ_WRITE)
        val secondMembership = TeamMemberEntity(team = TeamEntity(fixtureId(3), "Three"), user = entity(), role = Role.READ_WRITE)
        `when`(userRepository.findLockedById(fixtureId(1))).thenReturn(entity())
        `when`(teamMemberRepository.findByUserId(fixtureId(1))).thenReturn(listOf(firstMembership, secondMembership), listOf(firstMembership, secondMembership))
        `when`(teamRepository.findLockedById(fixtureId(3))).thenReturn(TeamEntity(fixtureId(3), "Three"))
        `when`(teamRepository.findLockedById(fixtureId(9))).thenReturn(TeamEntity(fixtureId(9), "Nine"))
        `when`(teamMemberRepository.findByTeamId(fixtureId(3))).thenReturn(listOf(secondMembership))
        `when`(teamMemberRepository.findByTeamId(fixtureId(9))).thenReturn(listOf(firstMembership))

        service.delete(fixtureId(1))

        val ordered = inOrder(teamRepository, teamMemberRepository)
        ordered.verify(teamRepository).findLockedById(fixtureId(3))
        ordered.verify(teamRepository).findLockedById(fixtureId(9))
        ordered.verify(teamMemberRepository).findByTeamId(fixtureId(3))
        ordered.verify(teamMemberRepository).findByTeamId(fixtureId(9))
    }

    @Test
    fun get_user_teams_ok() {
        // given
        val membership = TeamMemberEntity(team = TeamEntity(fixtureId(3), "Team"), user = entity(), role = Role.READ_WRITE)
        `when`(userRepository.findById(fixtureId(1))).thenReturn(Optional.of(entity()))
        `when`(teamMemberRepository.findByUserId(fixtureId(1))).thenReturn(listOf(membership))

        // when
        val result = service.getTeams(fixtureId(1))

        // then
        assertAll(
            { assertThat(result).hasSize(1) },
            { assertThat(result.single().team.id).isEqualTo(fixtureId(3)) },
            { assertThat(result.single().role).isEqualTo(Role.READ_WRITE) }
        )
    }

    @Test
    fun get_other_user_teams_is_forbidden() {
        val thrown = catchThrowable { service.getTeams(fixtureId(2)) }

        assertThat(thrown).isInstanceOf(AccessDeniedException::class.java)
        verify(userRepository, never()).findById(fixtureId(2))
        verify(teamMemberRepository, never()).findByUserId(fixtureId(2))
    }

    @Test
    fun authenticate_user_ok() {
        // given
        val hashed = encoder.encode("secret")!!
        `when`(userRepository.findLockedByUsername("alice")).thenReturn(UserEntity(fixtureId(1), "alice", hashed, Instant.EPOCH, null))

        // when
        val result = service.authenticate("alice", "secret")

        // then
        assertThat(result.password).isEqualTo(hashed)
    }

    @Test
    fun authenticate_with_wrong_password_ko() {
        // given
        val hashed = encoder.encode("secret")!!
        `when`(userRepository.findLockedByUsername("alice")).thenReturn(UserEntity(fixtureId(1), "alice", hashed, Instant.EPOCH, null))

        // when / then
        assertAppError(AppErrorMessage.INVALID_CREDENTIALS) { service.authenticate("alice", "wrong") }
    }

    @Test
    fun legacy_plaintext_password_is_not_accepted() {
        `when`(userRepository.findLockedByUsername("alice")).thenReturn(entity())
        assertAppError(AppErrorMessage.INVALID_CREDENTIALS) { service.authenticate("alice", "secret") }
    }

    private fun assertAppError(expected: AppErrorMessage, action: () -> Unit) {
        val thrown = catchThrowable(action)
        assertAll(
            { assertThat(thrown).isInstanceOf(AppException::class.java) },
            { assertThat((thrown as AppException).error).isEqualTo(expected) }
        )
    }

    private fun assertConstraintViolation(action: () -> Unit) {
        val thrown = catchThrowable(action)
        assertThat(thrown).isInstanceOf(ConstraintViolationException::class.java)
    }

    private fun user(username: String = "alice", password: String = "secret123") = User(fixtureId(1), username, password, Instant.EPOCH, null)

    private fun fixtureId(value: Int): UUID = UUID.nameUUIDFromBytes("user-fixture-$value".toByteArray())

    private fun entity(password: String = "secret") = UserEntity(fixtureId(1), "alice", password, Instant.EPOCH, null)
}
