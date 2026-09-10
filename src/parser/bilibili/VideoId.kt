package com.github.purofle.remakebot.parser.bilibili

sealed interface VideoId {
    data class Aid(val value: Long) : VideoId
    data class Bvid(val value: String) : VideoId
}