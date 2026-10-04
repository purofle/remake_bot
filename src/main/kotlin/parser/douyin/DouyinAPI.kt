package com.github.purofle.remakebot.parser.douyin

import com.github.purofle.remakebot.data.douyin.DouyinPost
import com.github.purofle.remakebot.network.HttpRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.coroutines.executeAsync
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** The share redirect supplies the post id; post pages and the detail API supply its media data. */
internal object DouyinAPI {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36"
    private val postId = Regex("""/(?:video|note|slides|gallery)/(\d+)""")
    private val renderData = Regex(
        """<script\b[^>]*\bid=["']RENDER_DATA["'][^>]*>(.*?)</script>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    suspend fun fetch(url: String, cookie: String?): DouyinPost {
        // A short link may redirect to /note/ as well as /video/. Never send a login cookie to
        // the short-link host or to an arbitrary destination chosen by its redirect.
        val pageUrl = if (postId.containsMatchIn(url)) url else resolve(url)
        val id = postId.find(pageUrl)?.groupValues?.get(1)
            ?: error("未能从抖音链接获取作品 ID")
        val canonical = "https://www.douyin.com/${if ("/note/" in pageUrl) "note" else "video"}/$id"

        val page = runCatching {
            HttpRequest.get<String>(canonical) {
                if (!cookie.isNullOrBlank()) header("Cookie", cookie)
                header("User-Agent", USER_AGENT)
            }
        }.getOrElse { if (it is CancellationException) throw it else null }
        if (page != null) {
            extractPost(page, id)?.let { return it }
        }

        // Try the aid for the resolved media type first; the other aid may return no detail.
        val aids = if ("/note/" in canonical) listOf("6383", "1128") else listOf("1128", "6383")
        for (aid in aids) {
            val response = try {
                fetchSignedDetail(id, aid, canonical, cookie)
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpRequest.HttpStatusException) {
                if (e.statusCode == 403) {
                    error("抖音作品详情请求被拒绝（HTTP 403）：请检查 DOUYIN_COOKIE 和签名")
                }
                null
            } catch (_: Exception) {
                null
            }
            response?.takeIf { it.isNotBlank() }?.let { extractPost(it, id) }?.let { return it }
        }
        error("抖音未返回作品数据（可能需要登录或通过网站验证）")
    }

    private suspend fun fetchSignedDetail(id: String, aid: String, referer: String, cookie: String?): String {
        val cookies = cookie?.split(';')?.mapNotNull { part ->
            val separator = part.indexOf('=')
            if (separator < 1) null else part.substring(0, separator).trim() to part.substring(separator + 1).trim()
        }?.toMap().orEmpty()
        val uifid = listOf("uifid", "uifid_temp", "uifidtemp", "UIFID", "UIFID_TEMP", "UIFIDTEMP")
            .firstNotNullOfOrNull { cookies[it]?.takeIf(String::isNotBlank) }
        val params = linkedMapOf(
            "device_platform" to "webapp",
            "aid" to aid,
            "channel" to "channel_pc_web",
            "pc_client_type" to "1",
            "version_code" to "290100",
            "version_name" to "29.1.0",
            "cookie_enabled" to (!cookie.isNullOrBlank()).toString(),
            "browser_language" to "zh-CN",
            "browser_platform" to "Win32",
            "browser_name" to "Chrome",
            "browser_version" to "147.0.0.0",
            "browser_online" to "true",
            "engine_name" to "Blink",
            "engine_version" to "147.0.0.0",
            "os_name" to "Windows",
            "os_version" to "10",
            "platform" to "PC",
            "screen_width" to "1536",
            "screen_height" to "864",
            "aweme_id" to id,
        )
        cookies["msToken"]?.let { params["msToken"] = it }
        val query = "https://www.douyin.com/aweme/v1/web/aweme/detail/".toHttpUrl().newBuilder().apply {
            params.forEach { (name, value) -> addQueryParameter(name, value) }
        }.build()
        val bogus = ABogusGenerator(USER_AGENT, aid.toInt()).getValue(query.encodedQuery.orEmpty())
        val bogusSigned = query.newBuilder().addQueryParameter("a_bogus", bogus).apply {
            cookies["s_v_web_id"]?.let { addQueryParameter("verifyFp", it); addQueryParameter("fp", it) }
        }.build()
        val webSigned = uifid?.let { WebSign.sign(bogusSigned, it) }
        return HttpRequest.get<String>(webSigned?.url ?: bogusSigned) {
            header("User-Agent", USER_AGENT)
            header("Referer", referer)
            header("Accept", "application/json")
            if (webSigned != null) {
                header("uifid", uifid)
                header("x-secsdk-web-expire", webSigned.timestamp)
                header("x-secsdk-web-signature", webSigned.signature)
            }
            if (!cookie.isNullOrBlank()) header("Cookie", cookie)
            this
        }
    }

    private suspend fun resolve(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).get().build()
        HttpRequest.client.newCall(request).executeAsync().use { response ->
            check(response.isSuccessful) { "抖音短链接请求失败：HTTP ${response.code}" }
            val finalUrl = response.request.url
            check(finalUrl.host == "douyin.com" || finalUrl.host.endsWith(".douyin.com")) {
                "抖音短链接跳转到了其他网站"
            }
            finalUrl.toString()
        }
    }

    internal fun extractPost(body: String, id: String): DouyinPost? {
        val candidates = buildList {
            renderData.find(body)?.groupValues?.get(1)?.let {
                add(URLDecoder.decode(it, StandardCharsets.UTF_8))
            }
            if (body.trimStart().startsWith('{')) add(body)
            // Some Douyin page versions embed the same data in an assignment instead of RENDER_DATA.
            for (marker in listOf("window._ROUTER_DATA", "window.__INITIAL_STATE__")) {
                val start = body.indexOf(marker)
                if (start >= 0) balancedObject(body, body.indexOf('{', start))?.let { add(it) }
            }
        }
        return candidates.firstNotNullOfOrNull { candidate ->
            runCatching {
                findPost(json.parseToJsonElement(candidate), id)?.let { json.decodeFromJsonElement<DouyinPost>(it) }
            }.getOrNull()
        }
    }

    private fun findPost(value: JsonElement, id: String): JsonObject? = when (value) {
        is JsonObject -> {
            if (value.string("aweme_id") == id &&
                (value["video"] is JsonObject || value["images"] is JsonArray || value["image_post_info"] is JsonObject)
            ) value else value.values.firstNotNullOfOrNull { findPost(it, id) }
        }
        is JsonArray -> value.firstNotNullOfOrNull { findPost(it, id) }
        else -> null
    }

    private fun balancedObject(source: String, start: Int): String? {
        if (start < 0) return null
        var depth = 0
        var quoted = false
        var escaped = false
        for (index in start until source.length) {
            val c = source[index]
            if (quoted) {
                if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') quoted = false
            } else when (c) {
                '"' -> quoted = true
                '{' -> depth++
                '}' -> if (--depth == 0) return source.substring(start, index + 1)
            }
        }
        return null
    }
}

internal fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
