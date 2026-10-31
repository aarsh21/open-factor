package org.openfactor.app

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

/** One automatic read per foreground visit, regardless of how recently a previous visit synced. */
internal class HealthConnectForegroundGate {
    private var attemptedThisVisit = false

    fun begin(state: HealthConnectSyncState, enabled: Boolean): Boolean {
        if (!enabled || state.lastSyncedAtMillis == null || attemptedThisVisit) return false
        attemptedThisVisit = true
        return true
    }

    fun leaveForeground() {
        attemptedThisVisit = false
    }
}

/**
 * Refreshes previously connected data while the app is visible. It never launches consent,
 * writes provider records, schedules a background job or invents readings after a failed read.
 */
@Composable
fun HealthConnectForegroundSync(
    state: HealthConnectSyncState,
    onSync: (HealthConnectSyncData) -> Unit,
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val transfers = remember(context) { HealthConnectTransfers(context.applicationContext) }
    val gate = remember { HealthConnectForegroundGate() }
    val scope = rememberCoroutineScope()
    val latestData by rememberUpdatedState(state)
    val latestSave by rememberUpdatedState(onSync)
    val latestEnabled by rememberUpdatedState(enabled)
    var job by remember { mutableStateOf<Job?>(null) }

    fun refresh() {
        if (job?.isActive == true) return
        val today = LocalDate.now()
        if (!gate.begin(latestData, latestEnabled)) return
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
        }
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
            if (event == Lifecycle.Event.ON_PAUSE) {
                cancelPendingRead()
                gate.leaveForeground()
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            cancelPendingRead()
        }
    }
    // Closing the explicit panel must not immediately repeat its just-completed transfer.
    LaunchedEffect(enabled) {
        if (!enabled) cancelPendingRead()
    }
    LaunchedEffect(state.lastSyncedAtMillis != null) {
        if (enabled && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) refresh()
    }
}
