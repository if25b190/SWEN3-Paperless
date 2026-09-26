package at.fhtw.swen3.paperless.document.service

import at.fhtw.swen3.paperless.document.entity.DocumentEntity
import at.fhtw.swen3.paperless.document.mapper.DocumentMapper
import at.fhtw.swen3.paperless.document.model.DocumentUpdate
import at.fhtw.swen3.paperless.document.model.DocumentUpload
import at.fhtw.swen3.paperless.document.model.ProcessingStatus
import at.fhtw.swen3.paperless.document.repository.DocumentRepository
import at.fhtw.swen3.paperless.documenttype.entity.DocumentTypeEntity
import at.fhtw.swen3.paperless.exception.AppErrorMessage
import at.fhtw.swen3.paperless.exception.AppException
import at.fhtw.swen3.paperless.team.entity.TeamEntity
import at.fhtw.swen3.paperless.team.model.Role
import at.fhtw.swen3.paperless.team.service.TeamService
import at.fhtw.swen3.paperless.user.entity.UserEntity
import jakarta.persistence.EntityManager
import jakarta.persistence.OptimisticLockException
import jakarta.validation.ConstraintViolationException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.never
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class DocumentServiceImplTest {

    @Mock
    lateinit var repository: DocumentRepository

    @Mock
    lateinit var entityManager: EntityManager

    @Mock
    lateinit var teamService: TeamService

    lateinit var service: DocumentServiceImpl

    @TempDir
    lateinit var tempDir: Path

    private lateinit var originalTempDirectory: String

    @BeforeEach
    fun use_test_owned_storage_and_actor() {
        originalTempDirectory = System.getProperty("java.io.tmpdir")
        System.setProperty("java.io.tmpdir", tempDir.toString())
        setActor(uuid(1))
        service = DocumentServiceImpl(repository, entityManager, teamService)
    }

    @AfterEach
    fun restore_test_state() {
        SecurityContextHolder.clearContext()
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization()
        }
        System.setProperty("java.io.tmpdir", originalTempDirectory)
    }

    @Test
    fun search_applies_visibility_and_document_type_filter_before_paging_and_counting() {
        val pageable = PageRequest.of(1, 1, Sort.by(Sort.Direction.ASC, "title"))
        val page = PageImpl(listOf(documentEntity()), pageable, 3)
        `when`(teamService.visibleTeamIds(uuid(1))).thenReturn(setOf(uuid(4), uuid(5)))
        `when`(repository.findVisibleDocuments(uuid(1), setOf(uuid(4), uuid(5)), uuid(9), pageable)).thenReturn(page)

        val result = service.searchDocuments(1, 1, "title,asc", uuid(9))

        assertAll(
            { assertThat(result.totalElements).isEqualTo(3) },
            { assertThat(result.content.single().title).isEqualTo("Invoice") },
            { verify(repository).findVisibleDocuments(uuid(1), setOf(uuid(4), uuid(5)), uuid(9), pageable) },
            { verify(repository, never()).findAll(any(Pageable::class.java)) }
        )
    }

    @Test
    fun search_without_memberships_uses_owner_only_query_and_preserves_document_type_filter() {
        val pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"))
        val page = PageImpl(listOf(documentEntity()), pageable, 1)
        `when`(teamService.visibleTeamIds(uuid(1))).thenReturn(emptySet())
        `when`(repository.findOwnerOnlyDocuments(uuid(1), uuid(9), pageable)).thenReturn(page)
        `when`(repository.findOwnerOnlyDocuments(uuid(1), null, pageable)).thenReturn(page)

        service.searchDocuments(0, 10, "created_at,desc", uuid(9))
        service.searchDocuments(0, 10, "created_at,desc", null)

        verify(repository).findOwnerOnlyDocuments(uuid(1), uuid(9), pageable)
        verify(repository).findOwnerOnlyDocuments(uuid(1), null, pageable)
        verify(repository, never()).findVisibleDocuments(uuid(1), emptySet(), uuid(9), pageable)
        verify(repository, never()).findVisibleDocuments(uuid(1), emptySet(), null, pageable)
    }

    @Test
    fun search_candidates_are_only_currently_visible_documents() {
        `when`(teamService.visibleTeamIds(uuid(1))).thenReturn(setOf(uuid(4)))
        `when`(repository.findVisibleDocuments(uuid(1), setOf(uuid(4)))).thenReturn(listOf(documentEntity(teamId = uuid(4))))

        val result = service.visibleDocumentsForSearch()

        assertThat(result.single().teamId).isEqualTo(uuid(4))
        verify(repository).findVisibleDocuments(uuid(1), setOf(uuid(4)))
    }

    @Test
    fun search_with_no_memberships_uses_owner_only_visibility_query() {
        `when`(teamService.visibleTeamIds(uuid(1))).thenReturn(emptySet())
        `when`(repository.findOwnerOnlyDocuments(uuid(1))).thenReturn(listOf(documentEntity()))

        val result = service.visibleDocumentsForSearch()

        assertThat(result.single().ownerId).isEqualTo(uuid(1))
        verify(repository).findOwnerOnlyDocuments(uuid(1))
        verify(repository, never()).findVisibleDocuments(uuid(1), emptySet())
    }

    @Test
    fun private_documents_are_uploader_only() {
        `when`(repository.findById(uuid(1))).thenReturn(Optional.of(documentEntity()))
        setActor(uuid(2))

        assertAppError(AppErrorMessage.DOCUMENT_NOT_FOUND) { service.getDocument(uuid(1)) }
        assertAppError(AppErrorMessage.DOCUMENT_NOT_FOUND) { service.updateDocument(uuid(1), update(title = "No")) }
        assertAppError(AppErrorMessage.DOCUMENT_NOT_FOUND) { service.deleteDocument(uuid(1)) }
    }

    @Test
    fun readonly_team_member_can_read_but_cannot_write() {
        val entity = documentEntity(teamId = uuid(4))
        `when`(repository.findById(uuid(1))).thenReturn(Optional.of(entity))
        `when`(teamService.roleFor(uuid(4), uuid(2))).thenReturn(Role.READONLY)
        `when`(teamService.lockedRoles(setOf(uuid(4)), uuid(2))).thenReturn(mapOf(uuid(4) to Role.READONLY))
        setActor(uuid(2))

        assertThat(service.getDocument(uuid(1)).teamId).isEqualTo(uuid(4))
        assertDenied { service.updateDocument(uuid(1), update(title = "No")) }
        assertDenied { service.deleteDocument(uuid(1)) }
    }

    @Test
    fun outsider_and_removed_uploader_get_not_found_for_team_documents() {
        val entity = documentEntity(ownerId = uuid(1), teamId = uuid(4))
        `when`(repository.findById(uuid(1))).thenReturn(Optional.of(entity))
        `when`(teamService.roleFor(uuid(4), uuid(2))).thenReturn(null)
        `when`(teamService.lockedRoles(setOf(uuid(4)), uuid(2))).thenReturn(mapOf(uuid(4) to null))
        setActor(uuid(2))

        assertAppError(AppErrorMessage.DOCUMENT_NOT_FOUND) { service.getDocument(uuid(1)) }
        assertAppError(AppErrorMessage.DOCUMENT_NOT_FOUND) { service.downloadDocument(uuid(1)) }
        assertAppError(AppErrorMessage.DOCUMENT_NOT_FOUND) { service.updateDocument(uuid(1), update(title = "No")) }
        assertAppError(AppErrorMessage.DOCUMENT_NOT_FOUND) { service.deleteDocument(uuid(1)) }
        verify(repository, never()).delete(entity)
        verify(repository, never()).save(any(DocumentEntity::class.java))

        setActor(uuid(1))
        assertAppError(AppErrorMessage.DOCUMENT_NOT_FOUND) { service.getDocument(uuid(1)) }
    }

    @Test
    fun private_upload_sets_owner_on_server_and_stays_private() {
        `when`(entityManager.find(UserEntity::class.java, uuid(1))).thenReturn(userEntity())
        `when`(repository.save(any(DocumentEntity::class.java))).thenAnswer {
            val unsaved = it.arguments[0] as DocumentEntity
            assertThat(unsaved.id).isNull()
            persistedDocument(unsaved, uuid(20))
        }

        val result = service.uploadDocument(upload())

        assertAll(
            { assertThat(result.id).isEqualTo(uuid(20)) },
            { assertThat(result.ownerId).isEqualTo(uuid(1)) },
            { assertThat(result.teamId).isNull() },
            { assertThat(result.status).isEqualTo(ProcessingStatus.PENDING) }
        )
        val stored = repositorySaveArgument()
        assertThat(stored.owner.id).isEqualTo(uuid(1))
        assertThat(stored.id).isNull()
        assertThat(stored.team).isNull()
    }

    @Test
    fun upload_rejects_blank_or_overlong_title_before_file_write() {
        `when`(entityManager.find(UserEntity::class.java, uuid(1))).thenReturn(userEntity())

        listOf(" \t\n", "x".repeat(256)).forEach { title ->
            assertThrows<ConstraintViolationException> {
                service.uploadDocument(upload(title = title))
            }
        }

        assertThat(tempDir.resolve("paperless-documents")).doesNotExist()
        verify(repository, never()).save(any(DocumentEntity::class.java))
    }

    @Test
    fun team_upload_requires_writer_and_validates_before_file_write() {
        `when`(entityManager.find(UserEntity::class.java, uuid(1))).thenReturn(userEntity())
        `when`(teamService.lockedRoles(setOf(uuid(4)), uuid(1))).thenReturn(mapOf(uuid(4) to Role.READONLY))

        assertDenied { service.uploadDocument(upload(teamId = uuid(4))) }

        assertThat(tempDir.resolve("paperless-documents")).doesNotExist()
        verify(repository, never()).save(any(DocumentEntity::class.java))
    }

    @Test
    fun team_upload_persists_owner_and_team_after_authorization() {
        `when`(entityManager.find(UserEntity::class.java, uuid(1))).thenReturn(userEntity())
        `when`(entityManager.find(TeamEntity::class.java, uuid(4))).thenReturn(teamEntity(uuid(4)))
        `when`(teamService.lockedRoles(setOf(uuid(4)), uuid(1))).thenReturn(mapOf(uuid(4) to Role.READ_WRITE))
        `when`(repository.save(any(DocumentEntity::class.java))).thenAnswer {
            persistedDocument(it.arguments[0] as DocumentEntity, uuid(21))
        }

        val result = service.uploadDocument(upload(teamId = uuid(4)))

        assertThat(result.ownerId).isEqualTo(uuid(1))
        assertThat(result.teamId).isEqualTo(uuid(4))
        assertThat(repositorySaveArgument().team?.id).isEqualTo(uuid(4))
    }

    @Test
    fun upload_preserves_document_type_metadata_without_correspondent() {
        `when`(entityManager.find(UserEntity::class.java, uuid(1))).thenReturn(userEntity())
        val documentType = DocumentTypeEntity(uuid(9), "Invoice")
        `when`(entityManager.find(DocumentTypeEntity::class.java, uuid(9))).thenReturn(documentType)
        `when`(repository.save(any(DocumentEntity::class.java))).thenAnswer {
            persistedDocument(it.arguments[0] as DocumentEntity, uuid(22))
        }

        val uploaded = service.uploadDocument(upload(documentTypeId = uuid(9)))

        assertThat(repositorySaveArgument().documentType).isEqualTo(documentType)
        assertThat(uploaded.documentType?.name).isEqualTo("Invoice")
        assertThat(DocumentMapper.toDto(uploaded).documentType?.name).isEqualTo("Invoice")
    }

    @Test
    fun update_changes_document_type_metadata_without_correspondent() {
        val existing = documentEntity()
        val updatedType = DocumentTypeEntity(uuid(8), "Receipt")
        `when`(repository.findById(uuid(1))).thenReturn(Optional.of(existing))
        `when`(entityManager.find(DocumentTypeEntity::class.java, uuid(8))).thenReturn(updatedType)
        `when`(repository.save(any(DocumentEntity::class.java))).thenAnswer { it.arguments[0] as DocumentEntity }

        val updated = service.updateDocument(uuid(1), update(documentTypeId = uuid(8)))

        assertThat(updated.documentType?.name).isEqualTo("Receipt")
        assertThat(DocumentMapper.toDto(updated).documentType?.name).isEqualTo("Receipt")
    }

    @Test
    fun upload_failure_cleans_file_on_rollback() {
        `when`(entityManager.find(UserEntity::class.java, uuid(1))).thenReturn(userEntity())
        `when`(repository.save(any(DocumentEntity::class.java))).thenThrow(IllegalStateException("save failed"))
        TransactionSynchronizationManager.initSynchronization()

        assertThrows<IllegalStateException> { service.uploadDocument(upload()) }

        val directory = tempDir.resolve("paperless-documents")
        assertThat(Files.list(directory).use { it.count() }).isZero()
        TransactionSynchronizationManager.getSynchronizations()
            .forEach { it.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK) }
    }

    @Test
    fun any_current_team_writer_can_edit_metadata_without_changing_owner_or_sharing() {
        val entity = documentEntity(ownerId = uuid(1), teamId = uuid(4))
        `when`(repository.findById(uuid(1))).thenReturn(Optional.of(entity))
        `when`(teamService.lockedRoles(setOf(uuid(4)), uuid(2))).thenReturn(mapOf(uuid(4) to Role.READ_WRITE))
        `when`(repository.save(any(DocumentEntity::class.java))).thenAnswer { it.arguments[0] as DocumentEntity }
        setActor(uuid(2))

        val result = service.updateDocument(uuid(1), update(title = "Updated"))

        assertAll(
            { assertThat(result.title).isEqualTo("Updated") },
            { assertThat(result.ownerId).isEqualTo(uuid(1)) },
            { assertThat(result.teamId).isEqualTo(uuid(4)) },
            { assertThat(repositorySaveArgument().version).isEqualTo(entity.version) }
        )
    }

    @Test
    fun update_rejects_blank_or_overlong_title_without_saving() {
        `when`(repository.findById(uuid(1))).thenReturn(Optional.of(documentEntity()))

        listOf(" \t\n", "x".repeat(256)).forEach { title ->
            assertThrows<ConstraintViolationException> {
                service.updateDocument(uuid(1), update(title = title))
            }
        }

        verify(repository, never()).save(any(DocumentEntity::class.java))
    }

    @Test
    fun stale_document_version_cannot_restore_sharing_or_delete_document() {
        val entity = documentEntity(teamId = uuid(4))
        `when`(repository.findById(uuid(1))).thenReturn(Optional.of(entity))
        `when`(teamService.lockedRoles(setOf(uuid(4)), uuid(1))).thenReturn(mapOf(uuid(4) to Role.ADMIN))
        doAnswer { entity.version++; null }.`when`(entityManager).refresh(entity)

        assertThrows<OptimisticLockException> { service.updateDocument(uuid(1), update(title = "Stale")) }
        assertThrows<OptimisticLockException> { service.deleteDocument(uuid(1)) }
        verify(repository, never()).save(any(DocumentEntity::class.java))
        verify(repository, never()).delete(entity)
    }

    @Test
    fun revoked_writer_is_rejected_after_team_lock_even_if_previous_role_was_writer() {
        val entity = documentEntity(teamId = uuid(4))
        `when`(repository.findById(uuid(1))).thenReturn(Optional.of(entity))
        `when`(teamService.lockedRoles(setOf(uuid(4)), uuid(1))).thenReturn(mapOf(uuid(4) to null))

        assertAppError(AppErrorMessage.DOCUMENT_NOT_FOUND) { service.updateDocument(uuid(1), update(clearTeam = true)) }
        assertAppError(AppErrorMessage.DOCUMENT_NOT_FOUND) { service.deleteDocument(uuid(1)) }
        verify(repository, never()).delete(entity)
    }

    @Test
    fun uploader_can_move_or_clear_sharing_with_write_access_to_source_and_destination() {
        val entity = documentEntity(ownerId = uuid(1), teamId = uuid(4))
        `when`(repository.findById(uuid(1))).thenReturn(Optional.of(entity))
        `when`(teamService.lockedRoles(setOf(uuid(4), uuid(5)), uuid(1)))
            .thenReturn(mapOf(uuid(4) to Role.READ_WRITE, uuid(5) to Role.ADMIN))
        `when`(teamService.lockedRoles(setOf(uuid(4)), uuid(1))).thenReturn(mapOf(uuid(4) to Role.READ_WRITE))
        `when`(entityManager.find(TeamEntity::class.java, uuid(5))).thenReturn(teamEntity(uuid(5)))
        `when`(repository.save(any(DocumentEntity::class.java))).thenAnswer { it.arguments[0] as DocumentEntity }

        val moved = service.updateDocument(uuid(1), update(teamId = uuid(5)))
        val madePrivate = service.updateDocument(uuid(1), update(clearTeam = true))

        assertAll(
            { assertThat(moved.ownerId).isEqualTo(uuid(1)) },
            { assertThat(moved.teamId).isEqualTo(uuid(5)) },
            { assertThat(madePrivate.teamId).isNull() }
        )
    }

    @Test
    fun current_writer_may_not_change_sharing_and_same_sharing_target_conflicts() {
        val entity = documentEntity(ownerId = uuid(1), teamId = uuid(4))
        `when`(repository.findById(uuid(1))).thenReturn(Optional.of(entity))
        `when`(teamService.lockedRoles(setOf(uuid(4), uuid(5)), uuid(2)))
            .thenReturn(mapOf(uuid(4) to Role.READ_WRITE, uuid(5) to null))
        setActor(uuid(2))

        assertDenied { service.updateDocument(uuid(1), update(teamId = uuid(5))) }
        verify(repository, never()).save(any(DocumentEntity::class.java))

        setActor(uuid(1))
        `when`(teamService.lockedRoles(setOf(uuid(4)), uuid(1))).thenReturn(mapOf(uuid(4) to Role.ADMIN))
        assertThrows<DataIntegrityViolationException> { service.updateDocument(uuid(1), update(teamId = uuid(4))) }
    }

    @Test
    fun sharing_request_cannot_set_destination_and_clear_together() {
        assertThrows<ConstraintViolationException> {
            service.updateDocument(uuid(1), update(teamId = uuid(4), clearTeam = true))
        }
        verify(repository, never()).findById(uuid(1))
    }

    @Test
    fun non_member_cannot_upload_to_existing_team_and_missing_team_is_not_found() {
        `when`(entityManager.find(UserEntity::class.java, uuid(1))).thenReturn(userEntity())
        `when`(teamService.lockedRoles(setOf(uuid(4)), uuid(1))).thenReturn(mapOf(uuid(4) to null))
        assertDenied { service.uploadDocument(upload(teamId = uuid(4))) }

        `when`(teamService.lockedRoles(setOf(uuid(8)), uuid(1))).thenThrow(AppException(AppErrorMessage.TEAM_NOT_FOUND))
        assertAppError(AppErrorMessage.TEAM_NOT_FOUND) { service.uploadDocument(upload(teamId = uuid(8))) }
        assertThat(tempDir.resolve("paperless-documents")).doesNotExist()
    }

    @Test
    fun delete_removes_file_only_after_transaction_commit() {
        val directory = tempDir.resolve("paperless-documents")
        Files.createDirectories(directory)
        val file = directory.resolve("delete.pdf")
        Files.write(file, byteArrayOf(1))
        val entity = documentEntity().withStorageKey("documents/delete.pdf")
        `when`(repository.findById(uuid(1))).thenReturn(Optional.of(entity))
        TransactionSynchronizationManager.initSynchronization()

        service.deleteDocument(uuid(1))

        assertThat(file).exists()
        TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }
        assertThat(file).doesNotExist()
    }

    @Test
    fun committed_delete_does_not_fail_when_file_unlink_fails() {
        val directory = tempDir.resolve("paperless-documents")
        val blocked = directory.resolve("not-a-file.pdf")
        Files.createDirectories(blocked)
        Files.write(blocked.resolve("child"), byteArrayOf(1))
        val entity = documentEntity().withStorageKey("documents/not-a-file.pdf")
        `when`(repository.findById(uuid(1))).thenReturn(Optional.of(entity))
        TransactionSynchronizationManager.initSynchronization()

        service.deleteDocument(uuid(1))
        TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }

        verify(repository).delete(entity)
        assertThat(blocked).exists()
    }

    private fun update(
        teamId: UUID? = null,
        clearTeam: Boolean = false,
        title: String? = null,
        documentTypeId: UUID? = null
    ) = DocumentUpdate(teamId, clearTeam, title, documentTypeId)

    private fun upload(
        title: String = "Invoice",
        teamId: UUID? = null,
        documentTypeId: UUID? = null
    ) = DocumentUpload(
        title,
        "invoice.pdf",
        "application/pdf",
        byteArrayOf(1, 2),
        documentTypeId,
        teamId
    )

    private fun setActor(id: UUID) {
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(id.toString(), "test")
    }

    private fun uuid(value: Int) = UUID(0L, value.toLong())

    private fun userEntity() = UserEntity(id = uuid(1), username = "uploader", password = "hash")

    private fun teamEntity(id: UUID) = TeamEntity(id = id, name = "Team $id", ownerId = uuid(1))

    private fun repositorySaveArgument(): DocumentEntity =
        org.mockito.Mockito.mockingDetails(repository).getInvocations()
            .last { it.method.name == "save" }.arguments[0] as DocumentEntity

    private fun persistedDocument(entity: DocumentEntity, id: UUID) = DocumentEntity(
        id = id,
        title = entity.title,
        originalFilename = entity.originalFilename,
        contentType = entity.contentType,
        fileSize = entity.fileSize,
        owner = entity.owner,
        team = entity.team,
        status = entity.status,
        ocrContent = entity.ocrContent,
        summary = entity.summary,
        storageKey = entity.storageKey,
        documentType = entity.documentType,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt
    )

    private fun assertAppError(expected: AppErrorMessage, action: () -> Unit) {
        val thrown = catchThrowable(action)
        assertAll(
            { assertThat(thrown).isInstanceOf(AppException::class.java) },
            { assertThat((thrown as AppException).error).isEqualTo(expected) }
        )
    }

    private fun assertDenied(action: () -> Unit) {
        assertThat(catchThrowable(action)).isInstanceOf(AccessDeniedException::class.java)
    }

    private inline fun <reified T : Throwable> assertThrows(noinline action: () -> Unit) {
        assertThat(catchThrowable(action)).isInstanceOf(T::class.java)
    }

    private fun documentEntity(
        ownerId: UUID = uuid(1),
        teamId: UUID? = null
    ) = DocumentEntity(
        id = uuid(1),
        title = "Invoice",
        originalFilename = "invoice.pdf",
        contentType = "application/pdf",
        fileSize = 10,
        owner = userEntity(),
        team = teamId?.let(::teamEntity),
        status = ProcessingStatus.COMPLETED,
        ocrContent = "Invoice from Acme",
        summary = "An invoice",
        storageKey = "documents/existing.pdf",
        documentType = DocumentTypeEntity(uuid(9), "Invoice"),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    ).let { entity ->
        if (ownerId == uuid(1)) entity else DocumentEntity(
            id = entity.id,
            title = entity.title,
            originalFilename = entity.originalFilename,
            contentType = entity.contentType,
            fileSize = entity.fileSize,
            owner = UserEntity(id = ownerId),
            team = entity.team,
            status = entity.status,
            ocrContent = entity.ocrContent,
            summary = entity.summary,
            storageKey = entity.storageKey,
            documentType = entity.documentType,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    private fun DocumentEntity.withStorageKey(value: String?) = DocumentEntity(
        id = id,
        title = title,
        originalFilename = originalFilename,
        contentType = contentType,
        fileSize = fileSize,
        owner = owner,
        team = team,
        status = status,
        ocrContent = ocrContent,
        summary = summary,
        storageKey = value,
        documentType = documentType,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
