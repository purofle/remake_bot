package com.github.purofle.remakebot.data.bilibili

import com.github.purofle.remakebot.data.serializer.InstantSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class BiliBiliResponse<T>(
    val code: Int,
    val data: T
)

@Serializable
data class NavData(
    @SerialName("wbi_img") val wbiImg: WbiImg,
)

@Serializable
data class WbiImg(
    @SerialName("img_url") val imgUrl: String,
    @SerialName("sub_url") val subUrl: String,
)

@Serializable
data class VideoInfo(
    val bvid: String,
    val title: String,
    @SerialName("desc") val description: String,
    val owner: Owner,
    val stat: VideoStat,

    @Serializable(with = InstantSerializer::class)
    @SerialName("pubdate") val publishDate: Instant,

    @Serializable(with = InstantSerializer::class)
    @SerialName("ctime") val createTime: Instant,

    val cid: Long,
)

@Serializable
data class VideoStat(
    val view: Long,
    val danmaku: Long,
    val reply: Long,
    val like: Long,
    val coin: Long,
    val favorite: Long,
    val share: Long,
)

@Serializable
data class Owner(
    val mid: Long,
    val name: String,
)