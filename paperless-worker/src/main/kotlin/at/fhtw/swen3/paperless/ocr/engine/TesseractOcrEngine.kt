package at.fhtw.swen3.paperless.ocr.engine

import at.fhtw.swen3.paperless.ocr.config.OcrWorkerProperties
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class TesseractOcrEngine(
    private val properties: OcrWorkerProperties
) : OcrEngine {
    override fun extractText(source: Path): String {
        if (!Files.isRegularFile(source)) {
            throw OcrEngineException("Source file not found: $source")
        }
        val workDir = Files.createTempDirectory(TEMP_DIR_PREFIX)
        try {
            val pages = rasterizePdf(workDir, source)
            logger.info(
                "[OCR] - ACTION: rasterizePdf: pages: {}, dpi: {}, timeout: {}",
                pages.size,
                properties.dpi,
                properties.processTimeout
            )
            return pages.map { ocrPage(workDir, it) }
                .filter(String::isNotBlank)
                .joinToString("\n\n")
        } finally {
            Files.walk(workDir).use { walker ->
                walker.sorted(Comparator.reverseOrder()).forEach { file ->
                    runCatching { Files.delete(file) }
                }
            }
        }
    }

    private fun rasterizePdf(workDir: Path, source: Path): List<Path> {
        runProcess(
            workDir = workDir,
            name = "gs",
            args = listOf(
                "-dNOPAUSE", "-dBATCH", "-dSAFER", "-dQUIET",
                "-sDEVICE=png16m",
                "-r${properties.dpi}",
                "-sOutputFile=${workDir.resolve(PAGE_TEMPLATE)}",
                source.toString()
            ),
            stdout = ProcessBuilder.Redirect.DISCARD
        )
        val pages = Files.list(workDir).use { stream ->
            stream.filter { isPageImage(it) }.toList()
        }.sortedBy { pageOrder(it) }
        if (pages.isEmpty()) {
            throw OcrEngineException("Ghostscript produced no pages for $source")
        }
        return pages
    }

    private fun ocrPage(workDir: Path, page: Path): String {
        val baseName = page.fileName.toString().removeSuffix(".png")
        val textFile = workDir.resolve("$baseName.txt")
        runProcess(
            workDir = workDir,
            name = "tesseract",
            args = listOf(page.toString(), workDir.resolve(baseName).toString(), "-l", properties.tesseractLanguages),
            stdout = ProcessBuilder.Redirect.DISCARD
        )
        if (!Files.isRegularFile(textFile)) {
            throw OcrEngineException("Tesseract produced no output for $page")
        }
        return Files.readString(textFile).trim()
    }

    private fun runProcess(workDir: Path, name: String, args: List<String>, stdout: ProcessBuilder.Redirect) {
        val stderr = workDir.resolve("$name.stderr.log")
        val process = ProcessBuilder(listOf(name) + args)
            .redirectOutput(stdout)
            .redirectError(ProcessBuilder.Redirect.to(stderr.toFile()))
            .start()
        if (!process.waitFor(properties.processTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            throw OcrEngineException(
                "$name timed out after ${properties.processTimeout.toSeconds()}s for args: ${args.joinToString(" ")}"
            )
        }
        val exit = process.exitValue()
        if (exit != 0) {
            val detail = readDetail(stderr)
            throw OcrEngineException("$name exited with code $exit: $detail")
        }
    }

    private fun readDetail(stderr: Path): String =
        if (Files.isRegularFile(stderr)) {
            Files.readString(stderr).take(MAX_DETAIL_LENGTH).trim()
        } else {
            "no output captured"
        }

    private fun isPageImage(file: Path): Boolean {
        val name = file.fileName.toString()
        return name.startsWith("page-") && name.endsWith(".png")
    }

    private fun pageOrder(file: Path): Int =
        file.fileName.toString().removePrefix("page-").removeSuffix(".png").toInt()

    private companion object {
        const val TEMP_DIR_PREFIX = "paperless-ocr-"
        const val PAGE_TEMPLATE = "page-%d.png"
        const val MAX_DETAIL_LENGTH = 2000
        val logger = LoggerFactory.getLogger(TesseractOcrEngine::class.java)
    }
}
