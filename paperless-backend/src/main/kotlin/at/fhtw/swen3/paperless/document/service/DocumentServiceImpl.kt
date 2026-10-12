package at.fhtw.swen3.paperless.document.service

import at.fhtw.swen3.paperless.document.model.Document
import at.fhtw.swen3.paperless.document.model.DocumentUpdate
import at.fhtw.swen3.paperless.document.model.DocumentUpload
import at.fhtw.swen3.paperless.document.model.ProcessingStatus
import at.fhtw.swen3.paperless.document.persistence.DocumentPersistence
import at.fhtw.swen3.paperless.document.persistence.DocumentReferencesPersistence
import at.fhtw.swen3.paperless.documenttype.model.DocumentType
import at.fhtw.swen3.paperless.exception.AppErrorMessage
import at.fhtw.swen3.paperless.exception.AppException
import at.fhtw.swen3.paperless.team.model.Role
import at.fhtw.swen3.paperless.team.service.TeamService
import jakarta.validation.ConstraintViolationException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

@Service
@Transactional
class DocumentServiceImpl(
    private val repository: DocumentPersistence,
    private val referencesPersistence: DocumentReferencesPersistence,
    private val teamService: TeamService
) : DocumentService {

    private val storageDirectory: Path = Paths.get(System.getProperty("java.io.tmpdir"), "paperless-documents")

    @Transactional(readOnly = true)
    override fun searchDocuments(
        page: Int,
        size: Int,
        sort: String,
        documentTypeId: UUID?
    ): Page<Document> {
        val actorId = currentActorId()
        val teamIds = teamService.visibleTeamIds(actorId)
        val pageable = PageRequest.of(page, size, toSort(sort))
        val documents = if (teamIds.isEmpty()) {
            repository.findOwnerOnlyDocuments(actorId, documentTypeId, pageable)
        } else {
            repository.findVisibleDocuments(actorId, teamIds, documentTypeId, pageable)
        }
        return documents
    }

    @Transactional(readOnly = true)
    override fun visibleDocumentsForSearch(): List<Document> {
        val actorId = currentActorId()
        val teamIds = teamService.visibleTeamIds(actorId)
        val documents = if (teamIds.isEmpty()) {
            repository.findOwnerOnlyDocuments(actorId)
        } else {
            repository.findVisibleDocuments(actorId, teamIds)
        }
        return documents
    }

    @Transactional(readOnly = true)
    override fun getDocument(id: UUID): Document =
        requireReadable(findDocument(id), currentActorId())

    override fun uploadDocument(upload: DocumentUpload): Document {
        val actorId = currentActorId()
        val references = validateUploadReferences(upload, actorId)
        validateTitle(upload.title)
        val storageKey = "documents/${UUID.randomUUID()}_${safeFilename(upload.originalFilename)}"
        val file = fileFor(storageKey)

        try {
            Files.createDirectories(storageDirectory)
            Files.write(file, upload.content)
        } catch (exception: RuntimeException) {
            Files.deleteIfExists(file)
            throw exception
        } catch (exception: java.io.IOException) {
            Files.deleteIfExists(file)
            throw exception
        }
        registerRollbackCleanup(file)

        return try {
            val now = Instant.now()
            val entity = repository.save(
                Document(
                    id = null,
                    ocrContent = null,
                    summary = null,
                    title = upload.title,
                    originalFilename = upload.originalFilename,
                    contentType = upload.contentType,
                    fileSize = upload.content.size.toLong(),
                    ownerId = actorId,
                    teamId = references.teamId,
                    status = ProcessingStatus.PENDING,
                    storageKey = storageKey,
                    documentType = references.documentType,
                    createdAt = now,
                    updatedAt = now
                )
            )
            entity
        } catch (exception: RuntimeException) {
            Files.deleteIfExists(file)
            throw exception
        }
    }

    override fun updateDocument(id: UUID, update: DocumentUpdate): Document {
        if (update.teamId != null && update.clearTeam) {
            throw ConstraintViolationException("team_id and clear_team cannot both be set", emptySet())
        }

        val actorId = currentActorId()
        val initial = findDocument(id)
        val sourceTeamId = initial.teamId
        val hasSharingUpdate = update.teamId != null || update.clearTeam
        val requestedTeamId = when {
            update.clearTeam -> null
            update.teamId != null -> update.teamId
            else -> sourceTeamId
        }
        val roles = lockedRoles(setOfNotNull(sourceTeamId, requestedTeamId), actorId)
        val existing = requireWritable(repository.refreshUnchanged(initial), actorId, roles)
        update.title?.let(::validateTitle)
        var updatedTeamId = existing.teamId

        if (hasSharingUpdate) {
            if (actorId != existing.ownerId) {
                throw AccessDeniedException("Only the document uploader can change sharing")
            }
            if (requestedTeamId == existing.teamId) {
                throw DataIntegrityViolationException("The requested sharing transition conflicts with current sharing state")
            }
            updatedTeamId = requestedTeamId?.let {
                if (roles[it]?.canWrite() != true) throw AccessDeniedException("Write access to the team is required")
                referencesPersistence.findTeam(it)?.id ?: throw AppException(AppErrorMessage.TEAM_NOT_FOUND)
            }
        }

        val documentType = update.documentTypeId?.let {
            referencesPersistence.findDocumentType(it)
                ?: throw AppException(AppErrorMessage.DOCUMENT_TYPE_NOT_FOUND)
        } ?: existing.documentType

        val updated = Document(
            id = existing.id,
            title = update.title ?: existing.title,
            originalFilename = existing.originalFilename,
            contentType = existing.contentType,
            fileSize = existing.fileSize,
            ownerId = existing.ownerId,
            teamId = updatedTeamId,
            status = existing.status,
            ocrContent = existing.ocrContent,
            summary = existing.summary,
            storageKey = existing.storageKey,
            documentType = documentType,
            createdAt = existing.createdAt,
            updatedAt = Instant.now(),
            version = existing.version
        )
        val result = repository.save(updated)
        repository.flush()
        return result
    }

    override fun deleteDocument(id: UUID) {
        val actorId = currentActorId()
        val initial = findDocument(id)
        val sourceTeamId = initial.teamId
        val roles = lockedRoles(setOfNotNull(sourceTeamId), actorId)
        val entity = requireWritable(repository.refreshUnchanged(initial), actorId, roles)
        repository.delete(entity)
        repository.flush()
        entity.storageKey?.let { deleteAfterCommit(fileFor(it)) }
    }

    @Transactional(readOnly = true)
    override fun downloadDocument(id: UUID): Resource {
        val entity = requireReadable(findDocument(id), currentActorId())
        val storageKey = entity.storageKey ?: throw AppException(AppErrorMessage.DOCUMENT_NOT_FOUND)
        val resource = FileSystemResource(fileFor(storageKey))
        if (!resource.exists() || !resource.isReadable) {
            throw AppException(AppErrorMessage.DOCUMENT_NOT_FOUND)
        }
        return resource
    }

    private fun validateUploadReferences(upload: DocumentUpload, actorId: UUID): UploadReferences {
        if (!referencesPersistence.ownerExists(actorId)) throw AppException(AppErrorMessage.USER_NOT_FOUND)
        val documentType = upload.documentTypeId?.let {
            referencesPersistence.findDocumentType(it)
                ?: throw AppException(AppErrorMessage.DOCUMENT_TYPE_NOT_FOUND)
        }
        val teamId = upload.teamId?.let {
            val roles = lockedRoles(setOf(it), actorId)
            if (roles[it]?.canWrite() != true) throw AccessDeniedException("Write access to the team is required")
            referencesPersistence.findTeam(it)?.id ?: throw AppException(AppErrorMessage.TEAM_NOT_FOUND)
        }

        return UploadReferences(teamId, documentType)
    }

    private fun lockedRoles(teamIds: Set<UUID>, actorId: UUID): Map<UUID, Role?> =
        if (teamIds.isEmpty()) emptyMap() else teamService.lockedRoles(teamIds.toSortedSet(), actorId)

    private fun requireReadable(entity: Document, actorId: UUID): Document {
        val teamId = entity.teamId
        val canRead = if (teamId == null) {
            entity.ownerId == actorId
        } else {
            teamService.roleFor(teamId, actorId) != null
        }
        if (!canRead) throw AppException(AppErrorMessage.DOCUMENT_NOT_FOUND)
        return entity
    }

    private fun requireWritable(entity: Document, actorId: UUID, roles: Map<UUID, Role?>): Document {
        val teamId = entity.teamId
        if (teamId == null) {
            if (entity.ownerId != actorId) throw AppException(AppErrorMessage.DOCUMENT_NOT_FOUND)
        } else {
            val role = roles[teamId]
                ?: throw AppException(AppErrorMessage.DOCUMENT_NOT_FOUND)
            if (!role.canWrite()) throw AccessDeniedException("Write access to the document is required")
        }
        return entity
    }

    private fun Role.canWrite(): Boolean = this == Role.READ_WRITE || this == Role.ADMIN

    private fun findDocument(id: UUID): Document =
        repository.findById(id).orElseThrow { AppException(AppErrorMessage.DOCUMENT_NOT_FOUND) }

    private fun currentActorId(): UUID =
        SecurityContextHolder.getContext().authentication?.name?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: throw AppException(AppErrorMessage.AUTHENTICATION_REQUIRED)

    private fun validateTitle(title: String) {
        if (title.isBlank() || title.length > MAX_TITLE_LENGTH) {
            throw ConstraintViolationException("title must be nonblank and at most $MAX_TITLE_LENGTH characters", emptySet())
        }
    }

    private fun registerRollbackCleanup(file: Path) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCompletion(status: Int) {
                if (status == TransactionSynchronization.STATUS_ROLLED_BACK) deleteQuietly(file)
            }
        })
    }

    private fun deleteAfterCommit(file: Path) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            Files.deleteIfExists(file)
            return
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                deleteQuietly(file)
            }
        })
    }

    private fun deleteQuietly(file: Path) {
        try {
            Files.deleteIfExists(file)
        } catch (exception: Exception) {
            logger.warn("Could not delete document file {}", file, exception)
        }
    }

    private fun fileFor(storageKey: String): Path = storageDirectory.resolve(storageKey.substringAfterLast('/'))

    private fun toSort(value: String): Sort {
        val parts = value.split(',', limit = 2)
        val property = when (parts.first()) {
            "created_at" -> "createdAt"
            "updated_at" -> "updatedAt"
            "title" -> "title"
            else -> "createdAt"
        }
        val direction = if (parts.getOrNull(1)?.equals("asc", ignoreCase = true) == true) {
            Sort.Direction.ASC
        } else {
            Sort.Direction.DESC
        }
        return Sort.by(direction, property)
    }

    private fun safeFilename(filename: String): String =
        filename.substringAfterLast('/').substringAfterLast('\\').replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { "document" }

    private data class UploadReferences(
        val teamId: UUID?,
        val documentType: DocumentType?
    )

    private companion object {
        const val MAX_TITLE_LENGTH = 255
        val logger = LoggerFactory.getLogger(DocumentServiceImpl::class.java)
    }
}
