package at.fhtw.swen3.paperless.auth

import at.fhtw.swen3.paperless.auth.repository.AccessTokenRepository
import at.fhtw.swen3.paperless.document.repository.DocumentRepository
import at.fhtw.swen3.paperless.user.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.nio.file.Files
import java.nio.file.Paths
import java.util.UUID

@SpringBootTest(properties = [
    "spring.datasource.url=jdbc:h2:mem:documentsecuritytest;DB_CLOSE_DELAY=-1",
    "spring.flyway.locations=classpath:db/migration/h2",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop"
])
@AutoConfigureMockMvc
class DocumentSecurityIntegrationTest(
    @Autowired private val mvc: MockMvc,
    @Autowired private val documents: DocumentRepository,
    @Autowired private val users: UserRepository,
    @Autowired private val tokens: AccessTokenRepository
) {
    private val createdDocuments = linkedMapOf<UUID, String>()

    @Test
    fun bearer_chain_enforces_private_and_team_document_access() {
        val suffix = System.nanoTime()
        val owner = registerAndLogin("owner$suffix")
        val writer = registerAndLogin("writer$suffix")
        val reader = registerAndLogin("reader$suffix")
        val outsider = registerAndLogin("outsider$suffix")

        val teamId = createTeam(owner.token, suffix)
        addMember(owner.token, teamId, writer, "READ_WRITE")
        addMember(owner.token, teamId, reader, "READONLY")

        val privateDocument = upload(owner, owner, "ownerprivateprobe")
        val outsiderPrivateDocument = upload(outsider, outsider, "outsiderprivateprobe")
        val adminTeamDocument = upload(owner, owner, "adminteamprobe", teamId)
        val writerTeamDocument = upload(writer, owner, "writerteamprobe", teamId)

        assertThat(documents.findById(privateDocument.id)).isPresent
        mvc.perform(delete("/teams/$teamId").header("Authorization", owner.bearer))
            .andExpect(status().isConflict)
        mvc.perform(delete("/users/${owner.id}").header("Authorization", owner.bearer))
            .andExpect(status().isConflict)
        mvc.perform(get("/auth/me").header("Authorization", owner.bearer))
            .andExpect(status().isOk)

        mvc.perform(get("/documents/${privateDocument.id}").header("Authorization", owner.bearer))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(privateDocument.id.toString()))
        mvc.perform(get("/documents/${privateDocument.id}/download").header("Authorization", owner.bearer))
            .andExpect(status().isOk)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().bytes(privateDocument.content))
        mvc.perform(get("/documents").header("Authorization", owner.bearer).param("size", "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.pagination.total_elements").value(3))
            .andExpect(jsonPath("$.pagination.total_pages").value(3))
        mvc.perform(get("/search").header("Authorization", owner.bearer)
            .param("query", "ownerprivateprobe").param("fuzzy", "false"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.pagination.total_elements").value(1))
            .andExpect(jsonPath("$.items[0].document.id").value(privateDocument.id.toString()))

        mvc.perform(get("/documents/${privateDocument.id}").header("Authorization", outsider.bearer))
            .andExpect(status().isNotFound)
        mvc.perform(get("/documents/${privateDocument.id}").header("Authorization", reader.bearer))
            .andExpect(status().isNotFound)
        mvc.perform(get("/documents/${privateDocument.id}/download").header("Authorization", outsider.bearer))
            .andExpect(status().isNotFound)
        mvc.perform(get("/documents").header("Authorization", outsider.bearer).param("size", "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.pagination.total_elements").value(1))
            .andExpect(jsonPath("$.pagination.total_pages").value(1))
            .andExpect(jsonPath("$.items[0].id").value(outsiderPrivateDocument.id.toString()))
        mvc.perform(get("/search").header("Authorization", outsider.bearer)
            .param("query", "ownerprivateprobe").param("fuzzy", "false"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.pagination.total_elements").value(0))

        mvc.perform(get("/documents/${adminTeamDocument.id}").header("Authorization", reader.bearer))
            .andExpect(status().isOk)
        mvc.perform(get("/documents/${writerTeamDocument.id}/download").header("Authorization", reader.bearer))
            .andExpect(status().isOk)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().bytes(writerTeamDocument.content))
        mvc.perform(get("/documents").header("Authorization", reader.bearer))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.pagination.total_elements").value(2))
        mvc.perform(get("/search").header("Authorization", reader.bearer)
            .param("query", "teamprobe").param("fuzzy", "false"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.pagination.total_elements").value(2))
        mvc.perform(get("/search").header("Authorization", reader.bearer)
            .param("query", "ownerprivateprobe").param("fuzzy", "false"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.pagination.total_elements").value(0))

        mvc.perform(uploadRequest("readonlyuploadprobe", teamId)
            .header("Authorization", reader.bearer))
            .andExpect(status().isForbidden)
        mvc.perform(put("/documents/${adminTeamDocument.id}").header("Authorization", reader.bearer)
            .contentType(MediaType.APPLICATION_JSON).content("""{"title":"forbidden edit"}"""))
            .andExpect(status().isForbidden)
        mvc.perform(delete("/documents/${adminTeamDocument.id}").header("Authorization", reader.bearer))
            .andExpect(status().isForbidden)

        mvc.perform(get("/documents/${adminTeamDocument.id}").header("Authorization", outsider.bearer))
            .andExpect(status().isNotFound)
        mvc.perform(get("/documents/${writerTeamDocument.id}/download").header("Authorization", outsider.bearer))
            .andExpect(status().isNotFound)
        mvc.perform(get("/search").header("Authorization", outsider.bearer)
            .param("query", "teamprobe").param("fuzzy", "false"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.pagination.total_elements").value(0))

        mvc.perform(delete("/teams/$teamId/members/${writer.id}").header("Authorization", owner.bearer))
            .andExpect(status().isNoContent)
        mvc.perform(get("/auth/me").header("Authorization", writer.bearer))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(writer.id.toString()))
        mvc.perform(get("/documents/${writerTeamDocument.id}").header("Authorization", writer.bearer))
            .andExpect(status().isNotFound)
        mvc.perform(get("/documents/${writerTeamDocument.id}/download").header("Authorization", writer.bearer))
            .andExpect(status().isNotFound)
        mvc.perform(get("/documents").header("Authorization", writer.bearer))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.pagination.total_elements").value(0))
        mvc.perform(get("/search").header("Authorization", writer.bearer)
            .param("query", "writerteamprobe").param("fuzzy", "false"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.pagination.total_elements").value(0))
        mvc.perform(get("/documents/${writerTeamDocument.id}").header("Authorization", owner.bearer))
            .andExpect(status().isOk)
    }

    @Test
    fun deleting_user_with_private_document_conflicts_and_rolls_back_account_and_token_deletion() {
        val suffix = System.nanoTime()
        val account = registerAndLogin("privateowner$suffix")
        mvc.perform(get("/teams").header("Authorization", account.bearer))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items").isEmpty)

        val privateDocument = upload(account, account, "deleteownerprobe")
        assertThat(tokens.countByUserId(account.id)).isEqualTo(1)

        mvc.perform(delete("/users/${account.id}").header("Authorization", account.bearer))
            .andExpect(status().isConflict)

        assertThat(users.findById(account.id)).isPresent
        assertThat(tokens.countByUserId(account.id)).isEqualTo(1)
        assertThat(documents.findById(privateDocument.id)).isPresent
        mvc.perform(get("/auth/me").header("Authorization", account.bearer))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(account.id.toString()))
        mvc.perform(get("/documents/${privateDocument.id}").header("Authorization", account.bearer))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(privateDocument.id.toString()))
    }

    @AfterEach
    fun cleanupUploadedDocuments() {
        createdDocuments.forEach { (id, cleanupToken) ->
            val file = documents.findById(id).orElse(null)?.storageKey?.let { key ->
                Paths.get(System.getProperty("java.io.tmpdir"), "paperless-documents", key.substringAfterLast('/'))
            }
            val deleted = runCatching {
                mvc.perform(delete("/documents/$id").header("Authorization", "Bearer $cleanupToken"))
                    .andReturn().response.status == 204
            }.getOrDefault(false)
            if (!deleted) file?.let { runCatching { Files.deleteIfExists(it) } }
        }
        createdDocuments.clear()
    }

    private fun registerAndLogin(username: String): Account {
        val password = "document-security-password"
        val registration = mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON)
            .content("""{"username":"$username","password":"$password"}"""))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.password").doesNotExist())
            .andReturn().response.contentAsString
        val id = jsonUuid(registration, "id")
        val login = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("""{"username":"$username","password":"$password"}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.token_type").value("Bearer"))
            .andReturn().response.contentAsString
        val token = Regex("\"token\":\"([^\"]+)\"").find(login)!!.groupValues[1]
        return Account(id, token)
    }

    private fun createTeam(token: String, suffix: Long): UUID =
        mvc.perform(post("/teams").header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"name":"Document security $suffix"}"""))
            .andExpect(status().isCreated)
            .andReturn().response.contentAsString.let { jsonUuid(it, "id") }

    private fun addMember(token: String, teamId: UUID, member: Account, role: String) {
        mvc.perform(post("/teams/$teamId/members").header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"user_id":"${member.id}","role":"$role"}"""))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.role").value(role))
    }

    private fun upload(
        uploader: Account,
        cleanupAccount: Account,
        title: String,
        teamId: UUID? = null
    ): UploadedDocument {
        val content = "$title\nGenerated integration PDF payload".toByteArray()
        val result = mvc.perform(uploadRequest(title, teamId).header("Authorization", uploader.bearer))
            .andExpect(status().isCreated)
            .andReturn()
        val body = result.response.contentAsString
        val id = jsonUuid(body, "id")
        createdDocuments[id] = cleanupAccount.token
        assertThat(result.response.getHeader("Location")).isEqualTo("/documents/$id")
        jsonPath("$.title").value(title).match(result)
        jsonPath("$.owner_id").value(uploader.id.toString()).match(result)
        jsonPath("$.original_filename").value("$title.pdf").match(result)
        jsonPath("$.content_type").value(MediaType.APPLICATION_PDF_VALUE).match(result)
        jsonPath("$.file_size").value(content.size).match(result)
        jsonPath("$.status").value("PENDING").match(result)
        if (teamId != null) jsonPath("$.team_id").value(teamId.toString()).match(result)
        else jsonPath("$.team_id").value(org.hamcrest.Matchers.nullValue()).match(result)
        return UploadedDocument(id, content)
    }

    private fun uploadRequest(title: String, teamId: UUID?): MockMultipartHttpServletRequestBuilder {
        val request = multipart("/documents")
            .file(MockMultipartFile("document", "$title.pdf", MediaType.APPLICATION_PDF_VALUE,
                "$title\nGenerated integration PDF payload".toByteArray()))
            .param("title", title)
        if (teamId != null) {
            request.param("team_id", teamId.toString())
        }
        return request
    }

    private fun jsonUuid(json: String, field: String): UUID =
        Regex("\"$field\":\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1)?.let(UUID::fromString)
            ?: error("Missing UUID '$field' in response: $json")

    private data class Account(val id: UUID, val token: String) {
        val bearer: String get() = "Bearer $token"
    }

    private data class UploadedDocument(val id: UUID, val content: ByteArray)
}
