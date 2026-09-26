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
import org.drinkless.tdlib.TdApi.*
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
                    td.sendVideoWithRemoteIds(cached.videoRemoteId, cached.coverRemoteId, message.chatId, caption)
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

            val remoteIds = td.extractVideoRemoteIds(sentMessage)
            if (remoteIds == null) {
                logger.debug { "No reusable remote file ids for $cacheKey, not caching it" }
            } else {
                BiliBiliCache.putVideo(cacheKey, CachedVideo(remoteIds.videoRemoteId, remoteIds.coverRemoteId))
            }
        }
    }


    companion object {
        private val VIDEO_ID_REGEX = Regex("""(?:av|AV|aV|Av)\d+|BV\w+""")

        private val B23_URL_REGEX = Regex("""https?://b23\.tv/\w+""")

        private val logger = KotlinLogging.logger("BiliBiliParser")

        /** Telegram captions are limited to 1024 characters. */
        private const val MAX_CAPTION_LENGTH = 1024

        /**
         * Striped locks so requests for the same video are handled one at a time, without keeping a
         * lock per video around forever.
         */
        private val videoLocks = List(64) { Mutex() }

        private fun lockFor(cacheKey: String) =
            videoLocks[(cacheKey.hashCode() and 0x7fffffff) % videoLocks.size]

        /**
         * Builds the caption out of plain text plus explicit [TextEntity]s rather than Markdown:
         * video titles are full of `[`, `_` and `*`, which a Markdown parser would reject or eat.
         * Entity offsets are derived as the rows are appended, so reordering rows cannot skew them.
         */
        private fun buildCaption(info: VideoInfo): FormattedText {
            val owner = "@${info.owner.name}"
            val stats = "播放量：${info.stat.view} 弹幕：${info.stat.danmaku} 评论：${info.stat.reply}"

            // Telegram caps captions at 1024 characters; give the description whatever is left.
            val budget = (MAX_CAPTION_LENGTH - info.title.length - owner.length - stats.length - 3).coerceAtLeast(0)
            val description = info.description.trim().take(budget)

            val rows = buildList {
                add(info.title to TextEntityTypeTextUrl("https://www.bilibili.com/video/${info.bvid}"))
                if (description.isNotEmpty()) add(description to TextEntityTypeExpandableBlockQuote())
                add(owner to TextEntityTypeTextUrl("https://space.bilibili.com/${info.owner.mid}"))
                add(stats to TextEntityTypeBlockQuote())
            }

            val entities = mutableListOf<TextEntity>()
            val text = StringBuilder()
            rows.forEachIndexed { index, (line, type) ->
                entities += TextEntity(text.length, line.length, type)
                text.append(line)
                if (index != rows.lastIndex) text.append('\n')
            }

            return FormattedText(text.toString(), entities.toTypedArray())
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