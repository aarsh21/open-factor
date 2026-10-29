package org.openfactor.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HealthConnectSettingsRow(onClick: () -> Unit) {
    SettingsRow(Icons.Rounded.HealthAndSafety, "Health Connect", "", onClick = onClick)
}

/** One action handles connection, Android consent and the first real provider sync. */
@Composable
fun HealthConnectPanel(
    data: AppData,
    onImportWeights: (List<ScaleWeightRecord>) -> Unit,
    onClose: () -> Unit,
    onSync: ((HealthConnectSyncData) -> Unit)? = null,
    autoConnectOnOpen: Boolean = false,
) {
    val context = LocalContext.current
    val transfers = remember(context) { HealthConnectTransfers(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var availability by remember { mutableStateOf(healthConnectAvailability(context)) }
    var grants by remember { mutableStateOf(emptySet<String>()) }
    var checking by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var waitingForPermission by rememberSaveable { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var autoConnectConsumed by rememberSaveable { mutableStateOf(false) }
    val importSync by rememberUpdatedState(onSync)
    val importWeights by rememberUpdatedState(onImportWeights)

    fun syncNow() {
        if (busy) return
        busy = true
        message = null
        scope.launch {
            try {
                val today = LocalDate.now()
                when (val result = withContext(Dispatchers.IO) {
                    transfers.sync(today.minusDays(29), today, ZoneId.systemDefault(), context.packageName)
                }) {
                    is HealthConnectResult.Success -> {
                        try {
                            val save = importSync
                            if (save != null) save(result.value) else importWeights(result.value.weights)
                            val state = result.value.state
                            message = if (state.weightReadings == 0 && state.dailySteps.isEmpty())
                                "Connected. No shared readings in the last 30 days."
                            else "Synced from Health Connect."
                        } catch (_: Exception) {
                            message = "Readings couldn’t be saved. Sync again to retry."
                        }
                    }
                    is HealthConnectResult.PermissionRequired -> {
                        grants = emptySet()
                        message = "Allow weight or steps access to connect."
                    }
                    is HealthConnectResult.ProviderUnavailable -> {
                        availability = result.availability
                        grants = emptySet()
                        message = "Health Connect is unavailable on this device."
                    }
                    HealthConnectResult.PermissionRevoked -> {
                        grants = emptySet()
                        message = "Access changed. Review Health Connect access, then connect again."
                    }
                    HealthConnectResult.Failed -> message = "Health Connect couldn’t read your records. Try syncing again."
                }
            } finally {
                busy = false
            }
        }
    }

    val permissions = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { granted ->
        waitingForPermission = false
        grants = granted.intersect(healthConnectReadPermissions)
        if (canSyncHealthConnect(grants)) syncNow()
        else message = "Access wasn’t allowed. You can connect again whenever you’re ready."
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        availability = healthConnectAvailability(context)
        scope.launch {
            checking = true
            try {
                grants = when (val result = withContext(Dispatchers.IO) { transfers.grantedReadPermissions() }) {
                    is HealthConnectResult.Success -> result.value
                    else -> emptySet()
                }
            } finally { checking = false }
        }
    }

    fun openAccess() {
        try {
            context.startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
        } catch (_: Exception) {
            message = "Health Connect settings couldn’t open. Open Android Settings and search for Health Connect."
        }
    }

    fun connectOrSync() {
        if (busy || checking || waitingForPermission) return
        message = null
        if (availability == HealthConnectAvailability.UPDATE_REQUIRED) {
            val uri = Uri.parse("market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding")
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.android.vending"))
            } catch (_: Exception) {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")))
                } catch (_: Exception) {
                    message = "Install or update Health Connect from Google Play, then return here."
                }
            }
            return
        }
        if (availability != HealthConnectAvailability.AVAILABLE) return
        if (canSyncHealthConnect(grants)) syncNow()
        else {
            waitingForPermission = true
            try {
                permissions.launch(healthConnectReadPermissions)
            } catch (_: Exception) {
                waitingForPermission = false
                availability = healthConnectAvailability(context)
                message = "The Health Connect permission screen couldn’t open. Update Health Connect and try again."
            }
        }
    }

    LaunchedEffect(autoConnectOnOpen, checking, availability) {
        if (autoConnectOnOpen && !checking && !autoConnectConsumed) {
            autoConnectConsumed = true
            connectOrSync()
        }
    }
    val state = data.healthConnect
    val connected = canSyncHealthConnect(grants)
    val pending = busy || waitingForPermission || checking
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.safeDrawingPadding()) {
                PageHeading("Health Connect", back = onClose, actions = {
                    if (availability == HealthConnectAvailability.AVAILABLE)
                        IconButton(::openAccess) { Icon(Icons.Rounded.Tune, "Manage Health Connect access") }
                })
                Column(Modifier.verticalScroll(rememberScrollState()).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(Icons.Rounded.HealthAndSafety, null, Modifier.size(36.dp))
                        Text(when {
                            checking -> "Checking access"
                            availability == HealthConnectAvailability.UNAVAILABLE -> "Unavailable on this device"
                            availability == HealthConnectAvailability.UPDATE_REQUIRED -> "Set up Health Connect"
                            connected -> "Connected"
                            else -> "Not connected"
                        }, style = MaterialTheme.typography.titleLarge)
                    }
                    PrimaryAction(
                        when {
                            busy -> "Syncing"
                            waitingForPermission -> "Waiting for access"
                            availability == HealthConnectAvailability.UPDATE_REQUIRED -> "Set up Health Connect"
                            connected -> "Sync now"
                            else -> "Connect Health Connect"
                        },
                        ::connectOrSync,
                        enabled = !pending && availability != HealthConnectAvailability.UNAVAILABLE,
                    )
                    if (pending) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (state.lastSyncedAtMillis != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            HealthSyncValue("Last synced", android.text.format.DateFormat.getMediumDateFormat(context)
                                .format(Date(state.lastSyncedAtMillis)) + " · " + android.text.format.DateFormat.getTimeFormat(context)
                                .format(Date(state.lastSyncedAtMillis)))
                            HealthSyncValue("Weight readings", if (state.weightAccess) state.weightReadings.toString() else "No access")
                            val stepsToday = state.dailySteps.firstOrNull { it.date == LocalDate.now() }?.count
                            HealthSyncValue("Steps today", when {
                                !state.stepsAccess -> "No access"
                                stepsToday == null -> "No reading"
                                else -> java.text.NumberFormat.getIntegerInstance().format(stepsToday)
                            })
                        }
                    }
                    message?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                    if (availability == HealthConnectAvailability.UNAVAILABLE) {
                        Text("Health Connect needs Android 9 or newer and a supported profile. Your local workouts and body records remain available.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("Reads weight, scale body fat and daily steps for the last 30 days. Manual weight entries keep priority. Readings stay on this device and appear in your charts.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (connected && !grants.containsAll(healthConnectReadPermissions))
                            Text("Some access is off. Use the access control above to change what Health Connect shares.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthSyncValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

/** Android opens this rationale directly from its Health Connect permission screen. */
class HealthConnectRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OpenFactorTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.safeDrawingPadding()) {
                        PageHeading("Health Connect privacy", back = { finish() })
                        Column(Modifier.verticalScroll(rememberScrollState()).padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("Open Factor reads weight, scale body fat and aggregated daily steps after you connect through Android’s permission screen.")
                            Text("Dated readings are stored locally, included in backups and shown in your charts. Manual weight dates take priority. No account is required.")
                            Text("Health records are not sent to the exercise video service. Internet access is used to download exercise demonstrations.")
                            Text("You can deny or revoke access in Health Connect. Revoking access does not erase readings already imported into Open Factor. Open Factor does not delete records from Health Connect.")
                        }
                    }
                }
            }
        }
    }
}
