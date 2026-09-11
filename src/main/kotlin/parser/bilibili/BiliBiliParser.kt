package com.github.purofle.remakebot.parser.bilibili

import com.github.purofle.remakebot.network.HttpRequest
import com.github.purofle.remakebot.tdlib.TdLibBot
import com.github.purofle.remakebot.utils.executeAwait
import io.github.oshai.kotlinlogging.KotlinLogging
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient
import org.telegram.telegrambots.meta.api.methods.send.SendChatAction
import org.telegram.telegrambots.meta.api.objects.message.Message


/**
 * Parse BiliBili Video
 * @param videoId the video's identifier.
 */
class BiliBiliParser(val videoId: VideoId) {

    data class VideoInfo(
        val videoInfoResponse: BiliBiliAPI.VideoInfoResponse,
        val playUrl: Durl,
    )

    suspend fun getVideoInfo(): VideoInfo {
        val videoInfo = BiliBiliAPI.getVideoInfo(videoId)

        return VideoInfo(
            videoInfo,
            BiliBiliAPI.getPlayUrl(videoId, videoInfo.data.cid).data.durl.first()
        )
    }

    suspend fun sendVideoCard(td: TdLibBot, message: Message, telegramClient: OkHttpTelegramClient) {
        val videoInfo = getVideoInfo()

        telegramClient.executeAwait(SendChatAction.builder().chatId(message.chatId).action("upload_video").build())
        logger.info { "Downloading video: ${videoInfo.videoInfoResponse.data.title}, size: ${videoInfo.playUrl.size / 1024 / 1024} MB" }
        val videoByteArray = HttpRequest.downloadVideo(videoInfo.playUrl.url)

        td.uploadVideoWithMessage(
            videoByteArray,
            message.chatId,
            videoInfo.videoInfoResponse.toString()
        )
    }


    companion object {
        private val VIDEO_ID_REGEX = Regex("""(?:av|AV|aV|Av)\d+|BV\w+""")

        private val B23_URL_REGEX = Regex("""https?://b23\.tv/\w+""")

        private val logger = KotlinLogging.logger("BiliBiliParser")

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