package com.github.purofle.remakebot.parser.bilibili

import com.github.purofle.remakebot.network.HttpRequest
import io.github.oshai.kotlinlogging.KotlinLogging


/**
 * Parse BiliBili Video
 * @param videoId the video's identifier.
 */
class BiliBiliParser(val videoId: VideoId) {

    suspend fun getVideoInfo(): String {
        val videoInfo = BiliBiliAPI.getVideoInfo(videoId)

        return videoInfo.toString() + BiliBiliAPI.getPlayUrl(videoId, videoInfo.data.cid)
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