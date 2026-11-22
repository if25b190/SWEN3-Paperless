package at.fhtw.swen3.paperless.search.persistence

import at.fhtw.swen3.paperless.search.model.SearchHit
import at.fhtw.swen3.paperless.search.model.SearchQuery
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.util.UUID

interface SearchPersistence {
    fun search(documentIds: Set<UUID>, query: SearchQuery, pageable: Pageable): Page<SearchHit>
}
