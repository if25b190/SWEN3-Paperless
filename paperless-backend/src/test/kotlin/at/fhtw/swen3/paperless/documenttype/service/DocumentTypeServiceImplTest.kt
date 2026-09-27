package at.fhtw.swen3.paperless.documenttype.service

import at.fhtw.swen3.paperless.documenttype.entity.DocumentTypeEntity
import at.fhtw.swen3.paperless.documenttype.model.DocumentType
import at.fhtw.swen3.paperless.documenttype.repository.DocumentTypeRepository
import at.fhtw.swen3.paperless.exception.AppErrorMessage
import at.fhtw.swen3.paperless.exception.AppException
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
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class DocumentTypeServiceImplTest {

    @Mock
    lateinit var repository: DocumentTypeRepository

    @InjectMocks
    lateinit var service: DocumentTypeServiceImpl

    @Test
    fun search_document_types_ok() {
        // given
        `when`(repository.findAll()).thenReturn(listOf(entity()))

        // when
        val result = service.searchDocumentTypes()

        // then
        assertAll(
            { assertThat(result).hasSize(1) },
            { assertThat(result.single().name).isEqualTo("Invoice") }
        )
    }

    @Test
    fun create_document_type_ok() {
        // given
        `when`(repository.findByName("Invoice")).thenReturn(null)
        `when`(repository.save(any(DocumentTypeEntity::class.java))).thenReturn(entity())

        // when
        val result = service.createDocumentType(documentType())

        // then
        assertAll(
            { assertThat(result.id).isEqualTo(DOCUMENT_TYPE_ID) },
            { assertThat(result.name).isEqualTo("Invoice") },
            { assertThat(result.description).isEqualTo("Bills") }
        )
    }

    @Test
    fun create_duplicate_document_type_ko() {
        // given
        `when`(repository.findByName("Invoice")).thenReturn(entity())

        // when / then
        assertAll(
            { assertAppError(AppErrorMessage.DOCUMENT_TYPE_NAME_ALREADY_EXISTS) { service.createDocumentType(documentType()) } },
            { verify(repository, never()).save(any(DocumentTypeEntity::class.java)) }
        )
    }

    @Test
    fun get_document_type_ok() {
        // given
        `when`(repository.findById(DOCUMENT_TYPE_ID)).thenReturn(Optional.of(entity()))

        // when
        val result = service.getDocumentTypeById(DOCUMENT_TYPE_ID)

        // then
        assertAll(
            { assertThat(result.id).isEqualTo(DOCUMENT_TYPE_ID) },
            { assertThat(result.name).isEqualTo("Invoice") }
        )
    }

    @Test
    fun get_document_type_not_found_ko() {
        // given
        `when`(repository.findById(MISSING_ID)).thenReturn(Optional.empty())

        // when / then
        assertAppError(AppErrorMessage.DOCUMENT_TYPE_NOT_FOUND) { service.getDocumentTypeById(MISSING_ID) }
    }

    @Test
    fun update_document_type_ok() {
        // given
        `when`(repository.findById(DOCUMENT_TYPE_ID)).thenReturn(Optional.of(entity()))
        `when`(repository.findByName("Receipt")).thenReturn(null)
        `when`(repository.save(any(DocumentTypeEntity::class.java))).thenAnswer { it.arguments[0] as DocumentTypeEntity }

        // when
        val result = service.updateDocumentType(DOCUMENT_TYPE_ID, DocumentType(OTHER_ID, "Receipt", "Proof"))

        // then
        assertAll(
            { assertThat(result.id).isEqualTo(DOCUMENT_TYPE_ID) },
            { assertThat(result.name).isEqualTo("Receipt") },
            { assertThat(result.description).isEqualTo("Proof") }
        )
    }

    @Test
    fun update_duplicate_document_type_ko() {
        // given
        `when`(repository.findById(DOCUMENT_TYPE_ID)).thenReturn(Optional.of(entity()))
        `when`(repository.findByName("Receipt")).thenReturn(DocumentTypeEntity(OTHER_ID, "Receipt"))

        // when / then
        assertAll(
            { assertAppError(AppErrorMessage.DOCUMENT_TYPE_NAME_ALREADY_EXISTS) { service.updateDocumentType(DOCUMENT_TYPE_ID, DocumentType(DOCUMENT_TYPE_ID, "Receipt", null)) } },
            { verify(repository, never()).save(any(DocumentTypeEntity::class.java)) }
        )
    }

    @Test
    fun delete_document_type_ok() {
        // given
        val value = entity()
        `when`(repository.findById(DOCUMENT_TYPE_ID)).thenReturn(Optional.of(value))

        // when
        service.deleteDocumentType(DOCUMENT_TYPE_ID)

        // then
        verify(repository).delete(value)
    }

    @Test
    fun delete_document_type_not_found_ko() {
        // given
        `when`(repository.findById(MISSING_ID)).thenReturn(Optional.empty())

        // when / then
        assertAppError(AppErrorMessage.DOCUMENT_TYPE_NOT_FOUND) { service.deleteDocumentType(MISSING_ID) }
    }

    private fun assertAppError(expected: AppErrorMessage, action: () -> Unit) {
        val thrown = catchThrowable(action)
        assertAll(
            { assertThat(thrown).isInstanceOf(AppException::class.java) },
            { assertThat((thrown as AppException).error).isEqualTo(expected) }
        )
    }

    private fun documentType() = DocumentType(null, "Invoice", "Bills")

    private fun entity() = DocumentTypeEntity(DOCUMENT_TYPE_ID, "Invoice", "Bills")

    private companion object {
        val DOCUMENT_TYPE_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val OTHER_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val MISSING_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000003")
    }
}
