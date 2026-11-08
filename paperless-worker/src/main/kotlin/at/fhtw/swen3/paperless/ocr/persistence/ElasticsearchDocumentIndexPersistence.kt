package at.fhtw.swen3.paperless.ocr.persistence

import at.fhtw.swen3.paperless.ocr.config.OcrWorkerProperties
import at.fhtw.swen3.paperless.ocr.model.IndexedDocument
import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch.core.IndexRequest
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest
import co.elastic.clients.elasticsearch.indices.ExistsRequest
import co.elastic.clients.elasticsearch._types.mapping.DateProperty
import co.elastic.clients.elasticsearch._types.mapping.KeywordProperty
import co.elastic.clients.elasticsearch._types.mapping.Property
import co.elastic.clients.elasticsearch._types.mapping.TextProperty
import jakarta.annotation.PostConstruct
import java.time.Instant
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository

@Repository
class ElasticsearchDocumentIndexPersistence(
    private val client: ElasticsearchClient,
    private val properties: OcrWorkerProperties
) : DocumentIndexPersistence {

    @PostConstruct
    fun ensureIndex() {
        try {
            val exists = client.indices().exists(ExistsRequest.of { it.index(properties.indexName) }).value()
            if (!exists) {
                client.indices().create(
                    CreateIndexRequest.of { request ->
                        request.index(properties.indexName)
                            .mappings { mappings ->
                                mappings
                                    .properties(FIELD_ID) { property -> property.keyword(keywordProperty()) }
                                    .properties(FIELD_CONTENT) { property -> property.text(textProperty()) }
                                    .properties(FIELD_INDEXED_AT) { property -> property.date(dateProperty()) }
                            }
                    }
                )
                logger.info("[INDEX] - ACTION: ensureIndex: indexName: {}, created: {}", properties.indexName, true)
            }
        } catch (exception: Exception) {
            logger.warn(
                "[INDEX] - ACTION: ensureIndex: indexName: {}, exceptionType: {}",
                properties.indexName,
                exception.javaClass.name
            )
        }
    }

    override fun index(documentId: UUID, ocrContent: String) {
        client.index(
            IndexRequest.of { request ->
                request
                    .index(properties.indexName)
                    .id(documentId.toString())
                    .document(IndexedDocument(documentId.toString(), ocrContent, Instant.now()))
            }
        )
        logger.info(
            "[INDEX] - ACTION: index: documentId: {}, indexName: {}, contentLength: {}",
            documentId,
            properties.indexName,
            ocrContent.length
        )
    }

    private fun keywordProperty(): KeywordProperty = KeywordProperty.of { builder -> builder }

    private fun textProperty(): TextProperty = TextProperty.of { builder -> builder }

    private fun dateProperty(): DateProperty = DateProperty.of { builder -> builder }

    private companion object {
        const val FIELD_ID = "id"
        const val FIELD_CONTENT = "content"
        const val FIELD_INDEXED_AT = "indexedAt"
        val logger = LoggerFactory.getLogger(ElasticsearchDocumentIndexPersistence::class.java)
    }
}
