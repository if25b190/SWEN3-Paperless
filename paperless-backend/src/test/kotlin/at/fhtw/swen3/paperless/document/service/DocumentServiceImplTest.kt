package at.fhtw.swen3.paperless.document.service

import at.fhtw.swen3.paperless.common.mapper.MappingConverters
import at.fhtw.swen3.paperless.document.mapper.DocumentMapperImpl
import at.fhtw.swen3.paperless.document.model.Document
import at.fhtw.swen3.paperless.document.model.DocumentUpdate
import at.fhtw.swen3.paperless.document.model.DocumentUpload
import at.fhtw.swen3.paperless.document.model.ProcessingStatus
import at.fhtw.swen3.paperless.document.persistence.DocumentPersistence
import at.fhtw.swen3.paperless.document.persistence.DocumentReferencesPersistence
import at.fhtw.swen3.paperless.documenttype.mapper.DocumentTypeMapperImpl

import at.fhtw.swen3.paperless.documenttype.model.DocumentType
import at.fhtw.swen3.paperless.exception.AppException
import at.fhtw.swen3.paperless.exception.DocumentNotFoundException
import at.fhtw.swen3.paperless.ocr.model.OcrJob
import at.fhtw.swen3.paperless.ocr.persistence.OcrJobPersistence
import at.fhtw.swen3.paperless.exception.TeamNotFoundException
import at.fhtw.swen3.paperless.team.model.Role
import at.fhtw.swen3.paperless.team.model.Team
import at.fhtw.swen3.paperless.team.model.TeamMember
import at.fhtw.swen3.paperless.team.persistence.TeamMemberPersistence
import at.fhtw.swen3.paperless.testsupport.any
import at.fhtw.swen3.paperless.user.model.User
import jakarta.persistence.OptimisticLockException
import jakarta.validation.ConstraintViolationException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.Optional
import java.util.UUID
import kotlin.reflect.KClass
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.never
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

@ExtendWith(MockitoExtension::class)
class DocumentServiceImplTest {

    @Mock
    lateinit var repository: DocumentPersistence

    @Mock
    lateinit var references: DocumentReferencesPersistence

    @Mock
    lateinit var teamMemberRepository: TeamMemberPersistence

    @Mock
    lateinit var ocrJobs: OcrJobPersistence

    lateinit var service: DocumentServiceImpl

    private val converters = MappingConverters()

    private val documentMapper = DocumentMapperImpl(converters, DocumentTypeMapperImpl(converters))

    @TempDir
    lateinit var tempDir: Path

    private lateinit var originalTempDirectory: String

    @BeforeEach
    fun use_test_owned_storage_and_actor() {
        originalTempDirectory = System.getProperty("java.io.tmpdir")
        System.setProperty("java.io.tmpdir", tempDir.toString())
        setActor(uuid(1))
        service = DocumentServiceImpl(repository, references, teamMemberRepository, ocrJobs)
        org.mockito.Mockito.lenient().`when`(repository.refreshUnchanged(any(Document::class.java)))
            .thenAnswer { it.arguments[0] as Document }
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
        `when`(teamMemberRepository.findByUserId(uuid(1))).thenReturn(listOf(membership(uuid(4), uuid(1), Role.READONLY), membership(uuid(5), uuid(1), Role.READONLY)))
        `when`(repository.findVisibleDocuments(uuid(1), setOf(uuid(4), uuid(5)), uuid(9), pageable)).thenReturn(page)

        val result = service.searchDocuments(1, 1, "title,asc", uuid(9))

        assertAll(
            { assertThat(result.totalElements).isEqualTo(3) },
            { assertThat(result.content.single().title).isEqualTo("Invoice") },
            { verify(repository).findVisibleDocuments(uuid(1), setOf(uuid(4), uuid(5)), uuid(9), pageable) },
        )
    }

    @Test
    fun search_without_memberships_uses_owner_only_query_and_preserves_document_type_filter() {
        val pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"))
        val page = PageImpl(listOf(documentEntity()), pageable, 1)
        `when`(teamMemberRepository.findByUserId(uuid(1))).thenReturn(emptyList())
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
    fun private_documents_are_uploader_only() {
        `when`(repository.findById(uuid(1))).thenReturn(Optional.of(documentEntity()))
        setActor(uuid(2))

        assertAppError(DocumentNotFoundException::class) { service.getDocument(uuid(1)) }
        assertAppError(DocumentNotFoundException::class) { service.updateDocument(uuid(1), update(title = "No")) }
        assertAppError(DocumentNotFoundException::class) { service.deleteDocument(uuid(1)) }
    }

    @Test
    fun readonly_team_member_can_read_but_cannot_write() {
        val entity = documentEntity(teamId = uuid(4))
        `when`(repository.findById(uuid(1))).thenReturn(Optional.of(entity))
        stubTeamRole(uuid(4), uuid(2), Role.READONLY)
        setActor(uuid(2))

        assertThat(service.getDocument(uuid(1)).teamId).isEqualTo(uuid(4))
        assertDenied { service.updateDocument(uuid(1), update(title = "No")) }
        assertDenied { service.deleteDocument(uuid(1)) }
    }

    @Test
    fun outsider_and_removed_uploader_get_not_found_for_team_documents() {
        val entity = documentEntity(ownerId = uuid(1), teamId = uuid(4))
        `when`(repository.findById(uuid(1))).thenReturn(Optional.of(entity))
        stubTeamRole(uuid(4), uuid(2), null)
        setActor(uuid(2))

        assertAppError(DocumentNotFoundException::class) { service.getDocument(uuid(1)) }
        assertAppError(DocumentNotFoundException::class) { service.downloadDocument(uuid(1)) }
        assertAppError(DocumentNotFoundException::class) { service.updateDocument(uuid(1), update(title = "No")) }
        assertAppError(DocumentNotFoundException::class) { service.deleteDocument(uuid(1)) }
        verify(repository, never()).delete(entity)
        verify(repository, never()).save(any(Document::class.java))

        setActor(uuid(1))
        assertAppError(DocumentNotFoundException::class) { service.getDocument(uuid(1)) }
    }

    @Test
    fun private_upload_sets_owner_on_server_and_stays_private() {
        `when`(references.ownerExists(uuid(1))).thenReturn(true)
        `when`(repository.save(any(Document::class.java))).thenAnswer {
            val unsaved = it.arguments[0] as Document
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
        assertThat(stored.ownerId).isEqualTo(uuid(1))
        assertThat(stored.id).isNull()
        assertThat(stored.teamId).isNull()
        verify(ocrJobs).enqueue(OcrJob(null, uuid(20), requireNotNull(result.storageKey), "application/pdf"))
    }

    @Test
    fun upload_rejects_blank_or_overlong_title_before_file_write() {
        `when`(references.ownerExists(uuid(1))).thenReturn(true)

        listOf(" \t\n", "x".repeat(256)).forEach { title ->
            assertThrows<ConstraintViolationException> {
                service.uploadDocument(upload(title = title))
            }
        }

        assertThat(tempDir.resolve("paperless-documents")).doesNotExist()
        verify(repository, never()).save(any(Document::class.java))
    }

    @Test
    fun team_upload_requires_writer_and_validates_before_file_write() {
        `when`(references.ownerExists(uuid(1))).thenReturn(true)
        stubTeamRole(uuid(4), uuid(1), Role.READONLY)

        assertDenied { service.uploadDocument(upload(teamId = uuid(4))) }

        assertThat(tempDir.resolve("paperless-documents")).doesNotExist()
        verify(repository, never()).save(any(Document::class.java))
    }

    @Test
    fun team_upload_persists_owner_and_team_after_authorization() {
        `when`(references.ownerExists(uuid(1))).thenReturn(true)
        `when`(references.findTeam(uuid(4))).thenReturn(teamEntity(uuid(4)))
        stubTeamRole(uuid(4), uuid(1), Role.READ_WRITE)
        `when`(repository.save(any(Document::class.java))).thenAnswer {
            persistedDocument(it.arguments[0] as Document, uuid(21))
        }

        val result = service.uploadDocument(upload(teamId = uuid(4)))

        assertThat(result.ownerId).isEqualTo(uuid(1))
        assertThat(result.teamId).isEqualTo(uuid(4))
        assertThat(repositorySaveArgument().teamId).isEqualTo(uuid(4))
    }

    @Test
    fun upload_preserves_document_type_metadata_without_correspondent() {
        `when`(references.ownerExists(uuid(1))).thenReturn(true)
        val documentType = DocumentType(uuid(9), "Invoice", null)
        `when`(references.findDocumentType(uuid(9))).thenReturn(documentType)
        `when`(repository.save(any(Document::class.java))).thenAnswer {
            persistedDocument(it.arguments[0] as Document, uuid(22))
        }

        val uploaded = service.uploadDocument(upload(documentTypeId = uuid(9)))

        assertThat(repositorySaveArgument().documentType).isEqualTo(documentType)
        assertThat(uploaded.documentType?.name).isEqualTo("Invoice")
        assertThat(documentMapper.toDto(uploaded).documentType?.name).isEqualTo("Invoice")
    }

    @Test
    fun update_changes_document_type_metadata_without_correspondent() {
        val existing = documentEntity()
        val updatedType = DocumentType(uuid(8), "Receipt", null)
        `when`(repository.findById(uuid(1))).thenReturn(Optional.of(existing))
        `when`(references.findDocumentType(uuid(8))).thenReturn(updatedType)
        `when`(repository.save(any(Document::class.java))).thenAnswer { it.arguments[0] as Document }

        val updated = service.updateDocument(uuid(1), update(documentTypeId = uuid(8)))

        assertThat(updated.documentType?.name).isEqualTo("Receipt")
        assertThat(documentMapper.toDto(updated).documentType?.name).isEqualTo("Receipt")
    }

    @Test
    fun upload_failure_cleans_file_on_rollback() {
        `when`(references.ownerExists(uuid(1))).thenReturn(true)
        `when`(repository.save(any(Document::class.java))).thenThrow(IllegalStateException("save failed"))
        TransactionSynchronizationManager.initSynchronization()

        assertThrows<IllegalStateException> { service.uploadDocument(upload()) }

        val directory = tempDir.resolve("paperless-documents")
        assertThat(Files.list(directory).use { it.count() }).isZero()
        TransactionSynchronizationManager.getSynchronizations()
            .forEach { it.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK) }
    }

    @Test
    fun update_clears_document_type_only_when_explicitly_requested_ok() {
        // given
        val existing = documentEntity()
        `when`(repository.findById(uuid(1))).thenReturn(Optional.of(existing))
        `when`(repository.save(any(Document::class.java))).thenAnswer { it.arguments[0] as Document }

        // when
        val updated = service.updateDocument(uuid(1), update().copy(clearDocumentType = true))

        // then
        assertAll(
            { assertThat(updated.documentType).isNull() },
            { assertThat(repositorySaveArgument().documentType).isNull() },
            { assertThat(updated.title).isEqualTo(existing.title) },
            { assertThat(updated.ownerId).isEqualTo(existing.ownerId) },
            { assertThat(updated.teamId).isEqualTo(existing.teamId) }
        )
    }

    @Test
    fun update_preserves_document_type_when_clear_flag_is_absent_ok() {
        // given
        val existing = documentEntity()
        `when`(repository.findById(uuid(1))).thenReturn(Optional.of(existing))
        `when`(repository.save(any(Document::class.java))).thenAnswer { it.arguments[0] as Document }

        // when
        val updated = service.updateDocument(uuid(1), update(title = "Renamed"))

        // then
        assertAll(
            { assertThat(updated.title).isEqualTo("Renamed") },
            { assertThat(updated.documentType).isEqualTo(existing.documentType) },
            { assertThat(repositorySaveArgument().documentType).isEqualTo(existing.documentType) }
        )
    }

    @Test
    fun update_rejects_replacing_and_clearing_document_type_together_ko() {
        // given
        val conflicting = update(documentTypeId = uuid(8)).copy(clearDocumentType = true)

        // when
        val thrown = catchThrowable { service.updateDocument(uuid(1), conflicting) }

        // then
        assertAll(
            { assertThat(thrown).isInstanceOf(ConstraintViolationException::class.java) },
            { verify(repository, never()).save(any(Document::class.java)) }
        )
    }

    @Test
    fun any_current_team_writer_can_edit_metadata_without_changing_owner_or_sharing() {
        val entity = documentEntity(ownerId = uuid(1), teamId = uuid(4))
        `when`(repository.findById(uuid(1))).thenReturn(Optional.of(entity))
        stubTeamRole(uuid(4), uuid(2), Role.READ_WRITE)
        `when`(repository.save(any(Document::class.java))).thenAnswer { it.arguments[0] as Document }
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

        verify(repository, never()).save(any(Document::class.java))
    }

    @Test
    fun stale_document_version_cannot_restore_sharing_or_delete_document() {
        val entity = documentEntity(teamId = uuid(4))
        `when`(repository.findById(uuid(1))).thenReturn(Optional.of(entity))
        stubTeamRole(uuid(4), uuid(1), Role.ADMIN)
        `when`(repository.refreshUnchanged(entity)).thenThrow(OptimisticLockException("Concurrent update"))

        assertThrows<OptimisticLockException> { service.updateDocument(uuid(1), update(title = "Stale")) }
        assertThrows<OptimisticLockException> { service.deleteDocument(uuid(1)) }
        verify(repository, never()).save(any(Document::class.java))
        verify(repository, never()).delete(entity)
    }

    @Test
    fun revoked_writer_is_rejected_after_team_lock_even_if_previous_role_was_writer() {
        val entity = documentEntity(teamId = uuid(4))
        `when`(repository.findById(uuid(1))).thenReturn(Optional.of(entity))
        stubTeamRole(uuid(4), uuid(1), null)

        assertAppError(DocumentNotFoundException::class) { service.updateDocument(uuid(1), update(clearTeam = true)) }
        assertAppError(DocumentNotFoundException::class) { service.deleteDocument(uuid(1)) }
        verify(repository, never()).delete(entity)
    }

    @Test
    fun uploader_can_move_or_clear_sharing_with_write_access_to_source_and_destination() {
        val entity = documentEntity(ownerId = uuid(1), teamId = uuid(4))
        `when`(repository.findById(uuid(1))).thenReturn(Optional.of(entity))
        stubTeamRole(uuid(4), uuid(1), Role.READ_WRITE)
        stubTeamRole(uuid(5), uuid(1), Role.ADMIN)
        `when`(references.findTeam(uuid(5))).thenReturn(teamEntity(uuid(5)))
        `when`(repository.save(any(Document::class.java))).thenAnswer { it.arguments[0] as Document }

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
        stubTeamRole(uuid(4), uuid(2), Role.READ_WRITE)
        stubTeamRole(uuid(5), uuid(2), null)
        setActor(uuid(2))

        assertDenied { service.updateDocument(uuid(1), update(teamId = uuid(5))) }
        verify(repository, never()).save(any(Document::class.java))

        setActor(uuid(1))
        stubTeamRole(uuid(4), uuid(1), Role.ADMIN)
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
        `when`(references.ownerExists(uuid(1))).thenReturn(true)
        stubTeamRole(uuid(4), uuid(1), null)
        assertDenied { service.uploadDocument(upload(teamId = uuid(4))) }

        `when`(references.findLockedTeam(uuid(8))).thenReturn(null)
        assertAppError(TeamNotFoundException::class) { service.uploadDocument(upload(teamId = uuid(8))) }
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

    @Test
    fun sharing_update_locks_all_teams_in_sorted_order_before_reading_roles_ok() {
        // given
        val existing = documentEntity(teamId = uuid(9))
        `when`(repository.findById(uuid(1))).thenReturn(Optional.of(existing))
        stubTeamRole(uuid(9), uuid(1), Role.READ_WRITE)
        stubTeamRole(uuid(3), uuid(1), Role.ADMIN)
        `when`(references.findTeam(uuid(3))).thenReturn(teamEntity(uuid(3)))
        `when`(repository.save(any(Document::class.java))).thenAnswer { it.arguments[0] as Document }

        // when
        val updated = service.updateDocument(uuid(1), update(teamId = uuid(3)))

        // then
        assertThat(updated.teamId).isEqualTo(uuid(3))
        val ordered = inOrder(references, teamMemberRepository, repository)
        ordered.verify(references).findLockedTeam(uuid(3))
        ordered.verify(references).findLockedTeam(uuid(9))
        ordered.verify(teamMemberRepository).findByTeamIdAndUserId(uuid(3), uuid(1))
        ordered.verify(teamMemberRepository).findByTeamIdAndUserId(uuid(9), uuid(1))
        ordered.verify(repository).refreshUnchanged(existing)
    }

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

    private fun membership(teamId: UUID, userId: UUID, role: Role) =
        TeamMember(teamEntity(teamId), userEntity().copy(id = userId), role, Instant.EPOCH)

    private fun stubTeamRole(teamId: UUID, userId: UUID, role: Role?) {
        `when`(references.findLockedTeam(teamId)).thenReturn(teamEntity(teamId))
        `when`(teamMemberRepository.findByTeamIdAndUserId(teamId, userId))
            .thenReturn(role?.let { membership(teamId, userId, it) })
    }

    private fun userEntity() = User(uuid(1), "uploader", "hash", Instant.EPOCH, null)

    private fun teamEntity(id: UUID) = Team(id, "Team $id", null, Instant.EPOCH, null, uuid(1))

    private fun repositorySaveArgument(): Document =
        org.mockito.Mockito.mockingDetails(repository).getInvocations()
            .last { it.method.name == "save" }.arguments[0] as Document

    private fun persistedDocument(document: Document, id: UUID) = document.copy(id = id)

    private fun assertAppError(expected: KClass<out AppException>, action: () -> Unit) {
        val thrown = catchThrowable(action)
        assertThat(thrown).isInstanceOf(expected.java)
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
    ) = Document(
        id = uuid(1),
        title = "Invoice",
        originalFilename = "invoice.pdf",
        contentType = "application/pdf",
        fileSize = 10,
        ownerId = ownerId,
        teamId = teamId,
        status = ProcessingStatus.COMPLETED,
        ocrContent = "Invoice from Acme",
        summary = "An invoice",
        storageKey = "documents/existing.pdf",
        documentType = DocumentType(uuid(9), "Invoice", null),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )

    private fun Document.withStorageKey(value: String?) = copy(storageKey = value)
}
