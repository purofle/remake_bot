package com.github.purofle.remakebot.parser.bilibili

import com.github.purofle.remakebot.data.bilibili.CachedContent
import com.github.purofle.remakebot.data.bilibili.CachedVideo
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
import java.util.concurrent.ConcurrentHashMap

/**
 * Consecutive reuse failures after which the cache is bypassed for that key. Without this, a file
 * id that can never be reused (as opposed to one that merely went stale) would make every single
 * request pay a failed send plus a full re-download, forever.
 */
private const val MAX_CACHE_FAILURES = 3


object BiliBiliCache {
    val json = Json {
        ignoreUnknownKeys = true
    }

    private val logger = KotlinLogging.logger("BiliBiliCache")

    /**
     * Guards the read-modify-write of [content] and its [persist] in [updateCache], [putVideo]
     * and [removeVideo], so they can neither drop each other's entries nor write back a stale
     * snapshot.
     */
    private val mutex = Mutex()

    @Volatile
    var content: CachedContent? = readCache()

    /** Consecutive reuse failures per cache key, in-memory only (reset on restart). */
    private val cacheFailures = ConcurrentHashMap<String, Int>()

    fun isCacheValid(): Boolean = content?.let {
        val zone = TimeZone.currentSystemDefault()
        return it.createdAt.toLocalDateTime(zone) == Clock.System.now().toLocalDateTime(zone).date
    } ?: false

    suspend fun updateCache(wbiImg: WbiImg) {
        mutex.withLock {
            val now = Clock.System.now()
            val updated = (content ?: CachedContent(now, wbiImg)).copy(
                createdAt = now,
                wbiImg = wbiImg,
            )
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

    fun getVideo(key: String): CachedVideo? =
        if (isDisabled(key)) null else content?.videos?.get(key)

    suspend fun putVideo(key: String, video: CachedVideo) {
        if (isDisabled(key)) return

        mutex.withLock {
            val current = content ?: return@withLock
            val updated = current.copy(videos = current.videos + (key to video))
            content = updated
            persist(updated)
        }
    }

    /** Records that reusing the cached file ids for [key] failed. */
    fun reportCacheFailure(key: String) {
        cacheFailures.merge(key, 1) { current, _ -> current + 1 }
    }

    /** Records that reusing the cached file ids for [key] succeeded. */
    fun reportCacheSuccess(key: String) {
        cacheFailures.remove(key)
    }

    private fun isDisabled(key: String): Boolean =
        cacheFailures.getOrDefault(key, 0) >= MAX_CACHE_FAILURES

    suspend fun removeVideo(key: String) {
        mutex.withLock {
            val current = content ?: return@withLock
            if (key !in current.videos) return@withLock
            val updated = current.copy(videos = current.videos - key)
            content = updated
            persist(updated)
        }
    }

    private suspend fun persist(value: CachedContent) = withContext(Dispatchers.IO) {
        Path(CACHE_FILE).writeText(json.encodeToString(value))
    }

    const val CACHE_FILE = "bilibili_cache.json"
}