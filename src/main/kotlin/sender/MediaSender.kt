package com.github.purofle.remakebot.sender

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.purofle.remakebot.cache.VideoFileIdCache
import com.github.purofle.remakebot.data.cache.CachedVideo
import com.github.purofle.remakebot.network.HttpRequest
import com.github.purofle.remakebot.parser.MediaPlatform
import com.github.purofle.remakebot.parser.MediaResource
import com.github.purofle.remakebot.parser.TextMediaParser
import com.github.purofle.remakebot.tdlib.TdLibBot
import com.github.purofle.remakebot.text.toBotApiEntities
import com.github.purofle.remakebot.utils.CacheLocks
import com.github.purofle.remakebot.utils.executeAwait
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient
import org.telegram.telegrambots.meta.api.methods.send.SendChatAction
import org.telegram.telegrambots.meta.api.methods.send.SendLivePhoto
import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.ReplyParameters
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto
import org.telegram.telegrambots.meta.api.objects.message.Message
import java.io.File

/** Telegram groups at most this many photos into one album. */
private const val MAX_ALBUM_SIZE = 10

/**
 * Sends a parsed [MediaResource] back into the chat, reusing the cached remote file ids of an
 * earlier upload when there are any.
 */
class MediaSender(
    private val td: TdLibBot,
    private val telegramClient: OkHttpTelegramClient,
    private val botToken: String,
) {

    suspend fun send(parser: TextMediaParser, message: Message) = when (val resource = parser.parse()) {
        is MediaResource.Video -> sendVideo(parser, resource, message)
        is MediaResource.Photos -> sendPhotos(resource, message)
    }

    /**
     * Photos are well within the Bot API upload limit, so unlike videos they go through it rather
     * than TDLib. They are small enough to always download and upload again, so nothing is cached.
     */
    private suspend fun sendPhotos(photos: MediaResource.Photos, message: Message) {
        telegramClient.executeAwait(SendChatAction.builder().chatId(message.chatId).action("upload_photo").build())

        logger.info { "Downloading ${photos.photos.size} ${photos.platform} photos: ${photos.title}" }

        if (photos.photos.any { it.liveVideoUrl != null }) {
            photos.photos.chunked(MAX_ALBUM_SIZE).forEachIndexed { chunkIndex, chunk ->
                val images = coroutineScope {
                    chunk.map { photo ->
                        async {
                            HttpRequest.getAsByteArray(photo.url) to
                                photo.liveVideoUrl?.let { HttpRequest.getAsByteArray(it) }
                        }
                    }.awaitAll()
                }
                if (chunk.size == 1) {
                    val (image, liveVideo) = images.single()
                    withContext(Dispatchers.IO) {
                        if (liveVideo != null) {
                            telegramClient.execute(SendLivePhoto.builder().apply {
                                chatId(message.chatId)
                                livePhoto(InputFile(liveVideo.inputStream(), "live.mp4"))
                                photo(InputFile(image.inputStream(), "photo.jpg"))
                                if (chunkIndex == 0) {
                                    caption(photos.caption.text)
                                    captionEntities(photos.caption.toBotApiEntities())
                                }
                                replyParameters(ReplyParameters.builder().messageId(message.messageId).build())
                            }.build())
                        } else {
                            telegramClient.execute(SendPhoto.builder().apply {
                                chatId(message.chatId)
                                photo(InputFile(image.inputStream(), "photo.jpg"))
                                if (chunkIndex == 0) {
                                    caption(photos.caption.text)
                                    captionEntities(photos.caption.toBotApiEntities())
                                }
                                replyToMessageId(message.messageId)
                            }.build())
                        }
                    }
                } else {
                    sendLiveAlbum(images, chunkIndex == 0, photos, message)
                }
            }
            return
        }

        val images = coroutineScope {
            photos.photos.map { async { HttpRequest.getAsByteArray(it.url) } }.awaitAll()
        }
        val captionText = photos.caption.text
        val captionEntities = photos.caption.toBotApiEntities()

        // An album takes 2 to 10 photos, a lone photo (also one left over after full albums) is sent on its own.
        images.chunked(MAX_ALBUM_SIZE).forEachIndexed { chunkIndex, chunk ->
            val caption = if (chunkIndex == 0) captionText else null
            val entities = if (chunkIndex == 0) captionEntities else null

            withContext(Dispatchers.IO) {
                if (chunk.size == 1) {
                    telegramClient.execute(SendPhoto.builder().apply {
                        chatId(message.chatId)
                        photo(InputFile(chunk.single().inputStream(), "photo.jpg"))
                        caption?.let { caption(it) }
                        entities?.let { captionEntities(it) }
                        replyToMessageId(message.messageId)
                    }.build())
                } else {
                    val medias = chunk.mapIndexed { index, bytes ->
                        InputMediaPhoto(bytes.inputStream(), "photo$index.jpg").apply {
                            if (index == 0) {
                                this.caption = caption
                                this.captionEntities = entities
                            }
                        }
                    }
                    telegramClient.execute(SendMediaGroup.builder().apply {
                        chatId(message.chatId)
                        medias(medias)
                        replyToMessageId(message.messageId)
                    }.build())
                }
            }
        }
    }

    /** TelegramBots 10.3 cannot attach the static photo of InputMediaLivePhoto in an album. */
    private suspend fun sendLiveAlbum(
        images: List<Pair<ByteArray, ByteArray?>>,
        firstChunk: Boolean,
        photos: MediaResource.Photos,
        message: Message,
    ) = withContext(Dispatchers.IO) {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", message.chatId.toString())
            .addFormDataPart("reply_to_message_id", message.messageId.toString())
        val media = images.mapIndexed { index, (image, liveVideo) ->
            val entry = linkedMapOf<String, Any>(
                "type" to if (liveVideo != null) "live_photo" else "photo",
                "media" to "attach://media$index",
            )
            if (liveVideo != null) {
                entry["photo"] = "attach://photo$index"
                body.addFormDataPart("media$index", "live$index.mp4", liveVideo.toRequestBody("video/mp4".toMediaType()))
                body.addFormDataPart("photo$index", "photo$index.jpg", image.toRequestBody("image/jpeg".toMediaType()))
            } else {
                body.addFormDataPart("media$index", "photo$index.jpg", image.toRequestBody("image/jpeg".toMediaType()))
            }
            if (firstChunk && index == 0) {
                entry["caption"] = photos.caption.text
                entry["caption_entities"] = photos.caption.toBotApiEntities()
            }
            entry
        }
        body.addFormDataPart("media", ObjectMapper().writeValueAsString(media))

        val request = Request.Builder()
            .url("https://api.telegram.org/bot$botToken/sendMediaGroup")
            .post(body.build())
            .build()
        HttpRequest.client.newCall(request).execute().use { response ->
            check(response.isSuccessful) {
                "Telegram Live Photo 相册发送失败 (HTTP ${response.code}): ${response.body.string().take(200)}"
            }
        }
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
                    td.sendVideoWithRemoteIds(cached.videoRemoteId, cached.coverRemoteId, message.chatId, message.messageId, caption, cached.duration)
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
                td.uploadVideoWithMessage(videoFile, cover, message.chatId, message.messageId, caption, video.duration)
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
                MediaPlatform.Douyin -> "douyin"
            }

            return "$prefix:${resource.id}"
        }
    }
}
