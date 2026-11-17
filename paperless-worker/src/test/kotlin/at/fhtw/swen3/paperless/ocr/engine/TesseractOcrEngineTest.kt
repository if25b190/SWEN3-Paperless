package at.fhtw.swen3.paperless.ocr.engine

import at.fhtw.swen3.paperless.ocr.config.OcrWorkerProperties
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class TesseractOcrEngineTest {
    private val properties = OcrWorkerProperties(enabled = true, storageDir = Paths.get("/tmp"))
    private val engine = TesseractOcrEngine(properties)

    @Test
    fun missing_source_file_is_rejected_ko() {
        // given
        val missing = Paths.get("/tmp/does-not-exist-paperless-test.pdf")

        // when / then
        assertThatThrownBy { engine.extractText(missing) }.isInstanceOf(OcrEngineException::class.java)
    }

    // Runs against the real Ghostscript and Tesseract binaries when they are
    // installed (GitHub runners have both preinstalled); skipped otherwise.
    @EnabledIf("pipelineAvailable")
    @Test
    fun extracts_text_from_each_page_of_a_scanned_pdf_ok() {
        // given
        val fixture = fixture("sample-invoice.pdf")

        // when
        val text = engine.extractText(fixture)

        // then
        assertThat(text).contains("INVOICE #9821")
        assertThat(text).contains("142.50")
        assertThat(text).contains("73459")
        // The two pages are joined in order, page one before page two.
        assertThat(text.indexOf("9821")).isLessThan(text.indexOf("73459"))
    }

    @EnabledIf("pipelineAvailable")
    @Test
    fun corrupt_source_is_rejected_with_tool_detail_ko() {
        // given
        val workDir = Files.createTempDirectory("paperless-engine-test")
        val corrupt = workDir.resolve("corrupt.pdf")
        Files.writeString(corrupt, "this is not a pdf")
        val properties = OcrWorkerProperties(enabled = true, storageDir = workDir)
        val engine = TesseractOcrEngine(properties)

        // when / then
        assertThatThrownBy { engine.extractText(corrupt) }.isInstanceOf(OcrEngineException::class.java)
    }

    companion object {
        // @JvmStatic so @EnabledIf("...#pipelineAvailable") resolves it as a
        // static method of the test class.
        @JvmStatic
        fun pipelineAvailable(): Boolean {
            val tesseract = findExecutable("tesseract")
            val ghostscript = findExecutable("gs")
            return tesseract != null && ghostscript != null && englishDataAvailable()
        }

        private fun findExecutable(name: String): Path? {
            val pathVar = System.getenv("PATH") ?: return null
            return pathVar.split(File.pathSeparator)
                .map { Paths.get(it, name) }
                .firstOrNull { Files.isExecutable(it) }
        }

        private fun englishDataAvailable(): Boolean {
            val candidates = buildList {
                System.getenv("TESSDATA_PREFIX")?.let { add(Paths.get(it, "eng.traineddata")) }
                add(Paths.get("/usr/share/tessdata/eng.traineddata"))
                System.getProperty("user.home")?.let { add(Paths.get(it, ".tessdata/eng.traineddata")) }
            }
            return candidates.any { Files.isRegularFile(it) }
        }

        fun fixture(name: String): Path {
            val url = requireNotNull(TesseractOcrEngineTest::class.java.classLoader.getResource("fixtures/$name")) {
                "Missing test fixture: fixtures/$name"
            }
            return Path.of(url.toURI())
        }
    }
}
