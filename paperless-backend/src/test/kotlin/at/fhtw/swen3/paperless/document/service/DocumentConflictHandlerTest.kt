package at.fhtw.swen3.paperless.document.service

import at.fhtw.swen3.paperless.exception.DocumentConflictException
import at.fhtw.swen3.paperless.exception.GlobalExceptionHandler
import org.junit.jupiter.api.Test
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

class DocumentConflictHandlerTest {
    @Test
    fun document_conflicts_return_409_ko() {
        assertConflict(DocumentConflictException())
    }

    @Test
    fun translated_commit_conflicts_return_409_ko() {
        assertConflict(OptimisticLockingFailureException("Concurrent update"))
    }

    private fun assertConflict(failure: RuntimeException) {
        val mvc = MockMvcBuilders.standaloneSetup(ConflictController(failure))
            .setControllerAdvice(GlobalExceptionHandler())
            .build()

        mvc.perform(get("/document"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.detail").value("The request conflicts with an existing resource."))
    }

    @RestController
    private class ConflictController(private val failure: RuntimeException) {
        @GetMapping("/document")
        fun getDocument(): Nothing = throw failure
    }
}
