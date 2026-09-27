package com.github.purofle.remakebot.data.bilibili

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * The BiliBili specific cache: just the WBI signing token and when it was fetched. Remote file ids
 * of uploaded videos live in [com.github.purofle.remakebot.cache.VideoFileIdCache] instead, because
 * the two have nothing in common and very different lifetimes.
 */
@Serializable
data class CachedContent(
    @SerialName("created_at") val createdAt: Instant,
    @SerialName("wbi_img") val wbiImg: WbiImg,
)
