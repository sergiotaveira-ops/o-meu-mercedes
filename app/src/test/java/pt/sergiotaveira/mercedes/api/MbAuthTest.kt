package pt.sergiotaveira.mercedes.api

import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MbAuthTest {
    private val now = 1_000_000L

    @Test
    fun loginStoresTokens() = runBlocking {
        val store = InMemoryTokenStore()
        val t = FakeTransport.happyLogin()
        val tokens = MbAuth(t, store, clock = { now }).login("a@b.pt", "pw")
        assertEquals(Tokens("AT1", "RT1", now + 3600), tokens)
        assertEquals(tokens, store.load())
        assertEquals("a@b.pt" to "pw", store.credentials())
        val exchange = t.requests.last { it.url.endsWith("/as/token.oauth2") }
        assertTrue(exchange.body!!.contains("grant_type=authorization_code"))
        assertTrue(exchange.body!!.contains("code=CODE1"))
        assertTrue(exchange.body!!.contains("code_verifier="))
    }

    @Test
    fun wrongPasswordThrowsWrongCredentials() {
        val t = FakeTransport.happyLogin().apply { on("/ciam/auth/login/pass") { HttpResponse(401, it.url, emptyMap(), "{}") } }
        assertFailsWith<AuthError.WrongCredentials> { runBlocking { MbAuth(t, InMemoryTokenStore(), clock = { now }).login("a@b.pt", "x") } }
    }

    @Test
    fun legalConsentIsAcceptedWhenAsked() = runBlocking {
        val t = FakeTransport.happyLogin().apply {
            on("/ciam/auth/login/pass") { FakeTransport.ok("""{"result":"GOTO_LOGIN_LEGAL_TEXTS","homeCountry":"PT","consentCountry":"PT"}""") }
            on("/ciam/auth/toas/saveLoginConsent") { FakeTransport.ok("""{"result":"RESUME2OIDCP","token":"PRE"}""") }
        }
        MbAuth(t, InMemoryTokenStore(), clock = { now }).login("a@b.pt", "pw")
        assertEquals(1, t.count("/saveLoginConsent"))
    }

    @Test
    fun expiredTokenIsRefreshed() = runBlocking {
        val store = InMemoryTokenStore().apply { save(Tokens("OLD", "RT1", now + 10)) }
        val t = FakeTransport.happyLogin(access = "NEW", refresh = "RT2")
        val token = MbAuth(t, store, clock = { now }).validAccessToken()
        assertEquals("NEW", token)
        assertEquals(Tokens("NEW", "RT2", now + 3600), store.load())
        assertTrue(t.requests.last().body!!.contains("grant_type=refresh_token&refresh_token=RT1"))
    }

    @Test
    fun freshTokenIsNotRefreshed() = runBlocking {
        val store = InMemoryTokenStore().apply { save(Tokens("CUR", "RT1", now + 600)) }
        val t = FakeTransport.happyLogin()
        assertEquals("CUR", MbAuth(t, store, clock = { now }).validAccessToken())
        assertEquals(0, t.requests.size)
    }

    @Test
    fun failedRefreshFallsBackToStoredCredentials() = runBlocking {
        val store = InMemoryTokenStore().apply { save(Tokens("OLD", "BAD", now - 1)); saveCredentials("a@b.pt", "pw") }
        var tokenCalls = 0
        val t = FakeTransport.happyLogin(access = "RELOGIN").apply {
            on("/as/token.oauth2") {
                tokenCalls++
                if (it.body!!.contains("refresh_token=BAD")) HttpResponse(400, it.url, emptyMap(), "{}")
                else FakeTransport.ok("""{"access_token":"RELOGIN","refresh_token":"R","expires_in":3600}""")
            }
        }
        assertEquals("RELOGIN", MbAuth(t, store, clock = { now }).validAccessToken())
        assertEquals(2, tokenCalls)
    }

    @Test
    fun failedRefreshWithoutCredentialsThrowsNotLoggedIn() {
        val store = InMemoryTokenStore().apply { save(Tokens("OLD", "BAD", now - 1)) }
        val t = FakeTransport.happyLogin().apply { on("/as/token.oauth2") { HttpResponse(400, it.url, emptyMap(), "{}") } }
        assertFailsWith<AuthError.NotLoggedIn> { runBlocking { MbAuth(t, store, clock = { now }).validAccessToken() } }
    }

    @Test
    fun http429ThrowsBlocked() {
        val t = FakeTransport.happyLogin().apply { on("/ciam/auth/login/user") { HttpResponse(429, it.url, emptyMap(), "") } }
        assertFailsWith<AuthError.Blocked> { runBlocking { MbAuth(t, InMemoryTokenStore(), clock = { now }).login("a@b.pt", "pw") } }
    }

    @Test
    fun twoFactorIsReportedAsUnexpected() {
        val t = FakeTransport.happyLogin().apply { on("/ciam/auth/login/pass") { FakeTransport.ok("""{"result":"GOTO_LOGIN_OTP"}""") } }
        assertFailsWith<AuthError.Unexpected> { runBlocking { MbAuth(t, InMemoryTokenStore(), clock = { now }).login("a@b.pt", "pw") } }
    }
}
