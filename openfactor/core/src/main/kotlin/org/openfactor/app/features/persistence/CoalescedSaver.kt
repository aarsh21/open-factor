package org.openfactor.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Serializes snapshot writes. [saveNow] is durable before it returns. [saveLater] coalesces a burst
 * of edits into one background write of the newest value, at most [delayMillis] after the first
 * edit of the burst. Every write stores the newest accepted value, so an older queued value can
 * never overwrite a newer synchronous save.
 */
class CoalescedSaver<T : Any>(
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
    private val _state = MutableStateFlow<PersistenceState>(PersistenceState.Saved)
    val state = _state.asStateFlow()

    val hasPendingWrite: Boolean get() = synchronized(lock) { savedVersion != version }

    /** A failed write leaves state unchanged and propagates, exactly like a direct save. */
    fun saveNow(value: T) {
        synchronized(lock) {
            try {
                write(value)
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                _state.value = PersistenceState.Failed(savedVersion != version)
                throw (failure as? PersistenceWriteException ?: PersistenceWriteException(failure))
            }
            latest = value
            savedVersion = ++version
            _state.value = PersistenceState.Saved
        }
    }

    fun saveLater(value: T) {
        synchronized(lock) {
            latest = value
            version++
            _state.value = if (_state.value is PersistenceState.Failed) PersistenceState.Failed(true) else PersistenceState.Pending
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
    fun flushQuietly(): Boolean = runCatching { flush() }.onFailure(onDeferredFailure).isSuccess

    /** Only an explicit acknowledgment may hide a rejected action; pending accepted edits stay visible. */
    fun dismissFailure(): Boolean = synchronized(lock) {
        if (_state.value != PersistenceState.Failed(false)) return@synchronized false
        _state.value = PersistenceState.Saved
        true
    }

    fun flush() {
        synchronized(lock) {
            if (savedVersion == version) return
            val target = version
            try {
                write(requireNotNull(latest))
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                _state.value = PersistenceState.Failed(true)
                throw (failure as? PersistenceWriteException ?: PersistenceWriteException(failure))
            }
            savedVersion = target
            _state.value = PersistenceState.Saved
        }
    }
}

/** How long typed set values may trail on a worker before they are written. */
const val TYPED_SAVE_DELAY_MS = 250L
