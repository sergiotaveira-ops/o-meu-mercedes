package pt.sergiotaveira.mercedes.api

data class Tokens(val accessToken: String, val refreshToken: String, val expiresAtEpochSec: Long)

interface TokenStore {
    fun load(): Tokens?
    fun save(t: Tokens)
    fun clear()
    fun saveCredentials(email: String, password: String)
    fun credentials(): Pair<String, String>?
    /** Stable per-install id sent as the CIAM.DEVICE cookie / X-Device-Id. */
    fun deviceId(): String
}

class InMemoryTokenStore : TokenStore {
    private var tokens: Tokens? = null
    private var creds: Pair<String, String>? = null
    private val device = java.util.UUID.randomUUID().toString()
    override fun load() = tokens
    override fun save(t: Tokens) { tokens = t }
    override fun clear() { tokens = null; creds = null }
    override fun saveCredentials(email: String, password: String) { creds = email to password }
    override fun credentials() = creds
    override fun deviceId() = device
}
