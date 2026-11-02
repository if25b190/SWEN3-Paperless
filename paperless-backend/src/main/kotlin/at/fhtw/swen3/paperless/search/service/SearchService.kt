package at.fhtw.swen3.paperless.search.service

import at.fhtw.swen3.paperless.search.model.SearchResult
import java.util.UUID
import org.springframework.data.domain.Page

interface SearchService {

    fun search(actorId: UUID, query: String, fuzzy: Boolean, page: Int, size: Int): Page<SearchResult>
}
