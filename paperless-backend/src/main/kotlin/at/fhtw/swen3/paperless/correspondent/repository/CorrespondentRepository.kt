package at.fhtw.swen3.paperless.correspondent.repository

import at.fhtw.swen3.paperless.correspondent.entity.CorrespondentEntity
import org.springframework.data.jpa.repository.JpaRepository

interface CorrespondentRepository : JpaRepository<CorrespondentEntity, Long> {

    fun findByName(name: String): CorrespondentEntity?
}
