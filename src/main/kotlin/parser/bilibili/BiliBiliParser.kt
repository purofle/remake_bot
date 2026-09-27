package com.github.purofle.remakebot.parser.bilibili

import com.github.purofle.remakebot.data.bilibili.VideoInfo
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

/**
 * Parse BiliBili Video
 * @param text the message text. Only a message that is nothing but a video id (`av...`, `BV...`), or
 *   one holding a bilibili.com video link or a `b23.tv` link is parsed. An id inside other text is
 *   ignored, chat like "av1 编码" would otherwise trigger it.
 */
class BiliBiliParser(override val text: String) : TextMediaParser {

    override fun extractUrlOrNull(): String? =
        findVideoId()?.let { "https://www.bilibili.com/video/$it" }
            ?: B23_URL_REGEX.find(text)?.value

    override fun supports(): Boolean = extractUrlOrNull() != null

    override suspend fun parse(): MediaResource {
        val videoId = resolveVideoId()
        val videoInfo = BiliBiliAPI.getVideoInfo(videoId).data
        val playUrl = BiliBiliAPI.getPlayUrl(videoId, videoInfo.cid).data.durl.first()

        logger.info { "Parsed video: ${videoInfo.title}, size: ${playUrl.size / 1024 / 1024} MB" }

        val author = Author(videoInfo.owner.name, "https://space.bilibili.com/${videoInfo.owner.mid}")
        val pageUrl = "https://www.bilibili.com/video/${videoInfo.bvid}"

        return MediaResource.Video(
            id = "${videoInfo.bvid}:${videoInfo.cid}",
            title = videoInfo.title,
            author = author,
            platform = MediaPlatform.BiliBili,
            url = pageUrl,
            caption = buildCaption(videoInfo, author, pageUrl),
            videoUrl = playUrl.url,
            coverUrl = videoInfo.picture,
            duration = videoInfo.duration,
        )
    }

    /** The video CDN refuses requests without a BiliBili Referer. */
    override suspend fun downloadVideo(video: MediaResource.Video, target: File) =
        HttpRequest.download(video.videoUrl, target) { header("Referer", "https://www.bilibili.com") }

    private fun buildCaption(info: VideoInfo, author: Author, pageUrl: String): FormattedText {
        val stats = "播放量：${info.stat.view} 弹幕：${info.stat.danmaku} 评论：${info.stat.reply}"
        val description = info.description.trim()

        return formattedText(MAX_CAPTION_LENGTH) {
            line { url(info.title, pageUrl) }
            if (description.isNotEmpty()) line { shrinkable { expandableBlockQuote(description) } }
            line { url("@${author.name}", author.url) }
            line { blockQuote(stats) }
        }
    }

    /** A standalone id, or the id in a bilibili.com video link. */
    private fun findVideoId(): String? =
        (STANDALONE_ID_REGEX.find(text) ?: VIDEO_URL_REGEX.find(text))?.groupValues?.get(1)

    /** A video id in [text] wins over a `b23.tv` link, which costs a request to resolve. */
    private suspend fun resolveVideoId(): VideoId {
        findVideoId()?.let { return toVideoId(it) }

        val url = B23_URL_REGEX.find(text)?.value ?: error("Invalid b23.tv URL: $text")

        return extractVideoId(HttpRequest.get<String>(url)) ?: error("未能从短链接解析出视频：$url")
    }

    companion object {
        /** BV ids are `BV` and 10 base58 characters, which leave out `0`, `I`, `O` and `l`. */
        private const val VIDEO_ID = """(?:av|AV|aV|Av)\d+|BV[1-9A-HJ-NP-Za-km-z]{10}"""

        private val STANDALONE_ID_REGEX = Regex("""^\s*($VIDEO_ID)\s*$""")

        private val VIDEO_URL_REGEX = Regex("""https?://(?:www\.|m\.)?bilibili\.com/video/($VIDEO_ID)""")

        /** Only for the page a `b23.tv` link redirects to, where the first id is the video's own. */
        private val PAGE_VIDEO_ID_REGEX = Regex("""(?:av|AV|aV|Av)\d+|BV\w+""")

        private val B23_URL_REGEX = Regex("""https?://b23\.tv/\w+""")

        private val logger = KotlinLogging.logger("BiliBiliParser")

        private fun extractVideoId(page: String): VideoId? =
            PAGE_VIDEO_ID_REGEX.find(page)?.let { toVideoId(it.value) }

        private fun toVideoId(id: String): VideoId {
            logger.debug { "Extracting video id: $id" }

            return if (id.startsWith("av", ignoreCase = true)) {
                VideoId.Aid(id.substring(2).toLong())
            } else {
                VideoId.Bvid(id)
            }
        }
    }
}
