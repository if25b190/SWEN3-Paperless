package at.fhtw.swen3.paperless.auth.service

import at.fhtw.swen3.paperless.auth.model.LoginCredentials
import at.fhtw.swen3.paperless.exception.AppErrorMessage
import at.fhtw.swen3.paperless.exception.AppException
import at.fhtw.swen3.paperless.user.model.User
import at.fhtw.swen3.paperless.user.service.UserService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.time.Instant

@ExtendWith(MockitoExtension::class)
class AuthServiceImplTest {

    @Mock
    lateinit var userService: UserService

    @InjectMocks
    lateinit var service: AuthServiceImpl

    @Test
    fun login_and_get_current_user_ok() {
        // given
        val user = user()
        `when`(userService.authenticate("alice", "secret")).thenReturn(user)
        val credentials = LoginCredentials("alice", "secret")

        // when
        val authenticated = service.login(credentials)
        `when`(userService.getById(user.id)).thenReturn(user)
        val current = service.getCurrentUser(authenticated.token)

        // then
        assertAll(
            { assertThat(authenticated.user).isEqualTo(user) },
            { assertThat(authenticated.token).isNotBlank() },
            { assertThat(current).isEqualTo(user) }
        )
    }

    @Test
    fun get_current_user_without_token_ko() {
        // given
        val token: String? = null

        // when / then
        assertAppError(AppErrorMessage.AUTHENTICATION_REQUIRED) { service.getCurrentUser(token) }
    }

    private fun assertAppError(expected: AppErrorMessage, action: () -> Unit) {
        val thrown = catchThrowable(action)
        assertAll(
            { assertThat(thrown).isInstanceOf(AppException::class.java) },
            { assertThat((thrown as AppException).error).isEqualTo(expected) }
        )
    }

    private fun user() = User(1, "alice", "alice@example.com", "secret", Instant.EPOCH, null)
}
