package at.fhtw.swen3.paperless.document.service

import at.fhtw.swen3.paperless.correspondent.entity.CorrespondentEntity
import at.fhtw.swen3.paperless.document.entity.DocumentEntity
import at.fhtw.swen3.paperless.document.model.DocumentUpdate
import at.fhtw.swen3.paperless.document.model.DocumentUpload
import at.fhtw.swen3.paperless.document.model.ProcessingStatus
import at.fhtw.swen3.paperless.document.repository.DocumentRepository
import at.fhtw.swen3.paperless.documenttype.entity.DocumentTypeEntity
import at.fhtw.swen3.paperless.exception.AppErrorMessage
import at.fhtw.swen3.paperless.exception.AppException
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.mockito.ArgumentMatchers.any
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class DocumentServiceImplTest {

    @Mock
    lateinit var repository: DocumentRepository

    @Mock
    lateinit var entityManager: EntityManager

    @InjectMocks
    lateinit var service: DocumentServiceImpl

    @TempDir
    lateinit var tempDir: Path

    private lateinit var originalTempDirectory: String

    @BeforeEach
    fun use_test_owned_storage() {
        originalTempDirectory = System.getProperty("java.io.tmpdir")
        System.setProperty("java.io.tmpdir", tempDir.toString())
        service = DocumentServiceImpl(repository, entityManager)
    }

    @AfterEach
    fun restore_system_storage() {
        System.setProperty("java.io.tmpdir", originalTempDirectory)
    }

    @Test
    fun search_documents_with_relationship_filters_ok() {
        // given
        val pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "title"))
        `when`(repository.findByCorrespondent_IdAndDocumentType_Id(7, 9, pageable))
            .thenReturn(PageImpl(listOf(documentEntity())))

        // when
        val result = service.searchDocuments(0, 10, "title,asc", 7, 9)

        // then
        assertAll(
            { assertThat(result.totalElements).isEqualTo(1) },
            { assertThat(result.content.single().title).isEqualTo("Invoice") },
            { verify(repository).findByCorrespondent_IdAndDocumentType_Id(7, 9, pageable) }
        )
    }

    @Test
    fun search_documents_by_correspondent_only_ok() {
        // given
        val pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"))
        `when`(repository.findByCorrespondent_Id(7, pageable)).thenReturn(PageImpl(listOf(documentEntity())))

        // when
        val result = service.searchDocuments(0, 10, "created_at,desc", 7, null)

        // then
        assertAll(
            { assertThat(result.totalElements).isEqualTo(1) },
            { verify(repository).findByCorrespondent_Id(7, pageable) }
        )
    }

    @Test
    fun search_documents_by_document_type_only_ok() {
        // given
        val pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"))
        `when`(repository.findByDocumentType_Id(9, pageable)).thenReturn(PageImpl(listOf(documentEntity())))

        // when
        val result = service.searchDocuments(0, 10, "created_at,desc", null, 9)

        // then
        assertAll(
            { assertThat(result.totalElements).isEqualTo(1) },
            { verify(repository).findByDocumentType_Id(9, pageable) }
        )
    }

    @Test
    fun search_documents_without_filters_ok() {
        // given
        val pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"))
        `when`(repository.findAll(pageable)).thenReturn(PageImpl(listOf(documentEntity())))

        // when
        val result = service.searchDocuments(0, 10, "created_at,desc", null, null)

        // then
        assertAll(
            { assertThat(result.totalElements).isEqualTo(1) },
            { verify(repository).findAll(pageable) }
        )
    }

    @Test
    fun get_document_ok() {
        // given
        `when`(repository.findById(1)).thenReturn(Optional.of(documentEntity()))

        // when
        val result = service.getDocument(1)

        // then
        assertAll(
            { assertThat(result.id).isEqualTo(1) },
            { assertThat(result.title).isEqualTo("Invoice") },
            { assertThat(result.correspondent?.id).isEqualTo(7) }
        )
    }

    @Test
    fun get_document_not_found_ko() {
        // given
        `when`(repository.findById(42)).thenReturn(Optional.empty())

        // when / then
        assertAppError(AppErrorMessage.DOCUMENT_NOT_FOUND) { service.getDocument(42) }
    }

    @Test
    fun upload_document_uses_and_cleans_test_storage_ok() {
        // given
        var saved: DocumentEntity? = null
        `when`(repository.save(any(DocumentEntity::class.java))).thenAnswer {
            saved = it.arguments[0] as DocumentEntity
            saved
        }
        val upload = DocumentUpload("Invoice", "invoice.pdf", "application/pdf", byteArrayOf(1, 2), null, null)

        // when
        val result = service.uploadDocument(upload)

        try {
            // then
            assertAll(
                { assertThat(result.title).isEqualTo("Invoice") },
                { assertThat(result.fileSize).isEqualTo(2) },
                { assertThat(result.status).isEqualTo(ProcessingStatus.PENDING) },
                { assertThat(saved?.storageKey).isNotBlank() }
            )
        } finally {
            `when`(repository.findById(result.id)).thenReturn(Optional.of(saved!!))
            service.deleteDocument(result.id)
        }
    }

    @Test
    fun update_document_preserves_relationships_when_patch_omits_them_ok() {
        // given
        val existing = documentEntity()
        `when`(repository.findById(1)).thenReturn(Optional.of(existing))
        `when`(repository.save(any(DocumentEntity::class.java))).thenAnswer { it.arguments[0] as DocumentEntity }

        // when
        val result = service.updateDocument(1, DocumentUpdate("Updated", null, null))

        // then
        assertAll(
            { assertThat(result.title).isEqualTo("Updated") },
            { assertThat(result.correspondent?.id).isEqualTo(7) },
            { assertThat(result.documentType?.id).isEqualTo(9) },
            { assertThat(result.storageKey).isEqualTo(existing.storageKey) }
        )
    }

    @Test
    fun update_document_replaces_non_null_relationships_ok() {
        // given
        val existing = documentEntity()
        val replacementCorrespondent = CorrespondentEntity(8, "New correspondent")
        val replacementType = DocumentTypeEntity(10, "Receipt")
        `when`(repository.findById(1)).thenReturn(Optional.of(existing))
        `when`(entityManager.getReference(CorrespondentEntity::class.java, 8L)).thenReturn(replacementCorrespondent)
        `when`(entityManager.getReference(DocumentTypeEntity::class.java, 10L)).thenReturn(replacementType)
        `when`(repository.save(any(DocumentEntity::class.java))).thenAnswer { it.arguments[0] as DocumentEntity }

        // when
        val result = service.updateDocument(1, DocumentUpdate(null, 8, 10))

        // then
        assertAll(
            { assertThat(result.title).isEqualTo(existing.title) },
            { assertThat(result.correspondent?.id).isEqualTo(8) },
            { assertThat(result.documentType?.id).isEqualTo(10) }
        )
    }

    @Test
    fun update_document_not_found_ko() {
        // given
        `when`(repository.findById(42)).thenReturn(Optional.empty())

        // when / then
        assertAll(
            { assertAppError(AppErrorMessage.DOCUMENT_NOT_FOUND) { service.updateDocument(42, DocumentUpdate("Updated", null, null)) } },
            { verify(repository, never()).save(any(DocumentEntity::class.java)) }
        )
    }

    @Test
    fun delete_document_ok() {
        // given
        val entity = documentEntity().copyStorageKey(null)
        `when`(repository.findById(1)).thenReturn(Optional.of(entity))

        // when
        service.deleteDocument(1)

        // then
        verify(repository).delete(entity)
    }

    @Test
    fun download_document_ok() {
        // given
        val directory = tempDir.resolve("paperless-documents")
        Files.createDirectories(directory)
        Files.write(directory.resolve("download.pdf"), byteArrayOf(1, 2))
        val entity = documentEntity().copyStorageKey("documents/download.pdf")
        `when`(repository.findById(1)).thenReturn(Optional.of(entity))

        // when
        val result = service.downloadDocument(1)

        // then
        assertAll(
            { assertThat(result.exists()).isTrue() },
            { assertThat(result.filename).isEqualTo("download.pdf") }
        )
    }

    @Test
    fun download_document_without_storage_ko() {
        // given
        `when`(repository.findById(1)).thenReturn(Optional.of(documentEntity().copyStorageKey(null)))

        // when / then
        assertAppError(AppErrorMessage.DOCUMENT_NOT_FOUND) { service.downloadDocument(1) }
    }

    private fun assertAppError(expected: AppErrorMessage, action: () -> Unit) {
        val thrown = catchThrowable(action)
        assertAll(
            { assertThat(thrown).isInstanceOf(AppException::class.java) },
            { assertThat((thrown as AppException).error).isEqualTo(expected) }
        )
    }

    private fun DocumentEntity.copyStorageKey(value: String?) = DocumentEntity(
        id = id,
        title = title,
        originalFilename = originalFilename,
        contentType = contentType,
        fileSize = fileSize,
        status = status,
        ocrContent = ocrContent,
        summary = summary,
        storageKey = value,
        correspondent = correspondent,
        documentType = documentType,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun documentEntity() = DocumentEntity(
        id = 1,
        title = "Invoice",
        originalFilename = "invoice.pdf",
        contentType = "application/pdf",
        fileSize = 10,
        status = ProcessingStatus.COMPLETED,
        ocrContent = "Invoice from Acme",
        summary = "An invoice",
        storageKey = "documents/existing.pdf",
        correspondent = CorrespondentEntity(7, "Acme"),
        documentType = DocumentTypeEntity(9, "Invoice"),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )
}
