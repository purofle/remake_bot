package com.github.purofle.remakebot.parser.xhs

import com.github.purofle.remakebot.data.xhs.XhsImage
import com.github.purofle.remakebot.data.xhs.XhsNote
import com.github.purofle.remakebot.data.xhs.XhsStreamEntry
import com.github.purofle.remakebot.network.HttpRequest
import com.github.purofle.remakebot.parser.Author
import com.github.purofle.remakebot.parser.MediaPlatform
import com.github.purofle.remakebot.parser.MediaResource
import com.github.purofle.remakebot.parser.TextMediaParser
import com.github.purofle.remakebot.text.formattedText
import com.github.purofle.remakebot.utils.MAX_CAPTION_LENGTH
import io.github.oshai.kotlinlogging.KotlinLogging
import org.drinkless.tdlib.TdApi.FormattedText
import java.io.File

private const val NOTE_TYPE_VIDEO = "video"

/** An image note. */
private const val NOTE_TYPE_NORMAL = "normal"

/** Media requests need this or the CDN refuses them. */
private const val XHS_REFERER = "https://www.xiaohongshu.com/"

/**
 * Parse a Xiaohongshu video or image note.
 *
 * Every expected failure (login wall, unsupported note type, no media) is thrown with a message that
 * is safe to show in chat; the caller reports it back to the user.
 *
 * @param text the message text, holding a note URL or an `xhslink.com`/`xhslink.cn` share link,
 *   possibly among the rest of an app share text. The URL has to be
 *   requested exactly as it appeared, with its query string, which carries a time limited
 *   `xsec_token`.
 * @param cookie the `XHS_COOKIE` value, if the bot has one. Most notes are behind a login wall.
 */
class XhsParser(override val text: String, val cookie: String?) : TextMediaParser {

    /**
     * The first note URL in [text], either a full one or an `xhslink` share link. Trailing
     * punctuation is excluded by only accepting URL safe characters in the query string.
     */
    override fun extractUrlOrNull(): String? =
        (NOTE_URL_REGEX.find(text) ?: SHORT_URL_REGEX.find(text))?.value

    override fun supports(): Boolean = extractUrlOrNull() != null

    override suspend fun parse(): MediaResource {
        val noteUrl = extractUrlOrNull() ?: error("Invalid xiaohongshu URL: $text")
        val fetched = XhsAPI.fetchNote(noteUrl, cookie)
        val note = fetched.note

        val user = note.user?.takeIf { it.userId.isNotBlank() && it.nickname.isNotBlank() }
        val author = user?.let { Author(it.nickname, "https://www.xiaohongshu.com/user/profile/${it.userId}") }

        return when (note.type) {
            NOTE_TYPE_VIDEO -> parseVideo(fetched, author, noteUrl)
            NOTE_TYPE_NORMAL -> parsePhotos(fetched, author, noteUrl)
            else -> {
                logger.info { "Note ${fetched.noteId} is type '${note.type}', which is not supported" }
                error("暂不支持该类型的小红书笔记（type=${note.type}）")
            }
        }
    }

    private fun parsePhotos(fetched: XhsNoteResult, author: Author?, noteUrl: String): MediaResource.Photos {
        val note = fetched.note
        val photoUrls = note.imageList.mapNotNull { photoUrl(it) }
        if (photoUrls.isEmpty()) error("未获取到笔记图片")

        return MediaResource.Photos(
            id = fetched.noteId,
            title = note.title,
            author = author,
            platform = MediaPlatform.RedNote,
            url = noteUrl,
            caption = buildCaption(note, author, noteUrl),
            photoUrls = photoUrls,
        )
    }

    private fun parseVideo(fetched: XhsNoteResult, author: Author?, noteUrl: String): MediaResource.Video {
        val note = fetched.note
        val stream = selectStream(note)
        val videoUrl = originVideoUrl(note) ?: stream?.masterUrl ?: error("未获取到视频流")
        val coverUrl = note.imageList.firstOrNull()?.let { it.urlDefault.ifBlank { it.url } }?.takeIf { it.isNotBlank() }
            ?: error("未获取到视频封面")

        return MediaResource.Video(
            // The note id rather than the URL: a share link's own query string carries a rotating
            // xsec_token, so URL keyed entries would never be reused.
            id = fetched.noteId,
            title = note.title,
            author = author,
            platform = MediaPlatform.RedNote,
            url = noteUrl,
            caption = buildCaption(note, author, noteUrl),
            videoUrl = videoUrl,
            // The photo host needs no Referer (verified), so the sender can fetch it as is.
            coverUrl = rawImageUrl(coverUrl),
            duration = note.video?.capa?.duration?.takeIf { it > 0 } ?: ((stream?.duration ?: 0) / 1000).toInt(),
        )
    }

    /** The video CDN is a different host than the photo one, so keep a Referer in case it is hotlink protected. */
    override suspend fun downloadVideo(video: MediaResource.Video, target: File) =
        HttpRequest.download(video.videoUrl, target) { header("Referer", XHS_REFERER) }

    private fun buildCaption(note: XhsNote, author: Author?, noteUrl: String): FormattedText {
        val description = note.desc.trim()

        return formattedText(MAX_CAPTION_LENGTH) {
            line { url(note.title, noteUrl) }
            if (author != null) line { url("@${author.name}", author.url) }
            if (description.isNotEmpty()) line { shrinkable { expandableBlockQuote(description) } }
        }
    }

    companion object {
        private val logger = KotlinLogging.logger("XhsParser")

        private val NOTE_URL_REGEX = Regex(
            """https?://(?:www\.)?xiaohongshu\.com/(?:explore|discovery/item)/[0-9a-zA-Z]+(?:\?[\w=&%.~-]*)?"""
        )

        private val SHORT_URL_REGEX = Regex(
            """https?://(?:www\.)?xhslink\.(?:com|cn)/[\w/?=&%.~-]+"""
        )

        private val IMAGE_TRACE_REGEX = Regex("""/[a-f0-9]{32}/(.*)/([^/!]+)(?:!.*)?""")

        private val IMAGE_FILE_REGEX = Regex("""/([^/!]+)(?:!.*)?$""")

        /**
         * The photo urls use this host, which serves the same OSS object without a signature, cookie
         * or Referer.
         */
        private const val RAW_IMAGE_HOST = "https://sns-img-hw.xhscdn.com"

        /**
         * Has the photo host re-encode the original as JPEG at full size, since an original can be a
         * format (e.g. HEIC) Telegram does not take as a photo.
         */
        private const val JPEG_QUERY = "?imageView2/2/w/0/format/jpg"

        private fun photoUrl(image: XhsImage): String? {
            if (image.fileId.isNotBlank()) return "$RAW_IMAGE_HOST/${image.fileId}$JPEG_QUERY"

            return image.urlDefault.ifBlank { image.url }.takeIf { it.isNotBlank() }?.let { "${rawImageUrl(it)}$JPEG_QUERY" }
        }

        /**
         * Serves the originally uploaded file by its key, without a signature, cookie or Referer.
         * It is the best quality there is, so it is preferred over the transcoded streams.
         */
        private const val ORIGIN_VIDEO_HOST = "https://sns-video-bd.xhscdn.com"

        private fun originVideoUrl(note: XhsNote): String? =
            note.video?.consumer?.originVideoKey?.takeIf { it.isNotBlank() }?.let { "$ORIGIN_VIDEO_HOST/$it" }

        /** h264 first, then the newer codecs, taking the first entry that actually has a URL. */
        private fun selectStream(note: XhsNote): XhsStreamEntry? {
            val stream = note.video?.media?.stream ?: return null

            return listOf(stream.h264, stream.av1, stream.h265, stream.h266)
                .firstNotNullOfOrNull { entries -> entries.firstOrNull { it.masterUrl.isNotBlank() } }
        }

        /**
         * A cover url the CDN will actually serve. `imageList[0].urlDefault` points at
         * `sns-webpic-qc.xhscdn.com` with a signed path that answers 403 unless the request carries
         * the page's session, which we deliberately do not forward to a CDN. The same OSS object is
         * public on the photo host, addressed by its trace id.
         */
        private fun rawImageUrl(url: String): String = "$RAW_IMAGE_HOST/${imageTraceId(url)}"

        private fun imageTraceId(url: String): String {
            val match = IMAGE_TRACE_REGEX.find(url) ?: IMAGE_FILE_REGEX.find(url) ?: return url

            return match.groupValues.drop(1).joinToString("/")
        }
    }
}
