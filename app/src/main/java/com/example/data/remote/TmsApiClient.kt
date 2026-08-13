package com.example.data.remote

import com.example.domain.repository.TokenRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Single place the app builds HTTP clients.
 *
 * TLS is left entirely to OkHttp and the platform trust store — there is no custom
 * `TrustManager`, no hostname-verifier override and no certificate pinning of a certificate we do
 * not yet have. Adding pinning is a deliberate later step once the TMS publishes its chain.
 */
object TmsApiClient {

    /** Generous enough for a POD upload on a rural 3G connection, short enough to fail usefully. */
    private const val CONNECT_TIMEOUT_SECONDS = 20L
    private const val READ_TIMEOUT_SECONDS = 30L
    private const val WRITE_TIMEOUT_SECONDS = 60L

    fun moshi(): Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    fun okHttp(
        tokens: TokenRepository,
        isDebugBuild: Boolean
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor(AuthInterceptor(tokens))
        .addInterceptor(loggingInterceptor(isDebugBuild))
        .build()

    /**
     * Debug builds log request lines and a redacted header set. Bodies are never logged in any
     * build: they carry signatures, customer names and POD imagery. Release logs nothing.
     */
    private fun loggingInterceptor(isDebugBuild: Boolean): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = if (isDebugBuild) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
            redactHeader("Authorization")
            redactHeader("Cookie")
            redactHeader("Set-Cookie")
        }

    fun retrofit(
        baseUrl: String,
        client: OkHttpClient,
        moshi: Moshi = moshi()
    ): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
}
