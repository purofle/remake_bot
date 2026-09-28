package com.github.purofle.remakebot.parser.xhs

import com.github.purofle.remakebot.data.xhs.XhsInitialState
import com.github.purofle.remakebot.data.xhs.XhsNote
import com.github.purofle.remakebot.data.xhs.XhsNoteState
import com.github.purofle.remakebot.network.HttpRequest
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import java.net.URI

/**
 * A fetched note together with the id it is registered under. The id is what callers should key a
 * cache on: a note URL carries a rotating `xsec_token`, so URL keyed entries would never be reused.
 */
data class XhsNoteResult(
    val noteId: String,
    val note: XhsNote,
)

/**
 * Reads Xiaohongshu note pages. There is no signed API here: the note is taken out of the
 * `window.__INITIAL_STATE__` object that the page ships with its HTML.
 */
object XhsAPI {
    private const val STATE_MARKER = "__INITIAL_STATE__"

    private val logger = KotlinLogging.logger("XhsAPI")

    /**
     * Xiaohongshu shows a phone browser the whole note without a login, while a desktop one is sent
     * to the login page.
     */
    private const val MOBILE_USER_AGENT =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) " +
            "Version/17.0 Mobile/15E148 Safari/604.1"

    private const val BROWSER_ACCEPT =
        "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"

    /** How much of a captured blob to put in the log, enough to see where it starts. */
    private const val LOG_PREFIX = 160

    /** A backslash escaping one of the characters a cookie delimiter or value can contain. */
    private val JSON_ESCAPE_REGEX = Regex("""\\(?=[;=,%{}"])""")

    /** Hosts (and their subdomains) that may receive `XHS_COOKIE`. */
    private val COOKIE_HOSTS = listOf("xiaohongshu.com", "xhslink.com", "xhslink.cn")

    /** Cookie names only, so a diagnostic can never echo a value. */
    private val COOKIE_NAME_REGEX = Regex("""[A-Za-z0-9_-]+""")

    /**
     * Stricter than [HttpRequest.json]: `undefined` in the page becomes `null` here, and third party
     * fields are frequently missing or null, so a null has to fall back to a field's default rather
     * than fail the whole decode.
     */
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    /**
     * Fetches a note and returns it. Throws with a message that is safe to show in chat.
     *
     * [url] has to be requested as given: note URLs carry a time limited `xsec_token`, and dropping
     * the query would land on an empty or blocked page.
     *
     * The mobile page is tried first since it needs no cookie; the desktop page, sent with [cookie],
     * is the fallback.
     */
    suspend fun fetchNote(url: String, cookie: String?): XhsNoteResult {
        fetchMobileNote(url)?.let { return it }

        val session = normalizeCookie(cookie)

        // A cookie that cannot be parsed would be sent as one garbage cookie and the answer would be
        // the anonymous shell page, which looks like a page layout problem. Say what is actually wrong.
        if (!cookie.isNullOrBlank() && session == null) {
            logger.warn { "XHS_COOKIE has no name=value pairs (${cookie.length} chars)" }
            error("XHS_COOKIE 格式无法识别，需要 name=value; name=value 形式（或整行 Cookie: ...）")
        }

        var html = fetchHtml(url, session)

        // Some share links answer with a 200 page that redirects itself instead of a 302.
        if (!html.contains(STATE_MARKER)) {
            extractRedirectUrl(html)?.let { redirected ->
                logger.debug { "Following a self redirect to $redirected" }
                // The target comes from page content, so only hand the login cookie to Xiaohongshu itself.
                html = fetchHtml(redirected, session.takeIf { isCookieHost(redirected) })
            }
        }

        val jsonText = extractInitialState(html)
        if (jsonText == null) {
            logger.warn { "No $STATE_MARKER in ${html.length} bytes of HTML (${cookieState(session)})" }
            error("未获取到笔记页面数据（${cookieState(session)}，或帖子已删除）")
        }

        // If the capture is not the state object at all, the page layout changed or the marker matched
        // something unrelated; say so instead of blaming the login.
        if (!jsonText.contains("noteDetailMap")) {
            logger.warn {
                "Captured text is not the note state (${cookieState(session)}, " +
                    "keys=${cookieKeys(session)}): ${jsonText.length}B starting with " +
                    jsonText.take(LOG_PREFIX)
            }
            error("未获取到笔记内容（页面结构可能已变化）")
        }

        val state = runCatching { json.decodeFromString<XhsInitialState>(jsonText) }.getOrElse { e ->
            logger.warn(e) { "Cannot decode $STATE_MARKER (${jsonText.length} bytes)" }
            error("未获取到笔记内容（页面结构可能已变化）")
        }

        // A login wall answers with a page whose note data is missing entirely.
        val noteState = state.note
        if (noteState == null) {
            logger.warn {
                "Page carries no note data (${cookieState(session)}, keys=${cookieKeys(session)}): " +
                    "html=${html.length}B state=${jsonText.length}B starting with ${jsonText.take(LOG_PREFIX)}"
            }
            error("该帖子需要登录后查看（${cookieState(session)}）")
        }

        return findNote(noteState, url) ?: run {
            logger.warn {
                "Note ${noteIdInUrl(url)} is not readable: firstNoteId=${noteState.firstNoteId} " +
                    "mapKeys=${noteState.noteDetailMap.keys} entriesWithoutNote=" +
                    "${noteState.noteDetailMap.values.count { it.note == null }}/${noteState.noteDetailMap.size}"
            }
            error("未获取到笔记内容（笔记可能已删除，或需要登录）")
        }
    }

    /** The note from the mobile page, or null when that page does not carry one. */
    private suspend fun fetchMobileNote(url: String): XhsNoteResult? {
        val html = try {
            fetchHtml(url, session = null, userAgent = MOBILE_USER_AGENT)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn(e) { "Mobile note page failed, falling back to the desktop one" }
            return null
        }

        val note = extractInitialState(html)
            ?.let { runCatching { json.decodeFromString<XhsInitialState>(it) }.getOrNull() }
            ?.noteData?.data?.noteData
            ?.takeIf { it.noteId.isNotBlank() }

        if (note == null) {
            logger.info { "Mobile note page has no note data (${html.length}B), falling back to the desktop one" }
            return null
        }

        return XhsNoteResult(note.noteId, note)
    }

    private fun cookieState(session: String?): String =
        if (session.isNullOrBlank()) "未配置 XHS_COOKIE" else "XHS_COOKIE 可能不是登录态（缺少 web_session）"

    /**
     * The cookie *names* present, never the values. Names are matched strictly, so anything that does
     * not parse as a `name=value` pair is reported as unrecognised rather than echoed: an earlier
     * version of this leaked a whole cookie value into the log when the input used another separator.
     *
     * Xiaohongshu's login token is `web_session`; without a name of that shape the request is simply
     * anonymous and the answer is the app shell with no note in it.
     */
    private fun cookieKeys(session: String?): String =
        session?.split(';')
            ?.mapNotNull { it.toCookiePair()?.first }
            ?.takeIf { it.isNotEmpty() }
            ?.toString()
            ?: "(none recognised)"

    /**
     * Turns a pasted cookie into the `name=value; name=value` form a header needs, or null when there
     * is nothing usable in it.
     *
     * Handles the value alone, the whole devtools header line (`Cookie: a=1; b=2`), JSON escaped
     * delimiters (`a\=1\; b\=2`), and stray newlines. A `name: value` list is deliberately *not*
     * guessed at: cookie values may contain `:` themselves, so the result would be silently wrong.
     */
    private fun normalizeCookie(cookie: String?): String? {
        val raw = cookie?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val prefix = "cookie:"

        return raw
            .let { if (it.startsWith(prefix, ignoreCase = true)) it.drop(prefix.length) else it }
            .replace(JSON_ESCAPE_REGEX, "")
            .split(';')
            .mapNotNull { it.toCookiePair() }
            .takeIf { it.isNotEmpty() }
            ?.joinToString("; ") { (name, value) -> "$name=$value" }
    }

    /** A cookie pair from a `name=value` part, or null when the part is not one. */
    private fun String.toCookiePair(): Pair<String, String>? {
        if ('=' !in this) return null

        val name = substringBefore('=').trim()
        if (!name.matches(COOKIE_NAME_REGEX)) return null

        return name to substringAfter('=').trim()
    }

    private fun isCookieHost(url: String): Boolean {
        val host = runCatching { URI(url).host }.getOrNull()?.lowercase() ?: return false
        return COOKIE_HOSTS.any { host == it || host.endsWith(".$it") }
    }

    private suspend fun fetchHtml(url: String, session: String?, userAgent: String? = null): String =
        HttpRequest.get<String>(url) {
            if (!session.isNullOrBlank()) header("Cookie", session)
            if (userAgent != null) header("User-Agent", userAgent)
            header("Accept", BROWSER_ACCEPT)
        }

    /**
     * Prefers the note id from the URL because it is there even when `firstNoteId` is missing, then
     * falls back to `firstNoteId` and finally to whatever the map happens to hold.
     */
    private fun findNote(state: XhsNoteState, url: String): XhsNoteResult? {
        noteIdInUrl(url)?.let { id ->
            state.noteDetailMap[id]?.note?.let { return XhsNoteResult(id, it) }
        }

        state.noteDetailMap[state.firstNoteId]?.note?.let { return XhsNoteResult(state.firstNoteId, it) }

        return state.noteDetailMap.entries.firstNotNullOfOrNull { (id, detail) ->
            detail.note?.let { XhsNoteResult(id, it) }
        }
    }
}
