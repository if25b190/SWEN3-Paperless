package at.fhtw.swen3.paperless.search.service

import at.fhtw.swen3.paperless.document.entity.DocumentEntity
import at.fhtw.swen3.paperless.document.model.ProcessingStatus
import at.fhtw.swen3.paperless.document.repository.DocumentRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.time.Instant

@ExtendWith(MockitoExtension::class)
class SearchServiceImplTest {

    @Mock
    lateinit var repository: DocumentRepository

    @InjectMocks
    lateinit var service: SearchServiceImpl

    @Test
    fun search_multi_term_and_pagination_ok() {
        // given
        `when`(repository.findAll()).thenReturn(
            listOf(
                document(1, "Invoice", "Invoice from Acme"),
                document(2, "Invoice", "Invoice from another sender")
            )
        )

        // when
        val firstPage = service.search("invoice acme", false, 0, 1)
        val secondPage = service.search("invoice acme", false, 1, 1)

        // then
        assertAll(
            { assertThat(firstPage.totalElements).isEqualTo(2) },
            { assertThat(firstPage.content.single().document.id).isEqualTo(1) },
            { assertThat(firstPage.content.single().score).isEqualTo(2.0f) },
            {
                assertThat(firstPage.content.single().highlights).containsExactly(
                    "<em>invoice</em>",
                    "<em>invoice</em> from <em>acme</em>",
                    "An <em>invoice</em>"
                )
            },
            { assertThat(secondPage.content.single().document.id).isEqualTo(2) }
        )
    }

    @Test
    fun search_fuzzy_terms_ok() {
        // given
        `when`(repository.findAll()).thenReturn(listOf(document(1, "Invoice", "Invoice from Acme")))

        // when
        val result = service.search("invoic acm", true, 0, 10)

        // then
        assertAll(
            { assertThat(result.totalElements).isEqualTo(1) },
            { assertThat(result.content.single().score).isEqualTo(2.0f) }
        )
    }

    @Test
    fun search_without_matches_returns_empty_page_ok() {
        // given
        `when`(repository.findAll()).thenReturn(listOf(document(1, "Invoice", "Invoice from Acme")))

        // when
        val result = service.search("passport", false, 0, 10)

        // then
        assertThat(result).isEmpty()
    }

    private fun document(id: Long, title: String, text: String) = DocumentEntity(
        id = id,
        title = title,
        originalFilename = "document.pdf",
        contentType = "application/pdf",
        fileSize = 10,
        status = ProcessingStatus.COMPLETED,
        ocrContent = text,
        summary = "An invoice",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )
}
