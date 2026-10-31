package org.openfactor.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Serializes snapshot writes. [saveNow] is durable before it returns. [saveLater] coalesces a burst
 * of edits into one background write of the newest value, at most [delayMillis] after the first
 * edit of the burst. Every write stores the newest accepted value, so an older queued value can
 * never overwrite a newer synchronous save.
 */
internal class CoalescedSaver<T : Any>(
    private val delayMillis: Long,
    private val scope: CoroutineScope,
    private val onDeferredFailure: (Throwable) -> Unit = {},
    private val write: (T) -> Unit,
) {
    private val lock = Any()
    private var latest: T? = null
    private var version = 0L
    private var savedVersion = 0L
    private var pending: Job? = null

    val hasPendingWrite: Boolean get() = synchronized(lock) { savedVersion != version }

    /** A failed write leaves state unchanged and propagates, exactly like a direct save. */
    fun saveNow(value: T) {
        synchronized(lock) {
            write(value)
            latest = value
            savedVersion = ++version
        }
    }

    fun saveLater(value: T) {
        synchronized(lock) {
            latest = value
            version++
            if (pending?.isActive == true) return
            pending = scope.launch {
                delay(delayMillis)
                // Edits arriving during this write schedule their own follow-up.
                synchronized(lock) { pending = null }
                // A failure stays pending; the next save or lifecycle flush retries with newer data.
                runCatching { flush() }.onFailure(onDeferredFailure)
            }
        }
    }

    /** Lifecycle flush: a failed write stays pending (reported like a deferred one) instead of throwing. */
    fun flushQuietly() { runCatching { flush() }.onFailure(onDeferredFailure) }

    fun flush() {
        synchronized(lock) {
            if (savedVersion == version) return
            val target = version
            write(requireNotNull(latest))
            savedVersion = target
        }
    }
}

/** How long typed set values may trail on a worker before they are written. */
internal const val TYPED_SAVE_DELAY_MS = 250L
