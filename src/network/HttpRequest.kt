package com.github.purofle.remakebot.network

import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.coroutines.executeAsync

object HttpRequest {
    val client = OkHttpClient.Builder()
        .addInterceptor {
            setUserAgent(it)
        }
        .build()
    val json = Json {
        ignoreUnknownKeys = true
    }

    private fun setUserAgent(chain: Interceptor.Chain) = chain.request().newBuilder()
        .header(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Mobile Safari/537.36"
        )
        .build().let {
            chain.proceed(it)
        }

    /**
     * @param url the URL to request
     * @param params optional query parameters
     */
    suspend inline fun <reified T> get(url: String, params: Map<String, Any?>? = null): T {

        val httpUrl = url.toHttpUrl().newBuilder().apply {
            params?.forEach { (name, value) -> addQueryParameter(name, value.toString()) }
        }.build()

        val request = Request.Builder().url(httpUrl).get().build()

        return client.newCall(request).executeAsync().use {

            if (T::class == String::class) {
                it.body.string() as T
            } else {
                json.decodeFromString<T>(it.body.string())
            }
        }
    }
}