package at.fhtw.swen3.paperless.document.service

import at.fhtw.swen3.paperless.correspondent.entity.CorrespondentEntity
import at.fhtw.swen3.paperless.correspondent.repository.CorrespondentRepository
import at.fhtw.swen3.paperless.document.entity.DocumentEntity
import at.fhtw.swen3.paperless.document.repository.DocumentRepository
import at.fhtw.swen3.paperless.documenttype.entity.DocumentTypeEntity
import at.fhtw.swen3.paperless.documenttype.repository.DocumentTypeRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@DataJpaTest
@Import(DocumentServiceImpl::class)
class DocumentServiceReadTransactionTest {

    @Autowired
    lateinit var service: DocumentService

    @Autowired
    lateinit var documentRepository: DocumentRepository

    @Autowired
    lateinit var correspondentRepository: CorrespondentRepository

    @Autowired
    lateinit var documentTypeRepository: DocumentTypeRepository

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun search_and_get_map_lazy_relationships_outside_test_transaction_ok() {
        // given
        val correspondent = correspondentRepository.save(CorrespondentEntity(name = "Transaction test correspondent"))
        val documentType = documentTypeRepository.save(DocumentTypeEntity(name = "Transaction test type"))
        val entity = documentRepository.save(
            DocumentEntity(
                title = "Transaction test document",
                originalFilename = "transaction-test.pdf",
                contentType = "application/pdf",
                fileSize = 1,
                correspondent = correspondent,
                documentType = documentType
            )
        )

        try {
            // when / then
            assertAll(
                {
                    val listed = service.searchDocuments(0, 10, "title,asc", null, null).content.single()
                    assertThat(listed.correspondent?.name).isEqualTo(correspondent.name)
                    assertThat(listed.documentType?.name).isEqualTo(documentType.name)
                },
                {
                    val detail = service.getDocument(entity.id)
                    assertThat(detail.correspondent?.name).isEqualTo(correspondent.name)
                    assertThat(detail.documentType?.name).isEqualTo(documentType.name)
                }
            )
        } finally {
            documentRepository.deleteById(entity.id)
            correspondentRepository.deleteById(correspondent.id)
            documentTypeRepository.deleteById(documentType.id)
        }
    }
}
