package com.github.purofle.remakebot.cache

import com.github.purofle.remakebot.data.cache.CachedVideo
import com.github.purofle.remakebot.data.cache.CachedVideos
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * How many consecutive reuse failures a key may accumulate before the cache is bypassed for it.
 * Note this counter is only reset by a *successful reuse* ([reportCacheSuccess]), deliberately not
 * by [put]: a failed reuse always falls through to a fresh upload followed by a [put], so resetting
 * there would clear the counter on every cycle and the breaker would never open.
 */
private const val MAX_CACHE_FAILURES = 3

/**
 * Maps a namespaced key to the Telegram remote file ids of an already uploaded video, so a repeated
 * request can skip both the download and the upload. Shared by every parser; keys must be
 * namespaced by source (`bili:...`, `xhs:...`) because a key collision would send the wrong video.
 */
object VideoFileIdCache {
    const val CACHE_FILE = "video_cache.json"

    private const val TMP_FILE = "$CACHE_FILE.tmp"

    private val json = Json {
        ignoreUnknownKeys = true
    }

    private val logger = KotlinLogging.logger("VideoFileIdCache")

    /**
     * Guards the read-modify-write of [content] and its [persist], so concurrent writers can neither
     * drop each other's entries nor write back a stale snapshot.
     */
    private val mutex = Mutex()

    /**
     * Never null, and empty when there is no cache file yet: on a fresh deployment every [put] still
     * has to land. A nullable map here would silently swallow every write.
     */
    @Volatile
    private var content: Map<String, CachedVideo> = readCache()

    /** Consecutive reuse failures per key, in-memory only (reset on restart). */
    private val failures = ConcurrentHashMap<String, Int>()

    fun get(key: String): CachedVideo? =
        if (isDisabled(key)) null else content[key]

    suspend fun put(key: String, video: CachedVideo) {
        if (isDisabled(key)) return

        mutex.withLock {
            val updated = content + (key to video)
            content = updated
            persist(updated)
        }
    }

    suspend fun remove(key: String) {
        mutex.withLock {
            if (key !in content) return@withLock
            val updated = content - key
            content = updated
            persist(updated)
        }
    }

    /** Records that reusing the cached ids for [key] failed. */
    fun reportCacheFailure(key: String) {
        failures.merge(key, 1) { current, _ -> current + 1 }
    }

    /** Records that reusing the cached ids for [key] succeeded. */
    fun reportCacheSuccess(key: String) {
        failures.remove(key)
    }

    private fun isDisabled(key: String): Boolean =
        failures.getOrDefault(key, 0) >= MAX_CACHE_FAILURES

    private fun readCache(): Map<String, CachedVideo> {
        val file = Path(CACHE_FILE)
        if (!file.isRegularFile()) return emptyMap()

        return runCatching {
            json.decodeFromString<CachedVideos>(file.readText()).videos
        }.getOrElse {
            logger.warn(it) { "Cannot decode $CACHE_FILE, deleting it" }
            file.deleteIfExists()
            emptyMap()
        }
    }

    /** Writes through a temp file and an atomic move, so a crash cannot leave a half written file. */
    private suspend fun persist(videos: Map<String, CachedVideo>) = withContext(Dispatchers.IO) {
        val target = Path(CACHE_FILE)
        Path(TMP_FILE).writeText(json.encodeToString(CachedVideos(videos)))
        Files.move(Path(TMP_FILE), target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
