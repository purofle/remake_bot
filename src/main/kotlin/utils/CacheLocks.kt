package com.github.purofle.remakebot.utils

import kotlinx.coroutines.sync.Mutex

/**
 * Hands out a lock per key from a fixed pool, so callers can serialize work for the same key without
 * keeping one lock per key alive forever. Two different keys may share a stripe, which only costs a
 * little extra contention.
 */
object CacheLocks {
    private val locks = List(64) { Mutex() }

    fun forKey(key: String): Mutex = locks[(key.hashCode() and 0x7fffffff) % locks.size]
}
