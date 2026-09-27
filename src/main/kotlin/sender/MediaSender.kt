package com.github.purofle.remakebot.sender

import com.github.purofle.remakebot.cache.VideoFileIdCache
import com.github.purofle.remakebot.data.cache.CachedVideo
import com.github.purofle.remakebot.network.HttpRequest
import com.github.purofle.remakebot.parser.MediaPlatform
import com.github.purofle.remakebot.parser.MediaResource
import com.github.purofle.remakebot.parser.TextMediaParser
import com.github.purofle.remakebot.tdlib.TdLibBot
import com.github.purofle.remakebot.utils.CacheLocks
import com.github.purofle.remakebot.utils.executeAwait
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient
import org.telegram.telegrambots.meta.api.methods.send.SendChatAction
import org.telegram.telegrambots.meta.api.objects.message.Message
import java.io.File

/**
 * Sends a parsed [MediaResource] back into the chat, reusing the cached remote file ids of an
 * earlier upload when there are any.
 */
class MediaSender(
    private val td: TdLibBot,
    private val telegramClient: OkHttpTelegramClient,
) {

    suspend fun send(parser: TextMediaParser, message: Message) = when (val resource = parser.parse()) {
        is MediaResource.Video -> sendVideo(parser, resource, message)
    }

    private suspend fun sendVideo(parser: TextMediaParser, video: MediaResource.Video, message: Message) {
        val cacheKey = cacheKeyOf(video)
        val caption = video.caption

        // Serialize requests for the same video, so a concurrent second request finds the file ids
        // cached by the first instead of downloading and uploading the same video again.
        CacheLocks.forKey(cacheKey).withLock {
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

            val cover = HttpRequest.getAsByteArray(video.coverUrl)

            logger.info { "Downloading ${video.platform} video: ${video.title}, url: ${video.videoUrl}" }

            val videoFile = File.createTempFile(video.platform.name.lowercase(), ".mp4")
            val sentMessage = try {
                parser.downloadVideo(video, videoFile)
                td.uploadVideoWithMessage(videoFile, cover, message.chatId, caption, video.duration)
            } finally {
                videoFile.delete()
            }

            val remoteIds = td.extractVideoRemoteIds(sentMessage)
            if (remoteIds == null) {
                logger.debug { "No reusable remote file ids for $cacheKey, not caching it" }
            } else {
                VideoFileIdCache.put(cacheKey, CachedVideo(remoteIds.videoRemoteId, remoteIds.coverRemoteId, video.duration))
            }
        }
    }

    companion object {
        private val logger = KotlinLogging.logger("MediaSender")

        /**
         * Namespaced by platform, since ids of different platforms may collide and a collision would
         * send the wrong video. The prefixes match the keys already in the cache file.
         */
        private fun cacheKeyOf(resource: MediaResource): String {
            val prefix = when (resource.platform) {
                MediaPlatform.BiliBili -> "bili"
                MediaPlatform.RedNote -> "xhs"
            }

            return "$prefix:${resource.id}"
        }
    }
}
