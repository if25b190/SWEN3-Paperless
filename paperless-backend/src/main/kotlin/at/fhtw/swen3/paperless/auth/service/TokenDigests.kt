package at.fhtw.swen3.paperless.auth.service

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.HexFormat

object TokenDigests {
    fun sha256(token: String): String = HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(token.toByteArray(StandardCharsets.UTF_8))
    )
}
