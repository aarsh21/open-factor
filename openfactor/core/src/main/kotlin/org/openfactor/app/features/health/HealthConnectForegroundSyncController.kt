package org.openfactor.app

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One automatic read per foreground visit, regardless of the previous snapshot's age. */
class HealthConnectForegroundGate {
    private var attemptedThisVisit = false

    fun begin(state: HealthConnectSyncState, enabled: Boolean): Boolean {
        if (!enabled || state.lastSyncedAtMillis == null || attemptedThisVisit) return false
        attemptedThisVisit = true
        return true
    }

    fun leaveForeground() {
        attemptedThisVisit = false
    }

    internal fun suppressCurrentVisit() {
        attemptedThisVisit = true
    }
}

/** Inclusive provider date window, all derived from the same injected clock instant and zone. */
data class HealthConnectAutomaticSyncRequest(
    val firstDate: LocalDate,
    val lastDate: LocalDate,
    val zone: ZoneId,
    val syncedAtMillis: Long,
    val expectedGeneration: Long = 0,
)

data class HealthConnectForegroundSyncStatus(
    val snapshot: HealthConnectSyncState,
    val foreground: Boolean = false,
    val enabled: Boolean = true,
    val isSyncing: Boolean = false,
)

/**
 * Read-only automatic synchronization, usable without Android or a rendering framework.
 *
 * Hosts forward visibility through [setForeground] and externally committed snapshots / panel
 * availability through [updateInputs]. A null [read] result represents an unsuccessful provider
 * read; it never clears the previous snapshot. Each read captures [generation] before dispatch.
 * [onCommit] must atomically compare that generation with the authoritative store and durably save
 * the complete result only if it still matches, returning true on acceptance. Returning false or
 * throwing retains the previous snapshot. Input observation is not the stale-write safety boundary:
 * an authoritative replacement can happen before the host forwards new inputs.
 *
 * Calls and the supplied scope must share one serialized owner context (for example Main in an
 * Android host). Only provider reads run on [readDispatcher]. The host owns the scope; [close]
 * cancels this controller's read, not that scope. A host can observe [state] independently of UI.
 */
class HealthConnectForegroundSyncController(
    private val scope: CoroutineScope,
    private val readDispatcher: CoroutineDispatcher,
    private val read: suspend (HealthConnectAutomaticSyncRequest) -> HealthConnectSyncData?,
    private val onCommit: (HealthConnectSyncData, expectedGeneration: Long) -> Boolean,
    private val generation: () -> Long = { 0 },
    private val clock: Clock = Clock.systemUTC(),
    private val zoneId: () -> ZoneId = ZoneId::systemDefault,
    initialState: HealthConnectSyncState = HealthConnectSyncState(),
    enabled: Boolean = true,
) : AutoCloseable {
    private val gate = HealthConnectForegroundGate()
    private val status = MutableStateFlow(HealthConnectForegroundSyncStatus(initialState, enabled = enabled))
    val state: StateFlow<HealthConnectForegroundSyncStatus> = status.asStateFlow()

    private var pendingRead: Job? = null
    private var activeRequest: Any? = null
    private var closed = false

    /**
     * Updating inputs never starts a transfer. In particular, closing an explicit Sync panel must
     * not immediately duplicate its transfer. A changed snapshot supersedes any in-flight read,
     * even when its timestamp is unchanged (for example, restoring an external snapshot).
     */
    fun updateInputs(snapshot: HealthConnectSyncState, enabled: Boolean = state.value.enabled) {
        if (closed) return
        val snapshotChanged = snapshot != status.value.snapshot
        if (snapshotChanged || !enabled) {
            cancelPendingRead()
            if (status.value.foreground) gate.suppressCurrentVisit()
        }
        status.value = status.value.copy(snapshot = snapshot, enabled = enabled)
    }

    /** Duplicate visibility notifications are idempotent; a real new visit permits one read. */
    fun setForeground(foreground: Boolean) {
        if (closed || foreground == status.value.foreground) return
        if (!foreground) {
            cancelPendingRead()
            gate.leaveForeground()
            status.value = status.value.copy(foreground = false)
            return
        }
        status.value = status.value.copy(foreground = true)
        if (!status.value.enabled) gate.suppressCurrentVisit()
        if (gate.begin(status.value.snapshot, status.value.enabled)) startRead()
    }

    override fun close() {
        if (closed) return
        closed = true
        cancelPendingRead()
        status.value = status.value.copy(foreground = false)
    }

    private fun cancelPendingRead() {
        // Invalidate before cancelling: an uncooperative provider may still return after cancellation.
        activeRequest = null
        val previous = pendingRead
        pendingRead = null
        status.value = status.value.copy(isSyncing = false)
        previous?.cancel()
    }

    private fun isCurrent(request: Any): Boolean =
        !closed && activeRequest === request && status.value.foreground && status.value.enabled

    private fun startRead() {
        val requestId = Any()
        activeRequest = requestId
        status.value = status.value.copy(isSyncing = true)
        // Enter on the owner context so even an already-cancelled scope clears its pending state.
        val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                currentCoroutineContext().ensureActive()
                val expectedGeneration = generation()
                val instant = clock.instant()
                val zone = zoneId()
                val today = instant.atZone(zone).toLocalDate()
                val request = HealthConnectAutomaticSyncRequest(
                    firstDate = today.minusDays(29), lastDate = today,
                    zone = zone, syncedAtMillis = instant.toEpochMilli(),
                    expectedGeneration = expectedGeneration,
                )
                val result = withContext(readDispatcher) { read(request) }
                currentCoroutineContext().ensureActive()
                if (result != null && isCurrent(requestId)) {
                    val accepted = onCommit(result, request.expectedGeneration)
                    if (accepted && isCurrent(requestId)) status.value = status.value.copy(snapshot = result.state)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Keep the previous dated snapshot; an explicit Sync action provides recovery.
            } finally {
                // An older cancelled read must not clear the next foreground visit's pending read.
                if (activeRequest === requestId) {
                    activeRequest = null
                    pendingRead = null
                    status.value = status.value.copy(isSyncing = false)
                }
            }
        }
        if (activeRequest === requestId) pendingRead = job
    }
}
