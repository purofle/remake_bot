package com.github.purofle.remakebot.parser.douyin

import com.github.purofle.remakebot.data.douyin.DouyinImage
import com.github.purofle.remakebot.data.douyin.DouyinPost
import com.github.purofle.remakebot.network.HttpRequest
import com.github.purofle.remakebot.parser.Author
import com.github.purofle.remakebot.parser.MediaPlatform
import com.github.purofle.remakebot.parser.MediaResource
import com.github.purofle.remakebot.parser.TextMediaParser
import com.github.purofle.remakebot.text.formattedText
import com.github.purofle.remakebot.utils.MAX_CAPTION_LENGTH
import java.io.File

/** Parses Douyin short shares, video pages, and image-note pages inside ordinary message text. */
class DouyinParser(override val text: String, private val cookie: String?) : TextMediaParser {
    override fun extractUrlOrNull(): String? = (PAGE_URL.find(text) ?: SHORT_URL.find(text))?.value

    override fun supports(): Boolean = extractUrlOrNull() != null

    override suspend fun parse(): MediaResource {
        val url = extractUrlOrNull() ?: error("无效的抖音链接")
        return fromPost(DouyinAPI.fetch(url, cookie))
    }

    internal fun fromPost(post: DouyinPost): MediaResource {
        val id = post.id
        val galleryItems = post.imagePostInfo?.images?.takeIf { it.isNotEmpty() } ?: post.images
        val isGallery = galleryItems.isNotEmpty()
        val images = images(post)
        val pageUrl = "https://www.douyin.com/${if (isGallery) "note" else "video"}/$id"
        val title = post.desc.trim().ifEmpty { post.itemTitle?.trim().orEmpty() }.ifEmpty { "抖音作品" }
        val author = post.author?.takeIf { it.nickname.isNotBlank() && !it.secUid.isNullOrBlank() }
            ?.let { Author(it.nickname, "https://www.douyin.com/user/${it.secUid}") }
        val caption = formattedText(MAX_CAPTION_LENGTH) {
            line { url(title, pageUrl) }
            if (author != null) line { url("@${author.name}", author.url) }
        }

        if (isGallery) {
            if (images.isEmpty()) error("未获取到图文图片")
            return MediaResource.Photos(id, title, author, MediaPlatform.Douyin, pageUrl, caption, images.map { MediaResource.Photo(it) })
        }

        val video = post.video ?: error("未获取到抖音作品媒体")
        // play_addr is the clear playback stream. download_addr may be a CENC encrypted file for
        // paid posts, so it must not be used as a fallback (see douyin-downloader).
        val videoUrl = sequenceOf(video.playAddr)
            .plus(video.bitRate.asSequence().map { it.playAddr })
            .filterNotNull()
            .flatMap { it.urlList.asSequence() }
            .firstOrNull { !it.contains("/playwm/") && !it.contains("watermark=1") }
            ?: error("未获取到无水印视频流")
        val cover = listOf(video.originCover, video.cover, video.dynamicCover)
            .firstNotNullOfOrNull { it?.urlList?.firstOrNull() }
            ?: error("未获取到视频封面")
        val duration = (video.duration / 1000).toInt()
        return MediaResource.Video(id, title, author, MediaPlatform.Douyin, pageUrl, caption, videoUrl, cover, duration)
    }

    override suspend fun downloadVideo(video: MediaResource.Video, target: File) =
        HttpRequest.download(video.videoUrl, target) { header("Referer", "https://www.douyin.com/") }

    private fun images(post: DouyinPost): List<String> {
        val entries = post.imagePostInfo?.images?.takeIf { it.isNotEmpty() } ?: post.images
        return entries.mapNotNull(::imageUrl)
    }

    private fun imageUrl(image: DouyinImage): String? =
        listOf(
            image.watermarkFreeUrls,
            image.originImage?.urlList.orEmpty(),
            image.displayImage?.urlList.orEmpty(),
            image.downloadAddr?.urlList.orEmpty(),
            image.urlList,
        ).firstNotNullOfOrNull { urls ->
            val valid = urls.filter { it.startsWith("https://") || it.startsWith("http://") }
            valid.firstOrNull {
                val path = it.substringBefore('?').lowercase()
                path.endsWith(".jpeg") || path.endsWith(".jpg")
            } ?: valid.firstOrNull()
        }

    companion object {
        private val PAGE_URL = Regex("""https?://(?:www\.|m\.)?douyin\.com/(?:video|note)/\d+""")
        private val SHORT_URL = Regex("""https?://(?:v\.douyin\.com|v\.iesdouyin\.com)/[A-Za-z0-9_-]+/?""")
    }
}
