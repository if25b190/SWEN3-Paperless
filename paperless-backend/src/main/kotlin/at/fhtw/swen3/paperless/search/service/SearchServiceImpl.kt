package at.fhtw.swen3.paperless.search.service

import at.fhtw.swen3.paperless.document.persistence.DocumentPersistence
import at.fhtw.swen3.paperless.search.model.SearchQuery
import at.fhtw.swen3.paperless.search.model.SearchResult
import at.fhtw.swen3.paperless.search.persistence.SearchPersistence
import at.fhtw.swen3.paperless.team.persistence.TeamMemberPersistence
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class SearchServiceImpl(
    private val documentRepository: DocumentPersistence,
    private val teamMemberRepository: TeamMemberPersistence,
    private val searchPersistence: SearchPersistence,
) : SearchService {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun search(actorId: UUID, query: String, fuzzy: Boolean, page: Int, size: Int): Page<SearchResult> {
        val request = PageRequest.of(page, size)
        if (query.isBlank()) return Page.empty(request)

        val teamIds = teamMemberRepository.findByUserId(actorId).mapTo(mutableSetOf()) { requireNotNull(it.team.id) }
        val documents = if (teamIds.isEmpty()) {
            documentRepository.findOwnerOnlyDocuments(actorId)
        } else {
            documentRepository.findVisibleDocuments(actorId, teamIds)
        }
        if (documents.isEmpty()) return Page.empty(request)

        val documentsById = documents.associateBy { requireNotNull(it.id) }
        val hits = searchPersistence.search(documentsById.keys, SearchQuery(query.trim(), fuzzy), request)
        val results = hits.content.mapNotNull { hit ->
            documentsById[hit.documentId]?.let { document -> SearchResult(document, hit.score, hit.highlights) }
        }
        log.debug("[SEARCH] - ACTION: search: page: {}, size: {}, resultCount: {}", page, size, hits.totalElements)

        return PageImpl(results, request, hits.totalElements)
    }
}
