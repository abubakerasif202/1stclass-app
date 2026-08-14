package au.com.firstclassexpress.driver.data.remote

import au.com.firstclassexpress.driver.domain.repository.TokenRepository
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches `Authorization: Bearer …` when — and only when — a live token exists.
 *
 * Unauthenticated endpoints (login) are skipped so credentials are never paired with a stale
 * bearer token, and a missing token simply means no header: we let the server answer 401 rather
 * than guessing at authorisation on the client.
 */
class AuthInterceptor(
    private val tokens: TokenRepository,
    private val clock: () -> Long = System::currentTimeMillis
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (isUnauthenticated(request.url.encodedPath)) return chain.proceed(request)

        val token = tokens.peek(clock())?.accessToken ?: return chain.proceed(request)

        return chain.proceed(
            request.newBuilder()
                .header("Authorization", "Bearer ${token.value}")
                .build()
        )
    }

    private fun isUnauthenticated(path: String): Boolean =
        UNAUTHENTICATED_PATH_SEGMENTS.any { path.contains(it, ignoreCase = true) }

    private companion object {
        /** Login must not carry a bearer token; refresh is listed for when the TMS defines one. */
        val UNAUTHENTICATED_PATH_SEGMENTS = listOf("/auth", "/login", "/token")
    }
}
