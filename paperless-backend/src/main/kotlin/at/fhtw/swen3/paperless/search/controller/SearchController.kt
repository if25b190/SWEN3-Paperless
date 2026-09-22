package at.fhtw.swen3.paperless.search.controller

import at.fhtw.swen3.paperless.api.SearchApi
import at.fhtw.swen3.paperless.dto.PageMetadata
import at.fhtw.swen3.paperless.dto.SearchResponse
import at.fhtw.swen3.paperless.search.mapper.SearchMapper
import at.fhtw.swen3.paperless.search.service.SearchService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class SearchController(private val searchService: SearchService) : SearchApi {

    override fun searchDocumentsFullText(
        query: String,
        fuzzy: Boolean,
        page: Int,
        size: Int
    ): ResponseEntity<SearchResponse> {
        val results = searchService.search(query, fuzzy, page, size)
        val items = results.content.map(SearchMapper::toDto)
        val pagination = PageMetadata(page, size, results.totalElements, results.totalPages)
        return ResponseEntity.ok(SearchResponse(pagination, items))
    }
}
