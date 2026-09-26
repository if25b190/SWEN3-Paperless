package at.fhtw.swen3.paperless.team

import at.fhtw.swen3.paperless.user.entity.UserEntity
import at.fhtw.swen3.paperless.user.repository.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

@SpringBootTest(properties = [
    "spring.datasource.url=jdbc:h2:mem:namevalidation;DB_CLOSE_DELAY=-1",
    "spring.flyway.locations=classpath:db/migration/h2",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop"
])
@AutoConfigureMockMvc
class TeamRequestValidationTest(
    @Autowired private val mvc: MockMvc,
    @Autowired private val users: UserRepository
) {

    @Test
    fun whitespace_only_name_is_rejected_with_bad_request() {
        val actorId = requireNotNull(users.saveAndFlush(
            UserEntity(
                username = "team-${UUID.randomUUID()}",
                password = "unused",
                createdAt = Instant.now()
            )
        ).id)

        mvc.perform(
            post("/teams")
                .with(user(actorId.toString()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"   "}""")
        ).andExpect(status().isBadRequest)
    }
}
