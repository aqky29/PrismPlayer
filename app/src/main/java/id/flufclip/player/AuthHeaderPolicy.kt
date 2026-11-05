package id.flufclip.player

import okhttp3.HttpUrl
import okhttp3.Interceptor

/** Account credentials are restricted to the standard HTTPS YouTube origin and its subdomains. */
internal fun permitsAccountHeaders(url: HttpUrl): Boolean =
    url.isHttps && url.port == 443 && url.username.isEmpty() && url.password.isEmpty() &&
        (url.host == "youtube.com" || url.host.endsWith(".youtube.com"))

internal val sensitiveAccountHeaders = listOf(
    "Cookie", "Authorization", "Proxy-Authorization", "X-Goog-AuthUser", "X-Origin"
)

/** Re-evaluate credentials for every network hop, including redirects. */
internal val accountHeaderInterceptor = Interceptor { chain ->
    val request = chain.request()
    val safe = if (permitsAccountHeaders(request.url)) request else request.newBuilder().apply {
        sensitiveAccountHeaders.forEach { removeHeader(it) }
    }.build()
    chain.proceed(safe)
}
