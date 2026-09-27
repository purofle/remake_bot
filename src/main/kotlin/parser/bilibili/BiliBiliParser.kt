package com.github.purofle.remakebot.parser.bilibili

import com.github.purofle.remakebot.cache.VideoFileIdCache
import com.github.purofle.remakebot.data.bilibili.VideoInfo
import com.github.purofle.remakebot.data.cache.CachedVideo
import com.github.purofle.remakebot.network.HttpRequest
import com.github.purofle.remakebot.tdlib.TdLibBot
import com.github.purofle.remakebot.text.formattedText
import com.github.purofle.remakebot.utils.CacheLocks
import com.github.purofle.remakebot.utils.executeAwait
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock
import org.drinkless.tdlib.TdApi.FormattedText
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient
import org.telegram.telegrambots.meta.api.methods.send.SendChatAction
import org.telegram.telegrambots.meta.api.objects.message.Message
import java.io.File


/**
 * Parse BiliBili Video
 * @param videoId the video's identifier.
 */
class BiliBiliParser(val videoId: VideoId) {

    suspend fun sendVideoCard(td: TdLibBot, message: Message, telegramClient: OkHttpTelegramClient) {
        val videoInfo = BiliBiliAPI.getVideoInfo(videoId).data
        val cacheKey = "bili:${videoInfo.bvid}:${videoInfo.cid}"

        // Serialize requests for the same video, so a concurrent second request finds the file ids
        // cached by the first instead of downloading and uploading the same video again.
        CacheLocks.forKey(cacheKey).withLock {
            val caption = buildCaption(videoInfo)

            telegramClient.executeAwait(SendChatAction.builder().chatId(message.chatId).action("upload_video").build())

            val cached = VideoFileIdCache.get(cacheKey)
            if (cached != null) {
                logger.info { "Reusing cached video file for $cacheKey" }
                try {
                    td.sendVideoWithRemoteIds(cached.videoRemoteId, cached.coverRemoteId, message.chatId, caption, cached.duration)
                    VideoFileIdCache.reportCacheSuccess(cacheKey)
                    return
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.warn(e) { "Cached video file for $cacheKey is no longer valid, re-uploading" }
                    VideoFileIdCache.reportCacheFailure(cacheKey)
                    VideoFileIdCache.remove(cacheKey)
                }
            }

            val playUrl = BiliBiliAPI.getPlayUrl(videoId, videoInfo.cid).data.durl.first()
            val picture = HttpRequest.getAsByteArray(videoInfo.picture)

            logger.info { "Downloading video: ${videoInfo.title}, size: ${playUrl.size / 1024 / 1024} MB, url: ${playUrl.url}" }

            val videoFile = File.createTempFile("bilibili", ".mp4")
            val sentMessage = try {
                HttpRequest.downloadVideo(playUrl.url, videoFile)
                td.uploadVideoWithMessage(videoFile, picture, message.chatId, caption, videoInfo.duration)
            } finally {
                videoFile.delete()
            }

            val remoteIds = td.extractVideoRemoteIds(sentMessage)
            if (remoteIds == null) {
                logger.debug { "No reusable remote file ids for $cacheKey, not caching it" }
            } else {
                VideoFileIdCache.put(cacheKey, CachedVideo(remoteIds.videoRemoteId, remoteIds.coverRemoteId, videoInfo.duration))
            }
        }
    }


    companion object {
        private val VIDEO_ID_REGEX = Regex("""(?:av|AV|aV|Av)\d+|BV\w+""")

        private val B23_URL_REGEX = Regex("""https?://b23\.tv/\w+""")

        private val logger = KotlinLogging.logger("BiliBiliParser")

        /**
         * Builds the caption from video info
         * @param info [VideoInfo]
         **/
        private fun buildCaption(info: VideoInfo): FormattedText {

            val owner = "@${info.owner.name}"
            val stats = "播放量：${info.stat.view} 弹幕：${info.stat.danmaku} 评论：${info.stat.reply}"

            val description = info.description

            return formattedText {
                line { url(info.title, "https://www.bilibili.com/video/${info.bvid}") }
                line { expandableBlockQuote(description) }
                line {url(owner, "https://space.bilibili.com/${info.owner.mid}") }
                line { blockQuote(stats) }
            }
        }

        fun containsVideoId(text: String): Boolean =
            VIDEO_ID_REGEX.containsMatchIn(text)

        suspend fun getVideoIdFromShortUrl(
            text: String,
        ): VideoId {
            val url = B23_URL_REGEX.find(text)?.value
                ?: error("Invalid b23.tv URL: $text")

            return extractVideoId(HttpRequest.get<String>(url))
        }

        fun extractVideoId(text: String): VideoId {
            val match = VIDEO_ID_REGEX.find(text) ?: error("Invalid video id: $text")

            val id = match.value

            logger.debug { "Extracting video id: $id" }

            return if (id.startsWith("av", ignoreCase = true)) {
                VideoId.Aid(id.substring(2).toLong())
            } else {
                VideoId.Bvid(id)
            }
        }

        suspend fun extractVideoIdOrNull(text: String): VideoId? {
            return if (containsVideoId(text)) {
                extractVideoId(text)
            } else {
                if (!text.contains("b23.tv/")) {
                    null
                } else {
                    getVideoIdFromShortUrl(text)
                }
            }
        }
    }
}