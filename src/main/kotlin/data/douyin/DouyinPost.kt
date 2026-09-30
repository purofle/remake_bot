package com.github.purofle.remakebot.data.douyin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DouyinPost(
    @SerialName("aweme_id") val id: String,
    val desc: String,
    @SerialName("item_title") val itemTitle: String? = null,
    val author: DouyinAuthor? = null,
    val video: DouyinVideo? = null,
    @SerialName("image_post_info") val imagePostInfo: DouyinImagePost? = null,
    val images: List<DouyinImage> = emptyList(),
)

@Serializable
data class DouyinAuthor(
    val nickname: String,
    @SerialName("sec_uid") val secUid: String? = null,
)

@Serializable
data class DouyinUrlList(
    @SerialName("url_list") val urlList: List<String>,
)

@Serializable
data class DouyinVideo(
    val duration: Long,
    @SerialName("play_addr") val playAddr: DouyinUrlList? = null,
    @SerialName("bit_rate") val bitRate: List<DouyinBitRate> = emptyList(),
    @SerialName("origin_cover") val originCover: DouyinUrlList? = null,
    val cover: DouyinUrlList? = null,
    @SerialName("dynamic_cover") val dynamicCover: DouyinUrlList? = null,
)

@Serializable
data class DouyinBitRate(
    @SerialName("play_addr") val playAddr: DouyinUrlList,
)

@Serializable
data class DouyinImagePost(
    val images: List<DouyinImage>,
)

@Serializable
data class DouyinImage(
    @SerialName("watermark_free_download_url_list") val watermarkFreeUrls: List<String> = emptyList(),
    @SerialName("origin_image") val originImage: DouyinUrlList? = null,
    @SerialName("display_image") val displayImage: DouyinUrlList? = null,
    @SerialName("download_addr") val downloadAddr: DouyinUrlList? = null,
    @SerialName("url_list") val urlList: List<String> = emptyList(),
)
