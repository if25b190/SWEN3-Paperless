package at.fhtw.swen3.paperless.architecture

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class PersistenceBoundaryTest {
    @Test
    fun business_layers_do_not_import_jpa_implementation_details_ok() {
        val sources = sourcesOutsidePersistence()

        assertThat(sources).isNotEmpty()
        assertThat(sources).allSatisfy { path ->
            assertThat(Files.readString(path)).describedAs(path.toString())
                .doesNotContain("import jakarta.persistence.", ".persistence.entity.", ".persistence.repository.")
                .doesNotContain(".persistence.mapper.", "import org.springframework.data.jpa.")
        }
    }

    @Test
    fun domain_persistence_interfaces_do_not_leak_jpa_types_ok() {
        val interfaces = persistenceInterfaces()

        assertThat(interfaces).isNotEmpty()
        assertThat(interfaces).allSatisfy { path ->
            assertThat(Files.readString(path)).describedAs(path.toString())
                .doesNotContain("jakarta.persistence.", ".persistence.entity.", ".persistence.repository.")
                .doesNotContain("JpaRepository", "EntityManager", ".persistence.mapper.")
        }
    }

    private fun sourcesOutsidePersistence(): List<Path> = Files.walk(Path.of("src/main/kotlin")).use { paths ->
        paths.filter { it.toString().endsWith(".kt") && !it.toString().contains("/persistence/") }
            .filter { path ->
                listOf("/service/", "/controller/", "/model/", "/mapper/").any { path.toString().contains(it) }
            }.toList()
    }

    private fun persistenceInterfaces(): List<Path> = Files.walk(Path.of("src/main/kotlin")).use { paths ->
        paths.filter { it.fileName.toString().endsWith("Persistence.kt") }
            .filter { !it.fileName.toString().startsWith("Jpa") }.toList()
    }
}
