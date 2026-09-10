package com.github.purofle.remakebot.parser.bilibili

import com.github.purofle.remakebot.data.bilibili.BiliBiliResponse
import com.github.purofle.remakebot.data.bilibili.VideoInfo
import com.github.purofle.remakebot.network.HttpRequest
import io.github.oshai.kotlinlogging.KotlinLogging


/**
 * Parse BiliBili Video
 * @param videoId the video's identifier.
 */
class BiliBiliParser(val videoId: VideoId) {

    typealias VideoInfoResponse = BiliBiliResponse<VideoInfo>

    sealed interface VideoId {
        data class Aid(val value: Long) : VideoId
        data class Bvid(val value: String) : VideoId
    }

    suspend fun getVideoInfo(): VideoInfoResponse {
        val params = when (videoId) {
            is VideoId.Aid -> mapOf("aid" to videoId.value)
            is VideoId.Bvid -> mapOf("bvid" to videoId.value)
        }

        val req = HttpRequest.get<VideoInfoResponse>(GET_VIDEO_INFO, params)

        return req
    }

    companion object {
        private const val GET_VIDEO_INFO = "https://api.bilibili.com/x/web-interface/view"

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