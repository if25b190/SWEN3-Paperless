package at.fhtw.swen3.paperless.auth.service

import at.fhtw.swen3.paperless.auth.model.LoginCredentials
import at.fhtw.swen3.paperless.auth.repository.AccessTokenRepository
import at.fhtw.swen3.paperless.user.model.User
import at.fhtw.swen3.paperless.user.service.UserService
import at.fhtw.swen3.paperless.user.repository.UserRepository
import at.fhtw.swen3.paperless.user.entity.UserEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class AuthServiceImplTest {

    @Mock
    lateinit var userService: UserService

    @Mock lateinit var userRepository: UserRepository
    @Mock lateinit var tokenRepository: AccessTokenRepository

    @InjectMocks
    lateinit var service: AuthServiceImpl

    @Test
    fun login_and_get_current_user_ok() {
        // given
        val user = user()
        `when`(userService.authenticate("alice", "secret")).thenReturn(user)
        `when`(userRepository.getReferenceById(userId())).thenReturn(UserEntity(userId(), "alice", "hash"))
        val credentials = LoginCredentials("alice", "secret")

        // when
        val authenticated = service.login(credentials)
        `when`(userService.getById(requireNotNull(user.id))).thenReturn(user)
        val current = service.getCurrentUser(requireNotNull(user.id))

        // then
        assertAll(
            { assertThat(authenticated.user).isEqualTo(user) },
            { assertThat(authenticated.token).isNotBlank() },
            { assertThat(current).isEqualTo(user) }
        )
    }

    private fun userId() = UUID.fromString("7d0f0b2a-4313-4cbc-ae69-52a272c4df10")

    private fun user() = User(userId(), "alice", "secret", Instant.EPOCH, null)
}
