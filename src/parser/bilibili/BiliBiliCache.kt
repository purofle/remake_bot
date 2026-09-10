package com.github.purofle.remakebot.parser.bilibili

import com.github.purofle.remakebot.data.bilibili.CachedContent
import com.github.purofle.remakebot.data.bilibili.WbiImg
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlin.io.path.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.Clock


object BiliBiliCache {
    val json = Json

    var content: CachedContent? = readCache()

    fun isCacheValid(): Boolean = content?.let {
        val zone = TimeZone.currentSystemDefault()
        return it.createdAt.toLocalDateTime(zone) == Clock.System.now().toLocalDateTime(zone).date
    } ?: false

    fun updateCache(wbiImg: WbiImg) {
        content = CachedContent(
            createdAt = Clock.System.now(),
            wbiImg = wbiImg,
        ).also {
            Path(CACHE_FILE).writeText(json.encodeToString(it))
        }
    }

    fun readCache(): CachedContent? {
        return if (!Path(CACHE_FILE).isRegularFile()) {
            null
        } else {
            json.decodeFromString(Path(CACHE_FILE).readText())
        }
    }

    const val CACHE_FILE = "bilibili_cache.json"
}