package com.github.purofle.remakebot.parser.bilibili

import com.github.purofle.remakebot.data.bilibili.CachedContent
import com.github.purofle.remakebot.data.bilibili.WbiImg
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlin.io.path.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.Clock


object BiliBiliCache {
    val json = Json {
        ignoreUnknownKeys = true
    }

    private val logger = KotlinLogging.logger("BiliBiliCache")

    /**
     * Guards the read-modify-write of [content] and its [persist], so a WBI refresh cannot write
     * back a stale snapshot.
     */
    private val mutex = Mutex()

    @Volatile
    var content: CachedContent? = readCache()

    fun isCacheValid(): Boolean = content?.let {
        val zone = TimeZone.currentSystemDefault()
        return it.createdAt.toLocalDateTime(zone) == Clock.System.now().toLocalDateTime(zone).date
    } ?: false

    suspend fun updateCache(wbiImg: WbiImg) {
        mutex.withLock {
            val now = Clock.System.now()
            val updated = CachedContent(createdAt = now, wbiImg = wbiImg)
            content = updated
            persist(updated)
        }
    }

    private fun readCache(): CachedContent? {
        val file = Path(CACHE_FILE)
        if (!file.isRegularFile()) return null

        return runCatching {
            json.decodeFromString<CachedContent>(file.readText())
        }.getOrElse {
            logger.warn(it) { "Cannot decode $CACHE_FILE, deleting it" }
            file.deleteIfExists()
            null
        }
    }

    private suspend fun persist(value: CachedContent) = withContext(Dispatchers.IO) {
        Path(CACHE_FILE).writeText(json.encodeToString(value))
    }

    const val CACHE_FILE = "bilibili_cache.json"
}
