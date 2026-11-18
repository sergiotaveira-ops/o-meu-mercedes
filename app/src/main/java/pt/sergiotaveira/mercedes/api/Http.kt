package pt.sergiotaveira.mercedes.api

data class HttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val followRedirects: Boolean = true,
)

/** [headers] keys are lower-case. [finalUrl] is the URL after redirects. */
data class HttpResponse(val status: Int, val finalUrl: String, val headers: Map<String, String>, val body: String)

fun interface HttpTransport {
    suspend fun execute(request: HttpRequest): HttpResponse

    /** Start a clean cookie/session context for a new login. Each login must not reuse the previous one's cookies. */
    fun newSession() {}
}
