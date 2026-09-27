package at.fhtw.swen3.paperless.search.service

import at.fhtw.swen3.paperless.document.model.Document
import at.fhtw.swen3.paperless.document.model.ProcessingStatus
import at.fhtw.swen3.paperless.document.service.DocumentService
import jakarta.validation.ConstraintViolationException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class SearchServiceImplTest {

    @Mock
    lateinit var documentService: DocumentService

    @InjectMocks
    lateinit var service: SearchServiceImpl

    @Test
    fun search_multi_term_and_pagination_ok() {
        // given
        `when`(documentService.visibleDocumentsForSearch()).thenReturn(
            listOf(
                document(uuid(1), "Invoice", "Invoice from Acme"),
                document(uuid(2), "Invoice", "Invoice from another sender")
            )
        )

        // when
        val firstPage = service.search("invoice acme", false, 0, 1)
        val secondPage = service.search("invoice acme", false, 1, 1)

        // then
        assertAll(
            { assertThat(firstPage.totalElements).isEqualTo(2) },
            { assertThat(firstPage.content.single().document.id).isEqualTo(uuid(1)) },
            { assertThat(firstPage.content.single().score).isEqualTo(2.0f) },
            {
                assertThat(firstPage.content.single().highlights).containsExactly(
                    "<em>invoice</em>",
                    "<em>invoice</em> from <em>acme</em>",
                    "An <em>invoice</em>"
                )
            },
            { assertThat(secondPage.content.single().document.id).isEqualTo(uuid(2)) }
        )
    }

    @Test
    fun search_fuzzy_terms_ok() {
        // given
        `when`(documentService.visibleDocumentsForSearch()).thenReturn(listOf(document(uuid(1), "Invoice", "Invoice from Acme")))

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
        `when`(documentService.visibleDocumentsForSearch()).thenReturn(listOf(document(uuid(1), "Invoice", "Invoice from Acme")))

        // when
        val result = service.search("passport", false, 0, 10)

        // then
        assertThat(result).isEmpty()
    }

    @Test
    fun search_does_not_expose_candidates_excluded_by_visibility_boundary() {
        // given
        val hiddenCandidate = document(uuid(2), "Secret report", "project-aurora")
        `when`(documentService.visibleDocumentsForSearch()).thenReturn(
            listOf(document(uuid(1), "Invoice", "Invoice from Acme"))
        )

        // when
        val result = service.search(hiddenCandidate.ocrContent!!, false, 0, 10)

        // then
        assertAll(
            { assertThat(result.content).isEmpty() },
            { assertThat(result.totalElements).isZero() },
            { assertThat(result.content.flatMap { it.highlights }).isEmpty() }
        )
        verify(documentService).visibleDocumentsForSearch()
    }

    @Test
    fun search_with_maximum_page_returns_empty_page_without_overflow() {
        // given
        `when`(documentService.visibleDocumentsForSearch()).thenReturn(listOf(document(uuid(1), "Invoice", "Invoice from Acme")))

        // when
        val result = service.search("invoice", false, Int.MAX_VALUE, 100)

        // then
        assertAll(
            { assertThat(result.content).isEmpty() },
            { assertThat(result.totalElements).isEqualTo(1) }
        )
    }

    @Test
    fun search_rejects_negative_page() {
        assertThrows<ConstraintViolationException> {
            service.search("invoice", false, -1, 10)
        }
        verifyNoInteractions(documentService)
    }

    @Test
    fun search_rejects_zero_size() {
        assertThrows<ConstraintViolationException> {
            service.search("invoice", false, 0, 0)
        }
        verifyNoInteractions(documentService)
    }

    @Test
    fun search_rejects_size_above_maximum() {
        assertThrows<ConstraintViolationException> {
            service.search("invoice", false, 0, 101)
        }
        verifyNoInteractions(documentService)
    }

    private fun uuid(value: Int) = UUID(0L, value.toLong())

    private fun document(id: UUID, title: String, text: String) = Document(
        id = id,
        title = title,
        originalFilename = "document.pdf",
        contentType = "application/pdf",
        fileSize = 10,
        status = ProcessingStatus.COMPLETED,
        ocrContent = text,
        summary = "An invoice",
        storageKey = null,
        documentType = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        ownerId = uuid(1)
    )
}
