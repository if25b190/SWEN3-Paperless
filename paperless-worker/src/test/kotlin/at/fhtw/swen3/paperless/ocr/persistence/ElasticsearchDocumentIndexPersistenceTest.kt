package at.fhtw.swen3.paperless.ocr.persistence

import at.fhtw.swen3.paperless.ocr.config.OcrWorkerProperties
import at.fhtw.swen3.paperless.ocr.model.IndexedDocument
import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch.core.IndexRequest
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient
import co.elastic.clients.elasticsearch.indices.ExistsRequest
import co.elastic.clients.transport.endpoints.BooleanResponse
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import at.fhtw.swen3.paperless.testsupport.any
import java.io.IOException
import java.nio.file.Paths
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class ElasticsearchDocumentIndexPersistenceTest {
    @Mock
    lateinit var client: ElasticsearchClient

    @Mock
    lateinit var indices: ElasticsearchIndicesClient

    private val documentId = UUID.randomUUID()
    private lateinit var persistence: ElasticsearchDocumentIndexPersistence

    @BeforeEach
    fun setUp() {
        persistence = ElasticsearchDocumentIndexPersistence(
            client,
            OcrWorkerProperties(enabled = true, storageDir = Paths.get("/tmp"))
        )
    }

    @Test
    fun index_stores_document_by_id_ok() {
        // when
        persistence.index(documentId, "INVOICE #9821")

        // then
        @Suppress("UNCHECKED_CAST")
        val captor = ArgumentCaptor.forClass(IndexRequest::class.java) as ArgumentCaptor<IndexRequest<IndexedDocument>>
        verify(client).index(captor.capture())
        val request = captor.value
        val stored = request.document()
        assertAll(
            { assertThat(request.index()).isEqualTo("paperless-documents") },
            { assertThat(request.id()).isEqualTo(documentId.toString()) },
            { assertThat(stored.id).isEqualTo(documentId.toString()) },
            { assertThat(stored.content).isEqualTo("INVOICE #9821") }
        )
    }

    @Test
    fun ensure_index_creates_index_when_missing_ok() {
        // given
        `when`(client.indices()).thenReturn(indices)
        `when`(indices.exists(any(ExistsRequest::class.java))).thenReturn(BooleanResponse(false))

        // when
        persistence.ensureIndex()

        // then
        val captor: ArgumentCaptor<CreateIndexRequest> = ArgumentCaptor.forClass(CreateIndexRequest::class.java)
        verify(indices).create(captor.capture())
        assertThat(captor.value.index()).isEqualTo("paperless-documents")
    }

    @Test
    fun ensure_index_keeps_existing_index_ok() {
        // given
        `when`(client.indices()).thenReturn(indices)
        `when`(indices.exists(any(ExistsRequest::class.java))).thenReturn(BooleanResponse(true))

        // when
        persistence.ensureIndex()

        // then
        verify(indices, never()).create(any(CreateIndexRequest::class.java))
    }

    @Test
    fun ensure_index_swallows_cluster_failure_ko() {
        // given
        `when`(client.indices()).thenReturn(indices)
        `when`(indices.exists(any(ExistsRequest::class.java))).thenThrow(IOException("cluster unavailable"))

        // when / then
        assertThatCode { persistence.ensureIndex() }.doesNotThrowAnyException()
    }
}
