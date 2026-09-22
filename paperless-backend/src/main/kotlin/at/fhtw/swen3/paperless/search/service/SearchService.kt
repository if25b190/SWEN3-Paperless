package at.fhtw.swen3.paperless.search.service

import at.fhtw.swen3.paperless.search.model.SearchResult
import org.springframework.data.domain.Page

interface SearchService {

    fun search(query: String, fuzzy: Boolean, page: Int, size: Int): Page<SearchResult>
}
