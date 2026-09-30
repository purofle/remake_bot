// Ported from Evil0ctal/Douyin_TikTok_Download_API, src/dtk/signing/native/websign.py.
// Copyright Evil0ctal contributors. Licensed under Apache-2.0.
// https://github.com/Evil0ctal/Douyin_TikTok_Download_API/blob/main/LICENSE
package com.github.purofle.remakebot.parser.douyin

import okhttp3.HttpUrl
import java.security.MessageDigest

/** Signs the exact URL query sent to Douyin, using its webSignUrl scheme. */
internal object WebSign {
    private const val SALT = "A96D855A08C0A9707F8BEF0D9A527E4E"

    data class SignedRequest(val url: HttpUrl, val signature: String, val timestamp: String)

    fun sign(url: HttpUrl, uifid: String, timestamp: Long = System.currentTimeMillis() / 1000): SignedRequest {
        require(uifid.isNotBlank()) { "抖音签名缺少 UIFID" }
        val params = buildList {
            for (index in 0 until url.querySize) {
                add(url.queryParameterName(index) to url.queryParameterValue(index).orEmpty())
            }
            if (none { it.first == "uifid" }) add("uifid" to uifid)
            add("timestamp" to timestamp.toString())
        }
        // Match Python quote(..., safe="*-._") in Douyin's webSignUrl implementation.
        // The MD5 covers these exact bytes, including the percent-encoding.
        val query = params.joinToString("&") { (name, value) -> "${encode(name)}=${encode(value)}" }
        val signature = MessageDigest.getInstance("MD5")
            .digest("${uifid}_${timestamp}_${SALT}_${query}".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val signedUrl = url.newBuilder()
            .encodedQuery(query)
            .addQueryParameter("x-secsdk-web-signature", signature)
            .build()
        check(signedUrl.encodedQuery == "$query&x-secsdk-web-signature=$signature") {
            "抖音签名查询参数在构造 URL 时发生变化"
        }
        return SignedRequest(signedUrl, signature, timestamp.toString())
    }

    private fun encode(value: String): String = buildString {
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            val code = byte.toInt() and 0xff
            if (code in 'A'.code..'Z'.code || code in 'a'.code..'z'.code ||
                code in '0'.code..'9'.code || code == '-'.code || code == '_'.code ||
                code == '.'.code || code == '~'.code || code == '*'.code
            ) append(code.toChar()) else append("%%%02X".format(code))
        }
    }
}
