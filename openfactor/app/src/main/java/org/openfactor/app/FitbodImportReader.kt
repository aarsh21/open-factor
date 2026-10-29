package org.openfactor.app

import java.io.InputStream
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

/** Provider I/O can ignore thread interruption. Cancel the provider and close its owned stream too. */
internal suspend fun readFitbodInput(
    timeoutMillis: Long = 30_000,
    cancelOpen: () -> Unit = {},
    open: () -> InputStream,
): FitbodPreview = withTimeout(timeoutMillis) {
    suspendCancellableCoroutine { continuation ->
        val active = AtomicReference<InputStream?>(null)
        continuation.invokeOnCancellation {
            // Do not make the UI's cancellation wait for a provider's blocking close/cancel call.
            Dispatchers.IO.dispatch(EmptyCoroutineContext, Runnable {
                runCatching(cancelOpen)
                runCatching { active.getAndSet(null)?.close() }
            })
        }
        Dispatchers.IO.dispatch(continuation.context, Runnable {
            if (!continuation.isActive) return@Runnable
            try {
                val parsed = open().use { input ->
                    active.set(input)
                    if (!continuation.isActive) return@Runnable
                    FitbodImport.read(input)
                }
                if (continuation.isActive) continuation.resume(parsed)
            } catch (failure: Exception) {
                if (continuation.isActive) continuation.resumeWithException(failure)
            } finally { active.set(null) }
        })
    }
}
