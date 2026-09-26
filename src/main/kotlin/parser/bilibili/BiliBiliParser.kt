package com.github.purofle.remakebot.parser.bilibili

import com.github.purofle.remakebot.data.bilibili.CachedVideo
import com.github.purofle.remakebot.data.bilibili.VideoInfo
import com.github.purofle.remakebot.network.HttpRequest
import com.github.purofle.remakebot.tdlib.TdLibBot
import com.github.purofle.remakebot.utils.executeAwait
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
        val cacheKey = "${videoInfo.bvid}_${videoInfo.cid}"

        // Serialize requests for the same video, so a concurrent second request finds the file ids
        // cached by the first instead of downloading and uploading the same video again.
        lockFor(cacheKey).withLock {
            val caption = buildCaption(videoInfo)

            telegramClient.executeAwait(SendChatAction.builder().chatId(message.chatId).action("upload_video").build())

            val cached = BiliBiliCache.getVideo(cacheKey)
            if (cached != null) {
                logger.info { "Reusing cached video file for $cacheKey" }
                try {
                    td.sendVideoWithFileIds(cached.videoFileId, cached.coverFileId, message.chatId, caption)
                    BiliBiliCache.reportCacheSuccess(cacheKey)
                    return
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.warn(e) { "Cached video file for $cacheKey is no longer valid, re-uploading" }
                    BiliBiliCache.reportCacheFailure(cacheKey)
                    BiliBiliCache.removeVideo(cacheKey)
                }
            }

            val playUrl = BiliBiliAPI.getPlayUrl(videoId, videoInfo.cid).data.durl.first()
            val picture = HttpRequest.getAsByteArray(videoInfo.picture)

            logger.info { "Downloading video: ${videoInfo.title}, size: ${playUrl.size / 1024 / 1024} MB, url: ${playUrl.url}" }

            val videoFile = File.createTempFile("bilibili", ".mp4")
            val sentMessage = try {
                HttpRequest.downloadVideo(playUrl.url, videoFile)
                td.uploadVideoWithMessage(videoFile, picture, message.chatId, caption)
            } finally {
                videoFile.delete()
            }

            val fileIds = td.extractVideoFileIds(sentMessage)
            if (fileIds == null) {
                logger.debug { "No reusable file ids for $cacheKey, not caching it" }
            } else {
                BiliBiliCache.putVideo(cacheKey, CachedVideo(fileIds.videoFileId, fileIds.coverFileId))
            }
        }
    }


    companion object {
        private val VIDEO_ID_REGEX = Regex("""(?:av|AV|aV|Av)\d+|BV\w+""")

        private val B23_URL_REGEX = Regex("""https?://b23\.tv/\w+""")

        private val logger = KotlinLogging.logger("BiliBiliParser")

        /** Telegram captions are limited to 1024 characters. */
        private const val MAX_CAPTION_LENGTH = 1024
        private const val MAX_DESCRIPTION_LENGTH = 600

        /**
         * Striped locks so requests for the same video are handled one at a time, without keeping a
         * lock per video around forever.
         */
        private val videoLocks = List(64) { Mutex() }

        private fun lockFor(cacheKey: String) =
            videoLocks[(cacheKey.hashCode() and 0x7fffffff) % videoLocks.size]

        private fun buildCaption(info: VideoInfo): String = buildString {
            appendLine(info.title)
            appendLine("UP: ${info.owner.name}")
            val description = info.description.trim()
            if (description.isNotEmpty()) appendLine(description.take(MAX_DESCRIPTION_LENGTH))
            append("https://www.bilibili.com/video/${info.bvid}")
        }.take(MAX_CAPTION_LENGTH)

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