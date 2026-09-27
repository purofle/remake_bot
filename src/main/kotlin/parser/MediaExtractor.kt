package com.github.purofle.remakebot.parser

import com.github.purofle.remakebot.network.HttpRequest
import java.io.File

interface TextMediaParser {
    val text: String

    fun extractUrlOrNull(): String?

    fun supports(): Boolean
    suspend fun parse(): MediaResource

    /**
     * Downloads the file of a [video] this parser returned into [target]. Override it for a CDN that
     * wants more than a plain GET, e.g. a Referer.
     */
    suspend fun downloadVideo(video: MediaResource.Video, target: File) =
        HttpRequest.download(video.videoUrl, target)
}