package at.fhtw.swen3.paperless

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PaperlessApplication

fun main(args: Array<String>) {
	runApplication<PaperlessApplication>(*args)
}
