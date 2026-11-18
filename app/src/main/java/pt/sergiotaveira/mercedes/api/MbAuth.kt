package pt.sergiotaveira.mercedes.api

import java.net.URLEncoder
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed class AuthError(msg: String) : Exception(msg) {
    class WrongCredentials : AuthError("wrong credentials")
    class Blocked : AuthError("blocked by Mercedes (429)")
    class NotLoggedIn : AuthError("not logged in")
    class Network(msg: String) : AuthError(msg)
    class Unexpected(msg: String) : AuthError(msg)
}

/** Mercedes me login (OAuth2 + PKCE), ported from mbapi2020 oauth.py (MIT). */
class MbAuth(
    private val http: HttpTransport,
    private val store: TokenStore,
    private val urls: MbUrls = MbUrls.EMEA,
    private val clock: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    private val lock = Mutex()
    private val sessionId = UUID.randomUUID().toString()

    suspend fun login(email: String, password: String): Tokens = lock.withLock { loginLocked(email.trim(), password) }

    /** Returns a usable access token, refreshing (or logging in again with stored credentials) when needed. */
    suspend fun validAccessToken(): String = lock.withLock {
        val current = store.load() ?: throw AuthError.NotLoggedIn()
        if (current.expiresAtEpochSec - clock() >= 60) return@withLock current.accessToken
        val refreshed = try { refresh(current.refreshToken) } catch (e: AuthError.Blocked) { throw e } catch (e: AuthError) { null }
        if (refreshed != null) return@withLock refreshed.accessToken
        val (email, password) = store.credentials() ?: run { store.clear(); throw AuthError.NotLoggedIn() }
        loginLocked(email, password).accessToken
    }

    private suspend fun loginLocked(email: String, password: String): Tokens {
        http.newSession()
        val verifier = Pkce.verifier()
        val base = urls.login

        val authUrl = "$base/as/authorization.oauth2?" + form(
            "client_id" to MbConstants.LOGIN_APP_ID, "code_challenge" to Pkce.challenge(verifier),
            "code_challenge_method" to "S256", "redirect_uri" to MbConstants.REDIRECT_URI,
            "response_type" to "code", "scope" to MbConstants.SCOPE,
        )
        val auth = send(HttpRequest("GET", authUrl, mapOf(
            "user-agent" to MbConstants.SAFARI_USER_AGENT,
            "accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "accept-language" to "de-DE,de;q=0.9",
        )))
        if (auth.status >= 400) throw AuthError.Unexpected("authorization ${auth.status}")
        val resume = queryParam(auth.finalUrl, "resume") ?: throw AuthError.Unexpected("no resume parameter")

        send(HttpRequest("POST", "$base/ciam/auth/ua", safari(accept = "*/*", referer = false),
            Json.encode(mapOf("browserName" to "Mobile Safari", "browserVersion" to "15.6.6", "osName" to "iOS"))))

        val user = send(HttpRequest("POST", "$base/ciam/auth/login/user", safari(), Json.encode(mapOf("username" to email))))
        if (user.status in 400..499) throw AuthError.WrongCredentials()
        if (user.status >= 500) throw AuthError.Network("username ${user.status}")

        val rid = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(24).also { SecureRandom().nextBytes(it) })
        val passBody = mapOf("username" to email, "password" to password, "rememberMe" to false, "rid" to rid)
        val pass = send(HttpRequest("POST", "$base/ciam/auth/login/pass", safari(), Json.encode(passBody)))
        if (pass.status in 400..499) throw AuthError.WrongCredentials()
        if (pass.status >= 500) throw AuthError.Network("password ${pass.status}")
        var pre = Json.obj(pass.body)

        if (pre["passkeyDemoEnabled"] == true) {
            val r = send(HttpRequest("POST", "$base/ciam/auth/disablePasskeyDemo", safari(), Json.encode(passBody + ("disablePasskeyDemo" to true))))
            if (r.status >= 400) throw AuthError.Unexpected("passkey ${r.status}")
            pre = Json.obj(r.body)
        }
        when (pre["result"]) {
            "RESUME2OIDCP" -> Unit
            "GOTO_LOGIN_LEGAL_TEXTS" -> {
                val r = send(HttpRequest("POST", "$base/ciam/auth/toas/saveLoginConsent", safari(), Json.encode(mapOf(
                    "texts" to emptyMap<String, Any>(), "homeCountry" to (pre["homeCountry"] ?: ""), "consentCountry" to (pre["consentCountry"] ?: ""),
                ))))
                if (r.status >= 400) throw AuthError.Unexpected("legal consent ${r.status}")
                pre = Json.obj(r.body)
                if (pre["result"] != "RESUME2OIDCP") throw AuthError.Unexpected("legal consent not accepted")
            }
            "GOTO_LOGIN_OTP" -> throw AuthError.Unexpected("two-factor authentication is not supported")
            else -> throw AuthError.Unexpected("login result ${pre["result"]}")
        }
        val preToken = pre["token"] as? String ?: throw AuthError.Unexpected("no pre-login token")

        val resumeResp = send(HttpRequest("POST", base + resume,
            safari() + mapOf("accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                "content-type" to "application/x-www-form-urlencoded"),
            form("token" to preToken), followRedirects = false))
        val location = resumeResp.headers["location"].orEmpty()
        if (resumeResp.status !in 301..302 || !location.startsWith("rismycar://")) throw AuthError.Unexpected("resume ${resumeResp.status}")
        val code = queryParam(location, "code") ?: throw AuthError.Unexpected("no authorization code")

        val tokenResp = send(HttpRequest("POST", "$base/as/token.oauth2",
            appHeaders() + ("Content-Type" to "application/x-www-form-urlencoded"),
            form("client_id" to MbConstants.LOGIN_APP_ID, "code" to code, "code_verifier" to verifier,
                "grant_type" to "authorization_code", "redirect_uri" to MbConstants.REDIRECT_URI)))
        if (tokenResp.status >= 400) throw AuthError.Unexpected("token exchange ${tokenResp.status}")
        val tokens = toTokens(Json.obj(tokenResp.body), fallbackRefresh = null)
        store.save(tokens)
        store.saveCredentials(email, password)
        return tokens
    }

    private suspend fun refresh(refreshToken: String): Tokens {
        send(HttpRequest("GET", "${urls.rest}/v1/config", appHeaders()))
        val resp = send(HttpRequest("POST", "${urls.login}/as/token.oauth2",
            appHeaders() + mapOf("Content-Type" to "application/x-www-form-urlencoded",
                "X-Device-Id" to store.deviceId(), "X-Request-Id" to UUID.randomUUID().toString()),
            "grant_type=refresh_token&refresh_token=$refreshToken"))
        if (resp.status >= 400) throw AuthError.Unexpected("refresh ${resp.status}")
        return toTokens(Json.obj(resp.body), fallbackRefresh = refreshToken).also(store::save)
    }

    private fun toTokens(j: Map<String, Any?>, fallbackRefresh: String?): Tokens {
        val access = j["access_token"] as? String ?: throw AuthError.Unexpected("no access token")
        val refresh = j["refresh_token"] as? String ?: fallbackRefresh ?: throw AuthError.Unexpected("no refresh token")
        val expiresIn = (j["expires_in"] as? Number)?.toLong() ?: 3600L
        return Tokens(access, refresh, clock() + expiresIn)
    }

    private suspend fun send(req: HttpRequest): HttpResponse {
        val resp = try { http.execute(req) } catch (e: java.io.IOException) { throw AuthError.Network(e.message ?: "network") }
        if (resp.status == 429) throw AuthError.Blocked()
        return resp
    }

    private fun safari(accept: String = "application/json, text/plain, */*", referer: Boolean = true) = buildMap {
        put("accept", accept); put("content-type", "application/json"); put("origin", urls.login)
        put("accept-language", "de-DE,de;q=0.9"); put("user-agent", MbConstants.SAFARI_USER_AGENT)
        if (referer) put("referer", "${urls.login}/ciam/auth/login")
    }

    /** Headers of the app's own API calls (oauth.py _get_header + apply_oauth_headers). */
    fun appHeaders(): Map<String, String> = mapOf(
        "Ris-Os-Name" to MbConstants.RIS_OS_NAME, "Ris-Os-Version" to MbConstants.RIS_OS_VERSION,
        "Ris-Sdk-Version" to MbConstants.RIS_SDK_VERSION, "X-Locale" to MbConstants.LOCALE,
        "X-Trackingid" to UUID.randomUUID().toString(), "X-Sessionid" to sessionId,
        "User-Agent" to MbConstants.APP_USER_AGENT, "Content-Type" to "application/json", "Accept-Language" to "en-GB",
        "X-Applicationname" to MbConstants.APPLICATION_NAME, "Ris-Application-Version" to MbConstants.RIS_APPLICATION_VERSION,
    )

    private fun form(vararg pairs: Pair<String, String>) =
        pairs.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }

    private fun queryParam(url: String, name: String): String? =
        url.substringAfter('?', "").split('&').firstOrNull { it.startsWith("$name=") }
            ?.substringAfter('=')?.let { java.net.URLDecoder.decode(it, "UTF-8") }
}
