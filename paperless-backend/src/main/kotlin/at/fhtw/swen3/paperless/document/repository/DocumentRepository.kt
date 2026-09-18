package at.fhtw.swen3.paperless.document.repository

import at.fhtw.swen3.paperless.document.entity.DocumentEntity
import org.springframework.data.jpa.repository.JpaRepository

interface DocumentRepository : JpaRepository<DocumentEntity, Long>