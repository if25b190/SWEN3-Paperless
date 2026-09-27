package at.fhtw.swen3.paperless.document.repository

import at.fhtw.swen3.paperless.document.entity.DocumentEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface DocumentRepository : JpaRepository<DocumentEntity, UUID> {

    @Query(
        value = """
            select d from DocumentEntity d
            left join d.documentType t
            where ((d.team is null and d.owner.id = :ownerId) or d.team.id in :teamIds)
              and (:documentTypeId is null or t.id = :documentTypeId)
        """,
        countQuery = """
            select count(d) from DocumentEntity d
            left join d.documentType t
            where ((d.team is null and d.owner.id = :ownerId) or d.team.id in :teamIds)
              and (:documentTypeId is null or t.id = :documentTypeId)
        """
    )
    fun findVisibleDocuments(
        @Param("ownerId") ownerId: UUID,
        @Param("teamIds") teamIds: Set<UUID>,
        @Param("documentTypeId") documentTypeId: UUID?,
        pageable: Pageable
    ): Page<DocumentEntity>

    @Query(
        value = """
            select d from DocumentEntity d
            left join d.documentType t
            where d.team is null and d.owner.id = :ownerId
              and (:documentTypeId is null or t.id = :documentTypeId)
        """,
        countQuery = """
            select count(d) from DocumentEntity d
            left join d.documentType t
            where d.team is null and d.owner.id = :ownerId
              and (:documentTypeId is null or t.id = :documentTypeId)
        """
    )
    fun findOwnerOnlyDocuments(
        @Param("ownerId") ownerId: UUID,
        @Param("documentTypeId") documentTypeId: UUID?,
        pageable: Pageable
    ): Page<DocumentEntity>

    @Query(
        """
            select d from DocumentEntity d
            where (d.team is null and d.owner.id = :ownerId) or d.team.id in :teamIds
        """
    )
    fun findVisibleDocuments(
        @Param("ownerId") ownerId: UUID,
        @Param("teamIds") teamIds: Set<UUID>
    ): List<DocumentEntity>

    @Query(
        """
            select d from DocumentEntity d
            where d.team is null and d.owner.id = :ownerId
        """
    )
    fun findOwnerOnlyDocuments(@Param("ownerId") ownerId: UUID): List<DocumentEntity>
}
