package com.github.purofle.remakebot.data.bilibili

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class CachedContent(
    @SerialName("created_at") val createdAt: Instant,
    @SerialName("wbi_img") val wbiImg: WbiImg,
    @SerialName("videos") val videos: Map<String, CachedVideo> = emptyMap(),
)

/**
 * Telegram remote file ids of an uploaded video, keyed by "bvid_cid".
 */
@Serializable
data class CachedVideo(
    @SerialName("video_remote_id") val videoRemoteId: String,
    @SerialName("cover_remote_id") val coverRemoteId: String,
)
