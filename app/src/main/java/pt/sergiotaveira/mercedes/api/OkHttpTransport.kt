package pt.sergiotaveira.mercedes.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** In-memory cookie jar pre-seeded with the CIAM.DEVICE cookie the Mercedes login expects. */
class LoginCookieJar(deviceId: String) : CookieJar {
    private val cookies = mutableListOf<Cookie>()

    init {
        cookies += Cookie.Builder().name("CIAM.DEVICE").value(deviceId).domain("id.mercedes-benz.com").path("/").build()
    }

    @Synchronized override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { c -> this.cookies.removeAll { it.name == c.name && it.domain == c.domain }; this.cookies += c }
    }

    @Synchronized override fun loadForRequest(url: HttpUrl): List<Cookie> = cookies.filter { it.matches(url) }
}

class OkHttpTransport(base: OkHttpClient, deviceId: String) : HttpTransport {
    private val client = base.newBuilder().cookieJar(LoginCookieJar(deviceId)).build()
    private val noRedirect = client.newBuilder().followRedirects(false).followSslRedirects(false).build()

    override suspend fun execute(request: HttpRequest): HttpResponse = withContext(Dispatchers.IO) {
        val contentType = request.headers.entries.firstOrNull { it.key.equals("content-type", true) }?.value
        val body = request.body?.toRequestBody(contentType?.toMediaTypeOrNull())
        val req = Request.Builder().url(request.url.toHttpUrl()).apply {
            request.headers.forEach { (k, v) -> header(k, v) }
            method(request.method, body ?: if (request.method == "POST") ByteArray(0).toRequestBody(null) else null)
        }.build()
        (if (request.followRedirects) client else noRedirect).newCall(req).execute().use { r ->
            HttpResponse(
                status = r.code,
                finalUrl = r.request.url.toString(),
                headers = r.headers.associate { it.first.lowercase() to it.second },
                body = r.body?.string().orEmpty(),
            )
        }
    }
}
