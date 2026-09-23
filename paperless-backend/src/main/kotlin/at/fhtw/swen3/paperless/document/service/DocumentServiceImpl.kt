package at.fhtw.swen3.paperless.document.service

import at.fhtw.swen3.paperless.document.entity.DocumentEntity
import at.fhtw.swen3.paperless.document.mapper.DocumentEntityMapper
import at.fhtw.swen3.paperless.document.model.Document
import at.fhtw.swen3.paperless.document.model.DocumentUpdate
import at.fhtw.swen3.paperless.document.model.DocumentUpload
import at.fhtw.swen3.paperless.document.model.ProcessingStatus
import at.fhtw.swen3.paperless.document.repository.DocumentRepository
import at.fhtw.swen3.paperless.correspondent.entity.CorrespondentEntity
import at.fhtw.swen3.paperless.documenttype.entity.DocumentTypeEntity
import at.fhtw.swen3.paperless.exception.AppErrorMessage
import at.fhtw.swen3.paperless.exception.AppException
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class DocumentServiceImpl(
    private val repository: DocumentRepository,
    private val entityManager: EntityManager
) : DocumentService {

    private val storageDirectory: Path = Paths.get(System.getProperty("java.io.tmpdir"), "paperless-documents")

    override fun searchDocuments(
        page: Int,
        size: Int,
        sort: String,
        correspondentId: Long?,
        documentTypeId: Long?
    ): Page<Document> {
        val pageable = PageRequest.of(page, size, toSort(sort))
        val documents = when {
            correspondentId != null && documentTypeId != null ->
                repository.findByCorrespondent_IdAndDocumentType_Id(correspondentId, documentTypeId, pageable)
            correspondentId != null -> repository.findByCorrespondent_Id(correspondentId, pageable)
            documentTypeId != null -> repository.findByDocumentType_Id(documentTypeId, pageable)
            else -> repository.findAll(pageable)
        }
        return documents.map(DocumentEntityMapper::toModel)
    }

    override fun getDocument(id: Long): Document = DocumentEntityMapper.toModel(findEntity(id))

    override fun uploadDocument(upload: DocumentUpload): Document {
        val storageKey = "documents/${UUID.randomUUID()}_${safeFilename(upload.originalFilename)}"
        val file = storageDirectory.resolve(storageKey.substringAfterLast('/'))
        Files.createDirectories(storageDirectory)
        Files.write(file, upload.content)

        return try {
            val now = Instant.now()
            val entity = repository.save(
                DocumentEntity(
                    title = upload.title,
                    originalFilename = upload.originalFilename,
                    contentType = upload.contentType,
                    fileSize = upload.content.size.toLong(),
                    status = ProcessingStatus.PENDING,
                    storageKey = storageKey,
                    correspondent = upload.correspondentId?.let {
                        entityManager.getReference(CorrespondentEntity::class.java, it)
                    },
                    documentType = upload.documentTypeId?.let {
                        entityManager.getReference(DocumentTypeEntity::class.java, it)
                    },
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

    override fun updateDocument(id: Long, update: DocumentUpdate): Document {
        val existing = findEntity(id)
        val now = Instant.now()
        val updated = DocumentEntity(
            id = existing.id,
            title = update.title ?: existing.title,
            originalFilename = existing.originalFilename,
            contentType = existing.contentType,
            fileSize = existing.fileSize,
            status = existing.status,
            ocrContent = existing.ocrContent,
            summary = existing.summary,
            storageKey = existing.storageKey,
            correspondent = update.correspondentId?.let {
                entityManager.getReference(CorrespondentEntity::class.java, it)
            } ?: existing.correspondent,
            documentType = update.documentTypeId?.let {
                entityManager.getReference(DocumentTypeEntity::class.java, it)
            } ?: existing.documentType,
            createdAt = existing.createdAt,
            updatedAt = now
        )
        return DocumentEntityMapper.toModel(repository.save(updated))
    }

    override fun deleteDocument(id: Long) {
        val entity = findEntity(id)
        repository.delete(entity)
        entity.storageKey?.let { Files.deleteIfExists(storageDirectory.resolve(it.substringAfterLast('/'))) }
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    override fun downloadDocument(id: Long): Resource {
        val entity = findEntity(id)
        val storageKey = entity.storageKey ?: throw AppException(AppErrorMessage.DOCUMENT_NOT_FOUND)
        val resource = FileSystemResource(storageDirectory.resolve(storageKey.substringAfterLast('/')))
        if (!resource.exists() || !resource.isReadable) {
            throw AppException(AppErrorMessage.DOCUMENT_NOT_FOUND)
        }
        return resource
    }

    private fun findEntity(id: Long): DocumentEntity =
        repository.findById(id).orElseThrow { AppException(AppErrorMessage.DOCUMENT_NOT_FOUND) }

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
}
