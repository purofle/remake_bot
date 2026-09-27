package com.github.purofle.remakebot.data.cache

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Telegram remote file ids of an already uploaded video, reusable across parsers. The key is
 * namespaced by source (e.g. `bili:BV...:cid`, `xhs:<noteId>`).
 */
@Serializable
data class CachedVideo(
    @SerialName("video_remote_id") val videoRemoteId: String,
    @SerialName("cover_remote_id") val coverRemoteId: String,
    val duration: Int,
)

/**
 * Persisted shape of the remote file id cache.
 */
@Serializable
data class CachedVideos(
    @SerialName("videos") val videos: Map<String, CachedVideo> = emptyMap(),
)
