package at.fhtw.swen3.paperless.auth

import at.fhtw.swen3.paperless.auth.repository.AccessTokenRepository
import at.fhtw.swen3.paperless.auth.entity.AccessTokenEntity
import at.fhtw.swen3.paperless.auth.service.TokenDigests
import at.fhtw.swen3.paperless.auth.service.LocalTokenIntrospector
import at.fhtw.swen3.paperless.team.entity.TeamEntity
import at.fhtw.swen3.paperless.team.entity.TeamMemberEntity
import at.fhtw.swen3.paperless.team.model.Role
import at.fhtw.swen3.paperless.team.repository.TeamMemberRepository
import at.fhtw.swen3.paperless.team.repository.TeamRepository
import at.fhtw.swen3.paperless.user.entity.UserEntity
import at.fhtw.swen3.paperless.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest(properties = [
    "spring.datasource.url=jdbc:h2:mem:securitytest;DB_CLOSE_DELAY=-1",
    "spring.flyway.locations=classpath:db/migration/h2",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop"
])
@AutoConfigureMockMvc
class SecurityIntegrationTest(
    @Autowired private val mvc: MockMvc,
    @Autowired private val tokens: AccessTokenRepository,
    @Autowired private val users: UserRepository,
    @Autowired private val introspector: LocalTokenIntrospector,
    @Autowired private val teams: TeamRepository,
    @Autowired private val teamMembers: TeamMemberRepository
) {
    @Test
    fun bearer_chain_registration_login_self_access_and_revocation() {
        val alice = register("alice", "páß🔑".repeat(20))
        val bob = register("bobby", "secret123")
        assertThat(users.findById(alice).orElseThrow().password)
            .startsWith("{pbkdf2@SpringSecurity_v5_8}")
            .doesNotContain("páß🔑")
        val originalHash = users.findById(alice).orElseThrow().password

        mvc.perform(get("/auth/me")).andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("WWW-Authenticate", "Bearer"))
        mvc.perform(get("/auth/me").header("Authorization", "Bearer invalid"))
            .andExpect(status().isUnauthorized).andExpect(jsonPath("$.status").value(401))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("WWW-Authenticate", "Bearer"))
        mvc.perform(get("/users")).andExpect(status().isUnauthorized)
        mvc.perform(get("/teams")).andExpect(status().isUnauthorized)
        mvc.perform(get("/auth/me").header("Authorization", "Basic abc"))
            .andExpect(status().isUnauthorized)
        mvc.perform(get("/auth/me").with(user("not-a-uuid")))
            .andExpect(status().isUnauthorized)

        val login = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("""{"username":"alice","password":"${"páß🔑".repeat(20)}"}"""))
            .andExpect(status().isOk).andExpect(jsonPath("$.token_type").value("Bearer"))
            .andReturn().response.contentAsString
        val token = Regex("\"token\":\"([^\"]+)\"").find(login)!!.groupValues[1]
        assertThat(introspector.introspect(token).name).isEqualTo(alice.toString())
        assertThat(tokens.findById(token)).isEmpty
        assertThat(tokens.findById(TokenDigests.sha256(token))).isPresent
        tokens.save(AccessTokenEntity(TokenDigests.sha256("expired"), users.getReferenceById(bob), Instant.EPOCH))
        // An expired credential is rejected even if its digest is still in the database.
        mvc.perform(get("/auth/me").header("Authorization", "Bearer expired"))
            .andExpect(status().isUnauthorized)
        val replacementLogin = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("""{"username":"alice","password":"${"páß🔑".repeat(20)}"}"""))
            .andExpect(status().isOk).andReturn().response.contentAsString
        val replacementToken = Regex("\"token\":\"([^\"]+)\"").find(replacementLogin)!!.groupValues[1]
        assertThat(replacementToken).isNotEqualTo(token)
        assertThat(tokens.countByUserId(alice)).isEqualTo(1)
        mvc.perform(get("/auth/me").header("Authorization", "Bearer $token"))
            .andExpect(status().isUnauthorized)
        mvc.perform(get("/auth/me").header("Authorization", "Bearer $replacementToken"))
            .andExpect(status().isOk).andExpect(jsonPath("$.id").value(alice.toString()))
            .andExpect(jsonPath("$.password").doesNotExist())
        mvc.perform(put("/users/$bob").header("Authorization", "Bearer $replacementToken")
            .contentType(MediaType.APPLICATION_JSON).content("""{"password":"forbidden"}"""))
            .andExpect(status().isForbidden).andExpect(jsonPath("$.status").value(403))

        mvc.perform(put("/users/$alice").header("Authorization", "Bearer $replacementToken")
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk).andExpect(jsonPath("$.username").value("alice"))
        assertThat(users.findById(alice).orElseThrow().password).isEqualTo(originalHash)
        assertThat(users.findById(alice).orElseThrow().username).isEqualTo("alice")

        mvc.perform(put("/users/$alice").header("Authorization", "Bearer $replacementToken")
            .contentType(MediaType.APPLICATION_JSON).content("""{"username":"bobby"}"""))
            .andExpect(status().isConflict)
        assertThat(users.findById(alice).orElseThrow().username).isEqualTo("alice")

        val renamedUsername = "alice-renamed"
        mvc.perform(put("/users/$alice").header("Authorization", "Bearer $replacementToken")
            .contentType(MediaType.APPLICATION_JSON).content("""{"username":"$renamedUsername"}"""))
            .andExpect(status().isOk).andExpect(jsonPath("$.username").value(renamedUsername))
        assertThat(users.findById(alice).orElseThrow().username).isEqualTo(renamedUsername)
        assertThat(users.findById(alice).orElseThrow().password).isEqualTo(originalHash)
        assertThat(tokens.countByUserId(alice)).isEqualTo(1)
        mvc.perform(get("/auth/me").header("Authorization", "Bearer $replacementToken"))
            .andExpect(status().isOk).andExpect(jsonPath("$.id").value(alice.toString()))
            .andExpect(jsonPath("$.username").value(renamedUsername))
        val renamedLogin = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("""{"username":"$renamedUsername","password":"páß🔑${"páß🔑".repeat(19)}"}"""))
            .andExpect(status().isOk).andReturn().response.contentAsString
        val renamedToken = Regex("\"token\":\"([^\"]+)\"").find(renamedLogin)!!.groupValues[1]

        mvc.perform(put("/users/$alice").header("Authorization", "Bearer $replacementToken")
            .contentType(MediaType.APPLICATION_JSON).content("""{"password":"new-unicode-🔒"}"""))
            .andExpect(status().isUnauthorized)
        mvc.perform(put("/users/$alice").header("Authorization", "Bearer $renamedToken")
            .contentType(MediaType.APPLICATION_JSON).content("""{"password":"new-unicode-🔒"}"""))
            .andExpect(status().isOk).andExpect(jsonPath("$.username").value(renamedUsername))
        assertThat(users.findById(alice).orElseThrow().username).isEqualTo(renamedUsername)
        mvc.perform(get("/auth/me").header("Authorization", "Bearer $token"))
            .andExpect(status().isUnauthorized)
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("""{"username":"$renamedUsername","password":"${"páß🔑".repeat(20)}"}"""))
            .andExpect(status().isUnauthorized)
        val newToken = Regex("\"token\":\"([^\"]+)\"").find(mvc.perform(post("/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"username":"$renamedUsername","password":"new-unicode-🔒"}"""))
            .andExpect(status().isOk).andReturn().response.contentAsString)!!.groupValues[1]
        mvc.perform(delete("/users/$bob").header("Authorization", "Bearer $newToken"))
            .andExpect(status().isForbidden)
        mvc.perform(delete("/users/$alice").header("Authorization", "Bearer $newToken"))
            .andExpect(status().isNoContent)
        mvc.perform(get("/auth/me").header("Authorization", "Bearer $newToken"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun concurrent_logins_leave_exactly_one_active_token() {
        val userId = register("race_${System.nanoTime()}", "race-password")
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        val tasks = (1..2).map {
            pool.submit<String> {
                ready.countDown()
                check(start.await(5, TimeUnit.SECONDS))
                val body = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("""{"username":"${users.findById(userId).orElseThrow().username}","password":"race-password"}"""))
                    .andExpect(status().isOk).andReturn().response.contentAsString
                Regex("\"token\":\"([^\"]+)\"").find(body)!!.groupValues[1]
            }
        }
        try {
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue()
            start.countDown()
            val issuedTokens = tasks.map { it.get(20, TimeUnit.SECONDS) }
            assertThat(tokens.countByUserId(userId)).isEqualTo(1)
            assertThat(issuedTokens.count { runCatching { introspector.introspect(it) }.isSuccess }).isEqualTo(1)
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun duplicate_username_registration_returns_conflict() {
        val username = "duplicate_${System.nanoTime()}"
        register(username, "first-password")

        mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON)
            .content("""{"username":"$username","password":"second-password"}"""))
            .andExpect(status().isConflict)
    }

    @Test
    fun registration_and_profile_updates_enforce_username_and_password_boundaries() {
        val initialUsername = boundaryUsername()
        val updatedUsername = boundaryUsername().let { candidate ->
            if (candidate == initialUsername) boundaryUsername() else candidate
        }

        mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON)
            .content("""{"username":"abc","password":"12345678"}"""))
            .andExpect(status().isBadRequest)
        mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON)
            .content("""{"username":"$initialUsername","password":"1234567"}"""))
            .andExpect(status().isBadRequest)

        val createdResponse = mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON)
            .content("""{"username":"$initialUsername","password":"12345678"}"""))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.username").value(initialUsername))
            .andExpect(jsonPath("$.password").doesNotExist())
            .andReturn().response.contentAsString
        val userId = Regex("\"id\":\"([^\"]+)\"").find(createdResponse)!!.groupValues[1]
        val login = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("""{"username":"$initialUsername","password":"12345678"}"""))
            .andExpect(status().isOk).andReturn().response.contentAsString
        val token = Regex("\"token\":\"([^\"]+)\"").find(login)!!.groupValues[1]

        mvc.perform(put("/users/$userId").header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON).content("""{"username":"abc"}"""))
            .andExpect(status().isBadRequest)
        mvc.perform(put("/users/$userId").header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON).content("""{"password":"1234567"}"""))
            .andExpect(status().isBadRequest)

        val updatedResponse = mvc.perform(put("/users/$userId").header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"username":"$updatedUsername","password":"abcdefgh"}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.username").value(updatedUsername))
            .andExpect(jsonPath("$.password").doesNotExist())
            .andReturn().response.contentAsString
        assertThat(updatedResponse).doesNotContain("abcdefgh")
        assertThat(users.findById(UUID.fromString(userId)).orElseThrow().password)
            .startsWith("{pbkdf2@SpringSecurity_v5_8}")
            .doesNotContain("abcdefgh")
    }

    private fun boundaryUsername(): String = UUID.randomUUID().toString().replace("-", "").take(4)

    private fun register(name: String, password: String): UUID =
        mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON)
            .content("""{"username":"$name","password":"$password"}"""))
            .andExpect(status().isCreated).andExpect(jsonPath("$.password").doesNotExist())
            .andReturn().response.contentAsString.let { UUID.fromString(Regex("\"id\":\"([^\"]+)\"").find(it)!!.groupValues[1]) }

    @Test
    fun persistence_generates_uuid_ids_for_users_teams_and_memberships() {
        val user = users.saveAndFlush(
            UserEntity(username = "uuid_${UUID.randomUUID()}", password = "unused", createdAt = Instant.now())
        )
        val userId = requireNotNull(user.id)
        val team = teams.saveAndFlush(
            TeamEntity(name = "UUID team", createdAt = Instant.now(), ownerId = userId)
        )
        val teamId = requireNotNull(team.id)
        val member = teamMembers.saveAndFlush(TeamMemberEntity(team = team, user = user, role = Role.ADMIN))
        val memberId = requireNotNull(member.id)

        assertThat(users.findById(userId)).isPresent
        assertThat(teams.findById(teamId)).isPresent
        assertThat(teamMembers.findById(memberId)).isPresent
    }
}
