package at.fhtw.swen3.paperless.search.persistence

import at.fhtw.swen3.paperless.ocr.config.OcrProcessingProperties
import at.fhtw.swen3.paperless.search.model.SearchQuery
import at.fhtw.swen3.paperless.testsupport.any
import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch.core.SearchRequest
import co.elastic.clients.elasticsearch.core.SearchResponse
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.eq
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.PageRequest
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class ElasticsearchSearchPersistenceTest {
    @Mock lateinit var client: ElasticsearchClient

    @Test
    fun search_scopes_hits_and_totals_to_visible_ids_ok() {
        // given
        val documentId = UUID.randomUUID()
        val persistence =
            ElasticsearchSearchPersistence(client, OcrProcessingProperties(indexName = "custom-documents"))
        val response = SearchResponse.of<Void> { search ->
            search.took(1).timedOut(false)
                .shards { it.total(1).successful(1).failed(0) }
                .hits { hits ->
                    hits.total { it.value(1).relation(TotalHitsRelation.Eq) }
                        .hits {
                            it.index("custom-documents").id(documentId.toString()).score(3.5)
                                .highlight("content", listOf("<em>invoice</em>"))
                        }
                }
        }
        `when`(client.search(any(SearchRequest::class.java), eq(Void::class.java))).thenReturn(response)

        // when
        val result = persistence.search(setOf(documentId), SearchQuery("invoic", true), PageRequest.of(0, 10))

        // then
        val captor = ArgumentCaptor.forClass(SearchRequest::class.java)
        verify(client).search(captor.capture(), eq(Void::class.java))
        val request = captor.value
        val bool = requireNotNull(request.query()).bool()
        assertAll(
            { assertThat(request.index()).containsExactly("custom-documents") },
            { assertThat(bool.filter().single().ids().values()).containsExactly(documentId.toString()) },
            { assertThat(bool.must().single().match().field()).isEqualTo("content") },
            { assertThat(bool.must().single().match().fuzziness()).isEqualTo("AUTO") },
            { assertThat(requireNotNull(request.trackTotalHits()).enabled()).isTrue() },
            { assertThat(result.totalElements).isEqualTo(1) },
            { assertThat(result.content.single().score).isEqualTo(3.5f) },
            { assertThat(result.content.single().highlights).containsExactly("<em>invoice</em>") },
        )
    }

    @Test
    fun search_without_visible_ids_never_queries_cluster_ok() {
        // given
        val persistence = ElasticsearchSearchPersistence(client, OcrProcessingProperties())

        // when
        val result = persistence.search(emptySet(), SearchQuery("invoice", false), PageRequest.of(0, 10))

        // then
        assertThat(result.totalElements).isZero()
        verifyNoInteractions(client)
    }
}
