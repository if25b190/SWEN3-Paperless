package at.fhtw.swen3.paperless.document.service

import at.fhtw.swen3.paperless.document.entity.DocumentEntity
import at.fhtw.swen3.paperless.document.mapper.DocumentEntityMapper
import at.fhtw.swen3.paperless.document.model.Document
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
import jakarta.persistence.EntityNotFoundException
import jakarta.persistence.OptimisticLockException
import jakarta.validation.ConstraintViolationException
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
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class DocumentServiceImpl(
    private val repository: DocumentRepository,
    private val entityManager: EntityManager,
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
        return documents.map(DocumentEntityMapper::toModel)
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
            .map(DocumentEntityMapper::toModel)
    }

    @Transactional(readOnly = true)
    override fun getDocument(id: UUID): Document =
        DocumentEntityMapper.toModel(requireReadable(findEntity(id), currentActorId()))

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
                DocumentEntity(
                    title = upload.title,
                    originalFilename = upload.originalFilename,
                    contentType = upload.contentType,
                    fileSize = upload.content.size.toLong(),
                    owner = references.owner,
                    team = references.team,
                    status = ProcessingStatus.PENDING,
                    storageKey = storageKey,
                    documentType = references.documentType,
                    createdAt = now,
                    updatedAt = now
                )
            )
            DocumentEntityMapper.toModel(entity)
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
        val initial = findEntity(id)
        val sourceTeamId = initial.team?.id
        val hasSharingUpdate = update.teamId != null || update.clearTeam
        val requestedTeamId = when {
            update.clearTeam -> null
            update.teamId != null -> update.teamId
            else -> sourceTeamId
        }
        val roles = lockedRoles(setOfNotNull(sourceTeamId, requestedTeamId), actorId)
        val existing = requireWritable(refreshUnchanged(initial, sourceTeamId), actorId, roles)
        update.title?.let(::validateTitle)
        var updatedTeam = existing.team

        if (hasSharingUpdate) {
            if (actorId != existing.owner.id) {
                throw AccessDeniedException("Only the document uploader can change sharing")
            }
            if (requestedTeamId == existing.team?.id) {
                throw DataIntegrityViolationException("The requested sharing transition conflicts with current sharing state")
            }
            updatedTeam = requestedTeamId?.let {
                if (roles[it]?.canWrite() != true) throw AccessDeniedException("Write access to the team is required")
                entityManager.find(TeamEntity::class.java, it) ?: throw AppException(AppErrorMessage.TEAM_NOT_FOUND)
            }
        }

        val documentType = update.documentTypeId?.let {
            entityManager.find(DocumentTypeEntity::class.java, it)
                ?: throw AppException(AppErrorMessage.DOCUMENT_TYPE_NOT_FOUND)
        } ?: existing.documentType

        val updated = DocumentEntity(
            id = existing.id,
            title = update.title ?: existing.title,
            originalFilename = existing.originalFilename,
            contentType = existing.contentType,
            fileSize = existing.fileSize,
            owner = existing.owner,
            team = updatedTeam,
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
        entityManager.flush()
        return DocumentEntityMapper.toModel(result)
    }

    override fun deleteDocument(id: UUID) {
        val actorId = currentActorId()
        val initial = findEntity(id)
        val sourceTeamId = initial.team?.id
        val roles = lockedRoles(setOfNotNull(sourceTeamId), actorId)
        val entity = requireWritable(refreshUnchanged(initial, sourceTeamId), actorId, roles)
        repository.delete(entity)
        entityManager.flush()
        entity.storageKey?.let { deleteAfterCommit(fileFor(it)) }
    }

    @Transactional(readOnly = true)
    override fun downloadDocument(id: UUID): Resource {
        val entity = requireReadable(findEntity(id), currentActorId())
        val storageKey = entity.storageKey ?: throw AppException(AppErrorMessage.DOCUMENT_NOT_FOUND)
        val resource = FileSystemResource(fileFor(storageKey))
        if (!resource.exists() || !resource.isReadable) {
            throw AppException(AppErrorMessage.DOCUMENT_NOT_FOUND)
        }
        return resource
    }

    private fun validateUploadReferences(upload: DocumentUpload, actorId: UUID): UploadReferences {
        val owner = entityManager.find(UserEntity::class.java, actorId)
            ?: throw AppException(AppErrorMessage.USER_NOT_FOUND)
        val documentType = upload.documentTypeId?.let {
            entityManager.find(DocumentTypeEntity::class.java, it)
                ?: throw AppException(AppErrorMessage.DOCUMENT_TYPE_NOT_FOUND)
        }
        val team = upload.teamId?.let {
            val roles = lockedRoles(setOf(it), actorId)
            if (roles[it]?.canWrite() != true) throw AccessDeniedException("Write access to the team is required")
            entityManager.find(TeamEntity::class.java, it) ?: throw AppException(AppErrorMessage.TEAM_NOT_FOUND)
        }
        return UploadReferences(owner, team, documentType)
    }

    private fun lockedRoles(teamIds: Set<UUID>, actorId: UUID): Map<UUID, Role?> =
        if (teamIds.isEmpty()) emptyMap() else teamService.lockedRoles(teamIds.toSortedSet(), actorId)

    private fun refreshUnchanged(entity: DocumentEntity, sourceTeamId: UUID?): DocumentEntity {
        val originalVersion = entity.version
        val ownerId = entity.owner.id
        try {
            entityManager.refresh(entity)
        } catch (exception: EntityNotFoundException) {
            throw OptimisticLockException("Document was deleted concurrently", exception)
        }
        if (entity.version != originalVersion || entity.team?.id != sourceTeamId || entity.owner.id != ownerId) {
            throw OptimisticLockException("Document sharing changed concurrently")
        }
        return entity
    }

    private fun requireReadable(entity: DocumentEntity, actorId: UUID): DocumentEntity {
        val team = entity.team
        val canRead = if (team == null) {
            entity.owner.id == actorId
        } else {
            teamService.roleFor(requireNotNull(team.id) { "Persisted document team must have an ID" }, actorId) != null
        }
        if (!canRead) throw AppException(AppErrorMessage.DOCUMENT_NOT_FOUND)
        return entity
    }

    private fun requireWritable(entity: DocumentEntity, actorId: UUID, roles: Map<UUID, Role?>): DocumentEntity {
        val team = entity.team
        if (team == null) {
            if (entity.owner.id != actorId) throw AppException(AppErrorMessage.DOCUMENT_NOT_FOUND)
        } else {
            val teamId = requireNotNull(team.id) { "Persisted document team must have an ID" }
            val role = roles[teamId]
                ?: throw AppException(AppErrorMessage.DOCUMENT_NOT_FOUND)
            if (!role.canWrite()) throw AccessDeniedException("Write access to the document is required")
        }
        return entity
    }

    private fun Role.canWrite(): Boolean = this == Role.READ_WRITE || this == Role.ADMIN

    private fun findEntity(id: UUID): DocumentEntity =
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
        val owner: UserEntity,
        val team: TeamEntity?,
        val documentType: DocumentTypeEntity?
    )

    private companion object {
        const val MAX_TITLE_LENGTH = 255
        val logger = LoggerFactory.getLogger(DocumentServiceImpl::class.java)
    }
}
