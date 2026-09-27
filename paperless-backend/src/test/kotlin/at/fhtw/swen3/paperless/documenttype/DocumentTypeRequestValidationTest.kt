package at.fhtw.swen3.paperless.documenttype

import at.fhtw.swen3.paperless.documenttype.entity.DocumentTypeEntity
import at.fhtw.swen3.paperless.documenttype.repository.DocumentTypeRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@SpringBootTest(properties = [
    "spring.datasource.url=jdbc:h2:mem:namevalidation;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop"
])
@AutoConfigureMockMvc
class DocumentTypeRequestValidationTest(
    @Autowired private val mvc: MockMvc,
    @Autowired private val repository: DocumentTypeRepository
) {

    @Test
    fun whitespace_only_name_is_rejected_with_bad_request() {
        mvc.perform(
            post("/document-types")
                .with(user("name-validation"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"   "}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun generated_id_is_uuid() {
        val saved = repository.save(DocumentTypeEntity(name = "UUID generated document type"))

        assertThat(saved.id).isInstanceOf(UUID::class.java)
    }
}
