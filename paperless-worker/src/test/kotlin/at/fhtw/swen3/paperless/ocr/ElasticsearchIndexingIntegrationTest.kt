package at.fhtw.swen3.paperless.ocr

import at.fhtw.swen3.paperless.ocr.config.OcrWorkerProperties
import at.fhtw.swen3.paperless.ocr.model.IndexedDocument
import at.fhtw.swen3.paperless.ocr.persistence.ElasticsearchDocumentIndexPersistence
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
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Paths
import java.util.UUID

// Runs against a live Elasticsearch reachable at ELASTICSEARCH_HOST/ELASTICSEARCH_PORT
// (e.g. `docker compose up elasticsearch`); skipped otherwise. Uses a throwaway index
// per test so the application's index is left untouched.
// paperless.worker.enabled=false keeps the Rabbit listener from starting: the test
// needs no broker connection, and a local broker with foreign credentials would
// otherwise abort context startup.
@SpringBootTest(properties = ["paperless.worker.enabled=false"])
@EnabledIf("elasticsearchAvailable")
class ElasticsearchIndexingIntegrationTest(
    @Autowired private val client: ElasticsearchClient,
    @Autowired private val properties: OcrWorkerProperties
) {
    private var indexName: String = ""
    private lateinit var persistence: ElasticsearchDocumentIndexPersistence

    @BeforeEach
    fun setUp() {
        indexName = "paperless-worker-test-${UUID.randomUUID()}"
        val testProperties = properties.copy(indexName = indexName, storageDir = Paths.get("/tmp"))
        persistence = ElasticsearchDocumentIndexPersistence(client, testProperties)
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
        val stored = client.get(GetRequest.of { it.index(indexName).id(documentId.toString()) }, IndexedDocument::class.java).source()
        assertAll(
            { assertThat(stored).isNotNull },
            { assertThat(stored?.id).isEqualTo(documentId.toString()) },
            { assertThat(stored?.content).isEqualTo("the quick red fox") }
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
        val firstStored = client.get(GetRequest.of { it.index(indexName).id(first.toString()) }, IndexedDocument::class.java).source()
        val secondStored = client.get(GetRequest.of { it.index(indexName).id(second.toString()) }, IndexedDocument::class.java).source()
        assertAll(
            { assertThat(firstStored?.content).isEqualTo("first document") },
            { assertThat(secondStored?.content).isEqualTo("second document") }
        )
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
