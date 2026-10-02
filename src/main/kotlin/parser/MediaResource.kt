package com.github.purofle.remakebot.parser

import org.drinkless.tdlib.TdApi.FormattedText

enum class MediaPlatform {
    BiliBili,
    RedNote,
}

data class Author(
    val name: String,
    val url: String,
)

sealed interface MediaResource {
    /**
     * Identifies the resource within its [platform]. It has to stay the same across requests, so
     * it cannot be a share URL whose query string rotates.
     */
    val id: String
    val title: String
    val platform: MediaPlatform

    /** Null when the platform does not expose it, e.g. some Xiaohongshu note pages. */
    val author: Author?

    /** The page the resource was parsed from. */
    val url: String

    /**
     * What is sent along with the media. Each platform lays it out itself, and has to keep it
     * within Telegram's caption limit.
     */
    val caption: FormattedText

    /**
     * @param videoUrl a direct link to the video file.
     * @param duration in seconds, 0 when unknown.
     */
    data class Video(
        override val id: String,
        override val title: String,
        override val author: Author?,
        override val platform: MediaPlatform,
        override val url: String,
        override val caption: FormattedText,
        val videoUrl: String,
        val coverUrl: String,
        val duration: Int,
    ): MediaResource

    /**
     * @param photos photos in the order they are shown, with a video URL for each live photo.
     */
    data class Photos(
        override val id: String,
        override val title: String,
        override val author: Author?,
        override val platform: MediaPlatform,
        override val url: String,
        override val caption: FormattedText,
        val photos: List<Photo>,
    ): MediaResource

    data class Photo(
        val url: String,
        val liveVideoUrl: String? = null,
    )
}
