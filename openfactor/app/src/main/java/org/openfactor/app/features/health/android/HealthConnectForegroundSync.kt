package org.openfactor.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers

/** Android lifetime and provider bindings; synchronization policy belongs to the core controller. */
@Composable
fun HealthConnectForegroundSync(
    state: HealthConnectSyncState,
    commands: WorkoutCommands,
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val transfers = remember(context) { HealthConnectTransfers(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val controller = remember(transfers, scope, commands) {
        HealthConnectForegroundSyncController(
            scope = scope,
            readDispatcher = Dispatchers.IO,
            read = { request ->
                when (val result = transfers.sync(
                    request.firstDate, request.lastDate, request.zone,
                    context.packageName, request.syncedAtMillis,
                )) {
                    is HealthConnectResult.Success -> result.value
                    else -> null
                }
            },
            generation = commands::healthSyncGeneration,
            onCommit = commands::importHealthConnectSyncIfCurrent,
            initialState = state,
            enabled = enabled,
        )
    }

    SideEffect { controller.updateInputs(state, enabled) }
    DisposableEffect(controller) {
        onDispose { controller.close() }
    }
    DisposableEffect(lifecycle, controller) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> controller.setForeground(true)
                Lifecycle.Event.ON_PAUSE -> controller.setForeground(false)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        controller.setForeground(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose {
            lifecycle.removeObserver(observer)
            controller.setForeground(false)
        }
    }
}
