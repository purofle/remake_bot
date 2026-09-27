package com.github.purofle.remakebot.parser.xhs

/**
 * Anchored on `window.` because a bare `__INITIAL_STATE__` also turns up elsewhere in the page (in
 * bundle URLs and preload lists), and starting the capture at one of those would grab the wrong
 * script's text.
 */
private val INITIAL_STATE_REGEX = Regex(
    """window\.__INITIAL_STATE__\s*=\s*(.*?)</script>""",
    RegexOption.DOT_MATCHES_ALL,
)

/**
 * JavaScript's `undefined` has no JSON counterpart, so values using it become `null`.
 *
 * This also rewrites the word when it appears inside a string, so a title that happens to contain
 * "undefined" would read "null" in the caption. Keeping the replacement out of string literals needs
 * a hand written scanner, which is not worth it for a case that rare.
 */
private val UNDEFINED_REGEX = Regex("""\bundefined\b""")

private val META_REFRESH_REGEX = Regex(
    """<meta[^>]*http-equiv\s*=\s*["']?refresh["']?[^>]*content\s*=\s*["']([^"']*)["']""",
    RegexOption.IGNORE_CASE,
)

private val META_REFRESH_URL_REGEX = Regex("""url\s*=\s*(.+)""", RegexOption.IGNORE_CASE)

private val JS_LOCATION_REGEX = Regex("""window\.location\.(?:href|replace)\s*=\s*["']([^"']+)["']""")

private val NOTE_ID_REGEX = Regex("""/(?:explore|discovery/item|item)/([0-9a-fA-F]{24})""")

private val LOOSE_NOTE_ID_REGEX = Regex("""/([0-9a-fA-F]{24})(?:[/?#]|$)""")

/**
 * A note page ships all of its data in a `<script>window.__INITIAL_STATE__={...}</script>` blob, and
 * the page is the only way to read a note without signing API requests.
 *
 * Returns that object literal as JSON, or null when the page has none (a login wall, a 404, or a
 * layout change).
 */
internal fun extractInitialState(html: String): String? =
    INITIAL_STATE_REGEX.find(html)
        ?.groupValues?.get(1)
        ?.trim()
        ?.removeSuffix(";")
        ?.let { UNDEFINED_REGEX.replace(it, "null") }

/**
 * Resolves the URL a share page points at. `xhslink.com` links are not always 302s — some answer
 * with a `200` page that redirects via a meta tag or a script — so both are checked.
 */
internal fun extractRedirectUrl(html: String): String? {
    META_REFRESH_REGEX.find(html)?.groupValues?.get(1)?.let { content ->
        META_REFRESH_URL_REGEX.find(content)?.groupValues?.get(1)?.let { url ->
            return url.trim().trim('"', '\'', ';')
        }
    }

    return JS_LOCATION_REGEX.find(html)?.groupValues?.get(1)
}

/** The 24 hex character note id from a note URL, if it has one. */
internal fun noteIdInUrl(url: String): String? =
    (NOTE_ID_REGEX.find(url) ?: LOOSE_NOTE_ID_REGEX.find(url))?.groupValues?.get(1)
