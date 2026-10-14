package at.fhtw.swen3.paperless.document.entity

import at.fhtw.swen3.paperless.user.entity.UserEntity
import jakarta.validation.Validation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class DocumentEntityValidationTest {

    @Test
    fun document_metadata_constraints_reject_invalid_values() {
        val violations = validate(
            DocumentEntity(
                title = " ",
                originalFilename = "\t",
                contentType = "",
                fileSize = -1,
                owner = userEntity()
            )
        )

        assertThat(violations.map { it.propertyPath.toString() })
            .contains("title", "originalFilename", "contentType", "fileSize")

        val overlongTitleViolations = validate(
            DocumentEntity(title = "x".repeat(256), owner = userEntity())
        )
        assertThat(overlongTitleViolations.map { it.propertyPath.toString() }).contains("title")
    }

    @Test
    fun generated_id_and_optional_document_fields_remain_valid() {
        val entity = DocumentEntity(
            id = null,
            title = "x".repeat(255),
            originalFilename = "document.pdf",
            contentType = "application/pdf",
            fileSize = 0,
            owner = userEntity(),
            storageKey = null,
            team = null,
            documentType = null
        )

        assertThat(entity.id).isNull()
        assertThat(validate(entity)).isEmpty()
    }

    private fun validate(entity: DocumentEntity) = Validation.buildDefaultValidatorFactory().let { factory ->
        try {
            factory.validator.validate(entity)
        } finally {
            factory.close()
        }
    }

    private fun userEntity() = UserEntity(id = UUID(0L, 1L), username = "uploader", password = "hash")
}
