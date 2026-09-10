package com.github.purofle.remakebot.parser.bilibili

import kotlinx.serialization.Serializable

@Serializable
data class VideoUrl(
    val durl: List<Durl>
)

@Serializable
data class Durl(
    val length: Long,
    val size: Long,
    val url: String,
)