package pt.sergiotaveira.mercedes.api

/** Test double: answers by URL path; records every request. */
class FakeTransport(private val routes: MutableMap<String, (HttpRequest) -> HttpResponse> = mutableMapOf()) : HttpTransport {
    val requests = mutableListOf<HttpRequest>()
    fun on(path: String, handler: (HttpRequest) -> HttpResponse) { routes[path] = handler }
    override suspend fun execute(request: HttpRequest): HttpResponse {
        requests += request
        val path = request.url.substringAfter("://").substringAfter("/", "").substringBefore("?").let { "/$it" }
        val h = routes[path] ?: return HttpResponse(404, request.url, emptyMap(), "")
        return h(request)
    }
    fun count(path: String) = requests.count { it.url.contains(path) }

    companion object {
        fun ok(body: String = "{}", url: String = "") = HttpResponse(200, url, emptyMap(), body)

        /** A transport that completes the whole happy-path login and returns the given tokens. */
        fun happyLogin(access: String = "AT1", refresh: String = "RT1", expiresIn: Int = 3600) = FakeTransport().apply {
            on("/as/authorization.oauth2") { HttpResponse(200, "https://id.mercedes-benz.com/ciam/auth/login?resume=/as/RES/resume/as/authorization.ping", emptyMap(), "<html/>") }
            on("/ciam/auth/ua") { ok() }
            on("/ciam/auth/login/user") { ok() }
            on("/ciam/auth/login/pass") { ok("""{"result":"RESUME2OIDCP","token":"PRE"}""") }
            on("/as/RES/resume/as/authorization.ping") { HttpResponse(302, it.url, mapOf("location" to "rismycar://login-callback?code=CODE1"), "") }
            on("/as/token.oauth2") { ok("""{"access_token":"$access","refresh_token":"$refresh","expires_in":$expiresIn}""") }
            on("/v1/config") { ok() }
        }
    }
}
