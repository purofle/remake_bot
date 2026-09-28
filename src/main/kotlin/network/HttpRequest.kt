package com.github.purofle.remakebot.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.coroutines.executeAsync
import java.io.File

object HttpRequest {
    val client = OkHttpClient.Builder()
        .addInterceptor {
            setUserAgent(it)
        }
        .build()
    val json = Json {
        ignoreUnknownKeys = true
    }

    /** A default only: a request that sets its own `User-Agent` keeps it. */
    private fun setUserAgent(chain: Interceptor.Chain) = chain.request().takeIf { it.header("User-Agent") == null }
        ?.newBuilder()
        ?.header(
            "User-Agent",
            // Keep the major version one that sites actually recognise: Xiaohongshu answers an
            // "unsupported browser" shell (with an `__INITIAL_STATE__` that has no note in it) for a
            // version newer than anything it knows.
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36"
        )
        ?.build()
        .let { chain.proceed(it ?: chain.request()) }

    /**
     * @param url the URL to request
     * @param params optional query parameters
     * @param requestBuilder extra request customization, e.g. a Cookie header or a `User-Agent` other
     *   than the default one.
     */
    suspend inline fun <reified T> get(
        url: String,
        params: Map<String, Any?>? = null,
        requestBuilder: Request.Builder.() -> Request.Builder = { this },
    ): T {
        val httpUrl = url.toHttpUrl().newBuilder().apply {
            params?.forEach { (name, value) -> addQueryParameter(name, value.toString()) }
        }.build()

        // Built outside withContext on purpose: its block is crossinline, and an inline function's
        // lambda parameter cannot be inlined across that boundary.
        val request = Request.Builder()
            .url(httpUrl)
            .requestBuilder()
            .get()
            .build()

        return withContext(Dispatchers.IO) {
            client.newCall(request).executeAsync().use {
                check(it.isSuccessful) { "HTTP ${it.code} for ${it.request.url.host}${it.request.url.encodedPath}" }
                if (T::class == String::class) {
                    it.body.string() as T
                } else {
                    json.decodeFromString<T>(it.body.string())
                }
            }
        }
    }

    suspend fun getAsByteArray(url: String, requestBuilder: Request.Builder.() -> Request.Builder = { this }): ByteArray = withContext(Dispatchers.IO) {
        val httpUrl = url.toHttpUrl()
        val request = Request.Builder()
            .url(httpUrl)
            .requestBuilder()
            .build()

        client.newCall(request).executeAsync().use {
            check(it.isSuccessful) { "HTTP ${it.code} for ${it.request.url.host}${it.request.url.encodedPath}" }
            return@use it.body.bytes()
        }
    }

    /**
     * Streams the response body into [target] instead of buffering it in memory, so large videos do
     * not have to fit in the heap. [requestBuilder] is for hosts that want a Referer or a Cookie.
     */
    suspend fun download(
        url: String,
        target: File,
        requestBuilder: Request.Builder.() -> Request.Builder = { this },
    ): Unit = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url.toHttpUrl())
            .requestBuilder()
            .build()

        client.newCall(request).executeAsync().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code} for ${response.request.url.host}${response.request.url.encodedPath}" }
            response.body.byteStream().use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }
}
