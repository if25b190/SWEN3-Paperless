package at.fhtw.swen3.paperless.auth.service

import at.fhtw.swen3.paperless.auth.model.AccessToken
import at.fhtw.swen3.paperless.auth.model.LoginCredentials
import at.fhtw.swen3.paperless.auth.persistence.AccessTokenPersistence
import at.fhtw.swen3.paperless.exception.AppException
import at.fhtw.swen3.paperless.exception.InvalidCredentialsException
import at.fhtw.swen3.paperless.exception.UserNotFoundException
import at.fhtw.swen3.paperless.testsupport.any
import at.fhtw.swen3.paperless.user.model.User
import at.fhtw.swen3.paperless.user.persistence.UserPersistence
import java.time.Instant
import java.util.Optional
import java.util.UUID
import kotlin.reflect.KClass
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Spy
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.security.crypto.password.DelegatingPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder

@ExtendWith(MockitoExtension::class, OutputCaptureExtension::class)
class AuthServiceImplTest {
    @Mock lateinit var userRepository: UserPersistence
    @Mock lateinit var tokenRepository: AccessTokenPersistence
    @Spy val passwordEncoder: PasswordEncoder = DelegatingPasswordEncoder(
        "pbkdf2@SpringSecurity_v5_8",
        mapOf("pbkdf2@SpringSecurity_v5_8" to Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8())
    )
    @InjectMocks lateinit var service: AuthServiceImpl

    @Test
    fun login_locks_user_and_replaces_session_before_returning_token_ok(output: CapturedOutput) {
        // given
        val user = user(requireNotNull(passwordEncoder.encode("secret")))
        `when`(userRepository.findLockedByUsername("alice")).thenReturn(user)

        // when
        val authenticated = service.login(LoginCredentials("alice", "secret"))

        // then
        assertAll(
            { assertThat(authenticated.user).isEqualTo(user) },
            { assertThat(authenticated.token).isNotBlank() }
        )
        val ordered = inOrder(userRepository, tokenRepository)
        ordered.verify(userRepository).findLockedByUsername("alice")
        ordered.verify(tokenRepository).deleteByUserId(userId())
        ordered.verify(tokenRepository).save(any(AccessToken::class.java))
        assertThat(output.all)
            .contains("[AUTH] - ACTION: login: userId: ${userId()}")
            .doesNotContain("alice", "secret", authenticated.token, user.password)
        val stored = savedToken()
        assertAll(
            { assertThat(stored.digest).isEqualTo(TokenDigests.sha256(authenticated.token)) },
            { assertThat(stored.userId).isEqualTo(userId()) },
            { assertThat(stored.expiresAt).isAfter(Instant.now()) }
        )
    }

    @Test
    fun get_current_user_ok() {
        // given
        val user = user()
        `when`(userRepository.findById(userId())).thenReturn(Optional.of(user))

        // when / then
        assertThat(service.getCurrentUser(userId())).isEqualTo(user)
    }

    @Test
    fun get_missing_current_user_ko() {
        // when / then
        assertAppError(UserNotFoundException::class) { service.getCurrentUser(userId()) }
    }

    @Test
    fun wrong_password_does_not_replace_existing_session_ko() {
        // given
        val hashed = requireNotNull(passwordEncoder.encode("secret"))
        `when`(userRepository.findLockedByUsername("alice")).thenReturn(user(hashed))

        // when / then
        assertAppError(InvalidCredentialsException::class) { service.login(LoginCredentials("alice", "wrong")) }
        verifyNoInteractions(tokenRepository)
    }

    @Test
    fun missing_user_does_not_create_token_ko() {
        // when / then
        assertAppError(InvalidCredentialsException::class) { service.login(LoginCredentials("missing", "secret")) }
        verifyNoInteractions(tokenRepository)
    }

    @Test
    fun legacy_plaintext_password_is_not_accepted_ko() {
        // given
        `when`(userRepository.findLockedByUsername("alice")).thenReturn(user("secret"))

        // when / then
        assertAppError(InvalidCredentialsException::class) { service.login(LoginCredentials("alice", "secret")) }
        verifyNoInteractions(tokenRepository)
    }

    private fun assertAppError(expected: KClass<out AppException>, action: () -> Unit) {
        val thrown = catchThrowable(action)
        assertThat(thrown).isInstanceOf(expected.java)
    }

    private fun savedToken(): AccessToken =
        org.mockito.Mockito.mockingDetails(tokenRepository).invocations
            .single { it.method.name == "save" }.arguments[0] as AccessToken

    private fun userId() = UUID.fromString("7d0f0b2a-4313-4cbc-ae69-52a272c4df10")

    private fun user(password: String = "unused") = User(userId(), "alice", password, Instant.EPOCH, null)
}
