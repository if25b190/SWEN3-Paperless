package at.fhtw.swen3.paperless.auth.model

import at.fhtw.swen3.paperless.exception.AuthenticationRequiredException
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.authentication.TestingAuthenticationToken

class AuthenticatedPrincipalTest {

    @Test
    fun resolves_user_id_from_authentication() {
        val id = UUID.randomUUID()

        val resolved = AuthenticatedPrincipal.userId(TestingAuthenticationToken(id.toString(), null))

        assertThat(resolved).isEqualTo(id)
    }

    @Test
    fun accepts_uppercase_canonical_uuid_name() {
        val id = UUID.randomUUID()

        val resolved = AuthenticatedPrincipal.userId(TestingAuthenticationToken(id.toString().uppercase(), null))

        assertThat(resolved).isEqualTo(id)
    }

    @Test
    fun rejects_malformed_name() {
        assertThrows<AuthenticationRequiredException> {
            AuthenticatedPrincipal.userId(TestingAuthenticationToken("not-a-uuid", null))
        }
    }

    @Test
    fun rejects_non_canonical_uuid_name() {
        val id = UUID.randomUUID()

        // A UUID-parsable string that is not the canonical form of the parsed UUID.
        assertThrows<AuthenticationRequiredException> {
            AuthenticatedPrincipal.userId(TestingAuthenticationToken("${id}extra", null))
        }
    }

    @Test
    fun rejects_missing_name() {
        assertThrows<AuthenticationRequiredException> {
            AuthenticatedPrincipal.userId(name = null)
        }
    }
}
