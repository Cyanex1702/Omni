package com.omniplayer.app.download

import java.util.ArrayDeque
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Limits yt-dlp/FFmpeg work without representing unrelated downloads as WorkManager prerequisites.
 * The gate is process-local; WorkManager restarts stopped workers after process recreation and they
 * acquire a fresh permit before starting native work.
 */
internal object DownloadConcurrencyGate {
    private val mutex = Mutex()
    private val waiters = ArrayDeque<Waiter>()
    private var active = 0

    suspend fun <T> withPermit(limit: Int, block: suspend () -> T): T {
        val waiter = acquire(limit.coerceIn(1, 3))
        try {
            return block()
        } finally {
            release(waiter)
        }
    }

    private suspend fun acquire(limit: Int): Waiter {
        val waiter = Waiter(limit)
        val grantedImmediately = mutex.withLock {
            if (waiters.isEmpty() && active < limit) {
                active++
                waiter.granted = true
                true
            } else {
                waiters.addLast(waiter)
                false
            }
        }
        if (!grantedImmediately) {
            try {
                waiter.signal.await()
            } catch (error: Throwable) {
                mutex.withLock {
                    if (!waiters.remove(waiter) && waiter.granted) {
                        active = (active - 1).coerceAtLeast(0)
                        grantNextLocked()
                    }
                }
                throw error
            }
        }
        return waiter
    }

    private suspend fun release(waiter: Waiter) {
        mutex.withLock {
            if (!waiter.granted) return@withLock
            waiter.granted = false
            active = (active - 1).coerceAtLeast(0)
            grantNextLocked()
        }
    }

    private fun grantNextLocked() {
        val next = waiters.firstOrNull() ?: return
        if (active >= next.limit) return
        waiters.removeFirst()
        active++
        next.granted = true
        next.signal.complete(Unit)
    }

    private data class Waiter(
        val limit: Int,
        val signal: CompletableDeferred<Unit> = CompletableDeferred(),
        var granted: Boolean = false,
    )
}
