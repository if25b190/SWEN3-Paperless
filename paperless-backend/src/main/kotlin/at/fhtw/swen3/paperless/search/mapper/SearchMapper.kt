package at.fhtw.swen3.paperless.search.mapper

import at.fhtw.swen3.paperless.document.mapper.DocumentMapper
import at.fhtw.swen3.paperless.dto.SearchResultItem
import at.fhtw.swen3.paperless.search.model.SearchResult

object SearchMapper {

    fun toDto(result: SearchResult): SearchResultItem =
        SearchResultItem(
            document = DocumentMapper.toDto(result.document),
            score = result.score,
            highlights = result.highlights.ifEmpty { null }
        )
}
