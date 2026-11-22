package at.fhtw.swen3.paperless.ocr

import at.fhtw.swen3.paperless.ocr.config.OcrProcessingProperties
import at.fhtw.swen3.paperless.ocr.model.IndexedDocument
import at.fhtw.swen3.paperless.ocr.persistence.ElasticsearchDocumentIndexPersistence
import at.fhtw.swen3.paperless.search.model.SearchQuery
import at.fhtw.swen3.paperless.search.persistence.ElasticsearchSearchPersistence
import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch.core.GetRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

// Uses an isolated index on a reachable Elasticsearch instance.
@SpringBootTest(properties = ["spring.rabbitmq.listener.simple.auto-startup=false"])
@EnabledIf("elasticsearchAvailable")
class ElasticsearchIndexingIntegrationTest(
    @Autowired private val client: ElasticsearchClient,
    @Autowired private val properties: OcrProcessingProperties,
) {
    private var indexName: String = ""
    private lateinit var persistence: ElasticsearchDocumentIndexPersistence
    private lateinit var search: ElasticsearchSearchPersistence

    @BeforeEach
    fun setUp() {
        indexName = "paperless-backend-test-${UUID.randomUUID()}"
        val testProperties = properties.copy(indexName = indexName)
        persistence = ElasticsearchDocumentIndexPersistence(client, testProperties)
        search = ElasticsearchSearchPersistence(client, testProperties)
        persistence.ensureIndex()
    }

    @AfterEach
    fun tearDown() {
        runCatching { client.indices().delete { it.index(indexName) } }
    }

    @Test
    fun index_stores_and_updates_document_by_id_ok() {
        // given
        val documentId = UUID.randomUUID()

        // when
        persistence.index(documentId, "the quick brown fox")
        persistence.index(documentId, "the quick red fox")
        client.indices().refresh { it.index(indexName) }

        // then
        val stored = client.get(
            GetRequest.of {
                it.index(indexName).id(documentId.toString())
            },
            IndexedDocument::class.java,
        ).source()
        assertAll(
            { assertThat(stored).isNotNull },
            { assertThat(stored?.id).isEqualTo(documentId.toString()) },
            { assertThat(stored?.content).isEqualTo("the quick red fox") },
        )
    }

    @Test
    fun index_keeps_documents_separate_ok() {
        // given
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()

        // when
        persistence.index(first, "first document")
        persistence.index(second, "second document")
        client.indices().refresh { it.index(indexName) }

        // then
        val firstStored = client.get(
            GetRequest.of {
                it.index(indexName).id(first.toString())
            },
            IndexedDocument::class.java,
        ).source()
        val secondStored = client.get(
            GetRequest.of {
                it.index(indexName).id(second.toString())
            },
            IndexedDocument::class.java,
        ).source()
        assertAll(
            { assertThat(firstStored?.content).isEqualTo("first document") },
            { assertThat(secondStored?.content).isEqualTo("second document") },
        )
    }

    @Test
    fun search_filters_visibility_before_ranking_pagination_and_totals_ok() {
        // given
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        val hidden = UUID.randomUUID()
        persistence.index(first, "invoice from acme")
        persistence.index(second, "invoice from another sender")
        persistence.index(hidden, "invoice acme invoice acme classified")
        client.indices().refresh { it.index(indexName) }

        // when
        val firstPage = search.search(setOf(first, second), SearchQuery("invoice acme", false), PageRequest.of(0, 1))
        val secondPage = search.search(setOf(first, second), SearchQuery("invoice acme", false), PageRequest.of(1, 1))
        val hiddenOnly = search.search(setOf(first, second), SearchQuery("classified", false), PageRequest.of(0, 10))

        // then
        assertAll(
            { assertThat(firstPage.totalElements).isEqualTo(2) },
            { assertThat(secondPage.totalElements).isEqualTo(2) },
            { assertThat(firstPage.content.single().documentId).isEqualTo(first) },
            { assertThat(secondPage.content.single().documentId).isEqualTo(second) },
            { assertThat(firstPage.content.single().score).isGreaterThan(0.0f) },
            {
                assertThat(firstPage.content.single().highlights).containsExactly("<em>invoice</em> from <em>acme</em>")
            },
            { assertThat(hiddenOnly.totalElements).isZero() },
        )
    }

    @Test
    fun search_fuzzy_matching_and_html_highlights_ok() {
        // given
        val documentId = UUID.randomUUID()
        persistence.index(documentId, "<script>alert(1)</script> invoice from acme")
        client.indices().refresh { it.index(indexName) }

        // when
        val exact = search.search(setOf(documentId), SearchQuery("invoic", false), PageRequest.of(0, 10))
        val fuzzy = search.search(setOf(documentId), SearchQuery("invoic", true), PageRequest.of(0, 10))

        // then
        assertAll(
            { assertThat(exact.totalElements).isZero() },
            { assertThat(fuzzy.content.single().documentId).isEqualTo(documentId) },
            { assertThat(fuzzy.content.single().highlights.single()).contains("<em>invoice</em>", "&lt;script&gt;") },
            { assertThat(fuzzy.content.single().highlights.single()).doesNotContain("<script>") },
        )
    }

    @Test
    fun search_maximum_page_returns_total_without_overflow_ok() {
        // given
        val documentId = UUID.randomUUID()
        persistence.index(documentId, "invoice")
        client.indices().refresh { it.index(indexName) }

        // when
        val result = search.search(setOf(documentId), SearchQuery("invoice", false), PageRequest.of(Int.MAX_VALUE, 100))

        // then
        assertAll(
            { assertThat(result.content).isEmpty() },
            { assertThat(result.totalElements).isEqualTo(1) },
        )
    }

    @Test
    fun search_missing_index_returns_empty_page_ok() {
        // given
        client.indices().delete { it.index(indexName) }

        // when
        val result = search.search(setOf(UUID.randomUUID()), SearchQuery("invoice", false), PageRequest.of(0, 10))

        // then
        assertThat(result.totalElements).isZero()
    }

    companion object {
        // @JvmStatic so @EnabledIf("elasticsearchAvailable") resolves it as a
        // static method of the test class.
        @JvmStatic
        fun elasticsearchAvailable(): Boolean {
            val host = System.getenv("ELASTICSEARCH_HOST") ?: "localhost"
            val port = System.getenv("ELASTICSEARCH_PORT")?.toIntOrNull() ?: 9200
            return try {
                val connection = URL("http://$host:$port/").openConnection() as HttpURLConnection
                connection.connectTimeout = 2000
                connection.readTimeout = 2000
                val status = connection.responseCode
                connection.disconnect()
                status == HttpURLConnection.HTTP_OK
            } catch (exception: Exception) {
                false
            }
        }
    }
}
