package at.fhtw.swen3.paperless.user.service

import at.fhtw.swen3.paperless.exception.AppErrorMessage
import at.fhtw.swen3.paperless.exception.AppException
import at.fhtw.swen3.paperless.team.entity.TeamEntity
import at.fhtw.swen3.paperless.team.entity.TeamMemberEntity
import at.fhtw.swen3.paperless.team.model.Role
import at.fhtw.swen3.paperless.team.repository.TeamMemberRepository
import at.fhtw.swen3.paperless.user.entity.UserEntity
import at.fhtw.swen3.paperless.user.model.User
import at.fhtw.swen3.paperless.user.repository.UserRepository
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
class UserServiceImplTest {

    @Mock
    lateinit var userRepository: UserRepository

    @Mock
    lateinit var teamMemberRepository: TeamMemberRepository

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
        `when`(userRepository.findAll()).thenReturn(emptyList())
        `when`(userRepository.save(any(UserEntity::class.java))).thenReturn(entity())

        // when
        val result = service.create(user)

        // then
        assertAll(
            { assertThat(result.id).isEqualTo(1) },
            { assertThat(result.username).isEqualTo(user.username) },
            { assertThat(result.email).isEqualTo(user.email) }
        )
    }

    @Test
    fun create_duplicate_email_ko() {
        // given
        val user = user()
        `when`(userRepository.findByUsername(user.username)).thenReturn(null)
        `when`(userRepository.findAll()).thenReturn(listOf(UserEntity(2, "bob", user.email, "secret")))

        // when / then
        assertAll(
            { assertAppError(AppErrorMessage.EMAIL_ALREADY_EXISTS) { service.create(user) } },
            { verify(userRepository, never()).save(any(UserEntity::class.java)) }
        )
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
        `when`(userRepository.findById(1)).thenReturn(Optional.of(entity()))

        // when
        val result = service.getById(1)

        // then
        assertAll(
            { assertThat(result.id).isEqualTo(1) },
            { assertThat(result.username).isEqualTo("alice") }
        )
    }

    @Test
    fun get_user_not_found_ko() {
        // given
        `when`(userRepository.findById(42)).thenReturn(Optional.empty())

        // when / then
        assertAppError(AppErrorMessage.USER_NOT_FOUND) { service.getById(42) }
    }

    @Test
    fun update_user_ok() {
        // given
        val update = user().copy(email = "new@example.com")
        `when`(userRepository.findById(1)).thenReturn(Optional.of(entity()))
        `when`(userRepository.findAll()).thenReturn(emptyList())
        `when`(userRepository.save(any(UserEntity::class.java))).thenAnswer { it.arguments[0] as UserEntity }

        // when
        val result = service.update(update)

        // then
        assertAll(
            { assertThat(result.email).isEqualTo("new@example.com") },
            { assertThat(result.username).isEqualTo("alice") },
            { assertThat(result.updatedAt).isNotNull() }
        )
    }

    @Test
    fun update_user_not_found_ko() {
        // given
        `when`(userRepository.findById(42)).thenReturn(Optional.empty())

        // when / then
        assertAll(
            { assertAppError(AppErrorMessage.USER_NOT_FOUND) { service.update(user().copy(id = 42)) } },
            { verify(userRepository, never()).save(any(UserEntity::class.java)) }
        )
    }

    @Test
    fun update_duplicate_username_ko() {
        // given
        val update = user().copy(username = "bob")
        `when`(userRepository.findById(1)).thenReturn(Optional.of(entity()))
        `when`(userRepository.findByUsername("bob")).thenReturn(UserEntity(2, "bob", "bob@example.com", "secret"))

        // when / then
        assertAll(
            { assertAppError(AppErrorMessage.USERNAME_ALREADY_EXISTS) { service.update(update) } },
            { verify(userRepository, never()).save(any(UserEntity::class.java)) }
        )
    }

    @Test
    fun update_duplicate_email_ko() {
        // given
        val update = user().copy(email = "bob@example.com")
        `when`(userRepository.findById(1)).thenReturn(Optional.of(entity()))
        `when`(userRepository.findAll()).thenReturn(listOf(UserEntity(2, "bob", "bob@example.com", "secret")))

        // when / then
        assertAll(
            { assertAppError(AppErrorMessage.EMAIL_ALREADY_EXISTS) { service.update(update) } },
            { verify(userRepository, never()).save(any(UserEntity::class.java)) }
        )
    }

    @Test
    fun delete_user_ok() {
        // given
        `when`(userRepository.findById(1)).thenReturn(Optional.of(entity()))

        // when
        service.delete(1)

        // then
        verify(userRepository).deleteById(1)
    }

    @Test
    fun delete_user_not_found_ko() {
        // given
        `when`(userRepository.findById(42)).thenReturn(Optional.empty())

        // when / then
        assertAppError(AppErrorMessage.USER_NOT_FOUND) { service.delete(42) }
    }

    @Test
    fun get_user_teams_ok() {
        // given
        val membership = TeamMemberEntity(team = TeamEntity(3, "Team"), user = entity(), role = Role.MEMBER)
        `when`(userRepository.findById(1)).thenReturn(Optional.of(entity()))
        `when`(teamMemberRepository.findByUserId(1)).thenReturn(listOf(membership))

        // when
        val result = service.getTeams(1)

        // then
        assertAll(
            { assertThat(result).hasSize(1) },
            { assertThat(result.single().team.id).isEqualTo(3) },
            { assertThat(result.single().role).isEqualTo(Role.MEMBER) }
        )
    }

    @Test
    fun authenticate_user_ok() {
        // given
        `when`(userRepository.findByUsername("alice")).thenReturn(entity())

        // when
        val result = service.authenticate("alice", "secret")

        // then
        assertThat(result).isEqualTo(user())
    }

    @Test
    fun authenticate_with_wrong_password_ko() {
        // given
        `when`(userRepository.findByUsername("alice")).thenReturn(entity())

        // when / then
        assertAppError(AppErrorMessage.INVALID_CREDENTIALS) { service.authenticate("alice", "wrong") }
    }

    private fun assertAppError(expected: AppErrorMessage, action: () -> Unit) {
        val thrown = catchThrowable(action)
        assertAll(
            { assertThat(thrown).isInstanceOf(AppException::class.java) },
            { assertThat((thrown as AppException).error).isEqualTo(expected) }
        )
    }

    private fun user() = User(1, "alice", "alice@example.com", "secret", Instant.EPOCH, null)

    private fun entity() = UserEntity(1, "alice", "alice@example.com", "secret", Instant.EPOCH, null)
}
