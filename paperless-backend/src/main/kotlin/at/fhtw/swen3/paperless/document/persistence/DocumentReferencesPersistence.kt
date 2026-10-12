package at.fhtw.swen3.paperless.document.persistence

import at.fhtw.swen3.paperless.documenttype.model.DocumentType
import at.fhtw.swen3.paperless.team.model.Team
import java.util.UUID

interface DocumentReferencesPersistence {
    fun ownerExists(id: UUID): Boolean
    fun findTeam(id: UUID): Team?
    fun findDocumentType(id: UUID): DocumentType?
}
