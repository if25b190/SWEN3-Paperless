package at.fhtw.swen3.paperless.search.persistence

import at.fhtw.swen3.paperless.ocr.config.OcrProcessingProperties
import at.fhtw.swen3.paperless.search.model.SearchHit
import at.fhtw.swen3.paperless.search.model.SearchQuery
import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch.core.SearchRequest
import co.elastic.clients.elasticsearch.core.search.HighlightField
import co.elastic.clients.elasticsearch.core.search.HighlighterEncoder
import co.elastic.clients.util.NamedValue
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class ElasticsearchSearchPersistence(
    private val client: ElasticsearchClient,
    private val properties: OcrProcessingProperties,
) : SearchPersistence {
    override fun search(documentIds: Set<UUID>, query: SearchQuery, pageable: Pageable): Page<SearchHit> {
        if (documentIds.isEmpty() || query.text.isBlank()) return Page.empty(pageable)

        // There cannot be more hits than visible documents. Count without a deep offset for empty pages.
        val beyondCandidates = pageable.offset >= documentIds.size.toLong()
        val request = SearchRequest.of { search ->
            search.index(properties.indexName)
                .ignoreUnavailable(true)
                .allowNoIndices(true)
                .from(if (beyondCandidates) 0 else pageable.offset.toInt())
                .size(if (beyondCandidates) 0 else pageable.pageSize)
                .trackTotalHits { it.enabled(true) }
                .source { it.fetch(false) }
                .query { root ->
                    root.bool { bool ->
                        bool.filter { filter -> filter.ids { it.values(documentIds.map(UUID::toString)) } }
                            .must { must ->
                                must.match { match ->
                                    match.field(FIELD_CONTENT).query(query.text)
                                        .fuzziness(if (query.fuzzy) "AUTO" else "0")
                                }
                            }
                    }
                }
                .highlight { highlight ->
                    highlight.encoder(HighlighterEncoder.Html)
                        .fields(
                            NamedValue.of(
                                FIELD_CONTENT,
                                HighlightField.of {
                                    it.numberOfFragments(MAX_HIGHLIGHTS)
                                },
                            ),
                        )
                }
        }
        val response = client.search(request, Void::class.java)
        val hits = response.hits().hits().map { hit ->
            SearchHit(
                documentId = UUID.fromString(hit.id()),
                score = hit.score()?.toFloat() ?: 0.0f,
                highlights = hit.highlight()[FIELD_CONTENT].orEmpty(),
            )
        }

        return PageImpl(hits, pageable, response.hits().total()?.value() ?: 0L)
    }

    private companion object {
        const val FIELD_CONTENT = "content"
        const val MAX_HIGHLIGHTS = 3
    }
}
