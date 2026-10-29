package org.openfactor.app

import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal const val HEALTH_CONNECT_FOREGROUND_INTERVAL_MS = 15L * 60L * 1000L

/** Uses wall time for saved freshness and elapsed time to throttle unsuccessful foreground reads. */
internal class HealthConnectForegroundGate {
    private var lastAttemptElapsed: Long? = null
    private var lastAttemptDate: LocalDate? = null

    fun begin(state: HealthConnectSyncState, enabled: Boolean, now: Long, today: LocalDate, elapsed: Long): Boolean {
        val lastSync = state.lastSyncedAtMillis ?: return false
        if (!enabled) return false
        val age = now - lastSync
        if (state.lastDate == today && age in 0 until HEALTH_CONNECT_FOREGROUND_INTERVAL_MS) return false
        val attempted = lastAttemptElapsed
        if (attempted != null && lastAttemptDate == today && elapsed >= attempted &&
            elapsed - attempted < HEALTH_CONNECT_FOREGROUND_INTERVAL_MS) return false
        lastAttemptElapsed = elapsed
        lastAttemptDate = today
        return true
    }

    fun cancel() {
        lastAttemptElapsed = null
        lastAttemptDate = null
    }
}

/**
 * Refreshes previously connected data while the app is visible. It never launches consent,
 * writes provider records, schedules a background job or invents readings after a failed read.
 */
@Composable
fun HealthConnectForegroundSync(
    data: AppData,
    onSync: (HealthConnectSyncData) -> Unit,
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val transfers = remember(context) { HealthConnectTransfers(context.applicationContext) }
    val gate = remember { HealthConnectForegroundGate() }
    val scope = rememberCoroutineScope()
    val latestData by rememberUpdatedState(data.healthConnect)
    val latestSave by rememberUpdatedState(onSync)
    val latestEnabled by rememberUpdatedState(enabled)
    var job by remember { mutableStateOf<Job?>(null) }

    fun refresh() {
        if (job?.isActive == true) return
        val today = LocalDate.now()
        if (!gate.begin(latestData, latestEnabled, System.currentTimeMillis(), today, SystemClock.elapsedRealtime())) return
        job = scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    transfers.sync(today.minusDays(29), today, ZoneId.systemDefault(), context.packageName)
                }
                if (result is HealthConnectResult.Success && latestEnabled &&
                    lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    latestSave(result.value)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Keep the last dated snapshot. The explicit Sync action provides visible recovery.
            }
        }
    }

    fun cancelPendingRead() {
        if (job?.isActive == true) {
            job?.cancel()
            gate.cancel()
        }
    }

    DisposableEffect(lifecycle, enabled) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
            if (event == Lifecycle.Event.ON_PAUSE) {
                cancelPendingRead()
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            cancelPendingRead()
        }
    }
    LaunchedEffect(enabled) {
        if (enabled && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) refresh()
    }
}
