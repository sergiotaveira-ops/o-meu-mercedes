package pt.sergiotaveira.mercedes.api

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object Pkce {
    private val b64 = Base64.getUrlEncoder().withoutPadding()

    fun verifier(): String = b64.encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) })

    fun challenge(verifier: String): String =
        b64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
}
