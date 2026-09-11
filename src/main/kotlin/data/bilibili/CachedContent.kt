package com.github.purofle.remakebot.data.bilibili

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class CachedContent(
    @SerialName("created_at") val createdAt: Instant,
    @SerialName("wbi_img") val wbiImg: WbiImg,
)
